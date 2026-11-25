package com.porcupinestudios.tardisrb.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * Where the pilot sits while the box is set down as a block during piloting (Aaron 2026-11-19: "allow piloting while
 * it's a block, you just can't move, you can only see (basically a security camera)"): an invisible seat over the
 * block that never moves. Not saved; it goes when nobody sits in it.
 */
public class PilotCamera extends Entity {
    private int empty;

    public PilotCamera(EntityType<? extends PilotCamera> type, Level level) {
        super(type, level);
        noPhysics = true;
        setInvisible(true);
        setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(0, 0, 0);
        if (!level().isClientSide) {
            empty = isVehicle() ? 0 : empty + 1;
            if (empty > 60) {
                discard();
            }
        }
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
