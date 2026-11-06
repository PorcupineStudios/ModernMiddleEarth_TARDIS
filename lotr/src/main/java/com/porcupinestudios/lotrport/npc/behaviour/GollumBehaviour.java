package com.porcupinestudios.lotrport.npc.behaviour;

import com.porcupinestudios.lotrport.npc.LotrNpc;
import com.porcupinestudios.lotrport.npc.NpcType;
import java.util.EnumSet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * LOTR's Gollum (LOTREntityGollum and its AI): wild, he runs from players (and shows it, crouched and fleeing).
 * Feed him {@code fish_required} (20) raw fish and he follows the one who fed him. His master's right-click makes
 * him sit and stay or get up and follow ("Stay!"/"Follow!"); fish heal him, with an eating pause. Hurt, he panics
 * and flees for a while.
 */
public class GollumBehaviour implements CreatureBehaviour {
    private int panic;

    @Override
    public void init(LotrNpc npc, NpcType type) {
        npc.setState("fishRequired", CreatureBehaviour.data(type, "fish_required", 20));
    }

    @Override
    public void addGoals(LotrNpc npc) {
        npc.goals().addGoal(1, new AvoidEntityGoal<>(npc, Player.class, 10f, 1.0, 1.4,
                p -> npc.owner().isEmpty() || panic > 0) {
            @Override
            public void start() {
                super.start();
                npc.setState("isGollumFleeing", 1f);
            }

            @Override
            public void stop() {
                super.stop();
                npc.setState("isGollumFleeing", 0f);
            }
        });
        npc.goals().addGoal(0, new net.minecraft.world.entity.ai.goal.Goal() {
            {
                setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
            }

            @Override
            public boolean canUse() {
                return npc.state("isGollumSitting") > 0;
            }

            @Override
            public void start() {
                npc.getNavigation().stop();
            }
        });
    }

    @Override
    public void tick(LotrNpc npc) {
        if (panic > 0) {
            panic--;
        }
    }

    @Override
    public float hurt(LotrNpc npc, DamageSource source, float amount) {
        panic = 100;
        npc.setState("isGollumSitting", 0f);
        return amount;
    }

    @Override
    public InteractionResult interact(LotrNpc npc, ServerPlayer player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        boolean fish = held.is(ItemTags.FISHES);
        if (npc.isOwnedBy(player)) {
            if (fish && npc.getHealth() < npc.getMaxHealth()) {
                eat(npc, player, held);
                npc.heal(4f);
                return InteractionResult.SUCCESS;
            }
            boolean sit = npc.state("isGollumSitting") <= 0;
            npc.setState("isGollumSitting", sit ? 1f : 0f);
            npc.setCommand(sit ? LotrNpc.Command.GUARD : LotrNpc.Command.FOLLOW);
            player.sendSystemMessage(Component.literal("<").append(npc.getName()).append("> ")
                    .append(Component.translatableWithFallback(sit ? "lotr_port.gollum.stay" : "lotr_port.gollum.follow",
                            sit ? "We waits here, precious." : "Yes, master, we comes, gollum!")));
            return InteractionResult.SUCCESS;
        }
        if (npc.owner().isEmpty() && fish) {
            eat(npc, player, held);
            float left = npc.state("fishRequired") - 1;
            npc.setState("fishRequired", left);
            if (left <= 0) {
                npc.hireTo(player);
                if (npc.level() instanceof ServerLevel level) {
                    level.sendParticles(ParticleTypes.HEART, npc.getX(), npc.getEyeY(), npc.getZ(), 6, 0.4, 0.4, 0.4,
                            0);
                }
            }
            return InteractionResult.SUCCESS;
        }
        return null;
    }

    private static void eat(LotrNpc npc, ServerPlayer player, ItemStack fish) {
        if (!player.getAbilities().instabuild) {
            fish.shrink(1);
        }
        npc.playSound(SoundEvents.GENERIC_EAT, 0.5f, 1f);
    }
}
