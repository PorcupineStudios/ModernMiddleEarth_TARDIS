package com.porcupinestudios.lotrport.colony.ui;

import static com.porcupinestudios.lotrport.colony.ui.ColonyViews.title;
import static com.porcupinestudios.lotrport.colony.ui.ColonyViews.tr;

import com.porcupinestudios.lotrport.colonist.ColonistData;
import com.porcupinestudios.lotrport.colonist.Colonists;
import com.porcupinestudios.lotrport.colonist.RaceDef;
import com.porcupinestudios.lotrport.colonist.Rhythm;
import com.porcupinestudios.lotrport.colonist.Schedule;
import com.porcupinestudios.lotrport.colonist.Schedules;
import com.porcupinestudios.lotrport.colony.Colonies;
import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.colony.members.Perm;
import com.porcupinestudios.lotrport.colony.members.Permissions;
import com.porcupinestudios.lotrport.society.CoreComponents;
import com.porcupinestudios.lotrport.society.Person;
import com.porcupinestudios.lotrport.society.Society;
import com.porcupinestudios.lotrport.ui.Action;
import com.porcupinestudios.lotrport.ui.Page;
import com.porcupinestudios.lotrport.ui.ViewProvider;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * The colony view's Schedules tab (ONI's schedule screen): one row of 24 hours per schedule, painted Work, Leisure,
 * Sleep or Any; New schedule and Remove; and, greyed under them, each race's preferred hours so players can plan
 * shifts round them. Who keeps which schedule is set on the citizen page. Changes need {@link Perm#CITIZENS}.
 */
final class ScheduleViews {
    static final String PAINT = "paint";
    static final String ADD = "add_schedule";
    static final String REMOVE = "remove_schedule";

    /** The palette, in order: a painted cell's value is {@code hour * 16 + index}. */
    static final Schedule.Block[] PALETTE = {Schedule.Block.WORK, Schedule.Block.LEISURE, Schedule.Block.SLEEP,
        Schedule.Block.ANY};
    /** Letters of the race rows (not paintable). */
    static final char AWAKE = 'U';
    static final char ASLEEP = 'Z';

    private ScheduleViews() {}

    static Component blockName(Schedule.Block b) {
        String fallback = switch (b) {
            case WORK -> "Work";
            case LEISURE -> "Leisure";
            case SLEEP -> "Sleep";
            case ANY -> "Any";
        };
        return tr("schedule." + b.id(), fallback);
    }

    static Page page(ServerPlayer player, Colony c) {
        boolean canEdit = Permissions.can(player.getUUID(), c, Perm.CITIZENS);
        Schedules all = Colonists.schedules(c);
        Map<String, Integer> keeping = new HashMap<>();
        for (Person p : Society.citizens(player.server, c)) {
            if (!p.has(CoreComponents.UNIT)) {
                keeping.merge(Colonists.scheduleOf(all, p).id(), 1, Integer::sum);
            }
        }
        List<Page.Kind> kinds = new ArrayList<>();
        for (int i = 0; i < PALETTE.length; i++) {
            kinds.add(new Page.Kind(PALETTE[i].letter, blockName(PALETTE[i]), i, true));
        }
        kinds.add(new Page.Kind(AWAKE, tr("schedule.awake", "Likes to be up"), 4, false));
        kinds.add(new Page.Kind(ASLEEP, tr("schedule.asleep", "Likes to sleep"), 5, false));

        List<Page.Line> lines = new ArrayList<>();
        for (Schedule s : all.list()) {
            lines.add(new Page.Line(s.id(), Component.literal(s.name()),
                    tr("schedule.keeping", "(%s)", keeping.getOrDefault(s.id(), 0)), s.blocks(), true));
        }
        List<Component> notes = new ArrayList<>();
        // each race's hours as a greyed row, and its wants in a note
        List<Map.Entry<ResourceLocation, RaceDef>> races = new ArrayList<>(ColonistData.races().entrySet());
        races.sort(Map.Entry.comparingByKey());
        for (Map.Entry<ResourceLocation, RaceDef> e : races) {
            Rhythm r = e.getValue().rhythm();
            StringBuilder cells = new StringBuilder(Schedule.HOURS);
            for (int h = 0; h < Schedule.HOURS; h++) {
                cells.append(r.awake(h) ? AWAKE : ASLEEP);
            }
            Component race = raceName(e.getKey());
            lines.add(new Page.Line("race:" + e.getKey(), race, Component.empty(), cells.toString(), false));
            notes.add(tr("schedule.race_note", "%s: up %s, want %s h sleep and %s h leisure", race, r.window(),
                    r.sleepHours(), r.leisureHours()));
        }
        notes.add(tr("schedule.how", "Choose who keeps which schedule on their citizen page (Schedule tab)."));
        List<Action> rowActions = List.of(canEdit
                ? Action.of(REMOVE, tr("schedule.remove", "Remove")).confirmed()
                : Action.disabled(REMOVE, tr("schedule.remove", "Remove"), tr("schedule.no_edit",
                        "Only those who may give citizens orders may change schedules")));
        List<Action> buttons = List.of(canEdit && all.list().size() < Schedules.MAX
                ? Action.of(ADD, tr("schedule.add", "New schedule"))
                : Action.disabled(ADD, tr("schedule.add", "New schedule"), canEdit
                        ? tr("schedule.too_many", "A colony keeps at most %s schedules", Schedules.MAX)
                        : tr("schedule.no_edit", "Only those who may give citizens orders may change schedules")));
        return new Page.Timetable(kinds, lines, canEdit ? Optional.of(PAINT) : Optional.empty(), rowActions, buttons,
                notes);
    }

    static Component raceName(ResourceLocation race) {
        return Component.translatableWithFallback("colonist_race." + race.getNamespace() + "." + race.getPath(),
                title(race.getPath()));
    }

    /** Paint, add or remove; anything else is not ours (empty). */
    static Optional<ViewProvider.Result> act(ServerPlayer player, Colony c, String action, String row, int value) {
        if (!action.equals(PAINT) && !action.equals(ADD) && !action.equals(REMOVE)) {
            return Optional.empty();
        }
        if (!Permissions.can(player.getUUID(), c, Perm.CITIZENS)) {
            return Optional.of(ViewProvider.Result.say(tr("schedule.no_edit",
                    "Only those who may give citizens orders may change schedules")));
        }
        Schedules all = Colonists.schedules(c);
        Schedules next = all;
        switch (action) {
            case PAINT -> {
                int hour = value / 16;
                int kind = value % 16;
                Optional<Schedule> s = all.get(row);
                if (s.isPresent() && hour >= 0 && hour < Schedule.HOURS && kind >= 0 && kind < PALETTE.length) {
                    next = all.put(s.get().with(hour, PALETTE[kind]));
                }
            }
            case ADD -> {
                if (all.list().size() < Schedules.MAX) {
                    String id = all.freeId();
                    next = all.put(new Schedule(id, tr("schedule.new_name", "Schedule %s", all.list().size() + 1)
                            .getString(), all.first().blocks()));
                }
            }
            default -> next = all.remove(row);
        }
        if (!next.equals(all)) {
            Colonies.get(player.server).set(c, Schedules.TYPE, next);
        }
        return Optional.of(ViewProvider.Result.NONE);
    }
}
