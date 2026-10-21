package dev.bastion.weapon;

import dev.bastion.turret.TurretBaseBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Shell arcs (PLAN "Overwatch Link"): the high-arc launch of weapons with shell_speed and gravity params (Mortar). The
 * server aims and fires with it and the Overwatch view draws the same arc, so the drawn path is where the shell flies.
 */
public final class Ballistics {
    private Ballistics() {
    }

    /** A launch from {@code muzzle} at {@code velocity} that comes down on the aim point after {@code ticks}. */
    public record Launch(Vec3 muzzle, Vec3 velocity, double ticks) {
        /**
         * Where the shell is {@code t} ticks after launch. TurretRocketEntity adds half the gravity before each move and half
         * after, which lands exactly on this parabola at every whole tick.
         */
        public Vec3 at(double t, double gravity) {
            return muzzle.add(velocity.scale(t)).add(0, -gravity * t * t / 2, 0);
        }
    }

    /** Launch velocity of the steep solution of the ballistic equation, or null when out of reach. */
    @Nullable
    public static Vec3 highArc(Vec3 from, Vec3 to, double speed, double gravity) {
        double dx = to.x - from.x, dz = to.z - from.z, dy = to.y - from.y, h = Math.sqrt(dx * dx + dz * dz), v2 = speed * speed;
        double disc = v2 * v2 - gravity * (gravity * h * h + 2 * dy * v2);
        if (disc < 0 || h < 1e-3) return null;
        double angle = Math.atan((v2 + Math.sqrt(disc)) / (gravity * h));
        double horizontal = Math.cos(angle) * speed;
        return new Vec3(dx / h * horizontal, Math.sin(angle) * speed, dz / h * horizontal);
    }

    /**
     * The high arc from this turret's muzzle to {@code to}, within the barrel's elevation limits, or null when none
     * reaches. The muzzle moves with the elevation, so the solve repeats from where the last one put it.
     */
    @Nullable
    public static Launch solve(TurretBaseBlockEntity turret, WeaponData data, Vec3 to) {
        double speed = data.param("shell_speed"), gravity = data.param("gravity");
        Vec3 pivot = turret.pivot(data), from = pivot, velocity = null;
        for (int i = 0; i < 3; i++) {
            velocity = highArc(from, to, speed, gravity);
            if (velocity == null) return null;
            from = pivot.add(velocity.normalize().scale(data.aim().muzzleLength()));
        }
        double elevation = Math.toDegrees(Math.atan2(velocity.y, velocity.horizontalDistance()));
        if (elevation < data.aim().minPitch() || elevation > data.aim().maxPitch()) return null;
        return new Launch(from, velocity, to.subtract(from).horizontalDistance() / Math.max(velocity.horizontalDistance(), 1e-3));
    }
}
