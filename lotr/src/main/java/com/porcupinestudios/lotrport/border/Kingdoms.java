package com.porcupinestudios.lotrport.border;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.porcupinestudios.lotrport.colony.Colonies;
import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.faction.Alignment;
import com.porcupinestudios.lotrport.faction.Faction;
import com.porcupinestudios.lotrport.faction.Factions;
import com.porcupinestudios.lotrport.faction.Pledges;
import com.porcupinestudios.lotrport.faction.Realms;
import com.porcupinestudios.lotrport.player.Races;
import java.util.List;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Player kingdoms: a player either founds a kingdom of their own (a realm: its own faction, alignment and colour, shown
 * on the map) or pledges to an existing faction, a kingdom included. A kingdom's colonies are those founded for it:
 * its ruler's, its subjects' (players pledged to it), and any it takes in war. Its borders are what its colonies hold
 * ({@link Borders}).
 */
public final class Kingdoms {
    public enum Result { FOUNDED, ALREADY_RULER, NEEDS_COLONY, BAD_NAME }

    private Kingdoms() {}

    /** The kingdom this player rules, if any. */
    public static Optional<Realms.Realm> ruled(MinecraftServer server, ServerPlayer player) {
        return Realms.get(server).ownedBy(player.getUUID());
    }

    /** The player's own colony (not an AI's), if any. */
    public static Optional<Colony> colonyOf(MinecraftServer server, ServerPlayer player) {
        return Colonies.get(server).all().stream()
                .filter(c -> !c.ai() && c.founder().equals(player.getUUID())).findFirst();
    }

    /**
     * Founds a kingdom ruled by the player. A pledge is renounced, and that people becomes the kingdom's culture (its
     * relations and people until it makes its own); without one, the culture is the player's race's first people.
     * The ruler's colony joins the kingdom and becomes its capital; the ruler starts with {@code ruler_alignment}.
     */
    public static Result found(ServerPlayer player, String name, Optional<Integer> color) {
        MinecraftServer server = player.server;
        BorderData.Settings s = BorderData.settings();
        String trimmed = name.trim();
        if (trimmed.isEmpty() || trimmed.length() > 40) {
            return Result.BAD_NAME;
        }
        if (ruled(server, player).isPresent()) {
            return Result.ALREADY_RULER;
        }
        Optional<Colony> colony = colonyOf(server, player);
        if (s.kingdomNeedsColony() && colony.isEmpty()) {
            return Result.NEEDS_COLONY;
        }
        Optional<ResourceLocation> culture = Pledges.pledge(player).filter(p -> Realms.get(server).get(p).isEmpty());
        if (culture.isEmpty()) {
            culture = Races.of(player).map(Races.all()::get).flatMap(d -> d.factions().stream().findFirst());
        }
        int colour = color.orElseGet(() -> 0);
        if (color.isEmpty()) {
            Optional<ResourceLocation> c = culture;
            colour = c.flatMap(id -> Factions.get(server, id)).map(Faction::color).orElse(0xAAAAAA);
        }
        Pledges.unpledge(player);
        Realms.Realm realm = Realms.get(server).found(server, trimmed, Optional.of(player.getUUID()), culture,
                colour & 0xFFFFFF);
        Alignment.set(player, realm.id(), Math.max(Alignment.get(player, realm.id()), s.rulerAlignment()));
        colony.ifPresent(c -> Colonies.get(server).transfer(server, c, player.getUUID(), realm.id(), false,
                "kingdom_founded"));
        Borders.get(server).touch();
        return Result.FOUNDED;
    }

    /** The faction a player's new colony is founded for: their kingdom, else their pledge. */
    public static Optional<ResourceLocation> colonyFaction(ServerPlayer player) {
        Optional<Realms.Realm> realm = ruled(player.server, player);
        return realm.isPresent() ? Optional.of(realm.get().id()) : Pledges.pledge(player);
    }

