package com.porcupinestudios.lotrport.society;

import com.mojang.serialization.Codec;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.ringbearer.api.history.HistoryEntry;
import com.porcupinestudios.ringbearer.api.history.HistoryLog;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The registry of every {@link Person}, saved in the overworld's data storage. The only place Persons change: it
 * keeps the in-memory indexes (never saved, rebuilt on load), marks the data dirty, writes history and posts
 * {@link SocietyEvent}s. Living Persons are indexed by owner; dead ones stay for history but leave the index.
 */
public final class Persons extends SavedData {
    /** Same file as the old {@code People} records, which version 1 held. */
    public static final String FILE = "lotr_port_people";
    public static final int DATA_VERSION = 2;
    public static final ResourceLocation HIRED_HISTORY = LotrPort.id("npc_hired");
    public static final ResourceLocation DIED_HISTORY = LotrPort.id("npc_died");
    public static final ResourceLocation OWNER_HISTORY = LotrPort.id("person_owner");

    private static final SavedData.Factory<Persons> FACTORY = new SavedData.Factory<>(Persons::new, Persons::load,
            null);

    private final Map<UUID, Person> persons = new LinkedHashMap<>();
    private final Map<Owner, Set<UUID>> byOwner = new HashMap<>();

    public static Persons get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE);
    }

    public Optional<Person> get(UUID id) {
        return Optional.ofNullable(persons.get(id));
    }

    /** Every record, dead ones too, oldest first. */
    public Collection<Person> all() {
        return Collections.unmodifiableCollection(persons.values());
    }

    public int size() {
        return persons.size();
    }

    /** Living Persons of this owner, in the order they were added. */
    public List<Person> ownedBy(Owner owner) {
        Set<UUID> ids = byOwner.get(owner);
        if (ids == null) {
            return List.of();
        }
        List<Person> out = new ArrayList<>(ids.size());
        for (UUID id : ids) {
            out.add(persons.get(id));
        }
        return out;
    }

    /** Living Persons of this owner carrying a component of {@code type}. */
    public List<Person> ownedBy(Owner owner, ComponentType<?> type) {
        return ownedBy(owner, p -> p.has(type));
    }

    public List<Person> ownedBy(Owner owner, Predicate<Person> filter) {
        Set<UUID> ids = byOwner.get(owner);
        if (ids == null) {
            return List.of();
        }
        List<Person> out = new ArrayList<>();
        for (UUID id : ids) {
            Person p = persons.get(id);
            if (filter.test(p)) {
                out.add(p);
            }
        }
        return out;
    }

    /** How many living Persons of this owner carry {@code type} (no list made). */
    public int count(Owner owner, ComponentType<?> type) {
        Set<UUID> ids = byOwner.get(owner);
        if (ids == null) {
            return 0;
        }
        int n = 0;
        for (UUID id : ids) {
            if (persons.get(id).has(type)) {
                n++;
            }
        }
        return n;
    }

    /**
     * Adds a new record (or replaces a dead or older one with the same id), posts {@link SocietyEvent.Added} and,
     * with a {@code history} type, writes the record to the history log.
     */
    public Person add(MinecraftServer server, Person person, Optional<ResourceLocation> history,
            Optional<GlobalPos> where) {
        Person old = persons.put(person.id(), person);
        if (old != null) {
            unindex(old);
        }
        index(person);
        setDirty();
        history.ifPresent(h -> log(server, h, person, where, new CompoundTag()));
        NeoForge.EVENT_BUS.post(new SocietyEvent.Added(server, person));
        return person;
    }

    /** Moves a Person to a new owner (hired, stationed, its colony taken). {@code reason} goes to history. */
    public void setOwner(MinecraftServer server, Person person, Owner owner, String reason) {
        Owner from = person.owner;
        if (from.equals(owner)) {
            return;
        }
        unindex(person);
        person.owner = owner;
        index(person);
        setDirty();
        CompoundTag extra = new CompoundTag();
        extra.putString("from", from.encode());
        extra.putString("reason", reason);
        log(server, OWNER_HISTORY, person, person.lastSeen, extra);
        NeoForge.EVENT_BUS.post(new SocietyEvent.OwnerChanged(server, person, from));
    }

    /** Longest name a person may be given (a rename from the citizen page). */
    public static final int MAX_NAME = 32;
    public static final ResourceLocation RENAMED_HISTORY = LotrPort.id("person_renamed");

    /**
     * Gives a person a new name (trimmed, at most {@link #MAX_NAME}): history gets {@code person_renamed} with the old
     * name, and {@link SocietyEvent.Renamed} lets the body follow. A blank or unchanged name does nothing.
     *
     * @return whether the name changed
     */
    public boolean rename(MinecraftServer server, Person person, String name) {
        String clean = name.strip();
        if (clean.length() > MAX_NAME) {
            clean = clean.substring(0, MAX_NAME).strip();
        }
        if (clean.isEmpty() || clean.equals(person.name)) {
            return false;
        }
        String old = person.name;
        person.name = clean;
        setDirty();
        CompoundTag extra = new CompoundTag();
        extra.putString("old_name", old);
        log(server, RENAMED_HISTORY, person, person.lastSeen, extra);
        NeoForge.EVENT_BUS.post(new SocietyEvent.Renamed(server, person, old));
        return true;
    }

    /** Sets (or with null removes) a component and posts {@link SocietyEvent.ComponentChanged}. */
    public <T> void set(MinecraftServer server, Person person, ComponentType<T> type, T value) {
        person.components.set(type, value);
        setDirty();
        NeoForge.EVENT_BUS.post(new SocietyEvent.ComponentChanged(server, person, type));
    }

    /** Sets a component without an event: bulk updates by a system that owns it (aging, daily pay). */
    public <T> void setQuiet(Person person, ComponentType<T> type, T value) {
        person.components.set(type, value);
        setDirty();
    }

    /** Notes where the body was last seen (cheap, no event). */
    public void seen(Person person, GlobalPos where) {
        person.lastSeen = Optional.of(where);
        setDirty();
    }

    /** Marks a Person dead: it leaves the owner index, history gets {@code npc_died}, then {@link SocietyEvent.Died}. */
    public void died(MinecraftServer server, UUID id, Optional<GlobalPos> where) {
        Person p = persons.get(id);
        if (p == null || !p.alive) {
            return;
        }
        unindex(p);
        p.alive = false;
        where.ifPresent(w -> p.lastSeen = Optional.of(w));
        setDirty();
        log(server, DIED_HISTORY, p, where, new CompoundTag());
        NeoForge.EVENT_BUS.post(new SocietyEvent.Died(server, p, where));
    }

    /** Forgets a record (a migrant who left before joining): posts {@link SocietyEvent.Removed}. */
    public void remove(MinecraftServer server, UUID id) {
        Person p = persons.remove(id);
        if (p == null) {
            return;
        }
        unindex(p);
        setDirty();
        NeoForge.EVENT_BUS.post(new SocietyEvent.Removed(server, p));
    }

    private void index(Person p) {
        if (p.alive) {
            byOwner.computeIfAbsent(p.owner, o -> new LinkedHashSet<>()).add(p.id());
        }
    }

    private void unindex(Person p) {
        Set<UUID> ids = byOwner.get(p.owner);
        if (ids != null) {
            ids.remove(p.id());
            if (ids.isEmpty()) {
                byOwner.remove(p.owner);
            }
        }
    }

    private static void log(MinecraftServer server, ResourceLocation type, Person p, Optional<GlobalPos> where,
            CompoundTag extra) {
        CompoundTag tag = (CompoundTag) Person.CODEC.encodeStart(NbtOps.INSTANCE, p).getOrThrow();
        tag.remove("components");
        tag.merge(extra);
        HistoryLog.get(server).append(new HistoryEntry(server.overworld().getGameTime(), type,
                where.map(GlobalPos::dimension).orElse(server.overworld().dimension()), where.map(GlobalPos::pos),
                tag));
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("dataVersion", DATA_VERSION);
        tag.put("persons", Codec.list(Person.CODEC).encodeStart(NbtOps.INSTANCE, List.copyOf(persons.values()))
                .getOrThrow());
        return tag;
    }

    public static Persons load(CompoundTag tag, HolderLookup.Provider registries) {
        Persons out = new Persons();
        if (tag.getInt("dataVersion") < 2) {
            Migration.peopleV1(tag).forEach(out::put);
            out.setDirty();
            return out;
        }
        for (Tag e : tag.getList("persons", Tag.TAG_COMPOUND)) {
            Person.CODEC.parse(NbtOps.INSTANCE, e).resultOrPartial(err -> LotrPort.LOG.error("[persons] {}", err))
                    .ifPresent(out::put);
        }
        return out;
    }

    /** A record moved over from an old save format: added (or reused) without events or history. */
    public Person migrated(UUID id, ResourceLocation type, String name, long born, Owner owner) {
        Person p = persons.get(id);
        if (p == null) {
            p = Person.create(id, type, name, Optional.empty(), born);
            p.owner = owner;
            put(p);
        } else if (!p.owner.equals(owner)) {
            unindex(p);
            p.owner = owner;
            index(p);
        }
        setDirty();
        return p;
    }

    /** Adds a loaded or migrated record without events or history. */
    void put(Person p) {
        persons.put(p.id(), p);
        index(p);
    }

    /** Old save formats turned into Persons. */
    static final class Migration {
        private Migration() {}

        /** Version 1 ({@code People}): hired units only: {@code entity, type, name, faction, owner, since, alive}. */
        static List<Person> peopleV1(CompoundTag tag) {
            List<Person> out = new ArrayList<>();
            for (Tag e : tag.getList("people", Tag.TAG_COMPOUND)) {
                CompoundTag t = (CompoundTag) e;
                ResourceLocation type = ResourceLocation.tryParse(t.getString("type"));
                if (type == null || !t.hasUUID("entity")) {
                    continue;
                }
                Optional<ResourceLocation> faction = t.contains("faction")
                        ? Optional.ofNullable(ResourceLocation.tryParse(t.getString("faction")))
                        : Optional.empty();
                long since = t.getLong("since");
                Person p = Person.create(t.getUUID("entity"), type, t.getString("name"), faction, since);
                if (t.hasUUID("owner")) {
                    p.owner = Owner.player(t.getUUID("owner"));
                    p.components.set(CoreComponents.UNIT, CoreComponents.Unit.hired(since));
                }
                p.alive = !t.contains("alive") || t.getBoolean("alive");
                out.add(p);
            }
            return out;
        }
    }
}
