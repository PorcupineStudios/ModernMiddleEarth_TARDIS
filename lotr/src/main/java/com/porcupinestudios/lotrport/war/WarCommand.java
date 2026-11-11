package com.porcupinestudios.lotrport.war;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.porcupinestudios.lotrport.colony.Colonies;
import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.colony.members.Perm;
import com.porcupinestudios.lotrport.colony.members.Permissions;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /lotr_war}: your colonies' wars with score, exhaustion and armies, and links to act.
 * <ul>
 * <li>{@code targets}: colonies you could declare on, with [Declare] links</li>
 * <li>{@code declare <colony> <goal> <reason>}: goal tribute, take_colony, vassal or free_vassal</li>
 * <li>{@code muster <war> siege|raid <soldiers>}</li>
 * <li>{@code peace <war> [tribute]} offers peace; {@code accept <war>} takes the other side's offer</li>
 * </ul>
 */
public final class WarCommand {
    private WarCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("lotr_war")
                .executes(ctx -> list(ctx.getSource()))
                .then(Commands.literal("targets").executes(ctx -> targets(ctx.getSource())))
                .then(Commands.literal("declare").then(Commands.argument("colony", UuidArgument.uuid())
                        .then(Commands.argument("goal", StringArgumentType.word())
                                .suggests((c, b) -> {
                                    for (War.Goal g : War.Goal.values()) {
                                        b.suggest(g.getSerializedName());
                                    }
                                    return b.buildFuture();
                                })
                                .then(Commands.argument("reason", StringArgumentType.greedyString())
                                        .executes(WarCommand::declare)))))
                .then(Commands.literal("muster").then(Commands.argument("war", StringArgumentType.word())
                        .then(Commands.literal("siege").then(Commands.argument("soldiers",
                                IntegerArgumentType.integer(1, 200)).executes(ctx -> muster(ctx,
                                        War.Army.Kind.SIEGE))))
                        .then(Commands.literal("raid").then(Commands.argument("soldiers",
                                IntegerArgumentType.integer(1, 200)).executes(ctx -> muster(ctx,
                                        War.Army.Kind.RAID))))))
                .then(Commands.literal("peace").then(Commands.argument("war", StringArgumentType.word())
                        .executes(ctx -> peace(ctx, 0))
                        .then(Commands.argument("tribute", IntegerArgumentType.integer(0))
                                .executes(ctx -> peace(ctx, IntegerArgumentType.getInteger(ctx, "tribute"))))))
                .then(Commands.literal("accept").then(Commands.argument("war", StringArgumentType.word())
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            Optional<War> war = war(ctx);
                            if (war.isEmpty()) {
                                return 0;
                            }
                            return answer(ctx.getSource(), Wars.get(player.server).accept(player.server,
                                    player.getUUID(), war.get()));
                        }))));
    }

    private static Optional<War> war(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "war");
        Optional<War> war = Wars.get(ctx.getSource().getServer()).find(id);
        if (war.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("No war " + id));
        }
        return war;
    }

    private static int answer(CommandSourceStack source, Wars.Result r) {
        if (r.ok()) {
            source.sendSuccess(r::message, false);
            return 1;
        }
        source.sendFailure(r.message());
        return 0;
    }

    /** The colonies the player may make war for: their own first, then the nearest. */
    private static List<Colony> mine(ServerPlayer player) {
        return Colonies.get(player.server).all().stream()
                .filter(c -> Permissions.can(player.getUUID(), c, Perm.WAR))
                .sorted(Comparator.comparingDouble(c -> c.hall().distSqr(player.blockPosition()))).toList();
    }

    private static int declare(CommandContext<CommandSourceStack> ctx) throws
            com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        MinecraftServer server = player.server;
        Optional<Colony> target = Colonies.get(server).get(UuidArgument.getUuid(ctx, "colony"));
        List<Colony> mine = mine(player);
        String goalName = StringArgumentType.getString(ctx, "goal").toUpperCase(Locale.ROOT);
        War.Goal goal;
        try {
            goal = War.Goal.valueOf(goalName);
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendFailure(Component.literal("Goals: tribute, take_colony, vassal, free_vassal"));
            return 0;
        }
        if (target.isEmpty() || mine.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal(mine.isEmpty() ? "You lead no colony" : "No such colony"));
            return 0;
        }
        return answer(ctx.getSource(), Wars.get(server).declare(server, player.getUUID(), mine.get(0), target.get(),
                goal, StringArgumentType.getString(ctx, "reason").strip(), Optional.empty()));
    }

    private static int muster(CommandContext<CommandSourceStack> ctx, War.Army.Kind kind) throws
            com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Optional<War> war = war(ctx);
        if (war.isEmpty()) {
            return 0;
        }
        return answer(ctx.getSource(), Wars.get(player.server).muster(player.server, player.getUUID(), war.get(),
                kind, IntegerArgumentType.getInteger(ctx, "soldiers")));
    }

    private static int peace(CommandContext<CommandSourceStack> ctx, int tribute) throws
            com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Optional<War> war = war(ctx);
        if (war.isEmpty()) {
            return 0;
        }
        return answer(ctx.getSource(), Wars.get(player.server).propose(player.server, player.getUUID(), war.get(),
                tribute));
    }

    private static MutableComponent link(String label, String command, boolean run) {
        return Component.literal("[" + label + "]").withStyle(s -> s.withColor(ChatFormatting.AQUA)
                .withClickEvent(new ClickEvent(run ? ClickEvent.Action.RUN_COMMAND : ClickEvent.Action.SUGGEST_COMMAND,
                        command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(command))));
    }

    private static int targets(CommandSourceStack source) throws
            com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        List<Colony> mine = mine(player);
        if (mine.isEmpty()) {
            source.sendFailure(Component.literal("You lead no colony"));
            return 0;
        }
        Colony home = mine.get(0);
        List<Colony> others = Colonies.get(player.server).all().stream().filter(c -> !c.id().equals(home.id()))
                .sorted(Comparator.comparingDouble(c -> c.hall().distSqr(home.hall()))).limit(12).toList();
        source.sendSystemMessage(Component.literal("Colonies " + home.name() + " could declare on:")
                .withStyle(ChatFormatting.GOLD));
        for (Colony c : others) {
            int dist = (int) Math.sqrt(c.hall().distSqr(home.hall()));
            MutableComponent line = Component.literal(" " + c.name() + " (" + dist + " blocks, march "
                    + Wars.minutes(WarSettings.get().armies().march(dist)).getString() + ") ");
            for (War.Goal g : War.Goal.values()) {
                if (g != War.Goal.FREE_VASSAL) {
                    line.append(link(g.getSerializedName().replace('_', ' '), "/lotr_war declare " + c.id() + " "
                            + g.getSerializedName() + " ", false)).append(" ");
                }
            }
            source.sendSystemMessage(line);
        }
        return others.size();
    }

    private static int list(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        MinecraftServer server = player.server;
        Wars wars = Wars.get(server);
        int n = 0;
        for (Colony c : Colonies.get(server).all()) {
            if (!Permissions.isMember(player.getUUID(), c)) {
                continue;
            }
            for (War w : wars.of(c.asOwner())) {
                n++;
                boolean attacking = w.attacker().equals(c.asOwner());
                long now = Wars.now(server);
                source.sendSystemMessage(Component.literal(Wars.name(server, w.attacker()) + " vs "
                        + Wars.name(server, w.defender()) + " (" + w.goal().getSerializedName().replace('_', ' ')
                        + ", " + (w.state() == War.State.WARNING ? "begins in "
                                + Wars.minutes((int) (w.activeFrom() - now)).getString() : "fought") + ") #"
                        + w.shortId()).withStyle(ChatFormatting.GOLD));
                War.Tally t = w.tally();
                source.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                        " score %d : %d, exhaustion %.0f : %.0f, reason: %s", t.scoreA(), t.scoreB(),
                        t.exhaustionA(), t.exhaustionB(), w.reason())));
                for (War.Army a : w.armies()) {
                    long left = a.arrives() - now;
                    source.sendSystemMessage(Component.literal(" army: " + (a.soldiers().size() + a.strength()) + " "
                            + a.kind().getSerializedName() + ", " + switch (a.stage()) {
                                case MARCHING -> "arrives in " + Wars.minutes((int) left).getString();
                                case SIEGE -> "besieging, held " + Wars.minutes((int) a.held()).getString() + " of "
                                        + Wars.minutes(WarSettings.get().armies().siegeHold()).getString();
                                case RETURNING -> "home in " + Wars.minutes((int) left).getString();
                            }));
                }
                MutableComponent actions = Component.literal(" ");
                if (attacking && w.state() == War.State.ACTIVE) {
                    actions.append(link("Siege", "/lotr_war muster " + w.shortId() + " siege 10", false)).append(" ")
                            .append(link("Raid", "/lotr_war muster " + w.shortId() + " raid "
                                    + WarSettings.get().armies().raidCap(), false)).append(" ");
                }
                actions.append(link("Offer peace", "/lotr_war peace " + w.shortId() + " "
                        + Wars.tributeCap(w, attacking), false));
                w.proposal().filter(p -> p.byAttacker() != attacking).ifPresent(p -> actions.append(" ").append(
                        link("Accept peace (" + p.tribute() + " coins)", "/lotr_war accept " + w.shortId(), true)));
                source.sendSystemMessage(actions);
            }
        }
        if (n == 0) {
            source.sendSystemMessage(Component.literal("Your colonies are at peace. ").append(link("Targets",
                    "/lotr_war targets", true)));
        }
        return n;
    }

    /** For tests and other code: the war of a colony against another, if any. */
    public static Optional<War> between(MinecraftServer server, UUID a, UUID b) {
        return Wars.get(server).between(com.porcupinestudios.lotrport.society.Owner.colony(a),
                com.porcupinestudios.lotrport.society.Owner.colony(b));
    }
}
