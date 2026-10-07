package dev.bastion.dev;

import dev.bastion.Bastion;
import dev.bastion.client.ClientTurret;
import dev.bastion.registry.BastionBlocks;
import dev.bastion.registry.BastionItems;
import dev.bastion.turret.LargeTurretBaseBlock;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.TurretInventory;
import dev.bastion.turret.TurretState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Dev-only scene for the Repulsor and Repulsor Dome (-Pshowcase=repulsor, with -PshowcaseWorld set to a scratch copy of
 * a world). A Repulsor and a Dome, each fed by a Creative Power Source, get still husks in range; the shots follow the
 * turrets' own state: the Repulsor charging (rings lit) and its wave, the dome at three moments of its growth, then the
 * dome again at night.
 */
@Mod.EventBusSubscriber(modid = Bastion.MOD_ID, value = Dist.CLIENT)
public final class RepulsorShowcase {
    private static final boolean ON = "repulsor".equals(System.getProperty("bastion.showcase"));
    private static final BlockPos ORIGIN = new BlockPos(0, 150, 0), REPULSOR = ORIGIN.offset(-5, 0, 0), DOME = ORIGIN.offset(4, 0, -1);
    private static final Vec3 DOME_MIDDLE = new Vec3(5, 151, 0);
    private static int tick = -1, phase, mark;
    private static long seenShot = -100;

    private RepulsorShowcase() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (!ON || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        if (mc.player == null || mc.level == null || server == null) return;
        mc.options.pauseOnLostFocus = false;
        if (mc.screen instanceof PauseScreen) mc.setScreen(null);
        tick++;
        if (tick == 20) Showcase.server(server, RepulsorShowcase::build);
        if (tick == 22) mc.options.hideGui = true;
        if (tick == 24) Showcase.camera(server, DOME_MIDDLE.add(0.3, 3.2, 1.6), DOME_MIDDLE.add(0, 1, 0)); // close-ups of the models' paint
        if (tick == 27) Showcase.shot(mc, "r_dome_model");
        if (tick == 28) Showcase.camera(server, Vec3.atCenterOf(REPULSOR).add(-1.6, 1.4, 1.2), Vec3.atCenterOf(REPULSOR).add(0, 1, 0));
        if (tick == 29) Showcase.shot(mc, "r_model");
        if (tick == 30) Showcase.camera(server, Vec3.atCenterOf(REPULSOR).add(1.2, 2.2, 5.2), Vec3.atCenterOf(REPULSOR).add(1.4, 0.2, 0));
        if (tick == 40) Showcase.server(server, s -> husks(s, REPULSOR.offset(2, 0, 0), REPULSOR.offset(2, 0, 1)));
        switch (phase) {
            case 0 -> { // the Repulsor: its rings lighting while it charges, then the wave
                if (tick > 40 && state(mc, REPULSOR) == TurretState.CHARGING && mark == 0) mark = tick;
                if (mark > 0 && tick == mark + 7) Showcase.shot(mc, "r_charge");
                if (fired(REPULSOR)) {
                    mark = tick;
                    phase = 1;
                }
            }
            case 1 -> {
                if (tick == mark + 2) Showcase.shot(mc, "r_wave");
                if (tick == mark + 6) Showcase.shot(mc, "r_wave_late");
                if (tick == mark + 20) {
                    Showcase.camera(server, DOME_MIDDLE.add(0.5, 7, 13), DOME_MIDDLE.add(0, -0.5, 0));
                    Showcase.server(server, s -> husks(s, DOME.offset(-2, 0, 1), DOME.offset(4, 0, 1), DOME.offset(1, 0, -3), DOME.offset(1, 0, 4)));
                    seenShot = ClientTurret.of(DOME).lastShotTick;
                    phase = 2;
                }
            }
            case 2, 4 -> { // the dome growing
                if (fired(DOME)) {
                    mark = tick;
                    phase++;
                }
            }
            case 3 -> {
                if (tick == mark + 1) Showcase.shot(mc, "r_dome_1");
                if (tick == mark + 4) Showcase.shot(mc, "r_dome_2");
                if (tick == mark + 8) Showcase.shot(mc, "r_dome_3");
                if (tick == mark + 30) {
                    Showcase.server(server, s -> s.overworld().setDayTime(18000));
                    Showcase.server(server, s -> husks(s, DOME.offset(-2, 0, 1), DOME.offset(4, 0, 1), DOME.offset(1, 0, -3)));
                    seenShot = ClientTurret.of(DOME).lastShotTick;
                    phase = 4;
                }
            }
            case 5 -> {
                if (tick == mark + 4) Showcase.shot(mc, "r_night");
                if (tick == mark + 30) mc.stop();
            }
            default -> {
            }
        }
        if (tick > 2400) mc.stop(); // never hang if a turret does not fire
    }

    private static TurretState state(Minecraft mc, BlockPos pos) {
        return mc.level.getBlockEntity(pos) instanceof TurretBaseBlockEntity turret ? turret.clientState() : null;
    }

    /** True once, on the first tick after the turret at {@code pos} fired since the last check. */
    private static boolean fired(BlockPos pos) {
        long shot = ClientTurret.of(pos).lastShotTick;
        if (shot == seenShot) return false;
        boolean first = seenShot != -100 || shot > 0;
        seenShot = shot;
        return first;
    }

    private static void husks(MinecraftServer server, BlockPos... at) {
        ServerLevel level = server.overworld();
        for (BlockPos pos : at) {
            Husk husk = EntityType.HUSK.create(level);
            if (husk == null) continue;
            husk.removeFreeWill(); // NoAI mobs ignore physics: they would not fly
            husk.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
            level.addFreshEntity(husk);
        }
    }

    private static void build(MinecraftServer server) {
        ServerLevel level = server.overworld();
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        level.setDayTime(6000);
        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
        player.setGameMode(GameType.CREATIVE);
        player.getInventory().clearContent();
        for (int x = -14; x <= 14; x++) {
            for (int z = -10; z <= 10; z++) {
                level.setBlock(ORIGIN.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
                for (int y = 0; y < 10; y++) level.setBlock(ORIGIN.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
            }
        }
        level.setBlock(REPULSOR, BastionBlocks.TURRET_BASE.get().defaultBlockState(), 3);
        arm(level, REPULSOR, BastionItems.REPULSOR_TURRET.get());
        level.setBlock(REPULSOR.west(), BastionBlocks.CREATIVE_POWER_SOURCE.get().defaultBlockState(), 3);
        LargeTurretBaseBlock.placeAt(level, DOME, BastionBlocks.LARGE_TURRET_BASE.get());
        arm(level, DOME, BastionItems.REPULSOR_DOME_TURRET.get());
        level.setBlock(DOME.offset(2, 0, 0), BastionBlocks.CREATIVE_POWER_SOURCE.get().defaultBlockState(), 3);
    }

    private static void arm(ServerLevel level, BlockPos at, Item weapon) {
        if (level.getBlockEntity(at) instanceof TurretBaseBlockEntity base) base.inventory().setStackInSlot(TurretInventory.WEAPON, new ItemStack(weapon));
    }
}
