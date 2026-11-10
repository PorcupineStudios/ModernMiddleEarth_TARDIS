package com.porcupinestudios.lotrport.machine;

import com.porcupinestudios.lotrport.crafting.LotrRecipes;
import com.porcupinestudios.lotrport.crafting.MachineRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The hobbit oven (LOTRTileEntityHobbitOven): nine inputs over nine outputs and one fuel; a run cooks all nine at
 * once. It cooks only food (its {@code hobbit_oven} recipes, else a furnace result that is food). Slots: input 0-8,
 * output 9-17, fuel 18.
 */
public class OvenBlockEntity extends MachineBlockEntity {
    public static final int OUTPUT = 9;
    public static final int FUEL = 18;

    public OvenBlockEntity(BlockPos pos, BlockState state) {
        super(Machines.OVEN.get(), pos, state, 19);
    }

    @Override
    protected int runTicks() {
        return Machines.settings().ovenTicks();
    }

    @Override
    protected int fuelSlot() {
        return FUEL;
    }

    @Override
    protected MachineMenu.Layout layout() {
        return MachineMenu.Layout.OVEN;
    }

    private ItemStack result(int slot) {
        ItemStack input = items.get(slot);
        if (input.isEmpty() || level == null) {
            return ItemStack.EMPTY;
        }
        for (RecipeHolder<MachineRecipe> h : level.getRecipeManager()
                .getAllRecipesFor(LotrRecipes.MACHINE_TYPES.get(MachineRecipe.Machine.HOBBIT_OVEN).get())) {
            if (h.value().ingredients().get(0).test(input)) {
                return h.value().result();
            }
        }
        ItemStack s = smelt(input);
        return s.has(DataComponents.FOOD) ? s : ItemStack.EMPTY;
    }

    @Override
    protected boolean canWork() {
        for (int i = 0; i < 9; i++) {
            if (fits(OUTPUT + i, result(i))) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void finish() {
        for (int i = 0; i < 9; i++) {
            ItemStack r = result(i);
            if (fits(OUTPUT + i, r)) {
                put(OUTPUT + i, r);
                items.get(i).shrink(1);
            }
        }
    }
}
