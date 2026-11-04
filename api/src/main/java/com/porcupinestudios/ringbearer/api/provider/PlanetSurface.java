package com.porcupinestudios.ringbearer.api.provider;

import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * A planet's surface in blocks: x from {@code minX} to {@code maxX} goes once round the globe (longitude), z from
 * {@code minZ} (north pole) to {@code maxZ} (south pole) is the latitude. For an undistorted globe make it twice as
 * wide as it is tall. {@code picture}, if any, is a client texture showing part of the surface.
 */
public record PlanetSurface(int minX, int maxX, int minZ, int maxZ, Optional<Picture> picture) {
    /**
     * A client texture (an equirectangular map, north up) covering the blocks {@code minX..maxX}, {@code minZ..maxZ};
     * the rest of the globe is {@code fill} (RGB), or the picture's top-left pixel when {@code fill} is -1.
     */
    public record Picture(ResourceLocation texture, int minX, int maxX, int minZ, int maxZ, int fill) {}
}
