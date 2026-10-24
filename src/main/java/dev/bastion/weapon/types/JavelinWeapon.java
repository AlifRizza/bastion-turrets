package dev.bastion.weapon.types;

import dev.bastion.projectile.JavelinMissileEntity;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.weapon.FireContext;
import dev.bastion.weapon.FireProfile;
import dev.bastion.weapon.ShotReport;
import dev.bastion.weapon.StatSheet;
import dev.bastion.weapon.WeaponData;
import dev.bastion.weapon.WeaponState;
import dev.bastion.weapon.WeaponType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Javelin Missile (Fortress Turret Base, PLAN "Turret 3x3", user 2026-10-23): one big missile straight up out of
 * sight, then straight down onto the densest cluster of valid ground targets under open sky. Each shot takes a
 * Javelin Missile and energy_per_shot FE. It never picks a flyer, a target inside min_range or one under a roof, and
 * never fires from a roofed silo (so not in the Nether or underground at all: user, no fallback).
 */
public class JavelinWeapon extends WeaponType {
    /** The silo mouth, in blocks above the turntable (the model's muzzle_0, design_weapons.py JV_MOUTH). */
    public static final double MOUTH = 1.875;
    /** Where the loaded missile's tail sits, in blocks above the turntable (design_weapons.py JV_SEAT): its flight starts here. */
    public static final double SEAT = 0.25;

    @Override
    public FireProfile fireProfile() {
        return FireProfile.PROJECTILE;
    }

    @Override
    public boolean canTarget(Entity entity, TurretBaseBlockEntity turret) {
        return MortarWeapon.grounded(entity) && outsideMinRange(entity, turret) && openSky(entity);
    }

    /** Nothing but sky above its head: no block a missile would hit above it (false under a roof or leaves, underground and in the Nether). */
    public static boolean openSky(Entity entity) {
        return openSky(entity.level(), BlockPos.containing(entity.getEyePosition()));
    }

