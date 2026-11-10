package com.porcupinestudios.lotrport.content.thrown;

import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** LOTR's throwing axe (LOTRItemThrowingAxe): thrown at once on use, flying at {@code axe_speed}. */
public class ThrowingAxeItem extends Item implements Thrown.ThrowingItem {
    private final float multiplier;

    public ThrowingAxeItem(Properties properties, float multiplier) {
        super(properties);
        this.multiplier = multiplier;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            Throws.weapon(level, player, stack, ThrownWeapon.Kind.AXE, multiplier, Thrown.num("axe_speed", 2f),
                    false);
            Throws.sound(level, player, 0.5f);
        }
        stack.consume(1, player);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    public float multiplier() {
        return multiplier;
    }

    @Override
    public boolean dispense(Level level, Position at, Direction facing, ItemStack one) {
        Throws.fromDispenser(Throws.weaponAt(level, at, one, ThrownWeapon.Kind.AXE, multiplier), facing, 1.1f * 1.5f);
        return true;
    }
}
