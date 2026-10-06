package dev.bastion.gametest;

import dev.bastion.Bastion;
import dev.bastion.config.BastionConfig;
import dev.bastion.registry.BastionBlocks;
import dev.bastion.registry.BastionItems;
import dev.bastion.turret.TurretBaseBlock;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.TurretHitboxEntity;
import dev.bastion.turret.TurretInventory;
import dev.bastion.turret.TurretState;
import dev.bastion.turret.TurretTier;
import dev.bastion.turret.targeting.TargetFilter.Rule;
import dev.bastion.weapon.StatSheet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Fase 3-7 acceptance checks (PLAN 8): Machine Gun and Shotgun, modifiers, the target filter, redstone,
 * HP, the hitbox and destruction. Same arena rules as {@link TurretCombatTests}.
 */
@GameTestHolder(Bastion.MOD_ID)
@PrefixGameTestTemplate(false)
public class TurretSystemsTests {
    private static final String ARENA = "arena";
    private static final BlockPos BASE = new BlockPos(2, 2, 3);
    private static final BlockPos MID = new BlockPos(6, 2, 3);
    private static final BlockPos FAR = new BlockPos(9, 2, 3);

    private static TurretBaseBlockEntity turret(GameTestHelper helper, Item weapon, Item ammo, int count) {
        helper.setBlock(BASE, BastionBlocks.TURRET_BASE.get());
        TurretBaseBlockEntity turret = (TurretBaseBlockEntity) helper.getBlockEntity(BASE);
        turret.inventory().setStackInSlot(TurretInventory.WEAPON, new ItemStack(weapon));
        if (count > 0) turret.inventory().setStackInSlot(TurretInventory.AMMO_START, new ItemStack(ammo, count));
        return turret;
    }

    private static TurretBaseBlockEntity gun(GameTestHelper helper) {
        return turret(helper, BastionItems.GUN_TURRET.get(), BastionItems.KINETIC_ROUNDS.get(), 10);
    }

    private static void disarm(TurretBaseBlockEntity turret) {
        turret.inventory().setStackInSlot(TurretInventory.WEAPON, ItemStack.EMPTY);
    }

    private static boolean untouched(LivingEntity entity) {
        return entity.getHealth() == entity.getMaxHealth();
    }

    // --- weapons ------------------------------------------------------------------------------

