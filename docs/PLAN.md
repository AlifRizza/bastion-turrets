# Bastion Turrets — Master Plan untuk Claude Code

> Mod turret standalone bergaya **futuristik** untuk **Minecraft Forge 1.20.1**. MVP: **Gun Turret, Machine Gun Turret, Shotgun Turret**. Base turret bertier, weapon module yang dipasang di atas base, ammo yang bisa diotomasi lewat sistem transport mod mana pun, modifier, GUI filter target, dan **VFX yang 100% custom tanpa aset visual/audio vanilla**.
>
> Nama sementara: **Bastion Turrets**, mod id `bastion`, package `dev.bastion`. Ganti bebas, tapi konsisten di semua file.

---

## 0. Cara pakai dokumen ini

1. Simpan file ini sebagai `docs/PLAN.md` di root repo.
2. Buat `CLAUDE.md` di root repo:
   ```markdown
   Baca docs/PLAN.md sebelum mengerjakan apa pun.
   Kerjakan HANYA fase yang diminta. Patuhi bagian 1 (Quality Bar) dan bagian 9 (Aturan Kerja) tanpa pengecualian.
   Setelah selesai satu fase: pastikan `./gradlew build` lolos, lalu tulis checklist cara saya mengetesnya di game.
   ```
3. Prompt pertama di Claude Code: `Kerjakan Fase 0 dari docs/PLAN.md.`
4. Setelah tiap fase: jalankan `./gradlew runClient`, test di game, kirim feedback visual (screenshot/rekaman + apa yang kurang), baru lanjut fase berikutnya.
5. Blockbench MCP sudah terpasang. Model dan animasi dibuat lewat MCP itu mengikuti konvensi di bagian 6.

---

## 1. Visi & Quality Bar

**Target rasa:** setiap tembakan terasa "berat" dan sinematik seperti Superb Warfare atau serangan boss modern. Ada antisipasi (windup/charge), aksi (muzzle flash, proyektil bercahaya, recoil), dan konsekuensi (impact, partikel, cahaya, getaran kamera). Turret terasa hidup bahkan saat diam (idle hum, scanning sweep, lampu status berdenyut).

### Aturan NO-VANILLA (wajib, tidak bisa ditawar)

| Dilarang | Gantinya |
|---|---|
| `ParticleTypes.*` vanilla (smoke, flame, crit, dust, explosion, dll.) | `ParticleType` milik `bastion` dengan sprite sendiri |
| Proyektil yang di-render sebagai item (`ThrowableItemProjectile`, snowball, arrow renderer) | `EntityRenderer` custom: billboard core+glow, ribbon trail, atau model GeckoLib |
| Tekstur vanilla untuk model, partikel, beam, GUI | Tekstur original (dibuat di Blockbench atau digenerate prosedural oleh `tools/gen_textures.py`) |
| `SoundEvents.*` vanilla untuk tembakan/impact/ambient | `SoundEvent` milik `bastion` dengan file `.ogg` sendiri |
| `Level.explode()` dengan efek visual vanilla | Sistem ledakan sendiri: damage + knockback dihitung manual, visual dari VFX framework |
| GUI dengan background chest/furnace vanilla | Tekstur GUI original |

Yang **boleh**: API dan class vanilla/Forge sebagai fondasi kode (`Entity`, `BlockEntity`, `AbstractContainerMenu`, `RenderType`, dll.), dan item vanilla sebagai bahan resep.

### Kriteria "bagus" yang terukur

- Rotasi turret selalu diinterpolasi dengan `partialTick`, tidak pernah patah-patah antar tick.
- Setiap tembakan punya minimal: animasi keyframe (recoil/charge), muzzle flash custom, cahaya dinamis singkat, efek impact custom, dan suara custom.
- Proyektil bercahaya (fullbright + additive glow) dan punya trail.
- Tidak ada efek yang "muncul tiba-tiba lalu hilang". Semua efek punya fade-in/fade-out atau kurva skala.
- Di 60 FPS dengan 10 turret aktif menembak bersamaan, FPS turun maksimal 15% di preset VFX `HIGH` (lihat budget di bagian 9).

---

## 2. Tech Stack & Keputusan Arsitektur

| Komponen | Pilihan | Alasan |
|---|---|---|
| Loader | Forge 1.20.1, `47.2.0+` | Ekosistem TacZ, SBW, Create, Sentry Arm ada di sini |
| Bahasa | Java 17 | Sederhana, tanpa runtime Kotlin |
| Mapping | Parchment 1.20.1 | Nama parameter terbaca |
| Animasi & model | GeckoLib 4.4.x (`GeoBlockEntity`, `GeoItem`, `GeoEntity`) | Format Bedrock langsung dari Blockbench, animasi keyframe + kontrol bone procedural |
| Data senjata | JSON di datapack `data/bastion/turret_weapons/*.json` | Balancing tanpa recompile |
| Dependency wajib | Forge + GeckoLib saja | Mod tetap standalone |
| Compat opsional (fase akhir) | Jade, JEI, Create (Ponder), Iris/Oculus | Tanpa hard dependency |

**Keputusan kunci:**

1. **Base = Block + BlockEntity**, bukan entity. Supaya hopper, belt/funnel/chute Create, pipa Mekanism, conveyor mod lain bisa memasukkan ammo lewat capability `ForgeCapabilities.ITEM_HANDLER`. Tidak perlu integrasi khusus per mod.
2. **Weapon = Item** yang tidak punya aksi tembak. Hanya berfungsi saat terpasang di base.
3. **Logika senjata server-authoritative**, visual seluruhnya client-side. Server memutuskan target, rotasi, kapan menembak, dan damage. Client menerima event lewat packet lalu memainkan animasi dan VFX.
4. **Tidak menyalin kode dari mod GPL** (Superb Warfare, TacZ). Boleh mempelajari tekniknya, tulis implementasi sendiri. Kode Create Sentry Mechanical Arm (MIT) boleh dijadikan referensi dengan atribusi di README jika ada bagian yang diadaptasi.

---

## 3. Struktur Project

```
src/main/java/dev/bastion/
├── Bastion.java                     # @Mod entry, event bus wiring
├── registry/                        # BastionBlocks, BastionItems, BastionBlockEntities,
│                                    # BastionEntities, BastionMenus, BastionParticles,
│                                    # BastionSounds, BastionWeaponTypes
├── turret/
│   ├── TurretBaseBlock.java
│   ├── TurretBaseBlockEntity.java   # state, inventory, tick, targeting, fire cycle
│   ├── TurretTier.java              # enum T1..T3 + stat
│   ├── TurretState.java             # enum state machine
│   ├── TurretInventory.java         # slot weapon / ammo / modifier
│   ├── AmmoOnlyItemHandler.java     # wrapper capability: hanya slot ammo yang terekspos
│   ├── TurretHitboxEntity.java      # entity tak terlihat penerima damage untuk HP base
│   └── targeting/                   # TargetFilter, TargetSelector, LeadSolver, LineOfSight
├── weapon/
│   ├── WeaponType.java              # abstract: logika fire per senjata
│   ├── WeaponData.java              # stat dari JSON
│   ├── WeaponDataLoader.java        # SimpleJsonResourceReloadListener
│   ├── WeaponModuleItem.java        # GeoItem
│   ├── StatSheet.java               # stat final = base x tier x modifier
│   └── types/                       # MVP: GunWeapon, MachineGunWeapon, ShotgunWeapon
├── modifier/                        # ModifierItem, ModifierEffect, daftar modifier
├── projectile/                      # (post-MVP) entity proyektil sungguhan untuk senjata lambat
├── damage/                          # BastionDamageTypes, BastionExplosion (tanpa visual vanilla)
├── menu/                            # TurretMenu, slot classes, filter data sync
├── network/                         # BastionNetwork + packet (lihat 4.8)
├── config/                          # BastionConfig (common), BastionClientConfig (VFX quality)
└── client/
    ├── BastionClient.java
    ├── render/
    │   ├── BastionRenderTypes.java  # additive, emissive, beam, soft particle
    │   ├── TurretBaseRenderer.java  # GeoBlockRenderer + render weapon di mount bone
    │   ├── WeaponModelRenderer.java
    │   ├── TracerRenderer.java          # tracer visual hitscan
│   ├── HologramRenderer.java        # reticle, angka ammo, cincin status
│   ├── CasingRenderer.java          # casing/shell yang terlempar
    │   ├── TrailRenderer.java
    │   └── projectile/              # renderer per proyektil
    ├── vfx/
    │   ├── VfxManager.java          # titik masuk: VfxManager.play(preset, pos, dir, params)
    │   ├── VfxPreset.java
    │   ├── VfxPresets.java          # semua preset efek per senjata
    │   ├── DynamicLightManager.java
    │   ├── CameraShake.java
    │   └── ScreenFlash.java
    ├── particle/                    # Particle + Provider per tipe
    ├── anim/                        # WeaponAnimatable (client-side per turret), RecoilSpring
    └── screen/                      # TurretScreen, tab, widget filter

src/main/resources/
├── assets/bastion/
│   ├── geo/        animations/      textures/{block,item,entity,particle,gui,vfx}/
│   ├── particles/  sounds/          sounds.json   lang/en_us.json  lang/id_id.json
│   └── shaders/core/                # shader soft particle + beam (fase VFX)
└── data/bastion/
    ├── turret_weapons/              # gun.json, machine_gun.json, shotgun.json
    ├── recipes/  loot_tables/  tags/

tools/
├── gen_textures.py                  # generator sprite prosedural (Python + numpy + Pillow)
└── gen_sounds.py                    # synth placeholder SFX (lihat 5.7)
```

