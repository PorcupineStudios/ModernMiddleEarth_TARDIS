package com.porcupinestudios.tardisrb.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

/**
 * A lightning arc across the time vortex tunnel (Aaron 2026-11-10: obstacles are "unmoving lightning bolts" drawn
 * through a cross-section of the vortex, to dodge). A straight line from its position minus {@link #half} to its
 * position plus it; it never moves, only crackles ({@code client/ArcRenderer}). The hit is worked out on the server
 * from the trip's data ({@code vortex/Vortex}); this entity is only there to be drawn.
 */
public class VortexArc extends Entity {
    private static final EntityDataAccessor<Vector3f> HALF = SynchedEntityData.defineId(VortexArc.class,
            EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Float> THICKNESS = SynchedEntityData.defineId(VortexArc.class,
            EntityDataSerializers.FLOAT);

    public VortexArc(EntityType<? extends VortexArc> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(HALF, new Vector3f(1, 0, 0));
        builder.define(THICKNESS, 0.35F);
    }

    public void setup(Vector3f half, float thickness) {
        entityData.set(HALF, half);
        entityData.set(THICKNESS, thickness);
    }

    /** From the middle to one end. */
    public Vector3f half() {
        return entityData.get(HALF);
    }

    public float thickness() {
        return entityData.get(THICKNESS);
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        Vector3f h = half();
        return new AABB(position(), position()).inflate(Math.abs(h.x) + 1, Math.abs(h.y) + 1, Math.abs(h.z) + 1);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 160 * 160;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setup(new Vector3f(tag.getFloat("hx"), tag.getFloat("hy"), tag.getFloat("hz")), tag.getFloat("thickness"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        Vector3f h = half();
        tag.putFloat("hx", h.x);
        tag.putFloat("hy", h.y);
        tag.putFloat("hz", h.z);
        tag.putFloat("thickness", thickness());
    }
}
