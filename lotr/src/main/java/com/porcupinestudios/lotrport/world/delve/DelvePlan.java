package com.porcupinestudios.lotrport.world.delve;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A delve's whole layout for one world seed ({@link DelvePlanner}): its nodes, the ways between them and the pieces
 * that build them, indexed by cell so a chunk finds the few pieces it holds at once. Immutable once made; every chunk
 * draws its own part ({@link #build}).
 */
public final class DelvePlan {
    public final DelveSpec spec;
    public final long seed;
    final int ox;
    final int oz;
    final int cell;
    final List<Node> nodes;
    final List<Edge> edges;
    final List<Piece> pieces;
    /** The hidden route's nodes, gate to gate, in order (empty if it couldn't be made). */
    final List<Node> route;
    final boolean routeComplete;
    private final Map<Long, int[]> index = new HashMap<>();
    public final int minX;
    public final int maxX;
    public final int minY;
    public final int maxY;
    public final int minZ;
    public final int maxZ;
    public final String summary;
    /** Gate doorways by set piece id: {x, y, z, facing}. */
    final Map<String, int[]> doorways;

    DelvePlan(DelveSpec spec, long seed, int ox, int oz, List<Node> nodes, List<Edge> edges, List<Piece> pieces,
              List<Node> route, boolean routeComplete, String summary, Map<String, int[]> doorways) {
        this.spec = spec;
        this.seed = seed;
        this.ox = ox;
        this.oz = oz;
        this.cell = spec.cell;
        this.nodes = nodes;
        this.edges = edges;
        this.pieces = pieces;
        this.route = route;
        this.routeComplete = routeComplete;
        this.summary = summary;
        this.doorways = Map.copyOf(doorways);
        int ax = Integer.MAX_VALUE;
        int ay = Integer.MAX_VALUE;
        int az = Integer.MAX_VALUE;
        int bx = Integer.MIN_VALUE;
        int by = Integer.MIN_VALUE;
        int bz = Integer.MIN_VALUE;
        Map<Long, List<Integer>> lists = new HashMap<>();
        for (int i = 0; i < pieces.size(); i++) {
            Piece p = pieces.get(i);
            ax = Math.min(ax, p.x0);
            ay = Math.min(ay, p.y0);
            az = Math.min(az, p.z0);
            bx = Math.max(bx, p.x1);
            by = Math.max(by, p.y1);
            bz = Math.max(bz, p.z1);
            for (int c = Math.floorDiv(p.x0 - ox, cell); c <= Math.floorDiv(p.x1 - ox, cell); c++) {
                for (int r = Math.floorDiv(p.z0 - oz, cell); r <= Math.floorDiv(p.z1 - oz, cell); r++) {
                    lists.computeIfAbsent(key(c, r), k -> new ArrayList<>()).add(i);
                }
            }
        }
        for (Map.Entry<Long, List<Integer>> e : lists.entrySet()) {
            index.put(e.getKey(), e.getValue().stream().mapToInt(Integer::intValue).toArray());
        }
        minX = ax;
        minY = ay;
        minZ = az;
        maxX = bx;
        maxY = by;
        maxZ = bz;
    }

    private static long key(int c, int r) {
        return ((long) c << 32) ^ (r & 0xFFFFFFFFL);
    }

    /** Whether anything of the delve lies in these columns. */
    public boolean touches(int x0, int z0, int x1, int z1) {
        return !pieces.isEmpty() && x1 >= minX && x0 <= maxX && z1 >= minZ && z0 <= maxZ;
    }

    /** Draws every piece that reaches the canvas's columns, in build order; returns how many. */
    public int build(Canvas canvas) {
        if (!touches(canvas.minX(), canvas.minZ(), canvas.maxX(), canvas.maxZ())) {
            return 0;
        }
        int[] ids = piecesIn(canvas.minX(), canvas.minZ(), canvas.maxX(), canvas.maxZ());
        Draw d = new Draw(canvas, spec, seed);
        int n = 0;
        for (int id : ids) {
            Piece p = pieces.get(id);
            if (p.touches(canvas)) {
                p.build(d);
                n++;
            }
        }
        return n;
    }

    /** Ids of the pieces whose cells meet these columns, in build order (order, then plan order). */
    int[] piecesIn(int x0, int z0, int x1, int z1) {
        int[] all = new int[0];
        int size = 0;
        for (int c = Math.floorDiv(x0 - ox, cell); c <= Math.floorDiv(x1 - ox, cell); c++) {
            for (int r = Math.floorDiv(z0 - oz, cell); r <= Math.floorDiv(z1 - oz, cell); r++) {
                int[] ids = index.get(key(c, r));
                if (ids == null) {
                    continue;
                }
                if (size + ids.length > all.length) {
                    all = Arrays.copyOf(all, Math.max(16, (size + ids.length) * 2));
                }
                System.arraycopy(ids, 0, all, size, ids.length);
                size += ids.length;
            }
        }
        int[] ids = Arrays.stream(all, 0, size).distinct().toArray();
        Integer[] boxed = Arrays.stream(ids).boxed().toArray(Integer[]::new);
        Arrays.sort(boxed, (p, q) -> {
            int o = Integer.compare(pieces.get(p).order, pieces.get(q).order);
            return o != 0 ? o : Integer.compare(p, q);
        });
        return Arrays.stream(boxed).mapToInt(Integer::intValue).toArray();
    }

    public boolean routeComplete() {
        return routeComplete;
    }

    /** The route as {x, y, z} per node, gate to gate. */
    public List<int[]> routePoints() {
        List<int[]> out = new ArrayList<>();
        for (Node n : route) {
            out.add(new int[] {n.cx, n.y, n.cz});
        }
        return out;
    }

    /** A gate's doorway {x, y (floor), z, facing side}, or null. */
    public int[] doorway(String setPiece) {
        int[] d = doorways.get(setPiece);
        return d == null ? null : d.clone();
    }

    /** A set piece's interior {x0, y, z0, x1, y + height - 1, z1} on its own level, or null if it wasn't placed. */
    public int[] setPiece(String id) {
        for (Node n : nodes) {
            if (n.set != null && n.set.id().equals(id)) {
                return new int[] {n.x0(), n.y, n.z0(), n.x1(), n.y + n.h - 1, n.z1()};
            }
        }
        return null;
    }

    /** Every maze room and junction (not set pieces): {x0, y, z0, x1, y + height - 1, z1}. */
    public List<int[]> mazeRooms() {
        List<int[]> out = new ArrayList<>();
        for (Node n : nodes) {
            if (n.set == null && !n.kind.equals("stair")) {
                out.add(new int[] {n.x0(), n.y, n.z0(), n.x1(), n.y + n.h - 1, n.z1()});
            }
        }
        return out;
    }

    /** How many levels the hidden route passes through. */
    public int routeLevels() {
        return (int) route.stream().map(n -> n.level.name()).distinct().count();
    }

    public int nodeCount() {
        return nodes.size();
    }

    public int pieceCount() {
        return pieces.size();
    }
}
