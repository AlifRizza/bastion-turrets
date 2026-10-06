package dev.bastion.gametest;

import dev.bastion.Bastion;
import dev.bastion.registry.BastionBlocks;
import dev.bastion.registry.BastionItems;
import dev.bastion.turret.LargeTurretBaseBlock;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.TurretBurn;
import dev.bastion.turret.TurretInventory;
import dev.bastion.turret.TurretState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Tesla Coil (Forge Energy, random bolts) and Flamethrower (cone, burning). */
@GameTestHolder(Bastion.MOD_ID)
@PrefixGameTestTemplate(false)
public class TurretTeslaFlameTests {
    private static final String RANGE = "range", ARENA = "arena";
    private static final BlockPos LARGE = new BlockPos(2, 2, 5);
    private static final BlockPos SMALL = new BlockPos(2, 2, 3);
    private static final int CAPACITY = 60000, PER_SHOT = 3000, MAX_INPUT = 1000;

    private static TurretBaseBlockEntity tesla(GameTestHelper helper, boolean charged) {
        LargeTurretBaseBlock.placeAt(helper.getLevel(), helper.absolutePos(LARGE), BastionBlocks.LARGE_TURRET_BASE.get());
        TurretBaseBlockEntity turret = (TurretBaseBlockEntity) helper.getBlockEntity(LARGE);
        turret.inventory().setStackInSlot(TurretInventory.WEAPON, new ItemStack(BastionItems.TESLA_TURRET.get()));
        if (charged) {
            // Through a part of the 2x2, as a cable on any of its blocks would.
            IEnergyStorage energy = energy(helper, LARGE.east());
            while (energy.receiveEnergy(Integer.MAX_VALUE, false) > 0) ;
        }
        return turret;
    }

    private static IEnergyStorage energy(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockEntity(pos).getCapability(ForgeCapabilities.ENERGY, Direction.UP).orElseThrow(IllegalStateException::new);
    }

    private static TurretBaseBlockEntity flamethrower(GameTestHelper helper, int fuel) {
        helper.setBlock(SMALL, BastionBlocks.TURRET_BASE.get());
        TurretBaseBlockEntity turret = (TurretBaseBlockEntity) helper.getBlockEntity(SMALL);
        turret.inventory().setStackInSlot(TurretInventory.WEAPON, new ItemStack(BastionItems.FLAMETHROWER_TURRET.get()));
        if (fuel > 0) turret.inventory().setStackInSlot(TurretInventory.AMMO_START, new ItemStack(BastionItems.FUEL_CANISTER.get(), fuel));
        return turret;
    }

    private static void disarm(TurretBaseBlockEntity turret) {
        turret.inventory().setStackInSlot(TurretInventory.WEAPON, ItemStack.EMPTY);
    }

    private static boolean hurt(LivingEntity entity) {
        return entity.getHealth() < entity.getMaxHealth();
    }

