package com.chatbiasa.customitems;

import org.bukkit.plugin.java.JavaPlugin;

public final class CustomItemsPlugin extends JavaPlugin {

    private Items items;
    private Blocks blocks;
    private Mobs mobs;
    private PackBuilder pack;
    private PackServer packServer;
    private GuiMenu gui;
    private String packUrl;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        items = new Items(this);
        blocks = new Blocks(this);
        mobs = new Mobs(this);
        pack = new PackBuilder(this);
        loadItems();
        try {
            pack.build();
        } catch (Exception e) {
            getLogger().severe("Failed to build resource pack: " + e.getMessage());
        }
        try {
            packServer = new PackServer(this);
            packUrl = packServer.start();
        } catch (Exception e) {
            getLogger().severe("Failed to start pack webserver: " + e.getMessage());
        }
        getServer().getPluginManager().registerEvents(new JoinListener(this), this);
        getServer().getPluginManager().registerEvents(new BlockListener(this), this);
        gui = new GuiMenu(this);
        getServer().getPluginManager().registerEvents(gui, this);
        getCommand("ci").setExecutor(new GiveCommand(this));
    }

    @Override
    public void onDisable() {
        if (packServer != null) packServer.stop();
    }

    public void loadItems() {
        items.load();
        blocks.load();
        mobs.load();
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
}
