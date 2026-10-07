package dev.bastion.client;

import dev.bastion.client.anim.WeaponAnimatable;
import dev.bastion.client.render.CasingRenderer;
import dev.bastion.client.render.TracerRenderer;
import dev.bastion.client.vfx.VfxManager;
import dev.bastion.client.vfx.VfxParams;
import dev.bastion.client.vfx.VfxPresets;
import dev.bastion.client.vfx.WeaponFx;
import dev.bastion.network.TurretFireEvent;
import dev.bastion.registry.BastionSounds;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.weapon.WeaponData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

/**
 * Plays one turret discharge on the client (PLAN 5.3, 7): muzzle flash at the muzzle bone, recoil, tracer
 * per shot, impact when each tracer lands, casing, sound (distant tail past 32 blocks), heat haze on streaks.
 */
public final class TurretFireEffects {
    private static final double FAR_SOUND_DISTANCE = 32;
    /**
     * Shot sounds started per tick, near ones first: with hundreds of turrets firing, every shot would take one of the
     * sound engine's few hundred channels and clip the rest. Far shots stop at half the budget.
     */
    private static final int SHOT_SOUNDS_PER_TICK = 8;
    private static long soundTick;
    private static int soundsThisTick;

    private TurretFireEffects() {
    }

    public static void onFire(TurretFireEvent event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !(level.getBlockEntity(event.pos()) instanceof TurretBaseBlockEntity turret)) return;
        WeaponData data = turret.inventory().weaponData();
        int color = 0xFF000000 | (data == null ? 0xFFFFFF : data.energyColor());
        WeaponFx fx = WeaponFx.of(event.weaponType());
        ClientTurret client = ClientTurret.of(event.pos());
        RandomSource random = RandomSource.create(event.seed());

        Vec3 muzzle = client.muzzles[Math.floorMod(event.muzzle(), client.muzzles.length)];
        Vec3 firstEnd = event.shots().isEmpty() ? null : event.shots().get(0).end();
        if (muzzle == null || muzzle.distanceToSqr(Vec3.atCenterOf(event.pos())) > (turret.large() ? 49 : 9)) { // Railgun: ~5 blocks out
            muzzle = turret.alongMount(1.45); // not drawn yet: near the pivot, whichever way the turret is mounted
        }
        // No shot to follow (rockets fly as entities): the barrel's own direction.
        Vec3 direction = firstEnd == null ? turret.toWorld(Vec3.directionFromRotation(turret.renderPitch(1), turret.renderYaw(1)))
                : firstEnd.subtract(muzzle).normalize();

        WeaponAnimatable weapon = client.weapon();
        if (weapon != null) {
            weapon.emptyTube(event.muzzle()); // Rocket Launcher: the rocket left this tube
            weapon.trigger("fire");
            weapon.recoil.impulse(fx.recoilKick());
        }
        long now = level.getGameTime();
        client.streak = now - client.lastShotTick <= 20 ? client.streak + 1 : 1;
        client.lastShotTick = now;
        // Weapons with their own delivery: lightning bolts and a flame stream instead of tracers.
        if (fx == WeaponFx.TESLA) {
            TeslaEffects.discharge(level, event, muzzle, color);
            return;
        }
        if (fx == WeaponFx.RAILGUN) {
            RailgunEffects.fire(level, event, client, muzzle, color);
            return;
        }
        if (fx == WeaponFx.LASER_RIFLE) {
            LaserEffects.fire(level, event, muzzle, color);
            return;
        }
        if (fx == WeaponFx.FLAMETHROWER) {
            FlameEffects.pulse(level, event, client, muzzle, direction, data == null ? 8 : data.range(), color);
            return;
        }
        if (fx == WeaponFx.REPULSOR) { // the wave follows the barrel, not the first pushed mob
            Vec3 aim = turret.toWorld(Vec3.directionFromRotation(turret.renderPitch(1), turret.renderYaw(1)));
            RepulsorEffects.wave(level, event, client, muzzle, aim, data == null ? 3 : data.range(), color);
            return;
        }
        if (fx == WeaponFx.REPULSOR_DOME) {
            RepulsorEffects.dome(level, event, muzzle, data == null ? 6 : (float) data.range(), color);
            return;
        }
        VfxManager.play(fx.muzzle(), muzzle, direction, VfxParams.of().color(color).seed(event.seed()));
        if (client.streak >= 3) {
            VfxManager.play(VfxPresets.HEAT_HAZE, muzzle, direction, VfxParams.of().seed(event.seed() + 1));
        }

