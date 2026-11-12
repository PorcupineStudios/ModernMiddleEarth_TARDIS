package com.porcupinestudios.lotrport.colony.ui;

import com.google.gson.JsonObject;
import com.porcupinestudios.lotrport.colony.Colonies;
import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.journey.CaravanBook;
import com.porcupinestudios.lotrport.journey.CaravanConditions;
import com.porcupinestudios.lotrport.journey.Caravans;
import com.porcupinestudios.lotrport.journey.Outcome;
import com.porcupinestudios.lotrport.journey.Outcomes;
import com.porcupinestudios.lotrport.ui.Page;
import com.porcupinestudios.lotrport.ui.Stat;
import com.porcupinestudios.lotrport.ui.Tone;
import com.porcupinestudios.ringbearer.api.condition.Condition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/**
 * The route editor's Rules tab: a list of rules, each "If (conditions) Then (outcomes) Else (outcomes)", built from
 * blocks. "Add rule..." offers kinds of rule (a plain If, every N days, top up when low, ...); conditions are the
 * {@link RuleEditors} blocks (AND, OR, NOT and the rest) and outcomes the {@link RuleEditors#outcomes} blocks, each
 * added from a menu where it goes. Actions name where they act: {@code <rule>} then, for conditions, the node path
 * ({@code c_type:2/0.1}), for outcomes the list and index ({@code o_type:2/t/0}).
 */
final class RuleViews {
    static final String REMOVE = "-";
    static final String INDENT = "   ";

    private RuleViews() {}

    // ---------------------------------------------------------------- the page

    static List<Page.Section> sections(MinecraftServer server, Colony home, CaravanBook.Route r) {
        List<Page.Section> out = new ArrayList<>();
        List<CaravanBook.Rule> rules = r.program();
        for (int i = 0; i < rules.size(); i++) {
            out.add(new Page.Section(Component.literal("Rule " + (i + 1)), rule(server, home, r, rules.get(i), i,
                    rules.size())));
        }
        List<Stat> add = new ArrayList<>();
        if (rules.isEmpty()) {
            add.add(Stat.of(Component.literal("No rules: it goes only by hand"), "", Tone.MUTED));
        } else {
            boolean now = Caravans.dueNow(server, home, r);
            add.add(Stat.of(Component.literal("Right now"), now ? "would send" : "would wait", now ? Tone.GOOD
                    : Tone.MUTED));
        }
        add.add(CaravanViews.picker("Add rule", "Choose a kind...", "rule_add", "", List.of(
                Stat.Choice.of("if", Component.literal("If ... then ... (else ...)")),
                Stat.Choice.of("every", Component.literal("Every few days: send")),
                Stat.Choice.of("low", Component.literal("When the destination runs low: top it up")),
                Stat.Choice.of("excess", Component.literal("When home piles up: send the excess")),
                Stat.Choice.of("always", Component.literal("Every check: do ...")))));
        add.add(Stat.of(Component.literal("Rules run in order every 10 s;"), "", Tone.MUTED));
        add.add(Stat.of(Component.literal("one caravan of a route at a time."), "", Tone.MUTED));
        out.add(new Page.Section(Component.literal("Rules"), add));
        return out;
    }

    private static List<Stat> rule(MinecraftServer server, Colony home, CaravanBook.Route r, CaravanBook.Rule rule,
            int i, int count) {
        List<Stat> out = new ArrayList<>();
        boolean yes = Caravans.holds(server, home, r, rule);
        List<Stat.Choice> ops = new ArrayList<>();
        ops.add(Stat.Choice.of("delete", Component.literal("Delete this rule")));
        if (i > 0) {
            ops.add(Stat.Choice.of("up", Component.literal("Move up")));
        }
        if (i < count - 1) {
            ops.add(Stat.Choice.of("down", Component.literal("Move down")));
        }
        ops.add(Stat.Choice.of("edge", Component.literal(rule.edge() ? "Fire at every check" : "Fire only when it changes")));
        out.add(CaravanViews.picker(rule.edge() ? "Fires when it changes" : "Fires at every check", "Rule...",
                "rule_op:" + i, "", ops));
        if (rule.when().isPresent()) {
            node(out, server, home, r, RuleEditors.toJson(rule.when().get()), i + "/", 0, "If", true);
        } else {
            out.add(CaravanViews.picker("If (always)", "Add a condition...", "c_set:" + i, "", types(false)));
        }
        outcomes(out, rule.then(), i, "t", yes ? "Then (now)" : "Then");
        if (!rule.otherwise().isEmpty() || rule.when().isPresent()) {
            outcomes(out, rule.otherwise(), i, "e", !yes ? "Else (now)" : "Else");
        }
        return out;
    }

