package dev.bastion.hub;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Hub Connector (PLAN "Turret Control Hub", "Hub Connector"): right-click a Hub Interface to link the whole stack in hand
 * to that hub; one in a turret's Connector slot makes the turret show in that hub (within its satellite's reach).
 * Shift + right-click in the air clears the link. The link is the Interface's position and dimension.
 */
public class HubConnectorItem extends Item {
    private static final String LINK = "HubLink", POS = "Pos", DIMENSION = "Dimension";

    public HubConnectorItem(Properties properties) {
        super(properties);
    }

    /** Links {@code stack} to the hub whose Interface is at {@code iface} in {@code level}; returns the stack. */
    public static ItemStack link(ItemStack stack, Level level, BlockPos iface) {
        CompoundTag link = new CompoundTag();
        link.putLong(POS, iface.asLong());
        link.putString(DIMENSION, level.dimension().location().toString());
        stack.getOrCreateTag().put(LINK, link);
        return stack;
    }

    public static boolean isLinked(ItemStack stack) {
        return stack.getTag() != null && stack.getTag().contains(LINK, Tag.TAG_COMPOUND);
    }

    /** Whether {@code stack} is a connector linked to the Interface at {@code iface} in {@code level}'s dimension. */
    public static boolean linkedTo(ItemStack stack, Level level, BlockPos iface) {
        if (!(stack.getItem() instanceof HubConnectorItem) || !isLinked(stack)) return false;
        CompoundTag link = stack.getTag().getCompound(LINK);
        return link.getLong(POS) == iface.asLong() && level.dimension().location().toString().equals(link.getString(DIMENSION));
    }

    /** Shift + right-click on a Hub Interface links the stack in hand; a plain right-click opens the hub's screen (user 2026-10-21). */
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (context.getPlayer() == null || !context.getPlayer().isSecondaryUseActive()
                || !(level.getBlockState(pos).getBlock() instanceof HubInterfaceBlock)) return InteractionResult.PASS;
        if (!level.isClientSide) {
            link(stack, level, pos);
            if (context.getPlayer() != null) {
                context.getPlayer().displayClientMessage(Component.translatable("message.bastion.hub_connector.linked", pos.getX(), pos.getY(), pos.getZ()), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Shift + right-click in the air clears the link. Vanilla also calls this after a sneaking click on a block (it skips the
     * block's own use), so a block in reach means "not in the air": a crouching player clicking a turret keeps the link.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isSecondaryUseActive() || !isLinked(stack)
                || getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE).getType() != HitResult.Type.MISS) {
            return InteractionResultHolder.pass(stack);
        }
        if (!level.isClientSide) {
            stack.removeTagKey(LINK);
            player.displayClientMessage(Component.translatable("message.bastion.hub_connector.cleared"), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        if (isLinked(stack)) {
            BlockPos pos = BlockPos.of(stack.getTag().getCompound(LINK).getLong(POS));
            tooltip.add(Component.translatable("tooltip.bastion.hub_connector.linked", pos.getX(), pos.getY(), pos.getZ()).withStyle(ChatFormatting.AQUA));
        } else {
            tooltip.add(Component.translatable("tooltip.bastion.hub_connector.unlinked").withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("tooltip.bastion.hub_connector").withStyle(ChatFormatting.GRAY));
    }
}
