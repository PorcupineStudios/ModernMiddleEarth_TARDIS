package com.porcupinestudios.lotrport.npc.behaviour;

import com.porcupinestudios.lotrport.npc.LotrNpc;
import com.porcupinestudios.lotrport.sound.LotrSounds;
import java.util.EnumSet;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Throwing at a target out of reach (LOTREntityAIRangedAttack, LOTREntityMountainTroll/SnowTroll/MarshWraith
 * onAttackModeChange): beyond {@code melee_range} and within {@code ranged_range} it stops and throws every
 * {@code throw_min}-{@code throw_max} ticks; while it does, the state LOTR's model reads ("isThrowingRocks",
 * "isThrowingSnow") is set, which raises the arms to throw.
 */
public class RangedBehaviour implements CreatureBehaviour {
    private final LotrProjectile.Kind kind;
    private final String flag;

    public RangedBehaviour(LotrProjectile.Kind kind, String flag) {
        this.kind = kind;
        this.flag = flag;
    }

    @Override
    public void addGoals(LotrNpc npc) {
        npc.goals().addGoal(0, new ThrowGoal(npc));
    }

    private final class ThrowGoal extends Goal {
        private final LotrNpc npc;
        private int cooldown;

        ThrowGoal(LotrNpc npc) {
            this.npc = npc;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        private boolean inRange() {
            LivingEntity t = npc.getTarget();
            if (t == null || !t.isAlive() || !npc.hasLineOfSight(t)) {
                return false;
            }
            double d = npc.distanceTo(t);
            return d > CreatureBehaviour.data(npc, "melee_range", 6f)
                    && d <= CreatureBehaviour.data(npc, "ranged_range", 24f);
        }

        @Override
        public boolean canUse() {
            return inRange();
        }

        @Override
        public void start() {
            npc.setState(flag, 1f);
            cooldown = 10;
        }

        @Override
        public void stop() {
            npc.setState(flag, 0f);
        }

        @Override
        public void tick() {
            LivingEntity t = npc.getTarget();
            if (t == null) {
                return;
            }
            npc.getNavigation().stop();
            npc.getLookControl().setLookAt(t, 30f, 30f);
            if (--cooldown > 0) {
                return;
            }
            float min = CreatureBehaviour.data(npc, "throw_min", 30f);
            float max = CreatureBehaviour.data(npc, "throw_max", 60f);
            cooldown = (int) (min + npc.random().nextFloat() * (max - min));
            float damage = CreatureBehaviour.data(npc, "throw_damage", kind == LotrProjectile.Kind.SNOW ? 3f : 5f);
            LotrProjectile p = new LotrProjectile(npc.level(), npc, kind, damage);
            double dx = t.getX() - npc.getX();
            double dy = t.getY(0.333) - p.getY();
            double dz = t.getZ() - npc.getZ();
            double flat = Math.sqrt(dx * dx + dz * dz);
            p.shoot(dx, dy + flat * (kind == LotrProjectile.Kind.WRAITH ? 0 : 0.2), dz, 1.5f, 1f);
            npc.level().addFreshEntity(p);
            npc.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            if (kind == LotrProjectile.Kind.WRAITH) {
                LotrSounds.playAt(npc.level(), npc.getX(), npc.getY(), npc.getZ(), "wraith.marsh_wraith_shoot",
                        SoundSource.HOSTILE, 1f, 1f / (npc.random().nextFloat() * 0.4f + 0.8f));
            }
        }

        @Override
        public boolean canContinueToUse() {
            return inRange();
        }
    }
}
