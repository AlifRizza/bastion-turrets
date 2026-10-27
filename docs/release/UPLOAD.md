# Upload sheet: 0.5.0

Icon: [icon.png](icon.png) (512 px, rendered in game: `-Pshowcase=icon` + `tools/make_icon.py`).
Project page text: [DESCRIPTION.md](DESCRIPTION.md) (Modrinth, Markdown) or
[DESCRIPTION.curseforge.html](DESCRIPTION.curseforge.html) (CurseForge editor: Source code view, links to CurseForge pages;
swap each `[IMAGE: ...]` line for that picture). Gallery images: [screenshots/](screenshots/); in-description images
under 850 px wide: [screenshots-840/](screenshots-840/).
Ready-to-upload folders (local, git-ignored; built 2026-10-23): `build/upload/0.3.2/` and `build/upload/0.4.0/`, each
with the jar, `changelog.txt`, the store text of that version and `LANGKAH.md` (steps in Indonesian). The 0.3.2 jar
there is rebuilt from commit 2cacf9d: the one first left in `build/libs` was built mid-0.4.0 and held early-game files.

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
| File                   | `build/libs/bastion-1.20.1-0.6.0.jar` (`./gradlew build`) |
| Version number         | 0.6.0                                                     |
| Version name           | Bastion Turrets 0.6.0                                     |
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
0.4.0 (user 2026-10-22 22:00): the early game turrets, a big update, so the middle number. Prepared on 2026-10-23
(changelog below, store text in `DESCRIPTION_UPDATE_0.4.0.md`), committed 2026-10-23, not pushed or uploaded yet: the user uploads it
about a week after 0.3.2.
0.5.0 (user roadmap 2026-10-23): the Fortress Turret Base and the first three 3x3 turrets (Javelin Missile, Brood Cannon,
Hive Carrier), with the user's adjustments of 2026-10-24. Changelog below, store text in `DESCRIPTION_UPDATE_0.5.0.md`;
committed 2026-10-24, not pushed or uploaded yet. Upload folder: `build/upload/0.5.0/`.
0.6.0 (user roadmap 2026-10-23, spec approved 2026-10-24): research and the Turret Codex, a big update, so the middle
number. Changelog below, store text in `DESCRIPTION_UPDATE_0.6.0.md` (new gallery image `23_turret_codex`); committed
2026-10-24 (`38c1c15`), not pushed or uploaded yet. Upload folder: `build/upload/0.6.0/`.

## Changelog: 0.6.0

