package com.porcupinestudios.lotrport.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.porcupinestudios.lotrport.road.RoadData;
import com.porcupinestudios.lotrport.road.RoadPaving;
import com.porcupinestudios.lotrport.road.RoadWorks;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * {@code /lotr road}: road works by hand until caravans and Waymaker stations make them (lotr/docs/roads.md):
 * {@code add <tier> <x1> <z1> <x2> <z2>}, {@code list}, {@code tier <id> <tier>}, {@code wear <id> <amount>},
 * {@code remove <id>}, {@code tiers}.
 */
public final class RoadCommand {
    private RoadCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("road")
                .then(Commands.literal("tiers").executes(ctx -> {
                    List<ResourceLocation> ladder = RoadData.ladder();
                    for (ResourceLocation id : ladder) {
                        RoadData.tier(id).ifPresent(t -> say(ctx, id + ": rank " + t.rank() + ", built by "
                                + t.builtBy() + (t.byTraffic() ? ", wear " + t.wear() : "")));
                    }
                    return ladder.size();
                }))
                .then(Commands.literal("list").executes(ctx -> {
                    List<RoadWorks.Stretch> all = RoadWorks.get(ctx.getSource().getServer()).all();
                    for (RoadWorks.Stretch s : all) {
                        RoadWorks.Point a = s.points.get(0);
                        RoadWorks.Point b = s.points.get(s.points.size() - 1);
                        say(ctx, "#" + s.id + " " + s.tier() + " (" + s.source + ", wear " + s.wear() + ") "
                                + a.x() + " " + a.z() + " -> " + b.x() + " " + b.z() + " in "
                                + s.dimension.location());
                    }
                    return all.size();
                }))
                .then(Commands.literal("add")
                        .then(Commands.argument("tier", ResourceLocationArgument.id()).suggests(RoadCommand::tiers)
                                .then(Commands.argument("x1", IntegerArgumentType.integer())
                                        .then(Commands.argument("z1", IntegerArgumentType.integer())
                                                .then(Commands.argument("x2", IntegerArgumentType.integer())
                                                        .then(Commands.argument("z2", IntegerArgumentType.integer())
                                                                .executes(RoadCommand::add)))))))
                .then(Commands.literal("tier")
                        .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                .then(Commands.argument("tier", ResourceLocationArgument.id())
                                        .suggests(RoadCommand::tiers).executes(ctx -> {
                                            ResourceLocation tier = ResourceLocationArgument.getId(ctx, "tier");
                                            if (RoadData.tier(tier).isEmpty()) {
                                                return fail(ctx, "No road tier " + tier);
                                            }
                                            RoadWorks works = RoadWorks.get(ctx.getSource().getServer());
                                            return works.stretch(IntegerArgumentType.getInteger(ctx, "id")).map(s -> {
                                                works.setTier(ctx.getSource().getServer(), s, tier);
                                                say(ctx, "Stretch #" + s.id + " is now " + tier);
                                                return 1;
                                            }).orElseGet(() -> fail(ctx, "No such stretch"));
                                        }))))
                .then(Commands.literal("wear")
                        .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                .then(Commands.argument("amount", LongArgumentType.longArg()).executes(ctx -> {
                                    RoadWorks works = RoadWorks.get(ctx.getSource().getServer());
                                    return works.stretch(IntegerArgumentType.getInteger(ctx, "id")).map(s -> {
                                        ResourceLocation t = works.addWear(ctx.getSource().getServer(), s,
                                                LongArgumentType.getLong(ctx, "amount"));
                                        say(ctx, "Stretch #" + s.id + ": wear " + s.wear() + ", " + t);
                                        return 1;
                                    }).orElseGet(() -> fail(ctx, "No such stretch"));
                                }))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("id", IntegerArgumentType.integer(1)).executes(ctx -> {
                            boolean gone = RoadWorks.get(ctx.getSource().getServer())
                                    .remove(IntegerArgumentType.getInteger(ctx, "id"));
                            return gone ? 1 : fail(ctx, "No such stretch");
                        })));
    }

    private static int add(CommandContext<CommandSourceStack> ctx) {
        ResourceLocation tier = ResourceLocationArgument.getId(ctx, "tier");
        if (RoadData.tier(tier).isEmpty()) {
            return fail(ctx, "No road tier " + tier);
        }
        List<RoadWorks.Point> points = List.of(point(ctx, "x1", "z1"), point(ctx, "x2", "z2"));
        var server = ctx.getSource().getServer();
        RoadWorks.Stretch s = RoadWorks.get(server).add(server, ctx.getSource().getLevel().dimension(), points, tier,
                RoadWorks.COMMAND);
        RoadPaving.flush(server);
        say(ctx, "Added stretch #" + s.id + " (" + tier + ")");
        return s.id;
    }

    private static RoadWorks.Point point(CommandContext<CommandSourceStack> ctx, String x, String z) {
        return new RoadWorks.Point(IntegerArgumentType.getInteger(ctx, x), IntegerArgumentType.getInteger(ctx, z));
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> tiers(
            CommandContext<CommandSourceStack> ctx, com.mojang.brigadier.suggestion.SuggestionsBuilder b) {
        return SharedSuggestionProvider.suggestResource(RoadData.ladder(), b);
    }

    private static void say(CommandContext<CommandSourceStack> ctx, String text) {
        ctx.getSource().sendSuccess(() -> Component.literal(text), false);
    }

    private static int fail(CommandContext<CommandSourceStack> ctx, String text) {
        ctx.getSource().sendFailure(Component.literal(text));
        return 0;
    }
}
