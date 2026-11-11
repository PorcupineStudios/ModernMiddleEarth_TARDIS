package com.porcupinestudios.lotrport.colonist;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/**
 * Mood is the sum of what is on a colonist's mind: remembered thoughts (a wedding, a death) that fade, and standing
 * thoughts that last as long as their cause (a need level, a trait, their home). Plain rules: data in, data out.
 */
public final class MoodRules {
    public static final int MIN = 0;
    public static final int MAX = 100;

    private MoodRules() {}

    /**
     * The mood after something happened: the thought is remembered from now, stacking up to its limit. A thought
     * with no duration is a standing one and is not remembered.
     */
    public static Mood remember(Mood mood, Map<ResourceLocation, ThoughtDef> defs, ResourceLocation thought,
            long now) {
        ThoughtDef def = defs.get(thought);
        if (def == null || def.durationTicks() <= 0) {
            return mood;
        }
        List<Mood.Memory> out = new ArrayList<>();
        int stacks = 0;
        for (Mood.Memory m : mood.memories()) {
            if (m.thought().equals(thought)) {
                stacks = live(m, def, now) ? m.stacks() : 0;
            } else {
                out.add(m);
            }
        }
        out.add(new Mood.Memory(thought, now, Math.min(Math.max(1, def.stackLimit()), stacks + 1)));
        return new Mood(out);
    }

    /** The mood without the memories that have faded or whose thought no longer exists. */
    public static Mood forget(Mood mood, Map<ResourceLocation, ThoughtDef> defs, long now) {
        List<Mood.Memory> out = new ArrayList<>();
        for (Mood.Memory m : mood.memories()) {
            ThoughtDef def = defs.get(m.thought());
            if (def != null && live(m, def, now)) {
                out.add(m);
            }
        }
        return out.size() == mood.memories().size() ? mood : new Mood(out);
    }

    private static boolean live(Mood.Memory m, ThoughtDef def, long now) {
        return now - m.since() < def.durationTicks();
    }

    /**
     * The mood score, 0 to 100: the base, plus live memories, plus each standing thought once.
     *
     * @param standing thoughts that hold right now (needs, traits, home); repeats count once
     */
    public static int score(ColonistSettings settings, Map<ResourceLocation, ThoughtDef> defs, Mood mood,
            Collection<ResourceLocation> standing, long now) {
        int score = settings.baseMood();
        for (Mood.Memory m : mood.memories()) {
            ThoughtDef def = defs.get(m.thought());
            if (def != null && live(m, def, now)) {
                score += def.mood() * m.stacks();
            }
        }
        for (ResourceLocation id : new java.util.LinkedHashSet<>(standing)) {
            ThoughtDef def = defs.get(id);
            if (def != null) {
                score += def.mood();
            }
        }
        return Math.max(MIN, Math.min(MAX, score));
    }

    public static MoodState state(ColonistSettings settings, int score) {
        if (score < settings.majorBreak()) {
            return MoodState.MAJOR_BREAK;
        }
        if (score < settings.minorBreak()) {
            return MoodState.MINOR_BREAK;
        }
        if (score < settings.unhappy()) {
            return MoodState.UNHAPPY;
        }
        return score >= settings.inspired() ? MoodState.INSPIRED : MoodState.CONTENT;
    }
}
