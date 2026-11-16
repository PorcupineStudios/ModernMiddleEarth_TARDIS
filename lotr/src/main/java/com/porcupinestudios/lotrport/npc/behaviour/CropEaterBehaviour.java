package com.porcupinestudios.lotrport.npc.behaviour;

import com.porcupinestudios.lotrport.npc.LotrNpc;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * LOTR's rabbits eat crops (LOTREntityAIRabbitEatCrops): with mob griefing on, 1 in 20 a tick one looks for a crop
 * within 16 (8 up or down) that no farmhand is within 16 of, hops there ({@code eat_speed}) and nibbles it for
 * {@code eat_ticks} (60), which destroys it. It gives up after 200 ticks of walking.
 */
public class CropEaterBehaviour implements CreatureBehaviour {
    @Override
    public void addGoals(LotrNpc npc) {
        npc.goals().addGoal(3, new EatCropsGoal(npc));
    }

    private static final class EatCropsGoal extends Goal {
        private final LotrNpc npc;
        private BlockPos crop;
        private int walking;
        private int eating;

        EatCropsGoal(LotrNpc npc) {
            this.npc = npc;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!Crops.canGrief(npc.level()) || npc.random().nextInt(20) != 0) {
                return false;
            }
            crop = Crops.find(npc, 16, 8, false, p -> !Crops.farmhandNear(npc.level(), p));
            return crop != null;
        }

        @Override
        public boolean canContinueToUse() {
            return Crops.canGrief(npc.level()) && walking < 200 && eating < CreatureBehaviour.data(npc, "eat_ticks", 60)
                    && Crops.edible(npc.level(), crop, false) && !Crops.farmhandNear(npc.level(), crop);
        }

        @Override
        public void stop() {
            walking = 0;
            eating = 0;
            npc.setState("isRabbitEating", 0);
        }

        @Override
        public void tick() {
            if (npc.distanceToSqr(crop.getX() + 0.5, crop.getY(), crop.getZ() + 0.5) > 2.25) {
                npc.setState("isRabbitEating", 0);
                npc.getLookControl().setLookAt(crop.getX() + 0.5, crop.getY(), crop.getZ() + 0.5);
                if (walking++ % 10 == 0) {
                    npc.getNavigation().moveTo(crop.getX() + 0.5, crop.getY(), crop.getZ() + 0.5,
                            CreatureBehaviour.data(npc, "eat_speed", 1));
                }
                return;
            }
            npc.setState("isRabbitEating", 1);
            if (++eating % 6 == 0) {
                npc.playSound(SoundEvents.GENERIC_EAT, 1f, 1f + (npc.random().nextFloat() - npc.random().nextFloat()) * 0.2f);
            }
            if (eating >= CreatureBehaviour.data(npc, "eat_ticks", 60)) {
                Crops.eat(npc.level(), crop);
            }
        }
    }
}
