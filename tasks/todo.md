# Task List — Perbaikan Temuan Audit CustomItems 0.8.5

Rencana induk: `tasks/plan.md`. Rencana sudah disetujui; Task 1–10 sudah diimplementasikan. Status verifikasi ringkas ada di bawah.

## Status verifikasi (2026-11-18)

- Task 1–3: RED/GREEN via `test-pack`, `setup-server` tanpa flag sukses memakai JAR versi tertinggi.
- Task 4–10: build sukses; `test-pack` PASS 0 warning dengan 7 verifier assertion di `tools/verify/`; smoke test server Paper 26.2 normal dan RCON `list`/`stop` sukses.
- Task 5: RED/GREEN runtime dengan direktori `pack.zip` → plugin benar-benar disable dengan pesan jelas.
- Task 7: RED/GREEN runtime dengan marker legacy + UTF-8 BOM → tidak ada warning, marker terbaca.
- Task 8, 9: verifier source assertion + build; uji runtime khusus (entity dihapus plugin lain, `/ci give` item dengan damage) belum dilakukan.

---

## Task 1: `test-pack` memeriksa `imports/*.yml` dan menerima indentasi YAML apa pun

**Description:** Perluas `checkConfig` agar membaca semua file `.yml` di folder `imports/` dengan parser yang sama seperti `items.yml`, dan longgarkan regex indentasi dua spasi agar konfigurasi dengan indent empat spasi tetap terdeteksi.

**Acceptance criteria:**
- [ ] `test-pack` melaporkan `bad` ketika sebuah key di `imports/*.yml` tidak punya pasangan model/tekstur di pack.
- [ ] `test-pack` tetap PASS untuk setup ZIP bersih yang sudah di-regenerate (24 item impor, 0 duplikat).
- [ ] Key YAML dengan indent 4 spasi ikut divalidasi, bukan diabaikan.

**Verification:**
- [ ] Tests pass: `javac -d build/tool-classes tools/CustomItemsTools.java`
- [ ] Manual check: jalankan `test-pack` pada config uji dengan satu tekstur impor sengaja dihapus → harus gagal; lalu kembalikan → PASS 0 warning.

**Dependencies:** None

**Files likely touched:**
- `tools/CustomItemsTools.java`

**Estimated scope:** S

---

## Task 2: Verifier di `tools/verify/` jadi assertion dan dipanggil `test-pack`

**Description:** Pindahkan dua verifier dari `build/verify/` (di-ignore git) ke `tools/verify/`, ubah `System.out.println` menjadi assertion yang melempar saat gagal, lalu panggil keduanya dari `test-pack`.

**Acceptance criteria:**
- [ ] `VerifyPack` dan `VerifyInstrumentRoundTrip` menghasilkan exit code bukan nol saat hasilnya salah.
- [ ] `test-pack` menjalankan keduanya otomatis dan menggagalkan perintah bila assertion gagal.
- [ ] Self-test hanya memeriksa logika internal deterministik, tidak bergantung pada isi config user.

**Verification:**
- [ ] Tests pass: `javac -d build/tool-classes tools/CustomItemsTools.java tools/verify/*.java`
- [ ] Manual check: `test-pack` pada pack hasil server uji tetap PASS; ubah satu assertion jadi salah sementara → perintah gagal.

**Dependencies:** Task 1

**Files likely touched:**
- `tools/CustomItemsTools.java`
- `tools/verify/VerifyPack.java`
- `tools/verify/VerifyInstrumentRoundTrip.java`

**Estimated scope:** M

---

## Task 3: Default `setup-server` memakai JAR terbaru, tanpa menebak Paper jar

**Description:** Ganti default `releases/CustomItems-0.4.0.jar` dengan pemilih JAR versi tertinggi dari `build/libs/` dan `releases/`, dan batasi `findPaperJar` hanya ke folder `.mc-test` atau wajibkan `--paper-jar`.

**Acceptance criteria:**
- [ ] `setup-server` tanpa `--jar` memilih `CustomItems-0.8.5.jar`, bukan 0.4.0.
- [ ] Tidak ada JAR kandidat → error dengan pesan yang menyebut `--jar`, bukan diam-diam memakai versi lama.
- [ ] `setup-server` tanpa `--paper-jar` hanya mencari di `.mc-test`, bukan semua folder Desktop.

