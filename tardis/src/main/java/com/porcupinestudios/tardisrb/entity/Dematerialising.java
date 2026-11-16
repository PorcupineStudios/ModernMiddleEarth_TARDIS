package com.porcupinestudios.tardisrb.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * The police box fading away where it stood ({@code flight/Materialise}): no collision, drawn by
 * {@code client/DematerialisingRenderer} with the pulsing fade, gone after {@code ticks}. Not saved.
 */
public class Dematerialising extends Entity {
    private static final EntityDataAccessor<Integer> LOOK = SynchedEntityData.defineId(Dematerialising.class,
            EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TICKS = SynchedEntityData.defineId(Dematerialising.class,
            EntityDataSerializers.INT);

    public Dematerialising(EntityType<? extends Dematerialising> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    private static final EntityDataAccessor<Boolean> DOORS = SynchedEntityData.defineId(Dematerialising.class,
            EntityDataSerializers.BOOLEAN);

    public void setup(int look, int ticks, boolean doorsOpen) {
        entityData.set(DOORS, doorsOpen);
        entityData.set(LOOK, look);
        entityData.set(TICKS, ticks);
    }

    public boolean doorsOpen() {
        return entityData.get(DOORS);
    }

    public int look() {
        return entityData.get(LOOK);
    }

    /** 0..1 through the fade-out. */
    public float progress(float partialTick) {
        return Math.min(1, (tickCount + partialTick) / Math.max(1, entityData.get(TICKS)));
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount > entityData.get(TICKS) + 20) {
            discard();
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(LOOK, 0).define(TICKS, 200).define(DOORS, false);
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
