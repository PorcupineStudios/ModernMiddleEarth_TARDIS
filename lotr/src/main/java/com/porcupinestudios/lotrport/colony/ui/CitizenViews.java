package com.porcupinestudios.lotrport.colony.ui;

import static com.porcupinestudios.lotrport.colony.ui.ColonyViews.personName;
import static com.porcupinestudios.lotrport.colony.ui.ColonyViews.title;
import static com.porcupinestudios.lotrport.colony.ui.ColonyViews.tr;
import static com.porcupinestudios.lotrport.colony.ui.ColonyViews.traitName;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.colonist.ColonistData;
import com.porcupinestudios.lotrport.colonist.Colonists;
import com.porcupinestudios.lotrport.colonist.Kin;
import com.porcupinestudios.lotrport.colonist.Orders;
import com.porcupinestudios.lotrport.colonist.RationDef;
import com.porcupinestudios.lotrport.colonist.Rations;
import com.porcupinestudios.lotrport.colonist.Rest;
import com.porcupinestudios.lotrport.colonist.Rhythm;
import com.porcupinestudios.lotrport.colonist.Schedule;
import com.porcupinestudios.lotrport.colonist.ScheduleRules;
import com.porcupinestudios.lotrport.colonist.Schedules;
import com.porcupinestudios.lotrport.colonist.SleepSettings;
import com.porcupinestudios.lotrport.colonist.ThoughtDef;
import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.colony.building.Building;
import com.porcupinestudios.lotrport.colony.building.BuildingType;
import com.porcupinestudios.lotrport.colony.building.Buildings;
import com.porcupinestudios.lotrport.colony.members.Perm;
import com.porcupinestudios.lotrport.colony.members.Permissions;
import com.porcupinestudios.lotrport.colony.work.Assign;
import com.porcupinestudios.lotrport.colony.work.JobDef;
import com.porcupinestudios.lotrport.colony.work.Life;
import com.porcupinestudios.lotrport.colony.work.LifeSettings;
import com.porcupinestudios.lotrport.colony.work.Races;
import com.porcupinestudios.lotrport.colony.work.WorkComponents;
import com.porcupinestudios.lotrport.society.CoreComponents;
import com.porcupinestudios.lotrport.society.Person;
import com.porcupinestudios.lotrport.society.Persons;
import com.porcupinestudios.lotrport.society.Society;
import com.porcupinestudios.lotrport.ui.Page;
import com.porcupinestudios.lotrport.ui.Stat;
import com.porcupinestudios.lotrport.ui.Tone;
import com.porcupinestudios.lotrport.ui.View;
import com.porcupinestudios.lotrport.ui.ViewProvider;
import com.porcupinestudios.lotrport.ui.Views;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code lotr_port:citizen} (context: {@code <colony id>/<person id>}): one colonist up close, RimWorld style, in
 * four tabs under their portrait and name (renamed here): Overview (needs as bars, and orders: where they work or that
 * they rest, where they live, their ration), Mood (the colony's part of their mood and every thought, with how long
 * each lasts), Schedule (their race's hours, how their last day went, which colony schedule they keep) and About
 * (traits, purse, family). Members of the colony may look; changing anything takes {@link Perm#CITIZENS}.
 * Opened from a row of the colony's People tab; Back returns there. Orders take effect at once ({@link Assign#run}).
 */
public final class CitizenViews {
    public static final ResourceLocation CITIZEN = LotrPort.id("citizen");

    static final String RENAME = "rename";
    static final String WORK = "work";
    static final String HOME = "home";
    static final String RATION = "ration";
    static final String SCHEDULE_ORDER = "schedule";
    static final String EDIT_SCHEDULES = "edit_schedules";

    // tabs
    static final String OVERVIEW = "overview";
    static final String MOOD = "mood";
    static final String SCHEDULE = "schedule";
    static final String ABOUT = "about";
    static final String AUTO = "auto";
    static final String REST = "rest";

    private CitizenViews() {}

    public static void register() {
        Views.register(CITIZEN, new Provider());
    }

    public static View.Target target(Colony c, Person p) {
        return new View.Target(CITIZEN, c.id() + "/" + p.id(), "");
    }

    /** The colony and the person a context names, if the person is one of its citizens. */
    static Optional<Found> find(MinecraftServer server, String context) {
        int slash = context.indexOf('/');
        if (slash < 0) {
            return Optional.empty();
        }
        Optional<Colony> colony = ColonyViews.colony(server, context.substring(0, slash));
        UUID person;
        try {
            person = UUID.fromString(context.substring(slash + 1));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        if (colony.isEmpty()) {
            return Optional.empty();
        }
        return Society.person(server, person).filter(Person::alive)
                .filter(p -> Society.colonyOf(server, p).map(x -> x.id().equals(colony.get().id())).orElse(false))
                .map(p -> new Found(colony.get(), p));
    }

    record Found(Colony colony, Person person) {}

    static final class Provider implements ViewProvider {
        @Override
        public Optional<View> build(ServerPlayer player, String context, String tab) {
            Optional<Found> f = find(player.server, context);
            if (f.isEmpty() || !Permissions.isMember(player.getUUID(), f.get().colony())) {
                return Optional.empty();
            }
            Colony c = f.get().colony();
            Person p = f.get().person();
            List<View.Tab> tabs = List.of(new View.Tab(OVERVIEW, tr("citizen.tab.overview", "Overview")),
                    new View.Tab(MOOD, tr("citizen.tab.mood", "Mood")),
                    new View.Tab(SCHEDULE, tr("citizen.tab.schedule", "Schedule")),
                    new View.Tab(ABOUT, tr("citizen.tab.about", "About")));
            String open = tabs.stream().anyMatch(t -> t.id().equals(tab)) ? tab : OVERVIEW;
            Page page = page(player, c, p, open);
            return Optional.of(new View(CITIZEN, context, Component.literal(c.name()), tabs, open, page,
                    Optional.of(new View.Target(ColonyViews.COLONY, c.id().toString(), ColonyViews.PEOPLE))));
        }

        @Override
        public Component denied() {
            return tr("citizen.gone", "That citizen is no longer in your colony");
        }

        @Override
        public Result act(ServerPlayer player, String context, String tab, String action, String row, int value) {
            Optional<Found> f = find(player.server, context);
            if (f.isEmpty()) {
                return Result.NONE;
            }
            Colony c = f.get().colony();
            Person p = f.get().person();
            if (action.equals(EDIT_SCHEDULES)) {
                return Result.go(new View.Target(ColonyViews.COLONY, c.id().toString(), ColonyViews.SCHEDULES));
            }
            if (!Permissions.can(player.getUUID(), c, Perm.CITIZENS)) {
                return Result.say(tr("citizen.no_orders", "You may not give %s's citizens orders", c.name()));
            }
            MinecraftServer server = player.server;
            Persons persons = Persons.get(server);
            if (action.equals(RENAME)) {
                String old = personName(p);
                if (persons.rename(server, p, row)) {
                    return Result.say(tr("citizen.renamed", "%s is now called %s", old, p.name()));
                }
                return Result.NONE;
            }
            Orders before = Colonists.orders(p);
            Orders after = switch (action) {
                case WORK -> row.equals(AUTO) ? before.withWorks(true).withJob(Optional.empty())
                        : row.equals(REST) ? before.withWorks(false).withJob(Optional.empty())
                        : parse(row).filter(id -> workplaces(server, c, p).stream()
                                .anyMatch(w -> w.building().id().equals(id) && w.free() > 0))
                                .map(id -> before.withWorks(true).withJob(Optional.of(id))).orElse(before);
                case HOME -> row.equals(AUTO) ? before.withHome(Optional.empty())
                        : parse(row).filter(id -> homes(server, c, p).stream()
                                .anyMatch(h -> h.building().id().equals(id) && h.free() > 0))
                                .map(id -> before.withHome(Optional.of(id))).orElse(before);
                case RATION -> ColonistData.rations().containsKey(ResourceLocation.tryParse(row))
                        ? before.withRation(ResourceLocation.parse(row)) : before;
                case SCHEDULE_ORDER -> Colonists.schedules(c).get(row).isPresent() ? before.withSchedule(row) : before;
                default -> before;
            };
            if (!after.equals(before)) {
                persons.set(server, p, ColonistData.ORDERS, after.isDefault() ? null : after);
                Assign.run(server, c, server.overworld().getGameTime());
            }
            return Result.NONE;
        }
    }

    private static Optional<UUID> parse(String s) {
        try {
            return Optional.of(UUID.fromString(s));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    // ---------------------------------------------------------------- the page

    static Page page(ServerPlayer player, Colony c, Person p, String tab) {
        MinecraftServer server = player.server;
        long now = server.overworld().getGameTime();
        LifeSettings life = LifeSettings.get();
        Buildings buildings = Buildings.of(c);
        WorkComponents.Needs n = p.get(WorkComponents.NEEDS).orElse(WorkComponents.Needs.NEW);
        boolean soldier = p.has(CoreComponents.UNIT);
        boolean canOrder = Permissions.can(player.getUUID(), c, Perm.CITIZENS);
        Optional<Building> home = p.get(WorkComponents.HOME).flatMap(h -> buildings.get(h.building()));
        Optional<WorkComponents.Job> job = p.get(WorkComponents.JOB);

        // the line under the name: race, age, work
        List<String> under = new ArrayList<>();
        under.add(title(Races.of(p.type())));
        Colonists.age(p, now).ifPresent(a -> under.add(tr("citizen.age", "%s years old", (int) Math.floor(a))
                .getString()));
        under.add(soldier ? tr("soldier", "Soldier").getString()
                : job.map(j -> JobDef.titleOf(j.job()).getString()).orElse(tr("jobless", "Jobless").getString()));
        Component subtitle = Component.literal(String.join("  ·  ", under));

        // needs
        List<Stat> needs = new ArrayList<>();
        needs.add(Stat.of(tr("citizen.mood", "Mood"), Math.round(n.happiness()) + " / 100",
                ColonyViews.happinessTone(n.happiness(), life)).withBar(n.happiness() / 100));
        needs.add(Stat.of(tr("citizen.food", "Food"), Math.round(n.food()) + " / 20",
                n.food() <= 0 ? Tone.BAD : n.food() < life.eatBelow() ? Tone.WARN : Tone.NORMAL)
                .withBar(n.food() / 20));
        needs.add(Stat.of(tr("citizen.comfort", "Comfort"),
                home.map(b -> b.quality() + " / 100").orElse(tr("homeless", "Homeless").getString()),
                home.isPresent() ? Tone.NORMAL : Tone.WARN).withBar(home.map(b -> b.quality() / 100.0).orElse(0.0)));
        needs.add(Stat.of(tr("citizen.wages", "Wages"), n.unpaid() > 0
                ? tr("citizen.owed", "Owed %s days", n.unpaid()).getString() : tr("citizen.paid", "Paid").getString(),
                n.unpaid() > 0 ? Tone.BAD : Tone.GOOD));
        Optional<Rest> rest = p.get(ColonistData.REST);
        SleepSettings sleep = ColonistData.sleep();
        double energy = rest.map(Rest::energy).orElse(100.0);
        needs.add(Stat.of(tr("citizen.energy", "Energy"), Math.round(energy) + " / 100",
                rest.map(r -> r.lowest() < sleep.exhaustedBelow() ? Tone.BAD : r.lowest() < sleep.tiredBelow()
                        ? Tone.WARN : Tone.NORMAL).orElse(Tone.NORMAL)).withBar(energy / 100));
        job.ifPresent(j -> needs.add(Stat.of(tr("citizen.skill", "Skill"), Math.round(j.skill()) + " / 100")
                .withBar(j.skill() / 100)));

        // about
        List<Stat> about = new ArrayList<>();
        List<ResourceLocation> traits = p.get(ColonistData.TRAITS).orElse(List.of());
        about.add(Stat.of(tr("citizen.traits", "Traits"), traits.isEmpty() ? "-"
                : String.join(", ", traits.stream().map(t -> traitName(t).getString()).toList())));
        about.add(Stat.of(tr("citizen.purse", "Purse"),
                p.get(WorkComponents.PURSE).orElse(WorkComponents.Purse.EMPTY).coins() + "c"));
        List<Stat> kin = new ArrayList<>(family(server, p));
        if (kin.isEmpty()) {
            kin.add(Stat.of(tr("citizen.no_family", "No family in the colony yet"), "", Tone.MUTED));
        }

        // mood, RimWorld style: the colony's part, then each thought
        Optional<ResourceLocation> homeThought = Optional.ofNullable(
                Colonists.homeThoughts(Society.citizens(server, c), now).get(p.id()));
        WorkComponents.Economy eco = c.get(WorkComponents.ECONOMY).orElse(WorkComponents.Economy.DEFAULT);
        List<Stat> colonyMood = new ArrayList<>();
        List<Stat> mood = new ArrayList<>();
        double[] target = {0};
        if (!soldier) {
            Life.happinessParts(life, home, n.food(), false, n.unpaid(), eco, (key, v) -> {
                target[0] += v;
                colonyMood.add(moodLine(colonyPart(key), v));
            });
        }
        Map<ResourceLocation, ThoughtDef> defs = ColonistData.thoughts();
        for (Colonists.Thought t : Colonists.thoughts(p, homeThought, now)) {
            target[0] += t.mood();
            String label = thoughtName(t.id(), defs.get(t.id())).getString();
            if (t.stacks() > 1) {
                label += " x" + t.stacks();
            }
            if (t.ticksLeft() > 0) {
                label += " (" + daysLeft(t.ticksLeft()) + ")";
            }
            mood.add(moodLine(Component.literal(label), t.mood()));
        }
        if (mood.isEmpty()) {
            mood.add(Stat.of(tr("citizen.no_thoughts", "Nothing on their mind"), "", Tone.MUTED));
        }
        double goal = Math.max(0, Math.min(100, target[0]));
        Component moodTitle = tr("citizen.mood_title", "Mood: %s, heading for %s", Math.round(n.happiness()),
                Math.round(goal));

        // orders
        List<Stat> orders = new ArrayList<>();
        if (!soldier) {
            orders.add(workPicker(server, c, p, canOrder));
            orders.add(homePicker(server, c, p, canOrder));
            orders.add(rationPicker(p, canOrder));
        } else {
            orders.add(Stat.of(tr("citizen.soldier_orders", "Soldiers take orders as units"), ""));
        }

        List<Page.Section> left;
        List<Page.Section> right;
        List<com.porcupinestudios.lotrport.ui.Action> buttons = List.of();
        switch (tab) {
            case MOOD -> {
                left = List.of(new Page.Section(moodTitle, List.of(Stat.of(tr("citizen.mood", "Mood"),
                        Math.round(n.happiness()) + " / 100", ColonyViews.happinessTone(n.happiness(), life))
                        .withBar(n.happiness() / 100))), new Page.Section(tr("citizen.from_colony", "From the colony"),
                        colonyMood));
                right = List.of(new Page.Section(tr("citizen.thoughts", "On their mind"), mood));
            }
            case SCHEDULE -> {
                left = List.of(new Page.Section(tr("citizen.their_hours", "Their hours"), hours(p)),
                        new Page.Section(tr("citizen.last_day", "Their last day"), lastDay(rest, sleep)));
                Schedules schedules = Colonists.schedules(c);
                Schedule mine = Colonists.scheduleOf(schedules, p);
                List<Stat> sched = new ArrayList<>();
                sched.add(schedulePicker(schedules, mine, canOrder));
                for (Schedule.Block b : ScheduleViews.PALETTE) {
                    String spans = spans(mine, b);
                    if (!spans.isEmpty()) {
                        sched.add(Stat.of(ScheduleViews.blockName(b), spans));
                    }
                }
                right = List.of(new Page.Section(tr("citizen.schedule", "Schedule"), sched));
                buttons = List.of(com.porcupinestudios.lotrport.ui.Action.of(EDIT_SCHEDULES,
                        tr("citizen.edit_schedules", "Edit schedules")));
            }
            case ABOUT -> {
                left = List.of(new Page.Section(tr("citizen.about", "About"), about));
                right = List.of(new Page.Section(tr("citizen.family", "Family"), kin));
            }
            default -> {
                left = List.of(new Page.Section(tr("citizen.needs", "Needs"), needs));
                right = List.of(new Page.Section(tr("citizen.orders", "Orders"), orders));
            }
        }
        return new Page.Profile(Optional.of(p.id()), Component.literal(personName(p)),
                canOrder ? Optional.of(RENAME) : Optional.empty(), subtitle, left, right, buttons);
    }

    /** The race's preferred hours and wants. */
    private static List<Stat> hours(Person p) {
        Rhythm r = Colonists.rhythm(p);
        return List.of(Stat.of(tr("citizen.likes_up", "Likes to be up"), r.window()),
                Stat.of(tr("citizen.wants_sleep", "Wants sleep"), tr("citizen.hours", "%s h", r.sleepHours())
                        .getString()),
                Stat.of(tr("citizen.wants_leisure", "Wants leisure"), tr("citizen.hours", "%s h", r.leisureHours())
                        .getString()));
    }

    /** How the last day went by their schedule. */
    private static List<Stat> lastDay(Optional<Rest> rest, SleepSettings s) {
        if (rest.isEmpty() || !rest.get().counted()) {
            return List.of(Stat.of(tr("citizen.no_day", "No day counted yet"), "", Tone.MUTED));
        }
        Rest r = rest.get();
        double factor = ScheduleRules.workFactor(r, s);
        return List.of(
                Stat.of(tr("citizen.slept", "Slept"), tr("citizen.hours", "%s h", r.sleepHours()).getString()),
                Stat.of(tr("citizen.worked", "Worked"), tr("citizen.work_hours", "%s h (%s%% output)",
                        Math.round(r.workDone() * 10) / 10.0, Math.round(factor * 100)).getString(),
                        factor < 0.8 ? Tone.WARN : Tone.NORMAL),
                Stat.of(tr("citizen.leisure", "Leisure"), tr("citizen.hours", "%s h", r.leisureHours()).getString()),
                Stat.of(tr("citizen.off_hours", "Up at odd hours"), tr("citizen.hours", "%s h", r.offHours())
                        .getString(), r.offHours() >= s.wrongHoursFrom() ? Tone.BAD : r.offHours() >= s.oddHoursFrom()
                        ? Tone.WARN : Tone.GOOD),
                Stat.of(tr("citizen.lowest", "Lowest energy"), Long.toString(Math.round(r.lowest())),
                        r.lowest() < s.exhaustedBelow() ? Tone.BAD : r.lowest() < s.tiredBelow() ? Tone.WARN
                                : Tone.NORMAL).withBar(r.lowest() / 100));
    }

    /** "22-06, 12-13": the hour spans a schedule gives to one kind of block. */
    static String spans(Schedule s, Schedule.Block b) {
        List<String> out = new ArrayList<>();
        int start = -1;
        // start at an hour that isn't this block, so a span over midnight comes out whole
        int from = 0;
        while (from < Schedule.HOURS && s.at(from) == b) {
            from++;
        }
        if (from == Schedule.HOURS) {
            return "00-24";
        }
        for (int i = 1; i <= Schedule.HOURS; i++) {
            int h = (from + i) % Schedule.HOURS;
            boolean in = s.at(h) == b && i < Schedule.HOURS;
            if (in && start < 0) {
                start = h;
            } else if (!in && start >= 0) {
                out.add(String.format(Locale.ROOT, "%02d-%02d", start, h == 0 ? 24 : h));
                start = -1;
            }
        }
        return String.join(", ", out);
    }

    static Stat schedulePicker(Schedules all, Schedule mine, boolean enabled) {
        List<Stat.Choice> choices = new ArrayList<>();
        for (Schedule s : all.list()) {
            choices.add(Stat.Choice.of(s.id(), Component.literal(s.name())));
        }
        return picker(tr("citizen.keeps", "Keeps"), mine.id(), choices, SCHEDULE_ORDER, enabled);
    }

    private static Stat moodLine(Component label, double v) {
        long r = Math.round(v);
        return Stat.of(label, (r > 0 ? "+" : "") + r, r > 0 ? Tone.GOOD : r < 0 ? Tone.BAD : Tone.MUTED);
    }

    private static Component colonyPart(String key) {
        String fallback = switch (key) {
            case "base" -> "Living in the colony";
            case "home" -> "A home and its comfort";
            case "fed" -> "Well fed";
            case "hungry" -> "Hungry";
            case "tavern" -> "A drink at the tavern";
            case "unpaid" -> "Wages owed";
            case "tax" -> "Head tax";
            default -> key;
        };
        return tr("mood." + key, fallback);
    }

    static Component thoughtName(ResourceLocation id, ThoughtDef def) {
        String fallback = def != null && def.name().isPresent() ? def.name().get() : title(id.getPath());
        return Component.translatableWithFallback("thought." + id.getNamespace() + "." + id.getPath(), fallback);
    }

    private static String daysLeft(long ticks) {
        double days = ticks / (double) Math.max(1, com.porcupinestudios.lotrport.colony.ColonyData.settings()
                .dayTicks());
        return days >= 1 ? tr("citizen.days", "%s days", (int) Math.ceil(days)).getString()
                : tr("citizen.today", "today").getString();
    }

    private static List<Stat> family(MinecraftServer server, Person p) {
        Optional<Kin> kin = p.get(ColonistData.KIN);
        if (kin.isEmpty()) {
            return List.of();
        }
        List<Stat> out = new ArrayList<>();
        kin.get().spouse().ifPresent(s -> out.add(Stat.of(tr("citizen.spouse", "Spouse"), nameOf(server, s))));
        if (!kin.get().parents().isEmpty()) {
            out.add(Stat.of(tr("citizen.parents", "Parents"), String.join(", ",
                    kin.get().parents().stream().map(id -> nameOf(server, id)).toList())));
        }
        if (!kin.get().children().isEmpty()) {
            out.add(Stat.of(tr("citizen.children", "Children"), String.join(", ",
                    kin.get().children().stream().map(id -> nameOf(server, id)).toList())));
        }
        return out;
    }

    private static String nameOf(MinecraftServer server, UUID id) {
        return Society.person(server, id).map(x -> personName(x) + (x.alive() ? "" : " †")).orElse("?");
    }

    // ---------------------------------------------------------------- orders

    /** A place this citizen could be sent: the building, its job and how many more it takes (counting them out). */
    record Slot(Building building, String label, int free) {}

    static List<Slot> workplaces(MinecraftServer server, Colony c, Person p) {
        Map<UUID, Integer> taken = new HashMap<>();
        for (Person o : Society.citizens(server, c)) {
            if (!o.id().equals(p.id())) {
                o.get(WorkComponents.JOB).ifPresent(j -> taken.merge(j.building(), 1, Integer::sum));
            }
        }
        List<Slot> out = new ArrayList<>();
        for (Building b : Buildings.of(c).list()) {
            Optional<ResourceLocation> job = BuildingType.get(b.type()).flatMap(BuildingType::job);
            Optional<JobDef> def = job.flatMap(JobDef::get);
            if (def.isPresent()) {
                int slots = def.get().slots(b.quality());
                out.add(new Slot(b, tr("citizen.job_at", "%s at %s", JobDef.titleOf(job.get()), b.label())
                        .getString(), slots - taken.getOrDefault(b.id(), 0)));
            }
        }
        return out;
    }

    static List<Slot> homes(MinecraftServer server, Colony c, Person p) {
        Map<UUID, Integer> taken = new HashMap<>();
        for (Person o : Society.citizens(server, c)) {
            if (!o.id().equals(p.id())) {
                o.get(WorkComponents.HOME).ifPresent(h -> taken.merge(h.building(), 1, Integer::sum));
            }
        }
        List<Slot> out = new ArrayList<>();
        for (Building b : Buildings.of(c).homes()) {
            int beds = b.count("beds");
            int used = taken.getOrDefault(b.id(), 0);
            out.add(new Slot(b, tr("citizen.home_beds", "%s (%s of %s beds)", b.label(), used, beds).getString(),
                    beds - used));
        }
        return out;
    }

    static Stat workPicker(MinecraftServer server, Colony c, Person p, boolean enabled) {
        Orders o = Colonists.orders(p);
        String current = !o.works() ? REST : o.job().map(UUID::toString).orElse(AUTO);
        List<Stat.Choice> choices = new ArrayList<>();
        choices.add(Stat.Choice.of(AUTO, tr("citizen.work_auto", "Colony decides")));
        choices.add(Stat.Choice.of(REST, tr("citizen.work_rest", "Rest, no work")));
        for (Slot s : workplaces(server, c, p)) {
            String id = s.building().id().toString();
            choices.add(s.free() > 0 || id.equals(current) ? Stat.Choice.of(id, Component.literal(s.label()))
                    : new Stat.Choice(id, Component.literal(s.label()), false,
                            Optional.of(tr("citizen.full", "No free place"))));
        }
        return picker(tr("citizen.work", "Work"), current, choices, WORK, enabled);
    }

    static Stat homePicker(MinecraftServer server, Colony c, Person p, boolean enabled) {
        String current = Colonists.orders(p).home().map(UUID::toString).orElse(AUTO);
        List<Stat.Choice> choices = new ArrayList<>();
        choices.add(Stat.Choice.of(AUTO, tr("citizen.home_auto", "Colony decides")));
        for (Slot s : homes(server, c, p)) {
            String id = s.building().id().toString();
            choices.add(s.free() > 0 || id.equals(current) ? Stat.Choice.of(id, Component.literal(s.label()))
                    : new Stat.Choice(id, Component.literal(s.label()), false,
                            Optional.of(tr("citizen.no_bed", "No free bed"))));
        }
        return picker(tr("citizen.home", "Home"), current, choices, HOME, enabled);
    }

    static Stat rationPicker(Person p, boolean enabled) {
        String current = Colonists.orders(p).ration().toString();
        List<Stat.Choice> choices = new ArrayList<>();
        for (ResourceLocation id : Rations.ordered()) {
            RationDef def = Rations.get(id);
            String pct = Math.round(def.foodRate() * 100) + "%";
            choices.add(Stat.Choice.of(id.toString(), Component.translatableWithFallback(
                    "ration." + id.getNamespace() + "." + id.getPath(), "%s (%s food)", title(id.getPath()), pct)));
        }
        return picker(tr("citizen.ration", "Ration"), current, choices, RATION, enabled);
    }

    /** A line with a drop-down; without the permission it shows the choice in force and no drop-down. */
    private static Stat picker(Component label, String current, List<Stat.Choice> choices, String action,
            boolean enabled) {
        String shown = choices.stream().filter(ch -> ch.id().equals(current)).findFirst()
                .map(ch -> ch.label().getString()).orElse(current.toLowerCase(Locale.ROOT));
        Stat s = Stat.of(label, shown);
        return enabled ? s.withPicker(new Stat.Picker(action, current, choices)) : s;
    }
}
