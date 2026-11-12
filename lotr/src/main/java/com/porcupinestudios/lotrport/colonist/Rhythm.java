package com.porcupinestudios.lotrport.colonist;

/**
 * When a race likes to be up and how much rest and leisure it wants ({@code colonist_race/<race>.json}
 * {@code rhythm}), ONI's early birds and night owls by race. Hours are clock hours, 0-23.
 *
 * @param awakeFrom first hour of the day they like to be up
 * @param awakeTo the hour they like to be asleep by (may be past midnight: {@code awakeTo < awakeFrom})
 * @param sleepHours hours of sleep a day that keep them rested (energy refills at 1/this per hour asleep)
 * @param leisureHours hours of leisure a day they want
 */
public record Rhythm(int awakeFrom, int awakeTo, int sleepHours, int leisureHours) {
    public static final Rhythm DEFAULT = new Rhythm(6, 22, 8, 3);

    /** Whether they like to be up at this clock hour. */
    public boolean awake(int hour) {
        int h = Math.floorMod(hour, Schedule.HOURS);
        return awakeFrom <= awakeTo ? h >= awakeFrom && h < awakeTo : h >= awakeFrom || h < awakeTo;
    }

    /** "06:00-22:00". */
    public String window() {
        return String.format(java.util.Locale.ROOT, "%02d:00-%02d:00", awakeFrom, awakeTo);
    }
}
