package dev.bastion.turret;

import dev.bastion.registry.BastionBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Feed Hub block: turret bases mount on its faces and it hands piped-in ammo and FE out to them (FeedHubBlockEntity).
 * Hubs next to each other merge (FeedHubStructure); X/Y/Z hold where this block sits in its structure.
 */
public class FeedHubBlock extends BaseEntityBlock {
    /** Where a block sits along one axis of its structure. */
    public enum Part implements StringRepresentable {
        ALONE, LOW, MIDDLE, HIGH;

        /** The part of the block {@code offset} blocks into a structure {@code size} long on that axis. */
        public static Part of(int offset, int size) {
            return size == 1 ? ALONE : offset == 0 ? LOW : offset == size - 1 ? HIGH : MIDDLE;
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final EnumProperty<Part> X = EnumProperty.create("x", Part.class);
    public static final EnumProperty<Part> Y = EnumProperty.create("y", Part.class);
    public static final EnumProperty<Part> Z = EnumProperty.create("z", Part.class);

    public FeedHubBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(X, Part.ALONE).setValue(Y, Part.ALONE).setValue(Z, Part.ALONE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(X, Y, Z);
    }

    public static Part part(BlockState state, Direction.Axis axis) {
        return state.getValue(axis == Direction.Axis.X ? X : axis == Direction.Axis.Y ? Y : Z);
    }

    /** Merging waits a tick (as Create's vaults do): the block entity does not exist yet inside onPlace. */
    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moving) {
        super.onPlace(state, level, pos, oldState, moving);
        if (!oldState.is(this)) level.scheduleTick(pos, this, 1);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        FeedHubStructure.reform(level, pos);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FeedHubBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, BastionBlockEntities.FEED_HUB.get(), FeedHubBlockEntity::serverTick);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** Opens the buffer; with a block in hand (a turret base to mount on this face) the click places it instead. */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.getItemInHand(hand).getItem() instanceof BlockItem) return InteractionResult.PASS;
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof FeedHubBlockEntity hub) {
            NetworkHooks.openScreen(serverPlayer, hub, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Broken: only this block's own buffer drops (its FE is lost), and the hubs around it re-form. */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (state.is(newState.getBlock())) { // only its place in the structure changed
            super.onRemove(state, level, pos, newState, moving);
            return;
        }
        if (level.getBlockEntity(pos) instanceof FeedHubBlockEntity hub) {
            for (int i = 0; i < hub.buffer().getSlots(); i++) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), hub.buffer().getStackInSlot(i));
            }
        }
        super.onRemove(state, level, pos, newState, moving);
        for (Direction side : Direction.values()) {
            if (level.getBlockState(pos.relative(side)).is(this)) level.scheduleTick(pos.relative(side), this, 1);
        }
    }
}
