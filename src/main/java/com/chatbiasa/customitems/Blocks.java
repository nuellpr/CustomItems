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

    /**
     * Noteblock instrument name -> Bukkit Instrument enum name. Only used to turn a placed
     * noteblock back into a lookup key; the pack does not emit a note_block blockstate, because
     * vanilla has a single "" variant with no instrument keys and any override could only delete
     * the model. Newer instruments (trumpet, zombie, ...) are not listed — we only ever stamp the
     * ten below, and anything else falls back to "harp".
     */
    static final String[][] ALL_INSTRUMENTS = {
            {"harp", "PIANO"}, {"basedrum", "BASS_DRUM"}, {"snare", "SNARE_DRUM"}, {"hat", "STICKS"},
            {"bass", "BASS_GUITAR"}, {"flute", "FLUTE"}, {"bell", "BELL"}, {"guitar", "GUITAR"},
            {"chime", "CHIME"}, {"xylophone", "XYLOPHONE"}, {"iron_xylophone", "IRON_XYLOPHONE"},
            {"cow_bell", "COW_BELL"}, {"didgeridoo", "DIDGERIDOO"}, {"bit", "BIT"}, {"banjo", "BANJO"},
            {"pling", "PLING"}, {"zombie", "ZOMBIE"}, {"skeleton", "SKELETON"}, {"creeper", "CREEPER"},
            {"dragon", "DRAGON"}, {"wither_skeleton", "WITHER_SKELETON"}, {"piglin", "PIGLIN"},
            {"custom_head", "CUSTOM_HEAD"}
    };

    // instruments custom blocks are assigned from; harp excluded (a plain noteblock uses it by default)
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
    /** "instrument:note" -> def. A noteblock has no block entity, so its blockstate IS the identity. */
    private final Map<String, BlockDef> byState = new LinkedHashMap<>();

    public Blocks(CustomItemsPlugin plugin) {
        this.plugin = plugin;
        this.pdcKey = new NamespacedKey(plugin, "cblock");
    }

    public void load() {
        byKey.clear();
        byState.clear();
        if (!new File(plugin.getDataFolder(), "blocks.yml").exists()) plugin.saveResource("blocks.yml", false);
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "blocks.yml"));
        // accept both a "blocks:" wrapper (as documented in the README) and bare root-level keys
        ConfigurationSection root = yml.getConfigurationSection("blocks");
        if (root == null) root = yml;
        int i = 0;
        for (String key : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null) continue;
            String[] inst = INSTRUMENTS[i % INSTRUMENTS.length];
            int note = (i / INSTRUMENTS.length) % 25;
            String lower = key.toLowerCase();
            BlockDef def = new BlockDef(lower, s.getString("texture", lower + ".png"),
                    MiniMessage.miniMessage().deserialize(s.getString("name", "<white>" + key)),
                    inst[0], note);
            byKey.put(lower, def);
            byState.put(inst[0] + ":" + note, def);
            i++;
        }
        plugin.getLogger().info("Loaded " + byKey.size() + " custom blocks");
    }

    /** blockstate instrument name for a Bukkit Instrument, e.g. PIANO -> harp */
    public static String stateName(org.bukkit.Instrument instrument) {
        for (String[] pair : ALL_INSTRUMENTS) {
            if (pair[1].equals(instrument.name())) return pair[0];
        }
        return "harp";
    }

    /** custom block matching instrument+note, or null */
    public BlockDef byState(String instrument, int note) {
        return byState.get(instrument + ":" + note);
    }

    /** custom block a placed noteblock currently represents, or null */
    public BlockDef byNoteBlock(org.bukkit.block.data.type.NoteBlock nb) {
        return byState(stateName(nb.getInstrument()), nb.getNote().getId());
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
        if (st == null) return null;
        return st.getPersistentDataContainer().get(pdcKey, PersistentDataType.STRING);
    }
}
