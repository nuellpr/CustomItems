# CustomItems

Plugin Minecraft Paper seperti ItemsAdder: custom items, blocks, mobs, dan GUI menu dengan resource pack yang di-generate otomatis + webserver built-in. Pemain tinggal join, resource pack langsung diprompt.

## Fitur

- **Custom Items** — tekstur custom via resource pack, nama + lore (MiniMessage)
- **Custom Blocks** — model custom, tekstur, nama; aman di-break (drop item custom kembali)
- **Custom Mobs** — vanilla entity dengan custom nama, HP, dan kecepatan
- **Rank Tags** — tag pixel-art di chat (seperti Better Ranks), via font glyph
- **Emojis** — ketik `:smile:` di chat, otomatis jadi gambar emoji (font glyph)
- **Custom Recipes** — crafting recipe untuk item/block custom (shaped & shapeless)
- **Custom Sounds** — mainkan suara .ogg sendiri via resource pack
- **GUI Menu** — `/ci menu` lihat semua isi, klik untuk ambil
- **Resource Pack Otomatis** — plugin generate pack.zip, serve via webserver built-in (port 8077), auto-prompt saat player join

## Kompatibilitas

Target default: **Paper 26.2 (Minecraft 26.2 "Chaos Cubed"), Java 25**. Set `pack-format` di `config.yml` sesuai versi server:

| Versi | pack-format | Java |
|---|---|---|
| 1.21.4 | 46 | 21 |
| 1.21.5 | 55 | 21 |
| 1.21.6 | 63 | 21 |
| 1.21.7 – 1.21.8 | 64 | 21 |
| 1.21.9 – 1.21.10 | 69 | 21 |
| 1.21.11 | 75 | 21 |
| 26.1 | 84 | 25 |
| 26.2 | 88 | 25 |

Salah angka tetap jalan, hanya muncul confirm prompt "incompatible" saat pemain menerima pack.

> 26.3 masih alpha di Paper, belum ada release stabil — jangan set `pack-format: 97` di server 26.2.

### Catatan custom block

Custom block adalah noteblock yang **item di tangan**-nya pakai model kustom
(`assets/minecraft/items/note_block.json` → `select` pada `minecraft:custom_model_data`).
Blockstate vanilla `note_block.json` **tidak** di-override, jadi blok yang sudah dipasang
selalu tampil seperti noteblock biasa. Merender block yang benar-benar berbeda butuh
mekanisme lain (mis. data-driven block via plugin), di luar cakupan plugin ini.

## Install

1. Download `CustomItems-0.6.0.jar` dari [Releases](../../releases), taruh di folder `plugins/`
2. Start server → folder `plugins/CustomItems/` tergenerate
3. Untuk server online: **wajib** set `external-url` di `config.yml` ke IP/URL publik (mis. `http://play.myserver.com:8077`) dan buka port-nya. Kalau dibiarkan kosong, plugin memakai bind IP server dan hanya berfungsi untuk pemain di mesin yang sama — URL `http://0.0.0.0:8077` tidak bisa di-download client.
4. `/ci reload`

## Perubahan 0.3.0 (perbaikan bug)

| Bug | Gejala | Perbaikan |
|---|---|---|
| Noteblock vanilla jadi tak terlihat | Resource pack menimpa `blockstates/note_block.json` dengan 1150 varian `instrument=...,note=...`, padahal vanilla cuma punya satu variant `""` — semua key `instrument=` tidak pernah match, jadi model hilang | Override dihapus total. Blockstate vanilla dibiarkan utuh; identitas custom lewat `items/note_block.json` |
| Seluruh teks server rusak jadi kotak | `font/default.json` ditulis ulang tanpa referensi font vanilla, padahal resource pack **mengganti** file itu, bukan menggabungkannya | Referensi `include/space`, `include/default`, `include/unifont` ditambahkan kembali |
| Custom block tidak bisa di-break | Block identity disimpan ke `TileState`, tapi noteblock **tidak punya block entity** — PDC tidak pernah tersimpan, jadi block tidak dikenali dan drop item gagal | Identity dibaca dari blockstate `instrument+note` |
| Pack URL `http://0.0.0.0:8077` | Client tidak bisa mengunduh pack | `0.0.0.0`/`::` diganti loopback + warning, trailing slash dan `/pack.zip` ganda dinormalkan |
| `/ci menu` crash | `createInventory` melempar error di atas 54 slot saat item+block > 54 | Ukuran dibatasi 54 + warning |
| Build gagal tanpa Gradle | `gradle` tidak ada di PATH dan repo tidak punya wrapper | Lihat "Build dari source" — bisa pakai `javac` langsung |

