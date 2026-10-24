package dev.bastion.dev;

import com.mojang.logging.LogUtils;
import dev.bastion.Bastion;
import dev.bastion.registry.BastionBlocks;
import dev.bastion.registry.BastionItems;
import dev.bastion.turret.FortressTurretBaseBlock;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.TurretInventory;
import dev.bastion.turret.TurretTier;
import dev.bastion.unit.HiveDroneEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
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
 * Dev-only scene for the Hive Carrier (-Pshowcase=hive, with -PshowcaseWorld set to a scratch copy of a world, never the
 * user's showcase world): a Fortress with a Hive Carrier, frozen husks and a cow 16 blocks out, a phantom over them, a
 * helmeted skeleton that shoots the drones. Frames: a wave flying out of the hive's doorways, the drones patrolling and
 * shooting (wide and close), one drone diving kamikaze, the targets gone and the drones flying home to dock, night, then
 * the GUI and the item icons.
 */
@Mod.EventBusSubscriber(modid = Bastion.MOD_ID, value = Dist.CLIENT)
public final class HiveShowcase {
    private static final Logger LOG = LogUtils.getLogger();
    private static final boolean ON = "hive".equals(System.getProperty("bastion.showcase"));
    private static final BlockPos ORIGIN = new BlockPos(0, 150, 0);
    private static final BlockPos FORT = ORIGIN.offset(0, 0, -10);
    private static final Vec3[] HUSKS = {new Vec3(0.5, 0, 8.5), new Vec3(-2.5, 0, 9.5), new Vec3(2.5, 0, 10.5)};
    private static final Vec3 COW = new Vec3(1.5, 0, 7.5), PHANTOM = new Vec3(-4.5, 7, 6.5), SKELETON = new Vec3(9.5, 0, 4.5);
    private static final List<Mob> TARGETS = new ArrayList<>();
    private static int tick = -1, firstWave = -1, kamikazeTick = -1, homeTick = -1, nightTick = -1, endTick = -1;
    private static boolean refilling = true;

    private HiveShowcase() {
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
        if (firstWave < 0) {
            for (Entity entity : mc.level.entitiesForRendering()) {
                if (entity instanceof HiveDroneEntity) {
                    firstWave = tick;
                    LOG.info("[showcase] t{} first wave", tick);
                    break;
                }
            }
        }
        if (tick == 20) Showcase.server(server, HiveShowcase::build);
        if (tick == 22) mc.options.hideGui = true;
        Vec3 hive = Vec3.atBottomCenterOf(FORT).add(0, 1, 0), field = Vec3.atBottomCenterOf(ORIGIN).add(0.5, 0, 8.5);
        // 1. The carrier close up, low enough to see into its doorways: deploy, a wave flying out and climbing.
        if (tick == 30) Showcase.camera(server, hive.add(3.0, 1.9, -4.8), hive.add(0, 1.1, -0.4));
        if (tick == 70) Showcase.server(server, s -> {
            if (s.overworld().getBlockEntity(FORT) instanceof TurretBaseBlockEntity t) {
                t.inventory().setStackInSlot(TurretInventory.WEAPON, new ItemStack(BastionItems.HIVE_CARRIER_TURRET.get()));
            }
        });
        if (firstWave < 0 && tick >= 66 && tick < 300 && tick % 3 == 0) Showcase.shot(mc, "hc_bay_wait_" + tick);
        int since = firstWave < 0 ? -1 : tick - firstWave;
        if (firstWave > 0 && since <= 40 && since % 2 == 0) Showcase.shot(mc, "hc_launch_" + since);
        // 2. Wide: patrol and shooting.
        if (since == 44) Showcase.camera(server, hive.add(16, 10, 6), hive.add(0, 5, 10));
        if (since >= 50 && since <= 140 && since % 3 == 0) Showcase.shot(mc, "hc_wide_" + since);
        // 3. Close on the targets: tracers, hits.
        if (since == 144) Showcase.camera(server, field.add(5.5, 4.5, -5.0), field.add(0, 1.5, 0));
        if (since >= 148 && since <= 200 && since % 2 == 0) Showcase.shot(mc, "hc_field_" + since);
        // 4. One drone dives kamikaze (worn down by hand).
        if (since == 204) {
            kamikazeTick = tick;
            Showcase.server(server, s -> s.overworld().getEntitiesOfClass(HiveDroneEntity.class, new AABB(ORIGIN).inflate(40)).stream().findFirst()
                    .ifPresent(d -> d.setHealth(d.getMaxHealth() * 0.2f)));
        }
        if (kamikazeTick > 0 && tick > kamikazeTick && tick <= kamikazeTick + 50 && (tick - kamikazeTick) % 2 == 0) Showcase.shot(mc, "hc_kamikaze_" + (tick - kamikazeTick));
        // 5. The targets gone: the drones fly home and dock.
        if (kamikazeTick > 0 && tick == kamikazeTick + 54) {
            homeTick = tick;
            refilling = false;
            Showcase.server(server, s -> TARGETS.forEach(Entity::discard));
            Showcase.camera(server, hive.add(5.0, 2.6, -5.6), hive.add(0, 1.2, 0));
        }
        if (homeTick > 0 && tick > homeTick && tick <= homeTick + 240 && (tick - homeTick) % 6 == 0) Showcase.shot(mc, "hc_home_" + (tick - homeTick));
        // 6. Night: targets back, a wave from the docked drones.
        if (homeTick > 0 && tick == homeTick + 250) {
            nightTick = tick;
            refilling = true;
            Showcase.server(server, s -> s.overworld().setDayTime(18000));
            Showcase.camera(server, hive.add(16, 10, 6), hive.add(0, 5, 10));
        }
        if (nightTick > 0 && tick > nightTick && tick <= nightTick + 160 && (tick - nightTick) % 4 == 0) Showcase.shot(mc, "hc_night_" + (tick - nightTick));
        // 7. The GUI, then the icons.
        if (nightTick > 0 && tick == nightTick + 170) {
            Showcase.server(server, s -> s.overworld().setDayTime(6000));
            mc.options.hideGui = false;
            Showcase.camera(server, hive.add(0.4, 1.0, -4.6), hive.add(0, -0.8, -1.2));
            endTick = tick;
        }
        if (endTick > 0 && tick == endTick + 8 && mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        }
        if (endTick > 0 && tick == endTick + 16) Showcase.shot(mc, "hc_gui");
        if (endTick > 0 && tick == endTick + 18) mc.setScreen(null);
        if (endTick > 0 && tick == endTick + 20) Showcase.server(server, s -> {
            ServerPlayer player = s.getPlayerList().getPlayers().get(0);
            Item[] items = {BastionItems.HIVE_CARRIER_TURRET.get(), BastionItems.HIVE_DRONE.get(), BastionItems.FORTRESS_TURRET_BASE.get()};
            for (int i = 0; i < items.length; i++) player.getInventory().setItem(i + 1, new ItemStack(items[i]));
            for (String part : List.of("hive_hangar", "hive_relay", "hive_bays")) {
                BastionItems.PARTS.stream().filter(p -> p.getId().getPath().equals(part)).findFirst()
                        .ifPresent(p -> player.getInventory().add(new ItemStack(p.get())));
            }
            player.getInventory().selected = 1;
        });
        if (endTick > 0 && tick == endTick + 28) Showcase.shot(mc, "hc_hotbar");
        if ((endTick > 0 && tick == endTick + 36) || tick == 3000) mc.stop();
        if (refilling && tick > 20 && tick % 20 == 0) Showcase.server(server, HiveShowcase::refill);
        if (tick > 40 && tick % 40 == 0) Showcase.server(server, s -> {
            if (s.overworld().getBlockEntity(FORT) instanceof TurretBaseBlockEntity t) {
                LOG.info("[showcase] t{} hive {} docked {} drones {}", tick, t.state(), t.weaponState().dockedDrones,
                        s.overworld().getEntitiesOfClass(HiveDroneEntity.class, new AABB(ORIGIN).inflate(60)).size());
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
        for (int x = -24; x <= 24; x++) {
            for (int z = -24; z <= 24; z++) {
                level.setBlock(ORIGIN.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
                for (int y = 0; y < 24; y++) level.setBlock(ORIGIN.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
            }
        }
        FortressTurretBaseBlock.placeAt(level, FORT, BastionBlocks.FORTRESS_TURRET_BASE.get());
        if (level.getBlockEntity(FORT) instanceof TurretBaseBlockEntity t) {
            t.upgradeTo(TurretTier.T2);
            t.inventory().setStackInSlot(TurretInventory.AMMO_START, new ItemStack(BastionItems.CREATIVE_AMMO.get()));
        }
        refill(server);
    }

    /** Frozen 5000 HP husks, the cow, a frozen phantom over them, and a helmeted skeleton that shoots drones; put back if they die. */
    private static void refill(MinecraftServer server) {
        ServerLevel level = server.overworld();
        TARGETS.removeIf(m -> !m.isAlive());
        if (!TARGETS.isEmpty() && TARGETS.size() < HUSKS.length + 3) {
            TARGETS.forEach(Entity::discard);
            TARGETS.clear();
        }
        Vec3 base = Vec3.atBottomCenterOf(ORIGIN);
        while (TARGETS.size() < HUSKS.length + 3) {
            int i = TARGETS.size();
            EntityType<? extends Mob> type = i < HUSKS.length ? EntityType.HUSK : i == HUSKS.length ? EntityType.COW
                    : i == HUSKS.length + 1 ? EntityType.PHANTOM : EntityType.SKELETON;
            Mob mob = type.create(level);
            if (mob == null) return;
            Vec3 at = base.add(i < HUSKS.length ? HUSKS[i] : i == HUSKS.length ? COW : i == HUSKS.length + 1 ? PHANTOM : SKELETON);
            mob.moveTo(at.x, at.y, at.z, 180, 0);
            if (type != EntityType.SKELETON) mob.setNoAi(true);
            else mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            mob.setPersistenceRequired();
            mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(5000);
            mob.setHealth(5000);
            level.addFreshEntity(mob);
            TARGETS.add(mob);
        }
    }
}
