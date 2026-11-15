package com.porcupinestudios.lotrport.colonist;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/**
 * A colonist's day by their schedule, hour by hour (plain rules: data in, data out). Asleep, energy refills by
 * {@code day_drain / sleep_hours} an hour (less without a home); up, it drains by {@code day_drain / (24 -
 * sleep_hours)}. An Any hour goes to sleep when energy is low, to leisure while they have had less than their race
 * wants, else to work. Hours up outside the race's hours are counted.
 */
public final class ScheduleRules {
    private ScheduleRules() {}

    /** Runs {@code days} days from {@code start} (the last day's energy); the result is the last day's. */
    public static Rest days(Schedule schedule, Rhythm rhythm, Rest start, boolean home, SleepSettings s, int days) {
        Rest r = start;
        for (int d = 0; d < Math.max(1, days); d++) {
            r = day(schedule, rhythm, r.energy(), home, s);
        }
        return r;
    }

    public static Rest day(Schedule schedule, Rhythm rhythm, double energy, boolean home, SleepSettings s) {
        int sleepNeed = Math.max(1, Math.min(Schedule.HOURS - 1, rhythm.sleepHours()));
        double refill = s.dayDrain() / sleepNeed * (home ? 1 : s.homelessSleep());
        double drain = s.dayDrain() / (Schedule.HOURS - sleepNeed);
        double e = energy;
        double lowest = 100;
        double work = 0;
        int leisure = 0;
        int sleep = 0;
        int off = 0;
        for (int h = 0; h < Schedule.HOURS; h++) {
            Schedule.Block b = resolve(schedule.at(h), e, leisure, rhythm, s);
            if (b == Schedule.Block.SLEEP) {
                e = Math.min(100, e + refill);
                sleep++;
                continue;
            }
            if (b == Schedule.Block.WORK) {
                work += e < s.exhaustedBelow() ? s.exhaustedWork() : 1;
            } else {
                leisure++;
            }
            if (!rhythm.awake(h)) {
                off++;
            }
            e = Math.max(0, e - drain);
            lowest = Math.min(lowest, e);
        }
        return new Rest(e, lowest, work, leisure, sleep, off);
    }

    /** What an hour turns out to be: Any becomes whatever they need most right now. */
    public static Schedule.Block resolve(Schedule.Block b, double energy, int leisureSoFar, Rhythm rhythm,
            SleepSettings s) {
        if (b != Schedule.Block.ANY) {
            return b;
        }
        if (energy < s.anySleepBelow()) {
            return Schedule.Block.SLEEP;
        }
        return leisureSoFar < rhythm.leisureHours() ? Schedule.Block.LEISURE : Schedule.Block.WORK;
    }

    /** How much a day of this work gives against a standard day (1), up to {@code max_work_factor}. */
    public static double workFactor(Rest r, SleepSettings s) {
        if (!r.counted()) {
            return 1;
        }
        return Math.max(0, Math.min(s.maxWorkFactor(), r.workDone() / Math.max(1, s.standardWorkHours())));
    }

    /** What {@link #thoughts} add to mood, without a list (the daily pass runs it for every colonist). */
    public static int mood(Rest r, Rhythm rhythm, SleepSettings s, java.util.Map<ResourceLocation, ThoughtDef> defs) {
        if (!r.counted()) {
            return 0;
        }
        SleepSettings.Thoughts t = s.thoughts();
        int sum = 0;
        if (r.lowest() < s.exhaustedBelow()) {
            sum += moodOf(defs, t.exhausted());
        } else if (r.lowest() < s.tiredBelow()) {
            sum += moodOf(defs, t.tired());
        }
        if (r.offHours() >= s.wrongHoursFrom()) {
            sum += moodOf(defs, t.wrongHours());
        } else if (r.offHours() >= s.oddHoursFrom()) {
            sum += moodOf(defs, t.oddHours());
        }
        if (rhythm.leisureHours() > 0) {
            sum += moodOf(defs, r.leisureHours() >= rhythm.leisureHours() ? t.enoughLeisure() : t.noLeisure());
        }
        return sum;
    }

    private static int moodOf(java.util.Map<ResourceLocation, ThoughtDef> defs, ResourceLocation id) {
        ThoughtDef d = defs.get(id);
        return d == null ? 0 : d.mood();
    }

    /** The standing thoughts a day like this gives. */
    public static List<ResourceLocation> thoughts(Rest r, Rhythm rhythm, SleepSettings s) {
        List<ResourceLocation> out = new ArrayList<>(3);
        if (!r.counted()) {
            return out;
        }
        if (r.lowest() < s.exhaustedBelow()) {
            out.add(s.thoughts().exhausted());
        } else if (r.lowest() < s.tiredBelow()) {
            out.add(s.thoughts().tired());
        }
        if (r.offHours() >= s.wrongHoursFrom()) {
            out.add(s.thoughts().wrongHours());
        } else if (r.offHours() >= s.oddHoursFrom()) {
            out.add(s.thoughts().oddHours());
        }
        if (rhythm.leisureHours() > 0) {
            out.add(r.leisureHours() >= rhythm.leisureHours() ? s.thoughts().enoughLeisure()
                    : s.thoughts().noLeisure());
        }
        return out;
    }
}
