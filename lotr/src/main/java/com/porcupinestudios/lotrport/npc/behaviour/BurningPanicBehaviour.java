package com.porcupinestudios.lotrport.npc.behaviour;

import com.porcupinestudios.lotrport.npc.LotrNpc;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

/**
 * LOTR's burning NPCs panic (LOTREntityAIBurningPanic): on fire and not fighting, one runs ({@code burn_panic_speed}
 * 1.5) to water it can stand in within 8 blocks (32 tries), else anywhere within 5 (4 up or down).
 */
public class BurningPanicBehaviour implements CreatureBehaviour {
    @Override
    public void addGoals(LotrNpc npc) {
        npc.goals().addGoal(0, new PanicGoal(npc));
    }

    private static final class PanicGoal extends Goal {
        private final LotrNpc npc;
        private Vec3 to;

        PanicGoal(LotrNpc npc) {
            this.npc = npc;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (!npc.isOnFire() || npc.getTarget() != null || npc.fireImmune()) {
                return false;
            }
            to = water();
            if (to == null) {
                to = DefaultRandomPos.getPos(npc, 5, 4);
            }
            return to != null;
        }

        private Vec3 water() {
            var r = npc.random();
            for (int i = 0; i < 32; i++) {
                BlockPos p = npc.blockPosition().offset(r.nextInt(17) - 8, r.nextInt(17) - 8, r.nextInt(17) - 8);
                var level = npc.level();
                if (!level.getBlockState(p).isSolid() && !level.getBlockState(p.above()).isSolid()
                        && level.getFluidState(p.below()).is(Fluids.WATER)) {
                    return Vec3.atBottomCenterOf(p);
                }
            }
            return null;
        }

        @Override
        public void start() {
            npc.getNavigation().moveTo(to.x, to.y, to.z, CreatureBehaviour.data(npc, "burn_panic_speed", 1.5f));
        }

        @Override
        public boolean canContinueToUse() {
            return npc.isOnFire() && npc.getTarget() == null && !npc.getNavigation().isDone();
        }
    }
}