    @GameTest(template = ARENA, timeoutTicks = 200, batch = "machineGunKills")
    public static void machineGunKills(GameTestHelper helper) {
        TurretBaseBlockEntity turret = turret(helper, BastionItems.MACHINE_GUN_TURRET.get(), BastionItems.KINETIC_ROUNDS.get(), 64);
        LivingEntity husk = helper.spawnWithNoFreeWill(EntityType.HUSK, FAR);
        helper.succeedWhen(() -> {
            helper.assertTrue(!husk.isAlive(), "husk still alive, state " + turret.state());
            disarm(turret);
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 200, batch = "shotgunHits")
    public static void shotgunHits(GameTestHelper helper) {
        TurretBaseBlockEntity turret = turret(helper, BastionItems.SHOTGUN_TURRET.get(), BastionItems.SCATTER_SHELLS.get(), 8);
        LivingEntity husk = helper.spawnWithNoFreeWill(EntityType.HUSK, MID);
        helper.succeedWhen(() -> {
            helper.assertTrue(!untouched(husk), "shotgun never hit, state " + turret.state());
            helper.assertTrue(turret.inventory().getStackInSlot(TurretInventory.AMMO_START).getCount() < 8, "no shell spent");
            disarm(turret);
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 200, batch = "creativeAmmoNeverRunsOut")
    public static void creativeAmmoNeverRunsOut(GameTestHelper helper) {
        // Shotgun, so the scatter-shell weapon proves Creative Ammo is not tied to one ammo tag.
        TurretBaseBlockEntity turret = turret(helper, BastionItems.SHOTGUN_TURRET.get(), BastionItems.CREATIVE_AMMO.get(), 1);
        LivingEntity husk = helper.spawnWithNoFreeWill(EntityType.HUSK, MID);
        helper.succeedWhen(() -> {
            helper.assertTrue(!husk.isAlive(), "husk still alive, state " + turret.state());
            ItemStack ammo = turret.inventory().getStackInSlot(TurretInventory.AMMO_START);
            helper.assertTrue(ammo.is(BastionItems.CREATIVE_AMMO.get()) && ammo.getCount() == 1, "creative ammo was used up: " + ammo);
            disarm(turret);
        });
    }

    // --- mounting: floor, wall, ceiling ------------------------------------------------------------

    @GameTest(template = ARENA, batch = "mountFrameMatchesFacing")
    public static void mountFrameMatchesFacing(GameTestHelper helper) {
        for (Direction facing : Direction.values()) {
            helper.setBlock(BASE, BastionBlocks.TURRET_BASE.get().defaultBlockState().setValue(TurretBaseBlock.FACING, facing));
            TurretBaseBlockEntity turret = (TurretBaseBlockEntity) helper.getBlockEntity(BASE);
            Vec3 up = turret.toWorld(new Vec3(0, 1, 0));
            helper.assertTrue(up.distanceTo(Vec3.atLowerCornerOf(facing.getNormal())) < 1e-4, facing + ": mount axis points to " + up);
            Vec3 v = new Vec3(0.3, -0.7, 0.2);
            helper.assertTrue(turret.toLocal(turret.toWorld(v)).distanceTo(v) < 1e-4, facing + ": toLocal does not undo toWorld");
            Vec3 face = Vec3.atCenterOf(helper.absolutePos(BASE)).subtract(Vec3.atLowerCornerOf(facing.getNormal()).scale(0.5));
            helper.assertTrue(turret.alongMount(0).distanceTo(face) < 1e-4, facing + ": mount does not start on the attached face");
        }
        helper.succeed();
    }

    @GameTest(template = ARENA, timeoutTicks = 200, batch = "wallTurretKills")
    public static void wallTurretKills(GameTestHelper helper) {
        BlockPos wall = new BlockPos(1, 4, 3);
        helper.setBlock(wall.west(), Blocks.STONE);
        TurretBaseBlockEntity turret = mounted(helper, wall, Direction.EAST);
        LivingEntity husk = helper.spawnWithNoFreeWill(EntityType.HUSK, FAR);
        helper.succeedWhen(() -> {
            helper.assertTrue(!husk.isAlive(), "wall turret never killed the husk, state " + turret.state());
            disarm(turret);
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 200, batch = "ceilingTurretKills")
    public static void ceilingTurretKills(GameTestHelper helper) {
        BlockPos ceiling = new BlockPos(5, 6, 3);
        helper.setBlock(ceiling.above(), Blocks.STONE);
        TurretBaseBlockEntity turret = mounted(helper, ceiling, Direction.DOWN);
        LivingEntity husk = helper.spawnWithNoFreeWill(EntityType.HUSK, FAR);
        helper.succeedWhen(() -> {
            helper.assertTrue(!husk.isAlive(), "ceiling turret never killed the husk, state " + turret.state());
            disarm(turret);
        });
    }

    private static TurretBaseBlockEntity mounted(GameTestHelper helper, BlockPos pos, Direction facing) {
        helper.setBlock(pos, BastionBlocks.TURRET_BASE.get().defaultBlockState().setValue(TurretBaseBlock.FACING, facing));
        TurretBaseBlockEntity turret = (TurretBaseBlockEntity) helper.getBlockEntity(pos);
        turret.inventory().setStackInSlot(TurretInventory.WEAPON, new ItemStack(BastionItems.GUN_TURRET.get()));
        turret.inventory().setStackInSlot(TurretInventory.AMMO_START, new ItemStack(BastionItems.KINETIC_ROUNDS.get(), 10));
        return turret;
    }

    // --- Sniper & Rocket Launcher ------------------------------------------------------------------

    @GameTest(template = ARENA, timeoutTicks = 200, batch = "sniperPierces")
    public static void sniperPierces(GameTestHelper helper) {
        TurretBaseBlockEntity turret = turret(helper, BastionItems.SNIPER_TURRET.get(), BastionItems.SNIPER_ROUNDS.get(), 10);
        LivingEntity front = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(7, 2, 3));
        LivingEntity back = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(9, 2, 3));
        helper.succeedWhen(() -> {
            helper.assertTrue(!front.isAlive() && !back.isAlive(), "husks alive, state " + turret.state());
            int used = 10 - turret.inventory().getStackInSlot(TurretInventory.AMMO_START).getCount();
            helper.assertTrue(used == 1, "expected one round through both husks, used " + used);
            disarm(turret);
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 240, batch = "rocketKills")
    public static void rocketKills(GameTestHelper helper) {
        TurretBaseBlockEntity turret = turret(helper, BastionItems.ROCKET_LAUNCHER_TURRET.get(), BastionItems.ROCKETS.get(), 8);
        LivingEntity husk = helper.spawnWithNoFreeWill(EntityType.HUSK, FAR);
        helper.succeedWhen(() -> {
            helper.assertTrue(!husk.isAlive(), "husk alive, state " + turret.state());
            disarm(turret);
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 240, batch = "rocketSparesNonTargets")
    public static void rocketSparesNonTargets(GameTestHelper helper) {
        TurretBaseBlockEntity turret = turret(helper, BastionItems.ROCKET_LAUNCHER_TURRET.get(), BastionItems.ROCKETS.get(), 8);
        LivingEntity husk = helper.spawnWithNoFreeWill(EntityType.HUSK, FAR);
        LivingEntity cow = helper.spawnWithNoFreeWill(EntityType.COW, FAR.south());
        helper.succeedWhen(() -> {
            helper.assertTrue(!husk.isAlive(), "husk alive, state " + turret.state());
            helper.assertTrue(untouched(cow), "the blast hurt a cow the turret may not target: " + cow.getHealth());
            disarm(turret);
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 120, batch = "rocketMinRange")
    public static void rocketMinRange(GameTestHelper helper) {
        TurretBaseBlockEntity turret = turret(helper, BastionItems.ROCKET_LAUNCHER_TURRET.get(), BastionItems.ROCKETS.get(), 8);
        LivingEntity husk = helper.spawnWithNoFreeWill(EntityType.HUSK, BASE.east(3));
        helper.runAtTickTime(80, () -> {
            helper.assertTrue(untouched(husk), "fired at a target inside min_range");
            helper.assertTrue(turret.inventory().getStackInSlot(TurretInventory.AMMO_START).getCount() == 8, "spent a rocket inside min_range");
            disarm(turret);
            helper.succeed();
        });
    }

    // --- modifiers ----------------------------------------------------------------------------

    @GameTest(template = ARENA, batch = "modifiersApply")
    public static void modifiersApply(GameTestHelper helper) {
        TurretBaseBlockEntity turret = gun(helper);
        float baseRange = StatSheet.of(turret.inventory().weaponData(), TurretTier.T1).range();
        turret.inventory().setStackInSlot(TurretInventory.MODIFIER_START, new ItemStack(BastionItems.RANGE_MODULE.get()));
        float range = StatSheet.of(turret.inventory().weaponData(), TurretTier.T1, turret.inventory().modifierEffect()).range();
        helper.assertTrue(Math.abs(range - baseRange * 1.25f) < 1e-3, "Range Module: expected " + baseRange * 1.25f + ", got " + range);

        turret.inventory().setStackInSlot(TurretInventory.MODIFIER_START, new ItemStack(BastionItems.OVERCLOCK.get()));
        float expected = BastionConfig.maxHealth(TurretTier.T1) * 0.85f;
        helper.assertTrue(Math.abs(turret.maxHealth() - expected) < 1e-3, "Overclock: max HP " + turret.maxHealth() + ", expected " + expected);
        disarm(turret);
        helper.succeed();
    }

    /** Choke Module: Shotgun only, 40% tighter spread; refused next to another weapon, idle after a weapon swap. */
    @GameTest(template = ARENA, batch = "chokeFitsShotgunOnly")
    public static void chokeFitsShotgunOnly(GameTestHelper helper) {
        TurretBaseBlockEntity turret = gun(helper);
        TurretInventory inventory = turret.inventory();
        ItemStack choke = new ItemStack(BastionItems.CHOKE_MODULE.get());
        helper.assertTrue(!inventory.isItemValid(TurretInventory.MODIFIER_START, choke), "Choke Module accepted next to a Gun");

        inventory.setStackInSlot(TurretInventory.WEAPON, new ItemStack(BastionItems.SHOTGUN_TURRET.get()));
        helper.assertTrue(inventory.isItemValid(TurretInventory.MODIFIER_START, choke), "Choke Module refused next to a Shotgun");
        float base = StatSheet.of(inventory.weaponData(), TurretTier.T1).spread();
        inventory.setStackInSlot(TurretInventory.MODIFIER_START, choke);
        float choked = StatSheet.of(inventory.weaponData(), TurretTier.T1, inventory.modifierEffect()).spread();
        helper.assertTrue(Math.abs(choked - base * 0.6f) < 1e-3, "spread " + choked + ", expected " + base * 0.6f);

        inventory.setStackInSlot(TurretInventory.WEAPON, new ItemStack(BastionItems.GUN_TURRET.get()));
        helper.assertTrue(inventory.modifierEffect().spread() == 0 && inventory.inactiveModifier(TurretInventory.MODIFIER_START),
                "the Choke Module still works on a Gun");
        disarm(turret);
        helper.succeed();
    }

    /** Creative tabs: bases (+ tools), weapon modules, ammo, modules, workstations & parts apart; every Bastion item in exactly one of them. */
    @GameTest(template = ARENA, batch = "creativeTabsSortItems")
    public static void creativeTabsSortItems(GameTestHelper helper) {
        var params = new CreativeModeTab.ItemDisplayParameters(helper.getLevel().enabledFeatures(), true, helper.getLevel().registryAccess());
        List<CreativeModeTab> tabs = List.of(BastionItems.TAB.get(), BastionItems.WEAPONS_TAB.get(), BastionItems.AMMO_TAB.get(), BastionItems.MODULES_TAB.get(),
                BastionItems.WORKSHOP_TAB.get());
        tabs.forEach(tab -> tab.buildContents(params));
        for (var entry : BastionItems.REGISTER.getEntries()) {
            long in = tabs.stream().filter(tab -> tab.contains(new ItemStack(entry.get()))).count();
            helper.assertTrue(in == 1, entry.getId() + " is in " + in + " tabs");
        }
        helper.assertTrue(BastionItems.TAB.get().contains(new ItemStack(BastionItems.REPAIR_KIT.get())), "repair kit not with the bases");
        helper.assertTrue(BastionItems.WEAPONS_TAB.get().contains(new ItemStack(BastionItems.TESLA_TURRET.get())), "Tesla not with the weapons");
        helper.assertTrue(BastionItems.AMMO_TAB.get().contains(new ItemStack(BastionItems.CREATIVE_AMMO.get())), "Creative Ammo not with the ammo");
        helper.assertTrue(BastionItems.MODULES_TAB.get().contains(new ItemStack(BastionItems.CHOKE_MODULE.get())), "Choke not with the modules");
        helper.assertTrue(BastionItems.WORKSHOP_TAB.get().contains(new ItemStack(BastionItems.PART_ASSEMBLER.get())), "Part Assembler not in the workshop tab");
        helper.succeed();
    }

    // --- target filter ------------------------------------------------------------------------

    @GameTest(template = ARENA, timeoutTicks = 200, batch = "alwaysRuleTargetsPassive")
    public static void alwaysRuleTargetsPassive(GameTestHelper helper) {
        TurretBaseBlockEntity turret = gun(helper);
        turret.filter().rules.put(new ResourceLocation("cow"), Rule.ALWAYS);
        LivingEntity cow = helper.spawnWithNoFreeWill(EntityType.COW, FAR);
        helper.succeedWhen(() -> {
            helper.assertTrue(!untouched(cow), "the always-rule cow was never shot, state " + turret.state());
            disarm(turret);
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 200, batch = "neverRuleSparesHostile")
    public static void neverRuleSparesHostile(GameTestHelper helper) {
        TurretBaseBlockEntity turret = gun(helper);
        turret.filter().rules.put(new ResourceLocation("husk"), Rule.NEVER);
        LivingEntity husk = helper.spawnWithNoFreeWill(EntityType.HUSK, FAR);
        helper.runAtTickTime(80, () -> {
            helper.assertTrue(untouched(husk), "shot a never-rule husk");
            helper.assertTrue(turret.state() == TurretState.IDLE, "expected IDLE, got " + turret.state());
            disarm(turret);
            helper.succeed();
        });
    }

    // --- on/off -------------------------------------------------------------------------------

    @GameTest(template = ARENA, timeoutTicks = 200, batch = "redstoneDisables")
    public static void redstoneDisables(GameTestHelper helper) {
        TurretBaseBlockEntity turret = gun(helper);
        helper.setBlock(BASE.north(), Blocks.REDSTONE_BLOCK);
        LivingEntity husk = helper.spawnWithNoFreeWill(EntityType.HUSK, FAR);
        helper.runAtTickTime(80, () -> {
            helper.assertTrue(untouched(husk), "fired while powered");
            helper.assertTrue(turret.state() == TurretState.DISABLED, "expected DISABLED, got " + turret.state());
            // Inverted: the same signal now switches it on.
            turret.setRedstoneInverted(true);
        });
        helper.runAtTickTime(180, () -> {
            helper.assertTrue(!untouched(husk), "inverted redstone did not enable the turret, state " + turret.state());
            disarm(turret);
            helper.succeed();
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 200, batch = "switchedOff")
    public static void switchedOff(GameTestHelper helper) {
        TurretBaseBlockEntity turret = gun(helper);
        turret.setEnabled(false);
        LivingEntity husk = helper.spawnWithNoFreeWill(EntityType.HUSK, FAR);
        helper.runAtTickTime(80, () -> {
            helper.assertTrue(untouched(husk), "fired while switched off");
            helper.assertTrue(turret.state() == TurretState.DISABLED, "expected DISABLED, got " + turret.state());
            disarm(turret);
            helper.succeed();
        });
    }

    // --- health -------------------------------------------------------------------------------

    @GameTest(template = ARENA, timeoutTicks = 100, batch = "hitboxForwardsDamage")
    public static void hitboxForwardsDamage(GameTestHelper helper) {
        helper.setBlock(BASE, BastionBlocks.TURRET_BASE.get());
        TurretBaseBlockEntity turret = (TurretBaseBlockEntity) helper.getBlockEntity(BASE);
        helper.succeedWhen(() -> {
            List<TurretHitboxEntity> hitboxes = helper.getLevel().getEntitiesOfClass(TurretHitboxEntity.class,
                    new AABB(helper.absolutePos(BASE)).inflate(1));
            helper.assertTrue(hitboxes.size() == 1, "expected one hitbox, found " + hitboxes.size());
            float before = turret.health();
            hitboxes.get(0).hurt(helper.getLevel().damageSources().generic(), 10);
            helper.assertTrue(turret.health() == before, "environmental damage must not reach the turret");
            hitboxes.get(0).hurt(helper.getLevel().damageSources().explosion(null, null), 10);
            helper.assertTrue(Math.abs(turret.health() - (before - 10)) < 1e-3, "hitbox did not forward damage: " + turret.health());
        });
    }

    /**
     * Hostile mobs seek out turrets (TurretAggro). Armed, so the base is solid up to the weapon, but without ammo,
     * so the turret cannot kill the zombie first.
     */
    @GameTest(template = ARENA, timeoutTicks = 400, batch = "zombieAttacksTurret")
    public static void zombieAttacksTurret(GameTestHelper helper) {
        TurretBaseBlockEntity turret = turret(helper, BastionItems.GUN_TURRET.get(), BastionItems.KINETIC_ROUNDS.get(), 0);
        helper.spawn(EntityType.ZOMBIE, MID);
        helper.succeedWhen(() -> helper.assertTrue(turret.health() < turret.maxHealth(), "zombie never hit the turret"));
    }

    @GameTest(template = ARENA, batch = "repairAndUpgrade")
    public static void repairAndUpgrade(GameTestHelper helper) {
        helper.setBlock(BASE, BastionBlocks.TURRET_BASE.get());
        TurretBaseBlockEntity turret = (TurretBaseBlockEntity) helper.getBlockEntity(BASE);
        turret.takeDamage(helper.getLevel().damageSources().generic(), 50);
        turret.repair(20);
        helper.assertTrue(Math.abs(turret.health() - (turret.maxHealth() - 30)) < 1e-3, "repair: HP " + turret.health());
        helper.assertTrue(!turret.upgradeTo(TurretTier.T3), "a T1 base must not jump to T3");
        helper.assertTrue(turret.upgradeTo(TurretTier.T2), "T1 -> T2 upgrade refused");
        helper.assertTrue(turret.tier() == TurretTier.T2 && turret.health() == turret.maxHealth(), "upgrade must give full T2 HP");
        helper.succeed();
    }

    @GameTest(template = ARENA, timeoutTicks = 60, batch = "destroyedT1DropsDamagedBase")
    public static void destroyedT1DropsDamagedBase(GameTestHelper helper) {
        TurretBaseBlockEntity turret = gun(helper);
        turret.takeDamage(helper.getLevel().damageSources().generic(), 10_000);
        helper.assertBlockPresent(Blocks.AIR, BASE);
        helper.assertItemEntityPresent(BastionItems.DAMAGED_TURRET_BASE.get(), BASE, 2);
        helper.assertItemEntityPresent(BastionItems.GUN_TURRET.get(), BASE, 2);
        helper.assertItemEntityPresent(BastionItems.KINETIC_ROUNDS.get(), BASE, 2);
        helper.succeed();
    }

    @GameTest(template = ARENA, timeoutTicks = 60, batch = "destroyedT3DropsT2Base")
    public static void destroyedT3DropsT2Base(GameTestHelper helper) {
        helper.setBlock(BASE, BastionBlocks.TURRET_BASE.get());
        TurretBaseBlockEntity turret = (TurretBaseBlockEntity) helper.getBlockEntity(BASE);
        turret.upgradeTo(TurretTier.T2);
        turret.upgradeTo(TurretTier.T3);
        turret.takeDamage(helper.getLevel().damageSources().generic(), 10_000);
        List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(BASE)).inflate(2),
                e -> e.getItem().is(BastionItems.TURRET_BASE.get()));
        helper.assertTrue(drops.size() == 1, "expected one turret base drop, found " + drops.size());
        String tier = drops.get(0).getItem().getOrCreateTagElement("BlockEntityTag").getString("Tier");
        helper.assertTrue(tier.equals("T2"), "a destroyed T3 must drop a T2 base, got '" + tier + "'");
        helper.succeed();
    }
}
