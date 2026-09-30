package com.chatbiasa.customitems;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class FurnitureListener implements Listener {

    private final CustomItemsPlugin plugin;
    private final NamespacedKey itemKey;
    private final NamespacedKey pairKey;
    private final Set<UUID> pendingBreaks = new HashSet<>();

    public FurnitureListener(CustomItemsPlugin plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "furniture_item");
        this.pairKey = new NamespacedKey(plugin, "furniture_pair");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getBlockFace() != BlockFace.UP) return;

        ItemStack held = event.getItem();
        if (held == null || held.getType().isAir()) return;
        String key = plugin.items().id(held);
        ItemDef def = plugin.items().get(key);
        if (def == null || def.furniture() == null) return;

        Block support = event.getClickedBlock();
        if (support == null || !support.getType().isSolid()) return;
        Block target = support.getRelative(BlockFace.UP);
        if (!target.getType().isAir()) return;

        event.setCancelled(true);
        Location location = new Location(target.getWorld(), target.getX() + 0.5 + def.furniture().offsetX(),
                target.getY() + def.furniture().offsetY(), target.getZ() + 0.5 + def.furniture().offsetZ(),
                def.furniture().fixedRotation() ? 0 : event.getPlayer().getLocation().getYaw(), 0);
        UUID pair = UUID.randomUUID();
        ArmorStand visual = null;
        Interaction hitbox = null;
        try {
            ItemStack display = plugin.items().stack(def);
            display.setAmount(1);
            visual = location.getWorld().spawn(location, ArmorStand.class, stand -> {
                stand.setInvisible(true);
                stand.setMarker(true);
                stand.setGravity(false);
                stand.setInvulnerable(true);
                stand.setSilent(true);
                stand.setPersistent(true);
                stand.setBasePlate(false);
                stand.setArms(false);
                stand.addDisabledSlots(EquipmentSlot.HEAD);
                stand.getEquipment().setHelmet(display);
                tag(stand, key, pair);
            });
            hitbox = location.getWorld().spawn(location, Interaction.class, interaction -> {
                interaction.setInteractionWidth(def.furniture().hitboxWidth());
                interaction.setInteractionHeight(def.furniture().hitboxHeight());
                interaction.setResponsive(true);
                interaction.setPersistent(true);
                tag(interaction, key, pair);
            });
            if (!visual.isValid() || !hitbox.isValid()) {
                if (visual.isValid()) visual.remove();
                if (hitbox.isValid()) hitbox.remove();
                return;
            }
            if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
                ItemStack current = event.getPlayer().getInventory().getItemInMainHand();
                if (current != null && key.equals(plugin.items().id(current))) {
                    if (current.getAmount() <= 1) event.getPlayer().getInventory().setItemInMainHand(null);
                    else current.setAmount(current.getAmount() - 1);
                }
            }
        } catch (RuntimeException e) {
            if (visual != null && visual.isValid()) visual.remove();
            if (hitbox != null && hitbox.isValid()) hitbox.remove();
            plugin.getLogger().warning("Could not place furniture '" + key + "': " + e.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAttack(PrePlayerAttackEntityEvent event) {
        Entity attacked = event.getAttacked();
        if (!event.willAttack()) return;
        String pair = attacked.getPersistentDataContainer().get(pairKey, PersistentDataType.STRING);
        if (pair == null || !pendingBreaks.add(attacked.getUniqueId())) return;

        String item = attacked.getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
        Location dropLocation = attacked.getLocation().clone().add(0, 0.25, 0);
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            pendingBreaks.remove(attacked.getUniqueId());
            if (!attacked.isValid()) return;

            ItemStack drop = null;
            for (Entity entity : attacked.getWorld().getNearbyEntities(dropLocation, 4, 4, 4)) {
                if (!pair.equals(entity.getPersistentDataContainer().get(pairKey, PersistentDataType.STRING))) continue;
                if (entity instanceof ArmorStand stand && stand.getEquipment().getHelmet() != null) {
                    drop = stand.getEquipment().getHelmet().clone();
                    drop.setAmount(1);
                }
                entity.remove();
            }

            if (drop == null) {
                ItemDef def = plugin.items().get(item);
                if (def != null) drop = plugin.items().stack(def);
            }
            if (drop != null && player.getGameMode() != GameMode.CREATIVE) {
                dropLocation.getWorld().dropItemNaturally(dropLocation, drop);
            }
        });
    }

    private void tag(Entity entity, String item, UUID pair) {
        entity.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, item);
        entity.getPersistentDataContainer().set(pairKey, PersistentDataType.STRING, pair.toString());
    }
}
