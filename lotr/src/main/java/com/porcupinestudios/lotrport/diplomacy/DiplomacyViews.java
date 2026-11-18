package com.porcupinestudios.lotrport.diplomacy;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.border.Borders;
import com.porcupinestudios.lotrport.colony.Colonies;
import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.faction.Diplomacy;
import com.porcupinestudios.lotrport.faction.Faction;
import com.porcupinestudios.lotrport.faction.Factions;
import com.porcupinestudios.lotrport.faction.Realms;
import com.porcupinestudios.lotrport.faction.Relation;
import com.porcupinestudios.lotrport.ui.Action;
import com.porcupinestudios.lotrport.ui.Cell;
import com.porcupinestudios.lotrport.ui.Column;
import com.porcupinestudios.lotrport.ui.Page;
import com.porcupinestudios.lotrport.ui.Stat;
import com.porcupinestudios.lotrport.ui.TableBuilder;
import com.porcupinestudios.lotrport.ui.Tone;
import com.porcupinestudios.lotrport.ui.View;
import com.porcupinestudios.lotrport.ui.ViewProvider;
import com.porcupinestudios.lotrport.ui.Views;
import com.porcupinestudios.lotrport.war.War;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The diplomacy table's screen ({@code lotr_port:diplomacy}): every realm and power with your relation, treaties and
 * wars (declare war, or open an offer); Incoming (offers to you: the list, or one in full when the context is its id;
 * accept or decline here, never in chat); New offer (context = the other side): coins, land and colonies each way,
 * trade agreement, truce, alliance, vassalage, ending wars; Sent (your offers waiting, withdraw; what became of recent
 * offers); treaties (break); wars (offer peace). Answers to actions show in the screen (UiNet.Notice). Only a kingdom's ruler acts; everyone else sees why not. Drafts are kept per
 * player and other side for the session.
 */
public final class DiplomacyViews implements ViewProvider {
    public static final ResourceLocation ID = LotrPort.id("diplomacy");
    private static final Map<String, Terms> DRAFTS = new HashMap<>();

    public static void register() {
        Views.register(ID, new DiplomacyViews());
    }

    private static Component t(String key, String english) {
        return Component.translatableWithFallback("ui.lotr_port.diplomacy." + key, english);
    }

    private static Terms draft(ServerPlayer p, ResourceLocation other) {
        return DRAFTS.getOrDefault(p.getUUID() + "|" + other, Terms.NONE);
    }

    private static void setDraft(ServerPlayer p, ResourceLocation other, Terms terms) {
        DRAFTS.put(p.getUUID() + "|" + other, terms);
    }

    /** The sides one may treat with: kingdoms and realms, and playable great powers that hold land or have a map. */
    static List<ResourceLocation> sides(MinecraftServer server) {
        List<ResourceLocation> out = new ArrayList<>();
        for (Faction f : Factions.all(server)) {
            if (f.realm() || (f.playable() && f.fixedAlignment().isEmpty()
                    && Factions.definitions().containsKey(f.id())
                    && !"none".equals(Factions.definitions().get(f.id()).region()))) {
                out.add(f.id());
            }
        }
        return out;
    }

    @Override
    public Optional<View> build(ServerPlayer player, String context, String tab) {
        MinecraftServer server = player.server;
        Optional<ResourceLocation> side = Treaties.sideOf(player);
        long incoming = side.map(s -> Treaties.get(server).offers().stream().filter(o -> o.to().equals(s)).count())
                .orElse(0L);
        List<View.Tab> tabs = List.of(new View.Tab("realms", t("tab.realms", "Realms")),
                new View.Tab("incoming", incoming > 0
                        ? Component.translatableWithFallback("ui.lotr_port.diplomacy.tab.incoming_n", "Incoming (%s)",
                                incoming)
                        : t("tab.incoming", "Incoming")),
                new View.Tab("offer", t("tab.offer", "New offer")), new View.Tab("sent", t("tab.sent", "Sent")),
                new View.Tab("treaties", t("tab.treaties", "Treaties")), new View.Tab("wars", t("tab.wars", "Wars")));
        String which = tabs.stream().anyMatch(x -> x.id().equals(tab)) ? tab : "realms";
        Component title = side.map(s -> (Component) Component.literal("Diplomacy of " + Treaties.name(server, s)))
                .orElse(t("title", "Diplomacy"));
        Page page = switch (which) {
            case "offer" -> offerPage(server, player, side, context);
            case "incoming" -> incomingPage(server, side, context);
            case "sent" -> sentPage(server, side);
            case "treaties" -> treatiesPage(server, side);
            case "wars" -> warsPage(server, side);
            default -> realmsPage(server, side);
        };
        return Optional.of(new View(ID, context, title, tabs, which, page, Optional.empty()));
    }

