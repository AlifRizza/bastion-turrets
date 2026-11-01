# TurretHitEvent → Anatomy (tembakan turret kena body part yang benar)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tembakan turret Bastion yang punya titik kena nyata mengenai body part Anatomy yang dilewati peluru, sehingga turret bisa memutus lengan/kaki dan menghancurkan kepala Rhask.

**Architecture:** Bastion mem-post `dev.bastion.api.TurretHitEvent` (Forge `EVENT_BUS`, server) tepat sebelum `hurt()` untuk hit dengan titik kena. Bastion tidak tahu Anatomy. Anatomy punya `compat/BastionCompat` (soft dependency seperti `TaczCompat`) yang me-resolve part dari ray melewati titik kena searah tembakan, lalu menandai pending hit yang dicocokkan dengan **identitas DamageSource** (turret tidak punya direct entity). Titik bidik turret pindah ke tengah badan dulu (keputusan user 2026-10-31).

**Tech Stack:** Forge 1.20.1 (47.4.26), Java 17, GameTest (Bastion), JUnit 5 (Anatomy).

**Spec:** `../Xersian/docs/superpowers/specs/2026-10-31-xersian-design.md` bagian 4 (terutama 4.4, opsi event) + permintaan user 2026-10-31 20:53.

## Global Constraints

- **Tanpa commit/push/tag** di ketiga repo sampai user bilang "commit". Jangan stage `modlist.html` atau `sfx/`.
- Gradle selalu `--offline`; log ke `build/*.log`, baca exit code, lalu grep file log (`rtk proxy grep`).
- Jangan buka game (`runClient`) untuk user. GameTest server boleh.
- Jangan ubah `run/config`. Jangan sentuh `run/saves/showcase`.
- Bastion tidak boleh mengimpor apa pun dari Anatomy/Xersian. Di Anatomy, class Bastion hanya disentuh di dalam `dev.anatomy.compat.BastionCompat`, didaftarkan hanya jika `ModList.get().isLoaded("bastion")`.
- Kode client hanya di package client (tidak ada yang client di rencana ini). Lang tidak berubah (tidak ada teks baru untuk pemain).
- Xersian: hanya `gradle.properties` (`bastion_jar_version`) dan `docs/TESTING.md`. Kode Xersian tidak diubah.
- Gaya kode: komentar dan nama mengikuti file sekitar (Javadoc pendek bahasa Inggris, `ponytail:` untuk penyederhanaan sengaja).

## Keputusan saya (bisa Anda tolak saat review rencana)

1. **Gun Turret tetap membidik mata dulu** (override `sight`). Senjata lain ke tengah badan dulu. Alasan: Precision Lock Gun memberi bonus headshot (seperempat atas badan); dengan bidikan tengah bonus itu hampir tidak pernah kena. Akibat di Rhask: Gun hampir selalu kena kepala.
2. **Bonus headshot Gun boleh bertumpuk dengan pengali kepala Anatomy** (×2) di mob Anatomy, hanya pada satu tembakan terkunci per target. Event tidak membawa flag headshot (YAGNI). Kalau salah: tambah satu boolean di event + `markHit`.
3. **`weapon` di event** = id weapon type (`bastion:gun`, `bastion:railgun`, ...) untuk senjata turret; id entity type untuk proyektil/unit (`bastion:turret_rocket`, `bastion:turret_missile`, `bastion:turret_brood_capsule`, `bastion:hive_drone`). `null` hanya untuk peluru yang dibuat langsung oleh GameTest (`Bullets.create`).
4. **Versi Bastion jadi 0.5.1** (`mod_version`). Jar `0.6.0` di `build/libs` adalah build lama sebelum renumber; 0.5.0 sedang direview di CurseForge. `bastion_jar_version` di Xersian: `0.6.0` → `0.5.1` (angkanya turun, tapi jar-nya lebih baru).
5. **Pending hit dari turret dipakai sekali** (dikonsumsi pada hurt pertama yang cocok). Jalur TaCZ tetap: berlaku untuk semua hurt di tick itu.

## Keputusan per senjata (masuk HANDOFF)

