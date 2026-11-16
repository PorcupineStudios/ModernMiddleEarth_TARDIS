package com.porcupinestudios.tardisrb.flight;

import com.porcupinestudios.tardisrb.block.ExteriorBlockEntity;
import com.porcupinestudios.tardisrb.core.TardisRecord;
import com.porcupinestudios.tardisrb.door.Doors;
import com.porcupinestudios.tardisrb.entity.Dematerialising;
import com.porcupinestudios.tardisrb.entity.TardisEntities;
import com.porcupinestudios.tardisrb.look.Looks;
import com.porcupinestudios.tardisrb.progression.TardisContent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The police box (de)materialising (Aaron 2026-11-16): it fades out where it stood (a {@link Dematerialising} ghost)
 * and fades in where it lands (the block entity's fade-in), with the show's pulsing fade over the tuning's
 * {@code hop.materialise_ticks}. Taking off with the doors open first swings them shut ({@link #afterDoorsShut}): the
 * doors close over the portal, so the doorway never shows what is behind it.
 */
public final class Materialise {
    private record Later(long due, Runnable run) {}

    private static final List<Later> LATER = new ArrayList<>();

    private Materialise() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> {
            long now = e.getServer().getTickCount();
            List<Later> due = new ArrayList<>();
            LATER.removeIf(l -> l.due() <= now && due.add(l));
            due.forEach(l -> l.run().run());
        });
    }

    public static int ticks() {
        return TardisContent.tuning().hop().materialiseTicks();
    }

    /**
     * Runs {@code flight} now if the doors are shut, else swings them shut and runs it once they are (the doors' own
     * close time, then they're closed for real by {@link Doors}).
     */
    public static void afterDoorsShut(MinecraftServer server, TardisRecord tardis, Runnable flight) {
        if (!Doors.isOpen(tardis)) {
            flight.run();
            return;
        }
        Doors.shut(server, tardis);
        int wait = TardisContent.tuning().door().closeTicks() + 2;
        LATER.add(new Later(server.getTickCount() + wait, flight));
    }

    /** Leaves a fading box where the TARDIS stood. */
    public static void fadeOut(ServerLevel level, BlockPos base, Direction facing, TardisRecord tardis) {
        Dematerialising ghost = TardisEntities.DEMATERIALISING.get().create(level);
        if (ghost == null) {
            return;
        }
        ghost.setup(Looks.exterior(level.getServer(), tardis), ticks());
        ghost.moveTo(base.getX() + 0.5, base.getY(), base.getZ() + 0.5, facing.toYRot(), 0);
        level.addFreshEntity(ghost);
    }

    /** Starts the fade-in of the box just placed at {@code base}. */
    public static void fadeIn(ServerLevel level, BlockPos base) {
        if (level.getBlockEntity(base) instanceof ExteriorBlockEntity be) {
            be.materialise(level.getGameTime(), ticks());
        }
    }

    /**
     * The pulsing fade: 1 = solid. {@code t} 0..1 through it; out fades 1 to 0, in 0 to 1, each breathing three times
     * like the show's.
     */
    public static float alpha(float t, boolean out) {
        float k = out ? t : 1 - t;
        float breathe = 0.65f + 0.35f * (float) Math.cos(k * 6 * Math.PI);
        return Math.max(0, Math.min(1, (out ? 1 - t : t) * breathe));
    }
}