    private static Component notRuler() {
        return t("not_ruler", "Only a kingdom's ruler decides (/lotr_kingdom found)");
    }

    private Page realmsPage(MinecraftServer server, Optional<ResourceLocation> side) {
        Treaties treaties = Treaties.get(server);
        Map<ResourceLocation, Integer> gains = Borders.get(server).gains();
        List<ResourceLocation> rows = sides(server).stream().filter(s -> !side.equals(Optional.of(s))).toList();
        TableBuilder<ResourceLocation> b = TableBuilder.of(rows, ResourceLocation::toString)
                .column(Column.text("name", t("col.name", "Realm"), 4), s -> Cell.text(Treaties.name(server, s)))
                .column(Column.text("kind", t("col.kind", "Kind"), 2), s -> Cell.text(kind(server, s), Tone.MUTED))
                .column(Column.text("relation", t("col.relation", "Relation"), 2), s -> side.map(me -> {
                    Relation r = Factions.relation(server, me, s);
                    return Cell.text(r.getSerializedName().replace('_', ' '),
                            r.isGood() ? Tone.GOOD : r.isBad() ? Tone.BAD : Tone.NORMAL);
                }).orElse(Cell.text("-", Tone.MUTED)))
                .column(Column.text("treaties", t("col.treaties", "Treaties"), 3), s -> side.map(me -> {
                    List<String> k = new ArrayList<>();
                    for (Treaties.Kind kind : Treaties.Kind.values()) {
                        if (treaties.has(me, s, kind)) {
                            k.add(kind.getSerializedName());
                        }
                    }
                    if (Diplomacy.get(server).overlord(s).equals(Optional.of(me))) {
                        k.add("vassal");
                    }
                    if (Diplomacy.get(server).overlord(me).equals(Optional.of(s))) {
                        k.add("overlord");
                    }
                    return Cell.text(k.isEmpty() ? "-" : String.join(", ", k));
                }).orElse(Cell.text("-", Tone.MUTED)))
                .column(Column.text("war", t("col.war", "War"), 1), s -> side.map(me ->
                        Treaties.wars(server, me, s).isEmpty() ? Cell.text("-", Tone.MUTED) : Cell.text("at war", Tone.BAD))
                        .orElse(Cell.text("-", Tone.MUTED)))
                .column(Column.number("land", t("col.land", "Land won"), 1), s -> Cell.number(gains.getOrDefault(s, 0)))
                .filterBy("kind", t("filter.kind", "Kind"), "kind:", s -> kind(server, s), Component::literal)
                .sortBy("name", false)
                .open("offer");
        if (side.isPresent()) {
            b.rowAction(Action.of("offer", t("act.offer", "Make an offer")));
            b.rowAction(Action.of("war", t("act.war", "Declare war")).confirmed());
        } else {
            b.rowAction(Action.disabled("offer", t("act.offer", "Make an offer"), notRuler()));
            b.rowAction(Action.disabled("war", t("act.war", "Declare war"), notRuler()));
        }
        return b.build();
    }

    private static String kind(MinecraftServer server, ResourceLocation s) {
        return Realms.get(server).get(s).map(r -> r.ai() ? "AI realm" : "kingdom").orElse("great power");
    }

