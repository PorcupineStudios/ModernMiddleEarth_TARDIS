package com.porcupinestudios.tardisrb.block;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** A part block's TARDIS and the repair that fixes it. */
public final class PartBlockEntity extends BlockEntity {
    private UUID tardis;
    private ResourceLocation repair;

    public PartBlockEntity(BlockPos pos, BlockState state) {
        super(TardisBlocks.PART_BE.get(), pos, state);
    }

    public Optional<UUID> tardis() {
        return Optional.ofNullable(tardis);
    }

    public Optional<ResourceLocation> repair() {
        return Optional.ofNullable(repair);
    }

    public void set(UUID tardisId, ResourceLocation repairId) {
        tardis = tardisId;
        repair = repairId;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (tardis != null) {
            tag.putUUID("tardis", tardis);
        }
        if (repair != null) {
            tag.putString("repair", repair.toString());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        tardis = tag.hasUUID("tardis") ? tag.getUUID("tardis") : null;
        repair = tag.contains("repair") ? ResourceLocation.tryParse(tag.getString("repair")) : null;
    }
}
