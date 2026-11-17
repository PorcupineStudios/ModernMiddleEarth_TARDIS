package com.porcupinestudios.lotrport.colony.book;

import com.porcupinestudios.lotrport.colony.Colonies;
import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.colony.members.Perm;
import com.porcupinestudios.lotrport.colony.members.Permissions;
import com.porcupinestudios.lotrport.war.War;
import com.porcupinestudios.lotrport.war.WarSettings;
import com.porcupinestudios.lotrport.war.Wars;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The Town Hall ledger's War tab: the colony's wars (score, armies, peace offers) with buttons to muster, offer and
 * accept peace, and the colonies it could declare on. Replaces the chat links of {@code /lotr_war}.
 */
final class WarPage {
    /** The reason each player last wrote for a war (a declaration needs one). */
    private static final Map<UUID, String> REASONS = new ConcurrentHashMap<>();
    private static final int TARGETS = 12;

    private WarPage() {}

    static List<El> page(ServerPlayer player, Colony c) {
        MinecraftServer server = player.server;
        boolean may = Permissions.can(player.getUUID(), c, Perm.WAR);
        String why = may ? "" : "Only those who may make war can";
        long now = server.overworld().getGameTime();
        List<El> out = new ArrayList<>();
        List<War> wars = Wars.get(server).of(c.asOwner()).stream().filter(w -> w.state() != War.State.ENDED)
                .toList();
        out.add(El.head(wars.isEmpty() ? "At peace" : "Wars"));
        if (wars.isEmpty()) {
            out.add(El.text(c.name() + " is at war with nobody.", El.MUTED));
        }
        for (War w : wars) {
            boolean attacking = w.attacker().equals(c.asOwner());
            War.Tally t = w.tally();
            String enemy = Wars.name(server, attacking ? w.defender() : w.attacker());
            String state = w.state() == War.State.WARNING
                    ? "begins in " + minutes(w.activeFrom() - now)
                    : "being fought";
            List<El> kids = new ArrayList<>();
            int ours = attacking ? t.scoreA() : t.scoreB();
            int theirs = attacking ? t.scoreB() : t.scoreA();
            double tired = attacking ? t.exhaustionA() : t.exhaustionB();
            kids.add(El.meter("Score", ours + " : " + theirs, ours + theirs == 0 ? 0.5 : ours / (double) (ours
                    + theirs)));
            kids.add(El.meter("Exhaustion", Math.round(tired) + " / 100", tired / 100));
            boolean active = w.state() == War.State.ACTIVE;
            if (attacking) {
                kids.add(El.button("Siege", "muster", w.shortId() + "|siege", may && active,
                        active ? why : "The war hasn't begun"));
                kids.add(El.button("Raid", "muster", w.shortId() + "|raid", may && active,
                        active ? why : "The war hasn't begun"));
            }
            int cap = Wars.tributeCap(w, attacking);
            kids.add(El.button(cap > 0 ? "Peace for " + cap + " coins" : "Offer peace", "peace", w.shortId(), may,
                    why));
            Optional<War.Proposal> offer = w.proposal().filter(p -> p.byAttacker() != attacking);
            offer.ifPresent(p -> kids.add(El.button(p.tribute() > 0 ? "Accept (pay " + p.tribute() + ")"
                    : "Accept peace", "accept", w.shortId(), may, why)));
            out.add(El.card("minecraft:iron_sword", (attacking ? "Against " : "Attacked by ") + enemy,
                    w.goal().getSerializedName().replace('_', ' ') + ", " + state, offer.isPresent() ? El.WARN
                            : El.BAD, "", "", kids));
            for (War.Army a : w.armies()) {
                int men = a.soldiers().size() + a.strength();
                String where = switch (a.stage()) {
                    case MARCHING -> "arrives in " + minutes(a.arrives() - now);
                    case SIEGE -> "holds the Hall " + minutes(a.held()) + " of "
                            + minutes(WarSettings.get().armies().siegeHold());
                    case RETURNING -> "home in " + minutes(a.arrives() - now);
                };
                out.add(El.pair(men + " " + a.kind().getSerializedName(), where, El.MUTED)
                        .withItem("minecraft:iron_helmet"));
            }
            if (!w.reason().isEmpty()) {
                out.add(El.text("\"" + w.reason() + "\"", El.MUTED));
            }
        }

        out.add(El.head("Declare war"));
        String reason = REASONS.getOrDefault(player.getUUID(), "");
        out.add(El.pair("Reason", reason.isEmpty() ? "none written yet" : reason, reason.isEmpty() ? El.BAD
                : El.NORMAL));
        out.add(El.field("Write the reason, then Enter", "", "reason", may, why));
        List<Colony> others = Colonies.get(server).all().stream().filter(x -> !x.id().equals(c.id()))
                .sorted(Comparator.comparingDouble(x -> x.hall().distSqr(c.hall()))).limit(TARGETS).toList();
        if (others.isEmpty()) {
            out.add(El.text("No other colony stands in Middle-earth yet.", El.MUTED));
        }
        for (Colony o : others) {
            int dist = (int) Math.sqrt(o.hall().distSqr(c.hall()));
            boolean atWar = Wars.get(server).atWar(c.asOwner(), o.asOwner());
            List<El> kids = new ArrayList<>();
            for (War.Goal g : War.Goal.values()) {
                if (g == War.Goal.FREE_VASSAL) {
                    continue;
                }
                String label = switch (g) {
                    case TRIBUTE -> "For tribute";
                    case TAKE_COLONY -> "To take it";
                    case VASSAL -> "To make a vassal";
                    default -> g.getSerializedName();
                };
                String no = !may ? why : atWar ? "Already at war" : reason.isEmpty() ? "Write a reason first" : "";
                kids.add(El.button(label, "declare", o.id() + "|" + g.getSerializedName(), no.isEmpty(), no));
            }
            out.add(El.card("minecraft:white_banner", o.name(), dist + " blocks, march "
                    + minutes(WarSettings.get().armies().march(dist)) + (o.ai() ? ", AI realm" : ""),
                    atWar ? El.BAD : El.NORMAL, "", "", kids));
        }
        return out;
    }

