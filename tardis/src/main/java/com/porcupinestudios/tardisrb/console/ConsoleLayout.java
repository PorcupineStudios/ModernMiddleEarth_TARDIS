package com.porcupinestudios.tardisrb.console;

/**
 * Click reach for the console's controls, shared by the click test (client) and the reach check (server). Where each
 * control is and its click box come from the console model ({@code assets/tardis_rb/console/capaldi.json}).
 */
public final class ConsoleLayout {
    /** How far a player can reach a control from, in blocks. */
    public static final double REACH = 5.0;
    /** How far from the console block's centre its controls reach, in blocks. */
    public static final double RADIUS = 1.6;

    /** The time rotor's axis from the console block's corner (the model's {@code pivot}); the monitor turns round it. */
    public static final double PIVOT_X = 0.5;
    public static final double PIVOT_Z = 0.25;

    private ConsoleLayout() {}
}
