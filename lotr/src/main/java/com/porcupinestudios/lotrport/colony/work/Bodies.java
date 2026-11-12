package com.porcupinestudios.lotrport.colony.work;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.colonist.Colonists;
import com.porcupinestudios.lotrport.colonist.Schedules;
import com.porcupinestudios.lotrport.colonist.TaskRules;
import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.colony.building.Building;
import com.porcupinestudios.lotrport.colony.building.Buildings;
import com.porcupinestudios.lotrport.npc.LotrNpc;
import com.porcupinestudios.lotrport.registry.LotrRegistries;
import com.porcupinestudios.lotrport.society.CoreComponents;
import com.porcupinestudios.lotrport.society.Person;
import com.porcupinestudios.lotrport.society.Society;
import com.porcupinestudios.lotrport.society.sim.SystemContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Bodies on demand (in person tier only): at most {@code bodies_max} citizens walk about a colony a player is near;
 * the farthest from players go inside (their bodies are removed, their records stay). Each body follows the top of
 * its task queue ({@code colonist.TaskRules}): it walks to that task's place ({@code LotrNpc.setTaskTarget}) and
 * potters about there; a colonist whose task is sleep and who has a home walks home and goes to bed (the body goes
 * inside once at the door, and stays in until another task wins). Below the cap, citizens without a body come out
 * at their task's place, a few a pulse. Soldiers and applicants are left alone: units keep their own bodies.
 */
public final class Bodies {
    public static final ResourceLocation SYSTEM = LotrPort.id("bodies");
    /** Counts: bodies sent inside, bodies brought out. */
    public static final ResourceLocation INSIDE = LotrPort.id("bodies_inside");
    public static final ResourceLocation OUT = LotrPort.id("bodies_out");
    /** Most bodies brought out in one pulse (a colony fills up over a few seconds, not in one tick). */
    static final int OUT_PER_PULSE = 4;
    /** A sleeper goes in within this of their door. */
    static final double AT_DOOR = 3;

    private Bodies() {}

    public static void run(SystemContext ctx) {
        ServerLevel level = ctx.level();
        if (level == null || ctx.colony().ai()) {
            return;
        }
        int max = Math.max(0, LifeSettings.get().bodiesMax());
        long dayTime = level.getDayTime();
        Schedules schedules = Colonists.schedules(ctx.colony());
        Buildings buildings = Buildings.of(ctx.colony());
        List<Building> food = foodPlaces(buildings);
        List<LotrNpc> out = new ArrayList<>();
        List<Person> inside = new ArrayList<>();
        java.util.Map<java.util.UUID, BlockPos> places = new java.util.HashMap<>();
        for (Person p : Society.citizens(ctx.server(), ctx.colony())) {
            if (p.has(CoreComponents.UNIT)
                    || com.porcupinestudios.lotrport.journey.Journeys.away(ctx.server(), p.id())) {
                continue;
            }
            Optional<TaskRules.Entry> task = TaskRules.current(Colonists.tasks(p, schedules, !food.isEmpty(),
                    dayTime));
            BlockPos place = place(ctx.colony(), buildings, food, p, task.map(t -> t.def().place()).orElse("home"));
            boolean bed = task.isPresent() && task.get().def().action().equals("sleep") && p.has(WorkComponents.HOME);
            Entity e = level.getEntity(p.id());
            if (e instanceof LotrNpc npc && npc.isAlive()) {
                npc.setTaskTarget(place);
                if (bed && npc.distanceToSqr(net.minecraft.world.phys.Vec3.atBottomCenterOf(place))
                        < AT_DOOR * AT_DOOR) {
                    npc.discard();
                    ctx.count(INSIDE, 1);
                } else {
                    out.add(npc);
                }
            } else if (!bed) {
                inside.add(p);
                places.put(p.id(), place);
            }
        }
        if (out.size() > max) {
            out.sort(Comparator.comparingDouble((LotrNpc n) -> nearestPlayer(level, n)).reversed());
            for (int i = 0; i < out.size() - max; i++) {
                out.get(i).discard();
                ctx.count(INSIDE, 1);
            }
            return;
        }
        int room = Math.min(OUT_PER_PULSE, max - out.size());
        for (Person p : inside) {
            if (room <= 0) {
                break;
            }
            BlockPos door = places.get(p.id());
            if (!level.isLoaded(door) || level.getEntity(p.id()) != null) {
                continue;
            }
            BlockPos at = spot(level, ctx.colony(), door);
            LotrNpc.bodyFor(level, p).ifPresent(npc -> {
                npc.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.random.nextFloat() * 360f, 0f);
                npc.setPersistenceRequired();
                npc.setData(LotrRegistries.COLONY, ctx.colony().id().toString());
                npc.setTaskTarget(door);
                if (level.addFreshEntity(npc)) {
                    ctx.count(OUT, 1);
                }
            });
            room--;
        }
    }

    /** Where colonists eat: storehouses and kitchens. */
    public static List<Building> foodPlaces(Buildings buildings) {
        List<Building> out = new ArrayList<>();
        for (Building b : buildings.list()) {
            String type = b.type().getPath();
            if (type.equals("storehouse") || type.equals("kitchen")) {
                out.add(b);
            }
        }
        return out;
    }

    /** The door of a task's place for this colonist: home, workplace, food (the nearest) or the Hall. */
    public static BlockPos place(Colony colony, Buildings buildings, List<Building> food, Person p, String place) {
        Optional<BlockPos> home = p.get(WorkComponents.HOME).flatMap(h -> buildings.get(h.building()))
                .map(Building::door);
        Optional<BlockPos> work = p.get(WorkComponents.JOB).flatMap(j -> buildings.get(j.building()))
                .map(Building::door);
        return switch (place) {
            case "workplace" -> work.orElse(colony.hall());
            case "food" -> {
                BlockPos from = home.orElse(colony.hall());
                yield food.stream().map(Building::door).min(Comparator.comparingDouble(d -> d.distSqr(from)))
                        .orElse(colony.hall());
            }
            case "hall" -> colony.hall();
            default -> home.orElse(colony.hall());
        };
    }

    /** Outside the door: the first standing spot beside it, else the ground near the Hall. */
    static BlockPos spot(ServerLevel level, Colony colony, BlockPos door) {
        for (var d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            BlockPos p = door.relative(d);
            if (level.getBlockState(p).getCollisionShape(level, p).isEmpty()
                    && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()
                    && !level.getBlockState(p.below()).getCollisionShape(level, p.below()).isEmpty()) {
                return p;
            }
        }
        return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, colony.hall().offset(1, 0, 1));
    }

    private static double nearestPlayer(ServerLevel level, Entity e) {
        Player p = level.getNearestPlayer(e, -1);
        return p == null ? Double.MAX_VALUE : p.distanceToSqr(e);
    }
}
