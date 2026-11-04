package com.porcupinestudios.tardisrb.interior;

import com.porcupinestudios.tardisrb.TardisRB;
import com.porcupinestudios.tardisrb.block.Exteriors;
import com.porcupinestudios.tardisrb.core.TardisManager;
import com.porcupinestudios.tardisrb.door.Doors;
import com.porcupinestudios.tardisrb.core.TardisRecord;
import com.porcupinestudios.tardisrb.progression.TardisContent;
import com.porcupinestudios.tardisrb.progression.Tuning;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Interiors: every TARDIS gets a plot in one shared void dimension ({@code tardis_rb:interior}), spaced
 * {@code plot_spacing} blocks apart along x. The console room (Aaron 2026-11-04: the Capaldi room) is a structure
 * template from the tuning file, placed when the plot is handed out, with the repair basement ({@code basement}) at
 * {@code basement_offset} under its lower deck; a ladder in the lower deck's floor ({@code hatch}) leads down to the
 * Repair Station. A block's TARDIS is found from its plot.
 */
public final class Interiors {
    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION, TardisRB.id("interior"));

    private Interiors() {}

    public static Optional<ServerLevel> level(MinecraftServer server) {
        return Optional.ofNullable(server.getLevel(DIMENSION));
    }

    public static BlockPos origin(int plot) {
        Tuning.Interior t = TardisContent.tuning().interior();
        return new BlockPos(plot * t.plotSpacing(), t.floorY(), 0);
    }

    /** The plot a position in the interior dimension belongs to. */
    public static int plotAt(BlockPos pos) {
        int spacing = TardisContent.tuning().interior().plotSpacing();
        return Math.floorDiv(pos.getX() + spacing / 2, spacing);
    }

    /** The TARDIS whose interior this position is in. */
    public static Optional<TardisRecord> at(ServerLevel level, BlockPos pos) {
        if (!level.dimension().equals(DIMENSION)) {
            return Optional.empty();
        }
        return TardisManager.get(level.getServer()).byPlot(plotAt(pos));
    }

    /** Hands out a plot and builds the first console room, once. Returns false if it couldn't be built. */
    public static boolean ensure(MinecraftServer server, TardisRecord tardis) {
        if (tardis.plot() >= 0) {
            return true;
        }
        Optional<ServerLevel> level = level(server);
        if (level.isEmpty()) {
            TardisRB.LOG.error("[interior] dimension {} is missing", DIMENSION.location());
            return false;
        }
        Tuning.Interior t = TardisContent.tuning().interior();
        Optional<StructureTemplate> room = level.get().getStructureManager().get(t.room());
        if (room.isEmpty()) {
            TardisRB.LOG.error("[interior] room template {} is missing", t.room());
            return false;
        }
        int plot = TardisManager.get(server).assignPlot(tardis);
        build(level.get(), origin(plot), room.get());
        return true;
    }

    /**
     * Builds the console room with its origin at {@code origin} (the floor in front of the console) and the repair
     * basement under it. Game tests build one anywhere.
     */
    public static void build(ServerLevel level, BlockPos origin, StructureTemplate room) {
        Tuning.Interior t = TardisContent.tuning().interior();
        BlockPos corner = origin.offset(t.roomOffset());
        room.placeInWorld(level, corner, corner, new StructurePlaceSettings(), level.getRandom(), Block.UPDATE_CLIENTS);
        Optional<StructureTemplate> basement = level.getStructureManager().get(t.basement());
        if (basement.isPresent()) {
            BlockPos at = origin.offset(t.basementOffset());
            basement.get().placeInWorld(level, at, at, new StructurePlaceSettings(), level.getRandom(),
                    Block.UPDATE_CLIENTS);
        } else {
            TardisRB.LOG.error("[interior] basement template {} is missing", t.basement());
        }
    }

    /**
     * Moves a TARDIS into a fresh plot with a newly built console room and its rooms rebuilt on the current layout
     * (for interiors made before a layout change). The old plot is left as it is. Players inside are moved along.
     */
    public static boolean rebuild(MinecraftServer server, TardisRecord tardis) {
        Optional<ServerLevel> level = level(server);
        Optional<StructureTemplate> room = level.flatMap(l -> l.getStructureManager().get(
                TardisContent.tuning().interior().room()));
        if (level.isEmpty() || room.isEmpty()) {
            return false;
        }
        Doors.close(server, tardis);
        int old = tardis.plot();
        int plot = TardisManager.get(server).newPlot(tardis);
        build(level.get(), origin(plot), room.get());
        Rooms.rebuild(level.get(), origin(plot), tardis);
        if (old >= 0) {
            for (ServerPlayer p : level.get().players()) {
                if (plotAt(p.blockPosition()) == old) {
                    BlockPos at = spawn(tardis);
                    p.teleportTo(level.get(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 180F, 0F);
                }
            }
        }
        TardisRB.LOG.info("[interior] {} rebuilt in plot {} (was {})", tardis.name(), plot, old);
        return true;
    }

    /** The corner of a TARDIS's repair basement. */
    public static BlockPos basement(TardisRecord tardis) {
        return origin(tardis.plot()).offset(TardisContent.tuning().interior().basementOffset());
    }

    /** Where a player appears inside. */
    public static BlockPos spawn(TardisRecord tardis) {
        return origin(tardis.plot()).offset(TardisContent.tuning().interior().spawn());
    }

    public static boolean enter(ServerPlayer player, TardisRecord tardis) {
        if (!ensure(player.server, tardis)) {
            return false;
        }
        ServerLevel level = level(player.server).orElseThrow();
        BlockPos at = spawn(tardis);
        // the spawn is just inside the main doors, looking at the console
        player.teleportTo(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 180F, 0F);
        return true;
    }

    /** Walks the player out in front of the exterior; false when the TARDIS isn't standing anywhere. */
    public static boolean exit(ServerPlayer player, TardisRecord tardis) {
        Optional<GlobalPos> where = tardis.location();
        if (where.isEmpty()) {
            return false;
        }
        ServerLevel level = player.server.getLevel(where.get().dimension());
        if (level == null) {
            return false;
        }
        Direction facing = Exteriors.facing(level, where.get().pos());
        BlockPos out = where.get().pos().relative(facing);
        player.teleportTo(level, out.getX() + 0.5, out.getY(), out.getZ() + 0.5, facing.toYRot(), 0F);
        return true;
    }
}
