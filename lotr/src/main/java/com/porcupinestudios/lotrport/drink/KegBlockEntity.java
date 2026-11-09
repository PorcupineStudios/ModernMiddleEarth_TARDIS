package com.porcupinestudios.lotrport.drink;

import com.porcupinestudios.lotrport.crafting.LotrRecipes;
import java.util.Optional;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * LOTR's barrel (LOTRTileEntityBarrel) as a keg. Slots 0-5 take a brewing recipe's ingredients, 6-8 its water buckets
 * (or water piped into the keg's water tank), slot 9 shows the drink. Start Brewing takes them and puts a weak drink in
 * the keg, one strength stronger every recipe {@code brew_time} up to potent; Stop Brewing keeps it as it is (after the
 * first step). A finished keg pours a serving into an empty vessel, and pipes drain it (or fill it with a drink, or the
 * water tank with water) through {@link #fluids}. The drink is kept as a fluid amount in mB, so pipes can move parts of
 * a serving.
 */
public class KegBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {
    public static final int EMPTY = 0;
    public static final int BREWING = 1;
    public static final int FULL = 2;
    static final int DRINK_SLOT = 9;
    private static final int[] SLOTS = IntStream.range(0, DRINK_SLOT).toArray();

    private NonNullList<ItemStack> items = NonNullList.withSize(10, ItemStack.EMPTY);
    /** The drink: one of it, with its strength (no vessel). */
    private ItemStack drink = ItemStack.EMPTY;
    private int amount;
    private int water;
    private int mode;
    private int brewTime;
    private int stepTicks;

    /** For the screen: mode, drink mB, capacity mB. */
    final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> mode;
                case 1 -> amount;
                default -> capacity();
            };
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return 3;
        }
    };

    public KegBlockEntity(BlockPos pos, BlockState state) {
        super(Drinks.KEG.get(), pos, state);
    }

    private static int serving() {
        return DrinkData.settings().servingMb();
    }

    private static int capacity() {
        return DrinkData.settings().kegServings() * serving();
    }

    public int mode() {
        return mode;
    }

    /** The drink (one, no vessel) and how many mB of it. */
    public ItemStack drink() {
        return drink.copy();
    }

    public int amount() {
        return amount;
    }

    // ------------------------------------------------------------------ brewing

    public void tick() {
        if (mode != BREWING) {
            return;
        }
        if (++brewTime % 100 == 0) {
            setChanged();
        }
        if (brewTime < Math.max(1, stepTicks)) {
            return;
        }
        brewTime = 0;
        int strength = Drinks.strength(drink) + 1;
        drink.set(Drinks.STRENGTH.get(), strength);
        if (strength >= DrinkData.settings().maxStrength()) {
            mode = FULL;
        }
        changed();
    }

    private RecipeInput input() {
        return new RecipeInput() {
            @Override
            public ItemStack getItem(int index) {
                return items.get(index);
            }

            @Override
            public int size() {
                return 6;
            }
        };
    }

    /** LOTR's Start Brewing: the ingredients must make a brewing recipe and its water be there. */
    public boolean startBrewing() {
        if (mode != EMPTY || level == null) {
            return false;
        }
        Optional<RecipeHolder<BrewingRecipe>> found =
                level.getRecipeManager().getRecipeFor(LotrRecipes.BREWING.get(), input(), level);
        if (found.isEmpty()) {
            return false;
        }
        BrewingRecipe recipe = found.get().value();
        int buckets = (int) IntStream.range(6, 9).filter(i -> items.get(i).is(Items.WATER_BUCKET)).count();
        int need = recipe.water();
        if (buckets * FluidType.BUCKET_VOLUME + water < need) {
            return false;
        }
        for (int i = 0; i < 6; i++) {
            ItemStack stack = items.get(i);
            if (!stack.isEmpty()) {
                ItemStack left = stack.getCraftingRemainingItem();
                stack.shrink(1);
                if (stack.isEmpty()) {
                    items.set(i, left);
                } else if (!left.isEmpty()) {
                    Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1,
                            worldPosition.getZ() + 0.5, left);
                }
            }
        }
        for (int i = 6; i < 9 && need > 0; i++) {
            if (items.get(i).is(Items.WATER_BUCKET)) {
                items.set(i, new ItemStack(Items.BUCKET));
                need -= FluidType.BUCKET_VOLUME;
            }
        }
        water = Math.max(0, water - Math.max(0, need));
        drink = recipe.result().copyWithCount(1);
        amount = recipe.result().getCount() * serving();
        boolean brews = Drinks.brewable(drink.getItem());
        if (brews) {
            drink.set(Drinks.STRENGTH.get(), 0);
        }
        mode = brews ? BREWING : FULL;
        brewTime = 0;
        stepTicks = recipe.brewTime();
        changed();
        return true;
    }

    /** LOTR's Stop Brewing: keep the drink at its strength now (not before its first step). */
    public boolean stopBrewing() {
        if (mode != BREWING || Drinks.strength(drink) == 0) {
            return false;
        }
        mode = FULL;
        brewTime = 0;
        changed();
        return true;
    }

    /** One serving of a finished drink (no vessel), taken from the keg; empty if there isn't a whole one. */
    public ItemStack pour() {
        if (mode != FULL || amount < serving()) {
            return ItemStack.EMPTY;
        }
        ItemStack out = drink.copy();
        take(serving());
        return out;
    }

    private void take(int mb) {
        amount -= mb;
        if (amount <= 0) {
            amount = 0;
            drink = ItemStack.EMPTY;
            mode = EMPTY;
        }
        changed();
    }

    private void changed() {
        items.set(DRINK_SLOT, drink.isEmpty() ? ItemStack.EMPTY : drink.copyWithCount(amount / serving()));
        setChanged();
    }

    /** The ingredients and buckets, for dropping when broken. */
    NonNullList<ItemStack> contents() {
        NonNullList<ItemStack> out = NonNullList.create();
        out.addAll(items.subList(0, DRINK_SLOT));
        return out;
    }

    // ------------------------------------------------------------------ fluids

    /** Tank 0: the finished drink (drain it, or fill an empty keg or more of the same). Tank 1: water for brewing. */
    final IFluidHandler fluids = new IFluidHandler() {
        @Override
        public int getTanks() {
            return 2;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            if (tank == 1) {
                return water > 0 ? new FluidStack(Fluids.WATER, water) : FluidStack.EMPTY;
            }
            return mode == FULL ? Drinks.toFluid(drink, amount) : FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int tank) {
            return tank == 1 ? DrinkData.settings().kegWaterMb() : capacity();
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return tank == 1 ? stack.is(Fluids.WATER) : !stack.is(Fluids.WATER) && !Drinks.fromFluid(stack).isEmpty();
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) {
                return 0;
            }
            if (resource.is(Fluids.WATER)) {
                int n = Math.max(0, Math.min(resource.getAmount(), DrinkData.settings().kegWaterMb() - water));
                if (action.execute() && n > 0) {
                    water += n;
                    setChanged();
                }
                return n;
            }
            ItemStack d = Drinks.fromFluid(resource);
            if (d.isEmpty() || !(mode == EMPTY || mode == FULL && Drinks.same(d, drink))) {
                return 0;
            }
            int n = Math.max(0, Math.min(resource.getAmount(), capacity() - amount));
            if (action.execute() && n > 0) {
                if (mode == EMPTY) {
                    drink = d;
                    mode = FULL;
                }
                amount += n;
                changed();
            }
            return n;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return !resource.isEmpty() && FluidStack.isSameFluidSameComponents(resource, getFluidInTank(0))
                    ? drain(resource.getAmount(), action) : FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            if (mode != FULL || maxDrain <= 0) {
                return FluidStack.EMPTY;
            }
            FluidStack out = Drinks.toFluid(drink, Math.min(maxDrain, amount));
            if (action.execute() && !out.isEmpty()) {
                take(out.getAmount());
            }
            return out;
        }
    };

    // ------------------------------------------------------------------ container

    @Override
    protected Component getDefaultName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        for (int i = 0; i < Math.min(DRINK_SLOT, items.size()); i++) {
            this.items.set(i, items.get(i));
        }
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new KegMenu(id, inventory, this, data);
    }

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot < 6 || slot < DRINK_SLOT && stack.is(Items.WATER_BUCKET);
    }

    /** Slot 9 only shows the drink: it is poured, never taken. */
    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot != DRINK_SLOT) {
            super.setItem(slot, stack);
        }
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        return slot == DRINK_SLOT ? ItemStack.EMPTY : super.removeItem(slot, count);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return slot == DRINK_SLOT ? ItemStack.EMPTY : super.removeItemNoUpdate(slot);
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        return canPlaceItem(slot, stack);
    }

    /** Hoppers and funnels take only the emptied buckets. */
    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot >= 6 && stack.is(Items.BUCKET);
    }

    // ------------------------------------------------------------------ saving

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putByte("Mode", (byte) mode);
        if (!drink.isEmpty()) {
            tag.put("Drink", drink.save(registries));
        }
        tag.putInt("Amount", amount);
        tag.putInt("Water", water);
        tag.putInt("BrewTime", brewTime);
        tag.putInt("StepTicks", stepTicks);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items = NonNullList.withSize(10, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
        mode = tag.getByte("Mode");
        water = tag.getInt("Water");
        brewTime = tag.getInt("BrewTime");
        stepTicks = tag.getInt("StepTicks");
        if (tag.contains("Drink")) {
            drink = ItemStack.parseOptional(registries, tag.getCompound("Drink"));
            amount = tag.getInt("Amount");
        } else {
            // a LOTR barrel from a structure (converter/structures.py keg): its drink and servings in slot 9
            ItemStack shown = items.get(DRINK_SLOT);
            drink = shown.copyWithCount(1);
            drink.remove(Drinks.VESSEL.get());
            amount = shown.getCount() * serving();
        }
        if (drink.isEmpty() || amount <= 0) {
            drink = ItemStack.EMPTY;
            amount = 0;
            mode = EMPTY;
        }
        items.set(DRINK_SLOT, drink.isEmpty() ? ItemStack.EMPTY : drink.copyWithCount(amount / serving()));
    }
}
