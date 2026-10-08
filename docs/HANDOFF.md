# Bastion Turrets — Session Handoff (2026-10-07, 23:00 WIB)

Read this first in a new session, then `CLAUDE.md` and `docs/PLAN.md` (the spec). Manual test list: `docs/TESTING.md`.
Store/upload material: `docs/release/` (DESCRIPTION.md, DESCRIPTION.curseforge.html, UPLOAD.md, icon.png, screenshots).

---

## 0. Where we stopped

**NOW (read first):**
0000000000. **Railgun trail overshoot (user 12:51, there since the Railgun was built), committed 10-08 13:32 (not pushed):** the shot event carries the
   path's far end (range, MISS); the trail head runs `speed x (age+1)` = 40 blocks on frame one and was only trimmed by
   `trail.land()` when the tracer *arrived*, so for a tick it drew past the target. `TracerRenderer.Landing` got a default
   `known(end)`, called from `TracerRenderer.land` the moment the server's `BulletImpact` arrives; RailgunEffects trims
   the trail there. Shown with a temporary side-view, wall-less railgun scene: frame 82 trail to the screen edge before,
   ends at the husk after (scene restored). 109/109.
000000000. **Mod compat (user 11:10), committed 10-08 13:32 (not pushed):** `-PwithCompatMods` (build.gradle `compatMods`: Fungal Infection: Spore
   2.2.0j `PbOZOahW`, TaCZ 1.1.8-hotfix2 `AzCBJlex`, runClient + GameTests). Dev `CompatModTests` (pass at once without
   the mods): Spore Infected is shot and attacks an unarmed turret (worked as is: `Infected extends Monster`, Calamity/
   Organoid implement Enemy, so TargetFilter HOSTILE + TurretAggro apply); TaCZ picks bullet hits with
   `EntityUtil.findEntityOnPath` = box swept by motion + `isPickable && !isSpectator` → turret hitbox was never hit. Fix
   (user picked "server-side"): `TurretHitboxEntity.isPickable()` = `!level().isClientSide` (client crosshair still goes
   to the block). RED with the old value, GREEN; 108/108 with and without the compat mods. Owner protection unchanged
   (user: own turrets only fall to hostile mobs); for testing `/bastiondev owndamage true|false` sets trustedCanDamage.
   Not checked by hand: a real TaCZ gun shot (needs the user's gun pack). User 12:19: `/bastiondev busser` added (dev test
   `devCommandSpawnsBussers`, RED→GREEN, 109/109 with and without compat mods) to test turrets vs flyers and stingers.
   Watch: Mortar `grounded()` knows vanilla flyers only; Spore's Busser is a Monster (marker FlyingInfected).
00000000. **Railgun fix (user 10:31, screenshot: raised Railgun ignoring a zombie crowd ~7 blocks out), committed 10-08 10:53 (0.1.2 work, not pushed).**
   Two root causes, each pinned by a test that failed first: (1) `railgun.json` `min_pitch` -10 → **-30** (from a base
   one block up the head of a ground mob 6.5 out is 12° down: `TargetSelector.firstVisible` rejected it → IDLE;
   `railgunShootsDownFromARaisedBase`); (2) `Bullets.step` dropped a whole step when its far end was unloaded or out of
   the world, so a 40 b/t slug aimed down near y -64 (superflat) vanished even with a target 2 blocks ahead; now the
   step is halved until its end is loaded (`fastBulletIntoTheVoidStillHits`, all bullet weapons). **105/105** twice.
   Not checked on screen: the barrel at -30° (computed: clears the base edge by ~0.5 block).
0000000. **Placement hologram (user 00:44, design B 00:46, plan approved "native" 00:53), committed 10-08 10:53 (0.1.2 work, not pushed).** Spec PLAN
   "Hologram penempatan multiblock", plan `docs/superpowers/plans/2026-10-08-placement-hologram.md`, ledger in
   `.superpowers/sdd/2026-10-08-placement-hologram/`. `turret/Multiblock` + `Footprint(blocks, blocked, front)`:
   LargeTurretBaseBlock/WorkstationBlock `footprint(BlockPlaceContext)` feeds both `getStateForPlacement` and the
   preview (1x1 stations: null). Client `PlacementPreview` (RenderLevelStageEvent **AFTER_PARTICLES**; AFTER_TRANSLUCENT_BLOCKS
   drew nothing) builds the click's context from `mc.hitResult` (main hand's block wins), draws boxes
   (`BastionRenderTypes.ghostBox()` fill 15% + vanilla line box 60%) and `GhostRenderer` (GeoObjectRenderer,
   `PlacementGhost.of(blockIdPath, tier)`, `GhostModel` resets shared bones to rest + `showTier`, translucentEmissive,
   40% alpha, cyan / red 0xFF3A30). Tests `TurretPlacementTests` (large base + assembler with a blocker + `stationRefusesToBuildInsideAMob`), **103/103**.
   Final review (fresh reviewer): fixed workstations building around mobs in their other cells (pre-existing; now
   `isUnobstructed` like the large base). Deferred minors: clicked cell not red for an entity; hologram shows where the
   click would open a GUI / adventure mode / F1; scene lacks red-assembler and large-base night shots.
   Scene `-Pshowcase=placement` (PlacementShowcase): checked free/blocked/west-facing ghost vs placed/main hand wins/night.
000000. **Dev command (user 00:28):** `/bastiondev` also spawns `skeleton` (bow, drop chance 0) and `ravager`, count up to
   200 (sunflower spiral, one body width apart), HP up to 5,000 (vanilla max_health cap 1,024 raised in DevCommands'
   static init, src/dev only). Dev GameTest `DevCommandTests.devCommandSpawnsTankyMobs` runs the real command (failed
   before, passes). **100/100.**
00000. **Health lights, creative T2/T3 bases, module Shift tooltip (user 23:55; tooltip = "turrets it affects"),
   committed 10-08 10:53 (0.1.2 work, not pushed).** `EmissiveLayer.healthGlow(health)`: cyan, yellow < `DAMAGED_FRACTION` 0.5, red blinking
   (10-tick cycle bright/dim) < `CRITICAL_FRACTION` 0.25, used by the **base glow only** (user 00:15: weapon stays cyan,
   the earlier weapon tint was removed). User 00:15 also: bigger base glow (design_base: slits 2 x 3.4 px, seam
   10.9-11.6 depth 0.4; design_large_base: slits 12 x 2, seam 11.2-12 depth 0.35) and a fix for cyan showing through
   yellow/red: base glow bones use gen_textures material `status` (neutral STATUS_BASE albedo, glows like `emitter`)
   instead of cyan `emitter`. Large Base idle clip no longer spins `energy_ring` (straight bars on a square cut through
   the body while turning). Sparks 1/40 per tick below half, 1/8 below a quarter.
   Creative: `BastionItems.tab` adds `TurretBaseItem.withTier(item, T2/T3)` after each base. Modules: `ModifierEffect.affects`
   (fits + per stat: damage>0, heat_per_shot>0, turn speed>0, lead_accuracy param, bullet_speed/slug_speed for pierce,
   ammo_per_shot>0, spread>0; range/fire rate/max HP/reload = all), `ModifierItem.worksOn()`, Shift via DistExecutor +
   Screen::hasShiftDown; > half the turrets → "every turret except". Tests `moduleListsTurretsItWorksOn` + creative T2/T3
   asserts; **99/99**. New dev scene `-Pshowcase=health` (HealthShowcase: T1 Gun bases + T3 Large Tesla bases at
   100/40/15% HP, day/night/large shots): colours pure, weapon cyan, no diagonal bars.
