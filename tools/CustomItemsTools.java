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
import java.util.Locale;
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
    private static final String VERSION = "0.8.5";
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
                case "import-oraxen" -> importOraxen(options);
                case "import-itemsadder" -> importItemsAdder(options);
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
        System.out.println("  java tools/CustomItemsTools.java import-oraxen --zip <pack.zip> --out <CustomItems-data-folder>");
        System.out.println("  java tools/CustomItemsTools.java import-itemsadder --zip <pack.zip> --out <CustomItems-data-folder>");
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

    private static void importOraxen(Options options) throws Exception {
        Path archive = requiredPath(options, "zip");
        Path output = requiredPath(options, "out");
        Files.createDirectories(output);
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            List<? extends ZipEntry> configs = zip.stream()
                    .filter(entry -> entry.getName().replace('\\', '/').matches("(?i)(?:.*/)?plugins/Oraxen/items/.+\\.yml"))
                    .sorted(Comparator.comparing(entry -> entry.getName().replace('\\', '/'))).toList();
            if (configs.isEmpty()) throw new IOException("Oraxen items/**/*.yml not found in " + archive);

            ImportReport report = new ImportReport("Oraxen", archive);
            Set<String> importedKeys = new HashSet<>();
            for (ZipEntry entry : zip.stream().filter(e -> !e.isDirectory()).toList()) {
                String name = entry.getName().replace('\\', '/');
                if (!name.matches("(?i)(?:.*/)?plugins/Oraxen/.+\\.yml")) continue;
                try (InputStream in = zip.getInputStream(entry)) {
                    report.scan(name, new String(in.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
            for (ZipEntry config : configs) {
                String configPath = config.getName().replace('\\', '/');
                String yaml;
                try (InputStream in = zip.getInputStream(config)) {
                    yaml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
                List<ImportedItem> items = parseOraxenItems(yaml);
                if (items.isEmpty()) {
                    report.skip(configPath + ": no supported item entries");
                    continue;
                }

                String pluginPrefix = configPath.substring(0, configPath.lastIndexOf("items/"));
                String relativeConfig = configPath.substring(pluginPrefix.length() + "items/".length());
                String fileName = safeFileName(relativeConfig.replace('/', '_'));
                Path importedFile = output.resolve("imports").resolve(fileName);
                if (Files.exists(importedFile)) {
                    throw new IOException("import already exists; remove it first to replace: " + importedFile);
                }
                Path assets = output.resolve("pack-assets/assets");
                int copied = copyOraxenAssets(zip, pluginPrefix + "pack/models/",
                        assets.resolve("minecraft/models"), ".json");
                copied += copyOraxenAssets(zip, pluginPrefix + "pack/textures/",
                        assets.resolve("minecraft/textures"), ".png", ".mcmeta");
                Map<String, String> armorModels = importOraxenArmorModels(zip, pluginPrefix, assets, report);
                report.assets += copied;

                StringBuilder generated = new StringBuilder("items:\n");
                for (ImportedItem item : items) {
                    if (importedKeys.contains(item.key())) {
                        report.skip(item.key() + ": duplicate key in " + configPath);
                        continue;
                    }
                    String textureDestination = null;
                    if (item.model() != null) {
                        String modelFile = pluginPrefix + "pack/models/" + item.model() + ".json";
                        if (zip.getEntry(modelFile) == null) {
                            report.skip(item.key() + ": model missing from archive (" + modelFile + ")");
                            continue;
                        }
                    } else if (item.texture() != null) {
                        String texture = item.texture().replaceFirst("(?i)\\.png$", "");
                        String textureEntry = pluginPrefix + "pack/textures/" + texture + ".png";
                        ZipEntry textureFile = zip.getEntry(textureEntry);
                        if (textureFile == null) {
                            report.skip(item.key() + ": texture missing from archive (" + textureEntry + ")");
                            continue;
                        }
                        textureDestination = "imported/oraxen/" + item.key() + ".png";
                        copyZipEntry(zip, textureFile, output.resolve("textures").resolve(textureDestination));
                    } else {
                        report.skip(item.key() + ": item has no supported model or texture");
                        continue;
                    }
                    importedKeys.add(item.key());
                    generated.append("  ").append(item.key()).append(":\n")
                            .append("    base: ").append(item.base()).append('\n')
                            .append("    name: ").append(yamlString(item.name())).append('\n');
                    if (item.model() != null) {
                        generated.append("    model: ").append(yamlString("minecraft:" + item.model())).append('\n');
                        ImportedStates states = mergeStates(item.states(), inferModelStates(assets,
                                "minecraft", item.base(), item.model()));
                        appendModelStates(generated, states, "minecraft");
                    } else {
                        generated.append("    texture: ").append(yamlString(textureDestination)).append('\n');
                    }
                    String armorModel = armorModels.get(parentPath(item.texture()));
                    if (armorModel != null && isArmorMaterial(item.base())) {
                        generated.append("    armor-model: ").append(yamlString(armorModel)).append('\n');
                    }
                    report.imported(item.key());
                }
                if (generated.length() > "items:\n".length()) {
                    Files.createDirectories(importedFile.getParent());
                    Files.writeString(importedFile, generated, StandardCharsets.UTF_8);
                }
            }
            report.finish(output.resolve("imports"));
        }
    }

    private static void importItemsAdder(Options options) throws Exception {
        Path archive = requiredPath(options, "zip");
        Path output = requiredPath(options, "out");
        Files.createDirectories(output);
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            List<? extends ZipEntry> configs = zip.stream().filter(entry -> !entry.isDirectory())
                    .filter(entry -> entry.getName().replace('\\', '/').matches("(?i)(?:.*/)?contents/[^/]+/configs/.+\\.yml"))
                    .sorted(Comparator.comparing(ZipEntry::getName)).toList();
            if (configs.isEmpty()) throw new IOException("ItemsAdder contents/*/configs/*.yml not found in " + archive);
            ImportReport report = new ImportReport("ItemsAdder", archive);
            Set<String> importedKeys = new HashSet<>();
            for (ZipEntry config : configs) {
                String configPath = config.getName().replace('\\', '/');
                String lowerPath = configPath.toLowerCase(Locale.ROOT);
                int configsIndex = lowerPath.lastIndexOf("configs/");
                if (configsIndex < 0) continue;
                String contentRoot = configPath.substring(0, configsIndex);
                String folderNamespace = contentRoot.substring(contentRoot.toLowerCase(Locale.ROOT).lastIndexOf("contents/")
                        + "contents/".length()).replaceAll("/$", "");
                String text;
                try (InputStream in = zip.getInputStream(config)) {
                    text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
                report.scan(configPath, text);
                ItemsAdderData data = parseItemsAdderItems(text, folderNamespace);
                if (data.items().isEmpty()) continue;
                if (!data.namespace().matches("[a-z0-9._-]+")) {
                    report.skip("invalid ItemsAdder namespace in " + configPath + ": " + data.namespace());
                    continue;
                }
                String relativeConfig = configPath.substring(configsIndex + "configs/".length());
                String fileName = safeFileName(data.namespace() + "_" + relativeConfig.replace('/', '_'));
                Path importedFile = output.resolve("imports").resolve(fileName);
                if (Files.exists(importedFile)) throw new IOException("import already exists; remove it first to replace: " + importedFile);
                report.assets += copyItemsAdderAssets(zip, contentRoot, data.namespace(), output.resolve("pack-assets/assets"));
                Set<String> armorModels = writeItemsAdderArmorModels(zip, contentRoot, data,
                        output.resolve("pack-assets/assets"), report);

                StringBuilder generated = new StringBuilder("items:\n");
                for (ItemsAdderItem item : data.items()) {
                    if (importedKeys.contains(item.key())) {
                        report.skip(item.key() + ": duplicate key across ItemsAdder namespaces/configs");
                        continue;
                    }
                    Path modelFile = item.model() == null ? null
                            : importedModelPath(output.resolve("pack-assets/assets"), item.model());
                    String texturePath = item.texture() == null ? null : withPngExtension(item.texture());
                    String textureFile = texturePath == null ? null : output.resolve("pack-assets/assets")
                            .resolve(data.namespace()).resolve("textures").resolve(texturePath).normalize().toString();
                    if (item.model() != null && !modelAvailable(output.resolve("pack-assets/assets"), item.model())) {
                        report.skip(item.key() + ": ItemsAdder model not found (" + modelFile + ")");
                        continue;
                    }
                    if (item.model() == null && (textureFile == null || !Files.isRegularFile(Path.of(textureFile)))) {
                        report.skip(item.key() + ": ItemsAdder texture not found (" + textureFile + ")");
                        continue;
                    }
                    generated.append("  ").append(item.key()).append(":\n")
                            .append("    base: ").append(item.base()).append('\n')
                            .append("    name: ").append(yamlString(item.name())).append('\n');
                    if (item.model() != null) {
                        generated.append("    model: ").append(yamlString(item.model())).append('\n');
                        Path assets = output.resolve("pack-assets/assets");
                        ImportedStates states = validateModelStates(item.key(), assets, report, mergeStates(
                                item.states(), inferModelStates(assets, data.namespace(), item.base(), item.model())));
                        appendModelStates(generated, states, data.namespace());
                    } else {
                        String destination = "imported/itemsadder/" + data.namespace() + "/" + item.key() + ".png";
                        copyZipEntry(zip, findItemsAdderTexture(zip, contentRoot, data.namespace(), texturePath),
                                output.resolve("textures").resolve(destination));
                        generated.append("    texture: ").append(yamlString(destination)).append('\n');
                    }
                    if (item.textureCount() > 1) report.note(item.key()
                            + ": multiple texture layers/states found; only the normal/base texture is imported");
                    if (item.armorModel() != null && armorModels.contains(item.armorModel())) {
                        generated.append("    armor-model: ")
                                .append(yamlString(data.namespace() + ":" + item.armorModel())).append('\n');
                    } else if (item.armorModel() != null) {
                        report.note(item.key() + ": ItemsAdder armor set not found (" + item.armorModel() + ")");
                    }
                    importedKeys.add(item.key());
                    report.imported(item.key());
                }
                if (generated.length() == "items:\n".length()) continue;
                Files.createDirectories(importedFile.getParent());
                Files.writeString(importedFile, generated, StandardCharsets.UTF_8);
            }
            report.finish(output.resolve("imports"));
        }
    }

    private static ItemsAdderData parseItemsAdderItems(String yaml, String fallbackNamespace) {
        String namespace = fallbackNamespace.toLowerCase(Locale.ROOT);
        List<ItemsAdderItem> items = new ArrayList<>();
        List<ArmorRendering> armors = new ArrayList<>();
        String armorKey = null, layer1 = null, layer2 = null;
        String key = null, base = null, name = null, legacyModel = null, legacyTexture = null;
        String graphicsModel = null, graphicsTexture = null, armorSlot = null, armorModel = null;
        Map<String, String> graphicsModels = new LinkedHashMap<>();
        Map<String, String> graphicsTextures = new LinkedHashMap<>();
        int textureCount = 0;
        boolean inItems = false, inArmors = false, inResource = false, inGraphics = false;
        boolean inGraphicsModels = false, inGraphicsTextures = false, inArmorProperties = false, readingTextures = false;
        for (String line : yaml.split("\\R")) {
            if (!inItems) {
                Matcher namespaceField = Pattern.compile("^ {2}namespace:\\s*(.*?)\\s*$").matcher(line);
                if (namespaceField.matches()) namespace = unquote(namespaceField.group(1)).toLowerCase(Locale.ROOT);
                if (line.matches("^armors_rendering:\\s*$")) { inArmors = true; continue; }
                if (line.matches("^items:\\s*$")) {
                    addArmorRendering(armors, armorKey, layer1, layer2);
                    inArmors = false;
                    inItems = true;
                    continue;
                }
                if (inArmors) {
                    Matcher armorStart = Pattern.compile("^ {2}([a-zA-Z0-9_-]+):\\s*$").matcher(line);
                    if (armorStart.matches()) {
                        addArmorRendering(armors, armorKey, layer1, layer2);
                        armorKey = armorStart.group(1);
                        layer1 = layer2 = null;
                        continue;
                    }
                    Matcher layer = Pattern.compile("^ {4}(layer_1|layer_2):\\s*(.*?)\\s*$").matcher(line);
                    if (layer.matches()) {
                        String path = cleanItemsAdderPath(unquote(layer.group(2)));
                        if (layer.group(1).equals("layer_1")) layer1 = path;
                        else layer2 = path;
                    }
                }
                continue;
            }
            Matcher itemStart = Pattern.compile("^ {2}([a-zA-Z0-9_-]+):\\s*$").matcher(line);
            if (itemStart.matches()) {
                addItemsAdderItem(items, namespace, key, base, name, legacyModel, legacyTexture,
                        graphicsModel, graphicsTexture, graphicsModels, graphicsTextures,
                        armorSlot, armorModel, textureCount);
                key = itemStart.group(1).toLowerCase(Locale.ROOT);
                base = name = legacyModel = legacyTexture = graphicsModel = graphicsTexture = armorSlot = armorModel = null;
                graphicsModels = new LinkedHashMap<>();
                graphicsTextures = new LinkedHashMap<>();
                textureCount = 0;
                inResource = inGraphics = inGraphicsModels = inGraphicsTextures = inArmorProperties = readingTextures = false;
                continue;
            }
            if (line.matches("^[^\\s#][^:]*:\\s*$")) {
                addItemsAdderItem(items, namespace, key, base, name, legacyModel, legacyTexture,
                        graphicsModel, graphicsTexture, graphicsModels, graphicsTextures,
                        armorSlot, armorModel, textureCount);
                inItems = false;
                break;
            }
            Matcher section = Pattern.compile("^ {4}([a-zA-Z_]+):\\s*$").matcher(line);
            if (section.matches()) {
                inResource = section.group(1).equals("resource");
                inGraphics = section.group(1).equals("graphics");
                inArmorProperties = section.group(1).equals("specific_properties");
                inGraphicsModels = inGraphicsTextures = readingTextures = false;
                continue;
            }
            Matcher property = Pattern.compile("^ {4}(display_name|name|material):\\s*(.*?)\\s*$").matcher(line);
            if (property.matches()) {
                switch (property.group(1)) {
                    case "display_name", "name" -> name = unquote(property.group(2));
                    case "material" -> base = unquote(property.group(2)).toUpperCase(Locale.ROOT);
                }
                continue;
            }
            if (inGraphics && line.matches("^ {6}models:\\s*$")) {
                inGraphicsModels = true;
                inGraphicsTextures = false;
                continue;
            }
            if (inGraphics && line.matches("^ {6}textures:\\s*$")) {
                inGraphicsTextures = true;
                inGraphicsModels = false;
                continue;
            }
            Matcher graphicsProperty = Pattern.compile("^ {6}(model|texture|icon|parent):\\s*(.*?)\\s*$").matcher(line);
            if (inGraphics && graphicsProperty.matches()) {
                String value = unquote(graphicsProperty.group(2));
                if (graphicsProperty.group(1).equals("model")) graphicsModel = value;
                if (graphicsProperty.group(1).equals("texture")) graphicsTexture = value;
                inGraphicsModels = inGraphicsTextures = false;
                continue;
            }
            Matcher graphicsEntry = Pattern.compile("^ {8}([a-zA-Z0-9_-]+):\\s*(.*?)\\s*$").matcher(line);
            if (inGraphicsModels && graphicsEntry.matches()) {
                graphicsModels.put(graphicsEntry.group(1).toLowerCase(Locale.ROOT), unquote(graphicsEntry.group(2)));
                continue;
            }
            if (inGraphicsTextures && graphicsEntry.matches()) {
                graphicsTextures.put(graphicsEntry.group(1).toLowerCase(Locale.ROOT), unquote(graphicsEntry.group(2)));
                textureCount++;
                continue;
            }
            Matcher material = Pattern.compile("^ {6}material:\\s*(.*?)\\s*$").matcher(line);
            if (inResource && material.matches()) { base = unquote(material.group(1)).toUpperCase(Locale.ROOT); continue; }
            Matcher modelField = Pattern.compile("^ {6}model_path:\\s*(.*?)\\s*$").matcher(line);
            if (inResource && modelField.matches()) { legacyModel = unquote(modelField.group(1)); continue; }
            if (inResource && line.matches("^ {6}textures:\\s*$")) { readingTextures = true; continue; }
            Matcher textureField = Pattern.compile("^ {6,8}-\\s*(.*?)\\s*$").matcher(line);
            if (inResource && readingTextures && textureField.matches()) {
                String value = cleanItemsAdderPath(unquote(textureField.group(1)));
                if (value != null) {
                    textureCount++;
                    if (legacyTexture == null) legacyTexture = value;
                }
                continue;
            }
            Matcher slot = Pattern.compile("^ {8}slot:\\s*(.*?)\\s*$").matcher(line);
            if (inArmorProperties && slot.matches()) armorSlot = unquote(slot.group(1)).toLowerCase(Locale.ROOT);
            Matcher customArmor = Pattern.compile("^ {8}custom_armor:\\s*(.*?)\\s*$").matcher(line);
            if (inArmorProperties && customArmor.matches()) armorModel = unquote(customArmor.group(1));
        }
        if (inItems) addItemsAdderItem(items, namespace, key, base, name, legacyModel, legacyTexture,
                graphicsModel, graphicsTexture, graphicsModels, graphicsTextures,
                armorSlot, armorModel, textureCount);
        else addArmorRendering(armors, armorKey, layer1, layer2);
        return new ItemsAdderData(namespace, List.copyOf(items), List.copyOf(armors));
    }

    private static void addItemsAdderItem(List<ItemsAdderItem> items, String namespace, String key, String base,
                                          String name, String legacyModel, String legacyTexture,
                                          String graphicsModel, String graphicsTexture,
                                          Map<String, String> graphicsModels, Map<String, String> graphicsTextures,
                                          String armorSlot, String armorModel, int textureCount) {
        if (key == null) return;
        if (base == null) base = switch (armorSlot == null ? "" : armorSlot) {
            case "head" -> "LEATHER_HELMET";
            case "chest" -> "LEATHER_CHESTPLATE";
            case "legs" -> "LEATHER_LEGGINGS";
            case "feet" -> "LEATHER_BOOTS";
            default -> "PAPER";
        };
        if (!base.matches("[A-Z0-9_]+")) return;
        String model = graphicsModels.getOrDefault("normal", graphicsModel == null ? legacyModel : graphicsModel);
        String texture = graphicsTextures.getOrDefault("normal", graphicsTexture == null ? legacyTexture : graphicsTexture);
        model = model == null ? null : qualifyItemsAdderModel(namespace, model);
        if (model != null && !safeModelId(model)) model = null;
        if (armorModel != null && !safeResourcePath(armorModel)) armorModel = null;
        if (texture != null) {
            texture = cleanItemsAdderPath(texture);
            if (texture != null && !safeResourcePath(texture)) texture = null;
        }
        if (model != null || texture != null) {
            List<String> pulling = new ArrayList<>();
            for (String state : List.of("pulling_0", "pulling_1", "pulling_2")) {
                String value = graphicsModels.get(state);
                if (value != null) {
                    String qualified = qualifyOptionalModel(namespace, value);
                    if (qualified != null) pulling.add(qualified);
                }
            }
            String charged = modelValue(namespace, graphicsModels.get("charged"), graphicsModels.get("arrow"));
            String firework = modelValue(namespace, graphicsModels.get("firework"), graphicsModels.get("rocket"));
            String cast = qualifyOptionalModel(namespace, graphicsModels.get("cast"));
            String blocking = qualifyOptionalModel(namespace, graphicsModels.get("blocking"));
            ImportedStates states = new ImportedStates(pulling.size() == 3 ? pulling : List.of(),
                    charged, firework, cast, blocking);
            items.add(new ItemsAdderItem(key, base, name == null || name.isBlank() ? key : name,
                    model, texture, armorSlot, armorModel, textureCount, states));
        }
    }

    private static String modelValue(String namespace, String preferred, String fallback) {
        return qualifyOptionalModel(namespace, preferred == null ? fallback : preferred);
    }

    private static String qualifyOptionalModel(String namespace, String model) {
        if (model == null) return null;
        String qualified = qualifyItemsAdderModel(namespace, model);
        return safeModelId(qualified) ? qualified : null;
    }

    private static String qualifyItemsAdderModel(String namespace, String model) {
        int colon = model.indexOf(':');
        String modelNamespace = colon < 0 ? namespace : model.substring(0, colon).toLowerCase(Locale.ROOT);
        String path = cleanItemsAdderPath(model);
        return path == null ? null : modelNamespace + ":" + path;
    }

    private static boolean safeModelId(String model) {
        int colon = model.indexOf(':');
        return colon > 0 && model.indexOf(':', colon + 1) < 0
                && model.substring(0, colon).matches("[a-z0-9._-]+")
                && safeResourcePath(model.substring(colon + 1));
    }

    private static void addArmorRendering(List<ArmorRendering> armors, String key, String layer1, String layer2) {
        if (key != null && safeResourcePath(key) && layer1 != null && layer2 != null) {
            armors.add(new ArmorRendering(key, layer1, layer2));
        }
    }

    private static int copyItemsAdderAssets(ZipFile zip, String contentRoot, String namespace, Path output) throws IOException {
        Set<String> prefixes = new LinkedHashSet<>(List.of(
                contentRoot + "resourcepack/assets/",
                contentRoot + "assets/",
                contentRoot + "resourcepack/" + namespace + "/",
                contentRoot + namespace + "/"));
        int copied = 0;
        for (ZipEntry entry : zip.stream().sorted(Comparator.comparing(ZipEntry::getName)).toList()) {
            String name = entry.getName().replace('\\', '/');
            if (entry.isDirectory()) continue;
            String relative = null;
            for (String prefix : prefixes) {
                if (!name.startsWith(prefix)) continue;
                String suffix = name.substring(prefix.length());
                relative = prefix.endsWith("resourcepack/" + namespace + "/") || prefix.equals(contentRoot + namespace + "/")
                        ? namespace + "/" + suffix : suffix;
                break;
            }
            if (relative == null) {
                for (String kind : List.of("models", "textures", "equipment")) {
                    String prefix = contentRoot + kind + "/";
                    if (name.startsWith(prefix)) { relative = namespace + "/" + kind + "/" + name.substring(prefix.length()); break; }
                }
            }
            if (relative == null || !safeResourcePath(relative)) continue;
            String[] parts = relative.split("/", 3);
            if (parts.length < 3 || !List.of("models", "textures", "equipment").contains(parts[1])) continue;
            String lower = relative.toLowerCase(Locale.ROOT);
            if (!(lower.endsWith(".json") || lower.endsWith(".png") || lower.endsWith(".mcmeta"))) continue;
            if (copyZipEntry(zip, entry, output.resolve(relative.replace('/', java.io.File.separatorChar)))) copied++;
        }
        return copied;
    }

    private static ZipEntry findItemsAdderTexture(ZipFile zip, String contentRoot, String namespace, String texture) {
        if (texture == null) return null;
        Set<String> candidates = new LinkedHashSet<>();
        candidates.add(contentRoot + "resourcepack/assets/" + namespace + "/textures/" + texture);
        candidates.add(contentRoot + "assets/" + namespace + "/textures/" + texture);
        candidates.add(contentRoot + "resourcepack/" + namespace + "/textures/" + texture);
        candidates.add(contentRoot + namespace + "/textures/" + texture);
        candidates.add(contentRoot + "textures/" + texture);
        for (String path : candidates) {
            ZipEntry entry = zip.getEntry(path);
            if (entry != null) return entry;
        }
        return null;
    }

    private static Set<String> writeItemsAdderArmorModels(ZipFile zip, String contentRoot, ItemsAdderData data,
                                                          Path assets, ImportReport report) throws IOException {
        Set<String> written = new HashSet<>();
        for (ArmorRendering armor : data.armors()) {
            ZipEntry layer1 = findItemsAdderTexture(zip, contentRoot, data.namespace(), withPngExtension(armor.layer1()));
            ZipEntry layer2 = findItemsAdderTexture(zip, contentRoot, data.namespace(), withPngExtension(armor.layer2()));
            if (layer1 == null || layer2 == null) {
                report.note("ItemsAdder armor set " + armor.key() + " was not imported: missing layer texture");
                continue;
            }
            copyZipEntry(zip, layer1, assets.resolve(data.namespace()).resolve("textures/entity/equipment/humanoid")
                    .resolve(armor.key() + ".png"));
            copyZipEntry(zip, layer2, assets.resolve(data.namespace()).resolve("textures/entity/equipment/humanoid_leggings")
                    .resolve(armor.key() + ".png"));
            Path equipment = assets.resolve(data.namespace()).resolve("equipment").resolve(armor.key() + ".json");
            if (!Files.exists(equipment)) {
                Files.createDirectories(equipment.getParent());
                String json = "{\"layers\":{\"humanoid\":[{\"texture\":\"" + data.namespace()
                        + ":entity/equipment/humanoid/" + armor.key() + "\"}],\"humanoid_leggings\":[{\"texture\":\""
                        + data.namespace() + ":entity/equipment/humanoid_leggings/" + armor.key() + "\"}]}}";
                Files.writeString(equipment, json, StandardCharsets.UTF_8);
            }
            written.add(armor.key());
        }
        return written;
    }

    private static Map<String, String> importOraxenArmorModels(ZipFile zip, String pluginPrefix, Path assets,
                                                               ImportReport report)
            throws IOException {
        String texturePrefix = pluginPrefix + "pack/textures/";
        Map<String, List<String>> modelByDirectory = new HashMap<>();
        for (ZipEntry entry : zip.stream().sorted(Comparator.comparing(ZipEntry::getName)).toList()) {
            String name = entry.getName().replace('\\', '/');
            if (!name.startsWith(texturePrefix) || !name.endsWith("_armor_layer_1.png")) continue;
            String layer1Path = name.substring(texturePrefix.length());
            if (!safeResourcePath(layer1Path)) continue;
            String setPath = layer1Path.substring(0, layer1Path.length() - "_layer_1.png".length());
            String layer2EntryName = texturePrefix + setPath + "_layer_2.png";
            ZipEntry layer2 = zip.getEntry(layer2EntryName);
            if (layer2 == null) continue;
            String setKey = setPath;
            copyZipEntry(zip, entry, assets.resolve("minecraft/textures/entity/equipment/humanoid")
                    .resolve(setKey + ".png"));
            copyZipEntry(zip, layer2, assets.resolve("minecraft/textures/entity/equipment/humanoid_leggings")
                    .resolve(setKey + ".png"));
            Path equipment = assets.resolve("minecraft/equipment").resolve(setKey + ".json");
            if (!Files.exists(equipment)) {
                Files.createDirectories(equipment.getParent());
                String json = "{\"layers\":{\"humanoid\":[{\"texture\":\"minecraft:entity/equipment/humanoid/"
                        + setKey + "\"}],\"humanoid_leggings\":[{\"texture\":\"minecraft:entity/equipment/humanoid_leggings/"
                        + setKey + "\"}]}}";
                Files.writeString(equipment, json, StandardCharsets.UTF_8);
            }
            String directory = parentPath(setKey);
            modelByDirectory.computeIfAbsent(directory, ignored -> new ArrayList<>()).add("minecraft:" + setKey);
        }
        Map<String, String> unambiguous = new HashMap<>();
        modelByDirectory.forEach((directory, models) -> {
            if (models.size() == 1) unambiguous.put(directory, models.getFirst());
            else report.note("multiple Oraxen armor layer pairs in " + directory
                    + "; set armor-model manually for items using this directory");
        });
        return unambiguous;
    }

    private static ImportedStates inferModelStates(Path assets, String namespace, String base, String model) {
        if (model == null) return ImportedStates.EMPTY;
        String root = model.indexOf(':') >= 0 ? model : namespace + ":" + model;
        List<String> pulling = List.of();
        if (base.equals("BOW") || base.equals("CROSSBOW")) {
            List<String> candidates = List.of(root + "_0", root + "_1", root + "_2");
            if (candidates.stream().allMatch(id -> modelFileExists(assets, id))) pulling = candidates;
        }
        String charged = base.equals("CROSSBOW") && modelFileExists(assets, root + "_charged") ? root + "_charged" : null;
        String firework = base.equals("CROSSBOW") && modelFileExists(assets, root + "_firework") ? root + "_firework" : null;
        String cast = base.equals("FISHING_ROD") && modelFileExists(assets, root + "_cast") ? root + "_cast" : null;
        String blocking = base.equals("SHIELD") && modelFileExists(assets, root + "_blocking") ? root + "_blocking" : null;
        return new ImportedStates(pulling, charged, firework, cast, blocking);
    }

    private static Path importedModelPath(Path assets, String model) {
        int colon = model.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : model.substring(0, colon);
        String path = colon < 0 ? model : model.substring(colon + 1);
        return assets.resolve(namespace).resolve("models").resolve(path + ".json").normalize();
    }

    private static ImportedStates mergeStates(ImportedStates preferred, ImportedStates fallback) {
        return new ImportedStates(preferred.pulling().isEmpty() ? fallback.pulling() : preferred.pulling(),
                preferred.charged() == null ? fallback.charged() : preferred.charged(),
                preferred.firework() == null ? fallback.firework() : preferred.firework(),
                preferred.cast() == null ? fallback.cast() : preferred.cast(),
                preferred.blocking() == null ? fallback.blocking() : preferred.blocking());
    }

    private static boolean modelFileExists(Path assets, String id) {
        int colon = id.indexOf(':');
        if (colon <= 0) return false;
        Path path = assets.resolve(id.substring(0, colon)).resolve("models")
                .resolve(id.substring(colon + 1) + ".json").normalize();
        return path.startsWith(assets.normalize()) && Files.isRegularFile(path);
    }

    private static boolean modelAvailable(Path assets, String id) {
        return id.startsWith("minecraft:item/") || id.startsWith("minecraft:block/") || modelFileExists(assets, id);
    }

    private static ImportedStates validateModelStates(String key, Path assets, ImportReport report,
                                                       ImportedStates states) {
        List<String> pulling = states.pulling();
        if (!pulling.isEmpty() && pulling.stream().anyMatch(model -> !modelAvailable(assets, model))) {
            report.note(key + ": bow/crossbow state models are incomplete; pulling states were omitted");
            pulling = List.of();
        }
        return new ImportedStates(pulling,
                availableState(key, "charged", states.charged(), assets, report),
                availableState(key, "firework", states.firework(), assets, report),
                availableState(key, "cast", states.cast(), assets, report),
                availableState(key, "blocking", states.blocking(), assets, report));
    }

    private static String availableState(String key, String state, String model, Path assets, ImportReport report) {
        if (model != null && !modelAvailable(assets, model)) {
            report.note(key + ": " + state + " state model not found and was omitted (" + model + ")");
            return null;
        }
        return model;
    }

    private static void appendModelStates(StringBuilder yaml, ImportedStates states, String defaultNamespace) {
        if (states.isEmpty()) return;
        yaml.append("    model-states:\n");
        if (!states.pulling().isEmpty()) {
            yaml.append("      pulling:\n");
            states.pulling().forEach(model -> yaml.append("        - ").append(yamlString(qualifyModel(defaultNamespace, model))).append('\n'));
        }
        appendModelState(yaml, "charged", states.charged(), defaultNamespace);
        appendModelState(yaml, "firework", states.firework(), defaultNamespace);
        appendModelState(yaml, "cast", states.cast(), defaultNamespace);
        appendModelState(yaml, "blocking", states.blocking(), defaultNamespace);
    }

    private static void appendModelState(StringBuilder yaml, String field, String model, String defaultNamespace) {
        if (model != null) yaml.append("      ").append(field).append(": ")
                .append(yamlString(qualifyModel(defaultNamespace, model))).append('\n');
    }

    private static String qualifyModel(String namespace, String model) {
        return model.indexOf(':') >= 0 ? model : namespace + ":" + model;
    }

    private static String parentPath(String path) {
        if (path == null) return "";
        int slash = path.lastIndexOf('/');
        return slash < 0 ? "" : path.substring(0, slash);
    }

    private static boolean isArmorMaterial(String material) {
        return List.of("LEATHER_HELMET", "LEATHER_CHESTPLATE", "LEATHER_LEGGINGS", "LEATHER_BOOTS")
                .contains(material);
    }

    private static String cleanItemsAdderPath(String value) {
        if (value == null || value.isBlank()) return null;
        int namespace = value.indexOf(':');
        String path = namespace >= 0 ? value.substring(namespace + 1) : value;
        path = path.replaceFirst("(?i)\\.png$", "");
        return safeResourcePath(path) ? path : null;
    }

    private static String withPngExtension(String path) {
        return path.toLowerCase(Locale.ROOT).endsWith(".png") ? path : path + ".png";
    }

    private static List<ImportedItem> parseOraxenItems(String yaml) {
        List<ImportedItem> result = new ArrayList<>();
        String key = null, base = null, name = null, model = null, texture = null;
        List<String> pulling = new ArrayList<>();
        String charged = null, firework = null, cast = null, blocking = null;
        boolean readingTextures = false;
        boolean readingPulling = false;
        for (String line : yaml.split("\\R")) {
            Matcher top = Pattern.compile("^([a-zA-Z0-9_-]+):\\s*$").matcher(line);
            if (top.matches()) {
                addImportedItem(result, key, base, name, model, texture,
                        new ImportedStates(pulling, charged, firework, cast, blocking));
                key = top.group(1).toLowerCase(Locale.ROOT);
                base = name = model = texture = null;
                pulling = new ArrayList<>();
                charged = firework = cast = blocking = null;
                readingTextures = false;
                readingPulling = false;
                continue;
            }
            if (key == null) continue;
            Matcher field = Pattern.compile("^ {2}(material|displayname):\\s*(.*?)\\s*$").matcher(line);
            if (field.matches()) {
                if (field.group(1).equals("material")) base = unquote(field.group(2)).toUpperCase(Locale.ROOT);
                else name = unquote(field.group(2));
                readingTextures = false;
                readingPulling = false;
                continue;
            }
            Matcher modelField = Pattern.compile("^ {4}model:\\s*(.*?)\\s*$").matcher(line);
            if (modelField.matches()) {
                model = unquote(modelField.group(1));
                readingTextures = false;
                readingPulling = false;
                continue;
            }
            if (line.matches("^ {4}pulling_models:\\s*$")) {
                readingPulling = true;
                readingTextures = false;
                continue;
            }
            Matcher stateModel = Pattern.compile("^ {4}(charged_model|firework_model|cast_model|blocking_model):\\s*(.*?)\\s*$")
                    .matcher(line);
            if (stateModel.matches()) {
                String value = unquote(stateModel.group(2));
                switch (stateModel.group(1)) {
                    case "charged_model" -> charged = value;
                    case "firework_model" -> firework = value;
                    case "cast_model" -> cast = value;
                    case "blocking_model" -> blocking = value;
                }
                readingPulling = readingTextures = false;
                continue;
            }
            Matcher pullingModel = Pattern.compile("^ {6}-\\s*(.*?)\\s*$").matcher(line);
            if (readingPulling && pullingModel.matches()) {
                String value = unquote(pullingModel.group(1));
                if (safeResourcePath(value)) pulling.add(value);
                continue;
            }
            if (line.matches("^ {4}textures:\\s*$")) {
                readingTextures = true;
                readingPulling = false;
                continue;
            }
            Matcher textureField = Pattern.compile("^ {6}-\\s*(.*?)\\s*$").matcher(line);
            if (readingTextures && texture == null && textureField.matches()) texture = unquote(textureField.group(1));
            if (line.matches("^ {4}[^\\s].*:\\s*.*$")) readingPulling = readingTextures = false;
        }
        addImportedItem(result, key, base, name, model, texture,
                new ImportedStates(pulling, charged, firework, cast, blocking));
        return result;
    }

    private static void addImportedItem(List<ImportedItem> items, String key, String base, String name,
                                        String model, String texture, ImportedStates states) {
        if (key == null || base == null || !base.matches("[A-Z0-9_]+")) return;
        if (model != null && !safeResourcePath(model)) model = null;
        if (texture != null && !safeResourcePath(texture)) texture = null;
        if (model != null || texture != null) items.add(new ImportedItem(key, base,
                name == null || name.isBlank() ? key : name, model, texture, states));
    }

    private static boolean safeResourcePath(String value) {
        Path path = Path.of(value.replace('/', java.io.File.separatorChar)).normalize();
        String normalized = path.toString().replace('\\', '/');
        return !path.isAbsolute() && !path.startsWith("..") && normalized.equals(value)
                && value.matches("[a-zA-Z0-9_./-]+")
                && !value.contains("//");
    }

    private static String safeFileName(String value) {
        return value.replaceFirst("(?i)\\.yml$", "").replaceAll("[^a-zA-Z0-9_-]", "_") + ".yml";
    }

    private static String safeFileStem(String value) {
        return value.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    private static String unquote(String value) {
        String text = value.trim();
        if (text.length() >= 2 && ((text.startsWith("\"") && text.endsWith("\""))
                || (text.startsWith("'") && text.endsWith("'")))) return text.substring(1, text.length() - 1);
        return text;
    }

    private static String yamlString(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    private static int copyOraxenAssets(ZipFile zip, String prefix, Path output, String... suffixes) throws IOException {
        int copied = 0;
        for (ZipEntry entry : zip.stream().sorted(Comparator.comparing(ZipEntry::getName)).toList()) {
            String entryName = entry.getName().replace('\\', '/');
            if (entry.isDirectory() || !entryName.startsWith(prefix)) continue;
            String relative = entryName.substring(prefix.length());
            String lower = relative.toLowerCase(Locale.ROOT);
            if (relative.isBlank() || Arrays.stream(suffixes).noneMatch(lower::endsWith) || !safeResourcePath(relative)) continue;
            Path destination = output.resolve(relative.replace('/', java.io.File.separatorChar)).normalize();
            if (!destination.startsWith(output.normalize())) continue;
            if (copyZipEntry(zip, entry, destination)) copied++;
        }
        return copied;
    }

    private static boolean copyZipEntry(ZipFile zip, ZipEntry entry, Path destination) throws IOException {
        if (Files.exists(destination)) return false;
        Files.createDirectories(destination.getParent());
        try (InputStream in = zip.getInputStream(entry)) {
            Files.copy(in, destination);
        }
        return true;
    }

    private record ImportedItem(String key, String base, String name, String model, String texture, ImportedStates states) {}
    private record ItemsAdderItem(String key, String base, String name, String model, String texture,
                                  String armorSlot, String armorModel, int textureCount, ImportedStates states) {}
    private record ItemsAdderData(String namespace, List<ItemsAdderItem> items, List<ArmorRendering> armors) {}
    private record ArmorRendering(String key, String layer1, String layer2) {}

    private static final class ImportReport {
        private static final Map<String, String> UNTRANSLATED_KEYS = Map.ofEntries(
                Map.entry("mechanics", "Oraxen mechanics"),
                Map.entry("components", "Item components and attributes"),
                Map.entry("behaviours", "ItemsAdder behaviors"),
                Map.entry("events", "Gameplay event actions"),
                Map.entry("lore", "Item lore"),
                Map.entry("permission", "Per-item permissions"),
                Map.entry("blocks", "Custom block definitions"),
                Map.entry("entities", "Custom entity definitions"),
                Map.entry("huds", "HUD definitions"),
                Map.entry("font_images", "Font images and glyphs"),
                Map.entry("recipes", "Custom recipes"),
                Map.entry("furniture", "Furniture mechanics"),
                Map.entry("vehicle", "Vehicle mechanics"),
                Map.entry("vehicles", "Vehicle mechanics"),
                Map.entry("icon", "Separate inventory icon graphics"),
                Map.entry("parent", "Custom model parent settings"),
                Map.entry("oversized_in_gui", "Oversized GUI item rendering"),
                Map.entry("hand_animation_on_swap", "Hand animation settings"),
                Map.entry("item_model", "Manually assigned item model identifiers"),
                Map.entry("model_id", "Legacy custom model data identifiers"),
                Map.entry("variant_of", "Template and variant inheritance"),
                Map.entry("template", "Template and variant inheritance"),
                Map.entry("item_flags", "Item tooltip flags"),
                Map.entry("durability", "Custom item durability")
        );

        private final String format;
        private final Path archive;
        private final List<String> imported = new ArrayList<>();
        private final List<String> skipped = new ArrayList<>();
        private final List<String> notes = new ArrayList<>();
        private final Map<String, Set<String>> untranslated = new LinkedHashMap<>();
        private int assets;

        private ImportReport(String format, Path archive) {
            this.format = format;
            this.archive = archive;
        }

        private void imported(String key) { imported.add(key); }
        private void skip(String reason) { skipped.add(reason); }
        private void note(String message) { notes.add(message); }

        private void scan(String source, String yaml) {
            String lowerSource = source.toLowerCase(Locale.ROOT);
            if (lowerSource.contains("/glyphs/") || lowerSource.endsWith("/glyphs.yml"))
                detect("Font images and glyphs", source);
            if (lowerSource.contains("/huds/") || lowerSource.endsWith("/huds.yml"))
                detect("HUD definitions", source);
            for (String line : yaml.split("\\R")) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                int colon = trimmed.indexOf(':');
                if (colon < 1) continue;
                String key = trimmed.substring(0, colon).trim().toLowerCase(Locale.ROOT);
                String feature = UNTRANSLATED_KEYS.get(key);
                if (feature != null) detect(feature, source);
            }
        }

        private void detect(String feature, String source) {
            untranslated.computeIfAbsent(feature, ignored -> new LinkedHashSet<>()).add(source);
        }

        private void finish(Path reportDirectory) throws IOException {
            Files.createDirectories(reportDirectory);
            String stem = archive.getFileName().toString().replaceFirst("(?i)\\.zip$", "");
            Path report = reportDirectory.resolve(safeFileStem(stem) + "-" + format.toLowerCase(Locale.ROOT) + "-import-report.txt");
            StringBuilder text = new StringBuilder()
                    .append(format).append(" import report\n")
                    .append("Archive: ").append(archive).append('\n')
                    .append("Imported items: ").append(imported.size()).append('\n')
                    .append("Copied model/texture/equipment assets: ").append(assets).append(" files\n\n")
                    .append("Translated fields: item key, base material, display name, supported model/texture,")
                    .append(" recognized bow/crossbow/shield/fishing-rod model states, and recognized armor assets.\n")
                    .append("This importer is partial: unsupported source configuration is not applied automatically.\n\n")
                    .append("Detected but not translated:\n");
            if (untranslated.isEmpty()) text.append("- No known unsupported keys detected in scanned item configuration files.\n");
            untranslated.forEach((feature, sources) -> text.append("- ").append(feature).append(" (in ")
                    .append(String.join(", ", sources)).append(")\n"));
            text.append("\nSkipped entries:\n");
            if (skipped.isEmpty()) text.append("- None\n");
            else skipped.forEach(reason -> text.append("- ").append(reason).append('\n'));
            text.append("\nPartial conversions and notes:\n");
            if (notes.isEmpty()) text.append("- None\n");
            else notes.forEach(message -> text.append("- ").append(message).append('\n'));
            text.append("\nImported keys:\n");
            if (imported.isEmpty()) text.append("- None\n");
            else imported.forEach(key -> text.append("- ").append(key).append('\n'));
            Files.writeString(report, text, StandardCharsets.UTF_8);
            System.out.printf("Imported %d items and %d model/texture/equipment files to %s%n", imported.size(), assets,
                    report.getParent().getParent());
            System.out.println("Import report: " + report);
            if (imported.isEmpty()) throw new IOException("no supported static items were imported; see " + report);
            System.out.println("Run /ci reload in-game; imported item keys are available with /ci give <key>.");
        }
    }

    private record ImportedStates(List<String> pulling, String charged, String firework, String cast, String blocking) {
        private static final ImportedStates EMPTY = new ImportedStates(List.of(), null, null, null, null);

        private ImportedStates {
            pulling = List.copyOf(pulling);
        }

        private boolean isEmpty() {
            return pulling.isEmpty() && charged == null && firework == null && cast == null && blocking == null;
        }
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
