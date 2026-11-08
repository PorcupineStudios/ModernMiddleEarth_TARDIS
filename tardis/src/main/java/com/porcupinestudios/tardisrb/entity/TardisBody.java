package com.porcupinestudios.tardisrb.entity;

import com.porcupinestudios.tardisrb.flight.FreeFlight;
import com.porcupinestudios.tardisrb.flight.PilotInput;
import com.porcupinestudios.tardisrb.progression.TardisContent;
import com.porcupinestudios.tardisrb.progression.Tuning;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The police box when it isn't a block (docs/free-flight-plan.md, Aaron 2026-11-08): with the thrusters off it is a
 * physics object (it falls, slides and can be pushed); with them on, anti-grav holds it still in the air. It moves on
 * the server; the client is sent its position. It can't be hurt or broken: {@link FreeFlight#phase} turns it back into
 * the block.
 */
public class TardisBody extends Entity {
    private static final EntityDataAccessor<Optional<UUID>> TARDIS = SynchedEntityData.defineId(TardisBody.class,
            EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Boolean> THRUSTERS = SynchedEntityData.defineId(TardisBody.class,
            EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> LOOK = SynchedEntityData.defineId(TardisBody.class,
            EntityDataSerializers.INT);

    public TardisBody(EntityType<? extends TardisBody> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(TARDIS, Optional.empty());
        builder.define(THRUSTERS, false);
        builder.define(LOOK, 0);
    }

    public Optional<UUID> tardis() {
        return entityData.get(TARDIS);
    }

    public void setTardis(UUID id) {
        entityData.set(TARDIS, Optional.of(id));
    }

    public boolean thrusters() {
        return entityData.get(THRUSTERS);
    }

    public void setThrusters(boolean on) {
        entityData.set(THRUSTERS, on);
    }

    /** Which exterior look (texture) it is drawn with, as the block's {@code look}. */
    public int look() {
        return entityData.get(LOOK);
    }

    public void setLook(int look) {
        entityData.set(LOOK, look);
    }

    @Override
    public void tick() {
        super.tick();
        tickLerp();
        if (isControlledByLocalInstance()) {
            physics();
        }
        if (!level().isClientSide && tickCount % 20 == 0) {
            FreeFlight.track(this);
        }
    }

    /**
     * Moves it one tick, where it is controlled: on the server, or on the pilot's client while someone flies it (their
     * client sends the box's moves, as with a boat). Flown: thrust along the pilot's view from {@link PilotInput}.
     */
    private void physics() {
        Tuning.FreeFlight t = TardisContent.tuning().flight();
        Vec3 v = getDeltaMovement();
        boolean flown = getControllingPassenger() != null && thrusters();
        if (flown) {
            Tuning.Piloting p = t.piloting();
            double yaw = Math.toRadians(getControllingPassenger().getYRot());
            Vec3 forward = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
            Vec3 left = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
            Vec3 push = forward.scale(PilotInput.forward).add(left.scale(PilotInput.left));
            Vec3 across = new Vec3(v.x, 0, v.z);
            if (push.lengthSqr() > 1.0E-4) {
                across = across.add(push.normalize().scale(p.acceleration()));
            } else {
                across = across.scale(t.hoverDamping());
            }
            double top = p.maxSpeed() * (PilotInput.boost ? p.boost() : 1);
            if (across.length() > top) {
                across = across.normalize().scale(top);
            }
            double vy = v.y + (PilotInput.up * p.verticalSpeed() - v.y) * 0.5;
            v = new Vec3(across.x, vy, across.z);
            if (PilotInput.brake) {
                v = v.scale(0.5);
            }
        } else {
            v = thrusters() ? v.scale(t.hoverDamping()) : v.add(0, -t.gravity(), 0);
        }
        setDeltaMovement(v);
        move(MoverType.SELF, getDeltaMovement());
        if (!flown) {
            double across = onGround() ? t.groundFriction() : t.drag();
            setDeltaMovement(getDeltaMovement().multiply(across, thrusters() ? 1 : t.drag(), across));
        }
    }

    /** The pilot (the first passenger, if a player). */
    @Override
    public LivingEntity getControllingPassenger() {
        return getFirstPassenger() instanceof Player p ? p : null;
    }

    // smooth movement on clients that don't steer it (as boats do)
    private int lerpSteps;
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private double lerpYRot;

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        lerpX = x;
        lerpY = y;
        lerpZ = z;
        lerpYRot = yRot;
        lerpSteps = steps + 2;
    }

    private void tickLerp() {
        if (isControlledByLocalInstance()) {
            lerpSteps = 0;
            syncPacketPositionCodec(getX(), getY(), getZ());
        }
        if (lerpSteps > 0) {
            lerpPositionAndRotationStep(lerpSteps, lerpX, lerpY, lerpZ, lerpYRot, getXRot());
            lerpSteps--;
        }
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (!level().isClientSide && getPassengers().isEmpty()) {
            setDeltaMovement(getDeltaMovement().scale(0.5));
        }
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("tardis")) {
            setTardis(tag.getUUID("tardis"));
        }
        setThrusters(tag.getBoolean("thrusters"));
        setLook(tag.getInt("look"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tardis().ifPresent(id -> tag.putUUID("tardis", id));
        tag.putBoolean("thrusters", thrusters());
        tag.putInt("look", look());
    }
}
