package dev.bastion.workstation;

import dev.bastion.registry.BastionBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.Nullable;

/** Every non-core block of a workstation: hoppers, belts and cables on it reach the core's inputs, output and energy. */
public class WorkstationPartBlockEntity extends BlockEntity {
    public WorkstationPartBlockEntity(BlockPos pos, BlockState state) {
        super(BastionBlockEntities.WORKSTATION_PART.get(), pos, state);
    }

    @Override
    public <C> LazyOptional<C> getCapability(Capability<C> cap, @Nullable Direction side) {
        WorkstationBlockEntity core = level == null ? null : WorkstationBlock.core(level, worldPosition);
        return core != null ? core.getCapability(cap, side) : super.getCapability(cap, side);
    }
}
