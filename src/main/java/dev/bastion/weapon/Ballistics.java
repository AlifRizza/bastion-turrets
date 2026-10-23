package dev.bastion.weapon;

import dev.bastion.turret.TurretBaseBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Shell arcs (PLAN "Overwatch Link"): the high-arc launch of weapons with shell_speed and gravity params (Mortar, Arrow
 * Launcher). The server aims and fires with it and the Overwatch view draws the same arc, so the drawn path is where the
 * shell flies. A {@code drag} param below 1 (Arrow Launcher: vanilla arrows keep 0.99 of their speed a tick) bends the arc.
 */
public final class Ballistics {
    /** Elevation steps of the drag solve's scan; a root pair closer than this (the very edge of reach) is missed. */
    private static final double SCAN_STEP = Math.toRadians(1);

    private Ballistics() {
    }

    /**
     * A launch from {@code muzzle} at {@code velocity} that comes down on the aim point after {@code ticks}, slowed by
     * {@code drag} each tick (1: none).
     */
    public record Launch(Vec3 muzzle, Vec3 velocity, double ticks, double drag) {
        public Launch(Vec3 muzzle, Vec3 velocity, double ticks) {
            this(muzzle, velocity, ticks, 1);
        }

        /**
         * Where the shot is {@code t} ticks after launch. Without drag: TurretRocketEntity adds half the gravity before each
         * move and half after, which lands exactly on this parabola at every whole tick. With drag, the vanilla arrow's tick
         * (move by v, then v *= drag, then v.y -= gravity) in closed form.
         */
        public Vec3 at(double t, double gravity) {
            if (drag >= 1) return muzzle.add(velocity.scale(t)).add(0, -gravity * t * t / 2, 0);
            double moved = (1 - Math.pow(drag, t)) / (1 - drag);
            return muzzle.add(velocity.scale(moved)).add(0, -gravity / (1 - drag) * (t - moved), 0);
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
     * As above for a shot slowed by {@code drag} each tick, which has no closed-form solution: the elevation is scanned
     * down from {@code maxPitch} to 45 degrees for the first one that no longer comes down short, then bisected. Null when
     * the steep arc would need more than maxPitch or none reaches. Drag 1 is the exact solution above.
     */
    @Nullable
    public static Vec3 highArc(Vec3 from, Vec3 to, double speed, double gravity, double drag, double maxPitch) {
        if (drag >= 1) return highArc(from, to, speed, gravity);
        double dx = to.x - from.x, dz = to.z - from.z, h = Math.sqrt(dx * dx + dz * dz);
        if (h < 1e-3) return null;
        double top = Math.toRadians(maxPitch), bottom = Math.toRadians(45);
        if (above(from, to, speed, gravity, drag, top) >= 0) return null;
        for (double high = top; high > bottom; high -= SCAN_STEP) {
            double low = Math.max(high - SCAN_STEP, bottom);
            if (above(from, to, speed, gravity, drag, low) < 0) continue;
            for (int i = 0; i < 40; i++) { // above(low) >= 0 > above(high)
                double mid = (low + high) / 2;
                if (above(from, to, speed, gravity, drag, mid) >= 0) low = mid;
                else high = mid;
            }
            return aimed(from, to, speed, (low + high) / 2);
        }
        return null;
    }

    /** Launch velocity toward {@code to} at {@code elevation} radians. */
    private static Vec3 aimed(Vec3 from, Vec3 to, double speed, double elevation) {
        Vec3 flat = new Vec3(to.x - from.x, 0, to.z - from.z).normalize().scale(speed * Math.cos(elevation));
        return flat.add(0, speed * Math.sin(elevation), 0);
    }

    /** How far above {@code to} a shot at {@code elevation} passes over it; -infinity when drag stops it short. */
    private static double above(Vec3 from, Vec3 to, double speed, double gravity, double drag, double elevation) {
        Vec3 velocity = aimed(from, to, speed, elevation);
        double ticks = ticks(new Vec3(to.x - from.x, 0, to.z - from.z).length(), velocity.horizontalDistance(), drag);
        return Double.isNaN(ticks) ? Double.NEGATIVE_INFINITY : new Launch(from, velocity, ticks, drag).at(ticks, gravity).y - to.y;
    }

    /** Ticks a shot moving {@code speed} blocks a tick sideways takes to cover {@code distance}; NaN when drag stops it short. */
    private static double ticks(double distance, double speed, double drag) {
        if (drag >= 1) return distance / Math.max(speed, 1e-3);
        double left = 1 - distance * (1 - drag) / speed; // drag^ticks
        return left > 0 ? Math.log(left) / Math.log(drag) : Double.NaN;
    }

    /**
     * The high arc from this turret's muzzle to {@code to}, within the barrel's elevation limits, or null when none
     * reaches. The muzzle moves with the elevation, so the solve repeats from where the last one put it.
     */
    @Nullable
    public static Launch solve(TurretBaseBlockEntity turret, WeaponData data, Vec3 to) {
        double speed = data.param("shell_speed"), gravity = data.param("gravity"), drag = data.params().getOrDefault("drag", 1f);
        Vec3 pivot = turret.pivot(data), from = pivot, velocity = null;
        for (int i = 0; i < 3; i++) {
            velocity = highArc(from, to, speed, gravity, drag, data.aim().maxPitch());
            if (velocity == null) return null;
            from = pivot.add(velocity.normalize().scale(data.aim().muzzleLength()));
        }
        double elevation = Math.toDegrees(Math.atan2(velocity.y, velocity.horizontalDistance()));
        if (elevation < data.aim().minPitch() || elevation > data.aim().maxPitch()) return null;
        double ticks = ticks(to.subtract(from).horizontalDistance(), velocity.horizontalDistance(), drag);
        return Double.isNaN(ticks) ? null : new Launch(from, velocity, ticks, drag);
    }
}
