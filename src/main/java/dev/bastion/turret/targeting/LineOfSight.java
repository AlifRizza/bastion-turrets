package dev.bastion.turret.targeting;

import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Block-only ray test, PLAN 4.5: anything with a collision shape blocks the shot (walls, glass, doors). */
public final class LineOfSight {
    private LineOfSight() {
    }

    public static boolean clear(Level level, Vec3 from, Vec3 to) {
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null)).getType() == HitResult.Type.MISS;
    }
}
