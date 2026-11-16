package com.porcupinestudios.lotrport.colonist;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * A trait a colonist is born with ({@code colonist_trait/<id>.json}).
 *
 * @param scale traits on the same scale are degrees of one thing (industrious / lazy): a colonist has at most one
 * @param weight how often it is rolled (0 = never rolled, only inherited or given)
 * @param heritable chance (0-1) a child gets it from a parent who has it
 * @param workSpeed multiplies work speed (1 = no change)
 * @param needRates multiplies how fast a need falls, by need id
 * @param thoughts thoughts the colonist always has because of this trait
 * @param onBreak what the colonist does when their mood collapses (brawl, binge, idle, wander, smash), if this
 *     trait decides it
 * @param conflicts traits that can't go with this one
 */
public record TraitDef(Kind kind, Optional<String> scale, int weight, double heritable, double workSpeed,
        Map<ResourceLocation, Double> needRates, List<ResourceLocation> thoughts, Optional<String> onBreak,
        List<ResourceLocation> conflicts) {
    public enum Kind {
        POSITIVE, NEGATIVE, NEUTRAL
    }
}
