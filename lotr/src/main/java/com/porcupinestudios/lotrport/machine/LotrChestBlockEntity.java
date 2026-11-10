package com.porcupinestudios.lotrport.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.LidBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * One of LOTR's chests (stone chest, reed basket, lebethron casket, mallorn box, ancient Haradric chest;
 * LOTRTileEntityChest): a single chest of 27 slots with a lid, drawn on vanilla's chest model in LOTR's texture.
 * Loot tables work as for vanilla chests.
 */
public class LotrChestBlockEntity extends RandomizableContainerBlockEntity implements LidBlockEntity {
    private NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
    private int openers;
    private float openness;
    private float oOpenness;

    public LotrChestBlockEntity(BlockPos pos, BlockState state) {
        super(Machines.CHEST.get(), pos, state);
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
        return 27;
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return ChestMenu.threeRows(id, inventory, this);
    }

    @Override
    public void startOpen(Player player) {
        if (!remove && !player.isSpectator()) {
            if (openers++ == 0) {
                level.playSound(null, worldPosition, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.5f,
                        level.random.nextFloat() * 0.1f + 0.9f);
            }
            level.blockEvent(worldPosition, getBlockState().getBlock(), 1, openers);
        }
    }

    @Override
    public void stopOpen(Player player) {
        if (!remove && !player.isSpectator()) {
            if (--openers <= 0) {
                openers = 0;
                level.playSound(null, worldPosition, SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.5f,
                        level.random.nextFloat() * 0.1f + 0.9f);
            }
            level.blockEvent(worldPosition, getBlockState().getBlock(), 1, openers);
        }
    }

    @Override
    public boolean triggerEvent(int id, int type) {
        if (id == 1) {
            openers = type;
            return true;
        }
        return super.triggerEvent(id, type);
    }

    /** Client: eases the lid towards open or shut. */
    public void clientTick() {
        oOpenness = openness;
        openness = openers > 0 ? Math.min(1f, openness + 0.1f) : Math.max(0f, openness - 0.1f);
    }

    @Override
    public float getOpenNess(float partialTicks) {
        return oOpenness + (openness - oOpenness) * partialTicks;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!trySaveLootTable(tag)) {
            ContainerHelper.saveAllItems(tag, items, registries);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items = NonNullList.withSize(27, ItemStack.EMPTY);
        if (!tryLoadLootTable(tag)) {
            ContainerHelper.loadAllItems(tag, items, registries);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return new CompoundTag();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
