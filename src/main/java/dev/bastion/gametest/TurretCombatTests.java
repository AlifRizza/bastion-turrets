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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.PlayLevelSoundEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

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
        FireContext context = new FireContext(helper.getLevel(), turret, muzzle, direction, husk, stats, new WeaponState(), 1, false, null);
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

    /**
     * User report 2026-10-08 (Railgun on a superflat world): a 40 block/tick slug fired down at a husk a few blocks away
     * must hit it even though the step's far end is below the world's bottom (it used to drop the whole step).
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "fastBulletIntoTheVoidStillHits")
    public static void fastBulletIntoTheVoidStillHits(GameTestHelper helper) {
        TurretBaseBlockEntity turret = gunTurret(helper, 0);
        LivingEntity husk = helper.spawnWithNoFreeWill(EntityType.HUSK, FAR);
        Vec3 muzzle = helper.absoluteVec(Vec3.atCenterOf(BASE).add(1, 2.5, 0)); // above the husk: the shot goes down
        Vec3 direction = husk.getBoundingBox().getCenter().subtract(muzzle).normalize();
        Vec3 farEnd = muzzle.add(direction.scale(40));
        helper.assertTrue(!helper.getLevel().isInWorldBounds(BlockPos.containing(farEnd)), "set-up: the step's far end must be out of the world, " + farEnd);
        StatSheet stats = StatSheet.of(turret.inventory().weaponData(), turret.tier(), turret.inventory().modifierEffect());
        FireContext context = new FireContext(helper.getLevel(), turret, muzzle, direction, husk, stats, new WeaponState(), 1, false, null);
        WeaponType gun = BastionWeaponTypes.GUN.get();
        Bullets.fire(context, gun, 0, List.of(direction), 40, 0, Bullets.validTargets(context, gun),
                (bullet, hit) -> Hitscan.damage(hit.getEntity(), helper.getLevel(), 5));
        helper.assertTrue(husk.getHealth() < husk.getMaxHealth(), "the slug vanished instead of hitting the husk " + muzzle.distanceTo(husk.position()) + " blocks ahead");
        disarm(turret);
        helper.succeed();
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

    /**
     * Turret hits clear a mob's damage cooldown (fast weapons would lose most hits), so every bullet was a fresh hit and a
     * fresh groan: twelve a second from one Machine Gun (user 10-10, "zombie hit sound too loud"). Hurt sounds from turret
     * damage now come once per mob per turretHurtSoundInterval (10 ticks); the damage itself and the death sound stay.
     */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void turretHitsGroanOncePerInterval(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(2, 2, 2));
        List<SoundEvent> heard = new ArrayList<>();
        Consumer<PlayLevelSoundEvent.AtPosition> listener = event -> {
            if (event.getSound() != null && Vec3.atLowerCornerOf(BlockPos.containing(event.getPosition())).distanceTo(zombie.position()) < 3) {
                heard.add(event.getSound().value());
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, PlayLevelSoundEvent.AtPosition.class, listener);
        float before = zombie.getHealth();
        for (int i = 0; i < 5; i++) Hitscan.damage(zombie, level, 1);
        long burst = heard.stream().filter(sound -> sound == SoundEvents.ZOMBIE_HURT).count();
        boolean hurtFive = before - zombie.getHealth() > 4.5f; // each hit landed (2 armour points take 6% off each)
        helper.runAtTickTime(12, () -> Hitscan.damage(zombie, level, 1));
        helper.runAtTickTime(14, () -> {
            Hitscan.damage(zombie, level, 1000);
            MinecraftForge.EVENT_BUS.unregister(listener);
            long hurt = heard.stream().filter(sound -> sound == SoundEvents.ZOMBIE_HURT).count();
            helper.assertTrue(hurtFive, "five turret hits in one tick did not all deal damage");
            helper.assertTrue(burst == 1, "five turret hits in one tick groaned " + burst + " times");
            helper.assertTrue(hurt == 2, "a hit 12 ticks later should groan again: " + hurt + " hurt sounds in all");
            helper.assertTrue(heard.contains(SoundEvents.ZOMBIE_DEATH), "the death sound went missing");
            helper.succeed();
        });
    }
}
