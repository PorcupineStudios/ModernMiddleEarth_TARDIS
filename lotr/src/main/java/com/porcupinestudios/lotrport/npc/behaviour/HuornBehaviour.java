package com.porcupinestudios.lotrport.npc.behaviour;

import com.porcupinestudios.lotrport.npc.LotrNpc;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * LOTR's huorns (LOTREntityHuornBase, LOTREntityAINearestAttackableTargetHuorn) stand still as trees until they wake:
 * when hurt, or when they find a target (within {@code wake_range}, 24 blocks). Asleep they don't move and look
 * like any tree; awake they hunt. They fall asleep again when they have no target.
 */
public class HuornBehaviour implements CreatureBehaviour {
    @Override
    public void tick(LotrNpc npc) {
        LivingEntity target = npc.getTarget();
        boolean active = target != null && target.isAlive()
                && npc.distanceTo(target) <= CreatureBehaviour.data(npc, "wake_range", 24f);
        npc.setState("isHuornActive", active ? 1f : 0f);
        if (!active) {
            npc.getNavigation().stop();
            npc.setDeltaMovement(new Vec3(0, npc.getDeltaMovement().y, 0));
            npc.setYRot(npc.yRotO);
        }
    }

    @Override
    public float hurt(LotrNpc npc, DamageSource source, float amount) {
        if (source.getEntity() instanceof LivingEntity attacker) {
            npc.setTarget(attacker);
        }
        return amount;
    }
}
