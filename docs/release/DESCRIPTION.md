# Bastion Turrets

Futuristic, modular turrets for Forge 1.20.1. Place a base, mount a weapon, feed it ammo, tune it with modules.
Every muzzle flash, beam, lightning arc and explosion is made for this mod: no vanilla particles, sounds or explosion effects.

> **Beta.** Expect rough edges and balance changes between versions, and back up your world. Sounds are placeholders for now.

<!-- screenshot: 01_laser_rifle_beam.png -->

## Weapons

| Weapon | Base | What it does |
|---|---|---|
| Gun Turret | Standard | Hitscan rifle. After a second on one target, its next shot deals x1.5 (x1.5 more on a headshot). |
| Machine Gun Turret | Standard | Spins up to a stream of fire. Builds heat, slows what it hits, overheats if pushed. |
| Shotgun Turret | Standard | 8 pellets with falloff and heavy knockback. |
| Sniper Turret | Standard | Charged shot behind a laser sight, 64 block range, pierces 2 targets. |
| Rocket Launcher Turret | Standard | Leads moving targets. The blast only hurts what the turret is allowed to shoot. |
| Flamethrower Turret | Standard | A cone of fire that sets every valid target in it burning. Runs on Fuel Canisters. |
| Laser Rifle Turret | Standard | Charges up, then fires a beam through every valid target in a line. Stops at walls. |
| Missile Launcher Turret | Large | 12 homing missiles. Single mode, or Salvo mode that spreads them over every target in range. |
| Tesla Coil Turret | Large | Runs on FE, no ammo. Strikes two targets at once with lightning. |

<!-- screenshot: 03_tesla_coil.png -->
<!-- screenshot: 05_missile_salvo.png -->

## Turret bases

- **Turret Base** (1 block): mounts on floors, walls and ceilings.
- **Large Turret Base** (2x2): floor only, carries the heavy weapons, more HP.
- **Three tiers.** Upgrade kits add HP, regeneration, turn speed and more ammo and module slots, with armor plates and cooling fins you can see.
- **Turrets have health.** Hostile mobs go after them; Repair Kits restore 40% HP. A destroyed turret drops its weapon, ammo and modules, and the base drops one tier (a T1 base becomes a Damaged Turret Base you can restore).
- Mining a base keeps its tier and everything inside, like a shulker box.

<!-- screenshot: 07_floor_wall_ceiling.png -->

## Targeting and control

- Target Hostile, Neutral, Passive, Players and Bosses, with rules for specific mobs and lists of players.
- Priority: nearest, lowest HP or highest threat.
- Owner protection: only you and your trusted players can open or change a turret.
- Redstone can turn a turret on or off. A comparator reads its ammo (or energy) level.
- **Turret Configurator:** copy the targeting setup of one turret and paste it onto others.

## Modules

| Module | Effect |
|---|---|
| Range Module | +25% range |
| Rapid Cycler | +20% fire rate, +15% heat per shot |
| Damage Amplifier | +20% damage |
| Penetrator | pierces 1 more target |
| Ammo Recycler | 20% chance to save ammo |
| Coolant Loop | +40% cooling |
| Overclock | +40% fire rate, +10% damage, +60% heat per shot, -15% max HP |
| Targeting AI | +25% turn speed, leads moving targets |
| Choke Module | Shotgun only: -40% spread |

## Ammo and automation

Kinetic Rounds, Scatter Shells, Sniper Rounds, Rockets, Missiles, Fuel Canisters and Laser Cells.

- Feed turrets with hoppers, Create belts, funnels and chutes, or item pipes from other mods. Automation can only insert ammo.
- With Create installed, Mechanical Arms load turrets directly.

<!-- screenshot: 08_create_automation.png -->

## Workstations

In survival, all turret gear comes out of five FE-powered machines with animated models and their own GUI:

- **Part Workstation** (2x1x2): machines the parts for every base and weapon.
- **Part Assembler** (2x2x2): builds bases and weapons from parts, and restores damaged bases.
- **Module Workstation** and **Ammo Workstation** (1x1x2): craft instantly from your inventory, or run on their own when automation feeds them.
- **Charging Station**: charges Empty Laser Cells with FE.

The crafting table only makes the stations. Each station accepts only the ingredients of its selected recipe and gives out only the result, so they are easy to automate.

> **You need an FE source.** Bastion does not add a generator. Use any mod that produces Forge Energy, for example Create Crafts & Additions, Mekanism, Thermal Series, Powah or Immersive Engineering.

<!-- screenshot: 09_workstations.png -->
<!-- screenshot: 10_part_assembler_gui.png -->

## Effects

Muzzle flashes, tracers, ejected casings, short-lived dynamic light, camera shake, custom explosions and smoke, lightning arcs, laser beams, flame jets and scorch marks that grow on walls.
The client config has VFX presets (LOW / MEDIUM / HIGH) and switches for camera shake and screen flash.

## For modpack makers

- Weapon stats (`data/bastion/turret_weapons`), modules (`data/bastion/turret_modifiers`) and all workstation recipes are datapack JSON.
- Server config: HP and regeneration per tier, owner protection, whether mobs attack turrets, workstation energy.

## Requirements

- Minecraft 1.20.1, Forge 47.2 or newer
- [GeckoLib](https://modrinth.com/mod/geckolib) 4.8 or newer (required)
- A mod that produces FE (for workstations and the Tesla Coil)
- [Create](https://modrinth.com/mod/create) 6.0 or newer (optional, Mechanical Arm support)

Install on both client and server.

## Languages

English, Bahasa Indonesia.

## Known limits of this beta

- Sounds are placeholders.
- No JEI or Jade support yet. Browse recipes in each station's own GUI.

Found a bug? Open an issue: https://github.com/AlifRizza/bastion-turrets/issues
