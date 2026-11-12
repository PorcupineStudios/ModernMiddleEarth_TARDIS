package com.porcupinestudios.lotrport.colony.ui;

import com.google.gson.JsonObject;
import com.porcupinestudios.lotrport.colony.Colonies;
import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.journey.CaravanBook;
import com.porcupinestudios.lotrport.journey.CaravanConditions;
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
 * The route editor's Rules section: the rule tree as indented blocks. Each block is a picker of its type (any
 * {@link RuleEditors} block, or "remove"), then its fields; groups (AND, OR, NOT) end with "+ add a condition". Each
 * block says whether it holds now. Actions: {@code r_type:<path>}, {@code r_field:<path>:<field>},
 * {@code r_add:<path>}.
 */
final class RuleViews {
    static final String REMOVE = "-";
    static final String INDENT = "   ";

    private RuleViews() {}

    static List<Stat.Choice> types(boolean removable) {
        List<Stat.Choice> out = new ArrayList<>();
        if (removable) {
            out.add(Stat.Choice.of(REMOVE, Component.literal("(remove this block)")));
        }
        for (Map.Entry<String, RuleEditors.Spec> e : RuleEditors.specs().entrySet()) {
            out.add(Stat.Choice.of(e.getKey(), Component.literal(e.getValue().label())));
        }
        return out;
    }

    static List<Stat> lines(MinecraftServer server, Colony home, CaravanBook.Route r) {
        List<Stat> out = new ArrayList<>();
        r.rule().ifPresent(c -> node(out, server, home, r, RuleEditors.toJson(c), "", 0, ""));
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

    private static void node(List<Stat> out, MinecraftServer server, Colony home, CaravanBook.Route r, JsonObject j,
            String path, int depth, String joint) {
        String pad = INDENT.repeat(depth);
        String type = RuleEditors.str(j, "type", "?");
        RuleEditors.Spec spec = RuleEditors.specs().get(type);
        boolean ok = holds(server, home, r, j);
        String label = pad + (joint.isEmpty() ? "" : joint + " ") + (ok ? "[yes]" : "[no]");
        out.add(CaravanViews.picker(label, spec == null ? type : spec.label(), "r_type:" + path, type,
                types(!path.isEmpty())));
        if (spec == null) {
            out.add(Stat.of(Component.literal(pad + INDENT + "(set in data; no editor)"), "", Tone.MUTED));
            return;
        }
        for (RuleEditors.Field f : spec.fields()) {
            out.add(field(server, home, r, j, path, f, pad + INDENT));
        }
        if (!spec.group().isEmpty()) {
            List<JsonObject> kids = RuleEditors.children(j);
            String word = type.endsWith(":any") ? "OR" : type.endsWith(":all") ? "AND" : "";
            for (int i = 0; i < kids.size(); i++) {
                node(out, server, home, r, kids.get(i), path.isEmpty() ? Integer.toString(i) : path + "." + i,
                        depth + 1, i == 0 || word.isEmpty() ? "" : word);
            }
            if (spec.group().equals("conditions")) {
                out.add(CaravanViews.picker(pad + INDENT + "+", "Add a condition...", "r_add:" + path, "",
                        types(false)));
            }
        }
    }

    private static Stat field(MinecraftServer server, Colony home, CaravanBook.Route r, JsonObject j, String path,
            RuleEditors.Field f, String pad) {
        String action = "r_field:" + path + ":" + f.name();
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
            case ITEM -> {
                String cur = RuleEditors.str(j, f.name(), "minecraft:bread");
                List<Colony> from = new ArrayList<>();
                from.add(home);
                Colonies.get(server).get(r.to()).ifPresent(from::add);
                yield CaravanViews.picker(label, CaravanViews.itemName(cur), action, cur,
                        CaravanViews.itemChoices(server, from, Set.of(cur), false));
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

    /**
     * Applies a rule edit; returns the new rules, or null when the edit is refused. {@code arg} is the path, or
     * {@code path:field} for fields.
     */
    static Optional<Condition> edit(MinecraftServer server, Colony home, CaravanBook.Route r, String what, String arg,
            String row, int value) {
        JsonObject root = r.rule().map(RuleEditors::toJson).orElseGet(RuleEditors::starter);
        switch (what) {
            case "r_type" -> {
                String path = arg;
                if (row.equals(REMOVE)) {
                    if (path.isEmpty() || !RuleEditors.replace(root, path, null)) {
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
            case "r_add" -> {
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
                String name = arg.substring(colon + 1);
                RuleEditors.Spec spec = node == null ? null : RuleEditors.specs().get(RuleEditors.str(node, "type",
                        ""));
                RuleEditors.Field f = spec == null ? null : spec.fields().stream().filter(x -> x.name().equals(name))
                        .findFirst().orElse(null);
                if (f == null) {
                    return null;
                }
                switch (f.kind()) {
                    case NUMBER, HOUR -> node.addProperty(name, Math.max(f.min(), Math.min(f.max(), value)));
                    case COMPARE -> {
                        if (!CaravanConditions.COMPARES.contains(row)) {
                            return null;
                        }
                        node.addProperty(name, row);
                    }
                    case COLONY -> {
                        if (!RuleEditors.watchable(server, home, r, row)) {
                            return null;
                        }
                        node.addProperty(name, row);
                    }
                    default -> {
                        if (net.minecraft.resources.ResourceLocation.tryParse(row) == null) {
                            return null;
                        }
                        node.addProperty(name, row);
                    }
                }
            }
        }
        Optional<Condition> c = RuleEditors.fromJson(root);
        return c.isPresent() ? c : null;
    }
}
