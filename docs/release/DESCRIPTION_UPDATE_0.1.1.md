# Store description: what changed since the text on the stores (0.1.0-beta.2/beta.3)

Only these 7 parts changed; everything else (and every image already uploaded) stays as it is. Each part has the
Modrinth text (Markdown) and the CurseForge text (HTML, editor in Source code view). Replace the old part with it.
Full files: `DESCRIPTION.md` (Modrinth), `DESCRIPTION.curseforge.html` (CurseForge).

New pictures: `screenshots/11_repulsor_dome.png`, `screenshots/12_railgun_charge.png` (gallery, captions in
UPLOAD.md) and `screenshots-840/11_repulsor_dome.jpg`, `12_railgun_charge.jpg` (inside the description).

## 1. Weapons: replace the whole table

Changed: the Shotgun row (5 damage per pellet) and the Tesla Coil row (up to five targets). New rows: Repulsor, Railgun Turret, Mortar Turret, Repulsor Dome.

Modrinth:

```markdown
| Weapon | Base | What it does |
|---|---|---|
| Gun Turret | Standard | Precise rifle. Hold a target for a second and the next shot deals x1.5 (another x1.5 on a headshot). |
| Machine Gun Turret | Standard | Spins up into a stream of bullets. Builds heat, slows what it hits and overheats if you push it. |
| Shotgun Turret | Standard | 8 pellets of 5 damage each, falling off with distance, and heavy knockback. |
| Sniper Turret | Standard | Charges behind a laser sight, then sends one heavy round through 2 targets. 64 block range. |
| Rocket Launcher Turret | Standard | Leads moving targets. The blast only hurts what the turret is allowed to shoot. |
| Flamethrower Turret | Standard | Sets valid targets in a short cone on fire. Burns through Fuel Canisters. |
| Laser Rifle Turret | Standard | Charges up and fires one beam through all valid targets in a line. Walls stop it. |
| Repulsor | Standard | No damage: after a short charge a wave of force throws everything in front of it, up to 5 blocks out, about 4 blocks back, several mobs at once, so they can't hug your guns. Too weak for heavy mobs like iron golems, ravagers and wardens. Runs on FE. |
| Missile Launcher Turret | Large | 12 homing missiles. Fire them as they load, or wait for a full salvo spread over everything in range. |
| Tesla Coil Turret | Large | Runs on FE instead of ammo. Its lightning hits up to five targets at once. |
| Railgun Turret | Large | Anti-boss gun. Each shot takes a Rail Slug and FE: the barrel lights up, crackles, then fires a slug that ignores armor, hits bosses twice as hard and throws mobs around the impact away. Always aims at the biggest target. |
| Mortar Turret | Large | Lobs shells over walls at mobs on the ground. Where one lands, a 3x3 fire patch burns for 5 seconds and sets everyone standing in it on fire, you and your animals too. Burnable blocks catch real fire (a server config switch turns that off). |
| Repulsor Dome | Large | No damage: a dome of force bursts out and throws every enemy within 7 blocks about 6 blocks away, through walls (heavy mobs 60% less). Runs on FE. |
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
<td>Leads moving targets. The blast only hurts what the turret is allowed to shoot.</td>
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
<td>No damage: a dome of force bursts out and throws every enemy within 7 blocks about 6 blocks away, through walls (heavy mobs 60% less). Runs on FE.</td>
</tr>
</tbody>
</table>
```

## 2. Weapons: two new images

Under the Tesla Coil image, add the two new pictures (upload them to the gallery / description first).

Modrinth:

```markdown
<!-- screenshot: 12_railgun_charge.png -->
<!-- screenshot: 11_repulsor_dome.png -->
```

CurseForge:

```html
<p>[IMAGE: screenshots-840/12_railgun_charge.jpg]</p>
<p>[IMAGE: screenshots-840/11_repulsor_dome.jpg]</p>
```

## 3. Turret bases: first paragraph

The list of heavy weapons for the Large Turret Base.

Modrinth:

```markdown
The standard Turret Base is one block and mounts on floors, walls or ceilings. For the heavy weapons (Missile Launcher, Tesla Coil, Railgun, Mortar and Repulsor Dome) there's the Large Turret Base, which takes a 2x2 spot on the floor and has more HP.
```

CurseForge:

