package com.porcupinestudios.lotrport.colony.ui;

import static com.porcupinestudios.lotrport.colony.ui.ColonyViews.tr;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.colonist.ColonistData;
import com.porcupinestudios.lotrport.colonist.Skills;
import com.porcupinestudios.lotrport.colonist.TraitDef;
import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.colony.Immigration;
import com.porcupinestudios.lotrport.colony.members.Perm;
import com.porcupinestudios.lotrport.colony.members.Permissions;
import com.porcupinestudios.lotrport.colony.work.Races;
import com.porcupinestudios.lotrport.ui.Action;
import com.porcupinestudios.lotrport.ui.Page;
import com.porcupinestudios.lotrport.ui.Stat;
import com.porcupinestudios.lotrport.ui.Tone;
import com.porcupinestudios.lotrport.ui.View;
import com.porcupinestudios.lotrport.ui.ViewProvider;
import com.porcupinestudios.lotrport.ui.Views;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * A would-be settler's page (view {@code lotr_port:candidate}, context {@code <colony>/<offer>/<member>}), opened from
 * the Immigration tab: the citizen page's look with what is known before they move in. Overview (who they are, the
 * group), Skills, Traits, People (the rest of their group) and About with the group's family tree; Welcome and Turn
 * away answer for the whole group. Opening another member of the group goes to their page.
 */
public final class CandidateViews {
    public static final ResourceLocation ID = LotrPort.id("candidate");
    static final String OVERVIEW = "overview";
    static final String SKILLS = "skills";
    static final String TRAITS = "traits";
    static final String PEOPLE = "people";
    static final String ABOUT = "about";

    private CandidateViews() {}

    public static void register() {
        Views.register(ID, new Provider());
    }

    public static View.Target target(Colony c, int offer, int member, String tab) {
        return new View.Target(ID, c.id() + "/" + offer + "/" + member, tab);
    }

    record Ctx(Colony colony, Immigration.Offer offer, int member) {
        Immigration.Prospect who() {
            return offer.members().get(member);
        }
    }

    static Optional<Ctx> ctx(ServerPlayer player, String context) {
        String[] p = context.split("/");
        if (p.length != 3) {
            return Optional.empty();
        }
        Optional<Colony> c = ColonyViews.colony(player.server, p[0]);
        if (c.isEmpty() || !Permissions.isMember(player.getUUID(), c.get())) {
            return Optional.empty();
        }
        int offer = CaravanViews.parse(p[1]);
        int member = CaravanViews.parse(p[2]);
        return c.get().get(Immigration.BOARD).flatMap(b -> b.offers().stream().filter(o -> o.id() == offer)
                .findFirst()).filter(o -> member >= 0 && member < o.members().size())
                .map(o -> new Ctx(c.get(), o, member));
    }

    static String bond(Immigration.Offer o, int a, int b) {
        Immigration.Prospect pa = o.members().get(a);
        Immigration.Prospect pb = o.members().get(b);
        if (pa.spouse() == b) {
            return "Spouse";
        }
        if (pa.parents().contains(b)) {
            return "Parent";
        }
        if (pb.parents().contains(a)) {
            return "Child";
        }
        if (!pa.parents().isEmpty() && pa.parents().equals(pb.parents())) {
            return "Sibling";
        }
        return o.bond().equals("friends") ? "Friend" : "Travelling together";
    }

    /** The family tree of a group (tests). */
    public static Page.Tree treeOf(Immigration.Offer o, int focus) {
        return tree(o, focus);
    }

    static Page.Tree tree(Immigration.Offer o, int focus) {
        List<Page.TreeNode> nodes = new ArrayList<>();
        List<Page.TreeLink> links = new ArrayList<>();
        for (int i = 0; i < o.members().size(); i++) {
            Immigration.Prospect m = o.members().get(i);
            nodes.add(new Page.TreeNode(Integer.toString(i), Component.literal(m.name().isEmpty() ? "?" : m.name()),
                    Component.literal(ColonyViews.title(Races.of(m.type())) + ", " + m.age()), true, i != focus, ""));
            if (m.spouse() > i) {
                links.add(new Page.TreeLink(Integer.toString(i), Integer.toString(m.spouse()),
                        Page.TreeLink.SPOUSE));
            }
            for (int parent : m.parents()) {
                links.add(new Page.TreeLink(Integer.toString(parent), Integer.toString(i), Page.TreeLink.PARENT));
            }
        }
        return new Page.Tree(Integer.toString(focus), nodes, links, Optional.of("member"));
    }

