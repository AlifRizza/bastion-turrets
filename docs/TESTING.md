# Manual test checklist (Fase 2–7)

Automated: `./gradlew runGameTestServer` (67 tests: base, combat, weapons, modifiers, filter, redstone, HP,
destruction). Visual: `./gradlew devClasses runClient -Pshowcase`. The list below is what still needs eyes and hands.

Setup: creative world, a turret base with a weapon module and Creative Ammo (never runs out). Targets:
`/bastiondev zombie` (500 HP, sunlight-proof), or `/bastiondev zombie 100 5` for five 100 HP zombies. Also `skeleton`
(with a bow, shoots back) and `ravager`; up to 5,000 HP and 200 at once, spread in a spiral (dev runs lift vanilla's 1,024 HP cap).
Damage numbers and the target's HP bar come from ToroHealth (dev runClient only). Vanilla zombies have 2 armour,
so a 7-damage shot lands as ~6.9.

## Weapons (Fase 2, 4–6)
- [ ] Gun Turret: tracks a moving zombie smoothly, cyan tracer + impact sparks, lock reticle appears after ~1 s on one target, the lock shot hits harder.
- [ ] Gun Turret does not shoot through a wall and stops with `No ammo` when empty.
- [ ] Machine Gun: barrels spin up before firing, spin whine follows the barrels, heat bar rises, overheat vents steam + alarm, targets get Slowness.
- [ ] Shotgun: 8-pellet spread, magenta muzzle shockwave, pump + shell ejection, heavy knockback, weaker at long range.
- [ ] Shotgun damage (beta.4): 5 per pellet (~4.9 on a zombie). Point blank, one blast of ~4-5 pellets kills a 20 HP zombie; at ~6 blocks only 1-2 pellets land.
- [ ] Sneak + empty hand removes the weapon; refused with a message while it is firing.
- [ ] `/bastion vfx <preset>` plays every preset (tab-complete lists them, incl. `turret_destroyed`, `tier_up`, `damaged_spark`).
- [ ] VFX quality LOW / MEDIUM / HIGH (client config) all look right.

## Model textures (0.2.0 UV fix)
- [ ] Machine Gun from behind: both fan blades (the cross) have the same shading, no odd light/dark pixel at the centre; the two cyan barrel rings glow evenly all round.
- [ ] Flamethrower: the small fuel pipe on top (between the tanks and the tube) looks like plain metal, no stray stripe.
- [ ] Part Workstation: all three drawers have a dark bottom edge (the bottom drawer too); the two wrenches on the tool board look alike.

## Camera shake (0.2.0)
- [ ] Stand 2-3 blocks from 3-4 Rocket Launchers firing at a crowd: the camera shakes only a little and no harder than with one launcher; the explosions still feel like a thump.
- [ ] Same next to 2 Repulsor Domes: each burst gives a short light shake, never a long strong wobble.
- [ ] Railgun, Mortar and Shotgun still shake as before when fired alone.

## Module tiers (0.2.0)
- [ ] Creative Modules tab: each module three times (Mk1, Mk2 cyan frame, Mk3 gold frame), tooltip first line "Mk1/Mk2/Mk3", names "... Mk2/Mk3" (also in Indonesian).
- [ ] Module Workstation, Range Module tab: "Range Module Mk2" needs the Mk1 + 2 diamonds + 4 redstone, "Mk3" needs the Mk2 + netherite scrap + 2 diamonds + amethyst shard; Choke Module tab has the Choke and Autoloader upgrades. The recipe list still scrolls.
- [ ] Turret with Range Module (Mk1): a Range Mk3 is refused (click and shift-click); a Damage Amplifier Mk2 goes in. (Automation never puts modules into turrets: their item port takes ammo only.)
- [ ] Hopper/chest above a Module Workstation set to Range Module Mk2, holding several Mk1 + diamonds + redstone, a hopper below: it keeps upgrading one after another (inputs never fill up with modules).
- [ ] Info tab with Range Module Mk3: range +50%; Overclock Mk3: turret HP -30%; Penetrator Mk3: a Gun Turret bullet hits 4 mobs in a line (pierces 3).
- [ ] Choke Mk3 (Shotgun) tightens the spread a lot but still spreads a little; Autoloader Mk3 refills rockets/missiles clearly faster.

## Mod compatibility (0.2.0): `-PwithCompatMods` (Fungal Infection: Spore 2.2.0j, TaCZ 1.1.8-hotfix2)
Automated: `./gradlew runGameTestServer --offline -PwithCompatMods` runs `CompatModTests` with the mods (without them those 4 tests pass at once).
- [ ] Spore: `/summon spore:inf_human` (or any infected) near a turret: the turret shoots it; an unarmed turret gets walked up to and hit (lights go yellow/red).
- [ ] Spore's Howitzer (user 2026-10-27): put an armed turret on a stone floor with stone round it, `/bastiondev spawn spore:howitzer 2000` some 15 blocks off. Its bombs, its landing stomp and its walking through blocks clear the stone round the turret, but the turret and the block under it stay; the turret loses HP from the blasts (it can still be destroyed that way). A wall or ceiling turret keeps the block it hangs on too.
- [ ] Hit boxes stay put (user 2026-10-27): F3+B, a ring of turrets, then a Spore swarm with Griefers or Volatiles (`/bastiondev spawn spore:vigil 500`): every white hit box stays on its turret through the blasts; no box floats in the air or stands on empty ground, and no mob crowds round empty air. Killing the swarm with `/kill @e[type=#spore:fungus_entities]` leaves only the boxes on turrets.
- [ ] TaCZ: shoot a turret that is not yours (or run `/bastiondev owndamage true` first to test on your own): it takes damage and breaks at 0 HP (weapon + ammo lost, Damaged Base drops). Afterwards `/bastiondev owndamage false`.
- [ ] TaCZ with owndamage false: shooting your own turret does nothing to it (the bullet stops at it, like an arrow).
- [ ] Crosshair still goes through the turret to its base block: right-click opens the GUI, mining works, you still cannot punch it.
- [ ] Flyers: `/bastiondev busser [hp] [count] [variant]` (Spore's flying Busser; variant default/kidnapper/kamikaze/noxious/transporter, only **noxious** shoots stingers, the rest fight in melee; without a variant it is default). Every turret that can aim up shoots it without hitting the ground or itself; the Mortar should ignore it (ground only); its stingers hit and damage turrets.

## Placement hologram (0.2.0)
- [ ] Hold each of Large Turret Base, Part Workstation, Part Assembler, Module Workstation, Ammo Workstation and aim at the floor: a see-through cyan model plus a cyan box per block it will fill; workstations show their front toward you. Click: it lands exactly there.
- [ ] Put a solid block in one of the cells: that cell's box and the whole model turn red; clicking does nothing. Tall grass or a snow layer there stays cyan (it gets replaced).
- [ ] A T3 Large Turret Base from creative shows its corner posts in the hologram; a T1 one does not. Placed T3 bases nearby keep their armour/posts.
- [ ] Aim a Large Turret Base at a wall or ceiling: no hologram. Turret Base, Charging Station, Feed Hub (1 block): no hologram.
- [ ] Main hand another block, off hand a workstation: no hologram (the main-hand block is what gets placed). Open a GUI: the hologram goes away.
- [ ] A mob, pet or you standing in one of the cells: that cell turns red and the click fails (no more stations built around a dog). Only the clicked cell itself is not marked red for an entity (vanilla refuses that click anyway). Dev scene: `-Pshowcase=placement -PshowcaseWorld=showcase_fx`.

## Health lights, creative bases, module tooltip (0.2.0)
- [ ] Damage a turret (`/bastiondev zombie`, or arrows): above half HP the base glows cyan; below half its tall window slits, energy ring (and T3 fin/post caps) turn steady yellow and the odd spark flies; below a quarter they blink red fast and spark often. The weapon module keeps its cyan glow throughout. No cyan showing through the yellow/red. Repair Kit: back to yellow/cyan. Dev scene: `-Pshowcase=health -PshowcaseWorld=showcase_fx`.
- [ ] Creative tab Turret Bases: Turret Base and Large Turret Base each appear three times (T1, tooltip Tier 2, Tier 3); a placed T3 shows armour + fins at once and full T3 HP.
- [ ] Module tooltip: "Hold Shift: ..." line; with Shift: Range Module "Works on every turret", Coolant Loop "Works on: Machine Gun Turret", Penetrator lists Gun, Machine Gun, Shotgun, Sniper, Railgun; Damage Amplifier "every turret except" the two Repulsors; Ammo Recycler skips Tesla and both Repulsors. In Indonesian too.

## Balance 0.2.0
- [ ] Mortar: one shell kills a plain zombie or husk standing where it lands; tankier mobs survive the blast and burn in the patch.
- [ ] Burn (Flamethrower and Mortar patch): one hit per second of ~3 (ToroHealth shows ~2.9 on a zombie); a zombie that leaves the Flamethrower cone after one touch keeps losing ~3 a second for 4 s (~12 HP).

## Bullets (travel time)
- [ ] Gun, Machine Gun, Shotgun and Sniper: the tracer visibly flies to the target (Sniper fastest, Shotgun slowest), the impact sparks appear when it arrives, not when the turret fires.
- [ ] A zombie walking sideways at 20+ blocks still gets hit: the turret aims ahead of it (with a Targeting AI module even more precisely).
- [ ] Stand a villager (or yourself, trusted) between a turret and a zombie: the bullets fly through the villager and hit the zombie. A wall still stops them.
- [ ] Shotgun knockback still throws a close zombie back; Sniper bullet still goes through 2 mobs in a line.

## Big bases (performance)
- [ ] Bases look exactly as before (they are now part of the world mesh): tier armour (T2) and fins (T3) appear right after a Tier Upgrade Kit, the energy ring still turns on the standard base (the Large Base ring stands still since 0.2.0), the glow still breathes and turns yellow/red when damaged.
- [ ] 50+ turrets in view: FPS stays playable; sounds don't cut out (only a few nearby shot sounds per tick play, near ones first); casings only fly from turrets within ~32 blocks.
- [ ] Dev load test (dev runs only): `./gradlew runClient -Pshowcase=stress -PstressCount=200 -PshowcaseWorld=<scratch copy>` logs `[stress]` MSPT and FPS for empty / idle / combat.

## Sniper & Rocket Launcher
- [ ] Sniper: locks on, a green laser sight grows thicker for ~1.5 s (with a charging whine), then one heavy crack; two mobs in a line both get hit.
- [ ] Sniper re-charges from zero when its target moves out of the sights or dies mid-charge.
- [ ] Rocket Launcher: rockets fly visibly (glow, motor roar), lead running mobs, and explode on impact without breaking blocks.
- [ ] Smoke trails: rockets leave a thick grey trail, missiles a thinner white one that follows their curve; it starts right at the glowing nozzle (no gap), runs straight into the explosion, which happens where the rocket meets the mob's body (not at its feet), widens, drifts up and fades over ~2 s; smooth bends, no flat shards; dark at night.
- [ ] Several mobs in range: when a target dies, the turret takes the next one at once. The Flamethrower's flame stays on while it swings to the next mob and only stops when nothing is left in range; the Machine Gun keeps firing through short swings (up to ~30°).
- [ ] Explosion: white flash + shockwave, rolling fireballs (white-hot to orange to red), flame tongues, black smoke that lingers ~5 s, dust ring on the ground, a scorch mark on the floor/wall that was hit.
- [ ] Tubes: four rockets sit in the tubes; each shot empties only its own tube, a new rocket slides in ~1 s later; with no ammo the tubes run empty.
- [ ] The blast never hurts you, trusted players, pets/passive mobs (unless targeted) or other turrets; it does not fire at mobs within 4 blocks.
- [ ] Ammo: Sniper Rounds and Rockets are craftable; Creative Ammo works for both.

## Large Turret Base & Missile Launcher
- [ ] Large Turret Base: click the top of a block; it fills a 2x2 forward and to your right, only if all four blocks are free. Walls/ceilings refuse it.
- [ ] Right-click, mine, hoppers and redstone work on any of its four blocks; breaking one drops one base (tier and contents kept).
- [ ] Missile Launcher fits only the large base (the small modules only the standard one); you get a message otherwise.
- [ ] Pods stay tilted up and only turn left/right; twelve missiles show in the tubes and disappear as they fire.
- [ ] Tubes reload one by one (1.5 s each, one Missiles item each); out of missiles the lamp goes red.
- [ ] Status tab "Mode" button: Single fires each missile as it loads; Salvo waits for all 12, ripples them out and spreads them over every target in range (one target gets all 12).
- [ ] Missiles fly up, curve down onto targets, follow moving mobs, pick a new target if theirs dies; never fire at mobs closer than 6 blocks.

## Tesla Coil & Flamethrower
- [ ] Tesla Coil fits only the Large Turret Base. It never turns; the coil rings glow and the crown spins slowly.
- [ ] Without power: red lamp, no zaps. Feed FE into any of the four base blocks (an FE mod's cable or generator); the Status tab's blue gauge fills (hover: `Energy x / 60,000 FE`), max 1,000 FE/t.
- [ ] Creative Ammo in an ammo slot powers it for free (creative testing without an FE mod).
- [ ] Charging (~0.6 s): arcs crackle off the crown with a rising whine; then up to 5 jagged blue-white bolts strike 5 random valid targets in range (16 blocks), with a flash, sparks and a crack. At most 2 bolts per target: 1 mob takes 2 bolts (20), 2 mobs 2 each, 3 mobs 2+2+1, 5 or more mobs 1 each. Struck mobs crawl with small arcs for a moment.
- [ ] Bolts follow moving mobs, never go through walls, never hit you, trusted players or passive mobs. 3,000 FE per discharge (gauge drops).
- [ ] Idle with energy: a small spark jumps off the crown now and then (faint crackle).
- [ ] Flamethrower: a solid jet of fire from the nozzle into billowing flames (~8 blocks), roaring while it fires; flames pile up and splash on walls. Each spot gets one scorch mark that glows while the fire is on it and widens the longer it burns (never a pile of marks); it cools and fades ~15 s after the fire moves on.
- [ ] Everything valid in the cone gets hurt and keeps burning ~4 s after leaving it (custom flames on the mob, it glows at night); passive mobs/you/trusted players in the cone are not burnt.
- [ ] Water or rain puts a burning mob out. Blazes and other fire-immune mobs are ignored (the turret stays idle, no fuel used).
- [ ] Fuel: 1 Fuel Canister lasts ~4 s of flame (`/give` or craft: iron + coal, makes 3). A blue pilot light flickers at the nozzle while it waits.
- [ ] Death messages: "electrocuted", "burned to a crisp", "burned to death".

## Railgun
- [ ] Railgun fits only the Large Turret Base. Big two-rail gun (~5 blocks long) on a yoke, drum magazine on its left; it turns slowly and tilts from -10° to 60°.
- [ ] Needs both: Rail Slugs in an ammo slot **and** FE in the base (gauge on the Status tab, 80,000 FE, 2,000 FE/t). Missing either: red lamp, no shot. Creative Ammo pays for both.
- [ ] Charge (3 s): the glow turns violet and the five coil bands light up one by one from the back to the muzzle; then arcs crawl over the whole barrel with a crackle and a rising whine; a violet laser sight shows where it will hit.
- [ ] Shot: rails slam back, flash + three rings ahead of the muzzle, camera shake, a violet trail with a spiral that hangs ~1.5 s; steam from the vents a moment later; the drum turns a quarter.
- [ ] Impact: flash, violet ring, debris; valid mobs within 4 blocks are thrown away (no extra damage); you, pets and passive mobs are not pushed.
- [ ] `/bastiondev zombie 100 3` plus a Wither (or any bigger mob): it shoots the biggest first, even when a smaller one is closer. 60 per hit (diamond armor makes no difference), 120 on the Wither. A boss hovering right above the gun (steeper than 60° or inside 5 blocks) cannot be hit.
- [ ] Never aims at mobs closer than 5 blocks. On a base raised a block or two (e.g. on Feed Hubs), it still shoots zombies on the ground 6-8 blocks out (barrel dips to -30°, was -10°), also on a superflat world.
- [ ] Watch a Railgun shot from the side in an open field: the violet trail ends at the mob it hits from the first frame (it used to flash past the mob for a tick). Info tab: Damage 60, Range 80, Energy per shot 8k, Ammo 1.

## Mortar
- [ ] Mortar fits only the Large Turret Base. Squat armoured housing on a turning ring, short fat barrel that always points steeply up (45-86°), also when idle or out of ammo.
- [ ] Mortar Shells in an ammo slot (or Creative Ammo). It shoots every 3 s at mobs on the ground 8 to 40 blocks away, also behind a wall (it lobs over it); never at flying mobs (phantoms, ghasts, blazes, bees, bats) or anything closer than 8 blocks.
- [ ] Shot: the barrel slams down, a flash and a smoke ring blown up, a shell with a glowing fuse and a smoke trail climbing ~20 blocks; it falls silently (no whistle since 10-17). No lead, no homing: it lands where the target stood when it fired, so a mob that keeps walking can get away (it is meant for crowds).
- [ ] Impact: a blast (20 damage close by since 0.2.0: kills a zombie outright, valid targets only), then the whole 3x3 burns for 5 s: glowing embers across the square and flames on every block (also on stone), light, crackle, a scorch left behind. Everyone standing in it burns, you, your pets and passive mobs too, and keeps burning ~3 s after stepping out. Fire-immune mobs don't burn; water and rain put it out.
- [ ] Burnable blocks in the patch (wood, leaves, wool, grass) catch real fire, which then spreads like vanilla fire. Server config `mortarBlockFire = false`: no block fire, mobs still burn.

## Feed Hub
- [ ] Mount turret bases on the Feed Hub's faces (sides, top, bottom): click a face with a Turret Base, the hub does not open its GUI while you hold a block.
- [ ] A hopper, Create belt/funnel/chute, pipe or Mechanical Arm into the hub: ammo goes to the turrets whose weapon takes it, the emptiest first (two guns end up with the same amount). Ammo no armed turret uses stays in the pipe/belt.
- [ ] Turrets full: the ammo waits in the hub and goes in as soon as they fire.
- [ ] A base merely standing next to the hub (its back not on it) and an unarmed base get nothing.
- [ ] Break the hub: its contents drop. Recipe at the Module Workstation; Bahasa Indonesia name "Hub Pengumpan".

### Feed Hub multiblock + energy
- [ ] Place hubs next to each other: a tick later they merge into one block with one frame and a cyan glow line around its outer edges, no seams between the hubs (2x2x1, 2x2x2, 3x3x1, 3x3x2, 3x3x3). A lone hub keeps its port-and-ring look. Extra hubs that don't complete a shape stay single (e.g. 5 in a row: a 2x2 can't form, they stay single).
- [ ] Break one block of a structure: only that block's 9 slots drop (its FE is lost), the rest re-forms (break a corner of a 3x3x1: a 2x2x1 plus four single hubs).
- [ ] Turret bases on any outer face of the structure get ammo; a Large Turret Base standing with all four blocks on top of a 2x2 or wider structure gets its ammo too. One standing half off the edge gets nothing.
- [ ] A hopper or pipe into any block of the structure fills the whole structure (a 3x3x3 holds 243 stacks).
- [ ] FE: cable or Creative Power Source into any block, up to 4,000 FE/t per block, 50,000 FE per block. A Tesla Coil or Railgun on top charges from it (least charged first). The hub never sends FE back into cables.
- [ ] Open the GUI from any block: **Summary** tab shows the size (e.g. "3x3x2 (18 blocks)"), turret count with weapon icons (hover = name), energy bar with numbers, ammo totals per type. **Storage** tab: 4 rows of slots with a scrollbar (mouse wheel or drag); a single hub shows 1 open row and 3 hatched rows. Shift-click from your inventory fills the whole structure, not just the rows on screen.
- [ ] While a hopper feeds a 3x3x3, scroll the Storage tab up and down: every row shows its own items, nothing flickers into the wrong row.
- [ ] Save and reload the world: shapes, slots and FE are kept.
- [ ] Server config: `feedHubEnergyCapacity`, `feedHubMaxInput`, `feedHubItemsPerTick` (all per block).
- [ ] Scroll the Storage tab to the bottom of a big structure, then break blocks until it is small: the tab jumps back to its first row (no hatched-only view).
- [ ] Creative Power Source + an energy pipe from another mod (dev: Pipez, set the pipe end at the source to extract): the pipe attaches and carries FE into a Feed Hub or workstation.
- [ ] Turret GUI fix: shift-click rounds onto an ammo stack already in a turret, and shift-click part of a stack out with a nearly full inventory; save, quit, reload: the counts stay as they were (no lost or doubled rounds).

## Turret Control Hub
- [ ] Recipes at the Module Workstation, own tab: 8 Hub Casings, Hub Power Port, Hub Interface, Satellite Uplink T1 (T2 from a T1, T3 from a T2), 4 Hub Connectors.
- [ ] Build the 3x3x3 cube: the Hub Interface facing you in the middle of the front side of the middle layer (eye level when you stand on the ground), Hub Casings everywhere else with at least one Hub Power Port on the outside, then a Satellite Uplink on the top middle block (its hologram shows both blocks). Before it is done, right-click the Interface with an empty hand: the action bar names the first problem; a hologram shows cyan boxes with the missing block turning inside (bottom layer first), red over a wrong block, amber on every casing while there is no port; boxes go as you place parts, all of it when the hub forms (10 s at most).
- [ ] Forming: a cyan sheet sweeps through the cube, a flash at the dish, the dish unfolds and turns (about one turn per 10 s) with a soft hum, the Interface screen shows a radar, cyan lines along every outer edge of the cube (none around the Interface), a chime. Without FE it is OFFLINE: red blinking screen, dish stopped and drooping, red glow, a power-down sound.
- [ ] FE: a cable or Creative Power Source on a Power Port (up to 2,000 FE/t per port; a port in no hub takes nothing). Upkeep: T1 20, T2 60, T3 150 FE/t (hover the FE bar). Run it dry: OFFLINE, and it stays OFFLINE without flickering even while a trickle of FE comes in; it comes back ONLINE once it holds 5 s of upkeep (T1 2,000, T2 6,000, T3 15,000 FE).
- [ ] Hub Connectors: shift + right-click the Interface with a stack of connectors: the whole stack is linked (action bar, tooltip "Linked to the hub at x, y, z", the icon's lamp lit); a plain right-click opens the hub screen instead (since 0.3.0); shift + right-click in the air clears the link (shift + right-click on a block, e.g. a turret, keeps it). Put one in a turret's Connector slot (Status tab, between the ammo and the modules; hover the empty slot for its name; hoppers never touch it).
- [ ] Only turrets with a connector linked to this hub show on the Map and List (header "N turrets"), within the satellite's reach: T1 its chunk, T2 3x3, T3 7x7 chunks (F3 + G). A turret without a connector, with an unlinked one, or linked to another hub does not show; with none: "No connected turrets..." on both tabs. Another player's connected turrets show only once they trust you; an op sees all.
- [ ] Click a dot or a List row: that turret's own GUI opens from the hub; weapon, ammo and modules go in from your inventory, Targeting and Info work; "◀ Map" brings back the hub screen with the same tab, zoom and selection. Walk more than 8 blocks from the Interface, or take the connector out: it closes.
- [ ] The hub screen nearly fills the screen (also in fullscreen and after resizing the window): a bigger map, more List rows, wider Batch columns; every text on it is one size (legend lines, names, buttons), nothing squeezed.
- [ ] Map controls, also on a MacBook trackpad: the + / - buttons in the map's corner zoom 1x / 2x / 4x; one wheel notch or one two-finger swipe = one zoom step; a left-drag (or right-drag) moves the map; a click without moving opens the turret.
- [ ] List tab: icon, name, coordinates, HP bar, ammo or FE %, state lamp per row; sort by distance / HP / ammo; the checkbox selects; the wheel scrolls.
- [ ] Batch settings: select turrets (Shift+click, Shift+drag a box, checkboxes, Select all), then change a category, the player mode, the priority, add or remove a trusted player, cycle a mob rule (none -> always -> never -> none), Turn on / Turn off: only that setting changes on every selected turret. Values that differ show "Mixed", names and rules only some have show "(some)".
- [ ] Break any part (bottom, middle or top layer, or the satellite): the hub stops (lines off, dish folds), open hub and turret screens close; only the broken block drops. Breaking the Interface loses its FE. A destroyed turret drops its connector with its modules; a mined turret keeps it inside.
- [ ] A world from before this update: every turret opens with an empty Connector slot, nothing else moved.
- [ ] Dedicated server (`runServer` + a client) and a second, untrusted player: they see none of your turrets on your hub. Save and reload: the hub is formed again, FE and connectors kept. Server config: `controlHubEnergyCapacity`, `controlHubPortMaxInput`, `controlHubUpkeep`, `controlHubRestartTicks`, `controlHubRange`.

## Rocket Launcher Salvo (2026-10-14)
- [ ] Turret screen of a Rocket Launcher: the Mode: Single / Mode: Salvo button is there (as on the Missile Launcher).
- [ ] Single: one rocket every ~1.8 s as before; each tube shows its rocket sliding back in shortly after the shot, and the clank lands as the rocket reaches the tube mouth.
- [ ] Salvo: the 4 rockets leave in the same instant, side by side (one flash per tube, one launch sound, one kick), at one target; the tubes then fill one by one (~1.8 s each, a clank as each seats); the next salvo goes a moment after the 4th rocket has slid in and clanked (~7 s cycle), never before.
- [ ] Switching Salvo -> Single mid-reload: it fires from the loaded tubes at once, no stuck turret.
- [ ] Missiles: the flight roar follows each missile all the way to a far target (30+ blocks) without an audible repeating pattern; a 12-missile salvo is not a wall of noise (3 roars at most). A rocket salvo's 4 roars sound like 4 motors, not one doubled.

## Sounds (real SFX, 2026-10-10)
- [ ] Gun, Shotgun: new shot, distant tail (stand 40+ blocks away), Gun lock beep, Shotgun pump (two quick strokes) and one shell bounce.
- [ ] Sniper (Omni, 10-16): the charge starts with a hiss as the green sight appears, then a buzz that grows brighter and runs straight into the shot (no gap); a sharp crack with a deep boom and a ~2 s metallic ring-out and echo; the two shots sound a little different; the ring-out doesn't drown the next charge. From 40+ blocks a muffled thump.
- [ ] Machine Gun: the barrels whir up, a few single shots while it spins up, then one solid barrage (no gaps, no clicks between shots) with the whir quietly underneath; after the last shot the burst decays and the whir winds down. Far away (40+ blocks): a distant crackle.
- [ ] Rocket Launcher (Omni audio, 10-14): a heavy clunk-and-roar launch, a big boom with a long rumble where it lands (the Mortar's blast uses it too, a little higher); Missile Launcher (Omni, 10-14): a pop-and-ignition launch, a smooth motor roar that follows each rocket or missile however far it flies (no pulsing, no clicks), a heavy blast; from 40+ blocks a muffled launch. Reloads: the Rocket Launcher clanks each time a rocket slides into a tube (8 ticks after each shot, and four in a row as it deploys); the Missile Launcher gives a short slide-and-clunk per missile loaded, with no hiss after it.
- [ ] Laser Rifle (Omni, 10-15): a rising electric hum for the whole 2.5 s charge that ends right as the beam fires (no gap, no overlap), then a heavy pulsing discharge with a ~1 s ring-out; the two shots sound a little different; from 40+ blocks a muffled thump.
- [ ] Flamethrower (Omni, 10-15): a short flare as the jet lights, then a steady deep roar with a gas rush on top that never pulses or repeats audibly over a long burst; burning mobs crackle quietly. When the burst ends the roar stops at once (no shut-off sound yet). Mortar fire patch: the same ignition, a little lower, and the crackle.
- [ ] Tesla Coil (Omni, 10-15): a crackling electric buzz builds for the 0.6 s charge and runs straight into a sharp crack-and-sizzle strike (two variants); back-to-back discharges don't pile up into noise. Idle coil: faint spark crackles now and then; from 40+ blocks a muffled thump. The same crackle on the Railgun's charge and on Workstations.
- [ ] Railgun (Omni, 10-16): a crackling power-up then a hum that grows brighter for the whole 3 s charge, with Tesla-style crackles on top once the arcs crawl over the barrel; it runs straight into a huge crack with an electric sizzle (two variants). Where the slug lands: a heavy crack-and-boom (two variants), also on a block. From 40+ blocks a muffled thump.
- [ ] Plasma Cannon (Omni, 10-16): a 1 s rising energy charge that runs straight into a deep plasma thoom with a sizzle (two variants); where the ball lands a big boom with a crackle and a rushing wash (two variants). Fast firing doesn't turn into noise.
- [ ] Repulsor (Omni, 10-16): a short rising suck-in during the 0.5 s charge, then a deep whoosh of air that swells and fades (two variants), no gap between them; from 40+ blocks a muffled whoosh.
- [ ] Repulsor Dome (Omni, 10-16): a rising suck-in during the charge, then a huge gust of wind rushing out that fades over ~3 s (two variants), no crack or bang; from 40+ blocks a muffled rush.
- [ ] Ion Rifle (Omni, 10-16): a sharp electric zap-crack with a sizzle, two variants alternating; steady fire every 0.8 s stays clear, no clicks.
- [ ] Mortar (Omni, 10-16): a deep hollow thoomp with a short ring as the shell leaves (two variants, one a little lower); from 40+ blocks a muffled thump. The shell falls silently (no whistle since 10-17).
- [ ] Early game (Omni, 10-27), each on a Makeshift Turret Base: Cobblestone (SFX generator), a light clack then a springy release as the rock leaves (two variants, one a little higher), nothing like a gate being rammed, gone before the next shot; Snowball Repeater, a ratchet click with a soft snowy fwump, a steady 5-per-second stream stays clean (no clicks, no pile-up); Potato Shotgun, a hollow deep pop with a short ringing pipe and a hiss; Arrow Launcher, a deep twang of several strings with a wooden knock (two variants, neither like the vanilla bow). Quieter than the Gun Turret, no clipping crackle. Listen without the game: `build/sfx-sheets/early_preview.wav`.
- [ ] Makeshift Turret Base (SFX generator, 10-27): placing it, and mounting a weapon on it, gives a dull wooden knock and a short creak (not the vanilla plank sound); when its HP runs out, a heavy crunch, splintering and boards clattering down, no explosion. Breaking it by hand still plays vanilla wood. Preview: `build/sfx-sheets/makeshift_preview.wav`.
- [ ] Arrow Launcher arrows (user 10-27): each arrow makes vanilla's thunk when it hits a mob or sticks in a block, and splashes in water; the other early projectiles stay silent on impact.
- [ ] 3x3 turrets (SFX generator, 10-27; previews `build/sfx-sheets/javelin_preview.wav`, `brood_preview.wav`, `hive_preview.wav`): Javelin, the silo doors hiss open with a deep clunk when it finds a target and slam shut when it goes idle (not on every launch); a huge launch roar, the engine roar riding up and dying away in the sky over ~2 s once the missile is out of sight (not cut off), a rising shriek on the dive, the biggest blast in the mod. Brood Cannon, a hollow pneumatic thoomp, a pod clanking down and hissing open; the spiders skitter quietly as they walk and climb (a dozen at once is not a wall of noise), snap when they bite, kick and whoosh on a leap, pop when they burst. Hive Carrier, bay doors and fans spinning up on launch, a light fan hum per drone, a fast rattle of shots (10 a second) that does not pile into noise with 3 drones, a sharp blast on a kamikaze.
- [ ] Plasma Repeater: three slightly different shots; Turret base: deploy, idle hum (loops without a click), repair, destroyed (a Large Turret Base sounds bigger).
- [ ] Volume: the new sounds sit about as loud as the turrets still on placeholders (Rocket, Tesla, ...).
- [ ] Zombies hit by turrets groan at most twice a second each and quieter than before (config `turretHurtSoundInterval` 10 ticks, `turretHurtSoundVolume` 0.5); their death sound is unchanged. No sound when a bullet, plasma ball or ion bolt lands (removed for now).
- [ ] `/bastiondev turrets [t1|t2|t3] [true|false]` (dev runs and the `-dev` jar): one armed base per weapon (Large Turret Base for the big ones) with Creative Ammo; place one and it fires. With `true` (user 2026-10-27) the names end in "(unlimited HP)" and the placed turrets take no damage: a ravager, a creeper or an owner with `/bastiondev owndamage true` never lowers their HP; broken with a pickaxe and placed again, still unlimited. Without it (or `false`) they take damage as before.
- [ ] `/bastiondev spawn <mob> [health] [count]` (user 2026-10-27): tab lists every mob, vanilla and modded (as `/summon`). `/bastiondev spawn minecraft:pillager 300 5`: five pillagers with crossbows, 300 HP each, no armour, adults; `/bastiondev spawn minecraft:zombie 300 10`: no baby zombies, no extra zombies when hit, all exactly 300 HP; `/bastiondev spawn minecraft:oak_boat` refuses (not a mob). `ai` and `knockbackimmune` apply to them too.

## Repulsor & Repulsor Dome
- [ ] Repulsor on a standard base (floor, wall, ceiling) with FE (Creative Power Source, cable, or a Feed Hub): turns to a mob even right at its feet, the three rings in its horn light up one by one while it charges, then a wave (rings + a sweeping air cone) throws every mob in front of it, up to 5 blocks out, ~4 blocks back in one push; dust at their feet. No damage.
- [ ] Put it behind a Gun Turret and let zombies hug the gun: the Repulsor pushes them off so the gun can shoot again.
- [ ] A mob behind a wall is not pushed by the Repulsor. You, pets and villagers in its cone are never pushed.
- [ ] Repulsor Dome on a Large Turret Base with FE: its ring spins up while it charges, then a white sphere of force grows out to 9 blocks (was 7 before 0.2.0) with a sand ring rolling on the ground, the screen shakes lightly (within ~10 blocks); every hostile within 7 blocks flies ~6 blocks away, also behind walls. No damage.
- [ ] Dome push by distance: a zombie right next to the dome flies ~12 blocks, one 8 blocks out ~7; anything in between lands in between (one more block per block closer).
- [ ] Without FE both sit in NO_AMMO and push nothing; 500 FE per Repulsor push, 2,000 per Dome pulse (GUI energy bar).
- [ ] Range Module / Rapid Cycler work; the Info tab shows damage 0. Recipes: 3 parts each at the Part Workstation, assembled at the Part Assembler.
- [ ] Heavy mobs (Iron Golem, Ravager, Warden): the Repulsor ignores them (does not fire, no FE spent, they stay put); the Dome throws them ~60 % less far (~4 blocks from 4.5 blocks out). Add a modded mob id to `repulsorHeavyMobs` in `config/bastion-common.toml`: after a restart it is treated as heavy too; `repulsorHeavyMobPush` changes the 40 %.
- [ ] Put the Repulsor one block behind a Gun Turret: mobs in front of the gun (~4-5 blocks from the Repulsor) are pushed.

## Laser Rifle
- [ ] Fits the standard base; Laser Cells as ammo (creative tab or `/give`, no recipe yet: they will come from a charging station later). Creative Ammo also works.
- [ ] Locks on, then charges ~2.5 s: light gathers at the emitter from a spark into a big red orb, sparks stream into it, the focus rings on the barrel spin faster, a rising whine; a lens flare flashes just before the shot.
- [ ] The shot: a thick red beam with energy flowing along it, a flash and kick at the emitter, sparks on every target it passes through, a glowing burn where it hits a wall. 1 cell per shot.
- [ ] Line up several mobs: one beam hits them all. A villager/pet/you standing in the line is not hurt. The beam stops at walls.
- [ ] Moving targets (also Sniper): the charge keeps going while the turret tracks a running mob; when full it fires as soon as the aim lines up. It only starts over when the target dies or leaves sight.
- [ ] The aiming laser during the charge comes straight out of the barrel and lands exactly where the beam then goes. Info tab shows Pierce `∞`.

## Ion Rifle, Plasma Repeater, Plasma Cannon
- [ ] Recipes: Empty Plasma Cell at the Ammo Workstation (2 iron, 1 glass, 1 glowstone dust -> 4); a Plasma Cell at the Charging Station (80 ticks, 4,000 FE). The three modules from 3 parts each (Part Workstation), assembled at the Part Assembler; the Plasma Cannon is a large module.
- [ ] Ion Rifle (standard base, FE from a Creative Power Source, a cable or a Feed Hub): a thick blue energy bolt with a glowing head (much thicker than Gun/Machine Gun tracers) flies to the target; where it hits, a white-blue flash and ring and straight blue beams to every mob within ~3 blocks for a moment; those mobs take damage too (the one hit takes 8, the others 5). A cow/villager/pet near the impact gets no beam and no damage. A mob behind a thin wall next to the impact is still zapped. No FE: NO_AMMO; 1,200 FE per shot (GUI energy bar).
- [ ] Plasma Repeater (standard base, Plasma Cells): big teal balls you can see fly with a short trail, 4 a second; each splashes on mobs right next to the one hit. Keep it firing ~6 s: the barrel glow turns teal, then red, it overheats (steam, vent flaps open, a hiss) and stops ~3 s. One cell lasts 10 shots. Coolant Loop helps (Shift tooltip lists it), Penetrator does not.
- [ ] Plasma Cannon (Large Turret Base, Plasma Cells): locks on, a green ball grows at the muzzle while the chamber glows green (~1 s), then a big slow ball flies out; where it lands a BFG-style explosion: a white flash, a flat green shockwave, a big green cloud ~4 blocks across each way that fades within ~1 s, sparks, a camera shake, a big green scorch on the ground. Every mob within ~4 blocks takes damage (more near the middle), also behind a wall; cows/villagers/pets don't. It never shoots a mob within 4 blocks. One cell per shot.
- [ ] All three at night: the balls, beams and flashes light up the area. With a Damage Amplifier the beams, splashes and the Cannon's blast hit harder too (the Info tab shows only the main Damage; compare how fast a mob dies with and without it).
- [ ] Swap the Plasma Cannon for another large module (e.g. Tesla Coil) right after it fires: the ball still lands and explodes, nothing crashes.
- [ ] Plasma Cannon into a mixed crowd (zombies, spiders, baby zombies): the blast hits all of them, short mobs too.

## Weapon-specific modules
- [ ] Choke Module (craft: nuggets, redstone, copper, hopper): goes in a modules slot only while a Shotgun is mounted (refused next to other weapons). Tooltip: "-40% spread", "Only for: Shotgun Turret".
- [ ] With it, the Shotgun's pellets land in a visibly tighter cone; Info tab Spread drops from 12° to 7.2°.
- [ ] Swap the Shotgun for another weapon with the Choke still inside: its slot turns red and it does nothing until a Shotgun is back.
- [ ] Autoloader (Module Workstation: nuggets, redstone, copper, piston): only fits next to a Rocket or Missile Launcher. Tooltip "+40% reload speed". Missile Launcher: tubes refill visibly faster, Info tab Reload 1.5s → 1.1s. Rocket Launcher: rockets come faster, Info tab Fire rate goes up.
- [ ] A second Autoloader (or any module already in the base) is refused by the other slots.

## Mounting: floor, wall, ceiling
- [ ] Place a base on a floor, on a wall and under a ceiling: it attaches to the clicked face and the weapon sits on its far end.
- [ ] Wall and ceiling turrets track and kill mobs (wall: straight out from the wall too; ceiling: below it).
- [ ] Casings, muzzle flashes and tracers come out of the barrel in every orientation.
- [ ] Mining a wall/ceiling turret drops it like a floor one; zombies attack wall/ceiling turrets too.

## Tiers, HP, repair (Fase 7)
- [ ] Tier Upgrade Kit T2 on a T1 base: cyan ring + armour plates appear, more ammo/module slots unlock. T3 kit on T2: cooling fins.
- [ ] T3 kit on a T1 base is refused with a message.
- [ ] Right-click / mine works from any angle, also aiming at the weapon on top.
- [ ] Arrows, explosions and hostile mobs lower HP (Status tab, left bar); a sword swing mines the block instead. Below 30%: glow blinks red, sparks.
- [ ] Zombies walk up to a turret and hit it from the side (never stand on an armed one); a nearby survival player is still chased first. Off with `mobsAttackTurrets=false` in `config/bastion-common.toml`.
- [ ] Repair Kit restores 40% HP.
- [ ] At 0 HP (0.2.0): custom blast (no vanilla explosion); the weapon module and the ammo are gone, only the modules drop, plus a Damaged Turret Base whose tooltip shows the tier it had (T3 turret: "Tier 3").
- [ ] Damaged Turret Base + Repair Kit at the Part Assembler gives a Turret Base of that tier back (T3 stays T3, armour and fins on when placed). Same for a Damaged Large Turret Base (two kits).
- [ ] Mining your own base with a pickaxe still keeps everything (weapon, ammo, modules, tier).

## GUI & settings (Fase 7)
- [ ] Status tab: HP bar, heat bar, state lamp under the weapon (green idle, cyan tracking, amber firing, red blinking = overheat/no ammo; hover any of them for details), ON/OFF toggle.
- [ ] Targeting tab: category toggles, player mode, priority (nearest / lowest HP / highest threat), redstone mode.
- [ ] Trusted list: type a name, `+` or Enter adds it, click a name to remove it.
- [ ] Mob rules: search `cow`, click once = always target, twice = never, three times = back to default.
- [ ] Info tab: stats change when a Range Module / Overclock is inserted.
- [ ] Redstone block next to the base disables it; after switching "Turns it on", only a powered turret fires.
- [ ] Turret Configurator: sneak + right-click copies targeting, right-click another turret pastes it.
- [ ] Another player (second client) cannot open the GUI or remove the weapon of your turret; trusted players can.

## Workstations (survival crafting, Fase 9)
- [ ] The crafting table makes the 5 stations (Part Workstation needs a smithing table, the Part Assembler a Part Workstation and a diamond) and, since 0.4.0, the early gear: the Makeshift Turret Base and its four turrets. No other turret, module or ammo recipe there.
- [ ] Placing: the station grows to your right, away from you and up from the block you click, its front facing you; it refuses if any of its blocks is taken. Breaking any block breaks it all and drops it once (with its contents).
- [ ] Power: a Creative Power Source (creative tab) next to any of its blocks, or any FE cable; the bar top right fills (hover for FE). Without FE nothing is made ("Needs FE").
- [ ] Part Workstation: pick a tab (each turret/base) and a part, put the materials in (or let a hopper/belt/arm do it: only that recipe's materials go in), it machines it over a few seconds (carriage runs, spindle spins, sparks, hum), part in the output (hoppers can pull it).
- [ ] Parts show in 3D as pieces of their turret. Part Assembler: the three parts in, the finished module/base turns on the deck while the arms weld and the scanner sweeps, then appears in the output. Also restores damaged bases (with repair kits).
- [ ] Module and Ammo Workstations: Craft button makes it instantly from the input slots and your inventory (shift: x8); with materials piped in they also make it by themselves over time.
- [ ] Charging Station (1x1x1): Empty Laser Cells (from the Ammo Workstation) in, charged Laser Cells out, 3s and 3k FE each; arcs jump from the coil tips into the cell while it charges.
- [ ] GUI: tabs, list with scroll, turning preview (flat icons face you), ingredients with have/need in green/red, progress bar, status text, no overlapping text.

## Creative tabs
- [ ] Six Bastion tabs in this order (Forge puts mod tabs on the creative screen's next pages): Turret Bases (bases, damaged bases, repair and upgrade kits, configurator; the Makeshift Turret Base once, with no T2/T3 copies), Weapon Modules (the four early turrets at the end), Ammo (incl. Creative Ammo; no vanilla items), Modules (incl. Choke Module), Workstations (the 5 stations + Creative Power Source), Parts (all 33, grouped per base/weapon).
- [ ] No item shows up in two Bastion tabs; Bahasa Indonesia names: "Bastion: Workstation", "Bastion: Part".

## Persistence & servers
- [ ] Leave and rejoin: tier, HP, filter, trusted list, ON/OFF, redstone mode, inventory all kept.
- [ ] Mine a T2 base with stuff inside: the item keeps tier + contents (tooltip), placing it restores them.
- [ ] `runServer` + separate client: everything above works on a dedicated server, no errors in either log.

## Turret stats config (bastion-turrets.toml)
Put every value back to its "Default" number when done (or delete the file): the GameTests use the same `run/config` file (`runConfigLeavesEveryValueAtDefault` names any number left there).
- [ ] Start the game once: `config/bastion-turrets.toml` appears next to `bastion-common.toml`, with the explanation at the top, one section per turret (`[gun]` first, `[hive_carrier]` last) and every value written as the mod's own number (`damage = 24` under `[sniper]`, no `.0`), with one comment line above it: what it does, then "Default 24, allowed 0 ~ 10000".
- [ ] Condensed file (2026-10-28): about 800 lines, not 2,100. `[tesla]` has 9 keys (no `turn_speed`, `spread`, pitch, heat or `ammo_per_shot`); only `[machine_gun]` and `[plasma_repeater]` have the 4 heat keys; `[missile_launcher]` and `[javelin]` have no `min_pitch`/`max_pitch`; `[hive_carrier] damage` says "Damage per drone bullet" and `unit_cap` speaks of drones. No comment starts with a turret's name. An old file keeps every number you changed; its unused lines are gone after the first start.
- [ ] With a world open, set `[sniper] damage = 100` and save the file: within a few seconds (up to about 10 on macOS), a Sniper's Info tab shows 100 and it kills a Witch (26 HP) in one shot, not two. Type 24 back and save: 24 again. No restart.
- [ ] Set `[railgun] charge_ticks = 10` and save: the Railgun charges for half a second.
- [ ] Set `[gun] damage = -5` (or `"abc"`) and add a line `ammo_per_shot = 1` under `[ion_rifle]`, then restart: the log warns, `[gun] damage` is 7 again, the `ammo_per_shot` line under `[ion_rifle]` is gone, and both turrets still fire.
- [ ] Set `[ion_rifle] energy_capacity = 5000` on a full Ion Rifle: its energy bar shows 5,000 at most and it still fires (4 shots of 1,200 FE per full bar).
- [ ] Set `[plasma_cannon] damage = 0`: mobs still take the blast damage (only the direct hit's own damage is gone) and still die normally.
- [ ] LAN / dedicated server: change the server's file; a second player sees the new numbers in the Info tab. Changing the client's own file changes nothing on that server.

## JEI & Jade
Run `./gradlew runClient --offline` (JEI and Jade come with the dev run; `-PnoDevMods` leaves them out).

**JEI**
- [ ] Press R on a Gun Barrel: the Part Workstation page shows 3 Iron Ingot + 1 Copper Ingot with their counts, 5 s · 3000 FE (30 FE/t), the Part Workstation as catalyst. All 5 stations have a tab; the Module and Ammo Workstation pages say they also craft instantly.
- [ ] Press U on Kinetic Rounds: the Turret Weapons pages of the Gun, Machine Gun (and every turret that fires them) show up. Each page shows the weapon, the base it mounts on, its ammo (FE weapons: "Runs on FE"), the T1 stats exactly as in a turret's Info tab with that weapon, and "Ability: hover the weapon" (hovering the weapon slot shows it); no text overlaps or runs past the page, also in Bahasa Indonesia (the Mortar page has the longest text).
- [ ] Change `[gun] damage` in `config/bastion-turrets.toml` while playing, reopen the Gun's JEI page: the new number shows. Put it back to 7.
- [ ] Press U on a Turret Base, Repair Kit, Hub Connector, Creative Power Source: an Information page with the text from the lang file.
- [ ] Open an Ammo Workstation with copper, gunpowder, iron nuggets and one Kinetic Rounds in your inventory, hover the Kinetic Rounds in your inventory slots inside the station GUI and press R (recipes; U would list the turrets that fire them), click + next to the recipe: the recipe is picked in the GUI and one of each moves into the inputs. Shift-click +: as many as you have. Remove the gunpowder and look at +: red, the gunpowder slot marked; clicking does nothing. On a Part Workstation page with the Ammo Workstation open there is no + button.

**Jade**
- [ ] Look at a Gun Turret: "Gun Turret · T1", HP, State; hold Shift: Owner (your name), Modules, Heat, Target while it shoots. Ammo shows as items (Jade's own), an energy weapon shows an FE bar, a non-energy one shows no FE bar.
- [ ] Look at each block of a Large Turret Base, a Part Assembler, and a Control Hub's casing, Power Port and satellite: the core's info (turret, station, hub), not the part's.
- [ ] Workstation: "Pick a recipe", then "Making: …", then "Working N%" while it works, with its FE bar. Feed Hub: "Feeds N turrets". Control Hub: Online / Offline / Not formed, the satellite line with the connected turret count (updates within a second), FE.
- [ ] Jade's plugin config lists Bastion's four switches; turning "Turret" off removes the turret lines.

**Without them**
- [ ] `./gradlew runClient --offline -PnoDevMods`: the game starts, workstations and turrets work, no errors mentioning jei, jade, mezz or snownee in `run/logs/latest.log`.
- [ ] `./gradlew runServer --offline` (the dev server has neither JEI nor Jade): it starts with no such errors; the dev client joins and sees the JEI pages, while Jade shows only its own lines (no Bastion lines without Jade on the server).

## Overwatch Link

Run `./gradlew runClient --offline` (add `-PwithCompatMods` for the camera/compat check at the end).

Setup: a formed Control Hub (T2+), online. Two Gun turrets, a Mortar and a Tesla Coil in reach. Each gets an Overwatch Link linked to the hub (shift + right-click the Interface with the Link first). One more turret gets a plain Hub Connector.

1. **It loads:** the game reaches the title screen and a world (the Camera mixin applies). The Link shows in JEI with its recipe (Module Workstation) and info page.
2. **Hub button:**
   - On the Map tab, shift-click one Gun turret. The side button reads "Take control" (with none or two selected: "Select one turret"; the plain connector turret: "Needs Overwatch Link").
   - Hovering a Link turret's dot shows "Overwatch Link".
3. **Third person:** press Take control.
   - The screen closes and the camera sits behind the turret. Mouse looks around, and the barrel follows at once.
   - The HUD shows: brackets, REC, time, turret name, distance to the hub, HP/ammo/heat bars, hints. No vanilla hotbar, hearts or hand.
   - Your own body is visible at the hub if you look back.
4. **Fire:**
   - Hold LMB on a cow: the Gun fires and hits it. Ammo goes down in the HUD.
   - Fire at your own spot at the hub: you take no damage.
5. **F5, zoom, switch:**
   - F5 switches to the barrel view (no weapon model in the way).
   - Scroll zooms 1x–4x.
   - ← / → jumps to the other Link turrets and skips the plain connector turret.
6. **Mortar:**
   - On the Mortar, glowing dashes arc from the barrel to the ground with a ring at the end. Close to the base (inside 8 blocks), the arc and ring turn red and LMB does nothing.
   - Fire: the shell lands in the ring.
7. **Tesla Coil:** the crosshair says "VIEW ONLY". The coil keeps zapping hostiles by itself.
8. **Letting go:**
   - Shift returns you to your body.
   - Take control again, then get hit (a zombie, or `/damage @s 1`): control ends.
   - Take control, then break the hub's Power Port so it goes offline: control ends.
9. **Others see it** (`./gradlew runServer --offline` + this client, optional): a second player looking at a controlled turret sees its barrel follow the controller, and Jade shows "Manual: <name>".
10. **Compat** (`-PwithCompatMods`, Embeddium/Oculus): the camera, HUD and arc still draw. Note any shader issue.
11. **Ceiling and wall turrets** (fixed after review): take control of a Gun on a ceiling and one on a wall. Third person looks out from below/beside it and the barrel follows the crosshair (not into its own base).
12. **Switching** (fixed after review): control turret A, press → to B, then look at A from B. A's barrel tracks targets again; it is not frozen where you left it.
13. **Shots in flight** (fixed after review): with the Missile Launcher, lock a cow and fire, then press Shift before the missile lands. It still hits the cow.
14. **F5 and HUD** (fixed after your test): F5 looks out like a sight just over the muzzle; nothing of the turret blocks the view, and the shots (also the Gun's tracers) fly out from under the crosshair. At GUI scale 3–4, no HUD text runs off the screen (the hints wrap onto two lines).
15. **Sneak: Toggle** (Options > Controls, fixed after review): one Shift press leaves, the body is not left crouching, and the next session also leaves with one press.
15b. **Aim on a mob's body** (fixed after your test): put the crosshair on a zombie's chest. The barrel aims there, not at its feet.
15c. **Right-click on the hub** (QoL after your test): right-click the Interface holding anything (a base, a weapon module, a Hub Connector) and its screen opens. Shift + right-click links a Hub Connector or Overwatch Link, and places a block you hold.
15d. **Override while off** (after your test): switch a Gun off (its GUI switch or a redstone torch), take control: it aims and fires. Let go: it is off again.
15e. **Machine Gun off mid-burst** (old bug): switch a firing Machine Gun off. The barrels wind down and stop within ~1.5 s.
15f. **Longer reach under control** (after your test): a controlled Gun (range 32) hits a zombie ~45 blocks away; on its own it would not. Config `overwatchRangeMultiplier` (default 1.5).
15g. **Sky view** (after your test): third person sits ~3 blocks above the turret and 4 back; indoors under a low roof it stays below the roof.
15h. **F5 really clear** (root cause fixed): F5 on the Railgun and the Gun: no part of the weapon in view at all.
15i. **Shot marker** (sky view): a small amber square shows where the shot lands along the barrel. On open ground it sits on the crosshair; aim at a mob behind a wall the turret cannot see over, and it stays on the wall.
15j. **Crosshair and RELOAD**: the crosshair is thin at GUI scale 3–4. Sniper, Mortar, Railgun, Shotgun show a RELOAD bar that refills after each shot; Rocket and Missile Launchers show their loaded tubes.
16. **Dev toggles** (`/bastiondev`): `ai false` makes every test mob stand still (and new ones from `/bastiondev zombie`); `ai true` brings them back. `knockbackimmune true` stops Shotgun, Repulsor and blast knockback on them (mobs on the heavy list still get the Repulsor's heavy push); `false` undoes it.

## Overwatch: launchers (2026-10-22)

Setup: a Control Hub as in "Overwatch Link", a Rocket Launcher and a Missile Launcher (Large Turret Base) with an Overwatch Link each, ammo in both. Zombies and a cow in range (`/bastiondev zombie`, `/bastiondev ai false` keeps them still).

1. **R switches mode**: control the Rocket Launcher, press R. The HUD's MODE row flips SINGLE / SALVO. Leave, open the turret's GUI: it shows the same mode. R on a Gun does nothing.
2. **Click speed**: Rocket in SINGLE with TUBES 4/4. Click 4 times fast: 4 rockets leave about as fast as you click (on a laggy server too: no click on a loaded tube is lost). Click again at once: nothing. When one tube reloads (TUBES 1/4), a click fires it right away.
3. **Holding**: hold attack on a loaded Rocket: one rocket only.
4. **Rocket Salvo**: in SALVO the crosshair is red until TUBES shows 4/4 and the last rocket has slid in; a click then does nothing. Once white: one click, all four leave together. In SINGLE with all four spent the crosshair is red too.
5. **Missile Single**:
   - Crosshair on a zombie: amber brackets frame it. Click: the missile goes for it.
   - Aim at empty ground with a zombie and a cow in range: the missile goes for the zombie, never the cow, even when the cow is in its way.
6. **Missile Salvo locks**:
   - Click one zombie 3 times: brackets with ×3, and LOCK 3/12. You hear a click per lock.
   - Right-click: ×2, LOCK 2/12.
   - Click empty sky: no lock.
   - Lock 12 in total over 2–3 mobs: all 12 launch once TUBES shows 12/12, each mob gets its count.
7. **Lock gone**: lock a zombie 12 times while the tubes are still loading, then kill it with another turret before the launch. The missiles go for another hostile, not a cow.
8. **Locks clear**: with some locks, press R, or ←/→ to another turret and back: LOCK is gone.
9. **Numbers on every turret**: HP `now/max`, AMMO an item count, FE `12.4k` style, TUBES on launchers; HEAT, CHARGE, RELOAD in %. At GUI scale 3–4 nothing runs off the screen (the hints may wrap to two lines).
10. **Automatic mode unchanged**: let go. The launchers fire on their own as before (Rocket Salvo: all four; Missile Salvo: twelve spread over the targets).
11. **Ammo out before 12**: Missile in SALVO, take the Missiles out while TUBES shows e.g. 7/12, lock 12: nothing launches (TUBES 7/12, AMMO 0). Press R: Single, each click fires one.
12. **Rocket still sliding in** (review fix): in SINGLE fire all four, then click fast as one tube reloads: it fires only once its rocket has slid all the way in (the clank).

### Minor fixes (2026-10-22, also from the 0.3.0 review)
1. **←/→ keeps the view**: F5 and zoom 3x on one turret, → to the next: still F5 and 3x.
2. **No Jade popup** while controlling (Jade installed: `-PwithCompatMods`); it comes back after leaving.
3. **Zoom sensitivity**: at 4x the mouse turns the view about 4 times slower than at 1x; after leaving, normal speed.
4. **Holding R**: hold R for two seconds: the mode flips once.
5. **Brackets follow the server**: a zombie behind a wall the launcher cannot see over gets no bracket and cannot be locked (Single: the missile goes for a visible hostile). Your own body at the hub never gets one.
6. **Creative players** are never hit by controlled shots, PvP on or off (needs a second player, `runServer` + 2 clients).
7. **Letting go mid-salvo**: launch a Missile Salvo, press Shift during it: the rest stay in their tubes.
8. **Hub hint** at the smallest hub screen (GUI scale 4, small window): the map tab's hint shows all its lines (smaller print), none cut off.
9. **HUD**: in Missile SALVO the hint line says "lock" for the attack key; HP number moves with the HP bar.

## Early game turrets (0.4.0)

**First**: check that `run/config/bastion-turrets.toml` has `[arrow_launcher] shell_speed = 1.4` (reset from a stale
2.2 on 2026-10-23) and `min_pitch = 40` (was 20, 2026-10-23 13:00). With 2.2 the Arrow Launcher arcs about 38 blocks
high, never fires under a low roof, and 6 GameTests fail; with 20 it rests nearly level.

Setup: a world with a few Makeshift Turret Bases, the four early turrets and their ammo. Targets: `/bastiondev zombie`
(`/bastiondev ai false` keeps them still). Vanilla zombies have 2 armour, so ToroHealth shows 3 as ~2.9, 2 as ~1.9 and
1.5 as ~1.4.

1. **Recipes** (crafting table; JEI R on each item). Key: C cobblestone, I iron ingot, L any log, P any planks,
   S string, K stick, B snow block, O potato, H leather, X crossbow. Every one needs iron.
   - Makeshift Turret Base `CIC / LIL / CIC`
   - Cobblestone Turret `CSC / ICI / P P`
   - Snowball Repeater `KBK / IPI / PIP`
   - Potato Shotgun `IOI / IHI / P P`
   - Arrow Launcher `PXP / IPI / _I_` (`_` = empty)
2. **Base on floor, wall, ceiling**: place one on each. It reads as a plank crate on a stone plinth with iron straps,
   rivets and rope. Wood creaks when it is placed and when a weapon is mounted. Nothing glows, by day or by night. An
   axe or a pickaxe mines it.
3. **Base screen**: right-click it. The title is "Makeshift Turret Base" with no T1; no Modules label, no module or
   Connector frames, only the 3 ammo slots (no hatched locked frames). Hovering where the Connector slot was shows
   nothing.
4. **Locks**:
   - Each early turret mounts on it (right-click with the module); a Gun Turret is refused.
   - An early turret on a standard or Large Turret Base is refused: "This module fits a Makeshift Turret Base only".
   - A Tier Upgrade Kit on it is refused ("A makeshift base cannot be upgraded") and stays in your hand.
   - Turret Configurator copy/paste, ON/OFF and redstone work as on a standard base.
5. **Arrow Launcher, floor only**: on a wall or ceiling base it is refused ("This module fits a base on the floor
   only"), by right-click and by shift-click in the screen. Mount it on a floor base, mine the base, place the base on a
   wall: the launcher pops off as an item.
   An empty wall or ceiling base takes no arrows (hopper, shift-click) but still takes stones; a floor base takes arrows.
   Spectral arrows: Glowing lasts `glow_ticks` (200 by default).
6. **Cobblestone Turret**: cobblestone or cobbled deepslate, one per shot, a shot every 0.75 s, reaches 16 blocks,
   ~2.9 per hit. Look: a short hooped cannon with a stone in its mouth and a spring behind; when it fires the barrel
   kicks back into the spring, the stone leaves and a new one appears. In flight a grey rock tumbles with a thin dust
   wisp; chips and dust where it hits. Sound: a spring thunk and twang.
7. **Snowball Repeater**: 5 shots a second, one snowball for 4 shots (16 snowballs = 64 shots, about 13 s), reaches 14
   blocks, ~1.4 per hit. Look: four barrels on a drum, a snow hopper on top, a crank at the back, a gear on the left.
   The drum turns a quarter and the crank once per shot, and keeps turning through a burst (no stop-go). Cold vapour at
   the muzzle, a white clump with faint frost in flight, a snow burst with white flakes on a hit.
8. **Potato Shotgun**: a shot every 1.5 s, 5 chunks in a wide cone, reaches 10 blocks, ~1.9 per chunk. Poisonous
   potatoes fire too and poison nothing. Look: a fat iron pipe, a dark pressure tank and a hand pump with a T-handle;
   the pump goes down and up after each shot. Tumbling potato chunks, a flat splat ring and tan bits on a hit.
9. **Arrow Launcher**: a zombie 6 to 32 blocks away. One arrow item = one volley of 6, every 2.5 s. The volley climbs
   about 14–20 blocks and lands about 2.25–2.75 s after it leaves: one arrow at the zombie's feet, five on a ring about
   1.5 blocks around it, ~2.9 per arrow.
   - It ignores flyers (a bat, a phantom) and anything closer than 6 blocks.
   - A cow or villager next to the zombie is never hit; the arrows fly through them.
   - The arrows cannot be picked up and vanish 10 s after they stick.
   - Look: a hwacha. A wooden box with a grid of 20 holes in its face, six arrows (2 rows of 3) sticking out of it, on
     a cart with two spoked wheels and two pull shafts; a small bow on the box's back corners, strings to a slider, a
     windlass at the back. When it fires, the strings snap straight, the holes empty, the windlass winds back and six
     new arrows slide in from behind.
   - With no target it sweeps left and right with the box tilted about 40° up, never level. In your hand, the
     hotbar and JEI it is tilted the same way.
     String-snap streaks at the rack, a pale wind streak behind each arrow, a little dust where one sticks.
10. **Arrow kinds**:
    - Tipped (e.g. Poison): the zombie gets the effect; a mist in the arrow's colour follows the arrows, with no vanilla
      swirls on the arrows. The zombie itself shows the effect's usual swirls while it lasts, as with a potion.
    - Spectral: the arrows are drawn yellow (spectral texture) and the zombie glows for 10 s.
    - Creative Ammo: plain arrows.
11. **Into water**: a volley that lands in water makes no bubbles and no splash.
12. **Your own arrows unchanged**: shoot a tipped arrow and a fully drawn (crit) shot with a bow yourself: the vanilla
    swirls and crit sparkles still show (the arrow mixins touch turret arrows only).
13. **Mixin log**: after a session with launchers firing, search `run/logs/latest.log` for `ArrowMixin` and
    `AbstractArrowMixin`: no warning or error. Both are optional mixins: if one fails to apply, the game still runs and
    the vanilla swirls or bubbles quietly come back.
14. **Missed sling shot**: a rock, snow clump or potato chunk that misses and runs out of range fades out where it
    stops over a few ticks; it does not blink out.
15. **Night**: no idle hum, no glow and no light from the base or the four weapons; no muzzle flash, no smoke wisp after
    a burst. The shots and the arrows' wind streaks are dark at night (lit by their surroundings, not glowing); next to
    a torch they light up. A Rocket Launcher's or Mortar's smoke trail at night is dark too (only the exhaust glows).
16. **Cracks**: let mobs hit a base, or `/data` its Health. Below 75 % a few thin cracks, below 50 % more cracks and a
    brown splinter now and then, below 25 % heavy cracks. No coloured health lights at any HP. Leave and rejoin (or walk
    100+ blocks away and back): the cracks are still there. The heaviest stage looked like dark blotches more than
    cracks in the first screenshots: tell me how it reads to you.
17. **Destroyed**: let a base reach 0 HP. It bursts into planks, scraps and dust with a wood crunch, with no amber flash
    or ring, and no blast: you, a pet or a cow standing next to it take no damage and no knockback. It drops 1 iron
    ingot and 2 oak planks and no Damaged base; the weapon and ammo are gone.
18. **Mined**: mine an armed base with ammo in it (axe or pickaxe): the item keeps the weapon and ammo; placing it puts
    them back.
19. **Automation**:
    - A hopper into an empty makeshift base: cobblestone, snowballs, potatoes and arrows go in; Kinetic Rounds do not.
    - With a Cobblestone Turret mounted, only cobblestone and cobbled deepslate go in.
    - A hopper with cobblestone into an empty standard Turret Base: nothing goes in.
    - A makeshift base with a Cobblestone Turret on a Feed Hub face, cobblestone in the hub: the turret gets fed.
    - A comparator reads the ammo level.
20. **JEI**: U on cobblestone, a snowball, a potato or a plain arrow opens that turret's Turret Weapons page: base Makeshift
    Turret Base, HP 50, the ammo and the stats. U on the Makeshift Turret Base: its information page (also in Bahasa
    Indonesia, where it names the Pelontar Panah).
21. **Jade**: look at a makeshift base with a Cobblestone Turret: "Cobblestone Turret" with no "· T1", HP `now/50`, the
    state.
22. **Bahasa Indonesia**: Base Turret Darurat, Turret Batu, Pelontar Bola Salju, Shotgun Kentang, Pelontar Panah.
23. **Dedicated server** (`runServer` + a client): the four turrets fire, arrows draw (spectral ones yellow), no errors
    in either log.

## Fortress Turret Base (0.5.0)

The Fortress takes only huge (3x3) weapon modules: the Javelin Missile Turret (next section) is the first; the Brood
Cannon and Hive Carrier follow in 0.5.0. Weapon steps (firing, sparks, the Overwatch fire) are in the Javelin section.
Visual reference: `-Pshowcase=fortress` (`run/screenshots/showcase_fortress_*`).

Setup: creative world; the bastion tab has the Fortress Turret Base as T1, T2 and T3 after the Large ones.

1. **Recipes** (JEI R on each item):
   - Fortress Frame, Part Workstation, Large Turret Base tab: 4 Iron Block + 1 Diamond + 1 Redstone Block, 10 s,
     8,000 FE. It sits next to the Large Base parts in the parts tab.
   - Fortress Turret Base, Part Assembler, Turret Base tab: 1 Fortress Frame + 2 Heavy Base Plating + 1 Heavy Base
     Housing + 1 Heavy Servo Turntable, 20 s, 25,000 FE. The Large Turret Base still takes one of each heavy part.
   - Fortress Turret Base from 1 Damaged Fortress Turret Base + 3 Repair Kits, 15 s, 12,000 FE.
2. **Items**: in the hotbar the Fortress is a small 3D platform with cyan strips (T3 shows its fins); the Fortress
   Frame shows the octagonal foot and the four corner plates; the Damaged Fortress Turret Base is a flat stepped sprite.
   Hold the Fortress: the hand shows part of the platform, like the Large base. Tooltips: "3x3, floor only. For huge
   weapon modules"; the damaged one names the three Repair Kits and keeps its tier line.
3. **Hologram**: hold it and point at the top of a floor block. The 3x3 is centred on the block under the crosshair,
   cyan boxes with the platform inside. Put a block or a mob (a cow) in any of the 9 cells: that box turns red and the
   platform too; placing does nothing. A wall or ceiling face shows nothing and places nothing.
4. **Placing**: one model on the 3x3, centred on the middle block; the 8 outer blocks draw nothing of their own. The
   foot and the sloped corner plates sit on the floor, nothing floats, and the plates run into the core with no gap.
   Placed on a ledge, the underside is closed.
5. **Tiers**: a Tier Upgrade Kit on any of the 9 blocks (an outer corner too) upgrades it. T1: plain corner plates,
   core and hull. T2: a vented block with four slots and a sloped plate on each side. T3: a bank of cooling fins with
   a glowing spine on each corner plate.
6. **HP and lights**: Jade or the Info tab: 500 HP at T1, 1,250 at T2, 2,500 at T3 (server config
   `fortressBaseHealthMultiplier` 5.0 in `bastion-common.toml`; 2.0 gives 200/500/1,000). Damage it (mobs, or a
   player with `trustedCanDamage`): the strips, fins and the seam round the core turn yellow below 50 % and blink red
   below 25 %. Look at a strip on a sloped plate from close and from above: solid colour, no flickering dark lines.
   A Repair Kit puts 40 % back. With a Javelin on it, damage sparks leave the hull's rim on every side, above the
   corner plates (a base without a weapon never sparks).
7. **Night**: the gunmetal goes dark like the world around it, the glow stays bright.
8. **One block**: break any outer block with a pickaxe: the whole base goes and drops one Fortress Turret Base; never
   nine. A piston cannot push it.
9. **Destroyed**: at 0 HP a blast a little bigger than the Large base's (custom, no block damage), the large-base
   destroy sound, metal debris; one Damaged Fortress Turret Base drops, its tooltip shows the tier it had; modules drop,
   the ammo is gone. Restore it at the Part Assembler (step 1): the base comes back at that tier.
10. **Mined**: put ammo and a module in a T2 Fortress and mine it: the item keeps the tier and the contents; placing it
    puts them back.
11. **Locks**: every current module is refused on a Fortress with the message of the base it fits ("This module fits a
    standard Turret Base only", "This module needs a Large Turret Base", "This module fits a Makeshift Turret Base
    only").
11b. **Comparator** (also a 0.5.0 fix for the Large base): a comparator reading an outer corner block of an empty
    Fortress, then shift-click Kinetic Rounds into its ammo: the comparator lights up without a block update. On a
    Large base, a comparator beside any of its four blocks does the same.
12. **Feed Hub**: a Fortress on a 3x3x1 Feed Hub: Jade on the hub counts it in "Feeds N turrets". A Fortress on a 2x2
    hub, standing partly off it, does not count. The Feed Hub tooltip and JEI page mention fortress bases on a 3x3.
13. **Turret Configurator** (also a 0.5.0 fix for the Large base): sneak-right-click a configured standard base to copy,
    then right-click an outer block of a Fortress: its Targeting tab shows the pasted settings. Do the same on a
    non-core block of a Large base (before, only one of its four blocks worked). Copying from an outer block works too.
14. **Control Hub**: a Hub Connector linked to a formed hub in the Fortress's Connector slot: it shows on the hub's Map
    and List; clicking it opens its screen.
15. **Overwatch**: an Overwatch Link in its Connector slot, then Take control: the camera sits higher and further back
    than on a Large base (firing: the Javelin section).
16. **Jade and JEI**: Jade on any of the 9 blocks shows the core's lines (tier, HP). JEI U on the Fortress Turret Base:
    its information page.
17. **Bahasa Indonesia**: Base Turret Benteng, Base Turret Benteng Rusak, Rangka Benteng; tooltips and the JEI page
    translated.
18. **Save and reload**, then a dedicated server (`runServer` + a client): the base, its tier and HP come back; no
    errors in either log.

## Javelin Missile (0.5.0)

The first 3x3 turret, on a Fortress Turret Base only. Visual reference: `-Pshowcase=javelin`
(`run/screenshots/showcase_jv_*`). Its sounds are placeholders (`docs/SOUND_SHEET.md` 77–80).

Setup: creative world, a flat open area outdoors by day; a Fortress Turret Base (T1 is fine), a Javelin Missile Turret,
Javelin Missiles, and FE into the base (a Feed Hub under it, or any FE cable). Spawn husks with `/summon husk ~ ~ ~
{NoAI:1b}` for still targets.

1. **Recipes** (JEI R): Javelin Silo, Javelin Radar Mast, Javelin Mount at the Part Workstation (Large Base sizes);
   the Javelin Missile Turret at the Part Assembler under the Missile Launcher tab, 20 s, 25,000 FE; the Javelin
   Missile at the Ammo Workstation under the Rockets tab: 4 iron ingots, 2 TNT, 1 firework rocket, 2 redstone, 5 s,
   2,000 FE, makes one.
2. **Items**: the module icon is a grey armoured silo with cyan strips and a small radar mast; the missile is a white
   missile sprite with a cyan nose; the three parts show the silo, the mast and the deck plate. Tooltip: the ability
   line ("Fires one big missile straight up ..."), "Fits: Fortress Turret Base". A standard or Large base refuses it
   with "This module needs a Fortress Turret Base".
3. **Model**: on the Fortress, a sloped armoured silo standing still in the middle, two blast doors on top, a radar
   mast at the back right whose dish sweeps round while idle. Mounting it: the silo rises out of the deck and the dish
   unfolds. JEI: FE per shot 10k, capacity 30k, input 4k FE/t, ammo 1.
4. **Ammo and FE**: missiles but no FE: it does not fire; FE but no missiles: it does not fire (the well under the
   doors is empty). Both: each shot takes one missile and 10,000 FE (Info tab: FE bar).
5. **Targets**: a lone husk 12 blocks out and three husks bunched 20 blocks out: it picks the bunch. With a Rapid Cycler
   (the next shot comes before the first lands) the second missile goes to the lone husk, not the bunch already marked. A husk under a
   roof (a block over its head), a phantom, a husk closer than 8 blocks: never. A cow or villager next to the bunch is
   never hurt.
6. **Launch**: once it has a target the doors swing open, the dish turns to the target; a burst of grey gas, the
   missile is pushed half out of the silo (its tail still inside) and stays still there, nose up, for half a second;
   then the engine lights with a blast of flame and smoke out of the silo mouth and it climbs straight up, crawling at first, then faster and faster
   until it shoots out of sight (~60 blocks, 2 s) leaving a thick smoke column; a roar follows it up and stops there. The doors stay open while it has a
   target and shut when it has none; after a shot a new missile rises into the well.
7. **The mark**: from the launch, a cyan double chevron floats over the target's head and a cyan ring on the ground
   shrinks from 6 blocks wide to the impact; in the last second both blink red.
8. **Dive and blast**: about 3 s after it vanished (the column still hangs there) the missile appears high over the
   target and comes straight down onto it with a rising whistle and a thin trail, then a
   big fireball, a ground shockwave, dust, debris, a scorch, smoke, a hard camera shake and flash. With damage numbers
   (or Jade): about 40 on the target, about 20 on one 5 blocks away, nothing past 6 blocks. No block is broken.
9. **Moving target**: push the target a few blocks while the missile is up: it still lands on it. Lead it under a roof
   during the climb: the missile lands where it last stood in the open.
10. **No sky**: a block right above the silo: it holds fire (remove it: it fires). Underground, in a cave, under a roof
    or in the Nether: it never fires.
11. **Turret gone mid-flight**: break the Fortress while a missile climbs: the missile still comes down and hurts
    hostile mobs. Leave the area (unload the chunk) with one in flight and come back: no missile is left.
12. **Overwatch**: an Overwatch Link in the Fortress's Connector slot, Take control: aim at open ground 8–72 blocks
    out: a cyan ring (blast size) and chevron mark the spot; press attack: the missile comes down there (on a creature
    under the crosshair, it follows that creature). Closer than 8 blocks, under a roof or out of reach: the mark is red
    and nothing fires. The HUD shows the FE bar and the reload.
13. **Night**: the silo goes dark like the world; the strips, lens and missile nose glow; the launch and the blast
    light the ground; the smoke column is dark, not glowing.
14. **Far away**: watch from 60–100 blocks: the missile stays drawn on the whole climb and dive.
15. **Bahasa Indonesia**: Turret Rudal Javelin, Rudal Javelin, Silo Javelin, Tiang Radar Javelin, Dudukan Javelin;
    tooltip and subtitles translated.
16. **Save and reload** with a missile in flight, then a dedicated server (`runServer` + a client): the turret works,
    no missile is left over, no errors in either log.

## Brood Cannon (0.5.0)

The second 3x3 turret, on a Fortress Turret Base only. Visual reference: `-Pshowcase=brood`
(`run/screenshots/showcase_bc_*`). Its sounds are placeholders (`docs/SOUND_SHEET.md` 81–85).

Setup: creative world, a flat open area; a Fortress Turret Base, a Brood Cannon Turret, Brood Capsules, and FE into the
base. A stone wall 4 high between the turret and the targets helps. Still targets: `/summon husk ~ ~ ~ {NoAI:1b}`.

1. **Recipes** (JEI R): Brood Barrel, Brood Chamber, Brood Mount at the Part Workstation; the Brood Cannon Turret at
   the Part Assembler under the Missile Launcher tab, 20 s, 25,000 FE; the Brood Capsule at the Ammo Workstation under
   the Rockets tab: 3 iron ingots, 1 copper ingot, 2 redstone, 1 spider eye, 4 s, 1,500 FE, makes one.
2. **Items**: the module is a fat cannon on a round chamber with cyan-lit windows; the capsule is a dark round pod with
   green light in its seams; the parts show the barrel, the chamber and the loader arm. Tooltip: the ability line,
   "Fits: Fortress Turret Base"; other bases refuse it.
3. **Model**: the barrel pitches up (45–85°) and turns with the chamber; through each window a folded spider. Firing:
   the barrel slams back, the hatches puff, a loader arm at the back swings the next capsule in. No capsules: the loader
   holds none.
4. **Ammo and FE**: no shot without a capsule or without 4,000 FE; each shot takes one of each.
5. **Targets**: husks on the ground 6–32 blocks out, behind the wall: it lobs over the wall. A phantom or a husk closer
   than 6 blocks: never.
6. **Capsule**: it spins on its arc, lands at the target's feet (on water it floats), a dust ring and a thump; a second
   later the three petals open with a hiss and a green glow, and three spiders unfold their legs and climb out. The
   empty pod goes after two seconds. A capsule that comes down on a husk's head takes ~10 off that husk only (Jade).
7. **Spiders**: grey robot spiders with cyan lights. They walk (alternating legs) to a valid target on the ground, the
   one fewest other spiders are on (three husks: three spiders spread over them), and bite (pounce, fangs) for 8 every
   second; a husk they cannot reach (across lava, up a ledge) is passed over
   for one they can. A cow or villager among the husks is never bitten; spiders never
   attack each other or a turret, and no turret shoots them. A player is bitten only if the turret's filter has
   Players. With nothing to hunt for 10 s a spider bursts by itself (a husk showing up first starts the wait over), so
   none pile up after a fight. Many cannons (16) against a crowd: every spider fights, none stands idle by its turret,
   and the capsules spread over the crowd instead of piling on one husk. Their lights turn yellow
   below half HP and blink red below a quarter.
8. **Mobs go for them**: a zombie or skeleton near a spider attacks it (`mobsAttackTurrets` on). Hit a spider yourself:
   its owner and trusted players only hurt it with `trustedCanDamage` on; others always can.
9. **Death**: a spider at 0 HP blinks red for half a second, then bursts (fireball, sparks, pieces, bigger than before): about 15 to
   valid targets within 4 blocks, nothing to the cow beside them, no blocks broken.
10. **Cap**: keep firing: at 12 spiders (capsules in the air count) it stops with ammo and FE untouched (state Aiming);
    kill three and it fires again. `maxTurretUnits` in `bastion-common.toml` (96) caps all spiders in the dimension.
11. **Turret gone**: break the Fortress while spiders fight: they keep fighting, then burst 10 s after their last target. Leave the
    area (unload it) and come back, or rejoin the world: no spiders or capsules are left, and the count starts at 0.
12. **Overwatch**: an Overwatch Link in the Fortress, Take control: the arc shows where the capsule lands (red inside
    6 blocks or out of reach); press attack: the capsule comes down there and opens.
13. **Night**: the cannon and spiders go dark like the world; their lights, the capsule core and the windows glow.
14. **Bahasa Indonesia**: Turret Meriam Induk, Kapsul Induk, Laba-laba Induk, Laras Induk, Ruang Induk, Dudukan Induk;
    tooltip and subtitles translated.
15. **Save and reload** with spiders out, then a dedicated server (`runServer` + a client): no spiders come back, the
    turret fires again, no errors in either log.

**0.6.0 (user 2026-10-26): climb, leap, dying leap.** Visual reference: `-Pshowcase=spiderleap`
(`run/screenshots/showcase_sl_*`, checked 2026-10-26).

- **Climb**: a husk on top of a 4-high pillar of blocks: a spider walks to the pillar, crawls up its side and bites it on
  top. A husk on an island in lava is still passed over (the spider goes for one it can reach).
- **Leap**: a husk 7-9 blocks off: the spider runs until it is about 5 blocks away, then leaps (legs tucked, a quick
  whoosh), bites as it lands, and leaps again only 3 s later. Farther than 5 it never leaps; closer than 2 it walks up
  and bites. Still one bite a second: a leap right after a bite waits for the next.
- **Leap on odd ground** (review minors 2026-10-27): a husk 4 blocks off with the spider on soul sand, on ice, and up a
  3-high ledge above it: each leap lands on the husk (not short, not past it). A leap into a wall stops at the wall and
  the spider climbs it in its walk, not frozen with its legs tucked; one pressed against a wall leaps its full length.
- **Dying leap**: kill a spider 4-5 blocks from a husk: its eyes blink red as it leaps at the husk and bursts on it (15,
  the husk only, no blocks). With two husks, it goes for the nearer. Kill it with a sprint hit or a Knockback sword,
  pushing it away from the husk: the leap still goes at the husk. Kill one with no enemy within 5 blocks: it bursts
  where it is. An idle spider's 10 s self-destruct bursts where it is too.
- Config `bastion-turrets.toml [brood_cannon]`: `leap_range` 5, `leap_ticks` 8, `leap_cooldown` 60.

## Hive Carrier (0.5.0)

The third 3x3 turret, on a Fortress Turret Base only. Visual reference: `-Pshowcase=hive`
(`run/screenshots/showcase_hc_*`, checked 2026-10-24). Its sounds are
placeholders (`docs/SOUND_SHEET.md` 86–89).

Setup: creative world, a flat open area; a Fortress Turret Base, a Hive Carrier Turret, Hive Drones, and FE into the
base. Still targets: `/summon husk ~ ~ ~ {NoAI:1b}`; a flyer: `/summon phantom ~ ~5 ~ {NoAI:1b}`.

1. **Recipes** (JEI R): Hive Hangar, Hive Relay Mast, Hive Launch Bays at the Part Workstation; the Hive Carrier
   Turret at the Part Assembler under the Missile Launcher tab, 20 s, 25,000 FE; the Hive Drone at the Ammo Workstation
   under the Rockets tab: 2 iron ingots, 2 copper ingots, 1 redstone, 1 feather, 5 s, 2,500 FE, makes one.
2. **Items and JEI**: the module is a big layered hive with doorways and a mast; the Hive Drone is a small quadcopter
   seen from above. JEI and the Info tab show Energy/drone 2k FE, capacity 12k, Drones/wave 3, fire rate 0.07/s.
3. **Model**: a big hollow wasp-nest hive (nearly the width of the 3x3) on a slim pedestal, never turning: honeycomb
   plated layers with lips, swelling out above three doorways (north, south-east, south-west) with hoods and cyan
   frames; through each doorway a landing pad with cyan lights inside; a relay mast out of the cap whose antenna tip
   pulses. Mounting it: the mast rises out of the cap. A wave: the mast head spins and the antenna flashes.
4. **Waves**: with a husk within 64 blocks (0.6.0; was 32) under open sky or in sight of the hive, three drones fly level out of
   the three doorways, then climb (three Hive Drones and
   6,000 FE spent); the next wave 15 s later. No FE, or no Hive Drone and none docked: no wave.
5. **Drones**: small quadcopters with ducted fans (spinning) and a cyan light; they climb, patrol round the carrier
   about 8 blocks up and about 32 blocks out, hover off a target and shoot it from up to 32 blocks (amber tracers, a chin flash, sparks on hits, 10 shots a
   second each, 35 damage a second); a phantom is shot too. A cow or villager among the husks is never hit; drones never shoot each other,
   spiders or turrets, and no turret shoots them. Their light turns yellow below half HP and blinks red below a quarter.
   Shot down while shooting a mob (user 2026-10-27; `/bastiondev owndamage true` and a sword or arrow, or a Spore mob):
   a drone no longer just drops: it noses down trailing red smoke into the nearest enemy in sight and bursts on it. One
   killed with no enemy in sight still drops without a blast.
6. **Kamikaze**: a drone out of bullets, or below a quarter HP, with an enemy in reach noses down, rolls, trails red
   smoke and bursts on it (a bigger blast than before): about 15 to valid targets within 4.5 blocks, nothing to the
   cow, no blocks broken.
7. **Home**: kill or remove the targets: after about 5 s each drone comes back high, drops down in front of its
   doorway, flies in level and vanishes on its pad (docked); none passes through the hive's walls or roof. Bring a husk
   back: the next wave relaunches the docked drones for 1,000 FE each, no Hive Drone spent.
7b. **Inside the hive**: look in through a doorway. Docked drones stand on the racks, as many as are docked (the three
   pads first, then the shelves over them, up to 9); they leave the racks as a wave takes them. In the 15 s after a wave
   a new drone takes shape on each empty pad (lit plate, legs, body, rotors, then its lights) with welding sparks, then
   stands there ready until a wave takes it; none is built without a Hive Drone in the slots. The insides are drawn
   only within 24 blocks. A wave's puffs come out of the doorways it uses (not one walled off).
7c. **Unreachable mobs** (0.6.0): a zombie boxed in stone (or in a cave under the hive) within 64 blocks sends no wave;
   one under a roof but in sight of the hive does. No more drones going out and back every 15 s with nothing to shoot.
8. **Cap**: keep it busy: never more than 9 drones flying plus docked; at the cap it waits with items and FE untouched.
9. **Mobs go for them**: a skeleton shoots drones; a zombie targets a low one. Shot down, a drone drops and is gone
   (no blast). Hit one yourself: owner and trusted players only with `trustedCanDamage` on.
10. **Carrier gone**: break the Fortress mid-fight: the drones keep fighting and dive when their bullets run out; with
    nothing left they hover where they are. Leave the area or rejoin the world: no flying drones are left (docked ones
    stay docked, item 14).
10b. **Docked drones are never lost by moving the carrier**: with drones docked, take the module off (sneak + empty
    hand, or the GUI): the docked drones come back as Hive Drone items in the base's ammo slots (dropped on the base when
    full). Mine the Fortress with drones docked and place it again: they are still docked. Let it be destroyed (0 HP):
    they are lost with the weapon and the ammo.
11. **Overwatch**: Take control of the Fortress: you can watch it; it keeps sending waves on its own.
11b. **Roof and trees**: put the carrier in a room with a roof two blocks over the base and the husk walled in: the
    drones fly out of their doorways, bump the roof, give up the climb, patrol under it and come home through their
    doorways after about 5 s. In a forest: a drone out of bullets dives at the husk, not into the leaves next to it.
12. **Night**: the hive goes dark like the world; the doorway frames, pad lights, antenna and drone lights glow;
    tracers read.
13. **Bahasa Indonesia**: Turret Kapal Induk Sarang, Drone Sarang, Hanggar Sarang, Tiang Relay Sarang, Ruang Luncur
    Sarang; tooltip, JEI rows and subtitles translated.
14. **Save and reload** with drones out and docked, then a dedicated server (`runServer` + a client): the flying ones
    are gone, the docked ones are still there (the next wave relaunches them for 1,000 FE each, no Hive Drone taken),
    no errors in either log.

## Research & Turret Codex (0.6.0)

Research per world, the Turret Codex, recipe locks, JEI. Visual reference: `-Pshowcase=codex`
(`run/screenshots/showcase_codex_*`, checked 2026-10-24). The research sound is a placeholder (`docs/SOUND_SHEET.md` 90).

Setup: a **new** survival world (research on, the default). Give yourself iron, copper, redstone, gold, diamonds and
netherite scrap when a step needs them.

1. **First join**: a Turret Codex is in your inventory. Leave and rejoin (or die and respawn): no second one.
2. **Locked**: the crafting table makes no Part Workstation (the pattern gives no result); the Codex, the Makeshift
   Turret Base and the four early turrets still craft. A Bastion item's tooltip (in JEI or creative) says
   "Not researched: …" in red.
3. **Codex**: right-click it. Three tabs; the Turrets tab is the tree from the user's diagram (Cobblestone → Gun →
   Sniper / Ion…, Arrow → Rocket / Mortar…), row 0 at the bottom; free cards cyan, the rest red; a card that can be
   researched now pulses; small base icons on turret cards until that base is researched. Hover a card: name and
   state. Drag (and scroll) pans when the tab is bigger than the panel, also a drag that starts on a card (a plain click
   picks it); small arrows show more off an edge. The page (a turret: "1x1 · Turret Base · floor, wall or ceiling"):
   the item turning (drag turns it), base and ammo, three bars, text, Unlocks, Needs, the costs with how many you carry
   (red when short). The Modules tab: two bands of five columns, Mk1 → Mk2 → Mk3 upward. What a card brings along sits
   beside it as smaller cards in its colour (Gun → Kinetic Rounds; Laser Rifle → Laser Cell, Empty Laser Cell, Charging
   Station; Control Hub → Casing, Power Port, Uplinks T1-T3, Hub Connector; Turret Base → Repair Kit, Configurator); no
   parts there (they are in the page's Unlocks). Hover one: its name and "Comes with …"; clicking it picks its card.
4. **First research**: Workshop tab, Workstations, with 8 iron + 8 copper + 4 redstone: press Research: the card
   flashes cyan, a chime, a chat line "<you> researched Workstations", the items are gone. Now the crafting table makes
   the stations. Without the items the button stays grey.
5. **Chain**: Turret Base, then Gun (Turrets tab): the Gun card pulses only after the Turret Base. At the Part
   Workstation the Gun Barrel row loses its padlock; a locked row (Sniper Barrel) is dim with a padlock, clicking it
   does nothing, its tooltip says "Research Sniper Turret in the Turret Codex". Kinetic Rounds open with the Gun or the
   Machine Gun; the Charging Station with the Laser Rifle or the Plasma Repeater.
   The Part Workstation's tab row stays inside its screen: 11 tabs between < and > (click them or scroll the wheel
   over the tabs; the dim arrow is the end); the open recipe's tab is in view when the screen opens.
6. **Modules**: Choke Module Mk1 shows a small Shotgun icon and stays locked until the Shotgun is researched; Mk2 needs
   Mk1, Mk3 needs Mk2; the Module Workstation's Mk2 rows open only after the Mk2 card.
7. **Creative**: switch to creative: Research needs no items.
8. **LAN / second player**: open to LAN, join with a second client: they get their own Codex; when one player
   researches, the other's open Codex turns that card cyan at once and both see the chat line.
9. **Old world**: open a world last played with 0.5.0 or older: every card is cyan, every recipe works as before.
10. **Config while playing**: in `config/bastion-research.toml` set `enabled = false` and save: the Codex says
    research is off, the Research button is gone, every recipe opens. Back to `true`: locks return (a station that had a
    locked recipe picked stops). Change `gun`'s cost (for example to `["minecraft:dirt 2"]`) and save: the Gun page shows
    it at once; a typo (`"minecraft:dirtt 2"`) is skipped with a warning in the log. Put the file back.
11. **JEI**: R on an iron ingot: the Research pages that cost iron; U on the Gun Turret: its Research page (cost, what
    it unlocks, Needs, Leads to, researched or not). A locked station recipe shows a small padlock bottom-right with
    the same hint on hover, and its + button is red with "Research … in the Turret Codex". The Codex's Recipe button
    opens JEI on the selected item. With `gun`'s cost changed to `["minecraft:dirt 2"]` (step 10): R on dirt finds the Gun
    page, R on copper no longer does.
12. **Commands** (op): `/bastion research list` (free cards left out), `/bastion research grant gun`, `/bastion research revoke all`,
    `/bastion research revoke cobblestone` (refused: always researched)
    (an open Codex follows at once). `/bastion vfx <preset>` still works (it shares the `/bastion` root on the client).
13. **Without JEI** (`-PnoDevMods`): no Recipe button, nothing breaks.
14. **Dedicated server**: `runServer` + a client: the Codex opens, research syncs, costs from the server's config.
15. **Bahasa Indonesia**: Kodeks Turret, tabs Turret / Workshop / Modul, "Siap diriset", "Belum diriset: …", the chat
    line "<pemain> meriset …", the JEI page "Riset".

## Camera mods: no startup crash (0.5.1, GitHub issue #1)

Every `./gradlew runClient` now loads a dev-only mixin (`src/dev/.../CameraModTurnMixin`) that redirects
`player.turn` in `MouseHandler.turnPlayer` the way camera mods do, applied before Bastion's mixins.

1. **Start**: `./gradlew runClient` reaches the main menu (before the fix: crash "Specified index 0 for @ModifyArg is
   invalid for args (Lnet/minecraft/client/player/LocalPlayer;DD)").
2. **Mouse**: in a world, looking around works as usual, same speed as before.
3. **Overwatch zoom**: enter Overwatch on a turret, zoom in (scroll): the mouse turns the view slower the further in;
   zoom back out: normal speed again.

## Turret hits → body parts (0.5.2)

Bastion's part (plain Bastion, no other mods): the aim moved to the body centre. Mod-side checks are in
`../Xersian/docs/TESTING.md` "Turret Bastion → body part".

1. **Aim**: a Machine Gun, Sniper or Laser turret and a zombie walking towards it: the barrel points at its chest, not
   its eyes; tracers end on the chest. Same for a spider (wide and low): the shots land on its body.
2. **Gun keeps the head**: a Gun Turret aims at the zombie's eyes; the locked first shot still does its headshot damage.
3. **Nothing else changed**: kills take about as many shots as before; a mob behind a half wall whose chest is hidden
   but whose head shows is still shot (in the head).
4. GameTests: `gunShotPostsTurretHitEvent`, `splashPostsNoTurretHitEvent`, `laserPostsTurretHitEventPerTarget`,
   `rocketDirectHitPostsTurretHitEvent`, `turretsAimAtTheBodyCentre`, `gunStillAimsAtTheHead`.
