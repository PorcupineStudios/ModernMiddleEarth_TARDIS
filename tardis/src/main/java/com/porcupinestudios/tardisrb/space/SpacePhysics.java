package com.porcupinestudios.tardisrb.space;

import com.porcupinestudios.tardisrb.progression.TardisContent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Runs {@link Orbits#step} on the server for everything in space except players (their movement is the client's:
 * {@code client/ClientSpacePhysics}).
 */
public final class SpacePhysics {
    private SpacePhysics() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener(SpacePhysics::onTick);
    }

    private static void onTick(EntityTickEvent.Post event) {
        Entity e = event.getEntity();
        if (e instanceof Player || !(e.level() instanceof ServerLevel level) || !Orbits.moves(e)) {
            return;
        }
        MinecraftServer server = level.getServer();
        if (Systems.isSystem(server, level.dimension())) {
            Systems.near(Systems.all(server), level.dimension(), e.position())
                    .ifPresent(s -> Orbits.stepSystem(e, s, TardisContent.tuning().space(), 0));
            return;
        }
        if (!Planets.isSpace(server, level.dimension())) {
            return;
        }
        Planets.under(server, level.dimension(), e.getX(), e.getZ())
                .ifPresent(p -> Orbits.step(e, p, TardisContent.tuning().space(), 0));
    }
}
