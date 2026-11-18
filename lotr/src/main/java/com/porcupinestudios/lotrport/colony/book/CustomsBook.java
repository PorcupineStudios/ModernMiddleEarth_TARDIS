package com.porcupinestudios.lotrport.colony.book;

import com.porcupinestudios.lotrport.colonist.Skills;
import com.porcupinestudios.lotrport.colony.Colonies;
import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.colony.Immigration;
import com.porcupinestudios.lotrport.colony.members.Perm;
import com.porcupinestudios.lotrport.colony.members.Permissions;
import com.porcupinestudios.lotrport.colony.work.Races;
import com.porcupinestudios.lotrport.journey.CaravanConditions;
import com.porcupinestudios.lotrport.society.Society;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * The Customs Counter: who would like to settle. The left page is the gate's state (the colony's immigration level,
 * places on the board, biggest group, newcomers' skill, free beds, when new faces come); Arrivals shows each waiting
 * group with every member's face, race, age, best skills and traits, to welcome or turn away ({@link Perm#MIGRANTS});
 * Levels shows what the colony unlocks as it grows. Context: the colony id.
 */
public final class CustomsBook implements BookProvider {
    public static final String KIND = "customs";

    private CustomsBook() {}

    static void register() {
        Books.register(KIND, new CustomsBook());
    }

    @Override
    public Optional<Book> build(ServerPlayer player, String context, String tab) {
        Optional<Colony> found = Pages.colony(player.server, context);
        if (found.isEmpty() || !Permissions.isMember(player.getUUID(), found.get())) {
            return Optional.empty();
        }
        MinecraftServer server = player.server;
        Colony c = found.get();
        Immigration.Board board = Immigration.board(server, c);
        Immigration.Perks perks = Immigration.perks(server, c);
        long now = server.overworld().getGameTime();
        int minutes = (int) Math.max(0, Math.ceil((board.next() - now) / 1200.0));
        ServerLevel level = server.getLevel(c.dimension());
        int beds = level == null ? 0 : Colonies.homes(level, c);
        int people = Society.citizenCount(server, c);
        int free = Math.max(0, beds - people);

        List<Book.Tab> tabs = List.of(new Book.Tab("arrivals", "Arrivals", "minecraft:oak_boat"),
                new Book.Tab("levels", "Levels", "minecraft:experience_bottle"));
        String open = tab.equals("levels") ? "levels" : "arrivals";

        List<El> left = new ArrayList<>();
        String levelName = perks.levels().isEmpty() ? "Hamlet" : perks.levels().get(perks.levels().size() - 1);
        left.add(El.pair("Standing", levelName).withItem("minecraft:experience_bottle"));
        left.add(El.pair("Places on the board", Integer.toString(perks.slots())));
        left.add(El.pair("Biggest group", Integer.toString(perks.maxGroup())));
        left.add(El.meter("Newcomers' skill", "quality " + perks.quality(), Math.min(1, perks.quality() / 10.0)));
        left.add(El.gap());
        left.add(El.meter("Free beds", free + " of " + beds, beds == 0 ? 0 : free / (double) beds)
                .withTip("Newcomers need a bed each (the first settlers excepted)"));
        left.add(El.pair("New faces in", minutes + " min", El.MUTED));
        left.add(El.gap());
        left.add(El.buttons(List.of(El.button("Town Hall", "goto", HallLedgerBook.KIND))));

        List<El> right = open.equals("levels") ? levels(server, c) : arrivals(player, c, board, free);
        List<El> chips = List.of(El.chip("minecraft:oak_boat", Integer.toString(board.offers().size()), "Waiting"),
                El.chip("minecraft:red_bed", Integer.toString(free), "Free beds"));
        return Optional.of(new Book(KIND, context, c.name(), "Customs Counter", chips, tabs, open, left, right, "", 3));
    }

