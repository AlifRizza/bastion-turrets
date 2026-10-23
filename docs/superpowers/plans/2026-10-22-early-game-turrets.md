# Early Game Turrets (0.4.0) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A Makeshift Turret Base and four early turrets: Cobblestone Turret, Snowball Repeater, Potato Shotgun and Arrow Launcher. All of them are crafted at a crafting table, use easy ammo and have a rustic look. They ship as 0.4.0.

**Architecture:**
- The makeshift base reuses `TurretBaseBlockEntity`. The boolean `large` becomes a `BaseKind` enum (STANDARD, LARGE, MAKESHIFT), so slot rules, HP, drops and visuals branch in one place each.
- **Three weapons share `SlingWeapon`.** Cobblestone, Snowball and Potato fire data bullets (`Bullets`) through it, with a `pellets` param.
- **The Arrow Launcher** fires `TurretArrowEntity` (a vanilla `Arrow` subclass) on a drag-aware high arc (`Ballistics` gains drag).
- **Assets** are scripted:
  - models: `tools/blockout`
  - rustic paints: `tools/gen_textures.py`
  - placeholder sounds: `tools/gen_sounds.py`
  - recipes: `tools/gen_recipes.py`

**Tech Stack:** Forge 1.20.1 (47.4.26), Java 17, GeckoLib 4.8.4, GameTest; Python 3 asset generators.

**Spec:** `docs/PLAN.md` §8 "Turret early game":
- approved 2026-10-14;
- version 0.4.0 and the extra decisions added 2026-10-22 22:15.

Read the spec in full before any task. Numbers there are binding.

## Global Constraints

**Git and protected files**
- **No commits, no pushes, no tags.** The user commits ("jangan commit sebelum saya bilang 'commit'"). A task ends with its checks passing, not with a commit. Ignore any "commit" step in skill templates.
- Never stage or touch `modlist.html` or `sfx/`.
- Never touch `run/saves/showcase`.
- Never edit `run/config/*`.

**Gradle and test runs**
- Gradle always runs with `--offline`.
- Never start `runClient` for the user.
- Self-quitting showcase runs are allowed for checking visuals:
  1. `cp -R "run/saves/New World" run/saves/showcase_fx` once.
  2. Run `./gradlew runClient --offline -Pshowcase=early -PshowcaseWorld=showcase_fx > build/showcase-early.log 2>&1`.
  3. Read the screenshots in `run/screenshots/showcase_early_*.png`.
  4. Delete `run/saves/showcase_fx` afterwards.
- If `runClient` says "Can't find a primary monitor", the Mac screen is locked. Keep `caffeinate -dimsu` running, and never try to bypass the lock.
- Save every run to a file, then read the exit code, then grep the file. In this shell, plain `grep` is rewritten; use `rtk proxy grep`.
- GameTest: `./gradlew runGameTestServer --offline > build/gt-<task>.log 2>&1; echo "exit $?"`. It passes when the log says `All N required tests passed`. Baseline before this plan: 248.
- Known flaky tests (rerun once before treating as real): `ControlHubTests.satelliteHalvesBreakTogether`, `powerPortTakesFeOnlyWhenFormed`, `feedHubNewControllerFeeds`, `TurretLargeTests.largeBaseIsOneBlock`, `TurretBaseTests.workstationIsOneBlock` / `destroyedT3KeepsTierOnDamagedBase`, `TurretFeedHubTests.feedHubScrollFollowsShrink`, `feedHubDropsOwnShare`, `laserHitsAStrafingTarget`.
- **GameTest timing lessons** (memory):
  - A test in a freshly loaded chunk cannot look its entities up by id for a few ticks. Wait until `level.getEntity(id) == mob` before acting on a mob (see `OverwatchTests.once` / `findable`).
  - Assert on events (`startSequence().thenWaitUntil`), not fixed early ticks.
  - The arena floor is at test y 1, so stand things at y 2.
- `src/dev` (showcase) compiles only with `./gradlew compileDevJava --offline`, `runClient` or `runGameTestServer`, not with `build`.

**Code rules**
- **§1 no vanilla visuals.** Only exception: the Arrow Launcher's arrows are a vanilla `Arrow` subclass drawn by the vanilla arrow renderer (`TippableArrowRenderer`). Early projectiles do not glow and use no dynamic light. Their sprites, trails, particles, sounds, models and textures are all custom.
- **§9.5:** `net.minecraft.client` only in `dev.bastion.client..` or mixins listed under `"client"`.
- **§9.6:** balance numbers live in JSON:
  - weapon stats: `data/bastion/turret_weapons/*.json`
  - recipes: `tools/gen_recipes.py`
  - scrap drop: a loot table
  - base HP: config (`makeshiftBaseHealth`)
