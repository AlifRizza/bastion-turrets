# Reference study: Turret TaCZ 0.8.5 (MIT)

Source: Modrinth `turret-tacz` jar, decompiled into `references/turret-tacz/src` (study only). Summary written in our own words.

## Models & animation
- 6 turret types share one bone pattern: `base` > `rotating_mount` (yaw) > `rotating_mount_vertical` (pitch) > gun > barrel > muzzle locators (`gun_muzzle_fire*`) and eject locators (`gun_shell_eject*`). Dual guns mirror left/right; the rotary keeps a fixed main muzzle outside the spinning group so the flash does not orbit.
- Textures are 4x the declared UV size (UV 64-256, PNG 256-1024): GeckoLib scales UVs, giving 4x pixel density without remapping.
- Animations are short recoil clips (barrel kicks 2-5 px back at ~0.083 s, returns by 0.17-0.25 s); the rotary spin is procedural. No deploy animation, no emissive textures.
- One triggerable `fire_<barrel>` animation per barrel on a fire controller, separate reload controller.

## Placement preview
- Ghost: cached client-only turret rendered through the real renderer with a translucent tint (green valid / red invalid, alpha ~0.56).
- Overlays: terrain-following range ring (raycast, cached on a 1/8-block grid), half-range ring, aim lane, elevation fan (min..max pitch), forward arrow with 90-degree snapping, reachability probe tiles.

## Aiming & rotation
- Server aims from the active muzzle, not the turret centre. Pitch limits per turret.
- Velocity-based smoothing: accelerate toward the target at ~30% of max speed, snap within one step; fire only after an aim lock (within 2.5 deg for N ticks). Reads as servo-driven.
- Sync via entity data plus a rotation sequence counter; client keeps a 24-sample jitter buffer, renders 2 samples behind, catches up at 1.25x.

## Firing
- Gameplay muzzle = fixed per-barrel offsets on the server; VFX muzzle = tracked bone world position on the client (cached after render).
- Far players (80-160 blocks) get a quieter throttled distant shot sound.
- Mortar: tick-by-tick 2D ballistic simulation, pitch search + refinement, 3D raymarch obstruction check, target lead from position history, results cached 10-20 ticks.

## Particles & VFX
- Smoke: rises, grows with ease-out (x2.6-3.2), fades with (1-life)^1.3, spin slows, lit brightly for the first 2 ticks.
- Sparks: fullbright, heavy gravity, colour ramp white-hot > orange > dark red.
- Impact flash: 3 ticks, fullbright.
- Muzzle flash: procedural camera-facing quads from one sheet (two crossed flame cards + star + halo); full intensity for ~34% of life then pow(1.5) falloff.
- Tracers: 3-layer additive ribbon (halo, outer, core), colour cools with distance.
- Casings simulated client-side only with bounce/settle and caps.

## Lighting & sound
- Heat glow: runtime mask from base-texture luminance, re-render weighted bones with an emissive render type tinted by heat.
- No real dynamic lights: packed light of the turret is raised during a flash.
- Rotation sound: start/loop/end state machine driven by smoothed angular speed (1 tick delay to start, 10 still ticks to stop).
- Reverb tail picks short/long from cached environment probes; random variants per event; near-miss vignette; camera shake.

## GUI & network
- Flat fill() panels, tabs: Ammo, Status, Filter presets, Access (target bitmask, whitelist mode, online-player whitelist), side panel cycling Priority/Behaviour, maintenance strip.
- Each control sends a tiny packet (entity id + value); server checks distance <= 8, ownership and ranges.
- Shot packets carry shot + rotation sequence and server time; client holds a flash until its rendered rotation reaches that shot (<= 6 ticks), drops anything older than 10.
- Budgets: target search every 4 ticks, <= 12 impact effects/tick within 32 blocks, 6 flashes + 36 tracers per turret, widened render culling box for tracers.

## Worth adopting (ours, in our own implementation)
1. Server muzzle from data offsets, client VFX muzzle from tracked bones (already PLAN 6.2).
2. Servo feel: acceleration-limited rotation + short aim lock before firing.
3. Hold shot VFX until the rendered barrel reaches the shot direction.
4. Rotation start/loop/end sound driven by angular speed.
5. Procedural flash quads + 3-layer additive tracer ribbons, recoloured per weapon energy colour.
6. Ghost placement preview with range ring and elevation fan (post-MVP nicety).
7. Ship real emissive textures and a deploy animation (they have neither).
