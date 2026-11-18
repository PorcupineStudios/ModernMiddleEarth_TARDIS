package com.porcupinestudios.lotrport.colony.book;

import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.colony.building.Building;
import com.porcupinestudios.lotrport.colony.building.BuildingType;
import com.porcupinestudios.lotrport.colony.building.Buildings;
import com.porcupinestudios.lotrport.colony.market.Market;
import com.porcupinestudios.lotrport.colony.market.Research;
import com.porcupinestudios.lotrport.colony.members.Perm;
import com.porcupinestudios.lotrport.colony.members.Permissions;
import com.porcupinestudios.lotrport.colony.tech.TechDef;
import com.porcupinestudios.lotrport.colony.work.JobDef;
import com.porcupinestudios.lotrport.colony.work.LifeSettings;
import com.porcupinestudios.lotrport.colony.work.Production;
import com.porcupinestudios.lotrport.colony.work.Races;
import com.porcupinestudios.lotrport.colony.work.Stores;
import com.porcupinestudios.lotrport.colony.work.WorkComponents;
import com.porcupinestudios.lotrport.society.CoreComponents;
import com.porcupinestudios.lotrport.society.Person;
import com.porcupinestudios.lotrport.society.Society;
import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * A building's plaque (RingRealms): the left page is the building at a glance (type, quality, size, who is inside);
 * the tabs depend on what it is: a home lists its household, a workplace its workers, what they make, its tools and
 * its chests, a storehouse its goods, a library its researchers and the colony's research, a market its prices, a
 * barracks its soldiers; every building has its Room tab (what the type needs, met or not). Context:
 * {@code <colony>/<building>}.
 */
public final class PlaqueBook implements BookProvider {
    public static final String KIND = "plaque";
    static final ResourceLocation STOREHOUSE = Stores.STOREHOUSE;
    static final ResourceLocation LIBRARY = com.porcupinestudios.lotrport.LotrPort.id("library");
    static final ResourceLocation MARKET = com.porcupinestudios.lotrport.LotrPort.id("market");
    static final ResourceLocation BARRACKS = com.porcupinestudios.lotrport.LotrPort.id("barracks");
    static final int GRID_MAX = 48;

    private PlaqueBook() {}

    static void register() {
        Books.register(KIND, new PlaqueBook());
    }

    public static String context(Colony c, Building b) {
        return c.id() + "/" + b.id();
    }

    record At(Colony colony, Building building) {}

