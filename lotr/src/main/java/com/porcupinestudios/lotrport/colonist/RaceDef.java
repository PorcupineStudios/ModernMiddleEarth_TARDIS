package com.porcupinestudios.lotrport.colonist;

import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/**
 * How a race lives ({@code colonist_race/<id>.json}).
 *
 * @param match an NPC type is of this race if its id's path contains one of these (the first race that matches, in
 *     id order, wins; the settings' default race otherwise)
 * @param daysPerYear colony days it takes to age one year (Men: 1, so 18 days is age 18)
 * @param adultAge age in years at which a child comes of age
 * @param extendedHousehold grown children and grandparents stay in one household (hobbits); otherwise a household
 *     is a couple and their children who have not come of age
 * @param needRates multiplies how fast a need falls, by need id
 * @param settlerAgeMin youngest age of a settler who arrives grown
 * @param settlerAgeMax oldest
 */
public record RaceDef(List<String> match, double daysPerYear, int adultAge, boolean extendedHousehold,
        Map<ResourceLocation, Double> needRates, int settlerAgeMin, int settlerAgeMax) {}
