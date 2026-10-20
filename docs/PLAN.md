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
- HP 0 → turret hancur: ledakan custom (tanpa merusak block). **Weapon module dan amunisi hilang**, modul modifier drop, base drop sebagai "damaged base" yang **ingat tier-nya** (BlockEntityTag Tier); Part Assembler + Repair Kit memulihkannya ke tier itu (user 2026-10-07 23:42, sebelumnya: semua drop + base turun 1 tier). Menambang base sendiri tetap menyimpan semuanya (gaya shulker).
- Indikator HP (user 2026-10-07/08): glow **base saja** (senjata tetap cyan) cyan saat sehat, **kuning tetap** di bawah 50% HP, **merah berkedip cepat** (terang/redup, 0,5 dtk) di bawah 25%. Percikan custom mulai di bawah 50% (jarang) dan sering di bawah 25%. Area glow base diperbesar (celah jendela vertikal 2x3,4 px, cincin energi lebih tebal; Large Base: celah 12x2, cincin tidak berputar lagi karena batang lurusnya memotong badan saat berputar), dan teksel glow di tekstur base netral (abu gelap) supaya warna HP tidak tercampur cyan.
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

**Tier modul Mk1/Mk2/Mk3 (permintaan user 2026-10-08 13:35, desain disetujui 13:39):**
- Semua 10 modul upgrade (8 di tabel + Choke Module + Autoloader) punya Mk1, Mk2, Mk3. Weapon module tidak bertier. Nama "Mk" (bukan "Tier") supaya tidak tertukar dengan tier base T1–T3.
- **Item terpisah per tier**: Mk1 = id lama (`range_module`, world lama aman), Mk2 = `<id>_mk2`, Mk3 = `<id>_mk3`. Nama Mk1 tetap ("Range Module"), Mk2/Mk3 diberi akhiran ("Range Module Mk2"); tooltip baris pertama "Mk N".
- **Kekuatan**: setiap angka efek Mk1 dikali **1,5 (Mk2)** dan **2 (Mk3)**, termasuk efek negatif (Overclock HP −15% → −22,5% → −30%). Pengecualian **Penetrator**: tembus +1 / +2 / +3. Daftar `weapons` (Choke, Autoloader) sama untuk semua tier.
- **Data**: satu JSON per item di `data/bastion/turret_modifiers/` (`range_module_mk2.json`, ...), dibuat oleh `tools/gen_modifier_tiers.py` dari JSON Mk1 (angka bisa diubah per tier lewat datapack).
- **Didapat dengan upgrade** di Module Workstation (instan untuk pemain, berwaktu untuk otomasi, butuh FE): Mk1 + 2 diamond + 4 redstone → Mk2; Mk2 + 1 netherite scrap + 2 diamond + 1 amethyst shard → Mk3 (`tools/gen_recipes.py`).
- **Satu per jenis**: modul sejenis dihitung sekali per base apa pun tier-nya (Range Mk1 dan Range Mk3 tidak bisa bersama).
- **Tekstur**: chip yang sama dengan tanda tier: bingkai berwarna (Mk1 seperti sekarang, Mk2 cyan, Mk3 emas) dan 1–3 titik di sudut.
- **Acceptance**: GameTest: angka Mk2/Mk3 = Mk1 × 1,5 / × 2 untuk setiap modul (Penetrator 1/2/3); Range Mk1 + Range Mk3 ditolak di satu base; upgrade di Module Workstation mengubah Mk1 + bahan menjadi Mk2. Semua GameTest lama lolos; tekstur dicek visual.

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

- `CameraShake`: lewat `ViewportEvent.ComputeCameraAngles`. Intensitas berkurang sesuai jarak pemain ke sumber, trauma model (`shake = trauma²`, decay per tick). Beberapa sumber **tidak menumpuk**: trauma = yang terkuat (user 2026-10-07: banyak turret dekat pemain terlalu geter). MVP: Shotgun (radius 8) dan kehancuran turret.
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

Disimpan untuk setelah MVP rilis, memakai framework yang sama: **Laser** (beam kontinu dengan ramp-up), **Plasma Charge** (proyektil charged + ledakan; sekarang di-spec sebagai Plasma Repeater + Plasma Cannon, bagian 8 "Ion Rifle, Plasma Repeater & Plasma Cannon"), **Missile** (homing salvo), **Flak** (proximity fuse anti-udara), **Rail** (hitscan charged menembus), **EMP** (pulsa stun area), **Hive** (spawner drone). Komponen framework yang khusus untuk mereka (beam renderer, `energy_mote`, `plasma_arc`, `flak_burst`, ledakan custom, entity proyektil sungguhan) baru dibangun saat senjata itu dikerjakan.

**Dukungan addon "turret pack" (dicatat 2026-10-07 23:09, permintaan user, dikerjakan setelah rilis, sekitar 1.0.0; belum didesain):** pembuat addon cukup membuat desain (model Bedrock/GeckoLib + tekstur), animasi, suara, dan JSON berisi daftar variabel; tanpa Java. Contoh acuan: gun pack TaCZ (zip atau folder di `.minecraft/tacz`; per senjata file index, data, display, model, animasi, tekstur, suara OGG). Yang perlu dibangun: (1) satu item module generik yang dibuat dari isi pack, menggantikan pendaftaran Java per senjata; (2) path model/animasi/tekstur memakai namespace addon, bukan `bastion`; (3) file *display* JSON untuk efek (muzzle flash, tracer, warna, suara, model proyektil) yang sekarang ada di Java (VfxPresets); (4) folder pack `.minecraft/bastion/` + template + satu contoh addon + dokumen daftar variabel; (5) proyektil: addon memilih jenis yang sudah ada (peluru, roket, misil homing, peluru mortar) dan mengatur angka serta modelnya; perilaku baru tetap lewat addon Java. Format pack diberi nomor versi dan dijaga kompatibel. Sebelum mulai: ringkasan desain dalam bahasa Indonesia untuk disetujui user.

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


### Repulsor & Repulsor Dome (permintaan user 2026-10-07, disetujui 18:36)

**Latar belakang**: banyak turret tidak bisa menembak mob yang sudah menempel atau ada di belakang larasnya; turret itu diam saja saat diserang dari dekat. Dua weapon module baru mendorong mob menjauh supaya turret penembak bisa bekerja lagi. Keputusan user: nama Repulsor / Repulsor Dome (1a), biaya FE (2a), tanpa damage (3a), interval 1,5 dtk / 4 dtk dengan charge 0,5 dtk (4a), pendekatan A (weapon module di base yang ada, bukan blok mandiri).

**Bersama**
- Weapon module biasa: filter target, ON/OFF, redstone, tier/HP, module, GUI, comparator, dan suplai FE dari Feed Hub berlaku seperti turret lain. Hanya **target sah** yang didorong (pemain, peliharaan, villager tidak, sesuai filter).
- **Tanpa damage.** Dorongan = kecepatan menjauh dari turret (horizontal + sedikit terangkat), dikalibrasi supaya mob terlempar sekitar jarak `knockback` blok di tanah datar; jarak dorong **sama rata** di seluruh jangkauan (tanpa falloff). Mob tahan knockback (atribut) terdorong lebih pendek.
- **Mob berat** (user 19:45-19:47; config server `repulsorHeavyMobs`, bawaan iron golem, ravager, warden; bisa ditambah mob mod lain): **Repulsor 1x1 tidak bisa mendorongnya sama sekali** (dan tidak menembak mereka, supaya FE tidak terbuang); **Repulsor Dome mendorongnya 60 % lebih pendek** (`repulsorHeavyMobPush` 0,4), atribut knockback resistance mereka diabaikan.
- Biaya FE dari kapasitor base (seperti Tesla); Creative Ammo membayar. Tanpa FE cukup → state NO_AMMO, tidak menembak.
- Module: Range Module memperluas jangkauan, Rapid Cycler mempercepat interval; Damage Amplifier tidak berpengaruh (tab Info: damage 0). Prioritas target dari GUI.
- Semua angka di JSON senjata (`data/bastion/turret_weapons/repulsor.json`, `repulsor_dome.json`), profil `AREA_PULSE`.

**Repulsor** (modul kecil, base 1x1; lantai, dinding, plafon)
- Berputar ke target (12°/tick), tanpa batas pitch (-90..90): bisa membidik mob tepat di kakinya.
- Charge 10 tick, lalu gelombang kerucut `cone_angle` 35° dari poros turret sejauh `range` **5** blok (user 19:45, dulu 3): **semua** target sah di kerucut yang terlihat (blok menghalangi) terdorong `knockback` **4** blok menjauh dari turret, dalam **satu** dorongan. (Diukur dari poros, bukan mulut corong: dari mulut corong, mob yang berdesakan di samping turret jatuh di luar kerucut.) Arah dorongan dihitung dari titik base menempel, jadi Repulsor di dinding mendorong mob menjauhi dinding, bukan ke dalamnya.
- 500 FE per dorongan, `fire_interval` 30 tick. Kapasitor 10.000 FE, input 500 FE/t.

**Repulsor Dome** (modul besar, Large Turret Base 2x2)
- Tidak berputar (seperti Tesla). Aktif bila ada target sah dalam `range` **7** blok dari tengah base (user 19:45, dulu 6).
- Charge 10 tick, lalu kubah: **semua** target sah dalam **9** blok (user 2026-10-08, dulu 7) terdorong keluar dari tengah base: `knockback` **6** blok di tepi jangkauan, **+1 blok tiap blok lebih dekat** (user 2026-10-18 12:13, rumus "+1 per blok" dipilih 12:15): `knockback + (range − jarak)`, jarak 3D dari bola kubah (titik yang sama dengan jangkauan), jadi 15 tepat di tengah dan ~12,5 untuk mob yang menempel base 2x2. Modul range ikut menaikkan maksimumnya. (Dulu rata 6, sebelumnya 5.) **Menembus dinding** (medan, tanpa line of sight).
- 2.000 FE per dorongan, `fire_interval` 80 tick. Kapasitor 40.000 FE, input 2.000 FE/t.

