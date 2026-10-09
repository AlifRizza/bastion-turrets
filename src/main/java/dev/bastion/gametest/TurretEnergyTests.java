package dev.bastion.gametest;

import dev.bastion.Bastion;
import dev.bastion.damage.BastionDamageTypes;
import dev.bastion.network.IonField;
import dev.bastion.registry.BastionBlocks;
import dev.bastion.registry.BastionItems;
import dev.bastion.turret.LargeTurretBaseBlock;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.TurretInventory;
import dev.bastion.turret.TurretState;
import dev.bastion.weapon.Bullets;
import dev.bastion.weapon.Splash;
import dev.bastion.weapon.types.IonRifleWeapon;
import dev.bastion.weapon.types.PlasmaCannonWeapon;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.IntStream;

/** Ion Rifle, Plasma Repeater, Plasma Cannon and their shared pieces (PLAN "Ion Rifle, Plasma Repeater & Plasma Cannon"). */
@GameTestHolder(Bastion.MOD_ID)
@PrefixGameTestTemplate(false)
public class TurretEnergyTests {
    private static final String ARENA = "arena", RANGE = "range";
    private static final Predicate<Entity> HUSKS = e -> e instanceof Husk;

    /** Health lost so far (the mobs here never die mid-test). */
    static float lost(LivingEntity entity) {
        return entity.getMaxHealth() - entity.getHealth();
    }

