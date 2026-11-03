package com.porcupinestudios.lotrport;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server settings for server owners. Balance numbers belong in data files, not here. */
public final class LotrConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue RECORD_ALIGNMENT_HISTORY = BUILDER
            .comment("Write every alignment change to the world history log (used by time travel).")
            .define("history.recordAlignment", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private LotrConfig() {}
}
