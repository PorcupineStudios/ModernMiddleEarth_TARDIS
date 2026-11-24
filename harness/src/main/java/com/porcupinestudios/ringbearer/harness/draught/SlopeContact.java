package com.porcupinestudios.ringbearer.harness.draught;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/**
 * How vehicles touch slope blocks: {@code data/<ns>/slope_contact/<name>.json}.
 *
 * @param stiffnessHz the contact spring's natural frequency against the vehicle's mass at the contact (it sinks
 *                    g / (2 pi f)^2 under its own weight: a few millimetres at 8 Hz)
 * @param dampingRatio the contact's damping (1: no bounce)
 * @param friction sliding friction along the slope, a share of the push (sleds slide; higher grips)
 */
public record SlopeContact(List<ResourceLocation> blocks, double stiffnessHz, double dampingRatio, double friction) {
    public static final Codec<SlopeContact> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.listOf().fieldOf("blocks").forGetter(SlopeContact::blocks),
            Codec.DOUBLE.optionalFieldOf("stiffness_hz", 8.0).forGetter(SlopeContact::stiffnessHz),
            Codec.DOUBLE.optionalFieldOf("damping_ratio", 0.9).forGetter(SlopeContact::dampingRatio),
            Codec.DOUBLE.optionalFieldOf("friction", 0.3).forGetter(SlopeContact::friction)
    ).apply(i, SlopeContact::new));

    public static final SlopeContact DEFAULT = new SlopeContact(List.of(), 8.0, 0.9, 0.3);
}
