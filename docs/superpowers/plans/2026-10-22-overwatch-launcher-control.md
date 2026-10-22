# Overwatch Launcher Control Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Under Overwatch control, the Rocket and Missile Launchers fire one shot per click while loaded, switch Single/Salvo with a key, and the Missile Launcher locks targets (12 clicks = 12 locks in Salvo). The HUD shows numbers on every turret.

**Architecture:**
- Gameplay stays in common code, covered by GameTests:
  - `ManualControl` gains clicks, unlocks and the lock list.
  - `TurretBaseBlockEntity.tickManual` gets a per-click branch, driven by three new `WeaponType` hooks.
  - `MissileLauncherWeapon` / `TurretRocketEntity` handle the filter fallback.
  - `OverwatchSessions` handles the mode toggle and the lock sync.
- The client (`dev.bastion.client.overwatch`) only counts clicks, sends packets and draws: lock brackets and HUD numbers.

**Tech Stack:** Forge 1.20.1 (47.4.26), Java 17, GameTest, fastutil (bundled with Minecraft).

**Spec:** `docs/PLAN.md` §8, "Overwatch: mode, tembak per klik & lock Rocket/Missile". Approved by the user on 2026-10-22 at 17:12. It extends "Overwatch Link" (the section above it); read both.

## Global Constraints

**Git and protected files:**
- **No commits.** Never commit, push or tag. The user's rule: "jangan commit sebelum saya bilang 'commit'". Each task ends with its checks passing, not with a commit.
- Never stage `modlist.html` or `sfx/`.
- Never touch `run/saves/showcase`. Do not edit `run/config/*`.

**Gradle and test runs:**
- Gradle always runs with `--offline`.
- Never start the client (`runClient`); the user runs it. GameTest server runs are fine.
- Save every run to a file, then read the exit code, then grep the file. Never pipe a run straight into grep.
- A GameTest run passes when the log says `All N required tests passed`.
- The whole suite runs every time (about 2 minutes).
- Known flaky tests: `ControlHubTests.satelliteHalvesBreakTogether`, `ControlHubTests.powerPortTakesFeOnlyWhenFormed`, `TurretLargeTests.largeBaseIsOneBlock`, `TurretBaseTests.workstationIsOneBlock` / `destroyedT3KeepsTierOnDamagedBase`, `ControlHubTests.feedHubNewControllerFeeds`. If one of these fails, rerun once before treating the failure as real.
- Multiblock tests wait for formation (`whenOnline`), never a fixed tick.

**Code rules:**
- §9.5: anything importing `net.minecraft.client` lives only in `dev.bastion.client..`. Packets reach client code only through `ClientPacketHandler` and `DistExecutor`, as `OverwatchView` does.
- §1: no vanilla visual assets. Brackets and numbers are drawn with `GuiGraphics.fill` and text, the same way as the HUD's corner frame and its shot marker. No texture is needed. This is my call; it replaces "tekstur custom lewat tools/" in spec item 7, and it still draws custom art.
- §9.6: balance numbers stay in JSON/config. Lock count = the Missile Launcher's `TUBES` (12). The ripple uses `salvo_interval` from the JSON.
- Match the surrounding code: one-line Javadoc on new types and non-obvious methods, `ponytail:` comments for deliberate shortcuts, no extra abstractions.
- Lang: every new key goes into both `en_us.json` and `id_id.json`, with the texts given in Task 5.
- `BastionNetwork.PROTOCOL` goes from `"12"` to `"13"` (Task 1).

**Gameplay rules from the spec (exact):**
- Per click only for **Rocket and Missile Launchers under control**. Every other weapon keeps hold-to-fire.
- A click fires one shot while a tube is loaded, and `fire_interval` is ignored. Holding the button fires once. With every tube empty, the click is dropped (not queued). A reloaded tube can fire at once. Reload speed is unchanged.
- Salvo waits for **all** tubes: Rocket needs 4 loaded and seated; Missile needs 12 loaded **and** 12 locks. A controlled Missile Salvo never launches a partial load, even when the ammo has run out.
- Missile Single: a lockable mob under the crosshair is the target. With none, the missile picks a **random** candidate of the turret's GUI filter.
- Missile Salvo:
  - A click on a lockable mob adds one lock, up to 12. The same mob may be locked many times.
  - A click on empty space is ignored.
  - Right-click (use key) removes the last lock.
  - All locks are cleared on a mode change, a turret switch or a session end.
