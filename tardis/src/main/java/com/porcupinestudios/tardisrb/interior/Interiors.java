package com.porcupinestudios.tardisrb.interior;

import com.porcupinestudios.tardisrb.TardisRB;
import com.porcupinestudios.tardisrb.block.Exteriors;
import com.porcupinestudios.tardisrb.core.TardisManager;
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
 * {@code plot_spacing} blocks apart along x. The first console room is a structure template from the tuning file,
 * placed when the plot is handed out, with the repair basement ({@code basement}) at {@code basement_offset} under
 * it; a ladder in the console room floor leads down to the Repair Station. A block's TARDIS is found from its plot.
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
        BlockPos origin = origin(plot);
        room.get().placeInWorld(level.get(), origin, origin, new StructurePlaceSettings(), level.get().getRandom(),
                Block.UPDATE_CLIENTS);
        Optional<StructureTemplate> basement = level.get().getStructureManager().get(t.basement());
        if (basement.isPresent()) {
            BlockPos at = origin.offset(t.basementOffset());
            basement.get().placeInWorld(level.get(), at, at, new StructurePlaceSettings(), level.get().getRandom(),
                    Block.UPDATE_CLIENTS);
        } else {
            TardisRB.LOG.error("[interior] basement template {} is missing", t.basement());
        }
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
        player.teleportTo(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0F, 0F);
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
