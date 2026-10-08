# Module Tiers (Mk1/Mk2/Mk3) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give each of the 10 modifier ("upgrade") modules a Mk2 and Mk3 (×1.5 / ×2 of Mk1, Penetrator +1/+2/+3), made by upgrading the tier below at the Module Workstation, one module kind per base whatever its Mk.

**Architecture:** Mk2/Mk3 are separate items (`<id>_mk2`, `<id>_mk3`) registered right after their Mk1, so the existing per-item data loader, recipes and creative tab work unchanged. `ModifierItem` learns its `mk()` and `family()` (the Mk1 id); `TurretInventory` compares families. A small generator writes the Mk2/Mk3 data JSON (scaled from Mk1) and item models; `gen_recipes.py` adds the upgrade recipes; `gen_textures.py` marks chips by tier.

**Tech Stack:** Forge 1.20.1, Java 17, Python 3 generators (numpy/Pillow for textures), GameTest.

**Spec:** `docs/PLAN.md` §4.6 "Tier modul Mk1/Mk2/Mk3 (permintaan user 2026-10-08 13:35, desain disetujui 13:39)"

## Global Constraints

- Mk1 keeps its item id (`range_module`, ...): existing worlds load unchanged.
- Mk2 = every Mk1 number × 1.5, Mk3 × 2 (negatives too); Penetrator `pierce` = 1 / 2 / 3; `weapons` lists copied.
- Upgrade recipes (Module Workstation): Mk1 + 2 diamond + 4 redstone → Mk2 (100 t, 4,000 FE); Mk2 + 1 netherite scrap + 2 diamond + 1 amethyst shard → Mk3 (140 t, 8,000 FE).
- Names: Mk1 unchanged ("Range Module"), Mk2/Mk3 "<name> Mk2/Mk3" (en_us and id_id); tooltip first line "Mk N".
- Textures: Mk1 chip frame graphite + 1 pip, Mk2 cyan frame + 2 pips, Mk3 gold frame + 3 pips.
- PLAN 9.6: balance numbers live in JSON. No commit until the user says "commit". Run Gradle with `--offline`.

## Review Focus

1. A base that already holds a Mk1 module gets a Mk3 of the same kind from a hopper/Feed Hub (automation goes through `isItemValid`): it must be refused like a manual insert. Pinned by `oneModifierPerKind` (calls `isItemValid`, the path automation uses).
2. Old worlds: a turret with `range_module` installed loads and keeps working (id unchanged). No migration code; covered by the unchanged id (Global Constraints).
3. Choke Mk3 spread -0.8 and Autoloader Mk3 reload +0.8 stay sane (spread multiplier `max(0, 1 + spread)` = 0.2; reload rate 1.8): a reasonable player sees tighter/faster, not broken. Checked by the scaling test's numbers plus the TESTING line.
4. Workstation GUI recipe list grows by 20 entries under the Range/Choke tabs: the list must still scroll and the upgrade recipes must show their Mk1 ingredient. Manual TESTING line.
5. The Shift "works on" tooltip and `affects()` read Mk2/Mk3 effects through the same `effect()` data: a Mk3 Coolant Loop still lists only the Machine Gun. Covered indirectly: the scaling test keeps every non-zero Mk1 field non-zero in Mk2/Mk3, and `affects()` only checks which fields are non-zero.

---

### Task 1: Mk and family on ModifierItem, registration, one per kind

**Files:**
- Modify: `src/main/java/dev/bastion/modifier/ModifierItem.java`
- Modify: `src/main/java/dev/bastion/registry/BastionItems.java` (`modifier(String)`, a `MODIFIER_TIERS` list)
- Modify: `src/main/java/dev/bastion/turret/TurretInventory.java` (`hasModifierElsewhere`)
- Test: `src/main/java/dev/bastion/gametest/TurretModifierTierTests.java` (new)

**Interfaces:**
- Produces: `new ModifierItem(Item.Properties, int mk)`, `int mk()`, `ResourceLocation family()` (the Mk1 item id); `BastionItems.MODIFIER_TIERS` (List of the 20 Mk2/Mk3 RegistryObjects); items `bastion:<mk1 id>_mk2` / `_mk3` for all 10 kinds.

- [ ] **Step 1: Write the failing test** `TurretModifierTierTests.oneModifierPerKind`:

