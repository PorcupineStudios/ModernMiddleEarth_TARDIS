package com.porcupinestudios.tardisrb.gravity.gc.mixin;

import com.porcupinestudios.tardisrb.space.SpaceChunks;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Ours, not Gravity Changer's: a player flying fast through space loads no chunks, as a spectator who may not
 * generate chunks doesn't ({@link SpaceChunks}).
 */
@Mixin(ChunkMap.class)
public abstract class SpaceChunkMapMixin {
    @Inject(method = "skipPlayer", at = @At("HEAD"), cancellable = true)
    private void tardis_rb$skipFastInSpace(ServerPlayer player, CallbackInfoReturnable<Boolean> cir) {
        if (SpaceChunks.skips(player)) {
            cir.setReturnValue(true);
        }
    }
}
