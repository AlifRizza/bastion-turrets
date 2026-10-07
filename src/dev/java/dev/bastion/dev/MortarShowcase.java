package dev.bastion.dev;

import com.mojang.logging.LogUtils;
import dev.bastion.Bastion;
import dev.bastion.registry.BastionBlocks;
import dev.bastion.registry.BastionItems;
import dev.bastion.turret.LargeTurretBaseBlock;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.TurretInventory;
import dev.bastion.weapon.FirePatches;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
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
 * Dev-only scene for the Mortar (-Pshowcase=mortar, with -PshowcaseWorld set to a scratch copy of a world, never the
 * user's showcase world): the barrel firing, shells arcing over a wall, the fire patch burning husks and a cow and
 * setting the wooden floor alight, by day and by night, then the GUI and the item icons.
 */
@Mod.EventBusSubscriber(modid = Bastion.MOD_ID, value = Dist.CLIENT)
public final class MortarShowcase {
    private static final Logger LOG = LogUtils.getLogger();
    private static final boolean ON = "mortar".equals(System.getProperty("bastion.showcase"));
    private static final BlockPos ORIGIN = new BlockPos(0, 150, 0);
    /** The mortar's 2x2 runs from MORTAR to MORTAR + (1, 0, 1); its targets stand south, behind a wall. */
    private static final BlockPos MORTAR = ORIGIN.offset(-1, 0, -12);
    /** The first husk is the nearest (the one it shells); the cow stands beside it, inside the fire patch. */
    private static final Vec3[] HUSKS = {new Vec3(0.5, 0, 12.5), new Vec3(-2.5, 0, 16.5), new Vec3(-4.5, 0, 18.5)};
    private static final Vec3 COW = new Vec3(1.5, 0, 13.5);
    private static final List<Mob> TARGETS = new ArrayList<>();
    private static int tick = -1;

    private MortarShowcase() {
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
        if (tick == 20) Showcase.server(server, MortarShowcase::build);
        if (tick == 22) mc.options.hideGui = true;
        Vec3 gun = Vec3.atLowerCornerOf(MORTAR).add(1, 1, 1), field = Vec3.atBottomCenterOf(ORIGIN).add(0.5, 0, 14.5);
        // 1. The mortar from the side: the barrel up, the shot, the smoke ring.
        if (tick == 30) Showcase.camera(server, gun.add(5.5, 2.2, -3.5), gun.add(0, 1.4, 0.5));
        if (tick >= 60 && tick < 200 && tick % 3 == 0) Showcase.shot(mc, "m_gun_" + (tick - 60) / 3);
        // 2. Wide: the arc over the wall.
        if (tick == 202) Showcase.camera(server, gun.add(16, 10, 10), gun.add(0, 8, 14));
        if (tick >= 210 && tick < 330 && tick % 3 == 0) Showcase.shot(mc, "m_arc_" + (tick - 210) / 3);
        // 3. The target field: the burst, the patch, burning husks and cow, the planks catching fire.
        if (tick == 332) Showcase.camera(server, field.add(4, 3.4, 1), field.add(0, 0.3, -2));
        if (tick >= 340 && tick < 440 && tick % 3 == 0) Showcase.shot(mc, "m_field_" + (tick - 340) / 3);
        // 3b. A fire patch alone on bare stone, nothing in the way: the whole 3x3 must read as burning.
        Vec3 bare = Vec3.atBottomCenterOf(ORIGIN).add(8, 0, 9);
        if (tick == 442) Showcase.server(server, s -> FirePatches.start(s.overworld(), bare, new FirePatches.Patch(3, 100, 60, 1.5f)));
        if (tick == 443) Showcase.camera(server, bare.add(3.2, 3.0, -3.2), bare.add(0, 0.3, 0));
        if (tick >= 446 && tick < 482 && tick % 4 == 0) Showcase.shot(mc, "m_patch_" + (tick - 446) / 4);
        // 4. Night.
        if (tick == 482) Showcase.server(server, s -> s.overworld().setDayTime(18000));
        if (tick == 484) Showcase.camera(server, field.add(9, 6, -8), field.add(0, 0.5, 0));
        if (tick >= 490 && tick < 610 && tick % 3 == 0) Showcase.shot(mc, "m_night_" + (tick - 490) / 3);
        // 5. GUI, then the icons.
        if (tick == 612) {
            Showcase.server(server, s -> s.overworld().setDayTime(6000));
            mc.options.hideGui = false;
            Showcase.camera(server, gun.add(0.4, 1.2, -4.0), gun.add(0, -0.6, -0.4));
        }
        if (tick == 620 && mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        }
        if (tick == 628) Showcase.shot(mc, "m_gui");
        if (tick == 630) mc.setScreen(null);
        if (tick == 632) Showcase.server(server, s -> {
            ServerPlayer player = s.getPlayerList().getPlayers().get(0);
            Item[] items = {BastionItems.MORTAR_TURRET.get(), BastionItems.MORTAR_SHELLS.get(), BastionItems.LARGE_TURRET_BASE.get()};
            for (int i = 0; i < items.length; i++) player.getInventory().setItem(i + 1, new ItemStack(items[i]));
            player.getInventory().selected = 1;
        });
        if (tick == 640) Showcase.shot(mc, "m_hotbar");
        if (tick == 648) mc.stop();
        if (tick > 20 && tick % 20 == 0) Showcase.server(server, MortarShowcase::refill);
        if (tick > 40 && tick % 20 == 0) Showcase.server(server, s -> {
            if (s.overworld().getBlockEntity(MORTAR) instanceof TurretBaseBlockEntity t) LOG.info("[showcase] t{} mortar {}", tick, t.state());
        });
    }

    private static void build(MinecraftServer server) {
        ServerLevel level = server.overworld();
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(false, server); // the planks catch fire but it does not spread
        level.setWeatherParameters(6000, 0, false, false);
        level.setDayTime(6000);
        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
        player.setGameMode(GameType.CREATIVE);
        player.getInventory().clearContent();
        for (int x = -14; x <= 14; x++) {
            for (int z = -18; z <= 24; z++) {
                // Planks west of x 1 only: the patch lies half on wood (real fire) and half on stone (only Bastion's flames).
                level.setBlock(ORIGIN.offset(x, -1, z), (z >= 10 && z <= 20 && x >= -6 && x <= 0 ? Blocks.OAK_PLANKS : Blocks.SMOOTH_STONE).defaultBlockState(), 2);
                for (int y = 0; y < 26; y++) level.setBlock(ORIGIN.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
            }
        }
        // A wall between the mortar and its targets: it fires over it.
        for (int x = -6; x <= 6; x++) for (int y = 0; y < 5; y++) level.setBlock(ORIGIN.offset(x, y, 4), Blocks.STONE_BRICKS.defaultBlockState(), 2);
        LargeTurretBaseBlock.placeAt(level, MORTAR, BastionBlocks.LARGE_TURRET_BASE.get());
        if (level.getBlockEntity(MORTAR) instanceof TurretBaseBlockEntity t) {
            t.inventory().setStackInSlot(TurretInventory.WEAPON, new ItemStack(BastionItems.MORTAR_TURRET.get()));
            t.inventory().setStackInSlot(TurretInventory.AMMO_START, new ItemStack(BastionItems.CREATIVE_AMMO.get()));
        }
        refill(server);
    }

    /** Three frozen 5000 HP husks and a cow among them, put back if they die. */
    private static void refill(MinecraftServer server) {
        ServerLevel level = server.overworld();
        TARGETS.removeIf(m -> !m.isAlive());
        Vec3 base = Vec3.atBottomCenterOf(ORIGIN);
        while (TARGETS.size() < HUSKS.length + 1) {
            boolean cow = TARGETS.size() == HUSKS.length;
            Mob mob = (cow ? EntityType.COW : EntityType.HUSK).create(level);
            if (mob == null) return;
            Vec3 at = base.add(cow ? COW : HUSKS[TARGETS.size()]);
            mob.moveTo(at.x, at.y, at.z, 180, 0);
            mob.setNoAi(true);
            mob.setPersistenceRequired();
            mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(5000);
            mob.setHealth(5000);
            level.addFreshEntity(mob);
            TARGETS.add(mob);
        }
    }
}
