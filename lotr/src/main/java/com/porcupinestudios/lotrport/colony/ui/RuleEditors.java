package com.porcupinestudios.lotrport.colony.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.porcupinestudios.lotrport.colony.Colonies;
import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.journey.CaravanBook;
import com.porcupinestudios.lotrport.journey.CaravanConditions;
import com.porcupinestudios.ringbearer.api.condition.Condition;
import com.porcupinestudios.ringbearer.api.condition.Conditions;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.server.MinecraftServer;

/**
 * Rule blocks for the caravan route editor: how each condition type shows and which of its fields the screen edits.
 * Rules are the shared conditions ({@code ringbearer_api:all}, {@code any}, {@code not} and any mod's types), edited
 * as their JSON, so a type registered here with {@link #register} is a new block in every rule editor; a type with no
 * editor still runs (set in data or by command) and shows as its id. Paths address nodes: {@code ""} is the root,
 * {@code "0.2"} the third child of the root's first child.
 */
public final class RuleEditors {
    /** What a field holds, which decides how it is edited. */
    public enum Kind { COLONY, ITEM, COMPARE, NUMBER, HOUR }

    /** A field of a rule block. */
    public record Field(String name, String label, Kind kind, int min, int max) {}

    /**
     * A rule block.
     *
     * @param label its name in the "add" menus
     * @param defaults its JSON when added (with "type")
     * @param fields what the screen edits
     * @param group "conditions" (a list of children), "condition" (one child) or "" (none)
     * @param summary one line for tables and headers
     */
    public record Spec(String label, JsonObject defaults, List<Field> fields, String group, Summary summary) {}

    /** Writes a block's one-line summary. */
    @FunctionalInterface
    public interface Summary {
        String of(MinecraftServer server, Colony home, CaravanBook.Route route, JsonObject json);
    }

    private static final Map<String, Spec> SPECS = new LinkedHashMap<>();

    private RuleEditors() {}

    public static void register(String type, Spec spec) {
        SPECS.put(type, spec);
    }

    public static Map<String, Spec> specs() {
        return SPECS;
    }

    static JsonObject obj(String type, Object... kv) {
        JsonObject o = new JsonObject();
        o.addProperty("type", type);
        for (int i = 0; i + 1 < kv.length; i += 2) {
            if (kv[i + 1] instanceof Number n) {
                o.addProperty((String) kv[i], n);
            } else if (kv[i + 1] instanceof JsonElement e) {
                o.add((String) kv[i], e);
            } else {
                o.addProperty((String) kv[i], String.valueOf(kv[i + 1]));
            }
        }
        return o;
    }

