package com.porcupinestudios.ringbearer.harness.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.porcupinestudios.ringbearer.harness.content.Harnessing;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A harnessed horse can't take a saddle: the harness is worn in its place. {@code isSaddleable} gates both using a
 * saddle on the horse and the saddle slot of its inventory screen.
 */
@Mixin(AbstractHorse.class)
abstract class AbstractHorseSaddleMixin {
    @ModifyReturnValue(method = "isSaddleable", at = @At("RETURN"))
    private boolean ringbearer_harness$notWhileHarnessed(boolean saddleable) {
        return saddleable && !Harnessing.wears((AbstractHorse) (Object) this);
    }
}
