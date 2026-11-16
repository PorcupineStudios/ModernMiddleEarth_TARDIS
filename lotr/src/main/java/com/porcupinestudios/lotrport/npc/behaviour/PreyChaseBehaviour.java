package com.porcupinestudios.lotrport.npc.behaviour;

import com.porcupinestudios.lotrport.npc.LotrNpc;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.phys.Vec3;

/**
 * LOTR's lions chase prey for sport (LOTREntityAILionChase): a grown lion, 1 in {@code chase_chance} (800) a tick,
 * picks an animal within 12 that can't fight (no attack damage) and runs after it ({@code chase_speed} 1.5) for
 * 300-699 ticks or until it is 16 away; the prey bolts (speed 2) whenever it stops. The chase itself does no harm.
 */
public class PreyChaseBehaviour implements CreatureBehaviour {
    @Override
    public void addGoals(LotrNpc npc) {
        npc.goals().addGoal(6, new ChaseGoal(npc));
    }

    /**
     * Prey: an animal that can't fight. LOTR's test was "no attack damage"; here LOTR's own animals are NPCs with an
     * attack, so those that run instead of fighting back count too.
     */
    static boolean harmless(PathfinderMob mob) {
        if (mob instanceof LotrNpc n) {
            return n.behaviours().stream().anyMatch(b -> b instanceof AnimalBehaviour)
                    && CreatureBehaviour.data(n, "fights_back", 0) <= 0;
        }
        return mob instanceof Animal && mob.getAttribute(Attributes.ATTACK_DAMAGE) == null;
    }

    private static final class ChaseGoal extends Goal {
        private final LotrNpc npc;
        private PathfinderMob prey;
        private int timer;
        private int repath;

        ChaseGoal(LotrNpc npc) {
            this.npc = npc;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (npc.isBaby() || npc.getTarget() != null
                    || npc.random().nextInt((int) CreatureBehaviour.data(npc, "chase_chance", 800)) != 0) {
                return false;
            }
            List<PathfinderMob> prey = npc.level().getEntitiesOfClass(PathfinderMob.class, npc.getBoundingBox().inflate(12),
                    a -> a != npc && harmless(a));
            if (prey.isEmpty()) {
                return false;
            }
            this.prey = prey.get(npc.random().nextInt(prey.size()));
            return true;
        }

        @Override
        public void start() {
            timer = 300 + npc.random().nextInt(400);
            repath = 0;
        }

        @Override
        public boolean canContinueToUse() {
            return prey.isAlive() && timer > 0 && npc.distanceToSqr(prey) < 256 && npc.getTarget() == null;
        }

        @Override
        public void tick() {
            timer--;
            npc.getLookControl().setLookAt(prey, 30, 30);
            if (--repath <= 0) {
                repath = 10;
                npc.getNavigation().moveTo(prey, CreatureBehaviour.data(npc, "chase_speed", 1.5f));
            }
            if (prey.getNavigation().isDone()) {
                Vec3 away = DefaultRandomPos.getPosAway(prey, 16, 7, npc.position());
                if (away != null) {
                    prey.getNavigation().moveTo(away.x, away.y, away.z, 2);
                }
            }
        }
    }
}
