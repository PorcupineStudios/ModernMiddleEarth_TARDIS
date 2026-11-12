package com.porcupinestudios.lotrport.journey;

import java.util.List;
import net.minecraft.resources.ResourceLocation;

/**
 * An animal a party can take ({@code journey_steed/<id>.json}): which entity types it is, how fast it goes against
 * walking (1), what it carries and whether it can draw a wagon. Any entity type can be one (LOTR's horses, ponies,
 * camels, elk; vanilla horses, donkeys, mules, llamas; another mod's), so new steeds are a data file.
 *
 * @param entities entity types that count as this steed
 * @param speed against walking pace
 * @param carry stacks of goods it carries
 * @param drawsWagon it can be hitched to a wagon
 * @param needsTame only tamed ones (and not one a player is riding) go
 */
public record SteedDef(List<ResourceLocation> entities, double speed, int carry, boolean drawsWagon,
        boolean needsTame) {
    public PartyRules.Steed rules() {
        return new PartyRules.Steed(speed, carry, drawsWagon);
    }
}