- A missile chasing a filter target (no lock, a lock that died before launch, or a target lost mid-flight) uses the **filter** for collision and blast as well. It flies past cows and villagers.
- A missile chasing a player's lock keeps the manual rule: everything except the owner and the controller; other players only with PvP on.
- A missile whose target dies mid-flight switches to the filter and picks a random filter target within `RETARGET_RANGE` (10). With none, it flies straight until its fuel runs out.
- Mode key: `key.bastion.overwatch.mode`, default **R**, category `key.categories.bastion`. It flips the turret's own `salvo` setting, which is kept after the session. It does nothing on a weapon without `salvo_interval`.

## Review Focus

These failure modes are not pinned by a spec item. Each one has a test in the task that owns the code:
1. **Several clicks inside one tick**, from a fast clicker or two packets per tick. Expected: one shot per tick, and the extra clicks of that tick are dropped. Test `clicksInOneTickFireOnce` (Task 1).
2. **Clicks while every tube is empty.** Expected: dropped. Nothing fires by itself once a tube reloads. Test `rocketClicksSkipTheFireInterval`, the ignored click at tick 7 (Task 1).
3. **Clicking with no ammo at all.** Expected: the state is `NO_AMMO`, nothing fires, nothing crashes. Test `rocketClicksSkipTheFireInterval`, final part (Task 1).
4. **A locked mob dies before the salvo launches.** Expected: its missiles chase a filter target (the husk), not nothing. Test `deadLockGoesToTheFilter` (Task 3).
5. **The mode is switched to Single in the middle of a Missile Salvo ripple.** Expected: the ripple stops, the remaining tubes stay loaded, and the locks clear. Test `singleStopsTheRipple` (Task 3).

---

### Task 1: One shot per click (Rocket & Missile under control)

**Files:**
- Modify: `src/main/java/dev/bastion/weapon/WeaponType.java` (new hooks)
- Modify: `src/main/java/dev/bastion/weapon/types/RocketLauncherWeapon.java`, `MissileLauncherWeapon.java` (`firesPerClick`)
- Modify: `src/main/java/dev/bastion/turret/ManualControl.java` (clicks, unlocks)
- Modify: `src/main/java/dev/bastion/turret/TurretBaseBlockEntity.java` (`tickManual` → per-click branch)
- Modify: `src/main/java/dev/bastion/network/OverwatchInput.java`, `src/main/java/dev/bastion/hub/OverwatchSessions.java` (`input`), `src/main/java/dev/bastion/network/BastionNetwork.java` (PROTOCOL 13)
- Test: `src/main/java/dev/bastion/gametest/OverwatchTests.java`

**Interfaces:**
- Produces:
  - `WeaponType.firesPerClick() -> boolean`. Default false; Rocket and Missile return true.
  - `WeaponType.manualLocks(WeaponState) -> int`. Default 0. Task 3 overrides it.
  - `WeaponType.manualSalvoReady(StatSheet, WeaponState) -> boolean`. Default `ready(stats, state)`. Task 3 overrides it.
  - `ManualControl.input(Vec3 point, int entityId, boolean trigger, int clicks, int unlocks, long gameTime)`: adds clicks and unlocks to pending counters, each capped at `MAX_PENDING = 12`. The existing 4-arg `input` delegates with `0, 0`.
  - `ManualControl.takeClicks() -> int` and `ManualControl.takeUnlocks() -> int`: return the count and reset it.
  - `OverwatchInput(BlockPos turret, Vec3 point, int entity, boolean trigger, int clicks, int unlocks)`: the two new fields are VarInts, clamped to 0..12 on decode.
  - `OverwatchSessions.input(ServerPlayer, BlockPos, Vec3, int entity, boolean trigger, int clicks, int unlocks)`.

- [ ] **Step 1: Write the failing tests** (ARENA; Rocket Launcher on the small base at `BASE`, 16 `ROCKETS`, cow at `FAR`)

