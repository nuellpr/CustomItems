import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Asserts that blockstate instrument names round-trip through Bukkit's Instrument enum. */
public class VerifyInstrumentRoundTrip {

    public static void main(String[] args) throws Exception {
        Class<?> blocks = Class.forName("com.chatbiasa.customitems.Blocks");
        Field all = blocks.getDeclaredField("ALL_INSTRUMENTS");
        all.setAccessible(true);
        String[][] allInstruments = (String[][]) all.get(null);
        Field assign = blocks.getDeclaredField("INSTRUMENTS");
        assign.setAccessible(true);
        String[][] assignable = (String[][]) assign.get(null);

        Set<String> enumNames = new HashSet<>(Arrays.asList(
                "PIANO", "BASS_DRUM", "SNARE_DRUM", "STICKS", "BASS_GUITAR", "FLUTE", "BELL",
                "GUITAR", "CHIME", "XYLOPHONE", "IRON_XYLOPHONE", "COW_BELL", "DIDGERIDOO",
                "BIT", "BANJO", "PLING", "ZOMBIE", "SKELETON", "CREEPER", "DRAGON",
                "WITHER_SKELETON", "PIGLIN", "CUSTOM_HEAD"));
        Map<String, String> enumToState = new HashMap<>();
        for (String[] pair : allInstruments) enumToState.put(pair[1], pair[0]);
        Set<String> stateNames = new HashSet<>(enumToState.values());

        for (String[] pair : assignable) {
            String enumName = pair[0].toUpperCase(Locale.ROOT);
            check(enumNames.contains(enumName), "not a Bukkit Instrument: " + enumName);
            check(pair[0].equals(enumToState.get(enumName)), "round trip failed for " + pair[0]);
            check(stateNames.contains(pair[0]), "blockstate missing for " + pair[0]);
        }
        System.out.println("VerifyInstrumentRoundTrip: OK (" + assignable.length + " assignable)");
    }

    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
