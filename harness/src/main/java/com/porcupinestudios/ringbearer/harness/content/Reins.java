package com.porcupinestudios.ringbearer.harness.content;

import com.porcupinestudios.ringbearer.harness.HarnessMod;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

/**
 * Driving from the vehicle (Aaron 2026-11-25: steer the animals without riding them). Use the reins on a hitch to take
 * them up (again, or with them on nothing, to let go): while you hold the reins within {@link #REACH} blocks of it,
 * your movement keys drive its team's leader instead of you (W go, with sprint a canter; S back; A/D turn), and the
 * rest of the team follows the leader ({@link Teams}). The player's attachment {@code driving} (synced, not saved) is
 * the hitch held; the client sends its keys every tick ({@link DriveInput}); keys older than {@link #STALE} ticks
 * count as none, so the team slows to a stop.
 */
public final class Reins {
    public static final double REACH = 24;
    static final long STALE = 10;

    record Key(ResourceKey<Level> level, BlockPos hitch) {}

    /** The latest keys for a hitch. */
    public record Input(float forward, float turn, boolean sprint, long time) {}

    private static final Map<Key, Input> INPUTS = new ConcurrentHashMap<>();

    private Reins() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(Reins::check);
    }

    public static Optional<BlockPos> driving(Player p) {
        return p.getData(HarnessMod.DRIVING);
    }

    /** Takes up the reins of {@code hitch}, or lets them go if they're already held. Server side. */
    public static void toggle(Player p, HitchBlockEntity hitch) {
        if (driving(p).map(hitch.getBlockPos()::equals).orElse(false)) {
            letGo(p);
            return;
        }
        if (!hitch.hasHitches()) {
            p.displayClientMessage(Component.translatable("message.ringbearer_harness.reins_nothing"), true);
            return;
        }
        p.setData(HarnessMod.DRIVING, Optional.of(hitch.getBlockPos().immutable()));
        p.displayClientMessage(Component.translatable("message.ringbearer_harness.reins_taken"), true);
        p.level().playSound(null, p, SoundEvents.LEASH_KNOT_PLACE, SoundSource.PLAYERS, 0.7f, 1.2f);
    }

    public static void letGo(Player p) {
        driving(p).ifPresent(pos -> {
            INPUTS.remove(new Key(p.level().dimension(), pos));
            p.setData(HarnessMod.DRIVING, Optional.empty());
            p.displayClientMessage(Component.translatable("message.ringbearer_harness.reins_dropped"), true);
        });
    }

    static boolean holdsReins(Player p) {
        return p.getMainHandItem().is(HarnessMod.REINS.get()) || p.getOffhandItem().is(HarnessMod.REINS.get());
    }

    /** A driver's keys arrived (network thread: queued onto the server). */
    public static void receive(DriveInput msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer p && holdsReins(p)) {
                driving(p).ifPresent(pos -> set(p.level(), pos, msg.forward(), msg.turn(), msg.sprint()));
            }
        });
    }

    /** Sets a hitch's keys now (the network, and GameTests driving a team without a player). */
    public static void set(Level level, BlockPos hitch, float forward, float turn, boolean sprint) {
        INPUTS.put(new Key(level.dimension(), hitch.immutable()), new Input(Mth.clamp(forward, -1, 1),
                Mth.clamp(turn, -1, 1), sprint, level.getGameTime()));
    }

    /** The hitch's keys, if fresh. */
    @Nullable
    public static Input input(Level level, BlockPos hitch) {
        Input i = INPUTS.get(new Key(level.dimension(), hitch));
        return i == null || level.getGameTime() - i.time() > STALE ? null : i;
    }

    /** Lets go of reins no longer held: put away, too far from the hitch, or the hitch gone or empty. */
    private static void check(PlayerTickEvent.Post event) {
        Player p = event.getEntity();
        if (p.level().isClientSide || driving(p).isEmpty()) {
            return;
        }
        BlockPos pos = driving(p).get();
        boolean ok = holdsReins(p) && p.level().getBlockEntity(pos) instanceof HitchBlockEntity hitch
                && hitch.hasHitches() && near(p, hitch);
        if (!ok) {
            letGo(p);
        }
    }

    private static boolean near(Player p, HitchBlockEntity hitch) {
        Vec3 w = hitch.hitchWorld();
        if (w == null) {
            w = Vec3.atCenterOf(hitch.getBlockPos());
        }
        return p.position().distanceToSqr(w) <= REACH * REACH;
    }
}