**Model** (`tools/blockout/design_weapons.py`, gunmetal + satu glow cyan, konvensi bone bagian 6.2)
- Repulsor: corong emitter pendek-lebar di atas yoke, 3 cincin glow di dalam mulut corong (`glow_coil_0..2`, mekanisme nyala yang sama dengan Railgun), sirip ventilasi samping. Charge: cincin menyala satu per satu dari belakang ke depan; tembak: corong recoil mundur, cincin berkilat.
- Repulsor Dome: generator bulat pendek di turntable 2x2, bola inti glow di dalam sangkar 4 pilar melengkung, cincin horizontal yang berputar pelan mengelilingi bola. Charge: cincin berputar makin cepat, bola makin terang; tembak: bola berkilat, pilar terdorong sedikit keluar.

**VFX** (framework sendiri, warna efek putih kebiruan; tanpa partikel vanilla)
- Repulsor: 3–4 cincin transparan meluncur sepanjang kerucut sambil melebar (~0,3 dtk sampai ujung jangkauan), riak udara (heat haze) di kerucut, kilatan di moncong, cahaya dinamis singkat; tiap mob yang terdorong: kepulan debu di kaki + jejak gerak singkat.
- Repulsor Dome (referensi user: bola putih "almighty push" + cincin debu di tanah): bola putih transparan membesar dari tengah sampai ujung jangkauan (9 blok) dalam ~8 tick, tepi paling terang, lalu memudar; cincin awan debu menggelinding di tanah di tepi kubah; garis-garis kecepatan radial sesaat; camera shake untuk pemain dalam ~10 blok, kilatan putih singkat, cahaya dinamis.
- Charge: cahaya mengumpul di emitter/inti + dengung naik. Suara placeholder (`gen_sounds.py`): charge, dorongan, dentuman kubah.

**Survival**: part lewat Part Workstation, dirakit di Part Assembler (angka di `tools/gen_recipes.py`). Repulsor (part kecil): Repulsor Emitter (iron, copper, piston), Repulsor Coil (copper, redstone), Repulsor Mount (iron, observer). Repulsor Dome (part besar): Dome Core (diamond, redstone block, 2 piston), Dome Pylons (iron block, copper block), Dome Mount (iron, copper, observer). Lang en_us + id_id, tooltip, creative tab Weapon Modules.

**Selesai jika**
- GameTest Repulsor: husk di depan terdorong ≥ 2,5 blok; dua husk di kerucut sama-sama terdorong; sapi tidak; husk di balik dinding tidak; tanpa FE tidak menembak; tidak ada damage.
- GameTest Repulsor Dome: husk di 4 sisi terdorong keluar ≥ 4 blok, juga yang di balik dinding; sapi tidak; tanpa damage; butuh FE.
- 83 GameTest lama tetap lolos; `./gradlew build` lolos; scene `-Pshowcase=repulsor` dicek (charge, gelombang, kubah, siang/malam); checklist di `docs/TESTING.md`.

**Di luar cakupan**: damage atau efek status tambahan (slow, stun), dorongan ke atas/tarik ke dalam, module khusus Repulsor.

### Hologram penempatan multiblock (permintaan user 2026-10-08 00:44, desain B disetujui 00:46)

**Tujuan**: sebelum memasang blok multiblock, pemain melihat persis blok mana saja yang akan terisi dan bentuk jadinya, tanpa menebak.

**Item**: blok yang lebih dari 1 blok: Large Turret Base (2x2x1), Part Workstation (2x1x2), Part Assembler (2x2x2), Module Workstation (1x1x2), Ammo Workstation (1x1x2). Blok 1x1 (Turret Base, Charging Station, Feed Hub) tidak memakai hologram; outline vanilla sudah cukup.

**Kapan muncul**: item dipegang di tangan utama (atau tangan kedua bila tangan utama bukan item multiblock), crosshair mengenai blok dalam jangkauan, pemain bukan spectator, tidak ada GUI terbuka. Large Turret Base hanya bisa dipasang di permukaan atas blok: saat crosshair di sisi lain, tidak ada hologram.

**Tampilan** (fullbright, diuji kedalaman sehingga tidak tembus dinding, tidak menulis kedalaman sehingga tidak menutupi blok lain):
1. **Model hantu**: model GeckoLib blok itu, di posisi dan arah persis hasil pasang (tengah footprint, arah depan workstation = menghadap pemain), pose diam, tier dari item (base T3 dari creative tampil dengan armor + sirip T3). Tekstur asli, transparan ~40%, diwarnai cyan hologram (`MODEL_GLOW`); merah bila tidak bisa dipasang.
2. **Kotak per blok**: isi transparan ~15% dan garis tepi ~60%; cyan untuk blok yang bebas, **merah untuk blok yang menghalangi**, jadi pemain tahu blok mana yang harus disingkirkan.

**Satu sumber kebenaran**: blok `LargeTurretBaseBlock` dan `WorkstationBlock` mendapat satu fungsi penempatan (`Multiblock.footprint(BlockPlaceContext)`: daftar blok, blok yang terhalang, arah depan) yang dipakai **baik** oleh `getStateForPlacement` maupun hologram. Hologram tidak bisa berbeda dari hasil pasang.

**Performa**: satu hologram (milik pemain sendiri), dihitung per frame dari state yang sudah ada; tidak ada packet, tidak ada state server.

**Acceptance**:
- GameTest: untuk Large Turret Base dan Part Assembler, blok dari `footprint()` sama persis dengan blok yang terisi setelah item dipakai oleh pemain palsu; blok penghalang tercantum di `blocked` dan pemasangan gagal.
- Scene dev `-Pshowcase=placement`: screenshot hologram bebas (cyan) dan terhalang (merah) untuk Large Base T3 dan Part Assembler, siang dan malam.
- Semua GameTest lama lolos, `./gradlew build` lolos, checklist di `docs/TESTING.md`.

**Di luar cakupan**: hologram untuk blok 1x1, memutar arah sebelum memasang (arah tetap mengikuti pemain), hologram yang terlihat oleh pemain lain.

### Ion Rifle, Plasma Repeater & Plasma Cannon (permintaan user 2026-10-08 16:54, desain disetujui 17:09–17:11)

**Status**: selesai 2026-10-10 (plan `docs/superpowers/plans/2026-10-10-ion-plasma-turrets.md`): 128 GameTest lolos (setelah ledakan menggantikan cincin, 2026-10-10 06:15), scene `-Pshowcase=ion` dan `-Pshowcase=plasma` dicek siang/malam; belum dites user di game. User 2026-10-09 23:40 mencabut aturan "tidak ada turret baru sampai SFX placeholder diganti" (2026-10-07 23:37): SFX masih placeholder, diganti menyusul lewat `docs/SOUND_SHEET.md`. Urutan: Ion Rifle → Plasma Repeater (bersama Plasma Cell) → Plasma Cannon.

**Latar belakang**: user ingin turret plasma dan turret ion. Laser Rifle = garis cahaya instan yang menembus; plasma = bola panas yang terlihat terbang dan menyebar saat kena; ion = meniru Ion Rifle di Crimsonland (perilaku dibaca dari reimplementasi `banteg/crimson`: peluru cepat berjejak biru, berhenti di musuh pertama, lalu inti biru 0,25 dtk menarik sinar lurus ke **semua** musuh dalam radius dan memberi damage ke mereka). Keputusan user: sebaran Ion bentuk **bintang** dari satu titik, bukan pohon/rantai (17:00); plasma **dua-duanya**, Repeater 1x1 + Cannon 2x2; amunisi **Plasma Cell** untuk kedua plasma, **FE** untuk Ion; Repeater dengan **splash kecil**; warna efek plasma **hijau `#3DFF6E`** (awalnya toska `#3DFFB5`; user 2026-10-10 01:08: di layar terlihat cyan, harusnya hijau), ion **biru Crimsonland `#8099FF`**.

**Bersama**
- Weapon module biasa: filter target, ON/OFF, redstone, tier/HP, module, GUI, comparator, Feed Hub (ammo dan FE) berlaku seperti turret lain.
- Peluru/bola hanya mengenai **target sah**, menembus yang lain, berhenti di block (aturan peluru bagian 7). Damage area, sinar dan ledakan juga hanya ke target sah, tidak merusak block, tidak mengenai base sendiri.
- Semua peluru punya waktu terbang dan turret melakukan lead (`lead_accuracy`, Targeting AI berlaku).
- Jarak untuk radius (sinar Ion, splash Repeater) diukur dari titik kena ke kotak tubuh target terdekat, bukan ke titik tengahnya.
- Kunci kecepatan di JSON: `bolt_speed` / `orb_speed`, **bukan** `bullet_speed` / `slug_speed`, supaya Penetrator tidak tercantum sebagai berpengaruh (`ModifierEffect.affects`).
- Semua angka di `data/bastion/turret_weapons/ion_rifle.json`, `plasma_repeater.json`, `plasma_cannon.json`.