**Verification:**
- [ ] Tests pass: `javac -d build/tool-classes tools/CustomItemsTools.java`
- [ ] Manual check: jalankan `setup-server --root <temp>` tanpa `--jar`, cek log server menampilkan CustomItems 0.8.5.

**Dependencies:** None (dikerjakan di Phase 1 agar checkpoint tooling mencakupnya)

**Files likely touched:**
- `tools/CustomItemsTools.java`

**Estimated scope:** S

---

## Checkpoint: Tooling

- [ ] `javac` tool lolos
- [ ] `test-pack` gagal ketika sengaja ada tekstur impor yang hilang
- [ ] `test-pack` tetap PASS pada setup ZIP bersih
- [ ] `setup-server` memakai plugin versi 0.8.5

---

## Task 4: HP mob kustom tidak pulih saat chunk dimuat ulang

**Description:** Pisahkan `apply()` di `Mobs.java` menjadi `applyOnSpawn()` yang men-set current health dan `reapply()` yang hanya memasang atribut + nama; `onChunkLoad` memakai `reapply()`.

**Acceptance criteria:**
- [ ] Membuka ulang chunk tidak mengubah HP saat ini mob kustom.
- [ ] Mob baru yang di-spawn tetap mendapat HP sesuai config.
- [ ] Atribut kecepatan dan nama kustom tetap dipasang ulang setelah chunk load.

**Verification:**
- [ ] Build succeeds: `java tools/CustomItemsTools.java build --libraries <paper-libraries>`
- [ ] Manual check: guardian zombie 40 HP dibiarkan 1 HP, pemain pergi >128 blok dan kembali → HP masih 1.

**Dependencies:** None

**Files likely touched:**
- `src/main/java/com/chatbiasa/customitems/Mobs.java`

**Estimated scope:** S

---

## Task 5: Gagal build pack membuat plugin gagal start dengan pesan jelas

**Description:** Di `onEnable`, kalau `pack.build()` melempar, disable plugin dengan pesan yang menyebut pengaturan yang salah, daripada tetap hidup dengan `packFile` kosong.

**Acceptance criteria:**
- [ ] `pack-format` invalid membuat plugin disable dengan log yang menyebut penyebabnya.
- [ ] Konfigurasi normal tetap load seperti sebelumnya (25 item, 1 mob, 1 rank).
- [ ] Tidak ada player yang join tanpa pack tanpa ada peringatan di log.

**Verification:**
- [ ] Build succeeds: `java tools/CustomItemsTools.java build --libraries <paper-libraries>`
- [ ] Manual check: server uji dengan `pack-format: -1` → plugin disabled + pesan jelas.

**Dependencies:** None

**Files likely touched:**
- `src/main/java/com/chatbiasa/customitems/CustomItemsPlugin.java`

**Estimated scope:** S

---

## Task 6: Rank di-resolve di main thread, bukan di `AsyncChatEvent`

**Description:** Simpan rank hasil resolve per-UUID di `Ranks` dengan pola `volatile` yang sudah ada, isi dari `PlayerJoinEvent` dan `/ci reload`, lalu `ChatListener` hanya membaca cache tanpa memanggil `hasPermission`.

**Acceptance criteria:**
- [ ] `AsyncChatEvent` tidak lagi memanggil API permission Bukkit.
- [ ] Tag rank tampil benar setelah join dan setelah `/ci reload`.
- [ ] Pemain tanpa rank tetap mengirim chat normal dengan emoji utuh.

**Verification:**
- [ ] Build succeeds: `java tools/CustomItemsTools.java build --libraries <paper-libraries>`
- [ ] Manual check: chat sebagai pemain ber-rank dan tanpa rank, reload saat chat aktif, tidak ada warning thread di log.

**Dependencies:** None

**Files likely touched:**
- `src/main/java/com/chatbiasa/customitems/Ranks.java`
- `src/main/java/com/chatbiasa/customitems/ChatListener.java`
- `src/main/java/com/chatbiasa/customitems/JoinListener.java`

**Estimated scope:** M

---

## Checkpoint: Gameplay

- [ ] Plugin load normal di server uji
- [ ] HP mob tetap 1 setelah chunk reload
- [ ] Plugin disable dengan pesan jelas saat pack gagal dibangun
- [ ] Chat rank dan emoji masih berfungsi setelah reload

---

## Task 7: `placed-blocks.txt` memakai world UUID dan masih membaca format lama

**Description:** Ubah `coord()` agar menulis UUID world, dan `loadPlaced()` agar menerima entri lama berbentuk nama world lalu mengonversinya ke UUID dan menandai file dirty.

