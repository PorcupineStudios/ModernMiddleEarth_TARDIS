package com.porcupinestudios.lotrport.tree;

import com.mojang.serialization.Codec;
import com.porcupinestudios.lotrport.LotrPort;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;

/**
 * The trees grown in one level, by the chunk their trunk stands in ({@code lotr_port_trees}, codecs, a
 * {@code dataVersion}). World generation writes here from worker threads, so the store is opened on the main thread
 * when the level loads and kept in {@link #OPEN}; all access goes through synchronized methods.
 */
public final class TreeRecords extends SavedData {
    public static final String FILE = "lotr_port_trees";
    public static final int DATA_VERSION = 1;
    private static final Codec<Map<String, List<TreeRecord>>> CHUNKS =
            Codec.unboundedMap(Codec.STRING, TreeRecord.CODEC.listOf());
    private static final SavedData.Factory<TreeRecords> FACTORY = new SavedData.Factory<>(TreeRecords::new,
            TreeRecords::load, null);
    private static final Map<ResourceKey<Level>, TreeRecords> OPEN = new ConcurrentHashMap<>();

    private final Map<Long, List<TreeRecord>> byChunk = new ConcurrentHashMap<>();

    public static void register() {
        NeoForge.EVENT_BUS.addListener((LevelEvent.Load event) -> {
            if (event.getLevel() instanceof ServerLevel level) {
                OPEN.put(level.dimension(), level.getDataStorage().computeIfAbsent(FACTORY, FILE));
            }
        });
        NeoForge.EVENT_BUS.addListener((LevelEvent.Unload event) -> {
            if (event.getLevel() instanceof ServerLevel level) {
                OPEN.remove(level.dimension());
            }
        });
    }

    /** The level's store, or null before it has loaded (then nothing is recorded). */
    public static TreeRecords of(ServerLevel level) {
        return OPEN.get(level.dimension());
    }

    public synchronized void add(TreeRecord tree) {
        byChunk.computeIfAbsent(ChunkPos.asLong(tree.trunk()), k -> new ArrayList<>()).add(tree);
        setDirty();
    }

    public synchronized boolean remove(TreeRecord tree) {
        List<TreeRecord> list = byChunk.get(ChunkPos.asLong(tree.trunk()));
        boolean removed = list != null && list.remove(tree);
        if (list != null && list.isEmpty()) {
            byChunk.remove(ChunkPos.asLong(tree.trunk()));
        }
        if (removed) {
            setDirty();
        }
        return removed;
    }

    /** Trees whose box holds the position (a tree's crown can reach into the next chunks). */
    public synchronized List<TreeRecord> containing(BlockPos pos) {
        List<TreeRecord> out = new ArrayList<>();
        ChunkPos c = new ChunkPos(pos);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (TreeRecord t : byChunk.getOrDefault(ChunkPos.asLong(c.x + dx, c.z + dz), List.of())) {
                    if (t.box().isInside(pos)) {
                        out.add(t);
                    }
                }
            }
        }
        return out;
    }

    /** Trees whose trunk stands within the radius (blocks) of a position: what a woodcutter could go for. */
    public synchronized List<TreeRecord> near(BlockPos pos, int radius) {
        List<TreeRecord> out = new ArrayList<>();
        ChunkPos c = new ChunkPos(pos);
        int r = (radius >> 4) + 1;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (TreeRecord t : byChunk.getOrDefault(ChunkPos.asLong(c.x + dx, c.z + dz), List.of())) {
                    if (t.trunk().distSqr(pos) <= (double) radius * radius) {
                        out.add(t);
                    }
                }
            }
        }
        return out;
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("dataVersion", DATA_VERSION);
        Map<String, List<TreeRecord>> chunks = new java.util.TreeMap<>();
        byChunk.forEach((k, v) -> chunks.put(Long.toString(k), List.copyOf(v)));
        tag.put("chunks", CHUNKS.encodeStart(NbtOps.INSTANCE, chunks).getOrThrow());
        return tag;
    }

    public static TreeRecords load(CompoundTag tag, HolderLookup.Provider registries) {
        TreeRecords out = new TreeRecords();
        if (tag.contains("chunks")) {
            CHUNKS.parse(NbtOps.INSTANCE, tag.get("chunks")).resultOrPartial(e -> LotrPort.LOG.error("[trees] {}", e))
                    .ifPresent(m -> m.forEach((k, v) -> out.byChunk.put(Long.parseLong(k), new ArrayList<>(v))));
        }
        return out;
    }
}
