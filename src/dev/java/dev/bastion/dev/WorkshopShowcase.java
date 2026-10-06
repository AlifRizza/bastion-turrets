package dev.bastion.dev;

import com.mojang.logging.LogUtils;
import dev.bastion.Bastion;
import dev.bastion.registry.BastionBlocks;
import dev.bastion.registry.BastionItems;
import dev.bastion.workstation.WorkstationBlock;
import dev.bastion.workstation.WorkstationBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

/**
 * Dev-only scene for the workstations (-Pshowcase=workshop with -PshowcaseWorld set to a scratch copy of a world): all
 * four working side by side, close-ups of each, two GUIs, and an inventory of parts to check their 3D icons.
 */
@Mod.EventBusSubscriber(modid = Bastion.MOD_ID, value = Dist.CLIENT)
public final class WorkshopShowcase {
    private static final Logger LOG = LogUtils.getLogger();
    private static final boolean ON = "workshop".equals(System.getProperty("bastion.showcase"));
    private static final BlockPos ORIGIN = new BlockPos(0, 150, 0);
    // Cores; every front faces north (the camera side), so each grows west (-x), south (+z) and up.
    private static final BlockPos PARTS = ORIGIN.offset(-5, 0, 4), ASSEMBLER = ORIGIN.offset(0, 0, 4), MODULES = ORIGIN.offset(3, 0, 4),
            AMMO = ORIGIN.offset(6, 0, 4), CHARGING = ORIGIN.offset(9, 0, 4);
    private static int tick = -1;

    private WorkshopShowcase() {
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
        Vec3 o = Vec3.atBottomCenterOf(ORIGIN);
        if (tick == 20) Showcase.server(server, WorkshopShowcase::build);
        if (tick == 22) mc.options.hideGui = true;
        if (tick == 30) Showcase.camera(server, o.add(0.5, 3.4, -5.5), o.add(0.5, 1.2, 4.5));
        if (tick >= 50 && tick < 90 && tick % 5 == 0) Showcase.shot(mc, "w_all_" + (tick - 50) / 5);
        shots(mc, server, 92, "w_parts", Vec3.atLowerCornerOf(PARTS).add(-1.4, 2.1, -1.6), Vec3.atLowerCornerOf(PARTS).add(0, 1.0, 0.5));
        shots(mc, server, 132, "w_assembler", Vec3.atLowerCornerOf(ASSEMBLER).add(1.6, 2.6, -2.2), Vec3.atLowerCornerOf(ASSEMBLER).add(0, 0.9, 1.0));
        shots(mc, server, 172, "w_modules", Vec3.atLowerCornerOf(MODULES).add(1.6, 2.2, -1.4), Vec3.atLowerCornerOf(MODULES).add(0.5, 1.0, 0.5));
        shots(mc, server, 212, "w_ammo", Vec3.atLowerCornerOf(AMMO).add(1.7, 2.2, -1.4), Vec3.atLowerCornerOf(AMMO).add(0.5, 1.0, 0.5));
        shots(mc, server, 330, "w_charging", Vec3.atLowerCornerOf(CHARGING).add(1.6, 1.5, -1.2), Vec3.atLowerCornerOf(CHARGING).add(0.5, 0.45, 0.5));
        // GUIs: look at a station's front with the HUD on and right-click it.
        gui(mc, server, 252, "w_gui_assembler", Vec3.atLowerCornerOf(ASSEMBLER).add(0, 1.6, -1.6), Vec3.atLowerCornerOf(ASSEMBLER).add(0, 0.6, 0.5));
        gui(mc, server, 282, "w_gui_modules", Vec3.atLowerCornerOf(MODULES).add(0.5, 1.6, -1.5), Vec3.atLowerCornerOf(MODULES).add(0.5, 0.6, 0.5));
        if (tick == 312) Showcase.server(server, s -> {
            ServerPlayer player = s.getPlayerList().getPlayers().get(0);
            for (int i = 0; i < 36 && i < BastionItems.PARTS.size(); i++) player.getInventory().setItem(i, new ItemStack(BastionItems.PARTS.get(i).get()));
        });
        if (tick == 316) mc.setScreen(new InventoryScreen(mc.player));
        if (tick == 324) Showcase.shot(mc, "w_inventory");
        if (tick == 326) mc.setScreen(null);
        if (tick == 372) mc.stop();
        if (tick > 20 && tick % 20 == 0) Showcase.server(server, s -> {
            for (BlockPos pos : new BlockPos[]{PARTS, ASSEMBLER, MODULES, AMMO, CHARGING}) {
                if (s.overworld().getBlockEntity(pos) instanceof WorkstationBlockEntity w) {
                    LOG.info("[showcase] t{} {} working {} progress {} energy {} out {}", tick, w.type(), w.working(), w.progress(), w.energy(),
                            w.items().getStackInSlot(WorkstationBlockEntity.OUTPUT));
                    w.items().setStackInSlot(WorkstationBlockEntity.OUTPUT, ItemStack.EMPTY); // keep them busy
                }
            }
        });
    }

