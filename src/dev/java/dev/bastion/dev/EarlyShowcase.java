package dev.bastion.dev;

import com.mojang.logging.LogUtils;
import dev.bastion.Bastion;
import dev.bastion.client.ClientTurret;
import dev.bastion.projectile.TurretArrowEntity;
import dev.bastion.registry.BastionItems;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.TurretInventory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Dev-only scene for the early game (-Pshowcase=early in a scratch world): makeshift bases on the floor, a wall and a
 * ceiling, a row losing HP (70 / 30 / 15 %: crack levels 0, 1, 2), by day and night; then the four early turrets on floor
 * bases shooting frozen husks (night, day, turning onto husks that step aside, a burst of frames from each turret's next
 * shot and a side view); then their shots in flight and where they land, at night and by day (the Arrow Launcher also
 * with tipped and spectral arrows); the most damaged base destroyed; then the items and the screen.
 */
@Mod.EventBusSubscriber(modid = Bastion.MOD_ID, value = Dist.CLIENT)
public final class EarlyShowcase {
    private static final Logger LOG = LogUtils.getLogger();
    private static final boolean ON = "early".equals(System.getProperty("bastion.showcase"));
    private static final BlockPos ORIGIN = new BlockPos(0, 150, 0);
    private static final BlockPos FLOOR = ORIGIN, WALL = ORIGIN.offset(-4, 1, 0), CEILING = ORIGIN.offset(4, 2, 0);
    /** The damage row behind, and the HP each base is left with. */
    private static final BlockPos[] DAMAGED = {ORIGIN.offset(-3, 0, 4), ORIGIN.offset(0, 0, 4), ORIGIN.offset(3, 0, 4)};
    private static final float[] HEALTH = {0.7f, 0.3f, 0.15f};
    /** The turret row (Arrow Launcher last: floor bases only) and each one's husk (the Arrow Launcher's past its min range). */
    private static final BlockPos[] TURRETS = {ORIGIN.offset(-6, 0, 10), ORIGIN.offset(-2, 0, 10), ORIGIN.offset(2, 0, 10), ORIGIN.offset(6, 0, 10)};
    private static final String[] NAMES = {"cobblestone", "snowball", "potato", "arrow"};
    private static final Vec3[] HUSKS = {new Vec3(-5.5, 0, 18.5), new Vec3(-1.5, 0, 18.5), new Vec3(2.5, 0, 17.5), new Vec3(6.5, 0, 24.5)};
    /** How far the husks step aside so the turrets turn after them. */
    private static final double STEP = 4;
    private static final Husk[] TARGETS = new Husk[HUSKS.length];
    /** Close-ups start here: one turret after another, each waits for its next shot. */
    private static final int CLOSE_UPS = 400;
    /**
     * The flight passes: turret (index into TURRETS), night or day, and the Arrow Launcher's ammo. Night first: lit, not
     * glowing, so the shots go dark with the world.
     */
    private record Flight(int turret, boolean night, String ammo) {
    }

    private static final Flight[] FLIGHTS = {new Flight(0, true, "plain"), new Flight(1, true, "plain"), new Flight(2, true, "plain"),
            new Flight(3, true, "plain"), new Flight(0, false, "plain"), new Flight(1, false, "plain"), new Flight(2, false, "plain"),
            new Flight(3, false, "tipped"), new Flight(3, false, "spectral")};
    private static int tick = -1, turret = 0, flight = 0, mark = -1, shotAt = -1, landed = -1;
    private static boolean wrecked;
    private static long seenShot;

