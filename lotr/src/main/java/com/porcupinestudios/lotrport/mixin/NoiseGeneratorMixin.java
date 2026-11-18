package com.porcupinestudios.lotrport.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.porcupinestudios.lotrport.world.map.NoiseTop;
import net.minecraft.core.Holder;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Middle-earth's chunks are filled, and their ground found, only up to where its terrain can reach
 * ({@link NoiseTop}): the world is 704 blocks tall for the mountain ranges, and the sky over low land would otherwise
 * cost as much to work out as the ground (Aaron 2026-11-15: LOTR's fast travel needs fast chunk loading). Other
 * generators are untouched.
 */
@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NoiseGeneratorMixin {
    @Shadow
    @Final
    private Holder<NoiseGeneratorSettings> settings;

    /** Filling a chunk: the cells it fills. */
    @WrapOperation(method = "fillFromNoise", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/"
            + "NoiseSettings;clampToHeightAccessor(Lnet/minecraft/world/level/LevelHeightAccessor;)"
            + "Lnet/minecraft/world/level/levelgen/NoiseSettings;"))
    private NoiseSettings lotr_port$cutFill(NoiseSettings noise, LevelHeightAccessor level,
            Operation<NoiseSettings> original, @Local(argsOnly = true) ChunkAccess chunk) {
        NoiseSettings clamped = original.call(noise, level);
        return NoiseTop.isMiddleEarth(settings)
                ? NoiseTop.forChunk(clamped, chunk.getPos().x, chunk.getPos().z) : clamped;
    }

    /** The chunk's noise chunk (used to fill it, and for its surface and carvers): the same cells. */
    @WrapOperation(method = "createNoiseChunk", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/"
            + "levelgen/NoiseChunk;forChunk(Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/world/level/"
            + "levelgen/RandomState;Lnet/minecraft/world/level/levelgen/DensityFunctions$BeardifierOrMarker;"
            + "Lnet/minecraft/world/level/levelgen/NoiseGeneratorSettings;Lnet/minecraft/world/level/levelgen/"
            + "Aquifer$FluidPicker;Lnet/minecraft/world/level/levelgen/blending/Blender;)"
            + "Lnet/minecraft/world/level/levelgen/NoiseChunk;"))
    private NoiseChunk lotr_port$cutNoiseChunk(ChunkAccess chunk, RandomState random,
            DensityFunctions.BeardifierOrMarker beardifier, NoiseGeneratorSettings noiseSettings,
            Aquifer.FluidPicker fluids, Blender blender, Operation<NoiseChunk> original) {
        if (NoiseTop.isMiddleEarth(settings)) {
            NoiseSettings cut = NoiseTop.forChunk(noiseSettings.noiseSettings(), chunk.getPos().x, chunk.getPos().z);
            if (cut != noiseSettings.noiseSettings()) {
                noiseSettings = new NoiseGeneratorSettings(cut, noiseSettings.defaultBlock(),
                        noiseSettings.defaultFluid(), noiseSettings.noiseRouter(), noiseSettings.surfaceRule(),
                        noiseSettings.spawnTarget(), noiseSettings.seaLevel(), noiseSettings.disableMobGeneration(),
                        noiseSettings.aquifersEnabled(), noiseSettings.oreVeinsEnabled(),
                        noiseSettings.useLegacyRandomSource());
            }
        }
        return original.call(chunk, random, beardifier, noiseSettings, fluids, blender);
    }

    /** Finding a column's ground (structures, spawning, getBaseHeight): the scan starts at its cell's top. */
    @WrapOperation(method = "iterateNoiseColumn", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/"
            + "levelgen/NoiseSettings;clampToHeightAccessor(Lnet/minecraft/world/level/LevelHeightAccessor;)"
            + "Lnet/minecraft/world/level/levelgen/NoiseSettings;"))
    private NoiseSettings lotr_port$cutColumn(NoiseSettings noise, LevelHeightAccessor level,
            Operation<NoiseSettings> original, @Local(argsOnly = true, ordinal = 0) int x,
            @Local(argsOnly = true, ordinal = 1) int z) {
        NoiseSettings clamped = original.call(noise, level);
        return NoiseTop.isMiddleEarth(settings) ? NoiseTop.forColumn(clamped, x, z) : clamped;
    }

    /** Ground scans run with Middle-earth's noise caves off (world/map/CavesOn): caves are under the ground. */
    @com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method = "iterateNoiseColumn")
    private java.util.OptionalInt lotr_port$scanWithoutCaves(LevelHeightAccessor level, RandomState random, int x,
            int z, org.apache.commons.lang3.mutable.MutableObject<net.minecraft.world.level.NoiseColumn> column,
            java.util.function.Predicate<net.minecraft.world.level.block.state.BlockState> stop,
            Operation<java.util.OptionalInt> original) {
        if (!NoiseTop.isMiddleEarth(settings)) {
            return original.call(level, random, x, z, column, stop);
        }
        return com.porcupinestudios.lotrport.world.map.CavesOn.scanning(
                () -> original.call(level, random, x, z, column, stop));
    }
}
