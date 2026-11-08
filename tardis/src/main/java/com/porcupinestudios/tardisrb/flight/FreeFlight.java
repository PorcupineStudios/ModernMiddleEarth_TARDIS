package com.porcupinestudios.tardisrb.flight;

import com.porcupinestudios.tardisrb.TardisRB;
import com.porcupinestudios.tardisrb.block.ExteriorBlock;
import com.porcupinestudios.tardisrb.block.Exteriors;
import com.porcupinestudios.tardisrb.core.TardisManager;
import com.porcupinestudios.tardisrb.core.TardisRecord;
import com.porcupinestudios.tardisrb.door.Doors;
import com.porcupinestudios.tardisrb.entity.TardisBody;
import com.porcupinestudios.tardisrb.entity.TardisEntities;
import com.porcupinestudios.tardisrb.look.Looks;
import com.porcupinestudios.tardisrb.progression.TardisContent;
import com.porcupinestudios.tardisrb.progression.Tuning;
import com.porcupinestudios.tardisrb.progression.Unlocks;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The exterior's states besides standing as a block (docs/free-flight-plan.md, Aaron 2026-11-08: one control turns
 * the exterior into an entity, one switches the thrusters on): {@link #phase} turns the block into a loose
 * {@link TardisBody} and back; {@link #setThrusters} makes it hover or fall. The record keeps where it is
 * ({@link TardisRecord#location}, updated every second while loose) and, in module data {@value #MODULE}, the body's
 * id and the thrusters' setting. While loose the doors stay shut. A jump or hop while loose takes the body away and
 * lands as a block ({@link #dock}).
 */
public final class FreeFlight {
    public static final String MODULE = "free_flight";
    /** Feature: turning the exterior into an entity. */
    public static final ResourceLocation EXTERIOR_PHASE = TardisRB.id("exterior_phase");
    /** Feature: the thrusters (anti-grav). */
    public static final ResourceLocation THRUSTERS = TardisRB.id("thrusters");

    private FreeFlight() {}

    /** True while the exterior is a loose body, not a block. */
    public static boolean isLoose(TardisRecord tardis) {
        return tardis.moduleData(MODULE).hasUUID("body");
    }

    /** Whether the thrusters are set on (they act only while loose). */
    public static boolean thrusters(TardisRecord tardis) {
        return tardis.moduleData(MODULE).getBoolean("thrusters");
    }

    /** The loose body, if it is loose and its chunk is loaded. */
    public static Optional<TardisBody> body(MinecraftServer server, TardisRecord tardis) {
        if (!isLoose(tardis) || tardis.location().isEmpty()) {
            return Optional.empty();
        }
        ServerLevel level = server.getLevel(tardis.location().get().dimension());
        Entity e = level == null ? null : level.getEntity(tardis.moduleData(MODULE).getUUID("body"));
        return e instanceof TardisBody b && !b.isRemoved() ? Optional.of(b) : Optional.empty();
    }

    /** Block to loose body, or loose body back to a block on the ground under it. Says what happened. */
    public static Component phase(MinecraftServer server, TardisRecord tardis, Optional<ServerPlayer> by) {
        return isLoose(tardis) ? toBlock(server, tardis) : toBody(server, tardis, by);
    }

    private static Component toBody(MinecraftServer server, TardisRecord tardis, Optional<ServerPlayer> by) {
        Unlocks.Result unlocked = Unlocks.check(server, tardis, EXTERIOR_PHASE, by);
        if (!unlocked.allowed()) {
            return unlocked.reason();
        }
        Optional<GlobalPos> at = tardis.location();
        ServerLevel level = at.map(g -> server.getLevel(g.dimension())).orElse(null);
        if (level == null || !(level.getBlockState(at.get().pos()).getBlock() instanceof ExteriorBlock)) {
            return Component.translatableWithFallback("tardis_rb.free_flight.not_standing",
                    "The TARDIS isn't standing anywhere");
        }
        BlockPos pos = at.get().pos();
        Doors.close(server, tardis);
        Direction facing = Exteriors.lift(level, pos);
        TardisBody body = TardisEntities.BODY.get().create(level);
        if (body == null) {
            Exteriors.place(level, pos, facing, tardis.id());
            return Component.translatableWithFallback("tardis_rb.free_flight.failed", "The box stays put");
        }
        body.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, facing.toYRot(), 0);
        body.setTardis(tardis.id());
        body.setThrusters(thrusters(tardis));
        body.setLook(Looks.exterior(server, tardis));
        level.addFreshEntity(body);
        CompoundTag tag = tardis.moduleData(MODULE);
        tag.putUUID("body", body.getUUID());
        tardis.setModuleData(MODULE, tag);
        return thrusters(tardis)
                ? Component.translatableWithFallback("tardis_rb.free_flight.loose_hover",
                        "The box comes loose and hovers on its thrusters")
                : Component.translatableWithFallback("tardis_rb.free_flight.loose",
                        "The box comes loose: it is a physics object now");
    }

    private static Component toBlock(MinecraftServer server, TardisRecord tardis) {
        Optional<TardisBody> body = body(server, tardis);
        GlobalPos last = tardis.location().orElse(null);
        ServerLevel level = body.map(b -> (ServerLevel) b.level())
                .orElseGet(() -> last == null ? null : server.getLevel(last.dimension()));
        if (level == null) {
            dock(server, tardis);
            return Component.translatableWithFallback("tardis_rb.free_flight.lost", "The box is lost");
        }
        BlockPos near = body.map(Entity::blockPosition).orElse(last.pos());
        Tuning.FreeFlight t = TardisContent.tuning().flight();
        Optional<BlockPos> spot = Landing.find(level, near, t.landingRadius(), t.landingHeight());
        if (spot.isEmpty()) {
            return Component.translatableWithFallback("tardis_rb.free_flight.no_ground",
                    "Nowhere to set down here: bring the box nearer the ground");
        }
        Direction facing = body.map(b -> Direction.fromYRot(b.getYRot())).orElse(Direction.NORTH);
        dock(server, tardis);
        Exteriors.place(level, spot.get(), facing, tardis.id());
        tardis.setLocation(Optional.of(GlobalPos.of(level.dimension(), spot.get())));
        return Component.translatableWithFallback("tardis_rb.free_flight.landed", "The box sets down as a block");
    }

    /** Thrusters on (it hovers) or off (it falls). Kept while landed, for the next time it comes loose. */
    public static Component setThrusters(MinecraftServer server, TardisRecord tardis, boolean on,
            Optional<ServerPlayer> by) {
        if (on) {
            Unlocks.Result unlocked = Unlocks.check(server, tardis, THRUSTERS, by);
            if (!unlocked.allowed()) {
                return unlocked.reason();
            }
        }
        CompoundTag tag = tardis.moduleData(MODULE);
        tag.putBoolean("thrusters", on);
        tardis.setModuleData(MODULE, tag);
        body(server, tardis).ifPresent(b -> {
            b.setThrusters(on);
            if (!on) {
                b.ejectPassengers(); // no anti-grav: it falls, and the pilot goes back inside
            }
        });
        return on ? Component.translatableWithFallback("tardis_rb.free_flight.thrusters_on", "Thrusters on")
                : Component.translatableWithFallback("tardis_rb.free_flight.thrusters_off", "Thrusters off");
    }

    /** Takes the loose body away (a jump or hop is about to land the TARDIS as a block somewhere). */
    public static void dock(MinecraftServer server, TardisRecord tardis) {
        if (!isLoose(tardis)) {
            return;
        }
        body(server, tardis).ifPresent(b -> {
            b.ejectPassengers();
            b.discard();
        });
        CompoundTag tag = tardis.moduleData(MODULE);
        tag.remove("body");
        tardis.setModuleData(MODULE, tag);
    }

    /** The way the loose body faces (north if it can't be found). */
    public static Direction facing(MinecraftServer server, TardisRecord tardis) {
        return body(server, tardis).map(b -> Direction.fromYRot(b.getYRot())).orElse(Direction.NORTH);
    }

    /**
     * Called by a body every second on the server: keeps its record's location on it, and removes a body that is no
     * longer its TARDIS's (a stale copy, say after the box landed elsewhere).
     */
    public static void track(TardisBody body) {
        MinecraftServer server = body.getServer();
        Optional<UUID> id = body.tardis();
        Optional<TardisRecord> tardis = server == null || id.isEmpty() ? Optional.empty()
                : TardisManager.get(server).get(id.get());
        if (tardis.isEmpty() || !isLoose(tardis.get())
                || !tardis.get().moduleData(MODULE).getUUID("body").equals(body.getUUID())) {
            body.discard();
            return;
        }
        GlobalPos now = GlobalPos.of(body.level().dimension(), body.blockPosition());
        if (!tardis.get().location().equals(Optional.of(now))) {
            tardis.get().setLocation(Optional.of(now));
        }
    }

    /** How high the loose body is over the ground under it, in blocks (0 if not loose or not found). */
    public static int height(MinecraftServer server, TardisRecord tardis) {
        return body(server, tardis).map(b -> Math.max(0, b.blockPosition().getY() - b.level().getHeight(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, b.getBlockX(),
                b.getBlockZ()))).orElse(0);
    }
}