```java
/** Clicks fire each loaded tube at once (fire_interval 36 ignored); a click with all four spent is dropped; a reloaded tube fires on the next click; with no ammo, clicks do nothing. */
@GameTest(template = ARENA, timeoutTicks = 80, batch = "rocketClicksSkipTheFireInterval")
public static void rocketClicksSkipTheFireInterval(GameTestHelper helper)
// onEachTick: control.input(cowCentre, cow.getId(), false, 0, 0, time)
// clicks (input with clicks = 1) at ticks 2, 3, 4, 5 and 7, then 14
// at 9:  assert ammo(turret) == 12 && turret.weaponState().spentTubes == 0b1111   (the click at 7 did nothing)
// at 12: assert ammo(turret) == 12                                                 (tube 0 reloaded ~tick 10; nothing fired by itself)
// at 17: assert ammo(turret) == 11
// then:  empty the ammo slot at 18, click at 20; at 23 assert turret.state() == TurretState.NO_AMMO
//        (the Rocket Launcher pays per shot, so hasAmmo is false) and spentTubes unchanged since 18

/** Holding the attack key fires once: one click, then the trigger held for 40 ticks, is one rocket. */
@GameTest(template = ARENA, timeoutTicks = 60, batch = "holdingFiresOnce")
public static void holdingFiresOnce(GameTestHelper helper)
// click at tick 2 with trigger true; trigger true every tick after with clicks 0; at 45: assert ammo == 15

/** Several clicks reaching the server in one tick fire one shot. */
@GameTest(template = ARENA, timeoutTicks = 40, batch = "clicksInOneTickFireOnce")
public static void clicksInOneTickFireOnce(GameTestHelper helper)
// one input with clicks = 3 at tick 2; at 6: assert ammo == 15

/** Rocket Salvo under control: a click before all four are seated does nothing; once seated, one click sends all four in one tick. */
@GameTest(template = ARENA, timeoutTicks = 80, batch = "rocketSalvoClickNeedsAllFour")
public static void rocketSalvoClickNeedsAllFour(GameTestHelper helper)
// setSalvo(true); weaponState().spentTubes = 0b0001 at start (it reloads at once, then seats for SLIDE_TICKS + 6)
// click at 2; at 4 assert ammo == 16. Click at 40; record ammo every tick: it goes 16 -> 12 within one tick, by 43

/** Missile Single under control: three clicks on three ticks launch three missiles (fire_interval 8 would allow one); holding launches none. */
@GameTest(template = RANGE, timeoutTicks = 60, batch = "missileClicksLaunchEachTube")
public static void missileClicksLaunchEachTube(GameTestHelper helper)
// largeWith(MISSILE_LAUNCHER_TURRET), CREATIVE_AMMO, tubes = ALL_TUBES, cow at (19,2,3) under the crosshair
// trigger held with no clicks for ticks 1..10: at 11 assert bitCount(tubes) == 12
// clicks at 12, 13, 14: at 17 assert bitCount(tubes) == 9
```

Also change `missileFollowsTheCrosshairLock`: one click at tick 2 instead of the trigger held every tick. Keep the `onEachTick` input with `clicks` 0 so the point stays fresh.

- [ ] **Step 2: Run, expect compile errors** (new `input` overload missing)

Run: `./gradlew runGameTestServer --offline > build/gt-t1-red.log 2>&1; echo "exit $?"; grep -E "error:" build/gt-t1-red.log | head`

- [ ] **Step 3: Implement the hooks, `ManualControl` counters, packet fields and PROTOCOL "13"**

The client side of `OverwatchInput` (`OverwatchClient.onClientTick`) sends `0, 0` for now. Task 5 counts the real clicks.

- [ ] **Step 4: Implement the per-click branch in `tickManual`**

`tickManual` keeps its first part: aim, `armed`, `type.tick`, `trigger`. When `type.firesPerClick()`, it then hands off to `private void tickPerClick(ServerLevel level, StatSheet stats, WeaponType type, ManualAim.Aim aim, boolean armed)`. Every other weapon runs the old code unchanged. The order inside `tickPerClick` matters:

```java
int clicks = manual.takeClicks(), unlocks = manual.takeUnlocks();
int need = type.manualLocks(weaponState);
// Task 3 adds lock handling here (uses clicks, unlocks, need); in Task 1 need is always 0.
if (!armed) { chargeTimer = 0; state = TurretState.NO_AMMO; return; }
if (aim == null) { state = TurretState.IDLE; return; }
if (weaponState.salvoActive) { // a launched Missile Salvo ripples out on its own, salvo_interval apart
    if (fireCooldown > 0) { state = TurretState.COOLDOWN; return; }
    fire(level, stats, type);
    return;
}
boolean go = need > 0 ? manual.lockCount() >= need && type.manualSalvoReady(stats, weaponState)  // lockCount(): Task 3
                      : clicks > 0 && type.ready(stats, weaponState);
if (!go) { state = type.ready(stats, weaponState) ? TurretState.IDLE : TurretState.AIMING; return; }
fireCooldown = 0; // ponytail: clicks skip fire_interval; one shot per tick, extra clicks in the same tick are dropped
fire(level, stats, type);
```