| Post event | Tidak post (alasan) |
|---|---|
| Gun, Machine Gun, Shotgun (per pellet), Sniper, Sling, Railgun (hit langsung), Laser (per target yang ditembus), Ion Rifle (bolt), Plasma Repeater (bola, hit langsung), Plasma Cannon (orb, hit langsung), Rocket/Missile (hit langsung), Brood Capsule (jatuh di kepala), Hive Drone (peluru) | Tesla (rantai, tanpa titik), Flamethrower + burn (api), semua splash/blast (Bastion splash sudah lewat rute EXPLOSION Anatomy jika ada posisi), Ion field, Javelin (blast saja), Mortar (blast saja), Brood spider: gigitan = melee vanilla → rute MELEE Anatomy sudah jalan; ledakan mati = splash, Hive kamikaze (splash), Arrow Launcher (`TurretArrowEntity extends Arrow` → rute PROJECTILE Anatomy sudah jalan), turret hancur (direct null → torso) |

## Review Focus

1. **Splash memakai DamageSource yang sama dengan hit langsung, pada target yang sama di tick yang sama** (Plasma Cannon: orb kena X lalu blast `Splash.around(..., null)` juga kena X): blast tidak boleh mendarat di part dari hit langsung → konsumsi pending (keputusan 5).
2. **Event di-post tapi hurt tidak terjadi** (amount 0/NaN, target kebal): event tidak boleh di-post untuk amount ≤ 0/NaN; pending basi kedaluwarsa per tick.
3. **Peluru menembus beberapa target dalam satu step** (Sniper, Railgun, Laser): tiap target dapat event sendiri dengan titik kena sendiri.
4. **Modul senjata diganti saat peluru masih terbang**: `weapon` diambil saat menembak (di `Bullets.fire`), bukan saat kena.
5. **TaCZ tidak rusak**: `markHit` lama membersihkan `pendingSource`; peluru TaCZ dicocokkan lewat direct entity seperti sebelumnya.

---

### Task 1: `TurretHitEvent` + `Hitscan` overload + Gun

**Files:**
- Create: `src/main/java/dev/bastion/api/TurretHitEvent.java`
- Modify: `src/main/java/dev/bastion/weapon/Hitscan.java`, `src/main/java/dev/bastion/weapon/Bullets.java` (field `weapon` di `Bullet`, diisi di `fire`), `src/main/java/dev/bastion/weapon/types/GunWeapon.java`
- Test: `src/main/java/dev/bastion/gametest/TurretCombatTests.java`

**Interfaces:**
- Produces:
  - `public class TurretHitEvent extends net.minecraftforge.eventbus.api.Event` — getters `Entity getTarget()`, `Vec3 getHitPos()`, `Vec3 getDirection()` (unit), `@Nullable ResourceLocation getWeapon()`, `DamageSource getSource()`. Tidak cancelable. Javadoc: server only; `getSource()` adalah instance yang sama yang dipakai `hurt()` berikutnya.
  - `Hitscan.damage(Entity target, DamageSource source, float amount, Vec3 hitPos, Vec3 direction, @Nullable ResourceLocation weapon): boolean` — guard amount sama seperti sekarang, lalu post event, lalu `damage(target, source, amount)`.
  - `Hitscan.damage(Bullets.Bullet bullet, EntityHitResult hit, DamageSource source, float amount): boolean` — memakai `hit.getLocation()`, `bullet.direction`, `bullet.weapon`.
  - `Bullets.Bullet.weapon` (`@Nullable ResourceLocation`, diisi `Bullets.fire` dari `context.stats().data().type()`).

- [ ] **Step 1: Tulis GameTest yang gagal** — di `TurretCombatTests`:
  - `gunShotPostsTurretHitEvent`: gun turret berisi ammo, husk di `FAR`. Listener `TurretHitEvent` (target == husk) menyimpan event; listener `LivingHurtEvent` (entity == husk) menyimpan source. Dalam ≤ 100 tick: event ada; `husk.getBoundingBox().inflate(0.2).contains(hitPos)`; `direction.dot(arah pivot→pusat husk) > 0.9`; `getWeapon()` == `bastion:gun`; `getSource() == ` source hurt (identitas, `==`). Listener di-unregister sebelum `succeed` dan saat gagal.
  - `splashPostsNoTurretHitEvent`: `BastionExplosion.detonate` di samping husk → husk terluka, tidak ada `TurretHitEvent`.
- [ ] **Step 2: Jalankan, pastikan gagal** — `./gradlew runGameTestServer --offline > build/gametest.log 2>&1`. Expected: compile error `cannot find symbol TurretHitEvent`.
- [ ] **Step 3: Implementasi** event, dua overload, field `weapon`, dan `GunWeapon` memanggil `Hitscan.damage(bullet, hit, BastionDamageTypes.turretShot(context.level()), amount)`.
- [ ] **Step 4: Jalankan lagi** — Expected: kedua tes lolos; jumlah gagal sama dengan baseline (flaky lama yang dikenal: `knockbackDoesNotBendTheDyingLeap`, ulangi sekali kalau muncul).

