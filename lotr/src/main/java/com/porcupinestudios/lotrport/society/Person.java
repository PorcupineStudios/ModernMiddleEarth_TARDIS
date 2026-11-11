package com.porcupinestudios.lotrport.society;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceLocation;

/**
 * One NPC that matters: a hired unit, a colonist, an applicant, a colony soldier, a named character. The record is
 * the truth; the entity in the world is only a body, and a body that is spawned again gets the Person's id as its
 * UUID. Change a Person only through {@link Persons}, which saves it, keeps the indexes and posts events.
 *
 * @param id the Person's id, which is also its body's entity UUID
 */
public final class Person {
    public static final Codec<Person> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(Person::id),
            ResourceLocation.CODEC.fieldOf("type").forGetter(Person::type),
            Codec.STRING.optionalFieldOf("name", "").forGetter(Person::name),
            ResourceLocation.CODEC.optionalFieldOf("faction").forGetter(Person::faction),
            Codec.LONG.optionalFieldOf("born", 0L).forGetter(Person::born),
            Owner.CODEC.optionalFieldOf("owner", Owner.NONE).forGetter(Person::owner),
            Codec.BOOL.optionalFieldOf("alive", true).forGetter(Person::alive),
            GlobalPos.CODEC.optionalFieldOf("last_seen").forGetter(Person::lastSeen),
            ComponentMap.CODEC.optionalFieldOf("components", new ComponentMap()).forGetter(p -> p.components))
            .apply(i, Person::new));

    private final UUID id;
    private final ResourceLocation type;
    String name;
    private final Optional<ResourceLocation> faction;
    private final long born;
    Owner owner;
    boolean alive;
    Optional<GlobalPos> lastSeen;
    final ComponentMap components;

    Person(UUID id, ResourceLocation type, String name, Optional<ResourceLocation> faction, long born, Owner owner,
            boolean alive, Optional<GlobalPos> lastSeen, ComponentMap components) {
        this.id = id;
        this.type = type;
        this.name = name;
        this.faction = faction;
        this.born = born;
        this.owner = owner;
        this.alive = alive;
        this.lastSeen = lastSeen;
        this.components = components;
    }

    /** A new record, owned by nobody yet: add it with {@link Persons#add}. */
    public static Person create(UUID id, ResourceLocation type, String name, Optional<ResourceLocation> faction,
            long born) {
        return new Person(id, type, name, faction, born, Owner.NONE, true, Optional.empty(), new ComponentMap());
    }

    public UUID id() {
        return id;
    }

    public ResourceLocation type() {
        return type;
    }

    public String name() {
        return name;
    }

    public Optional<ResourceLocation> faction() {
        return faction;
    }

    public long born() {
        return born;
    }

    public Owner owner() {
        return owner;
    }

    public boolean alive() {
        return alive;
    }

    /** Where its body was last seen (saved when the body unloads or the record changes). */
    public Optional<GlobalPos> lastSeen() {
        return lastSeen;
    }

    public <T> Optional<T> get(ComponentType<T> type) {
        return components.get(type);
    }

    public boolean has(ComponentType<?> type) {
        return components.has(type);
    }

    /** Read-only view of every component (for saving, debugging and screens). */
    public ComponentMap components() {
        return components;
    }

    @Override
    public String toString() {
        return name + " (" + type + ", " + owner.encode() + (alive ? "" : ", dead") + ")";
    }
}
