# Feed Hub Multiblock + Energy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Feed Hubs placed together merge into one structure (1x1x1 up to 3x3x3) that shares its slots, stores FE and feeds ammo + FE to every turret mounted on it, large bases on top included, with a two-tab GUI and a Create-vault-style connected look.

**Architecture:** A hub's place in its structure lives in three blockstate properties (`x`, `y`, `z` = alone/low/middle/high), so the structure needs no saved data, syncs to the client with the block, and drives a generated multipart model (no render code). Every place/break schedules a 1-tick re-form that re-partitions all connected hubs from scratch. Each block keeps its own 9 slots + FE; a per-tick `Group` (built by the structure's lowest block) joins them for capabilities, the GUI and distribution.

**Tech Stack:** Forge 1.20.1 (47.4.x), Java 17, Forge GameTests, Python 3 generators in `tools/` (numpy + Pillow for textures, plain json for models).

**Spec:** `docs/PLAN.md`, section "Feed Hub" → "Multiblock + energi" (approved by the user 2026-10-07 15:47).

## Global Constraints

- No new dependencies; Forge 1.20.1, Java 17.
- NO-VANILLA visuals: every texture comes from `tools/gen_textures.py`.
- Client code (anything touching `net.minecraft.client`) only in `dev.bastion.client` packages.
- Balance numbers in config, per block: `feedHubEnergyCapacity` 50000, `feedHubMaxInput` 4000 (FE per tick), `feedHubItemsPerTick` 8.
- Shapes: width 1..3, height <= width: 1x1x1, 2x2x1, 2x2x2, 3x3x1, 3x3x2, 3x3x3; most blocks first (27, 18, 9, 8, 4, 1) from the lowest block (y, then x, then z).
- GUI text through `fit()`, no overlap; every string in `en_us.json` and `id_id.json` (tab names: "Summary"/"Ringkasan", "Storage"/"Storage").
- A lone hub looks exactly as today (`block/feed_hub`).
- No commits until the user says "commit"; never commit `modlist.html`.
- Never touch `run/saves/showcase`; scenes run in a scratch copy (`showcase_fx`). Gradle always with `--offline`.

## Review Focus

1. **Structure across a chunk border, one side unloaded**: nothing force-loads a chunk and nothing writes into an unloaded block's slots. Owned by Task 1 (`isHub` checks `isLoaded`) and Task 2 (`Group` skips unloaded/removed blocks; a block whose controller is not loaded runs alone). No GameTest can unload a chunk: check by reading the code paths in review.
2. **A pipe that fetched the capability before the hubs merged**: it must reach the whole structure afterwards (the view is dynamic). Test in Task 2 (`feedHubSharesStorage`).
3. **A large base standing half on the structure**: it gets nothing. Test in Task 2 (`feedHubFeedsLargeBase`).
4. **Breaking the controller (lowest) block**: the rest re-forms and the new lowest block keeps feeding. Test in Task 2 (`feedHubNewControllerFeeds`).
5. **Scrolling the Storage tab while items flow in**: the client slots mirror the server's window, so no row shows another row's items for more than a tick. Manual check in Task 5.

---

## File map

| File | Change | Responsibility |
|---|---|---|
| `src/main/java/dev/bastion/turret/FeedHubBlock.java` | modify | `Part` enum + `X/Y/Z` properties, schedule re-form on place/break, run it in `tick` |
| `src/main/java/dev/bastion/turret/FeedHubStructure.java` | create | `Box` (read a structure from states) and `reform` (partition connected hubs) |
| `src/main/java/dev/bastion/turret/FeedHubBlockEntity.java` | modify | per-block slots + FE, per-tick `Group`, structure-wide item/energy views, feeds incl. large bases, distribution |
| `src/main/java/dev/bastion/config/BastionConfig.java` | modify | 3 config values |
| `src/main/java/dev/bastion/menu/FeedHubMenu.java` | rewrite | scrolling storage window, summary `ContainerData`, shift-click into the whole structure |
| `src/main/java/dev/bastion/client/screen/FeedHubScreen.java` | rewrite | side tabs Summary / Storage, scrollbar |
| `tools/gen_textures.py` | modify | `feed_hub_gui(plain)`, `feed_hub_panel`, `feed_hub_edge`, `feed_hub_edge_glow` |
| `tools/gen_feed_hub_models.py` | create | multipart blockstate + panel/frame/glow models |
| `src/main/resources/assets/bastion/{blockstates,models/block,textures}/...` | generated | output of the two generators |
| `src/main/resources/assets/bastion/lang/{en_us,id_id}.json` | modify | GUI strings, tooltip |
| `src/main/java/dev/bastion/gametest/TurretFeedHubTests.java` | modify | 7 new tests; the 2 old ones unchanged |
| `src/dev/java/dev/bastion/dev/FeedHubShowcase.java` | modify | scene: lone hub, 2x2x1 + Tesla, 3x3x2 ring of turrets, both GUI tabs |
| `docs/TESTING.md`, `docs/release/DESCRIPTION.md`, `docs/release/DESCRIPTION.curseforge.html`, `docs/release/UPLOAD.md`, `docs/HANDOFF.md` | modify | checklist, store text, beta.5 changelog, handoff |

GameTest command for every task: `./gradlew runGameTestServer --offline` (runs all tests; the log ends with the pass/fail summary). Arena `arena` inside: test x 1..11, y 2..6 (floor at y 1), z 1..5.

---

### Task 1: Structure shapes and forming

**Files:**
- Modify: `src/main/java/dev/bastion/turret/FeedHubBlock.java`
- Create: `src/main/java/dev/bastion/turret/FeedHubStructure.java`
- Test: `src/main/java/dev/bastion/gametest/TurretFeedHubTests.java`

**Interfaces:**
- Produces: `FeedHubBlock.Part {ALONE, LOW, MIDDLE, HIGH}` with `static Part of(int offset, int size)`; `FeedHubBlock.X/Y/Z` (`EnumProperty<Part>`, names `x`/`y`/`z`); `static Part FeedHubBlock.part(BlockState, Direction.Axis)`; `record FeedHubStructure.Box(BlockPos min, int width, int height)` with `static Box of(Level, BlockPos, BlockState)`, `static Box single(BlockPos)`, `boolean contains(BlockPos)`, `int blocks()`, `List<BlockPos> positions()` (y, then x, then z), `String size()` ("3x3x2"); `static void FeedHubStructure.reform(Level, BlockPos)`; `static boolean FeedHubStructure.isHub(Level, BlockPos)`.

- [ ] **Step 1: Write the failing tests** (append to `TurretFeedHubTests`; imports `FeedHubBlock`, `FeedHubStructure`, `java.util.List`)

```java
    /** Hubs on every block of a box {@code width} wide and {@code height} tall from {@code min}. */
    private static void hubs(GameTestHelper helper, BlockPos min, int width, int height) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                for (int z = 0; z < width; z++) helper.setBlock(min.offset(x, y, z), BastionBlocks.FEED_HUB.get());
            }
        }
    }

    /** Every block of the box carries its place in a structure of that size. */
    private static void assertBox(GameTestHelper helper, BlockPos min, int width, int height) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                for (int z = 0; z < width; z++) {
                    BlockPos pos = min.offset(x, y, z);
                    BlockState state = helper.getBlockState(pos);
                    boolean ok = state.is(BastionBlocks.FEED_HUB.get())
                            && state.getValue(FeedHubBlock.X) == FeedHubBlock.Part.of(x, width)
                            && state.getValue(FeedHubBlock.Y) == FeedHubBlock.Part.of(y, height)
                            && state.getValue(FeedHubBlock.Z) == FeedHubBlock.Part.of(z, width);
                    helper.assertTrue(ok, pos + " is " + state + ", expected part of a " + width + "x" + width + "x" + height + " from " + min);
                }
            }
        }
    }

    /** Loose hubs merge: a 2x2x1, a 2x2x2 and a 3x3x3 side by side (gaps between them). */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "feedHubForms")
    public static void feedHubForms(GameTestHelper helper) {
        hubs(helper, new BlockPos(1, 2, 1), 2, 1);
        hubs(helper, new BlockPos(4, 2, 1), 2, 2);
        hubs(helper, new BlockPos(7, 2, 1), 3, 3);
        helper.succeedWhen(() -> {
            assertBox(helper, new BlockPos(1, 2, 1), 2, 1);
            assertBox(helper, new BlockPos(4, 2, 1), 2, 2);
            assertBox(helper, new BlockPos(7, 2, 1), 3, 3);
        });
    }

    /** Breaking a corner of a 3x3x1 re-forms the rest: a 2x2x1 from the lowest corner, the four others alone. */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = "feedHubSplits")
    public static void feedHubSplits(GameTestHelper helper) {
        BlockPos min = new BlockPos(4, 2, 1);
        hubs(helper, min, 3, 1);
        helper.runAtTickTime(5, () -> {
            assertBox(helper, min, 3, 1);
            helper.destroyBlock(min.offset(2, 0, 2));
        });
        helper.runAfterDelay(6, () -> helper.succeedWhen(() -> {
            assertBox(helper, min, 2, 1);
            for (BlockPos alone : List.of(min.offset(2, 0, 0), min.offset(2, 0, 1), min.offset(0, 0, 2), min.offset(1, 0, 2))) {
                assertBox(helper, alone, 1, 1);
            }
        }));
    }
```