### Task 2: Semua hit dengan titik kena post event

**Files:**
- Modify: `weapon/types/{MachineGun,Shotgun,Sniper,Sling,Railgun,LaserRifle,IonRifle,PlasmaRepeater,PlasmaCannon}Weapon.java`, `projectile/TurretRocketEntity.java` (`explode`: hit langsung, `direction = getDeltaMovement().normalize()`), `projectile/BroodCapsuleEntity.java` (hit kepala, arah = `motion`), `unit/HiveDroneEntity.java` (`shoot`: titik = `entityHit.getLocation()`, arah = `aim - from`).
- Test: `gametest/TurretCombatTests.java` (atau file tes senjata terkait)

**Interfaces:**
- Consumes: kedua overload `Hitscan.damage` dari Task 1.
- Laser memakai overload umum dengan `context.direction()` dan `context.stats().data().type()`. Proyektil/unit: `EntityType.getKey(getType())`.
- Splash di file yang sama (`Splash.around`, `field`, blast) **tetap** memakai `damage(target, source, amount)` lama.

- [ ] **Step 1: Tulis GameTest yang gagal:** `laserPostsTurretHitEventPerTarget` (dua husk berbaris → dua event, hitPos masing-masing di box-nya, weapon `bastion:laser_rifle`) dan `rocketDirectHitPostsTurretHitEvent` (roket diluncurkan ke husk → satu event dengan weapon `bastion:turret_rocket`, lalu splash tanpa event tambahan untuk husk itu).
- [ ] **Step 2: Jalankan** — Expected: dua tes baru FAIL (event tidak ada).
- [ ] **Step 3: Ganti pemanggil** sesuai tabel di atas.
- [ ] **Step 4: Jalankan semua GameTest** — Expected: semua lolos kecuali flaky yang dikenal.

### Task 3: Titik bidik tengah badan dulu

**Files:**
- Modify: `turret/targeting/TargetSelector.java` (`aimPoint`: `getBoundingBox().getCenter()` dulu, lalu `getEyePosition()`; Javadoc diperbarui), `weapon/types/GunWeapon.java` (override `sight`: `TargetSelector.firstVisible(turret, stats, eye, center)`, Javadoc: Precision Lock butuh kepala).
- Test: `gametest/TurretCombatTests.java`

- [ ] **Step 1: Tulis GameTest yang gagal:** `turretsAimAtTheBodyCentre` (Machine Gun turret, husk terlihat penuh → `type.sight(turret, husk, stats)` == pusat box) dan `gunStillAimsAtTheHead` (Gun → `getEyePosition()`).
- [ ] **Step 2: Jalankan** — Expected: `turretsAimAtTheBodyCentre` FAIL (dapat mata), `gunStillAimsAtTheHead` PASS sudah dari awal (tes penjaga, dicatat).
- [ ] **Step 3: Implementasi** urutan baru + override Gun.
- [ ] **Step 4: Jalankan semua GameTest.** Tes lama yang gagal karena menganggap bidikan mata: perbaiki asumsi tesnya (bukan kodenya) dan catat tiap tes sebagai ruling. Expected akhir: semua lolos kecuali flaky yang dikenal.

### Task 4: Versi 0.5.1, dokumen, jar

**Files:**
- Modify: `gradle.properties` (`mod_version=0.5.1`), `docs/HANDOFF.md` (§0 paragraf baru + tabel per senjata di atas), `docs/TESTING.md` (bagian baru "Turret hits → Anatomy (0.5.1)", checklist in-game), `docs/release/UPLOAD.md` (draft "## Changelog: 0.5.1": bidikan tengah badan, Gun tetap kepala, `TurretHitEvent` untuk addon).

- [ ] **Step 1:** `./gradlew build --offline > build/build.log 2>&1` — Expected: `BUILD SUCCESSFUL`, ada `build/libs/bastion-1.20.1-0.5.1.jar`.
- [ ] **Step 2:** `unzip -l build/libs/bastion-1.20.1-0.5.1.jar | grep api/TurretHitEvent` — Expected: class ada.

### Task 5: Anatomy `BastionCompat` + pencocokan lewat DamageSource

