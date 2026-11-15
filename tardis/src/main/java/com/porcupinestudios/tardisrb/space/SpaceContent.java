package com.porcupinestudios.tardisrb.space;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Where a space level holds anything (Aaron 2026-11-15: "in outer space ... 99.99% of chunks are empty, only store
 * where [things] are and only load in those chunks"), and the space stations that makes.
 * <p>
 * A chunk is in the index while its last save had blocks or block entities, or its entities' last save had entities
 * (mixins on {@code ChunkMap.save} and {@code EntityStorage.storeEntities}). An empty chunk that isn't in the index is
 * never written to disk; one that is gets written once more (so the old blocks on disk go) and leaves the index.
 * {@link SpaceChunks} loads only indexed chunks near players, besides a small working radius. On first use in an old
 * world the index is seeded from the region files' headers, so nothing built before is lost.
 * <p>
 * <b>Stations</b> (Aaron: "if players build space stations we can save where they are (with orbit kept in mind so we
 * can move its location around the planet's center)"): indexed chunks within {@code station_gap} chunks of each other
 * are one station. Each keeps its id while it grows or shrinks, its chunks, and where it is as an orbit: the planet
 * whose layer it's over, the longitude and latitude round that planet's centre and the full-size altitude
 * ({@link Station#orbit}). {@link Station#placeAt} turns an orbit back into layer coordinates, so a station's spot can
 * be moved round the planet (by {@code period}); moving its blocks there is not built yet.
 */
public final class SpaceContent extends SavedData {
    private static final String NAME = "tardis_rb_space_chunks";

    /** Indexed chunks with blocks, to their lowest and highest non-empty section ({@code min << 16 | max & 0xFFFF}). */
    private final Long2IntOpenHashMap blocks = new Long2IntOpenHashMap();
    private final LongOpenHashSet entities = new LongOpenHashSet();
    private final List<Station> stations = new ArrayList<>();
    private int nextStation = 1;
    /** The index changed since the stations were last worked out. */
    private boolean stationsStale = true;

    /**
     * Where a station is, as an orbit round its planet's centre: longitude and latitude in radians at {@code epoch}
     * (game time), full-size altitude in blocks, and the period of one turn round the planet in ticks (0 stays put).
     */
    public record Orbit(Optional<ResourceLocation> planet, double lon, double lat, double altitude, long period,
            long epoch) {
        public static final Codec<Orbit> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.optionalFieldOf("planet").forGetter(Orbit::planet),
                Codec.DOUBLE.fieldOf("lon").forGetter(Orbit::lon),
                Codec.DOUBLE.fieldOf("lat").forGetter(Orbit::lat),
                Codec.DOUBLE.fieldOf("altitude").forGetter(Orbit::altitude),
                Codec.LONG.optionalFieldOf("period", 0L).forGetter(Orbit::period),
                Codec.LONG.optionalFieldOf("epoch", 0L).forGetter(Orbit::epoch))
                .apply(i, Orbit::new));

        /** The longitude at game time {@code time}: one turn east per period. */
        public double lonAt(long time) {
            if (period <= 0) {
                return lon;
            }
            double turned = lon + 2 * Math.PI * (double) (time - epoch) / period;
            return Math.IEEEremainder(turned, 2 * Math.PI);
        }
    }

    /**
     * A space station: its id, its chunks (absolute, as recorded), the block it's centred on and its orbit. In a level
     * without planets (the solar system zone) the orbit has no planet and {@code lon, lat, altitude} are the centre's
     * x, z and y.
     */
    public record Station(int id, List<Long> chunks, int centreX, int centreY, int centreZ, Orbit orbit) {
        public static final Codec<Station> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("id").forGetter(Station::id),
                Codec.LONG.listOf().fieldOf("chunks").forGetter(Station::chunks),
                Codec.INT.fieldOf("x").forGetter(Station::centreX),
                Codec.INT.fieldOf("y").forGetter(Station::centreY),
                Codec.INT.fieldOf("z").forGetter(Station::centreZ),
                Orbit.CODEC.fieldOf("orbit").forGetter(Station::orbit))
                .apply(i, Station::new));

        /**
         * Where the station's centre belongs at game time {@code time} in its planet's orbit layer (x, y, z of the
         * space level), or its recorded centre if it has no planet. Its chunks keep their offsets from the centre.
         */
        public double[] placeAt(Planet planet, long time) {
            if (planet == null || orbit.planet().isEmpty()) {
                return new double[] {centreX, centreY, centreZ};
            }
            double lon = orbit.lonAt(time);
            double surfaceX = planet.minX() + (lon + Math.PI) / (2 * Math.PI) * planet.width();
            double surfaceZ = planet.minZ() + (Math.PI / 2 - orbit.lat()) / Math.PI * planet.height();
            return new double[] {planet.spaceX(surfaceX), planet.spaceY(orbit.altitude()), planet.spaceZ(surfaceZ)};
        }
    }

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
        stationsStale = true;
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

    // ---- stations ----

    public List<Station> stations() {
        return List.copyOf(stations);
    }

    /**
     * Works the stations out again if the index changed: clusters of indexed chunks no more than {@code gap} chunks
     * apart. A cluster keeps the id (and orbit period) of the old station it shares most chunks with.
     */
    public void updateStations(ServerLevel level, int gap) {
        if (!stationsStale) {
            return;
        }
        stationsStale = false;
        LongOpenHashSet all = new LongOpenHashSet(blocks.keySet());
        all.addAll(entities);
        Map<Long, Integer> owner = new HashMap<>();
        for (Station s : stations) {
            for (long c : s.chunks()) {
                owner.put(c, s.id());
            }
        }
        Map<Integer, Station> old = new HashMap<>();
        stations.forEach(s -> old.put(s.id(), s));
        List<Station> fresh = new ArrayList<>();
        LongOpenHashSet done = new LongOpenHashSet();
        for (long start : all) {
            if (!done.add(start)) {
                continue;
            }
            LongList cluster = new LongArrayList();
            cluster.add(start);
            for (int i = 0; i < cluster.size(); i++) {
                long c = cluster.getLong(i);
                int cx = ChunkPos.getX(c);
                int cz = ChunkPos.getZ(c);
                for (int dx = -gap; dx <= gap; dx++) {
                    for (int dz = -gap; dz <= gap; dz++) {
                        long n = ChunkPos.asLong(cx + dx, cz + dz);
                        if (all.contains(n) && done.add(n)) {
                            cluster.add(n);
                        }
                    }
                }
            }
            Map<Integer, Integer> votes = new HashMap<>();
            for (long c : cluster) {
                Integer id = owner.get(c);
                if (id != null) {
                    votes.merge(id, 1, Integer::sum);
                }
            }
            int id = votes.entrySet().stream().filter(e -> fresh.stream().noneMatch(s -> s.id() == e.getKey()))
                    .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(nextStation++);
            fresh.add(station(level, id, cluster, old.get(id)));
        }
        stations.clear();
        stations.addAll(fresh);
        setDirty();
    }

    private Station station(ServerLevel level, int id, LongList chunks, Station before) {
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (long c : chunks) {
            minX = Math.min(minX, ChunkPos.getX(c));
            maxX = Math.max(maxX, ChunkPos.getX(c));
            minZ = Math.min(minZ, ChunkPos.getZ(c));
            maxZ = Math.max(maxZ, ChunkPos.getZ(c));
            if (blocks.containsKey(c)) {
                int r = blocks.get(c);
                minY = Math.min(minY, r >> 16);
                maxY = Math.max(maxY, (short) (r & 0xFFFF));
            }
        }
        int x = (minX + maxX + 1) * 8;
        int z = (minZ + maxZ + 1) * 8;
        int y = minY == Integer.MAX_VALUE ? level.getSeaLevel() : (minY + maxY + 1) * 8;
        long period = before == null ? 0 : before.orbit().period();
        long now = level.getGameTime();
        Orbit orbit = Planets.under(level.getServer(), level.dimension(), x, z)
                .map(p -> new Orbit(Optional.of(p.id()), p.lon(p.surfaceX(x)), p.lat(p.surfaceZ(z)),
                        p.altitude(y), period, now))
                .orElse(new Orbit(Optional.empty(), x, z, y, period, now));
        return new Station(id, List.copyOf(chunks), x, y, z, orbit);
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
        tag.putInt("next_station", nextStation);
        Station.CODEC.listOf().encodeStart(NbtOps.INSTANCE, stations).resultOrPartial(TardisRB.LOG::error)
                .ifPresent(t -> tag.put("stations", t));
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
        c.nextStation = Math.max(1, tag.getInt("next_station"));
        if (tag.contains("stations", Tag.TAG_LIST)) {
            Station.CODEC.listOf().parse(NbtOps.INSTANCE, tag.get("stations")).resultOrPartial(TardisRB.LOG::error)
                    .ifPresent(c.stations::addAll);
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
