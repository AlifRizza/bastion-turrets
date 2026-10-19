# Store description: what changed since the text on the stores (0.1.1)

Only these 13 parts changed; everything else (and every image already uploaded) stays as it is. Each part has the
Modrinth text (Markdown) and the CurseForge text (HTML, editor in Source code view). Replace the old part with it.
Full files: `DESCRIPTION.md` (Modrinth), `DESCRIPTION.curseforge.html` (CurseForge). No new pictures this time.
The 0.2.0 changelog is in `UPLOAD.md`.

## 1. Top: the beta note

The sounds are no longer placeholders.

Modrinth:

```markdown
> This is a beta. Numbers will move between versions, so back up your world before you update.
```

CurseForge:

```html
<blockquote>
<p>This is a beta. Numbers will move between versions, so back up your world before you update.</p>
</blockquote>
```

## 2. Weapons: replace the whole table

New rows: Ion Rifle Turret, Plasma Repeater Turret, Plasma Cannon Turret. Changed: Rocket Launcher (Salvo mode), Repulsor Dome (9 blocks, was 7).

Modrinth:

```markdown
| Weapon | Base | What it does |
|---|---|---|
| Gun Turret | Standard | Precise rifle. Hold a target for a second and the next shot deals x1.5 (another x1.5 on a headshot). |
| Machine Gun Turret | Standard | Spins up into a stream of bullets. Builds heat, slows what it hits and overheats if you push it. |
| Shotgun Turret | Standard | 8 pellets of 5 damage each, falling off with distance, and heavy knockback. |
| Sniper Turret | Standard | Charges behind a laser sight, then sends one heavy round through 2 targets. 64 block range. |
| Rocket Launcher Turret | Standard | Leads moving targets. Fires one rocket at a time, or all four tubes at once in Salvo mode. The blast only hurts what the turret is allowed to shoot. |
| Flamethrower Turret | Standard | Sets valid targets in a short cone on fire. Burns through Fuel Canisters. |
| Laser Rifle Turret | Standard | Charges up and fires one beam through all valid targets in a line. Walls stop it. |
| Repulsor | Standard | No damage: after a short charge a wave of force throws everything in front of it, up to 5 blocks out, about 4 blocks back, several mobs at once, so they can't hug your guns. Too weak for heavy mobs like iron golems, ravagers and wardens. Runs on FE. |
| Ion Rifle Turret | Standard | A fast ion bolt. Where it stops, a beam zaps every valid target within 3 blocks for 5 damage, even through thin walls. Runs on FE. |
| Plasma Repeater Turret | Standard | Rapid plasma balls you can watch fly; each one splashes the targets around where it lands. Heats up and vents like the Machine Gun. 10 shots per Plasma Cell. |
| Missile Launcher Turret | Large | 12 homing missiles. Fire them as they load, or wait for a full salvo spread over everything in range. |
| Tesla Coil Turret | Large | Runs on FE instead of ammo. Its lightning hits up to five targets at once. |
| Railgun Turret | Large | Anti-boss gun. Each shot takes a Rail Slug and FE: the barrel lights up, crackles, then fires a slug that ignores armor, hits bosses twice as hard and throws mobs around the impact away. Always aims at the biggest target. |
| Mortar Turret | Large | Lobs shells over walls at mobs on the ground. Where one lands, a 3x3 fire patch burns for 5 seconds and sets everyone standing in it on fire, you and your animals too. Burnable blocks catch real fire (a server config switch turns that off). |
| Repulsor Dome | Large | No damage: a dome of force bursts out and throws every enemy within 9 blocks about 6 blocks away, through walls (heavy mobs 60% less). Runs on FE. |
| Plasma Cannon Turret | Large | Charges, then fires a big slow plasma ball that explodes like a BFG: every enemy within 4 blocks of the impact takes heavy damage, more near the middle. One Plasma Cell per shot. |
```

CurseForge:

```html
<table border="1" cellpadding="6">
<thead>
<tr>
<th>Weapon</th>
<th>Base</th>
<th>What it does</th>
</tr>
</thead>
<tbody>
<tr>
<td>Gun Turret</td>
<td>Standard</td>
<td>Precise rifle. Hold a target for a second and the next shot deals x1.5 (another x1.5 on a headshot).</td>
</tr>
<tr>
<td>Machine Gun Turret</td>
<td>Standard</td>
<td>Spins up into a stream of bullets. Builds heat, slows what it hits and overheats if you push it.</td>
</tr>
<tr>
<td>Shotgun Turret</td>
<td>Standard</td>
<td>8 pellets of 5 damage each, falling off with distance, and heavy knockback.</td>
</tr>
<tr>
<td>Sniper Turret</td>
<td>Standard</td>
<td>Charges behind a laser sight, then sends one heavy round through 2 targets. 64 block range.</td>
</tr>
<tr>
<td>Rocket Launcher Turret</td>
<td>Standard</td>
<td>Leads moving targets. Fires one rocket at a time, or all four tubes at once in Salvo mode. The blast only hurts what the turret is allowed to shoot.</td>
</tr>
<tr>
<td>Flamethrower Turret</td>
<td>Standard</td>
<td>Sets valid targets in a short cone on fire. Burns through Fuel Canisters.</td>
</tr>
<tr>
<td>Laser Rifle Turret</td>
<td>Standard</td>
<td>Charges up and fires one beam through all valid targets in a line. Walls stop it.</td>
</tr>
<tr>
<td>Repulsor</td>
<td>Standard</td>
<td>No damage: after a short charge a wave of force throws everything in front of it, up to 5 blocks out, about 4 blocks back, several mobs at once, so they can't hug your guns. Too weak for heavy mobs like iron golems, ravagers and wardens. Runs on FE.</td>
</tr>
<tr>
<td>Ion Rifle Turret</td>
<td>Standard</td>
<td>A fast ion bolt. Where it stops, a beam zaps every valid target within 3 blocks for 5 damage, even through thin walls. Runs on FE.</td>
</tr>
<tr>
<td>Plasma Repeater Turret</td>
<td>Standard</td>
<td>Rapid plasma balls you can watch fly; each one splashes the targets around where it lands. Heats up and vents like the Machine Gun. 10 shots per Plasma Cell.</td>
</tr>
<tr>
<td>Missile Launcher Turret</td>
<td>Large</td>
<td>12 homing missiles. Fire them as they load, or wait for a full salvo spread over everything in range.</td>
</tr>
<tr>
<td>Tesla Coil Turret</td>
<td>Large</td>
<td>Runs on FE instead of ammo. Its lightning hits up to five targets at once.</td>
</tr>
<tr>
<td>Railgun Turret</td>
<td>Large</td>
<td>Anti-boss gun. Each shot takes a Rail Slug and FE: the barrel lights up, crackles, then fires a slug that ignores armor, hits bosses twice as hard and throws mobs around the impact away. Always aims at the biggest target.</td>
</tr>
<tr>
<td>Mortar Turret</td>
<td>Large</td>
<td>Lobs shells over walls at mobs on the ground. Where one lands, a 3x3 fire patch burns for 5 seconds and sets everyone standing in it on fire, you and your animals too. Burnable blocks catch real fire (a server config switch turns that off).</td>
</tr>
<tr>
<td>Repulsor Dome</td>
<td>Large</td>
<td>No damage: a dome of force bursts out and throws every enemy within 9 blocks about 6 blocks away, through walls (heavy mobs 60% less). Runs on FE.</td>
</tr>
<tr>
<td>Plasma Cannon Turret</td>
<td>Large</td>
<td>Charges, then fires a big slow plasma ball that explodes like a BFG: every enemy within 4 blocks of the impact takes heavy damage, more near the middle. One Plasma Cell per shot.</td>
</tr>
</tbody>
</table>
```

## 3. Weapons: the paragraph under the table

Ion Rifle and both plasma turrets also fire real shots.

Modrinth:

```markdown
The Gun, Machine Gun, Shotgun, Sniper, Ion Rifle and both plasma turrets fire real shots with travel time. Turrets aim ahead of moving targets, and a bullet flies through anything the turret isn't allowed to shoot, so the villager walking past your wall stays alive.
```

CurseForge:

```html
<p>The Gun, Machine Gun, Shotgun, Sniper, Ion Rifle and both plasma turrets fire real shots with travel time. Turrets aim ahead of moving targets, and a bullet flies through anything the turret isn't allowed to shoot, so the villager walking past your wall stays alive.</p>
```

## 4. Turret bases: first paragraph

Plasma Cannon in the list of heavy weapons; the placement hologram.

Modrinth:

```markdown
The standard Turret Base is one block and mounts on floors, walls or ceilings. For the heavy weapons (Missile Launcher, Tesla Coil, Railgun, Mortar, Repulsor Dome and Plasma Cannon) there's the Large Turret Base, which takes a 2x2 spot on the floor and has more HP. Hold one (or a multi-block workstation) and a hologram shows exactly which blocks it will take, red where something is in the way.
```

CurseForge:

