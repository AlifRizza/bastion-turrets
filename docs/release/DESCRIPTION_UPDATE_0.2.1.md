# Store description: what changed since the 0.2.0 text

Only these 2 parts changed; everything else (and every image) stays as it is. Each part has the Modrinth text
(Markdown) and the CurseForge text (HTML, editor in Source code view). Replace the old part with it.
Full files: `DESCRIPTION.md` (Modrinth), `DESCRIPTION.curseforge.html` (CurseForge). No new pictures.
The 0.2.1 changelog is in `UPLOAD.md`.

## 1. Weapons table: the Repulsor Dome row

The push now depends on distance: 6 blocks at the edge of its reach, one more per block closer.

Modrinth (replace the row):

```markdown
| Repulsor Dome | Large | No damage: a dome of force bursts out and throws every enemy within 9 blocks away, through walls: 6 blocks from the edge of its reach, one more per block closer (heavy mobs 60% less). Runs on FE. |
```

CurseForge (in the Repulsor Dome row, replace the third cell):

```html
<td>No damage: a dome of force bursts out and throws every enemy within 9 blocks away, through walls: 6 blocks from the edge of its reach, one more per block closer (heavy mobs 60% less). Runs on FE.</td>
```

## 2. For modpack makers: second paragraph

New: `config/bastion-turrets.toml`, every turret's stats in one config file.

Modrinth:

```markdown
Every turret's stats and weapon-specific numbers are also in `config/bastion-turrets.toml`: each value starts at the mod's own number; type another and it applies as soon as you save. Weapon stats come from `data/bastion/turret_weapons` and modules from `data/bastion/turret_modifiers`, plain datapack JSON like the workstation recipes; a changed number in the config wins over a datapack. The server config covers HP and regeneration per tier, owner protection, whether mobs attack turrets, whether Mortar shells set blocks on fire, how much energy the workstations, Feed Hubs and the Control Hub hold, take in and use, how often a mob hit by turrets may groan, and which mobs count as too heavy for the Repulsors (add big mobs from other mods there).
```

CurseForge:

```html
<p>Every turret's stats and weapon-specific numbers are also in <code>config/bastion-turrets.toml</code>: each value starts at the mod's own number; type another and it applies as soon as you save. Weapon stats come from <code>data/bastion/turret_weapons</code> and modules from <code>data/bastion/turret_modifiers</code>, plain datapack JSON like the workstation recipes; a changed number in the config wins over a datapack. The server config covers HP and regeneration per tier, owner protection, whether mobs attack turrets, whether Mortar shells set blocks on fire, how much energy the workstations, Feed Hubs and the Control Hub hold, take in and use, how often a mob hit by turrets may groan, and which mobs count as too heavy for the Repulsors (add big mobs from other mods there).</p>
```
