package com.porcupinestudios.ringbearer.harness.content;

import com.porcupinestudios.ringbearer.harness.HarnessMod;
import com.porcupinestudios.ringbearer.harness.draught.SlopeContact;
import com.porcupinestudios.ringbearer.harness.physics.Convex;
import com.porcupinestudios.ringbearer.harness.physics.Sat;
import com.porcupinestudios.ringbearer.harness.physics.TraceCoupling;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.force.ForceTotal;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.neoforge.event.ForgeSablePrePhysicsTickEvent;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Vector3d;

/**
 * Vehicles against slope blocks, every physics substep, since slopes give Sable no collider (a staircase of boxes
 * catches sliding sleds). Two cases: the world's slopes against the vehicle's blocks, and the vehicle's own slopes (a
 * sled's nose) against the world's blocks. Each pair is a separating-axis test of the true shapes ({@link Sat}); an
 * overlap pushes the vehicle out along the contact normal through the same implicit spring-damper as the traces
 * (mass-scaled, stable at any step), with sliding friction along the face.
 */
public final class SlopeContacts {
    /** Each vehicle's own slope blocks (plot positions), found again every second. */
    private record OwnSlopes(long scannedAt, List<BlockPos> positions) {}

    private static final Map<SubLevel, OwnSlopes> OWN = new WeakHashMap<>();
    private static final int RESCAN_TICKS = 20;

