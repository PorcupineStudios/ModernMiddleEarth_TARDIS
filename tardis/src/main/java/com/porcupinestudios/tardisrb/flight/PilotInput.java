package com.porcupinestudios.tardisrb.flight;

/**
 * The local pilot's flight keys, written each tick by the client ({@code client/PilotClient}) and read by the loose
 * box when the local player steers it ({@code TardisBody}): forward (W/S, -1..1), left (A/D), up (Q/E), boost
 * (sprint), brake (jump). Plain values, so common code can read them without touching client classes.
 */
public final class PilotInput {
    public static volatile float forward;
    public static volatile float left;
    public static volatile float up;
    public static volatile boolean boost;
    public static volatile boolean brake;

    private PilotInput() {}

    public static void set(float forward, float left, float up, boolean boost, boolean brake) {
        PilotInput.forward = forward;
        PilotInput.left = left;
        PilotInput.up = up;
        PilotInput.boost = boost;
        PilotInput.brake = brake;
    }

    public static void clear() {
        set(0, 0, 0, false, false);
    }
}
