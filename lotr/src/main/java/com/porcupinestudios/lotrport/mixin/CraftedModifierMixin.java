package com.porcupinestudios.lotrport.mixin;

import com.porcupinestudios.lotrport.modifier.Modifiers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Crafted gear rolls LOTR's random modifiers ({@link Modifiers#onCrafted}). {@code onCraftedBy} is the one call both
 * taking the result and shift-clicking it make on the real stack (the crafting event sees an already-moved, empty stack
 * on a shift-click).
 */
@Mixin(Item.class)
public abstract class CraftedModifierMixin {
    @Inject(method = "onCraftedBy", at = @At("HEAD"))
    private void lotr_port$modifiers(ItemStack stack, Level level, Player player, CallbackInfo ci) {
        Modifiers.onCrafted(stack, level, player);
    }
}
