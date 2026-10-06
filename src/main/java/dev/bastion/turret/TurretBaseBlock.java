package dev.bastion.turret;

import dev.bastion.menu.TurretMenu;
import dev.bastion.registry.BastionBlockEntities;
import dev.bastion.registry.BastionItems;
import dev.bastion.registry.BastionSounds;
import dev.bastion.weapon.WeaponModuleItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.EnumMap;
import java.util.Map;

/**
 * Turret base block, PLAN 4.1: one block for every tier, rendered by GeckoLib. Breaking it keeps
 * tier and inventory inside the dropped item like a shulker box (loot table copy_nbt).
 */
public class TurretBaseBlock extends BaseEntityBlock {
    /** Where the base's top points (PLAN 4.1): UP on a floor, DOWN under a ceiling, the wall's normal on a wall. */
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    /**
     * Per facing: the base fills its block; with a weapon the shape grows half a block along the mount axis, so
     * aiming at the gun selects the turret and mobs cannot stand on it.
     */
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class), ARMED_SHAPES = new EnumMap<>(Direction.class);

    static {
        for (Direction facing : Direction.values()) {
            VoxelShape base = Shapes.block();
            SHAPES.put(facing, base);
            ARMED_SHAPES.put(facing, Shapes.or(base, rotated(facing, 2, 16, 2, 14, 24, 14)));
        }
    }

    /** A box given in floor-turret pixels, turned the way the model is turned for {@code facing}. */
    private static VoxelShape rotated(Direction facing, double x0, double y0, double z0, double x1, double y1, double z1) {
        Quaternionf rotation = facing.getRotation();
        Vector3f a = rotation.transform(new Vector3f((float) (x0 - 8), (float) (y0 - 8), (float) (z0 - 8)));
        Vector3f b = rotation.transform(new Vector3f((float) (x1 - 8), (float) (y1 - 8), (float) (z1 - 8)));
        return Block.box(Math.round(Math.min(a.x, b.x)) + 8, Math.round(Math.min(a.y, b.y)) + 8, Math.round(Math.min(a.z, b.z)) + 8,
                Math.round(Math.max(a.x, b.x)) + 8, Math.round(Math.max(a.y, b.y)) + 8, Math.round(Math.max(a.z, b.z)) + 8);
    }
    /** Share of max HP one repair kit restores. */
    private static final float REPAIR_FRACTION = 0.4f;

    /** Where this base's block entity lives; a multiblock part answers with its core block (LargeTurretBaseBlock). */
    public BlockPos corePos(BlockState state, BlockGetter level, BlockPos pos) {
        return pos;
    }

    /** The turret behind any block of a base, small or large, or null. */
    @Nullable
    public static TurretBaseBlockEntity core(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof TurretBaseBlock block)) return null;
        return level.getBlockEntity(block.corePos(state, level, pos)) instanceof TurretBaseBlockEntity base ? base : null;
    }

    public TurretBaseBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** Mounts on whatever face was clicked: floor, wall or ceiling. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace());
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
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TurretBaseBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, BastionBlockEntities.TURRET_BASE.get(),
                level.isClientSide ? TurretBaseBlockEntity::clientTick : TurretBaseBlockEntity::serverTick);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL; // the still base is meshed (BakedTurretBaseModel), the BER draws the moving parts
    }

    /**
     * Outline, click target and collision: an armed turret is solid up to its weapon, so mobs attack it from the
     * side instead of standing on the gun. Needs the block's dynamicShape() since it depends on the block entity.
     */
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        TurretBaseBlockEntity base = core(level, pos);
        boolean armed = base != null && !base.inventory().getStackInSlot(TurretInventory.WEAPON).isEmpty();
        return (armed ? ARMED_SHAPES : SHAPES).get(state.getValue(FACING));
    }


    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof Player player && level.getBlockEntity(pos) instanceof TurretBaseBlockEntity base) {
            base.setOwner(player.getUUID());
        }
    }

    /**
     * Repair and upgrade kits work for anyone (PLAN 4.1); the rest is owner/trusted only (PLAN 4.7).
     * A weapon module mounts; sneak + empty hand takes it off (not mid-engagement, PLAN 4.3); anything
     * else opens the GUI. The configurator handles its own clicks.
     */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        TurretBaseBlockEntity base = core(level, pos);
        if (base == null) return InteractionResult.PASS;
        ItemStack held = player.getItemInHand(hand);
        boolean hasWeapon = !base.inventory().getStackInSlot(TurretInventory.WEAPON).isEmpty();

        if (held.is(BastionItems.REPAIR_KIT.get())) {
            if (base.health() >= base.maxHealth()) return InteractionResult.PASS;
            if (level instanceof ServerLevel server) {
                base.repair(base.maxHealth() * REPAIR_FRACTION);
                if (!player.getAbilities().instabuild) held.shrink(1);
                server.playSound(null, pos, BastionSounds.TURRET_REPAIR.get(), SoundSource.BLOCKS, 1f, 1f);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        TurretTier upgrade = held.is(BastionItems.TIER_UPGRADE_KIT_T2.get()) ? TurretTier.T2
                : held.is(BastionItems.TIER_UPGRADE_KIT_T3.get()) ? TurretTier.T3 : null;
        if (upgrade != null) {
            if (!level.isClientSide) {
                if (base.upgradeTo(upgrade)) {
                    if (!player.getAbilities().instabuild) held.shrink(1);
                } else {
                    player.displayClientMessage(Component.translatable("message.bastion.turret.wrong_tier", upgrade.ordinal()), true);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (held.getItem() instanceof TurretConfiguratorItem) return InteractionResult.PASS;
        if (!base.canConfigure(player)) {
            if (!level.isClientSide) player.displayClientMessage(Component.translatable("message.bastion.turret.not_owner"), true);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (held.getItem() instanceof WeaponModuleItem module && !hasWeapon) {
            if (!base.inventory().isItemValid(TurretInventory.WEAPON, held)) {
                // Big modules need a large base and small ones a standard base.
                if (!level.isClientSide) player.displayClientMessage(Component.translatable(module.large()
                        ? "message.bastion.turret.needs_large_base" : "message.bastion.turret.needs_small_base"), true);
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            if (!level.isClientSide) {
                base.inventory().setStackInSlot(TurretInventory.WEAPON, held.copyWithCount(1));
                if (!player.getAbilities().instabuild) held.shrink(1);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (player.isSecondaryUseActive() && held.isEmpty() && hasWeapon) {
            if (!level.isClientSide) {
                if (base.state().isEngaged()) {
                    player.displayClientMessage(Component.translatable("message.bastion.turret.busy"), true);
                } else {
                    player.getInventory().placeItemBackInInventory(base.inventory().extractItem(TurretInventory.WEAPON, 1, false));
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            NetworkHooks.openScreen(serverPlayer, base, buf -> TurretMenu.writeOpenData(buf, base));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Spectators open menus through vanilla's provider path, which sends none of the open data TurretMenu needs;
     * returning null keeps them out instead of breaking their client.
     */
    @Override
    @Nullable
    public MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return null;
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        TurretBaseBlockEntity base = core(level, pos);
        return base != null ? base.comparatorLevel() : 0;
    }

    /** Creative breaking skips the loot table, so drop a filled base ourselves, like a shulker box does. */
    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        TurretBaseBlockEntity base = core(level, pos);
        if (!level.isClientSide && player.isCreative() && base != null && !base.isDefault()) {
            ItemStack stack = new ItemStack(this);
            base.saveToItem(stack);
            ItemEntity drop = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
            drop.setDefaultPickUpDelay();
            level.addFreshEntity(drop);
        }
        super.playerWillDestroy(level, pos, state, player);
    }
}
