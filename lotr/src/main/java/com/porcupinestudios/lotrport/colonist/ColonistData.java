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
            Codec.INT.optionalFieldOf("stack_limit", 1).forGetter(ThoughtDef::stackLimit),
            Codec.STRING.optionalFieldOf("name").forGetter(ThoughtDef::name))
            .apply(i, ThoughtDef::new));

    private static final Codec<Integer> HOUR = Codec.intRange(0, 23);
    public static final Codec<Rhythm> RHYTHM_CODEC = RecordCodecBuilder.create(i -> i.group(
            HOUR.optionalFieldOf("awake_from", Rhythm.DEFAULT.awakeFrom()).forGetter(Rhythm::awakeFrom),
            HOUR.optionalFieldOf("awake_to", Rhythm.DEFAULT.awakeTo()).forGetter(Rhythm::awakeTo),
            Codec.intRange(1, 23).optionalFieldOf("sleep_hours", Rhythm.DEFAULT.sleepHours())
                    .forGetter(Rhythm::sleepHours),
            Codec.intRange(0, 23).optionalFieldOf("leisure_hours", Rhythm.DEFAULT.leisureHours())
                    .forGetter(Rhythm::leisureHours))
            .apply(i, Rhythm::new));

    public static final SleepSettings DEFAULT_SLEEP = new SleepSettings("SSSSSSWWWWWWAWWWWWLLLLSS", 70, 0.7, 60, 25, 10,
            0.6, 10, 1.25, 1, 4, new SleepSettings.Thoughts(LotrPort.id("tired"), LotrPort.id("exhausted"),
                    LotrPort.id("odd_hours"), LotrPort.id("wrong_hours"), LotrPort.id("no_leisure"),
                    LotrPort.id("enough_leisure")));

    private static final Codec<SleepSettings.Thoughts> SLEEP_THOUGHTS_CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.optionalFieldOf("tired", DEFAULT_SLEEP.thoughts().tired())
                    .forGetter(SleepSettings.Thoughts::tired),
            ResourceLocation.CODEC.optionalFieldOf("exhausted", DEFAULT_SLEEP.thoughts().exhausted())
                    .forGetter(SleepSettings.Thoughts::exhausted),
            ResourceLocation.CODEC.optionalFieldOf("odd_hours", DEFAULT_SLEEP.thoughts().oddHours())
                    .forGetter(SleepSettings.Thoughts::oddHours),
            ResourceLocation.CODEC.optionalFieldOf("wrong_hours", DEFAULT_SLEEP.thoughts().wrongHours())
                    .forGetter(SleepSettings.Thoughts::wrongHours),
            ResourceLocation.CODEC.optionalFieldOf("no_leisure", DEFAULT_SLEEP.thoughts().noLeisure())
                    .forGetter(SleepSettings.Thoughts::noLeisure),
            ResourceLocation.CODEC.optionalFieldOf("enough_leisure", DEFAULT_SLEEP.thoughts().enoughLeisure())
                    .forGetter(SleepSettings.Thoughts::enoughLeisure))
            .apply(i, SleepSettings.Thoughts::new));

    public static final Codec<SleepSettings> SLEEP_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("standard_schedule", DEFAULT_SLEEP.standardBlocks())
                    .forGetter(SleepSettings::standardBlocks),
            Codec.DOUBLE.optionalFieldOf("day_drain", DEFAULT_SLEEP.dayDrain()).forGetter(SleepSettings::dayDrain),
            Codec.DOUBLE.optionalFieldOf("homeless_sleep", DEFAULT_SLEEP.homelessSleep())
                    .forGetter(SleepSettings::homelessSleep),
            Codec.DOUBLE.optionalFieldOf("any_sleep_below", DEFAULT_SLEEP.anySleepBelow())
                    .forGetter(SleepSettings::anySleepBelow),
            Codec.DOUBLE.optionalFieldOf("tired_below", DEFAULT_SLEEP.tiredBelow()).forGetter(SleepSettings::tiredBelow),
            Codec.DOUBLE.optionalFieldOf("exhausted_below", DEFAULT_SLEEP.exhaustedBelow())
                    .forGetter(SleepSettings::exhaustedBelow),
            Codec.DOUBLE.optionalFieldOf("exhausted_work", DEFAULT_SLEEP.exhaustedWork())
                    .forGetter(SleepSettings::exhaustedWork),
            Codec.DOUBLE.optionalFieldOf("standard_work_hours", DEFAULT_SLEEP.standardWorkHours())
                    .forGetter(SleepSettings::standardWorkHours),
            Codec.DOUBLE.optionalFieldOf("max_work_factor", DEFAULT_SLEEP.maxWorkFactor())
                    .forGetter(SleepSettings::maxWorkFactor),
            Codec.INT.optionalFieldOf("odd_hours_from", DEFAULT_SLEEP.oddHoursFrom())
                    .forGetter(SleepSettings::oddHoursFrom),
            Codec.INT.optionalFieldOf("wrong_hours_from", DEFAULT_SLEEP.wrongHoursFrom())
                    .forGetter(SleepSettings::wrongHoursFrom),
            SLEEP_THOUGHTS_CODEC.optionalFieldOf("thoughts", DEFAULT_SLEEP.thoughts())
                    .forGetter(SleepSettings::thoughts))
            .apply(i, SleepSettings::new));

    public static final Codec<RaceDef> RACE_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.DOUBLE.optionalFieldOf("days_per_year", 1.0).forGetter(RaceDef::daysPerYear),
            Codec.INT.optionalFieldOf("adult_age", 18).forGetter(RaceDef::adultAge),
            Codec.BOOL.optionalFieldOf("extended_household", false).forGetter(RaceDef::extendedHousehold),
            RATES.optionalFieldOf("need_rates", Map.of()).forGetter(RaceDef::needRates),
            Codec.INT.optionalFieldOf("settler_age_min", 18).forGetter(RaceDef::settlerAgeMin),
            Codec.INT.optionalFieldOf("settler_age_max", 50).forGetter(RaceDef::settlerAgeMax),
            RHYTHM_CODEC.optionalFieldOf("rhythm", Rhythm.DEFAULT).forGetter(RaceDef::rhythm))
            .apply(i, RaceDef::new));

    public static final Codec<RationDef> RATION_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.DOUBLE.optionalFieldOf("food_rate", 1.0).forGetter(RationDef::foodRate),
            ResourceLocation.CODEC.optionalFieldOf("thought").forGetter(RationDef::thought),
            Codec.INT.optionalFieldOf("order", 0).forGetter(RationDef::order))
            .apply(i, RationDef::new));

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

    private static final Codec<Orders> ORDERS_CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.optionalFieldOf("job").forGetter(Orders::job),
            UUIDUtil.CODEC.optionalFieldOf("home").forGetter(Orders::home),
            Codec.BOOL.optionalFieldOf("works", true).forGetter(Orders::works),
            ResourceLocation.CODEC.optionalFieldOf("ration", Rations.NORMAL).forGetter(Orders::ration),
            Codec.STRING.optionalFieldOf("schedule", Schedules.STANDARD).forGetter(Orders::schedule))
            .apply(i, Orders::new));

    /** A person's traits, for life. */
    public static final ComponentType<List<ResourceLocation>> TRAITS =
            ComponentType.register(LotrPort.id("traits"), IDS);
    /** Remembered thoughts that still count. */
    public static final ComponentType<Mood> MOOD = ComponentType.register(LotrPort.id("mood"), MOOD_CODEC);
    private static final Codec<Rest> REST_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.DOUBLE.fieldOf("energy").forGetter(Rest::energy),
            Codec.DOUBLE.optionalFieldOf("lowest", 100.0).forGetter(Rest::lowest),
            Codec.DOUBLE.optionalFieldOf("work", -1.0).forGetter(Rest::workDone),
            Codec.INT.optionalFieldOf("leisure", -1).forGetter(Rest::leisureHours),
            Codec.INT.optionalFieldOf("sleep", -1).forGetter(Rest::sleepHours),
            Codec.INT.optionalFieldOf("off", 0).forGetter(Rest::offHours))
            .apply(i, Rest::new));
    /** Energy and how the last day's hours went (sleep and schedules). */
    public static final ComponentType<Rest> REST = ComponentType.register(LotrPort.id("rest"), REST_CODEC);

    /** Race, birth and family links. */
    public static final ComponentType<Kin> KIN = ComponentType.register(LotrPort.id("kin"), KIN_CODEC);
    /** What the colony's managers told them (workplace, home, work or rest, ration); absent: all left to the colony. */
    public static final ComponentType<Orders> ORDERS = ComponentType.register(LotrPort.id("orders"), ORDERS_CODEC);

    // ---------------------------------------------------------------- loading

    private static final ResourceLocation SETTINGS = LotrPort.id("settings");
    private static final CodecJsonLoader<ColonistSettings> SETTINGS_LOADER =
            new CodecJsonLoader<>("colonist", SETTINGS_CODEC);
    private static final CodecJsonLoader<TraitDef> TRAITS_LOADER = new CodecJsonLoader<>("colonist_trait", TRAIT_CODEC);
    private static final CodecJsonLoader<ThoughtDef> THOUGHTS_LOADER =
            new CodecJsonLoader<>("colonist_thought", THOUGHT_CODEC);
    private static final CodecJsonLoader<RaceDef> RACES_LOADER = new CodecJsonLoader<>("colonist_race", RACE_CODEC);
    private static final ResourceLocation SLEEP_ID = LotrPort.id("settings");
    private static final CodecJsonLoader<SleepSettings> SLEEP_LOADER =
            new CodecJsonLoader<>("colonist_sleep", SLEEP_CODEC);
    private static final CodecJsonLoader<RationDef> RATIONS_LOADER =
            new CodecJsonLoader<>("colonist_ration", RATION_CODEC);

    private ColonistData() {}

    /** Registers the loaders; also loads this class, which registers the components before any world loads. */
    public static void register() {
        // the colony part lives in its own class: load it now, before any world, as component types must be
        java.util.Objects.requireNonNull(Schedules.TYPE);
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent event) -> {
            event.addListener(SETTINGS_LOADER);
            event.addListener(TRAITS_LOADER);
            event.addListener(THOUGHTS_LOADER);
            event.addListener(RACES_LOADER);
            event.addListener(RATIONS_LOADER);
            event.addListener(SLEEP_LOADER);
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

    public static SleepSettings sleep() {
        return SLEEP_LOADER.entries().getOrDefault(SLEEP_ID, DEFAULT_SLEEP);
    }

    public static Map<ResourceLocation, RationDef> rations() {
        return RATIONS_LOADER.entries();
    }

    /** Load errors, and every id in the data that points at a thought, trait, need or race that does not exist. */
    public static List<String> loadProblems() {
        List<String> all = new ArrayList<>(SETTINGS_LOADER.lastErrors());
        all.addAll(TRAITS_LOADER.lastErrors());
        all.addAll(THOUGHTS_LOADER.lastErrors());
        all.addAll(RACES_LOADER.lastErrors());
        all.addAll(RATIONS_LOADER.lastErrors());
        all.addAll(SLEEP_LOADER.lastErrors());
        SleepSettings.Thoughts st = sleep().thoughts();
        for (ResourceLocation t : List.of(st.tired(), st.exhausted(), st.oddHours(), st.wrongHours(), st.noLeisure(),
                st.enoughLeisure())) {
            if (!thoughts().containsKey(t)) {
                all.add("colonist_sleep/settings: unknown thought " + t);
            }
        }
        if (!rations().containsKey(Rations.NORMAL)) {
            all.add("colonist_ration: no normal.json (everyone's ration until told otherwise)");
        }
        rations().forEach((id, def) -> def.thought().filter(t -> !thoughts().containsKey(t))
                .ifPresent(t -> all.add("colonist_ration/" + id + ": unknown thought " + t)));
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
