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
 * the JVM without saving (the check world is thrown away).
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
            LotrPort.LOG.info("[worldcheck] seed {}", overworld.getSeed());
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
            } else if (server.getLevel(LotrDimensions.VANILLA_OVERWORLD) == null) {
                problem = "no vanilla Overworld dimension (" + LotrDimensions.VANILLA_OVERWORLD.location() + ")";
            }
            if (problem == null) {
                problem = everyBiomeGenerates(overworld);
            }
            if (problem == null) {
                problem = roadsAreLaid(overworld);
            }
            if (problem == null) {
                problem = keptRoadsAreBare(overworld);
            }
            if (problem == null) {
                problem = enclosuresAreBuilt(overworld);
            }
            if (problem == null) {
                problem = spiderNestIsBuilt(overworld);
            }
            if (problem == null) {
                problem = TreeCheck.start(overworld);
            }
            if (problem == null) {
                problem = StructureCheck.start(server);
                if (problem == null) {
                    return; // finishes on a later tick, once structure NPCs are in the world
                }
            }
        } catch (RuntimeException e) {
            LotrPort.LOG.error("[worldcheck] crashed", e);
            problem = e.toString();
        }
        finish(problem);
    }

    /** Prints the result and stops the JVM without saving. */
    static void finish(String problem) {
        LotrPort.LOG.info(problem == null ? "WORLDCHECK PASSED" : "WORLDCHECK FAILED: " + problem);
        // The check world is thrown away. Saving ~200 far-apart chunks on a normal stop runs the CI server out of
        // memory, so flush the log and stop the JVM without saving.
        org.apache.logging.log4j.LogManager.shutdown();
        Runtime.getRuntime().halt(0);
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
            // the map pixel is 128 blocks wide; rivers and border wobble can cover its centre, so look for a
            // column inside it where the biome source itself answers this biome
            int[] at = columnWithBiome(map, i, samples[i][0], samples[i][1]);
            // a biome on few pixels can have its chosen one covered (2026-11-14: moving the rivers covered wold's):
            // it only has to be found in one of its pixels
            for (int[] c : at == null ? map.pixelCentres(i, 40) : List.<int[]>of()) {
                at = columnWithBiome(map, i, c[0], c[1]);
                if (at != null) {
                    break;
                }
            }
            if (at == null) {
                missing.add(id + " (rivers or borders cover its whole pixel at " + samples[i][0] + ","
                        + samples[i][1] + ")");
                continue;
            }
            int x = at[0];
            int z = at[1];
            var chunk = level.getChunk(x >> 4, z >> 4);
            generated++;
            BlockPos pos = LotrDimensions.surface(level, x, z);
            // the chunk's stored biome, not level.getBiome, which blurs biome borders by a few blocks
            // sea pixels are a sea biome by latitude (converter/waters.py): expect the one the biome source gives there
            String want = source.biomeAtBlock(x, z).unwrapKey().map(k -> k.location().toString())
                    .filter(b -> id.equals("lotr_port:ocean") && b.endsWith("_ocean")).orElse(id);
            boolean found = chunk.getNoiseBiome(x >> 2, pos.getY() >> 2, z >> 2).unwrapKey()
                    .map(k -> k.location().toString().equals(want)).orElse(false);
            if (!found) {
                missing.add(id + " at " + x + "," + z);
            }
        }
        LotrPort.LOG.info("[worldcheck] generated {} biome chunks in {} s, missing: {}", generated,
                (System.nanoTime() - start) / 1_000_000_000L, missing);
        return missing.isEmpty() ? null : "biomes not found where the map has them: " + missing;
    }

    /** A column within the sample's map pixel (searched outward from its centre) where the map gives the biome. */
    private static int[] columnWithBiome(MiddleEarthMap map, int biome, int cx, int cz) {
        int half = map.blocksPerPixel() / 2;
        for (int r = 0; r <= half; r += 8) {
            for (int dx = -r; dx <= r; dx += 8) {
                for (int dz = -r; dz <= r; dz += 8) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    int x = (cx + dx) & ~3; // biomes are stored per 4x4 column
                    int z = (cz + dz) & ~3;
                    if (map.biomeIndexAt(x, z) == biome) {
                        return new int[] { x, z };
                    }
                }
            }
        }
        return null;
    }

    /**
     * Builds the nearest enclosure of each kind to the spawn (the Shire's fenced orchards and corn fields) and checks
     * them: a fence, hedge, gate or post on 80% of the fence's dry columns, trees in orchards, corn on half the plot.
     */
    private static String enclosuresAreBuilt(ServerLevel level) {
        MiddleEarthMap map = ((MapBiomeSource) level.getChunkSource().getGenerator().getBiomeSource()).map();
        java.util.Map<String, com.porcupinestudios.lotrport.world.map.Enclosures.Enclosure> nearest =
                new java.util.LinkedHashMap<>();
        for (int r = 0; r <= 24 && nearest.size() < 3; r++) {
            for (int i = -r; i <= r; i++) {
                for (int k = -r; k <= r; k++) {
                    if (Math.max(Math.abs(i), Math.abs(k)) != r) {
                        continue;
                    }
                    var e = map.enclosureInCellOf(i * 128, k * 128);
                    if (e != null) {
                        nearest.putIfAbsent(e.kind().name(), e);
                    }
                }
            }
        }
        if (nearest.isEmpty()) {
            return "no enclosure within 3000 blocks of the Shire's middle";
        }
        for (var e : nearest.values()) {
            for (int cx = e.minX() >> 4; cx <= e.maxX() >> 4; cx++) {
                for (int cz = e.minZ() >> 4; cz <= e.maxZ() >> 4; cz++) {
                    level.getChunk(cx, cz);
                }
            }
            int fenceColumns = 0;
            int fenced = 0;
            int inside = 0;
            int corn = 0;
            int logs = 0;
            for (int x = e.minX(); x <= e.maxX(); x++) {
                for (int z = e.minZ(); z <= e.maxZ(); z++) {
                    BlockPos top = LotrDimensions.surface(level, x, z);
                    if (e.onFence(x, z)) {
                        BlockPos ground = top.below();
                        if (!level.getBlockState(ground).getFluidState().isEmpty()) {
                            continue;
                        }
                        fenceColumns++;
                        for (int dy = -3; dy <= 3; dy++) {
                            BlockState b = level.getBlockState(top.above(dy));
                            if (b.getBlock() instanceof net.minecraft.world.level.block.FenceBlock
                                    || b.getBlock() instanceof net.minecraft.world.level.block.FenceGateBlock
                                    || b.is(net.minecraft.tags.BlockTags.LEAVES) || b.is(Blocks.OAK_LOG)) {
                                fenced++;
                                break;
                            }
                        }
                        continue;
                    }
                    inside++;
                    for (int dy = -12; dy <= 2; dy++) {
                        BlockState b = level.getBlockState(top.above(dy));
                        String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(b.getBlock()).getPath();
                        if (id.equals("corn_stalks")) {
                            corn++;
                            break;
                        }
                        if (b.is(net.minecraft.tags.BlockTags.LOGS)) {
                            logs++;
                            break;
                        }
                    }
                }
            }
            LotrPort.LOG.info("[worldcheck] enclosure {} at {},{} ({}x{}, level {}): fence {}/{}, corn {}/{}, "
                    + "columns with logs {}", e.kind().name(), e.minX(), e.minZ(), e.sizeX(), e.sizeZ(), e.level(),
                    fenced, fenceColumns, corn, inside, logs);
            if (fenceColumns > 0 && fenced < fenceColumns * 0.8) {
                return "enclosure " + e.kind().name() + " at " + e.minX() + "," + e.minZ() + " is fenced on only "
                        + fenced + " of " + fenceColumns + " columns";
            }
            if (e.kind().name().contains("corn") && corn < inside / 2) {
                return "corn field at " + e.minX() + "," + e.minZ() + " has corn on " + corn + " of " + inside;
            }
            if (e.kind().name().contains("orchard") && logs == 0) {
                return "orchard at " + e.minX() + "," + e.minZ() + " has no trees";
            }
        }
        return null;
    }

    /**
     * Builds the spider nest nearest the Forest Gate (lairs are seeded: it is somewhere else in every world) and checks
     * its core: webs, at least as many egg sacs as kill it, and the nest biome round it.
     */
    private static String spiderNestIsBuilt(ServerLevel level) {
        MiddleEarthMap map = ((MapBiomeSource) level.getChunkSource().getGenerator().getBiomeSource()).map();
        BlockPos gate = map.blockOfPixel(1310, 655);
        com.porcupinestudios.lotrport.world.map.Lairs.Lair best = null;
        for (int dx = -6000; dx <= 6000; dx += 256) {
            for (int dz = -6000; dz <= 6000; dz += 256) {
                for (var l : map.lairsInCellsOf(gate.getX() + dx, gate.getZ() + dz)) {
                    if (best == null || l.distance(gate.getX(), gate.getZ()) < best.distance(gate.getX(), gate.getZ())) {
                        best = l;
                    }
                }
            }
        }
        if (best == null) {
            return "no spider nest within 6000 blocks of the Forest Gate";
        }
        int core = best.kind().core();
        for (int cx = (best.x() - core) >> 4; cx <= (best.x() + core) >> 4; cx++) {
            for (int cz = (best.z() - core) >> 4; cz <= (best.z() + core) >> 4; cz++) {
                level.getChunk(cx, cz);
            }
        }
        int webs = 0;
        int sacs = 0;
        for (int x = best.x() - core; x <= best.x() + core; x++) {
            for (int z = best.z() - core; z <= best.z() + core; z++) {
                BlockPos top = LotrDimensions.surface(level, x, z);
                for (int dy = -24; dy <= 12; dy++) {
                    BlockState b = level.getBlockState(top.above(dy));
                    webs += b.is(Blocks.COBWEB) ? 1 : 0;
                    sacs += b.is(com.porcupinestudios.lotrport.lair.EggSacBlock.BLOCK.get()) ? 1 : 0;
                }
            }
        }
        String biome = level.getBiome(LotrDimensions.surface(level, best.x(), best.z())).unwrapKey()
                .map(k -> k.location().toString()).orElse("?");
        LotrPort.LOG.info("[worldcheck] spider nest at {},{}: {} webs, {} egg sacs, biome {}", best.x(), best.z(), webs,
                sacs, biome);
        int deadAt = com.porcupinestudios.lotrport.lair.LairLife.settings(best.kind()).deadAt();
        if (webs < 30 || sacs < deadAt) {
            return "spider nest at " + best.x() + "," + best.z() + " has " + webs + " webs and " + sacs + " egg sacs";
        }
        if (!biome.equals("lotr_port:mirkwood_spider_nests")) {
            return "spider nest at " + best.x() + "," + best.z() + " stands in " + biome;
        }
        return null;
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
                    Roads.Style style = roads.style(level.getBiome(top).unwrapKey()
                            .map(k -> k.location().toString()).orElse(""));
                    String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                    if (style != null && (style.top().stream().anyMatch(p -> p.block().equals(id))
                            || style.slab().stream().anyMatch(p -> p.block().equals(id))
                            || id.equals(style.deck()) || id.equals(style.edge()))) {
                        LotrPort.LOG.info("[worldcheck] road found at {}", top.toShortString());
                        return null;
                    }
                }
            }
        }
        return "no road blocks found on " + tried + " road segments";
    }

    /**
     * Roads LOTR kept in repair (repair 1) hold no plants: the vegetation's grass and flowers are cleared off them by
     * {@code lotr_port:road_verge}. Checks the road columns round the middle of the first 40 segments.
     */
    private static String keptRoadsAreBare(ServerLevel level) {
        Roads roads = Roads.get();
        int tried = 0;
        int columns = 0;
        for (Roads.Segment s : roads.segments()) {
            if (tried++ >= 40) {
                break;
            }
            int x = (int) Math.floor((s.x1() + s.x2()) / 2);
            int z = (int) Math.floor((s.z1() + s.z2()) / 2);
            level.getChunk(x >> 4, z >> 4);
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    int cx = x + dx;
                    int cz = z + dz;
                    if (!com.porcupinestudios.lotrport.world.feature.RoadFeature.onRoad(roads, cx, cz)) {
                        continue;
                    }
                    BlockPos at = new BlockPos(cx, level.getHeight(
                            net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz), cz);
                    BlockState ground = level.getBlockState(at.below());
                    Roads.Style style = roads.style(level.getBiome(at.below()).unwrapKey()
                            .map(k -> k.location().toString()).orElse(""));
                    if (style == null || style.repair() < 1 || !ground.getFluidState().isEmpty()) {
                        continue;
                    }
                    columns++;
                    BlockState above = level.getBlockState(at);
                    if (above.getBlock() instanceof net.minecraft.world.level.block.BushBlock
                            || above.is(net.minecraft.tags.BlockTags.FLOWERS)) {
                        return "a plant (" + above + ") stands on a kept road at " + at.toShortString();
                    }
                }
            }
        }
        LotrPort.LOG.info("[worldcheck] {} kept road columns are bare", columns);
        return null;
    }
}