    private EarlyShowcase() {
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
        Vec3 scene = Vec3.atBottomCenterOf(ORIGIN).add(0, 0.8, 2);
        Vec3 row = Vec3.atBottomCenterOf(DAMAGED[1]).add(0, 0.5, 0);
        Vec3 rowEye = Vec3.atBottomCenterOf(ORIGIN).add(-8.5, 2.8, 15.5), rowAt = Vec3.atBottomCenterOf(ORIGIN).add(0, 0.7, 10);
        switch (tick) {
            case 20 -> Showcase.server(server, EarlyShowcase::build);
            case 30 -> {
                mc.options.hideGui = true;
                Showcase.camera(server, scene.add(0, 2.2, -7.5), scene);
            }
            case 90 -> Showcase.shot(mc, "early_overview_day");
            case 92 -> Showcase.camera(server, FLOOR.getCenter().add(1.3, 0.9, -1.9), FLOOR.getCenter().add(0, -0.1, 0));
            case 120 -> Showcase.shot(mc, "early_base_day");
            case 122 -> {
                for (BlockPos pos : DAMAGED) {
                    if (mc.level.getBlockEntity(pos) instanceof TurretBaseBlockEntity base) LOG.info("[showcase] client sees {} at {} HP", pos, base.clientHealth());
                }
                Showcase.camera(server, row.add(1.2, 1.4, -3.6), row);
            }
            case 150 -> Showcase.shot(mc, "early_cracks_day");
            case 152 -> Showcase.camera(server, ORIGIN.getCenter().add(0, 1.4, -4.2), ORIGIN.getCenter().add(0, 1.2, 0));
            case 180 -> Showcase.shot(mc, "early_mounts_day");
            case 182 -> {
                Showcase.server(server, s -> s.overworld().setDayTime(18000));
                Showcase.camera(server, scene.add(0, 2.2, -7.5), scene);
            }
            case 220 -> Showcase.shot(mc, "early_overview_night");
            case 222 -> Showcase.camera(server, row.add(1.2, 1.4, -3.6), row);
            case 250 -> Showcase.shot(mc, "early_cracks_night");
            // The turrets: at night (no glow anywhere), by day, then turning after husks that step aside.
            case 252 -> Showcase.camera(server, rowEye, rowAt);
            case 290 -> Showcase.shot(mc, "early_turrets_night");
            case 292 -> Showcase.camera(server, TURRETS[1].getCenter().add(-1.7, 1.2, 2.0), TURRETS[1].getCenter().add(0, 0.9, 0));
            case 320 -> Showcase.shot(mc, "early_snowball_night");
            case 322 -> {
                Showcase.server(server, s -> s.overworld().setDayTime(6000));
                Showcase.camera(server, rowEye, rowAt);
            }
            case 360 -> Showcase.shot(mc, "early_turrets_day");
            case 362 -> Showcase.server(server, s -> step(STEP));
            case 364, 369, 374, 379, 384, 389 -> Showcase.shot(mc, "early_turn_" + (tick - 364) / 5);
            case 392 -> Showcase.server(server, s -> step(-STEP));
            default -> {
            }
        }
        if (tick >= CLOSE_UPS && turret < TURRETS.length) closeUps(mc, server);
        else if (turret == TURRETS.length && flight < FLIGHTS.length) flights(mc, server);
        else if (flight == FLIGHTS.length && !wrecked) wreck(mc, server);
        else if (wrecked) items(mc, server);
        if (tick > 4800) mc.stop(); // never hang if a turret does not fire
    }

    /**
     * For each turret in turn: a front three-quarter view, then six frames two ticks apart from its next shot (the fire clip:
     * spring, drum, pump, string), then a side view from the weapon's left (the Snowball Repeater's gear).
     */
    private static void closeUps(Minecraft mc, MinecraftServer server) {
        Vec3 centre = TURRETS[turret].getCenter();
        boolean arrow = turret == 3; // pitched up with a wide bow: from further out
        if (mark < 0) {
            Showcase.camera(server, centre.add(arrow ? -2.6 : -1.7, arrow ? 1.6 : 1.2, arrow ? 2.4 : 2.0), centre.add(0, arrow ? 1.3 : 0.9, 0));
            mark = tick;
            shotAt = -1;
        }
        if (shotAt < 0) { // the camera settles, then wait for the next shot (a quiet turret cannot stall the scene)
            long shot = ClientTurret.of(TURRETS[turret]).lastShotTick;
            if (tick - mark < 20) seenShot = shot;
            else if (shot != seenShot || tick - mark > 140) shotAt = tick;
            if (shotAt < 0) return;
        }
        int frame = tick - shotAt;
        if (frame <= 10 && frame % 2 == 0) Showcase.shot(mc, "early_" + NAMES[turret] + "_" + frame / 2);
        if (frame == 12) Showcase.camera(server, centre.add(arrow ? 2.8 : 2.3, arrow ? 1.6 : 1.1, arrow ? -0.2 : -0.5), centre.add(0, arrow ? 1.3 : 0.9, 0));
        if (frame == 34) Showcase.shot(mc, "early_" + NAMES[turret] + "_side");
        if (frame == 36) {
            turret++;
            mark = -1;
        }
    }

