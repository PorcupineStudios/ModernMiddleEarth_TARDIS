package com.porcupinestudios.tardisrb.gravity.gc.mixin;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.WorldLoader;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Not gravity: lives here because this is the mod's only mixin package. Vanilla's game test server bakes its
 * dimensions against an empty registry, so datapack dimensions (the interior, the time vortex) never load in
 * {@code runGameTestServer}; bake against the datapack's dimensions, as a real server does.
 */
@Mixin(GameTestServer.class)
public abstract class GameTestServerMixin {
    @Redirect(method = "lambda$create$1", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/WorldDimensions;bake(Lnet/minecraft/core/Registry;)Lnet/minecraft/world/level/levelgen/WorldDimensions$Complete;"))
    private static WorldDimensions.Complete tardisrb$bakeDatapackDimensions(WorldDimensions dims, Registry<LevelStem> empty,
            LevelSettings settings, WorldLoader.DataLoadContext context) {
        return dims.bake(context.datapackDimensions().registryOrThrow(Registries.LEVEL_STEM));
    }

    /** Vanilla runs tests up to 15M blocks out, past the overworld planet's edges (sky/overworld.json), where the space
     * code wraps entities round the globe mid-test; run them near the middle of the surface instead. */
    @Redirect(method = "startTests", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/RandomSource;nextIntBetweenInclusive(II)I"))
    private int tardisrb$testsOnThePlanet(RandomSource random, int min, int max) {
        return random.nextIntBetweenInclusive(-8000, 8000);
    }
}
