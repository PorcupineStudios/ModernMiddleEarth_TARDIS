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
                        .then(Commands.literal("perf")
                                .executes(ctx -> perf(ctx.getSource(), false))
                                .then(Commands.literal("reset").executes(ctx -> perf(ctx.getSource(), true))))
                        .then(Commands.literal("day")
                                .executes(ctx -> days(ctx.getSource(), 1))
                                .then(Commands.argument("n", IntegerArgumentType.integer(1, 100))
                                        .executes(ctx -> days(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "n")))))));
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
                "%d colonies, %d people: %.3f ms mean, %.3f ms longest per tick over %d ticks (budget %.1f ms)",
                colonies, persons, meanMs, maxMs, t.runs, budget))
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
}
