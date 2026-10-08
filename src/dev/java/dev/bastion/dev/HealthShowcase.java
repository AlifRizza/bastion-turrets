package dev.bastion.dev;

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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Dev-only: the health glows (-Pshowcase=health in a scratch world). Switched-off Gun Turrets (T1) and Tesla Coils on
 * T3 Large Bases behind them at full HP, 40% (yellow) and 15% (blinking red), by day and by night; two night frames half
 * a blink apart, then the large row from closer.
 */
@Mod.EventBusSubscriber(modid = Bastion.MOD_ID, value = Dist.CLIENT)
public final class HealthShowcase {
    private static final boolean ON = "health".equals(System.getProperty("bastion.showcase"));
    private static final BlockPos ORIGIN = new BlockPos(0, 150, 0);
    private static int tick = -1;

    private HealthShowcase() {
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
        Vec3 aim = ORIGIN.getCenter().add(0, 0.6, 0);
        switch (tick) {
            case 20 -> Showcase.server(server, HealthShowcase::build);
            case 30 -> {
                mc.options.hideGui = true;
                Showcase.camera(server, aim.add(0, 1.8, -6.5), aim);
            }
            case 90 -> Showcase.shot(mc, "health_day");
            case 92 -> Showcase.server(server, s -> s.overworld().setDayTime(18000));
            case 130 -> Showcase.shot(mc, "health_night_a");
            case 135 -> Showcase.shot(mc, "health_night_b");
            case 137 -> Showcase.camera(server, aim.add(0, 3.0, 1.0), aim.add(0, 0, 6));
            case 160 -> Showcase.shot(mc, "health_large");
            case 165 -> mc.stop();
            default -> {
            }
        }
    }

    private static void build(MinecraftServer server) {
        ServerLevel level = server.overworld();
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        level.setDayTime(6000);
        for (int x = -10; x <= 10; x++) {
            for (int z = -8; z <= 10; z++) {
                level.setBlock(ORIGIN.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
                for (int y = 0; y < 6; y++) level.setBlock(ORIGIN.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
            }
        }
        float[] lost = {0, 0.6f, 0.85f};
        for (int i = 0; i < lost.length; i++) {
            BlockPos pos = ORIGIN.offset(3 - 3 * i, 0, 0);
            Showcase.place(level, pos, Direction.UP, BastionItems.GUN_TURRET.get(), TurretTier.T1);
            TurretBaseBlockEntity turret = (TurretBaseBlockEntity) level.getBlockEntity(pos);
            turret.setEnabled(false);
            if (lost[i] > 0) turret.takeDamage(level.damageSources().generic(), turret.maxHealth() * lost[i]);
            BlockPos min = ORIGIN.offset(3 - 4 * i, 0, 5);
            LargeTurretBaseBlock.placeAt(level, min, BastionBlocks.LARGE_TURRET_BASE.get());
            TurretBaseBlockEntity large = (TurretBaseBlockEntity) level.getBlockEntity(min);
            large.upgradeTo(TurretTier.T2);
            large.upgradeTo(TurretTier.T3);
            large.inventory().setStackInSlot(TurretInventory.WEAPON, new ItemStack(BastionItems.TESLA_TURRET.get()));
            large.setEnabled(false);
            if (lost[i] > 0) large.takeDamage(level.damageSources().generic(), large.maxHealth() * lost[i]);
        }
    }
}
