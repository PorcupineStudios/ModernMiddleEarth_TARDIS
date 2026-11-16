package com.porcupinestudios.lotrport.npc.behaviour;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import com.porcupinestudios.lotrport.npc.LotrNpc;
import net.minecraft.world.entity.ai.goal.PanicGoal;

/**
 * LOTR's animals (deer, rabbits, birds, bears...): they wander, and when hurt most run off ({@code panic_speed});
 * those with {@code fights_back} 1 (bears, lions, rhinos, aurochs, crocodiles) turn on the attacker instead.
 */
public class AnimalBehaviour implements CreatureBehaviour {
    @Override
    public void addGoals(LotrNpc npc) {
        if (CreatureBehaviour.data(npc, "fights_back", 0) <= 0) {
            npc.goals().addGoal(1, new PanicGoal(npc, CreatureBehaviour.data(npc, "panic_speed", 1.25f)));
        }
    }

    /**
     * A hurt cub or calf of an animal that fights back (bears, lions, aurochs) sets the grown ones of its kind within
     * {@code defend_young} (12) blocks on whoever hurt it.
     */
    @Override
    public float hurt(LotrNpc npc, DamageSource source, float amount) {
        float range = CreatureBehaviour.data(npc, "defend_young", 0);
        if (range > 0 && npc.isBaby() && source.getEntity() instanceof LivingEntity attacker) {
            for (LotrNpc adult : npc.level().getEntitiesOfClass(LotrNpc.class, npc.getBoundingBox().inflate(range))) {
                if (adult != npc && !adult.isBaby() && adult.npcType() == npc.npcType()) {
                    adult.setTarget(attacker);
                }
            }
        }
        return amount;
    }

    @Override
    public void tick(LotrNpc npc) {
        if (CreatureBehaviour.data(npc, "fights_back", 0) <= 0 && npc.getTarget() != null) {
            npc.setTarget(null);
        }
    }
}
