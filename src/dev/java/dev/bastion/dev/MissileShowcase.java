package dev.bastion.dev;

import com.mojang.logging.LogUtils;
import dev.bastion.Bastion;
import dev.bastion.client.ClientTurret;
import dev.bastion.registry.BastionBlocks;
import dev.bastion.registry.BastionItems;
import dev.bastion.turret.LargeTurretBaseBlock;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.TurretInventory;
import dev.bastion.turret.TurretTier;
import dev.bastion.weapon.types.MissileLauncherWeapon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.core.BlockPos;
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
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Dev-only scene for the Large Turret Base and Missile Launcher (-Pshowcase=missiles): the models, a single-mode
 * launcher and a salvo launcher against a spread group of husks, missile flight and impacts, the GUI fire mode.
 */
@Mod.EventBusSubscriber(modid = Bastion.MOD_ID, value = Dist.CLIENT)
public final class MissileShowcase {
    private static final Logger LOG = LogUtils.getLogger();
    private static final boolean ON = "missiles".equals(System.getProperty("bastion.showcase"));
    private static final BlockPos ORIGIN = new BlockPos(0, 150, 0);
    private static final BlockPos SINGLE = ORIGIN.offset(-6, 0, 0), SALVO = ORIGIN.offset(4, 0, 0);
    private static final List<Husk> TARGETS = new ArrayList<>();
    private static int tick = -1;

    private MissileShowcase() {
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
        Vec3 single = Vec3.atLowerCornerOf(SINGLE).add(1, 0, 1), salvo = Vec3.atLowerCornerOf(SALVO).add(1, 0, 1);
        switch (tick) {
            case 20 -> Showcase.server(server, MissileShowcase::build);
            case 30 -> {
                mc.options.hideGui = true;
                Showcase.camera(server, salvo.add(3.2, 2.6, -3.6), salvo.add(0, 1.4, 0));
            }
            case 70 -> {
                Showcase.shot(mc, "m_launcher");
                logMouths(mc, server);
            }
            case 72 -> Showcase.camera(server, salvo.add(-1.2, 2.4, 4.2), salvo.add(0, 1.6, 0));
            case 90 -> Showcase.shot(mc, "m_front");
            case 92 -> Showcase.camera(server, single.add(-3.6, 2.2, -3.4), single.add(0, 1.2, 0));
            case 110 -> Showcase.shot(mc, "m_single_base");
            // Salvo: side view of the launch and the arcs.
            case 112 -> Showcase.camera(server, salvo.add(9, 4, 6), salvo.add(0, 3, 8));
            case 120 -> Showcase.server(server, s -> {
                for (Husk h : TARGETS) h.setInvisible(false);
                TurretBaseBlockEntity t = (TurretBaseBlockEntity) s.overworld().getBlockEntity(SALVO);
                t.setEnabled(true); // release the salvo
            });
            default -> {
            }
        }
        if (tick >= 122 && tick < 182 && tick % 2 == 0) Showcase.shot(mc, "m_salvo_" + (tick - 122) / 2);
        if (tick == 184) Showcase.camera(server, ORIGIN.getCenter().add(0, 6, -9), ORIGIN.getCenter().add(0, 1, 9));
        if (tick >= 190 && tick < 290 && tick % 4 == 0) Showcase.shot(mc, "m_wide_" + (tick - 190) / 4);
        if (tick == 292) Showcase.server(server, s -> s.overworld().setDayTime(18000));
        if (tick == 294) Showcase.camera(server, salvo.add(3.2, 2.6, -3.6), salvo.add(0, 1.4, 0));
        if (tick == 330) Showcase.shot(mc, "m_night");
        if (tick == 332) {
            Showcase.server(server, s -> {
                s.overworld().setDayTime(6000);
                ServerPlayer player = s.getPlayerList().getPlayers().get(0);
                Item[] items = {BastionItems.LARGE_TURRET_BASE.get(), BastionItems.MISSILE_LAUNCHER_TURRET.get(), BastionItems.MISSILES.get(),
                        BastionItems.DAMAGED_LARGE_TURRET_BASE.get(), BastionItems.TURRET_BASE.get()};
                for (int i = 0; i < items.length; i++) player.getInventory().setItem(i + 1, new ItemStack(items[i]));
            });
            mc.options.hideGui = false;
            Showcase.camera(server, single.add(0, 2.6, -3), single.add(0, 0.6, 0));
        }
        if (tick == 350) Showcase.shot(mc, "m_hotbar");
        if (tick == 352 && mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            LOG.info("[showcase] gui click on {}", mc.level.getBlockState(hit.getBlockPos()));
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        }
        if (tick == 362) Showcase.shot(mc, "m_gui");
        if (tick == 364) mc.setScreen(null);
        if (tick == 370) mc.stop();
        if (tick > 20 && tick % 20 == 0 && tick < 300) Showcase.server(server, MissileShowcase::refill);
    }

