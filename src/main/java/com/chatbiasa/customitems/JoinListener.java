package com.chatbiasa.customitems;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class JoinListener implements Listener {

    private final CustomItemsPlugin plugin;

    public JoinListener(CustomItemsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        byte[] sha1 = plugin.pack().sha1;
        if (sha1 != null) {
            event.getPlayer().setResourcePack(plugin.packUrl(), sha1);
        }
    }
}
