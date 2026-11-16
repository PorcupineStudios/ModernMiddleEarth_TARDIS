package com.porcupinestudios.lotrport.rig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The blocks a hiding creature becomes: its own boxes, posed in its hide pose (feet planted on flat ground, fully
 * crouched), filled into whole blocks. So the rock pile is the creature's shape, block for block, and the change
 * between the two lines up.
 *
 * <p>Cells are in the drawing frame: the creature stands on a block centre, turned a quarter turn, so cell
 * {@code (x, y, z)} is the block whose centre is {@code x, z} blocks from its feet along the rig's x and z (rig scale
 * applied) and whose bottom is {@code y} blocks up. A cell is filled when at least {@link #FILL} of its sample points
 * are inside a box; its block is the one most of those points fell in.
 */
public final class RigVoxels {
    /** Share of a cell's 27 sample points that must be inside the body. */
    public static final float FILL = 0.4f;

    /** The block ids the cells use. */
    public final List<String> blocks;
    /** Cells: x, y, z, index into {@link #blocks}. */
    public final int[][] cells;

    private RigVoxels(List<String> blocks, int[][] cells) {
        this.blocks = blocks;
        this.cells = cells;
    }

    public static RigVoxels of(RigSkeleton sk) {
        RigDef.Hide hide = sk.def.hide().orElse(null);
        String fallback = hide == null ? "minecraft:stone" : hide.block();
        Map<String, Integer> ids = new LinkedHashMap<>();
        List<int[]> out = new ArrayList<>();
        if (hide != null && !hide.blocks().isEmpty()) { // written by hand
            int id = ids.computeIfAbsent(fallback, k -> ids.size());
            for (List<Integer> b : hide.blocks()) {
                if (b.size() >= 3) {
                    out.add(new int[] {b.get(0), b.get(1), b.get(2), id});
                }
            }
            return new RigVoxels(new ArrayList<>(ids.keySet()), out.toArray(new int[0][]));
        }

        float scale = sk.def.scale();
        RigState state = new RigState(sk);
        RigPose pose = new RigPose(sk);
        RigState.Body body = new RigState.Body();
        body.scale = scale;
        body.hide = 1;
        RigState.Ground flat = (x, z, top, bottom) -> 0 <= top && 0 >= bottom ? 0 : Double.NaN;
        state.tick(body, flat, null);
        pose.pose(state, body, 1, true);

        // every box as an inverse matrix (rig blocks -> its own pixels) and its bounds
        List<Matrix4f> inv = new ArrayList<>();
        List<float[]> bounds = new ArrayList<>();
        List<Integer> mat = new ArrayList<>();
        Vector3f min = new Vector3f(Float.MAX_VALUE);
        Vector3f max = new Vector3f(-Float.MAX_VALUE);
        for (int b = 0; b < sk.count; b++) {
            // bone pixels -> rig: bone matrix, then y and z flipped, pixels / 16 (as the renderer draws them)
            Matrix4f toRig = new Matrix4f(pose.bones[b]).scale(1 / 16f, -1 / 16f, -1 / 16f);
            for (RigDef.Cube c : sk.bones[b].cubes()) {
                float g = c.inflate();
                float[] box = {c.origin().get(0) - g, c.origin().get(1) - g, c.origin().get(2) - g,
                        c.origin().get(0) + c.size().get(0) + g, c.origin().get(1) + c.size().get(1) + g,
                        c.origin().get(2) + c.size().get(2) + g};
                inv.add(new Matrix4f(toRig).invert());
                bounds.add(box);
                mat.add(ids.computeIfAbsent(c.block().orElse(fallback), k -> ids.size()));
                for (int k = 0; k < 8; k++) {
                    Vector3f p = toRig.transformPosition(new Vector3f(box[(k & 1) != 0 ? 3 : 0],
                            box[(k & 2) != 0 ? 4 : 1], box[(k & 4) != 0 ? 5 : 2]));
                    min.min(p);
                    max.max(p);
                }
            }
        }
        if (inv.isEmpty()) {
            return new RigVoxels(List.of(), new int[0][]);
        }
        int x0 = (int) Math.floor(min.x * scale - 0.5f);
        int x1 = (int) Math.ceil(max.x * scale + 0.5f);
        int y0 = Math.max(0, (int) Math.floor(min.y * scale));
        int y1 = (int) Math.ceil(max.y * scale);
        int z0 = (int) Math.floor(min.z * scale - 0.5f);
        int z1 = (int) Math.ceil(max.z * scale + 0.5f);
        Vector3f p = new Vector3f();
        Vector3f q = new Vector3f();
        Map<Integer, Integer> votes = new HashMap<>();
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    votes.clear();
                    int inside = 0;
                    for (int s = 0; s < 27; s++) {
                        float sx = x + (s % 3 - 1) / 3f;
                        float sy = y + 0.5f + (s / 3 % 3 - 1) / 3f;
                        float sz = z + (s / 9 - 1) / 3f;
                        p.set(sx / scale, sy / scale, sz / scale);
                        for (int i = 0; i < inv.size(); i++) {
                            inv.get(i).transformPosition(p, q);
                            float[] b = bounds.get(i);
                            if (q.x >= b[0] && q.x <= b[3] && q.y >= b[1] && q.y <= b[4] && q.z >= b[2]
                                    && q.z <= b[5]) {
                                inside++;
                                votes.merge(mat.get(i), 1, Integer::sum);
                                break;
                            }
                        }
                    }
                    if (inside >= FILL * 27) {
                        int best = votes.entrySet().stream().max(Map.Entry.comparingByValue()).get().getKey();
                        out.add(new int[] {x, y, z, best});
                    }
                }
            }
        }
        return new RigVoxels(new ArrayList<>(ids.keySet()), out.toArray(new int[0][]));
    }
}
