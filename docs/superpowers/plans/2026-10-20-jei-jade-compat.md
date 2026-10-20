# JEI & Jade Compat Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Optional JEI and Jade support. JEI shows recipe pages for the 5 workstations, a "Turret Weapons" page per weapon, info pages, and a working + button. Jade shows turret, workstation, Feed Hub and Control Hub info.

**Architecture:**
- All logic that can be tested lives in common Bastion code and is covered by GameTests, which run without JEI or Jade:
  - `WeaponStatRows`: the stat table.
  - `WorkstationBlockEntity.fill` and the `WorkstationFill` packet.
  - `BlockInfo`: the Jade lines, plus `corePos` for multiblock parts.
  - `HubInterfaceBlockEntity.connectedCount`.
- Thin adapters in `dev.bastion.client.compat.jei` (client package, §9.5) and `dev.bastion.compat.jade` call that code. JEI and Jade discover the adapters by annotation; nothing in Bastion refers to them.

**Tech Stack:** Forge 1.20.1 (47.4.26), Java 17, JEI 15.20.0.134 (Modrinth `ujE6Mlsq`), Jade 11.13.3+forge (Modrinth `xJQHCmWJ`), GameTest.

**Spec:** `docs/PLAN.md` §8 "Compat JEI & Jade" (approved 2026-10-20 12:59).

## Global Constraints

**Git and protected files:**
- **No commits:** never commit, push or tag. The user's rule is "jangan commit sebelum saya bilang 'commit'". Each task ends with its checks passing, not with a commit.
- Never commit or stage `modlist.html` or `sfx/`.
- Never touch `run/saves/showcase`.
- Do not edit `run/config/*` without asking.

**Gradle and test runs:**
- Gradle always runs with `--offline`. The one exception is Task 4 Step 1, a one-time dependency fetch.
- Never start the client yourself (`runClient`); the user runs it. GameTest server runs are fine.
- Never pipe a test run into `grep`. Save the full log to `build/*.log`, read its exit code, then grep the file.
- A GameTest run passes when the log says `All N required tests passed`.
- Known flaky tests that pass on a rerun: `ControlHubTests.satelliteHalvesBreakTogether`, `ControlHubTests.powerPortTakesFeOnlyWhenFormed`, `TurretLargeTests.largeBaseIsOneBlock` ("drops: []" or "did not form"). Rerun once before treating one as real.
- `TurretStatsConfigTests.runConfigLeavesEveryValueAtDefault` fails while the user's test value `[repulsor_dome] max_targets = 400` is in `run/config/bastion-turrets.toml`. Ask the user before the first GameTest run; never "fix" the test.

**Code rules:**
- Client code (anything importing `net.minecraft.client`) goes only in `dev.bastion.client..` packages. JEI code goes in `dev.bastion.client.compat.jei`.
- Jade code goes in `dev.bastion.compat.jade` and must not import `net.minecraft.client`.
- No vanilla visual or audio assets (PLAN §1). JEI pages use JEI's own slot and arrow drawables and plain text.
- Balance numbers stay in config or JSON. JEI pages read live weapon data and config; nothing is hardcoded.
- Match the surrounding code: Javadoc one-liners, `ponytail:` comments only for deliberate shortcuts, no new abstractions beyond those named here.

**Dependencies and text:**
- Optional dependencies only: with neither JEI nor Jade installed, the mod loads and runs, on a dedicated server too.
- Lang: every new key goes into both `en_us.json` and `id_id.json`, with the texts given in this plan.

## Review Focus

1. **No JEI/Jade installed** (GameTest server, `runServer`, a player without them). Nothing from `compat.jade` or `client.compat.jei` loads, and there is no crash. Pinned by the full GameTest run in Task 4 and Task 6, which has neither on the classpath, and by the manual `runServer` check.
2. **JEI loads before `WeaponDataSync` arrives** (login order). Every weapon still gets a page, and the numbers are read when the page is drawn. Pinned by registering all `WeaponModuleItem`s unfiltered and guarding `data() == null` in `WeaponCategory` (Task 5).
3. **The + button with input slots already holding another recipe's leftovers**, so the ingredients do not fit. Nothing moves, nothing is lost or duplicated. Pinned by `fillMovesNothingWhenItDoesNotFit` (Task 2).
4. **A datapack station recipe with more than 6 ingredients.** The JEI page lays the extras out on a second row instead of crashing or hiding them (Task 5, `StationCategory.inputX/inputY`).
5. **Jade on a turret whose owner is offline and not in the profile cache** (GameTest servers have no profile cache). There is no Owner line and no crash. Pinned by `turretInfoLines`, whose owner is a random UUID with no resolvable name (Task 3).

---

## File Structure

**New files:**

| File | What it holds |
|---|---|
| `src/main/java/dev/bastion/weapon/WeaponStatRows.java` | The stat table shared by the turret Info tab and JEI |
| `src/main/java/dev/bastion/network/WorkstationFill.java` | C→S packet for JEI's + button |
| `src/main/java/dev/bastion/compat/BlockInfo.java` | What Jade shows (later The One Probe): write on the server, lines on the client, `corePos` |
| `src/main/java/dev/bastion/compat/jade/BastionJadePlugin.java` | `@WailaPlugin`: registrations and the multiblock redirect |
| `src/main/java/dev/bastion/compat/jade/BastionJadeProvider.java` | Enum of 4 providers (server data + tooltip) |
| `src/main/java/dev/bastion/client/compat/jei/BastionJeiPlugin.java` | `@JeiPlugin`: categories, recipes, info pages, catalysts, transfer |
| `src/main/java/dev/bastion/client/compat/jei/StationCategory.java` | One workstation's recipe page |
| `src/main/java/dev/bastion/client/compat/jei/WeaponCategory.java` | The "Turret Weapons" page |
| `src/main/java/dev/bastion/client/compat/jei/StationTransfer.java` | The + button |
| `src/main/java/dev/bastion/gametest/CompatTests.java` | GameTests for everything above that runs without JEI/Jade |

**Modified files:**

| File | Change |
|---|---|
| `client/screen/TurretScreen.java` | `renderInfo` uses `WeaponStatRows` |
| `turret/TurretBaseBlockEntity.java` | Static `maxHealth(tier, large, effect)` |
| `workstation/WorkstationBlockEntity.java` | `fill(Player, int)` |
| `network/BastionNetwork.java` | Register `WorkstationFill`; PROTOCOL 10 → 11 |
| `hub/HubInterfaceBlockEntity.java` | `connectedCount()`; shared scan with `visibleTurrets` |
| `build.gradle` | JEI/Jade `compileOnly` + `clientDevMods` |
| `src/main/resources/META-INF/mods.toml` | Optional `jei` and `jade` |
| `assets/bastion/lang/en_us.json`, `id_id.json` | New keys |
| Docs | `docs/TESTING.md`, `docs/PLAN.md`, `docs/HANDOFF.md`, `docs/release/DESCRIPTION.md`, `docs/release/DESCRIPTION.curseforge.html`, `docs/release/UPLOAD.md` |

(Paths without a prefix are under `src/main/java/dev/bastion/`.)

**Test commands** (used by every task):
- **GameTests:** `./gradlew runGameTestServer --offline > build/gametest-<task>.log 2>&1; echo "exit=$?"`, then `grep -E "required tests passed|tests failed|Failed|Exception" build/gametest-<task>.log | tail -20`.
- **Compile:** `./gradlew compileJava --offline > build/compile-<task>.log 2>&1; echo "exit=$?"`, then `grep -E "error:|BUILD" build/compile-<task>.log | head -20`.
- **Build:** `./gradlew build --offline > build/build-<task>.log 2>&1; echo "exit=$?"`.

---

### Task 1: Shared weapon stat rows

**Files:**
- Create: `src/main/java/dev/bastion/weapon/WeaponStatRows.java`
- Create: `src/main/java/dev/bastion/gametest/CompatTests.java`
- Modify: `src/main/java/dev/bastion/turret/TurretBaseBlockEntity.java:307-310`
- Modify: `src/main/java/dev/bastion/client/screen/TurretScreen.java:426-488`

**Interfaces:**
- Consumes: `StatSheet.of(WeaponData, TurretTier)`, `WeaponData.param/params/ammoPerShot/type`, `BastionWeaponTypes.LASER_RIFLE`.
- Produces:
  - `WeaponStatRows.Row(String key, String value)`.
  - `WeaponStatRows.left(WeaponData, StatSheet) -> List<Row>`.
  - `WeaponStatRows.right(WeaponData, StatSheet, float maxHealth, float regenPerSecond) -> List<Row>`.
  - `WeaponStatRows.fmt(float) -> String` and `WeaponStatRows.fe(float) -> String`.
  - `TurretBaseBlockEntity.maxHealth(TurretTier, boolean large, ModifierEffect) -> float` (static).
  - `CompatTests.text(Component) -> String` (test helper, used by Task 3).

- [ ] **Step 1: Write the failing test.** Create `CompatTests.java`:

```java
package dev.bastion.gametest;

import dev.bastion.Bastion;
import dev.bastion.turret.TurretTier;
import dev.bastion.weapon.StatSheet;
import dev.bastion.weapon.WeaponData;
import dev.bastion.weapon.WeaponDataLoader;
import dev.bastion.weapon.WeaponStatRows;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.LiteralContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** What JEI and Jade show, tested without either installed (PLAN "Compat JEI & Jade"). */
@GameTestHolder(Bastion.MOD_ID)
@PrefixGameTestTemplate(false)
public class CompatTests {
    private static final String ARENA = "arena", EMPTY = "empty";

    /** A component as "key[arg,arg]" (servers have no mod lang): what the tests compare. */
    static String text(Component component) {
        StringBuilder out = new StringBuilder();
        if (component.getContents() instanceof TranslatableContents translatable) {
            out.append(translatable.getKey());
            if (translatable.getArgs().length > 0) {
                out.append(Arrays.stream(translatable.getArgs()).map(arg -> arg instanceof Component c ? text(c) : String.valueOf(arg))
                        .collect(Collectors.joining(",", "[", "]")));
            }
        } else if (component.getContents() instanceof LiteralContents literal) {
            out.append(literal.text());
        }
        for (Component sibling : component.getSiblings()) out.append(text(sibling));
        return out.toString();
    }

    private static List<String> keys(List<WeaponStatRows.Row> rows) {
        return rows.stream().map(WeaponStatRows.Row::key).toList();
    }

    private static String value(List<WeaponStatRows.Row> rows, String key) {
        return rows.stream().filter(r -> r.key().equals(key)).findFirst().orElseThrow().value();
    }

    /** The Info tab's table, now shared with JEI: same rows, same order, same text as before the move. */
    @GameTest(template = EMPTY, batch = "statRowsMatchTheInfoTab")
    public static void statRowsMatchTheInfoTab(GameTestHelper helper) {
        WeaponData gun = WeaponDataLoader.get(Bastion.id("gun"));
        StatSheet stats = StatSheet.of(gun, TurretTier.T1);
        List<WeaponStatRows.Row> left = WeaponStatRows.left(gun, stats);
        helper.assertTrue(keys(left).equals(List.of("damage", "fire_rate", "range", "turn_speed", "pierce", "spread", "ammo_save")), "left " + keys(left));
        helper.assertTrue(value(left, "damage").equals("7") && value(left, "fire_rate").equals("1.7/s") && value(left, "range").equals("32")
                && value(left, "pierce").equals("0") && value(left, "spread").equals("0.3°") && value(left, "ammo_save").equals("0%"), "gun left " + left);
        List<WeaponStatRows.Row> right = WeaponStatRows.right(gun, stats, 40, 0.5f);
        helper.assertTrue(keys(right).equals(List.of("heat_per_shot", "max_heat", "cooling", "max_hp", "regen", "ammo")), "right " + keys(right));
        helper.assertTrue(value(right, "max_hp").equals("40") && value(right, "regen").equals("0.5/s") && value(right, "ammo").equals("1"), "gun right " + right);

        WeaponData tesla = WeaponDataLoader.get(Bastion.id("tesla"));
        helper.assertTrue(keys(WeaponStatRows.right(tesla, StatSheet.of(tesla, TurretTier.T1), 40, 0))
                .equals(List.of("energy_per_shot", "energy_capacity", "max_input", "max_hp", "regen", "bolts")), "tesla is an energy weapon with bolts");
        WeaponData laser = WeaponDataLoader.get(Bastion.id("laser_rifle"));
        helper.assertTrue(value(WeaponStatRows.left(laser, StatSheet.of(laser, TurretTier.T1)), "pierce").equals("∞"), "the Laser Rifle pierces everything");
        WeaponData missiles = WeaponDataLoader.get(Bastion.id("missile_launcher"));
        List<WeaponStatRows.Row> missileRight = WeaponStatRows.right(missiles, StatSheet.of(missiles, TurretTier.T1), 60, 0);
        helper.assertTrue(missileRight.get(missileRight.size() - 1).key().equals("reload"), "the Missile Launcher shows its reload: " + keys(missileRight));
        helper.assertTrue(WeaponStatRows.fe(3000).equals("3k") && WeaponStatRows.fe(1500).equals("1.5k") && WeaponStatRows.fe(800).equals("800"), "fe()");
        helper.succeed();
    }
}
```

