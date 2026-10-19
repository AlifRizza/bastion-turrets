# Turret Stats Config Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A second gameplay config file, `config/bastion-turrets.toml`, where every built-in turret weapon's stats and weapon-specific params can be set without a datapack; each value is `"default"` (the JSON's) or a number that replaces it, applied live when the file is saved.

**Architecture:** `TurretStatsConfig` builds a Forge `COMMON` config spec from the mod's own weapon JSON in the jar (keys, "Mod default" comments, ranges). `WeaponDataLoader` keeps the loaded JSON as *raw* data and serves *effective* data = raw with each non-default config number laid over it (`WeaponData.withOverrides`, `WeaponDataLoader.merge`); every consumer already reads the loader, and clients keep receiving the effective map through the existing `WeaponDataSync`. When Forge sees the file change (`ModConfigEvent.Reloading`), the server thread rebuilds the effective map and resyncs every player.

**Tech Stack:** Forge 1.20.1 (47.4.26, parchment 2023.09.03), Java 17, `ForgeConfigSpec` (night-config TOML), Mojang codecs/DFU, Forge GameTests.

**Spec:** `docs/PLAN.md` → section 8, "Config stat turret (permintaan user 2026-10-17 12:35, desain disetujui 12:50–12:51)", spec approved by the user 2026-10-17 12:53.

## Global Constraints

