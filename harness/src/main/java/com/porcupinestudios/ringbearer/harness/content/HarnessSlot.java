package com.porcupinestudios.ringbearer.harness.content;

import com.porcupinestudios.ringbearer.harness.HarnessMod;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The harness slot in a horse-family animal's inventory screen: the animal's Curios harness slot, under the armour
 * slot. It holds a copy the menu syncs; changes go straight into the Curios slot on the server (and worn follows,
 * {@link Harnessing#slotChanged}), so the client needs no Curios data of its own. Shown for an animal that can wear a
 * harness, or while it holds one.
 */
public class HarnessSlot extends Slot {
    /** Where the slot sits in the horse screen: under the armour slot (8, 36). */
    public static final int X = 8, Y = 54;
    private final AbstractHorse horse;

    public HarnessSlot(AbstractHorse horse, int x, int y) {
        super(new SimpleContainer(1), 0, x, y);
        this.horse = horse;
        if (!horse.level().isClientSide) {
            Harnessing.slot(horse).ifPresent(s -> container.setItem(0, s.getStackInSlot(0).copy()));
        }
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return !hasItem() && stack.is(HarnessMod.HARNESS.get()) && Harnessing.canWear(horse);
    }

    @Override
    public boolean isActive() {
        return hasItem() || Harnessing.canWear(horse);
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public void setChanged() {
        super.setChanged();
        if (!horse.level().isClientSide) {
            ItemStack now = getItem();
            Harnessing.slot(horse).ifPresent(s -> s.setStackInSlot(0, now.copy()));
            Harnessing.slotChanged(horse, now.is(HarnessMod.HARNESS.get()));
        }
    }
}
