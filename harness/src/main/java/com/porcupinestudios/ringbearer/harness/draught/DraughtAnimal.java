package com.porcupinestudios.ringbearer.harness.draught;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/**
 * How one kind of animal pulls: {@code data/<ns>/draught/<name>.json}, reloaded with /reload. Forces are in Sable's
 * units (its block masses x blocks/s²); the GameTests log the test wagon's mass and weight to calibrate against.
 *
 * @param entities entity types this applies to (ids of mods that aren't loaded are skipped)
 * @param towPoint where the traces start on the animal: blocks right, up and forward of its position, turned with its
 *                 body (behind the hind legs for a horse)
 * @param minGap shortest rest length of the traces (hitching closer than this snaps to it)
 * @param maxGap longest; hitching further away pulls the animal in to it
 * @param maxPull most force it pulls with
 * @param maxPush most force it holds back with (braking, downhill)
 * @param stiffnessHz the traces' natural frequency (Hz): higher is stiffer, quicker and kickier
 * @param dampingRatio 1 is critically damped (no overshoot); lower swings, higher drags
 * @param smoothingHz how fast the tow point follows the animal's tick-by-tick moves (Hz; 0 = exactly): rounds off
 *                    step-ups and jumps so they don't kick the vehicle
 * @param slack how far past its rest length the animal gets before it is held back
 * @param breakDistance farther than this (a teleport, a fall) and it comes unhitched
 * @param steer how far the vehicle's front wheels turn toward the traces (1: they point along them, like a wagon pole
 *              turning the front axle; 0: they don't steer). Offroad wheels turn at most 30 degrees.
 * @param pushSlack how far closer than its rest length the vehicle can push it before it is shoved forward
 * @param loadSlowing how much a straining animal slows (0..1 of its speed at full strain: pulling at its limit or at
 *                    the end of its slack); smooth, and it works for ridden animals too (speed syncs to the rider)
 * @param maxAngle how far the animal can swing round from straight ahead of the hitch (degrees): the pole stops it, so
 *                 in a turn tighter than the vehicle can follow it walks a wider circle instead of jackknifing
 */
public record DraughtAnimal(List<ResourceLocation> entities, List<Double> towPoint, double minGap, double maxGap,
                            double maxPull, double maxPush, double stiffnessHz, double dampingRatio,
                            double smoothingHz, double slack, double breakDistance, double steer,
                            double maxAngle, double pushSlack, double loadSlowing) {
    public static final Codec<DraughtAnimal> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.listOf().fieldOf("entities").forGetter(DraughtAnimal::entities),
            Codec.DOUBLE.listOf(3, 3).optionalFieldOf("tow_point", List.of(0.0, 0.9, -0.95))
                    .forGetter(DraughtAnimal::towPoint),
            Codec.DOUBLE.optionalFieldOf("min_gap", 0.3).forGetter(DraughtAnimal::minGap),
            Codec.DOUBLE.optionalFieldOf("max_gap", 3.0).forGetter(DraughtAnimal::maxGap),
            Codec.DOUBLE.fieldOf("max_pull").forGetter(DraughtAnimal::maxPull),
            Codec.DOUBLE.fieldOf("max_push").forGetter(DraughtAnimal::maxPush),
            Codec.DOUBLE.optionalFieldOf("stiffness_hz", 1.2).forGetter(DraughtAnimal::stiffnessHz),
            Codec.DOUBLE.optionalFieldOf("damping_ratio", 1.0).forGetter(DraughtAnimal::dampingRatio),
            Codec.DOUBLE.optionalFieldOf("smoothing_hz", 4.0).forGetter(DraughtAnimal::smoothingHz),
            Codec.DOUBLE.optionalFieldOf("slack", 1.0).forGetter(DraughtAnimal::slack),
            Codec.DOUBLE.optionalFieldOf("break_distance", 10.0).forGetter(DraughtAnimal::breakDistance),
            Codec.DOUBLE.optionalFieldOf("steer", 1.0).forGetter(DraughtAnimal::steer),
            Codec.DOUBLE.optionalFieldOf("max_angle", 60.0).forGetter(DraughtAnimal::maxAngle),
            Codec.DOUBLE.optionalFieldOf("push_slack", 0.4).forGetter(DraughtAnimal::pushSlack),
            Codec.DOUBLE.optionalFieldOf("load_slowing", 0.85).forGetter(DraughtAnimal::loadSlowing)
    ).apply(i, DraughtAnimal::new));

    /** A copy with other trace settings (GameTest calibration sweeps). */
    public DraughtAnimal withTraces(double stiffnessHz, double dampingRatio, double smoothingHz) {
        return new DraughtAnimal(entities, towPoint, minGap, maxGap, maxPull, maxPush, stiffnessHz, dampingRatio,
                smoothingHz, slack, breakDistance, steer, maxAngle, pushSlack, loadSlowing);
    }

    public double omega() {
        return 2 * Math.PI * stiffnessHz;
    }

    public double smoothingOmega() {
        return 2 * Math.PI * smoothingHz;
    }
}
