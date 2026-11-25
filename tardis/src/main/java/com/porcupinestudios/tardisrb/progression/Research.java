package com.porcupinestudios.tardisrb.progression;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.ringbearer.api.condition.Condition;
import com.porcupinestudios.ringbearer.api.condition.Conditions;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * An upgrade in the research tree ({@code tardis/research}): needs every {@code parents} node first (at level 1),
 * bought at once (no research time since 2026-11-19, Aaron). A plain node is one level: {@code cost} and
 * {@code stats} (added to named numbers once bought, e.g. {@code "range": 16}). A node with {@code levels} can be
 * bought once per level, each with its own cost and stats (they add up). Anything else it unlocks is written on the
 * other side, as {@code tardis_rb:research} requirements (level 1). An old {@code time} key is ignored.
 */
public record Research(List<ResourceLocation> parents, Either<ResourceLocation, Cost> cost,
        Optional<Condition> requirements, Map<String, Double> stats, List<Level> levels) {
    /** One level of a multi-level node. */
    public record Level(Either<ResourceLocation, Cost> cost, Map<String, Double> stats) {
        public static final Codec<Level> CODEC = RecordCodecBuilder.create(i -> i.group(
                Cost.REF_CODEC.optionalFieldOf("cost", Cost.NONE).forGetter(Level::cost),
                Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).optionalFieldOf("stats", Map.of())
                        .forGetter(Level::stats))
                .apply(i, Level::new));
    }

    public static final Codec<Research> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.listOf().optionalFieldOf("parents", List.of()).forGetter(Research::parents),
            Cost.REF_CODEC.optionalFieldOf("cost", Cost.NONE).forGetter(Research::cost),
            Conditions.CODEC.optionalFieldOf("requirements").forGetter(Research::requirements),
            Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).optionalFieldOf("stats", Map.of()).forGetter(Research::stats),
            Level.CODEC.listOf().optionalFieldOf("levels", List.of()).forGetter(Research::levels))
            .apply(i, Research::new));

    public int maxLevel() {
        return levels.isEmpty() ? 1 : levels.size();
    }

    /** Level n (1-based): its cost and stats. */
    public Level level(int n) {
        return levels.isEmpty() ? new Level(cost, stats) : levels.get(Math.max(0, Math.min(n, levels.size()) - 1));
    }

    /** The stats of levels 1..n together. */
    public Map<String, Double> statsUpTo(int n) {
        Map<String, Double> out = new HashMap<>();
        for (int k = 1; k <= Math.min(n, maxLevel()); k++) {
            level(k).stats().forEach((key, v) -> out.merge(key, v, Double::sum));
        }
        return out;
    }
}
