package dev.bastion.dev;

import dev.bastion.Bastion;
import dev.bastion.registry.BastionItems;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.TurretTier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Dev-only: the project icon, rendered from our own model (-Pshowcase=icon in a scratch world). A switched-off T3 Gun
 * Turret on lime concrete in front of lime walls, shot with a narrow field of view from two angles; tools/make_icon.py
 * keys the lime out and puts the turret on the icon background.
 */
@Mod.EventBusSubscriber(modid = Bastion.MOD_ID, value = Dist.CLIENT)
public final class IconShowcase {
    private static final boolean ON = "icon".equals(System.getProperty("bastion.showcase"));
    private static volatile BlockPos origin = BlockPos.ZERO;
    private static int tick = -1, fov;

    private IconShowcase() {
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
        Vec3 aim = Vec3.atBottomCenterOf(origin).add(0, 0.95, 0);
        if (tick == 20) Showcase.server(server, IconShowcase::build);
        if (tick == 30) {
            mc.options.hideGui = true;
            fov = mc.options.fov().get();
            mc.options.fov().set(30);
        }
        // Three-quarter views either side of the barrel (it points at 0 degrees), slightly from above.
        int[] angles = {15, 25, 35, 325, 335, 345};
        for (int i = 0; i < angles.length; i++) {
            int at = 40 + i * 30;
            double angle = Math.toRadians(angles[i]);
            if (tick == at) Showcase.camera(server, aim.add(Math.sin(angle) * 5.6, 2.0, Math.cos(angle) * 5.6), aim);
            if (tick == at + 25) Showcase.shot(mc, "icon_raw_" + angles[i]);
        }
        if (tick == 300) {
            mc.options.fov().set(fov);
            mc.stop();
        }
    }

    /** Above the spawn: a lime floor and a ring of lime walls, open to the sun, around the turret. */
    private static void build(MinecraftServer server) {
        ServerLevel level = server.overworld();
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
        level.setWeatherParameters(6000, 0, false, false);
        level.setDayTime(6000);
        BlockPos spawn = level.getSharedSpawnPos();
        origin = new BlockPos(spawn.getX(), 200, spawn.getZ());
        for (int x = -14; x <= 14; x++) {
            for (int z = -14; z <= 14; z++) {
                level.setBlock(origin.offset(x, -1, z), Blocks.LIME_CONCRETE.defaultBlockState(), 2);
                for (int y = 0; y < 14; y++) {
                    boolean wall = Math.max(Math.abs(x), Math.abs(z)) == 12;
                    level.setBlock(origin.offset(x, y, z), wall ? Blocks.LIME_CONCRETE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        Showcase.place(level, origin, Direction.UP, BastionItems.GUN_TURRET.get(), TurretTier.T3);
        ((TurretBaseBlockEntity) level.getBlockEntity(origin)).setEnabled(false); // no scanning sweep: the barrel holds still
    }
}
