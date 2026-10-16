# Control Hub Cube + Hub Connector Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the finished (uncommitted) 3x3 Turret Control Hub into a 3x3x3 cube with the Interface at eye level and the satellite on top, and make turrets join a hub only through a Hub Connector item linked to it and put in a new Connector slot on every turret.

**Architecture:** `ControlHubStructure` keeps being the single layout check, now over 26 cube positions (bottom → top, front → back, left → right) with the satellite two blocks above the cube's middle block; casings and ports gain a `y` part next to `x`/`z` (FeedHubBlock's parts, as the 3x3x3 Feed Hub), so the Feed Hub edge models outline the cube. A new `HubConnectorItem` stores the Interface's position + dimension in its NBT; `TurretInventory` gains slot `CONNECTOR` (index 14, size 15, old 14-slot saves still load); `HubInterfaceBlockEntity.visibleTurrets`/`reachable` (and with them the roster, remote turret GUIs and batch edits) additionally require a connector linked to that hub.

**Tech Stack:** Forge 1.20.1 (47.4.26, parchment 2023.09.03), Java 17, GeckoLib 4, Forge GameTests, Python 3 generators in `tools/` (numpy, pillow).

**Spec:** `docs/PLAN.md` → section 8, "Turret Control Hub" (revision approved by the user 2026-10-10 15:10: "Revisi 15:01", "Struktur", "Hub Connector", "Turret yang tampil", "Aturan server", "Resep", "Selesai jika"). Previous plan (the 3x3 version, done): `docs/superpowers/plans/2026-10-10-turret-control-hub.md`.

## Global Constraints

- Cube 3x3x3: the Hub Interface in the middle of one side of the **middle layer**, facing out (towards the player who placed it); the other 26 positions (the hidden core included) Hub Casing or Hub Power Port, **at least one Power Port**; the Satellite Uplink (1x1x2) stands on the cube's top middle block (`center.above(2)`), total height 5. Reach is still counted from the satellite's chunk: T1 1, T2 3x3, T3 7x7 chunks (`controlHubRange`).
- Problem order: satellite, then the cube positions from the bottom layer up, in each layer front row to back and left to right (seen from in front of the Interface), then the missing Power Port.
- Formed casings and ports carry `x`, `y`, `z` = `alone`/`low`/`middle`/`high` (`FeedHubBlock.Part`); the glow lines run along the cube's outer edges; the Interface (mid-face) has none.
- Hub Connector: id `hub_connector`, names "Hub Connector" / "Konektor Hub", stacks to 16, main creative tab. Right-click a Hub Interface (formed or not) with connectors → the **whole stack in hand** is linked (NBT `HubLink` {`Pos` long, `Dimension` string}); the click never opens the hub screen. Shift + right-click in the air clears the link. Tooltip: the linked hub's coordinates or "not linked". Item property `bastion:linked` (lit lamp icon).
- Turret slot `TurretInventory.CONNECTOR` (index 14, `SIZE` 15) on every base (standard and large, all tiers), Status tab, only `HubConnectorItem`; automation never sees it (only ammo slots are exposed); a destroyed turret drops it with its modules; a mined turret keeps it; turrets saved with 14 slots load with an empty 15th.
- A turret shows in a hub, opens through it and takes batch edits only with a connector linked to that hub (same position and dimension) + inside the reach + chunk loaded + `canConfigure`. Removing the connector closes a GUI opened through the hub.
- Recipes (Module Workstation, hub tab): **8** Hub Casing = 4 iron ingot, 2 copper ingot, 1 redstone (60 t, 1,000 FE); **4** Hub Connector = 2 copper ingot, 1 redstone, 1 ender pearl (60 t, 1,000 FE).
- Unchanged: FE rules (`controlHubRestartTicks` 100 confirmed by the user), screens, packets, network protocol 10 (never released).
- Never commit until the user says "commit"; never commit `modlist.html`; never touch `run/saves/showcase` (scenes run with `-PshowcaseWorld=showcase_fx`); Gradle always `--offline`; `tools/gen_sounds.py` without `--force`; client code only in `dev.bastion.client` (and `src/dev`).

## Review Focus

1. **Worlds saved before this change**: turrets (and mined turret items, whose `BlockEntityTag` holds the same inventory NBT) saved with 14 slots must load with an empty Connector slot and nothing shifted. Test: `oldTurretSaveGetsAConnectorSlot` (Task 4).
2. **A connector linked to the same coordinates in another dimension, or to a position with no hub**: the turret must not join. Tests: `connectorLinksByClickingTheInterface` (Nether check, Task 3), `hubListsOnlyConnectedTurrets` (Task 5).
3. **The connector taken out through a GUI opened from the hub**: the GUI must close at once (no further edits through the hub). Test: the connector step in `remoteTurretGuiFollowsTheHub` (Task 5).
4. **Parts of the bottom and top layers and the satellite two blocks above the top**: breaking any of them must break up the hub within a tick (`notifyAround` now looks 1 up, 1 down and 2 down). Test: `breakingAPartUnformsTheHub` (Task 1).
5. **Turrets standing right next to the cube or under the satellite** (positions the old 3x3 never covered): a turret is never a hub part and a casing never a turret; the cube's 26 positions are exactly those of `slots()`. Test: `hubFormsWhenComplete` asserts 26 slots and every part's x/y/z (Task 1).

---

## File map

| File | Change | Responsibility |
|---|---|---|
| `src/main/java/dev/bastion/hub/ControlHubStructure.java` | rewrite | cube geometry, satellite position, x/y/z form/unform, neighbour notify |
| `src/main/java/dev/bastion/hub/HubCasingBlock.java` | rewrite | `y` property |
| `src/main/java/dev/bastion/hub/HubConnectorItem.java` | create | link / clear / tooltip / `linkedTo` |
| `src/main/java/dev/bastion/hub/HubInterfaceBlockEntity.java` | modify | `connected(turret)` in `visibleTurrets` and `reachable` |
| `src/main/java/dev/bastion/turret/TurretInventory.java` | modify | `CONNECTOR` slot, old-save size |
| `src/main/java/dev/bastion/menu/TurretMenu.java` | modify | the slot in the GUI |
| `src/main/java/dev/bastion/client/screen/TurretScreen.java` | modify | empty-slot icon + tooltip |
| `src/main/java/dev/bastion/client/screen/ControlHubScreen.java` | modify | "no connected turrets" over the map |
| `src/main/java/dev/bastion/client/render/HubBlueprint.java`, `client/ControlHubEffects.java` | modify | scan sheet as tall as the cube, dish flash higher |
| `src/main/java/dev/bastion/client/BastionClient.java`, `registry/BastionItems.java` | modify | item property, registration |
| `src/main/java/dev/bastion/gametest/ControlHubTests.java` | modify | cube tests, connector tests |
| `src/dev/java/dev/bastion/dev/ControlHubShowcase.java` | modify | cubes on the floor, connectors in turrets |
| `tools/gen_control_hub_models.py`, `tools/gen_textures.py`, `tools/gen_recipes.py` | modify | cube edges, connector icons + GUI sprite, recipes |
| `src/main/resources/assets/bastion/models/item/hub_connector*.json`, `lang/en_us.json`, `lang/id_id.json` | create/modify | item models, strings |
| `docs/PLAN.md`, `docs/TESTING.md`, `docs/release/*`, `docs/HANDOFF.md` | modify | docs |

Commands: build `./gradlew build --offline`; all GameTests `./gradlew runGameTestServer --offline` (the log ends "All N required tests passed"); with the compat mods `./gradlew runGameTestServer --offline -PwithCompatMods`; scene `./gradlew runClient --offline -Pshowcase=hub -PshowcaseWorld=showcase_fx` (screenshots in `run/screenshots/showcase_hub_*.png`; the Mac screen must be unlocked: `ioreg -n Root -d1 -a | grep -c CGSSessionScreenIsLocked` prints 0). Never pipe a test run through `grep` when its exit code matters; redirect to a log file and read it. Baseline: **147** tests. Expected totals: 147 (T1, T2), 148 (T3), 151 (T4), 152 (T5, T6).

---

### Task 1: The cube layout

**Files:**
- Modify: `src/main/java/dev/bastion/hub/ControlHubStructure.java` (full rewrite below), `src/main/java/dev/bastion/hub/HubCasingBlock.java` (full rewrite below), `src/main/java/dev/bastion/gametest/ControlHubTests.java`
- Check wording: `src/main/java/dev/bastion/hub/HubInterfaceBlock.java`, `HubInterfaceBlockEntity.java` (javadoc "3x3" / "layer" → "cube")

**Interfaces:**
- Consumes: `FeedHubBlock.Part`, `FeedHubBlock.X/Y/Z`.
- Produces: `ControlHubStructure.slots(BlockPos iface, Direction facing)` → 26 positions in problem order (index 0 = bottom layer, front row, left; index 10 = middle layer, front row, right; index 12 = core; index 21 = top middle; index 25 = top layer, back row, right); `static BlockPos satellite(BlockPos center)` (= `center.above(2)`); `HubCasingBlock.Y`.

- [ ] **Step 1: Failing tests.** In `ControlHubTests`:

Replace the `IFACE` javadoc line with:

```java
    /** Arena: the Interface faces north in the middle layer (y 2); the cube runs south (z 3..5) from y 1 to 3; the satellite stands on (6, 4, 4). */
```

In `hub(ServerLevel level, BlockPos iface, Direction facing, int tier)` replace its javadoc and the satellite line:

```java
    /**
     * A cube hub with its Interface at absolute {@code iface} facing {@code facing}: casings, a Power Port in the first slot
     * (bottom layer, front row, left as seen from in front) and, unless {@code tier} is 0, a satellite of that tier on top.
     */
```

```java
        if (tier > 0) satellite(tier).placeAt(level, ControlHubStructure.center(iface, facing).above(2));
```

Replace the whole `hubFormsWhenComplete` method:

```java
    /** Interface, 25 casings and a Power Port, a satellite on top: formed a tick later (OFFLINE: no FE), every part knows its place. */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "hubFormsWhenComplete")
    public static void hubFormsWhenComplete(GameTestHelper helper) {
        HubInterfaceBlockEntity hub = hub(helper, IFACE, 1);
        helper.succeedWhen(() -> {
            helper.assertTrue(hub.status() == Status.OFFLINE, "expected formed without FE (OFFLINE), got " + hub.status());
            helper.assertTrue(hub.tier() == 1, "tier " + hub.tier());
            BlockPos center = hub.center();
            List<BlockPos> slots = ControlHubStructure.slots(hub.getBlockPos(), Direction.NORTH);
            helper.assertTrue(slots.size() == 26, "a cube has 26 parts besides the Interface, got " + slots.size());
            for (BlockPos pos : slots) {
                BlockState state = helper.getLevel().getBlockState(pos);
                helper.assertTrue(state.getValue(HubCasingBlock.X) == Part.of(pos.getX() - center.getX() + 1, 3)
                        && state.getValue(HubCasingBlock.Y) == Part.of(pos.getY() - center.getY() + 1, 3)
                        && state.getValue(HubCasingBlock.Z) == Part.of(pos.getZ() - center.getZ() + 1, 3), "wrong place at " + pos + ": " + state);
            }
            helper.assertTrue(helper.getLevel().getBlockState(center.above(2)).getValue(SatelliteUplinkBlock.STATUS) == Status.OFFLINE,
                    "the satellite on top did not follow the hub");
        });
    }
```

Replace the whole `hubNamesTheFirstProblem` method:

```java
    /** Four broken cubes in a row: no satellite, satellite on a top corner, stone in the hidden core, no Power Port. None forms. */
    @GameTest(template = YARD, timeoutTicks = 60, batch = "hubNamesTheFirstProblem")
    public static void hubNamesTheFirstProblem(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        HubInterfaceBlockEntity noSatellite = hub(helper, new BlockPos(3, 2, 3), 0);
        HubInterfaceBlockEntity offCentre = hub(helper, new BlockPos(9, 2, 3), 0);
        // On the top layer's back-right corner instead of its middle.
        satellite(1).placeAt(level, ControlHubStructure.slots(offCentre.getBlockPos(), Direction.NORTH).get(25).above());
        HubInterfaceBlockEntity stone = hub(helper, new BlockPos(15, 2, 3), 1);
        level.setBlock(stone.center(), Blocks.STONE.defaultBlockState(), 3);
        HubInterfaceBlockEntity noPort = hub(helper, new BlockPos(21, 2, 3), 1);
        level.setBlock(ControlHubStructure.slots(noPort.getBlockPos(), Direction.NORTH).get(0), BastionBlocks.HUB_CASING.get().defaultBlockState(), 3);
        helper.runAtTickTime(5, () -> {
            assertFirst(helper, noSatellite, Kind.NEED_SATELLITE, noSatellite.center().above(2));
            assertFirst(helper, offCentre, Kind.NEED_SATELLITE, offCentre.center().above(2));
            assertFirst(helper, stone, Kind.WRONG, stone.center());
            List<ControlHubStructure.Issue> issues = ControlHubStructure.check(level, noPort.getBlockPos(), Direction.NORTH).issues();
            helper.assertTrue(issues.size() == 25 && issues.stream().allMatch(i -> i.kind() == Kind.NEED_PORT), "no port: " + issues);
            for (HubInterfaceBlockEntity hub : List.of(noSatellite, offCentre, stone, noPort)) {
                helper.assertTrue(hub.status() == Status.UNFORMED, "formed anyway at " + hub.getBlockPos());
            }
            helper.succeed();
        });
    }
```

In `partOfAnotherHubIsWrong`: replace its javadoc with `/** A second cube built against a formed one shares the first one's x 6 wall (3 rows x 3 layers): those count as another hub's, it never forms. */`, the comment `// its 3x3 spans x 6..8: the first one's x 6 column is shared` with `// its cube spans x 6..8: the first one's x 6 wall is shared`, the satellite line with

```java
            satellite(1).placeAt(level, ControlHubStructure.center(second, Direction.NORTH).above(2));
```

and the issue assertion with

```java
            helper.assertTrue(issues.size() == 9 && issues.stream().allMatch(i -> i.kind() == Kind.OTHER_HUB),
                    "expected the 9 shared blocks as another hub's: " + issues);
```

Replace the whole `breakingAPartUnformsTheHub` method:

```java
    /**
     * A part of the bottom layer, then one of the top layer, then the satellite broken: each time the hub breaks up within a
     * tick and its parts forget their places; put back, it forms again.
     */
    @GameTest(template = ARENA, timeoutTicks = 80, batch = "breakingAPartUnformsTheHub")
    public static void breakingAPartUnformsTheHub(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        HubInterfaceBlockEntity hub = hub(helper, IFACE, 1);
        List<BlockPos> slots = ControlHubStructure.slots(hub.getBlockPos(), Direction.NORTH);
        BlockPos bottom = slots.get(2), top = slots.get(23), satellite = hub.center().above(2);
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(hub.formed(), "did not form");
            level.removeBlock(bottom, false);
        });
        helper.runAtTickTime(7, () -> {
            helper.assertTrue(hub.status() == Status.UNFORMED, "still formed after a bottom-layer part was broken");
            for (BlockPos pos : slots) {
                BlockState state = level.getBlockState(pos);
                helper.assertTrue(!(state.getBlock() instanceof HubCasingBlock) || state.getValue(HubCasingBlock.X) == Part.ALONE,
                        "part still formed at " + pos);
            }
            level.setBlock(bottom, BastionBlocks.HUB_CASING.get().defaultBlockState(), 3);
        });
        helper.runAtTickTime(10, () -> {
            helper.assertTrue(hub.formed(), "did not form again with the part back");
            level.removeBlock(top, false);
        });
        helper.runAtTickTime(12, () -> {
            helper.assertTrue(hub.status() == Status.UNFORMED, "still formed after a top-layer part was broken");
            level.setBlock(top, BastionBlocks.HUB_CASING.get().defaultBlockState(), 3);
        });
        helper.runAtTickTime(15, () -> {
            helper.assertTrue(hub.formed(), "did not form again with the top part back");
            level.removeBlock(satellite, false);
        });
        helper.runAtTickTime(17, () -> {
            helper.assertTrue(hub.status() == Status.UNFORMED, "still formed after the satellite was broken");
            helper.succeed();
        });
    }
```

In `brokenInterfaceFreesItsParts` replace the satellite check and the stale-part line:

```java
            helper.assertTrue(level.getBlockState(center.above(2)).getValue(SatelliteUplinkBlock.STATUS) == Status.UNFORMED, "satellite still formed");
            BlockPos stale = slots.get(3);
            level.setBlock(stale, level.getBlockState(stale).setValue(HubCasingBlock.X, Part.LOW).setValue(HubCasingBlock.Y, Part.LOW)
                    .setValue(HubCasingBlock.Z, Part.LOW), 2);
```

In `hubAcrossChunkBorder` replace the javadoc with `/** A cube whose columns straddle a chunk border forms like any other. */`. In `hubGoesOfflineWithoutFe` replace `hub.center().above()` with `hub.center().above(2)`. Afterwards `grep -n "center().above()" src/main/java/dev/bastion/gametest/ControlHubTests.java` prints nothing.

- [ ] **Step 2: Run to verify it fails.** `./gradlew compileJava --offline > <workspace>/t1-red.log 2>&1`. Expected: compile errors `cannot find symbol ... Y` in `HubCasingBlock`.

- [ ] **Step 3: `HubCasingBlock`.** Replace the file with:

```java
package dev.bastion.hub;

import dev.bastion.turret.FeedHubBlock;
import dev.bastion.turret.FeedHubBlock.Part;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Hub Casing (PLAN "Turret Control Hub"): the plain block of a Control Hub's 3x3x3 cube. X, Y and Z hold its place in a
 * formed hub (FeedHubBlock's parts; ALONE = in no hub), set by the hub's Interface; placing or breaking one makes the
 * Interfaces around check their layout again.
 */
public class HubCasingBlock extends Block {
    public static final EnumProperty<Part> X = FeedHubBlock.X, Y = FeedHubBlock.Y, Z = FeedHubBlock.Z;

    public HubCasingBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(X, Part.ALONE).setValue(Y, Part.ALONE).setValue(Z, Part.ALONE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(X, Y, Z);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moving) {
        super.onPlace(state, level, pos, oldState, moving);
        if (!oldState.is(this)) ControlHubStructure.notifyAround(level, pos);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        super.onRemove(state, level, pos, newState, moving);
        if (!state.is(newState.getBlock())) ControlHubStructure.notifyAround(level, pos);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.bastion." + tooltipKey()).withStyle(ChatFormatting.GRAY));
    }

    protected String tooltipKey() {
        return "hub_casing";
    }
}
```

- [ ] **Step 4: `ControlHubStructure`.** Replace the file with:

