# Upload sheet: 0.1.0-beta.1

Project page text: [DESCRIPTION.md](DESCRIPTION.md). Gallery images: [screenshots/](screenshots/).

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
| File | `build/libs/bastion-1.20.1-0.1.0-beta.1.jar` (`./gradlew build`) |
| Version number | 0.1.0-beta.1 |
| Version name | Bastion Turrets 0.1.0-beta.1 |
| Release channel / type | Beta |
| Loader | Forge |
| Game version | 1.20.1 |
| Java | 17 |
| Dependencies | GeckoLib: required. Create: optional. |

Next betas: `0.1.0-beta.2`, `-beta.3`, ... Bump `mod_version` in `gradle.properties`, rebuild, upload as Beta.
First non-beta: `0.1.0` with channel Release.

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
- Custom VFX for every weapon. Sounds are placeholders.
```

## Gallery captions

| File | Title | Description |
|---|---|---|
| 01_laser_rifle_beam | Laser Rifle | One beam through every valid target in line. |
| 02_laser_rifle_charge | Charging up | Light gathers at the emitter before the shot. |
| 03_tesla_coil | Tesla Coil | Lightning into two targets at once, powered by FE. |
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
