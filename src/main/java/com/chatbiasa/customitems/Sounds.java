package com.chatbiasa.customitems;

import org.bukkit.SoundCategory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Sounds {

    public record SoundDef(String key, String ogg, float volume, float pitch) {}

    private final CustomItemsPlugin plugin;
    private final Map<String, SoundDef> byKey = new LinkedHashMap<>();

    public Sounds(CustomItemsPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        byKey.clear();
        File f = new File(plugin.getDataFolder(), "sounds.yml");
        if (!f.exists()) plugin.saveResource("sounds.yml", false);
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
        ConfigurationSection sec = yml.getConfigurationSection("sounds");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                ConfigurationSection s = sec.getConfigurationSection(key);
                if (s == null) continue;
                byKey.put(key.toLowerCase(), new SoundDef(
                        key.toLowerCase(),
                        s.getString("file", key + ".ogg"),
                        (float) s.getDouble("volume", 1.0),
                        (float) s.getDouble("pitch", 1.0)));
            }
        }
        plugin.getLogger().info("Loaded " + byKey.size() + " custom sounds");
    }

    public SoundDef get(String key) {
        return byKey.get(key.toLowerCase());
    }

    public List<SoundDef> all() {
        return new ArrayList<>(byKey.values());
    }

    public void play(Player p, SoundDef def) {
        // sound id format: "custom.<key>" registered in pack sounds.json
        p.playSound(p.getLocation(), "custom." + def.key(), SoundCategory.MASTER, def.volume(), def.pitch());
    }
}
