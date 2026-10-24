package dev.bastion.dev;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import dev.bastion.Bastion;
import dev.bastion.projectile.BroodCapsuleEntity;
import dev.bastion.projectile.JavelinMissileEntity;
import dev.bastion.registry.BastionBlocks;
import dev.bastion.registry.BastionItems;
import dev.bastion.turret.FortressTurretBaseBlock;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.TurretInventory;
import dev.bastion.unit.BroodSpiderEntity;
import dev.bastion.unit.HiveDroneEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Dev-only trailer of the three 0.5.0 Fortress turrets (-Pshowcase=trailer, with -PshowcaseWorld set to a scratch copy
 * of a world, never the user's showcase world; user 2026-10-24: a video to show friends, no sound). A grass field in the
 * sky with a Javelin, a Brood Cannon and a Hive Carrier 45 blocks apart, filmed in one take: an intro pan, each turret in
 * turn (its targets and its Creative Ammo only in its own part), then a crane up over all three, idle again. The
 * camera is the spectating player, moved every tick between keyframes (rendering eases in between); a cut also resets
 * the interpolation. Frames: 1280x720 at ~30 fps (user: small, for Discord) into build/video-frames-trailer/f_<ms>.png (named by their time, so a
 * slow frame does not stretch the clip); the log marks each part ("[trailer] at <ms> <part>") for the titles.
 */
@Mod.EventBusSubscriber(modid = Bastion.MOD_ID, value = Dist.CLIENT)
public final class TrailerShowcase {
    private static final Logger LOG = LogUtils.getLogger();
    private static final boolean ON = "trailer".equals(System.getProperty("bastion.showcase"));
    private static final BlockPos ORIGIN = new BlockPos(0, 150, 0);
    private static final BlockPos JAVELIN = ORIGIN.offset(-45, 0, 0), BROOD = ORIGIN, HIVE = ORIGIN.offset(45, 0, 0);
    /** Where each turret's targets stand (south of it). */
    private static final Vec3 JAVELIN_FIELD = at(JAVELIN).add(0, 0, 22), BROOD_FIELD = at(BROOD).add(0, 0, 20), HIVE_FIELD = at(HIVE).add(0, 0, 16);
    private static final long FRAME_NS = 1_000_000_000L / 30;
    // ponytail: 6 writers keep up with 30 fps of 720p PNGs on the M4; a slower machine queues frames in memory.
    private static final ExecutorService WRITERS = Executors.newFixedThreadPool(6);
    /** Mobs of the running part (server thread only). */
    private static final List<Entity> SPAWNED = new ArrayList<>();

    private enum Part {SETUP, INTRO, JAVELIN, BROOD, HIVE, FINALE, DONE}

    private static Part part = Part.SETUP;
    /** The part to start at the next tick (a part ends mid-tick: the next one starts clean, at its t 0). */
    private static Part next;
    private static int tick = -1, partStart, mark = -1, landed = -1, killTick = -1;
    private static int missileId = -1, capsuleId = -1;
    private static Vec3 landing = Vec3.ZERO;
    private static Move move;
    private static Vec3 lastLook = Vec3.ZERO;
    private static boolean recording;
    private static Path dir;
    private static long start, lastFrame;
    private static int frames;

    /** A camera move: from {@code from} to {@code to} over {@code length} ticks (smoothstep), looking at {@code look}. */
    private record Move(int start, int length, Vec3 from, Vec3 to, Supplier<Vec3> look, boolean cut) {
    }

    private TrailerShowcase() {
    }

    private static Vec3 at(BlockPos base) {
        return Vec3.atBottomCenterOf(base);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (!ON || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        if (mc.player == null || server == null || mc.level == null) return;
        mc.options.pauseOnLostFocus = false;
        if (mc.screen instanceof PauseScreen) mc.setScreen(null);
        tick++;
        if (tick == 20) Showcase.server(server, TrailerShowcase::build);
        if (tick == 22) mc.options.hideGui = true;
        if (tick == 30) cut(1, introEye(0), introEye(0), () -> at(JAVELIN).add(0, 1.5, 0));
        if (tick == 160) { // the field is big: its chunks need a few seconds to be drawn
            startRecording(mc);
            begin(Part.INTRO);
        }
        if (next != null) {
            part = next;
            next = null;
            partStart = tick;
            mark = landed = killTick = missileId = capsuleId = -1;
            LOG.info("[trailer] at {} {}", (System.nanoTime() - start) / 1_000_000, part.name().toLowerCase());
        }
        int t = tick - partStart;
        switch (part) {
            case INTRO -> intro(t);
            case JAVELIN -> javelin(mc, server, t);
            case BROOD -> brood(mc, server, t);
            case HIVE -> hive(mc, server, t);
            case FINALE -> finale(mc, server, t);
            default -> {
            }
        }
        camera(mc);
    }

    private static void begin(Part part) {
        next = part;
    }

    // --- the parts ----------------------------------------------------------------------------------

    /** The eye of the intro's sweep along the three turrets, {@code f} 0..1. */
    private static Vec3 introEye(double f) {
        return at(ORIGIN).add(-60 + 120 * f, 7, -16);
    }

    private static void intro(int t) {
        if (t == 0) {
            Vec3 from = introEye(0), to = introEye(1);
            move = new Move(tick, 110, from, to, () -> {
                double f = Mth.clamp((tick - partStart) / 110.0, 0, 1);
                return at(JAVELIN).lerp(at(HIVE), f * f * (3 - 2 * f)).add(0, 1.5, 0);
            }, false);
        }
        if (t == 110) begin(Part.JAVELIN);
    }

    /** Close on the silo for the launch, its climb followed up into the sky; then the target field for the dive and blast. */
    private static void javelin(Minecraft mc, MinecraftServer server, int t) {
        Vec3 silo = at(JAVELIN).add(0, 2.3, 0);
        if (t == 0) {
            Showcase.server(server, s -> {
                arm(s, JAVELIN, true);
                ServerLevel level = s.overworld();
                for (Vec3 o : new Vec3[]{new Vec3(0, 0, 0), new Vec3(-1.6, 0, 1.0), new Vec3(1.5, 0, 0.8), new Vec3(-0.6, 0, -1.6), new Vec3(1.0, 0, 2.2)}) {
                    mob(level, EntityType.HUSK, JAVELIN_FIELD.add(o), false, JAVELIN);
                }
                mob(level, EntityType.COW, JAVELIN_FIELD.add(3.2, 0, 1.5), false, JAVELIN); // in the blast, spared
            });
            cut(110, at(JAVELIN).add(5.8, 2.6, -4.6), at(JAVELIN).add(4.4, 2.1, -3.3), () -> silo);
        }
        if (mark < 0) {
            JavelinMissileEntity missile = first(mc, JavelinMissileEntity.class);
            if (missile != null) {
                mark = tick;
                missileId = missile.getId();
            } else if (t > 260) {
                end(server, Part.BROOD);
            }
            return;
        }
        int m = tick - mark;
        if (m == 14) { // the engine about to light: tilt up after it as it climbs
            moveFrom(mc, 46, at(JAVELIN).add(5.6, 1.3, -4.8), () -> {
                if (mc.level.getEntity(missileId) instanceof JavelinMissileEntity missile && missile.phase() <= JavelinMissileEntity.CLIMB) {
                    return missile.position().add(0, 0.75, 0);
                }
                return null;
            });
        }
        if (m == 64) cut(80, JAVELIN_FIELD.add(8, 2.8, -7), JAVELIN_FIELD.add(6, 2.4, -5.2), () -> JAVELIN_FIELD.add(0, 3.2, 0));
        if (m == 180) end(server, Part.BROOD);
    }

    /** The cannon lobs a capsule over the wall; close on it as it opens and the spiders go for the husks; one spider bursts. */
    private static void brood(Minecraft mc, MinecraftServer server, int t) {
        if (t == 0) {
            Showcase.server(server, s -> {
                arm(s, BROOD, true);
                ServerLevel level = s.overworld();
                for (Vec3 o : new Vec3[]{new Vec3(-3, 0, 0), new Vec3(0, 0, 1.5), new Vec3(3, 0, 0), new Vec3(-1.5, 0, 3), new Vec3(1.5, 0, 3.5), new Vec3(4, 0, 2.5)}) {
                    mob(level, EntityType.HUSK, BROOD_FIELD.add(o), true, BROOD);
                }
            });
            cut(90, at(BROOD).add(-5.8, 2.7, 1.2), at(BROOD).add(-4.8, 2.3, 2.4), () -> at(BROOD).add(0, 1.9, 1.2));
        }
        if (mark < 0) {
            BroodCapsuleEntity capsule = first(mc, BroodCapsuleEntity.class);
            if (capsule != null) {
                mark = tick;
                capsuleId = capsule.getId();
            } else if (t > 260) {
                end(server, Part.HIVE);
            }
            return;
        }
        int c = tick - mark;
        if (c == 4) cut(60, at(BROOD).add(-19, 7.5, 8), at(BROOD).add(-17, 7, 11), () -> at(BROOD).add(0, 3.5, 10.5));
        if (landed < 0) {
            Entity capsule = mc.level.getEntity(capsuleId);
            boolean still = capsule != null && capsule.getDeltaMovement().lengthSqr() < 1e-4 && c > 10;
            if (still || capsule == null && c > 10 || c > 120) {
                landed = tick;
                landing = capsule != null ? capsule.position() : BROOD_FIELD;
            }
            return;
        }
        int l = tick - landed;
        Vec3 pod = landing;
        // From the side (+x): the husks walk to the turret (-z) across the frame, not into the camera.
        if (l == 3) cut(120, pod.add(5.4, 2.3, 1.6), pod.add(4.2, 1.9, 1.0), () -> pod.add(-0.3, 0.5, -0.6));
        if (l == 80) { // the spider nearest a husk takes a fatal hit: it blinks red and bursts among them
            BroodSpiderEntity spider = mc.level.getEntitiesOfClass(BroodSpiderEntity.class, new AABB(pod, pod).inflate(16)).stream()
                    .min(Comparator.comparingDouble(sp -> nearestHusk(mc, sp.position()))).orElse(null);
            if (spider != null) {
                int id = spider.getId();
                Vec3 p = spider.position();
                killTick = tick;
                cut(70, p.add(4.4, 2.3, 1.6), p.add(3.5, 2.0, 1.2), () -> p.add(0, 0.4, 0));
                Showcase.server(server, s -> {
                    Entity e = s.overworld().getEntity(id);
                    if (e != null) e.hurt(s.overworld().damageSources().generic(), 1000);
                });
            }
        }
        if (l == 150) end(server, Part.HIVE);
    }

    /** Drones out of the hive's doorways; a wide view of them shooting; one dives kamikaze; then home in through the doorways. */
    private static void hive(Minecraft mc, MinecraftServer server, int t) {
        Vec3 hive = at(HIVE);
        if (t == 0) {
            Showcase.server(server, s -> {
                arm(s, HIVE, true);
                ServerLevel level = s.overworld();
                for (Vec3 o : new Vec3[]{new Vec3(-2.5, 0, 0), new Vec3(0, 0, 1.5), new Vec3(2.5, 0, 0.5), new Vec3(0.5, 0, -1.5)}) {
                    mob(level, EntityType.HUSK, HIVE_FIELD.add(o), false, HIVE);
                }
                mob(level, EntityType.BLAZE, HIVE_FIELD.add(-3, 6, 2), false, HIVE).setNoGravity(true);
            });
            cut(70, hive.add(4.9, 1.9, 3.7), hive.add(4.3, 1.8, 3.2), () -> hive.add(0.3, 1.75, 0.4));
        }
        if (mark < 0) {
            if (first(mc, HiveDroneEntity.class) != null) mark = tick;
            else if (t > 200) end(server, Part.FINALE);
            return;
        }
        int w = tick - mark;
        if (w == 26) moveFrom(mc, 30, hive.add(5.6, 2.4, 4.4), () -> hive.add(1.8, 5.5, 1.6)); // they climb out of shot
        if (w == 60) cut(70, HIVE_FIELD.add(16, 9, -7), HIVE_FIELD.add(13, 8, 3), () -> HIVE_FIELD.add(0, 3.5, 0));
        if (w == 130) { // one drone with a target, worn down: it dives in and bursts
            cut(65, HIVE_FIELD.add(8.5, 3.4, -6.5), HIVE_FIELD.add(7, 3.0, -5.2), () -> HIVE_FIELD.add(0, 1.2, 0));
            Showcase.server(server, s -> s.overworld().getEntitiesOfClass(HiveDroneEntity.class, new AABB(hive, hive).inflate(40)).stream()
                    .filter(d -> d.state() == HiveDroneEntity.State.ENGAGE).findFirst().ifPresent(d -> d.setHealth(d.getMaxHealth() * 0.2f)));
        }
        if (w == 195) { // what is left goes: the drones idle, then fly home
            Showcase.server(server, s -> SPAWNED.forEach(Entity::discard));
            cut(160, hive.add(-8, 5, 7), hive.add(8, 5, 7), () -> hive.add(0, 3, 0));
        }
        // The first drone on its way home: low by a doorway to see them come down in front of it and fly in.
        if (w > 195 && landed < 0 && mc.level.getEntitiesOfClass(HiveDroneEntity.class, new AABB(hive, hive).inflate(40))
                .stream().anyMatch(d -> d.state() == HiveDroneEntity.State.RETURN)) {
            landed = tick;
            cut(170, hive.add(5.2, 1.9, 4.3), hive.add(4.4, 1.8, 3.5), () -> hive.add(0.9, 1.7, 0.6));
        }
        boolean anyOut = first(mc, HiveDroneEntity.class) != null;
        if (landed > 0 && tick - landed > 60 && !anyOut || w == 520) end(server, Part.FINALE);
    }

    /** A crane up and back over the three, idle again (all three firing at once dropped the take to ~10 fps). */
    private static void finale(Minecraft mc, MinecraftServer server, int t) {
        if (t == 0) cut(150, at(ORIGIN).add(-5, 4, -12), at(ORIGIN).add(0, 20, -36), () -> at(ORIGIN).add(0, 2, 4));
        if (t == 150) {
            recording = false;
            WRITERS.shutdown();
            try {
                WRITERS.awaitTermination(3, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            LOG.info("[trailer] at {} end, {} frames", (System.nanoTime() - start) / 1_000_000, frames);
            part = Part.DONE;
            mc.stop();
        }
    }

    private static void end(MinecraftServer server, Part next) {
        Showcase.server(server, s -> {
            SPAWNED.forEach(Entity::discard);
            SPAWNED.clear();
            for (BlockPos base : List.of(JAVELIN, BROOD, HIVE)) arm(s, base, false);
            AABB scene = new AABB(ORIGIN).inflate(80, 80, 60);
            for (Class<? extends Entity> unit : List.of(BroodSpiderEntity.class, BroodCapsuleEntity.class, JavelinMissileEntity.class)) {
                s.overworld().getEntitiesOfClass(unit, scene).forEach(Entity::discard);
            }
        });
        begin(next);
    }

    private static double nearestHusk(Minecraft mc, Vec3 p) {
        return mc.level.getEntitiesOfClass(Mob.class, new AABB(p, p).inflate(8), m -> m.getType() == EntityType.HUSK).stream()
                .mapToDouble(m -> m.position().distanceToSqr(p)).min().orElse(1e9);
    }

    private static <T extends Entity> T first(Minecraft mc, Class<T> type) {
        for (Entity e : mc.level.entitiesForRendering()) if (type.isInstance(e)) return type.cast(e);
        return null;
    }

    // --- the camera ---------------------------------------------------------------------------------

    private static void cut(int length, Vec3 from, Vec3 to, Supplier<Vec3> look) {
        move = new Move(tick, length, from, to, look, true);
    }

    /** A move on from wherever the camera is now. */
    private static void moveFrom(Minecraft mc, int length, Vec3 to, Supplier<Vec3> look) {
        move = new Move(tick, length, mc.player.getEyePosition(), to, look, false);
    }

    private static void camera(Minecraft mc) {
        if (move == null) return;
        double f = Mth.clamp((tick - move.start()) / (double) move.length(), 0, 1);
        Vec3 eye = move.from().lerp(move.to(), f * f * (3 - 2 * f));
        Vec3 look = move.look().get();
        if (look == null) look = lastLook;
        lastLook = look;
        Vec3 d = look.subtract(eye);
        LocalPlayer player = mc.player;
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
        yaw = player.getYRot() + Mth.wrapDegrees(yaw - player.getYRot()); // no 360° spin between ticks
        float pitch = (float) -(Mth.atan2(d.y, d.horizontalDistance()) * Mth.RAD_TO_DEG);
        player.setPos(eye.x, eye.y - player.getEyeHeight(), eye.z);
        player.setDeltaMovement(Vec3.ZERO);
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.yHeadRot = yaw;
        if (move.cut() && tick == move.start()) { // a cut: nothing to ease from
            player.xo = player.xOld = eye.x;
            player.yo = player.yOld = eye.y - player.getEyeHeight();
            player.zo = player.zOld = eye.z;
            player.yRotO = player.yHeadRotO = yaw;
            player.xRotO = pitch;
        }
    }

    // --- the field ----------------------------------------------------------------------------------

    private static void build(MinecraftServer server) {
        ServerLevel level = server.overworld();
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_DOMOBLOOT).set(false, server);
        level.setWeatherParameters(6000, 0, false, false);
        level.setDayTime(5000);
        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
        player.getInventory().clearContent();
        player.setGameMode(GameType.SPECTATOR);
        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
        for (int x = -100; x <= 100; x++) { // wide enough that no shot sees its edge
            for (int z = -55; z <= 80; z++) {
                level.setBlock(ORIGIN.offset(x, -2, z), Blocks.DIRT.defaultBlockState(), 2);
                level.setBlock(ORIGIN.offset(x, -1, z), grass, 2);
                for (int y = 0; y < 20; y++) level.setBlock(ORIGIN.offset(x, y, z), air, 2);
            }
        }
        for (BlockPos base : List.of(JAVELIN, BROOD, HIVE)) {
            for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) level.setBlock(base.offset(x, -1, z), Blocks.POLISHED_ANDESITE.defaultBlockState(), 2);
            FortressTurretBaseBlock.placeAt(level, base, BastionBlocks.FORTRESS_TURRET_BASE.get());
        }
        // A wall the Brood Cannon lobs its capsules over.
        for (int x = -6; x <= 6; x++) for (int y = 0; y < 3; y++) level.setBlock(BROOD.offset(x, y, 10), Blocks.STONE_BRICKS.defaultBlockState(), 2);
        Item[] modules = {BastionItems.JAVELIN_TURRET.get(), BastionItems.BROOD_CANNON_TURRET.get(), BastionItems.HIVE_CARRIER_TURRET.get()};
        List<BlockPos> bases = List.of(JAVELIN, BROOD, HIVE);
        for (int i = 0; i < 3; i++) {
            if (level.getBlockEntity(bases.get(i)) instanceof TurretBaseBlockEntity turret) {
                turret.inventory().setStackInSlot(TurretInventory.WEAPON, new ItemStack(modules[i]));
            }
        }
    }

    /** Creative Ammo in (its part, or the finale) or out (the others idle). */
    private static void arm(MinecraftServer server, BlockPos base, boolean on) {
        if (server.overworld().getBlockEntity(base) instanceof TurretBaseBlockEntity turret) {
            turret.inventory().setStackInSlot(TurretInventory.AMMO_START, on ? new ItemStack(BastionItems.CREATIVE_AMMO.get()) : ItemStack.EMPTY);
        }
    }

    /** A mob at {@code at} facing the turret at {@code base}; frozen unless {@code ai}. */
    private static Mob mob(ServerLevel level, EntityType<? extends Mob> type, Vec3 at, boolean ai, BlockPos base) {
        Mob mob = type.create(level);
        Vec3 d = at(base).subtract(at);
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
        mob.moveTo(at.x, at.y, at.z, yaw, 0);
        mob.setYHeadRot(yaw);
        mob.setYBodyRot(yaw);
        mob.setNoAi(!ai);
        mob.setPersistenceRequired();
        level.addFreshEntity(mob);
        SPAWNED.add(mob);
        return mob;
    }

    // --- the recording ------------------------------------------------------------------------------

    private static void startRecording(Minecraft mc) {
        dir = mc.gameDirectory.toPath().resolve("../build/video-frames-trailer").normalize();
        try {
            Files.createDirectories(dir);
            try (var old = Files.list(dir)) {
                for (Path p : old.toList()) Files.delete(p);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        start = lastFrame = System.nanoTime();
        recording = true;
        LOG.info("[trailer] recording into {}", dir);
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (!ON || !recording || event.phase != TickEvent.Phase.END) return;
        long now = System.nanoTime();
        if (now - lastFrame < FRAME_NS) return;
        lastFrame = now;
        NativeImage full = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget());
        frames++;
        Path file = dir.resolve(String.format("f_%06d.png", (now - start) / 1_000_000));
        // Downscale and encode off the render thread: on it, they cut the game's frame rate.
        WRITERS.execute(() -> {
            try (full; NativeImage frame = new NativeImage(1280, 720, false)) {
                full.resizeSubRectTo(0, 0, full.getWidth(), full.getHeight(), frame);
                frame.writeToFile(file);
            } catch (IOException e) {
                LOG.error("[trailer] frame {}", file, e);
            }
        });
    }
}
