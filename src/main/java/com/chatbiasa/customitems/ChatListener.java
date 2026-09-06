package com.chatbiasa.customitems;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class ChatListener implements Listener {

    private final CustomItemsPlugin plugin;

    public ChatListener(CustomItemsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        Ranks.RankDef rank = plugin.ranks().forPlayer(event.getPlayer());
        // replace :emoji: tokens with glyphs; plain-text roundtrip is fine for typed chat
        String plain = PlainTextComponentSerializer.plainText().serialize(event.message());
        StringBuilder sb = new StringBuilder(plain);
        boolean replaced = false;
        for (Emojis.EmojiDef e : plugin.emojis().all()) {
            String token = ":" + e.key() + ":";
            int idx;
            while ((idx = sb.indexOf(token)) >= 0) {
                sb.replace(idx, idx + token.length(), plugin.emojis().glyph(e));
                replaced = true;
            }
        }
        if (rank == null && !replaced) return;
        Component msg = Component.text(sb.toString()).decoration(TextDecoration.ITALIC, false);
        // renderer runs per-viewer; italic-off so glyph chars don't render slanted in chat
        event.renderer((source, displayName, m, viewer) -> {
            if (rank == null) return msg;
            return Component.join(JoinConfiguration.separator(Component.space()),
                    Component.text(plugin.ranks().glyph(rank)).decoration(TextDecoration.ITALIC, false),
                    displayName,
                    Component.text(":"),
                    msg);
        });
    }
}
