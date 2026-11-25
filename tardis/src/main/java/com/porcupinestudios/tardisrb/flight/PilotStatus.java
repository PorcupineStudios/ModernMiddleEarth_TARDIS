package com.porcupinestudios.tardisrb.flight;

/**
 * What the local pilot's client knows about the box it flies, sent by the server every half second
 * ({@code net/PilotNet.Status}): read by the box's physics on the pilot's client (out of charge: slower, no boost,
 * drift; flight speed upgrades) and drawn by the flight HUD. Plain values, so common code can read them.
 */
public final class PilotStatus {
    public static volatile double health;
    public static volatile double maxHealth;
    public static volatile long charge;
    public static volatile long chargeCap;
    /** x(1 + upgrades) on speed in the world and in the vortex. */
    public static volatile double flightSpeed = 1;
    public static volatile double vortexSpeed = 1;
    public static volatile boolean camera;

    private PilotStatus() {}

    public static boolean empty() {
        return charge <= 0;
    }

    public static void set(double health, double maxHealth, long charge, long chargeCap, double flightSpeed,
            double vortexSpeed, boolean camera) {
        PilotStatus.health = health;
        PilotStatus.maxHealth = maxHealth;
        PilotStatus.charge = charge;
        PilotStatus.chargeCap = chargeCap;
        PilotStatus.flightSpeed = flightSpeed;
        PilotStatus.vortexSpeed = vortexSpeed;
        PilotStatus.camera = camera;
    }
}
