# Ion Rifle, Plasma Repeater & Plasma Cannon Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Three new turret weapons: the Ion Rifle (1x1, FE: a fast bolt whose ion core zaps every target within 3 blocks of where it stops, star-shaped beams like Crimsonland's Ion Rifle), the Plasma Repeater (1x1, Plasma Cells: slow visible plasma balls with a small splash and Machine-Gun heat) and the Plasma Cannon (2x2, Plasma Cells: a big slow ball that bursts into a ring of 12 small balls), plus the Plasma Cell ammo.

**Architecture:** All three fly on the existing entity-free `Bullets` system (travel time, lead, valid targets only, stop at blocks) and hook their effect into `Bullets.Hit.hit/blocked`. Shared pieces: `Splash` (who a burst reaches, by distance to the box, no line of sight), `Bullets.filtered` (targets without the range limit), `Bullets.create/launch` (bullets from anywhere, safe to launch from inside another bullet's hit). Two new turret-event packets tell clients what only the server knows: `IonField` (which targets the core zapped) and `PlasmaBurst` (the fragment ids and paths). On the client, `TracerRenderer` gains plasma "orb" tracers, trail lengths in blocks, early landings and `onLanding(id, task)` so the field and the fragments appear exactly when the parent bolt's tracer arrives. New `IonEffects` / `PlasmaEffects` follow the `LaserEffects` pattern; beams reuse `BeamRenderer`, the Cannon's charge reuses the Laser's charge orb.

**Tech Stack:** Forge 1.20.1, Java 17, GeckoLib 4.8.4, Forge GameTests, Python 3 generators (`tools/`).

**Spec:** `docs/PLAN.md` → section 8 "Ion Rifle, Plasma Repeater & Plasma Cannon" (design approved by the user 2026-10-08 17:09–17:11, build go-ahead 2026-10-09 23:40).

## Global Constraints

- No new dependencies. Client code (`net.minecraft.client`) only in `dev.bastion.client` packages and `src/dev`.
- NO-VANILLA visuals and sounds: every particle, texture and sound comes from our VFX framework and generators.
- Effect colours: plasma `#3DFFB5`, ion `#8099FF` (both weapons' `energy_color`).
- Ion Rifle JSON: range 32, fire_interval 16, damage 8, turn_speed 8, aim_tolerance 1.5, pitch -35..90, ammo `bastion:ammo/energy`, ammo_per_shot 0; params bolt_speed 4, lead_accuracy 0.9, field_radius 3, field_damage 5, field_ticks 6, energy_per_shot 1200, energy_capacity 40000, max_input 1000.
- Plasma Repeater JSON: range 24, fire_interval 5, damage 5, spread 0.8, turn_speed 10, aim_tolerance 2, pitch -35..90, ammo `bastion:ammo/plasma`, ammo_per_shot 0.1, heat_per_shot 8, heat_dissipation_per_second 15, max_heat 100, overheat_ticks 60; params bolt_speed 1.5, lead_accuracy 0.9, splash_damage 3, splash_radius 1.2.
- Plasma Cannon JSON: range 40, **fire_interval 30 + charge_ticks 20 = the spec's 50-tick cycle** (the state machine charges only after the cooldown, so the spec's "fire_interval 50 incl. charge" becomes 30 + 20; PLAN text is corrected in Task 5), damage 18, turn_speed 4, aim_tolerance 1.5, pitch -30..60, ammo `bastion:ammo/plasma`, ammo_per_shot 1; params orb_speed 0.8, lead_accuracy 0.9, min_range 4, fragments 12, fragment_damage 6, fragment_range 6, fragment_speed 1.0.
- Plasma Cell: stack 16, tag `bastion:ammo/plasma`; Empty Plasma Cell at the Ammo Workstation = 2 iron ingot + 1 glass + 1 glowstone dust → 4 (40 t, 400 FE); charged at the Charging Station 80 t, 4,000 FE.
- Speed keys are `bolt_speed` / `orb_speed` / `fragment_speed`, never `bullet_speed` / `slug_speed` (so `ModifierEffect.affects` never lists the Penetrator for these weapons). All three pass pierce 0.
- Field, splash and fragments: valid targets only (filter, no range limit), no block damage, never the firing base, no line of sight needed for the field/splash.
- Names (en_us / id_id): Ion Rifle Turret / Turret Senapan Ion; Plasma Repeater Turret / Turret Pelontar Plasma; Plasma Cannon Turret / Turret Meriam Plasma; Plasma Cell / Sel Plasma; Empty Plasma Cell / Sel Plasma Kosong. Items `ion_rifle_turret`, `plasma_repeater_turret` (small modules), `plasma_cannon_turret` (large module), `plasma_cell`, `empty_plasma_cell`.
- Sound events (exact ids, from `docs/SOUND_SHEET.md`): `ion.fire`, `ion.hit`, `plasma_repeater.fire`, `plasma.hit_small`, `plasma.vent`, `plasma_cannon.charge`, `plasma_cannon.fire`, `plasma_cannon.hit`, `plasma.fragment`. Placeholders only; `tools/gen_sounds.py` must stop overwriting existing sound files (the user is replacing them by hand).
- Never commit until the user says "commit"; never commit `modlist.html`; never touch `run/saves/showcase` (scenes use `-PshowcaseWorld=showcase_fx`); Gradle always `--offline`.

## Review Focus

1. **A shot that lands on a block** (Ion bolt into a wall, Cannon orb into the floor): the core/burst still happens where it stopped, the field reaches through a thin wall, and the Cannon's ring starts at least half a block above the floor. Tests: `ionZapsWhereTheBoltStops` (Task 3), `orbOnTheFloorRaisesTheRing` (Task 5).
2. **Non-targets beside the blast** (villagers, pets, cows, the owner): never touched by the field, the splash or the fragments. Tests: cows in `ionZapsEveryTargetAround`, `ionZapsWhereTheBoltStops`, `repeaterSplashesAround`, `cannonRingHitsAround`.
3. **A mob farm inside one ion field** (>64 mobs): every one is damaged, the beam list sent to clients is capped at 64 so the packet can never exceed the decoder's limit and disconnect players. Test: `ionFieldPacketIsCapped` (Task 3).
4. **The Cannon firing straight up or down** (a flyer overhead, lead pointing vertical): the ring is still 12 flat, evenly spaced directions. Test: `plasmaRingShape` (Task 5).
5. **Fragments launched while the level is stepping its bullets** (every orb that hits after its first tick): no ConcurrentModificationException, the fragments fly and hit. Test: `bulletsLaunchedFromAHitFly` (Task 1).

---

## File map

| File | Change | Responsibility |
|---|---|---|
| `src/main/java/dev/bastion/weapon/Splash.java` | create | who a burst reaches: box distance, no line of sight, nearest first |
| `src/main/java/dev/bastion/weapon/Bullets.java` | modify | `filtered`, `create`/`launch`, safe launch during stepping, public `direction` |
| `src/main/java/dev/bastion/damage/BastionDamageTypes.java`, `data/bastion/damage_type/turret_plasma.json` | modify/create | plasma damage type + death messages |
| `src/main/java/dev/bastion/client/render/TracerRenderer.java` | modify | orb tracers, trail in blocks, early landings, `onLanding` |
| `src/main/java/dev/bastion/client/render/LaserChargeRenderer.java` | modify | `billboard` package-private; Plasma Cannon charge orb (Task 5) |
| `src/main/java/dev/bastion/client/TurretFireEffects.java` | modify | `playShot` package-private; Ion/Plasma branches |
| `src/main/java/dev/bastion/registry/BastionSounds.java`, `assets/bastion/sounds.json`, `tools/gen_sounds.py` | modify | 9 sound events, placeholders, write-missing-only |
| `src/main/java/dev/bastion/registry/BastionItems.java` | modify | cells, 3 weapon modules, 9 parts, tab predicates |
| `data/bastion/tags/items/ammo/plasma.json`, `ammo.json` | create/modify | plasma ammo tag |
| `tools/gen_textures.py`, `tools/gen_recipes.py`, `tools/fit_item_display.py`, `assets/bastion/parts.json` | modify | cell sprites, model palettes, recipes, item displays, part pieces |
| `src/main/java/dev/bastion/weapon/types/IonRifleWeapon.java`, `PlasmaRepeaterWeapon.java`, `PlasmaCannonWeapon.java` | create | fire logic |
| `data/bastion/turret_weapons/ion_rifle.json`, `plasma_repeater.json`, `plasma_cannon.json` | create | numbers |
| `src/main/java/dev/bastion/registry/BastionWeaponTypes.java` | modify | 3 weapon types |
| `src/main/java/dev/bastion/network/IonField.java`, `PlasmaBurst.java`, `TurretEvents.java`, `BastionNetwork.java` | create/modify | packets, protocol 9 |
| `src/main/java/dev/bastion/client/IonEffects.java`, `PlasmaEffects.java`, `ClientPacketHandler.java`, `ClientTurret.java`, `vfx/WeaponFx.java`, `vfx/VfxPresets.java` | create/modify | visuals, sounds, charge and heat looks |
| `tools/blockout/design_weapons.py`, `assets/bastion/geo/*`, `animations/*`, `textures/item/*`, `models/item/*` | modify/generate | 3 models + clips |
| `src/main/java/dev/bastion/gametest/TurretEnergyTests.java`, `TurretWorkstationTests.java` | create/modify | 15 GameTests |
| `src/dev/java/dev/bastion/dev/EnergyShowcase.java`, `Showcase.java` | create/modify | scenes `-Pshowcase=ion`, `-Pshowcase=plasma` |
| lang en_us/id_id, `docs/PLAN.md`, `docs/TESTING.md`, `docs/SOUND_SHEET.md`, `docs/release/*`, `docs/HANDOFF.md` | modify | strings and docs |

GameTest command: `./gradlew runGameTestServer --offline` (all tests; the log ends with "All N required tests passed"). A single batch can be checked faster by reading its line in `run/gametestworld` logs, but always finish a task with the full run. Baseline: **113** tests.

---

### Task 1: Shared plumbing (Splash, Bullets, plasma damage type, client tracers)

**Files:**
- Create: `src/main/java/dev/bastion/weapon/Splash.java`, `src/main/java/dev/bastion/gametest/TurretEnergyTests.java`, `src/main/resources/data/bastion/damage_type/turret_plasma.json`
- Modify: `src/main/java/dev/bastion/weapon/Bullets.java`, `src/main/java/dev/bastion/damage/BastionDamageTypes.java`, `src/main/java/dev/bastion/client/render/TracerRenderer.java`, `src/main/java/dev/bastion/client/render/LaserChargeRenderer.java:61` (`billboard`), `src/main/java/dev/bastion/client/TurretFireEffects.java` (`playShot`), lang en_us/id_id

**Interfaces:**
- Produces: `static List<LivingEntity> Splash.around(ServerLevel, Vec3 at, double radius, Predicate<Entity> canHit, @Nullable Entity except)`, `static double Splash.distance(Vec3, AABB)`.
- Produces: `static Predicate<Entity> Bullets.filtered(FireContext, WeaponType)`, `static List<Bullets.Bullet> Bullets.create(ServerLevel, List<Vec3> starts, List<Vec3> directions, double speed, double range, int pierce, Predicate<Entity>, Bullets.Hit)`, `static void Bullets.launch(List<Bullets.Bullet>)`, field `public final Vec3 Bullets.Bullet.direction`.
- Produces: `BastionDamageTypes.TURRET_PLASMA`.
- Produces (client): `TracerRenderer.spawnStreak(int id, Vec3 from, Vec3 end, double speed, int argb, float width, float trailBlocks, Landing)`, `TracerRenderer.spawnOrb(int id, Vec3 from, Vec3 end, double speed, int argb, float radius, float trailBlocks, Landing)`, `TracerRenderer.onLanding(int id, Runnable task)`; `static void LaserChargeRenderer.billboard(...)` (package-private); `static void TurretFireEffects.playShot(ClientLevel, TurretFireEvent, Vec3, WeaponFx, RandomSource)` (package-private).

- [ ] **Step 1: Write the failing tests.** Create `TurretEnergyTests.java`:

```java
package dev.bastion.gametest;

import dev.bastion.Bastion;
import dev.bastion.weapon.Bullets;
import dev.bastion.weapon.Splash;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.function.Predicate;

/** Ion Rifle, Plasma Repeater, Plasma Cannon and their shared pieces (PLAN "Ion Rifle, Plasma Repeater & Plasma Cannon"). */
@GameTestHolder(Bastion.MOD_ID)
@PrefixGameTestTemplate(false)
public class TurretEnergyTests {
    private static final String ARENA = "arena", RANGE = "range";
    private static final Predicate<Entity> HUSKS = e -> e instanceof Husk;

    /** Health lost so far (the mobs here never die mid-test). */
    static float lost(LivingEntity entity) {
        return entity.getMaxHealth() - entity.getHealth();
    }

    /**
     * Who a burst reaches: a box within the radius by its nearest edge (not its centre), one behind a wall too (no line of
     * sight), never the excluded one, never one the predicate refuses (the cow), nearest first.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "splashReachesBoxesInRadius")
    public static void splashReachesBoxesInRadius(GameTestHelper helper) {
        LivingEntity struck = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(6, 2, 3));
        LivingEntity near = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(8, 2, 3));   // box edge 1.7 from the point
        LivingEntity far = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(10, 2, 3));   // 3.7
        for (int x = 5; x <= 7; x++) for (int y = 1; y <= 3; y++) helper.setBlock(new BlockPos(x, y, 2), Blocks.STONE);
        LivingEntity walled = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(6, 2, 1)); // 1.7, behind the wall
        LivingEntity cow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(6, 2, 5));
        helper.runAtTickTime(20, () -> {
            Vec3 at = helper.absoluteVec(new Vec3(6.5, 1.5, 3.5));
            List<LivingEntity> got = Splash.around(helper.getLevel(), at, 3, HUSKS, struck);
            helper.assertTrue(got.contains(near) && got.contains(walled), "missed the near or the walled husk: " + got);
            helper.assertTrue(!got.contains(far) && !got.contains(struck) && !got.contains(cow), "reached too much: " + got);
            helper.assertTrue(got.size() == 2, "expected exactly two, got " + got);
            helper.succeed();
        });
    }

    /**
     * Plasma Cannon fragments are bullets launched from inside another bullet's hit, while the level steps its bullets: they
     * must fly and hit (no ConcurrentModificationException), and only what the child's predicate allows.
     */
    @GameTest(template = ARENA, timeoutTicks = 80, batch = "bulletsLaunchedFromAHitFly")
    public static void bulletsLaunchedFromAHitFly(GameTestHelper helper) {
        LivingEntity first = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(8, 2, 3));
        LivingEntity second = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(8, 2, 1));
        var level = helper.getLevel();
        Bullets.Hit child = (bullet, hit) -> hit.getEntity().hurt(level.damageSources().generic(), 3);
        Bullets.Hit parent = (bullet, hit) -> {
            Entity struck = hit.getEntity();
            struck.hurt(level.damageSources().generic(), 3);
            // From the struck husk's middle (its box edge would graze the second husk's), which the child may not hit.
            Bullets.launch(Bullets.create(level, List.of(struck.position().add(0, 1, 0)), List.of(new Vec3(0, 0, -1)), 1, 6, 0,
                    HUSKS.and(e -> e != struck), child));
        };
        // 1 block per tick from 6 blocks out: the parent hits during a later tick's step, not in launch().
        helper.runAtTickTime(20, () -> Bullets.launch(Bullets.create(level, List.of(helper.absoluteVec(new Vec3(2.5, 2, 3.5))),
                List.of(new Vec3(1, 0, 0)), 1, 10, 0, HUSKS, parent)));
        helper.succeedWhen(() -> {
            helper.assertTrue(lost(first) > 0, "the parent bullet never hit");
            helper.assertTrue(lost(second) > 0, "the bullet launched from the hit never hit");
            helper.assertTrue(lost(first) < 4, "the child hit the parent's target too");
        });
    }
}
```

- [ ] **Step 2: Run to verify it fails.** `./gradlew runGameTestServer --offline`. Expected: compile error (`Splash`, `Bullets.create`, `Bullets.launch` not defined).

- [ ] **Step 3: Implement `Splash`.**

```java
package dev.bastion.weapon;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * Who a burst reaches (Ion Rifle field, Plasma Repeater splash): every entity {@code canHit} accepts whose box comes
 * within {@code radius} of the point, nearest first. Distance is to the box, not its centre, so a big mob counts as soon
 * as its edge is in. No line of sight: a burst on one side of a thin wall reaches the other (PLAN "Ion Rifle").
 */
public final class Splash {
    private Splash() {
    }

    public static List<LivingEntity> around(ServerLevel level, Vec3 at, double radius, Predicate<Entity> canHit, @Nullable Entity except) {
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius),
                        e -> e != except && canHit.test(e) && distance(at, e.getBoundingBox()) <= radius)
                .stream().sorted(Comparator.comparingDouble(e -> distance(at, e.getBoundingBox()))).toList();
    }

    /** Distance from {@code p} to the nearest point of {@code box}, 0 inside it. */
    public static double distance(Vec3 p, AABB box) {
        double dx = Math.max(Math.max(box.minX - p.x, 0), p.x - box.maxX);
        double dy = Math.max(Math.max(box.minY - p.y, 0), p.y - box.maxY);
        double dz = Math.max(Math.max(box.minZ - p.z, 0), p.z - box.maxZ);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
```

- [ ] **Step 4: Bullets.** In `Bullets.Bullet` change `private final Vec3 direction;` to `public final Vec3 direction;` (javadoc: `/** Unit vector of flight (Plasma Cannon: its ring is laid out around it). */`). Add fields and replace `fire`:

```java
    /** Bullets launched from inside another bullet's hit while the level steps its list (Plasma Cannon fragments); joined after the step. */
    private static final List<Bullet> SPAWNED = new ArrayList<>();
    private static boolean stepping;

    public static void fire(FireContext context, WeaponType type, int muzzle, List<Vec3> directions, double speed, int pierce,
                            Predicate<Entity> canHit, Hit onHit) {
        double range = context.stats().range();
        List<Bullet> fired = create(context.level(), Collections.nCopies(directions.size(), context.muzzle()), directions, speed, range,
                pierce, canHit, onHit);
        List<TurretFireEvent.Shot> shots = new ArrayList<>(fired.size());
        // The path's far end; clients stop the tracer early when the impact for this id arrives.
        for (Bullet b : fired) shots.add(new TurretFireEvent.Shot(b.origin.add(b.direction.scale(range)), b.direction.reverse(), TurretFireEvent.MISS, b.id));
        ShotReport.send(context, type, muzzle, (float) speed, shots);
        launch(fired);
    }

    /**
     * Bullets that leave from anywhere, not a muzzle (Plasma Cannon fragments): one per start and direction. Tell clients
     * their ids first, then {@link #launch} them.
     */
    public static List<Bullet> create(ServerLevel level, List<Vec3> starts, List<Vec3> directions, double speed, double range, int pierce,
                                      Predicate<Entity> canHit, Hit onHit) {
        List<Bullet> out = new ArrayList<>(directions.size());
        for (int i = 0; i < directions.size(); i++) out.add(new Bullet(level, starts.get(i), directions.get(i), speed, range, pierce, canHit, onHit));
        return out;
    }

    /**
     * Moves each bullet once right away, so a target at point blank is hit this tick as with hitscan, and keeps the ones
     * still flying. Launched from inside another bullet's hit, they join the level's list after this tick's step.
     */
    public static void launch(List<Bullet> bullets) {
        for (Bullet bullet : bullets) {
            if (!step(bullet)) continue;
            if (stepping) SPAWNED.add(bullet);
            else add(bullet);
        }
    }

    private static void add(Bullet bullet) {
        List<Bullet> active = ACTIVE.computeIfAbsent(bullet.level.dimension(), k -> new ArrayList<>());
        if (active.size() >= MAX_PER_LEVEL) end(active.remove(0), null);
        active.add(bullet);
    }

    /**
     * The turret's targets without its range limit: what a field, a splash or a fragment may hit where a shot landed,
     * which can be a little past the range targets are picked from.
     */
    public static Predicate<Entity> filtered(FireContext context, WeaponType type) {
        TurretBaseBlockEntity turret = context.turret();
        return e -> Hitscan.canHit(e) && e instanceof LivingEntity living && !living.isInvulnerable()
                && turret.filter().test(living, turret.owner()) && type.canTarget(living, turret);
    }
```

In `onLevelTick` replace the START branch:

```java
        if (event.phase == TickEvent.Phase.START) {
            List<Bullet> active = ACTIVE.get(level.dimension());
            if (active != null) {
                stepping = true;
                try {
                    active.removeIf(bullet -> !step(bullet));
                } finally {
                    stepping = false;
                }
            }
            SPAWNED.forEach(Bullets::add);
            SPAWNED.clear();
            return;
        }
```

`onServerStopping` also does `SPAWNED.clear();`. Imports: `java.util.Collections`, `dev.bastion.turret.TurretBaseBlockEntity`. Update the class javadoc's weapon list: "for the Gun, Machine Gun, Shotgun, Sniper, Railgun, Ion Rifle and both Plasma weapons".

- [ ] **Step 5: Plasma damage type.** `data/bastion/damage_type/turret_plasma.json`:

```json
{
  "message_id": "bastion.turret_plasma",
  "exhaustion": 0.1,
  "scaling": "when_caused_by_living_non_player"
}
```

`BastionDamageTypes`: `/** Plasma Repeater and Plasma Cannon; not is_fire, so fire-immune mobs still take it. */ public static final ResourceKey<DamageType> TURRET_PLASMA = ResourceKey.create(Registries.DAMAGE_TYPE, Bastion.id("turret_plasma"));`
Lang en_us: `"death.attack.bastion.turret_plasma": "%1$s was melted by a turret"`, `"death.attack.bastion.turret_plasma.player": "%1$s was melted by %2$s's turret"`; id_id: `"%1$s dilelehkan plasma turret"`, `"%1$s dilelehkan plasma turret milik %2$s"`.

- [ ] **Step 6: Client tracers.** In `TracerRenderer`:
  - Add to `Tracer`: `final float orb;` (radius of a plasma ball, 0 = a streak), `final int trailPoints;` (≤0 = the quality preset's `VfxManager.trailLength()`), `final List<Runnable> then = new ArrayList<>();` (tasks chained to the landing). Constructor gains `(…, float orb, int trailPoints)`; existing `spawn(...)` and `spawnBullet(...)` pass `0, -1`.
  - In `tick()`: `int max = t.trailPoints > 0 ? t.trailPoints : VfxManager.trailLength(); while (t.trail.size() > max) t.trail.removeLast();`, and at its top `now++; EARLY.values().removeIf(l -> now - l.tick() > EARLY_TICKS);`. `clear()` also clears `EARLY`.
  - New code:

```java
    private static final ResourceLocation ORB = Bastion.id("textures/vfx/orb.png");
    /** Impacts that came before their tracer existed (fragments spawn when their orb lands); applied on spawn, dropped after 2 s. */
    private static final Map<Integer, Landed> EARLY = new HashMap<>();
    private static final int EARLY_TICKS = 40;
    private static long now;

    private record Landed(Vec3 end, Vec3 normal, int hit, int blockState, long tick) {
    }

    /** A streak like spawnBullet whose ribbon trails {@code trailBlocks} behind the head (Ion Rifle). */
    public static void spawnStreak(int id, Vec3 from, Vec3 end, double speed, int argb, float width, float trailBlocks, Landing landing) {
        track(new Tracer(id, from, end, argb, width, speed, null, landing, 0, points(trailBlocks, speed)));
    }

    /** A glowing ball of {@code radius} instead of a streak (Plasma Repeater, Plasma Cannon and its fragments). */
    public static void spawnOrb(int id, Vec3 from, Vec3 end, double speed, int argb, float radius, float trailBlocks, Landing landing) {
        track(new Tracer(id, from, end, argb, radius, speed, null, landing, radius, points(trailBlocks, speed)));
    }

    /**
     * Runs {@code task} when bullet {@code id}'s tracer lands (the Ion Rifle's beams, the Plasma Cannon's ring), or now when
     * it has no tracer (dropped over the cap, never spawned). The server sends these before the bullet's own impact.
     */
    public static void onLanding(int id, Runnable task) {
        Tracer tracer = BULLETS.get(id);
        if (tracer == null) task.run();
        else tracer.then.add(task);
    }

    private static int points(float blocks, double speed) {
        return Math.max(2, (int) Math.ceil(blocks / Math.max(speed, 1e-3)));
    }

    private static void track(Tracer tracer) {
        add(tracer);
        BULLETS.put(tracer.id, tracer);
        Landed early = EARLY.remove(tracer.id);
        if (early != null) land(tracer.id, early.end(), early.normal(), early.hit(), early.blockState());
    }
```

  `spawnBullet` becomes `track(new Tracer(id, from, end, argb, width, speed, null, landing, 0, -1));`. `land(...)`:

```java
    public static void land(int id, Vec3 end, Vec3 normal, int hit, int blockState) {
        Tracer tracer = BULLETS.remove(id);
        if (tracer == null) { // not spawned yet (a fragment whose orb is still flying) or dropped over the cap
            if (EARLY.size() < MAX_ACTIVE) EARLY.put(id, new Landed(end, normal, hit, blockState, now));
            return;
        }
        tracer.setEnd(end);
        tracer.landing.known(end);
        tracer.onArrive = () -> {
            tracer.landing.land(end, normal, hit, blockState);
            tracer.then.forEach(Runnable::run);
        };
    }
```

  `add(...)`: when over the cap, `Tracer old = ACTIVE.remove(0); remove(old); if (!old.arrived) old.then.forEach(Runnable::run);` (a dropped orb still bursts).
  - `render(...)`: streak segments only when `t.orb == 0`; ribbons for all; then the orbs, each pass in its own buffer:

```java
        if (ACTIVE.stream().noneMatch(t -> t.orb > 0)) return;
        // Plasma balls: a deep halo (reads by day), a coloured glow and a white core, breathing a little.
        Quaternionf facing = camera.rotation();
        VertexConsumer halo = buffers.getBuffer(BastionRenderTypes.translucentGlow(ORB));
        for (Tracer t : ACTIVE) {
            if (t.orb <= 0) continue;
            float r = pulse(t, partialTick);
            LaserChargeRenderer.billboard(halo, pose, cam, facing, t.head(t.age + partialTick), r * 2.4f, r * 2.4f,
                    ArcRenderer.deepen(t.argb), 0.45f * alpha(t, partialTick));
        }
        VertexConsumer glow = buffers.getBuffer(BastionRenderTypes.additiveGlow(ORB));
        for (Tracer t : ACTIVE) {
            if (t.orb <= 0) continue;
            float r = pulse(t, partialTick), a = alpha(t, partialTick);
            Vec3 head = t.head(t.age + partialTick);
            LaserChargeRenderer.billboard(glow, pose, cam, facing, head, r * 1.5f, r * 1.5f, t.argb, a);
            LaserChargeRenderer.billboard(glow, pose, cam, facing, head, r * 0.65f, r * 0.65f, 0xFFFFFF, a);
        }
```

  with

```java
    private static float alpha(Tracer t, float partialTick) {
        float time = t.age + partialTick, arrival = t.arrival();
        return time <= arrival ? 1 : Mth.clamp(1 - (time - arrival) / FADE_TICKS, 0, 1);
    }

    /** A plasma ball's radius, breathing ~8 %. */
    private static float pulse(Tracer t, float partialTick) {
        return t.orb * (1 + 0.08f * Mth.sin((t.age + partialTick) * 0.9f));
    }
```

  (the streak loop uses `alpha(t, partialTick)` too). Import `org.joml.Quaternionf`.
  - `LaserChargeRenderer.billboard`: drop `private` (package-private, reused by `TracerRenderer`).
  - `TurretFireEffects.playShot`: drop `private` (package-private, reused by `IonEffects`/`PlasmaEffects`).

- [ ] **Step 7: Run to verify it passes.** `./gradlew build --offline` then `./gradlew runGameTestServer --offline`. Expected: "All 115 required tests passed".

---

### Task 2: Sound events, placeholders, Plasma Cell

**Files:**
- Modify: `src/main/java/dev/bastion/registry/BastionSounds.java`, `src/main/resources/assets/bastion/sounds.json`, `tools/gen_sounds.py`, `src/main/java/dev/bastion/registry/BastionItems.java`, `src/main/resources/data/bastion/tags/items/ammo.json`, `tools/gen_textures.py`, `tools/gen_recipes.py`, lang en_us/id_id, `src/main/java/dev/bastion/gametest/TurretWorkstationTests.java`
- Create: `data/bastion/tags/items/ammo/plasma.json`, `assets/bastion/models/item/plasma_cell.json`, `empty_plasma_cell.json`; generated `sounds/ion/*`, `sounds/plasma/*`, `textures/item/plasma_cell.png`, `empty_plasma_cell.png`, recipes

**Interfaces:**
- Produces: `BastionSounds.ION_FIRE, ION_HIT, PLASMA_REPEATER_FIRE, PLASMA_HIT_SMALL, PLASMA_VENT, PLASMA_CANNON_CHARGE, PLASMA_CANNON_FIRE, PLASMA_CANNON_HIT, PLASMA_FRAGMENT`; `BastionItems.PLASMA_CELL`, `BastionItems.EMPTY_PLASMA_CELL`; recipe ids `workstation/ammo_workstation/empty_plasma_cell`, `workstation/charging_station/plasma_cell`.

- [ ] **Step 1: Failing tests** (in `TurretWorkstationTests`, after `chargingStationChargesLaserCells`):

```java
    /** Empty Plasma Cells: four from 2 iron, 1 glass and 1 glowstone dust at the Ammo Workstation. */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "ammoWorkstationMakesEmptyPlasmaCells")
    public static void ammoWorkstationMakesEmptyPlasmaCells(GameTestHelper helper) {
        WorkstationBlockEntity station = station(helper, BastionBlocks.AMMO_WORKSTATION, true);
        helper.runAtTickTime(5, () -> {
            select(station, "ammo_workstation", "empty_plasma_cell");
            FakePlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
            player.getInventory().clearContent();
            player.getInventory().add(new ItemStack(Items.IRON_INGOT, 2));
            player.getInventory().add(new ItemStack(Items.GLASS));
            player.getInventory().add(new ItemStack(Items.GLOWSTONE_DUST));
            station.instantCraft(player, 1);
            helper.assertTrue(player.getInventory().countItem(BastionItems.EMPTY_PLASMA_CELL.get()) == 4, "expected 4 Empty Plasma Cells");
            player.getInventory().clearContent();
            helper.succeed();
        });
    }

    /** Plasma Cells: an Empty Plasma Cell charged at the Charging Station, 80 ticks each. */
    @GameTest(template = ARENA, timeoutTicks = 260, batch = "chargingStationChargesPlasmaCells")
    public static void chargingStationChargesPlasmaCells(GameTestHelper helper) {
        WorkstationBlockEntity station = station(helper, BastionBlocks.CHARGING_STATION, true);
        select(station, "charging_station", "plasma_cell");
        ItemHandlerHelper.insertItem(items(helper, CORE), new ItemStack(BastionItems.EMPTY_PLASMA_CELL.get(), 2), false);
        helper.runAtTickTime(100, () -> helper.assertTrue(station.items().getStackInSlot(WorkstationBlockEntity.OUTPUT).getCount() < 2,
                "two cells charged in 100 ticks: faster than 80 each"));
        helper.succeedWhen(() -> helper.assertTrue(station.items().getStackInSlot(WorkstationBlockEntity.OUTPUT).getCount() == 2
                && station.items().getStackInSlot(WorkstationBlockEntity.OUTPUT).is(BastionItems.PLASMA_CELL.get()), "cells not charged yet"));
    }
```

- [ ] **Step 2: Run, verify fail.** Compile error: `EMPTY_PLASMA_CELL` / `PLASMA_CELL` missing.

- [ ] **Step 3: Items, tag, models.** `BastionItems`, after `EMPTY_LASER_CELL`:

```java
    /** Ammo of the Plasma Repeater (10 shots) and the Plasma Cannon (1 shot): an Empty Plasma Cell charged at the Charging Station. */
    public static final RegistryObject<Item> PLASMA_CELL = REGISTER.register("plasma_cell", () -> new Item(new Item.Properties().stacksTo(16)));
    /** Charged into a Plasma Cell with FE at the Charging Station. */
    public static final RegistryObject<Item> EMPTY_PLASMA_CELL = REGISTER.register("empty_plasma_cell", () -> new Item(new Item.Properties().stacksTo(16)));
```

  Tab predicates: main `TAB` adds `&& item != EMPTY_PLASMA_CELL.get()`; `AMMO_TAB` becomes `isAmmo(item) || item == EMPTY_LASER_CELL.get() || item == EMPTY_PLASMA_CELL.get()`.
  `data/bastion/tags/items/ammo/plasma.json`: `{"values": ["bastion:plasma_cell"]}`; add `"#bastion:ammo/plasma"` to `ammo.json`.
  `models/item/plasma_cell.json` / `empty_plasma_cell.json`: `{"parent": "item/generated", "textures": {"layer0": "bastion:item/plasma_cell"}}` (resp. `empty_plasma_cell`).
  Lang en_us: `"item.bastion.plasma_cell": "Plasma Cell"`, `"item.bastion.empty_plasma_cell": "Empty Plasma Cell"`; id_id `"Sel Plasma"`, `"Sel Plasma Kosong"`.

- [ ] **Step 4: Textures.** `tools/gen_textures.py`, after `empty_laser_cell()`:

```python
PLASMA_CELL = Sprite(LASER_CELL.rows, {**LASER_CELL.palette, "R": rgb("3DFFB5"), "r": rgb("1FBF83"), "q": rgb("0F6B49"), "Y": rgb("C9FFEA")})
EMPTY_PLASMA = Sprite(LASER_CELL.rows, {**LASER_CELL.palette, "R": rgb("2E5A4C"), "r": rgb("244538"), "q": rgb("17302A"), "Y": rgb("3F7564")})


def plasma_cell():
    """Plasma Cannon / Repeater ammo: the Laser Cell's casing with a teal core."""
    img = np.zeros((16, 16, 4))
    for x, y in ((1, 3), (7, 1)):
        stamp(img, PLASMA_CELL, x, y)
    return outline(img, OUTLINE)


def empty_plasma_cell():
    """A Plasma Cell with a dark teal, uncharged core (darker than the Empty Laser Cell, so the two are told apart)."""
    img = np.zeros((16, 16, 4))
    for x, y in ((1, 3), (7, 1)):
        stamp(img, EMPTY_PLASMA, x, y)
    return outline(img, OUTLINE)
```

  TEXTURES: `"item/plasma_cell.png": plasma_cell, "item/empty_plasma_cell.png": empty_plasma_cell,`. Run `cd tools && python3 gen_textures.py`; `git status src/main/resources/assets/bastion/textures` → only the two new files.

- [ ] **Step 5: Recipes.** `tools/gen_recipes.py`: AMMO `"empty_plasma_cell": ({"iron_ingot": 2, "glass": 1, "glowstone_dust": 1}, 40, 400, 4, "laser_cell"),`; CHARGE `"plasma_cell": ({B + "empty_plasma_cell": 1}, 80, 4000, 1),` (and the CHARGE comment becomes "ammo that stores energy"). Run `python3 tools/gen_recipes.py`; expect 2 new recipe files, nothing else changed.

- [ ] **Step 6: Sounds.** `BastionSounds`, after the Repulsor block:

```java
    // Ion Rifle and Plasma (PLAN "Ion Rifle, Plasma Repeater & Plasma Cannon"); placeholders until docs/SOUND_SHEET.md is done.
    public static final RegistryObject<SoundEvent> ION_FIRE = register("ion.fire");
    public static final RegistryObject<SoundEvent> ION_HIT = register("ion.hit");
    public static final RegistryObject<SoundEvent> PLASMA_REPEATER_FIRE = register("plasma_repeater.fire");
    public static final RegistryObject<SoundEvent> PLASMA_HIT_SMALL = register("plasma.hit_small");
    public static final RegistryObject<SoundEvent> PLASMA_VENT = register("plasma.vent");
    public static final RegistryObject<SoundEvent> PLASMA_CANNON_CHARGE = register("plasma_cannon.charge");
    public static final RegistryObject<SoundEvent> PLASMA_CANNON_FIRE = register("plasma_cannon.fire");
    public static final RegistryObject<SoundEvent> PLASMA_CANNON_HIT = register("plasma_cannon.hit");
    public static final RegistryObject<SoundEvent> PLASMA_FRAGMENT = register("plasma.fragment");
```

  `sounds.json` (append; `subtitle` keys `subtitles.bastion.<event>`):

```json
  "ion.fire": {"subtitle": "subtitles.bastion.ion.fire", "sounds": [{"name": "bastion:ion/fire_0", "attenuation_distance": 48}, {"name": "bastion:ion/fire_1", "attenuation_distance": 48}]},
  "ion.hit": {"subtitle": "subtitles.bastion.ion.hit", "sounds": [{"name": "bastion:ion/hit_0", "attenuation_distance": 32}, {"name": "bastion:ion/hit_1", "attenuation_distance": 32}]},
  "plasma_repeater.fire": {"subtitle": "subtitles.bastion.plasma_repeater.fire", "sounds": [{"name": "bastion:plasma/repeater_fire_0", "attenuation_distance": 32}, {"name": "bastion:plasma/repeater_fire_1", "attenuation_distance": 32}, {"name": "bastion:plasma/repeater_fire_2", "attenuation_distance": 32}]},
  "plasma.hit_small": {"subtitle": "subtitles.bastion.plasma.hit_small", "sounds": ["bastion:plasma/hit_small_0", "bastion:plasma/hit_small_1"]},
  "plasma.vent": {"subtitle": "subtitles.bastion.plasma.vent", "sounds": ["bastion:plasma/vent"]},
  "plasma_cannon.charge": {"subtitle": "subtitles.bastion.plasma_cannon.charge", "sounds": [{"name": "bastion:plasma/cannon_charge", "attenuation_distance": 24}]},
  "plasma_cannon.fire": {"subtitle": "subtitles.bastion.plasma_cannon.fire", "sounds": [{"name": "bastion:plasma/cannon_fire_0", "attenuation_distance": 64}, {"name": "bastion:plasma/cannon_fire_1", "attenuation_distance": 64}]},
  "plasma_cannon.hit": {"subtitle": "subtitles.bastion.plasma_cannon.hit", "sounds": [{"name": "bastion:plasma/cannon_hit_0", "attenuation_distance": 48}, {"name": "bastion:plasma/cannon_hit_1", "attenuation_distance": 48}]},
  "plasma.fragment": {"subtitle": "subtitles.bastion.plasma.fragment", "sounds": [{"name": "bastion:plasma/fragment_0", "attenuation_distance": 24}, {"name": "bastion:plasma/fragment_1", "attenuation_distance": 24}]}
```

  Subtitles en_us: Ion Rifle fires / Ion field crackles / Plasma Repeater fires / Plasma splashes / Plasma Repeater vents / Plasma Cannon charges / Plasma Cannon fires / Plasma ball bursts / Plasma bolt whooshes. id_id: Senapan Ion menembak / Medan ion berderak / Pelontar Plasma menembak / Plasma memercik / Pelontar Plasma membuang panas / Meriam Plasma mengisi / Meriam Plasma menembak / Bola plasma pecah / Bola plasma melesat.
  `tools/gen_sounds.py`: append the placeholder functions before `SOUNDS`:

```python
# --- Ion Rifle and Plasma (PLAN "Ion Rifle, Plasma Repeater & Plasma Cannon") -------------------------
def ion_fire(v):
    j = jitter(1100 + v); L = 0.6
    snap = hp(noise(L, 1101 + v), 2000) * env(L, 0.0003, 0.015)
    zap = onepole_lp(square(L, 1400 * j, 300), 5000) * env(L, 0.001, 0.06) * 0.3
    thump = sweep(L, 200 * j, 55) * env(L, 0.002, 0.07) * 0.8
    whoosh = bp(noise(L, 1102 + v), 1200, 6000) * env(L, 0.004, 0.16) * 0.4
    return fade_out(norm(snap + zap + thump + whoosh + crackles(L, 1103 + v, 16, 2500, (0.1, 0.5), (0, 0.35))), 0.08)
def ion_hit(v):
    j = jitter(1110 + v); L = 0.6
    burst = hp(noise(L, 1111 + v), 1500) * env(L, 0.0004, 0.03)
    buzz = onepole_lp(square(L, 140 * j, 90 * j), 2600) * env(L, 0.002, 0.12) * 0.3
    return fade_out(norm(burst + buzz + crackles(L, 1112 + v, 50, 1800, (0.2, 0.9), (0, 0.5))), 0.08)
def plasma_repeater_fire(v):
    j = jitter(1120 + v); L = 0.3
    thoom = sweep(L, 320 * j, 90) * env(L, 0.002, 0.06)
    bubble = sweep(L, 900 * j, 300) * env(L, 0.002, 0.04) * (0.6 + 0.4 * np.sin(2 * np.pi * 60 * t(L))) * 0.4
    hiss = bp(noise(L, 1121 + v), 2000, 8000) * env(L, 0.002, 0.05) * 0.3
    return fade_out(norm(thoom + bubble + hiss), 0.05)
def plasma_hit_small(v):
    j = jitter(1130 + v); L = 0.4
    pop = sweep(L, 500 * j, 120) * env(L, 0.001, 0.04) * 0.7
    sizzle = bp(noise(L, 1131 + v), 2500, 9000) * env(L, 0.003, 0.12) * 0.6
    return fade_out(norm(pop + sizzle), 0.05)
def plasma_vent():
    L = 1.5
    hiss = hp(noise(L, 1140), 2200) * env(L, 0.03, 0.6)
    hum = sweep(L, 240, 90) * env(L, 0.01, 0.5) * 0.3
    return fade_out(norm(hiss + hum + crackles(L, 1141, 10, 3000, (0.1, 0.4), (0.4, 1.0)), 0.75), 0.2)
def plasma_cannon_charge():
    L = 1.0; ramp = np.linspace(0, 1, n(L)) ** 1.3; tt = t(L)
    roar = bp(noise(L, 1150), 200, 1800) * ramp * 0.6
    throb = sweep(L, 60, 140) * (0.6 + 0.4 * np.sin(2 * np.pi * (4 + 14 * ramp) * tt)) * ramp * 0.6
    return fade_out(norm(roar + throb, 0.8), 0.03)
def plasma_cannon_fire(v):
    j = jitter(1160 + v); L = 1.4
    whumm = sweep(L, 160 * j, 38) * env(L, 0.003, 0.22)
    roar = bp(noise(L, 1161 + v), 150, 1500) * env(L, 0.005, 0.35) * 0.6
    pulse = sweep(L, 420 * j, 140) * env(L, 0.004, 0.3) * (0.6 + 0.4 * np.sin(2 * np.pi * 18 * t(L))) * 0.3
    return fade_out(norm(whumm + roar + pulse), 0.2)
def plasma_cannon_hit(v):
    j = jitter(1170 + v); L = 1.6
    blast = sweep(L, 130 * j, 32) * env(L, 0.002, 0.25)
    splash = bp(noise(L, 1171 + v), 300, 6000) * env(L, 0.002, 0.3) * 0.7
    sizzle = bp(noise(L, 1172 + v), 3000, 10000) * env(L, 0.02, 0.6) * 0.3
    return fade_out(norm(blast + splash + sizzle), 0.25)
def plasma_fragment(v):
    j = jitter(1180 + v); L = 0.5
    return fade_out(norm(bp(noise(L, 1181 + v), 1500 * j, 7000) * np.sin(np.pi * t(L) / L), 0.6), 0.05)
```

  SOUNDS entries:

```python
    **{f"ion/fire_{v}.ogg": (lambda v=v: ion_fire(v)) for v in range(2)},
    **{f"ion/hit_{v}.ogg": (lambda v=v: ion_hit(v)) for v in range(2)},
    **{f"plasma/repeater_fire_{v}.ogg": (lambda v=v: plasma_repeater_fire(v)) for v in range(3)},
    **{f"plasma/hit_small_{v}.ogg": (lambda v=v: plasma_hit_small(v)) for v in range(2)},
    "plasma/vent.ogg": plasma_vent, "plasma/cannon_charge.ogg": plasma_cannon_charge,
    **{f"plasma/cannon_fire_{v}.ogg": (lambda v=v: plasma_cannon_fire(v)) for v in range(2)},
    **{f"plasma/cannon_hit_{v}.ogg": (lambda v=v: plasma_cannon_hit(v)) for v in range(2)},
    **{f"plasma/fragment_{v}.ogg": (lambda v=v: plasma_fragment(v)) for v in range(2)},
```

  Main: write only missing files (the user is replacing sounds by hand):

```python
if __name__ == "__main__":
    # Only missing files: a sound already replaced with real SFX (docs/SOUND_SHEET.md) survives a re-run.
    # --force rewrites every placeholder.
    force = "--force" in sys.argv
    for rel, make in SOUNDS.items():
        path = OUT / rel
        if path.exists() and not force:
            continue
        path.parent.mkdir(parents=True, exist_ok=True)
        sf.write(path, make(), RATE, format="OGG", subtype="VORBIS")
        print("wrote", path.relative_to(OUT.parents[5]))
```

  (`import sys`; docstring: "Only writes missing files; `--force` rewrites all."). Run `python3 tools/gen_sounds.py` → prints exactly the 17 new files; `git status src/main/resources/assets/bastion/sounds` shows no modified file.

- [ ] **Step 7: Verify.** `./gradlew build --offline`, `./gradlew runGameTestServer --offline` → "All 117 required tests passed".

---

### Task 3: Ion Rifle (gameplay, packet, visuals)

**Files:**
- Create: `src/main/java/dev/bastion/weapon/types/IonRifleWeapon.java`, `src/main/resources/data/bastion/turret_weapons/ion_rifle.json`, `src/main/java/dev/bastion/network/IonField.java`, `src/main/java/dev/bastion/client/IonEffects.java`
- Modify: `BastionWeaponTypes.java`, `BastionItems.java`, `network/TurretEvents.java`, `network/BastionNetwork.java` (`PROTOCOL = "9"`), `client/ClientPacketHandler.java`, `client/TurretFireEffects.java`, `client/vfx/WeaponFx.java`, `client/vfx/VfxPresets.java`, lang, `gametest/TurretEnergyTests.java`

**Interfaces:**
- Consumes: Task 1 (`Splash.around`, `Bullets.filtered/create/launch`, `TracerRenderer.spawnStreak/onLanding`, `TurretFireEffects.playShot`), Task 2 (`ION_FIRE`, `ION_HIT`).
- Produces: `BastionWeaponTypes.ION_RIFLE`, `BastionItems.ION_RIFLE_TURRET`; `record IonRifleWeapon.Field(double radius, float damage, int color, int ticks)`, `static Bullets.Hit IonRifleWeapon.bolt(ServerLevel, float damage, Field, Predicate<Entity> canHit, DamageSource)`; `record IonField(int bullet, Vec3 core, int color, int ticks, int[] targets)` with `MAX_TARGETS = 64`; `WeaponFx.ION_RIFLE`; presets `ION_MUZZLE`, `ION_HIT`, `ION_ZAP`.

- [ ] **Step 1: Failing tests** (append to `TurretEnergyTests`; add imports `BastionBlocks`, `BastionItems`, `BastionDamageTypes`, `TurretBaseBlockEntity`, `TurretInventory`, `TurretState`, `IonRifleWeapon`, `IonField`, `ItemStack`, `Item`, `Direction`, `ForgeCapabilities`, `IEnergyStorage`, `FriendlyByteBuf`, `io.netty.buffer.Unpooled`, `java.util.stream.IntStream`):

```java
    private static TurretBaseBlockEntity mount(GameTestHelper helper, BlockPos pos, Item weapon) {
        helper.setBlock(pos, BastionBlocks.TURRET_BASE.get());
        TurretBaseBlockEntity base = (TurretBaseBlockEntity) helper.getBlockEntity(pos);
        base.inventory().setStackInSlot(TurretInventory.WEAPON, new ItemStack(weapon));
        return base;
    }

    private static IEnergyStorage energy(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockEntity(pos).getCapability(ForgeCapabilities.ENERGY, Direction.UP).orElseThrow(IllegalStateException::new);
    }

    /** Feeds exactly {@code fe} (several transfers: max_input caps each one). */
    private static void charge(GameTestHelper helper, BlockPos pos, int fe) {
        IEnergyStorage energy = energy(helper, pos);
        for (int left = fe, got; left > 0 && (got = energy.receiveEnergy(left, false)) > 0; ) left -= got;
    }

    private static final BlockPos ION = new BlockPos(2, 2, 5);

    /**
     * FE for exactly one shot: the husk struck loses 8 (not 8 + 5), the two within 3 blocks of the impact lose 5 each, the one
     * 3.7 blocks out and the cow beside the impact nothing; then the turret waits without FE.
     */
    @GameTest(template = RANGE, timeoutTicks = 160, batch = "ionZapsEveryTargetAround")
    public static void ionZapsEveryTargetAround(GameTestHelper helper) {
        TurretBaseBlockEntity turret = mount(helper, ION, BastionItems.ION_RIFLE_TURRET.get());
        charge(helper, ION, 1200);
        LivingEntity struck = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(9, 2, 5)); // nearest: targeted
        LivingEntity behind = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(11, 2, 5));
        LivingEntity aside = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(9, 2, 7));
        LivingEntity far = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(9, 2, 9));
        LivingEntity cow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(10, 2, 4));
        helper.succeedWhen(() -> {
            helper.assertTrue(lost(struck) >= 7.5f && lost(struck) <= 8.05f, "struck husk lost " + lost(struck) + ", state " + turret.state());
            helper.assertTrue(lost(behind) >= 4.5f && lost(behind) <= 5.05f && lost(aside) >= 4.5f && lost(aside) <= 5.05f,
                    "field husks lost " + lost(behind) + " and " + lost(aside));
            helper.assertTrue(lost(far) == 0 && lost(cow) == 0, "reached too far: husk " + lost(far) + ", cow " + lost(cow));
            helper.assertTrue(turret.state() == TurretState.NO_AMMO, "expected NO_AMMO after the one shot, got " + turret.state());
        });
    }

    /**
     * A bolt that stops at a wall still forms its core there: the husk beside the impact and the one behind the wall (no line
     * of sight) lose 5; the cow does not. Drives the weapon's own hit with a bare bullet, so the block path is certain.
     */
    @GameTest(template = ARENA, timeoutTicks = 80, batch = "ionZapsWhereTheBoltStops")
    public static void ionZapsWhereTheBoltStops(GameTestHelper helper) {
        for (int y = 1; y <= 4; y++) for (int z = 1; z <= 5; z++) helper.setBlock(new BlockPos(10, y, z), Blocks.STONE);
        LivingEntity beside = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(9, 2, 1));
        LivingEntity behind = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(11, 2, 3));
        LivingEntity cow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(9, 2, 5));
        var level = helper.getLevel();
        helper.runAtTickTime(20, () -> Bullets.launch(Bullets.create(level, List.of(helper.absoluteVec(new Vec3(2.5, 2, 3.5))),
                List.of(new Vec3(1, 0, 0)), 4, 20, 0, HUSKS, IonRifleWeapon.bolt(level, 8, new IonRifleWeapon.Field(3, 5, 0x8099FF, 6), HUSKS,
                        BastionDamageTypes.of(level, BastionDamageTypes.TURRET_SHOCK)))));
        helper.succeedWhen(() -> {
            helper.assertTrue(lost(beside) >= 4.5f && lost(behind) >= 4.5f, "zapped " + lost(beside) + " beside, " + lost(behind) + " behind the wall");
            helper.assertTrue(lost(cow) == 0, "the cow was zapped");
        });
    }

    /** Without FE: NO_AMMO; max_input 1,000 per transfer, capacity 40,000, 1,200 per shot. */
    @GameTest(template = RANGE, timeoutTicks = 160, batch = "ionRunsOnEnergy")
    public static void ionRunsOnEnergy(GameTestHelper helper) {
        TurretBaseBlockEntity turret = mount(helper, ION, BastionItems.ION_RIFLE_TURRET.get());
        LivingEntity husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(9, 2, 5));
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(turret.state() == TurretState.NO_AMMO, "expected NO_AMMO without FE, got " + turret.state());
            helper.assertTrue(energy(helper, ION).receiveEnergy(Integer.MAX_VALUE, false) == 1000, "max_input is not 1000");
            charge(helper, ION, Integer.MAX_VALUE);
            helper.assertTrue(turret.energy() == 40000, "capacity is not 40000: " + turret.energy());
        });
        helper.runAfterDelay(41, () -> helper.succeedWhen(() -> {
            helper.assertTrue(lost(husk) > 0, "never fired with FE");
            helper.assertTrue(turret.energy() == 38800, "a shot should cost 1200, capacitor at " + turret.energy());
        }));
    }

    /** A crowd of 100 in one field: the beam list on the wire is cut to 64 and decodes cleanly (the damage is not capped). */
    @GameTest(template = ARENA, batch = "ionFieldPacketIsCapped")
    public static void ionFieldPacketIsCapped(GameTestHelper helper) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        new IonField(7, Vec3.ZERO, 0x8099FF, 6, IntStream.range(0, 100).toArray()).encode(buf);
        IonField back = IonField.decode(buf);
        helper.assertTrue(back.targets().length == IonField.MAX_TARGETS && back.bullet() == 7 && back.ticks() == 6, "decoded " + back.targets().length);
        helper.succeed();
    }
```

- [ ] **Step 2: Run, verify fail.** Compile errors (`ION_RIFLE_TURRET`, `IonRifleWeapon`, `IonField`).

- [ ] **Step 3: Weapon + data + registration.** `ion_rifle.json`:

```json
{
  "type": "bastion:ion_rifle",
  "ammo": "bastion:ammo/energy",
  "energy_color": "#8099FF",
  "range": 32,
  "fire_interval": 16,
  "damage": 8,
  "spread": 0,
  "ammo_per_shot": 0,
  "turn_speed": 8,
  "aim_tolerance": 1.5,
  "aim_lock_ticks": 3,
  "min_pitch": -35,
  "max_pitch": 90,
  "pivot_height": 0.4375,
  "muzzle_length": 1.5375,
  "params": {
    "bolt_speed": 4,
    "lead_accuracy": 0.9,
    "field_radius": 3,
    "field_damage": 5,
    "field_ticks": 6,
    "energy_per_shot": 1200,
    "energy_capacity": 40000,
    "max_input": 1000
  }
}
```

`IonRifleWeapon.java`:

```java
package dev.bastion.weapon.types;

import dev.bastion.damage.BastionDamageTypes;
import dev.bastion.network.BastionNetwork;
import dev.bastion.network.IonField;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.weapon.Bullets;
import dev.bastion.weapon.FireContext;
import dev.bastion.weapon.FireProfile;
import dev.bastion.weapon.Hitscan;
import dev.bastion.weapon.LeadSolver;
import dev.bastion.weapon.Splash;
import dev.bastion.weapon.StatSheet;
import dev.bastion.weapon.WeaponData;
import dev.bastion.weapon.WeaponState;
import dev.bastion.weapon.WeaponType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

/**
 * Ion Rifle (PLAN "Ion Rifle, Plasma Repeater & Plasma Cannon", after Crimsonland's Ion Rifle): a fast ion bolt
 * (bolt_speed, led onto moving targets) that stops at the first valid target or block. Where it stops an ion core forms:
 * the target struck takes damage, every other valid target whose box is within field_radius takes field_damage once,
 * through walls, and clients draw a straight beam from the core to each (IonField). Runs on FE (energy_per_shot).
 */
public class IonRifleWeapon extends WeaponType {
    /** The core: reach, damage to each zapped target, colour and how long clients show the beams. */
    public record Field(double radius, float damage, int color, int ticks) {
    }

    @Override
    public FireProfile fireProfile() {
        return FireProfile.PROJECTILE;
    }

    @Override
    public Vec3 aimPoint(TurretBaseBlockEntity turret, LivingEntity target, Vec3 point, StatSheet stats) {
        return LeadSolver.lead(turret, target, point, stats, stats.data().param("bolt_speed"));
    }

    @Override
    public boolean outOfAmmo(TurretBaseBlockEntity turret, WeaponState state) {
        return !turret.hasEnergy(cost(turret));
    }

    @Override
    public void onFire(FireContext context) {
        if (!context.turret().useEnergy(cost(context.turret()))) return;
        StatSheet stats = context.stats();
        WeaponData data = stats.data();
        // Damage modules scale the field like the bolt.
        Field field = new Field(data.param("field_radius"), data.param("field_damage") * stats.damage() / data.damage(), data.energyColor(),
                Math.round(data.param("field_ticks")));
        Bullets.fire(context, this, 0, List.of(context.direction()), data.param("bolt_speed"), 0, Bullets.validTargets(context, this),
                bolt(context.level(), stats.damage(), field, Bullets.filtered(context, this),
                        BastionDamageTypes.of(context.level(), BastionDamageTypes.TURRET_SHOCK)));
    }

    /** What a bolt does where it stops: {@code damage} to the target struck, then the core (public for the GameTests). */
    public static Bullets.Hit bolt(ServerLevel level, float damage, Field field, Predicate<Entity> canHit, DamageSource source) {
        return new Bullets.Hit() {
            @Override
            public void hit(Bullets.Bullet bullet, EntityHitResult hit) {
                Hitscan.damage(hit.getEntity(), source, damage);
                zap(level, bullet.id, hit.getLocation(), field, canHit, hit.getEntity(), source);
            }

            @Override
            public void blocked(Bullets.Bullet bullet, Vec3 at) {
                zap(level, bullet.id, at, field, canHit, null, source);
            }
        };
    }

    /** Field damage to every target in reach but the one struck; clients get the core and who to draw beams to. */
    private static void zap(ServerLevel level, int bullet, Vec3 core, Field field, Predicate<Entity> canHit, @Nullable Entity struck,
                            DamageSource source) {
        List<LivingEntity> zapped = Splash.around(level, core, field.radius(), canHit, struck);
        for (LivingEntity target : zapped) Hitscan.damage(target, source, field.damage());
        BastionNetwork.sendNear(level, core, new IonField(bullet, core, field.color(), field.ticks(),
                zapped.stream().mapToInt(Entity::getId).toArray()));
    }

    private static int cost(TurretBaseBlockEntity turret) {
        WeaponData data = turret.inventory().weaponData();
        return data == null ? Integer.MAX_VALUE : Math.round(data.param("energy_per_shot"));
    }
}
```

`BastionWeaponTypes`: `public static final RegistryObject<WeaponType> ION_RIFLE = REGISTER.register("ion_rifle", IonRifleWeapon::new);`
`BastionItems` (after `REPULSOR_TURRET`, before "Big modules"):

```java
    /** A fast ion bolt; where it stops, every target within 3 blocks is zapped too; runs on FE. */
    public static final RegistryObject<WeaponModuleItem> ION_RIFLE_TURRET = REGISTER.register("ion_rifle_turret",
            () -> new WeaponModuleItem(new Item.Properties().stacksTo(1), Bastion.id("ion_rifle")));
```

Lang en_us: `"item.bastion.ion_rifle_turret": "Ion Rifle Turret"`, `"tooltip.bastion.weapon.ion_rifle_turret.ability": "A fast ion bolt; where it stops, a beam zaps every valid target within 3 blocks for 5 damage, even through thin walls. Runs on FE (1,200 per shot)"`; id_id: `"Turret Senapan Ion"`, `"Peluru ion cepat; di titik berhentinya, sinar menyambar setiap target sah dalam 3 blok sebesar 5 damage, tembus dinding tipis. Pakai FE (1.200 per tembakan)"`.

- [ ] **Step 4: Packet.** `network/IonField.java`:

```java
package dev.bastion.network;

import dev.bastion.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.Arrays;
import java.util.function.Supplier;

/**
 * S->C an Ion Rifle core (IonRifleWeapon): where bolt {@code bullet} stopped, its colour, how long the beams show and the
 * targets it zapped, so clients draw a beam to each once the bolt's tracer arrives. Travels in TurretEvents, queued before
 * the bolt's own BulletImpact.
 */
public record IonField(int bullet, Vec3 core, int color, int ticks, int[] targets) {
    /** Most beams on the wire; a bigger crowd is all damaged, only these get a beam. */
    public static final int MAX_TARGETS = 64;

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(bullet);
        buf.writeDouble(core.x).writeDouble(core.y).writeDouble(core.z);
        buf.writeInt(color);
        buf.writeByte(ticks);
        buf.writeVarIntArray(targets.length > MAX_TARGETS ? Arrays.copyOf(targets, MAX_TARGETS) : targets);
    }

    public static IonField decode(FriendlyByteBuf buf) {
        return new IonField(buf.readVarInt(), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readInt(), buf.readByte(),
                buf.readVarIntArray(MAX_TARGETS));
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.ionField(this));
        context.get().setPacketHandled(true);
    }
}
```

`TurretEvents.KINDS`: append `new Kind<>(IonField.class, IonField::encode, IonField::decode, IonField::handle)`. `BastionNetwork.PROTOCOL = "9"`.
`ClientPacketHandler`:

```java
    /** Ion Rifle: the core's beams appear when the bolt's tracer gets there (or at once if it has none). */
    public static void ionField(IonField field) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) TracerRenderer.onLanding(field.bullet(), () -> IonEffects.field(level, field));
    }
```

- [ ] **Step 5: Visuals.** `VfxPresets` (new section after Repulsor; also `ALL.put("ion_muzzle", ION_MUZZLE)`, `"ion_hit"`, `"ion_zap"`):

```java
    // --- Ion Rifle, Crimsonland blue (bolt: TracerRenderer streak, beams: BeamRenderer, IonEffects) ------------------

    /** The bolt leaving the barrel: a blue flash with a white core, a tight ring, a few sparks. */
    public static final VfxPreset ION_MUZZLE = v -> v
            .single(BastionParticles.MUZZLE_FLASH.get(), v.origin(), 2.6f, 3, Curve.PULSE, v.params().color())
            .single(BastionParticles.MUZZLE_FLASH.get(), v.origin(), 1.2f, 2, Curve.PULSE, 0xFFF4F6FF)
            .ring(BastionParticles.MUZZLE_RING.get(), v.origin(), v.direction(), 0.06f, 1.8f, 5, Curve.EASE_OUT, v.params().color())
            .burst(BastionParticles.SPARK.get(), 4, v.direction(), 20, 0.3f, 0.5f, 6, Curve.LINEAR, v.params().color())
            .light(11, 2);

    /** The ion core: a white-blue flash that shrinks to a small blue core over ~6 ticks, an expanding ring, blue sparks. */
    public static final VfxPreset ION_HIT = v -> v
            .single(BastionParticles.MUZZLE_FLASH.get(), v.origin(), 4.5f, 3, Curve.PULSE, 0xFFE8EEFF)
            .single(BastionParticles.MUZZLE_FLASH.get(), v.origin(), 1.6f, 6, Curve.EASE_OUT, v.params().color())
            .ring(BastionParticles.SHOCKWAVE_RING.get(), v.origin(), v.params().normal(), 0, 12f, 7, Curve.EASE_OUT, v.params().color())
            .burst(BastionParticles.SPARK.get(), 12, v.params().normal(), 170, 0.32f, 0.55f, 9, Curve.LINEAR, v.params().color())
            .light(13, 6);

    /** Where a beam meets a target: a small flash and sparks. */
    public static final VfxPreset ION_ZAP = v -> v
            .single(BastionParticles.MUZZLE_FLASH.get(), v.origin(), 1.6f, 3, Curve.PULSE, v.params().color())
            .burst(BastionParticles.SPARK.get(), 4, v.params().normal(), 120, 0.2f, 0.4f, 7, Curve.LINEAR, v.params().color());
```

`WeaponFx` (+ `Map.entry(Bastion.id("ion_rifle"), ION_RIFLE)`):

```java
    /** A blue streak to where the bolt stops, then the ion core's beams (IonEffects). */
    public static final WeaponFx ION_RIFLE = new WeaponFx(VfxPresets.ION_MUZZLE, VfxPresets.ION_HIT, VfxPresets.ION_HIT,
            0.09f, 1f, 50f, false, false, BastionSounds.ION_FIRE, BastionSounds.ION_FIRE);
```

`client/IonEffects.java`:

```java
package dev.bastion.client;

import dev.bastion.client.render.BeamRenderer;
import dev.bastion.client.render.TracerRenderer;
import dev.bastion.client.vfx.VfxManager;
import dev.bastion.client.vfx.VfxParams;
import dev.bastion.client.vfx.VfxPresets;
import dev.bastion.client.vfx.WeaponFx;
import dev.bastion.network.IonField;
import dev.bastion.network.TurretFireEvent;
import dev.bastion.registry.BastionSounds;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Ion Rifle (PLAN "Ion Rifle, ..."): a blue streak to where the bolt stops, and there the core's beams (IonField). */
public final class IonEffects {
    private static final float BOLT_WIDTH = 0.09f, TRAIL_BLOCKS = 6f, BEAM_WIDTH = 0.14f;
    private static final Vec3 UP = new Vec3(0, 1, 0);

    private IonEffects() {
    }

    public static void fire(ClientLevel level, TurretFireEvent event, WeaponFx fx, Vec3 muzzle, Vec3 direction, int color) {
        VfxManager.play(fx.muzzle(), muzzle, direction, VfxParams.of().color(color).seed(event.seed()));
        for (TurretFireEvent.Shot shot : event.shots()) {
            // The impact is the core's (IonField, chained to this tracer's landing); a bolt out of range just fades.
            TracerRenderer.spawnStreak(shot.blockState(), muzzle, shot.end(), event.speed(), color, BOLT_WIDTH, TRAIL_BLOCKS,
                    (end, normal, hit, blockState) -> {});
        }
        TurretFireEffects.playShot(level, event, muzzle, fx, RandomSource.create(event.seed()));
    }

    /** The core: a flash and ring where the bolt stopped, a straight beam to each target it zapped for {@code ticks}. */
    public static void field(ClientLevel level, IonField field) {
        int color = 0xFF000000 | field.color();
        Vec3 core = field.core();
        VfxManager.play(VfxPresets.ION_HIT, core, UP, VfxParams.of().color(color).normal(UP).seed(field.bullet()));
        for (int id : field.targets()) {
            Entity target = level.getEntity(id);
            if (target == null) continue;
            Vec3 at = target.getBoundingBox().getCenter();
            Vec3 back = core.subtract(at).normalize();
            BeamRenderer.spawn(core, at, color, BEAM_WIDTH, field.ticks());
            VfxManager.play(VfxPresets.ION_ZAP, at, back.reverse(), VfxParams.of().color(color).normal(back).seed(id));
        }
        level.playLocalSound(core.x, core.y, core.z, BastionSounds.ION_HIT.get(), SoundSource.HOSTILE, 1f,
                0.94f + level.random.nextFloat() * 0.12f, false);
    }
}
```

`TurretFireEffects.onFire`, after the `REPULSOR_DOME` branch:

```java
        if (fx == WeaponFx.ION_RIFLE) { // a streak, then the core's beams (IonEffects, IonField)
            IonEffects.fire(level, event, fx, muzzle, direction, color);
            return;
        }
```

- [ ] **Step 6: Verify.** `./gradlew build --offline`; `./gradlew runGameTestServer --offline` → "All 121 required tests passed".

---

### Task 4: Plasma Repeater

**Files:**
- Create: `src/main/java/dev/bastion/weapon/types/PlasmaRepeaterWeapon.java`, `data/bastion/turret_weapons/plasma_repeater.json`, `src/main/java/dev/bastion/client/PlasmaEffects.java`
- Modify: `BastionWeaponTypes.java`, `BastionItems.java`, `client/TurretFireEffects.java`, `client/ClientTurret.java`, `client/vfx/WeaponFx.java`, `client/vfx/VfxPresets.java`, lang, `TurretEnergyTests.java`

**Interfaces:**
- Consumes: Tasks 1–2 (`Splash`, `Bullets.filtered`, `TracerRenderer.spawnOrb`, `TURRET_PLASMA`, `PLASMA_CELL`, `PLASMA_REPEATER_FIRE`, `PLASMA_HIT_SMALL`, `PLASMA_VENT`).
- Produces: `BastionWeaponTypes.PLASMA_REPEATER`, `BastionItems.PLASMA_REPEATER_TURRET`; `static Bullets.Hit PlasmaRepeaterWeapon.bolt(ServerLevel, float damage, float splash, double radius, Predicate<Entity>, DamageSource)`; `PlasmaEffects.fire(ClientLevel, TurretFireEvent, WeaponFx, Vec3 muzzle, Vec3 direction, int color, boolean cannon)`, `PlasmaEffects.burst(ClientLevel, Vec3 end, Vec3 normal, int hit, int blockState, int color, boolean big, boolean loud)` (package-private); `WeaponFx.PLASMA_REPEATER`; presets `PLASMA_MUZZLE`, `PLASMA_HIT`, `PLASMA_SCORCH`.

- [ ] **Step 1: Failing tests** (append; import `net.minecraft.world.entity.ai.attributes.Attributes`):

```java
    private static final BlockPos REPEATER = new BlockPos(2, 2, 5);

    private static LivingEntity tank(GameTestHelper helper, BlockPos pos) {
        LivingEntity husk = helper.spawnWithNoFreeWill(EntityType.HUSK, pos);
        husk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        husk.setHealth(1000);
        return husk;
    }

    /**
     * Each ball splashes: the husk 1 block behind the one struck loses less than it (3 vs 5 a shot), the husk 1.7 blocks
     * aside and the cow 0.7 blocks aside nothing.
     */
    @GameTest(template = RANGE, timeoutTicks = 160, batch = "repeaterSplashesAround")
    public static void repeaterSplashesAround(GameTestHelper helper) {
        TurretBaseBlockEntity turret = mount(helper, REPEATER, BastionItems.PLASMA_REPEATER_TURRET.get());
        turret.inventory().setStackInSlot(TurretInventory.AMMO_START, new ItemStack(BastionItems.PLASMA_CELL.get(), 3));
        LivingEntity struck = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(8, 2, 5));
        LivingEntity behind = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(9, 2, 5));
        LivingEntity aside = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(8, 2, 7));
        LivingEntity cow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(8, 2, 4));
        helper.succeedWhen(() -> {
            helper.assertTrue(lost(struck) > 0 && lost(behind) > 0, "struck " + lost(struck) + ", splashed " + lost(behind) + ", state " + turret.state());
            helper.assertTrue(lost(behind) < lost(struck), "the splash hit as hard as the ball");
            helper.assertTrue(lost(aside) == 0 && lost(cow) == 0, "splash too wide: husk " + lost(aside) + ", cow " + lost(cow));
        });
    }

    /** Heat: after 15-30 shots it overheats and holds fire through the vent (60 ticks). */
    @GameTest(template = RANGE, timeoutTicks = 400, batch = "repeaterOverheatsAndVents")
    public static void repeaterOverheatsAndVents(GameTestHelper helper) {
        TurretBaseBlockEntity turret = mount(helper, REPEATER, BastionItems.PLASMA_REPEATER_TURRET.get());
        turret.inventory().setStackInSlot(TurretInventory.AMMO_START, new ItemStack(BastionItems.CREATIVE_AMMO.get()));
        LivingEntity husk = tank(helper, new BlockPos(8, 2, 5));
        long[] hotAt = {-1};
        float[] atHot = {0}, settled = {0};
        helper.onEachTick(() -> {
            if (hotAt[0] < 0 && turret.state() == TurretState.OVERHEAT) {
                hotAt[0] = helper.getTick();
                atHot[0] = lost(husk);
            }
            if (hotAt[0] >= 0 && helper.getTick() == hotAt[0] + 10) settled[0] = lost(husk); // balls still in flight have landed
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(hotAt[0] >= 0, "never overheated, heat " + turret.heat());
            // +8 a shot, 4 shots/s, -15/s: ~23 shots (~113 damage at 4.92 a shot after the husk's armour).
            helper.assertTrue(atHot[0] >= 70 && atHot[0] <= 150, "overheated after ~" + atHot[0] / 4.92f + " shots, expected 15-30");
            helper.assertTrue(helper.getTick() >= hotAt[0] + 50, "venting");
            helper.assertTrue(lost(husk) == settled[0], "fired while venting");
        });
    }

    /** One Plasma Cell = 10 shots: the lone husk loses ~49 and the turret runs dry. */
    @GameTest(template = RANGE, timeoutTicks = 200, batch = "repeaterTenShotsPerCell")
    public static void repeaterTenShotsPerCell(GameTestHelper helper) {
        TurretBaseBlockEntity turret = mount(helper, REPEATER, BastionItems.PLASMA_REPEATER_TURRET.get());
        turret.inventory().setStackInSlot(TurretInventory.AMMO_START, new ItemStack(BastionItems.PLASMA_CELL.get()));
        LivingEntity husk = tank(helper, new BlockPos(8, 2, 5));
        helper.succeedWhen(() -> {
            helper.assertTrue(turret.state() == TurretState.NO_AMMO, "still firing, state " + turret.state());
            helper.assertTrue(lost(husk) >= 45 && lost(husk) <= 50.5f, "10 shots should take ~49, took " + lost(husk));
        });
    }
```

- [ ] **Step 2: Run, verify fail.** Compile errors (`PLASMA_REPEATER_TURRET`).

- [ ] **Step 3: Weapon, data, registration.** `plasma_repeater.json`:

```json
{
  "type": "bastion:plasma_repeater",
  "ammo": "bastion:ammo/plasma",
  "energy_color": "#3DFFB5",
  "range": 24,
  "fire_interval": 5,
  "damage": 5,
  "spread": 0.8,
  "ammo_per_shot": 0.1,
  "turn_speed": 10,
  "aim_tolerance": 2,
  "aim_lock_ticks": 2,
  "min_pitch": -35,
  "max_pitch": 90,
  "heat_per_shot": 8,
  "heat_dissipation_per_second": 15,
  "max_heat": 100,
  "overheat_ticks": 60,
  "pivot_height": 0.4375,
  "muzzle_length": 1.05,
  "params": {
    "bolt_speed": 1.5,
    "lead_accuracy": 0.9,
    "splash_damage": 3,
    "splash_radius": 1.2
  }
}
```

`PlasmaRepeaterWeapon.java`:

```java
package dev.bastion.weapon.types;

import dev.bastion.damage.BastionDamageTypes;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.weapon.Bullets;
import dev.bastion.weapon.FireContext;
import dev.bastion.weapon.FireProfile;
import dev.bastion.weapon.Hitscan;
import dev.bastion.weapon.LeadSolver;
import dev.bastion.weapon.Splash;
import dev.bastion.weapon.StatSheet;
import dev.bastion.weapon.WeaponData;
import dev.bastion.weapon.WeaponType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.Predicate;

/**
 * Plasma Repeater (PLAN "Ion Rifle, Plasma Repeater & Plasma Cannon"): rapid plasma balls, slow enough to see
 * (bolt_speed), led onto moving targets. Each stops at the first valid target or block and splashes splash_damage on
 * every other valid target within splash_radius. Heats up like the Machine Gun (top-level heat fields). Plasma Cells,
 * ammo_per_shot 0.1.
 */
public class PlasmaRepeaterWeapon extends WeaponType {
    @Override
    public FireProfile fireProfile() {
        return FireProfile.PROJECTILE;
    }

    @Override
    public Vec3 aimPoint(TurretBaseBlockEntity turret, LivingEntity target, Vec3 point, StatSheet stats) {
        return LeadSolver.lead(turret, target, point, stats, stats.data().param("bolt_speed"));
    }

    @Override
    public void onFire(FireContext context) {
        StatSheet stats = context.stats();
        WeaponData data = stats.data();
        Vec3 direction = Hitscan.spread(context.direction(), stats.spread(), RandomSource.create(context.seed()));
        Bullets.fire(context, this, 0, List.of(direction), data.param("bolt_speed"), 0, Bullets.validTargets(context, this),
                bolt(context.level(), stats.damage(), data.param("splash_damage") * stats.damage() / data.damage(), data.param("splash_radius"),
                        Bullets.filtered(context, this), BastionDamageTypes.of(context.level(), BastionDamageTypes.TURRET_PLASMA)));
    }

    /** What a ball does where it lands: {@code damage} to the target struck, {@code splash} to the others in {@code radius}. */
    public static Bullets.Hit bolt(ServerLevel level, float damage, float splash, double radius, Predicate<Entity> canHit, DamageSource source) {
        return new Bullets.Hit() {
            @Override
            public void hit(Bullets.Bullet bullet, EntityHitResult hit) {
                Hitscan.damage(hit.getEntity(), source, damage);
                for (LivingEntity e : Splash.around(level, hit.getLocation(), radius, canHit, hit.getEntity())) Hitscan.damage(e, source, splash);
            }

            @Override
            public void blocked(Bullets.Bullet bullet, Vec3 at) {
                for (LivingEntity e : Splash.around(level, at, radius, canHit, null)) Hitscan.damage(e, source, splash);
            }
        };
    }
}
```

`BastionWeaponTypes.PLASMA_REPEATER` (`"plasma_repeater"`, `PlasmaRepeaterWeapon::new`). `BastionItems` after `ION_RIFLE_TURRET`:

```java
    /** Rapid plasma balls that splash; heats up and vents like the Machine Gun; Plasma Cells. */
    public static final RegistryObject<WeaponModuleItem> PLASMA_REPEATER_TURRET = REGISTER.register("plasma_repeater_turret",
            () -> new WeaponModuleItem(new Item.Properties().stacksTo(1), Bastion.id("plasma_repeater")));
```

Lang en_us: `"Plasma Repeater Turret"`, ability `"Rapid plasma balls you can see fly; each splashes 3 damage on targets within 1.2 blocks of where it lands. Heats up and vents like the Machine Gun. 10 shots per Plasma Cell"`; id_id: `"Turret Pelontar Plasma"`, `"Bola plasma beruntun yang terlihat terbang; tiap bola memercik 3 damage ke target dalam 1,2 blok dari titik kena. Panas lalu membuang panas seperti Machine Gun. 10 tembakan per Sel Plasma"`.

- [ ] **Step 4: Visuals.** `VfxPresets` (+ `ALL` entries `plasma_muzzle`, `plasma_hit`, `plasma_scorch`):

```java
    // --- Plasma Repeater and Plasma Cannon, teal (balls: TracerRenderer orbs, PlasmaEffects) ------------------------

    public static final VfxPreset PLASMA_MUZZLE = v -> v
            .single(BastionParticles.MUZZLE_FLASH.get(), v.origin(), 2.2f, 2, Curve.PULSE, v.params().color())
            .ring(BastionParticles.MUZZLE_RING.get(), v.origin(), v.direction(), 0.05f, 1.4f, 4, Curve.EASE_OUT, v.params().color())
            .light(10, 2);

    /** A small ball bursting: a teal flash, a splash ring about the splash radius across, sparks and embers. */
    public static final VfxPreset PLASMA_HIT = v -> v
            .single(BastionParticles.MUZZLE_FLASH.get(), v.origin(), 2.4f, 3, Curve.PULSE, v.params().color())
            .ring(BastionParticles.IMPACT_SPLASH.get(), v.origin(), v.params().normal(), 0, 2.4f, 6, Curve.EASE_OUT, v.params().color())
            .burst(BastionParticles.SPARK.get(), 6, v.params().normal(), 90, 0.22f, 0.5f, 8, Curve.LINEAR, v.params().color())
            .burst(BastionParticles.EMBER.get(), 2, v.params().normal(), 60, 0.05f, 0.5f, 14, Curve.FLICKER, v.params().color())
            .light(9, 3);

    /** A small burn on the struck block that fades within ~2 s. */
    public static final VfxPreset PLASMA_SCORCH = v -> v.scorch(0.35f, 40);

    /** The Cannon's big ball bursting: a white flash in a teal one, a flat teal shockwave, sparks, embers, a hard light, a light shake. */
    public static final VfxPreset PLASMA_BURST = v -> v
            .single(BastionParticles.MUZZLE_FLASH.get(), v.origin(), 9f, 4, Curve.PULSE, v.params().color())
            .single(BastionParticles.MUZZLE_FLASH.get(), v.origin(), 4f, 3, Curve.PULSE, 0xFFEAFFF6)
            .ring(BastionParticles.SHOCKWAVE_RING.get(), v.origin(), UP, 0, 15f, 10, Curve.EASE_OUT, v.params().color())
            .burst(BastionParticles.SPARK.get(), 20, UP, 170, 0.5f, 0.9f, 12, Curve.LINEAR, v.params().color())
            .burst(BastionParticles.EMBER.get(), 8, UP, 100, 0.12f, 0.7f, 30, Curve.FLICKER, v.params().color())
            .light(15, 6)
            .shake(0.4f, 8);

    /** The big ball's burn, about 2 blocks across, fading over ~10 s. */
    public static final VfxPreset PLASMA_SCORCH_BIG = v -> v.scorch(1.0f, 200);
```

  (`PLASMA_BURST` / `PLASMA_SCORCH_BIG` are only played by the Cannon, Task 5; they live here so `PlasmaEffects.burst` compiles. `ALL` also gets `plasma_burst`, `plasma_scorch_big`.)

`WeaponFx` (+ `Map.entry(Bastion.id("plasma_repeater"), PLASMA_REPEATER)`):

```java
    /** Glowing balls instead of tracers, a splash where each lands (PlasmaEffects). */
    public static final WeaponFx PLASMA_REPEATER = new WeaponFx(VfxPresets.PLASMA_MUZZLE, VfxPresets.PLASMA_HIT, VfxPresets.PLASMA_HIT,
            0.16f, 1f, 18f, false, false, BastionSounds.PLASMA_REPEATER_FIRE, BastionSounds.PLASMA_REPEATER_FIRE);
```

`client/PlasmaEffects.java`:

```java
package dev.bastion.client;

import dev.bastion.client.render.TracerRenderer;
import dev.bastion.client.vfx.VfxManager;
import dev.bastion.client.vfx.VfxParams;
import dev.bastion.client.vfx.VfxPresets;
import dev.bastion.client.vfx.WeaponFx;
import dev.bastion.network.TurretFireEvent;
import dev.bastion.registry.BastionSounds;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

/** Plasma Repeater and Plasma Cannon (PLAN "Ion Rifle, ..."): glowing balls instead of tracers and a burst where each lands. */
public final class PlasmaEffects {
    private static final float BOLT = 0.16f, ORB = 0.42f;

    private PlasmaEffects() {
    }

    public static void fire(ClientLevel level, TurretFireEvent event, WeaponFx fx, Vec3 muzzle, Vec3 direction, int color, boolean cannon) {
        VfxManager.play(fx.muzzle(), muzzle, direction, VfxParams.of().color(color).seed(event.seed()));
        for (TurretFireEvent.Shot shot : event.shots()) {
            TracerRenderer.spawnOrb(shot.blockState(), muzzle, shot.end(), event.speed(), color, cannon ? ORB : BOLT, cannon ? 2f : 1f,
                    (end, normal, hit, blockState) -> burst(level, end, normal, hit, blockState, color, cannon, true));
        }
        TurretFireEffects.playShot(level, event, muzzle, fx, RandomSource.create(event.seed()));
    }

    /** Where a ball lands: the Cannon's big burst or a small splash, a burn on blocks, the sound when {@code loud}. */
    static void burst(ClientLevel level, Vec3 end, Vec3 normal, int hit, int blockState, int color, boolean big, boolean loud) {
        int blockColor = 0xFF888888;
        if (hit == TurretFireEvent.BLOCK) blockColor = 0xFF000000 | Block.stateById(blockState).getMapColor(level, BlockPos.containing(end)).col;
        VfxParams params = VfxParams.of().color(color).normal(normal).blockColor(blockColor).seed(end.hashCode());
        VfxManager.play(big ? VfxPresets.PLASMA_BURST : VfxPresets.PLASMA_HIT, end, normal.reverse(), params);
        if (hit == TurretFireEvent.BLOCK) VfxManager.play(big ? VfxPresets.PLASMA_SCORCH_BIG : VfxPresets.PLASMA_SCORCH, end, normal.reverse(), params);
        if (loud) level.playLocalSound(end.x, end.y, end.z, (big ? BastionSounds.PLASMA_CANNON_HIT : BastionSounds.PLASMA_HIT_SMALL).get(),
                SoundSource.HOSTILE, big ? 1.4f : 0.6f, 0.92f + level.random.nextFloat() * 0.16f, false);
    }
}
```

  `TurretFireEffects.onFire`, after the Ion branch:

```java
        if (fx == WeaponFx.PLASMA_REPEATER) { // glowing balls and splashes (PlasmaEffects)
            PlasmaEffects.fire(level, event, fx, muzzle, direction, color, false);
            return;
        }
```

  `ClientTurret.onStateChange`, the overheat sound:

```java
            var vent = data != null && data.type().equals(BastionWeaponTypes.PLASMA_REPEATER.getId()) ? BastionSounds.PLASMA_VENT
                    : BastionSounds.MACHINE_GUN_OVERHEAT;
            level.playLocalSound(at.x, at.y, at.z, vent.get(), SoundSource.HOSTILE, 0.9f, 1f, false);
```

  `ClientTurret.weaponTick`, before the Flamethrower branch:

```java
        } else if (data.type().equals(BastionWeaponTypes.PLASMA_REPEATER.getId())) {
            // The glow turns teal as the barrel heats; above half heat the usual overheat red takes over (glowColor).
            weapon.chargeColor = color & 0xFFFFFF;
            weapon.chargeGlow = Math.min(1, weapon.heat * 2) * (1 - Mth.clamp((weapon.heat - 0.5f) / 0.5f, 0, 1));
```

- [ ] **Step 5: Verify.** `./gradlew build --offline`; `./gradlew runGameTestServer --offline` → "All 124 required tests passed".

---

### Task 5: Plasma Cannon

**Files:**
- Create: `src/main/java/dev/bastion/weapon/types/PlasmaCannonWeapon.java`, `data/bastion/turret_weapons/plasma_cannon.json`, `src/main/java/dev/bastion/network/PlasmaBurst.java`
- Modify: `BastionWeaponTypes.java`, `BastionItems.java`, `network/TurretEvents.java`, `client/ClientPacketHandler.java`, `client/PlasmaEffects.java`, `client/TurretFireEffects.java`, `client/ClientTurret.java`, `client/render/LaserChargeRenderer.java`, `client/vfx/WeaponFx.java`, `client/vfx/VfxPresets.java`, lang, `TurretEnergyTests.java`, `docs/PLAN.md` (fire_interval wording)

**Interfaces:**
- Consumes: Tasks 1, 2, 4 (`Bullets.create/launch/filtered`, `TracerRenderer.spawnOrb/onLanding`, `PlasmaEffects.burst`, `TURRET_PLASMA`, sounds).
- Produces: `BastionWeaponTypes.PLASMA_CANNON`, `BastionItems.PLASMA_CANNON_TURRET`; `record PlasmaCannonWeapon.Ring(int count, float damage, double range, double speed, int color)`, `static Bullets.Hit PlasmaCannonWeapon.orb(ServerLevel, float damage, Ring, Predicate<Entity>, DamageSource)`, `static List<Vec3> PlasmaCannonWeapon.ring(Vec3 shot, int count)`, `static Vec3 PlasmaCannonWeapon.lift(Level, Vec3)`; `record PlasmaBurst(int bullet, Vec3 at, int color, float speed, int[] ids, List<Vec3> starts, List<Vec3> ends)`; `PlasmaEffects.fragments(ClientLevel, PlasmaBurst)`; `WeaponFx.PLASMA_CANNON`.

- [ ] **Step 1: Failing tests** (append; import `LargeTurretBaseBlock`, `PlasmaCannonWeapon`):

```java
    /** Lowest corner of the Cannon's 2x2 base in the range; its middle is at (3, _, 5). */
    private static final BlockPos CANNON = new BlockPos(2, 2, 4);

    private static TurretBaseBlockEntity cannon(GameTestHelper helper, int cells) {
        LargeTurretBaseBlock.placeAt(helper.getLevel(), helper.absolutePos(CANNON), BastionBlocks.LARGE_TURRET_BASE.get());
        TurretBaseBlockEntity base = (TurretBaseBlockEntity) helper.getBlockEntity(CANNON);
        base.inventory().setStackInSlot(TurretInventory.WEAPON, new ItemStack(BastionItems.PLASMA_CANNON_TURRET.get()));
        if (cells > 0) base.inventory().setStackInSlot(TurretInventory.AMMO_START, new ItemStack(BastionItems.PLASMA_CELL.get(), cells));
        return base;
    }

    /**
     * One cell, one shot: the husk struck loses 18 and no fragment; the ring's fragments hit the husks 3 blocks behind and
     * to both sides of it for 6 each; the cow 3 blocks before it (toward the turret, the 180-degree fragment) is untouched.
     */
    @GameTest(template = RANGE, timeoutTicks = 200, batch = "cannonRingHitsAround")
    public static void cannonRingHitsAround(GameTestHelper helper) {
        TurretBaseBlockEntity turret = cannon(helper, 1);
        LivingEntity struck = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(12, 2, 5));
        LivingEntity[] ring = {helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(15, 2, 5)),
                helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(12, 2, 2)), helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(12, 2, 8))};
        LivingEntity cow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(9, 2, 5));
        helper.succeedWhen(() -> {
            helper.assertTrue(lost(struck) >= 17f && lost(struck) <= 18.05f, "struck husk lost " + lost(struck) + ", state " + turret.state());
            for (LivingEntity husk : ring) helper.assertTrue(lost(husk) >= 5.5f && lost(husk) <= 6.05f, "a ring husk lost " + lost(husk));
            helper.assertTrue(lost(cow) == 0, "a fragment hit the cow");
            helper.assertTrue(turret.state() == TurretState.NO_AMMO, "expected NO_AMMO after its one cell, got " + turret.state());
        });
    }

    /** A fragment stops at a block: the husk behind a wall on the ring's path stays whole while the one behind the struck husk is hit. */
    @GameTest(template = RANGE, timeoutTicks = 200, batch = "cannonFragmentsStopAtBlocks")
    public static void cannonFragmentsStopAtBlocks(GameTestHelper helper) {
        cannon(helper, 1);
        helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(12, 2, 5));
        for (int x = 11; x <= 13; x++) for (int y = 1; y <= 4; y++) helper.setBlock(new BlockPos(x, y, 7), Blocks.STONE);
        LivingEntity walled = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(12, 2, 8));
        LivingEntity open = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(15, 2, 5));
        helper.succeedWhen(() -> {
            helper.assertTrue(lost(open) > 0, "the open ring husk was never hit");
            helper.assertTrue(lost(walled) == 0, "a fragment went through the wall");
        });
    }

    /** Inside min_range 4 the Cannon never fires: the husk 3.5 blocks from the base's middle keeps its health and the cell stays. */
    @GameTest(template = RANGE, timeoutTicks = 120, batch = "cannonKeepsItsMinimumRange")
    public static void cannonKeepsItsMinimumRange(GameTestHelper helper) {
        TurretBaseBlockEntity turret = cannon(helper, 1);
        LivingEntity close = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(6, 2, 5));
        helper.runAtTickTime(100, () -> {
            helper.assertTrue(lost(close) == 0, "fired inside the minimum range");
            helper.assertTrue(turret.inventory().getStackInSlot(TurretInventory.AMMO_START).getCount() == 1, "spent the cell");
            helper.succeed();
        });
    }

    /** The ring: 12 flat unit directions 30 degrees apart, the first along the shot laid flat; a vertical shot still gets a full ring. */
    @GameTest(template = ARENA, batch = "plasmaRingShape")
    public static void plasmaRingShape(GameTestHelper helper) {
        List<Vec3> ring = PlasmaCannonWeapon.ring(new Vec3(0.6, 0.3, 0.8).normalize(), 12);
        helper.assertTrue(ring.size() == 12, "size " + ring.size());
        helper.assertTrue(ring.get(0).distanceTo(new Vec3(0.6, 0, 0.8)) < 1e-6, "first is not the shot laid flat: " + ring.get(0));
        for (int i = 0; i < 12; i++) {
            Vec3 a = ring.get(i), b = ring.get((i + 1) % 12);
            helper.assertTrue(Math.abs(a.y) < 1e-9 && Math.abs(a.length() - 1) < 1e-6, "not a flat unit vector: " + a);
            helper.assertTrue(Math.abs(Math.toDegrees(Math.acos(Math.min(1, a.dot(b)))) - 30) < 1e-3, "not 30 degrees apart at " + i);
        }
        List<Vec3> up = PlasmaCannonWeapon.ring(new Vec3(0, 1, 0), 12);
        helper.assertTrue(up.stream().distinct().count() == 12 && up.stream().allMatch(v -> Math.abs(v.y) < 1e-9), "vertical shot: " + up);
        helper.succeed();
    }

    /** An orb that bursts just above the floor lifts its ring to half a block over it; one in mid-air stays where it is. */
    @GameTest(template = ARENA, batch = "orbOnTheFloorRaisesTheRing")
    public static void orbOnTheFloorRaisesTheRing(GameTestHelper helper) {
        // The arena's floor top is relative y 1.0 (its stone shell is y 0).
        Vec3 low = helper.absoluteVec(new Vec3(5.5, 1.2, 3.5)), air = helper.absoluteVec(new Vec3(5.5, 3.5, 3.5));
        double floorTop = helper.absoluteVec(new Vec3(0, 1.0, 0)).y;
        double lifted = PlasmaCannonWeapon.lift(helper.getLevel(), low).y;
        helper.assertTrue(Math.abs(lifted - (floorTop + 0.5)) < 1e-6, "lifted to " + (lifted - floorTop) + " above the floor");
        helper.assertTrue(PlasmaCannonWeapon.lift(helper.getLevel(), air).equals(air), "moved a mid-air burst");
        helper.succeed();
    }
```

- [ ] **Step 2: Run, verify fail.** Compile errors (`PLASMA_CANNON_TURRET`, `PlasmaCannonWeapon`).

- [ ] **Step 3: Weapon, data, packet, registration.** `plasma_cannon.json`:

```json
{
  "type": "bastion:plasma_cannon",
  "ammo": "bastion:ammo/plasma",
  "energy_color": "#3DFFB5",
  "range": 40,
  "fire_interval": 30,
  "damage": 18,
  "spread": 0,
  "ammo_per_shot": 1,
  "turn_speed": 4,
  "aim_tolerance": 1.5,
  "aim_lock_ticks": 4,
  "min_pitch": -30,
  "max_pitch": 60,
  "pivot_height": 0.875,
  "muzzle_length": 1.15,
  "params": {
    "charge_ticks": 20,
    "orb_speed": 0.8,
    "lead_accuracy": 0.9,
    "min_range": 4,
    "fragments": 12,
    "fragment_damage": 6,
    "fragment_range": 6,
    "fragment_speed": 1.0
  }
}
```

`PlasmaCannonWeapon.java`:

```java
package dev.bastion.weapon.types;

import dev.bastion.damage.BastionDamageTypes;
import dev.bastion.network.BastionNetwork;
import dev.bastion.network.PlasmaBurst;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.weapon.Bullets;
import dev.bastion.weapon.FireContext;
import dev.bastion.weapon.FireProfile;
import dev.bastion.weapon.Hitscan;
import dev.bastion.weapon.LeadSolver;
import dev.bastion.weapon.StatSheet;
import dev.bastion.weapon.WeaponData;
import dev.bastion.weapon.WeaponType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Plasma Cannon (Large Turret Base; PLAN "Ion Rifle, Plasma Repeater & Plasma Cannon", after Crimsonland's Plasma
 * Cannon): charges, then a big slow plasma ball (orb_speed, led) that stops at the first valid target or block. There it
 * bursts into a flat ring of `fragments` small balls, 0 degrees along the shot, starting at the edge of the target struck
 * (which no fragment hits) and at least half a block above the floor; each flies fragment_range at fragment_speed and
 * hits the first valid target on its path, stopping at blocks. One Plasma Cell per shot; never fires inside min_range.
 */
public class PlasmaCannonWeapon extends WeaponType {
    /** The burst: fragment count, damage, reach, speed and colour. */
    public record Ring(int count, float damage, double range, double speed, int color) {
    }

    @Override
    public FireProfile fireProfile() {
        return FireProfile.CHARGED;
    }

    @Override
    public int chargeTicks(StatSheet stats) {
        return Math.round(stats.data().param("charge_ticks"));
    }

    @Override
    public boolean canTarget(Entity entity, TurretBaseBlockEntity turret) {
        WeaponData data = turret.inventory().weaponData();
        if (data == null) return false;
        double min = data.param("min_range");
        return entity.distanceToSqr(turret.pivot(data)) >= min * min;
    }

    @Override
    public Vec3 aimPoint(TurretBaseBlockEntity turret, LivingEntity target, Vec3 point, StatSheet stats) {
        return LeadSolver.lead(turret, target, point, stats, stats.data().param("orb_speed"));
    }

    @Override
    public void onFire(FireContext context) {
        StatSheet stats = context.stats();
        WeaponData data = stats.data();
        Ring ring = new Ring(Math.round(data.param("fragments")), data.param("fragment_damage") * stats.damage() / data.damage(),
                data.param("fragment_range"), data.param("fragment_speed"), data.energyColor());
        Bullets.fire(context, this, 0, List.of(context.direction()), data.param("orb_speed"), 0, Bullets.validTargets(context, this),
                orb(context.level(), stats.damage(), ring, Bullets.filtered(context, this),
                        BastionDamageTypes.of(context.level(), BastionDamageTypes.TURRET_PLASMA)));
    }

    /** What the orb does where it stops: {@code damage} to the target struck, then the ring (public for the GameTests). */
    public static Bullets.Hit orb(ServerLevel level, float damage, Ring ring, Predicate<Entity> canHit, DamageSource source) {
        return new Bullets.Hit() {
            @Override
            public void hit(Bullets.Bullet bullet, EntityHitResult hit) {
                Entity struck = hit.getEntity();
                Hitscan.damage(struck, source, damage);
                AABB box = struck.getBoundingBox();
                Vec3 center = new Vec3(box.getCenter().x, Mth.clamp(hit.getLocation().y, box.minY + 0.5, box.maxY), box.getCenter().z);
                burst(level, bullet, center, box.getXsize() / 2 * Math.sqrt(2) + 0.05, ring, canHit.and(e -> e != struck), source);
            }

            @Override
            public void blocked(Bullets.Bullet bullet, Vec3 at) {
                burst(level, bullet, lift(level, at.subtract(bullet.direction.scale(0.15))), 0, ring, canHit, source);
            }
        };
    }

    private static void burst(ServerLevel level, Bullets.Bullet orb, Vec3 center, double startRadius, Ring ring, Predicate<Entity> canHit,
                              DamageSource source) {
        List<Vec3> directions = ring(orb.direction, ring.count());
        List<Vec3> starts = new ArrayList<>(directions.size()), ends = new ArrayList<>(directions.size());
        for (Vec3 d : directions) {
            starts.add(center.add(d.scale(startRadius)));
            ends.add(starts.get(starts.size() - 1).add(d.scale(ring.range())));
        }
        List<Bullets.Bullet> fragments = Bullets.create(level, starts, directions, ring.speed(), ring.range(), 0, canHit,
                (bullet, hit) -> Hitscan.damage(hit.getEntity(), source, ring.damage()));
        // Clients learn the fragments' ids before any of them can report an impact (launch moves them once right away).
        BastionNetwork.sendNear(level, center, new PlasmaBurst(orb.id, center, ring.color(), (float) ring.speed(),
                fragments.stream().mapToInt(b -> b.id).toArray(), starts, ends));
        Bullets.launch(fragments);
    }

    /** {@code count} flat unit directions evenly spaced, the first along the shot laid flat (north when it flew straight up or down). */
    public static List<Vec3> ring(Vec3 shot, int count) {
        Vec3 flat = new Vec3(shot.x, 0, shot.z);
        flat = flat.lengthSqr() < 1e-6 ? new Vec3(0, 0, -1) : flat.normalize();
        List<Vec3> out = new ArrayList<>(count);
        for (int k = 0; k < count; k++) { // Math, not Vec3.yRot: Mth's sine table is too coarse for an even ring
            double a = Math.PI * 2 * k / count, c = Math.cos(a), s = Math.sin(a);
            out.add(new Vec3(flat.x * c + flat.z * s, 0, flat.z * c - flat.x * s));
        }
        return out;
    }

    /** Keeps a burst point at least half a block above the floor right under it, so the ring does not plough into the ground. */
    public static Vec3 lift(Level level, Vec3 at) {
        BlockHitResult floor = level.clip(new ClipContext(at, at.add(0, -0.5, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null));
        return floor.getType() == HitResult.Type.MISS ? at : new Vec3(at.x, floor.getLocation().y + 0.5, at.z);
    }
}
```

  `network/PlasmaBurst.java`:

```java
package dev.bastion.network;

import dev.bastion.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * S->C a Plasma Cannon orb ({@code bullet}) burst at {@code at}: the fragment bullets' ids and paths, so clients fly them
 * once the orb's tracer lands and stop each at its BulletImpact. Travels in TurretEvents, queued before any fragment's impact.
 */
public record PlasmaBurst(int bullet, Vec3 at, int color, float speed, int[] ids, List<Vec3> starts, List<Vec3> ends) {
    /** Most fragments on the wire (the JSON has 12). */
    public static final int MAX = 32;

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(bullet);
        write(buf, at);
        buf.writeInt(color);
        buf.writeFloat(speed);
        int n = Math.min(ids.length, MAX);
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            buf.writeVarInt(ids[i]);
            write(buf, starts.get(i));
            write(buf, ends.get(i));
        }
    }

    public static PlasmaBurst decode(FriendlyByteBuf buf) {
        int bullet = buf.readVarInt();
        Vec3 at = read(buf);
        int color = buf.readInt();
        float speed = buf.readFloat();
        int n = Math.min(buf.readVarInt(), MAX);
        int[] ids = new int[n];
        List<Vec3> starts = new ArrayList<>(n), ends = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            ids[i] = buf.readVarInt();
            starts.add(read(buf));
            ends.add(read(buf));
        }
        return new PlasmaBurst(bullet, at, color, speed, ids, starts, ends);
    }

    private static void write(FriendlyByteBuf buf, Vec3 v) {
        buf.writeDouble(v.x).writeDouble(v.y).writeDouble(v.z);
    }

    private static Vec3 read(FriendlyByteBuf buf) {
        return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.plasmaBurst(this));
        context.get().setPacketHandled(true);
    }
}
```

  `TurretEvents.KINDS`: append `new Kind<>(PlasmaBurst.class, PlasmaBurst::encode, PlasmaBurst::decode, PlasmaBurst::handle)` (protocol is already 9 from Task 3).
  `BastionWeaponTypes.PLASMA_CANNON` (`"plasma_cannon"`). `BastionItems` after `REPULSOR_DOME_TURRET`:

```java
    /** A big slow plasma ball that bursts into a ring of twelve small ones; Plasma Cells. */
    public static final RegistryObject<WeaponModuleItem> PLASMA_CANNON_TURRET = REGISTER.register("plasma_cannon_turret",
            () -> new WeaponModuleItem(new Item.Properties().stacksTo(1), Bastion.id("plasma_cannon"), true));
```

  Lang en_us: `"Plasma Cannon Turret"`, ability `"Charges, then a big slow plasma ball (18 damage) bursts into a ring of 12 small balls that fly 6 blocks out (6 damage each, stopped by walls). Never fires within 4 blocks. 1 Plasma Cell per shot"`; id_id: `"Turret Meriam Plasma"`, `"Mengisi daya, lalu bola plasma besar yang lambat (18 damage) pecah jadi cincin 12 bola kecil yang terbang 6 blok ke luar (6 damage tiap bola, terhenti dinding). Tidak menembak dalam 4 blok. 1 Sel Plasma per tembakan"`.

- [ ] **Step 4: Visuals.** `VfxPresets` (+ `ALL` entry `plasma_cannon_muzzle`; `PLASMA_BURST` and `PLASMA_SCORCH_BIG` already exist from Task 4):

```java
    /** The big ball leaving the barrel: a flash in a white one, a wide ring, a puff of smoke, a hard light, a kick. */
    public static final VfxPreset PLASMA_CANNON_MUZZLE = v -> v
            .single(BastionParticles.MUZZLE_FLASH.get(), v.origin(), 5f, 4, Curve.PULSE, v.params().color())
            .single(BastionParticles.MUZZLE_FLASH.get(), v.origin(), 2.4f, 3, Curve.PULSE, 0xFFEAFFF6)
            .ring(BastionParticles.MUZZLE_RING.get(), v.origin(), v.direction(), 0.1f, 3.2f, 7, Curve.EASE_OUT, v.params().color())
            .burst(BastionParticles.SMOKE_PUFF.get(), 4, v.direction(), 30, 0.06f, 2f, 35, Curve.EASE_OUT, 0x90C8D8D2)
            .light(14, 4)
            .shake(0.3f, 10);
```
  `WeaponFx` (+ `Map.entry(Bastion.id("plasma_cannon"), PLASMA_CANNON)`):

```java
    /** A big slow ball that bursts into a ring of small ones (PlasmaEffects); a heavy kick. */
    public static final WeaponFx PLASMA_CANNON = new WeaponFx(VfxPresets.PLASMA_CANNON_MUZZLE, VfxPresets.PLASMA_BURST, VfxPresets.PLASMA_BURST,
            0.42f, 1f, 110f, false, false, BastionSounds.PLASMA_CANNON_FIRE, BastionSounds.PLASMA_CANNON_FIRE);
```

  `TurretFireEffects`: the plasma branch becomes `if (fx == WeaponFx.PLASMA_REPEATER || fx == WeaponFx.PLASMA_CANNON) { PlasmaEffects.fire(level, event, fx, muzzle, direction, color, fx == WeaponFx.PLASMA_CANNON); return; }`.
  `PlasmaEffects`:

```java
    private static final float FRAGMENT = 0.13f;

    /** The Cannon's ring: small balls flying out from where the orb burst, one whoosh for all of them; a fragment hitting a mob splats aloud. */
    public static void fragments(ClientLevel level, PlasmaBurst burst) {
        int color = 0xFF000000 | burst.color();
        for (int i = 0; i < burst.ids().length; i++) {
            TracerRenderer.spawnOrb(burst.ids()[i], burst.starts().get(i), burst.ends().get(i), burst.speed(), color, FRAGMENT, 0.8f,
                    (end, normal, hit, blockState) -> burst(level, end, normal, hit, blockState, color, false, hit == TurretFireEvent.ENTITY));
        }
        Vec3 at = burst.at();
        level.playLocalSound(at.x, at.y, at.z, BastionSounds.PLASMA_FRAGMENT.get(), SoundSource.HOSTILE, 0.8f, 0.95f + level.random.nextFloat() * 0.1f, false);
    }
```

  `ClientPacketHandler`:

```java
    /** Plasma Cannon: the ring of fragments flies off once the orb's tracer lands (or at once if it has none). */
    public static void plasmaBurst(PlasmaBurst burst) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) TracerRenderer.onLanding(burst.bullet(), () -> PlasmaEffects.fragments(level, burst));
    }
```

  `ClientTurret.onStateChange` charge sound chain: add `: BastionWeaponTypes.PLASMA_CANNON.getId().equals(type) ? BastionSounds.PLASMA_CANNON_CHARGE` before the Sniper default. `ClientTurret.weaponTick`, next to the Repeater branch:

```java
        } else if (data.type().equals(BastionWeaponTypes.PLASMA_CANNON.getId())) {
            // The ball grows at the muzzle (LaserChargeRenderer) with light streaming in; the chamber glow turns teal, fades after the shot.
            int charge = Math.round(data.params().getOrDefault("charge_ticks", 1f));
            float progress = state == TurretState.CHARGING ? chargeProgress(level.getGameTime(), 0, charge) : -1;
            weapon.chargeColor = color & 0xFFFFFF;
            weapon.chargeGlow = progress < 0 ? Math.max(0, weapon.chargeGlow - 0.07f) : Math.min(1, 0.3f + progress);
            if (progress >= 0) LaserEffects.gathering(level, this, progress, color);
```

  `LaserChargeRenderer.render`: accept both weapons, the Cannon's orb 1.6x:

```java
            boolean laser = data != null && data.type().equals(BastionWeaponTypes.LASER_RIFLE.getId());
            boolean plasma = data != null && data.type().equals(BastionWeaponTypes.PLASMA_CANNON.getId());
            if (!laser && !plasma) continue;
            int charge = Math.round(data.params().getOrDefault("charge_ticks", 1f));
            orbs.add(new Orb(client.muzzles[0], client.chargeProgress(level.getGameTime(), partialTick, charge), data.energyColor(), plasma ? 1.6f : 1f));
```

  with `record Orb(Vec3 at, float progress, int argb, float scale)` and `size(...)` multiplied by `o.scale` (class javadoc: "Laser Rifle and Plasma Cannon charge orbs").
  `docs/PLAN.md` Plasma Cannon bullet: replace "`fire_interval` 50 tick (termasuk charge)" with "`fire_interval` 30 + `charge_ticks` 20 = siklus 50 tick (state machine mengisi setelah cooldown)".

- [ ] **Step 5: Verify.** `./gradlew build --offline`; `./gradlew runGameTestServer --offline` → "All 128 required tests passed".

---

### Task 6: Models, clips, textures, item displays, parts, part recipes

**Files:** `tools/blockout/design_weapons.py`, `assets/bastion/animations/{ion_rifle_turret,plasma_repeater_turret,plasma_cannon_turret}.animation.json`, `tools/gen_textures.py`, `tools/fit_item_display.py`, `assets/bastion/parts.json`, `BastionItems.PARTS`, `tools/gen_recipes.py`, lang; generated `geo/*.geo.json`, `textures/item/*`, `models/item/*`, recipes.

**Interfaces:** Produces geo `ion_rifle_turret` (bones: PLAN 6.2 + `coils`, `glow_coils`, `glow_tip`, `muzzle_0` at z -24.6 = muzzle_length 1.5375), `plasma_repeater_turret` (+ `chamber`, `glow_chamber`, `bore`, `vent_l`/`vent_r` top flaps, `muzzle_0` at z -16.8 = 1.05), `plasma_cannon_turret` (pivot 14 px = 0.875, + `glow_chamber`, `bore`, `muzzle_0` at the tip -18.4 → muzzle_length 1.15). Nine parts.

- [ ] **Step 1: Model functions.** In `design_weapons.py` import `TAN22` (`from kit import Kit, GEO, TAN22`) and append before the call list:

```python
def octagon_z(k, tag, r, cy, z0, z1, step=0.01):
    """Octagon prism along Z (a barrel or chamber seen end-on), flats at distance r: four boxes turned about Z."""
    a = 2 * r * TAN22
    return [k.box(tag, -r, cy - a / 2, z0 - step * i, r, cy + a / 2, z1 - step * i, rot=[0, 0, rz], pivot=[0, cy, z0], hide=("up", "down"))
            for i, rz in enumerate((0, 45, 90, -45))]


def ion_rifle(path):
    """Ion Rifle (after Crimsonland's): a slim receiver with a capacitor block behind, a long thin barrel with three coil
    rings near the tip (glow_coils; the fire clip pulses them) and a short emitter tip. muzzle_0 = the tip
    (ion_rifle.json muzzle_length 1.5375 = 24.6 px)."""
    k = Kit(128)
    yoke(k)
    k.bone("body", "pitch_pivot", pivot=(0, P, 0), cubes=[
        k.box("rcv", -3.4, 3.8, -6.0, 3.4, 10.0, 5.0), k.box("rcvtop", -2.6, 10.0, -5.0, 2.6, 10.8, 3.6),
        k.box("cap", -3.0, 4.4, 5.0, 3.0, 9.4, 8.6)])
    k.bone("body_frame", "body", pivot=(0, P, 0), cubes=[k.box("capfin", -3.4, y, 5.4, 3.4, y + 0.5, 8.2) for y in (5.0, 6.6, 8.2)] + [
        k.box("rail", -4.2, 5.6, -5.0, -3.4, 8.4, 3.0), k.box("rail", 3.4, 5.6, -5.0, 4.2, 8.4, 3.0)])
    k.bone("glow_strips", "body", pivot=(0, P, 0), cubes=[
        k.box("gstrip", -4.25, 6.6, -4.4, -4.15, 7.4, 2.4), k.box("gstrip", 4.15, 6.6, -4.4, 4.25, 7.4, 2.4),
        k.box("capglow", -2.0, 9.4, 5.8, 2.0, 9.5, 7.8)])
    k.bone("vent_l", "body", pivot=(-3.45, 5.0, 2.0), cubes=[k.box("vent", -3.5, 4.2, 0.6, -3.35, 5.8, 3.4)])
    k.bone("vent_r", "body", pivot=(3.45, 5.0, 2.0), cubes=[k.box("vent", 3.35, 4.2, 0.6, 3.5, 5.8, 3.4)])
    k.bone("sensor", "body", pivot=(2.6, 11.2, -3.0), cubes=[k.box("scope", 1.8, 10.8, -4.6, 3.4, 11.8, -1.6)])
    k.bone("glow_lens", "sensor", pivot=(2.6, 11.3, -4.65), cubes=[k.box("lens", 2.0, 11.0, -4.65, 3.2, 11.6, -4.6)])
    k.bone("barrel_0", "body", pivot=(0, P, -6.0), cubes=k.rod_z("ibarrel", 0, P, -23.0, -6.0, 1.8) + [
        k.box("shroud", -1.4, P - 1.4, -10.0, 1.4, P + 1.4, -6.0), k.box("tip", -1.1, P - 1.1, -24.2, 1.1, P + 1.1, -23.0)])
    coils, glow = [], []
    for z in (-15.0, -18.0, -21.0):
        coils += square_frame(k, "icoil", P, z - 0.6, z + 0.6, 2.4, 0.8)
        glow += square_frame(k, "icoilglow", P, z - 0.25, z + 0.25, 1.6, 0.3)
    k.bone("coils", "barrel_0", pivot=(0, P, -18.0), cubes=coils)
    k.bone("glow_coils", "coils", pivot=(0, P, -18.0), cubes=glow)
    k.bone("glow_tip", "barrel_0", pivot=(0, P, -24.25), cubes=[k.box("tipglow", -0.6, P - 0.6, -24.3, 0.6, P + 0.6, -24.2)])
    k.bone("muzzle_0", "barrel_0", pivot=(0, P, -24.6))
    k.write("ion_rifle_turret", path, bounds=(3.5, 2.5), offset=(0, 0.5, 0))


def plasma_repeater(path):
    """Plasma Repeater: a squat finned receiver, a round containment chamber at the base of a short thick barrel
    (glow_chamber windows; the client tints the model glow teal as it heats), two flaps on top that swing open when it
    overheats (vent_l / vent_r). muzzle_0 = the mouth (plasma_repeater.json muzzle_length 1.05 = 16.8 px)."""
    k = Kit(128)
    yoke(k)
    k.bone("body", "pitch_pivot", pivot=(0, P, 0), cubes=[
        k.box("rcv", -3.8, 3.4, -4.0, 3.8, 10.4, 5.4), k.box("rcvtop", -3.0, 10.4, -3.0, 3.0, 11.2, 4.4),
        k.box("rcvrear", -2.8, 4.6, 5.4, 2.8, 9.2, 6.8)])
    k.bone("body_frame", "body", pivot=(0, P, 0), cubes=[k.box("fin", x0, y, -2.4, x1, y + 0.6, 4.6)
                                                         for side in (-1, 1) for x0, x1 in [sorted((side * 3.8, side * 4.7))]
                                                         for y in (4.4, 5.8, 7.2, 8.6)])
    k.bone("chamber", "body", pivot=(0, P, -6.5), cubes=octagon_z(k, "chamber", 3.4, P, -9.0, -4.0) + octagon_z(k, "chambercap", 2.6, P, -9.8, -3.2))
    k.bone("glow_chamber", "chamber", pivot=(0, P, -6.5), cubes=[
        k.box("window", -3.5, P - 0.9, -7.6, -3.4, P + 0.9, -5.4), k.box("window", 3.4, P - 0.9, -7.6, 3.5, P + 0.9, -5.4),
        k.box("windowtop", -0.9, P + 3.4, -7.6, 0.9, P + 3.5, -5.4)])
    k.bone("barrel_0", "body", pivot=(0, P, -9.8), cubes=k.rod_z("pbarrel", 0, P, -15.8, -9.8, 3.2, 2.4) + [
        k.box("lip", -2.1, P - 2.1, -16.8, 2.1, P + 2.1, -15.8)])
    k.bone("bore", "barrel_0", pivot=(0, P, -16.8), cubes=[k.box("bore", -1.3, P - 1.3, -16.85, 1.3, P + 1.3, -16.8)])
    k.bone("glow_strips", "body", pivot=(0, P, 0), cubes=[k.box("gstrip", -3.0, 11.2, 3.8, 3.0, 11.3, 4.3)])
    k.bone("vent_l", "body", pivot=(-2.9, 11.2, 0.6), cubes=[k.box("vent", -2.9, 11.2, -2.2, -0.3, 11.7, 3.4)])
    k.bone("vent_r", "body", pivot=(2.9, 11.2, 0.6), cubes=[k.box("vent", 0.3, 11.2, -2.2, 2.9, 11.7, 3.4)])
    k.bone("sensor", "body", pivot=(0, 11.7, -3.2), cubes=[k.box("scope", -1.0, 11.2, -4.0, 1.0, 12.2, -2.4)])
    k.bone("glow_lens", "sensor", pivot=(0, 11.7, -4.05), cubes=[k.box("lens", -0.6, 11.4, -4.05, 0.6, 12.0, -4.0)])
    k.bone("muzzle_0", "barrel_0", pivot=(0, P, -16.8))
    k.write("plasma_repeater_turret", path, bounds=(3, 2.5), offset=(0, 0.5, 0))


# Plasma Cannon: axle height and barrel tip; plasma_cannon.json pivot_height = PC_P / 16, muzzle_length = -PC_TIP / 16.
PC_P, PC_TIP = 14.0, -18.4


def plasma_cannon(path):
    """Plasma Cannon for the Large Turret Base: a turntable and pedestal with two arms holding a big round containment
    chamber on the axle (glow_chamber band: the client turns the model glow teal while it charges), a short wide barrel
    with a dark bore, vent flaps and the sensor on top of the chamber. muzzle_0 = the mouth."""
    k = Kit(128)
    P = PC_P
    k.bone("root")
    yaw = k.octagon("turn", 11, 0, 1.6) + k.chamfer("pedestal", 8.5, 2.5, 1.6, 6.0)
    for side in (-1, 1):
        x0, x1 = sorted((side * 8.6, side * 11.4))
        yaw += [k.box("arm", x0, 6.0, -4.0, x1, P + 3.0, 4.0), k.box("armcap", x0, P + 3.0, -2.8, x1, P + 4.2, 2.8)]
    k.bone("yaw_pivot", "root", cubes=yaw)
    k.bone("glow_strips", "yaw_pivot", pivot=(0, 4.0, 0), cubes=k.faces4("gstrip", 8.6, 3.7, 4.3, 6.0, 0.1))
    k.bone("axle", "yaw_pivot", pivot=(0, P, 0), cubes=k.rod_x("axle", P, 0, -8.6, 8.6, 3.0))
    k.bone("pitch_pivot", "yaw_pivot", pivot=(0, P, 0))
    k.bone("body", "pitch_pivot", pivot=(0, P, 0), cubes=octagon_z(k, "chamber", 7.0, P, -6.0, 4.0) + octagon_z(k, "chambercap", 5.0, P, -8.0, 6.0))
    k.bone("glow_chamber", "body", pivot=(0, P, -1.0), cubes=octagon_z(k, "chamberglow", 7.1, P, -1.6, -0.4))
    k.bone("vent_l", "body", pivot=(-2.6, P + 7.0, 1.0), cubes=[k.box("vent", -4.6, P + 7.0, -2.0, -0.6, P + 7.6, 3.0)])
    k.bone("vent_r", "body", pivot=(2.6, P + 7.0, 1.0), cubes=[k.box("vent", 0.6, P + 7.0, -2.0, 4.6, P + 7.6, 3.0)])
    k.bone("sensor", "body", pivot=(0, P + 7.7, -4.0), cubes=[k.box("scope", -1.2, P + 7.0, -5.0, 1.2, P + 8.4, -3.0)])
    k.bone("glow_lens", "sensor", pivot=(0, P + 7.7, -5.05), cubes=[k.box("lens", -0.8, P + 7.3, -5.05, 0.8, P + 8.1, -5.0)])
    k.bone("barrel_0", "body", pivot=(0, P, -8.0), cubes=octagon_z(k, "cbarrel", 4.4, P, -16.0, -8.0) + octagon_z(k, "clip", 5.2, P, PC_TIP, -16.0))
    k.bone("bore", "barrel_0", pivot=(0, P, PC_TIP), cubes=[k.box("bore", -3.0, P - 3.0, PC_TIP - 0.05, 3.0, P + 3.0, PC_TIP + 0.05)])
    k.bone("muzzle_0", "barrel_0", pivot=(0, P, PC_TIP - 0.3))
    k.write("plasma_cannon_turret", path, bounds=(3, 3), offset=(0, 1, 0))
```

  Calls: `ion_rifle(f"{out}/ion_rifle_turret.geo.json")`, `plasma_repeater(f"{out}/plasma_repeater_turret.geo.json")`, `plasma_cannon(f"{out}/plasma_cannon_turret.geo.json")`. Run `python3 tools/blockout/design_weapons.py`, then `git checkout -- src/main/resources/assets/bastion/geo/missile_launcher_turret.geo.json` (its committed bone order differs, HANDOFF §6) and `git status src/main/resources/assets/bastion/geo` → only the three new files. If the kit asserts "tag reused at another size", give that box its own tag.

- [ ] **Step 2: Clips** (`format_version` "1.8.0", the six clips every weapon has; the Repeater also `overheat` and `cooldown_vent`). `animations/ion_rifle_turret.animation.json`:

```json
{
  "format_version": "1.8.0",
  "animations": {
    "animation.ion_rifle_turret.deploy": {"animation_length": 0.9, "bones": {
      "barrel_0": {"position": {"0.0": [0, 0, 6], "0.6": [0, 0, -0.4], "0.9": [0, 0, 0]}},
      "coils": {"scale": {"0.0": [0.4, 0.4, 1], "0.6": [1.06, 1.06, 1], "0.9": [1, 1, 1]}},
      "body": {"scale": {"0.0": [0.92, 0.92, 0.92], "0.3": [1.02, 1.02, 1.02], "0.45": [1, 1, 1]}}}},
    "animation.ion_rifle_turret.idle": {"animation_length": 4, "loop": true, "bones": {
      "sensor": {"rotation": {"0.0": [0, 0, 0], "1.3": [0, 10, 0], "2.9": [0, -10, 0], "4.0": [0, 0, 0]}},
      "glow_coils": {"scale": {"0.0": [1, 1, 1], "2.0": [1.06, 1.06, 1], "4.0": [1, 1, 1]}}}},
    "animation.ion_rifle_turret.held_idle": {"animation_length": 4, "loop": true, "bones": {
      "glow_coils": {"scale": {"0.0": [1, 1, 1], "2.0": [1.06, 1.06, 1], "4.0": [1, 1, 1]}}}},
    "animation.ion_rifle_turret.aim": {"animation_length": 0.3, "loop": "hold_on_last_frame", "bones": {
      "barrel_0": {"position": {"0.0": [0, 0, 0], "0.3": [0, 0, -0.4]}}}},
    "animation.ion_rifle_turret.fire": {"animation_length": 0.45, "bones": {
      "barrel_0": {"position": {"0.0": [0, 0, 1.6], "0.12": [0, 0, 0.4], "0.45": [0, 0, 0]}},
      "glow_coils": {"scale": {"0.0": [1.35, 1.35, 1], "0.2": [0.95, 0.95, 1], "0.45": [1, 1, 1]}},
      "body": {"position": {"0.0": [0, 0, 0.4], "0.2": [0, 0, 0]}}}},
    "animation.ion_rifle_turret.no_ammo": {"animation_length": 1.2, "loop": true, "bones": {
      "sensor": {"rotation": {"0.0": [10, 0, 0], "0.6": [14, 0, 0], "1.2": [10, 0, 0]}},
      "glow_coils": {"scale": {"0.0": [0.8, 0.8, 1], "0.6": [0.75, 0.75, 1], "1.2": [0.8, 0.8, 1]}}}}
  }
}
```

  `animations/plasma_repeater_turret.animation.json`:

```json
{
  "format_version": "1.8.0",
  "animations": {
    "animation.plasma_repeater_turret.deploy": {"animation_length": 0.9, "bones": {
      "barrel_0": {"position": {"0.0": [0, 0, 4], "0.6": [0, 0, -0.3], "0.9": [0, 0, 0]}},
      "chamber": {"scale": {"0.0": [0.5, 0.5, 0.5], "0.5": [1.06, 1.06, 1.06], "0.8": [1, 1, 1]}},
      "body": {"scale": {"0.0": [0.92, 0.92, 0.92], "0.3": [1.02, 1.02, 1.02], "0.45": [1, 1, 1]}}}},
    "animation.plasma_repeater_turret.idle": {"animation_length": 4, "loop": true, "bones": {
      "sensor": {"rotation": {"0.0": [0, 0, 0], "1.3": [0, 10, 0], "2.9": [0, -10, 0], "4.0": [0, 0, 0]}},
      "glow_chamber": {"scale": {"0.0": [1, 1, 1], "2.0": [1.04, 1.04, 1.04], "4.0": [1, 1, 1]}}}},
    "animation.plasma_repeater_turret.held_idle": {"animation_length": 4, "loop": true, "bones": {
      "glow_chamber": {"scale": {"0.0": [1, 1, 1], "2.0": [1.04, 1.04, 1.04], "4.0": [1, 1, 1]}}}},
    "animation.plasma_repeater_turret.aim": {"animation_length": 0.3, "loop": "hold_on_last_frame", "bones": {
      "barrel_0": {"position": {"0.0": [0, 0, 0], "0.3": [0, 0, -0.3]}}}},
    "animation.plasma_repeater_turret.fire": {"animation_length": 0.2, "bones": {
      "barrel_0": {"position": {"0.0": [0, 0, 0.9], "0.08": [0, 0, 0.2], "0.2": [0, 0, 0]}},
      "chamber": {"scale": {"0.0": [1.08, 1.08, 1.08], "0.2": [1, 1, 1]}}}},
    "animation.plasma_repeater_turret.overheat": {"animation_length": 0.5, "bones": {
      "vent_l": {"rotation": {"0.0": [0, 0, 0], "0.3": [0, 0, -50], "0.4": [0, 0, -44]}},
      "vent_r": {"rotation": {"0.0": [0, 0, 0], "0.3": [0, 0, 50], "0.4": [0, 0, 44]}}}},
    "animation.plasma_repeater_turret.cooldown_vent": {"animation_length": 0.6, "bones": {
      "vent_l": {"rotation": {"0.0": [0, 0, -44], "0.45": [0, 0, 4], "0.6": [0, 0, 0]}},
      "vent_r": {"rotation": {"0.0": [0, 0, 44], "0.45": [0, 0, -4], "0.6": [0, 0, 0]}}}},
    "animation.plasma_repeater_turret.no_ammo": {"animation_length": 1.2, "loop": true, "bones": {
      "sensor": {"rotation": {"0.0": [10, 0, 0], "0.6": [14, 0, 0], "1.2": [10, 0, 0]}},
      "glow_chamber": {"scale": {"0.0": [0.85, 0.85, 0.85], "0.6": [0.8, 0.8, 0.8], "1.2": [0.85, 0.85, 0.85]}}}}
  }
}
```

  `animations/plasma_cannon_turret.animation.json`:

```json
{
  "format_version": "1.8.0",
  "animations": {
    "animation.plasma_cannon_turret.deploy": {"animation_length": 1.1, "bones": {
      "barrel_0": {"position": {"0.0": [0, 0, 6], "0.7": [0, 0, -0.4], "1.1": [0, 0, 0]}},
      "body": {"scale": {"0.0": [0.7, 0.7, 0.7], "0.5": [1.04, 1.04, 1.04], "0.8": [1, 1, 1]}}}},
    "animation.plasma_cannon_turret.idle": {"animation_length": 4, "loop": true, "bones": {
      "sensor": {"rotation": {"0.0": [0, 0, 0], "1.3": [0, 12, 0], "2.9": [0, -12, 0], "4.0": [0, 0, 0]}},
      "glow_chamber": {"scale": {"0.0": [1, 1, 1], "2.0": [1.03, 1.03, 1.03], "4.0": [1, 1, 1]}}}},
    "animation.plasma_cannon_turret.held_idle": {"animation_length": 4, "loop": true, "bones": {
      "glow_chamber": {"scale": {"0.0": [1, 1, 1], "2.0": [1.03, 1.03, 1.03], "4.0": [1, 1, 1]}}}},
    "animation.plasma_cannon_turret.aim": {"animation_length": 0.4, "loop": "hold_on_last_frame", "bones": {
      "barrel_0": {"position": {"0.0": [0, 0, 0], "0.4": [0, 0, -0.5]}}}},
    "animation.plasma_cannon_turret.fire": {"animation_length": 0.8, "bones": {
      "barrel_0": {"position": {"0.0": [0, 0, 3.0], "0.2": [0, 0, 0.8], "0.8": [0, 0, 0]}},
      "body": {"position": {"0.0": [0, 0, 1.0], "0.35": [0, 0, 0]}},
      "glow_chamber": {"scale": {"0.0": [1.2, 1.2, 1.2], "0.3": [0.97, 0.97, 0.97], "0.8": [1, 1, 1]}}}},
    "animation.plasma_cannon_turret.no_ammo": {"animation_length": 1.2, "loop": true, "bones": {
      "sensor": {"rotation": {"0.0": [10, 0, 0], "0.6": [14, 0, 0], "1.2": [10, 0, 0]}},
      "glow_chamber": {"scale": {"0.0": [0.85, 0.85, 0.85], "0.6": [0.8, 0.8, 0.8], "1.2": [0.85, 0.85, 0.85]}}}}
  }
}
```

- [ ] **Step 3: Textures.** `gen_textures.py` model table:

```python
    "ion_rifle_turret": {"yaw_pivot": "armor", "axle": "steel", "body": "armor", "body_frame": "frame", "glow_strips": "emitter",
                         "vent_l": "vent", "vent_r": "vent", "sensor": "steel", "glow_lens": "emitter", "barrel_0": "frame",
                         "coils": "steel", "glow_coils": "emitter", "glow_tip": "emitter"},
    "plasma_repeater_turret": {"yaw_pivot": "armor", "axle": "steel", "body": "armor", "body_frame": "frame", "chamber": "hex",
                               "glow_chamber": "emitter", "glow_strips": "emitter", "barrel_0": "steel", "bore": "bore",
                               "vent_l": "vent", "vent_r": "vent", "sensor": "steel", "glow_lens": "emitter"},
    "plasma_cannon_turret": {"yaw_pivot": "armor", "glow_strips": "emitter", "axle": "steel", "body": "hex", "glow_chamber": "emitter",
                             "vent_l": "vent", "vent_r": "vent", "sensor": "steel", "glow_lens": "emitter", "barrel_0": "steel",
                             "bore": "bore"},
```

  TEXTURES: `item/<name>.png` / `item/<name>_e.png` via `paint_model("<name>")[0]` / `[1]` for the three. Run `cd tools && python3 gen_textures.py`; only the 6 new files appear in `git status`.

- [ ] **Step 4: Item displays, parts, recipes, lang.**
  - `tools/fit_item_display.py` MODELS: `"ion_rifle_turret": "ion_rifle_turret", "plasma_repeater_turret": "plasma_repeater_turret", "plasma_cannon_turret": "plasma_cannon_turret"`.
  - `parts.json`: `"ion_barrel": {"model": "ion_rifle_turret", "texture": "item/ion_rifle_turret", "bones": ["barrel_0", "coils", "glow_coils", "glow_tip"]}`, `"ion_capacitor": {… "bones": ["body", "body_frame", "glow_strips"]}`, `"ion_mount": {… "bones": ["yaw_pivot", "axle"]}`, `"plasma_barrel": {"model": "plasma_repeater_turret", "texture": "item/plasma_repeater_turret", "bones": ["barrel_0", "bore"]}`, `"plasma_chamber": {… "bones": ["chamber", "glow_chamber"]}`, `"repeater_mount": {… "bones": ["yaw_pivot", "axle"]}`, `"plasma_cannon_core": {"model": "plasma_cannon_turret", "texture": "item/plasma_cannon_turret", "bones": ["body", "glow_chamber"]}`, `"plasma_cannon_barrel": {… "bones": ["barrel_0", "bore"]}`, `"plasma_cannon_mount": {… "bones": ["yaw_pivot", "glow_strips", "axle"]}`.
  - `BastionItems.PARTS`: append `"ion_barrel", "ion_capacitor", "ion_mount", "plasma_barrel", "plasma_chamber", "repeater_mount", "plasma_cannon_core", "plasma_cannon_barrel", "plasma_cannon_mount"`.
  - `gen_recipes.py` PARTS:

```python
    "ion_rifle_turret": {"ion_barrel": ({"iron_ingot": 2, "copper_ingot": 1, "amethyst_shard": 1}, SPECIAL_PART),
                         "ion_capacitor": ({"copper_ingot": 2, "redstone": 2, "gold_ingot": 1}, SMALL_PART),
                         "ion_mount": ({"iron_ingot": 1, "observer": 1}, SMALL_PART)},
    "plasma_repeater_turret": {"plasma_barrel": ({"iron_ingot": 2, "glass": 1, "glowstone_dust": 2}, SMALL_PART),
                               "plasma_chamber": ({"copper_ingot": 2, "glowstone_dust": 2, "blaze_powder": 1}, SPECIAL_PART),
                               "repeater_mount": ({"iron_ingot": 1, "observer": 1}, SMALL_PART)},
    "plasma_cannon_turret": {"plasma_cannon_core": ({"diamond": 1, "glowstone": 1, "blaze_rod": 1}, LARGE_PART),
                             "plasma_cannon_barrel": ({"iron_block": 1, "copper_block": 1}, LARGE_PART),
                             "plasma_cannon_mount": ({"iron_ingot": 4, "copper_ingot": 2, "observer": 1}, LARGE_PART)},
```

    and `"plasma_cannon_turret"` in `LARGE`.
  - Run `python3 tools/fit_item_display.py` and `python3 tools/gen_recipes.py`. Expected: new `models/item/` files for 3 weapons + 9 parts; 9 part + 3 assembler recipes; nothing else changed.
  - Lang parts (en_us / id_id): Ion Barrel / Laras Ion, Ion Capacitor / Kapasitor Ion, Ion Mount / Dudukan Ion, Plasma Barrel / Laras Plasma, Plasma Chamber / Ruang Plasma, Repeater Mount / Dudukan Pelontar, Cannon Core / Inti Meriam, Cannon Barrel / Laras Meriam, Cannon Mount / Dudukan Meriam.

- [ ] **Step 5: Verify.** `./gradlew build --offline`; `./gradlew runGameTestServer --offline` → 128 passed (`craftingTableOnlyMakesWorkstations` and the parts tests still pass).

---

### Task 7: Scenes, docs, final verification

**Files:** Create `src/dev/java/dev/bastion/dev/EnergyShowcase.java`; modify `src/dev/java/dev/bastion/dev/Showcase.java` (exclude "ion" and "plasma"), `docs/PLAN.md`, `docs/TESTING.md`, `docs/SOUND_SHEET.md`, `docs/release/DESCRIPTION.md`, `docs/release/DESCRIPTION.curseforge.html`, `docs/release/UPLOAD.md`, `docs/HANDOFF.md`.

- [ ] **Step 1: Scene.** `EnergyShowcase.java` (pattern of `LaserShowcase`):

```java
package dev.bastion.dev;

import com.mojang.logging.LogUtils;
import dev.bastion.Bastion;
import dev.bastion.registry.BastionBlocks;
import dev.bastion.registry.BastionItems;
import dev.bastion.turret.LargeTurretBaseBlock;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.TurretInventory;
import dev.bastion.turret.TurretTier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Dev scene (src/dev, never shipped): {@code -Pshowcase=ion} = an Ion Rifle firing into a frozen cluster of husks (beams
 * from the core); {@code -Pshowcase=plasma} = a Plasma Repeater and a Plasma Cannon on a Large base firing at a cluster
 * (orbs, splashes, the ring). Side, close and night screenshots, then quits.
 */
@Mod.EventBusSubscriber(modid = Bastion.MOD_ID, value = Dist.CLIENT)
public final class EnergyShowcase {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String SCENE = System.getProperty("bastion.showcase", "");
    private static final boolean ON = SCENE.equals("ion") || SCENE.equals("plasma");
    private static final BlockPos ORIGIN = new BlockPos(0, 150, 0), CANNON = ORIGIN.offset(4, 0, -1);
    private static final List<Husk> TARGETS = new ArrayList<>();
    private static int tick = -1;

    private EnergyShowcase() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (!ON || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        if (mc.player == null || server == null) return;
        mc.options.pauseOnLostFocus = false;
        if (mc.screen instanceof PauseScreen) mc.setScreen(null);
        tick++;
        Vec3 base = Vec3.atBottomCenterOf(ORIGIN);
        String p = SCENE.charAt(0) + "_";
        if (tick == 20) Showcase.server(server, EnergyShowcase::build);
        if (tick == 22) mc.options.hideGui = true;
        if (tick == 24) Showcase.camera(server, base.add(9, 3.2, 4), base.add(1, 1.2, 9));
        if (tick >= 40 && tick < 240 && tick % 4 == 0) Showcase.shot(mc, p + "side_" + (tick - 40) / 4);
        if (tick == 242) Showcase.camera(server, base.add(3, 2.2, 13), base.add(1, 1.2, 10));
        if (tick >= 250 && tick < 350 && tick % 3 == 0) Showcase.shot(mc, p + "close_" + (tick - 250) / 3);
        if (tick == 352) {
            Showcase.server(server, s -> s.overworld().setDayTime(18000));
            Showcase.camera(server, base.add(9, 3.2, 4), base.add(1, 1.2, 9));
        }
        if (tick >= 360 && tick < 460 && tick % 3 == 0) Showcase.shot(mc, p + "night_" + (tick - 360) / 3);
        if (tick == 470) mc.stop();
        if (tick > 20 && tick % 20 == 0) Showcase.server(server, EnergyShowcase::refill);
        if (tick > 40 && tick % 10 == 0) Showcase.server(server, s -> {
            if (s.overworld().getBlockEntity(ORIGIN) instanceof TurretBaseBlockEntity t) LOG.info("[showcase] t{} {} {}", tick, SCENE, t.state());
        });
    }

    private static void build(MinecraftServer server) {
        ServerLevel level = server.overworld();
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        level.setWeatherParameters(6000, 0, false, false);
        level.setDayTime(6000);
        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
        player.setGameMode(GameType.CREATIVE);
        player.getInventory().clearContent();
        for (int x = -10; x <= 12; x++) {
            for (int z = -8; z <= 22; z++) {
                level.setBlock(ORIGIN.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
                for (int y = 0; y < 8; y++) level.setBlock(ORIGIN.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
            }
        }
        for (int x = -3; x <= 5; x++) for (int y = 0; y < 3; y++) level.setBlock(ORIGIN.offset(x, y, 16), Blocks.STONE_BRICKS.defaultBlockState(), 2);
        if (SCENE.equals("ion")) {
            Showcase.place(level, ORIGIN, Direction.UP, BastionItems.ION_RIFLE_TURRET.get(), TurretTier.T1);
        } else {
            Showcase.place(level, ORIGIN, Direction.UP, BastionItems.PLASMA_REPEATER_TURRET.get(), TurretTier.T1);
            LargeTurretBaseBlock.placeAt(level, CANNON, BastionBlocks.LARGE_TURRET_BASE.get());
            TurretBaseBlockEntity cannon = (TurretBaseBlockEntity) level.getBlockEntity(CANNON);
            cannon.inventory().setStackInSlot(TurretInventory.WEAPON, new ItemStack(BastionItems.PLASMA_CANNON_TURRET.get()));
            cannon.inventory().setStackInSlot(TurretInventory.AMMO_START, new ItemStack(BastionItems.CREATIVE_AMMO.get()));
        }
        refill(server);
    }

    /** A frozen, tanky cluster down the lane: one in front, the rest within 1-3 blocks of it (field, splash and ring). */
    private static void refill(MinecraftServer server) {
        ServerLevel level = server.overworld();
        TARGETS.removeIf(h -> !h.isAlive());
        Vec3[] spots = {new Vec3(0.5, 0, 9.5), new Vec3(2.5, 0, 10.5), new Vec3(-1.5, 0, 11), new Vec3(0.5, 0, 12.5), new Vec3(3, 0, 12),
                new Vec3(-2.5, 0, 9)};
        while (TARGETS.size() < spots.length) {
            Husk husk = EntityType.HUSK.create(level);
            if (husk == null) return;
            Vec3 at = Vec3.atBottomCenterOf(ORIGIN).add(spots[TARGETS.size()]);
            husk.moveTo(at.x, at.y, at.z, 180, 0);
            husk.setNoAi(true);
            husk.setPersistenceRequired();
            husk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(4000);
            husk.setHealth(4000);
            level.addFreshEntity(husk);
            TARGETS.add(husk);
        }
    }
}
```

  `Showcase.ON`: add `&& !System.getProperty("bastion.showcase").equals("ion") && !System.getProperty("bastion.showcase").equals("plasma")`.

- [ ] **Step 2: Run the scenes** (keep `caffeinate -dimsu` running; the screen must be unlocked):
  `./gradlew runClient --offline -PnoDevMods -Pshowcase=ion -PshowcaseWorld=showcase_fx` then `-Pshowcase=plasma`. Read `run/screenshots/showcase_i_*.png` and `showcase_p_*.png`. Check: Ion — blue streak with a long trail, the core flash + ring, straight two-layer beams to each nearby husk that fade in ~6 ticks, coil glow pulse on fire; Plasma — Repeater balls visibly flying with a short teal trail, small splashes, barrel glow turning teal then red and vent flaps opening on overheat; Cannon — the teal orb growing at the muzzle while charging (chamber glow teal), the big slow ball, the flat shockwave, 12 small balls flying outward, scorch on the floor; all three at night. Fix what looks wrong (sizes in the presets, `BOLT`/`ORB`/`FRAGMENT` radii, model boxes) and re-run.

- [ ] **Step 3: Docs.**
  - `docs/PLAN.md`: the section's **Status** → "selesai 2026-10-10: 128 GameTest, scene `-Pshowcase=ion` / `plasma` dicek" (with the real count).
  - `docs/TESTING.md`: a new section "Ion Rifle, Plasma Repeater, Plasma Cannon" with the in-game checks: craft the 3 modules (parts → Part Assembler), Empty Plasma Cell → Plasma Cell at the Charging Station; Ion: FE via Creative Power Source or Feed Hub, beams to every mob near the impact, cows/villagers never hit, NO_AMMO without FE; Repeater: splash, overheat + vent (sound + flaps), 10 shots per cell; Cannon: charge orb, ring of 12, fragments stop at walls, never fires within 4 blocks, 1 cell per shot; module tooltips (Shift) list the right turrets (Penetrator not on any of the three, Coolant Loop on the Repeater only).
  - `docs/SOUND_SHEET.md`: the "Direncanakan" section → three real groups; each of the 9 entries gets **P2**, its file path(s) under `ion/` / `plasma/` and its hearing distance (from the sounds.json above); the "jangan jalankan `tools/gen_sounds.py`" step becomes "`tools/gen_sounds.py` now only writes missing files (`--force` rewrites all)". Update the counts in the intro (67 events, 0 planned).
  - `docs/release/DESCRIPTION.md` + `DESCRIPTION.curseforge.html`: add the three turrets to the turret list and Plasma Cells to the ammo list, same tone as the existing entries.
  - `docs/release/UPLOAD.md` (0.1.2 changelog, channel Beta), under New: "Ion Rifle (1x1, FE): a fast ion bolt; where it stops, a beam zaps every target within 3 blocks, through thin walls." / "Plasma Repeater (1x1, Plasma Cells): rapid plasma balls with a small splash; heats up and vents." / "Plasma Cannon (2x2, Plasma Cells): a big slow ball that bursts into a ring of 12." / "Plasma Cell: made at the Ammo Workstation, charged at the Charging Station." Under Changes: "Placeholder sound generator no longer overwrites replaced sounds."
  - `docs/HANDOFF.md`: NOW item 0 → done (what was built, test count, scenes checked, deferred items found).

- [ ] **Step 4: Final verification.** `./gradlew build --offline`; `./gradlew runGameTestServer --offline` (expect "All 128 required tests passed"); `./gradlew runGameTestServer --offline -PwithCompatMods` (same count). `git status` → no change under `run/saves/showcase`, `modlist.html` untracked. Do not commit; give the user the in-game checklist.
