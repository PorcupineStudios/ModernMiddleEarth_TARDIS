package com.porcupinestudios.ringbearer.harness.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.ringbearer.harness.HarnessMod;
import com.porcupinestudios.ringbearer.harness.draught.DraughtAnimal;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.Entity;

/**
 * On a hitched animal: where its hitch is (the block's position in its vehicle's plot, which is a real position in
 * the level), and the hold-back's limits for its rider's client ({@link #limits}: rest length, tow point x/y/z, slack,
 * push slack, max angle; a rider moves the animal on their client, so that's where it's held back). Saved with the
 * animal and synced to the players who see it. Data version 2 (2026-11-25) added the limits; links from version 1 are
 * renewed by their hitch on its next tick.
 */
public record HitchLink(int dataVersion, BlockPos harness, List<Double> limits) {
    public static final int DATA_VERSION = 2;
    public static final Codec<HitchLink> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("data_version", 1).forGetter(HitchLink::dataVersion),
            BlockPos.CODEC.fieldOf("harness").forGetter(HitchLink::harness),
            Codec.DOUBLE.listOf().optionalFieldOf("limits", List.of()).forGetter(HitchLink::limits)
    ).apply(i, HitchLink::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, HitchLink> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, HitchLink::dataVersion, BlockPos.STREAM_CODEC, HitchLink::harness,
            ByteBufCodecs.DOUBLE.apply(ByteBufCodecs.list(8)), HitchLink::limits, HitchLink::new);

    public static void set(Entity e, BlockPos harness, double rest, DraughtAnimal d) {
        List<Double> tow = d.towPoint();
        e.setData(HarnessMod.HITCH_LINK, Optional.of(new HitchLink(DATA_VERSION, harness,
                List.of(rest, tow.get(0), tow.get(1), tow.get(2), d.slack(), d.pushSlack(), d.maxAngle()))));
    }

    public static void clear(Entity e) {
        e.setData(HarnessMod.HITCH_LINK, Optional.empty());
    }

    public static Optional<HitchLink> of(Entity e) {
        return e.getData(HarnessMod.HITCH_LINK);
    }

    public boolean hasLimits() {
        return limits.size() == 7;
    }
}