```java
package dev.bastion.gametest;

import dev.bastion.Bastion;
import dev.bastion.modifier.ModifierItem;
import dev.bastion.registry.BastionBlocks;
import dev.bastion.registry.BastionItems;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.TurretInventory;
import dev.bastion.turret.TurretTier;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/** Modifier Mk1-Mk3 (PLAN 4.6 "Tier modul"): one per kind, scaled numbers, upgrade at the Module Workstation. */
@GameTestHolder(Bastion.MOD_ID)
@PrefixGameTestTemplate(false)
public class TurretModifierTierTests {
    private static final String ARENA = "arena";

    static ModifierItem item(String path) {
        return ForgeRegistries.ITEMS.getValue(Bastion.id(path)) instanceof ModifierItem modifier ? modifier : null;
    }

    /** A base takes one of each kind whatever its Mk: Range Mk1 in, Range Mk3 refused, Damage Amplifier Mk2 fine. */
    @GameTest(template = ARENA, batch = "oneModifierPerKind")
    public static void oneModifierPerKind(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 3);
        helper.setBlock(pos, BastionBlocks.TURRET_BASE.get());
        TurretBaseBlockEntity turret = (TurretBaseBlockEntity) helper.getBlockEntity(pos);
        turret.upgradeTo(TurretTier.T2);
        turret.upgradeTo(TurretTier.T3); // 4 module slots
        ModifierItem rangeMk3 = item("range_module_mk3"), damageMk2 = item("damage_amplifier_mk2");
        helper.assertTrue(rangeMk3 != null && damageMk2 != null, "Mk2/Mk3 items missing");
        int first = TurretInventory.MODIFIER_START;
        turret.inventory().setStackInSlot(first, new ItemStack(BastionItems.RANGE_MODULE.get()));
        helper.assertTrue(!turret.inventory().isItemValid(first + 1, new ItemStack(rangeMk3)), "Range Mk3 accepted next to Range Mk1");
        helper.assertTrue(turret.inventory().isItemValid(first + 1, new ItemStack(damageMk2)), "Damage Amplifier Mk2 refused");
        helper.succeed();
    }
}
```

- [ ] **Step 2: Run** `./gradlew build --offline -q && ./gradlew runGameTestServer --offline -q; grep -E "required tests|failed!" run/logs/latest.log` → Expected: `oneModifierPerKind failed! Mk2/Mk3 items missing`.

- [ ] **Step 3: Implement**

`ModifierItem`:
```java
    private final int mk;

    public ModifierItem(Properties properties, int mk) {
        super(properties);
        this.mk = mk;
    }

    /** 1, 2 or 3 (PLAN 4.6 "Tier modul"). */
    public int mk() {
        return mk;
    }

    /** The module kind every Mk shares (its Mk1 id): a base takes one of each kind. */
    public ResourceLocation family() {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(this);
        return mk == 1 ? id : new ResourceLocation(id.getNamespace(), id.getPath().substring(0, id.getPath().length() - "_mk2".length()));
    }
```
and, first in `appendHoverText`: `tooltip.add(Component.translatable("tooltip.bastion.modifier.mk", mk).withStyle(ChatFormatting.GRAY));` (key added in Task 4).

`BastionItems`, above `RANGE_MODULE` (static fields initialise in textual order):
```java
    /** Mk2 and Mk3 of every modifier, each registered right after its Mk1 so the creative tab keeps them together (PLAN 4.6). */
    public static final List<RegistryObject<ModifierItem>> MODIFIER_TIERS = new ArrayList<>();
```
and `modifier(String)`:
```java
    private static RegistryObject<ModifierItem> modifier(String name) {
        RegistryObject<ModifierItem> mk1 = REGISTER.register(name, () -> new ModifierItem(new Item.Properties().stacksTo(1), 1));
        for (int mk = 2; mk <= 3; mk++) {
            int tier = mk;
            MODIFIER_TIERS.add(REGISTER.register(name + "_mk" + mk, () -> new ModifierItem(new Item.Properties().stacksTo(1), tier)));
        }
        return mk1;
    }
```
(imports `java.util.ArrayList` if missing.)

