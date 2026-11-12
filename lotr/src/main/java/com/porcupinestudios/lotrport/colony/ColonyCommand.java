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
                .then(Commands.literal("manage").executes(ctx -> manage(ctx.getSource(), ""))
                        // straight to a tab: overview, buildings, people, schedules, stock, members
                        .then(Commands.argument("tab", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .executes(ctx -> manage(ctx.getSource(),
                                        com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "tab")))))
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
                        net.minecraft.commands.arguments.EntityArgument.entity())
                        .executes(ctx -> citizen(ctx.getSource(),
                                net.minecraft.commands.arguments.EntityArgument.getEntity(ctx, "npc"), ""))
                        // straight to a tab: overview, mood, schedule, about
                        .then(Commands.argument("tab", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .executes(ctx -> citizen(ctx.getSource(),
                                        net.minecraft.commands.arguments.EntityArgument.getEntity(ctx, "npc"),
                                        com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "tab"))))))
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
                        // /lotr colony upgrade <id>: gives the nearest colony an upgrade (what research will give)
                        .then(Commands.literal("upgrade").then(Commands.argument("id",
                                com.mojang.brigadier.arguments.StringArgumentType.word()).executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    Optional<Colony> c = nearestOf(player);
                                    if (c.isEmpty()) {
                                        ctx.getSource().sendFailure(Component.literal("You belong to no colony"));
                                        return 0;
                                    }
                                    String id = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "id");
                                    java.util.List<String> ups = new java.util.ArrayList<>(c.get().get(
                                            com.porcupinestudios.lotrport.journey.CaravanConditions.UPGRADES)
                                            .orElse(java.util.List.of()));
                                    if (!ups.contains(id)) {
                                        ups.add(id);
                                    }
                                    Colonies.get(player.server).set(c.get(),
                                            com.porcupinestudios.lotrport.journey.CaravanConditions.UPGRADES, ups);
                                    ctx.getSource().sendSuccess(() -> Component.literal(c.get().name()
                                            + " upgrades: " + ups), false);
                                    return 1;
                                })))
                        // /lotr colony event <id>: makes a colony event (colony_event/<id>) happen now, if it can
                        .then(Commands.literal("event").then(Commands.argument("id",
                                net.minecraft.commands.arguments.ResourceLocationArgument.id()).executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    Optional<Colony> c = nearestOf(player);
                                    if (c.isEmpty()) {
                                        ctx.getSource().sendFailure(Component.literal("You belong to no colony"));
                                        return 0;
                                    }
                                    boolean ok = com.porcupinestudios.lotrport.colony.events.ColonyEvents.run(
                                            player.server, c.get(), net.minecraft.commands.arguments
                                                    .ResourceLocationArgument.getId(ctx, "id"), new java.util.Random());
                                    if (!ok) {
                                        ctx.getSource().sendFailure(Component.literal("It found nobody to happen to"));
                                    }
                                    return ok ? 1 : 0;
                                })))
                        // /lotr colony immigrants: draws the nearest colony's immigration board afresh now
                        .then(Commands.literal("immigrants").executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            Optional<Colony> c = nearestOf(player);
                            if (c.isEmpty()) {
                                ctx.getSource().sendFailure(Component.literal("You belong to no colony"));
                                return 0;
                            }
                            Immigration.Board b = Immigration.draw(player.server, c.get(), c.get().get(
                                    Immigration.BOARD).orElse(Immigration.Board.EMPTY), player.server.overworld()
                                            .getGameTime());
                            ctx.getSource().sendSuccess(() -> Component.literal(b.offers().size() + " offers"), false);
                            return 1;
                        }))
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
                        // pin the scheduler's load to see colonies give way (light, normal, heavy, overloaded),
                        // or "auto" to measure again
                        .then(Commands.literal("load").then(Commands.argument("level",
                                com.mojang.brigadier.arguments.StringArgumentType.word()).executes(ctx -> {
                                    String l = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx,
                                            "level").toUpperCase(java.util.Locale.ROOT);
                                    ColonyScheduler.Load load = null;
                                    for (ColonyScheduler.Load x : ColonyScheduler.Load.values()) {
                                        if (x.name().equals(l)) {
                                            load = x;
                                        }
                                    }
                                    if (load == null && !l.equals("AUTO")) {
                                        ctx.getSource().sendFailure(Component.literal(
                                                "light, normal, heavy, overloaded or auto"));
                                        return 0;
                                    }
                                    ColonyScheduler.get(ctx.getSource().getServer()).forceLoad(load);
                                    ctx.getSource().sendSuccess(() -> Component.literal("Colony load: "
                                            + l.toLowerCase(java.util.Locale.ROOT)), true);
                                    return 1;
                                })))
                        // family links by hand until marriage and births exist: parent <child> <parent>,
                        // spouse <a> <b>, divorce <a> <b>
                        // banish a citizen as the citizen page's Banish button does (tests, screenshots)
                        .then(Commands.literal("banish").then(Commands.argument("npc",
                                net.minecraft.commands.arguments.EntityArgument.entity()).executes(ctx -> {
                                    var e = net.minecraft.commands.arguments.EntityArgument.getEntity(ctx, "npc");
                                    Optional<Person> p = Persons.get(ctx.getSource().getServer()).get(e.getUUID());
                                    Optional<Colony> c = p.flatMap(x -> Society.colonyOf(ctx.getSource().getServer(),
                                            x));
                                    if (c.isEmpty()) {
                                        ctx.getSource().sendFailure(Component.literal("Not a citizen"));
                                        return 0;
                                    }
                                    com.porcupinestudios.lotrport.colonist.Colonists.depart(
                                            ctx.getSource().getServer(), c.get(), p.get(),
                                            com.porcupinestudios.lotrport.colonist.Left.BANISHED,
                                            ctx.getSource().getServer().overworld().getGameTime());
                                    ctx.getSource().sendSuccess(() -> Component.literal("Banished"), true);
                                    return 1;
                                })))
                        .then(Commands.literal("kin")
                                .then(Commands.literal("parent").then(Commands.argument("child",
                                        net.minecraft.commands.arguments.EntityArgument.entity()).then(Commands
                                        .argument("parent", net.minecraft.commands.arguments.EntityArgument.entity())
                                        .executes(ctx -> kin(ctx.getSource(),
                                                net.minecraft.commands.arguments.EntityArgument.getEntity(ctx, "child"),
                                                net.minecraft.commands.arguments.EntityArgument.getEntity(ctx,
                                                        "parent"), "parent")))))
                                .then(Commands.literal("spouse").then(Commands.argument("a",
                                        net.minecraft.commands.arguments.EntityArgument.entity()).then(Commands
                                        .argument("b", net.minecraft.commands.arguments.EntityArgument.entity())
                                        .executes(ctx -> kin(ctx.getSource(),
                                                net.minecraft.commands.arguments.EntityArgument.getEntity(ctx, "a"),
                                                net.minecraft.commands.arguments.EntityArgument.getEntity(ctx, "b"),
                                                "spouse")))))
                                .then(Commands.literal("divorce").then(Commands.argument("a",
                                        net.minecraft.commands.arguments.EntityArgument.entity()).then(Commands
                                        .argument("b", net.minecraft.commands.arguments.EntityArgument.entity())
                                        .executes(ctx -> kin(ctx.getSource(),
                                                net.minecraft.commands.arguments.EntityArgument.getEntity(ctx, "a"),
                                                net.minecraft.commands.arguments.EntityArgument.getEntity(ctx, "b"),
                                                "divorce"))))))
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
        ColonyScheduler sch = ColonyScheduler.get(source.getServer());
        source.sendSuccess(() -> Component.literal(String.format(java.util.Locale.ROOT,
                " server %.1f ms a tick without colonies (%s); %d colony days waited, %d caught up, %d waiting now",
                sch.serverTickMs(), sch.load(SimSettings.get()).name().toLowerCase(java.util.Locale.ROOT),
                perf.deferredDays(), perf.caughtUpDays(), sch.backlog())), false);
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

    private static int manage(CommandSourceStack source, String tab)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Optional<Colony> c = Colonies.get(player.server).all().stream()
                .filter(x -> com.porcupinestudios.lotrport.colony.members.Permissions.isMember(player.getUUID(), x))
                .min(java.util.Comparator.comparingDouble(x -> x.hall().distSqr(player.blockPosition())));
        if (c.isEmpty()) {
            source.sendFailure(Component.literal("You belong to no colony"));
            return 0;
        }
        if (tab.isEmpty()) {
            com.porcupinestudios.lotrport.colony.ui.ColonyViews.open(player, c.get());
        } else {
            com.porcupinestudios.lotrport.ui.Views.open(player, new com.porcupinestudios.lotrport.ui.View.Target(
                    com.porcupinestudios.lotrport.colony.ui.ColonyViews.COLONY, c.get().id().toString(), tab), true);
        }
        return 1;
    }

    /** A citizen's page (as sneak + right-click on them), at a tab if given. */
    private static int citizen(CommandSourceStack source, net.minecraft.world.entity.Entity e, String tab)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Optional<Person> p = Persons.get(player.server).get(e.getUUID());
        Optional<Colony> c = p.flatMap(x -> com.porcupinestudios.lotrport.society.Society.colonyOf(player.server, x));
        if (p.isEmpty() || c.isEmpty()
                || !com.porcupinestudios.lotrport.colony.members.Permissions.isMember(player.getUUID(), c.get())) {
            source.sendFailure(Component.literal("Not a citizen of your colony"));
            return 0;
        }
        com.porcupinestudios.lotrport.ui.View.Target target =
                com.porcupinestudios.lotrport.colony.ui.CitizenViews.target(c.get(), p.get());
        com.porcupinestudios.lotrport.ui.Views.open(player, new com.porcupinestudios.lotrport.ui.View.Target(
                target.view(), target.context(), tab), true);
        return 1;
    }

    /**
     * Links two people's kin records both ways: {@code parent} (a is the child of b), {@code spouse} (a marries b;
     * anyone either was married to becomes a former spouse, on both sides) or {@code divorce}.
     */
    private static int kin(CommandSourceStack source, net.minecraft.world.entity.Entity a,
            net.minecraft.world.entity.Entity b, String how) {
        var kinType = com.porcupinestudios.lotrport.colonist.ColonistData.KIN;
        Persons persons = Persons.get(source.getServer());
        Optional<Person> pa = persons.get(a.getUUID());
        Optional<Person> pb = persons.get(b.getUUID());
        if (pa.isEmpty() || pb.isEmpty() || !pa.get().has(kinType) || !pb.get().has(kinType) || pa.equals(pb)) {
            source.sendFailure(Component.literal("Both must be colonists, and two of them"));
            return 0;
        }
        var ka = pa.get().get(kinType).orElseThrow();
        var kb = pb.get().get(kinType).orElseThrow();
        java.util.UUID ia = pa.get().id();
        java.util.UUID ib = pb.get().id();
        switch (how) {
            case "spouse" -> {
                // whoever they were married to is now married to nobody
                java.util.List<java.util.Map.Entry<java.util.UUID, Optional<java.util.UUID>>> was = java.util.List.of(
                        java.util.Map.entry(ia, ka.spouse()), java.util.Map.entry(ib, kb.spouse()));
                for (var side : was) {
                    side.getValue().filter(o -> !o.equals(ia) && !o.equals(ib)).flatMap(persons::get)
                            .ifPresent(o -> o.get(kinType).ifPresent(ko -> persons.set(source.getServer(), o,
                                    kinType, ko.divorce(side.getKey()))));
                }
                ka = ka.marry(ib);
                kb = kb.marry(ia);
            }
            case "divorce" -> {
                ka = ka.divorce(ib);
                kb = kb.divorce(ia);
            }
            default -> {
                java.util.List<java.util.UUID> parents = new java.util.ArrayList<>(ka.parents());
                if (!parents.contains(ib)) {
                    parents.add(ib);
                }
                ka = ka.withParents(parents);
                kb = kb.withChild(ia);
            }
        }
        persons.set(source.getServer(), pa.get(), kinType, ka);
        persons.set(source.getServer(), pb.get(), kinType, kb);
        source.sendSuccess(() -> Component.literal(switch (how) {
            case "spouse" -> "Married";
            case "divorce" -> "Divorced";
            default -> "Linked parent and child";
        }), true);
        return 1;
    }
}
