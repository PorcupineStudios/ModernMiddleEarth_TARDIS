package com.porcupinestudios.tardisrb.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.porcupinestudios.tardisrb.TardisRB;
import com.porcupinestudios.tardisrb.block.ConsoleBlockEntity;
import com.porcupinestudios.tardisrb.console.ConsoleLayout;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * The small console control models ({@code assets/tardis_rb/console/controls/<model>.json}, made by
 * {@code tools/flight_controls.py}): parts of quads round the control's base centre (x right, y up, the operator at
 * +z), each part's pivot, and what clicking a part does to which parts ({@link Action}). {@link #pose} works out where
 * every part is at a moment from the console's state, for the renderer and the click test alike. Loaded on first use
 * and again after a resource reload.
 */
public final class ControlModels {
    private static final Map<String, Optional<Model>> LOADED = new HashMap<>();

    public static final ResourceLocation TEXTURE = TardisRB.id("textures/entity/controls/controls.png");
    public static final ResourceLocation GLOW = TardisRB.id("textures/entity/controls/controls_glow.png");

    private ControlModels() {}

    public record Part(Vector3f pivot, List<ConsoleModel.Quad> quads) {}

    /** How one part moves when an action runs (see docs/flight-controls.md for the fields). */
    public record Move(Vector3f axis, float off, float on, float step, float turn, float from, float to,
            Vector3f move, Vector3f moveOn, Vector3f moveStep) {}

    /** One clickable part ({@code click}, null for gauges) and what it moves and lights. */
    public record Action(String click, String type, int ticks, Map<String, Move> moves, Map<String, String> lit,
            int count) {}

    public record Model(String name, Map<String, Part> parts, List<Action> actions) {
        /** The parts some action lights; glowing parts not listed here are always lit. */
        Set<String> lamps() {
            Set<String> out = new HashSet<>();
            actions.forEach(a -> out.addAll(a.lit().keySet()));
            return out;
        }
    }

    /** Where each part is now ({@code transforms}, applied to the part's quads) and which lamps are lit. */
    public record Pose(Map<String, Matrix4f> transforms, Set<String> lit) {
        public Matrix4f of(String part) {
            return transforms.getOrDefault(part, new Matrix4f());
        }
    }

    public static Optional<Model> get(String model) {
        return LOADED.computeIfAbsent(model, ControlModels::load);
    }

    public static void reset() {
        LOADED.clear();
    }

