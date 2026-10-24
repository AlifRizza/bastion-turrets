# Fortress Turret Base (3x3) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A third multiblock base, the 3x3 Fortress Turret Base, for a new "huge" weapon size class (no huge weapon exists yet), with tiers, HP, a damaged variant, hologram placement, Feed Hub support and its own platform model. It ships as 0.5.0.

**Architecture:** A new `BaseKind.FORTRESS` and a `FortressTurretBaseBlock` (extends `TurretBaseBlock`, not `LargeTurretBaseBlock`) with a 0–8 `CELL` property whose centre cell 4 is the core holding the `TurretBaseBlockEntity`; the eight others are `TurretPartBlockEntity` parts. Every spot that today asks `large()` or matches `LargeTurretBaseBlock` gets an explicit FORTRESS case (exhaustive switches where possible), so nothing silently falls back to the standard 1x1 behaviour.

**Tech Stack:** Forge 1.20.1 (47.4.26), Java 17, GeckoLib 4.8.4, GameTest (`runGameTestServer`), JEI, Jade; Python generators in `tools/`.

**Spec:** `docs/PLAN.md` §8 "Fortress Turret Base 3x3 (… ) — rilis 0.5.0". Code map of every Large Turret Base touchpoint: `<workspace>/large-base-map.md` (file:line, with [SIZE]/[RISK]/[SWITCH]/[GEN] tags). Read both.

## Global Constraints

Everything in `<workspace>/constraints.md` (git, Gradle, GameTest, generator and lang rules carried over from 0.4.0) applies. On top:
- **No commits.** The user commits. Never touch `modlist.html`, `sfx/`, `run/saves/showcase`, `run/config/*`.
- Ids: block/item `fortress_turret_base`, damaged item `damaged_fortress_turret_base`, part item `fortress_frame`, kind `BaseKind.FORTRESS` (appended last: enum ordinals go over the wire), geo/texture/animation name `fortress_turret_base`.
- Display names: "Fortress Turret Base", "Damaged Fortress Turret Base", "Fortress Frame". id_id: "Base Turret Benteng", "Base Turret Benteng Rusak", "Rangka Benteng".
- Config (server): `fortressBaseHealthMultiplier` default **5.0**, range 0.1–100, comment "Max HP of a Fortress Turret Base (3x3) relative to a standard base of the same tier." (T1/T2/T3 = 500/1250/2500 with the default standard HP).
- Slots, regen and turn-speed multiplier per tier: the same as every base (`TurretTier`, PLAN 4.1).
- Only weapons whose kind is FORTRESS fit a Fortress; a FORTRESS weapon fits nothing else (the existing rule `module.kind() == kind`).
- Repair: Part Assembler, 1 damaged fortress + **3** Repair Kits → Fortress Turret Base with its tier (300 ticks, 12000 FE).
- Recipes (`tools/gen_recipes.py`): `fortress_frame` at the Part Workstation = 4 iron_block, 1 diamond, 1 redstone_block (200 ticks, 8000 FE), category tab `bastion:large_turret_base` (no 19th tab); `fortress_turret_base` at the Part Assembler = 1 fortress_frame + 2 large_base_plating + 1 large_base_housing + 1 large_base_turntable (400 ticks, 25000 FE), category `bastion:turret_base`.
- PROTOCOL stays `"13"` unless a `bastion:main` packet's layout changes (the hitbox's spawn data is not a `bastion:main` packet; a 0.4.0 client cannot join anyway: new registry entries).
- `mod_version` becomes `0.5.0` (Task 4).

