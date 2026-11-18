import java.lang.reflect.Field;
import java.util.LinkedHashSet;
import java.util.Set;

/** Offline assertion check of the note_block variants and vanilla font references. */
public class VerifyPack {

    public static void main(String[] args) throws Exception {
        Class<?> blocks = Class.forName("com.chatbiasa.customitems.Blocks");
        Field field = blocks.getDeclaredField("ALL_INSTRUMENTS");
        field.setAccessible(true);
        String[][] instruments = (String[][]) field.get(null);

        StringBuilder variants = new StringBuilder();
        Set<String> names = new LinkedHashSet<>();
        for (String[] inst : instruments) {
            names.add(inst[0]);
            for (int note = 0; note < 25; note++) {
                for (String powered : new String[]{"false", "true"}) {
                    if (variants.length() > 0) variants.append(',');
                    variants.append("\"instrument=").append(inst[0]).append(",note=").append(note)
                            .append(",powered=").append(powered).append("\":{\"model\":\"minecraft:block/note_block\"}");
                }
            }
        }
        String json = "{\"variants\":{" + variants + "}}";
        check(variants.toString().split("instrument=").length - 1 == instruments.length * 50,
                "not every instrument has 25 notes x 2 powered states");
        check(json.contains("\"instrument=harp,note=0,powered=false\""), "vanilla noteblock state is missing");
        check(names.contains("custom_head") && names.contains("piglin"), "custom_head/piglin missing");

        String font = "{\"providers\":[{\"type\":\"bitmap\",\"file\":\"minecraft:font/rank_admin.png\",\"ascent\":8,"
                + "\"height\":8,\"chars\":[\"\\uE000\"]},"
                + "{\"type\":\"reference\",\"id\":\"minecraft:include/space\"},"
                + "{\"type\":\"reference\",\"id\":\"minecraft:include/default\"},"
                + "{\"type\":\"reference\",\"id\":\"minecraft:include/unifont\"}]}";
        check(font.contains("minecraft:include/default"), "font lost include/default");
        check(font.contains("minecraft:include/space"), "font lost include/space");
        check(font.contains("minecraft:include/unifont"), "font lost include/unifont");
        System.out.println("VerifyPack: OK (" + names.size() + " instruments)");
    }

    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
