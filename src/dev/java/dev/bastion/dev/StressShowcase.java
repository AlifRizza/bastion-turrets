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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.Item;
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
 * Dev-only load test (-Pshowcase=stress [-PstressCount=N], in a scratch copy of a world): a square grid of N T3 turrets
 * (Gun, Machine Gun, Shotgun, Sniper in turn, Creative Ammo) and a ring of walking 100k HP husks that never die, so
 * every turret keeps firing. Logs average server tick time (MSPT) and FPS for three phases: empty platform, turrets
 * idle, turrets in combat. Vsync and the frame cap are off during the run so FPS shows headroom.
 */
@Mod.EventBusSubscriber(modid = Bastion.MOD_ID, value = Dist.CLIENT)
public final class StressShowcase {
    private static final Logger LOG = LogUtils.getLogger();
    private static final boolean ON = "stress".equals(System.getProperty("bastion.showcase"));
    private static final int COUNT = Integer.getInteger("bastion.stressCount", 100);
    /** Above the world spawn, in the always-loaded spawn chunks, so no terrain generates while measuring. */
    private static volatile BlockPos origin = BlockPos.ZERO;
    private static final int SPACING = 3;

    private static final List<Integer> FPS = new ArrayList<>();
    private static final List<Float> MSPT = new ArrayList<>();
    private static int tick = -1;
    private static boolean vsync;
    private static int frameLimit;

    private StressShowcase() {
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
        int side = (int) Math.ceil(Math.sqrt(COUNT));
        double half = (side - 1) * SPACING / 2.0;

        switch (tick) {
            case 20 -> Showcase.server(server, StressShowcase::buildPlatform);
            case 30 -> {
                mc.options.hideGui = true;
                vsync = mc.options.enableVsync().get();
                frameLimit = mc.options.framerateLimit().get();
                mc.options.enableVsync().set(false);
                mc.options.framerateLimit().set(260); // 260 = unlimited
                Vec3 centre = Vec3.atBottomCenterOf(origin).add(half, 0, half);
                Showcase.camera(server, centre.add(0, 18 + half * 0.6, -(half + 22)), centre);
            }
            case 300 -> report(server, "empty");
            case 302 -> Showcase.server(server, s -> placeTurrets(s, side));
            case 500 -> report(server, "turrets idle");
            case 502 -> Showcase.server(server, s -> spawnHusks(s, half));
            case 620 -> Showcase.shot(Minecraft.getInstance(), "stress_" + COUNT);
            case 800 -> report(server, "combat");
            case 802 -> {
                mc.options.enableVsync().set(vsync);
                mc.options.framerateLimit().set(frameLimit);
                mc.stop();
            }
            default -> {
            }
        }
        // Samples once a second inside each phase window (skips the first seconds after a change).
        boolean sampling = (tick > 200 && tick < 300) || (tick > 400 && tick < 500) || (tick > 600 && tick < 800);
        if (sampling && tick % 20 == 0) {
            FPS.add(mc.getFps());
            MSPT.add(server.getAverageTickTime());
        }
    }

    private static void buildPlatform(MinecraftServer server) {
        ServerLevel level = server.overworld();
        BlockPos spawn = level.getSharedSpawnPos();
        origin = new BlockPos(spawn.getX(), 150, spawn.getZ());
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
        level.setWeatherParameters(6000, 0, false, false);
        level.setDayTime(6000);
        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
        player.setGameMode(GameType.CREATIVE);
        int side = (int) Math.ceil(Math.sqrt(COUNT));
        int from = -24, to = (side - 1) * SPACING + 24;
        for (int x = from; x <= to; x++) {
            for (int z = from; z <= to; z++) {
                level.setBlock(origin.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
                for (int y = 0; y < 8; y++) level.setBlock(origin.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
            }
        }
    }

    private static void placeTurrets(MinecraftServer server, int side) {
        ServerLevel level = server.overworld();
        Item[] weapons = {BastionItems.GUN_TURRET.get(), BastionItems.MACHINE_GUN_TURRET.get(), BastionItems.SHOTGUN_TURRET.get(),
                BastionItems.SNIPER_TURRET.get()};
        for (int i = 0; i < COUNT; i++) {
            BlockPos pos = origin.offset(i % side * SPACING, 0, i / side * SPACING);
            Showcase.place(level, pos, Direction.UP, weapons[i % weapons.length], TurretTier.T3);
        }
    }

    /** One walking husk per two turrets (at least 24) on a ring outside the grid; 100k HP, so the shooting never stops. */
    private static void spawnHusks(MinecraftServer server, double half) {
        ServerLevel level = server.overworld();
        int count = Math.max(24, COUNT / 2);
        Vec3 centre = Vec3.atBottomCenterOf(origin).add(half, 0, half);
        double radius = half * Math.sqrt(2) + 10;
        for (int i = 0; i < count; i++) {
            Husk husk = EntityType.HUSK.create(level);
            if (husk == null) return;
            double angle = Math.PI * 2 * i / count;
            husk.moveTo(centre.x + Math.cos(angle) * radius, centre.y, centre.z + Math.sin(angle) * radius, 0, 0);
            husk.setPersistenceRequired();
            husk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100_000);
            husk.setHealth(100_000);
            level.addFreshEntity(husk);
        }
    }

    private static void report(MinecraftServer server, String phase) {
        // Block entities and entities are only readable on the server thread.
        List<Integer> fpsSamples = List.copyOf(FPS);
        List<Float> msptSamples = List.copyOf(MSPT);
        FPS.clear();
        MSPT.clear();
        Showcase.server(server, s -> log(s, phase, fpsSamples, msptSamples));
    }

    private static void log(MinecraftServer server, String phase, List<Integer> fpsSamples, List<Float> msptSamples) {
        double fps = fpsSamples.stream().mapToInt(Integer::intValue).average().orElse(0);
        int minFps = fpsSamples.stream().mapToInt(Integer::intValue).min().orElse(0);
        double mspt = msptSamples.stream().mapToDouble(Float::doubleValue).average().orElse(0);
        long maxTick = 0;
        for (long t : server.tickTimes) maxTick = Math.max(maxTick, t);
        int turrets = 0, entities = 0;
        ServerLevel level = server.overworld();
        for (Entity ignored : level.getAllEntities()) entities++;
        int side = (int) Math.ceil(Math.sqrt(COUNT));
        for (int i = 0; i < COUNT; i++) {
            if (level.getBlockEntity(origin.offset(i % side * SPACING, 0, i / side * SPACING)) instanceof TurretBaseBlockEntity) turrets++;
        }
        LOG.info("[stress] {} | turrets {} of {} | entities {} | MSPT avg {} max {} | FPS avg {} min {}", phase, turrets, COUNT,
                entities, String.format("%.2f", mspt), String.format("%.1f", maxTick / 1e6), String.format("%.0f", fps), minFps);
    }
}