- [ ] **Step 2: Run the compile and check that it fails.** Use the Compile command with `<task>` = `t1`.
  Expected: `exit=1`, `error: cannot find symbol ... WeaponStatRows`.

- [ ] **Step 3: Create `WeaponStatRows.java`.** The logic is moved unchanged from `TurretScreen.renderInfo`:

```java
package dev.bastion.weapon;

import dev.bastion.registry.BastionWeaponTypes;

import java.util.List;
import java.util.Locale;

/**
 * The stat table of a turret weapon: the turret GUI's Info tab and JEI's weapon pages draw the same rows. Keys name the
 * label (gui.bastion.info.<key>); values are ready to draw.
 */
public final class WeaponStatRows {
    public record Row(String key, String value) {
    }

    private WeaponStatRows() {
    }

    public static List<Row> left(WeaponData data, StatSheet stats) {
        return List.of(
                new Row("damage", fmt(stats.damage())),
                new Row("fire_rate", fmt(20f / stats.fireInterval()) + "/s"),
                new Row("range", fmt(stats.range())),
                new Row("turn_speed", fmt(stats.turnSpeed() * 20) + "°/s"),
                // The Laser Rifle passes through every valid target on its line.
                new Row("pierce", data.type().equals(BastionWeaponTypes.LASER_RIFLE.getId()) ? "∞" : String.valueOf(stats.pierce())),
                new Row("spread", fmt(stats.spread()) + "°"),
                new Row("ammo_save", Math.round(stats.ammoSaveChance() * 100) + "%"));
    }

    public static List<Row> right(WeaponData data, StatSheet stats, float maxHealth, float regenPerSecond) {
        if (data.params().containsKey("energy_per_shot")) { // energy weapons: capacitor, no heat
            return List.of(
                    new Row("energy_per_shot", fe(data.param("energy_per_shot")) + " FE"),
                    new Row("energy_capacity", fe(data.param("energy_capacity")) + " FE"),
                    new Row("max_input", fe(data.param("max_input")) + " FE/t"),
                    new Row("max_hp", fmt(maxHealth)),
                    new Row("regen", fmt(regenPerSecond) + "/s"),
                    // Tesla: bolts per discharge; the Railgun also takes a slug per shot.
                    data.params().containsKey("bolts") ? new Row("bolts", String.valueOf(Math.round(data.param("bolts"))))
                            : new Row("ammo", String.valueOf(data.ammoPerShot())));
        }
        return List.of(
                new Row("heat_per_shot", fmt(stats.heatPerShot())),
                new Row("max_heat", fmt(stats.maxHeat())),
                new Row("cooling", fmt(stats.heatDissipationPerTick() * 20) + "/s"),
                new Row("max_hp", fmt(maxHealth)),
                new Row("regen", fmt(regenPerSecond) + "/s"),
                // Weapons that load ahead (Missile Launcher) show their per-missile reload instead of ammo per shot.
                data.params().containsKey("reload_ticks") ? new Row("reload", fmt(data.param("reload_ticks") / stats.reloadRate() / 20f) + "s")
                        : new Row("ammo", String.valueOf(data.ammoPerShot())));
    }

    /** 7 -> "7", 1.666 -> "1.7". */
    public static String fmt(float value) {
        return value == Math.round(value) ? String.valueOf(Math.round(value)) : String.format(Locale.ROOT, "%.1f", value);
    }

    /** 3000 -> "3k", 1500 -> "1.5k", 800 -> "800": energy figures that fit the info columns. */
    public static String fe(float value) {
        return value >= 1000 ? fmt(value / 1000f) + "k" : fmt(value);
    }
}
```

Check where `BastionWeaponTypes` lives: run `grep -rn "class BastionWeaponTypes" src/main/java` and fix the import if it is not `dev.bastion.registry`.

- [ ] **Step 4: Add the static max-health helper** in `TurretBaseBlockEntity.java`. Replace the existing `maxHealth()` body (lines 307-310) with:

```java
    /** HP of a base of this tier and size with these modules (the Info tab, JEI's weapon pages). */
    public static float maxHealth(TurretTier tier, boolean large, ModifierEffect effect) {
        float size = large ? BastionConfig.LARGE_HEALTH_MULTIPLIER.get().floatValue() : 1;
        return BastionConfig.maxHealth(tier) * size * Math.max(0.1f, 1 + effect.maxHealth());
    }

    public float maxHealth() {
        return maxHealth(tier, large(), inventory.modifierEffect());
    }
```

Add `import dev.bastion.modifier.ModifierEffect;` if it is missing.

- [ ] **Step 5: Make `TurretScreen.renderInfo` use the helper.**
  1. Replace the `String[][] left = {...}` and `String[][] right = ... ;` blocks and the two `statColumn` calls with:

```java
        StatSheet stats = StatSheet.of(data, menu.tier(), menu.turret().modifierEffect());
        statColumn(graphics, WeaponStatRows.left(data, stats), LEFT_X);
        statColumn(graphics, WeaponStatRows.right(data, stats, menu.maxHealth(), BastionConfig.regenPerSecond(menu.tier())), RIGHT_X);
```

  2. Replace `statColumn` with:

```java
    private void statColumn(GuiGraphics graphics, List<WeaponStatRows.Row> rows, int x) {
        for (int i = 0; i < rows.size(); i++) {
            int y = 46 + i * 10; // up to 7 rows above the ability divider at y 120
            String value = rows.get(i).value();
            int valueWidth = font.width(value);
            fit(graphics, Component.translatable("gui.bastion.info." + rows.get(i).key()), x, y, COLUMN_W - valueWidth - 4, MUTED);
            graphics.drawString(font, value, x + COLUMN_W - valueWidth, y, TEXT, false);
        }
    }
```

  3. Delete `TurretScreen.fe(float)` (its only callers were the rows).
  4. Keep `TurretScreen.fmt`: the HP tooltip still uses it.
  5. Add `import dev.bastion.weapon.WeaponStatRows;`. Remove imports that are no longer used: `BastionWeaponTypes`, if nothing else uses it.

- [ ] **Step 6: Compile.** Use the Compile command with `t1`.
  Expected: `exit=0`.

- [ ] **Step 7: Run the GameTests and check they pass.** Use the GameTests command with `t1`.
  Expected: `All N required tests passed` (N = previous count + 1).
  If the only failure is `runConfigLeavesEveryValueAtDefault`, see Global Constraints.

---

### Task 2: JEI + button on the server: `fill` and `WorkstationFill`

**Files:**
- Modify: `src/main/java/dev/bastion/workstation/WorkstationBlockEntity.java` (after `instantCraft`, ~line 189)
- Create: `src/main/java/dev/bastion/network/WorkstationFill.java`
- Modify: `src/main/java/dev/bastion/network/BastionNetwork.java:30,57-58`
- Test: `src/main/java/dev/bastion/gametest/CompatTests.java`

**Interfaces:**
- Consumes: `WorkstationBlockEntity.recipe()`, `selectRecipe(ResourceLocation)`, `recipeId()`, `items()`, `count(Counted, List<ItemStack>)`, `INPUTS`; `WorkstationMenu.pos()`, `stillValid(Player)`; `WorkstationBlock.core(BlockGetter, BlockPos)`.
- Produces:
  - `WorkstationBlockEntity.fill(Player player, int crafts) -> int`: the crafts moved.
  - `WorkstationFill(BlockPos pos, ResourceLocation recipe, int crafts)` with `MAX_CRAFTS = 64` and `apply(ServerPlayer) -> int`.
  - Used by Task 5's `StationTransfer` through `BastionNetwork.CHANNEL.sendToServer(new WorkstationFill(...))`.

- [ ] **Step 1: Write the failing tests.** Add to `CompatTests` the imports `dev.bastion.network.WorkstationFill`, `dev.bastion.registry.BastionBlocks`, `dev.bastion.workstation.*`, `net.minecraft.core.BlockPos`, `net.minecraft.core.Direction`, `net.minecraft.resources.ResourceLocation`, `net.minecraft.world.item.ItemStack`, `net.minecraft.world.item.Items`, `net.minecraftforge.common.util.FakePlayer`, `net.minecraftforge.registries.RegistryObject`, then:

