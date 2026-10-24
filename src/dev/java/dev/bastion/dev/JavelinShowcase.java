package dev.bastion.dev;

import com.mojang.logging.LogUtils;
import dev.bastion.Bastion;
import dev.bastion.projectile.JavelinMissileEntity;
import dev.bastion.registry.BastionBlocks;
import dev.bastion.registry.BastionItems;
import dev.bastion.turret.FortressTurretBaseBlock;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.TurretInventory;
import dev.bastion.turret.TurretTier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Dev-only scene for the Javelin Missile (-Pshowcase=javelin, with -PshowcaseWorld set to a scratch copy of a world,
 * never the user's showcase world): a Fortress Turret Base with a Javelin, a cluster of husks with a cow among them, a
 * lone husk and one under a roof. Frames follow the launches (a flight is ~136 ticks, the next launch 160 after): the
 * silo close up (doors, cold launch, the hold, ignition, the climb speeding up), a wide view (the mark, the column, the
 * wait out of sight, the dive, the blast), the target field (the shrinking ring, the chevron, the blast), the wide view
 * at night, then the GUI and the item icons. Each new view is set up after the last blast (since 145).
 */
@Mod.EventBusSubscriber(modid = Bastion.MOD_ID, value = Dist.CLIENT)
public final class JavelinShowcase {
    private static final Logger LOG = LogUtils.getLogger();
    private static final boolean ON = "javelin".equals(System.getProperty("bastion.showcase"));
    private static final BlockPos ORIGIN = new BlockPos(0, 150, 0);
    private static final BlockPos FORT = ORIGIN.offset(0, 0, -12);
    /** The cluster (it is the densest: the Javelin's pick), the cow among it, the lone husk and the roofed one. */
    private static final Vec3[] HUSKS = {new Vec3(0.5, 0, 10.5), new Vec3(-1.5, 0, 11.5), new Vec3(1.5, 0, 12.5),
            new Vec3(-10.5, 0, 4.5), new Vec3(9.5, 0, 6.5)};
    private static final Vec3 COW = new Vec3(2.5, 0, 10.5);
    private static final List<Mob> TARGETS = new ArrayList<>();
    private static final Set<Integer> SEEN = new HashSet<>();
    private static final int[] LAUNCH = new int[8];
    private static int tick = -1, launches, endTick = -1;

    private JavelinShowcase() {
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
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof JavelinMissileEntity missile && SEEN.add(missile.getId()) && launches < LAUNCH.length) {
                LAUNCH[launches++] = tick;
                LOG.info("[showcase] t{} launch {}", tick, launches);
            }
        }
        if (tick == 20) Showcase.server(server, JavelinShowcase::build);
        // The module goes on once the camera is in place and the chunks are drawn: the first launch is the close-up's.
        if (tick == 70) Showcase.server(server, s -> {
            if (s.overworld().getBlockEntity(FORT) instanceof TurretBaseBlockEntity t) {
                t.inventory().setStackInSlot(TurretInventory.WEAPON, new ItemStack(BastionItems.JAVELIN_TURRET.get()));
            }
        });
        if (tick == 22) mc.options.hideGui = true;
        Vec3 silo = Vec3.atBottomCenterOf(FORT).add(0, 1, 0), field = Vec3.atBottomCenterOf(ORIGIN).add(0.5, 0, 11.5);
        // 1. The silo close up: the doors open as it acquires, the cold launch, the hold, ignition, the climb speeding up.
        if (tick == 30) Showcase.camera(server, silo.add(4.6, 2.6, -4.2), silo.add(0, 1.6, 0));
        if (launches == 0 && tick >= 66 && tick < 400 && tick % 3 == 0) Showcase.shot(mc, "jv_silo_wait_" + tick);
        int since = launches == 0 ? -1 : tick - LAUNCH[launches - 1];
        if (launches == 1 && since <= 50 && since % 2 == 0) Showcase.shot(mc, "jv_silo_" + since);
        // 2. Wide: silo, field and the sky over them: the mark, the column, the wait out of sight, the dive, the blast.
        if (launches == 1 && since == 145) Showcase.camera(server, silo.add(17, 7, 6), silo.add(0, 9, 12));
        if (launches == 2 && since <= 140 && since % 3 == 0) Showcase.shot(mc, "jv_wide_" + since);
        // 3. The target field: the ring shrinking, the chevron over the target, cyan then red, the blast and its smoke.
        if (launches == 2 && since == 145) Showcase.camera(server, field.add(6.5, 4.2, -5.5), field.add(0, 0.6, 0));
        if (launches == 3 && since >= 6 && since <= 150 && since % (since < 100 ? 6 : 2) == 0) Showcase.shot(mc, "jv_field_" + since);
        // 4. Night, wide.
        if (launches == 3 && since == 152) {
            Showcase.server(server, s -> s.overworld().setDayTime(18000));
            Showcase.camera(server, silo.add(17, 7, 6), silo.add(0, 9, 12));
        }
        if (launches == 4 && since <= 140 && since % 3 == 0) Showcase.shot(mc, "jv_night_" + since);
        // 5. Looking up from the field at the dive (day): it appears out of the sky.
        if (launches == 4 && since == 145) {
            Showcase.server(server, s -> s.overworld().setDayTime(6000));
            Showcase.camera(server, field.add(7, 1.5, 3), field.add(0, 22, 0));
        }
        if (launches == 5 && since >= 100 && since <= 140 && since % 2 == 0) Showcase.shot(mc, "jv_up_" + since);
        // 6. The GUI, then the icons.
        if (launches == 5 && since == 145) {
            mc.options.hideGui = false;
            Showcase.camera(server, silo.add(0.4, 1.0, -4.6), silo.add(0, -0.8, -1.2));
            endTick = tick;
        }
        if (endTick > 0 && tick == endTick + 8 && mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        }
        if (endTick > 0 && tick == endTick + 16) Showcase.shot(mc, "jv_gui");
        if (endTick > 0 && tick == endTick + 18) mc.setScreen(null);
        if (endTick > 0 && tick == endTick + 20) Showcase.server(server, s -> {
            ServerPlayer player = s.getPlayerList().getPlayers().get(0);
            Item[] items = {BastionItems.JAVELIN_TURRET.get(), BastionItems.JAVELIN_MISSILE.get(), BastionItems.FORTRESS_TURRET_BASE.get()};
            for (int i = 0; i < items.length; i++) player.getInventory().setItem(i + 1, new ItemStack(items[i]));
            for (String part : List.of("javelin_silo", "javelin_radar", "javelin_mount")) {
                BastionItems.PARTS.stream().filter(p -> p.getId().getPath().equals(part)).findFirst()
                        .ifPresent(p -> player.getInventory().add(new ItemStack(p.get())));
            }
            player.getInventory().selected = 1;
        });
        if (endTick > 0 && tick == endTick + 28) Showcase.shot(mc, "jv_hotbar");
        if ((endTick > 0 && tick == endTick + 36) || tick == 3000) mc.stop();
        if (tick > 20 && tick % 20 == 0) Showcase.server(server, JavelinShowcase::refill);
        if (tick > 40 && tick % 20 == 0) Showcase.server(server, s -> {
            if (s.overworld().getBlockEntity(FORT) instanceof TurretBaseBlockEntity t) {
                LOG.info("[showcase] t{} javelin {} target {} FE {}", tick, t.state(), t.target(), t.energy());
            }
        });
    }

    private static void build(MinecraftServer server) {
        ServerLevel level = server.overworld();
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
        level.setWeatherParameters(6000, 0, false, false);
        level.setDayTime(6000);
        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
        player.setGameMode(GameType.CREATIVE);
        player.getInventory().clearContent();
        for (int x = -22; x <= 22; x++) {
            for (int z = -22; z <= 24; z++) {
                level.setBlock(ORIGIN.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
                for (int y = 0; y < 14; y++) level.setBlock(ORIGIN.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
            }
        }
        // A roof on four posts over the third husk: never picked.
        BlockPos roofed = BlockPos.containing(Vec3.atBottomCenterOf(ORIGIN).add(HUSKS[4]));
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) level.setBlock(roofed.offset(x, 3, z), Blocks.STONE_BRICK_SLAB.defaultBlockState(), 2);
        for (int x : new int[]{-1, 1}) for (int z : new int[]{-1, 1}) for (int y = 0; y < 3; y++) level.setBlock(roofed.offset(x, y, z), Blocks.STONE_BRICK_WALL.defaultBlockState(), 2);
        FortressTurretBaseBlock.placeAt(level, FORT, BastionBlocks.FORTRESS_TURRET_BASE.get());
        if (level.getBlockEntity(FORT) instanceof TurretBaseBlockEntity t) {
            t.upgradeTo(TurretTier.T2);
            t.inventory().setStackInSlot(TurretInventory.AMMO_START, new ItemStack(BastionItems.CREATIVE_AMMO.get()));
        }
        refill(server);
    }

    /** Frozen 5000 HP husks and the cow, put back if they die. */
    private static void refill(MinecraftServer server) {
        ServerLevel level = server.overworld();
        TARGETS.removeIf(m -> !m.isAlive());
        if (!TARGETS.isEmpty() && TARGETS.size() < HUSKS.length + 1) {
            TARGETS.forEach(Entity::discard);
            TARGETS.clear();
        }
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
