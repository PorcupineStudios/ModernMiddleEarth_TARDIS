package com.porcupinestudios.lotrport.diplomacy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.border.Borders;
import com.porcupinestudios.lotrport.colony.Colonies;
import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.faction.Diplomacy;
import com.porcupinestudios.lotrport.faction.Factions;
import com.porcupinestudios.lotrport.faction.Realms;
import com.porcupinestudios.lotrport.faction.Relation;
import com.porcupinestudios.lotrport.society.CoreComponents;
import com.porcupinestudios.lotrport.society.Owner;
import com.porcupinestudios.lotrport.war.War;
import com.porcupinestudios.lotrport.war.WarSettings;
import com.porcupinestudios.lotrport.war.Wars;
import com.porcupinestudios.lotrport.world.map.MiddleEarthMap;
import com.porcupinestudios.ringbearer.api.history.HistoryEntry;
import com.porcupinestudios.ringbearer.api.history.HistoryLog;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Diplomacy between sides (player kingdoms, AI realms, great powers): standing treaties (truce, trade, alliance) and
 * offers waiting for an answer. A player kingdom's ruler proposes and answers on the diplomacy table
 * ({@link DiplomacyViews}, Incoming tab; never in chat, Aaron 2026-11-18); great powers and AI realms answer at
 * once ({@link #aiAnswer}). Answered offers are kept ({@link Answer}, the last {@link #KEEP_ANSWERS} per side) so the
 * Sent and Incoming tabs show what became of them. Accepting carries out every
 * term or none ({@link #check} first). Wars between sides are {@code war/} wars between their realms
 * ({@link #declareWar}). SavedData {@code lotr_port_treaties}, {@code dataVersion} 1; history {@code diplomacy_*}.
 */
public final class Treaties extends SavedData {
    public static final String FILE = "lotr_port_treaties";
    public static final int DATA_VERSION = 1;
    public static final ResourceLocation OFFER_HISTORY = LotrPort.id("diplomacy_offer");
    public static final ResourceLocation TREATY_HISTORY = LotrPort.id("diplomacy_treaty");
    public static final ResourceLocation BROKEN_HISTORY = LotrPort.id("diplomacy_broken");

    public enum Kind implements StringRepresentable {
        TRUCE, TRADE, ALLIANCE;

        public static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** A standing treaty; {@code until} 0 = until broken. */
    public record Treaty(UUID id, ResourceLocation a, ResourceLocation b, Kind kind, long since, long until) {
        public static final Codec<Treaty> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(Treaty::id),
                ResourceLocation.CODEC.fieldOf("a").forGetter(Treaty::a),
                ResourceLocation.CODEC.fieldOf("b").forGetter(Treaty::b),
                Kind.CODEC.fieldOf("kind").forGetter(Treaty::kind),
                Codec.LONG.fieldOf("since").forGetter(Treaty::since),
                Codec.LONG.optionalFieldOf("until", 0L).forGetter(Treaty::until))
                .apply(i, Treaty::new));

        public boolean between(ResourceLocation x, ResourceLocation y) {
            return (a.equals(x) && b.equals(y)) || (a.equals(y) && b.equals(x));
        }

        public ResourceLocation other(ResourceLocation side) {
            return a.equals(side) ? b : a;
        }
    }

    /** An offer from one side to another, waiting for an answer until {@code expires}. */
    public record Offer(UUID id, ResourceLocation from, ResourceLocation to, UUID by, Terms terms, long made,
            long expires) {
        public static final Codec<Offer> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(Offer::id),
                ResourceLocation.CODEC.fieldOf("from").forGetter(Offer::from),
                ResourceLocation.CODEC.fieldOf("to").forGetter(Offer::to),
                UUIDUtil.CODEC.fieldOf("by").forGetter(Offer::by),
                Terms.CODEC.fieldOf("terms").forGetter(Offer::terms),
                Codec.LONG.fieldOf("made").forGetter(Offer::made),
                Codec.LONG.fieldOf("expires").forGetter(Offer::expires))
                .apply(i, Offer::new));
    }

    /** What became of an offer: accepted, refused (an AI's no), declined, withdrawn or expired. */
    public record Answer(Offer offer, String result, long at) {
        public static final Codec<Answer> CODEC = RecordCodecBuilder.create(i -> i.group(
                Offer.CODEC.fieldOf("offer").forGetter(Answer::offer),
                Codec.STRING.fieldOf("result").forGetter(Answer::result),
                Codec.LONG.fieldOf("at").forGetter(Answer::at))
                .apply(i, Answer::new));
    }

    public static final int KEEP_ANSWERS = 20;
    private static final Codec<List<Answer>> ANSWERS = Answer.CODEC.listOf();
    private static final Codec<List<Treaty>> TREATIES = Treaty.CODEC.listOf();
    private static final Codec<List<Offer>> OFFERS = Offer.CODEC.listOf();
    private static final SavedData.Factory<Treaties> FACTORY = new SavedData.Factory<>(Treaties::new, Treaties::load,
            null);

    private final Map<UUID, Treaty> treaties = new LinkedHashMap<>();
    private final Map<UUID, Offer> offers = new LinkedHashMap<>();
    private final List<Answer> answers = new ArrayList<>();

    public static Treaties get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE);
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> {
            if (e.getServer().overworld().getGameTime() % 200 == 0) {
                get(e.getServer()).expire(e.getServer());
            }
        });
    }

    // ---- sides

    /** The side a player decides for: the kingdom they rule. */
    public static Optional<ResourceLocation> sideOf(ServerPlayer player) {
        return Realms.get(player.server).ownedBy(player.getUUID()).map(Realms.Realm::id);
    }

    /** The player who decides for a side: a kingdom's ruler; empty for great powers and AI realms (they answer). */
    public static Optional<UUID> ruler(MinecraftServer server, ResourceLocation side) {
        return Realms.get(server).get(side).flatMap(Realms.Realm::owner);
    }

    public static String name(MinecraftServer server, ResourceLocation side) {
        return Factions.get(server, side).map(f -> f.name().getString()).orElse(side.toString());
    }

    /** A side's capital: its oldest colony in Middle-earth (where its coins are kept). */
    public static Optional<Colony> capital(MinecraftServer server, ResourceLocation side) {
        Optional<Colony> c = Optional.ofNullable(Borders.capitals(server).get(side));
        if (c.isPresent()) {
            return c;
        }
        return Colonies.get(server).all().stream().filter(x -> x.faction().equals(side))
                .min(Comparator.comparingLong(Colony::founded));
    }

    public static long treasury(MinecraftServer server, ResourceLocation side) {
        return capital(server, side).flatMap(c -> c.get(CoreComponents.TREASURY))
                .map(CoreComponents.Treasury::coins).orElse(0L);
    }

    public static List<Colony> colonies(MinecraftServer server, ResourceLocation side) {
        return Colonies.get(server).all().stream().filter(c -> c.faction().equals(side)).toList();
    }

    // ---- reading

    public List<Treaty> treaties() {
        return List.copyOf(treaties.values());
    }

    public List<Treaty> of(ResourceLocation side) {
        return treaties.values().stream().filter(t -> t.a().equals(side) || t.b().equals(side)).toList();
    }

    public boolean has(ResourceLocation a, ResourceLocation b, Kind kind) {
        return treaties.values().stream().anyMatch(t -> t.kind() == kind && t.between(a, b));
    }

    /** Whether two factions have a trade agreement (caravans trade as between friends). */
    public static boolean trading(MinecraftServer server, ResourceLocation a, ResourceLocation b) {
        return get(server).has(a, b, Kind.TRADE);
    }

    public List<Offer> offers() {
        return List.copyOf(offers.values());
    }

    public Optional<Offer> offer(UUID id) {
        return Optional.ofNullable(offers.get(id));
    }

    /** Answered offers a side made or got, newest first. */
    public List<Answer> answers(ResourceLocation side) {
        List<Answer> out = new ArrayList<>();
        for (int i = answers.size() - 1; i >= 0; i--) {
            Answer a = answers.get(i);
            if (a.offer().from().equals(side) || a.offer().to().equals(side)) {
                out.add(a);
            }
        }
        return out;
    }

    private void answered(MinecraftServer server, Offer offer, String result) {
        answers.add(new Answer(offer, result, server.overworld().getGameTime()));
        // keep the newest KEEP_ANSWERS per side
        for (ResourceLocation side : List.of(offer.from(), offer.to())) {
            List<Answer> theirs = answers.stream().filter(a -> a.offer().from().equals(side)
                    || a.offer().to().equals(side)).toList();
            for (int i = 0; i < theirs.size() - KEEP_ANSWERS; i++) {
                answers.remove(theirs.get(i));
            }
        }
        setDirty();
    }

    /** Wars (any state but ended) between two sides: their realms' wars and their colonies' wars. */
    public static List<War> wars(MinecraftServer server, ResourceLocation a, ResourceLocation b) {
        List<War> out = new ArrayList<>();
        for (War w : Wars.get(server).all()) {
            if (w.state() == War.State.ENDED) {
                continue;
            }
            Optional<ResourceLocation> x = sideOfParty(server, w.attacker());
            Optional<ResourceLocation> y = sideOfParty(server, w.defender());
            if ((x.equals(Optional.of(a)) && y.equals(Optional.of(b)))
                    || (x.equals(Optional.of(b)) && y.equals(Optional.of(a)))) {
                out.add(w);
            }
        }
        return out;
    }

    public static Optional<ResourceLocation> sideOfParty(MinecraftServer server, Owner party) {
        return switch (party) {
            case Owner.OfColony c -> Colonies.get(server).get(c.id()).map(Colony::faction);
            case Owner.OfRealm r -> Optional.of(r.id());
            default -> Optional.empty();
        };
    }

    // ---- offers

    /**
     * Puts an offer on the table from {@code from} (whose ruler {@code by} is) to {@code to}. A great power or AI realm
     * answers at once; a kingdom's ruler is told. Returns what the proposer is told.
     */
    public Component propose(MinecraftServer server, UUID by, ResourceLocation from, ResourceLocation to, Terms terms) {
        if (from.equals(to)) {
            return Component.literal("You can't treat with yourself");
        }
        if (terms.empty()) {
            return Component.literal("The offer is empty");
        }
        Optional<Component> problem = check(server, from, to, terms);
        if (problem.isPresent()) {
            return problem.get();
        }
        DiplomacyData d = DiplomacyData.get();
        long now = server.overworld().getGameTime();
        Offer offer = new Offer(UUID.randomUUID(), from, to, by, terms, now, now + (long) d.offerDays() * d.dayTicks());
        log(server, OFFER_HISTORY, offer, "proposed");
        Optional<UUID> theirRuler = ruler(server, to);
        if (theirRuler.isEmpty()) {
            double value = aiAnswer(server, offer);
            if (value < 0) {
                log(server, OFFER_HISTORY, offer, "declined");
                answered(server, offer, "refused");
                return Component.literal(name(server, to) + " refuses" + (value < -50 ? " outright" : "")
                        + ". Offer more, or ask less.");
            }
            carryOut(server, offer);
            answered(server, offer, "accepted");
            return Component.literal(name(server, to) + " accepts: " + summary(server, offer));
        }
        offers.put(offer.id(), offer);
        setDirty();
        return Component.literal("Offer sent to " + name(server, to) + "; it waits in their Incoming tab");
    }

    /** The ruler of the side an offer went to accepts it: every term is carried out, or none. */
    public Component accept(MinecraftServer server, UUID by, UUID offerId) {
        Offer offer = offers.get(offerId);
        if (offer == null || !ruler(server, offer.to()).equals(Optional.of(by))) {
            return Component.literal("There is no such offer for you");
        }
        Optional<Component> problem = check(server, offer.from(), offer.to(), offer.terms());
        if (problem.isPresent()) {
            return Component.literal("It can't be carried out now: ").append(problem.get());
        }
        offers.remove(offerId);
        carryOut(server, offer);
        answered(server, offer, "accepted");
        return Component.literal("Agreed: " + summary(server, offer));
    }

    public Component decline(MinecraftServer server, UUID by, UUID offerId) {
        Offer offer = offers.get(offerId);
        if (offer == null) {
            return Component.literal("There is no such offer");
        }
        boolean theirs = ruler(server, offer.to()).equals(Optional.of(by));
        boolean ours = ruler(server, offer.from()).equals(Optional.of(by));
        if (!theirs && !ours) {
            return Component.literal("That offer isn't yours to answer");
        }
        offers.remove(offerId);
        setDirty();
        log(server, OFFER_HISTORY, offer, theirs ? "declined" : "withdrawn");
        answered(server, offer, theirs ? "declined" : "withdrawn");
        return Component.literal(theirs ? "Declined" : "Withdrawn");
    }

    private void expire(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        List<Offer> old = offers.values().stream().filter(o -> o.expires() <= now).toList();
        old.forEach(o -> {
            offers.remove(o.id());
            answered(server, o, "expired");
        });
        boolean changed = !old.isEmpty();
        changed |= treaties.values().removeIf(t -> t.until() > 0 && t.until() <= now);
        if (changed) {
            setDirty();
        }
    }

    // ---- checking and carrying out

    /** Why the terms can't be carried out now, if they can't. */
    public Optional<Component> check(MinecraftServer server, ResourceLocation from, ResourceLocation to, Terms t) {
        DiplomacyData d = DiplomacyData.get();
        if (t.giveCoins() < 0 || t.askCoins() < 0 || t.giveLand() < 0 || t.askLand() < 0 || t.truceDays() < 0
                || t.giveLand() > d.maxLand() || t.askLand() > d.maxLand()) {
            return Optional.of(Component.literal("Those numbers aren't allowed"));
        }
        if (t.giveCoins() > treasury(server, from)) {
            return Optional.of(Component.literal(name(server, from) + "'s treasury hasn't " + t.giveCoins() + " coins"));
        }
        if (t.askCoins() > treasury(server, to)) {
            return Optional.of(Component.literal(name(server, to) + "'s treasury hasn't " + t.askCoins() + " coins"));
        }
        if (landFor(server, from, to, t.giveLand()).size() < t.giveLand()) {
            return Optional.of(Component.literal(name(server, from) + " hasn't that much land near " + name(server, to)));
        }
        if (landFor(server, to, from, t.askLand()).size() < t.askLand()) {
            return Optional.of(Component.literal(name(server, to) + " hasn't that much land near " + name(server, from)));
        }
        if (t.giveColony().isPresent() && !ownedBy(server, t.giveColony().get(), from)) {
            return Optional.of(Component.literal("That colony isn't " + name(server, from) + "'s"));
        }
        if (t.askColony().isPresent() && !ownedBy(server, t.askColony().get(), to)) {
            return Optional.of(Component.literal("That colony isn't " + name(server, to) + "'s"));
        }
        if ((t.giveColony().isPresent() && ruler(server, to).isEmpty())
                || (t.askColony().isPresent() && ruler(server, from).isEmpty())) {
            return Optional.of(Component.literal("Colonies can only be handed to a kingdom with a ruler"));
        }
        boolean atWar = !wars(server, from, to).isEmpty();
        if (atWar && !t.endWar() && (t.trade() || t.alliance() || t.truceDays() > 0)) {
            return Optional.of(Component.literal("Make peace first (end the war) for a truce, trade or alliance"));
        }
        if (t.vassal() == Terms.Vassal.THEY_SERVE && Diplomacy.get(server).overlord(to).isPresent()) {
            return Optional.of(Component.literal(name(server, to) + " already serves an overlord"));
        }
        if (t.vassal() == Terms.Vassal.WE_SERVE && Diplomacy.get(server).overlord(from).isPresent()) {
            return Optional.of(Component.literal(name(server, from) + " already serves an overlord"));
        }
        return Optional.empty();
    }

    private static boolean ownedBy(MinecraftServer server, UUID colony, ResourceLocation side) {
        return Colonies.get(server).get(colony).map(c -> c.faction().equals(side)).orElse(false);
    }

    /**
     * Up to {@code count} squares {@code giver} holds, nearest the receiver's capital (else the giver's), within
     * {@code land_search} map pixels.
     */
    static List<Integer> landFor(MinecraftServer server, ResourceLocation giver, ResourceLocation receiver,
            int count) {
        if (count <= 0) {
            return List.of();
        }
        Optional<Colony> centre = capital(server, receiver).or(() -> capital(server, giver));
        if (centre.isEmpty()) {
            return List.of();
        }
        MiddleEarthMap map = MiddleEarthMap.get();
        int c = Borders.pixelOf(centre.get().hall().getX(), centre.get().hall().getZ());
        if (c < 0) {
            return List.of();
        }
        int cx = c % map.width();
        int cz = c / map.width();
        int r = DiplomacyData.get().landSearch();
        Borders borders = Borders.get(server);
        List<int[]> found = new ArrayList<>();
        for (int z = Math.max(0, cz - r); z <= Math.min(map.height() - 1, cz + r); z++) {
            for (int x = Math.max(0, cx - r); x <= Math.min(map.width() - 1, cx + r); x++) {
                int i = z * map.width() + x;
                if (borders.owner(i).equals(Optional.of(giver))) {
                    found.add(new int[] {i, (x - cx) * (x - cx) + (z - cz) * (z - cz)});
                }
            }
        }
        found.sort(Comparator.comparingInt((int[] a) -> a[1]).thenComparingInt(a -> a[0]));
        return found.stream().limit(count).map(a -> a[0]).toList();
    }

    private void carryOut(MinecraftServer server, Offer offer) {
        Terms t = offer.terms();
        ResourceLocation from = offer.from();
        ResourceLocation to = offer.to();
        long now = server.overworld().getGameTime();
        DiplomacyData d = DiplomacyData.get();
        if (t.endWar()) {
            for (War w : wars(server, from, to)) {
                Wars.get(server).end(server, w, Optional.empty(), 0, "treaty");
            }
            Diplomacy dip = Diplomacy.get(server);
            if (dip.override(from, to).map(Relation::isBad).orElse(false)) {
                dip.setRelation(server, from, to, Relation.NEUTRAL);
            }
        }
        pay(server, from, to, t.giveCoins());
        pay(server, to, from, t.askCoins());
        Borders borders = Borders.get(server);
        for (int p : landFor(server, from, to, t.giveLand())) {
            borders.take(server, p, to, "treaty");
        }
        for (int p : landFor(server, to, from, t.askLand())) {
            borders.take(server, p, from, "treaty");
        }
        t.giveColony().flatMap(id -> Colonies.get(server).get(id)).ifPresent(c -> hand(server, c, to));
        t.askColony().flatMap(id -> Colonies.get(server).get(id)).ifPresent(c -> hand(server, c, from));
        if (t.truceDays() > 0) {
            add(server, new Treaty(UUID.randomUUID(), from, to, Kind.TRUCE, now,
                    now + (long) t.truceDays() * d.dayTicks()));
        }
        if (t.trade() && !has(from, to, Kind.TRADE)) {
            add(server, new Treaty(UUID.randomUUID(), from, to, Kind.TRADE, now, 0));
        }
        if (t.alliance() && !has(from, to, Kind.ALLIANCE)) {
            add(server, new Treaty(UUID.randomUUID(), from, to, Kind.ALLIANCE, now, 0));
            Diplomacy.get(server).setRelation(server, from, to, Relation.ALLY);
        }
        if (t.vassal() == Terms.Vassal.THEY_SERVE) {
            Diplomacy.get(server).setOverlord(server, to, from);
        } else if (t.vassal() == Terms.Vassal.WE_SERVE) {
            Diplomacy.get(server).setOverlord(server, from, to);
        }
        log(server, OFFER_HISTORY, offer, "accepted");
        setDirty();
    }

    private static void pay(MinecraftServer server, ResourceLocation from, ResourceLocation to, long coins) {
        if (coins <= 0) {
            return;
        }
        Optional<Colony> payer = capital(server, from);
        Optional<Colony> payee = capital(server, to);
        if (payer.isEmpty()) {
            return;
        }
        Colonies colonies = Colonies.get(server);
        long have = treasury(server, from);
        long moved = Math.min(have, coins);
        colonies.set(payer.get(), CoreComponents.TREASURY, new CoreComponents.Treasury(have - moved));
        payee.ifPresent(c -> colonies.set(c, CoreComponents.TREASURY, new CoreComponents.Treasury(
                c.get(CoreComponents.TREASURY).map(CoreComponents.Treasury::coins).orElse(0L) + moved)));
    }

    /** Hands a colony to a kingdom: its ruler owns it from now on. */
    private static void hand(MinecraftServer server, Colony colony, ResourceLocation to) {
        ruler(server, to).ifPresent(owner -> Colonies.get(server).transfer(server, colony, owner, to, false,
                "treaty"));
    }

    private void add(MinecraftServer server, Treaty treaty) {
        treaties.put(treaty.id(), treaty);
        CompoundTag tag = new CompoundTag();
        tag.putString("a", treaty.a().toString());
        tag.putString("b", treaty.b().toString());
        tag.putString("kind", treaty.kind().getSerializedName());
        tag.putLong("until", treaty.until());
        history(server, TREATY_HISTORY, tag);
    }

    /** A ruler breaks a trade agreement or alliance (a truce holds until it ends). */
    public Component breakTreaty(MinecraftServer server, UUID by, UUID treatyId) {
        Treaty t = treaties.get(treatyId);
        if (t == null) {
            return Component.literal("There is no such treaty");
        }
        Optional<ResourceLocation> side = ruler(server, t.a()).equals(Optional.of(by)) ? Optional.of(t.a())
                : ruler(server, t.b()).equals(Optional.of(by)) ? Optional.of(t.b()) : Optional.empty();
        if (side.isEmpty()) {
            return Component.literal("That treaty isn't yours to break");
        }
        if (t.kind() == Kind.TRUCE) {
            return Component.literal("A truce holds until it ends");
        }
        breakIt(server, t, side.get());
        return Component.literal("The " + t.kind().getSerializedName() + " with " + name(server, t.other(side.get()))
                + " is broken");
    }

    private void breakIt(MinecraftServer server, Treaty t, ResourceLocation by) {
        treaties.remove(t.id());
        setDirty();
        if (t.kind() == Kind.ALLIANCE) {
            Diplomacy.get(server).setRelation(server, t.a(), t.b(), Relation.NEUTRAL);
        }
        CompoundTag tag = new CompoundTag();
        tag.putString("a", t.a().toString());
        tag.putString("b", t.b().toString());
        tag.putString("kind", t.kind().getSerializedName());
        tag.putString("by", by.toString());
        history(server, BROKEN_HISTORY, tag);
    }

    // ---- war

    /** Why {@code side} can't declare war on {@code target}, if it can't. */
    public Optional<Component> whyNoWar(MinecraftServer server, ResourceLocation side, ResourceLocation target) {
        if (side.equals(target)) {
            return Optional.of(Component.literal("Not on yourself"));
        }
        if (has(side, target, Kind.TRUCE)) {
            return Optional.of(Component.literal("A truce holds"));
        }
        if (!wars(server, side, target).isEmpty()) {
            return Optional.of(Component.literal("Already at war"));
        }
        if (Diplomacy.get(server).overlord(side).equals(Optional.of(target))) {
            return Optional.of(Component.literal("You serve them: fight your own colonies' war to go free"));
        }
        return Optional.empty();
    }

    /**
     * A ruler's kingdom declares war on another side: trade and alliance with it end, relations turn to enemy and a
     * war between the two realms starts after the usual warning (its land can then be taken: border/Conquest).
     */
    public Component declareWar(MinecraftServer server, UUID by, ResourceLocation side, ResourceLocation target,
            String reason) {
        if (!ruler(server, side).equals(Optional.of(by))) {
            return Component.literal("Only the ruler declares war");
        }
        Optional<Component> why = whyNoWar(server, side, target);
        if (why.isPresent()) {
            return why.get();
        }
        for (Treaty t : List.copyOf(treaties.values())) {
            if (t.between(side, target)) {
                breakIt(server, t, side);
            }
        }
        offers.values().removeIf(o -> (o.from().equals(side) && o.to().equals(target))
                || (o.from().equals(target) && o.to().equals(side)));
        Diplomacy.get(server).setRelation(server, side, target, Relation.ENEMY);
        Wars.Result r = Wars.get(server).declare(server, new Owner.OfRealm(side), new Owner.OfRealm(target),
                War.Goal.TRIBUTE, reason, Optional.empty(), WarSettings.get().armies().warningTicks());
        setDirty();
        return r.message();
    }

    // ---- AI

    /**
     * How a great power or AI realm weighs an offer: what it gets minus what it gives (times {@code keep_factor}),
     * plus agreements' worth and its current feeling ({@code relation_bias}). It accepts at 0 or more.
     */
    public static double aiAnswer(MinecraftServer server, Offer offer) {
        DiplomacyData d = DiplomacyData.get();
        Terms t = offer.terms();
        Relation rel = Factions.relation(server, offer.to(), offer.from());
        double v = d.relationBias().getOrDefault(rel.getSerializedName(), 0.0);
        v += t.giveCoins() * d.coinValue() - t.askCoins() * d.coinValue() * d.keepFactor();
        v += t.giveLand() * d.landValue() - t.askLand() * d.landValue() * d.keepFactor();
        v += (t.giveColony().isPresent() ? d.colonyValue() : 0)
                - (t.askColony().isPresent() ? d.colonyValue() * d.keepFactor() : 0);
        if (t.trade()) {
            v += rel.isBad() ? -d.tradeValue() : d.tradeValue();
        }
        List<War> wars = wars(server, offer.from(), offer.to());
        if (t.endWar() && !wars.isEmpty()) {
            long since = wars.stream().mapToLong(w -> w.declared()).min().orElse(offer.made());
            v += d.peaceValue() + d.warDayValue() * Math.max(0, (offer.made() - since) / (double) d.dayTicks());
            v -= d.relationBias().getOrDefault(rel.getSerializedName(), 0.0); // war's own bitterness counted already
        }
        if (t.alliance()) {
            v += rel.isGood() ? d.allianceValue() : -d.allianceValue() * 3;
        }
        if (t.vassal() == Terms.Vassal.WE_SERVE) {
            v += d.vassalValue();
        } else if (t.vassal() == Terms.Vassal.THEY_SERVE) {
            v -= 1000; // great powers swear to nobody
        }
        return v;
    }

    // ---- bits

    public String summary(MinecraftServer server, Offer offer) {
        return String.join("; ", offer.terms().describe(name(server, offer.from()), name(server, offer.to()),
                id -> Colonies.get(server).get(id).map(Colony::name).orElse("a lost colony")));
    }

    private void log(MinecraftServer server, ResourceLocation type, Offer offer, String what) {
        CompoundTag tag = new CompoundTag();
        tag.putString("from", offer.from().toString());
        tag.putString("to", offer.to().toString());
        tag.putString("what", what);
        Terms.CODEC.encodeStart(NbtOps.INSTANCE, offer.terms()).result().ifPresent(n -> tag.put("terms", n));
        history(server, type, tag);
    }

    private static void history(MinecraftServer server, ResourceLocation type, CompoundTag tag) {
        HistoryLog.get(server).append(new HistoryEntry(server.overworld().getGameTime(), type, Level.OVERWORLD,
                Optional.empty(), tag));
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("dataVersion", DATA_VERSION);
        TREATIES.encodeStart(NbtOps.INSTANCE, List.copyOf(treaties.values())).result()
                .ifPresent(n -> tag.put("treaties", n));
        OFFERS.encodeStart(NbtOps.INSTANCE, List.copyOf(offers.values())).result().ifPresent(n -> tag.put("offers", n));
        ANSWERS.encodeStart(NbtOps.INSTANCE, List.copyOf(answers)).result().ifPresent(n -> tag.put("answers", n));
        return tag;
    }

    public static Treaties load(CompoundTag tag, HolderLookup.Provider registries) {
        Treaties t = new Treaties();
        // dataVersion 1 is the first format; later versions migrate here
        Tag tr = tag.get("treaties");
        if (tr != null) {
            TREATIES.parse(NbtOps.INSTANCE, tr).result().ifPresent(l -> l.forEach(x -> t.treaties.put(x.id(), x)));
        }
        Tag of = tag.get("offers");
        if (of != null) {
            OFFERS.parse(NbtOps.INSTANCE, of).result().ifPresent(l -> l.forEach(x -> t.offers.put(x.id(), x)));
        }
        Tag an = tag.get("answers");
        if (an != null) {
            ANSWERS.parse(NbtOps.INSTANCE, an).result().ifPresent(t.answers::addAll);
        }
        return t;
    }
}
