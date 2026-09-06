package com.chatbiasa.customitems;

import org.bukkit.Note;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.NoteBlock;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public final class BlockListener implements Listener {

    private final CustomItemsPlugin plugin;

    public BlockListener(CustomItemsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        String key = plugin.blocks().id(e.getItemInHand());
        if (key == null) return;
        Blocks.BlockDef def = plugin.blocks().get(key);
        if (def == null) return;
        NoteBlock nb = (NoteBlock) e.getBlockPlaced().getBlockData();
        nb.setInstrument(org.bukkit.Instrument.valueOf(def.instrument().toUpperCase()));
        nb.setNote(new Note(def.note()));
        nb.setPowered(false);
        e.getBlockPlaced().setBlockData(nb);
        // store which custom block this is on the tile state
        org.bukkit.block.BlockState st = e.getBlockPlaced().getState();
        if (st instanceof org.bukkit.block.TileState ts) {
            ts.getPersistentDataContainer().set(
                    new org.bukkit.NamespacedKey(plugin, "cblock"), org.bukkit.persistence.PersistentDataType.STRING, key);
            ts.update();
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        String key = plugin.blocks().idOf(e.getBlock().getState());
        if (key == null) return;
        Blocks.BlockDef def = plugin.blocks().get(key);
        if (def == null) return;
        e.setDropItems(false);
        e.getBlock().getWorld().dropItemNaturally(e.getBlock().getLocation().add(0.5, 0.5, 0.5), plugin.blocks().stack(def));
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() == null) return;
        Block b = e.getClickedBlock();
        if (b == null || !(b.getBlockData() instanceof NoteBlock)) return;
        if (plugin.blocks().idOf(b.getState()) != null) e.setCancelled(true); // no note sound / no chest open behind
    }
}
