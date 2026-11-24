package com.porcupinestudios.ringbearer.harness.mixin;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A horse's inventory: its saddle slot's {@code SlotAccess} takes only saddles, so a harness goes in through this. */
@Mixin(AbstractHorse.class)
public interface HorseInventoryAccess {
    @Accessor("inventory")
    SimpleContainer ringbearer_harness$inventory();
}
