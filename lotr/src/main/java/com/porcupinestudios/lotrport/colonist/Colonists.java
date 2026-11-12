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
        NeoForge.EVENT_BUS.addListener(Colonists::onStarted);
        NeoForge.EVENT_BUS.addListener(Colonists::onRenamed);
    }

    /** A renamed person's body, if it is loaded, takes the new name at once. */
    private static void onRenamed(SocietyEvent.Renamed e) {
        for (net.minecraft.server.level.ServerLevel level : e.server().getAllLevels()) {
            if (level.getEntity(e.person().id()) instanceof com.porcupinestudios.lotrport.npc.LotrNpc npc) {
                npc.takeName(e.person().name());
                return;
            }
        }
    }

    /**
     * A name for someone whose NPC type has no name bank (LOTR's men): from their people's bank ({@code gondor_man}
     * takes {@code npc_names/gondor_male}), else Bree's; steady for the same person. Empty if no bank is loaded.
     */
    public static String nameFor(Person p) {
        String type = p.type().getPath();
        String people = type.contains("_") ? type.substring(0, type.lastIndexOf('_')) : type;
        List<String> bank = com.porcupinestudios.lotrport.npc.NpcData.names(LotrPort.id(people + "_male"));
        if (bank.isEmpty()) {
            bank = com.porcupinestudios.lotrport.npc.NpcData.names(LotrPort.id("bree_male"));
        }
        return bank.isEmpty() ? "" : bank.get(Math.floorMod(p.id().hashCode(), bank.size()));
    }

    /**
     * Citizens from saves made before colonists had traits get theirs once, when the server starts: a one-off cost
     * at load (tens of microseconds each) instead of inside the colony passes' tick budget.
     */
    private static void onStarted(net.neoforged.neoforge.event.server.ServerStartedEvent e) {
        MinecraftServer server = e.getServer();
        int n = 0;
        for (Person p : List.copyOf(Persons.get(server).all())) {
            if (p.alive() && p.has(CoreComponents.CITIZEN) && (!p.has(ColonistData.TRAITS) || p.name().isEmpty())) {
                settle(server, p);
                n++;
            }
        }
        if (n > 0) {
            LotrPort.LOG.info("[colonists] gave traits to {} citizens from before traits existed", n);
        }
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
        Optional<Kin> kin = p.get(ColonistData.KIN);
        return kin.isPresent() ? kin.get().race() : LotrPort.id(Races.of(p.type()));
    }

    /**
     * Gives a new citizen a name if they have none ({@link #nameFor}), then traits, an empty mood and kin, once
     * (someone who already has traits keeps them, so a citizen who leaves and comes back is the same person).
     */
    public static void settle(MinecraftServer server, Person p) {
        Persons persons = Persons.get(server);
        if (p.name().isEmpty()) {
            persons.rename(server, p, nameFor(p));
        }
        if (p.has(ColonistData.TRAITS)) {
            return;
        }
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

    /** The last households worked out: Assign and Life ask for the same colony on the same tick. */
    private static java.util.Set<UUID> cachedFor = java.util.Set.of();
    private static long cachedAt = Long.MIN_VALUE;
    private static Map<UUID, UUID> cached = Map.of();

    /**
     * Each citizen's household id (see {@link KinRules#household}); the dead and those elsewhere don't count. Kept
     * for the next call with the same citizens on the same tick (the colony's homes and needs passes), so it's worked
     * out once per colony per day. Server thread only.
     */
    public static Map<UUID, UUID> households(List<Person> citizens, long now) {
        java.util.Set<UUID> ids = new java.util.HashSet<>(citizens.size() * 2);
        for (Person p : citizens) {
            ids.add(p.id());
        }
        if (now == cachedAt && ids.equals(cachedFor)) {
            return cached;
        }
        Map<UUID, UUID> out = householdsNow(citizens, now);
        cachedFor = ids;
        cachedAt = now;
        cached = out;
        return out;
    }

    private static Map<UUID, UUID> householdsNow(List<Person> citizens, long now) {
        Map<UUID, Kin> kin = new HashMap<>();
        for (Person p : citizens) {
            if (p.alive()) {
                p.get(ColonistData.KIN).ifPresent(k -> kin.put(p.id(), k));
            }
        }
        Map<ResourceLocation, RaceDef> races = ColonistData.races();
        Map<UUID, UUID> out = new HashMap<>();
        for (Person p : citizens) {
            Kin k = kin.get(p.id());
            // no parents and no spouse: their own household (what KinRules would say, without the walk). Every
            // settler starts like this, so until families form this is the whole colony
            if (k == null || k.parents().isEmpty() && k.spouse().isEmpty()) {
                out.put(p.id(), p.id());
            } else {
                out.put(p.id(), KinRules.household(p.id(), id -> Optional.ofNullable(kin.get(id)),
                        r -> Optional.ofNullable(races.get(r)), now, dayTicks()));
            }
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
        // households per house, counted once per house (not a set per person)
        Map<UUID, Integer> householdsIn = new HashMap<>();
        sleepers.forEach((home, list) -> householdsIn.put(home, new java.util.HashSet<>(list).size()));
        ColonistSettings s = ColonistData.settings();
        Map<UUID, ResourceLocation> out = new HashMap<>();
        for (Person p : citizens) {
            Optional<WorkComponents.Home> home = p.get(WorkComponents.HOME);
            if (home.isEmpty()) {
                continue;
            }
            UUID mine = households.get(p.id());
            int others = householdsIn.getOrDefault(home.get().building(), 1) - 1;
            out.put(p.id(), KinRules.homeThought(s, size.getOrDefault(mine, 1) >= 2, others));
        }
        return out;
    }

    /** The household homes of a colony, for {@code Assign}: one {@link HouseRules.Resident} per citizen. */
    public static List<HouseRules.Resident> residents(List<Person> citizens, long now) {
        Map<UUID, UUID> households = households(citizens, now);
        List<HouseRules.Resident> out = new ArrayList<>();
        for (Person p : citizens) {
            out.add(new HouseRules.Resident(p.id(), households.get(p.id()),
                    p.get(WorkComponents.HOME).map(WorkComponents.Home::building),
                    p.get(ColonistData.ORDERS).flatMap(Orders::home)));
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
        int sum = 0;
        if (!mood.memories().isEmpty()) {
            Mood kept = MoodRules.forget(mood, defs, now);
            if (kept != mood) {
                persons.setQuiet(p, ColonistData.MOOD, kept);
            }
            sum += MoodRules.sum(defs, kept, List.of(), now);
        }
        // standing thoughts count once each (MoodRules.sum's rule), summed here without building a set: traits have
        // one or two thoughts at most
        List<ResourceLocation> traits = p.get(ColonistData.TRAITS).orElse(List.of());
        Map<ResourceLocation, TraitDef> traitDefs = ColonistData.traits();
        List<ResourceLocation> counted = traits.isEmpty() ? List.of() : new ArrayList<>(4);
        for (ResourceLocation t : traits) {
            TraitDef def = traitDefs.get(t);
            if (def == null) {
                continue;
            }
            for (ResourceLocation thought : def.thoughts()) {
                ThoughtDef td = defs.get(thought);
                if (td != null && !counted.contains(thought)) {
                    counted.add(thought);
                    sum += td.mood();
                }
            }
        }
        if (homeThought.isPresent() && !counted.contains(homeThought.get())) {
            ThoughtDef td = defs.get(homeThought.get());
            sum += td == null ? 0 : td.mood();
        }
        // (counted may be the empty immutable list: check the home thought by hand rather than adding to it)
        Optional<ResourceLocation> ration = rationThought(p);
        if (ration.isPresent() && !counted.contains(ration.get()) && !ration.equals(homeThought)) {
            ThoughtDef td = defs.get(ration.get());
            sum += td == null ? 0 : td.mood();
        }
        // sleep and schedule thoughts are their own (tired, odd hours, leisure): no repeats to check
        Optional<Rest> rest = p.get(ColonistData.REST);
        if (rest.isPresent()) {
            sum += ScheduleRules.mood(rest.get(), rhythm(p), ColonistData.sleep(), defs);
        }
        return sum;
    }

    // ---------------------------------------------------------------- sleep and schedules

    /** The race's hours and wants. */
    public static Rhythm rhythm(Person p) {
        RaceDef race = ColonistData.races().get(raceOf(p));
        return race == null ? Rhythm.DEFAULT : race.rhythm();
    }

    /** The colony's schedules (the standard one alone if it has none saved). */
    public static Schedules schedules(com.porcupinestudios.lotrport.colony.Colony c) {
        return c.get(Schedules.TYPE).orElseGet(Schedules::standard);
    }

    /** The schedule this colonist keeps. */
    public static Schedule scheduleOf(Schedules all, Person p) {
        Optional<Orders> o = p.get(ColonistData.ORDERS);
        return o.isEmpty() ? all.first() : all.orFirst(o.get().schedule());
    }

    /** What decides a day's {@link Rest}: colonists on one schedule, of one race, housed alike, end up the same. */
    private record RestKey(Schedule schedule, Rhythm rhythm, boolean home, double start, int days) {}

    /**
     * Runs the colonist's day(s) by their schedule and keeps the result ({@link Rest}); the colony's needs pass.
     * {@code memo} (one per pass) shares the answer between colonists in the same case: after a day or two everyone
     * on a schedule has the same energy, so a colony of 150 works out a handful of days, not 150.
     */
    public static Rest restDays(Persons persons, Person p, Schedules all, boolean home, int days,
            Map<Object, Rest> memo) {
        Rest start = p.get(ColonistData.REST).orElse(Rest.FRESH);
        RestKey key = new RestKey(scheduleOf(all, p), rhythm(p), home, start.energy(), days);
        Rest r = memo.get(key);
        if (r == null) {
            r = ScheduleRules.days(key.schedule(), key.rhythm(), start, home, ColonistData.sleep(), days);
            memo.put(key, r);
        }
        if (!r.equals(start)) {
            persons.setQuiet(p, ColonistData.REST, r);
        }
        return r;
    }

    /** How much of a standard day's work their schedule and energy give (1 = normal; work output multiplies it). */
    public static double workHours(Person p) {
        Optional<Rest> r = p.get(ColonistData.REST);
        return r.isEmpty() ? 1 : ScheduleRules.workFactor(r.get(), ColonistData.sleep());
    }

    /** What the colonist is doing at this clock hour, by their schedule (Any by their last day's energy). */
    public static Schedule.Block activity(Schedules all, Person p, int hour) {
        Rest r = p.get(ColonistData.REST).orElse(Rest.FRESH);
        return ScheduleRules.resolve(scheduleOf(all, p).at(hour), r.energy(), 0, rhythm(p), ColonistData.sleep());
    }

    private static List<ResourceLocation> restThoughts(Person p) {
        Optional<Rest> r = p.get(ColonistData.REST);
        return r.isEmpty() ? List.of() : ScheduleRules.thoughts(r.get(), rhythm(p), ColonistData.sleep());
    }

    // ---------------------------------------------------------------- the task queue

    /**
     * What a colonist is like right now, for their task queue: this hour's block, energy worked out to this hour
     * from the day's start, food run down by the share of the day gone, and the facts tasks may require.
     *
     * @param foodPlace whether the colony has somewhere to eat (a storehouse or kitchen)
     * @param dayTime the level's day time
     */
    public static TaskRules.State taskState(Person p, Schedules all, boolean foodPlace, long dayTime) {
        int hour = Schedule.hourOf(dayTime);
        Schedule schedule = scheduleOf(all, p);
        Rhythm rhythm = rhythm(p);
        boolean home = p.has(WorkComponents.HOME);
        Rest rest = p.get(ColonistData.REST).orElse(Rest.FRESH);
        double energy = TaskRules.energyAt(schedule, rhythm, rest.energy(), home, ColonistData.sleep(), hour);
        WorkComponents.Needs n = p.get(WorkComponents.NEEDS).orElse(WorkComponents.Needs.NEW);
        double share = Math.floorMod(dayTime, 24000L) / 24000.0;
        double food = Math.max(0, n.food() - com.porcupinestudios.lotrport.colony.work.LifeSettings.get().foodPerDay()
                * foodRate(p) * share);
        java.util.Set<String> facts = new java.util.HashSet<>(6);
        if (p.has(WorkComponents.JOB)) {
            facts.add("job");
        }
        if (n.unpaid() <= 0) {
            facts.add("paid");
        }
        if (orders(p).works()) {
            facts.add("works");
        }
        if (foodPlace) {
            facts.add("food_place");
        }
        if (home) {
            facts.add("home");
        }
        return new TaskRules.State(schedule.at(hour), energy, food, facts);
    }

    /** Their task queue right now (open tasks by priority, then those waiting and why). */
    public static List<TaskRules.Entry> tasks(Person p, Schedules all, boolean foodPlace, long dayTime) {
        return TaskRules.queue(ColonistData.tasks(), taskState(p, all, foodPlace, dayTime));
    }

    // ---------------------------------------------------------------- leaving

    /** Whether anyone is linked to them: parents, a spouse or children. */
    public static boolean hasFamily(Person p) {
        Optional<Kin> k = p.get(ColonistData.KIN);
        return k.isPresent() && (!k.get().parents().isEmpty() || k.get().spouse().isPresent()
                || !k.get().children().isEmpty());
    }

    /**
     * A citizen leaves their colony ({@link Left#ABANDONED} or {@link Left#BANISHED}). Someone with family is kept,
     * owned by nobody and marked {@link Left}, so their family tree still shows them; anyone else is forgotten, as
     * before. Their body, if loaded, goes.
     */
    public static void depart(MinecraftServer server, com.porcupinestudios.lotrport.colony.Colony colony, Person p,
            String why, long now) {
        for (net.minecraft.server.level.ServerLevel level : server.getAllLevels()) {
            net.minecraft.world.entity.Entity body = level.getEntity(p.id());
            if (body != null) {
                body.discard();
            }
        }
        Persons persons = Persons.get(server);
        if (!hasFamily(p)) {
            persons.remove(server, p.id());
            return;
        }
        persons.setQuiet(p, WorkComponents.JOB, null);
        persons.setQuiet(p, WorkComponents.HOME, null);
        persons.setQuiet(p, ColonistData.ORDERS, null);
        persons.setQuiet(p, CoreComponents.CITIZEN, null);
        persons.setQuiet(p, ColonistData.LEFT, new Left(colony.id(), why, now));
        persons.setOwner(server, p, com.porcupinestudios.lotrport.society.Owner.NONE, why);
    }

    public static Orders orders(Person p) {
        return p.get(ColonistData.ORDERS).orElse(Orders.DEFAULT);
    }

    /** The standing thought of this person's ration, if it has one. */
    static Optional<ResourceLocation> rationThought(Person p) {
        Optional<Orders> o = p.get(ColonistData.ORDERS);
        return o.isEmpty() ? Optional.empty() : Rations.get(o.get().ration()).thought();
    }

    /** One line of what is on a colonist's mind, for the citizen page. */
    public record Thought(ResourceLocation id, int mood, int stacks, long ticksLeft) {}

    /**
     * Everything on this colonist's mind that moves their mood, as {@link #moodToday} counts it: standing thoughts
     * (traits, home, ration; {@code ticksLeft} -1) and live memories, the strongest first.
     */
    public static List<Thought> thoughts(Person p, Optional<ResourceLocation> homeThought, long now) {
        Map<ResourceLocation, ThoughtDef> defs = ColonistData.thoughts();
        List<Thought> out = new ArrayList<>();
        java.util.Set<ResourceLocation> standing = new java.util.LinkedHashSet<>();
        for (ResourceLocation t : p.get(ColonistData.TRAITS).orElse(List.of())) {
            TraitDef def = ColonistData.traits().get(t);
            if (def != null) {
                standing.addAll(def.thoughts());
            }
        }
        homeThought.ifPresent(standing::add);
        rationThought(p).ifPresent(standing::add);
        standing.addAll(restThoughts(p));
        for (ResourceLocation id : standing) {
            ThoughtDef def = defs.get(id);
            if (def != null) {
                out.add(new Thought(id, def.mood(), 1, -1));
            }
        }
        for (Mood.Memory m : p.get(ColonistData.MOOD).orElse(Mood.EMPTY).memories()) {
            ThoughtDef def = defs.get(m.thought());
            long left = def == null ? 0 : def.durationTicks() - (now - m.since());
            if (def != null && left > 0) {
                out.add(new Thought(m.thought(), def.mood() * m.stacks(), m.stacks(), left));
            }
        }
        out.sort(java.util.Comparator.comparingInt((Thought t) -> -Math.abs(t.mood())).thenComparing(
                t -> t.id().toString()));
        return out;
    }

    /** How fast this person's food runs down: race times traits times ration (1 = normal). */
    public static double foodRate(Person p) {
        double rate = TraitRules.needRate(ColonistData.traits(), p.get(ColonistData.TRAITS).orElse(List.of()),
                ColonistData.FOOD);
        Optional<Orders> orders = p.get(ColonistData.ORDERS);
        if (orders.isPresent()) {
            rate *= Rations.get(orders.get().ration()).foodRate();
        }
        RaceDef race = ColonistData.races().get(raceOf(p));
        return race == null ? rate : rate * race.needRates().getOrDefault(ColonistData.FOOD, 1.0);
    }

    /** How much this person's traits speed their work (1 = normal). */
    public static double workSpeed(Person p) {
        return TraitRules.workSpeed(ColonistData.traits(), p.get(ColonistData.TRAITS).orElse(List.of()));
    }
}
