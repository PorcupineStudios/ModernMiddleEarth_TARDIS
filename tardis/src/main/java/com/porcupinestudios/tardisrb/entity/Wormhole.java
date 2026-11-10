package com.porcupinestudios.tardisrb.entity;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A wormhole in the time vortex (Aaron 2026-11-09; placed and handled by {@code vortex/Wormholes}): a swirling disc
 * facing along the tunnel, centred on the entity's position. It pulls a box flying nearby toward its middle
 * ({@link TardisBody}) at {@link #pull} (already lessened by that TARDIS's stabilizers) within {@link #range}.
 * {@link #open} when the scanner sees through it: an Immersive Portals portal shows the far side and only the rim is
 * drawn. With a {@code life} it is the rift a box is thrown out of, and goes by itself.
 */
public class Wormhole extends Entity {
    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(Wormhole.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> PULL = SynchedEntityData.defineId(Wormhole.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> RANGE = SynchedEntityData.defineId(Wormhole.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> OPEN = SynchedEntityData.defineId(Wormhole.class,
            EntityDataSerializers.BOOLEAN);
    /** The TARDIS whose trip it is on: only that box feels it. */
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(Wormhole.class,
            EntityDataSerializers.OPTIONAL_UUID);
    private int life;

    public Wormhole(EntityType<? extends Wormhole> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(RADIUS, 4F);
        builder.define(PULL, 0F);
        builder.define(RANGE, 0F);
        builder.define(OPEN, false);
        builder.define(OWNER, Optional.empty());
    }

    public void setup(double radius, double pull, double range) {
        entityData.set(RADIUS, (float) radius);
        entityData.set(PULL, (float) pull);
        entityData.set(RANGE, (float) range);
    }

    public Optional<UUID> owner() {
        return entityData.get(OWNER);
    }

    public void setOwner(UUID tardis) {
        entityData.set(OWNER, Optional.of(tardis));
    }

    public float radius() {
        return entityData.get(RADIUS);
    }

    public float pull() {
        return entityData.get(PULL);
    }

    public float range() {
        return entityData.get(RANGE);
    }

    public boolean open() {
        return entityData.get(OPEN);
    }

    public void setOpen(boolean open) {
        entityData.set(OPEN, open);
    }

    /** Goes after {@code ticks} (a rift a box was thrown out of). */
    public void setLife(int ticks) {
        life = ticks;
    }

    /**
     * Which way its face points (Aaron 2026-11-10): with no turn, back down the tunnel (-z) at the oncoming box;
     * {@code yaw} turns it toward +x (90 = fully sideways), {@code pitch} toward +y (90 = straight up).
     */
    public static Vec3 facing(double yaw, double pitch) {
        return turn(new Vec3(0, 0, -1), yaw, pitch);
    }

    /** {@code v} turned the way a wormhole's disc is: the renderer and the see-through portal turn it the same way. */
    public static Vec3 turn(Vec3 v, double yaw, double pitch) {
        return v.xRot((float) Math.toRadians(-pitch)).yRot((float) Math.toRadians(-yaw));
    }

    /** Its face's direction, from its rotation (yaw = {@link #getYRot}, pitch = {@link #getXRot}). */
    public Vec3 facing() {
        return facing(getYRot(), getXRot());
    }

    /**
     * How much it pulls something at {@code at} this tick, as a push across the tunnel (x, y): toward the line straight
     * out of the middle of its face, so the box is drawn in front of it whichever way it is turned.
     */
    public Vec3 pullOn(Vec3 at) {
        Vec3 n = facing();
        Vec3 rel = at.subtract(position());
        double d = rel.length();
        if (pull() <= 0 || d >= range()) {
            return Vec3.ZERO;
        }
        Vec3 off = n.scale(Math.max(0, rel.dot(n))).subtract(rel); // to the nearest point on that line
        Vec3 across = new Vec3(off.x, off.y, 0);
        double a = across.length();
        return a < 1.0E-3 ? Vec3.ZERO : across.scale(pull() * (1 - d / range()) * Math.min(1, a) / a);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && life > 0 && --life == 0) {
            discard();
        }
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return new AABB(position(), position()).inflate(radius() * 1.5);
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
        setup(tag.getFloat("radius"), tag.getFloat("pull"), tag.getFloat("range"));
        setOpen(tag.getBoolean("open"));
        life = tag.getInt("life");
        if (tag.hasUUID("owner")) {
            setOwner(tag.getUUID("owner"));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("radius", radius());
        tag.putFloat("pull", pull());
        tag.putFloat("range", range());
        tag.putBoolean("open", open());
        tag.putInt("life", life);
        owner().ifPresent(o -> tag.putUUID("owner", o));
    }
}