---

## 4. Sistem Inti

### 4.1 Turret Base & Tier

Satu block `bastion:turret_base` dengan field `tier` di BlockEntity (bukan block terpisah per tier). Upgrade dengan item `bastion:tier_upgrade_kit_t2` / `_t3` yang diklik kanan ke base. Tier tidak bisa turun.

| Stat | T1 | T2 | T3 |
|---|---|---|---|
| Max HP | 100 | 250 | 500 |
| Slot ammo | 3 | 6 | 9 |
| Slot modifier | 1 | 2 | 4 |
| Regen HP | 0 | 0.5/detik | 1/detik |
| Multiplier turn speed | 1.0 | 1.15 | 1.3 |
| Visual | base polos | + plat armor + lampu status | + sirip pendingin + emissive trim |

Visual tier diatur lewat visibility bone di model base yang sama (`armor_t2`, `fins_t3`), bukan model berbeda.

**HP base.** Block tidak punya HP bawaan, jadi:
- BlockEntity menyimpan `health`.
- Saat aktif, base men-spawn `TurretHitboxEntity` (tidak di-render, tanpa gravitasi, tidak bisa didorong) seukuran turret. Entity ini menerima damage dari proyektil/serangan/ledakan dan meneruskannya ke BlockEntity.
- HP 0 → turret hancur: ledakan custom (tanpa merusak block), drop weapon + isi inventory + base yang turun 1 tier (atau drop "damaged base" di T1).
- Saat HP < 30%: animasi `damaged` (percikan, asap custom tipis, lampu status berkedip merah).
- Perbaikan: klik kanan dengan item `bastion:repair_kit`.

### 4.2 Inventory & Otomasi Ammo

| Slot | Isi | Terekspos ke capability? |
|---|---|---|
| Weapon (1) | `WeaponModuleItem` | Tidak |
| Ammo (3–9) | Item yang cocok dengan tag `bastion:ammo/<jenis>` dari weapon terpasang | **Ya, insert & extract** |
| Modifier (1–4) | `ModifierItem` | Tidak |

- `AmmoOnlyItemHandler` membungkus slot ammo saja untuk `getCapability(ITEM_HANDLER)` dari **semua sisi**. `isItemValid` menolak item yang bukan ammo untuk weapon yang terpasang, supaya belt tidak memasukkan sampah.
- Tanpa weapon terpasang: ammo apa pun dari daftar ammo mod diterima (supaya bisa diisi duluan).
- Comparator output: level isi ammo (0–15).
- Opsional fase akhir: capability `ENERGY` (FE) sebagai alternatif ammo untuk senjata energi.

### 4.3 Weapon Module

- `WeaponModuleItem` implements `GeoItem`. Di tangan pemain: model 3D dengan animasi `held_idle` (lampu berdenyut). Klik kanan di udara tidak melakukan apa pun. Tooltip menampilkan stat dan kemampuan khusus.
- Pasang: klik kanan base dengan weapon, atau lewat slot GUI. Cabut: shift + klik kanan dengan tangan kosong (turret harus tidak sedang menembak).
- Saat dipasang: animasi `deploy` dimainkan (weapon "terbuka" dan mengunci ke mount).
- `WeaponType` (abstract) mendefinisikan:
  ```java
  abstract void onFire(FireContext ctx);          // server: spawn proyektil / raycast / efek area
  abstract FireProfile fireProfile();             // MVP: HITSCAN_SINGLE, HITSCAN_SPINUP, HITSCAN_SPREAD
  int chargeTicks(StatSheet s);                   // 0 jika tidak ada charge
  boolean canTarget(Entity e, TurretContext ctx); // filter tambahan per senjata
  ```

### 4.4 State Machine Fire Cycle

```
DISABLED (tanpa weapon / redstone off / efek disable dari senjata post-MVP)
   │ weapon terpasang
   ▼
IDLE ──(target valid ditemukan)──► ACQUIRING ──(rotasi dalam toleransi)──► AIMING
  ▲                                                                         │
  │                                         chargeTicks>0 ? CHARGING : FIRING
  │                                                                         ▼
COOLDOWN ◄──────────────────────────── FIRING ◄────────────── CHARGING
  │  (heat > max) ──► OVERHEAT ──(dingin)──► IDLE
  └─(ammo habis) ──► NO_AMMO ──(ammo masuk)──► IDLE
```

- State disimpan di BlockEntity dan disinkronkan ke client (lewat `getUpdateTag` + packet event untuk transisi penting) supaya animasi sesuai state.
- **Heat**: tiap tembakan menambah heat, turun per tick. Overheat memicu animasi `overheat` (ventilasi terbuka, uap custom, glow merah di laras).
- **Redstone**: sinyal redstone menonaktifkan turret (bisa dibalik di GUI).

### 4.5 Targeting

- `TargetSelector` mencari entity dalam radius `range` setiap 10 tick (bukan tiap tick), lalu memvalidasi target tiap tick.
- Urutan validasi: filter GUI → `WeaponType.canTarget` → line of sight (raycast dari titik muzzle ke bagian tengah/kepala target, menghindari block) → batas pitch.
- Prioritas (bisa dipilih di GUI): terdekat, HP terendah, ancaman tertinggi (mob yang sedang menyerang owner/turret).
- `LeadSolver`: disiapkan untuk senjata proyektil post-MVP. Ketiga turret MVP hitscan, jadi tidak memakai lead.
- Rotasi: server menyimpan `yaw`, `pitch` target; diputar menuju target dengan `turnSpeed` derajat per tick (dengan easing kecil saat mendekati). Client menginterpolasi `prevYaw → yaw` dengan `partialTick`.
- Saat tidak ada target: animasi scanning sweep (yaw bergerak pelan kiri-kanan ±45°) yang dihitung client-side murni.

### 4.6 Modifier

Diterapkan sebagai multiplier/additive ke `StatSheet`. Stat final = `WeaponData` × multiplier tier × modifier.

| Modifier | Efek | Visual di turret |
|---|---|---|
| Range Module | range +25% | antena kecil muncul di base |
| Rapid Cycler | fire rate +20%, heat per tembakan +15% | — |
| Damage Amplifier | damage +20% | warna efek sedikit lebih terang |
| Penetrator | proyektil/hitscan menembus +1 target | — |
| Ammo Recycler | 20% peluang tidak memakai ammo | — |
| Coolant Loop | heat dissipation +40% | pipa coolant di base |
| Overclock | fire rate +40%, damage +10%, heat +60%, HP base −15% | glow oranye berdenyut |
| Targeting AI | lead lebih akurat, turn speed +25% | lensa sensor bercahaya |

