package com.porcupinestudios.lotrport.road;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.ringbearer.api.history.HistoryEntry;
import com.porcupinestudios.ringbearer.api.history.HistoryLog;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Roads that change after the world is made (2026-11-20, Aaron: the framework for roads that wear in and for roads
 * colonists build; lotr/docs/roads.md). LOTR's own roads ({@code world/map/Roads}) stay world generation and never
 * change; these are stretches on top of the land: a line of points, a {@link RoadTier}, wear, and where it came from.
 * {@link RoadPaving} lays a stretch's tier on the ground of loaded chunks (natural ground and lower road blocks only).
 * SavedData {@code lotr_port_road_works}; every tier change goes in the history log ({@code lotr_port:road_changed}).
 */
public final class RoadWorks extends SavedData {
    public static final String FILE = "lotr_port_road_works";
    public static final int DATA_VERSION = 1;
    public static final ResourceLocation HISTORY = LotrPort.id("road_changed");
    /** Who made a stretch: worn in by traffic, planned at a Waymaker station, or by command. */
    public static final String TRAFFIC = "traffic";
    public static final String WAYMAKER = "waymaker";
    public static final String COMMAND = "command";

    /** A point on a stretch's line, a block column. */
    public record Point(int x, int z) {
        public static final Codec<Point> CODEC = Codec.INT.listOf().comapFlatMap(l -> l.size() == 2
                ? com.mojang.serialization.DataResult.success(new Point(l.get(0), l.get(1)))
                : com.mojang.serialization.DataResult.error(() -> "a road point is [x, z]"), p -> List.of(p.x, p.z));
    }

