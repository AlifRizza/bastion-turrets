# Manual test checklist (Fase 2–7)

Automated: `./gradlew runGameTestServer` (47 tests: base, combat, weapons, modifiers, filter, redstone, HP,
destruction). Visual: `./gradlew devClasses runClient -Pshowcase`. The list below is what still needs eyes and hands.

Setup: creative world, a turret base with a weapon module and Creative Ammo (never runs out). Targets:
`/bastiondev zombie` (500 HP, sunlight-proof), or `/bastiondev zombie 100 5` for five 100 HP zombies.
Damage numbers and the target's HP bar come from ToroHealth (dev runClient only). Vanilla zombies have 2 armour,
so a 7-damage shot lands as ~6.9.

## Weapons (Fase 2, 4–6)
- [ ] Gun Turret: tracks a moving zombie smoothly, cyan tracer + impact sparks, lock reticle appears after ~1 s on one target, the lock shot hits harder.
- [ ] Gun Turret does not shoot through a wall and stops with `No ammo` when empty.
- [ ] Machine Gun: barrels spin up before firing, spin whine follows the barrels, heat bar rises, overheat vents steam + alarm, targets get Slowness.
- [ ] Shotgun: 8-pellet spread, magenta muzzle shockwave, pump + shell ejection, heavy knockback, weaker at long range.
- [ ] Sneak + empty hand removes the weapon; refused with a message while it is firing.
- [ ] `/bastion vfx <preset>` plays every preset (tab-complete lists them, incl. `turret_destroyed`, `tier_up`, `damaged_spark`).
- [ ] VFX quality LOW / MEDIUM / HIGH (client config) all look right.

## Sniper & Rocket Launcher
- [ ] Sniper: locks on, a green laser sight grows thicker for ~1.5 s (with a charging whine), then one heavy crack; two mobs in a line both get hit.
- [ ] Sniper re-charges from zero when its target moves out of the sights or dies mid-charge.
- [ ] Rocket Launcher: rockets fly visibly (smoke trail, glow, motor roar), lead running mobs, and explode on impact without breaking blocks.
- [ ] Explosion: white flash + shockwave, rolling fireballs (white-hot to orange to red), flame tongues, black smoke that lingers ~5 s, dust ring on the ground, a scorch mark on the floor/wall that was hit.
- [ ] Tubes: four rockets sit in the tubes; each shot empties only its own tube, a new rocket slides in ~1 s later; with no ammo the tubes run empty.
- [ ] The blast never hurts you, trusted players, pets/passive mobs (unless targeted) or other turrets; it does not fire at mobs within 4 blocks.
- [ ] Ammo: Sniper Rounds and Rockets are craftable; Creative Ammo works for both.

## Large Turret Base & Missile Launcher
- [ ] Large Turret Base: click the top of a block; it fills a 2x2 forward and to your right, only if all four blocks are free. Walls/ceilings refuse it.
- [ ] Right-click, mine, hoppers and redstone work on any of its four blocks; breaking one drops one base (tier and contents kept).
- [ ] Missile Launcher fits only the large base (the small modules only the standard one); you get a message otherwise.
- [ ] Pods stay tilted up and only turn left/right; twelve missiles show in the tubes and disappear as they fire.
- [ ] Tubes reload one by one (1.5 s each, one Missiles item each); out of missiles the lamp goes red.
- [ ] Status tab "Mode" button: Single fires each missile as it loads; Salvo waits for all 12, ripples them out and spreads them over every target in range (one target gets all 12).
- [ ] Missiles fly up, curve down onto targets, follow moving mobs, pick a new target if theirs dies; never fire at mobs closer than 6 blocks.

