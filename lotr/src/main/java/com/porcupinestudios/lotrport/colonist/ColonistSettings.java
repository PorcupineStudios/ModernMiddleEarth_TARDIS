package com.porcupinestudios.lotrport.colonist;

import net.minecraft.resources.ResourceLocation;

/**
 * Tuning for traits and homes ({@code colonist/settings.json}).
 *
 * @param maxTraits most traits a colonist has (every colonist has at least one positive and one negative)
 * @param extraTraitChance chance (0-1) of each trait beyond those two
 * @param ownHome thought for someone single living only with their own household
 * @param familyHome thought for a family (two or more) with a house to itself: what families want
 * @param sharedHome someone single sharing with another household
 * @param familyShared a family sharing with another household
 * @param barracks someone single sharing with {@code barracksHouseholds} or more other households
 * @param familyBarracks a family in a barracks
 * @param barracksHouseholds see {@code barracks}
 */
public record ColonistSettings(int maxTraits, double extraTraitChance, ResourceLocation ownHome,
        ResourceLocation familyHome, ResourceLocation sharedHome, ResourceLocation familyShared,
        ResourceLocation barracks, ResourceLocation familyBarracks, int barracksHouseholds) {}
