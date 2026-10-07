package dev.bastion.client.vfx;

import dev.bastion.client.render.CasingRenderer;
import dev.bastion.client.render.TracerRenderer;
import dev.bastion.registry.BastionParticles;
import dev.bastion.registry.BastionParticles.Curve;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every effect preset (PLAN 5, 7). Weapon presets take their energy colour from {@link VfxParams#color()};
 * {@link #ALL} names each one for the debug command {@code /bastion vfx <preset>}.
 */
public final class VfxPresets {
    public static final int CYAN = 0xFF4FD8FF, AMBER = 0xFFFFB347, MAGENTA = 0xFFFF4FD8;
    private static final Vec3 UP = new Vec3(0, 1, 0);

    // --- primitives: one per building block, for /bastion vfx ------------------------------------

    public static final VfxPreset SPARK = v -> v.burst(BastionParticles.SPARK.get(), 10, v.params().normal(), 70, 0.25f, 0.8f, 12, Curve.LINEAR, v.params().color());
    public static final VfxPreset EMBER = v -> v.burst(BastionParticles.EMBER.get(), 8, UP, 50, 0.05f, 0.6f, 30, Curve.FLICKER, v.params().color());
    public static final VfxPreset SMOKE_PUFF = v -> v.burst(BastionParticles.SMOKE_PUFF.get(), 6, UP, 40, 0.03f, 3f, 40, Curve.EASE_OUT, 0xB0D8DDE2);
    public static final VfxPreset HEAT_HAZE = v -> v.burst(BastionParticles.HEAT_HAZE.get(), 2, UP, 15, 0.03f, 2.2f, 20, Curve.EASE_OUT, 0x70FFFFFF);
    public static final VfxPreset SHOCKWAVE_RING = v -> v.ring(BastionParticles.SHOCKWAVE_RING.get(), v.origin(), UP, 0, 12f, 12, Curve.EASE_OUT, v.params().color());
    public static final VfxPreset MUZZLE_FLASH = v -> v.single(BastionParticles.MUZZLE_FLASH.get(), v.origin(), 2.2f, 4, Curve.PULSE, v.params().color());
    public static final VfxPreset DEBRIS = v -> v.burst(BastionParticles.DEBRIS.get(), 6, v.params().normal(), 60, 0.22f, 0.5f, 30, Curve.LINEAR, v.params().blockColor());
    public static final VfxPreset MUZZLE_RING = v -> v.ring(BastionParticles.MUZZLE_RING.get(), v.origin(), v.direction(), 0.06f, 2f, 6, Curve.EASE_OUT, v.params().color());
    public static final VfxPreset IMPACT_SPLASH = v -> v.ring(BastionParticles.IMPACT_SPLASH.get(), v.origin(), v.params().normal(), 0, 1.6f, 6, Curve.EASE_OUT, v.params().color());
    public static final VfxPreset SMOKE_WISP = v -> v.burst(BastionParticles.SMOKE_WISP.get(), 2, UP, 10, 0.02f, 1.4f, 30, Curve.EASE_OUT, 0x90E6EAF0);
    public static final VfxPreset LIGHT = v -> v.light(12, 20);
    public static final VfxPreset SHAKE = v -> v.shake(0.7f, 16);
    public static final VfxPreset FLASH = v -> v.flash(0.7f, 16);
    public static final VfxPreset DECAL = v -> v.decal(0.2f, 80);
    public static final VfxPreset TRACER = v -> TracerRenderer.spawn(v.origin(), v.origin().add(v.direction().scale(24)),
            v.params().color(), 0.07f, 3, () -> play(VfxPresets.GUN_IMPACT, v.origin().add(v.direction().scale(24)), v.direction().reverse(), v.params().normal(v.direction().reverse())));
    public static final VfxPreset CASING = v -> CasingRenderer.eject(BlockPos.containing(v.origin()), v.origin(),
            v.cone(new Vec3(1, 0.8, 0), 20).scale(0.12), v.params().color(), false, v.params().seed());

    // --- Gun Turret, cyan (PLAN 7.1) ------------------------------------------------------------

    public static final VfxPreset GUN_MUZZLE = v -> v
            .single(BastionParticles.MUZZLE_FLASH.get(), v.origin(), 2.2f, 3, Curve.PULSE, v.params().color())
            .ring(BastionParticles.MUZZLE_RING.get(), v.origin(), v.direction(), 0.05f, 1.6f, 5, Curve.EASE_OUT, v.params().color())
            .burst(BastionParticles.SPARK.get(), 3, v.direction(), 14, 0.3f, 0.5f, 5, Curve.LINEAR, v.params().color())
            .light(10, 2);

    public static final VfxPreset GUN_IMPACT = v -> v
            .burst(BastionParticles.SPARK.get(), 8, v.params().normal(), 60, 0.22f, 0.7f, 10, Curve.LINEAR, v.params().color())
            .ring(BastionParticles.IMPACT_SPLASH.get(), v.origin(), v.params().normal(), 0, 1.2f, 6, Curve.EASE_OUT, v.params().color())
            .burst(BastionParticles.EMBER.get(), 2, v.params().normal(), 40, 0.06f, 0.5f, 18, Curve.FLICKER, v.params().color())
            .decal(0.16f, 60);

    /** Precision Lock hit: bigger splash plus a ring at the impact (PLAN 7.1). */
    public static final VfxPreset GUN_LOCK_IMPACT = v -> {
        GUN_IMPACT.play(v);
        v.ring(BastionParticles.MUZZLE_RING.get(), v.origin(), v.params().normal(), 0.02f, 2.6f, 8, Curve.EASE_OUT, v.params().color())
                .burst(BastionParticles.SPARK.get(), 10, v.params().normal(), 75, 0.3f, 0.8f, 12, Curve.LINEAR, v.params().color())
                .light(8, 3);
    };

    // --- Machine Gun Turret, amber (PLAN 7.2) ---------------------------------------------------

    public static final VfxPreset MG_MUZZLE = v -> v
            .single(BastionParticles.MUZZLE_FLASH.get(), v.origin(), 1.6f, 2, Curve.PULSE, v.params().color())
            .ring(BastionParticles.MUZZLE_RING.get(), v.origin(), v.direction(), 0.04f, 1.0f, 3, Curve.EASE_OUT, v.params().color());

    public static final VfxPreset MG_IMPACT = v -> v
            .burst(BastionParticles.SPARK.get(), 3, v.params().normal(), 60, 0.18f, 0.5f, 8, Curve.LINEAR, v.params().color())
            .burst(BastionParticles.DEBRIS.get(), 2, v.params().normal(), 55, 0.16f, 0.4f, 25, Curve.LINEAR, v.params().blockColor())
            .decal(0.1f, 40);

    // --- Shotgun Turret, magenta (PLAN 7.3) -----------------------------------------------------

    public static final VfxPreset SG_MUZZLE = v -> v
            .single(BastionParticles.MUZZLE_FLASH.get(), v.origin(), 3.4f, 3, Curve.PULSE, v.params().color())
            .ring(BastionParticles.MUZZLE_RING.get(), v.origin(), v.direction(), 0.12f, 3.6f, 7, Curve.EASE_OUT, v.params().color())
            .burst(BastionParticles.SMOKE_WISP.get(), 3, v.direction(), 25, 0.04f, 1.2f, 25, Curve.EASE_OUT, 0x90E6EAF0)
            .light(13, 3)
            .shake(0.45f, 8);

    public static final VfxPreset SG_PELLET_IMPACT = v -> v
            .burst(BastionParticles.SPARK.get(), 2, v.params().normal(), 60, 0.16f, 0.45f, 7, Curve.LINEAR, v.params().color())
            .decal(0.08f, 40);

    /** A target hit by many pellets gets one big splash (PLAN 7.3). */
    public static final VfxPreset SG_HEAVY_IMPACT = v -> v
            .ring(BastionParticles.IMPACT_SPLASH.get(), v.origin(), v.params().normal(), 0, 2.4f, 7, Curve.EASE_OUT, v.params().color())
            .burst(BastionParticles.SPARK.get(), 6, v.params().normal(), 70, 0.25f, 0.7f, 10, Curve.LINEAR, v.params().color());

    // --- Sniper Turret, green ------------------------------------------------------------------

    public static final VfxPreset SNIPER_MUZZLE = v -> v
            .single(BastionParticles.MUZZLE_FLASH.get(), v.origin(), 3.0f, 3, Curve.PULSE, v.params().color())
            .ring(BastionParticles.MUZZLE_RING.get(), v.origin(), v.direction(), 0.1f, 2.6f, 7, Curve.EASE_OUT, v.params().color())
            .burst(BastionParticles.SPARK.get(), 5, v.direction(), 12, 0.45f, 0.6f, 6, Curve.LINEAR, v.params().color())
            .burst(BastionParticles.SMOKE_WISP.get(), 2, v.direction(), 20, 0.05f, 1.2f, 30, Curve.EASE_OUT, 0x90E6EAF0)
            .light(13, 3)
            .shake(0.25f, 10);

    public static final VfxPreset SNIPER_IMPACT = v -> v
            .burst(BastionParticles.SPARK.get(), 14, v.params().normal(), 70, 0.3f, 0.8f, 12, Curve.LINEAR, v.params().color())
            .ring(BastionParticles.IMPACT_SPLASH.get(), v.origin(), v.params().normal(), 0, 1.8f, 7, Curve.EASE_OUT, v.params().color())
            .ring(BastionParticles.MUZZLE_RING.get(), v.origin(), v.params().normal(), 0.03f, 2.2f, 8, Curve.EASE_OUT, v.params().color())
            .burst(BastionParticles.DEBRIS.get(), 4, v.params().normal(), 60, 0.2f, 0.5f, 30, Curve.LINEAR, v.params().blockColor())
            .burst(BastionParticles.EMBER.get(), 3, v.params().normal(), 40, 0.08f, 0.6f, 20, Curve.FLICKER, v.params().color())
            .light(9, 4)
            .decal(0.22f, 100);

    // --- Rocket Launcher Turret, orange ---------------------------------------------------------

    /** Launch: flash out of the tube, backblast smoke behind the pod. */
    public static final VfxPreset ROCKET_LAUNCH = v -> v
            .single(BastionParticles.MUZZLE_FLASH.get(), v.origin(), 2.6f, 3, Curve.PULSE, v.params().color())
            .burst(BastionParticles.SMOKE_PUFF.get(), 6, v.direction(), 30, 0.06f, 1.8f, 35, Curve.EASE_OUT, 0xB0B8BCC2)
            .burst(BastionParticles.SMOKE_PUFF.get(), 8, v.direction().reverse(), 35, 0.12f, 2.2f, 40, Curve.EASE_OUT, 0xA0A8ADB4)
            .burst(BastionParticles.SPARK.get(), 4, v.direction(), 20, 0.3f, 0.5f, 6, Curve.LINEAR, v.params().color())
            .light(13, 4)
            .shake(0.3f, 10);

    /** Every tick behind a flying rocket: a flame tongue, puffs of smoke around the SmokeTrailRenderer ribbon, the odd ember. */
    public static final VfxPreset ROCKET_TRAIL = v -> v
            .burst(BastionParticles.FLAME.get(), 3, v.direction(), 10, 0.12f, 1.7f, 5, Curve.LINEAR, v.params().color())
            .burst(BastionParticles.BLAST_SMOKE.get(), 2, v.direction(), 25, 0.015f, 2.1f, 50, Curve.EASE_OUT, 0xB0A6AAB0)
            .burst(BastionParticles.EMBER.get(), 1, v.direction(), 30, 0.05f, 0.5f, 14, Curve.FLICKER, v.params().color());

    /**
     * Rocket impact (PLAN 1, no vanilla explosion), in layers: a blinding flash and shockwave, a rolling cluster of
     * fireballs with a rising core, flame tongues, sparks and block-coloured debris thrown out, then black smoke that
     * billows up and lingers, with embers drifting through it. Scale = blast radius / 3.5.
     */
    public static final VfxPreset ROCKET_EXPLOSION = v -> {
        float s = v.params().scale();
        int fire = v.params().color();
        // Smoke first: SOFT particles draw in creation order, so the fireballs spawned after it sit in front.
        v.burst(BastionParticles.BLAST_SMOKE.get(), 14, UP, 80, 0.09f * s, 8f * s, 100, Curve.BILLOW, 0xE0303236)
                .burst(BastionParticles.BLAST_SMOKE.get(), 5, UP, 25, 0.16f * s, 9f * s, 115, Curve.BILLOW, 0xD0404246)
                .single(BastionParticles.MUZZLE_FLASH.get(), v.origin(), 15f * s, 4, Curve.PULSE, 0xFFFFF2D0)
                .ring(BastionParticles.SHOCKWAVE_RING.get(), v.origin(), UP, 0, 17f * s, 10, Curve.EASE_OUT, 0xFFFFE0B0)
                .burst(BastionParticles.FIREBALL.get(), 16, UP, 180, 0.14f * s, 8.5f * s, 22, Curve.BILLOW, fire)
                .burst(BastionParticles.FIREBALL.get(), 6, UP, 30, 0.2f * s, 7f * s, 30, Curve.BILLOW, fire)
                .burst(BastionParticles.FLAME.get(), 14, UP, 160, 0.6f * s, 2.4f, 10, Curve.LINEAR, fire)
                .burst(BastionParticles.SPARK.get(), 26, UP, 170, 0.75f, 1.3f, 16, Curve.LINEAR, fire)
                .burst(BastionParticles.DEBRIS.get(), 14, UP, 120, 0.45f, 1.0f, 40, Curve.LINEAR, v.params().blockColor())
                .burst(BastionParticles.EMBER.get(), 18, UP, 100, 0.16f, 0.9f, 55, Curve.FLICKER, fire)
                .light(15, 10)
                .shake(0.75f, 18)
                .flash(0.35f, 10);
    };

    /** Missile launch: a short flash out of the tube and a puff of backblast. Twelve of these can fire within a second. */
    public static final VfxPreset MISSILE_LAUNCH = v -> v
            .single(BastionParticles.MUZZLE_FLASH.get(), v.origin(), 1.3f, 2, Curve.PULSE, v.params().color())
            .burst(BastionParticles.BLAST_SMOKE.get(), 2, v.direction().reverse(), 40, 0.08f, 1.4f, 30, Curve.BILLOW, 0xB0909498)
            .light(10, 2);

    /** Every tick behind a flying missile: a small flame and a wisp around the ribbon (lighter than a rocket's, salvos are twelve). */
    public static final VfxPreset MISSILE_TRAIL = v -> v
            .burst(BastionParticles.FLAME.get(), 2, v.direction(), 8, 0.1f, 1.2f, 4, Curve.LINEAR, v.params().color())
            .burst(BastionParticles.SMOKE_WISP.get(), 1, v.direction(), 15, 0.01f, 1.1f, 22, Curve.EASE_OUT, 0xA0B4B8BE);

    /** Missile impact: the rocket blast in miniature, fewer particles so a full salvo stays inside the budget. */
    public static final VfxPreset MISSILE_EXPLOSION = v -> {
        float s = v.params().scale();
        int fire = v.params().color();
        v.burst(BastionParticles.BLAST_SMOKE.get(), 5, UP, 80, 0.08f * s, 7f * s, 70, Curve.BILLOW, 0xE0303236)
                .single(BastionParticles.MUZZLE_FLASH.get(), v.origin(), 12f * s, 4, Curve.PULSE, 0xFFFFF2D0)
                .ring(BastionParticles.SHOCKWAVE_RING.get(), v.origin(), UP, 0, 13f * s, 8, Curve.EASE_OUT, 0xFFFFE0B0)
                .burst(BastionParticles.FIREBALL.get(), 6, UP, 180, 0.12f * s, 7f * s, 18, Curve.BILLOW, fire)
                .burst(BastionParticles.SPARK.get(), 8, UP, 170, 0.6f, 1.0f, 12, Curve.LINEAR, fire)
                .burst(BastionParticles.DEBRIS.get(), 4, UP, 120, 0.35f, 0.8f, 30, Curve.LINEAR, v.params().blockColor())
                .burst(BastionParticles.EMBER.get(), 5, UP, 100, 0.14f, 0.8f, 40, Curve.FLICKER, fire)
                .light(14, 6)
                .shake(0.35f, 12);
    };

    /** Where the blast meets a surface: a dust ring sweeping out across the ground. */
    public static final VfxPreset ROCKET_DUST = v -> v
            .ring(BastionParticles.DUST_RING.get(), v.origin(), v.params().normal(), 0, 19f * v.params().scale(), 28, Curve.EASE_OUT,
                    0xB0000000 | (v.params().blockColor() & 0xFFFFFF))
            .burst(BastionParticles.BLAST_SMOKE.get(), 6, v.params().normal(), 80, 0.12f, 2.6f, 50, Curve.EASE_OUT,
                    0xA0000000 | (v.params().blockColor() & 0xFFFFFF));

    /** The burn left on the struck face: glows in the blast colour, cools to black, fades after ~20 s. */
    public static final VfxPreset ROCKET_SCORCH = v -> v.scorch(1.5f * v.params().scale(), 400);

    // --- Tesla Coil, electric blue (bolts: ArcRenderer, TeslaEffects) -------------------------------

    /** Discharge at the terminal: a white-hot flash in a coloured halo, a crackling ring, sparks thrown off, a hard light. */
    public static final VfxPreset TESLA_DISCHARGE = v -> v
            .single(BastionParticles.MUZZLE_FLASH.get(), v.origin(), 7f, 4, Curve.PULSE, v.params().color())
            .single(BastionParticles.MUZZLE_FLASH.get(), v.origin(), 3.6f, 3, Curve.PULSE, 0xFFF0F4FF)
            .ring(BastionParticles.SHOCKWAVE_RING.get(), v.origin(), UP, 0, 7f, 7, Curve.EASE_OUT, v.params().color())
            .burst(BastionParticles.SPARK.get(), 12, UP, 120, 0.35f, 0.6f, 8, Curve.LINEAR, v.params().color())
            .light(15, 3)
            .shake(0.2f, 10);

    /** Where a bolt strikes: a flash, a spray of sparks, a splash ring and a few embers. */
    public static final VfxPreset TESLA_IMPACT = v -> v
            .single(BastionParticles.MUZZLE_FLASH.get(), v.origin(), 3.2f, 3, Curve.PULSE, v.params().color())
            .burst(BastionParticles.SPARK.get(), 12, v.params().normal(), 150, 0.3f, 0.55f, 9, Curve.LINEAR, v.params().color())
            .ring(BastionParticles.IMPACT_SPLASH.get(), v.origin(), v.params().normal(), 0, 1.6f, 6, Curve.EASE_OUT, v.params().color())
            .burst(BastionParticles.EMBER.get(), 3, UP, 80, 0.06f, 0.5f, 16, Curve.FLICKER, v.params().color())
            .light(12, 4);

    /** A small arc leaving the crown: a couple of sparks where it jumps off. */
    public static final VfxPreset TESLA_SPARKLE = v -> v
            .burst(BastionParticles.SPARK.get(), 2, v.direction(), 60, 0.12f, 0.35f, 6, Curve.LINEAR, v.params().color());

    // --- Laser Rifle, red (beam: BeamRenderer, charge orb: LaserChargeRenderer) --------------------------

    /** Each tick of the charge: sparks of light streaming into the emitter (scale grows with the charge). */
    public static final VfxPreset LASER_GATHER = v -> v
            .converge(BastionParticles.EMBER.get(), 3, 1.0f, 7, 0.9f, Curve.EASE_OUT, v.params().color());

    /** The shot leaving the emitter: a white-hot flash in a red one, a ring along the beam, sparks, a hard light, a kick. */
    public static final VfxPreset LASER_MUZZLE = v -> v
            .single(BastionParticles.MUZZLE_FLASH.get(), v.origin(), 4.2f, 4, Curve.PULSE, v.params().color())
            .single(BastionParticles.MUZZLE_FLASH.get(), v.origin(), 2.2f, 3, Curve.PULSE, 0xFFFFF0F2)
            .ring(BastionParticles.MUZZLE_RING.get(), v.origin(), v.direction(), 0.1f, 3f, 7, Curve.EASE_OUT, v.params().color())
            .burst(BastionParticles.SPARK.get(), 6, v.direction(), 25, 0.4f, 0.6f, 6, Curve.LINEAR, v.params().color())
            .light(15, 3)
            .shake(0.3f, 10);

    /** Each target the beam passes through: a burst of sparks and a splash ring. */
    public static final VfxPreset LASER_IMPACT = v -> v
            .burst(BastionParticles.SPARK.get(), 10, v.params().normal(), 80, 0.3f, 0.6f, 9, Curve.LINEAR, v.params().color())
            .ring(BastionParticles.IMPACT_SPLASH.get(), v.origin(), v.params().normal(), 0, 1.6f, 6, Curve.EASE_OUT, v.params().color())
            .burst(BastionParticles.EMBER.get(), 2, v.params().normal(), 50, 0.06f, 0.5f, 16, Curve.FLICKER, v.params().color())
            .light(10, 3);

    /** Where the beam stops on a block: sparks, debris, a splash and a burn that glows red and cools. */
    public static final VfxPreset LASER_END = v -> v
            .burst(BastionParticles.SPARK.get(), 14, v.params().normal(), 75, 0.32f, 0.7f, 11, Curve.LINEAR, v.params().color())
            .burst(BastionParticles.DEBRIS.get(), 4, v.params().normal(), 60, 0.2f, 0.5f, 30, Curve.LINEAR, v.params().blockColor())
            .ring(BastionParticles.IMPACT_SPLASH.get(), v.origin(), v.params().normal(), 0, 2f, 7, Curve.EASE_OUT, v.params().color())
            .burst(BastionParticles.SMOKE_WISP.get(), 2, v.params().normal(), 20, 0.03f, 1.2f, 30, Curve.EASE_OUT, 0x90E6EAF0)
            .light(12, 4)
            .decal(0.28f, 140);

    /** Along the beam's path after the shot: a thin wisp of smoke. */
    public static final VfxPreset LASER_TRAIL = v -> v
            .burst(BastionParticles.SMOKE_WISP.get(), 1, UP, 10, 0.01f, 1.0f, 28, Curve.EASE_OUT, 0x70E6EAF0);

    // --- Flamethrower, fire orange (FlameEffects) -------------------------------------------------

    /**
     * One pulse of the flame stream (every 2 ticks), scale = range / 8: soot rolling off the far end first (SOFT particles
     * draw in creation order, so the fire sits in front of it), billowing flame puffs that grow as they fly and pile up
     * against walls, a hot root at the nozzle and the odd ember (FlameJetRenderer draws the solid jet itself). Particles
     * start spread along their paths, so the pulses join into one stream.
     */
    public static final VfxPreset FLAME_STREAM = v -> {
        float s = v.params().scale();
        int fire = v.params().color();
        v.jet(BastionParticles.BLAST_SMOKE.get(), 1, v.direction(), 16, 0.45f * s, 4.5f, 40, Curve.EASE_OUT, 0x80303236, 10f)
                .jet(BastionParticles.FLAME_JET.get(), 5, v.direction(), 6, 0.85f * s, 9f, 22, Curve.FLARE, fire, 2f)
                .single(BastionParticles.MUZZLE_FLASH.get(), v.origin(), 2f, 2, Curve.PULSE, 0xFFFFD28A)
                .burst(BastionParticles.EMBER.get(), 1, v.direction(), 18, 0.5f * s, 0.45f, 18, Curve.FLICKER, fire)
                .light(14, 3);
    };

    /** The stream meeting a wall or the floor: flames splash out across the surface, embers fly off. */
    public static final VfxPreset FLAME_SPLASH = v -> v
            .burst(BastionParticles.FLAME_JET.get(), 2, v.params().normal(), 80, 0.14f, 6f, 16, Curve.BILLOW, v.params().color())
            .burst(BastionParticles.EMBER.get(), 1, v.params().normal(), 60, 0.12f, 0.5f, 20, Curve.FLICKER, v.params().color());

    /**
     * Where the stream keeps hitting: one scorch per spot that glows while the fire is on it and widens the longer it
     * burns (about 9 s to full size), then cools and fades ~15 s after the fire moves on. Never stacks.
     */
    public static final VfxPreset FLAME_SCORCH = v -> v.burnMark(0.5f, 300, 0.01f, 1.4f);

    /** Every tick on a burning target, at a random point on its body: a licking flame and a fast tongue. */
    public static final VfxPreset BURNING = v -> v
            .burst(BastionParticles.FLAME_JET.get(), 1, UP, 20, 0.05f, 3.4f, 12, Curve.BILLOW, v.params().color())
            .burst(BastionParticles.FLAME.get(), 1, UP, 15, 0.09f, 1.5f, 6, Curve.LINEAR, 0xFFFFB050);

    /** The nozzle's blue pilot light while armed but not firing. */
    public static final VfxPreset PILOT_LIGHT = v -> v
            .burst(BastionParticles.EMBER.get(), 1, v.direction(), 10, 0.012f, 0.5f, 6, Curve.FLICKER, 0xFF7FB2FF);

    // --- workstations (WorkstationEffects) ---------------------------------------------------------

    /** Machining / printing: a few hot sparks flicking off the tool. */
    public static final VfxPreset WORKSTATION_SPARKS = v -> v
            .burst(BastionParticles.SPARK.get(), 3, UP, 70, 0.14f, 0.35f, 7, Curve.LINEAR, v.params().color());

    /** Welding arc on the assembler: a white-blue flash and a spray of sparks. */
    public static final VfxPreset WORKSTATION_WELD = v -> v
            .single(BastionParticles.MUZZLE_FLASH.get(), v.origin(), 0.9f, 2, Curve.PULSE, v.params().color())
            .burst(BastionParticles.SPARK.get(), 5, UP, 110, 0.18f, 0.35f, 9, Curve.LINEAR, 0xFFFFE0A0);

    /** The ammo press bottoming out: a puff of smoke and a few sparks. */
    public static final VfxPreset WORKSTATION_PUFF = v -> v
            .burst(BastionParticles.SMOKE_PUFF.get(), 3, UP, 50, 0.04f, 1.6f, 25, Curve.EASE_OUT, 0x90D8DDE2)
            .burst(BastionParticles.SPARK.get(), 3, UP, 80, 0.12f, 0.35f, 6, Curve.LINEAR, v.params().color());

    /** A craft finished: a ring of light rises off the work and a few embers. */
    public static final VfxPreset WORKSTATION_DONE = v -> v
            .ring(BastionParticles.MUZZLE_RING.get(), v.origin(), UP, 0.04f, 2.2f, 10, Curve.EASE_OUT, v.params().color())
            .burst(BastionParticles.EMBER.get(), 6, UP, 60, 0.06f, 0.5f, 20, Curve.FLICKER, v.params().color())
            .light(10, 6);

    // --- Railgun, violet (channel glow and ion trail: RailRenderer, arcs over the rails: RailgunEffects) --------------

    /** The slug leaving the rails: a blinding flash in a violet one, sparks down the line of fire, smoke, a hard light, a kick. */
    public static final VfxPreset RAIL_MUZZLE = v -> v
            .single(BastionParticles.MUZZLE_FLASH.get(), v.origin(), 6.5f, 4, Curve.PULSE, v.params().color())
            .single(BastionParticles.MUZZLE_FLASH.get(), v.origin(), 3.4f, 3, Curve.PULSE, 0xFFF6F0FF)
            .burst(BastionParticles.SPARK.get(), 14, v.direction(), 18, 0.7f, 0.7f, 7, Curve.LINEAR, v.params().color())
            .burst(BastionParticles.SMOKE_PUFF.get(), 5, v.direction(), 35, 0.08f, 2.2f, 40, Curve.EASE_OUT, 0xA0C8C4D0)
            .light(15, 4)
            .shake(0.6f, 24)
            .flash(0.15f, 8);

    /** One of the rings the shot punches through the air ahead of the muzzle (scale grows ring by ring). */
    public static final VfxPreset RAIL_RING = v -> v
            .ring(BastionParticles.MUZZLE_RING.get(), v.origin(), v.direction(), 0.04f, 2.6f * v.params().scale(), 9, Curve.EASE_OUT, v.params().color());

    /** Where the slug lands, on a mob or a block: a flash, a violet shockwave, sparks, debris, smoke and embers. */
    public static final VfxPreset RAIL_IMPACT = v -> v
            .single(BastionParticles.MUZZLE_FLASH.get(), v.origin(), 8f, 4, Curve.PULSE, 0xFFF6F0FF)
            .ring(BastionParticles.SHOCKWAVE_RING.get(), v.origin(), UP, 0, 19f, 12, Curve.EASE_OUT, v.params().color())
            .burst(BastionParticles.SPARK.get(), 24, v.params().normal(), 110, 0.7f, 1.1f, 14, Curve.LINEAR, v.params().color())
            .burst(BastionParticles.DEBRIS.get(), 10, UP, 100, 0.45f, 0.9f, 40, Curve.LINEAR, v.params().blockColor())
            .burst(BastionParticles.BLAST_SMOKE.get(), 6, UP, 80, 0.1f, 3.5f, 60, Curve.BILLOW, 0xC0505058)
            .burst(BastionParticles.EMBER.get(), 10, UP, 90, 0.14f, 0.8f, 40, Curve.FLICKER, v.params().color())
            .light(15, 6)
            .shake(0.8f, 24)
            .flash(0.25f, 12);

    /** On a block only: dust sweeping out across the surface and a scorch that glows violet and cools. */
    public static final VfxPreset RAIL_GROUND = v -> v
            .ring(BastionParticles.DUST_RING.get(), v.origin(), v.params().normal(), 0, 16f, 26, Curve.EASE_OUT,
                    0xB0000000 | (v.params().blockColor() & 0xFFFFFF))
            .scorch(1.4f, 300);

    // --- Mortar, fire orange (shell trail: RocketEffects, fire patch: MortarEffects) -----------------------------

    /** The shell leaving the barrel: a flash, a pale smoke ring blown up the line of fire, a plume of smoke, sparks. */
    public static final VfxPreset MORTAR_LAUNCH = v -> v
            .single(BastionParticles.MUZZLE_FLASH.get(), v.origin(), 4.5f, 3, Curve.PULSE, v.params().color())
            .ring(BastionParticles.MUZZLE_RING.get(), v.origin(), v.direction(), 0.12f, 3.2f, 14, Curve.EASE_OUT, 0xC0D8D8D8)
            .burst(BastionParticles.BLAST_SMOKE.get(), 8, v.direction(), 30, 0.12f, 3.0f, 50, Curve.BILLOW, 0xB0A0A4AA)
            .burst(BastionParticles.SPARK.get(), 6, v.direction(), 25, 0.5f, 0.6f, 8, Curve.LINEAR, v.params().color())
            .light(14, 3)
            .shake(0.45f, 16);

    /** Every tick behind a shell in flight: its glowing fuse and a wisp of smoke. */
    public static final VfxPreset MORTAR_TRAIL = v -> v
            .burst(BastionParticles.FLAME.get(), 1, v.direction(), 10, 0.06f, 0.9f, 4, Curve.LINEAR, v.params().color())
            .burst(BastionParticles.SMOKE_WISP.get(), 1, v.direction(), 15, 0.01f, 0.9f, 18, Curve.EASE_OUT, 0xA0B4B8BE);

    /** A flame licking up at a point of a fire patch (a few per tick across it). */
    public static final VfxPreset FIRE_PATCH = v -> v
            .burst(BastionParticles.FLAME_JET.get(), 1, UP, 25, 0.05f, 3.0f, 14, Curve.BILLOW, v.params().color())
            .burst(BastionParticles.FLAME.get(), 1, UP, 15, 0.08f, 1.3f, 6, Curve.LINEAR, 0xFFFFB050);

    /** Now and then over a fire patch: smoke and an ember rising off it. */
    public static final VfxPreset FIRE_PATCH_SMOKE = v -> v
            .burst(BastionParticles.BLAST_SMOKE.get(), 1, UP, 20, 0.03f, 2.4f, 45, Curve.BILLOW, 0x90303236)
            .burst(BastionParticles.EMBER.get(), 1, UP, 40, 0.07f, 0.5f, 25, Curve.FLICKER, v.params().color());

    /** The patch's burn on the ground: glows while it burns, then cools and fades. */
    public static final VfxPreset MORTAR_SCORCH = v -> v.scorch(1.6f * v.params().scale(), 500);

    // --- shared turret states -------------------------------------------------------------------

    /** Overheat vent: steam and heat haze (PLAN 4.4). */
    public static final VfxPreset OVERHEAT_STEAM = v -> v
            .burst(BastionParticles.SMOKE_PUFF.get(), 6, UP, 35, 0.06f, 1.8f, 30, Curve.EASE_OUT, 0xA0E8ECF0)
            .burst(BastionParticles.HEAT_HAZE.get(), 2, UP, 20, 0.04f, 2f, 20, Curve.EASE_OUT, 0x60FFFFFF);

    /** 0 HP: custom blast, no vanilla explosion (PLAN 4.1, 1). */
    public static final VfxPreset TURRET_DESTROYED = v -> v
            .single(BastionParticles.MUZZLE_FLASH.get(), v.origin(), 14f, 6, Curve.PULSE, 0xFFFFD9A0)
            .ring(BastionParticles.SHOCKWAVE_RING.get(), v.origin(), UP, 0, 18f, 16, Curve.EASE_OUT, v.params().color())
            .ring(BastionParticles.MUZZLE_RING.get(), v.origin(), UP, 0.05f, 8f, 8, Curve.EASE_OUT, 0xFFFFFFFF)
            .burst(BastionParticles.SPARK.get(), 32, UP, 160, 0.6f, 1.6f, 20, Curve.LINEAR, v.params().color())
            .burst(BastionParticles.DEBRIS.get(), 18, UP, 120, 0.4f, 1.2f, 40, Curve.LINEAR, 0xFF5A6068)
            .burst(BastionParticles.SMOKE_PUFF.get(), 14, UP, 90, 0.1f, 6f, 70, Curve.EASE_OUT, 0xB0707478)
            .burst(BastionParticles.EMBER.get(), 16, UP, 80, 0.15f, 1f, 45, Curve.FLICKER, v.params().color())
            .light(15, 10)
            .shake(0.9f, 18)
            .flash(0.5f, 10);

    /** Upgrade kit applied: a cyan ring climbs the base (PLAN 4.1). */
    public static final VfxPreset TIER_UP = v -> v
            .ring(BastionParticles.MUZZLE_RING.get(), v.origin().add(0, -0.4, 0), UP, 0.08f, 4f, 18, Curve.EASE_OUT, v.params().color())
            .ring(BastionParticles.SHOCKWAVE_RING.get(), v.origin().add(0, -0.55, 0), UP, 0, 6f, 16, Curve.EASE_OUT, v.params().color())
            .burst(BastionParticles.EMBER.get(), 20, UP, 40, 0.12f, 0.8f, 30, Curve.FLICKER, v.params().color())
            .light(11, 10);

    /** Below 30% HP: an occasional short-circuit spark (PLAN 4.1). */
    public static final VfxPreset DAMAGED_SPARK = v -> v
            .burst(BastionParticles.SPARK.get(), 4, v.direction(), 70, 0.2f, 0.45f, 8, Curve.LINEAR, 0xFFFFC870)
            .burst(BastionParticles.SMOKE_WISP.get(), 1, UP, 10, 0.02f, 1.1f, 30, Curve.EASE_OUT, 0x90404448);

    // --- Repulsor and Repulsor Dome, white-blue (rings and the dome sphere: RepulsorEffects) ---------------------

    /** The emitter's pop: a pale flash, a light, a small kick. */
    public static final VfxPreset REPULSOR_MUZZLE = v -> v
            .single(BastionParticles.MUZZLE_FLASH.get(), v.origin(), 2.6f, 3, Curve.PULSE, v.params().color())
            .light(11, 4)
            .shake(0.15f, 6);

    /** One ring of the wave (scale grows ring by ring as it travels). */
    public static final VfxPreset REPULSOR_RING = v -> v
            .ring(BastionParticles.SHOCKWAVE_RING.get(), v.origin(), v.direction(), 0, 3.4f * v.params().scale(), 8, Curve.EASE_OUT, v.params().color());

    /** The air rippling along the wave. */
    public static final VfxPreset REPULSOR_HAZE = v -> v
            .burst(BastionParticles.HEAT_HAZE.get(), 3, v.direction(), 25, 0.12f, 2.4f, 10, Curve.EASE_OUT, 0x90FFFFFF);

    /** A pushed mob: dust kicked up at its feet and a few pale streaks. */
    public static final VfxPreset REPULSOR_HIT = v -> v
            .ring(BastionParticles.DUST_RING.get(), v.origin(), UP, 0, 5f, 16, Curve.EASE_OUT, 0xB0000000 | (v.params().blockColor() & 0xFFFFFF))
            .burst(BastionParticles.SPARK.get(), 6, v.direction(), 30, 0.35f, 0.6f, 6, Curve.LINEAR, 0xFFF4FBFF);

    /** The dome bursting: a white flash in the middle, speed lines thrown out all round, a light, a hard shake, a flash. */
    public static final VfxPreset DOME_BURST = v -> v
            .single(BastionParticles.MUZZLE_FLASH.get(), v.origin(), 7f, 4, Curve.PULSE, 0xFFFFFFFF)
            .burst(BastionParticles.SPARK.get(), 60, new Vec3(0, 0.2, 0), 180, 1.1f, 2.6f, 9, Curve.LINEAR, v.params().color())
            .light(15, 8)
            .shake(0.9f, 10) // PLAN: players within ~10 blocks
            .flash(0.22f, 10);

    /** The dust ring rolling out on the ground at the dome's edge (scale = the dome's radius / 6). */
    public static final VfxPreset DOME_DUST = v -> v
            .ring(BastionParticles.DUST_RING.get(), v.origin(), UP, 0, 25f * v.params().scale(), 30, Curve.EASE_OUT, dust(v.params().blockColor(), 0xE0))
            .burst(BastionParticles.SMOKE_PUFF.get(), 24, new Vec3(0, 0.15, 0), 175, 0.5f, 4f, 45, Curve.EASE_OUT, dust(v.params().blockColor(), 0xC0));

    /** Dust kicked up off a block: its colour mixed with a darker sand, so it shows on any floor. */
    private static int dust(int blockColor, int alpha) {
        int r = blockColor >> 16 & 255, g = blockColor >> 8 & 255, b = blockColor & 255;
        return alpha << 24 | (r + 0x8C) / 2 << 16 | (g + 0x76) / 2 << 8 | (b + 0x58) / 2;
    }

    public static final Map<String, VfxPreset> ALL = new LinkedHashMap<>();

    static {
        ALL.put("spark", SPARK);
        ALL.put("ember", EMBER);
        ALL.put("smoke_puff", SMOKE_PUFF);
        ALL.put("heat_haze", HEAT_HAZE);
        ALL.put("shockwave_ring", SHOCKWAVE_RING);
        ALL.put("muzzle_flash", MUZZLE_FLASH);
        ALL.put("debris", DEBRIS);
        ALL.put("muzzle_ring", MUZZLE_RING);
        ALL.put("impact_splash", IMPACT_SPLASH);
        ALL.put("smoke_wisp", SMOKE_WISP);
        ALL.put("light", LIGHT);
        ALL.put("shake", SHAKE);
        ALL.put("flash", FLASH);
        ALL.put("decal", DECAL);
        ALL.put("tracer", TRACER);
        ALL.put("casing", CASING);
        ALL.put("gun_muzzle", GUN_MUZZLE);
        ALL.put("gun_impact", GUN_IMPACT);
        ALL.put("gun_lock_impact", GUN_LOCK_IMPACT);
        ALL.put("mg_muzzle", MG_MUZZLE);
        ALL.put("mg_impact", MG_IMPACT);
        ALL.put("sg_muzzle", SG_MUZZLE);
        ALL.put("sg_pellet_impact", SG_PELLET_IMPACT);
        ALL.put("sg_heavy_impact", SG_HEAVY_IMPACT);
        ALL.put("overheat_steam", OVERHEAT_STEAM);
        ALL.put("sniper_muzzle", SNIPER_MUZZLE);
        ALL.put("sniper_impact", SNIPER_IMPACT);
        ALL.put("rocket_launch", ROCKET_LAUNCH);
        ALL.put("rocket_trail", ROCKET_TRAIL);
        ALL.put("rocket_explosion", ROCKET_EXPLOSION);
        ALL.put("rocket_dust", ROCKET_DUST);
        ALL.put("rocket_scorch", ROCKET_SCORCH);
        ALL.put("missile_launch", MISSILE_LAUNCH);
        ALL.put("missile_trail", MISSILE_TRAIL);
        ALL.put("missile_explosion", MISSILE_EXPLOSION);
        ALL.put("tesla_discharge", TESLA_DISCHARGE);
        ALL.put("tesla_impact", TESLA_IMPACT);
        ALL.put("laser_muzzle", LASER_MUZZLE);
        ALL.put("laser_impact", LASER_IMPACT);
        ALL.put("laser_end", LASER_END);
        ALL.put("rail_muzzle", RAIL_MUZZLE);
        ALL.put("rail_ring", RAIL_RING);
        ALL.put("rail_impact", RAIL_IMPACT);
        ALL.put("rail_ground", RAIL_GROUND);
        ALL.put("mortar_launch", MORTAR_LAUNCH);
        ALL.put("mortar_trail", MORTAR_TRAIL);
        ALL.put("fire_patch", FIRE_PATCH);
        ALL.put("fire_patch_smoke", FIRE_PATCH_SMOKE);
        ALL.put("mortar_scorch", MORTAR_SCORCH);
        ALL.put("flame_stream", FLAME_STREAM);
        ALL.put("flame_splash", FLAME_SPLASH);
        ALL.put("burning", BURNING);
        ALL.put("turret_destroyed", TURRET_DESTROYED);
        ALL.put("tier_up", TIER_UP);
        ALL.put("damaged_spark", DAMAGED_SPARK);
        ALL.put("repulsor_muzzle", REPULSOR_MUZZLE);
        ALL.put("repulsor_ring", REPULSOR_RING);
        ALL.put("repulsor_haze", REPULSOR_HAZE);
        ALL.put("repulsor_hit", REPULSOR_HIT);
        ALL.put("dome_burst", DOME_BURST);
        ALL.put("dome_dust", DOME_DUST);
    }

    private VfxPresets() {
    }

    private static void play(VfxPreset preset, Vec3 at, Vec3 direction, VfxParams params) {
        VfxManager.play(preset, at, direction, params);
    }
}
