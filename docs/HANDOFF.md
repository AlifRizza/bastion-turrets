# Bastion Turrets — Session Handoff (2026-10-10, 16:30 WIB)

Read this first in a new session, then `CLAUDE.md` and `docs/PLAN.md` (the spec). Manual test list: `docs/TESTING.md`.
Store/upload material: `docs/release/` (DESCRIPTION.md, DESCRIPTION.curseforge.html, UPLOAD.md, icon.png, screenshots).

---

## 0. Where we stopped

**2026-10-19: 0.2.1 committed, tagged `v0.2.1` and pushed** (with the 0.2.0 commit; 0.2.0 was uploaded by the user,
never tagged). 0.2.1 = the turret stats config and the Dome push below; changelog in `docs/release/UPLOAD.md`, store
text changes in `docs/release/DESCRIPTION_UPDATE_0.2.1.md`. The user uploads the jar.

**2026-10-18: Repulsor Dome push by distance (in 0.2.1).** `RepulsorDomeWeapon.push(knockback, range, distance)` =
knockback + (range - distance from the orb), so 6 at the edge of its 9 blocks, +1 per block closer, 15 in the middle
(~12.5 against the 2x2 base). Config `knockback` for the Dome now means the push at the edge. 179/179 GameTests.

**2026-10-17: turret stats config (in 0.2.1; tested by the user in game 10-18, live reload works).** `config/bastion-turrets.toml`
(`config/TurretStatsConfig`, spec PLAN §8 "Config stat turret", plan `docs/superpowers/plans/2026-10-17-turret-stats-config.md`):
16 weapons x (15 common stats + their JSON params), written with the mod's numbers (user 10-18; a number equal to the
mod's value is no override, so datapacks still count); `WeaponDataLoader` keeps raw JSON and
serves raw + overrides (`WeaponData.withOverrides`, `merge`, `rebuild`); `ModConfigEvent.Reloading` -> server thread
`TurretStatsConfig.reloaded()` -> rebuild + `WeaponDataSync` to all. 175/175 GameTests (also with compat mods). Final-review fixes: `Hitscan.damage` ignores 0/NaN, `StatSheet.damageScale()` for splash/blast/field, ammo_per_shot 0~1 (0~0 on no-item weapons), energy_capacity <= 3,000,000 and max_heat <= 3000 (GUI 16-bit bars). GameTests restore the previous config value; `runConfigLeavesEveryValueAtDefault` names numbers left in run/config.

**2026-10-17 00:15: 0.2.0 committed** (`mod_version` 0.2.0; changelog and store text in `docs/release/UPLOAD.md` /
DESCRIPTION.*): Control Hub cube + Hub Connector, Ion/Plasma turrets, every turret's sounds from Gemini Omni takes
(`tools/cut_sfx.py`, video scenes in src/dev VideoShowcase), Mortar whistle removed, hang + groan fixes. Uploaded by
the user; pushed with 0.2.1 on 10-19. Not committed on purpose: `sfx/` (sound sources, the user decides), `modlist.html`.
Remaining placeholders: machine_gun.overheat (user supplies), Workstation, Control Hub, turret.tier_up. The notes
below are the history that led here.

