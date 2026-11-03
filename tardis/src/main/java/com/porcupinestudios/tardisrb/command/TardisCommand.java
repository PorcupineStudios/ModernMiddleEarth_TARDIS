package com.porcupinestudios.tardisrb.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.porcupinestudios.tardisrb.block.Exteriors;
import com.porcupinestudios.tardisrb.core.TardisManager;
import com.porcupinestudios.tardisrb.core.TardisRecord;
import com.porcupinestudios.tardisrb.flight.Flight;
import com.porcupinestudios.tardisrb.flight.Landing;
import com.porcupinestudios.tardisrb.interior.Interiors;
import com.porcupinestudios.tardisrb.progression.Progression;
import com.porcupinestudios.tardisrb.progression.Subsystem;
import com.porcupinestudios.tardisrb.progression.TardisContent;
import com.porcupinestudios.tardisrb.progression.Unlocks;
import com.porcupinestudios.tardisrb.repair.Repairs;
import com.porcupinestudios.tardisrb.research.Researching;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Admin and testing commands (permission 2), acting on the TARDIS the player owns or crews: {@code /tardis create
 * [name]} (in front of you), {@code info}, {@code enter}, {@code hop <x y z>}, {@code repair <id>}, {@code damage <subsystem> <levels>}, {@code charge <amount>},
 * {@code study <node>} (paid, takes time), {@code research <node>} (done at once), {@code milestone <id>}, {@code check <feature>}, {@code data}.
 */
public final class TardisCommand {
    private static final SimpleCommandExceptionType NO_TARDIS = new SimpleCommandExceptionType(
            Component.translatableWithFallback("tardis_rb.command.none", "You have no TARDIS"));

