package com.porcupinestudios.lotrport.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

/** The table rules the UI screens use: search, filters (any within a group, all across groups) and sorting. */
class TableQueryTest {
    private static final List<Column> COLUMNS = List.of(
            Column.text("name", Component.literal("Name"), 3),
            Column.text("race", Component.literal("Race"), 2),
            Column.text("job", Component.literal("Job"), 2),
            Column.number("happiness", Component.literal("Happiness"), 2));

    private static final List<FilterGroup> GROUPS = List.of(
            new FilterGroup("status", Component.literal("Status"), List.of(
                    new FilterGroup.Filter("homeless", Component.literal("Homeless")),
                    new FilterGroup.Filter("jobless", Component.literal("Jobless")))),
            new FilterGroup("race", Component.literal("Race"), List.of(
                    new FilterGroup.Filter("race:dwarf", Component.literal("Dwarf")),
                    new FilterGroup.Filter("race:hobbit", Component.literal("Hobbit")))));

    private static Row row(String id, String name, String race, String job, double happy, String... tags) {
        return new Row(id, List.of(Cell.text(name), Cell.text(race), Cell.text(job),
                Cell.number(happy, Long.toString(Math.round(happy)), Tone.NORMAL)), List.of(tags));
    }

    // the mockup's colony: three dwarves the filters should find, and others they shouldn't
    private static final List<Row> ROWS = List.of(
            row("1", "Khulturg", "dwarf", "Jobless", 31, "homeless", "jobless", "race:dwarf"),
            row("2", "Flói", "dwarf", "Courier", 55, "homeless", "race:dwarf"),
            row("3", "Gimrin", "dwarf", "Jobless", 40, "jobless", "race:dwarf"),
            row("4", "Thrain", "dwarf", "Smith", 80, "race:dwarf"),
            row("5", "Hamfast", "hobbit", "Jobless", 70, "jobless", "race:hobbit"),
            row("6", "Bergil", "man", "Guard", 20, "homeless", "race:man"));

    private static List<String> ids(List<Row> rows) {
        List<String> out = new ArrayList<>();
        rows.forEach(r -> out.add(r.id()));
        return out;
    }

    @Test
    void ticksWidenWithinAGroupAndNarrowAcrossGroups() {
        Map<String, Set<String>> ticked = Map.of("status", Set.of("homeless", "jobless"), "race", Set.of("race:dwarf"));
        assertEquals(List.of("1", "2", "3"), ids(TableQuery.apply(COLUMNS, GROUPS, ROWS, "", ticked, -1, false)));
        assertEquals(3, TableQuery.tickedCount(GROUPS, ticked), "the button should say Filters (3)");
        assertEquals(List.of("1", "3", "5"), ids(TableQuery.apply(COLUMNS, GROUPS, ROWS, "",
                Map.of("status", Set.of("jobless")), -1, false)));
    }

    @Test
    void noTicksOrUnknownGroupsShowEverything() {
        assertEquals(6, TableQuery.apply(COLUMNS, GROUPS, ROWS, "", Map.of(), -1, false).size());
        Map<String, Set<String>> stale = Map.of("gone", Set.of("x"), "status", Set.of());
        assertEquals(6, TableQuery.apply(COLUMNS, GROUPS, ROWS, "", stale, -1, false).size());
        assertEquals(0, TableQuery.tickedCount(GROUPS, stale));
    }

    @Test
    void searchNeedsEveryWordInSomeSearchableColumn() {
        assertEquals(List.of("1", "3", "5"), ids(TableQuery.apply(COLUMNS, GROUPS, ROWS, "jobless", Map.of(), -1,
                false)));
        assertEquals(List.of("1", "3"), ids(TableQuery.apply(COLUMNS, GROUPS, ROWS, "  JOBLESS   dwarf ", Map.of(),
                -1, false)));
        // numbers are not searchable columns
        assertEquals(List.of(), ids(TableQuery.apply(COLUMNS, GROUPS, ROWS, "80", Map.of(), -1, false)));
    }

    @Test
    void sortsNumbersByValueAndTextIgnoringCaseWithStableTies() {
        assertEquals(List.of("6", "1", "3", "2", "5", "4"),
                ids(TableQuery.apply(COLUMNS, GROUPS, ROWS, "", Map.of(), 3, false)));
        assertEquals(List.of("4", "5", "2", "3", "1", "6"),
                ids(TableQuery.apply(COLUMNS, GROUPS, ROWS, "", Map.of(), 3, true)));
        // by job: the three "Jobless" rows keep name order (Gimrin, Hamfast, Khulturg)
        assertEquals(List.of("2", "6", "3", "5", "1", "4"),
                ids(TableQuery.apply(COLUMNS, GROUPS, ROWS, "", Map.of(), 2, false)));
    }
}