**Ion Rifle** (modul kecil, base 1x1; lantai, dinding, plafon; FE)
- Peluru ion `bolt_speed` 4 blok/tick, `lead_accuracy` 0,9. Berhenti di target sah pertama **atau** block.
- Di titik berhenti terbentuk **inti ion** selama `field_ticks` 6. Inti menarik sinar ke **setiap** target sah dalam `field_radius` 3 blok (bintang, satu lapis). Sinar **tidak butuh line of sight** (medan, seperti Repulsor Dome): peluru yang kena dinding tetap mengenai musuh di balik dinding tipis dalam radius.
- Damage: target yang kena langsung `damage` **8**; tiap target lain yang tersambung sinar `field_damage` **5**, **sekali** per tembakan (bukan per tick, supaya tidak terhalang i-frame).
- `range` 32, `fire_interval` 16 tick, `turn_speed` 8, `aim_tolerance` 1,5, pitch -35..90. Ammo `bastion:ammo/energy`, `ammo_per_shot` 0; `energy_per_shot` 1.200 FE, kapasitor 40.000 FE, input 1.000 FE/t (Creative Ammo membayar). Tanpa FE cukup → NO_AMMO.
- Module: Range, Damage Amplifier (langsung dan sinar), Overclock, Rapid Cycler, Targeting AI. Penetrator tidak berlaku (peluru selalu berhenti di target pertama); Ammo Recycler tidak berlaku (FE).
- Beda dengan Tesla: Tesla menyambar 5 target dalam 16 blok dari coil, instan; Ion menembak sampai 32 blok lalu mengenai satu gerombolan di titik kena.

**Plasma Repeater** (modul kecil, base 1x1; lantai, dinding, plafon; Plasma Cell)
- Bola plasma kecil `bolt_speed` 1,5 blok/tick (lambat dan terlihat terbang), `lead_accuracy` 0,9. Berhenti di target sah pertama atau block, lalu ledakan kecil: target yang kena `damage` **5**, target sah lain dalam `splash_radius` 1,2 blok `splash_damage` **3**. Bola yang kena block juga meledak.
- `range` 24, `fire_interval` 5 tick (4/dtk), `turn_speed` 10, `aim_tolerance` 2, pitch -35..90.
- Heat (sistem MG yang sudah ada): `heat_per_shot` 8, `heat_dissipation_per_second` 15, `max_heat` 100, `overheat_ticks` 60. Kira-kira 6 dtk menembak, lalu vent 3 dtk.
- Ammo `bastion:ammo/plasma`, `ammo_per_shot` 0,1 (10 tembakan per cell).
- Peran: single target lebih lemah dari MG (sekitar 20 vs 30 dmg/dtk), unggul di gerombolan rapat; bola lambat bisa meleset ke target cepat (disengaja).
- Module: Range, Damage Amplifier (langsung dan splash), Overclock, Rapid Cycler, Coolant Loop, Targeting AI, Ammo Recycler. Penetrator tidak berlaku.

**Plasma Cannon** (modul besar, Large Turret Base 2x2; Plasma Cell)
- Charge `charge_ticks` 20 (bola tumbuh di moncong), lalu bola besar `orb_speed` 0,8 blok/tick, `lead_accuracy` 0,9. Berhenti di target sah pertama atau block; target yang kena `damage` **18**.
- **Ledakan ala BFG 9000 Vic's Point Blank** (user 2026-10-10 06:01, ganti cincin 12 pecahan yang sering meleset ke depan; charge dan tembakan tetap sama, 06:06): di titik berhenti bola meledak dengan radius `blast_radius` **4** blok. Setiap target sah dalam radius (jarak ke kotak tubuh) kena `blast_damage` **14** di tengah, turun linear ke **7** di tepi; target yang kena langsung dapat 18 + ledakan. Tanpa line of sight, tanpa knockback, tidak merusak block, tidak mengenai non-target. Bola yang terbang sampai batas range tanpa kena apa-apa tidak meledak.
- `range` 40, `min_range` 4, `fire_interval` 30 + `charge_ticks` 20 = siklus 50 tick (state machine mengisi setelah cooldown), `turn_speed` 4, `aim_tolerance` 1,5, pitch -30..60. Ammo `bastion:ammo/plasma`, `ammo_per_shot` 1.
- Module: Range, Damage Amplifier (bola dan ledakan), Overclock, Rapid Cycler, Targeting AI, Ammo Recycler. Penetrator dan Autoloader tidak berlaku.

**Plasma Cell** (`plasma_cell`, `empty_plasma_cell`, stack 16 seperti Laser Cell, tag `bastion:ammo/plasma`)
- Empty Plasma Cell di Ammo Workstation: 2 iron ingot + 1 glass + 1 glowstone dust → 4 cell (40 tick, 400 FE).
- Diisi di Charging Station: 1 empty → 1 Plasma Cell, 80 tick, 4.000 FE (kategori `bastion:plasma_cell`).
- Turret tidak mengembalikan cell kosong (sama dengan Laser Cell).

**Model** (`tools/blockout/design_weapons.py`, gunmetal + satu glow cyan, konvensi bone bagian 6.2)
- Ion Rifle: laras ramping panjang dengan 3 cincin coil glow di dekat moncong (`glow_coil_0..2`), kapasitor kotak di belakang. Tembak: cincin berkilat dari belakang ke depan, recoil pendek.
- Plasma Repeater: laras pendek tebal dengan ruang penampung bulat di pangkal dan sirip vent di samping. Tembak: recoil cepat; heat tinggi: sirip menyala; vent: sirip membuka.
- Plasma Cannon (desain ulang user 2026-10-10 06:28, referensi Kromlech "Battle Tank Turret: Plasma Cannon"): turret tank lapis baja besar. Hull lebar di turntable 2x2 (glacis bertingkat, rok samping berpaku keling, bustle belakang dengan sirip vent, kubah palka, tabung coil glow di dek belakang, sensor di depan), hanya berputar kiri-kanan; laras coil tebal (rusuk gelap di atas inti glow `glow_coils`, tutup berat dengan satu lubang moncong bulat gelap (user 06:35: dua celah terlihat aneh)) naik-turun pada mantlet di depan hull (`pivot_height` 0,71875, `muzzle_length` 2,14375; user 06:38: dibuat lebih tinggi, hull di atas cincin turret). Palet tetap gunmetal + glow cyan (aturan model); glow jadi hijau saat charge. Tembak: laras recoil berat, coil berkilat.
- Animasi: `idle`, `aim`, `fire`, `deploy`; Repeater + `overheat`/`cooldown_vent`; Cannon + `charge`.

**VFX** (framework sendiri, tanpa partikel vanilla)
- Ion (`#8099FF`): kilatan biru di moncong; peluru = kepala putih kekuningan + jejak biru lurus ~6 blok yang memudar ke belakang; kena: cincin biru membesar + percikan biru, kepala mengecil jadi inti biru; sinar ke tiap target = strip lurus dua lapis (inti terang + pinggiran lembut, render `BEAM`) dengan glow kecil di ujung target; semua memudar dalam 6 tick. Bentuk lurus membedakannya dari petir zig-zag Tesla yang warnanya mirip.
- Plasma Repeater (`#3DFF6E`): bola = inti putih + halo hijau + jejak pendek ~1 blok; kena: semburan hijau kecil + percikan + decal hangus kecil (memudar ~2 dtk); laras berpendar hijau sesuai heat, uap + desis saat vent (pola MG).
- Plasma Cannon (`#3DFF6E`): charge = bola hijau tumbuh di moncong + partikel tersedot masuk; bola besar = inti putih + halo hijau berdenyut + jejak ~3 blok (charge dan tembakan tidak berubah). Ledakan (dari frame video BFG 9000): kilatan inti putih-kuning yang menyilaukan, awan bola api hijau bergulung sebesar radius (~1 dtk, putih di tengah lalu hijau lalu memudar gelap), bintik gelap, percikan dan bara hijau, gelombang hijau datar, cahaya kuat, camera shake ~12 blok, kilatan layar untuk yang dekat, bekas hangus hijau besar di tanah (juga saat kena mob: di tanah di bawahnya).
- SFX placeholder baru (8, `gen_sounds.py`, masuk sound sheet): `ion_fire`, `ion_hit`, `plasma_repeater_fire`, `plasma_hit_small`, `plasma_vent`, `plasma_cannon_charge`, `plasma_cannon_fire`, `plasma_cannon_hit` (`plasma_fragment` dihapus bersama cincin pecahan).

**Survival**: part lewat Part Workstation, dirakit di Part Assembler (angka di `tools/gen_recipes.py`). Ion Rifle (part kecil): Ion Barrel (iron, copper, amethyst shard), Ion Capacitor (copper, redstone, gold), Ion Mount (iron, observer). Plasma Repeater (part kecil): Plasma Barrel (iron, glass, glowstone dust), Plasma Chamber (copper, glowstone dust, blaze powder), Repeater Mount (iron, observer). Plasma Cannon (part besar): Cannon Core (diamond, glowstone block, blaze rod), Cannon Barrel (iron block, copper block), Cannon Mount (iron, copper, observer). Lang en_us + id_id, tooltip, creative tab Weapon Modules dan Ammo.

**Selesai jika**
- GameTest Ion: husk yang kena langsung -8; dua husk dalam 3 blok dari titik kena masing-masing -5; husk di 4 blok utuh; sapi dalam radius utuh; peluru yang kena block di dekat husk tetap membentuk inti (husk di balik dinding 1 blok dalam radius -5); tanpa FE tidak menembak; 1.200 FE per tembakan.
- GameTest Plasma Repeater: husk yang kena -5, husk 1 blok di sebelahnya -3, husk 2 blok utuh; sapi utuh; tidak ada block rusak; overheat setelah sekitar 6 dtk lalu berhenti 60 tick; 10 tembakan = 1 cell.
- GameTest Plasma Cannon: husk utama kena 18 + ledakan penuh; husk dan laba-laba dalam 4 blok kena ledakan (lebih kecil di tepi); husk di luar 4 blok utuh; sapi dalam radius utuh; bola yang kena dinding tetap meledak (husk dekat dinding, juga di baliknya, kena); 1 cell per tembakan; tidak menembak di bawah 4 blok; modul ditukar saat bola terbang tidak membuat crash.
- GameTest Plasma Cell: Ammo Workstation membuat 4 empty cell; Charging Station mengisi 1 cell (80 tick, 4.000 FE).
- 113 GameTest lama tetap lolos (juga dengan `-PwithCompatMods`; total jadi 128); `./gradlew build` lolos; scene `-Pshowcase=ion` dan `-Pshowcase=plasma` di `-PshowcaseWorld=showcase_fx` dicek (tembak, kena, sinar, splash, overheat, ledakan, siang/malam); checklist di `docs/TESTING.md`.

