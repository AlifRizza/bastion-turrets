# Sound Sheet: Bastion Turrets

Semua SFX di mod (69 sound event, termasuk 8 milik Ion Rifle dan Plasma dan 4 milik Turret Control Hub). Untuk tiap SFX: **kata kunci** untuk mencari di situs SFX gratis, dan **prompt** untuk AI generator suara. Semua yang ada sekarang masih placeholder hasil sintesis (`tools/gen_sounds.py`).

## Mulai dari sini

1. Ganti **8 SFX P1** dulu. Itu yang terdengar terus setiap main (tabel di bawah).
2. Lewati baris **Lewati**: terdaftar tapi tidak pernah diputar di game.
3. Taruh file baru di `src/main/resources/assets/bastion/sounds/` + path yang tertulis (nama file sama persis), lalu jalankan game. Tidak perlu ubah kode selama jumlah varian sama.
4. `tools/gen_sounds.py` sekarang hanya menulis file yang belum ada, jadi SFX yang sudah diganti aman. `--force` menimpa semuanya dengan placeholder.

## Syarat file (wajib)

- **OGG Vorbis, mono.** Minecraft hanya memberi arah dan jarak (suara 3D) ke file mono; file stereo terdengar dari mana-mana.
- 44,1 atau 48 kHz. Tanpa jeda hening di awal (maksimal ~5 ms). Puncak sekitar -1 dBFS; volume antar-SFX dibuat seimbang.
- **Varian**: kalau tertulis 3 varian, buat 3 file berbeda (`fire_0.ogg`, `fire_1.ogg`, `fire_2.ogg`). Game memilih acak dan menggeser pitch ±8%.
- **Loop**: harus menyambung mulus (awal dan akhir cocok). Di ElevenLabs nyalakan opsi loop; kalau tidak ada, potong di zero-crossing dan crossfade.
- **Versi jauh (`_tail`)**: diputar untuk pemain di atas 32 blok. Isinya versi teredam + gema, bukan suara dekat yang dikecilkan.

## Sumber gratis