    private static void outcomes(List<Stat> out, List<Outcome> list, int rule, String which, String title) {
        out.add(Stat.of(Component.literal(title), "", Tone.NORMAL));
        for (int k = 0; k < list.size(); k++) {
            JsonObject j = RuleEditors.outcomeJson(list.get(k));
            String type = RuleEditors.str(j, "type", "?");
            RuleEditors.OutcomeSpec spec = RuleEditors.outcomes().get(type);
            String at = rule + "/" + which + "/" + k;
            out.add(CaravanViews.picker(INDENT + "Do", spec == null ? type : spec.label(), "o_type:" + at, type,
                    outcomeTypes(true)));
            if (spec != null) {
                for (RuleEditors.Field f : spec.fields()) {
                    out.add(field(null, null, null, j, "o_field:" + at + ":" + f.name(), f, INDENT + INDENT));
                }
            }
        }
        out.add(CaravanViews.picker(INDENT + "+", "Add outcome...", "o_add:" + rule + "/" + which, "",
                outcomeTypes(false)));
    }

    static List<Stat.Choice> types(boolean removable) {
        List<Stat.Choice> out = new ArrayList<>();
        if (removable) {
            out.add(Stat.Choice.of(REMOVE, Component.literal("(remove)")));
        }
        for (Map.Entry<String, RuleEditors.Spec> e : RuleEditors.specs().entrySet()) {
            out.add(Stat.Choice.of(e.getKey(), Component.literal(e.getValue().label())));
        }
        return out;
    }

    static List<Stat.Choice> outcomeTypes(boolean removable) {
        List<Stat.Choice> out = new ArrayList<>();
        if (removable) {
            out.add(Stat.Choice.of(REMOVE, Component.literal("(remove)")));
        }
        for (Map.Entry<String, RuleEditors.OutcomeSpec> e : RuleEditors.outcomes().entrySet()) {
            out.add(Stat.Choice.of(e.getKey(), Component.literal(e.getValue().label())));
        }
        return out;
    }

    private static boolean holds(MinecraftServer server, Colony home, CaravanBook.Route r, JsonObject j) {
        return RuleEditors.fromJson(j).map(c -> {
            try {
                return c.test(CaravanConditions.context(server, home, r));
            } catch (RuntimeException e) {
                return false;
            }
        }).orElse(false);
    }

    /** A condition block and its children; {@code prefix} is "rule/" and the path follows. */
    private static void node(List<Stat> out, MinecraftServer server, Colony home, CaravanBook.Route r, JsonObject j,
            String at, int depth, String joint, boolean root) {
        String pad = INDENT.repeat(depth);
        String type = RuleEditors.str(j, "type", "?");
        RuleEditors.Spec spec = RuleEditors.specs().get(type);
        boolean ok = holds(server, home, r, j);
        String label = pad + (joint.isEmpty() ? "" : joint + " ") + (ok ? "[yes]" : "[no]");
        out.add(CaravanViews.picker(label, spec == null ? type : spec.label(), "c_type:" + at, type, types(true)));
        if (spec == null) {
            out.add(Stat.of(Component.literal(pad + INDENT + "(set in data; no editor)"), "", Tone.MUTED));
            return;
        }
        for (RuleEditors.Field f : spec.fields()) {
            out.add(field(server, home, r, j, "c_field:" + at + ":" + f.name(), f, pad + INDENT));
        }
        if (!spec.group().isEmpty()) {
            List<JsonObject> kids = RuleEditors.children(j);
            String word = type.endsWith(":any") ? "OR" : type.endsWith(":all") ? "AND" : "";
            String base = at.substring(0, at.indexOf('/') + 1);
            String path = at.substring(at.indexOf('/') + 1);
            for (int i = 0; i < kids.size(); i++) {
                node(out, server, home, r, kids.get(i), base + (path.isEmpty() ? Integer.toString(i) : path + "." + i),
                        depth + 1, i == 0 || word.isEmpty() ? "" : word, false);
            }
            if (spec.group().equals("conditions")) {
                out.add(CaravanViews.picker(pad + INDENT + "+", "Add a condition...", "c_add:" + at, "",
                        types(false)));
            }
        }
    }

