package dev.bastion.hub;

import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.TurretInventory;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Overwatch Link (PLAN "Overwatch Link"): a Hub Connector that also lets the player take control of the turret from the
 * hub's screen. It links and clears exactly like a Hub Connector and fits the same Connector slot.
 */
public class OverwatchLinkItem extends HubConnectorItem {
    public OverwatchLinkItem(Properties properties) {
        super(properties);
    }

    /** Whether the turret's Connector slot holds an Overwatch Link (linked or not). */
    public static boolean fitted(TurretBaseBlockEntity turret) {
        return turret.inventory().getStackInSlot(TurretInventory.CONNECTOR).getItem() instanceof OverwatchLinkItem;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.bastion.overwatch_link").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.bastion.overwatch_link.aim").withStyle(ChatFormatting.GOLD));
    }
}
