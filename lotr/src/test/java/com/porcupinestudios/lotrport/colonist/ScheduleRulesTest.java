package com.porcupinestudios.lotrport.colonist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** Days by schedule: energy, hours of work and leisure, and the thoughts for keeping (or not) a race's hours. */
class ScheduleRulesTest {
    private static final SleepSettings S = ColonistData.DEFAULT_SLEEP;
    private static final Schedule STANDARD = new Schedule("standard", "Standard", S.standardBlocks());
    private static final Rhythm MAN = new Rhythm(6, 22, 8, 3);
    private static final Rhythm HOBBIT = new Rhythm(8, 23, 9, 5);
    private static final Rhythm ELF = new Rhythm(10, 4, 4, 6);

    @Test
    void aManKeepsTheStandardScheduleRested() {
        Rest day = ScheduleRules.days(STANDARD, MAN, Rest.FRESH, true, S, 3);
        assertEquals(47.5, day.energy(), 1e-6, "47.5 at midnight; the night tops them up to 100 by 6");
        assertEquals(30, day.lowest(), 1e-6, "lowest at bedtime");
        assertEquals(11, day.workDone(), 1e-6, "11 work hours");
        assertEquals(5, day.leisureHours(), "4 leisure hours and the Any hour at noon");
        assertEquals(8, day.sleepHours());
        assertEquals(0, day.offHours());
        List<ResourceLocation> t = ScheduleRules.thoughts(day, MAN, S);
        assertEquals(List.of(S.thoughts().enoughLeisure()), t);
        assertEquals(1.1, ScheduleRules.workFactor(day, S), 1e-6);
    }

    @Test
    void aHobbitWantsMoreSleepThanTheStandardDayGives() {
        Rest first = ScheduleRules.day(STANDARD, HOBBIT, 100, true, S);
        assertFalse(ScheduleRules.thoughts(first, HOBBIT, S).contains(S.thoughts().tired()), "fine the first day");
        Rest second = ScheduleRules.day(STANDARD, HOBBIT, first.energy(), true, S);
        assertTrue(ScheduleRules.thoughts(second, HOBBIT, S).contains(S.thoughts().tired()),
                "8 hours in bed is short of their 9: tired by the second day, lowest " + second.lowest());
        assertEquals(2, second.offHours(), "up at 6 and 7, before their 8 o'clock");
    }

    @Test
    void anElfOnTheStandardDayIsUpAtTheWrongHours() {
        Rest day = ScheduleRules.days(STANDARD, ELF, Rest.FRESH, true, S, 2);
        assertEquals(4, day.offHours(), "work from 6 to 10, before their 10 o'clock");
        List<ResourceLocation> t = ScheduleRules.thoughts(day, ELF, S);
        assertTrue(t.contains(S.thoughts().wrongHours()) && t.contains(S.thoughts().noLeisure()), t.toString());
    }

    @Test
    void anyHoursSleepWhenTiredAndHomelessSleepIsShallower() {
        Schedule allAny = new Schedule("any", "Any", "A".repeat(24));
        Rest r = ScheduleRules.day(allAny, MAN, 100, true, S);
        assertTrue(r.sleepHours() > 0 && r.leisureHours() >= MAN.leisureHours(), r.toString());
        Rest home = ScheduleRules.day(STANDARD, MAN, 30, true, S);
        Rest rough = ScheduleRules.day(STANDARD, MAN, 30, false, S);
        assertTrue(rough.energy() < home.energy(), "sleeping rough rests less");
        assertEquals(Schedule.Block.WORK, Schedule.Block.of('w'));
        assertEquals(6, Schedule.hourOf(0), "Minecraft's day starts at 06:00");
        assertEquals(0, Schedule.hourOf(18000));
        assertTrue(ELF.awake(1) && !ELF.awake(5), "a window past midnight");
    }
}
