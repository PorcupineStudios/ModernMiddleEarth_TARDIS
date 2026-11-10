package com.porcupinestudios.lotrport.content.thrown;

import com.porcupinestudios.lotrport.LotrPort;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** LOTR's sling (LOTRItemSling): slings a pebble ({@code lotr_port:sling_ammo}) for twice a thrown pebble's hurt. */
public class SlingItem extends Item {
    public static final TagKey<Item> AMMO = TagKey.create(Registries.ITEM, LotrPort.id("sling_ammo"));

    public SlingItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        ItemStack ammo = ItemStack.EMPTY;
        for (int i = 0; i < player.getInventory().getContainerSize() && ammo.isEmpty(); i++) {
            if (player.getInventory().getItem(i).is(AMMO)) {
                ammo = player.getInventory().getItem(i);
            }
        }
        boolean creative = player.getAbilities().instabuild;
        if (ammo.isEmpty() && !creative) {
            return InteractionResultHolder.fail(stack);
        }
        ItemStack shot = ammo.isEmpty() ? new ItemStack(Thrown.item("pebble")) : ammo.copyWithCount(1);
        if (!level.isClientSide) {
            stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
            Throws.missile(level, player, shot, ThrownMissile.Kind.SLING_PEBBLE);
            Throws.lightSound(level, player);
        }
        if (!creative) {
            ammo.shrink(1);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
