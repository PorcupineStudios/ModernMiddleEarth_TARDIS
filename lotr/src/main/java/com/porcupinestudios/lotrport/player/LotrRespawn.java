package com.porcupinestudios.lotrport.player;

import com.porcupinestudios.lotrport.LotrConfig;
import com.porcupinestudios.lotrport.world.LotrDimensions;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * LOTR's respawning after a death in Middle-earth: at your bed if it is within {@code respawn.bedRange} blocks of where
 * you died; else at the world spawn if that is within {@code respawn.spawnRange}; else somewhere between
 * {@code respawn.randomMin} and {@code respawn.randomMax} blocks from where you died. Vanilla respawns the player first
 * (at the bed or the world spawn); this then moves them if LOTR's rule says otherwise.
 */
public final class LotrRespawn {
    /** Columns tried for a dry random respawn spot (each may generate a chunk) before taking the last one. */
    private static final int TRIES = 5;

    public enum Where { BED, SPAWN, RANDOM }

    private LotrRespawn() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent e) -> {
            if (!e.isEndConquered() && e.getEntity() instanceof ServerPlayer player) {
                respawned(player);
            }
        });
    }

    /** LOTR's choice, from where the player died, their bed in Middle-earth (null if none) and the world spawn. */
    public static Where choose(BlockPos death, @Nullable BlockPos bed, BlockPos spawn) {
        if (bed != null && flat(death, bed) <= LotrConfig.RESPAWN_BED_RANGE.get()) {
            return Where.BED;
        }
        return flat(death, spawn) <= LotrConfig.RESPAWN_SPAWN_RANGE.get() ? Where.SPAWN : Where.RANDOM;
    }

    private static void respawned(ServerPlayer player) {
        ServerLevel me = LotrDimensions.middleEarth(player.server);
        Optional<GlobalPos> died = player.getLastDeathLocation();
        if (!LotrConfig.LOTR_RESPAWN.get() || me == null || died.isEmpty()
                || !died.get().dimension().equals(me.dimension())) {
            return;
        }
        BlockPos death = died.get().pos();
        // vanilla clears a bed that is gone or blocked, so a bed still set here is where vanilla put the player
        BlockPos bed = me.dimension().equals(player.getRespawnDimension()) && player.level() == me
                ? player.getRespawnPosition() : null;
        BlockPos to = switch (choose(death, bed, me.getSharedSpawnPos())) {
            case BED -> null; // vanilla already did
            case SPAWN -> LotrDimensions.surface(me, me.getSharedSpawnPos().getX(), me.getSharedSpawnPos().getZ());
            case RANDOM -> randomNear(me, death, player.getRandom());
        };
        if (to != null) {
            player.teleportTo(me, to.getX() + 0.5, to.getY(), to.getZ() + 0.5, player.getYRot(), player.getXRot());
        }
    }

    /** A spot on dry ground {@code randomMin} to {@code randomMax} blocks from {@code death}, if one turns up. */
    public static BlockPos randomNear(ServerLevel level, BlockPos death, RandomSource random) {
        int min = LotrConfig.RESPAWN_RANDOM_MIN.get();
        int max = Math.max(min, LotrConfig.RESPAWN_RANDOM_MAX.get());
        BlockPos at = death;
        for (int i = 0; i < TRIES; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = min + random.nextDouble() * (max - min);
            at = LotrDimensions.surface(level, (int) Math.floor(death.getX() + Math.cos(angle) * distance),
                    (int) Math.floor(death.getZ() + Math.sin(angle) * distance));
            if (level.getFluidState(at.below()).isEmpty() && level.getFluidState(at).isEmpty()) {
                break;
            }
        }
        return at;
    }

    private static double flat(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }
}
