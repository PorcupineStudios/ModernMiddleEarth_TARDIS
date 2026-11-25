package com.porcupinestudios.lotrexport;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBush;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraft.world.storage.ISaveFormat;
import net.minecraftforge.common.IPlantable;

/**
 * Writes plants_census.json ({@code -Dlotrexport.plants=true}): every plant LOTR's own world generation puts on the
 * ground of each biome, counted in LOTR's real Middle-earth. Opens a throwaway single-player world and populates
 * chunks in 4x4 patches spread over each biome's pixels of LOTR's map (as the structure rates do), then scans every
 * column of each populated chunk from the top down through plants, leaves and water to the ground, and counts each
 * plant (block#meta; a stack of the same block, such as a double plant, cane or a cactus, counts once, by its bottom)
 * under the column's biome, with the block it stands on. Per biome: columns scanned, and per plant the count, the
 * number of chunks that had it (patchiness) and its grounds. This catches plants from every source: the decorator's
 * grass, flowers and doubles, and each biome's own decorate (athelas, simbelmyne, elanor...). Reflection on public game
 * state only; no LOTR code is copied.
 */
public class PlantCensus {
    private static final long SEED = 1L;
    private static final String FOLDER = "lotrexport-plants";
    private static final int PATCHES = Integer.getInteger("lotrexport.plantPatches", 6);
    private static final int SIDE = 4;
    /** How far under the first block from the top a column is followed to its ground (tall trees, deep water). */
    private static final int DEPTH = 64;

    private final File out;
    private final JsonArray errors = new JsonArray();
    private final Map<String, Tally> tallies = new LinkedHashMap<>();
    private final List<int[]> patches = new ArrayList<>();
    private final Set<Long> touched = new HashSet<>();
    private MapPatches map;
    private int next = -1;
    private long deadline;
    private int dimension;
    private int chunksDone;
    private boolean started;
    private volatile boolean finished;

    private static final class Tally {
        long columns;
        final Map<String, Plant> plants = new TreeMap<>();
    }

    private static final class Plant {
        long count;
        final Set<Long> chunks = new HashSet<>();
        final Map<String, Long> grounds = new HashMap<>();
    }

    public PlantCensus(File out) {
        this.out = out;
    }

