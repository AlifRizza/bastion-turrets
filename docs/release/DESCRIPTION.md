# Bastion Turrets

Futuristic, modular turrets for Forge 1.20.1. Place a base, mount a weapon, feed it ammo and tune it with modules.
The muzzle flashes, beams, lightning arcs and explosions were all made for this mod. You won't find a single vanilla particle in there.

> This is a beta. Numbers will move between versions, so back up your world before you update.

<!-- screenshot: 01_laser_rifle_beam.png -->

## Weapons

| Weapon | Base | What it does |
|---|---|---|
| Gun Turret | Standard | Precise rifle. Hold a target for a second and the next shot deals x1.5 (another x1.5 on a headshot). |
| Machine Gun Turret | Standard | Spins up into a stream of bullets. Builds heat, slows what it hits and overheats if you push it. |
| Shotgun Turret | Standard | 8 pellets of 5 damage each, falling off with distance, and heavy knockback. |
| Sniper Turret | Standard | Charges behind a laser sight, then sends one heavy round through 2 targets. 64 block range. |
| Rocket Launcher Turret | Standard | Leads moving targets. Fires one rocket at a time, or all four tubes at once in Salvo mode. The blast only hurts what the turret is allowed to shoot. |
| Flamethrower Turret | Standard | Sets valid targets in a short cone on fire. Burns through Fuel Canisters. |
| Laser Rifle Turret | Standard | Charges up and fires one beam through all valid targets in a line. Walls stop it. |
| Repulsor | Standard | No damage: after a short charge a wave of force throws everything in front of it, up to 5 blocks out, about 4 blocks back, several mobs at once, so they can't hug your guns. Too weak for heavy mobs like iron golems, ravagers and wardens. Runs on FE. |
| Ion Rifle Turret | Standard | A fast ion bolt. Where it stops, a beam zaps every valid target within 3 blocks for 5 damage, even through thin walls. Runs on FE. |
| Plasma Repeater Turret | Standard | Rapid plasma balls you can watch fly; each one splashes the targets around where it lands. Heats up and vents like the Machine Gun. 10 shots per Plasma Cell. |
| Missile Launcher Turret | Large | 12 homing missiles. Fire them as they load, or wait for a full salvo spread over everything in range. |
| Tesla Coil Turret | Large | Runs on FE instead of ammo. Its lightning hits up to five targets at once. |
| Railgun Turret | Large | Anti-boss gun. Each shot takes a Rail Slug and FE: the barrel lights up, crackles, then fires a slug that ignores armor, hits bosses twice as hard and throws mobs around the impact away. Always aims at the biggest target. |
| Mortar Turret | Large | Lobs shells over walls at mobs on the ground. Where one lands, a 3x3 fire patch burns for 5 seconds and sets everyone standing in it on fire, you and your animals too. Burnable blocks catch real fire (a server config switch turns that off). |
| Repulsor Dome | Large | No damage: a dome of force bursts out and throws every enemy within 9 blocks away, through walls: 6 blocks from the edge of its reach, one more per block closer (heavy mobs 60% less). Runs on FE. |
| Plasma Cannon Turret | Large | Charges, then fires a big slow plasma ball that explodes like a BFG: every enemy within 4 blocks of the impact takes heavy damage, more near the middle. One Plasma Cell per shot. |

The Gun, Machine Gun, Shotgun, Sniper, Ion Rifle and both plasma turrets fire real shots with travel time. Turrets aim ahead of moving targets, and a bullet flies through anything the turret isn't allowed to shoot, so the villager walking past your wall stays alive.

<!-- screenshot: 03_tesla_coil.png -->
<!-- screenshot: 12_railgun_charge.png -->
<!-- screenshot: 11_repulsor_dome.png -->
<!-- screenshot: 05_missile_salvo.png -->

## Turret bases

The standard Turret Base is one block and mounts on floors, walls or ceilings. For the heavy weapons (Missile Launcher, Tesla Coil, Railgun, Mortar, Repulsor Dome and Plasma Cannon) there's the Large Turret Base, which takes a 2x2 spot on the floor and has more HP. Hold one (or a multi-block workstation) and a hologram shows exactly which blocks it will take, red where something is in the way.

Upgrade kits take a base from T1 up to T3. Each tier adds HP, regeneration, turn speed and slots for ammo and modules, and you can see it happen as armor plates and cooling fins bolt onto the base. Turrets take damage too. Hostile mobs walk up and attack them, modded ones too (Fungal Infection: Spore works out of the box), and bullets from gun mods like TaCZ hit them as well: a turret's lights turn yellow below half HP and blink red when it's about to go, and a Repair Kit puts back 40% HP. Let one get destroyed and the weapon and its ammo are gone for good: only the modules drop, plus a Damaged Turret Base that the Part Assembler restores with a Repair Kit, at the tier it had. Mining a base works like breaking a shulker box, so tier and contents stay with the item.

<!-- screenshot: 07_floor_wall_ceiling.png -->

## Targeting and control

- Choose the categories a turret shoots (Hostile, Neutral, Passive, Players, Bosses), then add rules for specific mobs or players.
- Priority can be nearest, lowest HP or highest threat.
- Only you and players you trust can open or change your turrets.
- Redstone turns a turret on or off, and a comparator reads its ammo (or energy) level.
- The Turret Configurator copies one turret's targeting so you can paste it onto others.

## Turret Control Hub

