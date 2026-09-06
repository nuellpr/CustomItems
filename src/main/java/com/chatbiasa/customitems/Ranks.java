package com.chatbiasa.customitems;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Ranks {

    public record RankDef(String key, String texture, int ascent) {}

    private final CustomItemsPlugin plugin;
    private final Map<String, RankDef> ranks = new LinkedHashMap<>();

    public Ranks(CustomItemsPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        ranks.clear();
        File f = new File(plugin.getDataFolder(), "ranks.yml");
        if (!f.exists()) plugin.saveResource("ranks.yml", false);
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
        if (yml.getConfigurationSection("ranks") == null) return;
        for (String key : yml.getConfigurationSection("ranks").getKeys(false)) {
            String tex = yml.getString("ranks." + key + ".texture", key + ".png");
            int ascent = yml.getInt("ranks." + key + ".ascent", 8);
            ranks.put(key.toLowerCase(), new RankDef(key.toLowerCase(), tex, ascent));
        }
        plugin.getLogger().info("Loaded " + ranks.size() + " rank tags");
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
        int i = 0;
        for (RankDef d : ranks.values()) {
            if (d == def) break;
            i++;
        }
        return String.valueOf((char) (0xE000 + i));
    }
}
