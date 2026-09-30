package com.chatbiasa.customitems;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class Emojis {

    public record EmojiDef(String key, String texture, int ascent, int glyphIndex) {}

    /** U+E100..U+E1FF is 256 codepoints; same overflow guard as Ranks.MAX_GLYPHS. */
    public static final int MAX_GLYPHS = 256;

    private final CustomItemsPlugin plugin;
    /** Swapped wholesale by load(); see Ranks.ranks for why it is not refilled in place. */
    private volatile Map<String, EmojiDef> emojis = new LinkedHashMap<>();

    public Emojis(CustomItemsPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        Map<String, EmojiDef> next = new LinkedHashMap<>();
        File f = new File(plugin.getDataFolder(), "emojis.yml");
        if (!f.exists()) plugin.saveResource("emojis.yml", false);
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
        if (yml.getConfigurationSection("emojis") != null) {
            for (String key : yml.getConfigurationSection("emojis").getKeys(false)) {
                if (next.size() >= MAX_GLYPHS) {
                    plugin.getLogger().warning("emojis.yml: only the first " + MAX_GLYPHS
                            + " emojis are loaded; '" + key + "' and later ones have no free glyph.");
                    break;
                }
                String normalized = key.toLowerCase(Locale.ROOT);
                if (!normalized.matches("[a-z0-9_-]+") || next.containsKey(normalized)) {
                    plugin.getLogger().warning("emojis.yml: invalid or duplicate emoji key '" + key + "'");
                    continue;
                }
                String tex = yml.getString("emojis." + key + ".texture", key + ".png");
                int ascent = yml.getInt("emojis." + key + ".ascent", 8);
                if (ascent < 1 || ascent > 512) {
                    plugin.getLogger().warning("emojis.yml: ascent for '" + key + "' must be between 1 and 512");
                    continue;
                }
                next.put(normalized, new EmojiDef(normalized, tex, ascent, next.size()));
            }
        }
        emojis = next;
        plugin.getLogger().info("Loaded " + next.size() + " emojis");
    }

    public List<EmojiDef> all() {
        return new ArrayList<>(emojis.values());
    }

    /** emoji lookup by key for :name: replacement */
    public EmojiDef get(String key) {
        return key == null ? null : emojis.get(key.toLowerCase(Locale.ROOT));
    }

    /** chat glyph char, assigned in file order from a range above the rank tags */
    public String glyph(EmojiDef def) {
        return String.valueOf((char) (0xE100 + def.glyphIndex()));
    }
}