    private Page offerPage(MinecraftServer server, ServerPlayer player, Optional<ResourceLocation> side,
            String context) {
        ResourceLocation other = ResourceLocation.tryParse(context);
        if (side.isEmpty() || other == null || !sides(server).contains(other) || other.equals(side.get())) {
            return new Page.Stats(List.of(new Page.Section(t("offer.none", "No realm picked"), List.of(Stat.of(
                    side.isEmpty() ? notRuler() : t("offer.pick", "Pick a realm on the Realms tab"), "")))), List.of());
        }
        ResourceLocation me = side.get();
        Terms d = draft(player, other);
        boolean atWar = !Treaties.wars(server, me, other).isEmpty();
        boolean answersAtOnce = Treaties.ruler(server, other).isEmpty();
        DiplomacyData data = DiplomacyData.get();
        String them = Treaties.name(server, other);
        List<Stat> about = new ArrayList<>();
        Relation rel = Factions.relation(server, me, other);
        about.add(Stat.of(t("offer.relation", "Relation"), rel.getSerializedName().replace('_', ' '),
                rel.isGood() ? Tone.GOOD : rel.isBad() ? Tone.BAD : Tone.NORMAL));
        about.add(Stat.of(t("offer.war", "War"), atWar ? "at war" : "at peace", atWar ? Tone.BAD : Tone.GOOD));
        about.add(Stat.of(t("offer.answers", "Answers"), answersAtOnce ? "at once (its council weighs it)"
                : "when its ruler next looks", Tone.MUTED));

        long myCoins = Treaties.treasury(server, me);
        long theirCoins = Treaties.treasury(server, other);
        List<Stat> give = new ArrayList<>();
        give.add(Stat.of(t("give.coins", "Coins"), d.giveCoins() + " of " + myCoins)
                .withStepper(new Stat.Stepper("give_coins", (int) Math.min(Integer.MAX_VALUE, d.giveCoins()), 0,
                        (int) Math.min(Integer.MAX_VALUE, myCoins), 50)));
        give.add(Stat.of(t("give.land", "Land (squares)"), String.valueOf(d.giveLand()))
                .withStepper(new Stat.Stepper("give_land", d.giveLand(), 0, data.maxLand(), 1)));
        give.add(Stat.of(t("give.colony", "Colony"), colonyName(server, d.giveColony()))
                .withPicker(colonyPicker(server, "give_colony", me, d.giveColony())));
        List<Stat> ask = new ArrayList<>();
        ask.add(Stat.of(t("ask.coins", "Coins"), d.askCoins() + " of " + theirCoins)
                .withStepper(new Stat.Stepper("ask_coins", (int) Math.min(Integer.MAX_VALUE, d.askCoins()), 0,
                        (int) Math.min(Integer.MAX_VALUE, theirCoins), 50)));
        ask.add(Stat.of(t("ask.land", "Land (squares)"), String.valueOf(d.askLand()))
                .withStepper(new Stat.Stepper("ask_land", d.askLand(), 0, data.maxLand(), 1)));
        ask.add(Stat.of(t("ask.colony", "Colony"), colonyName(server, d.askColony()))
                .withPicker(colonyPicker(server, "ask_colony", other, d.askColony())));
        List<Stat> pacts = new ArrayList<>();
        if (atWar) {
            pacts.add(Stat.of(t("pact.peace", "End the war"), d.endWar() ? "yes" : "no", d.endWar() ? Tone.GOOD : Tone.NORMAL)
                    .withPicker(yesNo("end_war", d.endWar())));
        }
        pacts.add(Stat.of(t("pact.trade", "Trade agreement"), d.trade() ? "yes" : "no").withPicker(yesNo("trade", d.trade())));
        pacts.add(Stat.of(t("pact.truce", "Truce (days)"), String.valueOf(d.truceDays()))
                .withStepper(new Stat.Stepper("truce", d.truceDays(), 0, 120, 5)));
        pacts.add(Stat.of(t("pact.alliance", "Alliance"), d.alliance() ? "yes" : "no")
                .withPicker(yesNo("alliance", d.alliance())));
        pacts.add(Stat.of(t("pact.vassal", "Vassalage"), switch (d.vassal()) {
            case THEY_SERVE -> them + " swears to you";
            case WE_SERVE -> "you swear to " + them;
            default -> "none";
        }).withPicker(new Stat.Picker("vassal", d.vassal().getSerializedName(), List.of(
                Stat.Choice.of("none", Component.literal("None")),
                Stat.Choice.of("they_serve", Component.literal(them + " swears to you")),
                Stat.Choice.of("we_serve", Component.literal("You swear to " + them))))));
        List<Stat> summary = new ArrayList<>();
        Treaties treaties = Treaties.get(server);
        for (String line : d.describe("You", them, id -> colonyName(server, Optional.of(id)))) {
            summary.add(Stat.of(Component.literal(line), ""));
        }
        Optional<Component> problem = d.empty() ? Optional.of(t("offer.empty", "Add something to offer or ask"))
                : treaties.check(server, me, other, d);
        problem.ifPresent(p -> summary.add(Stat.of(p, "", Tone.WARN)));
        Action send = problem.isEmpty() ? Action.of("send", t("act.send", "Send offer"))
                : Action.disabled("send", t("act.send", "Send offer"), problem.get());
        return new Page.Stats(List.of(
                new Page.Section(Component.literal("With " + them), about),
                new Page.Section(t("offer.give", "You give"), give),
                new Page.Section(t("offer.ask", "You ask"), ask),
                new Page.Section(t("offer.pacts", "Agreements"), pacts),
                new Page.Section(t("offer.summary", "The offer"), summary)),
                List.of(send, Action.of("clear", t("act.clear", "Clear"))));
    }