Satu modifier yang sama tidak bisa dipasang dua kali.

### 4.7 GUI

Layar custom dengan tekstur original, 3 tab:

1. **Status**: slot weapon, slot ammo, slot modifier, bar HP, bar heat, tier, state saat ini, tombol on/off.
2. **Targeting**:
   - Toggle kategori: Hostile, Neutral, Passive, Players, Boss.
   - Mode pemain: semua / semua kecuali owner / semua kecuali team & trusted.
   - Daftar trusted player (tambah via nama).
   - Whitelist/blacklist entity type spesifik (search box dengan daftar `EntityType` terdaftar).
   - Prioritas target (dropdown).
3. **Info**: stat final weapon setelah modifier, deskripsi kemampuan khusus.

Item **Turret Configurator**: shift-klik base untuk menyalin pengaturan targeting, klik base lain untuk menempel. Akses GUI dan cabut weapon hanya untuk owner + trusted (opsi config untuk menonaktifkan proteksi).

### 4.8 Network Packet

| Packet | Arah | Isi |
|---|---|---|
| `TurretStateSync` | S→C | state, yaw, pitch, targetId, heat, hp |
| `TurretFireEvent` | S→C | pos turret, index muzzle, weaponType, seed, daftar titik akhir tracer (1 untuk Gun/MG, 8 untuk Shotgun), flag Precision Lock |
| `TurretImpactEvent` | S→C | posisi impact, normal, tipe impact, skala |
| `SpinSync` | S→C | kecepatan spin laras Machine Gun (hanya saat berubah signifikan) |
| `TurretConfigUpdate` | C→S | perubahan filter/pengaturan dari GUI (divalidasi owner) |

Semua packet S→C dikirim hanya ke pemain dalam radius 96 block. `seed` dipakai client agar variasi acak VFX konsisten.

---

## 5. VFX Framework

Dibangun **sekali**, dipakai semua senjata. Setiap efek dipanggil lewat satu pintu:

```java
VfxManager.play(VfxPresets.GUN_MUZZLE, origin, direction, VfxParams.of().scale(1.0f).color(0xFF4FD8FF));
```

### 5.1 Render Types (`BastionRenderTypes`)

| Nama | Blending | Depth write | Lightmap | Dipakai untuk |
|---|---|---|---|---|
| `ADDITIVE_GLOW` | `SRC_ALPHA, ONE` | off | fullbright | glow proyektil, muzzle flash, inti beam |
| `TRANSLUCENT_EMISSIVE` | translucent | off | fullbright | panel/lampu emissive di model (layer kedua GeckoLib) |
| `BEAM` | additive, texture scroll lewat UV offset | off | fullbright | (post-MVP) Laser, Rail, arc listrik |
| `SOFT_PARTICLE` | translucent + shader soft-depth | off | world light | asap, debu |

Model GeckoLib memakai `AutoGlowingGeoLayer` (atau layer emissive sendiri) dengan tekstur `_e.png` untuk bagian yang menyala.

### 5.2 Teknik Render Proyektil

- **Billboard core+glow**: dua quad menghadap kamera. Inti kecil putih-terang, glow besar berwarna. Sedikit pulsasi skala (sin waktu).
- **Stretched tracer**: quad memanjang searah gerak, panjang ∝ kecepatan. Dipakai tracer ketiga turret MVP (lihat 5.3).
- **Ribbon trail** (`TrailRenderer`): simpan 8–16 posisi terakhir di client, render strip quad yang menghadap kamera dengan lebar dan alpha mengecil ke ekor.
- **Model GeckoLib**: (post-MVP) untuk proyektil bermodel seperti misil dan drone.

### 5.3 Tracer, Hologram, Casing, Decal (MVP)

- **Tracer hitscan**: server mengirim titik awal dan akhir; client memutar "peluru visual" berupa stretched quad bercahaya (inti putih + glow warna energi) yang bergerak dari muzzle ke titik kena dalam 2–4 tick, dengan ekor ribbon pendek. Saat tiba, memicu efek impact.
- **Hologram**: quad additive bertekstur dengan scanline tipis dan flicker halus. Dipakai untuk reticle lock-on, angka ammo, cincin status di base.
- **Casing ejection**: partikel bermodel (model kecil sendiri, bukan sprite) yang terlempar dengan rotasi, gravitasi, memantul sekali, berpendar lalu memudar. Pool maksimal per turret.
- **Decal impact**: quad tipis menempel di permukaan block yang kena, bercahaya lalu mendingin (warna energi → gelap) dan hilang dalam 3–5 dtk. Pool maksimal global.
- **Beam renderer** (strip quad scroll dua lapis) masuk post-MVP bersama Laser/Rail.

### 5.4 Particle Types

Semua sprite original, digenerate `tools/gen_textures.py` (atau digambar manual) ke `textures/particle/`.

| Particle | Perilaku | Dipakai di |
|---|---|---|
| `spark` | streak memanjang searah velocity, gravitasi, memantul sekali di block, warna panas→dingin | impact, damaged, overheat |
| `ember` | titik bercahaya kecil, melayang naik, berkedip | impact kuat, turret hancur |
| `smoke_puff` | soft particle, mengembang, rotasi lambat, alpha turun | overheat, turret hancur |
| `heat_haze` | sprite distorsi semi-transparan yang naik | laras panas |
| `shockwave_ring` | ring datar yang mengembang cepat lalu memudar | turret hancur, upgrade tier |
| `muzzle_flash` | sprite sheet 3–4 frame, hidup 2–3 tick, additive, diwarnai warna energi | semua turret |
| `debris` | serpihan kecil berputar dengan gravitasi | impact keras, turret hancur |
| `muzzle_ring` | cincin energi tipis yang mengembang dari muzzle searah tembakan | semua turret, besar di Shotgun |
| `impact_splash` | percikan energi berbentuk mahkota kecil di titik kena | semua turret |
| `smoke_wisp` | asap tipis memanjang yang naik dari laras panas | Machine Gun, Shotgun |

Post-MVP: `energy_mote`, `plasma_arc`, `flak_burst`.

Setiap particle mendukung parameter: warna (ARGB), skala, umur, dan kurva (`EASE_OUT`, `PULSE`, dll.) lewat `ParticleOptions` custom dengan codec.

### 5.5 Dynamic Light

