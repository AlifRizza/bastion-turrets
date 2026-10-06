package dev.bastion.dev;

import com.mojang.logging.LogUtils;
import dev.bastion.Bastion;
import dev.bastion.registry.BastionItems;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.TurretTier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Dev-only scene for the Laser Rifle (-Pshowcase=laser, with -PshowcaseWorld set to a scratch copy of a world): the
 * charge gathering at the emitter, the beam through three husks in a row into a wall, a close-up, then by night.
 */
@Mod.EventBusSubscriber(modid = Bastion.MOD_ID, value = Dist.CLIENT)
public final class LaserShowcase {
    private static final Logger LOG = LogUtils.getLogger();
    private static final boolean ON = "laser".equals(System.getProperty("bastion.showcase"));
    private static final BlockPos ORIGIN = new BlockPos(0, 150, 0);
    private static final List<Husk> TARGETS = new ArrayList<>();
    private static int tick = -1;

    private LaserShowcase() {
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
        Vec3 base = Vec3.atBottomCenterOf(ORIGIN);
        if (tick == 20) Showcase.server(server, LaserShowcase::build);
        if (tick == 22) mc.options.hideGui = true;
        if (tick == 24) Showcase.camera(server, base.add(7, 2.6, 5), base.add(0, 1.2, 6));
        if (tick >= 40 && tick < 240 && tick % 4 == 0) Showcase.shot(mc, "l_side_" + (tick - 40) / 4);
        if (tick == 242) Showcase.camera(server, base.add(1.6, 2.1, 0.6), base.add(0, 1.45, 1.9));
        if (tick >= 250 && tick < 350 && tick % 3 == 0) Showcase.shot(mc, "l_close_" + (tick - 250) / 3);
        if (tick == 352) {
            Showcase.server(server, s -> s.overworld().setDayTime(18000));
            Showcase.camera(server, base.add(7, 2.6, 5), base.add(0, 1.2, 6));
        }
        if (tick >= 360 && tick < 460 && tick % 3 == 0) Showcase.shot(mc, "l_night_" + (tick - 360) / 3);
        if (tick == 470) mc.stop();
        if (tick > 20 && tick % 20 == 0) Showcase.server(server, LaserShowcase::refill);
        if (tick > 40 && tick % 10 == 0) Showcase.server(server, s -> {
            if (s.overworld().getBlockEntity(ORIGIN) instanceof TurretBaseBlockEntity t) LOG.info("[showcase] t{} laser {}", tick, t.state());
        });
    }

    private static void build(MinecraftServer server) {
        ServerLevel level = server.overworld();
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        level.setWeatherParameters(6000, 0, false, false);
        level.setDayTime(6000);
        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
        player.setGameMode(GameType.CREATIVE);
        player.getInventory().clearContent();
        for (int x = -10; x <= 10; x++) {
            for (int z = -8; z <= 22; z++) {
                level.setBlock(ORIGIN.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
                for (int y = 0; y < 8; y++) level.setBlock(ORIGIN.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
            }
        }
        for (int x = -3; x <= 3; x++) for (int y = 0; y < 4; y++) level.setBlock(ORIGIN.offset(x, y, 18), Blocks.STONE_BRICKS.defaultBlockState(), 2);
        Showcase.place(level, ORIGIN, Direction.UP, BastionItems.LASER_RIFLE_TURRET.get(), TurretTier.T1);
        refill(server);
    }

    /** Three frozen husks in a row down the lane, tanky enough to take every shot of the scene. */
    private static void refill(MinecraftServer server) {
        ServerLevel level = server.overworld();
        TARGETS.removeIf(h -> !h.isAlive());
        double[] lane = {6.5, 9.5, 13.5};
        while (TARGETS.size() < lane.length) {
            Husk husk = EntityType.HUSK.create(level);
            if (husk == null) return;
            Vec3 at = Vec3.atBottomCenterOf(ORIGIN).add(0, 0, lane[TARGETS.size()]);
            husk.moveTo(at.x, at.y, at.z, 180, 0);
            husk.setNoAi(true);
            husk.setPersistenceRequired();
            husk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(2000);
            husk.setHealth(2000);
            level.addFreshEntity(husk);
            TARGETS.add(husk);
        }
    }
}
