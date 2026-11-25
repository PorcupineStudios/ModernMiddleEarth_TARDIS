package com.porcupinestudios.lotrport.mixin;

import com.porcupinestudios.lotrport.LotrPort;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * LOTR's modifiers are enchantments under the hood (modifier/, all in the lotr_port namespace), but in LOTR they never
 * made gear glow. Item.isFoil (vanilla: "is enchanted") is false when every enchantment is a LOTR modifier; items that
 * always shine (Smith's Scrolls, golden apples...) override isFoil and are untouched, and a glint override component
 * still wins (ItemStack.hasFoil reads it first).
 */
@Mixin(Item.class)
public abstract class ModifierGlintMixin {
    @Inject(method = "isFoil", at = @At("HEAD"), cancellable = true)
    private void lotr_port$noModifierGlint(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        ItemEnchantments enchantments = stack.get(DataComponents.ENCHANTMENTS);
        if (enchantments == null || enchantments.isEmpty()) {
            return;
        }
        for (Holder<Enchantment> e : enchantments.keySet()) {
            if (e.unwrapKey().map(k -> !k.location().getNamespace().equals(LotrPort.MOD_ID)).orElse(true)) {
                return; // a real enchantment: vanilla's glint
            }
        }
        cir.setReturnValue(false);
    }
}
