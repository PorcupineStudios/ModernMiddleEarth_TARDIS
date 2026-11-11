package com.porcupinestudios.lotrport.society;

import com.mojang.serialization.Codec;
import com.porcupinestudios.lotrport.LotrPort;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/**
 * The components on one Person or colony. Saved as {@code {"<type id>": {"v": version, "d": data}}}. Data of a type
 * the code no longer knows is kept as it was read and written back unchanged, so removing a feature never loses its
 * data until a migration drops it on purpose.
 */
public final class ComponentMap {
    public static final Codec<ComponentMap> CODEC = CompoundTag.CODEC.xmap(ComponentMap::load, ComponentMap::save);

    private final Map<ComponentType<?>, Object> values = new IdentityHashMap<>();
    private final Map<ResourceLocation, CompoundTag> unknown = new LinkedHashMap<>();

    public <T> Optional<T> get(ComponentType<T> type) {
        @SuppressWarnings("unchecked")
        T v = (T) values.get(type);
        return Optional.ofNullable(v);
    }

    public <T> T getOrDefault(ComponentType<T> type, T fallback) {
        return get(type).orElse(fallback);
    }

    public boolean has(ComponentType<?> type) {
        return values.containsKey(type);
    }

    /** Sets (or with null removes) a component. Callers go through their owner so it is saved. */
    <T> void set(ComponentType<T> type, T value) {
        if (value == null) {
            values.remove(type);
        } else {
            values.put(type, value);
        }
    }

    public Set<ComponentType<?>> types() {
        return values.keySet();
    }

    public CompoundTag save() {
        CompoundTag out = new CompoundTag();
        unknown.forEach((id, raw) -> out.put(id.toString(), raw.copy()));
        values.forEach((type, value) -> {
            CompoundTag entry = new CompoundTag();
            entry.putInt("v", type.version());
            entry.put("d", encode(type, value));
            out.put(type.id().toString(), entry);
        });
        return out;
    }

    @SuppressWarnings("unchecked")
    private static <T> Tag encode(ComponentType<T> type, Object value) {
        return type.codec().encodeStart(NbtOps.INSTANCE, (T) value).getOrThrow();
    }

    public static ComponentMap load(CompoundTag tag) {
        ComponentMap out = new ComponentMap();
        ComponentType.freeze();
        for (String key : tag.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(key);
            if (id == null || !(tag.get(key) instanceof CompoundTag entry)) {
                continue;
            }
            Optional<ComponentType<?>> type = ComponentType.byId(id);
            if (type.isEmpty()) {
                out.unknown.put(id, entry.copy());
                continue;
            }
            read(out, type.get(), entry);
        }
        return out;
    }

    private static <T> void read(ComponentMap out, ComponentType<T> type, CompoundTag entry) {
        Tag data = type.upgrade(entry.getInt("v"), entry.get("d") == null ? new CompoundTag() : entry.get("d"));
        type.codec().parse(NbtOps.INSTANCE, data)
                .resultOrPartial(e -> LotrPort.LOG.error("[society] component {}: {}", type, e))
                .ifPresentOrElse(v -> out.values.put(type, v), () -> out.unknown.put(type.id(), entry.copy()));
    }

    public ComponentMap copy() {
        return load(save());
    }
}
