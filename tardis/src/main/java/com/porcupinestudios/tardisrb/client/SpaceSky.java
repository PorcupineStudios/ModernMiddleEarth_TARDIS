package com.porcupinestudios.tardisrb.client;

import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.world.phys.Vec3;

/**
 * Space's sky settings (dimension type effects {@code tardis_rb:space}): the overworld's starry sky, but no clouds
 * (Aaron 2026-11-15: "in space weather and clouds needs to be disabled"; space reaches far under the overworld's cloud
 * height, so they hung in view). No weather: {@code SpaceWeatherMixin}.
 */
public final class SpaceSky extends DimensionSpecialEffects {
    public SpaceSky() {
        super(Float.NaN, true, SkyType.NORMAL, false, false);
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 fog, float brightness) {
        // as the overworld's
        return fog.multiply(brightness * 0.94F + 0.06F, brightness * 0.94F + 0.06F, brightness * 0.91F + 0.09F);
    }

    @Override
    public boolean isFoggyAt(int x, int y) {
        return false;
    }
}
