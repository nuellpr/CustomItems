import java.nio.file.Files;
import java.nio.file.Path;

/** Fixes raised by the final whole-branch review. */
public final class VerifyReviewFixes {
    static void check(boolean ok, String msg) {
        if (!ok) throw new AssertionError(msg);
    }

    public static void main(String[] args) throws Exception {
        String tools = Files.readString(Path.of(args[0], "tools/CustomItemsTools.java"));
        check(tools.contains("Files.getLastModifiedTime"), "setup-server must pick the newest plugin jar, not the highest version");

        check(tools.contains("toLowerCase(Locale.ROOT).lastIndexOf(\"items/\")"), "Oraxen import must match folder case-insensitively");
        check(tools.contains("object(Json.parse(text)).containsKey(\"variants\")"), "blockstate check must parse JSON, not match a literal regex");
        check(tools.contains("--seed-from is not a directory"), "a missing --seed-from directory must error explicitly");
        check(!tools.contains("args[i].startsWith(\"--\")"), "option values starting with -- must be accepted");

        String blocks = Files.readString(Path.of(args[0], "src/main/java/com/chatbiasa/customitems/Blocks.java"));
        check(blocks.contains("placed.add(line.trim())"), "an unrecognised legacy marker must be kept, not dropped");
        check(blocks.contains("markerStillValid"), "stale placed-block markers must be dropped on load");

        String base = args[0] + "/src/main/java/com/chatbiasa/customitems/";
        String packBuilder = Files.readString(Path.of(base + "PackBuilder.java"));
        check(packBuilder.contains("namespace.equals(\"minecraft\")"), "imported minecraft/font assets must not duplicate generated glyphs");
        check(packBuilder.contains("Math.max(1, Math.min(255"), "pack-format must be clamped");
        String packServer = Files.readString(Path.of(base + "PackServer.java"));
        check(packServer.contains("hasPort"), "external-url with a port must not get a second port appended");
        check(packServer.contains("newFixedThreadPool(8"), "pack webserver pool must not stall under load");
        String recipes = Files.readString(Path.of(base + "Recipes.java"));
        check(recipes.contains("ok = Bukkit.addRecipe(recipe)"), "recipes must only be tracked when addRecipe succeeds");
        String items = Files.readString(Path.of(base + "Items.java"));
        check(items.contains("base.isAir()"), "air base materials must be rejected");
        String blockListener = Files.readString(Path.of(base + "BlockListener.java"));
        check(!blockListener.contains("block.getType() == org.bukkit.Material.NOTE_BLOCK"), "break drop must not depend on a live block pointer");
        String plugin = Files.readString(Path.of(base + "CustomItemsPlugin.java"));
        check(plugin.contains("Players would join without a resource pack"), "webserver failure must fail fast");
        check(plugin.contains("ranks.refresh(online)"), "rank cache must refresh periodically");
        String gui = Files.readString(Path.of(base + "GuiMenu.java"));
        check(gui.contains("dropItemNaturally"), "inventory overflow must drop leftover items");
        System.out.println("OK");
    }
}
