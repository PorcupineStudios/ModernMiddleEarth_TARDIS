package com.porcupinestudios.tardisrb.gravity.gc.mixin;

import com.porcupinestudios.tardisrb.space.SpaceChunks;
import com.porcupinestudios.tardisrb.space.SpaceContent;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Ours, not Gravity Changer's. In space players load no chunks the vanilla way, as a spectator who may not generate
 * chunks doesn't ({@link SpaceChunks} loads what they need), and empty space chunks are never saved
 * ({@link SpaceContent}).
 */
@Mixin(ChunkMap.class)
public abstract class SpaceChunkMapMixin {
    @Shadow
    @Final
    ServerLevel level;

    @Inject(method = "skipPlayer", at = @At("HEAD"), cancellable = true)
    private void tardis_rb$skipInSpace(ServerPlayer player, CallbackInfoReturnable<Boolean> cir) {
        if (SpaceChunks.skips(player)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "save(Lnet/minecraft/world/level/chunk/ChunkAccess;)Z", at = @At("HEAD"), cancellable = true)
    private void tardis_rb$skipEmptySpace(ChunkAccess chunk, CallbackInfoReturnable<Boolean> cir) {
        if (SpaceChunks.sparse(level) && !SpaceContent.get(level).onSave(chunk)) {
            cir.setReturnValue(false);
        }
    }
}
