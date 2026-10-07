# Bastion Turrets — Session Handoff (2026-10-07, 13:30 WIB)

Read this first in a new session, then `CLAUDE.md` and `docs/PLAN.md` (the spec). Manual test list: `docs/TESTING.md`.
Store/upload material: `docs/release/` (DESCRIPTION.md, DESCRIPTION.curseforge.html, UPLOAD.md, icon.png, screenshots).

---

## 0. Where we stopped

**Released (public beta):**
- GitHub `AlifRizza/bastion-turrets` is **public**. **0.1.0-beta.4** committed, tagged `v0.1.0-beta.4` and pushed
  2026-10-07 13:30 (the user asked for the push this time; otherwise the user pushes). Tags beta.1-4 all on GitHub.
- **beta.4 upload**: the user uploads `build/libs/bastion-1.20.1-0.1.0-beta.4.jar` to CurseForge and Modrinth
  (fields + changelog in `docs/release/UPLOAD.md`).
- **CurseForge**: beta.1 and beta.2 uploaded by the user (beta.2 = `e43a732`, must stay as uploaded).
- **Modrinth**: user is creating the project; **beta.3** (`build/libs/bastion-1.20.1-0.1.0-beta.3.jar`) is its first
  version. Icon `docs/release/icon.png` (Modrinth refused AI art; this one is rendered from our model).
- Working tree clean except the user's own `modlist.html` (never commit it). beta.4 = the balance changes, Railgun and
  Mortar below (user tested in game, "commit" 13:30).

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
  pitch -10..60, turn 3°/t. Cost: 1 `rail_slugs` (Ammo Workstation) + 8k FE (cap 80k, 2k/t). Shockwave: `BastionExplosion`
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
  (range 45 flat), every 60 t, 1 `mortar_shells`. Blast = `stats.damage` 6 r1.5 (valid targets), no direct hit damage.
- Shell = third `TurretRocketEntity` type `turret_mortar_shell` via `ballistic(gravity, patch)`: gravity half before /
  half after each move (exact parabola, matches the solver), server-side, velocity synced every tick (`hasImpulse`).
