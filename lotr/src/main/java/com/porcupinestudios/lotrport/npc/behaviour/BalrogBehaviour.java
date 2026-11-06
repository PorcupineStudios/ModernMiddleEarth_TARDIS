package com.porcupinestudios.lotrport.npc.behaviour;

import com.porcupinestudios.lotrport.npc.LotrNpc;
import com.porcupinestudios.lotrport.npc.NpcType;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.phys.Vec3;

/**
 * LOTR's Balrog (LOTREntityBalrog, LOTREntityAIBalrogCharge):
 * <ul>
 * <li>Wreathed in flame while dry: immune to fire, flames and smoke pour off it, and 1 in {@code fire_chance}
 * (80) ticks it sets up to 24 spots around it (8 blocks out, 4 down, 8 up) alight. Its bare-handed blows set the
 * target burning for 5 s and knock it back.</li>
 * <li>It heals 1 health every 10 ticks.</li>
 * <li>Kept from its target for {@code charge_after} (200) ticks, it charges: leans forward (the model's lean grows
 * over 10 ticks), runs at it at {@code charge_speed} (2.0) and hits everything within 2.5 blocks for its charge
 * damage (attack + 2) with a knock-back.</li>
 * </ul>
 */
public class BalrogBehaviour implements CreatureBehaviour {
    private int frustration;
    private int lean;

    @Override
    public void init(LotrNpc npc, NpcType type) {
        npc.setState("fireImmune", 1f);
    }

    @Override
    public void addGoals(LotrNpc npc) {
        npc.setState("fireImmune", 1f);
        npc.goals().addGoal(0, new ChargeGoal(npc));
    }

    @Override
    public void tick(LotrNpc npc) {
        if (npc.getHealth() < npc.getMaxHealth() && npc.tickCount % 10 == 0) {
            npc.heal(1f);
        }
        boolean charging = npc.state("isBalrogCharging") > 0;
        if (npc.getTarget() == null) {
            frustration = 0;
        } else if (!charging) {
            frustration++;
        } else {
            frustration = 0;
        }
        lean = charging ? Math.min(10, lean + 1) : Math.max(0, lean - 1);
        npc.setState("getInterpolatedChargeLean", lean / 10f);
        boolean flame = npc.isAlive() && !npc.isInWaterRainOrBubble();
        npc.setState("isWreathedInFlame", flame ? 1f : 0f);
        if (!flame || !(npc.level() instanceof ServerLevel level)) {
            return;
        }
        level.sendParticles(npc.random().nextInt(3) == 0 ? ParticleTypes.FLAME : ParticleTypes.LARGE_SMOKE,
                npc.getX(), npc.getY() + npc.getBbHeight() * 0.5, npc.getZ(), 3, npc.getBbWidth() * 0.5,
                npc.getBbHeight() * 0.5, npc.getBbWidth() * 0.5, 0);
        if (npc.random().nextInt((int) CreatureBehaviour.data(npc, "fire_chance", 80)) == 0) {
            for (int i = 0; i < 24; i++) {
                BlockPos p = npc.blockPosition().offset(Mth.nextInt(npc.random(), -8, 8),
                        Mth.nextInt(npc.random(), -4, 8), Mth.nextInt(npc.random(), -8, 8));
                if (level.getBlockState(p).canBeReplaced() && BaseFireBlock.canBePlacedAt(level, p,
                        net.minecraft.core.Direction.UP)) {
                    level.setBlockAndUpdate(p, BaseFireBlock.getState(level, p));
                }
            }
        }
    }

    @Override
    public void hit(LotrNpc npc, Entity target) {
        frustration = 0;
        if (npc.getMainHandItem().isEmpty()) {
            target.igniteForSeconds(5);
        }
        float yaw = npc.getYRot() * Mth.DEG_TO_RAD;
        target.push(-Mth.sin(yaw) * 0.5, 0.1, Mth.cos(yaw) * 0.5);
    }

    private final class ChargeGoal extends Goal {
        private final LotrNpc npc;
        private int time;

        ChargeGoal(LotrNpc npc) {
            this.npc = npc;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return npc.getTarget() != null && frustration >= CreatureBehaviour.data(npc, "charge_after", 200);
        }

        @Override
        public void start() {
            time = 0;
            npc.setState("isBalrogCharging", 1f);
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity t = npc.getTarget();
            return t != null && t.isAlive() && time < 200;
        }

        @Override
        public void tick() {
            time++;
            LivingEntity t = npc.getTarget();
            npc.getLookControl().setLookAt(t, 30f, 30f);
            Vec3 dir = t.position().subtract(npc.position()).multiply(1, 0, 1).normalize();
            double speed = CreatureBehaviour.data(npc, "charge_speed", 2f) * 0.25;
            npc.setDeltaMovement(dir.x * speed, npc.getDeltaMovement().y, dir.z * speed);
            float damage = (float) npc.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)
                    + CreatureBehaviour.data(npc, "charge_damage", 2f);
            for (LivingEntity hit : npc.level().getEntitiesOfClass(LivingEntity.class,
                    npc.getBoundingBox().inflate(2.5), e -> e != npc && e.isAlive())) {
                if (hit == t || npc.isEnemy(hit)) {
                    hit.hurt(npc.damageSources().mobAttack(npc), damage);
                    hit.push(dir.x * 2.0, 0.5, dir.z * 2.0);
                    time = 200;
                }
            }
        }

        @Override
        public void stop() {
            npc.setState("isBalrogCharging", 0f);
            frustration = 0;
        }
    }
}
