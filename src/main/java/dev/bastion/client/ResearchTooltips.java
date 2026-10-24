package dev.bastion.client;

import dev.bastion.Bastion;
import dev.bastion.research.Research;
import dev.bastion.research.ResearchTree;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client side of research (PLAN 8 "Riset & Turret Codex"): a red tooltip line on locked items, and forgetting a server's state on leave. */
@Mod.EventBusSubscriber(modid = Bastion.MOD_ID, value = Dist.CLIENT)
public final class ResearchTooltips {
    private ResearchTooltips() {
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        Minecraft mc = Minecraft.getInstance();
        Item item = event.getItemStack().getItem();
        if (mc.level == null || Research.unlocked(mc.level, item)) return;
        event.getToolTip().add(Component.translatable("tooltip.bastion.not_researched", ResearchTree.unlocking(item).get(0).name())
                .withStyle(ChatFormatting.RED));
    }

    /** The next server's Codex starts from defaults until its own sync arrives, never from this world's research. */
    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        Research.acceptClient(null);
    }
}
