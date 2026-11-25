package com.porcupinestudios.ringbearer.harness.content;

import com.porcupinestudios.ringbearer.harness.HarnessMod;
import com.porcupinestudios.ringbearer.harness.draught.WheelType;
import com.porcupinestudios.ringbearer.harness.physics.TraceCoupling;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.force.ForceTotal;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

/**
 * A wagon wheel's physics, every Sable substep on a vehicle: where the ground under it says its centre must be
 * ({@link WheelTerrain}, the true shapes of blocks and slopes through a long elliptic probe), a soft suspension
 * pushing toward that (the same implicit spring-damper as the traces, set by frequency against the vehicle's mass at
 * the wheel), sideways grip and rolling drag at the contact, and steering toward the traces when a hitch is in front.
 * Off a vehicle it does nothing.
 *
 * <p>Clients work out the same contact each tick for the drawing (where the hub rides, how far the wheel has rolled);
 * the server sends only the steering angle and the wheel's measurements.
 */
public class WagonWheelBlockEntity extends BlockEntity implements BlockEntitySubLevelActor {
    private final ForceTotal force = new ForceTotal();
    private double steer, sentSteer = Double.NaN;
    /** Client: the wheel's measurements (from the server's data), the hub's rise over its block's centre, the roll. */
    @Nullable private WheelType clientType;
    public double hub, hubO, roll, rollO, clientSteer, clientSteerO;

    public WagonWheelBlockEntity(BlockPos pos, BlockState state) {
        super(HarnessMod.WAGON_WHEEL_BE.get(), pos, state);
    }

    @Nullable
    private WheelType type() {
        return level != null && level.isClientSide ? clientType : HarnessMod.WHEELS.of(getBlockState().getBlock());
    }

    private Direction axle() {
        return getBlockState().getValue(WagonWheelBlock.FACING);
    }

    /** The rolling direction in the vehicle's frame: across the axle, turned by the steering. */
    private Vector3d rollLocal(double degrees) {
        Direction b = axle().getClockWise();
        double a = Math.toRadians(degrees);
        // b cos a + g sin a, with g = (-b.z, b.x): the trace angle's sense whichever way b points
        return new Vector3d(b.getStepX() * Math.cos(a) - b.getStepZ() * Math.sin(a), 0,
                b.getStepZ() * Math.cos(a) + b.getStepX() * Math.sin(a));
    }

    private Vector3d hubLocal() {
        return new Vector3d(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5);
    }

    /** The rolling direction in the world, level. */
    private static Vector3d level(Pose3dc pose, Vector3d local) {
        Vector3d w = pose.transformNormal(new Vector3d(local));
        w.y = 0;
        return w.lengthSquared() < 1e-9 ? new Vector3d(1, 0, 0) : w.normalize();
    }

