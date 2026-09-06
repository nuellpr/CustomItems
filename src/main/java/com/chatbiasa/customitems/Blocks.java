package com.chatbiasa.customitems;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Blocks {

    // {blockstate json name, Bukkit enum} pairs, assignment order; harp excluded (most likely vanilla collision)
    static final String[][] INSTRUMENTS = {
            {"banjo", "BANJO"}, {"didgeridoo", "DIDGERIDOO"}, {"pling", "PLING"},
            {"bit", "BIT"}, {"cow_bell", "COW_BELL"}, {"bell", "BELL"},
            {"chime", "CHIME"}, {"xylophone", "XYLOPHONE"}, {"iron_xylophone", "IRON_XYLOPHONE"},
            {"flute", "FLUTE"}
    };

    public record BlockDef(String key, String texture, Component name, String instrument, int note) {}

    private final CustomItemsPlugin plugin;
    private final NamespacedKey pdcKey;
    private final Map<String, BlockDef> byKey = new LinkedHashMap<>();

    public Blocks(CustomItemsPlugin plugin) {
        this.plugin = plugin;
        this.pdcKey = new NamespacedKey(plugin, "cblock");
    }

    public void load() {
        byKey.clear();
        if (!new File(plugin.getDataFolder(), "blocks.yml").exists()) plugin.saveResource("blocks.yml", false);
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "blocks.yml"));
        int i = 0;
        for (String key : yml.getKeys(false)) {
            ConfigurationSection s = yml.getConfigurationSection(key);
            if (s == null) continue;
            String[] inst = INSTRUMENTS[i % INSTRUMENTS.length];
            int note = (i / INSTRUMENTS.length) % 25;
            byKey.put(key, new BlockDef(key, s.getString("texture", key + ".png"),
                    MiniMessage.miniMessage().deserialize(s.getString("name", "<white>" + key)),
                    inst[0], note));
            i++;
        }
        plugin.getLogger().info("Loaded " + byKey.size() + " custom blocks");
    }

    public BlockDef get(String key) {
        return byKey.get(key);
    }

    public List<BlockDef> all() {
        return new ArrayList<>(byKey.values());
    }

    public ItemStack stack(BlockDef def) {
        ItemStack st = ItemStack.of(Material.NOTE_BLOCK);
        st.setData(io.papermc.paper.datacomponent.DataComponentTypes.CUSTOM_MODEL_DATA,
                io.papermc.paper.datacomponent.item.CustomModelData.customModelData().addString(def.key()).build());
        st.setData(io.papermc.paper.datacomponent.DataComponentTypes.CUSTOM_NAME, def.name());
        st.editPersistentDataContainer(pdc -> pdc.set(pdcKey, PersistentDataType.STRING, def.key()));
        return st;
    }

    /** custom block key stored on an item, or null */
    public String id(ItemStack st) {
        if (st == null || !st.hasItemMeta()) return null;
        return st.getItemMeta().getPersistentDataContainer().get(pdcKey, PersistentDataType.STRING);
    }

    /** custom block key stored on a placed block's tile state, or null */
    public String idOf(org.bukkit.block.BlockState state) {
        if (!(state instanceof org.bukkit.block.TileState ts)) return null;
        return ts.getPersistentDataContainer().get(pdcKey, PersistentDataType.STRING);
    }
}