        if (fx.casing() && client.eject != null) {
            Vec3 right = direction.cross(new Vec3(0, 1, 0));
            right = right.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : right.normalize(); // barrel straight up or down
            Vec3 velocity = right.scale(0.11 + random.nextFloat() * 0.05).add(0, 0.12 + random.nextFloat() * 0.06, 0)
                    .add(direction.scale(-0.03));
            CasingRenderer.eject(event.pos(), client.eject, velocity, color, false, event.seed());
        }

        if (fx.pump()) pump(level, event, client, color, random);

        for (TurretFireEvent.Shot shot : event.shots()) {
            if (event.speed() > 0) { // a bullet: flies its path until the server says where it ended (BulletImpact)
                TracerRenderer.spawnBullet(shot.blockState(), muzzle, shot.end(), event.speed(), color, fx.tracerWidth(),
                        (end, normal, hit, blockState) -> impact(level, fx, event.precisionLock(),
                                new TurretFireEvent.Shot(end, normal, hit, blockState), color, event.seed()));
                continue;
            }
            double distance = shot.end().distanceTo(muzzle);
            int ticks = Mth.clamp((int) Math.ceil(distance / fx.tracerSpeed()), 2, 4);
            TracerRenderer.spawn(muzzle, shot.end(), color, fx.tracerWidth(), ticks, shot.hit() == TurretFireEvent.MISS ? null
                    : () -> impact(level, fx, event.precisionLock(), shot, color, event.seed()));
        }
        playShot(level, event, muzzle, fx, random);
    }

    /** Shotgun: the pump racks after the recoil settles and throws the spent shell mid-stroke (PLAN 7.3). */
    private static void pump(ClientLevel level, TurretFireEvent event, ClientTurret client, int color, RandomSource random) {
        long seed = event.seed();
        client.after(7, () -> {
            if (client.weapon() != null) client.weapon().trigger("pump");
            level.playLocalSound(event.pos().getX() + 0.5, event.pos().getY() + 0.8, event.pos().getZ() + 0.5,
                    BastionSounds.SHOTGUN_PUMP.get(), SoundSource.HOSTILE, 0.8f, 0.96f + random.nextFloat() * 0.08f, false);
        });
        client.after(13, () -> {
            if (client.eject == null) return;
            Vec3 velocity = new Vec3(random.nextGaussian() * 0.03, 0.16, random.nextGaussian() * 0.03);
            CasingRenderer.eject(event.pos(), client.eject, velocity.add(client.eject.subtract(Vec3.atCenterOf(event.pos())).normalize().scale(0.1)),
                    color, true, seed);
        });
        client.after(20, () -> level.playLocalSound(event.pos().getX() + 0.5, event.pos().getY(), event.pos().getZ() + 0.5,
                BastionSounds.SHOTGUN_SHELL.get(), SoundSource.HOSTILE, 0.5f, 1f, false));
    }

    private static void impact(ClientLevel level, WeaponFx fx, boolean lock, TurretFireEvent.Shot shot, int color, long seed) {
        int blockColor = 0xFF888888;
        if (shot.hit() == TurretFireEvent.BLOCK) {
            var state = Block.stateById(shot.blockState());
            blockColor = 0xFF000000 | state.getMapColor(level, net.minecraft.core.BlockPos.containing(shot.end())).col;
        }
        VfxParams params = VfxParams.of().color(color).normal(shot.normal()).seed(seed).blockColor(blockColor);
        VfxManager.play(lock ? fx.lockImpact() : fx.impact(), shot.end(), shot.normal().reverse(), params);
        if (shot.hit() == TurretFireEvent.BLOCK) {
            level.playLocalSound(shot.end().x, shot.end().y, shot.end().z, BastionSounds.IMPACT_KINETIC.get(), SoundSource.HOSTILE,
                    0.5f, 0.92f + level.random.nextFloat() * 0.16f, false);
        }
    }

    /** Near players hear the shot; past 32 blocks they hear the muffled tail instead (PLAN 5.7). */
    private static void playShot(ClientLevel level, TurretFireEvent event, Vec3 muzzle, WeaponFx fx, RandomSource random) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        boolean far = player.position().distanceTo(muzzle) > FAR_SOUND_DISTANCE;
        if (level.getGameTime() != soundTick) {
            soundTick = level.getGameTime();
            soundsThisTick = 0;
        }
        if (soundsThisTick >= (far ? SHOT_SOUNDS_PER_TICK / 2 : SHOT_SOUNDS_PER_TICK)) return;
        soundsThisTick++;
        SoundEvent sound = (far ? fx.fireTail() : fx.fire()).get();
        float pitch = 0.92f + random.nextFloat() * 0.16f; // +-8%
        Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(sound, SoundSource.HOSTILE,
                far ? 1.6f : 1.0f, pitch, random, muzzle.x, muzzle.y, muzzle.z));
    }
}
