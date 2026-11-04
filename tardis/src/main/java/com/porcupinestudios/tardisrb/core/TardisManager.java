package com.porcupinestudios.tardisrb.core;

import com.mojang.serialization.Codec;
import com.porcupinestudios.tardisrb.Config;
import com.porcupinestudios.tardisrb.TardisRB;
import com.porcupinestudios.tardisrb.event.TardisCreatedEvent;
import com.porcupinestudios.tardisrb.progression.Progression;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.common.NeoForge;

/** Every TARDIS record, saved once on the Overworld ({@code data/tardis_rb_tardises.dat}). */
public final class TardisManager extends SavedData {
    public static final String FILE = "tardis_rb_tardises";
    private static final SavedData.Factory<TardisManager> FACTORY =
            new SavedData.Factory<>(TardisManager::new, TardisManager::load, null);

    private final Map<UUID, TardisRecord> records = new LinkedHashMap<>();
    private boolean crashSitePlaced;
    private int nextPlot;

    public static TardisManager get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE);
    }

    public Collection<TardisRecord> all() {
        return records.values();
    }

    public Optional<TardisRecord> get(UUID id) {
        return Optional.ofNullable(records.get(id));
    }

    public List<TardisRecord> ownedBy(UUID player) {
        return records.values().stream().filter(r -> r.owner().map(player::equals).orElse(false)).toList();
    }

    /** The TARDIS a player flies: the first they own, else the first they crew. */
    public Optional<TardisRecord> of(UUID player) {
        return ownedBy(player).stream().findFirst()
                .or(() -> records.values().stream().filter(r -> r.isCrew(player)).findFirst());
    }

    /** Whether the player may own another TARDIS (server config {@code allowMultipleTardisPerPlayer}). */
    public boolean mayOwnAnother(UUID player) {
        return Config.ALLOW_MULTIPLE_PER_PLAYER.get() || ownedBy(player).isEmpty();
    }

    /**
     * Makes a TARDIS in its first stage. {@code owner} empty = unclaimed (a random wreck). Ownership limits are the
     * caller's to check with {@link #mayOwnAnother}.
     */
    public TardisRecord create(MinecraftServer server, String name, Optional<UUID> owner) {
        TardisRecord record = TardisRecord.fresh(UUID.randomUUID(), name, owner, owner.isPresent());
        add(record);
        NeoForge.EVENT_BUS.post(new TardisCreatedEvent(record));
        Progression.update(server, record);
        return record;
    }

    /** The TARDIS whose interior plot this is. */
    public Optional<TardisRecord> byPlot(int plot) {
        return plot < 0 ? Optional.empty() : records.values().stream().filter(r -> r.plot() == plot).findFirst();
    }

    /** Gives the TARDIS a fresh plot, leaving its old one (rebuilding an interior on a new layout). */
    public int newPlot(TardisRecord record) {
        record.setPlot(nextPlot++);
        setDirty();
        return record.plot();
    }

    /** Gives the TARDIS the next free interior plot, if it has none; returns its plot. */
    public int assignPlot(TardisRecord record) {
        if (record.plot() < 0) {
            record.setPlot(nextPlot++);
            setDirty();
        }
        return record.plot();
    }

    /** Whether this world's crash site was already made (it is made once, on the first start). */
    public boolean crashSitePlaced() {
        return crashSitePlaced;
    }

    public void markCrashSitePlaced() {
        crashSitePlaced = true;
        setDirty();
    }

    public void remove(UUID id) {
        if (records.remove(id) != null) {
            setDirty();
        }
    }

    private void add(TardisRecord record) {
        record.attach(this::setDirty);
        records.put(record.id(), record);
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("data_version", TardisRecord.DATA_VERSION);
        tag.putBoolean("crash_site_placed", crashSitePlaced);
        tag.putInt("next_plot", nextPlot);
        tag.put("tardises", Codec.list(TardisRecord.CODEC)
                .encodeStart(NbtOps.INSTANCE, List.copyOf(records.values())).getOrThrow());
        return tag;
    }

    public static TardisManager load(CompoundTag tag, HolderLookup.Provider registries) {
        TardisManager out = new TardisManager();
        out.crashSitePlaced = tag.getBoolean("crash_site_placed");
        out.nextPlot = tag.getInt("next_plot");
        if (tag.contains("tardises")) {
            // Version 1 is the first format; later versions add migration steps here, oldest first.
            Codec.list(TardisRecord.CODEC).parse(NbtOps.INSTANCE, tag.get("tardises"))
                    .resultOrPartial(e -> TardisRB.LOG.error("[tardises] {}", e))
                    .ifPresent(list -> list.forEach(out::add));
        }
        return out;
    }
}
