package com.chatbiasa.customitems;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class PackBuilder {

    private final CustomItemsPlugin plugin;

    public PackBuilder(CustomItemsPlugin plugin) {
        this.plugin = plugin;
    }

    public byte[] sha1;
    public File packFile;

    public void build() throws Exception {
        File textures = new File(plugin.getDataFolder(), "textures");
        if (!textures.exists()) textures.mkdirs();

        // group items by base material so one item model file holds all string-CMD cases
        Map<org.bukkit.Material, List<ItemDef>> byBase = new LinkedHashMap<>();
        for (ItemDef def : plugin.items().all()) {
            byBase.computeIfAbsent(def.base(), k -> new ArrayList<>()).add(def);
        }

        File out = new File(plugin.getDataFolder(), "pack.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(out.toPath()))) {
            // 1.21.9+ reads min_format/max_format; pack_format stays for older clients.
            int fmt = plugin.getConfig().getInt("pack-format", 88);
            put(zip, "pack.mcmeta", """
                    {"pack":{"pack_format":%d,"min_format":%d,"max_format":%d,"description":"CustomItems pack"}}""".formatted(
                    fmt, fmt, fmt));
            for (List<ItemDef> defs : byBase.values()) {
                String base = defs.get(0).base().getKey().getKey();
                StringBuilder cases = new StringBuilder();
                for (ItemDef def : defs) {
                    if (!cases.isEmpty()) cases.append(',');
                    cases.append("{\"model\":{\"type\":\"minecraft:model\",\"model\":\"minecraft:item/")
                            .append(def.key()).append("\"},\"when\":\"").append(def.key()).append("\"}");
                }
                put(zip, "assets/minecraft/items/" + base + ".json", """
                        {"model":{"type":"minecraft:select","property":"minecraft:custom_model_data","cases":[%s],"fallback":{"type":"minecraft:model","model":"minecraft:item/%s"}}}"""
                        .formatted(cases, base));
                for (ItemDef def : defs) {
                    String parent = def.base().getKey().getKey().matches(".*(sword|pickaxe|axe|shovel|hoe|bow|trident)")
                            ? "minecraft:item/handheld" : "minecraft:item/generated";
                    put(zip, "assets/minecraft/models/item/" + def.key() + ".json",
                            "{\"parent\":\"" + parent + "\",\"textures\":{\"layer0\":\"minecraft:item/" + def.key() + "\"}}");
                    File png = new File(textures, def.texture());
                    if (png.isFile()) {
                        putBytes(zip, "assets/minecraft/textures/item/" + def.key() + ".png", Files.readAllBytes(png.toPath()));
                    } else {
                        plugin.getLogger().warning("Missing texture for '" + def.key() + "': " + png.getPath());
                    }
                }
            }
            // Custom blocks are note blocks whose instrument+note blockstate selects our own model --
            // the same mechanism Oraxen and ItemsAdder use. A note block has no block entity, so the
            // blockstate is the only identity it can carry, and it has enough combinations
            // (instrument x note x powered) to key a real model.
            // The "" variant is REQUIRED, not decoration: it is the fallback the client falls back to
            // when no other key matches the block's state. Vanilla note_block.json carries only "" and
            // still renders every note block in the game, which is the proof the fallback works.
            // Without it this file would match nothing and strip the model from every note block.
            if (!plugin.blocks().all().isEmpty()) {
                List<Blocks.BlockDef> blocks = plugin.blocks().all();
                StringBuilder variants = new StringBuilder("\"\":{\"model\":\"minecraft:block/note_block\"}");
                for (Blocks.BlockDef def : blocks) {
                    String model = "minecraft:block/cblock_" + def.key();
                    // both powered values, otherwise a redstone-powered custom block silently
                    // falls back to the vanilla noteblock model
                    for (String powered : new String[]{"false", "true"}) {
                        variants.append(",\"instrument=").append(def.instrument())
                                .append(",note=").append(def.note())
                                .append(",powered=").append(powered)
                                .append("\":{\"model\":\"").append(model).append("\"}");
                    }
                }
                put(zip, "assets/minecraft/blockstates/note_block.json",
                        "{\"variants\":{" + variants + "}}");
                StringBuilder blockCases = new StringBuilder();
                for (Blocks.BlockDef def : blocks) {
                    if (!blockCases.isEmpty()) blockCases.append(',');
                    blockCases.append("{\"model\":{\"type\":\"minecraft:model\",\"model\":\"minecraft:item/")
                            .append(def.key()).append("\"},\"when\":\"").append(def.key()).append("\"}");
                }
                put(zip, "assets/minecraft/items/note_block.json", """
                        {"model":{"type":"minecraft:select","property":"minecraft:custom_model_data","cases":[%s],"fallback":{"type":"minecraft:model","model":"minecraft:item/note_block"}}}"""
                        .formatted(blockCases));
                for (Blocks.BlockDef def : blocks) {
                    String id = "cblock_" + def.key();
                    put(zip, "assets/minecraft/models/block/" + id + ".json", """
                            {"parent":"minecraft:block/cube_all","textures":{"all":"minecraft:block/%s"}}""".formatted(id));
                    put(zip, "assets/minecraft/models/item/" + def.key() + ".json", """
                            {"parent":"minecraft:block/%s"}""".formatted(id));
                    File png = new File(textures, def.texture());
                    if (png.isFile()) {
                        putBytes(zip, "assets/minecraft/textures/block/" + id + ".png", Files.readAllBytes(png.toPath()));
                    } else {
                        plugin.getLogger().warning("Missing texture for '" + def.key() + "': " + png.getPath());
                    }
                }
            }
            // rank tags + emojis: bitmap font glyphs, merged additively into the default font.
            // Glyph indices advance for EVERY configured entry so they stay aligned with
            // Ranks.glyph()/Emojis.glyph(), which also count by position. A provider is only
            // emitted when the texture is actually packed: referencing a file the client cannot
            // find is a dangling reference, and the missing rank shows as one tofu box instead.
            StringBuilder providers = new StringBuilder();
            int gi = 0;
            for (Ranks.RankDef def : plugin.ranks().all()) {
                File png = new File(textures, def.texture());
                if (png.isFile()) {
                    if (!providers.isEmpty()) providers.append(',');
                    providers.append("{\"type\":\"bitmap\",\"file\":\"minecraft:font/rank_")
                            .append(def.key()).append(".png\",\"ascent\":").append(def.ascent())
                            .append(",\"height\":").append(def.ascent())
                            .append(",\"chars\":[\"\\u").append(String.format("%04X", 0xE000 + gi)).append("\"]}");
                    putBytes(zip, "assets/minecraft/textures/font/rank_" + def.key() + ".png",
                            Files.readAllBytes(png.toPath()));
                } else {
                    plugin.getLogger().warning("Missing texture for rank '" + def.key() + "': " + png.getPath()
                            + " - this rank will show as a blank box in chat");
                }
                gi++;
            }
            int ei = 0;
            for (Emojis.EmojiDef def : plugin.emojis().all()) {
                File png = new File(textures, def.texture());
                if (png.isFile()) {
                    if (!providers.isEmpty()) providers.append(',');
                    providers.append("{\"type\":\"bitmap\",\"file\":\"minecraft:font/emoji_")
                            .append(def.key()).append(".png\",\"ascent\":").append(def.ascent())
                            .append(",\"height\":").append(def.ascent())
                            .append(",\"chars\":[\"\\u").append(String.format("%04X", 0xE100 + ei)).append("\"]}");
                    putBytes(zip, "assets/minecraft/textures/font/emoji_" + def.key() + ".png",
                            Files.readAllBytes(png.toPath()));
                } else {
                    plugin.getLogger().warning("Missing texture for emoji '" + def.key() + "': " + png.getPath()
                            + " - :" + def.key() + ": will not render");
                }
                ei++;
            }
            if (!providers.isEmpty()) {
                // A pack REPLACES font/default.json outright — overlays do NOT merge JSON files.
                // Without re-adding the vanilla references, every ordinary character would lose its
                // glyph (the whole server would render as tofu boxes). Custom glyphs go first so they
                // win for their private-use codepoints, then vanilla handles everything else.
                String vanilla = "{\"type\":\"reference\",\"id\":\"minecraft:include/space\"},"
                        + "{\"type\":\"reference\",\"id\":\"minecraft:include/default\",\"filter\":{\"uniform\":false}},"
                        + "{\"type\":\"reference\",\"id\":\"minecraft:include/unifont\"}";
                put(zip, "assets/minecraft/font/default.json", "{\"providers\":[" + providers + "," + vanilla + "]}");
            }
            // custom sounds: ogg files + sounds.json registering "custom.<key>"
            if (!plugin.sounds().all().isEmpty()) {
                File sndDir = new File(plugin.getDataFolder(), "sounds");
                if (!sndDir.exists()) sndDir.mkdirs();
                StringBuilder soundsJson = new StringBuilder();
                for (Sounds.SoundDef def : plugin.sounds().all()) {
                    if (!soundsJson.isEmpty()) soundsJson.append(',');
                    soundsJson.append("\"custom.").append(def.key())
                            .append("\":{\"sounds\":[\"custom/").append(def.key())
                            .append("\"],\"category\":\"master\"}");
                    File ogg = new File(sndDir, def.ogg());
                    if (ogg.isFile()) {
                        putBytes(zip, "assets/minecraft/sounds/custom/" + def.key() + ".ogg", Files.readAllBytes(ogg.toPath()));
                    } else {
                        plugin.getLogger().warning("Missing sound '" + def.key() + "': " + ogg.getPath());
                    }
                }
                put(zip, "assets/minecraft/sounds.json", "{" + soundsJson + "}");
            }
        }
        packFile = out;
        sha1 = MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(out.toPath()));
        plugin.getLogger().info("Resource pack built: " + out.getPath());
    }

    private static void put(ZipOutputStream zip, String path, String content) throws IOException {
        putBytes(zip, path, content.getBytes(StandardCharsets.UTF_8));
    }

    private static void putBytes(ZipOutputStream zip, String path, byte[] bytes) throws IOException {
        zip.putNextEntry(new ZipEntry(path));
        zip.write(bytes);
        zip.closeEntry();
    }
}
