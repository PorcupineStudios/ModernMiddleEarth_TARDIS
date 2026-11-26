package com.porcupinestudios.lotrport.world.delve;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A delve as data (the {@code spec} of a {@code lotr_port:delve} configured feature, e.g. Moria): where it lies (an
 * axis and a half width, cut to where the mountain covers it), its levels, the style and palette each level builds
 * with, its room kinds, its set pieces (gates, great halls, the bridge...), how much of the maze is kept, and how
 * ruined it is. Parsed without the game, so the planner and its preview tool read the same file.
 */
public final class DelveSpec {
    /** Directions: north (-z), east (+x), south (+z), west (-x). */
    public static final int N = 0;
    public static final int E = 1;
    public static final int S = 2;
    public static final int W = 3;
    public static final int[] DX = {0, 1, 0, -1};
    public static final int[] DZ = {-1, 0, 1, 0};
    static final String[] SIDES = {"north", "east", "south", "west"};

    public record Style(String name, Palette palette, boolean hewn, int width, int height, int rib, double roughness,
                        double lights) {}

    public record Level(String name, int index, int floor, Style style, int floorJitter, double roomChance,
                        double deadEndRooms, double prune, double loops, double stairs, Map<String, Double> rooms) {}

    public record Dressing(String role, int min, int max, String at) {}

    public record Variant(String name, double weight, List<Dressing> dressing) {}

    public record RoomKind(String name, int[] size, int[] height, JsonObject params, List<Variant> variants) {}

    /** A placed set piece: {@code cells} wide (x) and deep (z), {@code route} its place on the hidden route (-1 off it). */
    public record SetPiece(String id, String kind, int x, int z, String level, int cellsX, int cellsZ, int height,
                           int below, int search, int route, int doors, String place, JsonObject params) {}

    public record Ruin(double rubble, double holes, double rough, double webs, double brokenPillars, double collapse,
                       double bones) {}

    public record Stairwell(int half, int newel, int rail, int gap, int maxRise) {}

    public final String name;
    public final long salt;
    public final int cell;
    public final int cover;
    public final int margin;
    public final int groundStep;
    public final int[] axisA;
    public final int[] axisB;
    public final double halfWidth;
    public final double decay;
    public final double routeWiggle;
    public final double stairCost;
    public final int prunePasses;
    public final Map<String, Style> styles;
    public final List<Level> levels;
    public final Map<String, RoomKind> rooms;
    public final Stairwell stairwell;
    public final List<SetPiece> setPieces;
    public final Ruin ruin;
    public final Map<String, Palette> palettes;
    /** Folder under {@code structure/} whose templates are this delve's .nbt rooms (kind {@code template}). */
    public final String templates;
    public final JsonObject json;

    private DelveSpec(JsonObject j) {
        this.json = j;
        name = J.s(j, "name");
        salt = j.get("salt").getAsLong();
        cell = J.i(j, "cell");
        cover = J.i(j, "cover");
        margin = J.i(j, "margin");
        groundStep = J.i(j, "ground_step");
        if (cell % groundStep != 0) {
            throw new IllegalArgumentException("delve " + name + ": ground_step must divide cell");
        }
        JsonArray axis = J.a(j, "axis");
        axisA = J.ints(axis.get(0).getAsJsonArray());
        axisB = J.ints(axis.get(1).getAsJsonArray());
        halfWidth = J.d(j, "half_width");
        decay = J.d(j, "decay");
        JsonObject maze = J.o(j, "maze");
        routeWiggle = J.d(maze, "route_wiggle");
        stairCost = J.d(maze, "stair_cost");
        prunePasses = J.i(maze, "prune_passes");
        templates = j.has("templates") ? j.get("templates").getAsString() : null;

        Map<String, Palette> pals = new LinkedHashMap<>();
        JsonObject palJson = J.o(j, "palettes");
        // parents first: a palette may extend one defined anywhere in the object
        for (int pass = 0; pals.size() < palJson.size() && pass < palJson.size() + 1; pass++) {
            for (Map.Entry<String, JsonElement> e : palJson.entrySet()) {
                if (pals.containsKey(e.getKey())) {
                    continue;
                }
                JsonObject p = e.getValue().getAsJsonObject();
                String parent = p.has("extends") ? p.get("extends").getAsString() : null;
                if (parent != null && !pals.containsKey(parent)) {
                    continue;
                }
                pals.put(e.getKey(), new Palette(e.getKey(), p, parent == null ? null : pals.get(parent), decay));
            }
        }
        if (pals.size() < palJson.size()) {
            throw new IllegalArgumentException("delve " + name + ": a palette extends one that doesn't exist");
        }
        palettes = Collections.unmodifiableMap(pals);

        Map<String, Style> st = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : J.o(j, "styles").entrySet()) {
            JsonObject o = e.getValue().getAsJsonObject();
            Palette p = pals.get(J.s(o, "palette"));
            if (p == null) {
                throw new IllegalArgumentException("delve " + name + ": style " + e.getKey() + " names no palette");
            }
            int width = J.i(o, "width");
            if (width % 2 == 0) {
                throw new IllegalArgumentException("delve " + name + ": corridor width must be odd");
            }
            st.put(e.getKey(), new Style(e.getKey(), p, J.b(o, "hewn"), width, J.i(o, "height"), J.i(o, "rib"),
                    J.d(o, "roughness"), J.d(o, "lights")));
        }
        styles = Collections.unmodifiableMap(st);

