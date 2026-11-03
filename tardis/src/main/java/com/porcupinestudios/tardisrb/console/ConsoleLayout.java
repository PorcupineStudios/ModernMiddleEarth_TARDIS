package com.porcupinestudios.tardisrb.console;

import net.minecraft.world.phys.Vec3;

/**
 * The console's shape, shared by the renderer (client), the click test (client) and the reach check (server): six
 * sloped panels around a central column, panel 0 facing +z and each next one 60 degrees on. Panels run from radius
 * {@link #INNER} (height {@link #INNER_Y}) out to {@link #OUTER} (height {@link #OUTER_Y}), in blocks from the centre of
 * the console block's floor.
 */
public final class ConsoleLayout {
    public static final double INNER = 0.35;
    public static final double OUTER = 1.25;
    public static final double INNER_Y = 1.05;
    public static final double OUTER_Y = 0.75;
    /** Half the size of a control's click box. */
    public static final double HIT = 0.09;
    /** How far a player can reach a control from, in blocks. */
    public static final double REACH = 5.0;

    private ConsoleLayout() {}

    /** The panel's yaw in radians: panel 0 faces +z. */
    public static double yaw(int panel) {
        return Math.toRadians(panel * 60.0);
    }

    /** How far the panels slope down, outward, in radians. */
    public static double tilt() {
        return Math.atan2(INNER_Y - OUTER_Y, OUTER - INNER);
    }

    /** A control's centre on its panel, relative to the console block's lower north-west corner. */
    public static Vec3 point(int panel, double u, double v) {
        double r = INNER + (OUTER - INNER) * (0.15 + 0.7 * v);
        double y = INNER_Y + (OUTER_Y - INNER_Y) * (0.15 + 0.7 * v) + 0.04;
        double half = r * Math.tan(Math.toRadians(30)) * 0.8;
        double side = (u - 0.5) * 2 * half;
        double yaw = yaw(panel);
        double dx = Math.sin(yaw);
        double dz = Math.cos(yaw);
        return new Vec3(0.5 + dx * r + dz * side, y, 0.5 + dz * r - dx * side);
    }
}
