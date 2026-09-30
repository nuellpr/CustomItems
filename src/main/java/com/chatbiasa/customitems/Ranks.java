package com.chatbiasa.customitems;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class Ranks {

    public record RankDef(String key, String texture, int ascent, int glyphIndex) {}

    /** U+E000..U+E0FF is 256 codepoints. Rank 257 would land on \u0F00 (a CJK glyph) and shift
     *  every later rank off its bitmap, so the file is truncated here instead. */
    public static final int MAX_GLYPHS = 256;

    private final CustomItemsPlugin plugin;
    /** Swapped wholesale by load(). AsyncChatEvent reads this from a chat thread while /ci reload
     *  runs on the main thread, so it must never be cleared-and-refilled in place: a reader would
     *  either throw ConcurrentModificationException or miss a rank. `next` is published only once
     *  fully built and is never mutated afterwards, so readers always see one complete snapshot. */
    private volatile Map<String, RankDef> ranks = new LinkedHashMap<>();

    public Ranks(CustomItemsPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        Map<String, RankDef> next = new LinkedHashMap<>();
        File f = new File(plugin.getDataFolder(), "ranks.yml");
        if (!f.exists()) plugin.saveResource("ranks.yml", false);
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
        if (yml.getConfigurationSection("ranks") != null) {
            for (String key : yml.getConfigurationSection("ranks").getKeys(false)) {
                if (next.size() >= MAX_GLYPHS) {
                    plugin.getLogger().warning("ranks.yml: only the first " + MAX_GLYPHS
                            + " ranks are loaded; '" + key + "' and later ones have no free glyph.");
                    break;
                }
                String normalized = key.toLowerCase(Locale.ROOT);
                if (!normalized.matches("[a-z0-9_-]+") || next.containsKey(normalized)) {
                    plugin.getLogger().warning("ranks.yml: invalid or duplicate rank key '" + key + "'");
                    continue;
                }
                String tex = yml.getString("ranks." + key + ".texture", key + ".png");
                int ascent = yml.getInt("ranks." + key + ".ascent", 8);
                if (ascent < 1 || ascent > 512) {
                    plugin.getLogger().warning("ranks.yml: ascent for '" + key + "' must be between 1 and 512");
                    continue;
                }
                next.put(normalized, new RankDef(normalized, tex, ascent, next.size()));
            }
        }
        ranks = next;
        plugin.getLogger().info("Loaded " + next.size() + " rank tags");
    }

    public List<RankDef> all() {
        return new ArrayList<>(ranks.values());
    }

    /** first rank whose permission the player holds, in file order */
    public RankDef forPlayer(org.bukkit.entity.Player p) {
        for (RankDef def : ranks.values()) {
            if (p.hasPermission("ci.rank." + def.key())) return def;
        }
        return null;
    }

    /** chat glyph char, assigned in file order from the private use area */
    public String glyph(RankDef def) {
        return String.valueOf((char) (0xE000 + def.glyphIndex()));
    }
}
