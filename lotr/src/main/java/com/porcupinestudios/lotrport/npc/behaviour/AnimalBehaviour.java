package com.porcupinestudios.lotrport.npc.behaviour;

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

    @Override
    public void tick(LotrNpc npc) {
        if (CreatureBehaviour.data(npc, "fights_back", 0) <= 0 && npc.getTarget() != null) {
            npc.setTarget(null);
        }
    }
}
