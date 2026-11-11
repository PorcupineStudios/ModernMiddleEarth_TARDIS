package com.porcupinestudios.lotrport.colony;

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
            Optional<Colony> colony = Colonies.get(player.server).nearest(player);
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
                            + f.getNamespace() + "." + f.getPath()), c.citizens().size(), beds,
                    c.hall().toShortString()), false);
            for (Colony.Citizen ci : c.citizens()) {
                ctx.getSource().sendSuccess(() -> Component.literal(" - " + ci.name() + " (").append(Component
                        .translatable("npc." + ci.type().getNamespace() + "." + ci.type().getPath()))
                        .append(ci.rare() ? ", rare)" : ")"), false);
            }
            c.applicants().forEach(a -> Migrants.tell(player, c, a));
            c.recruits().keySet().forEach(id -> Recruiting.describe(player, c, id));
            return c.citizens().size();
        }));
        dispatcher.register(Commands.literal("lotr")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("colony")
                        .then(Commands.literal("list").executes(ctx -> {
                            Colonies all = Colonies.get(ctx.getSource().getServer());
                            all.all().forEach(c -> ctx.getSource().sendSuccess(() -> Component.literal(c.name()
                                    + ": " + c.citizens().size() + " citizens at " + c.hall().toShortString()),
                                    false));
                            ctx.getSource().sendSuccess(() -> Component.literal("colony data problems: "
                                    + ColonyData.loadProblems()), false);
                            return all.all().size();
                        }))
                        .then(Commands.literal("day")
                                .executes(ctx -> days(ctx.getSource(), 1))
                                .then(Commands.argument("n", IntegerArgumentType.integer(1, 100))
                                        .executes(ctx -> days(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "n")))))));
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
        Optional<Colony> colony = all.nearest(player);
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
