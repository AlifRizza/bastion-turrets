package dev.bastion.gametest;

import dev.bastion.Bastion;
import dev.bastion.config.BastionConfig;
import dev.bastion.config.TurretStatsConfig;
import dev.bastion.projectile.BroodCapsuleEntity;
import dev.bastion.registry.BastionBlocks;
import dev.bastion.registry.BastionItems;
import dev.bastion.turret.FortressTurretBaseBlock;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.ManualControl;
import dev.bastion.turret.TurretInventory;
import dev.bastion.turret.TurretTier;
import dev.bastion.turret.targeting.TargetFilter;
import dev.bastion.unit.BroodSpiderEntity;
import dev.bastion.unit.SpiderStats;
import dev.bastion.unit.TurretUnits;
import dev.bastion.weapon.StatSheet;
import dev.bastion.workstation.WorkstationRecipe;
import dev.bastion.workstation.WorkstationType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Brood Cannon (PLAN "Turret 3x3"): its spiders bite only the turret's valid targets, leave units and turrets alone, are
 * hunted by hostile mobs, burst on death sparing non-targets, burst by themselves after a while with nothing to hunt,
 * spread over a crowd (spiders and cannons alike), fight on without a turret and are never saved. Spider tests run in the walled yard; the arc tests build a platform under open sky (as JavelinTests).
 */
@GameTestHolder(Bastion.MOD_ID)
@PrefixGameTestTemplate(false)
public class BroodTests {
    private static final String YARD = "yard";
    /**
     * The spec's numbers (brood_cannon.json): HP 16, speed 0.27, bite 8 every 20 ticks, sight 16, death 15 within 4, bursts
     * after 200 idle ticks, leaps up to 5 blocks in 8 ticks every 60 (user 2026-10-26).
     */
    static final SpiderStats STATS = new SpiderStats(16, 0.27f, 8, 20, 16, 15, 4, 200, 5, 8, 60);

    static BroodSpiderEntity spider(GameTestHelper helper, BlockPos at, BlockPos home, TargetFilter filter) {
        return BroodSpiderEntity.spawn(helper.getLevel(), helper.absoluteVec(Vec3.atBottomCenterOf(at)), helper.absolutePos(home), filter, null, STATS);
    }

