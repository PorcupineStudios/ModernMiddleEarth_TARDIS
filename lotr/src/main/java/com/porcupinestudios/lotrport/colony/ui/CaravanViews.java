package com.porcupinestudios.lotrport.colony.ui;

import static com.porcupinestudios.lotrport.colony.ui.ColonyViews.tr;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.colony.Colonies;
import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.colony.members.Perm;
import com.porcupinestudios.lotrport.colony.members.Permissions;
import com.porcupinestudios.lotrport.colony.work.Stores;
import com.porcupinestudios.lotrport.journey.CaravanBook;
import com.porcupinestudios.lotrport.journey.CaravanRules;
import com.porcupinestudios.lotrport.journey.Caravans;
import com.porcupinestudios.lotrport.journey.Journey;
import com.porcupinestudios.lotrport.journey.JourneyData;
import com.porcupinestudios.lotrport.journey.Journeys;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Caravans on screen. The colony view's Caravans tab lists its routes (manual, every N days, or "when X's item is
 * below/above N") with what is on the road; a route has its own editor ({@code lotr_port:caravan_route}: where, when,
 * what to take and bring, each line a fixed amount or "the excess over N"), and the colony's trade offers and floors
 * have theirs ({@code lotr_port:caravan_trade}). Anyone in the colony may look; changes and sending need
 * {@link Perm#CARAVANS}. Steppers move by 1, 10 with Shift, 64 with Ctrl.
 */
public final class CaravanViews {
    public static final ResourceLocation ROUTE = LotrPort.id("caravan_route");
    public static final ResourceLocation TRADE = LotrPort.id("caravan_trade");
    static final String TAB = "caravans";
    static final int MAX_COUNT = 100000;
    static final String REMOVE = "-";

    private CaravanViews() {}

    public static void register() {
        Views.register(ROUTE, new RouteProvider());
        Views.register(TRADE, new TradeProvider());
    }

    static boolean may(ServerPlayer p, Colony c) {
        return Permissions.can(p.getUUID(), c, Perm.CARAVANS);
    }

    static Component noPerm(Colony c) {
        return tr("caravan.no_perm", "You may not run %s's caravans", c.name());
    }

    static String itemName(String id) {
        ResourceLocation r = ResourceLocation.tryParse(id);
        return r == null ? id : BuiltInRegistries.ITEM.get(r).getDescription().getString();
    }

    static String goods(List<CaravanRules.Amount> l) {
        if (l.isEmpty()) {
            return "-";
        }
        List<String> out = new ArrayList<>();
        for (CaravanRules.Amount a : l) {
            out.add(a.excess() ? itemName(a.item()) + " over " + a.over() + (a.count() > 0 ? " (max " + a.count()
                    + ")" : "") : a.count() + " " + itemName(a.item()));
        }
        return String.join(", ", out);
    }

    static String colonyName(MinecraftServer server, UUID id) {
        return Colonies.get(server).get(id).map(Colony::name).orElse("(gone)");
    }

    static String when(MinecraftServer server, CaravanBook.Route r) {
        return switch (r.mode()) {
            case "every" -> "Every " + r.every() + (r.every() == 1 ? " day" : " days");
            case "when" -> {
                CaravanRules.Trigger t = r.when().get();
                yield "When " + colonyName(server, t.colony()) + "'s " + itemName(t.item()) + (t.above() ? " > "
                        : " < ") + t.threshold();
            }
            default -> "By hand";
        };
    }

    static Optional<Journey> out(MinecraftServer server, Colony c, int route) {
        for (Journey j : Journeys.get(server).from(c.id())) {
            if (j.kind.equals(Caravans.KIND) && j.stage != Journey.Stage.DONE && j.extra.contains("route")
                    && j.extra.getCompound("route").getInt("id") == route) {
                return Optional.of(j);
            }
        }
        return Optional.empty();
    }

    static String status(MinecraftServer server, Journey j) {
        int min = (int) Math.ceil(j.secondsLeft(JourneyData.settings()) / 60);
        return switch (j.stage) {
            case PLANNING -> "Finding the way";
            case OUTBOUND -> "Going, " + min + " min";
            case RETURNING -> "Coming home, " + min + " min";
            default -> "Done";
        };
    }

    // ================================================================ the colony's Caravans tab

    static Page tab(ServerPlayer player, Colony c) {
        MinecraftServer server = player.server;
        boolean may = may(player, c);
        CaravanBook b = Caravans.book(c);
        Component why = noPerm(c);
        return TableBuilder.of(b.routes(), r -> Integer.toString(r.id()))
                .column(Column.text("name", tr("caravan.col.name", "Name"), 4), r -> Cell.text(r.title()))
                .column(Column.text("to", tr("caravan.col.to", "To"), 3), r -> Cell.text(colonyName(server,
                        r.to())))
                .column(Column.text("when", tr("caravan.col.when", "Goes"), 4), r -> Cell.text(when(server, r),
                        r.mode().equals("manual") ? Tone.MUTED : Tone.NORMAL))
                .column(Column.text("take", tr("caravan.col.take", "Takes"), 3), r -> Cell.text(goods(r.send())))
                .column(Column.text("bring", tr("caravan.col.bring", "Brings"), 3), r -> Cell.text(goods(r
                        .bring())))
                .column(Column.text("status", tr("caravan.col.status", "Status"), 3), r -> out(server, c,
                        r.id()).map(j -> Cell.text(status(server, j), Tone.GOOD)).orElse(Cell.text("-", Tone.MUTED)))
                .open("edit")
                .rowAction(may ? Action.of("send", tr("caravan.send", "Send now"))
                        : Action.disabled("send", tr("caravan.send", "Send now"), why))
                .rowAction(may ? Action.of("delete", tr("caravan.delete", "Delete")).confirmed()
                        : Action.disabled("delete", tr("caravan.delete", "Delete"), why))
                .button(may ? Action.of("new_route", tr("caravan.new", "New caravan"))
                        : Action.disabled("new_route", tr("caravan.new", "New caravan"), why))
                .button(Action.of("trade", tr("caravan.trades", "Trades")))
                .empty(tr("caravan.none", "No caravans yet: New caravan sets one up. Double-click one to edit it"))
                .build();
    }

    /** The tab's actions; empty when not ours. */
    static Optional<ViewProvider.Result> tabAct(ServerPlayer player, Colony c, String action, String row) {
        switch (action) {
            case "edit" -> {
                return Optional.of(ViewProvider.Result.go(new View.Target(ROUTE, c.id() + "/" + row, "")));
            }
            case "trade" -> {
                return Optional.of(ViewProvider.Result.go(new View.Target(TRADE, c.id().toString(), "offers")));
            }
            case "new_route", "send", "delete" -> {
                if (!may(player, c)) {
                    return Optional.of(ViewProvider.Result.say(noPerm(c)));
                }
            }
            default -> {
                return Optional.empty();
            }
        }
        CaravanBook b = Caravans.book(c);
        int id = parse(row);
        switch (action) {
            case "new_route" -> {
                Optional<Colony> to = targets(player.server, c).stream().findFirst();
                if (to.isEmpty()) {
                    return Optional.of(ViewProvider.Result.say(tr("caravan.nowhere",
                            "There is no friendly colony to send caravans to")));
                }
                CaravanBook.Route r = new CaravanBook.Route(b.next(), to.get().id(), List.of(), List.of(), 0, 1,
                        false, -1).named(to.get().name() + " run");
                Caravans.setBook(player.server, c, b.withRoute(r));
                return Optional.of(ViewProvider.Result.go(new View.Target(ROUTE, c.id() + "/" + r.id(), "")));
            }
            case "send" -> {
                return Optional.of(b.route(id).map(r -> ViewProvider.Result.say(Caravans.send(player.server, c, r)
                        .why())).orElse(ViewProvider.Result.NONE));
            }
            default -> {
                Caravans.setBook(player.server, c, b.withoutRoute(id));
                return Optional.of(ViewProvider.Result.NONE);
            }
        }
    }

    static int parse(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Colonies a caravan from here may go to: yours and friends', nearest first. */
    static List<Colony> targets(MinecraftServer server, Colony c) {
        return Colonies.get(server).all().stream()
                .filter(x -> Caravans.standing(server, c, x) != Caravans.Standing.NONE
                        && x.dimension().equals(c.dimension()))
                .sorted(Comparator.comparingDouble(x -> x.hall().distSqr(c.hall()))).toList();
    }

    static Map<ResourceLocation, Integer> items(MinecraftServer server, Colony c) {
        return new Stores(server, server.getLevel(c.dimension()), c).items(null);
    }

    /** Item choices: what the colonies hold, plus those already chosen; optionally a "remove" choice first. */
    static List<Stat.Choice> itemChoices(MinecraftServer server, List<Colony> from, Set<String> chosen,
            boolean removable) {
        Set<String> ids = new LinkedHashSet<>(chosen);
        for (Colony c : from) {
            items(server, c).keySet().forEach(r -> ids.add(r.toString()));
        }
        List<String> sorted = new ArrayList<>(ids);
        sorted.sort(Comparator.comparing(CaravanViews::itemName));
        List<Stat.Choice> out = new ArrayList<>();
        if (removable) {
            out.add(Stat.Choice.of(REMOVE, tr("caravan.remove_line", "(remove this line)")));
        }
        for (String id : sorted.subList(0, Math.min(200, sorted.size()))) {
            out.add(Stat.Choice.of(id, Component.literal(itemName(id))));
        }
        return out;
    }

    static Stat stepper(String label, String action, int value, int min, int max) {
        return Stat.of(Component.literal(label), Integer.toString(value))
                .withStepper(new Stat.Stepper(action, value, min, max, 1));
    }

    static Stat picker(String label, String shown, String action, String current, List<Stat.Choice> choices) {
        return Stat.of(Component.literal(label), shown).withPicker(new Stat.Picker(action, current, choices));
    }

    // ================================================================ one route

    record RouteCtx(Colony home, CaravanBook.Route route) {}

    static Optional<RouteCtx> routeCtx(MinecraftServer server, String context) {
        int slash = context.indexOf('/');
        if (slash < 0) {
            return Optional.empty();
        }
        return ColonyViews.colony(server, context.substring(0, slash)).flatMap(c -> Caravans.book(c)
                .route(parse(context.substring(slash + 1))).map(r -> new RouteCtx(c, r)));
    }

    static final class RouteProvider implements ViewProvider {
        @Override
        public Optional<View> build(ServerPlayer player, String context, String tab) {
            Optional<RouteCtx> ctx = routeCtx(player.server, context);
            if (ctx.isEmpty() || !Permissions.isMember(player.getUUID(), ctx.get().home())) {
                return Optional.empty();
            }
            Colony home = ctx.get().home();
            CaravanBook.Route r = ctx.get().route();
            MinecraftServer server = player.server;
            Optional<Colony> dest = Colonies.get(server).get(r.to());
            boolean own = dest.map(d -> d.founder().equals(home.founder())).orElse(false);

            List<Stat> where = new ArrayList<>();
            List<Stat.Choice> to = new ArrayList<>();
            for (Colony x : targets(server, home)) {
                boolean mine = x.founder().equals(home.founder());
                to.add(Stat.Choice.of(x.id().toString(), Component.literal(x.name() + (mine ? "" : " (friend)"))));
            }
            where.add(picker("To", colonyName(server, r.to()), "to", r.to().toString(), to));
            where.add(picker("Goes", when(server, r).startsWith("When") ? "When..." : when(server, r)
                    .startsWith("Every") ? "Every few days" : "By hand", "mode", r.mode(), List.of(
                            Stat.Choice.of("manual", Component.literal("By hand (Send now)")),
                            Stat.Choice.of("every", Component.literal("Every few days")),
                            Stat.Choice.of("when", Component.literal("When an item is below/above...")))));
            if (r.mode().equals("every")) {
                where.add(stepper("Every (days)", "every", r.every(), 1, 365));
            }
            if (r.when().isPresent()) {
                CaravanRules.Trigger t = r.when().get();
                List<Stat.Choice> watch = new ArrayList<>();
                List<Colony> watchable = new ArrayList<>();
                for (Colony x : Colonies.get(server).all()) {
                    if (x.founder().equals(home.founder()) || x.id().equals(r.to())) {
                        watchable.add(x);
                        watch.add(Stat.Choice.of(x.id().toString(), Component.literal(x.name())));
                    }
                }
                where.add(picker("Watch colony", colonyName(server, t.colony()), "w_colony", t.colony().toString(),
                        watch));
                Colonies.get(server).get(t.colony()).ifPresent(wc -> where.add(picker("Its item",
                        itemName(t.item()), "w_item", t.item(), itemChoices(server, List.of(wc), Set.of(t.item()),
                                false))));
                where.add(picker("Is", t.above() ? "above" : "below", "w_above", t.above() ? "above" : "below",
                        List.of(Stat.Choice.of("below", Component.literal("below")),
                                Stat.Choice.of("above", Component.literal("above")))));
                where.add(stepper("Than", "w_threshold", t.threshold(), 0, MAX_COUNT));
                int now = Colonies.get(server).get(t.colony()).map(wc -> new Stores(server,
                        server.getLevel(wc.dimension()), wc).count(ResourceLocation.parse(t.item()), null)).orElse(0);
                where.add(Stat.of(Component.literal("Now"), now + (t.met(now) ? " (would send)" : ""),
                        t.met(now) ? Tone.GOOD : Tone.MUTED));
            }
            where.add(stepper("Colonists going", "people", r.people(), 1, 16));
            if (!own) {
                where.add(picker("Goods that don't trade", r.sell() ? "Bring them home" : "Leave as supplies",
                        "sell", r.sell() ? "yes" : "no", List.of(
                                Stat.Choice.of("no", Component.literal("Leave as supplies")),
                                Stat.Choice.of("yes", Component.literal("Bring them home")))));
            }
            out(server, home, r.id()).ifPresent(j -> where.add(Stat.of(Component.literal("Now"), status(server, j),
                    Tone.GOOD)));

            List<Colony> takeFrom = List.of(home);
            List<Colony> bringFrom = own ? List.of(dest.get()) : List.of();
            List<Page.Section> sections = new ArrayList<>();
            sections.add(new Page.Section(tr("caravan.where", "Where and when"), where));
            sections.add(lines("Take from " + home.name(), "t", r.send(), server, takeFrom, home));
            if (own || !r.bring().isEmpty()) {
                sections.add(lines("Bring back from " + colonyName(server, r.to()), "b", r.bring(), server,
                        bringFrom, dest.orElse(home)));
            } else {
                sections.add(new Page.Section(tr("caravan.bring", "Bring back"), List.of(Stat.of(Component.literal(
                        "From a friend's colony: what its trade offers give for what you take"), "", Tone.MUTED))));
            }
            boolean may = may(player, home);
            Component why = noPerm(home);
            List<Action> buttons = new ArrayList<>();
            buttons.add(may ? Action.of("add_t", tr("caravan.add_take", "Add to take"))
                    : Action.disabled("add_t", tr("caravan.add_take", "Add to take"), why));
            if (own) {
                buttons.add(may ? Action.of("add_b", tr("caravan.add_bring", "Add to bring"))
                        : Action.disabled("add_b", tr("caravan.add_bring", "Add to bring"), why));
            }
            buttons.add(may && out(server, home, r.id()).isEmpty() ? Action.of("send", tr("caravan.send",
                    "Send now")) : Action.disabled("send", tr("caravan.send", "Send now"), may
                            ? tr("caravan.on_road", "One of this route's caravans is on the road") : why));
            buttons.add(may ? Action.of("delete", tr("caravan.delete", "Delete")).confirmed()
                    : Action.disabled("delete", tr("caravan.delete", "Delete"), why));
            String sub = when(server, r) + " to " + colonyName(server, r.to()) + out(server, home, r.id())
                    .map(j -> " - " + status(server, j)).orElse("");
            Page page = new Page.Profile(Optional.empty(), Component.literal(r.title()), may ? Optional.of("rename")
                    : Optional.empty(), Component.literal(sub), List.of(sections.get(0)), sections.subList(1,
                            sections.size()), buttons);
            return Optional.of(new View(ROUTE, context, Component.literal(home.name() + ": caravans"), List.of(), "",
                    page, Optional.of(new View.Target(ColonyViews.COLONY, home.id().toString(), TAB))));
        }

        /** A list of goods: per line, its item, fixed or excess, and the number(s). */
        private static Page.Section lines(String title, String p, List<CaravanRules.Amount> list,
                MinecraftServer server, List<Colony> from, Colony counted) {
            List<Stat> out = new ArrayList<>();
            Set<String> chosen = new LinkedHashSet<>();
            list.forEach(a -> chosen.add(a.item()));
            List<Stat.Choice> choices = itemChoices(server, from, chosen, true);
            Map<ResourceLocation, Integer> have = items(server, counted);
            for (int i = 0; i < list.size(); i++) {
                CaravanRules.Amount a = list.get(i);
                int n = have.getOrDefault(ResourceLocation.tryParse(a.item()), 0);
                out.add(picker((i + 1) + ". Item (has " + n + ")", itemName(a.item()), p + "_item:" + i, a.item(),
                        choices));
                out.add(picker("   How much", a.excess() ? "The excess over..." : "A fixed amount", p + "_kind:" + i,
                        a.excess() ? "excess" : "fixed", List.of(
                                Stat.Choice.of("fixed", Component.literal("A fixed amount")),
                                Stat.Choice.of("excess", Component.literal("The excess over...")))));
                if (a.excess()) {
                    out.add(stepper("   Over", p + "_over:" + i, a.over(), 0, MAX_COUNT));
                    out.add(stepper("   At most (0: all)", p + "_count:" + i, a.count(), 0, MAX_COUNT));
                } else {
                    out.add(stepper("   Amount", p + "_count:" + i, a.count(), 1, MAX_COUNT));
                }
            }
            if (list.isEmpty()) {
                out.add(Stat.of(Component.literal("Nothing (a pick-up)"), "", Tone.MUTED));
            }
            return new Page.Section(Component.literal(title), out);
        }

        @Override
        public Component denied() {
            return tr("caravan.denied", "That caravan is gone, or isn't your colony's");
        }

        @Override
        public Result act(ServerPlayer player, String context, String tab, String action, String row, int value) {
            Optional<RouteCtx> ctx = routeCtx(player.server, context);
            if (ctx.isEmpty()) {
                return Result.NONE;
            }
            Colony home = ctx.get().home();
            if (!may(player, home)) {
                return Result.say(noPerm(home));
            }
            MinecraftServer server = player.server;
            CaravanBook.Route r = ctx.get().route();
            CaravanBook book = Caravans.book(home);
            switch (action) {
                case "send" -> {
                    return Result.say(Caravans.send(server, home, r).why());
                }
                case "rename" -> {
                    if (row.isBlank()) {
                        return Result.NONE;
                    }
                    Caravans.setBook(server, home, book.withRoute(r.named(row)));
                    return Result.say(tr("caravan.renamed", "Route renamed to %s", r.named(row).title()));
                }
                case "delete" -> {
                    Caravans.setBook(server, home, book.withoutRoute(r.id()));
                    return Result.go(new View.Target(ColonyViews.COLONY, home.id().toString(), TAB));
                }
                default -> { }
            }
            CaravanBook.Route next = edit(server, home, r, action, row, value);
            if (next != null) {
                Caravans.setBook(server, home, book.withRoute(next));
            }
            return Result.NONE;
        }

        private static CaravanBook.Route edit(MinecraftServer server, Colony home, CaravanBook.Route r, String action,
                String row, int value) {
            String field = action;
            int idx = -1;
            int colon = action.indexOf(':');
            if (colon > 0) {
                field = action.substring(0, colon);
                idx = parse(action.substring(colon + 1));
            }
            List<CaravanRules.Amount> send = new ArrayList<>(r.send());
            List<CaravanRules.Amount> bring = new ArrayList<>(r.bring());
            UUID to = r.to();
            int every = r.every();
            int people = r.people();
            boolean sell = r.sell();
            Optional<CaravanRules.Trigger> when = r.when();
            int v = Math.max(0, Math.min(MAX_COUNT, value));
            switch (field) {
                case "to" -> {
                    try {
                        UUID id = UUID.fromString(row);
                        Optional<Colony> d = Colonies.get(server).get(id);
                        if (d.isEmpty() || Caravans.standing(server, home, d.get()) == Caravans.Standing.NONE) {
                            return null;
                        }
                        to = id;
                        if (!d.get().founder().equals(home.founder())) {
                            bring.clear(); // from a friend only through its offers
                        }
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                }
                case "mode" -> {
                    switch (row) {
                        case "every" -> {
                            every = Math.max(1, every);
                            when = Optional.empty();
                        }
                        case "when" -> {
                            every = 0;
                            String item = !send.isEmpty() ? send.get(0).item() : "minecraft:bread";
                            when = Optional.of(new CaravanRules.Trigger(r.to(), item, false, 64));
                        }
                        default -> {
                            every = 0;
                            when = Optional.empty();
                        }
                    }
                }
                case "every" -> every = Math.max(1, Math.min(365, value));
                case "people" -> people = Math.max(1, Math.min(16, value));
                case "sell" -> sell = row.equals("yes");
                case "w_colony", "w_item", "w_above", "w_threshold" -> {
                    if (when.isEmpty()) {
                        return null;
                    }
                    CaravanRules.Trigger t = when.get();
                    switch (field) {
                        case "w_colony" -> {
                            try {
                                UUID id = UUID.fromString(row);
                                Optional<Colony> wc = Colonies.get(server).get(id);
                                if (wc.isEmpty() || !wc.get().founder().equals(home.founder()) && !id.equals(r.to())) {
                                    return null;
                                }
                                t = new CaravanRules.Trigger(id, t.item(), t.above(), t.threshold());
                            } catch (IllegalArgumentException e) {
                                return null;
                            }
                        }
                        case "w_item" -> {
                            if (ResourceLocation.tryParse(row) == null) {
                                return null;
                            }
                            t = new CaravanRules.Trigger(t.colony(), row, t.above(), t.threshold());
                        }
                        case "w_above" -> t = new CaravanRules.Trigger(t.colony(), t.item(), row.equals("above"),
                                t.threshold());
                        default -> t = new CaravanRules.Trigger(t.colony(), t.item(), t.above(), v);
                    }
                    when = Optional.of(t);
                }
                case "add_t" -> send.add(new CaravanRules.Amount(firstItem(server, home), 64));
                case "add_b" -> {
                    Optional<Colony> d = Colonies.get(server).get(to);
                    if (d.isEmpty() || !d.get().founder().equals(home.founder())) {
                        return null;
                    }
                    bring.add(new CaravanRules.Amount(firstItem(server, d.get()), 64));
                }
                case "t_item", "t_kind", "t_count", "t_over" -> {
                    if (!line(send, field.substring(2), idx, row, v)) {
                        return null;
                    }
                }
                case "b_item", "b_kind", "b_count", "b_over" -> {
                    if (!line(bring, field.substring(2), idx, row, v)) {
                        return null;
                    }
                }
                default -> {
                    return null;
                }
            }
            return new CaravanBook.Route(r.id(), to, send, bring, every, people, sell, r.lastDay(), when, r.name());
        }

        /** Changes line {@code idx}: its item (or removes it), kind, count or over. */
        private static boolean line(List<CaravanRules.Amount> list, String what, int idx, String row, int v) {
            if (idx < 0 || idx >= list.size()) {
                return false;
            }
            CaravanRules.Amount a = list.get(idx);
            switch (what) {
                case "item" -> {
                    if (row.equals(REMOVE)) {
                        list.remove(idx);
                        return true;
                    }
                    if (ResourceLocation.tryParse(row) == null) {
                        return false;
                    }
                    list.set(idx, new CaravanRules.Amount(row, a.count(), a.over()));
                }
                case "kind" -> list.set(idx, row.equals("excess")
                        ? new CaravanRules.Amount(a.item(), 0, Math.max(0, a.excess() ? a.over() : a.count()))
                        : new CaravanRules.Amount(a.item(), Math.max(1, a.excess() ? a.over() : a.count()), -1));
                case "count" -> list.set(idx, new CaravanRules.Amount(a.item(), a.excess() ? v : Math.max(1, v),
                        a.over()));
                default -> list.set(idx, new CaravanRules.Amount(a.item(), a.count(), a.excess() ? v : a.over()));
            }
            return true;
        }

        private static String firstItem(MinecraftServer server, Colony c) {
            return items(server, c).entrySet().stream().max(Map.Entry.comparingByValue())
                    .map(e -> e.getKey().toString()).orElse("minecraft:bread");
        }
    }

    // ================================================================ trade offers and floors

    static final class TradeProvider implements ViewProvider {
        @Override
        public Optional<View> build(ServerPlayer player, String context, String tab) {
            Optional<Colony> found = ColonyViews.colony(player.server, context);
            if (found.isEmpty() || !Permissions.isMember(player.getUUID(), found.get())) {
                return Optional.empty();
            }
            Colony c = found.get();
            MinecraftServer server = player.server;
            CaravanBook b = Caravans.book(c);
            boolean may = may(player, c);
            Component why = noPerm(c);
            List<View.Tab> tabs = List.of(new View.Tab("offers", tr("caravan.tab.offers", "Trade offers")),
                    new View.Tab("floors", tr("caravan.tab.floors", "Floors")));
            String open = tab.equals("floors") ? "floors" : "offers";
            List<Page.Section> sections = new ArrayList<>();
            List<Action> buttons = new ArrayList<>();
            Map<ResourceLocation, Integer> have = items(server, c);
            if (open.equals("offers")) {
                sections.add(new Page.Section(tr("caravan.offers_how", "How trading works"),
                        List.of(Stat.of(Component.literal("Friends' caravans trade here:"), "", Tone.MUTED),
                                Stat.of(Component.literal("for each 'For' they bring,"), "", Tone.MUTED),
                                Stat.of(Component.literal("they get the 'Give' goods."), "", Tone.MUTED))));
                for (int i = 0; i < b.offers().size(); i++) {
                    CaravanRules.Offer o = b.offers().get(i);
                    Set<String> chosen = Set.of(o.give().item(), o.want().item());
                    List<Stat> st = new ArrayList<>();
                    st.add(picker("Give (has " + have.getOrDefault(ResourceLocation.tryParse(o.give().item()), 0)
                            + ")", itemName(o.give().item()), "give:" + i, o.give().item(), itemChoices(server,
                                    List.of(c), chosen, true)));
                    st.add(stepper("   How many", "give_n:" + i, o.give().count(), 1, MAX_COUNT));
                    st.add(picker("For", itemName(o.want().item()), "want:" + i, o.want().item(), allItems(server, c,
                            chosen)));
                    st.add(stepper("   How many", "want_n:" + i, o.want().count(), 1, MAX_COUNT));
                    st.add(stepper("Keep at least", "keep:" + i, o.keep(), 0, MAX_COUNT));
                    st.add(stepper("At most a visit (0: any)", "limit:" + i, o.limit(), 0, MAX_COUNT));
                    sections.add(new Page.Section(Component.literal("Offer " + (i + 1)), st));
                }
                buttons.add(may ? Action.of("add_offer", tr("caravan.add_offer", "New offer"))
                        : Action.disabled("add_offer", tr("caravan.add_offer", "New offer"), why));
            } else {
                List<Stat> st = new ArrayList<>();
                st.add(Stat.of(Component.literal("Caravans never take an item below its floor"), "", Tone.MUTED));
                for (Map.Entry<String, Integer> e : b.keep().entrySet()) {
                    st.add(stepper(itemName(e.getKey()) + " (has " + have.getOrDefault(ResourceLocation.tryParse(
                            e.getKey()), 0) + ", 0 removes)", "floor:" + e.getKey(), e.getValue(), 0, MAX_COUNT));
                }
                List<Stat.Choice> add = new ArrayList<>(itemChoices(server, List.of(c), Set.of(), false));
                add.removeIf(ch -> b.keep().containsKey(ch.id()));
                st.add(picker("Add a floor for", "Choose...", "floor_add", "", add));
                sections.add(new Page.Section(tr("caravan.floors", "Floors"), st));
            }
            return Optional.of(new View(TRADE, context, Component.literal(c.name() + ": trades"), tabs, open,
                    new Page.Stats(sections, buttons), Optional.of(new View.Target(ColonyViews.COLONY,
                            c.id().toString(), TAB))));
        }

        /** What a colony may want: anything in its stores, plus common goods (its stores may lack what it wants). */
        private static List<Stat.Choice> allItems(MinecraftServer server, Colony c, Set<String> chosen) {
            Set<String> ids = new LinkedHashSet<>(chosen);
            for (String s : List.of("minecraft:bread", "minecraft:wheat", "minecraft:iron_ingot", "minecraft:coal",
                    "minecraft:oak_log", "minecraft:cobblestone", "minecraft:white_wool", "minecraft:leather",
                    "minecraft:gold_ingot", "minecraft:cooked_beef")) {
                ids.add(s);
            }
            List<Stat.Choice> out = itemChoices(server, List.of(c), ids, false);
            return out;
        }

        @Override
        public Result act(ServerPlayer player, String context, String tab, String action, String row, int value) {
            Optional<Colony> found = ColonyViews.colony(player.server, context);
            if (found.isEmpty()) {
                return Result.NONE;
            }
            Colony c = found.get();
            if (!may(player, c)) {
                return Result.say(noPerm(c));
            }
            CaravanBook b = Caravans.book(c);
            int v = Math.max(0, Math.min(MAX_COUNT, value));
            String field = action;
            String arg = "";
            int colon = action.indexOf(':');
            if (colon > 0) {
                field = action.substring(0, colon);
                arg = action.substring(colon + 1);
            }
            if (field.equals("floor") || field.equals("floor_add")) {
                Map<String, Integer> keep = new LinkedHashMap<>(b.keep());
                String item = field.equals("floor") ? arg : row;
                if (ResourceLocation.tryParse(item) == null) {
                    return Result.NONE;
                }
                if (field.equals("floor_add")) {
                    keep.put(item, 64);
                } else if (v == 0) {
                    keep.remove(item);
                } else {
                    keep.put(item, v);
                }
                Caravans.setBook(player.server, c, b.withKeep(keep));
                return Result.NONE;
            }
            List<CaravanRules.Offer> offers = new ArrayList<>(b.offers());
            if (field.equals("add_offer")) {
                String give = items(player.server, c).entrySet().stream().max(Map.Entry.comparingByValue())
                        .map(e -> e.getKey().toString()).orElse("minecraft:bread");
                offers.add(new CaravanRules.Offer(new CaravanRules.Amount(give, 1),
                        new CaravanRules.Amount("minecraft:bread", 1), 0, 0));
                Caravans.setBook(player.server, c, b.withOffers(offers));
                return Result.NONE;
            }
            int i = parse(arg);
            if (i < 0 || i >= offers.size()) {
                return Result.NONE;
            }
            CaravanRules.Offer o = offers.get(i);
            switch (field) {
                case "give" -> {
                    if (row.equals(REMOVE)) {
                        offers.remove(i);
                    } else if (ResourceLocation.tryParse(row) != null) {
                        offers.set(i, new CaravanRules.Offer(new CaravanRules.Amount(row, o.give().count()), o.want(),
                                o.keep(), o.limit()));
                    }
                }
                case "want" -> {
                    if (ResourceLocation.tryParse(row) != null) {
                        offers.set(i, new CaravanRules.Offer(o.give(), new CaravanRules.Amount(row, o.want().count()),
                                o.keep(), o.limit()));
                    }
                }
                case "give_n" -> offers.set(i, new CaravanRules.Offer(new CaravanRules.Amount(o.give().item(),
                        Math.max(1, v)), o.want(), o.keep(), o.limit()));
                case "want_n" -> offers.set(i, new CaravanRules.Offer(o.give(), new CaravanRules.Amount(
                        o.want().item(), Math.max(1, v)), o.keep(), o.limit()));
                case "keep" -> offers.set(i, new CaravanRules.Offer(o.give(), o.want(), v, o.limit()));
                case "limit" -> offers.set(i, new CaravanRules.Offer(o.give(), o.want(), o.keep(), v));
                default -> {
                    return Result.NONE;
                }
            }
            Caravans.setBook(player.server, c, b.withOffers(offers));
            return Result.NONE;
        }
    }

    /** For other code: the route editor's place. */
    public static View.Target routeTarget(Colony c, int route) {
        return new View.Target(ROUTE, c.id() + "/" + route, "");
    }
}
