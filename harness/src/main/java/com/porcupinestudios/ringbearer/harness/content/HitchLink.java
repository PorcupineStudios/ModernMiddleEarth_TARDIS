package com.porcupinestudios.ringbearer.harness.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.ringbearer.harness.HarnessMod;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;

/**
 * On a hitched animal: where its harness is (the block's position in its vehicle's plot, which is a real position in
 * the level). Lets the animal's tick find the harness that holds it back. Saved with the animal.
 */
public record HitchLink(int dataVersion, BlockPos harness) {
    public static final int DATA_VERSION = 1;
    public static final Codec<HitchLink> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("data_version", DATA_VERSION).forGetter(HitchLink::dataVersion),
            BlockPos.CODEC.fieldOf("harness").forGetter(HitchLink::harness)
    ).apply(i, HitchLink::new));

    public static void set(Entity e, BlockPos harness) {
        e.setData(HarnessMod.HITCH_LINK, Optional.of(new HitchLink(DATA_VERSION, harness)));
    }

    public static void clear(Entity e) {
        e.setData(HarnessMod.HITCH_LINK, Optional.empty());
    }

    public static Optional<HitchLink> of(Entity e) {
        return e.getData(HarnessMod.HITCH_LINK);
    }
}
