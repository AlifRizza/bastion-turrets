package dev.bastion.dev;

import com.mojang.logging.LogUtils;
import dev.bastion.Bastion;
import dev.bastion.registry.BastionBlocks;
import dev.bastion.registry.BastionItems;
import dev.bastion.turret.FortressTurretBaseBlock;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.TurretBaseItem;
import dev.bastion.turret.TurretTier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
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

/**
 * Dev-only: the Fortress Turret Base (-Pshowcase=fortress in a scratch world). Empty T1/T2/T3 Fortresses side by side
 * (close-ups, where the buttresses meet the core, low and from above), a T3 row at full HP, 40 % (yellow) and 15 %
 * (blinking red), a Fortress on a 3x3 Feed Hub, by day and night; the hologram free and blocked; one destroyed (blast,
 * drop); then the items in the hotbar and in hand, and a store gallery frame.
 */
@Mod.EventBusSubscriber(modid = Bastion.MOD_ID, value = Dist.CLIENT)
public final class FortressShowcase {
    private static final Logger LOG = LogUtils.getLogger();
    private static final boolean ON = "fortress".equals(System.getProperty("bastion.showcase"));
    private static final BlockPos ORIGIN = new BlockPos(0, 150, 0);
    /** Cores: the tier row, the health row behind it, the Feed Hub one (on the hub), the one blown up, the hologram spot. */
    private static final BlockPos[] TIERS = {ORIGIN.offset(-4, 0, 0), ORIGIN, ORIGIN.offset(4, 0, 0)};
    private static final BlockPos[] HURT = {ORIGIN.offset(-4, 0, 8), ORIGIN.offset(0, 0, 8), ORIGIN.offset(4, 0, 8)};
    private static final float[] HEALTH = {1, 0.4f, 0.15f};
    private static final BlockPos HUB = ORIGIN.offset(12, 0, 0), WRECK = ORIGIN.offset(-12, 0, 0), GHOST = ORIGIN.offset(0, 0, -8);
    private static int tick = -1;

