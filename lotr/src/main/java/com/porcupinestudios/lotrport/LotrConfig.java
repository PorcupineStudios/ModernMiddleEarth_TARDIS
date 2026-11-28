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

    public static final ModConfigSpec.IntValue DAY_LENGTH = BUILDER
            .comment("Ticks in a Middle-earth day (LOTR: 48000, twice vanilla's 24000). Only when Middle-earth is the",
                    "world type: as a dimension it shares the overworld's clock. /lotr time reads and sets the time.")
            .defineInRange("time.dayLength", 48000, 1200, 1_000_000);

    public static final ModConfigSpec.DoubleValue NPC_SPAWN_DAMPING = BUILDER
            .comment("LOTR's spawn damping for NPCs: each extra player near a player cuts how many NPCs may be around",
                    "them by this share (at most 0.5 + this / 2 in all). 0 = off, as LOTR's default.",
                    "/lotr spawndamping shows and sets it.")
            .defineInRange("npc.spawnDamping", 0.0, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue ANIMAL_SPAWN_DAMPING = BUILDER
            .comment("The same for LOTR's animals.")
            .defineInRange("npc.animalSpawnDamping", 0.0, 0.0, 1.0);

    public static final ModConfigSpec.BooleanValue LOTR_RESPAWN = BUILDER
            .comment("LOTR's respawning after a death in Middle-earth: at your bed if it is within respawn.bedRange of",
                    "where you died; else at the world spawn if that is within respawn.spawnRange; else somewhere",
                    "between respawn.randomMin and respawn.randomMax blocks from where you died.")
            .define("respawn.lotrRules", true);

    public static final ModConfigSpec.IntValue RESPAWN_BED_RANGE = BUILDER
            .defineInRange("respawn.bedRange", 5000, 0, 1_000_000);

    public static final ModConfigSpec.IntValue RESPAWN_SPAWN_RANGE = BUILDER
            .defineInRange("respawn.spawnRange", 2000, 0, 1_000_000);

    public static final ModConfigSpec.IntValue RESPAWN_RANDOM_MIN = BUILDER
            .defineInRange("respawn.randomMin", 500, 0, 100_000);

    public static final ModConfigSpec.IntValue RESPAWN_RANDOM_MAX = BUILDER
            .defineInRange("respawn.randomMax", 1500, 1, 100_000);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private LotrConfig() {}
}
