package com.porcupinestudios.lotrport.drink;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.content.LotrComponents;
import com.porcupinestudios.lotrport.content.LotrDrinkItem;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * LOTR's poisoned drinks (LOTRRecipePoisonDrinks): a drink and a bottle of poison anywhere in the grid make the same
 * drink, poisoned ({@link LotrComponents#POISONED}); the bottle comes back empty. Whoever crafts it is stored as the
 * poisoner ({@link LotrDrinkItem#onCraftedBy}): only they see "Poisoned!" on it.
 */
public class PoisonDrinkRecipe extends CustomRecipe {
    /** Set by {@link #assemble} until the crafting player is known. */
    public static final UUID UNKNOWN = new UUID(0, 0);

    public PoisonDrinkRecipe(CraftingBookCategory category) {
        super(category);
    }

    private static boolean poison(ItemStack s) {
        return s.is(BuiltInRegistries.ITEM.get(LotrPort.id("bottle_of_poison")));
    }

    private static ItemStack drink(CraftingInput input) {
        ItemStack drink = ItemStack.EMPTY;
        int poisons = 0;
        for (int i = 0; i < input.size(); i++) {
            ItemStack s = input.getItem(i);
            if (s.isEmpty()) {
                continue;
            }
            if (poison(s)) {
                poisons++;
            } else if (s.getItem() instanceof LotrDrinkItem && !s.has(LotrComponents.POISONED.get()) && drink.isEmpty()) {
                drink = s;
            } else {
                return ItemStack.EMPTY;
            }
        }
        return poisons == 1 ? drink : ItemStack.EMPTY;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return !drink(input).isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack out = drink(input).copyWithCount(1);
        if (!out.isEmpty()) {
            out.set(LotrComponents.POISONED.get(), UNKNOWN);
        }
        return out;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> out = NonNullList.withSize(input.size(), ItemStack.EMPTY);
        for (int i = 0; i < input.size(); i++) {
            if (poison(input.getItem(i))) {
                out.set(i, new ItemStack(Items.GLASS_BOTTLE));
            }
        }
        return out;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return Drinks.POISON_SERIALIZER.get();
    }
}
