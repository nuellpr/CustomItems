package com.chatbiasa.customitems;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class Mobs implements Listener {

    public record MobDef(String key, EntityType type, Component name, double health, double speed) {}

    private final CustomItemsPlugin plugin;
    private final Map<String, MobDef> byKey = new LinkedHashMap<>();

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
            String normalized = key.toLowerCase(Locale.ROOT);
            if (!normalized.matches("[a-z0-9_-]+") || byKey.containsKey(normalized)) {
                plugin.getLogger().warning("mobs.yml: invalid or duplicate mob key '" + key + "'");
                continue;
            }
            EntityType type;
            try {
                type = EntityType.valueOf(s.getString("type", "ZOMBIE").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("mobs.yml: unknown type for '" + key + "'");
                continue;
            }
            // health/speed are LivingEntity attributes, so a non-living type (BOAT, ITEM, ARMOR_STAND)
            // would blow up as a ClassCastException at spawn time, long after the typo was made
            Class<?> cls = type.getEntityClass();
            if (!type.isSpawnable() || cls == null || !LivingEntity.class.isAssignableFrom(cls)) {
                plugin.getLogger().warning("mobs.yml: type " + type + " for '" + key
                        + "' is not a spawnable living entity");
                continue;
            }
            double health = s.getDouble("health", 20.0);
            double speed = s.getDouble("speed", 0.25);
            if (!Double.isFinite(health) || health <= 0 || health > 1024
                    || !Double.isFinite(speed) || speed < 0 || speed > 1024) {
                plugin.getLogger().warning("mobs.yml: invalid health/speed for '" + key + "' (0 < health <= 1024, 0 <= speed <= 1024)");
                continue;
            }
            MobDef def = new MobDef(
                    normalized,
                    type,
                    MiniMessage.miniMessage().deserialize(s.getString("name", key)),
                    health,
                    speed
            );
            byKey.put(def.key(), def);
        }
        plugin.getLogger().info("Loaded " + byKey.size() + " custom mobs");
    }

    public MobDef get(String key) {
        return key == null ? null : byKey.get(key.toLowerCase(Locale.ROOT));
    }

    public Collection<MobDef> all() {
        return byKey.values();
    }

    public LivingEntity spawn(MobDef def, org.bukkit.Location loc) {
        LivingEntity e = (LivingEntity) loc.getWorld().spawnEntity(loc, def.type());
        apply(e, def);
        e.getPersistentDataContainer().set(plugin.mobKey(), PersistentDataType.STRING, def.key());
        return e;
    }

    private void apply(LivingEntity e, MobDef def) {
        e.customName(def.name());
        e.setCustomNameVisible(true);
        e.setPersistent(true);
        // an attribute can be absent for some mob types, so fall back to the vanilla value
        var health = e.getAttribute(Attribute.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(def.health());
            e.setHealth(def.health());
        }
        var speed = e.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null) speed.setBaseValue(def.speed());
    }

    /** A mob that survives a restart comes back as a vanilla zombie: attributes and custom name
     *  live in memory only, while the type and position are saved by the world. The PDC tag is
     *  what makes re-applying possible, so ChunkLoadEvent is where it has to happen. */
    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        for (var ent : e.getChunk().getEntities()) {
            if (!(ent instanceof LivingEntity le)) continue;
            String key = le.getPersistentDataContainer().get(plugin.mobKey(), PersistentDataType.STRING);
            if (key == null) continue;
            MobDef def = byKey.get(key.toLowerCase(Locale.ROOT));
            // a key removed from mobs.yml is left alone: it is now just a vanilla mob
            if (def != null) apply(le, def);
        }
    }
}
