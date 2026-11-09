package com.porcupinestudios.tardisrb.compat.immportals.mixin;

import com.porcupinestudios.tardisrb.space.SpaceChunks;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.chunk_loading.ChunkLoader;
import qouteall.imm_ptl.core.chunk_loading.ChunkVisibility;

/**
 * Immersive Portals loads and sends chunks itself, from each player's loaders (around the player and through nearby
 * portals). A player flying fast through space gets none ({@link SpaceChunks}).
 */
@Mixin(value = ChunkVisibility.class, remap = false)
public abstract class ChunkVisibilityMixin {
    @Inject(method = "foreachBaseChunkLoaders", at = @At("HEAD"), cancellable = true)
    private static void tardis_rb$noLoadersWhenFastInSpace(ServerPlayer player, Consumer<ChunkLoader> consumer,
            CallbackInfo ci) {
        if (SpaceChunks.skips(player)) {
            ci.cancel();
        }
    }
}
