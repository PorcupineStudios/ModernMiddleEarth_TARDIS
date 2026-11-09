package com.porcupinestudios.tardisrb.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * Where a wormhole in the time vortex can lead ({@code tardis/wormhole}, Aaron 2026-11-09): a {@code dimension} and
 * how likely it is, {@code weight}. {@code from} gives other weights for trips that left (or aim at) a given
 * dimension, for "adjacent" dimensions; see {@link #weight}. A dimension with no file is never a wormhole's exit.
 */
public record WormholeExit(ResourceKey<Level> dimension, double weight, Map<ResourceLocation, Double> from) {
    public static final Codec<WormholeExit> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(WormholeExit::dimension),
            Codec.DOUBLE.optionalFieldOf("weight", 1.0).forGetter(WormholeExit::weight),
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.DOUBLE).optionalFieldOf("from", Map.of())
                    .forGetter(WormholeExit::from))
            .apply(i, WormholeExit::new));

    /** The weight on a trip from {@code left} to {@code aim}: {@code from[left]}, else {@code from[aim]}, else {@code weight}. */
    public double weight(ResourceLocation left, ResourceLocation aim) {
        Double w = from.get(left);
        if (w == null) {
            w = from.get(aim);
        }
        return Math.max(0, w == null ? weight : w);
    }
}
