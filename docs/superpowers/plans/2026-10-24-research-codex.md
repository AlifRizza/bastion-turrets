# Research & Turret Codex Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (user's choice: native, 2026-10-24 14:23) to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 0.6.0: survival players unlock Bastion's recipes node by node in a research tree (pay items, parents first), read about everything in one item, the Turret Codex (three tabs: Turret, Workshop, Modul), and see research in JEI.

**Architecture:** The tree is a static Java table (`ResearchTree`, ~64 nodes: id, tab, grid cell, parents, cross-tab requires, unlocked item ids, cost tier). Per-world state is one `SavedData` on the overworld; the client holds a synced copy (`ResearchSync`). One rule locks everything: a recipe is locked when its result item is unlocked by no researched node (`Research.unlocked(level, item)`), checked in `WorkstationBlockEntity.recipe()/selectRecipe` and in a `bastion:research_shaped` crafting serializer for the five stations. Costs and the on/off switch live in `config/bastion-research.toml`. The Codex screen is code-drawn (FlatButton palette), client package only.

**Tech Stack:** Forge 1.20.1 (47.4.26), Java 17, GeckoLib 4.8.4, JEI 15.20 (compileOnly), GameTest; Python generators in `tools/`.

**Spec:** `docs/PLAN.md` §8 "Riset & Turret Codex" (approved 2026-10-24 14:28). Tree source: the user's diagram `~/Downloads/Untitled Diagram.png` + the 5 approved changes (spec "Tab Turret"). Workspace/ledger: `.superpowers/sdd/2026-10-24-research-codex/`.

