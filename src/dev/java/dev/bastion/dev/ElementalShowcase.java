package dev.bastion.dev;

import com.mojang.logging.LogUtils;
import dev.bastion.Bastion;
import dev.bastion.registry.BastionBlocks;
import dev.bastion.registry.BastionItems;
import dev.bastion.turret.LargeTurretBaseBlock;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.TurretInventory;
import dev.bastion.turret.TurretTier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Dev-only scene for the Tesla Coil and the Flamethrower (-Pshowcase=elemental, run it with -PshowcaseWorld set to a
 * scratch copy of a world, never the user's showcase world): bolts, charging arcs, the flame stream, burning targets,
 * by day and by night, then the GUI's energy gauge and the item icons.
 */
@Mod.EventBusSubscriber(modid = Bastion.MOD_ID, value = Dist.CLIENT)
public final class ElementalShowcase {
    private static final Logger LOG = LogUtils.getLogger();
    private static final boolean ON = "elemental".equals(System.getProperty("bastion.showcase"));
    private static final BlockPos ORIGIN = new BlockPos(0, 150, 0);
    /** The tesla's 2x2 runs from TESLA to TESLA + (1, 0, 1). */
    private static final BlockPos TESLA = ORIGIN.offset(-11, 0, -1), FLAME = ORIGIN.offset(10, 0, 0);
    private static final Vec3[] TESLA_TARGETS = {new Vec3(-14.5, 0, 7.5), new Vec3(-10, 0, 9.5), new Vec3(-6.5, 0, 6.5)};
    private static final Vec3[] FLAME_TARGETS = {new Vec3(10.5, 0, 5.5), new Vec3(12, 0, 6.5)};
    private static final List<Husk> TARGETS = new ArrayList<>();
    private static int tick = -1;

    private ElementalShowcase() {
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
        if (tick == 20) Showcase.server(server, ElementalShowcase::build);
        if (tick == 22) mc.options.hideGui = true;
        Vec3 tesla = Vec3.atLowerCornerOf(TESLA).add(1, 0, 1), flame = Vec3.atBottomCenterOf(FLAME);
        // 1. The coil and its targets from the side, through a few discharges.
        if (tick == 30) Showcase.camera(server, tesla.add(9, 3.2, 3), tesla.add(-0.5, 1.6, 4));
        if (tick >= 50 && tick < 110 && tick % 2 == 0) Showcase.shot(mc, "e_tesla_" + (tick - 50) / 2);
        // 2. The crown up close: idle sparks, charging arcs, the discharge.
        if (tick == 112) Showcase.camera(server, tesla.add(2.6, 3.4, -2.8), tesla.add(0, 2.6, 0));
        if (tick >= 130 && tick < 170 && tick % 2 == 0) Showcase.shot(mc, "e_crown_" + (tick - 130) / 2);
        // 3. The flame stream from beside the target lane.
        if (tick == 172) Showcase.camera(server, flame.add(-4.5, 2.2, 3.2), flame.add(0.8, 1.0, 3.5));
        if (tick >= 190 && tick < 240 && tick % 2 == 0) Showcase.shot(mc, "e_flame_" + (tick - 190) / 2);
        // 4. A burning target.
        if (tick == 242) Showcase.camera(server, flame.add(-1.2, 2.0, 7.8), flame.add(0.6, 1.0, 5.6));
        if (tick >= 260 && tick < 280 && tick % 2 == 0) Showcase.shot(mc, "e_burn_" + (tick - 260) / 2);
        // 5. Night: both at once from behind and above.
        if (tick == 282) Showcase.server(server, s -> s.overworld().setDayTime(18000));
        if (tick == 284) Showcase.camera(server, new Vec3(0, 157, -9), new Vec3(0, 151, 6));
        if (tick >= 300 && tick < 340 && tick % 2 == 0) Showcase.shot(mc, "e_night_" + (tick - 300) / 2);
        if (tick == 342) Showcase.camera(server, tesla.add(9, 3.2, 3), tesla.add(-0.5, 1.6, 4));
        if (tick >= 352 && tick < 382 && tick % 2 == 0) Showcase.shot(mc, "e_tnight_" + (tick - 352) / 2);
        // 6. GUI with the energy gauge, then the icons.
        if (tick == 384) {
            Showcase.server(server, s -> s.overworld().setDayTime(6000));
            mc.options.hideGui = false;
            Showcase.camera(server, tesla.add(0.4, 2.0, -3.2), tesla.add(0.4, 0.5, -0.6));
        }
        if (tick == 392 && mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        }
        if (tick == 400) Showcase.shot(mc, "e_gui");
        if (tick == 402) mc.setScreen(null);
        if (tick == 404) Showcase.server(server, s -> {
            ServerPlayer player = s.getPlayerList().getPlayers().get(0);
            Item[] items = {BastionItems.TESLA_TURRET.get(), BastionItems.FLAMETHROWER_TURRET.get(), BastionItems.FUEL_CANISTER.get(),
                    BastionItems.LARGE_TURRET_BASE.get()};
            for (int i = 0; i < items.length; i++) player.getInventory().setItem(i + 1, new ItemStack(items[i]));
            player.getInventory().selected = 1;
        });
        if (tick == 412) Showcase.shot(mc, "e_hotbar");
        if (tick == 420) mc.stop();
        if (tick > 20 && tick % 20 == 0) Showcase.server(server, ElementalShowcase::refill);
        if (tick > 40 && tick % 20 == 0) Showcase.server(server, s -> {
            if (s.overworld().getBlockEntity(TESLA) instanceof TurretBaseBlockEntity t) LOG.info("[showcase] t{} tesla {} energy {}", tick, t.state(), t.energy());
            if (s.overworld().getBlockEntity(FLAME) instanceof TurretBaseBlockEntity t) LOG.info("[showcase] t{} flame {}", tick, t.state());
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
        for (int x = -20; x <= 20; x++) {
            for (int z = -12; z <= 16; z++) {
                level.setBlock(ORIGIN.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
                for (int y = 0; y < 10; y++) level.setBlock(ORIGIN.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
            }
        }
        // A wall behind the flame lane, so the stream has something to splash on.
        for (int x = 8; x <= 13; x++) for (int y = 0; y < 3; y++) level.setBlock(ORIGIN.offset(x, y, 9), Blocks.STONE_BRICKS.defaultBlockState(), 2);
        LargeTurretBaseBlock.placeAt(level, TESLA, BastionBlocks.LARGE_TURRET_BASE.get());
        if (level.getBlockEntity(TESLA) instanceof TurretBaseBlockEntity t) t.inventory().setStackInSlot(TurretInventory.WEAPON,
                new ItemStack(BastionItems.TESLA_TURRET.get()));
        Showcase.place(level, FLAME, Direction.UP, BastionItems.FLAMETHROWER_TURRET.get(), TurretTier.T1);
        if (level.getBlockEntity(FLAME) instanceof TurretBaseBlockEntity t) {
            t.inventory().setStackInSlot(TurretInventory.AMMO_START, new ItemStack(BastionItems.FUEL_CANISTER.get(), 16));
        }
        refill(server);
    }

    /** Keeps the coil's capacitor topped up (as a generator would) and three frozen husks in front of each turret. */
    private static void refill(MinecraftServer server) {
        ServerLevel level = server.overworld();
        if (level.getBlockEntity(TESLA) instanceof TurretBaseBlockEntity t) {
            t.getCapability(ForgeCapabilities.ENERGY, Direction.UP).ifPresent(e -> {
                while (e.receiveEnergy(Integer.MAX_VALUE, false) > 0) ;
            });
        }
        TARGETS.removeIf(h -> !h.isAlive());
        List<Vec3> spots = new ArrayList<>(List.of(TESLA_TARGETS));
        spots.addAll(List.of(FLAME_TARGETS));
        while (TARGETS.size() < spots.size()) {
            Husk husk = EntityType.HUSK.create(level);
            if (husk == null) return;
            Vec3 at = Vec3.atBottomCenterOf(ORIGIN).add(spots.get(TARGETS.size()));
            husk.moveTo(at.x, at.y, at.z, 180, 0);
            husk.setNoAi(true);
            husk.setPersistenceRequired();
            husk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(2000);
            husk.setHealth(2000);
            level.addFreshEntity(husk);
            TARGETS.add(husk);
        }
    }
}