```java
    /** Core of the test station; with the front north the station grows west, south and up (as in TurretWorkstationTests). */
    private static final BlockPos STATION = new BlockPos(4, 2, 2);
    private static final ResourceLocation KINETIC = Bastion.id("workstation/ammo_workstation/kinetic_rounds");

    private static WorkstationBlockEntity station(GameTestHelper helper, RegistryObject<WorkstationBlock> block) {
        block.get().placeAt(helper.getLevel(), helper.absolutePos(STATION), Direction.NORTH);
        return (WorkstationBlockEntity) helper.getBlockEntity(STATION);
    }

    /** The fake player two blocks in front of the station with its GUI open and an empty inventory. */
    private static FakePlayer guiOpen(GameTestHelper helper, WorkstationBlockEntity station) {
        FakePlayer player = ControlHubTests.player(helper, STATION.north(2));
        player.getInventory().clearContent();
        player.containerMenu = new WorkstationMenu(1, player.getInventory(), station);
        return player;
    }

    private static void done(GameTestHelper helper, FakePlayer player) {
        player.getInventory().clearContent();
        player.containerMenu = player.inventoryMenu;
        helper.succeed();
    }

    private static int input(WorkstationBlockEntity station, net.minecraft.world.item.Item item) {
        int n = 0;
        for (int i = 0; i < WorkstationBlockEntity.INPUTS; i++) if (station.items().getStackInSlot(i).is(item)) n += station.items().getStackInSlot(i).getCount();
        return n;
    }

    /** The + button: the recipe gets picked and one craft's ingredients move from the inventory into the inputs. */
    @GameTest(template = ARENA, batch = "fillMovesOneCraft")
    public static void fillMovesOneCraft(GameTestHelper helper) {
        WorkstationBlockEntity station = station(helper, BastionBlocks.AMMO_WORKSTATION);
        FakePlayer player = guiOpen(helper, station);
        player.getInventory().add(new ItemStack(Items.COPPER_INGOT, 10));
        player.getInventory().add(new ItemStack(Items.GUNPOWDER, 10));
        player.getInventory().add(new ItemStack(Items.IRON_NUGGET, 10));
        int crafts = new WorkstationFill(station.getBlockPos(), KINETIC, 1).apply(player);
        helper.assertTrue(crafts == 1 && KINETIC.equals(station.recipeId()), "filled " + crafts + ", recipe " + station.recipeId());
        helper.assertTrue(input(station, Items.COPPER_INGOT) == 1 && input(station, Items.GUNPOWDER) == 1 && input(station, Items.IRON_NUGGET) == 1,
                "inputs should hold one of each");
        helper.assertTrue(player.getInventory().countItem(Items.GUNPOWDER) == 9, "gunpowder left " + player.getInventory().countItem(Items.GUNPOWDER));
        done(helper, player);
    }

    /** Shift + button: as many crafts as the scarcest ingredient allows; what the inputs already hold counts. */
    @GameTest(template = ARENA, batch = "shiftFillsAsManyAsThePlayerHas")
    public static void shiftFillsAsManyAsThePlayerHas(GameTestHelper helper) {
        WorkstationBlockEntity station = station(helper, BastionBlocks.AMMO_WORKSTATION);
        station.selectRecipe(KINETIC);
        station.items().setStackInSlot(0, new ItemStack(Items.COPPER_INGOT, 2));
        FakePlayer player = guiOpen(helper, station);
        player.getInventory().add(new ItemStack(Items.COPPER_INGOT, 10));
        player.getInventory().add(new ItemStack(Items.GUNPOWDER, 5));
        player.getInventory().add(new ItemStack(Items.IRON_NUGGET, 64));
        player.getInventory().add(new ItemStack(Items.IRON_NUGGET, 36));
        int crafts = new WorkstationFill(station.getBlockPos(), KINETIC, WorkstationFill.MAX_CRAFTS).apply(player);
        helper.assertTrue(crafts == 5, "expected 5 crafts (5 gunpowder), got " + crafts);
        helper.assertTrue(input(station, Items.COPPER_INGOT) == 5 && input(station, Items.GUNPOWDER) == 5 && input(station, Items.IRON_NUGGET) == 5,
                "inputs " + input(station, Items.COPPER_INGOT) + "/" + input(station, Items.GUNPOWDER) + "/" + input(station, Items.IRON_NUGGET));
        helper.assertTrue(player.getInventory().countItem(Items.COPPER_INGOT) == 7, "took copper the inputs already had: " + player.getInventory().countItem(Items.COPPER_INGOT));
        helper.assertTrue(player.getInventory().countItem(Items.IRON_NUGGET) == 95 && player.getInventory().countItem(Items.GUNPOWDER) == 0, "wrong amounts taken");
        done(helper, player);
    }

    /** Short of an ingredient: nothing moves (the recipe is still picked, as a click on it in the GUI would). */
    @GameTest(template = ARENA, batch = "fillMovesNothingWhenShort")
    public static void fillMovesNothingWhenShort(GameTestHelper helper) {
        WorkstationBlockEntity station = station(helper, BastionBlocks.AMMO_WORKSTATION);
        FakePlayer player = guiOpen(helper, station);
        player.getInventory().add(new ItemStack(Items.COPPER_INGOT, 10));
        player.getInventory().add(new ItemStack(Items.IRON_NUGGET, 10));
        int crafts = new WorkstationFill(station.getBlockPos(), KINETIC, WorkstationFill.MAX_CRAFTS).apply(player);
        helper.assertTrue(crafts == 0 && input(station, Items.COPPER_INGOT) == 0 && player.getInventory().countItem(Items.COPPER_INGOT) == 10, "moved without gunpowder");
        helper.assertTrue(KINETIC.equals(station.recipeId()), "recipe not picked");
        done(helper, player);
    }

    /** Inputs full of something else (another recipe's leftovers): no room for one craft, so nothing moves or vanishes. */
    @GameTest(template = ARENA, batch = "fillMovesNothingWhenItDoesNotFit")
    public static void fillMovesNothingWhenItDoesNotFit(GameTestHelper helper) {
        WorkstationBlockEntity station = station(helper, BastionBlocks.AMMO_WORKSTATION);
        for (int i = 0; i < 5; i++) station.items().setStackInSlot(i, new ItemStack(Items.DIRT));
        FakePlayer player = guiOpen(helper, station);
        player.getInventory().add(new ItemStack(Items.COPPER_INGOT, 10));
        player.getInventory().add(new ItemStack(Items.GUNPOWDER, 10));
        player.getInventory().add(new ItemStack(Items.IRON_NUGGET, 10));
        int crafts = new WorkstationFill(station.getBlockPos(), KINETIC, 1).apply(player);
        helper.assertTrue(crafts == 0, "filled " + crafts + " into one free slot");
        helper.assertTrue(input(station, Items.DIRT) == 5 && player.getInventory().countItem(Items.COPPER_INGOT) == 10
                && player.getInventory().countItem(Items.GUNPOWDER) == 10 && player.getInventory().countItem(Items.IRON_NUGGET) == 10, "items moved or lost");
        done(helper, player);
    }

    /** Only the station whose GUI is open, and only its own recipes. */
    @GameTest(template = ARENA, batch = "fillRefusesOtherStationsAndClosedGuis")
    public static void fillRefusesOtherStationsAndClosedGuis(GameTestHelper helper) {
        WorkstationBlockEntity station = station(helper, BastionBlocks.AMMO_WORKSTATION);
        FakePlayer player = guiOpen(helper, station);
        player.getInventory().add(new ItemStack(Items.IRON_INGOT, 10));
        player.getInventory().add(new ItemStack(Items.COPPER_INGOT, 10));
        player.getInventory().add(new ItemStack(Items.GUNPOWDER, 10));
        player.getInventory().add(new ItemStack(Items.IRON_NUGGET, 10));
        ResourceLocation barrel = Bastion.id("workstation/part_workstation/gun_barrel");
        helper.assertTrue(new WorkstationFill(station.getBlockPos(), barrel, 1).apply(player) == 0 && station.recipeId() == null,
                "an Ammo Workstation took a Part Workstation recipe");
        helper.assertTrue(new WorkstationFill(station.getBlockPos().east(5), KINETIC, 1).apply(player) == 0, "filled a station whose GUI is not open");
        player.containerMenu = player.inventoryMenu;
        helper.assertTrue(new WorkstationFill(station.getBlockPos(), KINETIC, 1).apply(player) == 0, "filled with no GUI open");
        helper.assertTrue(input(station, Items.COPPER_INGOT) == 0 && player.getInventory().countItem(Items.COPPER_INGOT) == 10, "items moved");
        done(helper, player);
    }
```

  `ControlHubTests.player(...)` is package-private static and sets `containerMenu = inventoryMenu`; `guiOpen` overrides it.

- [ ] **Step 2: Compile and check it fails.** Use the Compile command with `t2`.
  Expected: `exit=1`, `cannot find symbol ... WorkstationFill`.

- [ ] **Step 3: Add `fill` to `WorkstationBlockEntity`** (below `instantCraft`):

```java
    /**
     * JEI's + button: moves the selected recipe's ingredients from the player's main inventory into the inputs for up to
     * {@code crafts} crafts, the most that both the player's items and the input slots allow. What the inputs already hold
     * counts; stacks of anything else stay where they are. Nothing moves when not even one craft fits. Returns the crafts.
     */
    public int fill(Player player, int crafts) {
        WorkstationRecipe recipe = recipe();
        if (recipe == null) return 0;
        for (int n = crafts; n > 0; n--) { // ponytail: linear from the top, at most 64 tries of 6x36 stacks on a click
            if (move(recipe, n, player, true)) {
                move(recipe, n, player, false);
                return n;
            }
        }
        return 0;
    }

    /** Puts enough of every ingredient for {@code crafts} crafts into the inputs, on copies; writes them back unless simulating. */
    private boolean move(WorkstationRecipe recipe, int crafts, Player player, boolean simulate) {
        List<ItemStack> slots = new ArrayList<>(INPUTS);
        for (int i = 0; i < INPUTS; i++) slots.add(items.getStackInSlot(i).copy());
        List<ItemStack> inventory = player.getInventory().items.stream().map(ItemStack::copy).toList();
        for (WorkstationRecipe.Counted counted : recipe.ingredients()) {
            int left = counted.count() * crafts - count(counted, slots);
            for (ItemStack source : inventory) {
                if (left <= 0) break;
                if (counted.ingredient().test(source)) left -= insert(slots, source, left);
            }
            if (left > 0) return false;
        }
        if (!simulate) {
            for (int i = 0; i < INPUTS; i++) items.setStackInSlot(i, slots.get(i));
            for (int i = 0; i < inventory.size(); i++) player.getInventory().items.set(i, inventory.get(i));
            setChanged();
        }
        return true;
    }

    /** Moves up to {@code max} of {@code source} into the slots, onto the same item first, then into empty ones; returns how many. */
    private static int insert(List<ItemStack> slots, ItemStack source, int max) {
        int moved = 0;
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < slots.size() && moved < max && !source.isEmpty(); i++) {
                ItemStack slot = slots.get(i);
                if (pass == 0 ? !ItemStack.isSameItemSameTags(slot, source) : !slot.isEmpty()) continue;
                int n = Math.min(Math.min(source.getMaxStackSize() - slot.getCount(), max - moved), source.getCount());
                if (n <= 0) continue;
                if (slot.isEmpty()) slots.set(i, source.copyWithCount(n));
                else slot.grow(n);
                source.shrink(n);
                moved += n;
            }
        }
        return moved;
    }
```

Add `import net.minecraft.world.entity.player.Player;` if it is missing. `ArrayList` and `List` are already imported.

- [ ] **Step 4: Create `WorkstationFill.java`:**

```java
package dev.bastion.network;

import dev.bastion.workstation.WorkstationBlock;
import dev.bastion.workstation.WorkstationBlockEntity;
import dev.bastion.workstation.WorkstationMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C->S from JEI's + button on a workstation recipe (PLAN "Compat JEI & Jade"): pick the recipe and move its ingredients for
 * {@code crafts} crafts from the player's inventory into the inputs.
 */
public record WorkstationFill(BlockPos pos, ResourceLocation recipe, int crafts) {
    /** Shift + click: as many crafts as fit. */
    public static final int MAX_CRAFTS = 64;

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeResourceLocation(recipe);
        buf.writeVarInt(crafts);
    }

    public static WorkstationFill decode(FriendlyByteBuf buf) {
        return new WorkstationFill(buf.readBlockPos(), buf.readResourceLocation(), Mth.clamp(buf.readVarInt(), 1, MAX_CRAFTS));
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player != null) apply(player);
        context.get().setPacketHandled(true);
    }

    /** Only for the station whose GUI the sender has open and can still reach (as WorkstationAction); returns the crafts moved. */
    public int apply(ServerPlayer player) {
        if (!(player.containerMenu instanceof WorkstationMenu menu) || !menu.pos().equals(pos) || !menu.stillValid(player)) return 0;
        WorkstationBlockEntity station = WorkstationBlock.core(player.level(), pos);
        if (station == null) return 0;
        station.selectRecipe(recipe);
        return recipe.equals(station.recipeId()) ? station.fill(player, crafts) : 0;
    }
}
```

- [ ] **Step 5: Register the packet** in `BastionNetwork.register()`, after `HubAction`:

```java
        CHANNEL.messageBuilder(WorkstationFill.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(WorkstationFill::encode).decoder(WorkstationFill::decode).consumerMainThread(WorkstationFill::handle).add();
```

  Then change `private static final String PROTOCOL = "10";` to `"11"`. A new packet means an older client cannot join.

- [ ] **Step 6: Compile.** Use the Compile command with `t2`.
  Expected: `exit=0`.

- [ ] **Step 7: Run the GameTests and check they pass.** Use the GameTests command with `t2`.
  Expected: `All N required tests passed` (N = previous count + 5).

---

### Task 3: Block info for Jade (`BlockInfo`) and the hub's connected count

**Files:**
- Create: `src/main/java/dev/bastion/compat/BlockInfo.java`
- Modify: `src/main/java/dev/bastion/hub/HubInterfaceBlockEntity.java:141-161`
- Modify: `src/main/resources/assets/bastion/lang/en_us.json`, `id_id.json` (append before the closing `}`)
- Test: `src/main/java/dev/bastion/gametest/CompatTests.java`

**Interfaces:**
- Consumes: `WeaponStatRows.fmt(float)` (Task 1); `TurretBaseBlock.core(BlockGetter, BlockPos)`, `WorkstationBlock.core(BlockGetter, BlockPos)`, `ControlHubStructure.partCenter(BlockPos, BlockState)`, `ControlHubStructure.hubAt(Level, BlockPos)`, `HubCasingBlock.X`, `FeedHubBlock.Part.ALONE`, `SatelliteUplinkBlock.PART`.
- Produces:
  - `BlockInfo.TAG = "BastionInfo"`.
  - `BlockInfo.write(@Nullable BlockEntity) -> CompoundTag`.
  - `BlockInfo.lines(CompoundTag, boolean details) -> List<Component>`.
  - `BlockInfo.corePos(Level, BlockPos, BlockState) -> @Nullable BlockPos`.
  - `HubInterfaceBlockEntity.connectedCount() -> int`.
  - Used by Task 4's Jade adapters.

- [ ] **Step 1: Write the failing tests.** Add to `CompatTests` the imports `dev.bastion.compat.BlockInfo`, `dev.bastion.hub.*`, `dev.bastion.registry.BastionItems`, `dev.bastion.turret.*`, `net.minecraft.nbt.CompoundTag`, `net.minecraft.server.level.ServerLevel`, `net.minecraft.world.level.block.Blocks`, `net.minecraftforge.items.ItemHandlerHelper`, `java.util.ArrayList`, `java.util.UUID`, then:

