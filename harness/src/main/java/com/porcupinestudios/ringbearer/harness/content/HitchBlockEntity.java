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
import java.util.Optional;
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
    /** 2 (2026-11-25): each hitch keeps its place in the team (side, ahead). */
    private static final int DATA_VERSION = 2;
    private static final double TICK = 0.05;
    /**
     * Per tick: strain follows the load over about a second. Faster, it fed a loop: the straining horse slowed, the pull
     * dropped, it sped up, the pull hit its limit... a 3 Hz pull/push shudder on an overloaded climb (2026-11-24).
     */
    private static final double STRAIN_RATE = 0.05;

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
        /**
         * The tow point at the last two game ticks, as the physics saw them. Not the entity's xo: a ridden animal is
         * placed by its rider's packets with absMoveTo, which sets xo to the new position too, so from xo a ridden
         * animal never moved and pulled nothing (2026-11-25).
         */
        final Vector3d tickPrev = new Vector3d(), tickNow = new Vector3d();
        long tickStamp = Long.MIN_VALUE;
        /** the animal's speed away from the hitch along the traces, smoothed (blocks/s), for the assist */
        double matchRate;
        /** the most it may pull this step (force): its own max_pull, or more with the assist on a heavy load */
        double limit;

        /** its place in the team: blocks to the hitch's right and ahead of it, where it stood when hitched */
        double side, ahead;
        /** driving (the leader, on the reins): its speed (blocks/tick) and heading (degrees) */
        double driveSpeed;
        float driveYaw = Float.NaN;

        Hitch(UUID animal, double rest, double side, double ahead) {
            this.animal = animal;
            this.rest = rest;
            this.side = side;
            this.ahead = ahead;
        }
    }

    /** side and ahead: data version 2; hitches saved before have their team place as straight ahead at rest length */
    private record SavedHitch(UUID animal, double rest, Optional<Double> side, Optional<Double> ahead) {
        static final Codec<SavedHitch> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("animal").forGetter(SavedHitch::animal),
                Codec.DOUBLE.fieldOf("rest").forGetter(SavedHitch::rest),
                Codec.DOUBLE.optionalFieldOf("side").forGetter(SavedHitch::side),
                Codec.DOUBLE.optionalFieldOf("ahead").forGetter(SavedHitch::ahead)
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

    /** The hitch's forward in the world, horizontal (on no vehicle, its block's facing). */
    Vector3d forwardWorld() {
        ServerSubLevel s = vehicle();
        Vector3d f = s == null ? forwardLocal() : s.logicalPose().transformNormal(forwardLocal(), new Vector3d());
        f.y = 0;
        return f.lengthSquared() < 1e-9 ? forwardLocal() : f.normalize();
    }

    /** The team's leader: the animal hitched furthest ahead (its place when hitched), or null. */
    @Nullable
    Hitch leader() {
        Hitch best = null;
        for (Hitch h : hitches) {
            if (best == null || h.ahead > best.ahead) {
                best = h;
            }
        }
        return best;
    }

    @Nullable
    Entity animal(Hitch h) {
        return find(h);
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
        // its place in the team, in the hitch's frame (where the animal itself stands)
        Vector3d fw = forwardWorld();
        Vector3d at = new Vector3d(animal.getX(), 0, animal.getZ()).sub(hitch.x, 0, hitch.z);
        hitches.add(new Hitch(animal.getUUID(), rest, at.x * -fw.z + at.z * fw.x, at.x * fw.x + at.z * fw.z));
        HitchLink.set(animal, worldPosition, rest, d);
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
                double limit = Math.max(da.maxPull(), hz.limit);
                double load = Math.max(pull / Math.max(1e-6, limit), hz.stretch / Math.max(1e-6, da.slack()));
                hz.strain += (Mth.clamp(load, 0, 1) - hz.strain) * STRAIN_RATE;
                Straining.apply(animal, da, hz.strain);
            }
        }
        // links from before the hold-back's limits were synced (version 1): renew them
        for (Hitch hz : hitches) {
            Entity a = find(hz);
            DraughtAnimal da = a == null ? null : draught(a);
            if (da != null && HitchLink.of(a).map(l -> l.dataVersion() < HitchLink.DATA_VERSION).orElse(true)) {
                HitchLink.set(a, worldPosition, hz.rest, da);
            }
        }
        // the pole turns the front axle toward the team's leader
        Hitch lead = leader();
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
        boolean assist = HarnessConfig.ASSIST.get();
        long tick = level.getGameTime();
        // with the assist each animal can give its share of the vehicle at least assist_accel, however heavy it is
        double assistForce = assist ? mass.getMass() * HarnessConfig.ASSIST_ACCEL.get() / hitches.size() : 0;
        // how much the load needs it: none for a light cart (the team pulls it easily, and the assist only made it
        // jerkier), all of it once the load at assist_accel needs half of what the team can pull or more
        double teamPull = 0;
        for (Hitch hz : hitches) {
            Entity e = find(hz);
            DraughtAnimal d = e == null ? null : draught(e);
            teamPull += d == null ? 0 : d.maxPull();
        }
        double need = teamPull <= 0 ? 1
                : Mth.clamp((mass.getMass() * HarnessConfig.ASSIST_ACCEL.get() / teamPull - 0.25) / 0.25, 0, 1);
        double match = assist ? need * Math.min(1, HarnessConfig.ASSIST_MATCH.get() * h) : 0;
        boolean pulled = false;
        for (Hitch hz : hitches) {
            Entity e = find(hz);
            DraughtAnimal d = e == null ? null : draught(e);
            if (d == null) {
                continue;
            }
            Vector3d raw = towPoint(e, d, 1);
            if (hz.tickStamp == Long.MIN_VALUE || raw.distanceSquared(hz.tickNow) > 16) {
                hz.tickPrev.set(raw);
                hz.tickNow.set(raw);
            } else if (tick != hz.tickStamp) {
                hz.tickPrev.set(hz.tickNow);
                hz.tickNow.set(raw);
            } else {
                hz.tickNow.set(raw); // moved again within the tick (a late rider packet): keep the latest
            }
            hz.tickStamp = tick;
            // only a rider's animal needs the physics' own record (see tickPrev); others keep xo, which is exact
            boolean ridden = e.getControllingPassenger() instanceof net.minecraft.world.entity.player.Player;
            Vector3d prev = ridden ? new Vector3d(hz.tickPrev) : towPoint(e, d, 0);
            Vector3d now = ridden ? new Vector3d(hz.tickNow) : towPoint(e, d, 1);
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
            double align = n.dot(forward);
            // the speed gap, smoothed over about half a second: a step-up's one-tick jump isn't followed
            hz.matchRate += (rate - hz.matchRate) * Math.min(1, 2 * h);
            if (assist && align > 0 && stretch > 0 && hz.matchRate > 0) {
                // the cheat: while the traces are taut, bring the hitch toward the animal's own speed along them, so
                // a heavy load keeps up with its team instead of waiting for the traces to stretch to their limit
                p += mEff * hz.matchRate * match * align;
            }
            hz.limit = Math.max(d.maxPull(), assistForce);
            double limited = TraceCoupling.limited(p, hz.limit, Math.max(d.maxPush(), assistForce), align, h);
            pulled = true;
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
        if (assist && pulled) {
            // the cheat, steadiness: a hitched vehicle's roll (side to side) spin dies away, so a heavy wagon doesn't
            // rock or tip over on a step-up. Pitch stays free (it has to tip up onto a step) and so does yaw (turns).
            double steady = need * Math.min(1, HarnessConfig.ASSIST_STEADY.get() * h);
            Vector3d axis = pose.transformNormal(new Vector3d(forward));
            if (axis.lengthSquared() > 1e-9) {
                axis.normalize();
                Vector3d w = handle.getAngularVelocity(new Vector3d());
                double roll = w.dot(axis);
                handle.addLinearAndAngularVelocity(new Vector3d(), axis.mul(-roll * steady));
            }
        }
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
        Hitch hz = hitchOf(e);
        if (s == null || d == null || hz == null) {
            return;
        }
        // a rider moves its animal on its own client, which holds it back there (HitchLink carries the limits);
        // moving it here as well would only fight the rider
        if (e.getControllingPassenger() instanceof net.minecraft.world.entity.player.Player) {
            hz.correction.zero();
            return;
        }
        Pose3dc pose = s.logicalPose();
        Vector3d back = com.porcupinestudios.ringbearer.harness.physics.Restraint.correction(
                pose.transformPosition(hitchLocal(), new Vector3d()), pose.transformNormal(forwardLocal(), new Vector3d()),
                towPoint(e, d, 1), hz.rest, d.slack(), d.pushSlack(), d.maxAngle(), hz.correction);
        if (back.lengthSquared() < 1e-12) {
            return;
        }
        e.setPos(e.getX() - back.x, e.getY(), e.getZ() - back.z);
        Vec3 v = e.getDeltaMovement();
        Vector3d out = back.normalize(new Vector3d());
        double along = v.x * out.x + v.z * out.z;
        if (along > 0) {
            e.setDeltaMovement(v.x - out.x * along, v.y, v.z - out.z * along);
        }
    }

    @Nullable
    Hitch hitchOf(Entity e) {
        return hitches.stream().filter(x -> x.animal.equals(e.getUUID())).findFirst().orElse(null);
    }

    // --- saving -------------------------------------------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        List<SavedHitch> list = hitches.stream().map(h -> new SavedHitch(h.animal, h.rest, Optional.of(h.side),
                Optional.of(h.ahead))).toList();
        Saved.CODEC.encodeStart(NbtOps.INSTANCE, new Saved(DATA_VERSION, list))
                .ifSuccess(t -> tag.put("harness", t));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        hitches.clear();
        if (tag.contains("harness")) {
            Saved.CODEC.parse(NbtOps.INSTANCE, tag.get("harness")).ifSuccess(saved ->
                    saved.hitches().forEach(h -> hitches.add(new Hitch(h.animal(), h.rest(), h.side().orElse(0.0),
                            h.ahead().orElse(h.rest())))));
        }
    }
}
