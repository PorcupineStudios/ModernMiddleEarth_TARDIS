package com.porcupinestudios.ringbearer.harness.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.ringbearer.harness.HarnessConfig;
import com.porcupinestudios.ringbearer.harness.HarnessMod;
import com.porcupinestudios.ringbearer.harness.draught.DraughtAnimal;
import com.porcupinestudios.ringbearer.harness.draught.DraughtAnimals;
import com.porcupinestudios.ringbearer.harness.physics.AnchorTracker;
import com.porcupinestudios.ringbearer.harness.physics.TraceCoupling;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.force.ForceTotal;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * The hitch on a vehicle. On a Create: Aeronautics contraption (a Sable sub-level) Sable calls
 * {@link #sable$physicsTick} every physics substep, as it does Offroad's wheel mounts; each hitched animal pulls through
 * {@link TraceCoupling} at the hitch point (the block's front face). Off a vehicle it does nothing.
 *
 * <p>The traces are a rod hinged up and down at the hitch (a wagon pole): only the level (horizontal) gap counts and the
 * pull is level, so an animal stepping up a block, jumping or bobbing doesn't lift, slam or jerk the vehicle. Sideways it
 * swings freely, so the vehicle follows the animal round a turn like a trailer.
 */
public class HitchBlockEntity extends BlockEntity implements BlockEntitySubLevelActor {
    private static final int DATA_VERSION = 1;
    private static final double TICK = 0.05;
    /**
     * Per tick: strain follows the load over about a second. Faster, it fed a loop: the straining horse slowed, the pull
     * dropped, it sped up, the pull hit its limit... a 3 Hz pull/push shudder on an overloaded climb (2026-11-24).
     */
    private static final double STRAIN_RATE = 0.05;
    /** Share of an animal's overshoot past its limits taken back each tick, and the overshoot taken back at once. */
    private static final double RESTRAIN_GAIN = 0.5, HARD_OVERSHOOT = 0.5;
    /** blocks per tick per tick: at most 8 blocks/s² of shove or hold from the hold-back */
    private static final double MAX_CORRECTION_CHANGE = 0.02;

    /** One hitched animal. The tracker and the last step's numbers are runtime only. */
    static final class Hitch {
        final UUID animal;
        double rest;
        final AnchorTracker tracker = new AnchorTracker();
        @Nullable Entity cached;
        double stretch;
        double force;
        boolean saturated;
        /** the traces against the vehicle's forward (degrees, its own frame), NaN before the first step */
        double traceAngle = Double.NaN;
        /** how hard it works, 0..1, smoothed over about a second (slows it down) */
        double strain;
        /** the hold-back's last correction (blocks this tick, world), eased so a shove builds up instead of jumping */
        final Vector3d correction = new Vector3d();
        /** pull summed over this tick's substeps, for the strain */
        double forceSum;
        int forceSteps;

        Hitch(UUID animal, double rest) {
            this.animal = animal;
            this.rest = rest;
        }
    }

    private record SavedHitch(UUID animal, double rest) {
        static final Codec<SavedHitch> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("animal").forGetter(SavedHitch::animal),
                Codec.DOUBLE.fieldOf("rest").forGetter(SavedHitch::rest)
        ).apply(i, SavedHitch::new));
    }

    private record Saved(int dataVersion, List<SavedHitch> hitches) {
        static final Codec<Saved> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("data_version", DATA_VERSION).forGetter(Saved::dataVersion),
                SavedHitch.CODEC.listOf().fieldOf("hitches").forGetter(Saved::hitches)
        ).apply(i, Saved::new));
    }

    /** What one physics step did, for tests and debugging (the first hitched animal). */
    public record Step(double stretch, double force, boolean saturated, double speed, double animalSpeed,
                       double pitch, double roll, double pitchRate, double yawRate, double rollRate,
                       double traceAngle, double micros, double mass, Vector3dc wagonPos, Vector3dc towPos) {}

    public interface Probe {
        void step(Step step);
    }

    private final List<Hitch> hitches = new ArrayList<>();
    private final ForceTotal force = new ForceTotal();
    @Nullable private Probe probe;
    @Nullable private DraughtAnimal tuning;

    public HitchBlockEntity(BlockPos pos, BlockState state) {
        super(HarnessMod.HITCH_BE.get(), pos, state);
    }

    public void setProbe(@Nullable Probe probe) {
        this.probe = probe;
    }

    /** Overrides every hitched animal's draught data (GameTest calibration); null goes back to the data files. */
    public void setTuning(@Nullable DraughtAnimal tuning) {
        this.tuning = tuning;
    }

    @Nullable
    private DraughtAnimal draught(Entity e) {
        DraughtAnimal d = DraughtAnimals.of(e);
        return d != null && tuning != null ? tuning : d;
    }

    public boolean hasHitches() {
        return !hitches.isEmpty();
    }

    // --- geometry, in the vehicle's own (plot) frame ------------------------------------------------------------

    private Direction facing() {
        return getBlockState().getValue(HitchBlock.FACING);
    }

    /** The hitch point: the middle of the block's front face. */
    private Vector3d hitchLocal() {
        Direction f = facing();
        return new Vector3d(worldPosition.getX() + 0.5 + 0.5 * f.getStepX(), worldPosition.getY() + 0.5,
                worldPosition.getZ() + 0.5 + 0.5 * f.getStepZ());
    }

    private Vector3d forwardLocal() {
        Direction f = facing();
        return new Vector3d(f.getStepX(), 0, f.getStepZ());
    }

    /** Where the traces start on the animal, at its old ({@code partial} 0) or current (1) position and body turn. */
    static Vector3d towPoint(Entity e, DraughtAnimal d, double partial) {
        double x = Mth.lerp(partial, e.xo, e.getX());
        double y = Mth.lerp(partial, e.yo, e.getY());
        double z = Mth.lerp(partial, e.zo, e.getZ());
        float yaw = e instanceof Mob mob ? Mth.rotLerp((float) partial, mob.yBodyRotO, mob.yBodyRot)
                : Mth.rotLerp((float) partial, e.yRotO, e.getYRot());
        double rad = Math.toRadians(yaw);
        // Minecraft: yaw 0 faces +z; forward = (-sin, 0, cos), right = (-cos, 0, -sin)
        double fx = -Math.sin(rad), fz = Math.cos(rad);
        double rx = -fz, rz = fx;
        List<Double> p = d.towPoint();
        return new Vector3d(x + rx * p.get(0) + fx * p.get(2), y + p.get(1), z + rz * p.get(0) + fz * p.get(2));
    }

    @Nullable
    private ServerSubLevel vehicle() {
        SubLevel s = level == null ? null : Sable.HELPER.getContaining(this);
        return s instanceof ServerSubLevel ss && !ss.isRemoved() ? ss : null;
    }

    /** The hitch point in world space, or null off a vehicle. */
    @Nullable
    public Vec3 hitchWorld() {
        ServerSubLevel s = vehicle();
        if (s == null) {
            return null;
        }
        Vector3d w = s.logicalPose().transformPosition(hitchLocal(), new Vector3d());
        return new Vec3(w.x, w.y, w.z);
    }

    // --- hitching ---------------------------------------------------------------------------------------------------

    /**
     * Hitches {@code animal} (it must wear a harness): its rest length is how far its tow point is from the hitch now (horizontally),
     * kept within its kind's gaps. False if it can't pull or is already hitched here.
     */
    public boolean hitch(Entity animal) {
        DraughtAnimal d = DraughtAnimals.of(animal);
        if (d == null || !Harnessing.wears(animal) || hitches.stream().anyMatch(h -> h.animal.equals(animal.getUUID()))) {
            return false;
        }
        ServerSubLevel s = vehicle();
        Vector3d hitch = hitchLocal();
        if (s != null) {
            s.logicalPose().transformPosition(hitch);
        }
        Vector3d gap = towPoint(animal, d, 1).sub(hitch);
        gap.y = 0;
        double rest = Mth.clamp(gap.length(), d.minGap(), d.maxGap());
        hitches.add(new Hitch(animal.getUUID(), rest));
        HitchLink.set(animal, worldPosition);
        setChanged();
        return true;
    }

    /** Unhitches one animal (its harness stays on). */
    public void unhitch(Entity animal) {
        if (hitches.removeIf(h -> h.animal.equals(animal.getUUID()))) {
            setChanged();
        }
        HitchLink.clear(animal);
        Straining.clear(animal);
    }

    /** Unhitches everything; returns the animals that were here (and loaded). */
    public List<Entity> unhitchAll() {
        List<Entity> out = new ArrayList<>();
        for (Hitch h : hitches) {
            Entity e = find(h);
            if (e != null) {
                HitchLink.clear(e);
                Straining.clear(e);
                out.add(e);
            }
        }
        hitches.clear();
        setChanged();
        return out;
    }

    @Nullable
    private Entity find(Hitch h) {
        if (h.cached != null && !h.cached.isRemoved()) {
            return h.cached;
        }
        h.cached = level instanceof ServerLevel sl ? sl.getEntity(h.animal) : null;
        return h.cached;
    }

    // --- Sable --------------------------------------------------------------------------------------------------------

    /** Once a game tick: let go of animals that died or got too far. */
    @Override
    public void sable$tick(ServerSubLevel subLevel) {
        boolean changed = hitches.removeIf(h -> {
            Entity e = find(h);
            if (e == null) {
                return false; // unloaded: keep the hitch
            }
            DraughtAnimal d = draught(e);
            Vec3 hw = hitchWorld();
            boolean gone = !e.isAlive() || d == null
                    || (hw != null && e.position().distanceTo(hw) > d.breakDistance() + h.rest);
            if (gone) {
                HitchLink.clear(e);
                Straining.clear(e);
            }
            return gone;
        });
        if (changed) {
            setChanged();
        }
        for (Hitch hz : hitches) {
            Entity animal = find(hz);
            DraughtAnimal da = animal == null ? null : draught(animal);
            if (da != null) {
                double pull = hz.forceSteps == 0 ? 0 : hz.forceSum / hz.forceSteps;
                hz.forceSum = 0;
                hz.forceSteps = 0;
                double load = Math.max(pull / Math.max(1e-6, da.maxPull()), hz.stretch / Math.max(1e-6, da.slack()));
                hz.strain += (Mth.clamp(load, 0, 1) - hz.strain) * STRAIN_RATE;
                Straining.apply(animal, da, hz.strain);
            }
        }
        // the pole turns the front axle toward the first animal
        Hitch lead = hitches.isEmpty() ? null : hitches.get(0);
        Entity e = lead == null ? null : find(lead);
        DraughtAnimal d = e == null ? null : draught(e);
        if (d == null || Double.isNaN(lead.traceAngle) || d.steer() == 0) {
            HarnessSteering.clear(subLevel);
        } else {
            HarnessSteering.set(subLevel, facing(), d.steer() * lead.traceAngle);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        SubLevel s = level == null ? null : Sable.HELPER.getContaining(this);
        if (s != null) {
            HarnessSteering.clear(s);
        }
    }

    @Override
    public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double h) {
        if (hitches.isEmpty() || !HarnessConfig.PULLING.get()) {
            return;
        }
        long start = System.nanoTime();
        ServerLevel level = subLevel.getLevel();
        // physics runs at the start of the level tick, before entities move: substep k of n sits between the
        // animal's last two positions at (k + 1) / n
        double partial = SubLevelPhysicsSystem.require(level).getPartialPhysicsTick();
        Pose3dc pose = subLevel.logicalPose();
        MassData mass = subLevel.getMassTracker();
        Vector3d hitch = hitchLocal();
        Vector3d forward = forwardLocal();
        Vector3d hitchWorld = pose.transformPosition(hitch, new Vector3d());
        Vector3d hitchVel = Sable.HELPER.getVelocity(level, subLevel, hitch, new Vector3d());
        Step report = null;
        for (Hitch hz : hitches) {
            Entity e = find(hz);
            DraughtAnimal d = e == null ? null : draught(e);
            if (d == null) {
                continue;
            }
            Vector3d prev = towPoint(e, d, 0);
            Vector3d now = towPoint(e, d, 1);
            Vector3d target = prev.lerp(now, partial, new Vector3d());
            Vector3d targetVel = now.sub(prev, new Vector3d()).div(TICK);
            hz.tracker.step(target, targetVel, d.smoothingOmega(), h, 4);

            // the gap in the world's horizontal plane (the pole's hinge): an animal stepping up, jumping or bobbing,
            // or the vehicle pitching over a bump, doesn't change it. (Measured in the vehicle's own tilted floor, every
            // 1-block step-up on a 14-degree slope read as a quarter block of stretch: kicks.)
            Vector3d dw = hz.tracker.position().sub(hitchWorld, new Vector3d());
            Vector3d vw = hz.tracker.velocity().sub(hitchVel, new Vector3d());
            dw.y = 0;
            vw.y = 0;
            double dist = dw.length();
            if (dist < 1e-4) {
                continue;
            }
            Vector3d nw = dw.div(dist);
            double stretch = dist - hz.rest;
            double rate = vw.dot(nw);
            Vector3d n = pose.transformNormalInverse(nw, new Vector3d()); // the same direction, vehicle frame
            double mEff = 1 / mass.getInverseNormalMass(hitch, n);
            if (!(mEff > 0) || Double.isInfinite(mEff)) {
                continue;
            }
            double p = TraceCoupling.impulse(stretch, rate, mEff, d.omega(), d.dampingRatio(), h);
            double limited = TraceCoupling.limited(p, d.maxPull(), d.maxPush(), n.dot(forward), h);
            force.applyImpulseAtPoint(mass, hitch, n.mul(limited, new Vector3d()));
            hz.stretch = stretch;
            hz.traceAngle = Math.toDegrees(Math.atan2(forward.x * n.z - forward.z * n.x,
                    forward.x * n.x + forward.z * n.z));
            hz.force = limited / h;
            hz.forceSum += hz.force;
            hz.forceSteps++;
            hz.saturated = limited != p;
            if (report == null && probe != null) {
                report = report(subLevel, handle, pose, mass, forward, n, hz, hz.tracker.velocity(), start);
            }
        }
        handle.applyForcesAndReset(force);
        if (report != null) {
            double micros = (System.nanoTime() - start) / 1000.0;
            probe.step(new Step(report.stretch(), report.force(), report.saturated(), report.speed(),
                    report.animalSpeed(), report.pitch(), report.roll(), report.pitchRate(), report.yawRate(),
                    report.rollRate(), report.traceAngle(), micros, report.mass(), report.wagonPos(), report.towPos()));
        }
    }

    private static Step report(ServerSubLevel subLevel, RigidBodyHandle handle, Pose3dc pose, MassData mass,
                               Vector3dc forward, Vector3dc n, Hitch hz, Vector3dc animalVel, long start) {
        Vector3d up = new Vector3d(0, 1, 0);
        Vector3d right = up.cross(forward, new Vector3d());
        Vector3d fw = pose.transformNormal(new Vector3d(forward));
        Vector3d rw = pose.transformNormal(new Vector3d(right));
        Vector3d v = handle.getLinearVelocity(new Vector3d());
        Vector3d w = pose.transformNormalInverse(handle.getAngularVelocity(new Vector3d()));
        double traceAngle = Math.toDegrees(Math.atan2(forward.x() * n.z() - forward.z() * n.x(),
                forward.x() * n.x() + forward.z() * n.z()));
        return new Step(hz.stretch, hz.force, hz.saturated, v.dot(fw),
                Math.hypot(animalVel.x(), animalVel.z()),
                Math.toDegrees(Math.asin(Mth.clamp(fw.y, -1, 1))), Math.toDegrees(Math.asin(Mth.clamp(rw.y, -1, 1))),
                Math.toDegrees(w.dot(right)), Math.toDegrees(w.y), Math.toDegrees(w.dot(forward)), traceAngle,
                0, mass.getMass(), new Vector3d(pose.position()), new Vector3d(hz.tracker.position()));
    }

    // --- the animal's side: held back at the end of its traces ----------------------------------------------------

    /**
     * Called after the animal moves (and by scripted drivers), as a last resort behind {@link Straining}'s slowing: an
     * animal further than its rest length plus slack from the hitch (measured level) is put back at that distance and
     * loses its outward speed; one pushed closer than rest minus push slack is shoved forward; one swung round past
     * the pole's max angle is put back on it.
     */
    public void restrain(Entity e) {
        ServerSubLevel s = vehicle();
        DraughtAnimal d = draught(e);
        Hitch hz = hitches.stream().filter(x -> x.animal.equals(e.getUUID())).findFirst().orElse(null);
        if (s == null || d == null || hz == null) {
            return;
        }
        Pose3dc pose = s.logicalPose();
        Vector3d hitchWorld = pose.transformPosition(hitchLocal(), new Vector3d());
        Vector3d tow = towPoint(e, d, 1);
        Vector3d dw = tow.sub(hitchWorld, new Vector3d());
        dw.y = 0;
        double dist = dw.length();
        if (dist < 1e-6) {
            return;
        }
        // the pole: no further round than max_angle from straight ahead of the hitch, no further out than rest + slack
        Vector3d fw = pose.transformNormal(forwardLocal());
        double ahead = Math.atan2(fw.z, fw.x);
        double angle = Mth.wrapDegrees(Math.toDegrees(Math.atan2(dw.z, dw.x) - ahead));
        double maxAngle = Mth.clamp(d.maxAngle(), 0, 180);
        double keep = Mth.clamp(dist, Math.max(0.05, hz.rest - d.pushSlack()), hz.rest + d.slack());
        if (keep == dist && Math.abs(angle) <= maxAngle) {
            hz.correction.zero();
            return;
        }
        double a = ahead + Math.toRadians(Mth.clamp(angle, -maxAngle, maxAngle));
        Vector3d back = dw.sub(new Vector3d(Math.cos(a) * keep, 0, Math.sin(a) * keep), new Vector3d());
        // soft: half the overshoot a tick, and that correction changes by at most MAX_CORRECTION_CHANGE a tick, so a
        // shove or a hold builds up over a few ticks (a one-tick snap read as a yank); past half a block, all at once
        double over = back.length();
        if (over > HARD_OVERSHOOT) {
            back.mul((over - HARD_OVERSHOOT * RESTRAIN_GAIN) / over);
        } else {
            back.mul(RESTRAIN_GAIN);
            Vector3d change = back.sub(hz.correction, new Vector3d());
            if (change.length() > MAX_CORRECTION_CHANGE) {
                change.mul(MAX_CORRECTION_CHANGE / change.length());
            }
            back.set(hz.correction).add(change);
        }
        hz.correction.set(back);
        e.setPos(e.getX() - back.x, e.getY(), e.getZ() - back.z);
        Vec3 v = e.getDeltaMovement();
        if (back.lengthSquared() > 1e-12) {
            Vector3d out = back.normalize(new Vector3d());
            double along = v.x * out.x + v.z * out.z;
            if (along > 0) {
                e.setDeltaMovement(v.x - out.x * along, v.y, v.z - out.z * along);
            }
        }
    }

    // --- saving -------------------------------------------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        List<SavedHitch> list = hitches.stream().map(h -> new SavedHitch(h.animal, h.rest)).toList();
        Saved.CODEC.encodeStart(NbtOps.INSTANCE, new Saved(DATA_VERSION, list))
                .ifSuccess(t -> tag.put("harness", t));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        hitches.clear();
        if (tag.contains("harness")) {
            Saved.CODEC.parse(NbtOps.INSTANCE, tag.get("harness")).ifSuccess(saved ->
                    saved.hitches().forEach(h -> hitches.add(new Hitch(h.animal(), h.rest()))));
        }
    }
}
