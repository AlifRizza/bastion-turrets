package dev.bastion.hub;

import dev.bastion.client.render.HubBlueprint;
import dev.bastion.registry.BastionBlockEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * Hub Interface (PLAN "Turret Control Hub"): the hub's controller, in the middle of one side of the cube's middle layer,
 * facing out (at the player who placed it). It checks the layout a tick after it is placed or a part near it changes (and
 * every second), holds the hub's FE and opens its screen. STATUS mirrors the hub for its model and for clients: UNFORMED,
 * ONLINE (formed, upkeep paid), OFFLINE (formed, out of FE).
 */
public class HubInterfaceBlock extends BaseEntityBlock {
    public enum Status implements StringRepresentable {
        UNFORMED, ONLINE, OFFLINE;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<Status> STATUS = EnumProperty.create("status", Status.class);

    public HubInterfaceBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(STATUS, Status.UNFORMED));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, STATUS);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HubInterfaceBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, BastionBlockEntities.HUB_INTERFACE.get(), level.isClientSide ? HubInterfaceBlockEntity::clientTick : HubInterfaceBlockEntity::serverTick);
    }

    /** Checks a tick after placing: the block entity does not exist yet inside onPlace. */
    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moving) {
        super.onPlace(state, level, pos, oldState, moving);
        if (!oldState.is(this)) level.scheduleTick(pos, this, 1);
    }

    /** It was just placed, or a part near it changed (ControlHubStructure.notifyAround). */
    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof HubInterfaceBlockEntity hub) hub.recheck();
    }

    /**
     * A right-click opens a formed hub's screen whatever is in hand (user 2026-10-21: it needed an empty hand); an
     * unfinished hub says what is missing. Shift + right-click with an item skips this (vanilla): that places the next part
     * or links a Hub Connector.
     */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            if (state.getValue(STATUS) == Status.UNFORMED) {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> HubBlueprint.show(pos, state.getValue(FACING)));
            }
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof HubInterfaceBlockEntity hub) {
            if (!hub.formed()) hub.recheck(); // the last part may have gone in this very tick
            if (hub.formed()) {
                if (player instanceof ServerPlayer serverPlayer) hub.openMenu(serverPlayer);
            } else {
                player.displayClientMessage(ControlHubStructure.message(ControlHubStructure.check(level, pos, state.getValue(FACING))), true);
            }
        }
        return InteractionResult.CONSUME;
    }

    /** Broken: its parts and satellite stop being a hub; its FE is lost with it. */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide && state.getValue(STATUS) != Status.UNFORMED) {
            ControlHubStructure.unform(level, pos, state.getValue(FACING));
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    /** Spectators open menus through vanilla's provider path, which sends no open data: keep them out (as TurretBaseBlock). */
    @Override
    @Nullable
    public MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return null;
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.bastion.hub_interface").withStyle(ChatFormatting.GRAY));
    }
}
