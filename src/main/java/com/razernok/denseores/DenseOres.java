package com.razernok.denseores;
import com.hypixel.hytale.server.core.asset.AssetModule;
import com.razernok.denseores.Configs.DenseOresConfig;

import com.hypixel.hytale.event.EventPriority;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.events.ChunkPreLoadProcessEvent;
import com.razernok.denseores.analytics.HStats;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Level;

/**
 * Dense Ores - A Hytale server plugin.
 *
 * @author Razernok
 * @version 1.0.1
 */
public class DenseOres extends JavaPlugin {
    // Is mod in Debug mode - For Internal testing only
    boolean isDebug =
            ManagementFactory.getRuntimeMXBean()
                    .getInputArguments()
                    .toString()
                    .contains("-agentlib:jdwp");

    //Config
    DenseOresConfig config = DenseOresConfig.getInstance();

    private boolean debug = isDebug;

    //Setup Variables
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String DENSE_ORE_PACK_NAME = "razernok:TestingModDenseOres";
    private static final String MOD_DIRECTORY_NAME = "DenseOres";
    private static DenseOres instance;

    private Set<Integer> replaceableBlockIds = null;
    private Set<String> moddedOreNames = Collections.emptySet();
    private int minY = config.minY;
    private int maxY = config.maxY;
    String[] replaceableBlockNames;

    public DenseOres(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
        LOGGER.atInfo().log("Chunk Code Testing plugin loaded - version " + this.getManifest().getVersion().toString());
    }

    public static DenseOres getInstance() {
        return instance;
    }

