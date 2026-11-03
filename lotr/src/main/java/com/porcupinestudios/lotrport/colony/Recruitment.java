package com.porcupinestudios.lotrport.colony;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;

/**
 * An open recruitment quest: which envoy, who took it, the stage reached and progress per objective of that stage.
 * Immutable.
 */
public record Recruitment(UUID envoy, UUID recruiter, int stage, List<Integer> progress, long started) {
    public static final Codec<Recruitment> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("envoy").forGetter(Recruitment::envoy),
            UUIDUtil.CODEC.fieldOf("recruiter").forGetter(Recruitment::recruiter),
            Codec.INT.optionalFieldOf("stage", 0).forGetter(Recruitment::stage),
            Codec.INT.listOf().optionalFieldOf("progress", List.of()).forGetter(Recruitment::progress),
            Codec.LONG.optionalFieldOf("started", 0L).forGetter(Recruitment::started))
            .apply(i, Recruitment::new));

    public int progress(int objective) {
        return objective < progress.size() ? progress.get(objective) : 0;
    }

    public Recruitment withProgress(int objective, int value) {
        List<Integer> copy = new ArrayList<>(progress);
        while (copy.size() <= objective) {
            copy.add(0);
        }
        copy.set(objective, value);
        return new Recruitment(envoy, recruiter, stage, List.copyOf(copy), started);
    }

    public Recruitment nextStage() {
        return new Recruitment(envoy, recruiter, stage + 1, List.of(), started);
    }
}
