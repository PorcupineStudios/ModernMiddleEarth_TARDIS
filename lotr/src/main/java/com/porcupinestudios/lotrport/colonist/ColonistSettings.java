package com.porcupinestudios.lotrport.colonist;

import net.minecraft.resources.ResourceLocation;

/**
 * Tuning for traits, mood and homes ({@code colonist/settings.json}).
 *
 * @param maxTraits most traits a colonist has (every colonist has at least one positive and one negative)
 * @param extraTraitChance chance (0-1) of each trait beyond those two
 * @param baseMood mood with nothing on a colonist's mind
 * @param majorBreak mood below this is a major break
 * @param minorBreak below this, a minor break
 * @param unhappy below this, unhappy
 * @param inspired at or above this, inspired
 * @param defaultRace race of an NPC type no race matches
 * @param ownHome thought for living only with one's own household
 * @param sharedHome thought for sharing a house with people of other households
 * @param barracks thought for sharing with {@code barracksHouseholds} or more other households
 * @param homeless thought for having no home
 * @param barracksHouseholds see {@code barracks}
 */
public record ColonistSettings(int maxTraits, double extraTraitChance, int baseMood, int majorBreak, int minorBreak,
        int unhappy, int inspired, ResourceLocation defaultRace, ResourceLocation ownHome, ResourceLocation sharedHome,
        ResourceLocation barracks, ResourceLocation homeless, int barracksHouseholds) {}
