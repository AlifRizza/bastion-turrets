package dev.bastion.dev;

import com.mojang.logging.LogUtils;
import dev.bastion.Bastion;
import dev.bastion.registry.BastionItems;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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

/** Dev-only scene for the Sniper and Rocket Launcher (-Pshowcase=weapons): models, laser sight, rocket flight, blast. */
@Mod.EventBusSubscriber(modid = Bastion.MOD_ID, value = Dist.CLIENT)
public final class WeaponShowcase {
    private static final Logger LOG = LogUtils.getLogger();
    private static final boolean ON = "weapons".equals(System.getProperty("bastion.showcase"));
    private static final BlockPos ORIGIN = new BlockPos(0, 150, 0);
    private static final BlockPos SNIPER = ORIGIN.offset(-4, 0, 0), ROCKET = ORIGIN.offset(4, 0, 0);
    private static final List<Husk> TARGETS = new ArrayList<>();
    private static int tick = -1;

    private WeaponShowcase() {
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
        if (tick == 20) Showcase.server(server, WeaponShowcase::build);
        // Direct VFX check: the rocket explosion in open air in front of the camera, at day.
        Vec3 spot = Vec3.atBottomCenterOf(ORIGIN).add(-10, 1.5, 8);
        if (tick == 24) Showcase.camera(server, spot.add(0, 0.8, -6), spot);
        if (tick == 40) {
            int before = mc.particleEngine.countParticles().isEmpty() ? 0 : Integer.parseInt(mc.particleEngine.countParticles());
            dev.bastion.client.vfx.VfxManager.play(dev.bastion.client.vfx.VfxPresets.ROCKET_EXPLOSION, spot, new Vec3(0, 1, 0),
                    dev.bastion.client.vfx.VfxParams.of().scale(1).color(0xFFFF7A2E).blockColor(0xFF888888).seed(5));
            LOG.info("[showcase] explosion test: particles {} -> pending {}", before, mc.particleEngine.countParticles());
        }
        if (tick == 41 || tick == 43 || tick == 46 || tick == 50 || tick == 56) {
            Showcase.shot(mc, "x_test_" + (tick - 40));
            LOG.info("[showcase] t{} particles {}", tick, mc.particleEngine.countParticles());
        }
        if (tick == 22) mc.options.hideGui = true;
        if (tick == 60) {
            // In front of the launcher, just off the rockets' path: the four tubes, one emptying and reloading per shot.
            Showcase.camera(server, ROCKET.getCenter().add(-1.3, 1.5, 2.4), ROCKET.getCenter().add(0, 1.05, 0));
        }
        if (tick >= 60 && tick < 130 && tick % 3 == 0) Showcase.shot(mc, "t_tubes_" + (tick - 60) / 3);
        // Beside the rocket lane's target: the explosion, by day then by night.
        Vec3 target = Vec3.atBottomCenterOf(ORIGIN).add(5.5, 0.8, 16.5);
        if (tick == 132) Showcase.camera(server, target.add(5.5, 1.6, -4.5), target.add(0, 0.6, 0));
        if (tick >= 150 && tick < 230 && tick % 2 == 0) Showcase.shot(mc, "t_boom_" + (tick - 150) / 2);
        if (tick == 232) Showcase.server(server, s -> s.overworld().setDayTime(18000));
        if (tick >= 250 && tick < 330 && tick % 2 == 0) Showcase.shot(mc, "t_night_" + (tick - 250) / 2);
        if (tick == 340) mc.stop();
        if (tick > 20 && tick % 20 == 0) Showcase.server(server, WeaponShowcase::refill);
        if (tick > 40 && tick % 10 == 0) Showcase.server(server, s -> {
            for (BlockPos pos : new BlockPos[]{SNIPER, ROCKET}) {
                if (s.overworld().getBlockEntity(pos) instanceof dev.bastion.turret.TurretBaseBlockEntity t) {
                    LOG.info("[showcase] t{} {} state {}", tick, pos.equals(SNIPER) ? "sniper" : "rocket", t.state());
                }
            }
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
        for (int x = -16; x <= 16; x++) {
            for (int z = -10; z <= 22; z++) {
                level.setBlock(ORIGIN.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
                for (int y = 0; y < 9; y++) level.setBlock(ORIGIN.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
            }
        }
        Showcase.place(level, SNIPER, Direction.UP, BastionItems.SNIPER_TURRET.get(), TurretTier.T1);
        Showcase.place(level, ROCKET, Direction.UP, BastionItems.ROCKET_LAUNCHER_TURRET.get(), TurretTier.T1);
        refill(server);
    }

    /** Tanky frozen husks far down each lane, so rockets fly a while and the sniper keeps charging. */
    private static void refill(MinecraftServer server) {
        ServerLevel level = server.overworld();
        TARGETS.removeIf(h -> !h.isAlive());
        Vec3[] spots = {new Vec3(-2.5, 0, 16.5), new Vec3(5.5, 0, 16.5)};
        while (TARGETS.size() < spots.length) {
            Husk husk = EntityType.HUSK.create(level);
            if (husk == null) return;
            Vec3 at = Vec3.atBottomCenterOf(ORIGIN).add(spots[TARGETS.size()]);
            husk.moveTo(at.x, at.y, at.z, 180, 0);
            husk.setNoAi(true);
            husk.setPersistenceRequired();
            husk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(2000);
            husk.setHealth(2000);
            level.addFreshEntity(husk);
            TARGETS.add(husk);
            LOG.info("[showcase] husk at {}", at);
        }
    }
}
