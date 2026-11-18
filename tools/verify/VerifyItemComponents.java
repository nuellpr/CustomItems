import java.nio.file.Files;
import java.nio.file.Path;

public class VerifyItemComponents {
    public static void main(String[] args) throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/chatbiasa/customitems/Items.java"));

        int meta = source.indexOf("s.setItemMeta(meta);");
        int model = source.indexOf("s.setData(DataComponentTypes.ITEM_MODEL");
        int pdc = source.indexOf("s.editPersistentDataContainer(");

        check(meta >= 0 && model >= 0 && pdc >= 0,
                "could not find component writes in Items.stack");
        check(meta < model && meta < pdc,
                "setItemMeta must run before ITEM_MODEL and PDC are written");
        System.out.println("OK");
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
