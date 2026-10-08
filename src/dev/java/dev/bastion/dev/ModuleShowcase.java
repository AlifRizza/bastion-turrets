package dev.bastion.dev;

import dev.bastion.Bastion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/** Dev-only: module tiers in the hotbar (-Pshowcase=modules): Range, Overclock and Penetrator Mk1-Mk3, models and chips. */
@Mod.EventBusSubscriber(modid = Bastion.MOD_ID, value = Dist.CLIENT)
public final class ModuleShowcase {
    private static final boolean ON = "modules".equals(System.getProperty("bastion.showcase"));
    private static final String[] ITEMS = {"range_module", "range_module_mk2", "range_module_mk3", "overclock", "overclock_mk2",
            "overclock_mk3", "penetrator", "penetrator_mk2", "penetrator_mk3"};
    private static int tick = -1;

    private ModuleShowcase() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (!ON || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        if (mc.player == null || server == null) return;
        mc.options.pauseOnLostFocus = false;
        if (mc.screen instanceof PauseScreen) mc.setScreen(null);
        tick++;
        if (tick == 20) {
            Showcase.server(server, s -> {
                ServerPlayer player = s.getPlayerList().getPlayers().get(0);
                for (int i = 0; i < ITEMS.length; i++) {
                    player.getInventory().setItem(i, new ItemStack(ForgeRegistries.ITEMS.getValue(Bastion.id(ITEMS[i]))));
                }
                player.getInventory().selected = 2;
            });
            mc.options.hideGui = false;
        }
        if (tick == 50) Showcase.shot(mc, "modules_hotbar");
        if (tick == 55) mc.stop();
    }
}
