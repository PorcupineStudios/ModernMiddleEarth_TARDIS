package com.porcupinestudios.tardisrb;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server config. Read values through these fields so changes apply live. */
public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    /** Whether one player may own more than one TARDIS. Players can always share through crew lists. */
    public static final ModConfigSpec.BooleanValue ALLOW_MULTIPLE_PER_PLAYER = BUILDER
            .comment("Whether one player may own more than one TARDIS. Crews can always share.")
            .define("allowMultipleTardisPerPlayer", false);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {}
}
