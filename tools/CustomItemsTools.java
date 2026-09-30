import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.imageio.ImageIO;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/** Small JDK-only build and maintenance tools for CustomItems. */
public final class CustomItemsTools {
    private static final String VERSION = "0.8.1";
    private static final String[] MISSING_FONT_REFS = {
            "minecraft:include/space", "minecraft:include/default", "minecraft:include/unifont"
    };

    private CustomItemsTools() {}

    public static void main(String[] args) {
        try {
            if (args.length == 0 || args[0].equals("help") || args[0].equals("--help")) {
                usage();
                return;
            }
            Options options = new Options(args, 1);
            switch (args[0]) {
                case "build" -> build(options);
                case "generate-textures" -> generateTextures(options);
                case "test-pack" -> testPack(options);
                case "setup-server" -> setupServer(options);
                case "rcon" -> rcon(options);
                default -> throw new IllegalArgumentException("unknown command: " + args[0]);
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(2);
        }
    }

    private static void usage() {
        System.out.println("CustomItems Java tools (JDK 25)");
        System.out.println("  java tools/CustomItemsTools.java build --libraries <paper-libraries>");
        System.out.println("  java tools/CustomItemsTools.java generate-textures [--out-dir <dir>]");
        System.out.println("  java tools/CustomItemsTools.java test-pack --pack <zip> [--config-dir <dir>]");
        System.out.println("  java tools/CustomItemsTools.java setup-server [--paper-jar <jar>] [--jar <jar>]");
        System.out.println("  java tools/CustomItemsTools.java rcon --password <password> [--command <text> ...]");
    }

    private static Path root() {
        for (Path dir = Path.of("").toAbsolutePath().normalize(); dir != null; dir = dir.getParent()) {
            if (Files.isDirectory(dir.resolve("src/main/java"))) return dir;
        }
        throw new IllegalStateException("run this command from the repository or one of its subdirectories");
    }

    private static void build(Options options) throws Exception {
        Path root = root();
        String version = options.get("version", VERSION);
        List<Path> classpath = buildClasspath(root, options);
        Path classes = root.resolve("build/classes");
        deleteTree(classes);
        Files.createDirectories(classes);

        List<Path> sources;
        try (Stream<Path> files = Files.walk(root.resolve("src/main/java"))) {
            sources = files.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
        if (sources.isEmpty()) throw new IllegalStateException("no Java sources found");
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IllegalStateException("a JDK is required; a JRE does not include javac");

        DiagnosticCollector<javax.tools.JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            List<String> args = new ArrayList<>(List.of("-encoding", "UTF-8", "--release", "25", "-d", classes.toString()));
            if (!classpath.isEmpty()) {
                args.addAll(List.of("-classpath", String.join(System.getProperty("path.separator"),
                        classpath.stream().map(Path::toString).toList())));
            }
            Iterable<? extends javax.tools.JavaFileObject> units = manager.getJavaFileObjectsFromPaths(sources);
            if (!compiler.getTask(null, manager, diagnostics, args, null, units).call()) {
                diagnostics.getDiagnostics().forEach(d -> System.err.printf("%s:%d: %s%n",
                        d.getSource() == null ? "javac" : Path.of(d.getSource().toUri()).getFileName(),
                        d.getLineNumber(), d.getMessage(null)));
                throw new IllegalStateException("compilation failed");
            }
        }

        Path jar = root.resolve("build/libs/CustomItems-" + version + ".jar");
        Files.createDirectories(jar.getParent());
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            addFiles(out, classes, "", null);
            addFiles(out, root.resolve("src/main/resources"), "", version);
            addFiles(out, root.resolve("textures"), "textures/", null);
        }
        System.out.println("built " + jar + " (" + sources.size() + " Java sources)");
    }

