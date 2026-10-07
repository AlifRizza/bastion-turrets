package dev.bastion.gametest;

import dev.bastion.Bastion;
import dev.bastion.registry.BastionBlocks;
import dev.bastion.registry.BastionItems;
import dev.bastion.turret.LargeTurretBaseBlock;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.TurretInventory;
import dev.bastion.turret.TurretState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Railgun: slug + FE per shot, armor ignored, the biggest target first and bosses x2, a push-only shockwave. */
@GameTestHolder(Bastion.MOD_ID)
@PrefixGameTestTemplate(false)
public class TurretRailgunTests {
    private static final String RANGE = "range";
    private static final BlockPos LARGE = new BlockPos(2, 2, 5);
    private static final int CAPACITY = 80000, PER_SHOT = 8000, DAMAGE = 60;

    private static TurretBaseBlockEntity railgun(GameTestHelper helper, int slugs, boolean charged) {
        LargeTurretBaseBlock.placeAt(helper.getLevel(), helper.absolutePos(LARGE), BastionBlocks.LARGE_TURRET_BASE.get());
        TurretBaseBlockEntity turret = (TurretBaseBlockEntity) helper.getBlockEntity(LARGE);
        turret.inventory().setStackInSlot(TurretInventory.WEAPON, new ItemStack(BastionItems.RAILGUN_TURRET.get()));
        if (slugs > 0) turret.inventory().setStackInSlot(TurretInventory.AMMO_START, new ItemStack(BastionItems.RAIL_SLUGS.get(), slugs));
        if (charged) charge(helper);
        return turret;
    }

    private static void charge(GameTestHelper helper) {
        IEnergyStorage energy = helper.getBlockEntity(LARGE).getCapability(ForgeCapabilities.ENERGY, Direction.UP).orElseThrow(IllegalStateException::new);
        while (energy.receiveEnergy(Integer.MAX_VALUE, false) > 0) ;
    }

    private static void disarm(TurretBaseBlockEntity turret) {
        turret.inventory().setStackInSlot(TurretInventory.WEAPON, ItemStack.EMPTY);
    }

    private static boolean hurt(LivingEntity entity) {
        return entity.getHealth() < entity.getMaxHealth();
    }

    /** A husk that survives a slug, so the damage can be read off its health. */
    private static LivingEntity tough(GameTestHelper helper, BlockPos pos, float health) {
        LivingEntity husk = helper.spawnWithNoFreeWill(EntityType.HUSK, pos);
        husk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
        husk.setHealth(health);
        return husk;
    }

    /** Slugs but no FE: it waits (red lamp); fed FE, it fires, paying one slug and energy_per_shot. */
    @GameTest(template = RANGE, timeoutTicks = 300, batch = "railgunNeedsEnergy")
    public static void railgunNeedsEnergy(GameTestHelper helper) {
        TurretBaseBlockEntity turret = railgun(helper, 4, false);
        LivingEntity husk = tough(helper, new BlockPos(14, 2, 6), 200);
        helper.runAtTickTime(80, () -> {
            helper.assertTrue(turret.state() == TurretState.NO_AMMO, "expected NO_AMMO without FE, got " + turret.state());
            helper.assertTrue(!hurt(husk), "fired without FE");
            charge(helper);
        });
        helper.runAfterDelay(81, () -> helper.succeedWhen(() -> {
            helper.assertTrue(hurt(husk), "no shot yet, state " + turret.state());
            helper.assertTrue(turret.energy() == CAPACITY - PER_SHOT, "expected one shot's FE spent, energy " + turret.energy());
            helper.assertTrue(turret.inventory().getStackInSlot(TurretInventory.AMMO_START).getCount() == 3, "expected one slug spent");
            disarm(turret);
        }));
    }

