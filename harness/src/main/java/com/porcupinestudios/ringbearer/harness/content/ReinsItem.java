package com.porcupinestudios.ringbearer.harness.content;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** The reins: use them on a hitch to drive its team from the vehicle ({@link Reins}); use them on nothing to let go. */
public class ReinsItem extends Item {
    public ReinsItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (Reins.driving(player).isEmpty()) {
            return InteractionResultHolder.pass(stack);
        }
        if (!level.isClientSide) {
            Reins.letGo(player);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
