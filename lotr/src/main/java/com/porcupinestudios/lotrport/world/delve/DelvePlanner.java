package com.porcupinestudios.lotrport.world.delve;

import com.google.gson.JsonObject;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.SplittableRandom;
import java.util.TreeMap;

/**
 * Plans a delve for one world seed, without the game (only the ground height):
 * <ol>
 * <li>A grid of {@code cell}-block cells over the delve's area (a band {@code half_width} either side of its axis).
 * A cell is open on a level where the ground over it, sampled every {@code ground_step} blocks, stays at least
 * {@code cover} blocks above that level's tallest room, so the delve never breaks the mountainside except where a
 * set piece means to (gates, Durin's Tower).
 * <li>Set pieces take their cells first: gates at the mountain front, great halls, the bridge, the Endless Stair at
 * the highest ground near its spot. A piece that doesn't fit where the data puts it is moved up to {@code search}
 * cells. Cells a tall or deep piece passes through on other levels are kept clear.
 * <li>Every other open cell is a maze node. Ways: corridors between neighbouring cells of a level, stairwells between
 * the same cell on two levels (a share of the candidates, {@code stairs}).
 * <li>The hidden route: shortest ways (with random weights, {@code route_wiggle}) from each route set piece to the
 * next, gate to gate. Then a random spanning tree over everything else (Kruskal, route first), cells cut off from
 * the route dropped, dead ends pruned ({@code prune} per pass, {@code prune_passes}) and some joined back as loops
 * ({@code loops}).
 * <li>Each node becomes a junction, a room of a kind the level picks, a stair landing, a template room or its set
 * piece; then the pieces that build them.
 * </ol>
 */
public final class DelvePlanner {
    /** A structure template room: its size and doorways ({x, y, z, side} at each jigsaw block, facing out). */
    public record TemplateInfo(String id, int sx, int sy, int sz, List<int[]> doors) {}

    private static final int NONE = -1;
    private static final int RESERVED = -2;
    private static final double AVOID = 1e4;

    private final DelveSpec spec;
    private final long seed;
    private final Ground ground;
    private final List<TemplateInfo> templates;
    private final SplittableRandom rnd;
    private final int cell;
    private int ox;
    private int oz;
    private int cols;
    private int rows;
    private float[] cellMin;
    private float[] centre;
    private boolean[] capsule;
    private int[][] owner;
    private final List<Node> nodes = new ArrayList<>();
    private final List<Edge> edges = new ArrayList<>();
    private final List<Piece> setPieces = new ArrayList<>();
    private final Map<DelveSpec.SetPiece, Node> setNode = new HashMap<>();
    private final Map<DelveSpec.SetPiece, List<Node>> landings = new HashMap<>();
    private final Map<DelveSpec.SetPiece, int[]> gates = new HashMap<>();
    private final Map<DelveSpec.SetPiece, Integer> towers = new HashMap<>();
    private final StringBuilder log = new StringBuilder();

    private DelvePlanner(DelveSpec spec, long worldSeed, Ground ground, List<TemplateInfo> templates) {
        this.spec = spec;
        this.seed = Rng.mix(worldSeed ^ Rng.mix(spec.salt));
        this.ground = ground;
        this.templates = templates == null ? List.of() : templates;
        this.rnd = new SplittableRandom(seed);
        this.cell = spec.cell;
    }

    public static DelvePlan plan(DelveSpec spec, long worldSeed, Ground ground, List<TemplateInfo> templates) {
        return new DelvePlanner(spec, worldSeed, ground, templates).run();
    }

    private DelvePlan run() {
        grid();
        for (DelveSpec.SetPiece sp : spec.setPieces) {
            place(sp);
        }
        mazeNodes();
        candidates();
        List<Node> route = new ArrayList<>();
        boolean complete = route(route);
        spanningTree();
        dropIslands(route.isEmpty() ? null : route.get(0));
        prune();
        loops();
        shape();
        List<Piece> pieces = pieces();
        int live = 0;
        for (Node n : nodes) {
            if (!n.removed) {
                live++;
            }
        }
        log.insert(0, String.format("delve %s: grid %dx%d at (%d, %d), %d nodes (%d kept), %d pieces, route %s (%d nodes)%n",
                spec.name, cols, rows, ox, oz, nodes.size(), live, pieces.size(), complete ? "complete" : "BROKEN",
                route.size()));
        List<Node> keptNodes = new ArrayList<>();
        for (Node n : nodes) {
            if (!n.removed) {
                keptNodes.add(n);
            }
        }
        List<Edge> keptEdges = new ArrayList<>();
        for (Edge e : edges) {
            if (!e.removed) {
                keptEdges.add(e);
            }
        }
        return new DelvePlan(spec, seed, ox, oz, List.copyOf(keptNodes), List.copyOf(keptEdges), List.copyOf(pieces),
                List.copyOf(route), complete, log.toString(), doorways());
    }

    private Map<String, int[]> doorways() {
        Map<String, int[]> out = new HashMap<>();
        for (Map.Entry<DelveSpec.SetPiece, int[]> e : gates.entrySet()) {
            out.put(e.getKey().id(), e.getValue().clone());
        }
        return out;
    }

    // ---- grid and cover ----

    private int idx(int c, int r) {
        return c * rows + r;
    }

    private boolean inGrid(int c, int r) {
        return c >= 0 && r >= 0 && c < cols && r < rows;
    }

    private int centreX(int c) {
        return ox + c * cell + cell / 2;
    }

