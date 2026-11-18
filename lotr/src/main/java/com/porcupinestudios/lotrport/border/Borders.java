package com.porcupinestudios.lotrport.border;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.colony.Colonies;
import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.faction.Realms;
import com.porcupinestudios.lotrport.society.Society;
import com.porcupinestudios.lotrport.society.SocietyEvent;
import com.porcupinestudios.lotrport.world.LotrDimensions;
import com.porcupinestudios.lotrport.world.map.MiddleEarthMap;
import com.porcupinestudios.ringbearer.api.history.HistoryEntry;
import com.porcupinestudios.ringbearer.api.history.HistoryLog;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Borders in Middle-earth: who holds each map pixel (128 blocks). Three layers, read top down:
 * <ol>
 * <li>{@link #marks}: land taken by conquest ({@link Conquest}) or set by hand ({@link #mark}), kept until taken
 *     back or cleared;</li>
 * <li>{@link #claims}: what colonies settle, worked out from them ({@link TerritoryMath}) on every founding, transfer
 *     and disbanding and every {@code recompute_ticks}: unclaimed land and their own people's only, mountains only
 *     for big, near colonies;</li>
 * <li>the original map ({@link BorderData#base}: biome -> faction), which play never changes.</li>
 * </ol>
 * Both saved layers hold only pixels that differ from the one below, so the original map stays as it was and the
 * changes are forwarded onto it. World generation never reads any of this: a conquered Shire still generates as the
 * Shire. What changes is the player map ({@link BorderNet}), spawning ({@link TerritorySpawns}) and conditions.
 * Changes are written to history twice: {@code border_changed} (who took how much, readable) and
 * {@code border_layer} (the exact layer deltas, {@link BorderHistory}), so the map of any past tick can be rebuilt
 * and a rewound world puts its borders back ({@code HistoryRewindEvent}, {@link #restore}).
 */
public final class Borders extends SavedData {
    public static final String FILE = "lotr_port_borders";
    public static final int DATA_VERSION = 1;
    public static final ResourceLocation CHANGED_HISTORY = LotrPort.id("border_changed");

    private static final SavedData.Factory<Borders> FACTORY = new SavedData.Factory<>(Borders::new, Borders::load, null);

    /** Pixel index ({@code pz * width + px}) -> holder, from colonies; only where it differs from the base. */
    private final Map<Integer, ResourceLocation> claims = new HashMap<>();
    /** Pixel index -> holder set by hand or war; only where it differs from what's below. */
    private final Map<Integer, ResourceLocation> marks = new HashMap<>();
    /** Bumped on every change, for the client sync and caches. */
    private int version;
    private boolean pending = true;
    /** The layers as last written to history as deltas; each change logs only what differs from these. */
    private final Map<Integer, ResourceLocation> loggedClaims = new HashMap<>();
    private final Map<Integer, ResourceLocation> loggedMarks = new HashMap<>();
    /** Whether this world's layers have a baseline in the history (worlds from before layer deltas get one once). */
    private boolean baselined;

    private static Object baseKey;
    private static ResourceLocation[] baseByBiome = new ResourceLocation[0];

    public static Borders get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE);
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((SocietyEvent.ColonyFounded e) -> get(e.server()).pending = true);
        NeoForge.EVENT_BUS.addListener((SocietyEvent.ColonyDisbanded e) -> get(e.server()).pending = true);
        NeoForge.EVENT_BUS.addListener((SocietyEvent.ColonyTransferred e) -> get(e.server()).pending = true);
        NeoForge.EVENT_BUS.addListener((com.porcupinestudios.ringbearer.api.history.HistoryRewindEvent e) ->
                get(e.server()).restore(e.server(), BorderHistory.at(e.server(), e.toTime())));
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> {
            MinecraftServer server = e.getServer();
            Borders b = get(server);
            if (!b.baselined) {
                b.baseline(server);
            }
            int every = Math.max(20, BorderData.settings().recomputeTicks());
            if (b.pending || server.overworld().getGameTime() % every == 0) {
                b.recompute(server);
            }
        });
    }

    /** Writes both layers whole to history (a world's first, and after a rewind), so replays start from them. */
    private void baseline(MinecraftServer server) {
        BorderHistory.logDelta(server, BorderHistory.CLAIMS, Map.of(), claims, true);
        BorderHistory.logDelta(server, BorderHistory.MARKS, Map.of(), marks, true);
        loggedClaims.clear();
        loggedClaims.putAll(claims);
        loggedMarks.clear();
        loggedMarks.putAll(marks);
        baselined = true;
        setDirty();
    }

    /** The saved layers now (copies). */
    public BorderHistory.Layers layers() {
        return new BorderHistory.Layers(Map.copyOf(claims), Map.copyOf(marks));
    }

    /**
     * Puts the borders back as they were (a rewound world: {@code HistoryRewindEvent}): both layers replaced, a new
     * baseline written so later replays start from them, clients told. The colonies' own rewind decides the next
     * recompute of claims.
     */
    public void restore(MinecraftServer server, BorderHistory.Layers layers) {
        claims.clear();
        claims.putAll(layers.claims());
        marks.clear();
        marks.putAll(layers.marks());
        version++;
        baseline(server);
        BorderNet.sendToAll(server);
    }

    /** Asks for a recompute on the next tick (a realm was founded, data reloaded...). */
    public void touch() {
        pending = true;
    }

    public int version() {
        return version;
    }

    /**
     * The dimension borders are about: Middle-earth's level (the overworld in a Middle-earth world), or its dimension
     * key when the level isn't loaded (the GameTest server skips datapack dimensions; colonies only store the key).
     */
    public static net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension(
            MinecraftServer server) {
        ServerLevel me = LotrDimensions.middleEarth(server);
        return me != null ? me.dimension() : LotrDimensions.MIDDLE_EARTH;
    }

    // ---- reading

    /** The original holder of a biome by its map index (cached per data reload). */
    static Optional<ResourceLocation> baseOfBiome(int biomeIndex) {
        Object key = BorderData.baseVersion();
        if (key != baseKey) {
            MiddleEarthMap map = MiddleEarthMap.get();
            Map<ResourceLocation, ResourceLocation> base = BorderData.base();
            ResourceLocation[] arr = new ResourceLocation[map.biomes().size()];
            for (int i = 0; i < arr.length; i++) {
                arr[i] = base.get(ResourceLocation.parse(map.biomes().get(i).id()));
            }
            baseByBiome = arr;
            baseKey = key;
        }
        return biomeIndex >= 0 && biomeIndex < baseByBiome.length ? Optional.ofNullable(baseByBiome[biomeIndex])
                : Optional.empty();
    }

    /** The pixel index of a block, or -1 off the map. */
    public static int pixelOf(double blockX, double blockZ) {
        MiddleEarthMap map = MiddleEarthMap.get();
        int px = (int) Math.floor(map.pixelX(blockX));
        int pz = (int) Math.floor(map.pixelZ(blockZ));
        if (px < 0 || pz < 0 || px >= map.width() || pz >= map.height()) {
            return -1;
        }
        return pz * map.width() + px;
    }

    /** The original map's holder of a pixel. */
    public static Optional<ResourceLocation> baseOwner(int pixel) {
        if (pixel < 0) {
            return Optional.empty();
        }
        MiddleEarthMap map = MiddleEarthMap.get();
        return baseOfBiome(map.pixelIndex(pixel % map.width(), pixel / map.width()));
    }

    /** Who holds a pixel now: a mark, else a colony's claim, else the original map. */
    public Optional<ResourceLocation> owner(int pixel) {
        if (pixel < 0) {
            return Optional.empty();
        }
        ResourceLocation m = marks.get(pixel);
        if (m != null) {
            return m.equals(NOBODY) ? Optional.empty() : Optional.of(m);
        }
        ResourceLocation c = claims.get(pixel);
        return c != null ? Optional.of(c) : baseOwner(pixel);
    }

    /** Who holds the land at a block (Middle-earth only; elsewhere nobody). */
    public static Optional<ResourceLocation> ownerAt(ServerLevel level, BlockPos pos) {
        return LotrDimensions.isMiddleEarth(level) ? get(level.getServer()).owner(pixelOf(pos.getX(), pos.getZ()))
                : Optional.empty();
    }

    /** The original holder at a block, whatever happened since (Middle-earth only). */
    public static Optional<ResourceLocation> baseOwnerAt(ServerLevel level, BlockPos pos) {
        return LotrDimensions.isMiddleEarth(level) ? baseOwner(pixelOf(pos.getX(), pos.getZ())) : Optional.empty();
    }

    /** Pixels whose holder differs from the original map: marks over claims. */
    public Map<Integer, Optional<ResourceLocation>> changes() {
        Map<Integer, Optional<ResourceLocation>> out = new HashMap<>();
        claims.forEach((i, o) -> out.put(i, Optional.of(o)));
        marks.forEach((i, o) -> out.put(i, o.equals(NOBODY) ? Optional.empty() : Optional.of(o)));
        return out;
    }

    /** How many pixels each faction holds beyond its original land (claims and marks). */
    public Map<ResourceLocation, Integer> gains() {
        Map<ResourceLocation, Integer> out = new LinkedHashMap<>();
        changes().values().forEach(o -> o.ifPresent(f -> out.merge(f, 1, Integer::sum)));
        return out;
    }

    // ---- changing

    /** Holder id a mark uses for "nobody's land". */
    public static final ResourceLocation NOBODY = LotrPort.id("nobody");

    /**
     * Sets the holder of every land pixel within {@code radius} map pixels of a block (a war goal or an operator):
     * {@code faction} empty = nobody's; {@code clear} removes the marks there instead (back to claims and the
     * original map). Returns how many pixels changed.
     */
    public int mark(MinecraftServer server, BlockPos centre, double radius, Optional<ResourceLocation> faction,
            boolean clear, String reason) {
        MiddleEarthMap map = MiddleEarthMap.get();
        int c = pixelOf(centre.getX(), centre.getZ());
        if (c < 0) {
            return 0;
        }
        int cx = c % map.width();
        int cz = c / map.width();
        int r = (int) Math.ceil(radius);
        int n = 0;
        Map<Integer, Optional<ResourceLocation>> before = changes();
        for (int z = Math.max(0, cz - r); z <= Math.min(map.height() - 1, cz + r); z++) {
            for (int x = Math.max(0, cx - r); x <= Math.min(map.width() - 1, cx + r); x++) {
                if (Math.hypot(x - cx, z - cz) > radius || map.pixelIndex(x, z) == map.oceanIndex()) {
                    continue;
                }
                int i = z * map.width() + x;
                if (clear) {
                    n += marks.remove(i) != null ? 1 : 0;
                } else {
                    ResourceLocation to = faction.orElse(NOBODY);
                    if (!to.equals(marks.put(i, to))) {
                        n++;
                    }
                }
            }
        }
        if (n > 0) {
            changed(server, before, reason);
        }
        return n;
    }

    /**
     * Conquest takes one pixel for {@code faction}. A mark that would equal what lies below (the faction retaking its
     * own original land) is removed instead, so the overlay keeps only real changes.
     */
    public void take(MinecraftServer server, int pixel, ResourceLocation faction, String reason) {
        Map<Integer, Optional<ResourceLocation>> before = changes();
        ResourceLocation below = claims.get(pixel);
        Optional<ResourceLocation> under = below != null ? Optional.of(below) : baseOwner(pixel);
        if (under.equals(Optional.of(faction))) {
            if (marks.remove(pixel) == null) {
                return;
            }
        } else if (faction.equals(marks.put(pixel, faction))) {
            return;
        }
        changed(server, before, reason);
    }

    /** Works the colonies' claims out again; logs and syncs what changed. */
    public void recompute(MinecraftServer server) {
        pending = false;
        var dim = dimension(server);
        MiddleEarthMap map = MiddleEarthMap.get();
        BorderData.Settings s = BorderData.settings();
        List<ResourceLocation> owners = new ArrayList<>();
        List<TerritoryMath.Claim> list = new ArrayList<>();
        {
            for (Colony colony : Colonies.get(server).all()) {
                if (!colony.dimension().equals(dim)) {
                    continue;
                }
                int p = pixelOf(colony.hall().getX(), colony.hall().getZ());
                if (p < 0) {
                    continue;
                }
                int owner = owners.indexOf(colony.faction());
                if (owner < 0) {
                    owners.add(colony.faction());
                    owner = owners.size() - 1;
                }
                double radius = TerritoryMath.radius(Society.population(server, colony), s.claimRadius(),
                        s.peoplePerPixel(), s.maxRadius());
                list.add(new TerritoryMath.Claim(p % map.width(), p / map.width(), radius, owner, colony.founded()));
            }
        }
        double terrainCost = ConquestData.settings().claimTerrainCost();
        // settling takes only unclaimed land or the colony's own people's; others' land is taken by conquest
        Map<Integer, Integer> overlay = TerritoryMath.overlay(map.width(), map.height(), list,
                i -> baseOwner(i).map(owners::indexOf).orElse(-1),
                i -> map.pixelIndex(i % map.width(), i / map.width()) != map.oceanIndex(),
                i -> (Conquest.terrain(i) - 1) * terrainCost,
                (i, o) -> baseOwner(i).map(b -> b.equals(owners.get(o))).orElse(true));
        Map<Integer, ResourceLocation> next = new HashMap<>();
        overlay.forEach((i, o) -> next.put(i, owners.get(o)));
        if (next.equals(claims)) {
            return;
        }
        Map<Integer, Optional<ResourceLocation>> before = changes();
        claims.clear();
        claims.putAll(next);
        changed(server, before, "colonies");
    }

    private void changed(MinecraftServer server, Map<Integer, Optional<ResourceLocation>> before, String reason) {
        version++;
        setDirty();
        // the exact deltas, for rebuilding any past tick (BorderHistory)
        if (!baselined) {
            baseline(server);
        } else {
            BorderHistory.logDelta(server, BorderHistory.CLAIMS, loggedClaims, claims, false);
            BorderHistory.logDelta(server, BorderHistory.MARKS, loggedMarks, marks, false);
            loggedClaims.clear();
            loggedClaims.putAll(claims);
            loggedMarks.clear();
            loggedMarks.putAll(marks);
        }
        Map<Integer, Optional<ResourceLocation>> after = changes();
        // one history line per (from, to): how many pixels moved, and one of them
        Map<String, int[]> moves = new LinkedHashMap<>();
        java.util.Set<Integer> touched = new java.util.HashSet<>(before.keySet());
        touched.addAll(after.keySet());
        for (int i : touched) {
            Optional<ResourceLocation> from = before.containsKey(i) ? before.get(i) : baseOwner(i);
            Optional<ResourceLocation> to = after.containsKey(i) ? after.get(i) : baseOwner(i);
            if (!from.equals(to)) {
                String key = from.map(ResourceLocation::toString).orElse("") + ">" + to.map(ResourceLocation::toString)
                        .orElse("");
                int[] m = moves.computeIfAbsent(key, k -> new int[] {0, i});
                m[0]++;
            }
        }
        MiddleEarthMap map = MiddleEarthMap.get();
        long now = server.overworld().getGameTime();
        var dimension = dimension(server); // the overworld in a Middle-earth world
        moves.forEach((key, m) -> {
            String[] ft = key.split(">", -1);
            CompoundTag tag = new CompoundTag();
            tag.putString("from", ft[0]);
            tag.putString("to", ft[1]);
            tag.putInt("pixels", m[0]);
            tag.putInt("px", m[1] % map.width());
            tag.putInt("pz", m[1] / map.width());
            tag.putString("reason", reason);
            BlockPos at = map.blockOfPixel(m[1] % map.width() + 0.5, m[1] / map.width() + 0.5);
            HistoryLog.get(server).append(new HistoryEntry(now, CHANGED_HISTORY, dimension,
                    Optional.of(at), tag));
        });
        BorderNet.sendToAll(server);
    }

    /** The realms' capitals: each realm's oldest colony in Middle-earth (for map names). */
    public static Map<ResourceLocation, Colony> capitals(MinecraftServer server) {
        Map<ResourceLocation, Colony> out = new LinkedHashMap<>();
        var dim = dimension(server);
        List<Colony> all = new ArrayList<>(Colonies.get(server).all());
        all.sort((a, b) -> Long.compare(a.founded(), b.founded()));
        for (Colony c : all) {
            if (c.dimension().equals(dim) && Realms.get(server).get(c.faction()).isPresent()) {
                out.putIfAbsent(c.faction(), c);
            }
        }
        return Collections.unmodifiableMap(out);
    }

    // ---- saving

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("dataVersion", DATA_VERSION);
        tag.put("claims", write(claims));
        tag.put("marks", write(marks));
        tag.putBoolean("baselined", baselined);
        return tag;
    }

    private static ListTag write(Map<Integer, ResourceLocation> layer) {
        // grouped by holder: {"owner": id, "pixels": [..]}, readable and small
        Map<ResourceLocation, List<Integer>> by = new LinkedHashMap<>();
        layer.forEach((i, o) -> by.computeIfAbsent(o, k -> new ArrayList<>()).add(i));
        ListTag list = new ListTag();
        by.forEach((o, pixels) -> {
            CompoundTag t = new CompoundTag();
            t.putString("owner", o.toString());
            Collections.sort(pixels);
            t.putIntArray("pixels", pixels);
            list.add(t);
        });
        return list;
    }

    private static void read(ListTag list, Map<Integer, ResourceLocation> into) {
        for (Tag e : list) {
            CompoundTag t = (CompoundTag) e;
            ResourceLocation o = ResourceLocation.tryParse(t.getString("owner"));
            if (o != null) {
                for (int i : t.getIntArray("pixels")) {
                    into.put(i, o);
                }
            }
        }
    }

    public static Borders load(CompoundTag tag, HolderLookup.Provider registries) {
        Borders b = new Borders();
        // dataVersion 1 is the first format; later versions migrate here
        read(tag.getList("claims", Tag.TAG_COMPOUND), b.claims);
        read(tag.getList("marks", Tag.TAG_COMPOUND), b.marks);
        b.baselined = tag.getBoolean("baselined");
        b.loggedClaims.putAll(b.claims);
        b.loggedMarks.putAll(b.marks);
        return b;
    }
}
