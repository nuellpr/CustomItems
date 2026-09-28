package com.chatbiasa.customitems;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.TextReplacementConfig;
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
        // replaceText edits the text nodes in place, so the player's own bold/italic/colour and any
        // click+hover components survive. A plain-text roundtrip here used to flatten all of them.
        Component msg = event.message();
        // cheap pre-check on the flattened text so we only run replaceText for emojis actually typed;
        // the replacement itself runs on the real tree so formatting survives
        String plain = PlainTextComponentSerializer.plainText().serialize(msg);
        boolean replaced = false;
        for (Emojis.EmojiDef e : plugin.emojis().all()) {
            String token = ":" + e.key() + ":";
            if (!plain.contains(token)) continue;
            msg = msg.replaceText(TextReplacementConfig.builder()
                    .matchLiteral(token)
                    .replacement(plugin.emojis().glyph(e))
                    .build());
            replaced = true;
        }
        if (rank == null && !replaced) return;
        // italic off at the root so glyph chars don't render slanted; children that set it keep it
        final Component body = msg.decoration(TextDecoration.ITALIC, TextDecoration.State.FALSE);
        // renderer runs per-viewer, so viewers without the pack still get readable text
        event.renderer((source, displayName, m, viewer) -> {
            if (rank == null) return body;
            return Component.join(JoinConfiguration.separator(Component.space()),
                    Component.text(plugin.ranks().glyph(rank)).decoration(TextDecoration.ITALIC, false),
                    displayName,
                    Component.text(":"),
                    body);
        });
    }
}
