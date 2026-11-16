package com.porcupinestudios.lotrport.npc.behaviour;

import com.porcupinestudios.lotrport.npc.LotrNpc;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;

/**
 * Particles a LOTR creature gives off all the time (the Utumno fire warg's flames and smoke: each tick one particle
 * somewhere in its body, {@code trail_flame} of them flames, the rest smoke).
 */
public class TrailBehaviour implements CreatureBehaviour {
    @Override
    public void tick(LotrNpc npc) {
        if (!(npc.level() instanceof ServerLevel level)) {
            return;
        }
        var r = npc.random();
        boolean flame = r.nextFloat() < CreatureBehaviour.data(npc, "trail_flame", 0);
        level.sendParticles(flame ? ParticleTypes.FLAME : ParticleTypes.SMOKE,
                npc.getX() + (r.nextDouble() - 0.5) * npc.getBbWidth(), npc.getY() + r.nextDouble() * npc.getBbHeight(),
                npc.getZ() + (r.nextDouble() - 0.5) * npc.getBbWidth(), 1, 0, 0, 0, 0);
    }
}
