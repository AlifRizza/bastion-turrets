package dev.bastion.gametest;

import dev.bastion.Bastion;
import dev.bastion.registry.BastionBlocks;
import dev.bastion.registry.BastionItems;
import dev.bastion.registry.BastionWeaponTypes;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.TurretInventory;
import dev.bastion.turret.TurretState;
import dev.bastion.weapon.Bullets;
import dev.bastion.weapon.FireContext;
import dev.bastion.weapon.Hitscan;
import dev.bastion.weapon.StatSheet;
import dev.bastion.weapon.WeaponState;
import dev.bastion.weapon.WeaponType;
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
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.List;

/**
 * Fase 2 acceptance checks (PLAN 8): targeting, line of sight, ammo, state machine. Run with runGameTestServer.
 * Arenas are closed boxes and turrets are disarmed when done: GameTest places tests side by side and leaves them in place.
 */
@GameTestHolder(Bastion.MOD_ID)
@PrefixGameTestTemplate(false)
public class TurretCombatTests {
    /** data/bastion/structures/arena.nbt: a closed stone box (11x5x5 inside), so turrets cannot see neighbouring tests. */
    private static final String ARENA = "arena";
    // Relative y=1 is the template's first layer (the stone floor), so the inside starts at y=2.
    private static final BlockPos BASE = new BlockPos(2, 2, 3);
    private static final BlockPos FAR = new BlockPos(9, 2, 3);

    private static TurretBaseBlockEntity gunTurret(GameTestHelper helper, int kineticRounds) {
        helper.setBlock(BASE, BastionBlocks.TURRET_BASE.get());
        TurretBaseBlockEntity turret = (TurretBaseBlockEntity) helper.getBlockEntity(BASE);
        turret.inventory().setStackInSlot(TurretInventory.WEAPON, new ItemStack(BastionItems.GUN_TURRET.get()));
        if (kineticRounds > 0) {
            turret.inventory().setStackInSlot(TurretInventory.AMMO_START, new ItemStack(BastionItems.KINETIC_ROUNDS.get(), kineticRounds));
        }
        return turret;
    }

    /** GameTest leaves finished arenas in the world, so an armed turret would keep shooting later batches' mobs. */
    private static void disarm(TurretBaseBlockEntity turret) {
        turret.inventory().setStackInSlot(TurretInventory.WEAPON, ItemStack.EMPTY);
    }

    private static int rounds(TurretBaseBlockEntity turret) {
        return turret.inventory().getStackInSlot(TurretInventory.AMMO_START).getCount();
    }

