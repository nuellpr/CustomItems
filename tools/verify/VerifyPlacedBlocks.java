import java.lang.reflect.Method;
import java.util.UUID;

/** Placed-block markers must survive a world rename: store the world UUID, still read the old name format. */
public final class VerifyPlacedBlocks {
    static void check(boolean ok, String msg) {
        if (!ok) throw new AssertionError(msg);
    }

    public static void main(String[] args) throws Exception {
        Class<?> blocks = Class.forName("com.chatbiasa.customitems.Blocks");
        Method migrate = blocks.getDeclaredMethod("migratedPlacedLine", String.class, String.class, UUID.class);
        migrate.setAccessible(true);
        UUID id = UUID.fromString("11111111-2222-3333-4444-555555555555");

        String current = (String) migrate.invoke(null, id + ",10,20,30", "world", id);
        check((id + ",10,20,30").equals(current), "a UUID marker must be kept unchanged");

        String legacy = (String) migrate.invoke(null, "world,10,20,30", "world", id);
        check((id + ",10,20,30").equals(legacy), "old name marker must be converted to the world UUID");

        String other = (String) migrate.invoke(null, "nether,10,20,30", "world", id);
        check(other == null, "a marker from a different world must not be converted here");

        String bom = (String) migrate.invoke(null, "\uFEFFworld,10,20,30", "world", id);
        check((id + ",10,20,30").equals(bom), "a UTF-8 BOM before an old marker must be ignored");
        System.out.println("OK");
    }
}