    /** A husk or cow that stands still and has health to spare, so tests can read who was bitten. */
    static LivingEntity still(GameTestHelper helper, EntityType<? extends LivingEntity> type, BlockPos at) {
        LivingEntity mob = (LivingEntity) helper.spawnWithNoFreeWill((EntityType) type, at);
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500);
        mob.setHealth(500);
        return mob;
    }

    static float lost(LivingEntity mob) {
        return mob.getMaxHealth() - mob.getHealth();
    }

    private static TurretBaseBlockEntity fortress(GameTestHelper helper, BlockPos core) {
        FortressTurretBaseBlock.placeAt(helper.getLevel(), helper.absolutePos(core), BastionBlocks.FORTRESS_TURRET_BASE.get());
        return (TurretBaseBlockEntity) helper.getBlockEntity(core);
    }

    /** A husk 4 blocks off and a cow 4 blocks the other way: the husk is bitten, the cow never even targeted. */
    @GameTest(template = YARD, timeoutTicks = 300, batch = "spidersBiteOnlyValidTargets")
    public static void spidersBiteOnlyValidTargets(GameTestHelper helper) {
        BroodSpiderEntity spider = spider(helper, new BlockPos(10, 2, 14), new BlockPos(2, 2, 2), new TargetFilter());
        LivingEntity husk = still(helper, EntityType.HUSK, new BlockPos(14, 2, 14));
        LivingEntity cow = still(helper, EntityType.COW, new BlockPos(6, 2, 14));
        helper.onEachTick(() -> helper.assertFalse(spider.getTarget() == cow, "the spider went for the cow"));
        helper.succeedWhen(() -> {
            helper.assertTrue(lost(husk) > 0, "the husk was never bitten (target " + spider.getTarget() + ")");
            helper.assertTrue(lost(cow) == 0, "the cow was hurt: " + lost(cow));
        });
    }

    /**
     * Two spiders whose filter takes every category, and a Gun Turret beside them whose filter does too: after 100 ticks
     * nobody has a target and no round was fired. Units never fight units and turrets never aim at them.
     */
    @GameTest(template = YARD, timeoutTicks = 200, batch = "spidersLeaveUnitsAndTurretsAlone")
    public static void spidersLeaveUnitsAndTurretsAlone(GameTestHelper helper) {
        TargetFilter all = new TargetFilter();
        for (TargetFilter.Category category : TargetFilter.Category.values()) all.setCategory(category, true);
        BroodSpiderEntity a = spider(helper, new BlockPos(10, 2, 14), new BlockPos(2, 2, 2), all);
        BroodSpiderEntity b = spider(helper, new BlockPos(13, 2, 14), new BlockPos(2, 2, 2), all);
        BlockPos gun = new BlockPos(12, 2, 10);
        helper.setBlock(gun, BastionBlocks.TURRET_BASE.get());
        TurretBaseBlockEntity turret = (TurretBaseBlockEntity) helper.getBlockEntity(gun);
        turret.inventory().setStackInSlot(TurretInventory.WEAPON, new ItemStack(BastionItems.GUN_TURRET.get()));
        turret.inventory().setStackInSlot(TurretInventory.AMMO_START, new ItemStack(BastionItems.KINETIC_ROUNDS.get(), 10));
        turret.setFilter(all);
        helper.onEachTick(() -> {
            helper.assertTrue(a.getTarget() == null && b.getTarget() == null, "a spider targets " + a.getTarget() + " / " + b.getTarget());
            helper.assertTrue(turret.target() == null, "the turret targets " + turret.target());
        });
        helper.runAtTickTime(100, () -> {
            helper.assertTrue(turret.inventory().getStackInSlot(TurretInventory.AMMO_START).getCount() == 10, "the turret fired");
            turret.inventory().setStackInSlot(TurretInventory.WEAPON, ItemStack.EMPTY);
            helper.succeed();
        });
    }

    /** A zombie with no player near goes for a spider (hostile mobs hunt units, PLAN shared rules). */
    @GameTest(template = YARD, timeoutTicks = 200, batch = "hostileMobsTargetSpiders")
    public static void hostileMobsTargetSpiders(GameTestHelper helper) {
        TargetFilter none = new TargetFilter();
        for (TargetFilter.Category category : TargetFilter.Category.values()) none.setCategory(category, false);
        BroodSpiderEntity spider = spider(helper, new BlockPos(10, 2, 14), new BlockPos(2, 2, 2), none);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(18, 2, 14));
        helper.succeedWhen(() -> helper.assertTrue(zombie.getTarget() == spider, "the zombie targets " + zombie.getTarget()));
    }

    /** The numbers these tests use are the shipped ones (the jar's brood_cannon.json, not the config file). */
    @GameTest(template = YARD, batch = "spiderTestStatsAreTheShippedOnes")
    public static void spiderTestStatsAreTheShippedOnes(GameTestHelper helper) {
        SpiderStats shipped = SpiderStats.of(StatSheet.of(TurretStatsConfig.builtIn("brood_cannon"), TurretTier.T1));
        helper.assertTrue(shipped.equals(STATS), "brood_cannon.json gives " + shipped + ", the tests use " + STATS);
        helper.succeed();
    }

    /** A spider killed between a husk and a cow (2 blocks each) bursts: the husk loses ~15, the cow nothing. */
    @GameTest(template = YARD, timeoutTicks = 100, batch = "spiderDeathBlastSparesNonTargets")
    public static void spiderDeathBlastSparesNonTargets(GameTestHelper helper) {
        BroodSpiderEntity spider = spider(helper, new BlockPos(12, 2, 14), new BlockPos(2, 2, 2), new TargetFilter());
        LivingEntity husk = still(helper, EntityType.HUSK, new BlockPos(14, 2, 14));
        LivingEntity cow = still(helper, EntityType.COW, new BlockPos(10, 2, 14));
        helper.runAtTickTime(2, () -> spider.hurt(helper.getLevel().damageSources().generic(), 100));
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(spider.isRemoved(), "the spider is still there");
            helper.assertTrue(lost(husk) > 14 && lost(husk) <= 15, "the husk lost " + lost(husk));
            helper.assertTrue(lost(cow) == 0, "the cow lost " + lost(cow));
            helper.succeed();
        });
    }

    /**
     * Nothing to hunt for self_destruct_ticks: the spider bursts by itself and frees its place (user 2026-10-24: no idle
     * spiders piling up at their turret after a fight, holding the cap).
     */
    @GameTest(template = YARD, timeoutTicks = 300, batch = "spidersSelfDestructWhenIdle")
    public static void spidersSelfDestructWhenIdle(GameTestHelper helper) {
        BlockPos core = new BlockPos(5, 2, 14);
        TurretBaseBlockEntity turret = fortress(helper, core);
        BroodSpiderEntity spider = spider(helper, new BlockPos(20, 2, 14), core, new TargetFilter());
        helper.runAtTickTime(STATS.selfDestructTicks() - 20, () -> helper.assertTrue(spider.isAlive(), "it burst before self_destruct_ticks"));
        helper.runAfterDelay(STATS.selfDestructTicks() - 19, () -> helper.succeedWhen(() -> {
            helper.assertTrue(spider.isRemoved() && TurretUnits.count(helper.getLevel(), turret.getBlockPos()) == 0, "still there, " + spider.position());
            helper.getLevel().destroyBlock(turret.getBlockPos(), false);
        }));
    }

    /** Idle almost to the end, then a husk shows up in sight: the wait starts over and it hunts instead of bursting. */
    @GameTest(template = YARD, timeoutTicks = 400, batch = "spiderIdleTimerResetsOnAnEnemy")
    public static void spiderIdleTimerResetsOnAnEnemy(GameTestHelper helper) {
        BroodSpiderEntity spider = spider(helper, new BlockPos(20, 2, 14), new BlockPos(2, 2, 2), new TargetFilter());
        LivingEntity[] husk = new LivingEntity[1];
        // 100 ticks ahead: its target search may miss a few tries (1 in 5 every other tick); 40 failed ~1 % of runs.
        helper.runAtTickTime(STATS.selfDestructTicks() - 100, () -> husk[0] = still(helper, EntityType.HUSK, new BlockPos(24, 2, 14)));
        helper.runAtTickTime(STATS.selfDestructTicks() + 60, () -> {
            helper.assertTrue(spider.isAlive() && lost(husk[0]) > 0, "alive " + spider.isAlive() + ", the husk lost " + lost(husk[0]));
            spider.discard();
            husk[0].discard();
            helper.succeed();
        });
    }

    /** Three spiders at one spot, three husks in sight: each goes for a different husk, not all for the nearest. */
    @GameTest(template = YARD, timeoutTicks = 100, batch = "spidersSpreadOverTargets")
    public static void spidersSpreadOverTargets(GameTestHelper helper) {
        BlockPos home = new BlockPos(2, 2, 2);
        List<BroodSpiderEntity> spiders = List.of(spider(helper, new BlockPos(10, 2, 14), home, new TargetFilter()),
                spider(helper, new BlockPos(10, 2, 14), home, new TargetFilter()), spider(helper, new BlockPos(10, 2, 14), home, new TargetFilter()));
        List<LivingEntity> husks = List.of(still(helper, EntityType.HUSK, new BlockPos(14, 2, 10)), still(helper, EntityType.HUSK, new BlockPos(14, 2, 14)),
                still(helper, EntityType.HUSK, new BlockPos(14, 2, 18)));
        helper.succeedWhen(() -> {
            long distinct = spiders.stream().map(BroodSpiderEntity::getTarget).filter(t -> t != null && husks.contains(t)).distinct().count();
            helper.assertTrue(distinct == 3, "the three spiders go for " + distinct + " husk(s): " + spiders.stream().map(BroodSpiderEntity::getTarget).toList());
            spiders.forEach(Entity::discard);
            husks.forEach(Entity::discard);
        });
    }

    /** A capsule coming down on a husk's head hurts it (capsule_damage, ~10), never the husk two blocks off: no area. */
    @GameTest(template = YARD, timeoutTicks = 100, batch = "capsuleHurtsTheHeadItLandsOn")
    public static void capsuleHurtsTheHeadItLandsOn(GameTestHelper helper) {
        LivingEntity under = still(helper, EntityType.HUSK, new BlockPos(8, 2, 14)), beside = still(helper, EntityType.HUSK, new BlockPos(10, 2, 14));
        BroodCapsuleEntity capsule = new BroodCapsuleEntity(helper.getLevel(), helper.absolutePos(new BlockPos(2, 2, 2)), new TargetFilter(), null, STATS, 3, 12, 400, 0.05f)
                .aim(under, 10); // opens long after the test: no spider bites
        capsule.setPos(under.position().add(0, 5, 0));
        capsule.launch(new Vec3(0, -0.3, 0));
        helper.getLevel().addFreshEntity(capsule);
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(lost(under) > 9 && lost(under) <= 10, "the husk under it lost " + lost(under));
            helper.assertTrue(lost(beside) == 0, "the husk beside it lost " + lost(beside));
            capsule.discard();
            helper.succeed();
        });
    }

    /**
     * Only on the way down, and only above what it lands on: a capsule rising out through a husk does not hurt it, nor
     * does one stopping on a stone roof a fast fall would otherwise carry into the husk under it.
     */
    @GameTest(template = YARD, timeoutTicks = 100, batch = "capsuleHurtsOnlyOnTheWayDownAboveItsFloor")
    public static void capsuleHurtsOnlyOnTheWayDownAboveItsFloor(GameTestHelper helper) {
        LivingEntity risen = still(helper, EntityType.HUSK, new BlockPos(6, 2, 8)), roofed = still(helper, EntityType.HUSK, new BlockPos(14, 2, 20));
        helper.setBlock(new BlockPos(14, 4, 20), Blocks.STONE);
        BlockPos home = new BlockPos(2, 2, 2);
        BroodCapsuleEntity up = new BroodCapsuleEntity(helper.getLevel(), helper.absolutePos(home), new TargetFilter(), null, STATS, 3, 12, 400, 0.05f).aim(null, 10);
        up.setPos(risen.position().add(-1.2, 0.3, 0));
        up.launch(new Vec3(0.5, 0.45, 0)); // up through its head from the side, down again on the empty floor beyond
        BroodCapsuleEntity down = new BroodCapsuleEntity(helper.getLevel(), helper.absolutePos(home), new TargetFilter(), null, STATS, 3, 12, 400, 0.05f).aim(null, 10);
        down.setPos(roofed.position().add(0, 3.4, 0)); // just over the roof (the yard is 6 high, underground)
        down.launch(new Vec3(0, -1.5, 0)); // one tick from over the roof to inside the husk's head under it
        helper.getLevel().addFreshEntity(up);
        helper.getLevel().addFreshEntity(down);
        helper.runAtTickTime(60, () -> {
            helper.assertTrue(lost(risen) == 0, "the husk it rose out of lost " + lost(risen));
            helper.assertTrue(lost(roofed) == 0, "the husk under the roof lost " + lost(roofed) + " (capsule at " + down.position() + ")");
            up.discard();
            down.discard();
            helper.succeed();
        });
    }

    /** Two husks in range: the second capsule goes for the other one, not the husk the first is coming down on. */
    @GameTest(template = ARENA, timeoutTicks = 400, batch = "broodSpreadsItsCapsules")
    public static void broodSpreadsItsCapsules(GameTestHelper helper) {
        TurretBaseBlockEntity turret = brood(helper, 2, true);
        LivingEntity near = still(helper, EntityType.HUSK, core(helper).offset(12, 0, 0)), far = still(helper, EntityType.HUSK, core(helper).offset(16, 0, 4));
        java.util.Map<Integer, java.util.UUID> aims = new java.util.HashMap<>();
        Vec3 centre = helper.absoluteVec(Vec3.atCenterOf(core(helper)));
        helper.onEachTick(() -> helper.getLevel().getEntitiesOfClass(BroodCapsuleEntity.class, new AABB(centre, centre).inflate(60))
                .forEach(c -> aims.putIfAbsent(c.getId(), c.aimedAt())));
        helper.succeedWhen(() -> {
            helper.assertTrue(aims.size() >= 2, aims.size() + " capsule(s) fired");
            helper.assertTrue(aims.containsValue(near.getUUID()) && aims.containsValue(far.getUUID()), "aimed at " + aims.values()
                    + " (near " + near.getUUID() + ", far " + far.getUUID() + ")");
            done(helper, turret);
        });
    }

    /** Its Fortress is broken: the spider still bites a husk that turns up. */
    @GameTest(template = YARD, timeoutTicks = 300, batch = "spidersFightOnWithoutTheirTurret")
    public static void spidersFightOnWithoutTheirTurret(GameTestHelper helper) {
        BlockPos core = new BlockPos(5, 2, 14);
        TurretBaseBlockEntity turret = fortress(helper, core);
        BroodSpiderEntity spider = spider(helper, new BlockPos(12, 2, 14), core, new TargetFilter());
        helper.runAtTickTime(5, () -> helper.getLevel().destroyBlock(turret.getBlockPos(), false));
        LivingEntity[] husk = new LivingEntity[1];
        helper.runAtTickTime(10, () -> husk[0] = still(helper, EntityType.HUSK, new BlockPos(18, 2, 14)));
        helper.runAfterDelay(11, () -> helper.succeedWhen(() -> {
            helper.assertTrue(turret.isRemoved(), "the Fortress still stands");
            helper.assertTrue(spider.isAlive() && lost(husk[0]) > 0, "the husk was never bitten (target " + spider.getTarget() + ")");
        }));
    }

    /** Never written to the chunk, never despawned, counted while it lives and not after. */
    @GameTest(template = YARD, timeoutTicks = 40, batch = "spidersAreNeverSaved")
    public static void spidersAreNeverSaved(GameTestHelper helper) {
        BlockPos home = new BlockPos(2, 2, 2);
        BroodSpiderEntity spider = spider(helper, new BlockPos(12, 2, 14), home, new TargetFilter());
        helper.runAtTickTime(5, () -> {
            helper.assertFalse(spider.shouldBeSaved(), "a spider would be saved with its chunk");
            helper.assertFalse(spider.removeWhenFarAway(4096), "a spider would despawn");
            helper.assertTrue(TurretUnits.count(helper.getLevel(), helper.absolutePos(home)) == 1,
                    "counted " + TurretUnits.count(helper.getLevel(), helper.absolutePos(home)));
            spider.discard();
        });
        helper.runAtTickTime(7, () -> {
            helper.assertTrue(TurretUnits.count(helper.getLevel(), helper.absolutePos(home)) == 0, "a discarded spider still counts");
            helper.succeed();
        });
    }

    /**
     * A spider that skipped ticks (its chunk was unloaded, then loaded again: non-saved entities linger in memory) is gone
     * at its next tick and stops counting meanwhile: built and seen at tick 0, only added at tick 40.
     */
    @GameTest(template = YARD, timeoutTicks = 80, batch = "spidersVanishAfterSkippingTicks")
    public static void spidersVanishAfterSkippingTicks(GameTestHelper helper) {
        BlockPos home = new BlockPos(2, 2, 2);
        BroodSpiderEntity spider = BroodSpiderEntity.create(helper.getLevel(), helper.absoluteVec(new Vec3(12.5, 2, 14.5)), helper.absolutePos(home),
                new TargetFilter(), null, STATS);
        TurretUnits.ticked(spider);
        helper.runAtTickTime(30, () -> helper.assertTrue(TurretUnits.count(helper.getLevel(), helper.absolutePos(home)) == 0, "a spider that stopped ticking still counts"));
        helper.runAtTickTime(40, () -> helper.getLevel().addFreshEntity(spider));
        helper.runAtTickTime(45, () -> {
            helper.assertTrue(spider.isRemoved(), "a spider back from an unloaded chunk lives on");
            helper.succeed();
        });
    }

    /** Its owner hurts it only as the config's trustedCanDamage says (as a turret); a stranger always can. */
    @GameTest(template = YARD, timeoutTicks = 40, batch = "trustedPlayersCannotHurtSpiders")
    public static void trustedPlayersCannotHurtSpiders(GameTestHelper helper) {
        Player owner = helper.makeMockPlayer(), stranger = helper.makeMockPlayer();
        BroodSpiderEntity spider = BroodSpiderEntity.spawn(helper.getLevel(), helper.absoluteVec(new Vec3(12.5, 2, 14.5)), helper.absolutePos(new BlockPos(2, 2, 2)),
                new TargetFilter(), owner.getUUID(), STATS);
        helper.runAtTickTime(2, () -> {
            boolean allowed = BastionConfig.TRUSTED_CAN_DAMAGE.get();
            helper.assertTrue(spider.hurt(helper.getLevel().damageSources().playerAttack(owner), 3) == allowed,
                    "its owner " + (allowed ? "could not hurt it" : "hurt it") + " with trustedCanDamage " + allowed);
        });
        helper.runAtTickTime(15, () -> { // past the 10 ticks a hit makes it immune
            helper.assertTrue(spider.hurt(helper.getLevel().damageSources().playerAttack(stranger), 3), "a stranger could not hurt it");
            helper.succeed();
        });
    }

    // --- the cannon, its capsules and the cap (Task 2) --------------------------------------------------------

    private static final String ARENA = "arena";
    /** World height of the open-sky platform (GameTest runs underground: the arc needs the sky, as JavelinTests). */
    private static final int Y_SKY = 230;
    /** Wide enough that spiders strolling round the core never walk off it. */
    private static final int SIZE = 34;
    private static final int CAPACITY = 16000, PER_SHOT = 4000;

    private static BlockPos sky(GameTestHelper helper, int x, int y, int z) {
        return new BlockPos(x, Y_SKY - helper.absolutePos(BlockPos.ZERO).getY() + y, z);
    }

    private static BlockPos core(GameTestHelper helper) {
        return sky(helper, 8, 1, 17);
    }

    /** Clears the platform (and anything an earlier test left up there), lays it, places a Fortress with a Brood Cannon. */
    private static TurretBaseBlockEntity brood(GameTestHelper helper, int capsules, boolean charged) {
        clear(helper);
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                for (int y = 1; y < 8; y++) helper.setBlock(sky(helper, x, y, z), Blocks.AIR);
                helper.setBlock(sky(helper, x, 0, z), Blocks.STONE);
            }
        }
        TurretBaseBlockEntity turret = fortress(helper, core(helper));
        turret.inventory().setStackInSlot(TurretInventory.WEAPON, new ItemStack(BastionItems.BROOD_CANNON_TURRET.get()));
        if (capsules > 0) turret.inventory().setStackInSlot(TurretInventory.AMMO_START, new ItemStack(BastionItems.BROOD_CAPSULE.get(), capsules));
        if (charged) charge(helper);
        return turret;
    }

    private static void clear(GameTestHelper helper) {
        Vec3 centre = helper.absoluteVec(Vec3.atCenterOf(core(helper)));
        helper.getLevel().getEntitiesOfClass(Mob.class, new AABB(centre, centre).inflate(60)).forEach(Entity::discard);
        helper.getLevel().getEntitiesOfClass(BroodCapsuleEntity.class, new AABB(centre, centre).inflate(60)).forEach(Entity::discard);
    }

    private static void charge(GameTestHelper helper) {
        IEnergyStorage energy = helper.getBlockEntity(core(helper)).getCapability(ForgeCapabilities.ENERGY, Direction.UP).orElseThrow(IllegalStateException::new);
        while (energy.receiveEnergy(Integer.MAX_VALUE, false) > 0) ;
    }

    private static int capsules(TurretBaseBlockEntity turret) {
        return turret.inventory().getStackInSlot(TurretInventory.AMMO_START).getCount();
    }

    private static void done(GameTestHelper helper, TurretBaseBlockEntity turret) {
        turret.inventory().setStackInSlot(TurretInventory.WEAPON, ItemStack.EMPTY);
        clear(helper);
        helper.getLevel().destroyBlock(turret.getBlockPos(), false);
    }

    private static List<BroodSpiderEntity> spidersOf(GameTestHelper helper, BlockPos home, double radius) {
        Vec3 centre = Vec3.atCenterOf(home);
        return helper.getLevel().getEntitiesOfClass(BroodSpiderEntity.class, new AABB(centre, centre).inflate(radius), s -> s.home().equals(home));
    }

    /** No shot without FE, then one shot = one capsule and energy_per_shot FE. */
    @GameTest(template = ARENA, timeoutTicks = 300, batch = "broodNeedsCapsulesAndEnergy")
    public static void broodNeedsCapsulesAndEnergy(GameTestHelper helper) {
        TurretBaseBlockEntity turret = brood(helper, 1, false);
        still(helper, EntityType.HUSK, core(helper).offset(16, 0, 0));
        helper.runAtTickTime(80, () -> {
            helper.assertTrue(capsules(turret) == 1, "fired without FE: " + capsules(turret) + " capsules left");
            charge(helper);
        });
        helper.runAfterDelay(81, () -> helper.succeedWhen(() -> {
            helper.assertTrue(capsules(turret) == 0, "no shot after charging: " + capsules(turret) + " capsules, " + turret.energy() + " FE");
            helper.assertTrue(turret.energy() == CAPACITY - PER_SHOT, "energy after one shot: " + turret.energy());
            done(helper, turret);
        }));
    }

    /** A phantom and a husk inside min_range: no target, no shot. */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "broodIgnoresFlyersAndCloseTargets")
    public static void broodIgnoresFlyersAndCloseTargets(GameTestHelper helper) {
        TurretBaseBlockEntity turret = brood(helper, 4, true);
        helper.spawnWithNoFreeWill(EntityType.PHANTOM, core(helper).offset(14, 2, 0));
        still(helper, EntityType.HUSK, core(helper).offset(4, 0, 0));
        helper.runAtTickTime(120, () -> {
            helper.assertTrue(turret.target() == null && capsules(turret) == 4, "target " + turret.target() + ", capsules " + capsules(turret));
            done(helper, turret);
            helper.succeed();
        });
    }

    /** The capsule comes down near the husk, opens after open_ticks, three spiders of this turret climb out, the pod goes. */
    @GameTest(template = ARENA, timeoutTicks = 400, batch = "broodCapsuleReleasesThreeSpiders")
    public static void broodCapsuleReleasesThreeSpiders(GameTestHelper helper) {
        TurretBaseBlockEntity turret = brood(helper, 1, true);
        LivingEntity husk = still(helper, EntityType.HUSK, core(helper).offset(16, 0, 0));
        BroodCapsuleEntity[] capsule = new BroodCapsuleEntity[1];
        Vec3[] opened = new Vec3[1], huskAt = new Vec3[1]; // the spiders' bites push the husk about afterwards
        Vec3 centre = helper.absoluteVec(Vec3.atCenterOf(core(helper)));
        helper.onEachTick(() -> {
            if (capsule[0] == null) {
                List<BroodCapsuleEntity> found = helper.getLevel().getEntitiesOfClass(BroodCapsuleEntity.class, new AABB(centre, centre).inflate(60));
                if (!found.isEmpty()) capsule[0] = found.get(0);
            }
            if (capsule[0] != null && opened[0] == null && capsule[0].isOpen()) {
                opened[0] = capsule[0].position();
                huskAt[0] = husk.position();
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(opened[0] != null, "no capsule opened yet");
            double off = Math.hypot(opened[0].x - huskAt[0].x, opened[0].z - huskAt[0].z);
            helper.assertTrue(off < 3, "the capsule opened " + off + " blocks from the husk");
            helper.assertTrue(spidersOf(helper, turret.getBlockPos(), 40).size() == 3, "spiders of this turret: " + spidersOf(helper, turret.getBlockPos(), 40).size());
            helper.assertTrue(capsule[0].isRemoved(), "the empty pod is still there");
            done(helper, turret);
        });
    }

    /** Twelve spiders out: it waits, spending neither capsules nor FE; three of them gone: it fires again. */
    @GameTest(template = ARENA, timeoutTicks = 400, batch = "broodWaitsAtTheUnitCap")
    public static void broodWaitsAtTheUnitCap(GameTestHelper helper) {
        TurretBaseBlockEntity turret = brood(helper, 4, true);
        still(helper, EntityType.HUSK, core(helper).offset(16, 0, 0));
        TargetFilter none = new TargetFilter();
        for (TargetFilter.Category category : TargetFilter.Category.values()) none.setCategory(category, false);
        List<BroodSpiderEntity> out = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            BlockPos at = core(helper).offset(-3 + i % 4 * 2, 0, i < 4 ? -3 : i < 8 ? 3 : 4);
            out.add(BroodSpiderEntity.spawn(helper.getLevel(), helper.absoluteVec(Vec3.atBottomCenterOf(at)), turret.getBlockPos(), none, null, STATS));
        }
        helper.runAtTickTime(150, () -> {
            helper.assertTrue(capsules(turret) == 4 && turret.energy() == CAPACITY, "fired at the cap: " + capsules(turret) + " capsules, " + turret.energy() + " FE");
            out.subList(0, 3).forEach(Entity::discard);
        });
        helper.runAfterDelay(151, () -> helper.succeedWhen(() -> {
            helper.assertTrue(capsules(turret) == 3, "no shot once three spiders were gone: " + capsules(turret) + " capsules");
            done(helper, turret);
        }));
    }

    /** Eleven spiders count 11; one dies and bursts: 10. */
    @GameTest(template = YARD, timeoutTicks = 60, batch = "deadSpidersFreeTheirPlace")
    public static void deadSpidersFreeTheirPlace(GameTestHelper helper) {
        TargetFilter none = new TargetFilter();
        for (TargetFilter.Category category : TargetFilter.Category.values()) none.setCategory(category, false);
        BlockPos home = new BlockPos(2, 2, 2);
        List<BroodSpiderEntity> out = new ArrayList<>();
        for (int i = 0; i < 11; i++) out.add(spider(helper, new BlockPos(6 + i, 2, 14), home, none));
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(TurretUnits.count(helper.getLevel(), helper.absolutePos(home)) == 11, "counted " + TurretUnits.count(helper.getLevel(), helper.absolutePos(home)));
            out.get(0).hurt(helper.getLevel().damageSources().generic(), 100);
        });
        helper.runAtTickTime(25, () -> {
            helper.assertTrue(TurretUnits.count(helper.getLevel(), helper.absolutePos(home)) == 10, "counted " + TurretUnits.count(helper.getLevel(), helper.absolutePos(home)));
            out.forEach(Entity::discard);
            helper.succeed();
        });
    }

    /** One capsule comes down through a husk onto the floor, one onto water: both open and release their spiders. */
    @GameTest(template = YARD, timeoutTicks = 200, batch = "capsuleOpensWhereItLands")
    public static void capsuleOpensWhereItLands(GameTestHelper helper) {
        TargetFilter none = new TargetFilter();
        for (TargetFilter.Category category : TargetFilter.Category.values()) none.setCategory(category, false);
        LivingEntity husk = still(helper, EntityType.HUSK, new BlockPos(8, 2, 14));
        BlockPos pool = new BlockPos(20, 2, 14);
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) helper.setBlock(pool.offset(x, 0, z), x == 0 && z == 0 ? Blocks.WATER : Blocks.STONE);
        BlockPos homeA = new BlockPos(2, 2, 2), homeB = new BlockPos(2, 2, 26);
        BroodCapsuleEntity onHusk = capsule(helper, husk.position().add(0, 5, 0), homeA, none);
        BroodCapsuleEntity onWater = capsule(helper, helper.absoluteVec(Vec3.atBottomCenterOf(pool)).add(0, 5, 0), homeB, none);
        helper.succeedWhen(() -> {
            helper.assertTrue(onHusk.isRemoved() || onHusk.isOpen(), "the capsule over the husk never opened, at " + onHusk.position());
            helper.assertTrue(onWater.isRemoved() || onWater.isOpen(), "the capsule over the water never opened, at " + onWater.position());
            helper.assertTrue(spidersOf(helper, helper.absolutePos(homeA), 40).size() == 3, "spiders from the husk capsule: " + spidersOf(helper, helper.absolutePos(homeA), 40).size());
            helper.assertTrue(spidersOf(helper, helper.absolutePos(homeB), 40).size() == 3, "spiders from the water capsule: " + spidersOf(helper, helper.absolutePos(homeB), 40).size());
        });
    }

    private static BroodCapsuleEntity capsule(GameTestHelper helper, Vec3 at, BlockPos home, TargetFilter filter) {
        BroodCapsuleEntity capsule = new BroodCapsuleEntity(helper.getLevel(), helper.absolutePos(home), filter, null, STATS, 3, 12, 20, 0.05f);
        capsule.setPos(at);
        capsule.launch(new Vec3(0, -0.3, 0));
        helper.getLevel().addFreshEntity(capsule);
        return capsule;
    }

    /** Never saved; it holds its three places until it opens, none after. */
    @GameTest(template = YARD, timeoutTicks = 100, batch = "capsuleIsNeverSaved")
    public static void capsuleIsNeverSaved(GameTestHelper helper) {
        BlockPos home = new BlockPos(2, 2, 2);
        BroodCapsuleEntity capsule = capsule(helper, helper.absoluteVec(new Vec3(14.5, 4, 14.5)), home, new TargetFilter());
        helper.runAtTickTime(2, () -> {
            helper.assertFalse(capsule.shouldBeSaved(), "a capsule would be saved with its chunk");
            helper.assertTrue(TurretUnits.count(helper.getLevel(), helper.absolutePos(home)) == 3, "an unopened capsule counts " + TurretUnits.count(helper.getLevel(), helper.absolutePos(home)));
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(capsule.isOpen() || capsule.isRemoved(), "not open yet");
            helper.assertTrue(capsule.unitWeight() == 0, "an opened capsule still takes " + capsule.unitWeight() + " places");
            spidersOf(helper, helper.absolutePos(home), 40).forEach(Entity::discard);
        });
    }

    /** room: the turret's cap less what it has out, and never past the dimension's total (bastion-common maxTurretUnits). */
    @GameTest(template = YARD, timeoutTicks = 40, batch = "roomFollowsTheCapAndTheTotal")
    public static void roomFollowsTheCapAndTheTotal(GameTestHelper helper) {
        BlockPos a = new BlockPos(2, 2, 2), b = new BlockPos(2, 2, 26);
        List<BroodSpiderEntity> out = List.of(spider(helper, new BlockPos(10, 2, 14), a, new TargetFilter()), spider(helper, new BlockPos(12, 2, 14), a, new TargetFilter()));
        helper.runAtTickTime(5, () -> {
            int total = TurretUnits.total(helper.getLevel()), max = BastionConfig.maxTurretUnits();
            helper.assertTrue(TurretUnits.room(helper.getLevel(), helper.absolutePos(a), 12) == Math.max(0, Math.min(10, max - total)), "room at a");
            helper.assertTrue(TurretUnits.room(helper.getLevel(), helper.absolutePos(b), 12) == Math.max(0, Math.min(12, max - total)), "room at b");
            out.forEach(Entity::discard);
            helper.succeed();
        });
    }

    // --- recipes and Overwatch (Task 4) -----------------------------------------------------------------------

    /** Three parts at the Part Workstation, the module at the Part Assembler (400 ticks, 25,000 FE), the capsule at the Ammo Workstation. */
    @GameTest(template = YARD, batch = "broodRecipesExist")
    public static void broodRecipesExist(GameTestHelper helper) {
        for (String part : List.of("brood_barrel", "brood_chamber", "brood_mount")) {
            helper.assertTrue(helper.getLevel().getRecipeManager().byKey(Bastion.id("workstation/part_workstation/" + part)).isPresent(), "no recipe for " + part);
        }
        WorkstationRecipe module = FortressBaseTests.recipe(helper, "part_assembler/brood_cannon_turret", WorkstationType.PART_ASSEMBLER, "bastion:brood_cannon_turret",
                Map.of("bastion:brood_barrel", 1, "bastion:brood_chamber", 1, "bastion:brood_mount", 1));
        helper.assertTrue(module.time() == 400 && module.energy() == 25000, "module recipe " + module.time() + " ticks, " + module.energy() + " FE");
        FortressBaseTests.recipe(helper, "ammo_workstation/brood_capsule", WorkstationType.AMMO_WORKSTATION, "bastion:brood_capsule",
                Map.of("minecraft:iron_ingot", 3, "minecraft:copper_ingot", 1, "minecraft:redstone", 2, "minecraft:spider_eye", 1));
        helper.succeed();
    }

    /** Under Overwatch the capsule comes down on the spot under the crosshair (no creature there) and opens there. */
    @GameTest(template = ARENA, timeoutTicks = 400, batch = "manualBroodDropsOnTheCrosshair")
    public static void manualBroodDropsOnTheCrosshair(GameTestHelper helper) {
        TurretBaseBlockEntity turret = brood(helper, 1, true);
        ManualControl control = new ManualControl(UUID.randomUUID(), "Tester");
        turret.setManual(control);
        Vec3 spot = helper.absoluteVec(Vec3.atBottomCenterOf(core(helper).offset(16, 0, 4)));
        helper.onEachTick(() -> control.input(spot, -1, true, helper.getLevel().getGameTime()));
        Vec3 centre = helper.absoluteVec(Vec3.atCenterOf(core(helper)));
        helper.succeedWhen(() -> {
            List<BroodCapsuleEntity> open = helper.getLevel().getEntitiesOfClass(BroodCapsuleEntity.class, new AABB(centre, centre).inflate(60), BroodCapsuleEntity::isOpen);
            helper.assertTrue(!open.isEmpty(), "no capsule opened yet: " + turret.state() + ", " + capsules(turret) + " capsules");
            double off = Math.hypot(open.get(0).getX() - spot.x, open.get(0).getZ() - spot.z);
            helper.assertTrue(off < 1.5, "opened " + off + " blocks from the crosshair");
            turret.setManual(null);
            done(helper, turret);
        });
    }

    /** Under Overwatch, trigger held, it never fires inside min_range. */
    @GameTest(template = ARENA, timeoutTicks = 120, batch = "manualBroodRefusesTooClose")
    public static void manualBroodRefusesTooClose(GameTestHelper helper) {
        TurretBaseBlockEntity turret = brood(helper, 2, true);
        ManualControl control = new ManualControl(UUID.randomUUID(), "Tester");
        turret.setManual(control);
        Vec3 close = helper.absoluteVec(Vec3.atBottomCenterOf(core(helper).offset(4, 0, 0)));
        helper.onEachTick(() -> control.input(close, -1, true, helper.getLevel().getGameTime()));
        helper.runAtTickTime(80, () -> {
            helper.assertTrue(capsules(turret) == 2, "fired inside min_range");
            turret.setManual(null);
            done(helper, turret);
            helper.succeed();
        });
    }

    // --- final review fixes ---------------------------------------------------------------------------------

    /**
     * A husk on an island in a lava moat is nearer and in plain sight but cannot be reached (a pillar would not do: the
     * spider loses sight of a husk on top once it stands at the foot, and turns away anyway); the spider goes for the
     * husk on the floor instead of standing at the moat for ever.
     */
    @GameTest(template = YARD, timeoutTicks = 400, batch = "spidersSkipUnreachableTargets")
    public static void spidersSkipUnreachableTargets(GameTestHelper helper) {
        BlockPos island = new BlockPos(14, 2, 14);
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                int ring = Math.max(Math.abs(x), Math.abs(z));
                helper.setBlock(island.offset(x, 0, z), ring == 0 || ring == 3 ? Blocks.STONE : Blocks.LAVA);
            }
        }
        LivingEntity moated = still(helper, EntityType.HUSK, island.above());
        moated.setInvulnerable(false);
        LivingEntity floor = still(helper, EntityType.HUSK, new BlockPos(8, 2, 24));
        BroodSpiderEntity spider = spider(helper, new BlockPos(8, 2, 14), new BlockPos(2, 2, 2), new TargetFilter());
        helper.succeedWhen(() -> helper.assertTrue(lost(floor) > 0, "the reachable husk was never bitten (target "
                + (spider.getTarget() == moated ? "the husk behind the lava" : String.valueOf(spider.getTarget())) + ")"));
    }

    /**
     * Three husks it can see but never reach (islands in lava) and one on the floor another spider is already on: the
     * new spider does not keep trying the three (fewest spiders on them) till it bursts; it goes for the floor husk.
     */
    @GameTest(template = YARD, timeoutTicks = 300, batch = "spidersAreNotStarvedByUnreachableTargets")
    public static void spidersAreNotStarvedByUnreachableTargets(GameTestHelper helper) {
        for (int z : new int[]{8, 14, 20}) {
            BlockPos island = new BlockPos(14, 2, z);
            for (int x = -3; x <= 3; x++) {
                for (int dz = -3; dz <= 3; dz++) {
                    int ring = Math.max(Math.abs(x), Math.abs(dz));
                    helper.setBlock(island.offset(x, 0, dz), ring == 0 || ring == 3 ? Blocks.STONE : Blocks.LAVA);
                }
            }
            still(helper, EntityType.HUSK, island.above());
        }
        LivingEntity floor = still(helper, EntityType.HUSK, new BlockPos(6, 2, 24));
        BroodSpiderEntity first = spider(helper, new BlockPos(6, 2, 22), new BlockPos(2, 2, 2), new TargetFilter());
        first.setTarget(floor);
        BroodSpiderEntity spider = spider(helper, new BlockPos(6, 2, 14), new BlockPos(2, 2, 2), new TargetFilter());
        helper.succeedWhen(() -> {
            helper.assertTrue(spider.isAlive() && spider.getTarget() == floor, "the new spider goes for " + spider.getTarget() + " (alive " + spider.isAlive() + ")");
            first.discard();
            spider.discard();
        });
    }

    /** Two cannons side by side, two husks, the same one nearest to both: they go for different husks, not the same one. */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "broodCannonsSpreadOverEachOthersTargets")
    public static void broodCannonsSpreadOverEachOthersTargets(GameTestHelper helper) {
        TurretBaseBlockEntity first = brood(helper, 2, true);
        BlockPos other = core(helper).offset(0, 0, 6);
        TurretBaseBlockEntity second = fortress(helper, other);
        second.inventory().setStackInSlot(TurretInventory.WEAPON, new ItemStack(BastionItems.BROOD_CANNON_TURRET.get()));
        second.inventory().setStackInSlot(TurretInventory.AMMO_START, new ItemStack(BastionItems.BROOD_CAPSULE.get(), 2));
        IEnergyStorage energy = helper.getBlockEntity(other).getCapability(ForgeCapabilities.ENERGY, Direction.UP).orElseThrow(IllegalStateException::new);
        while (energy.receiveEnergy(Integer.MAX_VALUE, false) > 0) ;
        LivingEntity near = still(helper, EntityType.HUSK, core(helper).offset(12, 0, 3)), far = still(helper, EntityType.HUSK, core(helper).offset(18, 0, 3));
        // The first tick both hold a target, before either has fired (a capsule in the air would spread them anyway).
        String[] picked = new String[1];
        helper.onEachTick(() -> {
            if (picked[0] == null && first.target() != null && second.target() != null) {
                picked[0] = first.target() == second.target() ? "both on " + (first.target() == near ? "the near husk" : "the far husk") : "apart";
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue("apart".equals(picked[0]), "first picks: " + picked[0]);
            done(helper, first);
            done(helper, second);
        });
    }

    /** Units never ride into a save (a boat's passengers), through a portal, or on a lead. */
    @GameTest(template = YARD, timeoutTicks = 20, batch = "unitsCannotBeSavedOrCloned")
    public static void unitsCannotBeSavedOrCloned(GameTestHelper helper) {
        BroodSpiderEntity spider = spider(helper, new BlockPos(12, 2, 14), new BlockPos(2, 2, 2), new TargetFilter());
        BroodCapsuleEntity capsule = capsule(helper, helper.absoluteVec(new Vec3(16.5, 4, 14.5)), new BlockPos(2, 2, 2), new TargetFilter());
        helper.assertFalse(spider.saveAsPassenger(new CompoundTag()), "a spider would be saved as a passenger");
        helper.assertFalse(capsule.saveAsPassenger(new CompoundTag()), "a capsule would be saved as a passenger");
        helper.assertFalse(spider.canChangeDimensions(), "a spider could go through a portal");
        helper.assertFalse(capsule.canChangeDimensions(), "a capsule could go through a portal");
        helper.assertFalse(spider.canBeLeashed(helper.makeMockPlayer()), "a spider could be leashed");
        spider.discard();
        capsule.discard();
        helper.succeed();
    }

    /** open_ticks 0 (the config allows it): the capsule opens as it lands. */
    @GameTest(template = YARD, timeoutTicks = 100, batch = "capsuleWithNoWaitOpensAtOnce")
    public static void capsuleWithNoWaitOpensAtOnce(GameTestHelper helper) {
        BlockPos home = new BlockPos(2, 2, 2);
        BroodCapsuleEntity capsule = new BroodCapsuleEntity(helper.getLevel(), helper.absolutePos(home), new TargetFilter(), null, STATS, 3, 12, 0, 0.05f);
        capsule.setPos(helper.absoluteVec(new Vec3(14.5, 4, 14.5)));
        capsule.launch(new Vec3(0, -0.3, 0));
        helper.getLevel().addFreshEntity(capsule);
        helper.succeedWhen(() -> {
            helper.assertTrue(spidersOf(helper, helper.absolutePos(home), 40).size() == 3, "no spiders: the capsule never opened");
            spidersOf(helper, helper.absolutePos(home), 40).forEach(Entity::discard);
        });
    }

    /** The tick a capsule opens, its three spiders already count (else the turret saw room for one more capsule). */
    @GameTest(template = YARD, timeoutTicks = 100, batch = "openingCapsuleKeepsItsPlaces")
    public static void openingCapsuleKeepsItsPlaces(GameTestHelper helper) {
        BlockPos home = new BlockPos(2, 2, 2);
        BroodCapsuleEntity capsule = capsule(helper, helper.absoluteVec(new Vec3(14.5, 4, 14.5)), home, new TargetFilter());
        boolean[] checked = new boolean[1];
        helper.onEachTick(() -> {
            if (checked[0] || !capsule.isOpen()) return;
            checked[0] = true;
            int count = TurretUnits.count(helper.getLevel(), helper.absolutePos(home));
            helper.assertTrue(count == 3, "the tick it opened its places counted " + count);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(checked[0], "not open yet");
            spidersOf(helper, helper.absolutePos(home), 40).forEach(Entity::discard);
        });
    }

    /** The server stopping empties the unit registry (it would keep a closed world's entities, and its server, alive). */
    @GameTest(template = YARD, timeoutTicks = 20, batch = "serverStopForgetsUnits")
    public static void serverStopForgetsUnits(GameTestHelper helper) {
        BlockPos home = new BlockPos(2, 2, 2);
        BroodSpiderEntity spider = spider(helper, new BlockPos(12, 2, 14), home, new TargetFilter());
        helper.runAtTickTime(3, () -> {
            helper.assertTrue(TurretUnits.count(helper.getLevel(), helper.absolutePos(home)) == 1, "not counted");
            TurretUnits.onServerStopping(new net.minecraftforge.event.server.ServerStoppingEvent(helper.getLevel().getServer()));
            helper.assertTrue(TurretUnits.tracked() == 0, "still tracking " + TurretUnits.tracked() + " units");
            spider.discard();
            helper.succeed();
        });
    }

    private static double horizontalSpeed(BroodSpiderEntity spider) {
        return spider.getDeltaMovement().horizontalDistance();
    }

    /** User 2026-10-26: spiders climb walls like vanilla spiders: a husk up on a 4-high pillar gets bitten. */
    @GameTest(template = YARD, timeoutTicks = 400, batch = "spidersClimbToTargetsOnWalls")
    public static void spidersClimbToTargetsOnWalls(GameTestHelper helper) {
        BlockPos pillar = new BlockPos(14, 2, 14);
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) for (int y = 0; y < 4; y++) helper.setBlock(pillar.offset(x, y, z), Blocks.STONE);
        LivingEntity husk = still(helper, EntityType.HUSK, pillar.above(4));
        BroodSpiderEntity spider = spider(helper, new BlockPos(7, 2, 14), new BlockPos(2, 2, 2), new TargetFilter());
        helper.succeedWhen(() -> {
            helper.assertTrue(lost(husk) > 0, "the husk on the pillar was never bitten (spider at " + spider.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO))
                    + ", target " + spider.getTarget() + ")");
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) for (int y = 0; y < 4; y++) helper.setBlock(pillar.offset(x, y, z), Blocks.AIR);
            spider.discard();
        });
    }

    /**
     * User 2026-10-26: a spider leaps at prey within leap_range (5 blocks) and bites on arrival; farther off it runs. A husk
     * 9 blocks away: no leap while it is over 5.5 blocks off, then a leap (far faster than running) and a bite as it
     * arrives (review M7: within its flight time).
     */
    @GameTest(template = YARD, timeoutTicks = 200, batch = "spidersLeapAtPreyInRange")
    public static void spidersLeapAtPreyInRange(GameTestHelper helper) {
        LivingEntity husk = still(helper, EntityType.HUSK, new BlockPos(17, 2, 14));
        BroodSpiderEntity spider = spider(helper, new BlockPos(8, 2, 14), new BlockPos(2, 2, 2), new TargetFilter());
        long[] leapt = {-1}, bitten = {-1};
        helper.onEachTick(() -> {
            if (bitten[0] < 0 && lost(husk) > 0) bitten[0] = helper.getTick();
            if (!spider.isAlive() || horizontalSpeed(spider) <= 0.45 || leapt[0] >= 0) return;
            double off = Math.sqrt(spider.distanceToSqr(husk));
            helper.assertTrue(off <= 5.5 + 0.9, "a leap from " + off + " blocks off (leap_range 5)");
            leapt[0] = helper.getTick();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(leapt[0] >= 0, "the spider never leapt (it walked all the way)");
            helper.assertTrue(bitten[0] >= 0, "the leap ended without a bite");
            helper.assertTrue(bitten[0] - leapt[0] <= STATS.leapTicks() + 4, "bitten " + (bitten[0] - leapt[0]) + " ticks after the leap (want on arrival)");
            spider.discard();
        });
    }

    /**
     * User 2026-10-26: at 0 HP the spider does not burst where it stands: it leaps at the nearest valid target within
     * leap_range and bursts on it. A husk 4.8 blocks off (out of its 4-block burst from where it stood) takes the 15; a cow
     * 2 blocks the other way nothing.
     */
    @GameTest(template = YARD, timeoutTicks = 100, batch = "dyingSpiderLeapsAndBurstsOnTheEnemy")
    public static void dyingSpiderLeapsAndBurstsOnTheEnemy(GameTestHelper helper) {
        BroodSpiderEntity spider = spider(helper, new BlockPos(10, 2, 14), new BlockPos(2, 2, 2), new TargetFilter());
        LivingEntity husk = still(helper, EntityType.HUSK, new BlockPos(10, 2, 14));
        husk.moveTo(husk.getX() + 4.8, husk.getY(), husk.getZ());
        LivingEntity cow = still(helper, EntityType.COW, new BlockPos(8, 2, 14));
        helper.runAtTickTime(2, () -> spider.hurt(helper.getLevel().damageSources().generic(), 100));
        helper.runAtTickTime(60, () -> {
            helper.assertTrue(spider.isRemoved(), "the dying spider is still there");
            helper.assertTrue(lost(husk) > 14 && lost(husk) <= 15, "the husk 4.8 blocks off lost " + lost(husk) + " (want the 15 of a burst on it)");
            helper.assertTrue(lost(cow) == 0, "the cow lost " + lost(cow));
            helper.succeed();
        });
    }

    /** Review I1: a spider set on a husk across a lava moat gives it up and never walks into the lava after it. */
    @GameTest(template = YARD, timeoutTicks = 260, batch = "spidersNeverWalkIntoAMoat")
    public static void spidersNeverWalkIntoAMoat(GameTestHelper helper) {
        BlockPos island = new BlockPos(14, 2, 14);
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                int ring = Math.max(Math.abs(x), Math.abs(z));
                helper.setBlock(island.offset(x, 0, z), ring == 0 || ring == 3 ? Blocks.STONE : Blocks.LAVA);
            }
        }
        LivingEntity moated = still(helper, EntityType.HUSK, island.above());
        BroodSpiderEntity spider = spider(helper, new BlockPos(8, 2, 14), new BlockPos(2, 2, 2), new TargetFilter());
        spider.setTarget(moated);
        helper.onEachTick(() -> helper.assertFalse(spider.isInLava(), "the spider walked into the lava after the moated husk"));
        helper.runAtTickTime(240, () -> {
            for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) helper.setBlock(island.offset(x, 0, z), Blocks.AIR);
            spider.discard();
            helper.succeed();
        });
    }

    /**
     * Review I2 + M6: a husk on a 10-high tower (on a platform in the open: the yard is only 8 high), the spider 12 blocks
     * off: it finds it (prey far above too), walks over, climbs all the way without dropping it mid-wall (no ground path
     * while it climbs) and bites it on top.
     */
    @GameTest(template = YARD, timeoutTicks = 400, batch = "spidersClimbATallTowerWithoutGivingUp")
    public static void spidersClimbATallTowerWithoutGivingUp(GameTestHelper helper) {
        for (int x = 0; x < 30; x++) {
            for (int z = 0; z < 30; z++) {
                helper.setBlock(sky(helper, x, 0, z), Blocks.STONE);
                for (int y = 1; y < 16; y++) helper.setBlock(sky(helper, x, y, z), Blocks.AIR);
            }
        }
        BlockPos tower = sky(helper, 20, 1, 14);
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) for (int y = 0; y < 10; y++) helper.setBlock(tower.offset(x, y, z), Blocks.STONE);
        LivingEntity husk = still(helper, EntityType.HUSK, tower.above(10));
        BroodSpiderEntity spider = spider(helper, sky(helper, 8, 1, 14), new BlockPos(2, 2, 2), new TargetFilter());
        boolean[] hunted = {false};
        helper.onEachTick(() -> {
            if (spider.getTarget() == husk) hunted[0] = true;
            else if (hunted[0] && lost(husk) == 0) helper.fail("the spider dropped the husk before biting it (at "
                    + spider.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO)) + ")");
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(lost(husk) > 0, "the husk on the tower was never bitten (target " + spider.getTarget() + ", hunted " + hunted[0] + ")");
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) for (int y = 0; y < 10; y++) helper.setBlock(tower.offset(x, y, z), Blocks.AIR);
            spider.discard();
            husk.discard();
        });
    }

    /**
     * Review I3: prey at the spider's own level across a 1-wide lava trench is not "a climb away": the spider (no leap)
     * never walks into the trench after it.
     */
    @GameTest(template = YARD, timeoutTicks = 260, batch = "spidersSkipPreyAcrossATrench")
    public static void spidersSkipPreyAcrossATrench(GameTestHelper helper) {
        int x = 12, depth = 29; // wall to wall across the 30-wide yard (z 1..28), so there is no way round
        BlockState[] floor = new BlockState[depth];
        for (int z = 1; z < depth; z++) {
            floor[z] = helper.getBlockState(new BlockPos(x, 1, z));
            helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            helper.setBlock(new BlockPos(x, 1, z), Blocks.LAVA);
        }
        LivingEntity husk = still(helper, EntityType.HUSK, new BlockPos(x + 1, 2, 14));
        SpiderStats noLeap = new SpiderStats(16, 0.27f, 8, 20, 16, 15, 4, 200, 0, 8, 60);
        BroodSpiderEntity spider = BroodSpiderEntity.spawn(helper.getLevel(), helper.absoluteVec(Vec3.atBottomCenterOf(new BlockPos(6, 2, 14))),
                helper.absolutePos(new BlockPos(2, 2, 2)), new TargetFilter(), null, noLeap);
        helper.onEachTick(() -> helper.assertFalse(spider.isInLava(), "the spider walked into the trench after the husk across it"));
        helper.runAtTickTime(240, () -> {
            for (int z = 1; z < depth; z++) helper.setBlock(new BlockPos(x, 1, z), floor[z]);
            spider.discard();
            husk.discard();
            helper.succeed();
        });
    }

    /** A husk 4.8 blocks off along x from a fresh spider at (10, 2, 14), out of the burst from where it stands. */
    private static LivingEntity huskOffBy(GameTestHelper helper, double dx) {
        LivingEntity husk = still(helper, EntityType.HUSK, new BlockPos(10, 2, 14));
        husk.moveTo(husk.getX() + dx, husk.getY(), husk.getZ());
        return husk;
    }

    /**
     * Review M1: a spider killed halfway up a wall (pressed against it, climbing) leaps its full length at a husk 4 blocks
     * behind it and bursts there, not a hop off the wall.
     */
    @GameTest(template = YARD, timeoutTicks = 300, batch = "spidersLeapOffAWallAtFullLength")
    public static void spidersLeapOffAWallAtFullLength(GameTestHelper helper) {
        BlockPos pillar = new BlockPos(14, 2, 14);
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) for (int y = 0; y < 4; y++) helper.setBlock(pillar.offset(x, y, z), Blocks.STONE);
        LivingEntity onTop = still(helper, EntityType.HUSK, pillar.above(4));
        BroodSpiderEntity spider = spider(helper, new BlockPos(7, 2, 14), new BlockPos(2, 2, 2), new TargetFilter());
        double floor = helper.absoluteVec(Vec3.ZERO).y + 2;
        LivingEntity[] behind = {null};
        Vec3[] last = {null};
        helper.onEachTick(() -> {
            if (behind[0] == null && spider.onClimbable() && spider.getY() >= floor + 1) {
                onTop.discard();
                behind[0] = still(helper, EntityType.HUSK, new BlockPos(7, 2, 14));
                behind[0].moveTo(spider.getX() - 4, floor, spider.getZ());
                spider.hurt(helper.getLevel().damageSources().generic(), 100);
            }
            if (!spider.isRemoved()) last[0] = spider.position();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(behind[0] != null && spider.isRemoved(), "the spider was never killed up the wall, or never burst");
            double off = last[0].subtract(behind[0].position()).horizontalDistance();
            helper.assertTrue(off <= 1.5, "it burst " + off + " blocks from the husk behind it (a hop off the wall)");
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) for (int y = 0; y < 4; y++) helper.setBlock(pillar.offset(x, y, z), Blocks.AIR);
        });
    }

    /** Review M3: knockback that lands after the killing blow (a sprint hit, Punch, a blast's push) does not bend the dying leap. */
    @GameTest(template = YARD, timeoutTicks = 100, batch = "knockbackDoesNotBendTheDyingLeap")
    public static void knockbackDoesNotBendTheDyingLeap(GameTestHelper helper) {
        BroodSpiderEntity spider = spider(helper, new BlockPos(10, 2, 14), new BlockPos(2, 2, 2), new TargetFilter());
        LivingEntity husk = huskOffBy(helper, 4.8);
        spider.hurt(helper.getLevel().damageSources().generic(), 100);
        spider.knockback(1.0, 1, 0); // after hurt() returns, as Player.attack does: away from the husk
        helper.runAtTickTime(60, () -> {
            helper.assertTrue(spider.isRemoved(), "the dying spider is still there");
            helper.assertTrue(lost(husk) > 14, "the husk 4.8 blocks off lost " + lost(husk) + " (the knockback bent the leap)");
            helper.succeed();
        });
    }

    /** Review M4: prey under 2 blocks off (the spec's leap is for 2 to 5) it walks up to and bites; no leap. */
    @GameTest(template = YARD, timeoutTicks = 60, batch = "spidersWalkUpInsideTwoBlocks")
    public static void spidersWalkUpInsideTwoBlocks(GameTestHelper helper) {
        BroodSpiderEntity spider = spider(helper, new BlockPos(10, 2, 14), new BlockPos(2, 2, 2), new TargetFilter());
        LivingEntity husk = huskOffBy(helper, 1.8);
        spider.setTarget(husk);
        helper.onEachTick(() -> helper.assertFalse(spider.leaping(), "a leap at prey " + spider.distanceTo(husk) + " blocks off (under 2)"));
        helper.succeedWhen(() -> {
            helper.assertTrue(lost(husk) > 0, "the husk was never bitten");
            spider.discard();
        });
    }

    /**
     * Review M4: a bite knocks the husk out of reach and the spider leaps after it: the leap brings no extra bite (still 8
     * every bite_interval).
     */
    @GameTest(template = YARD, timeoutTicks = 100, batch = "aLeapAddsNoBite")
    public static void aLeapAddsNoBite(GameTestHelper helper) {
        BroodSpiderEntity spider = spider(helper, new BlockPos(10, 2, 14), new BlockPos(2, 2, 2), new TargetFilter());
        LivingEntity husk = huskOffBy(helper, 1.2);
        spider.setTarget(husk);
        List<Long> bites = new ArrayList<>();
        float[] seen = {0};
        helper.onEachTick(() -> {
            if (lost(husk) <= seen[0]) return;
            seen[0] = lost(husk);
            bites.add(helper.getTick());
            if (bites.size() == 1) { // knocked 3 blocks further, ready to be hurt again at once
                husk.moveTo(husk.getX() + 3, husk.getY(), husk.getZ());
                husk.invulnerableTime = 0;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(bites.size() >= 2, "bites at " + bites);
            long gap = bites.get(1) - bites.get(0);
            helper.assertTrue(gap >= STATS.biteInterval() - 2, "the second bite came " + gap + " ticks after the first (one every " + STATS.biteInterval() + ")");
            spider.discard();
        });
    }

    /**
     * Review M5: a leap off soul sand (it slows what stands on it) still lands where the husk stood at take-off, not far
     * short (a bite on the way knocks the husk itself back).
     */
    @GameTest(template = YARD, timeoutTicks = 100, batch = "leapsOffSoulSandLandOnThePrey")
    public static void leapsOffSoulSandLandOnThePrey(GameTestHelper helper) {
        BlockState stone = helper.getBlockState(new BlockPos(8, 1, 14));
        for (int x = 6; x <= 10; x++) for (int z = 12; z <= 16; z++) helper.setBlock(new BlockPos(x, 1, z), Blocks.SOUL_SAND);
        LivingEntity husk = still(helper, EntityType.HUSK, new BlockPos(12, 2, 14));
        BroodSpiderEntity spider = spider(helper, new BlockPos(8, 2, 14), new BlockPos(2, 2, 2), new TargetFilter());
        spider.setTarget(husk);
        Vec3[] aim = {null};
        helper.onEachTick(() -> {
            if (aim[0] == null && spider.leaping()) aim[0] = husk.position();
            if (aim[0] == null || spider.leaping()) return;
            double off = spider.position().subtract(aim[0]).horizontalDistance();
            helper.assertTrue(off <= 1.2, "the leap landed " + off + " blocks from where the husk stood (husk now "
                    + husk.position().subtract(aim[0]).horizontalDistance() + " off it, lost " + lost(husk) + ")");
            for (int x = 6; x <= 10; x++) for (int z = 12; z <= 16; z++) helper.setBlock(new BlockPos(x, 1, z), stone);
            spider.discard();
            helper.succeed();
        });
    }

    /** Review M5: a leap at a husk below a ledge leaves the ground at once (it used to push into the ledge and slide off it). */
    @GameTest(template = YARD, timeoutTicks = 60, batch = "leapsDownALedgeLeaveTheGround")
    public static void leapsDownALedgeLeaveTheGround(GameTestHelper helper) {
        for (int x = 6; x <= 9; x++) for (int z = 12; z <= 16; z++) for (int y = 2; y < 5; y++) helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
        LivingEntity husk = still(helper, EntityType.HUSK, new BlockPos(12, 2, 14));
        BroodSpiderEntity spider = spider(helper, new BlockPos(9, 5, 14), new BlockPos(2, 2, 2), new TargetFilter());
        spider.moveTo(spider.getX() - 0.2, spider.getY(), spider.getZ()); // 0.7 from the edge: it sees the husk over it
        spider.setTarget(husk);
        helper.onEachTick(() -> {
            if (!spider.leaping()) return;
            helper.assertFalse(spider.onGround(), "the leap down pushed into the ledge (on the ground after take-off)");
            for (int x = 6; x <= 9; x++) for (int z = 12; z <= 16; z++) for (int y = 2; y < 5; y++) helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
            spider.discard();
            helper.succeed();
        });
    }

    /** Review M7: leaps come every leap_cooldown ticks at most: a husk that keeps stepping 4 blocks away draws one, then one 3 s later. */
    @GameTest(template = YARD, timeoutTicks = 200, batch = "spidersLeapOnlyEveryLeapCooldown")
    public static void spidersLeapOnlyEveryLeapCooldown(GameTestHelper helper) {
        BroodSpiderEntity spider = spider(helper, new BlockPos(10, 2, 14), new BlockPos(2, 2, 2), new TargetFilter());
        LivingEntity husk = huskOffBy(helper, 4);
        spider.setTarget(husk);
        double middle = helper.absoluteVec(new Vec3(15, 0, 0)).x;
        List<Long> leaps = new ArrayList<>();
        boolean[] was = {false};
        helper.onEachTick(() -> {
            if (spider.leaping() && !was[0]) leaps.add(helper.getTick());
            was[0] = spider.leaping();
            if (spider.distanceTo(husk) < 3) husk.moveTo(spider.getX() + (spider.getX() > middle ? -4 : 4), husk.getY(), husk.getZ());
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(leaps.size() >= 2, "leaps at " + leaps);
            helper.assertTrue(leaps.get(1) - leaps.get(0) >= STATS.leapCooldown() - 2, "leaps at " + leaps + " (one every " + STATS.leapCooldown() + ")");
            spider.discard();
        });
    }

    /** Review M7: a dying spider between two husks leaps at the nearer (3.5 blocks) and bursts there; the one 4.8 the other way is spared. */
    @GameTest(template = YARD, timeoutTicks = 100, batch = "dyingSpiderPicksTheNearestPrey")
    public static void dyingSpiderPicksTheNearestPrey(GameTestHelper helper) {
        BroodSpiderEntity spider = spider(helper, new BlockPos(10, 2, 14), new BlockPos(2, 2, 2), new TargetFilter());
        LivingEntity near = huskOffBy(helper, -3.5), far = huskOffBy(helper, 4.8);
        spider.hurt(helper.getLevel().damageSources().generic(), 100);
        helper.runAtTickTime(60, () -> {
            helper.assertTrue(spider.isRemoved(), "the dying spider is still there");
            helper.assertTrue(lost(near) > 14 && lost(far) == 0, "near lost " + lost(near) + ", far " + lost(far));
            helper.succeed();
        });
    }

    /** Review M7: an idle self-destruct bursts where the spider stands, even with prey in leap range (a flyer it cannot hunt). */
    @GameTest(template = YARD, timeoutTicks = 100, batch = "idleSelfDestructNeverLeaps")
    public static void idleSelfDestructNeverLeaps(GameTestHelper helper) {
        SpiderStats quick = new SpiderStats(16, 0.27f, 8, 20, 16, 15, 4, 20, 5, 8, 60);
        BroodSpiderEntity spider = BroodSpiderEntity.spawn(helper.getLevel(), helper.absoluteVec(Vec3.atBottomCenterOf(new BlockPos(10, 2, 14))),
                helper.absolutePos(new BlockPos(2, 2, 2)), new TargetFilter(), null, quick);
        LivingEntity flyer = still(helper, EntityType.HUSK, new BlockPos(13, 4, 14));
        flyer.setNoGravity(true);
        Vec3[] died = {null};
        double[] moved = {0};
        helper.onEachTick(() -> {
            if (died[0] == null && !spider.isAlive()) died[0] = spider.position();
            if (died[0] != null) moved[0] = Math.max(moved[0], spider.position().distanceTo(died[0]));
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(spider.isRemoved(), "the idle spider never burst");
            helper.assertTrue(moved[0] < 1, "it moved " + moved[0] + " blocks after it died (a leap at the flyer)");
            flyer.discard();
        });
    }

    /** Review M8: a death a mod cancels (and heals) is no death: no leap, no burst. */
    @GameTest(template = YARD, timeoutTicks = 40, batch = "cancelledDeathNeverLeaps")
    public static void cancelledDeathNeverLeaps(GameTestHelper helper) {
        BroodSpiderEntity spider = spider(helper, new BlockPos(10, 2, 14), new BlockPos(2, 2, 2), new TargetFilter());
        huskOffBy(helper, 4.8);
        Consumer<LivingDeathEvent> save = event -> {
            if (event.getEntity() != spider) return;
            event.setCanceled(true);
            spider.setHealth(spider.getMaxHealth());
        };
        MinecraftForge.EVENT_BUS.addListener(save);
        try {
            spider.hurt(helper.getLevel().damageSources().generic(), 100);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(save);
        }
        helper.assertTrue(spider.isAlive(), "the saved spider died");
        helper.assertFalse(spider.leaping(), "a death a mod cancelled still started the dying leap");
        helper.runAtTickTime(12, () -> {
            helper.assertTrue(!spider.isRemoved() && spider.deathTime == 0, "the saved spider burst");
            spider.discard();
            helper.succeed();
        });
    }

    /**
     * Review M9: a leap that ends in deep water ends there: the spider swims in its walk, not frozen in the leap pose. The
     * husk's ground turns to a 3-deep pool as the spider takes off.
     */
    @GameTest(template = YARD, timeoutTicks = 100, batch = "aLeapIntoWaterEndsThere")
    public static void aLeapIntoWaterEndsThere(GameTestHelper helper) {
        BroodSpiderEntity spider = spider(helper, new BlockPos(10, 2, 14), new BlockPos(2, 2, 2), new TargetFilter());
        LivingEntity husk = huskOffBy(helper, 4);
        spider.setTarget(husk);
        Map<BlockPos, BlockState> pool = new HashMap<>();
        int[] swimming = {0};
        helper.onEachTick(() -> {
            if (pool.isEmpty() && spider.leaping()) {
                for (int x = 12; x <= 17; x++) {
                    for (int z = 12; z <= 16; z++) {
                        for (int y = -1; y <= 1; y++) {
                            BlockPos at = new BlockPos(x, y, z);
                            pool.put(at, helper.getBlockState(at));
                            helper.setBlock(at, Blocks.WATER);
                        }
                    }
                }
            }
            swimming[0] = spider.isInWater() && spider.leaping() ? swimming[0] + 1 : 0;
            helper.assertTrue(swimming[0] < 4, "still in the leap pose " + swimming[0] + " ticks in the water");
        });
        helper.runAtTickTime(60, () -> {
            helper.assertFalse(pool.isEmpty(), "the spider never leapt");
            pool.forEach(helper::setBlock);
            spider.discard();
            husk.discard();
            helper.succeed();
        });
    }

    /** Review M9 (guard): a leap that hits a wall ends there: the spider climbs it in its walk, not frozen in the leap pose. */
    @GameTest(template = YARD, timeoutTicks = 100, batch = "aLeapIntoAWallEndsThere")
    public static void aLeapIntoAWallEndsThere(GameTestHelper helper) {
        BroodSpiderEntity spider = spider(helper, new BlockPos(10, 2, 14), new BlockPos(2, 2, 2), new TargetFilter());
        LivingEntity husk = huskOffBy(helper, 4);
        spider.setTarget(husk);
        int[] wall = {-1}, climbing = {0};
        helper.onEachTick(() -> {
            if (wall[0] < 0 && spider.leaping()) { // just took off: a wall right ahead, across the yard
                wall[0] = (int) Math.floor(spider.getX() - helper.absoluteVec(Vec3.ZERO).x) + 2;
                for (int z = 1; z < 29; z++) for (int y = 2; y < 6; y++) helper.setBlock(new BlockPos(wall[0], y, z), Blocks.STONE);
            }
            climbing[0] = spider.onClimbable() && spider.leaping() ? climbing[0] + 1 : 0;
            helper.assertTrue(climbing[0] < 3, "still in the leap pose " + climbing[0] + " ticks up the wall");
        });
        helper.runAtTickTime(60, () -> {
            helper.assertTrue(wall[0] >= 0, "the spider never leapt");
            for (int z = 1; z < 29; z++) for (int y = 2; y < 6; y++) helper.setBlock(new BlockPos(wall[0], y, z), Blocks.AIR);
            spider.discard();
            helper.succeed();
        });
    }
}
