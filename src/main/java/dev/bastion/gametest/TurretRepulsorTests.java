package dev.bastion.gametest;

import dev.bastion.Bastion;
import dev.bastion.registry.BastionBlocks;
import dev.bastion.registry.BastionItems;
import dev.bastion.turret.LargeTurretBaseBlock;
import dev.bastion.turret.TurretBaseBlock;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.TurretInventory;
import dev.bastion.turret.TurretState;
import dev.bastion.weapon.Repulsion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Repulsor and Repulsor Dome (PLAN "Repulsor & Repulsor Dome"): pushes, no damage, FE, filters, walls. */
@GameTestHolder(Bastion.MOD_ID)
@PrefixGameTestTemplate(false)
public class TurretRepulsorTests {
    private static final String RANGE = "range", YARD = "yard";
    private static final float LIFT = 0.35f;

    /** How far {@code entity} moved over the ground since {@code start}. */
    private static double moved(LivingEntity entity, Vec3 start) {
        return Math.hypot(entity.getX() - start.x, entity.getZ() - start.z);
    }

    /**
     * Calibration: a mob without AI pushed for 3 and for 5 blocks on flat ground lands within half a block of that, and
     * one standing exactly on the push point goes the fallback way.
     */
    @GameTest(template = RANGE, timeoutTicks = 80, batch = "repulsionTravelsAsSet")
    public static void repulsionTravelsAsSet(GameTestHelper helper) {
        LivingEntity three = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(4, 2, 3));
        LivingEntity five = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(4, 2, 6));
        LivingEntity onTop = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(4, 2, 9));
        Vec3[] start = new Vec3[3];
        helper.runAtTickTime(5, () -> {
            start[0] = three.position();
            start[1] = five.position();
            start[2] = onTop.position();
            Repulsion.push(three, three.position().add(-1, 0, 0), new Vec3(1, 0, 0), 3, LIFT);
            Repulsion.push(five, five.position().add(-1, 0, 0), new Vec3(1, 0, 0), 5, LIFT);
            Repulsion.push(onTop, onTop.position(), new Vec3(1, 0, 0), 3, LIFT);
        });
        helper.runAtTickTime(60, () -> {
            double a = moved(three, start[0]), b = moved(five, start[1]);
            Bastion.LOGGER.info("[repulsionTravelsAsSet] pushed 3 -> {} blocks, 5 -> {} blocks", a, b);
            helper.assertTrue(Math.abs(a - 3) <= 0.5 && Math.abs(b - 5) <= 0.5, "pushed 3 -> " + a + " blocks, 5 -> " + b);
            helper.assertTrue(onTop.getX() - start[2].x > 2.5, "a mob on the push point did not go the fallback way: " + moved(onTop, start[2]));
            helper.succeed();
        });
    }

    private static final BlockPos REPULSOR = new BlockPos(3, 2, 5);

    /** A Repulsor on a standard floor base at {@code pos}, its capacitor filled (or empty). */
    private static TurretBaseBlockEntity repulsor(GameTestHelper helper, BlockPos pos, boolean charged) {
        helper.setBlock(pos, BastionBlocks.TURRET_BASE.get());
        TurretBaseBlockEntity base = (TurretBaseBlockEntity) helper.getBlockEntity(pos);
        base.inventory().setStackInSlot(TurretInventory.WEAPON, new ItemStack(BastionItems.REPULSOR_TURRET.get()));
        if (charged) fill(helper, pos);
        return base;
    }

    private static IEnergyStorage energy(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockEntity(pos).getCapability(ForgeCapabilities.ENERGY, Direction.UP).orElseThrow(IllegalStateException::new);
    }

    private static void fill(GameTestHelper helper, BlockPos pos) {
        IEnergyStorage energy = energy(helper, pos);
        while (energy.receiveEnergy(Integer.MAX_VALUE, false) > 0) ;
    }

    private static Vec3 spot(GameTestHelper helper, BlockPos pos) {
        return Vec3.atBottomCenterOf(helper.absolutePos(pos));
    }

    /**
     * Two husks in front (one straight ahead, one off to the side inside the cone) fly ~3 blocks in the same pulse (500 FE
     * spent, not 1,000); a cow right in front of the turret, in the cone too, is not a target and stays put; no damage.
     */
    @GameTest(template = RANGE, timeoutTicks = 120, batch = "repulsorPushesTheCone")
    public static void repulsorPushesTheCone(GameTestHelper helper) {
        TurretBaseBlockEntity turret = repulsor(helper, REPULSOR, true);
        LivingEntity ahead = helper.spawnWithNoFreeWill(EntityType.HUSK, REPULSOR.east(2));
        LivingEntity aside = helper.spawnWithNoFreeWill(EntityType.HUSK, REPULSOR.east(2).south());
        LivingEntity cow = helper.spawnWithNoFreeWill(EntityType.COW, REPULSOR.east());
        Vec3 a = spot(helper, REPULSOR.east(2)), b = spot(helper, REPULSOR.east(2).south()), c = spot(helper, REPULSOR.east());
        helper.succeedWhen(() -> {
            helper.assertTrue(moved(ahead, a) >= 2.5 && moved(aside, b) >= 2.5, "pushed " + moved(ahead, a) + " and " + moved(aside, b));
            helper.assertTrue(moved(cow, c) < 0.5, "pushed the cow " + moved(cow, c));
            helper.assertTrue(ahead.getHealth() == ahead.getMaxHealth() && aside.getHealth() == aside.getMaxHealth(), "the push did damage");
            helper.assertTrue(turret.energy() == 9500, "took " + (10000 - turret.energy()) / 500 + " pulses to push both");
        });
    }

    /** A husk behind a wall, inside the cone, stays put while the one in the open is pushed. */
    @GameTest(template = RANGE, timeoutTicks = 120, batch = "repulsorWallStopsTheWave")
    public static void repulsorWallStopsTheWave(GameTestHelper helper) {
        repulsor(helper, REPULSOR, true);
        for (int y = 2; y <= 4; y++) helper.setBlock(REPULSOR.east().south().atY(y), Blocks.STONE);
        LivingEntity open = helper.spawnWithNoFreeWill(EntityType.HUSK, REPULSOR.east(2));
        LivingEntity hidden = helper.spawnWithNoFreeWill(EntityType.HUSK, REPULSOR.east(2).south());
        Vec3 o = spot(helper, REPULSOR.east(2)), h = spot(helper, REPULSOR.east(2).south());
        helper.succeedWhen(() -> {
            helper.assertTrue(moved(open, o) >= 2.5, "the husk in the open moved " + moved(open, o));
            helper.assertTrue(moved(hidden, h) < 0.5, "the wave went through the wall: " + moved(hidden, h));
        });
    }

    /** Without FE: NO_AMMO and no push; filled, it pushes and each push costs energy_per_shot. */
    @GameTest(template = RANGE, timeoutTicks = 160, batch = "repulsorRunsOnEnergy")
    public static void repulsorRunsOnEnergy(GameTestHelper helper) {
        TurretBaseBlockEntity turret = repulsor(helper, REPULSOR, false);
        LivingEntity husk = helper.spawnWithNoFreeWill(EntityType.HUSK, REPULSOR.east(2));
        Vec3 start = spot(helper, REPULSOR.east(2));
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(turret.state() == TurretState.NO_AMMO, "expected NO_AMMO without FE, got " + turret.state());
            helper.assertTrue(moved(husk, start) < 0.5, "pushed without FE");
            helper.assertTrue(energy(helper, REPULSOR).receiveEnergy(Integer.MAX_VALUE, false) == 500, "max_input is not 500");
            fill(helper, REPULSOR);
            helper.assertTrue(turret.energy() == 10000, "capacity is not 10000: " + turret.energy());
        });
        helper.runAfterDelay(41, () -> helper.succeedWhen(() -> {
            helper.assertTrue(moved(husk, start) >= 2.5, "not pushed with FE");
            helper.assertTrue(turret.energy() == 9500, "a push should cost 500, capacitor at " + turret.energy());
        }));
    }

    /** Lowest corner of the dome's 2x2 base in the yard; its middle is at (15, _, 15). */
    private static final BlockPos DOME = new BlockPos(14, 2, 14);

    private static TurretBaseBlockEntity dome(GameTestHelper helper, boolean charged) {
        LargeTurretBaseBlock.placeAt(helper.getLevel(), helper.absolutePos(DOME), BastionBlocks.LARGE_TURRET_BASE.get());
        TurretBaseBlockEntity base = (TurretBaseBlockEntity) helper.getBlockEntity(DOME);
        base.inventory().setStackInSlot(TurretInventory.WEAPON, new ItemStack(BastionItems.REPULSOR_DOME_TURRET.get()));
        if (charged) fill(helper, DOME);
        return base;
    }

    /**
     * Husks two to three blocks out on all four sides (the north one behind a stone wall) fly ~5 blocks outward; a cow at
     * the same distance stays; nobody takes damage.
     */
    @GameTest(template = YARD, timeoutTicks = 160, batch = "domePushesEveryWay")
    public static void domePushesEveryWay(GameTestHelper helper) {
        dome(helper, true);
        BlockPos[] at = {new BlockPos(12, 2, 15), new BlockPos(18, 2, 15), new BlockPos(15, 2, 11), new BlockPos(15, 2, 18)};
        for (int x = 13; x <= 17; x++) for (int y = 2; y <= 4; y++) helper.setBlock(new BlockPos(x, y, 12), Blocks.STONE); // wall north
        LivingEntity[] husks = new LivingEntity[at.length];
        Vec3[] start = new Vec3[at.length];
        for (int i = 0; i < at.length; i++) {
            husks[i] = helper.spawnWithNoFreeWill(EntityType.HUSK, at[i]);
            start[i] = spot(helper, at[i]);
        }
        BlockPos cowAt = new BlockPos(12, 2, 12);
        LivingEntity cow = helper.spawnWithNoFreeWill(EntityType.COW, cowAt);
        Vec3 cowStart = spot(helper, cowAt);
        Vec3 middle = spot(helper, DOME).add(0.5, 0, 0.5);
        helper.succeedWhen(() -> {
            for (int i = 0; i < husks.length; i++) {
                double out = Math.hypot(husks[i].getX() - middle.x, husks[i].getZ() - middle.z)
                        - Math.hypot(start[i].x - middle.x, start[i].z - middle.z);
                helper.assertTrue(out >= 4, "husk " + i + " went " + out + " blocks outward");
                helper.assertTrue(husks[i].getHealth() == husks[i].getMaxHealth(), "the dome did damage");
            }
            helper.assertTrue(moved(cow, cowStart) < 0.5, "pushed the cow");
        });
    }

    /** Without FE: NO_AMMO and no push; filled, it pushes and the pulse costs 2,000. */
    @GameTest(template = YARD, timeoutTicks = 160, batch = "domeRunsOnEnergy")
    public static void domeRunsOnEnergy(GameTestHelper helper) {
        TurretBaseBlockEntity turret = dome(helper, false);
        BlockPos at = new BlockPos(18, 2, 15);
        LivingEntity husk = helper.spawnWithNoFreeWill(EntityType.HUSK, at);
        Vec3 start = spot(helper, at);
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(turret.state() == TurretState.NO_AMMO, "expected NO_AMMO without FE, got " + turret.state());
            helper.assertTrue(moved(husk, start) < 0.5, "pushed without FE");
            fill(helper, DOME);
            helper.assertTrue(turret.energy() == 40000, "capacity is not 40000: " + turret.energy());
        });
        helper.runAfterDelay(41, () -> helper.succeedWhen(() -> {
            helper.assertTrue(moved(husk, start) >= 4, "not pushed with FE");
            helper.assertTrue(turret.energy() == 38000, "a pulse should cost 2000, capacitor at " + turret.energy());
        }));
    }

    /** A Repulsor on a standard base mounted {@code facing} at {@code pos}, filled. */
    private static TurretBaseBlockEntity mounted(GameTestHelper helper, BlockPos pos, Direction facing) {
        helper.setBlock(pos, BastionBlocks.TURRET_BASE.get().defaultBlockState().setValue(TurretBaseBlock.FACING, facing));
        TurretBaseBlockEntity base = (TurretBaseBlockEntity) helper.getBlockEntity(pos);
        base.inventory().setStackInSlot(TurretInventory.WEAPON, new ItemStack(BastionItems.REPULSOR_TURRET.get()));
        fill(helper, pos);
        return base;
    }

    /**
     * On a wall, a husk hugging the wall beside the turret (closer to the wall than the turret's pivot) is pushed off the
     * wall, not into it. (One tucked right under or beside the base is hidden behind the base and never targeted.)
     */
    @GameTest(template = RANGE, timeoutTicks = 120, batch = "repulsorOnAWallPushesOffIt")
    public static void repulsorOnAWallPushesOffIt(GameTestHelper helper) {
        for (int y = 2; y <= 5; y++) for (int z = 4; z <= 8; z++) helper.setBlock(new BlockPos(10, y, z), Blocks.STONE); // the wall
        mounted(helper, new BlockPos(9, 3, 5), Direction.WEST);
        BlockPos at = new BlockPos(9, 2, 7);
        LivingEntity husk = helper.spawnWithNoFreeWill(EntityType.HUSK, at);
        Vec3 start = spot(helper, at);
        helper.succeedWhen(() -> helper.assertTrue(start.x - husk.getX() >= 0.6,
                "pushed " + (start.x - husk.getX()) + " blocks off the wall (x " + start.x + " -> " + husk.getX() + ")"));
    }

    /**
     * A husk behind a 1.5-high wall, only its head showing over it: the turret that aims at it also pushes it (no endless
     * empty pulses that cost FE and move nothing).
     */
    @GameTest(template = RANGE, timeoutTicks = 120, batch = "repulsorSeesOverAFence")
    public static void repulsorSeesOverAFence(GameTestHelper helper) {
        repulsor(helper, REPULSOR, true);
        for (int z = 3; z <= 7; z++) helper.setBlock(new BlockPos(REPULSOR.getX() + 1, 2, z), Blocks.COBBLESTONE_WALL);
        LivingEntity husk = helper.spawnWithNoFreeWill(EntityType.HUSK, REPULSOR.east(2));
        Vec3 start = spot(helper, REPULSOR.east(2));
        helper.succeedWhen(() -> helper.assertTrue(moved(husk, start) >= 1.5, "fired over the fence without pushing (moved " + moved(husk, start) + ")"));
    }

    /** Under a ceiling Repulsor, a husk right on its mount axis is aimed at, fired on and pushed out (any way). */
    @GameTest(template = RANGE, timeoutTicks = 160, batch = "repulsorBelowACeiling")
    public static void repulsorBelowACeiling(GameTestHelper helper) {
        helper.setBlock(new BlockPos(3, 6, 5), Blocks.STONE);
        mounted(helper, new BlockPos(3, 5, 5), Direction.DOWN);
        LivingEntity husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(3, 2, 5));
        Vec3 start = spot(helper, new BlockPos(3, 2, 5));
        helper.succeedWhen(() -> helper.assertTrue(moved(husk, start) >= 1.5, "not pushed out from under the ceiling turret: " + moved(husk, start)));
    }

    /** Heavy mobs (config repulsorHeavyMobs: iron golem, ravager, warden) still move, at 40 % of the push: 5 blocks -> ~2. */
    @GameTest(template = RANGE, timeoutTicks = 80, batch = "repulsionHeavyMobsGoLess")
    public static void repulsionHeavyMobsGoLess(GameTestHelper helper) {
        LivingEntity golem = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, new BlockPos(4, 2, 3));
        LivingEntity ravager = helper.spawnWithNoFreeWill(EntityType.RAVAGER, new BlockPos(4, 2, 8));
        Vec3[] start = new Vec3[2];
        helper.runAtTickTime(5, () -> {
            start[0] = golem.position();
            start[1] = ravager.position();
            Repulsion.push(golem, golem.position().add(-1, 0, 0), new Vec3(1, 0, 0), 5, LIFT);
            Repulsion.push(ravager, ravager.position().add(-1, 0, 0), new Vec3(1, 0, 0), 5, LIFT);
        });
        helper.runAtTickTime(60, () -> {
            double g = moved(golem, start[0]), r = moved(ravager, start[1]);
            helper.assertTrue(Math.abs(g - 2) <= 0.5 && Math.abs(r - 2) <= 0.5, "pushed for 5: iron golem " + g + ", ravager " + r + " (want ~2)");
            helper.succeed();
        });
    }

    /**
     * The Repulsor never takes on a heavy mob: a ravager (hostile, so a target by the default filter; iron golems are not)
     * in front of it is not fired at (no FE spent) and stays put.
     */
    @GameTest(template = RANGE, timeoutTicks = 80, batch = "repulsorIgnoresHeavyMobs")
    public static void repulsorIgnoresHeavyMobs(GameTestHelper helper) {
        TurretBaseBlockEntity turret = repulsor(helper, REPULSOR, true);
        LivingEntity ravager = helper.spawnWithNoFreeWill(EntityType.RAVAGER, REPULSOR.east(2));
        Vec3 start = spot(helper, REPULSOR.east(2));
        helper.runAtTickTime(60, () -> {
            helper.assertTrue(turret.energy() == 10000, "fired at the ravager: capacitor at " + turret.energy());
            helper.assertTrue(moved(ravager, start) < 0.5, "pushed the ravager " + moved(ravager, start));
            helper.succeed();
        });
    }

    /** The Dome throws a ravager (heavy) at 40 % of its push: ~2.4 of 6 blocks. */
    @GameTest(template = YARD, timeoutTicks = 160, batch = "domePushesHeavyMobsLess")
    public static void domePushesHeavyMobsLess(GameTestHelper helper) {
        dome(helper, true);
        BlockPos at = new BlockPos(19, 2, 15);
        LivingEntity ravager = helper.spawnWithNoFreeWill(EntityType.RAVAGER, at);
        Vec3 start = spot(helper, at);
        helper.succeedWhen(() -> {
            double m = moved(ravager, start);
            helper.assertTrue(m >= 1.8 && m <= 3.0, "the ravager moved " + m + " (want ~2.4)");
        });
    }

    /** The Repulsor reaches 5 blocks: a husk ~4.3 blocks out in front is pushed ~4 blocks. */
    @GameTest(template = RANGE, timeoutTicks = 120, batch = "repulsorReachesFiveBlocks")
    public static void repulsorReachesFiveBlocks(GameTestHelper helper) {
        repulsor(helper, REPULSOR, true);
        LivingEntity husk = helper.spawnWithNoFreeWill(EntityType.HUSK, REPULSOR.east(4));
        Vec3 start = spot(helper, REPULSOR.east(4));
        helper.succeedWhen(() -> helper.assertTrue(moved(husk, start) >= 3, "a husk 4 blocks out moved " + moved(husk, start)));
    }

    /** The Dome reaches 7 blocks: a husk ~6.8 blocks from its middle is thrown ~6 blocks. */
    @GameTest(template = YARD, timeoutTicks = 160, batch = "domeReachesSevenBlocks")
    public static void domeReachesSevenBlocks(GameTestHelper helper) {
        dome(helper, true);
        BlockPos at = new BlockPos(21, 2, 15);
        LivingEntity husk = helper.spawnWithNoFreeWill(EntityType.HUSK, at);
        Vec3 start = spot(helper, at);
        helper.succeedWhen(() -> helper.assertTrue(moved(husk, start) >= 4.5, "a husk 6.5 blocks out moved " + moved(husk, start)));
    }
}