    private static Optional<Model> load(String id) {
        ResourceLocation file = TardisRB.id("console/controls/" + id + ".json");
        try (Reader r = Minecraft.getInstance().getResourceManager().openAsReader(file)) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            Map<String, Part> parts = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("parts").entrySet()) {
                JsonObject p = e.getValue().getAsJsonObject();
                List<ConsoleModel.Quad> quads = new ArrayList<>();
                for (JsonElement q : p.getAsJsonArray("quads")) {
                    quads.add(ConsoleModel.quad(q.getAsJsonObject()));
                }
                parts.put(e.getKey(), new Part(vec(p.getAsJsonArray("pivot")), quads));
            }
            List<Action> actions = new ArrayList<>();
            for (JsonElement el : root.getAsJsonArray("actions")) {
                JsonObject a = el.getAsJsonObject();
                Map<String, Move> moves = new LinkedHashMap<>();
                for (Map.Entry<String, JsonElement> m : a.getAsJsonObject("moves").entrySet()) {
                    moves.put(m.getKey(), move(m.getValue().getAsJsonObject()));
                }
                Map<String, String> lit = new HashMap<>();
                if (a.has("lit")) {
                    a.getAsJsonObject("lit").entrySet().forEach(x -> lit.put(x.getKey(), x.getValue().getAsString()));
                }
                actions.add(new Action(a.get("click").isJsonNull() ? null : a.get("click").getAsString(),
                        a.get("type").getAsString(), a.has("ticks") ? a.get("ticks").getAsInt() : 4, moves, lit,
                        a.has("count") ? a.get("count").getAsInt() : 1));
            }
            return Optional.of(new Model(root.get("name").getAsString(), parts, actions));
        } catch (Exception e) {
            TardisRB.LOG.error("Could not read the console control model {}", file, e);
            return Optional.empty();
        }
    }

    private static Move move(JsonObject m) {
        Vector3f axis = null;
        if (m.has("axis")) {
            JsonElement a = m.get("axis");
            axis = a.isJsonArray() ? vec(a.getAsJsonArray()).normalize() : switch (a.getAsString()) {
                case "x" -> new Vector3f(1, 0, 0);
                case "y" -> new Vector3f(0, 1, 0);
                default -> new Vector3f(0, 0, 1);
            };
        }
        return new Move(axis, f(m, "off"), f(m, "on"), f(m, "step"), f(m, "turn"), f(m, "from"), f(m, "to"),
                v(m, "move"), v(m, "move_on"), v(m, "move_step"));
    }

    private static float f(JsonObject o, String key) {
        return o.has(key) ? o.get(key).getAsFloat() : 0F;
    }

    private static Vector3f v(JsonObject o, String key) {
        return o.has(key) ? vec(o.getAsJsonArray(key)) : null;
    }

    private static Vector3f vec(JsonArray a) {
        return new Vector3f(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat());
    }

    /** Ease in and out, 0..1. */
    private static float ease(float t) {
        return (float) (0.5 - 0.5 * Math.cos(Math.PI * t));
    }

    /**
     * Every part's transform for this control on this console now: toggles swing between off and on (on after an odd
     * number of clicks), steps turn one notch per click, presses go in and back, spins turn once, gauges point at
     * their value. Each move eases over the action's {@code ticks}. Lamps light while on ({@code on}) or while the
     * animation runs ({@code active}).
     */
    public static Pose pose(Model model, ConsoleBlockEntity be, ConsoleBlockEntity.Shown c, float time) {
        Map<String, Matrix4f> out = new HashMap<>();
        Set<String> lit = new HashSet<>();
        Set<String> lamps = model.lamps();
        for (String part : model.parts().keySet()) {
            if (!lamps.contains(part)) {
                lit.add(part);
            }
        }
        int gauge = 0;
        for (Action a : model.actions()) {
            String key = a.click() == null ? "" : ConsoleLayout.key(c.id(), a.click());
            int uses = a.click() == null ? 0 : be.uses(key);
            float p = a.click() == null ? 1F
                    : Math.min(1F, Math.max(0F, (time - be.usedAt(key)) / Math.max(1, a.ticks())));
            float e = ease(p);
            boolean on = uses % 2 == 1;
            for (Map.Entry<String, Move> mv : a.moves().entrySet()) {
                Move m = mv.getValue();
                Vector3f pivot = model.parts().containsKey(mv.getKey()) ? model.parts().get(mv.getKey()).pivot()
                        : new Vector3f();
                Matrix4f t = new Matrix4f();
                switch (a.type()) {
                    case "toggle" -> {
                        if (m.axis() != null) {
                            float from = on ? m.off() : m.on();
                            float to = on ? m.on() : m.off();
                            t = rot(m.axis(), from + (to - from) * e, pivot);
                        }
                        if (m.moveOn() != null) {
                            float k = on ? e : 1 - e;
                            t = new Matrix4f().translation(new Vector3f(m.moveOn()).mul(k)).mul(t);
                        }
                    }
                    case "steps" -> {
                        int count = Math.max(1, a.count());
                        int now = Math.floorMod(uses, count);
                        int before = uses == 0 ? 0 : Math.floorMod(uses - 1, count);
                        float k = before + (now - before) * e;
                        if (m.axis() != null) {
                            t = rot(m.axis(), m.step() * k, pivot);
                        }
                        if (m.moveStep() != null) {
                            t = new Matrix4f().translation(new Vector3f(m.moveStep()).mul(k)).mul(t);
                        }
                    }
                    case "press" -> {
                        if (m.move() != null && p < 1) {
                            t = new Matrix4f().translation(new Vector3f(m.move()).mul((float) Math.sin(Math.PI * p)));
                        }
                    }
                    case "spin" -> {
                        if (m.axis() != null && p < 1) {
                            t = rot(m.axis(), m.turn() * e, pivot);
                        }
                    }
                    case "gauge" -> {
                        String shows = c.shows().isEmpty() ? "" : c.shows().get(Math.min(gauge, c.shows().size() - 1));
                        gauge++;
                        if (m.axis() != null) {
                            t = rot(m.axis(), m.from() + be.value(shows) * (m.to() - m.from()), pivot);
                        }
                    }
                    default -> {
                    }
                }
                out.merge(mv.getKey(), t, (old, add) -> new Matrix4f(add).mul(old));
            }
            for (Map.Entry<String, String> l : a.lit().entrySet()) {
                boolean shine = l.getValue().equals("active") ? p > 0.05 && p < 1 : on;
                if (shine || (a.type().equals("steps") && Math.floorMod(uses, Math.max(1, a.count())) != 0)) {
                    lit.add(l.getKey());
                }
            }
        }
        return new Pose(out, lit);
    }

    private static Matrix4f rot(Vector3f axis, float degrees, Vector3f pivot) {
        return new Matrix4f().translate(pivot).rotate((float) Math.toRadians(degrees), axis)
                .translate(-pivot.x, -pivot.y, -pivot.z);
    }

    /** A part's box in the control's own space, as posed now; null if it has no quads. */
    public static AABB box(Part part, Matrix4f transform) {
        float[] lo = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
        float[] hi = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        Vector4f v = new Vector4f();
        for (ConsoleModel.Quad q : part.quads()) {
            for (float[] p : q.v()) {
                transform.transform(v.set(p[0], p[1], p[2], 1));
                lo[0] = Math.min(lo[0], v.x);
                lo[1] = Math.min(lo[1], v.y);
                lo[2] = Math.min(lo[2], v.z);
                hi[0] = Math.max(hi[0], v.x);
                hi[1] = Math.max(hi[1], v.y);
                hi[2] = Math.max(hi[2], v.z);
            }
        }
        return lo[0] > hi[0] ? null : new AABB(lo[0], lo[1], lo[2], hi[0], hi[1], hi[2]);
    }
}