```java
    private static List<String> lines(net.minecraft.world.level.block.entity.BlockEntity entity, boolean details) {
        return BlockInfo.lines(BlockInfo.write(entity), details).stream().map(CompatTests::text).toList();
    }

    /** Turret: weapon and tier, HP, state; Shift adds modules and heat (no owner line when the name is unknown, no target). */
    @GameTest(template = ARENA, batch = "turretInfoLines")
    public static void turretInfoLines(GameTestHelper helper) {
        helper.setBlock(new BlockPos(3, 2, 3), BastionBlocks.TURRET_BASE.get());
        TurretBaseBlockEntity turret = (TurretBaseBlockEntity) helper.getBlockEntity(new BlockPos(3, 2, 3));
        turret.setOwner(UUID.randomUUID()); // nobody online or cached by that id
        turret.inventory().setStackInSlot(TurretInventory.WEAPON, new ItemStack(BastionItems.GUN_TURRET.get()));
        turret.inventory().setStackInSlot(TurretInventory.MODIFIER_START, new ItemStack(BastionItems.RANGE_MODULE.get()));
        List<String> compact = lines(turret, false);
        String hp = WeaponStatRows.fmt(turret.health()), max = WeaponStatRows.fmt(turret.maxHealth());
        helper.assertTrue(compact.equals(List.of("jade.bastion.turret.weapon[item.bastion.gun_turret,gui.bastion.turret.tier[1]]",
                "gui.bastion.turret.hp[" + hp + "," + max + "]",
                "jade.bastion.state[gui.bastion.state." + turret.state().name().toLowerCase(java.util.Locale.ROOT) + "]")), "compact " + compact);
        List<String> details = lines(turret, true);
        helper.assertTrue(details.subList(0, 3).equals(compact), "Shift should only add lines: " + details);
        helper.assertTrue(details.subList(3, details.size()).equals(List.of("jade.bastion.modules[item.bastion.range_module]",
                "gui.bastion.turret.heat[0," + WeaponStatRows.fmt(StatSheet.of(turret.inventory().weaponData(), TurretTier.T1).maxHeat()) + "]")),
                "details " + details);
        helper.succeed();
    }

    /** An empty base says so; no heat line without a weapon. */
    @GameTest(template = ARENA, batch = "unarmedTurretSaysNoWeapon")
    public static void unarmedTurretSaysNoWeapon(GameTestHelper helper) {
        helper.setBlock(new BlockPos(3, 2, 3), BastionBlocks.TURRET_BASE.get());
        List<String> details = lines(helper.getBlockEntity(new BlockPos(3, 2, 3)), true);
        helper.assertTrue(details.get(0).equals("jade.bastion.turret.weapon[gui.bastion.info.no_weapon,gui.bastion.turret.tier[1]]"), "first line " + details);
        helper.assertTrue(details.stream().noneMatch(line -> line.startsWith("gui.bastion.turret.heat")), "heat line without a weapon: " + details);
        helper.succeed();
    }

    /** Workstation: pick a recipe, then what it makes, then the progress while it works. */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = "workstationInfoLines")
    public static void workstationInfoLines(GameTestHelper helper) {
        WorkstationBlockEntity station = station(helper, BastionBlocks.AMMO_WORKSTATION);
        helper.assertTrue(lines(station, false).equals(List.of("gui.bastion.workstation.status.pick")), "no recipe: " + lines(station, false));
        station.selectRecipe(KINETIC);
        helper.assertTrue(lines(station, false).equals(List.of("jade.bastion.workstation.recipe[item.bastion.kinetic_rounds]")), "picked: " + lines(station, false));
        helper.setBlock(STATION.east(), BastionBlocks.CREATIVE_POWER_SOURCE.get());
        station.items().setStackInSlot(0, new ItemStack(Items.COPPER_INGOT));
        station.items().setStackInSlot(1, new ItemStack(Items.GUNPOWDER));
        station.items().setStackInSlot(2, new ItemStack(Items.IRON_NUGGET));
        helper.succeedWhen(() -> {
            helper.assertTrue(station.progress() > 0, "not working yet");
            List<String> working = lines(station, false);
            helper.assertTrue(working.size() == 2 && working.get(1).equals("gui.bastion.workstation.status.working[" + station.progress() * 100 / 40 + "]"),
                    "working: " + working);
        });
    }

    /** Feed Hub: how many turrets it feeds. */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "feedHubInfoLines")
    public static void feedHubInfoLines(GameTestHelper helper) {
        BlockPos hubPos = new BlockPos(5, 3, 3);
        helper.setBlock(hubPos, BastionBlocks.FEED_HUB.get());
        helper.setBlock(hubPos.east(), BastionBlocks.TURRET_BASE.get().defaultBlockState().setValue(TurretBaseBlock.FACING, Direction.EAST));
        ((TurretBaseBlockEntity) helper.getBlockEntity(hubPos.east())).inventory().setStackInSlot(TurretInventory.WEAPON, new ItemStack(BastionItems.GUN_TURRET.get()));
        helper.runAtTickTime(5, () -> {
            FeedHubBlockEntity hub = (FeedHubBlockEntity) helper.getBlockEntity(hubPos);
            helper.assertTrue(hub.feeds().size() == 1, "the hub should feed the one turret on it, feeds " + hub.feeds().size());
            helper.assertTrue(lines(hub, false).equals(List.of("jade.bastion.feed_hub.feeds[1]")), "lines " + lines(hub, false));
            helper.succeed();
        });
    }

    /** Control Hub: status, then satellite tier, reach and connected turrets, then FE; an unformed Interface only its status. */
    // Yard and a T2 satellite (3x3 chunks), as in ControlHubTests.hubListsOnlyConnectedTurrets: the turrets are surely in reach.
    @GameTest(template = YARD, timeoutTicks = 80, batch = "controlHubInfoLines")
    public static void controlHubInfoLines(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        HubInterfaceBlockEntity hub = ControlHubTests.hub(helper, new BlockPos(15, 2, 14), 2);
        hub.setEnergy(100_000);
        TurretBaseBlockEntity first = ControlHubTests.base(level, hub.center().offset(-4, 0, 4), null);
        ControlHubTests.connect(first, hub);
        helper.runAtTickTime(5, () -> {
            List<String> online = lines(hub, false);
            helper.assertTrue(online.equals(List.of("jade.bastion.control_hub.status[jade.bastion.control_hub.status.online]",
                    "gui.bastion.control_hub.summary[2," + hub.side() + ",1]",
                    "gui.bastion.control_hub.fe[" + hub.energy() + "," + hub.capacity() + "," + hub.upkeep() + "]")), "online " + online);
            // A second connected turret shows up within a second, not on every Jade request.
            TurretBaseBlockEntity second = ControlHubTests.base(level, hub.center().offset(4, 0, 4), null);
            ControlHubTests.connect(second, hub);
            helper.assertTrue(hub.connectedCount() == 1, "counted again within the same second");
        });
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(hub.connectedCount() == 2, "count not refreshed after a second: " + hub.connectedCount());
            helper.setBlock(new BlockPos(0, 2, 0), BastionBlocks.HUB_INTERFACE.get());
            helper.assertTrue(lines(helper.getBlockEntity(new BlockPos(0, 2, 0)), true)
                    .equals(List.of("jade.bastion.control_hub.status[jade.bastion.control_hub.status.unformed]")), "unformed Interface");
            helper.succeed();
        });
    }

    /** Jade shows a multiblock's core: every block of a Large Turret Base leads to it, a 1x1 base to itself, stone nowhere. */
    @GameTest(template = ARENA, batch = "largeBasePartsLeadToTheCore")
    public static void largeBasePartsLeadToTheCore(GameTestHelper helper) {
        BlockPos min = helper.absolutePos(new BlockPos(2, 2, 2));
        LargeTurretBaseBlock.placeAt(helper.getLevel(), min, BastionBlocks.LARGE_TURRET_BASE.get());
        BlockPos core = TurretBaseBlock.core(helper.getLevel(), min).getBlockPos();
        for (BlockPos p : List.of(min, min.east(), min.south(), min.south().east())) {
            helper.assertTrue(core.equals(BlockInfo.corePos(helper.getLevel(), p, helper.getLevel().getBlockState(p))), "part " + p + " does not lead to " + core);
        }
        BlockPos single = helper.absolutePos(new BlockPos(6, 2, 2));
        helper.getLevel().setBlock(single, BastionBlocks.TURRET_BASE.get().defaultBlockState(), 3);
        helper.assertTrue(single.equals(BlockInfo.corePos(helper.getLevel(), single, helper.getLevel().getBlockState(single))), "a 1x1 base is its own core");
        BlockPos stone = helper.absolutePos(new BlockPos(6, 2, 5));
        helper.getLevel().setBlock(stone, Blocks.STONE.defaultBlockState(), 3);
        helper.assertTrue(BlockInfo.corePos(helper.getLevel(), stone, helper.getLevel().getBlockState(stone)) == null, "stone has a core");
        helper.succeed();
    }

    /** Every block of a Part Assembler leads to its core. */
    @GameTest(template = ARENA, batch = "workstationPartsLeadToTheCore")
    public static void workstationPartsLeadToTheCore(GameTestHelper helper) {
        WorkstationBlockEntity station = station(helper, BastionBlocks.PART_ASSEMBLER);
        for (BlockPos p : BastionBlocks.PART_ASSEMBLER.get().footprint(station.getBlockPos(), Direction.NORTH)) {
            helper.assertTrue(station.getBlockPos().equals(BlockInfo.corePos(helper.getLevel(), p, helper.getLevel().getBlockState(p))), "block " + p);
        }
        helper.succeed();
    }

    /** Casings, the Power Port and both satellite halves of a formed hub lead to its Interface; a loose casing to nothing. */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "hubPartsLeadToTheInterface")
    public static void hubPartsLeadToTheInterface(GameTestHelper helper) {
        HubInterfaceBlockEntity hub = ControlHubTests.hub(helper, new BlockPos(6, 2, 3), 1);
        BlockPos loose = helper.absolutePos(new BlockPos(1, 2, 1));
        helper.getLevel().setBlock(loose, BastionBlocks.HUB_CASING.get().defaultBlockState(), 3);
        helper.succeedWhen(() -> {
            helper.assertTrue(hub.formed(), "not formed");
            List<BlockPos> parts = new ArrayList<>(ControlHubStructure.slots(hub.getBlockPos(), Direction.NORTH));
            parts.add(hub.center().above(2));
            parts.add(hub.center().above(3));
            parts.add(hub.getBlockPos());
            for (BlockPos p : parts) {
                helper.assertTrue(hub.getBlockPos().equals(BlockInfo.corePos(helper.getLevel(), p, helper.getLevel().getBlockState(p))), "part " + p);
            }
            helper.assertTrue(BlockInfo.corePos(helper.getLevel(), loose, helper.getLevel().getBlockState(loose)) == null, "a loose casing has a core");
        });
    }
```

  Also:
  - Add the imports `dev.bastion.weapon.StatSheet` (already present from Task 1) and `dev.bastion.turret.LargeTurretBaseBlock`.
  - Change the constants line to `private static final String ARENA = "arena", EMPTY = "empty", YARD = "yard";`.
  - `ControlHubTests.base`, `connect` and `hub` are package-private static.

- [ ] **Step 2: Compile and check it fails.** Use the Compile command with `t3`.
  Expected: `exit=1`, `cannot find symbol ... BlockInfo` and `connectedCount`.

- [ ] **Step 3: Add `connectedCount` to `HubInterfaceBlockEntity`.** Replace `visibleTurrets` (lines 141-161) with the version below, which shares one scan:

