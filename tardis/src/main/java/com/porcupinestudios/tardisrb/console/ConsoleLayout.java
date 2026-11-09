package com.porcupinestudios.tardisrb.console;

/**
 * Click reach and the console's sides, shared by the click test (client) and the server. Where each control stands
 * comes from its data file (panel, u, v); the panels' shape from the console model
 * ({@code assets/tardis_rb/console/capaldi.json}).
 */
public final class ConsoleLayout {
    /** How far a player can reach a control from, in blocks. */
    public static final double REACH = 5.0;
    /** How far from the console block's centre its controls reach, in blocks. */
    public static final double RADIUS = 1.6;

    /** The time rotor's axis from the console block's corner (the model's {@code pivot}); the monitor turns round it. */
    public static final double PIVOT_X = 0.5;
    public static final double PIVOT_Z = 0.25;

    /** The side the monitor stands over until someone turns it (where the model has it). */
    public static final int MONITOR_HOME_SIDE = 1;

    private ConsoleLayout() {}

    /** Panel k faces this way from the rotor axis, degrees ({@code atan2(z, x)}): panel 0 faces the door (+z). */
    public static double facing(int panel) {
        return 90 - 60 * panel;
    }

    /** The side (0-5) the monitor stands over when turned to {@code angle} ({@code atan2(z, x)} degrees; NaN = home). */
    public static int side(float angle) {
        if (Float.isNaN(angle)) {
            return MONITOR_HOME_SIDE;
        }
        return Math.floorMod(Math.round((90 - angle) / 60F), 6);
    }

    /** The key one clickable part of a control is known by (its uses and last use): {@code <control id>|<part>}. */
    public static String key(String control, String click) {
        return control + "|" + click;
    }
}