    private int centreZ(int r) {
        return oz + r * cell + cell / 2;
    }

    private void grid() {
        double hw = spec.halfWidth;
        int[] a = spec.axisA;
        int[] b = spec.axisB;
        int minX = (int) Math.floor(Math.min(a[0], b[0]) - hw) - 2 * cell;
        int maxX = (int) Math.ceil(Math.max(a[0], b[0]) + hw) + 2 * cell;
        int minZ = (int) Math.floor(Math.min(a[1], b[1]) - hw) - 2 * cell;
        int maxZ = (int) Math.ceil(Math.max(a[1], b[1]) + hw) + 2 * cell;
        ox = Math.floorDiv(minX, cell) * cell;
        oz = Math.floorDiv(minZ, cell) * cell;
        cols = (maxX - ox) / cell + 1;
        rows = (maxZ - oz) / cell + 1;
        int g = spec.groundStep;
        int per = cell / g;
        int lx = cols * per + 1;
        int lz = rows * per + 1;
        float[] lattice = new float[lx * lz];
        for (int i = 0; i < lx; i++) {
            for (int j = 0; j < lz; j++) {
                lattice[i * lz + j] = (float) ground.height(ox + i * g, oz + j * g);
            }
        }
        cellMin = new float[cols * rows];
        centre = new float[cols * rows];
        capsule = new boolean[cols * rows];
        for (int c = 0; c < cols; c++) {
            for (int r = 0; r < rows; r++) {
                float min = Float.MAX_VALUE;
                for (int i = c * per; i <= (c + 1) * per; i++) {
                    for (int j = r * per; j <= (r + 1) * per; j++) {
                        min = Math.min(min, lattice[i * lz + j]);
                    }
                }
                cellMin[idx(c, r)] = min;
                centre[idx(c, r)] = (float) ground.height(centreX(c), centreZ(r));
                capsule[idx(c, r)] = axisDistance(centreX(c), centreZ(r)) <= hw;
            }
        }
        owner = new int[spec.levels.size()][cols * rows];
        for (int[] o : owner) {
            Arrays.fill(o, NONE);
        }
    }

    private double axisDistance(double x, double z) {
        double ax = spec.axisA[0];
        double az = spec.axisA[1];
        double dx = spec.axisB[0] - ax;
        double dz = spec.axisB[1] - az;
        double len = dx * dx + dz * dz;
        double t = len == 0 ? 0 : Math.max(0, Math.min(1, ((x - ax) * dx + (z - az) * dz) / len));
        return Math.hypot(ax + t * dx - x, az + t * dz - z);
    }

    /** Whether a level's tallest room fits under the ground of this cell with the delve's cover. */
    private boolean covered(DelveSpec.Level level, int c, int r, int height) {
        return cellMin[idx(c, r)] >= level.floor() + height + 1 + spec.cover;
    }

    private boolean open(DelveSpec.Level level, int c, int r) {
        return inGrid(c, r) && capsule[idx(c, r)] && covered(level, c, r, spec.tallest(level));
    }

    /** Levels whose rooms (floor - 1 .. floor + tallest + 1) meet the height range. */
    private List<DelveSpec.Level> levelsMeeting(int lo, int hi) {
        List<DelveSpec.Level> out = new ArrayList<>();
        for (DelveSpec.Level l : spec.levels) {
            if (l.floor() - 1 <= hi && l.floor() + spec.tallest(l) + 1 >= lo) {
                out.add(l);
            }
        }
        return out;
    }

