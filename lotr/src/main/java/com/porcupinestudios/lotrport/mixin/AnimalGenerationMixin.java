package com.porcupinestudios.lotrport.mixin;

import com.porcupinestudios.lotrport.npc.NpcSpawner;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * LOTR's animals (deer, aurochs, bears... NPC types, so not on the biomes' vanilla spawn lists) come with the land:
 * after vanilla's creatures are placed in a newly generated chunk, the biome's npc_animals table may add a group
 * ({@link NpcSpawner#populate}).
 */
@Mixin(NoiseBasedChunkGenerator.class)
public abstract class AnimalGenerationMixin {
    @Inject(method = "spawnOriginalMobs", at = @At("TAIL"))
    private void lotrPort$animals(WorldGenRegion region, CallbackInfo ci) {
        NpcSpawner.populate(region);
    }
}