- **Generators:** never write `open(p,'w').write(open(p).read()...)` in one expression; read first, then write.
- **`tools/gen_textures.py`** rewrites every texture when run whole. Generate single textures with `cd tools && python3 -c "import gen_textures as g; ..."`, as HANDOFF §6 shows.
- **`tools/blockout/design_weapons.py`** writes every weapon geo. After running it, `git checkout` the missile_launcher geo (HANDOFF §6). `git diff --stat src/main/resources/assets/bastion/geo` must show only the new files.
- **Lang:** every new key goes in both `en_us.json` and `id_id.json`. Insert with a json round-trip, `json.dumps(d, indent=2, ensure_ascii=False)` plus the trailing newline; the files round-trip unchanged.
- **Style:** match the surrounding code. One-line Javadoc on new types and non-obvious methods; `ponytail:` comments for deliberate shortcuts; no speculative abstractions.

**Names and ids**

| Thing | Item / block id | Weapon JSON / type id | Ammo tag |
|---|---|---|---|
| Makeshift Turret Base | `makeshift_turret_base` | (base) | |
| Cobblestone Turret | `cobblestone_turret` | `cobblestone` | `bastion:ammo/pebble` = cobblestone, cobbled_deepslate |
| Snowball Repeater | `snowball_repeater` | `snowball` | `bastion:ammo/snow` = snowball |
| Potato Shotgun | `potato_shotgun` | `potato` | `bastion:ammo/potato` = potato, poisonous_potato |
| Arrow Launcher | `arrow_launcher` | `arrow_launcher` | `bastion:ammo/arrow` = `#minecraft:arrows` |

**Spec numbers (exact):**

| | Cobblestone | Snowball | Potato | Arrow Launcher |
|---|---|---|---|---|
| damage | 3 | 1.5 | 2 per pellet, `pellets` 5 | 3 per arrow, `arrows` 6 |
| fire_interval | 15 | 4 | 30 | 50 |
| range | 16 | 14 | 10 | 32, `min_range` 6 |
| spread | 1.5 | 3 | 10 | ring `ring_radius` 1.5 around the aim point |
| ammo_per_shot | 1 | 0.25 | 1 | 1 (one item = one volley) |
| turn_speed | 6 | 6 | 6 | 6 |

- Makeshift base: HP 50 (config `makeshiftBaseHealth`). It has no tier, no module slots, no FE and no Connector slot.
- Destroyed: drops scrap (1 iron ingot + 2 oak planks, loot table `bastion:gameplay/makeshift_scrap`). Weapon and ammo are lost.
- Mounting: floor, wall and ceiling. The Arrow Launcher fits only a floor-mounted makeshift base (FACING UP).

**My calls** (not in the spec; the user may change them):
- Bullet speeds: `bullet_speed` cobblestone 2.0, snowball 2.5, potato 1.8 ("terlihat terbang"), with `lead_accuracy` 0.9.
- Arrow Launcher arc params: `shell_speed` 2.2, `gravity` 0.05, `drag` 0.99, `min_pitch` 20, `max_pitch` 85.
- An empty makeshift base accepts `#bastion:ammo_early` (the four early tags). These tags are **not** added to `#bastion:ammo`, so empty standard and large bases never take cobblestone.
- The makeshift base is mineable with axe and pickaxe.
- A destroyed makeshift base sends its DESTROYED event at scale 0.8, which the client uses to pick the wood-crunch sound.
- Creative Ammo in an Arrow Launcher fires plain arrows.

## Review Focus

The five failure modes most likely to bite a player, each pinned by a test in the owning task:
1. **Automation feeding the wrong ammo.** A hopper into an empty makeshift base accepts early ammo only. An empty standard base still refuses cobblestone. Tests: `emptyMakeshiftTakesOnlyEarlyAmmo` (Task 3) and `emptyStandardBaseRefusesCobblestone` (Task 3).
2. **An Arrow Launcher reaching a wall or ceiling makeshift base** by hopper or shift-click. Expected: refused. Test: `arrowLauncherNeedsAFloorBase` (Task 4).
3. **Arrows hitting things they should not.** Expected: they pass through the cow and the villager, and are never picked up. Tests: `arrowsPassNonTargets`, `arrowsCannotBePickedUp` (Task 4).
4. **Creative Ammo in early weapons.** Expected: the Arrow Launcher fires plain arrows and the sling weapons fire normally. Test: `creativeAmmoFeedsEarlyTurrets` (Task 4).
5. **Breaking versus destroying a makeshift base.** Breaking it keeps its inventory (save/load). Destroying it drops scrap and never a "damaged base". Tests: `makeshiftKeepsItsContents`, `destroyedMakeshiftDropsScrap` (Task 1).

---

