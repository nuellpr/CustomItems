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

Custom block adalah **noteblock sungguhan** yang me-*stamp* `instrument` + `note` miliknya sendiri,
lalu resource pack memetakan state itu ke model kustom lewat
`assets/minecraft/blockstates/note_block.json` → `"instrument=...,note=...,powered=..."`.
Mekanisme ini sama dengan Oraxen dan ItemsAdder, dan hasilnya: blok yang dipasang benar-benar
**ter-render sebagai model kustom**, bukan cuma item di tangan.

Tiga hal yang perlu diketahui:

1. **Slot terbatas 250.** Identitas blok = `instrument` + `note`, jadi hanya 10 instrument × 25 note
   yang bisa dipakai. Lebih dari itu akan menimpa identitas blok sebelumnya, jadi plugin berhenti
   memuat dan memberi warning.
2. **Noteblock vanilla bisa "meniru" tampilannya.** Pemain bisa me-*right-click* noteblock biasa
   sampai `instrument` + `note`-nya kebetulan sama, dan blok itu akan **terlihat** seperti block
   custom kita. Ini cuma menipu mata — `placed-blocks.txt` tetap mengatur drop, jadi tidak ada item
   custom yang gratis. Trade-off yang sama dimiliki Oraxen/ItemsAdder.
3. **Block yang dipasang tidak bisa di-cycle.** *Right-click* pada block custom di-cancel, jadi
   `instrument`/`note`-nya tidak berubah.

## Install

1. Download `CustomItems-0.8.3.jar` dari [Releases](../../releases), taruh di folder `plugins/`
2. Start server → folder `plugins/CustomItems/` tergenerate; tekstur bawaan Ruby Sword, Marble Block, admin, dan smile ikut disalin otomatis ke `textures/`
3. Untuk server online: **wajib** set `external-url` di `config.yml` ke IP/URL publik (mis. `http://play.myserver.com:8077`) dan buka port-nya. Kalau dibiarkan kosong, plugin memakai bind IP server dan hanya berfungsi untuk pemain di mesin yang sama — URL `http://0.0.0.0:8077` tidak bisa di-download client.
4. `/ci reload`

## Perubahan 0.8.3

| Perubahan | Detail |
|---|---|
| Import pack Oraxen | Java tool dapat mengimpor model, tekstur, tekstur animasi, dan daftar item statis dari arsip Oraxen ke folder data CustomItems. Item hasil impor dimuat terpisah dan tidak menimpa `items.yml`. |
| Model item eksternal | `items.yml` menerima `model: namespace:path`. Aset model/tekstur di `pack-assets/assets/` digabungkan ke pack saat `/ci reload`; model item vanilla tidak ditimpa. |

## Perubahan 0.8.2

| Perubahan | Detail |
|---|---|
| Tekstur bawaan otomatis | JAR menyertakan tekstur contoh dan menyalinnya ke folder data plugin saat pertama dijalankan, sehingga pengguna tidak perlu menyalin file PNG manual. Tekstur yang sudah ada tidak ditimpa. |
| Perilaku item | `items.yml` dapat mengatur damage, armor, attack speed, efek saat dipegang/dipakai, serta ability on-hit dan on-right-click. Semua opsi bersifat opsional dan default-nya mempertahankan perilaku lama. |
| Drop dan skill mob | `mobs.yml` dapat mengatur drop vanilla/custom dengan peluang dan skill potion sederhana saat menyerang. Konfigurasi lama tetap berlaku. |

## Perubahan 0.8.1 (stabilitas dan kompatibilitas)