    /**
     * One flight pass: the light set and the ammo loaded, then frames of the turret's next shot. A sling shot from just over
     * the turret's shoulder as it leaves, flies (a tumbling lit sprite, its wisp) and lands (chips, snow, a splat); the
     * Arrow Launcher's volley from far to the side (the arc, the wind streaks, a tipped one's mist), then the arrows stuck
     * round the husk (the dust, the spectral texture).
     */
    private static void flights(Minecraft mc, MinecraftServer server) {
        Flight f = FLIGHTS[flight];
        boolean arrow = f.turret() == 3;
        Vec3 base = TURRETS[f.turret()].getCenter(), husk = Vec3.atBottomCenterOf(ORIGIN).add(HUSKS[f.turret()]);
        String name = "early_fly_" + NAMES[f.turret()] + (f.ammo().equals("plain") ? "" : "_" + f.ammo()) + (f.night() ? "_night" : "_day");
        if (mark < 0) {
            Showcase.server(server, s -> {
                s.overworld().setDayTime(f.night() ? 18000 : 6000);
                if (!arrow || !(s.overworld().getBlockEntity(TURRETS[3]) instanceof TurretBaseBlockEntity launcher)) return;
                ItemStack ammo = switch (f.ammo()) {
                    case "tipped" -> PotionUtils.setPotion(new ItemStack(Items.TIPPED_ARROW, 64), Potions.POISON);
                    case "spectral" -> new ItemStack(Items.SPECTRAL_ARROW, 64);
                    default -> new ItemStack(BastionItems.CREATIVE_AMMO.get());
                };
                launcher.inventory().setStackInSlot(TurretInventory.AMMO_START, ammo);
            });
            if (arrow) Showcase.camera(server, new Vec3(base.x - 15, base.y + 7, (base.z + husk.z) / 2), new Vec3(base.x, base.y + 6, (base.z + husk.z) / 2));
            else Showcase.camera(server, base.add(1.1, 1.2, -1.4), husk.add(0, 1.2, 0));
            mark = tick;
            shotAt = landed = -1;
        }
        if (shotAt < 0) { // the camera settles and the ammo is in, then wait for the next shot
            long shot = ClientTurret.of(TURRETS[f.turret()]).lastShotTick;
            if (tick - mark < 20) seenShot = shot;
            else if (shot != seenShot || tick - mark > 160) shotAt = tick;
            if (shotAt < 0) return;
        }
        int frame = tick - shotAt;
        if (!arrow) {
            if (frame == 1 || frame == 2 || frame == 3 || frame == 5 || frame == 9) Showcase.shot(mc, name + "_" + frame);
        } else {
            if (frame == 6 || frame == 14 || frame == 22 || frame == 30 || frame == 38) Showcase.shot(mc, name + "_" + frame);
            // The flight time follows the arc (a run/config shell_speed changes it): wait for this volley to stick.
            Vec3 stuck = landed < 0 && frame > 38 ? volleyLanded(mc, frame) : null;
            if (stuck != null || landed < 0 && frame > 200) {
                landed = frame;
                Vec3 at = stuck != null ? stuck : husk; // the launcher shoots the nearest husk past its min range, not always its own
                Showcase.camera(server, at.add(-3, 2.2, -3), at.add(0, 0.3, 0));
            }
            if (landed >= 0 && (frame == landed + 3 || frame == landed + 8 || frame == landed + 18)) Showcase.shot(mc, name + "_landed_" + (frame - landed));
        }
        if (arrow ? landed >= 0 && frame == landed + 20 : frame == 10) {
            flight++;
            mark = -1;
        }
    }

