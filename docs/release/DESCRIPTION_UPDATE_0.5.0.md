# Store description: what changed since the 0.4.0 text

Only these parts changed; everything else (and every old image) stays as it is. Each part has the Modrinth text
(Markdown) and the CurseForge text (HTML, editor in Source code view). Full files: `DESCRIPTION.md` (Modrinth),
`DESCRIPTION.curseforge.html` (CurseForge). The 0.5.0 changelog is in `UPLOAD.md`.

This 0.5.0 also holds what was first planned as 0.6.0 (user 2026-10-31), so parts 6 to 9 come from that
update and part 10 drops the placeholder-sound line the 0.4.0 page has.

Not uploaded yet (2026-10-31): the live page has the 0.4.0 text (uploaded 2026-10-28, with the placeholder-sound line).

## 1. Weapons table: the Javelin Missile, Brood Cannon and Hive Carrier Turrets

A new row after "Plasma Cannon Turret" (before "Cobblestone Turret").

Modrinth:

```markdown
| Javelin Missile Turret | Fortress | One big missile every 8 seconds: out of its silo, a moment's pause, then its engine lights and it climbs faster and faster until it is gone; a few seconds later it drops straight down onto the thickest crowd of ground enemies under open sky, marked from launch to impact. Its 6-block blast hurts only what the turret may shoot. Each shot takes a Javelin Missile and FE; under a roof, underground or in the Nether it does not fire. |
```

CurseForge:

```html
<tr>
<td>Javelin Missile Turret</td>
<td>Fortress</td>
<td>One big missile every 8 seconds: out of its silo, a moment's pause, then its engine lights and it climbs faster and faster until it is gone; a few seconds later it drops straight down onto the thickest crowd of ground enemies under open sky, marked from launch to impact. Its 6-block blast hurts only what the turret may shoot. Each shot takes a Javelin Missile and FE; under a roof, underground or in the Nether it does not fire.</td>
</tr>
```

Then a second new row right after it (also before "Cobblestone Turret"):

```markdown
| Brood Cannon Turret | Fortress | Lobs an armoured capsule over walls onto enemies on the ground; it opens like a flower and three robot spiders climb out to hunt and bite whatever the turret may shoot, and blow up when they die or after 10 seconds with nothing to hunt. Hostile mobs go after them. Up to 12 spiders per turret: it waits while they are out. Each shot takes a Brood Capsule and FE. |
```

```html
<tr>
<td>Brood Cannon Turret</td>
<td>Fortress</td>
<td>Lobs an armoured capsule over walls onto enemies on the ground; it opens like a flower and three robot spiders climb out to hunt and bite whatever the turret may shoot, and blow up when they die or after 10 seconds with nothing to hunt. Hostile mobs go after them. Up to 12 spiders per turret: it waits while they are out. Each shot takes a Brood Capsule and FE.</td>
</tr>
```

And a third row right after those (also before "Cobblestone Turret"):

```markdown
| Hive Carrier Turret | Fortress | A big hollow hive: while enemies are in reach it sends out waves of three combat drones every 15 seconds through the doorways in its side. They shoot whatever the turret may shoot, flyers too, patrol round the carrier, dive kamikaze when out of bullets or badly hurt, and fly home to dock and reload. Hostile mobs go after them. Up to 9 drones per carrier; each new drone takes a Hive Drone and FE, a docked one only FE. Docked drones are saved with the carrier; drones in flight are gone when the area unloads. |
```

```html
<tr>
<td>Hive Carrier Turret</td>
<td>Fortress</td>
<td>A big hollow hive: while enemies are in reach it sends out waves of three combat drones every 15 seconds through the doorways in its side. They shoot whatever the turret may shoot, flyers too, patrol round the carrier, dive kamikaze when out of bullets or badly hurt, and fly home to dock and reload. Hostile mobs go after them. Up to 9 drones per carrier; each new drone takes a Hive Drone and FE, a docked one only FE. Docked drones are saved with the carrier; drones in flight are gone when the area unloads.</td>
</tr>
```

## 2. Turret bases: one sentence about the Fortress

In the section's first paragraph, add this sentence right after the one that ends "has more HP." (before "Hold one (or
a multi-block workstation)..."). Same text on both sites (CurseForge: inside the same `<p>`):

```text
The Fortress Turret Base takes a 3x3 spot on the floor and has the most HP of all; it carries the huge weapons: the Javelin Missile, Brood Cannon and Hive Carrier Turrets.
```

## 3. Ammo and automation: thirteen kinds

Same text on both sites: "Ammo comes in ten kinds (... Mortar Shells and Plasma Cells)" becomes

