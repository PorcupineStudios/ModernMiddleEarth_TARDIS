package com.porcupinestudios.lotrport.colonist;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.group.PartType;
import com.porcupinestudios.lotrport.group.Parts;
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
 * The colonist data folders, all reloaded with /reload: {@code colonist_trait/<id>.json}, {@code colonist_need/},
 * {@code colonist_thought/}, {@code colonist_race/} and {@code colonist/settings.json}; and the parts a person
 * carries (traits, needs, mood, kin). The rules that use them are plain classes ({@link TraitRules},
 * {@link NeedRules}, {@link MoodRules}, {@link KinRules}); only the Codecs and the loading are here.
 */
public final class ColonistData {
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

    private static final Codec<NeedDef.Level> LEVEL_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.DOUBLE.fieldOf("below").forGetter(NeedDef.Level::below),
            ResourceLocation.CODEC.fieldOf("thought").forGetter(NeedDef.Level::thought))
            .apply(i, NeedDef.Level::new));

    public static final Codec<NeedDef> NEED_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.DOUBLE.fieldOf("fall_per_day").forGetter(NeedDef::fallPerDay),
            Codec.DOUBLE.optionalFieldOf("start", 80.0).forGetter(NeedDef::start),
            LEVEL_CODEC.listOf().optionalFieldOf("levels", List.of()).forGetter(NeedDef::levels))
            .apply(i, NeedDef::new));

    public static final Codec<ThoughtDef> THOUGHT_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("mood").forGetter(ThoughtDef::mood),
            Codec.LONG.optionalFieldOf("duration_ticks", 0L).forGetter(ThoughtDef::durationTicks),
            Codec.INT.optionalFieldOf("stack_limit", 1).forGetter(ThoughtDef::stackLimit))
            .apply(i, ThoughtDef::new));

    public static final Codec<RaceDef> RACE_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.listOf().optionalFieldOf("match", List.of()).forGetter(RaceDef::match),
            Codec.DOUBLE.optionalFieldOf("days_per_year", 1.0).forGetter(RaceDef::daysPerYear),
            Codec.INT.optionalFieldOf("adult_age", 18).forGetter(RaceDef::adultAge),
            Codec.BOOL.optionalFieldOf("extended_household", false).forGetter(RaceDef::extendedHousehold),
            RATES.optionalFieldOf("need_rates", Map.of()).forGetter(RaceDef::needRates),
            Codec.INT.optionalFieldOf("settler_age_min", 18).forGetter(RaceDef::settlerAgeMin),
            Codec.INT.optionalFieldOf("settler_age_max", 50).forGetter(RaceDef::settlerAgeMax))
            .apply(i, RaceDef::new));

    public static final ColonistSettings DEFAULT_SETTINGS = new ColonistSettings(3, 0.35, 50, 15, 30, 45, 80,
            LotrPort.id("man"), LotrPort.id("own_home"), LotrPort.id("shared_home"), LotrPort.id("barracks"),
            LotrPort.id("homeless"), 3);

    public static final Codec<ColonistSettings> SETTINGS_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("max_traits", DEFAULT_SETTINGS.maxTraits()).forGetter(ColonistSettings::maxTraits),
            Codec.DOUBLE.optionalFieldOf("extra_trait_chance", DEFAULT_SETTINGS.extraTraitChance())
                    .forGetter(ColonistSettings::extraTraitChance),
            Codec.INT.optionalFieldOf("base_mood", DEFAULT_SETTINGS.baseMood()).forGetter(ColonistSettings::baseMood),
            Codec.INT.optionalFieldOf("major_break", DEFAULT_SETTINGS.majorBreak())
                    .forGetter(ColonistSettings::majorBreak),
            Codec.INT.optionalFieldOf("minor_break", DEFAULT_SETTINGS.minorBreak())
                    .forGetter(ColonistSettings::minorBreak),
            Codec.INT.optionalFieldOf("unhappy", DEFAULT_SETTINGS.unhappy()).forGetter(ColonistSettings::unhappy),
            Codec.INT.optionalFieldOf("inspired", DEFAULT_SETTINGS.inspired()).forGetter(ColonistSettings::inspired),
            ResourceLocation.CODEC.optionalFieldOf("default_race", DEFAULT_SETTINGS.defaultRace())
                    .forGetter(ColonistSettings::defaultRace),
            ResourceLocation.CODEC.optionalFieldOf("own_home", DEFAULT_SETTINGS.ownHome())
                    .forGetter(ColonistSettings::ownHome),
            ResourceLocation.CODEC.optionalFieldOf("shared_home", DEFAULT_SETTINGS.sharedHome())
                    .forGetter(ColonistSettings::sharedHome),
            ResourceLocation.CODEC.optionalFieldOf("barracks", DEFAULT_SETTINGS.barracks())
                    .forGetter(ColonistSettings::barracks),
            ResourceLocation.CODEC.optionalFieldOf("homeless", DEFAULT_SETTINGS.homeless())
                    .forGetter(ColonistSettings::homeless),
            Codec.INT.optionalFieldOf("barracks_households", DEFAULT_SETTINGS.barracksHouseholds())
                    .forGetter(ColonistSettings::barracksHouseholds))
            .apply(i, ColonistSettings::new));

    // ---------------------------------------------------------------- person parts

    private static final Codec<Needs.Bar> BAR_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.DOUBLE.fieldOf("value").forGetter(Needs.Bar::value),
            Codec.LONG.fieldOf("tick").forGetter(Needs.Bar::tick))
            .apply(i, Needs.Bar::new));
    private static final Codec<Needs> NEEDS_CODEC =
            Codec.unboundedMap(ResourceLocation.CODEC, BAR_CODEC).xmap(Needs::new, Needs::bars);

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
    public static final PartType<List<ResourceLocation>> TRAITS =
            Parts.register(PartType.of(LotrPort.id("traits"), 1, IDS));
    public static final PartType<Needs> NEEDS = Parts.register(PartType.of(LotrPort.id("needs"), 1, NEEDS_CODEC));
    public static final PartType<Mood> MOOD = Parts.register(PartType.of(LotrPort.id("mood"), 1, MOOD_CODEC));
    public static final PartType<Kin> KIN = Parts.register(PartType.of(LotrPort.id("kin"), 1, KIN_CODEC));

    // ---------------------------------------------------------------- loading

    private static final ResourceLocation SETTINGS = LotrPort.id("settings");
    private static final CodecJsonLoader<ColonistSettings> SETTINGS_LOADER =
            new CodecJsonLoader<>("colonist", SETTINGS_CODEC);
    private static final CodecJsonLoader<TraitDef> TRAITS_LOADER = new CodecJsonLoader<>("colonist_trait", TRAIT_CODEC);
    private static final CodecJsonLoader<NeedDef> NEEDS_LOADER = new CodecJsonLoader<>("colonist_need", NEED_CODEC);
    private static final CodecJsonLoader<ThoughtDef> THOUGHTS_LOADER =
            new CodecJsonLoader<>("colonist_thought", THOUGHT_CODEC);
    private static final CodecJsonLoader<RaceDef> RACES_LOADER = new CodecJsonLoader<>("colonist_race", RACE_CODEC);

    private ColonistData() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent event) -> {
            event.addListener(SETTINGS_LOADER);
            event.addListener(TRAITS_LOADER);
            event.addListener(NEEDS_LOADER);
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

    public static Map<ResourceLocation, NeedDef> needs() {
        return NEEDS_LOADER.entries();
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
        all.addAll(NEEDS_LOADER.lastErrors());
        all.addAll(THOUGHTS_LOADER.lastErrors());
        all.addAll(RACES_LOADER.lastErrors());
        ColonistSettings s = settings();
        for (ResourceLocation t : List.of(s.ownHome(), s.sharedHome(), s.barracks(), s.homeless())) {
            if (!thoughts().containsKey(t)) {
                all.add("colonist/settings: unknown thought " + t);
            }
        }
        if (!races().containsKey(s.defaultRace())) {
            all.add("colonist/settings: unknown race " + s.defaultRace());
        }
        traits().forEach((id, def) -> {
            def.thoughts().stream().filter(t -> !thoughts().containsKey(t))
                    .forEach(t -> all.add("colonist_trait/" + id + ": unknown thought " + t));
            def.conflicts().stream().filter(t -> !traits().containsKey(t))
                    .forEach(t -> all.add("colonist_trait/" + id + ": unknown trait " + t));
            def.needRates().keySet().stream().filter(n -> !needs().containsKey(n))
                    .forEach(n -> all.add("colonist_trait/" + id + ": unknown need " + n));
        });
        needs().forEach((id, def) -> def.levels().stream().filter(l -> !thoughts().containsKey(l.thought()))
                .forEach(l -> all.add("colonist_need/" + id + ": unknown thought " + l.thought())));
        races().forEach((id, def) -> def.needRates().keySet().stream().filter(n -> !needs().containsKey(n))
                .forEach(n -> all.add("colonist_race/" + id + ": unknown need " + n)));
        return all;
    }
}
