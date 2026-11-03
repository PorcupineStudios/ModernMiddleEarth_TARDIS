package com.porcupinestudios.lotrport.npc;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.ringbearer.api.history.HistoryEntry;
import com.porcupinestudios.ringbearer.api.history.HistoryLog;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Saved records of the NPCs that matter: hired units now (named, recruited and colonists later). Most NPCs never get
 * one, so a random orc costs nothing. A record outlives its NPC: a death is noted, not erased, for history.
 */
public final class People extends SavedData {
    public static final String FILE = "lotr_port_people";
    public static final int DATA_VERSION = 1;
    public static final ResourceLocation HIRED_HISTORY = LotrPort.id("npc_hired");
    public static final ResourceLocation DIED_HISTORY = LotrPort.id("npc_died");

    private static final SavedData.Factory<People> FACTORY = new SavedData.Factory<>(People::new, People::load, null);

    /**
     * @param entity the NPC's entity UUID
     * @param since game time the record was made
     */
    public record Person(UUID entity, ResourceLocation type, String name, Optional<ResourceLocation> faction,
            Optional<UUID> owner, long since, boolean alive) {
        public Person died() {
            return new Person(entity, type, name, faction, owner, since, false);
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putUUID("entity", entity);
            t.putString("type", type.toString());
            t.putString("name", name);
            faction.ifPresent(f -> t.putString("faction", f.toString()));
            owner.ifPresent(o -> t.putUUID("owner", o));
            t.putLong("since", since);
            t.putBoolean("alive", alive);
            return t;
        }

        static Optional<Person> load(CompoundTag t) {
            ResourceLocation type = ResourceLocation.tryParse(t.getString("type"));
            if (type == null || !t.hasUUID("entity")) {
                return Optional.empty();
            }
            return Optional.of(new Person(t.getUUID("entity"), type, t.getString("name"),
                    Optional.ofNullable(ResourceLocation.tryParse(t.getString("faction"))).filter(f -> t.contains(
                            "faction")),
                    t.hasUUID("owner") ? Optional.of(t.getUUID("owner")) : Optional.empty(), t.getLong("since"),
                    !t.contains("alive") || t.getBoolean("alive")));
        }
    }

    private final Map<UUID, Person> people = new LinkedHashMap<>();

    public static People get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE);
    }

    public Optional<Person> get(UUID entity) {
        return Optional.ofNullable(people.get(entity));
    }

    public Collection<Person> all() {
        return people.values();
    }

    /** Records a newly hired NPC and writes it to the history log. */
    public Person hired(MinecraftServer server, LotrNpc npc, UUID owner) {
        Person p = new Person(npc.getUUID(), npc.typeId(), npc.personName(), npc.faction(), Optional.of(owner),
                server.overworld().getGameTime(), true);
        people.put(p.entity(), p);
        setDirty();
        log(server, HIRED_HISTORY, npc.level().dimension(), npc.blockPosition(), p.save());
        return p;
    }

    public void died(MinecraftServer server, LotrNpc npc) {
        Person p = people.get(npc.getUUID());
        if (p == null || !p.alive()) {
            return;
        }
        people.put(p.entity(), p.died());
        setDirty();
        log(server, DIED_HISTORY, npc.level().dimension(), npc.blockPosition(), p.save());
    }

    private static void log(MinecraftServer server, ResourceLocation type,
            net.minecraft.resources.ResourceKey<Level> dim, BlockPos pos, CompoundTag tag) {
        HistoryLog.get(server).append(new HistoryEntry(server.overworld().getGameTime(), type, dim,
                Optional.of(pos), tag));
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("dataVersion", DATA_VERSION);
        ListTag list = new ListTag();
        people.values().forEach(p -> list.add(p.save()));
        tag.put("people", list);
        return tag;
    }

    public static People load(CompoundTag tag, HolderLookup.Provider registries) {
        People people = new People();
        for (Tag e : tag.getList("people", Tag.TAG_COMPOUND)) {
            Person.load((CompoundTag) e).ifPresent(p -> people.people.put(p.entity(), p));
        }
        return people;
    }
}
