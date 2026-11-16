package com.porcupinestudios.tardisrb.gravity.gc.mixin;

import com.porcupinestudios.tardisrb.TardisRB;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Ours: no weather in space (Aaron 2026-11-15). Levels other than the overworld share its weather, so space darkened
 * and flashed with the overworld's rain and thunder; in a {@code tardis_rb:space} type level the rain and thunder
 * levels are 0, on the server and the client.
 */
@Mixin(Level.class)
public abstract class SpaceWeatherMixin {
    private static final ResourceKey<DimensionType> SPACE = ResourceKey.create(Registries.DIMENSION_TYPE,
            TardisRB.id("space"));

    @Shadow
    public abstract Holder<DimensionType> dimensionTypeRegistration();

    @Inject(method = "getRainLevel", at = @At("HEAD"), cancellable = true)
    private void tardis_rb$noRainInSpace(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (dimensionTypeRegistration().is(SPACE)) {
            cir.setReturnValue(0F);
        }
    }

    @Inject(method = "getThunderLevel", at = @At("HEAD"), cancellable = true)
    private void tardis_rb$noThunderInSpace(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (dimensionTypeRegistration().is(SPACE)) {
            cir.setReturnValue(0F);
        }
    }
}
