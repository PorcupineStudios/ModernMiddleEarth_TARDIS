package com.porcupinestudios.ringbearer.api.condition;

/**
 * A yes/no question about the world, written in data files as {@code {"type": "<condition type id>", ...}}. TARDIS
 * unlocks, quests, story events and colony research all use the same conditions, so a new type works everywhere.
 */
public interface Condition {
    ConditionType<?> type();

    boolean test(ConditionContext context);
}
