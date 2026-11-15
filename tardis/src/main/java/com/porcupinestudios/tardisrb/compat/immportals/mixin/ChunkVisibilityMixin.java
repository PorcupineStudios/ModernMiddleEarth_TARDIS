package com.porcupinestudios.tardisrb.compat.immportals.mixin;

import com.porcupinestudios.tardisrb.space.SpaceChunks;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.chunk_loading.ChunkLoader;
import qouteall.imm_ptl.core.chunk_loading.ChunkVisibility;

/**
 * Immersive Portals loads and sends chunks itself, from each player's loaders (around the player and through nearby
 * portals). In space a player gets only {@link SpaceChunks}' loaders instead: none while flying fast; in sparse space
 * the chunks near them and those holding something within view distance (one loader each).
 */
@Mixin(value = ChunkVisibility.class, remap = false)
public abstract class ChunkVisibilityMixin {
    @Inject(method = "foreachBaseChunkLoaders", at = @At("HEAD"), cancellable = true)
    private static void tardis_rb$spaceLoaders(ServerPlayer player, Consumer<ChunkLoader> consumer,
            CallbackInfo ci) {
        if (SpaceChunks.skips(player)) {
            ci.cancel();
            SpaceChunks.loaders(player, (ChunkPos at, Integer radius) ->
                    consumer.accept(new ChunkLoader(player.level().dimension(), at.x, at.z, radius)));
        }
    }
}
