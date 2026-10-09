package dev.bastion.weapon;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * Who a burst reaches (Ion Rifle field, Plasma Repeater splash): every entity {@code canHit} accepts whose box comes
 * within {@code radius} of the point, nearest first. Distance is to the box, not its centre, so a big mob counts as soon
 * as its edge is in. No line of sight: a burst on one side of a thin wall reaches the other (PLAN "Ion Rifle").
 */
public final class Splash {
    private Splash() {
    }

    public static List<LivingEntity> around(ServerLevel level, Vec3 at, double radius, Predicate<Entity> canHit, @Nullable Entity except) {
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius),
                        e -> e != except && canHit.test(e) && distance(at, e.getBoundingBox()) <= radius)
                .stream().sorted(Comparator.comparingDouble(e -> distance(at, e.getBoundingBox()))).toList();
    }

    /** Distance from {@code p} to the nearest point of {@code box}, 0 inside it. */
    public static double distance(Vec3 p, AABB box) {
        double dx = Math.max(Math.max(box.minX - p.x, 0), p.x - box.maxX);
        double dy = Math.max(Math.max(box.minY - p.y, 0), p.y - box.maxY);
        double dz = Math.max(Math.max(box.minZ - p.z, 0), p.z - box.maxZ);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
