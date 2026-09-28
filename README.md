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

Paper 1.21.4 – 26.2 (Java 21+). Set `pack-format` di `config.yml` sesuai versi server:

| Versi | pack-format |
|---|---|
| 1.21.4 – 1.21.8 | 46 |
| 1.21.9 – 1.21.10 | 69 |
| 1.21.11 | 75 |
| 26.1 | 84 |
| 26.2 | 88 |

Salah angka tetap jalan, hanya muncul confirm prompt "incompatible" saat pemain menerima pack.

## Install

1. Download `CustomItems-0.3.0.jar` dari [Releases](../../releases), taruh di folder `plugins/`
2. Start server → folder `plugins/CustomItems/` tergenerate
3. Untuk server online: **wajib** set `external-url` di `config.yml` ke IP/URL publik (mis. `http://play.myserver.com:8077`) dan buka port-nya. Kalau dibiarkan kosong, plugin memakai bind IP server dan hanya berfungsi untuk pemain di mesin yang sama — URL `http://0.0.0.0:8077` tidak bisa di-download client.
4. `/ci reload`

## Perubahan 0.3.0 (perbaikan bug)

| Bug | Gejala | Perbaikan |
|---|---|---|
| Noteblock vanilla jadi tak terlihat | Resource pack hanya menulis 10 dari 23 instrument noteblock, padahal file `blockstates/note_block.json` menimpa vanilla — semua instrument lain (termasuk `harp`, default noteblock) kehilangan model | Semua 23 instrument × 25 note × 2 powered = 1150 varian ditulis |
| Seluruh teks server rusak jadi kotak | `font/default.json` ditulis ulang tanpa referensi font vanilla, padahal resource pack **mengganti** file itu, bukan menggabungkannya | Referensi `include/space`, `include/default`, `include/unifont` ditambahkan kembali |
| Custom block tidak bisa di-break | Block identity disimpan ke `TileState`, tapi noteblock **tidak punya block entity** — PDC tidak pernah tersimpan, jadi block tidak dikenali dan drop item gagal | Identity dibaca dari blockstate `instrument+note` |
| Pack URL `http://0.0.0.0:8077` | Client tidak bisa mengunduh pack | `0.0.0.0`/`::` diganti loopback + warning, trailing slash dan `/pack.zip` ganda dinormalkan |
| `/ci menu` crash | `createInventory` melempar error di atas 54 slot saat item+block > 54 | Ukuran dibatasi 54 + warning |
| Build gagal tanpa Gradle | `gradle` tidak ada di PATH dan repo tidak punya wrapper | Lihat "Build dari source" — bisa pakai `javac` langsung |

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
| `/ci reload` | Reload config + rebuild pack |

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

Butuh Gradle 9.x + Java 21+. Hasil di `build/libs/CustomItems-0.3.0.jar`.

Kalau Gradle tidak terpasang (repo ini belum punya wrapper), bisa kompilasi langsung dengan JDK 21+:

```bash
javac -encoding UTF-8 --release 21 -cp paper-api.jar -d build/classes $(find src/main/java -name '*.java')
```

`paper-api` bisa diambil dari `libraries/io/papermc/paper/paper-api/...` milik server Paper, atau dari cache Gradle.

## Catatan teknis

- Items: `custom_model_data` string + item model definition format 1.21.4+ (`select`)
- Blocks: noteblock method (instrument + note unik per block). Noteblock **tidak punya block entity**, jadi identitas block disimpan di blockstate-nya, bukan PDC. Konsekuensinya: jangan ubah `instrument`/`note` sebuah block di `blocks.yml` setelah pemain membangun dengannya — block lama akan berubah tampilan
- Mobs: vanilla model + atribut custom (bukan model 3D custom seperti ModelEngine)
- Rank tags: bitmap font glyph (`\uE000`+) di `font/default.json`, dengan referensi font vanilla dipertahankan agar teks biasa tetap tampil
- Emojis: glyph range terpisah (`\uE100`+), trigger `:key:` di chat
- Font bitmap: PNG harus setinggi nilai `ascent` (default 8 piksel) dan disusun horizontal, satu glyph per slot sesuai urutan `chars` — file terlalu tinggi/lebar membuat semua glyph bergeser. Texture yang tidak ada **tidak** lagi ditulis sebagai provider (dulu jadi referensi menggantung), tapi nomor glyph tetap selaras
- Recipes: Bukkit native, ingredient custom dicocokkan via PDC (ExactChoice)
- Sounds: `sounds.json` custom, key namespace `custom.<nama>`
- Webserver: `com.sun.net.httpserver`, serve `/pack.zip`