Task 1 has no `lockCount()` yet. Write the `need > 0` arm in Task 3, and keep only the click arm here.

- [ ] **Step 5: Build and run all GameTests**

Run: `./gradlew build --offline > build/build-t1.log 2>&1; echo "exit $?"` → `exit 0`.
Run: `./gradlew runGameTestServer --offline > build/gt-t1.log 2>&1; echo "exit $?"; grep -E "required tests|failed!" build/gt-t1.log | head` → `All N required tests passed` (N = 224 + 5).

---

### Task 2: Controlled missiles fall back to the filter

**Files:**
- Modify: `src/main/java/dev/bastion/projectile/TurretRocketEntity.java`
- Modify: `src/main/java/dev/bastion/turret/targeting/TargetSelector.java`
- Modify: `src/main/java/dev/bastion/weapon/types/MissileLauncherWeapon.java`
- Test: `src/main/java/dev/bastion/gametest/OverwatchTests.java`

**Interfaces:**
- Consumes: Task 1's per-click firing.
- Produces:
  - `TurretRocketEntity.underFilter() -> TurretRocketEntity`: sets `controller = null`, so what it hits is decided by the turret's filter.
  - `TurretRocketEntity.randomRetarget() -> TurretRocketEntity`: replaces `retargets(boolean)`, which is deleted along with its field. Once the homing target is gone, the missile sets `controller = null` and then picks a **random** entity among those `affects` accepts within `RETARGET_RANGE`. Missiles without this flag keep the current nearest pick, so automatic missiles are unchanged.
  - `TargetSelector.findAll(TurretBaseBlockEntity, StatSheet, WeaponType, int limit, @Nullable UUID controller) -> List<LivingEntity>`: the existing 4-arg version delegates with `turret.shooter()`. Pass `null` for the filter.
  - `MissileLauncherWeapon`:
    - private `randomEnemy(FireContext) -> @Nullable LivingEntity`: a uniform pick from `findAll(turret, stats, this, Integer.MAX_VALUE, null)` using `context.level().random`.
    - `launch(context, tube, @Nullable LivingEntity target, boolean filter)`: when `filter` is true it calls `.underFilter()`. Every missile fired under control calls `.randomRetarget()`.

Rules for which target a launch uses (spec):
- Under control, Single: `context.target()` (the crosshair mob, already checked by `manualTarget`). When it is null, use `randomEnemy(context)` with `filter = true`.
- Automatic launches: unchanged.

- [ ] **Step 1: Write the failing tests** (RANGE)

```java
/** Under control with nothing under the crosshair, a click sends the missile after the filter's husk; the cow it spares is not hit. */
@GameTest(template = RANGE, timeoutTicks = 300, batch = "unlockedMissileChasesTheFilter")
public static void unlockedMissileChasesTheFilter(GameTestHelper helper)
// launcher as in missileClicksLaunchEachTube; cow (19,2,3), husk (19,2,10); crosshair point = ground spot (12,1,6) centre, entity -1
// click at 2; succeedWhen: hurt(husk) && !hurt(cow)

/** A filter missile flies through a cow: launched straight at it with underFilter(), the cow is not hurt; the same missile without underFilter() under control hits it (existing rule). */
@GameTest(template = ARENA, timeoutTicks = 60, batch = "filterMissilePassesACow")
public static void filterMissilePassesACow(GameTestHelper helper)
// pattern of rocketsKeepTheRuleTheyWereFiredUnder: turret under ManualControl, new TurretRocketEntity(TURRET_MISSILE type,...)
//   .homing(null, 11, 1.1f, 0.07f, 0).underFilter(), from 3 blocks west of the cow at 0.4/tick east; at 40: assert !hurt(cow)

/** A controlled missile whose locked cow vanishes turns to the filter: it goes for the nearby husk, not the second cow. */
@GameTest(template = ARENA, timeoutTicks = 200, batch = "lostLockTurnsToTheFilter")
public static void lostLockTurnsToTheFilter(GameTestHelper helper)
// turret under control; cowA at FAR, husk at FAR.south(3), cowB at FAR.north(3)
// missile .homing(cowA, 11, 1.1f, 0.07f, 0).randomRetarget(), placed 4 blocks above FAR, launched up 0.3; cowA.discard() at tick 1
// succeedWhen: hurt(husk) && !hurt(cowB)
```