- [ ] **Step 2: Run, verify they fail**

Run: `./gradlew runGameTestServer --offline`
Expected: compile error (`FeedHubBlock.X`, `Part` undefined).

- [ ] **Step 3: Implement** — `FeedHubBlock` additions:

```java
    /** Where a block sits along one axis of its structure (FeedHubStructure). */
    public enum Part implements StringRepresentable {
        ALONE, LOW, MIDDLE, HIGH;

        /** The part of the block {@code offset} blocks into a structure {@code size} long on that axis. */
        public static Part of(int offset, int size) {
            return size == 1 ? ALONE : offset == 0 ? LOW : offset == size - 1 ? HIGH : MIDDLE;
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final EnumProperty<Part> X = EnumProperty.create("x", Part.class);
    public static final EnumProperty<Part> Y = EnumProperty.create("y", Part.class);
    public static final EnumProperty<Part> Z = EnumProperty.create("z", Part.class);

    public FeedHubBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(X, Part.ALONE).setValue(Y, Part.ALONE).setValue(Z, Part.ALONE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(X, Y, Z);
    }

    public static Part part(BlockState state, Direction.Axis axis) {
        return state.getValue(axis == Direction.Axis.X ? X : axis == Direction.Axis.Y ? Y : Z);
    }

    /** Merging waits a tick (as Create's vaults do): the block entity does not exist yet inside onPlace. */
    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moving) {
        super.onPlace(state, level, pos, oldState, moving);
        if (!oldState.is(this)) level.scheduleTick(pos, this, 1);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        FeedHubStructure.reform(level, pos);
    }
```

