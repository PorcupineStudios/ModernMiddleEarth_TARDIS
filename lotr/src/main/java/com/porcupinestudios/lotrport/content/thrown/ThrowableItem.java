package com.porcupinestudios.lotrport.content.thrown;

import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** A pebble, conker, fire pot or mystery web (LOTRItemPebble, Conker, FirePot, MysteryWeb): thrown on use. */
public class ThrowableItem extends Item implements Thrown.ThrowingItem {
    private final ThrownMissile.Kind kind;

    public ThrowableItem(Properties properties, ThrownMissile.Kind kind) {
        super(properties);
        this.kind = kind;
    }

    public ThrownMissile.Kind kind() {
        return kind;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return Throwing.use(level, player, hand, kind);
    }

    @Override
    public boolean dispense(Level level, Position at, Direction facing, ItemStack one) {
        Throws.fromDispenser(new ThrownMissile(level, at.x(), at.y(), at.z(), one, kind), facing,
                ThrownMissile.speed(kind) * 1.1f);
        return true;
    }

    /** Shared by plates, which place as blocks on a block and fly otherwise. */
    static final class Throwing {
        private Throwing() {}

        static InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand,
                ThrownMissile.Kind kind) {
            ItemStack stack = player.getItemInHand(hand);
            if (!level.isClientSide) {
                Throws.missile(level, player, stack, kind);
                if (kind == ThrownMissile.Kind.PLATE) {
                    Throws.sound(level, player, 0.5f);
                } else {
                    Throws.lightSound(level, player);
                }
            }
            stack.consume(1, player);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
    }
}
