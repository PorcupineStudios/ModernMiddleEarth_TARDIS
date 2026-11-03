package com.porcupinestudios.lotrport.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.porcupinestudios.lotrport.faction.Alignment;
import com.porcupinestudios.ringbearer.api.history.HistoryLog;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** {@code /lotr}: admin and test commands. */
public final class LotrCommand {
    private LotrCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("lotr")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("alignment")
                        .then(Commands.literal("get")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("faction", ResourceLocationArgument.id())
                                                .executes(ctx -> {
                                                    ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
                                                    ResourceLocation faction =
                                                            ResourceLocationArgument.getId(ctx, "faction");
                                                    float value = Alignment.get(player, faction);
                                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                                            player.getScoreboardName() + " has " + value
                                                                    + " alignment with " + faction), false);
                                                    return Math.round(value);
                                                }))))
                        .then(Commands.literal("set")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("faction", ResourceLocationArgument.id())
                                                .then(Commands.argument("value", FloatArgumentType.floatArg())
                                                        .executes(ctx -> {
                                                            ServerPlayer player =
                                                                    EntityArgument.getPlayer(ctx, "player");
                                                            ResourceLocation faction =
                                                                    ResourceLocationArgument.getId(ctx, "faction");
                                                            float value = FloatArgumentType.getFloat(ctx, "value");
                                                            Alignment.set(player, faction, value);
                                                            ctx.getSource().sendSuccess(() -> Component.literal(
                                                                    "Set " + player.getScoreboardName()
                                                                            + "'s alignment with " + faction + " to "
                                                                            + value), true);
                                                            return 1;
                                                        }))))))
                .then(Commands.literal("history")
                        .executes(ctx -> {
                            int size = HistoryLog.get(ctx.getSource().getServer()).size();
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "The world history log holds " + size + " entries"), false);
                            return size;
                        })));
    }
}
