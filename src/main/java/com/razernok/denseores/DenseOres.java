package com.razernok.denseores;
import com.razernok.denseores.Configs.DenseOresConfig;

import com.hypixel.hytale.event.EventPriority;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.events.ChunkPreLoadProcessEvent;

import javax.annotation.Nonnull;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Level;

/**
 * Dense Ores - A Hytale server plugin.
 *
 * @author Razernok
 * @version 1.0.1
 */
public class DenseOres extends JavaPlugin {
    //Config
    DenseOresConfig config = DenseOresConfig.getInstance();

    //Setup Variables
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static DenseOres instance;

    List<String> replaceableList = new ArrayList<>();
    private Set<Integer> replaceableBlockIds = null;
    private Set<BlockType> oreType = null;
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
        LOGGER.at(Level.INFO).log(" Setting up...");

        // Register chunk generation event for natural ore spawning
        // Use LATE priority so terrain is fully generated before we add ores
        this.getEventRegistry().registerGlobal(
                EventPriority.LATE,
                ChunkPreLoadProcessEvent.class,
                this::onChunkGenerated
        );

        Path serverRoot = Paths.get(".").toAbsolutePath().normalize();
        DenseOresConfig.getInstance().initialize(serverRoot);

        // Loop through every block
        for (DenseOresConfig.Block block : DenseOresConfig.getInstance().blocks.values()) {

            // Loop through each override
            for (String override : block.Overrides) {
                // Combine block name + override with underscore
                replaceableList.add(block.name + "_" + override);
            }
        }
        // Convert to array if needed
        replaceableBlockNames = replaceableList.toArray(new String[0]);

        for (String s : replaceableBlockNames) {
            LOGGER.at(Level.INFO).log(" Overrides: " + s);
        }
        LOGGER.at(Level.INFO).log(" Setup complete!");
    }

    private void onChunkGenerated(@Nonnull ChunkPreLoadProcessEvent event){
        if (!event.isNewlyGenerated()) return;

        WorldChunk chunk = event.getChunk();
        if (chunk == null) {
            return;
        }

        // Create a seeded random for this chunk so generation is deterministic
        long chunkSeed = ((long) chunk.getX() * 341873128712L) + ((long) chunk.getZ() * 132897987541L);
        Random seedRand = new Random(chunkSeed);

        // Get chunk coordinates (block coordinates of chunk corner)
        int chunkX = chunk.getX() << 5;  // Multiply by 32 (chunk size)
        int chunkZ = chunk.getZ() << 5;
        int maxX = (chunk.getX() << 5) + 32;
        int maxZ = (chunk.getZ() << 5) + 32;

        World world = chunk.getWorld();
        world.execute(() -> {
            for (int x = chunkX; x < maxX; x++) {
                for (int z = chunkZ; z < maxZ; z++) {
                    for (int y = minY; y < maxY; y++) {
                        int id = chunk.getBlock(x,y,z);
                        String name = getOreName(id);
                        if (name.startsWith("Ore_")){
                            double roll = seedRand.nextDouble();
                            double spawnChance = config.getSpawnChancePercent(config.getBaseBlockName(name));
                            if (roll < spawnChance) {
                                chunk.setBlock(x, y, z, BlockType.getAssetMap().getIndex("Dense_"+name), BlockType.getAssetMap().getAsset("Dense_"+name), 0, 0, 4);
                            }
                        }
                    }
                }
            }
        });

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