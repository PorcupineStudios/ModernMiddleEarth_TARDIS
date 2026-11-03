package com.porcupinestudios.lotrport.ai;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.porcupinestudios.lotrport.colony.Colonies;
import com.porcupinestudios.lotrport.colony.Colony;
import java.util.List;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Admin: {@code /lotr ai list} (rulers, strength, wars, colonies) and {@code /lotr ai day [n]} (runs n AI days). */
public final class AiCommand {
    private AiCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("lotr")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("ai")
                        .then(Commands.literal("list").executes(ctx -> {
                            var server = ctx.getSource().getServer();
                            Map<ResourceLocation, AiRulers.State> states = AiRulers.get(server).states();
                            for (Map.Entry<ResourceLocation, AiData.RulerDef> e : AiData.rulers().entrySet()) {
                                AiRulers.State st = states.get(e.getKey());
                                List<Colony> mine = Colonies.get(server).ownedBy(AiRulers.uuid(e.getKey()));
                                String line = e.getValue().name() + " (" + e.getKey().getPath() + "): "
                                        + (st == null ? "not started" : st.strength() + " soldiers, at war with "
                                                + st.wars().keySet())
                                        + ", " + mine.size() + " colonies";
                                ctx.getSource().sendSuccess(() -> Component.literal(line), false);
                            }
                            ctx.getSource().sendSuccess(() -> Component.literal("ai data problems: "
                                    + AiData.loadProblems()), false);
                            return AiData.rulers().size();
                        }))
                        .then(Commands.literal("day")
                                .executes(ctx -> days(ctx.getSource(), 1))
                                .then(Commands.argument("n", IntegerArgumentType.integer(1, 365))
                                        .executes(ctx -> days(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "n")))))));
    }

    private static int days(CommandSourceStack source, int n) {
        long day = AiRulers.get(source.getServer()).runDays(source.getServer(), n);
        source.sendSuccess(() -> Component.literal("Ran " + n + " AI days (now day " + day + ")"), true);
        return n;
    }
}
