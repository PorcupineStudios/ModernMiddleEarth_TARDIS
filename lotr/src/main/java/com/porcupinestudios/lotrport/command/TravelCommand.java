package com.porcupinestudios.lotrport.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.porcupinestudios.lotrport.quest.ActiveQuest;
import com.porcupinestudios.lotrport.quest.QuestData;
import com.porcupinestudios.lotrport.quest.QuestLog;
import com.porcupinestudios.lotrport.quest.Quests;
import com.porcupinestudios.lotrport.registry.LotrRegistries;
import com.porcupinestudios.lotrport.travel.FastTravel;
import com.porcupinestudios.lotrport.travel.Waypoint;
import java.util.Map;
import java.util.TreeSet;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Players: {@code /lotr_travel <waypoint>}, {@code /lotr_waypoints}, {@code /lotr_quests [abandon <n>]}. Admins:
 * {@code /lotr waypoint unlock <region|all>}, {@code /lotr waypoint ready} (clears the wait), {@code /lotr waypoint
 * info}. The map screen (M) does the same as /lotr_travel.
 */
public final class TravelCommand {
    private static final SuggestionProvider<CommandSourceStack> WAYPOINTS =
            (ctx, builder) -> SharedSuggestionProvider.suggestResource(FastTravel.waypoints().keySet(), builder);
    private static final SuggestionProvider<CommandSourceStack> REGIONS = (ctx, builder) -> {
        TreeSet<String> regions = new TreeSet<>();
        FastTravel.waypoints().values().forEach(w -> regions.add(w.region()));
        regions.add("all");
        return SharedSuggestionProvider.suggest(regions, builder);
    };

    private TravelCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("lotr_travel")
                .then(Commands.argument("waypoint", ResourceLocationArgument.id()).suggests(WAYPOINTS)
                        .executes(ctx -> FastTravel.travel(ctx.getSource().getPlayerOrException(),
                                ResourceLocationArgument.getId(ctx, "waypoint")) ? 1 : 0)));
        dispatcher.register(Commands.literal("lotr_waypoints").executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            int n = 0;
            for (Map.Entry<ResourceLocation, Waypoint> e : FastTravel.waypoints().entrySet()) {
                if (FastTravel.problem(player, e.getKey()).isEmpty()) {
                    ResourceLocation id = e.getKey();
                    ctx.getSource().sendSuccess(() -> Component.literal(" ").append(FastTravel.name(id))
                            .append(" (" + id + ")"), false);
                    n++;
                }
            }
            int count = n;
            ctx.getSource().sendSuccess(() -> Component.translatableWithFallback("waypoint.lotr_port.count",
                    "%s waypoints you can travel to now", count), false);
            return n;
        }));
        dispatcher.register(Commands.literal("lotr_quests")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    QuestLog log = Quests.log(player);
                    for (int i = 0; i < log.active().size(); i++) {
                        ActiveQuest q = log.active().get(i);
                        int index = i;
                        ctx.getSource().sendSuccess(() -> Component.literal(index + ". " + q.giverName() + ": ")
                                .append(Quests.describe(q)).append(" (" + Quests.progress(player, q) + "/"
                                        + q.count() + ")"), false);
                    }
                    ctx.getSource().sendSuccess(() -> Component.translatableWithFallback("quest.lotr_port.count",
                            "%s open quests, %s completed", log.active().size(), log.completed()), false);
                    return log.active().size();
                })
                .then(Commands.literal("abandon").then(Commands.argument("n", IntegerArgumentType.integer(0))
                        .executes(ctx -> Quests.abandon(ctx.getSource().getPlayerOrException(),
                                IntegerArgumentType.getInteger(ctx, "n")) ? 1 : 0))));
        dispatcher.register(Commands.literal("lotr")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("waypoint")
                        .then(Commands.literal("unlock").then(Commands.argument("region", StringArgumentType.word())
                                .suggests(REGIONS)
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    String region = StringArgumentType.getString(ctx, "region");
                                    int n = 0;
                                    for (Waypoint w : FastTravel.waypoints().values()) {
                                        if ((region.equals("all") || w.region().equals(region))
                                                && FastTravel.unlock(player, w.region())) {
                                            n++;
                                        }
                                    }
                                    return n;
                                })))
                        .then(Commands.literal("ready").executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            player.setData(LotrRegistries.TRAVEL, FastTravel.data(player).ready());
                            return 1;
                        }))
                        .then(Commands.literal("info").executes(ctx -> {
                            int n = FastTravel.waypoints().size();
                            ctx.getSource().sendSuccess(() -> Component.literal(n + " waypoints, "
                                    + QuestData.pools().size() + " quest pools, problems: "
                                    + FastTravel.loadProblems() + " " + QuestData.loadProblems()), false);
                            return n;
                        }))));
    }
}
