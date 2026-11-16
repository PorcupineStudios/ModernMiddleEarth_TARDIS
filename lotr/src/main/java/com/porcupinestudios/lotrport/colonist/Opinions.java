package com.porcupinestudios.lotrport.colonist;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.society.ComponentType;
import com.porcupinestudios.lotrport.society.Person;
import com.porcupinestudios.lotrport.society.Persons;
import com.porcupinestudios.ringbearer.api.data.CodecJsonLoader;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/**
 * What colonists think of each other (person component {@code opinions}: per other person, -100 to 100). Each day an
 * opinion drifts toward a target made of what the two share (race, home, workplace, family) and their traits (a
 * trait can like or dislike others with a trait), and events add to it (weddings, festivals, quarrels). Numbers in
 * {@code colonist_opinion/settings.json}. Opinions drive weddings and quarrels ({@code colony/events}).
 */
public final class Opinions {
    /**
     * @param drift share of the way to the target an opinion moves each day
     * @param sameRace target for the same race
     * @param otherRace target for a different race
     * @param housemates added when they share a home
     * @param coworkers added when they work at the same place
     * @param family added for parents, children, siblings and spouses
     * @param traitLikes per trait id: trait ids it likes (+) and dislikes (-) in others, as numbers
     */
    public record Settings(double drift, double sameRace, double otherRace, double housemates, double coworkers,
            double family, Map<String, Map<String, Double>> traitLikes) {
        public static final Settings DEFAULT = new Settings(0.08, 10, -5, 15, 10, 40, Map.of());
        public static final Codec<Settings> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.optionalFieldOf("drift", DEFAULT.drift()).forGetter(Settings::drift),
                Codec.DOUBLE.optionalFieldOf("same_race", DEFAULT.sameRace()).forGetter(Settings::sameRace),
                Codec.DOUBLE.optionalFieldOf("other_race", DEFAULT.otherRace()).forGetter(Settings::otherRace),
                Codec.DOUBLE.optionalFieldOf("housemates", DEFAULT.housemates()).forGetter(Settings::housemates),
                Codec.DOUBLE.optionalFieldOf("coworkers", DEFAULT.coworkers()).forGetter(Settings::coworkers),
                Codec.DOUBLE.optionalFieldOf("family", DEFAULT.family()).forGetter(Settings::family),
                Codec.unboundedMap(Codec.STRING, Codec.unboundedMap(Codec.STRING, Codec.DOUBLE))
                        .optionalFieldOf("trait_likes", Map.of()).forGetter(Settings::traitLikes))
                .apply(i, Settings::new));
    }

    public static final Codec<Map<UUID, Double>> MAP_CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.DOUBLE);
    public static final ComponentType<Map<UUID, Double>> TYPE =
            ComponentType.register(LotrPort.id("opinions"), MAP_CODEC);
    private static final CodecJsonLoader<Settings> SETTINGS = new CodecJsonLoader<>("colonist_opinion", Settings.CODEC);

    private Opinions() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent e) -> e.addListener(SETTINGS));
    }

    public static Settings settings() {
        return SETTINGS.entries().getOrDefault(LotrPort.id("settings"), Settings.DEFAULT);
    }

    public static double of(Person p, UUID other) {
        return p.get(TYPE).map(m -> m.getOrDefault(other, 0.0)).orElse(0.0);
    }

    public static void set(Persons persons, Person p, UUID other, double v) {
        Map<UUID, Double> m = new LinkedHashMap<>(p.get(TYPE).orElse(Map.of()));
        m.put(other, clamp(v));
        persons.setQuiet(p, TYPE, m);
    }

    public static void add(Persons persons, Person p, UUID other, double by) {
        set(persons, p, other, of(p, other) + by);
    }

    static double clamp(double v) {
        return Math.max(-100, Math.min(100, v));
    }

    /** What two people share, for the daily target (pure: for tests). */
    public record Pair(boolean sameRace, boolean housemates, boolean coworkers, boolean family,
            List<ResourceLocation> traitsA, List<ResourceLocation> traitsB) {}

    /** The opinion A drifts toward of B. */
    public static double target(Pair p, Settings s) {
        double t = p.sameRace() ? s.sameRace() : s.otherRace();
        if (p.housemates()) {
            t += s.housemates();
        }
        if (p.coworkers()) {
            t += s.coworkers();
        }
        if (p.family()) {
            t += s.family();
        }
        for (ResourceLocation a : p.traitsA()) {
            Map<String, Double> likes = s.traitLikes().get(a.toString());
            if (likes == null) {
                continue;
            }
            for (ResourceLocation b : p.traitsB()) {
                t += likes.getOrDefault(b.toString(), 0.0);
            }
        }
        return clamp(t);
    }

    /** One day's drift (or {@code days} of them). */
    public static double drift(double now, double target, Settings s, int days) {
        double v = now;
        for (int d = 0; d < Math.min(days, 30); d++) {
            v += (target - v) * s.drift();
        }
        return clamp(v);
    }

    /** Day pass for a colony's citizens: every pair drifts. {@code homeOf} and {@code workOf} may be null. */
    /** How many others each citizen thinks over a day (in turn, so all are reached; keeps big colonies cheap). */
    static final int PER_DAY = 6;

    public static void day(Persons persons, List<Person> citizens, Map<UUID, UUID> homeOf, Map<UUID, UUID> workOf,
            int days) {
        day(persons, citizens, homeOf, workOf, days, 0);
    }

    public static void day(Persons persons, List<Person> citizens, Map<UUID, UUID> homeOf, Map<UUID, UUID> workOf,
            int days, long turn) {
        Settings s = settings();
        Map<UUID, Map<UUID, Double>> next = new HashMap<>();
        java.util.Set<UUID> here = new java.util.HashSet<>();
        citizens.forEach(c -> here.add(c.id()));
        int n = citizens.size();
        for (int i = 0; i < n; i++) {
            Person a = citizens.get(i);
            Map<UUID, Double> mine = new LinkedHashMap<>(a.get(TYPE).orElse(Map.of()));
            Kin ka = a.get(ColonistData.KIN).orElse(null);
            List<ResourceLocation> ta = a.get(ColonistData.TRAITS).orElse(List.of());
            int look = Math.min(n - 1, PER_DAY);
            for (int k = 0; k < look; k++) {
                int j = (int) ((i + 1 + Math.floorMod(turn * PER_DAY + k, Math.max(1, n - 1))) % n);
                if (i == j) {
                    continue;
                }
                Person b = citizens.get(j);
                Kin kb = b.get(ColonistData.KIN).orElse(null);
                boolean family = ka != null && kb != null && related(a.id(), ka, b.id(), kb);
                UUID ha = homeOf.get(a.id());
                UUID wa = workOf.get(a.id());
                Pair pair = new Pair(ka != null && kb != null && ka.race().equals(kb.race()),
                        ha != null && ha.equals(homeOf.get(b.id())), wa != null && wa.equals(workOf.get(b.id())),
                        family, ta, b.get(ColonistData.TRAITS).orElse(List.of()));
                // a pair is thought over every (n - 1) / PER_DAY days: catch up those days at once
                int span = Math.max(1, (n - 1 + PER_DAY - 1) / PER_DAY) * days;
                mine.put(b.id(), drift(mine.getOrDefault(b.id(), 0.0), target(pair, s), s, span));
            }
            // forget people who are no longer here
            if (mine.size() > n) {
                mine.keySet().retainAll(here);
            }
            next.put(a.id(), mine);
        }
        for (Person a : citizens) {
            persons.setQuiet(a, TYPE, next.get(a.id()));
        }
    }

    static boolean related(UUID a, Kin ka, UUID b, Kin kb) {
        if (ka.spouse().filter(b::equals).isPresent() || ka.parents().contains(b) || kb.parents().contains(a)) {
            return true;
        }
        for (UUID p : ka.parents()) {
            if (kb.parents().contains(p)) {
                return true;
            }
        }
        return false;
    }

    /** "Friend", "Rival"... for a number. */
    public static String word(double v) {
        if (v >= 60) {
            return "Close friend";
        }
        if (v >= 25) {
            return "Friend";
        }
        if (v > -25) {
            return "Acquaintance";
        }
        if (v > -60) {
            return "Dislikes";
        }
        return "Rival";
    }
}
