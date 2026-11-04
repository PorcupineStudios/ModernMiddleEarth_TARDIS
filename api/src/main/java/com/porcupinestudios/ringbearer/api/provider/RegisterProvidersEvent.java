package com.porcupinestudios.ringbearer.api.provider;

import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

/** Fired once on the mod bus during common setup. Mods add their providers here. */
public final class RegisterProvidersEvent extends Event implements IModBusEvent {
    private final Providers.Builder builder;

    RegisterProvidersEvent(Providers.Builder builder) {
        this.builder = builder;
    }

    public void crashSite(CrashSiteProvider provider) {
        builder.crashSites.add(provider);
    }

    public void researchers(ResearcherProvider provider) {
        builder.researchers.add(provider);
    }

    public void planet(PlanetProvider provider) {
        builder.planets.add(provider);
    }
}