**State at a glance (2026-10-10 16:30):**
- **Turret Control Hub, cube + Hub Connector revision: built, uncommitted, not yet tested by the user in game** (NOW 0).
  User 15:01: the hub became a **3x3x3 cube** (Interface in the middle layer at eye level, satellite on top, 5 tall) and
  turrets only show in a hub when they hold a **Hub Connector** linked to it (new turret slot; satellite reach still
  applies; 8 casings per craft; 4 connectors = 2 copper, 1 redstone, 1 ender pearl). Design 15:10, 5 s restart rule
  15:10, spec (PLAN §8 "Turret Control Hub") 15:19. Plan `docs/superpowers/plans/2026-10-10-control-hub-cube-connector.md`,
  all 6 tasks done (native / executing-plans, ledger `.superpowers/sdd/2026-10-10-control-hub-cube-connector/progress.md`,
  kept until the commit; the first version's ledger `.superpowers/sdd/2026-10-10-turret-control-hub/` too).
  Final review (fresh reviewer, opus): no Critical, 2 Important, both fixed test-first (shift + right-click on any block
  cleared a connector stack's link, now only in the air; no test for a foreign block in the bottom/top layer or for the
  problem order, now pinned), 2 Minor deferred (NOW 0). 152/152 GameTests after the fixes, also with `-PwithCompatMods`.
- Git: `main` at `4abf5ab` ("Ion Rifle, Plasma Repeater, Plasma Cannon; Plasma Cell; sound sheet"), **4 commits not
  pushed** (`32ba29a` 0.1.2 work, `d25ce7d` compat + Railgun trail, `73fa0df` module tiers + Dome reach, `4abf5ab`
  Ion/Plasma); GitHub has `v0.1.1` (`40db2a2`). `mod_version` is still **0.1.1**; the next release is **0.2.0** (user 10-14, was going to be 0.1.2: too many changes;
  still Beta; "0.1.2 work" below = 0.2.0), not released (changelog in
  `docs/release/UPLOAD.md`). Uncommitted now: the whole Control Hub with its cube + connector revision (code, assets,
  generators, docs) and the `feedHubIdleIsCheap` rewrite; plus `modlist.html` (the user's file, never commit it). Commit
  only when the user says "commit" (commit the hub as one piece, never the 3x3 version alone).
- **Bug from the user's PC (18:47), fixed, confirmed fixed by the user on the PC (19:03):** after placing 6+ Large Turret Bases, leaving
  and rejoining hung the world load (not reproducible on the Mac). Cause: both base blocks use `dynamicShape()`, so the
  light engine (own thread) calls `propagatesSkylightDown` -> `getShape` -> `TurretBaseBlock.core` with the ServerLevel;
  `level.getBlockState` off the server thread does `supplyAsync(main).join()`, and while chunks load the server thread
  waits for that light work -> deadlock (timing-dependent: more bases, chunk borders, more cores). Fix: `core()` returns
  null off the server thread (plain block shape there). Test `TurretBaseTests.baseShapeNeverWaitsForTheServerThread`
  (RED: timed out after 2 s; GREEN). If the PC still hangs, get `logs/latest.log` + a thread dump from the PC.
- **Real SFX (user 21:51), built 22:00, not yet heard by the user in game:** the user's sources in `sfx/` (root; Gun,
  Shotgun, Sniper, turret base, Pixabay minigun, `plasma_gun.mp3`) -> `tools/cut_sfx.py` (cut / split / loop / variants by
  pitch, levelled by RMS = placeholder +2 dB, mono OGG via soundfile; ffmpeg here has no libvorbis) -> 28 files.
  Machine Gun, rebuilt 10-11 from the user's three new takes (the Pixabay minigun is no longer used): the barrels' whir
  (`minigun spin up - spinning - spin down.mp3`, one gain for all three cuts so they join level) ~10 dB under the
  barrage: `spin_up` when the synced spin leaves 0, `spin_loop` (ClientTurret, TurretLoopSound) fading in over the last
  quarter of the spin and out as it slows, `spin_down` once the spin drops from >1/3. On top, per shot again
  (`machine_gun_fire.wav`): each shot plays the burst's roar from one of three points inside it through its decay, so
  shots 50-100 ms apart chain into one barrage (checked by mixing on the game's schedule: no dip over 6 dB while firing).
  Far: `machine_gun_fire_trail.wav`, 2-3 distant shots per piece. `fire_loop` removed.
  Plasma Repeater shots re-cut from 3-4 s (user: the opening burst was wrong);
  `turret.destroyed_large` for Large Turret Bases (DESTROYED event scale 1.6). 68 events, all files mono, 155/155 (10-11).
  SOUND_SHEET: status table, Pixabay credit, MiniMax H3 Audio prompts per turret for the rest (user 10-11: ComfyUI node `t22m003/ComfyUI-MiniMaxH3Audio`, three
  fields integrated_multimodal_description / overall_soundscape / non_diegetic_music = N/A, positive wording only since H3
  ignores negatives, FPS-game keywords, 4-12 s; earlier: video-generator prompts 23:07, user 22:52: one natural take of the
  whole event, e.g. a minigun spinning up, firing, winding down; never separated sounds or gaps: I cut, loop and combine;
  tails and variants derived from the take).
  Video-to-audio (user 10-14, Google Gemini Omni: input video <= 10 s, 720p 16:9): `-Pshowcase=video
  -PshowcaseWorld=showcase_fx` records a silent Rocket Launcher clip (src/dev VideoShowcase: side view, 1280x720 frames
  named by their ms into build/video-frames, ~24 fps; ffmpeg concat + fps=30). Cut 1.6-10.9 s ->
  build/omni/rocket_launcher_silent.mp4 (9.3 s): launches 0.5 / 2.3 / 4.1 / 5.9 / 7.7 s, explosions 0.8 / 2.6 / 4.4 /
  6.3 / 8.1 s: cut rocket.launch / rocket.explode from Omni's audio at those times. Omni's first pass used vanilla
  Minecraft rocket/TNT sounds (it recognised the game); the prompt that worked asks for an original AAA military
  shooter sound design and rules out fireworks/TNT/Minecraft. Its audio -> `sfx/rocket_launcher_generated.wav` ->
  cut_sfx.py: launch 0.555-0.95 s (+ pitch 0.94 variant: shots 2-5 start over the last rumble), explosions 0.95-2.37 /
  6.305-7.69 s. `rocket.fly` and `rocket.launch_tail` still placeholders. 155/155.
  Missile Launcher (user 10-14: single mode, one missile at a time so nothing overlaps, husks far for a long flight
  sound): `-Pshowcase=video_missile`, T3, empty tubes, one Missiles item handed over at 0.5 / 3.8 / 7.1 s (it loads and
  fires at once), husks 30-34 blocks out -> build/omni/missile_launcher_silent.mp4 (9.9 s): launches 0.5 / 3.7 / 7.0 s,
  ~1.45 s flights, impacts 1.9 / 5.2 / 8.5 s. Omni's audio (user: "pretty similar with rocket but okay") ->
  `sfx/missile_launcher_generated.wav` -> cut_sfx.py: missile.launch shots 1-2, missile.explode shots 1-2, `rocket.fly`
  = steady flight of shots 2+3 chained into a 1.34 s loop (new `chain()`; seams within the roar's own +-2.5 dB),
  `rocket.launch_tail` = shot 1 launch+flight through `muffle()` (900 Hz). Rocket + missile sets now all real except
  missile.reload (reload and launch fall on the same tick in game). 155/155.
  Laser Rifle (10-15): `-Pshowcase=video_laser -PnoDevMods` (no ToroHealth bars), T1, husks arrive 0.5 s into the
  recording in a row on the beam line (8.5 / 11 / 13.5 blocks) so the first charge is on camera ->
  build/omni/laser_rifle_silent.mp4 (9.9 s): charges start 0.35 / 5.30 s, beams 2.90 / 7.83 s (frame check), each
  beam ~0.4 s bright. Cycle is cooldown 50 + charge 50 ticks, so 5 s per shot. Omni's audio (user file
  build/omni/laser_rfile_generated.mp4) -> `sfx/laser_rifle_generated.wav` -> cut_sfx.py: charge = last 2.5 s of
  charge 2 (Omni started charge 1 late), +4 dB over the take's gain; fire_0/1 = shots 1/2 to the end of the ring-out
  (1.9-2.1 s); fire_tail = shot 1 muffled at 900 Hz. Game-schedule mix: build/sfx-sheets/laser_preview.wav, the
  charge->shot seam dips 2.5 dB at most. 157/157.
  Flamethrower (10-15): `-Pshowcase=video_flame -PnoDevMods`, T1 switched on/off (setEnabled, FLAME_BURSTS) so the bursts
  have any length; husks 4.5-6 blocks out, they keep burning (red) between bursts and get pushed back a little by each
  pulse -> build/omni/flamethrower_silent.mp4 (9.9 s): stream on 0.25-3.75 s and 6.2-8.75 s (frame check), 1.15 s
  tail. Omni's audio -> `sfx/flamethrower_generated.wav` -> cut_sfx.py: ignite_0/1 (renamed from ignite, sounds.json +
  gen_sounds.py) = each burst's swell, levelled to the roar (Omni made no distinct whoomph); loop = 6 s texture() of
  both steady roars (burst 1 is 4 dB darker before 1.1 s, skipped); burning = crackle 4.2-5.1 s. Previews:
  build/sfx-sheets/flame_preview_now.wav (as in game: the roar cuts from -17 to -33 dB in one tick) and
  flame_preview_with_stop.wav (+ Omni's 0.3 s collapse at 3.66-4.6 s, a possible `flamethrower.stop`, not added:
  asked the user, no answer yet). 157/157. Events: flamethrower.ignite (first pulse; Mortar fire patch too), .loop (while
  flaming, drops to 0 four ticks after the last pulse: no stop sound yet), .burning (burning targets, Mortar patch).
  Tesla Coil (10-15): `-Pshowcase=video_tesla -PnoDevMods`, Large Turret Base at ORIGIN with no FE; 3000 FE (one
  discharge) handed over at TESLA_SHOTS so the four discharges never overlap; husks 7-10 blocks out ->
  build/omni/tesla_coil_silent.mp4 (9.9 s): charges 0.21 / 2.61 / 5.01 / 7.41 s, bolts 0.83 / 3.23 / 5.60 / 8.03 s
  (frame check), ~0.4 s on screen, struck husks crawl with small arcs ~0.7 s after. Omni's audio (the user left it
  in ~/Downloads; copied to build/omni) -> `sfx/tesla_coil_generated.wav` -> cut_sfx.py: charge = charge 4 (the only
  one 0.6 s long; Omni started each charge late) +3 dB; zap_0/1 = strikes 2 and 4, 1.3 s; crackle_0/1 = first 0.3 s
  of charges 2 and 3; zap_tail = strike 2 muffled. Game-schedule mix: build/sfx-sheets/tesla_preview.wav, no dip at
  the charge->strike seam. 157/157. Events:
  tesla.charge (0.6 s, charge_ticks 12), tesla.zap (x2, volume 1.2), tesla.zap_tail (past 32 blocks), tesla.crackle
  (x2: idle sparks at 0.35, Railgun charge, Workstation).
  Sniper redo (user 10-16: replace the old takes with an Omni score): `-Pshowcase=video_sniper -PnoDevMods`, T1, camera
  behind the turret's shoulder, husks in a row 10-17 blocks out arriving 0.5 s in -> build/omni/sniper_silent.mp4
  (9.9 s, trim 0.4): charges 0.27 / 3.22 / 6.17 / 9.12 s (1.5 s, charge_ticks 30; the last runs off the end), shots
  0.27+1.53 = 1.80 / 4.74 / 7.70 s (muzzle-flash frame check). Omni's audio -> `sfx/sniper_generated.wav` ->
  cut_sfx.py: shots at 1.80 / 4.775 / 7.77 s, ring-out gone by ~2.1 s; fire_0/1 = shots 2 and 3 (both from silence),
  2.1 s; fire_tail = shot 2 muffled. One gain: shot 2's first 0.3 s at -14.5 dBFS, as the old take, Laser and Gun.
  No charge in this take: Omni scored a noise rumble at 0.05-1.75 s (flatness 0.75, no rising tone) and nothing
  before shots 2 and 3; the laser sight is faint from behind the turret (charge from the second take below).
  sniper_fire.wav / sniper_fire_tail.wav no longer used. Game-schedule mix:
  build/sfx-sheets/sniper_preview.wav. 157/157.
  Second take for the charge only: the scene now films from the side and above (camera -6.5/+3.5/+6.5 from the
  turret, husks 9-13 blocks out) so the green sight crosses the frame over the floor (over the pale sky its additive
  glow vanished) -> build/omni/sniper_charge_silent.mp4 (9.9 s, trim 0.4): charges 0.31 / 3.30 / 6.25 / 9.15 s,
  hits 1.82 / 4.75 / 7.73 s (husks-turn-red frame check). Omni's audio -> `sfx/sniper_charge_generated.wav`: four
  charges (0.35 / 3.35 / 6.35 / 9.20 s), each a hiss as the sight appears then a buzz; only the 3rd grows brighter
  into its shot (3 kHz band +16 dB), the others stay flat or fade. sniper.charge = its last 1.5 s before the shot
  (6.278-7.778 s), gain ref that take's shot 2 (4.815 s) at -14.5, +6 dB (Omni ~11 dB under the shot; ClientTurret
  plays it at 0.8, so in game it sits where the Laser's charge does). Shot cuts moved to 3 ms before each attack
  (4.787 / 7.772 s): the preview had a 25 ms hole at the charge->shot seam, now none. 157/157.
  Railgun (10-16): two clips, since a slug (40 blocks/tick) lands the tick it is fired and Omni would score one sound
  for shot and impact. `-Pshowcase=video_railgun -PnoDevMods`: Large Turret Base, 16 Rail Slugs (Creative Ammo would
  pay the FE too), no FE; 8000 FE at RAIL_SHOTS -> build/omni/railgun_silent.mp4 (9.9 s, trim 0.3): charges 0.29 /
  5.24 s (3 s; coil bands light up, arcs crawl over the barrel from 2.19 / 7.23 s), shots 3.33 / 8.27 s (hit-flash
  frame check). `-Pshowcase=video_railgun_impact`: no turret, four staged impacts (RAIL_IMPACT + RAIL_GROUND as
  RailgunEffects.land plays a block hit, colour 0xFF000000|energy_color as TurretFireEffects passes it). First take
  staged them on husks: Omni returned the video with NO audio track at all (likely its safety filter: people hit by
  an unseen gun). Now on the bare floor -> build/omni/railgun_impact_silent.mp4 (trim 0.1): impacts 0.43 / 2.80 /
  5.21 / 7.59 s (violet-flash frame check). Omni's audio -> `sfx/railgun_generated.wav`,
  `sfx/railgun_impact_generated.wav` -> cut_sfx.py: charge = first 3 s of charge 1 (0.312-3.312 s; charge 2 came in
  short), a crackling attack then a hum that brightens ~10 dB, left ~7 dB under the shot as Omni put it; fire_0/1 =
  shots 1 and 2 (3.405 / 8.143 s, to the end of the sizzle, ~1.85 s); one gain, shot 1's first 0.3 s at -13 (1.5 dB
  over Sniper/Laser); fire_tail = shot 1 muffled, -19.5. impact_0/1 = impacts 3 and 4 (5.303 / 7.597 s, both from
  silence), 1.8 s, own gain at -14 (a missile blast). Omni scored no arc crackle (user: fine, use the Tesla crackle):
  RailgunEffects.charging already plays tesla.crackle (0.5, pitch 0.8-1.1, 1 in 3 ticks once the arcs show at 65 %),
  now the Omni crackle, ~7 dB under the hum. Game-schedule mix with those crackles: build/sfx-sheets/railgun_preview.wav
  (seam dip 4 dB for 5 ms; fire + impact together peak -0.1, the placeholders were hotter). 157/157.
  Plasma Cannon (10-16): `-Pshowcase=video_plasma_cannon -PnoDevMods`, Large Turret Base, one Plasma Cell at each
  PCANNON_SHOTS (3.4 s apart, so a blast dies before the next charge), husks ~20 blocks out so the ball flies ~0.9 s
  (at 10 blocks it was 0.4 s: the shot's ring-out would sit under the blast); camera behind the gun's shoulder.
  Take 1 with husks: Omni returned NO audio track (second time, after the railgun impact on husks): the targets are
  now NoAI magma cubes (size 2; invisible husks still render to the spectator camera) ->
  build/omni/plasma_cannon_silent.mp4 (9.9 s, trim 0.3): charges 0.25 / 3.58 / 6.95 s (1 s), shots 1.29 / 4.59 /
  7.91 s, blasts 2.35 / 5.58 / 8.94 s (blast-flash frame check). Omni then refused the 9.9 s clip ("edited video
  duration 9.875 s does not match config duration 10 s": it re-times to 24 fps): clips are now exactly 10.0 s, 240
  frames at 24 fps (ffmpeg `fps=24,trim=start=<t>:duration=10 -r 24`). Omni's audio -> `sfx/plasma_cannon_generated.wav`
  -> cut_sfx.py (files plasma/cannon_*): one charge only (0.325-1.33 s, 1 s, as the game's; user: fine); shots 1.33 /
  4.63 / 8.05 s (~1 s each), blasts 2.37 / 5.63 / 9.01 s, a stray burst at 7.20 s where charge 3 should be. fire_0/1 =
  shots 1 and 2, hit_0/1 = blasts 1 (2.2 s, dies out) and 2 (to 7.18 s); shot 3 runs into blast 3. One gain: shot 1's
  first 0.3 s at -14 (between Railgun and Sniper); charge ~3 dB under the shot as Omni put it (as Tesla, Railgun).
  Game-schedule mix (2.5 s cycle, blast 0.6 s after the shot): build/sfx-sheets/plasma_cannon_preview.wav, seam
  smooth. 157/157.
  Repulsor and Repulsor Dome (10-16): two clips. Targets are cave spiders with removeFreeWill(), not NoAI: NoAI mobs
  skip physics and were never pushed (the GameTests' spawnWithNoFreeWill only drops goals); magma cubes squashed,
  stretched and dropped flames when they landed. `-Pshowcase=video_repulsor -PnoDevMods`: Repulsor (T1, Creative Ammo
  taken out, it pays FE), three pairs right / left / behind it, 500 FE at REP_SHOTS; each push throws one pair out of
  range and it turns to the next -> build/omni/repulsor_silent.mp4 (10.0 s @24 fps, trim 0.1): charges 0.34 / 3.03 /
  5.73 s (0.5 s), waves 0.87 / 3.55 / 6.25 s. `-Pshowcase=video_repulsor_dome`: Dome on a Large Turret Base, six
  spiders at radius 2.2, 2000 FE at DOME_SHOTS; burst 1 throws them to ~8 blocks (still in range), burst 2 out ->
  build/omni/repulsor_dome_silent.mp4 (trim 0.1): charges 0.35 / 4.82 s, domes 0.86 / 5.36 s (brightness frame
  check). Repulsor audio -> `sfx/repulsor_generated.wav` -> cut_sfx.py: three waves from silence (0.90 / 3.66 /
  6.11 s), each a whoosh swelling ~0.5 s and gone in ~1.5 s; push_0/1 = waves 1 and 3; push_tail = wave 1 muffled;
  one gain, wave 1's loudest 0.3 s at -14.5. No charge from Omni (neither clip): repulsor.charge = wave 1's tail
  reversed (1.81-2.41 s), 0.6 s so its loud end fades under the push's swell (a 0.5 s version left an 18 dB hole for
  40 ms; now a 5 dB wobble). Preview: build/sfx-sheets/repulsor_preview.wav. 157/157.
  Dome take 1 (user: the bursts sound like gunshots, a sharp crack then a boom; no charge): re-shot close up
  (camera -5/+3.2/-4.5 from the base; the ring spinning up is in frame, the dome sweeps past the camera) ->
  build/omni/repulsor_dome_silent.mp4: charges 0.35 / 4.85 s, domes 0.87 / 5.35 s; new prompt asks for wind and a
  pressure wave with no sharp transient. Take 2 (user: "better, like wind now"; again no charge) ->
  `sfx/repulsor_dome_generated.wav` (take 1 kept as repulsor_dome_take1_generated.wav) -> cut_sfx.py: gusts from
  silence at 0.79 / 5.32 s, a wind roar held ~1 s, gone by ~3 s; dome_burst_0/1 = each gust from ~70 ms in (its
  first rise from silence left a hole under the charge's fading end), dome_burst_tail = gust 1 muffled; gain: gust 1's
  loudest 0.3 s at -15; dome_charge = gust 1's tail reversed (1.75-2.35 s, 0.6 s, as the Repulsor's). Preview:
  build/sfx-sheets/dome_preview.wav, seam within the wind's own wobble. 157/157.
  Ion Rifle (10-16): `-Pshowcase=video_ion -PnoDevMods`, T1, Creative Ammo taken out, 1200 FE at each ION_SHOTS
  (1.8 s apart; logged as the capacitor dropping), three NoAI magma cubes close together (the core zaps the other
  two), camera from the side and above -> build/omni/ion_silent.mp4 (10.0 s @24 fps, trim 0.1): shots 0.54 / 2.20 /
  3.99 / 5.79 / 7.60 s, bolts land ~0.1 s later. Only ion.fire to cut (2 variants; it is also the far sound).
  Omni's audio -> `sfx/ion_generated.wav` -> cut_sfx.py: five shots from silence (0.600 / 2.230 / 4.070 / 5.820 /
  7.675 s, zap-crack and sizzle, ~0.9 s); fire_0/1 = shots 2 and 5 (least alike by spectrum); gain: shot 2's first
  0.3 s at -15.5. Preview at the game's 0.8 s rate: build/sfx-sheets/ion_preview.wav. 157/157.
  Mortar (10-16): `-Pshowcase=video_mortar -PnoDevMods`, Large Turret Base, one Mortar Shell at each MORTAR_SHOTS
  (4.5 s apart), three NoAI magma cubes ~15 blocks out (fire-proof: the patch does not burn them); the log gives
  launch, the top of the arc (where RocketEffects starts mortar.whistle) and the blast. The arc tops out ~22 blocks
  up, so the camera sits 16 back and tilts up to hold it (closer, the shell left the frame for 2.5 s and fell into
  view only 0.3 s before the blast) -> build/omni/mortar_silent.mp4 (10.0 s @24 fps, trim 0.1): launches 0.47 / 4.80
  s, tops 1.93 / 6.29 s, blasts 3.55 / 7.86 s. To cut: fire (2), fire_tail, whistle (the blast stays rocket.explode,
  the patch flamethrower). Omni's audio (user added fire crackle to the prompt) -> `sfx/mortar_generated.wav`:
  launches 0.49 / 4.85 s, blasts 3.595 / 7.835 s with a quiet crackle after, NO whistle (the falling shell is small).
  cut_sfx.py: fire_0/1 = launch 1 (0.487-1.85 s) and the same 0.94 lower (launch 2 sits on blast 1's crackle),
  levelled to rms -20 (first 0.3 s -14.1); fire_tail = launch 1 muffled. The patch keeps flamethrower.burning (an
  Omni crackle already; this take's is under the blast tails). Whistle clip: `-Pshowcase=video_mortar_fall`, camera
  low by the cubes looking up the shell's way down (eye 1.8 up: at 0.5 the player's feet sank into the floor and
  the view came out level) -> build/omni/mortar_fall_silent.mp4 (10.0 s @24 fps, trim 0.1): launches 0.55 / 4.83
  s, tops 2.03 / 6.30 s, landings 3.60 / 7.85 s (just under the frame). Not used: the user dropped the whistle (10-17)
  and the fall scene went with it. mortar.whistle REMOVED: BastionSounds.MORTAR_WHISTLE, the sounds.json entry, en/id
  subtitles, gen_sounds.py's mortar_whistle, and sounds/mortar/whistle.ogg (git-tracked: stage the deletion at commit,
  as flamethrower/ignite.ogg); RocketEffects no longer starts a Roar for shells (rockets and missiles keep theirs).
  68 events, no missing files, no orphans. 157/157.
  Reloads (user 10-14, plain SFX generator prompts): both takes came out looped (sound wraps round the file end).
  missile.reload = slide + clunk 0.50-0.72 s minus the hiss after it (user: "delete the artifact"); new event
  `rocket.reload` (BastionSounds, sounds.json, en/id subtitles): `WeaponAnimatable.tickTubes` now returns true when a
  rocket starts sliding into an empty tube; ClientTurret plays it then (Rocket Launcher only: a fresh mount of any
  weapon empties the tubes too), vol 0.5, pitch +-10%. Client-only, no GameTest. 69 events, 155/155.
  Missile flight sound (user 10-14 "travel belum beres suara udah hilang"): RocketEffects gave missiles no roar on
  purpose (12 at once); now they roar at 0.6 (rockets 0.9), at most MAX_MISSILE_ROARS 3 at a time (a waiting one
  starts mid-flight); `rocket.fly` attenuation 32 -> 64 (missiles fly 48). Client-only.
  Rocket Launcher Salvo (user 10-14; answers: same DPS, all 4 at one target): `rocket_launcher.json` `salvo_interval`
  2 (that key is what shows the GUI fire-mode button). RocketLauncherWeapon keeps `WeaponState.spentTubes` (spent,
  not loaded, so fresh/loaded launchers start full; not saved), reloads the lowest spent tube every fire interval, or
  8 ticks after the shot for a lone tube in single mode (so the clank never lands on the next launch); RackSync carries
  the spent mask, `WeaponAnimatable.tickTubes(hasAmmo, spent)` slides a rocket in only once the server reloaded it.
  Single mode unchanged (shots every 35-36 ticks, measured). Tests `rocketSalvoFiresFourThenReloadsAll` (RED: shots
  every 36 ticks) and `rocketSingleFiresEveryIntervalAndReloadsAtOnce` (RED: no tube ever spent). 157/157. Version:
  next release 0.2.0 (see top).
  Salvo rework (user 10-14 22:04: clank before the rocket shows; salvo fired before the 4th rocket appeared; wants the
  four "literally together, side by side"): salvo fires all four tubes in ONE onFire, each from its own tube mouth
  (`TUBE_MOUTHS` = geo muzzle_0..3, (+-2.15, +-2.15, -10.8) px, via new `WeaponType.modelPoint`, which
  MissileLauncherWeapon.mouth now uses too); the base pays one ammo, the other three `takeOneAmmo` each. A reloaded
  rocket seats after `SLIDE_TICKS` 14 (client RELOAD_TICKS uses the same constant); `seatTimer` = slide + 6 hold, and
  salvo ready() waits for it; the first reload after a salvo comes `interval - 20` ticks in, so the cycle stays 4
  intervals (144). `salvo_interval` is 0 (only its presence shows the GUI button). Client: the clank plays when a
  rocket seats (the take's hit is at its start); several fire events from one turret in one tick (a rocket salvo, a
  missile volley) kick and sound once, each still flashes at its tube. Test renamed
  `rocketSalvoFiresFourTogetherThenReloadsAll` (RED: [16, 17, 19, 21]; checks same tick, rockets >0.2 apart, gap
  4 intervals, and >SLIDE_TICKS after the last reload; negative control with the old timing fails "left 1 ticks
  after"). 157/157.
  Flight loop repeated audibly (user 10-14 22:23): rocket.fly rebuilt as 8 s of random 0.25 s grains from the steady
  flight of all three Omni shots (`texture()` in cut_sfx.py: sine window, half overlap = constant power), looped;
  envelope self-similarity max 0.12 (the 1.34 s loop: ~1 at its period). Roar pitch now random 0.95-1.05 per
  instance (a 4-rocket salvo's roars, started in the same tick, no longer play identical samples). 157/157.
- **Early-game turrets: DESIGN ONLY (user 10-14 23:13, "untuk nanti banget"), spec in PLAN.md section 8 "Turret early
  game".** Makeshift Turret Base (locked: only the 4 early modules, no tier/modules/FE/Connector, HP 50) + Cobblestone
  Turret, Snowball Repeater, Potato Shotgun, Arrow Launcher (vanilla arrows incl. tipped/spectral with effects, 6 per
  volley, mortar-like arc). Crafting table, all need iron. Exceptions to NO-VANILLA recorded there. Version not decided
  (recommended 0.3.0). Next step when the user wants it: writing-plans from that section; no code yet.
  `tools/cut_sfx.py --sheet <file> <s>...` draws waveform + log spectrogram with cut marks into build/sfx-sheets/ (user
  10-11, idea from github.com/Bodila51/opus-sound-layer; plasma check: the take opens with UI-like beeps, shots every 250 ms
  from 1.85 s; cut starts 9-13 ms before the high-band onsets 3.095/3.331/3.585).
  Open: which events to drop (user: "several sound is unnecessary"), the licence of the generated .wav files, whether
  `sfx/` sources get committed.
- **User 22:31, built 22:40:** (1) projectile hit sounds removed for now (`impact.kinetic`, `plasma.hit_small`, `ion.hit`:
  playback, events, files, gen_sounds entries; 69 events). (2) `/bastiondev turrets [t1|t2|t3]`: one armed base per weapon
  module with Creative Ammo (dev test `devCommandGivesArmedTurrets`). (3) Zombie groan spam: every turret hit clears the
  damage cooldown, so each bullet played the hurt sound; `damage/TurretHurtSounds` (via `Hitscan.damage`, now also used by
  rockets, blasts and burn) lets a mob's hurt sound through once per `turretHurtSoundInterval` (10 t) at
  `turretHurtSoundVolume` (0.5), death sounds untouched (test `turretHitsGroanOncePerInterval`, RED "groaned 5 times").
  `feedHubDropsOwnShare` now clears loose items right before its break (placing its hubs over a hub left at that spot
  spilled 10 rounds after the clear at the start; the new tests had moved it there). 155/155.
- Test jar for another PC (user 19:42): `./gradlew build --offline -PwithDevCommands` -> `build/libs/bastion-1.20.1-<v>-dev.jar`
  = the mod + `/bastiondev` (DevCommands; `automation` only when Create is loaded; max_health cap raised via
  `ObfuscationReflectionHelper` SRG `f_22308_`). Never upload the `-dev` jar; the store jar is the plain `./gradlew build`.
- Tests: **155/155** GameTests (152 + the hang test + hurt-sound test + dev turrets-command test)
  Flaky, to investigate: `ControlHubTests.powerPortTakesFeOnlyWhenFormed` failed once ("did not form" at tick 5) in ~12
  runs on 10-10 (22:24), passed on the rerun; not touched by that change (sounds). Suspect: leftovers in the reused arena. (`./gradlew runGameTestServer --offline`; with `-PwithCompatMods` also 152). 128 at `4abf5ab`.
  The flaky one was `feedHubIdleIsCheap` (wall-clock limit), now a same-thread ratio test (NOW 0).
- Open user decisions: upgrade modules still drop from destroyed turrets (user 14:45 "biarkan dulu"); the "no new turrets
  until the placeholder sounds are replaced" rule (23:37) was lifted by the user 10-09 23:40 (the user is making SFX from
  `docs/SOUND_SHEET.md`; `tools/gen_sounds.py` only writes missing files now); addon "turret pack" support is backlog for
  ~1.0.0 (PLAN 7.6 end). Plasma effects are green `#3DFF6E` (user 01:08), the Ion is blue `#8099FF`.
- The user played the new turrets with TaCZ + Spore (10-10 01:47, 06:23): no errors. The latest Cannon changes (BFG
  blast 06:15, tank model 06:28, one muzzle 06:35, taller 06:38) were checked in the showcase scenes, not yet in the user's game.
- Deferred minors (offer when relevant): NOW item 3 below (fire-rate display cap, Mortar range vs Range Mk2/3, tooltip x.5%
  rounding, unused `MODIFIER_TIERS`, long Range recipe tab), the placement hologram item (clicked cell not red for an
  entity, hologram where a click opens a GUI / adventure / F1, scene shots) and from the Ion/Plasma review: ion beams can
  be lost when the bolt lands right at max range with lag; an evicted tracer (512 cap) skips its own impact effect;
  datapack edge values not clamped (`field_ticks` >127); no Cannon `charge` clip (glow + orb do the job).
- Scenes run in the scratch world `-PshowcaseWorld=showcase_fx` (a copy of the user's `showcase`); never the default.

**NOW (read first, newest first):**
0. **Turret Control Hub: built 10-10 (user request 11:25, design "oke" 11:43, spec "oke" 11:51, plan "native" 12:44),
   revised the same day into a 3x3x3 cube + Hub Connector (user 15:01, design 15:10, spec 15:19, built 15:40-16:30,
   native execution); uncommitted, not yet tested by the user in game.** A hand-built 3x3x3 multiblock (like the
   Tinkers' smeltery) to reach every turret of the base from one place. User answers 11:30/11:39/15:01-15:10: T3 reach
   **7x7 chunks** (satellite in the middle), turrets shown = **the ones holding a Hub Connector linked to this hub** that
   the player may open (`canConfigure`), **FE via a Hub Power Port**, batch = **whole Targeting tab + on/off**, the
   Satellite Uplink **stands on the cube's top middle block** (1x1x2 mast + spinning dish, bigger per tier; 5 tall in all).
   - Package `dev.bastion.hub`: `ControlHubStructure` (one layout check: server forms, client draws the blueprint).
     `slots(iface, facing)` = the 26 cube blocks besides the Interface in problem order: bottom layer, middle, top; in each
     layer the front row first, left to right seen from in front (index 0 bottom-front-left, 10 middle-front-right, 12 the
     hidden core, 21 top middle, 25 top-back-right). `center` = one block behind the Interface (the core; reach is counted
     from its chunk), `satellite(center)` = `center.above(2)`. Formed casings/ports carry `x`/`y`/`z` = alone/low/middle/
     high (`FeedHubBlock.Part`, so the Feed Hub edge models outline the cube); `partCenter` reads them back, which is how a
     hub tells its parts from another hub's (one block belongs to one hub). `notifyAround` schedules every Interface 2 out
     sideways, 1 up and 2 down (an Interface sits 1 above a bottom part, 1 below a top part, 2 below the satellite).
     `HubInterfaceBlock`/`BlockEntity` (status UNFORMED/ONLINE/OFFLINE in block state, re-check 1 tick after a part changes
     + every 20 ticks, FE 200,000, upkeep [20, 60, 150] FE/t, OFFLINE without it, back ONLINE only after
     `controlHubRestartTicks` (100) ticks of upkeep, confirmed 15:10), `HubCasingBlock` (x/y/z), `HubPowerPortBlock`/
     `BlockEntity` (receive-only FE, 2,000/t per port, nothing when not formed), `SatelliteUplinkBlock`/`BlockEntity`/`Item`
     (1x1x2, GeckoLib clips folded / deploy / spin / offline), `HubEdit` (one batch change: validate, apply), `FilterMix`.
     Config: `controlHubEnergyCapacity`, `controlHubPortMaxInput`, `controlHubUpkeep`, `controlHubRange` [1, 3, 7],
     `controlHubRestartTicks`.
   - **Hub Connector** (`HubConnectorItem`, stacks to 16, main tab): NBT `HubLink` {`Pos` long, `Dimension` string};
     `onItemUseFirst` on a Hub Interface (formed or not) links the whole stack in hand and never opens the hub screen;
     shift + use in the air clears it (only in the air: vanilla also calls `use` after a sneaking click on a block, so `use`
     passes while `getPlayerPOVHitResult` hits a block in reach); tooltip = the hub's coordinates or "not linked"; item property `bastion:linked` lights
     the lamp (`models/item/hub_connector.json` override). `link/isLinked/linkedTo` (position + dimension).
   - Turrets: `TurretInventory.CONNECTOR` (index 14, `SIZE` 15), always open on every tier and base, only a
     `HubConnectorItem`; automation never sees it (`AmmoOnlyItemHandler` exposes ammo slots only); a destroyed turret drops
     it with its modules, a mined one keeps it; `setSize` never shrinks below 15, so 14-slot saves (and mined turret items)
     load with an empty Connector slot. GUI: `TurretMenu.CONNECTOR_X/Y` (128, 55) between the ammo grid and the modules,
     faint plug sprite at (216, 66) of `gui/turret.png` while empty, hover tooltip. `HubInterfaceBlockEntity.connected
     (turret)` gates `visibleTurrets` (roster) and `reachable` (remote turret GUIs through `TurretMenu.stillValid`,
     `HubAction` OPEN, batch edits): taking the connector out closes a GUI opened through the hub.
   - Screen: `ControlHubMenu` (slot-less; `HubRoster` S->C every 10 ticks: header + up to 256 turrets nearest first, each
     turret's filter only when it changed, max 32 filters per packet) and `ControlHubScreen` (user 16:56: the panel fills
     most of the screen, `layout()` sizes map / side column / list rows / batch lists from it, min 256x200, max 640x400,
     drawn by code (`panel()`), one text size per group via `scaleFor`; map + / - buttons, left-drag pans (a click opens on
     release), one zoom level per wheel notch or trackpad swipe (scroll events < 200 ms apart), list rows from a trackpad's
     fractional deltas; tabs Map / List / Batch
     settings; `HubMap` terrain texture from client chunks, redrawn every 100 ticks; `HubSelection` keeps selection, tab,
     zoom and view while the player visits a turret GUI and comes back; with no connected turret the Map shows "No
     connected turrets: link a Hub Connector to this hub, then put it in a turret" wrapped over the map, the List the same
     line through `fit()`). `HubAction` C->S: OPEN / BACK / EDIT, re-checked per turret (`reachable`: online, player within
     8 blocks of the Interface, in reach, loaded, connected, `canConfigure`). Remote turret GUIs reuse `TurretMenu` with a
     hub link (open data); "◀ Map" in `TurretScreen`. `TurretConfigUpdate.apply` needs the sender's open, valid
     `TurretMenu` for that turret. Protocol 9 -> **10**. `MobList` (client) is the mob search shared by the turret
     Targeting tab and the batch tab.
   - Client: `HubBlueprint` (right-click an unfinished Interface: 10 s blueprint, cyan boxes + turning item where a part
     is missing, red where wrong / another hub's, amber on every casing without a Power Port; a cube-tall scan sheet when
     it forms), `ControlHubEffects` (scan + dish flash at `center` + 3.8 on forming, dish hum loop while online),
     `SatelliteUplinkRenderer` (glow cyan / red / dim). Assets: `tools/gen_control_hub_models.py` (casing/port/interface
     models + blockstates; formed parts get the Feed Hub frame + glow line on every face at a cube end, x/y/z; the
     Interface, mid-face, has none), `tools/blockout/design_control_hub.py` (3 satellite geos + clips), textures in
     `gen_textures.py` (incl. `gui/control_hub.png`, `hub_interface_front.png`, `item/hub_connector(_linked).png`, the
     connector sprite in `gui/turret.png`), recipes in the Module Workstation's own tab (8 Hub Casings; 4 Hub Connectors
     = 2 copper, 1 redstone, 1 ender pearl, 60 t, 1,000 FE; 127 workstation recipes), 4 placeholder sounds
     (`control_hub.online/offline/dish_loop/ui_select`, SOUND_SHEET 67-70).
   - Changed while building (rulings in the ledgers): first version: the Interface model is one full cube with a front
     texture cut open over a 3 px recess (partial lip faces z-fought with the edge glow lines); the map hint is small print
     (0.75); the map texture is created right before it is drawn; `fit_item_display.py` starts a missing GeckoLib item
     model itself. Revision: the old tooltips that still said "3x3" (Interface, Casing, Power Port, Satellite Uplink,
     "needs a Satellite Uplink") now describe the cube; two plan test setups fixed (a cube with no port has 26 casings;
     the cube's bottom layer replaces the GameTest floor at y 1).
   - Scene `-Pshowcase=hub` (`ControlHubShowcase`, scratch world `showcase_fx`): T1/T2/T3 cubes standing on the floor
     (Interfaces one block up, `FIELD` = floor anchor of the turrets), an unfinished cube (no satellite, the middle layer's
     front-right casing missing) with its blueprint, day/night, Map / List / zoom / turret GUI (Connector slot filled) /
     back / Batch / OFFLINE screens; every turret but the Ion Rifle holds a connector linked to the T3 hub (header "5
     turrets"). All checked 16:16.
   - Flaky test found and fixed first: `feedHubIdleIsCheap` (TurretFeedHubTests) timed 200 idle ticks against a fixed 40 ms
     and failed in 3 of 9 runs (56-76 ms); a probe showed CPU time = wall time and no GC: the server thread just ran slower
     (M4: 4 fast + 6 slow cores). Now it times 243 stacks against 1 stack on the same thread (best of 5) and needs < x20:
     x2-4 normally, x122 with the per-tick memory disabled (checked RED, code restored).
   - Tests: `gametest/ControlHubTests` (24): cube layout (26 slots, x/y/z of every part, slots 0/10/12/21/25 pinned), first
     problem (incl. stone in the bottom and top layers, bottom named first), other hub's parts
     (shared wall), break/unform (bottom, top, satellite), broken Interface, chunk border, satellite halves, upkeep per
     tier, OFFLINE/ONLINE, port intake, recipes (incl. the connector), reach square, connector link (+ other dimension,
     shift-clear in the air only), Connector slot (only connectors, no automation), old 14-slot save, connector drops, who is listed +
     filter send-once, only connected turrets listed / opened / batch-edited, remote GUI validity (+ connector taken out),
     config needs a valid GUI, batch edit, bad input + 256 cap, FilterMix, underfed hub. Negative controls this session:
     the old `notifyAround` reach fails the break test; a `linkedTo` without the dimension fails the connector test; a
     reversed layer order fails both order tests.
   - First version's final review (fresh reviewer, opus): no Critical, 2 Important, both fixed test-first (the underfed
     hub flipping ONLINE/OFFLINE every tick -> `controlHubRestartTicks`; the dish hum silent after a rejoin / F3+T).
   - Deferred minors from the first review (offer when relevant): `HubSelection.returning` can stay set when "◀ Map" does
     not reopen the hub; the roster's filter change check uses `CompoundTag.hashCode` (two category flips in one 10-tick
     window can cancel); `TurretMenu.stillValid`/`ControlHubMenu.reopen` call `getBlockEntity(hub)` before `isLoaded`
     (a cross-dimension /tp with a hub-opened turret GUI loads one chunk); test gaps (close-range `TurretConfigUpdate.apply`,
     `visibleTurrets` vs `covers` are two "in reach" codes); `controlHubRange` has no upper bound. Also a dev-only one:
     scenes set the hotbar slot on the server only (`selected = n`, no packet), so a scene after one that saved another
     slot shows an empty main hand (the placement scene's first run today).
   - Revision's final review (fresh reviewer, opus, 16:25-16:42): no Critical; 2 Important fixed test-first (see State at
     a glance); 14 "declined to judge" lines ruled in the ledger (e.g. pistons can push formed casings with their glow
     lines, first-version behaviour; the core may hold the only Power Port, as the spec says).
   - Deferred minor from the revision's review (offer when relevant): the link test covers only an unformed hub through
     `onItemUseFirst` (a click through `gameMode.useItemOn` on a formed hub would pin "links, never opens the screen").
     (The other one, the List tab's shrunk empty message, went with the bigger screen: centred messages now wrap.)
1. **Ion Rifle, Plasma Repeater, Plasma Cannon built (user 10-09 23:40 "gas implement", Native execution), committed 10-10 06:41 (`4abf5ab`),
   not yet tested by the user in game.** Spec PLAN §8, plan `docs/superpowers/plans/2026-10-10-ion-plasma-turrets.md`.
   Shared: `weapon/Splash` (who a burst reaches: box distance, no line of sight), `Bullets.filtered` (filter without
   range), `Bullets.create/launch` (bullets from anywhere; launched during the level's bullet step they join after it),
   damage type `turret_plasma`. Ion: `IonRifleWeapon` (bolt -> `IonField` packet with the zapped ids, capped at 64 beams;
   client `IonEffects`: streak + core flash/ring + `BeamRenderer` beams, chained to the bolt's tracer landing). Repeater:
   `PlasmaRepeaterWeapon` (splash via Splash, MG heat; client: teal heat glow, `plasma.vent` sound). Cannon:
   `PlasmaCannonWeapon` (orb -> **BFG-style blast** since 10-10 06:15, see below; charge orb via LaserChargeRenderer x1.6). Client `TracerRenderer`: orb tracers, trail in blocks, early landings,
   `onLanding(id, task)`; arrivals now run after its tick loop (the Cannon crashed the client with a CME until then).
   Protocol 9. Plasma Cell + Empty Plasma Cell (Ammo Workstation / Charging Station), 9 placeholder SFX (gen_sounds.py
   now only writes missing files, `--force` rewrites all), models/clips/textures/parts/recipes, lang en/id. 19 new
   GameTests (132). Scenes `-Pshowcase=ion` / `plasma` (EnergyShowcase, clears the stage first) checked day + night:
   ion streak, core ring, star beams; repeater balls, splashes; cannon charge orb, big ball, blast.
   Final review (fresh reviewer) fixes, each RED -> GREEN: a Cannon swapped mid-flight crashed the server (canTarget read
   the new weapon's missing min_range; shared `WeaponType.outsideMinRange` with default 0 now in Rocket/Missile/Railgun/
   Mortar/Cannon); the ring flew at head height over short mobs (now feet + 0.5); fragments skipped a mob pressed against
   the struck one (start where each ray leaves the box; `Hitscan.trace` counts a ray starting inside a box as a hit, all
   weapons). Deferred minors: TracerRenderer late landing / eviction / EARLY cap, datapack edge values (field_ticks >127,
   fragments >32 or <0), no Cannon charge clip (code-driven glow instead).
   User 01:08: plasma green `#3DFF6E` (was teal `#3DFFB5`, read cyan).
   **User 06:01-06:06: Cannon ring -> explosion** (the fragments mostly missed: they flew ahead past the front mob), after Vic's
   Point Blank BFG 9000 (frames studied from the user's Short): blast radius 4, 14 at the centre -> 7 at the edge, struck
   target 18 + blast, no LOS/knockback/block damage; charge and shot unchanged. Removed: fragments, `PlasmaBurst`, the
   `plasma.fragment` sound, TracerRenderer EARLY landings (M3 gone). Visual `VfxPresets.PLASMA_BURST`: white flash, green
   shockwave, FIREBALL cloud thrown to the edge + glowing lime blobs, sparks, dark specks, light/shake/flash, big scorch (on
   the ground below when it hit a mob). `VfxParticle.fireRamp`: a cool colour darkens in its own hue (no red/soot); fire
   colours unchanged. Tests: `cannonBlastHitsEveryoneAround`, `cannonBlastOnAWall` replace the 6 ring tests (128 total).
   **User 06:28: Cannon model redesigned** after Kromlech "Battle Tank Turret: Plasma Cannon": armoured tank hull (yaw only:
   stepped glacis, riveted skirts, bustle + vents, dome hatch, glowing coil tubes on the rear deck) and a thick coil barrel
   that pitches on a front mantlet (model pitch pivot at z -10, the server still aims from the axis: pivot_height 0.71875 since
   user 06:38 "pipih" -> taller hull on a raised turret ring, muzzle_length 2.14375; one round muzzle since 06:35). Gunmetal + cyan kept (palette rule); `glow_coils` turn green while charging. Parts re-mapped.
   User 01:00 "tembakan diperbesar, terutama ion": ion = `TracerRenderer.spawnBolt` (thick streak 0.4 + glowing head +
   deep alpha-blended halo so it reads by day; Gun tracer is 0.075), beams 0.42; plasma balls 0.28 / orb 0.6 / fragments 0.22.
   Also fixed on the way: flaky `repulsionHeavyMobsGoLess` / `repulsionTravelsAsSet` (pushed at tick 5 while the mobs
   were still landing; now tick 15); `moduleListsTurretsItWorksOn` no longer hardcodes 13 weapons.
   **Sound sheet (user 18:18), uncommitted:** `docs/SOUND_SHEET.md`: all 58 events + the 9 planned ones, each with
   search keywords + an AI prompt, durations/variants/reach read from sounds.json and the files; 8 P1 to replace first;
   4 events are registered but never played (debug.ping, turret.servo, machine_gun.spin_up/down). Replaced files must
   not be overwritten: `tools/gen_sounds.py` rewrites every sound file.
2. **Repulsor Dome reach 7 → 9 (user 14:45, push unchanged at 6), committed 10-08 16:00:** `repulsor_dome.json` range 9; test
   `domeReachesNineBlocks` (husk 8.5 out, RED at 7 → GREEN); dome sphere/dust ring follow the range (scene repulsor
   checked). 113/113. User 14:45: destroyed turrets keep dropping their upgrade modules for now ("biarkan dulu").
3. **Module tiers Mk1/Mk2/Mk3 (user 13:35, design + 4 answers 13:39, plan "oke, langsung" 13:43), committed 10-08 16:00.** Spec PLAN
   §4.6 "Tier modul", plan `docs/superpowers/plans/2026-10-08-module-tiers.md`. `ModifierItem(props, mk)` + `mk()` +
   `family()` (Mk1 id); `BastionItems.modifier()` registers `<id>_mk2/_mk3` right after each Mk1 (`MODIFIER_TIERS`);
   `TurretInventory.hasModifierElsewhere` compares families. Data + item models from `tools/gen_modifier_tiers.py`
   (x1.5/x2, pierce x mk, weapons copied); upgrade recipes in `gen_recipes.py` (Mk2: +2 diamond +4 redstone 100 t 4k FE;
   Mk3: +netherite scrap +2 diamond +amethyst shard 140 t 8k FE, Range or Choke tab); chips: frame graphite/cyan/gold +
   1-3 pips (`modifier_chip(name, mk)`); lang "<name> Mk2/Mk3" + `tooltip.bastion.modifier.mk`. Tests
   `TurretModifierTierTests` (one per kind, scaling of every field, upgrade at the Module Workstation, upgrade by
   hopper), **113/113**. Final review fixed: hopper-fed upgrades jammed the Module Workstation (modules stack to one and
   filled all 6 inputs) → `WorkstationBlockEntity.acceptsInto`: automation opens an empty input only while the inputs
   hold less of that ingredient than one craft takes. Deferred minors: fire rate shown above the real 1 shot/tick cap
   (MG + Overclock Mk3 + Rapid Cycler Mk3: Info 26/s, real 20/s; clamp in StatSheet); Mortar range with Range Mk2/Mk3
   exceeds a shell's ~45-block reach; tooltip rounds x.5% (Overclock Mk2 shows -22%); `BastionItems.MODIFIER_TIERS`
   unused; Range tab now lists 24 recipes in a 4-row list.
   Found on the way: GameTest arenas keep loose items an earlier batch left (a turret broken when the next arena was
   placed spilled 10 rounds); the two drop-counting tests now discard items present at their start. Scene
   `-Pshowcase=modules` (hotbar: all models load).
4. **Railgun trail overshoot (user 12:51, there since the Railgun was built), committed 10-08 13:32 (not pushed):** the shot event carries the
   path's far end (range, MISS); the trail head runs `speed x (age+1)` = 40 blocks on frame one and was only trimmed by
   `trail.land()` when the tracer *arrived*, so for a tick it drew past the target. `TracerRenderer.Landing` got a default
   `known(end)`, called from `TracerRenderer.land` the moment the server's `BulletImpact` arrives; RailgunEffects trims
   the trail there. Shown with a temporary side-view, wall-less railgun scene: frame 82 trail to the screen edge before,
   ends at the husk after (scene restored). 109/109.
5. **Mod compat (user 11:10), committed 10-08 13:32 (not pushed):** `-PwithCompatMods` (build.gradle `compatMods`: Fungal Infection: Spore
   2.2.0j `PbOZOahW`, TaCZ 1.1.8-hotfix2 `AzCBJlex`, runClient + GameTests). Dev `CompatModTests` (pass at once without
   the mods): Spore Infected is shot and attacks an unarmed turret (worked as is: `Infected extends Monster`, Calamity/
   Organoid implement Enemy, so TargetFilter HOSTILE + TurretAggro apply); TaCZ picks bullet hits with
   `EntityUtil.findEntityOnPath` = box swept by motion + `isPickable && !isSpectator` → turret hitbox was never hit. Fix
   (user picked "server-side"): `TurretHitboxEntity.isPickable()` = `!level().isClientSide` (client crosshair still goes
   to the block). RED with the old value, GREEN; 108/108 with and without the compat mods. Owner protection unchanged
   (user: own turrets only fall to hostile mobs); for testing `/bastiondev owndamage true|false` sets trustedCanDamage.
   Not checked by hand: a real TaCZ gun shot (needs the user's gun pack). User 12:19: `/bastiondev busser` added (dev test
   `devCommandSpawnsBussers`, RED→GREEN, 109/109 with and without compat mods) to test turrets vs flyers and stingers.
   Watch: Mortar `grounded()` knows vanilla flyers only; Spore's Busser is a Monster (marker FlyingInfected).
6. **Railgun fix (user 10:31, screenshot: raised Railgun ignoring a zombie crowd ~7 blocks out), committed 10-08 10:53 (0.1.2 work, not pushed).**
   Two root causes, each pinned by a test that failed first: (1) `railgun.json` `min_pitch` -10 → **-30** (from a base
   one block up the head of a ground mob 6.5 out is 12° down: `TargetSelector.firstVisible` rejected it → IDLE;
   `railgunShootsDownFromARaisedBase`); (2) `Bullets.step` dropped a whole step when its far end was unloaded or out of
   the world, so a 40 b/t slug aimed down near y -64 (superflat) vanished even with a target 2 blocks ahead; now the
   step is halved until its end is loaded (`fastBulletIntoTheVoidStillHits`, all bullet weapons). **105/105** twice.
   Not checked on screen: the barrel at -30° (computed: clears the base edge by ~0.5 block).
7. **Placement hologram (user 00:44, design B 00:46, plan approved "native" 00:53), committed 10-08 10:53 (0.1.2 work, not pushed).** Spec PLAN
   "Hologram penempatan multiblock", plan `docs/superpowers/plans/2026-10-08-placement-hologram.md`, ledger in
   `.superpowers/sdd/2026-10-08-placement-hologram/`. `turret/Multiblock` + `Footprint(blocks, blocked, front)`:
   LargeTurretBaseBlock/WorkstationBlock `footprint(BlockPlaceContext)` feeds both `getStateForPlacement` and the
   preview (1x1 stations: null). Client `PlacementPreview` (RenderLevelStageEvent **AFTER_PARTICLES**; AFTER_TRANSLUCENT_BLOCKS
   drew nothing) builds the click's context from `mc.hitResult` (main hand's block wins), draws boxes
   (`BastionRenderTypes.ghostBox()` fill 15% + vanilla line box 60%) and `GhostRenderer` (GeoObjectRenderer,
   `PlacementGhost.of(blockIdPath, tier)`, `GhostModel` resets shared bones to rest + `showTier`, translucentEmissive,
   40% alpha, cyan / red 0xFF3A30). Tests `TurretPlacementTests` (large base + assembler with a blocker + `stationRefusesToBuildInsideAMob`), **103/103**.
   Final review (fresh reviewer): fixed workstations building around mobs in their other cells (pre-existing; now
   `isUnobstructed` like the large base). Deferred minors: clicked cell not red for an entity; hologram shows where the
   click would open a GUI / adventure mode / F1; scene lacks red-assembler and large-base night shots.
   Scene `-Pshowcase=placement` (PlacementShowcase): checked free/blocked/west-facing ghost vs placed/main hand wins/night.
8. **Dev command (user 00:28):** `/bastiondev` also spawns `skeleton` (bow, drop chance 0) and `ravager`, count up to
   200 (sunflower spiral, one body width apart), HP up to 5,000 (vanilla max_health cap 1,024 raised in DevCommands'
   static init, src/dev only). Dev GameTest `DevCommandTests.devCommandSpawnsTankyMobs` runs the real command (failed
   before, passes). **100/100.**
9. **Health lights, creative T2/T3 bases, module Shift tooltip (user 23:55; tooltip = "turrets it affects"),
   committed 10-08 10:53 (0.1.2 work, not pushed).** `EmissiveLayer.healthGlow(health)`: cyan, yellow < `DAMAGED_FRACTION` 0.5, red blinking
   (10-tick cycle bright/dim) < `CRITICAL_FRACTION` 0.25, used by the **base glow only** (user 00:15: weapon stays cyan,
   the earlier weapon tint was removed). User 00:15 also: bigger base glow (design_base: slits 2 x 3.4 px, seam
   10.9-11.6 depth 0.4; design_large_base: slits 12 x 2, seam 11.2-12 depth 0.35) and a fix for cyan showing through
   yellow/red: base glow bones use gen_textures material `status` (neutral STATUS_BASE albedo, glows like `emitter`)
   instead of cyan `emitter`. Large Base idle clip no longer spins `energy_ring` (straight bars on a square cut through
   the body while turning). Sparks 1/40 per tick below half, 1/8 below a quarter.
   Creative: `BastionItems.tab` adds `TurretBaseItem.withTier(item, T2/T3)` after each base. Modules: `ModifierEffect.affects`
   (fits + per stat: damage>0, heat_per_shot>0, turn speed>0, lead_accuracy param, bullet_speed/slug_speed for pierce,
   ammo_per_shot>0, spread>0; range/fire rate/max HP/reload = all), `ModifierItem.worksOn()`, Shift via DistExecutor +
   Screen::hasShiftDown; > half the turrets → "every turret except". Tests `moduleListsTurretsItWorksOn` + creative T2/T3
   asserts; **99/99**. New dev scene `-Pshowcase=health` (HealthShowcase: T1 Gun bases + T3 Large Tesla bases at
   100/40/15% HP, day/night/large shots): colours pure, weapon cyan, no diagonal bars.
10. **Destruction rule (user 23:42, answers: modifiers drop / ammo lost, damaged base keeps its tier), committed 10-08 10:53 (0.1.2 work, not pushed):**
   `TurretBaseBlockEntity.destroy` drops only the modifier slots, clears weapon + ammo, drops DAMAGED(_LARGE)_TURRET_BASE
   with `BlockEntityTag.Tier` when T2/T3. `WorkstationBlockEntity.result()` puts the first consumed ingredient's tier on
   the result (so the Part Assembler repair recipes give T2/T3 back); `outputFits` checks that real result. Damaged items
   show "Tier N" (`BastionItems.damaged`). Tests: `destroyedLosesWeaponAndAmmo`, `destroyedT3KeepsTierOnDamagedBase`
   (replace the old T1/T3 drop tests), `assemblerRestoresDamagedTier`; failed before, pass after. **98/98.**
   Also the **Feed Hub fix**: a refused real insert goes back into its slot (`feedAmmo`, was voided). Store text
   (DESCRIPTION.md/.html) already describes the new rule: it ships with 0.1.2 (DESCRIPTION_UPDATE_0.1.1.md untouched).
11. **Balance (user 23:01), committed 10-08 10:53 (0.1.2 work, not pushed):** Mortar `damage` 6 → 20; `burn_damage` 1.5 → 3 per second in
   both `mortar.json` and `flamethrower.json` (the only two users of TurretBurn). 97/97 GameTests (the mortar test still
   sees the husk burning after the blast).
12. **Lighter camera shake (user 22:53 "terlalu geter kalau banyak turret dan gw deket"), committed 10-08 10:53 (0.1.2 work, not pushed):**
   `CameraShake.add` takes the max instead of adding (sources no longer stack; PLAN 5.6). Presets: ROCKET_LAUNCH
   0.3/10 → 0.15/8, ROCKET_EXPLOSION 0.75/18 → 0.5/14 (Missile explosion untouched, 0.35/12), DOME_BURST 0.9 → 0.5
   (radius 10). Shake = trauma²: rocket blast at 3 blocks 0.39 → 0.15, dome at 2 blocks 0.52 → 0.16, and 4 turrets
   together no longer hit the cap. 97/97 GameTests; shake can't be seen in a screenshot: the user checks it in game.
13. **UV fix in 3 older models (user 22:41 "boleh perbaiki dulu semua"), committed 10-08 10:53 (0.1.2 work, not pushed):** boxes of different
   sizes shared one kit tag, so they shared one UV region (paint_model keeps the first face's paint: a bigger face
   repainted it, a smaller one lost its dark bevel row). New tags: Machine Gun `bladev` (fan), `gring2` (barrel ring),
   Flamethrower `pipe2` (feed), Part Workstation `drawer2` (bottom drawer), `wrench2`. **Guard:** `Kit.region()` now
   asserts a reused tag has the same face size (all 4 design scripts pass; negative control fires). Changed: 3 geo +
   6 textures + kit/design scripts. 97/97 GameTests; scenes default/elemental/workshop checked (scratch world
   `run/saves/showcase_fx`, a copy of `showcase`). `blockbench/machine_gun_turret.bbmodel` was already out of sync
   with its geo (left alone). `build/libs/bastion-1.20.1-0.1.1.jar` was rebuilt and now contains this fix (tag v0.1.1
   does not). Changelog block "0.1.2 (not released yet)" in UPLOAD.md; `mod_version` still 0.1.1.
14. **0.1.1 released to GitHub (2026-10-07 ~20:05, user: "commit, push"):** commits `fad802f` (Feed Hub multiblock + energy,
   turret GUI shift-click fix, Creative Power Source pull fix, Pipez dev mod) and the 0.1.1 commit (Repulsor + Repulsor
   Dome, version bump), tag `v0.1.1`, pushed. **The user uploads `build/libs/bastion-1.20.1-0.1.1.jar` to CurseForge and
   Modrinth** (fields + changelog in `docs/release/UPLOAD.md`). Store text: the user pastes only the changed parts,
   `docs/release/DESCRIPTION_UPDATE_0.1.1.md` (7 parts, Markdown + HTML, since the beta.2/beta.3 text on both stores).
   New gallery images `11_repulsor_dome`, `12_railgun_charge` (screenshots/ + screenshots-840/, captions in UPLOAD.md),
   from `-PnoDevMods -Pshowcase=repulsor` (low camera, 8 husks) and `-Pshowcase=railgun` frame r_barrel_29. Changelog in
   UPLOAD.md reordered (new turrets first, then Feed Hub, then Fixes). **Uncommitted since `v0.1.1`:** the store
   docs above (DESCRIPTION.md/.html markers + FE/config sentences, UPLOAD.md, DESCRIPTION_UPDATE_0.1.1.md, 4 images),
   HANDOFF, and the RepulsorShowcase camera/8-husk tweak; commit only on "commit" (not part of the jar).
   Versioning (user 20:04): stays on the **Beta** channel
   with plain numbers `0.1.1`, `0.1.2`, ...; the first Release will be **1.0.0**. Never commit `modlist.html`.
15. **Feed Hub multiblock + energy: built** (design 15:35, approved 15:47 incl. the full 4a look; defaults taken without
   an answer: 8 items/tick **per block**, FE of a broken block is **lost**). See "Feed Hub multiblock" below.
16. **Released in 0.1.1: Repulsor (1x1) + Repulsor Dome (2x2)** (user idea 18:29: turrets can't hit
   mobs hugging/behind their barrel; these push valid targets away, no damage, FE). Decisions 18:32: 1a names, 2a FE,
   3a no damage, 4a 1.5 s / 4 s with a 0.5 s charge; approach A (weapon modules); spec PLAN "Repulsor & Repulsor Dome";
   plan `docs/superpowers/plans/2026-10-07-repulsor.md`. **92/92 GameTests**, scene `-Pshowcase=repulsor` checked,
   independent review done (wall-mount push fixed, dome shake radius 10).
   **User test 19:45: buffed** (Repulsor range 5 / push 4, Dome range 7 / push 6) + **heavy mobs** (config
   `repulsorHeavyMobs` = iron golem, ravager, warden; Repulsor never targets them, Dome pushes them x`repulsorHeavyMobPush`
   0.4, their vanilla KB resistance ignored; the hop is now the same for every mob so distance scales evenly). **97/97 GameTests.**
   Tested in game by the user (19:45 buff, 20:00 orb colour fix). See "Repulsor" below.

**Repulsor (built 2026-10-07 19:15):**
- `weapon/Repulsion.push(target, from, fallback, blocks, lift)`: horizontal launch `blocks x SPEED_PER_BLOCK (0.2)` +
  lift 0.35, scaled by (1 - knockback resistance); calibrated by GameTest `repulsionTravelsAsSet` (lands 2.99 / 4.98
  blocks for 3 / 5). NoAI mobs ignore physics (scene uses `removeFreeWill`).
- `RepulsorWeapon` (`repulsor.json`): AREA_PULSE, FE like Tesla (`cost()` from energy_per_shot 500, cap 10k, in 500/t),
  turns 12°/t, pitch -90..90, charge 10 t, every 30 t; cone 35° **from the pivot** (from the horn's mouth a husk beside
  the target fell outside and took a second pulse; test asserts one pulse), line of sight from the pivot, shares
  `FlamethrowerWeapon.inCone`. `RepulsorDomeWeapon` (`repulsor_dome.json`): never turns, `sight` = no line of sight,
  all valid targets within 6 of the pivot (orb, pivot_height 1.0) pushed 5, 2,000 FE (cap 40k, in 2,000/t), every 80 t.
- Client `RepulsorEffects`: wave = REPULSOR_MUZZLE + 4 SHOCKWAVE_RING rings + haze + a translucent air cone (`Wave`,
  6 ticks) along the barrel (not toward the first mob); dome = sphere mesh (12x24, rim-bright, `vfx/dome.png` streaks)
  growing to range in 8 t then fading 8 t, a sand dust ring drawn on the floor at the sphere's edge (shaded by light:
  the render type is fullbright), DOME_BURST (flash, speed-line sparks, shake, flash). Dust at each pushed mob
  (REPULSOR_HIT). Charge: Repulsor horn rings `glow_coil_0..2` light via `coilFill`, Dome ring `dome_ring` spins via
  `focusAngle` (WeaponModel `setRotY`), glow brightens (`chargeGlow`). Sounds `repulsor.*` placeholders.
- Models `design_weapons.py repulsor()/repulsor_dome()`, clips hand-written, parts `repulsor_emitter/coil/mount`,
  `dome_core/pylons/mount` (parts.json, gen_recipes), test arena `yard` (28x28). GameTests `TurretRepulsorTests` (9).
- Review (19:25): pushes start from where the base is attached (`alongMount(0)`), so a wall Repulsor pushes mobs off
  the wall (fallback: the facing); dome shake radius 10. Not reproduced, kept as guard tests: aim/push mismatch behind
  a fence (line of sight uses 1-block outlines), aim on the mount axis. Deferred minors: riders / KB-resistance-1 mobs
  still cost FE, visuals use data.range() not stats.range() (Range Module), wave drawn from the muzzle, focusAngle
  unbounded.
  `gen_structures.py` rewrites every template with a new gzip timestamp: restore the untouched ones from git.

**Feed Hub multiblock (built 2026-10-07 16:55, PLAN "Feed Hub" → "Multiblock + energi"):**
- Shapes 1x1x1..3x3x3 (square, H <= W). A hub's place lives in blockstate `x`/`y`/`z` = alone/low/middle/high
  (`FeedHubBlock.Part`), so no BE structure data, client gets it with the block. Placing/breaking schedules a 1-tick
  block tick (`FeedHubBlock.tick` → `FeedHubStructure.reform`): flood fill of connected hubs (cap 512, loaded only),
  re-partition from scratch, lowest block first (y, x, z), largest shape first; `setBlock(UPDATE_CLIENTS)` only.
  `FeedHubStructure.Box.of` reads a structure back from the states (never loads chunks).
- `FeedHubBlockEntity.Group`: one structure for one tick, built by the lowest block (controller) and shared:
  members (slot order), `CombinedInvWrapper` of their 9-slot buffers, feeds (standard bases on outer faces with their
  back on it + Large Turret Bases with all 4 blocks on top, dedup by core), FE (per-block `energy` int, fill/drain in
  order, per-tick input `received`). A member whose controller is not loaded runs alone. Capabilities on every block are
  views over `group()` (`StorageView`, `EnergyView` receive-only), so a pipe's cached handler stays right across merges.
  Controller tick: `feedAmmo(8 x blocks)` (least stock first), `feedEnergy()` (least charged first, turret max_input).
- Config (server, per block): `feedHubEnergyCapacity` 50000, `feedHubMaxInput` 4000, `feedHubItemsPerTick` 8.
- GUI `FeedHubMenu`/`FeedHubScreen` 192x194, side tabs **Summary** (size, turrets + up to 12 weapon icons, FE bar, up to
  8 ammo totals; ContainerData ints as 16-bit halves) and **Storage** (9x4 window, scroll by wheel/bar →
  `clickMenuButton(row)`; server slots = `Window` into the structure, client slots = plain mirror; shift-click fills the
  whole structure). Textures `gui/feed_hub.png` + `feed_hub_plain.png` (`feed_hub_gui(plain)`).
- Look: `tools/gen_feed_hub_models.py` writes the multipart blockstate + `feed_hub_panel` + 6 `feed_hub_frame_*` + 6
  `feed_hub_glow_*` models (full-face overlays, strip at the texture top turned by face `rotation`; all glow after all
  frames so lines meet in the corner); lone hub keeps `block/feed_hub`. Textures `feed_hub_panel/edge/edge_glow`.
- GameTests `TurretFeedHubTests` now 13: forms, splits, sharesStorage (cached handler sees the merge), feedsLargeBase
  (Mortar: an idle Missile Launcher loads its tubes from its slots), dropsOwnShare, newControllerFeeds, powersTesla,
  menuScrolls (server side of the GUI), partialShiftClickSaves, staleStateStaysApart, skipsRemovedTurret (review fixes),
  scrollFollowsShrink, idleIsCheap + the 2 old ones unchanged. Scene `-Pshowcase=feedhub`: lone hub, 2x2x1 + Tesla,
  3x3x2 with five turrets, Creative Power Sources, day/night, GUI Summary/Storage/scrolled.
- Create's Item Vault source (MIT) was read for reference (deferred forming, combined handler); code is our own.
- Review (fresh reviewer 16:55): fixed (1) partial shift-click out of Storage did not mark the hub changed (dupe on
  reload), (2) `Box.intact`: a block whose structure is not whole + loaded runs alone (a just-broken block's stale
  states read a wrong box spanning two structures; a chunk-border box could force-load via `TurretBaseBlock.core`),
  (3) a turret removed earlier in the tick makes the Group stale. Then fixed on the user's go (17:16): **turret GUI**
  shift-click merge/partial take-out was never saved (item loss / dupe on reload; `TurretSlot.setChanged` →
  `TurretInventory.changedInPlace`, GameTest `menuShiftClickMarksTurretChanged`, in the beta.5 changelog), Storage scroll
  follows a shrinking structure (`FeedHubMenu.scroll()` clamps; `feedHubScrollFollowsShrink`), idle cost of a full
  structure (ammo kinds with no room remembered per tick: 200 ticks of a full 3x3x3 + 27 full guns 238 → 11 ms;
  `feedHubIdleIsCheap`, fails over 40 ms). Still deferred (user decides): real insert result ignored in feedAmmo,
  `stillValid` ignores `isRemoved`, two misplaced registry comments (BastionBlocks:36, BastionBlockEntities:36).
- 17:55 (user test with Pipez): Creative Power Source exposed no ENERGY capability, so pull-based pipes never attached;
  it now offers endless extract-only FE on every side (GameTest `creativePowerSourceCanBePulled`; dev GameTest
  `PipezEnergyTests` runs real Pipez: source -> 2 energy pipes -> Feed Hub, negative control checked). **83/83 GameTests.**
  Pipez keeps a pipe's connections in its blockstate and only recomputes them on `neighborChanged`: pipes placed before
  the fix must be broken and re-placed.

**Released (public beta):**
- **0.1.1** committed, tagged `v0.1.1` and pushed 2026-10-07 ~20:05 (user asked). Upload pending by the user.
- GitHub `AlifRizza/bastion-turrets` is **public**. **0.1.0-beta.4** committed, tagged `v0.1.0-beta.4` and pushed
  2026-10-07 13:30 (the user asked for the push this time; otherwise the user pushes). Tags beta.1-4 all on GitHub.
- **beta.4 upload**: the user uploads `build/libs/bastion-1.20.1-0.1.0-beta.4.jar` to CurseForge and Modrinth
  (fields + changelog in `docs/release/UPLOAD.md`).
- **CurseForge**: beta.1 and beta.2 uploaded by the user (beta.2 = `e43a732`, must stay as uploaded).
- **Modrinth**: user is creating the project; **beta.3** (`build/libs/bastion-1.20.1-0.1.0-beta.3.jar`) is its first
  version. Icon `docs/release/icon.png` (Modrinth refused AI art; this one is rendered from our model).
- beta.4 = the balance changes, Railgun and Mortar below (user tested in game, "commit" 13:30). For the working tree
  now see NOW above.

**In beta.4 (2026-10-07 ~01:10): balance** (`mod_version=0.1.0-beta.4`). Waiting for the user's
in-game test and "commit". Never commit `modlist.html`.
1. **Tesla Coil**: `tesla.json` `bolts` 2 → 5, new `bolts_per_target` 2 (user pick): `TeslaWeapon` throws
   `min(bolts, bolts_per_target x targets)` round-robin, so 1 mob takes 2 bolts (20, same as before), 3 mobs 2+2+1, 5+
   mobs 1 each; leftover bolts are not thrown. FE 3k per discharge kept (user pick). GameTests renamed
   `teslaZapsFiveTargets` (6 husks, exactly 5 struck) and `teslaLoneTargetTakesTwo` (alive at ~0.3 HP after one discharge).
2. **Shotgun**: `shotgun.json` `damage` 2.5 → **5** per pellet (user pick over the proposed 4.0).
Docs updated: tooltips en_us/id_id, TESTING.md, DESCRIPTION.md + .curseforge.html, UPLOAD.md (beta.4 changelog, version
fields, Tesla gallery caption). Screenshot `03_tesla_coil` still shows 2 bolts (caption says "up to five": still true).
3. **GameTest fix (build.gradle)**: `runGameTestServer` uses its own world `run/gametestworld` and deletes only its
   `entities/` before each run. It used to share `run/world` (runServer's) and saved arenas' mobs and drops on exit; the
   next run loaded them back into its tests (entities load after an arena is cleared), so drop/target counts failed at
   random once the arena layout changed. Wiping the whole world instead cost ~25 s of terrain per run and once hung on a
   vanilla worldgen race (`MissingPaletteEntryException` in the light engine). `run/world` is no longer touched.

**In beta.4 (2026-10-07 ~12:00): Railgun** (user design, approved 10:45-11:14, accepted in game 12:09; PLAN
7.6). In the beta.4 changelog and both descriptions.
- Large module `railgun_turret`, weapon `railgun.json` (type `bastion:railgun`, `RailgunWeapon`): 60 dmg, damage type
  `turret_rail` in `minecraft:bypasses_armor` + `always_hurts_ender_dragons` (NOT `is_projectile`: endermen would dodge),
  x`boss_multiplier` 2 vs `forge:bosses`, range 80, charge 60 t + 40 t, slug 40 b/t (Bullets, lead), `min_range` 5,
  pitch -30..60 (was -10, 10-08), turn 3°/t. Cost: 1 `rail_slugs` (Ammo Workstation) + 8k FE (cap 80k, 2k/t). Shockwave: `BastionExplosion`
  with damage 0 (push only, valid targets) at the hit or block (`Bullets.Hit#blocked`, new). Target order: new hook
  `WeaponType#targetOrder` (biggest max HP first, GUI priority breaks ties).
- Model `tools/blockout/design_weapons.py railgun()` (Kit 256; RG_* constants = railgun.json pivot/muzzle and the client's
  coil fractions): muzzle_0 tip, muzzle_1 breech end of the channel, muzzle_2/3 rail tips, eject_0 vents; bones
  `glow_coil_0..4`. Charge visuals: `WeaponAnimatable.coilFill/chargeGlow` (ClientTurret) hide unlit bands
  (`WeaponModel`) and blend the glow to violet (`WeaponModelRenderer.glowColor`); arcs `RailgunEffects`; slot core,
  afterglow and the ion trail with helix `RailRenderer`. Presets `RAIL_*`. Sounds `railgun.*` (gen_sounds).
- Parts `railgun_rails/receiver/mount` (parts.json, gen_recipes LARGE_PART). Info tab shows Ammo instead of Bolts for it.
- GameTests `TurretRailgunTests` (5): needs FE, needs slugs, ignores armor (exact 60 through diamond), picks the boss +
  x2 (Wither with `setNoAi`: a free Wither flies over the turret, out of reach), shockwave pushes the valid husk, not
  the cow. **63/63 pass** (twice). Scene `-Pshowcase=railgun` (RailgunShowcase) checked: fill, arcs, shot, trail, push.

**In beta.4 (2026-10-07 ~12:40, fixed 13:25): Mortar** (design approved 12:09; PLAN 7.6). In the beta.4 changelog/descriptions.
- Large module `mortar_turret`, `mortar.json` (`MortarWeapon`): high-arc ballistic solve (`highArc`, re-solved from the
  muzzle 3x, elevation 45..86), **no lead** (user 13:15: it shells where the target stood when it fired; walkers can
  escape, it is for crowds; the lead first built looked like homing), `sight` = feet if the arc is clear
  (parabola cast in 2-tick segments, cached in `WeaponState.arc*` for 10 t), `canTarget` = grounded (no FlyingMob,
  FlyingAnimal, AmbientCreature, Blaze, Vex, Allay, Wither, Dragon, noGravity) + `min_range` 8. Shell speed 1.5, gravity 0.05
  (range 45 flat), every 60 t, 1 `mortar_shells`. Blast = `stats.damage` 20 (was 6, user 10-07 23:01) r1.5 (valid targets), no direct hit damage.
- Shell = third `TurretRocketEntity` type `turret_mortar_shell` via `ballistic(gravity, patch)`: gravity half before /
  half after each move (exact parabola, matches the solver), server-side, velocity synced every tick (`hasImpulse`).
- `weapon/FirePatches`: patch on the ground below the burst (clip down 4), size x size blocks, 100 t, every 5 t
  `TurretBurn.ignite` (60 t, 3/s since 10-07 23:01) on **every** living entity in it (fire-immune skipped by TurretBurn); vanilla fire in
  free spots touching a flammable block (`BaseFireBlock.canBePlacedAt`) unless config `mortarBlockFire` false. Client:
  `TurretImpactEvent` MORTAR_BLAST + FIRE_PATCH (scale = seconds, source = width) → `MortarEffects`: renders the patch
  itself (user 13:15: the whole 3x3 must look burnt; scattered particles read as ~1 block): an alpha-blended ember layer
  over exactly the square + 4 flame sheets per block (texture `vfx/fire_sheet.png`, tileable, climbing) in 3 layers, plus
  a few particles, light, crackle, scorch. Scene step `m_patch` shows a patch alone on stone. Whistle on descent (`RocketEffects`). Barrel pitch is now clamped to the weapon's elevation
  limits on server and in the client idle pose (generalised from the Missile Launcher's fixed pitch).
- GameTests `TurretMortarTests` (2) in the new tall template `pit` (26x32x13): over a wall onto a husk, husk + cow burn,
  planks catch fire; never at a phantom or a husk inside 8 blocks. **65/65 pass.** Scene `-Pshowcase=mortar` checked.

**Built, uncommitted (2026-10-07 ~15:05): Feed Hub** (user idea, the simple stand-in for the extender; PLAN "Feed Hub").
`turret/FeedHubBlock` + `FeedHubBlockEntity` (9-slot buffer = the item capability on every side; `isItemValid` = some
armed mounted turret takes it; `distribute()` moves up to 8 items/tick one by one to the mounted turret with the least
of that ammo, through the base's own ammo capability; `feeds()` = standard bases on the 6 faces whose FACING points
away from the hub). `menu/FeedHubMenu` (client uses the client BE's buffer so slot validity matches),
`client/screen/FeedHubScreen` (176x170, `gui/feed_hub.png` from `feed_hub_gui()`; `panel()` helper now shared with the
turret GUI, its PNGs unchanged). Block model: cube + fullbright glow overlay element (`forge_data` block/sky light 15).
A block item in hand passes the click, so bases can be placed on the hub. Recipe Module Workstation (TOOLS table).
GameTests `TurretFeedHubTests` (2): even split 8/8, shells to the shotgun, rockets refused, unarmed / merely adjacent
bases skipped; buffer holds when the gun is full and drains when it has room. **67/67 pass.** Scene `-Pshowcase=feedhub`.
Changelog block for 0.1.0-beta.5 in UPLOAD.md (version not bumped yet).

**Base Extender (PLAN Fase 10): "tidak direncanakan dulu"** (user 2026-10-07 14:31). Spec approved and kept in PLAN.md, no
implementation plan; postponed as long and maybe unnecessary. The user has two shorter ideas, one a much simpler
workaround for it. Do not build unless the user brings it back. Exploration notes for later: base API surface is large
(base used as weapon host by every WeaponType; plan was a `TurretMount` refactor with the base delegating to mount 0),
the extender block could reuse the `bastion:turret_base` baked-model loader (only needs FACING + a geo).

**Open items the user mentioned (do not build unasked):** Plasma Cell + its turret (Charging Station `CHARGE` table in
`tools/gen_recipes.py`); weapon module tiers Mk1/Mk2/Mk3 (bonuses **undecided**, ask); 3x3x1 large base; whether to add
an FE generator (Bastion has none; survival needs an FE mod, stated in the description); stacking the same module twice
(today refused, one of each kind per base: user asked, answered, offered to change it).

---

## 1. Project basics

- Minecraft Forge **1.20.1**, Forge 47.4.26 (min `[47.2,)`), Parchment 2023.09.03, **GeckoLib 4.8.4**, Java 17, Gradle 8.8.
- Mod id `bastion`, package `dev.bastion`, root `/Users/alifrizzaz/Documents/Minecraft Modding/Turret`.
- Version in `gradle.properties` (`mod_version=0.1.1`), jar `bastion-1.20.1-<version>.jar`, authors `AlifRizza`,
  license All Rights Reserved.
- **Git**: branch `main`, remote `origin` = GitHub (public). Commits: `df3660b` initial, `f1819a3`, `f460e59` Fase 9,
  `05d4bb9` tabs, `ae44021` release prep (beta.1), `2c08cd7` bullets + performance, `e43a732` beta.2, `f2c8179` beta.3.
  Commit **only when the user says "commit"**; the user pushes. Ignored: `run/`, `build/`, `.gradle/`, `/references/`.
- Build/run: `./gradlew build`, `./gradlew runClient --offline` (see §9), `./gradlew runGameTestServer` (**97 tests**).
- Dev runs include `src/dev` (never in the jar): dev commands, dev-only GameTests, scripted scenes.
- Dev runtime mods: Create 6.0.8 + Ponder + Flywheel + Registrate + MixinExtras (runtimeOnly); **Pipez** forge-1.20.1-1.2.26
  (runtimeOnly, Modrinth id `Mtjt7u5h`, user 17:14: Universal Pipe = items + FE for testing hubs/turrets); **ToroHealth** damage
  numbers (`clientDevMods`, runClient only; dropped with `-PnoDevMods` and in the stress scene).
- Language: answer in the language of the user's message (mostly Indonesian). Code, comments, docs in English.
- The user has ADHD-mode + ponytail output styles on: action first, short, minimal code, numbered steps, one next action.

## 2. Hard rules (CLAUDE.md + PLAN §1, §9)

- Read `docs/PLAN.md` before work; do only what is asked. Ask before guessing gameplay (PLAN 9.7).
- **NO-VANILLA visuals**: no vanilla particles, sounds, textures, explosion visuals, GUI textures, vanilla fire.
  Everything comes from our generators (`tools/`) and the VFX framework.
- No copying GPL code (`references/` is study only). Store art must not be generative AI (Modrinth rule).
- Strict client/server split (client code only in `client` packages, reached via `DistExecutor`/client events).
- Balance numbers in JSON / config / `tools/gen_recipes.py`, never hardcoded.
- After each feature: `./gradlew build` passes + a checklist in `docs/TESTING.md`.
- Never write `eula=true`. Never bypass macOS TCC or the screen lock.
- **Never overwrite `run/saves/showcase`** (the user's hand-built Create rig). Scenes run in a scratch copy:
  `cp -R "run/saves/New World" run/saves/showcase_fx` then `-PshowcaseWorld=showcase_fx`, delete after.

## 3. Decisions (user-confirmed unless marked)

| When | Decision |
|---|---|
| 10-06 Fase 0/1 | Create only as dev runtime (+ optional compat). Mined base keeps tier + inventory. GUI 216x200, no text overlap. |
| 10-06 morning | Creative Ammo (creative-only, fits every weapon, never used up). `/bastiondev zombie [hp] [count]`. ToroHealth. |
| 10-06 09:05 | Hitbox must not block clicks; zombies attack turrets (`TurretAggro`). GUI state = lamp. Players cannot melee turrets. |
| 10-06 11:00 | Redesign: uniform **gunmetal + one cyan glow**, per-weapon colours only in VFX. Base 1 block, floor/wall/ceiling. |
| 10-06 11:45 | Sniper: charge shot, pierces 2. Rocket Launcher: dumb-fire, splash only valid targets, min range 4. |
| 10-06 13:10 | **Large Turret Base 2x2x1** (floor only). **Missile Launcher**: 6+6 tubes, yaw only, fixed 30°, homing, Single/Salvo. |
| 10-06 14:35 | **Tesla Coil** (2x2, FE, 2 random valid targets). **Flamethrower** (1x1, Fuel Canister, cone, custom burn). |
| 10-06 15:45 | **Laser Rifle** (Laser Cell, single heavy beam, pierces valid targets, stops at blocks). |
| 10-06 16:40 | Weapon-specific modules (first: Choke Module). Mk1-3 weapon tiers planned, bonuses **undecided**. |
| 10-06 17:01 | **Fase 9 Workstations** (5 FE stations, crafting table only makes them, own part set per weapon). |
| 10-06 18:00 | Creative tabs: Turret Bases, Weapon Modules, Ammo, Modules, Workstations, Parts. |
| 10-06 18:30 | **Bullets with travel time** (Gun/MG/Shotgun/Sniper, "like TaCZ"), **option B**: data stepped per tick, no entities. Modpack target: tens to hundreds of turrets per base; optimise by measurement. |
| 10-06 20:00 | Public beta on CurseForge + Modrinth, beta channel, version `0.1.0-beta.N`. Repo public. |
| 10-06 20:46 | **Smoke trails** for rockets and missiles. |
| 10-06 22:40 | Flamethrower/MG must not stop between targets; flame stays on until nothing is in range. |
| 10-06 23:10 | **Autoloader** module for weapons with a reload. Same module twice: still refused (answered, not changed). |
| 10-06 23:25 | Store icon from an in-game render (no AI art). Disclaimer sentence: code AI-generated, art made by the AI through scripts and Blockbench MCP (not generative image AI). |
| 10-07 00:50 | Tesla **5 bolts, max 2 per target**, FE 3k kept. Shotgun **5 per pellet**. Version 0.1.0-beta.4. |
| 10-07 10:45 | **Railgun** (2x2, anti-boss, slug + FE, user's reference image: long two-rail barrel, drum; charge = barrel lights up then arcs over it). |
| 10-07 11:18 | **Mortar** (2x2, arcing, ground only, 3x3 fire patch burns everyone, vanilla fire on burnable blocks). Approved 12:09. |
| 10-07 12:09 | Railgun approved in game. **beta.4 ships after the Mortar** (balance + Railgun + Mortar). |
| 10-08 14:45 | Repulsor Dome range 9 (push 6 unchanged). Upgrade modules still drop from destroyed turrets (left as is). |
| 10-08 13:39 | Module tiers Mk1-Mk3: modifier modules only, Mk2 x1.5 / Mk3 x2 (Penetrator +1/+2/+3), upgrade at the Module Workstation, one per kind. |
| 10-08 11:10 | TaCZ bullets hit turrets (server-side pickable hitbox); own turrets stay protected (dev `owndamage` toggle for testing). Spore mobs fight turrets as is. |
| 10-08 10:31 | Railgun min pitch -30 (was -10); bullets no longer vanish when a step ends out of the world. |
| 10-08 00:46 | Placement hologram, design B (ghost model + per-block boxes, red where blocked). |
| 10-08 00:15 | Health colour on the base only (weapon stays cyan); bigger base glow; no cyan mixing in. |
| 10-07 23:55 | Health glow yellow < 50%, red blinking < 25%. T2/T3 bases in creative. Module Shift tooltip lists the turrets it affects (answer: affected, not merely fits). |
| 10-07 23:42 | Destroyed turret: weapon module + ammo lost, modifiers drop, base drops as a Damaged Base that keeps its tier (restored at the Part Assembler). Feed Hub insert fix. |
| 10-07 23:37 | **No new turrets for now**, until the placeholder sounds (58 events in sounds.json) are replaced by generated or found SFX. 0.1.2 = adjustments, balancing, small additions. |
| 10-07 23:09 | Addon support (TaCZ-style turret packs) goes in the backlog, after release. |
| 10-07 23:01 | Mortar damage 20; burn (Flamethrower + Mortar patch) 3 per second. |
| 10-07 22:53 | Less camera shake from Rocket Launcher and Repulsor Dome; shakes from many turrets must not add up. |
| 10-07 22:41 | Fix the UV overlap in the 3 older models (offer (a) below). |
| 10-14 21:35 | Next release is **0.2.0**, not 0.1.2: a big update bumps the middle number (still Beta channel). |
| 10-07 20:04 | Versioning: Beta channel with plain numbers (0.1.1, 0.1.2, ...), first Release = 1.0.0. Repulsor Dome orb all blue (UV fix). |
| 10-07 19:45 | Repulsor buff: range 5 / push 4; Dome range 7 / push 6. Heavy mobs (config list, default iron golem, ravager, warden): Repulsor cannot push them (ignores them), Dome x0.4. |
| 10-07 18:32 | **Repulsor + Repulsor Dome** (push mobs off the turrets, FE, no damage, 1.5 s / 4 s, charge 0.5 s). Cone measured from the pivot (19:08, so one push hits mobs crowding the turret's sides). |
| 10-07 13:15 | Mortar: **no lead** (hits where the target stood; misses walkers, for crowds); fire patch must visibly cover 3x3. |
| defaults, **not confirmed** | No bullet drop; auto-lead `lead_accuracy` 0.9; bullets fly through non-targets (old hitscan hit anything); speeds Gun 6, MG 5, Shotgun 4, Sniper 12 b/t. `sweep_tolerance` Flamethrower 180, MG 30. Autoloader +40%, recipe nuggets/redstone/copper/piston. Mismatched ammo never fires. Precision Lock x1.5 stacks with headshot. Default targets Hostile+Boss. Repair kit 40%. Large base HP x2.5. Tesla 60k/1k per tick. Flamethrower ignores fire-immune mobs. Workstation FE 50k, 2k/t. License All Rights Reserved. |

## 4. What exists

Fase 0–7 complete; Fase 8 partial (lang en_us/id_id done; Jade/JEI/Ponder compat and profiling vs PLAN budgets not
started; Embeddium/Oculus checked, see §7); Fase 9 (workstations) built.

**Blocks**: `turret_base` (1x1, any face), `large_turret_base` (2x2, `quadrant`/`core`), five stations, `creative_power_source`.

**Items**: 9 weapon modules, ammo (kinetic_rounds, scatter_shells, sniper_rounds, rockets, missiles, fuel_canister,
laser_cell, empty_laser_cell, creative_ammo), **10 modules** (8 general + choke_module + autoloader), repair_kit,
tier_upgrade_kit_t2/t3, turret_configurator, damaged (large) turret bases, 33 parts. Six creative tabs (by item type).

**Weapons** (`data/bastion/turret_weapons/*.json`, types in `BastionWeaponTypes`):

| Weapon | Profile | Key numbers |
|---|---|---|
| Gun | bullet 6 b/t, Precision Lock | 7 dmg, 32 range, 12 t, lock 20 t → x1.5 (+x1.5 headshot) |
| Machine Gun | bullet 5 b/t, spin-up, heat, slowness, sweep 30° | 2.5 dmg, 24 range, 12/s at full spin, 0.5 ammo/shot |
| Shotgun | 8 bullets 4 b/t, falloff, knockback, pump | **5/pellet**, 12 range, 24 t, spread 12° (Choke: 7.2°) |
| Sniper | charged bullet 12 b/t + laser sight | 24 dmg, 64 range, 30 t charge, pierces 2 |
| Rocket Launcher | projectile, lead, splash, smoke trail | 8 + 12 splash r3.5, 40 range, 36 t (Autoloader: /1.4), min range 4 |
| Missile Launcher (large) | homing, fixed 30°, 12 tubes, contrails | 7 + 6 splash r2.2, 48 range, reload 30 t/tube (Autoloader: /1.4) |
| Tesla Coil (large) | ARC, never turns, FE | 10/bolt **x5 bolts, max 2 per target**, 16 range, 12 t charge + 18 t, 3k FE/discharge |
| Flamethrower | CONE, custom burn, sweep 180° | 1.2/pulse every 2 t + burn 3/s 4 s, 8 range |
| Laser Rifle | CHARGED beam, pierces all valid targets | 28, 40 range, 50 t charge + 50 t, 1 Laser Cell/shot |
| Railgun (large) | charged slug 40 b/t, ignores armor, boss x2, biggest first, push shockwave r4 | 60, 80 range, 60 t + 40 t, 1 slug + 8k FE |
| Mortar (large) | ballistic shell (45-86°), no lead, ground targets, over walls, 3x3 fire patch 5 s burns everyone | 20 blast r1.5, 8-40 range, 60 t, 1 shell; patch burn 3/s |
| Repulsor | no damage, cone 35° push, line of sight, FE, ignores heavy mobs | push 4 blocks, range 5, charge 10 t, every 30 t, 500 FE |
| Repulsor Dome (large) | no damage, push everything around, through walls, FE, heavy mobs x0.4 | push 6 blocks, range 7, charge 10 t, every 80 t, 2,000 FE |

**Turret systems**: tiers T1–T3 (module slots 1/2/4), HP via `TurretHitboxEntity`, regen, repair, destruction,
owner/trusted, redstone, filters, configurator, modifiers (one of each kind per base, weapon-specific ones), servo + scan
sweep, lead, state machine, comparator, ammo-only automation, FE for energy weapons.

**VFX**: per-weapon muzzles/impacts, bullet tracers, rocket/missile smoke trails (`SmokeTrailRenderer`) + custom
explosions, Tesla arcs, laser beam + charge, flame jet + burn, scorch decals, workstation effects, dynamic lights,
camera shake, screen flash. Debug: `/bastion vfx <preset>`.

**Network** (`BastionNetwork`, PROTOCOL **"8"**): registered packets TurretEvents (S→C bundle), WeaponDataSync,
TurretConfigUpdate (C→S), BurnSync, WorkstationAction (C→S). `sendNear` queues TurretStateSync, TurretFireEvent (bullet
`speed`), BulletImpact, SpinSync, RackSync, TurretImpactEvent (`source` = exploding rocket's entity id); one packet per
player per level tick.

## 5. Architecture notes that are easy to get wrong

- **Mount frame**: turret logic in turret space (+Y = mount axis); `toWorld/toLocal` use `facing().getRotation()`.
- **Weapon hooks** (`WeaponType`): `tick`, `fireInterval`, `ready`, `chargeTicks`, `canTarget`, `aimPoint` (lead),
  `sight`, `outOfAmmo`, `firesAsItReloads` (Rocket Launcher: reload_speed also shortens its fire interval).
- **Targeting**: a lost target triggers a search the same tick (`searchTimer = 0`); `Aim.sweep_tolerance` applies within
  `SWEEP_GRACE` (10 t) of the last shot so bursts continue while swinging. Charge holding: a charge survives aim drift.
- **Never use `EntityHitResult#getLocation` / `ProjectileUtil.getEntityHitResult` for points**: it is the target's feet.
  Rockets use `TurretRocketEntity.impactPoint` (flight segment vs box); the aiming laser uses `Hitscan.trace`.
- **Homing**: missiles lead on `targetVelocity`, smoothed 0.8/0.2 per tick (raw velocity of blast-knocked mobs made them weave).
- **Smoke trails** (`SmokeTrailRenderer`, fed by `RocketEffects`): ribbon through tick positions; a flying rocket's own
  tick point is skipped (it lies ahead of the interpolated model) and the ribbon starts at the drawn nozzle; edges shared
  per point; `end(id, at)` on `TurretImpactEvent` (by id, never nearest) runs the trail into the blast, since the server
  removes a rocket a tick before its hit point. Texture `vfx/smoke_trail.png` (`smoke_strip` in gen_textures.py).
- **Bullets** (`weapon/Bullets`): `Bullets.fire` steps once at once; stepped at LevelTickEvent START, `Hit.endTick` at END.
  Fire event shots carry the bullet id in `blockState`; `TracerRenderer.spawnBullet` flies until `BulletImpact` lands it.
- **Baked base** (`BakedTurretBaseModel`, loader `bastion:turret_base`): built from `GeckoLibCache` per facing x tier x
  quadrant, tier via ModelData; `TurretBaseRenderer` draws only `energy_ring` + glow faces. Muzzle-flash dynamic light no
  longer brightens the base.
- **Glow pass** (`GlowCubes`): only faces with alpha in the `_e` texture, unsorted render type. Shader packs show it white.
- **Modifiers**: `ModifierEffect` fields are summed (`plus`); `reload_speed` → `StatSheet.reloadRate`; `weapons` list makes
  a module weapon-specific; `TurretInventory.hasModifierElsewhere` refuses a second copy of a kind.
- **GeckoLib mirrors geo X** (model +X is the weapon's left). **Render buffers**: finish one render type's pass before
  `getBuffer` of another. `TurretBaseBlock.core(level, pos)` resolves any base block.
- **Energy weapons**: turret `energy`, receive-only FE capability; Creative Ammo pays for shots. Tesla bolt hits carry the
  target id in `Shot.blockState`.
- **Workstation multiblock**: part index = x + width·(z + depth·y); `WorkstationBlock.core`. Parts render as model pieces
  (`assets/bastion/parts.json`, `PartModel`).

## 6. Asset pipeline

- Geometry: `tools/blockout/` → `assets/bastion/geo/*.geo.json` (after `design_weapons.py`, `git checkout` the
  missile_launcher geo: different bone order than the committed file).
- `tools/gen_textures.py` (models, `_e` glow, items incl. module glyphs `MODIFIER_GLYPHS`, particles, GUIs, `smoke_strip`):
  deterministic. Single texture: `python3 -c "import gen_textures as g; ..."` from `tools/` (see TEXTURES dict).
- `tools/gen_recipes.py`: all survival recipes (workstations); edit tables, run it.
- `tools/gen_sounds.py` (placeholders), `tools/fit_item_display.py`, `tools/gen_structures.py` (GameTest arenas).
- **Store icon**: `./gradlew runClient -Pshowcase=icon -PshowcaseWorld=showcase_fx` (T3 Gun Turret on lime, angles
  15/25/35/325/335/345) then `python3 tools/make_icon.py [angle]` (default 335) → `docs/release/icon.png`.
- Store screenshots: `docs/release/screenshots/` (full) + `screenshots-840/` (CurseForge description, < 850 px); take new
  ones with `-PnoDevMods` (no ToroHealth numbers). `DESCRIPTION.curseforge.html` is generated from DESCRIPTION.md
  (python `markdown` in a scratch venv, Modrinth links swapped for CurseForge ones, screenshot markers → `[IMAGE: ...]`).

## 7. Testing

- **GameTests: 97 pass**: TurretRepulsorTests 14, TurretFeedHubTests 15, TurretMortarTests 2, TurretRailgunTests 5, TurretBaseTests 5, TurretCombatTests 7 (bullets take time, pass non-targets),
  TurretSystemsTests 22 (Choke, creative tabs, zombie attacks turret), TurretLargeTests 7 (Autoloader reloads faster),
  TurretTeslaFlameTests 7 (flamethrower sweeps to next target), TurretLaserTests 4, TurretWorkstationTests 7, dev
  CreatePlacementTests 1, dev PipezEnergyTests 1. Negative controls were checked for the bullet pass-through and sweep tests.
- **Scenes** (`-Pshowcase=<name> -PshowcaseWorld=showcase_fx`): default, `weapons`, `missiles`, `automation`,
  `elemental`, `laser`, `workshop`, `stress`, `icon`, `railgun`, `mortar`, `feedhub`, `repulsor`, `health`, `placement`, `modules`. Shots in
  `run/screenshots/showcase_*.png`. Store shots: add `-PnoDevMods` (no ToroHealth numbers).
- **Load test**: `-Pshowcase=stress -PstressCount=200 [-Pjfr=<file>]` over the spawn chunks; `[stress]` MSPT/FPS lines.
  `-PstressMobs=400 -PstressWeapon=machine_gun_turret` (10-10): 400 zombies packed as /bastiondev spawns them, measured
  alone, then with the turrets. Mac M4, 60 MG + 400 zombies: empty 2.2 ms, zombies only 8.5 ms, combat 9.6 ms (turrets
  ~1 ms; JFR: ~9% of server time in Bastion code, the rest vanilla movement/collisions/AI). The user's PC (Ultra 5 225F)
  showed ~100 ms in the same kind of test: cause not found yet (asked for a spark profile).
  With -PwithCompatMods (Spore + TaCZ), 60 MG, 400 packed mobs (`-PstressMob=`): vanilla zombies 16.2 ms alone / 21.2 ms in
  combat; Spore `inf_human` 17.8 / 12.0 ms (21 of 60 turrets destroyed in 15 s). JFR: Bastion ~9% of server time in both,
  Spore's Infected AI 44% with infected; top Bastion frame `TurretHitboxEntity.tick`. The scene now discards leftover
  entities first (persistent stress mobs stayed in showcase_fx and skewed the first run).
  **User's PC instance replayed here (10-10 21:00)**: the user copied their Prism instance to `1.20.1(1)/` in the repo root
  (never commit it). A real Forge 47.4.13 dedicated server in the session scratchpad (user accepted the EULA themselves),
  their 45 server-side mods, a copy of `New World`, simulation distance 24, forceloaded 13x13 chunks round (137, -13),
  /bastiondev 600 zombies @100 HP + 50 ravagers @50 HP, JFR: 86 ms before the spawns (their earlier persistent test mobs
  are saved in the world), 150-184 ms after. Server thread: vanilla 57%, Sabotage Mobs 26% (SabotageAttackGoal.tick,
  config string matching per tick), Enhanced AI + InsaneLib 13%, Bastion 0.9%. Without Sabotage Mobs: 67 ms before,
  76-79 ms after (Enhanced AI ~30%, Bastion ~5% early). Advice given: `/kill @e[tag=bastion_dummy]` between tests.
  Without Enhanced AI + Sabotage, dummies killed first, 800 zombies: 13.8 ms before, 24-33 ms after (TPS 20; vanilla 84%,
  Bastion ~0%). That world still held 523 untagged zombies + 413 items and only 7 of the user's 60 turrets (2 after the
  wave): the PC's 60-70 ms were ~1,300 zombies with almost no turrets left, plus the client sharing the CPU.
  Mac (M4 Air): 200 turrets idle 75-80 FPS, combat 55, MSPT ~7; 400 turrets combat 39 FPS, MSPT 10.
- **`-PwithPerfMods`** (Radium, AI Improvements, ModernFix, FerriteCore, Clumps): combat MSPT -35%, spikes gone; tests pass.
- **`-PwithRenderMods`** (Embeddium + Oculus, MakeUp shader in `run/shaderpacks`): Embeddium identical; with shaders the
  model glow is white and holograms green (known limit in DESCRIPTION.md).
- Dev commands: `/bastiondev zombie|skeleton|ravager [hp <= 5000] [count <= 200]` (+ `busser [hp] [count] [variant]` when Spore is
  loaded, variant via Busser#setVariant(int): default 0, kidnapper 1, kamikaze 2, noxious 3 = stingers, transporter 4; `owndamage true|false`) (DevCommands raises the max_health
  cap by reflection, dev only; dev test `devCommandSpawnsTankyMobs`), `/bastiondev automation [report]`, `/bastion vfx <preset>`.

## 8. Backlog / known gaps

- Base Extender (PLAN Fase 10): spec kept, **tidak direncanakan dulu**.
- Both user ideas of 2026-10-07 are done (Feed Hub multiblock, Repulsor). Addon support ("turret pack", like TaCZ gun packs, no Java; recorded 23:09, after release ~1.0.0, PLAN 7.6 end). User-planned next: Plasma Cell turret, Mk1-3
  tiers (bonuses undecided), 3x3x1 base.
- **Offered, waiting for the user's answer:** (a) UV overlap in 3 older models: **done 22:50** (see NOW 0); (b) a night version of
  `12_railgun_charge` (violet reads much stronger at night); (c) Repulsor Dome pylon caps are steel grey like other
  turrets' steel parts: make them dark gunmetal or glowing blue if the user meant those (orb fixed 20:01).
- Deferred minors (user decides): FeedHubMenu.stillValid ignores
  isRemoved, two misplaced registry comments (BastionBlocks:36, BastionBlockEntities:36); Repulsor riders cost FE without
  moving, wave/dome visuals use data.range() not stats.range() (Range Module), the wave is drawn from the muzzle
  (~0.75 block past the push reach), focusAngle unbounded (Laser too).
- Shader packs: paint the cyan into `_e` textures and tint only for damage/heat (glow shows white under Oculus).
- Fase 8: Jade/JEI/Ponder compat (JEI would list workstation recipes), particle budget (flamethrower ~130/s vs 120),
  dedicated server test with a second player. All sounds are placeholders.
- No FE generator in the mod (ask before adding one).

## 9. Environment gotchas

- **Run with `--offline`**: ForgeGradle downloads Mojang version metadata (`DownloadMCMeta`) every run without a read
  timeout; a stalled connection hangs Gradle at "Configure project" with ~0% CPU. Kill it (`jstack <pid>` shows
  `DownloadMCMeta`), rerun with `--offline`. Asset downloads can also time out ("Failed to get asset"): just retry.
- Load tests away from the spawn chunks measure chunk generation, not turrets.
- zsh does not word-split `$VAR` (list paths literally); zsh aborts a chain on a glob with no match.
- `rtk` hook rewrites `grep`: use `rtk proxy grep`. `-PperfMods` clashed with the configuration name: flags are `withX`.
- **Never** `open(p,"w").write(open(p).read()...)` in one expression (it once truncated a file).
- `runClient` fails with "Can't find a primary monitor" while the Mac screen is locked; keep `caffeinate -dimsu` on.
- Blockbench MCP renders stale images while its window is hidden. Contact sheets hide thin lines: check full-res crops.
- GameTest: own world `run/gametestworld`, entities wiped per run; structures sit a block up, so a template's floor is at
  test y 1 (stand things at y 2); `destroyBlock` never drops loot; arenas persist (closed boxes, one batch per test, disarm turrets);
  `helper.onEachTick` for tick-exact checks; `FakePlayerFactory` for player-driven tests.
- GameTest arenas are hollow stone boxes: blocks and **loose items** a test leaves inside stay for the next batch placed
  there (adding tests reshuffles which test lands where). Tests that count drops must discard items present at their
  start; tests that place workstations/sources should clear them. Spawn mobs at absolute coords when comparing with
  absolute footprints (`helper.relativePos` → `spawnWithNoFreeWill(Vec3)` landed elsewhere once).
- `runClient` with `-PwithCompatMods` loads Spore + TaCZ; Spore logs its own recipe/model errors (not ours).
- The dev source set (`src/dev`) is compiled by `runGameTestServer`/`runClient`, not by `./gradlew build`: run
  `./gradlew compileDevJava --offline` after editing dev classes.
- Memory: `~/.claude/projects/-Users-alifrizzaz-Documents-Minecraft-Modding-Turret/memory/bastion-phase0-defaults.md`.
