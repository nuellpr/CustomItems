import java.nio.file.Files;
import java.nio.file.Path;

/** Fixes raised by the final whole-branch review. */
public final class VerifyReviewFixes {
    static void check(boolean ok, String msg) {
        if (!ok) throw new AssertionError(msg);
    }

    public static void main(String[] args) throws Exception {
        String tools = Files.readString(Path.of("tools/CustomItemsTools.java"));
        check(tools.contains("Files.getLastModifiedTime"), "setup-server must pick the newest plugin jar, not the highest version");

        String blocks = Files.readString(Path.of("src/main/java/com/chatbiasa/customitems/Blocks.java"));
        check(blocks.contains("placed.add(line.trim())"), "an unrecognised legacy marker must be kept, not dropped");
        System.out.println("OK");
    }
}