    private static Stat.Picker yesNo(String action, boolean on) {
        return new Stat.Picker(action, on ? "yes" : "no", List.of(Stat.Choice.of("no", Component.literal("No")),
                Stat.Choice.of("yes", Component.literal("Yes"))));
    }

    private static String colonyName(MinecraftServer server, Optional<UUID> id) {
        return id.flatMap(i -> Colonies.get(server).get(i)).map(Colony::name).orElse("none");
    }

    private static Stat.Picker colonyPicker(MinecraftServer server, String action, ResourceLocation side,
            Optional<UUID> current) {
        List<Stat.Choice> choices = new ArrayList<>();
        choices.add(Stat.Choice.of("none", Component.literal("None")));
        for (Colony c : Treaties.colonies(server, side)) {
            choices.add(Stat.Choice.of(c.id().toString(), Component.literal(c.name())));
        }
        return new Stat.Picker(action, current.map(UUID::toString).orElse("none"), choices);
    }

    private static String days(MinecraftServer server, long until) {
        long left = Math.max(0, until - server.overworld().getGameTime());
        return left / Math.max(1, DiplomacyData.get().dayTicks()) + " days";
    }

    /**
     * Offers to you: the list (double-click or Look closer for the whole offer), or one offer in full when the context
     * is its id, with what you would give and get and whether it can be carried out now.
     */
    private Page incomingPage(MinecraftServer server, Optional<ResourceLocation> side, String context) {
        Treaties treaties = Treaties.get(server);
        Optional<Treaties.Offer> one = Optional.empty();
        try {
            one = treaties.offer(UUID.fromString(context)).filter(o -> side.equals(Optional.of(o.to())));
        } catch (IllegalArgumentException e) {
            // the list
        }
        if (one.isPresent()) {
            Treaties.Offer o = one.get();
            String from = Treaties.name(server, o.from());
            List<Stat> head = new ArrayList<>();
            head.add(Stat.of(t("in.from", "From"), from));
            head.add(Stat.of(t("in.relation", "Relation"), Factions.relation(server, o.to(), o.from())
                    .getSerializedName().replace('_', ' ')));
            head.add(Stat.of(t("in.expires", "Stands for"), days(server, o.expires()), Tone.MUTED));
            List<Stat> terms = new ArrayList<>();
            for (String line : o.terms().flipped().describe("You", from,
                    id -> colonyName(server, Optional.of(id)))) {
                terms.add(Stat.of(Component.literal(line), ""));
            }
            Optional<Component> problem = treaties.check(server, o.from(), o.to(), o.terms());
            problem.ifPresent(p -> terms.add(Stat.of(t("in.cannot", "Can't be carried out now: ").copy().append(p), "",
                    Tone.WARN)));
            Action accept = problem.isEmpty() ? Action.of("accept", t("act.accept", "Accept")).confirmed()
                    : Action.disabled("accept", t("act.accept", "Accept"), problem.get());
            return new Page.Stats(List.of(new Page.Section(Component.literal("Offer from " + from), head),
                    new Page.Section(t("in.terms", "Its terms"), terms)),
                    List.of(accept, Action.of("decline", t("act.decline", "Decline")),
                            Action.of("list", t("act.list", "All offers"))));
        }
        List<Treaties.Offer> mine = side.map(s -> treaties.offers().stream().filter(o -> o.to().equals(s)).toList())
                .orElse(List.of());
        TableBuilder<Treaties.Offer> b = TableBuilder.of(mine, o -> o.id().toString())
                .column(Column.text("from", t("col.from", "From"), 3), o -> Cell.text(Treaties.name(server, o.from())))
                .column(Column.text("terms", t("col.terms", "Terms"), 8), o -> Cell.text(String.join("; ", o.terms()
                        .flipped().describe("You", Treaties.name(server, o.from()),
                                id -> colonyName(server, Optional.of(id))))))
                .column(Column.text("ok", t("col.ok", "Can do"), 2), o -> treaties.check(server, o.from(), o.to(),
                        o.terms()).isEmpty() ? Cell.text("yes", Tone.GOOD) : Cell.text("not now", Tone.WARN))
                .column(Column.text("left", t("col.expires", "Stands for"), 2), o -> Cell.text(days(server,
                        o.expires()), Tone.MUTED))
                .open("look")
                .empty(side.isEmpty() ? notRuler() : t("in.none", "No offers waiting for you"));
        if (side.isPresent()) {
            b.rowAction(Action.of("look", t("act.look", "Look closer")));
            b.rowAction(Action.of("accept", t("act.accept", "Accept")).confirmed());
            b.rowAction(Action.of("decline", t("act.decline", "Decline")));
        }
        return b.build();
    }

