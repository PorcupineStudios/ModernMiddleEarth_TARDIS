package com.porcupinestudios.ringbearer.api;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server settings of the shared API. Shown in the mods list config screen and applied live. */
public final class ApiConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue RECORD_BLOCK_CHANGES = BUILDER
            .comment("Record blocks placed and broken by entities in the world history log.",
                    "Time travel rebuilds the past from this log, so turning it off leaves gaps in the past.")
            .define("history.recordBlockChanges", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private ApiConfig() {}
}
