package com.chatbiasa.customitems;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class Recipes {

    private final CustomItemsPlugin plugin;
    private final List<NamespacedKey> registered = new ArrayList<>();

    public Recipes(CustomItemsPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        for (NamespacedKey key : registered) Bukkit.removeRecipe(key);
        registered.clear();
        File f = new File(plugin.getDataFolder(), "recipes.yml");
        if (!f.exists()) plugin.saveResource("recipes.yml", false);
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
        ConfigurationSection root = yml.getConfigurationSection("recipes");
        if (root == null) {
            plugin.getLogger().info("Loaded 0 recipes");
            return;
        }
        int count = 0;
        Set<String> seen = new HashSet<>();
        for (String name : root.getKeys(false)) {
            ConfigurationSection r = root.getConfigurationSection(name);
            if (r == null) continue;
            try {
                String normalizedName = name.toLowerCase(Locale.ROOT);
                if (!normalizedName.matches("[a-z0-9_-]+") || !seen.add(normalizedName)) {
                    plugin.getLogger().warning("recipes.yml: invalid or duplicate recipe key '" + name + "'");
                    continue;
                }
                ItemStack result = resultStack(r.getString("result", ""));
                if (result == null) {
                    plugin.getLogger().warning("recipes.yml: unknown result for '" + name + "'");
                    continue;
                }
                NamespacedKey key = new NamespacedKey(plugin, normalizedName);
                String type = r.getString("type", "shaped");
                boolean ok;
                if ("shapeless".equalsIgnoreCase(type)) {
                    ShapelessRecipe recipe = new ShapelessRecipe(key, result);
                    ok = true;
                    for (Object ing : r.getList("ingredients", List.of())) {
                        RecipeChoice choice = choice(ing);
                        if (choice == null) { ok = false; break; }
                        recipe.addIngredient(choice);
                    }
                    if (ok) Bukkit.addRecipe(recipe);
                } else if ("shaped".equalsIgnoreCase(type)) {
                    List<String> pattern = r.getStringList("pattern");
                    ConfigurationSection ingSec = r.getConfigurationSection("ingredients");
                    if (pattern.isEmpty() || ingSec == null) {
                        plugin.getLogger().warning("recipes.yml: '" + name + "' missing pattern/ingredients");
                        continue;
                    }
                    ShapedRecipe recipe = new ShapedRecipe(key, result);
                    recipe.shape(pattern.toArray(new String[0]));
                    ok = true;
                    for (String c : ingSec.getKeys(false)) {
                        if (c.length() != 1) { ok = false; break; }
                        RecipeChoice choice = choice(ingSec.get(c));
                        if (choice == null) { ok = false; break; }
                        recipe.setIngredient(c.charAt(0), choice);
                    }
                    if (ok) Bukkit.addRecipe(recipe);
                } else {
                    plugin.getLogger().warning("recipes.yml: unknown recipe type '" + type + "' for '" + name + "'");
                    continue;
                }
                if (ok) {
                    registered.add(key);
                    count++;
                } else {
                    plugin.getLogger().warning("recipes.yml: bad ingredient in '" + name + "'");
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to register recipe '" + name + "': " + e.getMessage());
            }
        }
        plugin.getLogger().info("Loaded " + count + " recipes");
    }

    private ItemStack resultStack(String key) {
        if (key == null) return null;
        ItemDef idef = plugin.items().get(key);
        if (idef != null) return plugin.items().stack(idef);
        Blocks.BlockDef bdef = plugin.blocks().get(key);
        if (bdef != null) return plugin.blocks().stack(bdef);
        return null;
    }

    /** vanilla material name, custom item key, or custom block key → recipe choice */
    private RecipeChoice choice(Object value) {
        if (value == null) return null;
        String s = String.valueOf(value);
        ItemDef idef = plugin.items().get(s);
        if (idef != null) return RecipeChoice.exactChoice(plugin.items().stack(idef));
        Blocks.BlockDef bdef = plugin.blocks().get(s);
        if (bdef != null) return RecipeChoice.exactChoice(plugin.blocks().stack(bdef));
        Material mat = Material.matchMaterial(s);
        if (mat != null) return new RecipeChoice.MaterialChoice(mat);
        return null;
    }
}