### Task 1: BaseKind and the Makeshift Turret Base (server)

**Files:**
- Create: `src/main/java/dev/bastion/turret/BaseKind.java`, `src/main/java/dev/bastion/turret/MakeshiftTurretBaseBlock.java`
- Modify:
  - `TurretBaseBlockEntity` (kind, maxHealth, destroy, energy capability)
  - `TurretInventory` (kind supplier, isActive, isItemValid)
  - `WeaponModuleItem` (kind)
  - `TurretBaseItem`
  - `TurretBaseBlock` (kit refusal, mount messages)
  - `TurretMenu` (open data: kind)
  - `BastionConfig` (`makeshiftBaseHealth`)
  - `BastionBlocks`, `BastionItems` (block, item; tab: no T2/T3 copies for makeshift), `BastionBlockEntities` (valid block)
- Create data:
  - `loot_tables/blocks/makeshift_turret_base.json` (the block itself plus `Inventory` nbt, no Tier)
  - `loot_tables/gameplay/makeshift_scrap.json` (1 `minecraft:iron_ingot`, 2 `minecraft:oak_planks`)
  - `data/minecraft/tags/blocks/mineable/axe.json` (the block is added to both axe and pickaxe)
  - blockstate and block model placeholders pointing at `geo/turret_base.geo.json` until Task 2 replaces them
- Test: create `src/main/java/dev/bastion/gametest/MakeshiftBaseTests.java`.

**Interfaces (produces):**
- `enum BaseKind { STANDARD, LARGE, MAKESHIFT }` with `static BaseKind of(Block block)`:
  - `MakeshiftTurretBaseBlock` → MAKESHIFT
  - `LargeTurretBaseBlock` → LARGE
  - else STANDARD
- `TurretBaseBlockEntity.kind() -> BaseKind`. `large()` stays as `kind() == BaseKind.LARGE` (many callers).
- `WeaponModuleItem(Properties, ResourceLocation weaponId, BaseKind kind)`. The old constructors map `true`/`false` to LARGE/STANDARD. Add `kind()`; `large()` stays.
- `TurretInventory(Supplier<TurretTier> tier, Supplier<BaseKind> kind, IntConsumer onChanged)`:
  - `isActive` is false for modifier slots and CONNECTOR on MAKESHIFT.
  - The WEAPON slot needs `module.kind() == kind`.
  - `modifierEffect()` ignores slots that are not active.
- `TurretBaseItem.kind() -> BaseKind`.
- `TurretMenu` open data and client inventory carry `BaseKind` instead of `large`. Add a menu accessor `kind()`.
- `TurretBaseBlockEntity.maxHealth(TurretTier, BaseKind, ModifierEffect)`: MAKESHIFT = `BastionConfig.makeshiftBaseHealth()` (default 50, never tier-scaled); LARGE as today.
- `destroy()` on MAKESHIFT:
  - rolls loot table `bastion:gameplay/makeshift_scrap` at the base and drops the result;
  - drops no damaged base;
  - the DESTROYED event scale is 0.8.
- ENERGY capability: `LazyOptional.empty()` on MAKESHIFT.
- Tier Upgrade Kit on MAKESHIFT: no upgrade, the kit is kept, and the player sees the message `message.bastion.turret.makeshift_no_tier`.
- Mount messages:
  - an early module on a standard or large base: `message.bastion.turret.needs_makeshift_base`;
  - a standard or large module on a makeshift base: the existing `needs_small_base` / `needs_large_base`.
- `BlockInfo` (Jade text) writes no `Tier` for MAKESHIFT, and its line has no "T1".

- [ ] **Step 1: Write the failing tests** (`MakeshiftBaseTests`, template `empty` or `arena`; helpers modelled on `TurretBaseTests.placeBase` / `breakWithDrops`):
  - `makeshiftRefusesModernModules`: on makeshift, `isItemValid(WEAPON, gun)` and `(WEAPON, mortar)` are false. Early modules are covered in Task 3.
  - `makeshiftHasNoModulesConnectorOrFe`:
    - every modifier slot and CONNECTOR is `!isActive`;
    - `isItemValid(CONNECTOR, hub connector)` is false;
    - `getCapability(ForgeCapabilities.ENERGY)` is not present.
  - `tierKitDoesNothingOnMakeshift`: a FakePlayer holding `TIER_UPGRADE_KIT_T2` uses the block (`BlockState.use`, pattern at `ControlHubTests` :517). The tier stays T1 and the kit count is unchanged.
  - `makeshiftHealthIsFiftyFromConfig`: `maxHealth()` == 50 and `health()` starts at 50.
  - `destroyedMakeshiftDropsScrap`:
    - `takeDamage(..., 100_000)`;
    - the item entities near the base are exactly 1 iron ingot + 2 oak planks;
    - no `DAMAGED_TURRET_BASE`;
    - the block is gone.
  - `makeshiftKeepsItsContents`:
    - Breaking with drops gives a `makeshift_turret_base` item whose `BlockEntityTag.Inventory` holds the ammo. Reuse a weapon that fits once Task 3 exists; for now ammo is enough, since an empty base takes `#bastion:ammo` until Task 3.
    - `saveWithoutMetadata` → `load` into a fresh BE keeps `kind()` and the inventory.
  - `makeshiftMountsOnWallsAndCeilings`: placing with `FACING` NORTH and DOWN gives a working BE (`kind()==MAKESHIFT`).
  - `jadeLinesHaveNoTierOnMakeshift`: `BlockInfo` lines for a makeshift base contain no "T1" (helper from `CompatTests` :218).
