package com.porcupinestudios.lotrexport;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import net.minecraft.block.Block;
import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.chunk.Chunk;

/**
 * Writes structures.json.gz. Builds every LOTR structure (the {@code structure2} generators) a few times, with
 * different seeds, on fresh flat chunks of a throwaway client world, and writes what each one placed: the blocks that
 * differ from the flat ground, chests' contents, and the NPCs and NPC spawners it added. Reflection on runtime state
 * only; no LOTR code is copied.
 */
public class StructureExporter {
    /** Layouts per structure (LOTR randomises size, rooms and decoration with the seed). */
    private static final int SEEDS = 3;
    private static final int RADIUS = 64;
    /** The area read back: RADIUS for structures, a village's own radius for villages. */
    private int radius = RADIUS;
    /** When set, build() grows this village generator (LOTRVillageGen) instead of a structure generator. */
    private Object villageGen;
    // flat world: bedrock 0, dirt 1-39, grass 40; deep enough for cellars and basements (3 cut them off, 2026-11-03)
    private static final int GROUND = 40;

    private final File out;
    private final JsonArray errors = new JsonArray();
    private final List<Class<?>> generators = new ArrayList<>();
    private final JsonObject structures = new JsonObject();
    private int next;

    public StructureExporter(File out) {
        this.out = out;
    }

    /** Builds everything in a throwaway client world (see NpcExporter), within a time budget. */
    public void run(WorldClient world) throws Exception {
        long deadline = System.currentTimeMillis() + Long.getLong("lotrexport.structureMinutes", 20) * 60_000L;
        findGenerators();
        System.out.println("[lotrexport] " + generators.size() + " structure generators");
        int total = generators.size() * SEEDS;
        for (next = 0; next < total; next++) {
            if (System.currentTimeMillis() > deadline) {
                errors.add(new JsonPrimitive("time budget used up after " + next + " of " + total + " layouts"));
                break;
            }
            Class<?> c = generators.get(next / SEEDS);
            int seed = next % SEEDS;
            try {
                JsonObject layout = build(world, c, seed);
                if (layout != null) {
                    String name = c.getSimpleName();
                    JsonArray layouts = structures.has(name) ? structures.getAsJsonArray(name) : new JsonArray();
                    layouts.add(layout);
                    structures.add(name, layouts);
                }
            } catch (Throwable t) {
                if (seed == 0) {
                    errors.add(new JsonPrimitive("structure " + c.getSimpleName() + ": " + t));
                }
            }
            if (next % 60 == 0) {
                System.out.println("[lotrexport] structures: " + next + " of " + total);
            }
        }
        villages(world, System.currentTimeMillis() + Long.getLong("lotrexport.villageMinutes", 15) * 60_000L);
        write();
    }

    /**
     * LOTR's villages (LOTRVillageGen, one per kind of village in the biomes' decorators): each grown whole a couple
     * of times on the flat ground, read back like a structure as "village:<class>", with the generator's grid
     * numbers (gridScale, gridRandomDisplace, spawnChance, villageChunkRadius) for placing them.
     */
    private void villages(WorldClient world, long deadline) {
        Map<String, Object> gens = new java.util.TreeMap<>();
        try {
            Class<?> dim = Class.forName("lotr.common.LOTRDimension");
            Object me = dim.getField("MIDDLE_EARTH").get(null);
            Field list = dim.getDeclaredField("biomeList");
            list.setAccessible(true);
            for (Object b : (Object[]) list.get(me)) {
                if (b == null) {
                    continue;
                }
                Object dec = field(b, "decorator");
                Object vs = dec == null ? null : field(dec, "villages");
                if (vs instanceof List<?> l) {
                    for (Object v : l) {
                        gens.putIfAbsent(v.getClass().getName(), v);
                    }
                }
            }
        } catch (Throwable t) {
            errors.add(new JsonPrimitive("villages: " + t));
            return;
        }
        System.out.println("[lotrexport] " + gens.size() + " village generators");
        for (Object v : gens.values()) {
            Object r = field(v, "villageChunkRadius");
            radius = Math.min(160, ((r instanceof Number n) ? n.intValue() : 5) * 16 + 16);
            villageGen = v;
            for (int seed = 0; seed < 2 && System.currentTimeMillis() < deadline; seed++) {
                try {
                    JsonObject layout = build(world, v.getClass(), seed);
                    if (layout != null) {
                        for (String f : new String[] { "gridScale", "gridRandomDisplace", "spawnChance",
                                "villageChunkRadius" }) {
                            Object x = field(v, f);
                            if (x instanceof Number n) {
                                layout.addProperty(f, n);
                            }
                        }
                        String name = "village:" + v.getClass().getSimpleName();
                        JsonArray layouts = structures.has(name) ? structures.getAsJsonArray(name) : new JsonArray();
                        layouts.add(layout);
                        structures.add(name, layouts);
                    }
                } catch (Throwable t) {
                    Throwable c = t.getCause() != null ? t.getCause() : t;
                    errors.add(new JsonPrimitive("village " + v.getClass().getSimpleName() + ": " + c));
                }
            }
            System.out.println("[lotrexport] village " + v.getClass().getSimpleName());
        }
        villageGen = null;
        radius = RADIUS;
    }

