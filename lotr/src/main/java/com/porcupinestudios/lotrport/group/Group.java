package com.porcupinestudios.lotrport.group;

import com.porcupinestudios.lotrport.LotrPort;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
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
    /** Parts by type id; values are that type's data. */
    final Map<ResourceLocation, Object> parts = new LinkedHashMap<>();
    /**
     * Saved parts this game can't read (their type isn't registered, or they failed to parse), by type id, exactly as
     * saved. Written back untouched, so removing a feature or a mod never wipes its data.
     */
    final Map<String, CompoundTag> unread = new LinkedHashMap<>();

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

    @SuppressWarnings("unchecked")
    public <T> Optional<T> part(PartType<T> type) {
        return Optional.ofNullable((T) parts.get(type.id()));
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
        CompoundTag saved = new CompoundTag();
        unread.forEach(saved::put);
        parts.forEach((typeId, value) -> Parts.get(typeId).ifPresent(type -> saved.put(typeId.toString(),
                savePart(type, value))));
        t.put("parts", saved);
        return t;
    }

    @SuppressWarnings("unchecked")
    private static <T> CompoundTag savePart(PartType<T> type, Object value) {
        CompoundTag t = new CompoundTag();
        t.putInt("dataVersion", type.dataVersion());
        t.put("data", type.codec().encodeStart(NbtOps.INSTANCE, (T) value).getOrThrow());
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
        CompoundTag saved = t.getCompound("parts");
        for (String key : saved.getAllKeys()) {
            CompoundTag part = saved.getCompound(key);
            ResourceLocation typeId = ResourceLocation.tryParse(key);
            Optional<PartType<?>> type = typeId == null ? Optional.empty() : Parts.get(typeId);
            Optional<Object> value = type.flatMap(p -> loadPart(p, part));
            if (value.isPresent()) {
                g.parts.put(typeId, value.get());
            } else {
                g.unread.put(key, part.copy());
            }
        }
        return Optional.of(g);
    }

    private static <T> Optional<Object> loadPart(PartType<T> type, CompoundTag part) {
        Tag data = part.get("data");
        if (data == null) {
            return Optional.empty();
        }
        int version = part.getInt("dataVersion");
        if (version > type.dataVersion()) {
            // saved by a newer build: leave it alone rather than guess
            return Optional.empty();
        }
        if (version < type.dataVersion()) {
            data = type.migrate().apply(version, data);
        }
        return type.codec().parse(NbtOps.INSTANCE, data)
                .resultOrPartial(e -> LotrPort.LOG.error("[groups] part {}: {}", type.id(), e))
                .map(v -> (Object) v);
    }
}
