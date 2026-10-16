# Turret Control Hub Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A hand-built 3x3 multiblock (Hub Interface + Hub Casings + at least one Hub Power Port, a Satellite Uplink T1/T2/T3 standing on the middle block) that, while it has FE, shows every turret the player may open within 1 / 3x3 / 7x7 chunks on a map and a list, opens any of them in their own GUI from the hub, and changes targeting settings on many selected turrets at once.

**Architecture:** New package `dev.bastion.hub`. `ControlHubStructure` is the single layout check (server forms the hub; client draws the blueprint hologram); the Interface's block entity re-checks on neighbour changes, pays upkeep and mirrors ONLINE/OFFLINE/UNFORMED in its block state (and the satellite's). The screen is a slot-less `ControlHubMenu` fed every 10 ticks by an S->C `HubRoster` (turret entries; each turret's `TargetFilter` only when it changed). Remote access reuses the existing `TurretMenu`/`TurretScreen`: the menu carries an optional hub link and `stillValid` checks the hub instead of the 8-block distance; `TurretConfigUpdate` now only needs the sender's open, valid turret menu. Batch edits are one `HubEdit` (a single setting change) per click, applied server-side to every selected turret the player can reach.

**Tech Stack:** Forge 1.20.1 (47.4.26, parchment 2023.09.03), Java 17, GeckoLib 4, Forge GameTests, Python 3 generators in `tools/` (numpy, pillow, soundfile).

**Spec:** `docs/PLAN.md` → section 8, "Turret Control Hub" (design approved by the user 2026-10-10 11:43, spec approved 11:51).

## Global Constraints

- Reach per Satellite Uplink tier, counted in chunks around the satellite's chunk (the hub's middle column): T1 1, T2 3x3, T3 7x7; config `controlHubRange` [1, 3, 7] (odd only).
- FE: Interface stores `controlHubEnergyCapacity` 200,000; each Hub Power Port takes up to `controlHubPortMaxInput` 2,000 FE/tick from cables; a formed hub pays `controlHubUpkeep` [20, 60, 150] FE/tick (T1, T2, T3); less FE than one tick's upkeep = OFFLINE. Unformed hubs keep their FE and pay nothing. Breaking the Interface loses its FE.
- Layout: one layer 3x3; Interface in the middle of one side, facing out (towards the player who placed it), the 3x3 behind it; the other 8 blocks (middle included) Hub Casing or Hub Power Port, at least one port; Satellite Uplink (1x1x2) standing on the middle block. One block belongs to one hub only.
- Turrets shown: every `TurretBaseBlockEntity` in a loaded chunk inside the reach that `canConfigure(player)` accepts; at most 256, nearest the satellite first; the header shows "256+" past that. The hub never loads chunks.
- Server rules: hub screen opens when formed (ONLINE or OFFLINE) and the player is within 8 blocks of the Interface; turret data and every action only while ONLINE; every remote action re-checks hub + reach + loaded + `canConfigure`.
- Colours: hologram cyan `EmissiveLayer.MODEL_GLOW` (0x4FD8FF), blocked red 0xFF3A30, missing-port amber 0xFFB347; map dots green ≥ 50 % HP, yellow 25-50 % (0xFFC21F), red blinking < 25 % (`TurretBaseBlockEntity.DAMAGED_FRACTION`/`CRITICAL_FRACTION`), grey = DISABLED, orange "!" = NO_AMMO, white ring = selected. Models: gunmetal + one cyan glow (palette rule).
- Recipes (Module Workstation, own tab "bastion:hub_interface"): 4 Hub Casing = 4 iron ingot, 2 copper ingot, 1 redstone (60 t, 1,000 FE); Hub Power Port = 1 Hub Casing, 1 redstone block, 2 copper ingot (60 t, 1,500); Hub Interface = 1 Hub Casing, 1 diamond, 3 glass pane, 2 redstone, 1 comparator (100 t, 3,000); Satellite Uplink T1 = 3 iron ingot, 2 copper ingot, 1 ender pearl, 1 redstone (100 t, 3,000); T2 = T1, 2 diamond, 2 gold ingot, 1 ender eye (140 t, 6,000); T3 = T2, 1 netherite scrap, 2 diamond, 2 ender eye (180 t, 10,000).
- Ids: blocks/items `hub_interface`, `hub_casing`, `hub_power_port`, `satellite_uplink_t1`, `_t2`, `_t3`; block entities `hub_interface`, `hub_power_port`, `satellite_uplink`; menu `control_hub`; sounds `control_hub.online`, `control_hub.offline`, `control_hub.dish_loop`, `control_hub.ui_select`. Names en_us / id_id: Hub Interface / Antarmuka Hub, Hub Casing / Rangka Hub, Hub Power Port / Port Daya Hub, Satellite Uplink T1-T3 / Uplink Satelit T1-T3, screen title "Turret Control Hub".
- NO-VANILLA: own textures (generated), own sounds (placeholders from `tools/gen_sounds.py`, listed in `docs/SOUND_SHEET.md`), effects through our VFX/render code. Client code only in `dev.bastion.client` (and `src/dev`), reached from common code through `DistExecutor`.
- Network protocol 9 -> **10** (two new packets, `TurretMenu` open data grows).
- Never commit until the user says "commit"; never commit `modlist.html`; never touch `run/saves/showcase` (scenes run with `-PshowcaseWorld=showcase_fx`); Gradle always `--offline`; `tools/gen_sounds.py` without `--force` (the user's replaced SFX must survive).

## Review Focus

1. **A hub over a chunk border** (3x3 split between two chunks, or a part's chunk loading after the Interface's): it must form, and its reach is counted from the satellite's chunk. Test: `hubAcrossChunkBorder` (Task 1); the 20-tick safety re-check covers late-loading parts.
2. **Two hubs sharing blocks / a hub whose Interface was broken**: shared blocks report "belongs to another hub" and only one hub forms; breaking an Interface frees its parts, and a part left marked by a vanished hub never blocks a new one. Tests: `partOfAnotherHubIsWrong`, `brokenInterfaceFreesItsParts` (Task 1).
3. **A remote turret GUI whose situation changes while it is open** (player walks away from the Interface, FE runs out, a hub part is broken, the turret is out of reach): the GUI closes, no setting gets through. Tests: `remoteTurretGuiFollowsTheHub`, `configUpdateNeedsAValidGui` (Task 5).
4. **Crafted or stale packets** (a batch edit naming turrets out of reach or someone else's, an illegal trusted name, an unknown mob id, an out-of-range enum, more than 256 positions): nothing out of reach changes and bad edits are refused. Tests: `batchEditChangesOnlyThatSetting`, `batchEditRejectsBadInput` (Task 7); `HubAction`/`HubRoster` decoders throw past 256 entries (Tasks 4, 7).
5. **Many turrets with long rule lists** (a base of 256 turrets each with 128 rules): the roster never sends more than 32 filters per packet, so a packet stays far below the 1 MB limit, and unchanged filters are not re-sent. Test: `hubListsOnlyTurretsThePlayerMayOpen` checks send-once / send-on-change (Task 4); `ControlHubMenu.FILTERS_PER_SEND` caps the size.

---

## File map

| File | Change | Responsibility |
|---|---|---|
| `src/main/java/dev/bastion/hub/ControlHubStructure.java` | create | layout geometry, the check, form/unform, neighbour notify, reach math, action-bar message |
| `src/main/java/dev/bastion/hub/HubCasingBlock.java`, `HubPowerPortBlock.java`, `HubPowerPortBlockEntity.java` | create | casing/port blocks (x/z place in a formed hub), port FE capability |
| `src/main/java/dev/bastion/hub/HubInterfaceBlock.java`, `HubInterfaceBlockEntity.java` | create | controller: status, FE, upkeep, recheck, reach, visible turrets, menu provider |
| `src/main/java/dev/bastion/hub/SatelliteUplinkBlock.java`, `SatelliteUplinkBlockEntity.java`, `SatelliteUplinkItem.java` | create | 1x1x2 satellite (placement footprint, halves), GeckoLib animatable, item |
| `src/main/java/dev/bastion/hub/HubEdit.java`, `FilterMix.java` | create | one batch change (validate, apply), what selected filters share |
| `src/main/java/dev/bastion/menu/ControlHubMenu.java` | create | slot-less hub menu: server roster + remote open + batch edit, client mirror |
| `src/main/java/dev/bastion/network/HubRoster.java`, `HubAction.java` | create | S->C roster, C->S open/back/edit |
| `src/main/java/dev/bastion/menu/TurretMenu.java`, `network/TurretConfigUpdate.java`, `network/BastionNetwork.java` | modify | hub link + stillValid, menu-based config check, packets + protocol 10 |
| `src/main/java/dev/bastion/config/BastionConfig.java`, `registry/BastionBlocks.java`, `BastionBlockEntities.java`, `BastionItems.java`, `BastionMenus.java`, `BastionSounds.java` | modify | config + registrations |
| `src/main/java/dev/bastion/client/screen/ControlHubScreen.java`, `HubMap.java`, `HubSelection.java`, `MobList.java` | create | hub screen (map, list, batch), terrain map texture, kept state, shared mob list |
| `src/main/java/dev/bastion/client/screen/TurretScreen.java` | modify | "◀ Map" button, MobList |
| `src/main/java/dev/bastion/client/render/HubBlueprint.java`, `SatelliteUplinkRenderer.java`, `SatelliteUplinkModel.java`, `SatelliteUplinkItemRenderer.java`, `PlacementPreview.java` | create/modify | blueprint + scan hologram, satellite rendering, shared box drawing |
| `src/main/java/dev/bastion/client/ControlHubEffects.java`, `ClientPacketHandler.java`, `BastionClient.java` | create/modify | formed flash, dish hum, roster handler, screen/renderer registration |
| `src/main/java/dev/bastion/gametest/ControlHubTests.java` | create | 18 GameTests |
| `tools/gen_control_hub_models.py`, `tools/blockout/design_control_hub.py` | create | casing/port/interface models + blockstates; satellite geos + clips |
| `tools/gen_textures.py`, `tools/gen_sounds.py`, `tools/gen_recipes.py`, `tools/fit_item_display.py` | modify | textures + GUI, 4 placeholder sounds, 6 recipes, satellite item displays |
| `src/main/resources/assets/bastion/{blockstates,models,textures,geo,animations,sounds}/…`, `sounds.json`, `lang/en_us.json`, `lang/id_id.json` | create/modify | assets and strings |
| `src/main/resources/data/bastion/loot_tables/blocks/*.json`, `data/minecraft/tags/blocks/mineable/pickaxe.json` | create/modify | drops, pickaxe |
| `src/dev/java/dev/bastion/dev/ControlHubShowcase.java`, `Showcase.java` | create/modify | scene `-Pshowcase=hub` |
| `docs/PLAN.md`, `docs/TESTING.md`, `docs/SOUND_SHEET.md`, `docs/release/*`, `docs/HANDOFF.md` | modify | docs |

Commands: build `./gradlew build --offline`; all GameTests `./gradlew runGameTestServer --offline` (the log ends "All N required tests passed"); with the compat mods `./gradlew runGameTestServer --offline -PwithCompatMods`. Baseline: **128** tests; this plan adds 18 (146).

Stages (spec "Tahap implementasi"): **Stage 1** = Tasks 1-3 (blocks, layout, FE, assets, blueprint), **Stage 2** = Tasks 4-6 (reach, roster, remote turret GUI, hub screen map + list), **Stage 3** = Tasks 7-8 (batch settings, docs). After each stage: build + all GameTests + the `hub` scene, then the user can test in game.

---

## Stage 1: blocks, layout, FE

### Task 1: Hub blocks, the layout check, forming and upkeep status

**Files:**
- Create: `src/main/java/dev/bastion/hub/ControlHubStructure.java`, `HubCasingBlock.java`, `HubPowerPortBlock.java`, `HubInterfaceBlock.java`, `HubInterfaceBlockEntity.java`, `SatelliteUplinkBlock.java`, `SatelliteUplinkBlockEntity.java`, `src/main/java/dev/bastion/gametest/ControlHubTests.java`, `src/main/resources/data/bastion/loot_tables/blocks/{hub_interface,hub_casing,hub_power_port,satellite_uplink_t1,satellite_uplink_t2,satellite_uplink_t3}.json`
- Modify: `src/main/java/dev/bastion/config/BastionConfig.java`, `registry/BastionBlocks.java`, `registry/BastionBlockEntities.java`, `registry/BastionItems.java`, `registry/BastionSounds.java`, `src/main/resources/assets/bastion/sounds.json`, `tools/gen_sounds.py`, `src/main/resources/data/minecraft/tags/blocks/mineable/pickaxe.json`, `src/main/resources/assets/bastion/lang/en_us.json`, `lang/id_id.json`

**Interfaces:**
- Produces: `HubInterfaceBlock.Status {UNFORMED, ONLINE, OFFLINE}`, `HubInterfaceBlock.FACING`, `HubInterfaceBlock.STATUS`; `HubCasingBlock.X`, `HubCasingBlock.Z` (= `FeedHubBlock.X/Z`, `FeedHubBlock.Part`); `SatelliteUplinkBlock.PART` (0 bottom, 1 top), `SatelliteUplinkBlock.STATUS`, `int SatelliteUplinkBlock.tier()`, `void SatelliteUplinkBlock.placeAt(Level, BlockPos bottom)`.
- Produces: `ControlHubStructure.Kind {NEED_SATELLITE, NEED_BLOCK, WRONG, OTHER_HUB, NEED_PORT}`, `record Issue(BlockPos pos, Kind kind)`, `record Result(List<Issue> issues, int tier)` with `boolean formed()`; static `BlockPos center(BlockPos iface, Direction facing)`, `List<BlockPos> slots(BlockPos iface, Direction facing)`, `Result check(Level, BlockPos iface, Direction facing)`, `@Nullable HubInterfaceBlockEntity hubAt(Level, BlockPos center)`, `BlockPos partCenter(BlockPos, BlockState)`, `void notifyAround(Level, BlockPos)`, `boolean covers(BlockPos center, int side, BlockPos pos)`, `Component message(Result)`; package-private `form`, `unform`, `setSatellite`.
- Produces: `HubInterfaceBlockEntity`: `facing()`, `status()`, `formed()`, `online()`, `tier()`, `center()`, `side()`, `energy()`, `capacity()`, `upkeep()`, `setEnergy(int)`, `recheck()`, static `serverTick(...)`.
- Produces: `BastionConfig.CONTROL_HUB_ENERGY_CAPACITY`, `CONTROL_HUB_PORT_MAX_INPUT`, static `int controlHubUpkeep(int tier)`, `int controlHubRange(int tier)`.
- Produces: `BastionBlocks.HUB_CASING, HUB_POWER_PORT, HUB_INTERFACE, SATELLITE_UPLINK_T1..T3`; `BastionBlockEntities.HUB_INTERFACE, SATELLITE_UPLINK`; `BastionItems.HUB_INTERFACE, HUB_CASING, HUB_POWER_PORT, SATELLITE_UPLINK_T1..T3`; `BastionSounds.CONTROL_HUB_ONLINE, CONTROL_HUB_OFFLINE, CONTROL_HUB_DISH_LOOP, CONTROL_HUB_UI_SELECT`.
- Produces (tests): `ControlHubTests.hub(ServerLevel, BlockPos ifaceAbs, Direction, int tier)`, `hub(GameTestHelper, BlockPos ifaceRel, int tier)` (facing north), `satellite(int tier)`.

- [ ] **Step 1: Write the failing tests.** Create `src/main/java/dev/bastion/gametest/ControlHubTests.java`:

```java
package dev.bastion.gametest;

import dev.bastion.Bastion;
import dev.bastion.hub.ControlHubStructure;
import dev.bastion.hub.ControlHubStructure.Kind;
import dev.bastion.hub.HubCasingBlock;
import dev.bastion.hub.HubInterfaceBlock;
import dev.bastion.hub.HubInterfaceBlock.Status;
import dev.bastion.hub.HubInterfaceBlockEntity;
import dev.bastion.hub.SatelliteUplinkBlock;
import dev.bastion.registry.BastionBlocks;
import dev.bastion.registry.BastionItems;
import dev.bastion.turret.FeedHubBlock.Part;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Turret Control Hub (PLAN "Turret Control Hub"): layout, forming, energy, reach, remote turret GUIs, batch settings. */
@GameTestHolder(Bastion.MOD_ID)
@PrefixGameTestTemplate(false)
public class ControlHubTests {
    private static final String ARENA = "arena", YARD = "yard", EMPTY = "empty";
    /** Arena: the Interface faces north on the floor (y 2), its 3x3 runs south (z 3..5), the satellite stands on (6, 3, 4). */
    private static final BlockPos IFACE = new BlockPos(6, 2, 3);

    static SatelliteUplinkBlock satellite(int tier) {
        return (tier == 3 ? BastionBlocks.SATELLITE_UPLINK_T3 : tier == 2 ? BastionBlocks.SATELLITE_UPLINK_T2 : BastionBlocks.SATELLITE_UPLINK_T1).get();
    }

    /**
     * A hub with its Interface at absolute {@code iface} facing {@code facing}: casings, a Power Port in the first slot
     * (front row, left as seen from in front) and, unless {@code tier} is 0, a satellite of that tier.
     */
    static HubInterfaceBlockEntity hub(ServerLevel level, BlockPos iface, Direction facing, int tier) {
        level.setBlock(iface, BastionBlocks.HUB_INTERFACE.get().defaultBlockState().setValue(HubInterfaceBlock.FACING, facing), 3);
        List<BlockPos> slots = ControlHubStructure.slots(iface, facing);
        for (int i = 0; i < slots.size(); i++) {
            level.setBlock(slots.get(i), (i == 0 ? BastionBlocks.HUB_POWER_PORT : BastionBlocks.HUB_CASING).get().defaultBlockState(), 3);
        }
        if (tier > 0) satellite(tier).placeAt(level, ControlHubStructure.center(iface, facing).above());
        return (HubInterfaceBlockEntity) level.getBlockEntity(iface);
    }

    /** A hub facing north with its Interface at test position {@code iface}. */
    static HubInterfaceBlockEntity hub(GameTestHelper helper, BlockPos iface, int tier) {
        return hub(helper.getLevel(), helper.absolutePos(iface), Direction.NORTH, tier);
    }

    private static void assertFirst(GameTestHelper helper, HubInterfaceBlockEntity hub, Kind kind, BlockPos at) {
        List<ControlHubStructure.Issue> issues = ControlHubStructure.check(helper.getLevel(), hub.getBlockPos(), Direction.NORTH).issues();
        helper.assertTrue(!issues.isEmpty() && issues.get(0).kind() == kind && issues.get(0).pos().equals(at),
                "expected " + kind + " at " + at + " first, got " + issues);
    }

    /** Interface, eight parts with one Power Port, a satellite: formed a tick later (OFFLINE: no FE), every part knows its place. */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "hubFormsWhenComplete")
    public static void hubFormsWhenComplete(GameTestHelper helper) {
        HubInterfaceBlockEntity hub = hub(helper, IFACE, 1);
        helper.succeedWhen(() -> {
            helper.assertTrue(hub.status() == Status.OFFLINE, "expected formed without FE (OFFLINE), got " + hub.status());
            helper.assertTrue(hub.tier() == 1, "tier " + hub.tier());
            BlockPos center = hub.center();
            for (BlockPos pos : ControlHubStructure.slots(hub.getBlockPos(), Direction.NORTH)) {
                BlockState state = helper.getLevel().getBlockState(pos);
                helper.assertTrue(state.getValue(HubCasingBlock.X) == Part.of(pos.getX() - center.getX() + 1, 3)
                        && state.getValue(HubCasingBlock.Z) == Part.of(pos.getZ() - center.getZ() + 1, 3), "wrong place at " + pos + ": " + state);
            }
            helper.assertTrue(helper.getLevel().getBlockState(center.above()).getValue(SatelliteUplinkBlock.STATUS) == Status.OFFLINE,
                    "the satellite did not follow the hub");
        });
    }

    /** Four broken hubs in a row: no satellite, satellite on a corner, stone in the middle, no Power Port. None forms. */
    @GameTest(template = YARD, timeoutTicks = 60, batch = "hubNamesTheFirstProblem")
    public static void hubNamesTheFirstProblem(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        HubInterfaceBlockEntity noSatellite = hub(helper, new BlockPos(3, 2, 3), 0);
        HubInterfaceBlockEntity offCentre = hub(helper, new BlockPos(9, 2, 3), 0);
        satellite(1).placeAt(level, ControlHubStructure.slots(offCentre.getBlockPos(), Direction.NORTH).get(7).above());
        HubInterfaceBlockEntity stone = hub(helper, new BlockPos(15, 2, 3), 1);
        level.setBlock(stone.center(), Blocks.STONE.defaultBlockState(), 3);
        HubInterfaceBlockEntity noPort = hub(helper, new BlockPos(21, 2, 3), 1);
        level.setBlock(ControlHubStructure.slots(noPort.getBlockPos(), Direction.NORTH).get(0), BastionBlocks.HUB_CASING.get().defaultBlockState(), 3);
        helper.runAtTickTime(5, () -> {
            assertFirst(helper, noSatellite, Kind.NEED_SATELLITE, noSatellite.center().above());
            assertFirst(helper, offCentre, Kind.NEED_SATELLITE, offCentre.center().above());
            assertFirst(helper, stone, Kind.WRONG, stone.center());
            List<ControlHubStructure.Issue> issues = ControlHubStructure.check(level, noPort.getBlockPos(), Direction.NORTH).issues();
            helper.assertTrue(issues.size() == 8 && issues.stream().allMatch(i -> i.kind() == Kind.NEED_PORT), "no port: " + issues);
            for (HubInterfaceBlockEntity hub : List.of(noSatellite, offCentre, stone, noPort)) {
                helper.assertTrue(hub.status() == Status.UNFORMED, "formed anyway at " + hub.getBlockPos());
            }
            helper.succeed();
        });
    }

    /** A second hub built against a formed one shares a column of its casings: those count as another hub's, it never forms. */
    @GameTest(template = YARD, timeoutTicks = 80, batch = "partOfAnotherHubIsWrong")
    public static void partOfAnotherHubIsWrong(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        HubInterfaceBlockEntity first = hub(helper, new BlockPos(5, 2, 3), 1);
        BlockPos second = helper.absolutePos(new BlockPos(7, 2, 3)); // its 3x3 spans x 6..8: the first one's x 6 column is shared
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(first.formed(), "the first hub did not form");
            level.setBlock(second, BastionBlocks.HUB_INTERFACE.get().defaultBlockState().setValue(HubInterfaceBlock.FACING, Direction.NORTH), 3);
            List<BlockPos> slots = ControlHubStructure.slots(second, Direction.NORTH);
            for (int i = 0; i < slots.size(); i++) {
                if (level.getBlockState(slots.get(i)).isAir()) {
                    level.setBlock(slots.get(i), (i == 0 ? BastionBlocks.HUB_POWER_PORT : BastionBlocks.HUB_CASING).get().defaultBlockState(), 3);
                }
            }
            satellite(1).placeAt(level, ControlHubStructure.center(second, Direction.NORTH).above());
        });
        helper.runAtTickTime(15, () -> {
            HubInterfaceBlockEntity hub = (HubInterfaceBlockEntity) level.getBlockEntity(second);
            List<ControlHubStructure.Issue> issues = ControlHubStructure.check(level, second, Direction.NORTH).issues();
            helper.assertTrue(issues.size() == 3 && issues.stream().allMatch(i -> i.kind() == Kind.OTHER_HUB),
                    "expected the 3 shared blocks as another hub's: " + issues);
            helper.assertTrue(!hub.formed() && first.formed(), "second formed: " + hub.formed() + ", first still formed: " + first.formed());
            helper.succeed();
        });
    }

    /** One part broken: the hub breaks up within a tick and its parts forget their places; put back, it forms again. */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = "breakingAPartUnformsTheHub")
    public static void breakingAPartUnformsTheHub(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        HubInterfaceBlockEntity hub = hub(helper, IFACE, 1);
        BlockPos part = ControlHubStructure.slots(hub.getBlockPos(), Direction.NORTH).get(2);
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(hub.formed(), "did not form");
            level.removeBlock(part, false);
        });
        helper.runAtTickTime(7, () -> {
            helper.assertTrue(hub.status() == Status.UNFORMED, "still formed after a part was broken");
            for (BlockPos pos : ControlHubStructure.slots(hub.getBlockPos(), Direction.NORTH)) {
                BlockState state = level.getBlockState(pos);
                helper.assertTrue(!(state.getBlock() instanceof HubCasingBlock) || state.getValue(HubCasingBlock.X) == Part.ALONE,
                        "part still formed at " + pos);
            }
            level.setBlock(part, BastionBlocks.HUB_CASING.get().defaultBlockState(), 3);
        });
        helper.runAtTickTime(10, () -> {
            helper.assertTrue(hub.formed(), "did not form again with the part back");
            helper.succeed();
        });
    }

    /**
     * Breaking the Interface frees the parts and the satellite. A part still marked by a hub that is gone (set by hand here)
     * does not block a new Interface.
     */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = "brokenInterfaceFreesItsParts")
    public static void brokenInterfaceFreesItsParts(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        HubInterfaceBlockEntity hub = hub(helper, IFACE, 1);
        BlockPos iface = hub.getBlockPos(), center = hub.center();
        List<BlockPos> slots = ControlHubStructure.slots(iface, Direction.NORTH);
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(hub.formed(), "did not form");
            level.removeBlock(iface, false);
        });
        helper.runAtTickTime(7, () -> {
            for (BlockPos pos : slots) helper.assertTrue(level.getBlockState(pos).getValue(HubCasingBlock.X) == Part.ALONE, "still formed at " + pos);
            helper.assertTrue(level.getBlockState(center.above()).getValue(SatelliteUplinkBlock.STATUS) == Status.UNFORMED, "satellite still formed");
            BlockPos stale = slots.get(3);
            level.setBlock(stale, level.getBlockState(stale).setValue(HubCasingBlock.X, Part.LOW).setValue(HubCasingBlock.Z, Part.LOW), 2);
            level.setBlock(iface, BastionBlocks.HUB_INTERFACE.get().defaultBlockState().setValue(HubInterfaceBlock.FACING, Direction.NORTH), 3);
        });
        helper.runAtTickTime(10, () -> {
            helper.assertTrue(level.getBlockEntity(iface) instanceof HubInterfaceBlockEntity again && again.formed(),
                    "a part marked by a vanished hub blocked the new one");
            helper.succeed();
        });
    }

    /** A 3x3 whose columns straddle a chunk border forms like any other. */
    @GameTest(template = YARD, timeoutTicks = 60, batch = "hubAcrossChunkBorder")
    public static void hubAcrossChunkBorder(GameTestHelper helper) {
        int border = Math.floorMod(-helper.absolutePos(BlockPos.ZERO).getX(), 16); // first test x where a chunk starts
        if (border < 2) border += 16;
        HubInterfaceBlockEntity hub = hub(helper, new BlockPos(border, 2, 3), 1); // columns border-1 .. border+1
        helper.succeedWhen(() -> helper.assertTrue(hub.formed(), "a hub over a chunk border did not form"));
    }

    /** Breaking the satellite's top half takes the bottom too, and the satellite drops once. */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "satelliteHalvesBreakTogether")
    public static void satelliteHalvesBreakTogether(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos bottom = helper.absolutePos(new BlockPos(6, 2, 3));
        AABB area = new AABB(bottom).inflate(3);
        level.getEntitiesOfClass(ItemEntity.class, area).forEach(Entity::discard); // left over from an earlier batch
        satellite(2).placeAt(level, bottom);
        level.destroyBlock(bottom.above(), true);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(level.getBlockState(bottom).isAir() && level.getBlockState(bottom.above()).isAir(), "a half stayed");
            List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, area);
            helper.assertTrue(drops.size() == 1 && drops.get(0).getItem().is(BastionItems.SATELLITE_UPLINK_T2.get()), "drops: " + drops);
            drops.forEach(Entity::discard);
            helper.succeed();
        });
    }
}
```

- [ ] **Step 2: Run to verify it fails.** `./gradlew runGameTestServer --offline`. Expected: compile errors (`dev.bastion.hub` does not exist, `BastionBlocks.HUB_INTERFACE` undefined).

- [ ] **Step 3: Config.** In `BastionConfig` add the fields (next to the Feed Hub ones):

```java
    public static final ForgeConfigSpec.IntValue CONTROL_HUB_ENERGY_CAPACITY;
    public static final ForgeConfigSpec.IntValue CONTROL_HUB_PORT_MAX_INPUT;
    private static final ForgeConfigSpec.ConfigValue<List<? extends Number>> CONTROL_HUB_UPKEEP;
    private static final ForgeConfigSpec.ConfigValue<List<? extends Number>> CONTROL_HUB_RANGE;
```

and in the static block, right after `REPULSOR_HEAVY_PUSH` (before `b.push("tiers")`):

```java
        CONTROL_HUB_ENERGY_CAPACITY = b
                .comment("FE a Turret Control Hub's Interface stores (PLAN \"Turret Control Hub\").")
                .defineInRange("controlHubEnergyCapacity", 200000, 0, Integer.MAX_VALUE);
        CONTROL_HUB_PORT_MAX_INPUT = b
                .comment("Most FE each Hub Power Port takes from cables per tick.")
                .defineInRange("controlHubPortMaxInput", 2000, 0, Integer.MAX_VALUE);
        CONTROL_HUB_UPKEEP = b
                .comment("FE per tick a formed Control Hub uses, per Satellite Uplink tier T1, T2, T3; without it the hub goes offline.")
                .defineList("controlHubUpkeep", List.<Number>of(20, 60, 150), o -> o instanceof Number n && n.intValue() >= 0);
        CONTROL_HUB_RANGE = b
                .comment("Side of the square of chunks a Control Hub reaches, centred on its satellite's chunk, per tier T1, T2, T3 (odd).")
                .defineList("controlHubRange", List.<Number>of(1, 3, 7), o -> o instanceof Number n && n.intValue() >= 1 && n.intValue() % 2 == 1);
```

and at the end of the class (import `net.minecraft.util.Mth`):

```java
    /** FE per tick of a formed Control Hub with a tier 1-3 satellite. */
    public static int controlHubUpkeep(int tier) {
        return tierInt(CONTROL_HUB_UPKEEP, tier, 0);
    }

    /** Chunks per side a Control Hub reaches with a tier 1-3 satellite; always odd, so the satellite's chunk is the middle one. */
    public static int controlHubRange(int tier) {
        int side = Math.max(1, tierInt(CONTROL_HUB_RANGE, tier, 1));
        return side % 2 == 0 ? side + 1 : side;
    }

    private static int tierInt(ForgeConfigSpec.ConfigValue<List<? extends Number>> list, int tier, int fallback) {
        List<? extends Number> values = list.get();
        int index = Mth.clamp(tier, 1, 3) - 1;
        return index < values.size() ? values.get(index).intValue() : fallback;
    }
```

(Numbers, not Integers: TOML may hand back Longs.)

- [ ] **Step 4: `ControlHubStructure`.** Create `src/main/java/dev/bastion/hub/ControlHubStructure.java`:

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
 * Turret Control Hub layout (PLAN "Turret Control Hub"): a 3x3 layer whose front row has the Hub Interface in its middle
 * (facing out, the layer behind it), every other block a Hub Casing or Hub Power Port (at least one port), and a Satellite
 * Uplink standing on the middle block. One check serves the Interface (forming, server) and the blueprint hologram
 * (client). A formed casing or port carries its place in the 3x3 in its block state (x, z as in FeedHubBlock), which is
 * also how a hub tells its own parts from another hub's.
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

    /** The middle block of the layer: one block behind the Interface. */
    public static BlockPos center(BlockPos interfacePos, Direction facing) {
        return interfacePos.relative(facing.getOpposite());
    }

    /** The eight layer blocks besides the Interface: front row first, left to right as seen standing in front of it. */
    public static List<BlockPos> slots(BlockPos interfacePos, Direction facing) {
        Direction back = facing.getOpposite(), right = back.getClockWise();
        List<BlockPos> slots = new ArrayList<>(8);
        for (int row = 0; row < 3; row++) {
            for (int col = -1; col <= 1; col++) {
                if (row != 0 || col != 0) slots.add(interfacePos.relative(back, row).relative(right, col));
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
        BlockPos satellite = center.above();
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
        List<BlockPos> casings = new ArrayList<>(8);
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

    /** The middle block of the 3x3 a formed part sits in. */
    public static BlockPos partCenter(BlockPos pos, BlockState state) {
        return pos.offset(step(state.getValue(HubCasingBlock.X)), 0, step(state.getValue(HubCasingBlock.Z)));
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

    /** Gives every part its place in the 3x3 (clients only: no neighbour updates). The caller sets the status. */
    static void form(Level level, BlockPos interfacePos, Direction facing) {
        BlockPos center = center(interfacePos, facing);
        for (BlockPos pos : slots(interfacePos, facing)) {
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof HubCasingBlock)) continue;
            BlockState formed = state.setValue(HubCasingBlock.X, Part.of(pos.getX() - center.getX() + 1, 3))
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
                level.setBlock(pos, state.setValue(HubCasingBlock.X, Part.ALONE).setValue(HubCasingBlock.Z, Part.ALONE), Block.UPDATE_CLIENTS);
            }
        }
        setSatellite(level, center, Status.UNFORMED);
    }

    /** The satellite on {@code center} shows the hub's status (dish folded, spinning or stopped). */
    static void setSatellite(Level level, BlockPos center, Status status) {
        BlockPos pos = center.above();
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
        for (int dy = 0; dy <= 2; dy++) { // the layer itself, or a satellite one or two blocks above it
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos at = pos.offset(dx, -dy, dz);
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

- [ ] **Step 5: Casing and port blocks.** Create `src/main/java/dev/bastion/hub/HubCasingBlock.java`:

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
 * Hub Casing (PLAN "Turret Control Hub"): the plain block of a Control Hub's 3x3. X and Z hold its place in a formed hub
 * (FeedHubBlock's parts; ALONE = in no hub), set by the hub's Interface; placing or breaking one makes the Interfaces
 * around check their layout again.
 */
public class HubCasingBlock extends Block {
    public static final EnumProperty<Part> X = FeedHubBlock.X, Z = FeedHubBlock.Z;

    public HubCasingBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(X, Part.ALONE).setValue(Z, Part.ALONE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(X, Z);
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

Create `src/main/java/dev/bastion/hub/HubPowerPortBlock.java` (Task 2 gives it its block entity):

```java
package dev.bastion.hub;

/** Hub Power Port (PLAN "Turret Control Hub"): a Hub Casing that also takes FE from cables for its hub. */
public class HubPowerPortBlock extends HubCasingBlock {
    public HubPowerPortBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected String tooltipKey() {
        return "hub_power_port";
    }
}
```

- [ ] **Step 6: The Interface.** Create `src/main/java/dev/bastion/hub/HubInterfaceBlock.java`:

```java
package dev.bastion.hub;

import dev.bastion.registry.BastionBlockEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * Hub Interface (PLAN "Turret Control Hub"): the hub's controller, in the middle of one side of the 3x3, facing out (at
 * the player who placed it). It checks the layout a tick after it is placed or a part near it changes (and every second),
 * holds the hub's FE and opens its screen. STATUS mirrors the hub for its model and for clients: UNFORMED, ONLINE (formed,
 * upkeep paid), OFFLINE (formed, out of FE).
 */
public class HubInterfaceBlock extends BaseEntityBlock {
    public enum Status implements StringRepresentable {
        UNFORMED, ONLINE, OFFLINE;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<Status> STATUS = EnumProperty.create("status", Status.class);

    public HubInterfaceBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(STATUS, Status.UNFORMED));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, STATUS);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HubInterfaceBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, BastionBlockEntities.HUB_INTERFACE.get(), HubInterfaceBlockEntity::serverTick);
    }

    /** Checks a tick after placing: the block entity does not exist yet inside onPlace. */
    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moving) {
        super.onPlace(state, level, pos, oldState, moving);
        if (!oldState.is(this)) level.scheduleTick(pos, this, 1);
    }

    /** It was just placed, or a part near it changed (ControlHubStructure.notifyAround). */
    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof HubInterfaceBlockEntity hub) hub.recheck();
    }

    /** Holding a block places it (the next part). Otherwise an unfinished hub says what is missing. */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.getItemInHand(hand).getItem() instanceof BlockItem) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof HubInterfaceBlockEntity hub) {
            if (!hub.formed()) hub.recheck(); // the last part may have gone in this very tick
            ControlHubStructure.Result result = ControlHubStructure.check(level, pos, state.getValue(FACING));
            if (!hub.formed() && !result.formed()) player.displayClientMessage(ControlHubStructure.message(result), true);
        }
        return InteractionResult.CONSUME;
    }

    /** Broken: its parts and satellite stop being a hub; its FE is lost with it. */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide && state.getValue(STATUS) != Status.UNFORMED) {
            ControlHubStructure.unform(level, pos, state.getValue(FACING));
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    /** Spectators open menus through vanilla's provider path, which sends no open data: keep them out (as TurretBaseBlock). */
    @Override
    @Nullable
    public MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return null;
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.bastion.hub_interface").withStyle(ChatFormatting.GRAY));
    }
}
```

Create `src/main/java/dev/bastion/hub/HubInterfaceBlockEntity.java`:

```java
package dev.bastion.hub;

import dev.bastion.config.BastionConfig;
import dev.bastion.hub.HubInterfaceBlock.Status;
import dev.bastion.registry.BastionBlockEntities;
import dev.bastion.registry.BastionSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Control Hub's state (PLAN "Turret Control Hub"): its FE, the satellite's tier, and the status kept in its block
 * state. Formed, it pays its tier's upkeep from its FE every tick and goes OFFLINE without it.
 */
public class HubInterfaceBlockEntity extends BlockEntity {
    /** Ticks between safety re-checks: a part in a chunk that loaded later, a change nothing reported. */
    private static final int RECHECK_INTERVAL = 20;
    private int energy;
    /** The satellite's tier (1-3) when it last formed. */
    private int tier = 1;
    private int recheckTimer;

    public HubInterfaceBlockEntity(BlockPos pos, BlockState state) {
        super(BastionBlockEntities.HUB_INTERFACE.get(), pos, state);
    }

    public Direction facing() {
        return getBlockState().getValue(HubInterfaceBlock.FACING);
    }

    public Status status() {
        return getBlockState().getValue(HubInterfaceBlock.STATUS);
    }

    public boolean formed() {
        return status() != Status.UNFORMED;
    }

    public boolean online() {
        return status() == Status.ONLINE;
    }

    public int tier() {
        return tier;
    }

    /** The middle block of the layer: the satellite stands on it and the reach is counted from its chunk. */
    public BlockPos center() {
        return ControlHubStructure.center(worldPosition, facing());
    }

    /** Side of the reach in chunks (1, 3, 7 by default). */
    public int side() {
        return BastionConfig.controlHubRange(tier);
    }

    public int energy() {
        return energy;
    }

    public int capacity() {
        return BastionConfig.CONTROL_HUB_ENERGY_CAPACITY.get();
    }

    /** FE per tick while formed. */
    public int upkeep() {
        return BastionConfig.controlHubUpkeep(tier);
    }

    /** Sets the stored FE (tests and dev scenes; Power Ports go through {@code receive}). */
    public void setEnergy(int amount) {
        energy = Mth.clamp(amount, 0, capacity());
        setChanged();
    }

    /** Checks the layout and forms the hub or breaks it up (a part nearby changed, every second, right after loading). */
    public void recheck() {
        if (!(level instanceof ServerLevel server)) return;
        ControlHubStructure.Result result = ControlHubStructure.check(server, worldPosition, facing());
        if (result.formed()) {
            tier = result.tier();
            ControlHubStructure.form(server, worldPosition, facing());
            setStatus(energy >= upkeep() ? Status.ONLINE : Status.OFFLINE);
            ControlHubStructure.setSatellite(server, center(), status()); // a satellite swapped in keeps up
        } else if (formed()) {
            ControlHubStructure.unform(server, worldPosition, facing());
            setStatus(Status.UNFORMED);
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, HubInterfaceBlockEntity hub) {
        if (--hub.recheckTimer <= 0) {
            hub.recheckTimer = RECHECK_INTERVAL;
            hub.recheck();
        }
        hub.payUpkeep();
    }

    private void payUpkeep() {
        if (!formed()) return;
        int cost = upkeep();
        if (energy < cost) {
            setStatus(Status.OFFLINE);
            return;
        }
        if (cost > 0) {
            energy -= cost;
            setChanged();
        }
        setStatus(Status.ONLINE);
    }

    /** Into the block state (model, clients) and onto the satellite; ONLINE and OFFLINE get their sounds. */
    private void setStatus(Status next) {
        BlockState state = getBlockState();
        if (level == null || state.getValue(HubInterfaceBlock.STATUS) == next) return;
        level.setBlock(worldPosition, state.setValue(HubInterfaceBlock.STATUS, next), Block.UPDATE_CLIENTS);
        ControlHubStructure.setSatellite(level, center(), next);
        if (next == Status.ONLINE) level.playSound(null, worldPosition, BastionSounds.CONTROL_HUB_ONLINE.get(), SoundSource.BLOCKS, 1f, 1f);
        else if (next == Status.OFFLINE) level.playSound(null, worldPosition, BastionSounds.CONTROL_HUB_OFFLINE.get(), SoundSource.BLOCKS, 1f, 1f);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (energy > 0) tag.putInt("Energy", energy);
        tag.putInt("Tier", tier);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        energy = tag.getInt("Energy");
        tier = Mth.clamp(tag.getInt("Tier"), 1, 3);
    }
}
```

- [ ] **Step 7: The satellite.** Create `src/main/java/dev/bastion/hub/SatelliteUplinkBlock.java`:

```java
package dev.bastion.hub;

import dev.bastion.config.BastionConfig;
import dev.bastion.hub.HubInterfaceBlock.Status;
import dev.bastion.registry.BastionBlockEntities;
import dev.bastion.turret.Footprint;
import dev.bastion.turret.Multiblock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Satellite Uplink T1-T3 (PLAN "Turret Control Hub"): a mast with a turning dish, two blocks tall, standing on the middle
 * block of a Control Hub; its tier sets the hub's reach. Placed like a workstation (Multiblock: the placement hologram
 * shows both blocks); the bottom block (PART 0) holds the block entity and draws the model, breaking either half breaks
 * both and drops the item once. STATUS is set by the hub's Interface (dish folded, turning or stopped).
 */
public class SatelliteUplinkBlock extends BaseEntityBlock implements Multiblock {
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 1);
    public static final EnumProperty<Status> STATUS = HubInterfaceBlock.STATUS;
    private static final VoxelShape MAST = Block.box(5, 0, 5, 11, 16, 11), HEAD = Block.box(3, 0, 3, 13, 14, 13);
    private final int tier;

    public SatelliteUplinkBlock(int tier, Properties properties) {
        super(properties);
        this.tier = tier;
        registerDefaultState(stateDefinition.any().setValue(PART, 0).setValue(STATUS, Status.UNFORMED));
    }

    public int tier() {
        return tier;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART, STATUS);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(PART) == 0 ? MAST : HEAD;
    }

    /** The clicked block and the one above it; never into a mob. Front UP: the hologram does not turn it. */
    @Override
    public Footprint footprint(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos(), top = pos.above();
        Set<BlockPos> blocked = new HashSet<>();
        if (!context.canPlace()) blocked.add(pos);
        if (top.getY() >= level.getMaxBuildHeight() || !level.getBlockState(top).canBeReplaced(context)
                || !level.isUnobstructed(defaultBlockState(), top, CollisionContext.empty())) blocked.add(top);
        return new Footprint(List.of(pos, top), blocked, Direction.UP);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return footprint(context).placeable() ? defaultBlockState() : null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide) level.setBlock(pos.above(), state.setValue(PART, 1), Block.UPDATE_ALL);
    }

    /** A whole satellite with its bottom at {@code bottom} (tests, dev scenes). */
    public void placeAt(Level level, BlockPos bottom) {
        level.setBlock(bottom, defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(bottom.above(), defaultBlockState().setValue(PART, 1), Block.UPDATE_ALL);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moving) {
        super.onPlace(state, level, pos, oldState, moving);
        if (!oldState.is(this) && state.getValue(PART) == 0) ControlHubStructure.notifyAround(level, pos);
    }

    /** One half gone takes the other (only the bottom drops the item, see its loot table) and tells the hub. */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!newState.is(this) && !level.isClientSide) {
            boolean bottom = state.getValue(PART) == 0;
            BlockPos other = bottom ? pos.above() : pos.below();
            BlockState otherState = level.getBlockState(other);
            if (otherState.is(this) && otherState.getValue(PART) != state.getValue(PART)) {
                if (bottom) level.setBlock(other, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                else level.destroyBlock(other, true);
            }
            ControlHubStructure.notifyAround(level, bottom ? pos : pos.below());
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    /** Creative breaking of the top half: the bottom goes without drops too. */
    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        super.playerWillDestroy(level, pos, state, player);
        if (!level.isClientSide && player.isCreative() && state.getValue(PART) == 1 && level.getBlockState(pos.below()).is(this)) {
            level.setBlock(pos.below(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
        }
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == 0 ? new SatelliteUplinkBlockEntity(pos, state) : null;
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide && state.getValue(PART) == 0
                ? createTickerHelper(type, BastionBlockEntities.SATELLITE_UPLINK.get(), SatelliteUplinkBlockEntity::clientTick) : null;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return state.getValue(PART) == 0 ? RenderShape.ENTITYBLOCK_ANIMATED : RenderShape.INVISIBLE;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.bastion.satellite_uplink", BastionConfig.controlHubRange(tier)).withStyle(ChatFormatting.GRAY));
    }
}
```

Create `src/main/java/dev/bastion/hub/SatelliteUplinkBlockEntity.java` (Task 3 adds the hum):

```java
package dev.bastion.hub;

import dev.bastion.hub.HubInterfaceBlock.Status;
import dev.bastion.registry.BastionBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Bottom block of a Satellite Uplink: GeckoLib animatable for its dish. Folded while the hub is not formed, unfolding once
 * when it is seen forming online (not when the chunk loads with it already up), turning while online, stopped offline.
 */
public class SatelliteUplinkBlockEntity extends BlockEntity implements GeoBlockEntity {
    private static final RawAnimation FOLDED = RawAnimation.begin().thenLoop("animation.satellite_uplink.folded");
    private static final RawAnimation DEPLOY = RawAnimation.begin().thenPlay("animation.satellite_uplink.deploy")
            .thenLoop("animation.satellite_uplink.spin");
    private static final RawAnimation SPIN = RawAnimation.begin().thenLoop("animation.satellite_uplink.spin");
    private static final RawAnimation OFFLINE = RawAnimation.begin().thenLoop("animation.satellite_uplink.offline");
    private final AnimatableInstanceCache animations = GeckoLibUtil.createInstanceCache(this);
    /** Client: the status one tick ago (null before the first tick). */
    @Nullable
    private Status seen;
    /** Client: online since it was seen forming, so the deploy clip plays before the spin. */
    private boolean deploying;

    public SatelliteUplinkBlockEntity(BlockPos pos, BlockState state) {
        super(BastionBlockEntities.SATELLITE_UPLINK.get(), pos, state);
    }

    public Status status() {
        return getBlockState().getValue(SatelliteUplinkBlock.STATUS);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, SatelliteUplinkBlockEntity satellite) {
        Status now = state.getValue(SatelliteUplinkBlock.STATUS);
        if (now != Status.ONLINE) satellite.deploying = false;
        else if (satellite.seen == Status.UNFORMED) satellite.deploying = true;
        satellite.seen = now;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "dish", 5, state -> state.setAndContinue(switch (status()) {
            case UNFORMED -> FOLDED;
            case OFFLINE -> OFFLINE;
            case ONLINE -> deploying ? DEPLOY : SPIN;
        })));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animations;
    }

    /** The dish reaches past the two blocks. */
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(1.5).expandTowards(0, 2, 0);
    }
}
```

- [ ] **Step 8: Registrations.** `BastionBlocks` (imports `dev.bastion.hub.*`): after `FEED_HUB`:

```java
    // Turret Control Hub (PLAN "Turret Control Hub"): parts placed one by one into a 3x3 with a satellite on the middle; pistons cannot move them.
    public static final RegistryObject<HubCasingBlock> HUB_CASING = REGISTER.register("hub_casing", () -> new HubCasingBlock(hubPart()));
    public static final RegistryObject<HubPowerPortBlock> HUB_POWER_PORT = REGISTER.register("hub_power_port", () -> new HubPowerPortBlock(hubPart()));
    public static final RegistryObject<HubInterfaceBlock> HUB_INTERFACE = REGISTER.register("hub_interface",
            () -> new HubInterfaceBlock(hubPart().noOcclusion()));
    public static final RegistryObject<SatelliteUplinkBlock> SATELLITE_UPLINK_T1 = satellite(1);
    public static final RegistryObject<SatelliteUplinkBlock> SATELLITE_UPLINK_T2 = satellite(2);
    public static final RegistryObject<SatelliteUplinkBlock> SATELLITE_UPLINK_T3 = satellite(3);
```

and at the end of the class:

```java
    private static BlockBehaviour.Properties hubPart() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5f, 6f).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK);
    }

    private static RegistryObject<SatelliteUplinkBlock> satellite(int tier) {
        return REGISTER.register("satellite_uplink_t" + tier, () -> new SatelliteUplinkBlock(tier, hubPart().noOcclusion()));
    }
```

`BastionBlockEntities` (imports `dev.bastion.hub.HubInterfaceBlockEntity`, `SatelliteUplinkBlockEntity`), after `FEED_HUB`:

```java
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<HubInterfaceBlockEntity>> HUB_INTERFACE = REGISTER.register("hub_interface",
            () -> BlockEntityType.Builder.of(HubInterfaceBlockEntity::new, BastionBlocks.HUB_INTERFACE.get()).build(null));
    /** The bottom block of every Satellite Uplink, all three tiers: it draws the mast and dish. */
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<SatelliteUplinkBlockEntity>> SATELLITE_UPLINK = REGISTER.register("satellite_uplink",
            () -> BlockEntityType.Builder.of(SatelliteUplinkBlockEntity::new, BastionBlocks.SATELLITE_UPLINK_T1.get(),
                    BastionBlocks.SATELLITE_UPLINK_T2.get(), BastionBlocks.SATELLITE_UPLINK_T3.get()).build(null));
```

`BastionItems`, after `FEED_HUB` (Task 3 turns the satellites into `SatelliteUplinkItem`):

```java
    // Turret Control Hub parts (PLAN "Turret Control Hub"); their tooltips come from the blocks.
    public static final RegistryObject<BlockItem> HUB_INTERFACE = REGISTER.register("hub_interface",
            () -> new BlockItem(BastionBlocks.HUB_INTERFACE.get(), new Item.Properties()));
    public static final RegistryObject<BlockItem> HUB_CASING = REGISTER.register("hub_casing",
            () -> new BlockItem(BastionBlocks.HUB_CASING.get(), new Item.Properties()));
    public static final RegistryObject<BlockItem> HUB_POWER_PORT = REGISTER.register("hub_power_port",
            () -> new BlockItem(BastionBlocks.HUB_POWER_PORT.get(), new Item.Properties()));
    public static final RegistryObject<BlockItem> SATELLITE_UPLINK_T1 = REGISTER.register("satellite_uplink_t1",
            () -> new BlockItem(BastionBlocks.SATELLITE_UPLINK_T1.get(), new Item.Properties().stacksTo(16)));
    public static final RegistryObject<BlockItem> SATELLITE_UPLINK_T2 = REGISTER.register("satellite_uplink_t2",
            () -> new BlockItem(BastionBlocks.SATELLITE_UPLINK_T2.get(), new Item.Properties().stacksTo(16)));
    public static final RegistryObject<BlockItem> SATELLITE_UPLINK_T3 = REGISTER.register("satellite_uplink_t3",
            () -> new BlockItem(BastionBlocks.SATELLITE_UPLINK_T3.get(), new Item.Properties().stacksTo(16)));
```

(They land in the main "Bastion" tab by the existing predicate.)

- [ ] **Step 9: Sounds.** `BastionSounds`, after the Plasma block:

```java
    // Turret Control Hub (PLAN "Turret Control Hub"); placeholders until docs/SOUND_SHEET.md is done.
    public static final RegistryObject<SoundEvent> CONTROL_HUB_ONLINE = register("control_hub.online");
    public static final RegistryObject<SoundEvent> CONTROL_HUB_OFFLINE = register("control_hub.offline");
    public static final RegistryObject<SoundEvent> CONTROL_HUB_DISH_LOOP = register("control_hub.dish_loop");
    public static final RegistryObject<SoundEvent> CONTROL_HUB_UI_SELECT = register("control_hub.ui_select");
```

`assets/bastion/sounds.json`, four entries:

```json
  "control_hub.online": {"subtitle": "subtitles.bastion.control_hub.online", "sounds": [{"name": "bastion:control_hub/online", "attenuation_distance": 24}]},
  "control_hub.offline": {"subtitle": "subtitles.bastion.control_hub.offline", "sounds": [{"name": "bastion:control_hub/offline", "attenuation_distance": 24}]},
  "control_hub.dish_loop": {"subtitle": "subtitles.bastion.control_hub.dish_loop", "sounds": [{"name": "bastion:control_hub/dish_loop", "attenuation_distance": 12}]},
  "control_hub.ui_select": {"sounds": ["bastion:control_hub/ui_select"]}
```

`tools/gen_sounds.py`: add after `plasma_cannon_hit`:

```python
def hub_online():
    """Control Hub online: a short servo whirr under two rising chime tones."""
    L = 1.2
    whirr = bp(noise(L, 401), 300, 1400) * env(L, 0.05, 0.35) * 0.35
    c1 = np.sin(2 * np.pi * 784 * t(L)) * env(L, 0.005, 0.18)
    c2 = pad(np.concatenate([np.zeros(n(0.18)), np.sin(2 * np.pi * 1175 * t(L - 0.18)) * env(L - 0.18, 0.005, 0.3)]), L)
    return fade_out(norm(whirr + 0.5 * c1 + 0.6 * c2))


def hub_offline():
    """Control Hub offline: a falling power-down tone over a soft relay click."""
    L = 1.0
    tone = sweep(L, 900, 180) * env(L, 0.005, 0.35)
    click = onepole_lp(noise(L, 402), 2500) * env(L, 0.001, 0.01)
    return fade_out(norm(tone + 0.5 * click))


def hub_dish_loop():
    """Satellite dish while online: a low 120 Hz hum and a slow servo whirr, loopable."""
    L = 3.0
    hum = np.sin(2 * np.pi * 120 * t(L)) * 0.5 + np.sin(2 * np.pi * 240 * t(L)) * 0.2
    whirr = bp(noise(L, 403), 500, 1800) * (0.5 + 0.5 * np.sin(2 * np.pi * (1 / L) * t(L))) * 0.25
    return norm(loopable(hum + whirr), 0.6)


def hub_ui_select():
    """Hub screen pick: a tiny beep."""
    L = 0.12
    return fade_out(norm(np.sin(2 * np.pi * 1320 * t(L)) * env(L, 0.002, 0.03), 0.7))
```

and in `SOUNDS`:

```python
    "control_hub/online.ogg": hub_online, "control_hub/offline.ogg": hub_offline,
    "control_hub/dish_loop.ogg": hub_dish_loop, "control_hub/ui_select.ogg": hub_ui_select,
```

Run `python3 tools/gen_sounds.py` (no `--force`). Expected: "wrote …/control_hub/online.ogg" and the other three, nothing else.

- [ ] **Step 10: Data and strings.** Loot tables, `data/bastion/loot_tables/blocks/hub_interface.json` (same shape for `hub_casing`, `hub_power_port`, each naming its own item):

```json
{
  "type": "minecraft:block",
  "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "bastion:hub_interface"}],
             "conditions": [{"condition": "minecraft:survives_explosion"}]}]
}
```

`satellite_uplink_t1.json` (and `_t2`, `_t3` with their block and item):

```json
{
  "type": "minecraft:block",
  "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "bastion:satellite_uplink_t1"}],
             "conditions": [{"condition": "minecraft:survives_explosion"},
                            {"condition": "minecraft:block_state_property", "block": "bastion:satellite_uplink_t1", "properties": {"part": "0"}}]}]
}
```

`data/minecraft/tags/blocks/mineable/pickaxe.json`: add `bastion:hub_interface`, `bastion:hub_casing`, `bastion:hub_power_port`, `bastion:satellite_uplink_t1`, `_t2`, `_t3`.

`lang/en_us.json`:

```json
  "block.bastion.hub_interface": "Hub Interface",
  "block.bastion.hub_casing": "Hub Casing",
  "block.bastion.hub_power_port": "Hub Power Port",
  "block.bastion.satellite_uplink_t1": "Satellite Uplink T1",
  "block.bastion.satellite_uplink_t2": "Satellite Uplink T2",
  "block.bastion.satellite_uplink_t3": "Satellite Uplink T3",
  "tooltip.bastion.hub_interface": "Turret Control Hub controller. Put it in the middle of one side of a 3x3 of Hub Casings (at least one a Hub Power Port), facing out, with a Satellite Uplink on the middle block. Right-click with an empty hand: what is missing, or the hub's screen",
  "tooltip.bastion.hub_casing": "Part of a Turret Control Hub's 3x3",
  "tooltip.bastion.hub_power_port": "Part of a Turret Control Hub's 3x3 that takes FE from cables (up to 2,000 FE/t) for the hub",
  "tooltip.bastion.satellite_uplink": "Stands on the middle block of a Turret Control Hub. Reach: %1$sx%1$s chunks around it",
  "message.bastion.control_hub.incomplete": "Turret Control Hub incomplete (%s): %s",
  "message.bastion.control_hub.need_satellite": "needs a Satellite Uplink on top of the middle block",
  "message.bastion.control_hub.need_block": "needs a Hub Casing or Hub Power Port",
  "message.bastion.control_hub.wrong": "a block that is not a hub part is in the way",
  "message.bastion.control_hub.other_hub": "a block belongs to another hub",
  "message.bastion.control_hub.need_port": "needs at least one Hub Power Port: swap a Hub Casing for one",
  "subtitles.bastion.control_hub.online": "Control Hub comes online",
  "subtitles.bastion.control_hub.offline": "Control Hub goes offline",
  "subtitles.bastion.control_hub.dish_loop": "Satellite dish hums",
```

`lang/id_id.json`:

```json
  "block.bastion.hub_interface": "Antarmuka Hub",
  "block.bastion.hub_casing": "Rangka Hub",
  "block.bastion.hub_power_port": "Port Daya Hub",
  "block.bastion.satellite_uplink_t1": "Uplink Satelit T1",
  "block.bastion.satellite_uplink_t2": "Uplink Satelit T2",
  "block.bastion.satellite_uplink_t3": "Uplink Satelit T3",
  "tooltip.bastion.hub_interface": "Pengendali Turret Control Hub. Taruh di tengah salah satu sisi 3x3 Rangka Hub (minimal satu Port Daya Hub), menghadap keluar, dengan Uplink Satelit di atas blok tengah. Klik kanan dengan tangan kosong: apa yang kurang, atau layar hub",
  "tooltip.bastion.hub_casing": "Bagian 3x3 Turret Control Hub",
  "tooltip.bastion.hub_power_port": "Bagian 3x3 Turret Control Hub yang menerima FE dari kabel (sampai 2.000 FE/t) untuk hub",
  "tooltip.bastion.satellite_uplink": "Berdiri di atas blok tengah Turret Control Hub. Jangkauan: %1$sx%1$s chunk di sekitarnya",
  "message.bastion.control_hub.incomplete": "Turret Control Hub belum lengkap (%s): %s",
  "message.bastion.control_hub.need_satellite": "butuh Uplink Satelit di atas blok tengah",
  "message.bastion.control_hub.need_block": "butuh Rangka Hub atau Port Daya Hub",
  "message.bastion.control_hub.wrong": "ada blok yang bukan bagian hub",
  "message.bastion.control_hub.other_hub": "ada blok milik hub lain",
  "message.bastion.control_hub.need_port": "butuh minimal satu Port Daya Hub: ganti satu Rangka Hub",
  "subtitles.bastion.control_hub.online": "Control Hub menyala",
  "subtitles.bastion.control_hub.offline": "Control Hub mati",
  "subtitles.bastion.control_hub.dish_loop": "Dish satelit berdengung",
```

- [ ] **Step 11: Run to verify it passes.** `./gradlew build --offline`, then `./gradlew runGameTestServer --offline`. Expected: "All 135 required tests passed" (128 + 7).

---

### Task 2: Power Port FE, upkeep and OFFLINE

**Files:**
- Create: `src/main/java/dev/bastion/hub/HubPowerPortBlockEntity.java`
- Modify: `src/main/java/dev/bastion/hub/HubPowerPortBlock.java`, `HubInterfaceBlockEntity.java`, `registry/BastionBlockEntities.java`, `gametest/ControlHubTests.java`

**Interfaces:**
- Consumes: `ControlHubStructure.hubAt`, `partCenter`; `HubInterfaceBlockEntity.capacity()`, `energy()`.
- Produces: `int HubInterfaceBlockEntity.receive(int amount, boolean simulate)`; `BastionBlockEntities.HUB_POWER_PORT`; the port's `ForgeCapabilities.ENERGY` (receive-only, any side).

- [ ] **Step 1: Failing tests.** Add to `ControlHubTests` (imports `net.minecraftforge.common.capabilities.ForgeCapabilities`, `net.minecraftforge.energy.IEnergyStorage`):

```java
    private static IEnergyStorage energy(ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos).getCapability(ForgeCapabilities.ENERGY, Direction.UP).orElseThrow(IllegalStateException::new);
    }

    /** A formed hub pays its tier's upkeep every tick: 20, 60, 150 FE (config defaults). */
    @GameTest(template = YARD, timeoutTicks = 80, batch = "hubPaysUpkeepPerTier")
    public static void hubPaysUpkeepPerTier(GameTestHelper helper) {
        HubInterfaceBlockEntity[] hubs = {hub(helper, new BlockPos(5, 2, 3), 1), hub(helper, new BlockPos(12, 2, 3), 2), hub(helper, new BlockPos(19, 2, 3), 3)};
        for (HubInterfaceBlockEntity hub : hubs) hub.setEnergy(100_000);
        int[] start = new int[3];
        helper.runAtTickTime(10, () -> {
            for (int i = 0; i < 3; i++) start[i] = hubs[i].energy();
        });
        helper.runAtTickTime(30, () -> {
            int[] cost = {20, 60, 150};
            for (int i = 0; i < 3; i++) {
                int used = start[i] - hubs[i].energy();
                helper.assertTrue(hubs[i].online() && used == 20 * cost[i], "T" + (i + 1) + " used " + used + " FE in 20 ticks, expected " + 20 * cost[i]);
            }
            helper.succeed();
        });
    }

    /** Ten ticks of FE: online, then OFFLINE (satellite too); FE through the Power Port brings it back. */
    @GameTest(template = ARENA, timeoutTicks = 80, batch = "hubGoesOfflineWithoutFe")
    public static void hubGoesOfflineWithoutFe(GameTestHelper helper) {
        HubInterfaceBlockEntity hub = hub(helper, IFACE, 1);
        hub.setEnergy(200);
        IEnergyStorage port = energy(helper.getLevel(), ControlHubStructure.slots(hub.getBlockPos(), Direction.NORTH).get(0));
        helper.runAtTickTime(4, () -> helper.assertTrue(hub.online(), "not online with FE"));
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(hub.status() == Status.OFFLINE && hub.energy() < 20, "still " + hub.status() + " with " + hub.energy() + " FE");
            helper.assertTrue(port.receiveEnergy(2000, false) == 2000, "the port refused FE");
        });
        helper.runAtTickTime(32, () -> {
            helper.assertTrue(hub.online(), "not back online once FE came in");
            helper.assertTrue(helper.getLevel().getBlockState(hub.center().above()).getValue(SatelliteUplinkBlock.STATUS) == Status.ONLINE,
                    "the satellite did not follow");
            helper.succeed();
        });
    }

    /** A port in no hub takes nothing; a formed hub's port takes 2,000 FE per tick at most, and never past the capacity. */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "powerPortTakesFeOnlyWhenFormed")
    public static void powerPortTakesFeOnlyWhenFormed(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos lone = helper.absolutePos(new BlockPos(1, 2, 1));
        level.setBlock(lone, BastionBlocks.HUB_POWER_PORT.get().defaultBlockState(), 3);
        IEnergyStorage alone = energy(level, lone);
        HubInterfaceBlockEntity hub = hub(helper, IFACE, 1);
        IEnergyStorage port = energy(level, ControlHubStructure.slots(hub.getBlockPos(), Direction.NORTH).get(0));
        helper.assertTrue(alone.receiveEnergy(1000, false) == 0, "a port outside a hub took FE");
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(hub.formed(), "did not form");
            int first = port.receiveEnergy(5000, false), second = port.receiveEnergy(5000, false);
            helper.assertTrue(first == 2000 && second == 0, "one tick took " + first + " then " + second + " (expected 2000, 0)");
        });
        helper.runAtTickTime(6, () -> {
            hub.setEnergy(hub.capacity() - 100);
            helper.assertTrue(port.receiveEnergy(5000, false) == 100, "took past the capacity");
            helper.succeed();
        });
    }
```

- [ ] **Step 2: Run to verify it fails.** `./gradlew runGameTestServer --offline`. Expected: the three new tests fail (`IllegalStateException`: no ENERGY capability on the port).

- [ ] **Step 3: Implement.** In `HubInterfaceBlockEntity` add:

```java
    /** FE from a Power Port: as much as fits; returns what was taken. */
    public int receive(int amount, boolean simulate) {
        int accepted = Math.max(0, Math.min(amount, capacity() - energy));
        if (!simulate && accepted > 0) {
            energy += accepted;
            setChanged();
        }
        return accepted;
    }
```

Create `src/main/java/dev/bastion/hub/HubPowerPortBlockEntity.java`:

```java
package dev.bastion.hub;

import dev.bastion.config.BastionConfig;
import dev.bastion.registry.BastionBlockEntities;
import dev.bastion.turret.FeedHubBlock.Part;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

/**
 * Hub Power Port: receive-only FE on every side, passed straight into its formed hub's Interface, at most
 * controlHubPortMaxInput per tick per port. A port in no hub takes nothing; the hub never gives FE back.
 */
public class HubPowerPortBlockEntity extends BlockEntity {
    private long tick = -1;
    private int received;
    private final IEnergyStorage storage = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            HubInterfaceBlockEntity hub = hub();
            if (hub == null || level == null) return 0;
            long now = level.getGameTime();
            if (now != tick) {
                tick = now;
                received = 0;
            }
            int taken = hub.receive(Math.min(maxReceive, BastionConfig.CONTROL_HUB_PORT_MAX_INPUT.get() - received), simulate);
            if (!simulate) received += taken;
            return taken;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            HubInterfaceBlockEntity hub = hub();
            return hub == null ? 0 : hub.energy();
        }

        @Override
        public int getMaxEnergyStored() {
            HubInterfaceBlockEntity hub = hub();
            return hub == null ? 0 : hub.capacity();
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return hub() != null;
        }
    };
    private LazyOptional<IEnergyStorage> capability = LazyOptional.of(() -> storage);

    public HubPowerPortBlockEntity(BlockPos pos, BlockState state) {
        super(BastionBlockEntities.HUB_POWER_PORT.get(), pos, state);
    }

    /** The formed hub this port is part of, or null. */
    @Nullable
    private HubInterfaceBlockEntity hub() {
        BlockState state = getBlockState();
        if (level == null || state.getValue(HubCasingBlock.X) == Part.ALONE) return null;
        return ControlHubStructure.hubAt(level, ControlHubStructure.partCenter(worldPosition, state));
    }

    @Override
    public <C> LazyOptional<C> getCapability(Capability<C> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ENERGY) return capability.cast();
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        capability.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        capability = LazyOptional.of(() -> storage);
    }
}
```

`HubPowerPortBlock` becomes `public class HubPowerPortBlock extends HubCasingBlock implements EntityBlock` with:

```java
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HubPowerPortBlockEntity(pos, state);
    }
```

(imports `net.minecraft.core.BlockPos`, `net.minecraft.world.level.block.EntityBlock`, `net.minecraft.world.level.block.entity.BlockEntity`, `net.minecraft.world.level.block.state.BlockState`). `BastionBlockEntities`, after `HUB_INTERFACE`:

```java
    /** A Hub Power Port: cables on any side feed its hub's Interface. */
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<HubPowerPortBlockEntity>> HUB_POWER_PORT = REGISTER.register("hub_power_port",
            () -> BlockEntityType.Builder.of(HubPowerPortBlockEntity::new, BastionBlocks.HUB_POWER_PORT.get()).build(null));
```

- [ ] **Step 4: Run to verify it passes.** `./gradlew build --offline` then `./gradlew runGameTestServer --offline`. Expected: "All 138 required tests passed".

---

### Task 3: Stage-1 assets, satellite rendering, blueprint hologram, recipes, scene

**Files:**
- Create: `tools/gen_control_hub_models.py`, `tools/blockout/design_control_hub.py`, `src/main/java/dev/bastion/hub/SatelliteUplinkItem.java`, `src/main/java/dev/bastion/client/render/SatelliteUplinkModel.java`, `SatelliteUplinkRenderer.java`, `SatelliteUplinkItemRenderer.java`, `HubBlueprint.java`, `src/main/java/dev/bastion/client/ControlHubEffects.java`, `src/dev/java/dev/bastion/dev/ControlHubShowcase.java`; generated `assets/bastion/{blockstates,models/block,models/item}/…`, `geo/satellite_uplink_t{1,2,3}.geo.json`, `animations/satellite_uplink_t{1,2,3}.animation.json`, `textures/block/…`, recipes
- Modify: `tools/gen_textures.py`, `tools/gen_recipes.py`, `tools/fit_item_display.py`, `src/main/java/dev/bastion/client/render/PlacementPreview.java`, `client/BastionClient.java`, `hub/HubInterfaceBlock.java`, `hub/HubInterfaceBlockEntity.java`, `hub/SatelliteUplinkBlockEntity.java`, `registry/BastionItems.java`, `src/dev/java/dev/bastion/dev/Showcase.java`, `gametest/ControlHubTests.java`, `docs/SOUND_SHEET.md`

**Interfaces:**
- Consumes: Task 1 blocks and states, `ControlHubStructure.check/center`, `BastionSounds.CONTROL_HUB_DISH_LOOP`.
- Produces: `PlacementPreview.box(PoseStack, MultiBufferSource, BlockPos, int rgb)` and `box(PoseStack, MultiBufferSource, AABB, int rgb, float fill, float edge)` (package-private); `HubBlueprint.show(BlockPos iface, Direction facing)`, `HubBlueprint.scan(BlockPos iface, Direction facing)`; `ControlHubEffects.formed(BlockPos, Direction)`, `ControlHubEffects.dishHum(SatelliteUplinkBlockEntity)`; `SatelliteUplinkItem`; `BastionItems.SATELLITE_UPLINK_T1..T3` become `RegistryObject<SatelliteUplinkItem>`; scene `-Pshowcase=hub` (`ControlHubShowcase`, with `static final BlockPos T3_IFACE` used by later tasks).

- [ ] **Step 1: Failing test (recipes).** Add to `ControlHubTests` (imports `net.minecraft.resources.ResourceLocation`):

```java
    /** Every hub part has its Module Workstation recipe, in the hub's own tab. */
    @GameTest(template = EMPTY, batch = "hubPartRecipesExist")
    public static void hubPartRecipesExist(GameTestHelper helper) {
        for (String item : List.of("hub_casing", "hub_power_port", "hub_interface", "satellite_uplink_t1", "satellite_uplink_t2", "satellite_uplink_t3")) {
            ResourceLocation id = Bastion.id("workstation/module_workstation/" + item);
            helper.assertTrue(helper.getLevel().getRecipeManager().byKey(id).isPresent(), "no recipe " + id);
        }
        helper.succeed();
    }
```

Run `./gradlew runGameTestServer --offline`. Expected: `hubPartRecipesExist` fails ("no recipe bastion:workstation/module_workstation/hub_casing").

- [ ] **Step 2: Recipes.** In `tools/gen_recipes.py`, after `TOOLS`:

```python
# Turret Control Hub (PLAN "Turret Control Hub"): its own tab at the Module Workstation. result: (ingredients, time, energy, count)
HUB = {"hub_casing": ({"iron_ingot": 4, "copper_ingot": 2, "redstone": 1}, 60, 1000, 4),
       "hub_power_port": ({B + "hub_casing": 1, "redstone_block": 1, "copper_ingot": 2}, 60, 1500, 1),
       "hub_interface": ({B + "hub_casing": 1, "diamond": 1, "glass_pane": 3, "redstone": 2, "comparator": 1}, 100, 3000, 1),
       "satellite_uplink_t1": ({"iron_ingot": 3, "copper_ingot": 2, "ender_pearl": 1, "redstone": 1}, 100, 3000, 1),
       "satellite_uplink_t2": ({B + "satellite_uplink_t1": 1, "diamond": 2, "gold_ingot": 2, "ender_eye": 1}, 140, 6000, 1),
       "satellite_uplink_t3": ({B + "satellite_uplink_t2": 1, "netherite_scrap": 1, "diamond": 2, "ender_eye": 2}, 180, 10000, 1)}
```

and in `main()`, after the `TOOLS` loop:

```python
    for part, (ingredients, time, energy, count) in HUB.items():
        write(out / "module_workstation" / f"{part}.json", recipe("module_workstation", B + "hub_interface", B + part, ingredients, time, energy, count))
    n += len(HUB)
```

Run `python3 tools/gen_recipes.py`. Expected: the count grows by 6, six new files under `data/bastion/recipes/workstation/module_workstation/`.

- [ ] **Step 3: Block textures and GUI-free assets.** In `tools/gen_textures.py`, after `feed_hub_edge_glow()`:

```python
# --- Turret Control Hub (PLAN "Turret Control Hub") -------------------------------------------------

def hub_casing():
    """Hub Casing: gunmetal plate, dark rim with a bevel, a recessed cross groove and four corner bolts."""
    img = np.zeros((16, 16, 4))
    img[..., :3], img[..., 3] = GUNMETAL * 1.05 * noise((16, 16), 0.04, seed=611)[..., None], 1
    img[0, :, :3] = img[15, :, :3] = img[:, 0, :3] = img[:, 15, :3] = DARKSTEEL
    img[1, 1:15, :3] = img[1:15, 1, :3] = GUNMETAL * 1.35
    img[14, 1:15, :3] = img[1:15, 14, :3] = GUNMETAL * 0.7
    rect(img, 7, 3, 2, 10, DARKSTEEL)
    rect(img, 3, 7, 10, 2, DARKSTEEL)
    for x, y in ((3, 3), (12, 3), (3, 12), (12, 12)):
        img[y, x, :3] = STEEL * 1.2
    return img


def hub_power_port():
    """Hub Power Port: the casing plate with a round socket (steel collar, dark bore) and a small lightning glyph."""
    img = hub_casing()
    yy, xx = np.mgrid[0:16, 0:16]
    d = np.hypot(xx - 7.5, yy - 7.5)
    img[(d >= 3.5) & (d < 5.5), :3] = STEEL * 0.9
    img[d < 3.5, :3] = BORE
    for x, y in ((8, 5), (7, 6), (6, 7), (7, 7), (8, 7), (9, 7), (8, 8), (7, 9), (6, 10)):
        img[y, x, :3] = STEEL * 1.3
    return img


def hub_power_port_glow():
    """Fullbright cyan ring round the Power Port socket (formed hub only)."""
    img = np.zeros((16, 16, 4))
    yy, xx = np.mgrid[0:16, 0:16]
    ring = (np.hypot(xx - 7.5, yy - 7.5) >= 4.6) & (np.hypot(xx - 7.5, yy - 7.5) < 5.6)
    img[ring, :3], img[ring, 3] = rgb("4FD8FF"), 1
    return img


def hub_interface_recess():
    """Back plate in the Interface's screen recess: dark steel with a faint grid."""
    img = np.zeros((16, 16, 4))
    img[..., :3], img[..., 3] = DARKSTEEL * noise((16, 16), 0.05, seed=612)[..., None], 1
    img[::4, :, :3] *= 1.25
    img[:, ::4, :3] *= 1.25
    return img


def hub_screen_off():
    """Interface screen, hub not formed: dark glass with a soft diagonal reflection."""
    img = np.zeros((16, 16, 4))
    yy, xx = np.mgrid[0:16, 0:16]
    shine = np.clip(1 - np.abs(xx - yy - 3) / 3, 0, 1) * 0.12
    img[..., :3], img[..., 3] = BORE + shine[..., None], 1
    return img


def hub_screen_radar():
    """Interface screen online: 8 frames (16x128) of a radar sweep, rings, a turning beam with a fading trail, two blips."""
    frames = []
    yy, xx = np.mgrid[0:16, 0:16]
    d = np.hypot(xx - 7.5, yy - 7.5)
    ang = (np.degrees(np.arctan2(yy - 7.5, xx - 7.5)) + 360) % 360
    for f in range(8):
        img = np.zeros((16, 16, 4))
        img[..., :3], img[..., 3] = rgb("061A1F"), 1
        sweep = f * 45
        trail = np.clip(1 - ((sweep - ang) % 360) / 120, 0, 1) * (d < 7.2)
        img[..., :3] += rgb("4FD8FF") * (0.75 * trail)[..., None]
        for r in (3.5, 6.5):
            ring = np.abs(d - r) < 0.5
            img[ring, :3] = np.maximum(img[ring, :3], rgb("1F6F80"))
        img[7:9, 7:9, :3] = rgb("BFF4FF")
        for bx, by, at in ((11, 5, 300), (5, 11, 135)):  # blips light up as the beam passes
            lit = np.clip(1 - ((sweep - at) % 360) / 180, 0, 1)
            img[by, bx, :3] = np.maximum(img[by, bx, :3], rgb("7CFF8A") * (0.3 + 0.7 * lit))
        frames.append(np.clip(img, 0, 1))
    return np.concatenate(frames, axis=0)


def hub_screen_alert():
    """Interface screen offline: 2 frames (16x32), a red power glyph blinking on dark glass."""
    glyph = [(7, 3), (8, 3), (7, 4), (8, 4), (7, 5), (8, 5), (7, 6), (8, 6), (4, 6), (11, 6), (3, 7), (12, 7), (3, 8), (12, 8),
             (3, 9), (12, 9), (4, 10), (11, 10), (5, 11), (10, 11), (6, 12), (7, 12), (8, 12), (9, 12)]
    frames = []
    for bright in (1.0, 0.35):
        img = hub_screen_off()
        for x, y in glyph:
            img[y, x, :3] = rgb("FF3A30") * bright
        frames.append(img)
    return np.concatenate(frames, axis=0)
```

In `MODEL_MATERIALS` add (the satellites; every bone with cubes is listed):

```python
    **{f"satellite_uplink_t{tier}": {"base": "hex", "mast": "steel", "collars": "frame", "servo": "armor", "glow_servo": "emitter",
                                     "head": "frame", "dish": "armor", "dish_rim": "frame", "feed": "steel", "glow_beacon": "emitter",
                                     **({"antenna": "steel"} if tier >= 2 else {}), **({"glow_beacons": "emitter"} if tier == 3 else {})}
       for tier in (1, 2, 3)},
```

In `TEXTURES` add:

```python
    "block/hub_casing.png": hub_casing, "block/hub_power_port.png": hub_power_port, "block/hub_power_port_glow.png": hub_power_port_glow,
    "block/hub_interface_recess.png": hub_interface_recess, "block/hub_screen_off.png": hub_screen_off,
    "block/hub_screen_radar.png": hub_screen_radar, "block/hub_screen_alert.png": hub_screen_alert,
    **{f"block/satellite_uplink_t{tier}{suffix}.png": (lambda tier=tier, i=i: paint_model(f"satellite_uplink_t{tier}")[i])
       for tier in (1, 2, 3) for i, suffix in ((0, ""), (1, "_e"))},
```

and the animated-texture metadata, next to `TEXTURES`:

```python
MCMETA = {"block/hub_screen_radar.png": {"animation": {"frametime": 3}}, "block/hub_screen_alert.png": {"animation": {"frametime": 10}}}
```

written at the end of `__main__`:

```python
    for rel, meta in MCMETA.items():
        (OUT / (rel + ".mcmeta")).write_text(json.dumps(meta) + "\n")
```

- [ ] **Step 4: Satellite models and clips.** Create `tools/blockout/design_control_hub.py`:

```python
"""Satellite Uplink models (PLAN "Turret Control Hub"), the turrets' kit and look: gunmetal panels, dark frames, one cyan
glow. Origin = the middle of the block's floor, front = north (-Z), units px, two blocks tall (mast in the lower block;
servo, head and dish in the upper). head turns (yaw) on the servo, dish tilts (pitch) on the head's yoke. Also writes the
clips, the same names for every tier: animations/satellite_uplink_t<N>.animation.json (folded, deploy, spin, offline).

    cd tools/blockout && python3 design_control_hub.py
"""
import json
from pathlib import Path

from kit import Kit, GEO

ANIMATIONS = Path(GEO).parent / "animations"
DISH = {1: 5.5, 2: 7.5, 3: 9.5}  # dish radius per tier, px


def satellite(tier):
    k = Kit(128)
    r = DISH[tier]
    k.bone("root")
    k.bone("base", "root", cubes=k.chamfer("foot", 6, 2, 0, 2) + [k.box("plinth", -4, 2, -4, 4, 4, 4)])
    k.bone("mast", "root", cubes=[k.box("pole", -1.5, 4, -1.5, 1.5, 21, 1.5)])
    k.bone("collars", "root", cubes=[k.box("collar", -2.5, y, -2.5, 2.5, y + 1.5, 2.5) for y in (8, 13.5, 19)])
    k.bone("servo", "root", cubes=[k.box("servo", -3.5, 21, -3.5, 3.5, 25, 3.5)])
    k.bone("glow_servo", "servo", cubes=[k.box("servo_ring", -3.7, 22.6, -3.7, 3.7, 23.4, 3.7)])
    k.bone("head", "root", pivot=(0, 25, 0), cubes=[
        k.box("turntable", -3, 25, -3, 3, 26.5, 3),
        k.box("yoke", -4.5, 26.5, -1, -3.5, 30.5, 1), k.box("yoke", 3.5, 26.5, -1, 4.5, 30.5, 1)])
    # Built facing up (+Y), tilted toward the front by the bone's rest rotation; the clips turn it from there.
    k.bone("dish", "head", pivot=(0, 29.5, 0), rotation=[-50, 0, 0],
           cubes=k.octagon("dish_back", r * 0.55, 28.6, 29.6) + k.octagon("dish_face", r * 0.95, 29.6, 30.2))
    k.bone("dish_rim", "dish", cubes=k.ring8("rim", r, 29.6, 31.2, 0.8))
    tip = 30.2 + r * 0.6
    k.bone("feed", "dish", cubes=[k.box("feed_rod", -0.5, 30.2, -0.5, 0.5, tip, 0.5)])
    if tier < 3:
        k.bone("glow_beacon", "feed", pivot=(0, tip + 0.6, 0), cubes=[k.box("beacon", -0.8, tip, -0.8, 0.8, tip + 1.2, 0.8)])
    else:  # T3: a big glowing feed horn and two lamps on the yoke
        k.bone("glow_beacon", "feed", pivot=(0, tip + 1, 0), cubes=[k.box("horn", -1.4, tip, -1.4, 1.4, tip + 2, 1.4)])
        k.bone("glow_beacons", "head", pivot=(0, 31, 0), cubes=[k.box("lamp", -4.6, 30.5, -0.6, -3.4, 31.7, 0.6),
                                                                k.box("lamp", 3.4, 30.5, -0.6, 4.6, 31.7, 0.6)])
    if tier >= 2:  # T2/T3: a whip antenna on the head
        k.bone("antenna", "head", cubes=[k.box("whip", 4.5, 26, 1.5, 5.5, 37, 2.5), k.box("whip_tip", 4.2, 37, 1.2, 5.8, 38, 2.8)])
    name = f"satellite_uplink_t{tier}"
    k.write(name, f"{GEO}{name}.geo.json", bounds=(3, 3), offset=(0, 1.5, 0))


def keys(frames):
    return {f"{t:g}": list(v) for t, v in frames}


def blink(length, period=1.0):
    frames, t = [], 0.0
    while t <= length + 1e-6:
        frames.append((round(t, 2), (1, 1, 1)))
        if t + period / 2 <= length:
            frames.append((round(t + period / 2, 2), (0.45, 0.45, 0.45)))
        t += period
    return keys(frames)


def clips(tier):
    spin = {"head": {"rotation": keys([(0, (0, 0, 0)), (2.5, (0, -90, 0)), (5, (0, -180, 0)), (7.5, (0, -270, 0)), (10, (0, -360, 0))])},
            "dish": {"rotation": keys([(0, (0, 0, 0)), (2.5, (-8, 0, 0)), (5, (0, 0, 0)), (7.5, (6, 0, 0)), (10, (0, 0, 0))])}}
    for bone in ["glow_beacon"] + (["glow_beacons"] if tier == 3 else []):
        spin[bone] = {"scale": blink(10)}
    return {"format_version": "1.8.0", "animations": {
        "animation.satellite_uplink.folded": {"animation_length": 1, "loop": True, "bones": {"dish": {"rotation": keys([(0, (65, 0, 0))])}}},
        "animation.satellite_uplink.deploy": {"animation_length": 1.2, "bones": {
            "dish": {"rotation": keys([(0, (65, 0, 0)), (0.8, (-8, 0, 0)), (1.2, (0, 0, 0))])}}},
        "animation.satellite_uplink.spin": {"animation_length": 10, "loop": True, "bones": spin},
        "animation.satellite_uplink.offline": {"animation_length": 1, "loop": True, "bones": {"dish": {"rotation": keys([(0, (35, 0, 0))])}}},
    }}


if __name__ == "__main__":
    for tier in (1, 2, 3):
        satellite(tier)
        (ANIMATIONS / f"satellite_uplink_t{tier}.animation.json").write_text(json.dumps(clips(tier), indent=1) + "\n")
```

In `tools/fit_item_display.py` add to `MODELS`: `"satellite_uplink_t1": "satellite_uplink_t1", "satellite_uplink_t2": "satellite_uplink_t2", "satellite_uplink_t3": "satellite_uplink_t3"`, and to `SIZE_FACTOR`: `"satellite_uplink_t1": 0.8, "satellite_uplink_t2": 0.8, "satellite_uplink_t3": 0.8`.

- [ ] **Step 5: Block models and blockstates.** Create `tools/gen_control_hub_models.py`:

```python
#!/usr/bin/env python3
"""Turret Control Hub block models and blockstates (PLAN "Turret Control Hub"). Hub Casing and Power Port pick their look
from their place in a formed hub (x, z = alone/low/middle/high; alone = in no hub): formed, a casing becomes the Feed
Hub structure's plain panel, the port keeps its sockets with a glowing ring, and both get the Feed Hub's frame and cyan
glow line along every outer edge of the 3x3 (the Feed Hub's edge models, reused). The Hub Interface (facing x status) is a
console block with a screen tilted back in a recess: dark, a radar sweep (online) or a blinking power glyph (offline);
formed, its outer edges get the frame and line too. Satellites: a particle-only block model (GeckoLib draws them).

    python3 tools/gen_control_hub_models.py
"""
import json
from pathlib import Path

ASSETS = Path(__file__).resolve().parent.parent / "src/main/resources/assets/bastion"
B = "bastion:block/"
FACES = ["down", "up", "north", "south", "west", "east"]
AXIS = {"down": "y", "up": "y", "north": "z", "south": "z", "west": "x", "east": "x"}
LOW_END = {"down", "north", "west"}
TURN = {"north": 0, "east": 90, "south": 180, "west": 270}
GLOW = {"block_light": 15, "sky_light": 15}
FORMED = {"x": "low|middle|high"}


def write(rel, data):
    path = ASSETS / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n")
    print("wrote", rel)


def model(textures, elements):
    return {"parent": "minecraft:block/block", "render_type": "minecraft:cutout", "textures": textures, "elements": elements}


def cube(texture, glow=False):
    element = {"from": [0, 0, 0], "to": [16, 16, 16], "faces": {f: {"texture": texture, "cullface": f} for f in FACES}}
    if glow:
        element["forge_data"], element["shade"] = GLOW, False
    return element


def interface(screen, lit):
    """Front = north: the body behind z 3, lips round a recess, a screen tilted back in it (its top into the block)."""
    def lip(x0, y0, x1, y1):
        return {"from": [x0, y0, 0], "to": [x1, y1, 3], "faces": {
            "north": {"texture": "#c", "cullface": "north"}, "up": {"texture": "#c"}, "down": {"texture": "#c"},
            "west": {"texture": "#c"}, "east": {"texture": "#c"}}}
    body = {"from": [0, 0, 3], "to": [16, 16, 16], "faces": {
        "north": {"texture": "#r"}, "south": {"texture": "#c", "cullface": "south"}, "west": {"texture": "#c", "cullface": "west"},
        "east": {"texture": "#c", "cullface": "east"}, "up": {"texture": "#c", "cullface": "up"}, "down": {"texture": "#c", "cullface": "down"}}}
    glass = {"from": [2.5, 4.5, 1.5], "to": [13.5, 12.5, 2.5], "rotation": {"angle": -22.5, "axis": "x", "origin": [8, 4.5, 2.5]},
             "faces": {"north": {"texture": "#s"}}}
    if lit:
        glass["forge_data"], glass["shade"] = GLOW, False
    textures = {"particle": B + "hub_casing", "c": B + "hub_casing", "r": B + "hub_interface_recess", "s": B + screen}
    return model(textures, [body, lip(0, 0, 16, 4), lip(0, 13, 16, 16), lip(0, 4, 2, 13), lip(14, 4, 16, 13), glass])


def edges(base_when, horizontal_cond):
    """Feed Hub frames, then glow lines (later quads win): top and bottom whenever formed, each side where it is an end."""
    parts = []
    for kind in ("frame", "glow"):
        for end in FACES:
            when = base_when if AXIS[end] == "y" else horizontal_cond(end)
            if when is not None:
                parts.append({"when": when, "apply": {"model": f"{B}feed_hub_{kind}_{end}"}})
    return parts


def layer_end(end):
    return {AXIS[end]: "low" if end in LOW_END else "high"}


if __name__ == "__main__":
    write("models/block/hub_casing.json", model({"particle": B + "hub_casing", "t": B + "hub_casing"}, [cube("#t")]))
    write("models/block/hub_power_port.json", model({"particle": B + "hub_power_port", "t": B + "hub_power_port"}, [cube("#t")]))
    write("models/block/hub_power_port_glow.json", model({"particle": B + "hub_power_port", "t": B + "hub_power_port_glow"}, [cube("#t", glow=True)]))
    for status, screen, lit in (("unformed", "hub_screen_off", False), ("online", "hub_screen_radar", True), ("offline", "hub_screen_alert", True)):
        write(f"models/block/hub_interface_{status}.json", interface(screen, lit))

    write("blockstates/hub_casing.json", {"multipart": [
        {"when": {"x": "alone"}, "apply": {"model": B + "hub_casing"}},
        {"when": FORMED, "apply": {"model": B + "feed_hub_panel"}},
        *edges(FORMED, layer_end)]})
    write("blockstates/hub_power_port.json", {"multipart": [
        {"apply": {"model": B + "hub_power_port"}},
        {"when": FORMED, "apply": {"model": B + "hub_power_port_glow"}},
        *edges(FORMED, layer_end)]})
    lit = {"status": "online|offline"}
    interface_parts = [{"when": {"facing": f, "status": s}, "apply": {"model": f"{B}hub_interface_{s}", "y": turn}}
                       for f, turn in TURN.items() for s in ("unformed", "online", "offline")]
    write("blockstates/hub_interface.json", {"multipart": interface_parts + edges(
        lit, lambda end: {"status": "online|offline", "facing": end})})

    write("models/item/hub_casing.json", {"parent": B + "hub_casing"})
    write("models/item/hub_power_port.json", {"parent": B + "hub_power_port"})
    write("models/item/hub_interface.json", {"parent": B + "hub_interface_online"})
    for tier in (1, 2, 3):
        write(f"models/block/satellite_uplink_t{tier}.json", {"textures": {"particle": f"{B}satellite_uplink_t{tier}"}})
        write(f"blockstates/satellite_uplink_t{tier}.json", {"variants": {"": {"model": f"{B}satellite_uplink_t{tier}"}}})
```

Run, in this order: `cd tools/blockout && python3 design_control_hub.py && cd ../..`, `python3 tools/gen_textures.py`, `python3 tools/fit_item_display.py`, `python3 tools/gen_control_hub_models.py`. Expected: three geos (`satellite_uplink_t1: N cubes …`, no assertion), three clip files, the textures and `.mcmeta` files, three item models with display transforms, the block models and blockstates listed above. `git status` shows only new or Control Hub files changed (plus regenerated textures that were byte-identical before: if any other PNG changed, the generator is not deterministic for it; revert that file).

- [ ] **Step 6: Satellite rendering.** Create `src/main/java/dev/bastion/client/render/SatelliteUplinkModel.java`:

```java
package dev.bastion.client.render;

import dev.bastion.Bastion;
import dev.bastion.hub.SatelliteUplinkBlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.model.GeoModel;

/** Satellite Uplink model files, named after the tier's block (geo/satellite_uplink_t2.geo.json, ...), shared by block and item. */
public class SatelliteUplinkModel<T extends GeoAnimatable> extends GeoModel<T> {
    private static String name(GeoAnimatable animatable) {
        if (animatable instanceof SatelliteUplinkBlockEntity satellite) return BuiltInRegistries.BLOCK.getKey(satellite.getBlockState().getBlock()).getPath();
        return BuiltInRegistries.ITEM.getKey((Item) animatable).getPath();
    }

    @Override
    public ResourceLocation getModelResource(T animatable) {
        return Bastion.id("geo/" + name(animatable) + ".geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(T animatable) {
        return Bastion.id("textures/block/" + name(animatable) + ".png");
    }

    @Override
    public ResourceLocation getAnimationResource(T animatable) {
        return Bastion.id("animations/" + name(animatable) + ".animation.json");
    }
}
```

Create `SatelliteUplinkRenderer.java`:

```java
package dev.bastion.client.render;

import dev.bastion.hub.SatelliteUplinkBlockEntity;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/** The satellite's mast and dish; the glow is cyan online, red offline, dim while the hub is not formed. */
public class SatelliteUplinkRenderer extends GeoBlockRenderer<SatelliteUplinkBlockEntity> {
    private static final int DIM = 0x1E5A6A;

    public SatelliteUplinkRenderer() {
        super(new SatelliteUplinkModel<>());
        addRenderLayer(new EmissiveLayer<>(this, satellite -> switch (satellite.status()) {
            case ONLINE -> EmissiveLayer.MODEL_GLOW;
            case OFFLINE -> EmissiveLayer.CRITICAL_RED;
            case UNFORMED -> DIM;
        }));
    }
}
```

Create `SatelliteUplinkItemRenderer.java`:

```java
package dev.bastion.client.render;

import dev.bastion.hub.SatelliteUplinkItem;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/** A Satellite Uplink as an item: its whole model, dish turning. */
public class SatelliteUplinkItemRenderer extends GeoItemRenderer<SatelliteUplinkItem> {
    public SatelliteUplinkItemRenderer() {
        super(new SatelliteUplinkModel<>());
        addRenderLayer(new EmissiveLayer<>(this, item -> EmissiveLayer.MODEL_GLOW));
    }

    /** Hooked up by SatelliteUplinkItem#initializeClient; built on first use, after resources load. */
    public static IClientItemExtensions extensions() {
        return new IClientItemExtensions() {
            private SatelliteUplinkItemRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) renderer = new SatelliteUplinkItemRenderer();
                return renderer;
            }
        };
    }
}
```

Create `src/main/java/dev/bastion/hub/SatelliteUplinkItem.java`:

```java
package dev.bastion.hub;

import dev.bastion.client.render.SatelliteUplinkItemRenderer;
import net.minecraft.world.item.BlockItem;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.function.Consumer;

/** A Satellite Uplink as an item: its animated model in hand and inventory. */
public class SatelliteUplinkItem extends BlockItem implements GeoItem {
    private static final RawAnimation SPIN = RawAnimation.begin().thenLoop("animation.satellite_uplink.spin");
    private final AnimatableInstanceCache animations = GeckoLibUtil.createInstanceCache(this);

    public SatelliteUplinkItem(SatelliteUplinkBlock block, Properties properties) {
        super(block, properties);
    }

    // Only ever called on the client, so the client-package renderer is never loaded on a server.
    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(SatelliteUplinkItemRenderer.extensions());
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "dish", 0, state -> state.setAndContinue(SPIN)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animations;
    }
}
```

In `BastionItems` the three satellites become:

```java
    public static final RegistryObject<SatelliteUplinkItem> SATELLITE_UPLINK_T1 = REGISTER.register("satellite_uplink_t1",
            () -> new SatelliteUplinkItem(BastionBlocks.SATELLITE_UPLINK_T1.get(), new Item.Properties().stacksTo(16)));
```

(same for T2, T3; import `dev.bastion.hub.SatelliteUplinkItem`). In `BastionClient.onRegisterRenderers` add
`event.registerBlockEntityRenderer(BastionBlockEntities.SATELLITE_UPLINK.get(), context -> new SatelliteUplinkRenderer());`.

- [ ] **Step 7: Blueprint hologram and the forming effects.** In `PlacementPreview`, replace `private static void box(PoseStack pose, MultiBufferSource buffers, BlockPos pos, int rgb)` by these two package-private methods (same body, now on any box):

```java
    /** A block's hologram box: translucent fill and an outline just outside its faces (no z-fighting with what is there). */
    static void box(PoseStack pose, MultiBufferSource buffers, BlockPos pos, int rgb) {
        box(pose, buffers, new AABB(pos).inflate(0.002), rgb, FILL, EDGE);
    }

    static void box(PoseStack pose, MultiBufferSource buffers, AABB box, int rgb, float fillAlpha, float edgeAlpha) {
        float r = (rgb >> 16 & 255) / 255f, g = (rgb >> 8 & 255) / 255f, b = (rgb & 255) / 255f;
        LevelRenderer.renderLineBox(pose, buffers.getBuffer(RenderType.lines()), box, r, g, b, edgeAlpha);
        VertexConsumer fill = buffers.getBuffer(BastionRenderTypes.ghostBox());
        Matrix4f m = pose.last().pose();
        float x0 = (float) box.minX, y0 = (float) box.minY, z0 = (float) box.minZ, x1 = (float) box.maxX, y1 = (float) box.maxY, z1 = (float) box.maxZ;
        float[][] quads = {
                {x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1}, {x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0},
                {x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0}, {x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1},
                {x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0}, {x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1}};
        for (float[] q : quads) {
            for (int i = 0; i < 12; i += 3) fill.vertex(m, q[i], q[i + 1], q[i + 2]).color(r, g, b, fillAlpha).endVertex();
        }
    }
```

Create `src/main/java/dev/bastion/client/render/HubBlueprint.java`:

```java
package dev.bastion.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.bastion.Bastion;
import dev.bastion.hub.ControlHubStructure;
import dev.bastion.hub.HubInterfaceBlock;
import dev.bastion.hub.HubInterfaceBlock.Status;
import dev.bastion.registry.BastionItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * Control Hub holograms (PLAN "Belum jadi: petunjuk ala Tinkers"). The blueprint: right-clicking an unfinished Hub
 * Interface shows, for 10 s and only to that player, a box per problem: cyan with the needed block turning inside where
 * one is missing, red where a block is in the way or belongs to another hub, amber on every casing while there is no
 * Power Port. Checked again every frame from the client's blocks, so boxes go as the player fills them, and all of it once
 * the hub forms. The scan: a cyan sheet sweeping a hub's layer from the Interface to the back as it forms.
 */
@Mod.EventBusSubscriber(modid = Bastion.MOD_ID, value = Dist.CLIENT)
public final class HubBlueprint {
    private static final int SHOW_TICKS = 200, SCAN_TICKS = 24;
    private static final int RED = 0xFF3A30, AMBER = 0xFFB347;
    @Nullable
    private static BlockPos shown, scanned;
    private static Direction shownFacing = Direction.NORTH, scanFacing = Direction.NORTH;
    private static long shownUntil, scanStart;

    private HubBlueprint() {
    }

    /** Shows the blueprint of the hub whose Interface is at {@code iface}. */
    public static void show(BlockPos iface, Direction facing) {
        shown = iface;
        shownFacing = facing;
        shownUntil = time() + SHOW_TICKS;
    }

    /** Sweeps the scan over the hub whose Interface is at {@code iface}. */
    public static void scan(BlockPos iface, Direction facing) {
        scanned = iface;
        scanFacing = facing;
        scanStart = time();
    }

    private static long time() {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null ? 0 : level.getGameTime();
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || (shown == null && scanned == null)) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        Vec3 camera = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        if (shown != null) blueprint(level, pose, buffers, event.getPartialTick());
        if (scanned != null) scan(level, pose, buffers, event.getPartialTick());
        pose.popPose();
        buffers.endBatch();
    }

    private static void blueprint(ClientLevel level, PoseStack pose, MultiBufferSource buffers, float partialTick) {
        BlockState state = level.getBlockState(shown);
        if (level.getGameTime() > shownUntil || !(state.getBlock() instanceof HubInterfaceBlock) || state.getValue(HubInterfaceBlock.STATUS) != Status.UNFORMED) {
            shown = null;
            return;
        }
        float spin = (level.getGameTime() + partialTick) * 4;
        for (ControlHubStructure.Issue issue : ControlHubStructure.check(level, shown, shownFacing).issues()) {
            switch (issue.kind()) {
                case NEED_BLOCK -> {
                    PlacementPreview.box(pose, buffers, issue.pos(), EmissiveLayer.MODEL_GLOW);
                    item(pose, buffers, issue.pos(), BastionItems.HUB_CASING.get(), spin);
                }
                case NEED_SATELLITE -> {
                    PlacementPreview.box(pose, buffers, issue.pos(), EmissiveLayer.MODEL_GLOW);
                    PlacementPreview.box(pose, buffers, issue.pos().above(), EmissiveLayer.MODEL_GLOW);
                    item(pose, buffers, issue.pos(), BastionItems.SATELLITE_UPLINK_T1.get(), spin);
                }
                case NEED_PORT -> PlacementPreview.box(pose, buffers, issue.pos(), AMBER);
                default -> PlacementPreview.box(pose, buffers, issue.pos(), RED);
            }
        }
    }

    /** The needed block as a small item turning in the middle of its box. */
    private static void item(PoseStack pose, MultiBufferSource buffers, BlockPos pos, Item item, float spin) {
        pose.pushPose();
        pose.translate(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(spin));
        pose.scale(0.5f, 0.5f, 0.5f);
        Minecraft mc = Minecraft.getInstance();
        mc.getItemRenderer().renderStatic(new ItemStack(item), ItemDisplayContext.FIXED, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                pose, buffers, mc.level, 0);
        pose.popPose();
    }

    /** A one-block-tall cyan sheet across the layer, moving from the Interface's front face to the back over SCAN_TICKS, fading. */
    private static void scan(ClientLevel level, PoseStack pose, MultiBufferSource buffers, float partialTick) {
        float t = (level.getGameTime() - scanStart + partialTick) / SCAN_TICKS;
        if (t < 0 || t >= 1) {
            scanned = null;
            return;
        }
        Direction back = scanFacing.getOpposite(), right = back.getClockWise();
        Vec3 front = Vec3.atBottomCenterOf(scanned).add(Vec3.atLowerCornerOf(scanFacing.getNormal()).scale(0.5));
        Vec3 depth = Vec3.atLowerCornerOf(back.getNormal()), across = Vec3.atLowerCornerOf(right.getNormal());
        Vec3 a = front.add(depth.scale(3 * t)).add(across.scale(-1.5));
        Vec3 b = front.add(depth.scale(3 * t + 0.1)).add(across.scale(1.5)).add(0, 1.05, 0);
        PlacementPreview.box(pose, buffers, new AABB(a, b), EmissiveLayer.MODEL_GLOW, 0.35f * (1 - t), 0.9f * (1 - t));
    }
}
```

Create `src/main/java/dev/bastion/client/ControlHubEffects.java`:

```java
package dev.bastion.client;

import dev.bastion.client.render.HubBlueprint;
import dev.bastion.client.vfx.VfxManager;
import dev.bastion.client.vfx.VfxParams;
import dev.bastion.client.vfx.VfxPresets;
import dev.bastion.hub.ControlHubStructure;
import dev.bastion.hub.HubInterfaceBlock.Status;
import dev.bastion.hub.SatelliteUplinkBlockEntity;
import dev.bastion.registry.BastionSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/** Control Hub client effects (PLAN "Tampilan"): the scan and a flash at the dish when a hub forms; the dish's hum while online. */
public final class ControlHubEffects {
    private static final Map<BlockPos, DishHum> HUMS = new HashMap<>();

    private ControlHubEffects() {
    }

    /** A hub this client saw forming. */
    public static void formed(BlockPos iface, Direction facing) {
        HubBlueprint.scan(iface, facing);
        Vec3 dish = Vec3.atBottomCenterOf(ControlHubStructure.center(iface, facing)).add(0, 2.8, 0);
        VfxManager.play(VfxPresets.TIER_UP, dish, new Vec3(0, 1, 0), VfxParams.of().scale(0.6f).color(VfxPresets.CYAN).seed(iface.hashCode()));
    }

    /** Keeps the dish humming while its satellite is online (called every client tick by the satellite while online). */
    public static void dishHum(SatelliteUplinkBlockEntity satellite) {
        DishHum hum = HUMS.get(satellite.getBlockPos());
        if (hum != null && !hum.isStopped()) return;
        hum = new DishHum(satellite);
        HUMS.put(satellite.getBlockPos(), hum);
        Minecraft.getInstance().getSoundManager().play(hum);
    }

    /** The dish's loop: stops once its satellite is gone or no longer online. */
    private static final class DishHum extends AbstractTickableSoundInstance {
        private final SatelliteUplinkBlockEntity satellite;

        DishHum(SatelliteUplinkBlockEntity satellite) {
            super(BastionSounds.CONTROL_HUB_DISH_LOOP.get(), SoundSource.BLOCKS, RandomSource.create());
            this.satellite = satellite;
            looping = true;
            delay = 0;
            volume = 0.5f;
            attenuation = SoundInstance.Attenuation.LINEAR;
            x = satellite.getBlockPos().getX() + 0.5;
            y = satellite.getBlockPos().getY() + 1.6;
            z = satellite.getBlockPos().getZ() + 0.5;
        }

        @Override
        public void tick() {
            if (satellite.isRemoved() || satellite.status() != Status.ONLINE) {
                stop();
                HUMS.remove(satellite.getBlockPos(), this);
            }
        }
    }
}
```

Wire them up:
- `SatelliteUplinkBlockEntity.clientTick`, last line: `if (now == Status.ONLINE) DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ControlHubEffects.dishHum(satellite));` (imports `dev.bastion.client.ControlHubEffects`, `net.minecraftforge.api.distmarker.Dist`, `net.minecraftforge.fml.DistExecutor`).
- `HubInterfaceBlockEntity` (imports `dev.bastion.client.ControlHubEffects`, `net.minecraftforge.api.distmarker.Dist`, `net.minecraftforge.fml.DistExecutor`, `org.jetbrains.annotations.Nullable`): field `@Nullable private Status seen;` (client: status one tick ago) and

```java
    /** Client: a hub seen forming gets its scan and dish flash (not one already formed when its chunk loaded). */
    public static void clientTick(Level level, BlockPos pos, BlockState state, HubInterfaceBlockEntity hub) {
        Status now = state.getValue(HubInterfaceBlock.STATUS);
        if (hub.seen == Status.UNFORMED && now != Status.UNFORMED) {
            Direction facing = state.getValue(HubInterfaceBlock.FACING);
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ControlHubEffects.formed(pos, facing));
        }
        hub.seen = now;
    }
```

- `HubInterfaceBlock.getTicker`: `return createTickerHelper(type, BastionBlockEntities.HUB_INTERFACE.get(), level.isClientSide ? HubInterfaceBlockEntity::clientTick : HubInterfaceBlockEntity::serverTick);`
- `HubInterfaceBlock.use`: replace `if (level.isClientSide) return InteractionResult.SUCCESS;` by

```java
        if (level.isClientSide) {
            if (state.getValue(STATUS) == Status.UNFORMED) {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> HubBlueprint.show(pos, state.getValue(FACING)));
            }
            return InteractionResult.SUCCESS;
        }
```

  (imports `dev.bastion.client.render.HubBlueprint`, `Dist`, `DistExecutor`).

- [ ] **Step 8: Sound sheet.** In `docs/SOUND_SHEET.md`: the count line becomes "70 sound event, termasuk 8 milik Ion Rifle dan Plasma dan 4 milik Turret Control Hub"; add four summary rows (P2 for `control_hub.online`, `control_hub.offline`, `control_hub.dish_loop` (loop ya); P3 for `control_hub.ui_select`) and a "## Turret Control Hub" section with entries 67-70 in the existing format:
  - 67 `control_hub.online`, P2, 1,2 dtk, 1 varian, `control_hub/online.ogg`, 24 blok, "Hub jadi atau FE kembali: hub menyala." Cari: `sci-fi system online` · `power up chime` · `servo startup`. Prompt: "A sci-fi command station coming online: a short servo whirr under two rising clean electronic chimes, confident and bright. 1.2 seconds. Close-mic, dry, isolated sound effect, mono, no music, no voices."
  - 68 `control_hub.offline`, P2, 1 dtk, 1 varian, `control_hub/offline.ogg`, 24 blok, "FE habis: hub mati." Cari: `power down sci-fi` · `system shutdown` · `relay click power off`. Prompt: "A sci-fi console losing power: a falling electronic tone sliding down with a soft relay click at the start, short and clear. 1 second. Close-mic, dry, isolated sound effect, mono, no music, no voices."
  - 69 `control_hub.dish_loop`, P2, 3 dtk, 1 varian, loop, `control_hub/dish_loop.ogg`, 12 blok, "Dish satelit berputar saat hub online." Cari: `radar dish motor loop` · `servo hum loop` · `antenna rotator`. Prompt: "A satellite dish slowly rotating on a motorized mount: a low steady electrical hum with a soft rhythmic servo whirr, seamless loop. 3 seconds. Close-mic, dry, isolated sound effect, mono, seamless loop, no music, no voices."
  - 70 `control_hub.ui_select`, P3, 0,12 dtk, 1 varian, `control_hub/ui_select.ogg`, GUI (tanpa jarak), "Klik/pilih turret di layar hub." Cari: `ui beep short` · `sci-fi interface click`. Prompt: "A tiny clean sci-fi interface beep for selecting an item on a tactical map, very short. 0.12 seconds. Dry, isolated UI sound, mono, no music, no voices."

- [ ] **Step 9: Scene.** Create `src/dev/java/dev/bastion/dev/ControlHubShowcase.java`:

```java
package dev.bastion.dev;

import dev.bastion.Bastion;
import dev.bastion.client.render.HubBlueprint;
import dev.bastion.hub.ControlHubStructure;
import dev.bastion.hub.HubInterfaceBlock;
import dev.bastion.hub.HubInterfaceBlockEntity;
import dev.bastion.hub.SatelliteUplinkBlock;
import dev.bastion.registry.BastionBlocks;
import dev.bastion.registry.BastionItems;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.TurretInventory;
import dev.bastion.turret.TurretTier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * Dev-only scene for the Turret Control Hub (-Pshowcase=hub, with -PshowcaseWorld=showcase_fx): an unfinished hub with
 * its blueprint, then T1, T2 and T3 hubs side by side, their Interfaces facing the camera (south), powered by Creative
 * Power Sources, with turrets around the T3 one (healthy, damaged, critical, unarmed, out of ammo), by day and by night;
 * then the T3 hub's screens (Map, List, zoom, a turret opened through it, back, Batch settings) and an OFFLINE hub.
 */
@Mod.EventBusSubscriber(modid = Bastion.MOD_ID, value = Dist.CLIENT)
public final class ControlHubShowcase {
    private static final boolean ON = "hub".equals(System.getProperty("bastion.showcase"));
    static final BlockPos ORIGIN = new BlockPos(0, 150, 0);
    static final BlockPos T1_IFACE = ORIGIN.offset(-8, 0, 4), T2_IFACE = ORIGIN.offset(0, 0, 4), T3_IFACE = ORIGIN.offset(8, 0, 4);
    static final BlockPos UNFINISHED = ORIGIN.offset(-18, 0, 4);
    private static int tick = -1;

    private ControlHubShowcase() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (!ON || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        if (mc.player == null || server == null) return;
        mc.options.pauseOnLostFocus = false;
        if (mc.screen instanceof PauseScreen) mc.setScreen(null);
        tick++;
        Vec3 lineup = Vec3.atBottomCenterOf(T2_IFACE).add(0, 1.5, -1);
        Vec3 dish = Vec3.atBottomCenterOf(T3_IFACE.north()).add(0, 2.4, 0);
        Vec3 unfinished = Vec3.atBottomCenterOf(UNFINISHED.north()).add(0, 1, 0);
        if (tick == 20) Showcase.server(server, ControlHubShowcase::build);
        if (tick == 22) mc.options.hideGui = true;
        if (tick == 40) Showcase.camera(server, lineup.add(0, 5, 13), lineup);
        if (tick == 60) Showcase.shot(mc, "hub_day");
        if (tick == 62) Showcase.camera(server, dish.add(3.2, 1.2, 3.6), dish);
        if (tick == 80) Showcase.shot(mc, "hub_dish");
        if (tick == 82) {
            Showcase.camera(server, unfinished.add(2.5, 3.5, 5.5), unfinished);
            HubBlueprint.show(UNFINISHED, Direction.SOUTH);
        }
        if (tick == 92) Showcase.shot(mc, "hub_blueprint");
        if (tick == 94) Showcase.server(server, s -> s.overworld().setDayTime(18000));
        if (tick == 96) Showcase.camera(server, lineup.add(0, 5, 13), lineup);
        if (tick == 112) Showcase.shot(mc, "hub_night");
        if (tick == 114) Showcase.server(server, s -> s.overworld().setDayTime(6000));
        if (tick == 130) mc.stop(); // Task 6 moves this past its screen shots
    }

    static void build(MinecraftServer server) {
        ServerLevel level = server.overworld();
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        level.setDayTime(6000);
        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
        player.setGameMode(GameType.CREATIVE);
        player.getInventory().clearContent();
        for (int x = -40; x <= 40; x++) {
            for (int z = -40; z <= 40; z++) {
                level.setBlock(ORIGIN.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
                for (int y = 0; y < 8; y++) level.setBlock(ORIGIN.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
            }
        }
        // The scratch world keeps what earlier scenes left (drops): clear the stage.
        level.getEntitiesOfClass(ItemEntity.class, new AABB(ORIGIN).inflate(48)).forEach(Entity::discard);
        hub(level, T1_IFACE, 1);
        hub(level, T2_IFACE, 2);
        hub(level, T3_IFACE, 3);
        // Unfinished: no satellite, one casing missing.
        hub(level, UNFINISHED, 0);
        level.setBlock(ControlHubStructure.slots(UNFINISHED, Direction.SOUTH).get(5), Blocks.AIR.defaultBlockState(), 3);
        // Turrets round the T3 hub, in its 7x7 chunks.
        turret(level, T3_IFACE.offset(-4, 0, 6), BastionItems.GUN_TURRET.get(), 1f, true);
        turret(level, T3_IFACE.offset(5, 0, 7), BastionItems.MACHINE_GUN_TURRET.get(), 0.4f, true);
        turret(level, T3_IFACE.offset(14, 0, -6), BastionItems.SNIPER_TURRET.get(), 0.15f, true);
        turret(level, T3_IFACE.offset(-12, 0, -14), BastionItems.SHOTGUN_TURRET.get(), 1f, false);
        turret(level, T3_IFACE.offset(24, 0, 18), null, 1f, false);
        turret(level, T3_IFACE.offset(30, 0, -30), BastionItems.ION_RIFLE_TURRET.get(), 0.8f, true);
    }

    /** A hub whose Interface faces south (the camera), its Power Port fed by a Creative Power Source; tier 0: no satellite. */
    static HubInterfaceBlockEntity hub(ServerLevel level, BlockPos iface, int tier) {
        level.setBlock(iface, BastionBlocks.HUB_INTERFACE.get().defaultBlockState().setValue(HubInterfaceBlock.FACING, Direction.SOUTH), 3);
        List<BlockPos> slots = ControlHubStructure.slots(iface, Direction.SOUTH);
        for (int i = 0; i < slots.size(); i++) {
            level.setBlock(slots.get(i), (i == 0 ? BastionBlocks.HUB_POWER_PORT : BastionBlocks.HUB_CASING).get().defaultBlockState(), 3);
        }
        level.setBlock(slots.get(0).relative(Direction.SOUTH.getOpposite().getCounterClockWise()), BastionBlocks.CREATIVE_POWER_SOURCE.get().defaultBlockState(), 3);
        if (tier > 0) {
            SatelliteUplinkBlock satellite = (tier == 3 ? BastionBlocks.SATELLITE_UPLINK_T3 : tier == 2 ? BastionBlocks.SATELLITE_UPLINK_T2
                    : BastionBlocks.SATELLITE_UPLINK_T1).get();
            satellite.placeAt(level, ControlHubStructure.center(iface, Direction.SOUTH).above());
        }
        return (HubInterfaceBlockEntity) level.getBlockEntity(iface);
    }

    /** A floor turret with {@code weapon} (null: none) at {@code health} of its HP, with Creative Ammo unless {@code ammo} is false. */
    private static void turret(ServerLevel level, BlockPos pos, Item weapon, float health, boolean ammo) {
        if (weapon == null) {
            level.setBlock(pos, BastionBlocks.TURRET_BASE.get().defaultBlockState(), 3);
            return;
        }
        Showcase.place(level, pos, Direction.UP, weapon, TurretTier.T2);
        TurretBaseBlockEntity turret = (TurretBaseBlockEntity) level.getBlockEntity(pos);
        if (!ammo) turret.inventory().setStackInSlot(TurretInventory.AMMO_START, ItemStack.EMPTY);
        if (health < 1) turret.takeDamage(level.damageSources().generic(), turret.maxHealth() * (1 - health));
    }
}
```

(`slots(iface, SOUTH).get(0)` is the front-left slot seen from the camera, i.e. west of the Interface; the power source sits west of it, outside the 3x3. The `ION_RIFLE_TURRET` gets FE from Creative Ammo, as every weapon.) In `Showcase.java` add `&& !System.getProperty("bastion.showcase").equals("hub")` to `ON`.

- [ ] **Step 10: Run.** `./gradlew build --offline`; `./gradlew runGameTestServer --offline` (expected "All 139 required tests passed"); `./gradlew runClient --offline -Pshowcase=hub -PshowcaseWorld=showcase_fx`. Then look at `run/screenshots/showcase_hub_day.png`, `_dish`, `_blueprint`, `_night` with the Read tool and check:
  - three formed hubs: platforms with cyan edge lines like a Feed Hub structure, Interfaces facing the camera with the radar screen lit, dishes bigger T1 -> T3, dish raised and turned (spin);
  - the unfinished hub: cyan boxes over the missing casing slot and the two satellite cells with a turning Hub Casing / Satellite Uplink inside, no red;
  - night: screens, port rings, edge lines and dish beacons glow.
  Fix what reads wrong (the screen must lean back into the Interface: flip the `-22.5` angle in `gen_control_hub_models.py` if it tips out; the dish must face forward-up: flip the dish bone's `-50` or the clip signs in `design_control_hub.py`), re-run the generators and the scene.

---

## Stage 2: reach, remote turret GUIs, the hub screen

### Task 4: Reach, the hub menu and its roster

**Files:**
- Create: `src/main/java/dev/bastion/menu/ControlHubMenu.java`, `src/main/java/dev/bastion/network/HubRoster.java`
- Modify: `src/main/java/dev/bastion/hub/HubInterfaceBlockEntity.java`, `hub/HubInterfaceBlock.java`, `registry/BastionMenus.java`, `network/BastionNetwork.java`, `client/ClientPacketHandler.java`, `gametest/ControlHubTests.java`, `lang/en_us.json`, `lang/id_id.json`

**Interfaces:**
- Consumes: Task 1-2 hub API; `TurretBaseBlockEntity.canConfigure/health/maxHealth/state/enabled/energy/energyCapacity/filter/inventory`.
- Produces: `HubInterfaceBlockEntity implements MenuProvider`: `boolean usableBy(Player)`, `boolean covers(BlockPos)`, `@Nullable TurretBaseBlockEntity reachable(Player, BlockPos)`, `List<TurretBaseBlockEntity> visibleTurrets(Player)`, `void openMenu(ServerPlayer)`.
- Produces: `ControlHubMenu` (`MAX_TURRETS` 256, `FILTERS_PER_SEND` 32; records `Header(Status status, int tier, int energy, int capacity, int upkeep, int side, int total, BlockPos center)` with `LOADING`, `Entry(BlockPos pos, Item weapon, float health, float maxHealth, TurretState state, boolean enabled, int ammo, int ammoCapacity, int energy, int energyCapacity)` with `healthFraction()`, `energyWeapon()`, `fill()`; server `HubRoster roster()`, `@Nullable HubInterfaceBlockEntity hub()`, `@Nullable ServerPlayer player()`; client `apply(HubRoster)`, `Header header()`, `List<Entry> turrets()`, `@Nullable TargetFilter filter(BlockPos)`, `BlockPos hubPos()`); `BastionMenus.CONTROL_HUB`; packet `HubRoster(int containerId, Header header, List<Entry> turrets, Map<BlockPos, CompoundTag> filters)`; `ClientPacketHandler.hubRoster(HubRoster)`; protocol "10".

- [ ] **Step 1: Failing tests.** Add to `ControlHubTests` (imports `dev.bastion.menu.ControlHubMenu`, `dev.bastion.network.HubRoster`, `dev.bastion.turret.LargeTurretBaseBlock`, `dev.bastion.turret.TurretBaseBlock`, `dev.bastion.turret.TurretBaseBlockEntity`, `dev.bastion.turret.targeting.TargetFilter`, `net.minecraftforge.common.util.FakePlayer`, `net.minecraftforge.common.util.FakePlayerFactory`, `net.minecraft.world.phys.Vec3`, `org.jetbrains.annotations.Nullable`, `java.util.Locale`, `java.util.Set`, `java.util.UUID`):

```java
    /** The fake player standing on test position {@code rel}, no GUI open; not an operator on the GameTest server. */
    static FakePlayer player(GameTestHelper helper, BlockPos rel) {
        FakePlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(rel));
        player.setPos(at.x, at.y, at.z);
        player.containerMenu = player.inventoryMenu;
        return player;
    }

    /** An unarmed floor base at absolute {@code pos} owned by {@code owner} (null: nobody). */
    static TurretBaseBlockEntity base(ServerLevel level, BlockPos pos, @Nullable UUID owner) {
        level.setBlock(pos, BastionBlocks.TURRET_BASE.get().defaultBlockState(), 3);
        TurretBaseBlockEntity base = (TurretBaseBlockEntity) level.getBlockEntity(pos);
        base.setOwner(owner);
        return base;
    }

    /** The reach is a square of chunks centred on the satellite's chunk: 1, 3, 7 chunks a side. */
    @GameTest(template = EMPTY, batch = "coverageIsChunkSquare")
    public static void coverageIsChunkSquare(GameTestHelper helper) {
        BlockPos center = new BlockPos(8, 64, 8); // chunk 0, 0
        record Case(int side, int x, int z, boolean in) {
        }
        for (Case c : List.of(new Case(1, 15, 0, true), new Case(1, 16, 8, false), new Case(1, -1, 8, false),
                new Case(3, 31, -16, true), new Case(3, 32, 8, false), new Case(3, 8, -17, false),
                new Case(7, 63, -48, true), new Case(7, 64, 8, false), new Case(7, 8, -49, false))) {
            boolean in = ControlHubStructure.covers(center, c.side(), new BlockPos(c.x(), 70, c.z()));
            helper.assertTrue(in == c.in(), "side " + c.side() + " at " + c.x() + ", " + c.z() + ": " + in);
        }
        helper.succeed();
    }

    /**
     * In the satellite's chunk (all a T1 reaches): the player's turret, an unowned one, the player's large base, and a
     * stranger's that shows only once it trusts the player; one just past the chunk never shows. The menu sends each
     * turret's targeting once, then only what changed.
     */
    @GameTest(template = YARD, timeoutTicks = 60, batch = "hubListsOnlyTurretsThePlayerMayOpen")
    public static void hubListsOnlyTurretsThePlayerMayOpen(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer player = player(helper, new BlockPos(15, 2, 13));
        helper.assertTrue(!player.hasPermissions(2), "the fake player is an operator here: ownership cannot be tested");
        HubInterfaceBlockEntity hub = hub(helper, new BlockPos(15, 2, 14), 1);
        hub.setEnergy(100_000);
        BlockPos center = hub.center();
        // The corner of the satellite's chunk farthest from the hub, so nothing lands in its 3x3.
        int x = (center.getX() & 15) < 8 ? (center.getX() | 15) - 3 : (center.getX() & ~15) + 1;
        int z = (center.getZ() & 15) < 8 ? (center.getZ() | 15) - 3 : (center.getZ() & ~15) + 1;
        BlockPos corner = new BlockPos(x, center.getY(), z);
        TurretBaseBlockEntity own = base(level, corner, player.getUUID());
        TurretBaseBlockEntity free = base(level, corner.east(), null);
        TurretBaseBlockEntity stranger = base(level, corner.east(2), UUID.randomUUID());
        LargeTurretBaseBlock.placeAt(level, corner.south(), BastionBlocks.LARGE_TURRET_BASE.get());
        TurretBaseBlockEntity large = TurretBaseBlock.core(level, corner.south());
        large.setOwner(player.getUUID());
        BlockPos outside = new BlockPos((center.getX() & ~15) - 2, center.getY(), center.getZ());
        base(level, outside, player.getUUID());
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(hub.online(), "the hub is not online");
            List<TurretBaseBlockEntity> seen = hub.visibleTurrets(player);
            helper.assertTrue(seen.size() == 3 && seen.containsAll(List.of(own, free, large)), "listed " + seen.size() + ": " + seen);
            ControlHubMenu menu = new ControlHubMenu(0, player.getInventory(), hub);
            HubRoster first = menu.roster(), second = menu.roster();
            helper.assertTrue(first.turrets().size() == 3 && first.filters().size() == 3 && second.filters().isEmpty(),
                    "targeting sent for " + first.filters().size() + ", then " + second.filters().size());
            TargetFilter trusting = stranger.filter().copy();
            trusting.trusted.add(player.getGameProfile().getName().toLowerCase(Locale.ROOT));
            stranger.setFilter(trusting);
            HubRoster third = menu.roster();
            helper.assertTrue(third.turrets().size() == 4 && third.filters().keySet().equals(Set.of(stranger.getBlockPos())),
                    "after trusting: " + third.turrets().size() + " turrets, targeting sent for " + third.filters().keySet());
            for (BlockPos pos : List.of(corner, corner.east(), corner.east(2), outside)) level.removeBlock(pos, false);
            for (BlockPos pos : LargeTurretBaseBlock.footprint(corner.south())) level.removeBlock(pos, false);
            helper.succeed();
        });
    }
```

- [ ] **Step 2: Run to verify it fails.** `./gradlew runGameTestServer --offline`. Expected: compile errors (`ControlHubMenu`, `HubRoster`, `visibleTurrets` undefined).

- [ ] **Step 3: Reach and the menu provider.** `HubInterfaceBlockEntity` now `implements MenuProvider` (imports `dev.bastion.menu.ControlHubMenu`, `dev.bastion.turret.TurretBaseBlockEntity`, `net.minecraft.network.chat.Component`, `net.minecraft.server.level.ServerPlayer`, `net.minecraft.world.MenuProvider`, `net.minecraft.world.entity.player.Inventory`, `net.minecraft.world.entity.player.Player`, `net.minecraft.world.inventory.AbstractContainerMenu`, `net.minecraft.world.level.chunk.LevelChunk`, `net.minecraft.world.phys.Vec3`, `net.minecraftforge.network.NetworkHooks`, `org.jetbrains.annotations.Nullable`, `java.util.ArrayList`, `java.util.Comparator`, `java.util.List`); add:

```java
    /** The player may use this hub's screen: formed, and within 8 blocks of the Interface (as for turret GUIs). */
    public boolean usableBy(Player player) {
        return !isRemoved() && formed() && player.level() == level && player.distanceToSqr(Vec3.atCenterOf(worldPosition)) <= 64;
    }

    /** Whether {@code pos} is in this hub's reach. */
    public boolean covers(BlockPos pos) {
        return ControlHubStructure.covers(center(), side(), pos);
    }

    /** The turret at {@code pos} if the player can reach it through this hub now: online, in reach, loaded, theirs to open. */
    @Nullable
    public TurretBaseBlockEntity reachable(Player player, BlockPos pos) {
        if (!online() || !usableBy(player) || !covers(pos) || !level.isLoaded(pos)) return null;
        return level.getBlockEntity(pos) instanceof TurretBaseBlockEntity turret && !turret.isRemoved() && turret.canConfigure(player) ? turret : null;
    }

    /** Every turret in reach the player may open, in loaded chunks only, nearest the satellite first; none unless online. */
    public List<TurretBaseBlockEntity> visibleTurrets(Player player) {
        if (!online() || level == null) return List.of();
        BlockPos center = center();
        int r = side() / 2, cx = center.getX() >> 4, cz = center.getZ() >> 4;
        List<TurretBaseBlockEntity> found = new ArrayList<>();
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(x, z);
                if (chunk == null) continue;
                for (BlockEntity entity : chunk.getBlockEntities().values()) {
                    if (entity instanceof TurretBaseBlockEntity turret && !turret.isRemoved() && turret.canConfigure(player)) found.add(turret);
                }
            }
        }
        found.sort(Comparator.comparingDouble(turret -> turret.getBlockPos().distSqr(center)));
        return found;
    }

    /** Opens the hub's screen; the menu learns the Interface's position from the open data. */
    public void openMenu(ServerPlayer player) {
        NetworkHooks.openScreen(player, this, buf -> buf.writeBlockPos(worldPosition));
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("gui.bastion.control_hub.title");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new ControlHubMenu(id, inventory, this);
    }
```

In `HubInterfaceBlock.use`, the server part becomes:

```java
        if (level.getBlockEntity(pos) instanceof HubInterfaceBlockEntity hub) {
            if (!hub.formed()) hub.recheck(); // the last part may have gone in this very tick
            if (hub.formed()) {
                if (player instanceof ServerPlayer serverPlayer) hub.openMenu(serverPlayer);
            } else {
                player.displayClientMessage(ControlHubStructure.message(ControlHubStructure.check(level, pos, state.getValue(FACING))), true);
            }
        }
        return InteractionResult.CONSUME;
```

(import `net.minecraft.server.level.ServerPlayer`).

- [ ] **Step 4: The menu.** Create `src/main/java/dev/bastion/menu/ControlHubMenu.java`:

```java
package dev.bastion.menu;

import dev.bastion.hub.HubInterfaceBlock.Status;
import dev.bastion.hub.HubInterfaceBlockEntity;
import dev.bastion.network.BastionNetwork;
import dev.bastion.network.HubRoster;
import dev.bastion.registry.BastionMenus;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.TurretInventory;
import dev.bastion.turret.TurretState;
import dev.bastion.turret.targeting.TargetFilter;
import dev.bastion.weapon.WeaponData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Turret Control Hub screen (PLAN "Turret Control Hub"). No slots: every half second the server sends the header and the
 * turrets this player can reach (HubRoster), each turret's targeting only when it changed since the last send (at most
 * FILTERS_PER_SEND per packet). On the server it also carries out what the screen asks.
 */
public class ControlHubMenu extends AbstractContainerMenu {
    public static final int MAX_TURRETS = 256, FILTERS_PER_SEND = 32;
    private static final int SEND_INTERVAL = 10;

    /** The hub as the header shows it; total = turrets in reach before the 256 cap. */
    public record Header(Status status, int tier, int energy, int capacity, int upkeep, int side, int total, BlockPos center) {
        /** Until the first roster arrives. */
        public static final Header LOADING = new Header(Status.UNFORMED, 1, 0, 0, 0, 1, 0, BlockPos.ZERO);

        public void write(FriendlyByteBuf buf) {
            buf.writeEnum(status);
            buf.writeVarInt(tier);
            buf.writeVarInt(energy);
            buf.writeVarInt(capacity);
            buf.writeVarInt(upkeep);
            buf.writeVarInt(side);
            buf.writeVarInt(total);
            buf.writeBlockPos(center);
        }

        public static Header read(FriendlyByteBuf buf) {
            return new Header(buf.readEnum(Status.class), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readBlockPos());
        }
    }

    /** One turret as the hub shows it. */
    public record Entry(BlockPos pos, Item weapon, float health, float maxHealth, TurretState state, boolean enabled,
                        int ammo, int ammoCapacity, int energy, int energyCapacity) {
        static Entry of(TurretBaseBlockEntity turret) {
            TurretInventory inventory = turret.inventory();
            int ammo = 0;
            for (int i = 0; i < inventory.ammoSlots(); i++) {
                ItemStack stack = inventory.getStackInSlot(TurretInventory.AMMO_START + i);
                if (inventory.isAmmo(stack)) ammo += stack.getCount();
            }
            WeaponData data = inventory.weaponData();
            int perSlot = data == null ? 64 : BuiltInRegistries.ITEM.getTag(data.ammo())
                    .flatMap(tag -> tag.stream().findFirst()).map(item -> item.value().getMaxStackSize()).orElse(64);
            return new Entry(turret.getBlockPos(), inventory.getStackInSlot(TurretInventory.WEAPON).getItem(), turret.health(),
                    turret.maxHealth(), turret.state(), turret.enabled(), ammo, inventory.ammoSlots() * perSlot, turret.energy(),
                    turret.energyCapacity());
        }

        public float healthFraction() {
            return maxHealth <= 0 ? 1 : health / maxHealth;
        }

        public boolean energyWeapon() {
            return energyCapacity > 0;
        }

        /** How full, 0-1: FE against the capacitor for energy weapons, else ammo items against the open ammo slots. */
        public float fill() {
            if (energyWeapon()) return energy / (float) energyCapacity;
            return ammoCapacity <= 0 ? 0 : Math.min(1, ammo / (float) ammoCapacity);
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeId(BuiltInRegistries.ITEM, weapon);
            buf.writeFloat(health);
            buf.writeFloat(maxHealth);
            buf.writeEnum(state);
            buf.writeBoolean(enabled);
            buf.writeVarInt(ammo);
            buf.writeVarInt(ammoCapacity);
            buf.writeVarInt(energy);
            buf.writeVarInt(energyCapacity);
        }

        public static Entry read(FriendlyByteBuf buf) {
            return new Entry(buf.readBlockPos(), buf.readById(BuiltInRegistries.ITEM), buf.readFloat(), buf.readFloat(),
                    buf.readEnum(TurretState.class), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
        }
    }

    private final BlockPos hubPos;
    // Server side.
    @Nullable
    private final HubInterfaceBlockEntity hub;
    @Nullable
    private final ServerPlayer player;
    /** Hash of each turret's targeting as last sent to this screen. */
    private final Map<BlockPos, Integer> sentFilters = new HashMap<>();
    private int sendTimer;
    // Client mirror of the last roster.
    private Header header = Header.LOADING;
    private List<Entry> turrets = List.of();
    private final Map<BlockPos, TargetFilter> filters = new HashMap<>();

    /** Client side: the Interface's position from the open data. */
    public ControlHubMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        super(BastionMenus.CONTROL_HUB.get(), id);
        hubPos = buf.readBlockPos();
        hub = null;
        player = null;
    }

    public ControlHubMenu(int id, Inventory inventory, HubInterfaceBlockEntity hub) {
        super(BastionMenus.CONTROL_HUB.get(), id);
        this.hubPos = hub.getBlockPos();
        this.hub = hub;
        this.player = inventory.player instanceof ServerPlayer serverPlayer ? serverPlayer : null;
    }

    public BlockPos hubPos() {
        return hubPos;
    }

    @Nullable
    public HubInterfaceBlockEntity hub() {
        return hub;
    }

    @Nullable
    public ServerPlayer player() {
        return player;
    }

    /** Server: what the screen gets now. */
    public HubRoster roster() {
        List<TurretBaseBlockEntity> all = hub.visibleTurrets(player);
        List<TurretBaseBlockEntity> shown = all.subList(0, Math.min(MAX_TURRETS, all.size()));
        Map<BlockPos, CompoundTag> changed = new HashMap<>();
        for (TurretBaseBlockEntity turret : shown) {
            if (changed.size() >= FILTERS_PER_SEND) break;
            CompoundTag tag = turret.filter().save();
            Integer hash = tag.hashCode();
            if (!hash.equals(sentFilters.get(turret.getBlockPos()))) {
                changed.put(turret.getBlockPos(), tag);
                sentFilters.put(turret.getBlockPos(), hash);
            }
        }
        Header head = new Header(hub.status(), hub.tier(), hub.energy(), hub.capacity(), hub.upkeep(), hub.side(), all.size(), hub.center());
        return new HubRoster(containerId, head, shown.stream().map(Entry::of).toList(), changed);
    }

    /** Called every tick for the open menu: a roster to the player every SEND_INTERVAL ticks, the first one at once. */
    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (hub == null || player == null || --sendTimer > 0) return;
        sendTimer = SEND_INTERVAL;
        BastionNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), roster());
    }

    /** Client: the server's latest roster. */
    public void apply(HubRoster roster) {
        header = roster.header();
        turrets = roster.turrets();
        roster.filters().forEach((pos, tag) -> filters.put(pos, TargetFilter.load(tag)));
    }

    public Header header() {
        return header;
    }

    public List<Entry> turrets() {
        return turrets;
    }

    /** Client: the targeting of the turret at {@code pos} as last received (null before it arrives). */
    @Nullable
    public TargetFilter filter(BlockPos pos) {
        return filters.get(pos);
    }

    /** The server closes it once the player cannot use the hub; the client never does on its own. */
    @Override
    public boolean stillValid(Player player) {
        return hub == null || hub.usableBy(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
```

`BastionMenus`: `public static final RegistryObject<MenuType<ControlHubMenu>> CONTROL_HUB = REGISTER.register("control_hub", () -> IForgeMenuType.create(ControlHubMenu::new));`

- [ ] **Step 5: The roster packet.** Create `src/main/java/dev/bastion/network/HubRoster.java`:

```java
package dev.bastion.network;

import dev.bastion.client.ClientPacketHandler;
import dev.bastion.menu.ControlHubMenu;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** S->C the Control Hub screen's data (PLAN "Turret Control Hub"): header, reachable turrets, changed targeting. */
public record HubRoster(int containerId, ControlHubMenu.Header header, List<ControlHubMenu.Entry> turrets, Map<BlockPos, CompoundTag> filters) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(containerId);
        header.write(buf);
        buf.writeCollection(turrets, (b, entry) -> entry.write(b));
        buf.writeMap(filters, FriendlyByteBuf::writeBlockPos, FriendlyByteBuf::writeNbt);
    }

    public static HubRoster decode(FriendlyByteBuf buf) {
        int id = buf.readVarInt();
        ControlHubMenu.Header header = ControlHubMenu.Header.read(buf);
        int count = buf.readVarInt();
        if (count > ControlHubMenu.MAX_TURRETS) throw new DecoderException("too many turrets: " + count);
        List<ControlHubMenu.Entry> turrets = new ArrayList<>(count);
        for (int i = 0; i < count; i++) turrets.add(ControlHubMenu.Entry.read(buf));
        return new HubRoster(id, header, turrets, buf.readMap(FriendlyByteBuf::readBlockPos, FriendlyByteBuf::readNbt));
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.hubRoster(this));
        context.get().setPacketHandled(true);
    }
}
```

`ClientPacketHandler` (imports `dev.bastion.menu.ControlHubMenu`, `dev.bastion.network.HubRoster`):

```java
    /** The Control Hub screen's data, for the hub menu it was sent to. */
    public static void hubRoster(HubRoster roster) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.containerMenu instanceof ControlHubMenu menu && menu.containerId == roster.containerId()) menu.apply(roster);
    }
```

`BastionNetwork`: `PROTOCOL = "10"`; at the end of `register()`:

```java
        CHANNEL.messageBuilder(HubRoster.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(HubRoster::encode).decoder(HubRoster::decode).consumerMainThread(HubRoster::handle).add();
```

Lang: en_us `"gui.bastion.control_hub.title": "Turret Control Hub"`; id_id the same.

- [ ] **Step 6: Run to verify it passes.** `./gradlew build --offline`, `./gradlew runGameTestServer --offline`. Expected: "All 141 required tests passed".

---

### Task 5: Turret GUIs through the hub

**Files:**
- Create: `src/main/java/dev/bastion/network/HubAction.java`
- Modify: `src/main/java/dev/bastion/menu/TurretMenu.java`, `menu/ControlHubMenu.java`, `network/TurretConfigUpdate.java`, `network/BastionNetwork.java`, `gametest/ControlHubTests.java`

**Interfaces:**
- Consumes: `HubInterfaceBlockEntity.reachable/usableBy/openMenu`, `ControlHubMenu.hub()/player()`.
- Produces: `TurretMenu(int, Inventory, TurretBaseBlockEntity, @Nullable BlockPos hub)`, `@Nullable BlockPos TurretMenu.hub()`, `TurretMenu.writeOpenData(FriendlyByteBuf, TurretBaseBlockEntity, @Nullable BlockPos hub)`; `boolean TurretConfigUpdate.apply(ServerPlayer)`, public `TurretConfigUpdate.MAX_TRUSTED` (32), `MAX_RULES` (128); `boolean ControlHubMenu.openTurret(BlockPos)`, `static void ControlHubMenu.reopen(ServerPlayer, BlockPos hubPos)`; packet `HubAction(Kind kind, List<BlockPos> turrets)` with `Kind {OPEN, BACK}`, `static open(BlockPos)`, `static back()` (Task 7 adds EDIT).

- [ ] **Step 1: Failing tests.** Add to `ControlHubTests` (imports `dev.bastion.menu.TurretMenu`, `dev.bastion.network.TurretConfigUpdate`):

```java
    /**
     * A turret 16 blocks out (a T2 reaches it) opens through the hub although the player is far from it; its GUI stays
     * usable only while the player stays within 8 blocks of the Interface, the hub is online and whole. A turret past the
     * reach is never reachable.
     */
    @GameTest(template = YARD, timeoutTicks = 80, batch = "remoteTurretGuiFollowsTheHub")
    public static void remoteTurretGuiFollowsTheHub(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        HubInterfaceBlockEntity hub = hub(helper, new BlockPos(5, 2, 14), 2);
        hub.setEnergy(100_000);
        FakePlayer player = player(helper, new BlockPos(5, 2, 12));
        BlockPos near = hub.center().offset(16, 0, 0), out = hub.center().offset(48, 0, 0);
        TurretBaseBlockEntity turret = base(level, near, player.getUUID());
        base(level, out, player.getUUID());
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(hub.online(), "not online");
            TurretMenu menu = new TurretMenu(0, player.getInventory(), turret, hub.getBlockPos());
            helper.assertTrue(player.distanceToSqr(Vec3.atCenterOf(near)) > 64 && menu.stillValid(player), "a turret in reach is not usable through the hub");
            helper.assertTrue(hub.reachable(player, out) == null, "a turret past the reach is reachable");
            player.setPos(player.getX() + 10, player.getY(), player.getZ());
            helper.assertTrue(!menu.stillValid(player), "still usable 10 blocks from the Interface");
            player.setPos(player.getX() - 10, player.getY(), player.getZ());
            hub.setEnergy(0);
        });
        helper.runAtTickTime(8, () -> {
            helper.assertTrue(hub.status() == Status.OFFLINE && !new TurretMenu(0, player.getInventory(), turret, hub.getBlockPos()).stillValid(player),
                    "usable while the hub is offline");
            hub.setEnergy(100_000);
        });
        helper.runAtTickTime(10, () -> {
            helper.assertTrue(new TurretMenu(0, player.getInventory(), turret, hub.getBlockPos()).stillValid(player), "not usable once online again");
            level.removeBlock(ControlHubStructure.slots(hub.getBlockPos(), Direction.NORTH).get(2), false);
        });
        helper.runAtTickTime(13, () -> {
            helper.assertTrue(!new TurretMenu(0, player.getInventory(), turret, hub.getBlockPos()).stillValid(player), "usable after a hub part was broken");
            level.removeBlock(near, false);
            level.removeBlock(out, false);
            helper.succeed();
        });
    }

    /** Settings for a far turret only get through while its GUI is open and valid, here through the hub. */
    @GameTest(template = YARD, timeoutTicks = 60, batch = "configUpdateNeedsAValidGui")
    public static void configUpdateNeedsAValidGui(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        HubInterfaceBlockEntity hub = hub(helper, new BlockPos(5, 2, 14), 2);
        hub.setEnergy(100_000);
        FakePlayer player = player(helper, new BlockPos(5, 2, 12));
        BlockPos at = hub.center().offset(16, 0, 0);
        TurretBaseBlockEntity turret = base(level, at, player.getUUID());
        helper.runAtTickTime(5, () -> {
            TargetFilter lowest = turret.filter().copy();
            lowest.priority = TargetFilter.Priority.LOWEST_HEALTH;
            TurretConfigUpdate update = new TurretConfigUpdate(at, lowest, true, false, false);
            helper.assertTrue(!update.apply(player), "applied from afar with no GUI open");
            player.containerMenu = new TurretMenu(0, player.getInventory(), turret, hub.getBlockPos());
            helper.assertTrue(update.apply(player) && turret.filter().priority == TargetFilter.Priority.LOWEST_HEALTH,
                    "not applied through the hub's GUI");
            player.containerMenu = player.inventoryMenu;
            level.removeBlock(at, false);
            helper.succeed();
        });
    }
```

- [ ] **Step 2: Run to verify it fails.** `./gradlew runGameTestServer --offline`. Expected: compile errors (`TurretMenu(…, BlockPos)`, `TurretConfigUpdate.apply` undefined).

- [ ] **Step 3: `TurretMenu` with a hub link.** In `TurretMenu` (imports `dev.bastion.hub.HubInterfaceBlockEntity`, `org.jetbrains.annotations.Nullable`):
  - field: `/** The Control Hub this GUI was opened through (null: opened at the turret). */ @Nullable private final BlockPos hub;`
  - `writeOpenData`:

```java
    public static void writeOpenData(FriendlyByteBuf buf, TurretBaseBlockEntity base) {
        writeOpenData(buf, base, null);
    }

    public static void writeOpenData(FriendlyByteBuf buf, TurretBaseBlockEntity base, @Nullable BlockPos hub) {
        buf.writeBlockPos(base.getBlockPos());
        buf.writeEnum(base.tier());
        buf.writeBoolean(base.large());
        base.filter().write(buf);
        buf.writeBoolean(base.enabled());
        buf.writeBoolean(base.redstoneInverted());
        buf.writeBoolean(base.salvo());
        buf.writeNullable(hub, FriendlyByteBuf::writeBlockPos);
    }
```

  - constructors:

```java
    public TurretMenu(int id, Inventory playerInventory, TurretBaseBlockEntity base) {
        this(id, playerInventory, base, null);
    }

    /** Server side; {@code hub}: the Control Hub it is opened through, or null at the turret itself. */
    public TurretMenu(int id, Inventory playerInventory, TurretBaseBlockEntity base, @Nullable BlockPos hub) {
        this(id, playerInventory, base.inventory(), base.getBlockPos(), base.tier(), base.filter().copy(), base.enabled(),
                base.redstoneInverted(), base.salvo(), serverData(base), hub);
    }

    private TurretMenu(int id, Inventory playerInventory, BlockPos pos, TurretTier tier, boolean large, FriendlyByteBuf buf) {
        this(id, playerInventory, new TurretInventory(() -> tier, () -> large, slot -> {}), pos, tier, TargetFilter.read(buf),
                buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), new SimpleContainerData(DATA_COUNT), buf.readNullable(FriendlyByteBuf::readBlockPos));
    }
```

  and the main private constructor gains a last parameter `@Nullable BlockPos hub` assigned to the field.
  - accessor and validity:

```java
    @Nullable
    public BlockPos hub() {
        return hub;
    }

    /** At the turret: within 8 blocks of it. Through a hub: the hub still reaches it for this player (PLAN "Aturan server"). */
    @Override
    public boolean stillValid(Player player) {
        if (hub == null) return stillValid(access, player, block);
        return player.level().getBlockEntity(hub) instanceof HubInterfaceBlockEntity interfaceEntity && interfaceEntity.reachable(player, pos) != null;
    }
```

- [ ] **Step 4: `TurretConfigUpdate`.** Make the caps public (`public static final int MAX_TRUSTED = 32, MAX_RULES = 128;`), drop `MAX_DISTANCE_SQR`, and replace `handle`:

```java
    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player != null) apply(player);
        context.get().setPacketHandled(true);
    }

    /**
     * Applies the settings when the sender has this turret's GUI open and can still use it, at the turret or through a
     * Control Hub (TurretMenu#stillValid); the reach check that used to live here is the GUI's own.
     */
    public boolean apply(ServerPlayer player) {
        if (!(player.containerMenu instanceof TurretMenu menu) || !menu.pos().equals(pos) || !menu.stillValid(player)) return false;
        if (!(player.level().getBlockEntity(pos) instanceof TurretBaseBlockEntity turret) || !turret.canConfigure(player)) return false;
        if (filter.trusted.size() > MAX_TRUSTED || filter.rules.size() > MAX_RULES) return false;
        turret.setFilter(filter);
        turret.setEnabled(enabled);
        turret.setRedstoneInverted(redstoneInverted);
        turret.setSalvo(salvo);
        return true;
    }
```

(import `dev.bastion.menu.TurretMenu`; update the class javadoc's last sentence to "The server checks that the sender's open turret GUI is still valid and owner/trusted access.").

- [ ] **Step 5: Open, back.** In `ControlHubMenu` (imports `dev.bastion.menu.TurretMenu` is the same package; `net.minecraft.world.SimpleMenuProvider`, `net.minecraftforge.network.NetworkHooks`):

```java
    /** Server: opens the turret's own GUI linked to this hub, if the player can reach it through the hub. */
    public boolean openTurret(BlockPos pos) {
        TurretBaseBlockEntity turret = hub == null || player == null ? null : hub.reachable(player, pos);
        if (turret == null) return false;
        BlockPos link = hub.getBlockPos();
        NetworkHooks.openScreen(player, new SimpleMenuProvider((id, inventory, p) -> new TurretMenu(id, inventory, turret, link), turret.getDisplayName()),
                buf -> TurretMenu.writeOpenData(buf, turret, link));
        return true;
    }

    /** Back from a turret opened through the hub: the hub's screen again if the player can still use it, else nothing open. */
    public static void reopen(ServerPlayer player, BlockPos hubPos) {
        if (player.level().getBlockEntity(hubPos) instanceof HubInterfaceBlockEntity hub && hub.usableBy(player)) hub.openMenu(player);
        else player.closeContainer();
    }
```

Create `src/main/java/dev/bastion/network/HubAction.java`:

```java
package dev.bastion.network;

import dev.bastion.menu.ControlHubMenu;
import dev.bastion.menu.TurretMenu;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * C->S from the Control Hub screen (PLAN "Turret Control Hub"): open a turret's GUI through the hub, or go back to the hub
 * from such a GUI. Only for the hub or turret GUI the sender has open, re-checked on the server.
 */
public record HubAction(Kind kind, List<BlockPos> turrets) {
    public enum Kind {OPEN, BACK}

    public static HubAction open(BlockPos turret) {
        return new HubAction(Kind.OPEN, List.of(turret));
    }

    public static HubAction back() {
        return new HubAction(Kind.BACK, List.of());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(kind);
        buf.writeCollection(turrets, FriendlyByteBuf::writeBlockPos);
    }

    public static HubAction decode(FriendlyByteBuf buf) {
        Kind kind = buf.readEnum(Kind.class);
        int count = buf.readVarInt();
        if (count > ControlHubMenu.MAX_TURRETS) throw new DecoderException("too many turrets: " + count);
        List<BlockPos> turrets = new ArrayList<>(count);
        for (int i = 0; i < count; i++) turrets.add(buf.readBlockPos());
        return new HubAction(kind, turrets);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player != null) {
            switch (kind) {
                case OPEN -> {
                    if (player.containerMenu instanceof ControlHubMenu menu && menu.stillValid(player) && !turrets.isEmpty()) menu.openTurret(turrets.get(0));
                }
                case BACK -> {
                    if (player.containerMenu instanceof TurretMenu menu && menu.hub() != null) ControlHubMenu.reopen(player, menu.hub());
                }
            }
        }
        context.get().setPacketHandled(true);
    }
}
```

`BastionNetwork.register()`, at the end:

```java
        CHANNEL.messageBuilder(HubAction.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(HubAction::encode).decoder(HubAction::decode).consumerMainThread(HubAction::handle).add();
```

- [ ] **Step 6: Run to verify it passes.** `./gradlew build --offline`, `./gradlew runGameTestServer --offline` (expected "All 143 required tests passed"; `menuShiftClickMarksTurretChanged` and every other turret GUI test unchanged), then `./gradlew runGameTestServer --offline -PwithCompatMods` (143).

---

### Task 6: The hub screen: Map and List tabs, "◀ Map" in the turret GUI

**Files:**
- Create: `src/main/java/dev/bastion/client/screen/ControlHubScreen.java`, `HubMap.java`, `HubSelection.java`
- Modify: `src/main/java/dev/bastion/client/screen/TurretScreen.java`, `client/BastionClient.java`, `tools/gen_textures.py`, `lang/en_us.json`, `lang/id_id.json`, `src/dev/java/dev/bastion/dev/ControlHubShowcase.java`

**Interfaces:**
- Consumes: `ControlHubMenu.header()/turrets()/hubPos()`, `ControlHubMenu.Entry`, `HubAction.open/back`, `TurretMenu.hub()`, `BastionSounds.CONTROL_HUB_UI_SELECT`.
- Produces: `ControlHubScreen` (package `client.screen`; Task 7 adds the Batch tab), `HubSelection.returning` (public) and package state `selected`, `hub`, `tab`, `zoom`, `sort`, `listScroll`, `viewX`, `viewZ`, `open(BlockPos)`; `HubMap.update(ClientLevel, int minX, int minZ, int size)`, `id()`, `close()`; `TurretScreen.stateColour(TurretState)` becomes package-private; texture `textures/gui/control_hub.png`.

GUI code is checked by the scene, not GameTests.

- [ ] **Step 1: Texture.** In `tools/gen_textures.py`, after `feed_hub_gui`:

```python
def control_hub_gui():
    """Control Hub screen (PLAN "Turret Control Hub"), a 256 x 200 panel; sprites under it: side tab (0, 200), active tab
    (24, 200), checkbox (48, 200) and ticked checkbox (56, 200), 8 x 8. ControlHubScreen draws the map, rows and bars."""
    img = np.zeros((256, 256, 4))
    panel(img, 256, 200)
    rect(img, 8, 16, 240, 1, UI_ACCENT * 0.55)  # under the title
    tab(img, 0, 200, active=False)
    tab(img, 24, 200, active=True)
    for x, ticked in ((48, False), (56, True)):
        rect(img, x, 200, 8, 8, UI_LIGHT)
        rect(img, x + 1, 201, 6, 6, UI_SLOT)
        if ticked:
            rect(img, x + 2, 202, 4, 4, UI_ACCENT)
    return img
```

and `"gui/control_hub.png": control_hub_gui,` in `TEXTURES`. Run `python3 tools/gen_textures.py`.

- [ ] **Step 2: Kept state and the terrain map.** Create `src/main/java/dev/bastion/client/screen/HubSelection.java`:

```java
package dev.bastion.client.screen;

import net.minecraft.core.BlockPos;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * What the Control Hub screen keeps while the player visits a turret's GUI through it and comes back (PLAN: selection,
 * tab, zoom and map position last for that trip, not past closing the hub screen).
 */
public final class HubSelection {
    /** Set by the turret GUI's "◀ Map" button: the next hub screen for the same hub keeps this state. */
    public static boolean returning;
    static final Set<BlockPos> selected = new LinkedHashSet<>();
    static BlockPos hub = BlockPos.ZERO;
    static int tab, zoom, sort, listScroll;
    /** Map view: blocks from the area's north-west corner to the view's. */
    static double viewX, viewZ;

    private HubSelection() {
    }

    /** A hub screen opens: coming back to the same hub keeps everything, anything else starts fresh. */
    static void open(BlockPos hubPos) {
        boolean keep = returning && hubPos.equals(hub);
        returning = false;
        if (keep) return;
        hub = hubPos;
        selected.clear();
        tab = zoom = sort = listScroll = 0;
        viewX = viewZ = 0;
    }
}
```

Create `src/main/java/dev/bastion/client/screen/HubMap.java`:

```java
package dev.bastion.client.screen;

import com.mojang.blaze3d.platform.NativeImage;
import dev.bastion.Bastion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import org.jetbrains.annotations.Nullable;

/**
 * The hub screen's terrain: one pixel per block column of the hub's area, coloured like a vanilla map (the top block's
 * map colour, lighter or darker as the ground rises or falls northward) from the chunks this client has loaded; dark where
 * it has none. Redrawn every 5 s while the screen is open.
 */
final class HubMap {
    private static final ResourceLocation ID = Bastion.id("control_hub_map");
    private static final int UNKNOWN = 0xFF141618; // ABGR, as NativeImage takes it
    private static final int REDRAW_TICKS = 100;
    @Nullable
    private DynamicTexture texture;
    private int size, minX, minZ;
    private long drawnAt = Long.MIN_VALUE;

    ResourceLocation id() {
        return ID;
    }

    void update(ClientLevel level, int minX, int minZ, int size) {
        boolean moved = texture == null || size != this.size || minX != this.minX || minZ != this.minZ;
        if (!moved && level.getGameTime() - drawnAt < REDRAW_TICKS) return;
        if (texture == null || size != this.size) {
            close();
            texture = new DynamicTexture(size, size, false);
            Minecraft.getInstance().getTextureManager().register(ID, texture);
        }
        this.size = size;
        this.minX = minX;
        this.minZ = minZ;
        drawnAt = level.getGameTime();
        NativeImage image = texture.getPixels();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = 0; x < size; x++) {
            int north = Integer.MIN_VALUE; // the column to the north, for the slope shading
            for (int z = 0; z < size; z++) {
                int wx = minX + x, wz = minZ + z;
                if (!level.hasChunk(wx >> 4, wz >> 4)) {
                    image.setPixelRGBA(x, z, UNKNOWN);
                    north = Integer.MIN_VALUE;
                    continue;
                }
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz) - 1;
                pos.set(wx, top, wz);
                MapColor colour = level.getBlockState(pos).getMapColor(level, pos);
                MapColor.Brightness light = north == Integer.MIN_VALUE || top == north ? MapColor.Brightness.NORMAL
                        : top > north ? MapColor.Brightness.HIGH : MapColor.Brightness.LOW;
                image.setPixelRGBA(x, z, colour == MapColor.NONE ? UNKNOWN : colour.calculateRGBColor(light));
                north = top;
            }
        }
        texture.upload();
    }

    void close() {
        if (texture != null) {
            Minecraft.getInstance().getTextureManager().release(ID);
            texture = null;
        }
    }
}
```

- [ ] **Step 3: The screen.** In `TurretScreen`, change `private static int stateColour(TurretState state)` to `static int stateColour(TurretState state)` (package-private: the hub list reuses the state lamp colours). Create `src/main/java/dev/bastion/client/screen/ControlHubScreen.java`:

```java
package dev.bastion.client.screen;

import dev.bastion.Bastion;
import dev.bastion.hub.HubInterfaceBlock.Status;
import dev.bastion.menu.ControlHubMenu;
import dev.bastion.menu.ControlHubMenu.Entry;
import dev.bastion.network.BastionNetwork;
import dev.bastion.network.HubAction;
import dev.bastion.registry.BastionItems;
import dev.bastion.registry.BastionSounds;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.TurretState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Turret Control Hub screen (PLAN "Turret Control Hub"): a header (satellite tier, reach, turret count, FE bar) and side
 * tabs like the turret GUI: Map (the hub's area from above, one dot per turret coloured by HP) and List (one row per
 * turret); Task 7 adds Batch settings. Clicking a turret opens its own GUI through the hub; selection, tab and map view
 * last for that trip (HubSelection). Everything shown comes from the server's roster, every half second.
 */
public class ControlHubScreen extends AbstractContainerScreen<ControlHubMenu> {
    private static final ResourceLocation TEXTURE = Bastion.id("textures/gui/control_hub.png");
    // Sprites under the 256 x 200 panel (tools/gen_textures.py control_hub_gui): side tab, active tab, checkbox off / on.
    private static final int SPRITE_V = 200, TAB_U = 0, TAB_ACTIVE_U = 24, CHECK_U = 48, CHECKED_U = 56;
    private static final int TEXT = 0xD8E2EC, MUTED = 0x7F8C9A, ACCENT = 0x9FC4E8, GREEN = 0x7CFF8A, YELLOW = 0xFFC21F, RED = 0xFF5A4F,
            DIM_RED = 0x6E100A, AMBER = 0xFFB347, GREY = 0x6B7480, CYAN = 0x4FD8FF;
    private static final int TAB_SIZE = 24, TAB_X = -23, TAB_Y = 8, TAB_GAP = 26;
    private static final int FE_X = 8, FE_Y = 18, FE_W = 240, FE_H = 6;
    // Map tab: the map square on the left, selection buttons, legend and hints on the right.
    private static final int MAP_X = 8, MAP_Y = 30, MAP_SIZE = 162, SIDE_X = 176, SIDE_W = 72, PICK = 4;
    // List tab: sort and selection buttons, then rows.
    private static final int LIST_Y = 46, ROW_H = 18, ROWS = 8;
    private static final int[] ZOOMS = {1, 2, 4};

    enum Tab {MAP, LIST}

    private final HubMap map = new HubMap();
    /** Map drags: a box selection (shift + left) or panning (right), started at (pressX, pressY). */
    private boolean boxing, panning;
    private double pressX, pressY;

    public ControlHubScreen(ControlHubMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = 256;
        imageHeight = 200;
        HubSelection.open(menu.hubPos());
    }

    private Tab tab() {
        return Tab.values()[Mth.clamp(HubSelection.tab, 0, Tab.values().length - 1)];
    }

    private boolean online() {
        return menu.header().status() == Status.ONLINE;
    }

    @Override
    protected void init() {
        super.init();
        if (tab() == Tab.MAP) {
            addRenderableWidget(new FlatButton(leftPos + SIDE_X, topPos + 44, SIDE_W, 12,
                    () -> Component.translatable("gui.bastion.control_hub.select_all"), this::selectAll));
            addRenderableWidget(new FlatButton(leftPos + SIDE_X, topPos + 58, SIDE_W, 12,
                    () -> Component.translatable("gui.bastion.control_hub.clear"), this::clearSelection));
        } else if (tab() == Tab.LIST) {
            String[] sorts = {"distance", "health", "ammo"};
            for (int i = 0; i < sorts.length; i++) {
                int sort = i;
                addRenderableWidget(new FlatButton(leftPos + 8 + i * 52, topPos + 30, 50, 12,
                        () -> Component.translatable("gui.bastion.control_hub.sort." + sorts[sort]), () -> {
                            HubSelection.sort = sort;
                            HubSelection.listScroll = 0;
                        }, () -> HubSelection.sort == sort));
            }
            addRenderableWidget(new FlatButton(leftPos + 166, topPos + 30, 40, 12,
                    () -> Component.translatable("gui.bastion.control_hub.select_all"), this::selectAll));
            addRenderableWidget(new FlatButton(leftPos + 208, topPos + 30, 40, 12,
                    () -> Component.translatable("gui.bastion.control_hub.clear"), this::clearSelection));
        }
    }

    /** Widgets only while online; turrets that left the reach leave the selection; the map keeps up. */
    @Override
    protected void containerTick() {
        super.containerTick();
        boolean online = online();
        for (var widget : renderables) {
            if (widget instanceof AbstractWidget w) w.visible = online;
        }
        if (!online || minecraft == null || minecraft.level == null) return;
        Set<BlockPos> present = new HashSet<>();
        for (Entry turret : menu.turrets()) present.add(turret.pos());
        HubSelection.selected.retainAll(present);
        map.update(minecraft.level, minX(), minZ(), blocks());
    }

    @Override
    public void removed() {
        super.removed();
        map.close();
    }

    private void switchTab(Tab next) {
        if (next == tab()) return;
        HubSelection.tab = next.ordinal();
        rebuildWidgets();
    }

    // --- rendering --------------------------------------------------------------------------------

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTabs(graphics, mouseX, mouseY);
        ControlHubMenu.Header h = menu.header();
        if (over(FE_X, FE_Y, FE_W, FE_H, mouseX, mouseY) && h.capacity() > 0) {
            graphics.renderTooltip(font, Component.translatable("gui.bastion.control_hub.fe", number(h.energy()), number(h.capacity()), number(h.upkeep())),
                    mouseX, mouseY);
        } else if (online() && tab() == Tab.MAP && overMap(mouseX, mouseY) && !boxing && !panning) {
            renderTurretTooltip(graphics, under(mouseX, mouseY), mouseX, mouseY);
        }
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        ControlHubMenu.Header h = menu.header();
        frame(graphics, leftPos + FE_X, topPos + FE_Y, FE_W, FE_H);
        int fill = h.capacity() == 0 ? 0 : (int) ((FE_W - 2) * Math.min(1, (double) h.energy() / h.capacity()));
        graphics.fill(leftPos + FE_X + 1, topPos + FE_Y + 1, leftPos + FE_X + 1 + fill, topPos + FE_Y + FE_H - 1, online() ? 0xFF86A8FF : 0xFFFF5A4F);
        if (online() && tab() == Tab.MAP) renderMap(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        ControlHubMenu.Header h = menu.header();
        Component summary = h.status() == Status.UNFORMED ? Component.translatable("gui.bastion.control_hub.connecting")
                : Component.translatable("gui.bastion.control_hub.summary", h.tier(), h.side(),
                h.total() > ControlHubMenu.MAX_TURRETS ? ControlHubMenu.MAX_TURRETS + "+" : String.valueOf(h.total()));
        int summaryW = Math.min(font.width(summary), 150);
        fit(graphics, title, 8, 6, imageWidth - 22 - summaryW, TEXT);
        fit(graphics, summary, imageWidth - 8 - summaryW, 6, summaryW, ACCENT);
        if (h.status() == Status.OFFLINE) {
            centered(graphics, Component.translatable("gui.bastion.control_hub.offline"), 96, RED);
            centered(graphics, Component.translatable("gui.bastion.control_hub.offline.hint"), 110, MUTED);
            return;
        }
        if (!online()) return;
        if (tab() == Tab.MAP) renderMapLabels(graphics);
        else renderListLabels(graphics, mouseX - leftPos, mouseY - topPos);
    }

    private void renderTabs(GuiGraphics graphics, int mouseX, int mouseY) {
        ItemStack[] icons = {new ItemStack(BastionItems.SATELLITE_UPLINK_T3.get()), new ItemStack(BastionItems.TURRET_BASE.get()),
                new ItemStack(BastionItems.TURRET_CONFIGURATOR.get())};
        for (Tab t : Tab.values()) {
            int x = leftPos + TAB_X, y = topPos + TAB_Y + t.ordinal() * TAB_GAP;
            graphics.blit(TEXTURE, x, y, t == tab() ? TAB_ACTIVE_U : TAB_U, SPRITE_V, TAB_SIZE, TAB_SIZE);
            graphics.renderItem(icons[t.ordinal()], x + 5, y + 4);
            if (overTab(t, mouseX, mouseY)) {
                graphics.renderTooltip(font, Component.translatable("gui.bastion.control_hub.tab." + t.name().toLowerCase(Locale.ROOT)), mouseX, mouseY);
            }
        }
    }

    // --- map --------------------------------------------------------------------------------------

    private int blocks() {
        return menu.header().side() * 16;
    }

    private int minX() {
        return ((menu.header().center().getX() >> 4) - menu.header().side() / 2) * 16;
    }

    private int minZ() {
        return ((menu.header().center().getZ() >> 4) - menu.header().side() / 2) * 16;
    }

    private int zoom() {
        return ZOOMS[Mth.clamp(HubSelection.zoom, 0, ZOOMS.length - 1)];
    }

    /** Blocks across the view. */
    private double visible() {
        return blocks() / (double) zoom();
    }

    /** Screen pixels per block. */
    private double scale() {
        return MAP_SIZE / visible();
    }

    private void clampView() {
        double max = blocks() - visible();
        HubSelection.viewX = Mth.clamp(HubSelection.viewX, 0, max);
        HubSelection.viewZ = Mth.clamp(HubSelection.viewZ, 0, max);
    }

    private double screenX(double worldX) {
        return leftPos + MAP_X + (worldX - minX() - HubSelection.viewX) * scale();
    }

    private double screenY(double worldZ) {
        return topPos + MAP_Y + (worldZ - minZ() - HubSelection.viewZ) * scale();
    }

    private boolean overMap(double mouseX, double mouseY) {
        return over(MAP_X, MAP_Y, MAP_SIZE, MAP_SIZE, mouseX, mouseY);
    }

    private void renderMap(GuiGraphics graphics, int mouseX, int mouseY) {
        int x0 = leftPos + MAP_X, y0 = topPos + MAP_Y;
        frame(graphics, x0 - 1, y0 - 1, MAP_SIZE + 2, MAP_SIZE + 2);
        clampView();
        int visible = (int) visible();
        graphics.blit(map.id(), x0, y0, MAP_SIZE, MAP_SIZE, (float) HubSelection.viewX, (float) HubSelection.viewZ, visible, visible, blocks(), blocks());
        graphics.enableScissor(x0, y0, x0 + MAP_SIZE, y0 + MAP_SIZE);
        for (int k = 1; k < menu.header().side(); k++) { // chunk borders
            int sx = (int) Math.round(screenX(minX() + 16 * k)), sy = (int) Math.round(screenY(minZ() + 16 * k));
            graphics.fill(sx, y0, sx + 1, y0 + MAP_SIZE, 0x30FFFFFF);
            graphics.fill(x0, sy, x0 + MAP_SIZE, sy + 1, 0x30FFFFFF);
        }
        BlockPos center = menu.header().center(); // the hub: a cyan square with a dark heart
        int hx = (int) screenX(center.getX() + 0.5), hy = (int) screenY(center.getZ() + 0.5);
        graphics.fill(hx - 4, hy - 4, hx + 5, hy + 5, 0xFF0B1418);
        graphics.fill(hx - 3, hy - 3, hx + 4, hy + 4, 0xFF000000 | CYAN);
        graphics.fill(hx - 1, hy - 1, hx + 2, hy + 2, 0xFF0B1418);
        long time = minecraft.level == null ? 0 : minecraft.level.getGameTime();
        for (Entry turret : menu.turrets()) {
            int x = (int) screenX(turret.pos().getX() + 0.5), y = (int) screenY(turret.pos().getZ() + 0.5);
            if (HubSelection.selected.contains(turret.pos())) graphics.fill(x - 4, y - 4, x + 5, y + 5, 0xFFFFFFFF); // white ring
            graphics.fill(x - 3, y - 3, x + 4, y + 4, 0xFF0B1418);
            graphics.fill(x - 2, y - 2, x + 3, y + 3, 0xFF000000 | dotColour(turret, time));
            if (turret.state() == TurretState.NO_AMMO && time % 20 < 14) { // "!" beside the dot
                graphics.fill(x + 4, y - 5, x + 5, y - 1, 0xFF000000 | AMBER);
                graphics.fill(x + 4, y, x + 5, y + 1, 0xFF000000 | AMBER);
            }
        }
        if (boxing) {
            graphics.fill((int) Math.min(pressX, mouseX), (int) Math.min(pressY, mouseY), (int) Math.max(pressX, mouseX),
                    (int) Math.max(pressY, mouseY), 0x304FD8FF);
        }
        graphics.disableScissor();
    }

    /** HP colour like the base's lights (PLAN 4.1); grey while switched off, unarmed or redstone-off. */
    private static int dotColour(Entry turret, long time) {
        if (turret.state() == TurretState.DISABLED) return GREY;
        float hp = turret.healthFraction();
        if (hp < TurretBaseBlockEntity.CRITICAL_FRACTION) return time % 10 < 5 ? RED : DIM_RED;
        return hp < TurretBaseBlockEntity.DAMAGED_FRACTION ? YELLOW : GREEN;
    }

    /** Turrets whose dot is under the cursor, highest first (turrets stacked in one column share a dot). */
    private List<Entry> under(double mouseX, double mouseY) {
        List<Entry> found = new ArrayList<>();
        for (Entry turret : menu.turrets()) {
            if (Math.abs(screenX(turret.pos().getX() + 0.5) - mouseX) <= PICK && Math.abs(screenY(turret.pos().getZ() + 0.5) - mouseY) <= PICK) found.add(turret);
        }
        found.sort(Comparator.comparingInt((Entry turret) -> turret.pos().getY()).reversed());
        return found;
    }

    private void renderMapLabels(GuiGraphics graphics) {
        fit(graphics, Component.translatable("gui.bastion.control_hub.selected", HubSelection.selected.size()), SIDE_X, 32, SIDE_W, TEXT);
        String[] legend = {"healthy", "damaged", "critical", "off", "no_ammo", "selected"};
        int[] colours = {GREEN, YELLOW, RED, GREY, AMBER, 0xFFFFFF};
        for (int i = 0; i < legend.length; i++) {
            int y = 76 + i * 11;
            graphics.fill(SIDE_X, y + 1, SIDE_X + 5, y + 6, 0xFF000000 | colours[i]);
            fit(graphics, Component.translatable("gui.bastion.control_hub.legend." + legend[i]), SIDE_X + 8, y, SIDE_W - 8, MUTED);
        }
        List<FormattedCharSequence> hint = font.split(Component.translatable("gui.bastion.control_hub.hint"), SIDE_W);
        for (int i = 0; i < Math.min(4, hint.size()); i++) graphics.drawString(font, hint.get(i), SIDE_X, 148 + i * 10, MUTED, false);
    }

    private void renderTurretTooltip(GuiGraphics graphics, List<Entry> turrets, int mouseX, int mouseY) {
        if (turrets.isEmpty()) return;
        List<Component> lines = new ArrayList<>();
        for (int i = 0; i < Math.min(4, turrets.size()); i++) {
            if (i > 0) lines.add(Component.empty());
            lines.addAll(describe(turrets.get(i)));
        }
        if (turrets.size() > 4) lines.add(Component.translatable("gui.bastion.control_hub.more", turrets.size() - 4).withStyle(ChatFormatting.DARK_GRAY));
        graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    /** Name, position, HP, ammo or FE, state: one turret's tooltip. */
    private static List<Component> describe(Entry turret) {
        BlockPos p = turret.pos();
        Component supply = turret.energyWeapon() ? Component.translatable("gui.bastion.control_hub.energy", Math.round(turret.fill() * 100))
                : Component.translatable("gui.bastion.control_hub.ammo", turret.ammo());
        return List.of(name(turret),
                Component.translatable("gui.bastion.control_hub.pos", p.getX(), p.getY(), p.getZ()).withStyle(ChatFormatting.GRAY),
                Component.translatable("gui.bastion.control_hub.hp", Math.round(turret.health()), Math.round(turret.maxHealth())).withStyle(ChatFormatting.GRAY),
                supply.copy().withStyle(ChatFormatting.GRAY),
                Component.translatable("gui.bastion.state." + turret.state().name().toLowerCase(Locale.ROOT)).withStyle(ChatFormatting.DARK_AQUA));
    }

    private static Component name(Entry turret) {
        return turret.weapon() == Items.AIR ? Component.translatable("gui.bastion.control_hub.unarmed") : new ItemStack(turret.weapon()).getHoverName();
    }

    // --- list -------------------------------------------------------------------------------------

    private List<Entry> sorted() {
        BlockPos center = menu.header().center();
        Comparator<Entry> order = switch (HubSelection.sort) {
            case 1 -> Comparator.comparingDouble(Entry::healthFraction);
            case 2 -> Comparator.comparingDouble(Entry::fill);
            default -> Comparator.comparingDouble(turret -> turret.pos().distSqr(center));
        };
        return menu.turrets().stream().sorted(order).toList();
    }

    /** Rows in screen-relative coordinates (renderLabels runs with the pose moved to the panel). */
    private void renderListLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        List<Entry> rows = sorted();
        if (rows.isEmpty()) {
            centered(graphics, Component.translatable("gui.bastion.control_hub.none"), 100, MUTED);
            return;
        }
        HubSelection.listScroll = Mth.clamp(HubSelection.listScroll, 0, Math.max(0, rows.size() - ROWS));
        long time = minecraft.level == null ? 0 : minecraft.level.getGameTime();
        for (int i = 0; i < ROWS && i + HubSelection.listScroll < rows.size(); i++) {
            Entry turret = rows.get(i + HubSelection.listScroll);
            int y = LIST_Y + i * ROW_H;
            if (mouseY >= y && mouseY < y + ROW_H && mouseX >= 8 && mouseX < 248) graphics.fill(8, y, 248, y + ROW_H, 0x18FFFFFF);
            graphics.blit(TEXTURE, 10, y + 5, HubSelection.selected.contains(turret.pos()) ? CHECKED_U : CHECK_U, SPRITE_V, 8, 8);
            if (turret.weapon() != Items.AIR) graphics.renderItem(new ItemStack(turret.weapon()), 22, y + 1);
            fit(graphics, name(turret), 42, y + 5, 62, TEXT);
            BlockPos p = turret.pos();
            fit(graphics, Component.translatable("gui.bastion.control_hub.pos", p.getX(), p.getY(), p.getZ()), 108, y + 5, 62, MUTED);
            frame(graphics, 174, y + 7, 30, 4);
            graphics.fill(175, y + 8, 175 + Math.round(28 * Mth.clamp(turret.healthFraction(), 0, 1)), y + 10, 0xFF000000 | dotColour(turret, time));
            Component supply = Component.literal(turret.energyWeapon() ? Math.round(turret.fill() * 100) + "%" : String.valueOf(turret.ammo()));
            fit(graphics, supply, 208, y + 5, 28, turret.state() == TurretState.NO_AMMO ? AMBER : TEXT);
            graphics.fill(239, y + 6, 245, y + 12, 0xFF000000 | TurretScreen.stateColour(turret.state()));
        }
        if (rows.size() > ROWS) { // slim scroll indicator
            int track = ROWS * ROW_H, knob = Math.max(8, track * ROWS / rows.size());
            int knobY = LIST_Y + (track - knob) * HubSelection.listScroll / (rows.size() - ROWS);
            graphics.fill(249, knobY, 251, knobY + knob, 0xFF000000 | ACCENT);
        }
    }

    // --- input ------------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (Tab t : Tab.values()) {
            if (overTab(t, mouseX, mouseY)) {
                switchTab(t);
                return true;
            }
        }
        if (online() && tab() == Tab.MAP && overMap(mouseX, mouseY)) {
            pressX = mouseX;
            pressY = mouseY;
            if (button == 1) {
                panning = true;
            } else if (button == 0 && hasShiftDown()) {
                boxing = true;
            } else if (button == 0) {
                List<Entry> hit = under(mouseX, mouseY);
                if (!hit.isEmpty()) open(hit.get(0));
            }
            return true;
        }
        if (online() && tab() == Tab.LIST && button == 0 && clickRow(mouseX - leftPos, mouseY - topPos)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (panning) {
            HubSelection.viewX -= dragX / scale();
            HubSelection.viewZ -= dragY / scale();
            clampView();
            return true;
        }
        return boxing || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (boxing && button == 0) {
            boxing = false;
            if (Math.abs(mouseX - pressX) < 3 && Math.abs(mouseY - pressY) < 3) { // a shift-click, not a box
                List<Entry> hit = under(mouseX, mouseY);
                if (!hit.isEmpty()) toggle(hit.get(0).pos());
                return true;
            }
            double x0 = Math.min(pressX, mouseX), x1 = Math.max(pressX, mouseX), y0 = Math.min(pressY, mouseY), y1 = Math.max(pressY, mouseY);
            for (Entry turret : menu.turrets()) {
                double x = screenX(turret.pos().getX() + 0.5), y = screenY(turret.pos().getZ() + 0.5);
                if (x >= x0 && x <= x1 && y >= y0 && y <= y1) HubSelection.selected.add(turret.pos());
            }
            click();
            return true;
        }
        if (panning && button == 1) {
            panning = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /** Map: zoom 1x / 2x / 4x round the block under the cursor. List: scroll. */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (online() && tab() == Tab.MAP && overMap(mouseX, mouseY)) {
            double px = mouseX - leftPos - MAP_X, pz = mouseY - topPos - MAP_Y;
            double atX = HubSelection.viewX + px / scale(), atZ = HubSelection.viewZ + pz / scale();
            HubSelection.zoom = Mth.clamp(HubSelection.zoom + (delta > 0 ? 1 : -1), 0, ZOOMS.length - 1);
            HubSelection.viewX = atX - px / scale();
            HubSelection.viewZ = atZ - pz / scale();
            clampView();
            return true;
        }
        if (online() && tab() == Tab.LIST) {
            HubSelection.listScroll = Math.max(0, HubSelection.listScroll + (delta > 0 ? -1 : 1));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    /** List: the checkbox toggles the selection, the rest of the row opens the turret. */
    private boolean clickRow(double x, double y) {
        if (y < LIST_Y || y >= LIST_Y + ROWS * ROW_H || x < 8 || x >= 248) return false;
        List<Entry> rows = sorted();
        int index = (int) (y - LIST_Y) / ROW_H + HubSelection.listScroll;
        if (index >= rows.size()) return false;
        if (x < 20) toggle(rows.get(index).pos());
        else open(rows.get(index));
        return true;
    }

    /** Clicking a side tab must not count as clicking outside the window. */
    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top, int button) {
        for (Tab t : Tab.values()) {
            if (overTab(t, mouseX, mouseY)) return false;
        }
        return super.hasClickedOutside(mouseX, mouseY, left, top, button);
    }

    private void toggle(BlockPos pos) {
        if (!HubSelection.selected.remove(pos)) HubSelection.selected.add(pos);
        click();
    }

    private void selectAll() {
        for (Entry turret : menu.turrets()) HubSelection.selected.add(turret.pos());
        click();
    }

    private void clearSelection() {
        HubSelection.selected.clear();
        click();
    }

    /** Asks the server to open the turret's own GUI through the hub; this screen's state waits in HubSelection. */
    private void open(Entry turret) {
        click();
        BastionNetwork.CHANNEL.sendToServer(HubAction.open(turret.pos()));
    }

    private void click() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(BastionSounds.CONTROL_HUB_UI_SELECT.get(), 1f));
    }

    // --- helpers ----------------------------------------------------------------------------------

    private boolean over(int x, int y, int w, int h, double mouseX, double mouseY) {
        return mouseX >= leftPos + x && mouseX < leftPos + x + w && mouseY >= topPos + y && mouseY < topPos + y + h;
    }

    private boolean overTab(Tab t, double mouseX, double mouseY) {
        int x = leftPos + TAB_X, y = topPos + TAB_Y + t.ordinal() * TAB_GAP;
        return mouseX >= x && mouseX < x + TAB_SIZE - 2 && mouseY >= y && mouseY < y + TAB_SIZE;
    }

    /** A frame at (x, y) in the current pose's coordinates. */
    private static void frame(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, FlatButton.EDGE);
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, FlatButton.DARK);
    }

    private void centered(GuiGraphics graphics, Component text, int y, int colour) {
        int width = Math.min(font.width(text), imageWidth - 16);
        fit(graphics, text, (imageWidth - width) / 2, y, imageWidth - 16, colour);
    }

    private static String number(int value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    /** Text scaled down (never up) to fit maxWidth, as in TurretScreen. */
    private void fit(GuiGraphics graphics, Component text, int x, int y, int maxWidth, int colour) {
        int width = font.width(text);
        if (width <= maxWidth) {
            graphics.drawString(font, text, x, y, colour, false);
            return;
        }
        float scale = maxWidth / (float) width;
        graphics.pose().pushPose();
        graphics.pose().translate(x, y + (1 - scale) * 4, 0);
        graphics.pose().scale(scale, scale, 1);
        graphics.drawString(font, text, 0, 0, colour, false);
        graphics.pose().popPose();
    }
}
```

`BastionClient.onClientSetup`: `MenuScreens.register(BastionMenus.CONTROL_HUB.get(), ControlHubScreen::new);`

- [ ] **Step 4: "◀ Map" in the turret GUI.** In `TurretScreen.init()`, after the on/off button (imports `dev.bastion.network.HubAction`):

```java
        if (menu.hub() != null) { // opened through a Control Hub: back to its screen
            addRenderableWidget(new FlatButton(leftPos + imageWidth - 74, topPos + 5, 34, 11, () -> Component.translatable("gui.bastion.turret.back"), () -> {
                HubSelection.returning = true;
                BastionNetwork.CHANNEL.sendToServer(HubAction.back());
            }));
        }
```

and in `renderLabels` replace `int tierX = imageWidth - 42 - font.width(tier);` with `int tierX = (menu.hub() != null ? imageWidth - 78 : imageWidth - 42) - font.width(tier);`.

- [ ] **Step 5: Strings.** `lang/en_us.json`:

```json
  "gui.bastion.control_hub.summary": "Satellite T%1$s · %2$sx%2$s chunks · %3$s turrets",
  "gui.bastion.control_hub.connecting": "Connecting…",
  "gui.bastion.control_hub.offline": "OFFLINE: out of FE",
  "gui.bastion.control_hub.offline.hint": "Feed FE into a Hub Power Port",
  "gui.bastion.control_hub.fe": "%s / %s FE · uses %s FE/t",
  "gui.bastion.control_hub.tab.map": "Map",
  "gui.bastion.control_hub.tab.list": "List",
  "gui.bastion.control_hub.selected": "%s selected",
  "gui.bastion.control_hub.select_all": "Select all",
  "gui.bastion.control_hub.clear": "Clear",
  "gui.bastion.control_hub.sort.distance": "Distance",
  "gui.bastion.control_hub.sort.health": "HP",
  "gui.bastion.control_hub.sort.ammo": "Ammo",
  "gui.bastion.control_hub.none": "No turrets in reach that you may open",
  "gui.bastion.control_hub.legend.healthy": "HP 50% or more",
  "gui.bastion.control_hub.legend.damaged": "HP 25-50%",
  "gui.bastion.control_hub.legend.critical": "HP below 25%",
  "gui.bastion.control_hub.legend.off": "Off / no weapon",
  "gui.bastion.control_hub.legend.no_ammo": "No ammo / FE",
  "gui.bastion.control_hub.legend.selected": "Selected",
  "gui.bastion.control_hub.hint": "Click: open. Shift: select, drag a box. Wheel: zoom. Right-drag: move",
  "gui.bastion.control_hub.unarmed": "No weapon",
  "gui.bastion.control_hub.pos": "%s, %s, %s",
  "gui.bastion.control_hub.hp": "HP %s / %s",
  "gui.bastion.control_hub.ammo": "Ammo: %s",
  "gui.bastion.control_hub.energy": "FE: %s%%",
  "gui.bastion.control_hub.more": "…and %s more",
  "gui.bastion.turret.back": "◀ Map",
```

`lang/id_id.json`:

```json
  "gui.bastion.control_hub.summary": "Satelit T%1$s · %2$sx%2$s chunk · %3$s turret",
  "gui.bastion.control_hub.connecting": "Menghubungkan…",
  "gui.bastion.control_hub.offline": "OFFLINE: FE habis",
  "gui.bastion.control_hub.offline.hint": "Isi FE lewat Port Daya Hub",
  "gui.bastion.control_hub.fe": "%s / %s FE · pakai %s FE/t",
  "gui.bastion.control_hub.tab.map": "Peta",
  "gui.bastion.control_hub.tab.list": "Daftar",
  "gui.bastion.control_hub.selected": "%s terpilih",
  "gui.bastion.control_hub.select_all": "Pilih semua",
  "gui.bastion.control_hub.clear": "Kosongkan",
  "gui.bastion.control_hub.sort.distance": "Jarak",
  "gui.bastion.control_hub.sort.health": "HP",
  "gui.bastion.control_hub.sort.ammo": "Amunisi",
  "gui.bastion.control_hub.none": "Tidak ada turret dalam jangkauan yang boleh kamu buka",
  "gui.bastion.control_hub.legend.healthy": "HP 50% ke atas",
  "gui.bastion.control_hub.legend.damaged": "HP 25-50%",
  "gui.bastion.control_hub.legend.critical": "HP di bawah 25%",
  "gui.bastion.control_hub.legend.off": "Mati / tanpa senjata",
  "gui.bastion.control_hub.legend.no_ammo": "Amunisi / FE habis",
  "gui.bastion.control_hub.legend.selected": "Terpilih",
  "gui.bastion.control_hub.hint": "Klik: buka. Shift: pilih, seret kotak. Roda: zoom. Seret kanan: geser",
  "gui.bastion.control_hub.unarmed": "Tanpa senjata",
  "gui.bastion.control_hub.pos": "%s, %s, %s",
  "gui.bastion.control_hub.hp": "HP %s / %s",
  "gui.bastion.control_hub.ammo": "Amunisi: %s",
  "gui.bastion.control_hub.energy": "FE: %s%%",
  "gui.bastion.control_hub.more": "…dan %s lagi",
  "gui.bastion.turret.back": "◀ Peta",
```

- [ ] **Step 6: Screen shots in the scene.** In `ControlHubShowcase.onClientTick`, replace `if (tick == 130) mc.stop(); // Task 6 moves this past its screen shots` with (imports `dev.bastion.client.screen.HubSelection`, `dev.bastion.network.BastionNetwork`, `dev.bastion.network.HubAction`, `net.minecraft.client.gui.screens.Screen`, `net.minecraft.world.InteractionHand`, `net.minecraft.world.phys.BlockHitResult`, `net.minecraft.world.phys.HitResult`):

```java
        Vec3 t3 = Vec3.atCenterOf(T3_IFACE), t2 = Vec3.atCenterOf(T2_IFACE);
        if (tick == 116) {
            mc.options.hideGui = false;
            Showcase.camera(server, t3.add(0, 0.3, 2.2), t3);
        }
        if (tick == 124) use(mc);
        if (tick == 140) Showcase.shot(mc, "hub_gui_map");
        if (tick == 142 && mc.screen != null) clickTab(mc.screen, 1);
        if (tick == 150) Showcase.shot(mc, "hub_gui_list");
        if (tick == 152 && mc.screen != null) {
            clickTab(mc.screen, 0);
            mc.screen.mouseScrolled(mc.screen.width / 2.0 - 128 + 8 + 81, mc.screen.height / 2.0 - 100 + 30 + 81, 1); // zoom on the map's middle
        }
        if (tick == 158) Showcase.shot(mc, "hub_gui_zoom");
        if (tick == 160) BastionNetwork.CHANNEL.sendToServer(HubAction.open(T3_IFACE.offset(-4, 0, 6)));
        if (tick == 172) Showcase.shot(mc, "hub_gui_turret");
        if (tick == 174) {
            HubSelection.returning = true;
            BastionNetwork.CHANNEL.sendToServer(HubAction.back());
        }
        if (tick == 184) Showcase.shot(mc, "hub_gui_back");
        // ticks 186-198: Task 7's batch settings shot
        if (tick == 200) {
            mc.setScreen(null);
            Showcase.server(server, s -> {
                ServerLevel level = s.overworld();
                level.removeBlock(source(T2_IFACE), false);
                if (level.getBlockEntity(T2_IFACE) instanceof HubInterfaceBlockEntity hub) hub.setEnergy(0);
            });
            Showcase.camera(server, t2.add(0, 0.3, 2.2), t2);
        }
        if (tick == 210) use(mc);
        if (tick == 224) Showcase.shot(mc, "hub_gui_offline");
        if (tick == 226) mc.setScreen(null);
        if (tick == 232) mc.stop();
```

and add:

```java
    /** Right-clicks the block under the crosshair, as the player would. */
    private static void use(Minecraft mc) {
        if (mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        }
    }

    /** The {@code index}th side tab left of the 256 x 200 panel (ControlHubScreen TAB_X -23, TAB_Y 8, TAB_GAP 26). */
    static void clickTab(Screen screen, int index) {
        int left = (screen.width - 256) / 2, top = (screen.height - 200) / 2;
        screen.mouseClicked(left - 23 + 10, top + 8 + index * 26 + 10, 0);
    }

    /** The Creative Power Source beside a scene hub's Power Port (see hub()). */
    static BlockPos source(BlockPos iface) {
        return ControlHubStructure.slots(iface, Direction.SOUTH).get(0).west();
    }
```

(and use `source(iface)` in `hub()` for the power source position.)

- [ ] **Step 7: Run.** `./gradlew build --offline`; `./gradlew runGameTestServer --offline` (still "All 143 required tests passed"); `./gradlew runClient --offline -Pshowcase=hub -PshowcaseWorld=showcase_fx`. Read `showcase_hub_gui_map.png`, `_list`, `_zoom`, `_turret`, `_back`, `_offline` and check: header "Satellite T3 · 7x7 chunks · 6 turrets" and a full FE bar; the terrain map with chunk lines, the cyan hub square, green / yellow / blinking red / grey dots and an orange "!" by the out-of-ammo Shotgun; legend and hints readable (no text runs into another); the list with icons, names, coordinates, HP bars, ammo and lamps; zoom shows bigger blocks round the middle; the Gun turret's own GUI with "◀ Map" left of the on/off button and the title not overlapping it; back on the Map tab after "◀ Map"; the T2 hub OFFLINE: red bar and "OFFLINE: out of FE". Fix layout issues, re-run.

---

## Stage 3: batch settings, docs

### Task 7: Batch settings

**Files:**
- Create: `src/main/java/dev/bastion/hub/HubEdit.java`, `hub/FilterMix.java`, `src/main/java/dev/bastion/client/screen/MobList.java`
- Modify: `src/main/java/dev/bastion/network/HubAction.java`, `menu/ControlHubMenu.java`, `client/screen/ControlHubScreen.java`, `client/screen/HubSelection.java`, `client/screen/TurretScreen.java`, `gametest/ControlHubTests.java`, `lang/en_us.json`, `lang/id_id.json`, `src/dev/java/dev/bastion/dev/ControlHubShowcase.java`

**Interfaces:**
- Consumes: `TurretConfigUpdate.MAX_TRUSTED/MAX_RULES`, `HubInterfaceBlockEntity.reachable`, `ControlHubMenu.filter(BlockPos)`, `HubSelection.selected`.
- Produces: `record HubEdit(Kind kind, String key, int value)` with `Kind {CATEGORY, PLAYER_MODE, PRIORITY, TRUSTED_ADD, TRUSTED_REMOVE, RULE, ENABLED}`, factories `category(Category, boolean)`, `playerMode(PlayerMode)`, `priority(Priority)`, `trust(String, boolean add)`, `rule(ResourceLocation, @Nullable Rule)`, `enabled(boolean)`, `boolean valid()`, `boolean applyTo(TargetFilter)`, `boolean apply(TurretBaseBlockEntity)`, `write(FriendlyByteBuf)`, `static read(FriendlyByteBuf)`; `FilterMix(Collection<TargetFilter>)` with `size()`, `@Nullable Boolean category(Category)`, `@Nullable PlayerMode playerMode()`, `@Nullable Priority priority()`, `Map<String, Integer> trusted()`, `List<ResourceLocation> ruleIds()`, `int ruleCount(ResourceLocation)`, `@Nullable Rule rule(ResourceLocation)`; `int ControlHubMenu.edit(Collection<BlockPos>, HubEdit)`; `HubAction(Kind kind, List<BlockPos> turrets, @Nullable HubEdit edit)` with `Kind.EDIT` and `static edit(Collection<BlockPos>, HubEdit)`; `public static void HubSelection.select(Collection<BlockPos>)`.

- [ ] **Step 1: Failing tests.** Add to `ControlHubTests` (imports `dev.bastion.hub.FilterMix`, `dev.bastion.hub.HubEdit`, `dev.bastion.network.HubAction`, `io.netty.buffer.Unpooled`, `io.netty.handler.codec.DecoderException`, `net.minecraft.network.FriendlyByteBuf`, `java.util.ArrayList`, `java.util.Map`):

```java
    /** One batch change reaches every selected turret and nothing else of their settings; one past the reach is skipped. */
    @GameTest(template = YARD, timeoutTicks = 60, batch = "batchEditChangesOnlyThatSetting")
    public static void batchEditChangesOnlyThatSetting(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        HubInterfaceBlockEntity hub = hub(helper, new BlockPos(15, 2, 14), 2);
        hub.setEnergy(100_000);
        FakePlayer player = player(helper, new BlockPos(15, 2, 12));
        BlockPos center = hub.center();
        List<TurretBaseBlockEntity> turrets = List.of(base(level, center.offset(-3, 0, 4), player.getUUID()),
                base(level, center.offset(0, 0, 4), player.getUUID()), base(level, center.offset(3, 0, 4), player.getUUID()));
        ResourceLocation zombie = new ResourceLocation("minecraft:zombie"), creeper = new ResourceLocation("minecraft:creeper");
        TargetFilter zombies = turrets.get(0).filter().copy();
        zombies.rules.put(zombie, TargetFilter.Rule.ALWAYS);
        turrets.get(0).setFilter(zombies);
        TargetFilter alex = turrets.get(1).filter().copy();
        alex.trusted.add("alex");
        turrets.get(1).setFilter(alex);
        BlockPos far = center.offset(80, 0, 0); // past a T2's reach
        TurretBaseBlockEntity outside = base(level, far, player.getUUID());
        helper.runAtTickTime(5, () -> {
            ControlHubMenu menu = new ControlHubMenu(0, player.getInventory(), hub);
            List<BlockPos> positions = new ArrayList<>(turrets.stream().map(TurretBaseBlockEntity::getBlockPos).toList());
            positions.add(far);
            int changed = menu.edit(positions, HubEdit.priority(TargetFilter.Priority.LOWEST_HEALTH));
            helper.assertTrue(changed == 3 && turrets.stream().allMatch(t -> t.filter().priority == TargetFilter.Priority.LOWEST_HEALTH),
                    "priority changed on " + changed);
            helper.assertTrue(outside.filter().priority == TargetFilter.Priority.NEAREST, "a turret past the reach changed");
            helper.assertTrue(turrets.get(0).filter().rules.get(zombie) == TargetFilter.Rule.ALWAYS && turrets.get(1).filter().trusted.contains("alex")
                    && turrets.get(2).filter().trusted.isEmpty(), "other settings changed");
            menu.edit(positions, HubEdit.rule(creeper, TargetFilter.Rule.NEVER));
            helper.assertTrue(turrets.stream().allMatch(t -> t.filter().rules.get(creeper) == TargetFilter.Rule.NEVER)
                    && turrets.get(0).filter().rules.size() == 2, "the creeper rule did not reach all three");
            menu.edit(positions, HubEdit.rule(creeper, null));
            helper.assertTrue(turrets.stream().noneMatch(t -> t.filter().rules.containsKey(creeper)), "the creeper rule was not removed");
            menu.edit(positions, HubEdit.enabled(false));
            helper.assertTrue(turrets.stream().noneMatch(TurretBaseBlockEntity::enabled) && outside.enabled(), "turn off did not reach exactly the three");
            for (TurretBaseBlockEntity turret : turrets) level.removeBlock(turret.getBlockPos(), false);
            level.removeBlock(far, false);
            helper.succeed();
        });
    }

    /** Bad names, unknown mobs and out-of-range values are refused; a batch for more than 256 turrets does not decode. */
    @GameTest(template = EMPTY, batch = "batchEditRejectsBadInput")
    public static void batchEditRejectsBadInput(GameTestHelper helper) {
        for (HubEdit edit : List.of(new HubEdit(HubEdit.Kind.TRUSTED_ADD, "bad name!", 0), new HubEdit(HubEdit.Kind.TRUSTED_ADD, "", 0),
                new HubEdit(HubEdit.Kind.RULE, "bastion:not_a_mob", 0), new HubEdit(HubEdit.Kind.RULE, "minecraft:zombie", 5),
                new HubEdit(HubEdit.Kind.CATEGORY, "NOPE", 1), new HubEdit(HubEdit.Kind.PLAYER_MODE, "", 9),
                new HubEdit(HubEdit.Kind.PRIORITY, "", -1), new HubEdit(HubEdit.Kind.ENABLED, "", 2))) {
            helper.assertTrue(!edit.valid(), "accepted " + edit);
        }
        helper.assertTrue(HubEdit.trust("Steve_1", true).valid() && HubEdit.rule(new ResourceLocation("minecraft:zombie"), null).valid(), "refused good edits");
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeEnum(HubAction.Kind.EDIT);
        buf.writeVarInt(ControlHubMenu.MAX_TURRETS + 1);
        boolean refused = false;
        try {
            HubAction.decode(buf);
        } catch (DecoderException e) {
            refused = true;
        }
        helper.assertTrue(refused, "decoded a batch for more than " + ControlHubMenu.MAX_TURRETS + " turrets");
        helper.succeed();
    }

    /** What two filters share shows as such; what differs as null (Mixed); names and rules count how many have them. */
    @GameTest(template = EMPTY, batch = "filterMixShowsMixedValues")
    public static void filterMixShowsMixedValues(GameTestHelper helper) {
        ResourceLocation zombie = new ResourceLocation("minecraft:zombie"), creeper = new ResourceLocation("minecraft:creeper");
        TargetFilter a = new TargetFilter(), b = new TargetFilter();
        a.rules.put(zombie, TargetFilter.Rule.ALWAYS);
        b.rules.put(zombie, TargetFilter.Rule.ALWAYS);
        b.rules.put(creeper, TargetFilter.Rule.NEVER);
        b.priority = TargetFilter.Priority.LOWEST_HEALTH;
        a.trusted.add("alex");
        b.trusted.addAll(List.of("alex", "steve"));
        FilterMix mix = new FilterMix(List.of(a, b));
        helper.assertTrue(Boolean.TRUE.equals(mix.category(TargetFilter.Category.HOSTILE)) && Boolean.FALSE.equals(mix.category(TargetFilter.Category.PASSIVE)),
                "shared categories not shown as shared");
        helper.assertTrue(mix.priority() == null && mix.playerMode() == TargetFilter.PlayerMode.ALL_EXCEPT_TRUSTED, "priority should be mixed, player mode shared");
        helper.assertTrue(mix.rule(zombie) == TargetFilter.Rule.ALWAYS && mix.ruleCount(zombie) == 2, "zombie rule");
        helper.assertTrue(mix.rule(creeper) == null && mix.ruleCount(creeper) == 1, "the creeper rule should be partial");
        helper.assertTrue(mix.trusted().equals(Map.of("alex", 2, "steve", 1)), "trusted counts " + mix.trusted());
        helper.succeed();
    }
```

- [ ] **Step 2: Run to verify it fails.** `./gradlew runGameTestServer --offline`. Expected: compile errors (`HubEdit`, `FilterMix`, `ControlHubMenu.edit` undefined).

- [ ] **Step 3: `HubEdit` and `FilterMix`.** Create `src/main/java/dev/bastion/hub/HubEdit.java`:

```java
package dev.bastion.hub;

import dev.bastion.network.TurretConfigUpdate;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.targeting.TargetFilter;
import dev.bastion.turret.targeting.TargetFilter.Category;
import dev.bastion.turret.targeting.TargetFilter.PlayerMode;
import dev.bastion.turret.targeting.TargetFilter.Priority;
import dev.bastion.turret.targeting.TargetFilter.Rule;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * One change from the Control Hub's batch settings (PLAN "Atur massal"): a single targeting setting, or on/off, applied to
 * every selected turret on its own, so the rest of each turret's settings stays as it was. The screen applies it to its
 * copies at once; the server checks it (valid) before touching the real turrets.
 *
 * <p>CATEGORY: key = category name, value 1 on / 0 off. PLAYER_MODE, PRIORITY: value = ordinal. TRUSTED_ADD /
 * TRUSTED_REMOVE: key = player name. RULE: key = entity type id, value = Rule ordinal or -1 to remove. ENABLED: value 1 / 0.
 */
public record HubEdit(Kind kind, String key, int value) {
    public enum Kind {CATEGORY, PLAYER_MODE, PRIORITY, TRUSTED_ADD, TRUSTED_REMOVE, RULE, ENABLED}

    /** The names the turret GUI's trusted box takes. */
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

    public static HubEdit category(Category category, boolean on) {
        return new HubEdit(Kind.CATEGORY, category.name(), on ? 1 : 0);
    }

    public static HubEdit playerMode(PlayerMode mode) {
        return new HubEdit(Kind.PLAYER_MODE, "", mode.ordinal());
    }

    public static HubEdit priority(Priority priority) {
        return new HubEdit(Kind.PRIORITY, "", priority.ordinal());
    }

    public static HubEdit trust(String name, boolean add) {
        return new HubEdit(add ? Kind.TRUSTED_ADD : Kind.TRUSTED_REMOVE, name, 0);
    }

    /** {@code rule} null removes the mob's rule. */
    public static HubEdit rule(ResourceLocation id, @Nullable Rule rule) {
        return new HubEdit(Kind.RULE, id.toString(), rule == null ? -1 : rule.ordinal());
    }

    public static HubEdit enabled(boolean on) {
        return new HubEdit(Kind.ENABLED, "", on ? 1 : 0);
    }

    /** Whether this is a change the turret GUI could have made itself (server check for packets). */
    public boolean valid() {
        return switch (kind) {
            case CATEGORY -> Arrays.stream(Category.values()).anyMatch(c -> c.name().equals(key)) && (value == 0 || value == 1);
            case PLAYER_MODE -> value >= 0 && value < PlayerMode.values().length;
            case PRIORITY -> value >= 0 && value < Priority.values().length;
            case TRUSTED_ADD, TRUSTED_REMOVE -> NAME.matcher(key).matches();
            case RULE -> {
                ResourceLocation id = ResourceLocation.tryParse(key);
                yield id != null && ForgeRegistries.ENTITY_TYPES.containsKey(id) && value >= -1 && value < Rule.values().length;
            }
            case ENABLED -> value == 0 || value == 1;
        };
    }

    /** Applies a targeting change to {@code filter} (a valid edit); false when nothing changed or a list is full. */
    public boolean applyTo(TargetFilter filter) {
        switch (kind) {
            case CATEGORY -> {
                Category category = Category.valueOf(key);
                if (filter.category(category) == (value != 0)) return false;
                filter.setCategory(category, value != 0);
                return true;
            }
            case PLAYER_MODE -> {
                PlayerMode mode = PlayerMode.values()[value];
                if (filter.playerMode == mode) return false;
                filter.playerMode = mode;
                return true;
            }
            case PRIORITY -> {
                Priority priority = Priority.values()[value];
                if (filter.priority == priority) return false;
                filter.priority = priority;
                return true;
            }
            case TRUSTED_ADD -> {
                return filter.trusted.size() < TurretConfigUpdate.MAX_TRUSTED && filter.trusted.add(key.toLowerCase(Locale.ROOT));
            }
            case TRUSTED_REMOVE -> {
                return filter.trusted.remove(key.toLowerCase(Locale.ROOT));
            }
            case RULE -> {
                ResourceLocation id = new ResourceLocation(key);
                if (value < 0) return filter.rules.remove(id) != null;
                Rule rule = Rule.values()[value];
                if (filter.rules.get(id) == rule) return false;
                if (!filter.rules.containsKey(id) && filter.rules.size() >= TurretConfigUpdate.MAX_RULES) return false;
                filter.rules.put(id, rule);
                return true;
            }
            default -> {
                return false; // ENABLED is the turret's switch, not a targeting setting
            }
        }
    }

    /** Applies it to a turret (server); false when nothing changed. */
    public boolean apply(TurretBaseBlockEntity turret) {
        if (kind == Kind.ENABLED) {
            if (turret.enabled() == (value != 0)) return false;
            turret.setEnabled(value != 0);
            return true;
        }
        TargetFilter filter = turret.filter().copy();
        if (!applyTo(filter)) return false;
        turret.setFilter(filter);
        return true;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeEnum(kind);
        buf.writeUtf(key, 64);
        buf.writeVarInt(value);
    }

    public static HubEdit read(FriendlyByteBuf buf) {
        return new HubEdit(buf.readEnum(Kind.class), buf.readUtf(64), buf.readVarInt());
    }
}
```

Create `src/main/java/dev/bastion/hub/FilterMix.java`:

```java
package dev.bastion.hub;

import dev.bastion.turret.targeting.TargetFilter;
import dev.bastion.turret.targeting.TargetFilter.Category;
import dev.bastion.turret.targeting.TargetFilter.PlayerMode;
import dev.bastion.turret.targeting.TargetFilter.Priority;
import dev.bastion.turret.targeting.TargetFilter.Rule;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;

/** What the selected turrets' targeting has in common, for the hub's batch settings: a shared value, or null when they differ. */
public final class FilterMix {
    private final List<TargetFilter> filters;

    public FilterMix(Collection<TargetFilter> filters) {
        this.filters = List.copyOf(filters);
    }

    public int size() {
        return filters.size();
    }

    @Nullable
    public Boolean category(Category category) {
        return common(filter -> filter.category(category));
    }

    @Nullable
    public PlayerMode playerMode() {
        return common(filter -> filter.playerMode);
    }

    @Nullable
    public Priority priority() {
        return common(filter -> filter.priority);
    }

    /** Every name some of them trust, with how many trust it. */
    public Map<String, Integer> trusted() {
        Map<String, Integer> names = new TreeMap<>();
        for (TargetFilter filter : filters) filter.trusted.forEach(name -> names.merge(name, 1, Integer::sum));
        return names;
    }

    /** Every mob some of them have a rule for, sorted. */
    public List<ResourceLocation> ruleIds() {
        TreeSet<ResourceLocation> ids = new TreeSet<>();
        for (TargetFilter filter : filters) ids.addAll(filter.rules.keySet());
        return new ArrayList<>(ids);
    }

    /** How many of them have any rule for this mob. */
    public int ruleCount(ResourceLocation id) {
        return (int) filters.stream().filter(filter -> filter.rules.containsKey(id)).count();
    }

    /** The rule all of them have for this mob, or null when they differ or some have none. */
    @Nullable
    public Rule rule(ResourceLocation id) {
        return ruleCount(id) == filters.size() ? common(filter -> filter.rules.get(id)) : null;
    }

    @Nullable
    private <T> T common(Function<TargetFilter, T> value) {
        if (filters.isEmpty()) return null;
        T first = value.apply(filters.get(0));
        for (TargetFilter filter : filters) {
            if (!Objects.equals(value.apply(filter), first)) return null;
        }
        return first;
    }
}
```

- [ ] **Step 4: Server side.** In `ControlHubMenu` (imports `dev.bastion.hub.HubEdit`, `java.util.Collection`, `java.util.LinkedHashSet`):

```java
    /** Server: one batch change on every listed turret the player can still reach through the hub; how many changed. */
    public int edit(Collection<BlockPos> positions, HubEdit edit) {
        if (hub == null || player == null || !edit.valid()) return 0;
        int changed = 0;
        for (BlockPos pos : new LinkedHashSet<>(positions)) {
            TurretBaseBlockEntity turret = hub.reachable(player, pos);
            if (turret != null && edit.apply(turret)) changed++;
        }
        return changed;
    }
```

`HubAction` becomes (javadoc: "…open a turret's GUI through the hub, go back to the hub from such a GUI, or apply one batch change to the selected turrets…"):

```java
public record HubAction(Kind kind, List<BlockPos> turrets, @Nullable HubEdit edit) {
    public enum Kind {OPEN, BACK, EDIT}

    public static HubAction open(BlockPos turret) {
        return new HubAction(Kind.OPEN, List.of(turret), null);
    }

    public static HubAction back() {
        return new HubAction(Kind.BACK, List.of(), null);
    }

    public static HubAction edit(Collection<BlockPos> turrets, HubEdit edit) {
        return new HubAction(Kind.EDIT, List.copyOf(turrets), edit);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(kind);
        buf.writeCollection(turrets, FriendlyByteBuf::writeBlockPos);
        buf.writeNullable(edit, (b, e) -> e.write(b));
    }

    public static HubAction decode(FriendlyByteBuf buf) {
        Kind kind = buf.readEnum(Kind.class);
        int count = buf.readVarInt();
        if (count > ControlHubMenu.MAX_TURRETS) throw new DecoderException("too many turrets: " + count);
        List<BlockPos> turrets = new ArrayList<>(count);
        for (int i = 0; i < count; i++) turrets.add(buf.readBlockPos());
        return new HubAction(kind, turrets, buf.readNullable(HubEdit::read));
    }
```

with, in `handle`, a third case:

```java
                case EDIT -> {
                    if (player.containerMenu instanceof ControlHubMenu menu && menu.stillValid(player) && edit != null) menu.edit(turrets, edit);
                }
```

(imports `dev.bastion.hub.HubEdit`, `org.jetbrains.annotations.Nullable`, `java.util.Collection`).

- [ ] **Step 5: Shared mob list.** Create `src/main/java/dev/bastion/client/screen/MobList.java`:

```java
package dev.bastion.client.screen;

import dev.bastion.turret.targeting.TargetFilter.Category;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Mob types for the targeting rule lists: the turret's Targeting tab and the Control Hub's batch settings. */
final class MobList {
    private static List<EntityType<?>> living;

    private MobList() {
    }

    /** Registered mobs whose id or name contains {@code query}, by name. */
    static List<ResourceLocation> matching(String query) {
        String q = query.trim().toLowerCase(Locale.ROOT);
        List<ResourceLocation> rows = new ArrayList<>();
        for (EntityType<?> type : living()) {
            ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(type);
            if (id != null && (id.toString().contains(q) || type.getDescription().getString().toLowerCase(Locale.ROOT).contains(q))) rows.add(id);
        }
        return rows;
    }

    /** Every registered mob: living entity types are exactly the ones with default attributes. */
    private static List<EntityType<?>> living() {
        if (living == null) {
            living = ForgeRegistries.ENTITY_TYPES.getValues().stream()
                    .filter(type -> type != EntityType.PLAYER && DefaultAttributes.hasSupplier(type))
                    .sorted(Comparator.comparing(type -> type.getDescription().getString()))
                    .toList();
        }
        return living;
    }

    static Component name(ResourceLocation id) {
        return ForgeRegistries.ENTITY_TYPES.containsKey(id) ? ForgeRegistries.ENTITY_TYPES.getValue(id).getDescription() : Component.literal(id.toString());
    }

    /**
     * Category a mob type falls under without a rule. ponytail: approximated from its spawn group, since the real check
     * needs an entity instance; bosses and neutral mobs show as hostile/passive here.
     */
    static Category category(ResourceLocation id) {
        if (!ForgeRegistries.ENTITY_TYPES.containsKey(id)) return Category.PASSIVE;
        return ForgeRegistries.ENTITY_TYPES.getValue(id).getCategory().isFriendly() ? Category.PASSIVE : Category.HOSTILE;
    }
}
```

In `TurretScreen`: delete the `livingTypes` field and the `livingTypes()`, `entityName(...)` and `categoryOf(...)` methods; `ruleRows()` becomes `return search.isBlank() ? new ArrayList<>(menu.filter.rules.keySet()) : MobList.matching(search);`; replace the remaining calls `entityName(` with `MobList.name(` and `categoryOf(` with `MobList.category(`; drop imports the compiler reports unused (`EntityType`, `DefaultAttributes`, `ForgeRegistries`, `Comparator`). The Targeting tab must behave exactly as before.

`HubSelection`, for dev scenes:

```java
    /** Selects these turrets (dev scenes). */
    public static void select(Collection<BlockPos> turrets) {
        selected.addAll(turrets);
    }
```

(import `java.util.Collection`).

- [ ] **Step 6: The Batch tab.** In `ControlHubScreen` (imports `dev.bastion.hub.FilterMix`, `dev.bastion.hub.HubEdit`, `dev.bastion.turret.targeting.TargetFilter`, `dev.bastion.turret.targeting.TargetFilter.Category`, `dev.bastion.turret.targeting.TargetFilter.PlayerMode`, `dev.bastion.turret.targeting.TargetFilter.Priority`, `dev.bastion.turret.targeting.TargetFilter.Rule`, `net.minecraft.client.gui.components.EditBox`, `org.lwjgl.glfw.GLFW`, `java.util.Map`):
  - `enum Tab {MAP, LIST, BATCH}` and the class javadoc's "Task 7 adds Batch settings" becomes "and Batch settings (one change at a time to every selected turret)".
  - fields:

```java
    // Batch tab: settings on the left, trusted players and mob rules on the right, as the turret's Targeting tab.
    private static final int B_LEFT = 8, B_RIGHT = 132, B_W = 116, LINE = 11, TRUSTED_Y = 57, TRUSTED_ROWS = 4, RULE_Y = 131, RULE_ROWS = 5;
    private EditBox trustedBox, searchBox;
    private String trustedDraft = "", search = "";
    private int trustedScroll, ruleScroll;
```

  - in `init()`: `else if (tab() == Tab.BATCH) initBatch();`; in `switchTab`: `trustedScroll = ruleScroll = 0;` before `rebuildWidgets()`.
  - in `containerTick()` the widget loop becomes `w.visible = online && (tab() != Tab.BATCH || !targets().isEmpty());` (compute `boolean any = !targets().isEmpty();` once before the loop).
  - in `renderBg`, after the map line: `if (online() && tab() == Tab.BATCH && !targets().isEmpty()) batchFrames(graphics);`
  - in `renderLabels`, the last two lines become:

```java
        switch (tab()) {
            case MAP -> renderMapLabels(graphics);
            case LIST -> renderListLabels(graphics, mouseX - leftPos, mouseY - topPos);
            case BATCH -> renderBatchLabels(graphics, mouseX - leftPos, mouseY - topPos);
        }
```

  - in `mouseClicked`, before `return super.mouseClicked(...)`: `if (online() && tab() == Tab.BATCH && button == 0 && clickBatchList((int) mouseX - leftPos, (int) mouseY - topPos)) return true;`
  - in `mouseScrolled`, before `return super.mouseScrolled(...)`: `if (online() && tab() == Tab.BATCH && scrollBatchList((int) mouseX - leftPos, (int) mouseY - topPos, delta > 0 ? -1 : 1)) return true;`
  - new members:

```java
    private void initBatch() {
        Category[] categories = Category.values();
        for (int i = 0; i < categories.length; i++) {
            Category category = categories[i];
            addRenderableWidget(new FlatButton(leftPos + B_LEFT, topPos + 44 + i * 14, B_W, 12, () -> {
                Component name = Component.translatable("gui.bastion.category." + category.name().toLowerCase(Locale.ROOT));
                return mix().category(category) == null ? mixed(name) : name;
            }, () -> send(HubEdit.category(category, !Boolean.TRUE.equals(mix().category(category)))), () -> Boolean.TRUE.equals(mix().category(category))));
        }
        addRenderableWidget(new FlatButton(leftPos + B_LEFT, topPos + 125, B_W, 12, () -> {
            PlayerMode mode = mix().playerMode();
            return mode == null ? Component.translatable("gui.bastion.control_hub.mixed")
                    : Component.translatable("gui.bastion.player_mode." + mode.name().toLowerCase(Locale.ROOT));
        }, () -> {
            PlayerMode mode = mix().playerMode();
            send(HubEdit.playerMode(PlayerMode.values()[mode == null ? 0 : (mode.ordinal() + 1) % PlayerMode.values().length]));
        }));
        addRenderableWidget(new FlatButton(leftPos + B_LEFT, topPos + 150, B_W, 12, () -> {
            Priority priority = mix().priority();
            return priority == null ? Component.translatable("gui.bastion.control_hub.mixed")
                    : Component.translatable("gui.bastion.priority." + priority.name().toLowerCase(Locale.ROOT));
        }, () -> {
            Priority priority = mix().priority();
            send(HubEdit.priority(Priority.values()[priority == null ? 0 : (priority.ordinal() + 1) % Priority.values().length]));
        }));
        addRenderableWidget(new FlatButton(leftPos + B_LEFT, topPos + 176, B_W / 2 - 1, 13,
                () -> Component.translatable("gui.bastion.control_hub.turn_on"), () -> send(HubEdit.enabled(true))));
        addRenderableWidget(new FlatButton(leftPos + B_LEFT + B_W / 2 + 1, topPos + 176, B_W / 2 - 1, 13,
                () -> Component.translatable("gui.bastion.control_hub.turn_off"), () -> send(HubEdit.enabled(false))));
        trustedBox = textBox(B_RIGHT, 42, B_W - 18, trustedDraft, "gui.bastion.trusted.hint");
        trustedBox.setMaxLength(16);
        trustedBox.setFilter(s -> s.matches("[A-Za-z0-9_]*"));
        trustedBox.setResponder(s -> trustedDraft = s);
        addRenderableWidget(new FlatButton(leftPos + B_RIGHT + B_W - 16, topPos + 42, 16, 12, () -> Component.literal("+"), this::addTrusted));
        searchBox = textBox(B_RIGHT, 116, B_W, search, "gui.bastion.rules.hint");
        searchBox.setMaxLength(48);
        searchBox.setResponder(s -> {
            search = s;
            ruleScroll = 0;
        });
    }

    /** Borderless text field; its frame is drawn in batchFrames. */
    private EditBox textBox(int x, int y, int width, String value, String hint) {
        EditBox box = new EditBox(font, leftPos + x + 3, topPos + y + 2, width - 6, 10, Component.translatable(hint));
        box.setBordered(false);
        box.setTextColor(TEXT);
        box.setValue(value);
        box.setHint(Component.translatable(hint).withStyle(ChatFormatting.DARK_GRAY));
        return addRenderableWidget(box);
    }

    private void batchFrames(GuiGraphics graphics) {
        frame(graphics, leftPos + B_RIGHT, topPos + 42, B_W - 18, 12);
        frame(graphics, leftPos + B_RIGHT, topPos + 116, B_W, 12);
        frame(graphics, leftPos + B_RIGHT, topPos + TRUSTED_Y - 1, B_W, TRUSTED_ROWS * LINE + 2);
        frame(graphics, leftPos + B_RIGHT, topPos + RULE_Y - 1, B_W, RULE_ROWS * LINE + 2);
    }

    /** Selected turrets still in the roster, in roster order. */
    private List<BlockPos> targets() {
        List<BlockPos> targets = new ArrayList<>();
        for (Entry turret : menu.turrets()) {
            if (HubSelection.selected.contains(turret.pos())) targets.add(turret.pos());
        }
        return targets;
    }

    /** What the selected turrets' targeting has in common (turrets whose targeting has arrived). */
    private FilterMix mix() {
        List<TargetFilter> filters = new ArrayList<>();
        for (BlockPos pos : targets()) {
            TargetFilter filter = menu.filter(pos);
            if (filter != null) filters.add(filter);
        }
        return new FilterMix(filters);
    }

    /** One change to every selected turret: shown on this screen's copies at once, then sent; the next roster confirms it. */
    private void send(HubEdit edit) {
        List<BlockPos> targets = targets();
        if (targets.isEmpty() || !edit.valid()) return;
        for (BlockPos pos : targets) {
            TargetFilter filter = menu.filter(pos);
            if (filter != null) edit.applyTo(filter);
        }
        BastionNetwork.CHANNEL.sendToServer(HubAction.edit(targets, edit));
        click();
    }

    private void addTrusted() {
        if (trustedDraft.isBlank()) return;
        send(HubEdit.trust(trustedDraft.trim(), true));
        trustedDraft = "";
        trustedBox.setValue("");
    }

    private Component mixed(Component name) {
        return name.copy().append(" · ").append(Component.translatable("gui.bastion.control_hub.mixed"));
    }

    /** The search's matches, or every mob some selected turret has a rule for while the search is empty. */
    private List<ResourceLocation> ruleRows(FilterMix mix) {
        return search.isBlank() ? mix.ruleIds() : MobList.matching(search);
    }

    private static boolean inColumn(int x, int y, int rowY) {
        return x >= B_RIGHT && x < B_RIGHT + B_W && y >= rowY && y < rowY + LINE;
    }

    private void renderBatchLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        List<BlockPos> targets = targets();
        if (targets.isEmpty()) {
            centered(graphics, Component.translatable("gui.bastion.control_hub.batch.none"), 100, AMBER);
            return;
        }
        FilterMix mix = mix();
        fit(graphics, Component.translatable("gui.bastion.control_hub.batch.count", targets.size()), B_LEFT, 32, B_W, TEXT);
        fit(graphics, Component.translatable("gui.bastion.targeting.players"), B_LEFT, 116, B_W, MUTED);
        fit(graphics, Component.translatable("gui.bastion.targeting.priority"), B_LEFT, 141, B_W, MUTED);
        long on = menu.turrets().stream().filter(turret -> HubSelection.selected.contains(turret.pos()) && turret.enabled()).count();
        fit(graphics, Component.translatable("gui.bastion.control_hub.on_count", on, targets.size()), B_LEFT, 166, B_W, MUTED);
        fit(graphics, Component.translatable("gui.bastion.targeting.trusted"), B_RIGHT, 32, B_W, MUTED);
        fit(graphics, Component.translatable("gui.bastion.targeting.rules"), B_RIGHT, 106, B_W, MUTED);

        List<Map.Entry<String, Integer>> names = new ArrayList<>(mix.trusted().entrySet());
        trustedScroll = Mth.clamp(trustedScroll, 0, Math.max(0, names.size() - TRUSTED_ROWS));
        for (int row = 0; row < TRUSTED_ROWS && row + trustedScroll < names.size(); row++) {
            Map.Entry<String, Integer> name = names.get(row + trustedScroll);
            int y = TRUSTED_Y + row * LINE;
            boolean hover = inColumn(mouseX, mouseY, y);
            Component text = Component.literal(name.getKey());
            if (name.getValue() < mix.size()) text = text.copy().append(" ").append(Component.translatable("gui.bastion.control_hub.partial"));
            fit(graphics, text, B_RIGHT + 3, y + 2, B_W - 16, hover ? RED : TEXT);
            if (hover) graphics.drawString(font, "x", B_RIGHT + B_W - 9, y + 2, RED, false);
        }
        if (names.isEmpty()) fit(graphics, Component.translatable("gui.bastion.trusted.empty"), B_RIGHT + 3, TRUSTED_Y + 2, B_W - 6, MUTED);

        List<ResourceLocation> rules = ruleRows(mix);
        ruleScroll = Mth.clamp(ruleScroll, 0, Math.max(0, rules.size() - RULE_ROWS));
        for (int row = 0; row < RULE_ROWS && row + ruleScroll < rules.size(); row++) {
            ResourceLocation id = rules.get(row + ruleScroll);
            int y = RULE_Y + row * LINE, count = mix.ruleCount(id);
            Rule rule = mix.rule(id);
            boolean partial = count > 0 && rule == null;
            int light = count == 0 ? 0xFF3C4550 : partial ? 0xFF000000 | AMBER : rule == Rule.ALWAYS ? 0xFF7CFF8A : 0xFFFF5A4F;
            graphics.fill(B_RIGHT + 3, y + 3, B_RIGHT + 8, y + 8, light);
            Component text = MobList.name(id);
            if (partial) text = text.copy().append(" ").append(Component.translatable("gui.bastion.control_hub.partial"));
            fit(graphics, text, B_RIGHT + 11, y + 2, B_W - 14, inColumn(mouseX, mouseY, y) ? ACCENT : TEXT);
        }
        if (rules.isEmpty()) {
            fit(graphics, Component.translatable(search.isBlank() ? "gui.bastion.rules.empty" : "gui.bastion.rules.none"), B_RIGHT + 3, RULE_Y + 2, B_W - 6, MUTED);
        }
    }

    /** A trusted row removes that name from all; a rule row cycles none/mixed -> always -> never -> none for all. */
    private boolean clickBatchList(int x, int y) {
        if (targets().isEmpty()) return false;
        FilterMix mix = mix();
        if (y >= TRUSTED_Y && y < TRUSTED_Y + TRUSTED_ROWS * LINE && inColumn(x, y, y)) {
            List<String> names = new ArrayList<>(mix.trusted().keySet());
            int index = (y - TRUSTED_Y) / LINE + trustedScroll;
            if (index < names.size()) send(HubEdit.trust(names.get(index), false));
            return true;
        }
        if (y >= RULE_Y && y < RULE_Y + RULE_ROWS * LINE && inColumn(x, y, y)) {
            List<ResourceLocation> rows = ruleRows(mix);
            int index = (y - RULE_Y) / LINE + ruleScroll;
            if (index < rows.size()) {
                ResourceLocation id = rows.get(index);
                Rule rule = mix.rule(id);
                send(HubEdit.rule(id, rule == null ? Rule.ALWAYS : rule == Rule.ALWAYS ? Rule.NEVER : null));
            }
            return true;
        }
        return false;
    }

    private boolean scrollBatchList(int x, int y, int step) {
        if (x < B_RIGHT || x >= B_RIGHT + B_W) return false;
        if (y >= TRUSTED_Y && y < TRUSTED_Y + TRUSTED_ROWS * LINE) {
            trustedScroll += step; // clamped when drawn
            return true;
        }
        if (y >= RULE_Y && y < RULE_Y + RULE_ROWS * LINE) {
            ruleScroll += step;
            return true;
        }
        return false;
    }

    /** Typing in a text box must not close the screen (inventory key) or swap hotbar slots (number keys). */
    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (getFocused() instanceof EditBox box && box.isFocused() && key != GLFW.GLFW_KEY_ESCAPE) {
            if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && box == trustedBox) addTrusted();
            else box.keyPressed(key, scanCode, modifiers);
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }
```

(Clamp the scrolls also when negative: `Mth.clamp(…, 0, …)` in `renderBatchLabels` covers it.)

- [ ] **Step 7: Strings and the scene.** `lang/en_us.json`:

```json
  "gui.bastion.control_hub.tab.batch": "Batch settings",
  "gui.bastion.control_hub.batch.none": "Select turrets on the Map or List tab first",
  "gui.bastion.control_hub.batch.count": "%s turrets selected",
  "gui.bastion.control_hub.mixed": "Mixed",
  "gui.bastion.control_hub.partial": "(some)",
  "gui.bastion.control_hub.turn_on": "Turn on",
  "gui.bastion.control_hub.turn_off": "Turn off",
  "gui.bastion.control_hub.on_count": "On: %s / %s",
```

`lang/id_id.json`:

```json
  "gui.bastion.control_hub.tab.batch": "Atur massal",
  "gui.bastion.control_hub.batch.none": "Pilih turret dulu di tab Peta atau Daftar",
  "gui.bastion.control_hub.batch.count": "%s turret terpilih",
  "gui.bastion.control_hub.mixed": "Campur",
  "gui.bastion.control_hub.partial": "(sebagian)",
  "gui.bastion.control_hub.turn_on": "Nyalakan",
  "gui.bastion.control_hub.turn_off": "Matikan",
  "gui.bastion.control_hub.on_count": "Nyala: %s / %s",
```

In `ControlHubShowcase.onClientTick`, replace the comment line `// ticks 186-198: Task 7's batch settings shot` with (imports `dev.bastion.turret.targeting.TargetFilter`, `net.minecraft.resources.ResourceLocation`):

```java
        if (tick == 186) {
            Showcase.server(server, s -> { // different settings, so Mixed and (some) show
                if (s.overworld().getBlockEntity(T3_IFACE.offset(14, 0, -6)) instanceof TurretBaseBlockEntity sniper) {
                    TargetFilter filter = sniper.filter().copy();
                    filter.priority = TargetFilter.Priority.LOWEST_HEALTH;
                    filter.rules.put(new ResourceLocation("minecraft:zombie"), TargetFilter.Rule.ALWAYS);
                    sniper.setFilter(filter);
                }
            });
            HubSelection.select(List.of(T3_IFACE.offset(-4, 0, 6), T3_IFACE.offset(5, 0, 7), T3_IFACE.offset(14, 0, -6)));
        }
        if (tick == 192 && mc.screen != null) clickTab(mc.screen, 2);
        if (tick == 198) Showcase.shot(mc, "hub_gui_batch");
```

- [ ] **Step 8: Run.** `./gradlew build --offline`; `./gradlew runGameTestServer --offline` (expected "All 146 required tests passed"); `./gradlew runGameTestServer --offline -PwithCompatMods` (146); `./gradlew runClient --offline -Pshowcase=hub -PshowcaseWorld=showcase_fx`. Read `showcase_hub_gui_batch.png`: "3 turrets selected", Hostile and Bosses lit, Priority "Mixed", "On: 3 / 3", the rules list showing Zombie "(some)" with an amber light, no text overlapping. Then open any turret's Targeting tab once in the scene world or check by hand that searching and clicking rules still works (MobList move).

---

### Task 8: Docs and final verification

**Files:**
- Modify: `docs/PLAN.md`, `docs/TESTING.md`, `docs/release/DESCRIPTION.md`, `docs/release/DESCRIPTION.curseforge.html`, `docs/release/UPLOAD.md`, `docs/HANDOFF.md`

- [ ] **Step 1: PLAN.md.** In section "Turret Control Hub" replace the status line with (the day it is finished as YYYY-MM-DD): `**Status**: selesai YYYY-MM-DD (plan `docs/superpowers/plans/2026-10-10-turret-control-hub.md`): 146 GameTest lolos (juga dengan `-PwithCompatMods`), scene `-Pshowcase=hub` dicek siang/malam dan semua layar; belum dites user di game.` Note there any value that changed during implementation (e.g. a flipped screen/dish angle) in one line.

- [ ] **Step 2: TESTING.md.** Add after "## Feed Hub" (and its multiblock subsection):

```markdown
## Turret Control Hub
- [ ] Recipes at the Module Workstation, own tab: 4 Hub Casings, Hub Power Port, Hub Interface, Satellite Uplink T1; T2 from a T1, T3 from a T2.
- [ ] Build: Hub Interface facing you, a 3x3 behind it of Hub Casings with one Hub Power Port anywhere (the middle too), a Satellite Uplink on the middle block (its hologram shows both blocks before you place it). Before it is done, right-click the Interface with an empty hand: the action bar names the first problem; a hologram shows cyan boxes with the missing block turning inside, red over a wrong block, amber on every casing while there is no port; boxes go as you place parts, all of it when the hub forms (10 s at most).
- [ ] Forming: a cyan sheet sweeps the platform, a flash at the dish, the dish unfolds and turns (about one turn per 10 s) with a soft hum, the Interface screen shows a radar, cyan lines along the platform's outer edges, a chime. Without FE it is OFFLINE: red blinking screen, dish stopped and drooping, red glow, a power-down sound.
- [ ] FE: a cable or Creative Power Source on a Power Port (up to 2,000 FE/t per port; a port in no hub takes nothing). Upkeep: T1 20, T2 60, T3 150 FE/t (hover the FE bar). Run it dry: OFFLINE; refill: ONLINE.
- [ ] Break any part: the hub stops (lines off, dish folds), open hub and turret screens close; only the broken block drops. Breaking the Interface loses its FE.
- [ ] Two hubs sharing casings: only the first one forms; the second one's blueprint shows the shared blocks red.
- [ ] Screen header: "Satellite T2 · 3x3 chunks · N turrets" and the FE bar. Map tab: terrain from above, chunk lines, the hub's cyan square, a dot per turret you may open: green, yellow below half HP, blinking red below a quarter, grey when off or unarmed, an orange "!" when out of ammo or FE; hover: name, coordinates, HP, ammo or FE %, state; the wheel zooms 1x / 2x / 4x round the cursor, right-drag moves.
- [ ] Reach: T1 only the satellite's chunk, T2 3x3, T3 7x7 chunks (F3 + G shows chunk borders). Another player's turrets are not listed until they trust you; an op sees all.
- [ ] Click a dot or a List row: that turret's own GUI opens from the hub; weapon, ammo and modules go in from your inventory, Targeting and Info work; "◀ Map" brings back the hub screen with the same tab, zoom and selection. Walk more than 8 blocks from the Interface: it closes.
- [ ] List tab: icon, name, coordinates, HP bar, ammo or FE %, state lamp per row; sort by distance / HP / ammo; the checkbox selects; the wheel scrolls.
- [ ] Batch settings: select turrets (Shift+click, Shift+drag a box, checkboxes, Select all), then change a category, the player mode, the priority, add or remove a trusted player, cycle a mob rule (none -> always -> never -> none), Turn on / Turn off: only that setting changes on every selected turret (open one turret's own Targeting tab to check). Values that differ show "Mixed", names and rules only some have show "(some)".
- [ ] Dedicated server (`runServer` + a client): all of the above; a second, untrusted player sees none of your turrets on your hub.
- [ ] Save and reload: the hub is formed again, FE kept. Server config: `controlHubEnergyCapacity`, `controlHubPortMaxInput`, `controlHubUpkeep`, `controlHubRange`.
```

- [ ] **Step 3: Store texts and changelog.**
  - `docs/release/DESCRIPTION.md`, a new section before "## Modules":

```markdown
## Turret Control Hub

Build a 3x3 platform: a Hub Interface on one side, Hub Casings with at least one Hub Power Port, and a Satellite Uplink on the middle block. Right-click an unfinished Interface and a hologram shows what is missing. Once it has FE, its screen shows every turret you may open within 1, 3x3 or 7x7 chunks (Satellite Uplink T1, T2, T3) on a map and a list, coloured by health, with their ammo and state. Click one to open its own screen from the hub: swap modules, top up ammo or change its targeting without walking over. Or select many and change their categories, priority, mob rules, trusted players or on/off all at once.
```

  - `docs/release/DESCRIPTION.curseforge.html`: the same section as `<h2>Turret Control Hub</h2><p>…</p>` before `<h2>Modules</h2>`.
  - `docs/release/UPLOAD.md`, "Changelog: 0.1.2", first entry under "New":

```
- Turret Control Hub: a 3x3 multiblock you build part by part (Hub Interface, Hub Casings, at least one Hub Power Port,
  a Satellite Uplink T1/T2/T3 on the middle block; a hologram shows what is missing). With FE (20/60/150 FE/t) it shows
  every turret you may open within 1, 3x3 or 7x7 chunks on a map and a list (health colours, ammo, state), opens any of
  them from the hub, and changes targeting or on/off on many selected turrets at once.
```

- [ ] **Step 4: HANDOFF.md.** Update "State at a glance" (test count 146, the hub built and uncommitted, not yet tested in game) and add a new NOW item 0 describing: the feature (spec PLAN "Turret Control Hub", plan path), the package `dev.bastion.hub`, the layout check / forming / upkeep, the roster (`ControlHubMenu` + `HubRoster` every 10 ticks, filters only when changed, 32 per packet), remote turret GUIs (`TurretMenu` hub link, `TurretConfigUpdate.apply` now menu-based), batch edits (`HubEdit`, `FilterMix`), protocol 10, the scene `-Pshowcase=hub`, the 4 placeholder sounds (SOUND_SHEET 67-70), and anything the scene review changed. Renumber the older NOW items.

- [ ] **Step 5: Final verification.** Run, and read each result before claiming anything:
  1. `./gradlew build --offline`
  2. `./gradlew runGameTestServer --offline` → "All 146 required tests passed"
  3. `./gradlew runGameTestServer --offline -PwithCompatMods` → 146
  4. `./gradlew runClient --offline -Pshowcase=hub -PshowcaseWorld=showcase_fx` → read every `showcase_hub_*.png`
  5. `./gradlew runClient --offline -Pshowcase=placement -PshowcaseWorld=showcase_fx` → the placement hologram looks as before (`PlacementPreview.box` was refactored)
  6. `git status`: only the files of this plan changed or added, `modlist.html` untouched; nothing committed.

  Then give the user the in-game test checklist (Indonesian, short, from the TESTING.md section) and wait for "commit".
