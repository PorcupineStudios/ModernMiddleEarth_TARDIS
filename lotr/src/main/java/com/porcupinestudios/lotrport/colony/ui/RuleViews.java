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
 * The route editor's Rules tab, as Scratch-like blocks ({@link Page.Blocks}): each rule an amber "if ... then ...
 * else" C block, conditions green/blue hexagons in its slot, outcomes stack blocks in its arms. "Add rule..." offers kinds of rule (a plain If, every N days, top up when low, ...); conditions are the
 * {@link RuleEditors} blocks (AND, OR, NOT and the rest) and outcomes the {@link RuleEditors#outcomes} blocks, each
 * added from a menu where it goes. Actions name where they act: {@code <rule>} then, for conditions, the node path
 * ({@code c_type:2/0.1}), for outcomes the list and index ({@code o_type:2/t/0}).
 */
final class RuleViews {
    static final String REMOVE = "-";
    static final String INDENT = "   ";

    private RuleViews() {}

    // ---------------------------------------------------------------- blocks

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

    /** The Rules tab as Scratch-like blocks. */
    static Page.Blocks page(MinecraftServer server, Colony home, CaravanBook.Route r) {
        List<Colony> from = new ArrayList<>();
        from.add(home);
        Colonies.get(server).get(r.to()).ifPresent(from::add);
        CURRENT_ITEMS.set(new Items(server, from));
        try {
            List<Page.Block> script = new ArrayList<>();
            List<CaravanBook.Rule> rules = r.program();
            for (int i = 0; i < rules.size(); i++) {
                script.add(ruleBlock(server, home, r, rules.get(i), i, rules.size()));
            }
            List<Component> notes = new ArrayList<>();
            if (rules.isEmpty()) {
                notes.add(Component.literal("No rules: this route goes only by hand. + Add rule to automate it."));
            } else {
                notes.add(Component.literal("Right now: " + (Caravans.dueNow(server, home, r) ? "it would send"
                        : "it would wait") + ". Rules run top to bottom every 10 s; one caravan at a time."));
            }
            notes.add(Component.literal("Click a block's words to change, move or remove it; + slots add blocks."));
            return new Page.Blocks(script, Optional.of(new Stat.Picker("rule_add", "", List.of(
                    Stat.Choice.of("if", Component.literal("If ... then ... else ...")),
                    Stat.Choice.of("every", Component.literal("Every few days: send")),
                    Stat.Choice.of("low", Component.literal("When the destination runs low: top it up")),
                    Stat.Choice.of("excess", Component.literal("When home piles up: send the excess")),
                    Stat.Choice.of("always", Component.literal("Every check: do ..."))))), List.of(), notes);
        } finally {
            CURRENT_ITEMS.remove();
        }
    }

    private static Page.Block ruleBlock(MinecraftServer server, Colony home, CaravanBook.Route r,
            CaravanBook.Rule rule, int i, int count) {
        List<Stat.Choice> ops = new ArrayList<>();
        if (i > 0) {
            ops.add(Stat.Choice.of("up", Component.literal("Move up")));
        }
        if (i < count - 1) {
            ops.add(Stat.Choice.of("down", Component.literal("Move down")));
        }
        ops.add(Stat.Choice.of("edge", Component.literal(rule.edge() ? "Run at every check"
                : "Run only when the answer changes")));
        ops.add(Stat.Choice.of("delete", Component.literal("Delete this rule")));
        List<Page.Part> head = new ArrayList<>();
        head.add(Page.Part.text(rule.edge() ? "when it changes, if" : "if"));
        if (rule.when().isPresent()) {
            head.add(Page.Part.block(condBlock(server, home, r, RuleEditors.toJson(rule.when().get()), i + "/")));
        } else {
            head.add(Page.Part.slot(new Stat.Picker("c_set:" + i, "", types(false))));
        }
        head.add(Page.Part.text("then"));
        List<Page.Arm> arms = new ArrayList<>();
        arms.add(new Page.Arm(Component.empty(), outcomeBlocks(rule.then(), i, "t"), Optional.of(new Stat.Picker(
                "o_add:" + i + "/t", "", outcomeTypes(false)))));
        if (rule.when().isPresent() || !rule.otherwise().isEmpty()) {
            arms.add(new Page.Arm(Component.literal("else"), outcomeBlocks(rule.otherwise(), i, "e"), Optional.of(
                    new Stat.Picker("o_add:" + i + "/e", "", outcomeTypes(false)))));
        }
        return new Page.Block("c", RuleEditors.CONTROL, head, arms, Optional.of(new Stat.Picker("rule_op:" + i, "",
                ops)));
    }

    private static List<Page.Block> outcomeBlocks(List<Outcome> list, int rule, String which) {
        List<Page.Block> out = new ArrayList<>();
        for (int k = 0; k < list.size(); k++) {
            JsonObject j = RuleEditors.outcomeJson(list.get(k));
            String type = RuleEditors.str(j, "type", "?");
            RuleEditors.OutcomeSpec spec = RuleEditors.outcomes().get(type);
            String at = rule + "/" + which + "/" + k;
            List<RuleEditors.Field> fields = spec == null ? List.of() : spec.fields();
            List<Page.Part> parts = sentence(type, spec == null ? type : spec.label(), fields, f -> field(null, null,
                    null, j, "o_field:" + at + ":" + f.name(), f, ""));
            int color = RuleEditors.lookOf(type).map(RuleEditors.Look::color).orElse(RuleEditors.MOTION);
            out.add(new Page.Block("stack", color, parts, List.of(), Optional.of(new Stat.Picker("o_type:" + at, type,
                    outcomeTypes(true)))));
        }
        return out;
    }

    private static Page.Block condBlock(MinecraftServer server, Colony home, CaravanBook.Route r, JsonObject j,
            String at) {
        String type = RuleEditors.str(j, "type", "?");
        RuleEditors.Spec spec = RuleEditors.specs().get(type);
        Stat.Picker menu = new Stat.Picker("c_type:" + at, type, types(true));
        List<Page.Part> parts = new ArrayList<>();
        int color = RuleEditors.lookOf(type).map(RuleEditors.Look::color).orElse(RuleEditors.OPERATORS);
        if (spec != null && !spec.group().isEmpty()) {
            List<JsonObject> kids = RuleEditors.children(j);
            String base = at.substring(0, at.indexOf('/') + 1);
            String path = at.substring(at.indexOf('/') + 1);
            boolean not = spec.group().equals("condition");
            String word = type.endsWith(":any") ? "or" : "and";
            if (not) {
                parts.add(Page.Part.text("not"));
            }
            for (int i = 0; i < kids.size(); i++) {
                if (i > 0 && !not) {
                    parts.add(Page.Part.text(word));
                }
                parts.add(Page.Part.block(condBlock(server, home, r, kids.get(i), base + (path.isEmpty()
                        ? Integer.toString(i) : path + "." + i))));
            }
            if (!not) {
                if (kids.size() > 0) {
                    parts.add(Page.Part.text(word));
                }
                parts.add(Page.Part.slot(new Stat.Picker("c_add:" + at, "", types(false))));
            }
            return new Page.Block("bool", RuleEditors.OPERATORS, parts, List.of(), Optional.of(menu));
        }
        List<RuleEditors.Field> fields = spec == null ? List.of() : spec.fields();
        parts.addAll(sentence(type, spec == null ? type : spec.label(), fields, f -> field(server, home, r, j,
                "c_field:" + at + ":" + f.name(), f, "")));
        // yes or no right now
        parts.add(Page.Part.text(holds(server, home, r, j) ? "\u2714" : "\u2718"));
        return new Page.Block("bool", color, parts, List.of(), Optional.of(menu));
    }

    /** A block's words and inputs from its look ("{colony} has {item} ..."), or its label then its inputs. */
    private static List<Page.Part> sentence(String type, String label, List<RuleEditors.Field> fields,
            java.util.function.Function<RuleEditors.Field, Stat> input) {
        List<Page.Part> out = new ArrayList<>();
        Optional<RuleEditors.Look> look = RuleEditors.lookOf(type);
        if (look.isEmpty()) {
            out.add(Page.Part.text(label));
            for (RuleEditors.Field f : fields) {
                out.add(Page.Part.text(f.label()));
                out.add(toPart(input.apply(f)));
            }
            return out;
        }
        for (String word : look.get().sentence().split(" ")) {
            if (word.startsWith("{") && word.endsWith("}")) {
                String name = word.substring(1, word.length() - 1);
                fields.stream().filter(f -> f.name().equals(name)).findFirst()
                        .ifPresent(f -> out.add(toPart(input.apply(f))));
            } else if (!out.isEmpty() && out.get(out.size() - 1).kind() == 0) {
                Page.Part last = out.remove(out.size() - 1);
                out.add(Page.Part.text(last.text().getString() + " " + word));
            } else {
                out.add(Page.Part.text(word));
            }
        }
        return out;
    }

    private static Page.Part toPart(Stat s) {
        if (s.picker().isPresent()) {
            return Page.Part.pick(s.value(), s.picker().get());
        }
        if (s.stepper().isPresent()) {
            return Page.Part.number(s.stepper().get());
        }
        return Page.Part.text(s.value());
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