    @GameTest(template = ARENA, timeoutTicks = 200, batch = "gunKillsHostileInRange")
    public static void gunKillsHostileInRange(GameTestHelper helper) {
        TurretBaseBlockEntity turret = gunTurret(helper, 10);
        LivingEntity husk = helper.spawnWithNoFreeWill(EntityType.HUSK, FAR);
        helper.succeedWhen(() -> {
            helper.assertTrue(!husk.isAlive(), "husk still alive, turret state " + turret.state());
            helper.assertTrue(rounds(turret) < 10, "no ammo was spent");
            disarm(turret);
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 200, batch = "wallBlocksTheShot")
    public static void wallBlocksTheShot(GameTestHelper helper) {
        TurretBaseBlockEntity turret = gunTurret(helper, 10);
        for (int y = 2; y <= 6; y++) {
            for (int z = 1; z <= 5; z++) helper.setBlock(new BlockPos(6, y, z), Blocks.STONE);
        }
        LivingEntity husk = helper.spawnWithNoFreeWill(EntityType.HUSK, FAR);
        helper.runAtTickTime(100, () -> {
            helper.assertTrue(husk.getHealth() == husk.getMaxHealth(), "shot went through the wall");
            helper.assertTrue(rounds(turret) == 10, "ammo spent without line of sight");
            helper.assertTrue(turret.state() == TurretState.IDLE, "expected IDLE, got " + turret.state());
            disarm(turret);
            helper.succeed();
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 200, batch = "stopsWithoutAmmo")
    public static void stopsWithoutAmmo(GameTestHelper helper) {
        TurretBaseBlockEntity turret = gunTurret(helper, 0);
        LivingEntity husk = helper.spawnWithNoFreeWill(EntityType.HUSK, FAR);
        helper.runAtTickTime(80, () -> {
            helper.assertTrue(husk.getHealth() == husk.getMaxHealth(), "fired without ammo");
            helper.assertTrue(turret.state() == TurretState.NO_AMMO, "expected NO_AMMO, got " + turret.state());
            disarm(turret);
            helper.succeed();
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 200, batch = "ignoresPassiveMobs")
    public static void ignoresPassiveMobs(GameTestHelper helper) {
        TurretBaseBlockEntity turret = gunTurret(helper, 10);
        LivingEntity cow = helper.spawnWithNoFreeWill(EntityType.COW, FAR);
        helper.runAtTickTime(80, () -> {
            helper.assertTrue(cow.getHealth() == cow.getMaxHealth(), "shot a passive mob");
            helper.assertTrue(turret.state() == TurretState.IDLE, "expected IDLE, got " + turret.state());
            disarm(turret);
            helper.succeed();
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 200, batch = "wrongAmmoIsNeverFired")
    public static void wrongAmmoIsNeverFired(GameTestHelper helper) {
        TurretBaseBlockEntity turret = gunTurret(helper, 0);
        turret.inventory().setStackInSlot(TurretInventory.AMMO_START, new ItemStack(BastionItems.SCATTER_SHELLS.get(), 8));
        IItemHandler handler = turret.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElseThrow(IllegalStateException::new);
        helper.assertTrue(!ItemHandlerHelper.insertItem(handler, new ItemStack(BastionItems.SCATTER_SHELLS.get()), false).isEmpty(),
                "a Gun turret must refuse scatter shells from automation");
        LivingEntity husk = helper.spawnWithNoFreeWill(EntityType.HUSK, FAR);
        helper.runAtTickTime(80, () -> {
            helper.assertTrue(husk.getHealth() == husk.getMaxHealth(), "fired with the wrong ammo: health " + husk.getHealth()
                    + ", state " + turret.state() + ", slot " + turret.inventory().getStackInSlot(TurretInventory.AMMO_START)
                    + ", last hurt by " + husk.getLastDamageSource());
            helper.assertTrue(turret.state() == TurretState.NO_AMMO, "expected NO_AMMO, got " + turret.state());
            disarm(turret);
            helper.succeed();
        });
    }

    // --- bullets (travel time, user decision 2026-10-06) ---------------------------------------

    /** A bullet arrives after flying, not on the tick it is fired: 1 block per tick over about 6 blocks. */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = "bulletsTakeTime")
    public static void bulletsTakeTime(GameTestHelper helper) {
        TurretBaseBlockEntity turret = gunTurret(helper, 0); // no ammo: it never fires by itself
        LivingEntity husk = helper.spawnWithNoFreeWill(EntityType.HUSK, FAR);
        Vec3 muzzle = helper.absoluteVec(Vec3.atCenterOf(BASE).add(1, 0.5, 0));
        Vec3 direction = husk.getBoundingBox().getCenter().subtract(muzzle).normalize();
        StatSheet stats = StatSheet.of(turret.inventory().weaponData(), turret.tier(), turret.inventory().modifierEffect());
        FireContext context = new FireContext(helper.getLevel(), turret, muzzle, direction, husk, stats, new WeaponState(), 1, false);
        WeaponType gun = BastionWeaponTypes.GUN.get();
        Bullets.fire(context, gun, 0, List.of(direction), 1, 0, Bullets.validTargets(context, gun),
                (bullet, hit) -> Hitscan.damage(hit.getEntity(), helper.getLevel(), 5));
        helper.assertTrue(husk.getHealth() == husk.getMaxHealth(), "the bullet hit on the tick it was fired");
        helper.runAtTickTime(3, () -> helper.assertTrue(husk.getHealth() == husk.getMaxHealth(), "the bullet arrived too early"));
        helper.runAtTickTime(12, () -> {
            helper.assertTrue(husk.getHealth() < husk.getMaxHealth(), "the bullet never arrived");
            disarm(turret);
            helper.succeed();
        });
    }

    /** Bullets fly through what the turret may not shoot: a villager in the line is spared, the husk behind is hit. */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "bulletsPassNonTargets")
    public static void bulletsPassNonTargets(GameTestHelper helper) {
        TurretBaseBlockEntity turret = gunTurret(helper, 10);
        LivingEntity villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(6, 2, 3));
        LivingEntity husk = helper.spawnWithNoFreeWill(EntityType.HUSK, FAR);
        helper.succeedWhen(() -> {
            helper.assertTrue(husk.getHealth() < husk.getMaxHealth(), "husk not hit, turret state " + turret.state());
            helper.assertTrue(villager.getHealth() == villager.getMaxHealth(), "the villager in the line was hit");
            disarm(turret);
        });
    }
}
