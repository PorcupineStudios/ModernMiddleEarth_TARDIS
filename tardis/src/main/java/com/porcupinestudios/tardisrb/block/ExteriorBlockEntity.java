package com.porcupinestudios.tardisrb.block;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** The lower half of a police box: holds only the id of its TARDIS record. */
public final class ExteriorBlockEntity extends BlockEntity {
    private UUID tardis;
    /** Client only, for the door animation: how far open the doors are drawn and when that was last advanced. */
    public float doorAnim;
    public float doorAnimTime = -1;
    /** Materialising: the game time it started (-1: not) and how long it takes. Synced to clients for the fade-in. */
    private long materialiseAt = -1;
    private int materialiseTicks;

    public ExteriorBlockEntity(BlockPos pos, BlockState state) {
        super(TardisBlocks.EXTERIOR_BE.get(), pos, state);
    }

    public Optional<UUID> tardis() {
        return Optional.ofNullable(tardis);
    }

    public void setTardis(UUID id) {
        tardis = id;
        setChanged();
    }

    /** Starts the fade-in ({@code Materialise}); tells clients. */
    public void materialise(long gameTime, int ticks) {
        materialiseAt = gameTime;
        materialiseTicks = ticks;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /** 0..1 through the fade-in at {@code time}; 1 when not materialising. */
    public float materialised(float time) {
        if (materialiseAt < 0 || materialiseTicks <= 0) {
            return 1;
        }
        return Math.max(0, Math.min(1, (time - materialiseAt) / materialiseTicks));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = saveWithoutMetadata(registries);
        return tag;
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (tardis != null) {
            tag.putUUID("tardis", tardis);
        }
        if (materialiseAt >= 0) {
            tag.putLong("materialise_at", materialiseAt);
            tag.putInt("materialise_ticks", materialiseTicks);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        tardis = tag.hasUUID("tardis") ? tag.getUUID("tardis") : null;
        materialiseAt = tag.contains("materialise_at") ? tag.getLong("materialise_at") : -1;
        materialiseTicks = tag.getInt("materialise_ticks");
    }
}
