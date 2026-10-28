package dev.bastion.gametest;

import dev.bastion.Bastion;
import dev.bastion.config.BastionConfig;
import dev.bastion.config.TurretStatsConfig;
import dev.bastion.registry.BastionBlocks;
import dev.bastion.registry.BastionItems;
import dev.bastion.turret.FortressTurretBaseBlock;
import dev.bastion.turret.ManualControl;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.TurretInventory;
import dev.bastion.turret.TurretTier;
import dev.bastion.turret.targeting.TargetFilter;
import dev.bastion.unit.DroneStats;
import dev.bastion.unit.HiveDroneEntity;
import dev.bastion.unit.TurretUnits;
import dev.bastion.weapon.StatSheet;
import dev.bastion.weapon.WeaponData;
import dev.bastion.weapon.WeaponDataLoader;
import dev.bastion.weapon.WeaponStatRows;
import dev.bastion.weapon.types.HiveCarrierWeapon;
import dev.bastion.workstation.WorkstationRecipe;
import dev.bastion.workstation.WorkstationType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Hive Carrier (PLAN "Turret 3x3"): its drones shoot only the carrier's valid targets (flyers too), dive kamikaze when out
 * of bullets or badly hurt (sparing non-targets), keep out of blocks, are hunted by hostile mobs and are never saved.
 * Flying tests build a platform under open sky at Y_SKY (GameTest runs underground, as JavelinTests).
 */
@GameTestHolder(Bastion.MOD_ID)
@PrefixGameTestTemplate(false)
public class HiveTests {
    private static final String ARENA = "arena", YARD = "yard";
    private static final int Y_SKY = 230, SIZE = 34;
    /**
     * The shipped numbers (hive_carrier.json): HP 20, bullet 3.5 at 10 a second (35 damage a second, user 2026-10-27: was 2
     * at 4), 300 bullets (30 s of fire, was 120), reach 32, patrol 64 (user 2026-10-24), kamikaze below 25 %: 15 within 4.5.
     */
    static final DroneStats STATS = new DroneStats(20, 3.5f, 300, 2, 32, 64, 0.25f, 15, 4.5f, 0.45f, 100);
    /**
     * STATS circling closer (patrol radius 8, not 64) round a home 6-8 blocks from the husks: with no carrier a drone
     * that has not seen its husk within idle_ticks hovers where it is, and on the wide circle that could be out of reach.
     */
    static final DroneStats NEAR = new DroneStats(20, 3.5f, 300, 2, 32, 8, 0.25f, 15, 4.5f, 0.45f, 100);

    private static BlockPos sky(GameTestHelper helper, int x, int y, int z) {
        return new BlockPos(x, Y_SKY - helper.absolutePos(BlockPos.ZERO).getY() + y, z);
    }

