package com.porcupinestudios.ringbearer.harness.content;

import com.porcupinestudios.ringbearer.harness.draught.WheelType;
import com.porcupinestudios.ringbearer.harness.physics.WheelEnvelope;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3dc;

/**
 * The ground under a wheel, read from the real shapes of the blocks (a slope's true face, a slab's top, a step's
 * edge) along three lines across the tyre, and the height the wheel's centre must be at to rest on it
 * ({@link WheelEnvelope}). The same on server (physics) and client (where the wheel is drawn).
 */
public final class WheelTerrain {
    /** Half the tyre's width: lines at the middle and both edges. */
    private static final double HALF_WIDTH = 0.12;

    private WheelTerrain() {}

    /**
     * The world height the centre must be at, already lowered to the drawn wheel's radius (so on flat ground: ground +
     * radius), or minus infinity if nothing is within reach.
     *
     * @param roll the rolling direction, horizontal unit (world)
     */
    public static double centreHeight(Level level, Vector3dc hub, Vector3dc roll, WheelType t) {
        double sx = -roll.z(), sz = roll.x(); // across the tyre
        double reach = t.contactReach(), height = t.contactHeight();
        int x0 = Mth.floor(hub.x() - reach - 1), x1 = Mth.floor(hub.x() + reach + 1);
        int z0 = Mth.floor(hub.z() - reach - 1), z1 = Mth.floor(hub.z() + reach + 1);
        int y0 = Mth.floor(hub.y() - height - t.travel() - 2), y1 = Mth.floor(hub.y() + t.maxCompression() + 1);
        double best = Double.NEGATIVE_INFINITY;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                for (int y = y0; y <= y1; y++) {
                    BlockState state = level.getBlockState(p.set(x, y, z));
                    if (state.isAir()) {
                        continue;
                    }
                    for (double t3 = -HALF_WIDTH; t3 <= HALF_WIDTH + 1e-9; t3 += HALF_WIDTH) {
                        double ox = hub.x() + sx * t3, oz = hub.z() + sz * t3;
                        if (state.getBlock() instanceof SlopeBlock) {
                            double[] seg = clip(ox, oz, roll.x(), roll.z(), x, z, x + 1, z + 1, reach);
                            if (seg != null) {
                                double h0 = y + SlopeBlock.topAt(state, ox + roll.x() * seg[0] - x,
                                        oz + roll.z() * seg[0] - z);
                                double h1 = y + SlopeBlock.topAt(state, ox + roll.x() * seg[1] - x,
                                        oz + roll.z() * seg[1] - z);
                                best = Math.max(best, WheelEnvelope.overEllipse(0, reach, height, seg[0], seg[1], h0,
                                        h1));
                            }
                            continue;
                        }
                        for (AABB box : state.getCollisionShape(level, p).toAabbs()) {
                            double[] seg = clip(ox, oz, roll.x(), roll.z(), x + box.minX, z + box.minZ,
                                    x + box.maxX, z + box.maxZ, reach);
                            if (seg != null) {
                                double top = y + box.maxY;
                                best = Math.max(best, WheelEnvelope.overEllipse(0, reach, height, seg[0], seg[1], top,
                                        top));
                            }
                        }
                    }
                }
            }
        }
        return best - (height - t.radius());
    }

    /** Where the line from (ox, oz) along (dx, dz) crosses the rectangle, within +-reach: {s0, s1}, or null. */
    private static double[] clip(double ox, double oz, double dx, double dz, double rx0, double rz0, double rx1,
                                 double rz1, double reach) {
        double lo = -reach, hi = reach;
        double[] o = {ox, oz}, d = {dx, dz}, mn = {rx0, rz0}, mx = {rx1, rz1};
        for (int a = 0; a < 2; a++) {
            if (Math.abs(d[a]) < 1e-9) {
                if (o[a] < mn[a] || o[a] > mx[a]) {
                    return null;
                }
            } else {
                double t0 = (mn[a] - o[a]) / d[a], t1 = (mx[a] - o[a]) / d[a];
                lo = Math.max(lo, Math.min(t0, t1));
                hi = Math.min(hi, Math.max(t0, t1));
            }
        }
        return hi >= lo ? new double[] {lo, hi} : null;
    }
}
