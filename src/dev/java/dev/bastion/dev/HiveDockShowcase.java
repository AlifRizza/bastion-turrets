package dev.bastion.dev;

import com.mojang.logging.LogUtils;
import dev.bastion.Bastion;
import dev.bastion.registry.BastionBlocks;
import dev.bastion.registry.BastionItems;
import dev.bastion.turret.FortressTurretBaseBlock;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.TurretInventory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Dev-only check of the Hive Carrier's insides (-Pshowcase=hivedock, scratch world copy only; user 2026-10-24: docked
 * drones should show in the hollow hive, and new ones being made between waves). The camera looks in through the
 * south-east doorway: 1, 3, 6 and 9 drones docked on the racks, then a wave leaves for a frozen husk and frames every
 * two seconds show the new drones being welded together on the empty pads over the wait between waves (fire_interval).
 */
@Mod.EventBusSubscriber(modid = Bastion.MOD_ID, value = Dist.CLIENT)
public final class HiveDockShowcase {
    private static final Logger LOG = LogUtils.getLogger();
    private static final boolean ON = "hivedock".equals(System.getProperty("bastion.showcase"));
    private static final BlockPos HIVE = new BlockPos(0, 150, 0);
    private static final int[] DOCKED = {1, 3, 6, 9};
    private static int tick = -1;

    private HiveDockShowcase() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (!ON || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        if (mc.player == null || server == null || mc.level == null) return;
        mc.options.pauseOnLostFocus = false;
        if (mc.screen instanceof PauseScreen) mc.setScreen(null);
        tick++;
        Vec3 middle = Vec3.atBottomCenterOf(HIVE).add(0, 1, 0), se = new Vec3(0.866, 0, 0.5);
        if (tick == 20) Showcase.server(server, HiveDockShowcase::build);
        if (tick == 22) mc.options.hideGui = true;
        if (tick == 30) Showcase.camera(server, middle.add(se.scale(3.3)).add(0, 1.15, 0), middle.add(se.scale(0.6)).add(0, 0.9, 0));
        for (int i = 0; i < DOCKED.length; i++) {
            int n = DOCKED[i];
            if (tick == 80 + 40 * i) Showcase.server(server, s -> dock(s, n));
            if (tick == 100 + 40 * i) Showcase.shot(mc, "hd_docked_" + n);
        }
        if (tick == 245) Showcase.camera(server, middle.add(se.scale(3.0)).add(0, 2.2, 0), middle.add(se.scale(0.4)).add(0, 0.7, 0));
        if (tick == 250) Showcase.shot(mc, "hd_docked_9_high");
        if (tick == 260) {
            Showcase.camera(server, middle.add(se.scale(3.3)).add(0, 1.15, 0), middle.add(se.scale(0.6)).add(0, 0.9, 0));
            Showcase.server(server, s -> {
                dock(s, 0);
                ServerLevel level = s.overworld();
                Mob husk = EntityType.HUSK.create(level);
                if (husk == null) return;
                Vec3 at = Vec3.atBottomCenterOf(HIVE).add(-6, 0, -14); // off to the north-west, out of the shot
                husk.moveTo(at.x, at.y, at.z, 0, 0);
                husk.setNoAi(true);
                husk.setPersistenceRequired();
                husk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(5000);
                husk.setHealth(5000);
                level.addFreshEntity(husk);
            });
        }
        if (tick > 270 && tick <= 900 && tick % 40 == 0) Showcase.shot(mc, "hd_build_" + (tick - 270) / 20);
        if (tick == 910) mc.stop();
    }

    private static void build(MinecraftServer server) {
        ServerLevel level = server.overworld();
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        level.setDayTime(6000);
        server.getPlayerList().getPlayers().get(0).setGameMode(GameType.SPECTATOR);
        for (int x = -20; x <= 20; x++) {
            for (int z = -20; z <= 20; z++) {
                level.setBlock(HIVE.offset(x, -1, z), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                for (int y = 0; y < 12; y++) level.setBlock(HIVE.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
            }
        }
        FortressTurretBaseBlock.placeAt(level, HIVE, BastionBlocks.FORTRESS_TURRET_BASE.get());
        if (level.getBlockEntity(HIVE) instanceof TurretBaseBlockEntity t) {
            t.inventory().setStackInSlot(TurretInventory.WEAPON, new ItemStack(BastionItems.HIVE_CARRIER_TURRET.get()));
            t.inventory().setStackInSlot(TurretInventory.AMMO_START, new ItemStack(BastionItems.CREATIVE_AMMO.get()));
        }
    }

    /** Sets the docked count as docking does and sends it to the client. */
    private static void dock(MinecraftServer server, int n) {
        ServerLevel level = server.overworld();
        if (level.getBlockEntity(HIVE) instanceof TurretBaseBlockEntity t) {
            t.weaponState().dockedDrones = n;
            level.sendBlockUpdated(HIVE, t.getBlockState(), t.getBlockState(), Block.UPDATE_CLIENTS);
            LOG.info("[showcase] docked {}", n);
        }
    }
}
