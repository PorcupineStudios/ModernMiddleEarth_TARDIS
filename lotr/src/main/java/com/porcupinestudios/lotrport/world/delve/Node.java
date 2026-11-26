package com.porcupinestudios.lotrport.world.delve;

import java.util.ArrayList;
import java.util.List;

/**
 * One place in a delve's maze: a cell of a level (a junction, a room, a stairwell landing) or a set piece that holds
 * several cells. Its interior is the air box {@code x0..x1, y..y+h-1, z0..z1}; walls, floor and ceiling lie one
 * block outside it.
 */
final class Node {
    final int id;
    final DelveSpec.Level level;
    /** Lowest cell it holds, and how many cells it spans. */
    final int col;
    final int row;
    final int cellsX;
    final int cellsZ;
    /** The set piece it is part of, null for maze cells. */
    final DelveSpec.SetPiece set;
    final List<Edge> edges = new ArrayList<>();
    String kind = "junction";
    DelveSpec.Variant variant;
    int cx;
    int cz;
    int y;
    int hx;
    int hz;
    int h;
    /** On the hidden route; never pruned (with {@link #keep}). */
    boolean route;
    boolean keep;
    boolean removed;
    /** Doors only from this cell (a gate's inner hall), or -1 for any perimeter cell. */
    int doorCol = -1;
    int doorRow = -1;
    DelvePlanner.TemplateInfo template;
    int rotation;
    /** Template doors after turning, relative to the interior's lowest corner: {x, y, z, side}. */
    List<int[]> templateDoors;
    long hash;

    Node(int id, DelveSpec.Level level, int col, int row, int cellsX, int cellsZ, DelveSpec.SetPiece set) {
        this.id = id;
        this.level = level;
        this.col = col;
        this.row = row;
        this.cellsX = cellsX;
        this.cellsZ = cellsZ;
        this.set = set;
    }

    int x0() {
        return cx - hx;
    }

    int x1() {
        return cx + hx;
    }

    int z0() {
        return cz - hz;
    }

    int z1() {
        return cz + hz;
    }

    boolean holds(int c, int r) {
        return c >= col && c < col + cellsX && r >= row && r < row + cellsZ;
    }

    int degree() {
        int n = 0;
        for (Edge e : edges) {
            if (!e.removed) {
                n++;
            }
        }
        return n;
    }

    boolean hasVertical() {
        for (Edge e : edges) {
            if (!e.removed && e.dir < 0) {
                return true;
            }
        }
        return false;
    }

    /** Interior edge coordinate on a side: x for east/west, z for north/south. */
    int edgeAt(int side) {
        return switch (side) {
            case DelveSpec.N -> z0();
            case DelveSpec.E -> x1();
            case DelveSpec.S -> z1();
            default -> x0();
        };
    }

    @Override
    public String toString() {
        return kind + "#" + id + "@" + level.name() + "(" + cx + "," + y + "," + cz + ")";
    }
}
