# Upload sheet: 0.1.0-beta.4

Icon: [icon.png](icon.png) (512 px, rendered in game: `-Pshowcase=icon` + `tools/make_icon.py`).
Project page text: [DESCRIPTION.md](DESCRIPTION.md) (Modrinth, Markdown) or
[DESCRIPTION.curseforge.html](DESCRIPTION.curseforge.html) (CurseForge editor: Source code view, links to CurseForge pages;
swap each `[IMAGE: ...]` line for that picture). Gallery images: [screenshots/](screenshots/); in-description images
under 850 px wide: [screenshots-840/](screenshots-840/).

## Project fields

| Field | Value |
|---|---|
| Name | Bastion Turrets |
| Slug | bastion-turrets |
| Summary | Futuristic modular turrets with tiered bases, automatable ammo, FE workstations and fully custom effects. |
| License | All Rights Reserved (`mod_license` in gradle.properties) |
| Source / Issues | https://github.com/AlifRizza/bastion-turrets (repo must be public for players to open issues) |
| CurseForge categories | Technology; Armor, Tools, and Weapons |
| Modrinth categories | Technology, Equipment |
| Modrinth environment | Client: required, Server: required |

## Version fields

| Field | Value |
|---|---|
| File | `build/libs/bastion-1.20.1-0.1.0-beta.4.jar` (`./gradlew build`) |
| Version number | 0.1.0-beta.4 |
| Version name | Bastion Turrets 0.1.0-beta.4 |
| Release channel / type | Beta |
| Loader | Forge |
| Game version | 1.20.1 |
| Java | 17 |
| Dependencies | GeckoLib: required. Create: optional. |

Next betas: `0.1.0-beta.2`, `-beta.3`, ... Bump `mod_version` in `gradle.properties`, rebuild, upload as Beta.
First non-beta: `0.1.0` with channel Release.

## Changelog: 0.1.0-beta.5 (not released yet)

```
- New block: Feed Hub. Mount turret bases on its faces and feed only the hub (pipes, belts, funnels, hoppers,
  Mechanical Arms): it hands the ammo out to the turrets whose weapon takes it, the emptiest first, and keeps
  what doesn't fit yet in a 9-slot buffer. Made at the Module Workstation.
- Feed Hubs placed together merge into one block, up to 3x3x3 (square base, never taller than wide), with
  one frame and glow line around it. The structure shares its slots (9 per block) and stores FE (50,000 per
  block, up to 4,000 FE/t in per block) for the energy turrets on it. Large Turret Bases standing on top of a
  2x2 or wider structure are fed too. Breaking one block drops only its own slots; the rest re-forms.
- Feed Hub GUI: Summary tab (size, turrets, energy, ammo totals) and a scrolling Storage tab.
- Fix: shift-clicking ammo into a turret's GUI onto a stack already there, or taking only part of a stack out
  (inventory nearly full), was not saved: after a reload the rounds put in were gone, or the ones taken came back.
- Creative Power Source: cables and pipes that pull energy (Pipez, Mekanism, ...) now connect to it and draw endless FE.
```

## Changelog: 0.1.0-beta.4

```
New turrets (Large Turret Base):
- Railgun: the anti-boss gun. Each shot takes a Rail Slug and 8,000 FE. The coils light up one by one,
  electricity crawls over the barrel, then a slug flies at 40 blocks per tick: 60 damage that ignores armor,
  double against bosses, and a shockwave that throws mobs around the impact away. Always aims at the
  biggest target in range.
- Mortar: lobs shells on a high arc over walls at mobs on the ground (never flyers). It shells the spot where
  its target stands, so it's best against crowds; a lone mob that keeps walking gets away. Each shell leaves a
  3x3 fire patch for 5 seconds that burns everyone standing in it, you and your animals too, and sets
  burnable blocks on fire with real fire (server config "mortarBlockFire" turns that off).
- New ammo: Rail Slugs and Mortar Shells (Ammo Workstation). New parts for both turrets.

Balance:
- Tesla Coil: lightning now hits up to 5 targets per discharge (was 2), at most 2 bolts per target.
  A lone mob still takes 2 bolts (20 damage). FE cost unchanged (3,000 per discharge).
- Shotgun: 5 damage per pellet (was 2.5). Point blank, one blast kills a zombie.
```

## Changelog: 0.1.0-beta.3

```
- Missile salvo trails no longer zigzag: each trail ends at its own missile's blast.
- Homing missiles fly smooth arcs instead of weaving around targets thrown by blasts.
```

## Changelog: 0.1.0-beta.2

```
- Rockets and missiles leave smoke trails: thick grey smoke behind rockets, white contrails behind missiles, a hot
  exhaust glow at the nozzle. The smoke widens, drifts and fades over about two seconds.
- New Autoloader module: +40% reload speed for the Rocket and Missile Launcher.
- Rockets and missiles explode where they hit a mob's body, not at its feet.
- Turrets pick their next target the moment the current one dies. The Flamethrower keeps its flame on while it
  swings to the next mob, the Machine Gun keeps firing through short swings.
```

## Changelog: 0.1.0-beta.1

