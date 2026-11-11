package com.porcupinestudios.lotrport.colony.work;

import com.porcupinestudios.lotrport.LotrPort;
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
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Bodies on demand (in person tier only): at most {@code bodies_max} citizens walk about a colony a player is near;
 * the farthest from players go inside (their bodies are removed, their records stay). Below the cap, citizens without
 * a loaded body come out of their home (or workplace, else the Hall), a few a pulse. Soldiers and applicants are left
 * alone: units keep their own bodies.
 */
public final class Bodies {
    public static final ResourceLocation SYSTEM = LotrPort.id("bodies");
    /** Counts: bodies sent inside, bodies brought out. */
    public static final ResourceLocation INSIDE = LotrPort.id("bodies_inside");
    public static final ResourceLocation OUT = LotrPort.id("bodies_out");
    /** Most bodies brought out in one pulse (a colony fills up over a few seconds, not in one tick). */
    static final int OUT_PER_PULSE = 4;

    private Bodies() {}

    public static void run(SystemContext ctx) {
        ServerLevel level = ctx.level();
        if (level == null || ctx.colony().ai()) {
            return;
        }
        int max = Math.max(0, LifeSettings.get().bodiesMax());
        List<LotrNpc> out = new ArrayList<>();
        List<Person> inside = new ArrayList<>();
        for (Person p : Society.citizens(ctx.server(), ctx.colony())) {
            if (p.has(CoreComponents.UNIT)) {
                continue;
            }
            Entity e = level.getEntity(p.id());
            if (e instanceof LotrNpc npc && npc.isAlive()) {
                out.add(npc);
            } else {
                inside.add(p);
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
        Buildings buildings = Buildings.of(ctx.colony());
        int room = Math.min(OUT_PER_PULSE, max - out.size());
        for (Person p : inside) {
            if (room <= 0) {
                break;
            }
            BlockPos door = p.get(WorkComponents.HOME).flatMap(h -> buildings.get(h.building())).map(Building::door)
                    .or(() -> p.get(WorkComponents.JOB).flatMap(j -> buildings.get(j.building())).map(Building::door))
                    .orElse(ctx.colony().hall());
            if (!level.isLoaded(door) || level.getEntity(p.id()) != null) {
                continue;
            }
            BlockPos at = spot(level, ctx.colony(), door);
            LotrNpc.bodyFor(level, p).ifPresent(npc -> {
                npc.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.random.nextFloat() * 360f, 0f);
                npc.setPersistenceRequired();
                npc.setData(LotrRegistries.COLONY, ctx.colony().id().toString());
                if (level.addFreshEntity(npc)) {
                    ctx.count(OUT, 1);
                }
            });
            room--;
        }
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
