package com.porcupinestudios.lotrport.journey;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.npc.LotrNpc;
import com.porcupinestudios.lotrport.registry.LotrRegistries;
import com.porcupinestudios.lotrport.society.Person;
import com.porcupinestudios.lotrport.society.Persons;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * A journey seen in person. When a player comes within {@code bodies.showRadius} of a party whose place is loaded,
 * its people (their own bodies), steeds (as they were saved) and wagons come into the world there and walk the
 * route; their leader's place sets {@code done}. With no player within {@code hideRadius} they go back to data,
 * steeds saved as they are now.
 *
 * <p>Stuck rules, by how long the leader has made no headway along the route: after {@code repathSeconds} it paths
 * again to a point further on; after {@code hopSeconds} the whole party is moved to the next route point it can
 * stand on (as if it found a way round); after {@code giveUpSeconds} the bodies go back to data, which goes on by
 * the plan, and stay away a minute. A body found in the world for a journey that isn't embodied (saved with its chunk
 * last time) is removed as it loads.
 */
public final class JourneyBodies {
    /** How far ahead along the route the leader walks to, in route cost. */
    static final double LEAD = 24;
    /** Within this of the end the party has arrived. */
    static final double END = 4;

    /** The entities of each embodied journey. */
    private static final Map<UUID, List<UUID>> BODIES = new HashMap<>();