    static final class Provider implements ViewProvider {
        @Override
        public Optional<View> build(ServerPlayer player, String context, String tab) {
            Optional<Ctx> found = ctx(player, context);
            if (found.isEmpty()) {
                return Optional.empty();
            }
            Ctx x = found.get();
            Immigration.Prospect m = x.who();
            Immigration.Offer o = x.offer();
            String open = switch (tab) {
                case SKILLS, TRAITS, PEOPLE, ABOUT -> tab;
                default -> OVERVIEW;
            };
            List<View.Tab> tabs = List.of(new View.Tab(OVERVIEW, tr("citizen.tab.overview", "Overview")),
                    new View.Tab(SKILLS, tr("citizen.tab.skills", "Skills")),
                    new View.Tab(TRAITS, tr("candidate.tab.traits", "Traits")),
                    new View.Tab(PEOPLE, tr("citizen.tab.people", "People")),
                    new View.Tab(ABOUT, tr("citizen.tab.about", "About")));
            String group = switch (o.bond()) {
                case "family" -> "Family of " + o.members().size();
                case "friends" -> o.members().size() + " friends";
                default -> "Alone";
            };
            Component subtitle = Component.literal(ColonyViews.title(Races.of(m.type())) + "  ·  " + m.age()
                    + " years old  ·  wants to settle");
            List<Page.Section> left = new ArrayList<>();
            List<Page.Section> right = new ArrayList<>();
            Optional<Page.Tree> tree = Optional.empty();
            switch (open) {
                case SKILLS -> {
                    List<Stat> sk = new ArrayList<>();
                    List<Map.Entry<ResourceLocation, Skills.Def>> defs = new ArrayList<>(Skills.defs().entrySet());
                    defs.sort(Comparator.comparingInt(e -> e.getValue().order()));
                    for (var e : defs) {
                        int lv = m.skills().getOrDefault(e.getKey(), new Skills.Skill(0, 0)).level();
                        sk.add(Stat.of(Component.literal(Skills.name(e.getKey())), "Level " + lv, lv >= 4 ? Tone.GOOD
                                : lv == 0 ? Tone.MUTED : Tone.NORMAL).withBar(lv / (double) Math.max(1,
                                        Skills.settings().maxLevel())));
                    }
                    left.add(new Page.Section(tr("citizen.skills", "Skills"), sk));
                    right.add(new Page.Section(tr("candidate.would_do", "Would do best as"), best(m)));
                }
                case TRAITS -> {
                    List<Stat> ts = new ArrayList<>();
                    for (ResourceLocation t : m.traits()) {
                        TraitDef d = ColonistData.traits().get(t);
                        ts.add(Stat.of(ColonyViews.traitName(t), d == null ? "" : effect(d), d == null
                                ? Tone.NORMAL : d.workSpeed() > 1 ? Tone.GOOD : d.workSpeed() < 1 ? Tone.BAD
                                        : Tone.NORMAL));
                    }
                    if (ts.isEmpty()) {
                        ts.add(Stat.of(tr("candidate.no_traits", "Nothing out of the ordinary"), "", Tone.MUTED));
                    }
                    left.add(new Page.Section(tr("citizen.traits", "Traits"), ts));
                }
                case PEOPLE -> {
                    List<Stat> ps = new ArrayList<>();
                    for (int i = 0; i < o.members().size(); i++) {
                        if (i != x.member()) {
                            Immigration.Prospect other = o.members().get(i);
                            ps.add(Stat.of(Component.literal(other.name()), bond(o, x.member(), i), Tone.GOOD));
                        }
                    }
                    if (ps.isEmpty()) {
                        ps.add(Stat.of(tr("candidate.alone", "Comes alone"), "", Tone.MUTED));
                    }
                    left.add(new Page.Section(tr("candidate.group", "Their group"), ps));
                    right.add(new Page.Section(tr("candidate.after", "Once settled"), List.of(Stat.of(Component.literal(
                            "They start out fond of their group"), "", Tone.MUTED), Stat.of(Component.literal(
                                    "and get to know the colony day by day."), "", Tone.MUTED))));
                }
                case ABOUT -> {
                    left.add(new Page.Section(tr("citizen.about", "About"), List.of(
                            Stat.of(tr("candidate.race", "Race"), ColonyViews.title(Races.of(m.type()))),
                            Stat.of(tr("candidate.age", "Age"), Integer.toString(m.age())),
                            Stat.of(tr("candidate.group_is", "Group"), group))));
                    tree = Optional.of(tree(o, x.member()));
                }
                default -> {
                    List<Stat> who = new ArrayList<>();
                    who.add(Stat.of(tr("candidate.race", "Race"), ColonyViews.title(Races.of(m.type()))));
                    who.add(Stat.of(tr("candidate.age", "Age"), Integer.toString(m.age())));
                    who.add(Stat.of(tr("candidate.group_is", "Group"), group));
                    who.add(Stat.of(tr("citizen.traits", "Traits"), m.traits().isEmpty() ? "-" : String.join(", ",
                            m.traits().stream().map(t -> ColonyViews.traitName(t).getString()).toList())));
                    left.add(new Page.Section(tr("candidate.who", "Who they are"), who));
                    List<Stat> members = new ArrayList<>();
                    for (int i = 0; i < o.members().size(); i++) {
                        Immigration.Prospect other = o.members().get(i);
                        members.add(Stat.of(Component.literal(other.name()), i == x.member() ? "this page"
                                : bond(o, x.member(), i), i == x.member() ? Tone.MUTED : Tone.NORMAL));
                    }
                    right.add(new Page.Section(tr("candidate.group", "Their group"), members));
                    right.add(new Page.Section(tr("candidate.would_do", "Would do best as"), best(m)));
                }
            }
            boolean may = Permissions.can(player.getUUID(), x.colony(), Perm.MIGRANTS);
            Component why = tr("immigration.no_perm", "Only those who may answer migrants can");
            List<Action> buttons = new ArrayList<>();
            if (o.members().size() > 1) {
                buttons.add(Action.of("next", tr("candidate.next", "Next in group")));
            }
            String n = o.members().size() > 1 ? " all " + o.members().size() : "";
            buttons.add(may ? Action.of("welcome", Component.literal("Welcome" + n)) : Action.disabled("welcome",
                    Component.literal("Welcome" + n), why));
            buttons.add(may ? Action.of("turn_away", tr("immigration.turn_away", "Turn away")).confirmed()
                    : Action.disabled("turn_away", tr("immigration.turn_away", "Turn away"), why));
            Page page = new Page.Profile(Optional.empty(), Component.literal(m.name()), Optional.empty(), subtitle,
                    left, right, buttons, tree, m.skin().isEmpty() ? Optional.empty() : Optional.of(m.skin()));
            return Optional.of(new View(ID, context, Component.literal(x.colony().name() + ": wanting to settle"),
                    tabs, open, page, Optional.of(new View.Target(ColonyViews.COLONY, x.colony().id().toString(),
                            ImmigrationViews.TAB))));
        }

