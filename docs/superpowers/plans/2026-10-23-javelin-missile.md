# Javelin Missile Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (user's choice: native, 2026-10-23 21:34) to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The first 3x3 turret: the Javelin Missile, a huge (FORTRESS) weapon module that fires one big missile straight up out of sight and drops it vertically onto the densest cluster of valid ground targets under open sky, with a visible target mark, a big valid-targets-only blast, item + FE ammo, and Overwatch drop-point aiming.

**Architecture:** A `JavelinWeapon` type (Railgun's item + FE pattern, Mortar's ground-only/min-range pattern, Missile's entity launch) plus a new `TargetPicker`-style hook so a weapon can choose among candidates with turret context (densest cluster). A `JavelinMissileEntity` with a climb → transfer → dive flight profile, synced target/drop-point data that drives a client mark renderer, and a `Splash`-based blast with centre→edge damage. Client: silo model with doors and radar mast, missile entity model, presets, sounds; Overwatch picks a ground point without the ballistic arc.

**Tech Stack:** Forge 1.20.1 (47.4.26), Java 17, GeckoLib 4.8.4, GameTest; Python generators in `tools/`.

**Spec:** `docs/PLAN.md` §8 "Turret 3x3: Javelin Missile, Brood Cannon, Hive Carrier" (shared rules, Javelin, Detail builder). Template map with file:line for every concern: `<workspace>/big-weapon-map.md`. Workspace: `.superpowers/sdd/2026-10-23-javelin-missile/`.

## Global Constraints
- Everything in `<workspace>/constraints.md` (no commits, `--offline`, logs to files + `rtk proxy grep`, showcase steps, tool drift restores, lang in both files via json round-trip, single-texture generation). Baseline: 301 GameTests.
- Ids: weapon type `bastion:javelin`, JSON `turret_weapons/javelin.json`; module item `javelin_turret` ("Javelin Missile Turret" / "Turret Rudal Javelin"), `BaseKind.FORTRESS`; ammo item `javelin_missile` ("Javelin Missile" / "Rudal Javelin"), tag `bastion:ammo/javelin_missile` (added to `#bastion:ammo`); entity `turret_javelin` ("Javelin Missile"); parts `javelin_silo` ("Javelin Silo" / "Silo Javelin"), `javelin_radar` ("Javelin Radar Mast" / "Tiang Radar Javelin"), `javelin_mount` ("Javelin Mount" / "Dudukan Javelin").
- JSON numbers *(controller, spec)*: `range` 48, `fire_interval` 160, `damage` 40 (blast centre), `ammo_per_shot` 1; params `min_range` 8, `blast_radius` 6, `blast_edge_damage` 15, `energy_capacity` 30000, `max_input` 4000, `energy_per_shot` 10000, `apex` 60, `climb_ticks` 40, `dive_ticks` 20. All params in `TurretStatsConfig.PARAMS` with ranges.
- Targeting: valid ground targets (Mortar `grounded()`), outside `min_range`, whose eye block `canSeeSky`; pick the one with the most valid targets within `blast_radius` (tie: larger summed health, then nearer). Fires only when the block above the silo mouth `canSeeSky`. No flyers. Nether/caves/roofs: never fires.
- Flight: climb vertically to `launchY + min(apex, maxBuildHeight − 4 − launchY)` in `climb_ticks`; at the apex it is moved over the drop point (out of sight); dive vertically in `dive_ticks`, x/z following the target while it stands under open sky, else the last open-sky point. Impact on the first block or entity hit, or on reaching the drop point.
- Blast: `Splash.around(level, at, blast_radius, affects)` hitting only targets the owning turret may shoot (rocket `affects()` pattern, `Enemy` fallback if the turret is gone); damage lerps from `damage` at distance 0 to `blast_edge_damage` at `blast_radius`; no block damage, no LOS; damage type `bastion:turret_blast`.
- NO-VANILLA visuals; the missile never saves (dies with the chunk); render distance at least 128 blocks.
- Overwatch: the crosshair picks a ground point (the mark shows there); a press fires if the point is in range, outside min range and under open sky; the HUD shows the FE bar and reload (known limit: not the missile count).

## Review Focus
1. **Roofs and the Nether**: a target under a roof or a roofed silo never fires; the Nether never fires. Tests `javelinSkipsRoofedTargets`, `javelinNeedsOpenSkyAboveItsSilo` (Task 1).
2. **A target that moves or hides after launch**: the missile follows its x/z, or falls on its last open-sky point. Tests `javelinDropsOnAMovedTarget`, `javelinFallsOnTheLastOpenSkyPoint` (Task 2).
3. **Friendly fire**: the blast spares a cow and a villager next to the target. Test `javelinBlastSparesNonTargets` (Task 2).
4. **Ammo accounting**: no shot without a missile or without 10,000 FE; one shot takes exactly one of each. Test `javelinNeedsMissilesAndEnergy` (Task 1).
5. **The turret is destroyed or the chunk unloads mid-flight**: the missile still lands (turret gone → `Enemy` fallback); an unloaded missile is gone, no orphan. Test `javelinLandsAfterItsTurretIsGone` (Task 2).

---

### Task 1: Weapon type, items, targeting, ammo + FE (server)
**Files:** create `weapon/types/JavelinWeapon.java`, `data/bastion/turret_weapons/javelin.json`, `data/bastion/tags/items/ammo/javelin_missile.json`, `gametest/JavelinTests.java`, an open-top test structure in `tools/gen_structures.py` (+ generated `.nbt`); modify `WeaponType` (new candidate-picking hook with turret context), the target-selection call site in `TurretBaseBlockEntity`/`TargetSelector`, `BastionWeaponTypes`, `BastionItems` (module + ammo), `tags/items/ammo.json`, `TurretStatsConfig` (WEAPONS, NAMES, PARAMS), lang (item names, ability tooltip), `models/item/javelin_missile.json` + its sprite (single texture).
**Produces:** `JavelinWeapon` (static `boolean openSky(Level, LivingEntity)`, `boolean siloSeesSky(TurretBaseBlockEntity)`, `Vec3 siloMouth(TurretBaseBlockEntity, WeaponData)`), `BastionItems.JAVELIN_TURRET`, `BastionItems.JAVELIN_MISSILE`; until Task 2, `onFire` spawns nothing but consumes FE (Task 2 adds the entity).
- [ ] RED: `javelinNeedsMissilesAndEnergy`, `javelinPicksTheDensestCluster` (one husk alone at 12 blocks, three bunched at 20 → target is one of the three), `javelinSkipsRoofedTargets`, `javelinIgnoresFlyersAndCloseTargets` (phantom; husk at 5 blocks), `javelinNeedsOpenSkyAboveItsSilo`; `configListsEveryBuiltInWeaponAndKey` fails until the config lists Javelin.
- [ ] GREEN, then full suite + `build compileDevJava`.

### Task 2: The missile (server)
**Files:** create `projectile/JavelinMissileEntity.java`; modify `BastionEntities`, `JavelinWeapon.onFire` (spawn + `ShotReport.send`), `TurretImpactEvent` (kind JAVELIN), lang `entity.bastion.turret_javelin`, `JavelinTests`.
**Produces:** synced data on the entity: target entity id, drop point, flight phase, ticks to impact (client mark + VFX read them).
- [ ] RED: `javelinClimbsOutOfSightThenDives` (peak ≥ launch + apex − 2; last 10 ticks of the dive move < 0.3 blocks sideways per tick), `javelinDropsOnAMovedTarget` (target moved 5 blocks after launch → impact within 1.5 of it), `javelinFallsOnTheLastOpenSkyPoint`, `javelinBlastFallsOffToTheEdge` (centre husk loses ~40, one at 5 blocks ~19), `javelinBlastSparesNonTargets`, `javelinLandsAfterItsTurretIsGone`. Use a lower `apex` override in tests where time matters.
- [ ] GREEN, full suite.

### Task 3: Look and sound (client)
**Files:** `tools/blockout/design_weapons.py` (functions `javelin_turret` and `javelin_missile` entity; restore the missile_launcher geo after running), `gen_textures.py` (materials, single textures), animations `javelin_turret.animation.json` (deploy, idle, held_idle, fire: doors open → launch; reload: missile rises, doors close) + entity animation, `fit_item_display.py` (restore the turntable drift files), `BastionClient` (renderer; long render distance), `client/render` (renderer override `shouldRenderAtSqrDistance` or entity override), `client/JavelinEffects.java` (mark: chevron billboard + shrinking ground ring at the target/drop point, cyan → blinking red in the last second; launch gas, engine, smoke column; dive whistle), `VfxPresets` (JAVELIN_LAUNCH, JAVELIN_TRAIL, JAVELIN_EXPLOSION with light, shake, flash, scorch, debris), `ClientPacketHandler` (JAVELIN impact), `WeaponFx` entry, `TurretFireEffects` branch, sounds (`BastionSounds`, `sounds.json`, `gen_sounds.py` placeholders: launch, fly loop, dive whistle, explosion; `SOUND_SHEET.md` rows), subtitles in both lang files.
- [ ] Model per spec "Tampilan": VLS silo with two blast doors (`door_l`, `door_r`), loaded missile bone shown while loaded, radar mast on `yaw_pivot` with a dish + glow lens, `muzzle_0` at the silo mouth, `weapon_mount` conventions; missile ~1.5 blocks with 4 tail fins and a glowing nose. Offline previews.
- [ ] `build compileDevJava`, suite green; visual check in Task 5's scene.

### Task 4: Parts, recipes, Overwatch, JEI
**Files:** `BastionItems.PARTS` + `parts.json` + part item models; `gen_recipes.py` (a FORTRESS weapon set: Part Assembler 400 ticks / 25000 FE in the existing large-weapon tab `bastion:missile_launcher_turret` (no new tab); parts `LARGE_PART`; ammo recipe at the Ammo Workstation `javelin_missile` = 4 iron_ingot + 2 tnt + 1 firework_rocket + 2 redstone, 100 ticks, 2000 FE, 1, tab `rockets`); `OverwatchClient` / `ManualAim` (a weapon flag that picks the ground point without the ballistic arc; a world-space drop marker reusing the mark renderer); lang; JEI check.
- [ ] RED: `javelinRecipesExist`, `manualJavelinDropsOnTheCrosshair` (pattern `OverwatchTests.manualMortarShellsTheSpot`), `manualJavelinRefusesRoofedOrClosePoints`.
- [ ] GREEN, full suite.

### Task 5: Showcase, docs, final review
- [ ] Scene `-Pshowcase=javelin` (Fortress + Javelin, husk cluster in the open, a roofed husk, day and night: doors, launch, climb, mark, dive, blast; Overwatch drop marker if feasible); read every frame.
- [ ] Docs: TESTING "Javelin Missile (0.5.0)", HANDOFF §0, UPLOAD changelog 0.5.0 (New: Javelin), DESCRIPTION_UPDATE_0.5.0 + DESCRIPTION weapons row, SOUND_SHEET.
- [ ] Final whole-branch review: fresh Opus 5.5, effort xhigh (memory), then one fix pass, each fix RED→GREEN.
