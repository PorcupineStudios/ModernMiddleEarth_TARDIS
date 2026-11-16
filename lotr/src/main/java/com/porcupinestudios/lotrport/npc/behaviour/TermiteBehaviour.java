package com.porcupinestudios.lotrport.npc.behaviour;

import com.porcupinestudios.lotrport.npc.LotrNpc;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;

/**
 * LOTR's exploding termites (LOTREntityTermite): when one reaches its target it hisses like a creeper and bursts
 * {@code fuse} (20) ticks later with an explosion of {@code explosion} (2) that breaks blocks only with mob griefing.
 */
public class TermiteBehaviour implements CreatureBehaviour {
    private static final String FUSE = "termite.fuse";

    @Override
    public void hit(LotrNpc npc, Entity target) {
        prime(npc);
    }

    @Override
    public void tick(LotrNpc npc) {
        Entity t = npc.getTarget();
        if (t != null && npc.distanceToSqr(t) < 2.5 * 2.5) {
            prime(npc);
        }
        float fuse = npc.state(FUSE);
        if (fuse > 0) {
            npc.setState(FUSE, fuse - 1);
            if (fuse - 1 <= 0) {
                float power = CreatureBehaviour.data(npc, "explosion", 2f);
                boolean grief = npc.level().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
                npc.level().explode(npc, npc.getX(), npc.getY(), npc.getZ(), power,
                        grief ? Level.ExplosionInteraction.MOB : Level.ExplosionInteraction.NONE);
                npc.discard();
            }
        }
    }

    private static void prime(LotrNpc npc) {
        if (npc.state(FUSE) <= 0) {
            npc.setState(FUSE, CreatureBehaviour.data(npc, "fuse", 20f));
            npc.playSound(SoundEvents.CREEPER_PRIMED, 1f, 0.5f);
        }
    }
}
