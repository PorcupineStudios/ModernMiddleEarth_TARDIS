package com.porcupinestudios.tardisrb.space;

import com.porcupinestudios.tardisrb.TardisRB;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Where a space level holds anything (Aaron 2026-11-15: "in outer space ... 99.99% of chunks are empty, only store
 * where [things] are and only load in those chunks").
 * <p>
 * A chunk is in the index while its last save had blocks or block entities, or its entities' last save had entities
 * (mixins on {@code ChunkMap.save} and {@code EntityStorage.storeEntities}). An empty chunk that isn't in the index is
 * never written to disk; one that is gets written once more (so the old blocks on disk go) and leaves the index.
 * {@link SpaceChunks} loads only indexed chunks near players, besides a small working radius. On first use in an old
 * world the index is seeded from the region files' headers, so nothing built before is lost.
 * <p>
 * Space stations are Create: Aeronautics (Sable) ships (Aaron 2026-11-15; compat/sable, docs/sable.md). Older
 * worlds saved a station list here; it is ignored.
 */
public final class SpaceContent extends SavedData {
    private static final String NAME = "tardis_rb_space_chunks";

    /** Indexed chunks with blocks, to their lowest and highest non-empty section ({@code min << 16 | max & 0xFFFF}). */
    private final Long2IntOpenHashMap blocks = new Long2IntOpenHashMap();
    private final LongOpenHashSet entities = new LongOpenHashSet();

    private SpaceContent() {}

    public static SpaceContent get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(() -> seeded(level),
                (tag, registries) -> load(tag), null), NAME);
    }

    // ---- the index ----

    /** Whether a chunk holds nothing at all: only air, no block entities. */
    public static boolean isEmpty(ChunkAccess chunk) {
        if (!chunk.getBlockEntitiesPos().isEmpty()) {
            return false;
        }
        for (LevelChunkSection section : chunk.getSections()) {
            if (!section.hasOnlyAir()) {
                return false;
            }
        }
        return true;
    }

    /**
     * A chunk is being saved: returns whether to write it. Empty chunks the index doesn't hold aren't written; an
     * indexed chunk that went empty is written once more and leaves the index.
     */
    public boolean onSave(ChunkAccess chunk) {
        long key = chunk.getPos().toLong();
        if (isEmpty(chunk)) {
            if (blocks.containsKey(key)) {
                blocks.remove(key);
                changed();
                return true;
            }
            return false;
        }
        int sections = sectionRange(chunk);
        if (!blocks.containsKey(key) || blocks.get(key) != sections) {
            blocks.put(key, sections);
            changed();
        }
        return true;
    }

    /** A chunk's entities are being saved. */
    public void onEntities(ChunkPos pos, boolean any) {
        if (any ? entities.add(pos.toLong()) : entities.remove(pos.toLong())) {
            changed();
        }
    }

    public boolean holds(long chunk) {
        return blocks.containsKey(chunk) || entities.contains(chunk);
    }

    public int size() {
        LongOpenHashSet all = new LongOpenHashSet(blocks.keySet());
        all.addAll(entities);
        return all.size();
    }

    /** Indexed chunks within {@code radius} chunks (square) of {@code centre}. */
    public LongList near(ChunkPos centre, int radius) {
        LongList out = new LongArrayList();
        long window = (2L * radius + 1) * (2L * radius + 1);
        if (window < blocks.size() + entities.size()) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    long key = ChunkPos.asLong(centre.x + dx, centre.z + dz);
                    if (holds(key)) {
                        out.add(key);
                    }
                }
            }
            return out;
        }
        LongSet seen = new LongOpenHashSet();
        for (long key : blocks.keySet()) {
            addIfNear(out, seen, key, centre, radius);
        }
        for (long key : entities) {
            addIfNear(out, seen, key, centre, radius);
        }
        return out;
    }

    private static void addIfNear(LongList out, LongSet seen, long key, ChunkPos centre, int radius) {
        if (Math.abs(ChunkPos.getX(key) - centre.x) <= radius && Math.abs(ChunkPos.getZ(key) - centre.z) <= radius
                && seen.add(key)) {
            out.add(key);
        }
    }

    private void changed() {
        setDirty();
    }

    private static int sectionRange(ChunkAccess chunk) {
        LevelChunkSection[] sections = chunk.getSections();
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int i = 0; i < sections.length; i++) {
            if (!sections[i].hasOnlyAir()) {
                int y = chunk.getSectionYFromSectionIndex(i);
                min = Math.min(min, y);
                max = Math.max(max, y);
            }
        }
        if (min == Integer.MAX_VALUE) { // block entities only
            min = max = chunk.getMinSection();
        }
        return min << 16 | (max & 0xFFFF);
    }

    // ---- saving ----

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        long[] keys = blocks.keySet().toLongArray();
        int[] ranges = new int[keys.length];
        for (int i = 0; i < keys.length; i++) {
            ranges[i] = blocks.get(keys[i]);
        }
        tag.putInt("data_version", 1);
        tag.putLongArray("blocks", keys);
        tag.putIntArray("sections", ranges);
        tag.putLongArray("entities", entities.toLongArray());
        return tag;
    }

    private static SpaceContent load(CompoundTag tag) {
        SpaceContent c = new SpaceContent();
        long[] keys = tag.getLongArray("blocks");
        int[] ranges = tag.getIntArray("sections");
        for (int i = 0; i < keys.length; i++) {
            c.blocks.put(keys[i], i < ranges.length ? ranges[i] : 0);
        }
        for (long e : tag.getLongArray("entities")) {
            c.entities.add(e);
        }
        return c;
    }

    /**
     * A first index for a level: every chunk its region files hold (from their headers; empty ones leave the index
     * the next time they're saved), with blocks anywhere in the height until then.
     */
    private static SpaceContent seeded(ServerLevel level) {
        SpaceContent c = new SpaceContent();
        Path root = DimensionType.getStorageFolder(level.dimension(),
                level.getServer().getWorldPath(LevelResource.ROOT));
        int all = level.getMinSection() << 16 | ((level.getMaxSection() - 1) & 0xFFFF);
        for (long key : stored(root.resolve("region"))) {
            c.blocks.put(key, all);
        }
        for (long key : stored(root.resolve("entities"))) {
            c.entities.add(key);
        }
        if (c.blocks.size() + c.entities.size() > 0) {
            TardisRB.LOG.info("[space] {}: indexed {} stored chunks and {} with entities from its region files",
                    level.dimension().location(), c.blocks.size(), c.entities.size());
        }
        c.setDirty();
        return c;
    }

    /** The chunks a region folder holds: each r.X.Z.mca's header lists 1024 chunk offsets, 0 for absent. */
    private static LongList stored(Path folder) {
        LongList out = new LongArrayList();
        if (!Files.isDirectory(folder)) {
            return out;
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(folder, "r.*.*.mca")) {
            for (Path file : files) {
                String[] parts = file.getFileName().toString().split("\\.");
                int rx = Integer.parseInt(parts[1]);
                int rz = Integer.parseInt(parts[2]);
                try (InputStream in = Files.newInputStream(file); DataInputStream data = new DataInputStream(in)) {
                    for (int i = 0; i < 1024 && in.available() >= 4; i++) {
                        if (data.readInt() != 0) {
                            out.add(ChunkPos.asLong(rx * 32 + (i & 31), rz * 32 + (i >> 5)));
                        }
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            TardisRB.LOG.warn("[space] couldn't read the region headers in {}: {}", folder, e.toString());
        }
        return out;
    }

    /** For tests: the index of a level whose files don't matter. */
    static Long2IntMap blocksOf(SpaceContent c) {
        return c.blocks;
    }
}
