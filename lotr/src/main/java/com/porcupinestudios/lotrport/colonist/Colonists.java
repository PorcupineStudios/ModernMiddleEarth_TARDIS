package com.porcupinestudios.lotrport.colonist;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.colony.ColonyData;
import com.porcupinestudios.lotrport.colony.work.Races;
import com.porcupinestudios.lotrport.colony.work.WorkComponents;
import com.porcupinestudios.lotrport.society.CoreComponents;
import com.porcupinestudios.lotrport.society.Person;
import com.porcupinestudios.lotrport.society.Persons;
import com.porcupinestudios.lotrport.society.SocietyEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Colonists as people with traits, mood memories and kin, on the society core: the glue between the plain rules of
 * this package and {@link Persons}. Hooks the colony's own systems call:
 * <ul>
 *   <li>{@code Assign.run}: {@link #households} for {@link HouseRules}, so families sleep under one roof.</li>
 *   <li>{@code Life.needs}: {@link #homeThoughts} once per colony, then {@link #moodToday} per citizen is added to the
 *       happiness target, and {@link #foodRate} scales how fast food runs down.</li>
 *   <li>{@code Life.work}: {@link #workSpeed} scales what a worker makes.</li>
 * </ul>
 * A citizen gets traits, an empty mood and kin the moment they become a citizen ({@link #settle}, from the
 * {@code citizen} component event), seeded by their id so the same settler always turns out the same.
 */
public final class Colonists {
    public static final ResourceLocation KIN_DIED = LotrPort.id("kin_died");

    private Colonists() {}

    public static void register() {
        ColonistData.register();
        NeoForge.EVENT_BUS.addListener(Colonists::onComponent);
        NeoForge.EVENT_BUS.addListener(Colonists::onDied);
    }

    private static void onComponent(SocietyEvent.ComponentChanged e) {
        if (e.type() == CoreComponents.CITIZEN && e.person().has(CoreComponents.CITIZEN)) {
            settle(e.server(), e.person());
        }
    }

    /** The dead person's parents, spouse and children remember the loss. */
    private static void onDied(SocietyEvent.Died e) {
        Optional<Kin> kin = e.person().get(ColonistData.KIN);
        if (kin.isEmpty()) {
            return;
        }
        List<UUID> family = new ArrayList<>(kin.get().parents());
        kin.get().spouse().ifPresent(family::add);
        family.addAll(kin.get().children());
        Persons persons = Persons.get(e.server());
        long now = e.server().overworld().getGameTime();
        for (UUID id : family) {
            persons.get(id).filter(Person::alive).ifPresent(p -> remember(persons, p, KIN_DIED, now));
        }
    }

    static int dayTicks() {
        return Math.max(1, ColonyData.settings().dayTicks());
    }

    /** The race id a person's kin and race data use: their NPC type's race ({@code colony_race/}). */
    public static ResourceLocation raceOf(Person p) {
        return p.get(ColonistData.KIN).map(Kin::race).orElse(LotrPort.id(Races.of(p.type())));
    }

    /**
     * Gives a new citizen traits, an empty mood and kin, once (someone who already has traits keeps them, so a
     * citizen who leaves and comes back is the same person).
     */
    public static void settle(MinecraftServer server, Person p) {
        if (p.has(ColonistData.TRAITS)) {
            return;
        }
        Persons persons = Persons.get(server);
        long now = server.overworld().getGameTime();
        Random random = new Random(p.id().getMostSignificantBits() ^ p.id().getLeastSignificantBits());
        persons.setQuiet(p, ColonistData.TRAITS, TraitRules.roll(ColonistData.traits(), random,
                ColonistData.settings()));
        if (!p.has(ColonistData.MOOD)) {
            persons.setQuiet(p, ColonistData.MOOD, Mood.EMPTY);
        }
        if (!p.has(ColonistData.KIN)) {
            ResourceLocation race = LotrPort.id(Races.of(p.type()));
            RaceDef def = ColonistData.races().get(race);
            long born = now;
            if (def != null) {
                int span = Math.max(0, def.settlerAgeMax() - def.settlerAgeMin());
                born = KinRules.bornFor(def.settlerAgeMin() + random.nextInt(span + 1), def, now, dayTicks());
            }
            persons.setQuiet(p, ColonistData.KIN, new Kin(race, born, List.of(), Optional.empty(), List.of()));
        }
    }

    /** Remembers something that happened to this person (a death in the family, a wedding). */
    public static void remember(Persons persons, Person p, ResourceLocation thought, long now) {
        Mood mood = p.get(ColonistData.MOOD).orElse(Mood.EMPTY);
        persons.setQuiet(p, ColonistData.MOOD, MoodRules.remember(mood, ColonistData.thoughts(), thought, now));
    }

    /** Age in years, if the person has kin data. */
    public static Optional<Double> age(Person p, long now) {
        return p.get(ColonistData.KIN).flatMap(k -> Optional.ofNullable(ColonistData.races().get(k.race()))
                .map(r -> KinRules.age(k, r, now, dayTicks())));
    }

    // ---------------------------------------------------------------- households and homes

    /** Each citizen's household id (see {@link KinRules#household}); the dead and those elsewhere don't count. */
    public static Map<UUID, UUID> households(List<Person> citizens, long now) {
        Map<UUID, Kin> kin = new HashMap<>();
        for (Person p : citizens) {
            if (p.alive()) {
                p.get(ColonistData.KIN).ifPresent(k -> kin.put(p.id(), k));
            }
        }
        Map<ResourceLocation, RaceDef> races = ColonistData.races();
        Map<UUID, UUID> out = new HashMap<>();
        for (Person p : citizens) {
            out.put(p.id(), KinRules.household(p.id(), id -> Optional.ofNullable(kin.get(id)),
                    r -> Optional.ofNullable(races.get(r)), now, dayTicks()));
        }
        return out;
    }

    /** What each citizen with a home thinks of who they share it with (absent: no home, no thought). */
    public static Map<UUID, ResourceLocation> homeThoughts(List<Person> citizens, long now) {
        Map<UUID, UUID> households = households(citizens, now);
        Map<UUID, Integer> size = new HashMap<>();
        households.values().forEach(h -> size.merge(h, 1, Integer::sum));
        Map<UUID, List<UUID>> sleepers = new HashMap<>();
        for (Person p : citizens) {
            p.get(WorkComponents.HOME).ifPresent(h -> sleepers.computeIfAbsent(h.building(), k -> new ArrayList<>())
                    .add(households.get(p.id())));
        }
        ColonistSettings s = ColonistData.settings();
        Map<UUID, ResourceLocation> out = new HashMap<>();
        for (Person p : citizens) {
            UUID mine = households.get(p.id());
            List<UUID> mates = p.get(WorkComponents.HOME).map(h -> sleepers.get(h.building())).orElse(List.of());
            KinRules.homeThought(s, mine, size.getOrDefault(mine, 1), mates).ifPresent(t -> out.put(p.id(), t));
        }
        return out;
    }

    /** The household homes of a colony, for {@code Assign}: one {@link HouseRules.Resident} per citizen. */
    public static List<HouseRules.Resident> residents(List<Person> citizens, long now) {
        Map<UUID, UUID> households = households(citizens, now);
        List<HouseRules.Resident> out = new ArrayList<>();
        for (Person p : citizens) {
            out.add(new HouseRules.Resident(p.id(), households.get(p.id()),
                    p.get(WorkComponents.HOME).map(WorkComponents.Home::building)));
        }
        return out;
    }

    // ---------------------------------------------------------------- what the colony's systems add

    /**
     * What this citizen's thoughts add to their happiness target today: their traits' standing thoughts, their home
     * thought and live memories. Faded memories are forgotten here (once a colony day).
     */
    public static double moodToday(Persons persons, Person p, Optional<ResourceLocation> homeThought, long now) {
        Map<ResourceLocation, ThoughtDef> defs = ColonistData.thoughts();
        Mood mood = p.get(ColonistData.MOOD).orElse(Mood.EMPTY);
        Mood kept = MoodRules.forget(mood, defs, now);
        if (kept != mood) {
            persons.setQuiet(p, ColonistData.MOOD, kept);
        }
        List<ResourceLocation> standing = new ArrayList<>(TraitRules.thoughts(ColonistData.traits(),
                p.get(ColonistData.TRAITS).orElse(List.of())));
        homeThought.ifPresent(standing::add);
        return MoodRules.sum(defs, kept, standing, now);
    }

    /** How fast this person's food runs down: race times traits (1 = normal). */
    public static double foodRate(Person p) {
        double rate = TraitRules.needRate(ColonistData.traits(), p.get(ColonistData.TRAITS).orElse(List.of()),
                ColonistData.FOOD);
        RaceDef race = ColonistData.races().get(raceOf(p));
        return race == null ? rate : rate * race.needRates().getOrDefault(ColonistData.FOOD, 1.0);
    }

    /** How much this person's traits speed their work (1 = normal). */
    public static double workSpeed(Person p) {
        return TraitRules.workSpeed(ColonistData.traits(), p.get(ColonistData.TRAITS).orElse(List.of()));
    }
}