        private static List<Stat> best(Immigration.Prospect m) {
            List<Stat> out = new ArrayList<>();
            m.skills().entrySet().stream().sorted(Map.Entry.<ResourceLocation, Skills.Skill>comparingByValue(
                    Comparator.comparingInt(Skills.Skill::level)).reversed()).limit(2).forEach(e -> {
                        List<String> jobs = Skills.defs().getOrDefault(e.getKey(), new Skills.Def("", List.of(),
                                Map.of(), 0)).jobs().stream().map(j -> com.porcupinestudios.lotrport.colony.work
                                        .JobDef.titleOf(j).getString()).toList();
                        out.add(Stat.of(Component.literal(Skills.name(e.getKey()) + " " + e.getValue().level()),
                                jobs.isEmpty() ? "" : String.join(", ", jobs)));
                    });
            if (out.isEmpty()) {
                out.add(Stat.of(tr("candidate.unskilled", "Unskilled for now"), "", Tone.MUTED));
            }
            return out;
        }

        private static String effect(TraitDef d) {
            List<String> parts = new ArrayList<>();
            if (d.workSpeed() != 1) {
                parts.add("works " + Math.round(Math.abs(d.workSpeed() - 1) * 100) + "% " + (d.workSpeed() > 1
                        ? "faster" : "slower"));
            }
            d.needRates().forEach((need, rate) -> {
                if (rate != 1) {
                    parts.add(need.getPath() + " " + (rate > 1 ? "+" : "") + Math.round((rate - 1) * 100) + "%");
                }
            });
            return parts.isEmpty() ? d.kind().name().toLowerCase(java.util.Locale.ROOT) : String.join(", ", parts);
        }

        @Override
        public Result act(ServerPlayer player, String context, String tab, String action, String row, int value) {
            Optional<Ctx> found = ctx(player, context);
            if (found.isEmpty()) {
                return Result.NONE;
            }
            Ctx x = found.get();
            View.Target back = new View.Target(ColonyViews.COLONY, x.colony().id().toString(), ImmigrationViews.TAB);
            switch (action) {
                case "next" -> {
                    return Result.go(target(x.colony(), x.offer().id(), (x.member() + 1) % x.offer().members().size(),
                            tab));
                }
                case "member" -> {
                    // a click in the family tree: that member's page
                    int i = CaravanViews.parse(row);
                    return i < 0 || i >= x.offer().members().size() ? Result.NONE
                            : Result.go(target(x.colony(), x.offer().id(), i, tab));
                }
                case "welcome", "turn_away" -> {
                    if (!Permissions.can(player.getUUID(), x.colony(), Perm.MIGRANTS)) {
                        return Result.say(tr("immigration.no_perm", "Only those who may answer migrants can"));
                    }
                    Immigration.Answer a = action.equals("welcome") ? Immigration.accept(player, x.colony(),
                            x.offer().id()) : Immigration.decline(player.server, x.colony(), x.offer().id());
                    return a.ok() ? new Result(Optional.of(a.message()), Optional.of(back)) : Result.say(a.message());
                }
                default -> {
                    return Result.NONE;
                }
            }
        }
    }
}
