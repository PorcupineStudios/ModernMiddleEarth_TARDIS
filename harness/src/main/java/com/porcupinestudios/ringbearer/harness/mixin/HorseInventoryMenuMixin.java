package com.porcupinestudios.ringbearer.harness.mixin;

import com.porcupinestudios.ringbearer.harness.content.HarnessSlot;
import net.minecraft.world.Container;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.HorseInventoryMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The horse inventory's saddle slot also takes a harness ({@link HarnessSlot}), same place, same container slot. */
@Mixin(HorseInventoryMenu.class)
abstract class HorseInventoryMenuMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void ringbearer_harness$harnessSlot(int containerId, Inventory inventory, Container horseContainer,
                                                AbstractHorse horse, int columns, CallbackInfo ci) {
        AbstractContainerMenu self = (AbstractContainerMenu) (Object) this;
        Slot old = self.slots.get(0);
        Slot slot = new HarnessSlot(horseContainer, old.x, old.y, horse);
        slot.index = old.index;
        self.slots.set(0, slot);
    }
}
