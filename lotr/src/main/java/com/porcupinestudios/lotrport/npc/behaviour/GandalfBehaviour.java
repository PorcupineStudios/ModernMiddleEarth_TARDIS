package com.porcupinestudios.lotrport.npc.behaviour;

import com.porcupinestudios.lotrport.npc.LotrNpc;
import com.porcupinestudios.lotrport.npc.NpcData;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;

/**
 * LOTR's Grey Wanderer (LOTREntityGandalf, LOTRGreyWandererTracker): only a creative player can hurt him; he stays
 * while players talk to him, and {@code wander_ticks} (3600) after the last talk, when he isn't fighting, he departs:
 * players within 64 get his {@code depart} line in chat, and he vanishes in a puff of smoke.
 */
public class GandalfBehaviour implements CreatureBehaviour {
    private static final String LEFT = "lotr_port.wanderer_ticks";

    @Override
    public float hurt(LotrNpc npc, DamageSource source, float amount) {
        return source.getEntity() instanceof Player p && p.isCreative() ? amount : 0;
    }

    @Override
    public InteractionResult interact(LotrNpc npc, ServerPlayer player, InteractionHand hand) {
        npc.getPersistentData().putInt(LEFT, (int) CreatureBehaviour.data(npc, "wander_ticks", 3600));
        return null;
    }

    @Override
    public void tick(LotrNpc npc) {
        var data = npc.getPersistentData();
        if (!data.contains(LEFT)) {
            data.putInt(LEFT, (int) CreatureBehaviour.data(npc, "wander_ticks", 3600));
        }
        int left = data.getInt(LEFT) - 1;
        data.putInt(LEFT, left);
        if (left > 0 || npc.getTarget() != null || !(npc.level() instanceof ServerLevel level)) {
            return;
        }
        List<String> lines = npc.npcType().speech().map(b -> NpcData.speech(b, "depart")).orElse(List.of());
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(npc) < 64 * 64 && !lines.isEmpty()) {
                String line = lines.get(npc.random().nextInt(lines.size())).replace("#", p.getGameProfile().getName());
                p.sendSystemMessage(Component.literal("<").append(npc.getDisplayName()).append("> " + line));
            }
        }
        npc.playSound(SoundEvents.CHICKEN_EGG, 2f, 0.5f + npc.random().nextFloat() * 0.5f); // 1.7.10's random.pop
        level.sendParticles(ParticleTypes.POOF, npc.getX(), npc.getY() + npc.getBbHeight() / 2, npc.getZ(), 20,
                npc.getBbWidth(), npc.getBbHeight() / 2, npc.getBbWidth(), 0.02);
        npc.discard();
    }
}
