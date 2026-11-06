package com.porcupinestudios.lotrport;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Each player's own screen settings (LOTR's client options for the alignment bar, compass and attack meter). */
public final class LotrClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ALWAYS_SHOW_ALIGNMENT = BUILDER
            .comment("Show the alignment bar (and compass) outside Middle-earth too.")
            .define("hud.alwaysShowAlignment", false);

    public static final ModConfigSpec.IntValue ALIGNMENT_X_OFFSET = BUILDER
            .comment("Move the alignment bar sideways from the top centre of the screen (GUI pixels).")
            .defineInRange("hud.alignmentXOffset", 0, -2000, 2000);

    public static final ModConfigSpec.IntValue ALIGNMENT_Y_OFFSET = BUILDER
            .comment("Move the alignment bar down from the top of the screen (GUI pixels).")
            .defineInRange("hud.alignmentYOffset", 0, -2000, 2000);

    public static final ModConfigSpec.BooleanValue ONSCREEN_COMPASS = BUILDER
            .comment("LOTR's on-screen compass in the top right corner while in Middle-earth.")
            .define("hud.onscreenCompass", true);

    public static final ModConfigSpec.BooleanValue COMPASS_EXTRA_INFO = BUILDER
            .comment("Show the biome above the compass and your coordinates below it.")
            .define("hud.compassExtraInfo", true);

    public static final ModConfigSpec.BooleanValue ATTACK_METER = BUILDER
            .comment("LOTR's attack meter (bottom right) while your melee wait runs.")
            .define("hud.attackMeter", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private LotrClientConfig() {}
}
