package com.porcupinestudios.ringbearer.api.condition;

import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * What a condition is asked about. Mods that need more (a TARDIS, a colony) look it up from the level and player,
 * or extend this through {@link #extra}.
 *
 * @param extra anything else the asker passes, keyed by the asker's mod id (for example a TARDIS or colony id)
 */
public record ConditionContext(ServerLevel level, Optional<ServerPlayer> player, java.util.Map<String, Object> extra) {
    public static ConditionContext of(ServerLevel level) {
        return new ConditionContext(level, Optional.empty(), java.util.Map.of());
    }

    public static ConditionContext of(ServerPlayer player) {
        return new ConditionContext(player.serverLevel(), Optional.of(player), java.util.Map.of());
    }
}
