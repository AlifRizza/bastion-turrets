package dev.bastion.dev;

import com.mojang.logging.LogUtils;
import dev.bastion.Bastion;
import dev.bastion.projectile.BroodCapsuleEntity;
import dev.bastion.registry.BastionBlocks;
import dev.bastion.registry.BastionItems;
import dev.bastion.turret.FortressTurretBaseBlock;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.TurretInventory;
import dev.bastion.turret.TurretTier;
import dev.bastion.turret.targeting.TargetFilter;
import dev.bastion.unit.BroodSpiderEntity;
import dev.bastion.unit.SpiderStats;
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
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Dev-only scene for the Brood Cannon (-Pshowcase=brood, with -PshowcaseWorld set to a scratch copy of a world, never the
 * user's showcase world): a Fortress with a Brood Cannon behind a wall, frozen husks with a cow among them beyond it, and a
 * husk that walks and goes for the spiders. Frames follow the capsules: the cannon close up (recoil, loader arm), a wide
 * view (the arc over the wall, landing), the field (petals, spiders emerging, walking, biting), a spider dying (red blink,
 * burst), the wide view at night, then the GUI and the item icons.
 */
@Mod.EventBusSubscriber(modid = Bastion.MOD_ID, value = Dist.CLIENT)
public final class BroodShowcase {
    private static final Logger LOG = LogUtils.getLogger();
    private static final boolean ON = "brood".equals(System.getProperty("bastion.showcase"));
    private static final BlockPos ORIGIN = new BlockPos(0, 150, 0);
    private static final BlockPos FORT = ORIGIN.offset(0, 0, -12);
    private static final Vec3[] HUSKS = {new Vec3(0.5, 0, 11.5), new Vec3(-1.5, 0, 12.5), new Vec3(2.5, 0, 13.5)};
    private static final Vec3 COW = new Vec3(1.5, 0, 10.5), WALKER = new Vec3(9.5, 0, 9.5);
    private static final List<Mob> TARGETS = new ArrayList<>();
    private static final Set<Integer> SEEN = new HashSet<>();
    private static final int[] LAUNCH = new int[8];
    private static int tick = -1, launches, endTick = -1, deathTick = -1;

    private BroodShowcase() {
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
            if (entity instanceof BroodCapsuleEntity capsule && SEEN.add(capsule.getId()) && launches < LAUNCH.length) {
                LAUNCH[launches++] = tick;
                LOG.info("[showcase] t{} launch {}", tick, launches);
            }
        }
        if (tick == 20) Showcase.server(server, BroodShowcase::build);
        if (tick == 22) mc.options.hideGui = true;
        // 0. A capsule alone on open ground, close up, before the cannon is mounted: landing, the petals, three spiders.
        Vec3 pod = Vec3.atBottomCenterOf(ORIGIN).add(12, 0, -6);
        if (tick == 24) Showcase.camera(server, pod.add(2.2, 2.0, -2.6), pod.add(0, 0.4, 0));
        if (tick == 28) Showcase.server(server, s -> {
            TargetFilter none = new TargetFilter();
            for (TargetFilter.Category category : TargetFilter.Category.values()) none.setCategory(category, false);
            BroodCapsuleEntity capsule = new BroodCapsuleEntity(s.overworld(), FORT, none, null, new SpiderStats(16, 0.27f, 8, 20, 16, 15, 4, 200), 3, 12, 20, 0.05f);
            capsule.setPos(pod.add(0, 6, 0));
            capsule.launch(new Vec3(0, -0.2, 0));
            s.overworld().addFreshEntity(capsule);
        });
        if (tick >= 30 && tick <= 90 && tick % 2 == 0) Showcase.shot(mc, "bc_pod_" + tick);
        if (tick == 92) Showcase.server(server, s -> s.overworld().getEntitiesOfClass(BroodSpiderEntity.class, new AABB(BlockPos.containing(pod)).inflate(8))
                .forEach(Entity::discard));
        if (tick == 100) Showcase.server(server, s -> {
            if (s.overworld().getBlockEntity(FORT) instanceof TurretBaseBlockEntity t) {
                t.inventory().setStackInSlot(TurretInventory.WEAPON, new ItemStack(BastionItems.BROOD_CANNON_TURRET.get()));
            }
        });
        Vec3 gun = Vec3.atBottomCenterOf(FORT).add(0, 1, 0), field = Vec3.atBottomCenterOf(ORIGIN).add(0.5, 0, 11.5);
        int since = launches == 0 ? -1 : tick - LAUNCH[launches - 1];
        // 1. The cannon close up: deploy, aim, the shot, recoil, the loader swinging the next capsule in.
        if (tick == 94) Showcase.camera(server, gun.add(5.2, 2.4, -4.4), gun.add(0, 1.4, 0));
        if (launches == 0 && tick >= 110 && tick < 300 && tick % 4 == 0) Showcase.shot(mc, "bc_gun_wait_" + tick);
        if (launches == 1 && since <= 36 && since % 2 == 0) Showcase.shot(mc, "bc_gun_" + since);
        // 2. Wide: the arc over the wall and the landing.
        if (launches == 1 && since == 40) Showcase.camera(server, gun.add(18, 9, 10), gun.add(0, 4, 12));
        if (launches == 2 && since <= 72 && since % 3 == 0) Showcase.shot(mc, "bc_wide_" + since);
        // 3. The field from above its target (the cow stood in the way from the side): landing, petals opening, spiders
        // climbing out, biting.
        Vec3 target = Vec3.atBottomCenterOf(ORIGIN).add(HUSKS[0]);
        if (launches == 2 && since == 80) Showcase.camera(server, target.add(2.4, 4.6, -3.0), target.add(0, 0.3, 0));
        if (launches == 3 && since >= 40 && since <= 120 && since % 2 == 0) Showcase.shot(mc, "bc_field_" + since);
        // 4. A spider dies: its eyes blink red, then the burst (killed by hand: the frozen husks never kill one).
        if (launches == 4 && since == 100) deathTick = tick;
        if (deathTick > 0 && tick == deathTick) Showcase.server(server, s -> {
            List<BroodSpiderEntity> spiders = s.overworld().getEntitiesOfClass(BroodSpiderEntity.class, new AABB(ORIGIN).inflate(30));
            spiders.sort(Comparator.comparingDouble(sp -> sp.distanceToSqr(Vec3.atCenterOf(ORIGIN))));
            if (spiders.isEmpty()) return;
            BroodSpiderEntity spider = spiders.get(0);
            Showcase.camera(s, spider.position().add(2.2, 1.4, -2.2), spider.position().add(0, 0.3, 0));
            for (int i = 0; i < Math.min(3, spiders.size()); i++) spiders.get(i).hurt(s.overworld().damageSources().generic(), 1000);
        });
        if (deathTick > 0 && tick > deathTick && tick <= deathTick + 24 && (tick - deathTick) % 2 == 0) Showcase.shot(mc, "bc_death_" + (tick - deathTick));
        // 5. Night, wide: three places free again, so it fires once more.
        if (deathTick > 0 && tick == deathTick + 30) {
            Showcase.server(server, s -> s.overworld().setDayTime(18000));
            Showcase.camera(server, gun.add(18, 9, 10), gun.add(0, 4, 12));
        }
        if (launches == 5 && since <= 90 && since % 3 == 0) Showcase.shot(mc, "bc_night_" + since);
        // 6. The GUI, then the icons.
        if (launches == 5 && since == 95) {
            Showcase.server(server, s -> s.overworld().setDayTime(6000));
            mc.options.hideGui = false;
            Showcase.camera(server, gun.add(0.4, 1.0, -4.6), gun.add(0, -0.8, -1.2));
            endTick = tick;
        }
        if (endTick > 0 && tick == endTick + 8 && mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        }
        if (endTick > 0 && tick == endTick + 16) Showcase.shot(mc, "bc_gui");
        if (endTick > 0 && tick == endTick + 18) mc.setScreen(null);
        if (endTick > 0 && tick == endTick + 20) Showcase.server(server, s -> {
            ServerPlayer player = s.getPlayerList().getPlayers().get(0);
            Item[] items = {BastionItems.BROOD_CANNON_TURRET.get(), BastionItems.BROOD_CAPSULE.get(), BastionItems.FORTRESS_TURRET_BASE.get()};
            for (int i = 0; i < items.length; i++) player.getInventory().setItem(i + 1, new ItemStack(items[i]));
            for (String part : List.of("brood_barrel", "brood_chamber", "brood_mount")) {
                BastionItems.PARTS.stream().filter(p -> p.getId().getPath().equals(part)).findFirst()
                        .ifPresent(p -> player.getInventory().add(new ItemStack(p.get())));
            }
            player.getInventory().selected = 1;
        });
        if (endTick > 0 && tick == endTick + 28) Showcase.shot(mc, "bc_hotbar");
        if ((endTick > 0 && tick == endTick + 36) || tick == 3000) mc.stop();
        if (tick > 20 && tick % 20 == 0) Showcase.server(server, BroodShowcase::refill);
        if (tick > 40 && tick % 40 == 0) Showcase.server(server, s -> {
            if (s.overworld().getBlockEntity(FORT) instanceof TurretBaseBlockEntity t) {
                LOG.info("[showcase] t{} brood {} target {} spiders {}", tick, t.state(), t.target(),
                        s.overworld().getEntitiesOfClass(BroodSpiderEntity.class, new AABB(ORIGIN).inflate(40)).size());
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
        // A wall between the cannon and its targets: it lobs over it.
        for (int x = -7; x <= 7; x++) for (int y = 0; y < 4; y++) level.setBlock(ORIGIN.offset(x, y, 0), Blocks.STONE_BRICKS.defaultBlockState(), 2);
        FortressTurretBaseBlock.placeAt(level, FORT, BastionBlocks.FORTRESS_TURRET_BASE.get());
        if (level.getBlockEntity(FORT) instanceof TurretBaseBlockEntity t) {
            t.upgradeTo(TurretTier.T2);
            t.inventory().setStackInSlot(TurretInventory.AMMO_START, new ItemStack(BastionItems.CREATIVE_AMMO.get()));
        }
        refill(server);
    }

    /** Frozen 5000 HP husks, the cow among them, and a walking husk that goes for the spiders; put back if they die. */
    private static void refill(MinecraftServer server) {
        ServerLevel level = server.overworld();
        TARGETS.removeIf(m -> !m.isAlive());
        if (!TARGETS.isEmpty() && TARGETS.size() < HUSKS.length + 2) {
            TARGETS.forEach(Entity::discard);
            TARGETS.clear();
        }
        Vec3 base = Vec3.atBottomCenterOf(ORIGIN);
        while (TARGETS.size() < HUSKS.length + 2) {
            int i = TARGETS.size();
            boolean cow = i == HUSKS.length, walker = i == HUSKS.length + 1;
            Mob mob = (cow ? EntityType.COW : EntityType.HUSK).create(level);
            if (mob == null) return;
            Vec3 at = base.add(cow ? COW : walker ? WALKER : HUSKS[i]);
            mob.moveTo(at.x, at.y, at.z, 180, 0);
            if (!walker) mob.setNoAi(true);
            mob.setPersistenceRequired();
            mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(5000);
            mob.setHealth(5000);
            level.addFreshEntity(mob);
            TARGETS.add(mob);
        }
    }
}
