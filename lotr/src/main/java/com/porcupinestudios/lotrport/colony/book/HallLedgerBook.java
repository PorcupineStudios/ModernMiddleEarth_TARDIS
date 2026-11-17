package com.porcupinestudios.lotrport.colony.book;

import com.porcupinestudios.lotrport.colonist.ColonistData;
import com.porcupinestudios.lotrport.colonist.Skills;
import com.porcupinestudios.lotrport.colony.Colonies;
import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.colony.building.Building;
import com.porcupinestudios.lotrport.colony.building.BuildingType;
import com.porcupinestudios.lotrport.colony.building.Buildings;
import com.porcupinestudios.lotrport.colony.market.Research;
import com.porcupinestudios.lotrport.colony.members.Members;
import com.porcupinestudios.lotrport.colony.members.Perm;
import com.porcupinestudios.lotrport.colony.members.Permissions;
import com.porcupinestudios.lotrport.colony.members.RoleDef;
import com.porcupinestudios.lotrport.colony.people.Council;
import com.porcupinestudios.lotrport.colony.people.EdictDef;
import com.porcupinestudios.lotrport.colony.tech.MilestoneDef;
import com.porcupinestudios.lotrport.colony.tech.TechDef;
import com.porcupinestudios.lotrport.colony.tech.Techs;
import com.porcupinestudios.lotrport.colony.work.JobDef;
import com.porcupinestudios.lotrport.colony.work.Modifiers;
import com.porcupinestudios.lotrport.colony.work.WorkComponents;
import com.porcupinestudios.lotrport.society.CoreComponents;
import com.porcupinestudios.lotrport.society.Person;
import com.porcupinestudios.lotrport.society.Society;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The Town Hall ledger (RingRealms' Steward's Ledger): the colony at a glance on the left page; People (cards with
 * filters, each opening the person's page), Buildings (each opening its plaque), Council (unity, edicts, quarrels),
 * Research (the tech tree by subject, milestones) and Members (roles, invites). Opened at the Ledger Stand, with the
 * Town Hall ledger, or by using the Hall. Context: the colony id.
 */
public final class HallLedgerBook implements BookProvider {
    public static final String KIND = "hall_ledger";
    static final String[] FILTERS = {"all", "homeless", "idle", "unhappy", "hungry"};

    private HallLedgerBook() {}

    static void register() {
        Books.register(KIND, new HallLedgerBook());
    }

    @Override
    public Optional<Book> build(ServerPlayer player, String context, String tab) {
        Optional<Colony> found = Pages.colony(player.server, context);
        if (found.isEmpty() || !Permissions.isMember(player.getUUID(), found.get())) {
            return Optional.empty();
        }
        MinecraftServer server = player.server;
        Colony c = found.get();
        List<Person> citizens = Society.citizens(server, c);
        List<Book.Tab> tabs = List.of(
                new Book.Tab("people", "People", "minecraft:player_head"),
                new Book.Tab("buildings", "Buildings", "minecraft:bricks"),
                new Book.Tab("council", "Council", "minecraft:bell"),
                new Book.Tab("research", "Research", "minecraft:enchanted_book"),
                new Book.Tab("members", "Members", "minecraft:name_tag"),
                new Book.Tab("war", "War", "minecraft:iron_sword"));
        String open = tab.isEmpty() ? "people" : tab;
        String tabId = open.contains(":") ? open.substring(0, open.indexOf(':')) : open;
        String arg = open.contains(":") ? open.substring(open.indexOf(':') + 1) : "";

        List<El> right = switch (tabId) {
            case "person" -> person(server, c, arg);
            case "buildings" -> buildings(server, c, citizens);
            case "council" -> council(player, c);
            case "research" -> research(player, c);
            case "members" -> members(player, c);
            case "war" -> WarPage.page(player, c);
            default -> people(c, citizens, arg.isEmpty() ? "all" : arg);
        };
        String shownTab = tabId.equals("person") ? "people" : tabId;
        return Optional.of(new Book(KIND, context, c.name(), "Town Hall ledger", chips(server, c, citizens), tabs,
                shownTab, overview(server, c, citizens), right, "", 1));
    }

    static List<El> chips(MinecraftServer server, Colony c, List<Person> citizens) {
        long coins = c.get(CoreComponents.TREASURY).orElse(CoreComponents.Treasury.EMPTY).coins();
        return List.of(El.chip("minecraft:player_head", Integer.toString(citizens.size()), "Citizens"),
                El.chip("minecraft:gold_nugget", Long.toString(coins), "Treasury"),
                El.chip("minecraft:bell", Math.round(Council.state(server, c).unity()) + "", "Unity"));
    }

    static List<El> overview(MinecraftServer server, Colony c, List<Person> citizens) {
        List<El> out = new ArrayList<>();
        out.add(El.pair("Ruler", Pages.playerName(server, c.founder())));
        out.add(El.pair("People of", Pages.faction(c.faction())));
        out.add(El.pair("Founded", "day " + c.founded() / 24000, El.MUTED));
        double mood = 0;
        double food = 0;
        int homes = 0;
        int jobs = 0;
        int count = 0;
        for (Person p : citizens) {
            if (p.has(CoreComponents.UNIT)) {
                continue;
            }
            WorkComponents.Needs n = p.get(WorkComponents.NEEDS).orElse(WorkComponents.Needs.NEW);
            mood += n.happiness();
            food += n.food();
            homes += p.has(WorkComponents.HOME) ? 1 : 0;
            jobs += p.has(WorkComponents.JOB) ? 1 : 0;
            count++;
        }
        int div = Math.max(1, count);
        out.add(El.gap());
        out.add(El.meter("Happiness", Math.round(mood / div) + " / 100", mood / div / 100, 0.25)
                .withTip("Citizens below 25 for days on end leave"));
        out.add(El.meter("Food", Math.round(food / div) + " / 20", food / div / 20));
        out.add(El.meter("Housed", homes + " of " + count, count == 0 ? 1 : homes / (double) count));
        out.add(El.meter("Employed", jobs + " of " + count, count == 0 ? 1 : jobs / (double) count));
        double unity = Council.state(server, c).unity();
        out.add(El.meter("Unity", Math.round(unity) + " / 100", unity / 100, 0.5)
                .withTip("Unity speeds or slows all work and lifts or sours every mood"));
        out.add(El.gap());
        out.add(El.pair("Buildings", Integer.toString(Buildings.of(c).list().size())));
        out.add(El.pair("Soldiers", Integer.toString(Society.soldiers(server, c).size())));
        out.add(El.gap());
        out.add(El.buttons(List.of(El.button("Treasury", "goto", TreasuryBook.KIND),
                El.button("Customs", "goto", CustomsBook.KIND))));
        return out;
    }

    static List<El> people(Colony c, List<Person> citizens, String filter) {
        List<El> out = new ArrayList<>();
        List<El> chips = new ArrayList<>();
        Map<String, Integer> counts = new HashMap<>();
        List<Person> shown = new ArrayList<>();
        for (Person p : citizens) {
            for (String f : FILTERS) {
                if (matches(p, f)) {
                    counts.merge(f, 1, Integer::sum);
                }
            }
            if (matches(p, filter)) {
                shown.add(p);
            }
        }
        for (String f : FILTERS) {
            El b = El.button(Pages.title(f) + " " + counts.getOrDefault(f, 0), "filter", f);
            chips.add(f.equals(filter) ? b.withTone(El.GOOD) : b);
        }
        out.add(El.buttons(chips));
        shown.sort(Comparator.comparing(Pages::name));
        if (shown.isEmpty()) {
            out.add(El.text(citizens.isEmpty() ? "Nobody lives here yet. Newcomers wait at the Customs Counter."
                    : "Nobody fits that filter."));
        }
        for (Person p : shown) {
            out.add(Pages.person(p, ""));
        }
        return out;
    }

    static boolean matches(Person p, String filter) {
        if (filter.equals("all")) {
            return true;
        }
        boolean soldier = p.has(CoreComponents.UNIT);
        WorkComponents.Needs n = p.get(WorkComponents.NEEDS).orElse(WorkComponents.Needs.NEW);
        return switch (filter) {
            case "homeless" -> !soldier && !p.has(WorkComponents.HOME);
            case "idle" -> !soldier && !p.has(WorkComponents.JOB);
            case "unhappy" -> n.happiness() < 50;
            case "hungry" -> n.food() < 10;
            default -> true;
        };
    }

    static List<El> person(MinecraftServer server, Colony c, String id) {
        List<El> out = new ArrayList<>();
        Optional<Person> found;
        try {
            found = Society.person(server, UUID.fromString(id)).filter(p -> Society.colonyOf(server, p)
                    .map(x -> x.id().equals(c.id())).orElse(false));
        } catch (IllegalArgumentException e) {
            found = Optional.empty();
        }
        out.add(El.buttons(List.of(El.button("< All people", "tab", "people"))));
        if (found.isEmpty()) {
            out.add(El.text("They no longer live here."));
            return out;
        }
        Person p = found.get();
        WorkComponents.Needs n = p.get(WorkComponents.NEEDS).orElse(WorkComponents.Needs.NEW);
        out.add(El.card(Pages.face(p), Pages.name(p), Pages.race(p) + " - born day " + p.born() / 24000, El.NORMAL,
                "", "", List.of()));
        out.add(El.meter("Happiness", Math.round(n.happiness()) + " / 100", n.happiness() / 100, 0.25));
        out.add(El.meter("Food", Math.round(n.food()) + " / 20", n.food() / 20));
        if (n.unpaid() > 0) {
            out.add(El.pair("On strike", n.unpaid() + " days unpaid", El.BAD));
        }
        Buildings buildings = Buildings.of(c);
        out.add(El.head("Home and work"));
        Optional<Building> home = p.get(WorkComponents.HOME).flatMap(h -> buildings.get(h.building()));
        out.add(home.map(h -> El.pair("Home", h.name().orElse(Pages.building(h.type())))
                .withItem("minecraft:red_bed").withAction("plaque", h.id().toString()))
                .orElse(El.pair("Home", "homeless", El.WARN).withItem("minecraft:red_bed")));
        Optional<WorkComponents.Job> job = p.get(WorkComponents.JOB);
        if (job.isPresent()) {
            Optional<Building> at = buildings.get(job.get().building());
            out.add(El.pair(Pages.job(job.get().job()), at.map(b -> b.name().orElse(Pages.building(b.type())))
                    .orElse("?")).withItem("minecraft:iron_pickaxe").withAction("plaque",
                            at.map(b -> b.id().toString()).orElse("")));
            out.add(El.meter("Skill", Math.round(job.get().skill()) + " / 100", job.get().skill() / 100));
        } else {
            out.add(El.pair("Work", p.has(CoreComponents.UNIT) ? "soldier" : "idle", El.MUTED)
                    .withItem("minecraft:iron_pickaxe"));
        }
        List<ResourceLocation> traits = p.get(ColonistData.TRAITS).orElse(List.of());
        if (!traits.isEmpty()) {
            out.add(El.head("Traits"));
            List<El> tl = new ArrayList<>();
            for (ResourceLocation t : traits) {
                tl.add(El.tag(Pages.lang("colonist_trait." + t.getNamespace() + "." + t.getPath(),
                        Pages.title(t.getPath())), El.NORMAL));
            }
            out.add(El.buttons(tl));
        }
        Map<ResourceLocation, Skills.Skill> skills = p.get(Skills.TYPE).orElse(Map.of());
        if (!skills.isEmpty()) {
            out.add(El.head("Skills"));
            skills.entrySet().stream().sorted(Map.Entry.<ResourceLocation, Skills.Skill>comparingByValue(
                    Comparator.comparingInt(Skills.Skill::level)).reversed()).limit(6).forEach(e ->
                    out.add(El.meter(Pages.title(Skills.name(e.getKey())), "level " + e.getValue().level(),
                            e.getValue().level() / 20.0)));
        }
        Map<UUID, Double> grudges = Council.grudges(p);
        if (!grudges.isEmpty()) {
            out.add(El.head("Grudges"));
            grudges.entrySet().stream().sorted(Map.Entry.<UUID, Double>comparingByValue().reversed()).limit(5)
                    .forEach(e -> out.add(El.meter(Society.person(server, e.getKey()).map(Pages::name).orElse("someone"),
                            Math.round(e.getValue()) + "", Math.min(1, e.getValue() / 60)).withTone(El.BAD)));
        }
        out.add(El.gap());
        out.add(El.buttons(List.of(El.button("Orders, family and more", "full", p.id().toString()))));
        return out;
    }

    static List<El> buildings(MinecraftServer server, Colony c, List<Person> citizens) {
        List<El> out = new ArrayList<>();
        Map<UUID, Integer> workers = new HashMap<>();
        Map<UUID, Integer> residents = new HashMap<>();
        for (Person p : citizens) {
            p.get(WorkComponents.JOB).ifPresent(j -> workers.merge(j.building(), 1, Integer::sum));
            p.get(WorkComponents.HOME).ifPresent(h -> residents.merge(h.building(), 1, Integer::sum));
        }
        List<Building> list = new ArrayList<>(Buildings.of(c).list());
        if (list.isEmpty()) {
            out.add(El.text("No buildings yet. Use a Building Writ on a door: the room behind it becomes a building, "
                    + "and a Plaque hung by the door shows its page."));
            return out;
        }
        list.sort(Comparator.comparing((Building b) -> b.type().getPath()).thenComparing(b -> b.door().asLong()));
        for (Building b : list) {
            Optional<BuildingType> t = BuildingType.get(b.type());
            String use;
            double fill;
            if (t.map(BuildingType::home).orElse(false)) {
                int beds = b.count("beds");
                int r = residents.getOrDefault(b.id(), 0);
                use = r + " of " + beds + " beds";
                fill = beds == 0 ? 0 : r / (double) beds;
            } else if (t.flatMap(BuildingType::job).isPresent()) {
                int slots = t.flatMap(BuildingType::job).flatMap(JobDef::get).map(j -> j.slots(b.quality())).orElse(0);
                int w = workers.getOrDefault(b.id(), 0);
                use = w + " of " + slots + " workers";
                fill = slots == 0 ? 0 : w / (double) slots;
            } else {
                use = "";
                fill = -1;
            }
            List<El> meters = new ArrayList<>();
            meters.add(El.meter("Quality", Integer.toString(b.quality()), b.quality() / 100.0));
            if (fill >= 0) {
                meters.add(El.meter("In use", use, fill));
            }
            out.add(El.card(PlaqueBook.icon(b), b.name().orElse(Pages.building(b.type())),
                    Pages.building(b.type()) + " - " + b.door().toShortString(), El.NORMAL, "plaque",
                    b.id().toString(), meters));
        }
        return out;
    }

    static List<El> council(ServerPlayer player, Colony c) {
        MinecraftServer server = player.server;
        boolean may = Permissions.can(player.getUUID(), c, Perm.EVENTS);
        String why = may ? "" : "Only those who may proclaim can";
        Council.State st = Council.state(server, c);
        List<El> out = new ArrayList<>();
        out.add(El.meter("Unity", Math.round(st.unity()) + " / 100", st.unity() / 100, 0.5));
        for (Modifiers.Part part : Modifiers.parts(server, c)) {
            if (Math.abs(part.work() - 1) < 1e-4 && Math.abs(part.happiness()) < 1e-4) {
                continue;
            }
            out.add(El.pair(Pages.title(part.name()), String.format(Locale.ROOT, "work x%.2f   mood %+.0f",
                    part.work(), part.happiness()), part.work() >= 1 && part.happiness() >= 0 ? El.GOOD : El.WARN));
        }
        out.add(El.head("Edicts in force (" + st.edicts().size() + " of " + Council.slots(server, c) + ")"));
        if (st.edicts().isEmpty()) {
            out.add(El.text("None."));
        }
        for (ResourceLocation id : st.edicts()) {
            EdictDef d = EdictDef.get(id).orElse(null);
            if (d != null) {
                out.add(El.card("minecraft:paper", d.title(id), d.description() + " - " + Pages.coins(d.upkeep())
                        + " a day", El.GOOD, "", "", List.of(El.button("Repeal", "repeal", id.toString(), may, why))));
            }
        }
        out.add(El.head("Edicts the Council may proclaim"));
        for (Map.Entry<ResourceLocation, EdictDef> e : EdictDef.all().entrySet()) {
            if (st.edicts().contains(e.getKey())) {
                continue;
            }
            Optional<String> no = Council.whyNot(server, c, e.getKey()).map(x -> x.getString());
            EdictDef d = e.getValue();
            out.add(El.card("minecraft:paper", d.title(e.getKey()), d.description() + " - "
                    + Pages.coins(d.upkeep()) + " a day", El.MUTED, "", "", List.of(El.button("Proclaim", "proclaim",
                    e.getKey().toString(), may && no.isEmpty(), no.orElse(why)))));
        }
        out.add(El.head("Quarrels and brawls"));
        if (st.reports().isEmpty()) {
            out.add(El.text("All quiet."));
        }
        for (int i = st.reports().size() - 1; i >= 0; i--) {
            Council.Report r = st.reports().get(i);
            out.add(El.pair(r.text(), "day " + r.time() / 24000, r.brawl() ? El.BAD : El.NORMAL));
        }
        return out;
    }

    static List<El> research(ServerPlayer player, Colony c) {
        MinecraftServer server = player.server;
        boolean may = Permissions.can(player.getUUID(), c, Perm.RESEARCH);
        Techs.State st = Techs.state(server, c);
        List<El> out = new ArrayList<>();
        List<El> pts = new ArrayList<>();
        for (ResourceLocation tier : Research.tiers().keySet()) {
            pts.add(El.pair(Research.name(tier), Integer.toString((int) Math.floor(Research.points(server, c, tier))))
                    .withItem("minecraft:book"));
        }
        out.addAll(pts);
        Map<String, List<Map.Entry<ResourceLocation, TechDef>>> lanes = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, TechDef> e : TechDef.sorted()) {
            lanes.computeIfAbsent(e.getValue().lane(), k -> new ArrayList<>()).add(e);
        }
        lanes.forEach((lane, nodes) -> {
            out.add(El.head(Pages.title(lane)));
            for (Map.Entry<ResourceLocation, TechDef> e : nodes) {
                TechDef d = e.getValue();
                boolean have = st.techs().containsKey(e.getKey());
                Optional<String> no = have ? Optional.empty()
                        : Techs.whyNot(server, c, e.getKey()).map(x -> x.getString());
                boolean closed = !have && Techs.closedBy(c, e.getKey()).isPresent();
                String sub = (d.fork().isPresent() ? "Choice: " : "") + d.description()
                        + (have ? "" : " - " + d.cost().text());
                int tone = have ? El.GOOD : closed ? El.BAD : no.isEmpty() ? El.WARN : El.MUTED;
                List<El> kids = have ? List.of() : List.of(El.button(closed ? "Closed" : "Research", "research",
                        e.getKey().toString(), may && no.isEmpty(), no.orElse(may ? "" : "Only those who may direct "
                                + "research can")));
                out.add(El.card(have ? "minecraft:enchanted_book" : "minecraft:book",
                        "Stage " + d.stage() + ": " + d.title(e.getKey()), sub, tone, "", "", kids));
            }
        });
        out.add(El.head("Milestones"));
        List<Map.Entry<ResourceLocation, MilestoneDef>> ms = new ArrayList<>(MilestoneDef.all().entrySet());
        ms.sort(Comparator.comparingInt(e -> e.getValue().order()));
        for (Map.Entry<ResourceLocation, MilestoneDef> e : ms) {
            boolean done = st.milestones().containsKey(e.getKey());
            MilestoneDef m = e.getValue();
            out.add(El.pair(m.title(e.getKey()) + " - " + m.description(), done ? "reached"
                    : m.coins() > 0 ? Pages.coins(m.coins()) : "ahead", done ? El.GOOD : El.MUTED)
                    .withItem(done ? "minecraft:gold_ingot" : "minecraft:iron_nugget"));
        }
        return out;
    }

    static List<El> members(ServerPlayer player, Colony c) {
        MinecraftServer server = player.server;
        boolean may = Permissions.can(player.getUUID(), c, Perm.MEMBERS);
        List<El> out = new ArrayList<>();
        out.add(El.card("minecraft:golden_helmet", Pages.playerName(server, c.founder()), "Ruler", El.GOOD, "", "",
                List.of()));
        Members m = c.get(Members.TYPE).orElse(Members.NONE);
        List<ResourceLocation> roles = Permissions.assignable();
        for (Map.Entry<UUID, ResourceLocation> e : m.roles().entrySet()) {
            String role = Optional.ofNullable(Permissions.roles().get(e.getValue())).map(RoleDef::name)
                    .orElse(e.getValue().getPath());
            List<El> kids = new ArrayList<>();
            int at = roles.indexOf(e.getValue());
            String id = e.getKey().toString();
            String why = may ? "" : "Only those who may manage members can";
            kids.add(El.button("Promote", "role", id + "|" + (at > 0 ? roles.get(at - 1) : ""), may && at > 0, why));
            kids.add(El.button("Demote", "role", id + "|" + (at >= 0 && at < roles.size() - 1 ? roles.get(at + 1) : ""),
                    may && at >= 0 && at < roles.size() - 1, why));
            kids.add(El.button("Remove", "role", id + "|", may, why));
            out.add(El.card("minecraft:iron_helmet", Pages.playerName(server, e.getKey()), role, El.NORMAL, "", "",
                    kids));
        }
        out.add(El.head("Invite a player"));
        out.add(El.field("Player name", "", "invite", may, may ? "" : "Only those who may manage members can"));
        return out;
    }

    @Override
    public Result act(ServerPlayer player, String context, String tab, String action, String arg, int num) {
        Optional<Colony> found = Pages.colony(player.server, context);
        if (found.isEmpty()) {
            return Result.NONE;
        }
        Colony c = found.get();
        MinecraftServer server = player.server;
        Optional<Result> war = WarPage.act(player, c, action, arg);
        if (war.isPresent()) {
            return war.get();
        }
        switch (action) {
            case "tab" -> {
                return Result.go(KIND, context, arg);
            }
            case "filter" -> {
                return Result.go(KIND, context, "people:" + arg);
            }
            case "citizen" -> {
                return Result.go(KIND, context, "person:" + arg);
            }
            case "plaque" -> {
                try {
                    return Buildings.of(c).get(UUID.fromString(arg)).map(b -> Result.go(PlaqueBook.KIND,
                            PlaqueBook.context(c, b), "")).orElse(Result.NONE);
                } catch (IllegalArgumentException e) {
                    return Result.NONE;
                }
            }
            case "goto" -> {
                return Result.go(arg, context, "");
            }
            case "full" -> {
                try {
                    Society.person(server, UUID.fromString(arg)).ifPresent(p -> com.porcupinestudios.lotrport.ui.Views
                            .open(player, com.porcupinestudios.lotrport.colony.ui.CitizenViews.target(c, p), true));
                } catch (IllegalArgumentException e) {
                    // not a person
                }
                return Result.NONE;
            }
            case "proclaim" -> {
                if (!Permissions.can(player.getUUID(), c, Perm.EVENTS)) {
                    return Result.say("Only those who may proclaim can");
                }
                ResourceLocation id = ResourceLocation.tryParse(arg);
                return id == null ? Result.NONE : Result.say(Council.proclaim(server, c, id));
            }
            case "repeal" -> {
                if (!Permissions.can(player.getUUID(), c, Perm.EVENTS)) {
                    return Result.say("Only those who may proclaim can");
                }
                ResourceLocation id = ResourceLocation.tryParse(arg);
                return id == null ? Result.NONE : Result.say(Council.repeal(server, c, id));
            }
            case "research" -> {
                if (!Permissions.can(player.getUUID(), c, Perm.RESEARCH)) {
                    return Result.say("Only those who may direct research can");
                }
                ResourceLocation id = ResourceLocation.tryParse(arg);
                return id == null ? Result.NONE : Result.say(Techs.research(server, c, id));
            }
            case "role" -> {
                String[] p = arg.split("\\|", -1);
                try {
                    UUID who = UUID.fromString(p[0]);
                    Optional<ResourceLocation> role = p.length > 1 && !p[1].isEmpty()
                            ? Optional.ofNullable(ResourceLocation.tryParse(p[1])) : Optional.empty();
                    return Result.say(Permissions.setRole(server, player.getUUID(), c, who, role).message());
                } catch (IllegalArgumentException e) {
                    return Result.NONE;
                }
            }
            case "invite" -> {
                String name = arg.trim();
                if (name.isEmpty()) {
                    return Result.NONE;
                }
                Optional<UUID> who = Optional.ofNullable(server.getPlayerList().getPlayerByName(name))
                        .map(ServerPlayer::getUUID).or(() -> Optional.ofNullable(server.getProfileCache())
                                .flatMap(pc -> pc.get(name)).map(com.mojang.authlib.GameProfile::getId));
                if (who.isEmpty()) {
                    return Result.say("Nobody called " + name + " has played here");
                }
                List<ResourceLocation> roles = Permissions.assignable();
                Optional<ResourceLocation> lowest = roles.isEmpty() ? Optional.empty()
                        : Optional.of(roles.get(roles.size() - 1));
                return Result.say(Permissions.setRole(server, player.getUUID(), c, who.get(), lowest).message());
            }
            default -> {
                return Result.NONE;
            }
        }
    }

    static Colonies colonies(MinecraftServer server) {
        return Colonies.get(server);
    }
}
