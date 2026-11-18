package com.porcupinestudios.lotrport.world;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.porcupinestudios.lotrport.travel.FastTravel;
import com.porcupinestudios.lotrport.travel.Waypoint;
import com.porcupinestudios.lotrport.world.map.Roads;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import java.util.Comparator;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Server chunk pregenerator for Middle-earth: {@code /lotr_pregen start <all|roads|waypoints|spawn|players> [radius]}
 * queues chunks near LOTR roads, fast-travel waypoints, world spawn and online players, then generates a few at a
 * time in the background (fewer while the server is lagging). The queue is saved, so it resumes after a restart.
 * {@code follow on} keeps generating ahead around players whenever the queue is empty.
 */
public final class Pregen extends SavedData {
    private static final String NAME = "lotr_port_pregen";
    private static final TicketType<ChunkPos> TICKET = TicketType.create("lotr_pregen",
            Comparator.comparingLong(ChunkPos::toLong));
    private static final int DEFAULT_SPAWN = 32;
    private static final int DEFAULT_WAYPOINTS = 12;
    private static final int DEFAULT_ROADS = 4;
    private static final int DEFAULT_PLAYERS = 24;

    private final LongLinkedOpenHashSet queue = new LongLinkedOpenHashSet();
    private boolean paused;
    private boolean follow;
    private int followRadius = DEFAULT_PLAYERS;
    private int maxInFlight = 8;
    private long done;
    // runtime only
    private final it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap pending = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
    private final LongLinkedOpenHashSet followed = new LongLinkedOpenHashSet();

    public static void register() {
        NeoForge.EVENT_BUS.addListener(Pregen::onTick);
    }