    /** Server spawn point of tubes 0 (pod -X) and 6 (pod +X) against the client's muzzle bones: the mirror check. */
    private static void logMouths(Minecraft mc, MinecraftServer server) {
        ClientTurret client = ClientTurret.of(SALVO);
        server.execute(() -> {
            TurretBaseBlockEntity t = (TurretBaseBlockEntity) server.overworld().getBlockEntity(SALVO);
            for (int tube : new int[]{0, 5, 6, 11}) {
                LOG.info("[showcase] tube {} server {} client {}", tube, MissileLauncherWeapon.mouth(t, t.inventory().weaponData(), tube),
                        client.muzzles[tube]);
            }
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
            for (int z = -12; z <= 32; z++) {
                level.setBlock(ORIGIN.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
                for (int y = 0; y < 14; y++) level.setBlock(ORIGIN.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
            }
        }
        place(level, SINGLE, TurretTier.T1, false);
        place(level, SALVO, TurretTier.T3, true);
        refill(server);
    }

    private static void place(ServerLevel level, BlockPos min, TurretTier tier, boolean salvo) {
        LargeTurretBaseBlock.placeAt(level, min, BastionBlocks.LARGE_TURRET_BASE.get());
        TurretBaseBlockEntity t = (TurretBaseBlockEntity) level.getBlockEntity(min);
        for (TurretTier step : TurretTier.values()) if (step.ordinal() > 0 && step.ordinal() <= tier.ordinal()) t.upgradeTo(step);
        t.inventory().setStackInSlot(TurretInventory.WEAPON, new ItemStack(BastionItems.MISSILE_LAUNCHER_TURRET.get()));
        t.inventory().setStackInSlot(TurretInventory.AMMO_START, new ItemStack(BastionItems.CREATIVE_AMMO.get()));
        t.weaponState().tubes = MissileLauncherWeapon.ALL_TUBES;
        if (salvo) {
            t.setSalvo(true);
            t.setEnabled(false); // held until the cameras are in place
        }
    }

    /** A spread group of tanky frozen husks downrange. */
    private static void refill(MinecraftServer server) {
        ServerLevel level = server.overworld();
        TARGETS.removeIf(h -> !h.isAlive());
        Vec3[] spots = {new Vec3(-6, 0, 18), new Vec3(-1, 0, 22), new Vec3(4, 0, 19), new Vec3(9, 0, 24), new Vec3(1, 0, 27)};
        while (TARGETS.size() < spots.length) {
            Husk husk = EntityType.HUSK.create(level);
            if (husk == null) return;
            Vec3 at = Vec3.atBottomCenterOf(ORIGIN).add(spots[TARGETS.size()]);
            husk.moveTo(at.x, at.y, at.z, 180, 0);
            husk.setNoAi(true);
            husk.setPersistenceRequired();
            husk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(400);
            husk.setHealth(400);
            level.addFreshEntity(husk);
            TARGETS.add(husk);
        }
    }
}
