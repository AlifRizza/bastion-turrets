package dev.bastion.dev;

import com.mojang.logging.LogUtils;
import dev.bastion.Bastion;
import dev.bastion.registry.BastionBlocks;
import dev.bastion.registry.BastionItems;
import dev.bastion.turret.TurretBaseBlock;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.TurretInventory;
import dev.bastion.turret.TurretTier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Dev-only scripted scene (src/dev, never shipped): floor turrets at T1/T2/T3, one on a wall and one under a ceiling,
 * all shooting tanky husks, with screenshots of the models, the glow at night, item icons and the GUI.
 * Runs only with -Dbastion.showcase=true, see build.gradle.
 */
@Mod.EventBusSubscriber(modid = Bastion.MOD_ID, value = Dist.CLIENT)
public final class Showcase {
    private static final Logger LOG = LogUtils.getLogger();
    private static final boolean ON = !System.getProperty("bastion.showcase", "").isEmpty()
            && !System.getProperty("bastion.showcase").equals("weapons") && !System.getProperty("bastion.showcase").equals("missiles") && !System.getProperty("bastion.showcase").equals("automation")
            && !System.getProperty("bastion.showcase").equals("elemental")
            && !System.getProperty("bastion.showcase").equals("laser");
    private static final BlockPos ORIGIN = new BlockPos(0, 150, 0);
    private static final BlockPos GUN = ORIGIN.offset(-5, 0, 0), MG = ORIGIN, SHOTGUN = ORIGIN.offset(5, 0, 0);
    private static final BlockPos WALL = ORIGIN.offset(-11, 2, 6), CEILING = ORIGIN.offset(11, 4, 6);
    private static final List<Husk> TARGETS = new ArrayList<>();

    private static int tick = -1;

    private Showcase() {
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

        switch (tick) {
            case 20 -> server(server, Showcase::buildScene);
            case 30 -> {
                mc.options.hideGui = true;
                camera(server, ORIGIN.getCenter().add(-1, 3.2, -8.5), ORIGIN.getCenter().add(0, 0.6, 1));
            }
            case 110 -> shot(mc, "v2_overview");
            case 112 -> camera(server, GUN.getCenter().add(2.4, 1.9, -2.6), GUN.getCenter().add(0, 1.0, 0));
            case 140 -> shot(mc, "v2_gun");
            case 142 -> camera(server, MG.getCenter().add(2.4, 1.9, -2.6), MG.getCenter().add(0, 1.0, 0));
            case 170 -> shot(mc, "v2_mg");
            case 172 -> camera(server, SHOTGUN.getCenter().add(-2.4, 1.9, -2.6), SHOTGUN.getCenter().add(0, 1.0, 0));
            case 200 -> shot(mc, "v2_shotgun");
            case 202 -> camera(server, GUN.getCenter().add(-1.6, 0.6, 2.8), GUN.getCenter().add(0, 1.1, 0));
            case 230 -> shot(mc, "v2_gun_back");
            case 232 -> camera(server, WALL.getCenter().add(4.2, 0.8, -3.2), WALL.getCenter().add(0.8, 0, 0));
            case 260 -> shot(mc, "v2_wall");
            case 262 -> camera(server, CEILING.getCenter().add(-4.2, -1.8, -3.2), CEILING.getCenter().add(-0.4, -0.8, 0));
            case 290 -> shot(mc, "v2_ceiling");
            case 292 -> {
                server(server, s -> s.overworld().setDayTime(18000));
                camera(server, ORIGIN.getCenter().add(-1, 3.2, -8.5), ORIGIN.getCenter().add(0, 0.6, 1));
            }
            case 330 -> shot(mc, "v2_night");
            case 332 -> camera(server, GUN.getCenter().add(2.4, 1.9, -2.6), GUN.getCenter().add(0, 1.0, 0));
            case 360 -> shot(mc, "v2_gun_night");
            // Items: hotbar icons, then the GUI with the weapon in its slot.
            case 362 -> {
                server(server, s -> {
                    s.overworld().setDayTime(6000);
                    ServerPlayer player = s.getPlayerList().getPlayers().get(0);
                    Item[] items = {BastionItems.TURRET_BASE.get(), BastionItems.GUN_TURRET.get(), BastionItems.MACHINE_GUN_TURRET.get(),
                            BastionItems.SHOTGUN_TURRET.get(), BastionItems.CREATIVE_AMMO.get()};
                    for (int i = 0; i < items.length; i++) player.getInventory().setItem(i + 1, new ItemStack(items[i]));
                    player.getInventory().selected = 0;
                });
                mc.options.hideGui = false;
                camera(server, GUN.getCenter().add(0, 1.3, -2.4), GUN.getCenter().add(0, 0.3, 0));
            }
            case 390 -> shot(mc, "v2_hotbar");
            case 392 -> {
                if (mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
                    mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
                }
            }
            case 402 -> shot(mc, "v2_gui");
            case 404 -> mc.setScreen(null);
            case 410 -> mc.stop();
            default -> {
            }
        }
        if (tick > 20 && tick % 40 == 0) server(server, Showcase::refill);
    }

