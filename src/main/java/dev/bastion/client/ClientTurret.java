package dev.bastion.client;

import dev.bastion.client.anim.WeaponAnimatable;
import dev.bastion.client.render.RailRenderer;
import dev.bastion.client.vfx.VfxManager;
import dev.bastion.client.vfx.VfxParams;
import dev.bastion.client.vfx.VfxPresets;
import dev.bastion.registry.BastionSounds;
import dev.bastion.registry.BastionWeaponTypes;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.TurretInventory;
import dev.bastion.turret.TurretState;
import dev.bastion.weapon.WeaponData;
import dev.bastion.weapon.WeaponModuleItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Client-only state of one turret (PLAN 6.1): its WeaponAnimatable, where the muzzle/eject/sensor bones were
 * last drawn, Precision Lock progress, barrel spin, shot streak, loop sounds and delayed effects. Keyed by
 * block position and dropped once the turret is gone.
 */
public final class ClientTurret {
    /** Barrel cluster speed at full spin: 4 barrels x 3 turns/s = 12 shots/s, in radians per tick. */
    private static final float MAX_SPIN_SPEED = (float) (Math.PI * 2 * 3 / 20);
    private static final Map<Long, ClientTurret> ALL = new HashMap<>();

    public final BlockPos pos;
    @Nullable
    private WeaponAnimatable weapon;
    private boolean seen;
    boolean removed;
    /** World positions captured during the last weapon render; null until first drawn. */
    /** World positions of muzzle_0..11 (Missile Launcher tubes; other weapons use up to four). */
    public final Vec3[] muzzles = new Vec3[12];
    @Nullable
    public Vec3 eject, sensor;
    /** Ticks aimed at the same target (drives the lock-on reticle). */
    public int lockTicks;
    private int lockTarget = -1;
    /** Machine Gun spin 0..1 from SpinSync. */
    public float spinTarget;
    public int streak;
    public long lastShotTick = -100;
    /** Game tick the current CHARGING started (Sniper laser sight), -1 when not charging. */
    public long chargeStart = -1;
    private TurretState lastState = TurretState.DISABLED;
    @Nullable
    private TurretLoopSound hum, spinLoop, flameLoop;
    private final List<Delayed> delayed = new ArrayList<>();

    private ClientTurret(BlockPos pos) {
        this.pos = pos;
    }

    public static ClientTurret of(BlockPos pos) {
        return ALL.computeIfAbsent(pos.asLong(), k -> new ClientTurret(pos.immutable()));
    }

    public static Iterable<ClientTurret> all() {
        return ALL.values();
    }

    @Nullable
    public WeaponAnimatable weapon() {
        return weapon;
    }

