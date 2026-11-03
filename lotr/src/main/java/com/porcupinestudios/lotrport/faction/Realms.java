package com.porcupinestudios.lotrport.faction;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.ringbearer.api.history.HistoryEntry;
import com.porcupinestudios.ringbearer.api.history.HistoryLog;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Realms: factions founded in play by a player or an AI ruler, saved with the world. A realm is a faction like any
 * other (alignment, relations, vassals); its colonies arrive with the colonies phase. Ids are
 * {@code lotr_port:realm/<number>}.
 */
public final class Realms extends SavedData {
    public static final String FILE = "lotr_port_realms";
    public static final int DATA_VERSION = 1;
    public static final ResourceLocation FOUNDED_HISTORY = LotrPort.id("realm_founded");

    private static final SavedData.Factory<Realms> FACTORY = new SavedData.Factory<>(Realms::new, Realms::load, null);

    /**
     * @param owner the player who rules it; empty for an AI ruler
     * @param culture the great power it follows (its people and looks), if any
     */
    public record Realm(ResourceLocation id, String name, Optional<UUID> owner, Optional<ResourceLocation> culture,
            int color) {
        public boolean ai() {
            return owner.isEmpty();
        }
    }

    private final Map<ResourceLocation, Realm> realms = new LinkedHashMap<>();
    private int nextId = 1;

    public static Realms get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE);
    }

    public Collection<Realm> all() {
        return realms.values();
    }

    public Optional<Realm> get(ResourceLocation id) {
        return Optional.ofNullable(realms.get(id));
    }

    public Optional<Realm> ownedBy(UUID player) {
        return realms.values().stream().filter(r -> r.owner().filter(player::equals).isPresent()).findFirst();
    }

    public Realm found(MinecraftServer server, String name, Optional<UUID> owner, Optional<ResourceLocation> culture,
            int color) {
        Realm realm = new Realm(LotrPort.id("realm/" + nextId++), name, owner, culture, color);
        realms.put(realm.id(), realm);
        setDirty();
        CompoundTag tag = new CompoundTag();
        tag.putString("realm", realm.id().toString());
        tag.putString("name", name);
        owner.ifPresent(o -> tag.putUUID("owner", o));
        culture.ifPresent(c -> tag.putString("culture", c.toString()));
        HistoryLog.get(server).append(new HistoryEntry(server.overworld().getGameTime(), FOUNDED_HISTORY,
                Level.OVERWORLD, Optional.empty(), tag));
        return realm;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("dataVersion", DATA_VERSION);
        tag.putInt("nextId", nextId);
        ListTag list = new ListTag();
        for (Realm r : realms.values()) {
            CompoundTag t = new CompoundTag();
            t.putString("id", r.id().toString());
            t.putString("name", r.name());
            r.owner().ifPresent(o -> t.putUUID("owner", o));
            r.culture().ifPresent(c -> t.putString("culture", c.toString()));
            t.putInt("color", r.color());
            list.add(t);
        }
        tag.put("realms", list);
        return tag;
    }

    public static Realms load(CompoundTag tag, HolderLookup.Provider registries) {
        Realms realms = new Realms();
        realms.nextId = Math.max(1, tag.getInt("nextId"));
        for (Tag e : tag.getList("realms", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) e;
            ResourceLocation id = ResourceLocation.tryParse(t.getString("id"));
            if (id == null) {
                continue;
            }
            Optional<UUID> owner = t.hasUUID("owner") ? Optional.of(t.getUUID("owner")) : Optional.empty();
            Optional<ResourceLocation> culture = t.contains("culture")
                    ? Optional.ofNullable(ResourceLocation.tryParse(t.getString("culture")))
                    : Optional.empty();
            realms.realms.put(id, new Realm(id, t.getString("name"), owner, culture, t.getInt("color")));
        }
        return realms;
    }
}