    @Override
    public void sable$tick(ServerSubLevel subLevel) {
        WheelType t = type();
        if (t == null) {
            return;
        }
        double wanted = HarnessSteering.degreesFor(this);
        steer = Double.isNaN(wanted) ? 0 : Math.max(-t.maxSteer(), Math.min(t.maxSteer(), wanted));
        if (Double.isNaN(sentSteer) || Math.abs(steer - sentSteer) > 0.5) {
            sentSteer = steer;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    @Override
    public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double h) {
        WheelType t = type();
        if (t == null) {
            return;
        }
        Pose3dc pose = subLevel.logicalPose();
        Vector3d hubL = hubLocal();
        Vector3d hubW = pose.transformPosition(new Vector3d(hubL));
        Vector3d rollL = rollLocal(steer);
        Vector3d rollW = level(pose, rollL);
        double ground = WheelTerrain.centreHeight(level, hubW, rollW, t);
        double compression = ground - hubW.y + t.travel(); // 0 just touching, more as the ground pushes the hub up
        if (!(compression > 0)) {
            return;
        }
        compression = Math.min(compression, t.travel() + t.maxCompression());
        MassData mass = subLevel.getMassTracker();
        Vector3d upL = new Vector3d(0, 1, 0);
        Vector3d upW = pose.transformNormal(new Vector3d(upL));
        Vector3d contactL = new Vector3d(hubL).fma(-t.radius(), upL);
        double mEff = 1 / mass.getInverseNormalMass(contactL, upL);
        if (!(mEff > 0) || Double.isInfinite(mEff)) {
            return;
        }
        Vector3d v = Sable.HELPER.getVelocity(level, subLevel, contactL, new Vector3d());
        double omega = 2 * Math.PI * t.suspensionHz();
        double p = TraceCoupling.impulse(compression, -v.dot(upW), mEff, omega, t.dampingRatio(), h);
        if (p <= 0) {
            return;
        }
        Vector3d impulse = new Vector3d(upW).mul(p);
        // grip across the tyre and drag along it, both limited by the load
        Vector3d sideW = new Vector3d(-rollW.z, 0, rollW.x);
        impulse.fma(grip(mass, pose, contactL, sideW, v, t.lateralGrip(), t.friction() * p), sideW);
        impulse.fma(grip(mass, pose, contactL, rollW, v, 1, t.rollingResistance() * p), rollW);
        force.applyImpulseAtPoint(mass, contactL, pose.transformNormalInverse(impulse));
        handle.applyForcesAndReset(force);
    }

    /** The impulse along {@code dir} taking {@code share} of the speed that way, at most {@code limit}. */
    private static double grip(MassData mass, Pose3dc pose, Vector3d contactL, Vector3d dir, Vector3d v, double share,
                               double limit) {
        double speed = v.dot(dir);
        double m = 1 / mass.getInverseNormalMass(contactL, pose.transformNormalInverse(new Vector3d(dir)));
        if (!(m > 0) || Double.isInfinite(m)) {
            return 0;
        }
        double want = -speed * m * share;
        return Math.max(-limit, Math.min(limit, want));
    }

    /** Client, each tick: where the hub rides on the ground and how far the wheel has rolled, for the drawing. */
    void clientTick() {
        WheelType t = clientType;
        hubO = hub;
        rollO = roll;
        clientSteerO = clientSteer;
        clientSteer += (steer - clientSteer) * 0.4;
        SubLevel sub = Sable.HELPER.getContaining(this);
        if (t == null || sub == null) {
            hub += (-t(t) - hub) * 0.3;
            return;
        }
        Pose3dc pose = sub.logicalPose();
        Vector3d hubL = hubLocal();
        Vector3d hubW = pose.transformPosition(new Vector3d(hubL));
        Vector3d rollW = level(pose, rollLocal(clientSteer));
        double ground = WheelTerrain.centreHeight(level, hubW, rollW, t);
        double rise = Double.isInfinite(ground) ? -t.travel() : ground - hubW.y;
        hub += (Math.max(-t.travel(), Math.min(t.maxCompression(), rise)) - hub) * 0.7;
        Vector3d v = Sable.HELPER.getVelocity(level, sub, hubL, new Vector3d());
        roll += v.dot(rollW) * 0.05 / t.radius();
    }

    private static double t(@Nullable WheelType t) {
        return t == null ? 0.2 : t.travel();
    }

    // --- sync: the steering angle and the wheel's measurements ---------------------------------------------------

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("steer", steer);
        WheelType t = type();
        if (t != null) {
            tag.putDouble("radius", t.radius());
            tag.putDouble("reach", t.contactReach());
            tag.putDouble("height", t.contactHeight());
            tag.putDouble("travel", t.travel());
            tag.putDouble("max_compression", t.maxCompression());
        }
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        read(tag);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket pkt,
                             HolderLookup.Provider registries) {
        read(pkt.getTag());
    }

    private void read(CompoundTag tag) {
        steer = tag.getDouble("steer");
        if (tag.contains("radius")) {
            clientType = new WheelType(List.of(), tag.getDouble("radius"), tag.getDouble("reach"),
                    tag.getDouble("height"), tag.getDouble("travel"), tag.getDouble("max_compression"), 0, 0, 0, 0,
                    0, 0);
        }
    }
}