    /** A row of the Sent tab: an offer still waiting, or what became of one (either way). */
    private record SentRow(String id, Treaties.Offer offer, String status, boolean waiting) {}

    /** Your offers still waiting, and what became of recent offers to and from you. */
    private Page sentPage(MinecraftServer server, Optional<ResourceLocation> side) {
        Treaties treaties = Treaties.get(server);
        List<SentRow> rows = new ArrayList<>();
        if (side.isPresent()) {
            for (Treaties.Offer o : treaties.offers()) {
                if (o.from().equals(side.get())) {
                    rows.add(new SentRow(o.id().toString(), o, "waiting", true));
                }
            }
            int n = 0;
            for (Treaties.Answer a : treaties.answers(side.get())) {
                rows.add(new SentRow("answer/" + n++, a.offer(), a.result(), false));
            }
        }
        TableBuilder<SentRow> b = TableBuilder.of(rows, SentRow::id)
                .column(Column.text("way", t("col.way", "Way"), 1), r -> r.offer().from().equals(side.orElse(null))
                        ? Cell.text("yours", Tone.MUTED) : Cell.text("to you", Tone.NORMAL))
                .column(Column.text("with", t("col.with", "With"), 3), r -> Cell.text(Treaties.name(server,
                        r.offer().from().equals(side.orElse(null)) ? r.offer().to() : r.offer().from())))
                .column(Column.text("terms", t("col.terms", "Terms"), 7), r -> Cell.text(treaties.summary(server,
                        r.offer())))
                .column(Column.text("status", t("col.status", "Answer"), 2), r -> Cell.text(r.status(),
                        switch (r.status()) {
                            case "accepted" -> Tone.GOOD;
                            case "refused", "declined" -> Tone.BAD;
                            case "waiting" -> Tone.NORMAL;
                            default -> Tone.MUTED;
                        }))
                .filterBy("status", t("filter.status", "Answer"), "status:", SentRow::status, Component::literal)
                .empty(side.isEmpty() ? notRuler() : t("sent.none", "No offers yet: make one on New offer"));
        if (side.isPresent()) {
            b.rowAction(Action.of("withdraw", t("act.withdraw", "Withdraw")));
        }
        return b.build();
    }

