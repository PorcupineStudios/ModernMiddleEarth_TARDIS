package com.porcupinestudios.lotrport.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.ringbearer.api.data.CodecJsonLoader;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/**
 * What a sapling grows into: {@code data/<ns>/sapling/<sapling block>.json}, LOTR's growTree read by
 * {@code converter/saplings.py}; reloads with /reload. The rules are tried in order and the first that fits grows.
 *
 * @param rules each a configured feature and what it needs
 */
public record SaplingRules(List<Rule> rules) {
    public static final Codec<SaplingRules> CODEC = RecordCodecBuilder.create(i -> i.group(
            Rule.CODEC.listOf().fieldOf("rules").forGetter(SaplingRules::rules)).apply(i, SaplingRules::new));

    /**
     * @param tree the configured feature it grows
     * @param square a square of this many by this many of the same sapling (0: a lone sapling)
     * @param cross a plus of five of the same sapling
     * @param chance a lone sapling only with chance 1 in this (0 or 1: always)
     * @param soil every sapling of the square must stand on this block
     */
    public record Rule(ResourceLocation tree, int square, boolean cross, int chance, Optional<ResourceLocation> soil) {
        public static final Codec<Rule> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("tree").forGetter(Rule::tree),
                Codec.INT.optionalFieldOf("square", 0).forGetter(Rule::square),
                Codec.BOOL.optionalFieldOf("cross", false).forGetter(Rule::cross),
                Codec.INT.optionalFieldOf("chance", 0).forGetter(Rule::chance),
                ResourceLocation.CODEC.optionalFieldOf("soil").forGetter(Rule::soil))
                .apply(i, Rule::new));
    }

    private static final CodecJsonLoader<SaplingRules> LOADER = new CodecJsonLoader<>("sapling", CODEC);

    public static void register() {
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent event) -> event.addListener(LOADER));
    }

    /** The rules for a sapling block id. */
    public static Optional<SaplingRules> of(ResourceLocation sapling) {
        return Optional.ofNullable(LOADER.entries().get(sapling));
    }
}
