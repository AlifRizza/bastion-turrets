package dev.bastion.workstation;

import dev.bastion.registry.BastionBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import org.jetbrains.annotations.Nullable;

/**
 * Creative only (no recipe): pushes unlimited FE into every block next to it each tick, so workstations and the Tesla
 * Coil can be used and tested without an energy mod.
 */
public class CreativePowerSourceBlock extends BaseEntityBlock {
    public CreativePowerSourceBlock(Properties properties) {
        super(properties);
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Source(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, BastionBlockEntities.CREATIVE_POWER_SOURCE.get(), (l, pos, s, source) -> push(l, pos));
    }

    private static void push(Level level, BlockPos pos) {
        for (Direction side : Direction.values()) {
            BlockEntity neighbour = level.getBlockEntity(pos.relative(side));
            if (neighbour != null) {
                neighbour.getCapability(ForgeCapabilities.ENERGY, side.getOpposite()).ifPresent(e -> e.receiveEnergy(Integer.MAX_VALUE, false));
            }
        }
    }

    public static class Source extends BlockEntity {
        public Source(BlockPos pos, BlockState state) {
            super(BastionBlockEntities.CREATIVE_POWER_SOURCE.get(), pos, state);
        }
    }
}
