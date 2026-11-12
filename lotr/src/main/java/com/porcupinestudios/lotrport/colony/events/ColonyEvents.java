package com.porcupinestudios.lotrport.colony.events;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.colonist.ColonistData;
import com.porcupinestudios.lotrport.colonist.Colonists;
import com.porcupinestudios.lotrport.colonist.Kin;
import com.porcupinestudios.lotrport.colonist.Opinions;
import com.porcupinestudios.lotrport.colony.Colonies;
import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.society.CoreComponents;
import com.porcupinestudios.lotrport.society.Person;
import com.porcupinestudios.lotrport.society.Persons;
import com.porcupinestudios.lotrport.society.Society;
import com.porcupinestudios.lotrport.society.sim.ColonySystem;
import com.porcupinestudios.lotrport.society.sim.ColonySystem.Cadence;
import com.porcupinestudios.lotrport.society.sim.ColonySystems;
import com.porcupinestudios.lotrport.society.sim.SystemContext;
import com.porcupinestudios.lotrport.society.sim.Tier;
import com.porcupinestudios.lotrport.society.ComponentType;
import com.porcupinestudios.ringbearer.api.data.CodecJsonLoader;
import com.porcupinestudios.ringbearer.api.history.HistoryEntry;
import com.porcupinestudios.ringbearer.api.history.HistoryLog;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/**
 * Colony events: things that happen in a colony by themselves, once a day at most each. Each is data
 * ({@code colony_event/<id>.json}: its {@code kind}, chance a day, days between, and the kind's numbers); kinds are
 * code ({@link Kind}, {@link #registerKind}): weddings, festivals, births and quarrels ship, another system adds its
 * own. An event that happens leaves memories ({@code colonist_thought/}), changes opinions, goes into the history log
 * ({@code colony_event}) and tells the owner. {@code /lotr colony event <id>} makes one happen now.
 */
public final class ColonyEvents {
    public static final ResourceLocation SYSTEM = LotrPort.id("events");
    public static final ResourceLocation HISTORY = LotrPort.id("colony_event");