## Global Constraints
- Everything in `.superpowers/sdd/2026-10-23-javelin-missile/constraints.md` holds: **no commits** (the user says "commit"), never touch `modlist.html`, `sfx/`, `run/saves/showcase`, `run/config/*`; Gradle `--offline`, every run logged to a file, read with `rtk proxy grep`; showcase only self-quitting in a `run/saves/showcase_fx` copy after a screen-lock check; generators that rewrite everything (`design_weapons.py`, `gen_structures.py`, `gen_textures.py`, `fit_item_display.py`) go to the scratchpad and only the needed file is copied; lang always en_us + id_id; client code only in `dev.bastion.client..`; balance numbers in config.
- GameTest baseline before this plan: **391** (`All 391 required tests passed`). Every test class here uses the codebase pattern `@GameTestHolder(Bastion.MOD_ID) @PrefixGameTestTemplate(false)`, template `arena`, `batch` = the test's own name (batches run one after another, so a test may change the world's research state if it restores it in a `finally`).
- Node ids are plain strings (paths in `bastion`); turret node ids are the weapon ids of `TurretStatsConfig.WEAPONS` (`tesla` for the Tesla Coil).
- Free nodes (always researched, never in `[costs]`, never in JEI's Research category): `cobblestone`, `snowball`, `potato`, `arrow_launcher`, `makeshift_turret_base`, `turret_codex`.
- Cost tiers (defaults, spec "Biaya default"): R1 `minecraft:iron_ingot 8`, `minecraft:copper_ingot 8`, `minecraft:redstone 4`; R2 `minecraft:iron_ingot 16`, `minecraft:redstone 8`, `minecraft:gold_ingot 4`; R3 `minecraft:iron_block 4`, `minecraft:gold_ingot 8`, `minecraft:diamond 4`; R4 `minecraft:iron_block 8`, `minecraft:diamond 8`, `minecraft:netherite_scrap 2`.
- Colours: researched = the mod cyan (`FlatButton.ACCENT`'s family, same CYAN as `WorkstationScreen`), not researched = red `0xFFFF5A4F` (the Control Hub's offline red), researchable now = that red pulsing (alpha 0.45–1.0, 1 s period).
- `BastionNetwork.PROTOCOL` "13" → "14".

## Review Focus
1. **A cost spread over several stacks** (8 iron as 5 + 3 in two slots, or in the offhand): research takes exactly 8 across them. Test `researchTakesFromSeveralStacks` (Task 1).
2. **A double click** (two `ResearchRequest`s in one tick): items are taken once, the second answers ALREADY. Test `researchTwiceTakesItemsOnce` (Task 1).
3. **A recipe selected before the lock** (a station kept `gun_barrel` selected, then research was switched back on): it stops being processed and filled. Test `lockedSelectedRecipeStops` (Task 3).
4. **The Codex opened before the first `ResearchSync`** (laggy join, or a client on a server whose sync is late): the screen draws from defaults (everything not free shows red, costs = mod defaults), no NPE. Opened at scene tick 1 in `CodexShowcase` (Task 4) + a TESTING.md dedicated-server line.
5. **A config typo** (`minecraft:iron_ingt 8`, `minecraft:diamond 0`): that entry is skipped with one log warning, the other entries still cost. Test `costSkipsBadEntries` (Task 1).

---

### Task 1: Tree, state, rules, config, the Codex item (server)

**Files:**
- Create: `src/main/java/dev/bastion/research/ResearchNode.java`, `ResearchTree.java`, `ResearchData.java`, `Research.java`, `TurretCodexItem.java`; `src/main/java/dev/bastion/config/ResearchConfig.java`; `src/main/java/dev/bastion/gametest/ResearchTests.java`.
- Modify: `Bastion.java` (register `ResearchConfig.SPEC` as COMMON with `ResearchConfig.FILE`, add its `onReloading` listener), `registry/BastionItems.java` (`TURRET_CODEX`, falls into the main `TAB` by its predicate), `tools/gen_recipes.py` (shapeless codex: 1 `minecraft:book`, 1 `minecraft:iron_ingot`, 1 `minecraft:redstone` → `data/bastion/recipes/turret_codex.json`), `tools/gen_textures.py` (`item/turret_codex.png`: gunmetal book, cyan glyph on the cover; generate in the scratchpad, copy only that file), `assets/bastion/models/item/turret_codex.json` (`item/generated`), lang (`item.bastion.turret_codex` "Turret Codex" / "Kodeks Turret", `tooltip.bastion.turret_codex` "Right-click: every turret, base, station and module, and the research tree" / "Klik kanan: semua turret, base, stasiun dan modul, serta tree riset").

**Interfaces (Produces):**
- `enum ResearchNode.Tab { TURRET, WORKSHOP, MODULE }`, `enum ResearchNode.Tier { FREE, R1, R2, R3, R4; List<String> defaultCost() }`.
- `record ResearchNode(String id, Tab tab, int col, int row, List<String> parents, List<String> requires, List<String> unlocks, Tier tier)` with `Item icon()` (the first unlock), `List<Item> unlockItems()`, `boolean free()`, `Component name()` (`research.bastion.<id>` for `workstations` and `control_hub`, else `icon().getDescription()`).
- `ResearchTree`: `static final List<ResearchNode> ALL`; `@Nullable static ResearchNode get(String id)`; `static List<ResearchNode> unlocking(Item item)` (lazy item → nodes map); `static List<ResearchNode> children(String id)`; `static List<ResearchNode> tab(Tab tab)`.
- `ResearchData extends SavedData` (`bastion_research` in the overworld's data storage, NBT list `Researched`): `static ResearchData get(MinecraftServer server)`; `static Set<String> initial(long overworldGameTime, boolean gameTestServer)` (all node ids when time > 0 or a GameTest server, else empty); `boolean has(String id)`, `void add(String id)`, `void remove(String id)`, `void set(Set<String> ids)`, `Set<String> all()`.
- `Research` (common; the client copy is a static set by `ResearchSync` in Task 2): `static boolean enabled(Level level)`; `static boolean researched(Level level, String id)` (free or disabled → true); `static boolean unlocked(Level level, Item item)` (no node unlocks it, or any of them researched); `static boolean available(Level level, ResearchNode node)` (not researched, all parents and requires researched); `static List<ItemStack> cost(Level level, String id)`; `enum Outcome { DONE, DISABLED, UNKNOWN, ALREADY, LOCKED, MISSING_ITEMS }`; `static Outcome research(ServerPlayer player, String id)` (checks in that order; creative pays nothing; survival: count over the whole inventory incl. offhand, take only when all are there; on DONE: `add` + `setDirty`; Task 2 adds the sync, chat and sound behind a `Research.onResearched(ServerPlayer, ResearchNode)` hook).
- `ResearchConfig`: `FILE = "bastion-research.toml"`; `ENABLED` (true; read through `static boolean enabled()` = `!SPEC.isLoaded() || ENABLED.get()`, since a config value read before load throws), `GIVE_CODEX` (true, key `giveCodexOnFirstJoin`); one `[costs]` key per non-free node (list of strings, default = its tier's list, comment: the node's English name + `Mod default: ...`); `static ItemStack parse(String entry)` ("ns:item count", count optional = 1, unknown item or count outside 1–4096 → `ItemStack.EMPTY`); `static List<ItemStack> cost(String id)` (parsed, bad entries skipped with one `LOGGER.warn` per entry per load); `static void onReloading(ModConfigEvent.Reloading)` (server thread → `Research.syncAll(server)`, a no-op until Task 2).
- `TurretCodexItem extends Item` (`stacksTo(1)`); `use` returns `InteractionResultHolder.sidedSuccess` (Task 4 opens the screen).

**The tree** (spec "Tab Turret/Workshop/Modul"; cells `col,row`, row 0 at the bottom; tier in brackets). Turret nodes also `requires` their base: STANDARD → `turret_base`, LARGE → `large_turret_base`, FORTRESS → `fortress_turret_base` (free early turrets require nothing).

| id | tab | cell | parents | unlocks (item ids) | tier |
|---|---|---|---|---|---|
| cobblestone / snowball / potato / arrow_launcher | TURRET | 1,0 / 2,0 / 4,0 / 7,0 | – | cobblestone_turret / snowball_repeater / potato_shotgun / arrow_launcher | FREE |
| gun | TURRET | 1,1 | cobblestone | gun_turret, gun_barrel, gun_receiver, gun_mount, kinetic_rounds | R1 |
| sniper | TURRET | 1,2 | gun | sniper_turret, sniper_barrel, sniper_receiver, sniper_scope_mount, sniper_rounds | R2 |
| laser_rifle | TURRET | 1,3 | sniper | laser_rifle_turret, laser_focus_barrel, laser_emitter_crystal, laser_housing, laser_cell, empty_laser_cell, charging_station | R2 |
| railgun | TURRET | 1,4 | laser_rifle | railgun_turret, railgun_rails, railgun_receiver, railgun_mount, rail_slugs | R3 |
| ion_rifle | TURRET | 0,3 | gun | ion_rifle_turret, ion_barrel, ion_capacitor, ion_mount | R2 |
| tesla | TURRET | 0,4 | ion_rifle | tesla_turret, tesla_coil, tesla_crown, tesla_capacitor_bank | R3 |
| machine_gun | TURRET | 2,1 | snowball | machine_gun_turret, machine_gun_barrels, machine_gun_receiver, machine_gun_mount, kinetic_rounds | R1 |
| plasma_repeater | TURRET | 2,3 | machine_gun | plasma_repeater_turret, plasma_barrel, plasma_chamber, repeater_mount, plasma_cell, empty_plasma_cell, charging_station | R2 |
| plasma_cannon | TURRET | 2,4 | plasma_repeater | plasma_cannon_turret, plasma_cannon_core, plasma_cannon_barrel, plasma_cannon_mount, plasma_cell, empty_plasma_cell | R3 |
| shotgun | TURRET | 4,1 | potato | shotgun_turret, shotgun_barrels, shotgun_receiver, shotgun_mount, scatter_shells | R1 |
| flamethrower | TURRET | 3,2 | shotgun | flamethrower_turret, flamethrower_nozzle, flamethrower_fuel_tanks, flamethrower_housing, fuel_canister | R2 |
| repulsor | TURRET | 5,2 | shotgun | repulsor_turret, repulsor_emitter, repulsor_coil, repulsor_mount | R2 |
| repulsor_dome | TURRET | 5,4 | repulsor | repulsor_dome_turret, dome_core, dome_pylons, dome_mount | R3 |
| rocket_launcher | TURRET | 6,1 | arrow_launcher | rocket_launcher_turret, rocket_pod, rocket_pod_housing, rocket_launcher_mount, rockets | R1 |
| missile_launcher | TURRET | 6,4 | rocket_launcher | missile_launcher_turret, missile_pod, missile_guidance_core, missile_launcher_mount, missiles | R3 |
| javelin | TURRET | 6,5 | missile_launcher | javelin_turret, javelin_silo, javelin_radar, javelin_mount, javelin_missile | R4 |
| mortar | TURRET | 7,4 | arrow_launcher | mortar_turret, mortar_barrel, mortar_housing, mortar_ring, mortar_shells | R3 |
| brood_cannon | TURRET | 7,5 | mortar | brood_cannon_turret, brood_barrel, brood_chamber, brood_mount, brood_capsule | R4 |
| hive_carrier | TURRET | 7,6 | brood_cannon | hive_carrier_turret, hive_hangar, hive_relay, hive_bays, hive_drone | R4 |
| turret_codex / makeshift_turret_base | WORKSHOP | 0,0 / 1,0 | – | turret_codex / makeshift_turret_base | FREE |
| workstations | WORKSHOP | 1,1 | makeshift_turret_base | part_workstation, part_assembler, ammo_workstation, module_workstation | R1 |
| turret_base | WORKSHOP | 1,2 | workstations | turret_base, turret_base_plating, turret_base_housing, turret_base_turntable, repair_kit, turret_configurator | R1 |
| large_turret_base | WORKSHOP | 0,3 | turret_base | large_turret_base, large_base_plating, large_base_housing, large_base_turntable | R2 |
| fortress_turret_base | WORKSHOP | 0,4 | large_turret_base | fortress_turret_base, fortress_frame | R3 |
| tier_upgrade_kit_t2 / _t3 | WORKSHOP | 1,3 / 1,4 | turret_base / tier_upgrade_kit_t2 | the kit itself | R2 / R3 |
| feed_hub | WORKSHOP | 2,3 | turret_base | feed_hub | R2 |
| control_hub | WORKSHOP | 3,3 | turret_base | hub_interface, hub_casing, hub_power_port, satellite_uplink_t1, satellite_uplink_t2, satellite_uplink_t3, hub_connector | R3 |
| overwatch_link | WORKSHOP | 3,4 | control_hub | overwatch_link | R3 |
| `<m>`, `<m>_mk2`, `<m>_mk3` | MODULE | i,0 / i,1 / i,2 | – / `<m>` / `<m>_mk2` | the module item of the same id | R1 / R2 / R3 |

Modules `m` by column i: 0 range_module, 1 damage_amplifier, 2 overclock, 3 rapid_cycler, 4 targeting_ai, 5 penetrator, 6 ammo_recycler, 7 coolant_loop, 8 choke_module, 9 autoloader. Mk1 `requires`: `workstations`, except choke_module → `shotgun`, autoloader → `rocket_launcher`.

- [ ] **Step 1: Write the failing tests** in `ResearchTests` (helper `withResearch(GameTestHelper, Set<String> start, Runnable body)`: saves `ResearchData.all()`, `set(start)`, runs, restores in `finally`, then `succeed()`; FakePlayer from `FakePlayerFactory`, survival unless stated):
  - `treeIsWhole`: ids unique; every parent exists and shares the tab; every require exists and sits in another tab; no cycles (DFS over parents + requires); `(tab, col, row)` unique; every unlock id is a registered item; every result of a `bastion:workstation`, `minecraft:crafting_shaped`/`shapeless` or (Task 3) `bastion:research_shaped` recipe with namespace `bastion` is unlocked by ≥ 1 node.
  - `turretsNeedTheirBase`: for each `WeaponModuleItem` of a non-free node, `requires` contains the base node for its `kind()`.
  - `researchNeedsParentsAndItems`: start `{}` → `research(player, "sniper")` == LOCKED; start `{workstations, turret_base, gun}`, empty inventory → MISSING_ITEMS and the inventory is unchanged; give 16 iron + 8 redstone + 4 gold + 1 extra iron → DONE, `researched(level, "sniper")`, inventory holds exactly 1 iron; again → ALREADY; `research(player, "nope")` == UNKNOWN.
  - `researchTakesFromSeveralStacks`: R1 node `gun` with iron 5 + 3 in two slots (and copper in the offhand) → DONE, no iron or copper left.
  - `researchTwiceTakesItemsOnce`: twice the R1 cost in the inventory, two `research` calls in a row → DONE then ALREADY, exactly one cost taken.
  - `creativeResearchIsFree`: creative player, empty inventory, start `{workstations}` → `research(player, "turret_base")` == DONE.
  - `sharedAmmoOpensWithEither`: start `{workstations, turret_base}` → `unlocked(kinetic_rounds)` false; add `machine_gun` → true; Charging Station opens with `plasma_repeater` alone.
  - `newWorldsStartEmpty`: `ResearchData.initial(0, false)` empty; `initial(100, false)` and `initial(0, true)` == every node id.
  - `disabledOpensEverything`: `ENABLED.set(false)` → `unlocked(level, railgun_turret)` true and `research(...)` == DISABLED; restore in `finally`.
  - `costSkipsBadEntries`: `parse("minecraft:iron_ingot 8")` == 8 iron; `parse("minecraft:iron_ingot")` == 1 iron; `parse("minecraft:iron_ingt 8")`, `parse("minecraft:diamond 0")`, `parse("minecraft:diamond 5000")` empty; a node whose list is `["minecraft:iron_ingot 2", "bad:item 1"]` costs exactly 2 iron; an empty list → DONE with nothing taken.
- [ ] **Step 2: Run** `./gradlew runGameTestServer --offline > build/gt-t1.log 2>&1; echo "exit $?"` → compile errors / the new tests fail.
- [ ] **Step 3: Implement** the files above. `ResearchData.get` uses `server.overworld().getDataStorage().computeIfAbsent(load, () -> new ResearchData(initial(server.overworld().getGameTime(), server instanceof GameTestServer)), "bastion_research")`; Task 2 calls it at `ServerStartedEvent` so `initial` sees the time before the first tick. Item taking: `Inventory.clearOrCountMatchingItems` per cost stack (simulate first for every stack, then take).
- [ ] **Step 4: Run** the suite → `All 401 required tests passed` (391 + 10); `./gradlew build --offline > build/build-t1.log 2>&1` exit 0.

### Task 2: Sync, requests, command, first-join Codex, chat and sound

**Files:**
- Create: `network/ResearchSync.java`, `network/ResearchRequest.java`, `research/ResearchEvents.java` (`@Mod.EventBusSubscriber`, forge bus), `research/ResearchCommand.java`.
- Modify: `network/BastionNetwork.java` (both packets after `OverwatchLocks`, PROTOCOL "14"), `research/Research.java` (client copy + `syncAll` + `onResearched`), `registry/BastionSounds.java` (`RESEARCH_COMPLETE = register("research.complete")`), `assets/bastion/sounds.json`, `tools/gen_sounds.py` (placeholder `research_complete`: a rising two-note chime, 0.8 s), `docs/SOUND_SHEET.md` (row 90, `research.complete`, P2), lang (`chat.bastion.research.done` "%s researched %s" / "%s meriset %s", `subtitles.bastion.research.complete`, `commands.bastion.research.*`).

**Interfaces:**
- Consumes: Task 1's `Research`, `ResearchData`, `ResearchTree`, `ResearchConfig`.
- Produces: `record ResearchSync(boolean enabled, Set<String> researched, Map<String, List<ItemStack>> costs)` with `static ResearchSync current(MinecraftServer)`, `encode/decode/handle` (handle → `Research.acceptClient(this)`); `record ResearchRequest(String node)` with `handle` → `Research.research(sender, node)`; `Research.syncAll(MinecraftServer)`; `Research.client()` → the last `ResearchSync` or a default one (enabled, nothing researched, default costs); `ResearchEvents.giveCodexOnce(ServerPlayer)` (persisted flag `bastion_codex_given` in `PERSISTED_NBT_TAG`; only when `GIVE_CODEX` and `ENABLED`); `ResearchCommand.register(RegisterCommandsEvent)`: `/bastion research grant <node|all>`, `revoke <node|all>`, `list`, permission 2, node suggestions from `ResearchTree.ALL`.
- Events: `ServerStartedEvent` → `ResearchData.get(server)`; `PlayerLoggedInEvent` → send `ResearchSync.current` to that player + `giveCodexOnce`; `onResearched`: `syncAll`, `broadcastSystemMessage(chat.bastion.research.done, player name, node.name())`, `player.playNotifySound(RESEARCH_COMPLETE, SoundSource.PLAYERS, 1, 1)`; grant/revoke also `syncAll`.

- [ ] **Step 1: Failing tests** (add to `ResearchTests`): `researchSyncRoundTrips` (encode → decode equals, incl. a cost map with 2 stacks); `codexGivenOnce` (`giveCodexOnce` twice → exactly one `turret_codex` in the inventory); `commandGrantsAndRevokes` (`server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withPermission(2), "bastion research grant gun")` → researched; `revoke gun` → not; `grant all` → every node); `requestPacketResearches` (`new ResearchRequest("turret_base").handle`-path via a helper `ResearchRequest.apply(ServerPlayer)` with the cost in the inventory → researched).
- [ ] **Step 2: Run** → fail.
- [ ] **Step 3: Implement.** The client copy is a plain static in `Research` (no client classes), replaced by `acceptClient`; on an integrated server both sides read their own copy.
- [ ] **Step 4: Run** → `All 405 required tests passed`; `build` exit 0. Generate the sound with `python3 tools/gen_sounds.py` into the scratchpad and copy only `sounds/research/complete.ogg`.

### Task 3: Locks (recipes, station GUI, tooltips)

**Files:**
- Create: `research/ResearchShapedRecipe.java`, `client/ResearchTooltips.java` (`@Mod.EventBusSubscriber(value = Dist.CLIENT)`, `ItemTooltipEvent`).
- Modify: `registry/BastionRecipes.java` (`RESEARCH_SHAPED_SERIALIZER = SERIALIZERS.register("research_shaped", ...)`), `tools/gen_recipes.py:229` (station recipes `"type": "bastion:research_shaped"`) and the 5 regenerated `data/bastion/recipes/{part_workstation,part_assembler,ammo_workstation,module_workstation,charging_station}.json` (check `git diff --stat` shows only those + the codex), `workstation/WorkstationBlockEntity.java:115-136` (`recipe()` filters `Research.unlocked(level, w.result().getItem())`; `selectRecipe` refuses a locked recipe), `client/screen/WorkstationScreen.java` (`renderList`: locked rows drawn at 40 % alpha with a 5x6 code-drawn padlock at the right; tooltip adds `gui.bastion.workstation.locked` "Research %s in the Turret Codex" in red; `mouseClicked` ignores locked rows), lang (`gui.bastion.workstation.locked`, `tooltip.bastion.not_researched` "Not researched: %s" / "Belum diriset: %s").

**Interfaces:**
- Consumes: `Research.unlocked`, `ResearchTree.unlocking`.
- Produces: `class ResearchShapedRecipe extends ShapedRecipe` (`matches(CraftingContainer, Level)` = `super.matches && Research.unlocked(level, result item)`; `getSerializer` = ours); its `Serializer` delegates `fromJson/fromNetwork/toNetwork` to `RecipeSerializer.SHAPED_RECIPE`. Tooltip: only Bastion items, only when `Research.client().enabled()` and `!Research.unlocked(level, item)`: one red line naming the first node of `ResearchTree.unlocking(item)`.

- [ ] **Step 1: Failing tests:** `workstationRefusesLockedRecipes` (start `{workstations, turret_base}`: Part Workstation `selectRecipe(gun_barrel)` → `recipeId()` null; add `gun` → selected); `lockedSelectedRecipeStops` (start all; select `gun_barrel`; `set({workstations, turret_base})` → `recipe()` null, `fill(player, 1)` == 0 with the iron in the inventory); `stationCraftingNeedsResearch` (a 3x3 `TransientCraftingContainer` filled with the Part Workstation pattern: `getRecipeFor(RecipeType.CRAFTING, …)` empty at start `{}`, present at `{workstations}`); `earlyRecipesStayOpen` (start `{}`: the cobblestone turret pattern still matches).
- [ ] **Step 2: Run** → fail.
- [ ] **Step 3: Implement**, regenerate recipes with `python3 tools/gen_recipes.py`.
- [ ] **Step 4: Run** → `All 409 required tests passed` (the existing workstation/JEI-fill tests still pass: the GameTest world starts fully researched); `build` exit 0.

### Task 4: The Codex screen (client)

**Files:**
- Create: `client/screen/CodexScreen.java` (layout, tabs, input, research button), `client/screen/CodexTree.java` (pan state, cell → pixel, node and line drawing, hit test), `client/screen/CodexPage.java` (right panel), `client/compat/jei/JeiBridge.java` (holds `IJeiRuntime` from `BastionJeiPlugin.onRuntimeAvailable`; `static void showRecipes(ItemStack)`); `src/dev/java/dev/bastion/dev/CodexShowcase.java`.
- Modify: `research/TurretCodexItem.java` (`use`, client side: `DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> CodexScreen::open)`), `client/compat/jei/BastionJeiPlugin.java` (`onRuntimeAvailable`), lang (`gui.bastion.codex.*`: `tab.turret` "Turrets"/"Turret", `tab.workshop` "Workshop"/"Workshop", `tab.module` "Modules"/"Modul", `research` "Research"/"Riset", `researched` "Researched"/"Sudah diriset", `needs` "Needs:"/"Butuh:", `cost` "Cost"/"Biaya", `unlocks` "Unlocks"/"Membuka", `recipe` "Recipe"/"Resep", `damage`/`fire_rate`/`range`, `disabled` "Research is off on this server"/"Riset dimatikan di server ini").

**Interfaces:**
- Consumes: `ResearchTree`, `Research.client()`, `Research.researched/available/cost` (client level), `ResearchRequest`, `WeaponDataLoader.all()`, `WeaponModuleItem.data()/kind()`.
- Produces: `public CodexScreen(ResearchNode.Tab tab, @Nullable String selected)`; `static void open()` (last tab/selection of this session, else TURRET / `gun`); `CodexScreen.select(String id)` and `tab(Tab)` for the showcase.

Layout (spec "Turret Codex"): window minus 8 px each side, at least 320x200 GUI px; left panel 60 % wide. Tabs: three `FlatButton`s in a row at the top of the left panel. Tree: cell step 34 x 30 px, card 22x22 with the 16 px icon, row 0 at the bottom, opens scrolled so row 0 sits at the panel's bottom; drag (left button) and scroll pan, clamped to the tab's bounds; lines: parent top-centre up to half a step under the child, across, up into the child's bottom-centre, 1 px, cyan when the parent is researched, else `0x80FF5A4F`; requires not yet researched: their icons at 50 % size on the card's top-right corner; hover → tooltip (name + researched / researchable / locked). Page (right panel, scrolls when taller): name; the icon item rendered at 3x scale turning 20°/s (drag on it turns it); for a weapon module: the `tooltip.bastion.weapon.fits_*` line, the ammo item icon(s) (`ammo().getItems()`), three bars (damage per shot, shots per second = 20 / fire_interval, range) scaled to the highest of the built-in weapons in `WeaponDataLoader.all()`, "—" for 0; text = the icon item's tooltip lines without its name (`research.bastion.<id>.desc` for `workstations`/`control_hub`); "Unlocks" icons; "Needs:" icons for unresearched parents/requires; Research block: cost rows (icon, `have/need`, red when short; have = player inventory count), a `FlatButton` "Research" active when `available` and (creative or enough) → `ResearchRequest`; or "Researched" in cyan; hidden with the `disabled` line when research is off; "Recipe" button only when `ModList.get().isLoaded("jei")` → `JeiBridge.showRecipes(icon)`. A node researched while open flashes cyan for 10 ticks.

- [ ] **Step 1: Implement** the screen and the item hook (no GameTest: client-only; the dedicated-server GameTest run proves nothing client leaks into common code).
- [ ] **Step 2: Showcase** `CodexShowcase` (`-Pshowcase=codex`, pattern of `WorkshopShowcase`): tick 1 open `new CodexScreen(TURRET, "gun")` before any sync (Review Focus 4); tick 20 server: `/bastion research revoke all`, then grant `workstations, turret_base, gun, sniper, machine_gun`; shots `codex_turret`, `codex_page_railgun` (select railgun), `codex_workshop`, `codex_modules`, `codex_page_choke`; then grant `laser_rifle` and shoot `codex_flash` 3 ticks later; close and quit. Run per the constraints (screen-lock check first; skip and list if locked).
- [ ] **Step 3: Verify** `./gradlew build compileDevJava --offline` exit 0, suite still 409; read the six screenshots (text inside panels, no overlap at the scene's GUI scale, lines not crossing, red/cyan readable).

### Task 5: JEI, docs, release prep, final review

**Files:**
- Create: `client/compat/jei/ResearchCategory.java`.
- Modify: `BastionJeiPlugin` (category, recipes = non-free `ResearchTree.ALL`, Codex info page `jei.bastion.info.turret_codex`), `client/compat/jei/StationCategory.java` (locked recipe: padlock at the top-right + tooltip `gui.bastion.workstation.locked`), lang (`jei.bastion.research` "Research"/"Riset", info text), `gradle.properties` (`mod_version=0.6.0`), `docs/TESTING.md` ("Riset & Turret Codex (0.6.0)"), `docs/release/UPLOAD.md` (changelog 0.6.0), `docs/release/DESCRIPTION_UPDATE_0.6.0.md` + both DESCRIPTION files, `docs/HANDOFF.md` §0, PLAN §8 status line.
- ResearchCategory page (150 x 80): cost slots (INPUT, from `Research.client().costs()` else the tier defaults) left, an arrow, unlock slots (OUTPUT) right, a "Needs" row (parents + requires icons) and a "Leads to" row (children icons) as RENDER_ONLY slots, a status line (researched / not, this world).

- [ ] **Step 1: Implement**; check by `runClient`-free means: `./gradlew build --offline` exit 0, suite 409.
- [ ] **Step 2: Docs** (TESTING list per spec "Manual": new survival world, LAN second player, old world, `enabled = false`, cost edit while running, JEI R/U + Research page, no-JEI run, dedicated server, `/bastion research` from a client — it shares the client `/bastion vfx` root).
- [ ] **Step 3: Final whole-branch review**: a fresh agent on **Opus 5.5, effort xhigh** (never Fable) over the diff since `ee7562a`; one fix pass, each fix RED → GREEN; suite + build green; rerun the showcase if the screen changed.
