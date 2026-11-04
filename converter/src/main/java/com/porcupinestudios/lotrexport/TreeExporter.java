package com.porcupinestudios.lotrexport;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import net.minecraft.block.Block;
import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.init.Blocks;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.feature.WorldGenerator;

/**
 * Writes trees.json.gz: LOTR's hand-made tree shapes. Grows every {@code LOTRTreeType} several times with different
 * seeds on fresh flat grass in a throwaway client world and writes the blocks each tree placed (logs, leaves,
 * branches, roots; the grass under it turning to dirt too). LOTR's tree generators are run, not copied.
 */
public class TreeExporter {
    /** Shapes per tree type (LOTR randomises height, branches and crown with the seed). */
    private static final int SHAPES = 6;
    /** Seeds tried per type before giving up (some trees refuse a spot, e.g. need more room). */
    private static final int TRIES = 24;
    private static final int RADIUS = 32;
    private static final int GROUND = 40;

    private final File out;
    private final JsonArray errors = new JsonArray();
    private final JsonObject trees = new JsonObject();

    public TreeExporter(File out) {
        this.out = out;
    }

    public void run(WorldClient world) throws Exception {
        Class<?> type = Class.forName("lotr.common.world.feature.LOTRTreeType");
        Object[] values = (Object[]) type.getMethod("values").invoke(null);
        java.lang.reflect.Method create = type.getMethod("create", boolean.class, Random.class);
        System.out.println("[lotrexport] " + values.length + " tree types");
        for (Object t : values) {
            String name = ((Enum<?>) t).name();
            JsonArray shapes = new JsonArray();
            String lastError = null;
            for (int seed = 0; seed < TRIES && shapes.size() < SHAPES; seed++) {
                try {
                    Random random = new Random(seed * 7919L + name.hashCode());
                    WorldGenerator gen = (WorldGenerator) create.invoke(t, false, random);
                    JsonObject shape = grow(world, gen, random);
                    if (shape != null) {
                        shape.addProperty("seed", seed);
                        shapes.add(shape);
                    }
                } catch (Throwable e) {
                    lastError = e.toString();
                }
            }
            if (shapes.size() > 0) {
                trees.add(name, shapes);
            } else {
                errors.add(new JsonPrimitive("tree " + name + " grew nothing" + (lastError == null ? ""
                        : ": " + lastError)));
            }
        }
        write();
    }

    /** Grows one tree at 0, GROUND + 1, 0 on fresh flat chunks and reads back what changed. */
    private JsonObject grow(WorldClient world, WorldGenerator gen, Random random) {
        ChunkProviderClient chunks = (ChunkProviderClient) world.getChunkProvider();
        for (int x = (-RADIUS - 16) >> 4; x <= (RADIUS + 16) >> 4; x++) {
            for (int z = (-RADIUS - 16) >> 4; z <= (RADIUS + 16) >> 4; z++) {
                chunks.unloadChunk(x, z);
                Chunk chunk = chunks.loadChunk(x, z);
                for (int bx = 0; bx < 16; bx++) {
                    for (int bz = 0; bz < 16; bz++) {
                        for (int y = 0; y <= GROUND; y++) {
                            chunk.func_150807_a(bx, y, bz, flat(y), 0);
                        }
                    }
                }
            }
        }
        if (!gen.generate(world, random, 0, GROUND + 1, 0)) {
            return null;
        }
        Map<String, Integer> palette = new LinkedHashMap<>();
        JsonArray blocks = new JsonArray();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                Chunk chunk = world.getChunkFromBlockCoords(x, z);
                int top = Math.max(GROUND + 1, chunk.getTopFilledSegment() + 16);
                for (int y = 1; y < top; y++) {
                    Block b = chunk.getBlock(x & 15, y, z & 15);
                    int meta = chunk.getBlockMetadata(x & 15, y, z & 15);
                    if (b == flat(y) && meta == 0) {
                        continue;
                    }
                    String key = Block.blockRegistry.getNameForObject(b) + "#" + meta;
                    Integer index = palette.get(key);
                    if (index == null) {
                        index = palette.size();
                        palette.put(key, index);
                    }
                    JsonArray a = new JsonArray();
                    a.add(new JsonPrimitive(x));
                    a.add(new JsonPrimitive(y));
                    a.add(new JsonPrimitive(z));
                    a.add(new JsonPrimitive(index));
                    blocks.add(a);
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    minZ = Math.min(minZ, z);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                    maxZ = Math.max(maxZ, z);
                }
            }
        }
        if (blocks.size() == 0) {
            return null;
        }
        JsonObject o = new JsonObject();
        o.addProperty("ground", GROUND);
        o.add("min", pos(minX, minY, minZ));
        o.add("max", pos(maxX, maxY, maxZ));
        JsonArray pal = new JsonArray();
        for (String k : palette.keySet()) {
            pal.add(new JsonPrimitive(k));
        }
        o.add("palette", pal);
        o.add("blocks", blocks);
        return o;
    }

    private static Block flat(int y) {
        return y == 0 ? Blocks.bedrock : y < GROUND ? Blocks.dirt : y == GROUND ? Blocks.grass : Blocks.air;
    }

    private static JsonArray pos(int x, int y, int z) {
        JsonArray a = new JsonArray();
        a.add(new JsonPrimitive(x));
        a.add(new JsonPrimitive(y));
        a.add(new JsonPrimitive(z));
        return a;
    }

    private void write() throws Exception {
        JsonObject root = new JsonObject();
        root.add("trees", trees);
        root.add("errors", errors);
        try (Writer w = new OutputStreamWriter(new java.util.zip.GZIPOutputStream(
                new FileOutputStream(new File(out, "trees.json.gz"))), StandardCharsets.UTF_8)) {
            new Gson().toJson(root, w);
        }
        System.out.println("[lotrexport] wrote trees.json.gz: " + trees.entrySet().size() + " tree types, "
                + errors.size() + " errors");
    }
}
