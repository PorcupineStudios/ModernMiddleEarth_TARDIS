package com.porcupinestudios.lotrport.group;

import com.porcupinestudios.lotrport.LotrPort;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/**
 * The parts one thing carries: a group's (land, stores, orders...) or a person's (traits, needs, mood, kin). Each
 * part is saved under its type id with its own data version. A saved part this game can't read (its type isn't
 * registered, it failed to parse, or a newer build wrote it) is kept exactly as saved and written back untouched, so
 * removing a feature or a mod never wipes its data.
 */
public final class PartBag {
    private final Map<ResourceLocation, Object> parts = new LinkedHashMap<>();
    private final Map<String, CompoundTag> unread = new LinkedHashMap<>();

    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(PartType<T> type) {
        return Optional.ofNullable((T) parts.get(type.id()));
    }

    public <T> void set(PartType<T> type, T value) {
        parts.put(type.id(), value);
        unread.remove(type.id().toString());
    }

    /** True if there was one to remove. */
    public <T> boolean remove(PartType<T> type) {
        return parts.remove(type.id()) != null;
    }

    public boolean isEmpty() {
        return parts.isEmpty() && unread.isEmpty();
    }

    public CompoundTag save() {
        CompoundTag saved = new CompoundTag();
        unread.forEach(saved::put);
        parts.forEach((typeId, value) -> Parts.get(typeId).ifPresent(type -> saved.put(typeId.toString(),
                savePart(type, value))));
        return saved;
    }

    @SuppressWarnings("unchecked")
    private static <T> CompoundTag savePart(PartType<T> type, Object value) {
        CompoundTag t = new CompoundTag();
        t.putInt("dataVersion", type.dataVersion());
        t.put("data", type.codec().encodeStart(NbtOps.INSTANCE, (T) value).getOrThrow());
        return t;
    }

    public static PartBag load(CompoundTag saved) {
        PartBag bag = new PartBag();
        for (String key : saved.getAllKeys()) {
            CompoundTag part = saved.getCompound(key);
            ResourceLocation typeId = ResourceLocation.tryParse(key);
            Optional<PartType<?>> type = typeId == null ? Optional.empty() : Parts.get(typeId);
            Optional<Object> value = type.flatMap(p -> loadPart(p, part));
            if (value.isPresent()) {
                bag.parts.put(typeId, value.get());
            } else {
                bag.unread.put(key, part.copy());
            }
        }
        return bag;
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
                .resultOrPartial(e -> LotrPort.LOG.error("[parts] {}: {}", type.id(), e))
                .map(v -> (Object) v);
    }
}
