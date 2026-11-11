package com.porcupinestudios.lotrport.colonist;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * Need bars. A bar is stored as a value at a tick and falls at a steady rate, so its value now is one subtraction
 * when someone looks: thousands of colonists cost nothing per tick. Plain rules: data in, data out.
 */
public final class NeedRules {
    public static final double MAX = 100;

    private NeedRules() {}

    /** Every need at its starting value. */
    public static Needs start(Map<ResourceLocation, NeedDef> defs, long now) {
        Map<ResourceLocation, Needs.Bar> bars = new LinkedHashMap<>();
        defs.forEach((id, def) -> bars.put(id, new Needs.Bar(clamp(def.start()), now)));
        return new Needs(bars);
    }

    /**
     * A need's value now.
     *
     * @param rate the race and trait multiplier on how fast it falls
     * @param dayTicks game ticks in a colony day
     */
    public static double value(Needs needs, ResourceLocation need, NeedDef def, double rate, long now,
            int dayTicks) {
        Needs.Bar bar = needs.bars().get(need);
        if (bar == null) {
            return clamp(def.start());
        }
        double days = Math.max(0, now - bar.tick()) / (double) Math.max(1, dayTicks);
        return clamp(bar.value() - def.fallPerDay() * rate * days);
    }

    /** The needs with one bar changed by {@code amount} (a meal, a night's sleep) from its value now. */
    public static Needs change(Needs needs, ResourceLocation need, NeedDef def, double rate, long now, int dayTicks,
            double amount) {
        Map<ResourceLocation, Needs.Bar> bars = new LinkedHashMap<>(needs.bars());
        bars.put(need, new Needs.Bar(clamp(value(needs, need, def, rate, now, dayTicks) + amount), now));
        return new Needs(bars);
    }

    /** The thought a need gives at this value: the level with the lowest bound the value is under, if any. */
    public static Optional<ResourceLocation> thought(NeedDef def, double value) {
        NeedDef.Level best = null;
        for (NeedDef.Level level : def.levels()) {
            if (value < level.below() && (best == null || level.below() < best.below())) {
                best = level;
            }
        }
        return Optional.ofNullable(best).map(NeedDef.Level::thought);
    }

    /** The thoughts all of a colonist's needs give now. {@code rates} is the multiplier per need (1 if absent). */
    public static List<ResourceLocation> thoughts(Map<ResourceLocation, NeedDef> defs, Needs needs,
            Map<ResourceLocation, Double> rates, long now, int dayTicks) {
        List<ResourceLocation> out = new ArrayList<>();
        defs.forEach((id, def) -> thought(def, value(needs, id, def, rates.getOrDefault(id, 1.0), now, dayTicks))
                .ifPresent(out::add));
        return out;
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(MAX, v));
    }
}
