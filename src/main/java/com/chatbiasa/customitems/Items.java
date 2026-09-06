package com.chatbiasa.customitems;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import io.papermc.paper.datacomponent.item.ItemLore;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Items {

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
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
        ConfigurationSection items = yml.getConfigurationSection("items");
        if (items == null) return;
        for (String key : items.getKeys(false)) {
            ConfigurationSection s = items.getConfigurationSection(key);
            if (s == null) continue;
            Material base = Material.matchMaterial(s.getString("base", "DIAMOND_SWORD"));
            if (base == null) {
                plugin.getLogger().warning("items.yml: unknown base material for '" + key + "'");
                continue;
            }
            String texture = s.getString("texture", key + ".png");
            ItemDef def = new ItemDef(
                    key.toLowerCase(),
                    base,
                    texture,
                    MiniMessage.miniMessage().deserialize(s.getString("name", key)),
                    s.getStringList("lore").stream()
                            .map(l -> MiniMessage.miniMessage().deserialize(l))
                            .toList()
            );
            byKey.put(def.key(), def);
        }
        plugin.getLogger().info("Loaded " + byKey.size() + " custom items");
    }

    public ItemDef get(String key) {
        return byKey.get(key.toLowerCase());
    }

    public Collection<ItemDef> all() {
        return byKey.values();
    }

    public ItemStack stack(ItemDef def) {
        ItemStack s = ItemStack.of(def.base());
        s.setData(DataComponentTypes.CUSTOM_MODEL_DATA,
                CustomModelData.customModelData().addString(def.key()).build());
        s.setData(DataComponentTypes.CUSTOM_NAME, def.name());
        if (!def.lore().isEmpty()) {
            s.setData(DataComponentTypes.LORE, ItemLore.lore(def.lore()));
        }
        s.editPersistentDataContainer(pdc -> pdc.set(idKey, PersistentDataType.STRING, def.key()));
        return s;
    }

    public String id(ItemStack stack) {
        return stack.getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
    }

    public List<String> keys() {
        return List.copyOf(byKey.keySet());
    }
}
