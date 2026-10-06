# Reference study: Open Modular Turrets Reborn (GPL-3.0-only)

Source: github.com/ShapeShifterDev/OMTReborn branch 1.20.1 (study only, no code copied). Summary in our own words.

## Architecture
- Two blocks, no turret entities: a 5-tier base (power, 9 ammo + addon/upgrade slots unlocked by tier, settings, owner/trust) and a head block that must sit on the base. Heads find the base by scanning neighbours.
- Stats in a COMMON ForgeConfigSpec, one reusable group per weapon; several keys are never read.

## Targeting
- Search every N ticks when idle; a locked target is re-checked every tick (alive, LOS, range).
- Cube search + per-weapon minimum range; owner/trusted/creative/spectator skipped; config blacklist of entity types. Everything non-Monster counts as "neutral" (pets and villagers get shot).
- LOS ray starts ~0.55 blocks toward the target so it does not hit the turret's own block at steep angles.
- Priority: ordered weighted keys (HP, missing HP, distance, armor, is-player).
- Lead: target velocity x flight time.
- Rotation snaps instantly (no turn speed, no smoothing); pitch limits are effectively broken.

## Firing
- Every weapon is a ThrowableProjectile (speed 3 blocks/tick, 40-tick lifespan). An unused hitscan path rays blocks first, then nearest inflated entity box.
- Custom damage types without a causing entity; i-frames reset on every hit.
- Ammo consumed per shot from base slots then expanders; item capability exposes all 13 slots (hoppers can pull upgrades out).

## Visuals, sound, sync
- Hand-coded vanilla ModelParts, partialTick ignored (choppy). Projectiles have empty renderers; visuals are per-tick particles (rockets send server particle packets).
- No muzzle flash beyond a server smoke burst, no dynamic lights. Fire sounds use per-player linear falloff, not positional.
- Angle packet to everyone within 64 blocks every tick while tracking; full NBT block updates.

## GUI & ownership
- Container screen + configure screen; packets carry pos + value and re-check access level, but some toggle instead of set, ranges unvalidated.
- Access ladder NONE < OPEN_GUI < CHANGE_SETTINGS < ADMIN with op bypass; yet anyone can open the base GUI, take ammo/upgrades, or break blocks.

## Worth adopting
1. Base owns ammo/upgrades/settings; mounted weapon is thin and reads stats from the base (already our design).
2. Search rarely when idle, re-validate a locked target every tick (already PLAN 4.5).
3. Start LOS rays outside the turret's own block (we start from the muzzle).
4. Weighted ordered priority keys (Fase 7 priority dropdown).
5. Access ladder re-checked server-side in every packet; packets send absolute values, never toggles.
6. Proximity warning to players with a per-player cooldown (optional).

## Do better than them
- Give damage a cause (FakePlayer owned by the turret owner) so loot, XP and kill credit work (Fase 7).
- Rate-limited server rotation, sparse sync, client interpolation with partialTick, correct pitch limits.
- Trust required to open/break/extract; automation sees only ammo slots; validate distance and ranges in packets.
- Cheap filters before rays, cached sort keys, positional sounds, client-side tracers instead of server particles.
