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
            // pack_format for <=1.21.8, min/max_format arrays for 1.21.9+ (both read by 1.21.9+)
            int fmt = plugin.getConfig().getInt("pack-format", 46);
            put(zip, "pack.mcmeta", """
                    {"pack":{"pack_format":%d,"min_format":[%d,0],"max_format":[%d,0],"description":"CustomItems pack"}}""".formatted(
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
            // custom blocks hijack noteblock states: one blockstate file covering ALL combos
            if (!plugin.blocks().all().isEmpty()) {
                List<Blocks.BlockDef> blocks = plugin.blocks().all();
                StringBuilder variants = new StringBuilder();
                for (String[] inst : Blocks.INSTRUMENTS) {
                    final String instName = inst[0];
                    for (int n = 0; n < 25; n++) {
                        final int note = n;
                        for (String powered : new String[]{"false", "true"}) {
                            if (!variants.isEmpty()) variants.append(',');
                            Blocks.BlockDef hit = blocks.stream()
                                    .filter(d -> d.instrument().equals(instName) && d.note() == note).findFirst().orElse(null);
                            String model = hit != null ? "minecraft:block/cblock_" + hit.key() : "minecraft:block/note_block";
                            variants.append("\"instrument=").append(instName).append(",note=").append(note)
                                    .append(",powered=").append(powered).append("\":{\"model\":\"").append(model).append("\"}");
                        }
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
            // rank tags: bitmap font glyphs, merged additively into the default font
            if (!plugin.ranks().all().isEmpty()) {
                StringBuilder providers = new StringBuilder();
                int i = 0;
                for (Ranks.RankDef def : plugin.ranks().all()) {
                    if (!providers.isEmpty()) providers.append(',');
                    providers.append("{\"type\":\"bitmap\",\"file\":\"minecraft:font/rank_")
                            .append(def.key()).append(".png\",\"ascent\":").append(def.ascent())
                            .append(",\"height\":").append(def.ascent())
                            .append(",\"chars\":[\"\\u").append(String.format("%04X", 0xE000 + i)).append("\"]}");
                    File png = new File(textures, def.texture());
                    if (png.isFile()) {
                        putBytes(zip, "assets/minecraft/textures/font/rank_" + def.key() + ".png",
                                Files.readAllBytes(png.toPath()));
                    } else {
                        plugin.getLogger().warning("Missing texture for rank '" + def.key() + "': " + png.getPath());
                    }
                    i++;
                }
                put(zip, "assets/minecraft/font/default.json", "{\"providers\":[" + providers + "]}");
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
