package com.porcupinestudios.lotrport.society.sim;

import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * One piece of colony behaviour (migration, pay, needs, production...). The scheduler runs it for each colony on its
 * {@link Cadence}, in the tiers it names, after the systems it names in {@link #after()}. A system keeps its own
 * state in components; it never calls another system (post an event instead). Register with
 * {@link ColonySystems#register} while the mod loads; {@code colony_systems/settings.json} can switch one off.
 */
public interface ColonySystem {
    /** How often a system runs for a colony. */
    enum Cadence {
        /** Every colony pulse ({@code pulse_ticks}, staggered across colonies). */
        PULSE,
        /** Once a colony day; {@link SystemContext#days()} says how many passed (catch-up for unloaded colonies). */
        DAY
    }

    ResourceLocation id();

    Cadence cadence();

    /** Tiers it runs in; an in-world system leaves unloaded colonies to an arithmetic one. */
    Set<Tier> tiers();

    /** Systems that must run before this one in the same pass (unknown ids are ignored). */
    default Set<ResourceLocation> after() {
        return Set.of();
    }

    void run(SystemContext ctx);

    /** A system from parts, for the simple cases. */
    static ColonySystem of(ResourceLocation id, Cadence cadence, Set<Tier> tiers, Set<ResourceLocation> after,
            java.util.function.Consumer<SystemContext> body) {
        return new ColonySystem() {
            @Override
            public ResourceLocation id() {
                return id;
            }

            @Override
            public Cadence cadence() {
                return cadence;
            }

            @Override
            public Set<Tier> tiers() {
                return tiers;
            }

            @Override
            public Set<ResourceLocation> after() {
                return after;
            }

            @Override
            public void run(SystemContext ctx) {
                body.accept(ctx);
            }

            @Override
            public String toString() {
                return id.toString();
            }
        };
    }
}