and `onRemove` becomes (drops only this block's own buffer, then lets the neighbours re-form):

```java
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (state.is(newState.getBlock())) { // only its place in the structure changed
            super.onRemove(state, level, pos, newState, moving);
            return;
        }
        if (level.getBlockEntity(pos) instanceof FeedHubBlockEntity hub) {
            for (int i = 0; i < hub.buffer().getSlots(); i++) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), hub.buffer().getStackInSlot(i));
            }
        }
        super.onRemove(state, level, pos, newState, moving);
        for (Direction side : Direction.values()) {
            if (level.getBlockState(pos.relative(side)).is(this)) level.scheduleTick(pos.relative(side), this, 1);
        }
    }
```

`FeedHubStructure.java`:

```java
package dev.bastion.turret;

import dev.bastion.turret.FeedHubBlock.Part;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * Feed Hub multiblock (PLAN "Feed Hub", 1a): hubs next to each other merge into boxes with a square footprint 1..3
 * wide, at most as tall as wide. A block's place in its box is in its state (FeedHubBlock X/Y/Z), so a structure needs
 * no saved data, reaches the client with the blocks and picks the connected model. Every change re-partitions all
 * connected hubs from scratch, so the result never depends on the order they were built in.
 */
public final class FeedHubStructure {
    public static final int MAX_WIDTH = 3;
    /** {width, height}, most blocks first: 27, 18, 9, 8, 4, 1. */
    private static final int[][] SHAPES = {{3, 3}, {3, 2}, {3, 1}, {2, 2}, {2, 1}, {1, 1}};
    // ponytail: hubs gathered per re-form; past this a giant hub wall only re-forms the part found first.
    private static final int SEARCH_LIMIT = 512;

    private FeedHubStructure() {
    }

    /** The box a structure fills. */
    public record Box(BlockPos min, int width, int height) {
        public static Box single(BlockPos pos) {
            return new Box(pos, 1, 1);
        }

        /** The box of the hub at {@code pos}, read from the states of the blocks on the way (loaded blocks only). */
        public static Box of(Level level, BlockPos pos, BlockState state) {
            BlockPos min = new BlockPos(pos.getX() - back(level, pos, state, Direction.Axis.X),
                    pos.getY() - back(level, pos, state, Direction.Axis.Y), pos.getZ() - back(level, pos, state, Direction.Axis.Z));
            return new Box(min, length(level, min, Direction.Axis.X), length(level, min, Direction.Axis.Y));
        }

        public boolean contains(BlockPos pos) {
            return pos.getX() >= min.getX() && pos.getX() < min.getX() + width && pos.getY() >= min.getY() && pos.getY() < min.getY() + height
                    && pos.getZ() >= min.getZ() && pos.getZ() < min.getZ() + width;
        }

        public int blocks() {
            return width * width * height;
        }

        /** Bottom layer first, then x, then z: the order of the structure's slots and FE. */
        public List<BlockPos> positions() {
            List<BlockPos> positions = new ArrayList<>(blocks());
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    for (int z = 0; z < width; z++) positions.add(min.offset(x, y, z));
                }
            }
            return positions;
        }

        /** "3x3x2" */
        public String size() {
            return width + "x" + width + "x" + height;
        }
    }

    /** Re-partitions every hub connected to {@code start} (a scheduled tick after a hub was placed or broken). */
    static void reform(Level level, BlockPos start) {
        if (!isHub(level, start)) return;
        Set<BlockPos> hubs = new HashSet<>(List.of(start));
        Deque<BlockPos> open = new ArrayDeque<>(hubs);
        while (!open.isEmpty() && hubs.size() < SEARCH_LIMIT) {
            BlockPos at = open.poll();
            for (Direction side : Direction.values()) {
                BlockPos next = at.relative(side);
                if (!hubs.contains(next) && isHub(level, next)) {
                    hubs.add(next);
                    open.add(next);
                }
            }
        }
        List<BlockPos> order = new ArrayList<>(hubs);
        order.sort(Comparator.comparingInt(BlockPos::getY).thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ));
        Set<BlockPos> free = new HashSet<>(hubs);
        for (BlockPos pos : order) {
            if (!free.contains(pos)) continue;
            for (int[] shape : SHAPES) {
                Box box = new Box(pos, shape[0], shape[1]);
                List<BlockPos> blocks = box.positions();
                if (!free.containsAll(blocks)) continue;
                for (BlockPos block : blocks) {
                    free.remove(block);
                    form(level, block, box);
                }
                break; // 1x1x1 always fits
            }
        }
    }

    /** Gives the block its place in the box (clients only: neighbours, turrets included, are not told). */
    private static void form(Level level, BlockPos pos, Box box) {
        BlockState state = level.getBlockState(pos);
        BlockState formed = state.setValue(FeedHubBlock.X, Part.of(pos.getX() - box.min().getX(), box.width()))
                .setValue(FeedHubBlock.Y, Part.of(pos.getY() - box.min().getY(), box.height()))
                .setValue(FeedHubBlock.Z, Part.of(pos.getZ() - box.min().getZ(), box.width()));
        if (formed != state) level.setBlock(pos, formed, Block.UPDATE_CLIENTS);
    }

    /** A hub in a loaded chunk (never loads one). */
    static boolean isHub(Level level, BlockPos pos) {
        return level.isLoaded(pos) && level.getBlockState(pos).getBlock() instanceof FeedHubBlock;
    }

    /** Steps from {@code pos} back to its structure's low end on {@code axis}. */
    private static int back(Level level, BlockPos pos, BlockState state, Direction.Axis axis) {
        int steps = 0;
        while (steps < MAX_WIDTH - 1 && FeedHubBlock.part(state, axis).ordinal() >= Part.MIDDLE.ordinal()) {
            BlockPos next = pos.relative(axis, -steps - 1);
            if (!isHub(level, next)) break;
            state = level.getBlockState(next);
            steps++;
        }
        return steps;
    }

    /** Blocks from {@code min} to its structure's high end on {@code axis}. */
    private static int length(Level level, BlockPos min, Direction.Axis axis) {
        if (!isHub(level, min) || FeedHubBlock.part(level.getBlockState(min), axis) == Part.ALONE) return 1;
        int length = 1;
        while (length < MAX_WIDTH && isHub(level, min.relative(axis, length))) {
            length++;
            if (FeedHubBlock.part(level.getBlockState(min.relative(axis, length - 1)), axis) == Part.HIGH) break;
        }
        return length;
    }
}
```

- [ ] **Step 4: Run, verify they pass**

Run: `./gradlew runGameTestServer --offline`
Expected: 69 tests pass (67 + `feedHubForms`, `feedHubSplits`). The blockstate's `""` variant matches every state, so merged hubs still show the lone-hub model until Task 4.

- [ ] **Step 5: No commit** (the user commits on "commit").

---

### Task 2: Shared storage and feeds (incl. large bases)

**Files:**
- Modify: `src/main/java/dev/bastion/turret/FeedHubBlockEntity.java` (rewrite of the distribution part)
- Modify: `src/main/java/dev/bastion/config/BastionConfig.java`
- Test: `src/main/java/dev/bastion/gametest/TurretFeedHubTests.java`

**Interfaces:**
- Consumes: Task 1 `FeedHubStructure.Box`, `isHub`.
- Produces: `FeedHubBlockEntity.group()` → `FeedHubBlockEntity.Group` with `box()`, `members()` (`List<FeedHubBlockEntity>`, slot order), `items()` (`IItemHandlerModifiable`, all slots), `feeds()` (`List<Feed>`); `FeedHubBlockEntity.storage()` (`IItemHandlerModifiable`, structure view); `FeedHubBlockEntity.takes(ItemStack)`; `FeedHubBlockEntity.feeds()` (kept for the screen); `record Feed(TurretBaseBlockEntity turret, IItemHandler ammo, @Nullable IEnergyStorage energy)`; `buffer()` unchanged; config `BastionConfig.FEED_HUB_ITEMS_PER_TICK`.

- [ ] **Step 1: Write the failing tests** (helpers + 4 tests; imports `LargeTurretBaseBlock`, `ItemEntity`, `AABB`)

```java
    private static IItemHandler items(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockEntity(pos).getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElseThrow(IllegalStateException::new);
    }

    private static TurretBaseBlockEntity large(GameTestHelper helper, BlockPos min, Item weapon) {
        LargeTurretBaseBlock.placeAt(helper.getLevel(), helper.absolutePos(min), BastionBlocks.LARGE_TURRET_BASE.get());
        TurretBaseBlockEntity base = (TurretBaseBlockEntity) helper.getBlockEntity(min);
        base.inventory().setStackInSlot(TurretInventory.WEAPON, new ItemStack(weapon));
        return base;
    }

    /**
     * Slots add up (2x2x1: 36, 3x3x3: 243), any block reaches them all, and a handler taken before the hubs merged
     * (a pipe's cached capability) sees the merged structure.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "feedHubSharesStorage")
    public static void feedHubSharesStorage(GameTestHelper helper) {
        BlockPos small = new BlockPos(2, 2, 1), big = new BlockPos(5, 2, 1); // the gun goes on small.west() (x 1)
        helper.setBlock(small, BastionBlocks.FEED_HUB.get());
        IItemHandler early = items(helper, small);
        hubs(helper, small, 2, 1);
        hubs(helper, big, 3, 3);
        base(helper, small.west(), Direction.WEST, BastionItems.GUN_TURRET.get());
        helper.runAfterDelay(2, () -> helper.succeedWhen(() -> {
            helper.assertTrue(early.getSlots() == 36, "early handler sees " + early.getSlots() + " slots");
            helper.assertTrue(items(helper, big.offset(2, 2, 2)).getSlots() == 243, "3x3x3 slots: " + items(helper, big.offset(2, 2, 2)).getSlots());
            IItemHandler far = items(helper, small.offset(1, 0, 1)); // not the block the gun sits on
            helper.assertTrue(ItemHandlerHelper.insertItem(far, new ItemStack(BastionItems.KINETIC_ROUNDS.get(), 64), true).isEmpty(),
                    "the far block refused rounds the gun on its structure fires");
        }));
    }

    /** A large base on all four blocks of a 2x2x1 gets its missiles; one standing half on another 2x2x1 gets none. */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "feedHubFeedsLargeBase")
    public static void feedHubFeedsLargeBase(GameTestHelper helper) {
        BlockPos fed = new BlockPos(1, 2, 1), half = new BlockPos(6, 2, 1);
        hubs(helper, fed, 2, 1);
        hubs(helper, half, 2, 1);
        TurretBaseBlockEntity on = large(helper, fed.above(), BastionItems.MISSILE_LAUNCHER_TURRET.get());
        TurretBaseBlockEntity off = large(helper, half.above().east(), BastionItems.MISSILE_LAUNCHER_TURRET.get());
        helper.runAtTickTime(3, () -> {
            helper.assertTrue(ItemHandlerHelper.insertItem(items(helper, fed), new ItemStack(BastionItems.MISSILES.get(), 8), false).isEmpty(),
                    "refused missiles for the large base on top");
            helper.assertTrue(ItemHandlerHelper.insertItem(items(helper, half), new ItemStack(BastionItems.MISSILES.get(), 8), false).getCount() == 8,
                    "took missiles for a base standing half off the structure");
        });
        helper.runAfterDelay(4, () -> helper.succeedWhen(() -> {
            helper.assertTrue(ammo(on, BastionItems.MISSILES.get()) == 8, "the large base got " + ammo(on, BastionItems.MISSILES.get()));
            helper.assertTrue(ammo(off, BastionItems.MISSILES.get()) == 0, "fed the base standing half off");
        }));
    }

    /** Breaking one block of a 2x2x1 drops only that block's slots; the others keep theirs and stand alone. */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = "feedHubDropsOwnShare")
    public static void feedHubDropsOwnShare(GameTestHelper helper) {
        BlockPos min = new BlockPos(4, 2, 2);
        hubs(helper, min, 2, 1);
        helper.runAtTickTime(3, () -> {
            ((FeedHubBlockEntity) helper.getBlockEntity(min)).buffer().setStackInSlot(0, new ItemStack(BastionItems.KINETIC_ROUNDS.get(), 10));
            ((FeedHubBlockEntity) helper.getBlockEntity(min.offset(1, 0, 1))).buffer().setStackInSlot(0, new ItemStack(BastionItems.SCATTER_SHELLS.get(), 3));
            helper.destroyBlock(min.offset(1, 0, 1));
        });
        helper.runAfterDelay(4, () -> helper.succeedWhen(() -> {
            int shells = 0, rounds = 0;
            for (ItemEntity drop : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(min)).inflate(3))) {
                if (drop.getItem().is(BastionItems.SCATTER_SHELLS.get())) shells += drop.getItem().getCount();
                if (drop.getItem().is(BastionItems.KINETIC_ROUNDS.get())) rounds += drop.getItem().getCount();
            }
            helper.assertTrue(shells == 3 && rounds == 0, "dropped " + shells + " shells, " + rounds + " rounds");
            helper.assertTrue(((FeedHubBlockEntity) helper.getBlockEntity(min)).buffer().getStackInSlot(0).getCount() == 10, "the rest lost its rounds");
            assertBox(helper, min, 1, 1);
        }));
    }

    /** Breaking a 2x2x2's lowest block re-forms its top layer as a 2x2x1 whose new lowest block feeds the gun on top. */
    @GameTest(template = ARENA, timeoutTicks = 80, batch = "feedHubNewControllerFeeds")
    public static void feedHubNewControllerFeeds(GameTestHelper helper) {
        BlockPos min = new BlockPos(4, 2, 2);
        hubs(helper, min, 2, 2);
        TurretBaseBlockEntity gun = base(helper, min.offset(1, 2, 1), Direction.UP, BastionItems.GUN_TURRET.get());
        helper.runAtTickTime(3, () -> helper.destroyBlock(min));
        helper.runAtTickTime(5, () -> {
            assertBox(helper, min.above(), 2, 1);
            helper.assertTrue(ItemHandlerHelper.insertItem(items(helper, min.above()), new ItemStack(BastionItems.KINETIC_ROUNDS.get(), 16), false).isEmpty(),
                    "the re-formed top layer refused rounds");
        });
        helper.runAfterDelay(6, () -> helper.succeedWhen(() ->
                helper.assertTrue(ammo(gun, BastionItems.KINETIC_ROUNDS.get()) == 16, "the gun got " + ammo(gun, BastionItems.KINETIC_ROUNDS.get()))));
    }
```

- [ ] **Step 2: Run, verify they fail**

Run: `./gradlew runGameTestServer --offline`
Expected: `feedHubSharesStorage` fails (`early handler sees 9 slots`), `feedHubFeedsLargeBase` fails (missiles refused), the other two fail on feeding/structure.

- [ ] **Step 3: Implement**

`BastionConfig` (after `MORTAR_BLOCK_FIRE`; declare the fields alongside the others):

```java
        FEED_HUB_ENERGY_CAPACITY = b
                .comment("FE each Feed Hub block stores; a merged structure holds this times its blocks.")
                .defineInRange("feedHubEnergyCapacity", 50000, 0, Integer.MAX_VALUE);
        FEED_HUB_MAX_INPUT = b
                .comment("Most FE each Feed Hub block accepts per tick; a merged structure takes this times its blocks.")
                .defineInRange("feedHubMaxInput", 4000, 0, Integer.MAX_VALUE);
        FEED_HUB_ITEMS_PER_TICK = b
                .comment("Ammo items each Feed Hub block moves into its turrets per tick.")
                .defineInRange("feedHubItemsPerTick", 8, 1, 64);
```

`FeedHubBlockEntity` (class doc updated to mention merging; `buffer`'s `isItemValid` becomes `takes(stack)`; `MOVES_PER_TICK` goes away):

```java
    private final IItemHandlerModifiable storage = new StorageView();
    private LazyOptional<IItemHandler> itemCapability = LazyOptional.of(() -> storage);
    /** This tick's structure, shared with the controller's (null until first asked). */
    @Nullable
    private Group group;

    /** A turret mounted on the structure: the handler that reaches its ammo slots and its FE storage (null if none). */
    public record Feed(TurretBaseBlockEntity turret, IItemHandler ammo, @Nullable IEnergyStorage energy) {
        static Optional<Feed> of(TurretBaseBlockEntity turret, Direction side) {
            return turret.getCapability(ForgeCapabilities.ITEM_HANDLER, side).resolve()
                    .map(ammo -> new Feed(turret, ammo, turret.getCapability(ForgeCapabilities.ENERGY, side).resolve().orElse(null)));
        }

        /** Its weapon fires this (a turret without a weapon takes nothing from the hub). */
        public boolean takes(ItemStack stack) {
            return turret.inventory().weaponData() != null && turret.inventory().isAmmo(stack);
        }

        /** How much ammo for its weapon it holds now. */
        int stock() { /* unchanged */ }
    }

    /**
     * One structure as of one tick: its loaded blocks in slot order, their slots as one handler, the turrets on it.
     * Built by the structure's lowest block and shared by all; rebuilt the next tick or once a block is gone.
     */
    public static final class Group {
        private final FeedHubStructure.Box box;
        private final List<FeedHubBlockEntity> members = new ArrayList<>();
        private final IItemHandlerModifiable items;
        private final List<Feed> feeds;
        private final long tick;

        private Group(Level level, FeedHubStructure.Box box, FeedHubBlockEntity owner, long tick) {
            this.box = box;
            this.tick = tick;
            for (BlockPos pos : box.positions()) {
                if (pos.equals(owner.worldPosition)) members.add(owner);
                else if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof FeedHubBlockEntity hub) members.add(hub);
            }
            items = new CombinedInvWrapper(members.stream().map(hub -> hub.buffer).toArray(IItemHandlerModifiable[]::new));
            feeds = findFeeds(level, box, members);
        }

        public FeedHubStructure.Box box() { return box; }
        public List<FeedHubBlockEntity> members() { return members; }
        public IItemHandlerModifiable items() { return items; }
        public List<Feed> feeds() { return feeds; }

        private boolean stale(long now) {
            return tick != now || members.stream().anyMatch(BlockEntity::isRemoved);
        }

        /** Moves stored ammo one item at a time into the turret that takes it and holds the least of it. */
        private void feedAmmo(int budget) {
            int[] stock = null;
            for (int slot = 0; slot < items.getSlots() && budget > 0; slot++) {
                while (budget > 0 && !items.getStackInSlot(slot).isEmpty()) {
                    if (stock == null) stock = feeds.stream().mapToInt(Feed::stock).toArray();
                    ItemStack one = items.getStackInSlot(slot).copyWithCount(1);
                    int best = -1;
                    for (int i = 0; i < feeds.size(); i++) {
                        Feed feed = feeds.get(i);
                        if ((best < 0 || stock[i] < stock[best]) && feed.takes(one)
                                && ItemHandlerHelper.insertItem(feed.ammo(), one, true).isEmpty()) best = i;
                    }
                    if (best < 0) break; // nobody has room for this one now: it waits
                    ItemHandlerHelper.insertItem(feeds.get(best).ammo(), items.extractItem(slot, 1, false), false);
                    stock[best]++;
                    budget--;
                }
            }
        }
    }

    /** This hub's structure this tick; a block whose controller (lowest block) is not loaded runs alone. */
    public Group group() {
        long now = level.getGameTime();
        if (group == null || group.stale(now)) group = findGroup(now);
        return group;
    }

    private Group findGroup(long now) {
        FeedHubStructure.Box box = FeedHubStructure.Box.of(level, worldPosition, getBlockState());
        if (box.min().equals(worldPosition)) return new Group(level, box, this, now);
        // The controller's min is its own position, and each hop goes strictly down-left: no cycle even on odd states.
        if (level.isLoaded(box.min()) && level.getBlockEntity(box.min()) instanceof FeedHubBlockEntity controller) return controller.group();
        return new Group(level, FeedHubStructure.Box.single(worldPosition), this, now);
    }

    /** Standard bases on the structure's outer faces (back on it) and large bases standing with all four blocks on top. */
    private static List<Feed> findFeeds(Level level, FeedHubStructure.Box box, List<FeedHubBlockEntity> members) {
        List<Feed> feeds = new ArrayList<>();
        Set<TurretBaseBlockEntity> large = new HashSet<>();
        for (FeedHubBlockEntity member : members) {
            for (Direction side : Direction.values()) {
                BlockPos at = member.worldPosition.relative(side);
                if (box.contains(at) || !level.isLoaded(at)) continue;
                BlockState state = level.getBlockState(at);
                if (state.getBlock() instanceof LargeTurretBaseBlock) {
                    boolean onTop = side == Direction.UP && LargeTurretBaseBlock.footprint(LargeTurretBaseBlock.footprintMin(state, at)).stream()
                            .allMatch(p -> box.contains(p.below()));
                    TurretBaseBlockEntity core = onTop ? TurretBaseBlock.core(level, at) : null;
                    if (core != null && large.add(core)) Feed.of(core, Direction.DOWN).ifPresent(feeds::add);
                } else if (state.getBlock() instanceof TurretBaseBlock && state.getValue(TurretBaseBlock.FACING) == side
                        && level.getBlockEntity(at) instanceof TurretBaseBlockEntity turret) {
                    Feed.of(turret, side.getOpposite()).ifPresent(feeds::add);
                }
            }
        }
        return feeds;
    }

    /** The turrets this hub's structure feeds (the GUI lists their weapons). */
    public List<Feed> feeds() {
        return group().feeds;
    }

    /** Some armed turret on the structure fires this. */
    public boolean takes(ItemStack stack) {
        return level != null && group().feeds.stream().anyMatch(feed -> feed.takes(stack));
    }

    /** All the structure's slots, through this block (capability and GUI). */
    public IItemHandlerModifiable storage() {
        return storage;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, FeedHubBlockEntity hub) {
        Group group = hub.group();
        if (group.members.get(0) != hub) return; // the structure's lowest block runs it for all
        group.feedAmmo(BastionConfig.FEED_HUB_ITEMS_PER_TICK.get() * group.members.size());
    }

    /** Delegates to this tick's structure, so a handler cached by a pipe stays right when hubs merge or split. */
    private final class StorageView implements IItemHandlerModifiable {
        private IItemHandlerModifiable items() {
            return group().items;
        }

        @Override public int getSlots() { return items().getSlots(); }
        @Override public ItemStack getStackInSlot(int slot) { return items().getStackInSlot(slot); }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return items().insertItem(slot, stack, simulate); }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) { return items().extractItem(slot, amount, simulate); }
        @Override public int getSlotLimit(int slot) { return items().getSlotLimit(slot); }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return items().isItemValid(slot, stack); }
        @Override public void setStackInSlot(int slot, ItemStack stack) { items().setStackInSlot(slot, stack); }
    }
```

- [ ] **Step 4: Run, verify they pass**

Run: `./gradlew runGameTestServer --offline`
Expected: 73 pass, including the two old Feed Hub tests unchanged.

- [ ] **Step 5: No commit.**

---

### Task 3: Energy

**Files:**
- Modify: `src/main/java/dev/bastion/turret/FeedHubBlockEntity.java`
- Test: `src/main/java/dev/bastion/gametest/TurretFeedHubTests.java`

**Interfaces:**
- Consumes: Task 2 `Group`, `Feed.energy`, config `FEED_HUB_ENERGY_CAPACITY`, `FEED_HUB_MAX_INPUT`.
- Produces: `Group.energy()`, `Group.capacity()` (ints, capped at `Integer.MAX_VALUE`); ENERGY capability (receive-only) on every block; NBT `Energy`.

- [ ] **Step 1: Write the failing test** (import `BastionConfig`, `IEnergyStorage`)

```java
    /** A 2x2x1 stores 4 x the per-block FE, takes 4 x the per-block input per tick, and charges the Tesla Coil on top. */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = "feedHubPowersTesla")
    public static void feedHubPowersTesla(GameTestHelper helper) {
        BlockPos min = new BlockPos(4, 2, 2);
        hubs(helper, min, 2, 1);
        TurretBaseBlockEntity tesla = large(helper, min.above(), BastionItems.TESLA_TURRET.get());
        int input = 4 * BastionConfig.FEED_HUB_MAX_INPUT.get();
        helper.runAtTickTime(3, () -> {
            IEnergyStorage hub = helper.getBlockEntity(min.offset(1, 0, 1)).getCapability(ForgeCapabilities.ENERGY, Direction.EAST)
                    .orElseThrow(IllegalStateException::new);
            helper.assertTrue(hub.getMaxEnergyStored() == 4 * BastionConfig.FEED_HUB_ENERGY_CAPACITY.get(), "capacity " + hub.getMaxEnergyStored());
            helper.assertTrue(hub.receiveEnergy(Integer.MAX_VALUE, false) == input, "took more or less than its per-tick input");
            helper.assertTrue(hub.receiveEnergy(1, false) == 0, "took FE past its per-tick input");
        });
        helper.runAfterDelay(4, () -> helper.succeedWhen(() -> {
            IEnergyStorage hub = helper.getBlockEntity(min).getCapability(ForgeCapabilities.ENERGY, Direction.UP).orElseThrow(IllegalStateException::new);
            helper.assertTrue(tesla.energy() > 0, "the Tesla Coil got no FE");
            helper.assertTrue(tesla.energy() + hub.getEnergyStored() == input, "FE lost or made: " + tesla.energy() + " + " + hub.getEnergyStored());
        }));
    }
```

- [ ] **Step 2: Run, verify it fails**

Run: `./gradlew runGameTestServer --offline`
Expected: `IllegalStateException` (no ENERGY capability).

- [ ] **Step 3: Implement** (in `FeedHubBlockEntity`)

```java
    /** FE in this block; the structure fills and drains its blocks in slot order. */
    private int energy;
    private final IEnergyStorage energyStorage = new EnergyView();
    private LazyOptional<IEnergyStorage> energyCapability = LazyOptional.of(() -> energyStorage);
```

In `Group`:

```java
        /** FE accepted this tick (a Group lives one tick), against the structure's per-tick input. */
        private int received;

        public int energy() {
            long sum = 0;
            for (FeedHubBlockEntity member : members) sum += member.energy;
            return (int) Math.min(Integer.MAX_VALUE, sum);
        }

        public int capacity() {
            return (int) Math.min(Integer.MAX_VALUE, (long) members.size() * BastionConfig.FEED_HUB_ENERGY_CAPACITY.get());
        }

        private int receive(int max, boolean simulate) {
            long input = (long) members.size() * BastionConfig.FEED_HUB_MAX_INPUT.get() - received;
            int accepted = (int) Math.max(0, Math.min(Math.min(max, input), (long) capacity() - energy()));
            if (!simulate && accepted > 0) {
                received += accepted;
                fill(accepted);
            }
            return accepted;
        }

        private void fill(int amount) {
            int cap = BastionConfig.FEED_HUB_ENERGY_CAPACITY.get();
            for (FeedHubBlockEntity member : members) {
                int put = Math.min(amount, Math.max(0, cap - member.energy));
                if (put == 0) continue;
                member.energy += put;
                member.setChanged();
                amount -= put;
                if (amount == 0) return;
            }
        }

        private void drain(int amount) {
            for (FeedHubBlockEntity member : members) {
                int take = Math.min(amount, member.energy);
                if (take == 0) continue;
                member.energy -= take;
                member.setChanged();
                amount -= take;
                if (amount == 0) return;
            }
        }

        /** Hands stored FE to the energy turrets, the least charged first, each within its weapon's max_input. */
        private void feedEnergy() {
            int left = energy();
            if (left == 0) return;
            List<Feed> powered = feeds.stream().filter(feed -> feed.energy() != null && feed.energy().canReceive())
                    .sorted(Comparator.comparingDouble(feed -> feed.energy().getEnergyStored() / (double) Math.max(1, feed.energy().getMaxEnergyStored())))
                    .toList();
            for (Feed feed : powered) {
                int given = feed.energy().receiveEnergy(left, false);
                drain(given);
                left -= given;
                if (left == 0) return;
            }
        }
```

`serverTick` adds `group.feedEnergy();` after `feedAmmo`. Capability, NBT, view:

```java
    @Override
    public <C> LazyOptional<C> getCapability(Capability<C> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) return itemCapability.cast();
        if (cap == ForgeCapabilities.ENERGY) return energyCapability.cast();
        return super.getCapability(cap, side);
    }
    // invalidateCaps / reviveCaps: also energyCapability.

    // saveAdditional: if (energy > 0) tag.putInt("Energy", energy);   load: energy = tag.getInt("Energy");

    /** Receive-only FE for the whole structure through this block; the hub never hands FE back to cables. */
    private final class EnergyView implements IEnergyStorage {
        @Override public int receiveEnergy(int maxReceive, boolean simulate) { return group().receive(maxReceive, simulate); }
        @Override public int extractEnergy(int maxExtract, boolean simulate) { return 0; }
        @Override public int getEnergyStored() { return group().energy(); }
        @Override public int getMaxEnergyStored() { return group().capacity(); }
        @Override public boolean canExtract() { return false; }
        @Override public boolean canReceive() { return true; }
    }
```

- [ ] **Step 4: Run, verify it passes**

Run: `./gradlew runGameTestServer --offline`
Expected: 74 pass.

- [ ] **Step 5: No commit.**

---

### Task 4: Connected look (Create-vault style)

**Files:**
- Modify: `tools/gen_textures.py` (3 textures + TEXTURES entries)
- Create: `tools/gen_feed_hub_models.py`
- Generated: `assets/bastion/blockstates/feed_hub.json` (multipart), `models/block/feed_hub_panel.json`, `feed_hub_frame_{down,up,north,south,west,east}.json`, `feed_hub_glow_{...}.json`, `textures/block/feed_hub_{panel,edge,edge_glow}.png`

**Interfaces:**
- Consumes: Task 1 properties `x`/`y`/`z`.
- Produces: nothing other tasks call.

- [ ] **Step 1: Textures** (`gen_textures.py`, next to `feed_hub()`; add `"block/feed_hub_panel.png": feed_hub_panel, "block/feed_hub_edge.png": feed_hub_edge, "block/feed_hub_edge_glow.png": feed_hub_edge_glow` to TEXTURES)

```python
def feed_hub_panel():
    """Face of a merged Feed Hub (4a): plain gunmetal plating that tiles without seams; the frame is the edge overlay."""
    img = np.zeros((16, 16, 4))
    img[..., :3], img[..., 3] = GUNMETAL * 1.1 * noise((16, 16), 0.04, seed=93)[..., None], 1
    return img


def feed_hub_edge():
    """Frame strip of a merged Feed Hub along the texture's top; the models turn it onto each outer edge. Row 0 is left
    for the glow line (feed_hub_edge_glow), which is drawn last so the lines of two edges meet in the corner pixel."""
    img = np.zeros((16, 16, 4))
    rect(img, 0, 0, 16, 1, DARKSTEEL)
    rect(img, 0, 1, 16, 1, DARKSTEEL)
    rect(img, 0, 2, 16, 1, GUNMETAL * 1.35)  # bevel
    rect(img, 0, 3, 16, 1, GUNMETAL * 0.7)   # shadow onto the panel
    return img


def feed_hub_edge_glow():
    """Fullbright cyan line on the structure's outer edge (forge_data block_light 15)."""
    img = np.zeros((16, 16, 4))
    rect(img, 0, 0, 16, 1, rgb("4FD8FF"))
    return img
```

- [ ] **Step 2: Model generator** `tools/gen_feed_hub_models.py`

```python
#!/usr/bin/env python3
"""Feed Hub block models (PLAN "Feed Hub", 4a). The blockstate picks parts from the hub's place in its structure
(properties x, y, z = alone/low/middle/high): a lone hub keeps block/feed_hub; a merged one gets plain panels, then a
frame along every edge where its structure ends, then a cyan glow line on top of all frames. Faces between hubs are
culled (cullface), so the structure reads as one block. Frames and glow lines are full-face overlays with the strip on
the texture's top, turned onto the right edge with the face rotation.

    python3 tools/gen_feed_hub_models.py
"""
import json
from pathlib import Path

ASSETS = Path(__file__).resolve().parent.parent / "src/main/resources/assets/bastion"
FACES = ["down", "up", "north", "south", "west", "east"]
AXIS = {"down": "y", "up": "y", "north": "z", "south": "z", "west": "x", "east": "x"}
LOW_END = {"down", "north", "west"}
# Where each world direction lies on a face's texture with default UVs, as the clockwise rotation that turns the
# texture's top there: 0 top, 90 right, 180 bottom, 270 left (vanilla BlockElement default UVs).
ROTATION = {
    "north": {"up": 0, "west": 90, "down": 180, "east": 270},
    "south": {"up": 0, "east": 90, "down": 180, "west": 270},
    "west": {"up": 0, "south": 90, "down": 180, "north": 270},
    "east": {"up": 0, "north": 90, "down": 180, "south": 270},
    "up": {"north": 0, "east": 90, "south": 180, "west": 270},
    "down": {"south": 0, "east": 90, "north": 180, "west": 270},
}
MERGED = {"OR": [{"x": "!alone"}, {"y": "!alone"}, {"z": "!alone"}]}


def model(texture, faces, glow=False):
    element = {"from": [0, 0, 0], "to": [16, 16, 16], "faces": faces}
    if glow:
        element = {"from": [0, 0, 0], "to": [16, 16, 16], "forge_data": {"block_light": 15, "sky_light": 15}, "shade": False, "faces": faces}
    return {"parent": "minecraft:block/block", "render_type": "minecraft:cutout",
            "textures": {"particle": "bastion:block/feed_hub_panel", "t": texture}, "elements": [element]}


def edge(texture, end, glow=False):
    """The strip on every face that touches the structure's end `end` (one of FACES)."""
    faces = {f: {"texture": "#t", "cullface": f, "rotation": ROTATION[f][end]} for f in FACES if AXIS[f] != AXIS[end]}
    return model(texture, faces, glow)


def write(rel, data):
    path = ASSETS / rel
    path.write_text(json.dumps(data, indent=2) + "\n")
    print("wrote", rel)


if __name__ == "__main__":
    write("models/block/feed_hub_panel.json", model("bastion:block/feed_hub_panel", {f: {"texture": "#t", "cullface": f} for f in FACES}))
    parts = [{"when": {"x": "alone", "y": "alone", "z": "alone"}, "apply": {"model": "bastion:block/feed_hub"}},
             {"when": MERGED, "apply": {"model": "bastion:block/feed_hub_panel"}}]
    for kind, texture, glow in (("frame", "bastion:block/feed_hub_edge", False), ("glow", "bastion:block/feed_hub_edge_glow", True)):
        for end in FACES:  # all frames before all glow lines: later coplanar quads win
            write(f"models/block/feed_hub_{kind}_{end}.json", edge(texture, end, glow))
            at_end = "alone|low" if end in LOW_END else "alone|high"
            parts.append({"when": {"AND": [{AXIS[end]: at_end}, MERGED]}, "apply": {"model": f"bastion:block/feed_hub_{kind}_{end}"}})
    write("blockstates/feed_hub.json", {"multipart": parts})
```

- [ ] **Step 3: Generate and build**

Run: `cd tools && python3 gen_textures.py && python3 gen_feed_hub_models.py && cd .. && ./gradlew build --offline`
Expected: BUILD SUCCESSFUL; `git status` shows the 13 new model files, 3 new textures, the rewritten blockstate (other textures byte-identical: the generator is deterministic).

- [ ] **Step 4: Visual check** (scene from Task 6 Step 1 can be built first, or a quick build in creative): run `./gradlew runClient --offline -Pshowcase=feedhub -PshowcaseWorld=showcase_fx` (scratch world copy, see Task 6) and look at `run/screenshots/showcase_f_*.png` at full resolution:
  - lone hub unchanged; 2x2x1 and 3x3x2 show one frame + cyan line around each outer face, nothing between blocks;
  - if a frame sits on the wrong edge of side faces, the clockwise assumption is off: swap 90 and 270 in `ROTATION` and regenerate.
  Expected: no seams, glow lines meet in the corners.

- [ ] **Step 5: No commit.**

---

### Task 5: GUI with Summary and Storage tabs

**Files:**
- Rewrite: `src/main/java/dev/bastion/menu/FeedHubMenu.java`
- Rewrite: `src/main/java/dev/bastion/client/screen/FeedHubScreen.java`
- Modify: `tools/gen_textures.py` (`feed_hub_gui(plain=False)`, TEXTURES `"gui/feed_hub.png"`, `"gui/feed_hub_plain.png"`)
- Modify: `src/main/resources/assets/bastion/lang/en_us.json`, `id_id.json`

**Interfaces:**
- Consumes: Task 2 `hub.group()` (`items()`, `feeds()`, `box()`, `members()`), `hub.storage()`, `hub.takes()`; Task 3 `Group.energy()/capacity()`.
- Produces: `FeedHubMenu` constants `COLUMNS=9, ROWS=4, VISIBLE=36, STORAGE_X=8, STORAGE_Y=18, PLAYER_INV_X=8, PLAYER_INV_Y=112, TYPES=8`; `scroll()`, `maxScroll()`, `setScroll(int)` (client), `energy()`, `capacity()`, `ammo()` → `List<AmmoTotal>` with `record AmmoTotal(ItemStack item, int count)`, `hub()`, public `slotsVisible`.

- [ ] **Step 1: GUI texture** (replaces `feed_hub_gui`)

```python
FEED_HUB_W, FEED_HUB_H = 192, 194  # FeedHubScreen imageWidth / imageHeight


def feed_hub_gui(plain=False):
    """Feed Hub screen (PLAN "Feed Hub", 2b), 192 x 194. Storage tab: 9 x 4 slot grid (scrollbar drawn by the screen
    right of it), divider, inventory; sprites right of the panel: locked slot (192, 0), side tabs (192, 18) and active
    (192, 42). plain = Summary tab, which FeedHubScreen draws on. Slot frames sit 1 px outside the item (FeedHubMenu)."""
    img = np.zeros((256, 256, 4))
    w, h = FEED_HUB_W, FEED_HUB_H
    panel(img, w, h)
    rect(img, 8, 16, w - 16, 1, UI_ACCENT * 0.55)  # under the title
    if plain:
        return img
    for i in range(36):
        slot(img, 7 + i % 9 * 18, 17 + i // 9 * 18)
    rect(img, 8, 96, w - 16, 1, UI_EDGE)  # storage | player
    for row in range(3):
        for col in range(9):
            slot(img, 7 + col * 18, 111 + row * 18)
    for col in range(9):
        slot(img, 7 + col * 18, 169)
    slot(img, 192, 0, locked=True)
    tab(img, 192, 18, active=False)
    tab(img, 192, 42, active=True)
    return img
```

TEXTURES: `"gui/feed_hub.png": feed_hub_gui, "gui/feed_hub_plain.png": lambda: feed_hub_gui(plain=True)`.

- [ ] **Step 2: Menu** (full file)

```java
package dev.bastion.menu;

// imports: BastionBlocks, BastionMenus, FeedHubBlockEntity, BlockPos, BuiltInRegistries, FriendlyByteBuf, Mth, Inventory,
// Player, AbstractContainerMenu, ContainerData, ContainerLevelAccess, SimpleContainerData, Slot, Item, ItemStack,
// IItemHandler, IItemHandlerModifiable, ItemHandlerHelper, ItemStackHandler, SlotItemHandler, Nullable, java.util.*

/**
 * Feed Hub screen (PLAN "Feed Hub", 2b): Summary (size, turrets, ammo totals, FE; numbers over ContainerData) and
 * Storage, 4 rows of the structure's slots that scroll. The server's slots look into the structure from the scrolled
 * row (clickMenuButton sets it); the client's are a plain mirror of what the server sends, so a scroll never shows
 * stale client copies. Shift-click from the inventory fills the whole structure, not only the rows on screen.
 */
public class FeedHubMenu extends AbstractContainerMenu {
    public static final int COLUMNS = 9, ROWS = 4, VISIBLE = COLUMNS * ROWS;
    public static final int STORAGE_X = 8, STORAGE_Y = 18, PLAYER_INV_X = 8, PLAYER_INV_Y = 112;
    /** Ammo types the summary lists. */
    public static final int TYPES = 8;
    // Data slots, each int as two 16-bit halves (ContainerData goes out as shorts).
    private static final int DATA_ENERGY = 0, DATA_CAPACITY = 2, DATA_TYPES = 4, DATA_COUNT = DATA_TYPES + TYPES * 4;

    /** One ammo type in the structure and how much of it there is. */
    public record AmmoTotal(ItemStack item, int count) {
    }

    @Nullable
    private final FeedHubBlockEntity hub;
    private final ContainerLevelAccess access;
    private final ContainerData data;
    /** Where shift-clicked items go: the structure on the server, the visible rows on the client (the server's result syncs back). */
    private final IItemHandler storage;
    /** First storage row shown. */
    private int scroll;
    /** Client: false on the Summary tab, which hides every slot. */
    public boolean slotsVisible = true;

    public FeedHubMenu(int id, Inventory playerInventory, FriendlyByteBuf buf) {
        this(id, playerInventory, buf.readBlockPos());
    }

    private FeedHubMenu(int id, Inventory playerInventory, BlockPos pos) {
        this(id, playerInventory, playerInventory.player.level().getBlockEntity(pos) instanceof FeedHubBlockEntity hub ? hub : null, pos);
    }

    public FeedHubMenu(int id, Inventory playerInventory, FeedHubBlockEntity hub) {
        this(id, playerInventory, hub, hub.getBlockPos());
    }

    private FeedHubMenu(int id, Inventory playerInventory, @Nullable FeedHubBlockEntity hub, BlockPos pos) {
        super(BastionMenus.FEED_HUB.get(), id);
        this.hub = hub;
        this.access = ContainerLevelAccess.create(playerInventory.player.level(), pos);
        IItemHandlerModifiable shown;
        if (hub != null && !playerInventory.player.level().isClientSide) {
            storage = hub.storage();
            shown = new Window(hub.storage());
            data = new Summary(hub);
        } else {
            ItemStackHandler mirror = new ItemStackHandler(VISIBLE) {
                @Override
                public boolean isItemValid(int slot, ItemStack stack) {
                    return hub != null && hub.takes(stack); // refuses on the client what the server would refuse
                }
            };
            storage = mirror;
            shown = mirror;
            data = new SimpleContainerData(DATA_COUNT);
        }
        for (int i = 0; i < VISIBLE; i++) addSlot(new StorageSlot(shown, i, STORAGE_X + i % COLUMNS * 18, STORAGE_Y + i / COLUMNS * 18));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new PlayerSlot(playerInventory, col + row * 9 + 9, PLAYER_INV_X + col * 18, PLAYER_INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) addSlot(new PlayerSlot(playerInventory, col, PLAYER_INV_X + col * 18, PLAYER_INV_Y + 58));
        addDataSlots(data);
    }

    @Nullable
    public FeedHubBlockEntity hub() {
        return hub;
    }

    public int scroll() {
        return scroll;
    }

    /** Slots in the structure (0 if its block entity is not loaded here). */
    private int totalSlots() {
        return hub == null ? 0 : hub.group().items().getSlots();
    }

    public int maxScroll() {
        return Math.max(0, Mth.positiveCeilDiv(totalSlots(), COLUMNS) - ROWS);
    }

    /** Client: moves the scrollbar at once; the screen then tells the server with the same row as a button id. */
    public void setScroll(int row) {
        scroll = Mth.clamp(row, 0, maxScroll());
    }

    @Override
    public boolean clickMenuButton(Player player, int row) {
        setScroll(row);
        return true; // the server then broadcasts the slots of the new rows
    }

    public int energy() {
        return read(DATA_ENERGY);
    }

    public int capacity() {
        return read(DATA_CAPACITY);
    }

    public List<AmmoTotal> ammo() {
        List<AmmoTotal> ammo = new ArrayList<>();
        for (int k = 0; k < TYPES; k++) {
            int id = read(DATA_TYPES + k * 4);
            if (id > 0) ammo.add(new AmmoTotal(new ItemStack(BuiltInRegistries.ITEM.byId(id - 1)), read(DATA_TYPES + k * 4 + 2)));
        }
        return ammo;
    }

    private int read(int index) {
        return data.get(index) & 0xFFFF | (data.get(index + 1) & 0xFFFF) << 16;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < VISIBLE) {
            if (!moveItemStackTo(stack, VISIBLE, slots.size(), true)) return ItemStack.EMPTY;
            if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
            else slot.setChanged();
            return original;
        }
        ItemStack rest = ItemHandlerHelper.insertItem(storage, stack, false); // refuses what no mounted turret takes
        if (rest.getCount() == stack.getCount()) return ItemStack.EMPTY;
        slot.setByPlayer(rest);
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, BastionBlocks.FEED_HUB.get());
    }

    /** Hidden on the Summary tab and past the structure's last slot. */
    private class StorageSlot extends SlotItemHandler {
        StorageSlot(IItemHandler handler, int index, int x, int y) {
            super(handler, index, x, y);
        }

        @Override
        public boolean isActive() {
            return slotsVisible && scroll * COLUMNS + getSlotIndex() < totalSlots();
        }
    }

    private class PlayerSlot extends Slot {
        PlayerSlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean isActive() {
            return slotsVisible;
        }
    }

    /** Server: the visible rows of the structure's slots, from the scrolled row on. */
    private final class Window implements IItemHandlerModifiable {
        private final IItemHandlerModifiable all;

        Window(IItemHandlerModifiable all) {
            this.all = all;
        }

        private int index(int slot) {
            return scroll * COLUMNS + slot;
        }

        private boolean exists(int slot) {
            return index(slot) < all.getSlots();
        }

        @Override public int getSlots() { return VISIBLE; }
        @Override public ItemStack getStackInSlot(int slot) { return exists(slot) ? all.getStackInSlot(index(slot)) : ItemStack.EMPTY; }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return exists(slot) ? all.insertItem(index(slot), stack, simulate) : stack; }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) { return exists(slot) ? all.extractItem(index(slot), amount, simulate) : ItemStack.EMPTY; }
        @Override public int getSlotLimit(int slot) { return exists(slot) ? all.getSlotLimit(index(slot)) : 0; }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return exists(slot) && all.isItemValid(index(slot), stack); }
        @Override public void setStackInSlot(int slot, ItemStack stack) { if (exists(slot)) all.setStackInSlot(index(slot), stack); }
    }

    /** Server: the summary numbers, worked out once per tick. */
    private static final class Summary implements ContainerData {
        private final FeedHubBlockEntity hub;
        private final int[] values = new int[DATA_COUNT];
        private long tick = -1;

        Summary(FeedHubBlockEntity hub) {
            this.hub = hub;
        }

        @Override
        public int get(int index) {
            long now = hub.getLevel().getGameTime();
            if (now != tick) {
                tick = now;
                refresh();
            }
            return values[index];
        }

        private void refresh() {
            FeedHubBlockEntity.Group group = hub.group();
            put(DATA_ENERGY, group.energy());
            put(DATA_CAPACITY, group.capacity());
            Map<Item, Integer> totals = new HashMap<>();
            IItemHandler items = group.items();
            for (int i = 0; i < items.getSlots(); i++) {
                ItemStack stack = items.getStackInSlot(i);
                if (!stack.isEmpty()) totals.merge(stack.getItem(), stack.getCount(), Integer::sum);
            }
            List<Map.Entry<Item, Integer>> most = totals.entrySet().stream()
                    .sorted(Map.Entry.<Item, Integer>comparingByValue().reversed()).limit(TYPES).toList();
            for (int k = 0; k < TYPES; k++) {
                boolean shown = k < most.size();
                put(DATA_TYPES + k * 4, shown ? BuiltInRegistries.ITEM.getId(most.get(k).getKey()) + 1 : 0);
                put(DATA_TYPES + k * 4 + 2, shown ? most.get(k).getValue() : 0);
            }
        }

        private void put(int index, int value) {
            values[index] = value & 0xFFFF;
            values[index + 1] = value >>> 16;
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    }
}
```

- [ ] **Step 3: Screen** (full rewrite; layout numbers are a first pass, tuned from the scene screenshot)

```java
/** Feed Hub GUI (PLAN "Feed Hub", 2b): side tabs Summary and Storage (scrolling 9 x 4 grid of the structure's slots). */
public class FeedHubScreen extends AbstractContainerScreen<FeedHubMenu> {
    private static final ResourceLocation TEXTURE = Bastion.id("textures/gui/feed_hub.png");
    private static final ResourceLocation PLAIN = Bastion.id("textures/gui/feed_hub_plain.png");
    // Sprites inside TEXTURE, see tools/gen_textures.py feed_hub_gui().
    private static final int LOCKED_U = 192, TAB_U = 192, TAB_V = 18, TAB_ACTIVE_V = 42;
    private static final int TEXT = 0xD8E2EC, MUTED = 0x7F8C9A, ACCENT = 0x9FC4E8;
    private static final int TAB_SIZE = 24, TAB_X = -23, TAB_Y = 8, TAB_GAP = 26;
    // Storage: scrollbar right of the grid.
    private static final int BAR_X = 174, BAR_Y = FeedHubMenu.STORAGE_Y - 1, BAR_W = 10, BAR_H = FeedHubMenu.ROWS * 18, KNOB_H = 15;
    // Summary.
    private static final int PAD = 10, ICONS_Y = 46, ICONS_PER_ROW = 6, MAX_ICONS = 12, ENERGY_Y = 90, AMMO_Y = 112;

    private enum Tab {SUMMARY, STORAGE}

    private Tab tab = Tab.SUMMARY;
    private boolean dragging;

    public FeedHubScreen(FeedHubMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = 192;
        imageHeight = 194;
        inventoryLabelY = FeedHubMenu.PLAYER_INV_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        menu.slotsVisible = tab == Tab.STORAGE;
    }

    // render(): renderBackground, super.render, renderTabs, weapon-icon tooltips on SUMMARY, renderTooltip (pattern of the
    // old screen + TurretScreen.renderTabs; tab icons: FEED_HUB item for Summary, KINETIC_ROUNDS for Storage;
    // tooltips "gui.bastion.feed_hub.tab.summary/storage").

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(tab == Tab.STORAGE ? TEXTURE : PLAIN, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        if (tab == Tab.SUMMARY) {
            List<ItemStack> weapons = weapons();
            for (int i = 0; i < weapons.size(); i++) graphics.renderItem(weapons.get(i), leftPos + PAD + i % ICONS_PER_ROW * 20, topPos + ICONS_Y + i / ICONS_PER_ROW * 20);
            frame(graphics, PAD, ENERGY_Y + 12, imageWidth - 2 * PAD, 8);
            int fill = menu.capacity() == 0 ? 0 : (int) ((imageWidth - 2 * PAD - 2) * (long) menu.energy() / menu.capacity());
            graphics.fill(leftPos + PAD + 1, topPos + ENERGY_Y + 13, leftPos + PAD + 1 + fill, topPos + ENERGY_Y + 19, 0xFF86A8FF);
            List<FeedHubMenu.AmmoTotal> ammo = menu.ammo();
            for (int i = 0; i < ammo.size(); i++) graphics.renderItem(ammo.get(i).item(), leftPos + PAD + i / 4 * 88, topPos + AMMO_Y + 12 + i % 4 * 18);
            return;
        }
        for (int i = 0; i < FeedHubMenu.VISIBLE; i++) { // rows past the structure's last slot: hatched
            Slot slot = menu.slots.get(i);
            if (!slot.isActive()) graphics.blit(TEXTURE, leftPos + slot.x - 1, topPos + slot.y - 1, LOCKED_U, 0, 18, 18);
        }
        frame(graphics, BAR_X, BAR_Y, BAR_W, BAR_H);
        int max = menu.maxScroll();
        int knobY = BAR_Y + 1 + (max == 0 ? 0 : (BAR_H - 2 - KNOB_H) * menu.scroll() / max);
        graphics.fill(leftPos + BAR_X + 1, topPos + knobY, leftPos + BAR_X + BAR_W - 1, topPos + knobY + KNOB_H, max == 0 ? 0xFF3C4550 : 0xFF9FC4E8);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        fit(graphics, title, 8, 6, imageWidth - 16, TEXT);
        if (tab == Tab.STORAGE) {
            fit(graphics, playerInventoryTitle, 8, inventoryLabelY, imageWidth - 16, MUTED);
            return;
        }
        FeedHubBlockEntity hub = menu.hub();
        if (hub == null) return;
        FeedHubStructure.Box box = hub.group().box();
        fit(graphics, Component.translatable("gui.bastion.feed_hub.size", box.size(), box.blocks()), PAD, 22, imageWidth - 2 * PAD, TEXT);
        int turrets = hub.feeds().size();
        fit(graphics, turrets == 0 ? Component.translatable("gui.bastion.feed_hub.none") : Component.translatable("gui.bastion.feed_hub.turrets", turrets),
                PAD, 34, imageWidth - 2 * PAD, MUTED);
        fit(graphics, Component.translatable("gui.bastion.feed_hub.energy"), PAD, ENERGY_Y, 60, MUTED);
        Component fe = Component.translatable("gui.bastion.feed_hub.fe", String.format("%,d", menu.energy()), String.format("%,d", menu.capacity()));
        fit(graphics, fe, imageWidth - PAD - Math.min(font.width(fe), 110), ENERGY_Y, 110, TEXT);
        fit(graphics, Component.translatable("gui.bastion.feed_hub.ammo"), PAD, AMMO_Y, 80, MUTED);
        List<FeedHubMenu.AmmoTotal> ammo = menu.ammo();
        if (ammo.isEmpty()) fit(graphics, Component.translatable("gui.bastion.feed_hub.empty"), PAD, AMMO_Y + 16, 80, TEXT);
        for (int i = 0; i < ammo.size(); i++) {
            FeedHubMenu.AmmoTotal total = ammo.get(i);
            fit(graphics, Component.literal(String.format("%,d ", total.count())).append(total.item().getHoverName()),
                    PAD + 18 + i / 4 * 88, AMMO_Y + 16 + i % 4 * 18, 66, TEXT);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (tab == Tab.STORAGE && menu.maxScroll() > 0) {
            scrollTo(menu.scroll() - (int) Math.signum(delta));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    // mouseClicked: tabs (switchTab → tab = t; rebuildWidgets()), then on STORAGE a click inside the bar starts
    // dragging and jumps: scrollTo(rowAt(mouseY)). mouseDragged while dragging: scrollTo(rowAt(mouseY)).
    // mouseReleased: dragging = false. hasClickedOutside: false over a tab (as TurretScreen).

    private int rowAt(double mouseY) {
        double t = (mouseY - topPos - BAR_Y - 1 - KNOB_H / 2.0) / (BAR_H - 2 - KNOB_H);
        return (int) Math.round(Mth.clamp(t, 0, 1) * menu.maxScroll());
    }

    private void scrollTo(int row) {
        int before = menu.scroll();
        menu.setScroll(row);
        if (menu.scroll() != before) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, menu.scroll());
    }

    /** The weapon of each armed turret on the structure (at most 12). */
    private List<ItemStack> weapons() { /* as today, limit(MAX_ICONS) */ }

    // frame(): as TurretScreen.frame (FlatButton.EDGE / DARK). fit(): unchanged.
}
```

- [ ] **Step 4: Lang** — en_us / id_id (replace the tooltip, keep `turrets` and `none`):

| key | en_us | id_id |
|---|---|---|
| `tooltip.bastion.feed_hub` | Mount turret bases on its faces, large bases on top of a 2x2 or wider. Ammo and FE piped in go to the turrets that use them, the emptiest first. Hubs placed together merge, up to 3x3x3, and share slots and FE | Pasang turret base di sisi-sisinya, base besar di atas hub 2x2 atau lebih. Peluru dan FE yang masuk diteruskan ke turret yang memakainya, yang paling kosong dulu. Hub yang ditaruh bersebelahan menyatu sampai 3x3x3 dan berbagi slot serta FE |
| `gui.bastion.feed_hub.tab.summary` | Summary | Ringkasan |
| `gui.bastion.feed_hub.tab.storage` | Storage | Storage |
| `gui.bastion.feed_hub.size` | Size: %s (%s blocks) | Ukuran: %s (%s blok) |
| `gui.bastion.feed_hub.energy` | Energy | Energi |
| `gui.bastion.feed_hub.fe` | %s / %s FE | %s / %s FE |
| `gui.bastion.feed_hub.ammo` | Ammo | Peluru |
| `gui.bastion.feed_hub.empty` | Empty | Kosong |

- [ ] **Step 5: Generate, build, test**

Run: `cd tools && python3 gen_textures.py && cd .. && ./gradlew build --offline && ./gradlew runGameTestServer --offline`
Expected: BUILD SUCCESSFUL, 74 tests pass.

- [ ] **Step 6: Manual check (Review Focus 5)** in the Task 6 scene or by hand: open a 3x3x3 with a hopper feeding it, scroll with the wheel and the bar while items arrive. Expected: every row shows its own items after at most one tick; a lone hub shows 1 open row and 3 hatched rows; Summary numbers match the Storage contents.

- [ ] **Step 7: No commit.**

---

### Task 6: Showcase scene, docs, final verification

**Files:**
- Modify: `src/dev/java/dev/bastion/dev/FeedHubShowcase.java`
- Modify: `docs/TESTING.md`, `docs/release/DESCRIPTION.md`, `docs/release/DESCRIPTION.curseforge.html`, `docs/release/UPLOAD.md`, `docs/HANDOFF.md`

- [ ] **Step 1: Scene** — `build()` keeps the lone hub on its post with four turrets (unchanged at `HUB`) and adds, on the same lit platform (enlarge the cleared area to x/z -10..10):
  - at `ORIGIN.offset(-6, 0, -1)`: a 2x2x1 (`hubs` loop with `level.setBlock(..., 3)`), a Large Turret Base with a Tesla Coil on top (`LargeTurretBaseBlock.placeAt`), guns on two side faces;
  - at `ORIGIN.offset(4, 0, -1)`: a 3x3x2 with five standard bases (weapons: gun, machine gun, shotgun, sniper, flamethrower) on its sides and top;
  - FE pushed into each multiblock through its ENERGY capability (100k) and 20 stacks of kinetic rounds + 2 of scatter shells into the 3x3x2.
  Shots (by day, then night like today): `f_day` (all three), `f_multi` (close on the 3x3x2: frame + glow lines), `f_tesla` (2x2x1 + Tesla), `f_night`, then the GUI of the 3x3x2: `f_gui_summary`, click the Storage tab via `screen.mouseClicked` on the tab position → `f_gui_storage`, `mouseScrolled(-1)` → `f_gui_scrolled`.

  Run (scratch world, never `showcase`):
  ```bash
  cp -R "run/saves/New World" run/saves/showcase_fx
  ./gradlew runClient --offline -Pshowcase=feedhub -PshowcaseWorld=showcase_fx
  rm -rf run/saves/showcase_fx
  ```
  Expected: the screenshots in `run/screenshots/showcase_f_*.png` show seamless multiblocks with frames and glow on the outer edges only, the Tesla charged lamp, both tabs readable with no overlapping text. Fix and rerun until they do.

- [ ] **Step 2: Full verification**

Run: `./gradlew build --offline && ./gradlew runGameTestServer --offline`
Expected: BUILD SUCCESSFUL; 74 tests pass (67 old + 7 new).

- [ ] **Step 3: Docs**
  - `docs/TESTING.md`: Feed Hub section gets a "Multiblock + energy" checklist (forming each shape, split, large base on top, FE into Tesla/Railgun, both GUI tabs + scroll, break one block drops its share, world reload keeps slots/FE/shape).
  - `docs/release/DESCRIPTION.md` Feed Hub paragraph: merging up to 3x3x3, shared slots + FE, large bases on top; regenerate `DESCRIPTION.curseforge.html` the documented way (HANDOFF §6).
  - `docs/release/UPLOAD.md`: extend the 0.1.0-beta.5 changelog block (version stays beta.4 in `gradle.properties` until the user asks for the release).
  - `docs/HANDOFF.md`: NOW section, test count 74, "Feed Hub multiblock" entry, Review Focus notes.
- [ ] **Step 4: Report** — Indonesian checklist for the user's in-game test; no commit until "commit".