## Tesla Coil & Flamethrower
- [ ] Tesla Coil fits only the Large Turret Base. It never turns; the coil rings glow and the crown spins slowly.
- [ ] Without power: red lamp, no zaps. Feed FE into any of the four base blocks (an FE mod's cable or generator); the Status tab's blue gauge fills (hover: `Energy x / 60,000 FE`), max 1,000 FE/t.
- [ ] Creative Ammo in an ammo slot powers it for free (creative testing without an FE mod).
- [ ] Charging (~0.6 s): arcs crackle off the crown with a rising whine; then 2 jagged blue-white bolts strike 2 random valid targets in range (16 blocks), with a flash, sparks and a crack. A lone target takes both bolts. Struck mobs crawl with small arcs for a moment.
- [ ] Bolts follow moving mobs, never go through walls, never hit you, trusted players or passive mobs. 3,000 FE per discharge (gauge drops).
- [ ] Idle with energy: a small spark jumps off the crown now and then (faint crackle).
- [ ] Flamethrower: a solid jet of fire from the nozzle into billowing flames (~8 blocks), roaring while it fires; flames pile up and splash on walls. Each spot gets one scorch mark that glows while the fire is on it and widens the longer it burns (never a pile of marks); it cools and fades ~15 s after the fire moves on.
- [ ] Everything valid in the cone gets hurt and keeps burning ~4 s after leaving it (custom flames on the mob, it glows at night); passive mobs/you/trusted players in the cone are not burnt.
- [ ] Water or rain puts a burning mob out. Blazes and other fire-immune mobs are ignored (the turret stays idle, no fuel used).
- [ ] Fuel: 1 Fuel Canister lasts ~4 s of flame (`/give` or craft: iron + coal, makes 3). A blue pilot light flickers at the nozzle while it waits.
- [ ] Death messages: "electrocuted", "burned to a crisp", "burned to death".

## Laser Rifle
- [ ] Fits the standard base; Laser Cells as ammo (creative tab or `/give`, no recipe yet: they will come from a charging station later). Creative Ammo also works.
- [ ] Locks on, then charges ~2.5 s: light gathers at the emitter from a spark into a big red orb, sparks stream into it, the focus rings on the barrel spin faster, a rising whine; a lens flare flashes just before the shot.
- [ ] The shot: a thick red beam with energy flowing along it, a flash and kick at the emitter, sparks on every target it passes through, a glowing burn where it hits a wall. 1 cell per shot.
- [ ] Line up several mobs: one beam hits them all. A villager/pet/you standing in the line is not hurt. The beam stops at walls.
- [ ] Moving targets (also Sniper): the charge keeps going while the turret tracks a running mob; when full it fires as soon as the aim lines up. It only starts over when the target dies or leaves sight.
- [ ] The aiming laser during the charge comes straight out of the barrel and lands exactly where the beam then goes. Info tab shows Pierce `∞`.

## Weapon-specific modules
- [ ] Choke Module (craft: nuggets, redstone, copper, hopper): goes in a modules slot only while a Shotgun is mounted (refused next to other weapons). Tooltip: "-40% spread", "Only for: Shotgun Turret".
- [ ] With it, the Shotgun's pellets land in a visibly tighter cone; Info tab Spread drops from 12° to 7.2°.
- [ ] Swap the Shotgun for another weapon with the Choke still inside: its slot turns red and it does nothing until a Shotgun is back.

## Mounting: floor, wall, ceiling
- [ ] Place a base on a floor, on a wall and under a ceiling: it attaches to the clicked face and the weapon sits on its far end.
- [ ] Wall and ceiling turrets track and kill mobs (wall: straight out from the wall too; ceiling: below it).
- [ ] Casings, muzzle flashes and tracers come out of the barrel in every orientation.
- [ ] Mining a wall/ceiling turret drops it like a floor one; zombies attack wall/ceiling turrets too.

## Tiers, HP, repair (Fase 7)
- [ ] Tier Upgrade Kit T2 on a T1 base: cyan ring + armour plates appear, more ammo/module slots unlock. T3 kit on T2: cooling fins.
- [ ] T3 kit on a T1 base is refused with a message.
- [ ] Right-click / mine works from any angle, also aiming at the weapon on top.
- [ ] Arrows, explosions and hostile mobs lower HP (Status tab, left bar); a sword swing mines the block instead. Below 30%: glow blinks red, sparks.
- [ ] Zombies walk up to a turret and hit it from the side (never stand on an armed one); a nearby survival player is still chased first. Off with `mobsAttackTurrets=false` in `config/bastion-common.toml`.
- [ ] Repair Kit restores 40% HP.
- [ ] At 0 HP: custom blast (no vanilla explosion), weapon + ammo + modules drop, base drops one tier lower (T1: Damaged Turret Base).
- [ ] Damaged Turret Base + Repair Kit in a crafting grid gives a Turret Base back.

## GUI & settings (Fase 7)
- [ ] Status tab: HP bar, heat bar, state lamp under the weapon (green idle, cyan tracking, amber firing, red blinking = overheat/no ammo; hover any of them for details), ON/OFF toggle.
- [ ] Targeting tab: category toggles, player mode, priority (nearest / lowest HP / highest threat), redstone mode.
- [ ] Trusted list: type a name, `+` or Enter adds it, click a name to remove it.
- [ ] Mob rules: search `cow`, click once = always target, twice = never, three times = back to default.
- [ ] Info tab: stats change when a Range Module / Overclock is inserted.
- [ ] Redstone block next to the base disables it; after switching "Turns it on", only a powered turret fires.
- [ ] Turret Configurator: sneak + right-click copies targeting, right-click another turret pastes it.
- [ ] Another player (second client) cannot open the GUI or remove the weapon of your turret; trusted players can.

## Persistence & servers
- [ ] Leave and rejoin: tier, HP, filter, trusted list, ON/OFF, redstone mode, inventory all kept.
- [ ] Mine a T2 base with stuff inside: the item keeps tier + contents (tooltip), placing it restores them.
- [ ] `runServer` + separate client: everything above works on a dedicated server, no errors in either log.
