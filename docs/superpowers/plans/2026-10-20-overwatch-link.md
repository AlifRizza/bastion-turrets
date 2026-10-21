# Overwatch Link Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Through a Control Hub, a player views any turret that holds an Overwatch Link from a camera at that turret. They aim and fire it themselves, or only watch for weapons that never turn. Mortar shots show their arc and landing ring.

**Architecture:**
- Everything that decides gameplay is common code covered by GameTests:
  - `Ballistics`: the shared arc math.
  - `ManualAim`: barrel angles for an aim point.
  - `ManualControl` plus `TurretBaseBlockEntity.tickManual` / `wouldHit`: manual fire and the hit rule.
  - `OverwatchSessions`: who controls what, start/stop/switch rules, auto-release.
- The camera is client-only, modelled on Superb Warfare's drone (approach studied, code not copied, §9.4):
  - a `Camera.setup` mixin moves the view to the turret;
  - the player's body stays at the hub;
  - nothing on the server moves or sends extra chunks.
- Client classes (`dev.bastion.client.overwatch`, `CameraMixin`) only render, read input and send packets.

**Tech Stack:** Forge 1.20.1 (47.4.26), Java 17, Mixin 0.8.5 (already set up, `bastion.mixins.json`), GameTest.

**Spec:** `docs/PLAN.md` §8, "Overwatch Link: kendali turret jarak jauh":
- Approved 2026-10-20 21:38.
- The camera section was changed to client-side at 21:45 on the user's choice.

## Global Constraints

**Git and protected files:**
- **No commits:** never commit, push or tag. The user's rule is "jangan commit sebelum saya bilang 'commit'". Each task ends with its checks passing, not with a commit.
- Never stage or commit `modlist.html` or `sfx/`.
- Never touch `run/saves/showcase`. Do not edit `run/config/*` without asking.

**Gradle and test runs:**
- Gradle always runs with `--offline`.
- Never start the client yourself (`runClient`); the user runs it. GameTest server runs are fine.
- Never pipe a test run into `grep`. Save the full log to a file, read the exit code, then grep the file.
- A GameTest run passes when the log says `All N required tests passed`.
- There is no per-test filter: every GameTest run is the full suite, about 2 minutes.
- Known flaky tests that pass on a rerun:
  - `ControlHubTests.satelliteHalvesBreakTogether`
  - `ControlHubTests.powerPortTakesFeOnlyWhenFormed`
  - `TurretLargeTests.largeBaseIsOneBlock`
  - `TurretBaseTests.workstationIsOneBlock` / `destroyedT3KeepsTierOnDamagedBase` ("drops: []" or "found 0")
  - `ControlHubTests.feedHubNewControllerFeeds`

  Rerun once before treating one of these as real.
- Multiblock tests must wait until the structure is formed (memory `bastion-timing-tests`). Use `whenOnline` (Task 4), never a fixed tick.

**Code rules:**
- §9.5: anything importing `net.minecraft.client` lives only in `dev.bastion.client..` or is a mixin listed under `"client"` in `bastion.mixins.json`. Common code must never reference client classes. The GameTest server and dedicated servers load common code only.
- §9.4: Superb Warfare is GPL-3.0. Its approach (a Camera mixin, input blocking) is reused; none of its code is copied.
- §1: no vanilla visual assets. The HUD and arc are drawn with fills, the mod's own `tracer.png`, and plain text.
- §9.6: balance numbers stay in config/JSON.
  - The recipe numbers live in `tools/gen_recipes.py`. Its output is the data file.
  - Camera distance, zoom steps and HUD layout are UI constants and live in client code.
- Match the surrounding code: one-line Javadoc on new types and non-obvious methods, `ponytail:` comments for deliberate shortcuts, no extra abstractions.
- Lang: every new key goes into both `en_us.json` and `id_id.json` with the texts in this plan. Both files round-trip through `json.dumps(indent=2, ensure_ascii=False)` unchanged (checked 2026-10-20), so the inline Python insert below is safe.

**Gameplay rules from the spec (exact):**
- Overwatch Link = a Hub Connector (links the same way, fits the Connector slot) plus control. Recipe at the Module Workstation: 1 Hub Connector, 2 diamond, 1 eye of ender, 1 spyglass, 1 observer; 200 ticks; 12,000 FE.
- Start needs:
  - the turret holds an Overwatch Link;
  - the hub is online;
  - the turret is in reach and loaded;
  - `canConfigure(player)`;
  - the player is within 8 blocks of the Interface (`hub.reachable`);
  - nobody else controls it.
- Released by:
  - sneak;
  - any damage to the player;
  - hub offline, unformed or gone;
  - turret gone, Link removed or swapped;
  - turret out of reach;
  - the player more than 8 blocks from the Interface;
  - logout, death, dimension change;
  - client: the turret's chunk not loaded on the client.
- Aim: the barrel points straight at the crosshair point, at once (turn speed ignored), pitch clamped to `min_pitch`/`max_pitch`.
- Trigger held: the normal fire cycle (interval, ammo, heat, overheat, FE, modules).
  - Machine Gun spins up while held.
  - Charged weapons charge while held and fire when full; letting go early cancels the charge.
- During control:
  - no auto-targeting;
  - no Precision Lock;
  - the state machine keeps its states;
  - GUI and Jade show "Manual: name".
- Per weapon:
  - Gun, MG, Shotgun, Sniper, Laser, Railgun, Ion, Plasma Repeater, Plasma Cannon, Flamethrower, Repulsor and Rocket Launcher fire along the barrel.
  - Missile Launcher locks the creature under the crosshair; with none, the missile flies straight.
  - Mortar: yaw at the spot, elevation from the high arc; it cannot fire outside `min_range`..`range`.
  - `turn_speed` 0 weapons (Tesla Coil, Repulsor Dome) are watch-only and keep firing automatically.
- Manual hits: every living thing except the owner. Other players only with server PvP on. The controller is also spared (ruling: their body stands at the hub; the spec names only the owner).
- Third person:
  - about 3.5 blocks behind and above the pivot (large base about 5.5);
  - pulled in by blocks.
- F5 view from the barrel, with that turret's weapon model hidden.
- Controls (KeyMappings in category "Bastion", rebindable):
  - LMB: fire;
  - scroll: zoom 1×–4×;
  - ←/→: switch turret;
  - Shift: leave.
