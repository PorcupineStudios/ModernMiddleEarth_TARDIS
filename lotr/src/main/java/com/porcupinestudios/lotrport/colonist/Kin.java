package com.porcupinestudios.lotrport.colonist;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/**
 * A colonist's place in the family tree (a person part). Ids are person ids; the dead stay listed, since person
 * records are kept after death.
 *
 * @param race the race definition that sets how they age and who they live with
 * @param born game time of birth (before the world began for a settler who arrived grown)
 */
public record Kin(ResourceLocation race, long born, List<UUID> parents, Optional<UUID> spouse, List<UUID> children) {
    public Kin {
        parents = List.copyOf(parents);
        children = List.copyOf(children);
    }

    public Kin withSpouse(Optional<UUID> newSpouse) {
        return new Kin(race, born, parents, newSpouse, children);
    }

    public Kin withChild(UUID child) {
        if (children.contains(child)) {
            return this;
        }
        List<UUID> more = new java.util.ArrayList<>(children);
        more.add(child);
        return new Kin(race, born, parents, spouse, more);
    }
}