    static List<El> arrivals(ServerPlayer player, Colony c, Immigration.Board board, int free) {
        boolean may = Permissions.can(player.getUUID(), c, Perm.MIGRANTS);
        String why = may ? "" : "Only those who may answer migrants can";
        List<El> out = new ArrayList<>();
        if (board.offers().isEmpty()) {
            out.add(El.text("Nobody is waiting at the counter. New faces come every 30 minutes."));
            return out;
        }
        for (Immigration.Offer o : board.offers()) {
            String group = switch (o.bond()) {
                case "family" -> "A family of " + o.members().size();
                case "friends" -> o.members().size() + " friends travelling together";
                default -> "Alone";
            };
            out.add(El.head(group));
            for (int i = 0; i < o.members().size(); i++) {
                Immigration.Prospect m = o.members().get(i);
                String face = m.skin().isEmpty() ? Pages.face(m.type(), i) : m.skin();
                String sub = Pages.title(Races.of(m.type())) + ", " + m.age() + " years" + relation(o, i);
                List<El> kids = new ArrayList<>();
                m.skills().entrySet().stream().filter(e -> e.getValue().level() > 0)
                        .sorted(Map.Entry.<ResourceLocation, Skills.Skill>comparingByValue(
                                Comparator.comparingInt(Skills.Skill::level)).reversed()).limit(3)
                        .forEach(e -> kids.add(El.meter(Pages.title(Skills.name(e.getKey())),
                                Integer.toString(e.getValue().level()), e.getValue().level() / 10.0)));
                for (ResourceLocation t : m.traits()) {
                    kids.add(El.tag(Pages.lang("colonist_trait." + t.getNamespace() + "." + t.getPath(),
                            Pages.title(t.getPath())), El.NORMAL));
                }
                out.add(El.card(face, m.name().isEmpty() ? "A stranger" : m.name(), sub, El.NORMAL, "", "", kids));
            }
            boolean room = free >= o.members().size();
            out.add(El.buttons(List.of(
                    El.button("Welcome", "welcome", Integer.toString(o.id()), may, why).withTone(El.GOOD),
                    El.button("Turn away", "turn_away", Integer.toString(o.id()), may, why).withTone(El.BAD))));
            if (!room) {
                out.add(El.text("Not enough free beds for them all yet.", El.WARN));
            }
        }
        return out;
    }

    static String relation(Immigration.Offer o, int i) {
        Immigration.Prospect m = o.members().get(i);
        if (m.spouse() >= 0) {
            return " - married";
        }
        if (!m.parents().isEmpty()) {
            return " - a child";
        }
        return "";
    }

    static List<El> levels(MinecraftServer server, Colony c) {
        List<El> out = new ArrayList<>();
        Immigration.Settings s = Immigration.settings();
        out.add(El.text("The colony's standing grows with its people and research. Each level reached adds to the "
                + "board."));
        out.add(El.pair("To begin with", s.slots() + " place, groups of " + s.maxGroup()));
        for (Immigration.Level l : s.levels()) {
            boolean ok = l.when().map(w -> {
                try {
                    return w.test(CaravanConditions.colonyContext(server, c));
                } catch (RuntimeException e) {
                    return false;
                }
            }).orElse(true);
            List<String> adds = new ArrayList<>();
            if (l.slots() != 0) {
                adds.add("+" + l.slots() + " place" + (l.slots() == 1 ? "" : "s"));
            }
            if (l.maxGroup() != 0) {
                adds.add("groups +" + l.maxGroup());
            }
            if (l.quality() != 0) {
                adds.add("skill +" + l.quality());
            }
            out.add(El.pair(l.name().isEmpty() ? "A level" : l.name(), String.join(", ", adds), ok ? El.GOOD : El.MUTED)
                    .withItem(ok ? "minecraft:experience_bottle" : "minecraft:glass_bottle"));
        }
        return out;
    }

    @Override
    public Result act(ServerPlayer player, String context, String tab, String action, String arg, int num) {
        Optional<Colony> found = Pages.colony(player.server, context);
        if (found.isEmpty()) {
            return Result.NONE;
        }
        Colony c = found.get();
        switch (action) {
            case "goto" -> {
                return Result.go(arg, context, "");
            }
            case "welcome", "turn_away" -> {
                if (!Permissions.can(player.getUUID(), c, Perm.MIGRANTS)) {
                    return Result.say("Only those who may answer migrants can");
                }
                int id;
                try {
                    id = Integer.parseInt(arg);
                } catch (NumberFormatException e) {
                    return Result.NONE;
                }
                Immigration.Answer a = action.equals("welcome") ? Immigration.accept(player, c, id)
                        : Immigration.decline(player.server, c, id);
                return Result.say(a.message());
            }
            default -> {
                return Result.NONE;
            }
        }
    }
}
