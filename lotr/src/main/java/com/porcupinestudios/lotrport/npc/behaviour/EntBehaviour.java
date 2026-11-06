package com.porcupinestudios.lotrport.npc.behaviour;

import com.porcupinestudios.lotrport.npc.LotrNpc;
import com.porcupinestudios.lotrport.npc.NpcType;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * LOTR's ents (LOTREntityEnt, LOTREntityAIEntHealSapling): they blink (eyes shut for 30 ticks, 1 in 400 a tick),
 * grow 0-{@code max_branches} extra branches on the head, and now and then ({@code heal_interval}, 300 ticks)
 * walk to a sapling within {@code heal_range} (24) blocks and tend it for {@code heal_ticks} (160) with golden
 * leaves falling, then it grows.
 */
public class EntBehaviour implements CreatureBehaviour {
    @Override
    public void init(LotrNpc npc, NpcType type) {
        npc.setState("getExtraHeadBranches",
                npc.random().nextInt((int) CreatureBehaviour.data(type, "max_branches", 3) + 1));
    }

    @Override
    public void addGoals(LotrNpc npc) {
        npc.goals().addGoal(4, new HealSaplingGoal(npc));
    }

    @Override
    public void tick(LotrNpc npc) {
        int eyes = (int) npc.state("eyesClosed");
        if (eyes > 0) {
            npc.setState("eyesClosed", eyes - 1);
        } else if (npc.random().nextInt(400) == 0) {
            npc.setState("eyesClosed", 30);
        }
    }

    private static final class HealSaplingGoal extends Goal {
        private final LotrNpc npc;
        private BlockPos sapling;
        private int healing;
        private int wait;

        HealSaplingGoal(LotrNpc npc) {
            this.npc = npc;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (npc.getTarget() != null || --wait > 0) {
                return false;
            }
            wait = (int) CreatureBehaviour.data(npc, "heal_interval", 300);
            int r = (int) CreatureBehaviour.data(npc, "heal_range", 24);
            for (BlockPos p : BlockPos.randomInCube(npc.random(), 64, npc.blockPosition(), r)) {
                if (npc.level().getBlockState(p).getBlock() instanceof SaplingBlock
                        || npc.level().getBlockState(p).getBlock().getClass().getSimpleName().contains("Sapling")) {
                    sapling = p.immutable();
                    return true;
                }
            }
            return false;
        }

        @Override
        public void start() {
            healing = 0;
            npc.getNavigation().moveTo(sapling.getX() + 0.5, sapling.getY(), sapling.getZ() + 0.5, 1.0);
        }

        @Override
        public boolean canContinueToUse() {
            return sapling != null && npc.getTarget() == null
                    && healing < CreatureBehaviour.data(npc, "heal_ticks", 160);
        }

        @Override
        public void tick() {
            npc.getLookControl().setLookAt(sapling.getX() + 0.5, sapling.getY() + 0.5, sapling.getZ() + 0.5);
            if (npc.distanceToSqr(sapling.getCenter()) > 9) {
                if (npc.getNavigation().isDone()) {
                    npc.getNavigation().moveTo(sapling.getX() + 0.5, sapling.getY(), sapling.getZ() + 0.5, 1.0);
                }
                return;
            }
            npc.getNavigation().stop();
            npc.setState("isHealingSapling", 1f);
            healing++;
            if (npc.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, sapling.getX() + 0.5, sapling.getY() + 0.8,
                        sapling.getZ() + 0.5, 1, 0.3, 0.3, 0.3, 0);
                if (healing >= CreatureBehaviour.data(npc, "heal_ticks", 160)) {
                    BlockState state = level.getBlockState(sapling);
                    if (state.getBlock() instanceof BonemealableBlock grow) {
                        for (int i = 0; i < 8 && level.getBlockState(sapling) == state; i++) {
                            grow.performBonemeal(level, npc.random(), sapling, state);
                        }
                    }
                }
            }
        }

        @Override
        public void stop() {
            npc.setState("isHealingSapling", 0f);
            sapling = null;
        }
    }
}
