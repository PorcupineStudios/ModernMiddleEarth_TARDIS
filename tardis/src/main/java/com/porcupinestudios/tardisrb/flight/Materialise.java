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
 * {@code hop.materialise_ticks}. Doors left open stay open on the fading box ({@link Doors#takeOff}), its doorway still
 * showing the console room, never what is behind it, until the fade ends and the doorway moves to where the TARDIS is.
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

    /** Runs {@code run} {@code ticks} server ticks from now. Not saved: a restart drops it. */
    public static void later(MinecraftServer server, int ticks, Runnable run) {
        LATER.add(new Later(server.getTickCount() + ticks, run));
    }

    /** Leaves a fading box where the TARDIS stood, its doors open if {@code doorsOpen}. */
    public static void fadeOut(ServerLevel level, BlockPos base, Direction facing, TardisRecord tardis,
            boolean doorsOpen) {
        Dematerialising ghost = TardisEntities.DEMATERIALISING.get().create(level);
        if (ghost == null) {
            return;
        }
        ghost.setup(Looks.exterior(level.getServer(), tardis), ticks(), doorsOpen);
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
