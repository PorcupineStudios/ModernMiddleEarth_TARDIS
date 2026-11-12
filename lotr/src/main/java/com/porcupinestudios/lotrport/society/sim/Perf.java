package com.porcupinestudios.lotrport.society.sim;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/** Time spent by the colony scheduler: per system and per server tick. Not saved; {@link #reset} clears it. */
public final class Perf {
    /** Runs, total and longest nanoseconds of one system. */
    public static final class Stat {
        public long runs;
        public long totalNanos;
        public long maxNanos;

        void add(long nanos) {
            runs++;
            totalNanos += nanos;
            maxNanos = Math.max(maxNanos, nanos);
        }

        public double meanMicros() {
            return runs == 0 ? 0 : totalNanos / 1000.0 / runs;
        }
    }

    private final Map<ResourceLocation, Stat> systems = new LinkedHashMap<>();
    private final Stat ticks = new Stat();
    private long overBudget;
    private long deferred;
    private long caughtUp;

    void system(ResourceLocation id, long nanos) {
        systems.computeIfAbsent(id, k -> new Stat()).add(nanos);
    }

    void tick(long nanos, long budgetNanos) {
        ticks.add(nanos);
        if (nanos > budgetNanos) {
            overBudget++;
        }
    }

    void deferred() {
        deferred++;
    }

    void caughtUp() {
        caughtUp++;
    }

    /** Colony days that waited in the backlog (a busy server, or the tick's budget spent). */
    public long deferredDays() {
        return deferred;
    }

    /** Waiting colony days run later from the backlog. */
    public long caughtUpDays() {
        return caughtUp;
    }

    /** Ticks that took longer than the budget (a garbage collection pause counts too). */
    public long overBudget() {
        return overBudget;
    }

    public Map<ResourceLocation, Stat> systems() {
        return systems;
    }

    /** Every scheduler tick, including ticks with nothing due. */
    public Stat ticks() {
        return ticks;
    }

    public void reset() {
        systems.clear();
        ticks.runs = 0;
        ticks.totalNanos = 0;
        ticks.maxNanos = 0;
        overBudget = 0;
        deferred = 0;
        caughtUp = 0;
    }
}
