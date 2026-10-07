package dev.bastion.turret.targeting;

import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.TurretHitboxEntity;
import dev.bastion.weapon.StatSheet;
import dev.bastion.weapon.WeaponType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

/**
 * Target search and validation, PLAN 4.5. The turret searches every 10 ticks and re-validates its
 * current target every tick: filter, weapon filter, range, pitch limits, then line of sight.
 */
public final class TargetSelector {
    private TargetSelector() {
    }

    /** Best valid target by the filter's priority (nearest breaks ties), or null. */
    @Nullable
    public static LivingEntity find(TurretBaseBlockEntity turret, StatSheet stats, WeaponType type) {
        List<LivingEntity> all = findAll(turret, stats, type, 1);
        return all.isEmpty() ? null : all.get(0);
    }

    /** Up to {@code limit} valid targets, best first by the filter's priority (Missile Launcher salvos spread over them). */
    public static List<LivingEntity> findAll(TurretBaseBlockEntity turret, StatSheet stats, WeaponType type, int limit) {
        Vec3 pivot = turret.pivot(stats.data());
        AABB area = new AABB(pivot, pivot).inflate(stats.range());
        Comparator<LivingEntity> nearest = Comparator.comparingDouble(e -> e.distanceToSqr(pivot));
        Comparator<LivingEntity> order = type.targetOrder(switch (turret.filter().priority) {
            case NEAREST -> nearest;
            case LOWEST_HEALTH -> Comparator.<LivingEntity>comparingDouble(LivingEntity::getHealth).thenComparing(nearest);
            case HIGHEST_THREAT -> Comparator.<LivingEntity>comparingDouble(e -> -threat(e, turret)).thenComparing(nearest);
        });
        return turret.getLevel().getEntitiesOfClass(LivingEntity.class, area, e -> isCandidate(turret, e, stats, type)).stream()
                .sorted(order)
                .filter(e -> type.sight(turret, e, stats) != null)
                .limit(limit)
                .toList();
    }

    public static boolean isCandidate(TurretBaseBlockEntity turret, LivingEntity entity, StatSheet stats, WeaponType type) {
        return entity.isAlive() && !entity.isSpectator() && !entity.isInvulnerable() && !(entity instanceof TurretHitboxEntity)
                && turret.filter().test(entity, turret.owner()) && type.canTarget(entity, turret)
                && entity.distanceToSqr(turret.pivot(stats.data())) <= stats.range() * stats.range();
    }

    /**
     * PLAN 4.5 "highest threat": a mob attacking the owner or this turret first, then one hunting any player,
     * then by attack damage.
     */
    private static double threat(LivingEntity entity, TurretBaseBlockEntity turret) {
        AttributeInstance attack = entity.getAttribute(Attributes.ATTACK_DAMAGE);
        double damage = attack == null ? 0 : attack.getValue();
        if (!(entity instanceof Mob mob)) return damage;
        if (mob.getTarget() instanceof TurretHitboxEntity hitbox && turret.getBlockPos().equals(hitbox.base())) return damage + 1000;
        if (!(mob.getTarget() instanceof Player hunted)) return damage;
        return damage + (hunted.getUUID().equals(turret.owner()) ? 1000 : 100);
    }

    /** Where to shoot: the head when visible, else the body centre; null if neither is visible within pitch limits. */
    @Nullable
    public static Vec3 aimPoint(TurretBaseBlockEntity turret, LivingEntity entity, StatSheet stats) {
        return firstVisible(turret, stats, entity.getEyePosition(), entity.getBoundingBox().getCenter());
    }

    /** The first of {@code points} within the pitch limits and in line of sight from the muzzle, or null. */
    @Nullable
    public static Vec3 firstVisible(TurretBaseBlockEntity turret, StatSheet stats, Vec3... points) {
        Vec3 pivot = turret.pivot(stats.data());
        for (Vec3 point : points) {
            Vec3 toPoint = point.subtract(pivot);
            float elevation = elevation(turret.toLocal(toPoint));
            if (elevation < stats.data().aim().minPitch() || elevation > stats.data().aim().maxPitch()) continue;
            Vec3 muzzle = pivot.add(toPoint.normalize().scale(stats.data().aim().muzzleLength()));
            if (LineOfSight.clear(turret.getLevel(), muzzle, point)) return point;
        }
        return null;
    }

    /** Degrees above the horizon (Minecraft pitch is the negative of this). */
    public static float elevation(Vec3 direction) {
        return (float) (Mth.atan2(direction.y, direction.horizontalDistance()) * Mth.RAD_TO_DEG);
    }

    /** Minecraft yaw for a direction: 0 faces +Z (south), 90 faces -X (west). */
    public static float yaw(Vec3 direction) {
        return (float) (Mth.atan2(direction.z, direction.x) * Mth.RAD_TO_DEG) - 90f;
    }
}
