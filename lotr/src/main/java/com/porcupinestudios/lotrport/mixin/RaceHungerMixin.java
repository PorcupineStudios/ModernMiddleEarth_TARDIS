package com.porcupinestudios.lotrport.mixin;

import com.porcupinestudios.lotrport.player.Races;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** A race's hunger drain (player_race "hunger", or the admin screen's tune) scales every bit of food exhaustion. */
@Mixin(Player.class)
public abstract class RaceHungerMixin {
    @ModifyVariable(method = "causeFoodExhaustion", at = @At("HEAD"), argsOnly = true)
    private float lotr_port$raceHunger(float exhaustion) {
        return (Object) this instanceof ServerPlayer sp ? exhaustion * Races.hunger(sp) : exhaustion;
    }
}
