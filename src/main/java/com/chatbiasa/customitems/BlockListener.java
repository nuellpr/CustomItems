package com.chatbiasa.customitems;

import org.bukkit.Instrument;
import org.bukkit.Note;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.NoteBlock;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

public final class BlockListener implements Listener {

    private final CustomItemsPlugin plugin;

    public BlockListener(CustomItemsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        BlockData data = e.getBlockPlaced().getBlockData();
        boolean custom = false;
        String key = plugin.blocks().id(e.getItemInHand());
        if (key != null) {
            Blocks.BlockDef def = plugin.blocks().get(key);
            if (def != null && data instanceof NoteBlock nb) {
                // A noteblock has no block entity, so it has nowhere to store a PDC: its instrument+note
                // blockstate IS the identity. Stamp the state and let Blocks.byState() read it back.
                // The location is remembered too: a vanilla noteblock can be right-clicked into the
                // same instrument+note, so blockstate alone would let a player mint custom blocks.
                nb.setInstrument(Instrument.valueOf(def.instrument().toUpperCase(Locale.ROOT)));
                nb.setNote(new Note(def.note()));
                nb.setPowered(false);
                e.getBlockPlaced().setBlockData(nb);
                plugin.blocks().markPlaced(e.getBlock().getLocation());
                custom = true;
            }
        }
        // a vanilla noteblock dropped at a spot we track would inherit the marker
        if (!custom && data instanceof NoteBlock) plugin.blocks().forget(e.getBlock().getLocation());
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        BlockData data = e.getBlock().getBlockData();
        if (!(data instanceof NoteBlock nb)) return;
        // not ours unless we remember placing it (see onPlace) -- blocks moved/destroyed by anything
        // else fall through to vanilla noteblock drops
        if (!plugin.blocks().isPlaced(e.getBlock().getLocation())) return;
        plugin.blocks().forget(e.getBlock().getLocation());
        Blocks.BlockDef def = plugin.blocks().byNoteBlock(nb);
        if (def == null) return;
        e.setDropItems(false);
        e.getBlock().getWorld().dropItemNaturally(e.getBlock().getLocation().add(0.5, 0.5, 0.5), plugin.blocks().stack(def));
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() == null) return;
        Block b = e.getClickedBlock();
        if (b == null || !(b.getBlockData() instanceof NoteBlock nb)) return;
        if (plugin.blocks().byNoteBlock(nb) != null) e.setCancelled(true); // no note sound / no chest open behind
    }
}
