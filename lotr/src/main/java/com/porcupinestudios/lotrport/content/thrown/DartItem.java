package com.porcupinestudios.lotrport.content.thrown;

import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** A Taurethrim dart (LOTRItemDart), shot from the blowgun; poisoned darts give LOTR's standard poison. */
public class DartItem extends Item implements Thrown.ThrowingItem {
    private final boolean poisoned;

    public DartItem(Properties properties, boolean poisoned) {
        super(properties);
        this.poisoned = poisoned;
    }

    public boolean poisoned() {
        return poisoned;
    }

    @Override
    public boolean dispense(Level level, Position at, Direction facing, ItemStack one) {
        Throws.fromDispenser(Throws.weaponAt(level, at, one, ThrownWeapon.Kind.DART,
                Thrown.num("dart_damage", 1f)), facing, 1.1f * 1.5f);
        return true;
    }
}
