# Reference study: TaCZ (GPL-3.0)

Source: github.com/MCModderAnchor/TACZ branch 1.20.1 (study only, no code copied). Summary in our own words.

## Bullets & tracers
- Bullets are real entities simulated client-side from spawn data (velocity sync off); collisions server-only via swept raycast.
- Tracer: a unit cube squashed to width x width x length along velocity, additive unculled tintable render type, fullbright via forced block light 15, colour from gun/ammo data.
- Length ~0.85 x distance travelled per tick (no gaps), capped at 0.8 x distance from the shooter so it never pokes out behind the gun.
- Width grows with camera distance (x max(1, d/3.5)) so far tracers stay visible.
- Hidden for the first ticks while within 2 blocks; first-person offset that fades to zero by 50 blocks.

## Muzzle flash
- Locator bone `muzzle_flash`; the pose is captured on the first frame after a shot and frozen; drawn after the gun batch flushes.
- One 2x2-block fullbright quad in two layers: translucent full size + additive core at half size; 50 ms life, grows 0 to full in the first 25 ms, random roll per shot.
- Muzzle position: walk the bone path and read the matrix translation (with an FOV correction for held items).

## Particles
- Single custom type: bullet hole decal drawn on the terrain sheet using a random 1/16 tile of the hit block's particle sprite (takes the block colour), tinted with the tracer colour, starts fullbright and cools to black over ~30 ticks, offset 0.01 from the surface, removed when the block goes away.
- Data-driven trail particles spawned along each tick's travel, skipped near the shooter. No sparks/debris, no pooling.

## Lighting & shaders
- No dynamic lights. Bones named `*_illuminated` are forced to full light.
- Oculus/Iris: skip flash/casings in the shadow pass, flush Iris's buffered source manually before first person; laser beam has its own additive render type (src-alpha/one, fullbright, no cull) with alpha fading along its length, and a config switch to disable the fade for shader packs.

## Recoil & camera
- Per-gun recoil keyframes with min/max ranges rolled per shot, fitted with a cubic spline; per-frame delta added to the view.
- Procedural root-bone kick for 300 ms (ease-out cubic) with noise sway; camera bone in animations; spring-damper (second-order dynamics) smoothing for transitions.

## Sound
- `shoot` (shooter) and `shoot_3p` (others) variants + silenced versions; 3p only to players in range (default 64 x per-gun multiplier).
- Manual attenuation: volume = base x (1 - d/max)^2; pitch 0.9-1.025; per-entity concurrency cap (2-4), oldest stopped; orphan sounds cleaned every 5 ticks.

## Casings
- No physics: position v0*t + a*t^2/2, rotation omega*t, wall-clock time; max 128 queued; first-person only.

## Performance & packets
- Bullets never saved, tracked within 5 chunks; inflated culling box with a NaN/zero fallback; distance LOD model.
- Avoid: full ItemStack per round, hurt/kill packets to the whole dimension, camera-space casings.

## Worth adopting (ours)
1. Server hit, client cosmetic tracer from the muzzle bone to the server end point (PLAN 5.3).
2. Tracer length ~ visual speed x one tick, capped by distance from the muzzle; widen with camera distance.
3. Custom additive fullbright beam render type with alpha falloff along the length (BastionRenderTypes.ADDITIVE_GLOW).
4. Two-layer muzzle flash (translucent shell + additive core), very short life, random roll.
5. Impact decal tinted with the energy colour that starts glowing and cools to black (PLAN 5.3 decal).
6. Near/far sound variants, manual falloff, concurrency cap.
7. Skip extra VFX in the shader shadow pass; inflate render bounding boxes for tracers.
