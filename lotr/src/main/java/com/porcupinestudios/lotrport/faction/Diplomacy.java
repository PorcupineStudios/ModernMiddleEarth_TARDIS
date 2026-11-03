package com.porcupinestudios.lotrport.faction;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.ringbearer.api.history.HistoryEntry;
import com.porcupinestudios.ringbearer.api.history.HistoryLog;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Live diplomacy, saved with the world: relations changed in play (wars declared, pacts made) override the data
 * defaults, and vassals (a faction or realm sworn to another). Every change goes into the history log, so a past can
 * be rebuilt with the relations it had.
 */
public final class Diplomacy extends SavedData {
    public static final String FILE = "lotr_port_diplomacy";
    public static final int DATA_VERSION = 1;
    public static final ResourceLocation RELATION_HISTORY = LotrPort.id("relation_change");
    public static final ResourceLocation VASSAL_HISTORY = LotrPort.id("vassal_change");

    private static final SavedData.Factory<Diplomacy> FACTORY =
            new SavedData.Factory<>(Diplomacy::new, Diplomacy::load, null);

    private final Map<String, Relation> overrides = new HashMap<>();
    private final Map<ResourceLocation, ResourceLocation> overlords = new HashMap<>();

    public static Diplomacy get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE);
    }

    /** Order-independent key for a pair of factions. */
    static String pair(ResourceLocation a, ResourceLocation b) {
        String x = a.toString();
        String y = b.toString();
        return x.compareTo(y) < 0 ? x + "|" + y : y + "|" + x;
    }

    public Optional<Relation> override(ResourceLocation a, ResourceLocation b) {
        return Optional.ofNullable(overrides.get(pair(a, b)));
    }

    /** Sets the relation between two factions in play. {@code null} goes back to the data default. */
    public void setRelation(MinecraftServer server, ResourceLocation a, ResourceLocation b, Relation relation) {
        Relation old = overrides.get(pair(a, b));
        if (relation == null) {
            overrides.remove(pair(a, b));
        } else {
            overrides.put(pair(a, b), relation);
        }
        setDirty();
        CompoundTag tag = new CompoundTag();
        tag.putString("a", a.toString());
        tag.putString("b", b.toString());
        tag.putString("old", old == null ? "default" : old.getSerializedName());
        tag.putString("new", relation == null ? "default" : relation.getSerializedName());
        record(server, RELATION_HISTORY, tag);
    }

    public Optional<ResourceLocation> overlord(ResourceLocation vassal) {
        return Optional.ofNullable(overlords.get(vassal));
    }

    public Map<ResourceLocation, ResourceLocation> overlords() {
        return Map.copyOf(overlords);
    }

    /** Swears {@code vassal} to {@code overlord}; {@code null} frees it. */
    public void setOverlord(MinecraftServer server, ResourceLocation vassal, ResourceLocation overlord) {
        ResourceLocation old = overlord == null ? overlords.remove(vassal) : overlords.put(vassal, overlord);
        setDirty();
        CompoundTag tag = new CompoundTag();
        tag.putString("vassal", vassal.toString());
        tag.putString("old", old == null ? "" : old.toString());
        tag.putString("new", overlord == null ? "" : overlord.toString());
        record(server, VASSAL_HISTORY, tag);
    }

    private static void record(MinecraftServer server, ResourceLocation type, CompoundTag tag) {
        HistoryLog.get(server).append(new HistoryEntry(server.overworld().getGameTime(), type, Level.OVERWORLD,
                Optional.empty(), tag));
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("dataVersion", DATA_VERSION);
        ListTag relations = new ListTag();
        overrides.forEach((key, relation) -> {
            String[] ab = key.split("\\|", 2);
            CompoundTag r = new CompoundTag();
            r.putString("a", ab[0]);
            r.putString("b", ab[1]);
            r.putString("relation", relation.getSerializedName());
            relations.add(r);
        });
        tag.put("relations", relations);
        ListTag vassals = new ListTag();
        overlords.forEach((vassal, overlord) -> {
            CompoundTag v = new CompoundTag();
            v.putString("vassal", vassal.toString());
            v.putString("overlord", overlord.toString());
            vassals.add(v);
        });
        tag.put("vassals", vassals);
        return tag;
    }

    public static Diplomacy load(CompoundTag tag, HolderLookup.Provider registries) {
        Diplomacy d = new Diplomacy();
        for (Tag t : tag.getList("relations", Tag.TAG_COMPOUND)) {
            CompoundTag r = (CompoundTag) t;
            Relation relation = Relation.byName(r.getString("relation"));
            ResourceLocation a = ResourceLocation.tryParse(r.getString("a"));
            ResourceLocation b = ResourceLocation.tryParse(r.getString("b"));
            if (relation != null && a != null && b != null) {
                d.overrides.put(pair(a, b), relation);
            }
        }
        for (Tag t : tag.getList("vassals", Tag.TAG_COMPOUND)) {
            CompoundTag v = (CompoundTag) t;
            ResourceLocation vassal = ResourceLocation.tryParse(v.getString("vassal"));
            ResourceLocation overlord = ResourceLocation.tryParse(v.getString("overlord"));
            if (vassal != null && overlord != null) {
                d.overlords.put(vassal, overlord);
            }
        }
        return d;
    }
}
