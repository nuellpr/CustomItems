package com.chatbiasa.customitems;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public final class Mobs {

    public record MobDef(String key, EntityType type, Component name, double health, double speed) {}

    private final CustomItemsPlugin plugin;
    private final Map<String, MobDef> byKey = new HashMap<>();

    public Mobs(CustomItemsPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        byKey.clear();
        File f = new File(plugin.getDataFolder(), "mobs.yml");
        if (!f.exists()) {
            plugin.saveResource("mobs.yml", false);
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
        ConfigurationSection mobs = yml.getConfigurationSection("mobs");
        if (mobs == null) return;
        for (String key : mobs.getKeys(false)) {
            ConfigurationSection s = mobs.getConfigurationSection(key);
            if (s == null) continue;
            EntityType type;
            try {
                type = EntityType.valueOf(s.getString("type", "ZOMBIE").toUpperCase());
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("mobs.yml: unknown type for '" + key + "'");
                continue;
            }
            MobDef def = new MobDef(
                    key.toLowerCase(),
                    type,
                    MiniMessage.miniMessage().deserialize(s.getString("name", key)),
                    s.getDouble("health", 20.0),
                    s.getDouble("speed", 0.25)
            );
            byKey.put(def.key(), def);
        }
        plugin.getLogger().info("Loaded " + byKey.size() + " custom mobs");
    }

    public MobDef get(String key) {
        return byKey.get(key.toLowerCase());
    }

    public Collection<MobDef> all() {
        return byKey.values();
    }

    public LivingEntity spawn(MobDef def, org.bukkit.Location loc) {
        LivingEntity e = (LivingEntity) loc.getWorld().spawnEntity(loc, def.type());
        e.customName(def.name());
        e.setCustomNameVisible(true);
        e.setPersistent(true);
        e.getAttribute(Attribute.MAX_HEALTH).setBaseValue(def.health());
        e.setHealth(def.health());
        e.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(def.speed());
        e.getPersistentDataContainer().set(plugin.mobKey(), PersistentDataType.STRING, def.key());
        return e;
    }
}
