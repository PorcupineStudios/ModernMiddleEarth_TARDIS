package com.porcupinestudios.lotrport.society;

import com.porcupinestudios.lotrport.colony.Colonies;
import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.npc.LotrNpc;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;

/**
 * The facade every feature asks about people and colonies. Callers never reach into {@link Persons} indexes or
 * colony fields themselves, so the storage behind these answers can change (or move to a database, or be sharded
 * per dimension) without touching them.
 */
public final class Society {
    private Society() {}

    public static Persons persons(MinecraftServer server) {
        return Persons.get(server);
    }

    public static Colonies colonies(MinecraftServer server) {
        return Colonies.get(server);
    }

    public static Optional<Person> person(MinecraftServer server, UUID id) {
        return persons(server).get(id);
    }

    /** Living citizens of a colony. */
    public static List<Person> citizens(MinecraftServer server, Colony colony) {
        return persons(server).ownedBy(colony.asOwner(), CoreComponents.CITIZEN);
    }

    public static int citizenCount(MinecraftServer server, Colony colony) {
        return persons(server).count(colony.asOwner(), CoreComponents.CITIZEN);
    }

    /** Migrants waiting at a colony's Hall. */
    public static List<Person> applicants(MinecraftServer server, Colony colony) {
        return persons(server).ownedBy(colony.asOwner(), CoreComponents.APPLICANT);
    }

    /** Soldiers of a colony: Persons it owns that carry a {@link CoreComponents#UNIT}. */
    public static List<Person> soldiers(MinecraftServer server, Colony colony) {
        return persons(server).ownedBy(colony.asOwner(), CoreComponents.UNIT);
    }

    /** Units a player hired (and still owns). */
    public static List<Person> unitsOf(MinecraftServer server, UUID player) {
        return persons(server).ownedBy(Owner.player(player), CoreComponents.UNIT);
    }

    /** Living citizens plus the counted people of an AI colony. */
    public static int population(MinecraftServer server, Colony colony) {
        return citizenCount(server, colony) + colony.virtualPopulation();
    }

    /** Rare citizens: those who joined by a recruitment quest. */
    public static long rareCitizens(MinecraftServer server, Colony colony) {
        return citizens(server, colony).stream()
                .filter(p -> p.get(CoreComponents.CITIZEN).map(CoreComponents.Citizen::rare).orElse(false)).count();
    }

    /** The colony that owns this Person, if any. */
    public static Optional<Colony> colonyOf(MinecraftServer server, Person person) {
        return person.owner().colonyId().flatMap(id -> colonies(server).get(id));
    }

    /** A new record for an NPC's body: same id as its entity, its type, name and faction. */
    public static Person recordFor(LotrNpc npc) {
        return Person.create(npc.getUUID(), npc.typeId(), npc.personName(), npc.faction(),
                npc.level().getGameTime());
    }

    /** The record of this body, made (owned by nobody) if it has none yet. */
    public static Person recordOf(MinecraftServer server, LotrNpc npc) {
        Persons persons = persons(server);
        return persons.get(npc.getUUID()).filter(Person::alive)
                .orElseGet(() -> persons.add(server, recordFor(npc), Optional.empty(), Optional.of(where(npc))));
    }

    /** Records a newly hired unit: owned by the player, with a {@link CoreComponents#UNIT}, history {@code npc_hired}. */
    public static Person hired(MinecraftServer server, LotrNpc npc, UUID player) {
        Person p = recordFor(npc);
        p.owner = Owner.player(player);
        p.components.set(CoreComponents.UNIT, CoreComponents.Unit.hired(server.overworld().getGameTime()));
        return persons(server).add(server, p, Optional.of(Persons.HIRED_HISTORY), Optional.of(where(npc)));
    }

    public static GlobalPos where(LotrNpc npc) {
        return GlobalPos.of(npc.level().dimension(), npc.blockPosition());
    }
}
