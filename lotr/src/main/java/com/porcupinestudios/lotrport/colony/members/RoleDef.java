package com.porcupinestudios.lotrport.colony.members;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;

/**
 * A colony role, {@code data/<ns>/colony_role/<id>.json}. {@code rank} orders roles: a member may only give roles
 * below their own. The colony's owner is always the ruler, whatever the data says.
 *
 * @param name lang key or text shown on the Town Hall screen
 * @param rank higher = more trusted (ruler 100, guest 0)
 * @param permissions {@link Perm} names this role grants; {@code "*"} grants all
 */
public record RoleDef(String name, int rank, List<String> permissions) {
    public static final Codec<RoleDef> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("name", "").forGetter(RoleDef::name),
            Codec.INT.optionalFieldOf("rank", 0).forGetter(RoleDef::rank),
            Codec.STRING.listOf().optionalFieldOf("permissions", List.of()).forGetter(RoleDef::permissions))
            .apply(i, RoleDef::new));

    public boolean grants(String perm) {
        return permissions.contains("*") || permissions.contains(perm);
    }
}
