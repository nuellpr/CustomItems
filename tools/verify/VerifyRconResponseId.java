import java.nio.file.Files;
import java.nio.file.Path;

public class VerifyRconResponseId {
    public static void main(String[] args) throws Exception {
        String source = Files.readString(Path.of("tools/CustomItemsTools.java"));

        check(source.contains("Packet response = readPacket(in);"),
                "RCON response must be read into a Packet");
        check(source.contains("response.id != commandId"),
                "RCON response id must match the command id");
        check(!source.contains("readPacket(in).body"),
                "RCON response body must not be read before checking the id");
        System.out.println("OK");
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
