package com.porcupinestudios.lotrport.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.porcupinestudios.lotrport.npc.Hiring;
import com.porcupinestudios.lotrport.npc.LotrNpc;
import com.porcupinestudios.lotrport.npc.NpcData;
import com.porcupinestudios.lotrport.society.Persons;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /lotr npc spawn|types|people} (admin) and {@code /lotr_hire <captain> <unit>} (any player; the [Hire] links
 * in a captain's chat list run it).
 */
public final class NpcCommand {
    private static final SuggestionProvider<CommandSourceStack> TYPES =
            (ctx, builder) -> SharedSuggestionProvider.suggestResource(NpcData.types().keySet(), builder);

    private NpcCommand() {}

    private static int offer(CommandSourceStack source, int radius) {
        Vec3 at = source.getPosition();
        int n = 0;
        for (LotrNpc npc : source.getLevel().getEntitiesOfClass(LotrNpc.class,
                new net.minecraft.world.phys.AABB(at, at).inflate(radius))) {
            if (npc.startQuestOffer()) {
                n++;
            }
        }
        int count = n;
        source.sendSuccess(() -> Component.literal(count + " NPCs now offer a quest"), true);
        return count;
    }

    private static int hireFree(CommandSourceStack source, ResourceLocation type, String squad)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        net.minecraft.server.level.ServerPlayer player = source.getPlayerOrException();
        Vec3 pos = source.getPosition();
        Optional<LotrNpc> npc = LotrNpc.spawn(source.getLevel(), type, pos.x, pos.y, pos.z);
        if (npc.isEmpty()) {
            source.sendFailure(Component.literal("Unknown NPC type " + type));
            return 0;
        }
        npc.get().hireTo(player);
        com.porcupinestudios.lotrport.society.Person p = com.porcupinestudios.lotrport.society.Society
                .hired(player.server, npc.get(), player.getUUID());
        if (!squad.isEmpty()) {
            com.porcupinestudios.lotrport.unit.Units.setSquad(player.server, p, squad);
        }
        source.sendSuccess(() -> Component.literal("Hired ").append(npc.get().getDisplayName()), true);
        return 1;
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("lotr")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("npc")
                        .then(Commands.literal("spawn")
                                .then(Commands.argument("type", ResourceLocationArgument.id()).suggests(TYPES)
                                        .executes(ctx -> {
                                            ResourceLocation type = ResourceLocationArgument.getId(ctx, "type");
                                            Vec3 pos = ctx.getSource().getPosition();
                                            return LotrNpc.spawn(ctx.getSource().getLevel(), type, pos.x, pos.y,
                                                    pos.z).map(npc -> {
                                                        npc.setPersistenceRequired();
                                                        ctx.getSource().sendSuccess(() -> Component.literal(
                                                                "Spawned ").append(npc.getDisplayName()), true);
                                                        return 1;
                                                    }).orElseGet(() -> {
                                                        ctx.getSource().sendFailure(Component.literal(
                                                                "Unknown NPC type " + type));
                                                        return 0;
                                                    });
                                        })))
                        // /lotr npc hire <type> [squad]: a unit of that type hired to you, free (testing)
                        .then(Commands.literal("hire")
                                .then(Commands.argument("type", ResourceLocationArgument.id()).suggests(TYPES)
                                        .executes(ctx -> hireFree(ctx.getSource(),
                                                ResourceLocationArgument.getId(ctx, "type"), ""))
                                        .then(Commands.argument("squad", StringArgumentType.greedyString())
                                                .executes(ctx -> hireFree(ctx.getSource(),
                                                        ResourceLocationArgument.getId(ctx, "type"),
                                                        StringArgumentType.getString(ctx, "squad"))))))
                        // /lotr npc offer [radius]: quest givers nearby start offering a quest now (testing)
                        .then(Commands.literal("offer").executes(ctx -> offer(ctx.getSource(), 16))
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 128))
                                        .executes(ctx -> offer(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "radius")))))
                        .then(Commands.literal("types").executes(ctx -> {
                            int n = NpcData.types().size();
                            ctx.getSource().sendSuccess(() -> Component.literal(n + " NPC types, problems: "
                                    + NpcData.loadProblems()), false);
                            return n;
                        }))
                        .then(Commands.literal("people").executes(ctx -> {
                            Persons people = Persons.get(ctx.getSource().getServer());
                            people.all().forEach(p -> ctx.getSource().sendSuccess(() -> Component.literal(
                                    p.toString()), false));
                            return people.size();
                        }))));
        dispatcher.register(Commands.literal("lotr_map").executes(ctx -> {
            net.minecraft.server.level.ServerPlayer sp = ctx.getSource().getPlayerOrException();
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp,
                    com.porcupinestudios.lotrport.net.MapNet.data(sp));
            return 1;
        }));
        dispatcher.register(Commands.literal("lotr_visibility").executes(ctx -> {
            com.porcupinestudios.lotrport.social.SocialNet.sendView(ctx.getSource().getPlayerOrException(), true);
            return 1;
        }));
        dispatcher.register(Commands.literal("lotr_units").executes(ctx -> {
            com.porcupinestudios.lotrport.unit.Units.openScreen(ctx.getSource().getPlayerOrException(),
                    Optional.empty());
            return 1;
        }));
        dispatcher.register(Commands.literal("lotr_hire")
                .then(Commands.argument("captain", StringArgumentType.word())
                        .then(Commands.argument("unit", IntegerArgumentType.integer(0))
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    UUID id;
                                    try {
                                        id = UUID.fromString(StringArgumentType.getString(ctx, "captain"));
                                    } catch (IllegalArgumentException e) {
                                        return 0;
                                    }
                                    Entity e = player.serverLevel().getEntity(id);
                                    if (!(e instanceof LotrNpc captain)
                                            || captain.distanceTo(player) > LotrNpc.DEAL_DISTANCE
                                            || !captain.willDealWith(player)) {
                                        ctx.getSource().sendFailure(Component.translatableWithFallback(
                                                "npc.lotr_port.hire.away", "The captain is not here"));
                                        return 0;
                                    }
                                    Hiring.Result result =
                                            Hiring.hire(player, captain, IntegerArgumentType.getInteger(ctx, "unit"));
                                    if (result.unit().isEmpty()) {
                                        ctx.getSource().sendFailure(result.problem());
                                        return 0;
                                    }
                                    ctx.getSource().sendSuccess(() -> Component.translatableWithFallback(
                                            "npc.lotr_port.hire.done", "You hired %s",
                                            result.unit().get().getDisplayName()), false);
                                    return 1;
                                }))));
    }
}