    @Override
    protected void setup() {
        //Setup HStats
        super.setup();
        new HStats("6f65c056-c69f-4c25-89a9-2062ef86f4bb", this.getManifest().getVersion().toString());

        LOGGER.at(Level.INFO).log(" Setting up...");

        // Register chunk generation event for natural ore spawning.
        // Use LAST so mods that also inject ores during pre-load run first.
        this.getEventRegistry().registerGlobal(
                EventPriority.LAST,
                ChunkPreLoadProcessEvent.class,
                this::onChunkGenerated
        );

        LOGGER.at(Level.INFO).log("[TestingMod] Generating dense ore asset pack...");

        final Path pluginJar = getFile().toAbsolutePath().normalize();
        final Path serverDir = pluginJar.getParent().getParent();
        final Path assetsZip = serverDir.resolve("Assets.zip");
        final Path modsDir = serverDir.resolve("mods");
        final Path modDataDirectory = modsDir.resolve(MOD_DIRECTORY_NAME);
        final Path outputDir = modDataDirectory.resolve("generated-dense-ores");
        final Path configPath = modDataDirectory.resolve("config.json");

        try {
            config = DenseOresConfig.load(configPath);
            final DenseOreAssetGenerator.GenerationResult result =
                    DenseOreAssetGenerator.generateDenseOrePack(assetsZip, modsDir, outputDir);

            final int configEntries = config.updateDenseOreConfig(result.generatedIds());
            minY = config.minY;
            maxY = config.maxY;
            replaceableBlockNames = result.generatedIds().stream()
                    .map(id -> id.startsWith("Dense_") ? id.substring("Dense_".length()) : id)
                    .distinct()
                    .toArray(String[]::new);
            moddedOreNames = new HashSet<>(result.moddedGeneratedIds().stream()
                    .map(id -> id.startsWith("Dense_") ? id.substring("Dense_".length()) : id)
                    .toList());

            if (AssetModule.get().getAssetPack(DENSE_ORE_PACK_NAME) != null) {
                AssetModule.get().unregisterPack(DENSE_ORE_PACK_NAME);
            }

            AssetModule.get().registerPack(
                    DENSE_ORE_PACK_NAME,
                    result.outputDir(),
                    result.manifest(),
                    true
            );

            LOGGER.at(Level.INFO).log(
                    "[TestingMod] Registered dense ore pack with %s generated assets",
                    result.generatedIds().size()
            );
            LOGGER.at(Level.INFO).log(
                    "[TestingMod] Updated %s with %s dense ore config entries",
                    config.getConfigPath(),
                    configEntries
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to generate dense ore asset pack", exception);
        }

        LOGGER.at(Level.INFO).log(" Setup complete!");
    }

    private void onChunkGenerated(@Nonnull ChunkPreLoadProcessEvent event){
        if (!event.isNewlyGenerated()) return;

        WorldChunk chunk = event.getChunk();
        if (chunk == null) {
            return;
        }

        long chunkSeed = ((long) chunk.getX() * 341873128712L) + ((long) chunk.getZ() * 132897987541L);

        // Get chunk coordinates (block coordinates of chunk corner)
        int chunkX = chunk.getX() << 5;  // Multiply by 32 (chunk size)
        int chunkZ = chunk.getZ() << 5;
        int maxX = (chunk.getX() << 5) + 32;
        int maxZ = (chunk.getZ() << 5) + 32;

        World world = chunk.getWorld();
        world.execute(() -> replaceDenseOres(chunk, chunkX, maxX, chunkZ, maxZ, chunkSeed));

    }

    private void replaceDenseOres(
            @Nonnull WorldChunk chunk,
            int chunkX,
            int maxX,
            int chunkZ,
            int maxZ,
            long chunkSeed
    ) {
        Random seedRand = new Random(chunkSeed);
        for (int x = chunkX; x < maxX; x++) {
            for (int z = chunkZ; z < maxZ; z++) {
                for (int y = minY; y < maxY; y++) {
                    int id = chunk.getBlock(x, y, z);
                    String name = getOreName(id);
                    if (name.startsWith("Ore_")) {
                        double roll = seedRand.nextDouble();
                        double spawnChance = config.getSpawnChancePercent(DenseOresConfig.getBaseBlockName(name));
                        if (roll < spawnChance) {
                            if (moddedOreNames.contains(name) && debug) {
                                LOGGER.at(Level.INFO).log(
                                        "Spawned dense modded ore Dense_%s at (%s, %s, %s) in chunk (%s, %s)",
                                        name,
                                        x,
                                        y,
                                        z,
                                        chunk.getX(),
                                        chunk.getZ()
                                );
                            }
                            chunk.setBlock(
                                    x,
                                    y,
                                    z,
                                    BlockType.getAssetMap().getIndex("Dense_" + name),
                                    BlockType.getAssetMap().getAsset("Dense_" + name),
                                    0,
                                    0,
                                    4
                            );
                        }
                    }
                }
            }
        }
    }

    private String getOreName(int id) {
        if (isReplaceableBlockId(id)){
            for (int i = 0; i < replaceableBlockNames.length; i++) {
                if (id == BlockType.getAssetMap().getIndex(replaceableBlockNames[i])){
                    return replaceableBlockNames[i];
                }
            }
        }
        return "n/a";
    }

    private boolean initializeBlockIds(){
        if (replaceableBlockNames == null || replaceableBlockNames.length == 0) {
            LOGGER.at(Level.WARNING).log(" No replaceable ore blocks were initialized.");
            replaceableBlockIds = Collections.emptySet();
            return false;
        }

        // Cache all replaceable block IDs
        replaceableBlockIds = new HashSet<>(replaceableBlockNames.length * 2);
        for (int i = 0; i < replaceableBlockNames.length; i++) {
            int id = BlockType.getAssetMap().getIndex(replaceableBlockNames[i]);
            replaceableBlockIds.add(id);
        }
        return true;
    }

    private boolean isReplaceableBlockId(int blockId) {
        if (replaceableBlockIds != null && replaceableBlockIds.contains(blockId)) {
            return true;
        }
        return false;
    }

    @Override
    protected void start() {
        if (!initializeBlockIds()) {
            return;
        }
        LOGGER.at(Level.INFO).log(" Started!");
    }

    @Override
    protected void shutdown() {
        LOGGER.at(Level.INFO).log(" Shutting down...");
        instance = null;
    }
}