    private TardisCommand() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> register(e.getDispatcher()));
    }

    private static SuggestionProvider<CommandSourceStack> ids(
            Function<TardisContent.Snapshot, Collection<ResourceLocation>> of) {
        return (ctx, builder) -> SharedSuggestionProvider.suggestResource(of.apply(TardisContent.get()), builder);
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("tardis").requires(s -> s.hasPermission(2))
                .then(Commands.literal("create")
                        .executes(ctx -> create(ctx, "TARDIS"))
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> create(ctx, StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("info").executes(TardisCommand::info))
                .then(Commands.literal("enter").executes(ctx -> Interiors.enter(ctx.getSource().getPlayerOrException(),
                        mine(ctx)) ? 1 : 0))
                .then(Commands.literal("hop").then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(ctx -> {
                            var out = Flight.hop(ctx.getSource().getServer(), mine(ctx),
                                    BlockPosArgument.getBlockPos(ctx, "pos"),
                                    Optional.ofNullable(ctx.getSource().getPlayer()));
                            if (!out.done()) {
                                ctx.getSource().sendFailure(out.problem().get());
                                return 0;
                            }
                            ctx.getSource().sendSuccess(() -> Component.literal("Landed at "
                                    + out.landed().get().pos().toShortString()), true);
                            return 1;
                        })))
                .then(Commands.literal("data").executes(ctx -> {
                    var data = TardisContent.get();
                    ctx.getSource().sendSuccess(() -> Component.literal(data.subsystems().size() + " subsystems, "
                            + data.stages().size() + " stages, " + data.repairs().size() + " repairs, "
                            + data.research().size() + " research, " + data.features().size() + " features, "
                            + data.problems().size() + " problems"), false);
                    data.problems().forEach(p -> ctx.getSource().sendFailure(Component.literal(p)));
                    return data.problems().size();
                }))
                .then(Commands.literal("repair").then(Commands.argument("repair", ResourceLocationArgument.id())
                        .suggests(ids(d -> d.repairs().keySet())).executes(ctx -> {
                            TardisRecord t = mine(ctx);
                            var out = Repairs.attempt(ctx.getSource().getServer(), t,
                                    ResourceLocationArgument.getId(ctx, "repair"),
                                    Optional.ofNullable(ctx.getSource().getPlayer()));
                            if (!out.done()) {
                                ctx.getSource().sendFailure(out.problem().get());
                                return 0;
                            }
                            ctx.getSource().sendSuccess(() -> Component.literal("Repaired."), true);
                            return 1;
                        })))
                .then(Commands.literal("damage").then(Commands.argument("subsystem", ResourceLocationArgument.id())
                        .suggests(ids(d -> d.subsystems().keySet()))
                        .then(Commands.argument("levels", IntegerArgumentType.integer(1)).executes(ctx -> {
                            TardisRecord t = mine(ctx);
                            int lost = t.damage(ResourceLocationArgument.getId(ctx, "subsystem"),
                                    IntegerArgumentType.getInteger(ctx, "levels"));
                            Progression.update(ctx.getSource().getServer(), t);
                            ctx.getSource().sendSuccess(() -> Component.literal("Lost " + lost + " levels."), true);
                            return lost;
                        }))))
                .then(Commands.literal("charge").then(Commands.argument("amount", LongArgumentType.longArg(1))
                        .executes(ctx -> {
                            long added = mine(ctx).addCharge(LongArgumentType.getLong(ctx, "amount"));
                            ctx.getSource().sendSuccess(() -> Component.literal("Stored " + added + " charge."), true);
                            return (int) Math.min(Integer.MAX_VALUE, added);
                        })))
                .then(Commands.literal("study").then(Commands.argument("node", ResourceLocationArgument.id())
                        .suggests(ids(d -> d.research().keySet())).executes(ctx -> {
                            var out = Researching.start(ctx.getSource().getServer(), mine(ctx),
                                    ResourceLocationArgument.getId(ctx, "node"),
                                    Optional.ofNullable(ctx.getSource().getPlayer()));
                            if (!out.done()) {
                                ctx.getSource().sendFailure(out.problem().get());
                                return 0;
                            }
                            ctx.getSource().sendSuccess(() -> Component.literal("Research started."), true);
                            return 1;
                        })))
                .then(Commands.literal("research").then(Commands.argument("node", ResourceLocationArgument.id())
                        .suggests(ids(d -> d.research().keySet())).executes(ctx -> {
                            Researching.finish(ctx.getSource().getServer(), mine(ctx),
                                    ResourceLocationArgument.getId(ctx, "node"));
                            return 1;
                        })))
                .then(Commands.literal("milestone").then(Commands.argument("milestone", ResourceLocationArgument.id())
                        .suggests(ids(d -> d.milestones().keySet())).executes(ctx -> {
                            TardisRecord t = mine(ctx);
                            t.passMilestone(ResourceLocationArgument.getId(ctx, "milestone"));
                            Progression.update(ctx.getSource().getServer(), t);
                            return 1;
                        })))
                .then(Commands.literal("check").then(Commands.argument("feature", ResourceLocationArgument.id())
                        .suggests(ids(d -> d.features().keySet())).executes(ctx -> {
                            var r = Unlocks.check(ctx.getSource().getServer(), mine(ctx),
                                    ResourceLocationArgument.getId(ctx, "feature"),
                                    Optional.ofNullable(ctx.getSource().getPlayer()));
                            ctx.getSource().sendSuccess(() -> r.allowed() ? Component.literal("Allowed")
                                    : Component.literal("Locked: ").append(r.reason()), false);
                            return r.allowed() ? 1 : 0;
                        }))));
    }

    private static TardisRecord mine(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        return TardisManager.get(ctx.getSource().getServer()).of(player.getUUID()).orElseThrow(NO_TARDIS::create);
    }

    private static int create(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        TardisManager manager = TardisManager.get(ctx.getSource().getServer());
        if (!manager.mayOwnAnother(player.getUUID())) {
            ctx.getSource().sendFailure(Component.translatableWithFallback("tardis_rb.command.one_each",
                    "You already own a TARDIS (allowMultipleTardisPerPlayer is off)"));
            return 0;
        }
        TardisRecord t = manager.create(ctx.getSource().getServer(), name, Optional.of(player.getUUID()));
        ServerLevel level = player.serverLevel();
        Direction facing = player.getDirection();
        BlockPos near = player.blockPosition().relative(facing, 2);
        Landing.find(level, near, 4, 4).ifPresent(spot -> {
            Exteriors.place(level, spot, facing.getOpposite(), t.id());
            t.setLocation(Optional.of(GlobalPos.of(level.dimension(), spot)));
        });
        ctx.getSource().sendSuccess(() -> Component.literal("Made " + t.name() + " (" + t.id() + ")"), true);
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        TardisRecord t = mine(ctx);
        CommandSourceStack src = ctx.getSource();
        src.sendSuccess(() -> Component.literal(t.name() + ": stage " + t.stage().map(Object::toString).orElse("-")
                + ", charge " + t.charge() + "/" + Unlocks.chargeCap(t) + ", range "
                + (long) Unlocks.stat(t, "range")), false);
        Map<ResourceLocation, Subsystem> subs = new TreeMap<>(TardisContent.get().subsystems());
        subs.forEach((id, s) -> src.sendSuccess(() -> Component.literal("  " + id + ": " + Unlocks.level(t, id) + "/"
                + s.maxLevel() + " (repaired " + t.repairedLevel(id) + ", damage " + t.damage(id) + ", cap "
                + Unlocks.cap(t, id) + ")"), false));
        if (!t.milestones().isEmpty()) {
            src.sendSuccess(() -> Component.literal("  milestones: " + t.milestones()), false);
        }
        return 1;
    }
}
