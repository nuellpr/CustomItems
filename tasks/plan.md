# Implementation Plan: Perbaikan Temuan Audit CustomItems 0.8.5

## Overview

Rencana ini mengubah 13 temuan audit `RISET-Plugin-Mirip-ItemsAdder.md` / laporan audit menjadi 10 task kecil yang bisa diverifikasi satu per satu. Tidak ada fitur baru. Tidak ada build system, framework, atau dependency baru — proyek ini sengaja JDK-only dan rencana ini menjaga itu.

Beban yang diasumsikan: satu server Paper 26.2 dengan puluhan sampai ratusan pemain, chat async, chunk sering load/unload, dan alur impor ItemsAdder yang dipakai berulang kali.

## Architecture Decisions

- **Semua perbaikan tetap di file yang sudah ada.** Tidak ada modul, interface, atau factory baru untuk satu implementasi.
- **Assertion test ditempatkan di `tools/verify/`, bukan `build/verify/`.** Folder `build/` di-ignore git, sehingga verifier di sana hilang saat build ulang dan tidak pernah dijalankan. Dua verifier lama dipindahkan (bukan ditulis ulang dari nol) dengan logika yang sama + `throw` saat gagal.
- **`test-pack` menjadi gerbang tunggal.** Validasi config, file `imports/`, dan self-test dijalankan dari satu perintah yang sama agar tidak ada pemeriksaan yang bisa dilewatkan.
- **Kompatibilitas data didahulukan.** Format `placed-blocks.txt` lama (nama world) tetap dibaca; penulisan baru memakai UUID.
- **Rank dihitung di main thread**, memakai pola swap `volatile` yang sudah dipakai `Ranks`, bukan menambah pengecekan permission di thread chat.
- **Perbaikan model item (Task 9) didahului pembuktian runtime.** Kalau `setItemMeta` ternyata tidak menimpa komponen, task berubah jadi penambah test saja — bukan perubahan kode.

## Task List

### Phase 1 — Tooling dipercaya (fail fast)

- [ ] Task 1: `test-pack` memeriksa `imports/*.yml` dan menerima indentasi YAML apa pun
- [ ] Task 2: Verifier di `tools/verify/` jadi assertion dan dipanggil `test-pack`
- [ ] Task 3: Default `setup-server` memakai JAR terbaru, tanpa menebak Paper jar

### Checkpoint: Tooling
- [ ] `javac` tool lolos
- [ ] `test-pack` gagal (exit != 0) ketika sengaja ada tekstur impor yang hilang
- [ ] `test-pack` tetap PASS pada setup ZIP bersih yang sudah di-regenerate

### Phase 2 — Perilaku gameplay benar

- [ ] Task 4: HP mob kustom tidak pulih saat chunk dimuat ulang
- [ ] Task 5: Gagal build pack membuat plugin gagal start dengan pesan jelas
- [ ] Task 6: Rank di-resolve di main thread, bukan di `AsyncChatEvent`

### Checkpoint: Gameplay
- [ ] Plugin load normal di server uji, 25 item / 1 mob / 1 rank seperti sebelumnya
- [ ] Mob dengan HP 1 tetap HP 1 setelah pemain pergi >128 blok dan kembali
- [ ] Chat dengan rank tag masih tampil, `/ci reload` tidak error

### Phase 3 — Penyimpanan dan detail

- [ ] Task 7: `placed-blocks.txt` memakai world UUID dan masih membaca format lama
- [ ] Task 8: Drop furniture tidak bergantung pada entity masih valid
- [ ] Task 9: Komponen item tidak hilang saat atribut ditambahkan (setelah dibuktikan)
- [ ] Task 10: Client RCON mencocokkan id response dengan id perintah

### Checkpoint: Complete
- [ ] Semua acceptance criteria terpenuhi
- [ ] `test-pack` PASS 0 warning pada pack hasil server uji
- [ ] Tidak ada file workspace user yang berubah (`.mc-test/`, `forgeai.html`, `spaceedu-earth.html`, `RISET-*.md`, `err.log`, `out.log`)
- [ ] Siap direview manusia sebelum diimplementasikan

## Risks and Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Task 7 mengubah format file, blok lama jadi hilang | High | Task 7 membaca dua format; tulis UUID hanya untuk entri baru; verifikasi dengan file lama di server uji |
| Task 6 cache rank jadi basi setelah permission berubah di runtime | Medium | Cache di-refresh di `PlayerJoinEvent` dan `/ci reload` (sudah ada hook keduanya) |
| Task 2 assertion terlalu ketat sehingga `test-pack` gagal di konfigurasi sah | Medium | Self-test hanya memeriksa logika internal deterministik, bukan isi config user |
| Task 9 ternyata bukan bug | Low | Task 9 berhenti di pembuktian runtime; kalau lolos, cukup tambah test, jangan ubah `Items.java` |
| Perubahan menyentuh plugin saat server produksi aktif | Medium | Semua uji di server sementara `Temp/opencode/mc-test-*`, bukan `.mc-test/` milik user |

## Open Questions

- Apakah kamu ingin semua 10 task dikerjakan, atau hanya 3 temuan `Must fix` (Task 1, 3, 4)?
- Untuk Task 9, apakah boleh aku menambah satu item uji dengan `damage:` di server uji untuk membuktikan risikonya sebelum memutuskan?
- Lokasi `tasks/` aku pilih di dalam proyek plugin (`CustomItems/tasks/`) karena di situlah kode yang diubah berada. Kalau kamu mau di root workspace, ini perlu dipindah sebelum implementasi.