```java
package dev.bastion.hub;

import dev.bastion.hub.HubInterfaceBlock.Status;
import dev.bastion.turret.FeedHubBlock.Part;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Turret Control Hub layout (PLAN "Turret Control Hub"): a 3x3x3 cube whose middle layer has the Hub Interface in the
 * middle of its front side (facing out, the cube behind it, at the eye level of a player on the ground), every other
 * block a Hub Casing or Hub Power Port (at least one port), and a Satellite Uplink standing on the cube's top middle
 * block. One check serves the Interface (forming, server) and the blueprint hologram (client). A formed casing or port
 * carries its place in the cube in its block state (x, y, z as in FeedHubBlock), which is also how a hub tells its own
 * parts from another hub's.
 */
public final class ControlHubStructure {
    /** What is wrong at one position; the action bar names the first one. */
    public enum Kind {NEED_SATELLITE, NEED_BLOCK, WRONG, OTHER_HUB, NEED_PORT}

    public record Issue(BlockPos pos, Kind kind) {
    }

    /** The check's outcome: formed when nothing is wrong; tier = the satellite's (0 without one). */
    public record Result(List<Issue> issues, int tier) {
        public boolean formed() {
            return issues.isEmpty();
        }
    }

    private ControlHubStructure() {
    }

    /** The cube's middle block: one block behind the Interface, at its height. The reach is counted from its column. */
    public static BlockPos center(BlockPos interfacePos, Direction facing) {
        return interfacePos.relative(facing.getOpposite());
    }

    /** Where the satellite's bottom half stands: on the cube's top middle block. */
    public static BlockPos satellite(BlockPos center) {
        return center.above(2);
    }

    /**
     * The 26 cube blocks besides the Interface, in problem order: the bottom layer, then the middle, then the top; in each
     * layer the front row first, left to right as seen standing in front of the Interface.
     */
    public static List<BlockPos> slots(BlockPos interfacePos, Direction facing) {
        Direction back = facing.getOpposite(), right = back.getClockWise();
        List<BlockPos> slots = new ArrayList<>(26);
        for (int layer = -1; layer <= 1; layer++) {
            for (int row = 0; row < 3; row++) {
                for (int col = -1; col <= 1; col++) {
                    if (layer != 0 || row != 0 || col != 0) slots.add(interfacePos.relative(back, row).relative(right, col).above(layer));
                }
            }
        }
        return slots;
    }

    /**
     * What keeps the hub of the Interface at {@code interfacePos} from forming: the satellite first, then the slots in
     * {@link #slots} order, then (only once every slot holds a part) the missing Power Port on every casing. Never loads a
     * chunk: a position in an unloaded one counts as missing.
     */
    public static Result check(Level level, BlockPos interfacePos, Direction facing) {
        BlockPos center = center(interfacePos, facing);
        List<Issue> issues = new ArrayList<>();
        int tier = 0;
        BlockPos satellite = satellite(center);
        BlockState up = level.isLoaded(satellite) ? level.getBlockState(satellite) : null;
        if (up != null && up.getBlock() instanceof SatelliteUplinkBlock uplink && up.getValue(SatelliteUplinkBlock.PART) == 0) {
            tier = uplink.tier();
        } else if (up == null || up.canBeReplaced()) {
            issues.add(new Issue(satellite, Kind.NEED_SATELLITE));
            BlockPos top = satellite.above(); // the satellite's upper half must fit too
            if (level.isLoaded(top) && !level.getBlockState(top).canBeReplaced()) issues.add(new Issue(top, Kind.WRONG));
        } else {
            issues.add(new Issue(satellite, Kind.WRONG));
        }
        int ports = 0;
        boolean complete = true;
        List<BlockPos> casings = new ArrayList<>(26);
        for (BlockPos pos : slots(interfacePos, facing)) {
            BlockState state = level.isLoaded(pos) ? level.getBlockState(pos) : null;
            if (state == null || !(state.getBlock() instanceof HubCasingBlock)) {
                issues.add(new Issue(pos, state == null || state.canBeReplaced() ? Kind.NEED_BLOCK : Kind.WRONG));
                complete = false;
            } else if (ownedElsewhere(level, pos, state, center)) {
                issues.add(new Issue(pos, Kind.OTHER_HUB));
                complete = false;
            } else if (state.getBlock() instanceof HubPowerPortBlock) {
                ports++;
            } else {
                casings.add(pos);
            }
        }
        // Before every slot holds a part, an empty slot can still take the port: only then point at the casings.
        if (complete && ports == 0) for (BlockPos pos : casings) issues.add(new Issue(pos, Kind.NEED_PORT));
        return new Result(issues, tier);
    }

    /** "Turret Control Hub incomplete (3): needs a Hub Casing or Hub Power Port" for the action bar of an unformed hub. */
    public static Component message(Result result) {
        Issue first = result.issues().get(0);
        return Component.translatable("message.bastion.control_hub.incomplete", result.issues().size(),
                Component.translatable("message.bastion.control_hub." + first.kind().name().toLowerCase(Locale.ROOT)));
    }

    /** A formed part whose place puts it around another middle block that still has a formed Interface. */
    private static boolean ownedElsewhere(Level level, BlockPos pos, BlockState state, BlockPos center) {
        if (state.getValue(HubCasingBlock.X) == Part.ALONE) return false;
        BlockPos owner = partCenter(pos, state);
        return !owner.equals(center) && hubAt(level, owner) != null;
    }

    /** The middle block of the cube a formed part sits in. */
    public static BlockPos partCenter(BlockPos pos, BlockState state) {
        return pos.offset(step(state.getValue(HubCasingBlock.X)), step(state.getValue(HubCasingBlock.Y)), step(state.getValue(HubCasingBlock.Z)));
    }

    private static int step(Part part) {
        return part == Part.LOW ? 1 : part == Part.HIGH ? -1 : 0;
    }

    /** The formed Interface of the hub around {@code center} (in the middle of one of its sides, facing away), or null. */
    @Nullable
    public static HubInterfaceBlockEntity hubAt(Level level, BlockPos center) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos pos = center.relative(side);
            if (!level.isLoaded(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof HubInterfaceBlock && state.getValue(HubInterfaceBlock.FACING) == side
                    && state.getValue(HubInterfaceBlock.STATUS) != Status.UNFORMED && level.getBlockEntity(pos) instanceof HubInterfaceBlockEntity hub) {
                return hub;
            }
        }
        return null;
    }

    /** Gives every part its place in the cube (clients only: no neighbour updates). The caller sets the status. */
    static void form(Level level, BlockPos interfacePos, Direction facing) {
        BlockPos center = center(interfacePos, facing);
        for (BlockPos pos : slots(interfacePos, facing)) {
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof HubCasingBlock)) continue;
            BlockState formed = state.setValue(HubCasingBlock.X, Part.of(pos.getX() - center.getX() + 1, 3))
                    .setValue(HubCasingBlock.Y, Part.of(pos.getY() - center.getY() + 1, 3))
                    .setValue(HubCasingBlock.Z, Part.of(pos.getZ() - center.getZ() + 1, 3));
            if (formed != state) level.setBlock(pos, formed, Block.UPDATE_CLIENTS);
        }
    }

    /** Its parts and satellite stop being this hub's (parts of other hubs are left alone). */
    static void unform(Level level, BlockPos interfacePos, Direction facing) {
        BlockPos center = center(interfacePos, facing);
        for (BlockPos pos : slots(interfacePos, facing)) {
            if (!level.isLoaded(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof HubCasingBlock && state.getValue(HubCasingBlock.X) != Part.ALONE && partCenter(pos, state).equals(center)) {
                level.setBlock(pos, state.setValue(HubCasingBlock.X, Part.ALONE).setValue(HubCasingBlock.Y, Part.ALONE)
                        .setValue(HubCasingBlock.Z, Part.ALONE), Block.UPDATE_CLIENTS);
            }
        }
        setSatellite(level, center, Status.UNFORMED);
    }

    /** The satellite on top of the cube around {@code center} shows the hub's status (dish folded, spinning or stopped). */
    static void setSatellite(Level level, BlockPos center, Status status) {
        BlockPos pos = satellite(center);
        if (!level.isLoaded(pos)) return;
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof SatelliteUplinkBlock && state.getValue(SatelliteUplinkBlock.PART) == 0
                && state.getValue(SatelliteUplinkBlock.STATUS) != status) {
            level.setBlock(pos, state.setValue(SatelliteUplinkBlock.STATUS, status), Block.UPDATE_CLIENTS);
        }
    }

    /** A part was placed or removed at {@code pos}: every Interface whose hub could hold it checks again next tick. */
    public static void notifyAround(Level level, BlockPos pos) {
        if (level.isClientSide) return;
        // The Interface sits in the middle layer: one above a bottom-layer part, one below a top-layer part, two below the satellite.
        for (int dy = -2; dy <= 1; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos at = pos.offset(dx, dy, dz);
                    if (level.isLoaded(at) && level.getBlockState(at).getBlock() instanceof HubInterfaceBlock block) level.scheduleTick(at, block, 1);
                }
            }
        }
    }

    /** Whether {@code pos} lies in the {@code side} x {@code side} chunks centred on the chunk of {@code center}. */
    public static boolean covers(BlockPos center, int side, BlockPos pos) {
        int r = side / 2;
        return Math.abs((pos.getX() >> 4) - (center.getX() >> 4)) <= r && Math.abs((pos.getZ() >> 4) - (center.getZ() >> 4)) <= r;
    }
}
```

Then `grep -n "3x3\|layer" src/main/java/dev/bastion/hub/HubInterfaceBlock.java src/main/java/dev/bastion/hub/HubInterfaceBlockEntity.java` and change any javadoc that describes the hub as a 3x3 layer to the cube (e.g. HubInterfaceBlock: "in the middle of one side of the 3x3" → "in the middle of one side of the cube's middle layer"; HubInterfaceBlockEntity.center: "The middle block of the layer" → "The cube's middle block"). Wording only.