    /**
     * An event as data. Not every kind uses every number.
     *
     * @param chance chance a day it is tried (0..1)
     * @param cooldownDays days after it happens before it can again (in this colony)
     * @param minOpinion opinions it needs (weddings: both ways at least this; quarrels: at most minus this)
     * @param thought memory for those it is about
     * @param guestThought memory for everyone else in the colony
     * @param opinionChange what it adds to the opinions between those it is about (festivals: random pairs)
     * @param minPeople citizens it needs
     */
    public record Def(ResourceLocation kind, double chance, int cooldownDays, double minOpinion,
            Optional<ResourceLocation> thought, Optional<ResourceLocation> guestThought, double opinionChange,
            int minPeople, String name) {
        public static final Codec<Def> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("kind").forGetter(Def::kind),
                Codec.DOUBLE.optionalFieldOf("chance", 0.1).forGetter(Def::chance),
                Codec.INT.optionalFieldOf("cooldown_days", 3).forGetter(Def::cooldownDays),
                Codec.DOUBLE.optionalFieldOf("min_opinion", 50.0).forGetter(Def::minOpinion),
                ResourceLocation.CODEC.optionalFieldOf("thought").forGetter(Def::thought),
                ResourceLocation.CODEC.optionalFieldOf("guest_thought").forGetter(Def::guestThought),
                Codec.DOUBLE.optionalFieldOf("opinion_change", 10.0).forGetter(Def::opinionChange),
                Codec.INT.optionalFieldOf("min_people", 2).forGetter(Def::minPeople),
                Codec.STRING.optionalFieldOf("name", "").forGetter(Def::name))
                .apply(i, Def::new));
    }

    /** What happened: those it was about, and a line for the owner (empty: nothing happened). */
    public record Outcome(List<UUID> about, Optional<Component> line) {
        public static final Outcome NOTHING = new Outcome(List.of(), Optional.empty());
    }

    /** One run of an event in a colony. */
    public record Ctx(MinecraftServer server, Colony colony, ResourceLocation id, Def def, List<Person> citizens,
            Random random, long now) {}

    /** A kind of event: what it does. */
    @FunctionalInterface
    public interface Kind {
        Outcome run(Ctx ctx);
    }

    private static final Map<ResourceLocation, Kind> KINDS = new LinkedHashMap<>();
    private static final CodecJsonLoader<Def> DEFS = new CodecJsonLoader<>("colony_event", Def.CODEC);

    /** Per colony: the day each event last happened. */
    public static final ComponentType<Map<ResourceLocation, Long>> LAST = ComponentType.register(
            LotrPort.id("events"), Codec.unboundedMap(ResourceLocation.CODEC, Codec.LONG));

    private ColonyEvents() {}

    public static void registerKind(ResourceLocation id, Kind kind) {
        KINDS.put(id, kind);
    }

    public static Map<ResourceLocation, Def> defs() {
        return DEFS.entries();
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent e) -> e.addListener(DEFS));
        registerKind(LotrPort.id("wedding"), ColonyEvents::wedding);
        registerKind(LotrPort.id("festival"), ColonyEvents::festival);
        registerKind(LotrPort.id("birth"), ColonyEvents::birth);
        registerKind(LotrPort.id("quarrel"), ColonyEvents::quarrel);
        ColonySystems.register(ColonySystem.of(SYSTEM, Cadence.DAY, Tier.ALL, Set.of(LotrPort.id("needs")),
                ColonyEvents::day));
    }

    static void day(SystemContext ctx) {
        Colony c = ctx.colony();
        if (c.ai()) {
            return;
        }
        MinecraftServer server = ctx.server();
        long day = server.overworld().getDayTime() / 24000L;
        Random random = new Random(c.id().getLeastSignificantBits() ^ day * 31L);
        // opinions drift first, so events see today's
        List<Person> citizens = Society.citizens(server, c);
        Map<UUID, UUID> home = new HashMap<>();
        Map<UUID, UUID> work = new HashMap<>();
        for (Person p : citizens) {
            p.get(com.porcupinestudios.lotrport.colony.work.WorkComponents.HOME).ifPresent(h -> home.put(p.id(),
                    h.building()));
            p.get(com.porcupinestudios.lotrport.colony.work.WorkComponents.JOB).ifPresent(j -> work.put(p.id(),
                    j.building()));
        }
        Opinions.day(Persons.get(server), citizens, home, work, ctx.days());
        for (Map.Entry<ResourceLocation, Def> e : defs().entrySet()) {
            Def d = e.getValue();
            long last = c.get(LAST).map(m -> m.getOrDefault(e.getKey(), Long.MIN_VALUE / 2)).orElse(Long.MIN_VALUE / 2);
            if (day - last < d.cooldownDays() || random.nextDouble() >= Math.min(1, d.chance() * ctx.days())) {
                continue;
            }
            run(server, c, e.getKey(), random);
        }
    }

    /** Runs an event now (if its kind finds anyone to happen to); true when it happened. */
    public static boolean run(MinecraftServer server, Colony c, ResourceLocation id, Random random) {
        Def d = defs().get(id);
        Kind kind = d == null ? null : KINDS.get(d.kind());
        if (kind == null) {
            return false;
        }
        List<Person> citizens = Society.citizens(server, c);
        if (citizens.size() < d.minPeople()) {
            return false;
        }
        long now = server.overworld().getGameTime();
        Outcome o = kind.run(new Ctx(server, c, id, d, citizens, random, now));
        if (o.line().isEmpty()) {
            return false;
        }
        Persons persons = Persons.get(server);
        for (Person p : Society.citizens(server, c)) {
            boolean about = o.about().contains(p.id());
            Optional<ResourceLocation> t = about ? d.thought() : d.guestThought();
            t.ifPresent(th -> Colonists.remember(persons, p, th, now));
        }
        long day = server.overworld().getDayTime() / 24000L;
        Map<ResourceLocation, Long> last = new LinkedHashMap<>(c.get(LAST).orElse(Map.of()));
        last.put(id, day);
        Colonies.get(server).set(c, LAST, last);
        CompoundTag tag = new CompoundTag();
        tag.putUUID("colony", c.id());
        tag.putString("event", id.toString());
        tag.putString("text", o.line().get().getString());
        net.minecraft.nbt.ListTag who = new net.minecraft.nbt.ListTag();
        o.about().forEach(u -> who.add(net.minecraft.nbt.NbtUtils.createUUID(u)));
        tag.put("about", who);
        HistoryLog.get(server).append(new HistoryEntry(now, HISTORY, c.dimension(), Optional.of(c.hall()), tag));
        ServerPlayer owner = server.getPlayerList().getPlayer(c.founder());
        if (owner != null) {
            owner.sendSystemMessage(Component.literal("[" + c.name() + "] ").append(o.line().get()));
        }
        return true;
    }

    // ---------------------------------------------------------------- built-in kinds

    static boolean single(Person p) {
        return p.get(ColonistData.KIN).map(k -> k.spouse().isEmpty()).orElse(false);
    }

    static Outcome wedding(Ctx x) {
        Person bestA = null;
        Person bestB = null;
        double best = x.def().minOpinion();
        List<Person> ps = x.citizens();
        for (int i = 0; i < ps.size(); i++) {
            Person a = ps.get(i);
            if (!single(a) || !Colonists.adult(a, x.now())) {
                continue;
            }
            for (int j = i + 1; j < ps.size(); j++) {
                Person b = ps.get(j);
                if (!single(b) || !Colonists.adult(b, x.now()) || family(a, b)) {
                    continue;
                }
                double both = Math.min(Opinions.of(a, b.id()), Opinions.of(b, a.id()));
                if (both >= best) {
                    best = both;
                    bestA = a;
                    bestB = b;
                }
            }
        }
        if (bestA == null) {
            return Outcome.NOTHING;
        }
        marry(x.server(), bestA, bestB);
        Persons persons = Persons.get(x.server());
        Opinions.add(persons, bestA, bestB.id(), x.def().opinionChange());
        Opinions.add(persons, bestB, bestA.id(), x.def().opinionChange());
        return new Outcome(List.of(bestA.id(), bestB.id()), Optional.of(Component.literal(bestA.name() + " and "
                + bestB.name() + " were married. The whole colony came.")));
    }

    /** Marries two people (their kin records), as the wedding does. */
    public static void marry(MinecraftServer server, Person a, Person b) {
        Persons persons = Persons.get(server);
        a.get(ColonistData.KIN).ifPresent(k -> persons.setQuiet(a, ColonistData.KIN, k.marry(b.id())));
        b.get(ColonistData.KIN).ifPresent(k -> persons.setQuiet(b, ColonistData.KIN, k.marry(a.id())));
    }

    static boolean family(Person a, Person b) {
        Kin ka = a.get(ColonistData.KIN).orElse(null);
        Kin kb = b.get(ColonistData.KIN).orElse(null);
        if (ka == null || kb == null) {
            return false;
        }
        if (ka.parents().contains(b.id()) || kb.parents().contains(a.id())) {
            return true;
        }
        for (UUID p : ka.parents()) {
            if (kb.parents().contains(p)) {
                return true;
            }
        }
        return false;
    }

    static Outcome festival(Ctx x) {
        List<Person> ps = x.citizens();
        Persons persons = Persons.get(x.server());
        // a few new friendships: each takes a turn with someone at random
        for (Person a : ps) {
            Person b = ps.get(x.random().nextInt(ps.size()));
            if (b != a) {
                Opinions.add(persons, a, b.id(), x.def().opinionChange());
                Opinions.add(persons, b, a.id(), x.def().opinionChange());
            }
        }
        List<UUID> all = new ArrayList<>();
        ps.forEach(p -> all.add(p.id()));
        String name = x.def().name().isEmpty() ? "a festival" : x.def().name();
        return new Outcome(all, Optional.of(Component.literal("The colony held " + name + ".")));
    }

    static Outcome birth(Ctx x) {
        for (Person a : x.citizens()) {
            Optional<Kin> ka = a.get(ColonistData.KIN);
            if (ka.isEmpty() || ka.get().spouse().isEmpty() || !Colonists.adult(a, x.now())) {
                continue;
            }
            UUID partner = ka.get().spouse().get();
            Person b = x.citizens().stream().filter(p -> p.id().equals(partner)).findFirst().orElse(null);
            // each couple once: the one with the smaller id asks
            if (b == null || a.id().compareTo(b.id()) > 0 || x.random().nextDouble() > 0.5) {
                continue;
            }
            Person child = born(x.server(), x.colony(), a, b);
            return new Outcome(List.of(a.id(), b.id()), Optional.of(Component.literal(a.name() + " and " + b.name()
                    + " had a child: " + child.name() + ".")));
        }
        return Outcome.NOTHING;
    }

    /** A child of two citizens joins the colony (their race and kind; grows up by its race's adult age). */
    public static Person born(MinecraftServer server, Colony colony, Person a, Person b) {
        Persons persons = Persons.get(server);
        long now = server.overworld().getGameTime();
        UUID id = UUID.randomUUID();
        Person child = persons.migrated(id, a.type(), "", now, colony.asOwner());
        Colonists.settle(server, child);
        Kin race = a.get(ColonistData.KIN).orElseThrow();
        persons.setQuiet(child, ColonistData.KIN, new Kin(race.race(), now, List.of(a.id(), b.id()), Optional.empty(),
                List.of(), List.of()));
        persons.setQuiet(a, ColonistData.KIN, a.get(ColonistData.KIN).orElseThrow().withChild(id));
        b.get(ColonistData.KIN).ifPresent(k -> persons.setQuiet(b, ColonistData.KIN, k.withChild(id)));
        persons.set(server, child, CoreComponents.CITIZEN, new CoreComponents.Citizen(now, false));
        Opinions.set(persons, child, a.id(), 60);
        Opinions.set(persons, child, b.id(), 60);
        Opinions.set(persons, a, id, 80);
        Opinions.set(persons, b, id, 80);
        return child;
    }

    static Outcome quarrel(Ctx x) {
        List<Person> ps = x.citizens();
        Person worstA = null;
        Person worstB = null;
        double worst = -x.def().minOpinion();
        for (Person a : ps) {
            for (Person b : ps) {
                if (a == b) {
                    continue;
                }
                double v = Opinions.of(a, b.id());
                if (v <= worst) {
                    worst = v;
                    worstA = a;
                    worstB = b;
                }
            }
        }
        if (worstA == null) {
            return Outcome.NOTHING;
        }
        Persons persons = Persons.get(x.server());
        Opinions.add(persons, worstA, worstB.id(), -x.def().opinionChange());
        Opinions.add(persons, worstB, worstA.id(), -x.def().opinionChange());
        return new Outcome(List.of(worstA.id(), worstB.id()), Optional.of(Component.literal(worstA.name() + " and "
                + worstB.name() + " had a shouting match.")));
    }
}
