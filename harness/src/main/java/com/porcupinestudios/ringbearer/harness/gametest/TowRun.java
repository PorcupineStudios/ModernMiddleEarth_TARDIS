package com.porcupinestudios.ringbearer.harness.gametest;

import com.mojang.logging.LogUtils;
import com.porcupinestudios.ringbearer.harness.HarnessMod;
import com.porcupinestudios.ringbearer.harness.content.HarnessBlock;
import com.porcupinestudios.ringbearer.harness.content.HarnessBlockEntity;
import com.porcupinestudios.ringbearer.harness.physics.TowMetrics;
import dev.ryanhcode.offroad.content.blocks.wheel_mount.WheelMountBlock;
import dev.ryanhcode.offroad.content.blocks.wheel_mount.WheelMountBlockEntity;
import dev.ryanhcode.offroad.index.OffroadBlocks;
import dev.ryanhcode.offroad.index.OffroadItems;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.slf4j.Logger;

/**
 * One scripted tow on a 48 x 72 course (near the world origin; the test's {@code arena} only times it): ground of the
 * given surface, a 3 x 5 plank wagon on four Offroad wheel mounts with tires and a harness at its front, assembled
 * into a Sable sub-level (what Simulated's assembler makes), and a horse hitched to it. The horse is driven along a path (speed up, cruise, turn, slow down, stand) and
 * every physics step is recorded; at the end {@link TowMetrics} measures the run, which is logged, written to
 * {@code harness-metrics/} in the game folder (with a CSV of every step) and checked against {@link Limits}.
 */
public final class TowRun {
    private static final Logger LOG = LogUtils.getLogger();

    public enum Surface {
        /** level ground */
        FLAT,
        /** climbing 1 block in 4 (14 degrees) straight ahead: pitch */
        UPHILL,
        /** the same slope going down: the horse holds the wagon back */
        DOWNHILL,
        /** the slope rising to the side: roll */
        SIDEHILL,
        /** the uphill slope built as a road of half-block slab steps (what players build): how much is the steps */
        UPHILL_SLABS
    }

    /**
     * @param yawRate degrees/s while turning (positive turns left), 0 = straight on
     * @param turnSeconds how long the turn (or the extra cruise) lasts
     * @param traces {stiffness Hz, damping ratio, smoothing Hz} instead of the horse's data (calibration sweeps,
     *               which only report), or null
     */
    public record Scenario(String name, Surface surface, double speed, double yawRate, double turnSeconds,
                           double @org.jetbrains.annotations.Nullable [] traces, int cargo) {
        public Scenario(String name, Surface surface, double speed, double yawRate, double turnSeconds) {
            this(name, surface, speed, yawRate, turnSeconds, null, 0);
        }

        public Scenario(String name, Surface surface, double speed, double yawRate, double turnSeconds,
                        double @org.jetbrains.annotations.Nullable [] traces) {
            this(name, surface, speed, yawRate, turnSeconds, traces, 0);
        }
    }

    /**
     * Pass marks; anything over fails the test with the whole measurement in the message. On 1-block stair slopes the
     * Offroad wheels climbing each step give kicks and pitch wobble whatever the traces do (calibration sweeps,
     * 2026-11-24: the same at 0.3 to 2.4 Hz), so there the marks guard what the harness controls (stretch, sustained
     * shaking, chatter) and only cap the terrain's bumps.
     */
    public record Limits(double stretchMax, double vibration, double shakeHz, double chatterPerS, double wobbleRms,
                         double accelOverAnimal, double jerkRms, double settleS, int rings, double traceAngleMax,
                         double microsMean, double minDistance) {
        static Limits of(Scenario sc) {
            if (sc.cargo() > 0) {
                // overloaded: pulling at its limit the horse leans into taut traces (stretch up to its slack is
                // right), and the heavy wagon on block steps settles slowly
                return new Limits(1.0, 0.5, 4, 3, 35, 5, 90, 2.5, 2, 90, 60, 8);
            }
            return sc.surface() == Surface.FLAT
                    ? new Limits(0.5, 0.1, 4, 0.5, 2, 1.5, 15, 1.0, 1, 70, 60, 8)
                    : new Limits(0.5, 0.5, 4, 3, 35, 5, 90, 1.5, 1, 70, 60, 8);
        }
    }

