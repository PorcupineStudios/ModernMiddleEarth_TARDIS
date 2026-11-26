package com.porcupinestudios.lotrport.world.delve;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.world.LotrDimensions;
import com.porcupinestudios.lotrport.world.map.MiddleEarthMap;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /lotr_delve} (operators): {@code info <delve>} prints the plan's summary; {@code spawn <delve>} forces one spawn attempt nearby; {@code goto <delve> <place>}
 * teleports to a set piece (a gate's outside, a hall's west end, the Endless Stair's lowest landing...);
 * {@code preview <delve>} writes {@link DelvePreview}'s pictures to {@code delve_preview/<delve>/} in the server
 * folder. A delve is a configured feature of type {@code lotr_port:delve}, e.g. {@code lotr_port:moria}.
 */
public final class DelveCommand {
    private static final float[] YAW = {180f, -90f, 0f, 90f};

    private DelveCommand() {}

    private static final SuggestionProvider<CommandSourceStack> DELVES = (ctx, b) -> SharedSuggestionProvider
            .suggestResource(delves(ctx.getSource().getServer()), b);

    private static final SuggestionProvider<CommandSourceStack> PLACES = (ctx, b) -> {
        List<String> places = new ArrayList<>();
        DelveFeature.config(ctx.getSource().getServer(), ResourceLocationArgument.getId(ctx, "delve"))
                .ifPresent(c -> DelveFeature.spec(c).setPieces.forEach(sp -> places.add(sp.id())));
        return SharedSuggestionProvider.suggest(places, b);
    };

    static List<ResourceLocation> delves(MinecraftServer server) {
        List<ResourceLocation> out = new ArrayList<>();
        server.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).entrySet().forEach(e -> {
            if (e.getValue().config() instanceof DelveFeature.Config) {
                out.add(e.getKey().location());
            }
        });
        return out;
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("lotr_delve").requires(s -> s.hasPermission(2))
                .then(Commands.literal("info").then(Commands.argument("delve", ResourceLocationArgument.id())
                        .suggests(DELVES).executes(DelveCommand::info)))
                .then(Commands.literal("goto").then(Commands.argument("delve", ResourceLocationArgument.id())
                        .suggests(DELVES).then(Commands.argument("place", StringArgumentType.word())
                                .suggests(PLACES).executes(DelveCommand::go))))
                .then(Commands.literal("spawn").then(Commands.argument("delve", ResourceLocationArgument.id())
                        .suggests(DELVES).executes(DelveCommand::spawn)))
                .then(Commands.literal("preview").requires(s -> s.hasPermission(3))
                        .then(Commands.argument("delve", ResourceLocationArgument.id()).suggests(DELVES)
                                .executes(DelveCommand::preview))));
    }

    private static DelvePlan plan(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "delve");
        DelveFeature.Config config = DelveFeature.config(server, id)
                .orElseThrow(() -> new IllegalArgumentException("no delve " + id));
        return DelveFeature.plan(server, config, LotrDimensions.middleEarth(server).getSeed());
    }

    private static int info(CommandContext<CommandSourceStack> ctx) {
        DelvePlan plan = plan(ctx);
        for (String line : plan.summary.split("\n")) {
            ctx.getSource().sendSuccess(() -> Component.literal(line), false);
        }
        return plan.nodeCount();
    }

    private static int go(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        DelvePlan plan = plan(ctx);
        String place = StringArgumentType.getString(ctx, "place");
        ServerLevel level = LotrDimensions.middleEarth(ctx.getSource().getServer());
        int[] door = plan.doorway(place);
        if (door != null) {
            // outside the gate, looking at its doors
            int s = door[3];
            player.teleportTo(level, door[0] + DelveSpec.DX[s] * 10 + 0.5, door[1], door[2] + DelveSpec.DZ[s] * 10 + 0.5,
                    YAW[(s + 2) % 4], 0f);
            return 1;
        }
        int[] box = plan.setPiece(place);
        if (box == null) {
            ctx.getSource().sendFailure(Component.literal("No placed set piece " + place));
            return 0;
        }
        player.teleportTo(level, box[0] + 1.5, box[1], (box[2] + box[5]) / 2 + 0.5, YAW[DelveSpec.E], 0f);
        return 1;
    }

    /** One forced spawn attempt of the delve's table near the player (the table's chance skipped). */
    private static int spawn(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "delve");
        DelveSpawns.Table table = DelveSpawns.tables().get(id);
        if (table == null) {
            ctx.getSource().sendFailure(Component.literal("No delve_spawns/" + id.getPath() + ".json"));
            return 0;
        }
        int n = DelveSpawns.trySpawn(player.serverLevel(), player, table, true);
        ctx.getSource().sendSuccess(() -> Component.literal(n + " spawned"), false);
        return n;
    }

    private static int preview(CommandContext<CommandSourceStack> ctx) {
        DelvePlan plan = plan(ctx);
        MinecraftServer server = ctx.getSource().getServer();
        Path out = server.getServerDirectory().resolve("delve_preview").resolve(plan.spec.name);
        CommandSourceStack source = ctx.getSource();
        source.sendSuccess(() -> Component.literal("Rendering " + plan.spec.name + " to " + out + " ..."), true);
        Thread t = new Thread(() -> {
            try {
                long t0 = System.nanoTime();
                DelvePreview.render(plan, MiddleEarthMap.get()::heightAt, out, 2);
                long s = (System.nanoTime() - t0) / 1_000_000_000L;
                server.execute(() -> source.sendSuccess(() -> Component.literal("Delve preview written in " + s
                        + " s: " + out), true));
            } catch (Exception e) {
                LotrPort.LOG.error("[delve] preview failed", e);
                server.execute(() -> source.sendFailure(Component.literal("Delve preview failed: " + e)));
            }
        }, "delve-preview");
        t.setDaemon(true);
        t.start();
        return 1;
    }
}
