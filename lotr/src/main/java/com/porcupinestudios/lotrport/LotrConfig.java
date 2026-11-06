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

    public static final ModConfigSpec SPEC = BUILDER.build();

    private LotrConfig() {}
}