- [ ] **Step 2: Run, expect compile errors** (`underFilter`, `randomRetarget` missing). Log: `build/gt-t2-red.log`.

- [ ] **Step 3: Implement** the interfaces above, and update the class Javadoc of `TurretRocketEntity` (filter vs controller rule).

- [ ] **Step 4: Build and run all GameTests.** Logs: `build/build-t2.log`, `build/gt-t2.log`. Expected: all pass. `missileFollowsTheCrosshairLock` must still hit the locked cow.

---

### Task 3: Missile Salvo locks

**Files:**
- Modify: `src/main/java/dev/bastion/turret/ManualControl.java` (lock list)
- Modify: `src/main/java/dev/bastion/turret/TurretBaseBlockEntity.java` (`tickPerClick` lock handling)
- Modify: `src/main/java/dev/bastion/weapon/types/MissileLauncherWeapon.java` (`manualLocks`, `manualSalvoReady`, salvo targets from locks, ripple stop)
- Create: `src/main/java/dev/bastion/network/OverwatchLocks.java` (S→C), and register it in `BastionNetwork`
- Modify: `src/main/java/dev/bastion/hub/OverwatchSessions.java` (send locks when they change)
- Modify: `src/main/java/dev/bastion/client/ClientPacketHandler.java` (`overwatchLocks` → `OverwatchClient.locks(int[])`, stub until Task 5)
- Test: `src/main/java/dev/bastion/gametest/OverwatchTests.java`

**Interfaces:**
- Consumes: `tickPerClick` (Task 1), `randomEnemy` / `launch(..., filter)` (Task 2).
- Produces:
  - `ManualControl` lock methods:
    - `addLock(int entityId)`, `removeLastLock()`, `clearLocks()`
    - `lockCount() -> int`, `lockIds() -> int[]` (in click order)
    - `takeLocksChanged() -> boolean`: true once after any change.
    - The list is a fastutil `IntArrayList`. It is never saved, and a new `ManualControl` (session start or ←/→) starts empty.
  - `MissileLauncherWeapon`:
    - `manualLocks(state)` = `state.salvoMode ? TUBES : 0`
    - `manualSalvoReady(stats, state)` = `!state.salvoActive && state.tubes == ALL_TUBES`. Never a partial load under control.
  - `OverwatchLocks(int[] ids)`: VarInt length capped at 12, then the ids.
  - `OverwatchClient.locks(int[] ids)` and `OverwatchClient.locks() -> int[]` (Task 5 draws them).

Lock handling, inserted in `tickPerClick` before the `armed` check:

```java
if (need == 0) manual.clearLocks(); // locks live only in a click-to-lock mode: any mode change drops them
else {
    for (int i = 0; i < unlocks; i++) manual.removeLastLock();
    LivingEntity mob = manualTarget(stats); // the crosshair mob, in reach and hittable; null: the click is ignored
    for (int i = 0; i < clicks && mob != null && manual.lockCount() < need; i++) manual.addLock(mob.getId());
}
```

After the `fire` call in the lock arm, call `manual.clearLocks()`. `onFire` has already copied the locks.

In `MissileLauncherWeapon.onFire` salvo start, under control (`context.manualPoint() != null`): `state.salvoTargets = context.turret().manual().lockIds()`.

In `salvoTarget(context, k)`, an id that is gone, dead, or no longer `wouldHit`/in range falls back as follows:
- under control: `randomEnemy(context)` with `filter = true`;
- automatic: `context.target()`, as before.

In `MissileLauncherWeapon.tick`, add `if (!state.salvoMode) state.salvoActive = false;`. A switch to Single stops a ripple, also from the GUI in automatic mode.

`OverwatchSessions.onServerTick`, for each valid session: `if (turret.manual().takeLocksChanged()) send(player, new OverwatchLocks(turret.manual().lockIds()))`.

- [ ] **Step 1: Write the failing tests** (RANGE; launcher with `salvo` on, `CREATIVE_AMMO`, `tubes = ALL_TUBES`; cow (19,2,3); husk (19,2,10))

