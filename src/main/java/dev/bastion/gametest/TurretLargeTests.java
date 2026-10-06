package dev.bastion.gametest;

import dev.bastion.Bastion;
import dev.bastion.registry.BastionBlocks;
import dev.bastion.registry.BastionItems;
import dev.bastion.turret.LargeTurretBaseBlock;
import dev.bastion.turret.TurretBaseBlock;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.TurretInventory;
import dev.bastion.turret.TurretState;
import dev.bastion.weapon.types.MissileLauncherWeapon;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Large Turret Base (2x2 multiblock) and the Missile Launcher. Missiles climb before they dive, so these run in the
 * taller "range" box (24 x 10 x 11 inside; tools/gen_structures.py).
 */
@GameTestHolder(Bastion.MOD_ID)
@PrefixGameTestTemplate(false)
public class TurretLargeTests {
    private static final String RANGE = "range";
    private static final BlockPos MIN = new BlockPos(2, 2, 5);

    private static TurretBaseBlockEntity large(GameTestHelper helper) {
        LargeTurretBaseBlock.placeAt(helper.getLevel(), helper.absolutePos(MIN), BastionBlocks.LARGE_TURRET_BASE.get());
        return (TurretBaseBlockEntity) helper.getBlockEntity(MIN);
    }

    private static TurretBaseBlockEntity launcher(GameTestHelper helper, Item ammo, int count) {
        TurretBaseBlockEntity turret = large(helper);
        turret.inventory().setStackInSlot(TurretInventory.WEAPON, new ItemStack(BastionItems.MISSILE_LAUNCHER_TURRET.get()));
        if (count > 0) turret.inventory().setStackInSlot(TurretInventory.AMMO_START, new ItemStack(ammo, count));
        return turret;
    }

    private static void disarm(TurretBaseBlockEntity turret) {
        turret.inventory().setStackInSlot(TurretInventory.WEAPON, ItemStack.EMPTY);
    }

