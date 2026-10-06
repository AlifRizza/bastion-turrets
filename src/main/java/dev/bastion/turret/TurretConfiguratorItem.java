package dev.bastion.turret;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Turret Configurator (PLAN 4.7): sneak-use on a base copies its targeting settings into the tool, use on
 * another base pastes them. Both need owner/trusted access when protection is on.
 */
public class TurretConfiguratorItem extends Item {
    private static final String KEY = "Filter";

    public TurretConfiguratorItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null || !(level.getBlockEntity(context.getClickedPos()) instanceof TurretBaseBlockEntity turret)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!turret.canConfigure(player)) {
            player.displayClientMessage(Component.translatable("message.bastion.turret.not_owner"), true);
            return InteractionResult.FAIL;
        }
        ItemStack stack = context.getItemInHand();
        if (player.isSecondaryUseActive()) {
            stack.getOrCreateTag().put(KEY, turret.filter().save());
            player.displayClientMessage(Component.translatable("message.bastion.configurator.copied"), true);
        } else if (stack.getTag() != null && stack.getTag().contains(KEY)) {
            turret.setFilter(dev.bastion.turret.targeting.TargetFilter.load(stack.getTag().getCompound(KEY)));
            player.displayClientMessage(Component.translatable("message.bastion.configurator.pasted"), true);
        } else {
            player.displayClientMessage(Component.translatable("message.bastion.configurator.empty"), true);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(KEY);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.bastion.configurator").withStyle(ChatFormatting.GRAY));
    }
}
