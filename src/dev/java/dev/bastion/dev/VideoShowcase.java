package dev.bastion.dev;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import dev.bastion.Bastion;
import dev.bastion.client.vfx.VfxManager;
import dev.bastion.client.vfx.VfxParams;
import dev.bastion.client.vfx.VfxPresets;
import dev.bastion.registry.BastionItems;
import dev.bastion.projectile.TurretRocketEntity;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.registry.BastionBlocks;
import dev.bastion.turret.LargeTurretBaseBlock;
import dev.bastion.turret.TurretInventory;
import dev.bastion.turret.TurretState;
import dev.bastion.turret.TurretTier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Dev-only silent clip for an AI audio model to score (user 10-14): -Pshowcase=video, one Rocket Launcher firing at
 * husks down its lane; -Pshowcase=video_missile, a T3 Missile Launcher in single mode firing three missiles, one at a
 * time, at husks 30+ blocks out (user 10-14: no overlapping sounds, and a long flight for the flight loop). One
 * -Pshowcase=video_laser, a Laser Rifle charging and firing twice through three husks lined up on its beam;
 * -Pshowcase=video_flame, a Flamethrower switched on for two bursts (FLAME_BURSTS) at three husks that keep burning in
 * between; -Pshowcase=video_tesla, a Tesla Coil discharging four times, each on 3000 FE handed over at TESLA_SHOTS (one
 * discharge's worth, so nothing overlaps), at three husks; -Pshowcase=video_sniper, a Sniper charging and firing three
 * times down a row of husks; -Pshowcase=video_railgun, a Railgun charging and firing twice, each on 8000 FE handed over at
 * RAIL_SHOTS; -Pshowcase=video_railgun_impact, four Railgun impacts on the floor on their own (RAIL_HITS: the game's
 * impact effect, no turret), since a real slug lands the same tick it is fired and Omni would score one sound for both;
 * -Pshowcase=video_plasma_cannon, a Plasma Cannon firing three charged balls, each on one Plasma Cell handed over at
 * PCANNON_SHOTS, at husks ~20 blocks out (the ball takes ~1 s, so shot and blast are scored apart);
 * -Pshowcase=video_repulsor, a Repulsor pushing three pairs of cave spiders around it one after another (500 FE at each
 * REP_SHOTS); -Pshowcase=video_repulsor_dome, a Repulsor Dome bursting twice (2000 FE at DOME_SHOTS) on a ring of
 * cave spiders, which the first burst throws to ~8 blocks, still in range, and the second out of it;
 * -Pshowcase=video_ion, an Ion Rifle firing five bolts, one on each 1200 FE handed over at ION_SHOTS, at three magma
 * cubes close together (the core zaps the other two); -Pshowcase=video_mortar, a Mortar lobbing two shells, one
 * handed over at each MORTAR_SHOTS, onto magma cubes ~15 blocks out (fire-proof, so the patch does not burn them). One
 * fixed camera, recorded as 1280x720 frames at ~30 fps into build/video-frames/f_<ms>.png (the name is the frame's time,
 * so a slow frame doesn't stretch the clip); ffmpeg joins them afterwards. The log gives each launch, impact and reload
 * in seconds from the first frame ("[showcase] at"), for the prompt's timings.
 */
@Mod.EventBusSubscriber(modid = Bastion.MOD_ID, value = Dist.CLIENT)
public final class VideoShowcase {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String SCENE = System.getProperty("bastion.showcase", "");
    private static final boolean ON = SCENE.equals("video") || SCENE.equals("video_missile") || SCENE.equals("video_laser")
            || SCENE.equals("video_flame") || SCENE.equals("video_tesla") || SCENE.equals("video_sniper") || SCENE.equals("video_railgun") || SCENE.equals("video_railgun_impact")
            || SCENE.equals("video_plasma_cannon") || SCENE.equals("video_repulsor") || SCENE.equals("video_repulsor_dome")
            || SCENE.equals("video_ion") || SCENE.equals("video_mortar");
    private static final boolean MISSILE = SCENE.equals("video_missile"), LASER = SCENE.equals("video_laser"),
            FLAME = SCENE.equals("video_flame"), TESLA = SCENE.equals("video_tesla"), SNIPER = SCENE.equals("video_sniper"),
            RAIL = SCENE.equals("video_railgun"), RAIL_HIT = SCENE.equals("video_railgun_impact"),
            PCANNON = SCENE.equals("video_plasma_cannon"), REPULSOR = SCENE.equals("video_repulsor"),
            DOME = SCENE.equals("video_repulsor_dome"), ION = SCENE.equals("video_ion"),
            MORTAR = SCENE.equals("video_mortar");
    private static final BlockPos ORIGIN = new BlockPos(0, 150, 0), ROCKET = ORIGIN;
    /** Ticks after the first frame when the launcher gets one missile: it loads it and fires at once. 3.3 s apart, so
     *  each flight (~1.6 s) and blast fade out before the next launch. */
    private static final int[] SHOTS = {10, 76, 142};
    /** Ticks after the first frame when the Flamethrower is switched on, off, on, off: bursts of 3.5 s and 3 s, 2 s apart. */
    private static final int[] FLAME_BURSTS = {10, 80, 120, 180};
    /** Ticks after the first frame when the Tesla Coil gets one discharge's FE: it charges 0.6 s and fires; 2.4 s apart. */
    private static final int[] TESLA_SHOTS = {10, 58, 106, 154};
    /** Ticks after the first frame when the Railgun gets one shot's FE: it charges 3 s and fires; the second comes once
     *  its 2 s cooldown is over. */
    private static final int[] RAIL_SHOTS = {10, 110};
    /** Ticks after the first frame of each staged impact, on spots 0, 1, 2, 0; 2.4 s apart so each boom dies out first. */
    private static final int[] RAIL_HITS = {12, 60, 108, 156};
    /** Ticks after the first frame when the Plasma Cannon gets one cell: it charges 1 s and fires; 3.3 s apart so each
     *  blast dies out before the next charge. */
    private static final int[] PCANNON_SHOTS = {10, 78, 146};
    /** Ticks after the first frame when the Repulsor gets one push's FE; it turns to the next pair in between. */
    private static final int[] REP_SHOTS = {10, 64, 118};
    /** Ticks after the first frame when the Repulsor Dome gets one burst's FE; past its 4 s fire_interval. */
    private static final int[] DOME_SHOTS = {10, 100};
    /** Ticks after the first frame when the Ion Rifle gets one bolt's FE; 1.8 s apart so each ring-out dies first. */
    private static final int[] ION_SHOTS = {10, 46, 82, 118, 154};
    /** Ticks after the first frame when the Mortar gets one shell; 4.5 s apart, past the flight, blast and fire patch. */
    private static final int[] MORTAR_SHOTS = {10, 100};
    private static final int RAIL_COLOR = 0xFFB57CFF; // with alpha, as TurretFireEffects passes it
    private static final Vec3[] RAIL_HIT_SPOTS = {new Vec3(0.5, 0, 6.5), new Vec3(1.5, 0, 8.5), new Vec3(-0.5, 0, 10)};
    private static final List<Mob> TARGETS = new ArrayList<>();
    private static final long FRAME_NS = 1_000_000_000L / 30;
    // ponytail: 6 writers keep up with 30 fps at 720p on the M4; a slower machine queues frames in memory.
    private static final ExecutorService WRITERS = Executors.newFixedThreadPool(6);
    private static int tick = -1;
    private static boolean recording;
    private static long start, lastFrame, captureNs;
    private static int frames, lastTubes = -1, lastMissiles, lastEnergy;
    private static TurretState lastState;
    private static boolean wasFalling;
    private static Path dir;

    private VideoShowcase() {
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
        if (tick == 20) Showcase.server(server, VideoShowcase::build);
        if (tick == 22) mc.options.hideGui = true;
        // From the side, a little behind: the launcher on the left, the husks on the right, every rocket crosses the frame.
        // The missiles arc and fly further: the same side view, further out.
        Vec3 large = Vec3.atLowerCornerOf(ORIGIN).add(1, 0, 1);
        if (tick == 24 && LASER) Showcase.camera(server, ROCKET.getCenter().add(-6, 1.2, 2.5), ROCKET.getCenter().add(0, 0.4, 5.5));
        // The Sniper from the side (user 10-16, second take): Omni scored no charge from behind the turret, where the laser
        // sight is a faint line pointing away; from here, above, it crosses the frame over the floor (over the pale sky
        // its additive glow vanishes).
        if (tick == 24 && SNIPER) Showcase.camera(server, ROCKET.getCenter().add(-6.5, 3.5, 6.5), ROCKET.getCenter().add(0, 0.2, 6.5));
        // Behind the Plasma Cannon's shoulder: the gun big in front, the husks ~20 blocks down range, so each ball flies
        // ~1 s and its shot's ring-out is over before the blast.
        if (tick == 24 && REPULSOR) Showcase.camera(server, ROCKET.getCenter().add(0.5, 4.2, -5.5), ROCKET.getCenter().add(0.5, -0.4, 1.5));
        // Close to the Dome (user 10-16: Omni scored no charge from far off): its ring spins up big in frame, the dome
        // sweeps out past the camera.
        if (tick == 24 && DOME) Showcase.camera(server, large.add(-5, 3.2, -4.5), large.add(0, 0.8, 0.5));
        // From the side and above, as the Sniper's second take: the bolt crosses the frame over the floor.
        if (tick == 24 && ION) Showcase.camera(server, ROCKET.getCenter().add(-6.5, 3.2, 5), ROCKET.getCenter().add(0, 0.3, 5));
        // From the side and far enough back to hold the arc up to ~20 blocks (its top, ~22 up, leaves the frame for a
        // moment): the fall is in view for ~1.4 s.
        if (tick == 24 && MORTAR) Showcase.camera(server, large.add(-16, 6, 8), large.add(0, 8.9, 8));
        if (tick == 24 && PCANNON) Showcase.camera(server, large.add(-4, 3.5, -3.5), large.add(0.5, 0.8, 12));
        if (tick == 24 && RAIL) Showcase.camera(server, large.add(-6.5, 3, 4.5), large.add(0.5, 1.3, 4.5));
        if (tick == 24 && RAIL_HIT) Showcase.camera(server, ROCKET.getCenter().add(-6, 2.5, 7.5), ROCKET.getCenter().add(0.5, 0.3, 8));
        if (tick == 24 && TESLA) Showcase.camera(server, large.add(-7.5, 2.6, 5), large.add(0, 1.6, 5));
        if (tick == 24 && FLAME) Showcase.camera(server, ROCKET.getCenter().add(-5.5, 1.3, 1.5), ROCKET.getCenter().add(0, 0.4, 3.6));
        if (tick == 24 && !MISSILE && !LASER && !FLAME && !TESLA && !SNIPER && !RAIL && !RAIL_HIT && !PCANNON && !REPULSOR && !DOME && !ION && !MORTAR) Showcase.camera(server, ROCKET.getCenter().add(-6.5, 1.1, 3), ROCKET.getCenter().add(0, 0.3, 5.2));
        if (tick == 24 && MISSILE) Showcase.camera(server, large.add(-16, 3.2, 15.5), large.add(0, 2.4, 15.5));
        for (int shot : SHOTS) {
            if (MISSILE && tick == 70 + shot) Showcase.server(server, s -> ((TurretBaseBlockEntity) s.overworld().getBlockEntity(ORIGIN))
                    .inventory().setStackInSlot(TurretInventory.AMMO_START, new ItemStack(BastionItems.MISSILES.get())));
        }
        for (int i = 0; i < FLAME_BURSTS.length; i++) {
            boolean on = i % 2 == 0;
            if (FLAME && tick == 70 + FLAME_BURSTS[i]) Showcase.server(server, s -> ((TurretBaseBlockEntity) s.overworld().getBlockEntity(ORIGIN)).setEnabled(on));
        }
        for (int shot : TESLA_SHOTS) {
            if (TESLA && tick == 70 + shot) Showcase.server(server, s -> s.overworld().getBlockEntity(ORIGIN).getCapability(ForgeCapabilities.ENERGY, null)
                    .ifPresent(energy -> {
                        for (int i = 0; i < 3; i++) energy.receiveEnergy(1000, false); // max_input 1000 per call
                    }));
        }
        for (int shot : RAIL_SHOTS) {
            if (RAIL && tick == 70 + shot) Showcase.server(server, s -> s.overworld().getBlockEntity(ORIGIN).getCapability(ForgeCapabilities.ENERGY, null)
                    .ifPresent(energy -> {
                        for (int i = 0; i < 4; i++) energy.receiveEnergy(2000, false); // max_input 2000 per call
                    }));
        }
        for (int shot : REP_SHOTS) {
            if (REPULSOR && tick == 70 + shot) energy(server, 500);
        }
        for (int shot : DOME_SHOTS) {
            if (DOME && tick == 70 + shot) energy(server, 2000);
        }
        for (int shot : ION_SHOTS) {
            if (ION && tick == 70 + shot) {
                energy(server, 1000); // max_input 1000 per call
                energy(server, 200);
            }
        }
        for (int shot : MORTAR_SHOTS) {
            if (MORTAR && tick == 70 + shot) Showcase.server(server, s -> ((TurretBaseBlockEntity) s.overworld().getBlockEntity(ORIGIN))
                    .inventory().setStackInSlot(TurretInventory.AMMO_START, new ItemStack(BastionItems.MORTAR_SHELLS.get())));
        }
        for (int shot : PCANNON_SHOTS) {
            if (PCANNON && tick == 70 + shot) Showcase.server(server, s -> ((TurretBaseBlockEntity) s.overworld().getBlockEntity(ORIGIN))
                    .inventory().setStackInSlot(TurretInventory.AMMO_START, new ItemStack(BastionItems.PLASMA_CELL.get())));
        }
        for (int i = 0; i < RAIL_HITS.length; i++) {
            if (RAIL_HIT && tick == 70 + RAIL_HITS[i]) impact(i % 3);
        }
        if ((MISSILE || LASER || FLAME || TESLA || SNIPER || RAIL || PCANNON || REPULSOR || DOME || ION || MORTAR) && recording) Showcase.server(server, VideoShowcase::logEvents);
        // The laser's and sniper's husks arrive half a second into the recording, so its first charge starts on camera.
        if (tick > 20 && tick % 20 == 0 && (!(LASER || SNIPER) || tick >= 80)) Showcase.server(server, VideoShowcase::refill);
        if (tick == 70) {
            dir = mc.gameDirectory.toPath().resolve("../build/video-frames").normalize();
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
            LOG.info("[showcase] recording into {}", dir);
        }
        if (tick == 70 + 20 * 14) {
            recording = false;
            WRITERS.shutdown();
            try {
                WRITERS.awaitTermination(2, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            LOG.info("[showcase] recording done: {} frames, {} ms per capture on the render thread", frames, captureNs / 1_000_000 / Math.max(frames, 1));
            mc.stop();
        }
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (!ON || !recording || event.phase != TickEvent.Phase.END) return;
        long now = System.nanoTime();
        if (now - lastFrame < FRAME_NS) return;
        lastFrame = now;
        NativeImage full = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget());
        captureNs += System.nanoTime() - now;
        frames++;
        Path file = dir.resolve(String.format("f_%06d.png", (now - start) / 1_000_000));
        // Downscale (the Retina framebuffer is 2560x1440) and encode off the render thread: on it, they cut the game to 9 fps.
        WRITERS.execute(() -> {
            try (full; NativeImage frame = new NativeImage(1280, 720, false)) {
                full.resizeSubRectTo(0, 0, full.getWidth(), full.getHeight(), frame);
                frame.writeToFile(file);
            } catch (IOException e) {
                LOG.error("[showcase] frame {}", file, e);
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
        // Wide enough that nothing from the other scenes in this world shows behind it; their leftovers go too.
        for (int x = -40; x <= 40; x++) {
            for (int z = -24; z <= 60; z++) {
                level.setBlock(ORIGIN.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
                for (int y = 0; y < 12; y++) level.setBlock(ORIGIN.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
            }
        }
        level.getEntities((Entity) null, new AABB(ORIGIN).inflate(64), e -> !(e instanceof Player)).forEach(Entity::discard);
        if (MISSILE) {
            // Single mode, empty tubes, no ammo: it aims at the husks and waits for each SHOTS missile.
            MissileShowcase.place(level, ORIGIN, TurretTier.T3, false);
            TurretBaseBlockEntity t = (TurretBaseBlockEntity) level.getBlockEntity(ORIGIN);
            t.weaponState().tubes = 0;
            t.inventory().setStackInSlot(TurretInventory.AMMO_START, ItemStack.EMPTY);
        } else if (RAIL) {
            // No energy until the first RAIL_SHOTS: it turns onto the husks and waits. Real slugs: Creative Ammo would
            // pay the FE too.
            LargeTurretBaseBlock.placeAt(level, ORIGIN, BastionBlocks.LARGE_TURRET_BASE.get());
            TurretBaseBlockEntity t = (TurretBaseBlockEntity) level.getBlockEntity(ORIGIN);
            t.inventory().setStackInSlot(TurretInventory.WEAPON, new ItemStack(BastionItems.RAILGUN_TURRET.get()));
            t.inventory().setStackInSlot(TurretInventory.AMMO_START, new ItemStack(BastionItems.RAIL_SLUGS.get(), 16));
        } else if (REPULSOR) {
            // No FE until the first REP_SHOTS (Creative Ammo would pay for the pushes).
            Showcase.place(level, ROCKET, Direction.UP, BastionItems.REPULSOR_TURRET.get(), TurretTier.T1);
            ((TurretBaseBlockEntity) level.getBlockEntity(ORIGIN)).inventory().setStackInSlot(TurretInventory.AMMO_START, ItemStack.EMPTY);
        } else if (MORTAR) {
            // No shell until the first MORTAR_SHOTS: it turns onto the cubes and waits.
            LargeTurretBaseBlock.placeAt(level, ORIGIN, BastionBlocks.LARGE_TURRET_BASE.get());
            ((TurretBaseBlockEntity) level.getBlockEntity(ORIGIN)).inventory().setStackInSlot(TurretInventory.WEAPON,
                    new ItemStack(BastionItems.MORTAR_TURRET.get()));
        } else if (ION) {
            // No FE until the first ION_SHOTS (Creative Ammo would pay for the bolts).
            Showcase.place(level, ROCKET, Direction.UP, BastionItems.ION_RIFLE_TURRET.get(), TurretTier.T1);
            ((TurretBaseBlockEntity) level.getBlockEntity(ORIGIN)).inventory().setStackInSlot(TurretInventory.AMMO_START, ItemStack.EMPTY);
        } else if (DOME) {
            LargeTurretBaseBlock.placeAt(level, ORIGIN, BastionBlocks.LARGE_TURRET_BASE.get());
            ((TurretBaseBlockEntity) level.getBlockEntity(ORIGIN)).inventory().setStackInSlot(TurretInventory.WEAPON,
                    new ItemStack(BastionItems.REPULSOR_DOME_TURRET.get()));
        } else if (PCANNON) {
            // No cell until the first PCANNON_SHOTS: it turns onto the husks and waits.
            LargeTurretBaseBlock.placeAt(level, ORIGIN, BastionBlocks.LARGE_TURRET_BASE.get());
            ((TurretBaseBlockEntity) level.getBlockEntity(ORIGIN)).inventory().setStackInSlot(TurretInventory.WEAPON,
                    new ItemStack(BastionItems.PLASMA_CANNON_TURRET.get()));
        } else if (RAIL_HIT) {
            // Nothing: the impacts are staged on the floor (impact).
        } else if (TESLA) {
            // No energy until the first TESLA_SHOTS: it holds its target and waits.
            LargeTurretBaseBlock.placeAt(level, ORIGIN, BastionBlocks.LARGE_TURRET_BASE.get());
            ((TurretBaseBlockEntity) level.getBlockEntity(ORIGIN)).inventory().setStackInSlot(TurretInventory.WEAPON,
                    new ItemStack(BastionItems.TESLA_TURRET.get()));
        } else if (FLAME) {
            // Off until the first burst: the husks arrive before the recording starts.
            Showcase.place(level, ROCKET, Direction.UP, BastionItems.FLAMETHROWER_TURRET.get(), TurretTier.T1);
            ((TurretBaseBlockEntity) level.getBlockEntity(ORIGIN)).setEnabled(false);
        } else Showcase.place(level, ROCKET, Direction.UP, (LASER ? BastionItems.LASER_RIFLE_TURRET : SNIPER ? BastionItems.SNIPER_TURRET : BastionItems.ROCKET_LAUNCHER_TURRET).get(), TurretTier.T1);
    }

    /** Launches (a tube empties), reloads (one fills) and impacts (a missile is gone); the laser's charge start and
     *  shot. In seconds from the first frame. */
    private static void logEvents(MinecraftServer server) {
        ServerLevel level = server.overworld();
        if (!(level.getBlockEntity(ORIGIN) instanceof TurretBaseBlockEntity t)) return;
        if (FLAME) {
            boolean firing = t.state() == TurretState.FIRING || t.state() == TurretState.COOLDOWN;
            boolean was = lastState == TurretState.FIRING || lastState == TurretState.COOLDOWN;
            String at = String.format("%.2f", (System.nanoTime() - start) / 1e9);
            if (firing != was && lastState != null) LOG.info("[showcase] at {} flame {}", at, firing ? "on" : "off");
            lastState = t.state();
            return;
        }
        if (MORTAR) { // launch, the top of the arc and the blast
            List<TurretRocketEntity> shells = level.getEntitiesOfClass(TurretRocketEntity.class, new AABB(ORIGIN).inflate(64));
            String at = String.format("%.2f", (System.nanoTime() - start) / 1e9);
            if (shells.size() > lastMissiles) LOG.info("[showcase] at {} launch", at);
            if (shells.size() < lastMissiles) LOG.info("[showcase] at {} blast", at);
            boolean falling = !shells.isEmpty() && shells.get(0).getDeltaMovement().y < 0;
            if (falling && !wasFalling) LOG.info("[showcase] at {} apex", at);
            wasFalling = falling;
            lastMissiles = shells.size();
            return;
        }
        if (ION) { // no charge: a shot is the capacitor dropping by a bolt's FE
            if (t.energy() < lastEnergy) LOG.info("[showcase] at {} fire", String.format("%.2f", (System.nanoTime() - start) / 1e9));
            lastEnergy = t.energy();
            return;
        }
        if (LASER || TESLA || SNIPER || RAIL || PCANNON || REPULSOR || DOME) {
            TurretState state = t.state();
            String at = String.format("%.2f", (System.nanoTime() - start) / 1e9);
            if (state == TurretState.CHARGING && lastState != TurretState.CHARGING) LOG.info("[showcase] at {} charge", at);
            if (state != TurretState.CHARGING && lastState == TurretState.CHARGING) LOG.info("[showcase] at {} fire", at);
            lastState = state;
            return;
        }
        int tubes = Integer.bitCount(t.weaponState().tubes);
        int missiles = level.getEntitiesOfClass(TurretRocketEntity.class, new AABB(ORIGIN).inflate(64)).size();
        double at = (System.nanoTime() - start) / 1e9;
        if (lastTubes >= 0 && tubes < lastTubes) LOG.info("[showcase] at {} launch x{}", String.format("%.2f", at), lastTubes - tubes);
        if (lastTubes >= 0 && tubes > lastTubes) LOG.info("[showcase] at {} reload", String.format("%.2f", at));
        if (missiles < lastMissiles) LOG.info("[showcase] at {} impact x{}", String.format("%.2f", at), lastMissiles - missiles);
        lastTubes = tubes;
        lastMissiles = missiles;
    }

    /** {@code fe} into the turret at ORIGIN, in one call (the Repulsors' max_input is one shot's worth). */
    private static void energy(MinecraftServer server, int fe) {
        Showcase.server(server, s -> s.overworld().getBlockEntity(ORIGIN).getCapability(ForgeCapabilities.ENERGY, null)
                .ifPresent(energy -> energy.receiveEnergy(fe, false)));
    }

    /** A Railgun slug landing on the floor at RAIL_HIT_SPOTS[which]: the game's block-hit effect (RailgunEffects.land:
     *  RAIL_IMPACT and RAIL_GROUND, smooth stone's map colour). No husks: Omni returned no audio at all for husks hit by
     *  an unseen gun (user 10-16), likely its safety filter. */
    private static void impact(int which) {
        Vec3 at = Vec3.atBottomCenterOf(ORIGIN).add(RAIL_HIT_SPOTS[which]), normal = new Vec3(0, 1, 0);
        VfxParams params = VfxParams.of().color(RAIL_COLOR).normal(normal).blockColor(0xFF707070).seed(which * 31L + tick);
        VfxManager.play(VfxPresets.RAIL_IMPACT, at, normal.reverse(), params);
        VfxManager.play(VfxPresets.RAIL_GROUND, at, normal.reverse(), params);
        LOG.info("[showcase] at {} impact", String.format("%.2f", (System.nanoTime() - start) / 1e9));
    }

    /** Tanky frozen husks: three 10-12 blocks down the rocket lane (each rocket flies a third of a second, lands in
     *  view), three 30-34 blocks out for the missiles' long flights, three in a row on the laser's line, three
     *  4.5-6 blocks out in the Flamethrower's cone (range 8), three 7-10 blocks from the Tesla Coil (range 16), three in
     *  a row 9-13 blocks out on the Sniper's line. */
    private static void refill(MinecraftServer server) {
        ServerLevel level = server.overworld();
        TARGETS.removeIf(h -> !h.isAlive());
        Vec3[] spots = MISSILE
                ? new Vec3[]{new Vec3(-1, 0, 30), new Vec3(2.5, 0, 32), new Vec3(0.5, 0, 34)}
                : LASER ? new Vec3[]{new Vec3(0.5, 0, 8.5), new Vec3(0.5, 0, 11), new Vec3(0.5, 0, 13.5)}
                : FLAME ? new Vec3[]{new Vec3(0, 0, 4.5), new Vec3(-0.9, 0, 6), new Vec3(0.9, 0, 6)}
                : RAIL ? new Vec3[]{new Vec3(1, 0, 7.5), new Vec3(2.5, 0, 9), new Vec3(-0.5, 0, 9.5)}
                : RAIL_HIT ? new Vec3[0]
                : REPULSOR ? new Vec3[]{new Vec3(3, 0, -0.6), new Vec3(3, 0, 0.6), new Vec3(-3, 0, -0.6), new Vec3(-3, 0, 0.6),
                        new Vec3(-0.6, 0, 3), new Vec3(0.6, 0, 3)}
                : DOME ? new Vec3[]{new Vec3(2.70, 0, 0.50), new Vec3(1.60, 0, 2.41), new Vec3(-0.60, 0, 2.41), new Vec3(-1.70, 0, 0.50), new Vec3(-0.60, 0, -1.41), new Vec3(1.60, 0, -1.41)}
                : MORTAR ? new Vec3[]{new Vec3(1.5, 0, 15), new Vec3(3, 0, 16), new Vec3(0, 0, 16.5)}
                : ION ? new Vec3[]{new Vec3(0.5, 0, 9), new Vec3(1.8, 0, 10), new Vec3(-0.8, 0, 10.5)}
                : PCANNON ? new Vec3[]{new Vec3(1, 0, 19), new Vec3(2.5, 0, 20.5), new Vec3(-0.5, 0, 21)}
                : SNIPER ? new Vec3[]{new Vec3(0.5, 0, 9), new Vec3(0.5, 0, 11), new Vec3(0.5, 0, 13)}
                : TESLA ? new Vec3[]{new Vec3(0.5, 0, 8), new Vec3(2.5, 0, 9.5), new Vec3(-1.5, 0, 10)}
                : new Vec3[]{new Vec3(-0.5, 0, 10.5), new Vec3(1.5, 0, 11.5), new Vec3(0.5, 0, 12.5)};
        while (TARGETS.size() < spots.length) {
            // Omni returned no audio at all for figures caught in a blast (railgun impact on husks, Plasma Cannon; user
            // 10-16): the Plasma Cannon and the Repulsors get other mobs instead (invisible husks still show to the spectator
            // camera).
            // Cave spiders for the Repulsors: magma cubes squash, stretch and drop flames when they land.
            Mob mob = PCANNON || ION || MORTAR ? EntityType.MAGMA_CUBE.create(level) : REPULSOR || DOME ? EntityType.CAVE_SPIDER.create(level)
                    : EntityType.HUSK.create(level);
            if (mob == null) return;
            if (mob instanceof MagmaCube cube) cube.setSize(2, true);
            Vec3 at = Vec3.atBottomCenterOf(ORIGIN).add(spots[TARGETS.size()]);
            mob.moveTo(at.x, at.y, at.z, 180, 0);
            // NoAI mobs ignore pushes (no physics): the Repulsors' spiders only lose their goals, as GameTests do.
            if (REPULSOR || DOME) mob.removeFreeWill();
            else mob.setNoAi(true);
            mob.setPersistenceRequired();
            mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(2000);
            mob.setHealth(2000);
            level.addFreshEntity(mob);
            TARGETS.add(mob);
        }
    }
}
