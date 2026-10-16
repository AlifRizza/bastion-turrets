package dev.bastion.hub;

import dev.bastion.config.BastionConfig;
import dev.bastion.hub.HubInterfaceBlock.Status;
import dev.bastion.registry.BastionBlockEntities;
import dev.bastion.turret.Footprint;
import dev.bastion.turret.Multiblock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Satellite Uplink T1-T3 (PLAN "Turret Control Hub"): a mast with a turning dish, two blocks tall, standing on the middle
 * block of a Control Hub; its tier sets the hub's reach. Placed like a workstation (Multiblock: the placement hologram
 * shows both blocks); the bottom block (PART 0) holds the block entity and draws the model, breaking either half breaks
 * both and drops the item once. STATUS is set by the hub's Interface (dish folded, turning or stopped).
 */
public class SatelliteUplinkBlock extends BaseEntityBlock implements Multiblock {
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 1);
    public static final EnumProperty<Status> STATUS = HubInterfaceBlock.STATUS;
    private static final VoxelShape MAST = Block.box(5, 0, 5, 11, 16, 11), HEAD = Block.box(3, 0, 3, 13, 14, 13);
    private final int tier;

    public SatelliteUplinkBlock(int tier, Properties properties) {
        super(properties);
        this.tier = tier;
        registerDefaultState(stateDefinition.any().setValue(PART, 0).setValue(STATUS, Status.UNFORMED));
    }

    public int tier() {
        return tier;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART, STATUS);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(PART) == 0 ? MAST : HEAD;
    }

    /** The clicked block and the one above it; never into a mob. Front UP: the hologram does not turn it. */
    @Override
    public Footprint footprint(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos(), top = pos.above();
        Set<BlockPos> blocked = new HashSet<>();
        if (!context.canPlace()) blocked.add(pos);
        if (top.getY() >= level.getMaxBuildHeight() || !level.getBlockState(top).canBeReplaced(context)
                || !level.isUnobstructed(defaultBlockState(), top, CollisionContext.empty())) blocked.add(top);
        return new Footprint(List.of(pos, top), blocked, Direction.UP);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return footprint(context).placeable() ? defaultBlockState() : null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide) level.setBlock(pos.above(), state.setValue(PART, 1), Block.UPDATE_ALL);
    }

    /** A whole satellite with its bottom at {@code bottom} (tests, dev scenes). */
    public void placeAt(Level level, BlockPos bottom) {
        level.setBlock(bottom, defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(bottom.above(), defaultBlockState().setValue(PART, 1), Block.UPDATE_ALL);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moving) {
        super.onPlace(state, level, pos, oldState, moving);
        if (!oldState.is(this) && state.getValue(PART) == 0) ControlHubStructure.notifyAround(level, pos);
    }

    /** One half gone takes the other (only the bottom drops the item, see its loot table) and tells the hub. */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!newState.is(this) && !level.isClientSide) {
            boolean bottom = state.getValue(PART) == 0;
            BlockPos other = bottom ? pos.above() : pos.below();
            BlockState otherState = level.getBlockState(other);
            if (otherState.is(this) && otherState.getValue(PART) != state.getValue(PART)) {
                if (bottom) level.setBlock(other, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                else level.destroyBlock(other, true);
            }
            ControlHubStructure.notifyAround(level, bottom ? pos : pos.below());
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    /** Creative breaking of the top half: the bottom goes without drops too. */
    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        super.playerWillDestroy(level, pos, state, player);
        if (!level.isClientSide && player.isCreative() && state.getValue(PART) == 1 && level.getBlockState(pos.below()).is(this)) {
            level.setBlock(pos.below(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
        }
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == 0 ? new SatelliteUplinkBlockEntity(pos, state) : null;
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide && state.getValue(PART) == 0
                ? createTickerHelper(type, BastionBlockEntities.SATELLITE_UPLINK.get(), SatelliteUplinkBlockEntity::clientTick) : null;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return state.getValue(PART) == 0 ? RenderShape.ENTITYBLOCK_ANIMATED : RenderShape.INVISIBLE;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.bastion.satellite_uplink", BastionConfig.controlHubRange(tier)).withStyle(ChatFormatting.GRAY));
    }
}