    /** Starts a fresh world; the rest runs on the integrated server's ticks and quits the game when done. */
    public void start(Minecraft mc) {
        mc.gameSettings.pauseOnLostFocus = false; // a paused integrated server stops ticking
        ISaveFormat saves = mc.getSaveLoader();
        saves.flushCache();
        saves.deleteWorldDirectory(FOLDER);
        FMLCommonHandler.instance().bus().register(this);
        started = true;
        mc.launchIntegratedServer(FOLDER, FOLDER,
                new WorldSettings(SEED, WorldSettings.GameType.CREATIVE, true, false, WorldType.DEFAULT));
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.phase != TickEvent.Phase.END || !started) {
            return;
        }
        if (finished) {
            started = false;
            System.out.println("[lotrexport] finished");
            mc.shutdown();
        } else if (mc.theWorld != null && mc.currentScreen != null && mc.currentScreen.doesGuiPauseGame()) {
            mc.displayGuiScreen(null); // any pausing screen would stop the server
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || finished) {
            return;
        }
        try {
            if (next < 0) {
                Class<?> dimClass = Class.forName("lotr.common.LOTRDimension");
                Object me = dimClass.getField("MIDDLE_EARTH").get(null);
                dimension = dimClass.getField("dimensionID").getInt(me);
                Object[] list = (Object[]) dimClass.getField("biomeList").get(me);
                boolean[] wanted = new boolean[list.length];
                for (int id = 0; id < list.length; id++) {
                    wanted[id] = list[id] != null;
                }
                map = new MapPatches(list, wanted, PATCHES, SEED, errors);
                patches.addAll(map.pixels);
                next = 0;
                deadline = System.currentTimeMillis() + Long.getLong("lotrexport.plantMinutes", 40) * 60_000L;
                System.out.println("[lotrexport] plant census: " + patches.size() + " patches");
            }
            WorldServer world = MinecraftServer.getServer().worldServerForDimension(dimension);
            ChunkProviderServer chunks = world.theChunkProviderServer;
            long tick = System.currentTimeMillis() + 2000;
            while (next < patches.size() && System.currentTimeMillis() < tick && System.currentTimeMillis() < deadline) {
                patch(world, patches.get(next++));
                if (next % 50 == 0) {
                    System.out.println("[lotrexport] plant census: " + next + " of " + patches.size() + " patches");
                }
            }
            chunks.unloadAllChunks(); // as the structure rates: keep memory flat, one chunk stays loaded
            chunks.loadChunk(0, 0);
            for (int n = 0; n < 50 && chunks.loadedChunks.size() > 1; n++) {
                chunks.unloadQueuedChunks();
            }
            for (String f : new String[] { "pendingTickListEntriesHashSet", "pendingTickListEntriesTreeSet" }) {
                if (field(world, f) instanceof java.util.Collection<?> ticks) {
                    ticks.clear();
                }
            }
            if (next >= patches.size() || System.currentTimeMillis() >= deadline) {
                if (next < patches.size()) {
                    errors.add(new JsonPrimitive("time budget used up after " + next + " of " + patches.size()
                            + " patches"));
                }
                write();
                finished = true;
            }
        } catch (Throwable t) {
            t.printStackTrace();
            errors.add(new JsonPrimitive("plant census: " + t));
            try {
                write();
            } catch (Throwable u) {
                u.printStackTrace();
            }
            finished = true;
        }
    }

    /** Populates one patch around a map pixel's middle and counts the plants of its chunks. */
    private void patch(WorldServer world, int[] pixel) {
        int x0 = map.chunkX(pixel) - SIDE / 2;
        int z0 = map.chunkZ(pixel) - SIDE / 2;
        for (int x = x0; x <= x0 + SIDE; x++) {
            for (int z = z0; z <= z0 + SIDE; z++) {
                if (touched.contains(ChunkCoordIntPair.chunkXZ2Int(x, z))) {
                    return;
                }
            }
        }
        for (int x = x0 - 1; x <= x0 + SIDE + 1; x++) {
            for (int z = z0 - 1; z <= z0 + SIDE + 1; z++) {
                touched.add(ChunkCoordIntPair.chunkXZ2Int(x, z));
            }
        }
        // one more row and column, so every counted chunk has the neighbours it needs to populate; a counted chunk's
        // plants can also come from its -x/-z neighbours' population, which runs once those load too
        for (int x = x0 - 1; x <= x0 + SIDE; x++) {
            for (int z = z0 - 1; z <= z0 + SIDE; z++) {
                world.theChunkProviderServer.loadChunk(x, z);
            }
        }
        for (int x = x0; x < x0 + SIDE; x++) {
            for (int z = z0; z < z0 + SIDE; z++) {
                Chunk chunk = world.getChunkFromChunkCoords(x, z);
                if (!chunk.isTerrainPopulated) {
                    continue;
                }
                long key = ChunkCoordIntPair.chunkXZ2Int(x, z);
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        column(world, chunk, key, (x << 4) + lx, (z << 4) + lz);
                    }
                }
                chunksDone++;
            }
        }
    }

    private void column(WorldServer world, Chunk chunk, long chunkKey, int x, int z) {
        BiomeGenBase biome = world.getBiomeGenForCoords(x, z);
        Tally tally = tallies.computeIfAbsent(biome.biomeName, k -> new Tally());
        tally.columns++;
        int y = chunk.getTopFilledSegment() + 15;
        while (y > 0 && world.getBlock(x, y, z).getMaterial() == Material.air) {
            y--;
        }
        for (int stop = Math.max(1, y - DEPTH); y >= stop; y--) {
            Block block = world.getBlock(x, y, z);
            if (isPlant(block)) {
                Block below = world.getBlock(x, y - 1, z);
                if (below == block) {
                    continue; // counted at its bottom
                }
                int meta = world.getBlockMetadata(x, y, z);
                if (block instanceof net.minecraft.block.BlockDoublePlant) {
                    meta &= 7;
                }
                Plant plant = tally.plants.computeIfAbsent(name(block) + "#" + meta, k -> new Plant());
                plant.count++;
                plant.chunks.add(chunkKey);
                String ground = below.getMaterial() == Material.water ? "minecraft:water" : name(below) + "#"
                        + world.getBlockMetadata(x, y - 1, z);
                plant.grounds.merge(ground, 1L, Long::sum);
                continue;
            }
            Material m = block.getMaterial();
            if (m == Material.air || m == Material.leaves || m == Material.water || m == Material.snow) {
                continue; // through canopies, water and snow layers to the ground
            }
            if (block.isOpaqueCube() || m.isSolid()) {
                return; // the ground (or a trunk, a rock): nothing more under it
            }
        }
    }

    /** Plants: bushes and flowers (BlockBush, IPlantable), vines, cacti, gourds; not leaves or logs. */
    private static boolean isPlant(Block block) {
        Material m = block.getMaterial();
        if (m == Material.leaves || m == Material.wood) {
            return false;
        }
        return block instanceof BlockBush || block instanceof IPlantable || m == Material.plants
                || m == Material.vine || m == Material.cactus || m == Material.gourd;
    }

    private static String name(Block block) {
        return String.valueOf(Block.blockRegistry.getNameForObject(block));
    }

    private void write() throws Exception {
        JsonObject root = new JsonObject();
        root.addProperty("seed", SEED);
        root.addProperty("patches", next);
        root.addProperty("chunks", chunksDone);
        JsonObject biomes = new JsonObject();
        for (Map.Entry<String, Tally> e : tallies.entrySet()) {
            Tally t = e.getValue();
            JsonObject o = new JsonObject();
            o.addProperty("columns", t.columns);
            double chunkArea = t.columns / 256.0;
            JsonObject plants = new JsonObject();
            for (Map.Entry<String, Plant> p : t.plants.entrySet()) {
                Plant plant = p.getValue();
                JsonObject j = new JsonObject();
                j.addProperty("count", plant.count);
                j.addProperty("per_chunk", Math.round(plant.count / chunkArea * 10000) / 10000.0);
                j.addProperty("chunks_with", plant.chunks.size());
                JsonObject grounds = new JsonObject();
                plant.grounds.entrySet().stream().sorted((a, b) -> Long.compare(b.getValue(), a.getValue())).limit(4)
                        .forEach(g -> grounds.addProperty(g.getKey(), g.getValue()));
                j.add("grounds", grounds);
                plants.add(p.getKey(), j);
            }
            o.add("plants", plants);
            biomes.add(e.getKey(), o);
        }
        root.add("biomes", biomes);
        root.add("errors", errors);
        this.out.mkdirs();
        try (Writer w = new OutputStreamWriter(new FileOutputStream(new File(this.out, "plants_census.json")),
                StandardCharsets.UTF_8)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(root, w);
        }
        System.out.println("[lotrexport] wrote plants_census.json: " + chunksDone + " chunks, " + errors.size()
                + " errors");
    }

    private static Object field(Object o, String name) {
        for (Class<?> c = o.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(o);
            } catch (NoSuchFieldException e) {
                // look in the superclass
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }
}
