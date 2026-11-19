package com.porcupinestudios.lotrport.content.pouch;

import com.porcupinestudios.lotrport.content.LotrItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Bigger pouches are only made by combining pouches (Aaron 2026-11-19; only the small one has a crafting recipe):
 * two or more pouches anywhere in the grid whose slots add up to exactly a bigger pouch's become that pouch, holding
 * everything they held (two small = medium; small + medium or three small = large). The first pouch's name is kept.
 */
public class PouchCombineRecipe extends CustomRecipe {
    public PouchCombineRecipe(CraftingBookCategory category) {
        super(category);
    }

    private static List<ItemStack> pouches(CraftingInput input) {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < input.size(); i++) {
            ItemStack s = input.getItem(i);
            if (s.isEmpty()) {
                continue;
            }
            if (!(s.getItem() instanceof PouchItem)) {
                return List.of();
            }
            out.add(s);
        }
        return out.size() >= 2 ? out : List.of();
    }

    /** The pouch item with exactly this many slots, or null. */
    private static PouchItem bySlots(int slots) {
        for (var holder : LotrItems.BY_ID.values()) {
            Item item = holder.get();
            if (item instanceof PouchItem p && p.slots() == slots) {
                return p;
            }
        }
        return null;
    }

    private static PouchItem result(List<ItemStack> pouches) {
        int total = 0;
        for (ItemStack s : pouches) {
            total += ((PouchItem) s.getItem()).slots();
        }
        return pouches.isEmpty() ? null : bySlots(total);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return result(pouches(input)) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        List<ItemStack> pouches = pouches(input);
        PouchItem into = result(pouches);
        if (into == null) {
            return ItemStack.EMPTY;
        }
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack p : pouches) {
            for (ItemStack s : PouchItem.contents(p)) {
                items.add(s.copy());
            }
        }
        ItemStack out = new ItemStack(into);
        PouchItem.store(out, items);
        ItemStack first = pouches.get(0);
        if (first.has(DataComponents.CUSTOM_NAME)) {
            out.set(DataComponents.CUSTOM_NAME, first.get(DataComponents.CUSTOM_NAME));
        }
        return out;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        return NonNullList.withSize(input.size(), ItemStack.EMPTY); // the contents went into the new pouch
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return Pouches.COMBINE_SERIALIZER.get();
    }
}
