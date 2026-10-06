# Bastion Turrets

Futuristic, modular turrets for Forge 1.20.1. Place a base, mount a weapon, feed it ammo and tune it with modules.
The muzzle flashes, beams, lightning arcs and explosions were all made for this mod. You won't find a single vanilla particle in there.

> This is a beta. Numbers will move between versions and the sounds are still placeholders, so back up your world before you update.

<!-- screenshot: 01_laser_rifle_beam.png -->

## Weapons

| Weapon | Base | What it does |
|---|---|---|
| Gun Turret | Standard | Precise rifle. Hold a target for a second and the next shot deals x1.5 (another x1.5 on a headshot). |
| Machine Gun Turret | Standard | Spins up into a stream of bullets. Builds heat, slows what it hits and overheats if you push it. |
| Shotgun Turret | Standard | 8 pellets with damage falloff and heavy knockback. |
| Sniper Turret | Standard | Charges behind a laser sight, then sends one heavy round through 2 targets. 64 block range. |
| Rocket Launcher Turret | Standard | Leads moving targets. The blast only hurts what the turret is allowed to shoot. |
| Flamethrower Turret | Standard | Sets valid targets in a short cone on fire. Burns through Fuel Canisters. |
| Laser Rifle Turret | Standard | Charges up and fires one beam through all valid targets in a line. Walls stop it. |
| Missile Launcher Turret | Large | 12 homing missiles. Fire them as they load, or wait for a full salvo spread over everything in range. |
| Tesla Coil Turret | Large | Runs on FE instead of ammo. Its lightning hits two targets at once. |

The Gun, Machine Gun, Shotgun and Sniper fire real bullets with travel time. Turrets aim ahead of moving targets, and a bullet flies through anything the turret isn't allowed to shoot, so the villager walking past your wall stays alive.

<!-- screenshot: 03_tesla_coil.png -->
<!-- screenshot: 05_missile_salvo.png -->

## Turret bases

The standard Turret Base is one block and mounts on floors, walls or ceilings. For the heavy weapons (Missile Launcher and Tesla Coil) there's the Large Turret Base, which takes a 2x2 spot on the floor and has more HP.

Upgrade kits take a base from T1 up to T3. Each tier adds HP, regeneration, turn speed and slots for ammo and modules, and you can see it happen as armor plates and cooling fins bolt onto the base. Turrets take damage too. Hostile mobs walk up and attack them, and a Repair Kit puts back 40% HP. A destroyed turret drops its weapon, ammo and modules while the base falls one tier (a T1 base turns into a Damaged Turret Base that you can restore). Mining a base works like breaking a shulker box, so tier and contents stay with the item.

<!-- screenshot: 07_floor_wall_ceiling.png -->

## Targeting and control

- Choose the categories a turret shoots (Hostile, Neutral, Passive, Players, Bosses), then add rules for specific mobs or players.
- Priority can be nearest, lowest HP or highest threat.
- Only you and players you trust can open or change your turrets.
- Redstone turns a turret on or off, and a comparator reads its ammo (or energy) level.
- The Turret Configurator copies one turret's targeting so you can paste it onto others.

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

Each kind of module fits once per base. Different modules add up, and a T3 base has four module slots.

## Ammo and automation

Ammo comes in seven kinds (Kinetic Rounds, Scatter Shells, Sniper Rounds, Rockets, Missiles, Fuel Canisters and Laser Cells). Hoppers, Create belts, funnels and chutes or item pipes from other mods can all load a turret, and they can't put anything else in. Got Create installed? Then Mechanical Arms load turrets directly as well.

<!-- screenshot: 08_create_automation.png -->

## Workstations

Survival players make all turret gear in five machines that run on FE. Each one has an animated model and its own GUI.

- Part Workstation (2x1x2) machines the parts for each base and weapon.
- Part Assembler (2x2x2) builds bases and weapons out of those parts and restores damaged bases.
- Module Workstation and Ammo Workstation (1x1x2) craft instantly from your inventory, or run by themselves when automation feeds them.
- Charging Station (1x1x1) fills Empty Laser Cells with FE.

The crafting table only makes the stations. A station accepts nothing but the ingredients of the recipe you picked and hands out only the result, so hooking one up to automation is easy.

> **You need an FE source.** Bastion doesn't add a generator. Pair it with any mod that makes Forge Energy, for example Create Crafts & Additions, Mekanism, Thermal Series, Powah or Immersive Engineering.

<!-- screenshot: 09_workstations.png -->
<!-- screenshot: 10_part_assembler_gui.png -->

## Effects

Muzzle flashes, tracers, ejected casings, short bursts of dynamic light, camera shake, custom explosions with smoke, lightning arcs, laser beams, flame jets and scorch marks that grow on walls. Too much for your PC (or your stomach)? The client config has VFX presets LOW, MEDIUM and HIGH plus switches for camera shake and screen flash.

## For modpack makers

Built for big bases. The load test runs 400 turrets firing at once. Bases are meshed into the world like blocks, and bullets aren't entities. Most of the server cost in that test is the mobs attacking, not the turrets. Adding Radium and AI Improvements cut the tick time by about a third.

Weapon stats live in `data/bastion/turret_weapons` and modules in `data/bastion/turret_modifiers`. Both are plain datapack JSON, same as the workstation recipes. The server config covers HP and regeneration per tier, owner protection, whether mobs attack turrets and how much energy the workstations hold and take in.

## Requirements

- Minecraft 1.20.1 with Forge 47.2 or newer
- [GeckoLib](https://modrinth.com/mod/geckolib) 4.8 or newer (required)
- Any mod that produces FE, for the workstations and the Tesla Coil
- [Create](https://modrinth.com/mod/create) 6.0 or newer if you want Mechanical Arm support (optional)

Install it on both client and server.

## Languages

English and Bahasa Indonesia.

## Known limits of this beta

Sounds are placeholders, and there's no JEI or Jade support yet (each station's GUI lists its own recipes).

Works with Embeddium and Oculus. With a shader pack on, the cyan glow on the models turns white and holograms turn green. Everything else looks as intended.

Found a bug? Report it at https://github.com/AlifRizza/bastion-turrets/issues
