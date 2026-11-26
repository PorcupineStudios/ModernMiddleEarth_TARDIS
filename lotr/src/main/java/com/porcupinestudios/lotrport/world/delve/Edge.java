package com.porcupinestudios.lotrport.world.delve;

/**
 * A way between two nodes: a corridor between neighbouring cells of one level ({@link #dir} is the side of
 * {@link #a} it leaves by), or a stair from {@code a} up to {@code b} on the level above ({@code dir} -1).
 */
final class Edge {
    final Node a;
    final Node b;
    final int dir;
    /** The cells it joins (a's and b's; the same cell for a stair). */
    final int aCol;
    final int aRow;
    final int bCol;
    final int bRow;
    final double weight;
    boolean route;
    boolean removed = true;

    Edge(Node a, Node b, int dir, int aCol, int aRow, int bCol, int bRow, double weight) {
        this.a = a;
        this.b = b;
        this.dir = dir;
        this.aCol = aCol;
        this.aRow = aRow;
        this.bCol = bCol;
        this.bRow = bRow;
        this.weight = weight;
    }

    Node other(Node n) {
        return n == a ? b : a;
    }
}
