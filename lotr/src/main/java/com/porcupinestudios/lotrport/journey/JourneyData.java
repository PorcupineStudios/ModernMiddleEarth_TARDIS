package com.porcupinestudios.lotrport.journey;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.ringbearer.api.data.CodecJsonLoader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/** Journey data, reloaded with /reload: {@code journey/settings.json}, {@code journey/terrain.json}, {@code journey_steed/}. */
public final class JourneyData {
    private static final JourneySettings D = JourneySettings.DEFAULT;

    private static final JourneySettings.Bodies B = JourneySettings.Bodies.DEFAULT;
    private static final Codec<JourneySettings.Bodies> BODIES_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("show_radius", B.showRadius()).forGetter(JourneySettings.Bodies::showRadius),
            Codec.INT.optionalFieldOf("hide_radius", B.hideRadius()).forGetter(JourneySettings.Bodies::hideRadius),
            Codec.INT.optionalFieldOf("repath_seconds", B.repathSeconds())
                    .forGetter(JourneySettings.Bodies::repathSeconds),
            Codec.INT.optionalFieldOf("hop_seconds", B.hopSeconds()).forGetter(JourneySettings.Bodies::hopSeconds),
            Codec.INT.optionalFieldOf("give_up_seconds", B.giveUpSeconds())
                    .forGetter(JourneySettings.Bodies::giveUpSeconds))
            .apply(i, JourneySettings.Bodies::new));

    public static final Codec<JourneySettings> SETTINGS_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.DOUBLE.optionalFieldOf("walk_speed", D.walkSpeed()).forGetter(JourneySettings::walkSpeed),
            Codec.INT.optionalFieldOf("cell", D.cell()).forGetter(JourneySettings::cell),
            Codec.INT.optionalFieldOf("sample", D.sample()).forGetter(JourneySettings::sample),
            Codec.DOUBLE.optionalFieldOf("road_speed", D.roadSpeed()).forGetter(JourneySettings::roadSpeed),
            Codec.INT.optionalFieldOf("margin", D.margin()).forGetter(JourneySettings::margin),
            Codec.INT.optionalFieldOf("max_nodes", D.maxNodes()).forGetter(JourneySettings::maxNodes),
            Codec.INT.optionalFieldOf("person_carry", D.personCarry()).forGetter(JourneySettings::personCarry),
            Codec.INT.optionalFieldOf("wagon_carry", D.wagonCarry()).forGetter(JourneySettings::wagonCarry),
            Codec.DOUBLE.optionalFieldOf("wagon_speed", D.wagonSpeed()).forGetter(JourneySettings::wagonSpeed),
            BODIES_CODEC.optionalFieldOf("bodies", B).forGetter(JourneySettings::bodies),
            Codec.INT.optionalFieldOf("tick_interval", D.tickInterval()).forGetter(JourneySettings::tickInterval),
            Codec.INT.optionalFieldOf("bridge_max_length", D.bridgeMaxLength())
                    .forGetter(JourneySettings::bridgeMaxLength),
            Codec.INT.optionalFieldOf("steed_radius", D.steedRadius()).forGetter(JourneySettings::steedRadius),
            Codec.DOUBLE.optionalFieldOf("greed", D.greed()).forGetter(JourneySettings::greed))
            .apply(i, JourneySettings::new));

    private static final Codec<TerrainData.Pattern> PATTERN = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("contains").forGetter(TerrainData.Pattern::contains),
            Codec.DOUBLE.fieldOf("speed").forGetter(TerrainData.Pattern::speed))
            .apply(i, TerrainData.Pattern::new));

    public static final Codec<TerrainData> TERRAIN_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.DOUBLE.optionalFieldOf("fallback", 1.0).forGetter(TerrainData::fallback),
            Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).optionalFieldOf("biomes", Map.of())
                    .forGetter(TerrainData::biomes),
            PATTERN.listOf().optionalFieldOf("patterns", List.of()).forGetter(TerrainData::patterns),
            Codec.STRING.listOf().optionalFieldOf("water", TerrainData.DEFAULT.water()).forGetter(TerrainData::water),
            Codec.DOUBLE.optionalFieldOf("roughness", TerrainData.DEFAULT.roughness())
                    .forGetter(TerrainData::roughness))
            .apply(i, TerrainData::new));

    public static final Codec<SteedDef> STEED_CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.listOf().fieldOf("entities").forGetter(SteedDef::entities),
            Codec.DOUBLE.optionalFieldOf("speed", 1.5).forGetter(SteedDef::speed),
            Codec.INT.optionalFieldOf("carry", 3).forGetter(SteedDef::carry),
            Codec.BOOL.optionalFieldOf("draws_wagon", false).forGetter(SteedDef::drawsWagon),
            Codec.BOOL.optionalFieldOf("needs_tame", true).forGetter(SteedDef::needsTame))
            .apply(i, SteedDef::new));

    private static final ResourceLocation SETTINGS = LotrPort.id("settings");
    private static final ResourceLocation TERRAIN = LotrPort.id("terrain");
    private static final CodecJsonLoader<JourneySettings> SETTINGS_LOADER =
            new CodecJsonLoader<>("journey", SETTINGS_CODEC);
    private static final CodecJsonLoader<TerrainData> TERRAIN_LOADER = new CodecJsonLoader<>("journey", TERRAIN_CODEC);
    private static final CodecJsonLoader<SteedDef> STEEDS = new CodecJsonLoader<>("journey_steed", STEED_CODEC);

    private JourneyData() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent e) -> {
            e.addListener(SETTINGS_LOADER);
            e.addListener(TERRAIN_LOADER);
            e.addListener(STEEDS);
        });
    }

    public static JourneySettings settings() {
        return SETTINGS_LOADER.entries().getOrDefault(SETTINGS, D);
    }

    public static TerrainData terrain() {
        return TERRAIN_LOADER.entries().getOrDefault(TERRAIN, TerrainData.DEFAULT);
    }

    public static Map<ResourceLocation, SteedDef> steeds() {
        return STEEDS.entries();
    }

    /** The steed an entity type counts as, if any. */
    public static Optional<Map.Entry<ResourceLocation, SteedDef>> steedOf(EntityType<?> type) {
        ResourceLocation id = EntityType.getKey(type);
        List<Map.Entry<ResourceLocation, SteedDef>> all = new ArrayList<>(steeds().entrySet());
        all.sort(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)));
        for (Map.Entry<ResourceLocation, SteedDef> e : all) {
            if (e.getValue().entities().contains(id)) {
                return Optional.of(e);
            }
        }
        return Optional.empty();
    }

    public static List<String> loadProblems() {
        List<String> out = new ArrayList<>(SETTINGS_LOADER.lastErrors());
        out.addAll(STEEDS.lastErrors());
        return out;
    }
}