**Acceptance criteria:**
- [ ] Entri baru di `placed-blocks.txt` berisi UUID world, bukan nama.
- [ ] File lama berisi `world,x,y,z` tetap terbaca dan ditulis ulang sebagai UUID saat flush.
- [ ] World di-rename tidak membuat marker custom block hilang.

**Verification:**
- [ ] Build succeeds: `java tools/CustomItemsTools.java build --libraries <paper-libraries>`
- [ ] Manual check: pasang marble block, restart server dengan file format lama, blok masih dikenali (tidak drop vanilla).

**Dependencies:** None

**Files likely touched:**
- `src/main/java/com/chatbiasa/customitems/Blocks.java`

**Estimated scope:** S

---

## Task 8: Drop furniture tidak bergantung pada entity masih valid

**Description:** Di `onAttack`, simpan item key dan lokasi drop ke variabel lokal sebelum task 1 tick, dan jangan return saat entity tidak valid — tetap jatuhkan item dari `item` yang sudah terbaca. Sekaligus hapus sisa `pendingBreaks` di `PlayerQuitEvent` jika masih dipertahankan.

**Acceptance criteria:**
- [ ] Furniture yang dihapus plugin lain di tick yang sama tetap menjatuhkan item.
- [ ] Tidak ada drop ganda saat dua attack terjadi sebelum task pertama jalan (dijaga `pendingBreaks`).
- [ ] Mode creative tetap tidak menjatuhkan item.

**Verification:**
- [ ] Build succeeds: `java tools/CustomItemsTools.java build --libraries <paper-libraries>`
- [ ] Manual check: pukul furniture, hapus entity dari plugin lain secara sengaja → item tetap jatuh.

**Dependencies:** None

**Files likely touched:**
- `src/main/java/com/chatbiasa/customitems/FurnitureListener.java`

**Estimated scope:** M

---

## Task 9: Komponen item tidak hilang saat atribut ditambahkan

**Description:** Buktikan dulu di server uji apakah `setItemMeta` di `Items.stack()` benar-benar menimpa `ITEM_MODEL`/PDC; kalau ya, pindahkan pemanggilan `setAttributeModifiers` ke sebelum `setItemMeta`, atau tulis ulang komponen setelahnya. Kalau tidak, cukup tambah assertion.

**Acceptance criteria:**
- [ ] Ada bukti runtime dari item dengan `damage:` + `model:` bahwa komponen model dan PDC masih ada setelah stack dibuat.
- [ ] Kalau ternyata hilang, perbaikan membuat keduanya bertahan.
- [ ] Tidak ada perubahan kode bila risiko tidak terkonfirmasi.

**Verification:**
- [ ] Build succeeds: `java tools/CustomItemsTools.java build --libraries <paper-libraries>`
- [ ] Manual check: `/ci give <player> <item>` untuk item dengan damage bonus → model kustom tampil dan atribut tambahan terpasang.

**Dependencies:** None

**Files likely touched:**
- `src/main/java/com/chatbiasa/customitems/Items.java`
- `tools/verify/VerifyPack.java` (hanya bila perlu assertion)

**Estimated scope:** M

---

## Task 10: Client RCON mencocokkan id response dengan id perintah

**Description:** Simpan id paket perintah auth dan perintah berikutnya, lalu pastikan paket response yang dibaca punya id yang sama sebelum dipakai.

**Acceptance criteria:**
- [ ] Response dengan id berbeda diabaikan atau menghasilkan error, bukan dipakai sebagai hasil.
- [ ] `ci reload` lewat RCON tetap sukses seperti sekarang.

**Verification:**
- [ ] Tests pass: `javac -d build/tool-classes tools/CustomItemsTools.java`
- [ ] Manual check: jalankan `ci reload` dua kali berturut-turut pada server uji → kedua response terbaca benar.

**Dependencies:** None

**Files likely touched:**
- `tools/CustomItemsTools.java`

**Estimated scope:** XS

---

## Checkpoint: Complete

- [ ] Semua acceptance criteria di atas tercentang
- [ ] `test-pack` PASS 0 warning pada pack hasil server uji
- [ ] Setup ZIP `build/CustomItems-0.8.5-itemsadder-setup.zip` masih 0 duplikat
- [ ] Workspace user tidak berubah
- [ ] Rencana disetujui manusia sebelum implementasi dimulai