    static String str(JsonObject o, String k, String def) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : def;
    }

    static int num(JsonObject o, String k, int def) {
        try {
            return o.has(k) ? o.get(k).getAsInt() : def;
        } catch (RuntimeException e) {
            return def;
        }
    }

    /** "home", "destination" or a colony id, by name. */
    static String colonyName(MinecraftServer server, Colony home, CaravanBook.Route r, String ref) {
        return switch (ref) {
            case "home" -> home.name();
            case "destination" -> CaravanViews.colonyName(server, r.to());
            default -> {
                try {
                    yield CaravanViews.colonyName(server, java.util.UUID.fromString(ref));
                } catch (IllegalArgumentException e) {
                    yield ref;
                }
            }
        };
    }

    static {
        JsonArray none = new JsonArray();
        register("ringbearer_api:all", new Spec("AND: all of", obj("ringbearer_api:all", "conditions", none),
                List.of(), "conditions", (s, h, r, j) -> join(s, h, r, j, " and ")));
        register("ringbearer_api:any", new Spec("OR: any of", obj("ringbearer_api:any", "conditions",
                new JsonArray()), List.of(), "conditions", (s, h, r, j) -> join(s, h, r, j, " or ")));
        register("ringbearer_api:not", new Spec("NOT", obj("ringbearer_api:not", "condition",
                obj("ringbearer_api:always")), List.of(), "condition", (s, h, r, j) -> "not ("
                        + describe(s, h, r, j.has("condition") ? j.getAsJsonObject("condition") : new JsonObject())
                        + ")"));
        register("ringbearer_api:always", new Spec("Always", obj("ringbearer_api:always"), List.of(), "",
                (s, h, r, j) -> "always"));
        register("lotr_port:colony_stock", new Spec("Stock", obj("lotr_port:colony_stock",
                "colony", "destination", "item", "minecraft:bread", "compare", "<", "value", 64), List.of(
                        new Field("colony", "Colony", Kind.COLONY, 0, 0),
                        new Field("item", "Item", Kind.ITEM, 0, 0),
                        new Field("compare", "Is", Kind.COMPARE, 0, 0),
                        new Field("value", "Than", Kind.NUMBER, 0, 100000)), "",
                (s, h, r, j) -> colonyName(s, h, r, str(j, "colony", "destination")) + "'s "
                        + CaravanViews.itemName(str(j, "item", "?")) + " " + str(j, "compare", "<") + " "
                        + num(j, "value", 0)));
        register("lotr_port:colony_population", new Spec("Population", obj(
                "lotr_port:colony_population", "colony", "destination", "compare", "<", "value", 10), List.of(
                        new Field("colony", "Colony", Kind.COLONY, 0, 0),
                        new Field("compare", "Is", Kind.COMPARE, 0, 0),
                        new Field("value", "Than", Kind.NUMBER, 0, 1000)), "",
                (s, h, r, j) -> colonyName(s, h, r, str(j, "colony", "destination")) + "'s people "
                        + str(j, "compare", "<") + " " + num(j, "value", 0)));
        register("lotr_port:days_since_sent", new Spec("Days since sent", obj(
                "lotr_port:days_since_sent", "days", 3), List.of(new Field("days", "At least (days)", Kind.NUMBER, 1,
                        365)), "", (s, h, r, j) -> "every " + num(j, "days", 1) + " days"));
        register("lotr_port:time_of_day", new Spec("Time of day", obj("lotr_port:time_of_day", "from", 6, "to",
                18), List.of(new Field("from", "From (hour)", Kind.HOUR, 0, 23),
                        new Field("to", "Until (hour)", Kind.HOUR, 0, 23)), "",
                (s, h, r, j) -> "between " + num(j, "from", 6) + ":00 and " + num(j, "to", 18) + ":00"));
        register("lotr_port:caravans_out", new Spec("Caravans out", obj(
                "lotr_port:caravans_out", "compare", "<", "value", 2), List.of(
                        new Field("compare", "Is", Kind.COMPARE, 0, 0),
                        new Field("value", "Than", Kind.NUMBER, 0, 100)), "",
                (s, h, r, j) -> "caravans out " + str(j, "compare", "<") + " " + num(j, "value", 0)));
    }

    private static String join(MinecraftServer s, Colony h, CaravanBook.Route r, JsonObject j, String word) {
        List<String> parts = new ArrayList<>();
        for (JsonObject c : children(j)) {
            parts.add(describe(s, h, r, c));
        }
        if (parts.isEmpty()) {
            return word.equals(" and ") ? "always" : "never";
        }
        return parts.size() == 1 ? parts.get(0) : "(" + String.join(word, parts) + ")";
    }

    /** One line for a rule. */
    public static String describe(MinecraftServer s, Colony h, CaravanBook.Route r, JsonObject j) {
        Spec spec = SPECS.get(str(j, "type", ""));
        if (spec == null) {
            return str(j, "type", "?");
        }
        try {
            return spec.summary().of(s, h, r, j);
        } catch (RuntimeException e) {
            return spec.label();
        }
    }

    public static String describe(MinecraftServer s, Colony h, CaravanBook.Route r) {
        return r.rule().map(c -> describe(s, h, r, toJson(c))).orElse("By hand");
    }

    public static JsonObject toJson(Condition c) {
        return Conditions.CODEC.encodeStart(JsonOps.INSTANCE, c).result().filter(JsonElement::isJsonObject)
                .map(JsonElement::getAsJsonObject).orElseGet(() -> obj("ringbearer_api:always"));
    }

    public static Optional<Condition> fromJson(JsonObject j) {
        return Conditions.CODEC.parse(JsonOps.INSTANCE, j).result();
    }

    /** A node's children (an empty list for a leaf). */
    public static List<JsonObject> children(JsonObject j) {
        List<JsonObject> out = new ArrayList<>();
        if (j.has("conditions") && j.get("conditions").isJsonArray()) {
            for (JsonElement e : j.getAsJsonArray("conditions")) {
                if (e.isJsonObject()) {
                    out.add(e.getAsJsonObject());
                }
            }
        } else if (j.has("condition") && j.get("condition").isJsonObject()) {
            out.add(j.getAsJsonObject("condition"));
        }
        return out;
    }

    /** The node at a path, or null. */
    public static JsonObject at(JsonObject root, String path) {
        JsonObject cur = root;
        if (path.isEmpty()) {
            return cur;
        }
        for (String p : path.split("\\.")) {
            List<JsonObject> ch = children(cur);
            int i;
            try {
                i = Integer.parseInt(p);
            } catch (NumberFormatException e) {
                return null;
            }
            if (i < 0 || i >= ch.size()) {
                return null;
            }
            cur = ch.get(i);
        }
        return cur;
    }

    /** Replaces (or with null removes) the node at a non-root path. */
    public static boolean replace(JsonObject root, String path, JsonObject with) {
        int dot = path.lastIndexOf('.');
        JsonObject parent = at(root, dot < 0 ? "" : path.substring(0, dot));
        if (parent == null) {
            return false;
        }
        int i;
        try {
            i = Integer.parseInt(path.substring(dot + 1));
        } catch (NumberFormatException e) {
            return false;
        }
        if (parent.has("conditions") && parent.get("conditions").isJsonArray()) {
            JsonArray a = parent.getAsJsonArray("conditions");
            if (i < 0 || i >= a.size()) {
                return false;
            }
            if (with == null) {
                a.remove(i);
            } else {
                a.set(i, with);
            }
            return true;
        }
        if (parent.has("condition") && i == 0) {
            parent.add("condition", with == null ? obj("ringbearer_api:always") : with);
            return true;
        }
        return false;
    }

    /** Adds a child to a group node. */
    public static boolean add(JsonObject root, String path, JsonObject child) {
        JsonObject g = at(root, path);
        if (g == null) {
            return false;
        }
        if (g.has("conditions") && g.get("conditions").isJsonArray()) {
            g.getAsJsonArray("conditions").add(child);
            return true;
        }
        if (g.has("condition")) {
            g.add("condition", child);
            return true;
        }
        return false;
    }

    /** A fresh block of a type (its defaults), keeping the children when one group becomes another. */
    public static JsonObject fresh(String type, JsonObject old) {
        Spec spec = SPECS.get(type);
        JsonObject j = spec == null ? obj(type) : spec.defaults().deepCopy();
        if (spec != null && old != null) {
            List<JsonObject> kids = children(old);
            if (spec.group().equals("conditions")) {
                JsonArray a = new JsonArray();
                kids.forEach(a::add);
                j.add("conditions", a);
            } else if (spec.group().equals("condition") && !kids.isEmpty()) {
                j.add("condition", kids.get(0));
            }
        }
        return j;
    }

    /** The starting rules for a route that goes by itself: every 3 days. */
    public static JsonObject starter() {
        JsonArray a = new JsonArray();
        a.add(SPECS.get("lotr_port:days_since_sent").defaults().deepCopy());
        return obj("ringbearer_api:all", "conditions", a);
    }

    /** Whether a colony reference names a colony the route may watch. */
    static boolean watchable(MinecraftServer server, Colony home, CaravanBook.Route r, String ref) {
        if (ref.equals("home") || ref.equals("destination")) {
            return true;
        }
        return CaravanConditions.colony(new CaravanConditions.Ctx(server, home, r), ref).isPresent()
                && Colonies.get(server).get(java.util.UUID.fromString(ref)).isPresent();
    }
}