0000. **Destruction rule (user 23:42, answers: modifiers drop / ammo lost, damaged base keeps its tier), committed 10-08 10:53 (0.1.2 work, not pushed):**
   `TurretBaseBlockEntity.destroy` drops only the modifier slots, clears weapon + ammo, drops DAMAGED(_LARGE)_TURRET_BASE
   with `BlockEntityTag.Tier` when T2/T3. `WorkstationBlockEntity.result()` puts the first consumed ingredient's tier on
   the result (so the Part Assembler repair recipes give T2/T3 back); `outputFits` checks that real result. Damaged items
   show "Tier N" (`BastionItems.damaged`). Tests: `destroyedLosesWeaponAndAmmo`, `destroyedT3KeepsTierOnDamagedBase`
   (replace the old T1/T3 drop tests), `assemblerRestoresDamagedTier`; failed before, pass after. **98/98.**
   Also the **Feed Hub fix**: a refused real insert goes back into its slot (`feedAmmo`, was voided). Store text
   (DESCRIPTION.md/.html) already describes the new rule: it ships with 0.1.2 (DESCRIPTION_UPDATE_0.1.1.md untouched).
000. **Balance (user 23:01), committed 10-08 10:53 (0.1.2 work, not pushed):** Mortar `damage` 6 → 20; `burn_damage` 1.5 → 3 per second in
   both `mortar.json` and `flamethrower.json` (the only two users of TurretBurn). 97/97 GameTests (the mortar test still
   sees the husk burning after the blast).
00. **Lighter camera shake (user 22:53 "terlalu geter kalau banyak turret dan gw deket"), committed 10-08 10:53 (0.1.2 work, not pushed):**
   `CameraShake.add` takes the max instead of adding (sources no longer stack; PLAN 5.6). Presets: ROCKET_LAUNCH
   0.3/10 → 0.15/8, ROCKET_EXPLOSION 0.75/18 → 0.5/14 (Missile explosion untouched, 0.35/12), DOME_BURST 0.9 → 0.5
   (radius 10). Shake = trauma²: rocket blast at 3 blocks 0.39 → 0.15, dome at 2 blocks 0.52 → 0.16, and 4 turrets
   together no longer hit the cap. 97/97 GameTests; shake can't be seen in a screenshot: the user checks it in game.
0. **UV fix in 3 older models (user 22:41 "boleh perbaiki dulu semua"), committed 10-08 10:53 (0.1.2 work, not pushed):** boxes of different
   sizes shared one kit tag, so they shared one UV region (paint_model keeps the first face's paint: a bigger face
   repainted it, a smaller one lost its dark bevel row). New tags: Machine Gun `bladev` (fan), `gring2` (barrel ring),
   Flamethrower `pipe2` (feed), Part Workstation `drawer2` (bottom drawer), `wrench2`. **Guard:** `Kit.region()` now
   asserts a reused tag has the same face size (all 4 design scripts pass; negative control fires). Changed: 3 geo +
   6 textures + kit/design scripts. 97/97 GameTests; scenes default/elemental/workshop checked (scratch world
   `run/saves/showcase_fx`, a copy of `showcase`). `blockbench/machine_gun_turret.bbmodel` was already out of sync
   with its geo (left alone). `build/libs/bastion-1.20.1-0.1.1.jar` was rebuilt and now contains this fix (tag v0.1.1
   does not). Changelog block "0.1.2 (not released yet)" in UPLOAD.md; `mod_version` still 0.1.1.
1. **0.1.1 released to GitHub (2026-10-07 ~20:05, user: "commit, push"):** commits `fad802f` (Feed Hub multiblock + energy,
   turret GUI shift-click fix, Creative Power Source pull fix, Pipez dev mod) and the 0.1.1 commit (Repulsor + Repulsor
   Dome, version bump), tag `v0.1.1`, pushed. **The user uploads `build/libs/bastion-1.20.1-0.1.1.jar` to CurseForge and
   Modrinth** (fields + changelog in `docs/release/UPLOAD.md`). Store text: the user pastes only the changed parts,
   `docs/release/DESCRIPTION_UPDATE_0.1.1.md` (7 parts, Markdown + HTML, since the beta.2/beta.3 text on both stores).
   New gallery images `11_repulsor_dome`, `12_railgun_charge` (screenshots/ + screenshots-840/, captions in UPLOAD.md),
   from `-PnoDevMods -Pshowcase=repulsor` (low camera, 8 husks) and `-Pshowcase=railgun` frame r_barrel_29. Changelog in
   UPLOAD.md reordered (new turrets first, then Feed Hub, then Fixes). **Uncommitted since `v0.1.1`:** the store
   docs above (DESCRIPTION.md/.html markers + FE/config sentences, UPLOAD.md, DESCRIPTION_UPDATE_0.1.1.md, 4 images),
   HANDOFF, and the RepulsorShowcase camera/8-husk tweak; commit only on "commit" (not part of the jar).
   Versioning (user 20:04): stays on the **Beta** channel
   with plain numbers `0.1.1`, `0.1.2`, ...; the first Release will be **1.0.0**. Never commit `modlist.html`.
2. **Feed Hub multiblock + energy: built** (design 15:35, approved 15:47 incl. the full 4a look; defaults taken without
   an answer: 8 items/tick **per block**, FE of a broken block is **lost**). See "Feed Hub multiblock" below.
3. **Released in 0.1.1: Repulsor (1x1) + Repulsor Dome (2x2)** (user idea 18:29: turrets can't hit
   mobs hugging/behind their barrel; these push valid targets away, no damage, FE). Decisions 18:32: 1a names, 2a FE,
   3a no damage, 4a 1.5 s / 4 s with a 0.5 s charge; approach A (weapon modules); spec PLAN "Repulsor & Repulsor Dome";
   plan `docs/superpowers/plans/2026-10-07-repulsor.md`. **92/92 GameTests**, scene `-Pshowcase=repulsor` checked,
   independent review done (wall-mount push fixed, dome shake radius 10).
   **User test 19:45: buffed** (Repulsor range 5 / push 4, Dome range 7 / push 6) + **heavy mobs** (config
   `repulsorHeavyMobs` = iron golem, ravager, warden; Repulsor never targets them, Dome pushes them x`repulsorHeavyMobPush`
   0.4, their vanilla KB resistance ignored; the hop is now the same for every mob so distance scales evenly). **97/97 GameTests.**
   Tested in game by the user (19:45 buff, 20:00 orb colour fix). See "Repulsor" below.

