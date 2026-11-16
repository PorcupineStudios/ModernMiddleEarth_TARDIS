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
 * @param spouse their spouse now
 * @param former spouses they were married to before (divorced, or widowed and remarried), oldest first
 */
public record Kin(ResourceLocation race, long born, List<UUID> parents, Optional<UUID> spouse, List<UUID> children,
        List<UUID> former) {
    public Kin {
        parents = List.copyOf(parents);
        children = List.copyOf(children);
        former = List.copyOf(former);
    }

    public Kin(ResourceLocation race, long born, List<UUID> parents, Optional<UUID> spouse, List<UUID> children) {
        this(race, born, parents, spouse, children, List.of());
    }

    public Kin withSpouse(Optional<UUID> newSpouse) {
        return new Kin(race, born, parents, newSpouse, children, former);
    }

    public Kin withParents(List<UUID> newParents) {
        return new Kin(race, born, newParents, spouse, children, former);
    }

    public Kin withChild(UUID child) {
        if (children.contains(child)) {
            return this;
        }
        List<UUID> more = new java.util.ArrayList<>(children);
        more.add(child);
        return new Kin(race, born, parents, spouse, more, former);
    }

    /** Married to {@code partner} now; a spouse they had before goes into {@code former}. */
    public Kin marry(UUID partner) {
        if (spouse.isPresent() && spouse.get().equals(partner)) {
            return this;
        }
        List<UUID> was = new java.util.ArrayList<>(former);
        spouse.filter(s -> !was.contains(s)).ifPresent(was::add);
        was.remove(partner);
        return new Kin(race, born, parents, Optional.of(partner), children, was);
    }

    /** No longer married to {@code partner}: they become a former spouse. */
    public Kin divorce(UUID partner) {
        if (spouse.isEmpty() || !spouse.get().equals(partner)) {
            return this;
        }
        List<UUID> was = new java.util.ArrayList<>(former);
        if (!was.contains(partner)) {
            was.add(partner);
        }
        return new Kin(race, born, parents, Optional.empty(), children, was);
    }
}
