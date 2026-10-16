package dev.bastion.hub;

import dev.bastion.turret.FeedHubBlock;
import dev.bastion.turret.FeedHubBlock.Part;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Hub Casing (PLAN "Turret Control Hub"): the plain block of a Control Hub's 3x3x3 cube. X, Y and Z hold its place in a
 * formed hub (FeedHubBlock's parts; ALONE = in no hub), set by the hub's Interface; placing or breaking one makes the
 * Interfaces around check their layout again.
 */
public class HubCasingBlock extends Block {
    public static final EnumProperty<Part> X = FeedHubBlock.X, Y = FeedHubBlock.Y, Z = FeedHubBlock.Z;

    public HubCasingBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(X, Part.ALONE).setValue(Y, Part.ALONE).setValue(Z, Part.ALONE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(X, Y, Z);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moving) {
        super.onPlace(state, level, pos, oldState, moving);
        if (!oldState.is(this)) ControlHubStructure.notifyAround(level, pos);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        super.onRemove(state, level, pos, newState, moving);
        if (!state.is(newState.getBlock())) ControlHubStructure.notifyAround(level, pos);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.bastion." + tooltipKey()).withStyle(ChatFormatting.GRAY));
    }

    protected String tooltipKey() {
        return "hub_casing";
    }
}
