package com.porcupinestudios.lotrport.ai;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.ringbearer.api.data.CodecJsonLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/** AI ruler files ({@code ai_ruler/<id>.json}) and the settings file ({@code ai/settings.json}). */
public final class AiData {
    /**
     * One AI ruler: a small realm aligned with {@code faction}, based at the waypoint {@code home}.
     *
     * @param aggression daily chance (0-1) to declare war on a hostile ruler, and to march on an enemy colony
     * @param expansion daily chance (0-1) to found a colony while under {@code max_colonies}
     * @param colonyNames names for its colonies, used in turn
     */
    public record RulerDef(String name, ResourceLocation faction, ResourceLocation home, double aggression,
            double expansion, int maxColonies, List<String> colonyNames, int color) {
        public static final Codec<RulerDef> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("name").forGetter(RulerDef::name),
                ResourceLocation.CODEC.fieldOf("faction").forGetter(RulerDef::faction),
                ResourceLocation.CODEC.fieldOf("home").forGetter(RulerDef::home),
                Codec.doubleRange(0, 1).optionalFieldOf("aggression", 0.2).forGetter(RulerDef::aggression),
                Codec.doubleRange(0, 1).optionalFieldOf("expansion", 0.3).forGetter(RulerDef::expansion),
                Codec.intRange(0, 64).optionalFieldOf("max_colonies", 4).forGetter(RulerDef::maxColonies),
                Codec.STRING.listOf().optionalFieldOf("colony_names", List.of()).forGetter(RulerDef::colonyNames),
                Codec.INT.optionalFieldOf("color", 0x808080).forGetter(RulerDef::color))
                .apply(i, RulerDef::new));
    }

    /**
     * The AI's numbers. Days are colony days. {@code found_*_distance}: how far from home new colonies go.
     * Strength is soldiers: each day a ruler gains {@code strength_per_day} plus {@code strength_per_colony} per
     * colony, up to {@code max_strength}. A colony's defense is its population x {@code defense_per_population} plus
     * {@code garrison_share} of its ruler's strength, split over the ruler's colonies. A ruler marches only when its
     * strength is at least {@code march_ratio} x that defense; the siege attacks with strength x (0.5 to 1.5), and the
     * loser loses {@code siege_losses} of its strength. Wars last at least {@code min_war_days}, then end with
     * {@code peace_chance} a day. With {@code wait_for_player} (Aaron, 2026-11-03) no ruler starts until a player
     * has founded a colony, so the AI gets no head start.
     */
    public record Settings(boolean enabled, int foundMinDistance, int foundMaxDistance, int startStrength,
            int strengthPerDay, int strengthPerColony, int maxStrength, int startPopulation, int maxPopulation,
            double defensePerPopulation, double garrisonShare, double marchRatio, double siegeLosses,
            int minWarDays, double peaceChance, boolean waitForPlayer) {
        public static final Settings DEFAULT = new Settings(true, 160, 640, 20, 1, 2, 200, 4, 24, 1.0, 0.25, 1.0,
                0.3, 3, 0.15, true);
        public static final Codec<Settings> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.optionalFieldOf("enabled", DEFAULT.enabled).forGetter(Settings::enabled),
                Codec.INT.optionalFieldOf("found_min_distance", DEFAULT.foundMinDistance)
                        .forGetter(Settings::foundMinDistance),
                Codec.INT.optionalFieldOf("found_max_distance", DEFAULT.foundMaxDistance)
                        .forGetter(Settings::foundMaxDistance),
                Codec.INT.optionalFieldOf("start_strength", DEFAULT.startStrength).forGetter(Settings::startStrength),
                Codec.INT.optionalFieldOf("strength_per_day", DEFAULT.strengthPerDay).forGetter(Settings::strengthPerDay),
                Codec.INT.optionalFieldOf("strength_per_colony", DEFAULT.strengthPerColony)
                        .forGetter(Settings::strengthPerColony),
                Codec.INT.optionalFieldOf("max_strength", DEFAULT.maxStrength).forGetter(Settings::maxStrength),
                Codec.INT.optionalFieldOf("start_population", DEFAULT.startPopulation)
                        .forGetter(Settings::startPopulation),
                Codec.INT.optionalFieldOf("max_population", DEFAULT.maxPopulation).forGetter(Settings::maxPopulation),
                Codec.DOUBLE.optionalFieldOf("defense_per_population", DEFAULT.defensePerPopulation)
                        .forGetter(Settings::defensePerPopulation),
                Codec.DOUBLE.optionalFieldOf("garrison_share", DEFAULT.garrisonShare).forGetter(Settings::garrisonShare),
                Codec.DOUBLE.optionalFieldOf("march_ratio", DEFAULT.marchRatio).forGetter(Settings::marchRatio),
                Codec.doubleRange(0, 1).optionalFieldOf("siege_losses", DEFAULT.siegeLosses)
                        .forGetter(Settings::siegeLosses),
                Codec.INT.optionalFieldOf("min_war_days", DEFAULT.minWarDays).forGetter(Settings::minWarDays),
                Codec.doubleRange(0, 1).optionalFieldOf("peace_chance", DEFAULT.peaceChance)
                        .forGetter(Settings::peaceChance),
                Codec.BOOL.optionalFieldOf("wait_for_player", DEFAULT.waitForPlayer).forGetter(Settings::waitForPlayer))
                .apply(i, Settings::new));
    }

    private static final ResourceLocation SETTINGS = LotrPort.id("settings");
    private static final CodecJsonLoader<Settings> SETTINGS_LOADER = new CodecJsonLoader<>("ai", Settings.CODEC);
    private static final CodecJsonLoader<RulerDef> RULERS = new CodecJsonLoader<>("ai_ruler", RulerDef.CODEC);

    private AiData() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent event) -> {
            event.addListener(SETTINGS_LOADER);
            event.addListener(RULERS);
        });
    }

    public static Settings settings() {
        return SETTINGS_LOADER.entries().getOrDefault(SETTINGS, Settings.DEFAULT);
    }

    public static Map<ResourceLocation, RulerDef> rulers() {
        return RULERS.entries();
    }

    public static List<String> loadProblems() {
        List<String> all = new ArrayList<>(SETTINGS_LOADER.lastErrors());
        all.addAll(RULERS.lastErrors());
        return all;
    }
}
