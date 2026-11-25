package com.porcupinestudios.lotrport.road;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/**
 * One step on the road ladder ({@code data/<ns>/road_tier/<id>.json}, reloads): what a stretch of road at this tier
 * lays and how it counts for travel. Tiers are ordered by {@code rank}. How a stretch climbs to it: {@code built_by}
 * {@code traffic} (caravans and travellers wear it in, once the stretch's wear reaches {@code wear}) or
 * {@code waymakers} (only colonists working to a Waymaker plan build it; not wired yet, see lotr/docs/roads.md).
 *
 * <p>{@code surface}: weighted blocks laid on the ground ({@code biome_style} true: the biome's own LOTR road blocks
 * instead, from {@code roads.json}); {@code coverage}: the share of columns laid (a trail is patchy);
 * {@code half_width}: blocks either side of the line; {@code route_road}: routes plan along it as along a road.
 */
public record RoadTier(int rank, String builtBy, long wear, List<Surface> surface, boolean biomeStyle,
        double coverage, double halfWidth, boolean routeRoad) {
    public static final String TRAFFIC = "traffic";
    public static final String WAYMAKERS = "waymakers";

    public record Surface(ResourceLocation block, double weight) {
        public static final Codec<Surface> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("block").forGetter(Surface::block),
                Codec.DOUBLE.optionalFieldOf("weight", 1.0).forGetter(Surface::weight))
                .apply(i, Surface::new));
    }

    public static final Codec<RoadTier> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("rank").forGetter(RoadTier::rank),
            Codec.STRING.optionalFieldOf("built_by", TRAFFIC).forGetter(RoadTier::builtBy),
            Codec.LONG.optionalFieldOf("wear", 0L).forGetter(RoadTier::wear),
            Surface.CODEC.listOf().optionalFieldOf("surface", List.of()).forGetter(RoadTier::surface),
            Codec.BOOL.optionalFieldOf("biome_style", false).forGetter(RoadTier::biomeStyle),
            Codec.doubleRange(0, 1).optionalFieldOf("coverage", 1.0).forGetter(RoadTier::coverage),
            Codec.doubleRange(0.5, 8).optionalFieldOf("half_width", 2.0).forGetter(RoadTier::halfWidth),
            Codec.BOOL.optionalFieldOf("route_road", true).forGetter(RoadTier::routeRoad))
            .apply(i, RoadTier::new));

    public boolean byTraffic() {
        return TRAFFIC.equals(builtBy);
    }
}
