# CustomItems

Plugin Minecraft Paper seperti ItemsAdder: custom items, blocks, mobs, dan GUI menu dengan resource pack yang di-generate otomatis + webserver built-in. Pemain tinggal join, resource pack langsung diprompt.

## Fitur

- **Custom Items** — tekstur custom via resource pack, nama + lore (MiniMessage)
- **Custom Blocks** — model custom, tekstur, nama; aman di-break (drop item custom kembali)
- **Custom Mobs** — vanilla entity dengan custom nama, HP, dan kecepatan
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

1. Download `CustomItems-0.1.0.jar` dari [Releases](../../releases), taruh di folder `plugins/`
2. Start server → folder `plugins/CustomItems/` tergenerate
3. Untuk server online: set `external-url` di `config.yml` ke IP/URL publik (mis. `http://play.myserver.com:8077`) dan buka port-nya
4. `/ci reload`

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

Texture PNG ditaruh di `plugins/CustomItems/textures/`, lalu `/ci reload` — pack.zip dibangun ulang otomatis.

## Perintah

| Perintah | Fungsi |
|---|---|
| `/ci give <item> [player]` | Beri item custom |
| `/ci spawn <mob>` | Spawn mob custom |
| `/ci menu` | Buka GUI semua isi |
| `/ci reload` | Reload config + rebuild pack |

Semua butuh permission `ci.admin` (default: op).

## Build dari source

```bash
gradle build
```

Butuh Gradle 9.x + Java 21+. Hasil di `build/libs/CustomItems-0.1.0.jar`.

## Catatan teknis

- Items: `custom_model_data` string + item model definition format 1.21.4+ (`select`)
- Blocks: noteblock method (instrument + note unik per block, PDC sebagai sumber kebenaran)
- Mobs: vanilla model + atribut custom (bukan model 3D custom seperti ModelEngine)
- Webserver: `com.sun.net.httpserver`, serve `/pack.zip`
