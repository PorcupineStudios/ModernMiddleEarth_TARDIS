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
import com.porcupinestudios.ringbearer.harness.draught.DraughtKeys;
import net.minecraft.world.entity.Entity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
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
     * @param body for animals not drawn by vanilla's horse renderer (which wear {@link HarnessModel}): the torso the
     *             harness straps round, in blocks in the animal's frame, {@code [width, bottom, top, rear, front]}
     *             (heights over its feet; rear and front along its facing, from its position). Empty: not drawn
     * @param collar optional padded collar round the neck's base: {@code [height, forward, width, tall, thick]}
     * @param pieces the harness on the animal's own model parts (girth on the body, collar on the neck...), drawn in
     *               each part's frame where the animal's mod says where its parts are ({@link PartPoses}), so they
     *               follow its animation; {@code body} and {@code collar} are the fallback
     */
    public record Fit(List<ResourceLocation> entities, List<Double> ropeAnchor, int ropeColor, List<Double> body,
                      List<Double> collar, List<Piece> pieces) {
        static final Codec<Fit> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.listOf().fieldOf("entities").forGetter(Fit::entities),
                Codec.DOUBLE.listOf(3, 3).fieldOf("rope_anchor").forGetter(Fit::ropeAnchor),
                Codec.STRING.xmap(s -> Integer.parseInt(s.replace("#", ""), 16), c -> String.format("#%06x", c))
                        .optionalFieldOf("rope_color", 0x5b3f26).forGetter(Fit::ropeColor),
                Codec.DOUBLE.listOf(5, 5).optionalFieldOf("body", List.of()).forGetter(Fit::body),
                Codec.DOUBLE.listOf(5, 5).optionalFieldOf("collar", List.of()).forGetter(Fit::collar),
                Piece.CODEC.listOf().optionalFieldOf("pieces", List.of()).forGetter(Fit::pieces)
        ).apply(i, Fit::new));
    }

    /**
     * One box of harness on a model part.
     *
     * @param part the part's name in the animal's model
     * @param material leather, brass or pad (the harness texture's squares)
     * @param box {@code [x0, y0, z0, x1, y1, z1]} in the part's frame, model pixels (y down, -z forward)
     */
    public record Piece(String part, String material, List<Double> box) {
        static final Codec<Piece> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("part").forGetter(Piece::part),
                Codec.STRING.optionalFieldOf("material", "leather").forGetter(Piece::material),
                Codec.DOUBLE.listOf(6, 6).fieldOf("box").forGetter(Piece::box)
        ).apply(i, Piece::new));
    }

    /** For an animal with no fit file: ropes to its sides at about horse height. */
    public static final Fit DEFAULT = new Fit(List.of(), List.of(0.4, 1.1, 0.2), 0x5b3f26, List.of(), List.of(),
            List.of());
    private static volatile Map<ResourceLocation, Fit> byKind = Map.of();

    public HarnessFits() {
        super(new Gson(), "harness_fit");
    }

    /** Whether a fit file names this animal's kind (the client's stand-in for the server's draught data). */
    public static boolean has(Entity e) {
        return DraughtKeys.of(e).stream().anyMatch(byKind::containsKey);
    }

    /** The fit for this animal's kind ({@link DraughtKeys}), else the default. */
    public static Fit of(Entity e) {
        for (ResourceLocation kind : DraughtKeys.of(e)) {
            Fit f = byKind.get(kind);
            if (f != null) {
                return f;
            }
        }
        return DEFAULT;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, Fit> out = new HashMap<>();
        files.forEach((id, json) -> Fit.CODEC.parse(JsonOps.INSTANCE, json)
                .resultOrPartial(err -> LOG.error("harness_fit {}: {}", id, err))
                .ifPresent(f -> f.entities().forEach(kind -> out.put(kind, f))));
        byKind = Map.copyOf(out);
    }
}
