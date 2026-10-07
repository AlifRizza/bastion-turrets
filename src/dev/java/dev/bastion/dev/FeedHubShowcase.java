package dev.bastion.dev;

import dev.bastion.Bastion;
import dev.bastion.registry.BastionBlocks;
import dev.bastion.registry.BastionItems;
import dev.bastion.turret.LargeTurretBaseBlock;
import dev.bastion.turret.TurretBaseBlock;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.TurretInventory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.ItemHandlerHelper;

/**
 * Dev-only scene for the Feed Hub (-Pshowcase=feedhub, with -PshowcaseWorld set to a scratch copy of a world): a lone hub
 * on a post with turrets on its four sides, a 2x2x1 with a Tesla Coil on top and guns on its sides, a 3x3x2 ringed with
 * turrets (both multiblocks fed FE by a Creative Power Source), by day and by night, then the 3x3x2's GUI: Summary,
 * Storage, Storage scrolled one row.
 */
@Mod.EventBusSubscriber(modid = Bastion.MOD_ID, value = Dist.CLIENT)
public final class FeedHubShowcase {
    private static final boolean ON = "feedhub".equals(System.getProperty("bastion.showcase"));
    private static final BlockPos ORIGIN = new BlockPos(0, 150, 0), HUB = ORIGIN.above();
    /** Lowest corners of the 2x2x1 (Tesla on top) and the 3x3x2. */
    private static final BlockPos SMALL = ORIGIN.offset(-6, 0, -1), BIG = ORIGIN.offset(4, 0, -1);
    private static int tick = -1;

    private FeedHubShowcase() {
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
        Vec3 overviewAt = Vec3.atCenterOf(HUB), big = new Vec3(BIG.getX() + 1.5, BIG.getY() + 1, BIG.getZ() + 1.5);
        Vec3 small = new Vec3(SMALL.getX() + 1, SMALL.getY() + 1, SMALL.getZ() + 1);
        if (tick == 20) Showcase.server(server, FeedHubShowcase::build);
        if (tick == 22) mc.options.hideGui = true;
        if (tick == 30) Showcase.server(server, FeedHubShowcase::fill);
        if (tick == 40) Showcase.camera(server, overviewAt.add(0.5, 5.5, 11), overviewAt);
        if (tick == 50) Showcase.shot(mc, "f_day");
        if (tick == 52) Showcase.camera(server, big.add(-3.6, 2.6, -4.4), big);
        if (tick == 62) Showcase.shot(mc, "f_multi");
        if (tick == 64) Showcase.camera(server, small.add(-3.4, 2.4, 3.8), small.add(0, 0.6, 0));
        if (tick == 74) Showcase.shot(mc, "f_tesla");
        if (tick == 76) Showcase.server(server, s -> s.overworld().setDayTime(18000));
        if (tick == 78) Showcase.camera(server, overviewAt.add(0.5, 5.5, 11), overviewAt);
        if (tick == 94) Showcase.shot(mc, "f_night");
        if (tick == 96) Showcase.camera(server, big.add(-3.6, 2.6, -4.4), big);
        if (tick == 106) Showcase.shot(mc, "f_multi_night");
        if (tick == 108) {
            Showcase.server(server, s -> s.overworld().setDayTime(6000));
            mc.options.hideGui = false;
            Vec3 face = Vec3.atCenterOf(BIG.offset(0, 1, 0)).add(0, 0, -0.5); // north face of a hub with no base on it
            Showcase.camera(server, face.add(0, 0.2, -2.2), face);
        }
        if (tick == 118 && mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        }
        if (tick == 126) Showcase.shot(mc, "f_gui_summary");
        if (tick == 128 && mc.screen != null) clickStorageTab(mc.screen);
        if (tick == 134) Showcase.shot(mc, "f_gui_storage");
        if (tick == 136 && mc.screen != null) mc.screen.mouseScrolled(mc.screen.width / 2.0, mc.screen.height / 2.0, -1);
        if (tick == 142) Showcase.shot(mc, "f_gui_scrolled");
        if (tick == 144) mc.setScreen(null);
        if (tick == 150) mc.stop();
    }

    /** The second side tab, left of the 192 x 194 panel (FeedHubScreen TAB_X -23, TAB_Y 8, TAB_GAP 26). */
    private static void clickStorageTab(Screen screen) {
        int left = (screen.width - 192) / 2, top = (screen.height - 194) / 2;
        screen.mouseClicked(left - 23 + 10, top + 8 + 26 + 10, 0);
    }

