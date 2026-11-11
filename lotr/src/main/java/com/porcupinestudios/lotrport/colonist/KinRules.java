package com.porcupinestudios.lotrport.colonist;

import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;

/**
 * Ages, households and what a colonist thinks of who they live with. Plain rules: data in, data out.
 *
 * <p>A household is the people who count as one family for housing. For most races it is a couple and their
 * children who have not come of age; a race with {@code extendedHousehold} (hobbits) keeps grown children and
 * grandparents in it. Sharing a house with one's own household is never a penalty.
 */
public final class KinRules {
    /** How far up the family tree a household is followed (a guard against bad data). */
    private static final int MAX_DEPTH = 16;

    private KinRules() {}

    /** Age in years. */
    public static double age(Kin kin, RaceDef race, long now, int dayTicks) {
        double days = Math.max(0, now - kin.born()) / (double) Math.max(1, dayTicks);
        return days / Math.max(0.0001, race.daysPerYear());
    }

    public static boolean adult(Kin kin, RaceDef race, long now, int dayTicks) {
        return age(kin, race, now, dayTicks) >= race.adultAge();
    }

    /** The birth tick of someone who is {@code years} old now. */
    public static long bornFor(double years, RaceDef race, long now, int dayTicks) {
        return now - Math.round(years * race.daysPerYear() * dayTicks);
    }

    /**
     * The id that names a person's household: two people are of one household exactly when this is equal.
     *
     * @param kin the kin of a person who lives in this colony and is alive; empty for anyone else, so the dead and
     *     those who moved away don't hold a household together
     * @param races race definitions by id
     */
    public static UUID household(UUID person, Function<UUID, Optional<Kin>> kin,
            Function<ResourceLocation, Optional<RaceDef>> races, long now, int dayTicks) {
        UUID at = person;
        for (int depth = 0; depth < MAX_DEPTH; depth++) {
            Optional<Kin> k = kin.apply(at);
            if (k.isEmpty()) {
                return at;
            }
            Optional<RaceDef> race = races.apply(k.get().race());
            boolean adult = race.map(r -> adult(k.get(), r, now, dayTicks)).orElse(true);
            boolean staysWithParents = !adult || race.map(RaceDef::extendedHousehold).orElse(false);
            Optional<UUID> parent = staysWithParents ? presentParent(k.get(), kin) : Optional.empty();
            if (parent.isEmpty() && staysWithParents && adult) {
                // an extended household also follows a spouse into their parents' home
                Optional<UUID> spouse = k.get().spouse().filter(s -> kin.apply(s).isPresent());
                parent = spouse.flatMap(s -> presentParent(kin.apply(s).get(), kin));
            }
            if (parent.isEmpty()) {
                return couple(at, k.get(), kin);
            }
            at = parent.get();
        }
        return at;
    }

    private static Optional<UUID> presentParent(Kin k, Function<UUID, Optional<Kin>> kin) {
        return k.parents().stream().filter(p -> kin.apply(p).isPresent()).sorted().findFirst();
    }

    /** One id for a couple, whichever of the two is asked. */
    private static UUID couple(UUID person, Kin k, Function<UUID, Optional<Kin>> kin) {
        Optional<UUID> spouse = k.spouse().filter(s -> kin.apply(s).isPresent());
        return spouse.isPresent() && spouse.get().compareTo(person) < 0 ? spouse.get() : person;
    }

    /**
     * What a colonist thinks of who they live with, or empty for none (the homeless already miss the colony's home
     * bonus). A family (a household of two or more) minds sharing far more than someone single, so the settings have
     * a pair of thoughts for each.
     *
     * @param myHousehold the colonist's household id
     * @param householdSize how many of the colonist's household live in the colony
     * @param housemates the household id of everyone who sleeps in the same house, the colonist included; empty if
     *     they have no home
     */
    public static Optional<ResourceLocation> homeThought(ColonistSettings settings, UUID myHousehold,
            int householdSize, Collection<UUID> housemates) {
        if (housemates.isEmpty()) {
            return Optional.empty();
        }
        Set<UUID> others = new HashSet<>(housemates);
        others.remove(myHousehold);
        boolean family = householdSize >= 2;
        if (others.isEmpty()) {
            return Optional.of(family ? settings.familyHome() : settings.ownHome());
        }
        if (others.size() >= settings.barracksHouseholds()) {
            return Optional.of(family ? settings.familyBarracks() : settings.barracks());
        }
        return Optional.of(family ? settings.familyShared() : settings.sharedHome());
    }
}