    private Page treatiesPage(MinecraftServer server, Optional<ResourceLocation> side) {
        List<Treaties.Treaty> list = side.map(s -> Treaties.get(server).of(s)).orElse(List.of());
        long now = server.overworld().getGameTime();
        int day = Math.max(1, DiplomacyData.get().dayTicks());
        TableBuilder<Treaties.Treaty> b = TableBuilder.of(list, x -> x.id().toString())
                .column(Column.text("with", t("col.with", "With"), 3), x -> Cell.text(Treaties.name(server,
                        x.other(side.get()))))
                .column(Column.text("kind", t("col.treaty", "Treaty"), 2), x -> Cell.text(x.kind().getSerializedName()))
                .column(Column.number("since", t("col.since", "Days held"), 1), x -> Cell.number((now - x.since()) / day))
                .column(Column.text("ends", t("col.ends", "Ends"), 2), x -> Cell.text(x.until() == 0 ? "when broken"
                        : "in " + Math.max(0, (x.until() - now) / day) + " days", Tone.MUTED))
                .empty(t("treaties.none", "No treaties"));
        if (side.isPresent()) {
            b.rowAction(Action.of("break", t("act.break", "Break")).confirmed());
        }
        return b.build();
    }

    private Page warsPage(MinecraftServer server, Optional<ResourceLocation> side) {
        List<ResourceLocation> enemies = new ArrayList<>();
        if (side.isPresent()) {
            for (ResourceLocation s : sides(server)) {
                if (!s.equals(side.get()) && !Treaties.wars(server, side.get(), s).isEmpty()) {
                    enemies.add(s);
                }
            }
        }
        long now = server.overworld().getGameTime();
        int day = Math.max(1, DiplomacyData.get().dayTicks());
        TableBuilder<ResourceLocation> b = TableBuilder.of(enemies, ResourceLocation::toString)
                .column(Column.text("enemy", t("col.enemy", "Enemy"), 3), s -> Cell.text(Treaties.name(server, s)))
                .column(Column.number("wars", t("col.wars", "Wars"), 1), s -> Cell.number(
                        Treaties.wars(server, side.get(), s).size()))
                .column(Column.text("state", t("col.state", "State"), 2), s -> {
                    boolean active = Treaties.wars(server, side.get(), s).stream()
                            .anyMatch(w -> w.state() == War.State.ACTIVE);
                    return Cell.text(active ? "fighting" : "warned", active ? Tone.BAD : Tone.WARN);
                })
                .column(Column.number("days", t("col.days", "Days"), 1), s -> Cell.number(Treaties.wars(server,
                        side.get(), s).stream().mapToLong(w -> (now - w.declared()) / day).max().orElse(0)))
                .empty(t("wars.none", "At peace with everyone"));
        if (side.isPresent()) {
            b.rowAction(Action.of("peace", t("act.peace", "Offer peace")));
        }
        return b.build();
    }

