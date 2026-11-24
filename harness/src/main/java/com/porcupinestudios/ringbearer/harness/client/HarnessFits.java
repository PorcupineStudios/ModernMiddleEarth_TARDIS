package com.porcupinestudios.ringbearer.harness.client;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import org.slf4j.Logger;

/**
 * How a harness sits on each kind of animal, for drawing: {@code assets/<ns>/harness_fit/<name>.json} (resource packs,
 * F3+T). The physics side is the server's draught data.
 */
public final class HarnessFits extends SimpleJsonResourceReloadListener {
    private static final Logger LOG = LogUtils.getLogger();

    /**
     * @param entities entity types it fits
     * @param ropeAnchor where each rope ends on the animal: blocks to its side (mirrored for the other rope), up and
     *                   forward of its position, turned with its body (the harness's tug rings on a horse)
     * @param ropeColor the ropes' colour, as 0xRRGGBB
     */
    public record Fit(List<ResourceLocation> entities, List<Double> ropeAnchor, int ropeColor) {
        static final Codec<Fit> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.listOf().fieldOf("entities").forGetter(Fit::entities),
                Codec.DOUBLE.listOf(3, 3).fieldOf("rope_anchor").forGetter(Fit::ropeAnchor),
                Codec.STRING.xmap(s -> Integer.parseInt(s.replace("#", ""), 16), c -> String.format("#%06x", c))
                        .optionalFieldOf("rope_color", 0x5b3f26).forGetter(Fit::ropeColor)
        ).apply(i, Fit::new));
    }

    /** For an animal with no fit file: ropes to its sides at about horse height. */
    public static final Fit DEFAULT = new Fit(List.of(), List.of(0.4, 1.1, 0.2), 0x5b3f26);
    private static volatile Map<EntityType<?>, Fit> byType = Map.of();

    public HarnessFits() {
        super(new Gson(), "harness_fit");
    }

    public static Fit of(EntityType<?> type) {
        Fit f = byType.get(type);
        return f == null ? DEFAULT : f;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        Map<EntityType<?>, Fit> out = new HashMap<>();
        files.forEach((id, json) -> Fit.CODEC.parse(JsonOps.INSTANCE, json)
                .resultOrPartial(err -> LOG.error("harness_fit {}: {}", id, err))
                .ifPresent(f -> f.entities().forEach(t -> BuiltInRegistries.ENTITY_TYPE.getOptional(t)
                        .ifPresent(type -> out.put(type, f)))));
        byType = Map.copyOf(out);
    }
}
