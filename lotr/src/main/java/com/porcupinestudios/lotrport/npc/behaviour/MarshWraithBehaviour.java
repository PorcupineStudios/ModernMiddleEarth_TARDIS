package com.porcupinestudios.lotrport.npc.behaviour;

import com.porcupinestudios.lotrport.npc.LotrNpc;
import com.porcupinestudios.lotrport.npc.NpcType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * LOTR's marsh-wraiths (LOTREntityMarshWraith): they rise out of the marsh, fading in over 30 ticks, hover over
 * water and mud, trail smoke, cast slowing bolts from range, and fade away over 30 ticks once they have no target
 * (despawning {@code despawn_ticks}, 100, after losing it).
 */
public class MarshWraithBehaviour extends RangedBehaviour {
    private int lostTarget;

    public MarshWraithBehaviour() {
        super(LotrProjectile.Kind.WRAITH, "isCasting");
    }

    @Override
    public void init(LotrNpc npc, NpcType type) {
        npc.setState("getSpawnFadeTime", 0);
        npc.setState("getDeathFadeTime", 0);
    }

    @Override
    public void tick(LotrNpc npc) {
        int spawn = (int) npc.state("getSpawnFadeTime");
        if (spawn < 30) {
            npc.setState("getSpawnFadeTime", spawn + 1);
        }
        int death = (int) npc.state("getDeathFadeTime");
        if (death > 0) {
            npc.setState("getDeathFadeTime", death - 1);
            if (death - 1 == 0) {
                npc.discard();
                return;
            }
        }
        if (npc.isInWater() || npc.isInLiquid() || npc.level().getBlockState(npc.blockPosition()).getFluidState()
                .isSource()) {
            npc.setDeltaMovement(npc.getDeltaMovement().x, 0.04, npc.getDeltaMovement().z);
        }
        if (npc.random().nextBoolean() && npc.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.SMOKE, npc.getX(), npc.getY() + npc.random().nextDouble()
                    * npc.getBbHeight(), npc.getZ(), 1, 0.25, 0, 0.25, 0);
        }
        if (spawn >= 30 && death == 0) {
            if (npc.getTarget() == null || !npc.getTarget().isAlive()) {
                if (++lostTarget >= CreatureBehaviour.data(npc, "despawn_ticks", 100)) {
                    npc.setState("getDeathFadeTime", 30);
                }
            } else {
                lostTarget = 0;
            }
        }
    }

    @Override
    public boolean climbs(LotrNpc npc) {
        return false;
    }

    static Vec3 none() {
        return Vec3.ZERO;
    }
}