    // ---- commands

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("lotr_kingdom")
                .then(Commands.literal("found")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .executes(ctx -> found(ctx, Optional.empty()))
                                .then(Commands.argument("color", StringArgumentType.word())
                                        .executes(ctx -> found(ctx, Optional.of(
                                                StringArgumentType.getString(ctx, "color")))))))
                .then(Commands.literal("info").executes(Kingdoms::info))
                .then(Commands.literal("land").executes(Kingdoms::land))
                .then(Commands.literal("assault")
                        .then(Commands.argument("soldiers", com.mojang.brigadier.arguments.IntegerArgumentType
                                .integer(1, 500)).executes(Kingdoms::assault))));
        dispatcher.register(Commands.literal("lotr")
                .then(Commands.literal("border")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("recompute").executes(ctx -> {
                            Borders.get(ctx.getSource().getServer()).recompute(ctx.getSource().getServer());
                            ctx.getSource().sendSuccess(() -> Component.literal("Borders worked out again"), true);
                            return 1;
                        }))
                        .then(Commands.literal("gains").executes(Kingdoms::gains))
                        .then(Commands.literal("mark")
                                .then(Commands.argument("radius", DoubleArgumentType.doubleArg(0, 64))
                                        .then(Commands.literal("nobody").executes(ctx -> mark(ctx, null, false)))
                                        .then(Commands.literal("clear").executes(ctx -> mark(ctx, null, true)))
                                        .then(Commands.argument("faction", ResourceLocationArgument.id())
                                                .executes(ctx -> mark(ctx,
                                                        ResourceLocationArgument.getId(ctx, "faction"), false)))))));
    }

    private static int found(CommandContext<CommandSourceStack> ctx, Optional<String> colorArg)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Optional<Integer> color = Optional.empty();
        if (colorArg.isPresent()) {
            try {
                color = Optional.of(Integer.parseInt(colorArg.get().replace("#", ""), 16));
            } catch (NumberFormatException e) {
                ctx.getSource().sendFailure(Component.literal("Colour is a hex RGB like #3366CC"));
                return 0;
            }
        }
        String name = StringArgumentType.getString(ctx, "name");
        Result r = found(player, name, color);
        Component msg = switch (r) {
            case FOUNDED -> Component.translatableWithFallback("kingdom.lotr_port.founded",
                    "You rule the kingdom of %s. Its colonies' land shows on the map.", name);
            case ALREADY_RULER -> Component.translatableWithFallback("kingdom.lotr_port.already_ruler",
                    "You already rule a kingdom");
            case NEEDS_COLONY -> Component.translatableWithFallback("kingdom.lotr_port.needs_colony",
                    "Found a colony first: a kingdom starts from its capital");
            case BAD_NAME -> Component.translatableWithFallback("kingdom.lotr_port.bad_name",
                    "A kingdom's name is 1 to 40 characters");
        };
        if (r == Result.FOUNDED) {
            ctx.getSource().sendSuccess(() -> msg, false);
            return 1;
        }
        ctx.getSource().sendFailure(msg);
        return 0;
    }

    private static int info(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        MinecraftServer server = player.server;
        Optional<Realms.Realm> realm = ruled(server, player);
        Optional<ResourceLocation> faction = realm.map(Realms.Realm::id).or(() -> Pledges.pledge(player));
        if (faction.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.translatableWithFallback("kingdom.lotr_port.none",
                    "You rule no kingdom and are pledged to nobody. Found one (/lotr_kingdom found <name>) or pledge."),
                    false);
            return 0;
        }
        ResourceLocation id = faction.get();
        List<Colony> colonies = Colonies.get(server).all().stream().filter(c -> c.faction().equals(id)).toList();
        int gained = Borders.get(server).gains().getOrDefault(id, 0);
        Component name = Factions.get(server, id).map(Faction::name).orElse(Component.literal(id.toString()));
        ctx.getSource().sendSuccess(() -> Component.empty()
                .append(realm.isPresent() ? Component.literal("Your kingdom: ") : Component.literal("Pledged to: "))
                .append(name).append(Component.literal(", " + colonies.size() + " colonies, " + gained
                        + " map squares beyond its old land")), false);
        return colonies.size();
    }

    private static int land(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        MinecraftServer server = player.server;
        Optional<ResourceLocation> now = Borders.ownerAt(player.serverLevel(), player.blockPosition());
        Optional<ResourceLocation> was = Borders.baseOwnerAt(player.serverLevel(), player.blockPosition());
        Component nowName = now.flatMap(id -> Factions.get(server, id)).map(Faction::name)
                .orElse(Component.literal("nobody"));
        Component msg = Component.literal("This land is held by ").append(nowName);
        if (!now.equals(was)) {
            msg = msg.copy().append(Component.literal(", once ")).append(was.flatMap(id -> Factions.get(server, id))
                    .map(Faction::name).orElse(Component.literal("nobody's")));
        }
        int pixel = Borders.pixelOf(player.getX(), player.getZ());
        Conquest conquest = Conquest.get(server);
        msg = msg.copy().append(Component.literal(String.format(". Defence %.1f (terrain x%.2f)",
                conquest.defence(server, pixel), Conquest.terrain(pixel))));
        Optional<ResourceLocation> mine = colonyFaction(player);
        if (mine.isPresent() && now.isPresent() && !now.equals(mine)) {
            msg = msg.copy().append(Component.literal(Conquest.hostile(server, mine.get(), now.get())
                    ? String.format(", your pressure %.1f (x%.2f from your colonies here)",
                            conquest.pressure(pixel, mine.get()), Conquest.multiplier(server, mine.get(), pixel))
                    : ", which your side can't take (not at war or on bad terms)"));
        }
        Component m = msg;
        ctx.getSource().sendSuccess(() -> m, false);
        return 1;
    }

    /** Sends soldiers of the player's colony (where they hold the war permission) at the land they stand in. */
    private static int assault(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        MinecraftServer server = player.server;
        int count = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "soldiers");
        Optional<ResourceLocation> side = colonyFaction(player);
        int pixel = Borders.pixelOf(player.getX(), player.getZ());
        Optional<Colony> from = Colonies.get(server).all().stream()
                .filter(c -> side.isPresent() && c.faction().equals(side.get()))
                .filter(c -> com.porcupinestudios.lotrport.colony.members.Permissions.can(player.getUUID(), c,
                        com.porcupinestudios.lotrport.colony.members.Perm.WAR))
                .filter(c -> !Conquest.fit(server, c).isEmpty())
                .min((a, b) -> Double.compare(Conquest.distance(server, a, pixel), Conquest.distance(server, b, pixel)));
        if (from.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatableWithFallback("kingdom.lotr_port.no_army",
                    "You command no colony with soldiers fit to fight"));
            return 0;
        }
        Optional<Conquest.AssaultResult> r = Conquest.get(server).assault(server, from.get(), pixel, count,
                player.getRandom());
        if (r.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatableWithFallback("kingdom.lotr_port.cannot_assault",
                    "This land is nobody's (settle it) or not your enemy's (be at war or on bad terms)"));
            return 0;
        }
        Conquest.AssaultResult a = r.get();
        String text = String.format("%s sent %d soldiers: %d fell, %d wounded. Defence %.1f -> %.1f%s",
                from.get().name(), a.sent(), a.died(), a.injured(), a.defenceBefore(), a.defenceAfter(),
                a.taken() ? ". The land is yours" : "");
        ctx.getSource().sendSuccess(() -> Component.literal(text), false);
        return a.sent();
    }

    private static int gains(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        var gains = Borders.get(server).gains();
        if (gains.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("Every border is as on the original map"), false);
        }
        gains.forEach((f, n) -> ctx.getSource().sendSuccess(() -> Component.literal(f + ": " + n + " squares"),
                false));
        return gains.size();
    }

    private static int mark(CommandContext<CommandSourceStack> ctx, ResourceLocation faction, boolean clear) {
        CommandSourceStack src = ctx.getSource();
        double radius = DoubleArgumentType.getDouble(ctx, "radius");
        int n = Borders.get(src.getServer()).mark(src.getServer(), net.minecraft.core.BlockPos.containing(
                src.getPosition()), radius, Optional.ofNullable(faction), clear, "command");
        src.sendSuccess(() -> Component.literal(n + " map squares changed"), true);
        return n;
    }
}