`TurretInventory.hasModifierElsewhere`:
```java
    /** Each modifier kind counts once per base, whatever its Mk (PLAN 4.6). */
    private boolean hasModifierElsewhere(Item item, int slot) {
        if (!(item instanceof ModifierItem modifier)) return false;
        for (int i = MODIFIER_START; i < SIZE; i++) {
            if (i != slot && getStackInSlot(i).getItem() instanceof ModifierItem other && other.family().equals(modifier.family())) return true;
        }
        return false;
    }
```

- [ ] **Step 4: Run** the suite → Expected: `All 110 required tests passed` (109 + 1).

---

### Task 2: Mk2/Mk3 data and item models

**Files:**
- Create: `tools/gen_modifier_tiers.py`
- Generated: `src/main/resources/data/bastion/turret_modifiers/<id>_mk2.json`, `_mk3.json` (20), `src/main/resources/assets/bastion/models/item/<id>_mk2.json`, `_mk3.json` (20)
- Test: `TurretModifierTierTests.modifierTiersScale`

- [ ] **Step 1: Write the failing test** (add to `TurretModifierTierTests`; imports `dev.bastion.modifier.ModifierEffect`, `net.minecraft.core.registries.BuiltInRegistries`, `java.lang.reflect.RecordComponent`, `java.util.List`):

```java
    /** Every float of Mk2/Mk3 is Mk1 x1.5 / x2, Penetrator's pierce 1/2/3, the weapons list unchanged. */
    @GameTest(template = ARENA, batch = "modifierTiersScale")
    public static void modifierTiersScale(GameTestHelper helper) throws ReflectiveOperationException {
        List<ModifierItem> mk1 = BuiltInRegistries.ITEM.stream().filter(i -> i instanceof ModifierItem m && m.mk() == 1).map(i -> (ModifierItem) i).toList();
        helper.assertTrue(mk1.size() == 10, "expected 10 modifier kinds, found " + mk1.size());
        for (ModifierItem base : mk1) {
            String id = BuiltInRegistries.ITEM.getKey(base).getPath();
            for (int mk = 2; mk <= 3; mk++) {
                ModifierItem tier = item(id + "_mk" + mk);
                helper.assertTrue(tier != null && tier.mk() == mk && tier.family().equals(BuiltInRegistries.ITEM.getKey(base)), id + " Mk" + mk + " missing");
                double scale = mk == 2 ? 1.5 : 2;
                for (RecordComponent c : ModifierEffect.class.getRecordComponents()) {
                    Object a = c.getAccessor().invoke(base.effect()), b = c.getAccessor().invoke(tier.effect());
                    if (a instanceof Float f) helper.assertTrue(Math.abs((Float) b - f * scale) < 1e-4, id + " Mk" + mk + " " + c.getName() + " " + b + " vs Mk1 " + f);
                    else if (a instanceof Integer n) helper.assertTrue((Integer) b == n * mk, id + " Mk" + mk + " " + c.getName() + " " + b);
                    else helper.assertTrue(a.equals(b), id + " Mk" + mk + " " + c.getName() + " differs");
                }
            }
        }
        helper.succeed();
    }
```

- [ ] **Step 2: Run** the suite → Expected: `modifierTiersScale failed! range_module Mk2 range 0.0 vs Mk1 0.25` (no data yet: NONE effect).

- [ ] **Step 3: Implement** `tools/gen_modifier_tiers.py`:

```python
#!/usr/bin/env python3
"""Mk2/Mk3 of every turret modifier (PLAN 4.6 "Tier modul"): data JSON scaled from the Mk1 file (x1.5, x2; Penetrator's
pierce +1/+2/+3) and the item models. Re-run after changing a Mk1 file; edit a Mk2/Mk3 file by hand to tune one tier
(the next run overwrites it)."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent / "src/main/resources"
DATA = ROOT / "data/bastion/turret_modifiers"
MODELS = ROOT / "assets/bastion/models/item"
SCALE = {2: 1.5, 3: 2.0}


def tier(effect, mk):
    out = {}
    for key, value in effect.items():
        if key == "weapons":
            out[key] = value
        elif key == "pierce":
            out[key] = value * mk  # whole targets: +1/+2/+3
        else:
            out[key] = round(value * SCALE[mk], 4)
    return out


def main():
    n = 0
    for path in sorted(DATA.glob("*.json")):
        if "_mk" in path.stem:
            continue
        effect = json.loads(path.read_text())
        for mk in (2, 3):
            name = f"{path.stem}_mk{mk}"
            (DATA / f"{name}.json").write_text(json.dumps(tier(effect, mk), indent=2) + "\n")
            model = {"parent": "minecraft:item/generated", "textures": {"layer0": f"bastion:item/{name}"}}
            (MODELS / f"{name}.json").write_text(json.dumps(model, indent=2) + "\n")
            n += 1
    print(f"wrote {n} modifier tiers")


if __name__ == "__main__":
    main()
```
Run `python3 tools/gen_modifier_tiers.py` → `wrote 20 modifier tiers`.

