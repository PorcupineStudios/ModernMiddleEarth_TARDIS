package com.porcupinestudios.ringbearer.harness.mixin;

import com.porcupinestudios.ringbearer.harness.HarnessMod;
import com.porcupinestudios.ringbearer.harness.content.Harnessing;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A horse-family animal wears its harness in its saddle slot (inventory slot 0). Vanilla counts anything in that slot
 * as a saddle (it only ever held saddles); with a harness there it isn't saddled (no saddle drawn, no steering), and
 * the synced {@code harness_worn} follows the slot (taking the harness out unhitches it).
 */
@Mixin(AbstractHorse.class)
abstract class AbstractHorseHarnessMixin {
    @Shadow
    protected SimpleContainer inventory;

    @Shadow
    protected abstract void setFlag(int flagId, boolean value);

    /** While its save is read: the migration below runs only then. */
    @Unique
    private boolean ringbearer_harness$loading;

    @Inject(method = "readAdditionalSaveData", at = @At("HEAD"))
    private void ringbearer_harness$loadStart(CompoundTag tag, CallbackInfo ci) {
        ringbearer_harness$loading = true;
    }

    @Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
    private void ringbearer_harness$loadEnd(CompoundTag tag, CallbackInfo ci) {
        ringbearer_harness$loading = false;
    }

    @Inject(method = "syncSaddleToClients", at = @At("HEAD"), cancellable = true)
    private void ringbearer_harness$harnessInSaddleSlot(CallbackInfo ci) {
        AbstractHorse self = (AbstractHorse) (Object) this;
        if (self.level().isClientSide) {
            return;
        }
        ItemStack slot = inventory.getItem(0);
        // saves from before the slot (2026-11-24): the attachment says worn, the slot is empty; loading (the
        // attachment is read before the inventory) puts the harness in the slot
        if (slot.isEmpty() && ringbearer_harness$loading && Harnessing.wears(self)) {
            slot = new ItemStack(HarnessMod.HARNESS.get());
            inventory.setItem(0, slot);
        }
        boolean harness = slot.is(HarnessMod.HARNESS.get());
        Harnessing.slotChanged(self, harness);
        if (harness) {
            setFlag(4, false);
            ci.cancel();
        }
    }
}