    private static final double SETTLE = 1.5, ACCEL = 2, CRUISE = 2, DECEL = 1.5, STAND = 3, RAMP = 0.5;
    private static final int X0 = 24, Z_WAGON = 3, LENGTH = 5;

    private final GameTestHelper helper;
    private final Scenario sc;
    private final ServerLevel level;
    private final List<TowMetrics.Sample> samples = new ArrayList<>();
    private volatile int phase = TowMetrics.SETTLE;
    private double heading; // radians, Minecraft yaw: 0 = +z
    private double px, pz; // where the horse was put (absolute)
    private double vy;
    private Horse horse;
    private HarnessBlockEntity harness;
    private ServerSubLevel wagon;
    private double mass;
    private int substeps;


    /** Runs by their horse: each horse is driven in its own entity tick, as an animal moves in game. */
    private static final java.util.Map<net.minecraft.world.entity.Entity, TowRun> DRIVEN =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static boolean listening;

    /**
     * The horse moves in its own tick (EntityTickEvent.Pre), so the harness holds it back right after (Post), then
     * physics at the start of the next tick sees where it ended up: the order an animal walking by itself or ridden
     * gets in game. (Moving it from the GameTest's tick, after all entities, ran the hold-back twice a tick.)
     */
    private static synchronized void listen() {
        if (!listening) {
            listening = true;
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                    (net.neoforged.neoforge.event.tick.EntityTickEvent.Pre e) -> {
                        TowRun run = DRIVEN.get(e.getEntity());
                        if (run != null) {
                            run.tick();
                        }
                    });
        }
    }

    /** Courses already laid out this run (each gets its own strip of ground). */
    private static final java.util.concurrent.atomic.AtomicInteger COURSES = new java.util.concurrent.atomic.AtomicInteger();

    private final BlockPos origin;
    private final int index;
    private long startTick;

    private TowRun(GameTestHelper helper, Scenario sc) {
        this.helper = helper;
        this.sc = sc;
        this.level = helper.getLevel();
        // The GameTest server puts tests millions of blocks out, where Sable's physics positions round to a quarter
        // or half block and a wagon can't move in smaller steps (it froze, 2026-11-24). Each course is laid out near
        // the world origin instead, in force-loaded chunks; the test's own (empty) arena only times it.
        this.index = COURSES.getAndIncrement();
        this.origin = new BlockPos(512 + index * 64, 0, 512);
        for (int cx = (origin.getX() - 16) >> 4; cx <= (origin.getX() + 64) >> 4; cx++) {
            for (int cz = (origin.getZ() - 16) >> 4; cz <= (origin.getZ() + 88) >> 4; cz++) {
                level.setChunkForced(cx, cz, true);
                level.getChunk(cx, cz);
            }
        }
    }

    /** A course-relative position in the world. */
    private BlockPos abs(BlockPos rel) {
        return origin.offset(rel);
    }

    /**
     * Sets the run up a few ticks after the previous one: Sable sometimes left a wagon frozen (no gravity, no response)
     * when many were built and assembled in the same tick (2026-11-24), which players never do.
     */
    public static void start(GameTestHelper helper, Scenario sc) {
        TowRun run = new TowRun(helper, sc);
        long begin = 1 + run.index * 4L;
        helper.runAtTickTime(begin, run::begin);
        listen();
        helper.runAtTickTime(begin + Math.round(run.total() * 20) + 1, run::finish);
    }

    private double total() {
        return SETTLE + ACCEL + CRUISE + sc.turnSeconds() + DECEL + STAND;
    }

    /** The y of the ground's top block (or slab) at a relative column. */
    private int ground(int x, int z) {
        return (halves(x, z) - 1) / 2;
    }

    /** The ground's surface height in half blocks above y 0: full blocks up to it, a bottom slab if it's odd. */
    private int halves(int x, int z) {
        return switch (sc.surface()) {
            case UPHILL_SLABS -> 6 + Math.floorDiv(z, 2);
            default -> 2 * (groundBlocks(x, z) + 1);
        };
    }

    private int groundBlocks(int x, int z) {
        return switch (sc.surface()) {
            case FLAT, UPHILL_SLABS -> 2;
            case UPHILL -> 2 + Math.floorDiv(z, 4);
            case DOWNHILL -> 2 + Math.floorDiv(71 - z, 4);
            case SIDEHILL -> 2 + Math.floorDiv(x, 4);
        };
    }

    private void begin() {
        // ground
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
        BlockState slab = Blocks.SMOOTH_STONE_SLAB.defaultBlockState();
        for (int x = 0; x < 48; x++) {
            for (int z = 0; z < 72; z++) {
                int h = halves(x, z);
                int full = h / 2; // full blocks at y 0 .. full - 1, then a bottom slab if h is odd
                for (int y = 0; y < full; y++) {
                    level.setBlock(abs(new BlockPos(x, y, z)), y == full - 1 && h % 2 == 0 ? grass : dirt, 2);
                }
                if (h % 2 == 1) {
                    level.setBlock(abs(new BlockPos(x, full, z)), slab, 2);
                }
            }
        }
        // the wagon: deck x 23..25, z 3..7, wheel mounts at the corners facing out, harness in front facing +z
        int deckY = 0;
        for (int x = X0 - 2; x <= X0 + 2; x++) {
            for (int z = Z_WAGON; z < Z_WAGON + LENGTH; z++) {
                deckY = Math.max(deckY, ground(x, z));
            }
        }
        deckY += 2;
        List<BlockPos> blocks = new ArrayList<>();
        for (int x = X0 - 1; x <= X0 + 1; x++) {
            for (int z = Z_WAGON; z < Z_WAGON + LENGTH; z++) {
                BlockPos p = abs(new BlockPos(x, deckY, z));
                boolean corner = x != X0 && (z == Z_WAGON || z == Z_WAGON + LENGTH - 1);
                if (corner) {
                    Direction out = x < X0 ? Direction.WEST : Direction.EAST;
                    level.setBlockAndUpdate(p, OffroadBlocks.WHEEL_MOUNT.getDefaultState()
                            .setValue(WheelMountBlock.HORIZONTAL_FACING, out));
                    if (level.getBlockEntity(p) instanceof WheelMountBlockEntity mount) {
                        mount.getInventory().slot.setStack(new ItemStack(OffroadItems.TIRE.get()));
                        mount.setChanged();
                    }
                } else {
                    level.setBlockAndUpdate(p, Blocks.OAK_PLANKS.defaultBlockState());
                }
                blocks.add(p);
            }
        }
        // cargo: iron blocks on the deck's middle row (an overloaded wagon)
        for (int i = 0; i < sc.cargo(); i++) {
            BlockPos p = abs(new BlockPos(X0 + (i % 3) - 1, deckY + 1 + i / 9, Z_WAGON + 1 + (i / 3) % 3));
            level.setBlockAndUpdate(p, Blocks.IRON_BLOCK.defaultBlockState());
            blocks.add(p);
        }
        BlockPos harnessAt = abs(new BlockPos(X0, deckY, Z_WAGON + LENGTH));
        level.setBlockAndUpdate(harnessAt, HarnessMod.HARNESS.get().defaultBlockState()
                .setValue(HarnessBlock.FACING, Direction.SOUTH));
        blocks.add(harnessAt);
        BlockPos min = abs(new BlockPos(X0 - 1, deckY, Z_WAGON));
        BlockPos max = harnessAt.offset(1, 3, 0);
        wagon = SubLevelAssemblyHelper.assembleBlocks(level, harnessAt, blocks,
                new BoundingBox3i(min.getX(), min.getY(), min.getZ(), max.getX(), max.getY(), max.getZ()));
        Vec3 local = wagon.logicalPose().transformPositionInverse(Vec3.atCenterOf(harnessAt));
        if (!(level.getBlockEntity(BlockPos.containing(local)) instanceof HarnessBlockEntity be)) {
            helper.fail("no harness on the assembled wagon at " + BlockPos.containing(local));
            return;
        }
        harness = be;
        mass = wagon.getMassTracker().getMass();
        substeps = SubLevelPhysicsSystem.require(level).getConfig().substepsPerTick;

        // the horse, its tow point 1 block in front of the hitch
        Vec3 hitch = Vec3.atCenterOf(harnessAt).add(0, 0, 0.5);
        px = hitch.x;
        pz = hitch.z + 1.0 + 0.95;
        horse = EntityType.HORSE.create(level);
        if (horse == null) {
            helper.fail("no horse");
            return;
        }
        horse.setNoAi(true);
        horse.setNoGravity(true);
        horse.setTamed(true);
        int groundY = ground(X0, (int) Math.floor(pz) - origin.getZ());
        horse.moveTo(px, abs(new BlockPos(0, groundY + 1, 0)).getY(), pz, 0, 0);
        horse.setYBodyRot(0);
        horse.setYHeadRot(0);
        level.addFreshEntity(horse);
        if (!harness.hitch(horse)) {
            helper.fail("the horse wouldn't hitch (is draught/horse.json loaded?)");
            return;
        }
        if (sc.traces() != null) {
            com.porcupinestudios.ringbearer.harness.draught.DraughtAnimal d =
                    com.porcupinestudios.ringbearer.harness.draught.DraughtAnimals.of(horse);
            harness.setTuning(d.withTraces(sc.traces()[0], sc.traces()[1], sc.traces()[2]));
        }
        harness.setProbe(this::record);
        startTick = helper.getTick();
        DRIVEN.put(horse, this);
    }

    private final List<double[]> positions = new ArrayList<>();

    private void record(HarnessBlockEntity.Step s) {
        double t = samples.size() * 0.05 / substeps;
        positions.add(new double[] {s.wagonPos().x(), s.wagonPos().y(), s.wagonPos().z(), s.towPos().x(),
                s.towPos().y(), s.towPos().z(), horse.getX(), horse.getZ(), px, pz});
        samples.add(new TowMetrics.Sample(t, phase, s.stretch(), s.force(), s.saturated(), s.speed(), s.animalSpeed(),
                s.pitch(), s.roll(), s.pitchRate(), s.yawRate(), s.rollRate(), s.traceAngle(), s.micros()));
    }

    private static double smooth(double u) {
        u = Math.max(0, Math.min(1, u));
        return u * u * (3 - 2 * u);
    }

    /** The horse's speed and turn rate at time t, and the phase. */
    private double[] plan(double t) {
        double turnStart = SETTLE + ACCEL + CRUISE;
        double decelStart = turnStart + sc.turnSeconds();
        double stop = decelStart + DECEL;
        double speed;
        int ph;
        if (t < SETTLE) {
            speed = 0;
            ph = TowMetrics.SETTLE;
        } else if (t < SETTLE + ACCEL) {
            speed = sc.speed() * smooth((t - SETTLE) / ACCEL);
            ph = TowMetrics.ACCEL;
        } else if (t < decelStart) {
            speed = sc.speed();
            ph = t >= turnStart && sc.yawRate() != 0 ? TowMetrics.TURN : TowMetrics.CRUISE;
        } else if (t < stop) {
            speed = sc.speed() * (1 - smooth((t - decelStart) / DECEL));
            ph = TowMetrics.DECEL;
        } else {
            speed = 0;
            ph = TowMetrics.STOPPED;
        }
        double yaw = 0;
        if (sc.yawRate() != 0 && t >= turnStart && t < decelStart) {
            double in = smooth((t - turnStart) / RAMP);
            double out = 1 - smooth((t - (decelStart - RAMP)) / RAMP);
            yaw = Math.toRadians(sc.yawRate()) * Math.min(in, out);
        }
        return new double[] {speed, yaw, ph};
    }

    private void tick() {
        if (horse == null || wagon == null) {
            return;
        }
        if (helper.getTick() <= startTick) {
            return;
        }

        double t = (helper.getTick() - startTick) / 20.0;
        double[] p = plan(t);
        phase = (int) p[2];
        heading += p[1] * 0.05;
        // like a rider: this tick's speed and heading from wherever the horse is (held back or not); vertical by
        // falling and stepping up like a horse
        // a horse straining at its load goes slower whatever the rider asks (its speed attribute, as in game)
        var speedAttr = horse.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        double willing = speedAttr == null || speedAttr.getBaseValue() <= 0 ? 1
                : speedAttr.getValue() / speedAttr.getBaseValue();
        double dx = -Math.sin(heading) * p[0] * willing * 0.05;
        double dz = Math.cos(heading) * p[0] * willing * 0.05;
        vy = horse.onGround() ? -0.2 : Math.max(-2, vy - 0.08);
        horse.setDeltaMovement(Vec3.ZERO);
        horse.move(MoverType.SELF, new Vec3(dx, vy, dz));
        float yaw = (float) Math.toDegrees(heading);
        horse.setYRot(yaw);
        horse.setYBodyRot(yaw);
        horse.setYHeadRot(yaw);
        px = horse.getX();
        pz = horse.getZ();
    }

    private void finish() {
        if (harness == null) {
            return; // setup failed and said why
        }
        DRIVEN.remove(horse);
        harness.setProbe(null);
        Vector3d g = DimensionPhysicsData.getGravity(level, new Vector3d(horse.getX(), horse.getY(), horse.getZ()));
        double weight = mass * g.length();
        String head = String.format(Locale.ROOT, "%s: %s %.0f b/s turn %.0f deg/s | wagon mass %.1f weight %.0f | %d"
                        + " steps (%d a tick)%s", sc.name(), sc.surface(), sc.speed(), sc.yawRate(), mass, weight,
                samples.size(), substeps, sc.traces() == null ? "" : String.format(Locale.ROOT,
                        " | SWEEP stiffness %.1f Hz damping %.1f smoothing %.1f Hz (report only)", sc.traces()[0],
                        sc.traces()[1], sc.traces()[2]));
        List<String> failures = new ArrayList<>();
        TowMetrics.Result r = null;
        if (samples.size() < 40) {
            failures.add("only " + samples.size() + " physics steps recorded");
        } else {
            r = TowMetrics.measure(samples, substeps, 0.02 * weight);
            check(r, Limits.of(sc), sc, failures);
        }
        int cx = (int) Math.floor(wagon.logicalPose().position().x()) >> 4;
        int cz = (int) Math.floor(wagon.logicalPose().position().z()) >> 4;
        var handle = SubLevelPhysicsSystem.require(level).getPhysicsHandle(wagon);
        LOG.info("[harness-diag] {} course {} chunk {},{} loadedEnough={} handleValid={} inContainer={} removed={}",
                sc.name(), origin, cx, cz,
                dev.ryanhcode.sable.sublevel.system.ticket.PhysicsChunkTicketManager.isChunkLoadedEnough(level, cx, cz),
                handle != null && handle.isValid(),
                SubLevelContainer.getContainer(level).getAllSubLevels().contains(wagon), wagon.isRemoved());
        String line = head + "\n    " + (r == null ? "-" : r.line());
        LOG.info("[harness-metrics] {}", line.replace("\n    ", " || "));
        write(line, r, failures);
        horse.discard();
        SubLevelContainer.getContainer(level).removeSubLevel(wagon, SubLevelRemovalReason.REMOVED);
        if (!failures.isEmpty() && sc.traces() == null && !Boolean.getBoolean("ringbearer_harness.metricsOnly")) {
            helper.fail(String.join("; ", failures) + " -- " + line.replace("\n    ", " | "));
            return;
        }
        helper.succeed();
    }

    private static void check(TowMetrics.Result r, Limits l, Scenario sc, List<String> f) {
        // the horse itself must not be yanked by the harness: at most twice the acceleration its rider asks for
        // (the speed-up's peak is 1.5 x speed / its duration), plus 3 blocks/s²
        double asked = 1.5 * sc.speed() / ACCEL;
        if (r.animalAccelPeak() > 2 * asked + 3) {
            f.add(String.format(Locale.ROOT, "horse yanked: %.1f blocks/s² (asked %.1f)", r.animalAccelPeak(), asked));
        }
        if (r.distance() < l.minDistance()) {
            f.add(String.format(Locale.ROOT, "wagon moved only %.1f blocks", r.distance()));
        }
        if (r.stretchMax() > l.stretchMax()) {
            f.add(String.format(Locale.ROOT, "too stretchy: %.2f > %.2f", r.stretchMax(), l.stretchMax()));
        }
        if (r.vibration() > l.vibration()) {
            f.add(String.format(Locale.ROOT, "vibrates: %.3f > %.3f", r.vibration(), l.vibration()));
        }
        if (r.shakeHz() > l.shakeHz()) {
            f.add(String.format(Locale.ROOT, "shakes at %.1f Hz", r.shakeHz()));
        }
        if (r.chatterPerS() > l.chatterPerS()) {
            f.add(String.format(Locale.ROOT, "pull/push chatter %.2f/s", r.chatterPerS()));
        }
        if (r.wobbleRms() > l.wobbleRms()) {
            f.add(String.format(Locale.ROOT, "wobbles: %.2f > %.2f deg/s", r.wobbleRms(), l.wobbleRms()));
        }
        if (r.accelPeak() > r.animalAccelPeak() * l.accelOverAnimal() + 0.5) {
            f.add(String.format(Locale.ROOT, "kicks: peak accel %.2f vs horse %.2f", r.accelPeak(),
                    r.animalAccelPeak()));
        }
        if (r.jerkRms() > l.jerkRms()) {
            f.add(String.format(Locale.ROOT, "jerky: %.1f > %.1f", r.jerkRms(), l.jerkRms()));
        }
        if (r.settleS() > l.settleS() || r.rings() > l.rings()) {
            f.add(String.format(Locale.ROOT, "springy stop: settles in %.2fs, %d swings", r.settleS(), r.rings()));
        }
        if (r.traceAngleMax() > l.traceAngleMax()) {
            f.add(String.format(Locale.ROOT, "traces at %.0f deg to the wagon", r.traceAngleMax()));
        }
        if (r.microsMean() > l.microsMean()) {
            f.add(String.format(Locale.ROOT, "costs %.1f us a step", r.microsMean()));
        }
    }

    private void write(String line, TowMetrics.Result r, List<String> failures) {
        try {
            Path dir = Path.of("harness-metrics");
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("summary.txt"), line + (failures.isEmpty() ? "\n    PASS\n"
                            : "\n    FAIL: " + String.join("; ", failures) + "\n"),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            StringBuilder csv = new StringBuilder("t,phase,stretch,force,saturated,speed,animal_speed,pitch,roll,"
                    + "pitch_rate,yaw_rate,roll_rate,trace_angle,micros,wagon_x,wagon_y,wagon_z,tow_x,tow_y,tow_z,"
                    + "horse_x,horse_z,path_x,path_z\n");
            for (int i = 0; i < samples.size(); i++) {
                TowMetrics.Sample s = samples.get(i);
                double[] q = positions.get(i);
                csv.append(String.format(Locale.ROOT, "%.4f,%d,%.4f,%.2f,%d,%.4f,%.4f,%.3f,%.3f,%.3f,%.3f,%.3f,%.2f,"
                                + "%.2f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f%n", s.t(), s.phase(),
                        s.stretch(), s.force(), s.saturated() ? 1 : 0, s.speed(), s.animalSpeed(), s.pitch(),
                        s.roll(), s.pitchRate(), s.yawRate(), s.rollRate(), s.traceAngle(), s.micros(), q[0], q[1],
                        q[2], q[3], q[4], q[5], q[6], q[7], q[8], q[9]));
            }
            Files.writeString(dir.resolve(sc.name() + ".csv"), csv);
        } catch (IOException e) {
            LOG.warn("couldn't write harness metrics", e);
        }
    }
}