- [ ] **Step 4: Run** the suite → Expected: `All 111 required tests passed`.

---

### Task 3: Upgrade recipes

**Files:** Modify `tools/gen_recipes.py` (`main()`, after the `autoloader` recipe); regenerate. Test: `TurretModifierTierTests.moduleUpgradesToMk2`.

- [ ] **Step 1: Write the failing test** (imports `dev.bastion.workstation.WorkstationBlockEntity`, `net.minecraft.core.Direction`, `net.minecraft.world.item.Items`, `net.minecraftforge.common.util.FakePlayer`, `net.minecraftforge.common.util.FakePlayerFactory`):

```java
    /** Module Workstation: Range Module + 2 diamonds + 4 redstone become a Range Module Mk2 (instant for the player). */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "moduleUpgradesToMk2")
    public static void moduleUpgradesToMk2(GameTestHelper helper) {
        BlockPos core = new BlockPos(4, 2, 2);
        BastionBlocks.MODULE_WORKSTATION.get().placeAt(helper.getLevel(), helper.absolutePos(core), Direction.NORTH);
        helper.setBlock(core.east(), BastionBlocks.CREATIVE_POWER_SOURCE.get());
        WorkstationBlockEntity station = (WorkstationBlockEntity) helper.getBlockEntity(core);
        helper.runAtTickTime(5, () -> {
            station.selectRecipe(Bastion.id("workstation/module_workstation/range_module_mk2"));
            FakePlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
            player.getInventory().clearContent();
            player.getInventory().add(new ItemStack(BastionItems.RANGE_MODULE.get()));
            player.getInventory().add(new ItemStack(Items.DIAMOND, 2));
            player.getInventory().add(new ItemStack(Items.REDSTONE, 4));
            station.instantCraft(player, 1);
            helper.assertTrue(player.getInventory().countItem(item("range_module_mk2")) == 1, "no Range Module Mk2");
            helper.assertTrue(player.getInventory().countItem(BastionItems.RANGE_MODULE.get()) == 0, "the Mk1 was not used up");
            player.getInventory().clearContent();
            helper.succeed();
        });
    }
```

- [ ] **Step 2: Run** → Expected: `moduleUpgradesToMk2 failed! no Range Module Mk2` (recipe unknown, selectRecipe ignored).

- [ ] **Step 3: Implement** in `gen_recipes.py` `main()`, after the autoloader `write(...)`:

```python
    # Mk2 / Mk3 (PLAN 4.6): upgrade the tier below at the Module Workstation, listed under the same tab as the Mk1.
    for module in [*MODULE, "choke_module", "autoloader"]:
        tab = B + ("choke_module" if module in ("choke_module", "autoloader") else "range_module")
        write(out / "module_workstation" / f"{module}_mk2.json", recipe("module_workstation", tab, B + f"{module}_mk2",
                                                                      {B + module: 1, "diamond": 2, "redstone": 4}, 100, 4000))
        write(out / "module_workstation" / f"{module}_mk3.json", recipe("module_workstation", tab, B + f"{module}_mk3",
                                                                      {B + f"{module}_mk2": 1, "netherite_scrap": 1, "diamond": 2, "amethyst_shard": 1},
                                                                      140, 8000))
        n += 2
```
Run `python3 tools/gen_recipes.py`; `git status --short src/main/resources/data/bastion/recipes` shows only the 20 new files (the generator rewrites everything deterministically).

- [ ] **Step 4: Run** the suite → Expected: `All 112 required tests passed`.

---

### Task 4: Textures, names, tooltip, visual check

