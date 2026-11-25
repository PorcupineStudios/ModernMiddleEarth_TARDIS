package com.porcupinestudios.ringbearer.harness.draught;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/**
 * A wheel block's behaviour: {@code data/<ns>/wheel/<name>.json}. Lengths in blocks.
 *
 * @param radius the drawn wheel's radius: on flat ground its centre rests this high
 * @param contactReach how far ahead and behind (half) the wheel feels the ground: the probe is an ellipse this long,
 *                     so a step is met early and climbed gently (an ellipse reaching 3, 1.5 high, climbs a 1-block step
 *                     at under 1.5 per block; a round wheel can't)
 * @param contactHeight the ellipse's half height; above the step heights it must climb smoothly
 * @param travel how far the hub can drop below its block's centre before the wheel leaves the ground
 * @param maxCompression how far above it the hub can be pushed (more is a wall: the chassis meets it)
 * @param suspensionHz the suspension's natural frequency against the vehicle's mass at the wheel
 * @param dampingRatio the suspension's damping (1: no bounce)
 * @param lateralGrip share of sideways sliding taken out per physics step (0..1)
 * @param friction most sideways grip as a share of the load (Coulomb)
 * @param rollingResistance drag along the rolling direction as a share of the load
 * @param maxSteer most a wheel turns when a hitch steers it (degrees)
 */
public record WheelType(List<ResourceLocation> blocks, double radius, double contactReach, double contactHeight,
                        double travel, double maxCompression, double suspensionHz, double dampingRatio,
                        double lateralGrip, double friction, double rollingResistance, double maxSteer) {
    public static final Codec<WheelType> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.listOf().fieldOf("blocks").forGetter(WheelType::blocks),
            Codec.DOUBLE.optionalFieldOf("radius", 0.9).forGetter(WheelType::radius),
            Codec.DOUBLE.optionalFieldOf("contact_reach", 3.0).forGetter(WheelType::contactReach),
            Codec.DOUBLE.optionalFieldOf("contact_height", 1.5).forGetter(WheelType::contactHeight),
            Codec.DOUBLE.optionalFieldOf("travel", 0.35).forGetter(WheelType::travel),
            Codec.DOUBLE.optionalFieldOf("max_compression", 0.6).forGetter(WheelType::maxCompression),
            Codec.DOUBLE.optionalFieldOf("suspension_hz", 1.5).forGetter(WheelType::suspensionHz),
            Codec.DOUBLE.optionalFieldOf("damping_ratio", 0.8).forGetter(WheelType::dampingRatio),
            Codec.DOUBLE.optionalFieldOf("lateral_grip", 0.5).forGetter(WheelType::lateralGrip),
            Codec.DOUBLE.optionalFieldOf("friction", 0.9).forGetter(WheelType::friction),
            Codec.DOUBLE.optionalFieldOf("rolling_resistance", 0.03).forGetter(WheelType::rollingResistance),
            Codec.DOUBLE.optionalFieldOf("max_steer", 45.0).forGetter(WheelType::maxSteer)
    ).apply(i, WheelType::new));
}
