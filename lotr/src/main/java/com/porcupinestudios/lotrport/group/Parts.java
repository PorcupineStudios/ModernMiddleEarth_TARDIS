package com.porcupinestudios.lotrport.group;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;

/** The part types the game knows. Register each once, at mod setup. */
public final class Parts {
    private static final Map<ResourceLocation, PartType<?>> TYPES = new ConcurrentHashMap<>();

    private Parts() {}

    public static <T> PartType<T> register(PartType<T> type) {
        if (TYPES.putIfAbsent(type.id(), type) != null) {
            throw new IllegalStateException("Part type registered twice: " + type.id());
        }
        return type;
    }

    public static Optional<PartType<?>> get(ResourceLocation id) {
        return Optional.ofNullable(TYPES.get(id));
    }
}