    private boolean free(List<DelveSpec.Level> levels, int c0, int r0, int cw, int cd) {
        for (int c = c0; c < c0 + cw; c++) {
            for (int r = r0; r < r0 + cd; r++) {
                if (!inGrid(c, r)) {
                    return false;
                }
                for (DelveSpec.Level l : levels) {
                    if (owner[l.index()][idx(c, r)] != NONE) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private void claim(List<DelveSpec.Level> levels, int c0, int r0, int cw, int cd, Node node) {
        for (int c = c0; c < c0 + cw; c++) {
            for (int r = r0; r < r0 + cd; r++) {
                for (DelveSpec.Level l : levels) {
                    owner[l.index()][idx(c, r)] = node != null && l == node.level ? node.id : RESERVED;
                }
            }
        }
    }

    // ---- set pieces ----

    private Node newNode(DelveSpec.Level level, int c, int r, int cw, int cd, DelveSpec.SetPiece sp) {
        Node n = new Node(nodes.size(), level, c, r, cw, cd, sp);
        n.hash = Rng.hash(seed, level.index(), c, r);
        nodes.add(n);
        return n;
    }

    /** The standard interior of a block of cells: centred, {@code margin} + 2 in from the cell edges. */
    private void cellBox(Node n, int c0, int r0, int cw, int cd) {
        int x0 = ox + c0 * cell + spec.margin + 2;
        int x1 = ox + (c0 + cw) * cell - spec.margin - 2;
        int z0 = oz + r0 * cell + spec.margin + 2;
        int z1 = oz + (r0 + cd) * cell - spec.margin - 2;
        n.cx = (x0 + x1) / 2;
        n.cz = (z0 + z1) / 2;
        n.hx = (x1 - x0) / 2;
        n.hz = (z1 - z0) / 2;
    }

    private void place(DelveSpec.SetPiece sp) {
        DelveSpec.Level level = spec.level(sp.level());
        switch (sp.place()) {
            case "front" -> placeGate(sp, level);
            case "summit" -> placeStair(sp, level);
            default -> placeCovered(sp, level);
        }
    }

    private void placeCovered(DelveSpec.SetPiece sp, DelveSpec.Level level) {
        int cw = sp.cellsX();
        int cd = sp.cellsZ();
        int ac = Math.floorDiv(sp.x() - ox, cell) - (cw - 1) / 2;
        int ar = Math.floorDiv(sp.z() - oz, cell) - (cd - 1) / 2;
        if (sp.params().has("attach")) {
            // right beside another set piece: centred on its side (the Bridge at the Dwarrowdelf's east end)
            JsonObject at = sp.params().getAsJsonObject("attach");
            Node to = null;
            for (Map.Entry<DelveSpec.SetPiece, Node> e : setNode.entrySet()) {
                if (e.getKey().id().equals(J.s(at, "to"))) {
                    to = e.getValue();
                }
            }
            if (to != null) {
                int side = side(J.s(at, "side"));
                ac = switch (side) {
                    case DelveSpec.E -> to.col + to.cellsX;
                    case DelveSpec.W -> to.col - cw;
                    default -> to.col + (to.cellsX - cw) / 2;
                };
                ar = switch (side) {
                    case DelveSpec.S -> to.row + to.cellsZ;
                    case DelveSpec.N -> to.row - cd;
                    default -> to.row + (to.cellsZ - cd) / 2;
                };
            }
        }
        List<DelveSpec.Level> span = levelsMeeting(level.floor() - sp.below() - 1, level.floor() + sp.height() + 1);
        for (int d = 0; d <= sp.search(); d++) {
            for (int[] off : ring(d)) {
                int c0 = ac + off[0];
                int r0 = ar + off[1];
                if (!free(span, c0, r0, cw, cd) || !coveredAll(level, c0, r0, cw, cd, sp.height())) {
                    continue;
                }
                Node n = newNode(level, c0, r0, cw, cd, sp);
                n.kind = sp.kind();
                n.keep = true;
                n.y = level.floor();
                n.h = sp.height();
                cellBox(n, c0, r0, cw, cd);
                claim(span, c0, r0, cw, cd, n);
                setNode.put(sp, n);
                log.append(String.format("  %s (%s) at cells %d,%d (%d cells away)%n", sp.id(), sp.kind(), c0, r0, d));
                return;
            }
        }
        log.append(String.format("  %s (%s): NO ROOM within %d cells%n", sp.id(), sp.kind(), sp.search()));
    }

    private boolean coveredAll(DelveSpec.Level level, int c0, int r0, int cw, int cd, int height) {
        for (int c = c0; c < c0 + cw; c++) {
            for (int r = r0; r < r0 + cd; r++) {
                if (!inGrid(c, r) || !covered(level, c, r, height)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Offsets at Chebyshev distance d, in a fixed order (nearest the centre line first). */
    private static List<int[]> ring(int d) {
        List<int[]> out = new ArrayList<>();
        if (d == 0) {
            out.add(new int[] {0, 0});
            return out;
        }
        for (int i = -d; i <= d; i++) {
            for (int j = -d; j <= d; j++) {
                if (Math.max(Math.abs(i), Math.abs(j)) == d) {
                    out.add(new int[] {i, j});
                }
            }
        }
        out.sort(Comparator.comparingInt((int[] o) -> Math.abs(o[0]) + Math.abs(o[1])));
        return out;
    }

    /**
     * A gate: its doorway at the mountain front ({@code x}, the lane of {@code z}'s cell row), facing out; the cells
     * from the doorway inward, through the mountain until one is covered at the gate's level, are its passage and
     * inner hall, and only the innermost takes corridors.
     */
    private void placeGate(DelveSpec.SetPiece sp, DelveSpec.Level level) {
        int facing = side(J.s(sp.params(), "facing"));
        int in = (facing + 2) % 4;
        int minCells = J.i(sp.params(), "min_cells");
        int maxCells = J.i(sp.params(), "max_cells");
        // the doorway: in a cell row's lane near the data's spot, where the mountain rises steeply behind it (at
        // least rise blocks within look blocks inward), the lowest such place; else the data's spot
        int foot = J.i(sp.params(), "foot_search");
        int look = J.i(sp.params(), "look");
        int rise = J.i(sp.params(), "rise");
        int rowSearch = J.i(sp.params(), "row_search");
        boolean ew = facing == DelveSpec.E || facing == DelveSpec.W;
        int baseLane = ew ? Math.floorDiv(sp.z() - oz, cell) : Math.floorDiv(sp.x() - ox, cell);
        int doorX = ew ? sp.x() : centreX(baseLane);
        int doorZ = ew ? centreZ(baseLane) : sp.z();
        double best = Double.MAX_VALUE;
        for (int lane = baseLane - rowSearch; lane <= baseLane + rowSearch; lane++) {
            for (int k = -foot; k <= foot; k++) {
                int x = ew ? sp.x() + k : centreX(lane);
                int z = ew ? centreZ(lane) : sp.z() + k;
                double g = ground.height(x, z);
                double behind = ground.height(x + DelveSpec.DX[in] * look, z + DelveSpec.DZ[in] * look);
                double score = g + Math.abs(k) * 0.01 + Math.abs(lane - baseLane) * 0.5;
                if (behind - g >= rise && score < best) {
                    best = score;
                    doorX = x;
                    doorZ = z;
                }
            }
        }
        int c = Math.floorDiv(doorX - ox, cell);
        int r = Math.floorDiv(doorZ - oz, cell);
        int doorY = (int) Math.floor(ground.height(doorX, doorZ)) + 1;
        int lo = Math.min(doorY, level.floor());
        int hi = Math.max(doorY, level.floor()) + sp.height();
        List<DelveSpec.Level> span = levelsMeeting(lo - 1, hi + 1);
        int k = 0;
        int ic = c;
        int ir = r;
        while (k < maxCells - 1 && (k < minCells - 1 || !covered(level, ic, ir, sp.height()))) {
            ic += DelveSpec.DX[in];
            ir += DelveSpec.DZ[in];
            k++;
        }
        int c0 = Math.min(c, ic);
        int r0 = Math.min(r, ir);
        int cw = Math.abs(ic - c) + 1;
        int cd = Math.abs(ir - r) + 1;
        if (!free(span, c0, r0, cw, cd)) {
            log.append(String.format("  %s: cells taken%n", sp.id()));
        }
        Node n = newNode(level, c0, r0, cw, cd, sp);
        n.kind = sp.kind();
        n.keep = true;
        n.y = level.floor();
        n.h = sp.height();
        n.doorCol = ic;
        n.doorRow = ir;
        cellBox(n, ic, ir, 1, 1);
        claim(span, c0, r0, cw, cd, n);
        setNode.put(sp, n);
        gates.put(sp, new int[] {doorX, doorY, doorZ, facing});
        log.append(String.format("  %s: doorway (%d, %d, %d) facing %s, %d cells to its hall%n", sp.id(), doorX, doorY,
                doorZ, DelveSpec.SIDES[facing], cw * cd));
    }

    /**
     * The Endless Stair: the cell with the highest ground near its spot; a landing on every level from its own up
     * that is covered there, and on up through the rock to just under the summit ({@code tower_depth}).
     */
    private void placeStair(DelveSpec.SetPiece sp, DelveSpec.Level bottom) {
        int ac = Math.floorDiv(sp.x() - ox, cell);
        int ar = Math.floorDiv(sp.z() - oz, cell);
        int best = -1;
        float bestGround = -Float.MAX_VALUE;
        for (int d = 0; d <= sp.search(); d++) {
            for (int[] off : ring(d)) {
                int c = ac + off[0];
                int r = ar + off[1];
                if (!inGrid(c, r) || !covered(bottom, c, r, spec.tallest(bottom))) {
                    continue;
                }
                boolean taken = false;
                for (DelveSpec.Level l : spec.levels) {
                    if (l.floor() >= bottom.floor() && owner[l.index()][idx(c, r)] != NONE) {
                        taken = true;
                    }
                }
                if (!taken && centre[idx(c, r)] > bestGround) {
                    bestGround = centre[idx(c, r)];
                    best = idx(c, r);
                }
            }
        }
        if (best < 0) {
            log.append(String.format("  %s: NO ROOM%n", sp.id()));
            return;
        }
        int c = best / rows;
        int r = best % rows;
        List<Node> list = new ArrayList<>();
        for (DelveSpec.Level l : spec.levels) {
            if (l.floor() < bottom.floor()) {
                continue;
            }
            if (covered(l, c, r, spec.tallest(l))) {
                Node n = newNode(l, c, r, 1, 1, sp);
                n.kind = sp.kind();
                n.keep = true;
                n.y = l.floor();
                n.h = l.style().height();
                cellBox(n, c, r, 1, 1);
                n.cx = centreX(c);
                n.cz = centreZ(r);
                n.hx = J.i(sp.params(), "half");
                n.hz = n.hx;
                owner[l.index()][idx(c, r)] = n.id;
                list.add(n);
            } else {
                owner[l.index()][idx(c, r)] = RESERVED;
            }
        }
        landings.put(sp, list);
        setNode.put(sp, list.get(0));
        int top = (int) Math.floor(centre[best]) - J.i(sp.params(), "tower_depth");
        towers.put(sp, top);
        log.append(String.format("  %s: cell %d,%d, ground %.0f, %d landings, tower floor %d%n", sp.id(), c, r,
                bestGround, list.size(), top));
    }

    static int side(String s) {
        int i = List.of(DelveSpec.SIDES).indexOf(s);
        if (i < 0) {
            throw new IllegalArgumentException("delve: bad side " + s);
        }
        return i;
    }

    // ---- maze ----

    private void mazeNodes() {
        for (DelveSpec.Level l : spec.levels) {
            for (int c = 0; c < cols; c++) {
                for (int r = 0; r < rows; r++) {
                    if (owner[l.index()][idx(c, r)] == NONE && open(l, c, r)) {
                        Node n = newNode(l, c, r, 1, 1, null);
                        owner[l.index()][idx(c, r)] = n.id;
                    }
                }
            }
        }
    }

    private boolean allowed(Node n, int c, int r, int side) {
        if (n.set == null) {
            return true;
        }
        if ((n.set.doors() & (1 << side)) == 0) {
            return false;
        }
        return n.doorCol < 0 || (c == n.doorCol && r == n.doorRow);
    }

    private void addEdge(Node a, Node b, int dir, int ac, int ar, int bc, int br, double weight) {
        Edge e = new Edge(a, b, dir, ac, ar, bc, br, weight);
        edges.add(e);
        a.edges.add(e);
        b.edges.add(e);
    }

    private void candidates() {
        double wig = spec.routeWiggle;
        for (DelveSpec.Level l : spec.levels) {
            int[] own = owner[l.index()];
            for (int c = 0; c < cols; c++) {
                for (int r = 0; r < rows; r++) {
                    int n = own[idx(c, r)];
                    if (n < 0) {
                        continue;
                    }
                    for (int dir : new int[] {DelveSpec.E, DelveSpec.S}) {
                        int nc = c + DelveSpec.DX[dir];
                        int nr = r + DelveSpec.DZ[dir];
                        if (!inGrid(nc, nr)) {
                            continue;
                        }
                        int m = own[idx(nc, nr)];
                        if (m < 0 || m == n) {
                            continue;
                        }
                        Node a = nodes.get(n);
                        Node b = nodes.get(m);
                        if (!allowed(a, c, r, dir) || !allowed(b, nc, nr, (dir + 2) % 4)) {
                            continue;
                        }
                        addEdge(a, b, dir, c, r, nc, nr, 1 + wig * rnd.nextDouble());
                    }
                }
            }
        }
        for (int li = 0; li + 1 < spec.levels.size(); li++) {
            DelveSpec.Level l = spec.levels.get(li);
            for (int c = 0; c < cols; c++) {
                for (int r = 0; r < rows; r++) {
                    int n = owner[li][idx(c, r)];
                    int m = owner[li + 1][idx(c, r)];
                    if (n < 0 || m < 0) {
                        continue;
                    }
                    Node a = nodes.get(n);
                    Node b = nodes.get(m);
                    if (a.set != null || b.set != null) {
                        continue;
                    }
                    if (Rng.unit(Rng.hash(seed, 77, li, idx(c, r))) < l.stairs()) {
                        addEdge(a, b, -1, c, r, c, r, spec.stairCost * (1 + wig * rnd.nextDouble()));
                    }
                }
            }
        }
        for (List<Node> list : landings.values()) {
            for (int i = 0; i + 1 < list.size(); i++) {
                Node a = list.get(i);
                Node b = list.get(i + 1);
                addEdge(a, b, -1, a.col, a.row, b.col, b.row, spec.stairCost * (1 + wig * rnd.nextDouble()));
            }
        }
    }

    private void keep(Edge e) {
        e.removed = false;
    }

    /** The hidden route: shortest weighted ways from each route set piece to the next. */
    private boolean route(List<Node> out) {
        TreeMap<Integer, Node> points = new TreeMap<>();
        for (DelveSpec.SetPiece sp : spec.setPieces) {
            Node n = setNode.get(sp);
            if (sp.route() >= 0 && n != null) {
                points.put(sp.route(), n);
            }
        }
        if (points.size() < 2) {
            log.append("  route: fewer than two route pieces placed\n");
            return false;
        }
        List<Node> pts = new ArrayList<>(points.values());
        boolean complete = true;
        for (int i = 0; i + 1 < pts.size(); i++) {
            List<Edge> path = shortest(pts.get(i), pts.get(i + 1), pts);
            if (path == null) {
                log.append(String.format("  route: no way from %s to %s%n", pts.get(i).set.id(), pts.get(i + 1).set.id()));
                complete = false;
                continue;
            }
            Node at = pts.get(i);
            if (out.isEmpty()) {
                out.add(at);
            }
            for (Edge e : path) {
                keep(e);
                e.route = true;
                at = e.other(at);
                at.route = true;
                out.add(at);
            }
            pts.get(i).route = true;
        }
        return complete;
    }

    private List<Edge> shortest(Node from, Node to, List<Node> routePoints) {
        double[] dist = new double[nodes.size()];
        Edge[] via = new Edge[nodes.size()];
        Arrays.fill(dist, Double.MAX_VALUE);
        dist[from.id] = 0;
        PriorityQueue<double[]> q = new PriorityQueue<>(Comparator.comparingDouble((double[] o) -> o[0]));
        q.add(new double[] {0, from.id});
        while (!q.isEmpty()) {
            double[] top = q.poll();
            int n = (int) top[1];
            if (top[0] > dist[n]) {
                continue;
            }
            if (n == to.id) {
                break;
            }
            Node node = nodes.get(n);
            for (Edge e : node.edges) {
                Node m = e.other(node);
                double w = e.weight;
                if (m.set != null && m != to && m != from) {
                    w += AVOID;
                }
                if (dist[n] + w < dist[m.id]) {
                    dist[m.id] = dist[n] + w;
                    via[m.id] = e;
                    q.add(new double[] {dist[m.id], m.id});
                }
            }
        }
        if (via[to.id] == null) {
            return null;
        }
        List<Edge> path = new ArrayList<>();
        Node at = to;
        while (at != from) {
            Edge e = via[at.id];
            path.add(e);
            at = e.other(at);
        }
        java.util.Collections.reverse(path);
        return path;
    }

    private int find(int[] uf, int i) {
        while (uf[i] != i) {
            uf[i] = uf[uf[i]];
            i = uf[i];
        }
        return i;
    }

    private void spanningTree() {
        int[] uf = new int[nodes.size()];
        for (int i = 0; i < uf.length; i++) {
            uf[i] = i;
        }
        for (Edge e : edges) {
            if (!e.removed) {
                uf[find(uf, e.a.id)] = find(uf, e.b.id);
            }
        }
        Edge[] order = edges.toArray(new Edge[0]);
        for (int i = order.length - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            Edge t = order[i];
            order[i] = order[j];
            order[j] = t;
        }
        for (Edge e : order) {
            if (!e.removed) {
                continue;
            }
            int a = find(uf, e.a.id);
            int b = find(uf, e.b.id);
            if (a != b) {
                keep(e);
                uf[a] = b;
            }
        }
    }

    private void remove(Node n) {
        n.removed = true;
        for (Edge e : n.edges) {
            e.removed = true;
        }
    }

    private void dropIslands(Node start) {
        if (start == null) {
            for (Node n : nodes) {
                if (n.set != null) {
                    start = n;
                    break;
                }
            }
        }
        boolean[] seen = new boolean[nodes.size()];
        if (start != null) {
            ArrayDeque<Node> q = new ArrayDeque<>();
            q.add(start);
            seen[start.id] = true;
            while (!q.isEmpty()) {
                Node n = q.poll();
                for (Edge e : n.edges) {
                    if (e.removed) {
                        continue;
                    }
                    Node m = e.other(n);
                    if (!seen[m.id]) {
                        seen[m.id] = true;
                        q.add(m);
                    }
                }
            }
        }
        int dropped = 0;
        for (Node n : nodes) {
            if (!seen[n.id] && !n.removed) {
                remove(n);
                dropped++;
            }
        }
        if (dropped > 0) {
            log.append(String.format("  %d nodes cut off from the route dropped%n", dropped));
        }
    }

    private void prune() {
        int removed = 0;
        for (int pass = 0; pass < spec.prunePasses; pass++) {
            List<Node> ends = new ArrayList<>();
            for (Node n : nodes) {
                if (!n.removed && !n.keep && !n.route && n.degree() <= 1) {
                    ends.add(n);
                }
            }
            for (Node n : ends) {
                if (n.removed || n.degree() > 1) {
                    continue;
                }
                if (Rng.unit(Rng.hash(n.hash, 91, pass, 0)) < n.level.prune()) {
                    remove(n);
                    removed++;
                }
            }
        }
        log.append(String.format("  pruned %d dead ends%n", removed));
    }

    private void loops() {
        int added = 0;
        for (Node n : nodes) {
            if (n.removed || n.keep || n.degree() != 1) {
                continue;
            }
            if (Rng.unit(Rng.hash(n.hash, 93, 0, 0)) >= n.level.loops()) {
                continue;
            }
            List<Edge> options = new ArrayList<>();
            for (Edge e : n.edges) {
                if (e.removed && e.dir >= 0 && !e.other(n).removed && !linked(n, e.other(n))) {
                    options.add(e);
                }
            }
            if (!options.isEmpty()) {
                keep(options.get(Rng.range(Rng.hash(n.hash, 94, 0, 0), 0, options.size() - 1)));
                added++;
            }
        }
        log.append(String.format("  %d loops%n", added));
    }

    private static boolean linked(Node a, Node b) {
        for (Edge e : a.edges) {
            if (!e.removed && e.other(a) == b) {
                return true;
            }
        }
        return false;
    }

    // ---- what each node becomes ----

    private String pickRoom(DelveSpec.Level level, long h, boolean templatesOk) {
        double total = 0;
        for (Map.Entry<String, Double> e : level.rooms().entrySet()) {
            if (templatesOk || !e.getKey().equals("template")) {
                total += e.getValue();
            }
        }
        if (total <= 0) {
            return "junction";
        }
        double r = Rng.unit(h) * total;
        for (Map.Entry<String, Double> e : level.rooms().entrySet()) {
            if (!templatesOk && e.getKey().equals("template")) {
                continue;
            }
            r -= e.getValue();
            if (r < 0) {
                return e.getKey();
            }
        }
        return "junction";
    }

    private void shape() {
        int maxHalf = cell / 2 - spec.margin - 2;
        for (Node n : nodes) {
            if (n.removed || n.set != null) {
                continue;
            }
            DelveSpec.Level l = n.level;
            int baseX = centreX(n.col);
            int baseZ = centreZ(n.row);
            n.cx = baseX;
            n.cz = baseZ;
            if (n.hasVertical()) {
                n.kind = "stair";
                n.hx = spec.stairwell.half();
                n.hz = n.hx;
                n.y = l.floor();
                n.h = l.style().height();
                continue;
            }
            int deg = n.degree();
            double chance = deg <= 1 ? l.deadEndRooms() : l.roomChance();
            n.y = l.floor() + Rng.range(Rng.hash(n.hash, 95, 0, 0), -l.floorJitter(), l.floorJitter());
            if (Rng.unit(Rng.hash(n.hash, 96, 0, 0)) < chance) {
                String kind = pickRoom(l, Rng.hash(n.hash, 97, 0, 0), !templates.isEmpty());
                if (kind.equals("template") && !fitTemplate(n, maxHalf)) {
                    kind = pickRoom(l, Rng.hash(n.hash, 98, 0, 0), false);
                }
                if (!kind.equals("template") && !kind.equals("junction")) {
                    DelveSpec.RoomKind rk = spec.room(kind);
                    n.kind = kind;
                    int w = odd(Rng.range(Rng.hash(n.hash, 99, 0, 0), rk.size()[0], rk.size()[1]));
                    int d = odd(Rng.range(Rng.hash(n.hash, 100, 0, 0), rk.size()[0], rk.size()[1]));
                    n.hx = Math.min((w - 1) / 2, maxHalf);
                    n.hz = Math.min((d - 1) / 2, maxHalf);
                    n.h = Rng.range(Rng.hash(n.hash, 101, 0, 0), rk.height()[0], rk.height()[1]);
                    int jx = maxHalf - n.hx;
                    int jz = maxHalf - n.hz;
                    n.cx = baseX + Rng.range(Rng.hash(n.hash, 102, 0, 0), -jx, jx);
                    n.cz = baseZ + Rng.range(Rng.hash(n.hash, 103, 0, 0), -jz, jz);
                    n.variant = pickVariant(rk, Rng.hash(n.hash, 104, 0, 0));
                    continue;
                }
                if (kind.equals("template")) {
                    continue;
                }
            }
            n.kind = "junction";
            n.hx = l.style().width() / 2 + 1;
            n.hz = n.hx;
            n.h = l.style().height();
        }
    }

    private static int odd(int v) {
        return v % 2 == 0 ? v + 1 : v;
    }

    private static DelveSpec.Variant pickVariant(DelveSpec.RoomKind rk, long h) {
        if (rk.variants().isEmpty()) {
            return null;
        }
        double total = 0;
        for (DelveSpec.Variant v : rk.variants()) {
            total += v.weight();
        }
        double r = Rng.unit(h) * total;
        for (DelveSpec.Variant v : rk.variants()) {
            r -= v.weight();
            if (r < 0) {
                return v;
            }
        }
        return rk.variants().get(rk.variants().size() - 1);
    }

    /** Picks a template (and a turn) that fits the cell and has a doorway on every side the node needs. */
    private boolean fitTemplate(Node n, int maxHalf) {
        int need = 0;
        for (Edge e : n.edges) {
            if (!e.removed && e.dir >= 0) {
                need |= 1 << sideOf(n, e);
            }
        }
        int start = Rng.range(Rng.hash(n.hash, 105, 0, 0), 0, templates.size() - 1);
        int turn0 = Rng.range(Rng.hash(n.hash, 106, 0, 0), 0, 3);
        for (int i = 0; i < templates.size(); i++) {
            TemplateInfo t = templates.get((start + i) % templates.size());
            for (int k = 0; k < 4; k++) {
                int rot = (turn0 + k) % 4;
                int sx = rot % 2 == 0 ? t.sx() : t.sz();
                int sz = rot % 2 == 0 ? t.sz() : t.sx();
                if (sx > 2 * maxHalf + 1 || sz > 2 * maxHalf + 1 || t.sy() > spec.tallest(n.level) + 2) {
                    continue;
                }
                List<int[]> doors = turned(t, rot);
                int have = 0;
                for (int[] d : doors) {
                    have |= 1 << d[3];
                }
                if ((have & need) != need) {
                    continue;
                }
                n.kind = "template";
                n.template = t;
                n.rotation = rot;
                n.templateDoors = doors;
                // the template's box is the interior with its own walls: x0..x1 = its footprint
                n.hx = (sx - 1) / 2;
                n.hz = (sz - 1) / 2;
                n.cx = centreX(n.col);
                n.cz = centreZ(n.row);
                n.y = n.level.floor();
                n.h = t.sy();
                return true;
            }
        }
        return false;
    }

    /** Doors of a template turned {@code rot} quarter turns clockwise, relative to the turned box's lowest corner. */
    static List<int[]> turned(TemplateInfo t, int rot) {
        List<int[]> out = new ArrayList<>();
        for (int[] d : t.doors()) {
            int x = d[0];
            int z = d[2];
            int side = d[3];
            for (int i = 0; i < rot; i++) {
                // clockwise seen from above: (x, z) -> (-z, x), then shifted back into the box
                int nx = -z;
                int nz = x;
                x = nx;
                z = nz;
                side = (side + 1) % 4;
            }
            int sx = t.sx();
            int sz = t.sz();
            int minX = switch (rot) {
                case 1, 2 -> -(rot == 1 ? sz : sx) + 1;
                default -> 0;
            };
            int minZ = switch (rot) {
                case 2, 3 -> -(rot == 2 ? sz : sx) + 1;
                default -> 0;
            };
            out.add(new int[] {x - minX, d[1], z - minZ, side});
        }
        return out;
    }

    static int sideOf(Node n, Edge e) {
        return e.a == n ? e.dir : (e.dir + 2) % 4;
    }

    // ---- pieces ----

    private int[] cellOf(Node n, Edge e) {
        return e.a == n ? new int[] {e.aCol, e.aRow} : new int[] {e.bCol, e.bRow};
    }

    /** Where a corridor meets a node: {lane, door floor y}. */
    private int[] door(Node n, Edge e) {
        int side = sideOf(n, e);
        boolean ew = side == DelveSpec.E || side == DelveSpec.W;
        if (n.templateDoors != null) {
            int[] best = null;
            for (int[] d : n.templateDoors) {
                if (d[3] == side && (best == null || Math.abs((ew ? d[2] - n.hz : d[0] - n.hx))
                        < Math.abs((ew ? best[2] - n.hz : best[0] - n.hx)))) {
                    best = d;
                }
            }
            if (best != null) {
                return new int[] {ew ? n.z0() + best[2] : n.x0() + best[0], n.y - 1 + best[1]};
            }
        }
        if (n.set != null && !n.kind.equals("endless_stair")) {
            int[] cr = cellOf(n, e);
            return new int[] {ew ? centreZ(cr[1]) : centreX(cr[0]), n.y};
        }
        return new int[] {ew ? n.cz : n.cx, n.y};
    }

    private List<Piece> pieces() {
        List<Piece> out = new ArrayList<>();
        Map<Integer, List<int[]>> doors = new HashMap<>();
        for (Edge e : edges) {
            if (e.removed || e.dir < 0 || e.a.removed || e.b.removed) {
                continue;
            }
            int[] da = door(e.a, e);
            int[] db = door(e.b, e);
            int d = e.dir;
            int sx = e.a.edgeAt(d);
            int ex = e.b.edgeAt((d + 2) % 4);
            int m = switch (d) {
                case DelveSpec.E -> ox + (e.aCol + 1) * cell;
                case DelveSpec.W -> ox + e.aCol * cell;
                case DelveSpec.S -> oz + (e.aRow + 1) * cell;
                default -> oz + e.aRow * cell;
            };
            List<int[]> legs = new ArrayList<>();
            boolean ew = d == DelveSpec.E || d == DelveSpec.W;
            if (da[0] == db[0]) {
                legs.add(ew ? new int[] {sx, da[0], ex, db[0]} : new int[] {da[0], sx, db[0], ex});
            } else if (ew) {
                legs.add(new int[] {sx, da[0], m, da[0]});
                legs.add(new int[] {m, da[0], m, db[0]});
                legs.add(new int[] {m, db[0], ex, db[0]});
            } else {
                legs.add(new int[] {da[0], sx, da[0], m});
                legs.add(new int[] {da[0], m, db[0], m});
                legs.add(new int[] {db[0], m, db[0], ex});
            }
            DelveSpec.Style style = e.a.level.style();
            boolean deadEnd = (!e.a.keep && e.a.degree() == 1) || (!e.b.keep && e.b.degree() == 1);
            boolean collapsed = !e.route && deadEnd
                    && Rng.unit(Rng.hash(seed, 111, e.a.id, e.b.id)) < spec.ruin.collapse();
            out.add(new CorridorPiece(legs, da[1], db[1], style, collapsed, e.route));
            doors.computeIfAbsent(e.a.id, k -> new ArrayList<>()).add(new int[] {sideOf(e.a, e), da[0], da[1],
                    style.width(), style.height()});
            doors.computeIfAbsent(e.b.id, k -> new ArrayList<>()).add(new int[] {sideOf(e.b, e), db[0], db[1],
                    style.width(), style.height()});
        }
        // stairwells: runs of stair nodes joined by kept stairs in one cell column
        Map<Integer, List<Node>> columns = new TreeMap<>();
        for (Node n : nodes) {
            if (!n.removed && n.kind.equals("stair")) {
                columns.computeIfAbsent(idx(n.col, n.row), k -> new ArrayList<>()).add(n);
            }
        }
        for (List<Node> col : columns.values()) {
            col.sort(Comparator.comparingInt(n -> n.level.index()));
            List<Node> run = new ArrayList<>();
            for (Node n : col) {
                if (!run.isEmpty() && !stairBetween(run.get(run.size() - 1), n)) {
                    out.add(new ShaftPiece(spec, run, doors, null, -1, 0));
                    run = new ArrayList<>();
                }
                run.add(n);
            }
            out.add(new ShaftPiece(spec, run, doors, null, -1, 0));
        }
        for (Node n : nodes) {
            if (n.removed || n.set != null || n.kind.equals("stair")) {
                continue;
            }
            List<int[]> nd = doors.getOrDefault(n.id, List.of());
            if (n.kind.equals("template")) {
                out.add(new TemplatePiece(n, nd));
            } else {
                out.add(new RoomPiece(spec, n, nd, n.kind.equals("junction") ? null : spec.room(n.kind).params(),
                        n.variant, n.level.style()));
            }
        }
        for (DelveSpec.SetPiece sp : spec.setPieces) {
            Node n = setNode.get(sp);
            if (n == null || n.removed) {
                continue;
            }
            List<int[]> nd = doors.getOrDefault(n.id, List.of());
            switch (sp.kind()) {
                case "endless_stair" -> {
                    List<Node> run = new ArrayList<>();
                    for (Node l : landings.get(sp)) {
                        if (!l.removed) {
                            run.add(l);
                        }
                    }
                    int top = towers.get(sp);
                    out.add(new ShaftPiece(spec, run, doors, sp, top, exitSide(run.get(0), top)));
                }
                case "west_gate", "east_gate" -> out.add(new GatePiece(spec, n, nd, gates.get(sp), ground));
                case "great_hall" -> out.add(new HallPiece(spec, n, nd));
                case "chasm_bridge" -> out.add(new ChasmPiece(spec, n, nd, lowestFloor(n)));
                default -> out.add(new RoomPiece(spec, n, nd, sp.params(), null, n.level.style()));
            }
        }
        return out;
    }

    private int lowestFloor(Node n) {
        return n.y - n.set.below();
    }

    private static boolean stairBetween(Node a, Node b) {
        for (Edge e : a.edges) {
            if (!e.removed && e.dir < 0 && e.other(a) == b) {
                return true;
            }
        }
        return false;
    }

    /** The side Durin's Tower opens on: the way the ground falls below its floor soonest. */
    private int exitSide(Node landing, int top) {
        int best = DelveSpec.E;
        int bestDist = Integer.MAX_VALUE;
        for (int s = 0; s < 4; s++) {
            for (int d = 8; d < 400; d += 2) {
                int x = landing.cx + DelveSpec.DX[s] * d;
                int z = landing.cz + DelveSpec.DZ[s] * d;
                if (ground.height(x, z) < top) {
                    if (d < bestDist) {
                        bestDist = d;
                        best = s;
                    }
                    break;
                }
            }
        }
        return best | (Math.min(bestDist, 400) << 2);
    }
}
