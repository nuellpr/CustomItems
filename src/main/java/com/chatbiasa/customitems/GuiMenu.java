package com.chatbiasa.customitems;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class GuiMenu implements Listener {

    /** rows of entries; the last row holds the page arrows */
    private static final int PAGE_ROWS = 5;
    private static final int PER_PAGE = PAGE_ROWS * 9;
    private static final int PREV_SLOT = PER_PAGE;
    private static final int NEXT_SLOT = PER_PAGE + 8;

    private final CustomItemsPlugin plugin;

    public GuiMenu(CustomItemsPlugin plugin) {
        this.plugin = plugin;
    }

    /** One open window. Per-player rather than one shared instance, so two players looking at
     *  different pages do not fight over a single Inventory field. */
    private static final class Menu implements InventoryHolder {
        private final Player viewer;
        private final List<ItemStack> entries;
        private final int page;
        private Inventory inv;

        Menu(Player viewer, List<ItemStack> entries, int page) {
            this.viewer = viewer;
            this.entries = entries;
            this.page = page;
        }

        int page() {
            return page;
        }

        Player viewer() {
            return viewer;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private static List<ItemStack> entries(CustomItemsPlugin plugin) {
        List<ItemStack> list = new ArrayList<>();
        for (ItemDef def : plugin.items().all()) list.add(plugin.items().stack(def));
        for (Blocks.BlockDef def : plugin.blocks().all()) list.add(plugin.blocks().stack(def));
        return list;
    }

    public void open(Player player) {
        show(player, 0);
    }

    private void show(Player player, int page) {
        List<ItemStack> entries = entries(plugin);
        int pages = Math.max(1, (entries.size() + PER_PAGE - 1) / PER_PAGE);
        int p = Math.clamp(page, 0, pages - 1);
        Menu menu = new Menu(player, entries, p);
        int size = entries.size() > PER_PAGE ? (PAGE_ROWS + 1) * 9 : Math.max(9, ((entries.size() - 1) / 9 + 1) * 9);
        menu.inv = plugin.getServer().createInventory(menu, size,
                Component.text("CustomItems " + (p + 1) + "/" + pages));
        for (int i = p * PER_PAGE; i < Math.min(entries.size(), (p + 1) * PER_PAGE); i++) {
            menu.inv.setItem(i - p * PER_PAGE, entries.get(i));
        }
        if (p > 0) menu.inv.setItem(PREV_SLOT, nav(Material.ARROW, "Previous page"));
        if (p < pages - 1) menu.inv.setItem(NEXT_SLOT, nav(Material.ARROW, "Next page"));
        player.openInventory(menu.inv);
    }

    private static ItemStack nav(Material mat, String name) {
        ItemStack s = ItemStack.of(mat);
        s.setData(io.papermc.paper.datacomponent.DataComponentTypes.CUSTOM_NAME,
                Component.text(name).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        return s;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Menu menu)) return;
        // Only the menu itself is read-only. Clicks in the player's own inventory must stay live,
        // otherwise shift-clicking to rearrange a hotbar is silently dead while the menu is open.
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        e.setCancelled(true);
        if (e.getSlot() == PREV_SLOT) {
            show(menu.viewer(), menu.page() - 1);
            return;
        }
        if (e.getSlot() == NEXT_SLOT) {
            show(menu.viewer(), menu.page() + 1);
            return;
        }
        ItemStack cur = e.getCurrentItem();
        if (cur == null || cur.getType().isAir() || !(e.getWhoClicked() instanceof Player p)) return;
        p.getInventory().addItem(cur.clone());
    }
}
