package com.porcupinestudios.ringbearer.harness;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Common settings (Mods > Config). Tuning per animal is data: {@code data/<ns>/draught/*.json}. */
public final class HarnessConfig {
    public static final ModConfigSpec SPEC;
    /** Turns all pulling off (animals stay hitched and are still held back). */
    public static final ModConfigSpec.BooleanValue PULLING;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        PULLING = b.comment("Hitched animals pull their vehicles. Off: they stay hitched but the vehicle isn't pulled.")
                .translation("ringbearer_harness.configuration.pulling")
                .define("pulling", true);
        SPEC = b.build();
    }

    private HarnessConfig() {}
}