- [ ] **Step 2: Run, expect compile errors** (no `MakeshiftTurretBaseBlock` / `BaseKind`). Log: `build/gt-t1-red.log`.
- [ ] **Step 3: Implement** the interfaces above. Update every `large()` branch listed in the exploration notes where the kind matters:
  - hitbox size: MAKESHIFT = standard;
  - FeedHub counts it like a standard base;
  - render AABB;
  - JEI `WeaponCategory` base item and HP by `kind()`;
  - `TurretBaseModel.name()` (makeshift → `makeshift_turret_base` once Task 2 adds it; until then standard).
- [ ] **Step 4: Build and run all GameTests.** Logs: `build/build-t1.log`, `build/gt-t1.log`. Expected: all pass (248 + new).

---

### Task 2: Makeshift base look, screen, JEI and the showcase scene (client and assets)

**Files:**
- Create:
  - `tools/blockout/design_makeshift_base.py`
  - `src/main/resources/assets/bastion/geo/makeshift_turret_base.geo.json` (generated)
  - `animations/makeshift_turret_base.animation.json` (minimal `idle`)
  - `textures/block/makeshift_turret_base.png` and a transparent `_e.png`
  - crack overlays `textures/block/makeshift_cracks_{0,1,2}.png`
  - `models/block/makeshift_turret_base.json` (`loader bastion:turret_base`), `blockstates/makeshift_turret_base.json`, `models/item/makeshift_turret_base.json` (`tools/fit_item_display.py`, SIZE_FACTOR 0.68)
  - `src/dev/java/dev/bastion/dev/EarlyShowcase.java`
- Modify:
  - `tools/gen_textures.py`: rustic materials and the `TEXTURES` entries
  - `client/render/TurretBaseModel` (name by kind)
  - `TurretBaseRenderer`: for MAKESHIFT, a crack overlay by health and no health lights
  - `ClientTurret`: damaged MAKESHIFT gives wood splinters instead of sparks
  - `TurretScreen`: MAKESHIFT hides module/connector frames, the Modules label, the connector tooltip and the tier text
  - `BastionJeiPlugin`: info page `jei.bastion.info.makeshift_turret_base`
  - `Showcase.ON` exclusion chain: add `early`
  - lang

**Interfaces:**
- Consumes `BaseKind`, `TurretBaseBlockEntity.kind()` and `TurretMenu.kind()` (Task 1).
- Produces:
  - Rustic materials in `gen_textures.MATERIAL_COLOURS` and `face_pixels`, used by Task 5: `wood` (planks with grain), `wood_dark`, `stone` (speckled grey), `iron_band` (dark iron with a soft highlight), `rivet`, `rope`, `leather`, `spring`.
  - `EarlyShowcase`: a scene behind `-Pshowcase=early` that later tasks extend:
    - a makeshift base on the floor, a wall and the ceiling, at ORIGIN like `WeaponShowcase`;
    - day and night screenshots `showcase_early_*`.

Look (spec "Tampilan"): a plank deck on a stone plinth, iron bands with rivets and rope wraps. There is no glow and no ring. The geo has `weapon_mount` at y 16 like `design_base.py`.

Damage shows as crack overlays at health ≤ 0.75 / 0.5 / 0.25:
- custom textures, drawn by the BER as a slightly inflated lit cutout box over the base;
- `DEBRIS` particles tinted wood-brown, instead of `DAMAGED_SPARK`.

- [ ] **Step 1:** Build the geometry and paint with the generators, then generate the item display (`fit_item_display.py`).
- [ ] **Step 2: Client changes** listed above, and lang:
  - `block.bastion.makeshift_turret_base` = "Makeshift Turret Base" / "Makeshift Turret Base";
  - `tooltip.bastion.makeshift_turret_base`;
  - `jei.bastion.info.makeshift_turret_base`.