| Perubahan | Detail |
|---|---|
| Model item custom terpisah dari item vanilla | Item custom memakai komponen `item_model` dan aset di namespace plugin. Pack tidak lagi mengganti `assets/minecraft/items/<material>.json`, jadi model vanilla untuk item lain dengan material yang sama tetap utuh. Item lama di inventory pemain, ender chest, container yang dibuka, dan item drop dimigrasikan otomatis saat ditemukan. |
| Identitas custom block stabil | Plugin membuat `block-states.yml` saat pertama kali berjalan. Alokasi awal mengikuti urutan lama agar dunia 0.8.0 tetap cocok; penambahan, penghapusan, dan pengurutan ulang `blocks.yml` berikutnya tidak menggeser state blok yang sudah ada. ID milik key yang dihapus tetap dicadangkan. |
| Proteksi custom block | Break yang dibatalkan plugin proteksi tidak menghasilkan drop custom. Custom block juga tidak dapat dipindahkan piston atau dihancurkan ledakan, agar pencatatan lokasinya tidak lepas dari blok. |
| Penyimpanan block dibatch dan atomik | Perubahan lokasi digabung, ditulis maksimal setiap 5 detik dan saat plugin berhenti. File sementara lalu dipindah agar restart/crash tidak meninggalkan file setengah tertulis. |
| Reload dan unduhan resource pack | `/ci reload` menerapkan perubahan port dan `external-url`. Server mengirim ZIP secara streaming dan menghentikan executor saat plugin dimatikan. |
| Chat dan GUI | Pesan emoji tanpa rank tetap memakai renderer chat sebelumnya. Klik shift/double-click dan drag tidak bisa memasukkan item sembarang ke slot menu untuk diduplikasi. |
| Validasi konfigurasi dan command | Key tidak aman/duplikat dan nilai mob/suara tidak valid diabaikan saat load. `/ci` menolak subcommand yang tidak dikenal; item yang tidak muat dari `/ci give` dijatuhkan di dekat penerima. |
| Pembacaan aset | File texture dan OGG hanya dimasukkan jika berada di dalam direktori aset plugin, termasuk pemeriksaan symlink. Glyph rank/emoji tetap terikat ke indeksnya selama chat yang sedang diproses ketika `/ci reload` berlangsung. |

Pada migrasi dari 0.8.0, jangan hapus `block-states.yml` atau `placed-blocks.txt`. File pertama menjaga pasangan state lama; file kedua membedakan custom block dari noteblock vanilla. Jika registry state rusak atau tidak dapat disimpan, plugin menonaktifkan custom block dan meminta pemulihan backup daripada memakai ulang ID blok yang mungkin sudah ada di dunia.

## Perubahan 0.3.0 (perbaikan bug)

| Bug | Gejala | Perbaikan |
|---|---|---|
| Noteblock vanilla jadi tak terlihat | Resource pack menimpa `blockstates/note_block.json` dengan 1150 varian `instrument=...,note=...` **tanpa** variant `""`. `""` itu fallback yang dipakai client saat tidak ada key yang cocok; tanpanya, tidak ada noteblock yang match dan semua kehilangan model | **`0.4.0`** menghapus override-nya. **`0.8.0`** memperbaikinya: custom block benar-benar me-override blockstate — dengan tetap menyertakan `""` sebagai fallback |
| Seluruh teks server rusak jadi kotak | `font/default.json` ditulis ulang tanpa referensi font vanilla, padahal resource pack **mengganti** file itu, bukan menggabungkannya | Referensi `include/space`, `include/default`, `include/unifont` ditambahkan kembali |
| Custom block tidak bisa di-break | Block identity disimpan ke `TileState`, tapi noteblock **tidak punya block entity** — PDC tidak pernah tersimpan, jadi block tidak dikenali dan drop item gagal | Identity dibaca dari blockstate `instrument+note` |
| Pack URL `http://0.0.0.0:8077` | Client tidak bisa mengunduh pack | `0.0.0.0`/`::` diganti loopback + warning, trailing slash dan `/pack.zip` ganda dinormalkan |
| `/ci menu` crash | `createInventory` melempar error di atas 54 slot saat item+block > 54 | Ukuran dibatasi 54 + warning |
| Build langsung dengan Java | Build sebelumnya memerlukan Gradle terpasang | Alat Java dari JDK mengompilasi dan mengemas plugin tanpa Gradle |