- `weapon/FirePatches`: patch on the ground below the burst (clip down 4), size x size blocks, 100 t, every 5 t
  `TurretBurn.ignite` (60 t, 1.5/s) on **every** living entity in it (fire-immune skipped by TurretBurn); vanilla fire in
  free spots touching a flammable block (`BaseFireBlock.canBePlacedAt`) unless config `mortarBlockFire` false. Client:
  `TurretImpactEvent` MORTAR_BLAST + FIRE_PATCH (scale = seconds, source = width) → `MortarEffects`: renders the patch
  itself (user 13:15: the whole 3x3 must look burnt; scattered particles read as ~1 block): an alpha-blended ember layer
  over exactly the square + 4 flame sheets per block (texture `vfx/fire_sheet.png`, tileable, climbing) in 3 layers, plus
  a few particles, light, crackle, scorch. Scene step `m_patch` shows a patch alone on stone. Whistle on descent (`RocketEffects`). Barrel pitch is now clamped to the weapon's elevation
  limits on server and in the client idle pose (generalised from the Missile Launcher's fixed pitch).
- GameTests `TurretMortarTests` (2) in the new tall template `pit` (26x32x13): over a wall onto a husk, husk + cow burn,
  planks catch fire; never at a phantom or a husk inside 8 blocks. **65/65 pass.** Scene `-Pshowcase=mortar` checked.

**Open items the user mentioned (do not build unasked):** Plasma Cell + its turret (Charging Station `CHARGE` table in
`tools/gen_recipes.py`); weapon module tiers Mk1/Mk2/Mk3 (bonuses **undecided**, ask); 3x3x1 large base; whether to add
an FE generator (Bastion has none; survival needs an FE mod, stated in the description); stacking the same module twice
(today refused, one of each kind per base: user asked, answered, offered to change it).

---

## 1. Project basics

- Minecraft Forge **1.20.1**, Forge 47.4.26 (min `[47.2,)`), Parchment 2023.09.03, **GeckoLib 4.8.4**, Java 17, Gradle 8.8.
- Mod id `bastion`, package `dev.bastion`, root `/Users/alifrizzaz/Documents/Minecraft Modding/Turret`.
- Version in `gradle.properties` (`mod_version=0.1.0-beta.4`), jar `bastion-1.20.1-<version>.jar`, authors `AlifRizza`,
  license All Rights Reserved.
- **Git**: branch `main`, remote `origin` = GitHub (public). Commits: `df3660b` initial, `f1819a3`, `f460e59` Fase 9,
  `05d4bb9` tabs, `ae44021` release prep (beta.1), `2c08cd7` bullets + performance, `e43a732` beta.2, `f2c8179` beta.3.
  Commit **only when the user says "commit"**; the user pushes. Ignored: `run/`, `build/`, `.gradle/`, `/references/`.
- Build/run: `./gradlew build`, `./gradlew runClient --offline` (see §9), `./gradlew runGameTestServer` (**65 tests**).
- Dev runs include `src/dev` (never in the jar): dev commands, dev-only GameTests, scripted scenes.
- Dev runtime mods: Create 6.0.8 + Ponder + Flywheel + Registrate + MixinExtras (runtimeOnly); **ToroHealth** damage
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
| Flamethrower | CONE, custom burn, sweep 180° | 1.2/pulse every 2 t + burn 1.5/s 4 s, 8 range |
| Laser Rifle | CHARGED beam, pierces all valid targets | 28, 40 range, 50 t charge + 50 t, 1 Laser Cell/shot |
| Railgun (large) | charged slug 40 b/t, ignores armor, boss x2, biggest first, push shockwave r4 | 60, 80 range, 60 t + 40 t, 1 slug + 8k FE |
| Mortar (large) | ballistic shell (45-86°), no lead, ground targets, over walls, 3x3 fire patch 5 s burns everyone | 6 blast r1.5, 8-40 range, 60 t, 1 shell |

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

- **GameTests: 65 pass**: TurretMortarTests 2, TurretRailgunTests 5, TurretBaseTests 4, TurretCombatTests 7 (bullets take time, pass non-targets),
  TurretSystemsTests 22 (Choke, creative tabs, zombie attacks turret), TurretLargeTests 7 (Autoloader reloads faster),
  TurretTeslaFlameTests 7 (flamethrower sweeps to next target), TurretLaserTests 4, TurretWorkstationTests 6, dev
  CreatePlacementTests 1. Negative controls were checked for the bullet pass-through and sweep tests.
- **Scenes** (`-Pshowcase=<name> -PshowcaseWorld=showcase_fx`): default, `weapons`, `missiles`, `automation`,
  `elemental`, `laser`, `workshop`, `stress`, `icon`. Shots in `run/screenshots/showcase_*.png`.
- **Load test**: `-Pshowcase=stress -PstressCount=200 [-Pjfr=<file>]` over the spawn chunks; `[stress]` MSPT/FPS lines.
  Mac (M4 Air): 200 turrets idle 75-80 FPS, combat 55, MSPT ~7; 400 turrets combat 39 FPS, MSPT 10.
- **`-PwithPerfMods`** (Radium, AI Improvements, ModernFix, FerriteCore, Clumps): combat MSPT -35%, spikes gone; tests pass.
- **`-PwithRenderMods`** (Embeddium + Oculus, MakeUp shader in `run/shaderpacks`): Embeddium identical; with shaders the
  model glow is white and holograms green (known limit in DESCRIPTION.md).
- Dev commands: `/bastiondev zombie [hp] [count]`, `/bastiondev automation [report]`, `/bastion vfx <preset>`.

## 8. Backlog / known gaps

- Next: the user uploads beta.4 (§0). Then user-planned: Plasma Cell turret, Mk1-3 tiers, 3x3x1 base.
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
