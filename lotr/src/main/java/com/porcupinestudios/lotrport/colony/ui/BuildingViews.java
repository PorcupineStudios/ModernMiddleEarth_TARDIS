package com.porcupinestudios.lotrport.colony.ui;

import static com.porcupinestudios.lotrport.colony.ui.ColonyViews.tr;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.colony.Colonies;
import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.colony.building.Building;
import com.porcupinestudios.lotrport.colony.building.BuildingType;
import com.porcupinestudios.lotrport.colony.building.Buildings;
import com.porcupinestudios.lotrport.colony.building.ColonyBuildings;
import com.porcupinestudios.lotrport.colony.members.Perm;
import com.porcupinestudios.lotrport.colony.work.LifeSettings;
import com.porcupinestudios.lotrport.ui.Cell;
import com.porcupinestudios.lotrport.ui.Column;
import com.porcupinestudios.lotrport.ui.TableBuilder;
import com.porcupinestudios.lotrport.colony.members.Permissions;
import com.porcupinestudios.lotrport.colony.tech.TechDef;
import com.porcupinestudios.lotrport.colony.work.JobDef;
import com.porcupinestudios.lotrport.colony.work.Production;
import com.porcupinestudios.lotrport.colony.work.Races;
import com.porcupinestudios.lotrport.colony.work.WorkComponents;
import com.porcupinestudios.lotrport.society.Person;
import com.porcupinestudios.lotrport.society.Society;
import com.porcupinestudios.lotrport.ui.Action;
import com.porcupinestudios.lotrport.ui.Page;
import com.porcupinestudios.lotrport.ui.Stat;
import com.porcupinestudios.lotrport.ui.Tone;
import com.porcupinestudios.lotrport.ui.View;
import com.porcupinestudios.lotrport.ui.ViewProvider;
import com.porcupinestudios.lotrport.ui.Views;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * A building's page (its plaque, RingRealms): type and quality, what the room measured, who lives or works there,
 * what its trade makes (locked recipes say which research opens them) and the type's requirements. Opened from the
 * colony view's Buildings tab, or by sneak + right-clicking the building's door with an empty hand. Context:
 * {@code <colony id>/<building id>}.
 */
public final class BuildingViews {
    public static final ResourceLocation ID = LotrPort.id("building");

    private BuildingViews() {}

    public static void register() {
        Views.register(ID, new Provider());
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(BuildingViews::onRightClick);
    }

    static View.Target target(Colony c, Building b) {
        return new View.Target(ID, c.id() + "/" + b.id(), "");
    }

