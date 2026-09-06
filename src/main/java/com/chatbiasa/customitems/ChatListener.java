package com.chatbiasa.customitems;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class ChatListener implements Listener {

    private final CustomItemsPlugin plugin;

    public ChatListener(CustomItemsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        Ranks.RankDef def = plugin.ranks().forPlayer(event.getPlayer());
        if (def == null) return;
        String glyph = plugin.ranks().glyph(def);
        // renderer runs per-viewer; italic-off so the glyph char doesn't render slanted in chat
        event.renderer((source, displayName, msg, viewer) ->
                Component.join(JoinConfiguration.separator(Component.space()),
                        Component.text(glyph).decoration(TextDecoration.ITALIC, false),
                        displayName,
                        Component.text(":"),
                        msg));
    }
}
