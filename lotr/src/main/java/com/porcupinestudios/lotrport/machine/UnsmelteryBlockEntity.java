package com.porcupinestudios.lotrport.machine;

import com.google.gson.JsonObject;
import com.porcupinestudios.lotrport.content.ContentData;
import com.porcupinestudios.lotrport.crafting.LotrRecipes;
import com.porcupinestudios.lotrport.crafting.MachineRecipe;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The unsmeltery (LOTRTileEntityUnsmeltery): takes gear apart into its material, fuelled like a furnace. Slots: input
 * 0, fuel 1, output 2. The {@code unsmelting} recipe gives the most it can return; LOTR's run gives back
 * {@code yield} of that, scaled by the durability left and a random factor ({@code content/unsmelting.json}).
 */
public class UnsmelteryBlockEntity extends MachineBlockEntity {
    private static float yield = -1;
    private static float randomMin;
    private static float randomMax;
    private static boolean byDurability;

    public UnsmelteryBlockEntity(BlockPos pos, BlockState state) {
        super(Machines.UNSMELTERY.get(), pos, state, 3);
    }

    @Override
    protected int runTicks() {
        return Machines.settings().unsmelteryTicks();
    }

    @Override
    protected int fuelSlot() {
        return 1;
    }

    @Override
    protected MachineMenu.Layout layout() {
        return MachineMenu.Layout.UNSMELTERY;
    }

    private MachineRecipe recipe() {
        if (items.get(0).isEmpty() || level == null) {
            return null;
        }
        for (RecipeHolder<MachineRecipe> h : level.getRecipeManager()
                .getAllRecipesFor(LotrRecipes.MACHINE_TYPES.get(MachineRecipe.Machine.UNSMELTING).get())) {
            if (h.value().ingredients().get(0).test(items.get(0))) {
                return h.value();
            }
        }
        return null;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return super.canPlaceItem(slot, stack);
    }

    @Override
    protected boolean canWork() {
        MachineRecipe r = recipe();
        return r != null && fits(2, r.result());
    }

    @Override
    protected void finish() {
        MachineRecipe r = recipe();
        if (r == null || !fits(2, r.result())) {
            return;
        }
        ItemStack gear = items.get(0);
        loadYield();
        float f = yield * (randomMin + level.random.nextFloat() * (randomMax - randomMin));
        if (byDurability && gear.isDamageableItem() && gear.getMaxDamage() > 0) {
            f *= (gear.getMaxDamage() - gear.getDamageValue()) / (float) gear.getMaxDamage();
        }
        int count = Math.round(r.result().getCount() * f);
        gear.shrink(1);
        if (count > 0) {
            put(2, r.result().copyWithCount(count));
        }
    }

    private static void loadYield() {
        if (yield >= 0) {
            return;
        }
        List<JsonObject> rows = ContentData.read("unsmelting.json");
        JsonObject o = rows.isEmpty() ? new JsonObject() : rows.get(0);
        yield = ContentData.num(o, "yield", 0.8f);
        randomMin = ContentData.num(o, "random_min", 0.7f);
        randomMax = ContentData.num(o, "random_max", 1f);
        byDurability = ContentData.bool(o, "scale_by_durability", true);
    }
}