    /** Where an arrow of the volley loosed {@code age} ticks ago is stuck, or null (older volleys' arrows are older on the client too). */
    @Nullable
    private static Vec3 volleyLanded(Minecraft mc, int age) {
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof TurretArrowEntity arrow && arrow.stuck() && arrow.tickCount <= age + 2) return arrow.position();
        }
        return null;
    }

    /** The most damaged base (15 % HP) is destroyed: it falls apart into planks and dust with a wood crunch, no glowing blast. */
    private static void wreck(Minecraft mc, MinecraftServer server) {
        if (mark < 0) {
            Showcase.camera(server, Vec3.atBottomCenterOf(DAMAGED[2]).add(-1.5, 1.9, -3.4), Vec3.atBottomCenterOf(DAMAGED[2]).add(0, 0.5, 0)); // clear of the ceiling base's pillar
            mark = tick;
        }
        int since = tick - mark;
        if (since == 20) {
            Showcase.server(server, s -> {
                if (s.overworld().getBlockEntity(DAMAGED[2]) instanceof TurretBaseBlockEntity base) base.takeDamage(s.overworld().damageSources().generic(), base.maxHealth());
            });
        }
        if (since == 23 || since == 28 || since == 40) Showcase.shot(mc, "early_wreck_" + (since - 20));
        if (since == 42) {
            wrecked = true;
            mark = -1;
        }
    }

    /** The items in the hotbar, one held, then the screen of the floor base (no tier, module or Connector slots). */
    private static void items(Minecraft mc, MinecraftServer server) {
        if (mark < 0) mark = tick;
        int since = tick - mark;
        if (since == 0) {
            Showcase.server(server, s -> {
                ServerPlayer player = s.getPlayerList().getPlayers().get(0);
                Item[] items = {BastionItems.MAKESHIFT_TURRET_BASE.get(), BastionItems.TURRET_BASE.get(), BastionItems.COBBLESTONE_TURRET.get(),
                        BastionItems.SNOWBALL_REPEATER.get(), BastionItems.POTATO_SHOTGUN.get(), BastionItems.ARROW_LAUNCHER.get()};
                for (int i = 0; i < items.length; i++) player.getInventory().setItem(i + 1, new ItemStack(items[i]));
                player.getInventory().selected = 0;
            });
            mc.options.hideGui = false;
            Showcase.camera(server, FLOOR.getCenter().add(0, 1.3, -2.4), FLOOR.getCenter().add(0, 0.1, 0));
        }
        if (since == 28) Showcase.shot(mc, "early_hotbar");
        if (since == 30) mc.player.getInventory().selected = 4; // the Snowball Repeater in hand (held_idle)
        if (since == 44) Showcase.shot(mc, "early_held");
        if (since == 46) mc.player.getInventory().selected = 0;
        if (since == 50 && mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        }
        if (since == 60) Showcase.shot(mc, "early_gui");
        if (since == 62) mc.setScreen(null);
        if (since == 68) mc.stop();
    }

    /** Moves every husk {@code dx} blocks sideways (the turrets turn after them). */
    private static void step(double dx) {
        for (Husk husk : TARGETS) if (husk != null) husk.moveTo(husk.getX() + dx, husk.getY(), husk.getZ(), 180, 0);
    }

    private static void build(MinecraftServer server) {
        ServerLevel level = server.overworld();
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
        level.setWeatherParameters(6000, 0, false, false);
        level.setDayTime(6000);
        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
        player.setGameMode(GameType.CREATIVE);
        player.getInventory().clearContent();
        for (int x = -16; x <= 16; x++) {
            for (int z = -10; z <= 30; z++) {
                level.setBlock(ORIGIN.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
                for (int y = 0; y < 9; y++) level.setBlock(ORIGIN.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
            }
        }
        // a wall for the wall base, a roof on a pillar for the ceiling one
        for (int y = 0; y < 4; y++) for (int z = -1; z <= 1; z++) level.setBlock(WALL.offset(-1, y - 1, z), Blocks.STONE_BRICKS.defaultBlockState(), 2);
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) level.setBlock(CEILING.offset(x, 1, z), Blocks.STONE_BRICKS.defaultBlockState(), 2);
        for (int y = 0; y < 3; y++) level.setBlock(CEILING.offset(1, y - 2, 1), Blocks.STONE_BRICKS.defaultBlockState(), 2);

        Showcase.placeMakeshift(level, FLOOR, Direction.UP, null);
        Showcase.placeMakeshift(level, WALL, Direction.EAST, null);
        Showcase.placeMakeshift(level, CEILING, Direction.DOWN, null);
        for (int i = 0; i < DAMAGED.length; i++) {
            TurretBaseBlockEntity base = Showcase.placeMakeshift(level, DAMAGED[i], Direction.UP, null);
            base.takeDamage(level.damageSources().generic(), base.maxHealth() * (1 - HEALTH[i]));
            LOG.info("[showcase] makeshift base at {}: {} / {} HP", DAMAGED[i], base.health(), base.maxHealth());
        }
        Item[] weapons = {BastionItems.COBBLESTONE_TURRET.get(), BastionItems.SNOWBALL_REPEATER.get(), BastionItems.POTATO_SHOTGUN.get(),
                BastionItems.ARROW_LAUNCHER.get()};
        for (int i = 0; i < TURRETS.length; i++) {
            Showcase.placeMakeshift(level, TURRETS[i], Direction.UP, weapons[i]);
            Husk husk = EntityType.HUSK.create(level);
            if (husk == null) continue;
            Vec3 at = Vec3.atBottomCenterOf(ORIGIN).add(HUSKS[i]);
            husk.moveTo(at.x, at.y, at.z, 180, 0);
            husk.setNoAi(true);
            husk.setPersistenceRequired();
            husk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
            husk.setHealth(1000);
            level.addFreshEntity(husk);
            TARGETS[i] = husk;
        }
    }
}