    /** Every concrete structure generator class in LOTR's jar. */
    private void findGenerators() throws Exception {
        Class<?> base = Class.forName("lotr.common.world.structure2.LOTRWorldGenStructureBase2");
        URL url = base.getProtectionDomain().getCodeSource().getLocation();
        String path = url.getPath();
        if (path.contains("!")) {
            path = path.substring(0, path.indexOf('!'));
        }
        path = java.net.URLDecoder.decode(path.replace("file:", ""), "UTF-8");
        List<String> names = new ArrayList<>();
        try (JarFile jar = new JarFile(path)) {
            for (Enumeration<JarEntry> e = jar.entries(); e.hasMoreElements();) {
                String n = e.nextElement().getName();
                if (n.startsWith("lotr/common/world/structure2/") && n.endsWith(".class") && !n.contains("$")
                        && n.indexOf('/', "lotr/common/world/structure2/".length()) < 0) {
                    names.add(n.substring(0, n.length() - 6).replace('/', '.'));
                }
            }
        }
        java.util.Collections.sort(names);
        for (String n : names) {
            try {
                Class<?> c = Class.forName(n);
                if (base.isAssignableFrom(c) && !Modifier.isAbstract(c.getModifiers())) {
                    c.getConstructor(boolean.class);
                    generators.add(c);
                }
            } catch (Throwable t) {
                errors.add(new JsonPrimitive("generator " + n + ": " + t));
            }
        }
    }

