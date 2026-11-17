package com.porcupinestudios.lotrport.colony.tech;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.ringbearer.api.condition.Condition;
import com.porcupinestudios.ringbearer.api.condition.Conditions;
import com.porcupinestudios.ringbearer.api.data.CodecJsonLoader;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/**
 * A colony milestone, {@code data/<ns>/colony_milestone/<id>.json} (reloads): when its condition holds ("home" names
 * the colony; any shared condition, e.g. {@code lotr_port:colony_population}) the colony reaches it once, the ruler is
 * told, and it pays its rewards: coins into the treasury and research points.
 *
 * @param techs colony techs the milestone needs (all), besides the condition
 * @param buildings building types the colony must have at least one of each
 */
public record MilestoneDef(String name, String description, int order, Optional<Condition> condition,
        java.util.List<ResourceLocation> techs, java.util.List<ResourceLocation> buildings, long coins,
        Map<ResourceLocation, Double> research) {
    public static final Codec<MilestoneDef> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("name", "").forGetter(MilestoneDef::name),
            Codec.STRING.optionalFieldOf("description", "").forGetter(MilestoneDef::description),
            Codec.INT.optionalFieldOf("order", 0).forGetter(MilestoneDef::order),
            Conditions.CODEC.optionalFieldOf("condition").forGetter(MilestoneDef::condition),
            ResourceLocation.CODEC.listOf().optionalFieldOf("techs", java.util.List.of()).forGetter(MilestoneDef::techs),
            ResourceLocation.CODEC.listOf().optionalFieldOf("buildings", java.util.List.of())
                    .forGetter(MilestoneDef::buildings),
            Codec.LONG.optionalFieldOf("coins", 0L).forGetter(MilestoneDef::coins),
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.DOUBLE).optionalFieldOf("research", Map.of())
                    .forGetter(MilestoneDef::research))
            .apply(i, MilestoneDef::new));

    private static final CodecJsonLoader<MilestoneDef> LOADER = new CodecJsonLoader<>("colony_milestone", CODEC);

    public static void register() {
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent e) -> e.addListener(LOADER));
    }

    public static Map<ResourceLocation, MilestoneDef> all() {
        return LOADER.entries();
    }

    public String title(ResourceLocation id) {
        return name.isEmpty() ? id.getPath().replace('_', ' ') : name;
    }
}
