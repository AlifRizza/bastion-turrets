package dev.bastion.weapon.types;

import dev.bastion.network.BastionNetwork;
import dev.bastion.network.RackSync;
import dev.bastion.projectile.TurretRocketEntity;
import dev.bastion.registry.BastionEntities;
import dev.bastion.registry.BastionSounds;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.targeting.LineOfSight;
import dev.bastion.turret.targeting.TargetSelector;
import dev.bastion.weapon.FireContext;
import dev.bastion.weapon.FireProfile;
import dev.bastion.weapon.ShotReport;
import dev.bastion.weapon.StatSheet;
import dev.bastion.weapon.WeaponData;
import dev.bastion.weapon.WeaponState;
import dev.bastion.weapon.WeaponType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Missile Launcher Turret (Large Turret Base): two pods of six tubes at a fixed upward angle, turning only left and
 * right. Tubes load one at a time (one Missiles item each, every reload_ticks). Single mode fires a missile whenever
 * one is loaded, alternating pods; salvo mode waits for all twelve and ripples them out in pairs, spread evenly over
 * the valid targets in range (all on one if it is alone). Missiles fly up and home in (TurretRocketEntity#homing).
 */
public class MissileLauncherWeapon extends WeaponType {
    public static final int TUBES = 12;
    /** Every tube loaded. */
    public static final int ALL_TUBES = (1 << TUBES) - 1;
    private static final int FULL = ALL_TUBES, PER_POD = 6;
    // Tube layout in model pixels (tools/blockout/design_weapons.py missile_launcher): pod centre, column and row
    // offsets around the pitch pivot, and how far forward the tube mouths are.
    private static final float POD_X = 11.75f, COLUMN = 2.9f, ROW = 5.2f, MOUTH = 12.4f;

    @Override
    public FireProfile fireProfile() {
        return FireProfile.PROJECTILE;
    }

    @Override
    public boolean firesPerClick() {
        return true;
    }

    /** Under control, salvo mode: one lock per tube before the salvo launches (user 2026-10-22). */
    @Override
    public int manualLocks(WeaponState state) {
        return state.salvoMode ? TUBES : 0;
    }

    /** Every tube loaded: a controlled salvo never launches a partial load, even once the ammo has run out. */
    @Override
    public boolean manualSalvoReady(StatSheet stats, WeaponState state) {
        return !state.salvoActive && state.tubes == FULL;
    }

    /** Tube i: pod i / 6 (0 = the model's -X side), then row (top first) and column (inner first) inside the pod. */
    public static Vec3 tubeOffsetPx(int tube) {
        int pod = tube / PER_POD, k = tube % PER_POD, row = k / 2, col = k % 2;
        float x = (pod == 0 ? -1 : 1) * (POD_X + (col == 0 ? -COLUMN : COLUMN));
        return new Vec3(x, (1 - row) * ROW, -MOUTH);
    }

    /** World position of a tube's mouth. */
    public static Vec3 mouth(TurretBaseBlockEntity turret, WeaponData data, int tube) {
        return modelPoint(turret, data, tubeOffsetPx(tube));
    }

    /** Never too close: a missile launched upward needs room to turn back down. */
    @Override
    public boolean canTarget(Entity entity, TurretBaseBlockEntity turret) {
        return outsideMinRange(entity, turret);
    }

    /** No pitch limits (the missiles climb and dive); only a clear line from above the launcher. */
    @Override
    @Nullable
    public Vec3 sight(TurretBaseBlockEntity turret, LivingEntity target, StatSheet stats) {
        Vec3 from = turret.pivot(stats.data()).add(turret.toWorld(new Vec3(0, 1, 0)));
        for (Vec3 point : new Vec3[]{target.getEyePosition(), target.getBoundingBox().getCenter()}) {
            if (LineOfSight.clear(turret.getLevel(), from, point)) return point;
        }
        return null;
    }

    /** Loads one empty tube every reload_ticks while ammo lasts, and keeps clients' view of the pods current. */
    @Override
    public void tick(TurretBaseBlockEntity turret, WeaponState state, StatSheet stats, boolean engaged) {
        if (!(turret.getLevel() instanceof ServerLevel level)) return;
        state.salvoMode = turret.salvo();
        if (!state.salvoMode) state.salvoActive = false; // switched to Single mid-salvo: the rest stay in their tubes
        int reloadTicks = Math.round(stats.data().param("reload_ticks") / stats.reloadRate());
        if (state.tubes == FULL) {
            state.reloadTimer = reloadTicks; // primed: the first reload comes a full interval after a shot
        } else if (state.reloadTimer > 0) {
            state.reloadTimer--;
        } else if (turret.takeOneAmmo(stats, level)) {
            state.tubes |= Integer.lowestOneBit(~state.tubes & FULL);
            state.reloadTimer = reloadTicks;
            level.playSound(null, turret.getBlockPos(), BastionSounds.MISSILE_RELOAD.get(), SoundSource.BLOCKS, 0.5f,
                    0.9f + level.random.nextFloat() * 0.2f);
        }
        state.ammoLeft = turret.inventory().findAmmoSlot() >= 0;
        if (state.tubes != state.syncedTubes) {
            state.syncedTubes = state.tubes;
            BastionNetwork.sendNear(level, turret.getBlockPos(), new RackSync(turret.getBlockPos(), state.tubes));
        }
    }

    /** Single: any loaded tube. Salvo: all twelve, or whatever is loaded once the ammo has run out. */
    @Override
    public boolean ready(StatSheet stats, WeaponState state) {
        if (!state.salvoMode) return state.tubes != 0;
        return state.salvoActive ? state.tubes != 0 : state.tubes == FULL || (!state.ammoLeft && state.tubes != 0);
    }

    @Override
    public boolean outOfAmmo(TurretBaseBlockEntity turret, WeaponState state) {
        return state.tubes == 0 && turret.inventory().findAmmoSlot() < 0;
    }

    @Override
    public float fireInterval(StatSheet stats, WeaponState state) {
        return state.salvoActive ? stats.data().param("salvo_interval") : stats.fireInterval();
    }

    @Override
    public void onFire(FireContext context) {
        WeaponState state = context.state();
        if (!state.salvoMode) {
            // Alternate pods: the other pod's first loaded tube, else this pod's.
            int tube = firstLoaded(state.tubes, 1 - state.muzzle);
            if (tube < 0) tube = firstLoaded(state.tubes, state.muzzle);
            if (tube < 0) return;
            // The crosshair's creature, if the launcher can see it (a modified client could name one behind a wall).
            LivingEntity target = context.target() instanceof LivingEntity locked && sight(context.turret(), locked, context.stats()) != null ? locked : null;
            // Under control with nothing locked: a random enemy of the filter, hit by the filter's rule (user 2026-10-22).
            boolean filter = target == null && context.manualPoint() != null;
            launch(context, tube, filter ? randomEnemy(context) : target, filter);
            return;
        }
        if (!state.salvoActive) {
            state.salvoActive = true;
            state.salvoFired = 0;
            // A controlled salvo goes where the controller locked, not over the filter's targets.
            state.salvoTargets = context.manualPoint() != null ? context.turret().manual().lockIds()
                    : TargetSelector.findAll(context.turret(), context.stats(), this, TUBES).stream().mapToInt(Entity::getId).toArray();
        }
        for (int pod = 0; pod < 2; pod++) { // one from each pod per volley
            int tube = firstLoaded(state.tubes, pod);
            if (tube < 0) continue;
            LivingEntity target = salvoTarget(context, state.salvoFired++);
            // A controlled salvo's lock that is gone or out of reach: a random enemy of the filter, hit by its rule.
            boolean filter = target == null && context.manualPoint() != null;
            launch(context, tube, filter ? randomEnemy(context) : target, filter);
        }
        if (state.tubes == 0) state.salvoActive = false;
    }

    /**
     * Missile k of a salvo goes to target k mod n: spread evenly, all on one when there is one. Under control the targets
     * are the locks, one per missile; a lock that is gone or out of reach gives none.
     */
    @Nullable
    private static LivingEntity salvoTarget(FireContext context, int k) {
        int[] ids = context.state().salvoTargets;
        boolean manual = context.manualPoint() != null;
        if (ids.length > 0 && context.level().getEntity(ids[k % ids.length]) instanceof LivingEntity target && target.isAlive()
                && (!manual || reachable(context, target))) {
            return target;
        }
        return !manual && context.target() instanceof LivingEntity target ? target : null;
    }

    /** A lock still worth a missile at launch: in the controlled reach and hittable by the controller's rule. */
    private static boolean reachable(FireContext context, LivingEntity target) {
        double range = context.stats().range();
        return target.distanceToSqr(context.turret().pivot(context.stats().data())) <= range * range && context.turret().wouldHit(target);
    }

    /** A random valid target of the turret's filter (not the controller's rule), or none. */
    @Nullable
    private LivingEntity randomEnemy(FireContext context) {
        return TargetSelector.random(context.turret(), context.stats(), this, null, context.level().random);
    }

    private static int firstLoaded(int tubes, int pod) {
        for (int i = pod * PER_POD; i < (pod + 1) * PER_POD; i++) {
            if ((tubes & 1 << i) != 0) return i;
        }
        return -1;
    }

    /** {@code filter}: the missile hits by the turret's filter, not the controller's rule. */
    private void launch(FireContext context, int tube, @Nullable LivingEntity target, boolean filter) {
        WeaponData data = context.stats().data();
        float damage = context.stats().damage();
        TurretRocketEntity missile = new TurretRocketEntity(BastionEntities.TURRET_MISSILE.get(), context.level(),
                context.turret().getBlockPos(), damage, data.param("splash_damage") * context.stats().damageScale(),
                data.param("splash_radius"), data.param("knockback"), Math.round(data.param("fuel_ticks")))
                .homing(target, data.param("turn_rate"), data.param("cruise_speed"), data.param("acceleration"),
                        Math.round(data.param("homing_delay")));
        if (context.manualPoint() != null) missile.randomRetarget();
        if (filter) missile.underFilter();
        missile.setPos(mouth(context.turret(), data, tube));
        missile.launch(context.direction().scale(data.param("launch_speed")));
        context.level().addFreshEntity(missile);
        context.state().tubes &= ~(1 << tube);
        context.state().muzzle = tube / PER_POD;
        ShotReport.send(context, this, tube, List.of());
    }
}