    private JourneyBodies() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> {
            if (e.getServer().getTickCount() % 10 == 5) {
                pulse(e.getServer());
            }
        });
        NeoForge.EVENT_BUS.addListener(JourneyBodies::onJoin);
    }

    private static void onJoin(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide() || !e.getEntity().hasData(LotrRegistries.JOURNEY)) {
            return;
        }
        String id = e.getEntity().getData(LotrRegistries.JOURNEY);
        if (id.isEmpty()) {
            return;
        }
        List<UUID> mine = BODIES.get(parse(id));
        if (mine == null || !mine.contains(e.getEntity().getUUID())) {
            e.setCanceled(true);
        }
    }

    static void pulse(MinecraftServer server) {
        Journeys all = Journeys.get(server);
        JourneySettings.Bodies s = JourneyData.settings().bodies();
        long now = server.overworld().getGameTime();
        for (Journey j : all.all()) {
            ServerLevel level = server.getLevel(j.dimension);
            if (level == null) {
                continue;
            }
            if (!j.moving()) {
                if (j.embodied) {
                    dissolve(level, j);
                }
                continue;
            }
            double[] p = j.position();
            double near = nearestPlayer(level, p[0], p[1]);
            if (!j.embodied) {
                BlockPos at = BlockPos.containing(p[0], 0, p[1]);
                if (near <= s.showRadius() && now >= j.noBodiesUntil && level.isLoaded(at)) {
                    embody(server, level, j);
                }
                continue;
            }
            if (near > s.hideRadius()) {
                dissolve(level, j);
                continue;
            }
            walk(server, all, level, j, s, now);
        }
    }

    private static void embody(MinecraftServer server, ServerLevel level, Journey j) {
        double[] p = j.position();
        List<UUID> ids = new ArrayList<>();
        BODIES.put(j.id, ids);
        Persons persons = Persons.get(server);
        int n = 0;
        for (UUID pid : j.people) {
            Optional<Person> person = persons.get(pid);
            if (person.isEmpty() || level.getEntity(pid) != null) {
                continue;
            }
            Optional<LotrNpc> body = LotrNpc.bodyFor(level, person.get());
            if (body.isPresent()) {
                LotrNpc npc = body.get();
                place(level, npc, p[0] + offset(n), p[1] + offset(n + 1));
                npc.setPersistenceRequired();
                npc.setData(LotrRegistries.JOURNEY, j.id.toString());
                ids.add(npc.getUUID());
                level.addFreshEntity(npc);
                n++;
            }
        }
        for (Journey.Steed st : j.steeds) {
            Optional<Entity> made = EntityType.create(st.entity(), level);
            if (made.isEmpty() || level.getEntity(made.get().getUUID()) != null) {
                continue;
            }
            Entity e = made.get();
            place(level, e, p[0] + offset(n), p[1] + offset(n + 1));
            e.setData(LotrRegistries.JOURNEY, j.id.toString());
            ids.add(e.getUUID());
            level.addFreshEntity(e);
            n++;
        }
        j.embodied = true;
        j.bestDone = j.done;
        j.bestAt = level.getGameTime();
        j.leg = 0;
    }

    /** Back to data: steeds saved as they are, bodies removed. */
    static void dissolve(ServerLevel level, Journey j) {
        List<UUID> ids = BODIES.remove(j.id);
        j.embodied = false;
        if (ids == null) {
            return;
        }
        List<Journey.Steed> kept = new ArrayList<>();
        for (int i = 0; i < j.steeds.size(); i++) {
            Journey.Steed st = j.steeds.get(i);
            UUID sid = st.entity().hasUUID("UUID") ? st.entity().getUUID("UUID") : null;
            Entity e = sid == null ? null : level.getEntity(sid);
            if (e != null && e.isAlive()) {
                CompoundTag tag = new CompoundTag();
                e.removeData(LotrRegistries.JOURNEY);
                if (e.save(tag)) {
                    kept.add(new Journey.Steed(st.def(), tag));
                }
            } else if (e == null) {
                kept.add(st);
            }
        }
        j.steeds.clear();
        j.steeds.addAll(kept);
        for (UUID id : ids) {
            Entity e = level.getEntity(id);
            if (e != null) {
                e.discard();
            }
        }
    }

    private static void walk(MinecraftServer server, Journeys all, ServerLevel level, Journey j,
            JourneySettings.Bodies s, long now) {
        List<UUID> ids = BODIES.getOrDefault(j.id, List.of());
        Mob leader = null;
        List<Mob> rest = new ArrayList<>();
        for (UUID id : ids) {
            if (level.getEntity(id) instanceof Mob m && m.isAlive()) {
                if (leader == null && m instanceof LotrNpc) {
                    leader = m;
                } else {
                    rest.add(m);
                }
            }
        }
        if (leader == null) {
            // everyone in it is dead (bodies that left the loaded area are handled by the hide radius)
            boolean anyPerson = ids.stream().anyMatch(id -> level.getEntity(id) instanceof LotrNpc);
            if (!anyPerson) {
                dissolve(level, j);
                j.people.removeIf(pid -> Persons.get(server).get(pid).isEmpty());
                if (j.people.isEmpty()) {
                    all.lose(server, j);
                }
            }
            return;
        }
        double d = j.route.doneAt(leader.getX(), leader.getZ(), j.leg);
        if (d > j.done) {
            j.done = d;
            all.setDirty();
        }
        if (j.done >= j.route.total() - END) {
            j.done = j.route.total();
            dissolve(level, j);
            all.arrive(server, j);
            return;
        }
        if (j.done > j.bestDone + 1) {
            j.bestDone = j.done;
            j.bestAt = now;
        }
        long still = (now - j.bestAt) / 20;
        double[] goal = j.route.at(Math.min(j.route.total(), j.done + LEAD));
        if (still >= s.giveUpSeconds()) {
            LotrPort.LOG.info("[journeys] {} stuck near {}, {}: back to data", j.id, (int) leader.getX(),
                    (int) leader.getZ());
            dissolve(level, j);
            j.noBodiesUntil = now + 20 * 60;
            return;
        }
        if (still >= s.hopSeconds() && (now - j.bestAt) % (20L * s.hopSeconds()) < 10) {
            double ahead = Math.min(j.route.total(), j.done + LEAD);
            double[] h = j.route.at(ahead);
            int n = 0;
            for (Mob m : withLeader(leader, rest)) {
                place(level, m, h[0] + offset(n), h[1] + offset(n + 1));
                m.getNavigation().stop();
                n++;
            }
            j.done = ahead;
            return;
        }
        boolean repath = still >= s.repathSeconds() || leader.getNavigation().isDone();
        if (repath) {
            BlockPos g = top(level, goal[0], goal[1]);
            leader.getNavigation().moveTo(g.getX() + 0.5, g.getY(), g.getZ() + 0.5, 1.0);
        }
        for (Mob m : rest) {
            if (m.distanceToSqr(leader) > 36 || m.getNavigation().isDone()) {
                m.getNavigation().moveTo(leader, 1.1);
            }
            if (m.distanceToSqr(leader) > 48 * 48) {
                place(level, m, leader.getX(), leader.getZ());
            }
        }
    }

    private static List<Mob> withLeader(Mob leader, List<Mob> rest) {
        List<Mob> out = new ArrayList<>();
        out.add(leader);
        out.addAll(rest);
        return out;
    }

    private static void place(ServerLevel level, Entity e, double x, double z) {
        BlockPos at = top(level, x, z);
        e.moveTo(x, at.getY(), z, level.random.nextFloat() * 360f, 0f);
    }

    private static BlockPos top(ServerLevel level, double x, double z) {
        return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(x, 0, z));
    }

    private static double offset(int n) {
        return ((n * 7919) % 5) - 2;
    }

    private static double nearestPlayer(ServerLevel level, double x, double z) {
        double best = Double.MAX_VALUE;
        for (ServerPlayer p : level.players()) {
            double dx = p.getX() - x;
            double dz = p.getZ() - z;
            best = Math.min(best, Math.sqrt(dx * dx + dz * dz));
        }
        return best;
    }

    private static UUID parse(String s) {
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException ex) {
            return new UUID(0, 0);
        }
    }

    /** For tests: the entities of an embodied journey. */
    public static List<UUID> bodiesOf(UUID journey) {
        return List.copyOf(BODIES.getOrDefault(journey, List.of()));
    }
}
