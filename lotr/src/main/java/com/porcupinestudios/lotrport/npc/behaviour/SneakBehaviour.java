package com.porcupinestudios.lotrport.npc.behaviour;

import com.porcupinestudios.lotrport.npc.LotrNpc;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;

/**
 * LOTR's rangers and Galadhrim wardens sneak up on their foes (LOTREntityRanger, GaladhrimWarden): picking a new target
 * on foot they crouch, and stay crouched while they have one; {@code sneak_cooldown} (20) ticks after losing it, when
 * hurt, or when mounted, they stand up.
 */
public class SneakBehaviour implements CreatureBehaviour {
    private LivingEntity lastTarget;
    private int cooldown;

    @Override
    public void tick(LotrNpc npc) {
        LivingEntity target = npc.getTarget();
        boolean sneaking = npc.getPose() == Pose.CROUCHING;
        if (target != null && target != lastTarget) {
            lastTarget = target;
            if (!sneaking && npc.getVehicle() == null) {
                set(npc, true);
                return;
            }
        }
        if (!sneaking) {
            return;
        }
        if (npc.getVehicle() != null) {
            set(npc, false);
        } else if (target != null) {
            cooldown = (int) CreatureBehaviour.data(npc, "sneak_cooldown", 20);
        } else if (--cooldown <= 0) {
            set(npc, false);
        }
    }

    @Override
    public float hurt(LotrNpc npc, DamageSource source, float amount) {
        if (amount > 0) {
            set(npc, false);
        }
        return amount;
    }

    private void set(LotrNpc npc, boolean sneak) {
        npc.setPose(sneak ? Pose.CROUCHING : Pose.STANDING);
        npc.setShiftKeyDown(sneak);
        cooldown = sneak ? (int) CreatureBehaviour.data(npc, "sneak_cooldown", 20) : 0;
    }
}
