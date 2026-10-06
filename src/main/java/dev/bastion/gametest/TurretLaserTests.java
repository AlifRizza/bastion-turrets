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
