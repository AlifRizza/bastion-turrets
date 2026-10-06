package dev.bastion.gametest;

import dev.bastion.Bastion;
import dev.bastion.registry.BastionBlocks;
import dev.bastion.registry.BastionItems;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.TurretInventory;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Laser Rifle: charge first, then one beam through every valid target on its line, stopped by blocks. */
@GameTestHolder(Bastion.MOD_ID)
@PrefixGameTestTemplate(false)
public class TurretLaserTests {
    private static final String ARENA = "arena";
    private static final BlockPos BASE = new BlockPos(2, 2, 3);

    private static TurretBaseBlockEntity laser(GameTestHelper helper, int cells) {
        helper.setBlock(BASE, BastionBlocks.TURRET_BASE.get());
        TurretBaseBlockEntity turret = (TurretBaseBlockEntity) helper.getBlockEntity(BASE);
        turret.inventory().setStackInSlot(TurretInventory.WEAPON, new ItemStack(BastionItems.LASER_RIFLE_TURRET.get()));
        turret.inventory().setStackInSlot(TurretInventory.AMMO_START, new ItemStack(BastionItems.LASER_CELL.get(), cells));
        return turret;
    }

    private static boolean hit(LivingEntity entity) {
        return !entity.isAlive() || entity.getHealth() < entity.getMaxHealth();
    }

    /** Three husks in a row all go down to one beam; the villager standing among them is untouched; one cell spent. */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "laserPiercesEveryValidTarget")
    public static void laserPiercesEveryValidTarget(GameTestHelper helper) {
        TurretBaseBlockEntity turret = laser(helper, 3);
        List<LivingEntity> husks = List.of(helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(5, 2, 3)),
                helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(7, 2, 3)), helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(10, 2, 3)));
        LivingEntity villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(8, 2, 3));
        helper.runAtTickTime(30, () -> helper.assertTrue(husks.stream().noneMatch(TurretLaserTests::hit), "fired before the charge finished"));
        helper.succeedWhen(() -> {
            helper.assertTrue(husks.stream().allMatch(TurretLaserTests::hit), "not every husk on the line was hit, state " + turret.state());
            helper.assertTrue(!hit(villager), "the villager on the line got hit");
            helper.assertTrue(turret.inventory().getStackInSlot(TurretInventory.AMMO_START).getCount() == 2, "expected one cell spent");
            turret.inventory().setStackInSlot(TurretInventory.WEAPON, ItemStack.EMPTY);
        });
    }

    /**
     * Charged weapons keep their charge on a target that moves: a husk zig-zagging side to side (~3 deg/tick as seen
     * from the turret, turning sharply) still gets shot. Before the fix every drift past aim_tolerance restarted the charge.
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "laserHitsAStrafingTarget")
    public static void laserHitsAStrafingTarget(GameTestHelper helper) {
        strafingTarget(helper, laser(helper, 3));
    }

    @GameTest(template = ARENA, timeoutTicks = 200, batch = "sniperHitsAStrafingTarget")
    public static void sniperHitsAStrafingTarget(GameTestHelper helper) {
        TurretBaseBlockEntity turret = laser(helper, 0);
        turret.inventory().setStackInSlot(TurretInventory.WEAPON, new ItemStack(BastionItems.SNIPER_TURRET.get()));
        turret.inventory().setStackInSlot(TurretInventory.AMMO_START, new ItemStack(BastionItems.SNIPER_ROUNDS.get(), 3));
        strafingTarget(helper, turret);
    }

    private static void strafingTarget(GameTestHelper helper, TurretBaseBlockEntity turret) {
        LivingEntity husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(7, 2, 3));
        Vec3 lane = helper.absoluteVec(new Vec3(7.5, 2, 3.5));
        int[] tick = {0};
        helper.onEachTick(() -> { // zig-zag at a steady 0.3 blocks/tick, turning sharply every 10 ticks, like a running mob
            int t = tick[0]++ % 20;
            double z = lane.z - 1.5 + 0.3 * (t < 10 ? t : 20 - t);
            husk.teleportTo(lane.x, lane.y, z);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(hit(husk), "never fired at the strafing husk, state " + turret.state());
            turret.inventory().setStackInSlot(TurretInventory.WEAPON, ItemStack.EMPTY);
        });
    }

    /** The beam goes through targets, not blocks: the husk behind the wall is never touched. */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "laserStopsAtBlocks")
    public static void laserStopsAtBlocks(GameTestHelper helper) {
        TurretBaseBlockEntity turret = laser(helper, 3);
        LivingEntity front = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(5, 2, 3));
        for (int y = 2; y <= 6; y++) for (int z = 1; z <= 5; z++) helper.setBlock(new BlockPos(7, y, z), Blocks.STONE);
        LivingEntity behind = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(9, 2, 3));
        helper.succeedWhen(() -> {
            helper.assertTrue(hit(front), "the husk in front was not hit, state " + turret.state());
            helper.assertTrue(!hit(behind), "the beam went through the wall");
            turret.inventory().setStackInSlot(TurretInventory.WEAPON, ItemStack.EMPTY);
        });
    }
}
