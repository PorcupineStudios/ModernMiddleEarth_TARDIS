package com.porcupinestudios.tardisrb.flight;

import com.porcupinestudios.ringbearer.api.history.HistoryEntry;
import com.porcupinestudios.ringbearer.api.history.HistoryLog;
import com.porcupinestudios.tardisrb.TardisRB;
import com.porcupinestudios.tardisrb.block.Exteriors;
import com.porcupinestudios.tardisrb.core.TardisRecord;
import com.porcupinestudios.tardisrb.door.Doors;
import com.porcupinestudios.tardisrb.event.JumpEvent;
import com.porcupinestudios.tardisrb.progression.Destination;
import com.porcupinestudios.tardisrb.progression.TardisContent;
import com.porcupinestudios.tardisrb.progression.Tuning;
import com.porcupinestudios.tardisrb.progression.Unlocks;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Short hops: within the dimension the TARDIS stands in, no further than its {@code range} stat, paid in charge. The
 * feature {@code tardis_rb:short_hop} must be unlocked. Jumps: to any open destination, at the destination's charge. Players inside travel with it, since the interior is
 * elsewhere.
 */
public final class Flight {
    public static final ResourceLocation SHORT_HOP = TardisRB.id("short_hop");
    public static final ResourceLocation HOP_HISTORY = TardisRB.id("hop");
    public static final ResourceLocation JUMP_HISTORY = TardisRB.id("jump");

    /** {@code problem} is empty when the TARDIS landed at {@code landed}. */
    public record Outcome(Optional<Component> problem, Optional<GlobalPos> landed) {
        public boolean done() {
            return problem.isEmpty();
        }

        public static Outcome fail(String key, String fallback, Object... args) {
            return new Outcome(Optional.of(Component.translatableWithFallback(key, fallback, args)), Optional.empty());
        }
    }

    private Flight() {}

    /** What a hop to {@code target} would cost, before listeners change it. */
    public static long baseCost(double distance) {
        Tuning.Hop t = TardisContent.tuning().hop();
        return Math.max(t.minCharge(), (long) Math.ceil(distance * t.chargePerBlock()));
    }

    public static Outcome hop(MinecraftServer server, TardisRecord tardis, BlockPos target,
            Optional<ServerPlayer> by) {
        return hop(server, tardis, target, Optional.empty(), by);
    }

    /** A short hop that lands with the doors facing {@code facing} (empty: as before). */
    public static Outcome hop(MinecraftServer server, TardisRecord tardis, BlockPos target, Optional<Direction> facing,
            Optional<ServerPlayer> by) {
        if (tardis.location().isEmpty()) {
            return Outcome.fail("tardis_rb.hop.nowhere", "The TARDIS isn't standing anywhere");
        }
        Unlocks.Result unlocked = Unlocks.check(server, tardis, SHORT_HOP, by);
        if (!unlocked.allowed()) {
            return new Outcome(Optional.of(unlocked.reason()), Optional.empty());
        }
        GlobalPos from = tardis.location().get();
        JumpEvent.Plan plan = NeoForge.EVENT_BUS.post(new JumpEvent.Plan(tardis, from, GlobalPos.of(from.dimension(),
                target)));
        if (plan.isCanceled()) {
            return Outcome.fail("tardis_rb.hop.cancelled", "Something holds the TARDIS in place");
        }
        GlobalPos to = plan.target();
        if (!to.dimension().equals(from.dimension())) {
            return Outcome.fail("tardis_rb.hop.dimension", "Short hops can't leave this dimension");
        }
        ServerLevel level = server.getLevel(from.dimension());
        if (level == null) {
            return Outcome.fail("tardis_rb.hop.nowhere", "The TARDIS isn't standing anywhere");
        }
        double distance = Math.sqrt(from.pos().distSqr(to.pos()));
        double range = Unlocks.stat(tardis, "range");
        if (distance > range) {
            return Outcome.fail("tardis_rb.hop.range", "Too far: %s blocks, the TARDIS can hop %s",
                    (long) Math.ceil(distance), (long) range);
        }
        JumpEvent.Cost cost = NeoForge.EVENT_BUS.post(new JumpEvent.Cost(tardis, from, to, baseCost(distance)));
        if (tardis.charge() < cost.charge()) {
            return Outcome.fail("tardis_rb.hop.charge", "Needs %s charge (the TARDIS has %s)", cost.charge(),
                    tardis.charge());
        }
        return land(server, tardis, from, level, level, to.pos(), cost.charge(), HOP_HISTORY, facing);
    }