```java
    /**
     * Every turret in reach connected to this hub that the player may open, in loaded chunks only, nearest the satellite
     * first; none unless online.
     */
    public List<TurretBaseBlockEntity> visibleTurrets(Player player) {
        if (!online() || level == null) return List.of();
        List<TurretBaseBlockEntity> found = connectedTurrets(turret -> turret.canConfigure(player));
        BlockPos center = center();
        found.sort(Comparator.comparingDouble(turret -> turret.getBlockPos().distSqr(center)));
        return found;
    }

    /** Turrets connected to this hub in its reach (loaded chunks), counted at most once a second: Jade asks several times a second. */
    public int connectedCount() {
        if (level == null || !formed()) return 0;
        long now = level.getGameTime();
        if (countedAt < 0 || now - countedAt >= 20) {
            connectedCount = connectedTurrets(turret -> true).size();
            countedAt = now;
        }
        return connectedCount;
    }

    private List<TurretBaseBlockEntity> connectedTurrets(Predicate<TurretBaseBlockEntity> filter) {
        BlockPos center = center();
        int r = side() / 2, cx = center.getX() >> 4, cz = center.getZ() >> 4;
        List<TurretBaseBlockEntity> found = new ArrayList<>();
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(x, z);
                if (chunk == null) continue;
                for (BlockEntity entity : chunk.getBlockEntities().values()) {
                    if (entity instanceof TurretBaseBlockEntity turret && !turret.isRemoved() && connected(turret) && filter.test(turret)) found.add(turret);
                }
            }
        }
        return found;
    }
```

  Then:
  1. Add the fields `private int connectedCount; private long countedAt = -1;` next to `energy` and `tier`.
  2. Add `import java.util.function.Predicate;`.

- [ ] **Step 4: Create `BlockInfo.java`:**

```java
package dev.bastion.compat;

import dev.bastion.hub.ControlHubStructure;
import dev.bastion.hub.HubCasingBlock;
import dev.bastion.hub.HubInterfaceBlock;
import dev.bastion.hub.HubInterfaceBlockEntity;
import dev.bastion.hub.SatelliteUplinkBlock;
import dev.bastion.turret.FeedHubBlock;
import dev.bastion.turret.FeedHubBlockEntity;
import dev.bastion.turret.TurretBaseBlock;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.TurretInventory;
import dev.bastion.weapon.StatSheet;
import dev.bastion.weapon.WeaponData;
import dev.bastion.workstation.WorkstationBlock;
import dev.bastion.workstation.WorkstationBlockEntity;
import dev.bastion.workstation.WorkstationRecipe;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static dev.bastion.weapon.WeaponStatRows.fmt;

/**
 * What block-info mods show for a Bastion block (PLAN "Compat JEI & Jade"): {@link #write} on the server gathers it into a
 * tag, {@link #lines} on the client turns the tag into lines (Shift adds the details). Jade calls these through
 * dev.bastion.compat.jade; The One Probe can later use the same two.
 */
public final class BlockInfo {
    /** Key of the tag in Jade's server data. */
    public static final String TAG = "BastionInfo";

    private BlockInfo() {
    }

    public static CompoundTag write(@Nullable BlockEntity entity) {
        CompoundTag tag = new CompoundTag();
        if (entity instanceof TurretBaseBlockEntity turret) turret(tag, turret);
        else if (entity instanceof WorkstationBlockEntity station) workstation(tag, station);
        else if (entity instanceof FeedHubBlockEntity hub) {
            tag.putString("Kind", "feed_hub");
            tag.putInt("Feeds", hub.feeds().size());
        } else if (entity instanceof HubInterfaceBlockEntity hub) controlHub(tag, hub);
        return tag;
    }

    private static void turret(CompoundTag tag, TurretBaseBlockEntity turret) {
        tag.putString("Kind", "turret");
        ItemStack weapon = turret.inventory().getStackInSlot(TurretInventory.WEAPON);
        if (!weapon.isEmpty()) tag.putString("Weapon", id(weapon.getItem()));
        tag.putInt("Tier", turret.tier().ordinal() + 1);
        tag.putFloat("Hp", turret.health());
        tag.putFloat("MaxHp", turret.maxHealth());
        tag.putString("State", turret.state().name().toLowerCase(Locale.ROOT));
        String owner = turret.owner() == null ? null : ownerName(turret.getLevel(), turret.owner());
        if (owner != null) tag.putString("Owner", owner);
        ListTag modules = new ListTag();
        for (int i = 0; i < turret.tier().modifierSlots; i++) {
            ItemStack module = turret.inventory().getStackInSlot(TurretInventory.MODIFIER_START + i);
            if (!module.isEmpty()) modules.add(StringTag.valueOf(id(module.getItem())));
        }
        tag.put("Modules", modules);
        WeaponData data = turret.inventory().weaponData();
        float maxHeat = data == null ? 0 : StatSheet.of(data, turret.tier(), turret.inventory().modifierEffect()).maxHeat();
        if (maxHeat > 0) {
            tag.putFloat("Heat", turret.heat());
            tag.putFloat("MaxHeat", maxHeat);
        }
        LivingEntity target = turret.target();
        if (target != null && target.isAlive()) tag.putString("Target", Component.Serializer.toJson(target.getDisplayName()));
    }

    /** The owner's name if they are online or in the server's profile cache (none on GameTest servers), else null. */
    @Nullable
    private static String ownerName(@Nullable Level level, UUID id) {
        if (level == null) return null;
        Player online = level.getPlayerByUUID(id);
        if (online != null) return online.getGameProfile().getName();
        MinecraftServer server = level.getServer();
        GameProfileCache cache = server == null ? null : server.getProfileCache();
        return cache == null ? null : cache.get(id).map(GameProfile::getName).orElse(null);
    }

    private static void workstation(CompoundTag tag, WorkstationBlockEntity station) {
        tag.putString("Kind", "workstation");
        WorkstationRecipe recipe = station.recipe();
        if (recipe == null) return;
        tag.putString("Recipe", id(recipe.result().getItem()));
        tag.putInt("Progress", station.progress());
        tag.putInt("Time", recipe.time());
    }

    private static void controlHub(CompoundTag tag, HubInterfaceBlockEntity hub) {
        tag.putString("Kind", "control_hub");
        tag.putString("Status", hub.status().name().toLowerCase(Locale.ROOT));
        if (!hub.formed()) return;
        tag.putInt("Tier", hub.tier());
        tag.putInt("Side", hub.side());
        tag.putInt("Turrets", hub.connectedCount());
        tag.putInt("Energy", hub.energy());
        tag.putInt("Capacity", hub.capacity());
        tag.putInt("Upkeep", hub.upkeep());
    }

    public static List<Component> lines(CompoundTag tag, boolean details) {
        List<Component> lines = new ArrayList<>();
        switch (tag.getString("Kind")) {
            case "turret" -> {
                Component weapon = tag.contains("Weapon") ? item(tag.getString("Weapon")).getDescription()
                        : Component.translatable("gui.bastion.info.no_weapon");
                lines.add(Component.translatable("jade.bastion.turret.weapon", weapon, Component.translatable("gui.bastion.turret.tier", tag.getInt("Tier"))));
                lines.add(Component.translatable("gui.bastion.turret.hp", fmt(tag.getFloat("Hp")), fmt(tag.getFloat("MaxHp"))));
                lines.add(Component.translatable("jade.bastion.state", Component.translatable("gui.bastion.state." + tag.getString("State"))));
                if (!details) break;
                if (tag.contains("Owner")) lines.add(Component.translatable("jade.bastion.owner", tag.getString("Owner")));
                ListTag modules = tag.getList("Modules", Tag.TAG_STRING);
                if (!modules.isEmpty()) {
                    MutableComponent names = Component.empty();
                    for (int i = 0; i < modules.size(); i++) {
                        if (i > 0) names.append(", ");
                        names.append(item(modules.getString(i)).getDescription());
                    }
                    lines.add(Component.translatable("jade.bastion.modules", names));
                }
                if (tag.contains("MaxHeat")) lines.add(Component.translatable("gui.bastion.turret.heat", fmt(tag.getFloat("Heat")), fmt(tag.getFloat("MaxHeat"))));
                Component target = tag.contains("Target") ? Component.Serializer.fromJson(tag.getString("Target")) : null;
                if (target != null) lines.add(Component.translatable("jade.bastion.target", target));
            }
            case "workstation" -> {
                if (!tag.contains("Recipe")) {
                    lines.add(Component.translatable("gui.bastion.workstation.status.pick"));
                    break;
                }
                lines.add(Component.translatable("jade.bastion.workstation.recipe", item(tag.getString("Recipe")).getDescription()));
                int progress = tag.getInt("Progress"), time = Math.max(1, tag.getInt("Time"));
                if (progress > 0) lines.add(Component.translatable("gui.bastion.workstation.status.working", progress * 100 / time));
            }
            case "feed_hub" -> lines.add(Component.translatable("jade.bastion.feed_hub.feeds", tag.getInt("Feeds")));
            case "control_hub" -> {
                lines.add(Component.translatable("jade.bastion.control_hub.status", Component.translatable("jade.bastion.control_hub.status." + tag.getString("Status"))));
                if (!tag.contains("Tier")) break;
                lines.add(Component.translatable("gui.bastion.control_hub.summary", tag.getInt("Tier"), tag.getInt("Side"), tag.getInt("Turrets")));
                lines.add(Component.translatable("gui.bastion.control_hub.fe", tag.getInt("Energy"), tag.getInt("Capacity"), tag.getInt("Upkeep")));
            }
            default -> {
            }
        }
        return lines;
    }

    /**
     * The core of the Bastion multiblock {@code pos} belongs to (a core gives its own position): the turret of a base, the
     * station of a workstation block, the Interface of a formed Control Hub's casing, Power Port or satellite half. Null for
     * anything else, a loose hub part included. Works on both sides (Jade calls it on the client).
     */
    @Nullable
    public static BlockPos corePos(Level level, BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        if (block instanceof TurretBaseBlock) {
            TurretBaseBlockEntity core = TurretBaseBlock.core(level, pos);
            return core == null ? null : core.getBlockPos();
        }
        if (block instanceof WorkstationBlock) {
            WorkstationBlockEntity core = WorkstationBlock.core(level, pos);
            return core == null ? null : core.getBlockPos();
        }
        if (block instanceof HubInterfaceBlock) return pos;
        HubInterfaceBlockEntity hub = null;
        if (block instanceof HubCasingBlock && state.getValue(HubCasingBlock.X) != FeedHubBlock.Part.ALONE) { // casings and Power Ports
            hub = ControlHubStructure.hubAt(level, ControlHubStructure.partCenter(pos, state));
        } else if (block instanceof SatelliteUplinkBlock) { // stands on the cube's top middle block: center + 2 (bottom half), + 3 (top)
            hub = ControlHubStructure.hubAt(level, pos.below(2 + state.getValue(SatelliteUplinkBlock.PART)));
        }
        return hub == null ? null : hub.getBlockPos();
    }

    private static String id(Item item) {
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
        return key == null ? "minecraft:air" : key.toString();
    }

    private static Item item(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        Item item = key == null ? null : ForgeRegistries.ITEMS.getValue(key);
        return item == null ? Items.AIR : item;
    }
}
```

Check `GameProfileCache.get(UUID)` in 1.20.1 returns `Optional<GameProfile>`. If the compiler says otherwise, adapt it and note it.

- [ ] **Step 5: Add the lang keys.** Append to `en_us.json` (comma after the previous last entry):

```json
  "jade.bastion.turret.weapon": "%s · %s",
  "jade.bastion.state": "State: %s",
  "jade.bastion.owner": "Owner: %s",
  "jade.bastion.modules": "Modules: %s",
  "jade.bastion.target": "Target: %s",
  "jade.bastion.workstation.recipe": "Making: %s",
  "jade.bastion.feed_hub.feeds": "Feeds %s turrets",
  "jade.bastion.control_hub.status": "Hub: %s",
  "jade.bastion.control_hub.status.unformed": "Not formed",
  "jade.bastion.control_hub.status.online": "Online",
  "jade.bastion.control_hub.status.offline": "Offline: out of FE"
```

and to `id_id.json`:

```json
  "jade.bastion.turret.weapon": "%s · %s",
  "jade.bastion.state": "Status: %s",
  "jade.bastion.owner": "Pemilik: %s",
  "jade.bastion.modules": "Modul: %s",
  "jade.bastion.target": "Target: %s",
  "jade.bastion.workstation.recipe": "Membuat: %s",
  "jade.bastion.feed_hub.feeds": "Menyuplai %s turret",
  "jade.bastion.control_hub.status": "Hub: %s",
  "jade.bastion.control_hub.status.unformed": "Belum terbentuk",
  "jade.bastion.control_hub.status.online": "Online",
  "jade.bastion.control_hub.status.offline": "Offline: FE habis"
```

  Check that both files still parse: `python3 -c "import json; [json.load(open(f'src/main/resources/assets/bastion/lang/{l}.json', encoding='utf-8')) for l in ('en_us','id_id')]; print('ok')"`.

