package com.porcupinestudios.lotrport.colonist;

import java.util.List;
import net.minecraft.resources.ResourceLocation;

/**
 * A need every colonist has ({@code colonist_need/<id>.json}): a bar from 0 to 100.
 *
 * @param fallPerDay points lost per colony day, before race and trait multipliers
 * @param start the bar's value when a colonist settles
 * @param levels thoughts by how low the bar is; the level with the lowest {@code below} the bar is under applies
 */
public record NeedDef(double fallPerDay, double start, List<Level> levels) {
    /** While the bar is under {@code below}, the colonist has {@code thought}. */
    public record Level(double below, ResourceLocation thought) {}
}
