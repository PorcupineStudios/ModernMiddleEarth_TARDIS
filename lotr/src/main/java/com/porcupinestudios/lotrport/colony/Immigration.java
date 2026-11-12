package com.porcupinestudios.lotrport.colony;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.colonist.ColonistData;
import com.porcupinestudios.lotrport.colonist.Colonists;
import com.porcupinestudios.lotrport.colonist.Kin;
import com.porcupinestudios.lotrport.colonist.KinRules;
import com.porcupinestudios.lotrport.colonist.Mood;
import com.porcupinestudios.lotrport.colonist.Opinions;
import com.porcupinestudios.lotrport.colonist.RaceDef;
import com.porcupinestudios.lotrport.colonist.Skills;
import com.porcupinestudios.lotrport.colonist.TraitRules;
import com.porcupinestudios.lotrport.colony.work.Races;
import com.porcupinestudios.lotrport.journey.CaravanConditions;
import com.porcupinestudios.lotrport.npc.LotrNpc;
import com.porcupinestudios.lotrport.society.ComponentType;
import com.porcupinestudios.lotrport.society.Person;
import com.porcupinestudios.lotrport.society.Persons;
import com.porcupinestudios.lotrport.society.Society;
import com.porcupinestudios.ringbearer.api.condition.Condition;
import com.porcupinestudios.ringbearer.api.condition.Conditions;
import com.porcupinestudios.ringbearer.api.data.CodecJsonLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Immigration by approval (Aaron, 2026-11-12). A colony's board (colony component {@code immigration}) holds a few
 * offers; each is a group that would like to settle: most often one person alone, sometimes a family or friends. The
 * owner (or anyone with the {@code migrants} permission) takes or turns away each offer; the board is drawn afresh
 * every {@code refresh_ticks} (30 minutes). Slots, how big a group may be and how skilled newcomers are start small and
 * grow with the colony: {@code levels} in {@code colony_immigration/settings.json}, each with a condition (the shared
 * conditions: population, upgrades from research ({@code lotr_port:colony_upgrade}), anything) and what it adds.
 */
