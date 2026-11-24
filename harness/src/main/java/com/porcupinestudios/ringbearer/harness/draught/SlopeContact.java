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
 * @param cushion on a vehicle that carries these slopes (a sled): how high its deck rides on a soft cushion over the
 *                ground (blocks; 0: none, it rests on Sable's rigid contact), so drops and landings are taken by the
 *                same spring-damper instead of a hard knock
 */
public record SlopeContact(List<ResourceLocation> blocks, double stiffnessHz, double dampingRatio, double friction,
                           double cushion) {
    public static final Codec<SlopeContact> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.listOf().fieldOf("blocks").forGetter(SlopeContact::blocks),
            Codec.DOUBLE.optionalFieldOf("stiffness_hz", 8.0).forGetter(SlopeContact::stiffnessHz),
            Codec.DOUBLE.optionalFieldOf("damping_ratio", 0.9).forGetter(SlopeContact::dampingRatio),
            Codec.DOUBLE.optionalFieldOf("friction", 0.3).forGetter(SlopeContact::friction),
            Codec.DOUBLE.optionalFieldOf("cushion", 0.0).forGetter(SlopeContact::cushion)
    ).apply(i, SlopeContact::new));

    public static final SlopeContact DEFAULT = new SlopeContact(List.of(), 8.0, 0.9, 0.3, 0.0);
}
