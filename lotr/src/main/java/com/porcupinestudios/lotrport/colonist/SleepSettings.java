package com.porcupinestudios.lotrport.colonist;

import net.minecraft.resources.ResourceLocation;

/**
 * Sleep and schedule tuning ({@code colonist_sleep/settings.json}).
 *
 * @param standardBlocks the standard schedule a new colony starts with (24 letters, see {@link Schedule})
 * @param dayDrain energy a race's preferred day uses up: up for {@code 24 - sleep_hours} hours costs this much, and
 *     {@code sleep_hours} asleep gives it back, so keeping their hours never quite empties them
 * @param homelessSleep how much of a night's rest someone with no home gets (0-1)
 * @param anySleepBelow an Any hour goes to sleep below this energy
 * @param tiredBelow lowest energy under which they are {@code tired}
 * @param exhaustedBelow and {@code exhausted}; work done while exhausted counts {@code exhaustedWork}
 * @param exhaustedWork see above (0-1)
 * @param standardWorkHours hours of work a day that give a workplace's normal output; more gives more, up to
 *     {@code maxWorkFactor} times
 * @param maxWorkFactor see above
 * @param oddHoursFrom hours up outside their race's hours that give {@code oddHours}
 * @param wrongHoursFrom and {@code wrongHours} instead
 */
public record SleepSettings(String standardBlocks, double dayDrain, double homelessSleep, double anySleepBelow,
        double tiredBelow, double exhaustedBelow, double exhaustedWork, double standardWorkHours, double maxWorkFactor,
        int oddHoursFrom, int wrongHoursFrom, Thoughts thoughts) {
    /** The thoughts sleep and schedules give. */
    public record Thoughts(ResourceLocation tired, ResourceLocation exhausted, ResourceLocation oddHours,
            ResourceLocation wrongHours, ResourceLocation noLeisure, ResourceLocation enoughLeisure) {}
}
