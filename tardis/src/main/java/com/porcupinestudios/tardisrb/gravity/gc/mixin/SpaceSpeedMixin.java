package com.porcupinestudios.tardisrb.gravity.gc.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.porcupinestudios.tardisrb.space.SpaceTravel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Ours, not Gravity Changer's: players in space (empty levels) may move faster than vanilla's "moved too quickly"
 * limit (Aaron 2026-11-04: navigate space faster), as a single-player owner already may. Speed is still capped by
 * {@code space.motion.max_speed}.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class SpaceSpeedMixin {
    @Shadow
    public ServerPlayer player;

    @ModifyExpressionValue(method = "handleMovePlayer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;isSingleplayerOwner()Z"))
    private boolean tardis_rb$fastInSpace(boolean owner) {
        return owner || SpaceTravel.isSpace(this.player.level());
    }
}
