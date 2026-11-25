package com.porcupinestudios.ringbearer.harness.content;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** The harness, worn instead of a saddle: use it on a tamed horse (or any animal that can pull). */
public class HarnessItem extends Item {
    public HarnessItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
                                                  InteractionHand hand) {
        if (Harnessing.wears(target)) {
            return InteractionResult.PASS;
        }
        if (player.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!Harnessing.putOn(target, player)) {
            player.displayClientMessage(Component.translatable("message.ringbearer_harness.cannot_wear"), true);
            return InteractionResult.FAIL;
        }
        stack.consume(1, player);
        return InteractionResult.CONSUME;
    }
}