    /** Sneak + right-click a building's door with an empty hand: its page. */
    public static void onRightClick(PlayerInteractEvent.RightClickBlock e) {
        if (e.getLevel().isClientSide() || e.getHand() != InteractionHand.MAIN_HAND || !e.getEntity().isShiftKeyDown()
                || !e.getItemStack().isEmpty() || !(e.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        BlockPos pos = e.getPos();
        for (Colony c : Colonies.get(player.server).all()) {
            if (!c.dimension().equals(player.level().dimension())) {
                continue;
            }
            Optional<Building> b = Buildings.of(c).atDoor(pos).or(() -> Buildings.of(c).atDoor(pos.below()));
            if (b.isPresent()) {
                if (Permissions.isMember(player.getUUID(), c)) {
                    Views.open(player, target(c, b.get()), true);
                    e.setCanceled(true);
                }
                return;
            }
        }
    }

    static final String PLAQUE = "page";
    static final String PEOPLE = "people";
    static final String WORK = "work";

    /** The colony and building a context names, if this player may see them. */
    record Found(Colony colony, Building building) {}

    static Optional<Found> find(ServerPlayer player, String context) {
        String[] parts = context.split("/");
        if (parts.length != 2) {
            return Optional.empty();
        }
        Optional<Colony> found = ColonyViews.colony(player.server, parts[0]);
        if (found.isEmpty() || !Permissions.isMember(player.getUUID(), found.get())) {
            return Optional.empty();
        }
        try {
            return Buildings.of(found.get()).get(UUID.fromString(parts[1])).map(b -> new Found(found.get(), b));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    static final class Provider implements ViewProvider {
        @Override
        public Optional<View> build(ServerPlayer player, String context, String tab) {
            Optional<Found> f = find(player, context);
            if (f.isEmpty()) {
                return Optional.empty();
            }
            Colony c = f.get().colony();
            Building b = f.get().building();
            Optional<BuildingType> type = BuildingType.get(b.type());
            boolean works = type.flatMap(BuildingType::job).flatMap(JobDef::get).isPresent();
            boolean home = type.map(BuildingType::home).orElse(false);
            List<View.Tab> tabs = new ArrayList<>();
            tabs.add(new View.Tab(PLAQUE, tr("building.tab", "Plaque")));
            tabs.add(new View.Tab(PEOPLE, home ? tr("building.tab.household", "Household")
                    : tr("building.tab.people", "People")));
            if (works) {
                tabs.add(new View.Tab(WORK, tr("building.tab.work", "Work")));
            }
            String open = tabs.stream().anyMatch(t -> t.id().equals(tab)) ? tab : PLAQUE;
            Page page = switch (open) {
                case PEOPLE -> people(player, c, b);
                case WORK -> work(player, c, type);
                default -> plaque(player, c, b, type);
            };
            return Optional.of(new View(ID, context, b.label(), tabs, open, page,
                    Optional.of(new View.Target(ColonyViews.COLONY, c.id().toString(), ColonyViews.BUILDINGS))));
        }

        @Override
        public Component denied() {
            return tr("denied", "Only members of a colony may manage it");
        }

        @Override
        public Result act(ServerPlayer player, String context, String tab, String action, String row, int value) {
            Optional<Found> f = find(player, context);
            if (f.isEmpty()) {
                return Result.NONE;
            }
            Colony c = f.get().colony();
            Building b = f.get().building();
            switch (action) {
                case "retype" -> {
                    ResourceLocation to = ResourceLocation.tryParse(row);
                    if (to == null || to.equals(b.type())) {
                        return Result.NONE;
                    }
                    return Result.say(ColonyBuildings.retype(player, b.door(), to).message());
                }
                case "measure" -> {
                    if (!Permissions.can(player.getUUID(), c, Perm.BUILD)) {
                        return Result.say(tr("building.no_build", "Only those who may build can measure it"));
                    }
                    net.minecraft.server.level.ServerLevel level = player.server.getLevel(c.dimension());
                    if (level == null) {
                        return Result.NONE;
                    }
                    if (!ColonyBuildings.recheck(level, c, b)) {
                        return new Result(Optional.of(tr("building.lost", "%s no longer stands", b.label())),
                                Optional.of(new View.Target(ColonyViews.COLONY, c.id().toString(),
                                        ColonyViews.BUILDINGS)));
                    }
                    return Result.say(tr("building.measured", "Measured %s again", b.label()));
                }
                case "citizen" -> {
                    try {
                        return Society.person(player.server, UUID.fromString(row))
                                .map(p -> Result.go(CitizenViews.target(c, p))).orElse(Result.NONE);
                    } catch (IllegalArgumentException e) {
                        return Result.NONE;
                    }
                }
                case "market" -> {
                    return Result.go(new View.Target(MarketViews.ID, c.id().toString(), "buy"));
                }
                default -> {
                    return ProductionViews.act(player, c, action, value).orElse(Result.NONE);
                }
            }
        }
    }

    // ---------------------------------------------------------------- Plaque: the room and its type

    static Page plaque(ServerPlayer player, Colony c, Building b, Optional<BuildingType> type) {
        List<Page.Section> sections = new ArrayList<>();
        List<Stat> about = new ArrayList<>();
        Stat typeStat = Stat.of(tr("building.type", "Type"), BuildingType.titleOf(b.type()).getString());
        // the other types this room fits are a drop-down here (no chat links)
        List<ResourceLocation> fits = BuildingType.fitting(b.counts());
        if (Permissions.can(player.getUUID(), c, Perm.BUILD) && fits.size() > 1) {
            List<Stat.Choice> choices = new ArrayList<>();
            for (ResourceLocation id : fits) {
                boolean researched = BuildingType.get(id).map(t -> t.researched(c)).orElse(false);
                choices.add(new Stat.Choice(id.toString(), BuildingType.titleOf(id), researched, researched
                        ? Optional.empty() : Optional.of(tr("building.unresearched", "Needs research first"))));
            }
            typeStat = typeStat.withPicker(new Stat.Picker("retype", b.type().toString(), choices));
        }
        about.add(typeStat);
        type.filter(t -> !t.description().isEmpty()).ifPresent(t -> about.add(Stat.of(
                Component.translatableWithFallback(t.description(), t.description()), "", Tone.MUTED)));
        about.add(Stat.of(tr("building.quality", "Quality"), Integer.toString(b.quality()),
                b.quality() >= 3 ? Tone.GOOD : Tone.NORMAL));
        about.add(Stat.of(tr("building.floor", "Floor"), b.floor() + " blocks"));
        about.add(Stat.of(tr("building.door", "Door"), b.door().toShortString(), Tone.MUTED));
        about.add(Stat.of(tr("building.built", "Built"), "day " + b.built() / 24000, Tone.MUTED));
        sections.add(new Page.Section(tr("building.about", "The building"), about));
        sections.add(new Page.Section(tr("building.special", "Its use"), special(player, c, b, type)));

        List<Stat> measures = new ArrayList<>();
        for (Map.Entry<String, Integer> m : new java.util.TreeMap<>(b.counts()).entrySet()) {
            if (m.getValue() > 0) {
                measures.add(Stat.of(Component.literal(ColonyViews.title(m.getKey())),
                        Integer.toString(m.getValue())));
            }
        }
        sections.add(new Page.Section(tr("building.measures", "Measured"), measures));

        if (type.isPresent() && !type.get().requires().isEmpty()) {
            List<Stat> needs = new ArrayList<>();
            for (BuildingType.Requirement r : type.get().requires()) {
                boolean ok = r.holds(b.counts());
                needs.add(Stat.of(r.why(), ok ? "met" : "missing", ok ? Tone.GOOD : Tone.BAD));
            }
            sections.add(new Page.Section(tr("building.needs", "What the type needs"), needs));
        }
        List<Action> buttons = new ArrayList<>();
        buttons.add(Permissions.can(player.getUUID(), c, Perm.BUILD)
                ? Action.of("measure", tr("building.measure", "Measure again"))
                : Action.disabled("measure", tr("building.measure", "Measure again"),
                        tr("building.no_build", "Only those who may build can measure it")));
        if (b.type().getPath().equals("market")) {
            buttons.add(Action.of("market", tr("building.open_market", "Open the market")));
        }
        return new Page.Stats(sections, buttons);
    }

    /** What this kind of building does for the colony, in a few lines. */
    static List<Stat> special(ServerPlayer player, Colony c, Building b, Optional<BuildingType> type) {
        List<Stat> out = new ArrayList<>();
        String kind = b.type().getPath();
        int living = 0;
        int workers = 0;
        for (Person p : Society.citizens(player.server, c)) {
            living += p.get(WorkComponents.HOME).map(h -> h.building().equals(b.id())).orElse(false) ? 1 : 0;
            workers += p.get(WorkComponents.JOB).map(j -> j.building().equals(b.id())).orElse(false) ? 1 : 0;
        }
        if (type.map(BuildingType::home).orElse(false)) {
            int beds = b.count("beds");
            out.add(Stat.of(tr("building.beds", "Beds taken"), living + " / " + beds,
                    living >= beds ? Tone.GOOD : Tone.NORMAL).withBar(beds == 0 ? 0 : living / (double) beds));
            if (kind.equals("barracks")) {
                out.add(Stat.of(tr("building.soldiers", "Soldiers of the colony"),
                        Integer.toString(Society.soldiers(player.server, c).size())));
            }
        }
        Optional<JobDef> job = type.flatMap(BuildingType::job).flatMap(JobDef::get);
        if (job.isPresent()) {
            int slots = job.get().slots(b.quality());
            out.add(Stat.of(tr("building.workers", "Workers"), workers + " / " + slots,
                    workers == 0 ? Tone.WARN : Tone.NORMAL).withBar(slots == 0 ? 0 : workers / (double) slots));
            WorkComponents.Economy eco = c.get(WorkComponents.ECONOMY).orElse(WorkComponents.Economy.DEFAULT);
            out.add(Stat.of(tr("building.wage", "Wage"), Math.max(0, job.get().wage() + eco.wageBonus())
                    + "c a day each"));
            if (job.get().tavern()) {
                out.add(Stat.of(tr("building.tavern", "Citizens with coins buy a drink each day and are happier"),
                        "", Tone.MUTED));
            }
        }
        if (kind.equals("storehouse")) {
            int waiting = 0;
            for (int n : c.get(WorkComponents.STOCK).orElse(WorkComponents.Stock.EMPTY).items().values()) {
                waiting += n;
            }
            out.add(Stat.of(tr("building.chests", "Chests"), Integer.toString(b.count("chests"))));
            out.add(Stat.of(tr("building.waiting", "Goods waiting to be put away"), Integer.toString(waiting),
                    waiting > 0 ? Tone.WARN : Tone.MUTED));
        }
        if (kind.equals("library")) {
            out.add(Stat.of(tr("building.research", "Research completed"), Integer.toString(
                    com.porcupinestudios.lotrport.colony.tech.Techs.state(player.server, c).techs().size())));
        }
        if (out.isEmpty()) {
            out.add(Stat.of(tr("building.nothing_special", "Nothing more to note"), "", Tone.MUTED));
        }
        return out;
    }

    // ---------------------------------------------------------------- People / Household

    static Page people(ServerPlayer player, Colony c, Building b) {
        List<Person> here = new ArrayList<>();
        for (Person p : Society.citizens(player.server, c)) {
            boolean lives = p.get(WorkComponents.HOME).map(h -> h.building().equals(b.id())).orElse(false);
            boolean works = p.get(WorkComponents.JOB).map(j -> j.building().equals(b.id())).orElse(false);
            if (lives || works) {
                here.add(p);
            }
        }
        LifeSettings life = LifeSettings.get();
        return TableBuilder.of(here, p -> p.id().toString())
                .column(Column.text("name", tr("col.name", "Name"), 3), p -> Cell.text(ColonyViews.personName(p)))
                .column(Column.text("race", tr("col.race", "Race"), 2),
                        p -> Cell.text(ColonyViews.title(Races.of(p.type())), Tone.MUTED))
                .column(Column.text("here", tr("col.here", "Here"), 3), p -> {
                    Optional<WorkComponents.Job> j = p.get(WorkComponents.JOB)
                            .filter(x -> x.building().equals(b.id()));
                    boolean lives = p.get(WorkComponents.HOME).map(h -> h.building().equals(b.id())).orElse(false);
                    String s = j.map(x -> JobDef.titleOf(x.job()).getString()).orElse("");
                    return Cell.text(s.isEmpty() ? "Lives here" : lives ? s + ", lives here" : s);
                })
                .column(Column.number("skill", tr("col.skill", "Skill"), 2), p -> p.get(WorkComponents.JOB)
                        .filter(x -> x.building().equals(b.id())).map(x -> Cell.number(Math.round(x.skill())))
                        .orElse(Cell.number(-1, "-", Tone.MUTED)))
                .column(Column.number("happiness", tr("col.happiness", "Happiness"), 2), p -> {
                    double h = p.get(WorkComponents.NEEDS).orElse(WorkComponents.Needs.NEW).happiness();
                    return Cell.number(h, Long.toString(Math.round(h)), ColonyViews.happinessTone(h, life));
                })
                .open("citizen")
                .rowAction(Action.of("citizen", tr("open_citizen", "Citizen page")))
                .sortBy("name", false)
                .empty(tr("building.empty", "Nobody lives or works here"))
                .build();
    }

    // ---------------------------------------------------------------- Work: what it makes, orders, tool

    static Page work(ServerPlayer player, Colony c, Optional<BuildingType> type) {
        ResourceLocation jobId = type.flatMap(BuildingType::job).orElseThrow();
        JobDef job = JobDef.get(jobId).orElseThrow();
        List<Page.Section> sections = new ArrayList<>();
        List<Stat> makes = new ArrayList<>();
        for (JobDef.Recipe r : job.recipes()) {
            String outs = items(r.produces().entrySet().stream().collect(java.util.stream.Collectors.toMap(
                    Map.Entry::getKey, x -> (int) Math.round(x.getValue()))));
            String ins = r.consumes().isEmpty() ? "from the land" : "from " + items(r.consumes());
            boolean locked = r.requires().isPresent() && !Production.unlocked.test(c, r.requires().get());
            makes.add(Stat.of(Component.literal(outs), locked ? "needs research: "
                    + TechDef.titleOf(r.requires().get()) : ins, locked ? Tone.MUTED : Tone.NORMAL));
        }
        if (makes.isEmpty()) {
            makes.add(Stat.of(tr("building.makes_nothing", "This trade makes nothing to keep"), "", Tone.MUTED));
        }
        sections.add(new Page.Section(tr("building.makes", "What it makes"), makes));

        Production.Plan plan = Production.plan(player.server, c);
        Map<ResourceLocation, Integer> stock = c.get(WorkComponents.STOCK).orElse(WorkComponents.Stock.EMPTY).items();
        boolean may = Permissions.can(player.getUUID(), c, Perm.PRODUCTION);
        List<Stat> plans = new ArrayList<>();
        for (Map.Entry<ResourceLocation, ResourceLocation> e : Production.makeable().entrySet()) {
            if (!e.getValue().equals(jobId)) {
                continue;
            }
            ResourceLocation item = e.getKey();
            String name = ProductionViews.name(item);
            int order = plan.order(item);
            int target = plan.targets().getOrDefault(item, 0);
            if (may) {
                plans.add(CaravanViews.stepper(name + ": work order", "order:" + item, order, 0,
                        ProductionViews.MAX));
                plans.add(CaravanViews.stepper(name + ": keep in stock (" + stock.getOrDefault(item, 0) + " now)",
                        "target:" + item, target, 0, ProductionViews.MAX));
            } else {
                plans.add(Stat.of(Component.literal(name), order + " ordered, keep " + target));
            }
        }
        if (!plans.isEmpty()) {
            sections.add(new Page.Section(tr("building.plans", "Orders and stock targets"), plans));
        }
        if (job.tool().isPresent()) {
            double w = plan.wear().getOrDefault(jobId, 0.0);
            sections.add(new Page.Section(tr("building.tool", "Tools"), List.of(Stat.of(Component.literal(
                    ProductionViews.name(job.tool().get())), w >= 1 ? "needs a new one"
                    : Math.round((1 - w) * 100) + "% left", w >= 1 ? Tone.WARN : Tone.NORMAL)
                    .withBar(Math.max(0, 1 - w)))));
        }
        return new Page.Stats(sections, List.of());
    }

    static String items(Map<ResourceLocation, Integer> m) {
        List<String> out = new ArrayList<>();
        m.forEach((k, v) -> out.add(v + " " + ProductionViews.name(k)));
        return String.join(", ", out);
    }
}
