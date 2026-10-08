package dev.bastion.turret;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.List;
import java.util.Set;

/**
 * Where a multiblock goes when placed with a given click (PLAN "Hologram penempatan multiblock"): every block it fills,
 * the ones in the way, and where its front looks (UP: it does not turn). Placement and the hologram both read it.
 */
public record Footprint(List<BlockPos> blocks, Set<BlockPos> blocked, Direction front) {
    public boolean placeable() {
        return blocked.isEmpty();
    }
}
