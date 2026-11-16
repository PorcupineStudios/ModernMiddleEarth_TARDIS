package com.porcupinestudios.lotrport.npc.behaviour;

import com.porcupinestudios.lotrport.npc.LotrNpc;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;

/**
 * LOTR's crocodiles (LOTREntityCrocodile): they float up in water, go for players only in the dark (light under
 * {@code hunt_light}, 8, at the crocodile), and with no target, 1 in {@code prey_chance} (1000) a tick, set on a
 * harmless animal within 12 (6 up or down). A bite snaps the jaws ({@code getSnapTime}, 20 ticks, and the
 * {@code attack} sound crocodile.snap).
 */
public class CrocodileBehaviour implements CreatureBehaviour {
    @Override
    public void addGoals(LotrNpc npc) {
        npc.targets().addGoal(1, new NearestAttackableTargetGoal<>(npc, Player.class, true,
                p -> !p.isSpectator() && !((Player) p).isCreative() && dark(npc)));
    }

    private static boolean dark(LotrNpc npc) {
        return npc.level().getMaxLocalRawBrightness(npc.blockPosition()) < CreatureBehaviour.data(npc, "hunt_light", 8);
    }

    @Override
    public void tick(LotrNpc npc) {
        if (npc.isInWater()) {
            npc.setDeltaMovement(npc.getDeltaMovement().add(0, 0.02, 0));
        }
        int snap = (int) npc.state("getSnapTime");
        if (snap > 0) {
            npc.setState("getSnapTime", snap - 1);
        }
        if (npc.getTarget() == null && npc.random().nextInt((int) CreatureBehaviour.data(npc, "prey_chance", 1000)) == 0) {
            List<PathfinderMob> prey = npc.level().getEntitiesOfClass(PathfinderMob.class,
                    npc.getBoundingBox().inflate(12, 6, 12), a -> a != npc && PreyChaseBehaviour.harmless(a));
            if (!prey.isEmpty()) {
                npc.setTarget(prey.get(npc.random().nextInt(prey.size())));
            }
        }
    }

    @Override
    public void hit(LotrNpc npc, Entity target) {
        npc.setState("getSnapTime", 20);
    }
}
