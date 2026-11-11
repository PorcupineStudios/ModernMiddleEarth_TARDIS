package com.porcupinestudios.lotrport.colonist;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/**
 * What the colony's managers told this colonist (a person part, set from the citizen page). Without it, or with a
 * field left empty, the colony decides for them as it always has.
 *
 * @param job the workplace they must work at; empty: the colony picks ({@code colony.work.Assign})
 * @param home the home they must sleep in; empty: the colony picks ({@link HouseRules})
 * @param works false: they take no job at all and rest
 * @param ration their ration ({@code colonist_ration/<id>.json}): how fast they eat and how they feel about it
 */
public record Orders(Optional<UUID> job, Optional<UUID> home, boolean works, ResourceLocation ration) {
    public static final Orders DEFAULT = new Orders(Optional.empty(), Optional.empty(), true, Rations.NORMAL);

    public Orders withJob(Optional<UUID> job) {
        return new Orders(job, home, works, ration);
    }

    public Orders withHome(Optional<UUID> home) {
        return new Orders(job, home, works, ration);
    }

    public Orders withWorks(boolean works) {
        return new Orders(job, home, works, ration);
    }

    public Orders withRation(ResourceLocation ration) {
        return new Orders(job, home, works, ration);
    }

    public boolean isDefault() {
        return equals(DEFAULT);
    }
}
