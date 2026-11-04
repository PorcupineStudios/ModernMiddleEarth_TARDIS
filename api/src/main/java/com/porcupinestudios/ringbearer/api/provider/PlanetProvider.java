package com.porcupinestudios.ringbearer.api.provider;

import java.util.Optional;
import net.minecraft.server.level.ServerLevel;

/**
 * Says what a dimension looks like as a planet: the full-size surface that wraps round the globe, and optionally a
 * picture of it for the globe seen from space. The LOTR port answers for Middle-earth (its whole map, at full size,
 * painted from LOTR's map). With no answer the TARDIS mod uses the sky link's own data.
 */
@FunctionalInterface
public interface PlanetProvider {
    Optional<PlanetSurface> planet(ServerLevel level);
}