    private static List<Path> buildClasspath(Path root, Options options) throws IOException {
        String libraries = options.get("libraries", null);
        String explicit = options.get("classpath", System.getenv("PAPER_CLASSPATH"));
        boolean provided = libraries != null || (explicit != null && !explicit.isBlank());
        List<Path> jars = new ArrayList<>();
        if (libraries != null) addJars(Path.of(libraries), jars);
        if (explicit != null && !explicit.isBlank()) {
            for (String entry : explicit.split(Pattern.quote(System.getProperty("path.separator")))) {
                Path path = Path.of(entry);
                if (Files.isDirectory(path)) addJars(path, jars);
                else if (Files.isRegularFile(path)) jars.add(path.toAbsolutePath().normalize());
                else throw new IllegalArgumentException("classpath entry not found: " + path);
            }
        }
        if (jars.isEmpty() && !provided) {
            Path local = root.getParent().resolve(".mc-test/libraries");
            if (Files.isDirectory(local)) addJars(local, jars);
        }
        jars = jars.stream().distinct().sorted().toList();
        if (jars.stream().noneMatch(p -> p.getFileName().toString().toLowerCase().startsWith("paper-api")))
            throw new IllegalArgumentException("Paper API jar not found; pass Paper's libraries folder or --classpath");
        return jars;
    }

