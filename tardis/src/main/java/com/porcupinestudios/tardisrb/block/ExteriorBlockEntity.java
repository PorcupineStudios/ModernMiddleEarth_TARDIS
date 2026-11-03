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

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (tardis != null) {
            tag.putUUID("tardis", tardis);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        tardis = tag.hasUUID("tardis") ? tag.getUUID("tardis") : null;
    }
}
