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
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Mortar: an arc over a wall onto a ground target, a fire patch that burns everyone and lights wood; no flyers, no close shots. */
@GameTestHolder(Bastion.MOD_ID)
@PrefixGameTestTemplate(false)
public class TurretMortarTests {
    /** 24 x 30 x 11 inside: the arcs climb ~22 blocks. Its floor is at y 1 (GameTest places structures a block up). */
    private static final String PIT = "pit";
    private static final BlockPos LARGE = new BlockPos(2, 2, 5);
    private static final int SHELLS = 4;

    private static TurretBaseBlockEntity mortar(GameTestHelper helper) {
        LargeTurretBaseBlock.placeAt(helper.getLevel(), helper.absolutePos(LARGE), BastionBlocks.LARGE_TURRET_BASE.get());
        TurretBaseBlockEntity turret = (TurretBaseBlockEntity) helper.getBlockEntity(LARGE);
        turret.inventory().setStackInSlot(TurretInventory.WEAPON, new ItemStack(BastionItems.MORTAR_TURRET.get()));
        turret.inventory().setStackInSlot(TurretInventory.AMMO_START, new ItemStack(BastionItems.MORTAR_SHELLS.get(), SHELLS));
        return turret;
    }

    private static int shellsLeft(TurretBaseBlockEntity turret) {
        return turret.inventory().getStackInSlot(TurretInventory.AMMO_START).getCount();
    }

    /**
     * A husk behind a wall the turret cannot see over: the shell arcs over it and bursts on the husk. The fire patch burns
     * the husk and the cow beside it (fire does not pick targets), and the planks under them catch vanilla fire.
     */
    @GameTest(template = PIT, timeoutTicks = 300, batch = "mortarShellsOverWall")
    public static void mortarShellsOverWall(GameTestHelper helper) {
        TurretBaseBlockEntity turret = mortar(helper);
        for (int z = 2; z <= 10; z++) for (int y = 2; y <= 5; y++) helper.setBlock(new BlockPos(9, y, z), Blocks.STONE_BRICKS);
        for (int x = 13; x <= 17; x++) for (int z = 4; z <= 8; z++) helper.setBlock(new BlockPos(x, 1, z), Blocks.OAK_PLANKS);
        LivingEntity husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(15, 2, 6));
        LivingEntity cow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(16, 2, 7));
        helper.succeedWhen(() -> {
            helper.assertTrue(husk.getHealth() < husk.getMaxHealth(), "no shell landed yet, state " + turret.state());
            helper.assertTrue(TurretBurn.isBurning(husk), "the husk is not burning");
            helper.assertTrue(TurretBurn.isBurning(cow), "the cow in the patch is not burning");
            boolean fire = false;
            for (int x = 14; x <= 16; x++) for (int z = 5; z <= 7; z++) {
                fire |= helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(x, 2, z))).getBlock() instanceof BaseFireBlock;
            }
            helper.assertTrue(fire, "no fire on the planks");
            helper.assertTrue(shellsLeft(turret) < SHELLS, "no shell used");
            turret.inventory().setStackInSlot(TurretInventory.WEAPON, ItemStack.EMPTY);
        });
    }

    /** A phantom in range (a flyer) and a husk inside min_range: it never fires. */
    @GameTest(template = PIT, timeoutTicks = 160, batch = "mortarIgnoresFlyersAndCloseTargets")
    public static void mortarIgnoresFlyersAndCloseTargets(GameTestHelper helper) {
        TurretBaseBlockEntity turret = mortar(helper);
        helper.spawnWithNoFreeWill(EntityType.PHANTOM, new BlockPos(15, 9, 6));
        helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(7, 2, 6));
        helper.runAtTickTime(140, () -> {
            helper.assertTrue(turret.state() == TurretState.IDLE, "expected IDLE, got " + turret.state());
            helper.assertTrue(shellsLeft(turret) == SHELLS, "it fired");
            turret.inventory().setStackInSlot(TurretInventory.WEAPON, ItemStack.EMPTY);
            helper.succeed();
        });
    }
}
