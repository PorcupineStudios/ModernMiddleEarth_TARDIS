package com.porcupinestudios.lotrport.colonist;

import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * A ration a colonist can be put on ({@code colonist_ration/<id>.json}); the citizen page lists them by
 * {@code order}. A pack can add its own (a feast ration, a fasting rule).
 *
 * @param foodRate how fast their food runs down (1 = normal); multiplies race and traits
 * @param thought a standing thought while on it (grumbling on small rations), if any
 * @param order place in the list
 */
public record RationDef(double foodRate, Optional<ResourceLocation> thought, int order) {}