- [ ] **Step 3: Build, then one showcase run.** Read the day and night screenshots and check:
  - the base reads as wood, stone and iron;
  - no glow;
  - the cracks show on a base at 30 % HP (the scene sets health).
- [ ] **Step 4: Run all GameTests** (`build/gt-t2.log`). Expected: all pass.

---

### Task 3: Early ammo, Cobblestone Turret, Snowball Repeater, Potato Shotgun (server), recipes

**Files:**
- Create:
  - tags `data/bastion/tags/items/ammo/{pebble,snow,potato,arrow}.json`, and `ammo_early.json` listing the four (**not** in `ammo.json`)
  - weapon JSONs `turret_weapons/{cobblestone,snowball,potato}.json`
  - `weapon/types/SlingWeapon.java`
- Modify:
  - `BastionWeaponTypes`: register `cobblestone`, `snowball` and `potato` → `SlingWeapon::new`
  - `BastionItems`: 3 modules with `BaseKind.MAKESHIFT`
  - `TurretInventory.ammoTag()`: an empty makeshift base accepts `#bastion:ammo_early`
  - `TurretStatsConfig` `WEAPONS` / `NAMES` (append in this order: cobblestone, snowball, potato, arrow_launcher; arrow_launcher is added in Task 4)
  - `tools/gen_recipes.py`: an early-recipes dict, and the key writer learns `#tag`
  - `TurretWorkstationTests.craftingTableOnlyMakesWorkstations`: rename to `craftingTableMakesStationsAndEarlyTurrets` and extend its list
  - lang: names, `tooltip.bastion.weapon.<item>.ability`, `tooltip.bastion.weapon.fits_makeshift`
- Test: create `src/main/java/dev/bastion/gametest/EarlyTurretTests.java`.

**Interfaces:**
- **`SlingWeapon extends WeaponType`:**
  - `fireProfile()` PROJECTILE.
  - `aimPoint` = `LeadSolver.lead(..., param("bullet_speed"))`.
  - `onFire` sends `directions(context)` through `Bullets.fire(context, this, 0, dirs, bullet_speed, stats.pierce(), Bullets.validTargets(context, this), (b, hit) -> Hitscan.damage(hit.getEntity(), level, stats.damage()))`.
  - `public static List<Vec3> directions(FireContext context)` gives `pellets` (default 1) cone directions from `context.seed()`, as the Shotgun does.
- **Weapon JSON fields** (beyond the spec table):
  - `aim_tolerance` 4
  - `aim_lock_ticks` 2
  - `min_pitch` -35
  - `max_pitch` 80
  - `pivot_height` and `muzzle_length` like `gun.json`; Task 5 refits them to the models
  - `energy_color` (unused, required) `#8A8A8A`
  - `bullet_color`: pebble `#9A9A9A`, snow `#F2F6FF`, potato `#C8A060`
  - params `bullet_speed`, `lead_accuracy` 0.9, `pellets` (potato 5)
- **Recipes** (`minecraft:crafting_shaped`, generated). I = iron_ingot, C = cobblestone, L = `#minecraft:logs`, P = `#minecraft:planks`, S = string or stick as noted, B = snow_block, O = potato, H = leather, X = crossbow.
  - `makeshift_turret_base`: `CIC` / `LIL` / `CIC`
  - `cobblestone_turret`: `CSC` / `ICI` / `P P` (S = string)
  - `snowball_repeater`: `SBS` / `IPI` / `PIP` (S = stick)
  - `potato_shotgun`: `IOI` / `IHI` / `P P`
  - `arrow_launcher` (written here, its item comes in Task 4, so generate it in Task 4): `PXP` / `IPI` / ` I `

- [ ] **Step 1: Write the failing tests** (`EarlyTurretTests`):
  - `earlyModulesFitOnlyMakeshift`: each of the 3 modules is valid on makeshift WEAPON and invalid on standard and large bases.
  - `emptyMakeshiftTakesOnlyEarlyAmmo`:
    - an empty makeshift base accepts cobblestone, snowball, potato and arrow;
    - it refuses kinetic rounds.
  - `emptyStandardBaseRefusesCobblestone`: an empty standard base refuses cobblestone and potato.
  - `cobblestoneHitsForThree`: a tanky husk (`TurretSystemsTests.tanky` pattern) in range; after the first hit its health dropped by exactly 3.
  - `snowballRepeatsFivePerSecond`:
    - using the `shots()` pattern with 2 snowballs, consecutive shots are 4 ticks apart (±1);
    - 2 snowballs give exactly 8 shots before NO_AMMO;
    - assert on ratios and counts, not on ms.
  - `potatoFiresFivePelletsOfTwo`:
    - `SlingWeapon.directions(ctx)` has 5 entries for potato and 1 for cobblestone and snowball;
    - a husk point-blank in front takes damage in steps of 2.
  - `slingBulletsPassNonTargets`: a villager in line is spared and the husk behind it is hit (copy `TurretCombatTests.bulletsPassNonTargets`).
  - `earlyRecipesUseIronAtTheCraftingTable`: the recipes for the base and the 3 modules exist (`byKey`), are crafting recipes, and each has an iron ingot among its ingredients.