**Repulsor (built 2026-10-07 19:15):**
- `weapon/Repulsion.push(target, from, fallback, blocks, lift)`: horizontal launch `blocks x SPEED_PER_BLOCK (0.2)` +
  lift 0.35, scaled by (1 - knockback resistance); calibrated by GameTest `repulsionTravelsAsSet` (lands 2.99 / 4.98
  blocks for 3 / 5). NoAI mobs ignore physics (scene uses `removeFreeWill`).
- `RepulsorWeapon` (`repulsor.json`): AREA_PULSE, FE like Tesla (`cost()` from energy_per_shot 500, cap 10k, in 500/t),
  turns 12°/t, pitch -90..90, charge 10 t, every 30 t; cone 35° **from the pivot** (from the horn's mouth a husk beside
  the target fell outside and took a second pulse; test asserts one pulse), line of sight from the pivot, shares
  `FlamethrowerWeapon.inCone`. `RepulsorDomeWeapon` (`repulsor_dome.json`): never turns, `sight` = no line of sight,
  all valid targets within 6 of the pivot (orb, pivot_height 1.0) pushed 5, 2,000 FE (cap 40k, in 2,000/t), every 80 t.
- Client `RepulsorEffects`: wave = REPULSOR_MUZZLE + 4 SHOCKWAVE_RING rings + haze + a translucent air cone (`Wave`,
  6 ticks) along the barrel (not toward the first mob); dome = sphere mesh (12x24, rim-bright, `vfx/dome.png` streaks)
  growing to range in 8 t then fading 8 t, a sand dust ring drawn on the floor at the sphere's edge (shaded by light:
  the render type is fullbright), DOME_BURST (flash, speed-line sparks, shake, flash). Dust at each pushed mob
  (REPULSOR_HIT). Charge: Repulsor horn rings `glow_coil_0..2` light via `coilFill`, Dome ring `dome_ring` spins via
  `focusAngle` (WeaponModel `setRotY`), glow brightens (`chargeGlow`). Sounds `repulsor.*` placeholders.
- Models `design_weapons.py repulsor()/repulsor_dome()`, clips hand-written, parts `repulsor_emitter/coil/mount`,
  `dome_core/pylons/mount` (parts.json, gen_recipes), test arena `yard` (28x28). GameTests `TurretRepulsorTests` (9).
- Review (19:25): pushes start from where the base is attached (`alongMount(0)`), so a wall Repulsor pushes mobs off
  the wall (fallback: the facing); dome shake radius 10. Not reproduced, kept as guard tests: aim/push mismatch behind
  a fence (line of sight uses 1-block outlines), aim on the mount axis. Deferred minors: riders / KB-resistance-1 mobs
  still cost FE, visuals use data.range() not stats.range() (Range Module), wave drawn from the muzzle, focusAngle
  unbounded.
  `gen_structures.py` rewrites every template with a new gzip timestamp: restore the untouched ones from git.

**Feed Hub multiblock (built 2026-10-07 16:55, PLAN "Feed Hub" → "Multiblock + energi"):**
- Shapes 1x1x1..3x3x3 (square, H <= W). A hub's place lives in blockstate `x`/`y`/`z` = alone/low/middle/high
  (`FeedHubBlock.Part`), so no BE structure data, client gets it with the block. Placing/breaking schedules a 1-tick
  block tick (`FeedHubBlock.tick` → `FeedHubStructure.reform`): flood fill of connected hubs (cap 512, loaded only),
  re-partition from scratch, lowest block first (y, x, z), largest shape first; `setBlock(UPDATE_CLIENTS)` only.
  `FeedHubStructure.Box.of` reads a structure back from the states (never loads chunks).
- `FeedHubBlockEntity.Group`: one structure for one tick, built by the lowest block (controller) and shared:
  members (slot order), `CombinedInvWrapper` of their 9-slot buffers, feeds (standard bases on outer faces with their
  back on it + Large Turret Bases with all 4 blocks on top, dedup by core), FE (per-block `energy` int, fill/drain in
  order, per-tick input `received`). A member whose controller is not loaded runs alone. Capabilities on every block are
  views over `group()` (`StorageView`, `EnergyView` receive-only), so a pipe's cached handler stays right across merges.
  Controller tick: `feedAmmo(8 x blocks)` (least stock first), `feedEnergy()` (least charged first, turret max_input).
- Config (server, per block): `feedHubEnergyCapacity` 50000, `feedHubMaxInput` 4000, `feedHubItemsPerTick` 8.
- GUI `FeedHubMenu`/`FeedHubScreen` 192x194, side tabs **Summary** (size, turrets + up to 12 weapon icons, FE bar, up to
  8 ammo totals; ContainerData ints as 16-bit halves) and **Storage** (9x4 window, scroll by wheel/bar →
  `clickMenuButton(row)`; server slots = `Window` into the structure, client slots = plain mirror; shift-click fills the
  whole structure). Textures `gui/feed_hub.png` + `feed_hub_plain.png` (`feed_hub_gui(plain)`).
- Look: `tools/gen_feed_hub_models.py` writes the multipart blockstate + `feed_hub_panel` + 6 `feed_hub_frame_*` + 6
  `feed_hub_glow_*` models (full-face overlays, strip at the texture top turned by face `rotation`; all glow after all
  frames so lines meet in the corner); lone hub keeps `block/feed_hub`. Textures `feed_hub_panel/edge/edge_glow`.
- GameTests `TurretFeedHubTests` now 13: forms, splits, sharesStorage (cached handler sees the merge), feedsLargeBase
  (Mortar: an idle Missile Launcher loads its tubes from its slots), dropsOwnShare, newControllerFeeds, powersTesla,
  menuScrolls (server side of the GUI), partialShiftClickSaves, staleStateStaysApart, skipsRemovedTurret (review fixes),
  scrollFollowsShrink, idleIsCheap + the 2 old ones unchanged. Scene `-Pshowcase=feedhub`: lone hub, 2x2x1 + Tesla,
  3x3x2 with five turrets, Creative Power Sources, day/night, GUI Summary/Storage/scrolled.
- Create's Item Vault source (MIT) was read for reference (deferred forming, combined handler); code is our own.
- Review (fresh reviewer 16:55): fixed (1) partial shift-click out of Storage did not mark the hub changed (dupe on
  reload), (2) `Box.intact`: a block whose structure is not whole + loaded runs alone (a just-broken block's stale
  states read a wrong box spanning two structures; a chunk-border box could force-load via `TurretBaseBlock.core`),
  (3) a turret removed earlier in the tick makes the Group stale. Then fixed on the user's go (17:16): **turret GUI**
  shift-click merge/partial take-out was never saved (item loss / dupe on reload; `TurretSlot.setChanged` →
  `TurretInventory.changedInPlace`, GameTest `menuShiftClickMarksTurretChanged`, in the beta.5 changelog), Storage scroll
  follows a shrinking structure (`FeedHubMenu.scroll()` clamps; `feedHubScrollFollowsShrink`), idle cost of a full
  structure (ammo kinds with no room remembered per tick: 200 ticks of a full 3x3x3 + 27 full guns 238 → 11 ms;
  `feedHubIdleIsCheap`, fails over 40 ms). Still deferred (user decides): real insert result ignored in feedAmmo,
  `stillValid` ignores `isRemoved`, two misplaced registry comments (BastionBlocks:36, BastionBlockEntities:36).