`DynamicLightManager` (client-side):
- Sumber cahaya sementara: `addLight(pos, level, ticks, falloff)`, misalnya muzzle flash Gun (level 10, 2 tick), Machine Gun (level 9, satu sumber yang diperbarui selama menembak), Shotgun (level 13, 3 tick).
- Implementasi: mixin pada perhitungan lightmap block di client (`LevelRenderer#getLightColor`) untuk mengambil nilai maksimum antara cahaya asli dan cahaya dinamis, lalu tandai chunk section yang terdampak untuk re-render.
- Budget: maksimal `maxDynamicLights` aktif (default 24) dan maksimal update section per frame. Sumber terlama/terlemah dibuang duluan.
- Jika mod **LambDynamicLights**/**Ryoamic Lights** terdeteksi, gunakan API mereka dan matikan implementasi sendiri.
- Config: `dynamicLights = true/false`.
- Fallback murah selalu aktif: efek glow additive sudah memberi kesan cahaya meski dynamic light dimatikan.

### 5.6 Screen Effects

- `CameraShake`: lewat `ViewportEvent.ComputeCameraAngles`. Intensitas berkurang sesuai jarak pemain ke sumber, trauma model (`shake = trauma²`, decay per tick). MVP: Shotgun (radius 8) dan kehancuran turret.
- `ScreenFlash`: overlay singkat saat turret hancur sangat dekat (< 6 block). Dipakai lebih banyak oleh senjata ledakan post-MVP.
- Semua bisa dimatikan di client config (aksesibilitas).

### 5.7 Audio

- Semua `SoundEvent` milik `bastion`, dengan variasi pitch ±8% dan 2–3 varian file per suara agar tidak monoton.
- Kategori suara per senjata: `charge`, `fire`, `fire_tail` (gema di kejauhan), `impact`, `overheat`, `deploy`, `idle_hum` (loop).
- Suara jauh: di atas 32 block, putar `fire_tail` (versi teredam) alih-alih `fire`.
- `tools/gen_sounds.py` membuat **placeholder** hasil sintesis (noise + envelope + filter) agar mod bisa jalan dari awal. Untuk rilis, ganti dengan SFX berkualitas: buatan sendiri atau dari sumber berlisensi CC0, dan catat kreditnya di README.

### 5.8 Preset Kualitas VFX

Client config `vfxQuality = LOW | MEDIUM | HIGH`:

| | LOW | MEDIUM | HIGH |
|---|---|---|---|
| Multiplier jumlah partikel | 0.3 | 0.6 | 1.0 |
| Panjang trail | 4 | 8 | 16 |
| Dynamic light | off | on (12 maks) | on (24 maks) |
| Soft particle shader | off | on | on |

---

## 6. Animasi: Pipeline GeckoLib + Blockbench

### 6.1 Struktur render

- `TurretBaseBlockEntity` implements `GeoBlockEntity`: animasi base (`idle`, `damaged`, `destroyed`, `tier_up`).
- `TurretBaseRenderer` (`GeoBlockRenderer`): render base, lalu ambil transform bone `weapon_mount` dan render model weapon di sana.
- Model weapon di dunia dirender lewat `WeaponAnimatable`: objek client-side per turret yang implements `GeoAnimatable`, punya controller sendiri, dan menyimpan state animasi weapon. Dibuat saat weapon terpasang, dibuang saat dicabut.
- Weapon di tangan pemain: `GeoItemRenderer` dengan model yang sama.

### 6.2 Konvensi bone (WAJIB untuk semua model weapon)

| Bone | Fungsi |
|---|---|
| `root` | akar model |
| `yaw_pivot` | diputar procedural di sumbu Y oleh kode (tidak boleh dianimasikan keyframe) |
| `pitch_pivot` | anak `yaw_pivot`, diputar procedural di sumbu X |
| `body` | bagian utama di bawah `pitch_pivot`, boleh dianimasikan keyframe (recoil body) |
| `barrel_0`, `barrel_1`, … | laras, dianimasikan keyframe (recoil laras) |
| `barrel_cluster` | (Machine Gun) induk semua laras rotary, diputar procedural sesuai spin |
| `sensor` | sensor/optik tempat hologram reticle muncul |
| `muzzle_0`, `muzzle_1`, … | locator kosong di ujung laras: titik spawn proyektil dan VFX |
| `eject_0` | titik keluar casing (jika ada) |
| `vent_*` | panel ventilasi yang terbuka saat overheat |
| `glow_*` | bagian yang memakai tekstur emissive |

Base memakai bone `weapon_mount`, `armor_t2`, `fins_t3`, `status_light`.

Posisi dunia `muzzle_N` diambil di client dari GeckoLib (aktifkan tracking matrix pada bone tersebut), dikirim ke `VfxManager` agar flash dan beam keluar tepat dari ujung laras, mengikuti recoil.

### 6.3 Daftar animasi per weapon

| Nama | Loop | Dipicu oleh |
|---|---|---|
| `idle` | loop | state IDLE, garis emissive berdenyut, bagian kecil bergerak halus |
| `deploy` | sekali | weapon dipasang |
| `undeploy` | sekali | weapon dicabut |
| `aim` | hold last frame | state AIMING (Gun: laras memanjang, sensor menyala) |
| `fire` | sekali | tiap tembakan (`triggerAnim` dari packet) |
| `pump` | sekali | (Shotgun) setelah tiap tembakan |
| `overheat` | sekali lalu hold | state OVERHEAT |
| `cooldown_vent` | sekali | keluar dari OVERHEAT |
| `no_ammo` | loop | state NO_AMMO (lampu kuning berkedip) |
| `held_idle` | loop | di tangan pemain |

### 6.4 Smoothness

- Rotasi yaw/pitch: interpolasi `partialTick` + sedikit lag (exponential smoothing, `alpha ≈ 0.35`) agar terasa punya massa.
- Recoil = kombinasi keyframe `fire` + **spring procedural** (`RecoilSpring`) pada `pitch_pivot` dan `barrel_N`: impuls saat menembak, kembali dengan damping. Ini yang membuat tembakan cepat beruntun tetap mulus.
- Transisi antar animasi memakai `transitionLength` 3–5 tick di controller.

### 6.5 Instruksi Blockbench MCP

- Format: **GeckoLib Animated Model**, tekstur 64×64 atau 128×128, pixel density konsisten antar model.
- Gaya: ikuti **7.5 Arah Desain Futuristik** (hard-surface sci-fi, garis emissive, elemen hologram). Desain original, bukan jiplakan karya pihak lain.
- **Revisi 2026-10-06 (keputusan user):** model base + senjata seragam gunmetal gelap dengan satu glow cyan; warna energi per senjata hanya di VFX. Base 1 blok penuh, senjata ~1 blok, base bisa dipasang di lantai, dinding, dan langit-langit.
- Setiap model: tekstur utama + tekstur `_e` (emissive) yang hanya berisi piksel menyala di atas latar transparan.
- Ekspor ke `assets/bastion/geo/<nama>.geo.json`, `animations/<nama>.animation.json`, `textures/...`.
- Animasi `fire` maksimal 0.5 detik dan harus bisa diputar ulang berulang (cancel) tanpa terlihat patah.

---

## 7. Spesifikasi Senjata (MVP: 3 turret)

MVP berisi tiga turret kinetik berteknologi futuristik. Semuanya **hitscan di server** (instan, akurat, ringan) dengan **tracer visual di client** yang terbang dari muzzle ke titik kena dalam 2–4 tick. Hasilnya tetap terlihat seperti proyektil sungguhan tanpa ratusan entity peluru, penting untuk Machine Gun yang menembak 12 kali per detik.

Nilai di bawah adalah titik awal, semuanya di JSON dan akan di-balance saat playtest.

> **Perubahan 2026-10-06 (keputusan user):** Gun, Machine Gun, Shotgun dan Sniper menembakkan **peluru dengan waktu terbang** (`weapon/Bullets`, tanpa entity): server menggerakkan tiap peluru `bullet_speed` block per tick dan men-trace segmen itu seperti hitscan pendek, turret melakukan lead (`lead_accuracy` + Targeting AI), peluru hanya mengenai target valid dan menembus yang lain, berhenti di block. Client menerima tembakan sekali dan menerbangkan tracer sendiri; titik akhir peluru datang sebagai `BulletImpact`. Biaya server/network setara hitscan (diukur: 200 turret, MSPT dan FPS sama). Profil `HITSCAN_*` dihapus.

### Ringkasan

| Turret | Peran | Range | Kecepatan tembak | Damage | Ammo | Kemampuan khusus | Warna energi |
|---|---|---|---|---|---|---|---|
| Gun Turret | presisi jarak menengah-jauh | 32 | 1 tembakan / 0.6 dtk | 7 | Kinetic Rounds (1/tembakan) | **Precision Lock**: setelah mengunci target 1 dtk, tembakan pertama ×1.5 dan bonus headshot ×1.5 | cyan |
| Machine Gun Turret | suppression, banyak target | 24 | spin-up 0 → 12 tembakan/dtk | 2.5 | Kinetic Rounds (1 per 2 tembakan) | **Spin-up & Suppression**: target yang kena mendapat Slowness I 1 dtk; spread melebar saat panas | amber |
| Shotgun Turret | pertahanan jarak dekat | 12 | 1 tembakan / 1.2 dtk | 8 pelet × 2.5 | Scatter Shells (1/tembakan) | **Kinetic Blast**: knockback kuat, damage pelet turun linear setelah 6 block | magenta |

Semua turret memakai `WeaponType` + `FireProfile` di 4.3. Profil MVP: `HITSCAN_SINGLE` (Gun), `HITSCAN_SPINUP` (Machine Gun), `HITSCAN_SPREAD` (Shotgun). Profil lain (`PROJECTILE`, `CHARGED`, `AREA_PULSE`, `SPAWNER`, `BEAM_CONTINUOUS`) disiapkan sebagai enum tapi baru diimplementasikan post-MVP.

### 7.1 Gun Turret
- **Fire**: satu raycast akurat (spread 0.3°) dari `muzzle_0`. Target lock dimulai saat state AIMING; setelah 20 tick mengunci target yang sama, tembakan berikutnya mendapat bonus Precision Lock. Raycast diarahkan ke kepala target jika terlihat, kalau tidak ke badan.
- **VFX tembakan**: muzzle flash custom (sprite sheet 3 frame + `muzzle_ring` energi cyan tipis yang mengembang), tracer cyan terang dengan ekor pendek, `heat_haze` tipis setelah 3 tembakan beruntun, sel peluru kosong keluar dari `eject_0` (model casing futuristik kecil berpendar lalu memudar).
- **VFX lock-on**: hologram reticle berputar di atas sensor turret, mengecil dan berubah dari putih ke cyan saat lock penuh. Garis laser pembidik tipis (alpha rendah) dari sensor ke target selama AIMING.
- **VFX impact**: `spark` cyan, `impact_splash` energi kecil, decal bekas tembakan bercahaya di block yang mendingin dalam 3 dtk. Precision Lock hit: impact lebih besar + `muzzle_ring` di titik kena.
- **Animasi**: `idle` (sensor menyapu, panel kecil bernapas), `aim` (laras sedikit memanjang, sensor menyala), `fire` (recoil laras tajam + body mundur kecil, ventilasi samping berkedip), `deploy` (laras keluar dari housing).
- **Light**: muzzle flash level 10 selama 2 tick.

### 7.2 Machine Gun Turret
- **Fire**: laras rotary 3 atau 4 buah. Spin-up 1 dtk dari diam ke kecepatan penuh, fire rate naik linear 0 → 12 tembakan/dtk. Tiap tembakan dari laras yang sedang di posisi atas (`muzzle_0..3` bergantian sesuai rotasi). Spread 1.5° dingin, naik ke 4° saat heat > 70%. Spin-down 1.5 dtk setelah berhenti, dan bisa langsung menembak lagi jika target baru muncul sebelum laras berhenti.
- **Heat**: tiap tembakan +1.2 heat, dissipation 8/dtk, max 100. Overheat → animasi `overheat`, menembak berhenti 3 dtk.
- **VFX tembakan**: muzzle flash amber cepat berganti frame, tracer amber (setiap tembakan; di preset LOW hanya 1 dari 3 yang punya tracer), aliran casing keluar dari `eject_0`, laras berpendar oranye → merah sesuai heat (emissive dinamis lewat warna overlay), `heat_haze` saat heat > 50%, asap tipis `smoke_wisp` dari laras setelah berhenti menembak.
- **VFX impact**: `spark` amber kecil, `debris` dari block yang kena (warna diambil dari block yang terkena, tekstur debris sendiri), decal bercahaya kecil.
- **Animasi**: `spin` (bone `barrel_cluster` diputar procedural sesuai kecepatan spin, bukan keyframe), `fire` (getaran kecil body, diputar cepat), `overheat` (ventilasi membuka, kipas pendingin berputar), `cooldown_vent`.
- **Audio**: suara spin-up/spin-down terpisah dari suara tembakan; suara tembakan memakai loop yang pitch-nya naik dengan fire rate agar tidak terdengar "berulang".
- **Light**: muzzle flash level 9, diperbarui terus selama menembak (satu sumber cahaya, bukan satu per tembakan).

### 7.3 Shotgun Turret
- **Fire**: 8 raycast pelet dalam kerucut 12°, pola acak berbasis `seed` dari packet (client dan server menghasilkan pola sama). Damage per pelet turun linear dari 100% di 6 block ke 40% di 12 block. Knockback total diterapkan sekali per target, sebanding dengan jumlah pelet yang kena.
- **Pump**: animasi pump/reload mekanik di antara tembakan (1.2 dtk) yang sekaligus menjadi cooldown.
- **VFX tembakan**: muzzle flash lebar magenta, `muzzle_ring` besar searah tembakan (shockwave kecil berbentuk kerucut), 8 tracer pendek, cangkang shell besar keluar dari `eject_0` saat pump, kepulan `smoke_wisp` dari laras.
- **VFX impact**: tiap pelet `spark` magenta kecil; target yang kena banyak pelet mendapat `impact_splash` besar.
- **Animasi**: `fire` (recoil berat seluruh body dengan overshoot), `pump` (slide/panel bergerak mundur-maju, shell terlempar di tengah animasi), `deploy`.
- **Feel**: camera shake ringan jika pemain dalam 8 block.
- **Light**: muzzle flash level 13, 3 tick.

### 7.4 Ammo Items (MVP)

| Item | Untuk | Catatan |
|---|---|---|
| `kinetic_rounds` | Gun Turret, Machine Gun Turret | stack 64, MG memakai 1 per 2 tembakan |
| `scatter_shells` | Shotgun Turret | stack 32 |

Keduanya terdaftar di tag `bastion:ammo/kinetic` dan `bastion:ammo/scatter` agar modpack bisa menambah alternatif.

### 7.5 Arah Desain Futuristik

Semua model mengikuti satu bahasa visual supaya terasa satu keluarga:

- **Bentuk**: hard-surface sci-fi. Panel bersudut tegas dengan potongan chamfer, celah panel (panel gap) yang jelas, siluet ramping. Hindari bentuk kotak polos ala vanilla.
- **Material & warna**: panel utama putih keabuan matte, rangka dan sambungan graphite gelap, aksen logam kecil. Warna energi per turret: Gun **cyan**, Machine Gun **amber**, Shotgun **magenta**.
- **Garis emissive**: setiap model punya strip/garis cahaya di sepanjang panel (tekstur `_e`) dengan warna energi turret-nya. Garis ini berdenyut saat idle, menyala penuh saat menembak, dan berubah merah saat overheat atau HP rendah.
- **Hologram**: elemen semi-transparan additive, misalnya reticle bidik di atas sensor, angka ammo kecil di samping body, dan cincin status di base. Dirender dengan `ADDITIVE_GLOW`, ada efek scanline tipis lewat tekstur.
- **Base**: platform heksagonal berlapis dengan cincin energi di bagian atas yang berputar pelan. Cincin berwarna putih saat tanpa weapon, lalu mengambil warna energi weapon yang terpasang. Tier menambah lapisan armor dan sirip pendingin bercahaya.
- **Gerak**: mekanis presisi. Panel membuka dengan easing cepat lalu mengunci (sedikit overshoot), bukan gerak lembek.

### 7.6 Backlog Post-MVP

**Ditambahkan 2026-10-06 (permintaan user):** Sniper (hitscan charged + laser sight, menembus 2 target) dan Rocket Launcher (roket tanpa pemandu, entity proyektil sungguhan, lead target, ledakan hanya ke target sah, jarak minimum 4 blok). Missile homing tetap backlog. **Ditambahkan sore 2026-10-06:** Missile Launcher di Large Turret Base 2x2 (homing, salvo); Tesla Coil (base 2x2, Forge Energy, petir custom ke 2 target acak, tidak berputar) dan Flamethrower (base 1x1, Fuel Canister, kerucut api, efek burn custom tanpa api vanilla); Laser Rifle (base 1x1, charge ~2.5 dtk dengan cahaya berkumpul di emitter, beam menembus semua target sah, berhenti di block, amunisi Laser Cell; Laser Cell kosong + station pengisi FE menyusul). **Module khusus weapon** (ModifierEffect `weapons`): Choke Module untuk Shotgun (spread -40%). **Rencana user (belum diputuskan):** sebagian weapon module punya tier Mk1/Mk2/Mk3; semua weapon saat ini = Mk1, bonus tiap tier belum ditentukan.

**Ditambahkan 2026-10-07 (keputusan user):** **Railgun** di Large Turret Base 2x2, peran anti-bos: 60 damage menembus armor, x2 ke `forge:bosses`, selalu membidik target dengan max HP terbesar, butuh 1 Rail Slug **dan** 8.000 FE per tembakan. Charge 3 dtk: glow model berubah ke warna efek (violet) dan pita kumparan menyala satu per satu dari pangkal ke moncong, lalu listrik merayap di seluruh laras seperti Tesla Coil; peluru 40 blok/tick; tumbukan memicu shockwave knockback radius 4 yang hanya mendorong target sah (tanpa damage tambahan). Model mengikuti referensi user: dua rel panjang (~4,6 blok dari poros) dengan celah bercahaya, drum magazine di samping, yoke di atas turntable. **Mortar** di Large Turret Base 2x2 (desain disetujui user 12:09, sudah dibuat): lintasan melengkung tanpa homing, laras selalu menghadap ke atas, hanya target di darat, bisa menembak melewati tembok; tumbukan menyisakan api custom 3x3 blok yang membakar **semua** yang berdiri di atasnya (burn custom, tetap terbakar 3 dtk setelah keluar), dan blok yang bisa terbakar di area itu diberi api vanilla (pengecualian aturan NO-VANILLA atas keputusan user). Model mengikuti foto referensi user: rumah berlapis baja berpaku keling di atas cincin putar, laras pendek gemuk.

Disimpan untuk setelah MVP rilis, memakai framework yang sama: **Laser** (beam kontinu dengan ramp-up), **Plasma Charge** (proyektil charged + ledakan), **Missile** (homing salvo), **Flak** (proximity fuse anti-udara), **Rail** (hitscan charged menembus), **EMP** (pulsa stun area), **Hive** (spawner drone). Komponen framework yang khusus untuk mereka (beam renderer, `energy_mote`, `plasma_arc`, `flak_burst`, ledakan custom, entity proyektil sungguhan) baru dibangun saat senjata itu dikerjakan.

---

## 8. Roadmap Fase & Acceptance Criteria

Kerjakan berurutan. Jangan lanjut sebelum acceptance criteria fase saat ini terpenuhi dan dites di game.

### Fase 0 — Scaffold
- Project Forge MDK 1.20.1 + Parchment + GeckoLib, mod id `bastion`, struktur package dari bagian 3.
- Registry kosong siap pakai, config common + client, packet channel.
- `tools/gen_textures.py` dan `tools/gen_sounds.py` versi awal.
- **Selesai jika**: `./gradlew build` dan `runClient` jalan, mod muncul di daftar mod, satu item uji muncul di creative tab `bastion`.

### Fase 1 — Base, Inventory, Otomasi
- `TurretBaseBlock` + BlockEntity + model base T1 futuristik (Blockbench) dengan animasi `idle` dan cincin energi.
- Inventory 3 bagian, `AmmoOnlyItemHandler`, menu + layar GUI dasar (tab Status saja).
- Item ammo `kinetic_rounds` dan `scatter_shells`.
- **Selesai jika**: hopper dan funnel/belt Create bisa memasukkan ammo ke base tapi tidak bisa memasukkan item lain atau menyentuh slot weapon/modifier; isi tersimpan setelah world reload; comparator membaca isi ammo.

### Fase 2 — Targeting, Rotasi, State Machine (Gun Turret)
- Weapon item Gun Turret (model final atau placeholder dengan konvensi bone lengkap), pasang/cabut, animasi `deploy`.
- Targeting dasar (hostile saja), line of sight, rotasi server + interpolasi client, scanning sweep.
- State machine lengkap, raycast damage **tanpa visual** (debug line boleh sementara, dihapus di Fase 4).
- **Selesai jika**: turret melacak zombie yang bergerak dengan mulus, tidak menembak menembus tembok, berhenti saat ammo habis, state tersinkron ke client.

### Fase 3 — VFX Framework (bagian MVP)
- `BastionRenderTypes`, renderer tracer + ribbon trail, renderer hologram, semua particle MVP di 5.4, casing ejection, decal impact, `DynamicLightManager`, `CameraShake`, `ScreenFlash`, preset kualitas.
- Command debug `/bastion vfx <preset>` untuk memutar efek di depan pemain.
- **Selesai jika**: setiap particle dan komponen bisa dipanggil lewat command dan tampil tanpa aset vanilla; FPS stabil saat 200 partikel aktif.

### Fase 4 — Gun Turret Lengkap (vertical slice)
- Seluruh 7.1: lock-on hologram, tracer, impact, casing, Precision Lock, animasi, suara, light.
- **Selesai jika**: rekaman 10 detik Gun Turret melawan sekelompok mob terlihat "jadi" dan bisa dipamerkan. Ini patokan kualitas untuk dua turret berikutnya.

### Fase 5 — Machine Gun Turret Lengkap
- Seluruh 7.2: spin-up/spin-down procedural, heat + overheat, laras berpendar, aliran casing, suppression.
- **Selesai jika**: setara kualitas Fase 4; 5 Machine Gun Turret menembak bersamaan tanpa penurunan FPS melebihi budget.

### Fase 6 — Shotgun Turret Lengkap
- Seluruh 7.3: spread deterministik dari seed, falloff, knockback, pump + shell ejection, muzzle shockwave.
- **Selesai jika**: setara kualitas Fase 4; pola pelet di client cocok dengan hit di server.

### Fase 7 — Tier, HP, Modifier, GUI Lengkap
- Tier T1–T3 + upgrade kit + visual tier, HP + hitbox entity + repair + kehancuran, semua modifier, tab Targeting & Info, Turret Configurator, proteksi owner/trusted, redstone control.
- **Selesai jika**: semua fitur di bagian 4 berfungsi dan tersimpan setelah reload; filter targeting bekerja untuk player/mob/entity spesifik; modifier terlihat efeknya di tab Info.

### Fase 8 — Polish, Compat, Rilis MVP
- Resep crafting semua item, lang `en_us` + `id_id`.
- Compat opsional: Jade (tooltip HP/ammo/state), JEI, Create Ponder, Iris/Oculus (cek render types & fallback), LambDynamicLights.
- Profiling performa, final balancing, README + kredit.
- **Selesai jika**: checklist performa di bagian 9 lolos, tidak ada crash di server dedicated (`runServer`), tidak ada error di log saat load.


### Fase 9 — Survival: Workstations & Parts (permintaan user 2026-10-06)
Semua item turret dibuat lewat 5 station bertenaga **FE**; resep crafting table untuk turret, weapon, module, ammo dihapus (crafting table hanya membuat workstation). Item baru masuk dua tab creative sendiri: "Bastion: Workstations" (5 station + Creative Power Source) dan "Bastion: Parts" (semua part).

| Workstation | Ukuran (lebar x dalam x tinggi) | Mode | Membuat |
|---|---|---|---|
| Part Workstation | 2x1x2 | berwaktu | semua part |
| Part Assembler | 2x2x2 | berwaktu | turret base & weapon module dari part; memulihkan damaged base |
| Module Workstation | 1x1x2 | instan (pemain) + berwaktu (otomasi) | semua module, repair kit, upgrade kit, configurator |
| Ammo Workstation | 1x1x2 | instan + berwaktu | semua ammo, Fuel Canister, Empty Laser Cell |
| Charging Station | 1x1x1 | berwaktu | mengisi ammo berenergi dengan FE: Laser Cell (nanti Plasma Cell) |

- **Model**: bukan block polos; model GeckoLib (meja kerja dengan gantry, assembler dengan lengan robot, printer modul, mesin press ammo), animasi `idle`, `working` (loop saat memproses) dan `craft` (sekali saat item jadi), plus VFX percikan/cahaya dan suara.
- **GUI** full custom: tab kategori, daftar resep, preview 3D berputar, daftar bahan (punya/butuh), 6 slot input + 1 output, bar energi, progress, tombol Craft (station instan).
- **Proses berwaktu**: resep terpilih tetap aktif; selama bahan di slot input lengkap, output muat dan ada FE, station memproses (FE per tick) lalu mengeluarkan hasil. Bahan masuk lewat hopper/belt/arm (hanya bahan resep terpilih), hasil diambil dari output, dari blok mana pun.
- **Instan**: tombol Craft memakai bahan dari slot input lalu inventory pemain, FE dari station, hasil langsung ke pemain.
- **Part**: set sendiri per weapon/base (3 part masing-masing, Missile Launcher butuh 2 Missile Pod). Part adalah potongan model 3D weapon/base itu sendiri (bone tertentu), jadi itemnya tampil 3D.
- **Energi**: kapasitas & input maks station di config; waktu & FE tiap resep di JSON resep (`bastion:workstation`).
- **Creative Power Source** (creative saja): blok yang mengalirkan FE tak terbatas ke blok di sebelahnya, untuk tes tanpa mod generator.


### Fase 10 — Base Extender: banyak turret di satu base (keputusan user 2026-10-07) — **tidak direncanakan dulu**

> **Status: tidak direncanakan dulu** (user, 2026-10-07 14:31). Implementasinya lama (refactor inti base ke banyak mount) dan bisa jadi tidak perlu; user punya ide lain yang lebih singkat, salah satunya bisa jadi workaround extender yang jauh lebih sederhana. Spec di bawah disimpan untuk nanti; jangan dibangun sebelum user mengangkatnya lagi. Rencana implementasi belum dibuat.

Blok **Base Extender** dipasang di atas Turret Base standar dan memberi menara itu **5 mount senjata**: atas, kiri, kanan, depan, belakang. GUI berubah supaya semua turret di menara bisa dikelola sekaligus. Keputusan user: peluru per turret (1a), extender menambah +2 slot module (2a), hanya base standar 1x1 (3a; untuk base 2x2 akan ada item utility lain, terpisah, belum didesain), base minimal **T2**.

**Pemasangan dan bentuk**
- Extender adalah blok 1x1 yang ditaruh di sisi mount Turret Base (sisi tempat senjata berada). Ditolak dengan pesan jika base T1, base besar 2x2, atau sudah ada extender. Senjata yang sudah terpasang di base pindah ke mount atas.
- 5 mount: **atas** (searah facing base) dan **4 sisi** tegak lurus facing base. Base di lantai: atas = ke atas, sisi = utara/timur/selatan/barat. Base di dinding/plafon: semuanya ikut arah base.
- Hanya weapon module kecil (bukan Missile Launcher, Tesla Coil, Railgun, Mortar). Senjata di sisi berputar dan membidik seperti turret dinding yang menghadap keluar dari sisi itu (frame mount yang sama dengan pemasangan dinding/plafon).
- Model extender: hub GeckoLib (gunmetal + glow cyan) dengan 5 turntable/weapon_mount, bisa dibuat lewat `tools/blockout`.

**Per mount**
- 1 slot senjata dan slot peluru sendiri. Mount atas memakai slot base yang sudah ada (peluru sesuai tier: T2 6 slot, T3 9 slot); tiap mount samping punya **3 slot peluru**. Tiap senjata hanya memakai peluru di mount-nya sendiri.
- Filter target sendiri (kategori, allow/block per mob dan pemain, mode pemain, trusted, prioritas) dan tombol **ON/OFF** sendiri.
- State machine, rotasi, target, heat, charge, lock dan state senjata (spin, tabung roket) masing-masing.

**Bersama untuk seluruh menara**
- **Module**: slot module base (T2: 2, T3: 4) + **2 slot extender**, berlaku ke semua senjata di menara; module khusus senjata (Choke, Autoloader) hanya ke senjata yang cocok. Aturan satu module per jenis berlaku untuk seluruh menara.
- **HP**: satu pool HP base (tier base). Hitbox jadi setinggi base + extender. Hancur: semua senjata, peluru, module dan extender jatuh, base turun satu tier seperti biasa.
- **Redstone**: menyalakan/mematikan seluruh menara. **Comparator**: isi peluru total semua mount. **Turret Configurator**: menyalin filter mount atas, menempel ke semua mount menara tujuan.
- **Otomasi**: hopper/belt/funnel/arm boleh menyisipkan dari blok mana pun (base atau extender). Peluru masuk ke mount pertama yang senjatanya cocok dan masih muat; peluru yang tidak cocok ke mount mana pun ditolak. Pengambilan (extract) dari slot peluru tetap boleh.
- **Bongkar**: menghancurkan extender menjatuhkan extender, isi 4 mount samping, dan 2 module extender; senjata dan peluru mount atas tetap di base (itu slot base sendiri) dan senjatanya kembali tampil di atas base. Menghancurkan base ikut menjatuhkan extender beserta isinya; base tetap shulker-style untuk isinya sendiri (tier, senjata + peluru mount atas, module base).

**GUI**
- Tanpa extender: GUI sama seperti sekarang.
- Dengan extender: kolom **pilihan mount** (5 tombol: ikon senjata + lampu state) dan tombol **Menara**.
- Halaman **Menara**: 5 baris berisi senjata, lampu state, bar peluru (jumlah + jenis) dan ON/OFF per mount. Ini tempat mengatur pemakaian peluru.
- Memilih satu mount: tab Status (slot senjata + slot peluru mount itu, slot module bersama selalu tampil), Targeting (filter mount itu, tombol **"Salin targeting ke semua mount"**), Info (stat senjata mount itu setelah module).
- Semua teks lewat `fit()`, tidak ada teks bertumpuk; ukuran GUI boleh melebar jika perlu.

**Survival**
- Resep mahal: 3 part baru di Part Workstation (**Extender Frame, Servo Hub, Ammo Feeder**) dengan bahan seperti diamond dan netherite scrap, dirakit di Part Assembler. Angka di `tools/gen_recipes.py`.
- Lang en_us + id_id, creative tab Turret Bases.

**Teknis**
- Refactor: semua state per senjata pindah dari `TurretBaseBlockEntity` ke kelas baru **`TurretMount`**; base menyimpan daftar mount (1 tanpa extender, 5 dengan extender) dan state tingkat menara (tier, HP, owner, module, redstone, hitbox, energi untuk senjata besar). Base tanpa extender harus berperilaku **persis sama** dengan sekarang.
- Inventory dan NBT per mount; save lama dibaca sebagai mount 0 (world lama aman).
- Packet `TurretStateSync`, `TurretFireEvent`, `SpinSync`, `RackSync`, `TurretConfigUpdate` membawa nomor mount; `PROTOCOL` naik.
- Client: `ClientTurret` per (posisi, mount); renderer menggambar senjata tiap mount di titik mount extender dengan frame arahnya sendiri.
- Blok extender meneruskan klik, kapabilitas item (otomasi) dan damage ke base di bawahnya (pola yang sama dengan part blok Large Turret Base).

**Selesai jika**
- 65 GameTest lama tetap lolos tanpa diubah (perilaku base tanpa extender tidak berubah).
- GameTest baru: extender ditolak di base T1; senjata samping menembak target di sisinya; peluru dipakai per mount (senjata utara tidak memakai peluru senjata selatan); filter per mount (mount yang memblokir husk tidak menembak husk, mount lain menembak); module dari slot extender berlaku ke semua senjata; hopper mengisi mount yang senjatanya cocok; membongkar extender menjatuhkan isinya; simpan + muat ulang mempertahankan semua mount.
- `./gradlew build` lolos, checklist di `docs/TESTING.md`, cek visual lewat scene showcase.

**Di luar cakupan Fase 10**: extender/utility untuk base 2x2 (ide user, belum didesain), senjata besar di extender, menumpuk extender di atas extender.


### Feed Hub (permintaan user 2026-10-07, pengganti sederhana Base Extender)

Blok **Feed Hub**: turret base standar 1x1 dipasang di sisi-sisinya (punggung base menempel di hub: atas, 4 samping, bawah). Peluru yang masuk dari pipa, belt, funnel, hopper atau Mechanical Arm diteruskan ke turret yang senjatanya cocok. Keputusan user: nama Feed Hub (1b), pembagian **rata** (turret dengan stok paling sedikit diisi dulu, 2a), hub punya **buffer 9 slot** (3b).
- Hanya menerima peluru yang dipakai minimal satu turret bersenjata yang terpasang; sisanya ditolak (tidak menyumbat buffer).
- Tiap tick memindahkan sampai 8 item per blok dari buffer ke turret yang cocok dan paling sedikit stoknya; kalau semua penuh, peluru menunggu di buffer.
- Turret tanpa senjata dan base yang hanya bersebelahan (punggungnya tidak di hub) tidak diisi.
- Otomasi boleh menarik isi buffer; hub dihancurkan → isi jatuh.
- Resep Module Workstation (iron block, 2 hopper, 4 copper ingot, 2 redstone). Model hub tunggal: kubus gunmetal dengan port di tiap sisi dan cincin cyan yang menyala (overlay fullbright).

#### Multiblock + energi (keputusan user 2026-10-07 15:35, disetujui 15:47: 1a, 2b, 3a, 4a)

**Bentuk (1a)**
- Feed Hub yang bersebelahan menyatu otomatis menjadi struktur beralas persegi, lebar W 1..3 dan tinggi H ≤ W: 1x1x1, 2x2x1, 2x2x2, 3x3x1, 3x3x2, 3x3x3.
- Setiap kali hub dipasang atau dihancurkan, semua hub yang tersambung (6 arah) dibagi ulang 1 tick kemudian: mulai dari blok terendah (urut y, lalu x, lalu z) dicoba bentuk terbesar dulu (27, 18, 9, 8, 4, 1 blok) yang semua bloknya masih bebas. Sisa menjadi hub tunggal.
- Tiap blok menyimpan isinya sendiri, jadi pembagian ulang tidak memindahkan item. Satu blok dihancurkan → hanya 9 slot blok itu yang jatuh, FE blok itu hilang; sisanya menyusun ulang.
- Posisi blok di struktur disimpan di 3 properti blockstate (`x`, `y`, `z`: `alone`/`low`/`middle`/`high`). Server dan client membaca bentuk struktur dari situ, tanpa data tambahan di block entity.

**Isi dan energi (3a)**
- Tiap blok: 9 slot + **50.000 FE**. Struktur N blok: 9N slot, N × 50.000 FE.
- Otomasi dari blok mana pun melihat seluruh struktur: peluru masuk ke slot pertama yang muat, tarik (extract) boleh. Aturan peluru tetap: hanya yang dipakai minimal satu turret bersenjata yang terpasang di struktur.
- FE masuk dari kabel di sisi mana pun, **4.000 FE per tick per blok** (struktur N blok: N × 4.000 per tick). Hub tidak mengeluarkan FE ke kabel.
- Tiap tick FE dibagi ke turret energi yang terpasang (kapasitornya bisa diisi: Tesla Coil, Railgun): yang persentase isinya paling rendah dulu, tiap turret dibatasi `max_input` senjatanya.
- Peluru dipindahkan **8 item per tick per blok**, turret dengan stok paling sedikit dulu (seperti hub tunggal).
- Angka di config server: `feedHubEnergyCapacity` 50000, `feedHubMaxInput` 4000, `feedHubItemsPerTick` 8 (semua per blok).

**Turret yang terpasang**
- Base standar 1x1 di sisi luar mana pun (atas, samping, bawah), punggungnya menempel ke blok struktur.
- Large Turret Base 2x2 di atas struktur, jika keempat bloknya berdiri di atas blok struktur yang sama (jadi struktur minimal 2x2). Base 3x3 nanti memakai aturan yang sama.

**GUI (2b)**, dibuka dari blok mana pun, tab samping seperti GUI turret:
- **Ringkasan**: ukuran (mis. `3x3x2`, 18 blok), jumlah turret + ikon senjatanya (maks. 12), total per jenis peluru (maks. 8 jenis), bar energi + angka FE.
- **Storage**: grid 9 kolom × 4 baris yang bisa di-scroll (roda mouse atau scrollbar) + inventory pemain. Shift-klik dari inventory mengisi seluruh struktur, bukan hanya baris yang terlihat. Hub tunggal: 1 baris aktif, sisanya tampil terkunci.
- Semua teks lewat `fit()`, tidak ada teks bertumpuk.

**Tampilan (4a)**
- Hub tunggal tetap seperti sekarang (port + cincin).
- Struktur: panel gunmetal polos tanpa sambungan antar blok, rangka + garis glow cyan (fullbright) hanya di tepi luar tiap sisi struktur, seperti Create Item Vault. Dibuat dengan model multipart dari properti blockstate (tanpa kode render), digenerate script di `tools/`.

**Selesai jika**
- GameTest baru: terbentuk 2x2x1, 2x2x2 dan 3x3x3 (slot dan FE ikut naik); pecah lalu menyusun ulang; base 2x2 di atas mendapat peluru; Tesla di atas mendapat FE; menghancurkan satu blok hanya menjatuhkan isinya sendiri.
- 67 GameTest lama tetap lolos, termasuk 2 test Feed Hub tanpa diubah.
- `./gradlew build` lolos, checklist di `docs/TESTING.md`, cek visual lewat scene `-Pshowcase=feedhub`.

**Di luar cakupan**: comparator, FE keluar ke kabel, struktur lebih besar dari 3x3x3, rotasi/mirror template struktur (pembentukan ulang setelah dipasang memperbaikinya).


---
## 9. Aturan Kerja untuk Claude Code

### Umum
1. Baca bagian relevan dokumen ini sebelum menulis kode. Kerjakan hanya fase yang diminta.
2. Setiap selesai fase: `./gradlew build` harus lolos tanpa error, lalu tulis checklist test manual di game.
3. Jangan pernah memakai aset atau efek vanilla yang dilarang di bagian 1. Jika butuh efek yang belum ada di framework, **tambahkan ke framework**, jangan pakai vanilla sebagai jalan pintas.
4. Jangan menyalin kode dari mod berlisensi GPL. Tulis implementasi sendiri.
5. Pisahkan kode client dan server dengan ketat: semua yang menyentuh `net.minecraft.client` hanya di package `client` dan dipanggil lewat `DistExecutor`/event client. Mod harus jalan di dedicated server.
6. Semua angka balance di JSON atau config, bukan hardcode.
7. Jika ada ambiguitas desain, tanyakan sebelum mengimplementasikan; jangan menebak hal yang mengubah gameplay.

### Aset
- Tekstur partikel/beam/GUI: generate lewat `tools/gen_textures.py` (gradient, noise, glow falloff, sprite sheet) atau minta saya membuat lewat Blockbench. Simpan skrip generator di repo agar bisa diregenerasi.
- Model & animasi: lewat Blockbench MCP mengikuti konvensi bagian 6. Validasi bahwa semua bone wajib ada sebelum menulis renderer.
- Suara: placeholder dari `gen_sounds.py`; tandai di `sounds.json` dengan komentar di README bahwa harus diganti sebelum rilis.

### Budget performa (dicek di Fase 8)
| Item | Batas |
|---|---|
| Partikel per turret per detik (HIGH) | ≤ 120 |
| Total partikel bastion aktif | ≤ 1500 (sisanya dibuang paling tua) |
| Dynamic light aktif | ≤ 24 |
| Pencarian target | tiap 10 tick, bukan tiap tick |
| Raycast per turret per tembakan | 1 (Shotgun: 8) |
| Casing aktif per turret | ≤ 12 |
| Decal impact aktif (global) | ≤ 256 |
| Packet `TurretStateSync` | hanya saat berubah, maks 1 per 2 tick per turret |

### Testing checklist (diulang tiap fase yang relevan)
- Single player dan dedicated server (`runServer` + client terpisah).
- World reload: semua state, inventory, tier, HP, filter tersimpan.
- Otomasi: hopper vanilla, Create funnel/belt/chute (jika Create terpasang di dev env sebagai `runtimeOnly`).
- Turret dihancurkan saat sedang menembak: tidak ada crash, efek dibersihkan.
- Chunk unload saat turret menembak: tidak ada entity/efek yatim.
- Dengan dan tanpa shader pack (fase 8).
- Preset VFX LOW/MEDIUM/HIGH semuanya tampil benar.