    /** Tracks weapon changes: deploy (and its sound) plays only when a weapon is mounted while the turret is loaded. */
    @Nullable
    public WeaponAnimatable updateWeapon(TurretBaseBlockEntity turret) {
        WeaponModuleItem item = turret.inventory().getStackInSlot(TurretInventory.WEAPON).getItem() instanceof WeaponModuleItem w ? w : null;
        if (item == null) {
            weapon = null;
        } else if (weapon == null || weapon.item() != item) {
            weapon = new WeaponAnimatable(item, seen);
            weapon.setPose(() -> switch (turret.clientState()) {
                case ACQUIRING, AIMING, CHARGING, FIRING, COOLDOWN -> WeaponAnimatable.Pose.AIM;
                case NO_AMMO -> WeaponAnimatable.Pose.NO_AMMO;
                case OVERHEAT -> WeaponAnimatable.Pose.OVERHEAT;
                default -> WeaponAnimatable.Pose.IDLE;
            });
            if (seen && turret.getLevel() instanceof ClientLevel level) {
                level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, BastionSounds.TURRET_DEPLOY.get(),
                        SoundSource.BLOCKS, 0.8f, 1f, false);
            }
        }
        seen = true;
        return weapon;
    }

    public void after(int ticks, Runnable task) {
        delayed.add(new Delayed(ticks, task));
    }

    /** Charge progress 0..1 of a charged weapon (Sniper). */
    public float chargeProgress(long gameTime, float partialTick, int chargeTicks) {
        return chargeStart < 0 ? 0 : Mth.clamp((gameTime - chargeStart + partialTick) / Math.max(1, chargeTicks), 0, 1);
    }

    /** Lock progress 0..1 toward Precision Lock ({@code lockTicksNeeded}). */
    public float lockProgress(int lockTicksNeeded) {
        return Math.min(1f, (float) lockTicks / Math.max(1, lockTicksNeeded));
    }

    @Nullable
    public Vec3 muzzleOrNull() {
        return muzzles[0];
    }

    public static void tickAll(ClientLevel level) {
        for (Iterator<ClientTurret> it = ALL.values().iterator(); it.hasNext(); ) {
            ClientTurret t = it.next();
            if (!(level.getBlockEntity(t.pos) instanceof TurretBaseBlockEntity turret)) {
                t.removed = true;
                it.remove();
                continue;
            }
            t.tick(level, turret);
        }
    }

    public static void clear() {
        ALL.values().forEach(t -> t.removed = true);
        ALL.clear();
    }

    private void tick(ClientLevel level, TurretBaseBlockEntity turret) {
        TurretState state = turret.clientState();
        boolean engaged = state == TurretState.AIMING || state == TurretState.COOLDOWN || state == TurretState.FIRING
                || state == TurretState.CHARGING;
        if (engaged && turret.clientTargetId() == lockTarget) lockTicks++;
        else lockTicks = 0;
        lockTarget = engaged ? turret.clientTargetId() : -1;

        WeaponData data = turret.inventory().weaponData();
        if (weapon != null) {
            weapon.prevSpinAngle = weapon.spinAngle;
            weapon.spinSpeed += (spinTarget * MAX_SPIN_SPEED - weapon.spinSpeed) * 0.2f;
            weapon.spinAngle += weapon.spinSpeed;
            weapon.heat = data == null || data.heat().max() <= 0 ? 0 : turret.clientHeat() / data.heat().max();
        }
        onStateChange(level, state, data);
        if (weapon != null) {
            weapon.tickTubes(state != TurretState.NO_AMMO);
            weapon.missiles = turret.clientTubes();
        }
        if (turret.clientDamaged() && level.random.nextInt(30) == 0) {
            // Damaged: short-circuit sparks off a random side of the base (PLAN 4.1).
            float angle = level.random.nextFloat() * Mth.TWO_PI;
            Vec3 side = new Vec3(Mth.cos(angle), 0.4, Mth.sin(angle));
            VfxManager.play(VfxPresets.DAMAGED_SPARK, Vec3.atBottomCenterOf(pos).add(side.x * 0.45, 0.35, side.z * 0.45), side,
                    VfxParams.of().seed(level.random.nextLong()));
        }
        weaponTick(level, state, data);
        loopSounds(level, data);
        smokeAfterBursts(level);

        for (Iterator<Delayed> it = delayed.iterator(); it.hasNext(); ) {
            Delayed d = it.next();
            if (--d.ticks <= 0) {
                it.remove();
                d.task.run();
            }
        }
    }

    /** Overheat vents steam and plays its alarm; leaving overheat plays the cooldown_vent clip (PLAN 4.4, 7.2). */
    private void onStateChange(ClientLevel level, TurretState state, @Nullable WeaponData data) {
        Vec3 at = muzzles[0] != null ? muzzles[0]
                : level.getBlockEntity(pos) instanceof TurretBaseBlockEntity turret ? turret.alongMount(1.45) : Vec3.atCenterOf(pos);
        if (state == TurretState.OVERHEAT && lastState != TurretState.OVERHEAT) {
            VfxManager.play(VfxPresets.OVERHEAT_STEAM, at, new Vec3(0, 1, 0), VfxParams.of().seed(level.getGameTime()));
            level.playLocalSound(at.x, at.y, at.z, BastionSounds.MACHINE_GUN_OVERHEAT.get(), SoundSource.HOSTILE, 0.9f, 1f, false);
        } else if (state == TurretState.OVERHEAT && level.getGameTime() % 5 == 0) {
            VfxManager.play(VfxPresets.HEAT_HAZE, at, new Vec3(0, 1, 0), VfxParams.of().seed(level.getGameTime()));
        } else if (state != TurretState.OVERHEAT && lastState == TurretState.OVERHEAT && weapon != null) {
            weapon.trigger("cooldown_vent");
        }
        if (state == TurretState.CHARGING && lastState != TurretState.CHARGING) {
            chargeStart = level.getGameTime();
            ResourceLocation type = data == null ? null : data.type();
            var sound = BastionWeaponTypes.TESLA.getId().equals(type) ? BastionSounds.TESLA_CHARGE
                    : BastionWeaponTypes.LASER_RIFLE.getId().equals(type) ? BastionSounds.LASER_CHARGE
                    : BastionWeaponTypes.RAILGUN.getId().equals(type) ? BastionSounds.RAILGUN_CHARGE : BastionSounds.SNIPER_CHARGE;
            level.playLocalSound(at.x, at.y, at.z, sound.get(), SoundSource.HOSTILE, sound == BastionSounds.SNIPER_CHARGE ? 0.8f : 1f, 1f, false);
        } else if (state != TurretState.CHARGING) {
            chargeStart = -1;
        }
        lastState = state;
    }

    /** Per-tick looks of weapons that live between shots: Tesla and Railgun arcs while charging, the Flamethrower's pilot light. */
    private void weaponTick(ClientLevel level, TurretState state, @Nullable WeaponData data) {
        if (data == null || weapon == null) return;
        int color = 0xFF000000 | data.energyColor();
        if (data.type().equals(BastionWeaponTypes.TESLA.getId())) {
            if (state == TurretState.CHARGING) {
                int charge = Math.round(data.params().getOrDefault("charge_ticks", 1f));
                TeslaEffects.charging(level, this, chargeProgress(level.getGameTime(), 0, charge), color);
            } else if (state == TurretState.IDLE) {
                TeslaEffects.idle(level, this, color);
            }
        } else if (data.type().equals(BastionWeaponTypes.LASER_RIFLE.getId())) {
            // Focus rings spin up with the charge and wind down after the shot.
            int charge = Math.round(data.params().getOrDefault("charge_ticks", 1f));
            float progress = state == TurretState.CHARGING ? chargeProgress(level.getGameTime(), 0, charge) : 0;
            weapon.focusSpeed = state == TurretState.CHARGING ? 0.05f + 0.6f * progress * progress : weapon.focusSpeed * 0.9f;
            weapon.prevFocusAngle = weapon.focusAngle;
            weapon.focusAngle += weapon.focusSpeed;
            if (state == TurretState.CHARGING) LaserEffects.gathering(level, this, progress, color);
        } else if (data.type().equals(BastionWeaponTypes.RAILGUN.getId())) {
            // The glow turns violet as it charges and the coil bands light up breech to muzzle; it fades after the shot.
            int charge = Math.round(data.params().getOrDefault("charge_ticks", 1f));
            float progress = state == TurretState.CHARGING ? chargeProgress(level.getGameTime(), 0, charge) : -1;
            weapon.chargeColor = color & 0xFFFFFF;
            weapon.coilFill = progress < 0 ? -1 : Math.min(1, progress / RailRenderer.FILL);
            weapon.chargeGlow = progress < 0 ? Math.max(0, weapon.chargeGlow - 0.07f) : Math.min(1, 0.4f + progress);
            if (progress >= 0) RailgunEffects.charging(level, this, progress, color);
        } else if (data.type().equals(BastionWeaponTypes.FLAMETHROWER.getId()) && !flaming(level)
                && state != TurretState.DISABLED && state != TurretState.NO_AMMO) {
            FlameEffects.pilot(level, this);
        }
    }

    /** Fired within the last few ticks (the Flamethrower pulses every 2). */
    private boolean flaming(ClientLevel level) {
        return level.getGameTime() - lastShotTick <= 4;
    }

    /** Idle hum while armed; Machine Gun spin whine whose pitch follows the barrels (PLAN 5.7, 7.2); the Flamethrower's roar. */
    private void loopSounds(ClientLevel level, @Nullable WeaponData data) {
        var sounds = Minecraft.getInstance().getSoundManager();
        if (weapon != null && (hum == null || hum.finished())) {
            hum = new TurretLoopSound(BastionSounds.TURRET_IDLE_HUM.get(), this,
                    () -> weapon == null ? 0 : (lastState == TurretState.DISABLED ? 0 : 0.18), () -> 1.0);
            sounds.play(hum);
        }
        if (spinTarget > 0.05f && (spinLoop == null || spinLoop.finished())) {
            spinLoop = new TurretLoopSound(BastionSounds.MACHINE_GUN_SPIN_LOOP.get(), this,
                    () -> weapon == null ? 0 : Math.min(1, weapon.spinSpeed / MAX_SPIN_SPEED) * 0.7,
                    () -> weapon == null ? 0.5 : 0.5 + Math.min(1, weapon.spinSpeed / MAX_SPIN_SPEED) * 0.9);
            sounds.play(spinLoop);
        }
        if (data != null && data.type().equals(BastionWeaponTypes.FLAMETHROWER.getId()) && flaming(level)
                && (flameLoop == null || flameLoop.finished())) {
            flameLoop = new TurretLoopSound(BastionSounds.FLAMETHROWER_LOOP.get(), this, () -> flaming(level) ? 0.9 : 0, () -> 1.0);
            sounds.play(flameLoop);
        }
    }

    /** A wisp of smoke rises from the barrel half a second after a burst ends (PLAN 7.2, 7.3). */
    private void smokeAfterBursts(ClientLevel level) {
        if (streak >= 3 && level.getGameTime() - lastShotTick == 10 && muzzles[0] != null) {
            VfxManager.play(VfxPresets.SMOKE_WISP, muzzles[0], new Vec3(0, 1, 0), VfxParams.of().seed(level.getGameTime()));
            streak = 0;
        }
    }

    private static final class Delayed {
        int ticks;
        final Runnable task;

        Delayed(int ticks, Runnable task) {
            this.ticks = ticks;
            this.task = task;
        }
    }
}