```text
Ammo comes in thirteen kinds (Kinetic Rounds, Scatter Shells, Sniper Rounds, Rockets, Missiles, Fuel Canisters, Laser Cells, Rail Slugs, Mortar Shells, Plasma Cells, Javelin Missiles, Brood Capsules and Hive Drones)
```

## 4. Overwatch Link: the Javelin's mark, the Brood Cannon's arc, the Hive Carrier watched

Same text on both sites: "Mortar shots show their arc; Tesla Coils" becomes

```text
Mortar and Brood Cannon shots show their arc and the Javelin marks the spot its missile will hit; Tesla Coils, Repulsor Domes and Hive Carriers can be watched.
```

(The sentence used to end "Tesla Coils and Repulsor Domes can be watched.")

## 5. Pictures

Gallery (both sites, from `screenshots/`, captions in `UPLOAD.md` "Gallery captions"): `17_fortress_base`, `18_javelin_launch`,
`19_javelin_strike`, `20_brood_spiders`, `21_hive_carrier`, `22_hive_inside`.

Optional: to show the Fortress in the text too, put `screenshots-840/17_fortress_base.jpg` after the
`13_early_turrets` picture (end of the Turret bases section); the full description files do not have it.

## 6. A new section: Research and the Turret Codex

Between "Workstations" and "Effects". Image: `screenshots/23_turret_codex.png` (CurseForge: `screenshots-840/23_turret_codex.jpg`).

Modrinth:

```markdown
## Research and the Turret Codex

Survival worlds open Bastion step by step. Every player gets a Turret Codex the first time they join (or crafts one from a book, an iron ingot and redstone). Its left half is the research tree in three tabs, Turrets, Workshop and Modules, growing from the four early turrets up to the 3x3 ones, with what each card brings along (ammo, stations, tools) beside it. Its right half is a page for the card you pick: the item in 3D, the base it needs and its ammo, bars for damage, fire rate and range, what it unlocks and what it still needs.

- A card needs the cards below it, and a turret also needs the base of its size. Press Research to pay its cost from your inventory: iron and copper for the first steps, up to diamonds and netherite scrap for the 3x3 turrets.
- Research unlocks recipes. A researched turret opens its parts and its ammo too. Gear you got another way (loot, creative, another mod) still works.
- Research is shared by everyone in the world, and a chat line tells them. Worlds from before 0.5.0 start with everything researched.
- Servers can change every cost in `config/bastion-research.toml` or switch research off; admins have `/bastion research grant|revoke|list`.

<!-- screenshot: 23_turret_codex.png -->
```

CurseForge:

```html
<h2>Research and the Turret Codex</h2>
<p>Survival worlds open Bastion step by step. Every player gets a Turret Codex the first time they join (or crafts one from a book, an iron ingot and redstone). Its left half is the research tree in three tabs, Turrets, Workshop and Modules, growing from the four early turrets up to the 3x3 ones, with what each card brings along (ammo, stations, tools) beside it. Its right half is a page for the card you pick: the item in 3D, the base it needs and its ammo, bars for damage, fire rate and range, what it unlocks and what it still needs.</p>
<ul>
<li>A card needs the cards below it, and a turret also needs the base of its size. Press Research to pay its cost from your inventory: iron and copper for the first steps, up to diamonds and netherite scrap for the 3x3 turrets.</li>
<li>Research unlocks recipes. A researched turret opens its parts and its ammo too. Gear you got another way (loot, creative, another mod) still works.</li>
<li>Research is shared by everyone in the world, and a chat line tells them. Worlds from before 0.5.0 start with everything researched.</li>
<li>Servers can change every cost in <code>config/bastion-research.toml</code> or switch research off; admins have <code>/bastion research grant|revoke|list</code>.</li>
</ul>
<p>[IMAGE: screenshots-840/23_turret_codex.jpg]</p>
```

## 7. Workstations: one sentence

Modrinth and CurseForge (same text in both):

Old: The crafting table makes only the stations and the early gear (the Makeshift Turret Base and its four turrets).

New: The crafting table makes only the stations (once researched, see below), the Turret Codex and the early gear (the Makeshift Turret Base and its four turrets).

## 8. For modpack makers: one sentence added

Modrinth: after "a changed number in the config wins over a datapack." add: What each research costs, and whether research is on at all, is in `config/bastion-research.toml`.

CurseForge: the same, with `<code>config/bastion-research.toml</code>`.

## 9. Requirements: the JEI line

Old: (recipe pages for the workstations and weapons, the + button)

New: (recipe pages for the workstations and weapons, research pages, the + button)

## 10. Known limits: the placeholder sounds line goes

The sounds are real now: in "Known limits of this beta", delete the paragraph "The four early turrets and the Makeshift
Turret Base still have placeholder sounds; their real sounds come in a later update." (after the Embeddium / Oculus
one), on both sites.