    static Optional<BookProvider.Result> act(ServerPlayer player, Colony c, String action, String arg) {
        MinecraftServer server = player.server;
        Wars wars = Wars.get(server);
        switch (action) {
            case "reason" -> {
                String r = arg.trim();
                if (r.isEmpty()) {
                    REASONS.remove(player.getUUID());
                } else {
                    REASONS.put(player.getUUID(), r);
                }
                return Optional.of(BookProvider.Result.say(r.isEmpty() ? "Reason cleared" : "Reason written"));
            }
            case "declare" -> {
                String[] p = arg.split("\\|", 2);
                if (p.length < 2) {
                    return Optional.of(BookProvider.Result.NONE);
                }
                Optional<Colony> target = Pages.colony(server, p[0]);
                Optional<War.Goal> goal = java.util.Arrays.stream(War.Goal.values())
                        .filter(g -> g.getSerializedName().equals(p[1])).findFirst();
                if (target.isEmpty() || goal.isEmpty()) {
                    return Optional.of(BookProvider.Result.NONE);
                }
                Wars.Result r = wars.declare(server, player.getUUID(), c, target.get(), goal.get(),
                        REASONS.getOrDefault(player.getUUID(), ""), Optional.empty());
                if (r.ok()) {
                    REASONS.remove(player.getUUID());
                }
                return Optional.of(BookProvider.Result.say(r.message()));
            }
            case "muster" -> {
                String[] p = arg.split("\\|", 2);
                Optional<War> w = wars.find(p[0]);
                if (w.isEmpty() || p.length < 2) {
                    return Optional.of(BookProvider.Result.NONE);
                }
                boolean siege = p[1].equals("siege");
                int count = siege ? 10 : WarSettings.get().armies().raidCap();
                return Optional.of(BookProvider.Result.say(wars.muster(server, player.getUUID(), w.get(),
                        siege ? War.Army.Kind.SIEGE : War.Army.Kind.RAID, count).message()));
            }
            case "peace" -> {
                Optional<War> w = wars.find(arg);
                if (w.isEmpty()) {
                    return Optional.of(BookProvider.Result.NONE);
                }
                boolean attacking = w.get().attacker().equals(c.asOwner());
                return Optional.of(BookProvider.Result.say(wars.propose(server, player.getUUID(), w.get(),
                        Wars.tributeCap(w.get(), attacking)).message()));
            }
            case "accept" -> {
                Optional<War> w = wars.find(arg);
                return Optional.of(w.isEmpty() ? BookProvider.Result.NONE
                        : BookProvider.Result.say(wars.accept(server, player.getUUID(), w.get()).message()));
            }
            default -> {
                return Optional.empty();
            }
        }
    }

    static String minutes(long ticks) {
        long t = Math.max(0, ticks);
        long m = t / 1200;
        long s = t % 1200 / 20;
        return m > 0 ? m + " min" + (s > 0 ? " " + s + " s" : "") : s + " s";
    }
}
