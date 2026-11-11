package com.porcupinestudios.lotrport.society;

import com.mojang.serialization.Codec;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.UnaryOperator;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/**
 * One typed piece of state a {@link Person} or a colony can carry (a hired unit's orders, a colonist's job, a
 * colony's members). Each type has its own codec and version; {@code upgrades} turn data saved at an older version
 * into the next one, so a component can change shape without breaking old worlds. Register types while the mod
 * loads (static fields), before any world is read.
 */
public final class ComponentType<T> {
    private static final Map<ResourceLocation, ComponentType<?>> TYPES = new LinkedHashMap<>();
    private static boolean frozen;

    private final ResourceLocation id;
    private final Codec<T> codec;
    private final int version;
    private final Map<Integer, UnaryOperator<Tag>> upgrades;

    private ComponentType(ResourceLocation id, Codec<T> codec, int version, Map<Integer, UnaryOperator<Tag>> upgrades) {
        this.id = id;
        this.codec = codec;
        this.version = version;
        this.upgrades = upgrades;
    }

    /** A component type at version 1. */
    public static <T> ComponentType<T> register(ResourceLocation id, Codec<T> codec) {
        return register(id, codec, 1, Map.of());
    }

    /**
     * A component type at {@code version}; {@code upgrades.get(v)} turns data saved at version v into v + 1. A
     * missing step keeps the data as it was.
     */
    public static synchronized <T> ComponentType<T> register(ResourceLocation id, Codec<T> codec, int version,
            Map<Integer, UnaryOperator<Tag>> upgrades) {
        if (frozen) {
            throw new IllegalStateException("component type " + id + " registered after worlds started loading");
        }
        if (TYPES.containsKey(id)) {
            throw new IllegalStateException("component type " + id + " registered twice");
        }
        ComponentType<T> type = new ComponentType<>(id, codec, version, new TreeMap<>(upgrades));
        TYPES.put(id, type);
        return type;
    }

    /** Called when the first world loads: no new types after that. */
    static void freeze() {
        frozen = true;
    }

    public static Optional<ComponentType<?>> byId(ResourceLocation id) {
        return Optional.ofNullable(TYPES.get(id));
    }

    public static Map<ResourceLocation, ComponentType<?>> all() {
        return Collections.unmodifiableMap(TYPES);
    }

    public ResourceLocation id() {
        return id;
    }

    public Codec<T> codec() {
        return codec;
    }

    public int version() {
        return version;
    }

    /** Raises data saved at {@code from} to the current version. */
    Tag upgrade(int from, Tag data) {
        Tag out = data;
        for (int v = from; v < version; v++) {
            UnaryOperator<Tag> step = upgrades.get(v);
            if (step != null) {
                out = step.apply(out);
            }
        }
        return out;
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
