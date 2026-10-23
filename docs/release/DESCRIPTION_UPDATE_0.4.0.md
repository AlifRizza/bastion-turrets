# Store description: what changed since the 0.3.2 text

Only these 5 parts changed; everything else (and every image) stays as it is. Each part has the Modrinth text
(Markdown) and the CurseForge text (HTML, editor in Source code view). Full files: `DESCRIPTION.md` (Modrinth),
`DESCRIPTION.curseforge.html` (CurseForge). No new pictures yet: the early turrets have not been captured in game. The
0.4.0 changelog is in `UPLOAD.md`.

Not uploaded yet (user 2026-10-22): 0.3.2 goes up a few days after 2026-10-22, 0.4.0 about a week after that.

## 1. Intro: the vanilla particle line

The Arrow Launcher fires real vanilla arrows. In the intro, replace this sentence:

```markdown
You won't find a single vanilla particle in there.
```

with this one (same text on both sites; on CurseForge it stays inside the first `<p>`):

```markdown
You won't find a vanilla particle in there, except the swirls of a potion effect on a mob hit by a tipped or spectral arrow. The Arrow Launcher fires real vanilla arrows, drawn as usual (plain, tipped or spectral); their trails and puffs are ours.
```

## 2. Weapons: four new rows at the end of the table

Add these rows after the Plasma Cannon Turret row.

Modrinth:

```markdown
| Cobblestone Turret | Makeshift | A spring cannon that flings cobblestone (or cobbled deepslate): 3 damage per stone, 16 block range. |
| Snowball Repeater | Makeshift | A crank drum that fires five snowballs a second, 1.5 damage each. One snowball is four shots. |
| Potato Shotgun | Makeshift | A spud gun: 5 potato chunks of 2 damage in a wide cone, 10 block range. Poisonous potatoes work too (no poison). |
| Arrow Launcher | Makeshift | One arrow fires a volley of 6 on a high arc over walls, onto and around a mob on the ground 6 to 32 blocks away, 3 damage each. Takes any arrow: tipped ones keep their effect, spectral ones make the target glow. Floor bases only. |
```

CurseForge (after the Plasma Cannon Turret `</tr>`):

```html
<tr>
<td>Cobblestone Turret</td>
<td>Makeshift</td>
<td>A spring cannon that flings cobblestone (or cobbled deepslate): 3 damage per stone, 16 block range.</td>
</tr>
<tr>
<td>Snowball Repeater</td>
<td>Makeshift</td>
<td>A crank drum that fires five snowballs a second, 1.5 damage each. One snowball is four shots.</td>
</tr>
<tr>
<td>Potato Shotgun</td>
<td>Makeshift</td>
<td>A spud gun: 5 potato chunks of 2 damage in a wide cone, 10 block range. Poisonous potatoes work too (no poison).</td>
</tr>
<tr>
<td>Arrow Launcher</td>
<td>Makeshift</td>
<td>One arrow fires a volley of 6 on a high arc over walls, onto and around a mob on the ground 6 to 32 blocks away, 3 damage each. Takes any arrow: tipped ones keep their effect, spectral ones make the target glow. Floor bases only.</td>
</tr>
```

## 3. Turret bases: a new paragraph about the makeshift base

Add it after the second paragraph (the one that ends "so tier and contents stay with the item"), before the picture.

Modrinth:

```markdown
The Makeshift Turret Base is the early one: a crate of wood, stone and iron made at a crafting table, before you have FE or any workstation. It mounts on floors, walls or ceilings and takes only the four early turrets (the Arrow Launcher only on a floor), which fit no other base. It has 50 HP (a T1 Turret Base has 100) and no tiers, module slots, FE or Connector slot. Nothing on it glows: cracks show its damage. Destroyed, it falls apart into an iron ingot and two planks, and the weapon and its ammo are lost; mined, it keeps its contents like the other bases.
```

CurseForge:

```html
<p>The Makeshift Turret Base is the early one: a crate of wood, stone and iron made at a crafting table, before you have FE or any workstation. It mounts on floors, walls or ceilings and takes only the four early turrets (the Arrow Launcher only on a floor), which fit no other base. It has 50 HP (a T1 Turret Base has 100) and no tiers, module slots, FE or Connector slot. Nothing on it glows: cracks show its damage. Destroyed, it falls apart into an iron ingot and two planks, and the weapon and its ammo are lost; mined, it keeps its contents like the other bases.</p>
```

## 4. Ammo and automation: the first sentence

Replace the first sentence ("Ammo comes in ten kinds (...)") with this one (same text on both sites):

```markdown
Ammo comes in ten kinds (Kinetic Rounds, Scatter Shells, Sniper Rounds, Rockets, Missiles, Fuel Canisters, Laser Cells, Rail Slugs, Mortar Shells and Plasma Cells), and the early turrets fire what you already have: cobblestone or cobbled deepslate, snowballs, potatoes and any arrow.
```

## 5. Workstations: two sentences

Same text on both sites. Replace the section's first sentence:

```markdown
Survival players make all turret gear in five machines that run on FE.
```

with:

```markdown
Survival players make all turret gear except the early turrets and their base in five machines that run on FE.
```

And replace this sentence under the list:

```markdown
The crafting table only makes the stations.
```

with:

```markdown
The crafting table makes only the stations and the early gear (the Makeshift Turret Base and its four turrets).
```
