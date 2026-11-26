package com.porcupinestudios.lotrport.world.delve;

import java.util.List;

/**
 * A room built in game and saved as a structure template ({@code structure/<delve templates folder>/...}): placed
 * whole (each chunk its part), turned so it has a doorway (a jigsaw block, facing out) on every side a corridor
 * comes from. The corridors run to those doorways.
 */
final class TemplatePiece extends Piece {
    private final Node n;

    TemplatePiece(Node n, List<int[]> doors) {
        super(1);
        this.n = n;
        int sx = n.rotation % 2 == 0 ? n.template.sx() : n.template.sz();
        int sz = n.rotation % 2 == 0 ? n.template.sz() : n.template.sx();
        bounds(n.x0(), n.y - 1, n.z0(), n.x0() + sx - 1, n.y - 1 + n.template.sy() - 1, n.z0() + sz - 1);
    }

    @Override
    void build(Draw d) {
        d.c.template(n.template.id(), n.rotation, n.x0(), n.y - 1, n.z0());
    }
}