## Perubahan 0.6.0 (fix race, config, dan chat)

| Bug | Gejala | Perbaikan |
|---|---|---|
| `/ci reload` bisa crash saat ada chat | `AsyncChatEvent` mengiterasi `LinkedHashMap` rank/emoji dari thread chat, sementara `load()` melakukan `clear()` + refill dari thread utama → `ConcurrentModificationException` | `Ranks`/`Emojis` membangun map baru lalu **menukar seluruhnya** (volatile). Pembaca selalu melihat snapshot utuh, tidak pernah setengah terisi |
| `type: BOAT` di `mobs.yml` bikin crash saat spawn | `EntityType.valueOf("BOAT")` sukses, lalu `(LivingEntity)` cast melempar `ClassCastException` — jauh setelah typo-nya dibuat | Type divalidasi di `load()`: wajib `isSpawnable()` + subclass `LivingEntity`, kalau tidak di-skip + warning |
| Chat kehilangan bold/italic/warna | Emoji replacement melakukan roundtrip `PlainTextComponentSerializer`, meratakan seluruh component | `Component.replaceText` men-edit node teks di tempatnya; format pemain tetap utuh |
| `/ci reload` abaikan perubahan `config.yml` | `reloadConfig()` tidak pernah dipanggil, jadi `port`/`external-url`/`pack-format` yang diedit tidak terpakai | `reloadConfig()` dipanggil sebelum `loadItems()` |
| Pack tidak dikirim ke player yang sudah online | Pack hanya dikirim di `PlayerJoinEvent`, jadi pemain yang online saat reload memakai texture lama | Setelah rebuild, pack dikirim ulang ke semua player online + pesan menyebut jumlahnya |
| `/ci menu` membekukan inventory pemain | `setCancelled(true)` membatalkan klik di **seluruh** view, termasuk inventory sendiri — shift-click buat rapiin hotbar mati | Hanya klik di inventory menu yang dibatalkan |
| Attribute mob yang hilang bikin NPE | `getAttribute(...)` bisa `null` untuk sebagian tipe mob | Null-guard, jatuh ke nilai vanilla |

## Perubahan 0.5.0 (fix dupe & overflow glyph)

| Bug | Gejala | Perbaikan |
|---|---|---|
| Custom block gratis tanpa batas | Identitas block dibaca dari blockstate noteblock. Tapi noteblock vanilla bisa di-*right-click* sampai jadi `instrument+note` yang sama persis, lalu di-break → dapat item custom. Cukup 1 noteblock vanilla per state | Lokasi block yang dipasang dari item custom dicatat di `plugins/CustomItems/placed-blocks.txt`. Item custom cuma drop kalau lokasinya tercatat di sana; noteblock vanilla di lokasi yang tidak tercatat jatuh ke drop vanilla |
| Glyph rank/emoji meluber ke huruf CJK | `\uE000`+ (rank) dan `\uE100`+ (emoji) hanya menyisakan 256 codepoint. Rank ke-257 mendarat di `\u0F00` (CJK) dan **semua** glyph setelahnya bergeser | Dibatasi 256 entri; sisanya diabaikan + warning di console |
| Build error di Paper 26.x | `RecipeChoice.ExactChoice(ItemStack)` deprecated-for-removal | Pakai static factory `RecipeChoice.exactChoice(...)` |

### Aturan custom block

Karena identitas custom block tersimpan di blockstate noteblock, ada dua aturan operasional:

1. **Jangan ganti `instrument`/`note` di `blocks.yml` setelah pemain membangun block itu.** Blok lama ikut berubah tampilan karena identitasnya = posisi file, bukan ID.
2. **Hapus `placed-blocks.txt` hanya saat server kosong.** File itu yang membedakan block kita dari noteblock vanilla; menghapusnya = semua block custom jadi tidak bisa di-drop.

Kalau file ini hilang, `onPlace` akan menandai ulang begitu pemain menaruh block lagi — jadi file di-backup bareng `plugins/CustomItems/`.

## Konfigurasi

### items.yml

```yaml
items:
  ruby_sword:
    base: DIAMOND_SWORD      # item vanilla yang jadi dasar
    texture: ruby_sword.png  # PNG di plugins/CustomItems/textures/
    name: "<red>Ruby Sword"
    lore: ["<gray>Pedang legendaris"]
```

### blocks.yml

```yaml
blocks:
  marble_block:
    texture: marble_block.png
    name: "<white>Marble Block"
```

### mobs.yml

```yaml
mobs:
  guardian_zombie:
    type: ZOMBIE
    name: "<dark_purple>Guardian"
    health: 40.0
    speed: 0.3
```

### ranks.yml

```yaml
ranks:
  admin:
    texture: admin.png   # pixel art transparan, tampil sebelum nama di chat
    ascent: 8            # posisi vertikal glyph (8 = sejajar teks)
  vip:
    texture: vip.png
```

Tag diaktifkan via permission `ci.rank.<key>` (mis. `ci.rank.admin`). Rank pertama yang dimiliki player yang dipakai (urutan sesuai file = prioritas).

### emojis.yml

```yaml
emojis:
  smile:
    texture: smile.png   # tampil saat player ketik :smile: di chat
    ascent: 8
```

Ketik `:smile:` (nama key dibatasi `:`) di chat → otomatis diganti glyph emoji.

### recipes.yml

```yaml
recipes:
  ruby_sword:
    type: shaped
    result: ruby_sword
    pattern: [" D ", " D ", " S "]
    ingredients:
      D: DIAMOND
      S: STICK
  marble_block:
    type: shapeless
    result: marble_block
    ingredients: [STONE, CLAY_BALL]
```

Ingredient bisa material vanilla ATAU key item/block custom.

### sounds.yml

```yaml
sounds:
  fanfare:
    file: fanfare.ogg   # hanya .ogg (MP3/WAV convert dulu via Audacity/ffmpeg)
    volume: 1.0
    pitch: 1.0
```

File .ogg ditaruh di `plugins/CustomItems/sounds/`, mainkan via `/ci play fanfare`.

Texture PNG ditaruh di `plugins/CustomItems/textures/`, lalu `/ci reload` — pack.zip dibangun ulang otomatis.

## Perintah

| Perintah | Fungsi |
|---|---|
| `/ci give <item> [player]` | Beri item custom |
| `/ci spawn <mob>` | Spawn mob custom |
| `/ci play <sound>` | Mainkan sound custom |
| `/ci menu` | Buka GUI semua isi |
| `/ci reload` | Reload `config.yml` + semua yml, rebuild pack, kirim ulang ke player online |
| `/ci pack` | Kirim ulang resource pack ke player online tanpa rebuild |

Semua butuh permission `ci.admin` (default: op).

## Tools

Ada di `tools/`, semuanya PowerShell dan tanpa dependency.

| Tool | Fungsi |
|---|---|
| `Test-Pack.ps1` | **Validasi pack sebelum pemain mengunduhnya.** Menangkap 3 kegagalan senyap: varian noteblock kurang (block tak terlihat), `font/default.json` kehilangan referensi vanilla (teks jadi kotak), dan texture yang dipakai config tapi tidak ikut ter-pack |
| `GenerateTextures.ps1` | Bikin placeholder pixel-art (`ruby_sword`, `marble_block`, `smile`) sesuai gaya rank tag yang ada |
| `Setup-TestServer.ps1` | Siapkan server Paper sekali-pakai di folder terpisah untuk uji end-to-end. **Tidak menyentuh server produksi** |
| `rcon.ps1` | Client RCON minimal untuk menjalankan perintah `/ci` secara scripted |

