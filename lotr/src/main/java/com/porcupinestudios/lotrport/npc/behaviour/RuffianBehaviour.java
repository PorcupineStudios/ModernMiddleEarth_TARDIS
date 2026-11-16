package com.porcupinestudios.lotrport.npc.behaviour;

import com.porcupinestudios.lotrport.npc.LotrNpc;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * LOTR's Bree ruffians gang up (LOTREntityBreeRuffian.attackEntityFrom): each hit on one adds {@code anger_per_hit}
 * (100) to its anger, which cools by one a tick, and every ruffian within anger / {@code anger_per_block} (25) blocks, at
 * most {@code anger_range} (24), that isn't fighting and isn't the attacker's hireling turns on the attacker and says so.
 */
public class RuffianBehaviour implements CreatureBehaviour {
    private int anger;

    @Override
    public void tick(LotrNpc npc) {
        if (anger > 0) {
            anger--;
        }
    }

    @Override
    public float hurt(LotrNpc npc, DamageSource source, float amount) {
        if (amount > 0 && source.getEntity() instanceof LivingEntity attacker && attacker != npc) {
            anger += (int) CreatureBehaviour.data(npc, "anger_per_hit", 100);
            double range = Math.min(anger / CreatureBehaviour.data(npc, "anger_per_block", 25),
                    CreatureBehaviour.data(npc, "anger_range", 24));
            for (LotrNpc r : npc.level().getEntitiesOfClass(LotrNpc.class, npc.getBoundingBox().inflate(range),
                    r -> r.isAlive() && r.getTarget() == null && !r.owner().map(attacker.getUUID()::equals).orElse(false)
                            && r.behaviours().stream().anyMatch(b -> b instanceof RuffianBehaviour))) {
                r.setTarget(attacker);
                if (attacker instanceof ServerPlayer sp) {
                    r.speak(sp);
                }
            }
        }
        return amount;
    }
}
