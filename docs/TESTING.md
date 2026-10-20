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
Automated: `./gradlew runGameTestServer --offline -PwithCompatMods` runs `CompatModTests` with the mods (without them those 3 tests pass at once).
- [ ] Spore: `/summon spore:inf_human` (or any infected) near a turret: the turret shoots it; an unarmed turret gets walked up to and hit (lights go yellow/red).
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
- [ ] Hub Connectors: right-click the Interface with a stack of connectors: the whole stack is linked (action bar, tooltip "Linked to the hub at x, y, z", the icon's lamp lit), and the hub screen does not open; shift + right-click in the air clears the link (shift + right-click on a block, e.g. a turret, keeps it). Put one in a turret's Connector slot (Status tab, between the ammo and the modules; hover the empty slot for its name; hoppers never touch it).
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
- [ ] Plasma Repeater: three slightly different shots; Turret base: deploy, idle hum (loops without a click), repair, destroyed (a Large Turret Base sounds bigger).
- [ ] Volume: the new sounds sit about as loud as the turrets still on placeholders (Rocket, Tesla, ...).
- [ ] Zombies hit by turrets groan at most twice a second each and quieter than before (config `turretHurtSoundInterval` 10 ticks, `turretHurtSoundVolume` 0.5); their death sound is unchanged. No sound when a bullet, plasma ball or ion bolt lands (removed for now).
- [ ] `/bastiondev turrets [t1|t2|t3]` (dev runs and the `-dev` jar): one armed base per weapon (Large Turret Base for the big ones) with Creative Ammo; place one and it fires.

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
- [ ] The crafting table only makes the 5 stations (Part Workstation needs a smithing table, the Part Assembler a Part Workstation and a diamond). No turret, module or ammo recipe left there.
- [ ] Placing: the station grows to your right, away from you and up from the block you click, its front facing you; it refuses if any of its blocks is taken. Breaking any block breaks it all and drops it once (with its contents).
- [ ] Power: a Creative Power Source (creative tab) next to any of its blocks, or any FE cable; the bar top right fills (hover for FE). Without FE nothing is made ("Needs FE").
- [ ] Part Workstation: pick a tab (each turret/base) and a part, put the materials in (or let a hopper/belt/arm do it: only that recipe's materials go in), it machines it over a few seconds (carriage runs, spindle spins, sparks, hum), part in the output (hoppers can pull it).
- [ ] Parts show in 3D as pieces of their turret. Part Assembler: the three parts in, the finished module/base turns on the deck while the arms weld and the scanner sweeps, then appears in the output. Also restores damaged bases (with repair kits).
- [ ] Module and Ammo Workstations: Craft button makes it instantly from the input slots and your inventory (shift: x8); with materials piped in they also make it by themselves over time.
- [ ] Charging Station (1x1x1): Empty Laser Cells (from the Ammo Workstation) in, charged Laser Cells out, 3s and 3k FE each; arcs jump from the coil tips into the cell while it charges.
- [ ] GUI: tabs, list with scroll, turning preview (flat icons face you), ingredients with have/need in green/red, progress bar, status text, no overlapping text.

## Creative tabs
- [ ] Six Bastion tabs in this order (Forge puts mod tabs on the creative screen's next pages): Turret Bases (bases, damaged bases, repair and upgrade kits, configurator), Weapon Modules, Ammo (incl. Creative Ammo), Modules (incl. Choke Module), Workstations (the 5 stations + Creative Power Source), Parts (all 33, grouped per base/weapon).
- [ ] No item shows up in two Bastion tabs; Bahasa Indonesia names: "Bastion: Workstation", "Bastion: Part".

## Persistence & servers
- [ ] Leave and rejoin: tier, HP, filter, trusted list, ON/OFF, redstone mode, inventory all kept.
- [ ] Mine a T2 base with stuff inside: the item keeps tier + contents (tooltip), placing it restores them.
- [ ] `runServer` + separate client: everything above works on a dedicated server, no errors in either log.

## Turret stats config (bastion-turrets.toml)
Put every value back to its "Mod default" number when done (or delete the file): the GameTests use the same `run/config` file (`runConfigLeavesEveryValueAtDefault` names any number left there).
- [ ] Start the game once: `config/bastion-turrets.toml` appears next to `bastion-common.toml`, with the explanation at the top, one section per turret (`[gun]` first, `[plasma_cannon]` last) and every value written as the mod's own number (`damage = 24` under `[sniper]`, no `.0`), with "Mod default" and "Range" in its comment.
- [ ] With a world open, set `[sniper] damage = 100` and save the file: within a few seconds (up to about 10 on macOS), a Sniper's Info tab shows 100 and it kills a Witch (26 HP) in one shot, not two. Type 24 back and save: 24 again. No restart.
- [ ] Set `[railgun] charge_ticks = 10` and save: the Railgun charges for half a second.
- [ ] Set `[gun] damage = -5` (or `"abc"`) and `[ion_rifle] ammo_per_shot = 1`, then restart: the log warns for both, the file shows the mod's numbers again (7 and 0) and both turrets still fire.
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
