package com.chatbiasa.customitems;

import org.bukkit.Instrument;
import org.bukkit.Note;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.NoteBlock;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.Locale;

public final class BlockListener implements Listener {

    private final CustomItemsPlugin plugin;

    public BlockListener(CustomItemsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Block block = e.getBlockPlaced();
        BlockData data = block.getBlockData();
        org.bukkit.Location location = block.getLocation();
        boolean trackedBefore = plugin.blocks().isPlaced(location);
        BlockData replacedData = e.getBlockReplacedState().getBlockData();
        String key = plugin.blocks().id(e.getItemInHand());
        Blocks.BlockDef def = key == null ? null : plugin.blocks().get(key);
        if (def != null && data instanceof NoteBlock noteBlock) {
            // A noteblock has no block entity, so its instrument+note state identifies it. The
            // location marker prevents vanilla noteblocks from producing free custom drops.
            noteBlock.setInstrument(Instrument.valueOf(def.instrument().toUpperCase(Locale.ROOT)));
            noteBlock.setNote(new Note(def.note()));
            noteBlock.setPowered(false);
            block.setBlockData(noteBlock);
            plugin.blocks().markPlaced(location);
        } else {
            def = null;
            if (trackedBefore) plugin.blocks().forget(location);
        }

        // Reconcile after other plugins finish; they may cancel or change this placement.
        Blocks.BlockDef placedDef = def;
        if (placedDef != null || trackedBefore) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (e.isCancelled()) {
                    if (trackedBefore && block.getBlockData().equals(replacedData)) plugin.blocks().markPlaced(location);
                    else if (placedDef != null) plugin.blocks().forget(location);
                    return;
                }
                if (placedDef != null && block.getBlockData() instanceof NoteBlock placed
                        && plugin.blocks().byNoteBlock(placed) == placedDef) {
                    plugin.blocks().markPlaced(location);
                } else {
                    plugin.blocks().forget(location);
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        BlockData data = e.getBlock().getBlockData();
        if (!(data instanceof NoteBlock nb)) return;
        // not ours unless we remember placing it (see onPlace) -- blocks moved/destroyed by anything
        // else fall through to vanilla noteblock drops
        if (!plugin.blocks().isPlaced(e.getBlock().getLocation())) return;
        Blocks.BlockDef def = plugin.blocks().byNoteBlock(nb);
        if (def != null) e.setDropItems(false);
        boolean dropCustom = def != null && e.getPlayer().getGameMode() != GameMode.CREATIVE;
        Block block = e.getBlock();
        org.bukkit.Location location = block.getLocation();
        // Resolve ownership after the event finishes. A protection plugin may cancel at a later
        // priority; dropping inside this handler would grant an item while leaving the block intact.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (e.isCancelled()) return;
            if (block.getType() == org.bukkit.Material.NOTE_BLOCK) return;
            plugin.blocks().forget(location);
            if (dropCustom) {
                location.add(0.5, 0.5, 0.5);
                block.getWorld().dropItemNaturally(location, plugin.blocks().stack(def));
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        if (e.getBlocks().stream().anyMatch(block -> plugin.blocks().isPlaced(block.getLocation()))) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        if (e.getBlocks().stream().anyMatch(block -> plugin.blocks().isPlaced(block.getLocation()))) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(block -> plugin.blocks().isPlaced(block.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(block -> plugin.blocks().isPlaced(block.getLocation()));
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() == null) return;
        Block b = e.getClickedBlock();
        if (b == null || !(b.getBlockData() instanceof NoteBlock nb)) return;
        if (plugin.blocks().byNoteBlock(nb) != null) e.setCancelled(true); // no note sound / no chest open behind
    }
}
