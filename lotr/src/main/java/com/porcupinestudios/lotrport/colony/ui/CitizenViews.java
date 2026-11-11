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
 * {@code lotr_port:citizen} (context: {@code <colony id>/<person id>}): one colonist up close, RimWorld style. Their
 * portrait and name (renamed here), needs as bars, what is on their mind (the colony's part of their mood and every
 * thought, with how long each lasts), facts (traits, family, purse), and orders: where they work or that they rest,
 * where they live, their ration. Members of the colony may look; changing anything takes {@link Perm#CITIZENS}.
 * Opened from a row of the colony's People tab; Back returns there. Orders take effect at once ({@link Assign#run}).
 */
public final class CitizenViews {
    public static final ResourceLocation CITIZEN = LotrPort.id("citizen");

    static final String RENAME = "rename";
    static final String WORK = "work";
    static final String HOME = "home";
    static final String RATION = "ration";
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
            Page page = page(player, c, p);
            return Optional.of(new View(CITIZEN, context, Component.literal(c.name()), List.of(), "", page,
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

    static Page page(ServerPlayer player, Colony c, Person p) {
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
        job.ifPresent(j -> needs.add(Stat.of(tr("citizen.skill", "Skill"), Math.round(j.skill()) + " / 100")
                .withBar(j.skill() / 100)));

        // about
        List<Stat> about = new ArrayList<>();
        List<ResourceLocation> traits = p.get(ColonistData.TRAITS).orElse(List.of());
        about.add(Stat.of(tr("citizen.traits", "Traits"), traits.isEmpty() ? "-"
                : String.join(", ", traits.stream().map(t -> traitName(t).getString()).toList())));
        family(server, p).forEach(about::add);
        about.add(Stat.of(tr("citizen.purse", "Purse"),
                p.get(WorkComponents.PURSE).orElse(WorkComponents.Purse.EMPTY).coins() + "c"));

        // mood, RimWorld style: the colony's part, then each thought
        Optional<ResourceLocation> homeThought = Optional.ofNullable(
                Colonists.homeThoughts(Society.citizens(server, c), now).get(p.id()));
        WorkComponents.Economy eco = c.get(WorkComponents.ECONOMY).orElse(WorkComponents.Economy.DEFAULT);
        List<Stat> mood = new ArrayList<>();
        double[] target = {0};
        if (!soldier) {
            Life.happinessParts(life, home, n.food(), false, n.unpaid(), eco, (key, v) -> {
                target[0] += v;
                mood.add(moodLine(colonyPart(key), v));
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

        List<Page.Section> left = List.of(new Page.Section(tr("citizen.needs", "Needs"), needs),
                new Page.Section(tr("citizen.about", "About"), about));
        List<Page.Section> right = List.of(new Page.Section(tr("citizen.orders", "Orders"), orders),
                new Page.Section(moodTitle, mood));
        return new Page.Profile(Optional.of(p.id()), Component.literal(personName(p)),
                canOrder ? Optional.of(RENAME) : Optional.empty(), subtitle, left, right, List.of());
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
