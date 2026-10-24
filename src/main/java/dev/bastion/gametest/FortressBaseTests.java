package dev.bastion.gametest;

import dev.bastion.Bastion;
import dev.bastion.compat.BlockInfo;
import dev.bastion.config.BastionConfig;
import dev.bastion.modifier.ModifierEffect;
import dev.bastion.registry.BastionBlocks;
import dev.bastion.registry.BastionItems;
import dev.bastion.turret.BaseKind;
import dev.bastion.turret.FeedHubBlockEntity;
import dev.bastion.turret.FortressTurretBaseBlock;
import dev.bastion.turret.Footprint;
import dev.bastion.turret.LargeTurretBaseBlock;
import dev.bastion.turret.TurretBaseBlock;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.TurretHitboxEntity;
import dev.bastion.turret.TurretInventory;
import dev.bastion.turret.TurretState;
import dev.bastion.turret.TurretTier;
import dev.bastion.turret.targeting.TargetFilter;
import dev.bastion.weapon.WeaponModuleItem;
import dev.bastion.weapon.WeaponStatRows;
import dev.bastion.workstation.WorkstationRecipe;
import dev.bastion.workstation.WorkstationType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fortress Turret Base (PLAN §8 "Fortress Turret Base 3x3", 0.5.0): one base over nine blocks with its core in the middle,
 * only huge (FORTRESS) weapon modules, HP x fortressBaseHealthMultiplier. Each test in its own batch.
 */
@GameTestHolder(Bastion.MOD_ID)
@PrefixGameTestTemplate(false)
public class FortressBaseTests {
    private static final String RANGE = "range";
    private static final BlockPos CORE = new BlockPos(5, 2, 5);

    private static TurretBaseBlockEntity fortress(GameTestHelper helper) {
        return fortress(helper, CORE);
    }

    private static TurretBaseBlockEntity fortress(GameTestHelper helper, BlockPos core) {
        FortressTurretBaseBlock.placeAt(helper.getLevel(), helper.absolutePos(core), BastionBlocks.FORTRESS_TURRET_BASE.get());
        return (TurretBaseBlockEntity) helper.getBlockEntity(core);
    }

    /** The nine absolute positions of a Fortress around the core at CORE. */
    private static List<BlockPos> cells(GameTestHelper helper) {
        return BlockPos.betweenClosedStream(helper.absolutePos(CORE).offset(-1, 0, -1), helper.absolutePos(CORE).offset(1, 0, 1))
                .map(BlockPos::immutable).toList();
    }

