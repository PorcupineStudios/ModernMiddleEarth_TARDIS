package com.porcupinestudios.lotrport.colonist;

import com.porcupinestudios.lotrport.colony.ColonyData;
import com.porcupinestudios.lotrport.npc.LotrNpc;
import com.porcupinestudios.lotrport.npc.People;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Random;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/**
 * Colonists as people with traits, needs, a mood and kin: the glue between the plain rules of this package and the
 * person records in {@link People}. So far a settler gets their parts on joining a colony and their mood can be
 * read; nothing fills needs or acts on mood yet (see the plan's "Colonists" section).
 */
public final class Colonists {
    private Colonists() {}

    /**
     * Gives a newly settled NPC their traits, needs, mood and kin, once. The roll is seeded by the NPC's id, so the
     * same settler always turns out the same (pasts rebuild exactly). Needs a person record ({@link People#settled}).
     */
    public static void settle(MinecraftServer server, LotrNpc npc) {
        People people = People.get(server);
        UUID id = npc.getUUID();
        if (people.get(id).isEmpty() || people.part(id, ColonistData.TRAITS).isPresent()) {
            return;
        }
        ColonistSettings settings = ColonistData.settings();
        long now = server.overworld().getGameTime();
        int dayTicks = dayTicks();
        Random random = new Random(id.getMostSignificantBits() ^ id.getLeastSignificantBits());
        people.setPart(id, ColonistData.TRAITS, TraitRules.roll(ColonistData.traits(), random, settings));
        people.setPart(id, ColonistData.NEEDS, NeedRules.start(ColonistData.needs(), now));
        people.setPart(id, ColonistData.MOOD, Mood.EMPTY);
        ResourceLocation raceId = RaceRules.raceOf(ColonistData.races(), npc.typeId(), settings.defaultRace());
        RaceDef race = ColonistData.races().get(raceId);
        long born = now;
        if (race != null) {
            int span = Math.max(0, race.settlerAgeMax() - race.settlerAgeMin());
            born = KinRules.bornFor(race.settlerAgeMin() + random.nextInt(span + 1), race, now, dayTicks);
        }
        people.setPart(id, ColonistData.KIN, new Kin(raceId, born, List.of(), Optional.empty(), List.of()));
    }

    private static int dayTicks() {
        return Math.max(1, ColonyData.settings().dayTicks());
    }

    /** How fast each need falls for this person: their race's rate times their traits'. */
    public static Map<ResourceLocation, Double> needRates(MinecraftServer server, UUID person) {
        People people = People.get(server);
        List<ResourceLocation> traits = people.part(person, ColonistData.TRAITS).orElse(List.of());
        RaceDef race = people.part(person, ColonistData.KIN).map(k -> ColonistData.races().get(k.race())).orElse(null);
        Map<ResourceLocation, Double> rates = new HashMap<>();
        for (ResourceLocation need : ColonistData.needs().keySet()) {
            double rate = TraitRules.needRate(ColonistData.traits(), traits, need);
            if (race != null) {
                rate *= race.needRates().getOrDefault(need, 1.0);
            }
            rates.put(need, rate);
        }
        return rates;
    }

    /** The thoughts that hold for this person right now from their traits and need levels. */
    public static List<ResourceLocation> standingThoughts(MinecraftServer server, UUID person) {
        People people = People.get(server);
        List<ResourceLocation> out = new ArrayList<>(TraitRules.thoughts(ColonistData.traits(),
                people.part(person, ColonistData.TRAITS).orElse(List.of())));
        people.part(person, ColonistData.NEEDS).ifPresent(needs -> out.addAll(NeedRules.thoughts(
                ColonistData.needs(), needs, needRates(server, person), server.overworld().getGameTime(),
                dayTicks())));
        return out;
    }

    /** The person's mood score (0-100), if they are a colonist with parts. */
    public static OptionalInt mood(MinecraftServer server, UUID person) {
        People people = People.get(server);
        Optional<Mood> mood = people.part(person, ColonistData.MOOD);
        if (mood.isEmpty()) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(MoodRules.score(ColonistData.settings(), ColonistData.thoughts(), mood.get(),
                standingThoughts(server, person), server.overworld().getGameTime()));
    }

    /** Something happened to this person that they will remember for a while (a wedding, a death). */
    public static void remember(MinecraftServer server, UUID person, ResourceLocation thought) {
        People people = People.get(server);
        people.part(person, ColonistData.MOOD).ifPresent(mood -> people.setPart(person, ColonistData.MOOD,
                MoodRules.remember(mood, ColonistData.thoughts(), thought, server.overworld().getGameTime())));
    }

    /**
     * " [strong, lazy]" for lists of citizens; empty for someone with no traits. Mood is left out until something
     * fills needs: bars only fall for now, so the number would mislead.
     */
    public static Component summary(MinecraftServer server, UUID person) {
        List<ResourceLocation> traits = People.get(server).part(person, ColonistData.TRAITS).orElse(List.of());
        if (traits.isEmpty()) {
            return Component.empty();
        }
        MutableComponent out = Component.literal(" [");
        for (int i = 0; i < traits.size(); i++) {
            ResourceLocation t = traits.get(i);
            if (i > 0) {
                out.append(", ");
            }
            out.append(Component.translatableWithFallback("trait." + t.getNamespace() + "." + t.getPath(),
                    t.getPath().replace('_', ' ')));
        }
        return out.append("]");
    }
}
