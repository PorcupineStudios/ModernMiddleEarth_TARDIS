package com.porcupinestudios.lotrport.mixin.create;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.porcupinestudios.lotrport.compat.create.FactionCrafting;
import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity;
import com.simibubi.create.content.kinetics.crafter.RecipeGridHandler;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** When Create finds no recipe for a crafter grid, a grid with faction crafters tries their tables' recipes. */
@Mixin(value = MechanicalCrafterBlockEntity.class, remap = false)
public abstract class CrafterRecipeMixin {
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/kinetics/crafter/"
            + "RecipeGridHandler;tryToApplyRecipe(Lnet/minecraft/world/level/Level;Lcom/simibubi/create/content/"
            + "kinetics/crafter/RecipeGridHandler$GroupedItems;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack lotr_port$factionRecipes(Level level, RecipeGridHandler.GroupedItems items,
            Operation<ItemStack> original) {
        ItemStack result = original.call(level, items);
        return result != null ? result
                : FactionCrafting.craft((MechanicalCrafterBlockEntity) (Object) this, level, items);
    }
}
