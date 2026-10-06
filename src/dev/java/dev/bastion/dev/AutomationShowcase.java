package dev.bastion.dev;

import com.mojang.logging.LogUtils;
import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import dev.bastion.Bastion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/** Dev-only check of the Create automation rig (-Pshowcase=automation): builds it, logs what arrives, screenshots it. */
@Mod.EventBusSubscriber(modid = Bastion.MOD_ID, value = Dist.CLIENT)
public final class AutomationShowcase {
    private static final Logger LOG = LogUtils.getLogger();
    private static final boolean ON = "automation".equals(System.getProperty("bastion.showcase"));
    private static final BlockPos ORIGIN = new BlockPos(0, 150, 0);
    private static int tick = -1;

    private AutomationShowcase() {
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
        Vec3 o = Vec3.atBottomCenterOf(ORIGIN);
        if (tick == 20) {
            Showcase.server(server, s -> {
                s.overworld().getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, s);
                s.overworld().getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, s);
                s.overworld().setDayTime(6000);
                ServerPlayer player = s.getPlayerList().getPlayers().get(0);
                player.setGameMode(GameType.CREATIVE);
                LOG.info("[showcase] arm type bastion:turret registered: {}",
                        CreateBuiltInRegistries.ARM_INTERACTION_POINT_TYPE.containsKey(Bastion.id("turret")));
                AutomationRig.build(s.overworld(), ORIGIN);
            });
        }
        if (tick == 30) {
            mc.options.hideGui = true;
            Showcase.camera(server, o.add(-6, 12, -7), o.add(4, 0, 15));
        }
        if (tick > 40 && tick % 200 == 0) Showcase.server(server, s -> AutomationRig.report(s.overworld()).forEach(l -> LOG.info("[showcase] t{} {}", tick, l)));
        if (tick == 160) Showcase.shot(mc, "a_overview");
        if (tick == 162) Showcase.camera(server, o.add(3, 3.2, -3.4), o.add(4, 0.6, 1));
        if (tick == 200) Showcase.shot(mc, "a_gun_lane");
        if (tick == 202) Showcase.camera(server, o.add(3, 3.4, 21.6), o.add(5, 0.6, 26));
        if (tick == 240) Showcase.shot(mc, "a_missile_lane");
        if (tick == 242) Showcase.camera(server, o.add(2, 3.2, 26.6), o.add(4, 0.4, 30));
        if (tick == 280) Showcase.shot(mc, "a_funnel_lane");
        if (tick == 282) Showcase.camera(server, o.add(-6, 12, -7), o.add(4, 0, 15));
        if (tick == 640) Showcase.shot(mc, "a_overview_late");
        if (tick == 650) mc.stop();
    }
}
