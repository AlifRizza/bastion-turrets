package dev.bastion.dev;

import dev.bastion.Bastion;
import dev.bastion.client.screen.CodexScreen;
import dev.bastion.registry.BastionBlocks;
import dev.bastion.research.Research;
import dev.bastion.research.ResearchData;
import dev.bastion.research.ResearchNode.Tab;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Set;

/**
 * Dev-only scene for the Turret Codex (-Pshowcase=codex with -PshowcaseWorld set to a scratch copy of a world): the screen
 * before any sync (defaults), each tab, a turret page with its bars, a researchable node with its costs, a research
 * done while it is open (the card flashes cyan), and a Part Workstation's GUI with its locked rows.
 */
@Mod.EventBusSubscriber(modid = Bastion.MOD_ID, value = Dist.CLIENT)
public final class CodexShowcase {
    private static final boolean ON = "codex".equals(System.getProperty("bastion.showcase"));
    private static final BlockPos STATION = new BlockPos(0, 150, 0);
    private static int tick = -1;
    private static CodexScreen screen;

    private CodexShowcase() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (!ON || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        if (mc.player == null || server == null) return;
        mc.options.pauseOnLostFocus = false;
        if (mc.screen instanceof PauseScreen) mc.setScreen(null);
        if (tick < 0 && mc.screen != null) return; // wait for "Loading terrain" to close
        tick++;
        mc.getToasts().clear(); // recipe and advancement toasts would cover the shots' top right
        if (tick == 1) { // Review Focus 4: opened before the server's sync, from defaults
            Research.acceptClient(null);
            mc.setScreen(screen = new CodexScreen(Tab.TURRET, "gun"));
        }
        if (tick == 4) Showcase.shot(mc, "codex_defaults");
        if (tick == 10) Showcase.server(server, s -> {
            ServerPlayer player = s.getPlayerList().getPlayers().get(0);
            player.setGameMode(GameType.SURVIVAL);
            player.getInventory().clearContent();
            player.getInventory().add(new ItemStack(Items.IRON_INGOT, 16));
            player.getInventory().add(new ItemStack(Items.REDSTONE, 8));
            player.getInventory().add(new ItemStack(Items.GOLD_INGOT, 4));
            ResearchData.get(s).set(Set.of("workstations", "turret_base", "gun", "sniper", "machine_gun"));
            Research.syncAll(s);
        });
        if (tick == 20) open(mc).select("railgun");
        if (tick == 24) Showcase.shot(mc, "codex_turret");
        if (tick == 30) open(mc).select("gun");
        if (tick == 34) Showcase.shot(mc, "codex_page_gun");
        if (tick == 40) open(mc).select("control_hub");
        if (tick == 44) Showcase.shot(mc, "codex_workshop");
        if (tick == 50) open(mc).select("choke_module");
        if (tick == 54) Showcase.shot(mc, "codex_modules");
        if (tick == 60) open(mc).select("laser_rifle");
        if (tick == 64) Showcase.shot(mc, "codex_available");
        if (tick == 70) Showcase.server(server, s -> Research.research(s.getPlayerList().getPlayers().get(0), "laser_rifle"));
        if (tick == 73) Showcase.shot(mc, "codex_flash");
        if (tick == 80) mc.setScreen(null);
        // A Part Workstation's GUI: its first tab (the Brood Cannon's parts) is locked: dimmed rows, icons too, padlocks.
        if (tick == 82) Showcase.server(server, s -> {
            s.getPlayerList().getPlayers().get(0).setGameMode(GameType.CREATIVE);
            BastionBlocks.PART_WORKSTATION.get().placeAt(s.overworld(), STATION, Direction.NORTH);
        });
        if (tick == 86) Showcase.camera(server, Vec3.atLowerCornerOf(STATION).add(0.5, 1.5, -1.6), Vec3.atLowerCornerOf(STATION).add(0.5, 0.6, 0.5));
        if (tick == 96 && mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        }
        if (tick == 108) Showcase.shot(mc, "codex_station_locked");
        // The tab row scrolled to its end with the wheel (21 tabs, 11 shown between the arrows).
        if (tick == 110 && mc.screen != null) {
            for (int i = 0; i < 20; i++) mc.screen.mouseScrolled(mc.screen.width / 2.0, (mc.screen.height - 236) / 2.0 + 23, -1);
        }
        if (tick == 114) Showcase.shot(mc, "codex_station_tabs_end");
        if (tick == 118) mc.setScreen(null);
        if (tick == 126) mc.stop();
    }

    /** The scene's Codex, reopened if anything closed it. */
    private static CodexScreen open(Minecraft mc) {
        if (mc.screen != screen) mc.setScreen(screen);
        return screen;
    }
}
