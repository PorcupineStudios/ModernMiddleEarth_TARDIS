package com.porcupinestudios.lotrport.society;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/**
 * Who a {@link Person} belongs to. Hiring makes a player the owner; joining a colony makes the colony the owner;
 * stationing a hired unit in a colony is one owner change. Saved as one string: {@code player:<uuid>},
 * {@code colony:<uuid>}, {@code realm:<id>} or {@code none}.
 */
public sealed interface Owner permits Owner.OfPlayer, Owner.OfColony, Owner.OfRealm, Owner.Nobody {
    Codec<Owner> CODEC = Codec.STRING.comapFlatMap(Owner::parse, Owner::encode);

    Nobody NONE = new Nobody();

    record OfPlayer(UUID id) implements Owner {}

    record OfColony(UUID id) implements Owner {}

    record OfRealm(ResourceLocation id) implements Owner {}

    record Nobody() implements Owner {}

    static Owner player(UUID id) {
        return new OfPlayer(id);
    }

    static Owner colony(UUID id) {
        return new OfColony(id);
    }

    default Optional<UUID> playerId() {
        return this instanceof OfPlayer p ? Optional.of(p.id()) : Optional.empty();
    }

    default Optional<UUID> colonyId() {
        return this instanceof OfColony c ? Optional.of(c.id()) : Optional.empty();
    }

    default String encode() {
        return switch (this) {
            case OfPlayer p -> "player:" + p.id();
            case OfColony c -> "colony:" + c.id();
            case OfRealm r -> "realm:" + r.id();
            case Nobody n -> "none";
        };
    }

    static DataResult<Owner> parse(String s) {
        try {
            if (s.equals("none") || s.isEmpty()) {
                return DataResult.success(NONE);
            }
            if (s.startsWith("player:")) {
                return DataResult.success(new OfPlayer(UUID.fromString(s.substring(7))));
            }
            if (s.startsWith("colony:")) {
                return DataResult.success(new OfColony(UUID.fromString(s.substring(7))));
            }
            if (s.startsWith("realm:")) {
                ResourceLocation id = ResourceLocation.tryParse(s.substring(6));
                return id == null ? DataResult.error(() -> "bad realm owner " + s) : DataResult.success(new OfRealm(id));
            }
        } catch (IllegalArgumentException e) {
            // falls through
        }
        return DataResult.error(() -> "bad owner " + s);
    }
}
