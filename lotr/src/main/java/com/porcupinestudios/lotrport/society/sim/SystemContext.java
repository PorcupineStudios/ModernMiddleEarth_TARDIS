package com.porcupinestudios.lotrport.society.sim;

import com.porcupinestudios.lotrport.colony.Colony;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * What a system gets for one run on one colony.
 *
 * @param level the colony's level, null if its dimension isn't loaded
 * @param now the game time of this pass
 * @param days colony days this pass covers (1, or more when an unloaded colony catches up; 0 for pulses)
 */
public record SystemContext(MinecraftServer server, Colony colony, ServerLevel level, Tier tier, long now, int days,
        Map<ResourceLocation, Integer> counts) {
    public SystemContext(MinecraftServer server, Colony colony, ServerLevel level, Tier tier, long now, int days) {
        this(server, colony, level, tier, now, days, new HashMap<>());
    }

    /** Notes a number for whoever ran the pass (tests, the perf command): arrivals, coins paid... */
    public void count(ResourceLocation what, int n) {
        counts.merge(what, n, Integer::sum);
    }

    public int counted(ResourceLocation what) {
        return counts.getOrDefault(what, 0);
    }
}