- 17:55 (user test with Pipez): Creative Power Source exposed no ENERGY capability, so pull-based pipes never attached;
  it now offers endless extract-only FE on every side (GameTest `creativePowerSourceCanBePulled`; dev GameTest
  `PipezEnergyTests` runs real Pipez: source -> 2 energy pipes -> Feed Hub, negative control checked). **83/83 GameTests.**
  Pipez keeps a pipe's connections in its blockstate and only recomputes them on `neighborChanged`: pipes placed before
  the fix must be broken and re-placed.

**Released (public beta):**
- **0.1.1** committed, tagged `v0.1.1` and pushed 2026-10-07 ~20:05 (user asked). Upload pending by the user.
- GitHub `AlifRizza/bastion-turrets` is **public**. **0.1.0-beta.4** committed, tagged `v0.1.0-beta.4` and pushed
  2026-10-07 13:30 (the user asked for the push this time; otherwise the user pushes). Tags beta.1-4 all on GitHub.
- **beta.4 upload**: the user uploads `build/libs/bastion-1.20.1-0.1.0-beta.4.jar` to CurseForge and Modrinth
  (fields + changelog in `docs/release/UPLOAD.md`).
- **CurseForge**: beta.1 and beta.2 uploaded by the user (beta.2 = `e43a732`, must stay as uploaded).
- **Modrinth**: user is creating the project; **beta.3** (`build/libs/bastion-1.20.1-0.1.0-beta.3.jar`) is its first
  version. Icon `docs/release/icon.png` (Modrinth refused AI art; this one is rendered from our model).
- beta.4 = the balance changes, Railgun and Mortar below (user tested in game, "commit" 13:30). For the working tree
  now see NOW above.

**In beta.4 (2026-10-07 ~01:10): balance** (`mod_version=0.1.0-beta.4`). Waiting for the user's
in-game test and "commit". Never commit `modlist.html`.
1. **Tesla Coil**: `tesla.json` `bolts` 2 → 5, new `bolts_per_target` 2 (user pick): `TeslaWeapon` throws
   `min(bolts, bolts_per_target x targets)` round-robin, so 1 mob takes 2 bolts (20, same as before), 3 mobs 2+2+1, 5+
   mobs 1 each; leftover bolts are not thrown. FE 3k per discharge kept (user pick). GameTests renamed
   `teslaZapsFiveTargets` (6 husks, exactly 5 struck) and `teslaLoneTargetTakesTwo` (alive at ~0.3 HP after one discharge).