    @GameTest(template = RANGE, batch = "largeBaseIsOneBlock")
    public static void largeBaseIsOneBlock(GameTestHelper helper) {
        TurretBaseBlockEntity turret = large(helper);
        for (BlockPos p : List.of(MIN, MIN.east(), MIN.south(), MIN.south().east())) {
            helper.assertBlockPresent(BastionBlocks.LARGE_TURRET_BASE.get(), p);
            helper.assertTrue(TurretBaseBlock.core(helper.getLevel(), helper.absolutePos(p)) == turret, "part " + p + " does not lead to the core");
        }
        helper.assertTrue(turret.mountCenter().equals(Vec3.atLowerCornerOf(helper.absolutePos(MIN)).add(1, 0.5, 1)),
                "turret does not turn around the middle of its 2x2: " + turret.mountCenter());
        helper.getLevel().destroyBlock(helper.absolutePos(MIN.south().east()), true); // break a part, not the core
        for (BlockPos p : List.of(MIN, MIN.east(), MIN.south(), MIN.south().east())) helper.assertBlockPresent(Blocks.AIR, p);
        List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(MIN)).inflate(3));
        helper.assertTrue(drops.size() == 1 && drops.get(0).getItem().is(BastionItems.LARGE_TURRET_BASE.get()),
                "expected exactly one Large Turret Base drop, got " + drops.stream().map(e -> e.getItem().toString()).toList());
        helper.succeed();
    }

    @GameTest(template = RANGE, batch = "moduleSizes")
    public static void moduleSizes(GameTestHelper helper) {
        TurretBaseBlockEntity large = large(helper);
        helper.setBlock(new BlockPos(10, 2, 5), BastionBlocks.TURRET_BASE.get());
        TurretBaseBlockEntity small = (TurretBaseBlockEntity) helper.getBlockEntity(new BlockPos(10, 2, 5));
        ItemStack missiles = new ItemStack(BastionItems.MISSILE_LAUNCHER_TURRET.get()), gun = new ItemStack(BastionItems.GUN_TURRET.get());
        helper.assertTrue(large.inventory().isItemValid(TurretInventory.WEAPON, missiles), "large base refuses the Missile Launcher");
        helper.assertTrue(!large.inventory().isItemValid(TurretInventory.WEAPON, gun), "large base accepts a small module");
        helper.assertTrue(!small.inventory().isItemValid(TurretInventory.WEAPON, missiles), "standard base accepts the Missile Launcher");
        helper.succeed();
    }

    /** Tubes load one at a time, one Missiles item each; the pods hold their fixed upward angle. */
    @GameTest(template = RANGE, timeoutTicks = 200, batch = "missileReload")
    public static void missileReload(GameTestHelper helper) {
        TurretBaseBlockEntity turret = launcher(helper, BastionItems.MISSILES.get(), 5);
        helper.runAtTickTime(150, () -> {
            int loaded = Integer.bitCount(turret.weaponState().tubes);
            helper.assertTrue(loaded == 5, "expected 5 loaded tubes, got " + loaded);
            helper.assertTrue(turret.inventory().getStackInSlot(TurretInventory.AMMO_START).isEmpty(), "missiles left in the slot");
            helper.assertTrue(Math.abs(turret.pitch() + 30) < 1e-3, "pods not at their fixed 30 deg: pitch " + turret.pitch());
            disarm(turret);
            helper.succeed();
        });
    }

    /** Autoloader (+40% reload speed, Rocket and Missile Launcher only): the same launcher fills its tubes faster. */
    @GameTest(template = RANGE, timeoutTicks = 200, batch = "autoloaderReloadsFaster")
    public static void autoloaderReloadsFaster(GameTestHelper helper) {
        TurretBaseBlockEntity plain = launcher(helper, BastionItems.MISSILES.get(), 12);
        BlockPos other = MIN.east(6);
        LargeTurretBaseBlock.placeAt(helper.getLevel(), helper.absolutePos(other), BastionBlocks.LARGE_TURRET_BASE.get());
        TurretBaseBlockEntity fast = (TurretBaseBlockEntity) helper.getBlockEntity(other);
        fast.inventory().setStackInSlot(TurretInventory.WEAPON, new ItemStack(BastionItems.MISSILE_LAUNCHER_TURRET.get()));
        fast.inventory().setStackInSlot(TurretInventory.AMMO_START, new ItemStack(BastionItems.MISSILES.get(), 12));
        fast.inventory().setStackInSlot(TurretInventory.MODIFIER_START, new ItemStack(BastionItems.AUTOLOADER.get()));
        helper.runAtTickTime(120, () -> {
            int without = Integer.bitCount(plain.weaponState().tubes), with = Integer.bitCount(fast.weaponState().tubes);
            helper.assertTrue(with > without, "Autoloader loaded " + with + " tubes, without it " + without);
            disarm(plain);
            disarm(fast);
            helper.succeed();
        });
    }

    @GameTest(template = RANGE, timeoutTicks = 400, batch = "missileKills")
    public static void missileKills(GameTestHelper helper) {
        TurretBaseBlockEntity turret = launcher(helper, BastionItems.CREATIVE_AMMO.get(), 1);
        LivingEntity husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(20, 2, 7));
        helper.succeedWhen(() -> {
            helper.assertTrue(!husk.isAlive(), "husk alive, state " + turret.state() + ", tubes " + Integer.bitCount(turret.weaponState().tubes));
            disarm(turret);
        });
    }

    /** A full salvo spreads over every target in range. */
    @GameTest(template = RANGE, timeoutTicks = 300, batch = "salvoSpreads")
    public static void salvoSpreads(GameTestHelper helper) {
        TurretBaseBlockEntity turret = launcher(helper, BastionItems.CREATIVE_AMMO.get(), 1);
        turret.setSalvo(true);
        turret.weaponState().tubes = MissileLauncherWeapon.ALL_TUBES;
        List<LivingEntity> husks = List.of(helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(19, 2, 3)),
                helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(20, 2, 7)),
                helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(19, 2, 10)));
        helper.succeedWhen(() -> {
            for (LivingEntity husk : husks) helper.assertTrue(!husk.isAlive(), "a husk survived the salvo at " + husk.blockPosition());
            disarm(turret);
        });
    }

    @GameTest(template = RANGE, timeoutTicks = 100, batch = "missileOutOfAmmo")
    public static void missileOutOfAmmo(GameTestHelper helper) {
        TurretBaseBlockEntity turret = launcher(helper, BastionItems.MISSILES.get(), 0);
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(turret.state() == TurretState.NO_AMMO, "expected NO_AMMO, got " + turret.state());
            disarm(turret);
            helper.succeed();
        });
    }
}