    private static void shots(Minecraft mc, MinecraftServer server, int start, String name, Vec3 eye, Vec3 at) {
        if (tick == start) Showcase.camera(server, eye, at);
        if (tick >= start + 10 && tick < start + 40 && tick % 5 == 0) Showcase.shot(mc, name + "_" + (tick - start - 10) / 5);
    }

    private static void gui(Minecraft mc, MinecraftServer server, int start, String name, Vec3 eye, Vec3 at) {
        if (tick == start) {
            mc.options.hideGui = false;
            Showcase.camera(server, eye, at);
        }
        if (tick == start + 8 && mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        }
        if (tick == start + 20) Showcase.shot(mc, name);
        if (tick == start + 24) mc.setScreen(null);
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
        for (int x = -12; x <= 12; x++) {
            for (int z = -8; z <= 12; z++) {
                level.setBlock(ORIGIN.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
                for (int y = 0; y < 6; y++) level.setBlock(ORIGIN.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
            }
        }
        station(level, BastionBlocks.PART_WORKSTATION, PARTS, "part_workstation/gun_barrel", new ItemStack(Items.IRON_INGOT, 64),
                new ItemStack(Items.COPPER_INGOT, 64));
        station(level, BastionBlocks.PART_ASSEMBLER, ASSEMBLER, "part_assembler/tesla_turret", part("tesla_coil"), part("tesla_crown"),
                part("tesla_capacitor_bank"));
        station(level, BastionBlocks.MODULE_WORKSTATION, MODULES, "module_workstation/range_module", new ItemStack(Items.IRON_NUGGET, 64),
                new ItemStack(Items.REDSTONE, 64), new ItemStack(Items.COPPER_INGOT, 64), new ItemStack(Items.SPYGLASS, 16));
        station(level, BastionBlocks.AMMO_WORKSTATION, AMMO, "ammo_workstation/kinetic_rounds", new ItemStack(Items.COPPER_INGOT, 64),
                new ItemStack(Items.GUNPOWDER, 64), new ItemStack(Items.IRON_NUGGET, 64));
        station(level, BastionBlocks.CHARGING_STATION, CHARGING, "charging_station/laser_cell", new ItemStack(BastionItems.EMPTY_LASER_CELL.get(), 16));
    }

    private static ItemStack part(String name) {
        Item item = BastionItems.PARTS.stream().filter(p -> p.getId().getPath().equals(name)).findFirst().orElseThrow().get();
        return new ItemStack(item, 16);
    }

    private static void station(ServerLevel level, RegistryObject<WorkstationBlock> block, BlockPos core, String recipe, ItemStack... inputs) {
        block.get().placeAt(level, core, Direction.NORTH);
        level.setBlock(core.below(), BastionBlocks.CREATIVE_POWER_SOURCE.get().defaultBlockState(), 3); // hidden in the floor
        if (level.getBlockEntity(core) instanceof WorkstationBlockEntity station) {
            station.selectRecipe(Bastion.id("workstation/" + recipe));
            for (int i = 0; i < inputs.length; i++) station.items().setStackInSlot(i, inputs[i]);
        }
    }
}