    /** FE but no slugs: it never fires. */
    @GameTest(template = RANGE, timeoutTicks = 140, batch = "railgunNeedsSlugs")
    public static void railgunNeedsSlugs(GameTestHelper helper) {
        TurretBaseBlockEntity turret = railgun(helper, 0, true);
        LivingEntity husk = tough(helper, new BlockPos(14, 2, 6), 200);
        helper.runAtTickTime(120, () -> {
            helper.assertTrue(turret.state() == TurretState.NO_AMMO, "expected NO_AMMO without slugs, got " + turret.state());
            helper.assertTrue(!hurt(husk) && turret.energy() == CAPACITY, "fired without a slug");
            disarm(turret);
            helper.succeed();
        });
    }

    /** Full diamond armor takes nothing off: exactly 60 (it would be ~48 through the armor). */
    @GameTest(template = RANGE, timeoutTicks = 300, batch = "railgunIgnoresArmor")
    public static void railgunIgnoresArmor(GameTestHelper helper) {
        TurretBaseBlockEntity turret = railgun(helper, 4, true);
        LivingEntity husk = tough(helper, new BlockPos(14, 2, 6), 200);
        husk.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
        husk.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        husk.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
        husk.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
        helper.succeedWhen(() -> {
            helper.assertTrue(hurt(husk), "no shot yet, state " + turret.state());
            helper.assertTrue(turret.energy() == CAPACITY - PER_SHOT, "expected one shot, energy " + turret.energy());
            float taken = husk.getMaxHealth() - husk.getHealth();
            helper.assertTrue(Math.abs(taken - DAMAGE) < 0.01f, "armor reduced the slug: took " + taken);
            disarm(turret);
        });
    }

    /** The Wither (forge:bosses, most health) is shot first though a husk stands closer, and takes 60 x 2. */
    @GameTest(template = RANGE, timeoutTicks = 300, batch = "railgunPicksTheBoss")
    public static void railgunPicksTheBoss(GameTestHelper helper) {
        TurretBaseBlockEntity turret = railgun(helper, 4, true);
        LivingEntity husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(9, 2, 2));
        WitherBoss wither = helper.spawnWithNoFreeWill(EntityType.WITHER, new BlockPos(17, 2, 6));
        wither.setNoAi(true); // its heads steer it even without goals: it flew over the turret, inside min_range
        helper.succeedWhen(() -> {
            helper.assertTrue(turret.energy() == CAPACITY - PER_SHOT, "expected one shot, energy " + turret.energy());
            float taken = wither.getMaxHealth() - wither.getHealth();
            helper.assertTrue(Math.abs(taken - DAMAGE * 2) < 0.01f, "expected the Wither to take " + DAMAGE * 2 + ", took " + taken);
            helper.assertTrue(!hurt(husk), "shot the nearer husk");
            disarm(turret);
        });
    }

    /** The shockwave throws the valid husk next to the target away and leaves the cow beside it where it stood. */
    @GameTest(template = RANGE, timeoutTicks = 300, batch = "railgunShockwave")
    public static void railgunShockwave(GameTestHelper helper) {
        TurretBaseBlockEntity turret = railgun(helper, 4, true);
        tough(helper, new BlockPos(14, 2, 6), 200); // the biggest: the one it shoots
        LivingEntity bystander = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(14, 2, 8));
        LivingEntity cow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(14, 2, 4));
        Vec3[] start = new Vec3[2];
        helper.runAtTickTime(20, () -> { // landed by now (spawned a block up); the shot comes ~100 ticks in
            start[0] = bystander.position();
            start[1] = cow.position();
        });
        helper.runAfterDelay(21, () -> helper.succeedWhen(() -> {
            helper.assertTrue(turret.energy() < CAPACITY, "no shot yet, state " + turret.state());
            double pushed = bystander.position().distanceTo(start[0]), moved = cow.position().distanceTo(start[1]);
            helper.assertTrue(pushed > 0.8, "bystander husk not thrown: moved " + pushed);
            helper.assertTrue(moved < 0.2, "the cow was pushed: moved " + moved);
            helper.assertTrue(!hurt(bystander) && !hurt(cow), "the shockwave did damage");
            disarm(turret);
        }));
    }
}