    /**
     * One stretch: {@code paved} lists the chunks (as longs) its current tier has been laid in; a tier change empties
     * it so they are laid again.
     */
    public static final class Stretch {
        public static final Codec<Stretch> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("id").forGetter(s -> s.id),
                ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(s -> s.dimension),
                Point.CODEC.listOf().fieldOf("points").forGetter(s -> s.points),
                ResourceLocation.CODEC.fieldOf("tier").forGetter(s -> s.tier),
                Codec.LONG.optionalFieldOf("wear", 0L).forGetter(s -> s.wear),
                Codec.STRING.optionalFieldOf("source", COMMAND).forGetter(s -> s.source),
                Codec.LONG.listOf().optionalFieldOf("paved", List.of()).forGetter(s -> List.copyOf(s.paved)))
                .apply(i, Stretch::new));

        public final int id;
        public final ResourceKey<Level> dimension;
        public final List<Point> points;
        private ResourceLocation tier;
        private long wear;
        public final String source;
        final Set<Long> paved;

        Stretch(int id, ResourceKey<Level> dimension, List<Point> points, ResourceLocation tier, long wear,
                String source, List<Long> paved) {
            this.id = id;
            this.dimension = dimension;
            this.points = List.copyOf(points);
            this.tier = tier;
            this.wear = wear;
            this.source = source;
            this.paved = new HashSet<>(paved);
        }

        public ResourceLocation tier() {
            return tier;
        }

        public long wear() {
            return wear;
        }

        /** Squared distance from a column's centre to the line. */
        public double distanceSq(double x, double z) {
            double best = Double.POSITIVE_INFINITY;
            for (int k = 1; k < points.size(); k++) {
                best = Math.min(best, segmentSq(points.get(k - 1), points.get(k), x, z));
            }
            if (points.size() == 1) {
                Point p = points.get(0);
                best = (p.x + 0.5 - x) * (p.x + 0.5 - x) + (p.z + 0.5 - z) * (p.z + 0.5 - z);
            }
            return best;
        }

        private static double segmentSq(Point a, Point b, double x, double z) {
            double ax = a.x + 0.5;
            double az = a.z + 0.5;
            double dx = b.x - a.x;
            double dz = b.z - a.z;
            double len = dx * dx + dz * dz;
            double t = len == 0 ? 0 : Math.max(0, Math.min(1, ((x - ax) * dx + (z - az) * dz) / len));
            double px = ax + t * dx - x;
            double pz = az + t * dz - z;
            return px * px + pz * pz;
        }

        /** The chunks (as longs) the stretch can touch, its width included. */
        public Set<Long> chunks(double halfWidth) {
            Set<Long> out = new HashSet<>();
            int pad = (int) Math.ceil(halfWidth) + 1;
            for (int k = 0; k < points.size(); k++) {
                Point a = points.get(k);
                Point b = points.get(Math.min(k + 1, points.size() - 1));
                int x0 = Math.min(a.x, b.x) - pad;
                int x1 = Math.max(a.x, b.x) + pad;
                int z0 = Math.min(a.z, b.z) - pad;
                int z1 = Math.max(a.z, b.z) + pad;
                for (int cx = x0 >> 4; cx <= x1 >> 4; cx++) {
                    for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) {
                        if (segmentSq(a, b, (cx << 4) + 8, (cz << 4) + 8) <= (12 + halfWidth) * (12 + halfWidth)) {
                            out.add(net.minecraft.world.level.ChunkPos.asLong(cx, cz));
                        }
                    }
                }
            }
            return out;
        }
    }

    /**
     * What routes see of the road works, made on every change and safe to read off the server thread: the stretches
     * whose tier counts as a road, per dimension.
     */
    public record Snapshot(Map<ResourceKey<Level>, List<Stretch>> roads, Map<Stretch, Double> widths) {
        public static final Snapshot EMPTY = new Snapshot(Map.of(), Map.of());

        /** Whether a column in {@code dim} is on a stretch that routes treat as road. */
        public boolean road(ResourceKey<Level> dim, int x, int z) {
            for (Stretch s : roads.getOrDefault(dim, List.of())) {
                double w = widths.getOrDefault(s, 2.0) + 1;
                if (s.distanceSq(x + 0.5, z + 0.5) <= w * w) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final SavedData.Factory<RoadWorks> FACTORY = new SavedData.Factory<>(RoadWorks::new,
            RoadWorks::load, null);

    private final Map<Integer, Stretch> stretches = new LinkedHashMap<>();
    private int nextId = 1;
    private int version;
    private volatile Snapshot snapshot = Snapshot.EMPTY;
    private boolean snapshotStale = true;
    private Map<ResourceKey<Level>, Map<Long, List<Stretch>>> index;

    public static RoadWorks get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE);
    }

    public List<Stretch> all() {
        return List.copyOf(stretches.values());
    }

    public Optional<Stretch> stretch(int id) {
        return Optional.ofNullable(stretches.get(id));
    }

    /** Bumped on every change (routes plan again). */
    public int version() {
        return version;
    }

    /** The stretches whose width reaches into a chunk. */
    public List<Stretch> touching(ResourceKey<Level> dim, long chunk) {
        if (index == null) {
            Map<ResourceKey<Level>, Map<Long, List<Stretch>>> out = new HashMap<>();
            for (Stretch s : stretches.values()) {
                for (long c : s.chunks(RoadPaving.halfWidth(s))) {
                    out.computeIfAbsent(s.dimension, k -> new HashMap<>()).computeIfAbsent(c, k -> new ArrayList<>())
                            .add(s);
                }
            }
            index = out;
        }
        return index.getOrDefault(dim, Map.of()).getOrDefault(chunk, List.of());
    }

    /** The stretch a column lies on (within its tier's width), if any: where traffic would wear. */
    public Optional<Stretch> at(ResourceKey<Level> dim, int x, int z) {
        long chunk = net.minecraft.world.level.ChunkPos.asLong(x >> 4, z >> 4);
        Stretch best = null;
        double bestD = Double.POSITIVE_INFINITY;
        for (Stretch s : touching(dim, chunk)) {
            double w = RoadPaving.halfWidth(s);
            double d = s.distanceSq(x + 0.5, z + 0.5);
            if (d <= w * w && d < bestD) {
                best = s;
                bestD = d;
            }
        }
        return Optional.ofNullable(best);
    }

    /** What routes see; rebuilt after a change. */
    public Snapshot snapshot() {
        if (snapshotStale) {
            Map<ResourceKey<Level>, List<Stretch>> roads = new HashMap<>();
            Map<Stretch, Double> widths = new HashMap<>();
            for (Stretch s : stretches.values()) {
                RoadData.tier(s.tier).filter(RoadTier::routeRoad).ifPresent(t -> {
                    roads.computeIfAbsent(s.dimension, k -> new ArrayList<>()).add(s);
                    widths.put(s, t.halfWidth());
                });
            }
            snapshot = new Snapshot(Map.copyOf(roads), Map.copyOf(widths));
            snapshotStale = false;
        }
        return snapshot;
    }

    /** A new stretch along {@code points} at {@code tier}. Gives it. */
    public Stretch add(MinecraftServer server, ResourceKey<Level> dim, List<Point> points, ResourceLocation tier,
            String source) {
        Stretch s = new Stretch(nextId++, dim, points, tier, 0, source, List.of());
        stretches.put(s.id, s);
        changed();
        history(server, s, null, "added");
        RoadPaving.queue(server, s);
        return s;
    }

    public boolean remove(int id) {
        boolean gone = stretches.remove(id) != null;
        if (gone) {
            changed();
        }
        return gone;
    }

    /** Sets a stretch's tier (up or down); its chunks are laid again. */
    public void setTier(MinecraftServer server, Stretch s, ResourceLocation tier) {
        if (s.tier.equals(tier)) {
            return;
        }
        ResourceLocation old = s.tier;
        s.tier = tier;
        s.paved.clear();
        changed();
        history(server, s, old, "tier");
        RoadPaving.queue(server, s);
    }

    /**
     * Wear on a stretch (traffic going along it). It climbs to the next tier while that tier is built by traffic and
     * the wear reaches it; tiers built by waymakers it never reaches this way (Aaron: caravans beat a dirt path, cobble
     * and brick take waymakers). Gives the tier it ends on.
     */
    public ResourceLocation addWear(MinecraftServer server, Stretch s, long amount) {
        s.wear = Math.max(0, s.wear + amount);
        setDirty();
        Optional<ResourceLocation> next = RoadData.next(s.tier);
        while (next.isPresent()) {
            RoadTier t = RoadData.tier(next.get()).orElse(null);
            if (t == null || !t.byTraffic() || s.wear < t.wear()) {
                break;
            }
            setTier(server, s, next.get());
            next = RoadData.next(s.tier);
        }
        return s.tier;
    }

    void markPaved(Stretch s, long chunk) {
        if (s.paved.add(chunk)) {
            setDirty();
        }
    }

    private void changed() {
        version++;
        snapshotStale = true;
        index = null;
        setDirty();
    }

    private static void history(MinecraftServer server, Stretch s, ResourceLocation old, String what) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("stretch", s.id);
        tag.putString("what", what);
        tag.putString("tier", s.tier.toString());
        if (old != null) {
            tag.putString("from", old.toString());
        }
        tag.putString("source", s.source);
        Point p = s.points.get(0);
        tag.putInt("x", p.x());
        tag.putInt("z", p.z());
        HistoryLog.get(server).append(new HistoryEntry(server.overworld().getGameTime(), HISTORY, s.dimension,
                Optional.empty(), tag));
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("dataVersion", DATA_VERSION);
        tag.putInt("next_id", nextId);
        tag.put("stretches", Stretch.CODEC.listOf().encodeStart(NbtOps.INSTANCE, List.copyOf(stretches.values()))
                .getOrThrow());
        return tag;
    }

    public static RoadWorks load(CompoundTag tag, HolderLookup.Provider registries) {
        RoadWorks out = new RoadWorks();
        out.nextId = Math.max(1, tag.getInt("next_id"));
        Stretch.CODEC.listOf().parse(NbtOps.INSTANCE, tag.get("stretches"))
                .resultOrPartial(e -> LotrPort.LOG.error("[road works] {}", e))
                .ifPresent(l -> l.forEach(s -> out.stretches.put(s.id, s)));
        return out;
    }
}
