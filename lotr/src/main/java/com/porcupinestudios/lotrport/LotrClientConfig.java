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

    public static final ModConfigSpec.BooleanValue MAP_BORDERS = BUILDER
            .comment("Show borders (who holds the land, kingdoms) on the map at first; B toggles them on the map.")
            .define("map.borders", true);

    public static final ModConfigSpec.BooleanValue RACE_SKINS = BUILDER
            .comment("Draw players with their race's skin (player_race files) instead of their own.")
            .define("race.skins", true);

    public static final ModConfigSpec.BooleanValue RACE_OWN_SKINS = BUILDER
            .comment("Races with own_skin draw each player's own skin, converted to the race body (hair, hobbit feet,",
                    "elf ears), instead of the race's skin.")
            .define("race.ownSkins", true);

    public static final ModConfigSpec.BooleanValue ATMOSPHERE = BUILDER
            .comment("Region looks (fog and colour filters per biome, assets/<ns>/atmosphere/*.json).")
            .define("atmosphere.enabled", true);

    public static final ModConfigSpec.BooleanValue ATMOSPHERE_FOG = BUILDER
            .comment("Region fog distance and colour.")
            .define("atmosphere.fog", true);

    public static final ModConfigSpec.BooleanValue ATMOSPHERE_FILTER = BUILDER
            .comment("Region colour filters over the screen.")
            .define("atmosphere.filters", true);

    public static final ModConfigSpec.DoubleValue ATMOSPHERE_STRENGTH = BUILDER
            .comment("Scale for all region looks: 0.5 = half as strong, 2 = twice.")
            .defineInRange("atmosphere.strength", 1.0, 0.0, 2.0);

    public static final ModConfigSpec.BooleanValue ATMOSPHERE_GRADE = BUILDER
            .comment("Region colour grading (saturation, contrast, warmth, vignette, glow) as a post-process shader.")
            .define("atmosphere.grading", true);

    public static final ModConfigSpec.BooleanValue ATMOSPHERE_REDUCE_MOTION = BUILDER
            .comment("Reduce motion: no shimmer, film grain or pulsing in region looks and the Ring's look.")
            .define("atmosphere.reduceMotion", false);

    public static final ModConfigSpec.DoubleValue ATMOSPHERE_GRAIN = BUILDER
            .comment("Scale for film grain in region looks and the Ring's look: 0 = none, 2 = twice.")
            .defineInRange("atmosphere.grain", 1.0, 0.0, 2.0);

    public static final ModConfigSpec.BooleanValue ATMOSPHERE_OVER_SHADER_PACKS = BUILDER
            .comment("With an Iris shader pack on, still draw the region looks and the Ring's look on top of it.")
            .define("atmosphere.overShaderPacks", true);

    public static final ModConfigSpec.DoubleValue ATMOSPHERE_SHADER_PACK_GRADING = BUILDER
            .comment("With an Iris shader pack on, scale for region colour grading (packs grade colour themselves).",
                    "The Ring's look is not scaled by this.")
            .defineInRange("atmosphere.shaderPackGrading", 0.5, 0.0, 1.0);

    public static final ModConfigSpec.BooleanValue RING_LOOK = BUILDER
            .comment("The wraith-world look while you wear a ring of power (assets/<ns>/ring_look/*.json).")
            .define("ring.look", true);

    public static final ModConfigSpec.DoubleValue RING_STRENGTH = BUILDER
            .comment("Scale for the Ring's look: 0.5 = half as strong, 2 = twice.")
            .defineInRange("ring.strength", 1.0, 0.0, 2.0);

    public static final ModConfigSpec.BooleanValue SKY = BUILDER
            .comment("Middle-earth's own sky: LOTR's sun, moon, night sky and Earendil.")
            .define("sky.enabled", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private LotrClientConfig() {}
}