    /** Builds one layout at its own spot and reads back what changed. */
    private JsonObject build(WorldClient world, Class<?> c, int seed) throws Exception {
        // the same spot every time: chunks are made fresh and flat, and dropped afterwards
        int cx = 0;
        int cz = 0;
        ChunkProviderClient chunks = (ChunkProviderClient) world.getChunkProvider();
        for (int x = (cx - radius - 16) >> 4; x <= (cx + radius + 16) >> 4; x++) {
            for (int z = (cz - radius - 16) >> 4; z <= (cz + radius + 16) >> 4; z++) {
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
        world.loadedEntityList.clear();
        Random random = new Random(seed * 7919L + c.getName().hashCode());
        Object placed;
        if (villageGen != null) {
            Class<?> vg = Class.forName("lotr.common.world.village.LOTRVillageGen");
            Class<?> info = Class.forName("lotr.common.world.village.LocationInfo");
            Object here = info.getField("RANDOM_GEN_HERE").get(null);
            java.lang.reflect.Method create = vg.getDeclaredMethod("createAndSetupVillageInstance",
                    net.minecraft.world.World.class, int.class, int.class, Random.class, info);
            create.setAccessible(true);
            Object instance = create.invoke(villageGen, world, cx, cz, random, here);
            java.lang.reflect.Method grow = null;
            for (java.lang.reflect.Method m : vg.getDeclaredMethods()) {
                if (m.getName().equals("generateCompleteVillageInstance")) {
                    grow = m;
                }
            }
            grow.setAccessible(true);
            grow.invoke(villageGen, instance, world, cx, cz);
            placed = instance;
        } else {
            Object gen = c.getConstructor(boolean.class).newInstance(false);
            setField(gen, "restrictions", false);
            setField(gen, "shouldFindSurface", false);
            placed = c.getMethod("generateWithSetRotation", net.minecraft.world.World.class, Random.class,
                    int.class, int.class, int.class, int.class).invoke(gen, world, random, cx, GROUND + 1, cz, 0);
        }

        Map<String, Integer> palette = new LinkedHashMap<>();
        JsonArray blocks = new JsonArray();
        JsonArray chests = new JsonArray();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        List<int[]> changed = new ArrayList<>();
        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int z = cz - radius; z <= cz + radius; z++) {
                Chunk chunk = world.getChunkFromBlockCoords(x, z);
                int top = Math.max(GROUND + 1, chunk.getTopFilledSegment() + 16);
                for (int y = 0; y < top; y++) {
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
                    changed.add(new int[] { x, y, z, index });
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    minZ = Math.min(minZ, z);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                    maxZ = Math.max(maxZ, z);
                    TileEntity te = chunk.func_150806_e(x & 15, y, z & 15);
                    if (te instanceof IInventory inv) {
                        JsonObject chest = new JsonObject();
                        chest.add("pos", pos(x - cx, y, z - cz));
                        JsonArray items = new JsonArray();
                        for (int s = 0; s < inv.getSizeInventory(); s++) {
                            ItemStack st = inv.getStackInSlot(s);
                            if (st != null && st.getItem() != null) {
                                JsonObject it = new JsonObject();
                                it.addProperty("item", Item.itemRegistry.getNameForObject(st.getItem()));
                                it.addProperty("meta", st.getItemDamage());
                                it.addProperty("count", st.stackSize);
                                items.add(it);
                            }
                        }
                        chest.add("items", items);
                        chests.add(chest);
                    }
                }
            }
        }
        // every entity is new: the list was cleared before building
        JsonArray entities = new JsonArray();
        for (Object o : new ArrayList<Object>(world.loadedEntityList)) {
            Entity e = (Entity) o;
            JsonObject en = new JsonObject();
            en.addProperty("id", EntityList.getEntityString(e));
            JsonArray p = new JsonArray();
            p.add(new JsonPrimitive(Math.round((e.posX - cx) * 100) / 100.0));
            p.add(new JsonPrimitive(Math.round(e.posY * 100) / 100.0));
            p.add(new JsonPrimitive(Math.round((e.posZ - cz) * 100) / 100.0));
            en.add("pos", p);
            for (String f : new String[] { "spawnClass1", "spawnClass2" }) {
                Object k = field(e, f);
                if (k instanceof Class<?> kc) {
                    en.addProperty(f, entityName(kc));
                }
            }
            for (String f : new String[] { "spawnCap", "checkHorizontalRange", "spawnHorizontalRange", "homeRange",
                    "spawnInterval", "mountSetting", "blockEnemySpawns" }) {
                Object v = field(e, f);
                if (v instanceof Number n) {
                    en.addProperty(f, n);
                }
            }
            entities.add(en);
            e.setDead();
        }
        if (changed.isEmpty()) {
            if (seed == 0) {
                errors.add(new JsonPrimitive("structure " + c.getSimpleName() + " placed nothing (returned "
                        + placed + ")"));
            }
            return null;
        }
        for (int[] b : changed) {
            JsonArray a = new JsonArray();
            a.add(new JsonPrimitive(b[0] - cx));
            a.add(new JsonPrimitive(b[1]));
            a.add(new JsonPrimitive(b[2] - cz));
            a.add(new JsonPrimitive(b[3]));
            blocks.add(a);
        }
        JsonObject o = new JsonObject();
        o.addProperty("seed", seed);
        o.addProperty("ground", GROUND);
        o.add("min", pos(minX - cx, minY, minZ - cz));
        o.add("max", pos(maxX - cx, maxY, maxZ - cz));
        JsonArray pal = new JsonArray();
        for (String k : palette.keySet()) {
            pal.add(new JsonPrimitive(k));
        }
        o.add("palette", pal);
        o.add("blocks", blocks);
        o.add("chests", chests);
        o.add("entities", entities);
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

    private static String entityName(Class<?> c) {
        try {
            Class<?> reg = Class.forName("lotr.common.entity.LOTREntities");
            Object name = reg.getMethod("getStringFromClass", Class.class).invoke(null, c);
            if (name != null) {
                return name.toString();
            }
        } catch (Throwable ignored) {
            // not a LOTR entity
        }
        return c.getName();
    }

    private static void setField(Object o, String name, Object value) {
        for (Class<?> c = o.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                f.set(o, value);
                return;
            } catch (NoSuchFieldException e) {
                // look in the superclass
            } catch (Throwable t) {
                return;
            }
        }
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

    private void write() throws Exception {
        JsonObject root = new JsonObject();
        root.add("structures", structures);
        root.add("errors", errors);
        Gson gson = new Gson(); // compact: this file is large
        try (Writer w = new OutputStreamWriter(new java.util.zip.GZIPOutputStream(
                new FileOutputStream(new File(out, "structures.json.gz"))), StandardCharsets.UTF_8)) {
            gson.toJson(root, w);
        }
        System.out.println("[lotrexport] wrote structures.json.gz: " + structures.entrySet().size()
                + " structures, " + errors.size() + " errors");
    }
}
