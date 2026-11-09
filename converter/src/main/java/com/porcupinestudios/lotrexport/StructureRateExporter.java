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
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraft.world.gen.feature.WorldGenerator;
import net.minecraft.world.storage.ISaveFormat;
import net.minecraftforge.common.util.BlockSnapshot;

/**
 * Writes structure_rates.json ({@code -Dlotrexport.rates=true}): how often each biome's LOTR structures really stand,
 * measured in LOTR's own Middle-earth. Opens a throwaway single-player world and, in Middle-earth, puts a probe in
 * place of every biome decorator's random structure and sets its chance to 1, so the decorator offers every structure
 * in every chunk at the spot and height it would have used. The probe builds the structure with Forge's block
 * snapshots captured, counts whether LOTR's own checks let it stand, then puts every block back and removes what it
 * spawned: each chunk samples each structure on land no other structure touched. Chunks are populated in 4x4 patches
 * spread over each biome's pixels of LOTR's map. Per biome it writes the chunks LOTR would roll structures in (off the
 * axes, no road near: the decorator's own checks) and per structure the probes and how many stood; a chunk whose
 * variant turns structures off (orchards, vineyards) counts but gets no probe. Reflection on public game state only;
 * no LOTR code is copied.
 */
public class StructureRateExporter {
    private static final long SEED = 1L;
    private static final String FOLDER = "lotrexport-rates";
    /** Patches per biome, each SIDE x SIDE populated chunks. */
    private static final int PATCHES = Integer.getInteger("lotrexport.ratePatches", 9);
    private static final int SIDE = 4;

    private final File out;
    private final JsonArray errors = new JsonArray();
    /** LOTR biome (identity) to its numbers, in LOTR's biome order. */
    private final Map<Object, Biome> biomes = new LinkedHashMap<>();
    private final List<int[]> patches = new ArrayList<>();
    /** Chunks in or near a patch already done: a patch touching them may find them populated already. */
    private final Set<Long> touched = new HashSet<>();
    /** The current patch's chunks, whose probes count. */
    private final Set<Long> counted = new HashSet<>();
    /** Chunks populated inside another probe: their decoration was put back with it, so they don't count. */
    private final Set<Long> spoiled = new HashSet<>();
    private final Set<String> failed = new HashSet<>();
    private boolean busy;
    private int next = -1;
    private long deadline;
    private int dimension;
    private int originX;
    private int originZ;
    private int scale;
    private Method roadNear;
    /** The decorator's chunk being decorated: a chunk populated inside a probe decorates over it (2026-11-04 run). */
    private Field[] decoratorState;
    private int chunksDone;
    private boolean started;
    private volatile boolean finished;

    private static final class Biome {
        final String name;
        final List<Probe> probes = new ArrayList<>();
        int chunks;

        Biome(String name) {
            this.name = name;
        }
    }

    /** Stands in for one of a decorator's random structures. */
    private static final class Probe extends WorldGenerator {
        final StructureRateExporter owner;
        final Object decorator;
        final WorldGenerator gen;
        final int chance;
        int calls;
        int placed;

        Probe(StructureRateExporter owner, Object decorator, WorldGenerator gen, int chance) {
            this.owner = owner;
            this.decorator = decorator;
            this.gen = gen;
            this.chance = chance;
        }

        @Override
        public boolean generate(World world, Random rand, int x, int y, int z) {
            return owner.probe(this, world, rand, x, y, z);
        }
    }

