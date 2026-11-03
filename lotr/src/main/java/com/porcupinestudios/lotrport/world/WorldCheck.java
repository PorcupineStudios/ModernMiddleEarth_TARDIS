package com.porcupinestudios.lotrport.world;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.world.map.MapBiomeSource;
import com.porcupinestudios.lotrport.world.map.MiddleEarthMap;
import com.porcupinestudios.lotrport.world.map.Roads;
import com.porcupinestudios.ringbearer.api.provider.Providers;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * CI check for a new Middle-earth world: started with {@code -Dlotr_port.worldcheck=true} on a server whose
 * {@code level-type} is {@code lotr_port:middle_earth}. Checks the spawn and the TARDIS crash site are in Middle-earth,
 * generates a chunk in every biome on the map and on a road, prints WORLDCHECK PASSED or WORLDCHECK FAILED, and stops
 * the server.
 */
final class WorldCheck {
    private WorldCheck() {}

    static void runIfAsked(MinecraftServer server) {
        if (!Boolean.getBoolean("lotr_port.worldcheck")) {
            return;
        }
        String problem = null;
        try {
            ServerLevel overworld = server.overworld();
            BlockPos spawn = overworld.getSharedSpawnPos();
            String biome = overworld.getBiome(spawn).unwrapKey().map(k -> k.location().toString()).orElse("?");
            LotrPort.LOG.info("[worldcheck] overworld is Middle-earth: {}, startsInMiddleEarth: {}, spawn {} in {}",
                    LotrDimensions.overworldIsMiddleEarth(server), LotrWorldData.get(server).startsInMiddleEarth(),
                    spawn.toShortString(), biome);
            if (!LotrDimensions.overworldIsMiddleEarth(server)) {
                problem = "the overworld is not generated from the Middle-earth map";
            } else if (!LotrWorldData.get(server).startsInMiddleEarth()) {
                problem = "the world was not marked as starting in Middle-earth";
            } else if (biome.contains("ocean")) {
                problem = "the world spawns in the ocean";
            } else if (spawn.getY() < overworld.getSeaLevel()) {
                problem = "the spawn is below sea level at y " + spawn.getY();
            } else if (Providers.startDimension(server) != Level.OVERWORLD) {
                problem = "the TARDIS would crash in " + Providers.startDimension(server).location()
                        + ", not at the Middle-earth spawn";
            }
            if (problem == null) {
                problem = everyBiomeGenerates(overworld);
            }
            if (problem == null) {
                problem = roadsAreLaid(overworld);
            }
        } catch (RuntimeException e) {
            LotrPort.LOG.error("[worldcheck] crashed", e);
            problem = e.toString();
        }
        LotrPort.LOG.info(problem == null ? "WORLDCHECK PASSED" : "WORLDCHECK FAILED: " + problem);
        server.halt(false);
    }

    /** Generates one chunk inside every biome on the map and checks the biome is there. */
    private static String everyBiomeGenerates(ServerLevel level) {
        MapBiomeSource source = (MapBiomeSource) level.getChunkSource().getGenerator().getBiomeSource();
        MiddleEarthMap map = source.map();
        int[][] samples = map.samplePositions();
        List<String> missing = new ArrayList<>();
        int generated = 0;
        long start = System.nanoTime();
        for (int i = 0; i < samples.length; i++) {
            String id = map.biomes().get(i).id();
            if (samples[i] == null) {
                LotrPort.LOG.info("[worldcheck] {} is not on the map", id);
                continue;
            }
            int x = samples[i][0];
            int z = samples[i][1];
            level.getChunk(x >> 4, z >> 4);
            generated++;
            boolean found = false;
            for (int dx = 0; dx < 16 && !found; dx += 4) {
                for (int dz = 0; dz < 16 && !found; dz += 4) {
                    BlockPos pos = LotrDimensions.surface(level, (x & ~15) + dx, (z & ~15) + dz);
                    found = level.getBiome(pos).unwrapKey().map(k -> k.location().toString().equals(id))
                            .orElse(false);
                }
            }
            if (!found) {
                missing.add(id + " at " + x + "," + z);
            }
        }
        LotrPort.LOG.info("[worldcheck] generated {} biome chunks in {} s, missing: {}", generated,
                (System.nanoTime() - start) / 1_000_000_000L, missing);
        return missing.isEmpty() ? null : "biomes not found where the map has them: " + missing;
    }

    /** Generates chunks on the first few roads and checks road blocks were laid. */
    private static String roadsAreLaid(ServerLevel level) {
        Roads roads = Roads.get();
        LotrPort.LOG.info("[worldcheck] {} road segments", roads.segmentCount());
        int tried = 0;
        for (Roads.Segment s : roads.segments()) {
            if (tried++ >= 40) {
                break;
            }
            int x = (int) Math.floor((s.x1() + s.x2()) / 2);
            int z = (int) Math.floor((s.z1() + s.z2()) / 2);
            level.getChunk(x >> 4, z >> 4);
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos top = LotrDimensions.surface(level, x + dx, z + dz).below();
                    BlockState state = level.getBlockState(top);
                    if (state.is(Blocks.DIRT_PATH) || state.is(Blocks.GRAVEL) || state.is(Blocks.SMOOTH_SANDSTONE)) {
                        LotrPort.LOG.info("[worldcheck] road found at {}", top.toShortString());
                        return null;
                    }
                }
            }
        }
        return "no road blocks found on " + tried + " road segments";
    }
}
