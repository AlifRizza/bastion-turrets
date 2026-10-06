package dev.bastion.weapon;

import dev.bastion.turret.TurretBaseBlockEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Where to aim a projectile so it meets a moving target (PLAN 4.5): rockets, missiles and bullets. */
public final class LeadSolver {
    private LeadSolver() {
    }

    /**
     * Aim point for a shot flying {@code speed} blocks per tick: the weapon's lead_accuracy plus Targeting AI modules
     * close the gap to a perfect intercept.
     */
    public static Vec3 lead(TurretBaseBlockEntity turret, LivingEntity target, Vec3 point, StatSheet stats, double speed) {
        double accuracy = Math.min(1, stats.data().param("lead_accuracy") + turret.inventory().modifierEffect().leadAccuracy());
        return intercept(turret.pivot(stats.data()), point, velocity(target), speed, accuracy);
    }

    /** Blocks per tick the entity moved last tick; works for players too, whose server-side motion is unknown. */
    public static Vec3 velocity(Entity entity) {
        Vec3 v = entity.position().subtract(entity.xo, entity.yo, entity.zo);
        return entity.onGround() ? new Vec3(v.x, 0, v.z) : v; // walking mobs bob; their aim point should not
    }

    /**
     * Smallest t with |point + v t - from| = speed t, returning point + v t * accuracy; the point itself when the
     * projectile can never catch up. accuracy 1 = perfect lead, 0 = aim at where the target is now.
     */
    public static Vec3 intercept(Vec3 from, Vec3 point, Vec3 velocity, double speed, double accuracy) {
        Vec3 d = point.subtract(from);
        double a = velocity.lengthSqr() - speed * speed;
        double b = 2 * d.dot(velocity);
        double c = d.lengthSqr();
        double t;
        if (Math.abs(a) < 1e-9) {
            t = Math.abs(b) < 1e-9 ? -1 : -c / b;
        } else {
            double disc = b * b - 4 * a * c;
            if (disc < 0) return point;
            double sq = Math.sqrt(disc), t1 = (-b - sq) / (2 * a), t2 = (-b + sq) / (2 * a);
            t = Math.min(t1, t2) > 0 ? Math.min(t1, t2) : Math.max(t1, t2);
        }
        return t > 0 ? point.add(velocity.scale(t * accuracy)) : point;
    }
}