    private FortressShowcase() {
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
        Vec3 row = ORIGIN.getCenter().add(0, -0.3, 1), hurt = HURT[1].getCenter().add(0, -0.3, 0);
        Vec3 t1 = TIERS[0].getCenter(), t3 = TIERS[2].getCenter(), hub = HUB.above().getCenter(), wreck = WRECK.getCenter();
        Vec3 ghost = Vec3.atBottomCenterOf(GHOST);
        switch (tick) {
            case 20 -> Showcase.server(server, FortressShowcase::build);
            case 30 -> {
                mc.options.hideGui = true;
                Showcase.camera(server, row.add(0, 3.6, -9), row);
            }
            case 90 -> Showcase.shot(mc, "fortress_tiers_day");
            case 92 -> close(server, 0);
            case 115 -> Showcase.shot(mc, "fortress_t1");
            case 117 -> close(server, 1);
            case 140 -> Showcase.shot(mc, "fortress_t2");
            case 142 -> close(server, 2);
            case 165 -> Showcase.shot(mc, "fortress_t3");
            // Where the sloped plates meet the core (front-left diagonal, close), low from far off (the eye cannot go under
            // 1.62 over the floor), from above.
            case 167 -> Showcase.camera(server, t3.add(-2.3, 1.2, -2.3), t3.add(-0.6, 0, -0.6));
            case 190 -> Showcase.shot(mc, "fortress_junction_t3");
            case 192 -> Showcase.camera(server, t1.add(-2.3, 1.2, -2.3), t1.add(-0.6, 0, -0.6));
            case 215 -> Showcase.shot(mc, "fortress_junction_t1");
            case 217 -> Showcase.camera(server, t3.add(5.5, 1.2, -5.5), t3.add(0, -0.2, 0));
            case 240 -> Showcase.shot(mc, "fortress_side_low");
            case 242 -> Showcase.camera(server, t3.add(0, 4.6, -1.2), t3.add(0, 0, 0));
            case 265 -> Showcase.shot(mc, "fortress_top");
            case 267 -> {
                for (BlockPos pos : HURT) {
                    if (mc.level.getBlockEntity(pos) instanceof TurretBaseBlockEntity base) LOG.info("[showcase] client sees {} at {} HP", pos, base.clientHealth());
                }
                Showcase.camera(server, hurt.add(0, 2.8, 7), hurt);
            }
            case 300 -> Showcase.shot(mc, "fortress_health_day");
            case 302 -> Showcase.camera(server, hub.add(-4, 2.4, -4), hub.add(0, -0.6, 0));
            case 330 -> Showcase.shot(mc, "fortress_feed_hub");
            case 332 -> {
                Showcase.server(server, s -> s.overworld().setDayTime(18000));
                Showcase.camera(server, row.add(0, 3.6, -9), row);
            }
            case 370 -> Showcase.shot(mc, "fortress_tiers_night");
            case 372 -> close(server, 2);
            case 395 -> Showcase.shot(mc, "fortress_t3_night");
            case 397 -> Showcase.camera(server, hurt.add(0, 2.8, 7), hurt);
            case 425 -> Showcase.shot(mc, "fortress_health_night_a");
            case 430 -> Showcase.shot(mc, "fortress_health_night_b"); // half a blink later
            case 432 -> {
                Showcase.server(server, s -> {
                    s.overworld().setDayTime(6000);
                    s.getPlayerList().getPlayers().get(0).setItemInHand(InteractionHand.MAIN_HAND,
                            TurretBaseItem.withTier(BastionItems.FORTRESS_TURRET_BASE.get(), TurretTier.T3));
                });
                Showcase.camera(server, ghost.add(-2.8, 3.0, -2.8), ghost); // the crosshair on GHOST: the 3x3 centres there
            }
            case 460 -> Showcase.shot(mc, "fortress_ghost");
            case 462 -> Showcase.server(server, s -> s.overworld().setBlockAndUpdate(GHOST.offset(1, 0, 1), Blocks.STONE.defaultBlockState()));
            case 480 -> Showcase.shot(mc, "fortress_ghost_blocked");
            case 482 -> {
                Showcase.server(server, s -> {
                    s.overworld().setBlockAndUpdate(GHOST.offset(1, 0, 1), Blocks.AIR.defaultBlockState());
                    s.getPlayerList().getPlayers().get(0).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                });
                Showcase.camera(server, wreck.add(3.6, 1.8, -3.6), wreck.add(0, -0.2, 0)); // past the 4.5 blast
            }
            case 510 -> Showcase.server(server, s -> {
                if (s.overworld().getBlockEntity(WRECK) instanceof TurretBaseBlockEntity base) base.takeDamage(s.overworld().damageSources().generic(), base.maxHealth());
            });
            case 513, 518, 530, 560 -> Showcase.shot(mc, "fortress_wreck_" + (tick - 510));
            // The items: the Fortress in hand over the hologram spot (crosshair shown), then the Fortress Frame.
            case 562 -> {
                Showcase.server(server, s -> {
                    ServerPlayer player = s.getPlayerList().getPlayers().get(0);
                    ItemStack[] items = {new ItemStack(BastionItems.FORTRESS_TURRET_BASE.get()),
                            TurretBaseItem.withTier(BastionItems.FORTRESS_TURRET_BASE.get(), TurretTier.T2),
                            TurretBaseItem.withTier(BastionItems.FORTRESS_TURRET_BASE.get(), TurretTier.T3),
                            new ItemStack(BastionItems.DAMAGED_FORTRESS_TURRET_BASE.get()), new ItemStack(BastionItems.FORTRESS_FRAME.get()),
                            new ItemStack(BastionItems.LARGE_TURRET_BASE.get()), new ItemStack(BastionItems.TURRET_BASE.get())};
                    for (int i = 0; i < items.length; i++) player.getInventory().setItem(i, items[i]);
                });
                mc.options.hideGui = false;
                mc.player.getInventory().selected = 0;
                Showcase.camera(server, ghost.add(0, 2.6, -3.4), ghost);
            }
            case 590 -> Showcase.shot(mc, "fortress_hotbar");
            case 592 -> mc.player.getInventory().selected = 4;
            case 610 -> Showcase.shot(mc, "fortress_held_frame");
            // A store gallery candidate: the T3 alone in front, from the side with no other base between.
            case 612 -> {
                mc.options.hideGui = true;
                Showcase.camera(server, t3.add(3.6, 1.5, -3.2), t3.add(0, -0.15, 0));
            }
            case 640 -> Showcase.shot(mc, "fortress_gallery");
            case 645 -> mc.stop();
            default -> {
            }
        }
    }

    /** A front three-quarter view of tier row base {@code i}. */
    private static void close(MinecraftServer server, int i) {
        Vec3 core = TIERS[i].getCenter();
        Showcase.camera(server, core.add(-3.0, 1.9, -3.4), core.add(0, -0.2, 0));
    }

    private static TurretBaseBlockEntity fortress(ServerLevel level, BlockPos core, TurretTier tier) {
        FortressTurretBaseBlock.placeAt(level, core, BastionBlocks.FORTRESS_TURRET_BASE.get());
        TurretBaseBlockEntity base = (TurretBaseBlockEntity) level.getBlockEntity(core);
        for (TurretTier t : TurretTier.values()) if (t.ordinal() > 0 && t.ordinal() <= tier.ordinal()) base.upgradeTo(t);
        return base;
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
        for (int x = -18; x <= 18; x++) {
            for (int z = -15; z <= 18; z++) {
                level.setBlock(ORIGIN.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
                for (int y = 0; y < 9; y++) level.setBlock(ORIGIN.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
            }
        }
        for (int i = 0; i < TIERS.length; i++) fortress(level, TIERS[i], TurretTier.values()[i]);
        for (int i = 0; i < HURT.length; i++) {
            TurretBaseBlockEntity base = fortress(level, HURT[i], TurretTier.T3);
            if (HEALTH[i] < 1) base.takeDamage(level.damageSources().generic(), base.maxHealth() * (1 - HEALTH[i]));
            LOG.info("[showcase] fortress at {}: {} / {} HP", HURT[i], base.health(), base.maxHealth());
        }
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) level.setBlock(HUB.offset(x, 0, z), BastionBlocks.FEED_HUB.get().defaultBlockState(), 3);
        fortress(level, HUB.above(), TurretTier.T2);
        fortress(level, WRECK, TurretTier.T2);
    }
}
