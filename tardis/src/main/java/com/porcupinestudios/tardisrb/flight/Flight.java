package com.porcupinestudios.tardisrb.flight;

import com.porcupinestudios.ringbearer.api.history.HistoryEntry;
import com.porcupinestudios.ringbearer.api.history.HistoryLog;
import com.porcupinestudios.tardisrb.TardisRB;
import com.porcupinestudios.tardisrb.block.Exteriors;
import com.porcupinestudios.tardisrb.core.TardisRecord;
import com.porcupinestudios.tardisrb.event.JumpEvent;
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
 * feature {@code tardis_rb:short_hop} must be unlocked. Players inside travel with it, since the interior is
 * elsewhere.
 */
public final class Flight {
    public static final ResourceLocation SHORT_HOP = TardisRB.id("short_hop");
    public static final ResourceLocation HOP_HISTORY = TardisRB.id("hop");

    /** {@code problem} is empty when the TARDIS landed at {@code landed}. */
    public record Outcome(Optional<Component> problem, Optional<GlobalPos> landed) {
        public boolean done() {
            return problem.isEmpty();
        }

        static Outcome fail(String key, String fallback, Object... args) {
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
        Tuning.Hop t = TardisContent.tuning().hop();
        Direction facing = Exteriors.lift(level, from.pos()); // so the old spot doesn't block the search
        Optional<BlockPos> spot = Landing.find(level, to.pos(), t.landingRadius(), t.landingHeight());
        if (spot.isEmpty()) {
            Exteriors.place(level, from.pos(), facing, tardis.id());
            return Outcome.fail("tardis_rb.hop.no_space", "There is nowhere to land near there");
        }
        JumpEvent.Land land = NeoForge.EVENT_BUS.post(new JumpEvent.Land(tardis, from,
                GlobalPos.of(from.dimension(), spot.get())));
        GlobalPos landed = land.spot();
        ServerLevel landLevel = server.getLevel(landed.dimension());
        if (landLevel == null) {
            landLevel = level;
            landed = GlobalPos.of(from.dimension(), spot.get());
        }
        tardis.useCharge(cost.charge());
        Exteriors.place(landLevel, landed.pos(), facing, tardis.id());
        tardis.setLocation(Optional.of(landed));
        NeoForge.EVENT_BUS.post(new JumpEvent.Arrived(tardis, from, landed));
        CompoundTag tag = new CompoundTag();
        tag.putUUID("tardis", tardis.id());
        tag.putLong("from", from.pos().asLong());
        tag.putLong("charge", cost.charge());
        HistoryLog.get(server).append(new HistoryEntry(server.overworld().getGameTime(), HOP_HISTORY,
                landed.dimension(), Optional.of(landed.pos()), tag));
        return new Outcome(Optional.empty(), Optional.of(landed));
    }
}
