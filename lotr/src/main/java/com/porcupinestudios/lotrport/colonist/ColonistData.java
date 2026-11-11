package com.porcupinestudios.lotrport.colonist;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.society.ComponentType;
import com.porcupinestudios.ringbearer.api.data.CodecJsonLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/**
 * The colonist data folders, reloaded with /reload: {@code colonist_trait/}, {@code colonist_thought/},
 * {@code colonist_race/} and {@code colonist/settings.json}; and the person components traits, mood and kin. The
 * rules that use them are plain classes ({@link TraitRules}, {@link MoodRules}, {@link KinRules},
 * {@link HouseRules}); the glue is {@link Colonists}.
 */
public final class ColonistData {
    /** The one need the colony tracks per person so far (their {@code Needs.food}); traits and races scale it. */
    public static final ResourceLocation FOOD = LotrPort.id("food");

    private static final Codec<Map<ResourceLocation, Double>> RATES =
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.DOUBLE);
    private static final Codec<List<ResourceLocation>> IDS = ResourceLocation.CODEC.listOf();
    private static final Codec<TraitDef.Kind> KIND = Codec.STRING.xmap(
            s -> TraitDef.Kind.valueOf(s.toUpperCase(Locale.ROOT)), k -> k.name().toLowerCase(Locale.ROOT));

    public static final Codec<TraitDef> TRAIT_CODEC = RecordCodecBuilder.create(i -> i.group(
            KIND.fieldOf("kind").forGetter(TraitDef::kind),
            Codec.STRING.optionalFieldOf("scale").forGetter(TraitDef::scale),
            Codec.INT.optionalFieldOf("weight", 10).forGetter(TraitDef::weight),
            Codec.DOUBLE.optionalFieldOf("heritable", 0.0).forGetter(TraitDef::heritable),
            Codec.DOUBLE.optionalFieldOf("work_speed", 1.0).forGetter(TraitDef::workSpeed),
            RATES.optionalFieldOf("need_rates", Map.of()).forGetter(TraitDef::needRates),
            IDS.optionalFieldOf("thoughts", List.of()).forGetter(TraitDef::thoughts),
            Codec.STRING.optionalFieldOf("on_break").forGetter(TraitDef::onBreak),
            IDS.optionalFieldOf("conflicts", List.of()).forGetter(TraitDef::conflicts))
            .apply(i, TraitDef::new));

    public static final Codec<ThoughtDef> THOUGHT_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("mood").forGetter(ThoughtDef::mood),
            Codec.LONG.optionalFieldOf("duration_ticks", 0L).forGetter(ThoughtDef::durationTicks),
            Codec.INT.optionalFieldOf("stack_limit", 1).forGetter(ThoughtDef::stackLimit))
            .apply(i, ThoughtDef::new));

    public static final Codec<RaceDef> RACE_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.DOUBLE.optionalFieldOf("days_per_year", 1.0).forGetter(RaceDef::daysPerYear),
            Codec.INT.optionalFieldOf("adult_age", 18).forGetter(RaceDef::adultAge),
            Codec.BOOL.optionalFieldOf("extended_household", false).forGetter(RaceDef::extendedHousehold),
            RATES.optionalFieldOf("need_rates", Map.of()).forGetter(RaceDef::needRates),
            Codec.INT.optionalFieldOf("settler_age_min", 18).forGetter(RaceDef::settlerAgeMin),
            Codec.INT.optionalFieldOf("settler_age_max", 50).forGetter(RaceDef::settlerAgeMax))
            .apply(i, RaceDef::new));

    public static final ColonistSettings DEFAULT_SETTINGS = new ColonistSettings(3, 0.35, LotrPort.id("own_home"),
            LotrPort.id("family_home"), LotrPort.id("shared_home"), LotrPort.id("family_shared"),
            LotrPort.id("barracks"), LotrPort.id("family_barracks"), 3);

    public static final Codec<ColonistSettings> SETTINGS_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("max_traits", DEFAULT_SETTINGS.maxTraits()).forGetter(ColonistSettings::maxTraits),
            Codec.DOUBLE.optionalFieldOf("extra_trait_chance", DEFAULT_SETTINGS.extraTraitChance())
                    .forGetter(ColonistSettings::extraTraitChance),
            ResourceLocation.CODEC.optionalFieldOf("own_home", DEFAULT_SETTINGS.ownHome())
                    .forGetter(ColonistSettings::ownHome),
            ResourceLocation.CODEC.optionalFieldOf("family_home", DEFAULT_SETTINGS.familyHome())
                    .forGetter(ColonistSettings::familyHome),
            ResourceLocation.CODEC.optionalFieldOf("shared_home", DEFAULT_SETTINGS.sharedHome())
                    .forGetter(ColonistSettings::sharedHome),
            ResourceLocation.CODEC.optionalFieldOf("family_shared", DEFAULT_SETTINGS.familyShared())
                    .forGetter(ColonistSettings::familyShared),
            ResourceLocation.CODEC.optionalFieldOf("barracks", DEFAULT_SETTINGS.barracks())
                    .forGetter(ColonistSettings::barracks),
            ResourceLocation.CODEC.optionalFieldOf("family_barracks", DEFAULT_SETTINGS.familyBarracks())
                    .forGetter(ColonistSettings::familyBarracks),
            Codec.INT.optionalFieldOf("barracks_households", DEFAULT_SETTINGS.barracksHouseholds())
                    .forGetter(ColonistSettings::barracksHouseholds))
            .apply(i, ColonistSettings::new));

    // ---------------------------------------------------------------- person components

    private static final Codec<Mood.Memory> MEMORY_CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("thought").forGetter(Mood.Memory::thought),
            Codec.LONG.fieldOf("since").forGetter(Mood.Memory::since),
            Codec.INT.optionalFieldOf("stacks", 1).forGetter(Mood.Memory::stacks))
            .apply(i, Mood.Memory::new));
    private static final Codec<Mood> MOOD_CODEC = MEMORY_CODEC.listOf().xmap(Mood::new, Mood::memories);

    private static final Codec<Kin> KIN_CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("race").forGetter(Kin::race),
            Codec.LONG.fieldOf("born").forGetter(Kin::born),
            UUIDUtil.CODEC.listOf().optionalFieldOf("parents", List.of()).forGetter(Kin::parents),
            UUIDUtil.CODEC.optionalFieldOf("spouse").forGetter(Kin::spouse),
            UUIDUtil.CODEC.listOf().optionalFieldOf("children", List.of()).forGetter(Kin::children))
            .apply(i, Kin::new));

    /** A person's traits, for life. */
    public static final ComponentType<List<ResourceLocation>> TRAITS =
            ComponentType.register(LotrPort.id("traits"), IDS);
    /** Remembered thoughts that still count. */
    public static final ComponentType<Mood> MOOD = ComponentType.register(LotrPort.id("mood"), MOOD_CODEC);
    /** Race, birth and family links. */
    public static final ComponentType<Kin> KIN = ComponentType.register(LotrPort.id("kin"), KIN_CODEC);

    // ---------------------------------------------------------------- loading

    private static final ResourceLocation SETTINGS = LotrPort.id("settings");
    private static final CodecJsonLoader<ColonistSettings> SETTINGS_LOADER =
            new CodecJsonLoader<>("colonist", SETTINGS_CODEC);
    private static final CodecJsonLoader<TraitDef> TRAITS_LOADER = new CodecJsonLoader<>("colonist_trait", TRAIT_CODEC);
    private static final CodecJsonLoader<ThoughtDef> THOUGHTS_LOADER =
            new CodecJsonLoader<>("colonist_thought", THOUGHT_CODEC);
    private static final CodecJsonLoader<RaceDef> RACES_LOADER = new CodecJsonLoader<>("colonist_race", RACE_CODEC);

    private ColonistData() {}

    /** Registers the loaders; also loads this class, which registers the components before any world loads. */
    public static void register() {
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent event) -> {
            event.addListener(SETTINGS_LOADER);
            event.addListener(TRAITS_LOADER);
            event.addListener(THOUGHTS_LOADER);
            event.addListener(RACES_LOADER);
        });
    }

    public static ColonistSettings settings() {
        return SETTINGS_LOADER.entries().getOrDefault(SETTINGS, DEFAULT_SETTINGS);
    }

    public static Map<ResourceLocation, TraitDef> traits() {
        return TRAITS_LOADER.entries();
    }

    public static Map<ResourceLocation, ThoughtDef> thoughts() {
        return THOUGHTS_LOADER.entries();
    }

    public static Map<ResourceLocation, RaceDef> races() {
        return RACES_LOADER.entries();
    }

    /** Load errors, and every id in the data that points at a thought, trait, need or race that does not exist. */
    public static List<String> loadProblems() {
        List<String> all = new ArrayList<>(SETTINGS_LOADER.lastErrors());
        all.addAll(TRAITS_LOADER.lastErrors());
        all.addAll(THOUGHTS_LOADER.lastErrors());
        all.addAll(RACES_LOADER.lastErrors());
        ColonistSettings s = settings();
        for (ResourceLocation t : List.of(s.ownHome(), s.familyHome(), s.sharedHome(), s.familyShared(), s.barracks(),
                s.familyBarracks())) {
            if (!thoughts().containsKey(t)) {
                all.add("colonist/settings: unknown thought " + t);
            }
        }
        if (!races().containsKey(LotrPort.id(com.porcupinestudios.lotrport.colony.work.Races.MAN))) {
            all.add("colonist_race: no man.json (the race of any NPC type in no colony_race list)");
        }
        traits().forEach((id, def) -> {
            def.thoughts().stream().filter(t -> !thoughts().containsKey(t))
                    .forEach(t -> all.add("colonist_trait/" + id + ": unknown thought " + t));
            def.conflicts().stream().filter(t -> !traits().containsKey(t))
                    .forEach(t -> all.add("colonist_trait/" + id + ": unknown trait " + t));
            def.needRates().keySet().stream().filter(n -> !n.equals(FOOD))
                    .forEach(n -> all.add("colonist_trait/" + id + ": unknown need " + n));
        });
        races().forEach((id, def) -> def.needRates().keySet().stream().filter(n -> !n.equals(FOOD))
                .forEach(n -> all.add("colonist_race/" + id + ": unknown need " + n)));
        return all;
    }
}