```
First public beta.
- 9 turret weapons: Gun, Machine Gun, Shotgun, Sniper, Rocket Launcher, Flamethrower, Laser Rifle,
  Missile Launcher and Tesla Coil.
- Turret Base (floor, wall, ceiling) and Large Turret Base (2x2), tiers T1-T3, HP, repair, destruction.
- Targeting filters, owner/trusted protection, redstone control, Turret Configurator.
- 9 modules, incl. the Shotgun-only Choke Module.
- Ammo automation (hoppers, Create belts/funnels/chutes, Mechanical Arms with Create).
- 5 FE-powered workstations for survival crafting: Part Workstation, Part Assembler,
  Module Workstation, Ammo Workstation, Charging Station.
- Gun, Machine Gun, Shotgun and Sniper fire bullets with travel time; turrets lead moving
  targets; bullets fly through non-targets.
- Built for big bases (load-tested with 400 turrets firing).
- Custom VFX for every weapon. Sounds are placeholders.
```

## Gallery captions

| File | Title | Description |
|---|---|---|
| 01_laser_rifle_beam | Laser Rifle | One beam through every valid target in line. |
| 02_laser_rifle_charge | Charging up | Light gathers at the emitter before the shot. |
| 03_tesla_coil | Tesla Coil | Lightning into up to five targets at once, powered by FE. |
| 04_night_defense | Night defense | Flamethrower and Tesla Coil holding a line. |
| 05_missile_salvo | Missile salvo | 12 homing missiles spread over every target in range. |
| 06_rocket_explosion | Rocket Launcher | Custom explosions that only hurt valid targets. |
| 07_floor_wall_ceiling | Any surface | Turrets mount on floors, walls and ceilings. |
| 08_create_automation | Automation | Create belts and Mechanical Arms keep turrets loaded. |
| 09_workstations | Workstations | Five FE-powered machines make every part, weapon and round. |
| 10_part_assembler_gui | Part Assembler | Recipe list, 3D preview, have/need, energy and progress. |

Featured image: `01_laser_rifle_beam` (or the generated banner below).

## Image prompts

Reference images (in-game, cropped from the screenshots) are in [reference/](reference/). Always attach them so the
generated turrets match the mod's models:

| File | Shows |
|---|---|
| `ref_all_turrets.png` | all six on one sheet: use this when the tool takes a single image |
| `ref_gun_turret.png` | Gun Turret on the standard base (best match for the icon) |
| `ref_rocket_launcher.png`, `ref_flamethrower.png` | other standard-base weapons |
| `ref_tesla_coil.png`, `ref_missile_launcher.png` | the large 2x2 base weapons |
| `ref_laser_rifle.png` | Laser Rifle barrel close-up with its focus rings |

Put any title text on afterwards in an editor: generated lettering is unreliable.

**Icon / logo** (square, 1:1, export 512x512; must still read at 64x64). Attach `ref_gun_turret.png` (+ `ref_all_turrets.png`):

```
Game mod icon. Recreate the turret from the reference image as faithfully as possible: same blocky voxel shapes
built from cubes, same dark gunmetal grey armor plates, same thin glowing cyan accent lines in the same places,
same octagonal tower base with the dark ring under the weapon head, same long barrel. Keep the Minecraft-like
pixel textures, do not smooth or redesign it. Show it alone at a three-quarter angle, barrel aimed slightly toward
the viewer, centered and filling about 80% of the frame. Bold clean silhouette, strong cyan rim light, dark navy to
black radial gradient background with a soft cyan glow behind the turret. High contrast, no text, no logo, no watermark.
```

Negative prompt (if supported): `text, letters, watermark, Minecraft logo, Mojang, realistic photo, people, smooth curved surfaces, different turret design, extra barrels, blurry, busy background`

**Banner / featured image** (16:9, 1920x1080). Attach `ref_all_turrets.png` (+ `ref_tesla_coil.png`, `ref_missile_launcher.png`, `ref_laser_rifle.png` if allowed):

```
Wide cinematic scene in a blocky voxel Minecraft style at dusk. A stone fortress wall defended by the turrets from
the reference images, reproduced faithfully (same blocky shapes, dark gunmetal plates, thin glowing cyan lines, no
redesign): the Laser Rifle turret fires a bright red beam, the Tesla Coil on its square base arcs blue-white
lightning into blocky zombies, the Missile Launcher with two 6-tube pods fires a salvo with smoke trails. Orange
explosion glow on the ground, dark blue sky, soft volumetric light, low dramatic camera angle. Keep the left third
of the image calm and dark for a title. No text, no logo, no watermark.
```

If the result drifts from the models, regenerate with a stronger reference weight (where the tool has one) or a
single reference instead of the sheet.

## Before you press publish

- [ ] Test the built jar outside the dev environment: a clean CurseForge or Modrinth app instance with Forge 1.20.1,
      GeckoLib and one FE mod. Craft a station, power it, make a turret in survival.
- [ ] Join that instance's world from a dedicated server (`./gradlew runServer` or a real server) with the same jar.
- [ ] Repo public (or remove the issues link from DESCRIPTION.md).
