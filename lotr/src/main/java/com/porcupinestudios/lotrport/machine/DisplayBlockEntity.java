package com.porcupinestudios.lotrport.machine;

import com.porcupinestudios.lotrport.content.LotrBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What a plate, weapon rack or armour stand holds (LOTRTileEntityPlate / WeaponRack / ArmorStand): a stack of one
 * food, one weapon, or four pieces of armour. Synced to clients, which draw it.
 */
public class DisplayBlockEntity extends BaseContainerBlockEntity {
    private NonNullList<ItemStack> items;

    public DisplayBlockEntity(BlockPos pos, BlockState state) {
        super(Machines.DISPLAY.get(), pos, state);
        items = NonNullList.withSize(size(state), ItemStack.EMPTY);
    }

    private static int size(BlockState state) {
        return "armor_stand".equals(kind(state)) ? 4 : 1;
    }

    public static String kind(BlockState state) {
        return LotrBlocks.machine(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath());
    }

    public String kind() {
        return kind(getBlockState());
    }

    public ItemStack held() {
        return items.get(0);
    }

    public void hold(ItemStack stack) {
        items.set(0, stack);
        changed();
    }

    void changed() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public void setChanged() {
        super.setChanged();
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        super.setItem(slot, stack);
        changed();
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        ItemStack out = super.removeItem(slot, count);
        changed();
        return out;
    }

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
        this.items = items;
    }

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    public int getMaxStackSize() {
        return "plate".equals(kind()) ? 64 : 1;
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new MachineMenu(MachineMenu.Layout.ARMOR_STAND, id, inventory, this,
                new net.minecraft.world.inventory.SimpleContainerData(4));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, true, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items = NonNullList.withSize(size(getBlockState()), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
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