    /**
     * The motion-blocking heightmap, not sky light: it changes the moment a block is placed (light lags, and is 0 in
     * the GameTest world).
     */
    public static boolean openSky(Level level, BlockPos pos) {
        // The Nether's heightmap stops at its bedrock roof, so its top would count as open: a ceiling world never does.
        return !level.dimensionType().hasCeiling() && pos.getY() >= level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ());
    }

    /** Where the missile leaves the silo. */
    public static Vec3 siloMouth(TurretBaseBlockEntity turret) {
        return turret.alongMount(TurretBaseBlockEntity.BASE_HEIGHT + MOUTH);
    }

    /** Nothing above the silo doors: the missile can climb. */
    public static boolean siloSeesSky(TurretBaseBlockEntity turret) {
        return openSky(turret.getLevel(), BlockPos.containing(siloMouth(turret)).above());
    }

    /** The target's feet (the missile comes down on top of it); none while the silo is roofed. No line of sight needed. */
    @Override
    @Nullable
    public Vec3 sight(TurretBaseBlockEntity turret, LivingEntity target, StatSheet stats) {
        return siloSeesSky(turret) ? target.position() : null;
    }

    /**
     * The densest cluster first: the candidate with the most valid targets within blast_radius of it (itself counted),
     * then the larger summed health of those; the GUI order (they arrive in it) breaks the rest of the ties. Candidates
     * within blast_radius of where any Javelin missile still in flight is coming down go last (its own next shot inside
     * a ~7 s flight with a fire-rate module, or another Javelin's; user 2026-10-24): fired at only when nothing else is
     * left. The box reaches a range past the turret's own: another Javelin's missile still climbs over its own silo.
     */
    @Override
    public List<LivingEntity> rank(TurretBaseBlockEntity turret, List<LivingEntity> candidates, StatSheet stats) {
        double radius = stats.data().param("blast_radius");
        double r2 = radius * radius;
        // ponytail: n² over the candidates in range, fine for the few dozen a 48-block search finds
        Map<LivingEntity, Integer> counts = new IdentityHashMap<>();
        Map<LivingEntity, Double> health = new IdentityHashMap<>();
        for (LivingEntity centre : candidates) {
            int count = 0;
            double sum = 0;
            for (LivingEntity other : candidates) {
                if (other.distanceToSqr(centre) <= r2) {
                    count++;
                    sum += other.getHealth();
                }
            }
            counts.put(centre, count);
            health.put(centre, sum);
        }
        double reach = stats.range() * 2 + 4;
        List<Vec3> incoming = turret.getLevel().getEntitiesOfClass(JavelinMissileEntity.class, new AABB(turret.getBlockPos()).inflate(reach, 128, reach))
                .stream().map(JavelinMissileEntity::dropPoint).toList();
        Map<LivingEntity, Boolean> covered = new IdentityHashMap<>();
        for (LivingEntity e : candidates) covered.put(e, incoming.stream().anyMatch(p -> e.distanceToSqr(p) <= r2));
        List<LivingEntity> ranked = new ArrayList<>(candidates);
        ranked.sort(Comparator.<LivingEntity, Boolean>comparing(covered::get).thenComparingInt(e -> -counts.get(e)).thenComparingDouble(e -> -health.get(e)));
        return ranked;
    }

    /** Overwatch: the spot under the crosshair, if in reach, outside min_range and under open sky, from a silo that sees the sky. */
    @Override
    @Nullable
    public Vec3 manualAim(TurretBaseBlockEntity turret, Vec3 point, StatSheet stats) {
        WeaponData data = stats.data();
        double distance = point.subtract(turret.pivot(data)).horizontalDistance();
        boolean reach = distance <= stats.range() && distance >= data.params().getOrDefault("min_range", 0f);
        // The block over the point: on a slab, path or farmland the point lies inside the topmost block itself.
        return reach && siloSeesSky(turret) && openSky(turret.getLevel(), BlockPos.containing(point).above()) ? point : null;
    }

    @Override
    public boolean picksFeet() {
        return true;
    }

    /** Needs the FE for a shot as well as a missile (the base checks the missile). */
    @Override
    public boolean outOfAmmo(TurretBaseBlockEntity turret, WeaponState state) {
        return !turret.hasEnergy(cost(turret));
    }

    /**
     * Launches the missile from the silo onto the target (its feet; the missile follows it while it stays under open sky),
     * or under Overwatch onto the spot under the crosshair (following the creature there, if any).
     */
    @Override
    public void onFire(FireContext context) {
        LivingEntity target = context.target() instanceof LivingEntity living && openSky(living) ? living : null;
        Vec3 drop = context.manualPoint() != null ? context.manualPoint() : target == null ? null : target.position();
        if (drop == null || !context.turret().useEnergy(cost(context.turret()))) return;
        WeaponData data = context.stats().data();
        JavelinMissileEntity missile = new JavelinMissileEntity(context.level(), context.turret().getBlockPos(), context.stats().damage(),
                data.param("blast_edge_damage") * context.stats().damageScale(), data.param("blast_radius"))
                .fly(context.turret().alongMount(TurretBaseBlockEntity.BASE_HEIGHT + SEAT), target, drop, data.param("apex"),
                        Math.round(data.param("hold_ticks")), Math.round(data.param("climb_ticks")), Math.round(data.param("hang_ticks")),
                        Math.round(data.param("dive_ticks")));
        context.level().addFreshEntity(missile);
        ShotReport.send(context, this, 0, List.of());
        context.turret().retarget(); // its next shot picks again: the cluster this missile is on goes last (rank)
    }

    private static int cost(TurretBaseBlockEntity turret) {
        WeaponData data = turret.inventory().weaponData();
        return data == null ? Integer.MAX_VALUE : Math.round(data.param("energy_per_shot"));
    }
}
