package com.porcupinestudios.lotrport.npc.behaviour;

import com.porcupinestudios.lotrport.npc.LotrNpc;
import com.porcupinestudios.lotrport.sound.LotrSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;

/**
 * LOTR's Wood-elf scouts (LOTREntityWoodElfScout): an archer that won't be cornered. When its target comes within
 * {@code too_close} (6) blocks it may (1 in {@code chance}, 20, a tick) vanish in a swirl of leaves and reappear
 * 16-32 blocks away on open ground, with {@code elf.wood_elf_teleport}.
 */
public class ElfScoutBehaviour implements CreatureBehaviour {
    @Override
    public void tick(LotrNpc npc) {
        LivingEntity t = npc.getTarget();
        RandomSource r = npc.getRandom();
        float near = CreatureBehaviour.data(npc, "too_close", 6f);
        if (t == null || npc.distanceToSqr(t) > near * near || r.nextInt((int) CreatureBehaviour.data(npc, "chance", 20f)) != 0
                || !(npc.level() instanceof ServerLevel level)) {
            return;
        }
        for (int i = 0; i < 16; i++) {
            double angle = r.nextDouble() * Math.PI * 2;
            double dist = 16 + r.nextDouble() * 16;
            int x = (int) Math.floor(t.getX() + Math.cos(angle) * dist);
            int z = (int) Math.floor(t.getZ() + Math.sin(angle) * dist);
            for (int dy = 6; dy >= -6; dy--) {
                BlockPos feet = new BlockPos(x, (int) t.getY() + dy, z);
                if (level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), net.minecraft.core.Direction.UP)
                        && level.noCollision(npc, npc.getDimensions(npc.getPose()).makeBoundingBox(
                                x + 0.5, feet.getY(), z + 0.5))
                        && !level.containsAnyLiquid(npc.getBoundingBox().move(x + 0.5 - npc.getX(),
                                feet.getY() - npc.getY(), z + 0.5 - npc.getZ()))) {
                    effects(npc, level);
                    npc.teleportTo(x + 0.5, feet.getY(), z + 0.5);
                    npc.getNavigation().stop();
                    effects(npc, level);
                    return;
                }
            }
        }
    }

    private static void effects(LotrNpc npc, ServerLevel level) {
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OAK_LEAVES.defaultBlockState()),
                npc.getX(), npc.getY() + npc.getBbHeight() / 2, npc.getZ(), 24, 0.4, 0.6, 0.4, 0.05);
        LotrSounds.get("elf.wood_elf_teleport").ifPresent(s ->
                level.playSound(null, npc.getX(), npc.getY(), npc.getZ(), s, SoundSource.HOSTILE, 0.5f, 1f));
    }
}
