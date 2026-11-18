package com.porcupinestudios.lotrport.border;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.colony.Colonies;
import com.porcupinestudios.ringbearer.api.history.HistoryEntry;
import com.porcupinestudios.ringbearer.api.history.HistoryLog;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

/**
 * The borders through time: every change to a saved layer ({@code claims}, {@code marks}) is a delta in the world
 * {@link HistoryLog} ({@code border_layer}: the pixels set, per holder, and the pixels cleared), so the map at any past
 * tick is rebuilt by replaying them ({@link #at}), the original map underneath being data. A {@code baseline} entry
 * holds a whole layer: written when a world first gets layer deltas (borders made before 2026-11-18 had only the
 * summary lines) and after a rewind. Ticks before the first baseline show the original map.
 *
 * <p>Time travel uses this two ways (shared API): a player visiting the past sees the map of that tick
 * ({@code TimeProvider}, {@code TimeShiftEvent}; {@link BorderNet}), and a rewound world puts its borders back
 * ({@code HistoryRewindEvent}; {@link Borders#restore}).
 */
public final class BorderHistory {
    public static final ResourceLocation LAYER_HISTORY = LotrPort.id("border_layer");
    public static final String CLAIMS = "claims";
    public static final String MARKS = "marks";

    /** Both saved layers at one tick: pixel index -> holder ({@link Borders#NOBODY} in marks = nobody's land). */
    public record Layers(Map<Integer, ResourceLocation> claims, Map<Integer, ResourceLocation> marks) {
        public static final Layers EMPTY = new Layers(Map.of(), Map.of());

        /** Who held a pixel: a mark, else a claim, else the original map. */
        public Optional<ResourceLocation> owner(int pixel) {
            ResourceLocation m = marks.get(pixel);
            if (m != null) {
                return m.equals(Borders.NOBODY) ? Optional.empty() : Optional.of(m);
            }
            ResourceLocation c = claims.get(pixel);
            return c != null ? Optional.of(c) : Borders.baseOwner(pixel);
        }

        /** Pixels whose holder differs from the original map (as {@link Borders#changes}). */
        public Map<Integer, Optional<ResourceLocation>> changes() {
            Map<Integer, Optional<ResourceLocation>> out = new HashMap<>();
            claims.forEach((i, o) -> out.put(i, Optional.of(o)));
            marks.forEach((i, o) -> out.put(i, o.equals(Borders.NOBODY) ? Optional.empty() : Optional.of(o)));
            return out;
        }
    }

    private BorderHistory() {}

    /**
     * Writes what changed in one layer from {@code before} to {@code after}; a {@code baseline} writes the whole of
     * {@code after} instead. Nothing is written when nothing changed (and it isn't a baseline).
     */
    static void logDelta(MinecraftServer server, String layer, Map<Integer, ResourceLocation> before,
            Map<Integer, ResourceLocation> after, boolean baseline) {
        Map<ResourceLocation, List<Integer>> set = new LinkedHashMap<>();
        List<Integer> cleared = new ArrayList<>();
        after.forEach((i, o) -> {
            if (baseline || !o.equals(before.get(i))) {
                set.computeIfAbsent(o, k -> new ArrayList<>()).add(i);
            }
        });
        if (!baseline) {
            before.keySet().forEach(i -> {
                if (!after.containsKey(i)) {
                    cleared.add(i);
                }
            });
            if (set.isEmpty() && cleared.isEmpty()) {
                return;
            }
        }
        CompoundTag tag = new CompoundTag();
        tag.putString("layer", layer);
        tag.putBoolean("baseline", baseline);
        CompoundTag s = new CompoundTag();
        set.forEach((o, pixels) -> s.putIntArray(o.toString(), pixels.stream().sorted().mapToInt(Integer::intValue)
                .toArray()));
        tag.put("set", s);
        tag.putIntArray("cleared", cleared.stream().sorted().mapToInt(Integer::intValue).toArray());
        HistoryLog.get(server).append(new HistoryEntry(server.overworld().getGameTime(), LAYER_HISTORY,
                Borders.dimension(server), Optional.empty(), tag));
    }

    private static long cachedTime = Long.MIN_VALUE;
    private static int cachedSize = -1;
    private static HistoryLog cachedLog;
    private static Layers cached;

    /** Both layers as they were at {@code time} (a game tick), replayed from the log. */
    public static Layers at(MinecraftServer server, long time) {
        HistoryLog log = HistoryLog.get(server);
        if (log == cachedLog && time == cachedTime && log.size() == cachedSize) {
            return cached;
        }
        Map<Integer, ResourceLocation> claims = new HashMap<>();
        Map<Integer, ResourceLocation> marks = new HashMap<>();
        for (HistoryEntry e : log.between(Long.MIN_VALUE, time, x -> x.type().equals(LAYER_HISTORY))) {
            CompoundTag tag = e.data();
            Map<Integer, ResourceLocation> layer = MARKS.equals(tag.getString("layer")) ? marks : claims;
            if (tag.getBoolean("baseline")) {
                layer.clear();
            }
            for (int i : tag.getIntArray("cleared")) {
                layer.remove(i);
            }
            CompoundTag s = tag.getCompound("set");
            for (String key : s.getAllKeys()) {
                ResourceLocation o = ResourceLocation.tryParse(key);
                if (o != null) {
                    for (int i : s.getIntArray(key)) {
                        layer.put(i, o);
                    }
                }
            }
        }
        cached = new Layers(Map.copyOf(claims), Map.copyOf(marks));
        cachedLog = log;
        cachedTime = time;
        cachedSize = log.size();
        return cached;
    }

    /** A colony as it stood at some tick, from its history (founded, transferred, disbanded). */
    public record PastColony(UUID id, String name, ResourceLocation faction, ResourceKey<Level> dimension,
            BlockPos hall, long founded) {}

    /** The colonies standing at {@code time}, oldest first, from {@code colony_*} history lines. */
    public static List<PastColony> coloniesAt(MinecraftServer server, long time) {
        Map<UUID, PastColony> out = new LinkedHashMap<>();
        for (HistoryEntry e : HistoryLog.get(server).between(Long.MIN_VALUE, time,
                x -> x.type().equals(Colonies.FOUNDED_HISTORY) || x.type().equals(Colonies.TRANSFER_HISTORY)
                        || x.type().equals(Colonies.DISBANDED_HISTORY))) {
            CompoundTag tag = e.data();
            if (!tag.hasUUID("colony")) {
                continue;
            }
            UUID id = tag.getUUID("colony");
            if (e.type().equals(Colonies.FOUNDED_HISTORY)) {
                ResourceLocation f = ResourceLocation.tryParse(tag.getString("faction"));
                if (f != null && e.pos().isPresent()) {
                    out.put(id, new PastColony(id, tag.getString("name"), f, e.dimension(), e.pos().get(),
                            e.gameTime()));
                }
            } else if (e.type().equals(Colonies.TRANSFER_HISTORY)) {
                PastColony c = out.get(id);
                ResourceLocation f = ResourceLocation.tryParse(tag.getString("faction"));
                if (c != null && f != null) {
                    out.put(id, new PastColony(id, c.name(), f, c.dimension(), c.hall(), c.founded()));
                }
            } else {
                out.remove(id);
            }
        }
        List<PastColony> list = new ArrayList<>(out.values());
        list.sort(Comparator.comparingLong(PastColony::founded));
        return list;
    }
}
