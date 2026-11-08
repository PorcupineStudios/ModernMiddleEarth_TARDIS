package com.porcupinestudios.tardisrb.door;

import com.porcupinestudios.tardisrb.TardisRB;
import com.porcupinestudios.tardisrb.block.Exteriors;
import com.porcupinestudios.tardisrb.core.TardisManager;
import com.porcupinestudios.tardisrb.core.TardisRecord;
import com.porcupinestudios.tardisrb.flight.FreeFlight;
import com.porcupinestudios.tardisrb.interior.Interiors;
import com.porcupinestudios.tardisrb.progression.TardisContent;
import com.porcupinestudios.tardisrb.progression.Tuning;
import com.porcupinestudios.tardisrb.block.ExteriorBlock;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import qouteall.imm_ptl.core.api.PortalAPI;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.q_misc_util.my_util.DQuaternion;

/**
 * The see-through TARDIS door (Aaron, 2026-11-03), built on Immersive Portals (a required mod): an open door is a pair
 * of portals, one in the police box's doorway looking into the console room and its reverse in the interior doorway
 * looking out. The door blocks turn into open shells ({@code open=true}) so the portals sit in line with the doorway. You see the other side live and walk through with no loading screen. Closing the door,
 * or the TARDIS flying off, removes them. The open state and portal ids are kept in the record's module data.
 */
public final class Doors {
    public static final String MODULE = "tardis_rb:door";
    public static final String TAG = "tardis_rb:door";