```
New: Research and the Turret Codex
- The Turret Codex: every player gets one the first time they join a world (it is also crafted from a book, an iron
  ingot and redstone). Right-click it: on the left the research tree in three tabs (Turrets, Workshop, Modules),
  growing from the early turrets up to the 3x3 ones, with what each card brings along (ammo, stations, tools) beside it;
  on the right a page for the card you pick: the item in 3D, its base
  and ammo, bars for damage, fire rate and range, its description, what it unlocks and what it still needs.
- Most recipes now need research first: the workstations, every turret but the four early ones, the bases, the tier
  kits, the modules (Mk1, Mk2 and Mk3 each), the Feed Hub, the Control Hub and the Overwatch Link. A turret opens its
  parts and its ammo with it. A card needs the cards below it, and a turret also the base of its size.
- Press Research on a card to pay its cost from your inventory: iron, copper and redstone for the first steps, gold
  for the later 1x1 turrets, diamonds for the 2x2 ones, diamonds and netherite scrap for the 3x3 ones. Creative players
  research for free.
- Research is shared by everyone in the world; a chat line tells everyone what was researched. Gear you got another
  way (loot, creative, another mod) still places and works.
- Worlds that ran before 0.6.0 start with everything researched; new worlds start from the early turrets.
- A locked recipe shows a padlock in its workstation (and in JEI, where its + button says why) and says what to
  research; a locked item's tooltip says so too.
- JEI: a Research page for every card with its cost, what it unlocks, what it needs and what it leads to (R on an iron
  ingot, U on a turret). The Codex's Recipe button opens JEI on the item.
- config/bastion-research.toml: every card's cost, research on or off, and the first-join Codex. Admins:
  /bastion research grant <card|all>, /bastion research revoke <card|all>, /bastion research list.

Changes
- Hive Carrier reach doubled: a wave goes out for enemies up to 64 blocks away (was 32), the drones patrol in a wider
  circle (patrol_radius 64, was 32) and see and shoot from 32 blocks (was 16). A config/bastion-turrets.toml from an
  older version still holds the old numbers and keeps them: set its [hive_carrier] range, patrol_radius and shot_range
  to 64, 64 and 32 (or delete those three lines).
- Brood spiders climb walls like spiders, so enemies up on a wall or a tower are no longer out of their reach. They
  leap at prey 2 to 5 blocks away (fast, every 3 seconds) and bite as they land, on any ground. At 0 HP a spider no longer bursts
  where it stands: it leaps at the nearest enemy within 5 blocks and bursts on it (with none in reach it bursts where
  it is; an idle spider's self-destruct too).
- The Hive Carrier sends a wave only for enemies its drones can get at: under open sky, or in sight of the hive (over
  its cap or from its doorways). Before, a mob in a cave or behind walls in range kept sending the drones out and back
  every 15 seconds.
- Hive drones fire faster and hit harder: 10 shots a second of 3.5 damage (35 damage a second each; was 4 of 2), and
  carry 300 bullets (was 120), so they still fire for 30 seconds before they dive. A config/bastion-turrets.toml from
  an older version keeps the old numbers: set its [hive_carrier] damage, shot_interval and drone_ammo to 3.5, 2 and
  300 (or delete those three lines).

Sounds
- Real sounds for the four early turrets, the Makeshift Turret Base and the three 3x3 turrets (they were placeholders).
- The Javelin's silo doors hiss open when it finds a target and slam shut when it goes idle.
- Brood spiders skitter as they walk and climb.
- Arrow Launcher arrows thunk like vanilla arrows when they hit or stick (they were silent).

Fixes
- The Part Workstation's row of tabs (one per turret) ran past the right edge of its screen; it now shows as many
  as fit with arrows at both ends (click them, or scroll the mouse wheel over the tabs).

Note
- The research sound is a placeholder for now.
- Clients need this version to join a 0.6.0 server.
```

## Changelog: 0.5.0