    static Optional<At> find(MinecraftServer server, String context) {
        String[] p = context.split("/");
        if (p.length != 2) {
            return Optional.empty();
        }
        Optional<Colony> c = Pages.colony(server, p[0]);
        if (c.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Buildings.of(c.get()).get(UUID.fromString(p[1])).map(b -> new At(c.get(), b));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** A building type's picture (RingRealms' icons, {@code textures/buildings/<type>.png}), drawn in icon grids. */
    static String typeIcon(ResourceLocation type) {
        return "tex:" + type.getNamespace() + ":textures/buildings/" + type.getPath() + ".png";
    }

    /** The building's icon: what it is about (its trade's first output, or by type). */
    static String icon(Building b) {
        Optional<BuildingType> t = BuildingType.get(b.type());
        if (t.isPresent() && t.get().home()) {
            return "minecraft:red_bed";
        }
        if (b.type().equals(STOREHOUSE)) {
            return "minecraft:chest";
        }
        if (b.type().equals(LIBRARY)) {
            return "minecraft:bookshelf";
        }
        if (b.type().equals(MARKET)) {
            return "minecraft:emerald";
        }
        if (b.type().equals(BARRACKS)) {
            return "minecraft:iron_sword";
        }
        return t.flatMap(BuildingType::job).flatMap(JobDef::get).flatMap(j -> j.recipes().stream().findFirst())
                .flatMap(r -> r.produces().keySet().stream().findFirst()).map(ResourceLocation::toString)
                .orElse("minecraft:oak_door");
    }

    /** The context of an unmarked plaque (no building yet) at a block. */
    public static String unmarked(BlockPos pos) {
        return UNMARKED + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    static final String UNMARKED = "unmarked:";

    static Optional<BlockPos> unmarkedPos(String context) {
        if (!context.startsWith(UNMARKED)) {
            return Optional.empty();
        }
        String[] p = context.substring(UNMARKED.length()).split(",");
        try {
            return p.length == 3 ? Optional.of(new BlockPos(Integer.parseInt(p[0]), Integer.parseInt(p[1]),
                    Integer.parseInt(p[2]))) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * An unmarked plaque's book (RingRealms' plaque screen before a type is picked): the left page is the room as
     * measured at the nearest door, or why it can't be; the Type page lists every building type with each of its
     * requirements ticked or crossed for this room, and makes it any type that fits.
     */
    static Optional<Book> unmarkedBook(ServerPlayer player, BlockPos pos) {
        if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64 * 64) {
            return Optional.empty();
        }
        var level = player.serverLevel();
        String context = unmarked(pos);
        Optional<Colony> colony = com.porcupinestudios.lotrport.colony.building.ColonyBuildings.colonyFor(player,
                level, pos);
        Optional<BlockPos> door = com.porcupinestudios.lotrport.colony.building.ColonyBuildings.plaqueDoor(level, pos);
        List<El> left = new ArrayList<>();
        left.add(El.pair("Plaque", "Unmarked", El.MUTED).withItem("lotr_port:building_plaque"));
        Map<String, Integer> counts = Map.of();
        boolean measured = false;
        if (colony.isEmpty()) {
            left.add(El.pair("Colony", "none", El.BAD).withTip("Hang it within "
                    + com.porcupinestudios.lotrport.colony.building.BuildingSettings.get().reach()
                    + " blocks of the Hall of a colony you may build for"));
        } else {
            left.add(El.pair("Colony", colony.get().name()));
        }
        if (door.isEmpty()) {
            left.add(El.pair("Door", "none near", El.BAD).withTip("Hang the plaque in a room with a door within "
                    + com.porcupinestudios.lotrport.colony.building.BuildingSettings.get().plaqueDoorReach()
                    + " blocks, or by the door outside"));
        } else {
            left.add(El.pair("Door", door.get().toShortString(), El.MUTED));
            var dw = com.porcupinestudios.lotrport.colony.building.ColonyBuildings.doorway(level, door.get());
            if (dw.isPresent()) {
                var room = com.porcupinestudios.lotrport.colony.building.ColonyBuildings.measure(level, dw.get(),
                        player.blockPosition());
                if (!room.ok()) {
                    left.add(El.pair("Room", "can't be used", El.BAD).withTip(room.problem().getString()));
                } else {
                    measured = true;
                    counts = room.counts();
                    left.add(El.pair("Floor", room.count("floor") + " blocks"));
                    left.add(El.pair("Storeys", Integer.toString(Math.max(1, room.count("storeys")))));
                    for (Map.Entry<String, Integer> m : new TreeMap<>(counts).entrySet()) {
                        if (m.getValue() > 0 && !m.getKey().equals("floor") && !m.getKey().equals("storeys")) {
                            left.add(El.pair(Pages.title(m.getKey()), Integer.toString(m.getValue()), El.MUTED));
                        }
                    }
                }
            }
        }
        boolean may = colony.isPresent();
        List<El> right = new ArrayList<>();
        right.add(El.head("What this room can be"));
        List<El> cells = new ArrayList<>();
        Map<String, Integer> have = counts;
        List<Map.Entry<ResourceLocation, BuildingType>> types = new ArrayList<>(BuildingType.all().entrySet());
        types.sort(java.util.Comparator.comparingInt((Map.Entry<ResourceLocation, BuildingType> e)
                -> -e.getValue().priority()).thenComparing(e -> e.getKey().toString()));
        for (Map.Entry<ResourceLocation, BuildingType> e : types) {
            BuildingType t = e.getValue();
            boolean known = colony.map(t::researched).orElse(false);
            StringBuilder tip = new StringBuilder();
            for (BuildingType.Requirement r : t.requires()) {
                tip.append(measured && r.holds(have) ? "\u2714 " : "\u2718 ").append(r.why().getString()).append('\n');
            }
            if (!known) {
                tip.append("Needs research first");
            }
            boolean fits = measured && t.missing(have).isEmpty();
            boolean can = fits && known && may;
            if (can) {
                tip.append("Click to make it a ").append(Pages.building(e.getKey()));
            }
            cells.add(El.cell(typeIcon(e.getKey()), Pages.building(e.getKey()), fits && known ? El.NORMAL : El.MUTED,
                    tip.toString().trim(), can ? "assign" : "", e.getKey().toString()));
        }
        right.add(El.grid(cells));
        right.add(El.text("Hover a type for what it needs; click one that fits to make the room it.", El.MUTED));
        right.add(El.buttons(List.of(El.button("Measure again", "measure", ""))));
        List<Book.Tab> tabs = List.of(new Book.Tab("type", "Type", "minecraft:oak_door"));
        return Optional.of(new Book(KIND, context, "Unmarked plaque", colony.map(Colony::name).orElse(""), List.of(),
                tabs, "type", left, right, "", 0));
    }

    @Override
    public Optional<Book> build(ServerPlayer player, String context, String tab) {
        Optional<BlockPos> plaque = unmarkedPos(context);
        if (plaque.isPresent()) {
            return unmarkedBook(player, plaque.get());
        }
        Optional<At> at = find(player.server, context);
        if (at.isEmpty()) {
            return Optional.empty();
        }
        if (!Permissions.isMember(player.getUUID(), at.get().colony())) {
            // someone else's building: what it is and whose, nothing more (was a line on the action bar)
            Colony fc = at.get().colony();
            Building fb = at.get().building();
            List<El> left = List.of(El.pair("Colony", fc.name()), El.pair("Type", Pages.building(fb.type()))
                    .withItem(icon(fb)), El.gap(), El.text("You are not one of this colony's members.", El.MUTED));
            return Optional.of(new Book(KIND, context, fb.label(), fc.name(), List.of(), List.of(), "",
                    left, List.of(), "", 0));
        }
        MinecraftServer server = player.server;
        Colony c = at.get().colony();
        Building b = at.get().building();
        Optional<BuildingType> type = BuildingType.get(b.type());
        boolean home = type.map(BuildingType::home).orElse(false);
        Optional<ResourceLocation> jobId = type.flatMap(BuildingType::job);
        Optional<JobDef> job = jobId.flatMap(JobDef::get);

        List<Person> residents = new ArrayList<>();
        List<Person> workers = new ArrayList<>();
        for (Person p : Society.citizens(server, c)) {
            if (p.get(WorkComponents.HOME).map(h -> h.building().equals(b.id())).orElse(false)) {
                residents.add(p);
            }
            if (p.get(WorkComponents.JOB).map(j -> j.building().equals(b.id())).orElse(false)) {
                workers.add(p);
            }
        }

        // tabs by what the building is
        List<Book.Tab> tabs = new ArrayList<>();
        if (home) {
            tabs.add(new Book.Tab("household", "Household", "minecraft:red_bed"));
        }
        if (job.isPresent()) {
            tabs.add(new Book.Tab("work", "Work", "minecraft:iron_pickaxe"));
            if (!job.get().recipes().isEmpty()) {
                tabs.add(new Book.Tab("goods", "Goods", "minecraft:crafting_table"));
            }
        }
        if (b.type().equals(LIBRARY)) {
            tabs.add(new Book.Tab("research", "Research", "minecraft:book"));
        }
        if (b.type().equals(MARKET)) {
            tabs.add(new Book.Tab("prices", "Prices", "minecraft:emerald"));
        }
        if (b.type().equals(BARRACKS)) {
            tabs.add(new Book.Tab("soldiers", "Soldiers", "minecraft:iron_sword"));
        }
        if (!home) {
            tabs.add(new Book.Tab("stores", "Stores", "minecraft:chest"));
        }
        tabs.add(new Book.Tab("room", "Room", "minecraft:oak_door"));
        tabs.add(new Book.Tab("type", "Type", "lotr_port:building_plaque"));
        String open = tabs.stream().anyMatch(t -> t.id().equals(tab)) ? tab : tabs.get(0).id();

        // left page: the building at a glance (RingRealms' plaque overview), in boxes by subject
        List<El> left = new ArrayList<>();
        boolean mayBuild = Permissions.can(player.getUUID(), c, Perm.BUILD);
        left.add(El.field("Name", b.name().orElse(""), "rename", mayBuild, mayBuild ? "Name it (empty: its type's name)"
                : "Only those who may build can name it"));
        left.add(El.head("The building"));
        left.add(El.pair("Type", Pages.building(b.type())).withItem(icon(b)));
        int q = b.quality();
        left.add(El.meter("Quality", q + " / 100", q / 100.0).withTip("More floor, lights, chests and tables raise it."
                + " Better rooms make happier homes and more worker places."));
        left.add(El.pair("Floor", b.floor() + " blocks"));
        left.add(El.pair("Storeys", Integer.toString(Math.max(1, b.count("storeys")))));
        if (home || job.isPresent()) {
            left.add(El.head(home ? "Household" : "Work"));
        }
        if (home) {
            int beds = b.count("beds");
            left.add(El.meter("Beds", residents.size() + " of " + beds, beds == 0 ? 0 : residents.size() / (double) beds));
            LifeSettings s = LifeSettings.get();
            left.add(El.pair("Comfort", "+" + Math.round(s.homeHappiness() + q * s.homeQualityFactor()) + " mood",
                    El.GOOD).withTip("What living here adds to a resident's happiness"));
        }
        if (job.isPresent()) {
            int slots = job.get().slots(q);
            left.add(El.meter(Pages.job(jobId.get()) + "s", workers.size() + " of " + slots,
                    slots == 0 ? 0 : workers.size() / (double) slots));
            left.add(El.pair("Wage", Pages.coins(job.get().wage()) + " a day"));
        }
        left.add(El.head("Where"));
        left.add(El.pair("Door", b.door().toShortString(), El.MUTED));
        left.add(El.pair("Built", "day " + b.built() / 24000, El.MUTED));

        List<El> right = switch (open) {
            case "household" -> household(residents);
            case "work" -> work(server, c, b, jobId.get(), job.get(), workers);
            case "goods" -> goods(server, player, c, jobId.get(), job.get());
            case "research" -> research(server, c);
            case "prices" -> prices(server, c, player.serverLevel(), b);
            case "soldiers" -> soldiers(server, c);
            case "stores" -> stores(server, c, b);
            case "type" -> types(player, c, b);
            default -> room(player, c, b, type, job);
        };
        String sub = c.name() + (b.name().isPresent() ? " - " + Pages.building(b.type()) : "");
        List<El> chips = new ArrayList<>();
        if (home) {
            chips.add(El.chip("minecraft:red_bed", residents.size() + "/" + b.count("beds"), "Residents / beds"));
        }
        if (job.isPresent()) {
            chips.add(El.chip("minecraft:iron_pickaxe", workers.size() + "/" + job.get().slots(q), "Workers / places"));
        }
        chips.add(El.chip("minecraft:nether_star", Integer.toString(q), "Quality"));
        return Optional.of(new Book(KIND, context, b.name().orElse(Pages.building(b.type())), sub, chips, tabs, open, left, right, "", 0));
    }

    static List<El> household(List<Person> residents) {
        List<El> out = new ArrayList<>();
        if (residents.isEmpty()) {
            out.add(El.text("Nobody lives here yet. Free beds go to the homeless at dawn."));
            return out;
        }
        out.add(El.head("Who lives here"));
        for (Person p : residents) {
            out.add(Pages.person(p, ""));
        }
        return out;
    }

    static List<El> work(MinecraftServer server, Colony c, Building b, ResourceLocation jobId, JobDef job,
            List<Person> workers) {
        List<El> out = new ArrayList<>();
        out.add(El.head("Workers"));
        if (workers.isEmpty()) {
            out.add(El.text("Nobody works here. Idle citizens are put to work at dawn; better rooms take more."));
        }
        for (Person p : workers) {
            WorkComponents.Job j = p.get(WorkComponents.JOB).orElseThrow();
            WorkComponents.Needs n = p.get(WorkComponents.NEEDS).orElse(WorkComponents.Needs.NEW);
            double prof = job.proficiency(Races.of(p.type()));
            String sub = Pages.race(p) + (prof > 1 ? " - gifted (x" + prof + ")" : prof < 1 ? " - slow (x" + prof + ")"
                    : "") + (n.unpaid() > 0 ? " - ON STRIKE" : "");
            out.add(El.card(Pages.face(p), Pages.name(p), sub, n.unpaid() > 0 ? El.BAD : El.NORMAL, "citizen",
                    p.id().toString(), List.of(El.meter("Skill", Math.round(j.skill()) + "", j.skill() / 100.0),
                            El.meter("Mood", Math.round(n.happiness()) + "", n.happiness() / 100.0))));
        }
        Production.Output output = Production.output(server, c);
        Map<ResourceLocation, Integer> made = output.made().getOrDefault(b.id(), Map.of());
        out.add(El.head("Made on the last day"));
        if (made.isEmpty()) {
            out.add(El.text(job.carries() > 0 ? "Couriers carry goods to the storehouses." : "Nothing yet."));
        } else {
            List<El> grid = new ArrayList<>();
            made.forEach((k, v) -> grid.add(El.stack(k.toString(), v)));
            out.add(El.items(grid));
        }
        if (job.tool().isPresent()) {
            ResourceLocation t = job.tool().get();
            double w = Production.plan(server, c).wear().getOrDefault(jobId, 0.0);
            out.add(El.head("Tools"));
            out.add(El.meter(Pages.item(t), w >= 1 ? "worn out" : Math.round((1 - w) * 100) + "% left",
                    Math.max(0, 1 - w)).withItem(t.toString()).withTip("One tool lasts " + job.toolDays()
                    + " worker-days; without one they make " + Math.round(job.noToolFactor() * 100) + "%"));
        }
        return out;
    }

    static List<El> goods(MinecraftServer server, ServerPlayer player, Colony c, ResourceLocation jobId, JobDef job) {
        List<El> out = new ArrayList<>();
        Production.Plan plan = Production.plan(server, c);
        boolean may = Permissions.can(player.getUUID(), c, Perm.PRODUCTION);
        String why = may ? "" : "Only those who may direct production can";
        out.add(El.text("Orders are made first, then whatever is furthest below its target. With targets set and met, "
                + "the trade rests."));
        for (JobDef.Recipe r : job.recipes()) {
            boolean locked = r.requires().isPresent() && !Production.unlocked.test(c, r.requires().get());
            for (Map.Entry<ResourceLocation, Double> o : r.produces().entrySet()) {
                ResourceLocation item = o.getKey();
                String from = r.consumes().isEmpty() ? "from the land" : "from " + Pages.items(r.consumes());
                out.add(El.pair(Pages.item(item), locked ? "needs " + TechDef.titleOf(r.requires().get()) : from,
                        locked ? El.MUTED : El.NORMAL).withItem(item.toString()));
                if (!locked) {
                    int order = plan.order(item);
                    int target = plan.targets().getOrDefault(item, 0);
                    out.add(El.buttons(List.of(
                            El.stepper("Order", order == 0 ? "-" : Integer.toString(order), "order", item.toString(),
                                    order, 0, 4096, 1, may, why),
                            El.stepper("Keep", target == 0 ? "-" : Integer.toString(target), "target", item.toString(),
                                    target, 0, 4096, 1, may, why))));
                }
            }
        }
        return out;
    }

    static List<El> research(MinecraftServer server, Colony c) {
        List<El> out = new ArrayList<>();
        out.add(El.head("Research points"));
        Map<ResourceLocation, Double> perDay = Research.perDay(server, c);
        for (ResourceLocation tier : Research.tiers().keySet()) {
            double pts = Research.points(server, c, tier);
            out.add(El.pair(Research.name(tier), (int) Math.floor(pts) + "  (+" + String.format(java.util.Locale.ROOT,
                    "%.1f", perDay.getOrDefault(tier, 0.0)) + " a day)").withItem("minecraft:book"));
        }
        out.add(El.head("Researchers"));
        List<Person> rs = Research.researchers(server, c);
        if (rs.isEmpty()) {
            out.add(El.text("No researchers. A library takes them; set their work in the Town Hall ledger."));
        }
        for (Person p : rs) {
            out.add(Pages.person(p, ""));
        }
        return out;
    }

    static List<El> prices(MinecraftServer server, Colony c, ServerLevel level, Building b) {
        List<El> out = new ArrayList<>();
        Market.State st = Market.state(server, c);
        out.add(El.pair("Market level", st.level() + " of " + Market.settings().maxLevel()));
        out.add(El.text("Right-click a store here to trade. Prices rise as stock runs low."));
        out.add(El.head("Prices"));
        Stores stores = new Stores(server, level, c);
        int shown = 0;
        for (ResourceLocation item : Market.settings().prices().keySet()) {
            if (shown++ >= 24) {
                break;
            }
            int stock = stores.count(item, null);
            out.add(El.pair(Pages.item(item), "buy " + Market.buyCost(item, stock, 1) + "  sell "
                    + Market.sellPay(item, stock, 1) + "  (" + stock + " held)").withItem(item.toString()));
        }
        return out;
    }

    static List<El> soldiers(MinecraftServer server, Colony c) {
        List<El> out = new ArrayList<>();
        List<Person> s = Society.soldiers(server, c);
        if (s.isEmpty()) {
            out.add(El.text("No soldiers. Station hired units in the colony to make them its guard."));
        }
        for (Person p : s) {
            String sub = p.get(CoreComponents.UNIT).map(u -> u.order().name().toLowerCase(java.util.Locale.ROOT)
                    + " - " + u.wage() + " a day").orElse("");
            out.add(El.card(Pages.face(p), Pages.name(p), Pages.race(p) + " - " + sub, El.NORMAL, "", "", List.of()));
        }
        return out;
    }

    static List<El> stores(MinecraftServer server, Colony c, Building b) {
        List<El> out = new ArrayList<>();
        Stores stores = new Stores(server, server.getLevel(c.dimension()), c);
        List<IItemHandler> chests = stores.chests(b);
        Map<ResourceLocation, Integer> held = new TreeMap<>();
        int used = 0;
        int slots = 0;
        for (IItemHandler h : chests) {
            for (int i = 0; i < h.getSlots(); i++) {
                ItemStack st = h.getStackInSlot(i);
                slots++;
                if (!st.isEmpty()) {
                    used++;
                    held.merge(BuiltInRegistries.ITEM.getKey(st.getItem()), st.getCount(), Integer::sum);
                }
            }
        }
        if (chests.isEmpty()) {
            out.add(El.text("No chests here (or the building isn't loaded)."));
            return out;
        }
        out.add(El.meter("Space", used + " of " + slots + " slots", slots == 0 ? 0 : 1 - used / (double) slots));
        if (held.isEmpty()) {
            out.add(El.text("Empty."));
            return out;
        }
        List<El> grid = new ArrayList<>();
        Map<ResourceLocation, Integer> sorted = new LinkedHashMap<>();
        held.entrySet().stream().sorted(Map.Entry.<ResourceLocation, Integer>comparingByValue().reversed())
                .forEach(e -> sorted.put(e.getKey(), e.getValue()));
        sorted.forEach((k, v) -> {
            if (grid.size() < GRID_MAX) {
                grid.add(El.stack(k.toString(), v));
            }
        });
        out.add(El.items(grid));
        if (sorted.size() > GRID_MAX) {
            out.add(El.text("and " + (sorted.size() - GRID_MAX) + " more kinds"));
        }
        return out;
    }

    /**
     * The Type page of a built plaque (RingRealms' "What is this building?"): every type as its icon, the current one
     * marked, faded where this room can't be one; hover lists what it needs ticked or crossed, click changes it.
     */
    static List<El> types(ServerPlayer player, Colony c, Building b) {
        List<El> out = new ArrayList<>();
        out.add(El.head("What is this building?"));
        boolean may = Permissions.can(player.getUUID(), c, Perm.BUILD);
        List<Map.Entry<ResourceLocation, BuildingType>> all = new ArrayList<>(BuildingType.all().entrySet());
        all.sort(java.util.Comparator.comparingInt((Map.Entry<ResourceLocation, BuildingType> e)
                -> -e.getValue().priority()).thenComparing(e -> e.getKey().toString()));
        List<El> cells = new ArrayList<>();
        for (Map.Entry<ResourceLocation, BuildingType> e : all) {
            BuildingType t = e.getValue();
            boolean now = e.getKey().equals(b.type());
            boolean known = t.researched(c);
            boolean fits = t.missing(b.counts()).isEmpty();
            StringBuilder tip = new StringBuilder();
            for (BuildingType.Requirement r : t.requires()) {
                tip.append(r.holds(b.counts()) ? "\u2714 " : "\u2718 ").append(r.why().getString()).append('\n');
            }
            if (t.requires().isEmpty()) {
                tip.append("Any closed room will do.\n");
            }
            if (!known) {
                tip.append("\u2718 Needs research first\n");
            }
            if (now) {
                tip.append("What it is now");
            } else if (fits && known && may) {
                tip.append("Click to make it one (frees the people working here)");
            } else if (!may) {
                tip.append("Only those who may build can change it");
            }
            boolean can = !now && fits && known && may;
            cells.add(El.cell(typeIcon(e.getKey()), Pages.building(e.getKey()),
                    now ? El.GOOD : fits && known ? El.NORMAL : El.MUTED, tip.toString().trim(),
                    can ? "retype" : "", e.getKey().toString()));
        }
        out.add(El.grid(cells));
        out.add(El.text("Hover a type to see what it needs. Faded: this room can't be one yet.", El.MUTED));
        return out;
    }

    static List<El> room(ServerPlayer player, Colony c, Building b, Optional<BuildingType> type,
            Optional<JobDef> job) {
        List<El> out = new ArrayList<>();
        out.add(El.head("What a " + Pages.building(b.type())
                + " needs"));
        if (type.isEmpty() || type.get().requires().isEmpty()) {
            out.add(El.text("Nothing more than a room."));
        } else {
            for (BuildingType.Requirement r : type.get().requires()) {
                int have = b.count(r.count());
                int need = r.min() + r.per().map(p -> r.each() * b.count(p)).orElse(0);
                boolean ok = r.holds(b.counts());
                out.add(El.pair(r.why().getString(), have + " of " + need, ok ? El.GOOD : El.BAD));
            }
        }
        out.add(El.head("Measured"));
        for (Map.Entry<String, Integer> m : new TreeMap<>(b.counts()).entrySet()) {
            if (m.getValue() > 0) {
                out.add(El.pair(Pages.title(m.getKey()), Integer.toString(m.getValue()), El.MUTED));
            }
        }
        out.add(El.head("Quality"));
        int q = b.quality();
        out.add(El.meter("Quality", q + " / 100", q / 100.0).withTip("More floor, lights, chests and tables raise it"));
        job.ifPresent(j -> out.add(El.text("Workers here: up to " + j.slots(q) + " at this quality.", El.MUTED)));
        out.add(El.text("Another type? See the Type ribbon.", El.MUTED));
        out.add(El.buttons(List.of(El.button("Measure again", "measure", ""))));
        return out;
    }

    @Override
    public Result act(ServerPlayer player, String context, String tab, String action, String arg, int num) {
        Optional<BlockPos> plaque = unmarkedPos(context);
        if (plaque.isPresent()) {
            if (!action.equals("assign")) {
                return Result.NONE; // "measure": the book is built again
            }
            ResourceLocation type = ResourceLocation.tryParse(arg);
            if (type == null) {
                return Result.NONE;
            }
            var r = com.porcupinestudios.lotrport.colony.building.ColonyBuildings.plaque(player,
                    player.serverLevel(), plaque.get(), Optional.of(type));
            if (r.building().isEmpty()) {
                return Result.say(r.message());
            }
            Optional<Colony> c = com.porcupinestudios.lotrport.colony.building.ColonyBuildings.colonyFor(player,
                    player.serverLevel(), r.building().get().door());
            return c.map(x -> Result.go(KIND, context(x, r.building().get()), ""))
                    .orElse(Result.say(r.message()));
        }
        Optional<At> at = find(player.server, context);
        if (at.isEmpty()) {
            return Result.NONE;
        }
        Colony c = at.get().colony();
        switch (action) {
            case "citizen" -> {
                return Result.go(HallLedgerBook.KIND, c.id().toString(), "person:" + arg);
            }
            case "rename" -> {
                return Result.say(com.porcupinestudios.lotrport.colony.building.ColonyBuildings.rename(player,
                        at.get().building().door(), arg).message());
            }
            case "measure" -> {
                var r = com.porcupinestudios.lotrport.colony.building.ColonyBuildings.add(player,
                        player.serverLevel(), at.get().building().door(), Optional.of(at.get().building().type()),
                        Optional.empty());
                return Result.say(r.message());
            }
            case "retype" -> {
                ResourceLocation type = ResourceLocation.tryParse(arg);
                if (type == null) {
                    return Result.NONE;
                }
                return Result.say(com.porcupinestudios.lotrport.colony.building.ColonyBuildings.retype(player,
                        at.get().building().door(), type).message());
            }
            case "order", "target" -> {
                if (!Permissions.can(player.getUUID(), c, Perm.PRODUCTION)) {
                    return Result.say("Only those who may direct production can");
                }
                ResourceLocation item = ResourceLocation.tryParse(arg);
                if (item != null) {
                    int v = Math.max(0, Math.min(4096, num));
                    if (action.equals("order")) {
                        Production.setOrder(player.server, c, item, v);
                    } else {
                        Production.setTarget(player.server, c, item, v);
                    }
                }
                return Result.NONE;
            }
            default -> {
                return Result.NONE;
            }
        }
    }
}