- [ ] **Step 5: Run to verify it passes.** `./gradlew build --offline`, then `./gradlew runGameTestServer --offline > <workspace>/t1-gt.log 2>&1`. Expected: "All 147 required tests passed".

---

### Task 2: The cube's look, blueprint and scene

**Files:**
- Modify: `tools/gen_control_hub_models.py`, `src/main/java/dev/bastion/client/render/HubBlueprint.java`, `src/main/java/dev/bastion/client/ControlHubEffects.java`, `src/dev/java/dev/bastion/dev/ControlHubShowcase.java`
- Generated: `src/main/resources/assets/bastion/blockstates/hub_casing.json`, `hub_power_port.json`, `hub_interface.json`

**Interfaces:**
- Consumes: Task 1 `ControlHubStructure.satellite`, `slots` index 10 (middle layer, front row, right).
- Produces: scene constant `FIELD` (floor-level anchor of the scene turrets, = the old `T3_IFACE`), `T1_IFACE`/`T2_IFACE`/`T3_IFACE`/`UNFINISHED` one block higher than before (cubes stand on the floor).

GUI and model code is checked by the scene, not GameTests.

- [ ] **Step 1: Cube edges.** In `tools/gen_control_hub_models.py`:

Replace the module docstring with:

```python
"""Turret Control Hub block models and blockstates (PLAN "Turret Control Hub"). Hub Casing and Power Port pick their look
from their place in a formed 3x3x3 hub (x, y, z = alone/low/middle/high; alone = in no hub): formed, a casing becomes the
Feed Hub structure's plain panel, the port keeps its sockets with a glowing ring, and both get the Feed Hub's frame and
cyan glow line on every face at an end of the cube, so the lines run along the cube's outer edges (the Feed Hub's edge
models, reused; faces between parts are culled). The Hub Interface (facing x status) is a console block with a screen
tilted back in a recess: dark, a radar sweep (online) or a blinking power glyph (offline); it sits in the middle of a side,
away from every edge, so it has no lines. Satellites: a particle-only block model (GeckoLib draws them).

    python3 tools/gen_control_hub_models.py
"""
```

Replace the `edges` function with:

```python
def edges():
    """Feed Hub frames, then glow lines (later quads win), on every side where the block is at that end of the cube."""
    return [{"when": layer_end(end), "apply": {"model": f"{B}feed_hub_{kind}_{end}"}} for kind in ("frame", "glow") for end in FACES]
```

In `__main__`, replace both `*edges(FORMED, layer_end)]})` with `*edges()]})`, and replace

```python
    lit = {"status": "online|offline"}
    interface_parts = [{"when": {"facing": f, "status": s}, "apply": {"model": f"{B}hub_interface_{s}", "y": turn}}
                       for f, turn in TURN.items() for s in ("unformed", "online", "offline")]
    write("blockstates/hub_interface.json", {"multipart": interface_parts + edges(
        lit, lambda end: {"status": "online|offline", "facing": end})})
```

with

```python
    interface_parts = [{"when": {"facing": f, "status": s}, "apply": {"model": f"{B}hub_interface_{s}", "y": turn}}
                       for f, turn in TURN.items() for s in ("unformed", "online", "offline")]
    write("blockstates/hub_interface.json", {"multipart": interface_parts})
```

Run `python3 tools/gen_control_hub_models.py`. Expected: the same file list as before; `git diff --stat` on `blockstates/` shows only `hub_casing.json`, `hub_power_port.json`, `hub_interface.json` (the first two now with `"y": "low"`/`"y": "high"` conditions, the last without edge parts).

- [ ] **Step 2: Scan sheet and dish flash.** In `HubBlueprint.scan` replace

```java
        Vec3 a = front.add(depth.scale(3 * t)).add(across.scale(-1.5));
        Vec3 b = front.add(depth.scale(3 * t + 0.1)).add(across.scale(1.5)).add(0, 1.05, 0);
```

with

```java
        Vec3 a = front.add(depth.scale(3 * t)).add(across.scale(-1.5)).add(0, -1, 0); // the cube runs from one below the Interface
        Vec3 b = front.add(depth.scale(3 * t + 0.1)).add(across.scale(1.5)).add(0, 2.05, 0); // to one above it
```

