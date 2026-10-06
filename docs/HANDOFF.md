# Bastion Turrets — Session Handoff (2026-10-06, 19:30 WIB)

Read this first in a new session, then `CLAUDE.md` and `docs/PLAN.md` (the spec). Manual test list: `docs/TESTING.md`.

---

## 0. Where we stopped

Fase 9 Workstations + Charging Station committed as `f460e59` (not yet play-tested by the user).
Committed on top (user checked in game): the "Workstations & Parts" creative tab split into "Bastion: Workstations"
(5 stations + Creative Power Source, id `bastion_workshop`) and "Bastion: Parts" (the 33 parts, id `bastion_parts`).

**Uncommitted (19:30): bullets + big-base performance** (user: "a modpack with tens, maybe hundreds of turrets per
base"; travel time "like TaCZ"; picked option B = bullets as data, not entities). Build + 56 GameTests pass, user has
not play-tested it. What changed, measured with the dev stress scene (200 turrets, same machine/world):
- Gun/MG/Shotgun/Sniper fire **bullets with travel time** (`weapon/Bullets`), auto-lead, pass non-targets. Cost = hitscan.
- Render: glow pass draws only lit faces (`GlowCubes`) with an unsorted glow type; the still base is **meshed into the
  chunk** (`BakedTurretBaseModel`, from the GeckoLib geo itself); casings capped (192, within 32 blocks); shot sounds
  budgeted (8/tick, near first). FPS 200 turrets: idle 28 → 75-80, combat 20 → 55. 400 turrets: idle 58, combat 39, MSPT 10.
- Network: every `sendNear` message is queued and leaves as **one `TurretEvents` packet per player per tick**.
- Also uncommitted: anti-slop rewrite of `docs/release/DESCRIPTION.md` (+ bullets paragraph), UPLOAD changelog lines.

**20:50: CurseForge has 0.1.0-beta.1 in review** (repo public, tag `v0.1.0-beta.1` = `2c08cd7`). Since then, uncommitted:
**smoke trails** for rockets and missiles (`SmokeTrailRenderer`: ribbon through the tick positions, widening/rising/fading,
world-lit, texture `vfx/smoke_trail.png` from `tools/gen_textures.py smoke_strip`, exhaust glow billboard; fed by
`RocketEffects`; denser trail particles; ribbon edges shared per point; the trail runs on into the blast via
`SmokeTrailRenderer.end` because the server removes a rocket one tick before its hit point) and **version 0.1.0-beta.2**.
Also (user, 22:40): turrets retarget the same tick their target is lost (`searchTimer = 0`), and **`sweep_tolerance`**
(Aim JSON, Flamethrower 180, Machine Gun 30) keeps a burst firing while swinging to the next target (within 10 ticks of
the last shot). GameTest `flamethrowerSweepsToNextTarget` (negative control checked); 57 pass. Rockets/missiles now explode at
`impactPoint` (flight segment vs the target's box) instead of `EntityHitResult#getLocation` (the feet: the trail dipped
down to them). **Autoloader** module (user, 23:10): `reload_speed` 0.4 (ModifierEffect field, StatSheet.reloadRate), fits
Rocket + Missile Launcher; missile `reload_ticks / reloadRate`, rocket fire interval too (`WeaponType.firesAsItReloads`).
Recipe in gen_recipes.py (piston), glyph ↻ in gen_textures.py. Defaults not confirmed: +40%, recipe. 58 GameTests.
23:45: salvo trails zigzagged: (1) `SmokeTrailRenderer.end` matched the nearest trail, often another missile's ->
TurretImpactEvent now carries the rocket's entity id (`source`, PROTOCOL "8"); (2) homing lead on raw target velocity
made missiles weave around blast-knocked targets -> `TurretRocketEntity.targetVelocity` smoothed (0.8/0.2). Store icon
from our own render: `-Pshowcase=icon` (IconShowcase, lime chroma) + `tools/make_icon.py` -> `docs/release/icon.png`
(Modrinth refuses generated art). `-PnoDevMods` drops ToroHealth for clean screenshots; 05_missile_salvo replaced.
**CurseForge has beta.2 (`e43a732`); these fixes are 0.1.0-beta.3.** Modrinth gets beta.3 as its first version.
Modrinth not uploaded yet.

**Release prep (18:30, committed; repo pushed to `AlifRizza/bastion-turrets`, private):** version `0.1.0-beta.1` (beta channel; jar `bastion-1.20.1-0.1.0-beta.1.jar`,
authors `AlifRizza`), `docs/release/` = DESCRIPTION.md (paste-ready page text), UPLOAD.md (fields, changelog, gallery
captions, icon/banner prompts, pre-publish checks), 10 curated screenshots, `reference/` = turret crops to attach
to the image prompts (Tesla crop: a test husk behind it was painted out). The user pushes to GitHub themselves
(`gh` logged in as AlifRizza; planned repo `AlifRizza/bastion-turrets`). Open: Bastion has **no FE generator**, survival
needs another FE mod (stated in the description; user not asked yet whether to add one).

Next steps:
1. Wait for the user's in-game feedback (tabs, workstations); fix what they report.
2. Commit only when they say "commit".
3. Open items the user mentioned for later (do not build unasked):
   - **Plasma Cell** + the turret that uses it (charged at the Charging Station: one line in `CHARGE`, `tools/gen_recipes.py`).
   - **Weapon module tiers Mk1/Mk2/Mk3** (all current = Mk1). The user has **not decided** what each tier gives:
     ask first. Options offered: pure stats / stronger ability per tier / extra module slots.
   - **3x3x1 large base** (will reuse the multiblock code).

Settled earlier today (no action needed): the Create mechanical arm loads turrets directly (user confirmed); a belt
funnel on a raised turret works; funnels can be placed on turret sides (hitbox `blocksBuilding = false`).

---

## 1. Project basics

- Minecraft Forge **1.20.1**, Forge 47.4.26 (min `[47.2,)`), Parchment 2023.09.03, **GeckoLib 4.8.4**, Java 17, Gradle 8.8.
- Mod id `bastion`, package `dev.bastion`, root `/Users/alifrizzaz/Documents/Minecraft Modding/Turret`.
- **Git** since 2026-10-06: branch `main`, no remote, commits `df3660b` (initial), `f1819a3` (charge tracking + Choke
  Module), `f460e59` (Fase 9 workstations),
  then the creative tab split. Commit **only when the user asks**. Ignored: `run/`, `build/`, `.gradle/`, the third-party `/references/`.
- Build/run: `./gradlew build`, `./gradlew runClient`, `./gradlew runGameTestServer` (**54 tests**), `./gradlew runServer`.
- Dev runs include `src/dev` (never in the jar): dev commands, dev-only GameTests, scripted screenshot scenes.
- Dev runtime mods: Create 6.0.8 + Ponder + Flywheel + Registrate + MixinExtras (runtimeOnly), **ToroHealth** damage
  numbers (configuration `clientDevMods`, **runClient only**: client-only mods crash server/GameTest runs).
- Language: answer in the language of the user's message (Indonesian or English). Code, comments, docs in English.
- The user has ADHD-mode + ponytail output styles on: action first, short, minimal code.

## 2. Hard rules (CLAUDE.md + PLAN §1, §9)

- Read `docs/PLAN.md` before work; do only what is asked. Ask before guessing gameplay (PLAN 9.7).
- **NO-VANILLA visuals**: no vanilla particles, sounds, textures, explosion visuals, GUI textures, vanilla fire.
  Everything comes from our generators (`tools/`) and the VFX framework.
- No copying GPL code (`references/` is study only).
- Strict client/server split (client code only in `client` packages, reached via `DistExecutor`/client events).
- Balance numbers in JSON / config / `tools/gen_recipes.py`, never hardcoded.
- After each feature: `./gradlew build` passes + a checklist in `docs/TESTING.md`.
- Never write `eula=true`. Never bypass macOS TCC or the screen lock.
- **Never overwrite `run/saves/showcase`**: it holds the user's hand-built Create rig. Screenshot scenes run in a
  scratch copy: `cp -R "run/saves/New World" run/saves/showcase_fx` then `-PshowcaseWorld=showcase_fx`, delete after.

## 3. Decisions (user-confirmed unless marked)

| When (2026-10-06) | Decision |
|---|---|
| Fase 0/1 | Create only as dev runtime (+ optional compileOnly compat). Mined base keeps tier + inventory. GUI 216x200, no text overlap. |
| morning | Creative Ammo (creative-only, fits every weapon, never used up). `/bastiondev zombie [hp] [count]`. ToroHealth. |
| 09:05 | Hitbox must not block clicks; zombies attack turrets (`TurretAggro`). GUI state = lamp; all GUI text auto-fits. Players cannot melee turrets. |
| 11:00 | Redesign: uniform **gunmetal + one cyan glow**, per-weapon colours only in VFX. Base = 1 block, weapons ~1 block. Floor/wall/ceiling mounting. |
| 11:45 | Sniper: charge shot, pierces 2. Rocket Launcher: dumb-fire, splash only valid targets, min range 4. Sniper Rounds + Rockets. |
| 12:10 | Custom rocket explosion; rocket tubes empty one by one and reload. |
| 13:10 | **Large Turret Base 2x2x1**, floor only; module sizes separate. **Missile Launcher**: 6+6 tubes, yaw only, fixed 30°, homing, Mode Single/Salvo, salvo spreads evenly (one target gets all). |
| 14:10 | Create arm loads turrets directly (no funnel). |
| 14:35 | **Tesla Coil** (2x2, **FE**, zaps 2 random valid targets, custom lightning). **Flamethrower** (1x1, **Fuel Canister**, cone hits all valid targets, **custom burn**). |
| 15:30 | Flamethrower scorch marks grow instead of stacking. |
| 15:45 | **Laser Rifle** (1x1): Laser Cell ammo, single heavy shot, pierces only valid targets, stops at blocks, light gathers at the tip while charging. |
| 16:11 | Aiming laser and beam must line up, straight out of the barrel. Charged weapons must not restart the charge on moving targets. |
| 16:40 | Weapon-specific modules; first: **Choke Module** (Shotgun, spread -40%). Mk1-3 weapon tiers planned, bonuses **undecided**. |
| 16:45 | Creative tabs split: Turret Bases (+tools), Weapon Modules, Ammo, Modules (+ Workstations & Parts later). |
| 17:01 | **Fase 9 Workstations** (sizes w x d x h): Part Workstation 2x1x2 + Part Assembler 2x2x2 (timed), Module Workstation 1x1x2 + Ammo Workstation 1x1x2 (instant for the player + timed for automation), all on **FE**; crafting-table recipes removed; **own part set per weapon**; GeckoLib models with working animations; full custom GUI; automation-friendly. |
| 17:36 | **Charging Station 1x1x1** charges energy ammo (Laser Cell now, Plasma Cell later). |
| 18:00 | Creative tab "Workstations & Parts" split into **Workstations** (stations + Creative Power Source) and **Parts**. |
| 18:30 | **Bullets with travel time** for Gun/MG/Shotgun/Sniper (like TaCZ), **option B**: data stepped per tick, no entities. Target: bases with hundreds of turrets; optimise by measurement. |
| defaults 18:30, **not confirmed** | No bullet drop. All four auto-lead (`lead_accuracy` 0.9, Targeting AI adds). Bullets **fly through non-targets** (old hitscan hit anything in the line: behaviour change, told the user). Speeds (blocks/tick): Gun 6, MG 5, Shotgun 4, Sniper 12. |
| defaults, **not confirmed** | Mismatched ammo stays but never fires. Precision Lock x1.5 stacks with headshot. Default targets Hostile+Boss. T1 destroyed → damaged base. Repair kit 40%. Turrets never shoot turrets. Large base HP x2.5. Tesla: lone target takes both bolts; 60k/1k per tick/3k per shot; energy lost when the module is removed. Flamethrower ignores fire-immune mobs; water/rain put burn out; burn 1.5/s for 4 s. Workstations: 3 parts per weapon/base cut from the 3D model (Missile needs 2 pods); Creative Power Source block; damaged bases restored at the Part Assembler; recipe numbers in `tools/gen_recipes.py`; workstation FE 50k, 2k/t (config). |

## 4. What exists

Fase 0–7 complete; Fase 8 partial (lang en_us/id_id done; Jade/JEI/Ponder/Iris compat and profiling not started);
Fase 9 (workstations) built. PLAN.md has a Fase 9 section.

**Blocks**: `turret_base` (1x1, any face), `large_turret_base` (2x2, `quadrant`/`core`), five stations
(`part_workstation`, `part_assembler`, `module_workstation`, `ammo_workstation`, `charging_station`; `WorkstationBlock`
with `facing` + `part`), `creative_power_source` (creative only, pushes unlimited FE to neighbours).

**Items**: 9 weapon modules (small: gun, machine_gun, shotgun, sniper, rocket_launcher, flamethrower, laser_rifle;
large: missile_launcher, tesla), ammo (kinetic_rounds, scatter_shells, sniper_rounds, rockets, missiles, fuel_canister,
laser_cell, empty_laser_cell, creative_ammo), 9 modules (8 general + choke_module), repair_kit, tier_upgrade_kit_t2/t3,
turret_configurator, damaged (large) turret bases, **33 parts** (3 per base/weapon, `BastionItems.PARTS`).
Creative tabs: Turret Bases, Weapon Modules, Ammo, Modules, Workstations, Parts (sorted by item type automatically in
`BastionItems`; GameTest `creativeTabsSortItems` checks every item is in exactly one).

**Weapons** (`data/bastion/turret_weapons/*.json`, types in `BastionWeaponTypes`):

| Weapon | Profile | Key numbers |
|---|---|---|
| Gun | bullet 6 b/t, Precision Lock | 7 dmg, 32 range, 12 t, lock 20 t → x1.5 (+x1.5 headshot) |
| Machine Gun | bullet 5 b/t, spin-up, heat/overheat, slowness | 2.5 dmg, 24 range, 12/s at full spin, 0.5 ammo/shot |
| Shotgun | 8 bullets 4 b/t, falloff, knockback, pump | 2.5/pellet, 12 range, 24 t, spread 12° (Choke: 7.2°) |
| Sniper | charged bullet 12 b/t + laser sight | 24 dmg, 64 range, 30 t charge, pierces 2 |
| Rocket Launcher | projectile, lead, splash | 8 + 12 splash r3.5, 40 range, min range 4 |
| Missile Launcher (large) | homing, fixed 30°, 12 tubes | 7 + 6 splash r2.2, 48 range, reload 30 t/tube, salvo |
| Tesla Coil (large) | ARC, never turns, FE | 10/bolt x2, 16 range, 12 t charge + 18 t, 3k FE/discharge |
| Flamethrower | CONE, custom burn | 1.2/pulse every 2 t + burn 1.5/s 4 s, 8 range, 1 canister ≈ 4 s |
| Laser Rifle | CHARGED beam, pierces all valid targets | 28, 40 range, 50 t charge + 50 t, 1 Laser Cell/shot |

**Turret systems**: tiers T1–T3, HP via invisible `TurretHitboxEntity`, regen, repair, destruction, owner/trusted
protection, redstone, filters (categories, players, trusted, per-entity rules, priorities), configurator, modifiers
(incl. weapon-specific), servo + scan sweep, lead, state machine (DISABLED … NO_AMMO), comparator, hopper/Create
automation (ammo-only capability), FE capability for energy weapons.

**Workstations** (package `dev.bastion.workstation`): 6 input slots + 1 output, FE buffer, selected recipe kept in
NBT; timed processing (FE per tick); instant Craft for Module/Ammo (inputs first, then player inventory, shift = x8);
automation from any block: insert only the selected recipe's ingredients, extract only the output. GUI: category tabs,
recipe list, turning 3D preview (flat icons face-on), ingredients have/need, progress, energy, status / Craft button.

**VFX** (`VfxManager`/`VfxPresets`): muzzles/impacts per weapon, rocket/missile trails + custom explosions, Tesla
`ArcRenderer`, `BeamRenderer` + `LaserChargeRenderer` (laser), `FlameJetRenderer` + flame particles + `BurnEffects`,
growing scorch decals, workstation sparks/welds/press puffs/charging arcs (`WorkstationEffects`), dynamic lights,
camera shake, screen flash. Debug: `/bastion vfx <preset>`.

**Network** (`BastionNetwork`, PROTOCOL **"7"**): registered packets are only TurretEvents (S→C bundle), WeaponDataSync,
TurretConfigUpdate (C→S), BurnSync, WorkstationAction (C→S). `sendNear` queues TurretStateSync, TurretFireEvent (with
bullet `speed`), BulletImpact, SpinSync, RackSync, TurretImpactEvent; flushed at the level tick END, one packet per player.

## 5. Architecture notes that are easy to get wrong

- **Mount frame**: turret logic in turret space (+Y = mount axis); `toWorld/toLocal` use `facing().getRotation()`;
  `TurretBaseRenderer.rotateBlock` applies the same (+ offset to the 2x2 centre).
- **Weapon hooks** (`WeaponType`): `tick`, `fireInterval`, `ready`, `chargeTicks`, `canTarget`, `aimPoint` (lead),
  `sight` (engage point or null), `outOfAmmo`. `Aim.fixedPitch()` pins pitch; `turn_speed 0` = never turns (no sweep).
- **Charge holding** (`TurretBaseBlockEntity.tickServer`): once a charge starts it survives aim drift; when full it waits
  for `aim_tolerance`; only losing the target resets it. Non-charged weapons behave as before.
- **Aiming laser** (`TurretHolograms.laserSight`): along the barrel via `Hitscan.trace` (the server's ray). Do not use
  `ProjectileUtil.getEntityHitResult` for points: its hit location is the entity's feet.
- **GeckoLib mirrors geo X** (model +X is the weapon's left). Missile tube mouths verified against bones.
- **Energy weapons**: turret `energy` (NBT `Energy`), receive-only FE capability (`max_input`, `energy_capacity` params).
  Menu data slots carry FE in hundreds. Creative Ammo pays for shots.
- **Tesla bolts**: `TurretFireEvent.Shot.blockState` holds the target's entity id for Tesla hits.
- **Burn**: `TurretBurn` (server, ServerTickEvent) + `BurnSync` → `BurnEffects`. `turret_flame`/`turret_burn` are in
  `minecraft:is_fire`.
- **Weapon-specific modules**: `ModifierEffect.weapons` (ids it fits) + `spread`; `TurretInventory` refuses a module next
  to another weapon and skips it in `modifierEffect()` after a swap (red slot).
- **Workstation multiblock**: part index = x + width·(z + depth·y), x to the right of the front, z away from it; the core
  (part 0) is where the player clicked. `WorkstationBlock.core(level, pos)` resolves any block. Parts forward
  capabilities via `WorkstationPartBlockEntity`. The renderer centres the model on the footprint (`rotateBlock`).
  Crafted → block event `CRAFTED` → client `craft` clip + `WorkstationEffects.crafted`.
- **Parts render as model pieces**: `assets/bastion/parts.json` (part → model, texture, bones). `PartModel` resets the
  shared baked bones to rest and hides cubes per bone (`setHidden` then `setChildrenHidden(false)`); `PartItemRenderer`
  unhides all in `postRender`. GeckoLib: `setHidden` hides own cubes + children; `setChildrenHidden` only children.
- **Render buffers**: with `MultiBufferSource.BufferSource`, `getBuffer` for another render type ends the previous
  batch: write a pass completely before asking for the next buffer (Arc/Beam/LaserCharge renderers do passes).
- `TurretBaseBlock.core(level, pos)` resolves any base block; use it, not `getBlockEntity(pos)`.
- **Bullets** (`weapon/Bullets`): fired in `onFire` via `Bullets.fire` (one step at once, so point blank hits this tick),
  stepped at LevelTickEvent START, `Hit.endTick` at END (Shotgun knockback summed per shot). Fire event shots carry the
  bullet id in `blockState` and the path end; the client tracer (`TracerRenderer.spawnBullet`) flies at the event's speed
  until `BulletImpact` lands it. A bullet that runs out of range sends nothing.
- **Baked base** (`client/render/BakedTurretBaseModel`, loader `bastion:turret_base` in `models/block/*turret_base.json`):
  built lazily per facing x tier x quadrant from `GeckoLibCache` with GeckoLib's own `RenderUtils` transforms; winding
  fixed per quad (chunk layers cull back faces). Tier comes as ModelData (`TurretBaseBlockEntity.MODEL_TIER`); a client
  tier change re-meshes. Render shape MODEL. `TurretBaseRenderer` draws only `ANIMATED` bones (energy_ring) in its main
  pass, plus all glow faces. Trade-off: muzzle-flash dynamic light no longer brightens the base itself.
- **Glow pass** (`GlowCubes`): per model, the faces whose UV rect has alpha in the `_e` texture; EmissiveLayer sets
  `GlowCubes.active` around its reRender. Renderers opt in by overriding `renderCubesOfBone` (base, weapon).

## 6. Asset pipeline

- Geometry is generated: `tools/blockout/` (`kit.py`, `design_base.py`, `design_large_base.py`,
  `design_weapons.py`, `design_workstations.py`) → `assets/bastion/geo/*.geo.json`.
  **Gotcha**: `design_weapons.py` rewrites `missile_launcher_turret.geo.json` with a different bone order than the
  committed (Blockbench round-trip) file; after running it, `git checkout src/main/resources/assets/bastion/geo/missile_launcher_turret.geo.json`.
- `tools/gen_textures.py`: model paint by bone material (`MODEL_MATERIALS`), `_e` emissive, items, particles, GUIs
  (`turret_gui`, `workstation_gui`). Deterministic (re-running changes nothing else).
- `tools/gen_sounds.py`: placeholder OGGs (render only new ones via the module's `SOUNDS` dict; full run is slow).
- `tools/fit_item_display.py`: item display transforms from geo bounds; parts use rotation-aware bounds of their bones.
- `tools/gen_recipes.py`: **all survival recipes** (workstation recipes under `data/bastion/recipes/workstation/<station>/`,
  the 5 station crafting recipes; deletes old crafting recipes). Edit the tables there, then run it.
- `tools/gen_structures.py`: GameTest templates (`arena`, `range`).
- Blockbench `.bbmodel` sources exist for the original turrets only; new models (Tesla, Flamethrower, Laser, workstations)
  live only as generated geo.
- Animations: JSON in `assets/bastion/animations/` (written by small Python snippets; clips `idle`, `working`/`aim`,
  `fire`/`craft`, `deploy`, `no_ammo`, `held_idle`).

## 7. Testing

- **GameTests: 56 pass**: `TurretBaseTests` 4, `TurretCombatTests` 7 (incl. bullets take time, bullets pass non-targets), `TurretSystemsTests` 22 (incl. Choke Module,
  creative tabs), `TurretLargeTests` 6, `TurretTeslaFlameTests` 6, `TurretLaserTests` 4 (pierce, walls, strafing target
  for Laser + Sniper), `TurretWorkstationTests` 6 (multiblock, power + automation filter, assembler, instant craft,
  charging, crafting table only makes stations), dev `CreatePlacementTests` 1.
- **Screenshot scenes** (`./gradlew runClient -Pshowcase=<name> -PshowcaseWorld=showcase_fx`, scratch world as in §2):
  default, `weapons`, `missiles`, `automation`, `elemental` (Tesla + Flamethrower), `laser`, `workshop` (all stations,
  GUIs, part icons). Shots in `run/screenshots/showcase_*.png`; log lines `[showcase]` in the gradle output.
- **Load test**: `./gradlew runClient -Pshowcase=stress -PstressCount=200 -PshowcaseWorld=showcase_fx [-Pjfr=<file.jfr>]`
  builds N T3 turrets (Gun/MG/Shotgun/Sniper) over the world spawn + N/2 walking 100k-HP husks, logs `[stress]` MSPT/FPS
  for empty / idle / combat, vsync off, no ToroHealth. FPS caps at 120 (display), so use 200+ turrets. JFR: `jfr print
  --json --events jdk.ExecutionSample` (render thread is `main` on macOS).
- **`-PwithPerfMods`** (runClient, runGameTestServer): adds Radium (Lithium port), AI Improvements, ModernFix, FerriteCore,
  Clumps from Modrinth (ids in build.gradle). Measured 19:55, alternating runs: 200 turrets combat MSPT 6.7-7.9 → 4.7-4.9,
  FPS 48 → 60; 400 turrets combat MSPT 10.5 (spike 91) → 6.6 (max 8), FPS 39 → 46. All 56 GameTests pass with them.
- **`-PwithRenderMods`** (runClient): Embeddium 0.3.31 + Oculus 1.8.0; shader pack `run/shaderpacks/MakeUp-UltraFast-9.5g.zip`,
  switched in `run/config/oculus.properties`. 20:05: Embeddium renders everything as vanilla (baked base, glow, holograms,
  tracers). Oculus + MakeUp: no crash, turrets cast shadows, but the model glow shows **white** (the shader ignores the
  vertex tint EmissiveLayer colours it with) and holograms **green**. Fix idea: paint the cyan into the `_e` textures
  and tint only for damage/heat. Noted as a known limit in DESCRIPTION.md.
- Dev commands: `/bastiondev zombie [hp] [count]`, `/bastiondev automation [report]`, `/bastion vfx <preset>`.
- Manual checklists: `docs/TESTING.md` (sections per feature, incl. Workstations and Charging Station).

## 8. Backlog / known gaps

- User-planned: Plasma Cell turret, Mk1-3 weapon tiers (ask about bonuses), 3x3x1 base.
- Fase 8: Jade/JEI/Ponder/Iris/LambDynamicLights compat (JEI would show workstation recipes), profiling against PLAN
  budgets (the flamethrower spawns ~130 particles/s, over the 120 budget), dedicated server test with a second player.
- All sounds are placeholders. Base damaged/destroyed/tier_up clips missing (VFX cover them).
- `.bbmodel` sources for the new models; an FE mod in the dev run for real cable tests (Creative Power Source meanwhile).
- Old 1x1 turrets placed before the wall/ceiling update load as floor turrets.

## 9. Environment gotchas

- Load tests away from the spawn chunks measure chunk generation, not turrets (first numbers today were off 3x).
- zsh does not word-split `$VAR`: `git stash push -- $FILES` fails; list the paths literally.

- `rtk` hook rewrites `grep`: use `rtk proxy grep`. zsh aborts a command chain on a glob with no match (use `find -delete`).
- **Never** `open(p,"w").write(open(p).read()...)` in one expression (it once truncated a file). Patch read-then-write
  (`<scratchpad>/patchlib.py`: `patch`, `add_import`, `sort_imports`). With git now, `git diff` / `git checkout` recover.
- `runClient` fails with "Can't find a primary monitor" while the Mac screen is locked
  (`ioreg -n Root -d1 | grep CGSSessionScreenIsLocked`); keep `caffeinate -dimsu` on for visual runs.
- Blockbench MCP renders stale images while its window is hidden.
- Contact sheets downscale thin lines away: check full-resolution crops.
- `GameTestHelper.destroyBlock` never drops loot (use `level.destroyBlock(pos, true)`). GameTest arenas persist and
  batches overlap: closed boxes, one batch per test, disarm turrets. A placed water source spreads across the arena:
  wall it in. `FakePlayerFactory.getMinecraft(level)` works for player-driven tests (`useItemOn`, inventories).
- Memory: `~/.claude/projects/-Users-alifrizzaz-Documents-Minecraft-Modding-Turret/memory/bastion-phase0-defaults.md`.