```
New: Fortress Turret Base
- A 3x3 base for huge weapon modules, on the floor only. It takes the 3x3 around the block you click; the hologram
  shows the spot, red where something is in the way. Breaking any of its nine blocks takes the whole base and drops
  it once.
- T1 to T3 with the same Tier Upgrade Kits: slots, regeneration and turn speed as on every base, 500 / 1,250 / 2,500
  HP. Its glow strips show the HP like the other bases' lights. T2 adds vented side blocks, T3 cooling fins.
- Destroyed, it blasts a little wider than the Large base; the modules drop, plus a Damaged Fortress Turret Base that
  the Part Assembler restores with three Repair Kits, at the tier it had. Mined, it keeps its contents.
- Made at the Part Assembler: a new part, the Fortress Frame (Part Workstation: 4 iron blocks, a diamond and a redstone
  block), two Heavy Base Plating, a Heavy Base Housing and a Heavy Servo Turntable.
- A Feed Hub feeds it when all nine blocks stand on the same 3x3 hub. Control Hub, Overwatch Link, the
  Turret Configurator, Jade and JEI work with it as with the Large base.

New: Javelin Missile Turret
- The first huge weapon, for the Fortress Turret Base: an armoured launch silo and a radar mast. Every 8 seconds it
  fires one big missile: gas pushes it half out of the silo, it waits there a moment, its engine lights and it climbs, slow
  at first, then faster and faster until it is gone. A few seconds later it drops straight down out of the sky on the
  thickest crowd of ground enemies in range (8 to 48 blocks) that stand under open sky. The target is marked from launch to impact: a hologram chevron
  over it and a ring on the ground that shrinks to the impact, red in the last second. A crowd a missile (any
  Javelin's) is already coming down on is passed over for the next shot while another crowd is in range.
- The missile follows its target while it stays under open sky; if it ducks under a roof, the missile comes down on its
  last open spot. The blast: 40 damage in the middle down to 15 at 6 blocks, only to what the turret may shoot, no
  block damage.
- Each shot takes a Javelin Missile (Ammo Workstation: 4 iron ingots, 2 TNT, a firework rocket and 2 redstone) and
  10,000 FE. It never fires under a roof, underground or in the Nether, and never at flying mobs.
- Made at the Part Assembler from three new parts: Javelin Silo, Javelin Radar Mast and Javelin Mount.
- Overwatch: aim at open ground and fire; the mark shows where the missile will come down, red where it can't.

New: Brood Cannon Turret
- The second huge weapon, for the Fortress Turret Base: a short fat cannon over a round brood chamber whose lit windows
  show folded spiders. Every 6 seconds it lobs an armoured capsule on a high arc (over walls, like the Mortar) onto
  enemies on the ground 6 to 32 blocks away. The capsule lands, opens like a flower, and three robot spiders climb out;
  one that comes down on an enemy's head deals it 10 damage. Against a crowd the capsules spread over it.
- The spiders (16 HP) hunt only what the turret may shoot, on the ground and within reach, spreading over a crowd: a
  bite of 8 every second. With nothing left to hunt for 10 seconds they self-destruct, so none pile up after a fight. Hostile mobs go after them, so they also
  draw attackers off your base. When one dies its eyes blink red and it bursts: 15 damage within 4 blocks, again only
  to valid targets. They keep fighting if their turret is destroyed; they are not saved when the area unloads.
- Up to 12 spiders per turret (capsules in the air count): at the cap it waits and spends nothing. The server config
  maxTurretUnits (96) caps all turret units in a dimension.
- Each shot takes a Brood Capsule (Ammo Workstation: 3 iron ingots, a copper ingot, 2 redstone and a spider eye) and
  4,000 FE. Made at the Part Assembler from three new parts: Brood Barrel, Brood Chamber and Brood Mount.
- Overwatch: aims like the Mortar (the arc shows where the capsule lands); press to fire.

New: Hive Carrier Turret
- The third huge weapon, for the Fortress Turret Base: a big hollow hive of honeycomb plating with a relay mast on
  top. While enemies are within 32 blocks it sends out a wave of three combat drones every 15 seconds; they fly out
  of the doorways in its side and climb away, and come home through the same doorways. Look in through a doorway:
  docked drones stand on racks inside, and between waves a new drone is welded together on each empty pad.
- Drones (20 HP, 120 bullets) shoot whatever the turret may shoot within 16 blocks, flyers too: 2 damage, 4 shots a
  second. They patrol round the carrier. Out of bullets or below a quarter HP with an enemy in reach they dive
  kamikaze: 15 damage within 4.5 blocks, again only to valid targets. With nothing left to shoot they fly home, dock
  and wait; the next wave launches them again with fresh bullets for 1,000 FE each. Hostile mobs go after them
  (skeletons shoot them down). They keep fighting if the carrier is destroyed. Docked drones are saved with the
  carrier (mining the base keeps them, taking the module off gives them back as Hive Drone items); drones in flight are
  gone when the area unloads, and the next wave replaces them with new Hive Drones.
- Up to 9 drones per carrier, docked ones included: at the cap it waits and spends nothing. Each new drone takes a
  Hive Drone (Ammo Workstation: 2 iron ingots, 2 copper ingots, a redstone and a feather) and 2,000 FE. Made at the
  Part Assembler from three new parts: Hive Hangar, Hive Relay Mast and Hive Launch Bays.
- Overwatch: the carrier can be watched, not steered.

Changes
- The server config has fortressBaseHealthMultiplier (5.0: a Fortress has five times a standard base's HP) and
  maxTurretUnits (96: turret units, spiders and drones, alive at once in one dimension).

Fixes
- The Turret Configurator now copies and pastes settings on any block of a Large (or Fortress) Turret Base; before,
  only one of the Large base's four blocks worked.
- Glow on some base faces flickered with dark lines (the Large base's side panels); it is solid now.
- A comparator beside any block of a Large (or Fortress) Turret Base now updates when its ammo changes; before, only
  one beside the right block did.
- Damage sparks on a Large Turret Base come off all four sides, not only two.

Note
- The Javelin Missile, Brood Cannon and Hive Carrier Turrets' sounds are placeholders for now.
- Clients need this version to join a 0.5.0 server (new blocks and items).
```

