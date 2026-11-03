package com.porcupinestudios.tardisrb.gametest;

import com.porcupinestudios.tardisrb.TardisRB;
import com.porcupinestudios.tardisrb.block.ExteriorBlock;
import com.porcupinestudios.tardisrb.block.TardisBlocks;
import com.porcupinestudios.tardisrb.core.TardisManager;
import com.porcupinestudios.tardisrb.core.TardisRecord;
import com.porcupinestudios.tardisrb.flight.CrashSite;
import com.porcupinestudios.tardisrb.interior.Interiors;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * A real dedicated-server world (CI, {@code -Dtardis_rb.worldcheck=true}): the crash site exists with its police box,
 * and its interior is built in the interior dimension, which the game test server doesn't make. Logs
 * "TARDIS WORLDCHECK PASSED" or the problems, then stops the server.
 */
public final class WorldCheck {
    private WorldCheck() {}

    public static void register() {
        if (Boolean.getBoolean("tardis_rb.worldcheck")) {
            NeoForge.EVENT_BUS.addListener((ServerStartedEvent e) -> run(e.getServer()));
        }
    }

    private static void run(MinecraftServer server) {
        List<String> problems = new ArrayList<>();
        CrashSite.place(server);
        TardisRecord wreck = TardisManager.get(server).all().stream().filter(r -> r.owner().isEmpty()).findFirst()
                .orElse(null);
        if (wreck == null || wreck.location().isEmpty()) {
            problems.add("no crash site");
        } else {
            GlobalPos at = wreck.location().get();
            ServerLevel level = server.getLevel(at.dimension());
            TardisRB.LOG.info("[worldcheck] the wreck stands at {} in {}", at.pos(), at.dimension().location());
            if (level == null || ExteriorBlock.tardisAt(level, at.pos()).isEmpty()) {
                problems.add("no police box at the crash site");
            }
            if (!Interiors.ensure(server, wreck)) {
                problems.add("interior not built");
            } else {
                ServerLevel inside = Interiors.level(server).orElseThrow();
                BlockPos console = Interiors.origin(wreck.plot()).offset(4, 1, 5);
                if (!inside.getBlockState(console).is(TardisBlocks.CONSOLE.get())) {
                    problems.add("no console at " + console + ": " + inside.getBlockState(console));
                }
                if (Interiors.at(inside, console).map(r -> r != wreck).orElse(true)) {
                    problems.add("the console doesn't find its TARDIS");
                }
            }
        }
        if (problems.isEmpty()) {
            TardisRB.LOG.info("TARDIS WORLDCHECK PASSED");
        } else {
            problems.forEach(p -> TardisRB.LOG.error("[worldcheck] {}", p));
            TardisRB.LOG.error("TARDIS WORLDCHECK FAILED");
        }
        server.halt(false);
    }
}
