package com.porcupinestudios.ringbearer.api.provider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoader;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/** Everything mods registered through {@link RegisterProvidersEvent}, and the questions answered from it. */
public final class Providers {
    private static List<CrashSiteProvider> crashSites = List.of();
    private static List<ResearcherProvider> researchers = List.of();

    private Providers() {}

    public static void register(IEventBus modBus) {
        modBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(() -> {
            Builder builder = new Builder();
            ModLoader.postEvent(new RegisterProvidersEvent(builder));
            crashSites = List.copyOf(builder.crashSites);
            researchers = List.copyOf(builder.researchers);
        }));
    }

    /** The dimension this world starts in: the first provider with an answer, else the Overworld. */
    public static ResourceKey<Level> startDimension(MinecraftServer server) {
        for (CrashSiteProvider provider : crashSites) {
            Optional<ResourceKey<Level>> dim = provider.startDimension(server);
            if (dim.isPresent()) {
                return dim.get();
            }
        }
        return Level.OVERWORLD;
    }

    /** Researchers working for any of these players, added up over every provider (each player counted once). */
    public static int researchers(MinecraftServer server, Collection<UUID> players) {
        int total = 0;
        for (UUID player : new LinkedHashSet<>(players)) {
            for (ResearcherProvider provider : researchers) {
                total += Math.max(0, provider.researchers(server, player));
            }
        }
        return total;
    }

    static final class Builder {
        final List<CrashSiteProvider> crashSites = new ArrayList<>();
        final List<ResearcherProvider> researchers = new ArrayList<>();
    }
}
