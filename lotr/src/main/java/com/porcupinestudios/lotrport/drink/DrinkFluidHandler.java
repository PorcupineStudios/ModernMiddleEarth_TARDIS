package com.porcupinestudios.lotrport.drink;

import com.porcupinestudios.lotrport.content.LotrDrinkItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;

/**
 * A drink or an empty vessel as a one-serving fluid container (Create's spout fills vessels, its item drain empties
 * drinks): a drink holds one serving of its fluid and drains to its vessel; a vessel takes exactly one serving of any
 * drink's fluid (water too). Single items only, as NeoForge's bucket wrapper.
 */
final class DrinkFluidHandler implements IFluidHandlerItem {
    private ItemStack container;

    DrinkFluidHandler(ItemStack container) {
        this.container = container;
    }

    private static int serving() {
        return DrinkData.settings().servingMb();
    }

    @Override
    public ItemStack getContainer() {
        return container;
    }

    @Override
    public int getTanks() {
        return 1;
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        return container.getItem() instanceof LotrDrinkItem ? Drinks.toFluid(container, serving()) : FluidStack.EMPTY;
    }

    @Override
    public int getTankCapacity(int tank) {
        return serving();
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return !Drinks.fromFluid(stack).isEmpty();
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (container.getCount() != 1 || !Drinks.isVessel(container.getItem())
                || resource.getAmount() < serving()) {
            return 0;
        }
        ItemStack drink = Drinks.fromFluid(resource);
        if (drink.isEmpty()) {
            return 0;
        }
        if (action.execute()) {
            container = Drinks.inVessel(drink, container.getItem());
        }
        return serving();
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        return FluidStack.isSameFluidSameComponents(resource, getFluidInTank(0))
                ? drain(resource.getAmount(), action) : FluidStack.EMPTY;
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        FluidStack out = getFluidInTank(0);
        if (container.getCount() != 1 || out.isEmpty() || maxDrain < out.getAmount()) {
            return FluidStack.EMPTY;
        }
        if (action.execute()) {
            container = new ItemStack(Drinks.vessel(container));
        }
        return out;
    }
}
