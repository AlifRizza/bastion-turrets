package dev.bastion.dev;

import dev.bastion.Bastion;
import dev.bastion.turret.TurretTier;
import dev.bastion.turret.targeting.TargetFilter;
import dev.bastion.unit.BroodSpiderEntity;
import dev.bastion.unit.SpiderStats;
import dev.bastion.weapon.StatSheet;
import dev.bastion.weapon.WeaponDataLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Dev-only scene for the Brood spiders' climb and leaps (-Pshowcase=spiderleap with -PshowcaseWorld set to a scratch copy of
 * a world; user 2026-10-26): one spider climbs a 4-high pillar to the husk on top, one leaps at a husk 5 blocks off, and one
 * killed 4.5 blocks from a husk leaps at it and bursts there. A fixed side view, a frame every other tick.
 */
@Mod.EventBusSubscriber(modid = Bastion.MOD_ID, value = Dist.CLIENT)
public final class SpiderLeapShowcase {
    private static final boolean ON = "spiderleap".equals(System.getProperty("bastion.showcase"));
    private static final BlockPos ORIGIN = new BlockPos(0, 150, 0);
    private static int tick = -1;
    private static BroodSpiderEntity dying;

    private SpiderLeapShowcase() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (!ON || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        if (mc.player == null || server == null) return;
        mc.options.pauseOnLostFocus = false;
        if (mc.screen instanceof PauseScreen) mc.setScreen(null);
        if (tick < 0 && mc.screen != null) return;
        tick++;
        mc.getToasts().clear();
        Vec3 o = Vec3.atBottomCenterOf(ORIGIN);
        // 1: a spider climbs the pillar to the husk on top.
        if (tick == 10) Showcase.server(server, s -> {
            floor(s.overworld());
            husk(s.overworld(), Vec3.atBottomCenterOf(PILLAR.above(4)));
            spider(s.overworld(), Vec3.atBottomCenterOf(ORIGIN.offset(1, 0, 4)));
        });
        if (tick == 12) mc.options.hideGui = true;
        if (tick == 14) Showcase.camera(server, o.add(2.5, 3.2, -4.5), o.add(2.5, 1.0, 4));
        if (tick >= 20 && tick <= 70 && tick % 2 == 0) Showcase.shot(mc, "sl_climb_" + tick);
        // 2: a spider runs at a husk 7 blocks off and leaps at it from 5.
        if (tick == 72) Showcase.server(server, s -> {
            clear(s.overworld());
            husk(s.overworld(), Vec3.atBottomCenterOf(ORIGIN.offset(-1, 0, 7)));
            spider(s.overworld(), Vec3.atBottomCenterOf(ORIGIN.offset(-1, 0, 0)));
        });
        if (tick >= 74 && tick <= 120 && tick % 2 == 0) Showcase.shot(mc, "sl_leap_" + tick);
        // 3: a spider killed 4.5 blocks from a husk, before it hunts: it leaps at the husk and bursts there.
        if (tick == 122) Showcase.server(server, s -> {
            clear(s.overworld());
            husk(s.overworld(), Vec3.atBottomCenterOf(ORIGIN.offset(-6, 0, 10)).add(0, 0, 0.5));
            dying = spider(s.overworld(), Vec3.atBottomCenterOf(ORIGIN.offset(-6, 0, 6)));
        });
        if (tick == 122) Showcase.camera(server, o.add(-10.5, 2.2, 8.2), o.add(-6, 0.6, 8.2));
        if (tick == 125) Showcase.server(server, s -> dying.hurt(s.overworld().damageSources().generic(), 100));
        if (tick >= 124 && tick <= 146 && tick % 2 == 0) Showcase.shot(mc, "sl_death_" + (tick - 124));
        if (tick == 156) mc.stop();
    }

    private static final BlockPos PILLAR = ORIGIN.offset(6, 0, 4);

    private static void floor(ServerLevel level) {
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
        level.setDayTime(6000);
        clear(level);
        for (int x = -14; x <= 14; x++) {
            for (int z = -14; z <= 14; z++) {
                level.setBlockAndUpdate(ORIGIN.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState());
                for (int y = 0; y < 8; y++) level.setBlockAndUpdate(ORIGIN.offset(x, y, z), Blocks.AIR.defaultBlockState());
            }
        }
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) for (int y = 0; y < 4; y++) level.setBlockAndUpdate(PILLAR.offset(x, y, z), Blocks.STONE_BRICKS.defaultBlockState());
    }

    /** One husk and one spider a phase: the others gone. */
    private static void clear(ServerLevel level) {
        level.getEntitiesOfClass(Mob.class, new AABB(ORIGIN).inflate(30)).forEach(Mob::discard);
    }

    private static BroodSpiderEntity spider(ServerLevel level, Vec3 at) {
        SpiderStats stats = SpiderStats.of(StatSheet.of(WeaponDataLoader.get(Bastion.id("brood_cannon")), TurretTier.T1));
        return BroodSpiderEntity.spawn(level, at, ORIGIN, new TargetFilter(), null, stats);
    }

    /** A husk that stands still and lasts. */
    private static void husk(ServerLevel level, Vec3 at) {
        Husk husk = EntityType.HUSK.create(level);
        if (husk == null) return;
        husk.setNoAi(true);
        husk.setPersistenceRequired(); // made before the camera moves here: far from the player it would despawn at once
        husk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500);
        husk.setHealth(500);
        husk.moveTo(at.x, at.y, at.z, 180, 0);
        level.addFreshEntity(husk);
    }
}
