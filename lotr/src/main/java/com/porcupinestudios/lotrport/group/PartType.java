package com.porcupinestudios.lotrport.group;

import com.mojang.serialization.Codec;
import java.util.function.BiFunction;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/**
 * A kind of part a group can carry (land, stores, orders, access...). Each part owns its data, its Codec and its own
 * data version, so a feature is added, changed or removed without touching {@link Group} or the other parts.
 *
 * @param dataVersion the version {@code codec} writes today
 * @param migrate turns data saved at an older version (first argument) into today's shape before it is read
 */
public record PartType<T>(ResourceLocation id, int dataVersion, Codec<T> codec, BiFunction<Integer, Tag, Tag> migrate) {
    public static <T> PartType<T> of(ResourceLocation id, int dataVersion, Codec<T> codec) {
        return new PartType<>(id, dataVersion, codec, (from, tag) -> tag);
    }
}
