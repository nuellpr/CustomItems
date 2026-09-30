package com.chatbiasa.customitems;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.bukkit.NamespacedKey;

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
        String namespace = new NamespacedKey(plugin, "pack").getNamespace();
        File out = new File(plugin.getDataFolder(), "pack.zip");
        File temp = new File(plugin.getDataFolder(), "pack.zip.tmp");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(temp.toPath()))) {
            // 1.21.9+ reads min_format/max_format; pack_format stays for older clients.
            int fmt = plugin.getConfig().getInt("pack-format", 88);
            put(zip, "pack.mcmeta", """
                    {"pack":{"pack_format":%d,"min_format":%d,"max_format":%d,"description":"CustomItems pack"}}""".formatted(
                    fmt, fmt, fmt));
            putImportedAssets(zip);
            // Give custom stacks their own item-model IDs. This avoids replacing
            // assets/minecraft/items/<base>.json, which would change the model for every vanilla
            // stack of that material (including state-driven models such as bows and crossbows).
            for (ItemDef def : plugin.items().all()) {
                String base = def.base().getKey().getKey();
                String parent = base.matches(".*(sword|pickaxe|axe|shovel|hoe|bow|trident)")
                        ? "minecraft:item/handheld" : "minecraft:item/generated";
                if (def.model() == null) {
                    put(zip, "assets/" + namespace + "/items/" + def.key() + ".json",
                            "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"" + namespace
                                    + ":item/" + def.key() + "\"}}");
                    put(zip, "assets/" + namespace + "/models/item/" + def.key() + ".json",
                            "{\"parent\":\"" + parent + "\",\"textures\":{\"layer0\":\"" + namespace
                                    + ":item/" + def.key() + "\"}}");
                    putAsset(zip, textures, def.texture(), "assets/" + namespace + "/textures/item/" + def.key() + ".png",
                            "Missing or unsafe texture for '" + def.key() + "': " + def.texture());
                } else {
                    put(zip, "assets/" + namespace + "/items/" + def.key() + ".json",
                            "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"" + def.model() + "\"}}");
                    if (!importedModelExists(def.model())) {
                        plugin.getLogger().warning("Missing imported model for '" + def.key() + "': " + def.model()
                                + " (copy its JSON under pack-assets/assets/<namespace>/models/)");
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
                    String model = namespace + ":block/cblock_" + def.key();
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
                for (Blocks.BlockDef def : blocks) {
                    String id = "cblock_" + def.key();
                    put(zip, "assets/" + namespace + "/items/block/" + def.key() + ".json",
                            "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"" + namespace
                                    + ":item/block/" + def.key() + "\"}}");
                    put(zip, "assets/" + namespace + "/models/block/" + id + ".json", """
                            {"parent":"minecraft:block/cube_all","textures":{"all":"%s:block/%s"}}"""
                            .formatted(namespace, id));
                    put(zip, "assets/" + namespace + "/models/item/block/" + def.key() + ".json", """
                            {"parent":"%s:block/%s"}""".formatted(namespace, id));
                    putAsset(zip, textures, def.texture(), "assets/" + namespace + "/textures/block/" + id + ".png",
                            "Missing or unsafe texture for '" + def.key() + "': " + def.texture());
                }
            }
            // rank tags + emojis: bitmap font glyphs, merged additively into the default font.
            // Glyph indices advance for EVERY configured entry so they stay aligned with
            // Ranks.glyph()/Emojis.glyph(), which also count by position. A provider is only
            // emitted when the texture is actually packed: referencing a file the client cannot
            // find is a dangling reference, and the missing rank shows as one tofu box instead.
            StringBuilder providers = new StringBuilder();
            for (Ranks.RankDef def : plugin.ranks().all()) {
                if (putAsset(zip, textures, def.texture(), "assets/minecraft/textures/font/rank_" + def.key() + ".png",
                        "Missing or unsafe texture for rank '" + def.key() + "': " + def.texture()
                                + " - this rank will show as a blank box in chat")) {
                    if (!providers.isEmpty()) providers.append(',');
                    providers.append("{\"type\":\"bitmap\",\"file\":\"minecraft:font/rank_")
                            .append(def.key()).append(".png\",\"ascent\":").append(def.ascent())
                            .append(",\"height\":").append(def.ascent())
                            .append(",\"chars\":[\"\\u").append(String.format("%04X", 0xE000 + def.glyphIndex())).append("\"]}");
                }
            }
            for (Emojis.EmojiDef def : plugin.emojis().all()) {
                if (putAsset(zip, textures, def.texture(), "assets/minecraft/textures/font/emoji_" + def.key() + ".png",
                        "Missing or unsafe texture for emoji '" + def.key() + "': " + def.texture()
                                + " - :" + def.key() + ": will not render")) {
                    if (!providers.isEmpty()) providers.append(',');
                    providers.append("{\"type\":\"bitmap\",\"file\":\"minecraft:font/emoji_")
                            .append(def.key()).append(".png\",\"ascent\":").append(def.ascent())
                            .append(",\"height\":").append(def.ascent())
                            .append(",\"chars\":[\"\\u").append(String.format("%04X", 0xE100 + def.glyphIndex())).append("\"]}");
                }
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
                    putAsset(zip, sndDir, def.ogg(), "assets/minecraft/sounds/custom/" + def.key() + ".ogg",
                            "Missing or unsafe sound '" + def.key() + "': " + def.ogg());
                }
                put(zip, "assets/minecraft/sounds.json", "{" + soundsJson + "}");
            }
        }
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        try (InputStream input = Files.newInputStream(temp.toPath())) {
            byte[] buffer = new byte[8192];
            int length;
            while ((length = input.read(buffer)) != -1) digest.update(buffer, 0, length);
        }
        byte[] newSha1 = digest.digest();
        try {
            Files.move(temp.toPath(), out.toPath(), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp.toPath(), out.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        packFile = out;
        sha1 = newSha1;
        plugin.getLogger().info("Resource pack built: " + out.getPath());
    }

    private static void put(ZipOutputStream zip, String path, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(path));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static void putFile(ZipOutputStream zip, String path, File file) throws IOException {
        zip.putNextEntry(new ZipEntry(path));
        try (InputStream input = Files.newInputStream(file.toPath())) {
            input.transferTo(zip);
        }
        zip.closeEntry();
    }

    private void putImportedAssets(ZipOutputStream zip) throws IOException {
        Path root = new File(plugin.getDataFolder(), "pack-assets/assets").toPath();
        if (!Files.isDirectory(root)) return;
        Path realRoot = root.toRealPath();
        try (Stream<Path> namespaces = Files.list(realRoot)) {
            for (Path namespaceDir : namespaces.sorted().toList()) {
                String namespace = namespaceDir.getFileName().toString();
                if (!namespace.matches("[a-z0-9._-]+") || !Files.isDirectory(namespaceDir)) {
                    plugin.getLogger().warning("Ignoring invalid resource-pack namespace: " + namespace);
                    continue;
                }
                for (String kind : List.of("models", "textures")) {
                    Path kindDir = namespaceDir.resolve(kind);
                    if (!Files.isDirectory(kindDir)) continue;
                    try (Stream<Path> files = Files.walk(kindDir)) {
                        for (Path file : files.sorted().toList()) {
                            if (!Files.isRegularFile(file)) continue;
                            Path realFile = file.toRealPath();
                            if (!realFile.startsWith(realRoot)) {
                                plugin.getLogger().warning("Ignoring imported asset outside pack-assets: " + file);
                                continue;
                            }
                            String relative = kindDir.relativize(file).toString().replace('\\', '/');
                            String firstPart = relative.contains("/") ? relative.substring(0, relative.indexOf('/')) : relative;
                            boolean vanillaNamespace = namespace.equals("minecraft");
                            boolean pluginNamespace = namespace.equals(plugin.getName().toLowerCase(Locale.ROOT));
                            if ((vanillaNamespace || pluginNamespace)
                                    && ((kind.equals("models") && List.of("item", "block").contains(firstPart))
                                    || (kind.equals("textures") && List.of("item", "block", "font").contains(firstPart)))) {
                                plugin.getLogger().warning("Ignoring imported asset in reserved path: " + relative);
                                continue;
                            }
                            String lower = relative.toLowerCase(Locale.ROOT);
                            if (!(lower.endsWith(".json") || lower.endsWith(".png") || lower.endsWith(".mcmeta"))) {
                                plugin.getLogger().warning("Ignoring unsupported imported asset: " + relative);
                                continue;
                            }
                            putFile(zip, "assets/" + namespace + "/" + kind + "/" + relative, realFile.toFile());
                        }
                    }
                }
            }
        }
    }

    private boolean importedModelExists(String model) {
        NamespacedKey key = NamespacedKey.fromString(model);
        if (key == null) return false;
        Path root = new File(plugin.getDataFolder(), "pack-assets/assets").toPath().toAbsolutePath().normalize();
        Path file = root.resolve(key.getNamespace()).resolve("models").resolve(key.getKey() + ".json").normalize();
        if (!file.startsWith(root)) return false;
        try {
            return Files.isRegularFile(file.toRealPath()) && file.toRealPath().startsWith(root.toRealPath());
        } catch (IOException e) {
            return false;
        }
    }

    private boolean putAsset(ZipOutputStream zip, File directory, String configuredPath, String packPath, String warning)
            throws IOException {
        File file = sourceFile(directory, configuredPath);
        if (file == null) {
            plugin.getLogger().warning(warning);
            return false;
        }
        putFile(zip, packPath, file);
        return true;
    }

    /** Read only files stored inside the configured asset directory, including through symlinks. */
    private File sourceFile(File directory, String configuredPath) {
        if (configuredPath == null || configuredPath.isBlank()) return null;
        Path root = directory.toPath().toAbsolutePath().normalize();
        Path candidate = root.resolve(configuredPath).normalize();
        if (!candidate.startsWith(root)) {
            plugin.getLogger().warning("Ignoring asset path outside " + directory.getName() + ": " + configuredPath);
            return null;
        }
        try {
            Path realRoot = root.toRealPath();
            Path realCandidate = candidate.toRealPath();
            if (!realCandidate.startsWith(realRoot) || !Files.isRegularFile(realCandidate)) {
                plugin.getLogger().warning("Ignoring asset path outside " + directory.getName() + ": " + configuredPath);
                return null;
            }
            return realCandidate.toFile();
        } catch (IOException e) {
            return null;
        }
    }
}
