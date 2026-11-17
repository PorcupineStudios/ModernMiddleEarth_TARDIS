package com.porcupinestudios.lotrport;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server settings for server owners. Balance numbers belong in data files, not here. A SERVER config is synced to
 * clients when they join, so the client reads the same combat switch as the server.
 */
public final class LotrConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue RECORD_ALIGNMENT_HISTORY = BUILDER
            .comment("Write every alignment change to the world history log (used by time travel).")
            .define("history.recordAlignment", true);

    public static final ModConfigSpec.BooleanValue FORCED_ATTACK_WAIT = BUILDER
            .comment("LOTR's melee timing in place of vanilla's attack cooldown. A player can't hit a living thing",
                    "again until the held weapon's wait (20 / its attack speed, in ticks) is over: the click does",
                    "nothing, as in LOTR. Every hit that does land is a full-strength hit (no weak spam hits).",
                    "Off: vanilla's cooldown, where early hits land but are weak.")
            .define("combat.forcedAttackWait", true);

    public static final ModConfigSpec.IntValue ATTACK_WAIT_GRACE = BUILDER
            .comment("Ticks the server lets a hit arrive early, so network jitter doesn't throw away a hit the",
                    "client timed correctly. 0 is strict.")
            .defineInRange("combat.attackWaitGraceTicks", 1, 0, 5);

    public static final ModConfigSpec.BooleanValue FEAST_MODE = BUILDER
            .comment("LOTR's feast mode: food can be eaten even with a full hunger bar (its saturation still counts).",
                    "Off: vanilla, where only always-edible food (golden apples...) can be eaten when full.")
            .define("food.feastMode", true);

    public static final ModConfigSpec.BooleanValue ORC_SKIRMISHES = BUILDER
            .comment("LOTR's orc skirmishes: now and then an orc picks a fight with a nearby orc, and others join in.")
            .define("npc.orcSkirmishes", true);

    public static final ModConfigSpec.BooleanValue RACE_PICKER = BUILDER
            .comment("Ask each player to choose a race (data/<ns>/player_race/*.json) the first time they are in",
                    "Middle-earth. /lotr_race opens the picker by hand.")
            .define("race.picker", true);

    public static final ModConfigSpec.BooleanValue RACE_CHANGE = BUILDER
            .comment("Players may choose again with /lotr_race after they have a race (operators always may).")
            .define("race.allowChange", false);

    public static final ModConfigSpec.BooleanValue POISON_SHOWN_TO_ALL = BUILDER
            .comment("A poisoned drink says \"Poisoned!\" (in the hand and on Jade) to everyone, not only to whoever",
                    "poisoned it (LOTR: only the poisoner)")
            .define("drinks.poisonShownToAll", false);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private LotrConfig() {}
}