2. **Shotgun**: `shotgun.json` `damage` 2.5 → **5** per pellet (user pick over the proposed 4.0).
Docs updated: tooltips en_us/id_id, TESTING.md, DESCRIPTION.md + .curseforge.html, UPLOAD.md (beta.4 changelog, version
fields, Tesla gallery caption). Screenshot `03_tesla_coil` still shows 2 bolts (caption says "up to five": still true).
3. **GameTest fix (build.gradle)**: `runGameTestServer` uses its own world `run/gametestworld` and deletes only its
   `entities/` before each run. It used to share `run/world` (runServer's) and saved arenas' mobs and drops on exit; the
   next run loaded them back into its tests (entities load after an arena is cleared), so drop/target counts failed at
   random once the arena layout changed. Wiping the whole world instead cost ~25 s of terrain per run and once hung on a
   vanilla worldgen race (`MissingPaletteEntryException` in the light engine). `run/world` is no longer touched.

**In beta.4 (2026-10-07 ~12:00): Railgun** (user design, approved 10:45-11:14, accepted in game 12:09; PLAN
7.6). In the beta.4 changelog and both descriptions.
- Large module `railgun_turret`, weapon `railgun.json` (type `bastion:railgun`, `RailgunWeapon`): 60 dmg, damage type
  `turret_rail` in `minecraft:bypasses_armor` + `always_hurts_ender_dragons` (NOT `is_projectile`: endermen would dodge),
  x`boss_multiplier` 2 vs `forge:bosses`, range 80, charge 60 t + 40 t, slug 40 b/t (Bullets, lead), `min_range` 5,
  pitch -30..60 (was -10, 10-08), turn 3°/t. Cost: 1 `rail_slugs` (Ammo Workstation) + 8k FE (cap 80k, 2k/t). Shockwave: `BastionExplosion`
  with damage 0 (push only, valid targets) at the hit or block (`Bullets.Hit#blocked`, new). Target order: new hook
  `WeaponType#targetOrder` (biggest max HP first, GUI priority breaks ties).
- Model `tools/blockout/design_weapons.py railgun()` (Kit 256; RG_* constants = railgun.json pivot/muzzle and the client's
  coil fractions): muzzle_0 tip, muzzle_1 breech end of the channel, muzzle_2/3 rail tips, eject_0 vents; bones
  `glow_coil_0..4`. Charge visuals: `WeaponAnimatable.coilFill/chargeGlow` (ClientTurret) hide unlit bands
  (`WeaponModel`) and blend the glow to violet (`WeaponModelRenderer.glowColor`); arcs `RailgunEffects`; slot core,
  afterglow and the ion trail with helix `RailRenderer`. Presets `RAIL_*`. Sounds `railgun.*` (gen_sounds).
- Parts `railgun_rails/receiver/mount` (parts.json, gen_recipes LARGE_PART). Info tab shows Ammo instead of Bolts for it.
- GameTests `TurretRailgunTests` (5): needs FE, needs slugs, ignores armor (exact 60 through diamond), picks the boss +
  x2 (Wither with `setNoAi`: a free Wither flies over the turret, out of reach), shockwave pushes the valid husk, not
  the cow. **63/63 pass** (twice). Scene `-Pshowcase=railgun` (RailgunShowcase) checked: fill, arcs, shot, trail, push.

**In beta.4 (2026-10-07 ~12:40, fixed 13:25): Mortar** (design approved 12:09; PLAN 7.6). In the beta.4 changelog/descriptions.
- Large module `mortar_turret`, `mortar.json` (`MortarWeapon`): high-arc ballistic solve (`highArc`, re-solved from the
  muzzle 3x, elevation 45..86), **no lead** (user 13:15: it shells where the target stood when it fired; walkers can
  escape, it is for crowds; the lead first built looked like homing), `sight` = feet if the arc is clear
  (parabola cast in 2-tick segments, cached in `WeaponState.arc*` for 10 t), `canTarget` = grounded (no FlyingMob,
  FlyingAnimal, AmbientCreature, Blaze, Vex, Allay, Wither, Dragon, noGravity) + `min_range` 8. Shell speed 1.5, gravity 0.05
  (range 45 flat), every 60 t, 1 `mortar_shells`. Blast = `stats.damage` 20 (was 6, user 10-07 23:01) r1.5 (valid targets), no direct hit damage.
- Shell = third `TurretRocketEntity` type `turret_mortar_shell` via `ballistic(gravity, patch)`: gravity half before /
  half after each move (exact parabola, matches the solver), server-side, velocity synced every tick (`hasImpulse`).
- `weapon/FirePatches`: patch on the ground below the burst (clip down 4), size x size blocks, 100 t, every 5 t
  `TurretBurn.ignite` (60 t, 3/s since 10-07 23:01) on **every** living entity in it (fire-immune skipped by TurretBurn); vanilla fire in
  free spots touching a flammable block (`BaseFireBlock.canBePlacedAt`) unless config `mortarBlockFire` false. Client:
  `TurretImpactEvent` MORTAR_BLAST + FIRE_PATCH (scale = seconds, source = width) → `MortarEffects`: renders the patch
  itself (user 13:15: the whole 3x3 must look burnt; scattered particles read as ~1 block): an alpha-blended ember layer
  over exactly the square + 4 flame sheets per block (texture `vfx/fire_sheet.png`, tileable, climbing) in 3 layers, plus
  a few particles, light, crackle, scorch. Scene step `m_patch` shows a patch alone on stone. Whistle on descent (`RocketEffects`). Barrel pitch is now clamped to the weapon's elevation
  limits on server and in the client idle pose (generalised from the Missile Launcher's fixed pitch).
- GameTests `TurretMortarTests` (2) in the new tall template `pit` (26x32x13): over a wall onto a husk, husk + cow burn,
  planks catch fire; never at a phantom or a husk inside 8 blocks. **65/65 pass.** Scene `-Pshowcase=mortar` checked.

**Built, uncommitted (2026-10-07 ~15:05): Feed Hub** (user idea, the simple stand-in for the extender; PLAN "Feed Hub").
`turret/FeedHubBlock` + `FeedHubBlockEntity` (9-slot buffer = the item capability on every side; `isItemValid` = some
armed mounted turret takes it; `distribute()` moves up to 8 items/tick one by one to the mounted turret with the least
of that ammo, through the base's own ammo capability; `feeds()` = standard bases on the 6 faces whose FACING points
away from the hub). `menu/FeedHubMenu` (client uses the client BE's buffer so slot validity matches),
`client/screen/FeedHubScreen` (176x170, `gui/feed_hub.png` from `feed_hub_gui()`; `panel()` helper now shared with the
turret GUI, its PNGs unchanged). Block model: cube + fullbright glow overlay element (`forge_data` block/sky light 15).
A block item in hand passes the click, so bases can be placed on the hub. Recipe Module Workstation (TOOLS table).
GameTests `TurretFeedHubTests` (2): even split 8/8, shells to the shotgun, rockets refused, unarmed / merely adjacent
bases skipped; buffer holds when the gun is full and drains when it has room. **67/67 pass.** Scene `-Pshowcase=feedhub`.
Changelog block for 0.1.0-beta.5 in UPLOAD.md (version not bumped yet).

**Base Extender (PLAN Fase 10): "tidak direncanakan dulu"** (user 2026-10-07 14:31). Spec approved and kept in PLAN.md, no
implementation plan; postponed as long and maybe unnecessary. The user has two shorter ideas, one a much simpler
workaround for it. Do not build unless the user brings it back. Exploration notes for later: base API surface is large
(base used as weapon host by every WeaponType; plan was a `TurretMount` refactor with the base delegating to mount 0),
the extender block could reuse the `bastion:turret_base` baked-model loader (only needs FACING + a geo).

**Open items the user mentioned (do not build unasked):** Plasma Cell + its turret (Charging Station `CHARGE` table in
`tools/gen_recipes.py`); weapon module tiers Mk1/Mk2/Mk3 (bonuses **undecided**, ask); 3x3x1 large base; whether to add
an FE generator (Bastion has none; survival needs an FE mod, stated in the description); stacking the same module twice
(today refused, one of each kind per base: user asked, answered, offered to change it).

---

## 1. Project basics

- Minecraft Forge **1.20.1**, Forge 47.4.26 (min `[47.2,)`), Parchment 2023.09.03, **GeckoLib 4.8.4**, Java 17, Gradle 8.8.
- Mod id `bastion`, package `dev.bastion`, root `/Users/alifrizzaz/Documents/Minecraft Modding/Turret`.
- Version in `gradle.properties` (`mod_version=0.1.1`), jar `bastion-1.20.1-<version>.jar`, authors `AlifRizza`,
  license All Rights Reserved.
- **Git**: branch `main`, remote `origin` = GitHub (public). Commits: `df3660b` initial, `f1819a3`, `f460e59` Fase 9,
  `05d4bb9` tabs, `ae44021` release prep (beta.1), `2c08cd7` bullets + performance, `e43a732` beta.2, `f2c8179` beta.3.
  Commit **only when the user says "commit"**; the user pushes. Ignored: `run/`, `build/`, `.gradle/`, `/references/`.
- Build/run: `./gradlew build`, `./gradlew runClient --offline` (see §9), `./gradlew runGameTestServer` (**97 tests**).
- Dev runs include `src/dev` (never in the jar): dev commands, dev-only GameTests, scripted scenes.
- Dev runtime mods: Create 6.0.8 + Ponder + Flywheel + Registrate + MixinExtras (runtimeOnly); **Pipez** forge-1.20.1-1.2.26
  (runtimeOnly, Modrinth id `Mtjt7u5h`, user 17:14: Universal Pipe = items + FE for testing hubs/turrets); **ToroHealth** damage
  numbers (`clientDevMods`, runClient only; dropped with `-PnoDevMods` and in the stress scene).
- Language: answer in the language of the user's message (mostly Indonesian). Code, comments, docs in English.
- The user has ADHD-mode + ponytail output styles on: action first, short, minimal code, numbered steps, one next action.

## 2. Hard rules (CLAUDE.md + PLAN §1, §9)

- Read `docs/PLAN.md` before work; do only what is asked. Ask before guessing gameplay (PLAN 9.7).
- **NO-VANILLA visuals**: no vanilla particles, sounds, textures, explosion visuals, GUI textures, vanilla fire.
  Everything comes from our generators (`tools/`) and the VFX framework.
- No copying GPL code (`references/` is study only). Store art must not be generative AI (Modrinth rule).
- Strict client/server split (client code only in `client` packages, reached via `DistExecutor`/client events).
- Balance numbers in JSON / config / `tools/gen_recipes.py`, never hardcoded.
- After each feature: `./gradlew build` passes + a checklist in `docs/TESTING.md`.
- Never write `eula=true`. Never bypass macOS TCC or the screen lock.
- **Never overwrite `run/saves/showcase`** (the user's hand-built Create rig). Scenes run in a scratch copy:
  `cp -R "run/saves/New World" run/saves/showcase_fx` then `-PshowcaseWorld=showcase_fx`, delete after.

## 3. Decisions (user-confirmed unless marked)

| When | Decision |
|---|---|
| 10-06 Fase 0/1 | Create only as dev runtime (+ optional compat). Mined base keeps tier + inventory. GUI 216x200, no text overlap. |
| 10-06 morning | Creative Ammo (creative-only, fits every weapon, never used up). `/bastiondev zombie [hp] [count]`. ToroHealth. |
| 10-06 09:05 | Hitbox must not block clicks; zombies attack turrets (`TurretAggro`). GUI state = lamp. Players cannot melee turrets. |
| 10-06 11:00 | Redesign: uniform **gunmetal + one cyan glow**, per-weapon colours only in VFX. Base 1 block, floor/wall/ceiling. |
| 10-06 11:45 | Sniper: charge shot, pierces 2. Rocket Launcher: dumb-fire, splash only valid targets, min range 4. |
| 10-06 13:10 | **Large Turret Base 2x2x1** (floor only). **Missile Launcher**: 6+6 tubes, yaw only, fixed 30°, homing, Single/Salvo. |
| 10-06 14:35 | **Tesla Coil** (2x2, FE, 2 random valid targets). **Flamethrower** (1x1, Fuel Canister, cone, custom burn). |
| 10-06 15:45 | **Laser Rifle** (Laser Cell, single heavy beam, pierces valid targets, stops at blocks). |
| 10-06 16:40 | Weapon-specific modules (first: Choke Module). Mk1-3 weapon tiers planned, bonuses **undecided**. |
| 10-06 17:01 | **Fase 9 Workstations** (5 FE stations, crafting table only makes them, own part set per weapon). |
| 10-06 18:00 | Creative tabs: Turret Bases, Weapon Modules, Ammo, Modules, Workstations, Parts. |
| 10-06 18:30 | **Bullets with travel time** (Gun/MG/Shotgun/Sniper, "like TaCZ"), **option B**: data stepped per tick, no entities. Modpack target: tens to hundreds of turrets per base; optimise by measurement. |
| 10-06 20:00 | Public beta on CurseForge + Modrinth, beta channel, version `0.1.0-beta.N`. Repo public. |
| 10-06 20:46 | **Smoke trails** for rockets and missiles. |
| 10-06 22:40 | Flamethrower/MG must not stop between targets; flame stays on until nothing is in range. |
| 10-06 23:10 | **Autoloader** module for weapons with a reload. Same module twice: still refused (answered, not changed). |
| 10-06 23:25 | Store icon from an in-game render (no AI art). Disclaimer sentence: code AI-generated, art made by the AI through scripts and Blockbench MCP (not generative image AI). |
| 10-07 00:50 | Tesla **5 bolts, max 2 per target**, FE 3k kept. Shotgun **5 per pellet**. Version 0.1.0-beta.4. |
| 10-07 10:45 | **Railgun** (2x2, anti-boss, slug + FE, user's reference image: long two-rail barrel, drum; charge = barrel lights up then arcs over it). |
| 10-07 11:18 | **Mortar** (2x2, arcing, ground only, 3x3 fire patch burns everyone, vanilla fire on burnable blocks). Approved 12:09. |
| 10-07 12:09 | Railgun approved in game. **beta.4 ships after the Mortar** (balance + Railgun + Mortar). |
| 10-08 11:10 | TaCZ bullets hit turrets (server-side pickable hitbox); own turrets stay protected (dev `owndamage` toggle for testing). Spore mobs fight turrets as is. |
| 10-08 10:31 | Railgun min pitch -30 (was -10); bullets no longer vanish when a step ends out of the world. |
| 10-08 00:46 | Placement hologram, design B (ghost model + per-block boxes, red where blocked). |
| 10-08 00:15 | Health colour on the base only (weapon stays cyan); bigger base glow; no cyan mixing in. |
| 10-07 23:55 | Health glow yellow < 50%, red blinking < 25%. T2/T3 bases in creative. Module Shift tooltip lists the turrets it affects (answer: affected, not merely fits). |
| 10-07 23:42 | Destroyed turret: weapon module + ammo lost, modifiers drop, base drops as a Damaged Base that keeps its tier (restored at the Part Assembler). Feed Hub insert fix. |
| 10-07 23:37 | **No new turrets for now**, until the placeholder sounds (58 events in sounds.json) are replaced by generated or found SFX. 0.1.2 = adjustments, balancing, small additions. |
| 10-07 23:09 | Addon support (TaCZ-style turret packs) goes in the backlog, after release. |
| 10-07 23:01 | Mortar damage 20; burn (Flamethrower + Mortar patch) 3 per second. |
| 10-07 22:53 | Less camera shake from Rocket Launcher and Repulsor Dome; shakes from many turrets must not add up. |
| 10-07 22:41 | Fix the UV overlap in the 3 older models (offer (a) below). |
| 10-07 20:04 | Versioning: Beta channel with plain numbers (0.1.1, 0.1.2, ...), first Release = 1.0.0. Repulsor Dome orb all blue (UV fix). |
| 10-07 19:45 | Repulsor buff: range 5 / push 4; Dome range 7 / push 6. Heavy mobs (config list, default iron golem, ravager, warden): Repulsor cannot push them (ignores them), Dome x0.4. |
| 10-07 18:32 | **Repulsor + Repulsor Dome** (push mobs off the turrets, FE, no damage, 1.5 s / 4 s, charge 0.5 s). Cone measured from the pivot (19:08, so one push hits mobs crowding the turret's sides). |
| 10-07 13:15 | Mortar: **no lead** (hits where the target stood; misses walkers, for crowds); fire patch must visibly cover 3x3. |
| defaults, **not confirmed** | No bullet drop; auto-lead `lead_accuracy` 0.9; bullets fly through non-targets (old hitscan hit anything); speeds Gun 6, MG 5, Shotgun 4, Sniper 12 b/t. `sweep_tolerance` Flamethrower 180, MG 30. Autoloader +40%, recipe nuggets/redstone/copper/piston. Mismatched ammo never fires. Precision Lock x1.5 stacks with headshot. Default targets Hostile+Boss. Repair kit 40%. Large base HP x2.5. Tesla 60k/1k per tick. Flamethrower ignores fire-immune mobs. Workstation FE 50k, 2k/t. License All Rights Reserved. |

## 4. What exists

Fase 0–7 complete; Fase 8 partial (lang en_us/id_id done; Jade/JEI/Ponder compat and profiling vs PLAN budgets not
started; Embeddium/Oculus checked, see §7); Fase 9 (workstations) built.

**Blocks**: `turret_base` (1x1, any face), `large_turret_base` (2x2, `quadrant`/`core`), five stations, `creative_power_source`.

**Items**: 9 weapon modules, ammo (kinetic_rounds, scatter_shells, sniper_rounds, rockets, missiles, fuel_canister,
laser_cell, empty_laser_cell, creative_ammo), **10 modules** (8 general + choke_module + autoloader), repair_kit,
tier_upgrade_kit_t2/t3, turret_configurator, damaged (large) turret bases, 33 parts. Six creative tabs (by item type).

**Weapons** (`data/bastion/turret_weapons/*.json`, types in `BastionWeaponTypes`):

| Weapon | Profile | Key numbers |
|---|---|---|
| Gun | bullet 6 b/t, Precision Lock | 7 dmg, 32 range, 12 t, lock 20 t → x1.5 (+x1.5 headshot) |
| Machine Gun | bullet 5 b/t, spin-up, heat, slowness, sweep 30° | 2.5 dmg, 24 range, 12/s at full spin, 0.5 ammo/shot |
| Shotgun | 8 bullets 4 b/t, falloff, knockback, pump | **5/pellet**, 12 range, 24 t, spread 12° (Choke: 7.2°) |
| Sniper | charged bullet 12 b/t + laser sight | 24 dmg, 64 range, 30 t charge, pierces 2 |
| Rocket Launcher | projectile, lead, splash, smoke trail | 8 + 12 splash r3.5, 40 range, 36 t (Autoloader: /1.4), min range 4 |
| Missile Launcher (large) | homing, fixed 30°, 12 tubes, contrails | 7 + 6 splash r2.2, 48 range, reload 30 t/tube (Autoloader: /1.4) |
| Tesla Coil (large) | ARC, never turns, FE | 10/bolt **x5 bolts, max 2 per target**, 16 range, 12 t charge + 18 t, 3k FE/discharge |
| Flamethrower | CONE, custom burn, sweep 180° | 1.2/pulse every 2 t + burn 3/s 4 s, 8 range |
| Laser Rifle | CHARGED beam, pierces all valid targets | 28, 40 range, 50 t charge + 50 t, 1 Laser Cell/shot |
| Railgun (large) | charged slug 40 b/t, ignores armor, boss x2, biggest first, push shockwave r4 | 60, 80 range, 60 t + 40 t, 1 slug + 8k FE |
| Mortar (large) | ballistic shell (45-86°), no lead, ground targets, over walls, 3x3 fire patch 5 s burns everyone | 20 blast r1.5, 8-40 range, 60 t, 1 shell; patch burn 3/s |
| Repulsor | no damage, cone 35° push, line of sight, FE, ignores heavy mobs | push 4 blocks, range 5, charge 10 t, every 30 t, 500 FE |
| Repulsor Dome (large) | no damage, push everything around, through walls, FE, heavy mobs x0.4 | push 6 blocks, range 7, charge 10 t, every 80 t, 2,000 FE |

**Turret systems**: tiers T1–T3 (module slots 1/2/4), HP via `TurretHitboxEntity`, regen, repair, destruction,
owner/trusted, redstone, filters, configurator, modifiers (one of each kind per base, weapon-specific ones), servo + scan
sweep, lead, state machine, comparator, ammo-only automation, FE for energy weapons.

**VFX**: per-weapon muzzles/impacts, bullet tracers, rocket/missile smoke trails (`SmokeTrailRenderer`) + custom
explosions, Tesla arcs, laser beam + charge, flame jet + burn, scorch decals, workstation effects, dynamic lights,
camera shake, screen flash. Debug: `/bastion vfx <preset>`.

**Network** (`BastionNetwork`, PROTOCOL **"8"**): registered packets TurretEvents (S→C bundle), WeaponDataSync,
TurretConfigUpdate (C→S), BurnSync, WorkstationAction (C→S). `sendNear` queues TurretStateSync, TurretFireEvent (bullet
`speed`), BulletImpact, SpinSync, RackSync, TurretImpactEvent (`source` = exploding rocket's entity id); one packet per
player per level tick.

## 5. Architecture notes that are easy to get wrong

- **Mount frame**: turret logic in turret space (+Y = mount axis); `toWorld/toLocal` use `facing().getRotation()`.
- **Weapon hooks** (`WeaponType`): `tick`, `fireInterval`, `ready`, `chargeTicks`, `canTarget`, `aimPoint` (lead),
  `sight`, `outOfAmmo`, `firesAsItReloads` (Rocket Launcher: reload_speed also shortens its fire interval).
- **Targeting**: a lost target triggers a search the same tick (`searchTimer = 0`); `Aim.sweep_tolerance` applies within
  `SWEEP_GRACE` (10 t) of the last shot so bursts continue while swinging. Charge holding: a charge survives aim drift.
- **Never use `EntityHitResult#getLocation` / `ProjectileUtil.getEntityHitResult` for points**: it is the target's feet.
  Rockets use `TurretRocketEntity.impactPoint` (flight segment vs box); the aiming laser uses `Hitscan.trace`.
- **Homing**: missiles lead on `targetVelocity`, smoothed 0.8/0.2 per tick (raw velocity of blast-knocked mobs made them weave).
- **Smoke trails** (`SmokeTrailRenderer`, fed by `RocketEffects`): ribbon through tick positions; a flying rocket's own
  tick point is skipped (it lies ahead of the interpolated model) and the ribbon starts at the drawn nozzle; edges shared
  per point; `end(id, at)` on `TurretImpactEvent` (by id, never nearest) runs the trail into the blast, since the server
  removes a rocket a tick before its hit point. Texture `vfx/smoke_trail.png` (`smoke_strip` in gen_textures.py).
- **Bullets** (`weapon/Bullets`): `Bullets.fire` steps once at once; stepped at LevelTickEvent START, `Hit.endTick` at END.
  Fire event shots carry the bullet id in `blockState`; `TracerRenderer.spawnBullet` flies until `BulletImpact` lands it.
- **Baked base** (`BakedTurretBaseModel`, loader `bastion:turret_base`): built from `GeckoLibCache` per facing x tier x
  quadrant, tier via ModelData; `TurretBaseRenderer` draws only `energy_ring` + glow faces. Muzzle-flash dynamic light no
  longer brightens the base.
- **Glow pass** (`GlowCubes`): only faces with alpha in the `_e` texture, unsorted render type. Shader packs show it white.
- **Modifiers**: `ModifierEffect` fields are summed (`plus`); `reload_speed` → `StatSheet.reloadRate`; `weapons` list makes
  a module weapon-specific; `TurretInventory.hasModifierElsewhere` refuses a second copy of a kind.
- **GeckoLib mirrors geo X** (model +X is the weapon's left). **Render buffers**: finish one render type's pass before
  `getBuffer` of another. `TurretBaseBlock.core(level, pos)` resolves any base block.
- **Energy weapons**: turret `energy`, receive-only FE capability; Creative Ammo pays for shots. Tesla bolt hits carry the
  target id in `Shot.blockState`.
- **Workstation multiblock**: part index = x + width·(z + depth·y); `WorkstationBlock.core`. Parts render as model pieces
  (`assets/bastion/parts.json`, `PartModel`).

## 6. Asset pipeline

- Geometry: `tools/blockout/` → `assets/bastion/geo/*.geo.json` (after `design_weapons.py`, `git checkout` the
  missile_launcher geo: different bone order than the committed file).
- `tools/gen_textures.py` (models, `_e` glow, items incl. module glyphs `MODIFIER_GLYPHS`, particles, GUIs, `smoke_strip`):
  deterministic. Single texture: `python3 -c "import gen_textures as g; ..."` from `tools/` (see TEXTURES dict).
- `tools/gen_recipes.py`: all survival recipes (workstations); edit tables, run it.
- `tools/gen_sounds.py` (placeholders), `tools/fit_item_display.py`, `tools/gen_structures.py` (GameTest arenas).
- **Store icon**: `./gradlew runClient -Pshowcase=icon -PshowcaseWorld=showcase_fx` (T3 Gun Turret on lime, angles
  15/25/35/325/335/345) then `python3 tools/make_icon.py [angle]` (default 335) → `docs/release/icon.png`.
- Store screenshots: `docs/release/screenshots/` (full) + `screenshots-840/` (CurseForge description, < 850 px); take new
  ones with `-PnoDevMods` (no ToroHealth numbers). `DESCRIPTION.curseforge.html` is generated from DESCRIPTION.md
  (python `markdown` in a scratch venv, Modrinth links swapped for CurseForge ones, screenshot markers → `[IMAGE: ...]`).

## 7. Testing

- **GameTests: 97 pass**: TurretRepulsorTests 14, TurretFeedHubTests 15, TurretMortarTests 2, TurretRailgunTests 5, TurretBaseTests 5, TurretCombatTests 7 (bullets take time, pass non-targets),
  TurretSystemsTests 22 (Choke, creative tabs, zombie attacks turret), TurretLargeTests 7 (Autoloader reloads faster),
  TurretTeslaFlameTests 7 (flamethrower sweeps to next target), TurretLaserTests 4, TurretWorkstationTests 7, dev
  CreatePlacementTests 1, dev PipezEnergyTests 1. Negative controls were checked for the bullet pass-through and sweep tests.
- **Scenes** (`-Pshowcase=<name> -PshowcaseWorld=showcase_fx`): default, `weapons`, `missiles`, `automation`,
  `elemental`, `laser`, `workshop`, `stress`, `icon`, `railgun`, `mortar`, `feedhub`, `repulsor`, `health`, `placement`. Shots in
  `run/screenshots/showcase_*.png`. Store shots: add `-PnoDevMods` (no ToroHealth numbers).
- **Load test**: `-Pshowcase=stress -PstressCount=200 [-Pjfr=<file>]` over the spawn chunks; `[stress]` MSPT/FPS lines.
  Mac (M4 Air): 200 turrets idle 75-80 FPS, combat 55, MSPT ~7; 400 turrets combat 39 FPS, MSPT 10.
- **`-PwithPerfMods`** (Radium, AI Improvements, ModernFix, FerriteCore, Clumps): combat MSPT -35%, spikes gone; tests pass.
- **`-PwithRenderMods`** (Embeddium + Oculus, MakeUp shader in `run/shaderpacks`): Embeddium identical; with shaders the
  model glow is white and holograms green (known limit in DESCRIPTION.md).
- Dev commands: `/bastiondev zombie|skeleton|ravager [hp <= 5000] [count <= 200]` (+ `busser [hp] [count] [variant]` when Spore is
  loaded, variant via Busser#setVariant(int): default 0, kidnapper 1, kamikaze 2, noxious 3 = stingers, transporter 4; `owndamage true|false`) (DevCommands raises the max_health
  cap by reflection, dev only; dev test `devCommandSpawnsTankyMobs`), `/bastiondev automation [report]`, `/bastion vfx <preset>`.

## 8. Backlog / known gaps

- Base Extender (PLAN Fase 10): spec kept, **tidak direncanakan dulu**.
- Both user ideas of 2026-10-07 are done (Feed Hub multiblock, Repulsor). Addon support ("turret pack", like TaCZ gun packs, no Java; recorded 23:09, after release ~1.0.0, PLAN 7.6 end). User-planned next: Plasma Cell turret, Mk1-3
  tiers (bonuses undecided), 3x3x1 base.
- **Offered, waiting for the user's answer:** (a) UV overlap in 3 older models: **done 22:50** (see NOW 0); (b) a night version of
  `12_railgun_charge` (violet reads much stronger at night); (c) Repulsor Dome pylon caps are steel grey like other
  turrets' steel parts: make them dark gunmetal or glowing blue if the user meant those (orb fixed 20:01).
- Deferred minors (user decides): FeedHubMenu.stillValid ignores
  isRemoved, two misplaced registry comments (BastionBlocks:36, BastionBlockEntities:36); Repulsor riders cost FE without
  moving, wave/dome visuals use data.range() not stats.range() (Range Module), the wave is drawn from the muzzle
  (~0.75 block past the push reach), focusAngle unbounded (Laser too).
- Shader packs: paint the cyan into `_e` textures and tint only for damage/heat (glow shows white under Oculus).
- Fase 8: Jade/JEI/Ponder compat (JEI would list workstation recipes), particle budget (flamethrower ~130/s vs 120),
  dedicated server test with a second player. All sounds are placeholders.
- No FE generator in the mod (ask before adding one).

## 9. Environment gotchas

- **Run with `--offline`**: ForgeGradle downloads Mojang version metadata (`DownloadMCMeta`) every run without a read
  timeout; a stalled connection hangs Gradle at "Configure project" with ~0% CPU. Kill it (`jstack <pid>` shows
  `DownloadMCMeta`), rerun with `--offline`. Asset downloads can also time out ("Failed to get asset"): just retry.
- Load tests away from the spawn chunks measure chunk generation, not turrets.
- zsh does not word-split `$VAR` (list paths literally); zsh aborts a chain on a glob with no match.
- `rtk` hook rewrites `grep`: use `rtk proxy grep`. `-PperfMods` clashed with the configuration name: flags are `withX`.
- **Never** `open(p,"w").write(open(p).read()...)` in one expression (it once truncated a file).
- `runClient` fails with "Can't find a primary monitor" while the Mac screen is locked; keep `caffeinate -dimsu` on.
- Blockbench MCP renders stale images while its window is hidden. Contact sheets hide thin lines: check full-res crops.
- GameTest: own world `run/gametestworld`, entities wiped per run; structures sit a block up, so a template's floor is at
  test y 1 (stand things at y 2); `destroyBlock` never drops loot; arenas persist (closed boxes, one batch per test, disarm turrets);
  `helper.onEachTick` for tick-exact checks; `FakePlayerFactory` for player-driven tests.
- Memory: `~/.claude/projects/-Users-alifrizzaz-Documents-Minecraft-Modding-Turret/memory/bastion-phase0-defaults.md`.
