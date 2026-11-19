package com.porcupinestudios.lotrport.colony.building;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.society.sim.ColonySystem;
import com.porcupinestudios.lotrport.society.sim.ColonySystems;
import com.porcupinestudios.lotrport.society.sim.SystemContext;
import com.porcupinestudios.lotrport.society.sim.Tier;
import com.porcupinestudios.lotrport.colony.Colonies;
import com.porcupinestudios.lotrport.colony.Colony;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Wires the buildings feature: data, the component, the re-measure system, and measuring again at once when a
 * building's door, walls or plaque are broken.
 */
public final class BuildingSystems {
    /** Measures buildings again, a few per pulse, oldest first, while the colony is in the world. */
    public static final ResourceLocation RECHECK = LotrPort.id("building_recheck");
    /** Count: buildings measured this pass. */
    public static final ResourceLocation RECHECKED = LotrPort.id("buildings_rechecked");
    /** Count: buildings lost this pass. */
    public static final ResourceLocation LOST = LotrPort.id("buildings_lost");

    private BuildingSystems() {}

    public static void register() {
        Buildings.init();
        BuildingSettings.register();
        BuildingType.register();
        ColonySystems.register(ColonySystem.of(RECHECK, ColonySystem.Cadence.PULSE, Tier.IN_WORLD, Set.of(),
                BuildingSystems::recheck));
        // a building is measured again the tick after its door, a wall, its plaque or anything in it is broken
        // (by hand or explosion), so taking a door or plaque away takes the building down at once
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.level.BlockEvent.BreakEvent e) -> {
            if (e.getLevel() instanceof ServerLevel sl) {
                changed(sl, e.getPos());
            }
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.level.ExplosionEvent.Detonate e) -> {
            if (e.getLevel() instanceof ServerLevel sl) {
                e.getAffectedBlocks().forEach(p -> changed(sl, p));
            }
        });
    }

    private static final Map<ServerLevel, Set<BlockPos>> PENDING = new HashMap<>();

    /** A block changed at {@code pos} (broken, blown up, a plaque fell): the buildings it touches are checked next tick. */
    public static void changed(ServerLevel level, BlockPos pos) {
        Set<BlockPos> set = PENDING.computeIfAbsent(level, l -> new HashSet<>());
        boolean first = set.isEmpty();
        set.add(pos.immutable());
        if (first) {
            MinecraftServer server = level.getServer();
            server.tell(new TickTask(server.getTickCount() + 1, () -> {
                Set<BlockPos> changed = PENDING.remove(level);
                if (changed != null) {
                    recheckAround(level, changed);
                }
            }));
        }
    }

    static void recheckAround(ServerLevel level, Set<BlockPos> changed) {
        for (Colony c : List.copyOf(Colonies.get(level.getServer()).all())) {
            if (c.ai() || !c.dimension().equals(level.dimension())) {
                continue;
            }
            for (Building b : List.copyOf(Buildings.of(c).list())) {
                if (touches(b, changed)) {
                    ColonyBuildings.recheck(level, c, b);
                }
            }
        }
    }

    /** Whether a change is the building's plaque, its door's column, one of its blocks or right beside one. */
    private static boolean touches(Building b, Set<BlockPos> changed) {
        it.unimi.dsi.fastutil.longs.LongSet blocks = null;
        for (BlockPos p : changed) {
            if (b.plaque().map(p::equals).orElse(false)) {
                return true;
            }
            if (p.distManhattan(b.door()) > 3 * BuildingSettings.get().maxFloor()) {
                continue; // too far to be part of it: skip unpacking its blocks
            }
            if (p.getX() == b.door().getX() && p.getZ() == b.door().getZ() && p.getY() >= b.door().getY() - 1
                    && p.getY() <= b.door().getY() + 16) {
                return true;
            }
            if (blocks == null) {
                blocks = b.blocks();
            }
            if (blocks.contains(p.asLong())) {
                return true;
            }
            for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
                if (blocks.contains(p.relative(d).asLong())) {
                    return true;
                }
            }
        }
        return false;
    }

    static void recheck(SystemContext ctx) {
        if (ctx.level() == null || ctx.colony().ai()) {
            return;
        }
        BuildingSettings s = BuildingSettings.get();
        List<Building> due = Buildings.of(ctx.colony()).list().stream()
                .filter(b -> ctx.now() - b.checked() >= s.recheckTicks() && ctx.level().isLoaded(b.door()))
                .sorted(Comparator.comparingLong(Building::checked)).limit(Math.max(0, s.recheckPerPulse())).toList();
        for (Building b : due) {
            ctx.count(RECHECKED, 1);
            if (!ColonyBuildings.recheck(ctx.level(), ctx.colony(), b)) {
                ctx.count(LOST, 1);
            }
        }
    }
}