    /** A field's control (server, home and route only needed for colony and item fields of conditions). */
    private static Stat field(MinecraftServer server, Colony home, CaravanBook.Route r, JsonObject j, String action,
            RuleEditors.Field f, String pad) {
        String label = pad + f.label();
        return switch (f.kind()) {
            case NUMBER, HOUR -> CaravanViews.stepper(label, action, RuleEditors.num(j, f.name(), f.min()), f.min(),
                    f.max());
            case COMPARE -> {
                List<Stat.Choice> ch = new ArrayList<>();
                for (String c : CaravanConditions.COMPARES) {
                    ch.add(Stat.Choice.of(c, Component.literal(c)));
                }
                String cur = RuleEditors.str(j, f.name(), "<");
                yield CaravanViews.picker(label, cur, action, cur, ch);
            }
            case CHOICE, BOOL -> {
                List<Stat.Choice> ch = new ArrayList<>();
                String cur = RuleEditors.str(j, f.name(), f.options().isEmpty() ? "" : f.options().get(0));
                String shown = cur;
                for (int k = 0; k + 1 < f.options().size(); k += 2) {
                    ch.add(Stat.Choice.of(f.options().get(k), Component.literal(f.options().get(k + 1))));
                    if (f.options().get(k).equals(cur)) {
                        shown = f.options().get(k + 1);
                    }
                }
                yield CaravanViews.picker(label, shown, action, cur, ch);
            }
            case ITEM -> {
                String cur = RuleEditors.str(j, f.name(), "minecraft:bread");
                yield CaravanViews.picker(label, CaravanViews.itemName(cur), action, cur, CURRENT_ITEMS.get() == null
                        ? List.of(Stat.Choice.of(cur, Component.literal(CaravanViews.itemName(cur))))
                        : CaravanViews.itemChoices(CURRENT_ITEMS.get().server(), CURRENT_ITEMS.get().from(),
                                Set.of(cur), false));
            }
            case COLONY -> {
                String cur = RuleEditors.str(j, f.name(), "destination");
                List<Stat.Choice> ch = new ArrayList<>();
                ch.add(Stat.Choice.of("home", Component.literal("Home (" + home.name() + ")")));
                ch.add(Stat.Choice.of("destination", Component.literal("Destination ("
                        + CaravanViews.colonyName(server, r.to()) + ")")));
                for (Colony c : Colonies.get(server).all()) {
                    if (c.founder().equals(home.founder()) && !c.id().equals(home.id())
                            && !c.id().equals(r.to())) {
                        ch.add(Stat.Choice.of(c.id().toString(), Component.literal(c.name())));
                    }
                }
                yield CaravanViews.picker(label, RuleEditors.colonyName(server, home, r, cur), action, cur, ch);
            }
        };
    }

    /** Where item pickers draw their choices from while a page is built. */
    record Items(MinecraftServer server, List<Colony> from) {}

    static final ThreadLocal<Items> CURRENT_ITEMS = new ThreadLocal<>();

    static List<Page.Section> page(MinecraftServer server, Colony home, CaravanBook.Route r) {
        List<Colony> from = new ArrayList<>();
        from.add(home);
        Colonies.get(server).get(r.to()).ifPresent(from::add);
        CURRENT_ITEMS.set(new Items(server, from));
        try {
            return sections(server, home, r);
        } finally {
            CURRENT_ITEMS.remove();
        }
    }

    // ---------------------------------------------------------------- editing

