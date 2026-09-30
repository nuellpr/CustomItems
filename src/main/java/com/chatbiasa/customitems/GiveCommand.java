package com.chatbiasa.customitems;

import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class GiveCommand implements TabExecutor {

    private static final String USAGE = "give <item> [player] | spawn <mob> | play <sound> | menu | reload | pack | list";
    private final CustomItemsPlugin plugin;

    public GiveCommand(CustomItemsPlugin plugin) {
        this.plugin = plugin;
    }

    /** every key an admin can type, so /ci list is a single source of truth for the command surface */
    private void list(CommandSender sender) {
        send(sender, "Items", plugin.items().keys());
        send(sender, "Blocks", plugin.blocks().all().stream().map(Blocks.BlockDef::key).toList());
        send(sender, "Mobs", plugin.mobs().all().stream().map(Mobs.MobDef::key).toList());
        send(sender, "Sounds", plugin.sounds().all().stream().map(Sounds.SoundDef::key).toList());
        send(sender, "Ranks", plugin.ranks().all().stream().map(Ranks.RankDef::key).toList());
        send(sender, "Emojis", plugin.emojis().all().stream().map(Emojis.EmojiDef::key).toList());
    }

    private void send(CommandSender sender, String label, List<String> keys) {
        sender.sendMessage(Component.text("[" + label + "] " + (keys.isEmpty() ? "-" : String.join(", ", keys))));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!sender.hasPermission("ci.admin")) return List.of();
        if (args.length == 1) return filter(List.of("give", "spawn", "play", "menu", "reload", "pack", "list"), args[0]);
        if (args.length != 2) {
            if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
                return filter(plugin.getServer().getOnlinePlayers().stream().map(Player::getName).toList(), args[2]);
            }
            return List.of();
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "give" -> {
                var keys = new ArrayList<String>(plugin.items().keys());
                plugin.blocks().all().forEach(d -> keys.add(d.key()));
                yield filter(keys, args[1]);
            }
            case "spawn" -> filter(plugin.mobs().all().stream().map(Mobs.MobDef::key).toList(), args[1]);
            case "play" -> filter(plugin.sounds().all().stream().map(Sounds.SoundDef::key).toList(), args[1]);
            default -> List.of();
        };
    }

    /** Bukkit does not filter for us, and a list of 200 mob keys is a wall of noise in the chat box */
    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(p)).toList();
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
        if (args.length == 1 && (args[0].equalsIgnoreCase("list") || args[0].equalsIgnoreCase("?"))) {
            list(sender);
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            plugin.reloadConfig();
            plugin.loadItems();
            JoinListener.migrateOnline(plugin);
            try {
                plugin.pack().build();
                plugin.refreshPackServer();
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
        if (args.length == 0) {
            sender.sendMessage(Component.text("Usage: /" + label + " " + USAGE));
            return true;
        }
        if (!args[0].equalsIgnoreCase("give")) {
            sender.sendMessage(Component.text("Unknown subcommand: " + args[0]));
            sender.sendMessage(Component.text("Usage: /" + label + " " + USAGE));
            return true;
        }
        if (args.length < 2 || args.length > 3) {
            sender.sendMessage(Component.text("Usage: /" + label + " " + USAGE));
            return true;
        }
        ItemDef def = plugin.items().get(args[1]);
        Blocks.BlockDef bdef = def == null ? plugin.blocks().get(args[1]) : null;
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
        var leftovers = target.getInventory().addItem(stack);
        leftovers.values().forEach(item -> target.getWorld().dropItemNaturally(target.getLocation(), item));
        sender.sendMessage(Component.text("Gave " + (def != null ? def.key() : bdef.key()) + " to " + target.getName()
                + (leftovers.isEmpty() ? "" : " (overflow dropped nearby)")));
        return true;
    }
}
