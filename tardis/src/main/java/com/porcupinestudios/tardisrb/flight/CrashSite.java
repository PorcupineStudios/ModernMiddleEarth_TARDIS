package com.porcupinestudios.tardisrb.flight;

import com.porcupinestudios.ringbearer.api.history.HistoryEntry;
import com.porcupinestudios.ringbearer.api.history.HistoryLog;
import com.porcupinestudios.ringbearer.api.provider.Providers;
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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * The one crash site per world: on the first server start, an unclaimed wrecked TARDIS lands a few blocks from the
 * spawn of the start dimension (Middle-earth in a Middle-earth world, via the shared API's {@code Providers}). The first
 * player to open its door claims it.
 */
public final class CrashSite {
    public static final ResourceLocation HISTORY = TardisRB.id("crash_site");

    private CrashSite() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent e) -> place(e.getServer()));
    }

    /** Makes the crash site if this world has none yet; returns the wreck. */
    public static Optional<TardisRecord> place(MinecraftServer server) {
        TardisManager manager = TardisManager.get(server);
        Tuning.CrashSite t = TardisContent.tuning().crashSite();
        if (manager.crashSitePlaced() || !t.enabled()) {
            return Optional.empty();
        }
        ServerLevel level = server.getLevel(Providers.startDimension(server));
        if (level == null) {
            level = server.overworld();
        }
        BlockPos spawn = level.getSharedSpawnPos().east(t.offset());
        BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn);
        Tuning.Hop hop = TardisContent.tuning().hop();
        BlockPos spot = Landing.find(level, top, hop.landingRadius(), hop.landingHeight()).orElse(top);
        TardisRecord wreck = manager.create(server, t.name(), Optional.empty());
        if (t.startCharge() > 0) {
            wreck.addCharge(t.startCharge());
        }
        Exteriors.place(level, spot, Direction.WEST, wreck.id());
        wreck.setLocation(Optional.of(GlobalPos.of(level.dimension(), spot)));
        manager.markCrashSitePlaced();
        CompoundTag tag = new CompoundTag();
        tag.putUUID("tardis", wreck.id());
        HistoryLog.get(server).append(new HistoryEntry(server.overworld().getGameTime(), HISTORY, level.dimension(),
                Optional.of(spot), tag));
        TardisRB.LOG.info("[crash site] the wreck landed at {} in {}", spot, level.dimension().location());
        return Optional.of(wreck);
    }
}
