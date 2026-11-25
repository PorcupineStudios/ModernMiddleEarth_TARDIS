package com.porcupinestudios.tardisrb.gametest;

import com.porcupinestudios.tardisrb.TardisRB;
import com.porcupinestudios.tardisrb.core.TardisRecord;
import com.porcupinestudios.tardisrb.repair.Repairs;
import java.util.Optional;
import net.minecraft.server.MinecraftServer;

/** For tests: navigation repaired to 1 and both flight upgrades bought, so flying and its controls are unlocked. */
final class FlightReady {
    private FlightReady() {}

    static boolean navigation(MinecraftServer server, TardisRecord t) {
        boolean fixed = Repairs.attempt(server, t, TardisRB.id("navigation_1"), Optional.empty()).done();
        t.completeResearch(TardisRB.id("materialised_flight"));
        t.completeResearch(TardisRB.id("dematerialised_flight"));
        return fixed;
    }
}
