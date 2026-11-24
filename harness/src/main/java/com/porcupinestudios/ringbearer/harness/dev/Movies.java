package com.porcupinestudios.ringbearer.harness.dev;

import com.mojang.logging.LogUtils;
import com.porcupinestudios.ringbearer.harness.gametest.HarnessTests;
import com.porcupinestudios.ringbearer.harness.gametest.TowRun;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/**
 * Dev only ({@code -Dringbearer_harness.movies=true}, optionally {@code =name,name}): when a player joins, plays the
 * GameTest tow runs one after another on courses near them, with the server's tick rate slowed
 * ({@code ringbearer_harness.movieTickRate}, default 5) so the client renders many frames per game second (Sable steps
 * physics per tick, so the physics is the same). Tells the client's {@code MovieRecorder} what to film through system
 * chat lines starting {@code [movie]}.
 */
public final class Movies {
    private static final Logger LOG = LogUtils.getLogger();
    public static final String PREFIX = "[movie] ";
    public static final String WANTED = System.getProperty("ringbearer_harness.movies", "");
    public static final boolean ON = !WANTED.isEmpty() && !WANTED.equals("false");

    private static ServerPlayer player;
    private static BlockPos ground;
    private static final List<TowRun.Scenario> QUEUE = new ArrayList<>();
    private static Host current;
    private static int wait = -1;

    private Movies() {}

    public static void register() {
        if (!ON) {
            return;
        }
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) {
                player = p;
                p.setGameMode(GameType.SPECTATOR);
                List<String> names = WANTED.equals("true") ? List.of() : Arrays.asList(WANTED.split(","));
                java.util.stream.Stream.concat(HarnessTests.SCENARIOS.stream(), HarnessTests.HARD.stream())
                        .filter(s -> names.isEmpty() || names.contains(s.name())).forEach(QUEUE::add);
                wait = 60;
            }
        });
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> {
            Host host = current;
            if (host != null) {
                host.tick++;
                Runnable r;
                while ((r = host.due()) != null) {
                    r.run();
                }
            } else if (wait > 0 && --wait == 0) {
                next(e.getServer().overworld());
            }
        });
    }

    private static void next(ServerLevel level) {
        if (QUEUE.isEmpty()) {
            say("all done");
            return;
        }
        if (ground == null) {
            level.setDayTime(6000);
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
            level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, level.getServer());
            level.getServer().tickRateManager().setTickRate(Float.parseFloat(
                    System.getProperty("ringbearer_harness.movieTickRate", "5")));
            BlockPos p = player.blockPosition();
            ground = new BlockPos(p.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING, p.getX(), p.getZ()) - 1,
                    p.getZ());
        }
        TowRun.Scenario sc = QUEUE.remove(0);
        current = new Host(level, sc);
        LOG.info("[movie] playing {}", sc.name());
        TowRun.start(current, sc);
    }

    private static void say(String line) {
        player.sendSystemMessage(Component.literal(PREFIX + line));
    }

    /** One run's host: its own tick count and schedule; courses side by side along +x from the player. */
    private static final class Host implements TowRun.Host {
        private final ServerLevel level;
        private final TowRun.Scenario sc;
        private final TreeMap<Long, List<Runnable>> schedule = new TreeMap<>();
        long tick;

        Host(ServerLevel level, TowRun.Scenario sc) {
            this.level = level;
            this.sc = sc;
        }

        Runnable due() {
            var first = schedule.firstEntry();
            if (first == null || first.getKey() > tick) {
                return null;
            }
            Runnable r = first.getValue().remove(0);
            if (first.getValue().isEmpty()) {
                schedule.remove(first.getKey());
            }
            return r;
        }

        @Override public ServerLevel level() { return level; }
        @Override public long tick() { return tick; }

        @Override
        public void at(long at, Runnable r) {
            schedule.computeIfAbsent(at, k -> new ArrayList<>()).add(r);
        }

        @Override
        public BlockPos course(int index) {
            return ground.offset(24 + index * 64, 0, -8);
        }

        /** Smooth stone: it shows the block grid and the steps, and compresses far better in a GIF than grass. */
        @Override
        public net.minecraft.world.level.block.state.BlockState groundTop() {
            return net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState();
        }

        @Override
        public void started(net.minecraft.world.entity.Mob horse) {
            say("start " + sc.name() + " " + horse.getId());
        }

        @Override
        public void fail(String message) {
            LOG.info("[movie] {} failed its marks: {}", sc.name(), message);
            done();
        }

        @Override
        public void succeed() {
            done();
        }

        private void done() {
            say("end " + sc.name());
            current = null;
            wait = 40;
        }
    }
}