    private static void addJars(Path path, List<Path> jars) throws IOException {
        if (!Files.isDirectory(path)) throw new IllegalArgumentException("library directory not found: " + path);
        try (Stream<Path> files = Files.walk(path)) {
            jars.addAll(files.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".jar"))
                    .map(p -> p.toAbsolutePath().normalize()).toList());
        }
    }

    private static void addFiles(JarOutputStream out, Path dir, String prefix, String version) throws IOException {
        if (!Files.isDirectory(dir)) return;
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                String name = prefix + dir.relativize(file).toString().replace('\\', '/');
                byte[] data = Files.readAllBytes(file);
                if (version != null && name.equals("plugin.yml")) {
                    data = new String(data, StandardCharsets.UTF_8).replace("${version}", version)
                            .getBytes(StandardCharsets.UTF_8);
                }
                JarEntry entry = new JarEntry(name);
                entry.setTime(0);
                out.putNextEntry(entry);
                out.write(data);
                out.closeEntry();
            }
        }
    }

    private static void deleteTree(Path path) throws IOException {
        if (!Files.exists(path)) return;
        try (Stream<Path> files = Files.walk(path)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file);
        }
    }

    private static void generateTextures(Options options) throws IOException {
        Map<Character, Integer> palette = Map.ofEntries(
                Map.entry('.', 0x00000000), Map.entry('d', rgb(122, 11, 20)), Map.entry('r', rgb(214, 40, 40)),
                Map.entry('l', rgb(255, 120, 120)), Map.entry('y', rgb(232, 179, 60)), Map.entry('x', rgb(168, 122, 31)),
                Map.entry('n', rgb(107, 74, 43)), Map.entry('E', rgb(128, 124, 118)), Map.entry('M', rgb(233, 231, 226)),
                Map.entry('i', rgb(184, 180, 173)), Map.entry('j', rgb(145, 140, 132)), Map.entry('O', rgb(107, 74, 0)),
                Map.entry('A', rgb(255, 204, 51)), Map.entry('C', rgb(86, 15, 22)), Map.entry('V', rgb(190, 32, 45)),
                Map.entry('W', rgb(240, 96, 104)));
        Map<String, List<String>> art = new LinkedHashMap<>();
        art.put("ruby_sword.png", List.of(".............dd.", "............dlld", "...........dlrd.", "..........dlrd..",
                ".........dlrd...", "........dlrd....", ".......dlrd.....", "......dlrd......", ".....dlrd.......",
                "....dlrd........", "...dlrd.........", "..xdlrd.........", ".xxydd..........", "nnny............",
                "nnn.............", ".n.............."));
        art.put("marble_block.png", List.of("EEEEEEEEEEEEEEEE", "EMMMMMMMMMMMMMME", "EMMMjMMMMMMMMMME", "EMMjMMMMMMjMMMME",
                "EMMMjMMMMjMMMMME", "EMMMMjMMjMMMMMME", "EMMjMMjjMMMjMMME", "EMjMMMMMMMjMMMME", "EMMMMMMMMjMMMMME",
                "EMMjMMMMjMMjMMME", "EMjMMMMMMMMMjMME", "EMMMjMMMMMMMMMME", "EMMMMMjMMMjMMMME", "EMMMMMMMMjMMMMME",
                "EMMMMMMMMMMMMMME", "EEEEEEEEEEEEEEEE"));
        art.put("smile.png", List.of("..OOOO..", ".OAAAAO.", "OAOAAOAO", "OAAAAAAO", "OAAAAAAO", "OAAAAAAO", ".OAOOAO.", "..OOOO.."));
        art.put("admin.png", List.of("..CCCC..", ".CWWVVVC", "CWWVVVVC", "CVVVVVVC", "CVVCVVVC", "CVVCVVVC", "CVVVVVVC", ".CCCCCC."));

        Path output = Path.of(options.get("out-dir", root().resolve("textures").toString())).toAbsolutePath().normalize();
        Files.createDirectories(output);
        for (String name : art.keySet().stream().sorted().toList()) {
            List<String> rows = art.get(name);
            int width = rows.getFirst().length();
            if (rows.stream().anyMatch(row -> row.length() != width)) throw new IllegalStateException(name + ": row width mismatch");
            BufferedImage image = new BufferedImage(width, rows.size(), BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < rows.size(); y++) {
                for (int x = 0; x < width; x++) {
                    char pixel = rows.get(y).charAt(x);
                    Integer color = palette.get(pixel);
                    if (color == null) throw new IllegalStateException(name + ": unknown pixel '" + pixel + "'");
                    image.setRGB(x, y, color);
                }
            }
            Path file = output.resolve(name);
            ImageIO.write(image, "png", file.toFile());
            System.out.printf("=== %s (%d x %d) -> %s ===%n", name, width, rows.size(), file);
            rows.forEach(row -> System.out.println("  " + row));
            System.out.println();
        }
        System.out.println("generated " + art.size() + " textures in " + output);
    }

    private static int rgb(int r, int g, int b) { return 0xff000000 | (r << 16) | (g << 8) | b; }

    private static void testPack(Options options) throws Exception {
        Path pack = requiredPath(options, "pack");
        Path configDir = options.get("config-dir", null) == null ? null : Path.of(options.get("config-dir", null));
        Set<String> names = new LinkedHashSet<>();
        Map<String, String> contents = new HashMap<>();
        try (ZipFile zip = new ZipFile(pack.toFile())) {
            zip.stream().forEach(entry -> names.add(entry.getName()));
            for (String name : List.of("pack.mcmeta", "assets/minecraft/blockstates/note_block.json",
                    "assets/minecraft/font/default.json")) {
                ZipEntry entry = zip.getEntry(name);
                if (entry != null && !entry.isDirectory()) {
                    try (InputStream in = zip.getInputStream(entry)) {
                        contents.put(name, new String(in.readAllBytes(), StandardCharsets.UTF_8));
                    }
                }
            }
        }
        Report report = new Report();
        System.out.printf("pack: %s  (%.1f KB, %d entries)%n", pack, Files.size(pack) / 1024d, names.size());

        report.head("pack.mcmeta");
        String metadata = contents.get("pack.mcmeta");
        if (metadata == null) report.bad("pack.mcmeta missing");
        else try {
            Map<String, Object> packRoot = object(Json.parse(metadata)).get("pack") instanceof Map<?, ?> map
                    ? castObject(map) : Map.of();
            Object format = packRoot.get("pack_format");
            if (truthy(format)) report.ok("pack_format = " + format);
            else report.bad("pack_format missing");
            if (packRoot.containsKey("min_format") && packRoot.get("min_format") != null) {
                Object min = packRoot.get("min_format");
                if (min instanceof List<?> list && !list.isEmpty()) min = list.getFirst();
                if (format != null && numberEquals(min, format)) report.ok("min_format matches pack_format");
                else report.warn("min_format " + min + " != pack_format " + format);
            }
        } catch (Exception e) { report.bad("pack.mcmeta is not valid JSON: " + e.getMessage()); }

        report.head("note_block blockstate must map custom blocks and keep the \"\" fallback");
        String blockstate = contents.get("assets/minecraft/blockstates/note_block.json");
        if (blockstate == null) report.ok("no custom blocks in this pack - vanilla note_block.json untouched");
        else checkBlockstate(blockstate, names, report);

        List<String> minecraftItems = names.stream().filter(n -> n.matches("^assets/minecraft/items/.+\\.json$")).toList();
        if (minecraftItems.isEmpty()) report.ok("no vanilla item model definitions are overridden");
        else report.bad("resource pack overrides vanilla item definitions: " + String.join(", ", minecraftItems));
        checkBlockModels(names, report);
        checkFont(contents.get("assets/minecraft/font/default.json"), names, report);
        if (configDir != null) checkConfig(configDir, names, report);

        report.head("result");
        if (report.failures == 0) {
            System.out.println("  PASS - " + report.warnings + " warning(s)");
        } else {
            System.out.println("  " + report.failures + " FAILURE(S), " + report.warnings + " warning(s)");
            System.exit(1);
        }
    }

    private static void checkBlockstate(String text, Set<String> names, Report report) {
        if (!text.matches("(?s)^\\{\\\"variants\\\":\\{.*}}$"))
            report.bad("note_block.json is not a {\"variants\":{...}} object");
        if (text.contains("{\"model\":\"minecraft:block/note_block\"}"))
            report.ok("blockstate keeps the \"\" fallback for non-custom noteblocks");
        else report.bad("blockstate has no \"\" fallback; every vanilla noteblock in the world will lose its model");

        Matcher models = Pattern.compile("\"instrument=([^\"]+)\":\\{\"model\":\"([^\":]+:block/cblock_[^\"]+)\"}").matcher(text);
        List<String> modelVariants = new ArrayList<>();
        while (models.find()) modelVariants.add(models.group(1) + "\t" + models.group(2));
        if (!modelVariants.isEmpty()) report.ok("blockstate maps " + modelVariants.size() + " custom block variants");
        else report.bad("blockstate has no instrument= variants; custom blocks will render as vanilla noteblocks");

        Map<String, Set<String>> powered = new HashMap<>();
        Matcher keys = Pattern.compile("\"([^\"]*)\":\\{\"model\"").matcher(text);
        while (keys.find()) {
            String key = keys.group(1);
            if (key.isEmpty()) continue;
            Matcher valid = Pattern.compile("^instrument=([^,]+),note=(\\d+),powered=(true|false)$").matcher(key);
            if (!valid.matches()) {
                report.bad("variant key '" + key + "' is not in instrument=,note=,powered= form");
                continue;
            }
            powered.computeIfAbsent(valid.group(1) + ":" + valid.group(2), ignored -> new HashSet<>()).add(valid.group(3));
        }
        List<String> unpaired = powered.entrySet().stream().filter(e -> e.getValue().size() < 2).map(Map.Entry::getKey).toList();
        if (!unpaired.isEmpty()) report.bad("these block states are missing a powered=false or powered=true variant: " + String.join(", ", unpaired));
        else if (!powered.isEmpty()) report.ok("every custom block state covers both powered=false and powered=true (" + powered.size() + " states)");

        for (String variant : modelVariants) {
            String[] fields = variant.split("\t", 2);
            Matcher id = Pattern.compile("^([^:]+):block/(.+)$").matcher(fields[1]);
            if (!id.matches()) {
                report.bad("variant references invalid model ID '" + fields[1] + "'");
                continue;
            }
            String model = "assets/" + id.group(1) + "/models/block/" + id.group(2) + ".json";
            if (!names.contains(model)) report.bad("variant '" + fields[0] + "' references " + model + ", which the pack does not contain");
        }
    }

    private static void checkBlockModels(Set<String> names, Report report) {
        List<String> dangling = new ArrayList<>();
        for (String name : names) {
            Matcher model = Pattern.compile("^assets/([^/]+)/models/block/(cblock_[^/]+)\\.json$").matcher(name);
            if (!model.matches()) continue;
            String namespace = model.group(1), id = model.group(2), key = id.substring("cblock_".length());
            if (!names.contains("assets/" + namespace + "/textures/block/" + id + ".png")) dangling.add(id);
            if (!names.contains("assets/" + namespace + "/items/block/" + key + ".json")) dangling.add(id + " (item definition)");
            if (!names.contains("assets/" + namespace + "/models/item/block/" + key + ".json")) dangling.add(id + " (item model)");
        }
        if (dangling.isEmpty()) report.ok("every cblock_* block model has its texture and item model");
        else report.bad("cblock_* references missing entries: " + String.join(", ", new LinkedHashSet<>(dangling)));
    }

    private static void checkFont(String text, Set<String> names, Report report) {
        report.head("font/default.json (vanilla glyph preservation)");
        if (text == null) {
            report.ok("no font override (no ranks/emojis defined)");
            return;
        }
        try {
            Map<String, Object> font = object(Json.parse(text));
            List<?> providers = list(font.get("providers"));
            List<Object> bitmaps = new ArrayList<>();
            Set<String> refs = new HashSet<>();
            for (Object value : providers) {
                Map<String, Object> provider = object(value);
                if ("reference".equals(provider.get("type"))) refs.add(String.valueOf(provider.get("id")));
                if ("bitmap".equals(provider.get("type"))) bitmaps.add(provider);
            }
            for (String ref : MISSING_FONT_REFS) {
                if (refs.contains(ref)) report.ok("keeps " + ref);
                else report.bad("MISSING " + ref + " - this file replaces vanilla, so ordinary text will render as tofu boxes");
            }
            report.ok(bitmaps.size() + " custom bitmap provider(s)");
            List<Integer> chars = new ArrayList<>();
            for (Object value : bitmaps) {
                Map<String, Object> bitmap = object(value);
                String file = String.valueOf(bitmap.get("file")).replaceFirst("^minecraft:", "");
                if (!names.contains("assets/minecraft/textures/" + file)) report.bad("font provider file missing: " + file);
                if (bitmap.get("height") == null) report.warn("provider for " + file + " has no 'height' - PNG height must equal 'ascent'");
                for (Object row : list(bitmap.get("chars"))) {
                    String line = String.valueOf(row);
                    if (!line.isEmpty()) chars.add((int) line.charAt(0));
                }
            }
            if (!chars.isEmpty()) {
                List<Integer> stray = chars.stream().filter(c -> c < 0xE000 || c > 0xE1FF).distinct().toList();
                if (stray.isEmpty()) {
                    long ranks = chars.stream().filter(c -> c >= 0xE000 && c <= 0xE0FF).count();
                    long emojis = chars.stream().filter(c -> c >= 0xE100 && c <= 0xE1FF).count();
                    report.ok("all " + chars.size() + " glyph codepoints inside U+E000-U+E1FF (" + ranks + " rank, " + emojis + " emoji)");
                } else report.bad("glyph codepoints outside the private use area: " + stray.stream()
                        .map(c -> String.format("U+%04X", c)).reduce((a, b) -> a + ", " + b).orElse("")
                        + " - more than 256 rank/emoji entries");
                Map<Integer, Long> counts = new LinkedHashMap<>();
                chars.forEach(c -> counts.merge(c, 1L, Long::sum));
                List<String> duplicates = counts.entrySet().stream().filter(e -> e.getValue() > 1)
                        .map(e -> String.format("U+%04Xx%d", e.getKey(), e.getValue())).toList();
                if (duplicates.isEmpty()) report.ok("no duplicated glyph codepoints");
                else report.bad("duplicated glyph codepoints: " + String.join(", ", duplicates) + " - entries overwrite each other");
            }
        } catch (Exception e) { report.bad("font/default.json is not valid JSON: " + e.getMessage()); }
    }

    private static void checkConfig(Path configDir, Set<String> names, Report report) throws IOException {
        if (!Files.isDirectory(configDir)) {
            report.warn("config dir not found: " + configDir);
            return;
        }
        report.head("config vs pack (" + configDir + ")");
        Path textureDir = configDir.resolve("textures");
        Path items = configDir.resolve("items.yml");
        if (Files.isRegularFile(items)) {
            boolean inSection = false;
            for (String line : Files.readAllLines(items)) {
                if (line.matches("^\\s*items:\\s*$")) { inSection = true; continue; }
                if (!inSection || !line.matches("^ {2}[^\\s:#]+:\\s*$")) continue;
                String key = line.strip().replaceFirst(":\\s*$", "");
                String definition = names.stream().filter(n -> n.matches("^assets/([^/]+)/items/" + Pattern.quote(key) + "\\.json$"))
                        .findFirst().orElse(null);
                if (definition != null) {
                    String namespace = definition.replaceFirst("^assets/([^/]+)/items/.*$", "$1");
                    if (names.contains("assets/" + namespace + "/textures/item/" + key + ".png")) report.ok("item " + key + " model and texture packed");
                    else report.bad("item " + key + " texture is not in pack - run /ci reload");
                } else if (Files.isRegularFile(textureDir.resolve(key + ".png"))) {
                    report.bad("item " + key + " model/texture exists on disk but is NOT in pack - run /ci reload");
                } else report.bad("item " + key + " texture missing: " + textureDir.resolve(key + ".png"));
            }
        }
        checkNamedTextures(configDir.resolve("emojis.yml"), "emoji", names, report);
        checkNamedTextures(configDir.resolve("ranks.yml"), "rank", names, report);
    }

    private static void checkNamedTextures(Path file, String kind, Set<String> names, Report report) throws IOException {
        if (!Files.isRegularFile(file)) return;
        for (String line : Files.readAllLines(file)) {
            if (!line.matches("^ {2}[^\\s:#]+:\\s*$")) continue;
            String key = line.strip().replaceFirst(":\\s*$", "");
            String resource = "assets/minecraft/textures/font/" + kind + "_" + key + ".png";
            if (names.contains(resource)) report.ok(kind + " " + key + " packed");
            else report.bad(kind + " " + key + " texture not in pack - run /ci reload");
        }
    }

    private static void setupServer(Options options) throws IOException {
        Path root = root();
        Path serverRoot = Path.of(options.get("root", root.getParent().resolve(".mc-test").toString())).toAbsolutePath().normalize();
        Path paper = options.get("paper-jar", null) == null ? findPaperJar(root) : Path.of(options.get("paper-jar", null));
        Path plugin = Path.of(options.get("jar", root.resolve("releases/CustomItems-0.4.0.jar").toString()));
        if (paper == null || !Files.isRegularFile(paper)) throw new IllegalArgumentException("Paper jar not found; pass --paper-jar <path>");
        if (!Files.isRegularFile(plugin)) throw new IllegalArgumentException("plugin jar not found: " + plugin + " (build it first)");
        int port = Integer.parseInt(options.get("port", "25698"));
        int rconPort = Integer.parseInt(options.get("rcon-port", "25699"));
        String password = options.get("rcon-password", "test123");

        Path plugins = serverRoot.resolve("plugins");
        Path data = plugins.resolve("CustomItems");
        Files.createDirectories(data.resolve("textures"));
        Files.createDirectories(data.resolve("sounds"));
        Files.copy(paper, serverRoot.resolve("paper.jar"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Files.writeString(serverRoot.resolve("eula.txt"), "eula=true\n", StandardCharsets.US_ASCII);
        Files.writeString(serverRoot.resolve("server.properties"), """
                online-mode=false
                server-port=%d
                level-name=world
                level-type=minecraft\\:flat
                spawn-protection=0
                max-players=5
                view-distance=4
                simulation-distance=4
                motd=CustomItems test
                enable-rcon=true
                rcon.port=%d
                rcon.password=%s
                """.formatted(port, rconPort, password), StandardCharsets.US_ASCII);
        Files.copy(plugin, plugins.resolve(plugin.getFileName()), java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        String seed = options.get("seed-from", null);
        if (seed != null && Files.isDirectory(Path.of(seed))) {
            Path source = Path.of(seed);
            copyMatching(source.resolve("textures"), data.resolve("textures"), ".png");
            copyMatching(source, data, ".yml");
            System.out.println("seeded config+textures from " + source);
        } else System.out.println("no --seed-from given: plugin will generate default configs");

        System.out.println("\ntest server ready: " + serverRoot);
        System.out.println("  start : java -Xms512M -Xmx1024M -jar paper.jar --nogui (run from " + serverRoot + ")");
        System.out.println("  rcon  : java tools/CustomItemsTools.java rcon --port " + rconPort + " --password " + password + " --command 'ci reload'");
        System.out.println("  stop  : java tools/CustomItemsTools.java rcon --port " + rconPort + " --password " + password + " --command stop");
        System.out.println("  check : java tools/CustomItemsTools.java test-pack --pack \"" + data.resolve("pack.zip")
                + "\" --config-dir \"" + data + "\"");
    }

    private static Path findPaperJar(Path root) throws IOException {
        Path local = root.resolve(".tmp-api/paper-server.jar");
        if (Files.isRegularFile(local)) return local;
        Path desktop = Path.of(System.getProperty("user.home"), "Desktop");
        Path legacy = desktop.resolve("ARCANIA SERVER/paper-26.2-129.jar");
        if (Files.isRegularFile(legacy)) return legacy;
        if (!Files.isDirectory(desktop)) return null;
        try (Stream<Path> dirs = Files.list(desktop)) {
            for (Path dir : dirs.filter(Files::isDirectory).toList()) {
                try (Stream<Path> jars = Files.list(dir)) {
                    Path found = jars.filter(p -> p.getFileName().toString().matches("paper-26.*\\.jar$"))
                            .findFirst().orElse(null);
                    if (found != null) return found;
                }
            }
        }
        return null;
    }

    private static void copyMatching(Path from, Path to, String suffix) throws IOException {
        if (!Files.isDirectory(from)) return;
        try (Stream<Path> files = Files.list(from)) {
            for (Path file : files.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(suffix)).toList())
                Files.copy(file, to.resolve(file.getFileName()), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void rcon(Options options) throws IOException {
        String password = options.get("password", null);
        if (password == null) throw new IllegalArgumentException("--password is required");
        String host = options.get("server", "127.0.0.1");
        int port = Integer.parseInt(options.get("port", "25699"));
        List<String> commands = options.all("command");
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 10000);
            socket.setSoTimeout(10000);
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();
            sendPacket(out, 1, 3, password);
            Packet auth = readPacket(in);
            if (auth.id == -1) throw new IOException("RCON auth failed (wrong password?)");
            System.out.println("[rcon] authenticated");
            int id = 2;
            for (String command : commands) {
                sendPacket(out, id++, 2, command);
                String body = readPacket(in).body;
                System.out.println("\n> " + command);
                System.out.println(body.isBlank() ? "  (no output)" : body.stripTrailing());
            }
        }
    }

    private static void sendPacket(OutputStream out, int id, int type, String body) throws IOException {
        byte[] text = body.getBytes(StandardCharsets.UTF_8);
        ByteBuffer packet = ByteBuffer.allocate(text.length + 14).order(ByteOrder.LITTLE_ENDIAN);
        packet.putInt(text.length + 10).putInt(id).putInt(type).put(text).put((byte) 0).put((byte) 0);
        out.write(packet.array());
        out.flush();
    }

    private static Packet readPacket(InputStream in) throws IOException {
        byte[] lengthBytes = in.readNBytes(4);
        if (lengthBytes.length != 4) throw new IOException("connection closed");
        int length = ByteBuffer.wrap(lengthBytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
        if (length < 10 || length > 1_048_576) throw new IOException("invalid RCON packet length: " + length);
        byte[] packet = in.readNBytes(length);
        if (packet.length != length) throw new IOException("connection closed");
        ByteBuffer data = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN);
        int id = data.getInt();
        data.getInt(); // packet type
        byte[] body = Arrays.copyOfRange(packet, 8, length - 2);
        return new Packet(id, new String(body, StandardCharsets.UTF_8));
    }

    private record Packet(int id, String body) {}

    private static Path requiredPath(Options options, String key) {
        String value = options.get(key, null);
        if (value == null) throw new IllegalArgumentException("--" + key + " is required");
        return Path.of(value).toAbsolutePath().normalize();
    }

    private static final class Options {
        private final Map<String, List<String>> values = new LinkedHashMap<>();
        Options(String[] args, int start) {
            for (int i = start; i < args.length; i++) {
                String key = args[i];
                if (!key.startsWith("--")) throw new IllegalArgumentException("expected an option, got " + key);
                if (++i == args.length || args[i].startsWith("--")) throw new IllegalArgumentException("missing value for " + key);
                values.computeIfAbsent(key.substring(2), ignored -> new ArrayList<>()).add(args[i]);
            }
        }
        String get(String key, String fallback) { return values.containsKey(key) ? values.get(key).getLast() : fallback; }
        List<String> all(String key) { return values.getOrDefault(key, List.of()); }
    }

    private static final class Report {
        int failures;
        int warnings;
        void ok(String message) { System.out.println("  [ OK ] " + message); }
        void bad(String message) { System.out.println("  [FAIL] " + message); failures++; }
        void warn(String message) { System.out.println("  [WARN] " + message); warnings++; }
        void head(String message) { System.out.println("\n" + message); }
    }

    private static boolean truthy(Object value) {
        return value != null && (!(value instanceof Number n) || n.doubleValue() != 0)
                && (!(value instanceof String s) || !s.isEmpty()) && !Boolean.FALSE.equals(value);
    }

    private static boolean numberEquals(Object left, Object right) {
        if (left instanceof Number a && right instanceof Number b) return Double.compare(a.doubleValue(), b.doubleValue()) == 0;
        return java.util.Objects.equals(left, right);
    }

    private static List<?> list(Object value) { return value instanceof List<?> list ? list : List.of(); }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private static Map<String, Object> castObject(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    /** Minimal JSON reader for the resource-pack checks; supports the complete JSON value grammar. */
    private static final class Json {
        private final String text;
        private int at;
        private Json(String text) { this.text = text; }
        static Object parse(String text) {
            Json parser = new Json(text);
            Object value = parser.value();
            parser.space();
            if (parser.at != text.length()) throw parser.error("trailing data");
            return value;
        }
        private Object value() {
            space();
            if (at == text.length()) throw error("expected value");
            return switch (text.charAt(at)) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", true);
                case 'f' -> literal("false", false);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }
        private Map<String, Object> object() {
            at++;
            Map<String, Object> result = new LinkedHashMap<>();
            space();
            if (take('}')) return result;
            do {
                space();
                if (at == text.length() || text.charAt(at) != '"') throw error("expected object key");
                String key = string();
                space();
                if (!take(':')) throw error("expected ':'");
                result.put(key, value());
                space();
                if (take('}')) return result;
            } while (take(','));
            throw error("expected ',' or '}'");
        }
        private List<Object> array() {
            at++;
            List<Object> result = new ArrayList<>();
            space();
            if (take(']')) return result;
            do {
                result.add(value());
                space();
                if (take(']')) return result;
            } while (take(','));
            throw error("expected ',' or ']'");
        }
        private String string() {
            at++;
            StringBuilder result = new StringBuilder();
            while (at < text.length()) {
                char c = text.charAt(at++);
                if (c == '"') return result.toString();
                if (c < 0x20) throw error("control character in string");
                if (c != '\\') { result.append(c); continue; }
                if (at == text.length()) throw error("unfinished escape");
                char escaped = text.charAt(at++);
                switch (escaped) {
                    case '"', '\\', '/' -> result.append(escaped);
                    case 'b' -> result.append('\b');
                    case 'f' -> result.append('\f');
                    case 'n' -> result.append('\n');
                    case 'r' -> result.append('\r');
                    case 't' -> result.append('\t');
                    case 'u' -> {
                        if (at + 4 > text.length()) throw error("unfinished unicode escape");
                        try { result.append((char) Integer.parseInt(text.substring(at, at + 4), 16)); }
                        catch (NumberFormatException e) { throw error("invalid unicode escape"); }
                        at += 4;
                    }
                    default -> throw error("invalid escape");
                }
            }
            throw error("unterminated string");
        }
        private Object number() {
            int start = at;
            take('-');
            if (take('0')) {
                if (at < text.length() && Character.isDigit(text.charAt(at))) throw error("leading zero in number");
            } else {
                if (at == text.length() || text.charAt(at) < '1' || text.charAt(at) > '9') throw error("invalid number");
                while (at < text.length() && text.charAt(at) >= '0' && text.charAt(at) <= '9') at++;
            }
            if (take('.')) {
                int fraction = at;
                while (at < text.length() && text.charAt(at) >= '0' && text.charAt(at) <= '9') at++;
                if (at == fraction) throw error("fraction requires digits");
            }
            if (at < text.length() && (text.charAt(at) == 'e' || text.charAt(at) == 'E')) {
                at++;
                if (at < text.length() && (text.charAt(at) == '+' || text.charAt(at) == '-')) at++;
                int exponent = at;
                while (at < text.length() && text.charAt(at) >= '0' && text.charAt(at) <= '9') at++;
                if (at == exponent) throw error("exponent requires digits");
            }
            try { return new BigDecimal(text.substring(start, at)); }
            catch (NumberFormatException e) { throw error("invalid number"); }
        }
        private Object literal(String name, Object value) {
            if (!text.startsWith(name, at)) throw error("invalid value");
            at += name.length();
            return value;
        }
        private boolean take(char c) {
            if (at < text.length() && text.charAt(at) == c) { at++; return true; }
            return false;
        }
        private void space() {
            while (at < text.length() && (text.charAt(at) == ' ' || text.charAt(at) == '\t'
                    || text.charAt(at) == '\n' || text.charAt(at) == '\r')) at++;
        }
        private IllegalArgumentException error(String message) { return new IllegalArgumentException(message + " at character " + at); }
    }
}
