package com.porcupinestudios.lotrport.group;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Every group (colonies, companies of hired units) and who belongs to which. The one place that changes a
 * {@link Group}. Lookups by owner and by person are kept as maps, so nothing scans every group: the target is 50
 * colonies of 100+ people.
 *
 * <p>A person is in at most one group. Their record (name, type, faction) is in {@code npc.People}; this class holds
 * only membership.
 */
public final class Groups extends SavedData {
    public static final String FILE = "lotr_port_groups";
    public static final int DATA_VERSION = 1;
    private static final SavedData.Factory<Groups> FACTORY = new SavedData.Factory<>(Groups::new, Groups::load, null);

    private final Map<GroupId, Group> groups = new LinkedHashMap<>();
    private final Map<UUID, Set<GroupId>> byOwner = new HashMap<>();
    private final Map<PersonId, GroupId> byPerson = new HashMap<>();

    public static Groups get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE);
    }

    public Optional<Group> get(GroupId id) {
        return Optional.ofNullable(groups.get(id));
    }

    public Collection<Group> all() {
        return List.copyOf(groups.values());
    }

    /** The owner's groups of one kind, oldest first. */
    public List<Group> ownedBy(UUID owner, ResourceLocation kind) {
        List<Group> out = new ArrayList<>();
        for (GroupId id : byOwner.getOrDefault(owner, Set.of())) {
            Group g = groups.get(id);
            if (g != null && g.kind.equals(kind)) {
                out.add(g);
            }
        }
        out.sort(Comparator.comparingLong((Group g) -> g.created).thenComparing(g -> g.id.value()));
        return out;
    }

    /** The group with this id, made now if there is none. An existing group is returned as it is. */
    public Group ensure(GroupId id, ResourceLocation kind, Owner owner, String name, long now) {
        Group g = groups.get(id);
        if (g == null) {
            g = new Group(id, kind, owner, name, now);
            groups.put(id, g);
            byOwner.computeIfAbsent(owner.id(), k -> new LinkedHashSet<>()).add(id);
            setDirty();
        }
        return g;
    }

    /**
     * The company a newly hired unit joins. One company per player for now; whether a player may have several is
     * undecided (Aaron, 2026-11-10), and this method is the only place that decides.
     */
    public Group company(UUID player, long now) {
        List<Group> mine = ownedBy(player, GroupKinds.COMPANY);
        return mine.isEmpty() ? ensure(GroupId.random(), GroupKinds.COMPANY, Owner.player(player), "", now)
                : mine.get(0);
    }

    public void setOwner(Group group, Owner owner) {
        if (group.owner.equals(owner)) {
            return;
        }
        Set<GroupId> old = byOwner.get(group.owner.id());
        if (old != null) {
            old.remove(group.id);
            if (old.isEmpty()) {
                byOwner.remove(group.owner.id());
            }
        }
        group.owner = owner;
        byOwner.computeIfAbsent(owner.id(), k -> new LinkedHashSet<>()).add(group.id);
        setDirty();
    }

    public void rename(Group group, String name) {
        if (!group.name.equals(name)) {
            group.name = name;
            setDirty();
        }
    }

    public Optional<Group> groupOf(PersonId person) {
        return Optional.ofNullable(byPerson.get(person)).map(groups::get);
    }

    /** Puts a person in a group with a role, taking them out of the group they were in. */
    public Member join(Group group, PersonId person, ResourceLocation role, long now, Set<String> flags) {
        GroupId before = byPerson.get(person);
        if (before != null && !before.equals(group.id)) {
            Group old = groups.get(before);
            if (old != null) {
                old.members.remove(person);
            }
        }
        Member member = new Member(person, role, now, flags);
        group.members.put(person, member);
        byPerson.put(person, group.id);
        setDirty();
        return member;
    }

    /** Takes a person out of their group (they left, were dismissed or died). False if they were in none. */
    public boolean leave(PersonId person) {
        GroupId id = byPerson.remove(person);
        if (id == null) {
            return false;
        }
        Group g = groups.get(id);
        if (g != null) {
            g.members.remove(person);
        }
        setDirty();
        return true;
    }

    /** Removes a group. Its members belong to no group afterwards. */
    public void disband(GroupId id) {
        Group g = groups.remove(id);
        if (g == null) {
            return;
        }
        g.members.keySet().forEach(byPerson::remove);
        Set<GroupId> owned = byOwner.get(g.owner.id());
        if (owned != null) {
            owned.remove(id);
            if (owned.isEmpty()) {
                byOwner.remove(g.owner.id());
            }
        }
        setDirty();
    }

    /** Sets a group's part of this type, replacing the one it had. */
    public <T> void setPart(Group group, PartType<T> type, T value) {
        group.parts.set(type, value);
        setDirty();
    }

    public <T> void removePart(Group group, PartType<T> type) {
        if (group.parts.remove(type)) {
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("dataVersion", DATA_VERSION);
        ListTag list = new ListTag();
        groups.values().forEach(g -> list.add(g.save()));
        tag.put("groups", list);
        return tag;
    }

    public static Groups load(CompoundTag tag, HolderLookup.Provider registries) {
        Groups out = new Groups();
        for (Tag e : tag.getList("groups", Tag.TAG_COMPOUND)) {
            Group.load((CompoundTag) e).ifPresent(g -> {
                out.groups.put(g.id, g);
                out.byOwner.computeIfAbsent(g.owner.id(), k -> new LinkedHashSet<>()).add(g.id);
                g.members.keySet().forEach(p -> out.byPerson.put(p, g.id));
            });
        }
        return out;
    }
}