```java
/** Eleven locks on the cow launch nothing; the twelfth launches all twelve, every one at the cow. */
@GameTest(template = RANGE, timeoutTicks = 80, batch = "salvoLaunchesOnTheTwelfthLock")
public static void salvoLaunchesOnTheTwelfthLock(GameTestHelper helper)
// crosshair on the cow; input clicks = 11 at tick 2; at 6 assert lockCount == 11 && bitCount(tubes) == 12
// click at 8; at 11 assert weaponState().salvoTargets is 12 x cow.getId() and lockCount == 0; at 30 assert tubes == 0

/** Locks refuse the owner and a mob that is gone; a click on empty space adds none; right-click removes the last lock. */
@GameTest(template = RANGE, timeoutTicks = 40, batch = "locksRefuseAndUnlock")
public static void locksRefuseAndUnlock(GameTestHelper helper)
// owner = FakePlayer standing at (19,2,6), turret.setOwner(owner.getUUID()); click on owner -> 0 locks
// click on a cow then cow.discard() before the tick -> 0 locks; crosshair on ground, entity -1, click -> 0 locks
// 2 clicks on the cow + 1 on the husk -> lockIds == [cow, cow, husk]; unlocks = 1 -> [cow, cow]
// takeLocksChanged() true after the change, false on the next call

/** Locks clear when the mode goes to Single. */
@GameTest(template = RANGE, timeoutTicks = 40, batch = "locksClearOnModeChange")
public static void locksClearOnModeChange(GameTestHelper helper)
// 5 locks; setSalvo(false); 2 ticks later assert lockCount == 0

/** A controlled salvo never launches a partial load: 7 tubes, no ammo left, 12 locks, nothing launches. */
@GameTest(template = RANGE, timeoutTicks = 60, batch = "salvoNeverLaunchesPartial")
public static void salvoNeverLaunchesPartial(GameTestHelper helper)
// no ammo item; tubes = 0b1111111; 12 locks at tick 2; at 40 assert bitCount(tubes) == 7 && !salvoActive

/** Review Focus 4: a locked cow gone before launch sends its missiles to the filter's husk. */
@GameTest(template = RANGE, timeoutTicks = 300, batch = "deadLockGoesToTheFilter")
public static void deadLockGoesToTheFilter(GameTestHelper helper)
// no ammo item at start, tubes = ALL_TUBES & ~1 (11 loaded: no launch yet; an empty reloadTimer would load at once with ammo)
// 12 clicks on cowA at tick 2; cowA.discard() at 4; CREATIVE_AMMO into the ammo slot at 5 (the 12th tube loads, the salvo launches)
// succeedWhen: hurt(husk) && !hurt(cowB at (19,2,0))

/** Review Focus 5: switching to Single mid-ripple stops it; the rest stay loaded. */
@GameTest(template = RANGE, timeoutTicks = 60, batch = "singleStopsTheRipple")
public static void singleStopsTheRipple(GameTestHelper helper)
// 12 locks at tick 2 (launch starts ~tick 3); setSalvo(false) at tick 5; at 20 assert bitCount(tubes) > 0 && !salvoActive
```

- [ ] **Step 2: Run, expect compile errors.** Log: `build/gt-t3-red.log`.

- [ ] **Step 3: Implement** the interfaces above, register `OverwatchLocks` (PLAY_TO_CLIENT, after `OverwatchStatus`), and add the `ClientPacketHandler` stub.

- [ ] **Step 4: Build and run all GameTests.** Logs: `build/build-t3.log`, `build/gt-t3.log`. Expected: all pass, including the existing automatic salvo tests (`rocketSalvo`, `TurretLargeTests` missile salvo).

---

### Task 4: Mode toggle from Overwatch (server)

**Files:**
- Modify: `src/main/java/dev/bastion/network/OverwatchRequest.java` (`Kind.MODE`, `OverwatchRequest.mode()`)
- Modify: `src/main/java/dev/bastion/hub/OverwatchSessions.java` (`toggleMode`)
- Test: `src/main/java/dev/bastion/gametest/OverwatchTests.java`

**Interfaces:**
- Produces:
  - `OverwatchRequest.mode() -> OverwatchRequest`.
  - `OverwatchSessions.toggleMode(ServerPlayer)`: only for the player's own valid session (`valid(session)`), and only when the weapon's params contain `salvo_interval`. It calls `turret.setSalvo(!turret.salvo())`, then `level.sendBlockUpdated(...)` so the client's `salvo()` (update tag `"Salvo"`) and the HUD follow. Locks clear on their own (Task 3).

- [ ] **Step 1: Write the failing test** (YARD, pattern of `inputOnlyFromTheController`)

