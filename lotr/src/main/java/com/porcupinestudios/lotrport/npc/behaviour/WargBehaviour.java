package com.porcupinestudios.lotrport.npc.behaviour;

import com.porcupinestudios.lotrport.npc.LotrNpc;

/** A warg holds its tail lower the more it is hurt (LOTREntityWarg.getTailRotation: -1.2 x the health lost). */
public class WargBehaviour implements CreatureBehaviour {
    @Override
    public void tick(LotrNpc npc) {
        float max = npc.getMaxHealth();
        npc.setState("getTailRotation", max <= 0 ? 0f : (max - npc.getHealth()) / max * -1.2f);
    }
}