    private static Pregen get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(Pregen::new, Pregen::load, null), NAME);
    }

    private static Pregen load(CompoundTag tag, HolderLookup.Provider lookup) {
        Pregen p = new Pregen();
        for (long l : tag.getLongArray("queue")) {
            p.queue.add(l);
        }
        p.paused = tag.getBoolean("paused");
        p.follow = tag.getBoolean("follow");
        p.followRadius = tag.contains("follow_radius") ? tag.getInt("follow_radius") : DEFAULT_PLAYERS;
        p.maxInFlight = tag.contains("max_in_flight") ? tag.getInt("max_in_flight") : 8;
        p.done = tag.getLong("done");
        return p;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        tag.putInt("dataVersion", 1);
        tag.put("queue", new LongArrayTag(queue.toLongArray()));
        tag.putBoolean("paused", paused);
        tag.putBoolean("follow", follow);
        tag.putInt("follow_radius", followRadius);
        tag.putInt("max_in_flight", maxInFlight);
        tag.putLong("done", done);
        return tag;
    }

    private static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerLevel level = LotrDimensions.middleEarth(server);
        if (level == null) {
            return;
        }
        Pregen p = get(server);
        if (p.queue.isEmpty() && p.follow && server.getTickCount() % 100 == 0) {
            for (ServerPlayer player : level.players()) {
                ChunkPos c = player.chunkPosition();
                if (p.followed.add(c.toLong())) { // only when a player enters a new chunk
                    p.addCircle(c.x, c.z, p.followRadius);
                }
            }
            if (p.followed.size() > 4096) {
                p.followed.clear();
            }
        }
        if (p.paused || (p.queue.isEmpty() && p.pending.isEmpty())) {
            return;
        }
        // back off while the server is over its tick budget
        double mspt = server.getAverageTickTimeNanos() / 1_000_000.0;
        int limit = mspt > 45 ? 1 : mspt > 35 ? Math.max(1, p.maxInFlight / 4) : p.maxInFlight;
        // never wait on a chunk here (the server thread is what finishes it): ticket it, check on later ticks
        var it = p.pending.long2IntEntrySet().fastIterator();
        while (it.hasNext()) {
            var e = it.next();
            ChunkPos pos = new ChunkPos(e.getLongKey());
            // shortcut: a chunk not loaded after 60 s is dropped, not retried
            if (level.getChunkSource().getChunkNow(pos.x, pos.z) != null
                    || server.getTickCount() - e.getIntValue() > 1200) {
                level.getChunkSource().removeRegionTicket(TICKET, pos, 0, pos);
                it.remove();
                p.done++;
            }
        }
        while (p.pending.size() < limit && !p.queue.isEmpty()) {
            long key = p.queue.removeFirstLong();
            ChunkPos pos = new ChunkPos(key);
            p.pending.put(key, server.getTickCount());
            level.getChunkSource().addRegionTicket(TICKET, pos, 0, pos);
            p.setDirty();
        }
        if (p.queue.isEmpty() && p.pending.isEmpty()) {
            server.getPlayerList().broadcastSystemMessage(Component.literal(
                    "[Pregen] Finished: " + p.done + " chunks"), false);
        }
    }

    private int addCircle(int cx, int cz, int radius) {
        int added = 0;
        int r2 = radius * radius;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz <= r2 && queue.add(ChunkPos.asLong(cx + dx, cz + dz))) {
                    added++;
                }
            }
        }
        if (added > 0) {
            setDirty();
        }
        return added;
    }

    private int addRoads(int radius) {
        int added = 0;
        for (Roads.Segment s : Roads.get().segments()) {
            double len = Math.hypot(s.x2() - s.x1(), s.z2() - s.z1());
            int steps = Math.max(1, (int) Math.ceil(len / 16));
            for (int i = 0; i <= steps; i++) {
                double t = (double) i / steps;
                int bx = (int) Math.floor(s.x1() + (s.x2() - s.x1()) * t);
                int bz = (int) Math.floor(s.z1() + (s.z2() - s.z1()) * t);
                added += addCircle(bx >> 4, bz >> 4, radius);
            }
        }
        return added;
    }

    private int add(MinecraftServer server, ServerLevel level, String target, int radius) {
        boolean all = target.equals("all");
        int added = 0;
        if (all || target.equals("spawn")) {
            ChunkPos spawn = new ChunkPos(level.getSharedSpawnPos());
            added += addCircle(spawn.x, spawn.z, radius > 0 ? radius : DEFAULT_SPAWN);
        }
        if (all || target.equals("players")) {
            for (ServerPlayer player : level.players()) {
                ChunkPos c = player.chunkPosition();
                added += addCircle(c.x, c.z, radius > 0 ? radius : DEFAULT_PLAYERS);
            }
        }
        if (all || target.equals("waypoints")) {
            for (Waypoint w : FastTravel.waypoints().values()) {
                added += addCircle(w.x() >> 4, w.z() >> 4, radius > 0 ? radius : DEFAULT_WAYPOINTS);
            }
        }
        if (all || target.equals("roads")) {
            added += addRoads(radius > 0 ? radius : DEFAULT_ROADS);
        }
        return added;
    }

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("lotr_pregen").requires(s -> s.hasPermission(3))
                .then(Commands.literal("start")
                        .then(Commands.argument("target", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                        new String[] {"all", "roads", "waypoints", "spawn", "players"}, b))
                                .executes(ctx -> start(ctx, 0))
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 256))
                                        .executes(ctx -> start(ctx, IntegerArgumentType.getInteger(ctx, "radius"))))))
                .then(Commands.literal("pause").executes(ctx -> setPaused(ctx, true)))
                .then(Commands.literal("resume").executes(ctx -> setPaused(ctx, false)))
                .then(Commands.literal("cancel").executes(ctx -> {
                    Pregen p = get(ctx.getSource().getServer());
                    int n = p.queue.size();
                    p.queue.clear();
                    p.setDirty();
                    reply(ctx, "Cancelled; dropped " + n + " queued chunks");
                    return n;
                }))
                .then(Commands.literal("speed")
                        .then(Commands.argument("chunks", IntegerArgumentType.integer(1, 64)).executes(ctx -> {
                            Pregen p = get(ctx.getSource().getServer());
                            p.maxInFlight = IntegerArgumentType.getInteger(ctx, "chunks");
                            p.setDirty();
                            reply(ctx, "Up to " + p.maxInFlight + " chunks at a time");
                            return 1;
                        })))
                .then(Commands.literal("follow")
                        .then(Commands.literal("off").executes(ctx -> follow(ctx, false, 0)))
                        .then(Commands.literal("on").executes(ctx -> follow(ctx, true, DEFAULT_PLAYERS))
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 128))
                                        .executes(ctx -> follow(ctx, true,
                                                IntegerArgumentType.getInteger(ctx, "radius"))))))
                .then(Commands.literal("status").executes(ctx -> {
                    Pregen p = get(ctx.getSource().getServer());
                    reply(ctx, (p.paused ? "Paused" : p.queue.isEmpty() ? "Idle" : "Running") + ": "
                            + p.queue.size() + " queued, " + p.pending.size() + " generating, " + p.done
                            + " done; speed " + p.maxInFlight + "; follow players "
                            + (p.follow ? "on (" + p.followRadius + ")" : "off"));
                    return p.queue.size();
                })));
    }

    private static int start(CommandContext<CommandSourceStack> ctx, int radius) {
        MinecraftServer server = ctx.getSource().getServer();
        ServerLevel level = LotrDimensions.middleEarth(server);
        if (level == null) {
            ctx.getSource().sendFailure(Component.literal("This world has no Middle-earth"));
            return 0;
        }
        String target = StringArgumentType.getString(ctx, "target");
        Pregen p = get(server);
        int added = p.add(server, level, target, radius);
        p.paused = false;
        reply(ctx, "Queued " + added + " new chunks near " + target + " (" + p.queue.size()
                + " in queue). /lotr_pregen status to watch");
        return added;
    }

    private static int setPaused(CommandContext<CommandSourceStack> ctx, boolean paused) {
        Pregen p = get(ctx.getSource().getServer());
        p.paused = paused;
        p.setDirty();
        reply(ctx, paused ? "Paused" : "Resumed");
        return 1;
    }

    private static int follow(CommandContext<CommandSourceStack> ctx, boolean on, int radius) {
        Pregen p = get(ctx.getSource().getServer());
        p.follow = on;
        if (on) {
            p.followRadius = radius;
        }
        p.followed.clear();
        p.setDirty();
        reply(ctx, on ? "Generating ahead within " + radius + " chunks of players when idle" : "Follow off");
        return 1;
    }

    private static void reply(CommandContext<CommandSourceStack> ctx, String text) {
        ctx.getSource().sendSuccess(() -> Component.literal("[Pregen] " + text), true);
    }
}