- [ ] **Step 6: Compile.** Use the Compile command with `t3`.
  Expected: `exit=0`.

- [ ] **Step 7: Run the GameTests and check they pass.** Use the GameTests command with `t3`.
  Expected: `All N required tests passed` (N = previous count + 8). The `ControlHubTests` hub tests must still pass, since `visibleTurrets` was refactored.

---

### Task 4: Dependencies, `mods.toml`, Jade plugin

**Files:**
- Modify: `build.gradle:136-162` (dependencies block)
- Modify: `src/main/resources/META-INF/mods.toml` (after the `create` entry)
- Create: `src/main/java/dev/bastion/compat/jade/BastionJadePlugin.java`
- Create: `src/main/java/dev/bastion/compat/jade/BastionJadeProvider.java`
- Modify: `en_us.json`, `id_id.json` (Jade config names)

**Interfaces:**
- Consumes: `BlockInfo.TAG`, `BlockInfo.write`, `BlockInfo.lines`, `BlockInfo.corePos` (Task 3).
- Produces: JEI and Jade on the compile classpath (needed by Task 5) and in `runClient`.

- [ ] **Step 1: Add the dependencies, then fetch them once online.** In `build.gradle`, after the Create `runtimeOnly` lines (~line 147), add:

```groovy
    // Optional JEI and Jade compat (dev.bastion.client.compat.jei, dev.bastion.compat.jade): compiled against, loaded only
    // when the player has them; in runClient, dropped with -PnoDevMods. Modrinth version ids: JEI 15.20.0.134 (the 15.20
    // line most 1.20.1 packs use), Jade 11.13.3+forge.
    compileOnly fg.deobf("maven.modrinth:jei:ujE6Mlsq")
    compileOnly fg.deobf("maven.modrinth:jade:xJQHCmWJ")
    clientDevMods fg.deobf("maven.modrinth:jei:ujE6Mlsq")
    clientDevMods fg.deobf("maven.modrinth:jade:xJQHCmWJ")
```

  Then run **once without** `--offline`, in the background with a 10-minute limit: `./gradlew compileJava > build/fetch-t4.log 2>&1; echo "exit=$?"`.
  - Expected: `exit=0`, and `ls ~/.gradle/caches/modules-2/files-2.1/maven.modrinth/` lists `jei` and `jade`.
  - If it hangs in `DownloadMCMeta` (HANDOFF §"Run with --offline"), stop it and run it again.

- [ ] **Step 2: Declare the optional dependencies** in `mods.toml`, after the `create` block:

```toml
# Optional: recipe pages and the + button in JEI (dev.bastion.client.compat.jei), block info in Jade (dev.bastion.compat.jade).
[[dependencies.${mod_id}]]
    modId="jei"
    mandatory=false
    versionRange="[15.20,)"
    ordering="NONE"
    side="CLIENT"

[[dependencies.${mod_id}]]
    modId="jade"
    mandatory=false
    versionRange="[11.13,)"
    ordering="NONE"
    side="BOTH"
```

- [ ] **Step 3: Create `BastionJadeProvider.java`:**

```java
package dev.bastion.compat.jade;

import dev.bastion.Bastion;
import dev.bastion.compat.BlockInfo;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/** One Jade provider per kind of Bastion block, each with its own switch in Jade's config; BlockInfo decides the lines. */
public enum BastionJadeProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    TURRET("turret"), WORKSTATION("workstation"), FEED_HUB("feed_hub"), CONTROL_HUB("control_hub");

    private final ResourceLocation uid;

    BastionJadeProvider(String name) {
        uid = Bastion.id(name);
    }

    @Override
    public ResourceLocation getUid() {
        return uid;
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        data.put(BlockInfo.TAG, BlockInfo.write(accessor.getBlockEntity()));
    }

    /** A server without Jade sends no data: no Bastion lines then, only Jade's own. */
    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        BlockInfo.lines(accessor.getServerData().getCompound(BlockInfo.TAG), accessor.showDetails()).forEach(tooltip::add);
    }
}
```

- [ ] **Step 4: Create `BastionJadePlugin.java`:**

```java
package dev.bastion.compat.jade;

import dev.bastion.Bastion;
import dev.bastion.compat.BlockInfo;
import dev.bastion.hub.HubInterfaceBlock;
import dev.bastion.hub.HubInterfaceBlockEntity;
import dev.bastion.turret.FeedHubBlock;
import dev.bastion.turret.FeedHubBlockEntity;
import dev.bastion.turret.TurretBaseBlock;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.workstation.WorkstationBlock;
import dev.bastion.workstation.WorkstationBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import snownee.jade.api.Accessor;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/** Jade block info for Bastion (PLAN "Compat JEI & Jade"). Jade finds this class by its annotation; nothing else refers to it. */
@WailaPlugin(Bastion.MOD_ID)
public class BastionJadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(BastionJadeProvider.TURRET, TurretBaseBlockEntity.class);
        registration.registerBlockDataProvider(BastionJadeProvider.WORKSTATION, WorkstationBlockEntity.class);
        registration.registerBlockDataProvider(BastionJadeProvider.FEED_HUB, FeedHubBlockEntity.class);
        registration.registerBlockDataProvider(BastionJadeProvider.CONTROL_HUB, HubInterfaceBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(BastionJadeProvider.TURRET, TurretBaseBlock.class);
        registration.registerBlockComponent(BastionJadeProvider.WORKSTATION, WorkstationBlock.class);
        registration.registerBlockComponent(BastionJadeProvider.FEED_HUB, FeedHubBlock.class);
        registration.registerBlockComponent(BastionJadeProvider.CONTROL_HUB, HubInterfaceBlock.class);
        // Looking at a part of a multiblock shows its core (the turret, station or hub Interface), with the core's FE and slots.
        registration.addRayTraceCallback((hit, accessor, original) -> core(registration, accessor));
    }

    private static Accessor<?> core(IWailaClientRegistration registration, @Nullable Accessor<?> accessor) {
        if (!(accessor instanceof BlockAccessor block)) return accessor;
        Level level = block.getLevel();
        BlockPos core = BlockInfo.corePos(level, block.getPosition(), block.getBlockState());
        if (core == null || core.equals(block.getPosition())) return accessor;
        return registration.blockAccessor().from(block).blockState(level.getBlockState(core)).blockEntity(level.getBlockEntity(core))
                .hit(block.getHitResult().withPosition(core)).build();
    }
}
```

- [ ] **Step 5: Add the Jade config names.**
  - Append to `en_us.json`: `"config.jade.plugin_bastion.turret": "Turret"`, `"config.jade.plugin_bastion.workstation": "Workstation"`, `"config.jade.plugin_bastion.feed_hub": "Feed Hub"`, `"config.jade.plugin_bastion.control_hub": "Turret Control Hub"`.
  - Append to `id_id.json`: the same keys with `"Turret"`, `"Workstation"`, `"Feed Hub"`, `"Turret Control Hub"`.
  - Re-run the JSON parse check from Task 3 Step 5.

- [ ] **Step 6: Check that nothing outside `client` imports client code** in the new packages: `grep -rn "net.minecraft.client" src/main/java/dev/bastion/compat`.
  Expected: no output.

- [ ] **Step 7: Build.** Use the Build command with `t4`.
  Expected: `exit=0`. A jar in `build/libs/` without JEI or Jade classes inside: `unzip -l build/libs/bastion-1.20.1-0.2.1.jar | grep -c "snownee\|mezz"` prints `0`.

- [ ] **Step 8: Run the GameTests** (neither mod is on that classpath).
  Use the GameTests command with `t4`.
  Expected: the same `All N required tests passed` as in Task 3, and no `ClassNotFoundException` or `NoClassDefFoundError` in the log: `grep -c "NoClassDefFoundError\|ClassNotFoundException" build/gametest-t4.log` prints `0`.

---

### Task 5: JEI plugin

**Files:**
- Create: `src/main/java/dev/bastion/client/compat/jei/BastionJeiPlugin.java`
- Create: `src/main/java/dev/bastion/client/compat/jei/StationCategory.java`
- Create: `src/main/java/dev/bastion/client/compat/jei/WeaponCategory.java`
- Create: `src/main/java/dev/bastion/client/compat/jei/StationTransfer.java`
- Modify: `en_us.json`, `id_id.json`

**Interfaces:**
- Consumes:
  - From Task 1: `WeaponStatRows.left/right/fmt` and `TurretBaseBlockEntity.maxHealth(TurretTier, boolean, ModifierEffect)`.
  - From Task 2: `WorkstationFill(BlockPos, ResourceLocation, int)` and `WorkstationFill.MAX_CRAFTS`.
  - Existing: `WorkstationBlockEntity.recipes(Level, WorkstationType)`, `count(Counted, List<ItemStack>)`, `INPUTS`; `WorkstationMenu.type()`, `pos()`; `BastionMenus.WORKSTATION`; `WeaponModuleItem.data()`, `large()`, `modelName()`.
- Produces: JEI pages; nothing in Bastion calls them.

JEI has no GameTest. This task is verified by compile and build. The user checks it in game (Task 6 checklist).
- Use only API calls present in JEI 15.20, so `[15.20,)` holds: `addSlot(role, x, y)`, `getBackground()` plus `getWidth()`/`getHeight()`, `createDrawableIngredient`, `getSlotDrawable`, `getRecipeArrow`, `createBlankDrawable`.
- The plan checked all of these against the 15.20.0.134 jar.

- [ ] **Step 1: Create `StationCategory.java`:**

```java
package dev.bastion.client.compat.jei;

import dev.bastion.weapon.WeaponStatRows;
import dev.bastion.workstation.WorkstationRecipe;
import dev.bastion.workstation.WorkstationType;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.Arrays;

/** One workstation's recipes in JEI: the counted ingredients, the result, time and FE (PLAN "Compat JEI & Jade"). */
final class StationCategory implements IRecipeCategory<WorkstationRecipe> {
    // Room for two rows of inputs: a datapack recipe may list more than a station's six (it can't be made, but shows whole).
    static final int WIDTH = 160, HEIGHT = 62;
    private static final int ARROW_X = 113, OUTPUT_X = 140, TEXT = 0xFF404040;

    private final RecipeType<WorkstationRecipe> type;
    private final WorkstationType station;
    private final IDrawable background, icon, slot, arrow;

    StationCategory(IGuiHelper gui, RecipeType<WorkstationRecipe> type, WorkstationType station) {
        this.type = type;
        this.station = station;
        background = gui.createBlankDrawable(WIDTH, HEIGHT);
        icon = gui.createDrawableIngredient(VanillaTypes.ITEM_STACK, BastionJeiPlugin.stationItem(station));
        slot = gui.getSlotDrawable();
        arrow = gui.getRecipeArrow();
    }

    @Override
    public RecipeType<WorkstationRecipe> getRecipeType() {
        return type;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("block.bastion." + station.id());
    }

    @Override
    @SuppressWarnings({"deprecation", "removal"})
    public IDrawable getBackground() { // older JEI 15.20 builds size the page from it
        return background;
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    private static int inputX(int i) {
        return (i % 6) * 18 + 1;
    }

    private static int inputY(int i) {
        return (i / 6) * 18 + 1;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, WorkstationRecipe recipe, IFocusGroup focuses) {
        for (int i = 0; i < recipe.ingredients().size(); i++) {
            WorkstationRecipe.Counted counted = recipe.ingredients().get(i);
            builder.addSlot(RecipeIngredientRole.INPUT, inputX(i), inputY(i))
                    .addItemStacks(Arrays.stream(counted.ingredient().getItems()).map(stack -> stack.copyWithCount(counted.count())).toList());
        }
        builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_X + 1, 1).addItemStack(recipe.result());
    }

    @Override
    public void draw(WorkstationRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        for (int i = 0; i < recipe.ingredients().size(); i++) slot.draw(graphics, inputX(i) - 1, inputY(i) - 1);
        slot.draw(graphics, OUTPUT_X, 0);
        arrow.draw(graphics, ARROW_X, 1);
        Font font = Minecraft.getInstance().font;
        int y = recipe.ingredients().size() > 6 ? 40 : 22;
        graphics.drawString(font, Component.translatable("jei.bastion.station.cost", WeaponStatRows.fmt(recipe.time() / 20f), recipe.energy(),
                recipe.energyPerTick()), 0, y, TEXT, false);
        if (station.instant) graphics.drawString(font, Component.translatable("jei.bastion.station.instant"), 0, y + 11, TEXT, false);
    }
}
```

