package com.porcupinestudios.lotrport.mixin;

import com.porcupinestudios.lotrport.client.MenuBackdrop;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** LOTR's title screen backdrop (the drifting map, {@link MenuBackdrop}) in place of the panorama, unless set off. */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin {
    @Shadow
    private float panoramaFade;

    @Inject(method = "renderPanorama", at = @At("HEAD"), cancellable = true)
    private void lotr_port$mapBackdrop(GuiGraphics g, float partialTick, CallbackInfo ci) {
        TitleScreen self = (TitleScreen) (Object) this;
        if (MenuBackdrop.render(g, self.width, self.height, panoramaFade)) {
            ci.cancel();
        }
    }
}
