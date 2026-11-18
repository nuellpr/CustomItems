import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;

/** Chat runs on an async thread, so rank resolution must not touch the Bukkit permission API there. */
public final class VerifyRankCache {
    static void check(boolean ok, String msg) {
        if (!ok) throw new AssertionError(msg);
    }

    public static void main(String[] args) throws Exception {
        Class<?> ranks = Class.forName("com.chatbiasa.customitems.Ranks");
        Class<?> player = Class.forName("org.bukkit.entity.Player", false, ranks.getClassLoader());
        boolean refresh = false;
        boolean cached = false;
        for (var m : ranks.getDeclaredMethods()) {
            if (m.getName().equals("refresh") && Arrays.equals(m.getParameterTypes(), new Class<?>[]{player})) refresh = true;
            if (m.getName().equals("cached") && Arrays.equals(m.getParameterTypes(), new Class<?>[]{UUID.class})) cached = true;
        }
        check(refresh, "Ranks.refresh(Player) missing");
        check(cached, "Ranks.cached(UUID) missing");

        String src = Files.readString(Path.of("src/main/java/com/chatbiasa/customitems/ChatListener.java"));
        check(!src.contains("forPlayer(event.getPlayer())"), "AsyncChatEvent still resolves rank via hasPermission");
        check(!src.contains("hasPermission("), "ChatListener still calls the permission API");
        System.out.println("OK");
    }
}