    /** The capacitor takes at most max_input per transfer and stops when full; without energy the coil waits. */
    @GameTest(template = RANGE, timeoutTicks = 100, batch = "teslaRunsOnEnergy")
    public static void teslaRunsOnEnergy(GameTestHelper helper) {
        TurretBaseBlockEntity turret = tesla(helper, false);
        LivingEntity husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(10, 2, 6));
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(turret.state() == TurretState.NO_AMMO, "expected NO_AMMO without energy, got " + turret.state());
            helper.assertTrue(!hurt(husk), "zapped without energy");
            IEnergyStorage energy = energy(helper, LARGE);
            helper.assertTrue(energy.receiveEnergy(5000, false) == MAX_INPUT, "one transfer must stop at max_input");
            while (energy.receiveEnergy(Integer.MAX_VALUE, false) > 0) ;
            helper.assertTrue(energy.getEnergyStored() == CAPACITY, "capacitor holds " + energy.getEnergyStored());
            disarm(turret);
            helper.assertTrue(!energy.canReceive() && energy.receiveEnergy(1000, false) == 0, "takes energy without an energy weapon");
            helper.succeed();
        });
    }

    /** One discharge: two bolts at two different targets out of three, paid from the capacitor. */
    @GameTest(template = RANGE, timeoutTicks = 200, batch = "teslaZapsTwoTargets")
    public static void teslaZapsTwoTargets(GameTestHelper helper) {
        TurretBaseBlockEntity turret = tesla(helper, true);
        List<LivingEntity> husks = List.of(helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(10, 2, 2)),
                helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(11, 2, 6)), helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(10, 2, 10)));
        helper.succeedWhen(() -> {
            helper.assertTrue(turret.energy() < CAPACITY, "no discharge yet, state " + turret.state());
            helper.assertTrue(turret.energy() == CAPACITY - PER_SHOT, "expected one discharge, energy " + turret.energy());
            long struck = husks.stream().filter(TurretTeslaFlameTests::hurt).count();
            helper.assertTrue(struck == 2, "expected 2 struck targets, got " + struck);
            disarm(turret);
        });
    }

    /** A lone target takes both bolts. */
    @GameTest(template = RANGE, timeoutTicks = 200, batch = "teslaLoneTargetTakesBoth")
    public static void teslaLoneTargetTakesBoth(GameTestHelper helper) {
        TurretBaseBlockEntity turret = tesla(helper, true);
        LivingEntity husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(11, 2, 6));
        helper.succeedWhen(() -> {
            helper.assertTrue(turret.energy() < CAPACITY, "no discharge yet, state " + turret.state());
            // 2 x 10 damage, a little less through the husk's natural armor
            helper.assertTrue(!husk.isAlive() || husk.getHealth() <= husk.getMaxHealth() - 19, "husk took one bolt only: " + husk.getHealth());
            disarm(turret);
        });
    }

    /** The cone burns the husk in front and leaves the cow beside it alone (not a valid target); fuel is spent. */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "flamethrowerBurnsValidTargets")
    public static void flamethrowerBurnsValidTargets(GameTestHelper helper) {
        TurretBaseBlockEntity turret = flamethrower(helper, 4);
        LivingEntity husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(6, 2, 3));
        LivingEntity cow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(6, 2, 2));
        helper.succeedWhen(() -> {
            helper.assertTrue(hurt(husk) && TurretBurn.isBurning(husk), "husk not burning, state " + turret.state());
            helper.assertTrue(!hurt(cow) && !TurretBurn.isBurning(cow), "the cow in the cone got burnt");
            helper.assertTrue(turret.inventory().getStackInSlot(TurretInventory.AMMO_START).getCount() < 4, "no fuel spent");
            disarm(turret);
        });
    }

    /**
     * With more enemies in range the flame never goes out: when its target dies the turret takes the next one at once
     * and keeps firing while it swings over (sweep_tolerance), instead of idling until the next target search.
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "flamethrowerSweepsToNextTarget")
    public static void flamethrowerSweepsToNextTarget(GameTestHelper helper) {
        TurretBaseBlockEntity turret = flamethrower(helper, 8);
        helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(5, 2, 2));
        helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(5, 2, 5));
        long[] killedAt = {-1};
        helper.onEachTick(() -> {
            if (killedAt[0] < 0) {
                if (turret.state() == TurretState.FIRING && turret.target() != null) {
                    turret.target().kill();
                    killedAt[0] = helper.getTick();
                }
            } else if (helper.getTick() == killedAt[0] + 2) {
                LivingEntity next = turret.target();
                helper.assertTrue(next != null && next.isAlive(), "no new target right after the kill, state " + turret.state());
                helper.assertTrue(turret.state() == TurretState.FIRING || turret.state() == TurretState.COOLDOWN,
                        "the flame went out between targets: " + turret.state());
                disarm(turret);
                helper.succeed();
            }
        });
    }

    /** Fire-immune mobs are never engaged: no fuel wasted on a blaze. */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "flamethrowerIgnoresFireImmune")
    public static void flamethrowerIgnoresFireImmune(GameTestHelper helper) {
        TurretBaseBlockEntity turret = flamethrower(helper, 4);
        LivingEntity blaze = helper.spawnWithNoFreeWill(EntityType.BLAZE, new BlockPos(6, 2, 3));
        helper.runAtTickTime(60, () -> {
            helper.assertTrue(turret.state() == TurretState.IDLE, "expected IDLE, got " + turret.state());
            helper.assertTrue(turret.inventory().getStackInSlot(TurretInventory.AMMO_START).getCount() == 4, "fuel spent on a blaze");
            helper.assertTrue(blaze.getHealth() == blaze.getMaxHealth(), "the blaze got hurt");
            disarm(turret);
            helper.succeed();
        });
    }

    /** Burning hurts every second until it runs out; water puts it out at once. */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "burnHurtsAndWaterPutsItOut")
    public static void burnHurtsAndWaterPutsItOut(GameTestHelper helper) {
        LivingEntity dry = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(3, 2, 2));
        LivingEntity wet = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(8, 2, 4));
        TurretBurn.ignite(dry, 60, 2);
        TurretBurn.ignite(wet, 60, 2);
        helper.setBlock(new BlockPos(8, 2, 4), Blocks.WATER);
        for (BlockPos wall : List.of(new BlockPos(7, 2, 4), new BlockPos(9, 2, 4), new BlockPos(8, 2, 3), new BlockPos(8, 2, 5))) {
            helper.setBlock(wall, Blocks.STONE); // keep the water from spreading to the dry husk
        }
        helper.runAtTickTime(45, () -> { // two ticks of 2 damage, a little less through the husk's natural armor
            helper.assertTrue(TurretBurn.isBurning(dry) && dry.getHealth() <= dry.getMaxHealth() - 3, "burn did not tick: " + dry.getHealth());
            helper.assertTrue(!TurretBurn.isBurning(wet) && !hurt(wet), "water did not put the fire out");
            helper.succeed();
        });
    }
}
