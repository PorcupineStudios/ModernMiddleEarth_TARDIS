package com.porcupinestudios.tardisrb.gravity.gc.mixin;

import com.porcupinestudios.tardisrb.space.SpaceChunks;
import com.porcupinestudios.tardisrb.space.SpaceContent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.chunk.storage.EntityStorage;
import net.minecraft.world.level.entity.ChunkEntities;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Ours: a space chunk with entities saved in it (a parked box, a drifting cart) stays in {@link SpaceContent}. */
@Mixin(EntityStorage.class)
public abstract class SpaceEntityStorageMixin {
    @Shadow
    @Final
    private ServerLevel level;

    @Inject(method = "storeEntities", at = @At("HEAD"))
    private void tardis_rb$indexEntities(ChunkEntities<Entity> entities, CallbackInfo ci) {
        if (SpaceChunks.sparse(level)) {
            SpaceContent.get(level).onEntities(entities.getPos(), !entities.isEmpty());
        }
    }
}
