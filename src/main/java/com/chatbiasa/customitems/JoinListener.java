package com.chatbiasa.customitems;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.UUID;

public final class JoinListener implements Listener {

    /** stable id so the same pack is never queued twice for one player */
    private static final UUID PACK_ID = UUID.fromString("6a1d6c4e-9f3b-4c2a-8e77-2b1f0c9a5d31");

    private final CustomItemsPlugin plugin;

    public JoinListener(CustomItemsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        send(plugin, event.getPlayer());
    }

    /** Queue the current pack for one player. addResourcePack is the non-deprecated path: it queues
     *  the pack and lets the client prompt. force=false so players can decline; they keep playing
     *  with vanilla textures instead. */
    public static void send(CustomItemsPlugin plugin, org.bukkit.entity.Player p) {
        byte[] sha1 = plugin.pack().sha1;
        String url = plugin.packUrl();
        if (sha1 == null || url == null) return;
        p.addResourcePack(PACK_ID, url, sha1, "CustomItems resource pack", false);
    }
}
