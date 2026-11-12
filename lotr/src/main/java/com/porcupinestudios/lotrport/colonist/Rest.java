package com.porcupinestudios.lotrport.colonist;

/**
 * How a colonist's last day went by their schedule (a person part, written by the colony's daily needs pass):
 * energy, and the hours that went to each thing. Mood thoughts and work output read it.
 *
 * @param energy 0-100 at the end of the day (sleep refills it, being up drains it)
 * @param lowest the lowest it got while they were up
 * @param workDone hours of work, a tired hour counting less ({@code exhausted_work})
 * @param leisureHours hours of leisure (Any hours they spent on it included)
 * @param sleepHours hours asleep
 * @param offHours hours they were up (working or at leisure) outside the hours their race likes
 */
public record Rest(double energy, double lowest, double workDone, int leisureHours, int sleepHours, int offHours) {
    public static final Rest FRESH = new Rest(100, 100, -1, -1, -1, 0);

    /** Whether a day has been counted yet (a new colonist has none: no thoughts from it, work as normal). */
    public boolean counted() {
        return workDone >= 0;
    }
}
