package com.porcupinestudios.lotrport.colonist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** The colonist rules (traits, needs, mood, kin and households) on made-up data: no game needed. */
class ColonistRulesTest {
    private static final int DAY = 24000;
    private static final long NOW = 100L * DAY;
    private static final ColonistSettings SETTINGS = new ColonistSettings(3, 0.5, 50, 15, 30, 45, 80, id("man"),
            id("own_home"), id("shared_home"), id("barracks"), id("homeless"), 3);

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("lotr_port", path);
    }

    private static TraitDef trait(TraitDef.Kind kind, String scale, int weight, double heritable, double speed) {
        return new TraitDef(kind, Optional.ofNullable(scale), weight, heritable, speed, Map.of(), List.of(),
                Optional.empty(), List.of());
    }

    private static Map<ResourceLocation, TraitDef> traits() {
        Map<ResourceLocation, TraitDef> traits = new HashMap<>();
        traits.put(id("industrious"), trait(TraitDef.Kind.POSITIVE, "industry", 10, 0.3, 1.25));
        traits.put(id("lazy"), trait(TraitDef.Kind.NEGATIVE, "industry", 10, 0.3, 0.75));
        traits.put(id("strong"), trait(TraitDef.Kind.POSITIVE, null, 10, 0.5, 1.1));
        traits.put(id("greedy"), new TraitDef(TraitDef.Kind.NEGATIVE, Optional.empty(), 10, 0.2, 1,
                Map.of(id("food"), 1.5), List.of(id("wants_more")), Optional.of("binge"), List.of(id("ascetic"))));
        traits.put(id("ascetic"), trait(TraitDef.Kind.NEUTRAL, null, 5, 0.1, 1));
        traits.put(id("royal_blood"), trait(TraitDef.Kind.POSITIVE, null, 0, 1.0, 1));
        return traits;
    }

    @Test
    void everyRollHasOneGoodAndOneBadTraitAndNoClash() {
        Map<ResourceLocation, TraitDef> traits = traits();
        for (long seed = 0; seed < 2000; seed++) {
            List<ResourceLocation> rolled = TraitRules.roll(traits, new Random(seed), SETTINGS);
            assertTrue(rolled.size() >= 2 && rolled.size() <= 3, "count: " + rolled);
            assertTrue(rolled.stream().anyMatch(t -> traits.get(t).kind() == TraitDef.Kind.POSITIVE), "" + rolled);
            assertTrue(rolled.stream().anyMatch(t -> traits.get(t).kind() == TraitDef.Kind.NEGATIVE), "" + rolled);
            assertFalse(rolled.contains(id("industrious")) && rolled.contains(id("lazy")), "same scale: " + rolled);
            assertFalse(rolled.contains(id("greedy")) && rolled.contains(id("ascetic")), "conflict: " + rolled);
            assertFalse(rolled.contains(id("royal_blood")), "a weight 0 trait was rolled");
            assertEquals(rolled.size(), new HashSet<>(rolled).size(), "repeat: " + rolled);
            assertEquals(rolled, TraitRules.roll(traits, new Random(seed), SETTINGS), "same seed, other colonist");
        }
    }

    @Test
    void childrenInheritByChance() {
        Map<ResourceLocation, TraitDef> traits = traits();
        List<List<ResourceLocation>> parents = List.of(List.of(id("royal_blood"), id("lazy")), List.of(id("strong")));
        for (long seed = 0; seed < 500; seed++) {
            List<ResourceLocation> child = TraitRules.inherit(traits, parents, new Random(seed), SETTINGS);
            assertTrue(child.contains(id("royal_blood")), "heritable 1 did not pass down: " + child);
            assertTrue(child.size() <= 3, "too many: " + child);
            assertFalse(child.contains(id("lazy")) && child.contains(id("industrious")), "same scale: " + child);
        }
    }

    @Test
    void traitsChangeWorkNeedsThoughtsAndBreaks() {
        Map<ResourceLocation, TraitDef> traits = traits();
        assertEquals(1.375, TraitRules.workSpeed(traits, List.of(id("industrious"), id("strong"))), 1e-9);
        assertEquals(1.5, TraitRules.needRate(traits, List.of(id("greedy")), id("food")), 1e-9);
        assertEquals(1.0, TraitRules.needRate(traits, List.of(id("strong")), id("food")), 1e-9);
        assertEquals(List.of(id("wants_more")), TraitRules.thoughts(traits, List.of(id("greedy"))));
        assertEquals("binge", TraitRules.onBreak(traits, List.of(id("strong"), id("greedy")), "idle"));
        assertEquals("idle", TraitRules.onBreak(traits, List.of(id("strong")), "idle"));
    }

    @Test
    void needsFallOnlyWhenRead() {
        NeedDef food = new NeedDef(40, 80, List.of(new NeedDef.Level(40, id("hungry")),
                new NeedDef.Level(15, id("starving"))));
        Map<ResourceLocation, NeedDef> defs = new LinkedHashMap<>();
        defs.put(id("food"), food);
        Needs needs = NeedRules.start(defs, 1000);
        assertEquals(80, NeedRules.value(needs, id("food"), food, 1, 1000, DAY), 1e-9);
        assertEquals(40, NeedRules.value(needs, id("food"), food, 1, 1000 + DAY, DAY), 1e-9);
        assertEquals(20, NeedRules.value(needs, id("food"), food, 1.5, 1000 + DAY, DAY), 1e-9);
        assertEquals(0, NeedRules.value(needs, id("food"), food, 1, 1000 + 10L * DAY, DAY), 1e-9);

        assertTrue(NeedRules.thoughts(defs, needs, Map.of(), 1000, DAY).isEmpty());
        assertEquals(List.of(id("hungry")), NeedRules.thoughts(defs, needs, Map.of(), 1000 + DAY + 1, DAY));
        assertEquals(List.of(id("starving")), NeedRules.thoughts(defs, needs, Map.of(), 1000 + 2L * DAY, DAY));

        Needs fed = NeedRules.change(needs, id("food"), food, 1, 1000 + DAY, DAY, 50);
        assertEquals(90, NeedRules.value(fed, id("food"), food, 1, 1000 + DAY, DAY), 1e-9);
        Needs stuffed = NeedRules.change(fed, id("food"), food, 1, 1000 + DAY, DAY, 50);
        assertEquals(100, NeedRules.value(stuffed, id("food"), food, 1, 1000 + DAY, DAY), 1e-9);
    }

    @Test
    void moodIsTheSumOfThoughts() {
        Map<ResourceLocation, ThoughtDef> thoughts = new HashMap<>();
        thoughts.put(id("wedding"), new ThoughtDef(8, 3L * DAY, 1));
        thoughts.put(id("saw_death"), new ThoughtDef(-6, 2L * DAY, 3));
        thoughts.put(id("hungry"), new ThoughtDef(-10, 0, 1));
        thoughts.put(id("own_home"), new ThoughtDef(12, 0, 1));
        thoughts.put(id("barracks"), new ThoughtDef(-15, 0, 1));

        Mood mood = MoodRules.remember(Mood.EMPTY, thoughts, id("wedding"), 0);
        mood = MoodRules.remember(mood, thoughts, id("wedding"), 10);
        assertEquals(58, MoodRules.score(SETTINGS, thoughts, mood, List.of(), 20), "stack limit 1 counts once");
        for (int i = 0; i < 5; i++) {
            mood = MoodRules.remember(mood, thoughts, id("saw_death"), 100);
        }
        assertEquals(40, MoodRules.score(SETTINGS, thoughts, mood, List.of(), 200), "three deaths stack, no more");
        assertEquals(42, MoodRules.score(SETTINGS, thoughts, mood,
                List.of(id("hungry"), id("hungry"), id("own_home")), 200), "standing thoughts count once each");
        assertSame(mood, MoodRules.remember(mood, thoughts, id("hungry"), 0), "a standing thought was remembered");

        assertEquals(58, MoodRules.score(SETTINGS, thoughts, mood, List.of(), 100 + 2L * DAY), "deaths did not fade");
        assertEquals(1, MoodRules.forget(mood, thoughts, 100 + 2L * DAY).memories().size());
        assertTrue(MoodRules.forget(mood, thoughts, 10 + 3L * DAY).memories().isEmpty());
        assertEquals(25, MoodRules.score(SETTINGS, thoughts, Mood.EMPTY, List.of(id("barracks"), id("hungry")), 0));

        assertEquals(MoodState.MAJOR_BREAK, MoodRules.state(SETTINGS, 10));
        assertEquals(MoodState.MINOR_BREAK, MoodRules.state(SETTINGS, 20));
        assertEquals(MoodState.UNHAPPY, MoodRules.state(SETTINGS, 40));
        assertEquals(MoodState.CONTENT, MoodRules.state(SETTINGS, 60));
        assertEquals(MoodState.INSPIRED, MoodRules.state(SETTINGS, 80));
    }

    private static final RaceDef MAN = new RaceDef(List.of(), 1, 18, false, Map.of(), 18, 50);
    private static final RaceDef HOBBIT = new RaceDef(List.of("hobbit"), 0.75, 33, true, Map.of(), 33, 70);
    private static final Map<ResourceLocation, RaceDef> RACES = Map.of(id("man"), MAN, id("hobbit"), HOBBIT);
    private static final Function<ResourceLocation, Optional<RaceDef>> RACE = r -> Optional.ofNullable(RACES.get(r));

    private static Kin kin(String race, long ageDays, List<UUID> parents, UUID spouse, List<UUID> children) {
        return new Kin(id(race), NOW - ageDays * DAY, parents, Optional.ofNullable(spouse), children);
    }

    @Test
    void raceAndAge() {
        assertEquals(id("hobbit"), RaceRules.raceOf(RACES, id("bree_hobbit"), id("man")));
        assertEquals(id("man"), RaceRules.raceOf(RACES, id("gondor_soldier"), id("man")));
        assertEquals(18, KinRules.age(kin("man", 18, List.of(), null, List.of()), MAN, NOW, DAY), 1e-9);
        assertTrue(KinRules.adult(kin("man", 18, List.of(), null, List.of()), MAN, NOW, DAY), "18 days is age 18");
        assertFalse(KinRules.adult(kin("man", 17, List.of(), null, List.of()), MAN, NOW, DAY));
        assertEquals(NOW - 30L * DAY, KinRules.bornFor(30, MAN, NOW, DAY));
    }

    @Test
    void aFamilySharesAHouseWithoutPenaltyUntilTheChildComesOfAge() {
        UUID father = new UUID(0, 1);
        UUID mother = new UUID(0, 2);
        UUID child = new UUID(0, 3);
        UUID grown = new UUID(0, 4);
        UUID stranger = new UUID(0, 5);
        UUID another = new UUID(0, 6);
        Map<UUID, Kin> kin = new HashMap<>();
        kin.put(father, kin("man", 40, List.of(), mother, List.of(child, grown)));
        kin.put(mother, kin("man", 38, List.of(), father, List.of(child, grown)));
        kin.put(child, kin("man", 6, List.of(father, mother), null, List.of()));
        kin.put(grown, kin("man", 19, List.of(father, mother), null, List.of()));
        kin.put(stranger, kin("man", 30, List.of(), null, List.of()));
        Function<UUID, Optional<Kin>> lookup = u -> Optional.ofNullable(kin.get(u));

        UUID home = KinRules.household(father, lookup, RACE, NOW, DAY);
        assertEquals(home, KinRules.household(mother, lookup, RACE, NOW, DAY));
        assertEquals(home, KinRules.household(child, lookup, RACE, NOW, DAY));
        assertNotEquals(home, KinRules.household(grown, lookup, RACE, NOW, DAY), "a grown child of Men stayed");
        assertNotEquals(home, KinRules.household(child, lookup, RACE, NOW + 13L * DAY, DAY),
                "the child did not leave the household on coming of age");

        assertEquals(id("own_home"), KinRules.homeThought(SETTINGS, home, List.of(home, home, home)),
                "a family in one house was penalised");
        UUID strangers = KinRules.household(stranger, lookup, RACE, NOW, DAY);
        assertEquals(id("shared_home"), KinRules.homeThought(SETTINGS, home, List.of(home, home, strangers)));
        assertEquals(id("barracks"), KinRules.homeThought(SETTINGS, home, List.of(home, strangers, grown, another)));
        assertEquals(id("homeless"), KinRules.homeThought(SETTINGS, home, List.of()));

        kin.remove(father);
        assertEquals(mother, KinRules.household(mother, lookup, RACE, NOW, DAY));
        assertEquals(mother, KinRules.household(child, lookup, RACE, NOW, DAY), "a widow and her child were split");
    }

    @Test
    void hobbitsKeepThreeGenerationsUnderOneRoof() {
        UUID grandfather = new UUID(1, 1);
        UUID grandmother = new UUID(1, 2);
        UUID son = new UUID(1, 3);
        UUID wife = new UUID(1, 0);
        UUID baby = new UUID(1, 5);
        Map<UUID, Kin> kin = new HashMap<>();
        kin.put(grandfather, kin("hobbit", 90, List.of(), grandmother, List.of(son)));
        kin.put(grandmother, kin("hobbit", 88, List.of(), grandfather, List.of(son)));
        kin.put(son, kin("hobbit", 40, List.of(grandfather, grandmother), wife, List.of(baby)));
        kin.put(wife, kin("hobbit", 39, List.of(), son, List.of(baby)));
        kin.put(baby, kin("hobbit", 2, List.of(son, wife), null, List.of()));
        Function<UUID, Optional<Kin>> lookup = u -> Optional.ofNullable(kin.get(u));
        Set<UUID> households = new HashSet<>();
        kin.keySet().forEach(u -> households.add(KinRules.household(u, lookup, RACE, NOW, DAY)));
        assertEquals(1, households.size(), "hobbit kin were split into " + households.size() + " households");
    }
}