## Perubahan 0.8.0 (custom block sungguhan)

| Perubahan | Detail |
|---|---|
| **Blok yang dipasang benar-benar custom** | Dulu `blockstates/note_block.json` sengaja tidak di-override, jadi blok custom hanya berbeda sebagai **item di tangan**; blok di dunia selalu tampil seperti noteblock biasa. Sekarang tiap custom block me-*stamp* `instrument` + `note` sendiri dan blockstate memetakannya ke `models/block/cblock_<key>.json`, jadi blok yang terpasang ter-render sebagai model kustom. Mekanisme yang sama dipakai Oraxen dan ItemsAdder |
| Fallback `""` di blockstate | `assets/minecraft/blockstates/note_block.json` ditulis ulang dengan satu variant `""` → `minecraft:block/note_block` **plus** satu variant per custom block. `""` itu wajib: client memakainya saat tidak ada key yang cocok, dan itu yang menjaga semua noteblock vanilla di dunia tetap tampil. Inilah yang hilang di 0.3.0 |
| `powered=true` ikut dipetakan | Tiap custom block dipetakan untuk **kedua** nilai `powered`. Kalau hanya `powered=false`, block yang di-*power* redstone diam-diam jatuh ke model noteblock vanilla |
| 4 nilai `trumpet` ditambahkan | `Blocks.ALL_INSTRUMENTS` punya 27 nilai yang cocok dengan blockstate noteblock di 26.2. `trumpet`, `trumpet_exposed`, `trumpet_oxidized`, `trumpet_weathered` sebelumnya hilang, sehingga `stateName()` jatuh ke `"harp"` dan salah melaporkan blok |
| Batas 250 block | Melewati 250 block (10 instrument × 25 note) membuat `instrument`/`note` berputar dan menimpa identitas blok sebelumnya secara diam-diam. Sekarang plugin berhenti memuat + memberi warning, seperti yang sudah dilakukan `Ranks`/`Emojis` untuk glyph |
| Validasi resource pack | Variant `""` **wajib ada**, tiap key harus berbentuk `instrument=,note=,powered=`, tiap state harus punya `powered=false` **dan** `powered=true`, dan model yang dirujuk harus benar-benar ada di dalam pack |

## Perubahan 0.7.0 (kemampuan CLI & GUI)

| Perubahan | Detail |
|---|---|
| **Tab completion** | `/ci <Tab>` mengisi subcommand, nama item/block untuk `give`, mob untuk `spawn`, sound untuk `play`, dan nama player online untuk `give <item> <player>`. Difilter case-insensitive |
| **`/ci list`** | Menampilkan semua key yang termuat: item, block, mob, sound, rank, emoji |
| **GUI paging** | Dulu `/ci menu` memotong di 54 entry. Sekarang 45 per halaman dengan tombol `<` `>`; inventory holder jadi per-pemain supaya dua player tidak saling menimpa halaman |
| **Custom mob bertahan setelah restart** | Tipe dan posisi tersimpan di world, tapi nama/HP/speed hanya ada di memori — setelah restart mob custom jadi zombie biasa. `ChunkLoadEvent` membaca tag PDC `cmob` dan menerapkan ulang atribut. Key yang sudah dihapus dari `mobs.yml` dibiarkan apa adanya |
| Validator resource pack cek glyph | Menandai `FAIL` kalau ada codepoint di luar U+E000–U+E1FF (artinya >256 rank/emoji dan glyph bocor ke CJK) atau ada codepoint duplikat (dua entry merebut satu slot) |

> Batas 256 per kategori ditegakkan di loader (`Ranks.MAX_GLYPHS` / `Emojis.MAX_GLYPHS`), jadi pack yang lolos validasi memang aman. Validator Java menangkap pack yang dibangun versi lama atau oleh alat lain.

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