- Disabled during control: movement, jump, inventory, hotbar, use and drop. Chat and Esc keep working.
- HUD:
  - corner frame, REC dot and time, scan lines;
  - turret name, distance to the hub;
  - crosshair (red when it can't fire);
  - HP, ammo/FE, heat and charge bars;
  - key hints.

  No vanilla hotbar, hand or crosshair.
- Arc indicator:
  - glowing dashes from the muzzle to the first block hit;
  - a ring of `blast_radius` where it lands;
  - red when it can't fire.

  Weapons opt in with `WeaponType.ballistic()`.
- Release 0.3.0 (version bumped when the user says to release). Network `PROTOCOL` "11" becomes "12".

## Review Focus

1. **The turret is destroyed, mined or unloaded while controlled.** The session ends, the player's view returns to the body, nothing crashes, and a reloaded turret is not left "controlled". Pinned by `controlEndsWhenItShould` (Task 4: the turret block removed) and by `manual` never being saved.
2. **The client stops sending input while the trigger was held** (lag, alt-tab, a crash). The turret stops firing within `ManualControl.INPUT_TIMEOUT` (20 ticks). Pinned by `triggerLetsGoWithoutInput` (Task 3).
3. **The weapon module is swapped mid-control** (to a watch-only weapon, or removed). Manual steering stops and the hit rule goes back to the filter; a Tesla keeps firing automatically. Pinned by `manualShotsSpareOwnerAndController` (swap to Tesla) and `teslaStaysAutomaticUnderControl` (Task 3).
4. **Garbage or hostile input:** NaN or far aim points, input for another turret, input from a player who is not the controller, a locked entity out of range. All are ignored. Pinned by `inputOnlyFromTheController` (Task 4) and the range/`wouldHit` check in `manualTarget` (Task 3).
5. **Switching turrets with ←/→ while another player controls one.** Their turret is skipped, as is one with a plain Hub Connector, and the order wraps. Pinned by `switchingSkipsTakenAndPlainTurrets` (Task 4).

---

## File Structure

**New files:**

| File | What it holds |
|---|---|
| `src/main/java/dev/bastion/hub/OverwatchLinkItem.java` | The item: a `HubConnectorItem` subclass; `fitted(turret)` |
| `src/main/java/dev/bastion/hub/OverwatchSessions.java` | Server sessions: start/stop/cycle/input, auto-release events, status pushes |
| `src/main/java/dev/bastion/turret/ManualControl.java` | The controller's identity and last input (server only, never saved) |
| `src/main/java/dev/bastion/weapon/Ballistics.java` | High-arc launch math, moved out of `MortarWeapon` |
| `src/main/java/dev/bastion/weapon/ManualAim.java` | Barrel yaw/pitch for an aim point, shared by server and client |
| `src/main/java/dev/bastion/network/OverwatchRequest.java` | C→S start / cycle / exit |
| `src/main/java/dev/bastion/network/OverwatchInput.java` | C→S aim point, locked entity, trigger (every client tick) |
| `src/main/java/dev/bastion/network/OverwatchView.java` | S→C session began (turret, hub) or ended |
| `src/main/java/dev/bastion/network/OverwatchStatus.java` | S→C the controlled turret's roster entry every 10 ticks (HUD ammo/FE) |
| `src/main/java/dev/bastion/client/overwatch/OverwatchClient.java` | Client session state, camera view, crosshair ray, input blocking, keys |
| `src/main/java/dev/bastion/client/overwatch/OverwatchHud.java` | The CCTV overlay |
| `src/main/java/dev/bastion/client/overwatch/OverwatchArc.java` | The Mortar arc and landing ring |
| `src/main/java/dev/bastion/mixin/CameraMixin.java` | Moves the camera to the turret |
| `src/main/java/dev/bastion/gametest/OverwatchTests.java` | All new GameTests |
| `src/main/resources/assets/bastion/models/item/overwatch_link.json`, `overwatch_link_linked.json` | Item models |
| generated: `textures/item/overwatch_link.png`, `overwatch_link_linked.png`, `data/bastion/recipes/workstation/module_workstation/overwatch_link.json` | From `tools/` |

**Modified files:**

- `registry/BastionItems.java`
- `client/BastionClient.java`
- `client/compat/jei/BastionJeiPlugin.java`
- `tools/gen_textures.py`
- `tools/gen_recipes.py`
- the lang files
- `weapon/WeaponType.java`
- `weapon/FireContext.java`
- `weapon/types/MortarWeapon.java`
- `weapon/types/MissileLauncherWeapon.java`
- `projectile/TurretRocketEntity.java`
- `weapon/Bullets.java`
- `turret/targeting/TargetSelector.java`
- `turret/TurretBaseBlockEntity.java`
- `gametest/TurretCombatTests.java` (FireContext arity)
- `network/BastionNetwork.java`
- `client/ClientPacketHandler.java`
- `menu/ControlHubMenu.java`
- `client/screen/ControlHubScreen.java`
- `client/screen/FlatButton.java`
- `client/screen/TurretScreen.java`
- `compat/BlockInfo.java`
- `client/render/WeaponMountLayer.java`
- `client/vfx/VfxManager.java`
- `resources/bastion.mixins.json`
- the docs

---

### Task 1: Overwatch Link item

**Files:**
- Create: `src/main/java/dev/bastion/hub/OverwatchLinkItem.java`, item models, `src/main/java/dev/bastion/gametest/OverwatchTests.java`
- Modify:
  - `src/main/java/dev/bastion/registry/BastionItems.java` (after `HUB_CONNECTOR`, line ~165)
  - `src/main/java/dev/bastion/client/BastionClient.java` (`onClientSetup`)
  - `src/main/java/dev/bastion/client/compat/jei/BastionJeiPlugin.java` (`registerRecipes`)
  - `tools/gen_textures.py`
  - `tools/gen_recipes.py`
  - both lang files

**Interfaces:**
- Produces:
  - `BastionItems.OVERWATCH_LINK` (`RegistryObject<OverwatchLinkItem>`)
  - `OverwatchLinkItem.fitted(TurretBaseBlockEntity) -> boolean` (true when the Connector slot holds an Overwatch Link, linked or not)
  - `OverwatchTests` class with constants `ARENA, YARD, PIT, RANGE` and helper `link(TurretBaseBlockEntity, HubInterfaceBlockEntity)`

- [ ] **Step 1: Write the failing test**

Create `src/main/java/dev/bastion/gametest/OverwatchTests.java`:

```java
package dev.bastion.gametest;

import dev.bastion.Bastion;
import dev.bastion.hub.HubConnectorItem;
import dev.bastion.hub.HubInterfaceBlockEntity;
import dev.bastion.hub.OverwatchLinkItem;
import dev.bastion.registry.BastionBlocks;
import dev.bastion.registry.BastionItems;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.TurretInventory;
import dev.bastion.workstation.WorkstationRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Overwatch Link (PLAN "Overwatch Link"): the item, manual control of a turret, the hub sessions. */
@GameTestHolder(Bastion.MOD_ID)
@PrefixGameTestTemplate(false)
public class OverwatchTests {
    static final String ARENA = "arena", YARD = "yard", PIT = "pit", RANGE = "range";

    /** An Overwatch Link linked to {@code hub}, in the turret's Connector slot. */
    static void link(TurretBaseBlockEntity turret, HubInterfaceBlockEntity hub) {
        turret.inventory().setStackInSlot(TurretInventory.CONNECTOR,
                HubConnectorItem.link(new ItemStack(BastionItems.OVERWATCH_LINK.get()), hub.getLevel(), hub.getBlockPos()));
    }

    /** It links like a Hub Connector, fits the Connector slot, counts as connected, and the Module Workstation makes it. */
    @GameTest(template = ARENA, batch = "overwatchLinkIsAHubConnector")
    public static void overwatchLinkIsAHubConnector(GameTestHelper helper) {
        ItemStack stack = new ItemStack(BastionItems.OVERWATCH_LINK.get());
        helper.assertTrue(stack.getItem() instanceof HubConnectorItem, "not a Hub Connector");
        BlockPos iface = helper.absolutePos(new BlockPos(1, 1, 1));
        HubConnectorItem.link(stack, helper.getLevel(), iface);
        helper.assertTrue(HubConnectorItem.linkedTo(stack, helper.getLevel(), iface), "it does not link");
        helper.setBlock(new BlockPos(3, 2, 3), BastionBlocks.TURRET_BASE.get());
        TurretBaseBlockEntity turret = (TurretBaseBlockEntity) helper.getBlockEntity(new BlockPos(3, 2, 3));
        helper.assertTrue(turret.inventory().isItemValid(TurretInventory.CONNECTOR, stack), "the Connector slot refuses it");
        helper.assertTrue(!OverwatchLinkItem.fitted(turret), "fitted with an empty slot");
        turret.inventory().setStackInSlot(TurretInventory.CONNECTOR, stack);
        helper.assertTrue(OverwatchLinkItem.fitted(turret), "not fitted");
        turret.inventory().setStackInSlot(TurretInventory.CONNECTOR, new ItemStack(BastionItems.HUB_CONNECTOR.get()));
        helper.assertTrue(!OverwatchLinkItem.fitted(turret), "a plain Hub Connector counts as a Link");
        var recipe = helper.getLevel().getRecipeManager().byKey(Bastion.id("workstation/module_workstation/overwatch_link"));
        helper.assertTrue(recipe.isPresent() && recipe.get() instanceof WorkstationRecipe w
                && w.result().is(BastionItems.OVERWATCH_LINK.get()), "no Module Workstation recipe");
        helper.succeed();
    }
}
```

- [ ] **Step 2: Run it and watch it fail**

Run: `./gradlew runGameTestServer --offline > build/gt-task1-red.log 2>&1; echo "exit $?"; grep -E "error:|required tests" build/gt-task1-red.log | head`
Expected: the compile fails with `cannot find symbol ... OVERWATCH_LINK` / `OverwatchLinkItem`.

- [ ] **Step 3: Write the item**

Create `src/main/java/dev/bastion/hub/OverwatchLinkItem.java`:

```java
package dev.bastion.hub;

import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.TurretInventory;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Overwatch Link (PLAN "Overwatch Link"): a Hub Connector that also lets the player take control of the turret from the
 * hub's screen. It links and clears exactly like a Hub Connector and fits the same Connector slot.
 */
public class OverwatchLinkItem extends HubConnectorItem {
    public OverwatchLinkItem(Properties properties) {
        super(properties);
    }

    /** Whether the turret's Connector slot holds an Overwatch Link (linked or not). */
    public static boolean fitted(TurretBaseBlockEntity turret) {
        return turret.inventory().getStackInSlot(TurretInventory.CONNECTOR).getItem() instanceof OverwatchLinkItem;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.bastion.overwatch_link").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.bastion.overwatch_link.aim").withStyle(ChatFormatting.GOLD));
    }
}
```

In `BastionItems.java`, right after the `HUB_CONNECTOR` registration (import `dev.bastion.hub.OverwatchLinkItem`):

```java
    /** A Hub Connector with remote control (PLAN "Overwatch Link"); same Connector slot. */
    public static final RegistryObject<OverwatchLinkItem> OVERWATCH_LINK = REGISTER.register("overwatch_link",
            () -> new OverwatchLinkItem(new Item.Properties().stacksTo(16).rarity(Rarity.RARE)));
```

In `BastionClient.onClientSetup`, after the `HUB_CONNECTOR` `ItemProperties.register` call:

```java
            ItemProperties.register(BastionItems.OVERWATCH_LINK.get(), Bastion.id("linked"),
                    (stack, level, entity, seed) -> HubConnectorItem.isLinked(stack) ? 1 : 0);
```

In `BastionJeiPlugin.registerRecipes`, after the `hub_connector` info line:

```java
        info(registration, "overwatch_link", BastionItems.OVERWATCH_LINK.get());
```

- [ ] **Step 4: Recipe, texture and models**

In `tools/gen_recipes.py`, add an entry to `HUB` after `"hub_connector"`:

```python
       "hub_connector": ({"copper_ingot": 2, "redstone": 1, "ender_pearl": 1}, 60, 1000, 4),
       "overwatch_link": ({B + "hub_connector": 1, "diamond": 2, "ender_eye": 1, "spyglass": 1, "observer": 1}, 200, 12000, 1)}
```

(Replace the closing `}` on the `hub_connector` line with `,` and close the dict after the new line.)

Run: `python3 tools/gen_recipes.py && git status --short src/main/resources/data`
Expected: exactly one new file, `?? src/main/resources/data/bastion/recipes/workstation/module_workstation/overwatch_link.json`. If any existing recipe shows as modified, stop and report it.

In `tools/gen_textures.py`:

1. After `def hub_connector(...)`, add:

```python
def overwatch_link(linked=False):
    """Overwatch Link item: the Hub Connector plug with a camera lens on its body and a tally light; the lens glows amber once linked."""
    img = hub_connector()
    rect(img, 5, 7, 6, 4, OUTLINE)  # lens housing
    rect(img, 6, 7, 4, 4, DARKSTEEL * 1.3)
    rect(img, 7, 8, 2, 2, rgb("FFB347") if linked else BORE)  # lens
    rect(img, 11, 6, 1, 1, rgb("FF3A30"))  # tally light
    return img
```

2. In `TEXTURES`, next to the `hub_connector` entries, add:

```python
    "item/overwatch_link.png": overwatch_link, "item/overwatch_link_linked.png": lambda: overwatch_link(linked=True),
```

Write only these two files; the full generator would rewrite every texture:

```bash
python3 - <<'EOF'
import sys; sys.path.insert(0, "tools")
import numpy as np
from PIL import Image
import gen_textures as g
for rel in ("item/overwatch_link.png", "item/overwatch_link_linked.png"):
    pixels = (np.clip(g.TEXTURES[rel](), 0, 1) * 255).round().astype(np.uint8)
    Image.fromarray(pixels, "RGBA").save(g.OUT / rel)
    print("wrote", rel)
EOF
```

Expected: `wrote item/overwatch_link.png`, `wrote item/overwatch_link_linked.png`.

Create `src/main/resources/assets/bastion/models/item/overwatch_link.json`:

```json
{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "bastion:item/overwatch_link"
  },
  "overrides": [
    {"predicate": {"bastion:linked": 1}, "model": "bastion:item/overwatch_link_linked"}
  ]
}
```

Create `src/main/resources/assets/bastion/models/item/overwatch_link_linked.json`:

```json
{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "bastion:item/overwatch_link_linked"
  }
}
```

- [ ] **Step 5: Lang**

```bash
python3 - <<'EOF'
import json
KEYS = {
 "en_us": {
  "item.bastion.overwatch_link": "Overwatch Link",
  "tooltip.bastion.overwatch_link": "Also lets you take control of the turret from the hub's screen",
  "tooltip.bastion.overwatch_link.aim": "Manual aim ignores turn speed: the barrel follows your mouse",
  "jei.bastion.info.overwatch_link": "A Hub Connector with a camera and fire control. Link it to a Hub Interface and put it in a turret's Connector slot. On the hub's map, shift-click that turret and press Take control: you look out from the turret, aim with the mouse and fire with attack. Manual aim ignores the turret's turn speed. Tesla Coils and Repulsor Domes can only be watched. Sneak to let go; taking damage, the hub going offline or the turret leaving the hub's reach also ends it."},
 "id_id": {
  "item.bastion.overwatch_link": "Overwatch Link",
  "tooltip.bastion.overwatch_link": "Turret juga bisa kamu kendalikan dari layar hub",
  "tooltip.bastion.overwatch_link.aim": "Bidikan manual mengabaikan turn speed: laras langsung ikut mouse",
  "jei.bastion.info.overwatch_link": "Hub Connector dengan kamera dan kendali tembak. Hubungkan ke Hub Interface lalu pasang di slot Connector turret. Di peta hub, Shift-klik turret itu lalu tekan Ambil Kendali: kamu melihat dari turret, membidik dengan mouse dan menembak dengan tombol serang. Bidikan manual mengabaikan turn speed turret. Tesla Coil dan Repulsor Dome hanya bisa dilihat. Jongkok untuk melepas; kena damage, hub offline atau turret keluar jangkauan hub juga melepasnya."}}
AFTER = "tooltip.bastion.hub_connector"  # new keys go after this key, in this order
for lang, keys in KEYS.items():
    p = f"src/main/resources/assets/bastion/lang/{lang}.json"
    d = json.load(open(p, encoding="utf-8"))
    assert not set(keys) & set(d), "key exists"
    out = {}
    for k, v in d.items():
        out[k] = v
        if k == AFTER: out.update(keys)
    assert len(out) == len(d) + len(keys)
    open(p, "w", encoding="utf-8").write(json.dumps(out, indent=2, ensure_ascii=False) + "\n")
print("ok")
EOF
```

Expected: `ok`. Then `git diff --stat src/main/resources/assets/bastion/lang` shows only insertions (4 per file).

Later tasks use the same snippet with their own `KEYS` and `AFTER`.

- [ ] **Step 6: Run the test and the build**

Run: `./gradlew build --offline > build/build-task1.log 2>&1; echo "exit $?"` → Expected: `exit 0`.
Run: `./gradlew runGameTestServer --offline > build/gt-task1.log 2>&1; echo "exit $?"; grep -E "required tests|failed!" build/gt-task1.log | head`
Expected: `All 197 required tests passed` (196 + 1).

---

### Task 2: Shared arc math and manual aim

**Files:**
- Create: `src/main/java/dev/bastion/weapon/Ballistics.java`, `src/main/java/dev/bastion/weapon/ManualAim.java`
- Modify:
  - `weapon/WeaponType.java`: new `manualAim`, `ballistic`
  - `weapon/FireContext.java`: new `manualPoint`
  - `weapon/types/MortarWeapon.java`
  - `turret/TurretBaseBlockEntity.java:623` and `gametest/TurretCombatTests.java:153,179`: add the extra `null` argument
- Test: `OverwatchTests` (add two tests)

**Interfaces:**
- Produces:
  - `Ballistics.Launch(Vec3 muzzle, Vec3 velocity, double ticks)` with `at(double t, double gravity) -> Vec3`
  - `Ballistics.highArc(Vec3 from, Vec3 to, double speed, double gravity) -> @Nullable Vec3`
  - `Ballistics.solve(TurretBaseBlockEntity, WeaponData, Vec3 to) -> @Nullable Launch`
  - `ManualAim.Aim(float yaw, float pitch, boolean onTarget)`
  - `ManualAim.solve(TurretBaseBlockEntity, WeaponType, StatSheet, Vec3 point) -> @Nullable Aim`
  - `WeaponType.manualAim(TurretBaseBlockEntity, Vec3 point, StatSheet) -> @Nullable Vec3` (default: `point`)
  - `WeaponType.ballistic() -> boolean` (default false; Mortar true)
  - `FireContext` gains a last component `@Nullable Vec3 manualPoint` (null for automatic shots)

- [ ] **Step 1: Write the failing tests** (add to `OverwatchTests`; imports `dev.bastion.registry.BastionWeaponTypes`, `dev.bastion.turret.LargeTurretBaseBlock`, `dev.bastion.turret.targeting.TargetSelector`, `dev.bastion.weapon.*` as used, `net.minecraft.world.phys.Vec3`)

```java
    private static final BlockPos LARGE = new BlockPos(2, 2, 5);

    static TurretBaseBlockEntity largeWith(GameTestHelper helper, net.minecraft.world.item.Item weapon) {
        LargeTurretBaseBlock.placeAt(helper.getLevel(), helper.absolutePos(LARGE), BastionBlocks.LARGE_TURRET_BASE.get());
        TurretBaseBlockEntity turret = (TurretBaseBlockEntity) helper.getBlockEntity(LARGE);
        turret.inventory().setStackInSlot(TurretInventory.WEAPON, new ItemStack(weapon));
        return turret;
    }

    static StatSheet stats(TurretBaseBlockEntity turret) {
        return StatSheet.of(turret.inventory().weaponData(), turret.tier(), turret.inventory().modifierEffect());
    }

    static WeaponType type(TurretBaseBlockEntity turret) {
        return BastionWeaponTypes.REGISTRY.get().getValue(turret.inventory().weaponData().type());
    }

    /** The launch Ballistics solves comes down on the point it was solved for; a spot past the shell's reach has none. */
    @GameTest(template = PIT, batch = "ballisticLaunchLandsOnTheAimPoint")
    public static void ballisticLaunchLandsOnTheAimPoint(GameTestHelper helper) {
        TurretBaseBlockEntity mortar = largeWith(helper, BastionItems.MORTAR_TURRET.get());
        WeaponData data = mortar.inventory().weaponData();
        Vec3 spot = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(15, 2, 6)));
        Ballistics.Launch launch = Ballistics.solve(mortar, data, spot);
        helper.assertTrue(launch != null, "no launch to a spot in range");
        Vec3 landing = launch.at(launch.ticks(), data.param("gravity"));
        helper.assertTrue(landing.distanceTo(spot) < 0.05, "lands at " + landing + ", aimed at " + spot);
        helper.assertTrue(Ballistics.solve(mortar, data, spot.add(80, 0, 0)) == null, "a launch past the shell's reach");
        helper.assertTrue(type(mortar).ballistic() && !type(pitGun(helper)).ballistic(), "ballistic flags");
        mortar.inventory().setStackInSlot(TurretInventory.WEAPON, ItemStack.EMPTY);
        helper.succeed();
    }

    /** A small Gun base standing in the PIT, away from the large base. */
    private static TurretBaseBlockEntity pitGun(GameTestHelper helper) {
        helper.setBlock(new BlockPos(20, 2, 9), BastionBlocks.TURRET_BASE.get());
        TurretBaseBlockEntity gun = (TurretBaseBlockEntity) helper.getBlockEntity(new BlockPos(20, 2, 9));
        gun.inventory().setStackInSlot(TurretInventory.WEAPON, new ItemStack(BastionItems.GUN_TURRET.get()));
        return gun;
    }

    /**
     * A straight-firing weapon points its barrel right at the spot, and is held at its pitch limit (off target) for a spot
     * straight down (Gun: min_pitch -35). A Mortar points along its arc, and has no aim inside min_range or past its range.
     */
    @GameTest(template = PIT, batch = "manualAimFollowsTheWeapon")
    public static void manualAimFollowsTheWeapon(GameTestHelper helper) {
        TurretBaseBlockEntity gun = pitGun(helper);
        WeaponData gunData = gun.inventory().weaponData();
        Vec3 pivot = gun.pivot(gunData), spot = pivot.add(-6, -1, -3);
        ManualAim.Aim aim = ManualAim.solve(gun, type(gun), stats(gun), spot);
        Vec3 dir = spot.subtract(pivot);
        helper.assertTrue(aim != null && aim.onTarget() && Math.abs(aim.yaw() - TargetSelector.yaw(dir)) < 1e-3
                && Math.abs(aim.pitch() + TargetSelector.elevation(dir)) < 1e-3, "gun aim " + aim);
        ManualAim.Aim down = ManualAim.solve(gun, type(gun), stats(gun), pivot.add(0, -10, 0.01));
        helper.assertTrue(down != null && !down.onTarget() && Math.abs(down.pitch() + gunData.aim().minPitch()) < 1e-3, "straight down " + down);
        TurretBaseBlockEntity mortar = largeWith(helper, BastionItems.MORTAR_TURRET.get());
        Vec3 mortarPivot = mortar.pivot(mortar.inventory().weaponData());
        helper.assertTrue(ManualAim.solve(mortar, type(mortar), stats(mortar), mortarPivot.add(3, -2, 0)) == null, "aims inside min_range");
        helper.assertTrue(ManualAim.solve(mortar, type(mortar), stats(mortar), mortarPivot.add(60, -2, 0)) == null, "aims past its range");
        ManualAim.Aim lob = ManualAim.solve(mortar, type(mortar), stats(mortar), Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(15, 2, 6))));
        helper.assertTrue(lob != null && lob.onTarget() && -lob.pitch() >= mortar.inventory().weaponData().aim().minPitch(), "mortar aim " + lob);
        gun.inventory().setStackInSlot(TurretInventory.WEAPON, ItemStack.EMPTY);
        mortar.inventory().setStackInSlot(TurretInventory.WEAPON, ItemStack.EMPTY);
        helper.succeed();
    }
```

- [ ] **Step 2: Run and watch it fail**

Run: `./gradlew runGameTestServer --offline > build/gt-task2-red.log 2>&1; echo "exit $?"; grep -E "error:" build/gt-task2-red.log | head`
Expected: the compile fails: `Ballistics`, `ManualAim`, `ballistic()` not found.

- [ ] **Step 3: Write `Ballistics`** (math moved unchanged from `MortarWeapon.highArc` / `solve`)

```java
package dev.bastion.weapon;

import dev.bastion.turret.TurretBaseBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Shell arcs (PLAN "Overwatch Link"): the high-arc launch of weapons with shell_speed and gravity params (Mortar). The
 * server aims and fires with it and the Overwatch view draws the same arc, so the drawn path is where the shell flies.
 */
public final class Ballistics {
    private Ballistics() {
    }

    /** A launch from {@code muzzle} at {@code velocity} that comes down on the aim point after {@code ticks}. */
    public record Launch(Vec3 muzzle, Vec3 velocity, double ticks) {
        /**
         * Where the shell is {@code t} ticks after launch. TurretRocketEntity adds half the gravity before each move and half
         * after, which lands exactly on this parabola at every whole tick.
         */
        public Vec3 at(double t, double gravity) {
            return muzzle.add(velocity.scale(t)).add(0, -gravity * t * t / 2, 0);
        }
    }

    /** Launch velocity of the steep solution of the ballistic equation, or null when out of reach. */
    @Nullable
    public static Vec3 highArc(Vec3 from, Vec3 to, double speed, double gravity) {
        double dx = to.x - from.x, dz = to.z - from.z, dy = to.y - from.y, h = Math.sqrt(dx * dx + dz * dz), v2 = speed * speed;
        double disc = v2 * v2 - gravity * (gravity * h * h + 2 * dy * v2);
        if (disc < 0 || h < 1e-3) return null;
        double angle = Math.atan((v2 + Math.sqrt(disc)) / (gravity * h));
        double horizontal = Math.cos(angle) * speed;
        return new Vec3(dx / h * horizontal, Math.sin(angle) * speed, dz / h * horizontal);
    }

    /**
     * The high arc from this turret's muzzle to {@code to}, within the barrel's elevation limits, or null when none
     * reaches. The muzzle moves with the elevation, so the solve repeats from where the last one put it.
     */
    @Nullable
    public static Launch solve(TurretBaseBlockEntity turret, WeaponData data, Vec3 to) {
        double speed = data.param("shell_speed"), gravity = data.param("gravity");
        Vec3 pivot = turret.pivot(data), from = pivot, velocity = null;
        for (int i = 0; i < 3; i++) {
            velocity = highArc(from, to, speed, gravity);
            if (velocity == null) return null;
            from = pivot.add(velocity.normalize().scale(data.aim().muzzleLength()));
        }
        double elevation = Math.toDegrees(Math.atan2(velocity.y, velocity.horizontalDistance()));
        if (elevation < data.aim().minPitch() || elevation > data.aim().maxPitch()) return null;
        return new Launch(from, velocity, to.subtract(from).horizontalDistance() / Math.max(velocity.horizontalDistance(), 1e-3));
    }
}
```

- [ ] **Step 4: Write `ManualAim` and the `WeaponType` hooks**

```java
package dev.bastion.weapon;

import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.targeting.TargetSelector;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Overwatch aim (PLAN "Overwatch Link"): the barrel angles for the controller's aim point, the same on server and client. */
public final class ManualAim {
    private ManualAim() {
    }

    /** Barrel yaw and pitch (turret frame, as TurretBaseBlockEntity yaw()/pitch()); onTarget false when held at a pitch limit. */
    public record Aim(float yaw, float pitch, boolean onTarget) {
    }

    /** How the turret points to send a shot at {@code point}; null when the weapon cannot reach it (Mortar: too close or too far). */
    @Nullable
    public static Aim solve(TurretBaseBlockEntity turret, WeaponType type, StatSheet stats, Vec3 point) {
        Vec3 toward = type.manualAim(turret, point, stats);
        if (toward == null) return null;
        WeaponData data = stats.data();
        Vec3 local = turret.toLocal(toward.subtract(turret.pivot(data)));
        float elevation = TargetSelector.elevation(local);
        float held = Mth.clamp(elevation, data.aim().minPitch(), data.aim().maxPitch());
        return new Aim(TargetSelector.yaw(local), -held, Math.abs(held - elevation) < 0.5f);
    }
}
```

In `WeaponType.java`, after `aimPoint(...)`:

```java
    /**
     * Overwatch (PLAN "Overwatch Link"): where the barrel points to send a shot at {@code point}, the spot under the
     * controller's crosshair; null when this weapon cannot reach it. Straight-firing weapons point right at it.
     */
    @Nullable
    public Vec3 manualAim(TurretBaseBlockEntity turret, Vec3 point, StatSheet stats) {
        return point;
    }

    /** Weapons that lob shells on an arc (Mortar): the Overwatch view draws their trajectory (Ballistics). */
    public boolean ballistic() {
        return false;
    }
```

- [ ] **Step 5: `FireContext.manualPoint` and the Mortar on `Ballistics`**

`FireContext.java` becomes:

```java
/**
 * Everything a weapon needs for one shot. {@code seed} drives spread so clients can rebuild the same pattern;
 * {@code precisionLock} is true for the one locked shot per target (PLAN 7.1). {@code manualPoint} is non-null only for a
 * shot an Overwatch controller fired: the spot under their crosshair (PLAN "Overwatch Link").
 */
public record FireContext(ServerLevel level, TurretBaseBlockEntity turret, Vec3 muzzle, Vec3 direction,
                          Entity target, StatSheet stats, WeaponState state, long seed, boolean precisionLock,
                          @Nullable Vec3 manualPoint) {
}
```

(Add `import org.jetbrains.annotations.Nullable;`.)

Add a final `null` argument to the three constructor calls: `TurretBaseBlockEntity.java:623`, `TurretCombatTests.java:153` and `:179`.

In `MortarWeapon.java`:
- Delete the private `record Launch`, `solve(...)` and `highArc(...)`. Import `dev.bastion.weapon.Ballistics`.
- In `sight(...)`, use `Ballistics.Launch launch = Ballistics.solve(turret, stats.data(), feet);`.
- `arcClear` takes `Ballistics.Launch` and reads `launch.muzzle()`, `launch.velocity()`, `launch.ticks()`, using `launch.at(t, gravity)` for `next`.
- `aimPoint`:

```java
    @Override
    public Vec3 aimPoint(TurretBaseBlockEntity turret, LivingEntity target, Vec3 point, StatSheet stats) {
        Ballistics.Launch launch = Ballistics.solve(turret, stats.data(), point);
        return launch == null ? point : turret.pivot(stats.data()).add(launch.velocity().normalize().scale(8));
    }

    /** Overwatch: yaw toward the spot, elevation from the high arc; no aim inside min_range or past the range. */
    @Override
    @Nullable
    public Vec3 manualAim(TurretBaseBlockEntity turret, Vec3 point, StatSheet stats) {
        WeaponData data = stats.data();
        double distance = point.subtract(turret.pivot(data)).horizontalDistance();
        if (distance > stats.range() || distance < data.params().getOrDefault("min_range", 0f)) return null;
        Ballistics.Launch launch = Ballistics.solve(turret, data, point);
        return launch == null ? null : turret.pivot(data).add(launch.velocity().normalize().scale(8));
    }

    @Override
    public boolean ballistic() {
        return true;
    }
```

In `onFire`, replace the velocity line with:

```java
        // At the creature it was fired at, else (Overwatch) the spot under the controller's crosshair.
        Vec3 to = context.target() != null ? context.target().position() : context.manualPoint();
        Vec3 velocity = to != null ? Ballistics.highArc(context.muzzle(), to, speed, gravity) : null;
```

`arcClear` after the change:

```java
    /** Nothing solid on the arc, in steps of two ticks, up to just before it comes down on the target. */
    private static boolean arcClear(Level level, Ballistics.Launch launch, double gravity) {
        Vec3 last = launch.muzzle();
        for (double t = 2; t < launch.ticks() - 1.5; t += 2) {
            Vec3 next = launch.at(t, gravity);
            if (!LineOfSight.clear(level, last, next)) return false;
            last = next;
        }
        return true;
    }
```

- [ ] **Step 6: Run the tests and the build**

Run: `./gradlew build --offline > build/build-task2.log 2>&1; echo "exit $?"` → `exit 0`.
Run: `./gradlew runGameTestServer --offline > build/gt-task2.log 2>&1; echo "exit $?"; grep -E "required tests|failed!" build/gt-task2.log | head`
Expected: `All 199 required tests passed`. The Mortar tests `mortarShellsOverWall` and `mortarIgnoresFlyersAndCloseTargets` still pass, which shows the move kept the old behaviour.

---

### Task 3: Manual control on the turret

**Files:**
- Create: `src/main/java/dev/bastion/turret/ManualControl.java`
- Modify:
  - `turret/TurretBaseBlockEntity.java`
  - `weapon/Bullets.java:221`
  - `turret/targeting/TargetSelector.java:54`
  - `projectile/TurretRocketEntity.java` (`affects`, `steer`, new `retargets`)
  - `weapon/types/MissileLauncherWeapon.java`
- Test: `OverwatchTests` (add 9 tests)

**Interfaces:**
- Consumes: `ManualAim.solve`, `FireContext.manualPoint` (Task 2).
- Produces:
  - `ManualControl(UUID player, String name)` with:
    - `player()`, `name()`
    - `input(Vec3 point, int entityId, boolean trigger, long gameTime)`
    - `@Nullable point()`, `entityId()`
    - `trigger(long gameTime)`
    - `INPUT_TIMEOUT = 20`
  - `TurretBaseBlockEntity`:
    - `@Nullable manual()`
    - `setManual(@Nullable ManualControl)`
    - `steering() -> boolean`
    - `wouldHit(LivingEntity) -> boolean`
    - `@Nullable clientController()`
  - `TurretRocketEntity.retargets(boolean) -> TurretRocketEntity`

- [ ] **Step 1: Write the failing tests** (add to `OverwatchTests`; imports `net.minecraft.util.Mth`, `java.util.UUID`, `com.mojang.authlib.GameProfile`, `net.minecraftforge.common.util.FakePlayerFactory`, `net.minecraft.world.entity.EntityType`, `net.minecraft.world.entity.LivingEntity`, `net.minecraft.world.level.block.BaseFireBlock`, `net.minecraft.world.level.block.Blocks`, `net.minecraftforge.common.capabilities.ForgeCapabilities`, `net.minecraft.core.Direction`, `dev.bastion.turret.ManualControl`, `dev.bastion.turret.TurretState`, `dev.bastion.weapon.types.MissileLauncherWeapon`, `dev.bastion.compat.BlockInfo`)

```java
    private static final BlockPos BASE = new BlockPos(2, 2, 3), FAR = new BlockPos(9, 2, 3);

    private static TurretBaseBlockEntity small(GameTestHelper helper, net.minecraft.world.item.Item weapon, net.minecraft.world.item.Item ammo, int count) {
        helper.setBlock(BASE, BastionBlocks.TURRET_BASE.get());
        TurretBaseBlockEntity turret = (TurretBaseBlockEntity) helper.getBlockEntity(BASE);
        turret.inventory().setStackInSlot(TurretInventory.WEAPON, new ItemStack(weapon));
        if (count > 0) turret.inventory().setStackInSlot(TurretInventory.AMMO_START, new ItemStack(ammo, count));
        return turret;
    }

    private static int ammo(TurretBaseBlockEntity turret) {
        return turret.inventory().getStackInSlot(TurretInventory.AMMO_START).getCount();
    }

    private static void release(TurretBaseBlockEntity turret) {
        turret.setManual(null);
        turret.inventory().setStackInSlot(TurretInventory.WEAPON, ItemStack.EMPTY);
    }

    private static boolean hurt(LivingEntity entity) {
        return entity.getHealth() < entity.getMaxHealth();
    }

    /** A cow (which the turret's filter never shoots) under the crosshair takes the controller's shots. */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "manualFireHitsWhatTheCrosshairIsOn")
    public static void manualFireHitsWhatTheCrosshairIsOn(GameTestHelper helper) {
        TurretBaseBlockEntity turret = small(helper, BastionItems.GUN_TURRET.get(), BastionItems.KINETIC_ROUNDS.get(), 10);
        LivingEntity cow = helper.spawnWithNoFreeWill(EntityType.COW, FAR);
        ManualControl control = new ManualControl(UUID.randomUUID(), "Tester");
        turret.setManual(control);
        helper.onEachTick(() -> control.input(cow.getBoundingBox().getCenter(), cow.getId(), true, helper.getLevel().getGameTime()));
        helper.succeedWhen(() -> {
            helper.assertTrue(hurt(cow), "the cow was not hit, state " + turret.state());
            helper.assertTrue(ammo(turret) < 10, "no ammo spent");
            release(turret);
        });
    }

    /**
     * Steered, it hurts anything but the owner and the controller, players only with PvP on; unsteered it follows its filter.
     * Swapping to a weapon that never turns (Tesla) stops the steering.
     */
    @GameTest(template = ARENA, batch = "manualShotsSpareOwnerAndController")
    public static void manualShotsSpareOwnerAndController(GameTestHelper helper) {
        TurretBaseBlockEntity turret = small(helper, BastionItems.GUN_TURRET.get(), BastionItems.KINETIC_ROUNDS.get(), 0);
        var owner = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "owner"));
        var controller = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "controller"));
        var other = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "other"));
        turret.setOwner(owner.getUUID());
        LivingEntity cow = helper.spawnWithNoFreeWill(EntityType.COW, FAR);
        helper.assertTrue(!turret.steering() && !turret.wouldHit(cow), "the filter shoots cows");
        turret.setManual(new ManualControl(controller.getUUID(), "controller"));
        helper.assertTrue(turret.steering() && turret.wouldHit(cow), "steered, it spares the cow");
        helper.assertTrue(!turret.wouldHit(owner) && !turret.wouldHit(controller), "it hits the owner or the controller");
        var server = helper.getLevel().getServer();
        boolean pvp = server.isPvpAllowed();
        server.setPvpAllowed(true);
        boolean withPvp = turret.wouldHit(other);
        server.setPvpAllowed(false);
        boolean withoutPvp = turret.wouldHit(other);
        server.setPvpAllowed(pvp);
        helper.assertTrue(withPvp && !withoutPvp, "PvP on " + withPvp + ", off " + withoutPvp);
        turret.inventory().setStackInSlot(TurretInventory.WEAPON, new ItemStack(BastionItems.TESLA_TURRET.get()));
        helper.assertTrue(!turret.steering() && !turret.wouldHit(cow), "a Tesla is steered");
        release(turret);
        helper.succeed();
    }

    /** The barrel lands on the aim in one tick (no servo), and stops at its pitch limit for a spot straight down. */
    @GameTest(template = ARENA, batch = "barrelSnapsToTheAim")
    public static void barrelSnapsToTheAim(GameTestHelper helper) {
        TurretBaseBlockEntity turret = small(helper, BastionItems.GUN_TURRET.get(), BastionItems.KINETIC_ROUNDS.get(), 0);
        ManualControl control = new ManualControl(UUID.randomUUID(), "Tester");
        turret.setManual(control);
        WeaponData data = turret.inventory().weaponData();
        Vec3 pivot = turret.pivot(data), side = pivot.add(1, -0.5, 4);
        control.input(side, -1, false, helper.getLevel().getGameTime());
        helper.runAtTickTime(2, () -> {
            ManualAim.Aim aim = ManualAim.solve(turret, type(turret), stats(turret), side);
            helper.assertTrue(Math.abs(Mth.wrapDegrees(turret.yaw() - aim.yaw())) < 1e-3 && Math.abs(turret.pitch() - aim.pitch()) < 1e-3,
                    "barrel at " + turret.yaw() + "/" + turret.pitch() + ", aim " + aim);
            control.input(pivot.add(0, -10, 0.01), -1, false, helper.getLevel().getGameTime());
        });
        helper.runAtTickTime(4, () -> {
            helper.assertTrue(Math.abs(turret.pitch() + data.aim().minPitch()) < 1e-3, "pitch " + turret.pitch() + " past its limit");
            release(turret);
            helper.succeed();
        });
    }

    /** A Sniper charge stops when the trigger is let go: 10 + 25 held ticks never add up to a shot. */
    @GameTest(template = ARENA, timeoutTicks = 120, batch = "lettingGoCancelsTheCharge")
    public static void lettingGoCancelsTheCharge(GameTestHelper helper) {
        TurretBaseBlockEntity turret = small(helper, BastionItems.SNIPER_TURRET.get(), BastionItems.SNIPER_ROUNDS.get(), 5);
        ManualControl control = new ManualControl(UUID.randomUUID(), "Tester");
        turret.setManual(control);
        Vec3 point = Vec3.atCenterOf(helper.absolutePos(FAR));
        helper.onEachTick(() -> {
            long t = helper.getTick();
            control.input(point, -1, t < 10 || t >= 15, helper.getLevel().getGameTime());
        });
        helper.runAtTickTime(8, () -> helper.assertTrue(turret.state() == TurretState.CHARGING, "not charging: " + turret.state()));
        helper.runAtTickTime(13, () -> helper.assertTrue(turret.state() == TurretState.IDLE, "let go but " + turret.state()));
        helper.runAtTickTime(40, () -> helper.assertTrue(ammo(turret) == 5, "fired at tick 40: the charge carried over"));
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getTick() > 40, "waiting for tick 40");
            helper.assertTrue(ammo(turret) < 5, "never fired after a full charge, state " + turret.state());
            release(turret);
        });
    }

    /** One trigger input, then silence: the turret fires, then stops once the input is INPUT_TIMEOUT ticks old. */
    @GameTest(template = ARENA, timeoutTicks = 80, batch = "triggerLetsGoWithoutInput")
    public static void triggerLetsGoWithoutInput(GameTestHelper helper) {
        TurretBaseBlockEntity turret = small(helper, BastionItems.GUN_TURRET.get(), BastionItems.KINETIC_ROUNDS.get(), 20);
        ManualControl control = new ManualControl(UUID.randomUUID(), "Tester");
        turret.setManual(control);
        control.input(Vec3.atCenterOf(helper.absolutePos(FAR)), -1, true, helper.getLevel().getGameTime());
        int[] at30 = new int[1];
        helper.runAtTickTime(30, () -> at30[0] = ammo(turret));
        helper.runAtTickTime(50, () -> {
            helper.assertTrue(at30[0] < 20, "never fired");
            helper.assertTrue(ammo(turret) == at30[0] && turret.state() == TurretState.IDLE,
                    "still firing without input: " + ammo(turret) + " left, " + turret.state());
            release(turret);
            helper.succeed();
        });
    }

    /** A controlled Mortar shells the spot under the crosshair: the fire patch lands there. */
    @GameTest(template = PIT, timeoutTicks = 300, batch = "manualMortarShellsTheSpot")
    public static void manualMortarShellsTheSpot(GameTestHelper helper) {
        TurretBaseBlockEntity mortar = largeWith(helper, BastionItems.MORTAR_TURRET.get());
        mortar.inventory().setStackInSlot(TurretInventory.AMMO_START, new ItemStack(BastionItems.MORTAR_SHELLS.get(), 4));
        for (int x = 13; x <= 17; x++) for (int z = 4; z <= 8; z++) helper.setBlock(new BlockPos(x, 1, z), Blocks.OAK_PLANKS);
        ManualControl control = new ManualControl(UUID.randomUUID(), "Tester");
        mortar.setManual(control);
        Vec3 spot = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(15, 2, 6)));
        helper.onEachTick(() -> control.input(spot, -1, true, helper.getLevel().getGameTime()));
        helper.succeedWhen(() -> {
            boolean fire = false;
            for (int x = 14; x <= 16; x++) for (int z = 5; z <= 7; z++) {
                fire |= helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(x, 2, z))).getBlock() instanceof BaseFireBlock;
            }
            helper.assertTrue(fire, "no fire at the spot yet, state " + mortar.state());
            release(mortar);
        });
    }

    /** Inside min_range the controlled Mortar never fires, and stays IDLE with the trigger held. */
    @GameTest(template = PIT, timeoutTicks = 100, batch = "manualMortarRefusesTooClose")
    public static void manualMortarRefusesTooClose(GameTestHelper helper) {
        TurretBaseBlockEntity mortar = largeWith(helper, BastionItems.MORTAR_TURRET.get());
        mortar.inventory().setStackInSlot(TurretInventory.AMMO_START, new ItemStack(BastionItems.MORTAR_SHELLS.get(), 4));
        ManualControl control = new ManualControl(UUID.randomUUID(), "Tester");
        mortar.setManual(control);
        Vec3 close = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(6, 2, 6)));
        helper.onEachTick(() -> control.input(close, -1, true, helper.getLevel().getGameTime()));
        helper.runAtTickTime(60, () -> {
            helper.assertTrue(mortar.inventory().getStackInSlot(TurretInventory.AMMO_START).getCount() == 4, "it fired inside min_range");
            helper.assertTrue(mortar.state() == TurretState.IDLE, "expected IDLE, got " + mortar.state());
            release(mortar);
            helper.succeed();
        });
    }

    /** The Missile Launcher locks the creature under the crosshair: the cow is hit and the husk (its filter's pick) is not. */
    @GameTest(template = RANGE, timeoutTicks = 300, batch = "missileFollowsTheCrosshairLock")
    public static void missileFollowsTheCrosshairLock(GameTestHelper helper) {
        TurretBaseBlockEntity launcher = largeWith(helper, BastionItems.MISSILE_LAUNCHER_TURRET.get());
        launcher.inventory().setStackInSlot(TurretInventory.AMMO_START, new ItemStack(BastionItems.CREATIVE_AMMO.get()));
        launcher.weaponState().tubes = MissileLauncherWeapon.ALL_TUBES;
        LivingEntity cow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(19, 2, 3));
        LivingEntity husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(19, 2, 10));
        ManualControl control = new ManualControl(UUID.randomUUID(), "Tester");
        launcher.setManual(control);
        helper.onEachTick(() -> control.input(cow.getBoundingBox().getCenter(), cow.getId(), true, helper.getLevel().getGameTime()));
        helper.succeedWhen(() -> {
            helper.assertTrue(hurt(cow), "the locked cow was not hit, state " + launcher.state());
            helper.assertTrue(!hurt(husk), "the husk was hit");
            release(launcher);
        });
    }

    /** A Tesla never turns: under control it still picks its own targets (the husk) and keeps to its filter (not the cow). */
    @GameTest(template = RANGE, timeoutTicks = 200, batch = "teslaStaysAutomaticUnderControl")
    public static void teslaStaysAutomaticUnderControl(GameTestHelper helper) {
        TurretBaseBlockEntity tesla = largeWith(helper, BastionItems.TESLA_TURRET.get());
        var energy = helper.getBlockEntity(LARGE).getCapability(ForgeCapabilities.ENERGY, Direction.UP).orElseThrow(IllegalStateException::new);
        while (energy.receiveEnergy(Integer.MAX_VALUE, false) > 0) ;
        LivingEntity husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(9, 2, 5));
        LivingEntity cow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(9, 2, 8));
        tesla.setManual(new ManualControl(UUID.randomUUID(), "Tester"));
        helper.assertTrue(!tesla.steering(), "a Tesla is steered");
        helper.succeedWhen(() -> {
            helper.assertTrue(hurt(husk), "the Tesla stopped firing on its own");
            helper.assertTrue(!hurt(cow), "the Tesla zapped the cow");
            release(tesla);
        });
    }
```

- [ ] **Step 2: Run and watch it fail**

Run: `./gradlew runGameTestServer --offline > build/gt-task3-red.log 2>&1; echo "exit $?"; grep -E "error:" build/gt-task3-red.log | head`
Expected: the compile fails: `ManualControl`, `setManual`, `steering`, `wouldHit` not found.

- [ ] **Step 3: `ManualControl`**

```java
package dev.bastion.turret;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** The player steering a turret through Overwatch and their latest input (PLAN "Overwatch Link"). Server only, never saved. */
public final class ManualControl {
    /** Input older than this (ticks) lets go of the trigger: a client that stopped sending must not keep the turret firing. */
    public static final int INPUT_TIMEOUT = 20;
    private final UUID player;
    private final String name;
    @Nullable
    private Vec3 point;
    private int entityId = -1;
    private boolean trigger;
    private long inputTime = Long.MIN_VALUE / 2;

    public ManualControl(UUID player, String name) {
        this.player = player;
        this.name = name;
    }

    public UUID player() {
        return player;
    }

    public String name() {
        return name;
    }

    /** One tick of input: the spot under the crosshair, the creature on it (-1: none), the trigger. */
    public void input(Vec3 point, int entityId, boolean trigger, long gameTime) {
        this.point = point;
        this.entityId = entityId;
        this.trigger = trigger;
        this.inputTime = gameTime;
    }

    @Nullable
    public Vec3 point() {
        return point;
    }

    public int entityId() {
        return entityId;
    }

    public boolean trigger(long gameTime) {
        return trigger && gameTime - inputTime <= INPUT_TIMEOUT;
    }
}
```

- [ ] **Step 4: The turret side** (`TurretBaseBlockEntity.java`)

Fields (after `weaponState`, line ~102):

```java
    /** Overwatch: who steers this turret and their input (PLAN "Overwatch Link"); server only, never saved. */
    @Nullable
    private ManualControl manual;
```

Client mirror (after `clientTubes`, line ~125):

```java
    /** Overwatch: the name of the player steering it, from the update tag; null when nobody is. */
    @Nullable
    private String clientController;
```

Methods, in the "ownership & settings" section after `canBeDamagedBy`:

```java
    // --- Overwatch (PLAN "Overwatch Link") ------------------------------------------------------

    @Nullable
    public ManualControl manual() {
        return manual;
    }

    /** Server (OverwatchSessions): who steers this turret now, or null; clients learn the name through the update tag. */
    public void setManual(@Nullable ManualControl control) {
        manual = control;
        target = null;
        chargeTimer = 0;
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    /** Under control with a weapon that turns: the controller aims and fires it. Watch-only weapons (turn_speed 0) stay automatic. */
    public boolean steering() {
        WeaponData data = inventory.weaponData();
        return manual != null && data != null && data.aim().turnSpeed() > 0;
    }

    /**
     * Whom this turret's shots may hurt: its targeting filter, or while steered every living thing but the owner and the
     * controller, other players only with PvP on.
     */
    public boolean wouldHit(LivingEntity entity) {
        if (!steering()) return filter.test(entity, owner);
        if (!(entity instanceof Player player)) return true;
        UUID id = player.getUUID();
        return !id.equals(owner) && !id.equals(manual.player()) && level != null && level.getServer() != null && level.getServer().isPvpAllowed();
    }

    /** Client: the name of whoever steers it, or null. */
    @Nullable
    public String clientController() {
        return clientController;
    }
```

In `tickServer`, right after the OVERHEAT block and before `updateTarget(stats, type);`:

```java
        if (steering()) {
            tickManual(level, stats, type);
            return;
        }
```

New method after `tickServer`:

```java
    /**
     * Overwatch: the barrel snaps to the controller's aim (turn speed ignored); the trigger runs the usual cycle (cooldown,
     * ready, charge, fire). Letting go cancels a charge; no aim (Mortar: out of reach) never fires.
     */
    private void tickManual(ServerLevel level, StatSheet stats, WeaponType type) {
        target = null;
        yawVelocity = pitchVelocity = 0;
        aimLockTicks = 0;
        ManualAim.Aim aim = manual.point() == null ? null : ManualAim.solve(this, type, stats, manual.point());
        if (aim != null) {
            yaw = Mth.wrapDegrees(aim.yaw());
            pitch = aim.pitch();
        }
        boolean armed = hasAmmo(stats) && !type.outOfAmmo(this, weaponState);
        boolean trigger = manual.trigger(level.getGameTime());
        type.tick(this, weaponState, stats, trigger && armed);
        if (!armed) {
            chargeTimer = 0;
            state = TurretState.NO_AMMO;
            return;
        }
        if (!trigger || aim == null) {
            chargeTimer = 0;
            fireCooldown = Math.max(fireCooldown, 0);
            state = TurretState.IDLE;
            return;
        }
        if (chargeTimer == 0) {
            if (fireCooldown > 0) {
                state = TurretState.COOLDOWN;
                return;
            }
            if (!type.ready(stats, weaponState)) {
                fireCooldown = Math.max(fireCooldown, 0);
                state = TurretState.AIMING;
                return;
            }
        }
        int charge = type.chargeTicks(stats);
        if (charge > 0 && chargeTimer < charge) {
            chargeTimer++;
            state = TurretState.CHARGING;
            return;
        }
        chargeTimer = 0;
        fire(level, stats, type);
    }

    /** The creature the controller has locked, while it can still be hit and is in range; else none. */
    @Nullable
    private LivingEntity manualTarget(StatSheet stats) {
        if (!(level.getEntity(manual.entityId()) instanceof LivingEntity living) || !living.isAlive() || living instanceof TurretHitboxEntity) return null;
        return living.distanceToSqr(pivot(stats.data())) <= stats.range() * stats.range() && wouldHit(living) ? living : null;
    }
```

In `fire(...)`: Precision Lock is off when steered, and the target and point come from the controller.

```java
        boolean steered = steering();
        boolean lock = !steered && data.params().containsKey("precision_lock_ticks") && !weaponState.lockSpent
                && weaponState.lockTicks >= data.param("precision_lock_ticks");
        ...
        type.onFire(new FireContext(level, this, muzzle, direction, steered ? manualTarget(stats) : target, stats, weaponState,
                level.random.nextLong(), lock, steered ? manual.point() : null));
```

`getUpdateTag()` adds the controller's name for clients:

```java
        if (manual != null) tag.putString("Controller", manual.name());
```

`load(...)`, inside the existing `if (tag.contains("State")) { ... }` client block:

```java
            clientController = tag.contains("Controller") ? tag.getString("Controller") : null;
```

In `tickClient()`, the idle sweep does not run on a steered turret, so viewers see the controller's aim:

```java
        if (clientState == TurretState.IDLE && clientController == null && (data == null || data.aim().turnSpeed() > 0)) {
```

Imports to add: `dev.bastion.weapon.ManualAim`, `net.minecraft.world.entity.player.Player` (if not present). `UUID` is already imported.

- [ ] **Step 5: Route the hit rule through `wouldHit`**

- `Bullets.filtered` (line 221): replace `turret.filter().test(living, turret.owner())` with `turret.wouldHit(living)`.
- `TargetSelector.isCandidate` (line 54): replace `turret.filter().test(entity, turret.owner())` with `turret.wouldHit(entity)`.
- `TurretRocketEntity.affects` (line 218): `return base == null ? entity instanceof Enemy : base.wouldHit(living);`.

- [ ] **Step 6: Missiles keep to the controller's lock**

In `TurretRocketEntity`:

```java
    /** False for a missile an Overwatch controller fired: it keeps to its lock, or flies straight without one. */
    private boolean retargets = true;

    public TurretRocketEntity retargets(boolean retargets) {
        this.retargets = retargets;
        return this;
    }
```

In `steer()`, change the retarget line:

```java
            if (homingTarget == null || !homingTarget.isAlive()) {
                homingTarget = retargets ? retarget() : null;
                targetVelocity = Vec3.ZERO;
            }
```

In `MissileLauncherWeapon.onFire`, a controlled salvo does not spread over the filter's targets:

```java
            List<LivingEntity> targets = context.manualPoint() != null ? List.of()
                    : TargetSelector.findAll(context.turret(), context.stats(), this, TUBES);
```

In `launch(...)`, chain `.retargets(context.manualPoint() == null)` after `.homing(...)`.

- [ ] **Step 7: Run the tests and the build**

Run: `./gradlew build --offline > build/build-task3.log 2>&1; echo "exit $?"` → `exit 0`.
Run: `./gradlew runGameTestServer --offline > build/gt-task3.log 2>&1; echo "exit $?"; grep -E "required tests|failed!" build/gt-task3.log | head`
Expected: `All 208 required tests passed`. All old combat, Mortar, Missile and Tesla tests still pass; the filter path is unchanged when nobody steers.

---

### Task 4: Hub sessions and packets

**Files:**
- Create:
  - `hub/OverwatchSessions.java`
  - `network/OverwatchRequest.java`
  - `network/OverwatchInput.java`
  - `network/OverwatchView.java`
  - `network/OverwatchStatus.java`
  - `client/overwatch/OverwatchClient.java` (state only; Task 6 adds the camera)
- Modify:
  - `network/BastionNetwork.java` (4 registrations, `PROTOCOL = "12"`)
  - `client/ClientPacketHandler.java`
  - `menu/ControlHubMenu.java` (`Entry` + `linked`, `controller`)
  - both lang files (messages)
- Test: `OverwatchTests` (add 6 tests + 2 helpers)

**Interfaces:**
- Consumes:
  - `OverwatchLinkItem.fitted` (Task 1)
  - `ManualControl`, `setManual`, `manual()` (Task 3)
- Produces:
  - `OverwatchSessions.start(ServerPlayer, BlockPos hub, BlockPos turret) -> @Nullable Component` (refusal message, null = started)
  - `OverwatchSessions.stop(ServerPlayer)`
  - `OverwatchSessions.cycle(ServerPlayer, int step)`
  - `OverwatchSessions.input(ServerPlayer, BlockPos turret, Vec3 point, int entity, boolean trigger)`
  - `OverwatchSessions.controlled(ServerPlayer) -> @Nullable BlockPos`
  - `OverwatchRequest.start(BlockPos) / cycle(int) / exit()`
  - `OverwatchInput(BlockPos turret, Vec3 point, int entity, boolean trigger)`
  - `OverwatchView(@Nullable BlockPos turret, @Nullable BlockPos hub)`, `OverwatchView.NONE`
  - `OverwatchStatus(ControlHubMenu.Entry entry)`
  - `ControlHubMenu.Entry.linked()`, `.controller()` (String, "" for none)
  - `OverwatchClient.begin(BlockPos turret, BlockPos hub)`, `end()`, `status(Entry)`, `active()`, `turretPos()`, `hubPos()`, `status()`

- [ ] **Step 1: Write the failing tests** (add to `OverwatchTests`; imports `dev.bastion.hub.OverwatchSessions`, `dev.bastion.menu.ControlHubMenu`, `net.minecraft.network.chat.Component`, `net.minecraft.network.chat.contents.TranslatableContents`, `net.minecraft.server.level.ServerLevel`, `net.minecraftforge.common.MinecraftForge`, `net.minecraftforge.common.util.FakePlayer`, `net.minecraftforge.event.entity.living.LivingDamageEvent`, `net.minecraftforge.event.entity.player.PlayerEvent`, `java.util.List`)

```java
    /** Yard (ControlHubTests): the hub's Interface and where its user stands. */
    private static final BlockPos IFACE = new BlockPos(15, 2, 14), STAND = new BlockPos(15, 2, 13);

    /** A corner of the satellite's chunk away from the hub cube: inside a T1 hub's reach. */
    private static BlockPos corner(HubInterfaceBlockEntity hub) {
        BlockPos center = hub.center();
        int x = (center.getX() & 15) < 8 ? (center.getX() | 15) - 3 : (center.getX() & ~15) + 1;
        int z = (center.getZ() & 15) < 8 ? (center.getZ() | 15) - 3 : (center.getZ() & ~15) + 1;
        return new BlockPos(x, center.getY(), z);
    }

    /** Runs {@code body} once, on the first tick the hub is online (a hub across a late chunk forms at its 20-tick re-check). */
    private static void whenOnline(GameTestHelper helper, HubInterfaceBlockEntity hub, Runnable body) {
        boolean[] done = {false};
        helper.onEachTick(() -> {
            if (done[0] || !hub.online()) return;
            done[0] = true;
            body.run();
        });
    }

    private static String key(Component message) {
        return message != null && message.getContents() instanceof TranslatableContents t ? t.getKey() : String.valueOf(message);
    }

    private static FakePlayer second(GameTestHelper helper) {
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "second"));
        Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(STAND.east()));
        player.setPos(at.x, at.y, at.z);
        return player;
    }

    /** Only a turret with an Overwatch Link that the player may open, in reach, can be taken; stop gives it back. */
    @GameTest(template = YARD, timeoutTicks = 80, batch = "takingControlNeedsLinkAndReach")
    public static void takingControlNeedsLinkAndReach(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer player = ControlHubTests.player(helper, STAND);
        HubInterfaceBlockEntity hub = ControlHubTests.hub(helper, IFACE, 1);
        hub.setEnergy(100_000);
        BlockPos corner = corner(hub);
        TurretBaseBlockEntity plain = ControlHubTests.base(level, corner, player.getUUID());
        ControlHubTests.connect(plain, hub);
        TurretBaseBlockEntity linked = ControlHubTests.base(level, corner.east(), player.getUUID());
        link(linked, hub);
        TurretBaseBlockEntity stranger = ControlHubTests.base(level, corner.east(2), UUID.randomUUID());
        link(stranger, hub);
        BlockPos outsidePos = new BlockPos((hub.center().getX() & ~15) - 2, hub.center().getY(), hub.center().getZ());
        link(ControlHubTests.base(level, outsidePos, player.getUUID()), hub);
        whenOnline(helper, hub, () -> {
            BlockPos h = hub.getBlockPos();
            helper.assertTrue(key(OverwatchSessions.start(player, h, plain.getBlockPos())).equals("message.bastion.overwatch.no_link"), "plain connector taken");
            helper.assertTrue(key(OverwatchSessions.start(player, h, stranger.getBlockPos())).equals("message.bastion.overwatch.unreachable"), "stranger's taken");
            helper.assertTrue(key(OverwatchSessions.start(player, h, outsidePos)).equals("message.bastion.overwatch.unreachable"), "out of reach taken");
            helper.assertTrue(OverwatchSessions.start(player, h, linked.getBlockPos()) == null, "the linked turret was refused");
            helper.assertTrue(linked.manual() != null && linked.manual().player().equals(player.getUUID())
                    && linked.getBlockPos().equals(OverwatchSessions.controlled(player)), "not controlled after start");
            OverwatchSessions.stop(player);
            helper.assertTrue(linked.manual() == null && OverwatchSessions.controlled(player) == null, "stop left it controlled");
            for (BlockPos pos : List.of(corner, corner.east(), corner.east(2), outsidePos)) level.removeBlock(pos, false);
            helper.succeed();
        });
    }

    /** A turret one player controls is refused to another until they let go. */
    @GameTest(template = YARD, timeoutTicks = 80, batch = "oneControllerPerTurret")
    public static void oneControllerPerTurret(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer first = ControlHubTests.player(helper, STAND);
        FakePlayer second = second(helper);
        HubInterfaceBlockEntity hub = ControlHubTests.hub(helper, IFACE, 1);
        hub.setEnergy(100_000);
        TurretBaseBlockEntity turret = ControlHubTests.base(level, corner(hub), null);
        link(turret, hub);
        whenOnline(helper, hub, () -> {
            BlockPos h = hub.getBlockPos(), t = turret.getBlockPos();
            helper.assertTrue(OverwatchSessions.start(first, h, t) == null, "first refused");
            Component taken = OverwatchSessions.start(second, h, t);
            helper.assertTrue(key(taken).equals("message.bastion.overwatch.taken"), "second got " + key(taken));
            OverwatchSessions.stop(first);
            helper.assertTrue(OverwatchSessions.start(second, h, t) == null, "second refused after the first let go");
            OverwatchSessions.stop(second);
            level.removeBlock(t, false);
            helper.succeed();
        });
    }

    /** Damage, logout, a swapped-out Link, walking away, the turret removed and the hub offline each end the control. */
    @GameTest(template = YARD, timeoutTicks = 120, batch = "controlEndsWhenItShould")
    public static void controlEndsWhenItShould(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer player = ControlHubTests.player(helper, STAND);
        HubInterfaceBlockEntity hub = ControlHubTests.hub(helper, IFACE, 1);
        hub.setEnergy(100_000);
        BlockPos corner = corner(hub);
        TurretBaseBlockEntity turret = ControlHubTests.base(level, corner, player.getUUID());
        link(turret, hub);
        TurretBaseBlockEntity doomed = ControlHubTests.base(level, corner.east(), player.getUUID());
        link(doomed, hub);
        BlockPos h = hub.getBlockPos(), t = turret.getBlockPos();
        Vec3 stand = player.position();
        whenOnline(helper, hub, () -> {
            helper.assertTrue(OverwatchSessions.start(player, h, t) == null, "refused");
            MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(player, level.damageSources().generic(), 1f));
            helper.assertTrue(OverwatchSessions.controlled(player) == null && turret.manual() == null, "damage did not end it");
            helper.assertTrue(OverwatchSessions.start(player, h, t) == null, "refused after damage");
            MinecraftForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(player));
            helper.assertTrue(OverwatchSessions.controlled(player) == null, "logout did not end it");
            helper.assertTrue(OverwatchSessions.start(player, h, t) == null, "refused after logout");
            ControlHubTests.connect(turret, hub); // the Link swapped for a plain Hub Connector
            helper.runAfterDelay(2, () -> {
                helper.assertTrue(OverwatchSessions.controlled(player) == null && turret.manual() == null, "a plain connector kept it");
                link(turret, hub);
                helper.assertTrue(OverwatchSessions.start(player, h, t) == null, "refused with the Link back");
                player.setPos(stand.x + 12, stand.y, stand.z);
                helper.runAfterDelay(2, () -> {
                    helper.assertTrue(OverwatchSessions.controlled(player) == null, "walking away kept it");
                    player.setPos(stand.x, stand.y, stand.z);
                    helper.assertTrue(OverwatchSessions.start(player, h, doomed.getBlockPos()) == null, "refused the second turret");
                    level.removeBlock(doomed.getBlockPos(), false);
                    helper.runAfterDelay(2, () -> {
                        helper.assertTrue(OverwatchSessions.controlled(player) == null, "a removed turret is still controlled");
                        helper.assertTrue(OverwatchSessions.start(player, h, t) == null, "refused before the hub went off");
                        hub.setEnergy(0);
                        helper.runAfterDelay(3, () -> {
                            helper.assertTrue(!hub.online(), "the hub is still online");
                            helper.assertTrue(OverwatchSessions.controlled(player) == null && turret.manual() == null, "an offline hub kept it");
                            level.removeBlock(t, false);
                            helper.succeed();
                        });
                    });
                });
            });
        });
    }

    /** Input from anyone but the controller, for another turret, NaN or absurdly far is dropped. */
    @GameTest(template = YARD, timeoutTicks = 80, batch = "inputOnlyFromTheController")
    public static void inputOnlyFromTheController(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer player = ControlHubTests.player(helper, STAND);
        FakePlayer second = second(helper);
        HubInterfaceBlockEntity hub = ControlHubTests.hub(helper, IFACE, 1);
        hub.setEnergy(100_000);
        TurretBaseBlockEntity turret = ControlHubTests.base(level, corner(hub), null);
        link(turret, hub);
        whenOnline(helper, hub, () -> {
            BlockPos t = turret.getBlockPos();
            helper.assertTrue(OverwatchSessions.start(player, hub.getBlockPos(), t) == null, "refused");
            Vec3 good = Vec3.atCenterOf(t).add(5, 0, 0);
            OverwatchSessions.input(second, t, good, -1, true);
            OverwatchSessions.input(player, t.east(), good, -1, true);
            OverwatchSessions.input(player, t, new Vec3(Double.NaN, 0, 0), -1, true);
            OverwatchSessions.input(player, t, good.add(1000, 0, 0), -1, true);
            helper.assertTrue(turret.manual().point() == null, "bad input taken: " + turret.manual().point());
            OverwatchSessions.input(player, t, good, -1, true);
            helper.assertTrue(good.equals(turret.manual().point()), "good input dropped");
            OverwatchSessions.stop(player);
            level.removeBlock(t, false);
            helper.succeed();
        });
    }

    /** ← / → go round the hub's Overwatch turrets, past one with a plain connector and one another player controls. */
    @GameTest(template = YARD, timeoutTicks = 80, batch = "switchingSkipsTakenAndPlainTurrets")
    public static void switchingSkipsTakenAndPlainTurrets(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer player = ControlHubTests.player(helper, STAND);
        FakePlayer second = second(helper);
        HubInterfaceBlockEntity hub = ControlHubTests.hub(helper, IFACE, 1);
        hub.setEnergy(100_000);
        BlockPos corner = corner(hub);
        TurretBaseBlockEntity a = ControlHubTests.base(level, corner, null), plain = ControlHubTests.base(level, corner.east(), null);
        TurretBaseBlockEntity b = ControlHubTests.base(level, corner.east(2), null), taken = ControlHubTests.base(level, corner.south(), null);
        link(a, hub);
        ControlHubTests.connect(plain, hub);
        link(b, hub);
        link(taken, hub);
        whenOnline(helper, hub, () -> {
            BlockPos h = hub.getBlockPos();
            helper.assertTrue(OverwatchSessions.start(second, h, taken.getBlockPos()) == null, "second refused");
            helper.assertTrue(OverwatchSessions.start(player, h, a.getBlockPos()) == null, "first refused");
            OverwatchSessions.cycle(player, 1);
            helper.assertTrue(b.getBlockPos().equals(OverwatchSessions.controlled(player)) && a.manual() == null, "next went to " + OverwatchSessions.controlled(player));
            OverwatchSessions.cycle(player, 1);
            helper.assertTrue(a.getBlockPos().equals(OverwatchSessions.controlled(player)), "it did not wrap: " + OverwatchSessions.controlled(player));
            helper.assertTrue(taken.manual() != null && taken.manual().player().equals(second.getUUID()), "the other player's turret moved");
            OverwatchSessions.stop(player);
            OverwatchSessions.stop(second);
            for (BlockPos pos : List.of(corner, corner.east(), corner.east(2), corner.south())) level.removeBlock(pos, false);
            helper.succeed();
        });
    }

    /** The hub's roster says which turrets have a Link and who controls them. */
    @GameTest(template = YARD, timeoutTicks = 80, batch = "rosterShowsLinkAndController")
    public static void rosterShowsLinkAndController(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer player = ControlHubTests.player(helper, STAND);
        HubInterfaceBlockEntity hub = ControlHubTests.hub(helper, IFACE, 1);
        hub.setEnergy(100_000);
        BlockPos corner = corner(hub);
        TurretBaseBlockEntity linked = ControlHubTests.base(level, corner, null), plain = ControlHubTests.base(level, corner.east(), null);
        link(linked, hub);
        ControlHubTests.connect(plain, hub);
        whenOnline(helper, hub, () -> {
            helper.assertTrue(OverwatchSessions.start(player, hub.getBlockPos(), linked.getBlockPos()) == null, "refused");
            List<ControlHubMenu.Entry> entries = new ControlHubMenu(0, player.getInventory(), hub).roster().turrets();
            ControlHubMenu.Entry l = entries.stream().filter(e -> e.pos().equals(linked.getBlockPos())).findFirst().orElseThrow();
            ControlHubMenu.Entry p = entries.stream().filter(e -> e.pos().equals(plain.getBlockPos())).findFirst().orElseThrow();
            helper.assertTrue(l.linked() && l.controller().equals(player.getGameProfile().getName()), "linked entry " + l);
            helper.assertTrue(!p.linked() && p.controller().isEmpty(), "plain entry " + p);
            OverwatchSessions.stop(player);
            for (BlockPos pos : List.of(corner, corner.east())) level.removeBlock(pos, false);
            helper.succeed();
        });
    }
```

- [ ] **Step 2: Run and watch it fail**

Run: `./gradlew runGameTestServer --offline > build/gt-task4-red.log 2>&1; echo "exit $?"; grep -E "error:" build/gt-task4-red.log | head`
Expected: the compile fails: `OverwatchSessions`, `Entry.linked()` not found.

- [ ] **Step 3: `ControlHubMenu.Entry` gains `linked` and `controller`**

Append two components to the record, `boolean linked, String controller`:
- In `of(...)`, pass `OverwatchLinkItem.fitted(turret), turret.manual() == null ? "" : turret.manual().name()` as the last two arguments.
- In `write(...)`, add `buf.writeBoolean(linked); buf.writeUtf(controller);`.
- In `read(...)`, append `buf.readBoolean(), buf.readUtf(64)` to the constructor call.

Import `dev.bastion.hub.OverwatchLinkItem`.

- [ ] **Step 4: `OverwatchSessions`**

```java
package dev.bastion.hub;

import dev.bastion.Bastion;
import dev.bastion.menu.ControlHubMenu;
import dev.bastion.network.BastionNetwork;
import dev.bastion.network.OverwatchStatus;
import dev.bastion.network.OverwatchView;
import dev.bastion.turret.ManualControl;
import dev.bastion.turret.TurretBaseBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Overwatch sessions (PLAN "Overwatch Link"): which player controls which turret through which hub. A session needs what
 * opening the turret through the hub needs (HubInterfaceBlockEntity.reachable: online, in reach, loaded, theirs to open,
 * the player within 8 blocks of the Interface) plus an Overwatch Link, and nobody else at the controls. It is re-checked
 * every tick and ends on any damage to the player and on logout; the client is told when one begins or ends.
 */
@Mod.EventBusSubscriber(modid = Bastion.MOD_ID)
public final class OverwatchSessions {
    /** Aim points farther than this from the turret are garbage (or a cheat) and dropped. */
    private static final double MAX_AIM_DISTANCE = 512;
    /** Ticks between the controlled turret's ammo/FE for the HUD. */
    private static final int STATUS_INTERVAL = 10;

    private record Session(ServerPlayer player, ServerLevel level, BlockPos hub, BlockPos turret) {
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private OverwatchSessions() {
    }

    /** The turret the player controls now, or null. */
    @Nullable
    public static BlockPos controlled(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        return session == null ? null : session.turret();
    }

    /** Puts the player at the controls of the turret (leaving any other); returns why not, or null when it started. */
    @Nullable
    public static Component start(ServerPlayer player, BlockPos hubPos, BlockPos turretPos) {
        if (!(player.level().getBlockEntity(hubPos) instanceof HubInterfaceBlockEntity hub)) return Component.translatable("message.bastion.overwatch.unreachable");
        TurretBaseBlockEntity turret = hub.reachable(player, turretPos);
        if (turret == null) return Component.translatable("message.bastion.overwatch.unreachable");
        if (!OverwatchLinkItem.fitted(turret)) return Component.translatable("message.bastion.overwatch.no_link");
        ManualControl current = turret.manual();
        if (current != null && !current.player().equals(player.getUUID())) return Component.translatable("message.bastion.overwatch.taken", current.name());
        end(player, false);
        turret.setManual(new ManualControl(player.getUUID(), player.getGameProfile().getName()));
        SESSIONS.put(player.getUUID(), new Session(player, (ServerLevel) player.level(), hubPos.immutable(), turretPos.immutable()));
        if (player.containerMenu instanceof ControlHubMenu) player.closeContainer();
        send(player, new OverwatchView(turretPos.immutable(), hubPos.immutable()));
        send(player, new OverwatchStatus(ControlHubMenu.Entry.of(turret)));
        return null;
    }

    /** Lets go of the turret; the client's view goes back to the player. */
    public static void stop(ServerPlayer player) {
        end(player, true);
    }

    private static void end(ServerPlayer player, boolean tell) {
        Session session = SESSIONS.remove(player.getUUID());
        if (session == null) return;
        if (session.level().getBlockEntity(session.turret()) instanceof TurretBaseBlockEntity turret && turret.manual() != null
                && turret.manual().player().equals(player.getUUID())) {
            turret.setManual(null);
        }
        if (tell) send(player, OverwatchView.NONE);
    }

    /** ← / →: the next (step 1) or previous (-1) Overwatch turret on the same hub that nobody else controls, wrapping round. */
    public static void cycle(ServerPlayer player, int step) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || !(session.level().getBlockEntity(session.hub()) instanceof HubInterfaceBlockEntity hub)) return;
        List<BlockPos> choices = new ArrayList<>();
        for (TurretBaseBlockEntity turret : hub.visibleTurrets(player)) {
            ManualControl control = turret.manual();
            if (OverwatchLinkItem.fitted(turret) && (control == null || control.player().equals(player.getUUID()))) choices.add(turret.getBlockPos());
        }
        int at = choices.indexOf(session.turret());
        if (choices.size() < 2 || at < 0) return;
        Component refusal = start(player, session.hub(), choices.get(Math.floorMod(at + step, choices.size())));
        if (refusal != null) player.displayClientMessage(refusal, true);
    }

    /** One tick of the controller's input, for the turret of their session only; NaN and far-off points are dropped. */
    public static void input(ServerPlayer player, BlockPos turretPos, Vec3 point, int entity, boolean trigger) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || !session.turret().equals(turretPos)) return;
        if (!Double.isFinite(point.x) || !Double.isFinite(point.y) || !Double.isFinite(point.z)
                || point.distanceToSqr(Vec3.atCenterOf(turretPos)) > MAX_AIM_DISTANCE * MAX_AIM_DISTANCE) return;
        if (session.level().getBlockEntity(turretPos) instanceof TurretBaseBlockEntity turret && turret.manual() != null
                && turret.manual().player().equals(player.getUUID())) {
            turret.manual().input(point, entity, trigger, session.level().getGameTime());
        }
    }

    /** The session's turret while it may still be controlled, else null. */
    @Nullable
    private static TurretBaseBlockEntity valid(Session session) {
        ServerPlayer player = session.player();
        if (player.isRemoved() || !player.isAlive() || player.level() != session.level()) return null;
        if (!(session.level().getBlockEntity(session.hub()) instanceof HubInterfaceBlockEntity hub)) return null;
        TurretBaseBlockEntity turret = hub.reachable(player, session.turret());
        if (turret == null || !OverwatchLinkItem.fitted(turret)) return null;
        ManualControl control = turret.manual();
        return control != null && control.player().equals(player.getUUID()) ? turret : null;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || SESSIONS.isEmpty()) return;
        for (Session session : List.copyOf(SESSIONS.values())) {
            TurretBaseBlockEntity turret = valid(session);
            if (turret == null) end(session.player(), true);
            else if (session.level().getGameTime() % STATUS_INTERVAL == 0) send(session.player(), new OverwatchStatus(ControlHubMenu.Entry.of(turret)));
        }
    }

    /** Any damage to the player's body at the hub ends the control (user 2026-10-20). */
    @SubscribeEvent
    public static void onDamage(LivingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getAmount() > 0) end(player, true);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) end(player, false);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        SESSIONS.clear();
    }

    /** GameTest fake players have no client to tell. */
    private static void send(ServerPlayer player, Object message) {
        if (!(player instanceof FakePlayer)) BastionNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }
}
```

- [ ] **Step 5: The four packets**

`OverwatchRequest.java`:

```java
package dev.bastion.network;

import dev.bastion.hub.OverwatchSessions;
import dev.bastion.menu.ControlHubMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C->S Overwatch (PLAN "Overwatch Link"): take control of a turret from the open hub screen, switch to the next or previous
 * one, or let go. The server re-checks everything (OverwatchSessions).
 */
public record OverwatchRequest(Kind kind, BlockPos turret, int step) {
    public enum Kind {START, CYCLE, EXIT}

    public static OverwatchRequest start(BlockPos turret) {
        return new OverwatchRequest(Kind.START, turret, 0);
    }

    public static OverwatchRequest cycle(int step) {
        return new OverwatchRequest(Kind.CYCLE, BlockPos.ZERO, step);
    }

    public static OverwatchRequest exit() {
        return new OverwatchRequest(Kind.EXIT, BlockPos.ZERO, 0);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(kind);
        buf.writeBlockPos(turret);
        buf.writeByte(step);
    }

    public static OverwatchRequest decode(FriendlyByteBuf buf) {
        return new OverwatchRequest(buf.readEnum(Kind.class), buf.readBlockPos(), Mth.clamp(buf.readByte(), -1, 1));
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player != null) {
            switch (kind) {
                case START -> {
                    if (player.containerMenu instanceof ControlHubMenu menu && menu.stillValid(player)) {
                        Component refusal = OverwatchSessions.start(player, menu.hubPos(), turret);
                        if (refusal != null) player.displayClientMessage(refusal, true);
                    }
                }
                case CYCLE -> OverwatchSessions.cycle(player, step);
                case EXIT -> OverwatchSessions.stop(player);
            }
        }
        context.get().setPacketHandled(true);
    }
}
```

`OverwatchInput.java`:

```java
package dev.bastion.network;

import dev.bastion.hub.OverwatchSessions;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** C->S every client tick under Overwatch: the spot under the crosshair, the creature on it (-1: none), the trigger. */
public record OverwatchInput(BlockPos turret, Vec3 point, int entity, boolean trigger) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(turret);
        buf.writeDouble(point.x);
        buf.writeDouble(point.y);
        buf.writeDouble(point.z);
        buf.writeVarInt(entity);
        buf.writeBoolean(trigger);
    }

    public static OverwatchInput decode(FriendlyByteBuf buf) {
        return new OverwatchInput(buf.readBlockPos(), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readVarInt(), buf.readBoolean());
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player != null) OverwatchSessions.input(player, turret, point, entity, trigger);
        context.get().setPacketHandled(true);
    }
}
```

`OverwatchView.java`:

```java
package dev.bastion.network;

import dev.bastion.client.ClientPacketHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/** S->C an Overwatch session began (the turret and the hub it goes through) or ended (both null). */
public record OverwatchView(@Nullable BlockPos turret, @Nullable BlockPos hub) {
    public static final OverwatchView NONE = new OverwatchView(null, null);

    public void encode(FriendlyByteBuf buf) {
        buf.writeNullable(turret, FriendlyByteBuf::writeBlockPos);
        buf.writeNullable(hub, FriendlyByteBuf::writeBlockPos);
    }

    public static OverwatchView decode(FriendlyByteBuf buf) {
        return new OverwatchView(buf.readNullable(FriendlyByteBuf::readBlockPos), buf.readNullable(FriendlyByteBuf::readBlockPos));
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.overwatchView(this));
        context.get().setPacketHandled(true);
    }
}
```

`OverwatchStatus.java`:

```java
package dev.bastion.network;

import dev.bastion.client.ClientPacketHandler;
import dev.bastion.menu.ControlHubMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** S->C the controlled turret as the hub lists it, every half second: the HUD's ammo and FE (the client has no live count). */
public record OverwatchStatus(ControlHubMenu.Entry entry) {
    public void encode(FriendlyByteBuf buf) {
        entry.write(buf);
    }

    public static OverwatchStatus decode(FriendlyByteBuf buf) {
        return new OverwatchStatus(ControlHubMenu.Entry.read(buf));
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.overwatchStatus(this));
        context.get().setPacketHandled(true);
    }
}
```

Register them at the end of `BastionNetwork.register()` and set `PROTOCOL = "12"`:

```java
        CHANNEL.messageBuilder(OverwatchRequest.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(OverwatchRequest::encode).decoder(OverwatchRequest::decode).consumerMainThread(OverwatchRequest::handle).add();
        CHANNEL.messageBuilder(OverwatchInput.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(OverwatchInput::encode).decoder(OverwatchInput::decode).consumerMainThread(OverwatchInput::handle).add();
        CHANNEL.messageBuilder(OverwatchView.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OverwatchView::encode).decoder(OverwatchView::decode).consumerMainThread(OverwatchView::handle).add();
        CHANNEL.messageBuilder(OverwatchStatus.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OverwatchStatus::encode).decoder(OverwatchStatus::decode).consumerMainThread(OverwatchStatus::handle).add();
```

- [ ] **Step 6: Client receiver (state only)**

Create `src/main/java/dev/bastion/client/overwatch/OverwatchClient.java`:

```java
package dev.bastion.client.overwatch;

import dev.bastion.Bastion;
import dev.bastion.menu.ControlHubMenu;
import dev.bastion.registry.BastionSounds;
import dev.bastion.turret.TurretBaseBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/** The client's side of an Overwatch session (PLAN "Overwatch Link"): which turret the camera is at, its HUD status. */
@Mod.EventBusSubscriber(modid = Bastion.MOD_ID, value = Dist.CLIENT)
public final class OverwatchClient {
    @Nullable
    private static BlockPos turret, hub;
    @Nullable
    private static ControlHubMenu.Entry status;

    private OverwatchClient() {
    }

    public static void begin(BlockPos turretPos, BlockPos hubPos) {
        turret = turretPos;
        hub = hubPos;
        status = null;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(BastionSounds.CONTROL_HUB_UI_SELECT.get(), 1f));
    }

    public static void end() {
        if (turret == null) return;
        turret = hub = null;
        status = null;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(BastionSounds.CONTROL_HUB_UI_SELECT.get(), 0.8f));
    }

    public static void status(ControlHubMenu.Entry entry) {
        status = entry;
    }

    public static boolean active() {
        return turret != null;
    }

    @Nullable
    public static BlockPos turretPos() {
        return turret;
    }

    @Nullable
    public static BlockPos hubPos() {
        return hub;
    }

    @Nullable
    public static ControlHubMenu.Entry status() {
        return status;
    }

    /** The controlled turret in the client's world, or null when its chunk is not here (or it is gone). */
    @Nullable
    public static TurretBaseBlockEntity turret(@Nullable Level level) {
        return turret != null && level != null && level.isLoaded(turret) && level.getBlockEntity(turret) instanceof TurretBaseBlockEntity base ? base : null;
    }
}
```

In `ClientPacketHandler`:

```java
    public static void overwatchView(OverwatchView view) {
        if (view.turret() != null && view.hub() != null) OverwatchClient.begin(view.turret(), view.hub());
        else OverwatchClient.end();
    }

    public static void overwatchStatus(OverwatchStatus message) {
        OverwatchClient.status(message.entry());
    }
```

- [ ] **Step 7: Lang (messages)**

Use the Task 1 snippet with `AFTER = "message.bastion.hub_connector.cleared"`:

| key | en_us | id_id |
|---|---|---|
| `message.bastion.overwatch.unreachable` | That turret can't be reached through this hub | Turret itu tidak terjangkau lewat hub ini |
| `message.bastion.overwatch.no_link` | That turret has no Overwatch Link | Turret itu tidak punya Overwatch Link |
| `message.bastion.overwatch.taken` | %s is controlling that turret | %s sedang mengendalikan turret itu |

- [ ] **Step 8: Run the tests and the build**

Run: `./gradlew build --offline > build/build-task4.log 2>&1; echo "exit $?"` → `exit 0`.
Run: `./gradlew runGameTestServer --offline > build/gt-task4.log 2>&1; echo "exit $?"; grep -E "required tests|failed!" build/gt-task4.log | head`
Expected: `All 214 required tests passed`.

---

### Task 5: Hub button, turret GUI and Jade show the controller

**Files:**
- Modify:
  - `client/screen/ControlHubScreen.java`
  - `client/screen/FlatButton.java`
  - `client/screen/TurretScreen.java` (`renderStatusTooltips`)
  - `compat/BlockInfo.java`
  - both lang files
- Test: `OverwatchTests.infoLinesNameTheController`

**Interfaces:**
- Consumes:
  - `Entry.linked()`, `Entry.controller()`, `OverwatchRequest.start` (Task 4)
  - `TurretBaseBlockEntity.manual()`, `clientController()` (Task 3)
- Produces: the lang key `gui.bastion.turret.manual` ("Manual: %s"), used by Jade, the turret GUI and the HUD.

- [ ] **Step 1: Write the failing test** (add to `OverwatchTests`)

```java
    /** Jade's compact turret lines name the player steering it, right after the state. */
    @GameTest(template = ARENA, batch = "infoLinesNameTheController")
    public static void infoLinesNameTheController(GameTestHelper helper) {
        TurretBaseBlockEntity turret = small(helper, BastionItems.GUN_TURRET.get(), BastionItems.KINETIC_ROUNDS.get(), 0);
        helper.assertTrue(BlockInfo.lines(BlockInfo.write(turret), false).stream().map(CompatTests::text)
                .noneMatch(line -> line.startsWith("gui.bastion.turret.manual")), "a manual line without a controller");
        turret.setManual(new ManualControl(UUID.randomUUID(), "Tester"));
        List<String> lines = BlockInfo.lines(BlockInfo.write(turret), false).stream().map(CompatTests::text).toList();
        helper.assertTrue(lines.size() == 4 && lines.get(3).equals("gui.bastion.turret.manual[Tester]"), "lines " + lines);
        release(turret);
        helper.succeed();
    }
```

- [ ] **Step 2: Run and watch it fail**

Run the GameTests into `build/gt-task5-red.log`.
Expected: it fails with `infolinesnamethecontroller failed! lines [..3 lines..]`.

- [ ] **Step 3: `BlockInfo`**

In `write(...)`, turret branch, after `tag.putString("State", ...)`:

```java
        if (turret.manual() != null) tag.putString("Controller", turret.manual().name());
```

In `lines(...)`, turret branch, right after the `jade.bastion.state` line and before `if (!details) break;`:

```java
                if (tag.contains("Controller")) lines.add(Component.translatable("gui.bastion.turret.manual", tag.getString("Controller")));
```

- [ ] **Step 4: The hub's "Take control" button**

In `FlatButton.renderWidget`, an inactive button shows muted text:

```java
        int colour = !active ? MUTED : lit == null || on ? TEXT : MUTED;
```

In `ControlHubScreen`:
- Change the layout constants to `LEGEND_Y = 90`, `HINT_Y = 160`. A third side button now sits at y 72, so the legend and hint move down 14.
- Add a field `private FlatButton control;`. In `init()`, set `control = null;` next to `zoomIn = zoomOut = null;`. In the MAP branch, after the "clear" button, add:

```java
            control = addRenderableWidget(new FlatButton(leftPos + sideX, topPos + 72, sideW, 12, this::controlLabel, this::takeControl));
```

In `containerTick()`, right after the visibility loop (before its early `return`):

```java
        if (control != null) control.active = controlBlock() == null;
```

New methods (import `dev.bastion.network.OverwatchRequest`, `org.jetbrains.annotations.Nullable`):

```java
    // --- Overwatch (PLAN "Overwatch Link") --------------------------------------------------------

    /** The one selected turret, or null when none or several are selected. */
    @Nullable
    private Entry selectedOne() {
        if (HubSelection.selected.size() != 1) return null;
        BlockPos pos = HubSelection.selected.iterator().next();
        for (Entry turret : menu.turrets()) if (turret.pos().equals(pos)) return turret;
        return null;
    }

    /** Why the selection can't be taken over (the button shows it), or null when it can. */
    @Nullable
    private Component controlBlock() {
        Entry turret = selectedOne();
        if (turret == null) return Component.translatable("gui.bastion.control_hub.control.pick");
        if (!turret.linked()) return Component.translatable("gui.bastion.control_hub.control.no_link");
        String me = minecraft == null || minecraft.player == null ? "" : minecraft.player.getGameProfile().getName();
        if (!turret.controller().isEmpty() && !turret.controller().equals(me)) {
            return Component.translatable("gui.bastion.control_hub.control.taken", turret.controller());
        }
        // The camera only works where this client has the world loaded (PLAN: no chunks are sent for it).
        if (minecraft == null || minecraft.level == null || !minecraft.level.isLoaded(turret.pos())) {
            return Component.translatable("gui.bastion.control_hub.control.far");
        }
        return null;
    }

    private Component controlLabel() {
        Component block = controlBlock();
        return block != null ? block : Component.translatable("gui.bastion.control_hub.control");
    }

    private void takeControl() {
        Entry turret = selectedOne();
        if (turret == null || controlBlock() != null) return;
        click();
        BastionNetwork.CHANNEL.sendToServer(OverwatchRequest.start(turret.pos()));
    }
```

In `describe(Entry)`, build the list with `new ArrayList<>(List.of(...))`. Before returning, append:

```java
        if (turret.linked()) lines.add(Component.translatable("gui.bastion.control_hub.linked").withStyle(ChatFormatting.AQUA));
        if (!turret.controller().isEmpty()) lines.add(Component.translatable("gui.bastion.control_hub.controller", turret.controller()).withStyle(ChatFormatting.GOLD));
```

- [ ] **Step 5: The turret GUI lamp tooltip**

In `TurretScreen.renderStatusTooltips`, replace the lamp branch:

```java
        if (x >= LAMP_X - 3 && x < LAMP_X + LAMP_SIZE + 3 && y >= LAMP_Y - 3 && y < LAMP_Y + LAMP_SIZE + 3) {
            List<Component> lines = new ArrayList<>(List.of(Component.translatable("gui.bastion.state." + key(menu.state()))));
            String controller = minecraft.level != null && minecraft.level.getBlockEntity(menu.pos()) instanceof TurretBaseBlockEntity turret
                    ? turret.clientController() : null;
            if (controller != null) lines.add(Component.translatable("gui.bastion.turret.manual", controller).withStyle(ChatFormatting.GOLD));
            graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
            return;
        }
```

(Add imports as needed: `java.util.ArrayList`, `java.util.List`, `net.minecraft.ChatFormatting`, `dev.bastion.turret.TurretBaseBlockEntity`.)

- [ ] **Step 6: Lang**

Use the Task 1 snippet twice:
- `AFTER = "gui.bastion.control_hub.hint"` for the `control_hub` keys;
- `AFTER = "gui.bastion.state.no_ammo"` for `gui.bastion.turret.manual`.

| key | en_us | id_id |
|---|---|---|
| `gui.bastion.control_hub.control` | Take control | Ambil Kendali |
| `gui.bastion.control_hub.control.pick` | Select one turret | Pilih satu turret |
| `gui.bastion.control_hub.control.no_link` | Needs Overwatch Link | Butuh Overwatch Link |
| `gui.bastion.control_hub.control.taken` | In use: %s | Dipakai: %s |
| `gui.bastion.control_hub.control.far` | Out of view range | Di luar jarak pandang |
| `gui.bastion.control_hub.linked` | Overwatch Link | Overwatch Link |
| `gui.bastion.control_hub.controller` | Controlled by %s | Dikendalikan oleh %s |
| `gui.bastion.turret.manual` | Manual: %s | Manual: %s |

- [ ] **Step 7: Run the tests and the build**

Run: `./gradlew build --offline > build/build-task5.log 2>&1; echo "exit $?"` → `exit 0`.
Run the GameTests into `build/gt-task5.log`. Expected: `All 215 required tests passed`. `CompatTests.turretInfoLines` still passes; no controller means no extra line.

---

### Task 6: Client camera and controls

**Files:**
- Create: `src/main/java/dev/bastion/mixin/CameraMixin.java`
- Modify:
  - `src/main/resources/bastion.mixins.json` (`"client": ["LevelRendererMixin", "CameraMixin"]`)
  - `client/overwatch/OverwatchClient.java` (camera, crosshair ray, keys, input blocking)
  - `client/BastionClient.java` (register key mappings)
  - `turret/TurretBaseBlockEntity.java` (client predicted aim)
  - `client/render/WeaponMountLayer.java` (hide the weapon in first person)
  - both lang files (keys)
- Test: no GameTest can drive the client camera. The checks are: the build compiles the mixin; the full GameTest run proves common code still loads without client classes; the in-game checklist (Task 8).

**Interfaces:**
- Consumes:
  - `ManualAim.solve`, `WeaponType.ballistic()` (Task 2)
  - `OverwatchRequest`, `OverwatchInput`, `OverwatchClient` state (Task 4)
- Produces:
  - `OverwatchClient.View(Vec3 anchor, float yRot, float xRot, double distance)`
  - `OverwatchClient.view(float partialTick) -> @Nullable View`
  - `OverwatchClient.aimFrom(Camera)`
  - `OverwatchClient.aimPoint() -> @Nullable Vec3`
  - `OverwatchClient.canFire() -> boolean`
  - `OverwatchClient.watchOnly() -> boolean`
  - `OverwatchClient.zoom() -> int`
  - `OverwatchClient.hidesWeapon(BlockPos) -> boolean`
  - `OverwatchClient.NEXT`, `OverwatchClient.PREVIOUS` (KeyMapping)
  - `TurretBaseBlockEntity.setPredictedAim(float yaw, float pitch)`, `clearPredictedAim()`

- [ ] **Step 1: Predicted aim on the turret** (`TurretBaseBlockEntity`, client section)

```java
    // Client: the controlling player's own aim, drawn at once instead of after the server's next sync (Overwatch)
    private boolean predicted;
    private float predictedYaw, predictedPitch;

    public void setPredictedAim(float yaw, float pitch) {
        predicted = true;
        predictedYaw = yaw;
        predictedPitch = pitch;
    }

    public void clearPredictedAim() {
        predicted = false;
    }
```

At the start of `tickClient()`, after the `prevRender...` assignments:

```java
        if (predicted) { // the controller's view: no smoothing, no sweep
            prevRenderYaw = renderYaw = predictedYaw;
            prevRenderPitch = renderPitch = predictedPitch;
            return;
        }
```

In `renderYaw(float)` / `renderPitch(float)`, return `predictedYaw` / `predictedPitch` first when `predicted` is set. The camera mixin updates it every frame, so the barrel turns with the mouse between ticks.

- [ ] **Step 2: `CameraMixin`**

```java
package dev.bastion.mixin;

import dev.bastion.client.overwatch.OverwatchClient;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Overwatch (PLAN "Overwatch Link"): while the player controls a turret through a hub, the camera sits at that turret
 * (behind it, or at the barrel in first person) and the player's body at the hub is drawn as in F5.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    private boolean detached;

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Shadow
    protected abstract void setPosition(Vec3 pos);

    @Shadow
    protected abstract void move(double distance, double vertical, double horizontal);

    @Shadow
    protected abstract double getMaxZoom(double distance);

    @Inject(method = "setup", at = @At("TAIL"))
    private void bastion$overwatch(BlockGetter level, Entity entity, boolean detached, boolean mirrored, float partialTick, CallbackInfo ci) {
        OverwatchClient.View view = OverwatchClient.view(partialTick);
        if (view == null) return;
        this.detached = true;
        setRotation(view.yRot(), view.xRot());
        setPosition(view.anchor());
        if (view.distance() > 0) move(-getMaxZoom(view.distance()), 0, 0);
        OverwatchClient.aimFrom((Camera) (Object) this);
    }
}
```

Add `"CameraMixin"` to the `"client"` array in `bastion.mixins.json`.

- [ ] **Step 3: Grow `OverwatchClient`**

New fields and constants:

```java
    public static final KeyMapping NEXT = new KeyMapping("key.bastion.overwatch.next", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT, "key.categories.bastion");
    public static final KeyMapping PREVIOUS = new KeyMapping("key.bastion.overwatch.previous", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT, "key.categories.bastion");
    /** Third person: this far behind the pivot (a large base's weapons are bigger), the view raised a little over the barrel. */
    private static final double BEHIND = 3.5, BEHIND_LARGE = 5.5, ABOVE = 0.9;
    private static final int MAX_ZOOM = 4;

    private static boolean firstPerson, sneakWasDown;
    private static int zoom = 1;
    // The crosshair as of the last frame, sent every tick.
    @Nullable
    private static Vec3 aimPoint;
    private static int aimEntity = -1;
    private static boolean canFire, watchOnly;

    /** Where the camera goes this frame: the anchor, the look direction and how far back (0 = first person). */
    public record View(Vec3 anchor, float yRot, float xRot, double distance) {
    }
```

`begin(...)` also resets `firstPerson = false; zoom = 1; aimPoint = null; aimEntity = -1; canFire = watchOnly = false;` and `sneakWasDown = true`, so a held Shift doesn't leave at once. `end()` first clears the turret's prediction:

```java
        TurretBaseBlockEntity base = turret(Minecraft.getInstance().level);
        if (base != null) base.clearPredictedAim();
```

View, crosshair and getters:

```java
    /** Camera.setup (CameraMixin): where the camera goes this frame, or null outside Overwatch. Mouse look turns the player, so the camera takes their view. */
    @Nullable
    public static View view(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        TurretBaseBlockEntity base = turret(mc.level);
        if (base == null || mc.player == null) return null;
        WeaponData data = base.inventory().weaponData();
        Vec3 pivot = data == null ? base.alongMount(1.5) : base.pivot(data);
        float yRot = mc.player.getViewYRot(partialTick), xRot = mc.player.getViewXRot(partialTick);
        if (firstPerson) return new View(pivot, yRot, xRot, 0);
        return new View(pivot.add(0, ABOVE, 0), yRot, xRot, base.large() ? BEHIND_LARGE : BEHIND);
    }

    /**
     * After the camera is placed: what the crosshair is on (blocks, then the first creature before them, out to the weapon's
     * range past the turret) and where the barrel points for it, drawn this frame.
     */
    public static void aimFrom(Camera camera) {
        Minecraft mc = Minecraft.getInstance();
        TurretBaseBlockEntity base = turret(mc.level);
        WeaponData data = base == null ? null : base.inventory().weaponData();
        WeaponType type = data == null ? null : BastionWeaponTypes.REGISTRY.get().getValue(data.type());
        if (base == null || type == null || mc.player == null) {
            aimPoint = null;
            canFire = watchOnly = false;
            if (base != null) base.clearPredictedAim();
            return;
        }
        StatSheet stats = StatSheet.of(data, base.tier(), base.inventory().modifierEffect());
        Vec3 from = camera.getPosition(), look = new Vec3(camera.getLookVector());
        Vec3 to = from.add(look.scale(from.distanceTo(base.pivot(data)) + stats.range()));
        BlockHitResult block = mc.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        Vec3 end = block.getType() == HitResult.Type.MISS ? to : block.getLocation();
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(mc.level, mc.player, from, end, new AABB(from, end).inflate(1),
                e -> e instanceof LivingEntity && e != mc.player && !(e instanceof TurretHitboxEntity) && e.isPickable() && e.isAlive());
        // A shell is fired at a creature's feet (MortarWeapon), so its arc is drawn there too.
        aimPoint = hit == null ? end : type.ballistic() ? hit.getEntity().position() : hit.getLocation();
        aimEntity = hit == null ? -1 : hit.getEntity().getId();
        watchOnly = data.aim().turnSpeed() <= 0;
        ManualAim.Aim aim = watchOnly ? null : ManualAim.solve(base, type, stats, aimPoint);
        canFire = aim != null && (aim.onTarget() || data.aim().fixedPitch()); // the Missile Launcher never tilts: its missiles home in
        if (aim != null) base.setPredictedAim(aim.yaw(), aim.pitch());
        else base.clearPredictedAim();
    }

    @Nullable
    public static Vec3 aimPoint() {
        return aimPoint;
    }

    public static boolean canFire() {
        return canFire;
    }

    public static boolean watchOnly() {
        return watchOnly;
    }

    public static int zoom() {
        return zoom;
    }

    /** First person looks out from inside the weapon: its model is not drawn (WeaponMountLayer). */
    public static boolean hidesWeapon(BlockPos pos) {
        return firstPerson && pos.equals(turret);
    }

    /** Back to the body: tells the server and drops the view at once. */
    private static void leave() {
        BastionNetwork.CHANNEL.sendToServer(OverwatchRequest.exit());
        end();
    }
```

Events (the class is already a FORGE-bus `Dist.CLIENT` subscriber):

```java
    /** Before vanilla reads its keys this tick: Overwatch takes F5, the switch keys and Shift, swallows inventory/hotbar/drop, sends input. */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START || turret == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            end();
            return;
        }
        if (turret(mc.level) == null) { // its chunk left this client, or it is gone (PLAN: no chunks are sent for the camera)
            leave();
            return;
        }
        Options o = mc.options;
        while (o.keyTogglePerspective.consumeClick()) firstPerson = !firstPerson;
        while (o.keyInventory.consumeClick()) ;
        while (o.keyDrop.consumeClick()) ;
        while (o.keySwapOffhand.consumeClick()) ;
        for (KeyMapping key : o.keyHotbarSlots) while (key.consumeClick()) ;
        while (NEXT.consumeClick()) BastionNetwork.CHANNEL.sendToServer(OverwatchRequest.cycle(1));
        while (PREVIOUS.consumeClick()) BastionNetwork.CHANNEL.sendToServer(OverwatchRequest.cycle(-1));
        boolean sneak = o.keyShift.isDown();
        if (sneak && !sneakWasDown && mc.screen == null) {
            leave();
            return;
        }
        sneakWasDown = sneak;
        if (aimPoint != null) {
            BastionNetwork.CHANNEL.sendToServer(new OverwatchInput(turret, aimPoint, aimEntity, mc.screen == null && o.keyAttack.isDown()));
        }
    }

    /** The body at the hub stands still. */
    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (turret == null) return;
        Input input = event.getInput();
        input.forwardImpulse = input.leftImpulse = 0;
        input.up = input.down = input.left = input.right = input.jumping = input.shiftKeyDown = false;
    }

    /** No attacking, using or picking blocks at the hub with the view away. */
    @SubscribeEvent
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (turret == null) return;
        event.setCanceled(true);
        event.setSwingHand(false);
    }

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        if (turret == null || Minecraft.getInstance().screen != null || event.getScrollDelta() == 0) return;
        zoom = Mth.clamp(zoom + (event.getScrollDelta() > 0 ? 1 : -1), 1, MAX_ZOOM); // ponytail: one step per event, a trackpad swipe zooms fast
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov event) {
        if (turret != null) event.setFOV(event.getFOV() / zoom);
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (turret != null) event.setCanceled(true);
    }

    /** The block outline would mark whatever the body at the hub looks at. */
    @SubscribeEvent
    public static void onHighlight(RenderHighlightEvent.Block event) {
        if (turret != null) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        turret = hub = null;
        status = null;
    }
```

Imports: `com.mojang.blaze3d.platform.InputConstants`, `dev.bastion.network.BastionNetwork`, `dev.bastion.network.OverwatchInput`, `dev.bastion.network.OverwatchRequest`, `dev.bastion.registry.BastionWeaponTypes`, `dev.bastion.turret.TurretHitboxEntity`, `dev.bastion.weapon.ManualAim`, `dev.bastion.weapon.StatSheet`, `dev.bastion.weapon.WeaponData`, `dev.bastion.weapon.WeaponType`, `net.minecraft.client.Camera`, `net.minecraft.client.KeyMapping`, `net.minecraft.client.Options`, `net.minecraft.client.player.Input`, `net.minecraft.util.Mth`, `net.minecraft.world.entity.LivingEntity`, `net.minecraft.world.entity.projectile.ProjectileUtil`, `net.minecraft.world.level.ClipContext`, `net.minecraft.world.phys.AABB`, `net.minecraft.world.phys.BlockHitResult`, `net.minecraft.world.phys.EntityHitResult`, `net.minecraft.world.phys.HitResult`, `net.minecraft.world.phys.Vec3`, `net.minecraftforge.client.event.*` (the 6 events above + `ClientPlayerNetworkEvent`), `net.minecraftforge.client.settings.KeyConflictContext`, `net.minecraftforge.event.TickEvent`, `net.minecraftforge.eventbus.api.SubscribeEvent`, `org.lwjgl.glfw.GLFW`.

In `BastionClient` (MOD bus):

```java
    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(OverwatchClient.NEXT);
        event.register(OverwatchClient.PREVIOUS);
    }
```

- [ ] **Step 4: Hide the weapon in first person** (`WeaponMountLayer`)

```java
    /** Takes vertices and draws nothing: the first-person Overwatch view looks out from inside the weapon (its bones still move, so muzzles stay right). */
    private static final VertexConsumer DISCARD = new VertexConsumer() {
        @Override public VertexConsumer vertex(double x, double y, double z) { return this; }
        @Override public VertexConsumer color(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer uv(float u, float v) { return this; }
        @Override public VertexConsumer overlayCoords(int u, int v) { return this; }
        @Override public VertexConsumer uv2(int u, int v) { return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { return this; }
        @Override public void endVertex() { }
        @Override public void defaultColor(int r, int g, int b, int a) { }
        @Override public void unsetDefaultColor() { }
    };
    private static final MultiBufferSource NOWHERE = type -> DISCARD;
```

In `render(...)`, draw into `NOWHERE` when hidden:

```java
            weaponRenderer.render(poseStack, weapon, OverwatchClient.hidesWeapon(turret.getBlockPos()) ? NOWHERE : bufferSource, null, null, packedLight);
```

- [ ] **Step 5: Lang (keys)**

Use the Task 1 snippet with `AFTER = "item.bastion.overwatch_link"`:

| key | en_us | id_id |
|---|---|---|
| `key.categories.bastion` | Bastion Turrets | Bastion Turrets |
| `key.bastion.overwatch.next` | Overwatch: next turret | Overwatch: turret berikutnya |
| `key.bastion.overwatch.previous` | Overwatch: previous turret | Overwatch: turret sebelumnya |

- [ ] **Step 6: Build and the full GameTest run**

Run: `./gradlew build --offline > build/build-task6.log 2>&1; echo "exit $?"` → `exit 0`. A wrong `@Shadow` target fails at mixin apply time, not at compile time. That surfaces in the user's first `runClient`; Task 8's checklist puts it first.
Run the GameTests into `build/gt-task6.log`. Expected: `All 215 required tests passed`, and no `ClassNotFoundException` / `RuntimeException: Attempted to load class net/minecraft/client` in the log: `grep -c "net/minecraft/client" build/gt-task6.log` → `0`.

---

### Task 7: CCTV HUD and the arc indicator

**Files:**
- Create: `client/overwatch/OverwatchHud.java`, `client/overwatch/OverwatchArc.java`
- Modify:
  - `client/BastionClient.java` (register the overlay)
  - `client/overwatch/OverwatchClient.java` (hide vanilla overlays)
  - `client/vfx/VfxManager.java` (draw the arc)
  - both lang files (HUD)
- Test: no GameTest can drive client rendering. The checks are: the build; the full GameTest run (no client class loaded); the in-game checklist. The arc's math is `Ballistics`, pinned by `ballisticLaunchLandsOnTheAimPoint` (Task 2).

**Interfaces:**
- Consumes:
  - `OverwatchClient.turret/aimPoint/canFire/watchOnly/zoom/status/hubPos/NEXT/PREVIOUS` (Task 6)
  - `Ballistics.solve`, `Launch.at` (Task 2)
- Produces: nothing used by later tasks.

- [ ] **Step 1: `OverwatchHud`**

```java
package dev.bastion.client.overwatch;

import dev.bastion.client.ClientTurret;
import dev.bastion.menu.ControlHubMenu;
import dev.bastion.registry.BastionWeaponTypes;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.TurretInventory;
import dev.bastion.turret.TurretState;
import dev.bastion.weapon.StatSheet;
import dev.bastion.weapon.WeaponData;
import dev.bastion.weapon.WeaponType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The Overwatch HUD (PLAN "Overwatch Link"), all drawn here, no vanilla sprites:
 * - scan lines, corner brackets, REC and the in-game time, the turret's name and distance to the hub;
 * - a crosshair that turns red when the shot can't go where it points;
 * - HP, ammo or FE, heat and charge bars, key hints.
 */
public final class OverwatchHud {
    private static final int TEXT = 0xFFD8E2EC, MUTED = 0xFF7F8C9A, ACCENT = 0xFF9FC4E8, RED = 0xFFFF3A30, GREEN = 0xFF7CFF8A,
            AMBER = 0xFFFFB347, BAR_BG = 0xA0101418;
    private static final int MARGIN = 10, BRACKET = 14, BAR_W = 70;

    private OverwatchHud() {
    }

    public static void render(GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        TurretBaseBlockEntity base = OverwatchClient.turret(mc.level);
        if (base == null || mc.options.hideGui) return;
        Font font = mc.font;
        long time = mc.level.getGameTime();
        for (int y = 0; y < height; y += 3) g.fill(0, y, width, y + 1, 0x0C000000); // scan lines
        int band = (int) ((time + partialTick) * 2 % (height + 40)) - 20;
        g.fill(0, band, width, band + 20, 0x08FFFFFF);
        brackets(g, width, height);
        if (time % 20 < 12) g.fill(MARGIN + 6, MARGIN + 6, MARGIN + 12, MARGIN + 12, RED);
        g.drawString(font, Component.translatable("hud.bastion.overwatch.rec"), MARGIN + 16, MARGIN + 5, TEXT, false);
        long day = mc.level.getDayTime();
        String clock = String.format("%02d:%02d", (day / 1000 + 6) % 24, day % 1000 * 60 / 1000);
        g.drawString(font, Component.translatable("hud.bastion.overwatch.time", day / 24000 + 1, clock), MARGIN + 6, MARGIN + 17, MUTED, false);
        WeaponData data = base.inventory().weaponData();
        Component name = data == null ? Component.translatable("gui.bastion.control_hub.unarmed")
                : Component.translatable("jade.bastion.turret.weapon", base.inventory().getStackInSlot(TurretInventory.WEAPON).getHoverName(),
                Component.translatable("gui.bastion.turret.tier", base.tier().ordinal() + 1));
        g.drawString(font, name, width - MARGIN - 6 - font.width(name), MARGIN + 5, TEXT, false);
        BlockPos hub = OverwatchClient.hubPos();
        if (hub != null) {
            Component distance = Component.translatable("hud.bastion.overwatch.distance", Math.round(Math.sqrt(base.getBlockPos().distSqr(hub))));
            g.drawString(font, distance, width - MARGIN - 6 - font.width(distance), MARGIN + 17, MUTED, false);
        }
        crosshair(g, font, width / 2, height / 2);
        bars(g, font, base, data, partialTick, time, height);
        if (OverwatchClient.zoom() > 1) {
            Component zoom = Component.translatable("hud.bastion.overwatch.zoom", OverwatchClient.zoom());
            g.drawString(font, zoom, width - MARGIN - 6 - font.width(zoom), height - MARGIN - 14, ACCENT, false);
        }
        Options o = mc.options;
        Component hints = Component.translatable("hud.bastion.overwatch.hints", o.keyAttack.getTranslatedKeyMessage(),
                Component.translatable("hud.bastion.overwatch.scroll"), o.keyTogglePerspective.getTranslatedKeyMessage(),
                OverwatchClient.PREVIOUS.getTranslatedKeyMessage(), OverwatchClient.NEXT.getTranslatedKeyMessage(), o.keyShift.getTranslatedKeyMessage());
        g.drawString(font, hints, (width - font.width(hints)) / 2, height - MARGIN - 4, MUTED, false);
    }

    private static void brackets(GuiGraphics g, int width, int height) {
        int l = MARGIN, r = width - MARGIN, t = MARGIN, b = height - MARGIN;
        g.fill(l, t, l + BRACKET, t + 1, ACCENT);
        g.fill(l, t, l + 1, t + BRACKET, ACCENT);
        g.fill(r - BRACKET, t, r, t + 1, ACCENT);
        g.fill(r - 1, t, r, t + BRACKET, ACCENT);
        g.fill(l, b - 1, l + BRACKET, b, ACCENT);
        g.fill(l, b - BRACKET, l + 1, b, ACCENT);
        g.fill(r - BRACKET, b - 1, r, b, ACCENT);
        g.fill(r - 1, b - BRACKET, r, b, ACCENT);
    }

    /** Four ticks round a gap and a centre dot; watch-only weapons get a label instead. */
    private static void crosshair(GuiGraphics g, Font font, int x, int y) {
        if (OverwatchClient.watchOnly()) {
            Component label = Component.translatable("hud.bastion.overwatch.view_only");
            g.fill(x, y, x + 1, y + 1, ACCENT);
            g.drawString(font, label, x - font.width(label) / 2, y + 8, ACCENT, false);
            return;
        }
        int c = OverwatchClient.canFire() ? TEXT : RED;
        g.fill(x - 7, y, x - 3, y + 1, c);
        g.fill(x + 4, y, x + 8, y + 1, c);
        g.fill(x, y - 7, x + 1, y - 3, c);
        g.fill(x, y + 4, x + 1, y + 8, c);
        g.fill(x, y, x + 1, y + 1, c);
    }

    private static void bars(GuiGraphics g, Font font, TurretBaseBlockEntity base, WeaponData data, float partialTick, long time, int height) {
        int x = MARGIN + 6, y = height - MARGIN - 52;
        float hp = Mth.clamp(base.clientHealth(), 0, 1);
        y = bar(g, font, x, y, "hud.bastion.overwatch.hp", hp, hp < TurretBaseBlockEntity.CRITICAL_FRACTION ? RED : hp < TurretBaseBlockEntity.DAMAGED_FRACTION ? AMBER : GREEN);
        ControlHubMenu.Entry status = OverwatchClient.status();
        if (status != null) {
            y = bar(g, font, x, y, status.energyWeapon() ? "hud.bastion.overwatch.energy" : "hud.bastion.overwatch.ammo", status.fill(),
                    base.clientState() == TurretState.NO_AMMO ? RED : ACCENT);
        }
        if (data == null) return;
        StatSheet stats = StatSheet.of(data, base.tier(), base.inventory().modifierEffect());
        if (stats.heatPerShot() > 0) y = bar(g, font, x, y, "hud.bastion.overwatch.heat", Mth.clamp(base.clientHeat() / stats.maxHeat(), 0, 1), AMBER);
        WeaponType type = BastionWeaponTypes.REGISTRY.get().getValue(data.type());
        int charge = type == null ? 0 : type.chargeTicks(stats);
        if (charge > 0) {
            float progress = base.clientState() == TurretState.CHARGING ? ClientTurret.of(base.getBlockPos()).chargeProgress(time, partialTick, charge) : 0;
            bar(g, font, x, y, "hud.bastion.overwatch.charge", progress, 0xFF000000 | data.energyColor());
        }
    }

    /** One labelled bar; returns the y of the next. */
    private static int bar(GuiGraphics g, Font font, int x, int y, String key, float fill, int colour) {
        g.drawString(font, Component.translatable(key), x, y, MUTED, false);
        int bx = x + 44;
        g.fill(bx, y + 1, bx + BAR_W, y + 7, BAR_BG);
        g.fill(bx + 1, y + 2, bx + 1 + Math.round((BAR_W - 2) * fill), y + 6, colour);
        return y + 11;
    }
}
```

Checked 2026-10-20: `ClientTurret.chargeProgress(long, float, int)` returns 0..1 (as in `TurretHolograms.laserSight`), and `gui.bastion.turret.tier` takes the tier number, `ordinal() + 1` (as `BlockInfo` writes "Tier").

Register it in `BastionClient` (MOD bus):

```java
    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("overwatch", (gui, graphics, partialTick, width, height) -> OverwatchHud.render(graphics, partialTick, width, height));
    }
```

In `OverwatchClient`, hide the vanilla HUD parts during a session:

```java
    private static final Set<ResourceLocation> HIDDEN = Set.of(VanillaGuiOverlay.HOTBAR.id(), VanillaGuiOverlay.CROSSHAIR.id(),
            VanillaGuiOverlay.PLAYER_HEALTH.id(), VanillaGuiOverlay.ARMOR_LEVEL.id(), VanillaGuiOverlay.FOOD_LEVEL.id(),
            VanillaGuiOverlay.AIR_LEVEL.id(), VanillaGuiOverlay.MOUNT_HEALTH.id(), VanillaGuiOverlay.JUMP_BAR.id(),
            VanillaGuiOverlay.EXPERIENCE_BAR.id(), VanillaGuiOverlay.ITEM_NAME.id(), VanillaGuiOverlay.HELMET.id(),
            VanillaGuiOverlay.SPYGLASS.id(), VanillaGuiOverlay.VIGNETTE.id());

    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Pre event) {
        if (turret != null && HIDDEN.contains(event.getOverlay().id())) event.setCanceled(true);
    }
```

- [ ] **Step 2: `OverwatchArc`**

```java
package dev.bastion.client.overwatch;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.bastion.Bastion;
import dev.bastion.client.render.BastionRenderTypes;
import dev.bastion.client.render.TrailRenderer;
import dev.bastion.registry.BastionWeaponTypes;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.weapon.Ballistics;
import dev.bastion.weapon.WeaponData;
import dev.bastion.weapon.WeaponType;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Overwatch arc indicator (PLAN "Overwatch Link"): for a ballistic weapon, glowing dashes along the shell's path from the
 * muzzle to the first block in its way and a ring of the blast radius where it lands; red when it can't fire there.
 */
public final class OverwatchArc {
    private static final ResourceLocation LASER = Bastion.id("textures/vfx/tracer.png");
    private static final int RED = 0xFF3A30, RING_SEGMENTS = 32;
    /** Ticks of flight per dash (and per gap). */
    private static final double DASH = 1;

    private OverwatchArc() {
    }

    public static void render(PoseStack poseStack, MultiBufferSource buffers, Camera camera, float partialTick, ClientLevel level) {
        TurretBaseBlockEntity base = OverwatchClient.turret(level);
        Vec3 point = OverwatchClient.aimPoint();
        WeaponData data = base == null ? null : base.inventory().weaponData();
        WeaponType type = data == null ? null : BastionWeaponTypes.REGISTRY.get().getValue(data.type());
        if (point == null || type == null || !type.ballistic()) return;
        boolean ok = OverwatchClient.canFire();
        int colour = ok ? data.energyColor() : RED;
        VertexConsumer glow = buffers.getBuffer(BastionRenderTypes.additiveGlow(LASER));
        Matrix4f pose = poseStack.last().pose();
        Vec3 cam = camera.getPosition(), landing = point;
        Ballistics.Launch launch = ok ? Ballistics.solve(base, data, point) : null;
        if (launch != null) {
            double gravity = data.param("gravity");
            Vec3 last = launch.muzzle();
            int step = 0;
            for (double t = DASH; ; t += DASH) {
                Vec3 next = launch.at(Math.min(t, launch.ticks()), gravity);
                BlockHitResult hit = level.clip(new ClipContext(last, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, Minecraft.getInstance().player));
                boolean blocked = hit.getType() != HitResult.Type.MISS;
                if (blocked) next = hit.getLocation();
                if (step++ % 2 == 0) TrailRenderer.segment(glow, pose, cam, last, next, 0.06f, colour, 0.85f);
                if (blocked || t >= launch.ticks()) {
                    landing = next;
                    break;
                }
                last = next;
            }
        }
        ring(glow, pose, cam, landing, data.params().getOrDefault("blast_radius", 1f), colour);
    }

    /** A flat circle just above {@code centre}. */
    private static void ring(VertexConsumer glow, Matrix4f pose, Vec3 cam, Vec3 centre, double radius, int colour) {
        Vec3 last = null;
        for (int i = 0; i <= RING_SEGMENTS; i++) {
            double a = i * 2 * Math.PI / RING_SEGMENTS;
            Vec3 p = centre.add(Math.cos(a) * radius, 0.05, Math.sin(a) * radius);
            if (last != null) TrailRenderer.segment(glow, pose, cam, last, p, 0.05f, colour, 0.9f);
            last = p;
        }
    }
}
```

In `VfxManager.onRenderLevel`, after the `TurretHolograms.render(...)` line:

```java
        if (Minecraft.getInstance().level != null) OverwatchArc.render(poseStack, buffers, camera, partialTick, Minecraft.getInstance().level);
```

- [ ] **Step 3: Lang (HUD)**

Use the Task 1 snippet with `AFTER = "key.bastion.overwatch.previous"`:

| key | en_us | id_id |
|---|---|---|
| `hud.bastion.overwatch.rec` | REC | REC |
| `hud.bastion.overwatch.time` | Day %s  %s | Hari %s  %s |
| `hud.bastion.overwatch.distance` | %s m from hub | %s m dari hub |
| `hud.bastion.overwatch.view_only` | VIEW ONLY | HANYA LIHAT |
| `hud.bastion.overwatch.hp` | HP | HP |
| `hud.bastion.overwatch.ammo` | AMMO | AMUNISI |
| `hud.bastion.overwatch.energy` | FE | FE |
| `hud.bastion.overwatch.heat` | HEAT | PANAS |
| `hud.bastion.overwatch.charge` | CHARGE | CHARGE |
| `hud.bastion.overwatch.zoom` | %sx | %sx |
| `hud.bastion.overwatch.scroll` | Scroll | Scroll |
| `hud.bastion.overwatch.hints` | %s fire · %s zoom · %s view · %s / %s switch · %s leave | %s tembak · %s zoom · %s pandangan · %s / %s ganti · %s keluar |

- [ ] **Step 4: Build and the full GameTest run**

Run: `./gradlew build --offline > build/build-task7.log 2>&1; echo "exit $?"` → `exit 0`.
Run the GameTests into `build/gt-task7.log`. Expected: `All 215 required tests passed`; `grep -c "net/minecraft/client" build/gt-task7.log` → `0`.

---

### Task 8: Docs and the in-game checklist

**Files:**
- Modify:
  - `docs/TESTING.md` (new section)
  - `docs/PLAN.md` (Overwatch status line)
  - `docs/HANDOFF.md` (top note)
  - `docs/release/UPLOAD.md` (changelog draft "Changelog: 0.3.0 (draft)")
- Create: `docs/release/DESCRIPTION_UPDATE_0.3.0.md` (draft)
- Test: final full runs.

- [ ] **Step 1: `docs/TESTING.md`**: add the section `## Overwatch Link`

```markdown
## Overwatch Link

Run `./gradlew runClient --offline` (add `-PwithCompatMods` for the camera/compat check at the end).

Setup: a formed Control Hub (T2+), online. Two Gun turrets, a Mortar and a Tesla Coil in reach. Each gets an Overwatch Link linked to the hub (right-click the Interface with the Link first). One more turret gets a plain Hub Connector.

1. **It loads:** the game reaches the title screen and a world (the Camera mixin applies). The Link shows in JEI with its recipe (Module Workstation) and info page.
2. **Hub button:**
   - On the Map tab, shift-click one Gun turret. The side button reads "Take control" (with none or two selected: "Select one turret"; the plain connector turret: "Needs Overwatch Link").
   - Hovering a Link turret's dot shows "Overwatch Link".
3. **Third person:** press Take control.
   - The screen closes and the camera sits behind the turret. Mouse looks around, and the barrel follows at once.
   - The HUD shows: brackets, REC, time, turret name, distance to the hub, HP/ammo/heat bars, hints. No vanilla hotbar, hearts or hand.
   - Your own body is visible at the hub if you look back.
4. **Fire:**
   - Hold LMB on a cow: the Gun fires and hits it. Ammo goes down in the HUD.
   - Fire at your own spot at the hub: you take no damage.
5. **F5, zoom, switch:**
   - F5 switches to the barrel view (no weapon model in the way).
   - Scroll zooms 1x–4x.
   - ← / → jumps to the other Link turrets and skips the plain connector turret.
6. **Mortar:**
   - On the Mortar, glowing dashes arc from the barrel to the ground with a ring at the end. Close to the base (inside 8 blocks), the arc and ring turn red and LMB does nothing.
   - Fire: the shell lands in the ring.
7. **Tesla Coil:** the crosshair says "VIEW ONLY". The coil keeps zapping hostiles by itself.
8. **Letting go:**
   - Shift returns you to your body.
   - Take control again, then get hit (a zombie, or `/damage @s 1`): control ends.
   - Take control, then break the hub's Power Port so it goes offline: control ends.
9. **Others see it** (`./gradlew runServer --offline` + this client, optional): a second player looking at a controlled turret sees its barrel follow the controller, and Jade shows "Manual: <name>".
10. **Compat** (`-PwithCompatMods`, Embeddium/Oculus): the camera, HUD and arc still draw. Note any shader issue.
```

- [ ] **Step 2: `docs/PLAN.md` status line** (Overwatch section)

Replace the `**Status**` line with:

```markdown
**Status**: plan `docs/superpowers/plans/2026-10-20-overwatch-link.md`; built <the date the final run passed, e.g. 2026-10-21>, 215 GameTests pass; waiting for the in-game test (`docs/TESTING.md` "Overwatch Link"). Not committed. Release 0.3.0 when the user says so.
```

- [ ] **Step 3: `docs/HANDOFF.md` top note**

Add a dated note above the previous top note:
- the feature is built and uncommitted;
- what passed (GameTest count);
- that the camera/HUD/arc are only checked in game;
- the checklist location;
- the next step: the user runs the checklist, then "commit" → bump `mod_version=0.3.0`, finalize the changelog, tag.

- [ ] **Step 4: Release drafts**

In `docs/release/UPLOAD.md`, add:

```
## Changelog: 0.3.0 (draft)

New:
- Overwatch Link: a Hub Connector with a camera and fire control (Module Workstation). Put it in a turret's Connector slot, then on the Control Hub's map select that turret and press Take control.
- You see from the turret (third person, F5 for the barrel view), aim with the mouse and fire with attack. Manual aim ignores turn speed.
- Mortar shots show their arc and where they land.
- Tesla Coils and Repulsor Domes can be watched.
- Scroll to zoom, arrow keys to switch turret, sneak to leave. Taking damage also ends it.
- Jade and the turret GUI show who is controlling a turret.

Note: clients need this version to join a 0.3.0 server (new network packets).
```

Create `docs/release/DESCRIPTION_UPDATE_0.3.0.md` in the same shape as `DESCRIPTION_UPDATE_0.2.2.md` (title, the "only these parts changed" paragraph, then the part). It has one part: "1. Features: a new bullet after the Turret Control Hub bullet". The text:

Modrinth:

```markdown
- **Overwatch Link**: take control of a turret from the Control Hub. See from it, aim with the mouse (no turn-speed limit) and fire yourself; Mortar shots show their arc. Tesla Coils and Repulsor Domes can be watched.
```

CurseForge:

```html
<li><strong>Overwatch Link</strong>: take control of a turret from the Control Hub. See from it, aim with the mouse (no turn-speed limit) and fire yourself; Mortar shots show their arc. Tesla Coils and Repulsor Domes can be watched.</li>
```

Check that `DESCRIPTION.md` and `DESCRIPTION.curseforge.html` have a Control Hub bullet in a features list. If the list is shaped differently, put the bullet where the Control Hub is described and say where in the file.

- [ ] **Step 5: Final runs**

Run: `./gradlew build --offline > build/build-final.log 2>&1; echo "exit $?"` → `exit 0`.
Run: `./gradlew runGameTestServer --offline > build/gt-final.log 2>&1; echo "exit $?"; grep -E "required tests" build/gt-final.log` → `All 215 required tests passed`.
Run: `./gradlew runGameTestServer --offline -PwithCompatMods > build/gt-final-compat.log 2>&1; echo "exit $?"; grep -E "required tests" build/gt-final-compat.log` → `All 215 required tests passed`.
Run: `git status --short` → only the files this plan names, plus `?? modlist.html` and `?? sfx/`. Nothing is staged.
