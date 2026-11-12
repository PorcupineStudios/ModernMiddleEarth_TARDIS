package com.porcupinestudios.lotrport.colonist;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * A colonist's task queue (plain rules: data in, data out): every task kind, with its priority right now or why it
 * can't be taken up. The first open task is what they do. Needs climb to the top only when they run low; outside the
 * hours a task allows, it waits whatever the need.
 */
public final class TaskRules {
    /** Why a task waits (lang keys {@code ui.lotr_port.colony.task.blocked.<reason>}). */
    public static final String NOT_NOW = "hours";
    public static final String NOT_NEEDED = "not_needed";

    private TaskRules() {}

    /**
     * What a colonist is like at this moment.
     *
     * @param block the schedule's block this hour (Any as written, not resolved)
     * @param energy 0-100 now
     * @param food 0-20 now
     * @param facts what holds of them and their colony: {@code job}, {@code paid}, {@code works},
     *     {@code food_place}, {@code home}
     */
    public record State(Schedule.Block block, double energy, double food, java.util.Set<String> facts) {}

    /** One line of the queue: open (priority > 0, no reason) or waiting (the reason). */
    public record Entry(ResourceLocation id, TaskDef def, int priority, Optional<String> waiting) {
        public boolean open() {
            return waiting.isEmpty();
        }
    }

    public static double needValue(String need, State s) {
        return switch (need) {
            case "energy" -> s.energy();
            case "food" -> s.food();
            default -> 100;
        };
    }

    /** The queue: open tasks by priority (highest first, ties by id), then the waiting ones by id. */
    public static List<Entry> queue(Map<ResourceLocation, TaskDef> defs, State s) {
        List<Entry> out = new ArrayList<>(defs.size());
        String block = s.block().id();
        for (Map.Entry<ResourceLocation, TaskDef> e : defs.entrySet()) {
            TaskDef d = e.getValue();
            Optional<String> missing = d.requires().stream().filter(r -> !s.facts().contains(r)).findFirst();
            int base = d.priority().getOrDefault(block, 0);
            if (missing.isPresent()) {
                out.add(new Entry(e.getKey(), d, 0, Optional.of(missing.get())));
                continue;
            }
            if (base <= 0) {
                out.add(new Entry(e.getKey(), d, 0, Optional.of(NOT_NOW)));
                continue;
            }
            if (d.need().isPresent()) {
                double v = needValue(d.need().get(), s);
                if (v < d.urgentBelow()) {
                    out.add(new Entry(e.getKey(), d, Math.max(base, d.urgentPriority()), Optional.empty()));
                    continue;
                }
                if (v >= d.wantBelow() && !d.alwaysIn().contains(block)) {
                    out.add(new Entry(e.getKey(), d, 0, Optional.of(NOT_NEEDED)));
                    continue;
                }
            }
            out.add(new Entry(e.getKey(), d, base, Optional.empty()));
        }
        out.sort(Comparator.comparing((Entry x) -> !x.open()).thenComparingInt(x -> -x.priority())
                .thenComparing(x -> x.id().toString()));
        return out;
    }

    /** The task they take up: the first open one. */
    public static Optional<Entry> current(List<Entry> queue) {
        return queue.isEmpty() || !queue.get(0).open() ? Optional.empty() : Optional.of(queue.get(0));
    }

    /** Energy at a clock hour of today, from the day's start, by the schedule (as {@link ScheduleRules#day}). */
    public static double energyAt(Schedule schedule, Rhythm rhythm, double start, boolean home, SleepSettings s,
            int hour) {
        int sleepNeed = Math.max(1, Math.min(Schedule.HOURS - 1, rhythm.sleepHours()));
        double refill = s.dayDrain() / sleepNeed * (home ? 1 : s.homelessSleep());
        double drain = s.dayDrain() / (Schedule.HOURS - sleepNeed);
        double e = start;
        int leisure = 0;
        for (int h = 0; h < hour; h++) {
            Schedule.Block b = ScheduleRules.resolve(schedule.at(h), e, leisure, rhythm, s);
            if (b == Schedule.Block.SLEEP) {
                e = Math.min(100, e + refill);
            } else {
                if (b == Schedule.Block.LEISURE) {
                    leisure++;
                }
                e = Math.max(0, e - drain);
            }
        }
        return e;
    }
}