    public StructureRateExporter(File out) {
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
                dimension = Class.forName("lotr.common.LOTRDimension").getField("dimensionID")
                        .getInt(Class.forName("lotr.common.LOTRDimension").getField("MIDDLE_EARTH").get(null));
            }
            WorldServer world = MinecraftServer.getServer().worldServerForDimension(dimension);
            ChunkProviderServer chunks = world.theChunkProviderServer;
            if (next < 0) {
                setUp();
                next = 0;
                deadline = System.currentTimeMillis() + Long.getLong("lotrexport.rateMinutes", 35) * 60_000L;
                System.out.println("[lotrexport] structure rates: " + biomes.size() + " biomes, " + patches.size()
                        + " patches");
            }
            long tick = System.currentTimeMillis() + 2000;
            while (next < patches.size() && System.currentTimeMillis() < tick && System.currentTimeMillis() < deadline) {
                patch(world, patches.get(next++));
                if (next % 50 == 0) {
                    System.out.println("[lotrexport] structure rates: " + next + " of " + patches.size() + " patches");
                }
            }
            // queued chunks unload 100 per world tick; drain them so memory stays flat, but keep one loaded
            // (Forge unloads a dimension with none)
            chunks.unloadAllChunks();
            chunks.loadChunk(0, 0);
            for (int n = 0; n < 50 && chunks.loadedChunks.size() > 1; n++) {
                chunks.unloadQueuedChunks();
            }
            // population schedules block ticks (water, sand) that 1.7.10 keeps after their chunk unloads: millions by
            // the end, slowing every tick (2026-11-04 run); probes run inside population, before any tick, so drop them
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
            errors.add(new JsonPrimitive("structure rates: " + t));
            try {
                write();
            } catch (Throwable u) {
                u.printStackTrace();
            }
            finished = true;
        }
    }

    /** Puts the probes in and picks the patches. */
    private void setUp() throws Exception {
        Class<?> dimClass = Class.forName("lotr.common.LOTRDimension");
        Object[] list = (Object[]) dimClass.getField("biomeList").get(dimClass.getField("MIDDLE_EARTH").get(null));
        roadNear = Class.forName("lotr.common.world.map.LOTRRoads").getMethod("isRoadNear", int.class, int.class,
                int.class);
        Class<?> decoratorClass = Class.forName("lotr.common.world.biome.LOTRBiomeDecorator");
        decoratorState = new Field[4];
        String[] names = { "worldObj", "rand", "chunkX", "chunkZ" };
        for (int n = 0; n < names.length; n++) {
            decoratorState[n] = decoratorClass.getDeclaredField(names[n]);
            decoratorState[n].setAccessible(true);
        }
        Map<Object, Boolean> decorators = new IdentityHashMap<>();
        boolean[] wanted = new boolean[list.length];
        for (int id = 0; id < list.length; id++) {
            Object b = list[id];
            if (b == null) {
                continue;
            }
            Object decorator = field(b, "decorator");
            List<?> structures = decorator == null ? null : (List<?>) field(decorator, "randomStructures");
            if (structures == null || structures.isEmpty()) {
                continue;
            }
            String name = ((BiomeGenBase) b).biomeName;
            if (decorators.put(decorator, true) != null) {
                errors.add(new JsonPrimitive("biome " + name + " shares a decorator; not measured"));
                continue;
            }
            Biome biome = new Biome(name);
            for (Object rs : structures) {
                Field gen = rs.getClass().getDeclaredField("structureGen");
                Field chance = rs.getClass().getDeclaredField("chunkChance");
                gen.setAccessible(true);
                chance.setAccessible(true);
                Probe probe = new Probe(this, decorator, (WorldGenerator) gen.get(rs), chance.getInt(rs));
                gen.set(rs, probe);
                chance.setInt(rs, 1); // offered in every chunk
                biome.probes.add(probe);
            }
            biomes.put(b, biome);
            wanted[id] = true;
        }

        Class<?> layer = Class.forName("lotr.common.world.genlayer.LOTRGenLayerWorld");
        if (!(Boolean) layer.getMethod("loadedBiomeImage").invoke(null)) {
            throw new IllegalStateException("LOTR's map image isn't loaded");
        }
        Field data = layer.getDeclaredField("biomeImageData");
        data.setAccessible(true);
        byte[] image = (byte[]) data.get(null);
        int w = layer.getField("imageWidth").getInt(null);
        int h = layer.getField("imageHeight").getInt(null);
        originX = layer.getField("originX").getInt(null);
        originZ = layer.getField("originZ").getInt(null);
        scale = layer.getField("scale").getInt(null);
        // per biome, PATCHES random pixels; inside the biome (its four neighbours the same) where it has any
        Random random = new Random(SEED);
        int[][] picks = new int[list.length][PATCHES];
        int[] seen = new int[list.length];
        for (int pass = 0; pass < 2; pass++) {
            boolean[] open = new boolean[list.length]; // pass 1: biomes with no pixel inside, any pixel
            for (int id = 0; id < list.length; id++) {
                open[id] = wanted[id] && seen[id] == 0;
            }
            for (int pz = 1; pz < h - 1; pz++) {
                for (int px = 1; px < w - 1; px++) {
                    int i = pz * w + px;
                    int id = image[i] & 255;
                    if (id >= list.length || !open[id] || pass == 0 && ((image[i - 1] & 255) != id
                            || (image[i + 1] & 255) != id || (image[i - w] & 255) != id
                            || (image[i + w] & 255) != id)) {
                        continue;
                    }
                    int n = seen[id]++; // reservoir sampling
                    int r = n < PATCHES ? n : random.nextInt(n + 1);
                    if (r < PATCHES) {
                        picks[id][r] = i;
                    }
                }
            }
        }
        for (int p = 0; p < PATCHES; p++) {
            for (int id = 0; id < list.length; id++) {
                if (wanted[id] && p < seen[id]) {
                    patches.add(new int[] { picks[id][p] % w, picks[id][p] / w });
                } else if (wanted[id] && p == 0) {
                    errors.add(new JsonPrimitive("biome " + ((BiomeGenBase) list[id]).biomeName + " not on the map"));
                }
            }
        }
    }

    /** Populates one patch around a map pixel's middle and counts its chunks. */
    private void patch(WorldServer world, int[] pixel) throws Exception {
        int x0 = ((pixel[0] - originX) * scale + scale / 2 >> 4) - SIDE / 2;
        int z0 = ((pixel[1] - originZ) * scale + scale / 2 >> 4) - SIDE / 2;
        for (int x = x0; x <= x0 + SIDE; x++) {
            for (int z = z0; z <= z0 + SIDE; z++) {
                if (touched.contains(ChunkCoordIntPair.chunkXZ2Int(x, z))) {
                    return;
                }
            }
        }
        for (int x = x0 - 3; x <= x0 + SIDE + 3; x++) {
            for (int z = z0 - 3; z <= z0 + SIDE + 3; z++) {
                touched.add(ChunkCoordIntPair.chunkXZ2Int(x, z));
            }
        }
        counted.clear();
        spoiled.clear();
        for (int x = x0; x < x0 + SIDE; x++) {
            for (int z = z0; z < z0 + SIDE; z++) {
                counted.add(ChunkCoordIntPair.chunkXZ2Int(x, z));
            }
        }
        // one more row and column, so every counted chunk has the neighbours it needs to populate
        for (int x = x0; x <= x0 + SIDE; x++) {
            for (int z = z0; z <= z0 + SIDE; z++) {
                world.theChunkProviderServer.loadChunk(x, z);
            }
        }
        for (int x = x0; x < x0 + SIDE; x++) {
            for (int z = z0; z < z0 + SIDE; z++) {
                Chunk chunk = world.getChunkFromChunkCoords(x, z);
                if (!chunk.isTerrainPopulated || spoiled.contains(ChunkCoordIntPair.chunkXZ2Int(x, z))) {
                    continue;
                }
                // the decorator's own rules: the biome at the chunk's +16, +16, off the axes, no road near +8, +8
                int bx = x << 4;
                int bz = z << 4;
                Biome biome = biomes.get(world.getBiomeGenForCoords(bx + 16, bz + 16));
                if (biome == null || Math.abs(bx) <= 32 || Math.abs(bz) <= 32
                        || ((Number) roadNear.invoke(null, bx + 8, bz + 8, 16)).floatValue() >= 0) {
                    continue;
                }
                biome.chunks++;
                chunksDone++;
            }
        }
        counted.clear();
    }

    /** Builds the structure with every block change captured, counts whether it stood, and undoes it. */
    private boolean probe(Probe p, World world, Random rand, int x, int y, int z) {
        // the decorator picks x and z 8 to 23 blocks into its chunk
        long chunk = ChunkCoordIntPair.chunkXZ2Int((x - 8) >> 4, (z - 8) >> 4);
        if (busy) {
            spoiled.add(chunk);
            return false;
        }
        if (!counted.contains(chunk)) {
            return false;
        }
        Object[] state = new Object[decoratorState.length];
        try {
            for (int n = 0; n < state.length; n++) {
                state[n] = decoratorState[n].get(p.decorator);
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e); // setAccessible was called in setUp
        }
        busy = true;
        int entities = world.loadedEntityList.size();
        boolean stood = false;
        world.captureBlockSnapshots = true;
        try {
            stood = p.gen.generate(world, rand, x, y, z);
        } catch (Throwable t) {
            if (failed.add(p.gen.getClass().getName())) {
                errors.add(new JsonPrimitive("structure " + p.gen.getClass().getName() + ": " + t));
            }
        } finally {
            // a neighbour populated inside the build decorates with this same decorator: give it back this chunk, or
            // the rest of this chunk's list goes to the neighbour's spots (lost probes, 2026-11-04 run)
            try {
                for (int n = 0; n < state.length; n++) {
                    decoratorState[n].set(p.decorator, state[n]);
                }
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
            world.captureBlockSnapshots = false;
            List<BlockSnapshot> snapshots = world.capturedBlockSnapshots;
            world.restoringBlockSnapshots = true; // no drops from chests taken away
            for (int n = snapshots.size() - 1; n >= 0; n--) {
                snapshots.get(n).restore(true, false);
            }
            world.restoringBlockSnapshots = false;
            snapshots.clear();
            List<?> loaded = world.loadedEntityList;
            for (int n = entities; n < loaded.size(); n++) {
                ((Entity) loaded.get(n)).setDead();
            }
            busy = false;
        }
        p.calls++;
        if (stood) {
            p.placed++;
        }
        return false;
    }

    private void write() throws Exception {
        JsonObject root = new JsonObject();
        root.addProperty("seed", SEED);
        root.addProperty("patches", next);
        root.addProperty("chunks", chunksDone);
        JsonObject out = new JsonObject();
        for (Biome b : biomes.values()) {
            JsonObject o = new JsonObject();
            o.addProperty("chunks", b.chunks);
            JsonArray list = new JsonArray();
            for (Probe p : b.probes) {
                JsonObject s = new JsonObject();
                s.addProperty("class", p.gen.getClass().getName());
                s.addProperty("chance", p.chance);
                s.addProperty("calls", p.calls);
                s.addProperty("placed", p.placed);
                list.add(s);
            }
            o.add("structures", list);
            out.add(b.name, o);
        }
        root.add("biomes", out);
        root.add("errors", errors);
        this.out.mkdirs();
        try (Writer w = new OutputStreamWriter(new FileOutputStream(new File(this.out, "structure_rates.json")),
                StandardCharsets.UTF_8)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(root, w);
        }
        System.out.println("[lotrexport] wrote structure_rates.json: " + chunksDone + " chunks, " + errors.size()
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
