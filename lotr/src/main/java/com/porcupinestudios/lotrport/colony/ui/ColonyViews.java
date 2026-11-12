package com.porcupinestudios.lotrport.colony.ui;

import com.mojang.authlib.GameProfile;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.colonist.ColonistData;
import com.porcupinestudios.lotrport.colony.Colonies;
import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.colony.building.Building;
import com.porcupinestudios.lotrport.colony.building.BuildingType;
import com.porcupinestudios.lotrport.colony.building.Buildings;
import com.porcupinestudios.lotrport.colony.members.Members;
import com.porcupinestudios.lotrport.colony.members.Perm;
import com.porcupinestudios.lotrport.colony.members.Permissions;
import com.porcupinestudios.lotrport.colony.members.RoleDef;
import com.porcupinestudios.lotrport.colony.work.JobDef;
import com.porcupinestudios.lotrport.colony.work.LedgerNet;
import com.porcupinestudios.lotrport.colony.work.LifeSettings;
import com.porcupinestudios.lotrport.colony.work.Races;
import com.porcupinestudios.lotrport.colony.work.WorkComponents;
import com.porcupinestudios.lotrport.society.CoreComponents;
import com.porcupinestudios.lotrport.society.Person;
import com.porcupinestudios.lotrport.society.Society;
import com.porcupinestudios.lotrport.ui.Action;
import com.porcupinestudios.lotrport.ui.Cell;
import com.porcupinestudios.lotrport.ui.Column;
import com.porcupinestudios.lotrport.ui.FilterGroup;
import com.porcupinestudios.lotrport.ui.Page;
import com.porcupinestudios.lotrport.ui.Stat;
import com.porcupinestudios.lotrport.ui.TableBuilder;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The colony views on the UI core:
 * <ul>
 *   <li>{@code lotr_port:colony} (context: the colony's id): Overview, Buildings, People, Schedules, Stock and
 *       Members tabs, for members. Head tax and wage bonus change on Overview with the {@code treasury} permission; roles change on
 *       Members with {@code members}.</li>
 *   <li>{@code lotr_port:colonies}: every colony you belong to; open one to manage it.</li>
 * </ul>
 * Opened from the Town Hall's Manage button and {@code /lotr_colony manage}.
 */
public final class ColonyViews {
    public static final ResourceLocation COLONY = LotrPort.id("colony");
    public static final ResourceLocation COLONIES = LotrPort.id("colonies");

    static final String OVERVIEW = "overview";
    static final String BUILDINGS = "buildings";
    static final String PEOPLE = "people";
    static final String STOCK = "stock";
    static final String SCHEDULES = "schedules";
    static final String MEMBERS = "members";

    // people tags (the filters' ticks)
    static final String HOMELESS = "homeless";
    static final String JOBLESS = "jobless";
    static final String UNHAPPY = "unhappy";
    static final String HUNGRY = "hungry";
    static final String SOLDIER = "soldier";

    private ColonyViews() {}

    public static void register() {
        Views.register(COLONY, new ColonyProvider());
        Views.register(COLONIES, new ColoniesProvider());
        CitizenViews.register();
        CaravanViews.register();
        CandidateViews.register();
    }

    /** Opens a colony's view for a player (the Hall's Manage button, the command). */
    public static void open(ServerPlayer player, Colony colony) {
        Views.open(player, new View.Target(COLONY, colony.id().toString(), PEOPLE), true);
    }

    public static void openAll(ServerPlayer player) {
        Views.open(player, new View.Target(COLONIES, "", ""), true);
    }

    static Component tr(String key, String fallback, Object... args) {
        return Component.translatableWithFallback("ui.lotr_port.colony." + key, fallback, args);
    }

    static Optional<Colony> colony(MinecraftServer server, String context) {
        try {
            return Colonies.get(server).get(UUID.fromString(context));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    static String personName(Person p) {
        return p.name().isEmpty() ? p.type().getPath().replace('_', ' ') : p.name();
    }

    static String playerName(MinecraftServer server, UUID id) {
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        if (online != null) {
            return online.getGameProfile().getName();
        }
        return Optional.ofNullable(server.getProfileCache()).flatMap(pc -> pc.get(id)).map(GameProfile::getName)
                .orElse(id.toString().substring(0, 8));
    }

    static String title(String raceOrWord) {
        return raceOrWord.isEmpty() ? raceOrWord
                : raceOrWord.substring(0, 1).toUpperCase(Locale.ROOT) + raceOrWord.substring(1).replace('_', ' ');
    }

    static Tone happinessTone(double h, LifeSettings s) {
        return h < s.leaveBelow() ? Tone.BAD : h < 50 ? Tone.WARN : h >= 75 ? Tone.GOOD : Tone.NORMAL;
    }

    // ================================================================ one colony

    static final class ColonyProvider implements ViewProvider {
        @Override
        public Optional<View> build(ServerPlayer player, String context, String tab) {
            Optional<Colony> found = colony(player.server, context);
            if (found.isEmpty() || !Permissions.isMember(player.getUUID(), found.get())) {
                return Optional.empty();
            }
            Colony c = found.get();
            List<View.Tab> tabs = List.of(
                    new View.Tab(OVERVIEW, tr("tab.overview", "Overview")),
                    new View.Tab(BUILDINGS, tr("tab.buildings", "Buildings")),
                    new View.Tab(PEOPLE, tr("tab.people", "People")),
                    new View.Tab(SCHEDULES, tr("tab.schedules", "Schedules")),
                    new View.Tab(STOCK, tr("tab.stock", "Stock")),
                    new View.Tab(ImmigrationViews.TAB, tr("tab.immigration", "Immigration")),
                    new View.Tab(CaravanViews.TAB, tr("tab.caravans", "Caravans")),
                    new View.Tab(MEMBERS, tr("tab.members", "Members")));
            String open = tabs.stream().anyMatch(t -> t.id().equals(tab)) ? tab : OVERVIEW;
            Page page = switch (open) {
                case BUILDINGS -> buildings(player.server, c);
                case PEOPLE -> people(player.server, c);
                case STOCK -> stock(c);
                case SCHEDULES -> ScheduleViews.page(player, c);
                case CaravanViews.TAB -> CaravanViews.tab(player, c);
                case ImmigrationViews.TAB -> ImmigrationViews.tab(player, c);
                case MEMBERS -> members(player, c);
                default -> overview(player, c);
            };
            Optional<View.Target> back = Colonies.get(player.server).all().stream()
                    .filter(x -> Permissions.isMember(player.getUUID(), x)).count() > 1
                    ? Optional.of(new View.Target(COLONIES, "", "")) : Optional.empty();
            return Optional.of(new View(COLONY, context, Component.literal(c.name()), tabs, open, page, back));
        }

        @Override
        public Component denied() {
            return tr("denied", "Only members of a colony may manage it");
        }

        @Override
        public Result act(ServerPlayer player, String context, String tab, String action, String row, int value) {
            Colony c = colony(player.server, context).orElse(null);
            if (c == null) {
                return Result.NONE;
            }
            if (tab.equals(ImmigrationViews.TAB)) {
                Optional<Result> imm = ImmigrationViews.act(player, c, action, row);
                if (imm.isPresent()) {
                    return imm.get();
                }
            }
            if (tab.equals(CaravanViews.TAB)) {
                Optional<Result> caravan = CaravanViews.tabAct(player, c, action, row);
                if (caravan.isPresent()) {
                    return caravan.get();
                }
            }
            Optional<Result> schedule = ScheduleViews.act(player, c, action, row, value);
            if (schedule.isPresent()) {
                return schedule.get();
            }
            if (action.equals("citizen")) {
                try {
                    return Society.person(player.server, UUID.fromString(row)).filter(p -> Society.colonyOf(
                            player.server, p).map(x -> x.id().equals(c.id())).orElse(false))
                            .map(p -> Result.go(CitizenViews.target(c, p))).orElse(Result.NONE);
                } catch (IllegalArgumentException e) {
                    return Result.NONE;
                }
            }
            if (action.equals("head_tax") || action.equals("wage_bonus")) {
                if (!Permissions.can(player.getUUID(), c, Perm.TREASURY)) {
                    return Result.say(tr("no_treasury", "You may not change %s's taxes", c.name()));
                }
                WorkComponents.Economy e = c.get(WorkComponents.ECONOMY).orElse(WorkComponents.Economy.DEFAULT);
                WorkComponents.Economy next = action.equals("head_tax")
                        ? new WorkComponents.Economy(Math.max(0, Math.min(LedgerNet.MAX_TAX, value)), e.wageBonus())
                        : new WorkComponents.Economy(e.headTax(),
                                Math.max(LedgerNet.MIN_BONUS, Math.min(LedgerNet.MAX_BONUS, value)));
                Colonies.get(player.server).set(c, WorkComponents.ECONOMY, next);
                return Result.NONE;
            }
            if (action.startsWith("role:") || action.equals("remove")) {
                UUID who;
                try {
                    who = UUID.fromString(row);
                } catch (IllegalArgumentException e) {
                    return Result.NONE;
                }
                Optional<ResourceLocation> role = action.equals("remove") ? Optional.empty()
                        : Optional.ofNullable(ResourceLocation.tryParse(action.substring("role:".length())));
                if (!action.equals("remove") && role.isEmpty()) {
                    return Result.NONE;
                }
                Permissions.Change ch = Permissions.setRole(player.server, player.getUUID(), c, who, role);
                return Result.say(ch.message());
            }
            if (action.equals("all")) {
                return Result.go(new View.Target(COLONIES, "", ""));
            }
            return Result.NONE;
        }
    }

    // ---------------------------------------------------------------- Overview

    static Page overview(ServerPlayer player, Colony c) {
        MinecraftServer server = player.server;
        LifeSettings life = LifeSettings.get();
        List<Person> people = Society.citizens(server, c);
        double happy = 0;
        double food = 0;
        int counted = 0;
        int homeless = 0;
        int jobless = 0;
        for (Person p : people) {
            if (p.has(CoreComponents.UNIT)) {
                continue;
            }
            WorkComponents.Needs n = p.get(WorkComponents.NEEDS).orElse(WorkComponents.Needs.NEW);
            happy += n.happiness();
            food += n.food();
            counted++;
            homeless += p.has(WorkComponents.HOME) ? 0 : 1;
            jobless += p.has(WorkComponents.JOB) ? 0 : 1;
        }
        double avgHappy = counted == 0 ? 0 : happy / counted;
        double avgFood = counted == 0 ? 0 : food / counted;
        Buildings buildings = Buildings.of(c);

        List<Stat> colony = List.of(
                Stat.of(tr("citizens", "Citizens"), Integer.toString(counted)),
                Stat.of(tr("soldiers", "Soldiers"), Integer.toString(Society.soldiers(server, c).size())),
                Stat.of(tr("immigrants", "Groups wanting to settle"),
                        Integer.toString(c.get(com.porcupinestudios.lotrport.colony.Immigration.BOARD)
                                .map(b -> b.offers().size()).orElse(0))),
                Stat.of(tr("buildings", "Buildings"), Integer.toString(buildings.list().size())),
                Stat.of(tr("homeless", "Homeless"), Integer.toString(homeless), homeless > 0 ? Tone.WARN : Tone.GOOD),
                Stat.of(tr("jobless", "Jobless"), Integer.toString(jobless), jobless > 0 ? Tone.WARN : Tone.GOOD));
        List<Stat> wellbeing = List.of(
                Stat.of(tr("happiness", "Happiness"), Math.round(avgHappy) + " / 100",
                        happinessTone(avgHappy, life)).withBar(avgHappy / 100),
                Stat.of(tr("food", "Food"), Math.round(avgFood) + " / 20",
                        avgFood <= 0 ? Tone.BAD : avgFood < life.eatBelow() ? Tone.WARN : Tone.NORMAL)
                        .withBar(avgFood / 20));

        boolean treasury = Permissions.can(player.getUUID(), c, Perm.TREASURY);
        WorkComponents.Economy eco = c.get(WorkComponents.ECONOMY).orElse(WorkComponents.Economy.DEFAULT);
        long coins = c.get(CoreComponents.TREASURY).map(CoreComponents.Treasury::coins).orElse(0L);
        Stat tax = Stat.of(tr("head_tax", "Head tax"), eco.headTax() + "c");
        Stat bonus = Stat.of(tr("wage_bonus", "Wage bonus"), (eco.wageBonus() >= 0 ? "+" : "") + eco.wageBonus() + "c");
        if (treasury) {
            tax = tax.withStepper(new Stat.Stepper("head_tax", eco.headTax(), 0, LedgerNet.MAX_TAX, 1));
            bonus = bonus.withStepper(new Stat.Stepper("wage_bonus", eco.wageBonus(), LedgerNet.MIN_BONUS,
                    LedgerNet.MAX_BONUS, 1));
        }
        List<Stat> money = List.of(Stat.of(tr("coins", "Coins"), Long.toString(coins)), tax, bonus);
        return new Page.Stats(List.of(
                new Page.Section(tr("section.colony", "Colony"), colony),
                new Page.Section(tr("section.wellbeing", "Wellbeing"), wellbeing),
                new Page.Section(tr("section.treasury", "Treasury"), money)), List.of());
    }

    // ---------------------------------------------------------------- Buildings

    static Page buildings(MinecraftServer server, Colony c) {
        Buildings buildings = Buildings.of(c);
        Map<UUID, Integer> workers = new HashMap<>();
        Map<UUID, Integer> residents = new HashMap<>();
        // who lives and works where: one pass over the citizens, not one per building
        for (Person p : Society.citizens(server, c)) {
            p.get(WorkComponents.JOB).ifPresent(j -> workers.merge(j.building(), 1, Integer::sum));
            p.get(WorkComponents.HOME).ifPresent(h -> residents.merge(h.building(), 1, Integer::sum));
        }
        FilterGroup kind = new FilterGroup("kind", tr("filter.kind", "Kind"), List.of(
                new FilterGroup.Filter("home", tr("filter.homes", "Homes")),
                new FilterGroup.Filter("work", tr("filter.workplaces", "Workplaces")),
                new FilterGroup.Filter("other", tr("filter.other", "Other"))));
        FilterGroup space = new FilterGroup("space", tr("filter.space", "Space"), List.of(
                new FilterGroup.Filter("full", tr("filter.full", "Full")),
                new FilterGroup.Filter("free", tr("filter.free", "Has room"))));
        return TableBuilder.of(buildings.list(), b -> b.id().toString())
                .column(Column.text("name", tr("col.building", "Building"), 3), b -> Cell.text(b.label().getString()))
                .column(Column.text("type", tr("col.type", "Type"), 3),
                        b -> Cell.text(BuildingType.titleOf(b.type()).getString(), Tone.MUTED))
                .column(Column.number("use", tr("col.use", "In use"), 2), b -> use(b, workers, residents))
                .column(Column.number("quality", tr("col.quality", "Quality"), 2), b -> Cell.number(b.quality()))
                .column(Column.text("door", tr("col.door", "Door"), 2),
                        b -> Cell.text(b.door().toShortString(), Tone.MUTED))
                .tags(b -> {
                    Optional<BuildingType> t = BuildingType.get(b.type());
                    String k = t.map(x -> x.home() ? "home" : x.job().isPresent() ? "work" : "other").orElse("other");
                    int[] u = slots(b, workers, residents);
                    return u[1] <= 0 ? List.of(k) : List.of(k, u[0] >= u[1] ? "full" : "free");
                })
                .filters(kind, space)
                .sortBy("name", false)
                .empty(tr("no_buildings", "No buildings yet: use a Building Writ on a door"))
                .build();
    }

    /** Used and total places: beds for a home, job slots for a workplace, else 0/0. */
    static int[] slots(Building b, Map<UUID, Integer> workers, Map<UUID, Integer> residents) {
        Optional<BuildingType> t = BuildingType.get(b.type());
        if (t.isEmpty()) {
            return new int[] {0, 0};
        }
        if (t.get().home()) {
            return new int[] {residents.getOrDefault(b.id(), 0), b.count("beds")};
        }
        return t.get().job().<int[]>map(j -> new int[] {workers.getOrDefault(b.id(), 0),
                JobDef.get(j).map(d -> d.slots(b.quality())).orElse(0)}).orElse(new int[] {0, 0});
    }

    static Cell use(Building b, Map<UUID, Integer> workers, Map<UUID, Integer> residents) {
        int[] u = slots(b, workers, residents);
        if (u[1] <= 0) {
            return Cell.number(-1, "-", Tone.MUTED);
        }
        return Cell.number(u[0] / (double) u[1], u[0] + " / " + u[1], u[0] >= u[1] ? Tone.GOOD : Tone.NORMAL);
    }

    // ---------------------------------------------------------------- People

    static Page people(MinecraftServer server, Colony c) {
        LifeSettings life = LifeSettings.get();
        Buildings buildings = Buildings.of(c);
        List<Person> all = new ArrayList<>(Society.citizens(server, c));
        com.porcupinestudios.lotrport.colonist.Schedules schedules =
                com.porcupinestudios.lotrport.colonist.Colonists.schedules(c);
        FilterGroup status = new FilterGroup("status", tr("filter.status", "Status"), List.of(
                new FilterGroup.Filter(HOMELESS, tr("filter.homeless", "Homeless")),
                new FilterGroup.Filter(JOBLESS, tr("filter.jobless", "Jobless")),
                new FilterGroup.Filter(UNHAPPY, tr("filter.unhappy", "Unhappy")),
                new FilterGroup.Filter(HUNGRY, tr("filter.hungry", "Hungry")),
                new FilterGroup.Filter(SOLDIER, tr("filter.soldiers", "Soldiers"))));
        // traits present in the colony, for a filter group (a person has several, so not filterBy)
        java.util.TreeSet<ResourceLocation> present = new java.util.TreeSet<>();
        all.forEach(p -> present.addAll(p.get(ColonistData.TRAITS).orElse(List.of())));
        List<FilterGroup.Filter> traitFilters = new ArrayList<>();
        present.forEach(t -> traitFilters.add(new FilterGroup.Filter("trait:" + t, traitName(t))));
        return TableBuilder.of(all, p -> p.id().toString())
                .column(Column.text("name", tr("col.name", "Name"), 3), p -> Cell.text(personName(p)))
                .column(Column.text("race", tr("col.race", "Race"), 2), p -> Cell.text(title(Races.of(p.type()))))
                .column(Column.text("job", tr("col.job", "Job"), 2), p -> job(p))
                .column(Column.text("home", tr("col.home", "Home"), 3), p -> home(p, buildings))
                .column(Column.text("traits", tr("col.traits", "Traits"), 4), p -> Cell.text(String.join(", ",
                        p.get(ColonistData.TRAITS).orElse(List.of()).stream().map(t -> traitName(t).getString())
                                .toList()), Tone.MUTED))
                .column(Column.number("happiness", tr("col.mood", "Mood"), 2), p -> {
                    double h = p.get(WorkComponents.NEEDS).orElse(WorkComponents.Needs.NEW).happiness();
                    return Cell.number(h, Long.toString(Math.round(h)), happinessTone(h, life));
                })
                .tags(p -> tags(p, life))
                .tags(p -> p.get(ColonistData.TRAITS).orElse(List.of()).stream().map(t -> "trait:" + t).toList())
                .filters(status, new FilterGroup("trait", tr("filter.trait", "Trait"), traitFilters))
                .filterBy("race", tr("filter.race", "Race"), "race:", p -> Races.of(p.type()),
                        r -> Component.literal(title(r)))
                .filterBy("schedule", tr("filter.schedule", "Schedule"), "schedule:",
                        p -> com.porcupinestudios.lotrport.colonist.Colonists.scheduleOf(schedules, p).name(),
                        Component::literal)
                .sortBy("happiness", false)
                .open("citizen")
                .rowAction(Action.of("citizen", tr("open_citizen", "Citizen page")))
                .empty(tr("no_people", "Nobody lives here yet"))
                .build();
    }

    static Component traitName(ResourceLocation t) {
        return Component.translatableWithFallback("trait." + t.getNamespace() + "." + t.getPath(),
                title(t.getPath()));
    }

    static Cell job(Person p) {
        if (p.has(CoreComponents.UNIT)) {
            return Cell.text(tr("soldier", "Soldier").getString());
        }
        return p.get(WorkComponents.JOB).map(j -> Cell.text(JobDef.titleOf(j.job()).getString()))
                .orElse(Cell.text(tr("jobless", "Jobless").getString(), Tone.WARN));
    }

    static Cell home(Person p, Buildings buildings) {
        return p.get(WorkComponents.HOME).flatMap(h -> buildings.get(h.building()))
                .map(b -> Cell.text(b.label().getString()))
                .orElse(Cell.text(tr("homeless", "Homeless").getString(), Tone.WARN));
    }

    static List<String> tags(Person p, LifeSettings life) {
        List<String> tags = new ArrayList<>();
        WorkComponents.Needs n = p.get(WorkComponents.NEEDS).orElse(WorkComponents.Needs.NEW);
        boolean soldier = p.has(CoreComponents.UNIT);
        if (soldier) {
            tags.add(SOLDIER);
        } else if (!p.has(WorkComponents.JOB)) {
            tags.add(JOBLESS);
        }
        if (!p.has(WorkComponents.HOME)) {
            tags.add(HOMELESS);
        }
        if (n.happiness() < life.leaveBelow()) {
            tags.add(UNHAPPY);
        }
        if (n.food() < life.eatBelow()) {
            tags.add(HUNGRY);
        }
        return tags;
    }

    // ---------------------------------------------------------------- Stock

    static Page stock(Colony c) {
        Map<ResourceLocation, Integer> goods = c.get(WorkComponents.STOCK).orElse(WorkComponents.Stock.EMPTY).items();
        List<Map.Entry<ResourceLocation, Integer>> list = new ArrayList<>(goods.entrySet());
        return TableBuilder.of(list, e -> e.getKey().toString())
                .column(Column.text("item", tr("col.item", "Item"), 4),
                        e -> Cell.text(BuiltInRegistries.ITEM.get(e.getKey()).getDescription().getString()))
                .column(Column.text("mod", tr("col.from", "From"), 2), e -> Cell.text(e.getKey().getNamespace(),
                        Tone.MUTED))
                .column(Column.number("count", tr("col.count", "Count"), 1), e -> Cell.number(e.getValue()))
                .sortBy("count", true)
                .empty(tr("no_stock", "The storehouses are empty"))
                .build();
    }

    // ---------------------------------------------------------------- Members

    static Page members(ServerPlayer player, Colony c) {
        MinecraftServer server = player.server;
        record Entry(UUID id, ResourceLocation role) {}
        List<Entry> entries = new ArrayList<>();
        entries.add(new Entry(c.founder(), Permissions.RULER));
        c.get(Members.TYPE).orElse(Members.NONE).roles().forEach((id, role) -> {
            if (!id.equals(c.founder())) {
                entries.add(new Entry(id, role));
            }
        });
        Map<ResourceLocation, RoleDef> roles = Permissions.roles();
        boolean canEdit = Permissions.can(player.getUUID(), c, Perm.MEMBERS);
        Component cannot = tr("no_members", "You may not change roles here");
        TableBuilder<Entry> t = TableBuilder.of(entries, e -> e.id().toString())
                .column(Column.text("player", tr("col.player", "Player"), 3), e -> Cell.text(playerName(server,
                        e.id()), e.id().equals(player.getUUID()) ? Tone.GOOD : Tone.NORMAL))
                .column(Column.text("role", tr("col.role", "Role"), 2), e -> Cell.text(roleName(roles, e.role())))
                .column(Column.number("rank", tr("col.rank", "Rank"), 1), e -> Cell.number(
                        Optional.ofNullable(roles.get(e.role())).map(RoleDef::rank).orElse(0)))
                .column(Column.text("can", tr("col.may", "May"), 5), e -> Cell.text(
                        Optional.ofNullable(roles.get(e.role())).map(r -> String.join(", ", r.permissions()))
                                .orElse(""), Tone.MUTED))
                .filterBy("role", tr("filter.role", "Role"), "role:", e -> e.role().toString(),
                        id -> Component.literal(roleName(roles, ResourceLocation.parse(id))))
                .sortBy("rank", true)
                .empty(tr("no_members_yet", "Only the ruler belongs here"));
        for (ResourceLocation id : Permissions.assignable()) {
            Component label = tr("make", "Make %s", roleName(roles, id));
            t.rowAction(canEdit ? Action.of("role:" + id, label) : Action.disabled("role:" + id, label, cannot));
        }
        Component remove = tr("remove", "Remove");
        t.rowAction(canEdit ? Action.of("remove", remove).confirmed() : Action.disabled("remove", remove, cannot));
        return t.build();
    }

    static String roleName(Map<ResourceLocation, RoleDef> roles, ResourceLocation id) {
        RoleDef def = roles.get(id);
        String name = def == null || def.name().isEmpty() ? title(id.getPath()) : def.name();
        return Component.translatableWithFallback(name, name).getString();
    }

    // ================================================================ all your colonies

    static final class ColoniesProvider implements ViewProvider {
        @Override
        public Optional<View> build(ServerPlayer player, String context, String tab) {
            MinecraftServer server = player.server;
            LifeSettings life = LifeSettings.get();
            List<Colony> mine = Colonies.get(server).all().stream()
                    .filter(c -> Permissions.isMember(player.getUUID(), c)).toList();
            Page.Table page = TableBuilder.of(mine, c -> c.id().toString())
                    .column(Column.text("name", tr("col.colony", "Colony"), 3), c -> Cell.text(c.name()))
                    .column(Column.text("role", tr("col.role", "Role"), 2), c -> Cell.text(roleName(
                            Permissions.roles(), Permissions.roleId(player.getUUID(), c).orElse(Permissions.GUEST)),
                            c.founder().equals(player.getUUID()) ? Tone.GOOD : Tone.NORMAL))
                    .column(Column.number("citizens", tr("col.citizens", "Citizens"), 2),
                            c -> Cell.number(Society.citizenCount(server, c)))
                    .column(Column.number("happiness", tr("col.happiness", "Happiness"), 2), c -> {
                        double h = com.porcupinestudios.lotrport.colony.work.Life.happiness(server, c);
                        return Cell.number(h, Long.toString(Math.round(h)), happinessTone(h, life));
                    })
                    .column(Column.number("coins", tr("col.coins", "Coins"), 2), c -> Cell.number(
                            c.get(CoreComponents.TREASURY).map(CoreComponents.Treasury::coins).orElse(0L)))
                    .column(Column.number("distance", tr("col.distance", "Distance"), 2), c -> distance(player, c))
                    .tags(c -> List.of(c.founder().equals(player.getUUID()) ? "ruled" : "member"))
                    .filters(new FilterGroup("rule", tr("filter.rule", "Yours"), List.of(
                            new FilterGroup.Filter("ruled", tr("filter.ruled", "You rule")),
                            new FilterGroup.Filter("member", tr("filter.member", "You serve")))))
                    .sortBy("distance", false)
                    .open("open")
                    .rowAction(Action.of("open", tr("manage", "Manage")))
                    .empty(tr("no_colonies", "You belong to no colony"))
                    .build();
            return Optional.of(new View(COLONIES, "", tr("all_title", "Your colonies"), List.of(), "", page,
                    Optional.empty()));
        }

        @Override
        public Result act(ServerPlayer player, String context, String tab, String action, String row, int value) {
            if (action.equals("open")) {
                return colony(player.server, row).filter(c -> Permissions.isMember(player.getUUID(), c))
                        .map(c -> Result.go(new View.Target(COLONY, c.id().toString(), PEOPLE)))
                        .orElse(Result.NONE);
            }
            return Result.NONE;
        }
    }

    static Cell distance(ServerPlayer player, Colony c) {
        if (!c.dimension().equals(player.level().dimension())) {
            return Cell.number(Double.MAX_VALUE, tr("elsewhere", "elsewhere").getString(), Tone.MUTED);
        }
        double d = Math.sqrt(c.hall().distSqr(player.blockPosition()));
        return Cell.number(d, Math.round(d) + "m", Tone.MUTED);
    }
}