    private static void build(MinecraftServer server) {
        ServerLevel level = server.overworld();
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        level.setDayTime(6000);
        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
        player.setGameMode(GameType.CREATIVE);
        player.getInventory().clearContent();
        for (int x = -11; x <= 11; x++) {
            for (int z = -7; z <= 7; z++) {
                level.setBlock(ORIGIN.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
                for (int y = 0; y < 6; y++) level.setBlock(ORIGIN.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
            }
        }
        level.setBlock(ORIGIN, Blocks.POLISHED_DEEPSLATE.defaultBlockState(), 2); // a post under the hub
        level.setBlock(HUB, BastionBlocks.FEED_HUB.get().defaultBlockState(), 3);
        // Four sides armed, the top left free for the feed (and to open the hub).
        Item[] weapons = {BastionItems.GUN_TURRET.get(), BastionItems.MACHINE_GUN_TURRET.get(), BastionItems.SHOTGUN_TURRET.get(),
                BastionItems.SNIPER_TURRET.get()};
        Direction[] sides = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        for (int i = 0; i < sides.length; i++) {
            BlockPos at = HUB.relative(sides[i]);
            level.setBlock(at, BastionBlocks.TURRET_BASE.get().defaultBlockState().setValue(TurretBaseBlock.FACING, sides[i]), 3);
            if (level.getBlockEntity(at) instanceof TurretBaseBlockEntity base) {
                base.inventory().setStackInSlot(TurretInventory.WEAPON, new ItemStack(weapons[i]));
            }
        }
        level.getBlockEntity(HUB).getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).ifPresent(buffer -> {
            // More than the turrets hold (T1: 3 slots), so some waits in the buffer for the GUI shot.
            for (int i = 0; i < 20; i++) ItemHandlerHelper.insertItem(buffer, new ItemStack(BastionItems.KINETIC_ROUNDS.get(), 64), false);
            ItemHandlerHelper.insertItem(buffer, new ItemStack(BastionItems.SCATTER_SHELLS.get(), 32), false);
        });
        // 2x2x1, Tesla Coil on top, guns on its west and north faces, FE from a Creative Power Source on its south side.
        hubs(level, SMALL, 2, 1);
        LargeTurretBaseBlock.placeAt(level, SMALL.above(), BastionBlocks.LARGE_TURRET_BASE.get());
        arm(level, SMALL.above(), BastionItems.TESLA_TURRET.get());
        base(level, SMALL.west(), Direction.WEST, BastionItems.GUN_TURRET.get());
        base(level, SMALL.north(), Direction.NORTH, BastionItems.GUN_TURRET.get());
        level.setBlock(SMALL.offset(1, 0, 2), BastionBlocks.CREATIVE_POWER_SOURCE.get().defaultBlockState(), 3);
        // 3x3x2 ringed with five turrets, power on its east side (ammo: fill, once merged).
        hubs(level, BIG, 3, 2);
        base(level, BIG.offset(1, 0, -1), Direction.NORTH, BastionItems.SHOTGUN_TURRET.get());
        base(level, BIG.offset(2, 1, -1), Direction.NORTH, BastionItems.MACHINE_GUN_TURRET.get());
        base(level, BIG.offset(-1, 1, 1), Direction.WEST, BastionItems.SNIPER_TURRET.get());
        base(level, BIG.offset(1, 2, 1), Direction.UP, BastionItems.GUN_TURRET.get());
        base(level, BIG.offset(1, 1, 3), Direction.SOUTH, BastionItems.FLAMETHROWER_TURRET.get());
        level.setBlock(BIG.offset(3, 0, 1), BastionBlocks.CREATIVE_POWER_SOURCE.get().defaultBlockState(), 3);
    }

    /** Ammo for the 3x3x2, well past what its turrets hold; a tick after build, once the hubs have merged. */
    private static void fill(MinecraftServer server) {
        server.overworld().getBlockEntity(BIG).getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).ifPresent(storage -> {
            for (int i = 0; i < 60; i++) ItemHandlerHelper.insertItem(storage, new ItemStack(BastionItems.KINETIC_ROUNDS.get(), 64), false);
            for (int i = 0; i < 8; i++) ItemHandlerHelper.insertItem(storage, new ItemStack(BastionItems.SNIPER_ROUNDS.get(), 64), false);
            ItemHandlerHelper.insertItem(storage, new ItemStack(BastionItems.SCATTER_SHELLS.get(), 32), false);
            ItemHandlerHelper.insertItem(storage, new ItemStack(BastionItems.FUEL_CANISTER.get(), 16), false);
        });
    }

    private static void hubs(ServerLevel level, BlockPos min, int width, int height) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                for (int z = 0; z < width; z++) level.setBlock(min.offset(x, y, z), BastionBlocks.FEED_HUB.get().defaultBlockState(), 3);
            }
        }
    }

    private static void base(ServerLevel level, BlockPos at, Direction facing, Item weapon) {
        level.setBlock(at, BastionBlocks.TURRET_BASE.get().defaultBlockState().setValue(TurretBaseBlock.FACING, facing), 3);
        arm(level, at, weapon);
    }

    private static void arm(ServerLevel level, BlockPos at, Item weapon) {
        if (level.getBlockEntity(at) instanceof TurretBaseBlockEntity base) base.inventory().setStackInSlot(TurretInventory.WEAPON, new ItemStack(weapon));
    }
}
