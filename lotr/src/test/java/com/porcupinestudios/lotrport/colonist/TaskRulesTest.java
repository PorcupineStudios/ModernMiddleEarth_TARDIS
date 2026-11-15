package com.porcupinestudios.lotrport.colonist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** The task queue: needs win only when low, never in work hours; work needs a job and wages paid. */
class TaskRulesTest {
    private static ResourceLocation id(String s) {
        return ResourceLocation.fromNamespaceAndPath("lotr_port", s);
    }

    private static final Map<ResourceLocation, TaskDef> DEFS = Map.of(
            id("sleep"), new TaskDef("sleep", "home", Map.of("sleep", 8, "any", 3, "leisure", 2),
                    Optional.of("energy"), 60, List.of("sleep"), 25, 10, List.of()),
            id("eat"), new TaskDef("eat", "food", Map.of("leisure", 6, "any", 6, "sleep", 4), Optional.of("food"),
                    14, List.of(), 5, 10, List.of("food_place")),
            id("work"), new TaskDef("work", "workplace", Map.of("work", 5, "any", 2), Optional.empty(), 1000,
                    List.of(), -1, 10, List.of("job", "works", "paid")),
            id("leisure"), new TaskDef("leisure", "hall", Map.of("leisure", 4, "any", 1), Optional.empty(), 1000,
                    List.of(), -1, 10, List.of()));
    private static final Set<String> ALL = Set.of("job", "works", "paid", "food_place", "home");

    private static ResourceLocation doing(Schedule.Block b, double energy, double food, Set<String> facts) {
        return TaskRules.current(TaskRules.queue(DEFS, new TaskRules.State(b, energy, food, facts)))
                .map(TaskRules.Entry::id).orElse(null);
    }

    @Test
    void workHoursAreForWorkWhateverTheNeeds() {
        assertEquals(id("work"), doing(Schedule.Block.WORK, 5, 2, ALL), "exhausted and starving, still no sleep or food");
        List<TaskRules.Entry> q = TaskRules.queue(DEFS, new TaskRules.State(Schedule.Block.WORK, 5, 2, ALL));
        assertTrue(q.stream().filter(e -> e.id().equals(id("sleep"))).allMatch(e -> e.waiting()
                .equals(Optional.of(TaskRules.NOT_NOW))));
    }

    @Test
    void needsWinOnlyWhenLow() {
        assertEquals(id("leisure"), doing(Schedule.Block.LEISURE, 80, 18, ALL), "rested and fed: leisure");
        assertEquals(id("eat"), doing(Schedule.Block.LEISURE, 80, 10, ALL), "hungry: eating beats leisure (6 > 4)");
        assertEquals(id("sleep"), doing(Schedule.Block.LEISURE, 20, 10, ALL), "exhausted: sleep is urgent (10)");
        assertEquals(id("eat"), doing(Schedule.Block.LEISURE, 20, 3, ALL), "starving too: a tie at 10, by id");
        assertEquals(id("sleep"), doing(Schedule.Block.SLEEP, 100, 18, ALL), "sleep hours: bed however rested");
    }

    @Test
    void requirementsSayWhyATaskWaits() {
        List<TaskRules.Entry> q = TaskRules.queue(DEFS, new TaskRules.State(Schedule.Block.WORK, 80, 18,
                Set.of("job", "works", "food_place")));
        TaskRules.Entry work = q.stream().filter(e -> e.id().equals(id("work"))).findFirst().orElseThrow();
        assertEquals(Optional.of("paid"), work.waiting(), "striking until paid");
        assertEquals(null, doing(Schedule.Block.WORK, 80, 18, Set.of()), "no job, nothing to do in work hours");
    }
}