```java
/** R flips the controlled launcher's Salvo setting; another player's request and a Gun do nothing. */
@GameTest(template = YARD, timeoutTicks = 80, batch = "modeKeyFlipsSalvo")
public static void modeKeyFlipsSalvo(GameTestHelper helper)
// turret at corner(hub) with ROCKET_LAUNCHER_TURRET + link; whenOnline: start(player)
// toggleMode(second) -> salvo() false; toggleMode(player) -> true; toggleMode(player) -> false
// swap the weapon to GUN_TURRET; toggleMode(player) -> salvo() still false; stop, removeBlock, succeed
```

- [ ] **Step 2: Run, expect a compile error.** Log: `build/gt-t4-red.log`.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Build and run all GameTests.** Logs: `build/build-t4.log`, `build/gt-t4.log`.

---

### Task 5: Client: clicks, R, lock brackets, HUD numbers; docs

**Files:**
- Modify: `src/main/java/dev/bastion/client/overwatch/OverwatchClient.java`
- Modify: `src/main/java/dev/bastion/client/overwatch/OverwatchHud.java`
- Modify: `src/main/java/dev/bastion/client/BastionClient.java` (register `MODE`)
- Modify: `src/main/resources/assets/bastion/lang/en_us.json`, `id_id.json`
- Modify: `docs/TESTING.md`, `docs/HANDOFF.md`, `docs/release/UPLOAD.md`, `docs/PLAN.md` (status line)

**Interfaces:**
- Consumes: `OverwatchInput` 6-arg (Task 1), `OverwatchRequest.mode()` (Task 4), `OverwatchClient.locks()` (Task 3).
- Produces:
  - `OverwatchClient.MODE`: `KeyMapping("key.bastion.overwatch.mode", KeyConflictContext.IN_GAME, KEYSYM, GLFW_KEY_R, "key.categories.bastion")`.
  - `OverwatchClient.hoverLock() -> int`: the entity id for the Single-mode bracket, or -1.

Client behaviour:
- `onClientTick`:
  - `int clicks = 0; while (o.keyAttack.consumeClick()) clicks++;` and the same for `unlocks` with `o.keyUse`.
  - Both are counted only while `mc.screen == null`. They are sent in the existing `OverwatchInput`.
  - `while (MODE.consumeClick()) send OverwatchRequest.mode()`.
  - `begin()` and `end()` reset the locks to an empty array.
- `aimFrom`: `hoverLock` = the picked entity's id when all of these hold:
  - the weapon type is `MISSILE_LAUNCHER`;
  - `!base.salvo()`;
  - the entity is a `LivingEntity` other than `mc.player`;
  - it is within `stats.range()` (the controlled range) of the pivot.

  Otherwise `hoverLock` is -1.
- HUD lock brackets:
  - Pull the projection in `marker()` out into `@Nullable double[] project(Vec3 at, int width, int height)`, in GUI pixels, null when behind the camera.
  - For `hoverLock` and every distinct id in `locks()`, project the 8 corners of the entity's bounding box. Skip the entity if any corner is behind the camera.
  - Draw four amber corner brackets round the min/max rectangle, in screen pixels like `marker()`. The corner length is a quarter of the shorter side, at least 2 px.
  - When an id appears N ≥ 2 times in `locks()`, draw `×N` in `AMBER` just right of the top-right corner.
- HUD rows (`bars`):
  - `bar(...)` gains a `String value`, drawn at `bx + BAR_W + 4` in `TEXT`.
  - HP: `round(health)/round(maxHealth)` from `status()`. Before the first status arrives, show the percentage of `clientHealth()`.
  - AMMO: `status.ammo()`. FE: `amount(status.energy())`, where `amount(int)` gives `950`, `12.4k`, `1.2M`.
  - Launchers: a **TUBES** row with value `loaded/total`, replacing their RELOAD row.
  - HEAT, CHARGE and the slow weapons' RELOAD: a percentage.
  - Salvo-capable weapons: a first text row `MODE` showing `SINGLE` / `SALVO` from `base.salvo()`.
  - Missile in Salvo: a `LOCK` bar with fill `locks/12` and value `n/12`.
