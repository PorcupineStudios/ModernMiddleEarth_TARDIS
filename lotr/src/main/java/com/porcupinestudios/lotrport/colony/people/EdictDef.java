package com.porcupinestudios.lotrport.colony.people;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.ringbearer.api.data.CodecJsonLoader;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/**
 * An edict the Council may proclaim, {@code data/<ns>/colony_edict/<id>.json} (reloads; RingRealms' edicts). While
 * in force it changes the colony's work and happiness, how often citizens quarrel and unity, and costs its upkeep
 * from the treasury every day (lapsing when the treasury can't pay).
 *
 * @param work multiplies every worker's output
 * @param happiness added to every citizen's happiness target
 * @param unity added to the colony's unity target
 * @param quarrels multiplies the chance of quarrels
 * @param upkeep coins a day
 * @param minPopulation citizens the colony needs to proclaim it
 * @param requires a colony tech ({@code colony_tech/<id>}) that must be researched first, if any
 * @param excludes other edicts that can't be in force with this one
 */
public record EdictDef(String name, String description, double work, double happiness, double unity, double quarrels,
        int upkeep, int minPopulation, Optional<ResourceLocation> requires, java.util.List<ResourceLocation> excludes) {
    public static final Codec<EdictDef> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("name", "").forGetter(EdictDef::name),
            Codec.STRING.optionalFieldOf("description", "").forGetter(EdictDef::description),
            Codec.DOUBLE.optionalFieldOf("work", 1.0).forGetter(EdictDef::work),
            Codec.DOUBLE.optionalFieldOf("happiness", 0.0).forGetter(EdictDef::happiness),
            Codec.DOUBLE.optionalFieldOf("unity", 0.0).forGetter(EdictDef::unity),
            Codec.DOUBLE.optionalFieldOf("quarrels", 1.0).forGetter(EdictDef::quarrels),
            Codec.INT.optionalFieldOf("upkeep", 0).forGetter(EdictDef::upkeep),
            Codec.INT.optionalFieldOf("min_population", 0).forGetter(EdictDef::minPopulation),
            ResourceLocation.CODEC.optionalFieldOf("requires").forGetter(EdictDef::requires),
            ResourceLocation.CODEC.listOf().optionalFieldOf("excludes", java.util.List.of()).forGetter(EdictDef::excludes))
            .apply(i, EdictDef::new));

    private static final CodecJsonLoader<EdictDef> LOADER = new CodecJsonLoader<>("colony_edict", CODEC);

    public static void register() {
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent e) -> e.addListener(LOADER));
    }

    public static Map<ResourceLocation, EdictDef> all() {
        return LOADER.entries();
    }

    public static Optional<EdictDef> get(ResourceLocation id) {
        return Optional.ofNullable(LOADER.entries().get(id));
    }

    public String title(ResourceLocation id) {
        return name.isEmpty() ? id.getPath().replace('_', ' ') : name;
    }
}
