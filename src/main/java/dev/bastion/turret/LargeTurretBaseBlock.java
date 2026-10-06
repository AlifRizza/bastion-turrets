package dev.bastion.turret;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Large turret base: a 2x2x1 multiblock, floor only, for big weapon modules (Missile Launcher). The block the player
 * placed is the core: it holds the TurretBaseBlockEntity and renders the whole model; the three other blocks are
 * parts that hand clicks, shape and automation to it. Breaking any block breaks the whole base and drops it once.
 */
public class LargeTurretBaseBlock extends TurretBaseBlock {
    /** Which corner of the 2x2 this block is: bit 0 = the +X half, bit 1 = the +Z half. */
    public static final IntegerProperty QUADRANT = IntegerProperty.create("quadrant", 0, 3);
    /** The block holding the block entity (the one the player placed). */
    public static final BooleanProperty CORE = BooleanProperty.create("core");
    private static final VoxelShape ARMED = Shapes.or(Shapes.block(), Block.box(0, 16, 0, 16, 24, 16));

    public LargeTurretBaseBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP).setValue(QUADRANT, 0).setValue(CORE, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, QUADRANT, CORE);
    }

    /** Lowest-X, lowest-Z block of the footprint. */
    public static BlockPos footprintMin(BlockState state, BlockPos pos) {
        int q = state.getValue(QUADRANT);
        return pos.offset(-(q & 1), 0, -(q >> 1));
    }

    public static List<BlockPos> footprint(BlockPos min) {
        return List.of(min, min.east(), min.south(), min.south().east());
    }

    /** The point the turret turns around: the middle of the 2x2, half a block up. */
    public static Vec3 center(BlockState state, BlockPos pos) {
        BlockPos min = footprintMin(state, pos);
        return new Vec3(min.getX() + 1, min.getY() + 0.5, min.getZ() + 1);
    }

    /**
     * Floor only, clicking the top of a block: the base grows forward and to the right of the player from the
     * clicked spot, and only if all four blocks are free.
     */
    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        if (context.getClickedFace() != Direction.UP) return null;
        BlockPos pos = context.getClickedPos();
        Direction forward = context.getHorizontalDirection();
        List<BlockPos> blocks = List.of(pos, pos.relative(forward), pos.relative(forward.getClockWise()),
                pos.relative(forward).relative(forward.getClockWise()));
        Level level = context.getLevel();
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        for (BlockPos p : blocks) {
            minX = Math.min(minX, p.getX());
            minZ = Math.min(minZ, p.getZ());
            if (p.equals(pos)) continue;
            if (!level.getBlockState(p).canBeReplaced(context) || !level.isUnobstructed(defaultBlockState(), p, CollisionContext.empty())) {
                return null;
            }
        }
        int quadrant = (pos.getX() - minX) | (pos.getZ() - minZ) << 1;
        return defaultBlockState().setValue(QUADRANT, quadrant).setValue(CORE, true);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide) placeParts(level, pos, state);
    }

    /** Fills in the three part blocks around a core. */
    public static void placeParts(Level level, BlockPos core, BlockState coreState) {
        BlockPos min = footprintMin(coreState, core);
        for (BlockPos p : footprint(min)) {
            if (p.equals(core)) continue;
            int quadrant = (p.getX() - min.getX()) | (p.getZ() - min.getZ()) << 1;
            level.setBlock(p, coreState.setValue(QUADRANT, quadrant).setValue(CORE, false), Block.UPDATE_ALL);
        }
    }

    /** Places a whole base with its core at the footprint's lowest corner (tests, dev scenes). */
    public static void placeAt(Level level, BlockPos min, LargeTurretBaseBlock block) {
        BlockState core = block.defaultBlockState();
        level.setBlock(min, core, Block.UPDATE_ALL);
        placeParts(level, min, core);
    }

    @Override
    public BlockPos corePos(BlockState state, BlockGetter level, BlockPos pos) {
        if (state.getValue(CORE)) return pos;
        for (BlockPos p : footprint(footprintMin(state, pos))) {
            BlockState other = level.getBlockState(p);
            if (other.is(this) && other.getValue(CORE)) return p;
        }
        return pos;
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(CORE) ? new TurretBaseBlockEntity(pos, state) : new TurretPartBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return state.getValue(CORE) ? RenderShape.MODEL : RenderShape.INVISIBLE;
    }

    /** Every block of an armed base is solid up to the weapon, so mobs cannot stand on the launcher. */
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
            for (BlockPos p : footprint(footprintMin(state, pos))) {
                BlockState other = level.getBlockState(p);
                if (p.equals(pos) || !other.is(this)) continue;
                if (other.getValue(CORE)) level.destroyBlock(p, true);
                else if (state.getValue(CORE)) level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    /** Creative breaking of a part: drop a filled base like the small one does, then clear the core without drops. */
    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        super.playerWillDestroy(level, pos, state, player);
        if (!level.isClientSide && player.isCreative() && !state.getValue(CORE)) {
            BlockPos core = corePos(state, level, pos);
            if (!core.equals(pos)) level.setBlock(core, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
        }
    }

    // Floor-only and square: rotating or mirroring a structure only renumbers the quadrants.
    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state;
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state;
    }
}
