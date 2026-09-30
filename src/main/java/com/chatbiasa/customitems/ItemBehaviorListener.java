package com.chatbiasa.customitems;

import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class ItemBehaviorListener implements Listener {

    private final CustomItemsPlugin plugin;
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    private BukkitTask heldEffectTask;

    public ItemBehaviorListener(CustomItemsPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (heldEffectTask == null) {
            heldEffectTask = plugin.getServer().getScheduler()
                    .runTaskTimer(plugin, () -> applyHeldEffects(), 1L, 20L);
        }
    }

    public void shutdown() {
        if (heldEffectTask != null) {
            heldEffectTask.cancel();
            heldEffectTask = null;
        }
        cooldowns.clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) return;

        ItemDef def = itemInMainHand(event.getPlayer());
        if (def == null) return;
        Player player = event.getPlayer();
        def.useEffects().forEach(effect -> apply(effect, player));
        runAbilities(player, player, def, "on-right-click", def.onRightClick());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player) || !(event.getEntity() instanceof LivingEntity target)) return;
        ItemDef def = itemInMainHand(player);
        if (def != null) runAbilities(player, target, def, "on-hit", def.onHit());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSlotChange(PlayerItemHeldEvent event) {
        plugin.getServer().getScheduler().runTask(plugin, () -> applyHeldEffects(event.getPlayer()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cooldowns.remove(event.getPlayer().getUniqueId());
    }

    private void applyHeldEffects() {
        for (Player player : plugin.getServer().getOnlinePlayers()) applyHeldEffects(player);
    }

    private void applyHeldEffects(Player player) {
        ItemDef def = itemInMainHand(player);
        if (def != null) def.heldEffects().forEach(effect -> apply(effect, player));
    }

    private ItemDef itemInMainHand(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        String key = plugin.items().id(held);
        return key == null ? null : plugin.items().get(key);
    }

    private void runAbilities(Player player, LivingEntity target, ItemDef def,
                              String trigger, List<ItemDef.Ability> abilities) {
        if (abilities.isEmpty()) return;
        Map<String, Long> playerCooldowns = cooldowns.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>());
        long now = System.nanoTime();
        for (int i = 0; i < abilities.size(); i++) {
            ItemDef.Ability ability = abilities.get(i);
            String cooldownKey = def.key() + ":" + trigger + ":" + i;
            Long availableAt = playerCooldowns.get(cooldownKey);
            if (availableAt != null && now - availableAt < 0) continue;
            if (ThreadLocalRandom.current().nextDouble() >= ability.chance()) continue;

            switch (ability.type()) {
                case POTION -> target.addPotionEffect(new PotionEffect(
                        ability.effect(), ability.duration(), ability.amplifier()), false);
                case HEAL -> {
                    var maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
                    double maximum = maxHealth == null ? player.getHealth() : maxHealth.getValue();
                    player.setHealth(Math.min(maximum, player.getHealth() + ability.amount()));
                }
            }
            if (ability.cooldownSeconds() > 0) {
                playerCooldowns.put(cooldownKey, now + ability.cooldownSeconds() * 1_000_000_000L);
            }
        }
    }

    private static void apply(ItemDef.PotionEffectDef effect, LivingEntity target) {
        target.addPotionEffect(new PotionEffect(effect.type(), effect.duration(), effect.amplifier()), false);
    }
}