    /**
     * A jump to another dimension (or far across this one) to an open {@link Destinations destination}. Lands near
     * {@code target}, or near the dimension's spawn when there is none. Costs the destination's charge.
     */
    public static Outcome jump(MinecraftServer server, TardisRecord tardis, ResourceLocation destinationId,
            Optional<BlockPos> target, Optional<ServerPlayer> by) {
        return jump(server, tardis, destinationId, target, Optional.empty(), by);
    }

    /** A jump that lands with the doors facing {@code facing} (empty: as before). */
    public static Outcome jump(MinecraftServer server, TardisRecord tardis, ResourceLocation destinationId,
            Optional<BlockPos> target, Optional<Direction> facing, Optional<ServerPlayer> by) {
        if (tardis.location().isEmpty()) {
            return Outcome.fail("tardis_rb.hop.nowhere", "The TARDIS isn't standing anywhere");
        }
        Optional<Component> closed = Destinations.closed(server, tardis, destinationId, by);
        if (closed.isPresent()) {
            return new Outcome(closed, Optional.empty());
        }
        Destination dest = TardisContent.destination(destinationId).orElseThrow();
        ServerLevel toLevel = server.getLevel(dest.dimension());
        GlobalPos from = tardis.location().get();
        ServerLevel fromLevel = server.getLevel(from.dimension());
        if (toLevel == null || fromLevel == null) {
            return Outcome.fail("tardis_rb.jump.missing", "That dimension isn't in this world");
        }
        BlockPos aim = target.orElseGet(toLevel::getSharedSpawnPos);
        JumpEvent.Plan plan = NeoForge.EVENT_BUS.post(new JumpEvent.Plan(tardis, from, GlobalPos.of(dest.dimension(),
                aim)));
        if (plan.isCanceled()) {
            return Outcome.fail("tardis_rb.hop.cancelled", "Something holds the TARDIS in place");
        }
        GlobalPos to = plan.target();
        ServerLevel planned = server.getLevel(to.dimension());
        if (planned == null) {
            return Outcome.fail("tardis_rb.jump.missing", "That dimension isn't in this world");
        }
        JumpEvent.Cost cost = NeoForge.EVENT_BUS.post(new JumpEvent.Cost(tardis, from, to, dest.charge()));
        if (tardis.charge() < cost.charge()) {
            return Outcome.fail("tardis_rb.hop.charge", "Needs %s charge (the TARDIS has %s)", cost.charge(),
                    tardis.charge());
        }
        return land(server, tardis, from, fromLevel, planned, to.pos(), cost.charge(), JUMP_HISTORY, facing);
    }

    /** Lifts the box, finds a spot near {@code target} in {@code toLevel}, pays and lands; puts it back on failure. */
    private static Outcome land(MinecraftServer server, TardisRecord tardis, GlobalPos from, ServerLevel level,
            ServerLevel toLevel, BlockPos target, long charge, ResourceLocation history, Optional<Direction> turn) {
        Tuning.Hop t = TardisContent.tuning().hop();
        Doors.close(server, tardis); // the doors shut before it moves
        Direction facing = Exteriors.lift(level, from.pos()); // so the old spot doesn't block the search
        Optional<BlockPos> spot = Landing.find(toLevel, target, t.landingRadius(), t.landingHeight());
        if (spot.isEmpty()) {
            Exteriors.place(level, from.pos(), facing, tardis.id());
            return Outcome.fail("tardis_rb.hop.no_space", "There is nowhere to land near there");
        }
        JumpEvent.Land land = NeoForge.EVENT_BUS.post(new JumpEvent.Land(tardis, from,
                GlobalPos.of(toLevel.dimension(), spot.get())));
        GlobalPos landed = land.spot();
        ServerLevel landLevel = server.getLevel(landed.dimension());
        if (landLevel == null) {
            landLevel = toLevel;
            landed = GlobalPos.of(toLevel.dimension(), spot.get());
        }
        tardis.useCharge(charge);
        Exteriors.place(landLevel, landed.pos(), turn.orElse(facing), tardis.id());
        tardis.setLocation(Optional.of(landed));
        NeoForge.EVENT_BUS.post(new JumpEvent.Arrived(tardis, from, landed));
        CompoundTag tag = new CompoundTag();
        tag.putUUID("tardis", tardis.id());
        tag.putString("from_dimension", from.dimension().location().toString());
        tag.putLong("from", from.pos().asLong());
        tag.putLong("charge", charge);
        HistoryLog.get(server).append(new HistoryEntry(server.overworld().getGameTime(), history,
                landed.dimension(), Optional.of(landed.pos()), tag));
        return new Outcome(Optional.empty(), Optional.of(landed));
    }
}