- Lock sound (spec default 10): `OverwatchClient.locks(int[])` plays `BastionSounds.CONTROL_HUB_UI_SELECT` via `SimpleSoundInstance.forUI` when the count changes: pitch 1.4, volume 0.5 for a lock added; pitch 0.9 for one removed. A clear after launch stays silent, because the launch has its own sound.
- Hints: for weapons with `salvo_interval`, append ` · ` + `hud.bastion.overwatch.hint_mode` (`MODE` key). For Missile in Salvo, also append `hud.bastion.overwatch.hint_unlock` (use key).

Lang (en_us / id_id):

| key | en_us | id_id |
|---|---|---|
| `key.bastion.overwatch.mode` | `Overwatch: fire mode (launchers)` | `Overwatch: mode tembak (launcher)` |
| `hud.bastion.overwatch.tubes` | `TUBES` | `TABUNG` |
| `hud.bastion.overwatch.lock` | `LOCK` | `LOCK` |
| `hud.bastion.overwatch.mode` | `MODE` | `MODE` |
| `hud.bastion.overwatch.single` | `SINGLE` | `SINGLE` |
| `hud.bastion.overwatch.salvo` | `SALVO` | `SALVO` |
| `hud.bastion.overwatch.hint_mode` | `%s mode` | `%s mode` |
| `hud.bastion.overwatch.hint_unlock` | `%s undo lock` | `%s hapus lock` |

Append to `jei.bastion.info.overwatch_link`:
- en_us: ` Rocket and Missile Launchers fire one shot per click while loaded; R switches Single/Salvo. In Salvo, click a mob to lock a missile on it (12 locks launch, one mob can take several); right-click undoes the last lock.`
- id_id: ` Rocket dan Missile Launcher menembak satu kali per klik selama tabung terisi; R mengganti Single/Salvo. Di Salvo, klik mob untuk mengunci satu missile padanya (12 lock = meluncur, satu mob bisa dikunci berkali-kali); klik kanan menghapus lock terakhir.`

- [ ] **Step 1: Implement the client parts and lang.** Insert the lang entries after `hud.bastion.overwatch.hints` with the json round-trip used in the Overwatch Link plan.
- [ ] **Step 2: Build and run all GameTests.** Logs: `build/build-t5.log`, `build/gt-t5.log`. Expected: `exit 0` and all pass. The client code is checked by compiling; the user tests it in game.
- [ ] **Step 3: Docs**
  - `docs/TESTING.md`: a new section `## Overwatch: launchers (2026-10-22)` after "Overwatch Link", with the steps below.
  - `docs/release/UPLOAD.md`: a new `## Changelog: (next)` entry. The user picks the version number at commit time.
  - `docs/HANDOFF.md` §0: a new top paragraph.
  - `docs/PLAN.md`: the section's Status line becomes "dibangun 2026-10-22, belum dites user di game".

TESTING.md steps (English, like the rest of the file):
1. **R switches mode**: control a Rocket Launcher; press R. The HUD's MODE row flips between SINGLE and SALVO. Open the turret's GUI after leaving: it shows the same mode.
2. **Click speed**: Rocket in Single with 4 loaded; click 4 times fast. 4 rockets leave about as fast as you click. Click again at once: nothing. When one tube reloads (TUBES 1/4), a click fires it.
3. **Holding**: hold attack on a loaded Rocket. One rocket only.
4. **Rocket Salvo**: in SALVO, click while TUBES shows 3/4: nothing. At 4/4: all four leave together.
5. **Missile Single**:
   - Put the crosshair on a zombie: amber brackets frame it. Click: the missile goes for it.
   - Aim at empty ground with a zombie and a cow in range: the missile goes for the zombie and never the cow, even if the cow is in the way.
6. **Missile Salvo locks**:
   - Click one zombie 3 times: brackets with ×3, and LOCK 3/12.
   - Right-click: ×2, LOCK 2/12.
   - Click empty sky: no lock.
   - Lock 12 times in total (spread over 2–3 mobs): all 12 launch once TUBES shows 12/12. Each mob gets its count.
7. **Lock gone**: lock a zombie 12 times while the tubes are still loading, then kill it with another turret before launch. The missiles go for another hostile, not a cow.
8. **Locks clear**: with some locks, press R, or ←/→ to another turret and back: LOCK is gone.
9. **Numbers on every turret**:
   - HP shows `now/max`, AMMO an item count, FE `12.4k` style, TUBES on launchers.
   - HEAT/CHARGE/RELOAD show %.
   - At GUI scale 3–4 nothing runs off the screen.
10. **Automatic mode unchanged**: let go. The launchers fire on their own as before (salvo: all four / twelve spread over targets).
