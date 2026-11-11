package com.porcupinestudios.lotrport.colony.work;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.colonist.Colonists;
import com.porcupinestudios.lotrport.colonist.HouseRules;
import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.colony.building.Building;
import com.porcupinestudios.lotrport.colony.building.BuildingType;
import com.porcupinestudios.lotrport.colony.building.Buildings;
import com.porcupinestudios.lotrport.colony.building.ColonyBuildings;
import com.porcupinestudios.lotrport.society.CoreComponents;
import com.porcupinestudios.lotrport.society.Person;
import com.porcupinestudios.lotrport.society.Persons;
import com.porcupinestudios.lotrport.society.Society;
import com.porcupinestudios.ringbearer.api.history.HistoryEntry;
import com.porcupinestudios.ringbearer.api.history.HistoryLog;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/**
 * Homes and jobs (RingRealms' {@code ColonyLogic} homes and {@code Work.assign}). Citizens keep the home and job they
 * have while the building stands and still fits; households sleep under one roof and the homeless are housed by
 * household, families first ({@code colonist.HouseRules}); the jobless get a free
 * slot: one worker per workplace first in job priority order, then the remaining slots, the best race for the job
 * first. Soldiers never take a job. History {@code citizen_job}.
 */
public final class Assign {
    public static final ResourceLocation JOB_HISTORY = LotrPort.id("citizen_job");

    private Assign() {}

    /** One workplace and how many slots it has left. */
    record Place(Building building, ResourceLocation job, JobDef def, int slots, List<Person> workers) {}

    public static void run(MinecraftServer server, Colony colony, long now) {
        Persons persons = Persons.get(server);
        Buildings buildings = Buildings.of(colony);
        List<Person> citizens = new ArrayList<>(Society.citizens(server, colony));
        citizens.sort(Comparator.comparing(Person::id));
        homes(persons, buildings, citizens, Colonists.residents(citizens, now));
        jobs(server, persons, colony, buildings, citizens, now);
    }

    static void homes(Persons persons, Buildings buildings, List<Person> citizens,
            List<HouseRules.Resident> residents) {
        List<HouseRules.Home> homes = buildings.homes().stream()
                .map(b -> new HouseRules.Home(b.id(), b.quality(), b.count("beds"))).toList();
        Map<UUID, UUID> placed = HouseRules.assign(homes, residents);
        for (Person p : citizens) {
            UUID before = p.get(WorkComponents.HOME).map(WorkComponents.Home::building).orElse(null);
            UUID after = placed.get(p.id());
            if (!Objects.equals(before, after)) {
                persons.setQuiet(p, WorkComponents.HOME, after == null ? null : new WorkComponents.Home(after));
            }
        }
    }

    static void jobs(MinecraftServer server, Persons persons, Colony colony, Buildings buildings,
            List<Person> citizens, long now) {
        List<Place> places = new ArrayList<>();
        Map<UUID, Place> byBuilding = new HashMap<>();
        for (Building b : buildings.list()) {
            Optional<ResourceLocation> job = BuildingType.get(b.type()).flatMap(BuildingType::job);
            Optional<JobDef> def = job.flatMap(JobDef::get);
            if (def.isPresent()) {
                Place p = new Place(b, job.get(), def.get(), def.get().slots(b.quality()), new ArrayList<>());
                places.add(p);
                byBuilding.put(b.id(), p);
            }
        }
        places.sort(Comparator.comparingInt((Place p) -> p.def().priority()).thenComparing(p -> p.building().id()));
        List<Person> jobless = new ArrayList<>();
        for (Person p : citizens) {
            if (p.has(CoreComponents.UNIT)) {
                if (p.has(WorkComponents.JOB)) {
                    setJob(server, persons, colony, p, null, now);
                }
                continue;
            }
            Optional<WorkComponents.Job> job = p.get(WorkComponents.JOB);
            Place place = job.map(j -> byBuilding.get(j.building())).orElse(null);
            if (place != null && place.job().equals(job.get().job()) && place.workers().size() < place.slots()) {
                place.workers().add(p);
            } else {
                if (job.isPresent()) {
                    setJob(server, persons, colony, p, null, now);
                }
                jobless.add(p);
            }
        }
        // one worker per workplace first, then the rest of the slots
        for (int round = 0; round < 2 && !jobless.isEmpty(); round++) {
            for (Place place : places) {
                int want = round == 0 ? 1 : place.slots();
                while (place.workers().size() < want && !jobless.isEmpty()) {
                    Person best = jobless.stream().max(Comparator.comparingDouble(
                            (Person p) -> place.def().proficiency(Races.of(p.type()))).thenComparing(Person::id,
                            Comparator.reverseOrder())).orElseThrow();
                    jobless.remove(best);
                    place.workers().add(best);
                    setJob(server, persons, colony, best, new WorkComponents.Job(place.job(), place.building().id(),
                            0, now, Map.of()), now);
                }
            }
        }
    }

    private static void setJob(MinecraftServer server, Persons persons, Colony colony, Person p,
            WorkComponents.Job job, long now) {
        persons.set(server, p, WorkComponents.JOB, job);
        CompoundTag tag = new CompoundTag();
        tag.putUUID("colony", colony.id());
        tag.putUUID("person", p.id());
        if (job != null) {
            tag.putString("job", job.job().toString());
            tag.putUUID("building", job.building());
        }
        HistoryLog.get(server).append(new HistoryEntry(now, JOB_HISTORY, colony.dimension(),
                Optional.of(colony.hall()), tag));
    }

    /** A lost building frees its workers and residents at once (they find others at the next assignment). */
    public static void onLost(ColonyBuildings.Lost e) {
        Persons persons = Persons.get(e.server());
        UUID id = e.building().id();
        for (Person p : Society.citizens(e.server(), e.colony())) {
            if (p.get(WorkComponents.JOB).map(j -> j.building().equals(id)).orElse(false)) {
                setJob(e.server(), persons, e.colony(), p, null, e.server().overworld().getGameTime());
            }
            if (p.get(WorkComponents.HOME).map(h -> h.building().equals(id)).orElse(false)) {
                persons.setQuiet(p, WorkComponents.HOME, null);
            }
        }
    }
}