**Di luar cakupan**: sebaran Ion bentuk pohon/rantai dan perk Shock Chain, varian Ion Minigun/Ion Cannon, debuff Melt, cincin pecahan (diganti ledakan 2026-10-10), ledakan di udara saat bola habis range, awan plasma yang tertinggal, merusak block, efek status (stun, slow), cell kosong yang dikembalikan turret, Plasma Cell untuk Laser Rifle.

### Turret Control Hub (permintaan user 2026-10-10 11:25, desain disetujui 11:43)

**Status**: versi pertama (lapisan 3x3) selesai 2026-10-10 14:45 (plan `docs/superpowers/plans/2026-10-10-turret-control-hub.md`, 147 GameTest lolos), belum dites user di game. **Revisi user 15:01 (kubus 3x3x3 + Hub Connector) selesai 2026-10-10 (plan `docs/superpowers/plans/2026-10-10-control-hub-cube-connector.md`): 152 GameTest lolos (juga dengan `-PwithCompatMods`), scene `-Pshowcase=hub` dicek; belum dites user di game.**
Setelah dites user (16:56): GUI hub hampir memenuhi layar (ukuran panel dari ukuran layar, minimal 256x200; peta, Daftar dan Atur massal ikut membesar) dan semua teks satu ukuran (tidak ada baris yang dikecilkan sendiri-sendiri). Diubah saat implementasi revisi: tidak ada angka yang berubah; tooltip Hub Interface/Casing/Power Port/Satellite Uplink dan pesan "butuh Uplink Satelit" yang masih menyebut 3x3 ikut diganti ke kubus.
Diubah saat implementasi: model Hub Interface jadi satu kubus penuh dengan tekstur depan berlubang (garis glow tepi tidak z-fight lagi); petunjuk di tab Peta memakai huruf kecil (0,75) agar muat 4 baris; dari review akhir: hub OFFLINE baru ONLINE lagi setelah menyimpan `controlHubRestartTicks` (100) tick upkeep, karena dengan pemasukan FE di bawah upkeep hub berkedip ON/OFF tiap tick (5 detik dikonfirmasi user 15:10).

**Latar belakang**: user ingin mengawasi dan mengatur semua turret di base dari satu tempat. Hub dirakit manual dari beberapa jenis blok seperti smeltery Tinkers' Construct: tiap blok punya peran, struktur baru aktif saat semua blok ada di posisi yang benar, dan controller (di sini Interface) menunjukkan bagian yang kurang. Keputusan user:
- Jangkauan satelit T1 1 chunk, T2 3x3 chunk, T3 **7x7 = 49 chunk** (hub tepat di tengah; 6x6 = 36 tidak bisa di tengah) (11:30).
- Turret yang tampil: **yang boleh dibuka pemain itu** (aturan GUI turret, 4.7) (11:30).
- **Pakai FE**, lewat blok Power Port (11:30).
- Atur massal: **isi tab Targeting + nyala/mati** (11:30).
- Satellite Uplink **berdiri di atas blok tengah**: tiang 2 blok dengan dish yang berputar (11:39); sejak revisi: di atas blok tengah lapisan paling atas (15:05).
- Pendekatan A: GUI turret yang sudah ada dibuka dari jauh, bukan panel turret di dalam GUI hub (11:43).
- **Revisi 15:01**: struktur jadi **kubus 3x3x3**, Interface di **lapisan tengah** (sejajar mata pemain), satelit paling atas di tengah; turret baru terhubung ke hub bila dipasangi **Hub Connector** yang sudah di-link ke hub itu, di **slot baru** tiap turret.
- Satelit berdiri di atas kubus, tinggi total 5 blok (15:05). Jangkauan tier satelit **tetap berlaku** untuk turret ber-connector (15:05).
- Resep Hub Casing jadi 8 per craft; Hub Connector seperti dijelaskan di bawah (15:08). Restart setelah OFFLINE: 5 detik upkeep (15:10).

**Blok dan item** (7 item; tab kreatif utama "Bastion"; resep di Module Workstation)

| Blok | Ukuran | Peran |
|---|---|---|
| Hub Interface | 1x1 | Controller: memeriksa struktur, menyimpan FE, klik kanan membuka GUI hub. Saat dipasang menghadap pemain. |
| Hub Casing | 1x1 | Rangka kubus 3x3x3. |
| Hub Power Port | 1x1 | Seperti Hub Casing, plus menerima FE dari kabel di sisi mana pun dan meneruskannya ke Interface. |
| Satellite Uplink T1 / T2 / T3 | 1x1x2 | Tiang + dish berputar; tier menentukan jangkauan. Item terpisah per tier (`satellite_uplink_t1`, `_t2`, `_t3`). Dipasang seperti Module Workstation (`Multiblock.footprint`, hologram penempatan). |
| Hub Connector | item, tumpuk 16 | Di-link ke satu hub, dipasang di slot Connector turret; turret hanya tampil di hub yang di-link connector-nya. |

**Struktur**
```
Tampak samping (depan)              Tampak atas lapisan tengah
        ◠    dish berputar             [C][C][C]
        ┃    Satellite Uplink          [C][C][C]   (tengah = blok inti: Casing/Port)
    [C][C][C]  lapisan atas            [C][I][C]
    [C][I][C]  lapisan tengah              ↑ pemain
    [C][C][C]  lapisan bawah
```
- Kubus 3x3x3: Interface (I) di tengah salah satu sisi **lapisan tengah**, menghadap keluar, sejajar mata pemain yang berdiri di tanah; 26 posisi lain (C, termasuk blok inti di tengah kubus) diisi Hub Casing atau Hub Power Port, **minimal 1 Power Port**. Power Port hanya bisa diberi kabel di sisi luar kubus (port di blok inti tidak terjangkau kabel).
- Satellite Uplink berdiri di atas blok tengah lapisan atas dan mengisi 2 blok ke atas. Tinggi total struktur 5 blok.
- Letak kubus dibaca dari Interface: Interface di tengah sisi depan lapisan tengah, kubus memanjang 2 blok ke belakang, 1 blok ke bawah dan 1 blok ke atas (berlawanan dengan arah hadap Interface). Tidak dicari ke arah lain.
- Struktur diperiksa ulang 1 tick setelah blok di dalam kubus atau satelit di atasnya dipasang/dibongkar, dan saat chunk Interface dimuat. Bagian di chunk yang tidak termuat = belum lengkap (pemeriksaan tidak pernah memuat chunk).
- Satu blok hanya milik satu hub: blok yang sudah jadi bagian hub lain dihitung salah di hub kedua.
- Jadi (formed): Casing dan Power Port menyimpan letaknya di kubus di blockstate (`x`, `y`, `z`: `alone`/`low`/`middle`/`high`, pola Feed Hub 3x3x3; `alone` = belum jadi), dipakai model tepi dan untuk tahu hub pemiliknya; satelit `status`; Interface `status` = `unformed`/`online`/`offline`.
- Satu bagian dibongkar → hub tidak jadi; GUI hub dan GUI turret yang dibuka lewat hub tertutup. Blok drop sebagai item biasa; hub tidak punya inventory.

**Belum jadi: petunjuk ala Tinkers**
- Klik kanan Interface yang belum jadi (tidak membuka GUI): pesan action bar dengan masalah pertama dan jumlah masalah ("Struktur belum lengkap (3): butuh Hub Casing"), lalu **hologram cetak biru** 10 detik, hanya untuk pemain itu, hilang lebih cepat bila struktur jadi:
  - posisi kosong: kotak hologram cyan + ikon item blok yang dibutuhkan (Hub Casing; untuk satelit Satellite Uplink T1, 2 kotak) berputar di tengah kotak;
  - blok salah (blok lain, bagian hub lain): kotak merah;
  - tidak ada Power Port: kotak kuning di setiap Hub Casing (salah satunya harus diganti).
- Urutan masalah: satelit, lalu posisi C dari lapisan bawah ke atas; di tiap lapisan dari baris depan ke belakang dan kiri ke kanan (dilihat dari depan Interface), lalu Power Port.
- Kotak memakai gaya hologram penempatan (`PlacementPreview`: isi 15 %, tepi 60 %, fullbright, tidak tembus dinding).

**Jangkauan**
- Dihitung dari chunk kolom tengah (tempat satelit). T1: chunk itu saja; T2: 3x3 chunk; T3: 7x7 chunk; chunk satelit selalu di tengah. Semua ketinggian, hanya dimensi yang sama.
- Turret di chunk yang tidak termuat di server tidak tampil (hub tidak pernah memuat chunk). Pemain yang berdiri di hub biasanya memuat seluruh area T3 (radius 3 chunk).

**Turret yang tampil**
- Setiap turret (base standar dan Large Turret Base; satu entri per turret, di blok intinya) yang **punya Hub Connector yang di-link ke hub ini** di slot Connector-nya, di chunk jangkauan yang termuat, yang boleh dibuka pemain itu (`canConfigure`: owner, trusted, op, atau semua bila `ownerProtection` mati). Tanpa connector, connector belum di-link, atau di-link ke hub lain: tidak tampil.
- Maksimal 256 turret, terdekat ke satelit dulu; bila lebih, header menulis "256+".

