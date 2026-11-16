package com.porcupinestudios.lotrport.npc.behaviour;

import com.porcupinestudios.lotrport.npc.LotrNpc;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * LOTR's boss leap (LOTREntityAIBossJumpAttack + LOTRBossInfo, the Mountain-troll chieftain): with its target 4-20
 * blocks off, 1 in {@code jump_chance} (0.03) a tick it leaps at it ({@code jump_speed} 1.5), and where it lands
 * everything within 12 blocks takes its attack damage, less with distance, and is thrown back.
 */
public class BossJumpBehaviour implements CreatureBehaviour {
    private static final String AIRBORNE = "boss.jumping";
    private static final double RADIUS = 12;

    @Override
    public void tick(LotrNpc npc) {
        if (!(npc.level() instanceof ServerLevel level)) {
            return;
        }
        if (npc.state(AIRBORNE) > 0) {
            if (npc.onGround() && npc.state(AIRBORNE) < 95) {
                npc.setState(AIRBORNE, 0);
                land(npc, level);
            } else {
                npc.setState(AIRBORNE, npc.state(AIRBORNE) - 1);
            }
            return;
        }
        LivingEntity t = npc.getTarget();
        if (t == null || !npc.onGround()) {
            return;
        }
        double d = npc.distanceTo(t);
        if (d < 4 || d > 20 || npc.getRandom().nextFloat() >= CreatureBehaviour.data(npc, "jump_chance", 0.03f)) {
            return;
        }
        double speed = CreatureBehaviour.data(npc, "jump_speed", 1.5f);
        Vec3 dir = new Vec3(t.getX() - npc.getX(), 0, t.getZ() - npc.getZ()).normalize();
        npc.setDeltaMovement(dir.x * speed * Math.min(1, d / 12), speed * 0.8, dir.z * speed * Math.min(1, d / 12));
        npc.hasImpulse = true;
        npc.getLookControl().setLookAt(t, 100f, 100f);
        npc.setState(AIRBORNE, 100);
    }

    @Override
    public float hurt(LotrNpc npc, net.minecraft.world.damagesource.DamageSource source, float amount) {
        // it lands on purpose: no fall damage from its own leap
        return npc.state(AIRBORNE) > 0 && source.is(net.minecraft.tags.DamageTypeTags.IS_FALL) ? 0 : amount;
    }

    private static void land(LotrNpc npc, ServerLevel level) {
        float attack = (float) npc.getAttributeValue(Attributes.ATTACK_DAMAGE);
        level.playSound(null, npc.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1f, 0.6f);
        level.sendParticles(ParticleTypes.EXPLOSION, npc.getX(), npc.getY(), npc.getZ(), 6, 2, 0.2, 2, 0);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, npc.getBoundingBox().inflate(RADIUS))) {
            if (e == npc || npc.isAlliedTo(e) || e instanceof LotrNpc o && o.faction().equals(npc.faction())) {
                continue;
            }
            double d = npc.distanceTo(e);
            if (d > RADIUS) {
                continue;
            }
            float share = (float) ((RADIUS - d) / RADIUS);
            if (e.hurt(npc.damageSources().mobAttack(npc), attack * share)) {
                Vec3 away = e.position().subtract(npc.position()).normalize();
                e.push(away.x * share, 0.5 * share, away.z * share);
            }
        }
    }
}