| Situs | Lisensi | Catatan |
|---|---|---|
| [Freesound](https://freesound.org) | campur: filter **Creative Commons 0** | CC0 tanpa kredit; CC-BY wajib kredit; hindari NonCommercial |
| [Kenney](https://kenney.nl/assets/category:Audio) | CC0 | paket Sci-fi Sounds, Impact Sounds |
| [OpenGameArt](https://opengameart.org) | campur: filter CC0 | cek lisensi per file |
| [Pixabay](https://pixabay.com/sound-effects/) | Pixabay Content License | gratis tanpa kredit, tapi melarang membagikan file apa adanya; file di dalam jar mod ikut terbagi, jadi baca lisensinya dulu |
| [Sonniss GDC Bundle](https://sonniss.com/gameaudiogdc) | royalty-free untuk game | boleh dipakai di game; baca syarat redistribusinya |

Paling aman: **CC0**. Catat sumber dan pembuat tiap file di README (PLAN 5.7).

## Generator AI

- Prompt ditulis dalam bahasa Inggris (hasilnya lebih baik). Atur **durasi** di tool sesuai kolom Durasi; buat 3-4 take, pilih yang terbaik.
- Tool contoh: ElevenLabs Sound Effects (ada pengaturan durasi dan loop), Stable Audio. **Cek lisensi hasilnya**: paket gratis sering melarang pemakaian komersial atau mewajibkan kredit.
- Tiap prompt berdiri sendiri, jadi bisa langsung disalin.

## Status SFX asli (2026-10-10)

Sumber dari user ada di `sfx/` (root repo). `tools/cut_sfx.py` memotong, memisah, membuat loop, menyamakan volume (RMS
placeholder +2 dB) dan menulis OGG mono. Jalankan ulang setelah mengganti file sumber: `python3 tools/cut_sfx.py`.
Cek potongan dengan mata: `python3 tools/cut_sfx.py --sheet "<file>" <detik> ...` menulis waveform + spektrogram (frekuensi log,
garis merah di detik yang diberikan) ke `build/sfx-sheets/` (ide dari opus-sound-layer, tanpa dependensi baru: ffmpeg).

| Event | Sumber | Diolah |
|---|---|---|
| `turret.deploy` | `turret_deploy.wav` | dipotong 0,45 dtk |
| `turret.idle_hum` | `turret_idle_hum.wav` | loop 2 dtk (crossfade 0,25 dtk) |
| `turret.repair` | `turret_repair.wav` | - |
| `turret.destroyed` | `turret_destroyed_small_turret.wav` | mulai dari ledakan (jeda 1,5 dtk di awal dibuang) |
| `turret.destroyed_large` (baru) | `turret_destroyed_large_turret.wav` | idem; dipakai Large Turret Base |
| `gun.fire` (3) | `gun_fire.wav` | varian pitch 1 / 0,97 / 1,03 |
| `gun.fire_tail` (2) | `rifle_gun_tail.wav` | - |
| `gun.lock` | `gun_lock.wav` | - |
| `machine_gun.spin_up` | `minigun spin up - spinning - spin down.mp3` | 0-1,3 dtk; diputar saat laras mulai berputar |
| `machine_gun.spin_loop` | idem | loop 2 dtk dari putaran stabil (2,0-4,2 dtk); masuk pelan di seperempat akhir spin-up, ~10 dB di bawah tembakan |
| `machine_gun.spin_down` | idem | 7,15-8,6 dtk; diputar saat laras mulai melambat |
| `machine_gun.fire` (3) | `machine_gun_fire.wav` | dari 3 titik di dalam raungan (0,715 / 0,735 / 0,755 dtk) sampai akhir decay (1,05 dtk); tiap tembakan turret satu potong, jadi menyambung jadi satu rentetan |
| `machine_gun.fire_tail` (2) | `machine_gun_fire_trail.wav` | 2-3 tembakan jauh per potong (0,365-0,442 / 0,442-0,528 dtk) |
| `shotgun.fire` (3), `shotgun.fire_tail` (2) | `shotgun_fire.wav`, `shotgun_fire_tail.wav` | jeda 0,3 dtk di awal dibuang |
| `shotgun.pump` | `shotgun_pump (... remove artifact ...).wav` | dua gerakan didekatkan (jeda 80 ms), derak di awal dibuang |
| `shotgun.shell` (2) | `shotgun _shell (...).wav` | tiap varian satu pantulan |
| `sniper.fire` (2) | `sniper_generated.wav` (Gemini Omni, 10-16, `-Pshowcase=video_sniper`; menggantikan `sniper_fire.wav`) | tembakan 2 (4,79 dtk) dan 3 (7,775 dtk), keduanya mulai dari hening, 2,1 dtk sampai gaungnya habis; 0,3 dtk pertama sekeras tembakan lama |
| `sniper.fire_tail` (jauh) | idem (menggantikan `sniper_fire_tail.wav`) | tembakan 2, diredam di atas 900 Hz |
| `sniper.charge` | `sniper_charge_generated.wav` (Gemini Omni, 10-16, take kedua dari samping) | charge ke-3, 1,5 dtk terakhir sebelum tembakan (6,278-7,778 dtk): desis saat laser muncul lalu dengung yang makin terang; +6 dB (game memutarnya di 0,8) |
| `plasma_repeater.fire` (3) | `plasma_gun.mp3` | 3 tembakan dari 3-4 dtk (aliran tembakan tiap 250 ms = laju Repeater) |
| `rocket.launch` (2) | `rocket_launcher_generated.wav` (Gemini Omni, 10-14) | tembakan 1 (0,555-0,95 dtk, satu-satunya yang mulai dari hening) + varian pitch 0,94 |
| `rocket.explode` (2) | idem | ledakan tembakan 1 (0,95-2,37 dtk) dan 4 (6,305-7,69 dtk), sampai peluncuran berikutnya, fade 0,4 dtk; juga dipakai ledakan Mortar |
| `missile.launch` (2) | `missile_launcher_generated.wav` (Gemini Omni, 10-14, mode single, satu-satu) | tembakan 1 (0,49 dtk) dan 2 (3,79 dtk), 0,46 dtk, fade ke loop terbang |
| `missile.explode` (2) | idem | ledakan 1 (1,89-3,75 dtk, habis sampai hening) dan 2 (5,17-6,95 dtk) |
| `rocket.fly` (roket + misil) | idem | 8 dtk dari potongan acak 0,25 dtk bagian terbang stabil ketiga tembakan (`texture()`), di-loop; loop 1,34 dtk sebelumnya terdengar berulang (user 10-14). Pitch acak +-5% per roket |
| `rocket.launch_tail` (roket + misil, jauh) | idem | launch + terbang tembakan 1 (0,49-1,85 dtk), diredam di atas 900 Hz |
| `rocket.reload` (baru) | `rocket_reload.wav` (SFX generator, 10-14) | file ter-loop: awal suara di 1,09 dtk + sisanya 0-0,40 dtk; diputar saat roket mulai masuk tabung |
| `missile.reload` | `missile_reload (delete the artifact ...).wav` | geser + clunk 0,50-0,72 dtk, desis panjang sesudahnya dibuang |
| `laser.charge` | `laser_rifle_generated.wav` (Gemini Omni, 10-15, `-Pshowcase=video_laser`) | charge ke-2, 2,5 dtk terakhir sebelum tembakan (5,44-7,94 dtk); +4 dB dibanding tembakan (Omni menaruhnya 12 dB di bawah) |
| `laser.fire` (2) | idem | tembakan 1 (3,025-4,95 dtk) dan 2 (7,937-10,0 dtk), sampai ring-out habis |
| `laser.fire_tail` (jauh) | idem | tembakan 1, diredam di atas 900 Hz |
| `flamethrower.ignite` (2, sebelumnya 1) | `flamethrower_generated.wav` (Gemini Omni, 10-15, `-Pshowcase=video_flame`) | awal semburan 1 (0,29 dtk) dan 2 (6,09 dtk), 0,6 dtk, sekeras raungan: di atas awal loop jadi lonjakan singkat. Omni tidak membuat "whoomph" yang jelas; juga dipakai api Mortar |
| `flamethrower.loop` | idem | 6 dtk dari potongan acak 0,25 dtk raungan stabil kedua semburan (1,2-3,6 / 6,4-8,65 dtk, `texture()`), di-loop |
| `flamethrower.burning` | idem | kobaran api setelah semburan 1 mati (4,2-5,1 dtk); juga dipakai api Mortar |
| `tesla.charge` | `tesla_coil_generated.wav` (Gemini Omni, 10-15, `-Pshowcase=video_tesla`, file dari ~/Downloads) | charge ke-4 (7,58-8,18 dtk), satu-satunya yang sepanjang charge game (0,6 dtk); +3 dB dibanding sambaran |
| `tesla.zap` (2) | idem | sambaran 2 (3,302 dtk) dan 4 (8,185 dtk), 1,3 dtk sampai desisnya habis |
| `tesla.crackle` (2) | idem | 0,3 dtk pertama charge 2 dan 3: percikan + dengung dari hening; juga percikan idle, charge Railgun, Workstation |
| `tesla.zap_tail` (jauh) | idem | sambaran 2, diredam di atas 900 Hz |
| `railgun.charge` | `railgun_generated.wav` (Gemini Omni, 10-16, `-Pshowcase=video_railgun`) | 3 dtk pertama charge 1 (0,312-3,312 dtk): serangan percikan lalu dengung yang makin terang ~10 dB; percikan busur listrik = `tesla.crackle` yang diputar game di atasnya (Omni tidak membuatnya) |
| `railgun.fire` (2) | idem | tembakan 1 (3,405 dtk) dan 2 (8,143 dtk), sampai desis listriknya habis (~1,85 dtk); 0,3 dtk pertama 1,5 dB di atas Sniper/Laser |
| `railgun.fire_tail` (jauh) | idem | tembakan 1, diredam di atas 900 Hz |
| `railgun.impact` (2) | `railgun_impact_generated.wav` (Gemini Omni, 10-16, `-Pshowcase=video_railgun_impact`: impact di lantai tanpa turret) | impact 3 (5,303 dtk) dan 4 (7,597 dtk), keduanya dari hening, 1,8 dtk; sekeras ledakan Missile |
| `plasma_cannon.charge` | `plasma_cannon_generated.wav` (Gemini Omni, 10-16, `-Pshowcase=video_plasma_cannon`, target kubus magma) | satu-satunya charge (0,325-1,33 dtk), 1 dtk seperti charge game |
| `plasma_cannon.fire` (2) | idem | tembakan 1 (1,33 dtk) dan 2 (4,62 dtk), ~1 dtk sampai habis; 0,3 dtk pertama antara Railgun dan Sniper |
| `plasma_cannon.hit` (2) | idem | ledakan 1 (2,36-4,56 dtk, habis) dan 2 (5,62-7,18 dtk) |
| `repulsor.charge` | `repulsor_generated.wav` (Gemini Omni, 10-16, `-Pshowcase=video_repulsor`) | Omni tidak membuat charge: ekor gelombang 1 diputar terbalik (1,81-2,41 dtk), tarikan napas 0,6 dtk yang naik ke dorongan; 0,1 dtk terakhir memudar di bawah awal dorongan |
| `repulsor.push` (2) | idem | gelombang 1 (0,895 dtk) dan 3 (6,105 dtk), whoosh yang naik ~0,5 dtk lalu habis ~1,5 dtk |
| `repulsor.push_tail` (jauh) | idem | gelombang 1, diredam di atas 900 Hz |
| `repulsor.dome_charge` | `repulsor_dome_generated.wav` (Gemini Omni, 10-16, `-Pshowcase=video_repulsor_dome`, take kedua: prompt angin) | Omni tidak membuat charge: ekor gust 1 diputar terbalik (1,75-2,35 dtk), 0,6 dtk, ujungnya memudar di bawah awal gust |
| `repulsor.dome_burst` (2) | idem | gust 1 (0,85 dtk) dan 2 (5,37 dtk), deru angin ~1 dtk lalu habis ~3 dtk; take pertama (`repulsor_dome_take1_generated.wav`) terdengar seperti tembakan |
| `repulsor.dome_burst_tail` (jauh) | idem | gust 1, diredam di atas 900 Hz |
| `ion.fire` (2) | `ion_generated.wav` (Gemini Omni, 10-16, `-Pshowcase=video_ion`) | tembakan 2 (2,227 dtk) dan 5 (7,672 dtk), paling beda spektrumnya, ~0,9-1,1 dtk sampai habis; juga suara jauhnya |
| `mortar.fire` (2) | `mortar_generated.wav` (Gemini Omni, 10-16, `-Pshowcase=video_mortar`) | peluncuran 1 (0,487-1,85 dtk) + varian pitch 0,94; peluncuran 2 tertumpuk crackle api ledakan 1 |
| `mortar.fire_tail` (jauh) | idem | peluncuran 1, diredam di atas 900 Hz |
| `turret.servo` | `turret_servo.wav` | loop dibuat, tapi event ini belum diputar di game |

**Dihapus (user 22:31, "nanti")**: suara proyektil kena, `impact.kinetic`, `plasma.hit_small`, `ion.hit` (event, file,
placeholder di `gen_sounds.py`). Ledakan besar (`railgun.impact`, `plasma_cannon.hit`, roket/misil) tetap.
Suara sakit mob karena turret: 1x per `turretHurtSoundInterval` (10 tick) per mob, volume `turretHurtSoundVolume` (0,5).

Machine Gun (user 10-11): dengung laras (spin_up, spin_loop, spin_down) dari satu rekaman, dipotong dengan satu gain
yang sama supaya sambungannya rata; rentetan tembakan di atasnya. `machine_gun.fire_loop` dihapus; rekaman Pixabay
minigun tidak dipakai lagi. Campuran uji sekali buat (10-11, tidak di-commit): `build/sfx-sheets/mg_preview.wav`,
satu siklus spin-up, tembak 3 dtk, spin-down sesuai jadwal tembak game.

**Kredit / lisensi**: (tidak dipakai lagi sejak 10-11) minigun = Pixabay, user *sovetsky_rastov72*, sound effect 244784 (Pixabay Content License: boleh di
dalam game/mod, tidak boleh dibagikan sebagai file lepas). File `.wav` lain dan `plasma_gun.mp3`: catat di sini tool dan
lisensinya (generator AI: cek apakah paketnya mengizinkan komersial/kredit). Rocket Launcher (10-14): audio dari Google Gemini
Omni (video bisu `-Pshowcase=video` + prompt), cek syarat penggunaan konten AI Google untuk rilis. Missile Launcher
(10-14): idem, `-Pshowcase=video_missile`. Laser Rifle (10-15): idem, `-Pshowcase=video_laser`. Flamethrower (10-15): idem, `-Pshowcase=video_flame`. Tesla Coil (10-15): idem, `-Pshowcase=video_tesla`. Sniper (10-16): idem, `-Pshowcase=video_sniper` (dua take: tembakan dari belakang turret, charge dari samping). Railgun (10-16): idem, `-Pshowcase=video_railgun` dan `video_railgun_impact`. Plasma Cannon (10-16): idem, `-Pshowcase=video_plasma_cannon`. Repulsor (10-16): idem, `-Pshowcase=video_repulsor` dan `video_repulsor_dome`. Ion Rifle (10-16): idem, `-Pshowcase=video_ion`. Mortar (10-16): idem, `-Pshowcase=video_mortar`.

## Prompt MiniMax H3 per turret (untuk SFX berikutnya)

Untuk node **MiniMax H3 Audio Prompt** (`t22m003/ComfyUI-MiniMaxH3Audio`): tiga kolom, isi persis seperti blok di bawah.
Aturan dari panduan resmi MiniMax H3 dan README node itu (riset 2026-10-11):
- **Tanpa kalimat larangan** ("no music", "no wind"): H3 tidak punya negative prompt (BasicGuider tanpa CFG), jadi kata
  yang dilarang justru bisa muncul. Semua ditulis positif: ruang uji tertutup yang sunyi, bukan "tanpa ambience".
- `integrated_multimodal_description`: diawali `[Shot 1]`, gaya + komposisi, lalu kejadian berurutan dengan kata sambung
  (then, as, a moment later); timestamp hanya untuk pergantian shot, di sini satu shot saja. Suara ditempel pada aksinya.
- `overall_soundscape`: 1-4 kalimat, satu paragraf, suara fisik dan ambience (bukan musik). Kata kunci gaya game FPS ada di sini.
- `non_diegetic_music`: `N/A` (cara resmi untuk tanpa musik).
- `seconds`: 5-15 (rentang latihan model); angka per turret di bawah.
Hasilnya kirim apa adanya ke `sfx/` (2-3 take per prompt kalau bisa); aku yang memotong, memisah, membuat loop, merapikan,
menggabungkan, membuat varian dan versi jauh.

**Machine Gun** (berputar, tembak, wind down) · seconds `10.0`

integrated_multimodal_description:
```text
[Shot 1] Stylized AAA first-person shooter game cinematic, sharp detail, dark sci-fi lighting. In a dark, quiet indoor concrete test chamber, a futuristic gunmetal rotary minigun turret with glowing cyan lines is framed in a static close-up. Its six barrels start to rotate and quickly spin up to full speed, then it fires one long continuous burst for several seconds with a bright flickering muzzle flash and spent casings pouring onto the floor. Then the firing stops, and the barrels keep rotating and slowly wind down until they come to a complete stop.
```
overall_soundscape:
```text
Punchy, crisp FPS game sound design with heavy low end and clean detail: an electric motor whine rises as the barrels spin up, then bursts into a dense, ripping minigun roar that holds steady for several seconds while brass casings clink on concrete. The roar stops at once and the spinning barrels whir down in a long falling whine to silence. The chamber around it is dry and quiet, so every mechanical detail sounds close, punchy and clear.
```
non_diegetic_music:
```text
N/A
```

**Sniper: charge** · seconds `6.0`

integrated_multimodal_description:
```text
[Shot 1] Stylized AAA first-person shooter game cinematic, sharp detail, dark sci-fi lighting. In a dark, quiet indoor concrete test chamber, a futuristic gunmetal sniper turret with a long glowing cyan barrel is framed in a static close-up. The barrel lights up brighter and brighter as it charges with a rising electric whine for about a second and a half, then it fires one shot with a bright muzzle flash and a heavy recoil.
```
overall_soundscape:
```text
Punchy, crisp FPS game sound design with heavy low end and clean detail: a thin electric whine climbs steadily in pitch, then a single huge, sharp supersonic rifle crack hits, followed by a long rolling echo that decays slowly. The chamber around it is dry and quiet, so every mechanical detail sounds close, punchy and clear.
```
non_diegetic_music:
```text
N/A
```

**Rocket Launcher** · seconds `8.0`

integrated_multimodal_description:
```text
[Shot 1] Stylized AAA first-person shooter game cinematic, sharp detail, dark sci-fi lighting. In a dark, quiet indoor concrete test chamber, a futuristic gunmetal rocket launcher turret with glowing cyan lines is framed in a static medium shot. It fires a rocket from its tube with a bright flash and a puff of smoke, the rocket streaks away down the long chamber trailing a smoke ribbon, and a moment later it slams into the far wall and explodes in a fireball.
```
overall_soundscape:
```text
Punchy, crisp FPS game sound design with heavy low end and clean detail: a punchy launch thump and a sharp pressurized whoosh, then the rocket motor's harsh roar recedes into the distance. A deep, heavy explosion follows with debris rattling down and a long rumbling tail. The chamber around it is dry and quiet, so every mechanical detail sounds close, punchy and clear.
```
non_diegetic_music:
```text
N/A
```

**Missile Launcher** · seconds `8.0`

integrated_multimodal_description:
```text
[Shot 1] Stylized AAA first-person shooter game cinematic, sharp detail, dark sci-fi lighting. In a dark, quiet indoor concrete test chamber, a futuristic gunmetal missile pod turret with glowing cyan lines is framed in a static medium shot. It fires two small guided missiles one after another from its tubes, each launch with a flash, the missiles streak away and burst against the far wall a moment later, then the pod reloads with a heavy mechanical motion.
```
overall_soundscape:
```text
Punchy, crisp FPS game sound design with heavy low end and clean detail: two sharp hissing missile launches in quick succession, each missile screaming away, then two medium explosions a moment later. The pod finishes with a heavy mechanical clunk and a metallic latch. The chamber around it is dry and quiet, so every mechanical detail sounds close, punchy and clear.
```
non_diegetic_music:
```text
N/A
```

**Tesla Coil** · seconds `8.0`

integrated_multimodal_description:
```text
[Shot 1] Stylized AAA first-person shooter game cinematic, sharp detail, dark sci-fi lighting. In a dark, quiet indoor concrete test chamber, a large futuristic gunmetal tesla coil turret with glowing cyan coils is framed in a static medium shot. It charges up as arcs crawl over its coils, discharges a bright bolt of lightning at a target, crackles with leftover sparks, then charges and discharges again.
```
overall_soundscape:
```text
Punchy, crisp FPS game sound design with heavy low end and clean detail: a rising electric hum with buzzing arcs builds into a loud, sharp lightning crack, followed by fizzling sparks; then the hum rises again into a second crack. The chamber around it is dry and quiet, so every mechanical detail sounds close, punchy and clear.
```
non_diegetic_music:
```text
N/A
```

**Flamethrower** · seconds `10.0`

integrated_multimodal_description:
```text
[Shot 1] Stylized AAA first-person shooter game cinematic, sharp detail, dark sci-fi lighting. In a dark, quiet indoor concrete test chamber, a futuristic gunmetal flamethrower turret with glowing cyan lines is framed in a static medium shot. Its pilot light clicks and the nozzle ignites with a burst of flame, it sprays a steady roaring jet of fire across the chamber for several seconds, then shuts off, while the burning target in front of it keeps crackling.
```
overall_soundscape:
```text
Punchy, crisp FPS game sound design with heavy low end and clean detail: an igniter click and a deep whoomp of gas catching fire open into a steady, powerful roaring flame jet that holds for several seconds. The jet cuts off with a short hiss, and the burning target crackles and pops as it smoulders. The chamber around it is dry and quiet, so every mechanical detail sounds close, punchy and clear.
```
non_diegetic_music:
```text
N/A
```

**Laser Rifle** · seconds `6.0`

integrated_multimodal_description:
```text
[Shot 1] Stylized AAA first-person shooter game cinematic, sharp detail, dark sci-fi lighting. In a dark, quiet indoor concrete test chamber, a futuristic gunmetal laser rifle turret with glowing cyan lines is framed in a static close-up. Light gathers at its muzzle as it charges for about two seconds, then it fires one powerful bright beam across the chamber that fades out.
```
overall_soundscape:
```text
Punchy, crisp FPS game sound design with heavy low end and clean detail: a bright electronic whine rises for two seconds into one powerful, sharp beam blast with a deep bass punch, followed by a sizzling, crackling decay. The chamber around it is dry and quiet, so every mechanical detail sounds close, punchy and clear.
```
non_diegetic_music:
```text
N/A
```

**Railgun** · seconds `8.0`

integrated_multimodal_description:
```text
[Shot 1] Stylized AAA first-person shooter game cinematic, sharp detail, dark sci-fi lighting. In a dark, quiet indoor concrete test chamber, a huge futuristic gunmetal railgun turret with two long rails and glowing cyan lines is framed in a static medium shot. Its rails charge with crawling electric arcs for about three seconds, then it fires with a blinding flash and a shockwave, and the slug slams into a thick metal wall at the far end of the chamber.
```
overall_soundscape:
```text
Punchy, crisp FPS game sound design with heavy low end and clean detail: a deep electromagnetic hum rises with crackling arcs, then a massive supersonic crack like a thunderclap. A heavy metallic impact boom follows from the far end, with a low rumbling tail. The chamber around it is dry and quiet, so every mechanical detail sounds close, punchy and clear.
```
non_diegetic_music:
```text
N/A
```

**Mortar** · seconds `8.0`

integrated_multimodal_description:
```text
[Shot 1] Stylized AAA first-person shooter game cinematic, sharp detail, dark sci-fi lighting. In a dark, quiet indoor concrete test chamber, a heavy futuristic gunmetal mortar turret with glowing cyan lines is framed in a static medium shot. It fires a shell high into the air with a flash and a puff of smoke, and a few seconds later the shell lands at the far end of the long chamber and explodes.
```
overall_soundscape:
```text
Punchy, crisp FPS game sound design with heavy low end and clean detail: a deep, hollow tube thump launches the shell, a descending whistle grows louder as it falls, then a heavy explosion with scattering debris. The chamber around it is dry and quiet, so every mechanical detail sounds close, punchy and clear.
```
non_diegetic_music:
```text
N/A
```

**Repulsor + Repulsor Dome** · seconds `8.0`

integrated_multimodal_description:
```text
[Shot 1] Stylized AAA first-person shooter game cinematic, sharp detail, dark sci-fi lighting. In a dark, quiet indoor concrete test chamber, a futuristic gunmetal force field emitter turret with glowing cyan lines is framed in a static medium shot, with a much larger dome emitter beside it. The small emitter charges with a glow and releases a pulse that pushes the air outward in a visible ripple; then the large dome emitter charges deeper and bursts outward in a huge spherical wave.
```
overall_soundscape:
```text
Punchy, crisp FPS game sound design with heavy low end and clean detail: a short rising energy hum releases a deep whoomph of air pressure; then a deeper, longer charge builds into a powerful low boom with a rush of displaced air. The chamber around it is dry and quiet, so every mechanical detail sounds close, punchy and clear.
```
non_diegetic_music:
```text
N/A
```

**Ion Rifle** · seconds `6.0`

integrated_multimodal_description:
```text
[Shot 1] Stylized AAA first-person shooter game cinematic, sharp detail, dark sci-fi lighting. In a dark, quiet indoor concrete test chamber, a futuristic gunmetal ion rifle turret with glowing blue lines is framed in a static medium shot. It fires an electric blue bolt that lands on the floor ahead and bursts into crackling arcs, and a moment later it fires a second bolt.
```
overall_soundscape:
```text
Punchy, crisp FPS game sound design with heavy low end and clean detail: a bright electric zap with a blue energy whoosh, then crackling arcs that fizzle out where the bolt lands; the zap and the crackle repeat once. The chamber around it is dry and quiet, so every mechanical detail sounds close, punchy and clear.
```
non_diegetic_music:
```text
N/A
```

**Plasma Repeater: vent** · seconds `8.0`

integrated_multimodal_description:
```text
[Shot 1] Stylized AAA first-person shooter game cinematic, sharp detail, dark sci-fi lighting. In a dark, quiet indoor concrete test chamber, a futuristic gunmetal plasma repeater turret with glowing green lines is framed in a static medium shot. It fires a long stream of green plasma balls, four per second, as its barrel glows hotter, until it overheats and vents hot gas from its sides in a long burst of steam.
```
overall_soundscape:
```text
Punchy, crisp FPS game sound design with heavy low end and clean detail: rapid punchy plasma shots, four per second, each a short wet energy thump, then a long pressurized steam hiss as the turret vents. The chamber around it is dry and quiet, so every mechanical detail sounds close, punchy and clear.
```
non_diegetic_music:
```text
N/A
```

**Plasma Cannon** · seconds `8.0`

integrated_multimodal_description:
```text
[Shot 1] Stylized AAA first-person shooter game cinematic, sharp detail, dark sci-fi lighting. In a dark, quiet indoor concrete test chamber, a large futuristic gunmetal plasma cannon turret with glowing green lines is framed in a static medium shot. A green glow builds inside its barrel as it charges, it fires one huge slow plasma ball, and a moment later the ball explodes at the far end of the chamber in a big green blast.
```
overall_soundscape:
```text
Punchy, crisp FPS game sound design with heavy low end and clean detail: a deep rising energy whine builds into one heavy, bassy plasma blast, then a big sizzling energy explosion with a long low tail. The chamber around it is dry and quiet, so every mechanical detail sounds close, punchy and clear.
```
non_diegetic_music:
```text
N/A
```

**Turret upgrade** · seconds `4.0`

integrated_multimodal_description:
```text
[Shot 1] Stylized AAA first-person shooter game cinematic, sharp detail, dark sci-fi lighting. In a dark, quiet indoor concrete test chamber, a futuristic gunmetal sci-fi turret with glowing cyan lines is framed in a static close-up while it is being upgraded: its armour plates slide, rotate and lock into a new shape, and its cyan lines flash brighter.
```
overall_soundscape:
```text
Punchy, crisp FPS game sound design with heavy low end and clean detail: mechanical parts whir, slide and lock into place with solid clunks, ending in a bright, clean confirmation chime. The chamber around it is dry and quiet, so every mechanical detail sounds close, punchy and clear.
```
non_diegetic_music:
```text
N/A
```

**Workstation** · seconds `12.0`

integrated_multimodal_description:
```text
[Shot 1] Stylized AAA first-person shooter game cinematic, sharp detail, dark sci-fi lighting. In a dark, quiet indoor concrete test chamber, a futuristic gunmetal sci-fi workshop machine with glowing cyan lines is framed in a static medium shot. It runs with humming motors, a robotic arm welds a metal part in a shower of sparks, a hydraulic press stamps the part, the machine keeps humming for a moment, then a status light turns green.
```
overall_soundscape:
```text
Punchy, crisp FPS game sound design with heavy low end and clean detail: steady humming motors run throughout, broken by a buzzing electric welding burst, then a heavy hydraulic press stamp with a short hiss, and finally a pleasant two-tone completion chime. The chamber around it is dry and quiet, so every mechanical detail sounds close, punchy and clear.
```
non_diegetic_music:
```text
N/A
```

**Turret Control Hub** · seconds `10.0`

integrated_multimodal_description:
```text
[Shot 1] Stylized AAA first-person shooter game cinematic, sharp detail, dark sci-fi lighting. In a dark, quiet indoor concrete test chamber, a futuristic gunmetal control console with a glowing cyan screen and a satellite dish on top is framed in a static medium shot. The console powers on and its screen lights up, the dish starts turning slowly, a hand presses a few buttons on the console, then the console powers down and the screen goes dark.
```
overall_soundscape:
```text
Punchy, crisp FPS game sound design with heavy low end and clean detail: a rising electronic power-on tone with a soft beep, then a soft steady servo hum with a faint radio hiss as the dish turns, a few soft button clicks, and a falling power-down tone. The chamber around it is dry and quiet, so every mechanical detail sounds close, punchy and clear.
```
non_diegetic_music:
```text
N/A
```

## Ringkasan

P1 = terdengar terus, ganti duluan · P2 = biasa · P3 = jarang atau versi jauh · Lewati = tidak dipakai

| # | SFX | Turret | Prioritas | Durasi | Varian | Loop |
|---|---|---|---|---|---|---|
| 2 | [`turret.deploy`](#2) | Turret base | P1 | 0,9 dtk | 1 |  |
| 3 | [`turret.idle_hum`](#3) | Turret base | P1 | 2 dtk | 1 | ya |
| 5 | [`impact.kinetic`](#5) | Turret base | P1 | 0,35 dtk | 3 |  |
| 6 | [`gun.fire`](#6) | Gun Turret | P1 | 0,7 dtk | 3 |  |
| 9 | [`machine_gun.fire`](#9) | Machine Gun | P1 | 0,25 dtk | 3 |  |
| 13 | [`machine_gun.spin_loop`](#13) | Machine Gun | P1 | 0,5 dtk | 1 | ya |
| 15 | [`shotgun.fire`](#15) | Shotgun | P1 | 1,3 dtk | 3 |  |
| 42 | [`workstation.loop`](#42) | Workstation | P1 | 2 dtk | 1 | ya |
| 8 | [`gun.lock`](#8) | Gun Turret | P2 | 0,3 dtk | 1 |  |
| 14 | [`machine_gun.overheat`](#14) | Machine Gun | P2 | 1,8 dtk | 1 |  |
| 17 | [`shotgun.pump`](#17) | Shotgun | P2 | 0,95 dtk | 1 |  |
| 18 | [`shotgun.shell`](#18) | Shotgun | P2 | 0,5 dtk | 2 |  |
| 22 | [`sniper.charge`](#22) | Sniper | P2 | 1,5 dtk | 1 |  |
| 23 | [`sniper.fire`](#23) | Sniper | P2 | 2,2 dtk | 2 |  |
| 25 | [`rocket.launch`](#25) | Rocket Launcher | P2 | 1,4 dtk | 2 |  |
| 27 | [`rocket.fly`](#27) | Rocket Launcher | P2 | 1 dtk | 1 | ya |
| 28 | [`rocket.explode`](#28) | Rocket Launcher | P2 | 2,8 dtk | 2 |  |
| 29 | [`missile.launch`](#29) | Missile Launcher | P2 | 0,9 dtk | 2 |  |
| 30 | [`missile.reload`](#30) | Missile Launcher | P2 | 0,5 dtk | 1 |  |
| 31 | [`missile.explode`](#31) | Missile Launcher | P2 | 1,6 dtk | 2 |  |
| 32 | [`tesla.charge`](#32) | Tesla Coil | P2 | 0,7 dtk | 1 |  |
| 33 | [`tesla.zap`](#33) | Tesla Coil | P2 | 0,9 dtk | 2 |  |
| 35 | [`tesla.crackle`](#35) | Tesla Coil | P2 | 0,35 dtk | 2 |  |
| 36 | [`flamethrower.ignite`](#36) | Flamethrower | P2 | 0,8 dtk | 1 |  |
| 37 | [`flamethrower.loop`](#37) | Flamethrower | P2 | 1 dtk | 1 | ya |
| 38 | [`flamethrower.burning`](#38) | Flamethrower | P2 | 0,9 dtk | 1 |  |
| 39 | [`laser.charge`](#39) | Laser Rifle | P2 | 2,5 dtk | 1 |  |
| 40 | [`laser.fire`](#40) | Laser Rifle | P2 | 1,2 dtk | 2 |  |
| 43 | [`workstation.weld`](#43) | Workstation | P2 | 0,5 dtk | 1 |  |
| 44 | [`workstation.press`](#44) | Workstation | P2 | 0,7 dtk | 1 |  |
| 45 | [`workstation.done`](#45) | Workstation | P2 | 0,9 dtk | 1 |  |
| 46 | [`railgun.charge`](#46) | Railgun | P2 | 3 dtk | 1 |  |
| 47 | [`railgun.fire`](#47) | Railgun | P2 | 1,8 dtk | 2 |  |
| 49 | [`railgun.impact`](#49) | Railgun | P2 | 1,5 dtk | 2 |  |
| 50 | [`mortar.fire`](#50) | Mortar | P2 | 1,4 dtk | 2 |  |
| 52 | ~~`mortar.whistle`~~ dihapus (user 10-17: peluru Mortar jatuh tanpa suara) | Mortar | - | - | - |  |
| 53 | [`repulsor.charge`](#53) | Repulsor | P2 | 0,6 dtk | 1 |  |
| 54 | [`repulsor.push`](#54) | Repulsor | P2 | 0,9 dtk | 2 |  |
| 56 | [`repulsor.dome_charge`](#56) | Repulsor Dome | P2 | 0,6 dtk | 1 |  |
| 57 | [`repulsor.dome_burst`](#57) | Repulsor Dome | P2 | 2 dtk | 2 |  |
| 59 | [`ion.fire`](#59) | Ion Rifle | P2 | 0,6 dtk | 2 |  |
| 60 | [`ion.hit`](#60) | Ion Rifle | P2 | 0,6 dtk | 2 |  |
| 61 | [`plasma_repeater.fire`](#61) | Plasma Repeater | P2 | 0,3 dtk | 3 |  |
| 62 | [`plasma.hit_small`](#62) | Plasma Repeater | P2 | 0,4 dtk | 2 |  |
| 63 | [`plasma.vent`](#63) | Plasma Repeater | P2 | 1,5 dtk | 1 |  |
| 64 | [`plasma_cannon.charge`](#64) | Plasma Cannon | P2 | 1 dtk | 1 |  |
| 65 | [`plasma_cannon.fire`](#65) | Plasma Cannon | P2 | 1,4 dtk | 2 |  |
| 66 | [`plasma_cannon.hit`](#66) | Plasma Cannon | P2 | 1,6 dtk | 2 |  |
| 67 | [`control_hub.online`](#67) | Control Hub | P2 | 1,2 dtk | 1 |  |
| 68 | [`control_hub.offline`](#68) | Control Hub | P2 | 1 dtk | 1 |  |
| 69 | [`control_hub.dish_loop`](#69) | Control Hub | P2 | 3 dtk | 1 | ya |
| 7 | [`gun.fire_tail`](#7) | Gun Turret | P3 | 1,2 dtk | 2 |  |
| 10 | [`machine_gun.fire_tail`](#10) | Machine Gun | P3 | 1,2 dtk | 2 |  |
| 16 | [`shotgun.fire_tail`](#16) | Shotgun | P3 | 1,2 dtk | 2 |  |
| 19 | [`turret.destroyed`](#19) | Turret base | P3 | 2,6 dtk | 1 |  |
| 20 | [`turret.tier_up`](#20) | Turret base | P3 | 1,2 dtk | 1 |  |
| 21 | [`turret.repair`](#21) | Turret base | P3 | 0,8 dtk | 1 |  |
| 24 | [`sniper.fire_tail`](#24) | Sniper | P3 | 2,4 dtk | 1 |  |
| 26 | [`rocket.launch_tail`](#26) | Rocket Launcher | P3 | 1,8 dtk | 1 |  |
| 34 | [`tesla.zap_tail`](#34) | Tesla Coil | P3 | 1,4 dtk | 1 |  |
| 41 | [`laser.fire_tail`](#41) | Laser Rifle | P3 | 1,6 dtk | 1 |  |
| 48 | [`railgun.fire_tail`](#48) | Railgun | P3 | 2,2 dtk | 1 |  |
| 51 | [`mortar.fire_tail`](#51) | Mortar | P3 | 1,8 dtk | 1 |  |
| 55 | [`repulsor.push_tail`](#55) | Repulsor | P3 | 1,2 dtk | 1 |  |
| 58 | [`repulsor.dome_burst_tail`](#58) | Repulsor Dome | P3 | 2,4 dtk | 1 |  |
| 70 | [`control_hub.ui_select`](#70) | Control Hub | P3 | 0,12 dtk | 1 |  |
| 1 | [`debug.ping`](#1) | Dev | Lewati | 0,6 dtk | 1 |  |
| 4 | [`turret.servo`](#4) | Turret base | Lewati | 1 dtk | 1 | ya |
| 11 | [`machine_gun.spin_up`](#11) | Machine Gun | Lewati | 1 dtk | 1 |  |
| 12 | [`machine_gun.spin_down`](#12) | Machine Gun | Lewati | 1,5 dtk | 1 |  |

---

## Dev

<a id="1"></a>
### 1. `debug.ping`

**Lewati** · 0,6 dtk · 1 varian · `debug/ping.ogg` · terdengar sampai 16 blok  
Bunyi tes pipeline Fase 0. *Hanya untuk development, tidak pernah diputar di game. Lewati.*

**Cari:** `ui ping` · `notification ping` · `short beep`

**Prompt AI:**

```text
A short descending synth ping with a soft click. 0.6 seconds. Dry, isolated UI sound, mono, no music, no voices.
```

---

## Turret base

<a id="2"></a>
### 2. `turret.deploy`

**P1** · 0,9 dtk · 1 varian · `turret/deploy.ogg` · terdengar sampai 16 blok  
Weapon module dipasang: laras keluar dari housing lalu terkunci.

**Cari:** `sci-fi turret deploy` · `servo extend lock` · `mechanical whir clunk`

**Prompt AI:**

```text
A compact futuristic sci-fi defense turret deploying: a short rising electric servo whir as the barrel slides out of its armored gunmetal housing, ending in a crisp heavy metallic lock clack. Precise, high-tech machinery. 0.9 seconds. Close-mic, dry, isolated sound effect, mono, no music, no voices.
```

<a id="3"></a>
### 3. `turret.idle_hum`

**P1** · 2 dtk · 1 varian · `turret/idle_hum.ogg` · terdengar sampai 16 blok  
Dengung pelan turret yang aktif; volume mengikuti status turret (loop).

**Cari:** `sci-fi machine hum loop` · `electric hum low loop` · `spaceship console idle`

**Prompt AI:**

```text
Quiet low electric hum of an idle, powered sci-fi turret: a soft 80 Hz transformer drone with gentle harmonics, a very slow subtle pulse and a faint electronic shimmer on top. Calm and unobtrusive, it sits under gameplay. 2 seconds. Perfectly seamless loop with no start or end transients. Dry, isolated, mono, no music, no voices.
```

<a id="4"></a>
### 4. `turret.servo`

**Lewati** · 1 dtk · 1 varian · `turret/servo_loop.ogg` · terdengar sampai 16 blok  
Motor servo saat turret berputar (loop). *Terdaftar tapi tidak pernah diputar di game. Lewati.*

**Cari:** `servo motor loop` · `robot servo whir` · `small motor rotate loop`

**Prompt AI:**

```text
Small precise servo motor rotating a sci-fi turret head: a smooth steady electric motor whine with a light gear whir. 1 second. Perfectly seamless loop with no start or end transients. Dry, isolated, mono, no music, no voices.
```

<a id="5"></a>
### 5. `impact.kinetic`

**P1** · 0,35 dtk · 3 varian · `impact/kinetic_0..2.ogg` · terdengar sampai 16 blok  
Peluru Gun, Machine Gun dan Shotgun kena mob atau block.

**Cari:** `bullet impact` · `bullet hit thud` · `projectile impact short`

**Prompt AI:**

```text
Short bullet impact: a crisp punchy thud of a high-velocity round hitting a body or stone surface, with a tiny bright metallic spark tick. Tight and very short so it can repeat many times per second without fatigue. No ricochet whine. 0.35 seconds. Close-mic, dry, isolated sound effect, mono, no music, no voices.
```

---

## Gun Turret

<a id="6"></a>
### 6. `gun.fire`

**P1** · 0,7 dtk · 3 varian · `gun/fire_0..2.ogg` · terdengar sampai 16 blok  
Tembakan Gun Turret (presisi, cyan).

**Cari:** `sci-fi rifle shot` · `futuristic gun single shot` · `energy rifle fire`

**Prompt AI:**

```text
Single shot from a precise futuristic sci-fi rifle turret: a sharp tight gunshot crack layered with a clean electric snap and a brief high-tech zing, solid low-mid punch, quick decay. Accurate and professional, not cartoonish. 0.7 seconds. Close-mic, dry, isolated sound effect, mono, no music, no voices.
```

<a id="7"></a>
### 7. `gun.fire_tail`

**P3** · 1,2 dtk · 2 varian · `gun/fire_tail_0..1.ogg` · terdengar sampai 16 blok  
Versi jauh tembakan Gun (di atas 32 blok).

**Cari:** `distant gunshot` · `gunshot echo far` · `rifle shot distant`

**Prompt AI:**

```text
A single sci-fi rifle shot heard from far away: muffled and low-passed, no sharp close transient, followed by a soft rolling outdoor echo. 1.2 seconds. Isolated sound effect, mono, natural reverb tail, no music, no voices.
```

<a id="8"></a>
### 8. `gun.lock`

**P2** · 0,3 dtk · 1 varian · `gun/lock.ogg` · terdengar sampai 16 blok  
Precision Lock tercapai (bunyi hologram reticle).

**Cari:** `target lock beep` · `sci-fi hud lock on` · `electronic confirm beep`

**Prompt AI:**

```text
Sci-fi targeting system lock-on: a quick crisp two-tone electronic beep rising in pitch, a clean digital HUD confirmation. 0.3 seconds. Dry, isolated UI sound, mono, no music, no voices.
```

---

## Machine Gun

<a id="9"></a>
### 9. `machine_gun.fire`

**P1** · 0,25 dtk · 3 varian · `machine_gun/fire_0..2.ogg` · terdengar sampai 16 blok  
Satu tembakan Machine Gun (sampai 12 per detik).

**Cari:** `minigun single shot` · `rotary gun shot` · `machine gun one shot`

**Prompt AI:**

```text
One single shot of a futuristic rotary machine gun turret: a short punchy dry bang with a tight mechanical clack and a very fast decay, so it blends into a stream at 12 shots per second. No tail. 0.25 seconds. Close-mic, dry, isolated sound effect, mono, no music, no voices.
```

<a id="10"></a>
### 10. `machine_gun.fire_tail`

**P3** · 1,2 dtk · 2 varian · `gun/fire_tail_0..1.ogg` · terdengar sampai 16 blok  
Versi jauh tembakan Machine Gun. *Sudah diganti (10-11): `machine_gun_fire_trail.wav`.*

**Cari:** `distant machine gun` · `gunfire far echo` · `distant automatic fire`

**Prompt AI:**

```text
One single machine gun shot heard from far away: muffled and low-passed, no sharp close transient, followed by a soft rolling outdoor echo. 1.2 seconds. Isolated sound effect, mono, natural reverb tail, no music, no voices.
```

<a id="11"></a>
### 11. `machine_gun.spin_up`

**Lewati** · 1 dtk · 1 varian · `machine_gun/spin_up.ogg` · terdengar sampai 16 blok  
Laras berputar makin cepat. *Tidak pernah diputar (spin_loop dengan pitch naik-turun sudah menggantikannya). Lewati.*

**Cari:** `minigun spin up` · `motor spin up whine` · `rotary barrel wind up`

**Prompt AI:**

```text
Sci-fi rotary machine gun barrels spinning up from rest: an electric motor whine rising in pitch with a mechanical whirr speeding up, no gunshots. 1 second. Close-mic, dry, isolated sound effect, mono, no music, no voices.
```

<a id="12"></a>
### 12. `machine_gun.spin_down`

**Lewati** · 1,5 dtk · 1 varian · `machine_gun/spin_down.ogg` · terdengar sampai 16 blok  
Laras berputar melambat. *Tidak pernah diputar. Lewati.*

**Cari:** `minigun spin down` · `motor wind down` · `rotary barrel slow down`

**Prompt AI:**

```text
Sci-fi rotary machine gun barrels spinning down: a motor whine falling in pitch with a mechanical whirr slowing to a stop and a soft final click. 1.5 seconds. Close-mic, dry, isolated sound effect, mono, no music, no voices.
```

<a id="13"></a>
### 13. `machine_gun.spin_loop`

**P1** · 0,5 dtk · 1 varian · `machine_gun/spin_loop.ogg` · terdengar sampai 16 blok  
Laras berputar penuh; pitch dan volume mengikuti kecepatan putar (loop).

**Cari:** `minigun spin loop` · `rotary barrel whir loop` · `electric motor loop`

**Prompt AI:**

```text
Sci-fi rotary machine gun barrels spinning at full speed: a steady electric motor whine with a smooth mechanical whirring rattle of rotating barrels, no gunshots. Keep it tonally steady, the game shifts its pitch with spin speed. 0.5 seconds. Perfectly seamless loop with no start or end transients. Dry, isolated, mono, no music, no voices.
```

<a id="14"></a>
### 14. `machine_gun.overheat`

**P2** · 1,8 dtk · 1 varian · `machine_gun/overheat.ogg` · terdengar sampai 16 blok  
Machine Gun overheat: vent terbuka, berhenti 3 dtk.

**Cari:** `steam vent hiss` · `overheat warning sci-fi` · `hot metal cooling ticks`

**Prompt AI:**

```text
Sci-fi machine gun overheating: a sharp pressurized steam vent hiss as cooling vents snap open, a short warning electronic buzz, then hot metal ticking and pinging as it cools. 1.8 seconds. Close-mic, dry, isolated sound effect, mono, no music, no voices.
```

---

## Shotgun

<a id="15"></a>
### 15. `shotgun.fire`

**P1** · 1,3 dtk · 3 varian · `shotgun/fire_0..2.ogg` · terdengar sampai 16 blok  
Tembakan Shotgun (kinetic blast, magenta).

**Cari:** `sci-fi shotgun blast` · `heavy shotgun shot` · `futuristic shotgun fire`

**Prompt AI:**

```text
Heavy futuristic shotgun turret blast: a wide booming low-end shot with a concussive air-pressure punch and a short energetic electric crack on top, quick decay. 1.3 seconds. Close-mic, dry, isolated sound effect, mono, no music, no voices.
```

<a id="16"></a>
### 16. `shotgun.fire_tail`

**P3** · 1,2 dtk · 2 varian · `gun/fire_tail_0..1.ogg` · terdengar sampai 16 blok  
Versi jauh tembakan Shotgun. *Saat ini memakai file `gun/fire_tail` dengan pitch 0,72. Buat file sendiri hanya kalau mau suara jauh yang khas.*

**Cari:** `distant shotgun` · `shotgun echo far` · `distant boom`

**Prompt AI:**

```text
A heavy shotgun blast, a deep boom, heard from far away: muffled and low-passed, no sharp close transient, followed by a soft rolling outdoor echo. 1.2 seconds. Isolated sound effect, mono, natural reverb tail, no music, no voices.
```

<a id="17"></a>
### 17. `shotgun.pump`

**P2** · 0,95 dtk · 1 varian · `shotgun/pump.ogg` · terdengar sampai 16 blok  
Pump di antara tembakan.

**Cari:** `shotgun pump action` · `weapon slide rack` · `mechanical reload slide`

**Prompt AI:**

```text
Futuristic shotgun pump action: a firm mechanical slide pulled back and pushed forward, two solid metal clacks with a subtle servo-assist whir between them, precise and heavy. 0.95 seconds. Close-mic, dry, isolated sound effect, mono, no music, no voices.
```

<a id="18"></a>
### 18. `shotgun.shell`

**P2** · 0,5 dtk · 2 varian · `shotgun/shell_0..1.ogg` · terdengar sampai 16 blok  
Selongsong shell jatuh saat pump.

**Cari:** `shotgun shell drop` · `shell casing falling` · `shell bounce stone`

**Prompt AI:**

```text
A single spent shotgun shell dropping onto stone: two or three light hollow bounces with a small metallic rim tick. 0.5 seconds. Close-mic, dry, isolated sound effect, mono, no music, no voices.
```

---

## Turret base

<a id="19"></a>
### 19. `turret.destroyed`

**P3** · 2,6 dtk · 1 varian · `turret/destroyed.ogg` · terdengar sampai 16 blok  
Turret hancur.

**Cari:** `machine destroyed explosion` · `electric short circuit explosion` · `robot destroyed`

**Prompt AI:**

```text
A futuristic automated turret being destroyed: a sharp electrical short-circuit crackle and a dying power-down whine, then a compact metallic explosion with crunching gunmetal plates and small debris clattering down. Dramatic but short, not a huge blast. 2.6 seconds. Isolated sound effect, mono, no music, no voices.
```

<a id="20"></a>
### 20. `turret.tier_up`

**P3** · 1,2 dtk · 1 varian · `turret/tier_up.ogg` · terdengar sampai 16 blok  
Base naik tier: pelat armor terkunci.

**Cari:** `upgrade sound sci-fi` · `armor lock clunk chime` · `power up success`

**Prompt AI:**

```text
Sci-fi upgrade: heavy armor plates sliding and locking into place with two firm mechanical clunks, followed by a bright rising energy chime that resolves upward, rewarding and satisfying. 1.2 seconds. Close-mic, dry, isolated sound effect, mono, no music, no voices.
```

<a id="21"></a>
### 21. `turret.repair`

**P3** · 0,8 dtk · 1 varian · `turret/repair.ogg` · terdengar sampai 16 blok  
Repair Kit dipakai ke turret.

**Cari:** `ratchet wrench repair` · `repair sound sci-fi` · `electric reconnect zap`

**Prompt AI:**

```text
Quick repair of a sci-fi machine: a few fast ratchet wrench clicks, a small electric zap of a circuit reconnecting, and a short positive two-note electronic chirp. 0.8 seconds. Close-mic, dry, isolated sound effect, mono, no music, no voices.
```

---

## Sniper

<a id="22"></a>
### 22. `sniper.charge`

**P2** · 1,5 dtk · 1 varian · `sniper/charge.ogg` · terdengar sampai 32 blok  
Sniper mengisi tembakan berat.

**Cari:** `sci-fi charge up` · `energy weapon charging` · `rising whine charge`

**Prompt AI:**

```text
A sci-fi sniper turret charging a heavy round: a rising electric whine that climbs smoothly in pitch, a faint capacitor hum and tiny clicks, building tension and stopping just before the shot. 1.5 seconds. Close-mic, dry, isolated sound effect, mono, no music, no voices.
```

<a id="23"></a>
### 23. `sniper.fire`

**P2** · 2,2 dtk · 2 varian · `sniper/fire_0..1.ogg` · terdengar sampai 32 blok  
Tembakan Sniper.

**Cari:** `sniper rifle shot heavy` · `anti-material rifle shot` · `high caliber gunshot`

**Prompt AI:**

```text
A heavy high-caliber sci-fi sniper rifle shot: an extremely sharp supersonic crack with a deep chest-thumping boom and a brief electric discharge zing, then a short metallic ring-out. Big and decisive. 2.2 seconds. Isolated sound effect, mono, no music, no voices.
```

<a id="24"></a>
### 24. `sniper.fire_tail`

**P3** · 2,4 dtk · 1 varian · `sniper/fire_tail.ogg` · terdengar sampai 96 blok  
Versi jauh tembakan Sniper (sampai 96 blok).

**Cari:** `distant sniper shot` · `far gunshot echo` · `rifle shot distant valley`

**Prompt AI:**

```text
A heavy sniper rifle shot heard from far away: muffled and low-passed, no sharp close transient, followed by a soft rolling outdoor echo. 2.4 seconds. Isolated sound effect, mono, natural reverb tail, no music, no voices.
```

---

## Rocket Launcher

<a id="25"></a>
### 25. `rocket.launch`

**P2** · 1,4 dtk · 2 varian · `rocket/launch_0..1.ogg` · terdengar sampai 32 blok  
Roket ditembakkan.

**Cari:** `rocket launch whoosh` · `rpg fire` · `rocket launcher shot`

**Prompt AI:**

```text
A sci-fi rocket launcher turret firing one dumb-fire rocket: a hard ignition thump and pop from the tube, then a fierce rushing rocket-motor whoosh moving away. No explosion. 1.4 seconds. Close-mic, dry, isolated sound effect, mono, no music, no voices.
```

<a id="26"></a>
### 26. `rocket.launch_tail`

**P3** · 1,8 dtk · 1 varian · `rocket/launch_tail.ogg` · terdengar sampai 96 blok  
Versi jauh peluncuran roket.

**Cari:** `distant rocket launch` · `far rocket whoosh` · `distant launch boom`

**Prompt AI:**

```text
A rocket launch, a thump and a whoosh, heard from far away: muffled and low-passed, no sharp close transient, followed by a soft rolling outdoor echo. 1.8 seconds. Isolated sound effect, mono, natural reverb tail, no music, no voices.
```

<a id="27"></a>
### 27. `rocket.fly`

**P2** · 1 dtk · 1 varian · `rocket/fly.ogg` · terdengar sampai 32 blok  
Mesin roket selama terbang; suara ikut bergerak bersama roket (loop).

**Cari:** `rocket engine loop` · `rocket thrust loop` · `missile flyby loop`

**Prompt AI:**

```text
A small rocket motor in flight: a steady roaring hiss of thrust with a slight crackle, constant intensity (the game moves the sound with the rocket). 1 second. Perfectly seamless loop with no start or end transients. Dry, isolated, mono, no music, no voices.
```

<a id="28"></a>
### 28. `rocket.explode`

**P2** · 2,8 dtk · 2 varian · `rocket/explode_0..1.ogg` · terdengar sampai 32 blok  
Ledakan roket. Juga ledakan peluru Mortar (pitch sedikit naik).

**Cari:** `explosion medium` · `rocket explosion` · `grenade explosion`

**Prompt AI:**

```text
A medium rocket explosion: a punchy concussive blast with a deep low-end boom, a bright crack on the attack and a short rumbling tail with light debris, no glass breaking. Powerful but not cinematic-huge. 2.8 seconds. Isolated sound effect, mono, natural outdoor tail, no music, no voices.
```

---

## Missile Launcher

<a id="29"></a>
### 29. `missile.launch`

**P2** · 0,9 dtk · 2 varian · `missile/launch_0..1.ogg` · terdengar sampai 32 blok  
Satu misil diluncurkan (salvo sampai 12 berurutan).

**Cari:** `missile launch short` · `micro missile fire` · `small rocket launch`

**Prompt AI:**

```text
A small homing micro-missile launching from a tube in a rapid salvo: a short sharp pneumatic pop followed by a quick fizzing whoosh that decays fast, so twelve can fire in a row without turning to mud. No explosion. 0.9 seconds. Close-mic, dry, isolated sound effect, mono, no music, no voices.
```

<a id="30"></a>
### 30. `missile.reload`

**P2** · 0,5 dtk · 1 varian · `missile/reload.ogg` · terdengar sampai 16 blok  
Tabung misil diisi ulang.

**Cari:** `weapon reload click` · `mechanical latch clunk` · `magazine insert`

**Prompt AI:**

```text
A missile tube being reloaded: a short mechanical slide and a solid latch clunk as a new missile locks in. 0.5 seconds. Close-mic, dry, isolated sound effect, mono, no music, no voices.
```

<a id="31"></a>
### 31. `missile.explode`

**P2** · 1,6 dtk · 2 varian · `missile/explode_0..1.ogg` · terdengar sampai 48 blok  
Ledakan misil kecil (banyak sekaligus).

**Cari:** `small explosion` · `grenade pop explosion` · `short blast`

**Prompt AI:**

```text
A small missile explosion, part of a rapid salvo: a tight punchy blast with a quick crack and a short low thump, fast decay so many overlapping hits stay clear. 1.6 seconds. Isolated sound effect, mono, short natural tail, no music, no voices.
```

---

## Tesla Coil

<a id="32"></a>
### 32. `tesla.charge`

**P2** · 0,7 dtk · 1 varian · `tesla/charge.ogg` · terdengar sampai 24 blok  
Coil mengisi sebelum menyambar.

**Cari:** `tesla coil charge` · `high voltage buzz rising` · `electric charge up`

**Prompt AI:**

```text
A large Tesla coil charging up: a rising high-voltage electric buzz with crackling static building quickly to a peak. 0.7 seconds. Close-mic, dry, isolated sound effect, mono, no music, no voices.
```

<a id="33"></a>
### 33. `tesla.zap`

**P2** · 0,9 dtk · 2 varian · `tesla/zap_0..1.ogg` · terdengar sampai 48 blok  
Sambaran petir Tesla.

**Cari:** `tesla coil zap` · `electric arc discharge` · `lightning zap`

**Prompt AI:**

```text
A powerful Tesla coil lightning discharge: a violent sharp electric crack with buzzing arc sizzle and a snapping zap, like a contained lightning bolt striking a target. No thunder rumble. 0.9 seconds. Close-mic, dry, isolated sound effect, mono, no music, no voices.
```

<a id="34"></a>
### 34. `tesla.zap_tail`

**P3** · 1,4 dtk · 1 varian · `tesla/zap_tail.ogg` · terdengar sampai 96 blok  
Versi jauh sambaran Tesla.

**Cari:** `distant lightning crack` · `far electric discharge` · `distant thunder short`

**Prompt AI:**

```text
An electric lightning discharge, a crack with a short thunder-like rumble, heard from far away: muffled and low-passed, no sharp close transient, followed by a soft rolling outdoor echo. 1.4 seconds. Isolated sound effect, mono, natural reverb tail, no music, no voices.
```

<a id="35"></a>
### 35. `tesla.crackle`

**P2** · 0,35 dtk · 2 varian · `tesla/crackle_0..1.ogg` · terdengar sampai 12 blok  
Percikan listrik kecil di sekitar coil. Juga charge Railgun dan Charging Station.

**Cari:** `electric crackle short` · `static sparks` · `electricity arcing`

**Prompt AI:**

```text
A short burst of quiet electric crackle: a few tiny static sparks snapping randomly, like electricity arcing across metal. No hum. 0.35 seconds. Close-mic, dry, isolated sound effect, mono, no music, no voices.
```

---

## Flamethrower

<a id="36"></a>
### 36. `flamethrower.ignite`

**P2** · 0,8 dtk · 1 varian · `flamethrower/ignite.ogg` · terdengar sampai 24 blok  
Flamethrower menyala. Juga api dari peluru Mortar.

**Cari:** `flamethrower ignite` · `gas burner ignition` · `fire whoosh ignite`

**Prompt AI:**

```text
A sci-fi flamethrower igniting: a sharp piezo igniter click followed by a fast gas whoosh as the flame bursts out. 0.8 seconds. Close-mic, dry, isolated sound effect, mono, no music, no voices.
```

<a id="37"></a>
### 37. `flamethrower.loop`

**P2** · 1 dtk · 1 varian · `flamethrower/loop.ogg` · terdengar sampai 24 blok  
Semburan api terus-menerus (loop).

**Cari:** `flamethrower loop` · `fire jet roar loop` · `gas torch loop`

**Prompt AI:**

```text
A flamethrower firing continuously: a steady roaring jet of burning fuel with turbulent flame rumble and hiss, constant intensity. 1 second. Perfectly seamless loop with no start or end transients. Dry, isolated, mono, no music, no voices.
```

<a id="38"></a>
### 38. `flamethrower.burning`

**P2** · 0,9 dtk · 1 varian · `flamethrower/burning.ogg` · terdengar sampai 12 blok  
Mob terbakar (burn custom). Juga di area api Mortar.

**Cari:** `fire sizzle` · `burning crackle small` · `flesh sizzle`

**Prompt AI:**

```text
A creature catching fire: a short crackling sizzle with small licking flames, quiet. No screams. 0.9 seconds. Close-mic, dry, isolated sound effect, mono, no music, no voices.
```

---

## Laser Rifle

<a id="39"></a>
### 39. `laser.charge`

**P2** · 2,5 dtk · 1 varian · `laser/charge.ogg` · terdengar sampai 24 blok  
Cahaya berkumpul di emitter (charge 2,5 dtk).

**Cari:** `laser charge up` · `sci-fi energy charge` · `power up whine`

**Prompt AI:**

```text
A sci-fi laser rifle turret charging: light gathering at the emitter, a smooth rising crystalline whine with a shimmering glassy resonance and a soft electric hum, accelerating toward the end. 2.5 seconds. Close-mic, dry, isolated sound effect, mono, no music, no voices.
```

<a id="40"></a>
### 40. `laser.fire`

**P2** · 1,2 dtk · 2 varian · `laser/fire_0..1.ogg` · terdengar sampai 48 blok  
Beam laser ditembakkan.

**Cari:** `laser beam shot` · `heavy laser blast` · `sci-fi laser fire`

**Prompt AI:**

```text
A heavy sci-fi laser beam discharge: an intense searing blast with a sharp bright attack, a sizzling hot electric burn and a deep resonant thump, decaying into a shimmering tail. Serious and powerful, not a toy pew. 1.2 seconds. Isolated sound effect, mono, no music, no voices.
```

<a id="41"></a>
### 41. `laser.fire_tail`

**P3** · 1,6 dtk · 1 varian · `laser/fire_tail.ogg` · terdengar sampai 96 blok  
Versi jauh tembakan laser.

**Cari:** `distant laser shot` · `far energy blast` · `laser echo`

**Prompt AI:**

```text
A heavy laser blast, a bright zap and thump, heard from far away: muffled and low-passed, no sharp close transient, followed by a soft rolling outdoor echo. 1.6 seconds. Isolated sound effect, mono, natural reverb tail, no music, no voices.
```

---

## Workstation

<a id="42"></a>
### 42. `workstation.loop`

**P1** · 2 dtk · 1 varian · `workstation/loop.ogg` · terdengar sampai 16 blok  
Workstation sedang bekerja (loop).

**Cari:** `factory machine loop` · `sci-fi machinery loop` · `fabricator working loop`

**Prompt AI:**

```text
A compact sci-fi fabrication workstation at work: a steady low motor hum, a soft cooling fan and light rhythmic servo movements, clean high-tech industry, quiet ambient level. 2 seconds. Perfectly seamless loop with no start or end transients. Dry, isolated, mono, no music, no voices.
```

<a id="43"></a>
### 43. `workstation.weld`

**P2** · 0,5 dtk · 1 varian · `workstation/weld.ogg` · terdengar sampai 16 blok  
Percikan las saat merakit.

**Cari:** `welding spark short` · `arc welder burst` · `electric weld sizzle`

**Prompt AI:**

```text
A short burst of electric arc welding: crackling buzzing sizzle with tiny spark spatter. 0.5 seconds. Close-mic, dry, isolated sound effect, mono, no music, no voices.
```

<a id="44"></a>
### 44. `workstation.press`

**P2** · 0,7 dtk · 1 varian · `workstation/press.ogg` · terdengar sampai 16 blok  
Hentakan press saat merakit.

**Cari:** `hydraulic press stamp` · `metal stamping press` · `pneumatic press thump`

**Prompt AI:**

```text
A hydraulic press stamping a metal part: a short pneumatic hiss, then a heavy solid metallic thunk. 0.7 seconds. Close-mic, dry, isolated sound effect, mono, no music, no voices.
```

<a id="45"></a>
### 45. `workstation.done`

**P2** · 0,9 dtk · 1 varian · `workstation/done.ogg` · terdengar sampai 16 blok  
Kerajinan selesai.

**Cari:** `craft complete chime` · `machine finished ding` · `sci-fi success notification`

**Prompt AI:**

```text
A sci-fi machine finishing a job: a soft mechanical release clunk followed by a pleasant bright two-note completion chime. 0.9 seconds. Clean, isolated sound effect, mono, no music bed, no voices.
```

---

## Railgun

<a id="46"></a>
### 46. `railgun.charge`

**P2** · 3 dtk · 1 varian · `railgun/charge.ogg` · terdengar sampai 32 blok  
Railgun mengisi 3 dtk; kumparan menyala satu per satu.

**Cari:** `railgun charge` · `capacitor charging whine` · `electromagnetic charge up`

**Prompt AI:**

```text
A massive railgun turret charging: a deep electrical hum that swells into a rising high-pitched capacitor whine, a series of heavy relay clicks as coils energize one by one along the rails, and growing electric crackle near the end. Tense and powerful. 3 seconds. Close-mic, dry, isolated sound effect, mono, no music, no voices.
```

<a id="47"></a>
### 47. `railgun.fire`

**P2** · 1,8 dtk · 2 varian · `railgun/fire_0..1.ogg` · terdengar sampai 64 blok  
Tembakan Railgun.

**Cari:** `railgun shot` · `electromagnetic cannon fire` · `heavy sci-fi cannon blast`

**Prompt AI:**

```text
A railgun firing a metal slug: an enormous electromagnetic discharge with a violent supersonic crack, a deep bass thump that hits the chest and a sizzling electric whip tearing through the air. The heaviest weapon in the arsenal. 1.8 seconds. Isolated sound effect, mono, short natural tail, no music, no voices.
```

<a id="48"></a>
### 48. `railgun.fire_tail`

**P3** · 2,2 dtk · 1 varian · `railgun/fire_tail.ogg` · terdengar sampai 128 blok  
Versi jauh tembakan Railgun (sampai 128 blok).

**Cari:** `distant cannon shot` · `far artillery boom` · `distant sonic crack`

**Prompt AI:**

```text
A railgun shot, a supersonic crack and deep boom, heard from far away: muffled and low-passed, no sharp close transient, followed by a soft rolling outdoor echo. 2.2 seconds. Isolated sound effect, mono, natural reverb tail, no music, no voices.
```

<a id="49"></a>
### 49. `railgun.impact`

**P2** · 1,5 dtk · 2 varian · `railgun/impact_0..1.ogg` · terdengar sampai 48 blok  
Slug Railgun kena target + shockwave.

**Cari:** `heavy impact shockwave` · `metal slug impact` · `heavy hit boom`

**Prompt AI:**

```text
A hypersonic railgun slug hitting its target: a heavy concussive impact slam with a shockwave whoomp, a brief electric crackle and scattering debris. 1.5 seconds. Isolated sound effect, mono, short natural tail, no music, no voices.
```

---

## Mortar

<a id="50"></a>
### 50. `mortar.fire`

**P2** · 1,4 dtk · 2 varian · `mortar/fire_0..1.ogg` · terdengar sampai 48 blok  
Mortar menembakkan peluru.

**Cari:** `mortar fire` · `mortar tube launch` · `artillery thump`

**Prompt AI:**

```text
A mortar turret firing a shell: a deep hollow tube 'thoonk' with a strong low-end punch and a short whoosh of the shell leaving the barrel. No explosion. 1.4 seconds. Close-mic, dry, isolated sound effect, mono, no music, no voices.
```

<a id="51"></a>
### 51. `mortar.fire_tail`

**P3** · 1,8 dtk · 1 varian · `mortar/fire_tail.ogg` · terdengar sampai 128 blok  
Versi jauh tembakan Mortar (sampai 128 blok).

**Cari:** `distant mortar` · `far artillery thump` · `distant cannon`

**Prompt AI:**

```text
A mortar shot, a deep hollow thump, heard from far away: muffled and low-passed, no sharp close transient, followed by a soft rolling outdoor echo. 1.8 seconds. Isolated sound effect, mono, natural reverb tail, no music, no voices.
```

<a id="52"></a>
### 52. `mortar.whistle` (dihapus 10-17)

Tidak dipakai lagi: user memutuskan peluru Mortar jatuh tanpa suara. Event, file, subtitle dan generatornya dihapus. Catatan lama di bawah hanya arsip.

**P2** · 1,6 dtk · 1 varian · `mortar/whistle.ogg` · terdengar sampai 40 blok  
Siulan peluru Mortar yang sedang jatuh.

**Cari:** `incoming shell whistle` · `artillery whistle falling` · `bomb drop whistle`

**Prompt AI:**

```text
An incoming mortar shell: a descending airy whistle that falls in pitch as the shell drops, ending abruptly before impact. No explosion. 1.6 seconds. Close-mic, dry, isolated sound effect, mono, no music, no voices.
```

---

## Repulsor

<a id="53"></a>
### 53. `repulsor.charge`

**P2** · 0,6 dtk · 1 varian · `repulsor/charge.ogg` · terdengar sampai 24 blok  
Repulsor mengisi sebelum mendorong.

**Cari:** `force field charge` · `air pressure build up` · `energy charge short`

**Prompt AI:**

```text
A sci-fi force repulsor charging: a short rising hum with air being drawn in, a soft pressurized swell. 0.6 seconds. Close-mic, dry, isolated sound effect, mono, no music, no voices.
```

<a id="54"></a>
### 54. `repulsor.push`

**P2** · 0,9 dtk · 2 varian · `repulsor/push_0..1.ogg` · terdengar sampai 48 blok  
Dorongan kerucut Repulsor (tanpa damage).

**Cari:** `force push whoosh` · `air blast` · `sci-fi force wave`

**Prompt AI:**

```text
A sci-fi force repulsor blast: a powerful focused air-pressure whoomp with a cone-shaped rushing wind burst and a soft low thump. No explosion, no fire. 0.9 seconds. Close-mic, dry, isolated sound effect, mono, no music, no voices.
```

<a id="55"></a>
### 55. `repulsor.push_tail`

**P3** · 1,2 dtk · 1 varian · `repulsor/push_tail.ogg` · terdengar sampai 128 blok  
Versi jauh dorongan Repulsor.

**Cari:** `distant whoosh` · `far air blast` · `distant thump`

**Prompt AI:**

```text
A focused air-pressure blast, a whoomp, heard from far away: muffled and low-passed, no sharp close transient, followed by a soft rolling outdoor echo. 1.2 seconds. Isolated sound effect, mono, natural reverb tail, no music, no voices.
```

---

## Repulsor Dome

<a id="56"></a>
### 56. `repulsor.dome_charge`

**P2** · 0,6 dtk · 1 varian · `repulsor/dome_charge.ogg` · terdengar sampai 24 blok  
Repulsor Dome mengisi sebelum meledakkan kubah.

**Cari:** `force field generator charge` · `deep hum rising` · `pressure build up`

**Prompt AI:**

```text
A large force-field generator charging: a deep rising hum with swirling air, building pressure for a big burst. 0.6 seconds. Close-mic, dry, isolated sound effect, mono, no music, no voices.
```

<a id="57"></a>
### 57. `repulsor.dome_burst`

**P2** · 2 dtk · 2 varian · `repulsor/dome_burst_0..1.ogg` · terdengar sampai 48 blok  
Kubah dorong ke segala arah (tanpa damage).

**Cari:** `shockwave blast` · `force field burst` · `sonic boom wave`

**Prompt AI:**

```text
A huge omnidirectional force-field burst: a deep powerful shockwave whoomp expanding outward, a rush of wind and rolling dust, with a soft electric shimmer. Pure force, no explosion, no fire. 2 seconds. Isolated sound effect, mono, natural tail, no music, no voices.
```

<a id="58"></a>
### 58. `repulsor.dome_burst_tail`

**P3** · 2,4 dtk · 1 varian · `repulsor/dome_burst_tail.ogg` · terdengar sampai 128 blok  
Versi jauh kubah Repulsor Dome (sampai 128 blok).

**Cari:** `distant shockwave` · `far boom wave` · `distant whoomp`

**Prompt AI:**

```text
A huge shockwave whoomp heard from far away: muffled and low-passed, no sharp close transient, followed by a soft rolling outdoor echo. 2.4 seconds. Isolated sound effect, mono, natural reverb tail, no music, no voices.
```

---

## Ion Rifle

<a id="59"></a>
### 59. `ion.fire`

**P2** · 0,6 dtk · 2 varian · `ion/fire_0..1.ogg` · terdengar sampai 48 blok  
Ion Rifle menembakkan peluru ion.

**Cari:** `ion cannon shot` · `sci-fi energy bolt fire` · `electric blaster shot`

**Prompt AI:**

```text
An ion rifle turret firing a fast ion bolt: a bright electric zap-thump with a crisp crackling snap and a short airy electric whoosh, cold blue energy feel, punchy and clean. 0.6 seconds. Close-mic, dry, isolated sound effect, mono, no music, no voices.
```

<a id="60"></a>
### 60. `ion.hit`

**P2** · 0,6 dtk · 2 varian · `ion/hit_0..1.ogg` · terdengar sampai 32 blok  
Inti ion terbentuk dan menyambar semua musuh di sekitarnya.

**Cari:** `electric discharge burst` · `electric arcs zap` · `ion impact crackle`

**Prompt AI:**

```text
An ion bolt hitting and releasing a burst of electric arcs to several nearby enemies at once: a sharp crackling electric discharge with quick buzzing zaps radiating outward, fading fast. 0.6 seconds. Close-mic, dry, isolated sound effect, mono, no music, no voices.
```

---

## Plasma Repeater

<a id="61"></a>
### 61. `plasma_repeater.fire`

**P2** · 0,3 dtk · 3 varian · `plasma/repeater_fire_0..2.ogg` · terdengar sampai 32 blok  
Satu tembakan bola plasma (4 per detik).

**Cari:** `plasma gun shot` · `sci-fi blaster pew heavy` · `plasma rifle fire`

**Prompt AI:**

```text
One shot of a plasma repeater turret: a short thick 'thoom' of a glowing plasma ball being launched, warm bubbling energy with a soft electric edge, punchy with a quick decay so it repeats cleanly at four shots per second. 0.3 seconds. Close-mic, dry, isolated sound effect, mono, no music, no voices.
```

<a id="62"></a>
### 62. `plasma.hit_small`

**P2** · 0,4 dtk · 2 varian · `plasma/hit_small_0..1.ogg` · terdengar sampai 16 blok  
Bola plasma Repeater kena dan memercik.

**Cari:** `plasma impact` · `energy splat sizzle` · `small energy explosion`

**Prompt AI:**

```text
A small plasma ball impact: a hot wet sizzling splat with a tiny energy pop. 0.4 seconds. Close-mic, dry, isolated sound effect, mono, no music, no voices.
```

<a id="63"></a>
### 63. `plasma.vent`

**P2** · 1,5 dtk · 1 varian · `plasma/vent.ogg` · terdengar sampai 16 blok  
Plasma Repeater overheat dan vent.

**Cari:** `steam vent hiss` · `overheat vent sci-fi` · `pressure release hiss`

**Prompt AI:**

```text
A plasma weapon venting excess heat: a long pressurized steam hiss with a hot electric hum fading out and hot metal ticking. 1.5 seconds. Close-mic, dry, isolated sound effect, mono, no music, no voices.
```

---

## Plasma Cannon

<a id="64"></a>
### 64. `plasma_cannon.charge`

**P2** · 1 dtk · 1 varian · `plasma/cannon_charge.ogg` · terdengar sampai 24 blok  
Bola plasma besar tumbuh di moncong (charge 1 dtk).

**Cari:** `energy charge up deep` · `plasma charge` · `power build up rumble`

**Prompt AI:**

```text
A plasma cannon charging a large plasma ball: a rising bubbling roar of contained energy with a deep throbbing hum and swirling intake, building to a peak. 1 second. Close-mic, dry, isolated sound effect, mono, no music, no voices.
```

<a id="65"></a>
### 65. `plasma_cannon.fire`

**P2** · 1,4 dtk · 2 varian · `plasma/cannon_fire_0..1.ogg` · terdengar sampai 64 blok  
Plasma Cannon menembakkan bola besar yang lambat.

**Cari:** `plasma cannon shot` · `heavy energy cannon fire` · `sci-fi cannon whoomp`

**Prompt AI:**

```text
A plasma cannon firing a huge slow plasma ball: a heavy deep 'whumm' launch with a thick warm energy roar and a pulsing hum as the ball leaves the barrel. 1.4 seconds. Close-mic, dry, isolated sound effect, mono, no music, no voices.
```

<a id="66"></a>
### 66. `plasma_cannon.hit`

**P2** · 1,6 dtk · 2 varian · `plasma/cannon_hit_0..1.ogg` · terdengar sampai 48 blok  
Bola besar meledak (gaya BFG 9000): awan plasma hijau radius 4 blok.

**Cari:** `plasma explosion` · `energy blast big` · `bfg explosion`

**Prompt AI:**

```text
A huge plasma explosion like a sci-fi BFG blast: a deep concussive whoomp with a bright energetic crackle on the attack, a roaring wash of searing plasma that swells and rolls outward, then a fizzing, sizzling fade. Massive and satisfying, not a fiery chemical explosion. 1.6 seconds. Isolated sound effect, mono, short natural tail, no music, no voices.
```

---

## Turret Control Hub

<a id="67"></a>
### 67. `control_hub.online`

**P2** · 1,2 dtk · 1 varian · `control_hub/online.ogg` · terdengar sampai 24 blok  
Hub jadi atau FE kembali: hub menyala.

**Cari:** `sci-fi system online` · `power up chime` · `servo startup`

**Prompt AI:**

```text
A sci-fi command station coming online: a short servo whirr under two rising clean electronic chimes, confident and bright. 1.2 seconds. Close-mic, dry, isolated sound effect, mono, no music, no voices.
```

<a id="68"></a>
### 68. `control_hub.offline`

**P2** · 1 dtk · 1 varian · `control_hub/offline.ogg` · terdengar sampai 24 blok  
FE habis: hub mati.

**Cari:** `power down sci-fi` · `system shutdown` · `relay click power off`

**Prompt AI:**

```text
A sci-fi console losing power: a falling electronic tone sliding down with a soft relay click at the start, short and clear. 1 second. Close-mic, dry, isolated sound effect, mono, no music, no voices.
```

<a id="69"></a>
### 69. `control_hub.dish_loop`

**P2** · 3 dtk · 1 varian · `control_hub/dish_loop.ogg` · terdengar sampai 12 blok  
Dish satelit berputar saat hub online.

**Cari:** `radar dish motor loop` · `servo hum loop` · `antenna rotator`

**Prompt AI:**

```text
A satellite dish slowly rotating on a motorized mount: a low steady electrical hum with a soft rhythmic servo whirr, seamless loop. 3 seconds. Close-mic, dry, isolated sound effect, mono, seamless loop, no music, no voices.
```

<a id="70"></a>
### 70. `control_hub.ui_select`

**P3** · 0,12 dtk · 1 varian · `control_hub/ui_select.ogg` · GUI (tanpa jarak)  
Klik/pilih turret di layar hub.

**Cari:** `ui beep short` · `sci-fi interface click`

**Prompt AI:**

```text
A tiny clean sci-fi interface beep for selecting an item on a tactical map, very short. 0.12 seconds. Dry, isolated UI sound, mono, no music, no voices.
```