    /** Applies an edit to a route's rules; null when refused. */
    static List<CaravanBook.Rule> edit(MinecraftServer server, Colony home, CaravanBook.Route r, String what,
            String arg, String row, int value) {
        List<CaravanBook.Rule> rules = new ArrayList<>(r.program());
        if (what.equals("rule_add")) {
            Optional<CaravanBook.Rule> made = template(row, r);
            if (made.isEmpty()) {
                return null;
            }
            rules.add(made.get());
            return rules;
        }
        int slash = arg.indexOf('/');
        int i = CaravanViews.parse(slash < 0 ? arg : arg.substring(0, slash));
        if (i < 0 || i >= rules.size()) {
            return null;
        }
        String rest = slash < 0 ? "" : arg.substring(slash + 1);
        CaravanBook.Rule rule = rules.get(i);
        switch (what) {
            case "rule_op" -> {
                switch (row) {
                    case "delete" -> rules.remove(i);
                    case "up" -> {
                        if (i > 0) {
                            rules.set(i, rules.get(i - 1));
                            rules.set(i - 1, rule);
                        }
                    }
                    case "down" -> {
                        if (i < rules.size() - 1) {
                            rules.set(i, rules.get(i + 1));
                            rules.set(i + 1, rule);
                        }
                    }
                    case "edge" -> rules.set(i, new CaravanBook.Rule(rule.when(), rule.then(), rule.otherwise(),
                            !rule.edge()));
                    default -> {
                        return null;
                    }
                }
                return rules;
            }
            case "c_set" -> {
                if (!RuleEditors.specs().containsKey(row)) {
                    return null;
                }
                Optional<Condition> c = RuleEditors.fromJson(RuleEditors.fresh(row, null));
                if (c.isEmpty()) {
                    return null;
                }
                rules.set(i, new CaravanBook.Rule(c, rule.then(), rule.otherwise(), rule.edge()));
                return rules;
            }
            case "c_type", "c_field", "c_add" -> {
                JsonObject root = rule.when().map(RuleEditors::toJson).orElse(null);
                if (root == null) {
                    return null;
                }
                Optional<Optional<Condition>> next = condition(server, home, r, root, what, rest, row, value);
                if (next == null) {
                    return null;
                }
                rules.set(i, new CaravanBook.Rule(next.get(), rule.then(), rule.otherwise(), rule.edge()));
                return rules;
            }
            case "o_add", "o_type", "o_field" -> {
                String[] parts = rest.split("/", 2);
                boolean then = parts[0].equals("t");
                List<Outcome> list = new ArrayList<>(then ? rule.then() : rule.otherwise());
                if (!outcome(list, what, parts.length > 1 ? parts[1] : "", row, value)) {
                    return null;
                }
                rules.set(i, then ? new CaravanBook.Rule(rule.when(), list, rule.otherwise(), rule.edge())
                        : new CaravanBook.Rule(rule.when(), rule.then(), list, rule.edge()));
                return rules;
            }
            default -> {
                return null;
            }
        }
    }

    /** The kinds of rule "Add rule" offers. */
    static Optional<CaravanBook.Rule> template(String kind, CaravanBook.Route r) {
        Outcome send = new Outcomes.Send(false);
        return switch (kind) {
            case "if" -> RuleEditors.fromJson(RuleEditors.specs().get("lotr_port:colony_stock").defaults().deepCopy())
                    .map(c -> new CaravanBook.Rule(Optional.of(c), List.of(send), List.of(), false));
            case "every" -> Optional.of(new CaravanBook.Rule(Optional.of(new CaravanConditions.DaysSinceSent(3)),
                    List.of(send), List.of(), false));
            case "low" -> Optional.of(new CaravanBook.Rule(Optional.of(new CaravanConditions.Stock("destination",
                    "minecraft:bread", "<", 64)), List.of(new Outcomes.Goods(true, "minecraft:bread", "topup", 256, 0),
                            new Outcomes.Send(true)), List.of(), false));
            case "excess" -> Optional.of(new CaravanBook.Rule(Optional.of(new CaravanConditions.Stock("home",
                    "minecraft:bread", ">", 512)), List.of(new Outcomes.Goods(true, "minecraft:bread", "excess", 256,
                            0), new Outcomes.Send(true)), List.of(), false));
            case "always" -> Optional.of(new CaravanBook.Rule(Optional.empty(), List.of(), List.of(), false));
            default -> Optional.empty();
        };
    }