**Hub Connector**
- Item `hub_connector` (Hub Connector / Konektor Hub), tumpuk 16, tab kreatif Bastion.
- Link: klik kanan Hub Interface (jadi atau belum) sambil memegang connector → **seluruh tumpukan di tangan** di-link ke hub itu (posisi Interface + dimensi disimpan di item); pesan action bar "Terhubung ke hub di x, y, z". Klik kanan Interface dengan connector tidak membuka GUI. Shift+klik kanan di udara menghapus link.
- Tooltip: hub tujuan (koordinat) atau "Belum terhubung: klik kanan Hub Interface". Ikon: chip gunmetal dengan steker; yang sudah di-link lampunya cyan (item property `linked`).
- Slot: tiap turret base (standar dan Large, semua tier) punya 1 slot **Connector** baru di tab Status GUI turret, di dekat slot modul; hanya menerima Hub Connector (yang belum di-link juga boleh masuk, tapi tidak menghubungkan apa pun). Hopper/pipa tidak bisa memasukkan atau mengambilnya (otomasi hanya melihat slot amunisi).
- Turret dihancurkan → connector drop seperti modul; turret ditambang → connector tetap di dalam seperti isi lainnya. Turret Configurator tidak menyalin connector (hanya targeting).
- Link menyimpan posisi Interface: hub yang dibangun ulang di tempat yang sama tetap terhubung; Interface di tempat lain = link ulang.
- Save lama: turret yang sudah ada otomatis mendapat slot Connector kosong.
- Mengambil connector lewat GUI turret yang dibuka dari hub memutus turret itu dari hub: GUI-nya tertutup (cek `stillValid`).

**Energi**
- Interface menyimpan FE: `controlHubEnergyCapacity` 200.000.
- Tiap Power Port menerima dari kabel sampai `controlHubPortMaxInput` 2.000 FE/tick dan meneruskannya ke Interface. Power Port yang belum jadi bagian hub menolak FE. Hub tidak mengeluarkan FE.
- Selama jadi, hub membayar `controlHubUpkeep` [20, 60, 150] FE/tick (T1, T2, T3) dari buffer. Buffer kurang dari biaya tick itu → **OFFLINE**; kembali **ONLINE** setelah buffer berisi `controlHubRestartTicks` (100) tick upkeep: 2.000 / 6.000 / 15.000 FE (tanpa jeda ini hub berkedip ON/OFF tiap tick bila pemasukan FE di bawah upkeep).
- Interface dibongkar → FE-nya hilang (seperti blok Feed Hub); bagian lain dibongkar → FE tetap.
- Semua angka di config server, termasuk `controlHubRange` [1, 3, 7] (sisi area dalam chunk per tier, bilangan ganjil) dan `controlHubRestartTicks` 100.

**Aturan server**
- GUI hub terbuka bila hub jadi (ONLINE atau OFFLINE) dan pemain dalam 8 blok dari Interface (jarak yang sama dengan GUI turret). Data turret dan semua aksi (buka turret, atur massal) hanya saat ONLINE.
- GUI turret yang dibuka lewat hub dicek tiap tick (`stillValid`): hub jadi + ONLINE + pemain dalam 8 blok dari Interface + turret di dalam jangkauan + chunk-nya termuat + **connector turret di-link ke hub ini** + `canConfigure`. Gagal → tertutup.
- Setiap packet dari GUI hub dicek ulang dengan syarat yang sama per turret; turret yang gagal dilewati.
- `TurretConfigUpdate` diterima bila pemain sedang membuka GUI turret itu dan GUI itu masih valid (dari dekat atau lewat hub), menggantikan cek jarak 8 blok saja. Dari dekat hasilnya sama dengan sekarang.

**GUI hub** (tekstur dari `tools/gen_textures.py`, tab samping dan `FlatButton` seperti GUI turret, semua teks lewat `fit()`)
- Header: nama hub, "Satelit T2 · 9 chunk · 14 turret", bar FE (hover: angka FE dan biaya per tick).
- OFFLINE: hanya header + pesan "OFFLINE: FE habis"; tab tidak bisa dipakai.
- Belum ada turret terhubung: Peta dan Daftar menulis "Belum ada turret terhubung: link Hub Connector ke hub ini, lalu pasang di turret".
- **Peta**
  - Peta tampak atas area jangkauan, digambar client dari chunk yang dimuat client: warna `MapColor` blok teratas (heightmap `WORLD_SURFACE`) dengan bayangan tinggi seperti peta vanilla; area yang tidak dimuat client gelap. Digambar ulang tiap 5 detik selama terbuka.
  - Utara di atas, garis tipis di tiap batas chunk, ikon hub di posisi satelit.
  - Titik turret: hijau HP ≥ 50 %, kuning 25–50 %, merah berkedip < 25 % (ambang lampu HP base, 4.1); abu-abu = mati (switch/redstone) atau tanpa senjata; tanda "!" oranye berkedip = NO_AMMO (amunisi atau FE habis); cincin putih = terpilih.
  - Hover: tooltip setiap turret di bawah kursor: nama senjata, koordinat, HP, amunisi (jumlah) atau FE (%), status.
  - Klik: buka GUI turret (yang paling tinggi bila bertumpuk). Shift+klik: pilih/lepas. Shift+seret: kotak pilih. Scroll atau tombol + / − di pojok peta: zoom 1x/2x/4x (satu tingkat per putaran roda atau per usapan touchpad). Seret (kiri atau kanan): geser saat zoom (user 10-10: touchpad MacBook).
- **Daftar**
  - Satu baris per turret: ikon senjata, nama, koordinat, bar HP, amunisi/FE, lampu status. Bisa di-scroll.
  - Urutan (tombol): jarak / HP terendah / amunisi terendah (persen isi: item amunisi terhadap kapasitas slot amunisi yang terbuka, atau FE terhadap kapasitor).
  - Klik baris: buka GUI turret. Kotak centang: pilih. Tombol "Pilih semua" dan "Kosongkan".
- **Atur massal**
  - "N turret terpilih"; tanpa pilihan, semua kontrol nonaktif.
  - Kontrol tab Targeting: kategori (5), mode pemain, prioritas, trusted (tambah/hapus), aturan per mob (cari mob → Selalu/Jangan/hapus), plus tombol Nyala dan Mati.
  - Nilai yang beda antar turret terpilih tampil "Campur"; daftar trusted dan aturan menampilkan gabungan semua turret terpilih, yang tidak dimiliki semua ditandai "sebagian".
  - Tiap klik dikirim sebagai satu perubahan ("prioritas = HP terendah", "zombie = Jangan", "hapus trusted steve", "nyala") dan diterapkan ke setiap turret terpilih yang lolos cek. Pengaturan lain tiap turret tidak berubah.
  - Mode redstone dan mode tembak (salvo) tidak ada di sini; tetap diatur per turret.
- Pilihan, tab, zoom dan posisi peta bertahan selama pemain bolak-balik ke GUI turret; hilang saat GUI hub ditutup.

**GUI turret dari jauh**
- Sama persis dengan GUI turret biasa (Status: slot senjata, amunisi, modul, connector, HP, heat, nyala/mati; Targeting; Info). Modul, amunisi dan senjata dipindah dari dan ke inventory pemain seperti biasa.
- Tambahan tombol "◀ Peta" di header untuk kembali ke GUI hub. Esc menutup semuanya seperti biasa.

**Tampilan** (palet gunmetal + glow cyan; efek lewat framework VFX, tanpa partikel vanilla)
- Hub Casing: panel gunmetal dengan alur dan baut. Jadi: panel polos dengan rangka + garis glow cyan di tepi luar kubus 3x3x3, seperti struktur Feed Hub 3x3x3 (model multipart dari blockstate, digenerate `tools/gen_control_hub_models.py`).
- Hub Power Port: panel Casing + soket daya bundar di tiap sisi (cincin cyan, ikon petir). Jadi: soket menyala, plus garis tepi seperti Casing.
- Hub Interface: konsol dengan layar miring menghadap depan. Belum jadi: layar gelap. Online: radar menyapu (tekstur animasi fullbright). Offline: layar berkedip merah "NO POWER". Letaknya di tengah sisi kubus, jadi tidak kena garis tepi.
- Satellite Uplink (GeckoLib, `tools/blockout/design_control_hub.py`, satu model dengan bagian per tier): tiang bersambungan dengan kotak servo di atasnya, kepala berputar membawa dish + feed horn. T1 dish kecil; T2 dish lebih besar + antena samping; T3 dish besar + feed horn menyala + 2 lampu suar.
  - Animasi: `folded` (belum jadi: dish terlipat menunduk, diam), `deploy` (sekali saat jadi: dish membuka dan tegak), `spin` (online: kepala berputar sekitar 1 putaran per 10 detik, dish sesekali mengangguk, lampu suar berkedip), `offline` (berhenti, lampu merah).
- Saat jadi: bidang scan cyan setinggi kubus menyapu dari depan ke belakang + kilatan kecil di dish.

**Suara** (placeholder dari `tools/gen_sounds.py`, dicatat di `docs/SOUND_SHEET.md`): `control_hub.online`, `control_hub.offline`, `control_hub.dish_loop` (dengung + servo pelan, loop di dekat satelit saat online), `control_hub.ui_select` (pilih/klik di GUI hub).

**Resep** (Module Workstation; angka di `tools/gen_recipes.py`, bisa diubah)

