package com.porcupinestudios.lotrport.npc.behaviour;

import com.porcupinestudios.lotrport.npc.LotrNpc;
import com.porcupinestudios.lotrport.npc.NpcType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;

/**
 * LOTR's warg bombardiers (LOTREntityWargBombardier, LOTREntityAIWargBombardierAttack): a warg with a lit bomb on its
 * back. Its fuse rests at {@code fuse} (35). Once it has a target the fuse is primed (TNT hiss) and smokes; within
 * {@code bomb_range} (4) blocks of the target it burns down from at most 20 and at 0 the warg blows up with power
 * ({@code bomb_strength} 1 + 1) x {@code power_per_strength} (4), breaking blocks only with mob griefing. Out of range
 * it creeps back above 20. Losing the target resets it.
 */
public class WargBombBehaviour implements CreatureBehaviour {
    private static final String FUSE = "getBombFuse";
    private static final String STRENGTH = "getBombStrengthLevel";
    private static final String PRIMED = "bomb.primed";

    @Override
    public void init(LotrNpc npc, NpcType type) {
        npc.setState(FUSE, CreatureBehaviour.data(type, "fuse", 35));
        npc.setState(STRENGTH, CreatureBehaviour.data(type, "bomb_strength", 1));
    }

    @Override
    public void tick(LotrNpc npc) {
        float rest = CreatureBehaviour.data(npc, "fuse", 35);
        int fuse = (int) npc.state(FUSE);
        LivingEntity target = npc.getTarget();
        if (target == null || !target.isAlive()) {
            npc.setState(FUSE, rest);
            npc.setState(PRIMED, 0);
            return;
        }
        if (npc.state(PRIMED) == 0) {
            npc.setState(PRIMED, 1);
            npc.playSound(SoundEvents.TNT_PRIMED, 1f, 1f);
        }
        float range = CreatureBehaviour.data(npc, "bomb_range", 4);
        if (npc.distanceToSqr(target.getX(), target.getY(), target.getZ()) <= range * range) {
            if (fuse > 20) {
                while (fuse > 20) { // down by 10s to 20 or under
                    fuse -= 10;
                }
            } else if (fuse > 0) {
                fuse--;
            } else {
                explode(npc);
                return;
            }
        } else if (fuse <= 20) {
            while (fuse <= 20) {
                fuse += 10;
            }
        } else {
            fuse--;
        }
        npc.setState(FUSE, fuse);
        if (fuse < rest && npc.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.SMOKE, npc.getX(), npc.getY() + 2.2, npc.getZ(), 1, 0, 0, 0, 0);
        }
    }

    private static void explode(LotrNpc npc) {
        float power = (npc.state(STRENGTH) + 1) * CreatureBehaviour.data(npc, "power_per_strength", 4);
        boolean grief = npc.level().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        npc.level().explode(npc, npc.getX(), npc.getY(), npc.getZ(), power,
                grief ? Level.ExplosionInteraction.MOB : Level.ExplosionInteraction.NONE);
        npc.discard();
    }
}
