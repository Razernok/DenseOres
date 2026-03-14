package com.razernok.denseores.Configs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DenseOresConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int DEFAULT_SPAWN_CHANCE = 50;
    private static DenseOresConfig INSTANCE;

    private transient Path configPath;

    // ---------- CONFIG VALUES ----------
    public int minY = 0;
    public int maxY = 149;
    public List<OreEntry> ores = new ArrayList<>();

    public static final class OreEntry {
        public String name;
        @SerializedName("spawn_chance")
        public int spawnChance = DEFAULT_SPAWN_CHANCE;
    }

    // ---------- SINGLETON ----------
    public static synchronized DenseOresConfig getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new DenseOresConfig();
        }
        return INSTANCE;
    }

    private DenseOresConfig() {}

    public DenseOresConfig(Path configPath) {
        this.configPath = configPath;
    }

    public static DenseOresConfig load(Path configPath) throws IOException {
        if (!Files.exists(configPath)) {
            return new DenseOresConfig(configPath);
        }

        try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            final DenseOresConfig config = GSON.fromJson(reader, DenseOresConfig.class);
            if (config == null) {
                return new DenseOresConfig(configPath);
            }

            config.configPath = configPath;
            config.ensureDefaults();
            return config;
        } catch (JsonParseException exception) {
            return new DenseOresConfig(configPath);
        }
    }

    public int updateDenseOreConfig(List<String> denseOreIds) throws IOException {
        ensureDefaults();

        final Map<String, OreEntry> entriesByName = new LinkedHashMap<>();
        for (OreEntry entry : ores) {
            if (entry == null || entry.name == null || entry.name.isBlank()) {
                continue;
            }
            final String normalizedName = normalizeOreName(entry.name);
            entry.name = normalizedName;
            entriesByName.put(normalizedName, entry);
        }

        for (String denseOreId : denseOreIds) {
            final String oreName = getOreName(denseOreId);
            entriesByName.computeIfAbsent(oreName, DenseOresConfig::createOreEntry);
        }

        ores = entriesByName.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                .map(Map.Entry::getValue)
                .toList();

        save();
        return ores.size();
    }

    public Path getConfigPath() {
        return configPath;
    }

    public void save() throws IOException {
        ensureDefaults();
        Files.createDirectories(configPath.getParent());
        try (Writer writer = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8)) {
            GSON.toJson(this, writer);
        }
    }

    public double getSpawnChancePercent(String oreName) {
        for (OreEntry entry : ores) {
            if (oreName.equals(entry.name)) {
                return entry.spawnChance / 100.0;
            }
        }
        return 0.0;
    }

    public static String getBaseBlockName(String blockName) {
        return normalizeOreName(blockName);
    }

    private void ensureDefaults() {
        if (ores == null) {
            ores = new ArrayList<>();
        }
    }

    private static OreEntry createOreEntry(String oreName) {
        final OreEntry entry = new OreEntry();
        entry.name = oreName;
        entry.spawnChance = DEFAULT_SPAWN_CHANCE;
        return entry;
    }

    private static String normalizeOreName(String blockName) {
        if (blockName == null || blockName.isBlank()) {
            return blockName;
        }

        final String[] parts = blockName.split("_");
        if (parts.length == 3 && "Ore".equals(parts[0])) {
            return parts[1];
        }
        return blockName;
    }

    private static String getOreName(String denseOreId) {
        if (denseOreId == null || denseOreId.isBlank()) {
            return denseOreId;
        }

        final String[] parts = denseOreId.split("_");
        if (parts.length == 4 && "Dense".equals(parts[0]) && "Ore".equals(parts[1])) {
            return parts[2];
        }
        return denseOreId;
    }
}
