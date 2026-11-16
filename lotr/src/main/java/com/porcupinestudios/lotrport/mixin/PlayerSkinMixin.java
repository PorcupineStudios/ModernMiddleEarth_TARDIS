package com.porcupinestudios.lotrport.mixin;

import com.porcupinestudios.lotrport.LotrClientConfig;
import com.porcupinestudios.lotrport.player.Races;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Draws a player with their race's skin (player.Races) in place of their own; cape, elytra and arm width stay. */
@Mixin(AbstractClientPlayer.class)
public abstract class PlayerSkinMixin {
    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void lotr_port$raceSkin(CallbackInfoReturnable<PlayerSkin> cir) {
        ResourceLocation race = Races.CLIENT_SKINS.get(((AbstractClientPlayer) (Object) this).getUUID());
        if (race != null && LotrClientConfig.RACE_SKINS.get()) {
            PlayerSkin s = cir.getReturnValue();
            cir.setReturnValue(new PlayerSkin(race, null, s.capeTexture(), s.elytraTexture(), s.model(), s.secure()));
        }
    }
}
