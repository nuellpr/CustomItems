package com.chatbiasa.customitems;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;

public final class CustomItemsPlugin extends JavaPlugin {

    private static final List<String> DEFAULT_TEXTURES = List.of(
            "ruby_sword.png", "marble_block.png", "admin.png", "smile.png");

    private Items items;
    private Blocks blocks;
    private Mobs mobs;
    private Ranks ranks;
    private Emojis emojis;
    private Recipes recipes;
    private Sounds sounds;
    private PackBuilder pack;
    private PackServer packServer;
    private GuiMenu gui;
    private ItemBehaviorListener itemBehavior;
    private String packUrl;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        installDefaultTextures();
        items = new Items(this);
        blocks = new Blocks(this);
        mobs = new Mobs(this);
        ranks = new Ranks(this);
        emojis = new Emojis(this);
        recipes = new Recipes(this);
        sounds = new Sounds(this);
        pack = new PackBuilder(this);
        loadItems();
        try {
            pack.build();
        } catch (Exception e) {
            getLogger().severe("Failed to build resource pack: " + e.getMessage());
        }
        try {
            refreshPackServer();
        } catch (Exception e) {
            getLogger().severe("Failed to start pack webserver: " + e.getMessage());
        }
        getServer().getPluginManager().registerEvents(new JoinListener(this), this);
        getServer().getPluginManager().registerEvents(new BlockListener(this), this);
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getServer().getPluginManager().registerEvents(mobs, this);
        itemBehavior = new ItemBehaviorListener(this);
        getServer().getPluginManager().registerEvents(itemBehavior, this);
        itemBehavior.start();
        gui = new GuiMenu(this);
        getServer().getPluginManager().registerEvents(gui, this);
        getCommand("ci").setExecutor(new GiveCommand(this));
        blocks.startSaveTask();
        JoinListener.migrateOnline(this);
    }

    private void installDefaultTextures() {
        File textureDirectory = new File(getDataFolder(), "textures");
        for (String texture : DEFAULT_TEXTURES) {
            File target = new File(textureDirectory, texture);
            if (target.isFile()) continue;
            if (target.exists()) {
                getLogger().warning("Default texture path is not a file: " + target.getName());
                continue;
            }
            try {
                saveResource("textures/" + texture, false);
            } catch (IllegalArgumentException e) {
                getLogger().warning("Could not install default texture " + texture + ": " + e.getMessage());
            }
        }
    }

    @Override
    public void onDisable() {
        if (itemBehavior != null) itemBehavior.shutdown();
        if (blocks != null) blocks.shutdown();
        if (packServer != null) packServer.stop();
    }

    public void loadItems() {
        items.load();
        blocks.load();
        mobs.load();
        ranks.load();
        emojis.load();
        recipes.load();
        sounds.load();
    }

    public Items items() {
        return items;
    }

    public Blocks blocks() {
        return blocks;
    }

    public Mobs mobs() {
        return mobs;
    }

    public Ranks ranks() {
        return ranks;
    }

    public Emojis emojis() {
        return emojis;
    }

    public Recipes recipes() {
        return recipes;
    }

    public Sounds sounds() {
        return sounds;
    }

    public org.bukkit.NamespacedKey mobKey() {
        return new org.bukkit.NamespacedKey(this, "cmob");
    }

    public PackBuilder pack() {
        return pack;
    }

    public GuiMenu gui() {
        return gui;
    }

    public String packUrl() {
        return packUrl;
    }

    public boolean migrateItemModel(org.bukkit.inventory.ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return false;
        return items.migrateModel(stack) || blocks.migrateModel(stack);
    }

    public void refreshPackServer() throws java.io.IOException {
        if (packServer == null) packServer = new PackServer(this);
        packUrl = packServer.start();
    }
}
