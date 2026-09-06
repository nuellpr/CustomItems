package com.chatbiasa.customitems;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Emojis {

    public record EmojiDef(String key, String texture, int ascent) {}

    private final CustomItemsPlugin plugin;
    private final Map<String, EmojiDef> emojis = new LinkedHashMap<>();

    public Emojis(CustomItemsPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        emojis.clear();
        File f = new File(plugin.getDataFolder(), "emojis.yml");
        if (!f.exists()) plugin.saveResource("emojis.yml", false);
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
        if (yml.getConfigurationSection("emojis") == null) return;
        for (String key : yml.getConfigurationSection("emojis").getKeys(false)) {
            String tex = yml.getString("emojis." + key + ".texture", key + ".png");
            int ascent = yml.getInt("emojis." + key + ".ascent", 8);
            emojis.put(key.toLowerCase(), new EmojiDef(key.toLowerCase(), tex, ascent));
        }
        plugin.getLogger().info("Loaded " + emojis.size() + " emojis");
    }

    public List<EmojiDef> all() {
        return new ArrayList<>(emojis.values());
    }

    /** emoji lookup by key for :name: replacement */
    public EmojiDef get(String key) {
        return emojis.get(key.toLowerCase());
    }

    /** chat glyph char, assigned in file order from a range above the rank tags */
    public String glyph(EmojiDef def) {
        int i = 0;
        for (EmojiDef d : emojis.values()) {
            if (d == def) break;
            i++;
        }
        return String.valueOf((char) (0xE100 + i));
    }
}
