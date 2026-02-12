package com.razernok.denseores.Configs;

import com.google.gson.*;
import com.hypixel.hytale.logger.HytaleLogger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Level;


public class DenseOresConfig {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static DenseOresConfig INSTANCE;

    // ---------- CONFIG VALUES ----------
    public int minY = 0;
    public int maxY = 319;

    /** block01 -> Block */
    public Map<String, Block> blocks = new HashMap<>();

    // ---------- PATHS ----------
    private transient Path configPath;
    private transient Path configDir;
    private transient boolean firstLaunch = false;

    // ---------- DATA TYPES ----------
    public static class Block {
        public String name;
        public int spawn_chance;
        public List<String> Overrides = new ArrayList<>();
    }

    // ---------- SINGLETON ----------
    public static synchronized DenseOresConfig getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new DenseOresConfig();
        }
        return INSTANCE;
    }

    private DenseOresConfig() {}

    // ---------- INIT ----------
    public void initialize(Path rootPath) {
        this.configDir = rootPath.resolve("mods").resolve("DenseOres");
        this.configPath = this.configDir.resolve("config.json");

        try {
            Files.createDirectories(configDir);

            if (Files.exists(configPath)) {
                load();
            } else {
                generateDefaultConfig();
                load();
                firstLaunch = true;
            }

        } catch (IOException e) {
            LOGGER.at(Level.SEVERE).log("Failed to initialize DenseOres config: " + e.getMessage());
        }
    }

    // ---------- LOAD ----------
    public void load() {
        try (Reader reader = Files.newBufferedReader(configPath)) {
            DenseOresConfig loaded = GSON.fromJson(reader, DenseOresConfig.class);

            this.minY = loaded.minY;
            this.maxY = loaded.maxY;

            this.blocks = loaded.blocks != null ? loaded.blocks : new HashMap<>();

            LOGGER.at(Level.INFO).log("DenseOres config loaded (" + blocks.size() + " blocks)");

        } catch (IOException e) {
            LOGGER.at(Level.SEVERE).log("Failed to load DenseOres config: " + e.getMessage());
        }

        for (Map.Entry<String, Block> entry : blocks.entrySet()) {

            String blockId = entry.getKey();
            Block block = entry.getValue();

            LOGGER.at(Level.INFO).log(
                    "Loaded DenseOre block:\n" +
                            "  ID: " + blockId + "\n" +
                            "  Name: " + block.name + "\n" +
                            "  Spawn Chance: " + block.spawn_chance + "\n" +
                            "  Overrides: " + block.Overrides
            );
        }
    }

    // ---------- SAVE ----------
    public void save() {
        try (Writer writer = Files.newBufferedWriter(configPath)) {
            GSON.toJson(this, writer);
            LOGGER.at(Level.INFO).log("DenseOres config saved");
        } catch (IOException e) {
            LOGGER.at(Level.SEVERE).log("Failed to save DenseOres config: " + e.getMessage());
        }
    }

    // ---------- DEFAULT CONFIG ----------
    private void generateDefaultConfig() throws IOException {

        blocks.clear();

        Block block01 = new Block();
        block01.name = "Ore_Adamantite";
        block01.spawn_chance = 30;
        block01.Overrides = List.of("Magma", "Magma_Cracked");

        Block block02 = new Block();
        block02.name = "Ore_Cobalt";
        block02.spawn_chance = 30;
        block02.Overrides = List.of("Shale", "Slate", "Slate_Cracked");

        Block block03 = new Block();
        block03.name = "Ore_Copper";
        block03.spawn_chance = 30;
        block03.Overrides = List.of("Sandstone", "Shale", "Stone");

        Block block04 = new Block();
        block04.name = "Ore_Gold";
        block04.spawn_chance = 30;
        block04.Overrides = List.of("Basalt", "Calcite", "Sandstone", "Shale", "Stone", "Volcanic");

        Block block05 = new Block();
        block05.name = "Ore_Iron";
        block05.spawn_chance = 30;
        block05.Overrides = List.of("Basalt", "Basalt_Cracked", "Sandstone", "Shale", "Slate", "Stone", "Volcanic");

        Block block06 = new Block();
        block06.name = "Ore_Mithril";
        block06.spawn_chance = 30;
        block06.Overrides = List.of("Stone");

        Block block07 = new Block();
        block07.name = "Ore_Silver";
        block07.spawn_chance = 30;
        block07.Overrides = List.of("Basalt", "Sandstone", "Shale", "Slate", "Stone", "Volcanic");

        Block block08 = new Block();
        block08.name = "Ore_Thorium";
        block08.spawn_chance = 30;
        block08.Overrides = List.of("Mud", "Mud_Cracked", "Sandstone");

        blocks.put("block01", block01);
        blocks.put("block02", block02);
        blocks.put("block03", block03);
        blocks.put("block04", block04);
        blocks.put("block05", block05);
        blocks.put("block06", block06);
        blocks.put("block07", block07);
        blocks.put("block08", block08);

        save();

        LOGGER.at(Level.INFO).log("Generated default DenseOres config");
    }

    public Double getSpawnChancePercent(String blockName) {
        for (DenseOresConfig.Block block : blocks.values()) {
            if (block.name.equals(blockName)) {
                return block.spawn_chance/100.0;
            }
        }
        // Return null if no block found
        return 0.0;
    }

    /**
     * Gets the base name of a block by keeping only the first two parts.
     * Everything after the second underscore is dropped.
     */
    public static String getBaseBlockName(String blockName) {
        if (blockName == null || blockName.isEmpty()) return blockName;

        String[] parts = blockName.split("_");

        if (parts.length >= 2) {
            // Keep only first two parts
            return parts[0] + "_" + parts[1];
        } else {
            // Only one part exists
            return parts[0];
        }
    }

    // ---------- HELPERS ----------
    public boolean isFirstLaunch() {
        return firstLaunch;
    }
}
