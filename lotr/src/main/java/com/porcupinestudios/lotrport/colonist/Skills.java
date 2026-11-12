package com.porcupinestudios.lotrport.colonist;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.society.ComponentType;
import com.porcupinestudios.lotrport.society.Person;
import com.porcupinestudios.lotrport.society.Persons;
import com.porcupinestudios.ringbearer.api.data.CodecJsonLoader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/**
 * Colonist skills (person component {@code skills}): a level 0 to {@code max_level} and XP toward the next, per skill.
 * Skills are data ({@code colonist_skill/<id>.json}: name, the jobs that use it and train it, per race a head start).
 * Work trains its job's skill each day it is done; each level makes the work {@code bonus_per_level} faster. The
 * numbers are {@code colonist_skill_settings/settings.json}.
 */
public final class Skills {
    /** One skill: its level and XP toward the next. */
    public record Skill(int level, double xp) {
        public static final Codec<Skill> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("level", 0).forGetter(Skill::level),
                Codec.DOUBLE.optionalFieldOf("xp", 0.0).forGetter(Skill::xp))
                .apply(i, Skill::new));
    }

    /** A kind of skill. */
    public record Def(String name, List<ResourceLocation> jobs, Map<String, Integer> races, int order) {
        public static final Codec<Def> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("name", "").forGetter(Def::name),
                ResourceLocation.CODEC.listOf().optionalFieldOf("jobs", List.of()).forGetter(Def::jobs),
                Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("races", Map.of()).forGetter(Def::races),
                Codec.INT.optionalFieldOf("order", 0).forGetter(Def::order))
                .apply(i, Def::new));
    }

    /**
     * @param maxLevel highest level
     * @param xpBase XP from level n to n+1 is {@code xpBase * (n + 1)}
     * @param xpPerDay XP a full day of work gives (times the day's work share)
     * @param bonusPerLevel work speed per level (0.1: level 10 works twice as fast as level 0)
     * @param settlerPoints levels a plain settler gets spread over skills (quality adds more)
     */
    public record Settings(int maxLevel, double xpBase, double xpPerDay, double bonusPerLevel, int settlerPoints) {
        public static final Settings DEFAULT = new Settings(20, 10, 4, 0.1, 3);
        public static final Codec<Settings> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("max_level", DEFAULT.maxLevel()).forGetter(Settings::maxLevel),
                Codec.DOUBLE.optionalFieldOf("xp_base", DEFAULT.xpBase()).forGetter(Settings::xpBase),
                Codec.DOUBLE.optionalFieldOf("xp_per_day", DEFAULT.xpPerDay()).forGetter(Settings::xpPerDay),
                Codec.DOUBLE.optionalFieldOf("bonus_per_level", DEFAULT.bonusPerLevel())
                        .forGetter(Settings::bonusPerLevel),
                Codec.INT.optionalFieldOf("settler_points", DEFAULT.settlerPoints()).forGetter(Settings::settlerPoints))
                .apply(i, Settings::new));
    }

    public static final Codec<Map<ResourceLocation, Skill>> MAP_CODEC =
            Codec.unboundedMap(ResourceLocation.CODEC, Skill.CODEC);
    public static final ComponentType<Map<ResourceLocation, Skill>> TYPE =
            ComponentType.register(LotrPort.id("skills"), MAP_CODEC);

    private static final CodecJsonLoader<Def> DEFS = new CodecJsonLoader<>("colonist_skill", Def.CODEC);
    private static final CodecJsonLoader<Settings> SETTINGS =
            new CodecJsonLoader<>("colonist_skill_settings", Settings.CODEC);

    private Skills() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent e) -> {
            e.addListener(DEFS);
            e.addListener(SETTINGS);
        });
    }

    public static Map<ResourceLocation, Def> defs() {
        return DEFS.entries();
    }

    public static Settings settings() {
        return SETTINGS.entries().getOrDefault(LotrPort.id("settings"), Settings.DEFAULT);
    }

    /** The skill a job uses, if any skill lists it. */
    public static Optional<ResourceLocation> forJob(ResourceLocation job) {
        for (Map.Entry<ResourceLocation, Def> e : defs().entrySet()) {
            if (e.getValue().jobs().contains(job)) {
                return Optional.of(e.getKey());
            }
        }
        return Optional.empty();
    }

    public static Map<ResourceLocation, Skill> of(Person p) {
        return p.get(TYPE).orElse(Map.of());
    }

    public static int level(Person p, ResourceLocation skill) {
        Skill s = of(p).get(skill);
        return s == null ? 0 : s.level();
    }

    /** How much faster this person does a job thanks to its skill (1 = untrained). */
    public static double workFactor(Person p, ResourceLocation job) {
        return forJob(job).map(s -> 1 + level(p, s) * settings().bonusPerLevel()).orElse(1.0);
    }

    /** Adds XP (levels up as far as it reaches). Pure: for tests. */
    public static Skill train(Skill s, double xp, Settings set) {
        int level = s.level();
        double have = s.xp() + Math.max(0, xp);
        while (level < set.maxLevel() && have >= set.xpBase() * (level + 1)) {
            have -= set.xpBase() * (level + 1);
            level++;
        }
        return new Skill(level, level >= set.maxLevel() ? 0 : have);
    }

    /** A day's work at a job trains its skill by {@code share} of a full day's XP. */
    public static void worked(Persons persons, Person p, ResourceLocation job, double share) {
        Optional<ResourceLocation> skill = forJob(job);
        if (skill.isEmpty() || share <= 0) {
            return;
        }
        Map<ResourceLocation, Skill> all = new LinkedHashMap<>(of(p));
        Skill s = all.getOrDefault(skill.get(), new Skill(0, 0));
        all.put(skill.get(), train(s, settings().xpPerDay() * share, settings()));
        persons.setQuiet(p, TYPE, all);
    }

    /**
     * Rolls a newcomer's skills: {@code points} levels spread at random over the skills (their race's head start
     * first), no skill above {@code cap}.
     */
    public static Map<ResourceLocation, Skill> roll(String race, int points, int cap, Random random) {
        Map<ResourceLocation, Skill> out = new LinkedHashMap<>();
        List<ResourceLocation> ids = defs().keySet().stream().sorted().toList();
        if (ids.isEmpty()) {
            return out;
        }
        for (ResourceLocation id : ids) {
            int start = defs().get(id).races().getOrDefault(race, 0);
            if (start > 0) {
                out.put(id, new Skill(Math.min(cap, start), 0));
            }
        }
        for (int i = 0; i < points; i++) {
            ResourceLocation id = ids.get(random.nextInt(ids.size()));
            Skill s = out.getOrDefault(id, new Skill(0, 0));
            if (s.level() < cap) {
                out.put(id, new Skill(s.level() + 1, 0));
            }
        }
        return out;
    }

    /** The best skill and its level, for lists. */
    public static Optional<Map.Entry<ResourceLocation, Skill>> best(Map<ResourceLocation, Skill> skills) {
        return skills.entrySet().stream().max(Map.Entry.comparingByValue(java.util.Comparator.comparingInt(
                Skill::level)));
    }

    public static String name(ResourceLocation id) {
        Def d = defs().get(id);
        return d == null || d.name().isEmpty() ? id.getPath() : d.name();
    }
}