    private SlopeContacts() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(SlopeContacts::prePhysics);
    }

    private static void prePhysics(ForgeSablePrePhysicsTickEvent event) {
        ServerLevel level = event.getPhysicsSystem().getLevel();
        double h = event.getTimeStep();
        for (SubLevel sub : SubLevelContainer.getContainer(level).getAllSubLevels()) {
            if (!(sub instanceof ServerSubLevel s) || s.isRemoved()) {
                continue;
            }
            RigidBodyHandle handle = event.getPhysicsSystem().getPhysicsHandle(s);
            if (handle == null || !handle.isValid()) {
                continue;
            }
            Pose3dc pose = s.logicalPose();
            ForceTotal force = new ForceTotal();
            Solve solve = new Solve();
            int contacts = worldSlopes(level, s, pose, force, solve, h) + ownSlopes(level, s, pose, force, solve, h);
            if (contacts > 0) {
                handle.applyForcesAndReset(force);
            }
        }
    }

    /** The world's slopes inside the vehicle's bounds against its blocks near them. */
    private static int worldSlopes(ServerLevel level, ServerSubLevel s, Pose3dc pose, ForceTotal force, Solve solve,
                                   double h) {
        BoundingBox3dc b = s.boundingBox();
        int n = 0;
        for (BlockPos p : BlockPos.betweenClosed(Mth.floor(b.minX() - 0.5), Mth.floor(b.minY() - 0.5),
                Mth.floor(b.minZ() - 0.5), Mth.floor(b.maxX() + 0.5), Mth.floor(b.maxY() + 0.5),
                Mth.floor(b.maxZ() + 0.5))) {
            BlockState state = level.getBlockState(p);
            if (!(state.getBlock() instanceof SlopeBlock)) {
                continue;
            }
            Convex slope = SlopeBlock.wedge(state, p);
            SlopeContact tune = tuning(state);
            // the vehicle's blocks that could reach it: its bounds in the vehicle's plot
            for (BlockPos q : plotBlocksNear(pose, slope.bounds())) {
                BlockState vs = level.getBlockState(q);
                for (Convex v : vehicleSolids(level, q, vs)) {
                    Sat.Contact c = Sat.test(slope, map(v, pose));
                    if (c != null) {
                        push(level, s, pose, force, solve, c.normal(), c.depth(), c.point(), tune, h);
                        n++;
                    }
                }
            }
        }
        return n;
    }

    /** The vehicle's own slopes against the world's blocks under and around them (world slopes are done above). */
    private static int ownSlopes(ServerLevel level, ServerSubLevel s, Pose3dc pose, ForceTotal force, Solve solve,
                                 double h) {
        int n = 0;
        for (BlockPos q : own(level, s)) {
            BlockState vs = level.getBlockState(q);
            if (!(vs.getBlock() instanceof SlopeBlock)) {
                continue;
            }
            Convex slope = map(SlopeBlock.wedge(vs, q), pose);
            SlopeContact tune = tuning(vs);
            double[] b = slope.bounds();
            for (BlockPos p : BlockPos.betweenClosed(Mth.floor(b[0]), Mth.floor(b[1]), Mth.floor(b[2]),
                    Mth.floor(b[3]), Mth.floor(b[4]), Mth.floor(b[5]))) {
                BlockState ws = level.getBlockState(p);
                if (ws.isAir() || ws.getBlock() instanceof SlopeBlock) {
                    continue;
                }
                for (AABB box : ws.getCollisionShape(level, p).toAabbs()) {
                    Convex world = Convex.box(p.getX() + box.minX, p.getY() + box.minY, p.getZ() + box.minZ,
                            p.getX() + box.maxX, p.getY() + box.maxY, p.getZ() + box.maxZ);
                    Sat.Contact c = Sat.test(world, slope);
                    if (c != null) {
                        push(level, s, pose, force, solve, c.normal(), c.depth(), c.point(), tune, h);
                        n++;
                    }
                }
            }
        }
        return n;
    }

    /**
     * The velocity change (vehicle frame) this substep's earlier contacts already gave the vehicle: each contact sees
     * it, so several contacts together cancel an approach once (each alone cancelling it pushed three times too hard
     * where a sled's deck met a ramp along three blocks, and threw it in the air).
     */
    private static final class Solve {
        final Vector3d dv = new Vector3d(), dw = new Vector3d();

        Vector3d velocityAt(Vector3d local, MassData mass) {
            Vector3d r = local.sub(mass.getCenterOfMass(), new Vector3d());
            return dw.cross(r, new Vector3d()).add(dv);
        }

        void add(Vector3d local, Vector3d impulseLocal, MassData mass) {
            dv.fma(mass.getInverseMass(), impulseLocal);
            Vector3d r = local.sub(mass.getCenterOfMass(), new Vector3d());
            dw.add(mass.getInverseInertiaTensor().transform(r.cross(impulseLocal, new Vector3d()), new Vector3d()));
        }
    }

    /** One contact: the vehicle pushed along {@code normal} (world, unit) at {@code point}, sliding with friction. */
    private static void push(ServerLevel level, ServerSubLevel s, Pose3dc pose, ForceTotal force, Solve solve,
                             Vector3d normal, double depth, Vector3d point, SlopeContact tune, double h) {
        MassData mass = s.getMassTracker();
        Vector3d local = pose.transformPositionInverse(new Vector3d(point));
        Vector3d nLocal = pose.transformNormalInverse(new Vector3d(normal));
        double mEff = 1 / mass.getInverseNormalMass(local, nLocal);
        if (!(mEff > 0) || Double.isInfinite(mEff)) {
            return;
        }
        Vector3d v = Sable.HELPER.getVelocity(level, s, local, new Vector3d())
                .add(pose.transformNormal(solve.velocityAt(local, mass)));
        double vn = v.dot(normal);
        double omega = 2 * Math.PI * tune.stiffnessHz();
        // depth closes like a stretched trace: the same implicit spring-damper, push only
        double p = TraceCoupling.impulse(depth, -vn, mEff, omega, tune.dampingRatio(), h);
        if (p <= 0) {
            return;
        }
        Vector3d impulse = new Vector3d(normal).mul(p);
        Vector3d vt = v.fma(-vn, normal, new Vector3d());
        double speed = vt.length();
        if (speed > 1e-6) {
            Vector3d dir = vt.div(speed);
            double mT = 1 / mass.getInverseNormalMass(local, pose.transformNormalInverse(new Vector3d(dir)));
            double jt = Math.min(tune.friction() * p, mT > 0 && Double.isFinite(mT) ? mT * speed : 0);
            impulse.fma(-jt, dir);
        }
        Vector3d impulseLocal = pose.transformNormalInverse(impulse);
        force.applyImpulseAtPoint(mass, local, impulseLocal);
        solve.add(local, impulseLocal, mass);
    }

    private static List<BlockPos> plotBlocksNear(Pose3dc pose, double[] worldBounds) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            Vector3d c = pose.transformPositionInverse(new Vector3d((i & 1) == 0 ? worldBounds[0] : worldBounds[3],
                    (i & 2) == 0 ? worldBounds[1] : worldBounds[4], (i & 4) == 0 ? worldBounds[2] : worldBounds[5]));
            minX = Math.min(minX, c.x); minY = Math.min(minY, c.y); minZ = Math.min(minZ, c.z);
            maxX = Math.max(maxX, c.x); maxY = Math.max(maxY, c.y); maxZ = Math.max(maxZ, c.z);
        }
        List<BlockPos> out = new ArrayList<>();
        BlockPos.betweenClosed(Mth.floor(minX), Mth.floor(minY), Mth.floor(minZ), Mth.floor(maxX), Mth.floor(maxY),
                Mth.floor(maxZ)).forEach(p -> out.add(p.immutable()));
        return out;
    }

    /** A vehicle block's solids in its plot: its collision boxes, or its true wedge if it's a slope. */
    private static List<Convex> vehicleSolids(ServerLevel level, BlockPos q, BlockState state) {
        if (state.isAir()) {
            return List.of();
        }
        if (state.getBlock() instanceof SlopeBlock) {
            return List.of(SlopeBlock.wedge(state, q));
        }
        List<Convex> out = new ArrayList<>();
        for (AABB box : state.getCollisionShape(level, q).toAabbs()) {
            out.add(Convex.box(q.getX() + box.minX, q.getY() + box.minY, q.getZ() + box.minZ, q.getX() + box.maxX,
                    q.getY() + box.maxY, q.getZ() + box.maxZ));
        }
        return out;
    }

    private static Convex map(Convex c, Pose3dc pose) {
        return c.map(pose::transformPosition, pose::transformNormal);
    }

    private static List<BlockPos> own(ServerLevel level, ServerSubLevel s) {
        long now = level.getGameTime();
        OwnSlopes cached = OWN.get(s);
        if (cached != null && now - cached.scannedAt() < RESCAN_TICKS) {
            return cached.positions();
        }
        List<BlockPos> found = new ArrayList<>();
        BoundingBox3ic b = s.getPlot().getBoundingBox();
        for (BlockPos p : BlockPos.betweenClosed(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ())) {
            if (level.getBlockState(p).getBlock() instanceof SlopeBlock) {
                found.add(p.immutable());
            }
        }
        OWN.put(s, new OwnSlopes(now, found));
        return found;
    }

    private static SlopeContact tuning(BlockState state) {
        SlopeContact t = HarnessMod.SLOPE_CONTACTS.of(state.getBlock());
        return t == null ? SlopeContact.DEFAULT : t;
    }
}