**Files (di `../Anatomy`):**
- Modify: `AnatomyState.java` (`DamageSource pendingSource`), `Anatomy.java` (`markHit` overload + daftar compat), `AnatomyDamage.java` (`pendingMatches`, konsumsi), `PartMath.java` (`throughPoint`), `AnatomyConfig.java` (`FROM_TURRETS`, key `"turrets"`, default true, komentar "Bastion turret shots hit the part they pass through."), `build.gradle` (flatDir `../Turret/build/libs` + `compileOnly fg.deobf("blank:bastion-${minecraft_version}:${bastion_jar_version}")`, komentar seperti baris TaCZ), `gradle.properties` (`bastion_jar_version=0.5.1`)
- Create: `src/main/java/dev/anatomy/compat/BastionCompat.java`
- Test: `src/test/java/dev/anatomy/PartMathTest.java`

**Interfaces:**
- Consumes: `dev.bastion.api.TurretHitEvent` (Task 1).
- Produces:
  - `Anatomy.markHit(LivingEntity entity, int part, DamageSource source, Vec3 impulseDir)` — `pendingDirect = null`, `pendingSource = source`, `pendingGunHeadshot = false`. `markHit` lama men-set `pendingSource = null`.
  - `static boolean AnatomyDamage.pendingMatches(long pendingTick, long now, @Nullable Entity direct, @Nullable Entity pendingDirect, DamageSource source, @Nullable DamageSource pendingSource)` — tick sama DAN (direct non-null == pendingDirect ATAU pendingSource non-null == source).
  - `static Vec3[] PartMath.throughPoint(Vec3 point, Vec3 direction, double reach)` — `{point - u·reach, point + u·reach}`, `u` = direction dinormalisasi.
  - `BastionCompat.register()`; listener: abaikan client/`!FROM_TURRETS`/bukan `AnatomyHost`/tanpa definisi; `reach = getBbWidth() + getBbHeight()`; `Anatomy.resolveRay` lalu `Anatomy.markHit(target, part, source, direction)`.

- [ ] **Step 1: Tulis tes JUnit yang gagal** di `PartMathTest`:
  - `turretShotThroughFrontHitsHead`: titik (10, 65.4, 15) arah (0, 0, -3) (tidak unit) → `clipRay` atas segmen `throughPoint(..., 2)` = 0 (head).
  - `turretShotFromSideHitsLeg`: titik (12, 64.5, 10.25) arah (-1, 0, 0), yaw 0 → 2 (left).
  - `pendingMatchesTurretSource`: source sama, tick sama, direct null → true.
  - `pendingIgnoresOtherSourceOrTick`: source lain → false; tick lain → false; dua-duanya null (tanpa pending) → false.
  - DamageSource untuk tes: `new DamageSource(Holder.direct(new DamageType("test", 0f)))`.
- [ ] **Step 2:** `./gradlew test --offline > build/test.log 2>&1` — Expected: compile error (`throughPoint`, `pendingMatches` belum ada).
- [ ] **Step 3: Implementasi** semua di atas; `onHurt` memakai `pendingMatches`; di cabang `PENDING`, jika `source == state.pendingSource` maka `state.pendingSource = null` setelah dipakai.
- [ ] **Step 4:** `./gradlew build --offline > build/build.log 2>&1` — Expected: `BUILD SUCCESSFUL`, semua tes lama + 4 baru lolos.

### Task 6: Xersian

**Files (di `../Xersian`):** `gradle.properties` (`bastion_jar_version=0.5.1`), `docs/TESTING.md` (bagian "Turret Bastion → body part (2026-10-31)").

- [ ] **Step 1:** `./gradlew build --offline > build/build.log 2>&1` — Expected: `BUILD SUCCESSFUL` (composite ikut meng-compile Anatomy + `BastionCompat`).
- [ ] **Step 2:** Checklist TESTING.md: `/gamemode survival`, `/xersiandev me health 500`, `/xersiandev spawn xersian:rhask 200 5`, pasang turret (Machine Gun, Sniper, Laser, Rocket); lengan/kaki putus sesuai sisi yang ditembak; dari depan kepala bisa hancur; darah kuning; roket memutus beberapa part; config `anatomy-common.toml` `turrets=false` → kembali ke torso.

### Final

- [ ] Review seluruh perubahan oleh reviewer baru (Opus 5.5, effort xhigh), dengan Review Focus di atas.
- [ ] Tulis checklist tes in-game untuk user (Bastion + Xersian).
