package com.porcupinestudios.lotrport.content.thrown;

import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/** A plate (LOTRItemPlate): put down on a block as the plate block, thrown into the air otherwise. */
public class PlateItem extends ItemNameBlockItem implements Thrown.ThrowingItem {
    public PlateItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return ThrowableItem.Throwing.use(level, player, hand, ThrownMissile.Kind.PLATE);
    }

    @Override
    public boolean dispense(Level level, Position at, Direction facing, ItemStack one) {
        Throws.fromDispenser(new ThrownMissile(level, at.x(), at.y(), at.z(), one, ThrownMissile.Kind.PLATE), facing,
                ThrownMissile.speed(ThrownMissile.Kind.PLATE) * 1.1f);
        return true;
    }
}
