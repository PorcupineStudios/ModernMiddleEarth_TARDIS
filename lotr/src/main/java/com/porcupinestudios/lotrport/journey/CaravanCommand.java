package com.porcupinestudios.lotrport.journey;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.porcupinestudios.lotrport.colony.Colonies;
import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.colony.members.Permissions;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Caravans by command, for the nearest colony you belong to (permission {@code caravans}, or its founder):
 * <ul>
 * <li>{@code /lotr_colony caravan send "<to>" <send> [<bring>]}: one caravan now;</li>
 * <li>{@code /lotr_colony caravan route add "<to>" <every days> <send> <bring> [sell]}, {@code route list},
 * {@code route remove <n>}, {@code route send <n>}: standing routes (every 0 = only by hand);</li>
 * <li>{@code /lotr_colony caravan offer add <give> <want> [keep] [limit]}, {@code offer list}, {@code offer remove
 * <n>}: what this colony trades with friends' caravans;</li>
 * <li>{@code /lotr_colony caravan keep <item> <count>}: a floor caravans never take it below;</li>
 * <li>{@code /lotr_colony caravan list}: caravans out now.</li>
 * </ul>
 * Goods are written {@code item*count,item*count} ({@code -} for none), e.g. {@code bread*64,lotr_port:mithril*2};
 * {@code bread>200} is everything over 200, {@code bread>200*64} at most 64 of it.
 */