Plugin menyimpan pasangan instrument/note per key di `plugins/CustomItems/block-states.yml`. Jangan hapus atau edit file itu setelah custom block dipasang di dunia. Key yang dihapus dari `blocks.yml` tetap menahan slotnya supaya blok lama tidak berubah menjadi jenis lain. `placed-blocks.txt` juga perlu masuk backup; file ini mencatat lokasi custom block dan membedakannya dari noteblock vanilla yang memiliki state sama.

## Konfigurasi

### items.yml

```yaml
items:
  ruby_sword:
    base: DIAMOND_SWORD      # item vanilla yang jadi dasar
    texture: ruby_sword.png  # PNG di plugins/CustomItems/textures/
    name: "<red>Ruby Sword"
    lore: ["<gray>Pedang legendaris"]
    damage: 4.0              # bonus ADD_NUMBER di atas atribut vanilla
    attack-speed: 0.5        # bonus ADD_NUMBER saat dipegang di tangan utama
    effects:
      held:                  # aktif selama item dipegang di tangan utama
        - type: strength
          amplifier: 0       # 0 = level I
          duration: 40       # 20–40 tick; diperbarui tiap detik
      use:                   # aktif pada klik-kanan
        - type: regeneration
          amplifier: 0
          duration: 100      # tick
    abilities:
      on-hit:
        - type: potion       # efek diberikan ke target yang terkena serangan langsung
          effect: slowness
          amplifier: 0
          duration: 60       # tick
          chance: 0.25       # 0.0–1.0
          cooldown: 3        # detik per pemain dan ability
      on-right-click:
        - type: heal          # heal hanya didukung untuk on-right-click
          amount: 4.0         # health point, maksimum 2048
          chance: 1.0
          cooldown: 10

  ruby_chestplate:
    base: DIAMOND_CHESTPLATE
    texture: ruby_chestplate.png
    name: "<red>Ruby Chestplate"
    armor: 4.0               # bonus armor pada slot equipment material dasar
```

Semua field tambahan opsional. Jika tidak ada, item tetap berperilaku seperti sebelumnya. `damage`, `armor`, dan `attack-speed` adalah bonus angka tetap yang ditambahkan di atas atribut bawaan material; armor memakai slot equipment dari material dasar. Bonus atribut dicatat saat stack item dibuat, jadi setelah mengubah stat, berikan ulang item dengan `/ci give <key>`. Efek `held` hanya memeriksa tangan utama, durasinya 20–40 tick dan efek yang aktif selesai setelah item dilepas. Efek `use` dan ability `on-right-click` dipicu pada klik-kanan. `on-hit` hanya berlaku untuk serangan langsung pemain, bukan panah/proyektil. Efek potion yang lebih kuat tidak ditimpa. Nilai di luar rentang atau tipe ability/efek yang tidak dikenal memberi warning di console dan entri tersebut dilewati.

Untuk model dari resource pack lain, set `model: minecraft:elitecreatures/fairy_heart_zeref_animated_weapon_set/sword` dan letakkan file JSON di `pack-assets/assets/minecraft/models/elitecreatures/...`; tekstur pasangannya di `pack-assets/assets/minecraft/textures/elitecreatures/...`. Importir Oraxen menyalin aset itu dan membuat konfigurasi item otomatis. File yang sudah ada tidak ditimpa.

### Import pack Oraxen

Jalankan tool Java dari repo. Ganti `--out` dengan folder data plugin di server:

```powershell
java tools/CustomItemsTools.java import-oraxen --zip "D:\MC\elitecreatures-fairy_heart_zeref_animated_weapon_set.zip" --out "D:\Server\plugins\CustomItems"
```

