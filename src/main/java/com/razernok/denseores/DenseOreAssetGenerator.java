package com.razernok.denseores;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hypixel.hytale.common.plugin.AuthorInfo;
import com.hypixel.hytale.common.plugin.PluginManifest;
import com.hypixel.hytale.common.semver.Semver;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class DenseOreAssetGenerator {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String ITEM_FILE_PREFIX = "Server/Item/Items/";
    private static final String PACK_GROUP = "razernok";
    private static final String PACK_NAME = "TestingModDenseOres";
    private static final String DENSE_PREFIX = "Dense_";
    private static final String DENSE_ORE_MODEL = "Resources/Ores/Ore_Dense.blockymodel";
    private static final String LARGE_ORE_MODEL = "Resources/Ores/Ore_Large.blockymodel";

    private DenseOreAssetGenerator() {
    }

    public static GenerationResult generateDenseOrePack(Path assetsZip, Path modsDir, Path outputDir) throws IOException {
        recreateDirectory(outputDir);

        final Map<String, JsonObject> sourceItems = new LinkedHashMap<>();
        final Set<String> moddedSourceIds = new HashSet<>();
        collectOreItemsFromZip(assetsZip, sourceItems, null);
        collectOreItemsFromModsDirectory(modsDir, sourceItems, moddedSourceIds);

        final List<String> generatedIds = new ArrayList<>();
        final List<String> moddedGeneratedIds = new ArrayList<>();
        final Map<String, String> languageEntries = new LinkedHashMap<>();
        for (Map.Entry<String, JsonObject> entry : sourceItems.entrySet()) {
            final String sourceId = entry.getKey();
            final String denseId = DENSE_PREFIX + sourceId;
            final JsonObject itemJson = entry.getValue().deepCopy();
            transformItemJson(itemJson, denseId);
            writeDenseOreJson(outputDir, sourceId, denseId, itemJson);
            generatedIds.add(denseId);
            if (moddedSourceIds.contains(sourceId)) {
                moddedGeneratedIds.add(denseId);
            }
            languageEntries.put("items." + denseId + ".name", toLanguageDisplayName(denseId));
        }

        generatedIds.sort(Comparator.naturalOrder());
        moddedGeneratedIds.sort(Comparator.naturalOrder());
        writeLanguageFile(outputDir, languageEntries);
        final PluginManifest manifest = createManifest();
        writeManifest(outputDir, manifest);
        return new GenerationResult(outputDir, manifest, generatedIds, moddedGeneratedIds);
    }

    private static void collectOreItemsFromModsDirectory(Path modsDir, Map<String, JsonObject> sourceItems, Set<String> moddedSourceIds)
            throws IOException {
        if (!Files.isDirectory(modsDir)) {
            return;
        }

        try (Stream<Path> entries = Files.list(modsDir)) {
            for (Path entry : entries.sorted().toList()) {
                final String fileName = entry.getFileName().toString();
                if (fileName.equals(PACK_NAME) || fileName.startsWith("razernok_TestingMod")) {
                    continue;
                }

                if (Files.isDirectory(entry)) {
                    if (Files.isRegularFile(entry.resolve("manifest.json"))) {
                        collectOreItemsFromDirectory(entry, sourceItems, moddedSourceIds);
                    }
                    continue;
                }

                final String lowerName = fileName.toLowerCase(Locale.ROOT);
                if ((lowerName.endsWith(".zip") || lowerName.endsWith(".jar")) && hasManifest(entry)) {
                    collectOreItemsFromZip(entry, sourceItems, moddedSourceIds);
                }
            }
        }
    }

    private static boolean hasManifest(Path zipPath) throws IOException {
        try (ZipFile zipFile = new ZipFile(zipPath.toFile(), StandardCharsets.UTF_8)) {
            return zipFile.getEntry("manifest.json") != null;
        }
    }

    private static void collectOreItemsFromZip(Path zipPath, Map<String, JsonObject> sourceItems, Set<String> moddedSourceIds)
            throws IOException {
        try (ZipFile zipFile = new ZipFile(zipPath.toFile(), StandardCharsets.UTF_8)) {
            final Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                final ZipEntry entry = entries.nextElement();
                final String name = entry.getName();
                if (!isDenseOreSource(name)) {
                    continue;
                }

                final String sourceId = idFromPath(name);
                sourceItems.put(sourceId, readJson(zipFile, entry));
                if (moddedSourceIds != null) {
                    moddedSourceIds.add(sourceId);
                }
            }
        }
    }

    private static void collectOreItemsFromDirectory(Path rootDir, Map<String, JsonObject> sourceItems, Set<String> moddedSourceIds)
            throws IOException {
        final Path serverDir = rootDir.resolve("Server");
        if (!Files.isDirectory(serverDir)) {
            return;
        }

        try (Stream<Path> walk = Files.walk(serverDir)) {
            for (Path path : walk.filter(Files::isRegularFile).sorted().toList()) {
                final String relative = rootDir.relativize(path).toString().replace('\\', '/');
                if (!isDenseOreSource(relative)) {
                    continue;
                }

                try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    final String sourceId = idFromPath(relative);
                    sourceItems.put(sourceId, GSON.fromJson(reader, JsonObject.class));
                    moddedSourceIds.add(sourceId);
                }
            }
        }
    }

    private static boolean isDenseOreSource(String zipPath) {
        if (!zipPath.startsWith(ITEM_FILE_PREFIX) || !zipPath.endsWith(".json")) {
            return false;
        }

        final String id = idFromPath(zipPath);
        if (!id.startsWith("Ore_")) {
            return false;
        }

        final String[] parts = id.split("_");
        return parts.length == 3;
    }

    private static String idFromPath(String zipPath) {
        final int slash = zipPath.lastIndexOf('/');
        final int dot = zipPath.lastIndexOf('.');
        return zipPath.substring(slash + 1, dot);
    }

    private static JsonObject readJson(ZipFile zipFile, ZipEntry entry) throws IOException {
        try (Reader reader = new java.io.InputStreamReader(zipFile.getInputStream(entry), StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, JsonObject.class);
        }
    }

    private static void transformItemJson(JsonObject itemJson, String denseId) {
        final JsonObject translation = itemJson.has("TranslationProperties")
                ? itemJson.getAsJsonObject("TranslationProperties")
                : new JsonObject();
        translation.addProperty("Name", "server.items." + denseId + ".name");
        itemJson.add("TranslationProperties", translation);

        tripleOreDrop(itemJson);

        if (itemJson.has("BlockType") && itemJson.get("BlockType").isJsonObject()) {
            final JsonObject blockType = itemJson.getAsJsonObject("BlockType");
            if (blockType.has("CustomModel")) {
                final String customModel = blockType.get("CustomModel").getAsString();
                if (LARGE_ORE_MODEL.equals(customModel)) {
                    blockType.addProperty("CustomModel", DENSE_ORE_MODEL);
                }
            }
        }
    }

    private static void tripleOreDrop(JsonObject itemJson) {
        if (!itemJson.has("BlockType") || !itemJson.get("BlockType").isJsonObject()) {
            return;
        }

        final JsonObject blockType = itemJson.getAsJsonObject("BlockType");
        final JsonObject gathering = getObject(blockType, "Gathering");
        final JsonObject breaking = getObject(gathering, "Breaking");
        final JsonObject dropList = getObject(breaking, "DropList");
        final JsonObject container = getObject(dropList, "Container");

        if (container == null || !"Multiple".equals(getString(container, "Type"))) {
            return;
        }

        final JsonArray containers = container.has("Containers") && container.get("Containers").isJsonArray()
                ? container.getAsJsonArray("Containers")
                : null;
        if (containers == null || containers.isEmpty()) {
            return;
        }

        JsonObject oreDropItem = null;
        for (JsonElement element : containers) {
            if (!element.isJsonObject()) {
                continue;
            }

            final JsonObject candidate = element.getAsJsonObject();
            if (!"Single".equals(getString(candidate, "Type"))) {
                continue;
            }

            final JsonObject item = getObject(candidate, "Item");
            final String itemId = getString(item, "ItemId");
            if (itemId != null && itemId.startsWith("Ore_")) {
                oreDropItem = item;
                break;
            }
        }

        if (oreDropItem == null) {
            return;
        }

        oreDropItem.addProperty("QuantityMin", 3);
        oreDropItem.addProperty("QuantityMax", 3);
    }

    private static JsonObject getObject(JsonObject parent, String key) {
        if (parent == null || !parent.has(key) || !parent.get(key).isJsonObject()) {
            return null;
        }
        return parent.getAsJsonObject(key);
    }

    private static String getString(JsonObject parent, String key) {
        if (parent == null || !parent.has(key) || !parent.get(key).isJsonPrimitive()) {
            return null;
        }
        return parent.get(key).getAsString();
    }

    private static String toLanguageDisplayName(String denseId) {
        final String[] parts = denseId.split("_");
        if (parts.length < 4) {
            return denseId;
        }

        return "Dense " + parts[2] + " Ore - " + parts[3];
    }

    private static void writeManifest(Path outputDir, PluginManifest pluginManifest) throws IOException {
        final JsonObject manifest = new JsonObject();
        manifest.addProperty("Group", pluginManifest.getGroup());
        manifest.addProperty("Name", pluginManifest.getName());
        writeJson(outputDir.resolve("manifest.json"), manifest);
    }

    private static void writeLanguageFile(Path outputDir, Map<String, String> entries) throws IOException {
        final Path languageFile = outputDir
                .resolve("Server")
                .resolve("Languages")
                .resolve("en-US")
                .resolve("server.lang");

        Files.createDirectories(languageFile.getParent());
        try (Writer writer = Files.newBufferedWriter(languageFile, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                writer.write(entry.getKey());
                writer.write(" = ");
                writer.write(entry.getValue());
                writer.write(System.lineSeparator());
            }
        }
    }

    private static void writeDenseOreJson(Path outputDir, String sourceId, String denseId, JsonObject itemJson)
            throws IOException {
        final String[] parts = sourceId.split("_");
        final String oreFamily = parts[1];
        final Path itemPath = outputDir
                .resolve("Server")
                .resolve("Item")
                .resolve("Items")
                .resolve("Ore")
                .resolve(oreFamily)
                .resolve(denseId + ".json");
        writeJson(itemPath, itemJson);
    }

    private static void writeJson(Path outputPath, JsonElement jsonElement) throws IOException {
        Files.createDirectories(outputPath.getParent());
        try (Writer writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            GSON.toJson(jsonElement, writer);
        }
    }

    private static void recreateDirectory(Path directory) throws IOException {
        if (Files.exists(directory)) {
            try (var walk = Files.walk(directory)) {
                walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException exception) {
                        throw new RuntimeException(exception);
                    }
                });
            } catch (RuntimeException exception) {
                if (exception.getCause() instanceof IOException ioException) {
                    throw ioException;
                }
                throw exception;
            }
        }
        Files.createDirectories(directory);
    }

    private static PluginManifest createManifest() {
        final AuthorInfo authorInfo = new AuthorInfo();
        authorInfo.setName("Razernok");

        final PluginManifest pluginManifest = new PluginManifest();
        pluginManifest.setGroup(PACK_GROUP);
        pluginManifest.setName(PACK_NAME);
        pluginManifest.setVersion(new Semver(1, 0, 0));
        pluginManifest.setDescription("Generated dense ore asset pack");
        pluginManifest.setAuthors(List.of(authorInfo));
        pluginManifest.setServerVersion(BuildConstants.SERVER_VERSION);
        return pluginManifest;
    }

    public record GenerationResult(
            Path outputDir,
            PluginManifest manifest,
            List<String> generatedIds,
            List<String> moddedGeneratedIds
    ) {
    }

}
