package com.chatbiasa.customitems;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public final class GuiMenu implements InventoryHolder, Listener {

    /** largest chest inventory the server accepts */
    private static final int MAX_SIZE = 54;

    private final CustomItemsPlugin plugin;
    private Inventory inv;

    public GuiMenu(CustomItemsPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        int count = plugin.items().all().size() + plugin.blocks().all().size();
        // createInventory throws above 54 slots; clamp so /ci menu can never hard-error.
        int size = Math.min(MAX_SIZE, Math.max(9, ((count - 1) / 9 + 1) * 9));
        if (count > MAX_SIZE) {
            plugin.getLogger().warning("/ci menu shows only the first " + MAX_SIZE + " of " + count
                    + " entries; raise the GUI limit by adding paging.");
        }
        inv = plugin.getServer().createInventory(this, size, Component.text("CustomItems"));
        for (ItemDef def : plugin.items().all()) {
            if (inv.firstEmpty() < 0) break;
            inv.addItem(plugin.items().stack(def));
        }
        for (Blocks.BlockDef def : plugin.blocks().all()) {
            if (inv.firstEmpty() < 0) break;
            inv.addItem(plugin.blocks().stack(def));
        }
        player.openInventory(inv);
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof GuiMenu)) return;
        e.setCancelled(true);
        ItemStack cur = e.getCurrentItem();
        if (cur == null || cur.getType().isAir() || !(e.getWhoClicked() instanceof Player p)) return;
        p.getInventory().addItem(cur.clone());
    }
}
