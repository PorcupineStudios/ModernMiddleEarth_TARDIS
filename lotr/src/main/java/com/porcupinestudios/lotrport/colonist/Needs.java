package com.porcupinestudios.lotrport.colonist;

import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/**
 * A colonist's need bars (a person part). A bar holds the value it had at a tick; the value now is worked out when it
 * is read ({@link NeedRules#value}), so nothing ticks per colonist.
 */
public record Needs(Map<ResourceLocation, Bar> bars) {
    public record Bar(double value, long tick) {}

    public Needs {
        bars = Map.copyOf(bars);
    }
}
