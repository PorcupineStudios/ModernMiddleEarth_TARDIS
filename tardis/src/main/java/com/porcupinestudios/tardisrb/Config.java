package com.porcupinestudios.tardisrb;

import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Server config. Read values through these fields so changes apply live. */
public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    /** Whether one player may own more than one TARDIS. Players can always share through crew lists. */
    public static final ModConfigSpec.BooleanValue ALLOW_MULTIPLE_PER_PLAYER = BUILDER
            .comment("Whether one player may own more than one TARDIS. Crews can always share.")
            .define("allowMultipleTardisPerPlayer", false);

    /** Feature ids switched on for every TARDIS, whatever the datapack says. */
    public static final ModConfigSpec.ConfigValue<List<? extends String>> FORCED_ON = BUILDER
            .comment("Feature ids (tardis/feature files) switched on for every TARDIS, ignoring their requirements.")
            .defineListAllowEmpty("forcedOnFeatures", List.of(), () -> "", o -> o instanceof String);

    /** Feature ids switched off for every TARDIS; wins over FORCED_ON. */
    public static final ModConfigSpec.ConfigValue<List<? extends String>> FORCED_OFF = BUILDER
            .comment("Feature ids switched off for every TARDIS. Wins over forcedOnFeatures.")
            .defineListAllowEmpty("forcedOffFeatures", List.of(), () -> "", o -> o instanceof String);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {}
}
