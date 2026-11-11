package com.porcupinestudios.lotrport.group;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/**
 * Anything that owns people: a colony, a player's company of hired units, later an army or a camp. A group is an id,
 * a kind, an owner, its members and a set of parts; everything a feature needs beyond that is a {@link PartType}.
 * Mutable; changed only through {@link Groups}, which saves it and keeps the lookups right.
 */
public final class Group {
    final GroupId id;
    final ResourceLocation kind;
    Owner owner;
    String name;
    final long created;
    final Map<PersonId, Member> members = new LinkedHashMap<>();
    /** What this group carries beyond its members: one part per feature. */
    PartBag parts = new PartBag();

    Group(GroupId id, ResourceLocation kind, Owner owner, String name, long created) {
        this.id = id;
        this.kind = kind;
        this.owner = owner;
        this.name = name;
        this.created = created;
    }

    public GroupId id() {
        return id;
    }

    public ResourceLocation kind() {
        return kind;
    }

    public Owner owner() {
        return owner;
    }

    public String name() {
        return name;
    }

    public long created() {
        return created;
    }

    public Collection<Member> members() {
        return List.copyOf(members.values());
    }

    public int size() {
        return members.size();
    }

    public Optional<Member> member(PersonId person) {
        return Optional.ofNullable(members.get(person));
    }

    public <T> Optional<T> part(PartType<T> type) {
        return parts.get(type);
    }

    CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", id.value());
        t.putString("kind", kind.toString());
        t.put("owner", owner.save());
        t.putString("name", name);
        t.putLong("created", created);
        ListTag list = new ListTag();
        members.values().forEach(m -> list.add(m.save()));
        t.put("members", list);
        t.put("parts", parts.save());
        return t;
    }

    static Optional<Group> load(CompoundTag t) {
        ResourceLocation kind = ResourceLocation.tryParse(t.getString("kind"));
        if (kind == null || !t.hasUUID("id") || !t.getCompound("owner").hasUUID("id")) {
            return Optional.empty();
        }
        Group g = new Group(GroupId.of(t.getUUID("id")), kind, Owner.load(t.getCompound("owner")),
                t.getString("name"), t.getLong("created"));
        for (Tag m : t.getList("members", Tag.TAG_COMPOUND)) {
            Member.load((CompoundTag) m).ifPresent(member -> g.members.put(member.person(), member));
        }
        g.parts = PartBag.load(t.getCompound("parts"));
        return Optional.of(g);
    }
}
