package com.porcupinestudios.lotrport.colonist;

import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/**
 * How a race lives ({@code colonist_race/<race>.json}, the race names of {@code colony_race/}: man, hobbit, dwarf, elf,
 * orc). Which NPC types are of a race is {@code colony_race/}'s job ({@code colony.work.Races}), not this file's.
 *
 * @param daysPerYear colony days it takes to age one year (Men: 1, so 18 days is age 18; Aaron 2026-11-10)
 * @param adultAge age in years at which a child comes of age
 * @param extendedHousehold grown children and grandparents stay in one household (hobbits); otherwise a household
 *     is a couple and their children who have not come of age
 * @param needRates multiplies how fast a need falls, by need id (so far only {@code lotr_port:food})
 * @param settlerAgeMin youngest age of a settler who arrives grown
 * @param settlerAgeMax oldest
 * @param rhythm the hours they like to keep, and how much sleep and leisure they want
 */
public record RaceDef(double daysPerYear, int adultAge, boolean extendedHousehold,
        Map<ResourceLocation, Double> needRates, int settlerAgeMin, int settlerAgeMax, Rhythm rhythm) {
    public RaceDef(double daysPerYear, int adultAge, boolean extendedHousehold, Map<ResourceLocation, Double> needRates,
            int settlerAgeMin, int settlerAgeMax) {
        this(daysPerYear, adultAge, extendedHousehold, needRates, settlerAgeMin, settlerAgeMax, Rhythm.DEFAULT);
    }
}