| Hasil | Bahan | Waktu, FE |
|---|---|---|
| 8 Hub Casing | 4 iron ingot, 2 copper ingot, 1 redstone | 60 t, 1.000 |
| Hub Power Port | 1 Hub Casing, 1 redstone block, 2 copper ingot | 60 t, 1.500 |
| Hub Interface | 1 Hub Casing, 1 diamond, 3 glass pane, 2 redstone, 1 comparator | 100 t, 3.000 |
| Satellite Uplink T1 | 3 iron ingot, 2 copper ingot, 1 ender pearl, 1 redstone | 100 t, 3.000 |
| Satellite Uplink T2 | 1 Satellite Uplink T1, 2 diamond, 2 gold ingot, 1 ender eye | 140 t, 6.000 |
| Satellite Uplink T3 | 1 Satellite Uplink T2, 1 netherite scrap, 2 diamond, 2 ender eye | 180 t, 10.000 |
| 4 Hub Connector | 2 copper ingot, 1 redstone, 1 ender pearl | 60 t, 1.000 |

**Teknis** (ringkas; detail di rencana implementasi)
- `ControlHubStructure`: satu fungsi cek untuk kubus 3x3x3 (26 posisi + satelit), dipakai server untuk membentuk dan client untuk hologram cetak biru. Hasil: jadi/tidak + daftar masalah (posisi, jenis, item yang dibutuhkan). Casing/Port: blockstate `x`, `y`, `z`.
- `TurretInventory`: slot baru `CONNECTOR` setelah slot modul (ukuran 14 → 15); save lama berukuran 14 dimuat lalu diperbesar tanpa memindah isi. `HubConnectorItem`: NBT posisi Interface + dimensi; link lewat `onItemUseFirst` di Interface. `HubInterfaceBlockEntity.visibleTurrets` dan `reachable` mensyaratkan connector ke hub ini.
- `HubInterfaceBlockEntity`: FE, status, tier satelit, hasil cek terakhir; tick server membayar upkeep dan memperbarui status.
- Satellite Uplink: blok 1x1x2 dengan properti `part` (pola `WorkstationBlock`); blok bawah memegang block entity dan renderer GeckoLib.
- `TurretMenu` mendapat tautan hub opsional (posisi Interface) di open data; `stillValid` memakai cek hub bila tertaut. Tombol "◀ Peta" hanya tampil bila tertaut.
- Packet baru: `HubRoster` (S→C, tiap 10 tick selama GUI hub terbuka: posisi, senjata, HP, status, amunisi/FE, nyala/mati; filter lengkap tiap turret hanya saat GUI dibuka dan saat berubah) dan `HubAction` (C→S: buka turret, kembali ke hub, satu perubahan atur massal untuk daftar posisi). Protokol jaringan naik ke 10.
- Peta: `DynamicTexture` 1 px per blok (T3 = 112x112), digambar ulang tiap 100 tick.

**Tahap implementasi** (tiap tahap: `./gradlew build` dan semua GameTest lolos, bisa dites di game)
1. Blok, struktur, hologram cetak biru, FE (ONLINE/OFFLINE), model + suara dasar.
2. GUI hub Peta + Daftar, sinkron turret, GUI turret dari jauh + "◀ Peta".
3. Pilih banyak + Atur massal; polish: VFX scan, animasi satelit, scene showcase, docs.

**Selesai jika**
- GameTest struktur (kubus 3x3x3): Interface + 25 Casing + 1 Power Port + satelit T1 di atas kubus → jadi (blockstate bagian `x/y/z` dan status Interface berubah). Tanpa satelit, satelit tidak di atas tengah, blok asing di posisi C (juga di lapisan bawah/atas dan blok inti), tanpa Power Port, blok milik hub lain → tidak jadi, dengan masalah pertama yang benar. Membongkar satu bagian → tidak jadi 1 tick kemudian.
- GameTest connector: klik kanan Interface me-link seluruh tumpukan (posisi tersimpan); slot Connector hanya menerima Hub Connector dan tidak bisa diisi/diambil otomasi; hub hanya menampilkan turret dengan connector ke hub itu (tanpa connector, belum di-link, atau ke hub lain → tidak tampil); buka turret dan atur massal ditolak tanpa connector; save lama (14 slot) dimuat dengan slot Connector kosong dan isi lain di tempatnya; connector drop saat turret hancur.
- GameTest jangkauan: untuk T1, T2 dan T3, turret di chunk terluar yang masih dalam jangkauan tampil, turret satu chunk di luarnya tidak.
- GameTest akses: turret milik pemain lain tidak tampil dan tampil setelah pemain dijadikan trusted. Buka turret lewat hub diizinkan bila ONLINE + dalam jangkauan + pemain ≤ 8 blok dari Interface, ditolak bila OFFLINE, di luar jangkauan, atau pemain terlalu jauh. GUI turret yang dibuka lewat hub tertutup saat satu bagian hub dibongkar. `TurretConfigUpdate` untuk turret jauh tanpa GUI yang valid ditolak.
- GameTest energi: upkeep 20/60/150 FE per tick; buffer habis → OFFLINE, diisi sampai 100 tick upkeep → ONLINE (1 FE kurang dari itu → tetap OFFLINE); diberi FE di bawah upkeep → tetap OFFLINE tanpa berkedip; Power Port yang belum jadi bagian hub menolak FE; 2.000 FE/tick per port.
- GameTest atur massal: "prioritas = HP terendah" ke 3 turret → ketiganya berubah, aturan mob dan trusted masing-masing tetap; satu aturan mob ditambah lalu dihapus di semua; turret di luar jangkauan dalam packet dilewati; nyala/mati massal.
- Semua GameTest lama tetap lolos (juga dengan `-PwithCompatMods`), yang struktur 3x3 diubah ke kubus; `./gradlew build` lolos.
- Scene `-Pshowcase=hub` di `-PshowcaseWorld=showcase_fx`: kubus belum jadi + hologram cetak biru; kubus jadi siang dan malam (dish berputar, garis glow, Interface sejajar mata); T1/T2/T3 berdampingan; turret dengan connector (dan satu tanpa connector yang tidak tampil); GUI turret dengan slot Connector; GUI Peta (turret hijau/kuning/merah dan "!"), Daftar, Atur massal dengan "Campur", GUI turret dengan "◀ Peta"; OFFLINE.
- `docs/TESTING.md`, deskripsi store (+ HTML CurseForge), changelog 0.2.0 di `docs/release/UPLOAD.md`, `docs/HANDOFF.md`.

**Di luar cakupan**: tablet/remote portabel, mob atau pemain di peta, notifikasi saat turret diserang atau hancur, grup turret bernama, chunk loading, dimensi lain, comparator dari hub, struktur lebih besar dari 3x3x3, link connector massal dari hub, isi amunisi atau modul ke banyak turret sekaligus, mode redstone dan salvo massal, peta 3D atau hologram peta di atas hub.


---

### Turret early game (permintaan user 2026-10-14 23:13, desain disetujui malam itu per bagian) — **untuk nanti, versi belum diputuskan**

**Status**: desain saja, belum ada plan atau kode. Versi: belum diputuskan oleh user (masuk 0.2.0 atau sesudahnya;
rekomendasi: 0.3.0, karena 0.2.0 sudah besar dan lini ini butuh 5 model, tekstur dan suara baru).

**Latar belakang**: user ingin empat turret early game yang dibuat di crafting table tanpa workstation, memakai peluru
yang gampang didapat, di base terpisah, dengan desain yang tidak terlalu futuristik.

**Keputusan user** (2026-10-14): base terpisah dan **terkunci**; Arrow Launcher menerima **semua panah dan efeknya
ikut**; **semua resep butuh besi**; arsitektur **A** (block base baru di atas block entity turret yang sama); angka di
bawah disetujui apa adanya.

**Pengecualian aturan bagian 1** (keputusan user, hanya untuk lini ini)
- Arrow Launcher memakai entity panah vanilla dan renderer panah vanilla (NO-VANILLA tetap berlaku untuk semua yang lain:
  partikel, jejak, suara, model, tekstur).
- Proyektil early tidak wajib bercahaya (batu atau kentang yang glow terasa salah); tetap sprite dan jejak custom.
  Tanpa cahaya dinamis.

**Makeshift Turret Base** (nama sementara)
- 1 block; block entity turret yang sama dengan jenis base `MAKESHIFT` (perluasan `large` di `TurretInventory` /
  `WeaponModuleItem` menjadi tiga jenis: standar, large, makeshift).
- Mati: tier (Tier Upgrade Kit tidak bekerja), slot modul, FE, slot Connector (tidak muncul di Control Hub). Layar
  turret yang sama, slot-slot itu disembunyikan.
- Tetap: targeting, filter target (+ Turret Configurator), ON/OFF dan redstone, HP dan hancur, otomasi hopper (slot
  ammo), comparator, save/load, sync.
- HP 50 (T1 standar 100), di config.
- Penguncian: hanya 4 modul early muat di base ini; modul early tidak muat di base standar atau large.

**Peluru** (item tag, modpack bisa menambah): `bastion:ammo/pebble` = cobblestone, cobbled deepslate;
`bastion:ammo/snow` = snowball; `bastion:ammo/potato` = potato, poisonous potato (racunnya tidak ikut);
`bastion:ammo/arrow` = `#minecraft:arrows`.

**Senjata** (semua angka di `data/bastion/turret_weapons/*.json`; modul kecil, base makeshift)

