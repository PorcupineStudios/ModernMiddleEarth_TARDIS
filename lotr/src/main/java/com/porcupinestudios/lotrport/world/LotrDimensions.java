package com.porcupinestudios.lotrport.world;

import com.porcupinestudios.lotrport.LotrPort;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Dimension keys. Middle-earth itself is a datapack dimension, added in phase 3. */
public final class LotrDimensions {
    public static final ResourceKey<Level> MIDDLE_EARTH = ResourceKey.create(Registries.DIMENSION,
            LotrPort.id("middle_earth"));

    private LotrDimensions() {}
}
