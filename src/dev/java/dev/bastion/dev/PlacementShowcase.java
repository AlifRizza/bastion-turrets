package dev.bastion.dev;

import dev.bastion.Bastion;
import dev.bastion.registry.BastionBlocks;
import dev.bastion.registry.BastionItems;
import dev.bastion.turret.TurretBaseItem;
import dev.bastion.turret.TurretTier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Dev-only: the placement hologram (-Pshowcase=placement in a scratch world). A T3 Large Base ghost free and blocked,
 * a Part Assembler ghost facing west next to the real station placed on the same spot, main hand beating off hand,
 * and a night shot.
 */
@Mod.EventBusSubscriber(modid = Bastion.MOD_ID, value = Dist.CLIENT)
public final class PlacementShowcase {
    private static final boolean ON = "placement".equals(System.getProperty("bastion.showcase"));
    private static final BlockPos ORIGIN = new BlockPos(0, 150, 0); // the block a station or base would fill first
    private static int tick = -1;

    private PlacementShowcase() {
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
        Vec3 floorTop = Vec3.atBottomCenterOf(ORIGIN);
        switch (tick) {
            case 20 -> Showcase.server(server, PlacementShowcase::build);
            case 30 -> {
                mc.options.hideGui = true;
                hold(server, TurretBaseItem.withTier(BastionItems.LARGE_TURRET_BASE.get(), TurretTier.T3), ItemStack.EMPTY);
                Showcase.camera(server, floorTop.add(-2.5, 2.6, -2.5), floorTop); // looking south-east: grows south-east
            }
            case 60 -> Showcase.shot(mc, "placement_large");
            case 62 -> Showcase.server(server, s -> s.overworld().setBlockAndUpdate(ORIGIN.south().east(), Blocks.STONE.defaultBlockState()));
            case 80 -> Showcase.shot(mc, "placement_large_blocked");
            case 82 -> {
                Showcase.server(server, s -> s.overworld().setBlockAndUpdate(ORIGIN.south().east(), Blocks.AIR.defaultBlockState()));
                hold(server, new ItemStack(BastionItems.PART_ASSEMBLER.get()), ItemStack.EMPTY);
                Showcase.camera(server, floorTop.add(3.5, 2.4, 0.4), floorTop); // looking west: the front faces east
            }
            case 110 -> Showcase.shot(mc, "placement_assembler_west");
            case 112 -> Showcase.server(server, s -> BastionBlocks.PART_ASSEMBLER.get().placeAt(s.overworld(), ORIGIN, Direction.EAST));
            case 114 -> hold(server, ItemStack.EMPTY, ItemStack.EMPTY); // the real station alone, same camera
            case 135 -> Showcase.shot(mc, "placement_assembler_placed");
            case 137 -> {
                Showcase.server(server, PlacementShowcase::clear);
                hold(server, new ItemStack(BastionItems.TURRET_BASE.get()), new ItemStack(BastionItems.PART_WORKSTATION.get()));
            }
            case 160 -> Showcase.shot(mc, "placement_mainhand_wins");
            case 162 -> {
                Showcase.server(server, s -> s.overworld().setDayTime(18000));
                hold(server, new ItemStack(BastionItems.PART_WORKSTATION.get()), ItemStack.EMPTY);
            }
            case 200 -> Showcase.shot(mc, "placement_night");
            case 205 -> mc.stop();
            default -> {
            }
        }
    }

    private static void hold(MinecraftServer server, ItemStack main, ItemStack off) {
        Showcase.server(server, s -> {
            ServerPlayer player = s.getPlayerList().getPlayers().get(0);
            player.getInventory().selected = 0;
            player.setItemInHand(InteractionHand.MAIN_HAND, main);
            player.setItemInHand(InteractionHand.OFF_HAND, off);
        });
    }

    private static void build(MinecraftServer server) {
        ServerLevel level = server.overworld();
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        level.setDayTime(6000);
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) level.setBlock(ORIGIN.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
        }
        clear(server);
    }

    private static void clear(MinecraftServer server) {
        ServerLevel level = server.overworld();
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) {
                for (int y = 0; y < 6; y++) level.setBlock(ORIGIN.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
            }
        }
    }
}
