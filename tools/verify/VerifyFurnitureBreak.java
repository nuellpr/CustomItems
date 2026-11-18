import java.nio.file.Files;
import java.nio.file.Path;

/** A furniture break must drop its item even if the hitbox entity is gone on the next tick. */
public final class VerifyFurnitureBreak {
    static void check(boolean ok, String msg) {
        if (!ok) throw new AssertionError(msg);
    }

    public static void main(String[] args) throws Exception {
        String src = Files.readString(Path.of(args[0], "src/main/java/com/chatbiasa/customitems/FurnitureListener.java"));
        check(!src.contains("if (!attacked.isValid()) return;"), "break must not depend on the attacked entity still being valid");
        check(!src.contains("onQuit"), "onQuit must not release a pending break before its one-tick task runs");
        check(src.contains("pendingBreaks"), "pending breaks must be tracked per pair, not per entity");
        System.out.println("OK");
    }
}
