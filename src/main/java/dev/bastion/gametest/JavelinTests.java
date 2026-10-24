package dev.bastion.gametest;

import dev.bastion.Bastion;
import dev.bastion.registry.BastionBlocks;
import dev.bastion.projectile.JavelinMissileEntity;
import dev.bastion.registry.BastionItems;
import dev.bastion.turret.FortressTurretBaseBlock;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.ManualControl;
import dev.bastion.turret.TurretInventory;
import dev.bastion.weapon.types.JavelinWeapon;
import dev.bastion.workstation.WorkstationRecipe;
import dev.bastion.workstation.WorkstationType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Javelin Missile (PLAN "Turret 3x3"): item + FE per shot, the densest cluster of ground targets under open sky, never
 * a roofed target, a flyer, one inside min_range or from a roofed silo. GameTest runs underground in a generated world
 * (trees ~80 blocks over the test boxes), so each test builds its own stone platform high up at Y_SKY under open sky,
 * clears it first (and leftover mobs: the Javelin needs no line of sight and reaches 48 blocks), and has its own batch.
 */
@GameTestHolder(Bastion.MOD_ID)
@PrefixGameTestTemplate(false)
public class JavelinTests {
    private static final String ARENA = "arena";
    /** World height of the platform: over any terrain, with room for the climb (apex 60) under the build limit. */
    private static final int Y_SKY = 230;
    private static final int SIZE = 26;
    private static final int CAPACITY = 30000, PER_SHOT = 10000;

    /** A test-relative position on the platform: (x, y above its floor, z). */
    private static BlockPos sky(GameTestHelper helper, int x, int y, int z) {
        return new BlockPos(x, Y_SKY - helper.absolutePos(BlockPos.ZERO).getY() + y, z);
    }

    /** The missile's length in blocks (its 1.5-block hitbox, the model's body). */
    private static final double LENGTH = 1.5;

    private static BlockPos core(GameTestHelper helper) {
        return sky(helper, 4, 1, 13);
    }