**Files:** Modify `tools/gen_textures.py` (`modifier_chip`, `TEXTURES`); lang `en_us.json`, `id_id.json`; create `src/dev/java/dev/bastion/dev/ModuleShowcase.java`; `Showcase.java` ON list.

- [ ] **Step 1: Textures.** In `gen_textures.py`:
```python
TIER_FRAME = {1: GRAPHITE, 2: rgb("2FA8CC"), 3: rgb("E0A81C")}  # Mk1 as before, Mk2 cyan, Mk3 gold (PLAN 4.6)
TIER_PIP = {1: METAL * 1.2, 2: rgb("7FE6FF"), 3: rgb("FFE07A")}


def modifier_chip(name, mk=1):
    """Circuit chip with a per-modifier glyph; the frame colour and 1-3 pips in the lower right corner tell the Mk."""
    img = np.zeros((16, 16, 4))
    stamp(img, Sprite(CHIP.rows, {**CHIP.palette, "g": TIER_FRAME[mk]}), 0, 1)
    hex_code, rows = MODIFIER_GLYPHS[name]
    accent = rgb(hex_code)
    stamp(img, Sprite(rows, {"a": accent, "d": accent * 0.55}), 4, 4)
    for i in range(mk):
        rect(img, 11 - 2 * i, 11, 1, 1, TIER_PIP[mk])
    return outline(img, OUTLINE)
```
(check `rect(img, x, y, w, h, colour)`'s colour format matches what `stamp` writes; adapt if it expects RGBA.) In `TEXTURES` add next to the Mk1 line: `**{f"item/{name}_mk{mk}.png": (lambda name=name, mk=mk: modifier_chip(name, mk)) for name in MODIFIER_GLYPHS for mk in (2, 3)},`. Check `MODIFIER_GLYPHS` has all 10 kinds (incl. choke_module, autoloader). Run `python3 tools/gen_textures.py`; `git status` shows the 10 Mk1 chips changed (one pip added) and 20 new PNGs, nothing else.
- [ ] **Step 2: Names + tooltip key.** Python snippet: for each of the 10 kinds read `item.bastion.<id>` from en_us/id_id and insert `item.bastion.<id>_mk2` = name + " Mk2", `_mk3` = name + " Mk3` right after it; add `"tooltip.bastion.modifier.mk": "Mk%s"` to both files; `json.loads` the result before writing.
- [ ] **Step 3: Contact sheet.** Scale the 30 chip PNGs ×8 into one image (Pillow) and look: frames graphite/cyan/gold, 1/2/3 pips, glyph unchanged.
- [ ] **Step 4: Scene** `-Pshowcase=modules` (`ModuleShowcase`, modelled on `HealthShowcase`; add `"modules"` to `Showcase.ON` exclusions): fills the hotbar with Range Mk1/2/3, Overclock Mk1/2/3, Penetrator Mk1/2/3, GUI shown (`hideGui = false`), shot `modules_hotbar`. One hotbar shot is enough to prove the models load (a missing model renders magenta/black). Run with `-PnoDevMods -PshowcaseWorld=showcase_fx`; check the shot.
- [ ] **Step 5:** `./gradlew build --offline -q && ./gradlew runGameTestServer --offline -q` → `All 112 required tests passed`.

---

### Task 5: Docs

- [ ] `docs/TESTING.md`: section "Module tiers (0.1.2)": tooltip Mk line; upgrade Mk1→Mk2→Mk3 at the Module Workstation (Range tab / Choke tab); Range Mk1 + Range Mk3 refused (also via hopper/Feed Hub); Mk3 stats visible on the turret's Info tab (e.g. Range Mk3 +50%); Overclock Mk3 −30% HP; Penetrator Mk3 pierces 3; workstation recipe list scrolls with the extra entries.
- [ ] `docs/release/UPLOAD.md` 0.1.2 "New": `- Upgrade modules come in Mk1, Mk2 and Mk3 (x1.5 / x2 of Mk1, Penetrator +1/+2/+3): upgrade the one you have at the Module Workstation (diamonds, then netherite scrap). One of each kind per turret.`
- [ ] `docs/release/DESCRIPTION.md` + `.html`: one sentence in the modules paragraph.
- [ ] `docs/HANDOFF.md`: NOW item (files, generator, tests, 112), decision row, scene `modules`.
- [ ] Final: build + suite green; in-game checklist for the user. No commit until "commit".