        Map<String, RoomKind> rk = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : J.o(j, "rooms").entrySet()) {
            JsonObject o = e.getValue().getAsJsonObject();
            List<Variant> variants = new ArrayList<>();
            if (o.has("variants")) {
                for (Map.Entry<String, JsonElement> v : o.getAsJsonObject("variants").entrySet()) {
                    JsonObject vo = v.getValue().getAsJsonObject();
                    List<Dressing> dressing = new ArrayList<>();
                    if (vo.has("dressing")) {
                        for (JsonElement d : vo.getAsJsonArray("dressing")) {
                            JsonObject dob = d.getAsJsonObject();
                            int[] count = J.range(dob, "count");
                            dressing.add(new Dressing(J.s(dob, "role"), count[0], count[1], J.s(dob, "at")));
                        }
                    }
                    variants.add(new Variant(v.getKey(), J.d(vo, "weight"), List.copyOf(dressing)));
                }
            }
            rk.put(e.getKey(), new RoomKind(e.getKey(), o.has("size") ? J.range(o, "size") : new int[] {0, 0},
                    o.has("height") ? J.range(o, "height") : new int[] {0, 0}, o, List.copyOf(variants)));
        }
        rooms = Collections.unmodifiableMap(rk);

        List<Level> lv = new ArrayList<>();
        JsonArray levelJson = J.a(j, "levels");
        int lastFloor = Integer.MIN_VALUE;
        for (int i = 0; i < levelJson.size(); i++) {
            JsonObject o = levelJson.get(i).getAsJsonObject();
            Style style = st.get(J.s(o, "style"));
            if (style == null) {
                throw new IllegalArgumentException("delve " + name + ": level " + o.get("name") + " names no style");
            }
            int floor = J.i(o, "floor");
            if (floor <= lastFloor) {
                throw new IllegalArgumentException("delve " + name + ": levels must go up (floor " + floor + ")");
            }
            lastFloor = floor;
            Map<String, Double> weights = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> e : J.o(o, "rooms").entrySet()) {
                if (!rk.containsKey(e.getKey())) {
                    throw new IllegalArgumentException("delve " + name + ": no room kind " + e.getKey());
                }
                weights.put(e.getKey(), e.getValue().getAsDouble());
            }
            lv.add(new Level(J.s(o, "name"), i, floor, style, J.i(o, "floor_jitter"), J.d(o, "room_chance"),
                    J.d(o, "dead_end_rooms"), J.d(o, "prune"), J.d(o, "loops"), J.d(o, "stairs"),
                    Collections.unmodifiableMap(weights)));
        }
        levels = List.copyOf(lv);

        JsonObject sw = J.o(j, "stairwell");
        stairwell = new Stairwell(J.i(sw, "half"), J.i(sw, "newel"), J.i(sw, "rail"), J.i(sw, "gap"),
                J.i(sw, "max_rise"));

        List<SetPiece> sp = new ArrayList<>();
        for (JsonElement e : J.a(j, "set_pieces")) {
            JsonObject o = e.getAsJsonObject();
            int doors = 15;
            if (o.has("doors")) {
                doors = 0;
                for (JsonElement d : o.getAsJsonArray("doors")) {
                    int side = List.of(SIDES).indexOf(d.getAsString());
                    if (side < 0) {
                        throw new IllegalArgumentException("delve " + name + ": bad door side " + d);
                    }
                    doors |= 1 << side;
                }
            }
            int[] cells = o.has("cells") ? J.ints(J.a(o, "cells")) : new int[] {1, 1};
            String levelName = J.s(o, "level");
            if (level(lv, levelName) == null) {
                throw new IllegalArgumentException("delve " + name + ": set piece " + o.get("id") + " names no level");
            }
            sp.add(new SetPiece(J.s(o, "id"), J.s(o, "kind"), J.i(o, "x"), J.i(o, "z"), levelName, cells[0],
                    cells[1], J.i(o, "height"), o.has("below") ? o.get("below").getAsInt() : 0, J.i(o, "search"),
                    o.has("route") ? o.get("route").getAsInt() : -1, doors,
                    o.has("place") ? o.get("place").getAsString() : "cover",
                    o.has("params") ? o.getAsJsonObject("params") : new JsonObject()));
        }
        setPieces = List.copyOf(sp);

        JsonObject r = J.o(j, "ruin");
        ruin = new Ruin(J.d(r, "rubble") * decay, J.d(r, "holes") * decay, J.d(r, "rough") * decay,
                J.d(r, "webs") * decay, J.d(r, "broken_pillars") * decay, J.d(r, "collapse") * decay,
                J.d(r, "bones") * decay);
    }

    public static DelveSpec parse(JsonObject json) {
        return new DelveSpec(json);
    }

    private static Level level(List<Level> levels, String name) {
        for (Level l : levels) {
            if (l.name().equals(name)) {
                return l;
            }
        }
        return null;
    }

    public Level level(String name) {
        return level(levels, name);
    }

    public RoomKind room(String kind) {
        RoomKind k = rooms.get(kind);
        if (k == null) {
            throw new IllegalArgumentException("delve " + name + ": no room kind " + kind);
        }
        return k;
    }

    /** Tallest room a level may hold (for its cover): its corridors and every room kind it picks. */
    int tallest(Level level) {
        int h = level.style().height();
        for (String kind : level.rooms().keySet()) {
            h = Math.max(h, rooms.get(kind).height()[1]);
        }
        return h;
    }
}
