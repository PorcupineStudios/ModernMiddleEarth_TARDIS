package com.porcupinestudios.tardisrb.door;

import com.porcupinestudios.tardisrb.TardisRB;
import com.porcupinestudios.tardisrb.block.Exteriors;
import com.porcupinestudios.tardisrb.core.TardisManager;
import com.porcupinestudios.tardisrb.core.TardisRecord;
import com.porcupinestudios.tardisrb.interior.Interiors;
import com.porcupinestudios.tardisrb.progression.TardisContent;
import com.porcupinestudios.tardisrb.progression.Tuning;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import qouteall.imm_ptl.core.api.PortalAPI;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.q_misc_util.my_util.DQuaternion;

/**
 * The see-through TARDIS door (Aaron, 2026-11-03), built on Immersive Portals (a required mod): an open door is a pair
 * of portals, one in front of the police box's doors looking into the console room and its reverse in front of the
 * interior door looking out. You see the other side live and walk through with no loading screen. Closing the door,
 * or the TARDIS flying off, removes them. The open state and portal ids are kept in the record's module data.
 */
public final class Doors {
    public static final String MODULE = "tardis_rb:door";
    public static final String TAG = "tardis_rb:door";

    private Doors() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(Doors::onJoin);
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

    /** One side of the doorway: where the portal plane's centre is, and the way the door faces (towards the viewer). */
    public record Side(Vec3 center, Direction facing) {}

    /** The outside opening, in front of the police box's doors. */
    public static Side outside(BlockPos base, Direction facing) {
        Tuning.DoorTuning t = TardisContent.tuning().door();
        Vec3 c = Vec3.atBottomCenterOf(base).add(0, t.height() / 2 + 0.02, 0)
                .add(Vec3.atLowerCornerOf(facing.getNormal()).scale(0.5 + t.offset()));
        return new Side(c, facing);
    }

    /** The inside opening, in front of the interior door, facing into the console room. */
    public static Side inside(TardisRecord tardis) {
        Tuning.DoorTuning t = TardisContent.tuning().door();
        BlockPos door = Interiors.origin(tardis.plot()).offset(t.interiorDoor());
        Vec3 c = Vec3.atBottomCenterOf(door).add(0, t.height() / 2 + 0.02, 0)
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
        if (where.isEmpty() || !Interiors.ensure(server, tardis)) {
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

    /** Closes the doors: removes both portals wherever they are. */
    public static void close(MinecraftServer server, TardisRecord tardis) {
        CompoundTag tag = tardis.moduleData(MODULE);
        if (!tag.getBoolean("open")) {
            return;
        }
        tardis.location().map(g -> server.getLevel(g.dimension())).ifPresent(l -> remove(l, tag, "outside"));
        Interiors.level(server).ifPresent(l -> remove(l, tag, "inside"));
        tardis.setModuleData(MODULE, new CompoundTag());
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

    public static boolean toggle(MinecraftServer server, TardisRecord tardis) {
        if (isOpen(tardis)) {
            close(server, tardis);
            return true;
        }
        return open(server, tardis);
    }
}