and its javadoc "A one-block-tall cyan sheet across the layer" with "A cube-tall cyan sheet across the hub". In `ControlHubEffects.formed` replace `.add(0, 2.8, 0)` with `.add(0, 3.8, 0)` (the dish is now two blocks above the cube's middle block plus its mast).

- [ ] **Step 3: Scene.** In `ControlHubShowcase`:

Replace

```java
    static final BlockPos T1_IFACE = ORIGIN.offset(-8, 0, 4), T2_IFACE = ORIGIN.offset(0, 0, 4), T3_IFACE = ORIGIN.offset(8, 0, 4);
    static final BlockPos UNFINISHED = ORIGIN.offset(-18, 0, 4);
```

with

```java
    // The Interfaces sit in their cubes' middle layer, one above the floor; FIELD is the floor-level anchor of the turrets.
    static final BlockPos T1_IFACE = ORIGIN.offset(-8, 1, 4), T2_IFACE = ORIGIN.offset(0, 1, 4), T3_IFACE = ORIGIN.offset(8, 1, 4);
    static final BlockPos UNFINISHED = ORIGIN.offset(-18, 1, 4), FIELD = ORIGIN.offset(8, 0, 4);
```

Then replace every `T3_IFACE.offset(` that names a **turret** position with `FIELD.offset(` (the six `turret(level, ...)` calls in `build`, `HubAction.open(T3_IFACE.offset(-4, 0, 6))`, the sniper lookup `T3_IFACE.offset(14, 0, -6)` and the three positions in `HubSelection.select(List.of(...))`). Afterwards `grep -n "T3_IFACE.offset" src/dev/java/dev/bastion/dev/ControlHubShowcase.java` prints nothing.

Replace `Vec3 dish = Vec3.atBottomCenterOf(T3_IFACE.north()).add(0, 2.4, 0);` with `Vec3 dish = Vec3.atBottomCenterOf(T3_IFACE.north()).add(0, 3.8, 0);`, and `Showcase.camera(server, unfinished.add(2.5, 3.5, 5.5), unfinished);` with `Showcase.camera(server, unfinished.add(3.5, 4.5, 7.5), unfinished);`.

In `hub(ServerLevel, BlockPos, int)` replace `satellite.placeAt(level, ControlHubStructure.center(iface, Direction.SOUTH).above());` with `satellite.placeAt(level, ControlHubStructure.satellite(ControlHubStructure.center(iface, Direction.SOUTH)));`.

In `build`, replace

```java
        // Unfinished: no satellite, the front-right casing missing (in sight of the blueprint camera).
        hub(level, UNFINISHED, 0);
        level.setBlock(ControlHubStructure.slots(UNFINISHED, Direction.SOUTH).get(1), Blocks.AIR.defaultBlockState(), 3);
```

with

```java
        // Unfinished: no satellite, the middle layer's front-right casing missing (in sight of the blueprint camera).
        hub(level, UNFINISHED, 0);
        level.setBlock(ControlHubStructure.slots(UNFINISHED, Direction.SOUTH).get(10), Blocks.AIR.defaultBlockState(), 3);
```

and in the class javadoc "T1, T2 and T3 hubs side by side" with "T1, T2 and T3 cube hubs side by side".

- [ ] **Step 4: Run.** `./gradlew build --offline`; `./gradlew runGameTestServer --offline` (still "All 147 required tests passed"); `./gradlew runClient --offline -Pshowcase=hub -PshowcaseWorld=showcase_fx`. Read `run/screenshots/showcase_hub_day.png`, `_dish`, `_blueprint`, `_night` and check: three 3x3x3 cubes standing on the floor, cyan lines along every outer edge of each cube and none around the Interfaces, Interfaces in the middle layer (radar lit), satellites on top (dish bigger T1 → T3, spinning); the unfinished cube with a cyan box and a turning Hub Casing where the middle layer's front-right block is missing, and two stacked cyan boxes with the turning Satellite Uplink above the top layer, no red; at night the lines, ports, screens and beacons glow. Zoom with PIL crops where something is unclear. Fix what reads wrong (camera positions in the scene, z-fighting in models) and re-run.

---

### Task 3: The Hub Connector item

**Files:**
- Create: `src/main/java/dev/bastion/hub/HubConnectorItem.java`, `src/main/resources/assets/bastion/models/item/hub_connector.json`, `hub_connector_linked.json`
- Modify: `src/main/java/dev/bastion/registry/BastionItems.java`, `src/main/java/dev/bastion/client/BastionClient.java`, `tools/gen_textures.py`, `tools/gen_recipes.py`, `lang/en_us.json`, `lang/id_id.json`, `gametest/ControlHubTests.java`

**Interfaces:**
- Consumes: `HubInterfaceBlock` (the clicked block's type).
- Produces: `HubConnectorItem` with `static ItemStack link(ItemStack, Level, BlockPos iface)` (writes the link, returns the stack), `static boolean isLinked(ItemStack)`, `static boolean linkedTo(ItemStack, Level, BlockPos iface)`; `BastionItems.HUB_CONNECTOR` (`RegistryObject<HubConnectorItem>`); item property `bastion:linked`.

- [ ] **Step 1: Failing tests.** In `ControlHubTests` (imports `dev.bastion.hub.HubConnectorItem`, `net.minecraft.world.InteractionHand`, `net.minecraft.world.InteractionResult`, `net.minecraft.world.item.ItemStack`, `net.minecraft.world.item.context.UseOnContext`, `net.minecraft.world.level.Level`, `net.minecraft.world.phys.BlockHitResult`), add `"hub_connector"` to the list in `hubPartRecipesExist`, and add:

```java
    /** Right-clicking an Interface links the whole stack to it, in this dimension only; other blocks do nothing; shift + right-click in the air clears it. */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "connectorLinksByClickingTheInterface")
    public static void connectorLinksByClickingTheInterface(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        HubInterfaceBlockEntity hub = hub(helper, IFACE, 1);
        BlockPos iface = hub.getBlockPos();
        FakePlayer player = player(helper, new BlockPos(6, 1, 1));
        ItemStack stack = new ItemStack(BastionItems.HUB_CONNECTOR.get(), 3);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        InteractionResult result = stack.onItemUseFirst(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(iface), Direction.NORTH, iface, false)));
        helper.assertTrue(result.consumesAction() && stack.getCount() == 3 && HubConnectorItem.linkedTo(stack, level, iface),
                "not linked by a click on the Interface: " + result + ", " + stack.getTag());
        helper.assertTrue(!HubConnectorItem.linkedTo(stack, level, iface.east()), "linked to another position");
        helper.assertTrue(!HubConnectorItem.linkedTo(stack, level.getServer().getLevel(Level.NETHER), iface), "linked in another dimension");
        ItemStack other = new ItemStack(BastionItems.HUB_CONNECTOR.get());
        BlockPos floor = helper.absolutePos(new BlockPos(2, 0, 2));
        helper.assertTrue(other.onItemUseFirst(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(floor), Direction.UP, floor, false))) == InteractionResult.PASS && !HubConnectorItem.isLinked(other),
                "a click on another block linked it");
        player.setShiftKeyDown(true);
        stack.use(level, player, InteractionHand.MAIN_HAND);
        player.setShiftKeyDown(false); // the fake player is shared with later tests
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.assertTrue(!HubConnectorItem.isLinked(stack), "shift + use in the air did not clear the link");
        helper.succeed();
    }
```

- [ ] **Step 2: Run to verify it fails.** `./gradlew compileJava --offline > <workspace>/t3-red.log 2>&1`. Expected: compile errors (`HubConnectorItem`, `BastionItems.HUB_CONNECTOR` undefined).

- [ ] **Step 3: The item.** Create `src/main/java/dev/bastion/hub/HubConnectorItem.java`:

```java
package dev.bastion.hub;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Hub Connector (PLAN "Turret Control Hub", "Hub Connector"): right-click a Hub Interface to link the whole stack in hand
 * to that hub; one in a turret's Connector slot makes the turret show in that hub (within its satellite's reach).
 * Shift + right-click in the air clears the link. The link is the Interface's position and dimension.
 */
public class HubConnectorItem extends Item {
    private static final String LINK = "HubLink", POS = "Pos", DIMENSION = "Dimension";

    public HubConnectorItem(Properties properties) {
        super(properties);
    }

    /** Links {@code stack} to the hub whose Interface is at {@code iface} in {@code level}; returns the stack. */
    public static ItemStack link(ItemStack stack, Level level, BlockPos iface) {
        CompoundTag link = new CompoundTag();
        link.putLong(POS, iface.asLong());
        link.putString(DIMENSION, level.dimension().location().toString());
        stack.getOrCreateTag().put(LINK, link);
        return stack;
    }

    public static boolean isLinked(ItemStack stack) {
        return stack.getTag() != null && stack.getTag().contains(LINK, Tag.TAG_COMPOUND);
    }

    /** Whether {@code stack} is a connector linked to the Interface at {@code iface} in {@code level}'s dimension. */
    public static boolean linkedTo(ItemStack stack, Level level, BlockPos iface) {
        if (!(stack.getItem() instanceof HubConnectorItem) || !isLinked(stack)) return false;
        CompoundTag link = stack.getTag().getCompound(LINK);
        return link.getLong(POS) == iface.asLong() && level.dimension().location().toString().equals(link.getString(DIMENSION));
    }

    /** Right-click on a Hub Interface links the stack in hand; it runs before the Interface would open its screen. */
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!(level.getBlockState(pos).getBlock() instanceof HubInterfaceBlock)) return InteractionResult.PASS;
        if (!level.isClientSide) {
            link(stack, level, pos);
            if (context.getPlayer() != null) {
                context.getPlayer().displayClientMessage(Component.translatable("message.bastion.hub_connector.linked", pos.getX(), pos.getY(), pos.getZ()), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Shift + right-click in the air clears the link. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isSecondaryUseActive() || !isLinked(stack)) return InteractionResultHolder.pass(stack);
        if (!level.isClientSide) {
            stack.removeTagKey(LINK);
            player.displayClientMessage(Component.translatable("message.bastion.hub_connector.cleared"), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        if (isLinked(stack)) {
            BlockPos pos = BlockPos.of(stack.getTag().getCompound(LINK).getLong(POS));
            tooltip.add(Component.translatable("tooltip.bastion.hub_connector.linked", pos.getX(), pos.getY(), pos.getZ()).withStyle(ChatFormatting.AQUA));
        } else {
            tooltip.add(Component.translatable("tooltip.bastion.hub_connector.unlinked").withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("tooltip.bastion.hub_connector").withStyle(ChatFormatting.GRAY));
    }
}
```

`BastionItems`, right after `SATELLITE_UPLINK_T3` (import `dev.bastion.hub.HubConnectorItem`):

```java
    /** Links a turret to a Turret Control Hub (PLAN "Hub Connector"); goes in the turret's Connector slot. */
    public static final RegistryObject<HubConnectorItem> HUB_CONNECTOR = REGISTER.register("hub_connector",
            () -> new HubConnectorItem(new Item.Properties().stacksTo(16)));
```

(It lands in the main creative tab: that tab takes every item no other tab claims.)

`BastionClient.onClientSetup`, inside `enqueueWork`, after the screen registrations (imports `dev.bastion.hub.HubConnectorItem`, `dev.bastion.registry.BastionItems`, `net.minecraft.client.renderer.item.ItemProperties`):

```java
            // A linked connector shows its lamp lit (models/item/hub_connector.json override).
            ItemProperties.register(BastionItems.HUB_CONNECTOR.get(), Bastion.id("linked"),
                    (stack, level, entity, seed) -> HubConnectorItem.isLinked(stack) ? 1 : 0);
```

Create `src/main/resources/assets/bastion/models/item/hub_connector.json`:

```json
{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "bastion:item/hub_connector"
  },
  "overrides": [
    {"predicate": {"bastion:linked": 1}, "model": "bastion:item/hub_connector_linked"}
  ]
}
```

and `hub_connector_linked.json`:

```json
{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "bastion:item/hub_connector_linked"
  }
}
```

In `tools/gen_textures.py`, after `hub_screen_alert()`:

```python
def hub_connector(linked=False):
    """Hub Connector item: a gunmetal plug with two steel prongs and a cable; its lamp turns cyan once linked to a hub."""
    img = np.zeros((16, 16, 4))
    for x in (5, 9):  # prongs
        rect(img, x, 1, 2, 5, OUTLINE)
        rect(img, x, 1, 2, 4, STEEL * 1.15)
    rect(img, 3, 5, 10, 8, OUTLINE)  # body
    rect(img, 4, 6, 8, 6, GUNMETAL * 1.1)
    rect(img, 4, 6, 8, 1, GUNMETAL * 1.45)  # top bevel
    rect(img, 7, 13, 2, 3, OUTLINE)  # cable
    rect(img, 7, 13, 2, 2, DARKSTEEL * 1.4)
    rect(img, 7, 8, 2, 2, rgb("4FD8FF") if linked else BORE)  # lamp
    return img
```

and in `TEXTURES`, next to the other hub entries: `"item/hub_connector.png": hub_connector, "item/hub_connector_linked.png": lambda: hub_connector(linked=True),`. Run `python3 tools/gen_textures.py`; `git status --short src/main/resources/assets/bastion/textures` shows only the two new files.

In `tools/gen_recipes.py`, in `HUB`, change the `hub_casing` count from `4` to `8` and add the line

```python
       "hub_connector": ({"copper_ingot": 2, "redstone": 1, "ender_pearl": 1}, 60, 1000, 4),
```

Run `python3 tools/gen_recipes.py`. Expected: "127 workstation recipes, …"; `hub_casing.json` now has `"count": 8` and `hub_connector.json` is new.

Lang (`en_us.json` / `id_id.json`, next to the other hub keys):

| Key | en_us | id_id |
|---|---|---|
| `item.bastion.hub_connector` | Hub Connector | Konektor Hub |
| `tooltip.bastion.hub_connector` | Put it in a turret's Connector slot: that turret then shows in the linked hub | Pasang di slot Connector turret: turret itu lalu tampil di hub yang di-link |
| `tooltip.bastion.hub_connector.linked` | Linked to the hub at %s, %s, %s | Terhubung ke hub di %s, %s, %s |
| `tooltip.bastion.hub_connector.unlinked` | Not linked: right-click a Hub Interface | Belum terhubung: klik kanan Hub Interface |
| `message.bastion.hub_connector.linked` | Linked to the hub at %s, %s, %s | Terhubung ke hub di %s, %s, %s |
| `message.bastion.hub_connector.cleared` | Link cleared | Link dihapus |

Check both files still parse: `python3 -c "import json; [json.load(open(f'src/main/resources/assets/bastion/lang/{f}.json')) for f in ('en_us', 'id_id')]"`.

- [ ] **Step 4: Run to verify it passes.** `./gradlew build --offline`, `./gradlew runGameTestServer --offline`. Expected: "All 148 required tests passed".

---

### Task 4: The Connector slot on every turret

**Files:**
- Modify: `src/main/java/dev/bastion/turret/TurretInventory.java`, `src/main/java/dev/bastion/menu/TurretMenu.java`, `src/main/java/dev/bastion/client/screen/TurretScreen.java`, `tools/gen_textures.py`, `lang/en_us.json`, `lang/id_id.json`, `gametest/ControlHubTests.java`

**Interfaces:**
- Consumes: Task 3 `HubConnectorItem`, `BastionItems.HUB_CONNECTOR`.
- Produces: `TurretInventory.CONNECTOR` (14), `TurretInventory.SIZE` (15); `TurretMenu.CONNECTOR_X` (128), `CONNECTOR_Y` (55); GUI sprite at (216, 66) in `textures/gui/turret.png`.

- [ ] **Step 1: Failing tests.** In `ControlHubTests` (imports `dev.bastion.turret.TurretInventory`, `dev.bastion.turret.TurretTier`, `net.minecraft.nbt.CompoundTag`, `net.minecraftforge.items.IItemHandler`, `net.minecraftforge.items.ItemHandlerHelper`):

```java
    /** The Connector slot takes Hub Connectors only (on a T1 base too), no other slot takes one, and automation can neither put one in nor take it out. */
    @GameTest(template = ARENA, batch = "connectorSlotTakesOnlyConnectors")
    public static void connectorSlotTakesOnlyConnectors(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TurretBaseBlockEntity turret = base(level, helper.absolutePos(new BlockPos(2, 1, 2)), null);
        TurretInventory inventory = turret.inventory();
        ItemStack connector = new ItemStack(BastionItems.HUB_CONNECTOR.get());
        helper.assertTrue(inventory.isItemValid(TurretInventory.CONNECTOR, connector), "a T1 base refuses a connector");
        helper.assertTrue(!inventory.isItemValid(TurretInventory.CONNECTOR, new ItemStack(BastionItems.KINETIC_ROUNDS.get())), "the Connector slot took ammo");
        for (int slot = 0; slot < TurretInventory.CONNECTOR; slot++) {
            helper.assertTrue(!inventory.isItemValid(slot, connector), "slot " + slot + " took a connector");
        }
        IItemHandler automation = turret.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElseThrow(IllegalStateException::new);
        helper.assertTrue(ItemHandlerHelper.insertItem(automation, connector.copy(), false).getCount() == 1
                && inventory.getStackInSlot(TurretInventory.CONNECTOR).isEmpty(), "automation put a connector in");
        inventory.setStackInSlot(TurretInventory.CONNECTOR, connector.copy());
        for (int slot = 0; slot < automation.getSlots(); slot++) {
            helper.assertTrue(!automation.extractItem(slot, 1, true).is(BastionItems.HUB_CONNECTOR.get()), "automation could take the connector out");
        }
        level.removeBlock(turret.getBlockPos(), false);
        helper.succeed();
    }

    /** A turret saved before the Connector slot (14 slots) loads with an empty 15th slot and everything else where it was. */
    @GameTest(template = EMPTY, batch = "oldTurretSaveGetsAConnectorSlot")
    public static void oldTurretSaveGetsAConnectorSlot(GameTestHelper helper) {
        TurretInventory old = new TurretInventory(() -> TurretTier.T3, () -> false, slot -> {});
        old.setStackInSlot(TurretInventory.WEAPON, new ItemStack(BastionItems.GUN_TURRET.get()));
        old.setStackInSlot(TurretInventory.AMMO_START + 8, new ItemStack(BastionItems.KINETIC_ROUNDS.get(), 32));
        old.setStackInSlot(TurretInventory.MODIFIER_START + 3, new ItemStack(BastionItems.RANGE_MODULE.get()));
        CompoundTag tag = old.serializeNBT();
        tag.putInt("Size", 14); // what a turret saved before this update holds
        TurretInventory loaded = new TurretInventory(() -> TurretTier.T3, () -> false, slot -> {});
        loaded.deserializeNBT(tag);
        helper.assertTrue(loaded.getSlots() == 15, "slots after loading: " + loaded.getSlots());
        helper.assertTrue(loaded.getStackInSlot(TurretInventory.WEAPON).is(BastionItems.GUN_TURRET.get())
                && loaded.getStackInSlot(TurretInventory.AMMO_START + 8).getCount() == 32
                && loaded.getStackInSlot(TurretInventory.MODIFIER_START + 3).is(BastionItems.RANGE_MODULE.get())
                && loaded.getStackInSlot(TurretInventory.CONNECTOR).isEmpty(), "contents moved while loading");
        helper.succeed();
    }

    /** A destroyed turret drops its connector with its modules (it is not lost with the ammo). */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "connectorDropsWhenTheTurretIsDestroyed")
    public static void connectorDropsWhenTheTurretIsDestroyed(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(6, 1, 3));
        AABB area = new AABB(pos).inflate(6);
        level.getEntitiesOfClass(ItemEntity.class, area).forEach(Entity::discard); // left over from an earlier batch
        TurretBaseBlockEntity turret = base(level, pos, null);
        turret.inventory().setStackInSlot(TurretInventory.CONNECTOR, new ItemStack(BastionItems.HUB_CONNECTOR.get()));
        turret.takeDamage(level.damageSources().generic(), 100_000);
        helper.runAtTickTime(3, () -> {
            List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, area);
            helper.assertTrue(drops.stream().anyMatch(item -> item.getItem().is(BastionItems.HUB_CONNECTOR.get())), "no connector among " + drops);
            drops.forEach(Entity::discard);
            helper.succeed();
        });
    }
```

- [ ] **Step 2: Run to verify it fails.** `./gradlew compileJava --offline`. Expected: compile errors (`TurretInventory.CONNECTOR` undefined).

- [ ] **Step 3: The slot, then the old-save fix (two RED → GREEN rounds).** In `TurretInventory` (import `dev.bastion.hub.HubConnectorItem`): replace

```java
    public static final int SIZE = MODIFIER_START + TurretTier.MAX_MODIFIER_SLOTS;
```

with

```java
    /** The Hub Connector (PLAN "Hub Connector"): links the turret to a Turret Control Hub; every tier has it. */
    public static final int CONNECTOR = MODIFIER_START + TurretTier.MAX_MODIFIER_SLOTS;
    public static final int SIZE = CONNECTOR + 1;
```

the class javadoc's first sentence with "Weapon / ammo / modifier slots of a base (PLAN 4.2) plus its Hub Connector slot.", `isActive` with

```java
    /** Whether the current tier unlocks this slot (the Connector slot is always open). */
    public boolean isActive(int slot) {
        if (slot == CONNECTOR) return true;
        if (slot < AMMO_START) return true;
        if (slot < MODIFIER_START) return slot - AMMO_START < tier.get().ammoSlots;
        return slot - MODIFIER_START < tier.get().modifierSlots;
    }
```

in `isItemValid`, right after `if (!isActive(slot)) return false;`, add `if (slot == CONNECTOR) return stack.getItem() instanceof HubConnectorItem;`, and in `hasModifierElsewhere` change `i < SIZE` to `i < CONNECTOR`. Build and run the GameTests: expected `oldTurretSaveGetsAConnectorSlot` fails with "slots after loading: 14" (ItemStackHandler.deserializeNBT resizes to the saved size); the other two new tests pass (the destroy loop already drops every slot from the modules on; the test guards that). Then add to `TurretInventory`:

```java
    /** Saves from before the Connector slot hold 14 slots: loading one must not shrink the handler below SIZE. */
    @Override
    public void setSize(int size) {
        super.setSize(Math.max(size, SIZE));
    }
```

`TurretMenu`: add `public static final int CONNECTOR_X = 128, CONNECTOR_Y = 55;` next to the other slot positions (between the ammo grid and the modules), and right after the modifier-slot loop:

```java
        addSlot(new TurretSlot(turret, TurretInventory.CONNECTOR, CONNECTOR_X, CONNECTOR_Y));
```

(Menu slot indices still equal inventory indices; the player slots start at `TurretInventory.SIZE`, which `quickMoveStack` and `TurretBaseTests` already use.)

`tools/gen_textures.py`: add `CONNECTOR_ICON = (216, 66)  # 16x16 faint plug drawn in the empty Connector slot` next to `SLOT, LOCKED_SLOT`, and in `turret_gui()` before `return img` (after the tabs):

```python
    icon = hub_connector()
    icon[..., :3] *= 0.45  # a faint hint on the dark slot
    x, y = CONNECTOR_ICON
    img[y:y + 16, x:x + 16] = icon
```

Run `python3 tools/gen_textures.py`; `gui/turret.png` changes (expected), nothing else but the hub textures from Task 3.

`TurretScreen`: add `CONNECTOR_ICON_U = 216, CONNECTOR_ICON_V = 66` to the sprite constants; in `renderBg`, inside `if (tab == Tab.STATUS)` right after the slot-frame loop:

```java
            Slot connector = menu.slots.get(TurretInventory.CONNECTOR);
            if (!connector.hasItem()) graphics.blit(TEXTURE, leftPos + connector.x, topPos + connector.y, CONNECTOR_ICON_U, CONNECTOR_ICON_V, 16, 16);
```

and at the start of `renderStatusTooltips`:

```java
        Slot connector = menu.slots.get(TurretInventory.CONNECTOR);
        if (!connector.hasItem() && isHovering(connector.x, connector.y, 16, 16, mouseX, mouseY)) {
            graphics.renderTooltip(font, Component.translatable("gui.bastion.turret.connector"), mouseX, mouseY);
            return;
        }
```

Lang: `gui.bastion.turret.connector` = "Hub Connector slot: links this turret to a Turret Control Hub" / "Slot Konektor Hub: menghubungkan turret ini ke Turret Control Hub".

- [ ] **Step 4: Run to verify it passes.** `./gradlew build --offline`, `./gradlew runGameTestServer --offline`. Expected: "All 151 required tests passed" (`menuShiftClickMarksTurretChanged` and every other turret GUI test unchanged).

---

### Task 5: Only connected turrets join a hub

**Files:**
- Modify: `src/main/java/dev/bastion/hub/HubInterfaceBlockEntity.java`, `src/main/java/dev/bastion/client/screen/ControlHubScreen.java`, `lang/en_us.json`, `lang/id_id.json`, `gametest/ControlHubTests.java`, `src/dev/java/dev/bastion/dev/ControlHubShowcase.java`

**Interfaces:**
- Consumes: Task 3 `HubConnectorItem.link/linkedTo`, Task 4 `TurretInventory.CONNECTOR`.
- Produces: `boolean HubInterfaceBlockEntity.connected(TurretBaseBlockEntity)`; test helper `ControlHubTests.connect(TurretBaseBlockEntity, HubInterfaceBlockEntity)`.

- [ ] **Step 1: Failing test, and the older tests connect their turrets.** In `ControlHubTests`, after the `base(...)` helper:

```java
    /** A Hub Connector linked to {@code hub}, in the turret's Connector slot. */
    static void connect(TurretBaseBlockEntity turret, HubInterfaceBlockEntity hub) {
        turret.inventory().setStackInSlot(TurretInventory.CONNECTOR,
                HubConnectorItem.link(new ItemStack(BastionItems.HUB_CONNECTOR.get()), hub.getLevel(), hub.getBlockPos()));
    }
```

`hubListsOnlyTurretsThePlayerMayOpen`: after `large.setOwner(player.getUUID());` add `for (TurretBaseBlockEntity turret : List.of(own, free, stranger, large)) connect(turret, hub);`, and replace `base(level, outside, player.getUUID());` with `connect(base(level, outside, player.getUUID()), hub);`.

`remoteTurretGuiFollowsTheHub`: after `TurretBaseBlockEntity turret = base(level, near, player.getUUID());` add `connect(turret, hub);`; replace `base(level, out, player.getUUID());` with `connect(base(level, out, player.getUUID()), hub);`; replace the tick-10 block with

```java
        helper.runAtTickTime(10, () -> {
            helper.assertTrue(new TurretMenu(0, player.getInventory(), turret, hub.getBlockPos()).stillValid(player), "not usable once online again");
            ItemStack connector = turret.inventory().getStackInSlot(TurretInventory.CONNECTOR);
            turret.inventory().setStackInSlot(TurretInventory.CONNECTOR, ItemStack.EMPTY);
            helper.assertTrue(!new TurretMenu(0, player.getInventory(), turret, hub.getBlockPos()).stillValid(player), "usable with its connector taken out");
            turret.inventory().setStackInSlot(TurretInventory.CONNECTOR, connector);
            level.removeBlock(ControlHubStructure.slots(hub.getBlockPos(), Direction.NORTH).get(2), false);
        });
```

and add to its javadoc: "Taking the connector out closes it too."

`configUpdateNeedsAValidGui`: after `TurretBaseBlockEntity turret = base(level, at, player.getUUID());` add `connect(turret, hub);`.

`batchEditChangesOnlyThatSetting`: after the `List<TurretBaseBlockEntity> turrets = ...;` statement add `turrets.forEach(turret -> connect(turret, hub));`, and after `TurretBaseBlockEntity outside = base(level, far, player.getUUID());` add `connect(outside, hub);`.

New test:

```java
    /** Only turrets holding a connector linked to this hub show, open or take batch edits: not one without, unlinked or linked elsewhere. */
    @GameTest(template = YARD, timeoutTicks = 60, batch = "hubListsOnlyConnectedTurrets")
    public static void hubListsOnlyConnectedTurrets(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        HubInterfaceBlockEntity hub = hub(helper, new BlockPos(15, 2, 14), 2);
        hub.setEnergy(100_000);
        FakePlayer player = player(helper, new BlockPos(15, 2, 12));
        BlockPos center = hub.center();
        TurretBaseBlockEntity linked = base(level, center.offset(-4, 0, 4), player.getUUID());
        TurretBaseBlockEntity unlinked = base(level, center.offset(-2, 0, 4), player.getUUID());
        TurretBaseBlockEntity elsewhere = base(level, center.offset(0, 0, 4), player.getUUID());
        TurretBaseBlockEntity none = base(level, center.offset(2, 0, 4), player.getUUID());
        connect(linked, hub);
        unlinked.inventory().setStackInSlot(TurretInventory.CONNECTOR, new ItemStack(BastionItems.HUB_CONNECTOR.get()));
        elsewhere.inventory().setStackInSlot(TurretInventory.CONNECTOR,
                HubConnectorItem.link(new ItemStack(BastionItems.HUB_CONNECTOR.get()), level, hub.getBlockPos().east(20)));
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(hub.online(), "not online");
            List<TurretBaseBlockEntity> seen = hub.visibleTurrets(player);
            helper.assertTrue(seen.equals(List.of(linked)), "listed " + seen);
            for (TurretBaseBlockEntity other : List.of(unlinked, elsewhere, none)) {
                helper.assertTrue(hub.reachable(player, other.getBlockPos()) == null, "reachable without a connector to this hub: " + other.getBlockPos());
            }
            ControlHubMenu menu = new ControlHubMenu(0, player.getInventory(), hub);
            int changed = menu.edit(List.of(linked.getBlockPos(), unlinked.getBlockPos(), elsewhere.getBlockPos(), none.getBlockPos()), HubEdit.enabled(false));
            helper.assertTrue(changed == 1 && !linked.enabled() && unlinked.enabled() && elsewhere.enabled() && none.enabled(), "batch reached " + changed);
            for (TurretBaseBlockEntity turret : List.of(linked, unlinked, elsewhere, none)) level.removeBlock(turret.getBlockPos(), false);
            helper.succeed();
        });
    }
```

- [ ] **Step 2: Run to verify it fails.** `./gradlew runGameTestServer --offline`. Expected: only `hubListsOnlyConnectedTurrets` fails ("listed [... four turrets ...]"); the older hub tests pass (their turrets are connected now).

- [ ] **Step 3: Implement.** In `HubInterfaceBlockEntity` (imports `dev.bastion.turret.TurretInventory`):

```java
    /** Whether the turret holds a Hub Connector linked to this hub (PLAN "Hub Connector"). */
    public boolean connected(TurretBaseBlockEntity turret) {
        return level != null && HubConnectorItem.linkedTo(turret.inventory().getStackInSlot(TurretInventory.CONNECTOR), level, worldPosition);
    }
```

In `reachable`, change the last line to

```java
        return level.getBlockEntity(pos) instanceof TurretBaseBlockEntity turret && !turret.isRemoved() && connected(turret)
                && turret.canConfigure(player) ? turret : null;
```

and in `visibleTurrets` the filter to `if (entity instanceof TurretBaseBlockEntity turret && !turret.isRemoved() && connected(turret) && turret.canConfigure(player)) found.add(turret);`; update both javadocs to say "connected to this hub". (Remote turret GUIs, `openTurret` and batch edits go through `reachable`, the roster through `visibleTurrets`.)

`ControlHubScreen.renderMapLabels`, at its start:

```java
        if (menu.turrets().isEmpty()) { // nothing connected yet: say how, over the map
            List<FormattedCharSequence> lines = font.split(Component.translatable("gui.bastion.control_hub.none"), MAP_SIZE - 16);
            int top = MAP_Y + MAP_SIZE / 2 - lines.size() * 5;
            graphics.fill(MAP_X, top - 4, MAP_X + MAP_SIZE, top + lines.size() * 10 + 2, 0xC0101418);
            for (int i = 0; i < lines.size(); i++) {
                graphics.drawString(font, lines.get(i), MAP_X + (MAP_SIZE - font.width(lines.get(i))) / 2, top + i * 10, TEXT, false);
            }
        }
```

Lang, change the existing `gui.bastion.control_hub.none` to "No connected turrets: link a Hub Connector to this hub, then put it in a turret" / "Belum ada turret terhubung: link Konektor Hub ke hub ini, lalu pasang di turret".

Scene (`ControlHubShowcase`, imports `dev.bastion.hub.HubConnectorItem`): replace the `turret(...)` helper with

```java
    /**
     * A floor turret with {@code weapon} (null: none) at {@code health} of its HP, with Creative Ammo unless {@code ammo} is
     * false, holding a connector linked to the T3 hub when {@code connected}.
     */
    private static void turret(ServerLevel level, BlockPos pos, Item weapon, float health, boolean ammo, boolean connected) {
        if (weapon == null) level.setBlock(pos, BastionBlocks.TURRET_BASE.get().defaultBlockState(), 3);
        else Showcase.place(level, pos, Direction.UP, weapon, TurretTier.T2);
        TurretBaseBlockEntity turret = (TurretBaseBlockEntity) level.getBlockEntity(pos);
        if (connected) {
            turret.inventory().setStackInSlot(TurretInventory.CONNECTOR,
                    HubConnectorItem.link(new ItemStack(BastionItems.HUB_CONNECTOR.get()), level, T3_IFACE));
        }
        if (weapon == null) return;
        if (!ammo) turret.inventory().setStackInSlot(TurretInventory.AMMO_START, ItemStack.EMPTY);
        if (health < 1) turret.takeDamage(level.damageSources().generic(), turret.maxHealth() * (1 - health));
    }
```

and add `, true` to the five `turret(...)` calls for the Gun, Machine Gun, Sniper, Shotgun and the unarmed base, `, false` to the Ion Rifle's (one turret in reach without a connector), and change the comment above them to `// Turrets round the T3 hub, in its 7x7 chunks; all but the Ion Rifle hold a connector linked to it.`

- [ ] **Step 4: Run to verify it passes.** `./gradlew build --offline`; `./gradlew runGameTestServer --offline` ("All 152 required tests passed"); `./gradlew runGameTestServer --offline -PwithCompatMods` (152); scene. Read every `showcase_hub_gui_*.png`: header "Satellite T3 · 7x7 chunks · 5 turrets" (the Ion Rifle and old leftovers without connectors are gone), the same dots and rows as before minus the Ion Rifle, the turret GUI with the Connector slot (filled, lamp lit) between the ammo and the modules and nothing overlapping, Batch settings as before, OFFLINE as before.

---

### Task 6: Docs and final verification

**Files:**
- Modify: `docs/PLAN.md`, `docs/TESTING.md`, `docs/release/DESCRIPTION.md`, `docs/release/DESCRIPTION.curseforge.html`, `docs/release/UPLOAD.md`, `docs/HANDOFF.md`

- [ ] **Step 1: PLAN.md.** In section "Turret Control Hub", replace the second sentence of the status line (`**Revisi user 15:01: kubus 3x3x3 + Hub Connector; desain disetujui 15:10; belum diimplementasi.**`) with `**Revisi user 15:01 (kubus 3x3x3 + Hub Connector) selesai YYYY-MM-DD (plan `docs/superpowers/plans/2026-10-10-control-hub-cube-connector.md`): 152 GameTest lolos (juga dengan `-PwithCompatMods`), scene `-Pshowcase=hub` dicek; belum dites user di game.**` (the day it is finished), and note any value changed while building in one line after it.

- [ ] **Step 2: TESTING.md.** Replace the whole `## Turret Control Hub` section with:

```markdown
## Turret Control Hub
- [ ] Recipes at the Module Workstation, own tab: 8 Hub Casings, Hub Power Port, Hub Interface, Satellite Uplink T1 (T2 from a T1, T3 from a T2), 4 Hub Connectors.
- [ ] Build the 3x3x3 cube: the Hub Interface facing you in the middle of the front side of the middle layer (eye level when you stand on the ground), Hub Casings everywhere else with at least one Hub Power Port on the outside, then a Satellite Uplink on the top middle block (its hologram shows both blocks). Before it is done, right-click the Interface with an empty hand: the action bar names the first problem; a hologram shows cyan boxes with the missing block turning inside (bottom layer first), red over a wrong block, amber on every casing while there is no port; boxes go as you place parts, all of it when the hub forms (10 s at most).
- [ ] Forming: a cyan sheet sweeps through the cube, a flash at the dish, the dish unfolds and turns (about one turn per 10 s) with a soft hum, the Interface screen shows a radar, cyan lines along every outer edge of the cube (none around the Interface), a chime. Without FE it is OFFLINE: red blinking screen, dish stopped and drooping, red glow, a power-down sound.
- [ ] FE: a cable or Creative Power Source on a Power Port (up to 2,000 FE/t per port; a port in no hub takes nothing). Upkeep: T1 20, T2 60, T3 150 FE/t (hover the FE bar). Run it dry: OFFLINE, and it stays OFFLINE without flickering even while a trickle of FE comes in; it comes back ONLINE once it holds 5 s of upkeep (T1 2,000, T2 6,000, T3 15,000 FE).
- [ ] Hub Connectors: right-click the Interface with a stack of connectors: the whole stack is linked (action bar, tooltip "Linked to the hub at x, y, z", the icon's lamp lit), and the hub screen does not open; shift + right-click in the air clears the link. Put one in a turret's Connector slot (Status tab, between the ammo and the modules; hover the empty slot for its name; hoppers never touch it).
- [ ] Only turrets with a connector linked to this hub show on the Map and List (header "N turrets"), within the satellite's reach: T1 its chunk, T2 3x3, T3 7x7 chunks (F3 + G). A turret without a connector, with an unlinked one, or linked to another hub does not show; with none: "No connected turrets..." on both tabs. Another player's connected turrets show only once they trust you; an op sees all.
- [ ] Click a dot or a List row: that turret's own GUI opens from the hub; weapon, ammo and modules go in from your inventory, Targeting and Info work; "◀ Map" brings back the hub screen with the same tab, zoom and selection. Walk more than 8 blocks from the Interface, or take the connector out: it closes.
- [ ] List tab: icon, name, coordinates, HP bar, ammo or FE %, state lamp per row; sort by distance / HP / ammo; the checkbox selects; the wheel scrolls.
- [ ] Batch settings: select turrets (Shift+click, Shift+drag a box, checkboxes, Select all), then change a category, the player mode, the priority, add or remove a trusted player, cycle a mob rule (none -> always -> never -> none), Turn on / Turn off: only that setting changes on every selected turret. Values that differ show "Mixed", names and rules only some have show "(some)".
- [ ] Break any part (bottom, middle or top layer, or the satellite): the hub stops (lines off, dish folds), open hub and turret screens close; only the broken block drops. Breaking the Interface loses its FE. A destroyed turret drops its connector with its modules; a mined turret keeps it inside.
- [ ] A world from before this update: every turret opens with an empty Connector slot, nothing else moved.
- [ ] Dedicated server (`runServer` + a client) and a second, untrusted player: they see none of your turrets on your hub. Save and reload: the hub is formed again, FE and connectors kept. Server config: `controlHubEnergyCapacity`, `controlHubPortMaxInput`, `controlHubUpkeep`, `controlHubRestartTicks`, `controlHubRange`.
```

- [ ] **Step 3: Store texts and changelog.** In `docs/release/DESCRIPTION.md`, replace the paragraph under `## Turret Control Hub` with:

```markdown
Build a 3x3x3 cube: a Hub Interface in the middle of one side at eye level, Hub Casings with at least one Hub Power Port, and a Satellite Uplink on top. Right-click an unfinished Interface and a hologram shows what is missing. Link Hub Connectors to the hub and put one in each turret you want on it: once the hub has FE, its screen shows those turrets within 1, 3x3 or 7x7 chunks (Satellite Uplink T1, T2, T3) on a map and a list, coloured by health, with their ammo and state. Click one to open its own screen from the hub: swap modules, top up ammo or change its targeting without walking over. Or select many and change their categories, priority, mob rules, trusted players or on/off all at once.
```

and the `<p>` after `<h2>Turret Control Hub</h2>` in `DESCRIPTION.curseforge.html` with the same text (HTML-escaped: no `<`, `>` or `&` in it). In `docs/release/UPLOAD.md`, "Changelog: 0.1.2", replace the Turret Control Hub entry with:

```
- Turret Control Hub: a 3x3x3 multiblock you build part by part (Hub Interface at eye level, Hub Casings, at least one Hub
  Power Port, a Satellite Uplink T1/T2/T3 on top; a hologram shows what is missing). Turrets join it through a Hub
  Connector linked to the hub (a new Connector slot on every turret). With FE (20/60/150 FE/t) it shows the connected
  turrets within 1, 3x3 or 7x7 chunks on a map and a list (health colours, ammo, state), opens any of them from the hub,
  and changes targeting or on/off on many selected turrets at once.
```

- [ ] **Step 4: HANDOFF.md.** Update "State at a glance" (152 tests, the cube + connector revision built and uncommitted, not yet tested in game; remove the "Revision requested … NOT implemented" bullet) and rewrite NOW 0 for the finished revision: the cube layout (`ControlHubStructure.slots` order, `satellite(center)`, x/y/z parts, `notifyAround` reach), `HubConnectorItem` (NBT `HubLink`, `onItemUseFirst`, shift-use clears, item property), `TurretInventory.CONNECTOR` (14, size 15, `setSize` keeps old saves), `HubInterfaceBlockEntity.connected`, the scene changes (`FIELD`, Interfaces one block up, Ion Rifle unconnected), anything the scene review changed, and the deferred minors still open from the first review.

- [ ] **Step 5: Final verification.** Run, and read each result before claiming anything:
  1. `./gradlew build --offline`
  2. `./gradlew runGameTestServer --offline` → "All 152 required tests passed"
  3. `./gradlew runGameTestServer --offline -PwithCompatMods` → 152
  4. `./gradlew runClient --offline -Pshowcase=hub -PshowcaseWorld=showcase_fx` → read every `showcase_hub_*.png`
  5. `./gradlew runClient --offline -Pshowcase=placement -PshowcaseWorld=showcase_fx` → the placement hologram unchanged (if the main-hand ghosts are missing, the scratch world saved another hotbar slot: run the scene once more; see HANDOFF deferred minors)
  6. `git status`: only files of this plan and the earlier hub plan changed or added, `modlist.html` untouched; nothing committed.

  Then a fresh reviewer on the whole revision (working tree against `4abf5ab`; build the review package by hand as the hub plan's ledger describes, since nothing is committed), the fix pass for its Critical/Important findings (each test-first), and the in-game test checklist for the user (Indonesian, short, from the TESTING.md section). Wait for "commit".