## Changelog: 0.4.0

```
New: early game turrets, made at the crafting table, no FE needed
- Makeshift Turret Base: a crate of wood, stone and iron (3 iron ingots, 2 logs, 4 cobblestone). Floor, wall or
  ceiling. 50 HP (server config makeshiftBaseHealth), no tiers, module slots, FE or Connector slot. Only the four
  turrets below fit on it, and they fit no other base. Cracks and wood splinters show its damage; nothing glows.
  Destroyed, it falls apart without a blast, drops an iron ingot and two oak planks, and the weapon and ammo are lost.
  Mined, it keeps its contents.
- Cobblestone Turret: a spring cannon. One cobblestone (or cobbled deepslate) per shot, 3 damage, a shot every
  0.75 s, 16 blocks.
- Snowball Repeater: a crank drum. 5 shots a second of 1.5 damage, one snowball for 4 shots, 14 blocks.
- Potato Shotgun: a spud gun. 5 chunks of 2 damage in a wide cone every 1.5 s, 10 blocks. Poisonous potatoes work
  too (no poison).
- Arrow Launcher (floor base only): a hwacha, an arrow box on a wheeled cart, always tilted up. One arrow fires a
  volley of 6 every 2.5 s on a high arc over walls, one onto the target and five around it, 3 damage each. Mobs on
  the ground 6 to 32 blocks away, never flyers. Any arrow: tipped arrows keep their effect, spectral arrows make the
  target glow; Creative Ammo fires plain arrows. The arrows fly through what the turret may not shoot, can't be
  picked up and vanish 10 s after they stick.
- The four have their own rustic models, shots, impacts and sounds, with no glow or light.
- Hoppers and pipes load them like any turret. An empty Makeshift Turret Base takes only early ammo, and the other
  bases never take it.
- JEI: a Turret Weapons page for each of the four and an information page for the base. Jade shows the base without
  a tier.

Changes
- config/bastion-turrets.toml has a section for each new turret (the Arrow Launcher's adds arrows per volley, ring
  radius, arc speed, gravity, drag and how long a stuck arrow stays). The server config has makeshiftBaseHealth (50).

Fixes
- A damaged turret base that does not regenerate (T1 by default) showed full-health lights after you rejoined or came
  back from far away.
- Rocket, missile and mortar shell smoke trails glowed at night; they are now as dark as the night around them (the
  exhaust glow still shines).

Note
- Clients need this version to join a 0.4.0 server (new blocks, items and an entity).
- The sounds of the four new turrets and the makeshift base are placeholders for now.
```

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
| 13_early_turrets      | Early turrets    | Four rustic turrets for the Makeshift Turret Base, made at a crafting table. |
| 14_arrow_launcher     | Arrow Launcher   | A hwacha: one arrow fires a volley of six high over walls.                   |
| 15_spectral_volley    | Spectral volley  | One arrow at the feet, five around; spectral arrows make the target glow.    |
| 16_makeshift_mounts   | Makeshift bases  | Makeshift Turret Bases on a floor, a wall and a ceiling.                     |
| 17_fortress_base      | Fortress base    | The 3x3 Fortress Turret Base, built for the huge turrets to come.            |
| 18_javelin_launch     | Javelin launch   | Pushed half out of its silo, the missile ignites and climbs out of sight.     |
| 19_javelin_strike     | Javelin strike   | Straight down on the marked crowd: a huge blast that spares the cow.          |
| 20_brood_spiders      | Brood spiders    | Robot spiders out of a Brood capsule go for the husks behind the wall.        |
| 21_hive_carrier       | Hive Carrier     | A wave of combat drones flies out of the hive's side doorways.                |
| 22_hive_inside        | Inside the hive  | A new drone, built between waves, ready on its pad inside the hive.           |


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