- [ ] **Step 2: Run RED.** Expect compile errors, then behavioural failures once the stubs compile. Log: `build/gt-t3-red.log`.
- [ ] **Step 3: Implement**, run `python3 tools/gen_recipes.py`, and check `git status` shows only the new recipe JSONs.
- [ ] **Step 4: Build and run all GameTests.** Expected: all pass, including `configListsEveryBuiltInWeaponAndKey`, `creativeTabsSortItems` and the renamed recipe test.

---

### Task 4: Arrow Launcher (server): drag-aware arc, TurretArrowEntity, floor-only

**Files:**
- Create:
  - `turret_weapons/arrow_launcher.json`
  - `weapon/types/ArrowLauncherWeapon.java`
  - `projectile/TurretArrowEntity.java`
- Modify:
  - `weapon/Ballistics.java`: drag
  - `BastionEntities`: `turret_arrow`, size 0.5, tracking like arrows
  - `BastionWeaponTypes`, `BastionItems` (`arrow_launcher`, MAKESHIFT)
  - `TurretBaseBlockEntity`: `lastAmmo()`
  - `TurretInventory`: floor rule
  - `TurretBaseBlock`: the `message.bastion.turret.needs_floor` message
  - `TurretStatsConfig`: append `arrow_launcher`; add `PARAMS` for `drag`, `arrows`, `ring_radius`
  - recipe (gen_recipes)
  - lang
- Test: `EarlyTurretTests`.

**Interfaces:**
- **`Ballistics.Launch(Vec3 muzzle, Vec3 velocity, double ticks, double drag)`:**
  - The old 3-arg constructor means drag 1.
  - `at(t, gravity)` follows the vanilla arrow order: move by v, then `v *= drag`, then `v.y -= gravity`. In closed form, `p(n) = p0 + v0·(1−dⁿ)/(1−d) − ŷ·g/(1−d)·(n − (1−dⁿ)/(1−d))`. With d = 1 it reduces to today's parabola, so Mortar results stay identical; its tests pin that.
- **`Ballistics.solve(turret, data, to)`:** when `params.drag < 1`, solves the high arc numerically. Scan the elevation from `max_pitch` down to 45° and bisect on `y(n(θ))` − Δy, where `n(θ)` comes from `dⁿ = 1 − h(1−d)/(v cosθ)`. No solution means null. `OverwatchArc` and `arcClear` need no change: they call `at`.
- **`TurretArrowEntity extends Arrow`:**
  - Constructor `(Level, BlockPos turret, ItemStack ammo, float damage)`.
  - `pickup = DISALLOWED`.
  - `canHitEntity` is true only for living entities the firing turret's filter (or controller) may hit and that pass `type.canTarget`, the same rule as `Bullets.filtered`, and never a `TurretHitboxEntity`. Everything else is passed through.
  - On an entity hit: clear `invulnerableTime`, crit off, base damage = `damage / speed`, so each arrow does about 3.
  - Tipped: `setEffectsFromItem(ammo)`. Spectral ammo: GLOWING for 200 ticks on hit. Creative Ammo or anything else: plain.
  - Not saved (`shouldBeSaved` false).
  - Client renderer: the vanilla `TippableArrowRenderer`, registered in Task 6.
- **`ArrowLauncherWeapon`:**
  - `ballistic()` true.
  - `canTarget` = `MortarWeapon.grounded(e) && outsideMinRange(e, turret)`.
  - `sight` and `aimPoint` follow the Mortar pattern, with the drag solve and a cached `arcClear`.
  - `onFire`: 6 arrows (`arrows`), each aimed at a point on a ring of `ring_radius` around the target's feet, evenly spaced, with an independent solve. If a ring point has no solution it uses the centre solve. Arrow kind comes from `turret.lastAmmo()`. Then `ShotReport.send(context, this, 0, List.of())`.
