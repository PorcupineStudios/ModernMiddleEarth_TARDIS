package com.porcupinestudios.ringbearer.harness.mixin;

import com.porcupinestudios.ringbearer.harness.HarnessMod;
import com.porcupinestudios.ringbearer.harness.content.HarnessSlot;
import net.minecraft.world.Container;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.HorseInventoryMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The horse inventory gets a harness slot (the animal's Curios harness slot, {@link HarnessSlot}) under the armour
 * slot, after every vanilla slot so vanilla's slot numbers stay as they were; shift-clicking moves a harness in and
 * out of it.
 */
@Mixin(HorseInventoryMenu.class)
abstract class HorseInventoryMenuMixin extends AbstractContainerMenu {
    @Unique
    private int ringbearer_harness$slot = -1;

    protected HorseInventoryMenuMixin(MenuType<?> type, int id) {
        super(type, id);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void ringbearer_harness$addHarnessSlot(int containerId, Inventory inventory, Container horseContainer,
                                                   AbstractHorse horse, int columns, CallbackInfo ci) {
        ringbearer_harness$slot = addSlot(new HarnessSlot(horse, HarnessSlot.X, HarnessSlot.Y)).index;
    }

    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void ringbearer_harness$shiftClick(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
        int h = ringbearer_harness$slot;
        if (h < 0 || index < 0 || index >= slots.size()) {
            return;
        }
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return;
        }
        ItemStack stack = slot.getItem();
        ItemStack before = stack.copy();
        // the player's inventory: the 36 slots just before the harness slot
        int playerStart = h - 36;
        boolean moved;
        if (index == h) {
            moved = moveItemStackTo(stack, playerStart, h, true);
        } else if (index >= playerStart && index < h && stack.is(HarnessMod.HARNESS.get())
                && slots.get(h).mayPlace(stack) && !slots.get(h).hasItem()) {
            moved = moveItemStackTo(stack, h, h + 1, false);
        } else {
            return;
        }
        if (!moved) {
            cir.setReturnValue(ItemStack.EMPTY);
            return;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        cir.setReturnValue(before);
    }
}
