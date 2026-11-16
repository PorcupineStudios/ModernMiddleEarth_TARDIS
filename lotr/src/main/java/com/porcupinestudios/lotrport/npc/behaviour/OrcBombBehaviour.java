package com.porcupinestudios.lotrport.npc.behaviour;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.content.thrown.OrcBomb;
import com.porcupinestudios.lotrport.npc.LotrNpc;
import java.util.EnumSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * LOTR's orc bombardiers and Uruk sappers carry one orc bomb (LOTREntityAIOrcPlaceBomb): with a target, they run at it
 * ({@code bomb_speed}) and, within {@code bomb_drop_range} (3) blocks, drop the lit bomb at their feet, hiss and shout,
 * and fight on without it. A bomb an NPC drops breaks blocks only where mobs may grief.
 * <p>shortcut: the bomb isn't drawn in their hand, and other orcs don't run from lit bombs yet.
 */
public class OrcBombBehaviour implements CreatureBehaviour {
    private static final String USED = "lotr_port.bomb_used";

    @Override
    public void addGoals(LotrNpc npc) {
        npc.goals().addGoal(0, new PlaceBombGoal(npc));
    }

    private static final class PlaceBombGoal extends Goal {
        private final LotrNpc npc;
        private LivingEntity target;
        private int repath;

        PlaceBombGoal(LotrNpc npc) {
            this.npc = npc;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            target = npc.getTarget();
            return target != null && !npc.getPersistentData().getBoolean(USED)
                    && npc.getNavigation().createPath(target, 0) != null;
        }

        @Override
        public boolean canContinueToUse() {
            return !npc.getPersistentData().getBoolean(USED) && npc.getTarget() != null && target.isAlive();
        }

        @Override
        public void start() {
            repath = 0;
        }

        @Override
        public void stop() {
            target = null;
            npc.getNavigation().stop();
        }

        @Override
        public void tick() {
            npc.getLookControl().setLookAt(target, 30, 30);
            if (npc.getSensing().hasLineOfSight(target) && --repath <= 0) {
                repath = 4 + npc.random().nextInt(7);
                npc.getNavigation().moveTo(target, CreatureBehaviour.data(npc, "bomb_speed", 1.4f));
            }
            if (npc.distanceTo(target) < CreatureBehaviour.data(npc, "bomb_drop_range", 3)) {
                var bomb = BuiltInRegistries.BLOCK.get(LotrPort.id("orc_bomb")).defaultBlockState();
                npc.level().addFreshEntity(new OrcBomb(npc.level(), npc.getX(), npc.getY() + 1, npc.getZ(), bomb, npc).byMob());
                npc.level().playSound(null, npc.getX(), npc.getY(), npc.getZ(), SoundEvents.TNT_PRIMED, SoundSource.HOSTILE, 1f, 1f);
                BuiltInRegistries.SOUND_EVENT.getOptional(LotrPort.id("orc.fire")).ifPresent(s -> npc.playSound(s, 1f,
                        (npc.random().nextFloat() - npc.random().nextFloat()) * 0.2f + 1f));
                npc.getPersistentData().putBoolean(USED, true);
            }
        }
    }
}