    /** Clears and lays the open-sky platform (and drops whatever an earlier test left up there). */
    private static void platform(GameTestHelper helper) {
        Vec3 centre = helper.absoluteVec(Vec3.atCenterOf(sky(helper, 17, 1, 17)));
        helper.getLevel().getEntitiesOfClass(Mob.class, new AABB(centre, centre).inflate(60)).forEach(Entity::discard);
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                for (int y = 1; y < 12; y++) helper.setBlock(sky(helper, x, y, z), Blocks.AIR);
                helper.setBlock(sky(helper, x, 0, z), Blocks.STONE);
            }
        }
    }

    private static void clear(GameTestHelper helper) {
        Vec3 centre = helper.absoluteVec(Vec3.atCenterOf(sky(helper, 17, 1, 17)));
        helper.getLevel().getEntitiesOfClass(Mob.class, new AABB(centre, centre).inflate(60)).forEach(Entity::discard);
    }

    static HiveDroneEntity drone(GameTestHelper helper, BlockPos at, BlockPos home, TargetFilter filter, int ammo) {
        return HiveDroneEntity.spawn(helper.getLevel(), helper.absoluteVec(Vec3.atBottomCenterOf(at)), helper.absolutePos(home), filter, null, NEAR, ammo);
    }

    static LivingEntity still(GameTestHelper helper, EntityType<? extends LivingEntity> type, BlockPos at) {
        return BroodTests.still(helper, type, at);
    }

    static float lost(LivingEntity mob) {
        return mob.getMaxHealth() - mob.getHealth();
    }

    private static TargetFilter none() {
        TargetFilter none = new TargetFilter();
        for (TargetFilter.Category category : TargetFilter.Category.values()) none.setCategory(category, false);
        return none;
    }

    /** A husk and a cow side by side: the husk is shot, the cow never; bullets are spent. */
    @GameTest(template = ARENA, timeoutTicks = 300, batch = "dronesShootOnlyValidTargets")
    public static void dronesShootOnlyValidTargets(GameTestHelper helper) {
        platform(helper);
        HiveDroneEntity drone = drone(helper, sky(helper, 10, 4, 17), sky(helper, 12, 1, 17), new TargetFilter(), 60);
        LivingEntity husk = still(helper, EntityType.HUSK, sky(helper, 18, 1, 17));
        LivingEntity cow = still(helper, EntityType.COW, sky(helper, 18, 1, 18));
        helper.succeedWhen(() -> {
            helper.assertTrue(lost(husk) > 0, "the husk was never shot (state " + drone.state() + ", target " + drone.getTarget() + ")");
            helper.assertTrue(lost(cow) == 0, "the cow was hit: " + lost(cow));
            helper.assertTrue(drone.ammo() < 60, "no bullet spent");
            clear(helper);
        });
    }

    /** A phantom in reach is shot (spiders could never catch flyers; drones can). */
    @GameTest(template = ARENA, timeoutTicks = 300, batch = "dronesShootFlyers")
    public static void dronesShootFlyers(GameTestHelper helper) {
        platform(helper);
        HiveDroneEntity drone = drone(helper, sky(helper, 10, 4, 17), sky(helper, 12, 1, 17), new TargetFilter(), 60);
        LivingEntity phantom = still(helper, EntityType.PHANTOM, sky(helper, 18, 6, 17));
        helper.succeedWhen(() -> {
            helper.assertTrue(lost(phantom) > 0, "the phantom was never shot (state " + drone.state() + ")");
            clear(helper);
        });
    }

    /** The numbers these tests use are the shipped ones (the jar's hive_carrier.json, not the config file). */
    @GameTest(template = YARD, batch = "droneTestStatsAreTheShippedOnes")
    public static void droneTestStatsAreTheShippedOnes(GameTestHelper helper) {
        DroneStats shipped = DroneStats.of(StatSheet.of(TurretStatsConfig.builtIn("hive_carrier"), TurretTier.T1));
        helper.assertTrue(shipped.equals(STATS), "hive_carrier.json gives " + shipped + ", the tests use " + STATS);
        helper.succeed();
    }

    /** No bullets left and a husk in reach: the drone dives into it and bursts (~15), then is gone. */
    @GameTest(template = ARENA, timeoutTicks = 300, batch = "kamikazeWhenOutOfAmmo")
    public static void kamikazeWhenOutOfAmmo(GameTestHelper helper) {
        platform(helper);
        HiveDroneEntity drone = drone(helper, sky(helper, 8, 4, 17), sky(helper, 12, 1, 17), new TargetFilter(), 0);
        LivingEntity husk = still(helper, EntityType.HUSK, sky(helper, 20, 1, 17));
        helper.succeedWhen(() -> {
            helper.assertTrue(drone.isRemoved(), "the drone is still flying (state " + drone.state() + ")");
            helper.assertTrue(lost(husk) > 14 && lost(husk) <= 15, "the husk lost " + lost(husk));
            clear(helper);
        });
    }

    /** Down to 4 of 20 HP (below a quarter): it dives even with bullets left. */
    @GameTest(template = ARENA, timeoutTicks = 300, batch = "kamikazeWhenBadlyHurt")
    public static void kamikazeWhenBadlyHurt(GameTestHelper helper) {
        platform(helper);
        HiveDroneEntity drone = drone(helper, sky(helper, 8, 4, 17), sky(helper, 12, 1, 17), new TargetFilter(), 60);
        drone.setHealth(4);
        LivingEntity husk = still(helper, EntityType.HUSK, sky(helper, 20, 1, 17));
        helper.succeedWhen(() -> {
            helper.assertTrue(drone.isRemoved(), "the drone is still flying (state " + drone.state() + ")");
            helper.assertTrue(lost(husk) >= 14, "the husk lost only " + lost(husk));
            clear(helper);
        });
    }

    /** Shot down with a target in reach it does not just drop: it dives into it and bursts (user 2026-10-27). */
    @GameTest(template = ARENA, timeoutTicks = 300, batch = "shotDownDroneDivesIntoItsTarget")
    public static void shotDownDroneDivesIntoItsTarget(GameTestHelper helper) {
        platform(helper);
        HiveDroneEntity drone = drone(helper, sky(helper, 8, 4, 17), sky(helper, 12, 1, 17), new TargetFilter(), 60);
        LivingEntity husk = still(helper, EntityType.HUSK, sky(helper, 20, 1, 17));
        boolean[] shot = {false};
        helper.onEachTick(() -> { // killed the moment it engages: a bullet or two at most before, the burst is 15
            if (!shot[0] && drone.state() == HiveDroneEntity.State.ENGAGE) {
                shot[0] = true;
                drone.hurt(helper.getLevel().damageSources().generic(), 1000);
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(shot[0], "the drone never engaged");
            helper.assertTrue(drone.isRemoved(), "the drone is still there (state " + drone.state() + ")");
            helper.assertTrue(lost(husk) >= 14, "the husk lost only " + lost(husk) + ": the drone fell instead of diving");
            clear(helper);
        });
    }

    /** A cow 1.5 blocks from the husk the drone dives into loses nothing. */
    @GameTest(template = ARENA, timeoutTicks = 300, batch = "kamikazeSparesNonTargets")
    public static void kamikazeSparesNonTargets(GameTestHelper helper) {
        platform(helper);
        HiveDroneEntity drone = drone(helper, sky(helper, 8, 4, 17), sky(helper, 12, 1, 17), new TargetFilter(), 0);
        LivingEntity husk = still(helper, EntityType.HUSK, sky(helper, 20, 1, 17));
        LivingEntity cow = still(helper, EntityType.COW, sky(helper, 20, 1, 19));
        helper.succeedWhen(() -> {
            helper.assertTrue(drone.isRemoved() && lost(husk) > 14, "no burst yet (state " + drone.state() + ")");
            helper.assertTrue(lost(cow) == 0, "the cow lost " + lost(cow));
            clear(helper);
        });
    }

    /** No carrier at home and two bullets left: it fires them, then dives. */
    @GameTest(template = ARENA, timeoutTicks = 400, batch = "homelessDroneDivesWhenEmpty")
    public static void homelessDroneDivesWhenEmpty(GameTestHelper helper) {
        platform(helper);
        HiveDroneEntity drone = drone(helper, sky(helper, 8, 4, 17), sky(helper, 12, 1, 17), new TargetFilter(), 2);
        LivingEntity husk = still(helper, EntityType.HUSK, sky(helper, 20, 1, 17));
        helper.succeedWhen(() -> {
            helper.assertTrue(drone.isRemoved(), "still flying with " + drone.ammo() + " bullets (state " + drone.state() + ")");
            helper.assertTrue(lost(husk) > 17, "the husk lost only " + lost(husk) + " (2 bullets and a burst)");
            clear(helper);
        });
    }

    /** A 6-high wall between the drone and a husk: for 200 ticks its box never overlaps a block. */
    @GameTest(template = ARENA, timeoutTicks = 260, batch = "dronesStayOutOfBlocks")
    public static void dronesStayOutOfBlocks(GameTestHelper helper) {
        platform(helper);
        for (int z = 10; z <= 24; z++) for (int y = 1; y <= 6; y++) helper.setBlock(sky(helper, 14, y, z), Blocks.STONE);
        HiveDroneEntity drone = drone(helper, sky(helper, 8, 2, 17), sky(helper, 12, 1, 17), new TargetFilter(), 60);
        still(helper, EntityType.HUSK, sky(helper, 20, 1, 17));
        helper.onEachTick(() -> helper.assertTrue(drone.isRemoved() || !helper.getLevel().getBlockCollisions(drone, drone.getBoundingBox().deflate(0.02)).iterator().hasNext(),
                "the drone is inside a block at " + drone.position()));
        helper.runAtTickTime(200, () -> {
            clear(helper);
            helper.succeed();
        });
    }

    /** Never written to the chunk (nor as a passenger), no portals, counted while it flies and not after. */
    @GameTest(template = YARD, timeoutTicks = 40, batch = "dronesAreNeverSaved")
    public static void dronesAreNeverSaved(GameTestHelper helper) {
        BlockPos home = new BlockPos(2, 2, 2);
        HiveDroneEntity drone = drone(helper, new BlockPos(14, 3, 14), home, none(), 60);
        helper.runAtTickTime(5, () -> {
            helper.assertFalse(drone.shouldBeSaved() || drone.saveAsPassenger(new CompoundTag()), "a drone would be saved");
            helper.assertFalse(drone.canChangeDimensions(), "a drone could go through a portal");
            helper.assertTrue(TurretUnits.count(helper.getLevel(), helper.absolutePos(home)) == 1, "counted " + TurretUnits.count(helper.getLevel(), helper.absolutePos(home)));
            drone.discard();
        });
        helper.runAtTickTime(7, () -> {
            helper.assertTrue(TurretUnits.count(helper.getLevel(), helper.absolutePos(home)) == 0, "a discarded drone still counts");
            helper.succeed();
        });
    }

    /** A zombie with no player near goes for a drone. */
    @GameTest(template = YARD, timeoutTicks = 200, batch = "hostileMobsTargetDrones")
    public static void hostileMobsTargetDrones(GameTestHelper helper) {
        HiveDroneEntity drone = drone(helper, new BlockPos(10, 3, 14), new BlockPos(10, 2, 14), none(), 60);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(18, 2, 14));
        helper.succeedWhen(() -> helper.assertTrue(zombie.getTarget() == drone, "the zombie targets " + zombie.getTarget()));
    }

    /** Its owner hurts it only as trustedCanDamage says; a stranger always can. */
    @GameTest(template = YARD, timeoutTicks = 40, batch = "trustedPlayersCannotHurtDrones")
    public static void trustedPlayersCannotHurtDrones(GameTestHelper helper) {
        Player owner = helper.makeMockPlayer(), stranger = helper.makeMockPlayer();
        HiveDroneEntity drone = HiveDroneEntity.spawn(helper.getLevel(), helper.absoluteVec(new Vec3(12.5, 3, 14.5)), helper.absolutePos(new BlockPos(2, 2, 2)),
                none(), owner.getUUID(), STATS, 60);
        helper.runAtTickTime(2, () -> {
            boolean allowed = BastionConfig.TRUSTED_CAN_DAMAGE.get();
            helper.assertTrue(drone.hurt(helper.getLevel().damageSources().playerAttack(owner), 3) == allowed,
                    "its owner " + (allowed ? "could not hurt it" : "hurt it") + " with trustedCanDamage " + allowed);
        });
        helper.runAtTickTime(15, () -> {
            helper.assertTrue(drone.hurt(helper.getLevel().damageSources().playerAttack(stranger), 3), "a stranger could not hurt it");
            drone.discard();
            helper.succeed();
        });
    }

    // --- the carrier: waves, docking, the cap (Task 2) --------------------------------------------------------

    private static final int CAPACITY = 12000, PER_DRONE = 2000, RELOAD = 1000;

    private static BlockPos core(GameTestHelper helper) {
        return sky(helper, 8, 1, 17);
    }

    private static TurretBaseBlockEntity carrier(GameTestHelper helper, int drones, boolean charged) {
        platform(helper);
        FortressTurretBaseBlock.placeAt(helper.getLevel(), helper.absolutePos(core(helper)), BastionBlocks.FORTRESS_TURRET_BASE.get());
        TurretBaseBlockEntity turret = (TurretBaseBlockEntity) helper.getBlockEntity(core(helper));
        turret.inventory().setStackInSlot(TurretInventory.WEAPON, new ItemStack(BastionItems.HIVE_CARRIER_TURRET.get()));
        if (drones > 0) turret.inventory().setStackInSlot(TurretInventory.AMMO_START, new ItemStack(BastionItems.HIVE_DRONE.get(), drones));
        if (charged) charge(helper);
        return turret;
    }

    private static void charge(GameTestHelper helper) {
        IEnergyStorage energy = helper.getBlockEntity(core(helper)).getCapability(ForgeCapabilities.ENERGY, Direction.UP).orElseThrow(IllegalStateException::new);
        while (energy.receiveEnergy(Integer.MAX_VALUE, false) > 0) ;
    }

    private static int items(TurretBaseBlockEntity turret) {
        return turret.inventory().getStackInSlot(TurretInventory.AMMO_START).getCount();
    }

    /**
     * Fails the test the moment one of the turret's drones is inside the hive's outline (within its wall, under its top)
     * above the bays' height: drones leave and come home only through the side holes, never the roof.
     */
    private static void throughTheHolesOnly(GameTestHelper helper, TurretBaseBlockEntity turret) {
        Vec3 bay = HiveCarrierWeapon.bay(turret, 0), centre = turret.alongMount(TurretBaseBlockEntity.BASE_HEIGHT);
        helper.onEachTick(() -> {
            for (HiveDroneEntity drone : dronesOf(helper, turret)) {
                double side = Math.hypot(drone.getX() - centre.x, drone.getZ() - centre.z), up = drone.getY() - bay.y;
                helper.assertFalse(side < 1.2 && up > 0.3 && up < 2.0, "a drone passed through the hive's roof at " + drone.position().subtract(centre)
                        + " (" + drone.state() + ")");
            }
        });
    }

    private static List<HiveDroneEntity> dronesOf(GameTestHelper helper, TurretBaseBlockEntity turret) {
        Vec3 centre = Vec3.atCenterOf(turret.getBlockPos());
        return helper.getLevel().getEntitiesOfClass(HiveDroneEntity.class, new AABB(centre, centre).inflate(60), d -> d.home().equals(turret.getBlockPos()));
    }

    private static void done(GameTestHelper helper, TurretBaseBlockEntity turret) {
        turret.inventory().setStackInSlot(TurretInventory.WEAPON, ItemStack.EMPTY);
        clear(helper);
        helper.getLevel().destroyBlock(turret.getBlockPos(), false);
    }

    /** A husk in reach: three drones leave, three Hive Drones and 3 x energy_per_drone FE are spent. */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "carrierLaunchesAWave")
    public static void carrierLaunchesAWave(GameTestHelper helper) {
        TurretBaseBlockEntity turret = carrier(helper, 4, true);
        still(helper, EntityType.HUSK, core(helper).offset(16, 0, 0));
        helper.succeedWhen(() -> {
            helper.assertTrue(dronesOf(helper, turret).size() == 3, "drones out: " + dronesOf(helper, turret).size());
            helper.assertTrue(items(turret) == 1 && turret.energy() == CAPACITY - 3 * PER_DRONE, items(turret) + " drones, " + turret.energy() + " FE left");
            done(helper, turret);
        });
    }

    /** Hive Drones but no FE: none leaves; FE but no Hive Drone and none docked: none either. */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "carrierNeedsDronesAndEnergy")
    public static void carrierNeedsDronesAndEnergy(GameTestHelper helper) {
        TurretBaseBlockEntity turret = carrier(helper, 2, false);
        still(helper, EntityType.HUSK, core(helper).offset(16, 0, 0));
        helper.runAtTickTime(60, () -> {
            helper.assertTrue(dronesOf(helper, turret).isEmpty() && items(turret) == 2, "launched without FE");
            turret.inventory().setStackInSlot(TurretInventory.AMMO_START, ItemStack.EMPTY);
            charge(helper);
        });
        helper.runAtTickTime(130, () -> {
            helper.assertTrue(dronesOf(helper, turret).isEmpty(), "launched without a Hive Drone");
            done(helper, turret);
            helper.succeed();
        });
    }

    /** A wave leaves sideways through the three holes: each drone gets out past the wall before it climbs. */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "dronesLeaveThroughTheSideHoles")
    public static void dronesLeaveThroughTheSideHoles(GameTestHelper helper) {
        TurretBaseBlockEntity turret = carrier(helper, 3, true);
        still(helper, EntityType.HUSK, core(helper).offset(16, 0, 0));
        throughTheHolesOnly(helper, turret);
        Vec3 centre = turret.alongMount(TurretBaseBlockEntity.BASE_HEIGHT);
        Set<HiveDroneEntity> out = new HashSet<>();
        helper.onEachTick(() -> dronesOf(helper, turret).stream().filter(d -> Math.hypot(d.getX() - centre.x, d.getZ() - centre.z) > 1.8).forEach(out::add));
        helper.succeedWhen(() -> {
            helper.assertTrue(out.size() == 3, out.size() + " drones out past the wall");
            done(helper, turret);
        });
    }

    /** A wall right in front of the north doorway: the wave leaves by the other two, none through the hive's roof. */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "dronesUseAClearDoorway")
    public static void dronesUseAClearDoorway(GameTestHelper helper) {
        TurretBaseBlockEntity turret = carrier(helper, 3, true);
        BlockPos core = core(helper);
        for (int x = -1; x <= 1; x++) for (int y = 1; y <= 4; y++) helper.setBlock(core.offset(x, y, -2), Blocks.STONE);
        still(helper, EntityType.HUSK, core.offset(16, 0, 0));
        throughTheHolesOnly(helper, turret);
        Vec3 centre = turret.alongMount(TurretBaseBlockEntity.BASE_HEIGHT);
        Set<HiveDroneEntity> out = new HashSet<>();
        helper.onEachTick(() -> dronesOf(helper, turret).stream().filter(d -> Math.hypot(d.getX() - centre.x, d.getZ() - centre.z) > 1.8).forEach(out::add));
        helper.succeedWhen(() -> {
            helper.assertTrue(out.size() == 3, out.size() + " drones out past the wall");
            done(helper, turret);
        });
    }

    /**
     * The model has a muzzle locator at each doorway's mouth (the wave's launch puffs play there, not inside the closed
     * hive): muzzle_0..2 within half a pixel of the code's bay + EXIT, geo X mirrored or not (the bays are mirror-symmetric).
     */
    @GameTest(template = YARD, batch = "hiveModelHasAMuzzleAtEachDoorway")
    public static void hiveModelHasAMuzzleAtEachDoorway(GameTestHelper helper) {
        com.google.gson.JsonObject geo;
        try (java.io.InputStream in = HiveTests.class.getResourceAsStream("/assets/bastion/geo/hive_carrier_turret.geo.json")) {
            geo = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in)).getAsJsonObject();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        List<Vec3> muzzles = new java.util.ArrayList<>();
        for (var bone : geo.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones")) {
            var b = bone.getAsJsonObject();
            if (b.get("name").getAsString().startsWith("muzzle_")) {
                var p = b.getAsJsonArray("pivot");
                muzzles.add(new Vec3(p.get(0).getAsDouble(), p.get(1).getAsDouble(), p.get(2).getAsDouble()));
            }
        }
        helper.assertTrue(muzzles.size() == 3, muzzles.size() + " muzzle locators");
        for (Vec3 mouth : HiveCarrierWeapon.doorwayMouthsPx()) {
            helper.assertTrue(muzzles.stream().anyMatch(m -> (Math.abs(m.x - mouth.x) < 0.5 || Math.abs(-m.x - mouth.x) < 0.5)
                    && Math.abs(m.y - mouth.y) < 0.5 && Math.abs(m.z - mouth.z) < 0.5), "no muzzle at the doorway mouth " + mouth + ": " + muzzles);
        }
        helper.succeed();
    }

    /**
     * Blocks put where a drone out of the north doorway would come down on its way home (its approach point): it comes
     * down at the doorway's mouth instead and still flies in and docks on its pad, not from outside nor through the roof.
     */
    @GameTest(template = ARENA, timeoutTicks = 500, batch = "dronesComeHomeByTheMouthWhenTheApproachIsBlocked")
    public static void dronesComeHomeByTheMouthWhenTheApproachIsBlocked(GameTestHelper helper) {
        TurretBaseBlockEntity turret = carrier(helper, 0, true);
        Vec3 bay = HiveCarrierWeapon.bay(turret, 0);
        HiveDroneEntity drone = HiveDroneEntity.spawn(helper.getLevel(), bay, turret.getBlockPos(), none(), null, STATS, 60);
        BlockPos core = core(helper);
        for (int x = -1; x <= 1; x++) for (int y = 1; y <= 5; y++) helper.setBlock(core.offset(x, y, -4), Blocks.STONE);
        throughTheHolesOnly(helper, turret);
        Vec3[] last = new Vec3[1];
        helper.onEachTick(() -> {
            if (!drone.isRemoved()) last[0] = drone.position();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(drone.isRemoved() && turret.weaponState().dockedDrones == 1, "not docked yet (state " + drone.state() + ")");
            helper.assertTrue(last[0].distanceTo(bay) < 1.0, "it docked " + last[0].distanceTo(bay) + " blocks from its bay, at " + last[0].subtract(bay));
            done(helper, turret);
        });
    }

    /** Three drones docked and no Hive Drone items: a wave of those three, for reload_energy each. */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "dockedDronesRelaunchForReloadEnergy")
    public static void dockedDronesRelaunchForReloadEnergy(GameTestHelper helper) {
        TurretBaseBlockEntity turret = carrier(helper, 0, true);
        turret.weaponState().dockedDrones = 3;
        still(helper, EntityType.HUSK, core(helper).offset(16, 0, 0));
        helper.succeedWhen(() -> {
            helper.assertTrue(dronesOf(helper, turret).size() == 3, "drones out: " + dronesOf(helper, turret).size());
            helper.assertTrue(turret.weaponState().dockedDrones == 0 && turret.energy() == CAPACITY - 3 * RELOAD,
                    turret.weaponState().dockedDrones + " docked, " + turret.energy() + " FE");
            done(helper, turret);
        });
    }

    /** Nine drones already out (the cap) and three docked: a wave relaunches none of them; never more than nine fly. */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "dockedDronesWaitForRoomToFly")
    public static void dockedDronesWaitForRoomToFly(GameTestHelper helper) {
        TurretBaseBlockEntity turret = carrier(helper, 0, true);
        turret.weaponState().dockedDrones = 3;
        for (int i = 0; i < 9; i++) HiveDroneEntity.spawn(helper.getLevel(), HiveCarrierWeapon.bay(turret, i).add(0, 3, 0), turret.getBlockPos(), none(), null, STATS, 60);
        still(helper, EntityType.HUSK, core(helper).offset(16, 0, 0));
        helper.onEachTick(() -> helper.assertTrue(dronesOf(helper, turret).size() <= 9, dronesOf(helper, turret).size() + " drones flying, cap 9"));
        helper.runAtTickTime(90, () -> {
            helper.assertTrue(turret.weaponState().dockedDrones == 3, turret.weaponState().dockedDrones + " docked: one left with no room to fly");
            done(helper, turret);
            helper.succeed();
        });
    }

    /** Docked drones are kept with the carrier: a save and load into a fresh turret still holds all three. */
    @GameTest(template = ARENA, batch = "dockedDronesAreSaved")
    public static void dockedDronesAreSaved(GameTestHelper helper) {
        TurretBaseBlockEntity turret = carrier(helper, 0, false);
        turret.weaponState().dockedDrones = 3;
        TurretBaseBlockEntity copy = new TurretBaseBlockEntity(turret.getBlockPos(), turret.getBlockState());
        copy.load(turret.saveWithoutMetadata());
        helper.assertTrue(copy.weaponState().dockedDrones == 3, copy.weaponState().dockedDrones + " docked after the load");
        done(helper, turret);
        helper.succeed();
    }

    /** A drone docking marks the turret's chunk for saving: the count is not lost to a save made while it was out. */
    @GameTest(template = ARENA, batch = "dockingMarksTheTurretForSaving")
    public static void dockingMarksTheTurretForSaving(GameTestHelper helper) {
        TurretBaseBlockEntity turret = carrier(helper, 0, false);
        LevelChunk chunk = helper.getLevel().getChunkAt(turret.getBlockPos());
        chunk.setUnsaved(false);
        HiveCarrierWeapon.dock(helper.getLevel(), turret.getBlockPos());
        helper.assertTrue(turret.weaponState().dockedDrones == 1 && chunk.isUnsaved(), "docked " + turret.weaponState().dockedDrones + ", chunk unsaved " + chunk.isUnsaved());
        done(helper, turret);
        helper.succeed();
    }

    /** Mined, the Fortress drops with its docked drones in the item (the loot table copies them, as creative's pick does). */
    @GameTest(template = ARENA, batch = "minedCarrierKeepsItsDockedDrones")
    public static void minedCarrierKeepsItsDockedDrones(GameTestHelper helper) {
        TurretBaseBlockEntity turret = carrier(helper, 0, false);
        turret.weaponState().dockedDrones = 3;
        BlockPos at = turret.getBlockPos();
        helper.getLevel().destroyBlock(at, true);
        List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(3), e -> e.getItem().is(BastionItems.FORTRESS_TURRET_BASE.get()));
        CompoundTag data = drops.isEmpty() ? null : BlockItem.getBlockEntityData(drops.get(0).getItem());
        helper.assertTrue(data != null && data.getInt("DockedDrones") == 3, "dropped base data " + data);
        drops.forEach(Entity::discard);
        clear(helper);
        helper.succeed();
    }

    /**
     * The carrier taken off its base gives its docked drones back as Hive Drone items: into the ammo slots (a bare base
     * takes any ammo), the rest dropped on the base.
     */
    @GameTest(template = ARENA, batch = "removedCarrierGivesItsDockedDronesBack")
    public static void removedCarrierGivesItsDockedDronesBack(GameTestHelper helper) {
        TurretBaseBlockEntity turret = carrier(helper, 0, false);
        turret.weaponState().dockedDrones = 3;
        turret.inventory().setStackInSlot(TurretInventory.WEAPON, ItemStack.EMPTY);
        int slotted = 0;
        for (int i = 0; i < turret.inventory().ammoSlots(); i++) {
            ItemStack stack = turret.inventory().getStackInSlot(TurretInventory.AMMO_START + i);
            if (stack.is(BastionItems.HIVE_DRONE.get())) slotted += stack.getCount();
        }
        helper.assertTrue(slotted + droneItems(helper, turret) == 3 && turret.weaponState().dockedDrones == 0,
                slotted + " Hive Drones in the ammo slots and " + droneItems(helper, turret) + " dropped, " + turret.weaponState().dockedDrones + " still docked");
        done(helper, turret);
        helper.succeed();
    }

    /** Destroyed at 0 HP its docked drones are lost with the weapon and the ammo (PLAN 4.1): none drop. */
    @GameTest(template = ARENA, batch = "destroyedCarrierLosesItsDockedDrones")
    public static void destroyedCarrierLosesItsDockedDrones(GameTestHelper helper) {
        TurretBaseBlockEntity turret = carrier(helper, 0, false);
        turret.weaponState().dockedDrones = 3;
        turret.takeDamage(helper.getLevel().damageSources().generic(), turret.maxHealth() + 1);
        helper.assertTrue(droneItems(helper, turret) == 0, droneItems(helper, turret) + " Hive Drone items dropped by a destroyed carrier");
        clear(helper);
        helper.succeed();
    }

    private static int droneItems(GameTestHelper helper, TurretBaseBlockEntity turret) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(turret.getBlockPos()).inflate(4), e -> e.getItem().is(BastionItems.HIVE_DRONE.get()))
                .stream().mapToInt(e -> e.getItem().getCount()).sum();
    }

    /** A wave, then the husk is gone: every drone flies home and docks (none left flying, three docked). */
    @GameTest(template = ARENA, timeoutTicks = 800, batch = "dronesFlyHomeAndDock")
    public static void dronesFlyHomeAndDock(GameTestHelper helper) {
        TurretBaseBlockEntity turret = carrier(helper, 3, true);
        LivingEntity husk = still(helper, EntityType.HUSK, core(helper).offset(16, 0, 0));
        helper.onEachTick(() -> {
            if (husk.isAlive() && dronesOf(helper, turret).size() == 3) husk.discard();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(!husk.isAlive(), "no wave yet");
            helper.assertTrue(dronesOf(helper, turret).isEmpty() && turret.weaponState().dockedDrones == 3,
                    dronesOf(helper, turret).size() + " flying, " + turret.weaponState().dockedDrones + " docked");
            done(helper, turret);
        });
    }

    /**
     * Nine drones of its own already out: no new drone (no Hive Drone item, no FE) while they fly; once idle ones dock
     * they may relaunch (for reload FE), but flying plus docked never pass nine.
     */
    @GameTest(template = ARENA, timeoutTicks = 260, batch = "carrierWaitsAtTheUnitCap")
    public static void carrierWaitsAtTheUnitCap(GameTestHelper helper) {
        TurretBaseBlockEntity turret = carrier(helper, 3, true);
        still(helper, EntityType.HUSK, core(helper).offset(16, 0, 0));
        for (int i = 0; i < 9; i++) {
            HiveDroneEntity.spawn(helper.getLevel(), helper.absoluteVec(Vec3.atBottomCenterOf(core(helper).offset(-3 + i, 6, -4))), turret.getBlockPos(),
                    none(), null, STATS, 60);
        }
        helper.onEachTick(() -> helper.assertTrue(dronesOf(helper, turret).size() + turret.weaponState().dockedDrones <= 9,
                dronesOf(helper, turret).size() + " flying + " + turret.weaponState().dockedDrones + " docked"));
        helper.runAtTickTime(80, () -> helper.assertTrue(items(turret) == 3 && turret.energy() == CAPACITY,
                "launched at the cap: " + items(turret) + " drones, " + turret.energy() + " FE"));
        helper.runAtTickTime(200, () -> {
            helper.assertTrue(items(turret) == 3, "a new drone left at the cap: " + items(turret) + " Hive Drones left");
            done(helper, turret);
            helper.succeed();
        });
    }

    /** Under Overwatch the carrier stays automatic (it does not turn: watch only) and still sends its wave. */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "overwatchOnlyWatchesTheCarrier")
    public static void overwatchOnlyWatchesTheCarrier(GameTestHelper helper) {
        TurretBaseBlockEntity turret = carrier(helper, 3, true);
        turret.setManual(new ManualControl(UUID.randomUUID(), "Tester"));
        still(helper, EntityType.HUSK, core(helper).offset(16, 0, 0));
        helper.succeedWhen(() -> {
            helper.assertFalse(turret.steering(), "the controller steers the carrier");
            helper.assertTrue(dronesOf(helper, turret).size() == 3, "drones out under Overwatch: " + dronesOf(helper, turret).size());
            turret.setManual(null);
            done(helper, turret);
        });
    }

    // --- recipes and JEI (Task 4) -----------------------------------------------------------------------------

    /** Three parts at the Part Workstation, the module at the Part Assembler (400 ticks, 25,000 FE), the drone at the Ammo Workstation. */
    @GameTest(template = YARD, batch = "hiveRecipesExist")
    public static void hiveRecipesExist(GameTestHelper helper) {
        for (String part : List.of("hive_hangar", "hive_relay", "hive_bays")) {
            helper.assertTrue(helper.getLevel().getRecipeManager().byKey(Bastion.id("workstation/part_workstation/" + part)).isPresent(), "no recipe for " + part);
        }
        WorkstationRecipe module = FortressBaseTests.recipe(helper, "part_assembler/hive_carrier_turret", WorkstationType.PART_ASSEMBLER, "bastion:hive_carrier_turret",
                Map.of("bastion:hive_hangar", 1, "bastion:hive_relay", 1, "bastion:hive_bays", 1));
        helper.assertTrue(module.time() == 400 && module.energy() == 25000, "module recipe " + module.time() + " ticks, " + module.energy() + " FE");
        FortressBaseTests.recipe(helper, "ammo_workstation/hive_drone", WorkstationType.AMMO_WORKSTATION, "bastion:hive_drone",
                Map.of("minecraft:iron_ingot", 2, "minecraft:copper_ingot", 2, "minecraft:redstone", 1, "minecraft:feather", 1));
        helper.succeed();
    }

    /** JEI and the Info tab: FE per drone and drones per wave, not "ammo 0" and heat; a wave every 15 s reads 0.07/s, not 0.1/s. */
    @GameTest(template = YARD, batch = "statRowsShowDronesPerWave")
    public static void statRowsShowDronesPerWave(GameTestHelper helper) {
        WeaponData hive = WeaponDataLoader.get(Bastion.id("hive_carrier"));
        StatSheet stats = StatSheet.of(hive, TurretTier.T1);
        List<WeaponStatRows.Row> right = WeaponStatRows.right(hive, stats, 500, 1);
        helper.assertTrue(right.stream().map(WeaponStatRows.Row::key).toList().equals(List.of("energy_per_drone", "energy_capacity", "max_input", "max_hp", "regen", "drones")),
                "right " + right);
        helper.assertTrue(right.get(0).value().equals("2k FE") && right.get(right.size() - 1).value().equals("3"), "right " + right);
        String rate = WeaponStatRows.left(hive, stats).stream().filter(r -> r.key().equals("fire_rate")).findFirst().orElseThrow().value();
        helper.assertTrue(rate.equals("0.07/s"), "fire rate " + rate);
        helper.succeed();
    }

    // --- final review fixes ---------------------------------------------------------------------------------

    /**
     * A drone with no bullets pressed up under a ceiling (it was climbing into it) spots a husk under the same ceiling:
     * its dive reaches the husk; it does not burst at the ceiling on the collision of its last climbing move.
     */
    @GameTest(template = ARENA, timeoutTicks = 300, batch = "kamikazeUnderACeilingReachesItsTarget")
    public static void kamikazeUnderACeilingReachesItsTarget(GameTestHelper helper) {
        platform(helper);
        for (int x = 0; x < SIZE; x++) for (int z = 0; z < SIZE; z++) helper.setBlock(sky(helper, x, 7, z), Blocks.STONE);
        HiveDroneEntity drone = drone(helper, sky(helper, 10, 6, 17), sky(helper, 12, 1, 17), new TargetFilter(), 0);
        LivingEntity husk = still(helper, EntityType.HUSK, sky(helper, 18, 1, 17));
        helper.succeedWhen(() -> {
            helper.assertTrue(drone.isRemoved(), "the drone is still flying (state " + drone.state() + ")");
            helper.assertTrue(lost(husk) > 14, "the dive burst away from the husk: it lost " + lost(husk));
            clear(helper);
        });
    }

    /**
     * A carrier under a low roof over the whole platform, a husk under the same roof that it sees from its doorways (a wave
     * goes out; the roof hides the cap's lookout), then the husk gone: the drones got out through their holes, cannot
     * climb, give the climb up, find nothing, come home and dock (three docked).
     */
    @GameTest(template = ARENA, timeoutTicks = 500, batch = "dronesUnderALowRoofStillDock")
    public static void dronesUnderALowRoofStillDock(GameTestHelper helper) {
        TurretBaseBlockEntity turret = carrier(helper, 3, true);
        for (int x = 0; x < SIZE; x++) for (int z = 0; z < SIZE; z++) helper.setBlock(sky(helper, x, 5, z), Blocks.STONE);
        LivingEntity husk = still(helper, EntityType.HUSK, core(helper).offset(16, 0, 0));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(dronesOf(helper, turret).size() == 3, "no wave under the low roof: " + dronesOf(helper, turret).size()))
                .thenExecute(husk::discard)
                .thenWaitUntil(() -> helper.assertTrue(turret.weaponState().dockedDrones == 3, turret.weaponState().dockedDrones + " docked, flying "
                        + dronesOf(helper, turret).stream().map(d -> d.state().name() + " at " + d.position().subtract(Vec3.atBottomCenterOf(turret.getBlockPos()))).toList()))
                .thenExecute(() -> done(helper, turret))
                .thenSucceed();
    }

    /** Its home now holds another module (a Brood Cannon): the drone does not fly in and vanish there; it stays out. */
    @GameTest(template = ARENA, timeoutTicks = 300, batch = "dronesDoNotDockIntoAnotherModule")
    public static void dronesDoNotDockIntoAnotherModule(GameTestHelper helper) {
        platform(helper);
        FortressTurretBaseBlock.placeAt(helper.getLevel(), helper.absolutePos(core(helper)), BastionBlocks.FORTRESS_TURRET_BASE.get());
        TurretBaseBlockEntity turret = (TurretBaseBlockEntity) helper.getBlockEntity(core(helper));
        turret.inventory().setStackInSlot(TurretInventory.WEAPON, new ItemStack(BastionItems.BROOD_CANNON_TURRET.get()));
        HiveDroneEntity drone = HiveDroneEntity.spawn(helper.getLevel(), helper.absoluteVec(Vec3.atBottomCenterOf(core(helper).above(2))), turret.getBlockPos(),
                none(), null, STATS, 60);
        helper.runAtTickTime(240, () -> {
            helper.assertFalse(drone.isRemoved(), "the drone docked into a Brood Cannon and was gone");
            done(helper, turret);
            helper.succeed();
        });
    }

    /**
     * Nothing in the way: a drone flies out of its hole, then home in through it and docks in its bay; it never vanishes
     * in mid-air over the hive (the showcase caught one: the stuck-near-home rule fired as it slowed over its bay).
     */
    @GameTest(template = ARENA, timeoutTicks = 500, batch = "dronesLandInTheirBay")
    public static void dronesLandInTheirBay(GameTestHelper helper) {
        TurretBaseBlockEntity turret = carrier(helper, 0, true);
        Vec3 bay = HiveCarrierWeapon.bay(turret, 0);
        HiveDroneEntity drone = HiveDroneEntity.spawn(helper.getLevel(), bay, turret.getBlockPos(), none(), null, STATS, 60);
        throughTheHolesOnly(helper, turret);
        Vec3[] last = new Vec3[1];
        helper.onEachTick(() -> {
            if (!drone.isRemoved()) last[0] = drone.position();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(drone.isRemoved() && turret.weaponState().dockedDrones == 1, "not docked yet (state " + drone.state() + ")");
            helper.assertTrue(last[0].distanceTo(bay) < 1.0, "it docked " + last[0].distanceTo(bay) + " blocks from its bay, at " + last[0].subtract(bay));
            done(helper, turret);
        });
    }

    /**
     * User 2026-10-24 16:55: waves go out for enemies up to 64 blocks away and drones reach them: range 64, patrol_radius 64
     * (circling at 32), shot_range 32 (they see and shoot 32 from themselves), in the mod's JSON and in what turrets use.
     */
    @GameTest(template = "arena", batch = "hiveReachesSixtyFourBlocks")
    public static void hiveReachesSixtyFourBlocks(GameTestHelper helper) {
        for (WeaponData data : new WeaponData[]{TurretStatsConfig.builtIn("hive_carrier"), WeaponDataLoader.get(Bastion.id("hive_carrier"))}) {
            helper.assertTrue(data.range() == 64 && data.param("patrol_radius") == 64 && data.param("shot_range") == 32,
                    "Hive reach: range " + data.range() + ", patrol_radius " + data.param("patrol_radius") + ", shot_range " + data.param("shot_range"));
        }
        helper.succeed();
    }

    /** Stone round {@code at} (walls and a roof two blocks up, the husk's column left open) or just a 3x3 roof three up. */
    private static void shelter(GameTestHelper helper, BlockPos at, boolean walls, net.minecraft.world.level.block.Block block) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (walls) {
                    for (int dy = 0; dy <= 2; dy++) if (dy == 2 || dx != 0 || dz != 0) helper.setBlock(at.offset(dx, dy, dz), block);
                } else {
                    helper.setBlock(at.offset(dx, 3, dz), block);
                }
            }
        }
    }

    /**
     * User 2026-10-24 17:09: a husk the drones cannot get at (boxed in: no open sky, not seen from over the hive) sends no
     * wave; before, such a mob in a cave or behind walls in range kept sending drones out and back every 15 s.
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "carrierSkipsTargetsDronesCannotReach")
    public static void carrierSkipsTargetsDronesCannotReach(GameTestHelper helper) {
        TurretBaseBlockEntity turret = carrier(helper, 4, true);
        BlockPos at = core(helper).offset(16, 0, 0);
        shelter(helper, at, true, Blocks.STONE);
        still(helper, EntityType.HUSK, at);
        helper.runAtTickTime(120, () -> {
            helper.assertTrue(dronesOf(helper, turret).isEmpty() && items(turret) == 4, "a wave went out for a boxed-in husk");
            shelter(helper, at, true, Blocks.AIR);
            done(helper, turret);
            helper.succeed();
        });
    }

    /** A husk under a roof (no open sky) that the hive still sees: drones can fly in under it, so a wave goes out. */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "carrierLaunchesForATargetItSees")
    public static void carrierLaunchesForATargetItSees(GameTestHelper helper) {
        TurretBaseBlockEntity turret = carrier(helper, 4, true);
        BlockPos at = core(helper).offset(16, 0, 0);
        shelter(helper, at, false, Blocks.STONE);
        still(helper, EntityType.HUSK, at);
        helper.succeedWhen(() -> {
            helper.assertTrue(!dronesOf(helper, turret).isEmpty(), "no wave for a husk the hive can see under a roof");
            shelter(helper, at, false, Blocks.AIR);
            done(helper, turret);
        });
    }
}