| | Cobblestone Turret | Snowball Repeater | Potato Shotgun | Arrow Launcher |
|---|---|---|---|---|
| Damage | 3 | 1,5 | 2 per butir, 5 butir | 3 per panah, 6 panah |
| Laju | 15 tick | 4 tick (5/dtk) | 30 tick | 50 tick per volley |
| Jangkauan | 16 | 14 | 10 | 32, min 6 |
| Sebaran | 1,5° | 3° | 10° | ring ~1,5 blok di sekitar titik bidik |
| Peluru | 1 cobble per tembakan | 1 snowball = 4 tembakan (`ammo_per_shot` 0,25) | 1 potato per tembakan | 1 panah = 1 volley (seperti Multishot) |
| Lintasan | lurus (sistem `Bullets`), terlihat terbang | lurus | lurus, menyebar | lengkung tinggi (solver Mortar), lewat atas tembok |

- Cobblestone, Snowball, Potato: peluru data (`Bullets`, tanpa entity), lead seperti Gun; berhenti di target sah
  pertama atau block; menembus non-target (aturan peluru bagian 7).
- Arrow Launcher: 6 panah per volley sebagai entity sendiri turunan `Arrow` vanilla (renderer panah vanilla, jadi
  tampilannya panah biasa) supaya hanya mengenai target sah; jenis panah dari item (biasa, spectral, tipped; efeknya
  ikut), `pickup` DISALLOWED, damage diset supaya ~3 per panah apa pun kecepatannya; solver lengkung Mortar (catatan: panah
  vanilla punya hambatan udara 0,99/tick, solver Mortar tidak; perlu dikompensasi atau disimulasi). Tidak menembak mob
  terbang (seperti Mortar).
- Putaran lebih lambat dari turret modern (mekanik kayu): `turn_speed` 6 untuk keempatnya (Gun 9, Shotgun 10).

**Resep** (crafting table, semua butuh besi; bentuk pola diputuskan saat plan, bahan ini usulan yang disetujui arahnya)
- Makeshift Turret Base: 3 iron ingot, 2 log, 4 cobblestone.
- Cobblestone Turret: 2 iron ingot, 3 cobblestone, 2 planks, 1 string.
- Snowball Repeater: 3 iron ingot, 3 planks, 2 stick, 1 snow block.
- Potato Shotgun: 4 iron ingot, 2 planks, 1 leather, 1 potato.
- Arrow Launcher: 3 iron ingot, 3 planks, 1 crossbow.

**Tampilan** (rustik: kayu, batu, pita besi, paku keling, tali, kulit; pegas, engkol, roda gigi; tanpa glow atau ring
energi; tekstur original)
- Base: kerusakan terlihat dari retakan tekstur dan serpihan kayu custom (bukan health lights).
- Cobblestone: meriam pegas pendek-lebar di pivot kayu; tembak = pegas tertekan, laras mundur.
- Snowball: drum 4 laras yang diputar engkol, corong salju di atas; drum berputar selama menembak.
- Potato: pipa besi gemuk, tabung tekanan, pompa tangan (spud gun); pompa naik-turun tiap tembakan.
- Arrow: rak balista 6 panah (2x3) menghadap ke atas, busur dan tali; tali menyentak, rak terisi ulang.
- Proyektil: Cobblestone = sprite batu kasar abu-abu berputar + debu tipis, kena = serpihan batu dan debu;
  Snowball = gumpalan putih lembut + jejak beku samar, kena = ledakan salju dan serpihan; Potato = potongan kentang
  berguling, kena = cipratan dan potongan kecil; Arrow = panah vanilla + garis angin pucat mengikuti tiap panah,
  menancap seperti vanilla + debu kecil.
- Moncong: semburan udara/debu (Snowball: uap beku, Arrow: garis sentakan tali), tanpa api.

**Suara** (event baru; placeholder `gen_sounds.py`, yang asli nanti dari Omni/SFX generator): tembakan tiap turret, base
deploy (derit kayu) dan hancur (kayu remuk). Tanpa hum idle. Tanpa suara proyektil kena (keputusan user sebelumnya).

**Test** (GameTest dulu, gagal dulu)
- Penguncian: modul early hanya di base makeshift; modul modern ditolak di sana; modul early ditolak di base standar dan
  large; Tier Upgrade Kit, slot modul, FE dan Connector tidak bekerja.
- Resep: base dan 4 turret ada di crafting table, semuanya memakai besi.
- Damage per kena 3 / 1,5 / 2; Potato 5 butir per tembakan; laju Snowball ~5/dtk; 1 snowball = 4 tembakan; 1 panah = 6
  panah.
- Arrow Launcher: panah tidak bisa dipungut; efek tipped kena target; menembak lewat atas tembok; tidak menembak mob
  terbang atau target dalam 6 blok.
- Base makeshift tersimpan setelah save/load; dedicated server.
- Visual: scene `-Pshowcase=early` (base + 4 turret, siang dan malam, proyektil, impact); checklist di `TESTING.md`.

**Di luar cakupan**: modul atau tier untuk base makeshift, jalur upgrade ke base modern, snow block sebagai peluru, efek
racun kentang beracun, suara proyektil kena.

### Config stat turret (permintaan user 2026-10-17 12:35, desain disetujui 12:50–12:51)

**Status**: dibuat 2026-10-17 (plan `docs/superpowers/plans/2026-10-17-turret-stats-config.md`, 175 GameTest lolos; final review diperbaiki: guard damage NaN/0, `ammo_per_shot` 0~1 (0~0 untuk senjata FE + Missile Launcher), `energy_capacity` ≤ 3.000.000, `max_heat` ≤ 3000; 2026-10-18: file ditulis dengan angka default mod, bukan `"default"`, 177 GameTest lolos), dites user di game 2026-10-18 (angka Shotgun, reload saat game jalan), rilis di 0.2.1 (commit 2026-10-19).

**Tujuan**: pemain dan admin server mengatur angka tiap turret lewat file config, tanpa membuat datapack. Jadi ada dua
file config gameplay: `bastion-common.toml` (yang sudah ada: tier base, proteksi, FE hub, dll.) dan file baru
`config/bastion-turrets.toml`. (Plus `bastion-client.toml` untuk tampilan, tidak berubah.)

**Keputusan user**:
- Cakupan: **senjata saja** (16 turret bawaan). Efek modul Mk1–Mk3 tetap lewat datapack (12:43).
- Isi awal tiap angka: ~~`"default"` + komentar~~ (12:45) → **angka default mod + komentar** (user 2026-10-18 11:55:
  "defaultnya jangan ditulis "default", tapi tulis saja angka defaultnya"). Angka yang sama dengan nilai mod = bukan
  override, jadi datapack tetap berlaku untuk key yang tidak diubah. Rebalance di update mod tidak sampai ke file yang
  sudah ada (angka lama tertulis di file dan dihitung sebagai override bila nilai mod berubah).
- Pendekatan A: **lapisan override saat data senjata dibangun** (12:50).

**File** (`ModConfig.Type.COMMON`, nama file `bastion-turrets.toml`, global untuk semua world):
- Komentar paling atas (Inggris): tiap nilai mulai dari angka mod; dibiarkan di angka itu = senjata seperti buatan mod
  atau datapack; angka lain menimpa; 20 tick = 1 detik; berlaku begitu file disimpan; stat yang tidak dipakai suatu senjata (mis.
  `spread` di Tesla Coil) tidak berpengaruh.
- Satu section per senjata, nama = id file JSON-nya (`[gun]`, `[machine_gun]`, ..., `[plasma_cannon]`), urutan seperti
  tabel senjata di deskripsi.
- Tiap section berisi **15 stat umum** (`range`, `fire_interval`, `damage`, `spread`, `ammo_per_shot`, `turn_speed`,
  `aim_tolerance`, `aim_lock_ticks`, `min_pitch`, `max_pitch`, `sweep_tolerance`, `heat_per_shot`,
  `heat_dissipation_per_second`, `max_heat`, `overheat_ticks`) **plus semua `params` di JSON senjata itu** (mis.
  `charge_ticks`, `energy_per_shot`, `blast_radius`), langsung di section (tanpa sub-tabel `params`; tidak ada nama
  yang bentrok).
- Tidak masuk config: `type`, `ammo`, `energy_color`, `pivot_height`, `muzzle_length` (identitas, warna, geometri
  model).
- Nilai tiap key: angka dalam batasnya, awalnya angka mod (bulat ditulis tanpa `.0`). Komentar per key: penjelasan satu baris (Inggris) +
  satuan + `Mod default: <nilai JSON bawaan>` + `Range: <min> ~ <max>`.
- Batas per key: tabel di kode, aturan umum per jenis (tick 0–12000, damage 0–10000, jarak/radius 0–256, sudut 0–180,
  pitch −90–90, rasio/akurasi 0–1, FE 0–10.000.000, jumlah ≥ 1) dengan pengecualian eksplisit (mis. `fire_interval`
  minimal 0,5 tick; `ammo_per_shot` 0–1, 0–0 untuk senjata tanpa item; FE kapasitas ≤ 3.000.000 dan `max_heat` ≤ 3000
  karena bar GUI 16-bit). Field int (`aim_lock_ticks`, `overheat_ticks`) dibulatkan.
- Nilai salah (teks, di luar batas): validator Forge mengembalikannya ke angka mod dan menulis
  peringatan di log (perilaku bawaan ForgeConfigSpec).

**Isi spec saat start**: dibangun di konstruksi mod dari JSON bawaan **di dalam jar** (`/data/bastion/turret_weapons/
<id>.json` lewat classloader): itu sumber daftar key, nilai `Mod default` dan nilai stat umum yang tidak tertulis di JSON
(default codec, mis. `heat_per_shot` 0). Daftar 16 id senjata tetap di kode; GameTest memastikan daftar itu sama dengan
file di folder `turret_weapons` bawaan.

