package dev.bastion.dev;

import com.mojang.logging.LogUtils;
import dev.bastion.Bastion;
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
 * Dev-only scene for the Railgun (-Pshowcase=railgun, with -PshowcaseWorld set to a scratch copy of a world, never the
 * user's showcase world): the barrel filling with light and crackling, the shot, the ion trail and the shockwave that
 * throws the husks around the target, by day and by night, then the GUI and the item icons.
 */
@Mod.EventBusSubscriber(modid = Bastion.MOD_ID, value = Dist.CLIENT)
public final class RailgunShowcase {
    private static final Logger LOG = LogUtils.getLogger();
    private static final boolean ON = "railgun".equals(System.getProperty("bastion.showcase"));
    private static final BlockPos ORIGIN = new BlockPos(0, 150, 0);
    /** The railgun's 2x2 runs from RAIL to RAIL + (1, 0, 1); it fires south down the lane. */
    private static final BlockPos RAIL = ORIGIN.offset(-1, 0, -10);
    /** The big target (it always picks the biggest) and two small husks beside it that the shockwave throws. */
    private static final Vec3 TARGET = new Vec3(0.5, 0, 14.5);
    private static final Vec3[] BYSTANDERS = {new Vec3(-1.5, 0, 15.5), new Vec3(2.5, 0, 13.5)};
    private static Husk target;
    private static final List<Husk> BYSTANDING = new ArrayList<>();
    private static int tick = -1;

    private RailgunShowcase() {
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
        if (tick == 20) Showcase.server(server, RailgunShowcase::build);
        if (tick == 22) mc.options.hideGui = true;
        Vec3 gun = Vec3.atLowerCornerOf(RAIL).add(1, 1, 1), target = Vec3.atBottomCenterOf(ORIGIN).add(TARGET);
        // 1. From the side, a full cycle: the light fills the channel, the arcs, the shot.
        if (tick == 30) Showcase.camera(server, gun.add(7.5, 2.5, 3.5), gun.add(0, 1.4, 2.6));
        if (tick >= 60 && tick < 270 && tick % 3 == 0) Showcase.shot(mc, "r_side_" + (tick - 60) / 3);
        // 2. The barrel up close from above.
        if (tick == 272) Showcase.camera(server, gun.add(3.2, 3.6, 1.0), gun.add(0, 1.3, 3.0));
        if (tick >= 280 && tick < 390 && tick % 3 == 0) Showcase.shot(mc, "r_barrel_" + (tick - 280) / 3);
        // 3. Downrange: the trail coming in and the shockwave at the target.
        if (tick == 392) Showcase.camera(server, target.add(6, 3.5, -6), target.add(-1, 1.0, -3));
        if (tick >= 400 && tick < 510 && tick % 3 == 0) Showcase.shot(mc, "r_impact_" + (tick - 400) / 3);
        // 4. Night, the whole lane from behind the gun.
        if (tick == 512) Showcase.server(server, s -> s.overworld().setDayTime(18000));
        if (tick == 514) Showcase.camera(server, gun.add(-4, 4.5, -5), gun.add(1, 1, 12));
        if (tick >= 520 && tick < 630 && tick % 3 == 0) Showcase.shot(mc, "r_night_" + (tick - 520) / 3);
        // 5. GUI, then the icons.
        if (tick == 632) {
            Showcase.server(server, s -> s.overworld().setDayTime(6000));
            mc.options.hideGui = false;
            Showcase.camera(server, gun.add(0.4, 1.2, -4.0), gun.add(0, -0.6, -0.4));
        }
        if (tick == 640 && mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        }
        if (tick == 648) Showcase.shot(mc, "r_gui");
        if (tick == 650) mc.setScreen(null);
        if (tick == 652) Showcase.server(server, s -> {
            ServerPlayer player = s.getPlayerList().getPlayers().get(0);
            Item[] items = {BastionItems.RAILGUN_TURRET.get(), BastionItems.RAIL_SLUGS.get(), BastionItems.LARGE_TURRET_BASE.get()};
            for (int i = 0; i < items.length; i++) player.getInventory().setItem(i + 1, new ItemStack(items[i]));
            player.getInventory().selected = 1;
        });
        if (tick == 660) Showcase.shot(mc, "r_hotbar");
        if (tick == 668) mc.stop();
        if (tick > 20 && tick % 10 == 0) Showcase.server(server, RailgunShowcase::refill);
        if (tick > 40 && tick % 20 == 0) Showcase.server(server, s -> {
            if (s.overworld().getBlockEntity(RAIL) instanceof TurretBaseBlockEntity t) LOG.info("[showcase] t{} railgun {}", tick, t.state());
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
        for (int x = -14; x <= 14; x++) {
            for (int z = -16; z <= 24; z++) {
                level.setBlock(ORIGIN.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
                for (int y = 0; y < 12; y++) level.setBlock(ORIGIN.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
            }
        }
        // A wall behind the target, so a missed slug and the dust have something to hit.
        for (int x = -6; x <= 6; x++) for (int y = 0; y < 4; y++) level.setBlock(ORIGIN.offset(x, y, 20), Blocks.STONE_BRICKS.defaultBlockState(), 2);
        LargeTurretBaseBlock.placeAt(level, RAIL, BastionBlocks.LARGE_TURRET_BASE.get());
        if (level.getBlockEntity(RAIL) instanceof TurretBaseBlockEntity t) {
            t.inventory().setStackInSlot(TurretInventory.WEAPON, new ItemStack(BastionItems.RAILGUN_TURRET.get()));
            t.inventory().setStackInSlot(TurretInventory.AMMO_START, new ItemStack(BastionItems.CREATIVE_AMMO.get())); // slugs and FE
        }
        refill(server);
    }

    /**
     * A frozen 5000 HP husk to shoot at, and two small husks beside it (physics on, no goals) that are put back on their
     * spots while the gun charges, so every shot throws them again.
     */
    private static void refill(MinecraftServer server) {
        ServerLevel level = server.overworld();
        Vec3 base = Vec3.atBottomCenterOf(ORIGIN);
        if (target == null || !target.isAlive()) {
            target = husk(level, base.add(TARGET), 5000);
            if (target != null) target.setNoAi(true);
        }
        boolean charging = level.getBlockEntity(RAIL) instanceof TurretBaseBlockEntity t && t.state() == TurretState.CHARGING;
        boolean displaced = BYSTANDING.size() < BYSTANDERS.length || BYSTANDING.stream().anyMatch(h -> !h.isAlive())
                || BYSTANDING.stream().anyMatch(h -> h.position().distanceTo(base.add(BYSTANDERS[BYSTANDING.indexOf(h)])) > 0.5);
        if (!charging || !displaced) return;
        BYSTANDING.forEach(Husk::discard);
        BYSTANDING.clear();
        for (Vec3 at : BYSTANDERS) {
            Husk h = husk(level, base.add(at), 20);
            if (h != null) BYSTANDING.add(h);
        }
    }

    private static Husk husk(ServerLevel level, Vec3 at, float health) {
        Husk husk = EntityType.HUSK.create(level);
        if (husk == null) return null;
        husk.moveTo(at.x, at.y, at.z, 180, 0);
        husk.removeFreeWill();
        husk.setPersistenceRequired();
        husk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
        husk.setHealth(health);
        level.addFreshEntity(husk);
        return husk;
    }
}