public final class CaravanCommand {
    private CaravanCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("lotr_colony").then(Commands.literal("caravan")
                .then(Commands.literal("send").then(Commands.argument("to", StringArgumentType.string())
                        .then(Commands.argument("send", StringArgumentType.string())
                                .executes(c -> sendNow(c, "-"))
                                .then(Commands.argument("bring", StringArgumentType.string())
                                        .executes(c -> sendNow(c, StringArgumentType.getString(c, "bring")))))))
                .then(Commands.literal("trades").executes(c -> open(c, "trades", 0)))
                .then(Commands.literal("route")
                        .then(Commands.literal("list").executes(CaravanCommand::listRoutes))
                        .then(Commands.literal("edit").then(Commands.argument("n", IntegerArgumentType.integer(1))
                                .executes(c -> open(c, "route", IntegerArgumentType.getInteger(c, "n")))))
                        // route when <n> "<colony>" <item> below|above <x>: the route goes by itself while that holds
                        .then(Commands.literal("when").then(Commands.argument("n", IntegerArgumentType.integer(1))
                                .then(Commands.argument("watch", StringArgumentType.string())
                                        .then(Commands.argument("item", StringArgumentType.string())
                                                .then(Commands.argument("is", StringArgumentType.word())
                                                        .then(Commands.argument("x", IntegerArgumentType.integer(0))
                                                                .executes(CaravanCommand::when)))))))
                        .then(Commands.literal("remove").then(Commands.argument("n", IntegerArgumentType.integer(1))
                                .executes(c -> edit(c, b -> b.withoutRoute(IntegerArgumentType.getInteger(c, "n")),
                                        "Route removed"))))
                        .then(Commands.literal("send").then(Commands.argument("n", IntegerArgumentType.integer(1))
                                .executes(CaravanCommand::sendRoute)))
                        .then(Commands.literal("add").then(Commands.argument("to", StringArgumentType.string())
                                .then(Commands.argument("every", IntegerArgumentType.integer(0))
                                        .then(Commands.argument("send", StringArgumentType.string())
                                                .then(Commands.argument("bring", StringArgumentType.string())
                                                        .executes(c -> addRoute(c, false))
                                                        .then(Commands.argument("sell", BoolArgumentType.bool())
                                                                .executes(c -> addRoute(c, BoolArgumentType
                                                                        .getBool(c, "sell"))))))))))
                .then(Commands.literal("offer")
                        .then(Commands.literal("list").executes(CaravanCommand::listOffers))
                        .then(Commands.literal("remove").then(Commands.argument("n", IntegerArgumentType.integer(1))
                                .executes(c -> edit(c, b -> {
                                    List<CaravanRules.Offer> o = new ArrayList<>(b.offers());
                                    int n = IntegerArgumentType.getInteger(c, "n");
                                    if (n <= o.size()) {
                                        o.remove(n - 1);
                                    }
                                    return b.withOffers(o);
                                }, "Offer removed"))))
                        .then(Commands.literal("add").then(Commands.argument("give", StringArgumentType.string())
                                .then(Commands.argument("want", StringArgumentType.string())
                                        .executes(c -> addOffer(c, 0, 0))
                                        .then(Commands.argument("keep", IntegerArgumentType.integer(0))
                                                .executes(c -> addOffer(c, IntegerArgumentType.getInteger(c, "keep"),
                                                        0))
                                                .then(Commands.argument("limit", IntegerArgumentType.integer(0))
                                                        .executes(c -> addOffer(c, IntegerArgumentType
                                                                .getInteger(c, "keep"), IntegerArgumentType
                                                                .getInteger(c, "limit")))))))))
                .then(Commands.literal("keep").then(Commands.argument("item", StringArgumentType.string())
                        .then(Commands.argument("count", IntegerArgumentType.integer(0)).executes(c -> edit(c, b -> {
                            Map<String, Integer> k = new LinkedHashMap<>(b.keep());
                            String item = Caravans.id(StringArgumentType.getString(c, "item")).toString();
                            int n = IntegerArgumentType.getInteger(c, "count");
                            if (n == 0) {
                                k.remove(item);
                            } else {
                                k.put(item, n);
                            }
                            return b.withKeep(k);
                        }, "Floor set")))))
                .then(Commands.literal("list").executes(CaravanCommand::listOut))));
    }

    private static Optional<Colony> mine(CommandSourceStack src) {
        ServerPlayer p = src.getPlayer();
        if (p == null) {
            src.sendFailure(Component.literal("Players only"));
            return Optional.empty();
        }
        Optional<Colony> c = Colonies.get(p.server).all().stream()
                .filter(x -> x.founder().equals(p.getUUID()) || Permissions.can(p.getUUID(), x, "caravans"))
                .min(Comparator.comparingDouble(x -> x.hall().distSqr(p.blockPosition())));
        if (c.isEmpty()) {
            src.sendFailure(Component.literal("You run no colony's caravans"));
        }
        return c;
    }

    private static Optional<Colony> named(CommandSourceStack src, String name) {
        Optional<Colony> c = Colonies.get(src.getServer()).all().stream()
                .filter(x -> x.name().equalsIgnoreCase(name)).findFirst();
        if (c.isEmpty()) {
            src.sendFailure(Component.literal("No colony called " + name));
        }
        return c;
    }

    /** {@code item*count,...}; a bare item is 64; {@code -} or empty is none. */
    static List<CaravanRules.Amount> goods(String s) {
        List<CaravanRules.Amount> out = new ArrayList<>();
        if (s == null || s.isBlank() || s.equals("-")) {
            return out;
        }
        for (String part : s.split(",")) {
            // item>over: everything above over (item>over*cap: at most cap of it)
            if (part.contains(">")) {
                String[] io = part.trim().split(">");
                String[] oc = io.length > 1 ? io[1].split("\\*") : new String[] {"0"};
                try {
                    out.add(new CaravanRules.Amount(Caravans.id(io[0].trim()).toString(), oc.length > 1
                            ? Integer.parseInt(oc[1].trim()) : 0, Integer.parseInt(oc[0].trim())));
                } catch (NumberFormatException e) {
                    // skipped
                }
                continue;
            }
            String[] kv = part.trim().split("\\*");
            int n = 64;
            if (kv.length > 1) {
                try {
                    n = Integer.parseInt(kv[1].trim());
                } catch (NumberFormatException e) {
                    continue;
                }
            }
            out.add(new CaravanRules.Amount(Caravans.id(kv[0].trim()).toString(), n));
        }
        return out;
    }

    private static int sendNow(CommandContext<CommandSourceStack> c, String bring) {
        Optional<Colony> home = mine(c.getSource());
        Optional<Colony> to = home.flatMap(h -> named(c.getSource(), StringArgumentType.getString(c, "to")));
        if (to.isEmpty()) {
            return 0;
        }
        Caravans.Sent s = Caravans.send(c.getSource().getServer(), home.get(), new CaravanBook.Route(0, to.get().id(),
                goods(StringArgumentType.getString(c, "send")), goods(bring), 0, 1, false, -1));
        c.getSource().sendSystemMessage(s.why());
        return s.journey().isPresent() ? 1 : 0;
    }

    private static int open(CommandContext<CommandSourceStack> c, String what, int n) {
        Optional<Colony> home = mine(c.getSource());
        ServerPlayer p = c.getSource().getPlayer();
        if (home.isEmpty() || p == null) {
            return 0;
        }
        com.porcupinestudios.lotrport.ui.Views.open(p, what.equals("route")
                ? com.porcupinestudios.lotrport.colony.ui.CaravanViews.routeTarget(home.get(), n)
                : new com.porcupinestudios.lotrport.ui.View.Target(com.porcupinestudios.lotrport.colony.ui.CaravanViews
                        .TRADE, home.get().id().toString(), "offers"), true);
        return 1;
    }

    private static int when(CommandContext<CommandSourceStack> c) {
        Optional<Colony> watch = named(c.getSource(), StringArgumentType.getString(c, "watch"));
        if (watch.isEmpty()) {
            return 0;
        }
        String item = Caravans.id(StringArgumentType.getString(c, "item")).toString();
        boolean above = StringArgumentType.getString(c, "is").equals("above");
        int x = IntegerArgumentType.getInteger(c, "x");
        int n = IntegerArgumentType.getInteger(c, "n");
        return edit(c, b -> b.route(n).map(r -> b.withRoute(new CaravanBook.Route(r.id(), r.to(), r.send(),
                r.bring(), 0, r.people(), r.sell(), r.lastDay(), Optional.of(new CaravanRules.Trigger(watch.get().id(),
                        item, above, x))))).orElse(b), "Route " + n + " now goes by itself when that holds");
    }

    private static int sendRoute(CommandContext<CommandSourceStack> c) {
        Optional<Colony> home = mine(c.getSource());
        if (home.isEmpty()) {
            return 0;
        }
        Optional<CaravanBook.Route> r = Caravans.book(home.get()).route(IntegerArgumentType.getInteger(c, "n"));
        if (r.isEmpty()) {
            c.getSource().sendFailure(Component.literal("No such route"));
            return 0;
        }
        Caravans.Sent s = Caravans.send(c.getSource().getServer(), home.get(), r.get());
        c.getSource().sendSystemMessage(s.why());
        return 1;
    }

    private static int addRoute(CommandContext<CommandSourceStack> c, boolean sell) {
        Optional<Colony> home = mine(c.getSource());
        Optional<Colony> to = home.flatMap(h -> named(c.getSource(), StringArgumentType.getString(c, "to")));
        if (to.isEmpty()) {
            return 0;
        }
        CaravanBook b = Caravans.book(home.get());
        CaravanBook.Route r = new CaravanBook.Route(b.next(), to.get().id(), goods(StringArgumentType.getString(c,
                "send")), goods(StringArgumentType.getString(c, "bring")), IntegerArgumentType.getInteger(c,
                "every"), 1, sell, -1);
        Caravans.setBook(c.getSource().getServer(), home.get(), b.withRoute(r));
        c.getSource().sendSystemMessage(Component.literal("Route " + r.id() + " to " + to.get().name() + " added"));
        return 1;
    }

    private static int addOffer(CommandContext<CommandSourceStack> c, int keep, int limit) {
        List<CaravanRules.Amount> give = goods(StringArgumentType.getString(c, "give"));
        List<CaravanRules.Amount> want = goods(StringArgumentType.getString(c, "want"));
        if (give.size() != 1 || want.size() != 1) {
            c.getSource().sendFailure(Component.literal("An offer is one item for one item, like wool*2 bread*1"));
            return 0;
        }
        return edit(c, b -> {
            List<CaravanRules.Offer> o = new ArrayList<>(b.offers());
            o.add(new CaravanRules.Offer(give.get(0), want.get(0), keep, limit));
            return b.withOffers(o);
        }, "Offer added");
    }

    private static int edit(CommandContext<CommandSourceStack> c, java.util.function.UnaryOperator<CaravanBook> f,
            String done) {
        Optional<Colony> home = mine(c.getSource());
        if (home.isEmpty()) {
            return 0;
        }
        Caravans.setBook(c.getSource().getServer(), home.get(), f.apply(Caravans.book(home.get())));
        c.getSource().sendSystemMessage(Component.literal(done));
        return 1;
    }

    private static String show(List<CaravanRules.Amount> l) {
        return l.isEmpty() ? "nothing" : String.join(", ", l.stream().map(a -> a.count() + " " + a.item()).toList());
    }

    private static int listRoutes(CommandContext<CommandSourceStack> c) {
        Optional<Colony> home = mine(c.getSource());
        if (home.isEmpty()) {
            return 0;
        }
        Colonies cs = Colonies.get(c.getSource().getServer());
        for (CaravanBook.Route r : Caravans.book(home.get()).routes()) {
            c.getSource().sendSystemMessage(Component.literal(r.id() + ". to " + cs.get(r.to()).map(Colony::name)
                    .orElse("(gone)") + (r.every() > 0 ? " every " + r.every() + " days" : " by hand") + ": take "
                    + show(r.send()) + ", bring " + show(r.bring()) + (r.sell() ? " (sell)" : "")));
        }
        return 1;
    }

    private static int listOffers(CommandContext<CommandSourceStack> c) {
        Optional<Colony> home = mine(c.getSource());
        if (home.isEmpty()) {
            return 0;
        }
        int n = 1;
        for (CaravanRules.Offer o : Caravans.book(home.get()).offers()) {
            c.getSource().sendSystemMessage(Component.literal((n++) + ". " + o.give().count() + " " + o.give().item()
                    + " for " + o.want().count() + " " + o.want().item() + (o.keep() > 0 ? ", keeping " + o.keep()
                    : "") + (o.limit() > 0 ? ", at most " + o.limit() + " a visit" : "")));
        }
        return 1;
    }

    private static int listOut(CommandContext<CommandSourceStack> c) {
        Optional<Colony> home = mine(c.getSource());
        if (home.isEmpty()) {
            return 0;
        }
        JourneySettings s = JourneyData.settings();
        for (Journey j : Journeys.get(c.getSource().getServer()).from(home.get().id())) {
            if (!j.kind.equals(Caravans.KIND)) {
                continue;
            }
            double[] p = j.position();
            c.getSource().sendSystemMessage(JourneyKind.of(j.kind).get().label(c.getSource().getServer(), j).copy()
                    .append(": " + j.stage.name().toLowerCase() + " at " + (int) p[0] + ", " + (int) p[1] + ", "
                            + (int) (j.secondsLeft(s) / 60) + " min to go, carrying "
                            + CaravanRules.size(Caravans.names(j.cargo))));
        }
        return 1;
    }
}
