package com.porcupinestudios.lotrport.faction;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * A great power from data: {@code data/<namespace>/faction/<name>.json}, reloaded with {@code /reload}. Names and rank
 * names are lang keys: {@code faction.<namespace>.<name>} and {@code faction.<namespace>.<name>.rank.<rank>}.
 *
 * @param color map and chat colour (RGB)
 * @param region the part of the map it belongs to (west, rhun, harad...), for grouping in screens
 * @param types kinds of people (free, elf, man, dwarf, orc, troll, tree)
 * @param playable whether players can earn alignment with it
 * @param fixedAlignment for factions like "hostile" whose alignment never changes
 * @param ranks titles by alignment, any order
 * @param pledgeRank the rank a player needs to pledge
 * @param map where it is on the map: pixel x, z and radius
 * @param controlZones where its alignment is earned at full rate (used from phase 5)
 * @param relations default relations with other factions; unlisted pairs are neutral (either side can list it)
 * @param killValue alignment a kill of one of its members moves
 */
public record FactionDef(int color, String region, List<String> types, boolean playable,
        Optional<Float> fixedAlignment, boolean isolationist, boolean approvesWarCrimes, List<Rank> ranks,
        Optional<String> pledgeRank, Optional<MapArea> map, List<MapArea> controlZones,
        Map<ResourceLocation, Relation> relations, float killValue) {

    public record Rank(String id, float alignment, boolean gendered) {
        public static final Codec<Rank> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("id").forGetter(Rank::id),
                Codec.FLOAT.fieldOf("alignment").forGetter(Rank::alignment),
                Codec.BOOL.optionalFieldOf("gendered", false).forGetter(Rank::gendered))
                .apply(i, Rank::new));
    }

    /** A circle on the map, in map pixels. */
    public record MapArea(double x, double z, int radius) {
        public static final Codec<MapArea> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.fieldOf("x").forGetter(MapArea::x),
                Codec.DOUBLE.fieldOf("z").forGetter(MapArea::z),
                Codec.INT.fieldOf("radius").forGetter(MapArea::radius))
                .apply(i, MapArea::new));
    }

    public static final Codec<FactionDef> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("color", 0xFFFFFF).forGetter(FactionDef::color),
            Codec.STRING.optionalFieldOf("region", "none").forGetter(FactionDef::region),
            Codec.STRING.listOf().optionalFieldOf("types", List.of()).forGetter(FactionDef::types),
            Codec.BOOL.optionalFieldOf("playable", true).forGetter(FactionDef::playable),
            Codec.FLOAT.optionalFieldOf("fixed_alignment").forGetter(FactionDef::fixedAlignment),
            Codec.BOOL.optionalFieldOf("isolationist", false).forGetter(FactionDef::isolationist),
            Codec.BOOL.optionalFieldOf("approves_war_crimes", false).forGetter(FactionDef::approvesWarCrimes),
            Rank.CODEC.listOf().optionalFieldOf("ranks", List.of()).forGetter(FactionDef::ranks),
            Codec.STRING.optionalFieldOf("pledge_rank").forGetter(FactionDef::pledgeRank),
            MapArea.CODEC.optionalFieldOf("map").forGetter(FactionDef::map),
            MapArea.CODEC.listOf().optionalFieldOf("control_zones", List.of()).forGetter(FactionDef::controlZones),
            Codec.unboundedMap(ResourceLocation.CODEC, Relation.CODEC).optionalFieldOf("relations", Map.of())
                    .forGetter(FactionDef::relations),
            Codec.FLOAT.optionalFieldOf("kill_value", 1f).forGetter(FactionDef::killValue))
            .apply(i, FactionDef::new));
}