Build a 3x3x3 cube: a Hub Interface in the middle of one side at eye level, Hub Casings with at least one Hub Power Port, and a Satellite Uplink on top. Right-click an unfinished Interface and a hologram shows what is missing. Link Hub Connectors to the hub and put one in each turret you want on it: once the hub has FE, its screen shows those turrets within 1, 3x3 or 7x7 chunks (Satellite Uplink T1, T2, T3) on a map and a list, coloured by health, with their ammo and state. Click one to open its own screen from the hub: swap modules, top up ammo or change its targeting without walking over. Or select many and change their categories, priority, mob rules, trusted players or on/off all at once.

## Modules

| Module | Effect |
|---|---|
| Range Module | +25% range |
| Rapid Cycler | +20% fire rate, +15% heat per shot |
| Damage Amplifier | +20% damage |
| Penetrator | pierces 1 more target |
| Ammo Recycler | 20% chance to save ammo |
| Coolant Loop | +40% cooling |
| Overclock | +40% fire rate and +10% damage, paid for with +60% heat per shot and -15% max HP |
| Targeting AI | +25% turn speed, aims ahead of moving targets perfectly |
| Choke Module | -40% spread (Shotgun only) |
| Autoloader | +40% reload speed (Rocket and Missile Launcher only) |

Every module comes in Mk1, Mk2 and Mk3 (one and a half and twice the Mk1 effect): upgrade the one you have at the Module Workstation with diamonds, then netherite scrap. Each kind of module fits once per base, whatever its Mk. Different modules add up, and a T3 base has four module slots.

## Ammo and automation

Ammo comes in ten kinds (Kinetic Rounds, Scatter Shells, Sniper Rounds, Rockets, Missiles, Fuel Canisters, Laser Cells, Rail Slugs, Mortar Shells and Plasma Cells). Hoppers, Create belts, funnels and chutes or item pipes from other mods can all load a turret, and they can't put anything else in. Got Create installed? Then Mechanical Arms load turrets directly as well. Or mount turrets on the faces of a Feed Hub and feed just the hub: it hands the ammo out to every turret whose weapon takes it, evenly, and holds what doesn't fit yet. Place hubs together and they merge, up to 3x3x3, into one block with shared storage and an FE bank: Tesla Coils and Railguns standing on top charge from it, and large turrets on top get their ammo too.

<!-- screenshot: 08_create_automation.png -->

## Workstations

Survival players make all turret gear in five machines that run on FE. Each one has an animated model and its own GUI.

- Part Workstation (2x1x2) machines the parts for each base and weapon.
- Part Assembler (2x2x2) builds bases and weapons out of those parts and restores damaged bases.
- Module Workstation and Ammo Workstation (1x1x2) craft instantly from your inventory, or run by themselves when automation feeds them.
- Charging Station (1x1x1) fills Empty Laser Cells and Empty Plasma Cells with FE.

The crafting table only makes the stations. A station accepts nothing but the ingredients of the recipe you picked and hands out only the result, so hooking one up to automation is easy.

> **You need an FE source.** Bastion doesn't add a generator. Pair it with any mod that makes Forge Energy, for example Create Crafts & Additions, Mekanism, Thermal Series, Powah or Immersive Engineering.

<!-- screenshot: 09_workstations.png -->
<!-- screenshot: 10_part_assembler_gui.png -->

## Effects

Muzzle flashes, tracers, ejected casings, short bursts of dynamic light, camera shake, custom explosions with smoke, lightning arcs, laser beams, railgun ion trails, flame jets, mortar fire patches and scorch marks that grow on walls. Every turret has its own sounds too: shots, charge-ups, reloads, rocket flight and blasts, with a muffled version when you hear them from far away. Too much for your PC (or your stomach)? The client config has VFX presets LOW, MEDIUM and HIGH plus switches for camera shake and screen flash.

## For modpack makers

Built for big bases. The load test runs 400 turrets firing at once. Bases are meshed into the world like blocks, and bullets aren't entities. Most of the server cost in that test is the mobs attacking, not the turrets. Adding Radium and AI Improvements cut the tick time by about a third.

Every turret's stats and weapon-specific numbers are also in `config/bastion-turrets.toml`: each value starts at the mod's own number; type another and it applies as soon as you save. Weapon stats come from `data/bastion/turret_weapons` and modules from `data/bastion/turret_modifiers`, plain datapack JSON like the workstation recipes; a changed number in the config wins over a datapack. The server config covers HP and regeneration per tier, owner protection, whether mobs attack turrets, whether Mortar shells set blocks on fire, how much energy the workstations, Feed Hubs and the Control Hub hold, take in and use, how often a mob hit by turrets may groan, and which mobs count as too heavy for the Repulsors (add big mobs from other mods there).

## Requirements

- Minecraft 1.20.1 with Forge 47.2 or newer
- [GeckoLib](https://modrinth.com/mod/geckolib) 4.8 or newer (required)
- Any mod that produces FE, for the workstations, the Tesla Coil, the Railgun, the Ion Rifle, the Repulsors and the Turret Control Hub
- [Create](https://modrinth.com/mod/create) 6.0 or newer if you want Mechanical Arm support (optional)
- Optional: [JEI](https://modrinth.com/mod/jei) 15.20+ (recipe pages for the workstations and weapons, the + button) and [Jade](https://modrinth.com/mod/jade) 11.13+ (turret, workstation and hub info when you look at them)

Install it on both client and server.

## Languages

English and Bahasa Indonesia.

## Known limits of this beta

Works with Embeddium and Oculus. With a shader pack on, the cyan glow on the models turns white and holograms turn green. Everything else looks as intended.

Found a bug? Report it at https://github.com/AlifRizza/bastion-turrets/issues