Cek pack yang sedang jalan:

```powershell
.\tools\Test-Pack.ps1 -Pack "..\..\plugins\CustomItems\pack.zip" -ConfigDir "..\..\plugins\CustomItems"
```

Kode keluar `0` = aman dibagikan ke pemain, `1` = ada masalah. Jalankan ini setelah `/ci reload` dan sebelum mengumumkan item baru ke pemain.

## Build dari source

```bash
gradle build
```

Butuh Gradle 9.x + Java 25. Hasil di `build/libs/CustomItems-0.6.0.jar`.

Kalau Gradle tidak terpasang (repo ini belum punya wrapper), bisa kompilasi langsung dengan JDK 25:

```bash
javac -encoding UTF-8 --release 25 -cp paper-api.jar -d build/classes $(find src/main/java -name '*.java')
```

`paper-api` 26.2 bisa diambil dari `libraries/io/papermc/paper/paper-api/...` milik server Paper, dari cache Gradle, atau dari `https://repo.papermc.io/repository/maven-public/` (versi `26.2.build.129-stable`).

Kompilasi butuh `paper-api.jar` **plus** dependency-nya di classpath: `org.jetbrains:annotations`,
`com.google.guava:guava` (dipakai `Material.getItemAttributes`), dan library adventure dari
`META-INF/libraries/` di dalam `paper-server.jar`. Tanpa `guava`/`annotations` javac akan
gagal dengan "cannot find symbol" meskipun paper-api-nya benar.

## Catatan teknis

- Items: `custom_model_data` string + item model definition format 1.21.4+ (`select`)
- Blocks: noteblock method (instrument + note unik per block). Identitas block dibaca dari blockstate noteblock (`instrument` + `note`), **bukan** PDC — noteblock tidak punya block entity. Konsekuensinya: jangan ubah `instrument`/`note` sebuah block di `blocks.yml` setelah pemain membangun dengannya — block lama akan berubah tampilan
- Blocks: lokasi yang sudah di-*mark* disimpan di `plugins/CustomItems/placed-blocks.txt`. `onBreak` menolak drop item custom kalau lokasi tidak ada di sana, jadi noteblock vanilla yang di-*right-click* jadi `instrument+note` yang sama tidak bisa di-*exploit*
- `blockstates/note_block.json` **tidak pernah** di-override. Vanilla punya satu variant `""` tanpa key `instrument=`, jadi override berbentuk `instrument=...,note=...` hanya bisa menghapus model (blok tak terlihat) — bukan memberi tekstur baru. 26.2 masih bentuk yang sama, jadi ini bukan regresi versi
- Recipes: ingredient custom dicocokkan via `RecipeChoice.exactChoice(ItemStack)` (constructor `ExactChoice(ItemStack)` deprecated-for-removal di Paper 26.x)
- Mobs: vanilla model + atribut custom (bukan model 3D custom seperti ModelEngine)
- Rank tags: bitmap font glyph (`\uE000`+) di `font/default.json`, dengan referensi font vanilla dipertahankan agar teks biasa tetap tampil. Maksimal **256 rank** (U+E000–U+E0FF); lebih dari itu diabaikan + warning
- Emojis: glyph range terpisah (`\uE100`+), trigger `:key:` di chat. Maksimal **256 emoji** (U+E100–U+E1FF)
- Font bitmap: PNG harus setinggi nilai `ascent` (default 8 piksel) dan disusun horizontal, satu glyph per slot sesuai urutan `chars` — file terlalu tinggi/lebar membuat semua glyph bergeser. Texture yang tidak ada **tidak** lagi ditulis sebagai provider (dulu jadi referensi menggantung), tapi nomor glyph tetap selaras
- Sounds: `sounds.json` custom, key namespace `custom.<nama>`
- Webserver: `com.sun.net.httpserver`, serve `/pack.zip`