```html
<p>The standard Turret Base is one block and mounts on floors, walls or ceilings. For the heavy weapons (Missile Launcher, Tesla Coil, Railgun, Mortar, Repulsor Dome and Plasma Cannon) there's the Large Turret Base, which takes a 2x2 spot on the floor and has more HP. Hold one (or a multi-block workstation) and a hologram shows exactly which blocks it will take, red where something is in the way.</p>
```

## 5. Turret bases: second paragraph

Spore and TaCZ, health lights, the destroyed-turret rule (weapon and ammo lost, Damaged Turret Base restored at its tier).

Modrinth:

```markdown
Upgrade kits take a base from T1 up to T3. Each tier adds HP, regeneration, turn speed and slots for ammo and modules, and you can see it happen as armor plates and cooling fins bolt onto the base. Turrets take damage too. Hostile mobs walk up and attack them, modded ones too (Fungal Infection: Spore works out of the box), and bullets from gun mods like TaCZ hit them as well: a turret's lights turn yellow below half HP and blink red when it's about to go, and a Repair Kit puts back 40% HP. Let one get destroyed and the weapon and its ammo are gone for good: only the modules drop, plus a Damaged Turret Base that the Part Assembler restores with a Repair Kit, at the tier it had. Mining a base works like breaking a shulker box, so tier and contents stay with the item.
```

CurseForge:

```html
<p>Upgrade kits take a base from T1 up to T3. Each tier adds HP, regeneration, turn speed and slots for ammo and modules, and you can see it happen as armor plates and cooling fins bolt onto the base. Turrets take damage too. Hostile mobs walk up and attack them, modded ones too (Fungal Infection: Spore works out of the box), and bullets from gun mods like TaCZ hit them as well: a turret's lights turn yellow below half HP and blink red when it's about to go, and a Repair Kit puts back 40% HP. Let one get destroyed and the weapon and its ammo are gone for good: only the modules drop, plus a Damaged Turret Base that the Part Assembler restores with a Repair Kit, at the tier it had. Mining a base works like breaking a shulker box, so tier and contents stay with the item.</p>
```

## 6. New section: Turret Control Hub

Insert it after the "Targeting and control" list, before "Modules".

Modrinth:

```markdown
## Turret Control Hub

Build a 3x3x3 cube: a Hub Interface in the middle of one side at eye level, Hub Casings with at least one Hub Power Port, and a Satellite Uplink on top. Right-click an unfinished Interface and a hologram shows what is missing. Link Hub Connectors to the hub and put one in each turret you want on it: once the hub has FE, its screen shows those turrets within 1, 3x3 or 7x7 chunks (Satellite Uplink T1, T2, T3) on a map and a list, coloured by health, with their ammo and state. Click one to open its own screen from the hub: swap modules, top up ammo or change its targeting without walking over. Or select many and change their categories, priority, mob rules, trusted players or on/off all at once.
```

CurseForge:

```html
<h2>Turret Control Hub</h2>
<p>Build a 3x3x3 cube: a Hub Interface in the middle of one side at eye level, Hub Casings with at least one Hub Power Port, and a Satellite Uplink on top. Right-click an unfinished Interface and a hologram shows what is missing. Link Hub Connectors to the hub and put one in each turret you want on it: once the hub has FE, its screen shows those turrets within 1, 3x3 or 7x7 chunks (Satellite Uplink T1, T2, T3) on a map and a list, coloured by health, with their ammo and state. Click one to open its own screen from the hub: swap modules, top up ammo or change its targeting without walking over. Or select many and change their categories, priority, mob rules, trusted players or on/off all at once.</p>
```

## 7. Modules: the paragraph under the table

Mk1 / Mk2 / Mk3.

Modrinth:

```markdown
Every module comes in Mk1, Mk2 and Mk3 (one and a half and twice the Mk1 effect): upgrade the one you have at the Module Workstation with diamonds, then netherite scrap. Each kind of module fits once per base, whatever its Mk. Different modules add up, and a T3 base has four module slots.
```

CurseForge:

```html
<p>Every module comes in Mk1, Mk2 and Mk3 (one and a half and twice the Mk1 effect): upgrade the one you have at the Module Workstation with diamonds, then netherite scrap. Each kind of module fits once per base, whatever its Mk. Different modules add up, and a T3 base has four module slots.</p>
```

## 8. Ammo and automation: first paragraph

Ten kinds of ammo: Plasma Cells added.

Modrinth:

```markdown
Ammo comes in ten kinds (Kinetic Rounds, Scatter Shells, Sniper Rounds, Rockets, Missiles, Fuel Canisters, Laser Cells, Rail Slugs, Mortar Shells and Plasma Cells). Hoppers, Create belts, funnels and chutes or item pipes from other mods can all load a turret, and they can't put anything else in. Got Create installed? Then Mechanical Arms load turrets directly as well. Or mount turrets on the faces of a Feed Hub and feed just the hub: it hands the ammo out to every turret whose weapon takes it, evenly, and holds what doesn't fit yet. Place hubs together and they merge, up to 3x3x3, into one block with shared storage and an FE bank: Tesla Coils and Railguns standing on top charge from it, and large turrets on top get their ammo too.
```

CurseForge:

```html
<p>Ammo comes in ten kinds (Kinetic Rounds, Scatter Shells, Sniper Rounds, Rockets, Missiles, Fuel Canisters, Laser Cells, Rail Slugs, Mortar Shells and Plasma Cells). Hoppers, Create belts, funnels and chutes or item pipes from other mods can all load a turret, and they can't put anything else in. Got Create installed? Then Mechanical Arms load turrets directly as well. Or mount turrets on the faces of a Feed Hub and feed just the hub: it hands the ammo out to every turret whose weapon takes it, evenly, and holds what doesn't fit yet. Place hubs together and they merge, up to 3x3x3, into one block with shared storage and an FE bank: Tesla Coils and Railguns standing on top charge from it, and large turrets on top get their ammo too.</p>
```

## 9. Workstations: the Charging Station line

It fills Empty Plasma Cells too.

Modrinth:

```markdown
- Charging Station (1x1x1) fills Empty Laser Cells and Empty Plasma Cells with FE.
```

CurseForge:

```html
<li>Charging Station (1x1x1) fills Empty Laser Cells and Empty Plasma Cells with FE.</li>
```

## 10. Effects paragraph

One sentence on the turret sounds.

Modrinth:

```markdown
Muzzle flashes, tracers, ejected casings, short bursts of dynamic light, camera shake, custom explosions with smoke, lightning arcs, laser beams, railgun ion trails, flame jets, mortar fire patches and scorch marks that grow on walls. Every turret has its own sounds too: shots, charge-ups, reloads, rocket flight and blasts, with a muffled version when you hear them from far away. Too much for your PC (or your stomach)? The client config has VFX presets LOW, MEDIUM and HIGH plus switches for camera shake and screen flash.
```

CurseForge:

```html
<p>Muzzle flashes, tracers, ejected casings, short bursts of dynamic light, camera shake, custom explosions with smoke, lightning arcs, laser beams, railgun ion trails, flame jets, mortar fire patches and scorch marks that grow on walls. Every turret has its own sounds too: shots, charge-ups, reloads, rocket flight and blasts, with a muffled version when you hear them from far away. Too much for your PC (or your stomach)? The client config has VFX presets LOW, MEDIUM and HIGH plus switches for camera shake and screen flash.</p>
```

## 11. For modpack makers: second paragraph

Server config: Control Hub energy, how often a mob hit by turrets may groan.

Modrinth:

```markdown
Weapon stats live in `data/bastion/turret_weapons` and modules in `data/bastion/turret_modifiers`. Both are plain datapack JSON, same as the workstation recipes. The server config covers HP and regeneration per tier, owner protection, whether mobs attack turrets, whether Mortar shells set blocks on fire, how much energy the workstations, Feed Hubs and the Control Hub hold, take in and use, how often a mob hit by turrets may groan, and which mobs count as too heavy for the Repulsors (add big mobs from other mods there).
```

CurseForge:

```html
<p>Weapon stats live in <code>data/bastion/turret_weapons</code> and modules in <code>data/bastion/turret_modifiers</code>. Both are plain datapack JSON, same as the workstation recipes. The server config covers HP and regeneration per tier, owner protection, whether mobs attack turrets, whether Mortar shells set blocks on fire, how much energy the workstations, Feed Hubs and the Control Hub hold, take in and use, how often a mob hit by turrets may groan, and which mobs count as too heavy for the Repulsors (add big mobs from other mods there).</p>
```

## 12. Requirements: the FE line

FE is also needed for the Ion Rifle and the Turret Control Hub.

Modrinth:

```markdown
- Any mod that produces FE, for the workstations, the Tesla Coil, the Railgun, the Ion Rifle, the Repulsors and the Turret Control Hub
```

CurseForge:

```html
<li>Any mod that produces FE, for the workstations, the Tesla Coil, the Railgun, the Ion Rifle, the Repulsors and the Turret Control Hub</li>
```

## 13. Known limits of this beta: first paragraph

Sounds are no longer placeholders.

Modrinth:

```markdown
There's no JEI or Jade support yet (each station's GUI lists its own recipes).
```

CurseForge:

```html
<p>There's no JEI or Jade support yet (each station's GUI lists its own recipes).</p>
```