    private Doors() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(Doors::onJoin);
        NeoForge.EVENT_BUS.addListener(Doors::onTick);
    }

    /** Doors that have finished swinging shut lose their portals. */
    private static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long now = server.overworld().getGameTime();
        for (TardisRecord tardis : TardisManager.get(server).all()) {
            CompoundTag tag = tardis.moduleData(MODULE);
            if (tag.contains("closing_at") && now >= tag.getLong("closing_at")) {
                close(server, tardis);
            }
        }
    }

    /** A door portal loading from disk whose TARDIS no longer has it open (closed while unloaded) is dropped. */
    private static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !event.loadedFromDisk() || !(event.getEntity() instanceof Portal p) || !TAG.equals(p.portalTag)
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        UUID id = p.getUUID();
        boolean listed = TardisManager.get(level.getServer()).all().stream().map(t -> t.moduleData(MODULE))
                .anyMatch(tag -> tag.getBoolean("open") && ((tag.hasUUID("outside") && tag.getUUID("outside")
                        .equals(id)) || (tag.hasUUID("inside") && tag.getUUID("inside").equals(id))));
        if (!listed) {
            event.setCanceled(true);
        }
    }

    public static boolean isOpen(TardisRecord tardis) {
        return tardis.moduleData(MODULE).getBoolean("open");
    }

    /** The doors are swinging shut; the portals stay until they are closed. */
    public static boolean isClosing(TardisRecord tardis) {
        return tardis.moduleData(MODULE).contains("closing_at");
    }

    /** What a player who used the doors is told: {@code closing} if they started to shut. */
    public static Component message(boolean closing) {
        return closing
                ? Component.translatableWithFallback("tardis_rb.door.closed", "The doors close")
                : Component.translatableWithFallback("tardis_rb.door.opened", "The doors swing open");
    }

    /** One side of the doorway: where the portal plane's centre is, and the way the door faces (towards the viewer). */
    public record Side(Vec3 center, Direction facing) {}

    /** The outside opening, in front of the police box's doors. */
    public static Side outside(BlockPos base, Direction facing) {
        Tuning.DoorTuning t = TardisContent.tuning().door();
        Vec3 c = Vec3.atBottomCenterOf(base).add(0, t.bottom() + t.height() / 2, 0)
                .add(Vec3.atLowerCornerOf(facing.getNormal()).scale(0.5 + t.offset()));
        return new Side(c, facing);
    }

    /** The inside opening, in front of the interior door, facing into the console room. */
    public static Side inside(TardisRecord tardis) {
        Tuning.DoorTuning t = TardisContent.tuning().door();
        BlockPos door = interiorDoor(tardis);
        Vec3 c = Vec3.atBottomCenterOf(door).add(0, t.bottom() + t.height() / 2, 0)
                .add(Vec3.atLowerCornerOf(t.interiorFacing().getNormal()).scale(0.5 + t.offset()));
        return new Side(c, t.interiorFacing());
    }

    /** The in-plane width axis for a portal whose visible side faces {@code facing} (width x up = facing). */
    public static Vec3 axisW(Direction facing) {
        return new Vec3(facing.getStepZ(), 0, -facing.getStepX());
    }

    /**
     * The turn that carries the view through the doorway: looking into the first opening (against {@code from}, its
     * facing) comes out looking into the room beyond the second (along {@code to}, its facing).
     */
    public static DQuaternion rotation(Direction from, Direction to) {
        double a = Math.atan2(-from.getStepX(), -from.getStepZ());
        double b = Math.atan2(to.getStepX(), to.getStepZ());
        return DQuaternion.rotationByRadians(new Vec3(0, 1, 0), b - a);
    }

    /** Opens the doors: builds the interior if needed and puts up the two portals. Returns false if it can't. */
    public static boolean open(MinecraftServer server, TardisRecord tardis) {
        if (isOpen(tardis)) {
            return true;
        }
        Optional<GlobalPos> where = tardis.location();
        // a loose box keeps its doors shut until it is a block again (docs/free-flight-plan.md)
        if (where.isEmpty() || FreeFlight.isLoose(tardis) || !Interiors.ensure(server, tardis)) {
            return false;
        }
        ServerLevel world = server.getLevel(where.get().dimension());
        Optional<ServerLevel> interior = Interiors.level(server);
        if (world == null || interior.isEmpty()) {
            return false;
        }
        Side out = outside(where.get().pos(), Exteriors.facing(world, where.get().pos()));
        Optional<Portal[]> pair = link(world, out, interior.get(), inside(tardis));
        if (pair.isEmpty()) {
            return false;
        }
        Portal portal = pair.get()[0];
        Portal back = pair.get()[1];
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("open", true);
        tag.putUUID("outside", portal.getUUID());
        tag.putUUID("inside", back.getUUID());
        tardis.setModuleData(MODULE, tag);
        setOpen(world, where.get().pos(), true);
        setOpen(interior.get(), interiorDoor(tardis), true);
        TardisRB.LOG.debug("[door] {} opened at {}", tardis.name(), out.center());
        return true;
    }

    /**
     * Puts up a two-way doorway: a portal at {@code a} showing {@code b}, and its reverse at {@code b} showing
     * {@code a}, both sized from the door tuning. Returns the two portals (a's first).
     */
    public static Optional<Portal[]> link(ServerLevel aLevel, Side a, ServerLevel bLevel, Side b) {
        Tuning.DoorTuning t = TardisContent.tuning().door();
        Portal portal = Portal.ENTITY_TYPE.create(aLevel);
        if (portal == null) {
            return Optional.empty();
        }
        portal.setOriginPos(a.center());
        portal.setOrientationAndSize(axisW(a.facing()), new Vec3(0, 1, 0), t.width(), t.height());
        PortalAPI.setPortalTransformation(portal, bLevel.dimension(), b.center(), rotation(a.facing(), b.facing()),
                1.0);
        portal.portalTag = TAG;
        PortalAPI.spawnServerEntity(portal);
        Portal back = PortalAPI.createReversePortal(portal);
        back.portalTag = TAG;
        PortalAPI.spawnServerEntity(back);
        return Optional.of(new Portal[] {portal, back});
    }

    /**
     * Starts shutting the doors (Aaron, 2026-11-04): the doors swing shut, and only once they are closed
     * ({@code door.close_ticks} later) do the portals go, so the view through the doorway lasts as long as the gap.
     * With no swing time the doors close at once.
     */
    public static void shut(MinecraftServer server, TardisRecord tardis) {
        CompoundTag tag = tardis.moduleData(MODULE);
        if (!tag.getBoolean("open") || tag.contains("closing_at")) {
            return;
        }
        int ticks = TardisContent.tuning().door().closeTicks();
        if (ticks <= 0) {
            close(server, tardis);
            return;
        }
        tag.putLong("closing_at", server.overworld().getGameTime() + ticks);
        tardis.setModuleData(MODULE, tag);
        exterior(server, tardis).ifPresent(l -> setClosing(l, tardis.location().get().pos(), true));
    }

    /** Stops the doors shutting: they swing open again and the portals stay. */
    static void reopen(MinecraftServer server, TardisRecord tardis) {
        CompoundTag tag = tardis.moduleData(MODULE);
        tag.remove("closing_at");
        tardis.setModuleData(MODULE, tag);
        exterior(server, tardis).ifPresent(l -> setClosing(l, tardis.location().get().pos(), false));
    }

    private static Optional<ServerLevel> exterior(MinecraftServer server, TardisRecord tardis) {
        return tardis.location().map(g -> server.getLevel(g.dimension()));
    }

    /**
     * Closes the doors now: anyone standing in a doorway is first stepped out in front of it (into the world outside,
     * or into the console room), so nobody is left inside the shut door block; then both portals go.
     */
    public static void close(MinecraftServer server, TardisRecord tardis) {
        CompoundTag tag = tardis.moduleData(MODULE);
        if (!tag.getBoolean("open")) {
            return;
        }
        tardis.location().ifPresent(g -> {
            ServerLevel l = server.getLevel(g.dimension());
            if (l != null) {
                remove(l, tag, "outside");
                clearDoorway(l, g.pos(), Exteriors.facing(l, g.pos()));
                setOpen(l, g.pos(), false);
            }
        });
        Interiors.level(server).ifPresent(l -> {
            remove(l, tag, "inside");
            clearDoorway(l, interiorDoor(tardis), TardisContent.tuning().door().interiorFacing());
            setOpen(l, interiorDoor(tardis), false);
        });
        tardis.setModuleData(MODULE, new CompoundTag());
    }

    /**
     * Moves every entity overlapping the two door blocks at {@code base} to just in front of the doorway (the way
     * {@code facing} points), keeping its look, so the closed door doesn't trap it or let it slip through the wall.
     */
    static void clearDoorway(ServerLevel level, BlockPos base, Direction facing) {
        AABB doorway = new AABB(base).expandTowards(0, 1, 0).deflate(0.01);
        Vec3 front = Vec3.atBottomCenterOf(base.relative(facing));
        for (Entity e : level.getEntitiesOfClass(Entity.class, doorway, x -> !(x instanceof Portal) && !x.isRemoved()
                && !x.isPassenger())) {
            e.teleportTo(front.x, front.y, front.z);
            e.setDeltaMovement(Vec3.ZERO);
            e.resetFallDistance();
            TardisRB.LOG.debug("[door] stepped {} out of the closing doorway at {}", e.getName().getString(), base);
        }
    }

    /** The interior door's lower block. */
    public static BlockPos interiorDoor(TardisRecord tardis) {
        return Interiors.origin(tardis.plot()).offset(TardisContent.tuning().door().interiorDoor());
    }

    /** Opens or shuts the two door blocks (exterior or interior) whose lower block is at {@code base}. */
    static void setOpen(ServerLevel level, BlockPos base, boolean open) {
        for (BlockPos p : new BlockPos[] {base, base.above()}) {
            BlockState state = level.getBlockState(p);
            if (!state.hasProperty(BlockStateProperties.OPEN)) {
                continue;
            }
            BlockState next = state.setValue(BlockStateProperties.OPEN, open);
            if (next.hasProperty(ExteriorBlock.CLOSING)) {
                next = next.setValue(ExteriorBlock.CLOSING, false);
            }
            if (next != state) {
                level.setBlock(p, next, Block.UPDATE_ALL);
            }
        }
    }

    /** Marks the police box's doors as swinging shut (the renderer plays the swing) or not. */
    static void setClosing(ServerLevel level, BlockPos base, boolean closing) {
        for (BlockPos p : new BlockPos[] {base, base.above()}) {
            BlockState state = level.getBlockState(p);
            if (state.hasProperty(ExteriorBlock.CLOSING) && state.getValue(ExteriorBlock.CLOSING) != closing) {
                level.setBlock(p, state.setValue(ExteriorBlock.CLOSING, closing), Block.UPDATE_ALL);
            }
        }
    }

    private static void remove(ServerLevel level, CompoundTag tag, String key) {
        if (!tag.hasUUID(key)) {
            return;
        }
        UUID id = tag.getUUID(key);
        Entity e = level.getEntity(id);
        if (e != null) {
            e.discard();
        }
    }

    /** Using the doors: open ones start to shut, shutting ones swing open again, shut ones open. */
    public static boolean toggle(MinecraftServer server, TardisRecord tardis) {
        if (isClosing(tardis)) {
            reopen(server, tardis);
            return true;
        }
        if (isOpen(tardis)) {
            shut(server, tardis);
            return true;
        }
        return open(server, tardis);
    }
}
