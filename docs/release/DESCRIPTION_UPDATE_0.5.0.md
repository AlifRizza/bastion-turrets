# Store description: what changed since the 0.4.0 text

Only these parts changed; everything else (and every old image) stays as it is. Each part has the Modrinth text
(Markdown) and the CurseForge text (HTML, editor in Source code view). Full files: `DESCRIPTION.md` (Modrinth),
`DESCRIPTION.curseforge.html` (CurseForge). The 0.5.0 changelog is in `UPLOAD.md`.

Not uploaded yet: 0.4.0 goes up about a week after 0.3.2, 0.5.0 after that, once all three 3x3 turrets (Javelin
Missile, Brood Cannon, Hive Carrier) are done. This file grows with each of them.

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

Gallery (both sites, from `screenshots/`, caption in `UPLOAD.md` "Gallery captions"): `17_fortress_base`. Pictures of the three
3x3 turrets come with the release screenshots.

Optional: to show the Fortress in the text too, put `screenshots-840/17_fortress_base.jpg` after the
`13_early_turrets` picture (end of the Turret bases section); the full description files do not have it.
