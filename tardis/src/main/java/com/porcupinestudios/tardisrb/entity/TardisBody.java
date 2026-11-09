package com.porcupinestudios.tardisrb.entity;

import com.porcupinestudios.tardisrb.flight.FreeFlight;
import com.porcupinestudios.tardisrb.flight.PilotInput;
import com.porcupinestudios.tardisrb.progression.TardisContent;
import com.porcupinestudios.tardisrb.progression.Tuning;
import com.porcupinestudios.tardisrb.vortex.Vortex;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
    /** The doors: 0 shut, 1 open, 2 swinging shut (Aaron 2026-11-09: the doors open while it is an entity too). */
    private static final EntityDataAccessor<Byte> DOORS = SynchedEntityData.defineId(TardisBody.class,
            EntityDataSerializers.BYTE);
    /** In the time vortex: the axis of its tunnel (x, y); NaN when it isn't flying one. */
    private static final EntityDataAccessor<Float> LANE_X = SynchedEntityData.defineId(TardisBody.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> LANE_Y = SynchedEntityData.defineId(TardisBody.class,
            EntityDataSerializers.FLOAT);

    /** Client only: how far open the doors are drawn (0..1) and when that was worked out. */
    public float doorAnim;
    public float doorAnimTime = -1;

    public TardisBody(EntityType<? extends TardisBody> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(TARDIS, Optional.empty());
        builder.define(THRUSTERS, false);
        builder.define(LOOK, 0);
        builder.define(DOORS, (byte) 0);
        builder.define(LANE_X, Float.NaN);
        builder.define(LANE_Y, Float.NaN);
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

    public boolean doorsOpen() {
        return entityData.get(DOORS) != 0;
    }

    /** Open and not swinging shut: the doors are drawn open. */
    public boolean doorsDrawnOpen() {
        return entityData.get(DOORS) == 1;
    }

    public void setDoors(boolean open, boolean closing) {
        entityData.set(DOORS, (byte) (!open ? 0 : closing ? 2 : 1));
    }

    /** Puts it in a vortex tunnel round the axis ({@code x}, {@code y}) along +z ({@code vortex/Vortex}). */
    public void setLane(float x, float y) {
        entityData.set(LANE_X, x);
        entityData.set(LANE_Y, y);
    }

    /** True while it flies a time vortex tunnel. */
    public boolean inLane() {
        return !Float.isNaN(entityData.get(LANE_X)) && level().dimension() == Vortex.DIMENSION;
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
        if (inLane()) {
            vortexPhysics();
            return;
        }
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

    /**
     * In the vortex: no gravity, nothing to bump into but debris (it passes through it; {@code Vortex} works out the
     * hit). Flown, it drifts forward along the tunnel by itself (W faster, S slower, sprint boosts) while A / D and
     * Q / E steer across it; it can't leave the tunnel's radius. Unflown, the server's autopilot carries it.
     */
    private void vortexPhysics() {
        Tuning.Piloting p = TardisContent.tuning().flight().piloting();
        Tuning.VortexTuning vt = TardisContent.tuning().vortex();
        Vec3 v = getDeltaMovement();
        double vx = v.x;
        double vy = v.y;
        double vz = 0;
        if (getControllingPassenger() != null) {
            double steer = p.acceleration() * 2;
            vx = (vx + PilotInput.left * steer) * (PilotInput.left == 0 ? 0.8 : 1);
            vy = (vy + PilotInput.up * steer) * (PilotInput.up == 0 ? 0.8 : 1);
            double across = Math.sqrt(vx * vx + vy * vy);
            double top = p.maxSpeed() * 0.75;
            if (across > top) {
                vx *= top / across;
                vy *= top / across;
            }
            vz = vt.speed() * (1 + 0.5 * PilotInput.forward) * (PilotInput.boost ? p.boost() : 1)
                    * (PilotInput.brake ? 0.5 : 1);
        } else {
            vx *= 0.8;
            vy *= 0.8;
        }
        double x = getX() + vx;
        double y = getY() + vy;
        double cx = entityData.get(LANE_X);
        double cy = entityData.get(LANE_Y);
        double ox = x - cx;
        double oy = y - cy;
        double r = Math.sqrt(ox * ox + oy * oy);
        if (r > vt.radius()) {
            x = cx + ox / r * vt.radius();
            y = cy + oy / r * vt.radius();
            vx *= 0.3;
            vy *= 0.3;
        }
        setDeltaMovement(vx, vy, vz);
        setYRot(Direction.SOUTH.toYRot());
        setPos(x, y, getZ() + vz);
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

    /** Solid while the doors are shut; with them open you can walk in to the doorway (and through it). */
    @Override
    public boolean canBeCollidedWith() {
        return !doorsOpen();
    }

    @Override
    public boolean isPushable() {
        return !doorsOpen();
    }

    /** Using the box from outside works its doors, as the block does ({@link FreeFlight#use}). */
    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        return player instanceof ServerPlayer sp ? FreeFlight.use(this, sp) : InteractionResult.PASS;
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
        setDoors(tag.getBoolean("doors_open"), false);
        if (tag.contains("lane_x")) {
            setLane(tag.getFloat("lane_x"), tag.getFloat("lane_y"));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tardis().ifPresent(id -> tag.putUUID("tardis", id));
        tag.putBoolean("thrusters", thrusters());
        tag.putInt("look", look());
        tag.putBoolean("doors_open", doorsOpen());
        if (!Float.isNaN(entityData.get(LANE_X))) {
            tag.putFloat("lane_x", entityData.get(LANE_X));
            tag.putFloat("lane_y", entityData.get(LANE_Y));
        }
    }
}
