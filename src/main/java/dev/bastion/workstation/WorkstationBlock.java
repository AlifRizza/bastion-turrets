package dev.bastion.workstation;

import dev.bastion.registry.BastionBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A workstation (PLAN Fase 9): a multiblock of {@link WorkstationType} size that grows to the right of, behind and above
 * the block the player placed, its front facing the player. That block is part 0, the core: it holds the
 * WorkstationBlockEntity and renders the whole model; the other blocks hand clicks and automation to it. Breaking any
 * block breaks the whole station and drops it once.
 */
public class WorkstationBlock extends BaseEntityBlock {
    /** Where the station's front looks. */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** Index of this block in the footprint: x + width * (z + depth * y), local axes as in {@link #offset}. */
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 7);

    private final WorkstationType type;

    public WorkstationBlock(WorkstationType type, Properties properties) {
        super(properties);
        this.type = type;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, 0));
    }

    public WorkstationType type() {
        return type;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }

    /** World offset of local (x to the right of the front, y up, z away from the front) for a station facing {@code front}. */
    public static BlockPos offset(Direction front, int x, int y, int z) {
        return BlockPos.ZERO.relative(front.getCounterClockWise(), x).above(y).relative(front.getOpposite(), z);
    }

    private BlockPos partOffset(Direction front, int part) {
        return offset(front, part % type.width, part / (type.width * type.depth), part / type.width % type.depth);
    }

    public BlockPos corePos(BlockState state, BlockPos pos) {
        return pos.subtract(partOffset(state.getValue(FACING), state.getValue(PART)));
    }

    /** Every block of the station whose core is at {@code core}, part 0 first. */
    public List<BlockPos> footprint(BlockPos core, Direction front) {
        List<BlockPos> blocks = new ArrayList<>(type.blocks());
        for (int i = 0; i < type.blocks(); i++) blocks.add(core.offset(partOffset(front, i)));
        return blocks;
    }

    /** The station behind any of its blocks, or null. */
    @Nullable
    public static WorkstationBlockEntity core(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof WorkstationBlock block)) return null;
        return level.getBlockEntity(block.corePos(state, pos)) instanceof WorkstationBlockEntity station ? station : null;
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction front = context.getHorizontalDirection().getOpposite();
        Level level = context.getLevel();
        for (BlockPos pos : footprint(context.getClickedPos(), front)) {
            if (pos.equals(context.getClickedPos())) continue;
            if (pos.getY() >= level.getMaxBuildHeight() || !level.getBlockState(pos).canBeReplaced(context)) return null;
        }
        return defaultBlockState().setValue(FACING, front).setValue(PART, 0);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide) placeParts(level, pos, state);
    }

    private void placeParts(Level level, BlockPos core, BlockState coreState) {
        List<BlockPos> blocks = footprint(core, coreState.getValue(FACING));
        for (int i = 1; i < blocks.size(); i++) level.setBlock(blocks.get(i), coreState.setValue(PART, i), Block.UPDATE_ALL);
    }

    /** Places a whole station with its core at {@code core} (tests, dev scenes). */
    public void placeAt(Level level, BlockPos core, Direction front) {
        BlockState state = defaultBlockState().setValue(FACING, front);
        level.setBlock(core, state, Block.UPDATE_ALL);
        placeParts(level, core, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        WorkstationBlockEntity station = core(level, pos);
        if (station != null && player instanceof ServerPlayer serverPlayer) {
            NetworkHooks.openScreen(serverPlayer, station, buf -> WorkstationMenu.writeOpenData(buf, station));
        }
        return InteractionResult.CONSUME;
    }

    /** One block gone means the whole station goes: a lost part breaks the core with drops; a lost core clears the parts. */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!newState.is(this) && !level.isClientSide) {
            boolean core = state.getValue(PART) == 0;
            if (core && level.getBlockEntity(pos) instanceof WorkstationBlockEntity station) {
                for (int i = 0; i < station.items().getSlots(); i++) {
                    Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, station.items().getStackInSlot(i));
                }
            }
            BlockPos corePos = corePos(state, pos);
            for (BlockPos p : footprint(corePos, state.getValue(FACING))) {
                BlockState other = level.getBlockState(p);
                if (p.equals(pos) || !other.is(this)) continue;
                if (other.getValue(PART) == 0) level.destroyBlock(p, true);
                else if (core) level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    /** Creative breaking of a part: the core goes without drops too. */
    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        super.playerWillDestroy(level, pos, state, player);
        if (!level.isClientSide && player.isCreative() && state.getValue(PART) != 0) {
            BlockPos core = corePos(state, pos);
            if (level.getBlockState(core).is(this)) level.setBlock(core, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
        }
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == 0 ? new WorkstationBlockEntity(pos, state) : new WorkstationPartBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (state.getValue(PART) != 0) return null;
        return createTickerHelper(blockEntityType, BastionBlockEntities.WORKSTATION.get(),
                level.isClientSide ? WorkstationBlockEntity::clientTick : WorkstationBlockEntity::serverTick);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return state.getValue(PART) == 0 ? RenderShape.ENTITYBLOCK_ANIMATED : RenderShape.INVISIBLE;
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
