package com.porcupinestudios.lotrport.npc.behaviour;

import com.porcupinestudios.lotrport.npc.LotrNpc;
import com.porcupinestudios.lotrport.npc.NpcType;

/**
 * Half-trolls look different from each other (LOTREntityHalfTroll.onSpawnWithEgg): a mohawk or not, horns or not,
 * and full horns for some of the horned.
 */
public class HalfTrollBehaviour implements CreatureBehaviour {
    @Override
    public void init(LotrNpc npc, NpcType type) {
        boolean horns = npc.random().nextBoolean();
        npc.setState("hasMohawk", npc.random().nextBoolean() ? 1f : 0f);
        npc.setState("hasHorns", horns ? 1f : 0f);
        npc.setState("hasFullHorns", horns && npc.random().nextBoolean() ? 1f : 0f);
    }
}
