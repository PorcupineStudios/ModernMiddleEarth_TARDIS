package com.porcupinestudios.ringbearer.harness;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Common settings (Mods > Config). Tuning per animal is data: {@code data/<ns>/draught/*.json}. */
public final class HarnessConfig {
    public static final ModConfigSpec SPEC;
    /** Turns all pulling off (animals stay hitched and are still held back). */
    public static final ModConfigSpec.BooleanValue PULLING;
    /** The pulling assist ("cheated" physics, 2026-11-25): see the three settings below. */
    public static final ModConfigSpec.BooleanValue ASSIST;
    /** With the assist, a team can always give its vehicle at least this acceleration (blocks/s²), however heavy. */
    public static final ModConfigSpec.DoubleValue ASSIST_ACCEL;
    /** With the assist, how fast the hitch is brought to the animal's speed along the traces (1/s). */
    public static final ModConfigSpec.DoubleValue ASSIST_MATCH;
    /** With the assist, how fast a hitched vehicle's pitch and roll spin dies away (1/s). */
    public static final ModConfigSpec.DoubleValue ASSIST_STEADY;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        PULLING = b.comment("Hitched animals pull their vehicles. Off: they stay hitched but the vehicle isn't pulled.")
                .translation("ringbearer_harness.configuration.pulling")
                .define("pulling", true);
        ASSIST = b.comment("Pulling assist: teams pull heavy vehicles reliably (strength grows with the load, the "
                        + "vehicle is matched to the animal's speed, its rocking is damped). Off: plain physics.")
                .translation("ringbearer_harness.configuration.assist")
                .define("assist", true);
        ASSIST_ACCEL = b.comment("Assist: the least acceleration (blocks/s²) a team can give its vehicle, however heavy.")
                .translation("ringbearer_harness.configuration.assist_accel")
                .defineInRange("assist_accel", 4.0, 0.0, 50.0);
        ASSIST_MATCH = b.comment("Assist: how fast the vehicle is brought to the animal's speed along the traces (1/s).")
                .translation("ringbearer_harness.configuration.assist_match")
                .defineInRange("assist_match", 1.5, 0.0, 20.0);
        ASSIST_STEADY = b.comment("Assist: how fast a hitched vehicle's pitch and roll rocking dies away (1/s).")
                .translation("ringbearer_harness.configuration.assist_steady")
                .defineInRange("assist_steady", 3.0, 0.0, 40.0);
        SPEC = b.build();
    }

    private HarnessConfig() {}
}