```html
<p>The standard Turret Base is one block and mounts on floors, walls or ceilings. For the heavy weapons (Missile Launcher, Tesla Coil, Railgun, Mortar and Repulsor Dome) there's the Large Turret Base, which takes a 2x2 spot on the floor and has more HP.</p>
```

## 4. Ammo and automation: first paragraph

Nine ammo kinds (Rail Slugs, Mortar Shells) and the Feed Hub (incl. merging and FE).

Modrinth:

```markdown
Ammo comes in nine kinds (Kinetic Rounds, Scatter Shells, Sniper Rounds, Rockets, Missiles, Fuel Canisters, Laser Cells, Rail Slugs and Mortar Shells). Hoppers, Create belts, funnels and chutes or item pipes from other mods can all load a turret, and they can't put anything else in. Got Create installed? Then Mechanical Arms load turrets directly as well. Or mount turrets on the faces of a Feed Hub and feed just the hub: it hands the ammo out to every turret whose weapon takes it, evenly, and holds what doesn't fit yet. Place hubs together and they merge, up to 3x3x3, into one block with shared storage and an FE bank: Tesla Coils and Railguns standing on top charge from it, and large turrets on top get their ammo too.
```

CurseForge:

```html
<p>Ammo comes in nine kinds (Kinetic Rounds, Scatter Shells, Sniper Rounds, Rockets, Missiles, Fuel Canisters, Laser Cells, Rail Slugs and Mortar Shells). Hoppers, Create belts, funnels and chutes or item pipes from other mods can all load a turret, and they can't put anything else in. Got Create installed? Then Mechanical Arms load turrets directly as well. Or mount turrets on the faces of a Feed Hub and feed just the hub: it hands the ammo out to every turret whose weapon takes it, evenly, and holds what doesn't fit yet. Place hubs together and they merge, up to 3x3x3, into one block with shared storage and an FE bank: Tesla Coils and Railguns standing on top charge from it, and large turrets on top get their ammo too.</p>
```

## 5. Effects paragraph

Adds railgun ion trails and mortar fire patches.

Modrinth:

```markdown
Muzzle flashes, tracers, ejected casings, short bursts of dynamic light, camera shake, custom explosions with smoke, lightning arcs, laser beams, railgun ion trails, flame jets, mortar fire patches and scorch marks that grow on walls. Too much for your PC (or your stomach)? The client config has VFX presets LOW, MEDIUM and HIGH plus switches for camera shake and screen flash.
```

CurseForge:

```html
<p>Muzzle flashes, tracers, ejected casings, short bursts of dynamic light, camera shake, custom explosions with smoke, lightning arcs, laser beams, railgun ion trails, flame jets, mortar fire patches and scorch marks that grow on walls. Too much for your PC (or your stomach)? The client config has VFX presets LOW, MEDIUM and HIGH plus switches for camera shake and screen flash.</p>
```

## 6. For modpack makers: second paragraph

Server config: Mortar block fire, Feed Hub energy, Repulsor heavy mobs.

Modrinth:

```markdown
Weapon stats live in `data/bastion/turret_weapons` and modules in `data/bastion/turret_modifiers`. Both are plain datapack JSON, same as the workstation recipes. The server config covers HP and regeneration per tier, owner protection, whether mobs attack turrets, whether Mortar shells set blocks on fire, how much energy the workstations and Feed Hubs hold and take in, and which mobs count as too heavy for the Repulsors (add big mobs from other mods there).
```

CurseForge:

```html
<p>Weapon stats live in <code>data/bastion/turret_weapons</code> and modules in <code>data/bastion/turret_modifiers</code>. Both are plain datapack JSON, same as the workstation recipes. The server config covers HP and regeneration per tier, owner protection, whether mobs attack turrets, whether Mortar shells set blocks on fire, how much energy the workstations and Feed Hubs hold and take in, and which mobs count as too heavy for the Repulsors (add big mobs from other mods there).</p>
```

## 7. Requirements: the FE line

FE is also needed for the Railgun and the Repulsors.

Modrinth:

```markdown
- Any mod that produces FE, for the workstations, the Tesla Coil, the Railgun and the Repulsors
```

CurseForge:

```html
<li>Any mod that produces FE, for the workstations, the Tesla Coil, the Railgun and the Repulsors</li>
```