- [ ] **Step 2: Create `WeaponCategory.java`:**

```java
package dev.bastion.client.compat.jei;

import dev.bastion.config.BastionConfig;
import dev.bastion.modifier.ModifierEffect;
import dev.bastion.registry.BastionItems;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.TurretTier;
import dev.bastion.weapon.StatSheet;
import dev.bastion.weapon.WeaponData;
import dev.bastion.weapon.WeaponModuleItem;
import dev.bastion.weapon.WeaponStatRows;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.List;

/**
 * "Turret Weapons" in JEI: per weapon the base it mounts on, its ammo (so U on an ammo lists the turrets that fire it) and
 * the turret GUI's Info table at T1 without modules, read from the live weapon data whenever the page is drawn.
 */
final class WeaponCategory implements IRecipeCategory<WeaponModuleItem> {
    static final int WIDTH = 176, HEIGHT = 210;
    private static final int COLUMN_W = 84, STATS_Y = 56, LINE = 10, AMMO_X = 89, TEXT = 0xFF202020, MUTED = 0xFF606060;

    private final IDrawable background, icon, slot;

    WeaponCategory(IGuiHelper gui) {
        background = gui.createBlankDrawable(WIDTH, HEIGHT);
        icon = gui.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(BastionItems.GUN_TURRET.get()));
        slot = gui.getSlotDrawable();
    }

    @Override
    public RecipeType<WeaponModuleItem> getRecipeType() {
        return BastionJeiPlugin.WEAPONS;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.bastion.weapons");
    }

    @Override
    @SuppressWarnings({"deprecation", "removal"})
    public IDrawable getBackground() { // older JEI 15.20 builds size the page from it
        return background;
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    /** The weapon's ammo tag, or nothing for weapons that only run on FE (or whose data has not arrived). */
    private static Ingredient ammo(WeaponModuleItem weapon) {
        WeaponData data = weapon.data();
        return data == null ? Ingredient.EMPTY : Ingredient.of(data.ammo());
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, WeaponModuleItem weapon, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, 1, 1).addItemStack(new ItemStack(weapon));
        builder.addSlot(RecipeIngredientRole.INPUT, 1, 23)
                .addItemStack(new ItemStack(weapon.large() ? BastionItems.LARGE_TURRET_BASE.get() : BastionItems.TURRET_BASE.get()));
        Ingredient ammo = ammo(weapon);
        if (!ammo.isEmpty()) builder.addSlot(RecipeIngredientRole.INPUT, AMMO_X, 23).addIngredients(ammo);
    }

    @Override
    public void draw(WeaponModuleItem weapon, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        Font font = Minecraft.getInstance().font;
        slot.draw(graphics, 0, 0);
        slot.draw(graphics, 0, 22);
        graphics.drawString(font, weapon.getDescription(), 22, 5, TEXT, false);
        graphics.drawString(font, Component.translatable("jei.bastion.weapon.mounts"), 22, 27, MUTED, false);
        WeaponData data = weapon.data();
        if (data == null) return; // removed by a datapack, or not synced yet: nothing more to show
        boolean ammo = !ammo(weapon).isEmpty();
        if (ammo) slot.draw(graphics, AMMO_X - 1, 22);
        graphics.drawString(font, Component.translatable(ammo ? "jei.bastion.weapon.ammo" : "jei.bastion.weapon.fe_only"), ammo ? AMMO_X + 20 : AMMO_X, 27, MUTED, false);
        graphics.drawString(font, Component.translatable("jei.bastion.weapon.base_stats"), 0, STATS_Y - 12, MUTED, false);
        StatSheet stats = StatSheet.of(data, TurretTier.T1);
        column(graphics, font, WeaponStatRows.left(data, stats), 0);
        column(graphics, font, WeaponStatRows.right(data, stats, TurretBaseBlockEntity.maxHealth(TurretTier.T1, weapon.large(), ModifierEffect.NONE),
                BastionConfig.regenPerSecond(TurretTier.T1)), WIDTH - COLUMN_W);
        List<FormattedCharSequence> ability = font.split(Component.translatable("tooltip.bastion.weapon." + weapon.modelName() + ".ability"), WIDTH);
        int abilityY = STATS_Y + 7 * LINE + 6;
        for (int i = 0; i < ability.size(); i++) graphics.drawString(font, ability.get(i), 0, abilityY + i * LINE, TEXT, false);
    }

    private static void column(GuiGraphics graphics, Font font, List<WeaponStatRows.Row> rows, int x) {
        for (int i = 0; i < rows.size(); i++) {
            int y = STATS_Y + i * LINE;
            String value = rows.get(i).value();
            int valueWidth = font.width(value);
            graphics.drawString(font, Language.getInstance().getVisualOrder(
                    font.substrByWidth(Component.translatable("gui.bastion.info." + rows.get(i).key()), COLUMN_W - valueWidth - 4)), x, y, MUTED, false);
            graphics.drawString(font, value, x + COLUMN_W - valueWidth, y, TEXT, false);
        }
    }
}
```

- [ ] **Step 3: Create `StationTransfer.java`:**

```java
package dev.bastion.client.compat.jei;

import dev.bastion.network.BastionNetwork;
import dev.bastion.network.WorkstationFill;
import dev.bastion.registry.BastionMenus;
import dev.bastion.workstation.WorkstationBlockEntity;
import dev.bastion.workstation.WorkstationMenu;
import dev.bastion.workstation.WorkstationRecipe;
import dev.bastion.workstation.WorkstationType;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JEI's + button on a station recipe while that station's GUI is open: the server picks the recipe and moves one craft's
 * ingredients (shift: as many as fit) from the inventory into the inputs (WorkstationFill). Missing ingredients turn the
 * button red and mark their slots; another station's GUI hides it.
 */
record StationTransfer(IRecipeTransferHandlerHelper helper, RecipeType<WorkstationRecipe> type, WorkstationType station)
        implements IRecipeTransferHandler<WorkstationMenu, WorkstationRecipe> {
    @Override
    public Class<? extends WorkstationMenu> getContainerClass() {
        return WorkstationMenu.class;
    }

    @Override
    public Optional<MenuType<WorkstationMenu>> getMenuType() {
        return Optional.of(BastionMenus.WORKSTATION.get());
    }

    @Override
    public RecipeType<WorkstationRecipe> getRecipeType() {
        return type;
    }

    @Override
    @Nullable
    public IRecipeTransferError transferRecipe(WorkstationMenu menu, WorkstationRecipe recipe, IRecipeSlotsView slots, Player player,
                                               boolean maxTransfer, boolean doTransfer) {
        if (menu.type() != station) return helper.createInternalError();
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < WorkstationBlockEntity.INPUTS; i++) stacks.add(menu.getSlot(i).getItem());
        stacks.addAll(player.getInventory().items);
        List<IRecipeSlotView> inputs = slots.getSlotViews(RecipeIngredientRole.INPUT);
        List<IRecipeSlotView> missing = new ArrayList<>();
        for (int i = 0; i < recipe.ingredients().size() && i < inputs.size(); i++) {
            WorkstationRecipe.Counted counted = recipe.ingredients().get(i);
            if (WorkstationBlockEntity.count(counted, stacks) < counted.count()) missing.add(inputs.get(i));
        }
        if (!missing.isEmpty()) return helper.createUserErrorForMissingSlots(Component.translatable("jei.bastion.transfer.missing"), missing);
        if (doTransfer) BastionNetwork.CHANNEL.sendToServer(new WorkstationFill(menu.pos(), recipe.id(), maxTransfer ? WorkstationFill.MAX_CRAFTS : 1));
        return null;
    }
}
```

  Check that `BastionMenus` is in `dev.bastion.registry` with `grep -rn "class BastionMenus" src/main/java`, and fix the import if not.

- [ ] **Step 4: Create `BastionJeiPlugin.java`:**

```java
package dev.bastion.client.compat.jei;

import dev.bastion.Bastion;
import dev.bastion.registry.BastionItems;
import dev.bastion.weapon.WeaponModuleItem;
import dev.bastion.workstation.WorkstationBlockEntity;
import dev.bastion.workstation.WorkstationRecipe;
import dev.bastion.workstation.WorkstationType;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

/** JEI pages for Bastion (PLAN "Compat JEI & Jade"). JEI finds this class by its annotation; nothing else refers to it. */
@JeiPlugin
public class BastionJeiPlugin implements IModPlugin {
    static final Map<WorkstationType, RecipeType<WorkstationRecipe>> STATIONS = new EnumMap<>(WorkstationType.class);
    static final RecipeType<WeaponModuleItem> WEAPONS = RecipeType.create(Bastion.MOD_ID, "turret_weapons", WeaponModuleItem.class);

    static {
        for (WorkstationType type : WorkstationType.values()) STATIONS.put(type, RecipeType.create(Bastion.MOD_ID, type.id(), WorkstationRecipe.class));
    }

    static ItemStack stationItem(WorkstationType type) {
        return new ItemStack(ForgeRegistries.ITEMS.getValue(Bastion.id(type.id())));
    }

    @Override
    public ResourceLocation getPluginUid() {
        return Bastion.id("jei");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper gui = registration.getJeiHelpers().getGuiHelper();
        for (WorkstationType type : WorkstationType.values()) registration.addRecipeCategories(new StationCategory(gui, STATIONS.get(type), type));
        registration.addRecipeCategories(new WeaponCategory(gui));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            for (WorkstationType type : WorkstationType.values()) registration.addRecipes(STATIONS.get(type), WorkstationBlockEntity.recipes(level, type));
        }
        // Every weapon, also before WeaponDataSync arrives (it may come after JEI loads): the page reads the data when drawn.
        registration.addRecipes(WEAPONS, BastionItems.REGISTER.getEntries().stream().map(RegistryObject::get)
                .filter(WeaponModuleItem.class::isInstance).map(WeaponModuleItem.class::cast).toList());
        info(registration, "turret_base", BastionItems.TURRET_BASE.get());
        info(registration, "large_turret_base", BastionItems.LARGE_TURRET_BASE.get());
        info(registration, "tier_upgrade_kit", BastionItems.TIER_UPGRADE_KIT_T2.get(), BastionItems.TIER_UPGRADE_KIT_T3.get());
        info(registration, "repair_kit", BastionItems.REPAIR_KIT.get());
        info(registration, "turret_configurator", BastionItems.TURRET_CONFIGURATOR.get());
        info(registration, "hub_connector", BastionItems.HUB_CONNECTOR.get());
        info(registration, "feed_hub", BastionItems.FEED_HUB.get());
        info(registration, "control_hub", BastionItems.HUB_INTERFACE.get(), BastionItems.HUB_CASING.get(), BastionItems.HUB_POWER_PORT.get(),
                BastionItems.SATELLITE_UPLINK_T1.get(), BastionItems.SATELLITE_UPLINK_T2.get(), BastionItems.SATELLITE_UPLINK_T3.get());
        info(registration, "creative_power_source", BastionItems.CREATIVE_POWER_SOURCE.get());
    }

    private static void info(IRecipeRegistration registration, String key, ItemLike... items) {
        registration.addItemStackInfo(Arrays.stream(items).map(ItemStack::new).toList(), Component.translatable("jei.bastion.info." + key));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        for (WorkstationType type : WorkstationType.values()) registration.addRecipeCatalyst(stationItem(type), STATIONS.get(type));
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        for (WorkstationType type : WorkstationType.values()) {
            registration.addRecipeTransferHandler(new StationTransfer(registration.getTransferHelper(), STATIONS.get(type), type), STATIONS.get(type));
        }
    }
}
```

- [ ] **Step 5: Add the JEI lang keys.** Append to `en_us.json`:

