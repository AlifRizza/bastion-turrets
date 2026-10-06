package dev.bastion.turret;

import dev.bastion.registry.BastionBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.Nullable;

/**
 * Stands in a non-core block of a large base so hoppers and pipes on any side reach the turret's ammo slots
 * (PLAN 4.2): every capability is the core's.
 */
public class TurretPartBlockEntity extends BlockEntity {
    public TurretPartBlockEntity(BlockPos pos, BlockState state) {
        super(BastionBlockEntities.TURRET_PART.get(), pos, state);
    }

    @Override
    public <C> LazyOptional<C> getCapability(Capability<C> cap, @Nullable Direction side) {
        TurretBaseBlockEntity core = level == null ? null : TurretBaseBlock.core(level, worldPosition);
        return core != null ? core.getCapability(cap, side) : super.getCapability(cap, side);
    }
}