    static void server(MinecraftServer server, Consumer<MinecraftServer> task) {
        server.execute(() -> task.accept(server));
    }

    private static void buildScene(MinecraftServer server) {
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
            for (int z = -10; z <= 18; z++) {
                level.setBlock(ORIGIN.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
                for (int y = 0; y < 9; y++) level.setBlock(ORIGIN.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
            }
        }
        // a wall for the wall turret, a roof (on a pillar) for the ceiling one
        for (int y = 0; y < 6; y++) for (int z = 3; z <= 9; z++) level.setBlock(ORIGIN.offset(-12, y, z), Blocks.STONE_BRICKS.defaultBlockState(), 2);
        for (int x = 8; x <= 14; x++) for (int z = 3; z <= 9; z++) level.setBlock(ORIGIN.offset(x, 5, z), Blocks.STONE_BRICKS.defaultBlockState(), 2);
        for (int y = 0; y < 5; y++) level.setBlock(ORIGIN.offset(14, y, 9), Blocks.STONE_BRICKS.defaultBlockState(), 2);

        place(level, GUN, Direction.UP, BastionItems.GUN_TURRET.get(), TurretTier.T1);
        place(level, MG, Direction.UP, BastionItems.MACHINE_GUN_TURRET.get(), TurretTier.T2);
        place(level, SHOTGUN, Direction.UP, BastionItems.SHOTGUN_TURRET.get(), TurretTier.T3);
        place(level, WALL, Direction.EAST, BastionItems.GUN_TURRET.get(), TurretTier.T1);
        place(level, CEILING, Direction.DOWN, BastionItems.MACHINE_GUN_TURRET.get(), TurretTier.T1);
        refill(server);
    }

    static void place(ServerLevel level, BlockPos pos, Direction facing, Item weapon, TurretTier tier) {
        level.setBlock(pos, BastionBlocks.TURRET_BASE.get().defaultBlockState().setValue(TurretBaseBlock.FACING, facing), 3);
        TurretBaseBlockEntity turret = (TurretBaseBlockEntity) level.getBlockEntity(pos);
        for (TurretTier t : TurretTier.values()) if (t.ordinal() > 0 && t.ordinal() <= tier.ordinal()) turret.upgradeTo(t);
        turret.inventory().setStackInSlot(TurretInventory.WEAPON, new ItemStack(weapon));
        turret.inventory().setStackInSlot(TurretInventory.AMMO_START, new ItemStack(BastionItems.CREATIVE_AMMO.get()));
    }

    /** Tanky, frozen husks: one in front of each floor turret and one between the wall and ceiling turrets. */
    private static void refill(MinecraftServer server) {
        ServerLevel level = server.overworld();
        TARGETS.removeIf(husk -> !husk.isAlive());
        Vec3[] spots = {new Vec3(-2, 0, 7.5), new Vec3(3, 0, 7.5), new Vec3(8.5, 0, 7.5), new Vec3(0.5, 0, 12.5)};
        while (TARGETS.size() < spots.length) {
            Husk husk = EntityType.HUSK.create(level);
            if (husk == null) return;
            Vec3 at = Vec3.atBottomCenterOf(ORIGIN).add(spots[TARGETS.size()]);
            husk.moveTo(at.x, at.y, at.z, 180, 0);
            husk.setNoAi(true);
            husk.setPersistenceRequired();
            husk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
            husk.setHealth(1000);
            level.addFreshEntity(husk);
            TARGETS.add(husk);
        }
    }

    /** Teleports the player (flying) to eye position {@code eye}, looking at {@code at}. */
    static void camera(MinecraftServer server, Vec3 eye, Vec3 at) {
        Vec3 d = at.subtract(eye);
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
        float pitch = (float) -(Mth.atan2(d.y, d.horizontalDistance()) * Mth.RAD_TO_DEG);
        server(server, s -> {
            ServerPlayer player = s.getPlayerList().getPlayers().get(0);
            player.connection.teleport(eye.x, eye.y - player.getEyeHeight(), eye.z, yaw, pitch);
            // The client drops creative flight whenever it touches ground; re-arm it mid-air after every jump.
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
        });
    }

    static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, "showcase_" + name + ".png", mc.getMainRenderTarget(), message -> {});
        LOG.info("[showcase] {}", name);
    }
}
