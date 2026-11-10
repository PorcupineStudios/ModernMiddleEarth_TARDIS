package com.porcupinestudios.lotrport.machine;

import com.porcupinestudios.lotrport.crafting.LotrRecipes;
import com.porcupinestudios.lotrport.crafting.MachineRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Ent jar (LOTRTileEntityEntJar): holds up to {@code ent_jar_capacity} servings of water or of one Ent-draught.
 * Rain fills it slowly; in Fangorn an ingredient dropped into a jar of water turns it into that draught
 * ({@code ent_jar} recipes); bowls take draughts out and full bowls pour them back.
 */
public class EntJarBlockEntity extends BlockEntity {
    /** The draught it holds, or empty for water. */
    private ItemStack drink = ItemStack.EMPTY;
    private int amount;

    public EntJarBlockEntity(BlockPos pos, BlockState state) {
        super(Machines.ENT_JAR.get(), pos, state);
    }

    public int amount() {
        return amount;
    }

    public ItemStack drink() {
        return drink;
    }

    public boolean isWater() {
        return amount > 0 && drink.isEmpty();
    }

    public int capacity() {
        return Machines.settings().entJarCapacity();
    }

    public void fillWithWater() {
        drink = ItemStack.EMPTY;
        amount = capacity();
        changed();
    }

    public void empty() {
        drink = ItemStack.EMPTY;
        amount = 0;
        changed();
    }

    /** Pours a bowl of draught in, if it is empty or holds the same and has room. */
    public boolean pourIn(ItemStack draught) {
        if (amount == 0 || !drink.isEmpty() && ItemStack.isSameItem(drink, draught) && amount < capacity()) {
            drink = draught.copyWithCount(1);
            amount++;
            changed();
            return true;
        }
        return false;
    }

    /** Takes one serving of draught out, or empty. */
    public ItemStack takeDraught() {
        if (amount == 0 || drink.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack out = drink.copy();
        if (--amount == 0) {
            drink = ItemStack.EMPTY;
        }
        changed();
        return out;
    }

    /** The draught an ingredient makes from water here, or empty. */
    public ItemStack brew(ItemStack ingredient) {
        if (level == null || !isWater()) {
            return ItemStack.EMPTY;
        }
        for (RecipeHolder<MachineRecipe> h : level.getRecipeManager()
                .getAllRecipesFor(LotrRecipes.MACHINE_TYPES.get(MachineRecipe.Machine.ENT_JAR).get())) {
            if (h.value().ingredients().get(0).test(ingredient)) {
                return h.value().result();
            }
        }
        return ItemStack.EMPTY;
    }

    public void turnInto(ItemStack draught) {
        drink = draught.copyWithCount(1);
        changed();
    }

    void serverTick() {
        int chance = Machines.settings().entJarRainChance();
        if ((amount == 0 || isWater() && amount < capacity()) && level.isRainingAt(worldPosition.above())
                && chance > 0 && level.random.nextInt(chance) == 0) {
            amount++;
            changed();
        }
    }

    private void changed() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!drink.isEmpty()) {
            tag.put("Drink", drink.save(registries));
        }
        tag.putInt("Amount", amount);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        drink = tag.contains("Drink") ? ItemStack.parseOptional(registries, tag.getCompound("Drink")) : ItemStack.EMPTY;
        amount = tag.getInt("Amount");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
