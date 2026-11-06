package com.porcupinestudios.lotrport.npc.behaviour;

import com.porcupinestudios.lotrport.npc.LotrNpc;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * A barrow-wight's touch (LOTREntityBarrowWight.attackEntityAsMob): slowness, mining fatigue and wither, each for
 * difficulty x (difficulty + 5) / 2 seconds.
 */
public class WightBehaviour implements CreatureBehaviour {
    @Override
    public void hit(LotrNpc npc, Entity target) {
        if (!(target instanceof LivingEntity living)) {
            return;
        }
        int d = npc.level().getDifficulty().getId();
        int seconds = d * (d + 5) / 2;
        if (seconds <= 0) {
            return;
        }
        living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, seconds * 20, 0));
        living.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, seconds * 20, 0));
        living.addEffect(new MobEffectInstance(MobEffects.WITHER, seconds * 20, 0));
    }
}
