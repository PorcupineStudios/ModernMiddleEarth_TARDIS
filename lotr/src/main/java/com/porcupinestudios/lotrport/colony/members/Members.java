package com.porcupinestudios.lotrport.colony.members;

import com.mojang.serialization.Codec;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.society.ComponentType;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceLocation;

/**
 * A colony's player members and their roles (a colony component). The owner is not listed: they are always the
 * ruler. Immutable; change it with {@link Permissions#setRole}.
 */
public record Members(Map<UUID, ResourceLocation> roles) {
    public static final Codec<Members> CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, ResourceLocation.CODEC)
            .xmap(Members::new, Members::roles);
    public static final Members NONE = new Members(Map.of());
    public static final ComponentType<Members> TYPE = ComponentType.register(LotrPort.id("members"), CODEC);

    public Members {
        roles = Map.copyOf(roles);
    }

    public Optional<ResourceLocation> role(UUID player) {
        return Optional.ofNullable(roles.get(player));
    }

    /** With {@code player} given {@code role}, or removed if empty. */
    public Members with(UUID player, Optional<ResourceLocation> role) {
        Map<UUID, ResourceLocation> next = new LinkedHashMap<>(roles);
        if (role.isPresent()) {
            next.put(player, role.get());
        } else {
            next.remove(player);
        }
        return new Members(next);
    }

    /** Loads the class (registers the component) from the mod constructor. */
    public static void init() {}
}
