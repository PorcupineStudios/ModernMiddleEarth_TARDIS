package com.porcupinestudios.lotrport.colonist;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.TreeMap;
import net.minecraft.resources.ResourceLocation;

/** Rolling, inheriting and reading traits. Plain rules: data in, data out. */
public final class TraitRules {
    private TraitRules() {}

    /**
     * Traits for a new colonist: one positive, one negative, then more by chance up to the settings' most. Never two
     * on the same scale or two that conflict. Fewer come back only if the data has none that fit.
     */
    public static List<ResourceLocation> roll(Map<ResourceLocation, TraitDef> defs, Random random,
            ColonistSettings settings) {
        return fill(defs, new ArrayList<>(), random, settings);
    }

    /**
     * Traits for a child: each trait of each parent passes down by its {@code heritable} chance, then the rest is
     * rolled as for anyone.
     */
    public static List<ResourceLocation> inherit(Map<ResourceLocation, TraitDef> defs,
            List<List<ResourceLocation>> parents, Random random, ColonistSettings settings) {
        List<ResourceLocation> chosen = new ArrayList<>();
        for (List<ResourceLocation> parent : parents) {
            for (ResourceLocation id : parent) {
                TraitDef def = defs.get(id);
                if (def != null && chosen.size() < settings.maxTraits() && fits(defs, id, chosen)
                        && random.nextDouble() < def.heritable()) {
                    chosen.add(id);
                }
            }
        }
        return fill(defs, chosen, random, settings);
    }

    private static List<ResourceLocation> fill(Map<ResourceLocation, TraitDef> defs, List<ResourceLocation> chosen,
            Random random, ColonistSettings settings) {
        for (TraitDef.Kind kind : List.of(TraitDef.Kind.POSITIVE, TraitDef.Kind.NEGATIVE)) {
            boolean has = chosen.stream().anyMatch(id -> defs.get(id) != null && defs.get(id).kind() == kind);
            if (!has && chosen.size() < settings.maxTraits()) {
                pick(defs, chosen, random, kind).ifPresent(chosen::add);
            }
        }
        while (chosen.size() < settings.maxTraits() && random.nextDouble() < settings.extraTraitChance()) {
            Optional<ResourceLocation> more = pick(defs, chosen, random, null);
            if (more.isEmpty()) {
                break;
            }
            chosen.add(more.get());
        }
        return List.copyOf(chosen);
    }

    /** A weighted pick among the traits of {@code kind} (any kind if null) that fit with those already chosen. */
    private static Optional<ResourceLocation> pick(Map<ResourceLocation, TraitDef> defs, List<ResourceLocation> chosen,
            Random random, TraitDef.Kind kind) {
        // in id order, so the same seed gives the same colonist whatever order the files loaded in
        Map<String, ResourceLocation> ordered = new TreeMap<>();
        defs.keySet().forEach(id -> ordered.put(id.toString(), id));
        List<ResourceLocation> candidates = new ArrayList<>();
        int total = 0;
        for (ResourceLocation id : ordered.values()) {
            TraitDef def = defs.get(id);
            if (def.weight() > 0 && (kind == null || def.kind() == kind) && fits(defs, id, chosen)) {
                candidates.add(id);
                total += def.weight();
            }
        }
        if (total <= 0) {
            return Optional.empty();
        }
        int roll = random.nextInt(total);
        for (ResourceLocation id : candidates) {
            roll -= defs.get(id).weight();
            if (roll < 0) {
                return Optional.of(id);
            }
        }
        return Optional.empty();
    }

    /** Whether a trait can join those already chosen: not a repeat, not on a scale already used, no conflict. */
    public static boolean fits(Map<ResourceLocation, TraitDef> defs, ResourceLocation id,
            List<ResourceLocation> chosen) {
        TraitDef def = defs.get(id);
        if (def == null || chosen.contains(id)) {
            return false;
        }
        for (ResourceLocation other : chosen) {
            TraitDef o = defs.get(other);
            if (def.conflicts().contains(other)) {
                return false;
            }
            if (o != null && (o.conflicts().contains(id)
                    || def.scale().isPresent() && def.scale().equals(o.scale()))) {
                return false;
            }
        }
        return true;
    }

    /** A colonist's work speed from their traits (1 = normal). */
    public static double workSpeed(Map<ResourceLocation, TraitDef> defs, List<ResourceLocation> traits) {
        double speed = 1;
        for (ResourceLocation id : traits) {
            TraitDef def = defs.get(id);
            if (def != null) {
                speed *= def.workSpeed();
            }
        }
        return speed;
    }

    /** How much faster (above 1) or slower a need falls for these traits. */
    public static double needRate(Map<ResourceLocation, TraitDef> defs, List<ResourceLocation> traits,
            ResourceLocation need) {
        double rate = 1;
        for (ResourceLocation id : traits) {
            TraitDef def = defs.get(id);
            if (def != null) {
                rate *= def.needRates().getOrDefault(need, 1.0);
            }
        }
        return rate;
    }

    /** Thoughts a colonist always has because of their traits. */
    public static List<ResourceLocation> thoughts(Map<ResourceLocation, TraitDef> defs,
            List<ResourceLocation> traits) {
        List<ResourceLocation> out = new ArrayList<>();
        for (ResourceLocation id : traits) {
            TraitDef def = defs.get(id);
            if (def != null) {
                out.addAll(def.thoughts());
            }
        }
        return out;
    }

    /** What the colonist does when their mood collapses: the first trait that says, else {@code fallback}. */
    public static String onBreak(Map<ResourceLocation, TraitDef> defs, List<ResourceLocation> traits,
            String fallback) {
        for (ResourceLocation id : traits) {
            TraitDef def = defs.get(id);
            if (def != null && def.onBreak().isPresent()) {
                return def.onBreak().get();
            }
        }
        return fallback;
    }
}
