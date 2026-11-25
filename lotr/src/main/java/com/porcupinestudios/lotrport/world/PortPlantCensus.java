package com.porcupinestudios.lotrport.world;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.world.map.MiddleEarthMap;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Dev tool (2026-11-25, Aaron: check the port's plant rates against LOTR's): {@code -Dlotr_port.plantcensus=<patches>}
 * on a server of the Middle-earth world type counts the plants of the port's own generation as the exporter's
 * PlantCensus counts LOTR's: per biome {@code patches} random spots inside it (seed 1), a 4x4 of generated chunks
 * round each (the ring round them generated too, so plants spilling in from neighbours are there), every column
 * followed from its top through plants, leaves, water and snow to the ground, each plant counted once by its bottom
 * block under the column's biome. Writes {@code plant_census_port.json} in the run folder ({@code converter/
 * plant_compare.py} sets it against LOTR's) and stops the JVM without saving.
 */
public final class PortPlantCensus {
    private static final int SIDE = 4;
    private static final int DEPTH = 64;

    private PortPlantCensus() {}

    private static final class Tally {
        long columns;
        final Map<String, long[]> plants = new TreeMap<>(); // count, chunks with
        final Map<String, Set<Long>> chunks = new TreeMap<>();
    }

    private static Job job;

    /** The census's state between server ticks: one patch a tick, so loaded chunks can unload in between. */
    private static final class Job {
        final ServerLevel level;
        final Path out;
        final java.util.List<int[]> spots = new java.util.ArrayList<>();
        final Map<String, Tally> tallies = new TreeMap<>();
        final Set<Long> done = new HashSet<>();
        final long start = System.nanoTime();
        int next;
        int chunks;

        Job(ServerLevel level, Path out) {
            this.level = level;
            this.out = out;
        }
    }

    static void runIfAsked(MinecraftServer server) {
        String arg = System.getProperty("lotr_port.plantcensus");
        if (arg == null) {
            return;
        }
        int patches = Integer.parseInt(arg.trim());
        Job j = new Job(server.overworld(), server.getServerDirectory().resolve("plant_census_port.json"));
        MiddleEarthMap map = MiddleEarthMap.get();
        Random random = new Random(1L);
        for (int b = 0; b < map.biomes().size(); b++) {
            List<int[]> pixels = map.pixelCentres(b, 1_000_000);
            for (int p = 0; p < patches && !pixels.isEmpty(); p++) {
                j.spots.add(pixels.get(random.nextInt(pixels.size())));
            }
        }
        LotrPort.LOG.info("[plantcensus] {} patches", j.spots.size());
        job = j;
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.tick.ServerTickEvent.Post e) -> tick());
    }

    private static void tick() {
        Job j = job;
        if (j == null) {
            return;
        }
        try {
            if (j.next < j.spots.size()) {
                patch(j, j.spots.get(j.next++));
                if (j.next % 50 == 0) {
                    LotrPort.LOG.info("[plantcensus] {} of {} patches, {} chunks", j.next, j.spots.size(), j.chunks);
                }
                return;
            }
            write(j);
        } catch (Exception e) {
            LotrPort.LOG.error("[plantcensus] failed", e);
        }
        job = null;
        org.apache.logging.log4j.LogManager.shutdown();
        Runtime.getRuntime().halt(0);
    }

    private static void patch(Job j, int[] spot) {
        int x0 = (spot[0] >> 4) - SIDE / 2;
        int z0 = (spot[1] >> 4) - SIDE / 2;
        for (int x = x0 - 1; x <= x0 + SIDE; x++) {
            for (int z = z0 - 1; z <= z0 + SIDE; z++) {
                j.level.getChunk(x, z); // the ring too: its plants spill in
            }
        }
        for (int x = x0; x < x0 + SIDE; x++) {
            for (int z = z0; z < z0 + SIDE; z++) {
                long key = ChunkPos.asLong(x, z);
                if (!j.done.add(key)) {
                    continue;
                }
                for (int i = 0; i < 16; i++) {
                    for (int k = 0; k < 16; k++) {
                        column(j.level, j.tallies, key, (x << 4) + i, (z << 4) + k);
                    }
                }
                j.chunks++;
            }
        }
    }

    private static void write(Job j) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("chunks", j.chunks);
        JsonObject all = new JsonObject();
        for (Map.Entry<String, Tally> e : j.tallies.entrySet()) {
            Tally t = e.getValue();
            JsonObject o = new JsonObject();
            o.addProperty("columns", t.columns);
            JsonObject plants = new JsonObject();
            for (Map.Entry<String, long[]> p : t.plants.entrySet()) {
                JsonObject q = new JsonObject();
                q.addProperty("count", p.getValue()[0]);
                q.addProperty("per_chunk", Math.round(p.getValue()[0] / (t.columns / 256.0) * 10000) / 10000.0);
                q.addProperty("chunks_with", t.chunks.get(p.getKey()).size());
                plants.add(p.getKey(), q);
            }
            o.add("plants", plants);
            all.add(e.getKey(), o);
        }
        root.add("biomes", all);
        try (Writer w = Files.newBufferedWriter(j.out, StandardCharsets.UTF_8)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(root, w);
        }
        LotrPort.LOG.info("[plantcensus] wrote {}: {} chunks in {} s", j.out, j.chunks,
                (System.nanoTime() - j.start) / 1_000_000_000L);
    }

    private static void column(ServerLevel level, Map<String, Tally> tallies, long chunk, int x, int z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, 0, z);
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        pos.setY(top);
        String biome = level.getBiome(pos).unwrapKey().map(k -> k.location().toString()).orElse("?");
        Tally tally = tallies.computeIfAbsent(biome, k -> new Tally());
        tally.columns++;
        for (int y = top; y >= Math.max(level.getMinBuildHeight(), top - DEPTH); y--) {
            BlockState state = level.getBlockState(pos.setY(y));
            if (isPlant(state)) {
                if (level.getBlockState(pos.setY(y - 1)).getBlock() == state.getBlock()) {
                    continue; // counted at its bottom
                }
                String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                tally.plants.computeIfAbsent(id, k -> new long[1])[0]++;
                tally.chunks.computeIfAbsent(id, k -> new HashSet<>()).add(chunk);
                continue;
            }
            if (state.isAir() || state.is(BlockTags.LEAVES) || !state.getFluidState().isEmpty()
                    || state.getBlock() instanceof SnowLayerBlock) {
                continue;
            }
            if (state.isSolid()) {
                return; // the ground, a trunk or a rock
            }
        }
    }

    /** As the exporter's census: bushes, flowers and grass, vines, cacti, cane; not leaves or logs. */
    private static boolean isPlant(BlockState state) {
        Block b = state.getBlock();
        if (state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS) || !state.getFluidState().isEmpty() && !(b instanceof BushBlock)) {
            return false;
        }
        return b instanceof BushBlock || b instanceof VineBlock || b instanceof CactusBlock || b instanceof SugarCaneBlock
                || b instanceof BambooStalkBlock || state.is(BlockTags.FLOWERS) || state.is(BlockTags.SAPLINGS)
                || state.is(BlockTags.REPLACEABLE) && !state.isAir() && !(b instanceof SnowLayerBlock)
                && state.getFluidState().isEmpty();
    }
}
