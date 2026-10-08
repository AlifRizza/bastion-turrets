package dev.bastion.turret;

import net.minecraft.world.item.context.BlockPlaceContext;
import org.jetbrains.annotations.Nullable;

/** A block that fills more than one block when placed: Large Turret Base, multi-block workstations. */
public interface Multiblock {
    /** The blocks this click would fill, or null where it can never go (a large base on a wall) or for a 1x1 block. */
    @Nullable
    Footprint footprint(BlockPlaceContext context);
}
