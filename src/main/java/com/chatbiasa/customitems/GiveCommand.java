package com.chatbiasa.customitems;

import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class GiveCommand implements CommandExecutor {

    private final CustomItemsPlugin plugin;

    public GiveCommand(CustomItemsPlugin plugin) {
        this.plugin = plugin;
    }

    /** Push the freshly built pack to everyone already online. Without this, a reload only reaches
     *  players who happen to rejoin, so their textures stay stale until then. */
    private int resendPack() {
        int n = 0;
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            JoinListener.send(plugin, p);
            n++;
        }
        return n;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("ci.admin")) {
            sender.sendMessage(Component.text("No permission."));
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            plugin.reloadConfig();
            plugin.loadItems();
            try {
                plugin.pack().build();
                int n = resendPack();
                sender.sendMessage(Component.text("Reloaded items and rebuilt pack; re-sent to "
                        + n + " online player(s)."));
            } catch (Exception e) {
                sender.sendMessage(Component.text("Reload failed: " + e.getMessage()));
            }
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("pack")) {
            int n = resendPack();
            sender.sendMessage(Component.text("Re-sent resource pack to " + n + " player(s)."));
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("menu")) {
            if (sender instanceof Player p) plugin.gui().open(p);
            else sender.sendMessage(Component.text("Players only."));
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("spawn")) {
            if (args.length < 2) {
                sender.sendMessage(Component.text("Usage: /" + label + " spawn <mob>"));
                return true;
            }
            Mobs.MobDef mdef = plugin.mobs().get(args[1]);
            if (mdef == null) {
                sender.sendMessage(Component.text("Unknown mob: " + args[1]));
                return true;
            }
            if (!(sender instanceof Player p)) {
                sender.sendMessage(Component.text("Players only."));
                return true;
            }
            plugin.mobs().spawn(mdef, p.getLocation());
            sender.sendMessage(Component.text("Spawned " + mdef.key()));
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("play")) {
            if (args.length < 2) {
                sender.sendMessage(Component.text("Usage: /" + label + " play <sound>"));
                return true;
            }
            Sounds.SoundDef sdef = plugin.sounds().get(args[1]);
            if (sdef == null) {
                sender.sendMessage(Component.text("Unknown sound: " + args[1]));
                return true;
            }
            if (!(sender instanceof Player p)) {
                sender.sendMessage(Component.text("Players only."));
                return true;
            }
            plugin.sounds().play(p, sdef);
            sender.sendMessage(Component.text("Playing " + sdef.key()));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /" + label + " give <item> [player] | /" + label + " spawn <mob> | /" + label + " play <sound> | /" + label + " menu | /" + label + " reload | /" + label + " pack"));
            return true;
        }
        ItemDef def = plugin.items().get(args[1]);
        Blocks.BlockDef bdef = def == null ? plugin.blocks().get(args[1].toLowerCase()) : null;
        if (def == null && bdef == null) {
            sender.sendMessage(Component.text("Unknown item: " + args[1]));
            return true;
        }
        Player target;
        if (args.length >= 3) {
            target = plugin.getServer().getPlayerExact(args[2]);
            if (target == null) {
                sender.sendMessage(Component.text("Player not online: " + args[2]));
                return true;
            }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            sender.sendMessage(Component.text("Specify a player: /" + label + " give <item> <player>"));
            return true;
        }
        ItemStack stack = def != null ? plugin.items().stack(def) : plugin.blocks().stack(bdef);
        target.getInventory().addItem(stack);
        sender.sendMessage(Component.text("Gave " + (def != null ? def.key() : bdef.key()) + " to " + target.getName()));
        return true;
    }
}
