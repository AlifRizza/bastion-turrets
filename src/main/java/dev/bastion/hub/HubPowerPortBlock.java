package dev.bastion.hub;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Hub Power Port (PLAN "Turret Control Hub"): a Hub Casing that also takes FE from cables for its hub. */
public class HubPowerPortBlock extends HubCasingBlock implements EntityBlock {
    public HubPowerPortBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HubPowerPortBlockEntity(pos, state);
    }

    @Override
    protected String tooltipKey() {
        return "hub_power_port";
    }
}