    /**
     * Who a burst reaches: a box within the radius by its nearest edge (not its centre), one behind a wall too (no line of
     * sight), never the excluded one, never one the predicate refuses (the cow), nearest first.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "splashReachesBoxesInRadius")
    public static void splashReachesBoxesInRadius(GameTestHelper helper) {
        LivingEntity struck = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(6, 2, 3));
        LivingEntity near = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(8, 2, 3));   // box edge 1.7 from the point
        LivingEntity far = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(10, 2, 3));   // 3.7
        for (int x = 5; x <= 7; x++) for (int y = 1; y <= 3; y++) helper.setBlock(new BlockPos(x, y, 2), Blocks.STONE);
        LivingEntity walled = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(6, 2, 1)); // 1.7, behind the wall
        LivingEntity cow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(6, 2, 5));
        helper.runAtTickTime(20, () -> {
            Vec3 at = helper.absoluteVec(new Vec3(6.5, 1.5, 3.5));
            List<LivingEntity> got = Splash.around(helper.getLevel(), at, 3, HUSKS, struck);
            helper.assertTrue(got.contains(near) && got.contains(walled), "missed the near or the walled husk: " + got);
            helper.assertTrue(!got.contains(far) && !got.contains(struck) && !got.contains(cow), "reached too much: " + got);
            helper.assertTrue(got.size() == 2, "expected exactly two, got " + got);
            helper.succeed();
        });
    }

    /**
     * Plasma Cannon fragments are bullets launched from inside another bullet's hit, while the level steps its bullets: they
     * must fly and hit (no ConcurrentModificationException), and only what the child's predicate allows.
     */
    @GameTest(template = ARENA, timeoutTicks = 80, batch = "bulletsLaunchedFromAHitFly")
    public static void bulletsLaunchedFromAHitFly(GameTestHelper helper) {
        LivingEntity first = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(8, 2, 3));
        LivingEntity second = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(8, 2, 1));
        var level = helper.getLevel();
        Bullets.Hit child = (bullet, hit) -> hit.getEntity().hurt(level.damageSources().generic(), 3);
        Bullets.Hit parent = (bullet, hit) -> {
            Entity struck = hit.getEntity();
            struck.hurt(level.damageSources().generic(), 3);
            // From the struck husk's middle (its box edge would graze the second husk's), which the child may not hit.
            Bullets.launch(Bullets.create(level, List.of(struck.position().add(0, 1, 0)), List.of(new Vec3(0, 0, -1)), 1, 6, 0,
                    HUSKS.and(e -> e != struck), child));
        };
        // 1 block per tick from 6 blocks out: the parent hits during a later tick's step, not in launch().
        helper.runAtTickTime(20, () -> Bullets.launch(Bullets.create(level, List.of(helper.absoluteVec(new Vec3(2.5, 2, 3.5))),
                List.of(new Vec3(1, 0, 0)), 1, 10, 0, HUSKS, parent)));
        helper.succeedWhen(() -> {
            helper.assertTrue(lost(first) > 0, "the parent bullet never hit");
            helper.assertTrue(lost(second) > 0, "the bullet launched from the hit never hit");
            helper.assertTrue(lost(first) < 4, "the child hit the parent's target too");
        });
    }

    private static TurretBaseBlockEntity mount(GameTestHelper helper, BlockPos pos, Item weapon) {
        helper.setBlock(pos, BastionBlocks.TURRET_BASE.get());
        TurretBaseBlockEntity base = (TurretBaseBlockEntity) helper.getBlockEntity(pos);
        base.inventory().setStackInSlot(TurretInventory.WEAPON, new ItemStack(weapon));
        return base;
    }

    private static IEnergyStorage energy(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockEntity(pos).getCapability(ForgeCapabilities.ENERGY, Direction.UP).orElseThrow(IllegalStateException::new);
    }

    /** Feeds exactly {@code fe} (several transfers: max_input caps each one). */
    private static void charge(GameTestHelper helper, BlockPos pos, int fe) {
        IEnergyStorage energy = energy(helper, pos);
        for (int left = fe, got; left > 0 && (got = energy.receiveEnergy(left, false)) > 0; ) left -= got;
    }

    private static final BlockPos ION = new BlockPos(2, 2, 5);

    /**
     * FE for exactly one shot: the husk struck loses 8 (not 8 + 5), the two within 3 blocks of the impact lose 5 each, the one
     * 3.7 blocks out and the cow beside the impact nothing; then the turret waits without FE.
     */
    @GameTest(template = RANGE, timeoutTicks = 160, batch = "ionZapsEveryTargetAround")
    public static void ionZapsEveryTargetAround(GameTestHelper helper) {
        TurretBaseBlockEntity turret = mount(helper, ION, BastionItems.ION_RIFLE_TURRET.get());
        charge(helper, ION, 1200);
        LivingEntity struck = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(9, 2, 5)); // nearest: targeted
        LivingEntity behind = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(11, 2, 5));
        LivingEntity aside = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(9, 2, 7));
        LivingEntity far = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(9, 2, 9));
        LivingEntity cow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(10, 2, 4));
        helper.succeedWhen(() -> {
            helper.assertTrue(lost(struck) >= 7.5f && lost(struck) <= 8.05f, "struck husk lost " + lost(struck) + ", state " + turret.state());
            helper.assertTrue(lost(behind) >= 4.5f && lost(behind) <= 5.05f && lost(aside) >= 4.5f && lost(aside) <= 5.05f,
                    "field husks lost " + lost(behind) + " and " + lost(aside));
            helper.assertTrue(lost(far) == 0 && lost(cow) == 0, "reached too far: husk " + lost(far) + ", cow " + lost(cow));
            helper.assertTrue(turret.state() == TurretState.NO_AMMO, "expected NO_AMMO after the one shot, got " + turret.state());
        });
    }

    /**
     * A bolt that stops at a wall still forms its core there: the husk beside the impact and the one behind the wall (no line
     * of sight) lose 5; the cow does not. Drives the weapon's own hit with a bare bullet, so the block path is certain.
     */
    @GameTest(template = ARENA, timeoutTicks = 80, batch = "ionZapsWhereTheBoltStops")
    public static void ionZapsWhereTheBoltStops(GameTestHelper helper) {
        for (int y = 1; y <= 4; y++) for (int z = 1; z <= 5; z++) helper.setBlock(new BlockPos(10, y, z), Blocks.STONE);
        LivingEntity beside = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(9, 2, 1));
        LivingEntity behind = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(11, 2, 3));
        LivingEntity cow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(9, 2, 5));
        var level = helper.getLevel();
        helper.runAtTickTime(20, () -> Bullets.launch(Bullets.create(level, List.of(helper.absoluteVec(new Vec3(2.5, 2, 3.5))),
                List.of(new Vec3(1, 0, 0)), 4, 20, 0, HUSKS, IonRifleWeapon.bolt(level, 8, new IonRifleWeapon.Field(3, 5, 0x8099FF, 6), HUSKS,
                        BastionDamageTypes.of(level, BastionDamageTypes.TURRET_SHOCK)))));
        helper.succeedWhen(() -> {
            helper.assertTrue(lost(beside) >= 4.5f && lost(behind) >= 4.5f, "zapped " + lost(beside) + " beside, " + lost(behind) + " behind the wall");
            helper.assertTrue(lost(cow) == 0, "the cow was zapped");
        });
    }

    /** Without FE: NO_AMMO; max_input 1,000 per transfer, capacity 40,000, 1,200 per shot. */
    @GameTest(template = RANGE, timeoutTicks = 160, batch = "ionRunsOnEnergy")
    public static void ionRunsOnEnergy(GameTestHelper helper) {
        TurretBaseBlockEntity turret = mount(helper, ION, BastionItems.ION_RIFLE_TURRET.get());
        LivingEntity husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(9, 2, 5));
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(turret.state() == TurretState.NO_AMMO, "expected NO_AMMO without FE, got " + turret.state());
            helper.assertTrue(energy(helper, ION).receiveEnergy(Integer.MAX_VALUE, false) == 1000, "max_input is not 1000");
            charge(helper, ION, Integer.MAX_VALUE);
            helper.assertTrue(turret.energy() == 40000, "capacity is not 40000: " + turret.energy());
        });
        helper.runAfterDelay(41, () -> helper.succeedWhen(() -> {
            helper.assertTrue(lost(husk) > 0, "never fired with FE");
            helper.assertTrue(turret.energy() == 38800, "a shot should cost 1200, capacitor at " + turret.energy());
        }));
    }

    /** A crowd of 100 in one field: the beam list on the wire is cut to 64 and decodes cleanly (the damage is not capped). */
    @GameTest(template = ARENA, batch = "ionFieldPacketIsCapped")
    public static void ionFieldPacketIsCapped(GameTestHelper helper) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        new IonField(7, Vec3.ZERO, 0x8099FF, 6, IntStream.range(0, 100).toArray()).encode(buf);
        IonField back = IonField.decode(buf);
        helper.assertTrue(back.targets().length == IonField.MAX_TARGETS && back.bullet() == 7 && back.ticks() == 6, "decoded " + back.targets().length);
        helper.succeed();
    }

    private static final BlockPos REPEATER = new BlockPos(2, 2, 5);

    private static LivingEntity tank(GameTestHelper helper, BlockPos pos) {
        LivingEntity husk = helper.spawnWithNoFreeWill(EntityType.HUSK, pos);
        husk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        husk.setHealth(1000);
        return husk;
    }

    /**
     * Each ball splashes: the husk 1 block behind the one struck loses less than it (3 vs 5 a shot), the husk 1.7 blocks
     * aside and the cow 0.7 blocks aside nothing.
     */
    @GameTest(template = RANGE, timeoutTicks = 160, batch = "repeaterSplashesAround")
    public static void repeaterSplashesAround(GameTestHelper helper) {
        TurretBaseBlockEntity turret = mount(helper, REPEATER, BastionItems.PLASMA_REPEATER_TURRET.get());
        turret.inventory().setStackInSlot(TurretInventory.AMMO_START, new ItemStack(BastionItems.PLASMA_CELL.get(), 3));
        LivingEntity struck = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(8, 2, 5));
        LivingEntity behind = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(9, 2, 5));
        LivingEntity aside = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(8, 2, 7));
        LivingEntity cow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(8, 2, 4));
        helper.succeedWhen(() -> {
            helper.assertTrue(lost(struck) > 0 && lost(behind) > 0, "struck " + lost(struck) + ", splashed " + lost(behind) + ", state " + turret.state());
            helper.assertTrue(lost(behind) < lost(struck), "the splash hit as hard as the ball");
            helper.assertTrue(lost(aside) == 0 && lost(cow) == 0, "splash too wide: husk " + lost(aside) + ", cow " + lost(cow));
        });
    }

    /** Heat: after 15-30 shots it overheats and holds fire through the vent (60 ticks). */
    @GameTest(template = RANGE, timeoutTicks = 400, batch = "repeaterOverheatsAndVents")
    public static void repeaterOverheatsAndVents(GameTestHelper helper) {
        TurretBaseBlockEntity turret = mount(helper, REPEATER, BastionItems.PLASMA_REPEATER_TURRET.get());
        turret.inventory().setStackInSlot(TurretInventory.AMMO_START, new ItemStack(BastionItems.CREATIVE_AMMO.get()));
        LivingEntity husk = tank(helper, new BlockPos(8, 2, 5));
        long[] hotAt = {-1};
        float[] atHot = {0}, settled = {0};
        helper.onEachTick(() -> {
            if (hotAt[0] < 0 && turret.state() == TurretState.OVERHEAT) {
                hotAt[0] = helper.getTick();
                atHot[0] = lost(husk);
            }
            if (hotAt[0] >= 0 && helper.getTick() == hotAt[0] + 10) settled[0] = lost(husk); // balls still in flight have landed
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(hotAt[0] >= 0, "never overheated, heat " + turret.heat());
            // +8 a shot, 4 shots/s, -15/s: ~23 shots (~113 damage at 4.92 a shot after the husk's armour).
            helper.assertTrue(atHot[0] >= 70 && atHot[0] <= 150, "overheated after ~" + atHot[0] / 4.92f + " shots, expected 15-30");
            helper.assertTrue(helper.getTick() >= hotAt[0] + 50, "venting");
            helper.assertTrue(lost(husk) == settled[0], "fired while venting");
        });
    }

    /** One Plasma Cell = 10 shots: the lone husk loses ~49 and the turret runs dry. */
    @GameTest(template = RANGE, timeoutTicks = 200, batch = "repeaterTenShotsPerCell")
    public static void repeaterTenShotsPerCell(GameTestHelper helper) {
        TurretBaseBlockEntity turret = mount(helper, REPEATER, BastionItems.PLASMA_REPEATER_TURRET.get());
        turret.inventory().setStackInSlot(TurretInventory.AMMO_START, new ItemStack(BastionItems.PLASMA_CELL.get()));
        LivingEntity husk = tank(helper, new BlockPos(8, 2, 5));
        helper.succeedWhen(() -> {
            helper.assertTrue(turret.state() == TurretState.NO_AMMO, "still firing, state " + turret.state());
            helper.assertTrue(lost(husk) >= 45 && lost(husk) <= 50.5f, "10 shots should take ~49, took " + lost(husk));
        });
    }

    /** Lowest corner of the Cannon's 2x2 base in the range; its middle is at (3, _, 5). */
    private static final BlockPos CANNON = new BlockPos(2, 2, 4);

    private static TurretBaseBlockEntity cannon(GameTestHelper helper, int cells) {
        LargeTurretBaseBlock.placeAt(helper.getLevel(), helper.absolutePos(CANNON), BastionBlocks.LARGE_TURRET_BASE.get());
        TurretBaseBlockEntity base = (TurretBaseBlockEntity) helper.getBlockEntity(CANNON);
        base.inventory().setStackInSlot(TurretInventory.WEAPON, new ItemStack(BastionItems.PLASMA_CANNON_TURRET.get()));
        if (cells > 0) base.inventory().setStackInSlot(TurretInventory.AMMO_START, new ItemStack(BastionItems.PLASMA_CELL.get(), cells));
        return base;
    }

    /**
     * Inside min_range 4 the Cannon never fires: a husk on a block ~3 blocks from the pivot (at its height, so the pitch limits
     * are not what stops it) keeps its health and the cell stays.
     */
    @GameTest(template = RANGE, timeoutTicks = 120, batch = "cannonKeepsItsMinimumRange")
    public static void cannonKeepsItsMinimumRange(GameTestHelper helper) {
        TurretBaseBlockEntity turret = cannon(helper, 1);
        helper.setBlock(new BlockPos(5, 2, 6), Blocks.STONE);
        LivingEntity close = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(5, 3, 6));
        helper.runAtTickTime(100, () -> {
            helper.assertTrue(lost(close) == 0, "fired inside the minimum range");
            helper.assertTrue(turret.inventory().getStackInSlot(TurretInventory.AMMO_START).getCount() == 1, "spent the cell");
            helper.succeed();
        });
    }

    /**
     * The Cannon is swapped for a Tesla Coil while its orb is in the air: the orb still lands and bursts (it read the mounted
     * weapon's min_range, which the Tesla has not, and threw inside the level tick: a server crash).
     */
    @GameTest(template = RANGE, timeoutTicks = 200, batch = "cannonSwappedMidFlight")
    public static void cannonSwappedMidFlight(GameTestHelper helper) {
        TurretBaseBlockEntity turret = cannon(helper, 1);
        LivingEntity far = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(20, 2, 5));
        boolean[] swapped = {false};
        helper.onEachTick(() -> {
            if (!swapped[0] && turret.inventory().getStackInSlot(TurretInventory.AMMO_START).isEmpty()) { // the cell just went: the orb is out
                swapped[0] = true;
                turret.inventory().setStackInSlot(TurretInventory.WEAPON, new ItemStack(BastionItems.TESLA_TURRET.get()));
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(swapped[0], "never fired");
            helper.assertTrue(lost(far) > 0, "the orb never landed");
        });
    }

    private static <E extends Mob> E tank(GameTestHelper helper, EntityType<E> type, BlockPos pos) {
        E mob = helper.spawnWithNoFreeWill(type, pos);
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        mob.setHealth(1000);
        return mob;
    }

    /**
     * One cell, one shot, BFG-style: the husk struck takes 18 + the blast's full 14; a husk 2 blocks behind it and a spider
     * (a short mob) 2.5 blocks aside take the blast, less than 14 (it falls off to 7 at 4 blocks); a husk 5 blocks out and
     * the cow inside the radius take nothing.
     */
    @GameTest(template = RANGE, timeoutTicks = 200, batch = "cannonBlastHitsEveryoneAround")
    public static void cannonBlastHitsEveryoneAround(GameTestHelper helper) {
        TurretBaseBlockEntity turret = cannon(helper, 1);
        LivingEntity struck = tank(helper, EntityType.HUSK, new BlockPos(12, 2, 5));
        LivingEntity behind = tank(helper, EntityType.HUSK, new BlockPos(14, 2, 5));
        LivingEntity spider = tank(helper, EntityType.SPIDER, new BlockPos(12, 2, 8));
        LivingEntity far = tank(helper, EntityType.HUSK, new BlockPos(12, 2, 11));
        LivingEntity cow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(10, 2, 6));
        helper.succeedWhen(() -> {
            helper.assertTrue(lost(struck) >= 30f && lost(struck) <= 32.1f, "struck husk lost " + lost(struck) + ", state " + turret.state());
            helper.assertTrue(lost(behind) >= 6.5f && lost(behind) < 14f, "husk behind lost " + lost(behind));
            helper.assertTrue(lost(spider) >= 6.5f && lost(spider) < 14f, "spider aside lost " + lost(spider));
            helper.assertTrue(lost(far) == 0 && lost(cow) == 0, "reached too far: husk " + lost(far) + ", cow " + lost(cow));
            helper.assertTrue(turret.state() == TurretState.NO_AMMO, "expected NO_AMMO after its one cell, got " + turret.state());
        });
    }

    /** An orb that hits a wall still explodes there: the husk beside the impact and the one behind the wall take the blast, the cow does not. */
    @GameTest(template = ARENA, timeoutTicks = 80, batch = "cannonBlastOnAWall")
    public static void cannonBlastOnAWall(GameTestHelper helper) {
        for (int y = 1; y <= 4; y++) for (int z = 1; z <= 5; z++) helper.setBlock(new BlockPos(10, y, z), Blocks.STONE);
        LivingEntity beside = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(9, 2, 1));
        LivingEntity behind = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(11, 2, 3));
        LivingEntity cow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(9, 2, 5));
        var level = helper.getLevel();
        helper.runAtTickTime(20, () -> Bullets.launch(Bullets.create(level, List.of(helper.absoluteVec(new Vec3(2.5, 2.5, 3.5))),
                List.of(new Vec3(1, 0, 0)), 0.8, 20, 0, HUSKS, PlasmaCannonWeapon.orb(level, 18, new PlasmaCannonWeapon.Blast(4, 14), HUSKS,
                        BastionDamageTypes.of(level, BastionDamageTypes.TURRET_PLASMA)))));
        helper.succeedWhen(() -> {
            helper.assertTrue(lost(beside) >= 6.5f && lost(behind) >= 6.5f, "blast took " + lost(beside) + " beside, " + lost(behind) + " behind the wall");
            helper.assertTrue(lost(cow) == 0, "the cow was hit");
        });
    }
}
