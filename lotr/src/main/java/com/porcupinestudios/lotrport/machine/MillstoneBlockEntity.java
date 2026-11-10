package com.porcupinestudios.lotrport.machine;

import com.porcupinestudios.lotrport.crafting.LotrRecipes;
import com.porcupinestudios.lotrport.crafting.MachineRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The millstone (LOTRTileEntityMillstone): one input, one output, turned by a redstone signal instead of fuel. Each
 * run grinds one item; LOTR's {@code chance} recipes (flowers into dye) sometimes give nothing.
 */
public class MillstoneBlockEntity extends MachineBlockEntity {
    public MillstoneBlockEntity(BlockPos pos, BlockState state) {
        super(Machines.MILLSTONE.get(), pos, state, 2);
    }

    @Override
    protected int runTicks() {
        return Machines.settings().millstoneTicks();
    }

    @Override
    protected int fuelSlot() {
        return -1;
    }

    @Override
    protected boolean powered() {
        return level != null && level.hasNeighborSignal(worldPosition);
    }

    @Override
    protected MachineMenu.Layout layout() {
        return MachineMenu.Layout.MILLSTONE;
    }

    private MachineRecipe recipe() {
        if (items.get(0).isEmpty() || level == null) {
            return null;
        }
        for (RecipeHolder<MachineRecipe> h : level.getRecipeManager()
                .getAllRecipesFor(LotrRecipes.MACHINE_TYPES.get(MachineRecipe.Machine.MILLSTONE).get())) {
            if (h.value().ingredients().get(0).test(items.get(0))) {
                return h.value();
            }
        }
        return null;
    }

    @Override
    protected boolean canWork() {
        MachineRecipe r = recipe();
        return r != null && fits(1, r.result());
    }

    @Override
    protected void finish() {
        MachineRecipe r = recipe();
        if (r == null || !fits(1, r.result())) {
            return;
        }
        items.get(0).shrink(1);
        if (r.chance() >= 1f || level.random.nextFloat() < r.chance()) {
            put(1, r.result());
        }
    }
}