- **`TurretBaseBlockEntity.lastAmmo() -> ItemStack`:** a one-item copy of the stack `consumeAmmo` last took (empty until then).
- **Floor rule:**
  - A ballistic module (its weapon type's `ballistic()`) fits a MAKESHIFT base only when its `FACING` is UP: `TurretInventory` gets `BooleanSupplier upright`, the client menu gets it in the open data, and `TurretBaseBlock` mounting says `needs_floor`.
  - The rule applies only to MAKESHIFT: the Mortar is large and large bases are floor-only anyway.

- [ ] **Step 1: Write the failing tests:**
  - `dragArcLandsOnItsTarget`: a solve to a spot 30 blocks away has `at(ticks)` within 0.1 of it; a spot 80 blocks away gives null; Mortar `ballisticLaunchLandsOnTheAimPoint` still passes.
  - `oneArrowIsSixArrows`: with 1 arrow item, one volley spawns exactly 6 `TurretArrowEntity` and leaves the ammo slot empty.
  - `arrowsCannotBePickedUp`: every spawned arrow has `pickup == DISALLOWED`.
  - `tippedArrowsCarryTheirEffect`: a slowness tipped arrow makes the husk get SLOWNESS.
  - `arrowsDealAboutThree`: a `TurretArrowEntity` launched at a tanky husk at speed 1.5 and again at 3.0 takes about 3 health each (±0.5).
  - `arrowsPassNonTargets`: a cow between the launcher and the husk is never hurt.
  - `arrowLauncherShootsOverAWall`: copy `TurretMortarTests.mortarShellsOverWall`, but on a makeshift base on the floor; the husk behind the wall is hurt.
  - `arrowLauncherIgnoresFlyersAndCloseTargets`: copy `mortarIgnoresFlyersAndCloseTargets`; the state stays IDLE and no ammo is used.
  - `arrowLauncherNeedsAFloorBase`:
    - makeshift with FACING NORTH: `isItemValid(WEAPON, arrow_launcher)` is false;
    - FACING UP: true;
    - a hopper cannot insert it either.
  - `creativeAmmoFeedsEarlyTurrets`: with Creative Ammo, the Arrow Launcher spawns plain arrows (no effects) and the Cobblestone Turret fires.
- [ ] **Step 2: Run RED** (`build/gt-t4-red.log`).
- [ ] **Step 3: Implement**, then run `gen_recipes.py` for `arrow_launcher`.
- [ ] **Step 4: Build and run all GameTests.** Expected: all pass, including the Mortar and Overwatch arc tests.

---

### Task 5: Weapon models, rustic paints, animations (assets)

**Files:**
- Modify:
  - `tools/blockout/design_weapons.py`: functions `cobblestone_turret`, `snowball_repeater`, `potato_shotgun`, `arrow_launcher`, plus their entries in the call list
  - `tools/gen_textures.py`: `MODEL_MATERIALS` for the 4 models, and the `TEXTURES` entries with a transparent `_e`
  - `tools/fit_item_display.py`: `MODELS`
  - weapon JSONs `pivot_height` / `muzzle_length`, refitted to the models
- Create:
  - `geo/<item>.geo.json` and `animations/<item>.animation.json` for the 4 items
  - `textures/item/<item>.png` and `_e.png`
  - `models/item/<item>.json`
- Extend `EarlyShowcase`: the 4 turrets on makeshift bases firing at husks, day and night.

Look (spec "Tampilan"):
- **Cobblestone:** a short, wide spring cannon on a wooden pivot.
- **Snowball:** a 4-barrel drum turned by a crank, with a snow hopper on top.
- **Potato:** a fat iron pipe, a pressure tank and a hand pump.
- **Arrow:** a ballista rack of 6 arrows (2×3) facing up, with a bow and string.

Requirements:
- Bones follow PLAN §6.2: `root` > `yaw_pivot` > `pitch_pivot` > `body`, plus `barrel_N` and `muzzle_N`. Use `yoke(k)` like the others. The Potato has one muzzle (pellets share it); the Arrow Launcher has `muzzle_0..5` at the 6 arrow tips.
- Size is about 1 block, like other small weapons.
- Clips: `deploy`, `idle`, `held_idle`, `fire`; `fire` lasts ≤ 0.5 s and can restart.
  - Cobblestone `fire`: the spring compresses and the barrel kicks back.
  - Snowball `fire`: the drum turns 90°. With its 4-fold symmetry, restarting each shot reads as continuous turning.
  - Potato `fire`: the pump goes down and up.
  - Arrow `fire`: the string snaps, the rack empties, then refills.

- [ ] **Step 1:** Generate the geos and paints. Run `git checkout` on the missile_launcher geo, and check `git diff --stat` on `geo/` (new files only).
- [ ] **Step 2:** Write the animations and refit the item displays.
- [ ] **Step 3: Build, then one showcase run.** Check the screenshots:
  - each turret reads as its spec description, rustic, with no glow;
  - the turrets turn and fire;
  - muzzles sit at the barrel tips.
- [ ] **Step 4: Run all GameTests** (the JSON aim changes must keep Task 3/4 tests green).

---

### Task 6: Client VFX, arrow renderer, sounds (client and assets)

**Files:**
- Modify:
  - `client/vfx/WeaponFx.java`: entries for `cobblestone`, `snowball`, `potato`, `arrow_launcher`
  - `client/TurretFireEffects.java`: dispatch to a new `client/EarlyEffects.java`. Heat haze, casings, the shotgun pump and shell, and light are all off for early weapons.
  - `client/render/TracerRenderer.java`, or a new lit `client/render/PelletRenderer.java` drawn from `VfxManager`: lit sprites (rock, snow clump, potato chunk) that spin and tumble, with the light taken from the level (`LevelRenderer.getLightColor`) as `CasingRenderer` does.
  - `client/ClientTurret.java`: no idle hum for early weapons.
  - `BastionClient`: register `TippableArrowRenderer` for `turret_arrow`.
  - `client/RocketEffects`-style tick loop for the arrows' pale wind streak (custom, not glowing).
  - `tools/gen_textures.py` `VFX_SPRITES`: `vfx/pebble.png`, `vfx/snow_clump.png`, `vfx/potato_chunk.png`, and particle sprites for stone chips, snow flakes, potato bits and wind streak. Particle JSONs as needed.
  - `BastionParticles` plus their behaviours. Prefer reusing non-glow types: `DEBRIS` tinted by colour, `SMOKE_PUFF`, `DUST_RING`.
  - Sounds: `BastionSounds`, `sounds.json`, `tools/gen_sounds.py` (placeholders), `docs/SOUND_SHEET.md` entries. Events:
    - `cobblestone.fire`, `snowball.fire`, `potato.fire`, `arrow_launcher.fire`
    - `makeshift_base.place` (creak), `makeshift_base.destroyed` (wood crunch)
  - The makeshift block gets a custom `SoundType` (place = creak, break = wood).
  - `ClientPacketHandler` DESTROYED: scale < 0.9 → `makeshift_base.destroyed`.
  - Subtitles in lang.
- Extend `EarlyShowcase`: projectiles in flight and impacts, day and night.

Effects (spec "Tampilan"):
- **Cobblestone:** a rough grey rock spinning, with thin dust. Impact: stone chips and dust.
- **Snowball:** a soft white clump with a faint frost trail. Impact: a snow burst and flakes.
- **Potato:** a tumbling potato chunk. Impact: a splat and small bits.
- **Arrow:** the vanilla arrow, plus a pale wind streak following it. It sticks like vanilla, with a little dust.
- **Muzzle:** a puff of air or dust. Snowball: cold vapour. Arrow: a string-snap streak. No fire, no flash glow.
- There is no sound when a projectile hits (user decision).

- [ ] **Step 1:** Generate the sprites and sounds, then implement the client code.
- [ ] **Step 2: Build, then one showcase run.** Check frames:
  - the projectiles are visible in flight by day and by night (lit, not glowing; dark at night is expected);
  - the impacts read;
  - the arrows show the streak.
- [ ] **Step 3: Run all GameTests.**

---

### Task 7: Docs, store text and changelog for 0.4.0

**Files:**
- `docs/TESTING.md`: a new section `## Early game turrets (0.4.0)` with in-game checks:
  - recipes, the base on floor, wall and ceiling;
  - Arrow Launcher floor only;
  - each turret's fire rate, ammo use, look and sounds;
  - cracks;
  - the scrap drop;
  - automation;
  - JEI and Jade.
  - Fix the two outdated bullets: "The crafting table only makes the 5 stations…" and the creative tabs one.
- `docs/release/UPLOAD.md`:
  - `# Upload sheet: 0.4.0` and the version fields;
  - version history line: 0.4.0 = early game turrets, prepared and not uploaded (the user uploads about a week after 0.3.2);
  - `## Changelog: 0.4.0` (New / Changes / Note).
- `gradle.properties`: `mod_version=0.4.0`.
- `docs/release/DESCRIPTION_UPDATE_0.4.0.md` with the Modrinth and CurseForge parts:
  - the weapons table gets 4 rows with Base "Makeshift";
  - "Turret bases" gets a makeshift paragraph;
  - the ammo text ("ten kinds") is updated;
  - the workstations text no longer says "the crafting table only makes the stations";
  - the "not a single vanilla particle" claim gets the Arrow Launcher's vanilla arrow noted.
  - Apply the same edits to `DESCRIPTION.md` and `DESCRIPTION.curseforge.html`.
- `docs/HANDOFF.md` §0: a new top paragraph (state, decisions, my calls, how to test).
- `docs/PLAN.md` early-game Status: built, not tested by the user in game.

- [ ] **Step 1:** Write the docs.
- [ ] **Step 2:** `./gradlew build --offline` produces `bastion-1.20.1-0.4.0.jar`. Run all GameTests, with and without `-PwithCompatMods`.
