package com.porcupinestudios.lotrport.content;

import com.porcupinestudios.lotrport.npc.LotrNpc;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * LOTR's branding iron (LOTRItemBrandingIron): name it on an anvil, then use it on an animal or one of your hired
 * units to give it that name (up to 64 letters), with a fizz. Each brand wears it one point. LOTR's heating step
 * (a "hot iron" from the fire) is left out.
 */
public class BrandingIronItem extends Item {
    public BrandingIronItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
            InteractionHand hand) {
        Component name = stack.get(DataComponents.CUSTOM_NAME);
        if (name == null || target instanceof Player) {
            return InteractionResult.PASS;
        }
        if (target instanceof LotrNpc npc && !npc.owner().map(player.getUUID()::equals).orElse(false)) {
            return InteractionResult.PASS; // LOTR: only your own hired units take a brand
        }
        String brand = name.getString().trim();
        if (brand.isEmpty() || target.hasCustomName() && brand.equals(target.getCustomName().getString())) {
            return InteractionResult.PASS;
        }
        if (!player.level().isClientSide) {
            target.setCustomName(Component.literal(brand.length() > 64 ? brand.substring(0, 64) : brand));
            if (target instanceof net.minecraft.world.entity.Mob mob) {
                mob.setPersistenceRequired();
            }
            player.level().playSound(null, target, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5f, 1.5f);
            stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
        }
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }
}
