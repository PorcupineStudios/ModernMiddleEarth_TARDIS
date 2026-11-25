package com.porcupinestudios.ringbearer.harness.content;

import com.porcupinestudios.ringbearer.harness.HarnessMod;
import net.minecraft.world.Container;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The horse inventory's saddle slot, which also takes a harness: a saddle if the animal takes one, a harness if it
 * can wear one (shown for either, and while it holds something, so a harness can always be taken out).
 */
public class HarnessSlot extends Slot {
    private final AbstractHorse horse;

    public HarnessSlot(Container container, int x, int y, AbstractHorse horse) {
        super(container, 0, x, y);
        this.horse = horse;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        if (hasItem()) {
            return false;
        }
        return stack.is(Items.SADDLE) && horse.isSaddleable()
                || stack.is(HarnessMod.HARNESS.get()) && Harnessing.canWear(horse);
    }

    @Override
    public boolean isActive() {
        return horse.isSaddleable() || hasItem() || Harnessing.canWear(horse);
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }
}
