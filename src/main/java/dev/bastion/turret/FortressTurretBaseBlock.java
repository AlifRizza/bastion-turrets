package dev.bastion.turret;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;

/**
 * Fortress turret base: a 3x3x1 multiblock, floor only, for huge weapon modules (BaseKind.FORTRESS). The centre block is
 * the core: it holds the TurretBaseBlockEntity and renders the whole model; the eight others are parts that hand clicks,
 * shape and automation to it. Breaking any block breaks the whole base and drops it once.
 */
public class FortressTurretBaseBlock extends TurretBaseBlock implements Multiblock {
    /** Where in the 3x3 this block is: {@code (dx + 1) + 3 * (dz + 1)} for its offset from the core; the core is cell 4. */
    public static final IntegerProperty CELL = IntegerProperty.create("cell", 0, 8);
    /** The CELL of the core block, the 3x3's middle. */
    public static final int CORE_CELL = 4;
    private static final VoxelShape ARMED = Shapes.or(Shapes.block(), Block.box(0, 16, 0, 16, 24, 16));

    public FortressTurretBaseBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP).setValue(CELL, CORE_CELL));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, CELL);
    }

    private static BlockPos cellPos(BlockPos core, int cell) {
        return core.offset(cell % 3 - 1, 0, cell / 3 - 1);
    }

    /** The core of the Fortress this block belongs to, from its CELL alone. */
    public static BlockPos corePosOf(BlockState state, BlockPos pos) {
        int cell = state.getValue(CELL);
        return pos.offset(1 - cell % 3, 0, 1 - cell / 3);
    }

    /** The nine blocks of a Fortress around its core, core first. */
    public static List<BlockPos> footprint(BlockPos core) {
        return IntStream.of(CORE_CELL, 0, 1, 2, 3, 5, 6, 7, 8).mapToObj(cell -> cellPos(core, cell)).toList();
    }

    /**
     * Floor only, clicking the top of a block: the 3x3 centred on the clicked spot. A block is in the way when it cannot be
     * replaced or something stands in it (the centre included, so the hologram agrees with what placing allows).
     */
    @Override
    @Nullable
    public Footprint footprint(BlockPlaceContext context) {
        if (context.getClickedFace() != Direction.UP) return null;
        BlockPos pos = context.getClickedPos();
        List<BlockPos> blocks = footprint(pos);
        Level level = context.getLevel();
        Set<BlockPos> blocked = new HashSet<>();
        for (BlockPos p : blocks) {
            boolean free = p.equals(pos) ? context.canPlace() : level.getBlockState(p).canBeReplaced(context);
            if (!free || !level.isUnobstructed(defaultBlockState(), p, CollisionContext.empty())) blocked.add(p);
        }
        return new Footprint(blocks, blocked, Direction.UP);
    }

    /** Only if all nine blocks are free (footprint); the clicked block becomes the core. */
    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Footprint footprint = footprint(context);
        return footprint != null && footprint.placeable() ? defaultBlockState() : null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide) placeParts(level, pos, state);
    }

    private static void placeParts(Level level, BlockPos core, BlockState coreState) {
        for (int cell = 0; cell < 9; cell++) {
            if (cell != CORE_CELL) level.setBlock(cellPos(core, cell), coreState.setValue(CELL, cell), Block.UPDATE_ALL);
        }
    }

    /** Places a whole base around {@code core} (tests, dev scenes). */
    public static void placeAt(Level level, BlockPos core, FortressTurretBaseBlock block) {
        BlockState state = block.defaultBlockState();
        level.setBlock(core, state, Block.UPDATE_ALL);
        placeParts(level, core, state);
    }

    @Override
    public List<BlockPos> footprint(BlockState state, BlockPos pos) {
        return footprint(corePosOf(state, pos));
    }

    /** The core the cell points at, if it is there; a stray cell (a half-printed schematic) answers with itself, so it forwards nothing. */
    @Override
    public BlockPos corePos(BlockState state, BlockGetter level, BlockPos pos) {
        BlockPos core = corePosOf(state, pos);
        BlockState there = level.getBlockState(core);
        return there.is(this) && isCore(there) ? core : pos;
    }

    private static boolean isCore(BlockState state) {
        return state.getValue(CELL) == CORE_CELL;
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isCore(state) ? new TurretBaseBlockEntity(pos, state) : new TurretPartBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return isCore(state) ? RenderShape.MODEL : RenderShape.INVISIBLE;
    }

    /** Every block of an armed base is solid up to the weapon, so mobs cannot stand on it. */
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        TurretBaseBlockEntity base = core(level, pos);
        boolean armed = base != null && !base.inventory().getStackInSlot(TurretInventory.WEAPON).isEmpty();
        return armed ? ARMED : Shapes.block();
    }

    /**
     * One block gone means the whole base goes: a lost part breaks the core with its normal drops (tier and inventory
     * kept in the item); a lost core clears the parts without drops.
     */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!newState.is(this) && !level.isClientSide) {
            for (BlockPos p : footprint(corePosOf(state, pos))) {
                BlockState other = level.getBlockState(p);
                if (p.equals(pos) || !other.is(this)) continue;
                if (isCore(other)) level.destroyBlock(p, true);
                else if (isCore(state)) level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    /** Creative breaking of a part: drop a filled base like the small one does, then clear the core without drops. */
    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        super.playerWillDestroy(level, pos, state, player);
        if (!level.isClientSide && player.isCreative() && !isCore(state)) {
            BlockPos core = corePos(state, level, pos);
            if (!core.equals(pos)) level.setBlock(core, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
        }
    }

    // Floor only (FACING stays UP): turning or mirroring a structure moves each block, so its cell turns with it.
    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return turned(state, rotation::rotate);
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return turned(state, mirror::mirror);
    }

    /** The cell whose offset from the core is this one's, turned the way {@code turn} turns east and south. */
    private static BlockState turned(BlockState state, UnaryOperator<Direction> turn) {
        int cell = state.getValue(CELL);
        Vec3i offset = turn.apply(Direction.EAST).getNormal().multiply(cell % 3 - 1).offset(turn.apply(Direction.SOUTH).getNormal().multiply(cell / 3 - 1));
        return state.setValue(CELL, (offset.getX() + 1) + 3 * (offset.getZ() + 1));
    }
}