    /** Edits a condition tree; empty inner Optional removes the condition (the rule then always holds). */
    private static Optional<Optional<Condition>> condition(MinecraftServer server, Colony home, CaravanBook.Route r,
            JsonObject root, String what, String arg, String row, int value) {
        switch (what) {
            case "c_type" -> {
                String path = arg;
                if (row.equals(REMOVE)) {
                    if (path.isEmpty()) {
                        return Optional.of(Optional.empty());
                    }
                    if (!RuleEditors.replace(root, path, null)) {
                        return null;
                    }
                } else {
                    if (!RuleEditors.specs().containsKey(row)) {
                        return null;
                    }
                    JsonObject fresh = RuleEditors.fresh(row, RuleEditors.at(root, path));
                    if (path.isEmpty()) {
                        root = fresh;
                    } else if (!RuleEditors.replace(root, path, fresh)) {
                        return null;
                    }
                }
            }
            case "c_add" -> {
                if (!RuleEditors.specs().containsKey(row) || !RuleEditors.add(root, arg,
                        RuleEditors.fresh(row, null))) {
                    return null;
                }
            }
            default -> {
                int colon = arg.lastIndexOf(':');
                if (colon < 0) {
                    return null;
                }
                JsonObject node = RuleEditors.at(root, arg.substring(0, colon));
                if (node == null || !setField(node, RuleEditors.specs().get(RuleEditors.str(node, "type", ""))
                        == null ? List.of() : RuleEditors.specs().get(RuleEditors.str(node, "type", "")).fields(),
                        arg.substring(colon + 1), row, value, server, home, r)) {
                    return null;
                }
            }
        }
        Optional<Condition> c = RuleEditors.fromJson(root);
        return c.isPresent() ? Optional.of(c) : null;
    }

    private static boolean outcome(List<Outcome> list, String what, String arg, String row, int value) {
        if (what.equals("o_add")) {
            RuleEditors.OutcomeSpec spec = RuleEditors.outcomes().get(row);
            Optional<Outcome> o = spec == null ? Optional.empty()
                    : RuleEditors.outcomeFromJson(spec.defaults().deepCopy());
            o.ifPresent(list::add);
            return o.isPresent();
        }
        String idx = arg;
        String field = "";
        int colon = arg.indexOf(':');
        if (colon >= 0) {
            idx = arg.substring(0, colon);
            field = arg.substring(colon + 1);
        }
        int k = CaravanViews.parse(idx);
        if (k < 0 || k >= list.size()) {
            return false;
        }
        if (what.equals("o_type")) {
            if (row.equals(REMOVE)) {
                list.remove(k);
                return true;
            }
            RuleEditors.OutcomeSpec spec = RuleEditors.outcomes().get(row);
            Optional<Outcome> o = spec == null ? Optional.empty()
                    : RuleEditors.outcomeFromJson(spec.defaults().deepCopy());
            o.ifPresent(x -> list.set(k, x));
            return o.isPresent();
        }
        JsonObject j = RuleEditors.outcomeJson(list.get(k));
        RuleEditors.OutcomeSpec spec = RuleEditors.outcomes().get(RuleEditors.str(j, "type", ""));
        if (spec == null || !setField(j, spec.fields(), field, row, value, null, null, null)) {
            return false;
        }
        Optional<Outcome> o = RuleEditors.outcomeFromJson(j);
        o.ifPresent(x -> list.set(k, x));
        return o.isPresent();
    }

    private static boolean setField(JsonObject node, List<RuleEditors.Field> fields, String name, String row,
            int value, MinecraftServer server, Colony home, CaravanBook.Route r) {
        RuleEditors.Field f = fields.stream().filter(x -> x.name().equals(name)).findFirst().orElse(null);
        if (f == null) {
            return false;
        }
        switch (f.kind()) {
            case NUMBER, HOUR -> node.addProperty(name, Math.max(f.min(), Math.min(f.max(), value)));
            case COMPARE -> {
                if (!CaravanConditions.COMPARES.contains(row)) {
                    return false;
                }
                node.addProperty(name, row);
            }
            case CHOICE -> {
                if (!f.options().contains(row)) {
                    return false;
                }
                node.addProperty(name, row);
            }
            case BOOL -> node.addProperty(name, row.equals("true"));
            case COLONY -> {
                if (server == null || !RuleEditors.watchable(server, home, r, row)) {
                    return false;
                }
                node.addProperty(name, row);
            }
            default -> {
                if (net.minecraft.resources.ResourceLocation.tryParse(row) == null) {
                    return false;
                }
                node.addProperty(name, row);
            }
        }
        return true;
    }
}
