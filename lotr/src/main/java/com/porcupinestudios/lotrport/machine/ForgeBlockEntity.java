package com.porcupinestudios.lotrport.machine;

import com.porcupinestudios.lotrport.crafting.LotrRecipes;
import com.porcupinestudios.lotrport.crafting.MachineRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A faction forge (alloy, dwarven, elven, orc; LOTRTileEntityAlloyForgeBase): four columns of alloy over input over
 * output, one fuel. Each column works on its own, all at once when a run ends. An input with a matching alloy makes
 * the forge's alloy; otherwise the forge's own smelting (orc forges char wood and rot food), otherwise a furnace's
 * result for what LOTR's forges took ({@code #lotr_port:forge_smeltable}). Slots: alloy 0-3, input 4-7, output 8-11,
 * fuel 12.
 */
public class ForgeBlockEntity extends MachineBlockEntity {
    public static final int ALLOY = 0;
    public static final int INPUT = 4;
    public static final int OUTPUT = 8;
    public static final int FUEL = 12;

    public ForgeBlockEntity(BlockPos pos, BlockState state) {
        super(Machines.FORGE.get(), pos, state, 13);
    }

    @Override
    protected int runTicks() {
        return Machines.settings().forgeTicks();
    }

    @Override
    protected int fuelSlot() {
        return FUEL;
    }

    @Override
    protected MachineMenu.Layout layout() {
        return MachineMenu.Layout.FORGE;
    }

    /** The column's result and whether it uses the alloy, or null. */
    private Result result(int column) {
        ItemStack input = items.get(INPUT + column);
        if (input.isEmpty() || level == null) {
            return null;
        }
        ItemStack alloy = items.get(ALLOY + column);
        ResourceLocation block = blockId();
        ItemStack single = ItemStack.EMPTY;
        for (RecipeHolder<MachineRecipe> h : level.getRecipeManager()
                .getAllRecipesFor(LotrRecipes.MACHINE_TYPES.get(MachineRecipe.Machine.ALLOY_FORGE).get())) {
            MachineRecipe r = h.value();
            if (!stationMatches(r.station(), block) || !r.ingredients().get(0).test(input)) {
                continue;
            }
            if (r.ingredients().size() == 2) {
                if (!alloy.isEmpty() && r.ingredients().get(1).test(alloy)) {
                    return new Result(r.result(), true);
                }
            } else if (single.isEmpty()) {
                single = r.result();
            }
        }
        if (!single.isEmpty()) {
            return new Result(single, false);
        }
        if (input.is(Machines.FORGE_SMELTABLE)) {
            ItemStack s = smelt(input);
            if (!s.isEmpty()) {
                return new Result(s, false);
            }
        }
        return null;
    }

    private record Result(ItemStack stack, boolean alloy) {}

    @Override
    protected boolean canWork() {
        for (int c = 0; c < 4; c++) {
            Result r = result(c);
            if (r != null && fits(OUTPUT + c, r.stack)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void finish() {
        for (int c = 0; c < 4; c++) {
            Result r = result(c);
            if (r == null || !fits(OUTPUT + c, r.stack)) {
                continue;
            }
            put(OUTPUT + c, r.stack);
            items.get(INPUT + c).shrink(1);
            if (r.alloy) {
                items.get(ALLOY + c).shrink(1);
            }
        }
    }
}