```json
  "jei.bastion.station.cost": "%s s · %s FE (%s FE/t)",
  "jei.bastion.station.instant": "Also crafts instantly from its GUI",
  "jei.bastion.weapons": "Turret Weapons",
  "jei.bastion.weapon.mounts": "Mounts on this base",
  "jei.bastion.weapon.ammo": "Ammo",
  "jei.bastion.weapon.fe_only": "Runs on FE",
  "jei.bastion.weapon.base_stats": "At T1, without modules:",
  "jei.bastion.transfer.missing": "Missing ingredients",
  "jei.bastion.info.turret_base": "Place it on a floor, wall or ceiling and right-click it: a weapon module goes in the weapon slot, ammo in the ammo slots, modules in the module slots. Hoppers, belts and item pipes can load its ammo and nothing else. Upgrade kits take it to T2 and T3: more HP, regeneration, turn speed and slots.",
  "jei.bastion.info.large_turret_base": "Takes a 2x2 spot on the floor and carries the heavy weapons: Missile Launcher, Tesla Coil, Railgun, Mortar, Repulsor Dome and Plasma Cannon. More HP than the standard base; otherwise it works the same.",
  "jei.bastion.info.tier_upgrade_kit": "Right-click a turret base one tier lower: T1 for the T2 kit, T2 for the T3 kit. Each tier adds HP, regeneration, turn speed and slots for ammo and modules.",
  "jei.bastion.info.repair_kit": "Right-click a turret base to restore 40% of its HP. The Part Assembler also takes one to restore a Damaged Turret Base at the tier it had.",
  "jei.bastion.info.turret_configurator": "Sneak + right-click a turret to copy its targeting, then right-click other turrets to paste it.",
  "jei.bastion.info.hub_connector": "Right-click a Hub Interface to link it, then put it in a turret's Connector slot: the turret shows on that hub's screen while it is in the hub's reach.",
  "jei.bastion.info.feed_hub": "Mount turret bases on its faces, large bases on top of a 2x2 or wider hub. Ammo and FE piped into it go to the turrets that use them, the emptiest first. Hubs placed together merge, up to 3x3x3, and share their slots and FE.",
  "jei.bastion.info.control_hub": "A Turret Control Hub is a 3x3x3 cube: a Hub Interface in the middle of one side at eye level, facing out, Hub Casings with at least one Hub Power Port, and a Satellite Uplink on the cube's top middle block. Feed FE into a Power Port. Its screen shows every turret holding a Hub Connector linked to it within 1, 3x3 or 7x7 chunks (Satellite Uplink T1, T2, T3).",
  "jei.bastion.info.creative_power_source": "Creative only: gives unlimited FE to the blocks next to it, for testing without an energy mod."
```

and to `id_id.json`:

```json
  "jei.bastion.station.cost": "%s dtk · %s FE (%s FE/t)",
  "jei.bastion.station.instant": "Bisa juga instan dari GUI-nya",
  "jei.bastion.weapons": "Senjata Turret",
  "jei.bastion.weapon.mounts": "Dipasang di base ini",
  "jei.bastion.weapon.ammo": "Ammo",
  "jei.bastion.weapon.fe_only": "Pakai FE",
  "jei.bastion.weapon.base_stats": "Di T1, tanpa modul:",
  "jei.bastion.transfer.missing": "Bahan kurang",
  "jei.bastion.info.turret_base": "Pasang di lantai, dinding atau langit-langit lalu klik kanan: weapon module masuk slot senjata, ammo di slot ammo, modul di slot modul. Hopper, belt dan pipa item bisa mengisi ammo-nya dan tidak bisa memasukkan yang lain. Upgrade kit menaikkannya ke T2 dan T3: HP, regenerasi, kecepatan putar dan slot bertambah.",
  "jei.bastion.info.large_turret_base": "Memakai tempat 2x2 di lantai dan membawa senjata berat: Missile Launcher, Tesla Coil, Railgun, Mortar, Repulsor Dome dan Plasma Cannon. HP-nya lebih besar dari base standar; selebihnya sama.",
  "jei.bastion.info.tier_upgrade_kit": "Klik kanan turret base satu tier di bawahnya: T1 untuk kit T2, T2 untuk kit T3. Tiap tier menambah HP, regenerasi, kecepatan putar dan slot ammo serta modul.",
  "jei.bastion.info.repair_kit": "Klik kanan turret base untuk memulihkan 40% HP-nya. Part Assembler juga memakainya untuk memulihkan Damaged Turret Base di tier semula.",
  "jei.bastion.info.turret_configurator": "Jongkok + klik kanan turret untuk menyalin targeting-nya, lalu klik kanan turret lain untuk menempelnya.",
  "jei.bastion.info.hub_connector": "Klik kanan Hub Interface untuk me-link, lalu pasang di slot Connector turret: turret itu tampil di layar hub tersebut selama ada di jangkauannya.",
  "jei.bastion.info.feed_hub": "Pasang turret base di sisi-sisinya, base besar di atas hub 2x2 atau lebih. Ammo dan FE yang masuk diteruskan ke turret yang memakainya, yang paling kosong dulu. Hub yang ditaruh bersebelahan menyatu sampai 3x3x3 dan berbagi slot serta FE.",
  "jei.bastion.info.control_hub": "Turret Control Hub adalah kubus 3x3x3: Hub Interface di tengah salah satu sisi setinggi mata, menghadap keluar, Hub Casing dengan minimal satu Hub Power Port, dan Satellite Uplink di blok tengah atas kubus. Alirkan FE ke Power Port. Layarnya menampilkan setiap turret yang memegang Hub Connector yang di-link ke hub itu dalam 1, 3x3 atau 7x7 chunk (Satellite Uplink T1, T2, T3).",
  "jei.bastion.info.creative_power_source": "Khusus creative: memberi FE tak terbatas ke blok di sebelahnya, untuk tes tanpa mod energi."
```

  Then run the JSON parse check from Task 3 Step 5.

- [ ] **Step 6: Build.** Use the Build command with `t5`.
  Expected: `exit=0`. Read every `warning:` for the new files: deprecation warnings on `getBackground` are expected, and nothing else is.

- [ ] **Step 7: Run the GameTests** (JEI is absent there, so this proves nothing JEI-related loads on a server).
  Use the GameTests command with `t5`.
  Expected: the same `All N required tests passed` as in Task 4.

---

### Task 6: Docs, in-game checklist, final runs

**Files:**
- Modify: `docs/TESTING.md` (new section at the end)
- Modify: `docs/PLAN.md` (status line of "Compat JEI & Jade")
- Modify: `docs/HANDOFF.md` (top note)
- Modify: `docs/release/DESCRIPTION.md`, `docs/release/DESCRIPTION.curseforge.html` (Requirements and Known limits)
- Modify: `docs/release/UPLOAD.md` (a "Changelog: next" draft under the 0.2.1 one)

- [ ] **Step 1: Add the in-game checklist** at the end of `docs/TESTING.md`:

```markdown
## JEI & Jade
Run `./gradlew runClient --offline` (JEI and Jade come with the dev run; `-PnoDevMods` leaves them out).

**JEI**
- [ ] Press R on a Gun Barrel: the Part Workstation page shows 3 Iron Ingot + 1 Copper Ingot with their counts, 5 s · 3000 FE (30 FE/t), the Part Workstation as catalyst. All 5 stations have a tab; the Module and Ammo Workstation pages say they also craft instantly.
- [ ] Press U on Kinetic Rounds: the Turret Weapons pages of the Gun, Machine Gun (and every turret that fires them) show up. Each page shows the weapon, the base it mounts on, its ammo (FE weapons: "Runs on FE"), the T1 stats exactly as in a turret's Info tab with that weapon, and the ability text, nothing cut off.
- [ ] Change `[gun] damage` in `config/bastion-turrets.toml` while playing, reopen the Gun's JEI page: the new number shows. Put it back to 7.
- [ ] Press U on a Turret Base, Repair Kit, Hub Connector, Creative Power Source: an Information page with the text from the lang file.
- [ ] Open an Ammo Workstation with copper, gunpowder and iron nuggets in your inventory, press U on Kinetic Rounds, click +: the recipe is picked in the GUI and one of each moves into the inputs. Shift-click +: as many as you have. Remove the gunpowder and look at +: red, the gunpowder slot marked; clicking does nothing. On a Part Workstation page with the Ammo Workstation open there is no + button.

**Jade**
- [ ] Look at a Gun Turret: "Gun Turret · T1", HP, State; hold Shift: Owner (your name), Modules, Heat, Target while it shoots. Ammo shows as items (Jade's own), an energy weapon shows an FE bar, a non-energy one shows no FE bar.
- [ ] Look at each block of a Large Turret Base, a Part Assembler, and a Control Hub's casing, Power Port and satellite: the core's info (turret, station, hub), not the part's.
- [ ] Workstation: "Pick a recipe", then "Making: …", then "Working N%" while it works, with its FE bar. Feed Hub: "Feeds N turrets". Control Hub: Online / Offline / Not formed, the satellite line with the connected turret count (updates within a second), FE.
- [ ] Jade's plugin config lists Bastion's four switches; turning "Turret" off removes the turret lines.

**Without them**
- [ ] `./gradlew runClient --offline -PnoDevMods`: the game starts, workstations and turrets work, no errors mentioning jei, jade, mezz or snownee in `run/logs/latest.log`.
- [ ] `./gradlew runServer --offline` (a dedicated server has no JEI): it starts with no such errors; a client with JEI and Jade joins and sees the Jade lines (Jade on the server) and the JEI pages.
```

- [ ] **Step 2: Update the docs.**
  1. **`docs/PLAN.md` status line.** In the "Compat JEI & Jade" section, change `(belum dikerjakan)` to `(dibuat 2026-10-20, N GameTest lolos; belum dites user di game)` with the real N.
  2. **`docs/HANDOFF.md` top note.** Add one line: "2026-10-20: JEI & Jade compat built (plan `docs/superpowers/plans/2026-10-20-jei-jade-compat.md`), uncommitted; waiting for the user's in-game test."
  3. **Store description, Known limits.** In `docs/release/DESCRIPTION.md`, delete the paragraph "There's no JEI or Jade support yet (each station's GUI lists its own recipes)." In `DESCRIPTION.curseforge.html`, delete the matching `<p>`.
  4. **Store description, Requirements.** In both files, add after the Create line: "Optional: JEI 15.20+ (recipe pages for the workstations and weapons, the + button) and Jade 11.13+ (turret, workstation and hub info when you look at them)". Use the same list markup as the Create line.
  5. **`docs/release/UPLOAD.md` changelog draft.** Under the 0.2.1 changelog, add:

```markdown
## Changelog: next version (draft, version decided at release)
New
- JEI support: a recipe page for each of the 5 workstations (ingredients with counts, time, FE), a "Turret Weapons" page per weapon (its base, ammo and stats; press U on an ammo to see which turrets fire it), information pages for bases, kits, hubs and the Creative Power Source, and the + button that moves a recipe's ingredients into an open station.
- Jade support: look at a turret for its weapon, tier, HP and state (Shift: owner, modules, heat, target); at a workstation for what it makes and its progress; at a Feed Hub or Control Hub for what it feeds or reaches. Any block of a big machine shows the whole machine.
Note
- Clients need this version to join a server running it (new network packet).
```

- [ ] **Step 3: Check that the lang files have the same keys:**

```bash
python3 -c "import json; e=json.load(open('src/main/resources/assets/bastion/lang/en_us.json',encoding='utf-8')); i=json.load(open('src/main/resources/assets/bastion/lang/id_id.json',encoding='utf-8')); print(sorted(set(e)^set(i)))"
```

  Expected: `[]`. If keys show up, run the same check on `git show HEAD:<file>`. Gaps that were already there are not this plan's to fix; report them.

- [ ] **Step 4: Run the final build:** `./gradlew build --offline > build/build-final.log 2>&1; echo "exit=$?"`.
  Expected: `exit=0`.

- [ ] **Step 5: Run the final GameTests twice, with and without compat mods.**
  1. `./gradlew runGameTestServer --offline > build/gametest-final.log 2>&1; echo "exit=$?"`.
  2. `./gradlew runGameTestServer --offline -PwithCompatMods > build/gametest-final-compat.log 2>&1; echo "exit=$?"`.
  3. Grep both files as in the GameTests command.

  Expected: `All N required tests passed` in both (only the known flaky tests may need a rerun).

- [ ] **Step 6: Check git status.**
  Run `git status --short` and confirm that only plan files, the new and modified sources, lang, `build.gradle`, `mods.toml` and docs changed. `modlist.html` and `sfx/` stay untracked.
  Do not commit; report to the user with the in-game checklist (CLAUDE.md).
