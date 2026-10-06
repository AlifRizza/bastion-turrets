package dev.bastion.weapon;

import dev.bastion.damage.BastionDamageTypes;
import dev.bastion.turret.TurretHitboxEntity;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/** Instant-hit shots shared by every MVP weapon (PLAN 7): one ray per bullet or pellet. */
public final class Hitscan {
    private Hitscan() {
    }

    /**
     * Entities on the ray (nearest first, at most {@code 1 + pierce}, PLAN 4.6 Penetrator) and where the ray
     * stopped: the last entity when the pierce budget ran out, else the block or the end of range.
     */
    public record Trace(List<EntityHitResult> entities, HitResult end) {
    }

    public static Trace trace(Level level, Vec3 from, Vec3 direction, double range, Predicate<Entity> canHit, int pierce) {
        Vec3 to = from.add(direction.scale(range));
        BlockHitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null));
        Vec3 blockEnd = block.getLocation();

        List<EntityHitResult> hits = new ArrayList<>();
        for (Entity entity : level.getEntities((Entity) null, new AABB(from, blockEnd).inflate(1), canHit)) {
            Optional<Vec3> hit = entity.getBoundingBox().inflate(0.1).clip(from, blockEnd);
            hit.ifPresent(point -> hits.add(new EntityHitResult(entity, point)));
        }
        hits.sort(Comparator.comparingDouble(h -> from.distanceToSqr(h.getLocation())));
        List<EntityHitResult> taken = hits.size() > pierce + 1 ? List.copyOf(hits.subList(0, pierce + 1)) : List.copyOf(hits);
        // Out of pierce budget: the ray stops in the last target; otherwise it flies on to the block or max range.
        HitResult end = taken.size() == pierce + 1 ? taken.get(pierce) : block;
        return new Trace(taken, end);
    }

    /** First block or entity on the ray; a MISS result still carries the end point for the tracer. */
    public static HitResult trace(Level level, Vec3 from, Vec3 direction, double range, Predicate<Entity> canHit) {
        Trace trace = trace(level, from, direction, range, canHit, 0);
        return trace.entities().isEmpty() ? trace.end() : trace.entities().get(0);
    }

    /** Uniform random direction inside a cone of half-angle {@code degrees}. Same seed, same pattern on every side. */
    public static Vec3 spread(Vec3 direction, float degrees, RandomSource random) {
        if (degrees <= 0) return direction;
        double theta = Math.toRadians(degrees) * Math.sqrt(random.nextDouble());
        double phi = random.nextDouble() * Math.PI * 2;
        Vec3 axis = Math.abs(direction.y) < 0.99 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 u = direction.cross(axis).normalize();
        Vec3 v = direction.cross(u);
        Vec3 offset = u.scale(Mth.cos((float) phi)).add(v.scale(Mth.sin((float) phi)));
        return direction.scale(Math.cos(theta)).add(offset.scale(Math.sin(theta))).normalize();
    }

    /**
     * Applies turret damage. Invulnerability frames are cleared first: hitscan weapons fire faster than
     * the vanilla 10-tick window and would otherwise lose most hits.
     */
    public static boolean damage(Entity target, Level level, float amount) {
        return damage(target, BastionDamageTypes.turretShot(level), amount);
    }

    public static boolean damage(Entity target, DamageSource source, float amount) {
        target.invulnerableTime = 0;
        return target.hurt(source, amount);
    }

    /** Turret hitboxes are skipped: turrets never shoot each other, a turret in the line of fire is not a shield. */
    public static boolean canHit(Entity entity) {
        return entity.isPickable() && entity.isAlive() && !entity.isSpectator() && !(entity instanceof TurretHitboxEntity);
    }
}
