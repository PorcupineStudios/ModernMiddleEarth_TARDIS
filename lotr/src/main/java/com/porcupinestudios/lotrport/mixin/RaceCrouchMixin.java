package com.porcupinestudios.lotrport.mixin;

import com.porcupinestudios.lotrport.player.Races;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A race's crouch_height on the crouching hitbox. Here, not in NeoForge's size event: vanilla picks crouch or crawl
 * from getDimensions(pose), which the event never sees (a hobbit crawled in 1-block gaps).
 */
@Mixin(Player.class)
public abstract class RaceCrouchMixin {
    @Inject(method = "getDefaultDimensions", at = @At("RETURN"), cancellable = true)
    private void lotr_port$raceCrouch(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        if (pose == Pose.CROUCHING) {
            float f = Races.crouchFactor((Player) (Object) this);
            if (f != 1f) {
                cir.setReturnValue(cir.getReturnValue().scale(1f, f));
            }
        }
    }
}
