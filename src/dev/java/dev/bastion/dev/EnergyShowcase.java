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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Dev scene (src/dev, never shipped): {@code -Pshowcase=ion} = an Ion Rifle firing into a frozen cluster of husks (beams
 * from the core); {@code -Pshowcase=plasma} = a Plasma Repeater and a Plasma Cannon on a Large base firing at a cluster
 * (orbs, splashes, the ring). Side, close and night screenshots, then quits.
 */
@Mod.EventBusSubscriber(modid = Bastion.MOD_ID, value = Dist.CLIENT)
public final class EnergyShowcase {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String SCENE = System.getProperty("bastion.showcase", "");
    private static final boolean ON = SCENE.equals("ion") || SCENE.equals("plasma");
    private static final BlockPos ORIGIN = new BlockPos(0, 150, 0), CANNON = ORIGIN.offset(-6, 0, -1);
    private static final List<Husk> TARGETS = new ArrayList<>();
    private static int tick = -1;

    private EnergyShowcase() {
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
        Vec3 base = Vec3.atBottomCenterOf(ORIGIN);
        String p = SCENE.charAt(0) + "_";
        boolean ion = SCENE.equals("ion");
        // Ion: from the side, then beside the cluster. Plasma: from behind both turrets, then close on the Cannon's muzzle.
        Vec3 wideEye = ion ? base.add(10, 4.5, 2) : base.add(9, 5, 3), wideAt = ion ? base.add(1, 1.0, 7) : base.add(0, 1.0, 10.5);
        Vec3 closeEye = ion ? base.add(6, 2.6, 6.5) : base.add(-11.5, 2.4, 1.5), closeAt = ion ? base.add(0.5, 1.2, 10.5) : base.add(-5, 1.0, 1.5);
        if (tick == 20) Showcase.server(server, EnergyShowcase::build);
        if (tick == 22) mc.options.hideGui = true;
        if (tick == 24) Showcase.camera(server, wideEye, wideAt);
        if (tick >= 40 && tick < 240 && tick % 4 == 0) Showcase.shot(mc, p + "side_" + (tick - 40) / 4);
        if (tick == 242) Showcase.camera(server, closeEye, closeAt);
        if (tick >= 250 && tick < 350 && tick % 3 == 0) Showcase.shot(mc, p + "close_" + (tick - 250) / 3);
        if (tick == 352) {
            Showcase.server(server, s -> s.overworld().setDayTime(18000));
            Showcase.camera(server, wideEye, wideAt);
        }
        if (tick >= 360 && tick < 460 && tick % 3 == 0) Showcase.shot(mc, p + "night_" + (tick - 360) / 3);
        if (tick == 470) mc.stop();
        if (tick > 20 && tick % 20 == 0) Showcase.server(server, EnergyShowcase::refill);
        if (tick > 40 && tick % 10 == 0) Showcase.server(server, s -> {
            if (s.overworld().getBlockEntity(ORIGIN) instanceof TurretBaseBlockEntity t) LOG.info("[showcase] t{} {} {}", tick, SCENE, t.state());
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
        for (int x = -16; x <= 18; x++) {
            for (int z = -10; z <= 24; z++) {
                level.setBlock(ORIGIN.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
                for (int y = 0; y < 10; y++) level.setBlock(ORIGIN.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
            }
        }
        // The scratch world keeps what earlier scenes left (persistent husks, drops): clear the stage.
        level.getEntitiesOfClass(Entity.class, new AABB(ORIGIN.offset(-16, -1, -10).getCenter(), ORIGIN.offset(18, 10, 24).getCenter()),
                e -> !(e instanceof Player)).forEach(Entity::discard);
        for (int x = -3; x <= 5; x++) for (int y = 0; y < 3; y++) level.setBlock(ORIGIN.offset(x, y, 16), Blocks.STONE_BRICKS.defaultBlockState(), 2);
        if (SCENE.equals("ion")) {
            Showcase.place(level, ORIGIN, Direction.UP, BastionItems.ION_RIFLE_TURRET.get(), TurretTier.T1);
        } else {
            Showcase.place(level, ORIGIN, Direction.UP, BastionItems.PLASMA_REPEATER_TURRET.get(), TurretTier.T1);
            LargeTurretBaseBlock.placeAt(level, CANNON, BastionBlocks.LARGE_TURRET_BASE.get());
            TurretBaseBlockEntity cannon = (TurretBaseBlockEntity) level.getBlockEntity(CANNON);
            cannon.inventory().setStackInSlot(TurretInventory.WEAPON, new ItemStack(BastionItems.PLASMA_CANNON_TURRET.get()));
            cannon.inventory().setStackInSlot(TurretInventory.AMMO_START, new ItemStack(BastionItems.CREATIVE_AMMO.get()));
        }
        refill(server);
    }

    /** A frozen, tanky cluster down the lane: one in front, the rest within 1-3 blocks of it (field, splash and ring). */
    private static void refill(MinecraftServer server) {
        ServerLevel level = server.overworld();
        TARGETS.removeIf(h -> !h.isAlive());
        Vec3[] spots = {new Vec3(0.5, 0, 9.5), new Vec3(2.5, 0, 10.5), new Vec3(-1.5, 0, 11), new Vec3(0.5, 0, 12.5), new Vec3(3, 0, 12),
                new Vec3(-2.5, 0, 9)};
        while (TARGETS.size() < spots.length) {
            Husk husk = EntityType.HUSK.create(level);
            if (husk == null) return;
            Vec3 at = Vec3.atBottomCenterOf(ORIGIN).add(spots[TARGETS.size()]);
            husk.moveTo(at.x, at.y, at.z, 180, 0);
            husk.setNoAi(true);
            husk.setPersistenceRequired();
            husk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(4000);
            husk.setHealth(4000);
            level.addFreshEntity(husk);
            TARGETS.add(husk);
        }
    }
}