    /**
     * Clears the space up there (a failed earlier test may have left a base, a test that passed as its missile left may
     * have left that missile in flight), lays the floor, places a Fortress with a Javelin.
     */
    private static TurretBaseBlockEntity javelin(GameTestHelper helper, int missiles, boolean charged) {
        Vec3 centre = helper.absoluteVec(Vec3.atCenterOf(core(helper)));
        helper.getLevel().getEntitiesOfClass(Mob.class, new AABB(centre, centre).inflate(80)).forEach(Entity::discard);
        helper.getLevel().getEntitiesOfClass(JavelinMissileEntity.class, new AABB(centre, centre).inflate(120)).forEach(Entity::discard);
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                for (int y = 1; y < 8; y++) helper.setBlock(sky(helper, x, y, z), Blocks.AIR);
                helper.setBlock(sky(helper, x, 0, z), Blocks.STONE);
            }
        }
        FortressTurretBaseBlock.placeAt(helper.getLevel(), helper.absolutePos(core(helper)), BastionBlocks.FORTRESS_TURRET_BASE.get());
        TurretBaseBlockEntity turret = (TurretBaseBlockEntity) helper.getBlockEntity(core(helper));
        turret.inventory().setStackInSlot(TurretInventory.WEAPON, new ItemStack(BastionItems.JAVELIN_TURRET.get()));
        if (missiles > 0) turret.inventory().setStackInSlot(TurretInventory.AMMO_START, new ItemStack(BastionItems.JAVELIN_MISSILE.get(), missiles));
        if (charged) charge(helper);
        return turret;
    }

    private static void charge(GameTestHelper helper) {
        IEnergyStorage energy = helper.getBlockEntity(core(helper)).getCapability(ForgeCapabilities.ENERGY, Direction.UP).orElseThrow(IllegalStateException::new);
        while (energy.receiveEnergy(Integer.MAX_VALUE, false) > 0) ;
    }

    private static int missiles(TurretBaseBlockEntity turret) {
        return turret.inventory().getStackInSlot(TurretInventory.AMMO_START).getCount();
    }

    /** Ends a test: the base, mobs and missiles up there go, so nothing keeps firing or falling into later tests. */
    private static void done(GameTestHelper helper, TurretBaseBlockEntity turret) {
        turret.inventory().setStackInSlot(TurretInventory.WEAPON, ItemStack.EMPTY);
        Vec3 centre = helper.absoluteVec(Vec3.atCenterOf(core(helper)));
        helper.getLevel().getEntitiesOfClass(Mob.class, new AABB(centre, centre).inflate(40)).forEach(Entity::discard);
        helper.getLevel().getEntitiesOfClass(JavelinMissileEntity.class, new AABB(centre, centre).inflate(120)).forEach(Entity::discard);
        helper.getLevel().destroyBlock(helper.absolutePos(core(helper)), false);
    }

    /** A husk that stands still and survives a blast, so tests can read who was picked. */
    private static LivingEntity husk(GameTestHelper helper, BlockPos pos) {
        LivingEntity husk = helper.spawnWithNoFreeWill(EntityType.HUSK, pos);
        husk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500);
        husk.setHealth(500);
        return husk;
    }

    /** No shot without FE, then one shot = one missile and energy_per_shot FE; one missile, so never a second. */
    @GameTest(template = ARENA, timeoutTicks = 300, batch = "javelinNeedsMissilesAndEnergy")
    public static void javelinNeedsMissilesAndEnergy(GameTestHelper helper) {
        TurretBaseBlockEntity turret = javelin(helper, 1, false);
        husk(helper, core(helper).offset(12, 0, 0));
        helper.runAtTickTime(80, () -> {
            helper.assertTrue(missiles(turret) == 1, "fired without FE: " + missiles(turret) + " missiles left");
            charge(helper);
        });
        helper.runAfterDelay(81, () -> helper.succeedWhen(() -> {
            helper.assertTrue(missiles(turret) == 0, "no shot after charging: " + missiles(turret) + " missiles, " + turret.energy() + " FE");
            helper.assertTrue(turret.energy() == CAPACITY - PER_SHOT, "energy after one shot: " + turret.energy());
            done(helper, turret);
        }));
    }

    /** A lone husk nearer, three bunched farther: it picks the bunch (more targets in one blast). */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "javelinPicksTheDensestCluster")
    public static void javelinPicksTheDensestCluster(GameTestHelper helper) {
        TurretBaseBlockEntity turret = javelin(helper, 4, true);
        BlockPos core = core(helper);
        husk(helper, core.offset(12, 0, 0));
        List<LivingEntity> bunch = List.of(husk(helper, core.offset(19, 0, -1)), husk(helper, core.offset(19, 0, 1)), husk(helper, core.offset(20, 0, 0)));
        helper.succeedWhen(() -> {
            helper.assertTrue(turret.target() != null && bunch.contains(turret.target()), "target " + turret.target() + ", not one of the bunch"
                    + " (sky over the bunch " + JavelinWeapon.openSky(bunch.get(0)) + ", over the silo " + JavelinWeapon.siloSeesSky(turret) + ")");
            done(helper, turret);
        });
    }

    /** A husk with a block over its head is never picked; one in the open farther away is. */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "javelinSkipsRoofedTargets")
    public static void javelinSkipsRoofedTargets(GameTestHelper helper) {
        TurretBaseBlockEntity turret = javelin(helper, 4, true);
        BlockPos roofed = core(helper).offset(12, 0, 0);
        helper.setBlock(roofed.above(3), Blocks.STONE);
        husk(helper, roofed);
        LivingEntity open = husk(helper, core(helper).offset(18, 0, 4));
        helper.succeedWhen(() -> {
            helper.assertTrue(turret.target() == open, "target " + turret.target() + ", expected the husk in the open");
            done(helper, turret);
        });
    }

    /** A phantom (a flyer) and a husk inside min_range: no target, no shot. */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "javelinIgnoresFlyersAndCloseTargets")
    public static void javelinIgnoresFlyersAndCloseTargets(GameTestHelper helper) {
        TurretBaseBlockEntity turret = javelin(helper, 4, true);
        helper.spawnWithNoFreeWill(EntityType.PHANTOM, core(helper).offset(14, 1, 0));
        husk(helper, core(helper).offset(5, 0, 0));
        helper.runAtTickTime(120, () -> {
            helper.assertTrue(turret.target() == null && missiles(turret) == 4, "target " + turret.target() + ", missiles " + missiles(turret));
            done(helper, turret);
            helper.succeed();
        });
    }

    /** A block above the silo: it holds fire; once the block goes, it fires. */
    @GameTest(template = ARENA, timeoutTicks = 300, batch = "javelinNeedsOpenSkyAboveItsSilo")
    public static void javelinNeedsOpenSkyAboveItsSilo(GameTestHelper helper) {
        TurretBaseBlockEntity turret = javelin(helper, 4, true);
        BlockPos lid = core(helper).above(4);
        helper.setBlock(lid, Blocks.STONE);
        husk(helper, core(helper).offset(14, 0, 0));
        helper.runAtTickTime(100, () -> {
            helper.assertTrue(missiles(turret) == 4, "fired from a roofed silo: " + missiles(turret) + " missiles left");
            helper.setBlock(lid, Blocks.AIR);
        });
        helper.runAfterDelay(101, () -> helper.succeedWhen(() -> {
            helper.assertTrue(missiles(turret) == 3, "no shot once the silo saw the sky: " + missiles(turret) + " missiles left");
            done(helper, turret);
        }));
    }

    // --- the missile (Task 2) ---------------------------------------------------------------------------------

    /** Follows the first Javelin missile up there tick by tick: where it was each tick, and whether it has landed. */
    private static final class Flight {
        final List<Vec3> path = new ArrayList<>();
        /** Its phase and whether it was hidden, each tick with path. */
        final List<Integer> phases = new ArrayList<>();
        final List<Boolean> hidden = new ArrayList<>();
        JavelinMissileEntity missile;
        boolean landed;

        static Flight of(GameTestHelper helper) {
            Flight flight = new Flight();
            Vec3 centre = helper.absoluteVec(Vec3.atCenterOf(core(helper)));
            helper.onEachTick(() -> {
                if (flight.missile == null) {
                    List<JavelinMissileEntity> found = helper.getLevel().getEntitiesOfClass(JavelinMissileEntity.class, new AABB(centre, centre).inflate(100));
                    if (!found.isEmpty()) flight.missile = found.get(0);
                }
                if (flight.missile == null || flight.landed) return;
                if (flight.missile.isRemoved()) flight.landed = true;
                else {
                    flight.path.add(flight.missile.position());
                    flight.phases.add(flight.missile.phase());
                    flight.hidden.add(flight.missile.isInvisible());
                }
            });
            return flight;
        }

        Vec3 last() {
            return path.get(path.size() - 1);
        }
    }

    private static double horizontal(Vec3 a, Vec3 b) {
        return Math.hypot(a.x - b.x, a.z - b.z);
    }

    private static float lost(LivingEntity mob) {
        return mob.getMaxHealth() - mob.getHealth();
    }

    /** Straight up from the silo to about apex blocks over it, then straight down onto the target. */
    @GameTest(template = ARENA, timeoutTicks = 400, batch = "javelinClimbsOutOfSightThenDives")
    public static void javelinClimbsOutOfSightThenDives(GameTestHelper helper) {
        TurretBaseBlockEntity turret = javelin(helper, 1, true);
        LivingEntity husk = husk(helper, core(helper).offset(14, 0, 0));
        Vec3 mouth = JavelinWeapon.siloMouth(turret);
        double apex = turret.inventory().weaponData().param("apex");
        Flight flight = Flight.of(helper);
        helper.succeedWhen(() -> {
            helper.assertTrue(flight.landed, "not landed yet (" + flight.path.size() + " ticks seen)");
            double peak = flight.path.stream().mapToDouble(Vec3::y).max().orElse(0);
            helper.assertTrue(peak >= mouth.y + apex - 2, "peak " + peak + ", launched at " + mouth.y);
            helper.assertTrue(horizontal(flight.path.get(0), mouth) < 0.5, "left from " + flight.path.get(0) + ", not the silo " + mouth);
            for (int i = flight.path.size() - 10; i < flight.path.size(); i++) {
                double side = horizontal(flight.path.get(i), flight.path.get(i - 1));
                helper.assertTrue(side < 0.3, "dive tick " + i + " moved " + side + " sideways");
            }
            helper.assertTrue(horizontal(flight.last(), husk.position()) < 0.5, "came down at " + flight.last() + ", husk at " + husk.position());
            done(helper, turret);
        });
    }

    /**
     * Pushed half out of the silo it holds still for hold_ticks, its tail still inside and its nose out (user 2026-10-24:
     * it floated too high); then the engine lights and every tick it climbs further than the last.
     */
    @GameTest(template = ARENA, timeoutTicks = 400, batch = "javelinHoldsOverItsSiloThenSpeedsUp")
    public static void javelinHoldsOverItsSiloThenSpeedsUp(GameTestHelper helper) {
        TurretBaseBlockEntity turret = javelin(helper, 1, true);
        husk(helper, core(helper).offset(14, 0, 0));
        Vec3 mouth = JavelinWeapon.siloMouth(turret);
        int hold = Math.round(turret.inventory().weaponData().param("hold_ticks"));
        Flight flight = Flight.of(helper);
        helper.succeedWhen(() -> {
            helper.assertTrue(flight.landed, "not landed yet");
            List<Double> climb = new ArrayList<>();
            int still = 0;
            for (int i = 1; i < flight.path.size(); i++) {
                double rise = flight.path.get(i).y - flight.path.get(i - 1).y;
                if (flight.phases.get(i) == JavelinMissileEntity.COLD && Math.abs(rise) < 1e-6) {
                    still++;
                    double tail = flight.path.get(i).y - mouth.y;
                    helper.assertTrue(tail < -0.25 && tail + LENGTH > 0.5, "held with its tail " + tail + " from the silo mouth, not inside with its nose out");
                }
                if (flight.phases.get(i) == JavelinMissileEntity.CLIMB) climb.add(rise);
            }
            helper.assertTrue(still >= hold - 1, "held still over the silo " + still + " ticks, not " + hold);
            helper.assertTrue(climb.size() > 2, "climbed " + climb.size() + " ticks");
            for (int i = 1; i < climb.size(); i++) {
                helper.assertTrue(climb.get(i) > climb.get(i - 1), "climb tick " + i + " rose " + climb.get(i) + " after " + climb.get(i - 1));
            }
            helper.assertTrue(climb.get(0) < 0.3 && climb.get(climb.size() - 1) > 3, "the climb rose " + climb.get(0) + " .. " + climb.get(climb.size() - 1) + " a tick");
            done(helper, turret);
        });
    }

    /** At the top it is out of sight (hidden, still) for hang_ticks; then it dives onto the target. */
    @GameTest(template = ARENA, timeoutTicks = 400, batch = "javelinWaitsOutOfSightBeforeItDives")
    public static void javelinWaitsOutOfSightBeforeItDives(GameTestHelper helper) {
        TurretBaseBlockEntity turret = javelin(helper, 1, true);
        LivingEntity husk = husk(helper, core(helper).offset(14, 0, 0));
        int hang = Math.round(turret.inventory().weaponData().param("hang_ticks"));
        Flight flight = Flight.of(helper);
        helper.succeedWhen(() -> {
            helper.assertTrue(flight.landed, "not landed yet");
            int first = flight.phases.indexOf(JavelinMissileEntity.HIDDEN), last = flight.phases.lastIndexOf(JavelinMissileEntity.HIDDEN);
            helper.assertTrue(first >= 0 && last - first + 1 >= hang - 1 && last - first + 1 <= hang + 1, "hidden " + (first < 0 ? 0 : last - first + 1) + " ticks, not " + hang);
            for (int i = first; i <= last; i++) {
                helper.assertTrue(flight.hidden.get(i), "drawn while out of sight at tick " + i);
                helper.assertTrue(i == first || flight.path.get(i).y == flight.path.get(i - 1).y, "moved up or down while out of sight at tick " + i);
            }
            helper.assertTrue(!flight.hidden.get(flight.path.size() - 1) && flight.phases.get(flight.path.size() - 1) == JavelinMissileEntity.DIVE,
                    "it did not come down in sight");
            helper.assertTrue(horizontal(flight.last(), husk.position()) < 0.5, "came down at " + flight.last() + ", husk at " + husk.position());
            done(helper, turret);
        });
    }

    /**
     * Clients keep the missile's pitch from its spawn packet (nose up): a velocity update must not turn it, or a missile
     * held still over its silo is drawn by a pitch made from (0, 0.01, 0) or (0, 0, 0) — upside down or flat.
     */
    @GameTest(template = ARENA, batch = "javelinKeepsItsNoseUpOnVelocityUpdates")
    public static void javelinKeepsItsNoseUpOnVelocityUpdates(GameTestHelper helper) {
        Vec3 at = helper.absoluteVec(Vec3.atBottomCenterOf(core(helper)));
        JavelinMissileEntity missile = new JavelinMissileEntity(helper.getLevel(), helper.absolutePos(core(helper)), 40, 15, 6)
                .fly(at, null, at, 60, 10, 40, 60, 20);
        missile.xRotO = missile.yRotO = 0; // as on a client: built from the spawn packet, moveTo zeroes the old rotation
        missile.lerpMotion(0, 0.01, 0);
        helper.assertTrue(missile.getXRot() == -90, "pitch " + missile.getXRot() + " after a velocity update while it rises");
        missile.lerpMotion(0, 0, 0);
        helper.assertTrue(missile.getXRot() == -90, "pitch " + missile.getXRot() + " after a velocity update while it is still");
        helper.succeed();
    }

    /**
     * A missile already on its way to the bunch, another Javelin's: this one goes for the lone husk instead of the bunch
     * that missile will kill (user 2026-10-24: any missile still in flight, not only its own).
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "javelinSparesAClusterAMissileIsOn")
    public static void javelinSparesAClusterAMissileIsOn(GameTestHelper helper) {
        TurretBaseBlockEntity turret = javelin(helper, 4, true);
        BlockPos core = core(helper);
        LivingEntity lone = husk(helper, core.offset(12, 0, 0));
        List<LivingEntity> bunch = List.of(husk(helper, core.offset(19, 0, -1)), husk(helper, core.offset(19, 0, 1)), husk(helper, core.offset(20, 0, 0)));
        Vec3 silo = JavelinWeapon.siloMouth(turret);
        helper.getLevel().addFreshEntity(new JavelinMissileEntity(helper.getLevel(), turret.getBlockPos().offset(0, 0, 30), 40, 15, 6)
                .fly(silo, bunch.get(0), bunch.get(0).position(), 60, 0, 300, 0, 20)); // still climbing all test long
        helper.succeedWhen(() -> {
            helper.assertTrue(turret.target() == lone, "target " + turret.target() + ", not the lone husk (the bunch already has a missile coming)");
            done(helper, turret);
        });
    }

    /** hang_ticks 1 (config): the move over the drop point still happens out of sight, never as a streak across the sky. */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "javelinNeverJumpsInSight")
    public static void javelinNeverJumpsInSight(GameTestHelper helper) {
        TurretBaseBlockEntity turret = javelin(helper, 0, false);
        Vec3 from = JavelinWeapon.siloMouth(turret), drop = from.add(12, -1, 0);
        Flight flight = Flight.of(helper);
        helper.getLevel().addFreshEntity(new JavelinMissileEntity(helper.getLevel(), turret.getBlockPos(), 40, 15, 6).fly(from, null, drop, 20, 0, 4, 1, 4));
        helper.succeedWhen(() -> {
            helper.assertTrue(flight.landed, "not landed yet");
            for (int i = 1; i < flight.path.size(); i++) {
                helper.assertTrue(horizontal(flight.path.get(i), flight.path.get(i - 1)) < 1.5 || flight.hidden.get(i),
                        "jumped " + horizontal(flight.path.get(i), flight.path.get(i - 1)) + " blocks in sight at tick " + i);
            }
            done(helper, turret);
        });
    }

    /** Its own next shot: once its missile is up for the bunch it turns to the lone husk (a fire-rate module fires within a flight). */
    @GameTest(template = ARENA, timeoutTicks = 300, batch = "javelinTurnsAwayFromItsOwnMissilesCluster")
    public static void javelinTurnsAwayFromItsOwnMissilesCluster(GameTestHelper helper) {
        TurretBaseBlockEntity turret = javelin(helper, 4, true);
        BlockPos core = core(helper);
        LivingEntity lone = husk(helper, core.offset(12, 0, 0));
        List<LivingEntity> bunch = List.of(husk(helper, core.offset(19, 0, -1)), husk(helper, core.offset(19, 0, 1)), husk(helper, core.offset(20, 0, 0)));
        Vec3 centre = helper.absoluteVec(Vec3.atCenterOf(core));
        helper.succeedWhen(() -> {
            helper.assertFalse(helper.getLevel().getEntitiesOfClass(JavelinMissileEntity.class, new AABB(centre, centre).inflate(120)).isEmpty(), "no missile up yet");
            helper.assertTrue(turret.target() == lone, "target " + (turret.target() != null && bunch.contains(turret.target()) ? "still the bunch" : String.valueOf(turret.target()))
                    + " with its missile on the bunch");
            done(helper, turret);
        });
    }

    /** The target steps 5 blocks aside during the dive: the missile follows and lands on it. */
    @GameTest(template = ARENA, timeoutTicks = 400, batch = "javelinDropsOnAMovedTarget")
    public static void javelinDropsOnAMovedTarget(GameTestHelper helper) {
        TurretBaseBlockEntity turret = javelin(helper, 1, true);
        LivingEntity husk = husk(helper, core(helper).offset(14, 0, 0));
        Vec3 moved = husk.position().add(0, 0, 5);
        Flight flight = Flight.of(helper);
        helper.onEachTick(() -> {
            if (flight.missile != null && flight.missile.phase() == JavelinMissileEntity.DIVE && husk.position().z < moved.z) {
                husk.teleportTo(moved.x, moved.y, moved.z);
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(flight.landed, "not landed yet");
            helper.assertTrue(horizontal(flight.last(), moved) < 1.5, "came down at " + flight.last() + ", husk moved to " + moved);
            helper.assertTrue(lost(husk) > 30, "husk lost only " + lost(husk));
            done(helper, turret);
        });
    }

    /** The target walks under a roof during the climb: the missile falls on its last spot under open sky. */
    @GameTest(template = ARENA, timeoutTicks = 400, batch = "javelinFallsOnTheLastOpenSkyPoint")
    public static void javelinFallsOnTheLastOpenSkyPoint(GameTestHelper helper) {
        TurretBaseBlockEntity turret = javelin(helper, 1, true);
        BlockPos open = core(helper).offset(14, 0, 0), roofed = core(helper).offset(14, 0, 6);
        helper.setBlock(roofed.above(3), Blocks.STONE);
        LivingEntity husk = husk(helper, open);
        Vec3 spot = husk.position(), hidden = helper.absoluteVec(Vec3.atBottomCenterOf(roofed));
        Flight flight = Flight.of(helper);
        helper.onEachTick(() -> {
            if (flight.missile != null && husk.position().z < hidden.z) husk.teleportTo(hidden.x, hidden.y, hidden.z);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(flight.landed, "not landed yet");
            helper.assertTrue(horizontal(flight.last(), spot) < 1.5, "came down at " + flight.last() + ", last open spot " + spot);
            done(helper, turret);
        });
    }

    /**
     * damage at the centre, down to blast_edge_damage at blast_radius: the husk it lands on loses ~40, one 5 blocks
     * away (4.7 to its box) ~20. A third husk 3 blocks the other side makes the middle one the densest pick.
     */
    @GameTest(template = ARENA, timeoutTicks = 400, batch = "javelinBlastFallsOffToTheEdge")
    public static void javelinBlastFallsOffToTheEdge(GameTestHelper helper) {
        TurretBaseBlockEntity turret = javelin(helper, 1, true);
        BlockPos at = core(helper).offset(14, 0, 0);
        LivingEntity centre = husk(helper, at), edge = husk(helper, at.offset(0, 0, 5));
        husk(helper, at.offset(0, 0, -3));
        Flight flight = Flight.of(helper);
        helper.succeedWhen(() -> {
            helper.assertTrue(flight.landed, "not landed yet");
            helper.assertTrue(lost(centre) > 36 && lost(centre) <= 40, "centre lost " + lost(centre));
            helper.assertTrue(lost(edge) > 17 && lost(edge) < 23, "edge (5 blocks) lost " + lost(edge));
            done(helper, turret);
        });
    }

    /** A cow and a villager beside the target: the blast hurts the husk only. */
    @GameTest(template = ARENA, timeoutTicks = 400, batch = "javelinBlastSparesNonTargets")
    public static void javelinBlastSparesNonTargets(GameTestHelper helper) {
        TurretBaseBlockEntity turret = javelin(helper, 1, true);
        BlockPos at = core(helper).offset(14, 0, 0);
        LivingEntity husk = husk(helper, at);
        LivingEntity cow = helper.spawnWithNoFreeWill(EntityType.COW, at.offset(0, 0, 2));
        LivingEntity villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, at.offset(2, 0, 0));
        Flight flight = Flight.of(helper);
        helper.succeedWhen(() -> {
            helper.assertTrue(flight.landed, "not landed yet");
            helper.assertTrue(lost(husk) > 30, "husk lost only " + lost(husk));
            helper.assertTrue(lost(cow) == 0 && lost(villager) == 0, "cow lost " + lost(cow) + ", villager " + lost(villager));
            done(helper, turret);
        });
    }

    /** The turret is broken while the missile climbs: it still lands and hurts the hostile (Enemy fallback); never saved. */
    @GameTest(template = ARENA, timeoutTicks = 400, batch = "javelinLandsAfterItsTurretIsGone")
    public static void javelinLandsAfterItsTurretIsGone(GameTestHelper helper) {
        TurretBaseBlockEntity turret = javelin(helper, 1, true);
        LivingEntity husk = husk(helper, core(helper).offset(14, 0, 0));
        Flight flight = Flight.of(helper);
        helper.onEachTick(() -> {
            if (flight.missile != null && !turret.isRemoved()) {
                helper.assertFalse(flight.missile.shouldBeSaved(), "a Javelin missile would be saved with its chunk");
                helper.getLevel().destroyBlock(helper.absolutePos(core(helper)), false);
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(turret.isRemoved(), "the turret is still there");
            helper.assertTrue(flight.landed, "not landed yet");
            helper.assertTrue(lost(husk) > 30, "husk lost only " + lost(husk));
            Vec3 centre = husk.position();
            helper.getLevel().getEntitiesOfClass(Mob.class, new AABB(centre, centre).inflate(40)).forEach(Entity::discard);
        });
    }

    // --- recipes and Overwatch (Task 4) -----------------------------------------------------------------------

    /** Three parts at the Part Workstation, the module at the Part Assembler (Fortress-sized: 400 ticks, 25,000 FE), the missile at the Ammo Workstation. */
    @GameTest(template = ARENA, batch = "javelinRecipesExist")
    public static void javelinRecipesExist(GameTestHelper helper) {
        for (String part : List.of("javelin_silo", "javelin_radar", "javelin_mount")) {
            helper.assertTrue(helper.getLevel().getRecipeManager().byKey(Bastion.id("workstation/part_workstation/" + part)).isPresent(), "no recipe for " + part);
        }
        WorkstationRecipe module = FortressBaseTests.recipe(helper, "part_assembler/javelin_turret", WorkstationType.PART_ASSEMBLER, "bastion:javelin_turret",
                Map.of("bastion:javelin_silo", 1, "bastion:javelin_radar", 1, "bastion:javelin_mount", 1));
        helper.assertTrue(module.time() == 400 && module.energy() == 25000, "module recipe " + module.time() + " ticks, " + module.energy() + " FE");
        FortressBaseTests.recipe(helper, "ammo_workstation/javelin_missile", WorkstationType.AMMO_WORKSTATION, "bastion:javelin_missile",
                Map.of("minecraft:iron_ingot", 4, "minecraft:tnt", 2, "minecraft:firework_rocket", 1, "minecraft:redstone", 2));
        helper.succeed();
    }

    /** Under Overwatch the missile comes down on the spot under the crosshair (no creature there), its mark on that spot. */
    @GameTest(template = ARENA, timeoutTicks = 400, batch = "manualJavelinDropsOnTheCrosshair")
    public static void manualJavelinDropsOnTheCrosshair(GameTestHelper helper) {
        TurretBaseBlockEntity turret = javelin(helper, 1, true);
        ManualControl control = new ManualControl(UUID.randomUUID(), "Tester");
        turret.setManual(control);
        Vec3 spot = helper.absoluteVec(Vec3.atBottomCenterOf(core(helper).offset(16, 0, 5)));
        helper.onEachTick(() -> control.input(spot, -1, true, helper.getLevel().getGameTime()));
        Flight flight = Flight.of(helper);
        helper.succeedWhen(() -> {
            helper.assertTrue(flight.landed, "not landed yet: " + turret.state() + ", " + missiles(turret) + " missiles");
            helper.assertTrue(horizontal(flight.last(), spot) < 1, "came down at " + flight.last() + ", crosshair on " + spot);
            helper.assertTrue(flight.missile.targetId() == -1 && horizontal(flight.missile.dropPoint(), spot) < 0.01,
                    "marked " + flight.missile.targetId() + " at " + flight.missile.dropPoint());
            turret.setManual(null);
            done(helper, turret);
        });
    }

    /** Under Overwatch, trigger held, it holds fire on a spot inside min_range and on one under a roof. */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "manualJavelinRefusesRoofedOrClosePoints")
    public static void manualJavelinRefusesRoofedOrClosePoints(GameTestHelper helper) {
        TurretBaseBlockEntity turret = javelin(helper, 2, true);
        ManualControl control = new ManualControl(UUID.randomUUID(), "Tester");
        turret.setManual(control);
        BlockPos roofed = core(helper).offset(16, 0, 0);
        helper.setBlock(roofed.above(3), Blocks.STONE);
        Vec3 close = helper.absoluteVec(Vec3.atBottomCenterOf(core(helper).offset(5, 0, 0)));
        Vec3 under = helper.absoluteVec(Vec3.atBottomCenterOf(roofed));
        helper.onEachTick(() -> control.input(helper.getTick() < 60 ? close : under, -1, true, helper.getLevel().getGameTime()));
        helper.runAtTickTime(59, () -> helper.assertTrue(missiles(turret) == 2, "fired inside min_range"));
        helper.runAtTickTime(120, () -> {
            helper.assertTrue(missiles(turret) == 2, "fired under a roof");
            turret.setManual(null);
            done(helper, turret);
            helper.succeed();
        });
    }

    // --- final review fixes ---------------------------------------------------------------------------------

    /**
     * The target walks on in the open early in the climb, then under a roof: the missile comes down where it last stood
     * in the open, not where it stood at launch (the drop point follows it during the climb too).
     */
    @GameTest(template = ARENA, timeoutTicks = 400, batch = "javelinFollowsThroughTheClimb")
    public static void javelinFollowsThroughTheClimb(GameTestHelper helper) {
        TurretBaseBlockEntity turret = javelin(helper, 1, true);
        BlockPos roofed = core(helper).offset(14, 0, 8);
        helper.setBlock(roofed.above(3), Blocks.STONE);
        LivingEntity husk = husk(helper, core(helper).offset(14, 0, 0));
        Vec3 walked = helper.absoluteVec(Vec3.atBottomCenterOf(core(helper).offset(14, 0, 4))), hidden = helper.absoluteVec(Vec3.atBottomCenterOf(roofed));
        Flight flight = Flight.of(helper);
        // Thresholds, not exact sizes: onEachTick runnables run in hash order, so this one may see the path before or after
        // Flight adds to it in a tick and could skip any one size.
        helper.onEachTick(() -> {
            if (flight.missile == null || flight.missile.phase() != JavelinMissileEntity.CLIMB) return;
            int n = flight.path.size();
            if (n >= 5 && n < 30 && husk.position().z < walked.z) husk.teleportTo(walked.x, walked.y, walked.z);
            if (n >= 30 && husk.position().z < hidden.z) husk.teleportTo(hidden.x, hidden.y, hidden.z);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(flight.landed, "not landed yet");
            helper.assertTrue(horizontal(flight.last(), walked) < 1.5, "came down at " + flight.last() + ", last open spot " + walked);
            done(helper, turret);
        });
    }

    /** The Nether's roof: the heightmap there is the bedrock ceiling's, but the Nether never counts as open sky. */
    @GameTest(template = ARENA, batch = "javelinNeverSeesSkyInTheNether")
    public static void javelinNeverSeesSkyInTheNether(GameTestHelper helper) {
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        helper.assertTrue(nether != null, "no Nether in the test server");
        helper.assertFalse(JavelinWeapon.openSky(nether, new BlockPos(0, 200, 0)), "open sky on the Nether roof");
        helper.assertTrue(JavelinWeapon.openSky(helper.getLevel(), helper.absolutePos(sky(helper, 2, 2, 2))), "no open sky over the overworld platform");
        helper.succeed();
    }

    /** Under Overwatch, a spot on top of a bottom slab under open sky is fine ground to drop on. */
    @GameTest(template = ARENA, timeoutTicks = 400, batch = "manualJavelinDropsOntoASlab")
    public static void manualJavelinDropsOntoASlab(GameTestHelper helper) {
        TurretBaseBlockEntity turret = javelin(helper, 1, true);
        ManualControl control = new ManualControl(UUID.randomUUID(), "Tester");
        turret.setManual(control);
        BlockPos slab = core(helper).offset(16, 0, -4);
        helper.setBlock(slab, Blocks.STONE_SLAB);
        Vec3 spot = helper.absoluteVec(Vec3.atBottomCenterOf(slab).add(0, 0.5, 0));
        helper.onEachTick(() -> control.input(spot, -1, true, helper.getLevel().getGameTime()));
        Flight flight = Flight.of(helper);
        helper.succeedWhen(() -> {
            helper.assertTrue(flight.landed, "not landed yet: " + turret.state() + ", " + missiles(turret) + " missiles");
            helper.assertTrue(horizontal(flight.last(), spot) < 1, "came down at " + flight.last() + ", crosshair on " + spot);
            turret.setManual(null);
            done(helper, turret);
        });
    }

    /**
     * A missile that missed its flight time (its chunk was unloaded, then loaded again) is gone at its next tick, without
     * a blast: built at tick 0 and only added at tick 80, it never comes down on the husk under it.
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "staleJavelinMissileIsDiscarded")
    public static void staleJavelinMissileIsDiscarded(GameTestHelper helper) {
        TurretBaseBlockEntity turret = javelin(helper, 0, true);
        LivingEntity husk = husk(helper, core(helper).offset(14, 0, 0));
        JavelinMissileEntity missile = new JavelinMissileEntity(helper.getLevel(), turret.getBlockPos(), 40, 15, 6)
                .fly(husk.position().add(0, 3, 0), husk, husk.position(), 4, 0, 2, 0, 2);
        helper.runAtTickTime(80, () -> helper.getLevel().addFreshEntity(missile));
        helper.runAtTickTime(100, () -> {
            helper.assertTrue(missile.isRemoved(), "a stale missile still flies: " + missile.phase() + ", " + missile.ticksToImpact() + " ticks left");
            helper.assertTrue(lost(husk) == 0, "a stale missile hurt the husk: " + lost(husk));
            done(helper, turret);
            helper.succeed();
        });
    }
}
