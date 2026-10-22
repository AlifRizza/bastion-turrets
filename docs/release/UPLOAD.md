# Upload sheet: 0.3.2

Icon: [icon.png](icon.png) (512 px, rendered in game: `-Pshowcase=icon` + `tools/make_icon.py`).
Project page text: [DESCRIPTION.md](DESCRIPTION.md) (Modrinth, Markdown) or
[DESCRIPTION.curseforge.html](DESCRIPTION.curseforge.html) (CurseForge editor: Source code view, links to CurseForge pages;
swap each `[IMAGE: ...]` line for that picture). Gallery images: [screenshots/](screenshots/); in-description images
under 850 px wide: [screenshots-840/](screenshots-840/).

## Project fields


| Field                 | Value                                                                                                                                         |
| --------------------- | --------------------------------------------------------------------------------------------------------------------------------------------- |
| Name                  | Bastion Turrets                                                                                                                               |
| Slug                  | bastion-turrets                                                                                                                               |
| Summary               | Futuristic modular turrets with tiered bases, automatable ammo, FE workstations and fully custom effects.                                     |
| License               | All Rights Reserved (`mod_license` in gradle.properties)                                                                                      |
| Source / Issues       | [https://github.com/AlifRizza/bastion-turrets](https://github.com/AlifRizza/bastion-turrets) (repo must be public for players to open issues) |
| CurseForge categories | Technology; Armor, Tools, and Weapons                                                                                                         |
| Modrinth categories   | Technology, Equipment                                                                                                                         |
| Modrinth environment  | Client: required, Server: required                                                                                                            |


## Version fields


| Field                  | Value                                                     |
| ---------------------- | --------------------------------------------------------- |
| File                   | `build/libs/bastion-1.20.1-0.3.2.jar` (`./gradlew build`) |
| Version number         | 0.3.2                                                     |
| Version name           | Bastion Turrets 0.3.2                                     |
| Release channel / type | Beta                                                      |
| Loader                 | Forge                                                     |
| Game version           | 1.20.1                                                    |
| Java                   | 17                                                        |
| Dependencies           | GeckoLib: required. Create, JEI, Jade: optional.          |


Versions (user 2026-10-07): betas `0.1.0-beta.1` .. `-beta.4`, then plain numbers on the Beta channel: `0.1.1`, `0.1.2`,
... Bump `mod_version` in `gradle.properties`, rebuild, upload as Beta. First Release: `1.0.0`.
The next one is **0.2.0**, not 0.1.2 (user 2026-10-14: too many changes for a patch number; still Beta). Big
updates bump the middle number, fixes the last. 0.2.1 (user 2026-10-19): the config file and the Dome push, after
0.2.0 was uploaded. 0.2.2 (user 2026-10-20): JEI and Jade support, a patch number like 0.2.1. 0.3.0 (user 2026-10-21):
Overwatch Link, a big update, so the middle number. 0.3.2 (user 2026-10-22): launcher control under Overwatch and the
fixes. From 0.3.2 on, versions are committed and pushed with their changelog and store text ready, but uploaded later by
the user: 0.3.2 a few days after 2026-10-22, 0.4.0 about a week after that. Development goes on in between.

## Changelog: 0.3.2

```
New
- Overwatch: Rocket and Missile Launchers fire one shot per click while a tube is loaded (no waiting for the fire
  interval); R switches Single / Salvo.
- Missile Launcher under control: the crosshair's mob is locked (amber brackets); with none, the missile goes for a random
  enemy of the turret's filter and flies past what the filter spares. In Salvo, click a mob to lock a missile on it,
  12 locks launch the salvo (one mob can take several, shown as x3), right-click undoes the last lock.
- The camera HUD shows numbers: HP, ammo count, FE, tubes, heat, charge and reload.

Fixes
- Switching turret with the arrow keys keeps F5 and the zoom.
- No Jade popup while controlling a turret.
- Zoomed in, the mouse turns the view slower (like a spyglass).
- Missiles can no longer be locked on mobs the launcher cannot see.
- Creative and spectator players are never hit by controlled shots.
- The Control Hub map hint no longer gets cut off on the smallest screen.
- The arrow keys skip turrets your game has not loaded, instead of ending the control.

Note
- Clients need this version to join a 0.3.2 server (new network packets).
```

## Changelog: 0.3.0

```
New
- Overwatch Link: a Hub Connector with a camera and fire control, made at the Module Workstation. Shift + right-click a
  Hub Interface to link it, put it in a turret's Connector slot, then on the hub's map shift-click that turret and press
  Take control.
- You see from the turret (a raised third-person view, F5 for a sight over the barrel), aim with the mouse and fire with
  attack. Manual aim ignores turn speed; a controlled turret reaches 1.5x farther (server config
  overwatchRangeMultiplier) and fires even when switched off. Scroll to zoom, arrow keys to switch turret, sneak to
  leave; taking damage also ends it.
- The camera HUD shows HP, ammo or FE, heat, charge and reload, and marks where the shot really lands. Mortar shots show
  their arc and landing ring. Tesla Coils and Repulsor Domes can be watched.
- Jade and the turret GUI show who is controlling a turret.

Changes
- Right-clicking a Hub Interface opens its screen whatever you hold; shift + right-click links a Hub Connector or places a
  block.
- Gun, Shotgun and Sniper bullets are amber like the Machine Gun's (their sights and holograms keep their colours); the
  Shotgun's spent shells are brass.

Fixes
- A Machine Gun switched off mid-burst kept its barrels spinning.

Note
- Clients need this version to join a 0.3.0 server (new network packets).
```

## Changelog: 0.2.2

```
New
- JEI support: a recipe page for each of the 5 workstations (ingredients with counts, time, FE), a "Turret Weapons"
  page per weapon (its base, ammo and stats; press U on an ammo to see which turrets fire it), information pages for
  bases, kits, hubs and the Creative Power Source, and the + button that moves a recipe's ingredients into an open
  station.
- Jade support: look at a turret for its weapon, tier, HP and state (Shift: owner, modules, heat, target); at a
  workstation for what it makes and its progress; at a Feed Hub or Control Hub for what it feeds or reaches. Any block
  of a big machine shows the whole machine.

Note
- Clients need this version to join a server running it (new network packet).
```

## Changelog: 0.2.1

```
New
- Turret stats config: config/bastion-turrets.toml holds every turret's stats (damage, range, fire rate, ammo or FE
  per shot, turn speed, heat, ...) and its own numbers (charge time, blast radius, knockback, ...). Each value starts
  at the mod's own number; type another to change it (left at the mod's number, a datapack's value still counts).
  Saving the file applies it at once, no restart; on a server, the server's file counts.

Changes
- Repulsor Dome: the closer a mob is, the harder it is thrown: 6 blocks at the edge of its 9-block reach, one more
  per block closer (a mob right against the dome flies about 12).

Fixes
- A turret weapon set to 0 damage or a 0 blast radius (by a datapack) no longer breaks the health of the mobs it hits.
```

## Changelog: 0.2.0

```
New turrets
- Ion Rifle (standard base, FE): fires a fast blue ion bolt. Where it stops, an ion core zaps every valid target
  within 3 blocks with a beam for 5 damage, even through thin walls. 1,200 FE per shot.
- Plasma Repeater (standard base): rapid plasma balls you can watch fly, each splashing the targets around where it
  lands. Heats up and vents like the Machine Gun. 10 shots per Plasma Cell.
- Plasma Cannon (Large Turret Base): charges for a second, then fires a big slow plasma ball that explodes like a
  BFG: every enemy within 4 blocks takes up to 14 damage (half at the edge), the one it hits 18 on top. One Plasma
  Cell per shot.
- Plasma Cell: made at the Ammo Workstation; empty cells refill at the Charging Station.

New: Turret Control Hub
- Build a 3x3x3 cube (all parts made at the Module Workstation): a Hub Interface in the middle of one side at eye
  level, Hub Casings (8 per craft) with at least one Hub Power Port, and a Satellite Uplink on top. Right-click an
  unfinished Interface and a hologram shows what is missing.
- Hub Connector (4 per craft): right-click the Interface to link a stack, then put one in a turret's new Connector
  slot. Only turrets holding a connector linked to that hub show up in it.
- The hub screen shows those turrets within 1, 3x3 or 7x7 chunks (Satellite Uplink T1/T2/T3) on a map and in a list,
  coloured by health, with ammo and state. Click one to open its own screen from the hub (swap modules, top up ammo,
  change targeting), or select many and change categories, priority, mob rules, trusted players or on/off at once.
- Runs on FE: 20 / 60 / 150 FE per tick (T1/T2/T3) from a 200,000 FE buffer filled through the Power Ports (up to
  2,000 FE/t each). Out of FE it goes OFFLINE and comes back once it holds 5 seconds of upkeep.

New sounds
- Every turret has real sound effects now instead of placeholders: shots, charge-ups, reloads, rocket and missile
  flight, blasts, plus muffled versions you hear from far away. The Machine Gun spins up, fires one solid barrage and
  winds down; Rocket and Missile Launchers clank as each round slides in; the Large Turret Base has its own
  destruction sound.
- Mobs hit by turrets groan at most twice a second and quieter (server config turretHurtSoundInterval and
  turretHurtSoundVolume): before, every single bullet played a hurt sound. Death sounds are unchanged.
- Removed for now: bullet impact sounds, and the Mortar shell's falling whistle (shells now fall silently).

New
- Upgrade modules come in Mk1, Mk2 and Mk3 (x1.5 / x2 of Mk1, Penetrator +1/+2/+3): upgrade the one you have at the
  Module Workstation (2 diamonds + 4 redstone, then 1 netherite scrap + 2 diamonds + 1 amethyst shard). One of each
  kind per turret, whatever its Mk.
- Rocket Launcher: Salvo mode (fire-mode button in the turret screen, like the Missile Launcher). It waits for all 4
  tubes, fires all four together, side by side, at its target, then reloads one tube at a time: same damage per
  second as Single, all in one burst.
- Placement hologram: holding a Large Turret Base or a multi-block workstation shows a see-through model and a box
  per block where it will go, red where something is in the way.
- Health lights: a turret base's lights turn yellow below half HP and blink red below a quarter, with sparks (the
  weapon keeps its glow); before, it only blinked red under 30%. The base lights are bigger: tall window slits and a
  thicker energy ring (the Large Base ring no longer spins, its straight bars cut through the body as they turned).
- Creative tab: Turret Base and Large Turret Base also come ready-made at T2 and T3.
- Modules: hold Shift on a module to see which turrets it actually does something on (e.g. Coolant Loop: Machine Gun).

Compatibility
- Guns from other mods (tested: TaCZ) now hit turrets: their bullets used to fly through. Owner protection still
  applies, so only turrets that are not yours (or hostile mobs) can break them.
- Fungal Infection: Spore: its mobs are shot by turrets and attack them like any other hostile mob.

Changes
- Repulsor Dome reaches 9 blocks (was 7); it still throws them about 6 blocks.
- A destroyed turret (0 HP) now loses its weapon module and its ammo; only the modules drop. The base always drops as
  a Damaged Turret Base that remembers its tier: restore it at the Part Assembler with a Repair Kit and it comes back
  as T1, T2 or T3 (before: everything dropped and the base only fell one tier). Mining your own base still keeps all.
- Mortar: blast damage 6 -> 20 (a shell now kills a plain zombie where it lands).
- Burning (Flamethrower and Mortar fire patch): 1.5 -> 3 damage per second.
- Lighter camera shake: Rocket Launcher launches and explosions and the Repulsor Dome burst shake much less, and
  shakes from several turrets no longer add up (many turrets firing at once shake like the strongest one).

Fixes
- Leaving and rejoining a world with several Large Turret Bases could hang the world load forever.
- Turrets now hit a mob pressed right against the muzzle (the shot started inside it and passed through).
- Railgun: the ion trail no longer flashes through and past the target for a moment before the hit shows; it stops
  where the slug lands from the first frame.
- Railgun: ignored mobs on the ground a few blocks away when it stood on a raised base (it could only dip 10°, now
  30°), and its slugs vanished when fired downward near the bottom of the world (superflat worlds). The second fix
  covers every bullet weapon.
- Workstations: placing one no longer builds its upper/back blocks around a mob or pet standing there (it
  suffocated).
- Feed Hub: an ammo item a turret refused at the last moment could vanish; it now stays in the hub.
- Textures: a few small faces showed the wrong strip of their texture (Machine Gun fan blades and barrel rings,
  Flamethrower fuel pipe, Part Workstation bottom drawer and one wrench on its tool board).
```

## Changelog: 0.1.1

```
New turrets (no damage, FE-powered): they push mobs away from your turrets, which can't shoot what hugs their barrels.
- Repulsor (standard base): turns to its target, charges, then a wave of force throws every valid target in a 35°
  cone up to 5 blocks out about 4 blocks back, several at once. Walls stop it. 500 FE per push, every 1.5 s.
- Repulsor Dome (Large Turret Base): a dome of force bursts out and throws every valid target within 7 blocks about
  6 blocks away, through walls. 2,000 FE per pulse, every 4 s.
- Heavy mobs (server config "repulsorHeavyMobs": iron golem, ravager, warden by default, add modded ones): the
  Repulsor leaves them alone, the Dome throws them 60% less far ("repulsorHeavyMobPush").
- Both: only valid targets (you, pets and villagers stay put), parts and recipes at the Part Workstation / Part
  Assembler.

New block: Feed Hub
- Mount turret bases on its faces and feed only the hub (pipes, belts, funnels, hoppers, Mechanical Arms): it hands
  the ammo out to the turrets whose weapon takes it, the emptiest first, and keeps what doesn't fit yet in a 9-slot
  buffer. Made at the Module Workstation.
- Feed Hubs placed together merge into one block, up to 3x3x3 (square base, never taller than wide), with one frame
  and glow line around it. The structure shares its slots (9 per block) and stores FE (50,000 per block, up to
  4,000 FE/t in per block) for the energy turrets on it. Large Turret Bases standing on top of a 2x2 or wider
  structure are fed too. Breaking one block drops only its own slots; the rest re-forms.
- GUI with a Summary tab (size, turrets, energy, ammo totals) and a scrolling Storage tab.

Fixes
- Shift-clicking ammo into a turret's GUI onto a stack already there, or taking only part of a stack out (inventory
  nearly full), was not saved: after a reload the rounds put in were gone, or the ones taken came back.
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


| File                  | Title            | Description                                                                  |
| --------------------- | ---------------- | ---------------------------------------------------------------------------- |
| 01_laser_rifle_beam   | Laser Rifle      | One beam through every valid target in line.                                 |
| 02_laser_rifle_charge | Charging up      | Light gathers at the emitter before the shot.                                |
| 03_tesla_coil         | Tesla Coil       | Lightning into up to five targets at once, powered by FE.                    |
| 04_night_defense      | Night defense    | Flamethrower and Tesla Coil holding a line.                                  |
| 05_missile_salvo      | Missile salvo    | 12 homing missiles spread over every target in range.                        |
| 06_rocket_explosion   | Rocket Launcher  | Custom explosions that only hurt valid targets.                              |
| 07_floor_wall_ceiling | Any surface      | Turrets mount on floors, walls and ceilings.                                 |
| 08_create_automation  | Automation       | Create belts and Mechanical Arms keep turrets loaded.                        |
| 09_workstations       | Workstations     | Five FE-powered machines make every part, weapon and round.                  |
| 10_part_assembler_gui | Part Assembler   | Recipe list, 3D preview, have/need, energy and progress.                     |
| 11_repulsor_dome      | Repulsor Dome    | A dome of force throws every mob within 9 blocks away from your turrets.     |
| 12_railgun_charge     | Railgun charging | The coils light up, then electricity crawls over the barrel before the shot. |


Featured image: `01_laser_rifle_beam` (or the generated banner below).

## Image prompts

Reference images (in-game, cropped from the screenshots) are in [reference/](reference/). Always attach them so the
generated turrets match the mod's models:


| File                                              | Shows                                                             |
| ------------------------------------------------- | ----------------------------------------------------------------- |
| `ref_all_turrets.png`                             | all six on one sheet: use this when the tool takes a single image |
| `ref_gun_turret.png`                              | Gun Turret on the standard base (best match for the icon)         |
| `ref_rocket_launcher.png`, `ref_flamethrower.png` | other standard-base weapons                                       |
| `ref_tesla_coil.png`, `ref_missile_launcher.png`  | the large 2x2 base weapons                                        |
| `ref_laser_rifle.png`                             | Laser Rifle barrel close-up with its focus rings                  |


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