    private static List<ItemEntity> drops(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(CORE)).inflate(3));
    }

    private static String names(List<ItemEntity> drops) {
        return drops.stream().map(e -> e.getItem().toString()).toList().toString();
    }

    @GameTest(template = RANGE, batch = "fortressBaseIsOneBlock")
    public static void fortressBaseIsOneBlock(GameTestHelper helper) {
        drops(helper).forEach(ItemEntity::discard); // counted below
        TurretBaseBlockEntity turret = fortress(helper);
        for (BlockPos p : cells(helper)) {
            helper.assertTrue(helper.getLevel().getBlockState(p).is(BastionBlocks.FORTRESS_TURRET_BASE.get()), "no Fortress block at " + p);
            helper.assertTrue(TurretBaseBlock.core(helper.getLevel(), p) == turret, "cell " + p + " does not lead to the core");
        }
        helper.assertTrue(turret.mountCenter().equals(Vec3.atCenterOf(helper.absolutePos(CORE))),
                "turret does not turn around its centre block: " + turret.mountCenter());
        helper.getLevel().destroyBlock(helper.absolutePos(CORE.offset(1, 0, 1)), true); // an outer corner, not the core
        for (BlockPos p : cells(helper)) helper.assertTrue(helper.getLevel().getBlockState(p).isAir(), "cell " + p + " survived");
        helper.succeedWhen(() -> { // a drop in a chunk still loading its entities shows up a few ticks late
            List<ItemEntity> drops = drops(helper);
            helper.assertTrue(drops.size() == 1 && drops.get(0).getItem().is(BastionItems.FORTRESS_TURRET_BASE.get()),
                    "expected exactly one Fortress Turret Base drop, got " + names(drops));
        });
    }

    @GameTest(template = RANGE, batch = "fortressFootprintIsWhatGetsPlaced")
    public static void fortressFootprintIsWhatGetsPlaced(GameTestHelper helper) {
        FortressTurretBaseBlock block = BastionBlocks.FORTRESS_TURRET_BASE.get();
        BlockItem item = (BlockItem) BastionItems.FORTRESS_TURRET_BASE.get();
        helper.assertTrue(block.footprint(TurretPlacementTests.click(helper, item, 90, Direction.NORTH)) == null, "a Fortress offered a footprint on a side face");
        BlockPlaceContext context = TurretPlacementTests.click(helper, item, 90, Direction.UP);
        BlockPos clicked = context.getClickedPos();
        Footprint footprint = block.footprint(context);
        helper.assertTrue(footprint != null && footprint.placeable() && Set.copyOf(footprint.blocks()).size() == 9 && footprint.blocks().stream()
                        .allMatch(p -> p.getY() == clicked.getY() && Math.abs(p.getX() - clicked.getX()) <= 1 && Math.abs(p.getZ() - clicked.getZ()) <= 1),
                "footprint " + footprint + " is not the 3x3 around " + clicked);

        BlockPos corner = clicked.offset(1, 0, 1);
        helper.getLevel().setBlockAndUpdate(corner, Blocks.STONE.defaultBlockState());
        Footprint blocked = block.footprint(context);
        helper.assertTrue(blocked.blocked().equals(Set.of(corner)) && !blocked.placeable(), "blocked " + blocked.blocked() + ", expected " + corner);
        helper.assertTrue(!item.place(context).consumesAction(), "placed through a stone block");
        helper.getLevel().setBlockAndUpdate(corner, Blocks.AIR.defaultBlockState());

        Cow cow = EntityType.COW.create(helper.getLevel()); // absolute position: the same space as the footprint
        cow.moveTo(Vec3.atBottomCenterOf(clicked));
        cow.setNoAi(true);
        helper.getLevel().addFreshEntity(cow);
        helper.assertTrue(block.footprint(context).blocked().contains(clicked), "a cow in the centre cell is not marked");
        cow.discard();

        helper.assertTrue(item.place(context).consumesAction(), "not placed once clear");
        Set<BlockPos> placed = TurretPlacementTests.placed(helper, block);
        helper.assertTrue(placed.equals(Set.copyOf(footprint.blocks())), "placed " + placed + ", footprint " + footprint.blocks());
        helper.succeed();
    }

    /**
     * Only huge modules fit a Fortress and they fit nothing else. No huge weapon exists yet, and a test cannot make one: an
     * Item built after registration throws "Registry is already frozen". So the lock is checked through the kind it compares
     * (module.kind() == base kind): the Fortress is FORTRESS, and every registered module fits each base exactly when its
     * kind is that base's (the first huge turret joins this matrix by itself).
     */
    @GameTest(template = RANGE, batch = "fortressTakesOnlyHugeModules")
    public static void fortressTakesOnlyHugeModules(GameTestHelper helper) {
        TurretBaseBlockEntity fortress = fortress(helper);
        BlockPos standardPos = new BlockPos(12, 2, 5), largePos = new BlockPos(16, 2, 5);
        helper.setBlock(standardPos, BastionBlocks.TURRET_BASE.get());
        LargeTurretBaseBlock.placeAt(helper.getLevel(), helper.absolutePos(largePos), BastionBlocks.LARGE_TURRET_BASE.get());
        TurretBaseBlockEntity standard = (TurretBaseBlockEntity) helper.getBlockEntity(standardPos), large = (TurretBaseBlockEntity) helper.getBlockEntity(largePos);
        helper.assertTrue(fortress.kind() == BaseKind.FORTRESS && fortress.inventory().kind() == BaseKind.FORTRESS,
                "a Fortress reads as " + fortress.kind() + " / " + fortress.inventory().kind());
        for (Item module : List.of(BastionItems.GUN_TURRET.get(), BastionItems.MISSILE_LAUNCHER_TURRET.get(), BastionItems.COBBLESTONE_TURRET.get())) {
            helper.assertTrue(!fortress.inventory().isItemValid(TurretInventory.WEAPON, new ItemStack(module)), "a Fortress took " + module);
        }
        Map<TurretBaseBlockEntity, BaseKind> bases = Map.of(fortress, BaseKind.FORTRESS, standard, BaseKind.STANDARD, large, BaseKind.LARGE);
        for (Item item : BuiltInRegistries.ITEM) {
            if (!(item instanceof WeaponModuleItem module)) continue;
            bases.forEach((base, kind) -> helper.assertTrue(base.inventory().isItemValid(TurretInventory.WEAPON, new ItemStack(module)) == (module.kind() == kind),
                    module + " (" + module.kind() + ") on a " + kind + " base"));
        }
        helper.succeed();
    }

    @GameTest(template = RANGE, batch = "fortressHealthFollowsConfig")
    public static void fortressHealthFollowsConfig(GameTestHelper helper) {
        float multiplier = BastionConfig.FORTRESS_HEALTH_MULTIPLIER.get().floatValue();
        for (TurretTier tier : TurretTier.values()) {
            float expected = BastionConfig.maxHealth(tier) * multiplier; // 500 / 1250 / 2500 with the default config
            float hp = TurretBaseBlockEntity.maxHealth(tier, BaseKind.FORTRESS, ModifierEffect.NONE);
            helper.assertTrue(Math.abs(hp - expected) < 1e-3, tier + " Fortress max HP " + hp + ", expected " + expected);
        }
        TurretBaseBlockEntity turret = fortress(helper);
        float t1 = BastionConfig.maxHealth(TurretTier.T1) * multiplier;
        helper.assertTrue(turret.tier() == TurretTier.T1 && Math.abs(turret.maxHealth() - t1) < 1e-3 && Math.abs(turret.health() - t1) < 1e-3,
                "a placed T1 Fortress has " + turret.health() + "/" + turret.maxHealth() + " HP, expected " + t1);
        helper.succeed();
    }

    /** 0 HP: every cell goes, one damaged Fortress remembers T3, the module drops, weapon and ammo are lost. */
    @GameTest(template = RANGE, batch = "destroyedFortressDropsItsDamagedSelf")
    public static void destroyedFortressDropsItsDamagedSelf(GameTestHelper helper) {
        drops(helper).forEach(ItemEntity::discard); // counted below
        TurretBaseBlockEntity turret = fortress(helper);
        turret.upgradeTo(TurretTier.T2);
        turret.upgradeTo(TurretTier.T3);
        turret.inventory().setStackInSlot(TurretInventory.WEAPON, new ItemStack(BastionItems.GUN_TURRET.get())); // past the size lock: no huge weapon yet
        turret.inventory().setStackInSlot(TurretInventory.AMMO_START, new ItemStack(BastionItems.KINETIC_ROUNDS.get(), 16));
        turret.inventory().setStackInSlot(TurretInventory.MODIFIER_START, new ItemStack(BastionItems.RANGE_MODULE.get()));
        turret.takeDamage(helper.getLevel().damageSources().generic(), turret.maxHealth() + 1);
        for (BlockPos p : cells(helper)) helper.assertTrue(helper.getLevel().getBlockState(p).isAir(), "cell " + p + " survived");
        helper.succeedWhen(() -> {
            List<ItemEntity> drops = drops(helper);
            List<ItemEntity> damaged = drops.stream().filter(e -> e.getItem().is(BastionItems.DAMAGED_FORTRESS_TURRET_BASE.get())).toList();
            helper.assertTrue(damaged.size() == 1 && damaged.get(0).getItem().getCount() == 1, "expected one Damaged Fortress Turret Base, got " + names(drops));
            String tier = damaged.get(0).getItem().getOrCreateTagElement("BlockEntityTag").getString("Tier");
            helper.assertTrue(tier.equals("T3"), "the damaged Fortress must remember T3, got '" + tier + "'");
            helper.assertTrue(drops.stream().anyMatch(e -> e.getItem().is(BastionItems.RANGE_MODULE.get())), "the module did not drop: " + names(drops));
            helper.assertTrue(drops.stream().noneMatch(e -> e.getItem().is(BastionItems.GUN_TURRET.get()) || e.getItem().is(BastionItems.KINETIC_ROUNDS.get())
                    || e.getItem().is(BastionItems.DAMAGED_TURRET_BASE.get()) || e.getItem().is(BastionItems.FORTRESS_TURRET_BASE.get())), "dropped " + names(drops));
        });
    }

    /** Mined, a Fortress keeps tier, weapon slot and ammo in its item (shulker-style); placed again it is that base again. */
    @GameTest(template = RANGE, timeoutTicks = 100, batch = "minedFortressKeepsItsContents")
    public static void minedFortressKeepsItsContents(GameTestHelper helper) {
        drops(helper).forEach(ItemEntity::discard); // counted below
        TurretBaseBlockEntity turret = fortress(helper);
        turret.upgradeTo(TurretTier.T2);
        turret.inventory().setStackInSlot(TurretInventory.WEAPON, new ItemStack(BastionItems.GUN_TURRET.get())); // past the size lock: no huge weapon yet
        turret.inventory().setStackInSlot(TurretInventory.AMMO_START, new ItemStack(BastionItems.KINETIC_ROUNDS.get(), 7));
        BlockPos core = helper.absolutePos(CORE);
        helper.getLevel().destroyBlock(core, true); // broken by hand: the loot table copies the contents
        ItemStack[] drop = new ItemStack[1];
        helper.startSequence().thenWaitUntil(() -> {
            List<ItemEntity> drops = drops(helper);
            helper.assertTrue(drops.size() == 1 && drops.get(0).getItem().is(BastionItems.FORTRESS_TURRET_BASE.get()), "expected one Fortress Turret Base, got " + names(drops));
            drop[0] = drops.get(0).getItem().copy();
            drops.get(0).discard();
        }).thenExecute(() -> {
            CompoundTag data = BlockItem.getBlockEntityData(drop[0]);
            helper.assertTrue(data != null && data.getString("Tier").equals("T2"), "the drop does not carry T2: " + data);
            TurretInventory carried = new TurretInventory(() -> TurretTier.T2, () -> BaseKind.FORTRESS, () -> true, slot -> {});
            carried.deserializeNBT(data.getCompound("Inventory"));
            helper.assertTrue(carried.getStackInSlot(TurretInventory.WEAPON).is(BastionItems.GUN_TURRET.get())
                    && carried.getStackInSlot(TurretInventory.AMMO_START).getCount() == 7, "the drop lost its weapon slot or ammo: " + data);

            FakePlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
            player.moveTo(core.getX() + 0.5, core.getY(), core.getZ() - 2.5, 0, 30); // south of the footprint, looking at it
            player.setItemInHand(InteractionHand.MAIN_HAND, drop[0]);
            BlockPos floor = core.below();
            BlockPlaceContext context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, drop[0],
                    new BlockHitResult(Vec3.atCenterOf(floor).relative(Direction.UP, 0.5), Direction.UP, floor, false));
            boolean placed = ((BlockItem) BastionItems.FORTRESS_TURRET_BASE.get()).place(context).consumesAction();
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY); // shared with later tests
            helper.assertTrue(placed, "the mined Fortress could not be placed again");
            TurretBaseBlockEntity restored = helper.getLevel().getBlockEntity(core) instanceof TurretBaseBlockEntity b ? b : null;
            helper.assertTrue(restored != null && restored.tier() == TurretTier.T2
                    && restored.inventory().getStackInSlot(TurretInventory.AMMO_START).getCount() == 7, "placed again it lost its tier or ammo");
            for (BlockPos p : cells(helper)) helper.assertTrue(TurretBaseBlock.core(helper.getLevel(), p) == restored, "cell " + p + " not part of the placed Fortress");
        }).thenWaitUntil(() -> {
            // A Gun does not fit a Fortress: placed again, it drops off whole (TurretBaseBlock.setPlacedBy) instead of being lost.
            helper.assertTrue(drops(helper).stream().anyMatch(e -> e.getItem().is(BastionItems.GUN_TURRET.get())), "the carried Gun did not drop off");
        }).thenSucceed();
    }

    @GameTest(template = RANGE, timeoutTicks = 300, batch = "redstoneOnAnyFortressBlockCounts")
    public static void redstoneOnAnyFortressBlockCounts(GameTestHelper helper) {
        TurretBaseBlockEntity turret = fortress(helper);
        turret.inventory().setStackInSlot(TurretInventory.WEAPON, new ItemStack(BastionItems.GUN_TURRET.get())); // the switch only counts with a weapon
        BlockPos underCore = CORE.below(), byCorner = CORE.offset(2, 0, 1); // byCorner touches cell (1, 1) only, not the core
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(turret.state() != TurretState.DISABLED, "an unpowered Fortress is " + turret.state()))
                .thenExecute(() -> helper.setBlock(underCore, Blocks.REDSTONE_BLOCK))
                .thenWaitUntil(() -> helper.assertTrue(turret.state() == TurretState.DISABLED, "redstone at the core did not switch it off: " + turret.state()))
                .thenExecute(() -> helper.setBlock(underCore, Blocks.STONE))
                .thenWaitUntil(() -> helper.assertTrue(turret.state() != TurretState.DISABLED, "still off without redstone: " + turret.state()))
                .thenExecute(() -> helper.setBlock(byCorner, Blocks.REDSTONE_BLOCK))
                .thenWaitUntil(() -> helper.assertTrue(turret.state() == TurretState.DISABLED, "redstone at an outer corner did not switch it off: " + turret.state()))
                .thenSucceed();
    }

    @GameTest(template = RANGE, batch = "fortressHitboxSpansItsFootprint")
    public static void fortressHitboxSpansItsFootprint(GameTestHelper helper) {
        fortress(helper);
        BlockPos core = helper.absolutePos(CORE);
        helper.succeedWhen(() -> {
            List<TurretHitboxEntity> hitboxes = helper.getLevel().getEntitiesOfClass(TurretHitboxEntity.class, new AABB(core).inflate(3), h -> core.equals(h.base()));
            helper.assertTrue(hitboxes.size() == 1, "expected one hitbox, found " + hitboxes.size());
            float width = hitboxes.get(0).getBbWidth();
            helper.assertTrue(Math.abs(width - 2.95f) < 1e-3, "the hitbox is " + width + " wide, expected 2.95");
        });
    }

    @GameTest(template = RANGE, batch = "tierKitWorksOnAnyFortressBlock")
    public static void tierKitWorksOnAnyFortressBlock(GameTestHelper helper) {
        TurretBaseBlockEntity turret = fortress(helper);
        BlockPos cell = helper.absolutePos(CORE.offset(-1, 0, 1)); // an outer corner
        FakePlayer player = ControlHubTests.player(helper, CORE.offset(-1, 0, 3));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BastionItems.TIER_UPGRADE_KIT_T2.get()));
        helper.getLevel().getBlockState(cell).use(helper.getLevel(), player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(cell), Direction.UP, cell, false));
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY); // shared with later tests
        helper.assertTrue(turret.tier() == TurretTier.T2, "a T2 kit on an outer cell left the Fortress at " + turret.tier());
        helper.succeed();
    }

    /** A structure turned or mirrored (structure blocks, jigsaw) moves every cell; each must still lead to the core. */
    @GameTest(template = RANGE, batch = "turnedFortressKeepsItsCore")
    public static void turnedFortressKeepsItsCore(GameTestHelper helper) {
        fortress(helper);
        BlockPos core = helper.absolutePos(CORE);
        for (BlockPos p : cells(helper)) {
            BlockState state = helper.getLevel().getBlockState(p);
            helper.assertTrue(state.is(BastionBlocks.FORTRESS_TURRET_BASE.get()), "no Fortress block at " + p);
            for (Mirror mirror : Mirror.values()) {
                for (Rotation rotation : Rotation.values()) {
                    // Template space with the core at the origin, moved as a structure places it (mirror, then rotate): the core stays put.
                    BlockPos moved = StructureTemplate.transform(p.subtract(core), mirror, rotation, BlockPos.ZERO);
                    BlockPos found = FortressTurretBaseBlock.corePosOf(state.mirror(mirror).rotate(rotation), moved);
                    helper.assertTrue(found.equals(BlockPos.ZERO), p.subtract(core) + " " + mirror + " " + rotation + " leads to " + found + ", not the core");
                }
            }
        }
        helper.succeed();
    }

    /** The workstation recipe {@code id} makes {@code result} at {@code station} from exactly {@code ingredients} (item id to count). */
    static WorkstationRecipe recipe(GameTestHelper helper, String id, WorkstationType station, String result, Map<String, Integer> ingredients) {
        WorkstationRecipe recipe = helper.getLevel().getRecipeManager().byKey(Bastion.id("workstation/" + id)).orElse(null) instanceof WorkstationRecipe w ? w : null;
        helper.assertTrue(recipe != null && recipe.station() == station && BuiltInRegistries.ITEM.getKey(recipe.result().getItem()).toString().equals(result),
                "no " + station + " recipe " + id + " making " + result);
        Map<String, Integer> found = new HashMap<>();
        recipe.ingredients().forEach(c -> found.put(BuiltInRegistries.ITEM.getKey(c.ingredient().getItems()[0].getItem()).toString(), c.count()));
        helper.assertTrue(found.equals(ingredients), id + " takes " + found + ", expected " + ingredients);
        return recipe;
    }

    /** Fortress Frame, the mixed assembler recipe and the repair; the Large Turret Base still takes one of each heavy part. */
    @GameTest(template = RANGE, batch = "fortressRecipesAreMixed")
    public static void fortressRecipesAreMixed(GameTestHelper helper) {
        WorkstationRecipe frame = recipe(helper, "part_workstation/fortress_frame", WorkstationType.PART_WORKSTATION, "bastion:fortress_frame",
                Map.of("minecraft:iron_block", 4, "minecraft:diamond", 1, "minecraft:redstone_block", 1));
        helper.assertTrue(frame.category().equals(Bastion.id("large_turret_base")), "the Fortress Frame sits under " + frame.category() + ", not the Large Base tab");
        recipe(helper, "part_assembler/fortress_turret_base", WorkstationType.PART_ASSEMBLER, "bastion:fortress_turret_base",
                Map.of("bastion:fortress_frame", 1, "bastion:large_base_plating", 2, "bastion:large_base_housing", 1, "bastion:large_base_turntable", 1));
        recipe(helper, "part_assembler/fortress_turret_base_from_damaged", WorkstationType.PART_ASSEMBLER, "bastion:fortress_turret_base",
                Map.of("bastion:damaged_fortress_turret_base", 1, "bastion:repair_kit", 3));
        recipe(helper, "part_assembler/large_turret_base", WorkstationType.PART_ASSEMBLER, "bastion:large_turret_base",
                Map.of("bastion:large_base_plating", 1, "bastion:large_base_housing", 1, "bastion:large_base_turntable", 1));
        helper.succeed();
    }

    private static long fedTimes(FeedHubBlockEntity hub, TurretBaseBlockEntity turret) {
        return hub.feeds().stream().filter(feed -> feed.turret() == turret).count();
    }

    /**
     * A Fortress with all nine blocks on a 3x3x1 Feed Hub is fed (once); one on a 2x2x1, its core over a hub block but five
     * cells over air, is not. Checked on the hub's fed list: no huge weapon exists to take ammo.
     */
    @GameTest(template = RANGE, timeoutTicks = 60, batch = "feedHubFeedsFortressOnlyOnAFull3x3")
    public static void feedHubFeedsFortressOnlyOnAFull3x3(GameTestHelper helper) {
        BlockPos full = new BlockPos(1, 2, 1), small = new BlockPos(8, 2, 1);
        TurretFeedHubTests.hubs(helper, full, 3, 1);
        TurretFeedHubTests.hubs(helper, small, 2, 1);
        TurretBaseBlockEntity on = fortress(helper, full.offset(1, 1, 1)), off = fortress(helper, small.offset(1, 1, 1));
        FeedHubBlockEntity fullHub = (FeedHubBlockEntity) helper.getBlockEntity(full), smallHub = (FeedHubBlockEntity) helper.getBlockEntity(small);
        helper.runAfterDelay(3, () -> helper.succeedWhen(() -> {
            helper.assertTrue(fedTimes(fullHub, on) == 1, "the Fortress on the 3x3 is fed " + fedTimes(fullHub, on) + " times, expected once");
            helper.assertTrue(fedTimes(smallHub, off) == 0, "fed a Fortress standing partly off a 2x2");
        }));
    }

    /** The configurator, filled on a standard base, pastes onto the core through an outer Fortress cell (and a Large Base part). */
    @GameTest(template = RANGE, batch = "configuratorWorksOnAnyFortressBlock")
    public static void configuratorWorksOnAnyFortressBlock(GameTestHelper helper) {
        TurretBaseBlockEntity fortress = fortress(helper);
        BlockPos sourcePos = new BlockPos(12, 2, 5), largePos = new BlockPos(16, 2, 5);
        helper.setBlock(sourcePos, BastionBlocks.TURRET_BASE.get());
        TurretBaseBlockEntity source = (TurretBaseBlockEntity) helper.getBlockEntity(sourcePos);
        LargeTurretBaseBlock.placeAt(helper.getLevel(), helper.absolutePos(largePos), BastionBlocks.LARGE_TURRET_BASE.get());
        TurretBaseBlockEntity large = TurretBaseBlock.core(helper.getLevel(), helper.absolutePos(largePos));
        TargetFilter filter = source.filter().copy();
        filter.priority = TargetFilter.Priority.LOWEST_HEALTH;
        source.setFilter(filter);
        helper.assertTrue(fortress.filter().priority != filter.priority && large.filter().priority != filter.priority, "a base already had the pasted priority");

        FakePlayer player = ControlHubTests.player(helper, CORE.offset(0, 0, 3));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BastionItems.TURRET_CONFIGURATOR.get()));
        BlockPos largePart = helper.absolutePos(largePos).offset(1, 0, 1); // placeAt puts the core at the low corner
        BlockPos outer = helper.absolutePos(CORE.offset(1, 0, -1));
        player.setShiftKeyDown(true);
        use(player, helper.absolutePos(sourcePos));
        player.setShiftKeyDown(false);
        use(player, outer);
        use(player, largePart);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY); // shared with later tests
        helper.assertTrue(fortress.filter().priority == TargetFilter.Priority.LOWEST_HEALTH, "an outer cell did not take the settings: " + fortress.filter().priority);
        helper.assertTrue(large.filter().priority == TargetFilter.Priority.LOWEST_HEALTH, "a Large Base part did not take the settings: " + large.filter().priority);
        helper.succeed();
    }

    /** Uses the held item on the top of {@code pos} (a base's block hands the configurator's click on to the item). */
    private static void use(FakePlayer player, BlockPos pos) {
        player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos).relative(Direction.UP, 0.5), Direction.UP, pos, false)));
    }

    /** Jade on any of the nine blocks shows the core's lines, tier and Fortress HP included. */
    @GameTest(template = RANGE, batch = "fortressPartsLeadToTheCore")
    public static void fortressPartsLeadToTheCore(GameTestHelper helper) {
        TurretBaseBlockEntity turret = fortress(helper);
        turret.upgradeTo(TurretTier.T2);
        List<String> core = CompatTests.lines(turret, true);
        float max = BastionConfig.maxHealth(TurretTier.T2) * BastionConfig.FORTRESS_HEALTH_MULTIPLIER.get().floatValue();
        helper.assertTrue(core.get(0).endsWith(",gui.bastion.turret.tier[2]]")
                && core.get(1).equals("gui.bastion.turret.hp[" + WeaponStatRows.fmt(turret.health()) + "," + WeaponStatRows.fmt(max) + "]"), "core lines " + core);
        for (BlockPos p : cells(helper)) {
            BlockPos found = BlockInfo.corePos(helper.getLevel(), p, helper.getLevel().getBlockState(p));
            helper.assertTrue(turret.getBlockPos().equals(found), "cell " + p + " leads to " + found + ", not the core");
            List<String> lines = CompatTests.lines(helper.getLevel().getBlockEntity(found), true);
            helper.assertTrue(lines.equals(core), "cell " + p + " shows " + lines);
        }
        helper.succeed();
    }

    /** A comparator beside an outer cell, not touching the core, follows the ammo like one beside the core (final review). */
    @GameTest(template = RANGE, timeoutTicks = 60, batch = "comparatorBesideAnyFortressBlockUpdates")
    public static void comparatorBesideAnyFortressBlockUpdates(GameTestHelper helper) {
        TurretBaseBlockEntity turret = fortress(helper);
        BlockPos comparator = CORE.offset(2, 0, 1); // east of the corner cell (1, 0, 1), reading it
        helper.setBlock(comparator, Blocks.COMPARATOR.defaultBlockState().setValue(ComparatorBlock.FACING, Direction.WEST));
        helper.runAtTickTime(5, () -> turret.inventory().insertItem(TurretInventory.AMMO_START, new ItemStack(BastionItems.KINETIC_ROUNDS.get(), 64), false));
        helper.runAfterDelay(6, () -> helper.succeedWhen(() -> {
            int signal = ((ComparatorBlockEntity) helper.getBlockEntity(comparator)).getOutputSignal();
            helper.assertTrue(signal > 0, "comparator beside an outer cell reads " + signal + ", the base " + turret.comparatorLevel());
        }));
    }

    /** A lone Fortress cell (a half-printed schematic) leads to no core, not to whatever base stands where its core would be. */
    @GameTest(template = RANGE, batch = "strayFortressCellLeadsNowhere")
    public static void strayFortressCellLeadsNowhere(GameTestHelper helper) {
        BlockPos stray = new BlockPos(2, 2, 2), wouldBeCore = stray.offset(1, 0, 1); // cell 0 sits at core + (-1, 0, -1)
        helper.setBlock(wouldBeCore, BastionBlocks.TURRET_BASE.get());
        helper.setBlock(stray, BastionBlocks.FORTRESS_TURRET_BASE.get().defaultBlockState().setValue(FortressTurretBaseBlock.CELL, 0));
        TurretBaseBlockEntity standard = (TurretBaseBlockEntity) helper.getBlockEntity(wouldBeCore);
        helper.assertTrue(TurretBaseBlock.core(helper.getLevel(), helper.absolutePos(stray)) != standard,
                "a stray Fortress cell leads to the standard base beside it");
        helper.succeed();
    }

    /** TNT at an outer corner takes the whole base down with exactly one drop (Review Focus 3, the exploding half). */
    @GameTest(template = RANGE, timeoutTicks = 60, batch = "explodedFortressCellDropsTheBaseOnce")
    public static void explodedFortressCellDropsTheBaseOnce(GameTestHelper helper) {
        drops(helper).forEach(ItemEntity::discard);
        fortress(helper);
        Vec3 blast = helper.absoluteVec(Vec3.atCenterOf(CORE.offset(2, 0, 2))); // just past the corner cell
        helper.getLevel().explode(null, blast.x, blast.y, blast.z, 6f, Level.ExplosionInteraction.TNT);
        for (BlockPos p : cells(helper)) helper.assertTrue(helper.getLevel().getBlockState(p).isAir(), "cell " + p + " survived the blast");
        helper.succeedWhen(() -> {
            List<ItemEntity> bases = drops(helper).stream().filter(e -> e.getItem().is(BastionItems.FORTRESS_TURRET_BASE.get())).toList();
            helper.assertTrue(bases.size() == 1, "expected one Fortress Turret Base drop, got " + names(drops(helper)));
        });
    }
}
