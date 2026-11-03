package com.porcupinestudios.ringbearer.api.history;

import com.porcupinestudios.ringbearer.api.RingBearerApi;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The world history log: one journal of what happened and when, kept from the first day of the world. The TARDIS
 * recorder, colonies, AI rulers and time travel all write to it and read from it.
 *
 * <p>Saved on the overworld as {@code data/ringbearer_history.dat}. Entries are appended in time order. Splitting old
 * history into compacted segments comes later, when the first system that writes a lot of entries lands.
 */
public final class HistoryLog extends SavedData {
    public static final String FILE = "ringbearer_history";
    /** Bump when the saved layout changes, and add a migration step in {@link #load}. */
    public static final int DATA_VERSION = 1;

    private static final SavedData.Factory<HistoryLog> FACTORY =
            new SavedData.Factory<>(HistoryLog::new, HistoryLog::load, null);

    private final List<HistoryEntry> entries = new ArrayList<>();

    public static HistoryLog get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE);
    }

    public void append(HistoryEntry entry) {
        entries.add(entry);
        setDirty();
    }

    public int size() {
        return entries.size();
    }

    public List<HistoryEntry> entries() {
        return Collections.unmodifiableList(entries);
    }

    /** Entries from {@code fromTime} to {@code toTime}, both included, that match the filter. */
    public List<HistoryEntry> between(long fromTime, long toTime, Predicate<HistoryEntry> filter) {
        List<HistoryEntry> out = new ArrayList<>();
        for (HistoryEntry e : entries) {
            if (e.gameTime() >= fromTime && e.gameTime() <= toTime && filter.test(e)) {
                out.add(e);
            }
        }
        return out;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("dataVersion", DATA_VERSION);
        HistoryEntry.CODEC.listOf().encodeStart(NbtOps.INSTANCE, entries)
                .resultOrPartial(err -> RingBearerApi.LOG.error("Could not save the history log: {}", err))
                .ifPresent(list -> tag.put("entries", list));
        return tag;
    }

    public static HistoryLog load(CompoundTag tag, HolderLookup.Provider registries) {
        HistoryLog log = new HistoryLog();
        Tag list = tag.get("entries");
        if (list != null) {
            HistoryEntry.CODEC.listOf().parse(NbtOps.INSTANCE, list)
                    .resultOrPartial(err -> RingBearerApi.LOG.error("Some history entries could not be read: {}", err))
                    .ifPresent(log.entries::addAll);
        }
        return log;
    }
}