    @Override
    public Result act(ServerPlayer player, String context, String tab, String action, String row, int value) {
        MinecraftServer server = player.server;
        Optional<ResourceLocation> side = Treaties.sideOf(player);
        if (side.isEmpty()) {
            return Result.say(notRuler());
        }
        ResourceLocation me = side.get();
        Treaties treaties = Treaties.get(server);
        switch (action) {
            case "offer" -> {
                ResourceLocation other = ResourceLocation.tryParse(row);
                return other == null ? Result.NONE : Result.go(new View.Target(ID, other.toString(), "offer"));
            }
            case "war" -> {
                ResourceLocation other = ResourceLocation.tryParse(row);
                if (other == null || !sides(server).contains(other)) {
                    return Result.NONE;
                }
                return Result.say(treaties.declareWar(server, player.getUUID(), me, other,
                        "declared at the diplomacy table"));
            }
            case "peace" -> {
                ResourceLocation other = ResourceLocation.tryParse(row);
                if (other == null) {
                    return Result.NONE;
                }
                setDraft(player, other, draft(player, other).withEndWar(true)
                        .withTruceDays(Math.max(10, draft(player, other).truceDays())));
                return Result.go(new View.Target(ID, other.toString(), "offer"));
            }
            case "look" -> {
                return Result.go(new View.Target(ID, row, "incoming"));
            }
            case "list" -> {
                return Result.go(new View.Target(ID, "", "incoming"));
            }
            case "accept", "decline", "withdraw", "break" -> {
                UUID id;
                try {
                    // a row's id in a list, or the offer open in full (its id is the context)
                    id = UUID.fromString(row.isEmpty() ? context : row);
                } catch (IllegalArgumentException e) {
                    return Result.NONE;
                }
                Component said = switch (action) {
                    case "accept" -> treaties.accept(server, player.getUUID(), id);
                    case "decline", "withdraw" -> treaties.decline(server, player.getUUID(), id);
                    default -> treaties.breakTreaty(server, player.getUUID(), id);
                };
                // from an offer open in full, back to the list (the offer is gone)
                return "incoming".equals(tab) && row.isEmpty()
                        ? new Result(Optional.of(said), Optional.of(new View.Target(ID, "", "incoming")))
                        : Result.say(said);
            }
            default -> { }
        }
        // the offer builder: context is the other side
        ResourceLocation other = ResourceLocation.tryParse(context);
        if (other == null || other.equals(me) || !sides(server).contains(other)) {
            return Result.NONE;
        }
        Terms d = draft(player, other);
        DiplomacyData data = DiplomacyData.get();
        switch (action) {
            case "give_coins" -> d = d.withGiveCoins(Math.max(0, Math.min(value, Treaties.treasury(server, me))));
            case "ask_coins" -> d = d.withAskCoins(Math.max(0, Math.min(value, Treaties.treasury(server, other))));
            case "give_land" -> d = d.withGiveLand(Math.max(0, Math.min(value, data.maxLand())));
            case "ask_land" -> d = d.withAskLand(Math.max(0, Math.min(value, data.maxLand())));
            case "give_colony" -> d = d.withGiveColony(colony(server, row, me));
            case "ask_colony" -> d = d.withAskColony(colony(server, row, other));
            case "trade" -> d = d.withTrade("yes".equals(row));
            case "alliance" -> d = d.withAlliance("yes".equals(row));
            case "end_war" -> d = d.withEndWar("yes".equals(row));
            case "truce" -> d = d.withTruceDays(Math.max(0, Math.min(value, 120)));
            case "vassal" -> d = d.withVassal(switch (row) {
                case "they_serve" -> Terms.Vassal.THEY_SERVE;
                case "we_serve" -> Terms.Vassal.WE_SERVE;
                default -> Terms.Vassal.NONE;
            });
            case "clear" -> d = Terms.NONE;
            case "send" -> {
                Component said = treaties.propose(server, player.getUUID(), me, other, d);
                setDraft(player, other, Terms.NONE);
                // the answer (an AI's at once, a ruler's later) shows on the Sent tab
                return new Result(Optional.of(said), Optional.of(new View.Target(ID, "", "sent")));
            }
            default -> {
                return Result.NONE;
            }
        }
        setDraft(player, other, d);
        return Result.NONE;
    }

    private static Optional<UUID> colony(MinecraftServer server, String row, ResourceLocation side) {
        try {
            UUID id = UUID.fromString(row);
            return Colonies.get(server).get(id).filter(c -> c.faction().equals(side)).map(Colony::id);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
