package com.chatbiasa.customitems;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class Blocks {

    private record State(String instrument, int note) {}

    private record ConfigBlock(String key, String name, ConfigurationSection section) {}

    /**
     * Noteblock instrument name -> Bukkit Instrument enum name, covering all 27 values the
     * instrument blockstate accepts. Only used to turn a placed noteblock back into a lookup key.
     * Missing one would make {@link #stateName} fall back to "harp" and misreport the block, so
     * this table is kept in sync with org.bukkit.Instrument.
     */
    static final String[][] ALL_INSTRUMENTS = {
            {"harp", "PIANO"}, {"basedrum", "BASS_DRUM"}, {"snare", "SNARE_DRUM"}, {"hat", "STICKS"},
            {"bass", "BASS_GUITAR"}, {"flute", "FLUTE"}, {"bell", "BELL"}, {"guitar", "GUITAR"},
            {"chime", "CHIME"}, {"xylophone", "XYLOPHONE"}, {"iron_xylophone", "IRON_XYLOPHONE"},
            {"cow_bell", "COW_BELL"}, {"didgeridoo", "DIDGERIDOO"}, {"bit", "BIT"}, {"banjo", "BANJO"},
            {"pling", "PLING"}, {"trumpet", "TRUMPET"}, {"trumpet_exposed", "TRUMPET_EXPOSED"},
            {"trumpet_oxidized", "TRUMPET_OXIDIZED"}, {"trumpet_weathered", "TRUMPET_WEATHERED"},
            {"zombie", "ZOMBIE"}, {"skeleton", "SKELETON"}, {"creeper", "CREEPER"},
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

    /**
     * A custom block is identified purely by its note block blockstate, so the number of distinct
     * blocks is capped by the states we assign: INSTRUMENTS.length * 25 notes. Past that the
     * instrument/note pair wraps around and silently overwrites an earlier block's identity, so
     * we stop instead.
     */
    public static final int MAX_BLOCKS = INSTRUMENTS.length * 25;

    public record BlockDef(String key, String texture, Component name, String instrument, int note) {}

    private final CustomItemsPlugin plugin;
    private final NamespacedKey pdcKey;
    private final Map<String, BlockDef> byKey = new LinkedHashMap<>();
    /** A noteblock has no block entity, so its instrument+note state IS the identity. */
    private final Map<State, BlockDef> byState = new LinkedHashMap<>();
    /** Persisted IDs keep existing placed blocks stable when blocks.yml is edited or reordered. */
    private final Map<String, State> stateAssignments = new LinkedHashMap<>();
    /** "world,x,y,z" of blocks a player placed from a custom block item. A vanilla noteblock can be
     *  right-clicked into any instrument+note the same way, so blockstate alone cannot prove a block
     *  is ours -- without this set a player converts unlimited vanilla note blocks into custom ones. */
    private final Set<String> placed = new HashSet<>();
    private File placedFile;
    private File stateFile;
    private BukkitTask saveTask;
    private boolean placedLoaded;
    private boolean placedDirty;

    public Blocks(CustomItemsPlugin plugin) {
        this.plugin = plugin;
        this.pdcKey = new NamespacedKey(plugin, "cblock");
        this.placedFile = new File(plugin.getDataFolder(), "placed-blocks.txt");
        this.stateFile = new File(plugin.getDataFolder(), "block-states.yml");
    }

    public void load() {
        byKey.clear();
        byState.clear();
        if (!new File(plugin.getDataFolder(), "blocks.yml").exists()) plugin.saveResource("blocks.yml", false);
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "blocks.yml"));
        // accept both a "blocks:" wrapper (as documented in the README) and bare root-level keys
        ConfigurationSection root = yml.getConfigurationSection("blocks");
        if (root == null) root = yml;
        if (!loadStateAssignments()) {
            plugin.getLogger().severe("Custom blocks are disabled because block-states.yml is invalid; "
                    + "restore a valid backup before loading blocks to prevent world states from being reassigned.");
            return;
        }
        boolean statesChanged = false;
        Set<State> usedStates = new HashSet<>(stateAssignments.values());

        List<ConfigBlock> definitions = new ArrayList<>();
        Set<String> activeKeys = new HashSet<>();
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) continue;
            String lower = key.toLowerCase(Locale.ROOT);
            if (!validKey(lower)) {
                plugin.getLogger().warning("blocks.yml: invalid block key '" + key + "' (use a-z, 0-9, _ or -)");
            } else if (!activeKeys.add(lower)) {
                plugin.getLogger().warning("blocks.yml: duplicate block key after normalization: '" + key + "'");
            } else {
                definitions.add(new ConfigBlock(lower, key, section));
            }
        }

        // On first upgrade, preserve the exact order-based IDs used by versions through 0.8.0.
        // Save them so future additions/removals cannot silently remap already-placed blocks.
        if (!stateFile.isFile()) {
            for (int i = 0; i < Math.min(definitions.size(), MAX_BLOCKS); i++) {
                State state = stateAt(i);
                stateAssignments.put(definitions.get(i).key(), state);
                usedStates.add(state);
                statesChanged = true;
            }
        }

        for (ConfigBlock entry : definitions) {
            State state = stateAssignments.get(entry.key());
            if (state == null) {
                if (stateAssignments.size() >= MAX_BLOCKS) {
                    plugin.getLogger().warning("No unused custom-block states remain; '" + entry.name() + "' is ignored. "
                            + "Maximum is " + MAX_BLOCKS + ". Removed keys stay reserved to protect existing world blocks.");
                    continue;
                }
                state = nextFreeState(usedStates);
                if (state == null) {
                    plugin.getLogger().warning("No unused custom-block states remain; '" + entry.name() + "' is ignored.");
                    continue;
                }
                stateAssignments.put(entry.key(), state);
                usedStates.add(state);
                statesChanged = true;
            }
            BlockDef def = new BlockDef(entry.key(), entry.section().getString("texture", entry.key() + ".png"),
                    MiniMessage.miniMessage().deserialize(entry.section().getString("name", "<white>" + entry.name())),
                    state.instrument(), state.note());
            byKey.put(entry.key(), def);
            byState.put(state, def);
        }
        if (statesChanged && !saveStateAssignments()) {
            byKey.clear();
            byState.clear();
            plugin.getLogger().severe("Custom blocks are disabled because block-states.yml could not be saved.");
            return;
        }
        plugin.getLogger().info("Loaded " + byKey.size() + " custom blocks");
        if (!placedLoaded) loadPlaced();
    }

    private static boolean validKey(String key) {
        return key.matches("[a-z0-9_-]+");
    }

    /** Keep this ordering compatible with the original 0.8.0 implicit allocation. */
    private static State stateAt(int index) {
        String[] instrument = INSTRUMENTS[index % INSTRUMENTS.length];
        return new State(instrument[0], index / INSTRUMENTS.length);
    }

    private State nextFreeState(Set<State> used) {
        for (int i = 0; i < MAX_BLOCKS; i++) {
            State candidate = stateAt(i);
            if (!used.contains(candidate)) return candidate;
        }
        return null;
    }

    private boolean loadStateAssignments() {
        stateAssignments.clear();
        if (!stateFile.isFile()) return true;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(stateFile);
        ConfigurationSection section = yml.getConfigurationSection("blocks");
        if (section == null) return false;
        Set<State> used = new HashSet<>();
        Set<String> usedKeys = new HashSet<>();
        boolean valid = true;
        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) continue;
            String instrument = entry.getString("instrument", "").toLowerCase(Locale.ROOT);
            int note = entry.getInt("note", -1);
            boolean knownInstrument = false;
            for (String[] candidate : INSTRUMENTS) {
                if (candidate[0].equals(instrument)) {
                    knownInstrument = true;
                    break;
                }
            }
            State state = new State(instrument, note);
            String lower = key.toLowerCase(Locale.ROOT);
            if (!validKey(lower) || !usedKeys.add(lower) || !knownInstrument || note < 0 || note >= 25 || !used.add(state)) {
                plugin.getLogger().warning("block-states.yml: ignoring invalid or duplicate assignment for '" + key + "'");
                valid = false;
                continue;
            }
            stateAssignments.put(lower, state);
        }
        return valid;
    }

    private boolean saveStateAssignments() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<String, State> entry : stateAssignments.entrySet()) {
            yml.set("blocks." + entry.getKey() + ".instrument", entry.getValue().instrument());
            yml.set("blocks." + entry.getKey() + ".note", entry.getValue().note());
        }
        try {
            File temp = new File(stateFile.getParentFile(), stateFile.getName() + ".tmp");
            yml.save(temp);
            moveReplace(temp, stateFile);
            return true;
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save block-states.yml: " + e.getMessage());
            return false;
        }
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
        return byState.get(new State(instrument, note));
    }

    /** custom block a placed noteblock currently represents, or null */
    public BlockDef byNoteBlock(org.bukkit.block.data.type.NoteBlock nb) {
        return byState(stateName(nb.getInstrument()), nb.getNote().getId());
    }

    public BlockDef get(String key) {
        return key == null ? null : byKey.get(key.toLowerCase(Locale.ROOT));
    }

    public List<BlockDef> all() {
        return new ArrayList<>(byKey.values());
    }

    public ItemStack stack(BlockDef def) {
        ItemStack st = ItemStack.of(Material.NOTE_BLOCK);
        st.setData(io.papermc.paper.datacomponent.DataComponentTypes.ITEM_MODEL,
                new NamespacedKey(plugin, "block/" + def.key()));
        st.setData(io.papermc.paper.datacomponent.DataComponentTypes.CUSTOM_NAME, def.name());
        st.editPersistentDataContainer(pdc -> pdc.set(pdcKey, PersistentDataType.STRING, def.key()));
        return st;
    }

    /** Upgrade block items created by versions which selected models through CUSTOM_MODEL_DATA. */
    public boolean migrateModel(ItemStack stack) {
        String key = id(stack);
        if (key == null) return false;
        BlockDef def = byKey.get(key.toLowerCase(Locale.ROOT));
        if (def == null) return false;
        NamespacedKey model = new NamespacedKey(plugin, "block/" + def.key());
        var customModelData = io.papermc.paper.datacomponent.DataComponentTypes.CUSTOM_MODEL_DATA;
        if (model.equals(stack.getData(io.papermc.paper.datacomponent.DataComponentTypes.ITEM_MODEL))
                && !stack.hasData(customModelData)) return false;
        stack.setData(io.papermc.paper.datacomponent.DataComponentTypes.ITEM_MODEL, model);
        stack.unsetData(customModelData);
        return true;
    }

    /** custom block key stored on an item, or null */
    public String id(ItemStack st) {
        if (st == null) return null;
        return st.getPersistentDataContainer().get(pdcKey, PersistentDataType.STRING);
    }

    private static String coord(org.bukkit.Location l) {
        return l.getWorld().getName() + "," + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
    }

    public boolean isPlaced(org.bukkit.Location l) {
        return l != null && placed.contains(coord(l));
    }

    public void markPlaced(org.bukkit.Location l) {
        if (l != null && placed.add(coord(l))) placedDirty = true;
    }

    public void forget(org.bukkit.Location l) {
        if (l != null && placed.remove(coord(l))) placedDirty = true;
    }

    private void loadPlaced() {
        placed.clear();
        if (!placedFile.isFile()) {
            placedLoaded = true;
            return;
        }
        try {
            for (String line : Files.readAllLines(placedFile.toPath(), StandardCharsets.UTF_8)) {
                if (!line.isBlank()) placed.add(line.trim());
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Could not read placed-blocks.txt: " + e.getMessage());
            return;
        }
        placedLoaded = true;
    }

    public void startSaveTask() {
        if (saveTask == null) {
            saveTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::flushPlaced, 100L, 100L);
        }
    }

    public void flushPlaced() {
        if (!placedDirty || !placedLoaded) return;
        File temp = new File(placedFile.getParentFile(), placedFile.getName() + ".tmp");
        try {
            Files.write(temp.toPath(), new TreeSet<>(placed), StandardCharsets.UTF_8);
            moveReplace(temp, placedFile);
            placedDirty = false;
        } catch (IOException e) {
            plugin.getLogger().warning("Could not write placed-blocks.txt: " + e.getMessage());
        }
    }

    public void shutdown() {
        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }
        flushPlaced();
    }

    private static void moveReplace(File temp, File target) throws IOException {
        try {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
