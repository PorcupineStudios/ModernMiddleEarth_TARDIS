package com.porcupinestudios.ringbearer.harness.content;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3dc;

/**
 * Which way each harnessed vehicle's front wheels should point (server thread). The harness sets it every tick from
 * the angle of its traces; Offroad's wheel mounts read it through {@code WheelMountSteeringMixin}. A wheel mount is a
 * front wheel when it sits ahead of the vehicle's centre of mass along the harness's facing.
 */
public final class HarnessSteering {
    /** Offroad: a signal of 15 turns a wheel 30 degrees. */
    public static final double DEGREES_PER_SIGNAL = 2;

    private record Steer(Direction forward, int signal, double degrees) {}

    private static final Map<SubLevel, Steer> STEER = new WeakHashMap<>();

    private HarnessSteering() {}

    /** Steer this vehicle's front wheels toward traces at {@code degrees} from its forward (the trace angle's sign). */
    static void set(SubLevel vehicle, Direction forward, double degrees) {
        int signal = (int) Math.round(Math.max(-15, Math.min(15, degrees / DEGREES_PER_SIGNAL)));
        STEER.put(vehicle, new Steer(forward, signal, degrees));
    }

    static void clear(SubLevel vehicle) {
        STEER.remove(vehicle);
    }

    /** The steering signal for this wheel mount, or null to leave it to redstone. */
    @Nullable
    public static Integer signalFor(BlockEntity wheelMount) {
        Steer steer = frontSteer(wheelMount);
        return steer == null ? null : steer.signal();
    }

    /**
     * How far a front wheel should turn toward the traces (degrees, the trace angle's sense), continuous (wagon
     * wheels), or NaN if no hitch steers it.
     */
    public static double degreesFor(BlockEntity wheel) {
        Steer steer = frontSteer(wheel);
        return steer == null ? Double.NaN : steer.degrees();
    }

    @Nullable
    private static Steer frontSteer(BlockEntity wheelMount) {
        if (STEER.isEmpty() || wheelMount.getLevel() == null || wheelMount.getLevel().isClientSide) {
            return null;
        }
        SubLevel vehicle = Sable.HELPER.getContaining(wheelMount);
        Steer steer = vehicle == null ? null : STEER.get(vehicle);
        if (steer == null || !(vehicle instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel s)) {
            return null;
        }
        Vector3dc com = s.getMassTracker().getCenterOfMass();
        BlockPos p = wheelMount.getBlockPos();
        double ahead = (p.getX() + 0.5 - com.x()) * steer.forward().getStepX()
                + (p.getZ() + 0.5 - com.z()) * steer.forward().getStepZ();
        return ahead > 0 ? steer : null;
    }
}