**Penggabungan** (`WeaponDataLoader`):
- Saat datapack dimuat: JSON diparse seperti sekarang → disimpan sebagai **data mentah**; **data efektif** = data mentah
  dengan setiap key config yang angkanya beda dari nilai mod ditimpa. `get()`/`all()` mengembalikan data efektif, jadi semua
  pemakai (fire cycle, `StatSheet`, `param()`, GUI Info, tooltip, efek client) ikut tanpa diubah.
- `min_pitch` > `max_pitch` setelah override: dua override itu diabaikan untuk senjata itu + peringatan log.
- Senjata yang tidak ada di data mentah (dihapus datapack): override-nya diabaikan.
- Senjata dari namespace lain (datapack mod lain) tidak ada di config dan tidak tersentuh.

**Sinkron & reload**:
- Client menerima data efektif lewat `WeaponDataSync` seperti sekarang (saat login dan `/reload`). File config milik
  client diabaikan untuk gameplay di server dedicated; di singleplayer server dan client memakai file yang sama.
- `ModConfigEvent.Reloading` untuk spec ini (Forge memantau file): di thread server, data efektif dibangun ulang dari
  data mentah lalu `WeaponDataSync` dikirim ke semua pemain online. Tidak perlu restart; turret memakai angka baru
  mulai tembakan/tick berikutnya.

**Acceptance**:
- GameTest: (1) tiap senjata bawaan punya section dengan tepat 15 stat umum + semua `params` JSON-nya (tidak kurang,
  tidak lebih) dan daftar id = isi folder; (2) override `damage` Sniper muncul di data efektif, kembali ke angka mod
  memulihkan angka JSON; (3) override `charge_ticks` Railgun dipakai saat charge (`chargeTicks`); (4) nilai di luar
  batas ditolak validator; (5) event reload membangun ulang data efektif.
- Manual: ubah satu angka saat game jalan → tab Info turret dan perilakunya berubah tanpa restart; server dedicated
  (LAN) memakai angka server. Checklist di `docs/TESTING.md`.
- `./gradlew build` lolos, semua GameTest lama lolos (juga `-PwithCompatMods`).
- Dokumen: paragraf "For modpack makers" di deskripsi (config untuk stat turret), `HANDOFF.md`.

**Di luar cakupan**: efek modul, tier base (sudah di `bastion-common.toml`), geometri/warna/ammo/type senjata, config per
world (`serverconfig`), editor config di dalam game, senjata dari namespace lain.

### Compat JEI & Jade (permintaan user 2026-10-20 12:50, desain disetujui 12:57, spec disetujui 12:59)

**Status**: plan `docs/superpowers/plans/2026-10-20-jei-jade-compat.md` (dibuat 2026-10-20, 195 GameTest lolos, review akhir diperbaiki; user tes di game 2026-10-20: halaman JEI station/weapon/info
  dan Jade turret oke, sisanya (tombol +, Jade station/hub, tanpa JEI/Jade) belum; rilis di 0.2.2, commit 2026-10-20).

**Tujuan**: menghapus "no JEI or Jade support yet" dari Known limits. Di JEI pemain bisa mencari resep dan kegunaan
item Bastion; dengan Jade, cukup melihat blok Bastion untuk tahu isinya.

**Keputusan user**:
- Cakupan ronde ini: **JEI + Jade**. The One Probe **nanti** (ronde terpisah). EMI ikut dapat resep lewat dukungan plugin
  JEI-nya (selama JEI terpasang); plugin EMI/REI native tidak dibuat.
- JEI: 5 kategori station **plus** halaman info item, tombol + (transfer resep), dan kategori stat weapon.
- Jade: **ringkas, detail saat Shift** (`showDetails` Jade).
- Pendekatan: **isi baris ditentukan kode Bastion**, adapter Jade tipis; TOP nanti cukup adapter baru.

**Build**:
- JEI 15.x dan Jade 11.13.x (Forge 1.20.1) dari Modrinth maven yang sudah ada di `build.gradle`; version id dikunci ke rilis
  berumur ≥ 2 minggu: JEI **15.20.0.134** (`ujE6Mlsq`, cabang 15.20 yang dipakai kebanyakan modpack) dan Jade
  **11.13.3+forge** (`xJQHCmWJ`). `compileOnly` untuk kompilasi; ikut `runClient` dev lewat `clientDevMods` (jadi `-PnoDevMods` dan
  scene stress tetap tanpa JEI/Jade). GameTest tidak memuatnya.
- `mods.toml`: `jei` (`[15.20,)`, side CLIENT) dan `jade` (`[11.13,)`) `mandatory=false` (seperti `create`).
- Kode di `dev.bastion.client.compat.jei` (`@JeiPlugin`; client saja, jadi di package `client` sesuai §9.5) dan
  `dev.bastion.compat.jade` (`@WailaPlugin`). Keduanya
  hanya dimuat oleh mod itu sendiri lewat anotasi; tanpa JEI/Jade tidak ada class compat yang tersentuh, juga di server
  dedicated.
- Jar JEI/Jade belum ada di cache Gradle: butuh **satu kali run Gradle online** khusus mengunduh dependency, setelah
  itu `--offline` seperti biasa.

**JEI**:
- **5 kategori**, satu per station (ikon & catalyst = item station): sampai 6 bahan dengan jumlahnya, hasil, waktu (detik),
  total FE + FE/t, tanda "instan" untuk Module dan Ammo Workstation. Isi = semua resep `bastion:workstation` (termasuk dari
  datapack). Resep crafting table untuk station tampil otomatis (resep vanilla).
- **Kategori "Turret Weapons"**: satu halaman per weapon: item weapon, slot ammo (isi tag `ammo` weapon itu, jadi U pada
  ammo menampilkan turret yang memakainya), item base yang cocok (Turret Base atau Large Turret Base), dan tabel stat yang
  **sama dengan tab Info GUI turret** (T1, tanpa module). Baris tabel itu dipindah ke satu helper client yang dipakai
  `TurretScreen` dan JEI. Angka dibaca saat digambar dari data senjata client (`WeaponDataSync`), jadi config/datapack
  yang berubah langsung terlihat. Ability weapon ada di tooltip slot weapon (halaman cukup "Ability: hover the weapon"): teks
  ability di halaman kepanjangan (Mortar 11 baris id_id) dan halaman harus muat di 1080p GUI scale 4 (review 2026-10-20).
- **Halaman info** (Information JEI), teks en_us + id_id: Turret Base & Large Turret Base (pasang weapon, ammo, tier),
  upgrade kit T2/T3, repair kit, Turret Configurator, Hub Connector, Feed Hub, part Control Hub (interface, casing, power
  port, satellite uplink T1–T3), Creative Power Source.
- **Tombol +**: hanya saat GUI station yang sama terbuka dan resepnya milik station itu. Klik = resep terpilih + bahan
  untuk 1 craft dipindah dari inventory pemain ke slot input; Shift-klik = sebanyak yang muat di slot. Paket baru C→S
  `WorkstationFill(pos, recipe, crafts)` dengan cek yang sama seperti `WorkstationAction` (menu terbuka di pos itu,
  `stillValid`), lalu server memilih resep dan memindahkan item. Bahan kurang: tombol merah dan slot yang kurang
  ditandai (perilaku standar JEI), tidak ada item yang berpindah.

**Jade** (Bastion menyusun baris; adapter Jade cuma menempelkan):
- **Turret**: selalu: HP / max HP, tier, state. Shift: owner, module terpasang, heat (senjata ber-heat), target.
  Ammo (isi slot) dan FE (senjata energi) lewat tampilan bawaan Jade dari capability.
- **Workstation**: resep yang sedang diproses + persen progress. FE dan isi slot lewat bawaan Jade.
- **Feed Hub**: jumlah turret yang disuplai. FE dan isi storage lewat bawaan Jade.
- **Control Hub** (interface, casing, power port, satellite uplink): Online / Offline / belum terbentuk, tier, upkeep
  FE/t, jumlah turret yang terhubung (dihitung maksimal sekali per detik per hub, bukan tiap request Jade).
- Part multiblock (Large Turret Base, workstation, Feed Hub, Control Hub) menampilkan info blok intinya.
- Data dari server lewat server data provider Jade (NBT ringkas); server tanpa Jade = tidak ada baris Bastion (hanya baris
  Jade sendiri): tier/FE hub dan progress station di client tidak tersinkron, jadi baris dari data client bisa salah (review
  2026-10-20).
  Hitbox turret tidak pickable di client, jadi Jade selalu membaca blok base.
- Tiap provider punya id sendiri, jadi bisa dimatikan di config Jade (lang `config.jade.plugin_bastion.*`).

**Acceptance**:
- GameTest (tanpa JEI/Jade): (1) isi baris turret/workstation/Feed Hub/Control Hub sesuai state blok, baris Shift terpisah
  dari baris ringkas; (2) part multiblock memberi info intinya; (3) `WorkstationFill` memindahkan jumlah yang pas,
  tidak memindahkan apa pun saat bahan kurang, menolak resep station lain dan pemain yang tidak membuka GUI itu; (4)
  tabel stat helper sama dengan yang dulu digambar `TurretScreen`.
- Manual (checklist di `docs/TESTING.md`): R/U di JEI untuk part, ammo, weapon; tombol + dan Shift; halaman info; Jade
  pada turret/station/hub (ringkas & Shift); run tanpa JEI/Jade tanpa crash; `runServer` tanpa JEI/Jade.
- `./gradlew build` lolos, semua GameTest lama lolos (juga `-PwithCompatMods`).
- Dokumen: Known limits & changelog di `docs/release/`, `HANDOFF.md`. Versi diputuskan saat rilis (kemungkinan 0.3.0).

**Di luar cakupan**: The One Probe (ronde berikut), plugin EMI/REI native, WTHIT, ikon/tekstur JEI baru selain yang
sudah ada di GUI station, tier/module selector di kategori weapon.

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
