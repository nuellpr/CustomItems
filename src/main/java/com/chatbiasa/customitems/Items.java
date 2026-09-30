package com.chatbiasa.customitems;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class Items {

    private static final double MAX_ATTRIBUTE_BONUS = 1024;
    private static final int MAX_EFFECT_DURATION = 1_200_000;
    private static final int MAX_HELD_DURATION = 40;
    private static final int MIN_HELD_DURATION = 20;
    private static final int MAX_AMPLIFIER = 255;
    private static final int MAX_COOLDOWN_SECONDS = 3600;
    private static final double MAX_HEAL = 2048;

    private final JavaPlugin plugin;
    private final NamespacedKey idKey;
    private final Map<String, ItemDef> byKey = new HashMap<>();

    public Items(JavaPlugin plugin) {
        this.plugin = plugin;
        this.idKey = new NamespacedKey(plugin, "item");
    }

    public void load() {
        byKey.clear();
        File f = new File(plugin.getDataFolder(), "items.yml");
        if (!f.exists()) {
            plugin.saveResource("items.yml", false);
        }
        loadFile(f);
        File imports = new File(plugin.getDataFolder(), "imports");
        File[] imported = imports.listFiles(file -> file.isFile() && file.getName().endsWith(".yml"));
        if (imported != null) {
            Arrays.sort(imported, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
            for (File file : imported) loadFile(file);
        }
        plugin.getLogger().info("Loaded " + byKey.size() + " custom items");
    }

    private void loadFile(File f) {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
        ConfigurationSection items = yml.getConfigurationSection("items");
        if (items == null) return;
        for (String key : items.getKeys(false)) {
            ConfigurationSection s = items.getConfigurationSection(key);
            if (s == null) continue;
            String lower = key.toLowerCase(Locale.ROOT);
            if (!lower.matches("[a-z0-9_-]+")) {
                plugin.getLogger().warning("items.yml: invalid item key '" + key + "' (use a-z, 0-9, _ or -)");
                continue;
            }
            if (byKey.containsKey(lower)) {
                plugin.getLogger().warning("items.yml: duplicate item key after normalization: '" + key + "'");
                continue;
            }
            Material base = Material.matchMaterial(s.getString("base", "DIAMOND_SWORD"));
            if (base == null) {
                plugin.getLogger().warning("items.yml: unknown base material for '" + key + "'");
                continue;
            }
            String texture = s.getString("texture", key + ".png");
            String model = externalModel(s, key);
            ItemDef def = new ItemDef(
                    lower,
                    base,
                    texture,
                    model,
                    MiniMessage.miniMessage().deserialize(s.getString("name", key)),
                    s.getStringList("lore").stream()
                            .map(l -> MiniMessage.miniMessage().deserialize(l))
                            .toList(),
                    attributeBonus(s, "damage", key),
                    attributeBonus(s, "armor", key),
                    attributeBonus(s, "attack-speed", key),
                    effects(s, "effects.held", key, 40, MIN_HELD_DURATION, MAX_HELD_DURATION),
                    effects(s, "effects.use", key, 100, 1, MAX_EFFECT_DURATION),
                    abilities(s, "on-hit", key),
                    abilities(s, "on-right-click", key)
            );
            byKey.put(def.key(), def);
        }
    }

    private String externalModel(ConfigurationSection item, String key) {
        String value = item.getString("model");
        if (value == null || value.isBlank()) return null;
        NamespacedKey model = NamespacedKey.fromString(value.toLowerCase(Locale.ROOT));
        if (model == null) {
            plugin.getLogger().warning("items.yml: invalid model for '" + key
                    + "' (expected namespace:path); using the generated texture model");
            return null;
        }
        return model.toString();
    }

    public ItemDef get(String key) {
        return key == null ? null : byKey.get(key.toLowerCase(Locale.ROOT));
    }

    public Collection<ItemDef> all() {
        return byKey.values();
    }

    public ItemStack stack(ItemDef def) {
        ItemStack s = ItemStack.of(def.base());
        s.setData(DataComponentTypes.ITEM_MODEL, new NamespacedKey(plugin, def.key()));
        s.setData(DataComponentTypes.CUSTOM_NAME, def.name());
        if (!def.lore().isEmpty()) {
            s.setData(DataComponentTypes.LORE, ItemLore.lore(def.lore()));
        }
        s.editPersistentDataContainer(pdc -> pdc.set(idKey, PersistentDataType.STRING, def.key()));
        if (hasAttributes(def)) {
            ItemMeta meta = s.getItemMeta();
            // Adding an explicit modifier component replaces the material defaults, so copy them
            // first to keep the base item's vanilla damage/armor/speed and add our bonuses on top.
            meta.setAttributeModifiers(def.base().getDefaultAttributeModifiers());
            addModifier(meta, def, Attribute.ATTACK_DAMAGE, def.damageBonus(), "damage", EquipmentSlotGroup.MAINHAND);
            addModifier(meta, def, Attribute.ARMOR, def.armorBonus(), "armor", armorSlot(def.base()));
            addModifier(meta, def, Attribute.ATTACK_SPEED, def.attackSpeedBonus(), "attack_speed", EquipmentSlotGroup.MAINHAND);
            s.setItemMeta(meta);
        }
        return s;
    }

    private Double attributeBonus(ConfigurationSection section, String field, String key) {
        if (!section.contains(field)) return null;
        Object value = section.get(field);
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())
                || Math.abs(number.doubleValue()) > MAX_ATTRIBUTE_BONUS) {
            plugin.getLogger().warning("items.yml: " + field + " for '" + key + "' must be a number between -"
                    + MAX_ATTRIBUTE_BONUS + " and " + MAX_ATTRIBUTE_BONUS + "; ignoring it");
            return null;
        }
        return number.doubleValue();
    }

    private List<ItemDef.PotionEffectDef> effects(ConfigurationSection item, String path, String key,
                                                   int defaultDuration, int minDuration, int maxDuration) {
        Object value = item.get(path);
        if (value == null) {
            warnIfParentIsInvalid(item, path, key);
            return List.of();
        }
        if (!(value instanceof List<?> entries)) {
            plugin.getLogger().warning("items.yml: " + path + " for '" + key + "' must be a list; ignoring it");
            return List.of();
        }
        List<ItemDef.PotionEffectDef> result = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            if (!(entries.get(i) instanceof Map<?, ?> entry)) {
                plugin.getLogger().warning("items.yml: invalid " + path + " entry " + i + " for '" + key + "'");
                continue;
            }
            PotionEffectType type = effectType(entry.get("type"));
            int amplifier = integer(entry, "amplifier", 0);
            int duration = integer(entry, "duration", defaultDuration);
            if (type == null || amplifier < 0 || amplifier > MAX_AMPLIFIER
                    || duration < minDuration || duration > maxDuration) {
                plugin.getLogger().warning("items.yml: invalid " + path + " effect " + i + " for '" + key
                        + "' (use a valid effect, amplifier 0-" + MAX_AMPLIFIER + ", duration "
                        + minDuration + "-" + maxDuration + " ticks)");
                continue;
            }
            result.add(new ItemDef.PotionEffectDef(type, amplifier, duration));
        }
        return List.copyOf(result);
    }

    private List<ItemDef.Ability> abilities(ConfigurationSection item, String trigger, String key) {
        String path = "abilities." + trigger;
        Object value = item.get(path);
        if (value == null) {
            warnIfParentIsInvalid(item, path, key);
            return List.of();
        }
        if (!(value instanceof List<?> entries)) {
            plugin.getLogger().warning("items.yml: " + path + " for '" + key + "' must be a list; ignoring it");
            return List.of();
        }
        List<ItemDef.Ability> result = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            if (!(entries.get(i) instanceof Map<?, ?> entry)) {
                plugin.getLogger().warning("items.yml: invalid " + path + " entry " + i + " for '" + key + "'");
                continue;
            }
            String type = string(entry.get("type"));
            double chance = decimal(entry, "chance", 1);
            int cooldown = integer(entry, "cooldown", 0);
            if (!Double.isFinite(chance) || chance < 0 || chance > 1
                    || cooldown < 0 || cooldown > MAX_COOLDOWN_SECONDS) {
                plugin.getLogger().warning("items.yml: invalid chance/cooldown in " + path + " entry " + i
                        + " for '" + key + "' (chance 0-1, cooldown 0-" + MAX_COOLDOWN_SECONDS + " seconds)");
                continue;
            }
            if ("potion".equalsIgnoreCase(type)) {
                PotionEffectType effect = effectType(entry.get("effect"));
                int amplifier = integer(entry, "amplifier", 0);
                int duration = integer(entry, "duration", 40);
                if (effect == null || amplifier < 0 || amplifier > MAX_AMPLIFIER
                        || duration < 1 || duration > MAX_EFFECT_DURATION) {
                    plugin.getLogger().warning("items.yml: invalid potion ability in " + path + " entry " + i
                            + " for '" + key + "' (valid effect, amplifier 0-" + MAX_AMPLIFIER
                            + ", duration 1-" + MAX_EFFECT_DURATION + " ticks required)");
                    continue;
                }
                result.add(new ItemDef.Ability(ItemDef.AbilityType.POTION, effect, amplifier,
                        duration, 0, chance, cooldown));
            } else if ("heal".equalsIgnoreCase(type) && trigger.equals("on-right-click")) {
                double amount = decimal(entry, "amount", Double.NaN);
                if (!Double.isFinite(amount) || amount <= 0 || amount > MAX_HEAL) {
                    plugin.getLogger().warning("items.yml: invalid heal amount in " + path + " entry " + i
                            + " for '" + key + "' (0 < amount <= " + MAX_HEAL + ")");
                    continue;
                }
                result.add(new ItemDef.Ability(ItemDef.AbilityType.HEAL, null, 0, 0,
                        amount, chance, cooldown));
            } else {
                plugin.getLogger().warning("items.yml: unsupported ability type '" + type + "' in " + path
                        + " entry " + i + " for '" + key + "'; use potion"
                        + (trigger.equals("on-right-click") ? " or heal" : ""));
            }
        }
        return List.copyOf(result);
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

    private void warnIfParentIsInvalid(ConfigurationSection item, String path, String key) {
        String parentPath = path.substring(0, path.lastIndexOf('.'));
        if (item.contains(parentPath) && !(item.get(parentPath) instanceof ConfigurationSection)) {
            plugin.getLogger().warning("items.yml: " + parentPath + " for '" + key
                    + "' must be a section; ignoring " + parentPath + " options");
        }
    }

    private static int integer(Map<?, ?> values, String key, int fallback) {
        Object value = values.get(key);
        if (value == null) return fallback;
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())
                || number.doubleValue() != Math.rint(number.doubleValue())
                || number.doubleValue() < Integer.MIN_VALUE || number.doubleValue() > Integer.MAX_VALUE) {
            return Integer.MIN_VALUE;
        }
        return number.intValue();
    }

    private static double decimal(Map<?, ?> values, String key, double fallback) {
        Object value = values.get(key);
        return value == null ? fallback : value instanceof Number number ? number.doubleValue() : Double.NaN;
    }

    private static String string(Object value) { return value instanceof String text ? text : ""; }

    private static boolean hasAttributes(ItemDef def) {
        return def.damageBonus() != null || def.armorBonus() != null || def.attackSpeedBonus() != null;
    }

    private void addModifier(ItemMeta meta, ItemDef def, Attribute attribute, Double amount,
                             String name, EquipmentSlotGroup slot) {
        if (amount == null || amount == 0) return;
        NamespacedKey key = new NamespacedKey(plugin, "items/" + def.key() + "/" + name);
        meta.addAttributeModifier(attribute,
                new AttributeModifier(key, amount, AttributeModifier.Operation.ADD_NUMBER, slot));
    }

    private static EquipmentSlotGroup armorSlot(Material material) {
        return switch (material.getEquipmentSlot()) {
            case HEAD -> EquipmentSlotGroup.HEAD;
            case CHEST -> EquipmentSlotGroup.CHEST;
            case LEGS -> EquipmentSlotGroup.LEGS;
            case FEET -> EquipmentSlotGroup.FEET;
            case BODY -> EquipmentSlotGroup.BODY;
            case OFF_HAND -> EquipmentSlotGroup.OFFHAND;
            case SADDLE -> EquipmentSlotGroup.SADDLE;
            default -> EquipmentSlotGroup.MAINHAND;
        };
    }

    /** Upgrade stacks created by versions which selected models through CUSTOM_MODEL_DATA. */
    public boolean migrateModel(ItemStack stack) {
        String key = id(stack);
        if (key == null) return false;
        ItemDef def = byKey.get(key.toLowerCase(Locale.ROOT));
        if (def == null) return false;
        NamespacedKey model = new NamespacedKey(plugin, def.key());
        if (model.equals(stack.getData(DataComponentTypes.ITEM_MODEL))
                && !stack.hasData(DataComponentTypes.CUSTOM_MODEL_DATA)) return false;
        stack.setData(DataComponentTypes.ITEM_MODEL, model);
        stack.unsetData(DataComponentTypes.CUSTOM_MODEL_DATA);
        return true;
    }

    public String id(ItemStack stack) {
        return stack.getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
    }

    public List<String> keys() {
        return List.copyOf(byKey.keySet());
    }
}
