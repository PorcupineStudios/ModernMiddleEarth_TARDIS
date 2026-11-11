package com.porcupinestudios.lotrport.colony;

import com.porcupinestudios.lotrport.society.Persons;
import com.porcupinestudios.lotrport.society.sim.ColonyScheduler;
import com.porcupinestudios.lotrport.society.sim.Perf;
import com.porcupinestudios.lotrport.society.sim.SimSettings;
import net.minecraft.ChatFormatting;
import com.porcupinestudios.lotrport.society.CoreComponents;
import com.porcupinestudios.lotrport.society.Person;
import com.porcupinestudios.lotrport.society.Society;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /lotr_colony} (your colony: name, faction, citizens, beds, waiting migrants, open recruitments),
 * {@code /lotr_colony accept|deny <migrant>} (the offer links) and, for admins,
 * {@code /lotr colony day [n]} (runs n colony days now) and {@code /lotr colony list}.
 */
public final class ColonyCommand {
    private ColonyCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("lotr_colony")
                // the Town Hall screen of the colony you belong to (the nearest, if several)
                .then(Commands.literal("hall").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    Optional<Colony> c = Colonies.get(player.server).all().stream()
                            .filter(x -> com.porcupinestudios.lotrport.colony.members.Permissions
                                    .isMember(player.getUUID(), x))
                            .min(java.util.Comparator.comparingDouble(x -> x.hall().distSqr(player.blockPosition())));
                    if (c.isEmpty()) {
                        ctx.getSource().sendFailure(Component.literal("You belong to no colony"));
                        return 0;
                    }
                    com.porcupinestudios.lotrport.colony.members.HallNet.send(player, c.get(), true);
                    return 1;
                }))
                // the colony management screen (UI core) of the nearest colony you belong to
                .then(Commands.literal("manage").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    Optional<Colony> c = Colonies.get(player.server).all().stream()
                            .filter(x -> com.porcupinestudios.lotrport.colony.members.Permissions
                                    .isMember(player.getUUID(), x))
                            .min(java.util.Comparator.comparingDouble(x -> x.hall().distSqr(player.blockPosition())));
                    if (c.isEmpty()) {
                        ctx.getSource().sendFailure(Component.literal("You belong to no colony"));
                        return 0;
                    }
                    com.porcupinestudios.lotrport.colony.ui.ColonyViews.open(player, c.get());
                    return 1;
                }))
                // every colony you belong to, to pick one to manage
                .then(Commands.literal("colonies").executes(ctx -> {
                    com.porcupinestudios.lotrport.colony.ui.ColonyViews.openAll(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
                // the [type] links after a writ: change a building's type to another that fits
                .then(Commands.literal("building").then(Commands.argument("door",
                        net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                        .then(Commands.argument("type", net.minecraft.commands.arguments.ResourceLocationArgument.id())
                                .executes(ctx -> {
                                    var r = com.porcupinestudios.lotrport.colony.building.ColonyBuildings.retype(
                                            ctx.getSource().getPlayerOrException(),
                                            net.minecraft.commands.arguments.coordinates.BlockPosArgument
                                                    .getBlockPos(ctx, "door"),
                                            net.minecraft.commands.arguments.ResourceLocationArgument.getId(ctx,
                                                    "type"));
                                    ctx.getSource().sendSystemMessage(r.message());
                                    return r.building().isPresent() ? 1 : 0;
                                }))))
                .then(Commands.literal("buildings").executes(ctx -> buildings(ctx.getSource())))
                // a citizen's page (as sneak + right-click on them)
                .then(Commands.literal("citizen").then(Commands.argument("npc",
                        net.minecraft.commands.arguments.EntityArgument.entity()).executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            var e = net.minecraft.commands.arguments.EntityArgument.getEntity(ctx, "npc");
                            Optional<Person> p = Persons.get(player.server).get(e.getUUID());
                            if (p.isEmpty() || !com.porcupinestudios.lotrport.colony.work.LedgerNet.sendCitizen(player,
                                    p.get())) {
                                ctx.getSource().sendFailure(Component.literal("Not a citizen of your colony"));
                                return 0;
                            }
                            return 1;
                        })))
                // the Ledger of the colony you belong to (the nearest)
                .then(Commands.literal("ledger").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    Optional<Colony> c = Colonies.get(player.server).all().stream()
                            .filter(x -> com.porcupinestudios.lotrport.colony.members.Permissions
                                    .isMember(player.getUUID(), x))
                            .min(java.util.Comparator.comparingDouble(x -> x.hall().distSqr(player.blockPosition())));
                    if (c.isEmpty()) {
                        ctx.getSource().sendFailure(Component.literal("You belong to no colony"));
                        return 0;
                    }
                    com.porcupinestudios.lotrport.colony.work.LedgerNet.send(player, c.get(), true);
                    return 1;
                }))
                .then(Commands.literal("accept").then(Commands.argument("migrant", UuidArgument.uuid())
                        .executes(ctx -> answer(ctx.getSource(), Migrants.accept(ctx.getSource()
                                .getPlayerOrException(), UuidArgument.getUuid(ctx, "migrant"))))))
                .then(Commands.literal("deny").then(Commands.argument("migrant", UuidArgument.uuid())
                        .executes(ctx -> answer(ctx.getSource(), Migrants.deny(ctx.getSource()
                                .getPlayerOrException(), UuidArgument.getUuid(ctx, "migrant"))))))
                .executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            Optional<Colony> colony = Colonies.get(player.server).ofFounder(player.getUUID());
            if (colony.isEmpty()) {
                ctx.getSource().sendFailure(Component.translatableWithFallback("colony.lotr_port.none",
                        "You have not founded a colony (use a Colony Charter)"));
                return 0;
            }
            Colony c = colony.get();
            ResourceLocation f = c.faction();
            int beds = Colonies.beds(player.serverLevel(), c);
            ctx.getSource().sendSuccess(() -> Component.translatableWithFallback("colony.lotr_port.info",
                    "%s (%s): %s citizens, %s beds, Hall at %s", c.name(), Component.translatable("faction."
                            + f.getNamespace() + "." + f.getPath()), Society.citizenCount(player.server, c), beds,
                    c.hall().toShortString()), false);
            for (Person ci : Society.citizens(player.server, c)) {
                boolean rare = ci.get(CoreComponents.CITIZEN).map(CoreComponents.Citizen::rare).orElse(false);
                ctx.getSource().sendSuccess(() -> Component.literal(" - " + ci.name() + " (").append(Component
                        .translatable("npc." + ci.type().getNamespace() + "." + ci.type().getPath()))
                        .append(rare ? ", rare)" : ")"), false);
            }
            Society.applicants(player.server, c).forEach(a -> Migrants.tell(player, c, a));
            c.recruits().keySet().forEach(id -> Recruiting.describe(player, c, id));
            return Society.citizenCount(player.server, c);
        }));
        dispatcher.register(Commands.literal("lotr")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("colony")
                        .then(Commands.literal("list").executes(ctx -> {
                            Colonies all = Colonies.get(ctx.getSource().getServer());
                            all.all().forEach(c -> ctx.getSource().sendSuccess(() -> Component.literal(c.name()
                                    + ": " + Society.population(ctx.getSource().getServer(), c) + " people at " + c.hall().toShortString()),
                                    false));
                            ctx.getSource().sendSuccess(() -> Component.literal("colony data problems: "
                                    + ColonyData.loadProblems()), false);
                            return all.all().size();
                        }))
                        // /lotr colony found <name> [faction]: founds a colony at your feet without a charter
                        .then(Commands.literal("found")
                                .then(Commands.argument("name", com.mojang.brigadier.arguments.StringArgumentType
                                        .string()).executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            Colonies.Founding f = Colonies.get(player.server).found(player,
                                                    com.mojang.brigadier.arguments.StringArgumentType.getString(ctx,
                                                            "name"), player.blockPosition(),
                                                    com.porcupinestudios.lotrport.LotrPort.id("gondor"));
                                            if (f.colony().isEmpty()) {
                                                ctx.getSource().sendFailure(f.problem());
                                                return 0;
                                            }
                                            return 1;
                                        })))
                        // /lotr colony found_ai <name>: an AI-held colony at your feet (a target for testing wars)
                        .then(Commands.literal("found_ai")
                                .then(Commands.argument("name", com.mojang.brigadier.arguments.StringArgumentType
                                        .string()).executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            String name = com.mojang.brigadier.arguments.StringArgumentType
                                                    .getString(ctx, "name");
                                            return Colonies.get(player.server).foundAi(player.server,
                                                    java.util.UUID.randomUUID(), java.util.UUID.nameUUIDFromBytes(
                                                            "lotr_port:test_ruler".getBytes(
                                                                    java.nio.charset.StandardCharsets.UTF_8)),
                                                    name, player.level().dimension(), player.blockPosition(),
                                                    com.porcupinestudios.lotrport.LotrPort.id("mordor"), 20)
                                                    .isPresent() ? 1 : 0;
                                        })))
                        // /lotr colony coins <n>: adds coins to the treasury of the nearest colony you belong to
                        .then(Commands.literal("coins").then(Commands.argument("n", IntegerArgumentType.integer())
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    Optional<Colony> c = nearestOf(player);
                                    if (c.isEmpty()) {
                                        ctx.getSource().sendFailure(Component.literal("You belong to no colony"));
                                        return 0;
                                    }
                                    long now = c.get().get(CoreComponents.TREASURY)
                                            .orElse(CoreComponents.Treasury.EMPTY).coins();
                                    Colonies.get(player.server).set(c.get(), CoreComponents.TREASURY,
                                            new CoreComponents.Treasury(Math.max(0, now
                                                    + IntegerArgumentType.getInteger(ctx, "n"))));
                                    return 1;
                                })))
                        // /lotr colony settle <npcs>: the NPCs join the nearest colony you belong to as citizens
                        .then(Commands.literal("settle")
                                .then(Commands.argument("npcs", net.minecraft.commands.arguments.EntityArgument
                                        .entities()).executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            Optional<Colony> c = nearestOf(player);
                                            if (c.isEmpty()) {
                                                ctx.getSource().sendFailure(Component.literal(
                                                        "You belong to no colony"));
                                                return 0;
                                            }
                                            int n = 0;
                                            for (var e : net.minecraft.commands.arguments.EntityArgument
                                                    .getEntities(ctx, "npcs")) {
                                                if (e instanceof com.porcupinestudios.lotrport.npc.LotrNpc npc
                                                        && npc.owner().isEmpty()) {
                                                    Colonies.get(player.server).join(player.server, c.get(), npc,
                                                            false);
                                                    n++;
                                                }
                                            }
                                            int settled = n;
                                            ctx.getSource().sendSuccess(() -> Component.literal(settled
                                                    + " settled in " + c.get().name()), false);
                                            return n;
                                        })))
                        .then(Commands.literal("perf")
                                .executes(ctx -> perf(ctx.getSource(), false))
                                .then(Commands.literal("reset").executes(ctx -> perf(ctx.getSource(), true))))
                        .then(Commands.literal("day")
                                .executes(ctx -> days(ctx.getSource(), 1))
                                .then(Commands.argument("n", IntegerArgumentType.integer(1, 100))
                                        .executes(ctx -> days(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "n")))))));
    }

    /** The colony the player belongs to nearest them. */
    static Optional<Colony> nearestOf(ServerPlayer player) {
        return Colonies.get(player.server).all().stream()
                .filter(x -> com.porcupinestudios.lotrport.colony.members.Permissions.isMember(player.getUUID(), x))
                .min(java.util.Comparator.comparingDouble(x -> x.hall().distSqr(player.blockPosition())));
    }

    /** Scheduler timing: per server tick (mean and longest) against the budget, then per system. */
    private static int perf(CommandSourceStack source, boolean reset) {
        Perf perf = ColonyScheduler.get(source.getServer()).perf();
        if (reset) {
            perf.reset();
            source.sendSuccess(() -> Component.literal("Colony timing reset"), false);
            return 1;
        }
        double budget = SimSettings.get().budgetMs();
        Perf.Stat t = perf.ticks();
        double meanMs = t.meanMicros() / 1000.0;
        double maxMs = t.maxNanos / 1_000_000.0;
        int colonies = Colonies.get(source.getServer()).all().size();
        int persons = Persons.get(source.getServer()).size();
        source.sendSuccess(() -> Component.literal(String.format(java.util.Locale.ROOT,
                "%d colonies, %d people: %.3f ms mean, %.3f ms longest per tick over %d ticks, %d over the %.1f ms budget",
                colonies, persons, meanMs, maxMs, t.runs, perf.overBudget(), budget))
                .withStyle(maxMs > budget ? ChatFormatting.RED : ChatFormatting.GREEN), false);
        perf.systems().forEach((id, st) -> source.sendSuccess(() -> Component.literal(String.format(
                java.util.Locale.ROOT, " %s: %d runs, %.1f us mean, %.1f us longest", id, st.runs, st.meanMicros(),
                st.maxNanos / 1000.0)), false));
        return (int) Math.ceil(maxMs);
    }

    private static int answer(CommandSourceStack source, Migrants.Answer answer) {
        if (answer.ok()) {
            source.sendSuccess(answer::message, false);
            return 1;
        }
        source.sendFailure(answer.message());
        return 0;
    }

    private static int days(CommandSourceStack source, int n) throws com.mojang.brigadier.exceptions
            .CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Colonies all = Colonies.get(player.server);
        Optional<Colony> colony = all.ofFounder(player.getUUID());
        if (colony.isEmpty()) {
            source.sendFailure(Component.literal("You have no colony"));
            return 0;
        }
        int came = 0;
        for (int i = 0; i < n; i++) {
            came += all.day(player.serverLevel(), colony.get());
        }
        int total = came;
        source.sendSuccess(() -> Component.literal(total + " newcomers"), true);
        return came;
    }

    /** Lists the buildings of the colony you belong to (the nearest). */
    static int buildings(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Optional<Colony> c = Colonies.get(player.server).all().stream()
                .filter(x -> com.porcupinestudios.lotrport.colony.members.Permissions.isMember(player.getUUID(), x))
                .min(java.util.Comparator.comparingDouble(x -> x.hall().distSqr(player.blockPosition())));
        if (c.isEmpty()) {
            source.sendFailure(Component.literal("You belong to no colony"));
            return 0;
        }
        var list = com.porcupinestudios.lotrport.colony.building.Buildings.of(c.get()).list();
        source.sendSystemMessage(Component.translatableWithFallback("colony.lotr_port.buildings.header",
                "%s: %s buildings", c.get().name(), list.size()));
        for (var b : list) {
            source.sendSystemMessage(Component.literal(" - ").append(b.label()).append(Component.literal(
                    " at " + b.door().toShortString() + ", quality " + b.quality() + " ("
                            + com.porcupinestudios.lotrport.colony.building.ColonyBuildings.describe(b.counts())
                            + ")")));
        }
        return list.size();
    }
}