**My calls (controller; listed for the user's review):** core in the centre and the footprint centred on the clicked block; HP ×5.0; 3 Repair Kits; blast `detonate(4.5f, 6f, 1.4f)` and DESTROYED event scale 2.0 (client picks the large destroy sound for >1.2); hitbox width 2.95, reach 2.6; Overwatch camera above 5 / behind 8; render box inflate 8; fallback muzzle distance 100 (squared, like `large ? 49 : 9`); Fortress Frame recipe and the mixed assembler recipe above; the Turret Configurator fix (works on any block of a multiblock base, Large included).

## Review Focus

1. **A Fortress silently treated as a 1x1 base** (HP 100, drops a damaged *standard* base, fed by a Feed Hub it only half stands on, hitbox 1x1, redstone only on the core). Tests: `fortressHealthFollowsConfig`, `destroyedFortressDropsItsDamagedSelf`, `fortressHitboxSpansItsFootprint`, `redstoneOnAnyFortressBlockCounts` (Task 1), `feedHubFeedsFortressOnlyOnAFull3x3` (Task 3).
2. **Placement next to blocks and mobs**: a block or a mob in any of the 9 cells blocks it; a side or bottom face offers nothing. Test: `fortressFootprintIsWhatGetsPlaced` (Task 1).
3. **Breaking or exploding an outer cell** takes the whole base down with exactly one drop, never 9. Test: `fortressBaseIsOneBlock` (Task 1).
4. **Tier kit and creative variants**: a T3 kit on an outer cell upgrades the base; the creative tab holds T1/T2/T3 Fortress. Tests: `tierKitWorksOnAnyFortressBlock` (Task 1), the existing creative-tab test extended (Task 1).
5. **Tools that click a part block** (Turret Configurator, Jade) see the core. Tests: `configuratorWorksOnAnyFortressBlock`, `fortressPartsLeadToTheCore` (Task 3).

---

### Task 1: Fortress block, kind and gameplay (server)

**Files:**
- Create: `src/main/java/dev/bastion/turret/FortressTurretBaseBlock.java`, `src/main/java/dev/bastion/gametest/FortressBaseTests.java`, `src/main/resources/data/bastion/loot_tables/blocks/fortress_turret_base.json` (copy of the large one, `core` condition adapted to `cell: "4"`)
- Modify (see the map for lines): `BaseKind`, `TurretBaseBlock` (needs-base message switch), `TurretBaseBlockEntity` (maxHealth, destroy, hitbox bind, active, mountCenter if needed, render box), `TurretHitboxEntity`, `WeaponModuleItem` (fits tooltip), `TurretBaseItem` (size tooltip), `BastionBlocks`, `BastionItems` (base item, damaged item), `BastionBlockEntities` (both valid-block lists), `BastionConfig`, `client/compat/jei/WeaponCategory.java` + `client/render/TurretBaseModel.java` (compile-forced switch cases only: item `FORTRESS_TURRET_BASE`, name `fortress_turret_base`), `src/dev/java/dev/bastion/dev/DevCommands.java` + `DevCommandTests.java` (base for a FORTRESS module), `data/minecraft/tags/blocks/mineable/pickaxe.json`, lang `en_us.json` + `id_id.json`, `gametest/EarlyTurretTests.java:114` (also refused on FORTRESS), `gametest/TurretSystemsTests.java:386-389` (creative tab also holds T2/T3 Fortress)

**Interfaces:**
- Produces:
  - `BaseKind.FORTRESS`; `BaseKind.of(block)` returns it for `FortressTurretBaseBlock` (checked first).
  - `FortressTurretBaseBlock extends TurretBaseBlock implements Multiblock`:
    - `public static final IntegerProperty CELL` (`"cell"`, 0–8); cell = `(dx + 1) + 3 * (dz + 1)` for dx, dz ∈ {-1, 0, 1} from the core; core = cell 4.
    - `public static BlockPos corePosOf(BlockState state, BlockPos pos)`
    - `public static List<BlockPos> footprint(BlockPos core)` (9 blocks, core first)
    - `public static void placeAt(Level level, BlockPos core, FortressTurretBaseBlock block)` (tests, dev scenes)
    - `public Footprint footprint(BlockPlaceContext ctx)`: null unless the clicked face is UP; the 9 blocks centred on `ctx.getClickedPos()`; blocked cells as in `LargeTurretBaseBlock.footprint`.
    - Parts render INVISIBLE; breaking any cell removes all 9 and drops once (core loot only); `rotate`/`mirror` keep cells consistent (the 3x3 is symmetric: map the cell index, or document why identity is right).
  - `BastionBlocks.FORTRESS_TURRET_BASE`, `BastionItems.FORTRESS_TURRET_BASE`, `BastionItems.DAMAGED_FORTRESS_TURRET_BASE`.
  - `BastionConfig.FORTRESS_HEALTH_MULTIPLIER`.
  - `TurretHitboxEntity.bind(BlockPos base, Vec3 center, Direction facing, BaseKind kind)` replacing the boolean (spawn data carries the kind's ordinal).
  - Lang keys: `block.bastion.fortress_turret_base`, `item.bastion.damaged_fortress_turret_base`, `tooltip.bastion.fortress_turret_base`, `tooltip.bastion.damaged_fortress_turret_base`, `tooltip.bastion.weapon.fits_fortress`, `message.bastion.turret.needs_fortress_base` (wording modelled on the large keys).

- [ ] **Step 1: Write the failing tests** in `FortressBaseTests` (`@GameTestHolder`, `@PrefixGameTestTemplate(false)`, template `"range"`, core at `new BlockPos(5, 2, 5)`; helper `fortress(helper)` = `placeAt` + core block entity). Each test is its own batch (multiblock tests: wait until formed).
  - `fortressBaseIsOneBlock`: all 9 cells hold the block and `TurretBaseBlock.core(level, cell) == turret`; `turret.mountCenter()` equals `Vec3.atCenterOf(core)`; destroying the corner cell `core.offset(1, 0, 1)` leaves 9 air and exactly one item drop, `FORTRESS_TURRET_BASE`.
  - `fortressFootprintIsWhatGetsPlaced` (pattern: `TurretPlacementTests.largeBaseFootprintIsWhatGetsPlaced`): a side-face click gives `null`; an UP click gives a placeable 9-block footprint centred on the clicked position; placing it fills exactly those 9; with a stone block in a corner cell the footprint lists that cell as blocked and `placeable()` is false.
  - `fortressTakesOnlyHugeModules`: the Fortress refuses `GUN_TURRET`, `MISSILE_LAUNCHER_TURRET`, `COBBLESTONE_TURRET` in the weapon slot; an unregistered `WeaponModuleItem` of kind FORTRESS (construct it in the test, register nothing) is accepted by the Fortress and refused by a standard and a large base. (If the constructor cannot work unregistered, ledger it and assert through the kind check.)
  - `fortressHealthFollowsConfig`: `maxHealth(T1/T2/T3, FORTRESS, …)` equals the standard value × `FORTRESS_HEALTH_MULTIPLIER.get()` (500 / 1250 / 2500 with defaults) and a placed T1 Fortress starts at 500.
  - `destroyedFortressDropsItsDamagedSelf`: a T3 Fortress with a module in a modifier slot and ammo, `takeDamage` past its HP → all 9 cells air; drops one `DAMAGED_FORTRESS_TURRET_BASE` whose BlockEntityTag Tier is T3, the module drops, the weapon and ammo do not.
  - `minedFortressKeepsItsContents`: breaking the core of a T2 Fortress as a survival player (pattern of the existing large/standard mined tests) drops one `FORTRESS_TURRET_BASE` carrying its tier, weapon slot and ammo; placing that item again (`placeAt` + the item's BlockEntityTag, or a fake player's `place`) restores T2 and the contents (the save/load round trip the spec asks for).
  - `redstoneOnAnyFortressBlockCounts`: a redstone block next to an outer corner cell (not touching the core) switches the turret off exactly as one next to the core does.
  - `fortressHitboxSpansItsFootprint`: once armed, the bound `TurretHitboxEntity` is 2.95 wide.
  - `tierKitWorksOnAnyFortressBlock`: using a T2 kit on an outer cell upgrades the core to T2.
- [ ] **Step 2: Run** `./gradlew runGameTestServer --offline > build/gt-fortress-t1-red.log 2>&1; echo "exit $?"`. Expected: compile errors first (no class), then each new test failing for its own reason. Ledger the RED.
- [ ] **Step 3: Implement** the block, registrations, kind cases, config, HP switch, destroy (damaged item per kind, event scale 2.0, blast `detonate(4.5f, 6f, 1.4f)` for FORTRESS), hitbox kind, redstone over `FortressTurretBaseBlock.footprint(core)`, render-box inflate 8, tooltips, message, loot table, pickaxe tag, lang, Dev command base, the two existing tests' lists. Use exhaustive `switch (kind)` instead of ternaries wherever a `large()` ternary stood ([RISK] lines in the map). Keep the `large()` shims only where callers still need them.
- [ ] **Step 4: Run** the GameTest server. Expected: `All N required tests passed` (N = 284 + the new tests). `./gradlew build compileDevJava --offline` passes.

### Task 2: Fortress model, rendering and client cases

**Files:**
- Create: `tools/blockout/design_fortress_base.py`, `geo/fortress_turret_base.geo.json`, `animations/fortress_turret_base.animation.json` (defines `animation.turret_base.idle` like the large one), `textures/block/fortress_turret_base.png` + `_e.png`, `textures/item/damaged_fortress_turret_base.png`, `blockstates/fortress_turret_base.json`, `models/block/fortress_turret_base.json` (`loader: bastion:turret_base`), `models/item/fortress_turret_base.json` (from `tools/fit_item_display.py`, `SIZE_FACTOR` 0.85), `models/item/damaged_fortress_turret_base.json`
- Modify: `tools/gen_textures.py` (material map for the new bones, damaged sprite "small damaged base three times" or similar, TEXTURES entries), `tools/fit_item_display.py` (MODELS + SIZE_FACTOR), `client/render/BakedTurretBaseModel.java` (skip non-core cells via `CELL != 4`; the core uses the centred 1x1 path), `client/overwatch/OverwatchClient.java` (camera per kind: FORTRESS above 5, behind 8), `client/TurretFireEffects.java:53` (fallback per kind, FORTRESS 100), `client/render/TurretBaseRenderer.java` (only if the centred core needs nothing: then leave it and ledger that)

**Interfaces:**
- Consumes: Task 1's `BaseKind.FORTRESS`, `FortressTurretBaseBlock.CELL`.
- Produces: geo bones `root, plinth, body, glow_windows, status_light, deck, energy_ring, weapon_mount (pivot y 16, centred), armor_t2, fins_t3, glow_fins_t3` (+ any extra visual bones, all mapped in `gen_textures.py`); part bones Task 3's `parts.json` entry can reuse: name two bones that together read as a frame (e.g. `body` and `buttress`).

- [ ] **Step 1: Model** with `tools/blockout` (`Kit(256)`; origin = centre of the core block on the floor, front north; footprint ±24 px). Shape from the spec and the user's reference ("Weapons Platform Large", in the session scratchpad as `images/2.png`): a low, wide platform ~16 px tall; a raised octagonal core in the middle with a round hatch, the `weapon_mount` turntable on top at y 16; four armoured buttresses on the corners sloping down to the floor, a glow strip along each buttress edge (`glow_windows`; the front one is `status_light`); T2 (`armor_t2`) adds the vented side blocks between the buttresses with armour plates; T3 (`fins_t3` + `glow_fins_t3`) adds banks of vertical cooling fins on the buttresses with a glowing cap strip. Gunmetal palette, one cyan glow (the same materials as `large_turret_base`). Run only the new script (never `design_weapons.py`).
- [ ] **Step 2: Preview** T1, T2 and T3 with the session's `preview.py` approach (or an equivalent offline render) from 4 angles; check the silhouette reads as the reference (low, wide, sloped corners) and nothing pokes past the 3x3 footprint by more than 2 px. Save the renders in the workspace and name them in the report.
- [ ] **Step 3: Textures and item model**: generate only the new textures (single-texture calls, constraints.md), run `fit_item_display.py`, then `git checkout` the two `*_turntable.json` item models it rewrites (HANDOFF tool drift).
- [ ] **Step 4: Client cases** (BakedTurretBaseModel, Overwatch camera, fire-effects fallback).
- [ ] **Step 5: Verify** `./gradlew build compileDevJava --offline` passes and the GameTest suite stays green (no server change expected). `git diff --stat` shows no other geo/texture/item-model changes.

### Task 3: Recipes, part, Feed Hub and tool integrations

**Files:**
- Modify: `tools/gen_recipes.py` (special cases; rerun), `registry/BastionItems.java` (`fortress_frame` in the `PARTS` list), `assets/bastion/parts.json` (`fortress_frame` → fortress geo/texture, the frame bones from Task 2), `models/item/fortress_frame.json` (fit_item_display), `turret/FeedHubBlockEntity.java:263-283` (a FORTRESS branch: fed only when all 9 cells stand on blocks of the same structure), `turret/TurretConfiguratorItem.java:32` (resolve the core through `TurretBaseBlock.core`), `client/compat/jei/BastionJeiPlugin.java:66` (info page) + lang `jei.bastion.info.fortress_turret_base`, lang `item.bastion.fortress_frame`, the Feed Hub tooltip/JEI text (`:357`, `:494`: large bases on a 2x2 or wider, fortress bases on a 3x3), both lang files
- Test: `gametest/FortressBaseTests.java` (new tests below)

**Interfaces:**
- Consumes: Task 1 (block, items, `placeAt`, `footprint`), Task 2 (geo bones for the part model).
- Produces: `BastionItems.FORTRESS_FRAME`; recipe files `workstation/part_workstation/fortress_frame.json`, `workstation/part_assembler/fortress_turret_base.json`, `workstation/part_assembler/fortress_turret_base_from_damaged.json`.

- [ ] **Step 1: Write the failing tests**:
  - `fortressRecipesAreMixed`: the level's recipe manager holds a Part Assembler recipe for `fortress_turret_base` with exactly {fortress_frame 1, large_base_plating 2, large_base_housing 1, large_base_turntable 1}, a Part Workstation recipe for `fortress_frame` with {iron_block 4, diamond 1, redstone_block 1}, and the repair recipe {damaged_fortress_turret_base 1, repair_kit 3}; the Large Turret Base recipe is unchanged (one of each heavy part).
  - `feedHubFeedsFortressOnlyOnAFull3x3` (pattern: `TurretFeedHubTests.feedHubFeedsLargeBase`): a Fortress on a 3x3x1 structure is in the hub's fed list; one on a 2x2x1 structure (standing partly off it) is not. Assert list membership: no huge weapon exists to take ammo (the map, §3).
  - `configuratorWorksOnAnyFortressBlock`: a configured Turret Configurator used on an outer cell applies its settings to the core (pattern: the existing configurator test on a standard base).
  - `fortressPartsLeadToTheCore` (pattern: `CompatTests.largeBasePartsLeadToTheCore`): Jade `BlockInfo` lines for an outer cell equal the core's, with tier and HP.
- [ ] **Step 2: Run RED** (log `build/gt-fortress-t3-red.log`).
- [ ] **Step 3: Implement**; `python3 tools/gen_recipes.py` and confirm `git diff --stat` on `data/bastion/recipes` shows only the three new files (the generator rewrites the whole folder).
- [ ] **Step 4: Run GREEN** + `./gradlew build compileDevJava --offline`.

### Task 4: Showcase, checklist, docs and version

**Files:**
- Create: `src/dev/java/dev/bastion/dev/FortressShowcase.java`, `docs/release/DESCRIPTION_UPDATE_0.5.0.md`
- Modify: `src/dev/java/dev/bastion/dev/Showcase.java` (exclusion list + wiring, pattern `EarlyShowcase`), `gradle.properties` (`mod_version=0.5.0`), `docs/TESTING.md` (section "Fortress Turret Base (0.5.0)"), `docs/HANDOFF.md` §0, `docs/release/UPLOAD.md` ("Changelog: 0.5.0" block above 0.4.0's, header to 0.5.0), `docs/release/DESCRIPTION.md` + `DESCRIPTION.curseforge.html` (Turret bases paragraph: the Fortress)

- [ ] **Step 1: Scene** `-Pshowcase=fortress` (self-quitting): T1, T2 and T3 Fortress side by side by day and by night; one at HP < 50 % (yellow) and one < 25 % (red); the placement hologram free and blocked; a Fortress on a 3x3 Feed Hub; the item in hand and in the hotbar; a destroyed one (blast, drop). Screenshot names `showcase_fortress_*`.
- [ ] **Step 2: Run** it (constraints.md showcase steps) and read every frame: tiers distinct, glow strips light and change colour with HP, hologram is 3x3, nothing floats or clips. Ledger findings; fix what is clearly wrong; leave taste calls to the user.
- [ ] **Step 3: Docs**: TESTING checklist (placement, tiers, HP colours, destroy/repair, mined, Feed Hub, Control Hub, Overwatch, Jade/JEI, recipes; "no huge weapon yet" note); HANDOFF §0 state + my calls; changelog 0.5.0 (New: Fortress Turret Base; Changes: Turret Configurator works on any block of a large/fortress base; Note: no weapon fits it yet, the first 3x3 turrets come later); DESCRIPTION_UPDATE_0.5.0 written against the 0.4.0 text.
- [ ] **Step 4: Verify** build, compileDevJava, full GameTest suite green.
