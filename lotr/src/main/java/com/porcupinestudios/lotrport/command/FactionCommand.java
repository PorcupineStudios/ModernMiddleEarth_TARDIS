package com.porcupinestudios.lotrport.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.porcupinestudios.lotrport.faction.Diplomacy;
import com.porcupinestudios.lotrport.faction.Faction;
import com.porcupinestudios.lotrport.faction.Factions;
import com.porcupinestudios.lotrport.faction.Pledges;
import com.porcupinestudios.lotrport.faction.Realms;
import com.porcupinestudios.lotrport.faction.Relation;
import java.util.Arrays;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /lotr faction ...}, {@code /lotr pledge}, {@code /lotr realm ...}: list factions, read and change relations
 * and vassals, pledge players, found realms. Admin commands (permission 2) until there are screens.
 */
public final class FactionCommand {
    private static final SimpleCommandExceptionType UNKNOWN =
            new SimpleCommandExceptionType(Component.literal("Unknown faction"));
    private static final SuggestionProvider<CommandSourceStack> FACTIONS = (ctx, builder) ->
            SharedSuggestionProvider.suggestResource(
                    Factions.all(ctx.getSource().getServer()).stream().map(Faction::id), builder);
    private static final SuggestionProvider<CommandSourceStack> RELATIONS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(
                    Arrays.stream(Relation.values()).map(Relation::getSerializedName), builder);

    private FactionCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("lotr")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("faction")
                        .then(Commands.literal("list").executes(FactionCommand::list))
                        .then(Commands.literal("relation")
                                .then(faction("a").then(faction("b")
                                        .executes(FactionCommand::getRelation)
                                        .then(Commands.argument("relation", StringArgumentType.word())
                                                .suggests(RELATIONS)
                                                .executes(FactionCommand::setRelation)))))
                        .then(Commands.literal("vassal")
                                .then(faction("vassal")
                                        .then(Commands.literal("none").executes(ctx -> setOverlord(ctx, false)))
                                        .then(faction("overlord").executes(ctx -> setOverlord(ctx, true))))))
                .then(Commands.literal("pledge")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.literal("none").executes(ctx -> {
                                    Pledges.unpledge(EntityArgument.getPlayer(ctx, "player"));
                                    ctx.getSource().sendSuccess(() -> Component.literal("Unpledged"), true);
                                    return 1;
                                }))
                                .then(faction("faction").executes(FactionCommand::pledge))))
                .then(Commands.literal("realm")
                        .then(Commands.literal("found")
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .executes(ctx -> foundRealm(ctx, false))
                                        .then(faction("culture").executes(ctx -> foundRealm(ctx, true)))))));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, ResourceLocation> faction(String name) {
        return Commands.argument(name, ResourceLocationArgument.id()).suggests(FACTIONS);
    }

    private static Faction get(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
        ResourceLocation id = ResourceLocationArgument.getId(ctx, name);
        return Factions.get(ctx.getSource().getServer(), id).orElseThrow(UNKNOWN::create);
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        int n = 0;
        for (Faction f : Factions.all(server)) {
            Component line = Component.literal(f.id() + " ").append(f.name())
                    .append(f.realm() ? Component.literal(" (realm)") : Component.empty());
            ctx.getSource().sendSuccess(() -> line, false);
            n++;
        }
        for (String problem : Factions.loadProblems()) {
            ctx.getSource().sendFailure(Component.literal(problem));
        }
        return n;
    }

    private static int getRelation(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Faction a = get(ctx, "a");
        Faction b = get(ctx, "b");
        Relation r = Factions.relation(ctx.getSource().getServer(), a.id(), b.id());
        ctx.getSource().sendSuccess(() -> Component.empty().append(a.name()).append(" and ").append(b.name())
                .append(": " + r.getSerializedName()), false);
        return r.ordinal();
    }

    private static int setRelation(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Faction a = get(ctx, "a");
        Faction b = get(ctx, "b");
        String name = StringArgumentType.getString(ctx, "relation");
        Relation r = name.equals("default") ? null : Relation.byName(name);
        if (r == null && !name.equals("default")) {
            throw new SimpleCommandExceptionType(Component.literal("Unknown relation " + name)).create();
        }
        if (a.id().equals(b.id())) {
            throw new SimpleCommandExceptionType(Component.literal("A faction can't change relations with itself"))
                    .create();
        }
        MinecraftServer server = ctx.getSource().getServer();
        Diplomacy.get(server).setRelation(server, a.id(), b.id(), r);
        Relation now = Factions.relation(server, a.id(), b.id());
        ctx.getSource().sendSuccess(() -> Component.empty().append(a.name()).append(" and ").append(b.name())
                .append(" are now " + now.getSerializedName()), true);
        return 1;
    }

    private static int setOverlord(CommandContext<CommandSourceStack> ctx, boolean set) throws CommandSyntaxException {
        Faction vassal = get(ctx, "vassal");
        Faction overlord = set ? get(ctx, "overlord") : null;
        MinecraftServer server = ctx.getSource().getServer();
        Diplomacy.get(server).setOverlord(server, vassal.id(), overlord == null ? null : overlord.id());
        ctx.getSource().sendSuccess(() -> overlord == null
                ? Component.empty().append(vassal.name()).append(" is free")
                : Component.empty().append(vassal.name()).append(" is now a vassal of ").append(overlord.name()),
                true);
        return 1;
    }

    private static int pledge(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        Faction faction = get(ctx, "faction");
        Pledges.Result result = Pledges.pledge(player, faction.id());
        if (result != Pledges.Result.PLEDGED) {
            ctx.getSource().sendFailure(Component.literal("Can't pledge: " + result.name().toLowerCase()));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(player.getScoreboardName() + " pledged to ")
                .append(faction.name()), true);
        return 1;
    }

    private static int foundRealm(CommandContext<CommandSourceStack> ctx, boolean withCulture)
            throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "name");
        Optional<ResourceLocation> culture = withCulture ? Optional.of(get(ctx, "culture").id()) : Optional.empty();
        ServerPlayer player = ctx.getSource().getPlayer();
        int color = culture.flatMap(c -> Factions.get(ctx.getSource().getServer(), c)).map(Faction::color)
                .orElse(0xAAAAAA);
        Realms.Realm realm = Realms.get(ctx.getSource().getServer()).found(ctx.getSource().getServer(), name,
                player == null ? Optional.empty() : Optional.of(player.getUUID()), culture, color);
        ctx.getSource().sendSuccess(() -> Component.literal("Founded " + name + " as " + realm.id()), true);
        return 1;
    }
}