- File `bastion-turrets.toml`, `ModConfig.Type.COMMON` (global, in `config/`), registered next to the existing `bastion-common.toml` and `bastion-client.toml`.
- Scope: the 16 built-in weapons only (`data/bastion/turret_weapons/*.json`), in this order: `gun, machine_gun, shotgun, sniper, rocket_launcher, flamethrower, laser_rifle, repulsor, ion_rifle, plasma_repeater, missile_launcher, tesla, railgun, mortar, repulsor_dome, plasma_cannon`. Modules stay datapack-only.
- Each weapon section = the **15 common stats** `range, fire_interval, damage, spread, ammo_per_shot, turn_speed, aim_tolerance, aim_lock_ticks, min_pitch, max_pitch, sweep_tolerance, heat_per_shot, heat_dissipation_per_second, max_heat, overheat_ticks` + **every `params` key of that weapon's JSON**, flat in the section (no `params` sub-table). Never in the file: `type`, `ammo`, `energy_color`, `pivot_height`, `muzzle_length`.
- A value is the string `"default"` or a number inside the key's range. Per-key comment lines, English: one-line description with its unit, `Mod default: <built-in JSON value>`, `Range: <min> ~ <max>`. Invalid values are reset to `"default"` by Forge's validator (it logs a warning).
- `"default"` = the value after datapacks. Config numbers win over datapacks; untouched keys follow the mod/datapack (so future rebalances still reach players).
- Merge rules: min_pitch above max_pitch after overrides → both pitch overrides dropped for that weapon + log warning; a weapon missing from the loaded data → its overrides unused; ids outside the `bastion` namespace → untouched; `aim_lock_ticks` and `overheat_ticks` are rounded to int.
- Live reload: on `ModConfigEvent.Reloading` for this spec, on the server thread: rebuild effective data, send `WeaponDataSync` to all players. No server running (a remote client's own file) → nothing.
- Never commit until the user says "commit"; never commit `modlist.html` or `sfx/`; never touch `run/saves/showcase`; Gradle always `--offline`; keep the full test log in a file, never pipe a run whose exit code matters into grep.

## Review Focus

1. **A turret holding more FE than a lowered `energy_capacity`**: no overfill, no crash; it shows and spends at most the new capacity and takes no more FE. Test: `loweringEnergyCapacityNeverOverfillsATurret` (Task 3).
2. **TOML integers vs decimals** (`damage = 30` reads as an Integer/Long, `damage = 30.5` as a Double): both must apply. Test: the Integer step in `configOverrideReachesTheWeapon` (Task 3).
3. **A datapack that changed a weapon**: with the config at `"default"` the datapack's value stays; with a number, the config's wins. Test: the datapack step in `mergeTouchesOnlyBastionWeaponsItHas` (Task 1).
4. **Weapons from other mods' datapacks, and a built-in weapon a datapack removed**: other namespaces untouched, overrides for a missing weapon unused, nothing thrown. Test: `mergeTouchesOnlyBastionWeaponsItHas` (Task 1).
5. **A fraction for an integer stat** (`aim_lock_ticks = 2.6`): rounds (to 3), never truncates silently to 2 or fails. Test: the rounding assert in `overridesReplaceOnlyTheirStats` (Task 1).

---

## File map

| File | Change | Responsibility |
|---|---|---|
| `src/main/java/dev/bastion/weapon/WeaponData.java` | modify | `withOverrides(Map<String, Double>)`: one weapon's merge |
| `src/main/java/dev/bastion/weapon/WeaponDataLoader.java` | modify | raw + effective maps, `merge(...)`, `rebuild()` |
| `src/main/java/dev/bastion/config/TurretStatsConfig.java` | create | the spec (keys, help, ranges, "Mod default"), `overrides(weapon)`, reload handler |
| `src/main/java/dev/bastion/network/WeaponDataSync.java` | modify | `current()` factory |
| `src/main/java/dev/bastion/Bastion.java` | modify | register the file, the reload listener, use `WeaponDataSync.current()` |
| `src/main/java/dev/bastion/gametest/TurretStatsConfigTests.java` | create | all GameTests of this plan |
| `docs/PLAN.md`, `docs/TESTING.md`, `docs/HANDOFF.md`, `docs/release/DESCRIPTION.md`, `docs/release/DESCRIPTION.curseforge.html`, `docs/release/UPLOAD.md` | modify | docs |

Commands: build `./gradlew build --offline > build/build.log 2>&1` (exit code 0 = pass; read the tail). All GameTests `./gradlew runGameTestServer --offline > build/gametest.log 2>&1`, then read the log: it ends with `All N required tests passed :)` or lists failures (`... failed!` lines with the assertion message). GameTests run as one suite; there is no per-test filter. Current count: **157**.

---

### Task 1: Overrides laid over weapon data

**Files:**
- Modify: `src/main/java/dev/bastion/weapon/WeaponData.java`
- Modify: `src/main/java/dev/bastion/weapon/WeaponDataLoader.java`
- Create: `src/main/java/dev/bastion/gametest/TurretStatsConfigTests.java`

**Interfaces:**
- Consumes: `WeaponDataLoader.get(ResourceLocation)` (existing; until Task 3 it returns the JSON as loaded).
- Produces: `public WeaponData withOverrides(Map<String, Double> overrides)` on `WeaponData`; `public static Map<ResourceLocation, WeaponData> merge(Map<ResourceLocation, WeaponData> raw, Function<String, Map<String, Double>> overrides)` on `WeaponDataLoader`; the test class `TurretStatsConfigTests` (template `"arena"`, one batch per test).

- [ ] **Step 1: Write the failing tests**

Create `src/main/java/dev/bastion/gametest/TurretStatsConfigTests.java`:

```java
package dev.bastion.gametest;

import dev.bastion.Bastion;
import dev.bastion.weapon.WeaponData;
import dev.bastion.weapon.WeaponDataLoader;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Map;

/** bastion-turrets.toml (PLAN 8 "Config stat turret"): overrides, the file's keys, live reload. */
@GameTestHolder(Bastion.MOD_ID)
@PrefixGameTestTemplate(false)
public class TurretStatsConfigTests {
    private static final String ARENA = "arena";

    private static WeaponData weapon(String id) {
        WeaponData data = WeaponDataLoader.get(Bastion.id(id));
        if (data == null) throw new IllegalStateException("no weapon data for " + id);
        return data;
    }

    /** Each key replaces its own stat or param; identity, colour, geometry and the rest stay; ints round. */
    @GameTest(template = ARENA, batch = "overridesReplaceOnlyTheirStats")
    public static void overridesReplaceOnlyTheirStats(GameTestHelper helper) {
        WeaponData sniper = weapon("sniper");
        helper.assertTrue(sniper.withOverrides(Map.of()) == sniper, "no overrides must return the data unchanged");
        WeaponData changed = sniper.withOverrides(Map.of("damage", 77.0, "charge_ticks", 9.0, "aim_lock_ticks", 2.6, "max_heat", 50.0));
        helper.assertTrue(changed.damage() == 77f, "damage " + changed.damage());
        helper.assertTrue(changed.param("charge_ticks") == 9f, "charge_ticks " + changed.param("charge_ticks"));
        helper.assertTrue(changed.aim().lockTicks() == 3, "aim_lock_ticks 2.6 must round to 3, got " + changed.aim().lockTicks());
        helper.assertTrue(changed.heat().max() == 50f, "max_heat " + changed.heat().max());
        helper.assertTrue(changed.range() == sniper.range() && changed.fireInterval() == sniper.fireInterval(), "range or fire_interval changed");
        helper.assertTrue(changed.param("bullet_speed") == sniper.param("bullet_speed"), "another param changed");
        helper.assertTrue(changed.type().equals(sniper.type()) && changed.ammo().equals(sniper.ammo()) && changed.energyColor() == sniper.energyColor(),
                "identity or colour changed");
        helper.assertTrue(changed.aim().pivotHeight() == sniper.aim().pivotHeight() && changed.aim().muzzleLength() == sniper.aim().muzzleLength(),
                "geometry changed");
        helper.succeed();
    }

    /** A key for a param this weapon doesn't have adds nothing. */
    @GameTest(template = ARENA, batch = "overrideOfAParamTheWeaponLacksIsIgnored")
    public static void overrideOfAParamTheWeaponLacksIsIgnored(GameTestHelper helper) {
        WeaponData sniper = weapon("sniper");
        WeaponData changed = sniper.withOverrides(Map.of("blast_radius", 5.0));
        helper.assertTrue(changed.params().equals(sniper.params()), "params became " + changed.params());
        helper.succeed();
    }

    /** min_pitch above max_pitch after overrides: both pitch overrides dropped; a sane one applies. */
    @GameTest(template = ARENA, batch = "crossedPitchOverridesAreDropped")
    public static void crossedPitchOverridesAreDropped(GameTestHelper helper) {
        WeaponData sniper = weapon("sniper");
        WeaponData crossed = sniper.withOverrides(Map.of("min_pitch", 50.0, "max_pitch", 10.0));
        helper.assertTrue(crossed.aim().minPitch() == sniper.aim().minPitch() && crossed.aim().maxPitch() == sniper.aim().maxPitch(),
                "crossed pitches applied: " + crossed.aim().minPitch() + " / " + crossed.aim().maxPitch());
        WeaponData lower = sniper.withOverrides(Map.of("min_pitch", -20.0));
        helper.assertTrue(lower.aim().minPitch() == -20f, "min_pitch -20 not applied: " + lower.aim().minPitch());
        helper.succeed();
    }

    /**
     * Only Bastion weapons that are loaded get overrides: another mod's weapon passes untouched, a built-in weapon a datapack
     * removed stays absent, and a datapack's changed value survives "default" but loses to a number.
     */
    @GameTest(template = ARENA, batch = "mergeTouchesOnlyBastionWeaponsItHas")
    public static void mergeTouchesOnlyBastionWeaponsItHas(GameTestHelper helper) {
        WeaponData sniper = weapon("sniper");
        WeaponData datapackSniper = sniper.withOverrides(Map.of("damage", 50.0)); // as if a datapack set damage 50
        ResourceLocation other = new ResourceLocation("othermod", "sniper");
        Map<ResourceLocation, WeaponData> raw = Map.of(Bastion.id("sniper"), datapackSniper, other, sniper);
        Map<ResourceLocation, WeaponData> untouched = WeaponDataLoader.merge(raw, weapon -> Map.of());
        helper.assertTrue(untouched.get(Bastion.id("sniper")).damage() == 50f, "datapack value lost under \"default\"");
        Map<ResourceLocation, WeaponData> merged = WeaponDataLoader.merge(raw, weapon -> Map.of("damage", 77.0));
        helper.assertTrue(merged.get(Bastion.id("sniper")).damage() == 77f, "config number must beat the datapack");
        helper.assertTrue(merged.get(other).damage() == sniper.damage(), "another mod's weapon was changed");
        helper.assertTrue(!merged.containsKey(Bastion.id("gun")) && merged.size() == 2, "merge invented weapons: " + merged.keySet());
        helper.succeed();
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew runGameTestServer --offline > build/gametest.log 2>&1`
Expected: the build fails to compile, `build/gametest.log` shows `cannot find symbol` for `withOverrides` and `merge`.

- [ ] **Step 3: Add `withOverrides` to `WeaponData`**

In `src/main/java/dev/bastion/weapon/WeaponData.java` add the imports `dev.bastion.Bastion` and `java.util.HashMap`, then add after `param(String key)`:

```java
    /**
     * This data with {@code overrides} (bastion-turrets.toml, PLAN 8 "Config stat turret") laid over it: each key replaces the
     * stat or param of that name; a param this weapon lacks is ignored, and type, ammo, colour and geometry are never touched.
     * aim_lock_ticks and overheat_ticks round to whole ticks. Overrides that would put min_pitch above max_pitch are both
     * dropped (logged), so a typo can't leave a turret that never aims.
     */
    public WeaponData withOverrides(Map<String, Double> overrides) {
        if (overrides.isEmpty()) return this;
        float minPitch = number(overrides, "min_pitch", aim.minPitch()), maxPitch = number(overrides, "max_pitch", aim.maxPitch());
        if (minPitch > maxPitch) {
            Bastion.LOGGER.warn("bastion-turrets.toml: min_pitch {} is above max_pitch {} for {}; both ignored", minPitch, maxPitch, type);
            minPitch = aim.minPitch();
            maxPitch = aim.maxPitch();
        }
        Aim newAim = new Aim(number(overrides, "turn_speed", aim.turnSpeed()), number(overrides, "aim_tolerance", aim.tolerance()),
                ticks(overrides, "aim_lock_ticks", aim.lockTicks()), minPitch, maxPitch, aim.pivotHeight(), aim.muzzleLength(),
                number(overrides, "sweep_tolerance", aim.sweepTolerance()));
        Heat newHeat = new Heat(number(overrides, "heat_per_shot", heat.perShot()),
                number(overrides, "heat_dissipation_per_second", heat.dissipationPerSecond()), number(overrides, "max_heat", heat.max()),
                ticks(overrides, "overheat_ticks", heat.overheatTicks()));
        Map<String, Float> newParams = new HashMap<>(params);
        params.forEach((key, value) -> newParams.put(key, number(overrides, key, value)));
        return new WeaponData(type, ammo, energyColor, number(overrides, "range", range), number(overrides, "fire_interval", fireInterval),
                number(overrides, "damage", damage), number(overrides, "spread", spread), number(overrides, "ammo_per_shot", ammoPerShot),
                newAim, newHeat, Map.copyOf(newParams));
    }

    private static float number(Map<String, Double> overrides, String key, float fallback) {
        Double value = overrides.get(key);
        return value == null ? fallback : value.floatValue();
    }

    private static int ticks(Map<String, Double> overrides, String key, int fallback) {
        Double value = overrides.get(key);
        return value == null ? fallback : (int) Math.round(value);
    }
```

- [ ] **Step 4: Add `merge` to `WeaponDataLoader`**

In `src/main/java/dev/bastion/weapon/WeaponDataLoader.java` add the import `java.util.function.Function` and this method after `accept(...)`:

```java
    /**
     * {@code raw} with each Bastion weapon's overrides (looked up by the file name, "sniper") laid over it. Other namespaces pass
     * through untouched; overrides for a weapon that isn't in {@code raw} are simply never used.
     */
    public static Map<ResourceLocation, WeaponData> merge(Map<ResourceLocation, WeaponData> raw,
                                                          Function<String, Map<String, Double>> overrides) {
        Map<ResourceLocation, WeaponData> merged = new HashMap<>();
        raw.forEach((id, data) -> merged.put(id, Bastion.MOD_ID.equals(id.getNamespace()) ? data.withOverrides(overrides.apply(id.getPath())) : data));
        return Map.copyOf(merged);
    }
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew runGameTestServer --offline > build/gametest.log 2>&1`
Expected: exit code 0, the log ends with `All 161 required tests passed :)`.

---

### Task 2: The config file

**Files:**
- Create: `src/main/java/dev/bastion/config/TurretStatsConfig.java`
- Modify: `src/main/java/dev/bastion/Bastion.java` (register the file)
- Modify: `src/main/java/dev/bastion/gametest/TurretStatsConfigTests.java`

**Interfaces:**
- Consumes: `WeaponData.CODEC`, `WeaponData.param(String)` (existing).
- Produces: `TurretStatsConfig.FILE` (`"bastion-turrets.toml"`), `DEFAULT` (`"default"`), `record Key(String description, double min, double max)` with `public boolean accepts(Object value)`, `List<String> WEAPONS`, `Map<String, Key> COMMON`, `Map<String, Key> PARAMS`, `ForgeConfigSpec SPEC`, `static WeaponData builtIn(String weapon)`, `static List<String> keys(WeaponData data)`, `static Map<String, Double> overrides(String weapon)` (empty while the file isn't loaded or everything is `"default"`).

- [ ] **Step 1: Write the failing tests**

Add the imports `dev.bastion.config.TurretStatsConfig`, `com.electronwill.nightconfig.core.UnmodifiableConfig`, `net.minecraftforge.common.ForgeConfigSpec`, `java.util.HashSet`, `java.util.List`, `java.util.Set`, `java.util.TreeSet` to `TurretStatsConfigTests` and add:

```java
    /** One section per built-in weapon file, holding exactly the 15 common stats and that weapon's JSON params. */
    @GameTest(template = ARENA, batch = "configListsEveryBuiltInWeaponAndKey")
    public static void configListsEveryBuiltInWeaponAndKey(GameTestHelper helper) {
        Set<String> files = new TreeSet<>();
        helper.getLevel().getServer().getResourceManager()
                .listResources("turret_weapons", id -> id.getPath().endsWith(".json"))
                .keySet().stream().filter(id -> id.getNamespace().equals(Bastion.MOD_ID))
                .forEach(id -> files.add(id.getPath().substring("turret_weapons/".length(), id.getPath().length() - ".json".length())));
        helper.assertTrue(files.equals(new TreeSet<>(TurretStatsConfig.WEAPONS)), "config weapons " + TurretStatsConfig.WEAPONS + " vs files " + files);
        Set<String> allParams = new HashSet<>();
        for (String weapon : TurretStatsConfig.WEAPONS) {
            WeaponData data = TurretStatsConfig.builtIn(weapon);
            Set<String> expected = new HashSet<>(TurretStatsConfig.COMMON.keySet());
            expected.addAll(data.params().keySet());
            allParams.addAll(data.params().keySet());
            UnmodifiableConfig section = TurretStatsConfig.SPEC.getValues().get(weapon);
            helper.assertTrue(section != null && section.valueMap().keySet().equals(expected),
                    weapon + " keys " + (section == null ? null : section.valueMap().keySet()) + " vs " + expected);
        }
        helper.assertTrue(TurretStatsConfig.PARAMS.keySet().equals(allParams), "param table has stale or missing keys: "
                + TurretStatsConfig.PARAMS.keySet() + " vs " + allParams);
        helper.succeed();
    }

    /** "default" and numbers inside the key's range pass the file's validator; everything else is reset by Forge. */
    @GameTest(template = ARENA, batch = "configValidatorAcceptsDefaultAndNumbersInRange")
    public static void configValidatorAcceptsDefaultAndNumbersInRange(GameTestHelper helper) {
        helper.assertTrue(spec("sniper", "damage").test("default"), "\"default\" rejected");
        helper.assertTrue(spec("sniper", "damage").test(30), "integer 30 rejected");
        helper.assertTrue(spec("sniper", "damage").test(30.5), "30.5 rejected");
        helper.assertTrue(!spec("sniper", "damage").test(-1), "negative damage accepted");
        helper.assertTrue(!spec("sniper", "damage").test(20000), "damage 20000 accepted");
        helper.assertTrue(!spec("sniper", "damage").test("abc"), "text accepted");
        helper.assertTrue(!spec("sniper", "fire_interval").test(0.4) && spec("sniper", "fire_interval").test(0.5), "fire_interval floor is not 0.5");
        helper.assertTrue(spec("sniper", "min_pitch").test(-90) && !spec("sniper", "min_pitch").test(-91), "pitch range is not -90 ~ 90");
        helper.assertTrue(spec("railgun", "energy_per_shot").test(8000) && !spec("railgun", "lead_accuracy").test(1.5), "param ranges not wired");
        helper.succeed();
    }

    /** Every key says what it does, the built-in value and its range. */
    @GameTest(template = ARENA, batch = "configCommentsShowTheModDefault")
    public static void configCommentsShowTheModDefault(GameTestHelper helper) {
        String damage = spec("sniper", "damage").getComment();
        helper.assertTrue(damage.contains("Mod default: 24") && damage.contains("Range: 0 ~ 10000"), "sniper damage comment: " + damage);
        String interval = spec("machine_gun", "fire_interval").getComment();
        helper.assertTrue(interval.contains("Mod default: 1.6667"), "machine gun fire_interval comment: " + interval);
        String charge = spec("railgun", "charge_ticks").getComment();
        helper.assertTrue(charge.contains("Mod default: 60") && charge.toLowerCase().contains("tick"), "railgun charge_ticks comment: " + charge);
        helper.succeed();
    }

    private static ForgeConfigSpec.ValueSpec spec(String weapon, String key) {
        return TurretStatsConfig.SPEC.getSpec().get(List.of(weapon, key));
    }
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew runGameTestServer --offline > build/gametest.log 2>&1`
Expected: compile failure, `cannot find symbol` for `TurretStatsConfig`.

- [ ] **Step 3: Create `TurretStatsConfig`**

Create `src/main/java/dev/bastion/config/TurretStatsConfig.java`:

```java
package dev.bastion.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.bastion.Bastion;
import dev.bastion.weapon.WeaponData;
import net.minecraftforge.common.ForgeConfigSpec;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * config/bastion-turrets.toml (PLAN 8 "Config stat turret", user 2026-10-17): every built-in turret weapon's stats and
 * params, each "default" (the JSON's value, or a datapack's) or a number that replaces it. Built from the mod's own JSON in
 * the jar, so the keys, the "Mod default" comments and the defaults of stats a JSON leaves out always match it.
 * WeaponDataLoader lays the numbers over the loaded JSON (overrides).
 */
public final class TurretStatsConfig {
    public static final String FILE = "bastion-turrets.toml";
    public static final String DEFAULT = "default";

    /** A key's one-line help (with its unit) and the numbers it accepts. */
    public record Key(String description, double min, double max) {
        public boolean accepts(Object value) {
            return DEFAULT.equals(value) || value instanceof Number n && n.doubleValue() >= min && n.doubleValue() <= max;
        }
    }

    /** The built-in weapons (data/bastion/turret_weapons/<id>.json), in the order of the store description's weapon table. */
    public static final List<String> WEAPONS = List.of("gun", "machine_gun", "shotgun", "sniper", "rocket_launcher",
            "flamethrower", "laser_rifle", "repulsor", "ion_rifle", "plasma_repeater", "missile_launcher", "tesla", "railgun",
            "mortar", "repulsor_dome", "plasma_cannon");

    private static final Map<String, String> NAMES = Map.ofEntries(
            Map.entry("gun", "Gun Turret"), Map.entry("machine_gun", "Machine Gun Turret"), Map.entry("shotgun", "Shotgun Turret"),
            Map.entry("sniper", "Sniper Turret"), Map.entry("rocket_launcher", "Rocket Launcher Turret"),
            Map.entry("flamethrower", "Flamethrower Turret"), Map.entry("laser_rifle", "Laser Rifle Turret"),
            Map.entry("repulsor", "Repulsor"), Map.entry("ion_rifle", "Ion Rifle Turret"),
            Map.entry("plasma_repeater", "Plasma Repeater Turret"), Map.entry("missile_launcher", "Missile Launcher Turret"),
            Map.entry("tesla", "Tesla Coil Turret"), Map.entry("railgun", "Railgun Turret"), Map.entry("mortar", "Mortar Turret"),
            Map.entry("repulsor_dome", "Repulsor Dome"), Map.entry("plasma_cannon", "Plasma Cannon Turret"));

    private static final String[] HEADER = {
            "Bastion Turrets: weapon stats. Each value is \"default\" or a number.",
            "  \"default\" = the mod's value, or a datapack's if one changes this weapon (data/bastion/turret_weapons).",
            "  A number replaces it. A value that is not a number or is out of range is reset to \"default\" (see the log).",
            "Times are in ticks (20 ticks = 1 second), distances in blocks, angles in degrees.",
            "A stat a weapon does not use (for example spread on the Tesla Coil) has no effect.",
            "Saving this file while the game runs applies it at once. On a server, the server's copy of this file counts."};

    /** The stats every weapon has, in file order. */
    public static final Map<String, Key> COMMON = ordered(
            "range", new Key("Targeting range in blocks.", 0.5, 256),
            "fire_interval", new Key("Ticks between shots (lower = faster).", 0.5, 12000),
            "damage", new Key("Damage per hit.", 0, 10000),
            "spread", new Key("Inaccuracy in degrees.", 0, 90),
            "ammo_per_shot", new Key("Ammo items used per shot (0.5 = one item every two shots).", 0, 64),
            "turn_speed", new Key("Degrees the weapon turns per tick.", 0.1, 180),
            "aim_tolerance", new Key("Degrees off target it still fires at.", 0, 180),
            "aim_lock_ticks", new Key("Ticks it must stay on target before its first shot.", 0, 200),
            "min_pitch", new Key("Lowest barrel angle in degrees (negative = down).", -90, 90),
            "max_pitch", new Key("Highest barrel angle in degrees.", -90, 90),
            "sweep_tolerance", new Key("Degrees off aim it keeps firing while a burst swings to the next target (0 = aim_tolerance).", 0, 180),
            "heat_per_shot", new Key("Heat added per shot (0 = never overheats).", 0, 1000),
            "heat_dissipation_per_second", new Key("Heat lost per second.", 0, 100000),
            "max_heat", new Key("Heat at which it overheats.", 1, 100000),
            "overheat_ticks", new Key("Ticks it stays locked after overheating.", 0, 12000));

    /** Every weapon-specific param a built-in weapon JSON uses (a GameTest checks the list against the JSON). */
    public static final Map<String, Key> PARAMS = Map.ofEntries(
            Map.entry("acceleration", new Key("Missile: speed gained per tick, in blocks per tick per tick.", 0, 10)),
            Map.entry("base_pierce", new Key("Extra targets each round passes through.", 0, 64)),
            Map.entry("blast_damage", new Key("Blast damage at the centre (half at the edge).", 0, 10000)),
            Map.entry("blast_radius", new Key("Blast radius in blocks.", 0, 64)),
            Map.entry("bolt_speed", new Key("Bolt speed in blocks per tick.", 0.01, 100)),
            Map.entry("bolts", new Key("Lightning bolts per discharge.", 1, 64)),
            Map.entry("bolts_per_target", new Key("Most bolts one target takes per discharge.", 1, 64)),
            Map.entry("boss_multiplier", new Key("Damage multiplier against bosses.", 0, 100)),
            Map.entry("bullet_speed", new Key("Bullet speed in blocks per tick.", 0.01, 100)),
            Map.entry("burn_damage", new Key("Damage per second while burning.", 0, 10000)),
            Map.entry("burn_ticks", new Key("Ticks a target keeps burning.", 0, 12000)),
            Map.entry("charge_ticks", new Key("Ticks it charges before each shot.", 0, 12000)),
            Map.entry("cone_angle", new Key("Half-angle of its cone in degrees.", 0, 180)),
            Map.entry("cruise_speed", new Key("Missile: top speed in blocks per tick.", 0.01, 100)),
            Map.entry("energy_capacity", new Key("FE the turret's capacitor holds.", 0, 10000000)),
            Map.entry("energy_per_shot", new Key("FE per shot.", 0, 10000000)),
            Map.entry("falloff_end", new Key("Distance in blocks where pellet damage bottoms out.", 0, 256)),
            Map.entry("falloff_min", new Key("Share of pellet damage left at falloff_end (0 to 1).", 0, 1)),
            Map.entry("falloff_start", new Key("Distance in blocks where pellet damage starts to drop.", 0, 256)),
            Map.entry("field_damage", new Key("Damage the ion core deals to each other target around it.", 0, 10000)),
            Map.entry("field_radius", new Key("Reach of the ion core in blocks.", 0, 64)),
            Map.entry("field_ticks", new Key("Ticks the ion core's beams stay visible.", 0, 200)),
            Map.entry("fire_ticks", new Key("Ticks a fire patch burns.", 0, 12000)),
            Map.entry("fuel_ticks", new Key("Missile: ticks of flight before its fuel runs out.", 0, 12000)),
            Map.entry("gravity", new Key("Shell gravity in blocks per tick per tick.", 0.001, 1)),
            Map.entry("headshot_multiplier", new Key("Damage multiplier on a headshot.", 0, 100)),
            Map.entry("homing_delay", new Key("Missile: ticks after launch before it starts steering.", 0, 200)),
            Map.entry("hot_spread", new Key("Spread in degrees while hot.", 0, 90)),
            Map.entry("hot_spread_threshold", new Key("Share of max heat (0 to 1) above which hot_spread applies.", 0, 1)),
            Map.entry("knockback", new Key("Knockback strength (Repulsors: about how many blocks a target is thrown).", 0, 100)),
            Map.entry("launch_speed", new Key("Missile: speed out of the tube in blocks per tick.", 0.01, 100)),
            Map.entry("lead_accuracy", new Key("How well it aims ahead of moving targets (0 = not at all, 1 = perfectly).", 0, 1)),
            Map.entry("lift", new Key("Upward push.", 0, 10)),
            Map.entry("max_input", new Key("Most FE it takes in per transfer.", 0, 10000000)),
            Map.entry("max_targets", new Key("Most targets one burst pushes.", 1, 1024)),
            Map.entry("min_range", new Key("Closest distance in blocks it fires at.", 0, 256)),
            Map.entry("min_spin_to_fire", new Key("Barrel spin (0 to 1) needed before it fires.", 0, 1)),
            Map.entry("orb_speed", new Key("Plasma ball speed in blocks per tick.", 0.01, 100)),
            Map.entry("patch_size", new Key("Side of the fire patch in blocks.", 1, 15)),
            Map.entry("pellets", new Key("Pellets per shot.", 1, 64)),
            Map.entry("precision_lock_multiplier", new Key("Damage multiplier of the shot after holding a target for precision_lock_ticks.", 0, 100)),
            Map.entry("precision_lock_ticks", new Key("Ticks on one target before the precision shot.", 0, 12000)),
            Map.entry("reload_ticks", new Key("Ticks to reload one tube.", 1, 12000)),
            Map.entry("rocket_speed", new Key("Rocket speed in blocks per tick.", 0.01, 100)),
            Map.entry("salvo_interval", new Key("Salvo mode: ticks between the rounds of a salvo (0 = all at once).", 0, 200)),
            Map.entry("shell_speed", new Key("Shell launch speed in blocks per tick.", 0.01, 100)),
            Map.entry("shock_knockback", new Key("How hard the impact's shockwave throws mobs.", 0, 100)),
            Map.entry("shock_radius", new Key("Reach of the impact's shockwave in blocks.", 0, 64)),
            Map.entry("slowness_amplifier", new Key("Level of the Slowness its hits apply (0 = Slowness I).", 0, 10)),
            Map.entry("slowness_ticks", new Key("Ticks of Slowness per hit.", 0, 12000)),
            Map.entry("slug_speed", new Key("Slug speed in blocks per tick.", 0.01, 100)),
            Map.entry("spin_down_ticks", new Key("Ticks the barrels take to spin down.", 1, 12000)),
            Map.entry("spin_up_ticks", new Key("Ticks the barrels take to spin up.", 1, 12000)),
            Map.entry("splash_damage", new Key("Splash damage to targets around the impact.", 0, 10000)),
            Map.entry("splash_radius", new Key("Splash radius in blocks.", 0, 64)),
            Map.entry("turn_rate", new Key("Missile: degrees it can turn per tick while homing.", 0, 180)));

    public static final ForgeConfigSpec SPEC;
    private static final Map<String, Map<String, ForgeConfigSpec.ConfigValue<Object>>> VALUES = new LinkedHashMap<>();

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        for (String weapon : WEAPONS) {
            WeaponData data = builtIn(weapon);
            List<String> comment = new ArrayList<>();
            if (weapon.equals(WEAPONS.get(0))) comment.addAll(List.of(HEADER));
            comment.add(NAMES.get(weapon));
            b.comment(comment.toArray(String[]::new)).push(weapon);
            Map<String, ForgeConfigSpec.ConfigValue<Object>> values = new LinkedHashMap<>();
            for (String key : keys(data)) {
                Key spec = key(key);
                values.put(key, b.comment(spec.description(), "Mod default: " + format(builtInValue(data, key)),
                                "Range: " + format(spec.min()) + " ~ " + format(spec.max()))
                        .define(key, (Object) DEFAULT, spec::accepts));
            }
            VALUES.put(weapon, values);
            b.pop();
        }
        SPEC = b.build();
    }

    private TurretStatsConfig() {
    }

    /** The mod's own JSON for {@code weapon}, read from the jar (never a datapack's). */
    public static WeaponData builtIn(String weapon) {
        String path = "/data/" + Bastion.MOD_ID + "/turret_weapons/" + weapon + ".json";
        try (InputStream in = TurretStatsConfig.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("Missing built-in turret weapon " + path);
            JsonElement json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            return WeaponData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(false, error -> {});
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A weapon's keys in file order: the common stats, then its params alphabetically. */
    public static List<String> keys(WeaponData data) {
        List<String> keys = new ArrayList<>(COMMON.keySet());
        data.params().keySet().stream().sorted().forEach(keys::add);
        return keys;
    }

    /**
     * The numbers set for {@code weapon} (keys left at "default" are absent). Empty while the file isn't loaded yet, so the
     * JSON stands as it is.
     */
    public static Map<String, Double> overrides(String weapon) {
        Map<String, ForgeConfigSpec.ConfigValue<Object>> values = VALUES.get(weapon);
        if (values == null || !SPEC.isLoaded()) return Map.of();
        Map<String, Double> set = new HashMap<>();
        values.forEach((key, value) -> {
            if (value.get() instanceof Number n) set.put(key, n.doubleValue());
        });
        return set;
    }

    private static Key key(String name) {
        Key key = COMMON.containsKey(name) ? COMMON.get(name) : PARAMS.get(name);
        if (key == null) throw new IllegalStateException("TurretStatsConfig.PARAMS has no entry for weapon param " + name);
        return key;
    }

    private static double builtInValue(WeaponData d, String key) {
        return switch (key) {
            case "range" -> d.range();
            case "fire_interval" -> d.fireInterval();
            case "damage" -> d.damage();
            case "spread" -> d.spread();
            case "ammo_per_shot" -> d.ammoPerShot();
            case "turn_speed" -> d.aim().turnSpeed();
            case "aim_tolerance" -> d.aim().tolerance();
            case "aim_lock_ticks" -> d.aim().lockTicks();
            case "min_pitch" -> d.aim().minPitch();
            case "max_pitch" -> d.aim().maxPitch();
            case "sweep_tolerance" -> d.aim().sweepTolerance();
            case "heat_per_shot" -> d.heat().perShot();
            case "heat_dissipation_per_second" -> d.heat().dissipationPerSecond();
            case "max_heat" -> d.heat().max();
            case "overheat_ticks" -> d.heat().overheatTicks();
            default -> d.param(key);
        };
    }

    /** 24 -> "24", 1.6667 -> "1.6667", 10000000 -> "10000000" (floats from the JSON print as written). */
    private static String format(double value) {
        return new BigDecimal(Float.toString((float) value)).stripTrailingZeros().toPlainString();
    }

    private static <V> Map<String, V> ordered(Object... pairs) {
        Map<String, V> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            @SuppressWarnings("unchecked") V value = (V) pairs[i + 1];
            map.put((String) pairs[i], value);
        }
        return java.util.Collections.unmodifiableMap(map);
    }
}
```

(`format` goes through `Float.toString` because the JSON values are floats: `1.6667` must not print as `1.6666999...`.)

- [ ] **Step 4: Register the file**

In `src/main/java/dev/bastion/Bastion.java` add the import `dev.bastion.config.TurretStatsConfig` and, right after the `BastionClientConfig` line:

```java
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, TurretStatsConfig.SPEC, TurretStatsConfig.FILE);
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew runGameTestServer --offline > build/gametest.log 2>&1`
Expected: exit code 0, `All 164 required tests passed :)`; `run/config/bastion-turrets.toml` now exists: open it and check the header, `[gun]` first, `damage = "default"` with the three comment lines above it, and a `[railgun]` section ending in its params (`boss_multiplier` ... `slug_speed`).

---

### Task 3: Overrides applied, live reload, docs

**Files:**
- Modify: `src/main/java/dev/bastion/weapon/WeaponDataLoader.java`
- Modify: `src/main/java/dev/bastion/config/TurretStatsConfig.java`
- Modify: `src/main/java/dev/bastion/network/WeaponDataSync.java`
- Modify: `src/main/java/dev/bastion/Bastion.java`
- Modify: `src/main/java/dev/bastion/gametest/TurretStatsConfigTests.java`
- Modify: `docs/PLAN.md`, `docs/TESTING.md`, `docs/HANDOFF.md`, `docs/release/DESCRIPTION.md`, `docs/release/DESCRIPTION.curseforge.html`, `docs/release/UPLOAD.md`

**Interfaces:**
- Consumes: `WeaponDataLoader.merge`, `TurretStatsConfig.overrides`, `TurretStatsConfig.SPEC`.
- Produces: `WeaponDataLoader.rebuild()` (effective = merge(raw, TurretStatsConfig::overrides)); `TurretStatsConfig.onReloading(ModConfigEvent.Reloading)`; `TurretStatsConfig.reloaded()` (rebuild + resync all); `WeaponDataSync.current()`.

- [ ] **Step 1: Write the failing tests**

Add the imports `dev.bastion.registry.BastionBlocks`, `dev.bastion.registry.BastionItems`, `dev.bastion.registry.BastionWeaponTypes`, `dev.bastion.turret.TurretBaseBlockEntity`, `dev.bastion.turret.TurretInventory`, `dev.bastion.turret.TurretTier`, `dev.bastion.weapon.StatSheet`, `net.minecraft.core.BlockPos`, `net.minecraft.core.Direction`, `net.minecraft.world.item.ItemStack`, `net.minecraftforge.common.capabilities.ForgeCapabilities`, `net.minecraftforge.energy.IEnergyStorage` to `TurretStatsConfigTests` and add:

```java
    @SuppressWarnings("unchecked")
    private static ForgeConfigSpec.ConfigValue<Object> value(String weapon, String key) {
        return (ForgeConfigSpec.ConfigValue<Object>) TurretStatsConfig.SPEC.getValues().get(List.of(weapon, key));
    }

    /** Sets weapon.key in the (real) file, runs {@code check} on the rebuilt weapons, and always puts "default" back. */
    private static void withValue(String weapon, String key, Object number, Runnable check) {
        ForgeConfigSpec.ConfigValue<Object> value = value(weapon, key);
        try {
            value.set(number);
            WeaponDataLoader.rebuild();
            check.run();
        } finally {
            value.set(TurretStatsConfig.DEFAULT);
            WeaponDataLoader.rebuild();
        }
    }

    /** A number in the file reaches the weapon (as an int or a decimal); "default" brings the JSON's value back. */
    @GameTest(template = ARENA, batch = "configOverrideReachesTheWeapon")
    public static void configOverrideReachesTheWeapon(GameTestHelper helper) {
        float json = TurretStatsConfig.builtIn("sniper").damage();
        withValue("sniper", "damage", 77.5, () -> helper.assertTrue(weapon("sniper").damage() == 77.5f, "damage " + weapon("sniper").damage()));
        withValue("sniper", "damage", Integer.valueOf(31), () -> helper.assertTrue(weapon("sniper").damage() == 31f, "integer 31 gave " + weapon("sniper").damage()));
        helper.assertTrue(weapon("sniper").damage() == json, "\"default\" did not restore " + json + ", got " + weapon("sniper").damage());
        helper.succeed();
    }

    /** The charge time a turret actually waits comes from the file's charge_ticks. */
    @GameTest(template = ARENA, batch = "railgunChargeOverrideSetsTheChargeTime")
    public static void railgunChargeOverrideSetsTheChargeTime(GameTestHelper helper) {
        withValue("railgun", "charge_ticks", 9, () -> {
            int ticks = BastionWeaponTypes.RAILGUN.get().chargeTicks(StatSheet.of(weapon("railgun"), TurretTier.T1));
            helper.assertTrue(ticks == 9, "railgun charges " + ticks + " ticks");
        });
        helper.succeed();
    }

    /** Saving the file while the game runs (Forge's reload) rebuilds the weapons. */
    @GameTest(template = ARENA, batch = "savingTheFileRebuildsTheWeapons")
    public static void savingTheFileRebuildsTheWeapons(GameTestHelper helper) {
        ForgeConfigSpec.ConfigValue<Object> value = value("sniper", "damage");
        try {
            value.set(66);
            TurretStatsConfig.reloaded();
            helper.assertTrue(weapon("sniper").damage() == 66f, "after the reload damage is " + weapon("sniper").damage());
        } finally {
            value.set(TurretStatsConfig.DEFAULT);
            WeaponDataLoader.rebuild();
        }
        helper.succeed();
    }

    /** A capacitor lowered below what a turret holds: it shows and spends only the new capacity and takes no more FE. */
    @GameTest(template = ARENA, batch = "loweringEnergyCapacityNeverOverfillsATurret")
    public static void loweringEnergyCapacityNeverOverfillsATurret(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 3);
        helper.setBlock(pos, BastionBlocks.TURRET_BASE.get());
        TurretBaseBlockEntity turret = (TurretBaseBlockEntity) helper.getBlockEntity(pos);
        turret.inventory().setStackInSlot(TurretInventory.WEAPON, new ItemStack(BastionItems.ION_RIFLE_TURRET.get()));
        IEnergyStorage energy = turret.getCapability(ForgeCapabilities.ENERGY, Direction.UP).orElseThrow(IllegalStateException::new);
        for (int left = 30000, got; left > 0 && (got = energy.receiveEnergy(left, false)) > 0; ) left -= got;
        helper.assertTrue(turret.energy() == 30000, "charged to " + turret.energy());
        withValue("ion_rifle", "energy_capacity", 1000, () -> {
            helper.assertTrue(turret.energy() == 1000, "holds " + turret.energy() + " with capacity 1000");
            helper.assertTrue(energy.receiveEnergy(500, false) == 0, "took FE above the new capacity");
        });
        helper.succeed();
    }
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew runGameTestServer --offline > build/gametest.log 2>&1`
Expected: compile failure, `cannot find symbol` for `rebuild` and `reloaded`.

- [ ] **Step 3: Raw and effective weapons in `WeaponDataLoader`**

In `src/main/java/dev/bastion/weapon/WeaponDataLoader.java` add the import `dev.bastion.config.TurretStatsConfig`. Replace the field line and `apply(...)` so the class reads (the `get`, `all`, `accept` and `merge` methods stay as they are):

```java
    /** The JSON as loaded (datapacks included), before bastion-turrets.toml; server side only. */
    private static volatile Map<ResourceLocation, WeaponData> raw = Map.of();
    /** What turrets use: raw with the config's numbers laid over it (on clients: what the server sent). */
    private static volatile Map<ResourceLocation, WeaponData> weapons = Map.of();
```

```java
    /** Lays bastion-turrets.toml over the last loaded JSON again (after a datapack load, or when the file changed). */
    public static void rebuild() {
        weapons = merge(raw, TurretStatsConfig::overrides);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resources, ProfilerFiller profiler) {
        Map<ResourceLocation, WeaponData> loaded = new HashMap<>();
        files.forEach((id, json) -> WeaponData.CODEC.parse(JsonOps.INSTANCE, json)
                .resultOrPartial(error -> Bastion.LOGGER.error("Bad turret weapon {}: {}", id, error))
                .ifPresent(data -> loaded.put(id, data)));
        raw = Map.copyOf(loaded);
        rebuild();
        Bastion.LOGGER.info("Loaded {} turret weapons", loaded.size());
    }
```

Also change the class Javadoc's last sentence to: "Clients receive the effective map (the JSON with bastion-turrets.toml laid over it) through WeaponDataSync, so tooltips, slot validation and the Info tab agree with the server."

- [ ] **Step 4: `WeaponDataSync.current()` and the reload handler**

In `src/main/java/dev/bastion/network/WeaponDataSync.java` add after the record header line:

```java
    /** Everything clients need right now: the effective weapons and the modifiers. */
    public static WeaponDataSync current() {
        return new WeaponDataSync(WeaponDataLoader.all(), ModifierDataLoader.all());
    }
```

In `src/main/java/dev/bastion/Bastion.java` change the first line of `syncWeaponData` to `WeaponDataSync message = WeaponDataSync.current();`, and add after the `TurretStatsConfig` registration line:

```java
        modBus.addListener(TurretStatsConfig::onReloading);
```

In `src/main/java/dev/bastion/config/TurretStatsConfig.java` add the imports `dev.bastion.network.BastionNetwork`, `dev.bastion.network.WeaponDataSync`, `dev.bastion.weapon.WeaponDataLoader`, `net.minecraft.server.MinecraftServer`, `net.minecraftforge.fml.event.config.ModConfigEvent`, `net.minecraftforge.network.PacketDistributor`, `net.minecraftforge.server.ServerLifecycleHooks`, and these methods after `overrides(...)`:

```java
    /**
     * Forge saw the file change (mod bus, its file-watcher thread): rebuild on the server thread. Without a running server
     * (a client on someone else's server) there is nothing to do: that server's file counts and its numbers arrive by packet.
     */
    public static void onReloading(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() != SPEC) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) server.execute(TurretStatsConfig::reloaded);
    }

    /** Rebuilds the effective weapons and sends them to every player; turrets use them from their next tick. */
    public static void reloaded() {
        WeaponDataLoader.rebuild();
        BastionNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(), WeaponDataSync.current());
        Bastion.LOGGER.info("Reloaded {}", FILE);
    }
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew runGameTestServer --offline > build/gametest.log 2>&1`
Expected: exit code 0, `All 168 required tests passed :)`. Then check `run/config/bastion-turrets.toml` still reads `damage = "default"` under `[sniper]`, `[railgun]` and `[ion_rifle]` (the tests put every value back).

- [ ] **Step 6: Compat run and build**

Run: `./gradlew runGameTestServer --offline -PwithCompatMods > build/gametest-compat.log 2>&1` → expected `All 168 required tests passed :)`.
Run: `./gradlew build --offline > build/build.log 2>&1` → expected exit code 0.

- [ ] **Step 7: PLAN.md status**

In `docs/PLAN.md`, directly under the heading `### Config stat turret (...)`, add the line:

```markdown
**Status**: dibuat 2026-10-17 (plan `docs/superpowers/plans/2026-10-17-turret-stats-config.md`, 168 GameTest lolos), belum dites user di game, belum di-commit.
```

- [ ] **Step 8: TESTING.md**

Append to `docs/TESTING.md`:

```markdown
## Turret stats config (bastion-turrets.toml)
- [ ] Start the game once: `config/bastion-turrets.toml` appears next to `bastion-common.toml`, with the explanation at the top, one section per turret (`[gun]` first, `[plasma_cannon]` last) and every value at `"default"` with "Mod default" and "Range" in its comment.
- [ ] With a world open, set `[sniper] damage = 100` and save the file: within a second, a Sniper's Info tab shows 100 and it one-shots a zombie. Put `"default"` back and save: 24 again. No restart.
- [ ] Set `[railgun] charge_ticks = 10` and save: the Railgun charges for half a second.
- [ ] Set `[gun] damage = -5` (or `"abc"`) and restart: the log warns, the file shows `"default"` again and the Gun still works.
- [ ] Set `[ion_rifle] energy_capacity = 1000` on a full Ion Rifle: its energy bar shows 1,000 at most and it still fires.
- [ ] LAN / dedicated server: change the server's file; a second player sees the new numbers in the Info tab. Changing the client's own file changes nothing on that server.
```

- [ ] **Step 9: Store text and changelog**

In `docs/release/DESCRIPTION.md`, in the "For modpack makers" paragraph that starts "Weapon stats live in", replace its first two sentences ("Weapon stats live in `data/bastion/turret_weapons` and modules in `data/bastion/turret_modifiers`. Both are plain datapack JSON, same as the workstation recipes.") with:

```markdown
Every turret's stats and weapon-specific numbers are also in `config/bastion-turrets.toml`: leave a value at "default" or type a number, and it applies as soon as you save. Weapon stats come from `data/bastion/turret_weapons` and modules from `data/bastion/turret_modifiers`, plain datapack JSON like the workstation recipes; a number in the config wins over a datapack.
```

In `docs/release/DESCRIPTION.curseforge.html` make the same replacement inside the `<p>Weapon stats live in ...` paragraph, with the paths in `<code>`:

```html
Every turret's stats and weapon-specific numbers are also in <code>config/bastion-turrets.toml</code>: leave a value at "default" or type a number, and it applies as soon as you save. Weapon stats come from <code>data/bastion/turret_weapons</code> and modules from <code>data/bastion/turret_modifiers</code>, plain datapack JSON like the workstation recipes; a number in the config wins over a datapack.
```

In `docs/release/UPLOAD.md`, insert above `## Changelog: 0.2.0`:

````markdown
## Changelog: next version (not released yet)

```
New
- Turret stats config: config/bastion-turrets.toml holds every turret's stats (damage, range, fire rate, ammo or FE
  per shot, turn speed, heat, ...) and its own numbers (charge time, blast radius, knockback, ...). Each value starts
  as "default" (the mod's number, or a datapack's); type a number to change it. Saving the file applies it at once,
  no restart; on a server, the server's file counts.
```

````

- [ ] **Step 10: HANDOFF.md**

In `docs/HANDOFF.md`, add as the first lines under `## 0. Where we stopped`:

```markdown
**2026-10-17: turret stats config built, uncommitted, not yet tested by the user in game.** `config/bastion-turrets.toml`
(`config/TurretStatsConfig`, spec PLAN §8 "Config stat turret", plan `docs/superpowers/plans/2026-10-17-turret-stats-config.md`):
16 weapons x (15 common stats + their JSON params), each `"default"` or a number; `WeaponDataLoader` keeps raw JSON and
serves raw + overrides (`WeaponData.withOverrides`, `merge`, `rebuild`); `ModConfigEvent.Reloading` -> server thread
`TurretStatsConfig.reloaded()` -> rebuild + `WeaponDataSync` to all. 168/168 GameTests (also with compat mods).
```

- [ ] **Step 11: Final check**

Run `git status --short`: only the files of this plan changed or added (plus `modlist.html`, `sfx/` untracked as before); nothing committed.