Tool membuat `imports/fairy_heart_zeref_animated_weapon_set.yml`, menyalin model/tekstur ke `pack-assets/assets/minecraft/`, dan menyalin ikon armor ke `textures/`. Setelah selesai, jalankan `/ci reload`; item hasil impor dapat diberikan dengan `/ci give <key>`. Importer mengambil model dan tekstur animasi, tetapi tidak menerjemahkan perilaku khusus Oraxen seperti pose tarik busur/crossbow, model shield/fishing-rod saat state berubah, armor ketika dipakai, atau furniture/hat mechanics. Item yang memakai model state khusus akan tetap memakai model statisnya.

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
    drops:
      - item: ruby_sword       # key item/block custom atau material vanilla
        amount: 1
        chance: 0.25           # 0.0–1.0, peluang untuk setiap entry
      - item: GOLD_INGOT
        amount: 2
        chance: 1.0
    skill:
      on-hit:
        effect: poison         # diberikan ke target yang terkena serangan mob
        amplifier: 0            # 0 = level I
        duration: 100           # tick
        chance: 0.25
        cooldown: 5             # detik per mob
```

`drops` menambahkan hasil drop di samping loot vanilla. `item` menerima key dari `items.yml`, key block dari `blocks.yml`, atau nama material Bukkit. Amount harus muat dalam satu stack. Skill `on-hit` memberi efek potion ke target serangan jarak dekat maupun proyektil; cooldown disimpan bersama data mob dan bertahan saat chunk/server dimuat ulang. Fitur berlaku untuk mob yang dibuat dengan `/ci spawn`; mob vanilla dengan tipe sama tidak ditandai sebagai custom. Semua field baru opsional, sehingga `mobs.yml` lama mempertahankan perilaku sebelumnya. Type, key, efek, peluang, cooldown, amount, dan nilai health/speed yang salah memberi warning di console; entri invalid dilewati.

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

Tekstur bawaan sudah disertakan di JAR. Texture PNG custom ditaruh di `plugins/CustomItems/textures/`, lalu `/ci reload` — pack.zip dibangun ulang otomatis. File yang sudah ada tidak ditimpa saat startup.

## Perintah

| Perintah | Fungsi |
|---|---|
| `/ci give <item> [player]` | Beri item custom |
| `/ci spawn <mob>` | Spawn mob custom |
| `/ci play <sound>` | Mainkan sound custom |
| `/ci menu` | Buka GUI semua isi |
| `/ci reload` | Reload `config.yml` + semua yml, rebuild pack, kirim ulang ke player online |
| `/ci pack` | Kirim ulang resource pack ke player online tanpa rebuild |
| `/ci list` | Tampilkan semua key item, block, mob, sound, rank, emoji yang termuat |

Semua subcommand punya tab completion.

Semua butuh permission `ci.admin` (default: op).

Tidak ada permission per item. Semua isi plugin bersifat publik: siapa pun yang memegang item/block/sound-nya bisa memakainya, dan rank tag hanya butuh `ci.rank.<key>`. Kalau nanti butuh gating per item (misalnya hanya untuk VIP), permission per key adalah tambahan kecil — bukan mengubah perilaku yang sekarang.

## Tools

Semua alat bantu ada di `tools/CustomItemsTools.java` dan memakai JDK saja.

| Tool | Fungsi |
|---|---|
| `test-pack` | **Validasi pack sebelum pemain mengunduhnya.** Mengecek varian noteblock, glyph vanilla, model, tekstur, dan kecocokan konfigurasi |
| `generate-textures` | Bikin placeholder pixel-art (`ruby_sword`, `marble_block`, `smile`) sesuai gaya rank tag yang ada |
| `setup-server` | Siapkan server Paper sekali-pakai di folder terpisah. **Tidak menyentuh server produksi** |
| `rcon` | Client RCON minimal untuk menjalankan perintah `/ci` |
| `build` | Kompilasi plugin dan buat JAR release dengan compiler dan API JDK |

Jalankan dari root repository:

```bash
java tools/CustomItemsTools.java generate-textures
java tools/CustomItemsTools.java test-pack --pack "../../plugins/CustomItems/pack.zip" --config-dir "../../plugins/CustomItems"
java tools/CustomItemsTools.java rcon --password test123 --command "ci reload"
```

Kode keluar `0` = pack lolos validasi, `1` = ada masalah, `2` = pemakaian/perintah gagal. Jalankan validator setelah `/ci reload` dan sebelum mengumumkan item baru ke pemain.

## Build dari source

```bash
java tools/CustomItemsTools.java build --libraries "/path/to/paper/libraries"
```

Butuh JDK 25 dan folder `libraries` dari server Paper 26.2 (harus berisi `paper-api` serta library dependensinya). Hasilnya di `build/libs/CustomItems-0.8.3.jar`, termasuk tekstur bawaan yang disalin otomatis ke folder data plugin pada startup pertama. Bisa juga diberikan classpath secara langsung lewat `--classpath` atau environment variable `PAPER_CLASSPATH`.

Untuk membuat server Paper uji terpisah, gunakan `java tools/CustomItemsTools.java setup-server --paper-jar "path/to/paper.jar"`. Metadata plugin (`plugin.yml`), konfigurasi YAML, dan aset PNG/OGG tetap memakai format yang diwajibkan Paper/resource pack; seluruh kode plugin dan alat yang bisa dieksekusi ditulis dalam Java.

## Catatan teknis

- Items: komponen `item_model` menunjuk aset milik plugin; file model item vanilla tidak ditimpa. Item custom lama dimigrasikan ketika inventory atau item drop dibuka/diambil
- Blocks: noteblock method (instrument + note unik per block). Identitas block dibaca dari blockstate noteblock (`instrument` + `note`), **bukan** PDC — noteblock tidak punya block entity. Alokasi state yang stabil disimpan di `plugins/CustomItems/block-states.yml`
- Blocks: lokasi yang sudah di-*mark* disimpan di `plugins/CustomItems/placed-blocks.txt`; perubahan disimpan berkala dan saat plugin berhenti. Break yang dibatalkan tidak menjatuhkan item; piston dan ledakan tidak memindahkan/menghapus custom block
- `blockstates/note_block.json` **di-override**: satu variant per custom block (`instrument=..,note=..,powered=..`) untuk `powered` false **dan** true, **plus** variant `""` yang menunjuk model noteblock vanilla. `""` itu wajib, bukan hiasan — itu fallback yang dipakai client saat tidak ada key yang cocok, dan vanilla sendiri hanya mendefinisikan satu variant itu untuk ratusan kombinasi state. Tanpa `""`, override apa pun hanya bisa menghapus model dari **seluruh** noteblock di dunia. Persis bug yang 0.3.0 ship dan 0.4.0 hapus dengan cara yang keliru. Maksimal **250 block** (10 instrument × 25 note)
- Recipes: ingredient custom dicocokkan via `RecipeChoice.exactChoice(ItemStack)` (constructor `ExactChoice(ItemStack)` deprecated-for-removal di Paper 26.x)
- Mobs: vanilla model + atribut custom (bukan model 3D custom seperti ModelEngine)
- Rank tags: bitmap font glyph (`\uE000`+) di `font/default.json`, dengan referensi font vanilla dipertahankan agar teks biasa tetap tampil. Maksimal **256 rank** (U+E000–U+E0FF); lebih dari itu diabaikan + warning
- Emojis: glyph range terpisah (`\uE100`+), trigger `:key:` di chat. Maksimal **256 emoji** (U+E100–U+E1FF)
- Font bitmap: PNG harus setinggi nilai `ascent` (default 8 piksel) dan disusun horizontal, satu glyph per slot sesuai urutan `chars` — file terlalu tinggi/lebar membuat semua glyph bergeser. Texture yang tidak ada **tidak** lagi ditulis sebagai provider (dulu jadi referensi menggantung), tapi nomor glyph tetap selaras
- Sounds: `sounds.json` custom, key namespace `custom.<nama>`
- Webserver: `com.sun.net.httpserver`, serve `/pack.zip`