public final class Immigration {
    /** One person in an offer. {@code spouse} and {@code parents} point at others in the same offer by index. */
    public record Prospect(ResourceLocation type, String name, List<ResourceLocation> traits,
            Map<ResourceLocation, com.porcupinestudios.lotrport.colonist.Skills.Skill> skills, int age, int spouse,
            List<Integer> parents) {
        public static final Codec<Prospect> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("type").forGetter(Prospect::type),
                Codec.STRING.optionalFieldOf("name", "").forGetter(Prospect::name),
                ResourceLocation.CODEC.listOf().optionalFieldOf("traits", List.of()).forGetter(Prospect::traits),
                Skills.MAP_CODEC.optionalFieldOf("skills", Map.of()).forGetter(Prospect::skills),
                Codec.INT.optionalFieldOf("age", 25).forGetter(Prospect::age),
                Codec.INT.optionalFieldOf("spouse", -1).forGetter(Prospect::spouse),
                Codec.INT.listOf().optionalFieldOf("parents", List.of()).forGetter(Prospect::parents))
                .apply(i, Prospect::new));
    }

    /** An offer: a group, and what binds it ("alone", "family", "friends"). */
    public record Offer(int id, List<Prospect> members, String bond) {
        public static final Codec<Offer> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("id").forGetter(Offer::id),
                Prospect.CODEC.listOf().fieldOf("members").forGetter(Offer::members),
                Codec.STRING.optionalFieldOf("bond", "alone").forGetter(Offer::bond))
                .apply(i, Offer::new));
    }

    /** A colony's board: its offers, when it is next drawn, and a counter for offer ids. */
    public record Board(List<Offer> offers, long next, int count) {
        public static final Board EMPTY = new Board(List.of(), 0, 0);
        public static final Codec<Board> CODEC = RecordCodecBuilder.create(i -> i.group(
                Offer.CODEC.listOf().optionalFieldOf("offers", List.of()).forGetter(Board::offers),
                Codec.LONG.optionalFieldOf("next", 0L).forGetter(Board::next),
                Codec.INT.optionalFieldOf("count", 0).forGetter(Board::count))
                .apply(i, Board::new));
    }

    /** A step up: when {@code when} holds, the colony gets these on top. */
    public record Level(String name, Optional<Condition> when, int slots, int maxGroup, int quality) {
        public static final Codec<Level> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("name", "").forGetter(Level::name),
                Conditions.CODEC.optionalFieldOf("when").forGetter(Level::when),
                Codec.INT.optionalFieldOf("slots", 0).forGetter(Level::slots),
                Codec.INT.optionalFieldOf("max_group", 0).forGetter(Level::maxGroup),
                Codec.INT.optionalFieldOf("quality", 0).forGetter(Level::quality))
                .apply(i, Level::new));
    }

    /**
     * @param refreshTicks how often the board is drawn afresh (game ticks; 36000 = 30 minutes)
     * @param groupChance chance an offer is a group, when groups are allowed
     * @param familyShare share of groups that are families (the rest are friends)
     * @param pointsPerQuality skill levels each quality adds to a newcomer
     * @param capBase highest skill level a newcomer may have at quality 0; {@code capPerQuality} more per quality
     */
    public record Settings(boolean enabled, long refreshTicks, int slots, int maxGroup, int quality,
            double groupChance, double familyShare, int pointsPerQuality, int capBase, int capPerQuality,
            List<Level> levels) {
        public static final Settings DEFAULT = new Settings(true, 36000, 1, 1, 0, 0.25, 0.6, 2, 4, 2, List.of());
        public static final Codec<Settings> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.optionalFieldOf("enabled", DEFAULT.enabled()).forGetter(Settings::enabled),
                Codec.LONG.optionalFieldOf("refresh_ticks", DEFAULT.refreshTicks()).forGetter(Settings::refreshTicks),
                Codec.INT.optionalFieldOf("slots", DEFAULT.slots()).forGetter(Settings::slots),
                Codec.INT.optionalFieldOf("max_group", DEFAULT.maxGroup()).forGetter(Settings::maxGroup),
                Codec.INT.optionalFieldOf("quality", DEFAULT.quality()).forGetter(Settings::quality),
                Codec.DOUBLE.optionalFieldOf("group_chance", DEFAULT.groupChance()).forGetter(Settings::groupChance),
                Codec.DOUBLE.optionalFieldOf("family_share", DEFAULT.familyShare()).forGetter(Settings::familyShare),
                Codec.INT.optionalFieldOf("points_per_quality", DEFAULT.pointsPerQuality())
                        .forGetter(Settings::pointsPerQuality),
                Codec.INT.optionalFieldOf("cap_base", DEFAULT.capBase()).forGetter(Settings::capBase),
                Codec.INT.optionalFieldOf("cap_per_quality", DEFAULT.capPerQuality()).forGetter(Settings::capPerQuality),
                Level.CODEC.listOf().optionalFieldOf("levels", List.of()).forGetter(Settings::levels))
                .apply(i, Settings::new));
    }

    /** What a colony gets now: slots, biggest group, quality, and the levels it reached. */
    public record Perks(int slots, int maxGroup, int quality, List<String> levels) {}

    public static final ComponentType<Board> BOARD = ComponentType.register(LotrPort.id("immigration"), Board.CODEC);
    private static final CodecJsonLoader<Settings> SETTINGS =
            new CodecJsonLoader<>("colony_immigration", Settings.CODEC);
    public static final ResourceLocation HISTORY = LotrPort.id("immigrants_welcomed");

    private Immigration() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent e) -> e.addListener(SETTINGS));
        // the old migration left people waiting at Halls: their bodies go as they load, their records at start
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.EntityJoinLevelEvent e) -> {
            if (!e.getLevel().isClientSide() && e.getEntity().getData(com.porcupinestudios.lotrport.registry
                    .LotrRegistries.COLONY).startsWith("applicant:")) {
                e.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStartedEvent e) -> {
            Persons persons = Persons.get(e.getServer());
            for (Person p : List.copyOf(persons.all())) {
                if (p.has(com.porcupinestudios.lotrport.society.CoreComponents.APPLICANT)) {
                    persons.remove(e.getServer(), p.id());
                }
            }
        });
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> {
            if (e.getServer().getTickCount() % 200 == 50 && settings().enabled()) {
                for (Colony c : List.copyOf(Colonies.get(e.getServer()).all())) {
                    if (!c.ai()) {
                        board(e.getServer(), c);
                    }
                }
            }
        });
    }

    public static Settings settings() {
        return SETTINGS.entries().getOrDefault(LotrPort.id("settings"), Settings.DEFAULT);
    }

    public static boolean enabled() {
        return settings().enabled();
    }

    public static Perks perks(MinecraftServer server, Colony c) {
        Settings s = settings();
        int slots = s.slots();
        int group = s.maxGroup();
        int quality = s.quality();
        List<String> reached = new ArrayList<>();
        for (Level l : s.levels()) {
            boolean ok = l.when().map(w -> {
                try {
                    return w.test(CaravanConditions.colonyContext(server, c));
                } catch (RuntimeException e) {
                    return false;
                }
            }).orElse(true);
            if (ok) {
                slots += l.slots();
                group += l.maxGroup();
                quality += l.quality();
                reached.add(l.name());
            }
        }
        return new Perks(Math.max(0, slots), Math.max(1, group), Math.max(0, quality), reached);
    }

    /** The colony's board, drawn afresh first if its time has come. */
    public static Board board(MinecraftServer server, Colony c) {
        Board b = c.get(BOARD).orElse(Board.EMPTY);
        long now = server.overworld().getGameTime();
        if (now >= b.next()) {
            b = draw(server, c, b, now);
        }
        return b;
    }

    /** Draws the board afresh now (the old offers go). */
    public static Board draw(MinecraftServer server, Colony c, Board old, long now) {
        Perks perks = perks(server, c);
        Random random = new Random(c.id().getMostSignificantBits() ^ now * 7919L);
        List<Offer> offers = new ArrayList<>();
        int count = old.count();
        for (int i = 0; i < perks.slots(); i++) {
            Optional<Offer> o = offer(c, ++count, perks, random);
            o.ifPresent(offers::add);
        }
        Board b = new Board(offers, now + settings().refreshTicks(), count);
        Colonies.get(server).set(c, BOARD, b);
        return b;
    }

    /** One offer: a lone settler most of the time, a family or friends when groups are allowed and the roll says. */
    static Optional<Offer> offer(Colony c, int id, Perks perks, Random random) {
        Settings s = settings();
        RandomSource rs = RandomSource.create(random.nextLong());
        Optional<ResourceLocation> first = Colonies.pickMigrant(c, rs);
        if (first.isEmpty()) {
            return Optional.empty();
        }
        int size = 1;
        if (perks.maxGroup() > 1 && random.nextDouble() < s.groupChance()) {
            size = 2 + random.nextInt(perks.maxGroup() - 1);
        }
        boolean family = size > 1 && random.nextDouble() < s.familyShare();
        List<Prospect> members = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            ResourceLocation type = i == 0 || family ? first.get() : Colonies.pickMigrant(c, rs).orElse(first.get());
            boolean child = family && i >= 2;
            members.add(prospect(type, perks, random, child, i));
        }
        if (family && size >= 2) {
            members.set(0, withLinks(members.get(0), 1, List.of()));
            members.set(1, withLinks(members.get(1), 0, List.of()));
            for (int i = 2; i < size; i++) {
                members.set(i, withLinks(members.get(i), -1, List.of(0, 1)));
            }
        }
        return Optional.of(new Offer(id, members, size == 1 ? "alone" : family ? "family" : "friends"));
    }

    private static Prospect withLinks(Prospect p, int spouse, List<Integer> parents) {
        return new Prospect(p.type(), p.name(), p.traits(), p.skills(), p.age(), spouse, parents);
    }

    private static Prospect prospect(ResourceLocation type, Perks perks, Random random, boolean child, int n) {
        Settings s = settings();
        String race = Races.of(type);
        RaceDef def = ColonistData.races().get(LotrPort.id(race));
        int min = def == null ? 18 : def.settlerAgeMin();
        int max = def == null ? 50 : def.settlerAgeMax();
        int age = child ? 1 + random.nextInt(Math.max(1, (def == null ? 18 : def.adultAge()) - 2))
                : min + random.nextInt(Math.max(1, max - min + 1));
        UUID tmp = new UUID(random.nextLong(), random.nextLong());
        String name = Colonists.nameFor(Person.create(tmp, type, "", Optional.empty(), 0));
        List<ResourceLocation> traits = TraitRules.roll(ColonistData.traits(), random, ColonistData.settings());
        int points = child ? 0 : Skills.settings().settlerPoints() + perks.quality() * s.pointsPerQuality();
        int cap = s.capBase() + perks.quality() * s.capPerQuality();
        return new Prospect(type, name, traits, Skills.roll(race, points, cap, random), age, -1, List.of());
    }

    /** What happened when an offer was answered. */
    public record Answer(boolean ok, Component message) {}

    /** Takes an offer: its people come to the Hall now as citizens, with their names, traits, skills and ties. */
    public static Answer accept(ServerPlayer player, Colony c, int offer) {
        MinecraftServer server = player.server;
        Board b = c.get(BOARD).orElse(Board.EMPTY);
        Optional<Offer> o = b.offers().stream().filter(x -> x.id() == offer).findFirst();
        if (o.isEmpty()) {
            return new Answer(false, Component.literal("That group has moved on"));
        }
        ServerLevel level = server.getLevel(c.dimension());
        if (level == null || !level.isLoaded(c.hall())) {
            return new Answer(false, Component.literal("Be near " + c.name() + " to welcome them"));
        }
        int size = o.get().members().size();
        int room = Colonies.homes(level, c) - Society.citizenCount(server, c);
        if (room < size) {
            return new Answer(false, Component.literal("Not enough beds: " + size + " to house, " + Math.max(0, room)
                    + " free"));
        }
        List<Person> came = welcome(server, level, c, o.get());
        if (came.isEmpty()) {
            return new Answer(false, Component.literal("They could not get to the Hall"));
        }
        List<Offer> left = new ArrayList<>(b.offers());
        left.remove(o.get());
        Colonies.get(server).set(c, BOARD, new Board(left, b.next(), b.count()));
        String names = String.join(", ", came.stream().map(Person::name).toList());
        return new Answer(true, Component.literal("Welcome to " + c.name() + ": " + names));
    }

    /** Turns an offer away: its slot stays empty until the board is drawn again. */
    public static Answer decline(MinecraftServer server, Colony c, int offer) {
        Board b = c.get(BOARD).orElse(Board.EMPTY);
        List<Offer> left = new ArrayList<>(b.offers());
        if (!left.removeIf(x -> x.id() == offer)) {
            return new Answer(false, Component.literal("That group has moved on"));
        }
        Colonies.get(server).set(c, BOARD, new Board(left, b.next(), b.count()));
        return new Answer(true, Component.literal("They went on their way"));
    }

    /** Makes an offer's people citizens; returns who came. */
    public static List<Person> welcome(MinecraftServer server, ServerLevel level, Colony c, Offer offer) {
        Colonies colonies = Colonies.get(server);
        Persons persons = Persons.get(server);
        long now = server.overworld().getGameTime();
        List<Person> came = new ArrayList<>();
        for (Prospect p : offer.members()) {
            Optional<LotrNpc> npc = colonies.arrive(level, c, p.type());
            if (npc.isEmpty()) {
                came.add(null);
                continue;
            }
            Person person = Society.recordOf(server, npc.get());
            Colonists.settle(server, person);
            if (!p.name().isEmpty()) {
                persons.rename(server, person, p.name());
            }
            persons.setQuiet(person, ColonistData.TRAITS, p.traits());
            persons.setQuiet(person, Skills.TYPE, p.skills());
            persons.setQuiet(person, ColonistData.MOOD, Mood.EMPTY);
            RaceDef race = ColonistData.races().get(LotrPort.id(Races.of(p.type())));
            Kin kin = person.get(ColonistData.KIN).orElse(new Kin(LotrPort.id(Races.of(p.type())), now, List.of(),
                    Optional.empty(), List.of(), List.of()));
            long born = race == null ? now : KinRules.bornFor(p.age(), race, now, ticksPerDay());
            persons.setQuiet(person, ColonistData.KIN, new Kin(kin.race(), born, List.of(), Optional.empty(),
                    List.of(), List.of()));
            came.add(person);
        }
        // ties inside the group
        for (int i = 0; i < offer.members().size(); i++) {
            Person a = came.get(i);
            if (a == null) {
                continue;
            }
            Prospect p = offer.members().get(i);
            if (p.spouse() >= 0 && p.spouse() < came.size() && came.get(p.spouse()) != null) {
                Person b = came.get(p.spouse());
                Kin k = a.get(ColonistData.KIN).orElseThrow();
                persons.setQuiet(a, ColonistData.KIN, k.marry(b.id()));
            }
            List<UUID> parents = new ArrayList<>();
            for (int q : p.parents()) {
                if (q >= 0 && q < came.size() && came.get(q) != null) {
                    parents.add(came.get(q).id());
                    Kin pk = came.get(q).get(ColonistData.KIN).orElseThrow();
                    persons.setQuiet(came.get(q), ColonistData.KIN, pk.withChild(a.id()));
                }
            }
            if (!parents.isEmpty()) {
                persons.setQuiet(a, ColonistData.KIN, a.get(ColonistData.KIN).orElseThrow().withParents(parents));
            }
            for (int j = 0; j < came.size(); j++) {
                if (j != i && came.get(j) != null) {
                    Opinions.set(persons, a, came.get(j).id(), offer.bond().equals("family") ? 60 : 40);
                }
            }
        }
        List<Person> out = new ArrayList<>();
        for (Person p : came) {
            if (p != null) {
                out.add(persons.get(p.id()).orElse(p));
            }
        }
        return out;
    }

    static int ticksPerDay() {
        return Math.max(1, ColonyData.settings().dayTicks());
    }
}
