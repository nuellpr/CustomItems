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
import java.util.List;

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
        for (String name : root.getKeys(false)) {
            ConfigurationSection r = root.getConfigurationSection(name);
            if (r == null) continue;
            try {
                ItemStack result = resultStack(r.getString("result", ""));
                if (result == null) {
                    plugin.getLogger().warning("recipes.yml: unknown result for '" + name + "'");
                    continue;
                }
                NamespacedKey key = new NamespacedKey(plugin, name.toLowerCase());
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
                } else {
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
                        RecipeChoice choice = choice(ingSec.get(c));
                        if (choice == null) { ok = false; break; }
                        recipe.setIngredient(c.charAt(0), choice);
                    }
                    if (ok) Bukkit.addRecipe(recipe);
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
        Blocks.BlockDef bdef = plugin.blocks().get(key.toLowerCase());
        if (bdef != null) return plugin.blocks().stack(bdef);
        return null;
    }

    /** vanilla material name, custom item key, or custom block key → recipe choice */
    private RecipeChoice choice(Object value) {
        if (value == null) return null;
        String s = String.valueOf(value);
        Material mat = Material.matchMaterial(s);
        if (mat != null) return new RecipeChoice.MaterialChoice(mat);
        ItemDef idef = plugin.items().get(s.toLowerCase());
        if (idef != null) return new RecipeChoice.ExactChoice(plugin.items().stack(idef));
        Blocks.BlockDef bdef = plugin.blocks().get(s.toLowerCase());
        if (bdef != null) return new RecipeChoice.ExactChoice(plugin.blocks().stack(bdef));
        return null;
    }
}
