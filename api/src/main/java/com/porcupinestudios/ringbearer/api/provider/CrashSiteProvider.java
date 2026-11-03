package com.porcupinestudios.ringbearer.api.provider;

import java.util.Optional;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

/**
 * Says which dimension a world starts in, so the TARDIS wreck lands at that dimension's spawn. The LOTR port answers
 * Middle-earth for a Middle-earth world; with no answer the start is the Overworld.
 */
@FunctionalInterface
public interface CrashSiteProvider {
    Optional<ResourceKey<Level>> startDimension(MinecraftServer server);
}
