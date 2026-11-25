package com.porcupinestudios.ringbearer.harness.draught;

import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;

/**
 * How a team's leader goes when driven on the reins: {@code data/<ns>/driving/<name>.json} (reload listener). A file
 * with no {@code entities} is the default for every kind no file names. Speeds in blocks/s.
 *
 * @param trot forward held
 * @param canter forward with sprint
 * @param reverse back held
 * @param accel how fast it speeds up (blocks/s²); it slows twice as fast
 * @param turn how fast it turns (degrees/s; half that standing)
 */
public record Driving(List<ResourceLocation> entities, double trot, double canter, double reverse, double accel,
                      double turn) {
    public static final Codec<Driving> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.listOf().optionalFieldOf("entities", List.of()).forGetter(Driving::entities),
            Codec.DOUBLE.optionalFieldOf("trot", 4.0).forGetter(Driving::trot),
            Codec.DOUBLE.optionalFieldOf("canter", 8.0).forGetter(Driving::canter),
            Codec.DOUBLE.optionalFieldOf("reverse", 1.0).forGetter(Driving::reverse),
            Codec.DOUBLE.optionalFieldOf("accel", 4.0).forGetter(Driving::accel),
            Codec.DOUBLE.optionalFieldOf("turn", 50.0).forGetter(Driving::turn)
    ).apply(i, Driving::new));
    public static final Driving DEFAULT = new Driving(List.of(), 4, 8, 1, 4, 50);

    /** The loaded files, by kind ({@link DraughtKeys}). */
    public static final class Data extends SimpleJsonResourceReloadListener {
        private static final Logger LOG = LogUtils.getLogger();
        private static volatile Map<ResourceLocation, Driving> byKind = Map.of();
        private static volatile Driving fallback = DEFAULT;

        public Data() {
            super(new com.google.gson.Gson(), "driving");
        }

        public static Driving of(Entity e) {
            for (ResourceLocation kind : DraughtKeys.of(e)) {
                Driving d = byKind.get(kind);
                if (d != null) {
                    return d;
                }
            }
            return fallback;
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller p) {
            Map<ResourceLocation, Driving> out = new HashMap<>();
            Driving[] def = {DEFAULT};
            files.forEach((id, json) -> CODEC.parse(JsonOps.INSTANCE, json)
                    .resultOrPartial(err -> LOG.error("driving {}: {}", id, err))
                    .ifPresent(d -> {
                        if (d.entities().isEmpty()) {
                            def[0] = d;
                        }
                        d.entities().forEach(k -> out.put(k, d));
                    }));
            byKind = Map.copyOf(out);
            fallback = def[0];
        }
    }
}
