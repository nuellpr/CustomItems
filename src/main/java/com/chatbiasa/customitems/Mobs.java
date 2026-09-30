package com.chatbiasa.customitems;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class Mobs implements Listener {

    private static final int MAX_EFFECT_DURATION = 1_200_000;
    private static final int MAX_AMPLIFIER = 255;
    private static final int MAX_COOLDOWN_SECONDS = 3600;

    public enum DropType { ITEM, BLOCK, MATERIAL }

    public record DropDef(DropType type, String key, Material material, int amount, double chance) {}

    public record SkillDef(PotionEffectType effect, int amplifier, int duration,
                           double chance, int cooldownSeconds) {}

    public record MobDef(String key, EntityType type, Component name, double health, double speed,
                         List<DropDef> drops, SkillDef skill) {
        public MobDef {
            drops = List.copyOf(drops);
        }
    }

    private final CustomItemsPlugin plugin;
    private final NamespacedKey skillLastUsedKey;
    private final Map<String, MobDef> byKey = new LinkedHashMap<>();

    public Mobs(CustomItemsPlugin plugin) {
        this.plugin = plugin;
        this.skillLastUsedKey = new NamespacedKey(plugin, "mob-skill-last-used");
    }

    public void load() {
        byKey.clear();
        File file = new File(plugin.getDataFolder(), "mobs.yml");
        if (!file.exists()) plugin.saveResource("mobs.yml", false);
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection mobs = yml.getConfigurationSection("mobs");
        if (mobs == null) {
            if (yml.contains("mobs")) plugin.getLogger().warning("mobs.yml: mobs must be a section");
            return;
        }
        for (String key : mobs.getKeys(false)) {
            ConfigurationSection section = mobs.getConfigurationSection(key);
            if (section == null) {
                plugin.getLogger().warning("mobs.yml: entry '" + key + "' must be a section; ignoring it");
                continue;
            }
            String normalized = key.toLowerCase(Locale.ROOT);
            if (!normalized.matches("[a-z0-9_-]+") || byKey.containsKey(normalized)) {
                plugin.getLogger().warning("mobs.yml: invalid or duplicate mob key '" + key + "'");
                continue;
            }
            EntityType type = entityType(section, key);
            if (type == null) continue;
            Double health = number(section, "health", 20, key);
            Double speed = number(section, "speed", 0.25, key);
            if (health == null || speed == null) continue;
            if (health <= 0 || health > 1024 || speed < 0 || speed > 1024) {
                plugin.getLogger().warning("mobs.yml: invalid health/speed for '" + key
                        + "' (0 < health <= 1024, 0 <= speed <= 1024)");
                continue;
            }
            Object configuredName = section.get("name");
            if (section.contains("name") && !(configuredName instanceof String)) {
                plugin.getLogger().warning("mobs.yml: name for '" + key + "' must be text; using the mob key");
            }
            String name = configuredName instanceof String text ? text : key;
            MobDef def = new MobDef(
                    normalized,
                    type,
                    MiniMessage.miniMessage().deserialize(name),
                    health,
                    speed,
                    drops(section, key),
                    skill(section, key)
            );
            byKey.put(def.key(), def);
        }
        plugin.getLogger().info("Loaded " + byKey.size() + " custom mobs");
    }

    private EntityType entityType(ConfigurationSection section, String key) {
        Object value = section.get("type");
        String name = value == null && !section.contains("type") ? "ZOMBIE"
                : value instanceof String text ? text : "";
        if (name.isBlank()) {
            plugin.getLogger().warning("mobs.yml: type for '" + key + "' must be a valid entity type");
            return null;
        }
        EntityType type;
        try {
            type = EntityType.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("mobs.yml: unknown type for '" + key + "'");
            return null;
        }
        Class<?> entityClass = type.getEntityClass();
        if (!type.isSpawnable() || entityClass == null || !LivingEntity.class.isAssignableFrom(entityClass)) {
            plugin.getLogger().warning("mobs.yml: type " + type + " for '" + key
                    + "' is not a spawnable living entity");
            return null;
        }
        return type;
    }

    private List<DropDef> drops(ConfigurationSection mob, String mobKey) {
        Object value = mob.get("drops");
        if (value == null) {
            warnIfInvalidSection(mob, "drops", mobKey);
            return List.of();
        }
        if (!(value instanceof List<?> entries)) {
            plugin.getLogger().warning("mobs.yml: drops for '" + mobKey + "' must be a list; ignoring it");
            return List.of();
        }
        List<DropDef> result = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            if (!(entries.get(i) instanceof Map<?, ?> entry)) {
                plugin.getLogger().warning("mobs.yml: invalid drop " + i + " for '" + mobKey + "'");
                continue;
            }
            Object itemValue = entry.get("item");
            if (!(itemValue instanceof String itemName) || itemName.isBlank()) {
                plugin.getLogger().warning("mobs.yml: drop " + i + " for '" + mobKey + "' needs an item key or material");
                continue;
            }

            ItemDef customItem = plugin.items().get(itemName);
            Blocks.BlockDef customBlock = customItem == null ? plugin.blocks().get(itemName) : null;
            Material material = customItem != null ? customItem.base()
                    : customBlock != null ? Material.NOTE_BLOCK : materialFor(itemName);
            DropType type = customItem != null ? DropType.ITEM
                    : customBlock != null ? DropType.BLOCK : DropType.MATERIAL;
            if (material == null || !material.isItem() || material.isAir()) {
                plugin.getLogger().warning("mobs.yml: unknown item/material '" + itemName + "' in drop " + i
                        + " for '" + mobKey + "'");
                continue;
            }

            int amount = integer(entry, "amount", 1);
            double chance = decimal(entry, "chance", 1);
            if (amount < 1 || amount > material.getMaxStackSize()
                    || !Double.isFinite(chance) || chance < 0 || chance > 1) {
                plugin.getLogger().warning("mobs.yml: invalid amount/chance in drop " + i + " for '" + mobKey
                        + "' (amount 1-" + material.getMaxStackSize() + ", chance 0-1)");
                continue;
            }
            result.add(new DropDef(type, type == DropType.MATERIAL ? null : itemName.toLowerCase(Locale.ROOT),
                    material, amount, chance));
        }
        return List.copyOf(result);
    }

    private SkillDef skill(ConfigurationSection mob, String mobKey) {
        Object value = mob.get("skill");
        if (value == null) {
            warnIfInvalidSection(mob, "skill", mobKey);
            return null;
        }
        if (!(value instanceof ConfigurationSection skills)) {
            plugin.getLogger().warning("mobs.yml: skill for '" + mobKey + "' must be a section; ignoring it");
            return null;
        }
        Object onHitValue = skills.get("on-hit");
        if (!(onHitValue instanceof ConfigurationSection onHit)) {
            plugin.getLogger().warning("mobs.yml: skill.on-hit for '" + mobKey
                    + "' must be a section; ignoring it");
            return null;
        }

        PotionEffectType effect = effectType(onHit.get("effect"));
        int amplifier = integer(onHit, "amplifier", 0);
        int duration = integer(onHit, "duration", 100);
        int cooldown = integer(onHit, "cooldown", 0);
        double chance = decimal(onHit, "chance", 1);
        if (effect == null || amplifier < 0 || amplifier > MAX_AMPLIFIER
                || duration < 1 || duration > MAX_EFFECT_DURATION
                || cooldown < 0 || cooldown > MAX_COOLDOWN_SECONDS
                || !Double.isFinite(chance) || chance < 0 || chance > 1) {
            plugin.getLogger().warning("mobs.yml: invalid skill.on-hit for '" + mobKey
                    + "' (valid effect, amplifier 0-" + MAX_AMPLIFIER + ", duration 1-" + MAX_EFFECT_DURATION
                    + ", chance 0-1, cooldown 0-" + MAX_COOLDOWN_SECONDS + " seconds required)");
            return null;
        }
        return new SkillDef(effect, amplifier, duration, chance, cooldown);
    }

    private void warnIfInvalidSection(ConfigurationSection section, String path, String mobKey) {
        if (section.contains(path)) {
            plugin.getLogger().warning("mobs.yml: " + path + " for '" + mobKey + "' has an invalid value; ignoring it");
        }
    }

    private Material materialFor(String name) {
        try {
            return Material.matchMaterial(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Double number(ConfigurationSection section, String key, double fallback, String mobKey) {
        if (!section.contains(key)) return fallback;
        Object value = section.get(key);
        if (value instanceof Number number && Double.isFinite(number.doubleValue())) return number.doubleValue();
        plugin.getLogger().warning("mobs.yml: " + key + " for '" + mobKey + "' must be a finite number");
        return null;
    }

    private static int integer(Map<?, ?> values, String key, int fallback) {
        return integer(values.get(key), fallback);
    }

    private static int integer(ConfigurationSection values, String key, int fallback) {
        return integer(values.get(key), fallback);
    }

    private static int integer(Object value, int fallback) {
        if (value == null) return fallback;
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())
                || number.doubleValue() != Math.rint(number.doubleValue())
                || number.doubleValue() < Integer.MIN_VALUE || number.doubleValue() > Integer.MAX_VALUE) {
            return Integer.MIN_VALUE;
        }
        return number.intValue();
    }

    private static double decimal(Map<?, ?> values, String key, double fallback) {
        return decimal(values.get(key), fallback);
    }

    private static double decimal(ConfigurationSection values, String key, double fallback) {
        return decimal(values.get(key), fallback);
    }

    private static double decimal(Object value, double fallback) {
        return value == null ? fallback : value instanceof Number number ? number.doubleValue() : Double.NaN;
    }

    private PotionEffectType effectType(Object value) {
        if (!(value instanceof String name) || name.isBlank()) return null;
        try {
            String keyText = name.toLowerCase(Locale.ROOT);
            NamespacedKey key = keyText.contains(":") ? NamespacedKey.fromString(keyText) : NamespacedKey.minecraft(keyText);
            return key == null ? null : Registry.EFFECT.get(key);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public MobDef get(String key) {
        return key == null ? null : byKey.get(key.toLowerCase(Locale.ROOT));
    }

    public Collection<MobDef> all() {
        return byKey.values();
    }

    public LivingEntity spawn(MobDef def, org.bukkit.Location location) {
        LivingEntity entity = (LivingEntity) location.getWorld().spawnEntity(location, def.type());
        apply(entity, def);
        entity.getPersistentDataContainer().set(plugin.mobKey(), PersistentDataType.STRING, def.key());
        return entity;
    }

    private void apply(LivingEntity entity, MobDef def) {
        entity.customName(def.name());
        entity.setCustomNameVisible(true);
        entity.setPersistent(true);
        var health = entity.getAttribute(Attribute.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(def.health());
            entity.setHealth(def.health());
        }
        var speed = entity.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null) speed.setBaseValue(def.speed());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCustomMobHit(EntityDamageByEntityEvent event) {
        LivingEntity attacker = attacker(event.getDamager());
        if (attacker == null || !(event.getEntity() instanceof LivingEntity target)) return;
        MobDef def = customMob(attacker);
        if (def == null || def.skill() == null) return;

        SkillDef skill = def.skill();
        long now = System.currentTimeMillis();
        var pdc = attacker.getPersistentDataContainer();
        Long lastUsed = pdc.get(skillLastUsedKey, PersistentDataType.LONG);
        if (skill.cooldownSeconds() > 0 && lastUsed != null
                && now - lastUsed < skill.cooldownSeconds() * 1000L) return;
        if (ThreadLocalRandom.current().nextDouble() >= skill.chance()) return;

        target.addPotionEffect(new PotionEffect(skill.effect(), skill.duration(), skill.amplifier()), false);
        if (skill.cooldownSeconds() > 0) pdc.set(skillLastUsedKey, PersistentDataType.LONG, now);
    }

    private static LivingEntity attacker(org.bukkit.entity.Entity damager) {
        if (damager instanceof LivingEntity living) return living;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof LivingEntity shooter) {
            return shooter;
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCustomMobDeath(EntityDeathEvent event) {
        MobDef def = customMob(event.getEntity());
        if (def == null || def.drops().isEmpty()) return;
        for (DropDef drop : def.drops()) {
            if (ThreadLocalRandom.current().nextDouble() >= drop.chance()) continue;
            ItemStack stack = switch (drop.type()) {
                case ITEM -> plugin.items().stack(plugin.items().get(drop.key()));
                case BLOCK -> plugin.blocks().stack(plugin.blocks().get(drop.key()));
                case MATERIAL -> ItemStack.of(drop.material());
            };
            stack.setAmount(drop.amount());
            event.getDrops().add(stack);
        }
    }

    private MobDef customMob(LivingEntity entity) {
        String key = entity.getPersistentDataContainer().get(plugin.mobKey(), PersistentDataType.STRING);
        return key == null ? null : byKey.get(key.toLowerCase(Locale.ROOT));
    }

    /** Re-apply runtime-only attributes when a custom mob is loaded from a saved chunk. */
    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        for (var entity : event.getChunk().getEntities()) {
            if (entity instanceof LivingEntity living) {
                MobDef def = customMob(living);
                if (def != null) apply(living, def);
            }
        }
    }
}
