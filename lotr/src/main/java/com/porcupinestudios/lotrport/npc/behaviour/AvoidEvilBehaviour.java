package com.porcupinestudios.lotrport.npc.behaviour;

import com.porcupinestudios.lotrport.faction.Alignment;
import com.porcupinestudios.lotrport.npc.LotrNpc;

/**
 * LOTR's hobbits run from players at {@code avoid_alignment} (-100) or worse with their faction within
 * {@code avoid_range} (8), faster ({@code avoid_near_speed}) once the player is within 7 (LOTREntityAINPCAvoidEvilPlayer).
 * LOTR's children (dwarf and hobbit) also ran from anyone below 0; the port has no NPC children yet.
 */
public class AvoidEvilBehaviour implements CreatureBehaviour {
    @Override
    public void addGoals(LotrNpc npc) {
        npc.goals().addGoal(0, new FleeGoal(npc,
                p -> npc.faction().map(f -> Alignment.get(p, f)).orElse(0f)
                        <= CreatureBehaviour.data(npc, "avoid_alignment", -100),
                () -> npc.owner().isEmpty(),
                CreatureBehaviour.data(npc, "avoid_range", 8), CreatureBehaviour.data(npc, "avoid_speed", 1.5f),
                CreatureBehaviour.data(npc, "avoid_near_speed", 1.8f)));
    }
}
