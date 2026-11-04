package com.porcupinestudios.tardisrb.gametest;

import com.porcupinestudios.tardisrb.TardisRB;
import com.porcupinestudios.tardisrb.block.ExteriorBlock;
import com.porcupinestudios.tardisrb.block.InteriorDoorBlock;
import com.porcupinestudios.tardisrb.block.TardisBlocks;
import com.porcupinestudios.tardisrb.core.TardisManager;
import com.porcupinestudios.tardisrb.core.TardisRecord;
import com.porcupinestudios.tardisrb.flight.CrashSite;
import com.porcupinestudios.tardisrb.door.Doors;
import qouteall.imm_ptl.core.portal.Portal;
import com.porcupinestudios.tardisrb.interior.Interiors;
import com.porcupinestudios.tardisrb.interior.Rooms;
import com.porcupinestudios.tardisrb.progression.TardisContent;
import com.porcupinestudios.tardisrb.space.Planet;
import com.porcupinestudios.tardisrb.space.Planets;
import com.porcupinestudios.tardisrb.space.SeamPortals;
import com.porcupinestudios.tardisrb.space.SpaceTravel;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * A real dedicated-server world (CI, {@code -Dtardis_rb.worldcheck=true}): the crash site exists with its police box,
 * and its interior is built in the interior dimension, which the game test server doesn't make. Logs
 * "TARDIS WORLDCHECK PASSED" or the problems, then stops the server.
 */
public final class WorldCheck {
    private WorldCheck() {}

    public static void register() {
        if (Boolean.getBoolean("tardis_rb.worldcheck")) {
            NeoForge.EVENT_BUS.addListener((ServerStartedEvent e) -> run(e.getServer()));
        }
    }

    /** Repairs the room systems and adds a library in the real interior dimension. */
    private static void checkRoom(MinecraftServer server, TardisRecord wreck, ServerLevel inside, List<String> problems) {
        ResourceLocation repairId = TardisRB.id("rooms_1");
        TardisContent.repair(repairId).ifPresent(r -> wreck.applyRepair(repairId, r));
        wreck.addCharge(1000);
        var out = Rooms.add(server, wreck, TardisRB.id("library"), Optional.empty());
        if (!out.done()) {
            problems.add("library not added: " + out.problem().get().getString());
            return;
        }
        // the first room goes at the end of the first hallway, turned to face along it
        BlockPos shelf = Rooms.placement(Interiors.origin(wreck.plot()), 1, 9).at(1, 1, 2);
        if (!inside.getBlockState(shelf).is(Blocks.BOOKSHELF)) {
            problems.add("no library shelf at " + shelf + ": " + inside.getBlockState(shelf));
        }
    }

    /** The see-through door: opening puts up a portal pair between the box and the console room; closing removes it. */
    private static void checkDoor(MinecraftServer server, TardisRecord wreck, ServerLevel world, ServerLevel inside,
            List<String> problems) {
        if (world == null || !Doors.open(server, wreck)) {
            problems.add("the doors didn't open");
            return;
        }
        var tag = wreck.moduleData(Doors.MODULE);
        var out = world.getEntity(tag.getUUID("outside"));
        var in = inside.getEntity(tag.getUUID("inside"));
        if (!(out instanceof Portal outPortal) || in == null) {
            problems.add("door portals missing: " + out + ", " + in);
            return;
        }
        var expect = Doors.inside(wreck).center();
        var lands = outPortal.transformPoint(outPortal.getOriginPos());
        if (lands.distanceTo(expect) > 0.01) {
            problems.add("the door leads to " + lands + ", not " + expect);
        }
        TardisRB.LOG.info("[worldcheck] door open: {} -> {}", outPortal.getOriginPos(), lands);
        BlockPos box = wreck.location().get().pos();
        BlockPos door = Doors.interiorDoor(wreck);
        if (!world.getBlockState(box).getValue(ExteriorBlock.OPEN)
                || !inside.getBlockState(door.above()).getValue(InteriorDoorBlock.OPEN)) {
            problems.add("the door blocks didn't open: " + world.getBlockState(box) + ", "
                    + inside.getBlockState(door.above()));
        }
        // shutting: the doors swing shut first; the portals stay until they are closed
        Doors.toggle(server, wreck);
        if (!Doors.isClosing(wreck) || out.isRemoved() || !world.getBlockState(box).getValue(ExteriorBlock.CLOSING)
                || !world.getBlockState(box).getValue(ExteriorBlock.OPEN)) {
            problems.add("the doors didn't start swinging shut with the portals still up");
        }
        // someone half through the doorway is stepped out into the console room, not left inside the shut door
        var stand = net.minecraft.world.entity.EntityType.ARMOR_STAND.create(inside);
        if (stand != null) {
            stand.moveTo(Vec3.atBottomCenterOf(door));
            inside.addFreshEntity(stand);
        }
        Doors.close(server, wreck);
        if (stand != null) {
            BlockPos expectStand = door.relative(TardisContent.tuning().door().interiorFacing());
            if (!stand.blockPosition().equals(expectStand)) {
                problems.add("someone in the doorway wasn't stepped out: " + stand.blockPosition());
            }
            stand.discard();
        }
        if (world.getBlockState(box).getValue(ExteriorBlock.CLOSING)) {
            problems.add("the police box is still marked closing");
        }
        if (world.getBlockState(box.above()).getValue(ExteriorBlock.OPEN)
                || inside.getBlockState(door).getValue(InteriorDoorBlock.OPEN)) {
            problems.add("the door blocks didn't shut");
        }
        if (Doors.isOpen(wreck) || !out.isRemoved() || !in.isRemoved()) {
            problems.add("the doors didn't close");
        }
    }

    /**
     * Space: an armor stand above the Overworld's ceiling comes out at the bottom of its orbit layer over the same spot,
     * and back down under the ceiling; one past the planet's east edge wraps to the west edge; a probe at the edge
     * puts up seam portals that lead round the globe.
     */
    private static void checkSpace(MinecraftServer server, List<String> problems) {
        var found = Planets.of(server, Level.OVERWORLD);
        if (found.isEmpty()) {
            problems.add("the Overworld is no planet");
            return;
        }
        Planet planet = found.get();
        ServerLevel world = server.overworld();
        ServerLevel space = server.getLevel(planet.link().space());
        if (space == null) {
            problems.add("no space dimension " + planet.link().space().location());
            return;
        }
        Vec3 start = new Vec3(300.5, planet.link().ceiling() + 5, -700.5);
        world.getChunk(BlockPos.containing(start));
        ArmorStand stand = new ArmorStand(world, start.x, start.y, start.z);
        if (!world.addFreshEntity(stand)) {
            problems.add("the test armor stand wasn't added at " + start);
            return;
        }
        // a fresh check world has no ticking chunks up there, so the moved entity is checked as returned
        var there = SpaceTravel.toSpace(stand, planet, space);
        Vec3 up = new Vec3(planet.spaceX(start.x), planet.link().orbit().floor() + 3, planet.spaceZ(start.z));
        if (there.level() != space || there.position().distanceTo(up) > 1) {
            problems.add("the armor stand went to " + there.level().dimension().location() + " " + there.position()
                    + ", not space at " + up);
            return;
        }
        TardisRB.LOG.info("[worldcheck] up into space: {} -> {} (altitude {})", start, there.position(),
                (int) planet.altitude(there.getY()));
        if (!(there instanceof ArmorStand up1)
                || !up1.getAttribute(Attributes.GRAVITY).hasModifier(SpaceTravel.GRAVITY_ID)) {
            problems.add("no low gravity in space");
        }
        var back = SpaceTravel.toSurface(there, planet, world, TardisContent.tuning().space());
        Vec3 down = new Vec3(start.x, planet.link().ceiling() - 3, start.z);
        if (back.level() != world || back.position().distanceTo(down) > 1) {
            problems.add("the armor stand came down to " + back.level().dimension().location() + " "
                    + back.position() + ", not " + down);
            return;
        }
        TardisRB.LOG.info("[worldcheck] down from space: {}", back.position());
        if (back instanceof ArmorStand down1
                && down1.getAttribute(Attributes.GRAVITY).hasModifier(SpaceTravel.GRAVITY_ID)) {
            problems.add("still low gravity after coming down");
        }
        // off the east edge comes out at the west edge
        var b = planet.link().surface();
        Vec3 off = new Vec3(b.maxX() + 4.5, 100, 20.5);
        world.getChunk(BlockPos.containing(off));
        back.teleportTo(off.x, off.y, off.z);
        SpaceTravel.wrapOnSurface(back, planet);
        if (Math.abs(back.getX() - (b.minX() + 4.5)) > 0.01 || Math.abs(back.getZ() - 20.5) > 0.01) {
            problems.add("off the east edge went to " + back.position() + ", not x " + (b.minX() + 4.5));
        } else {
            TardisRB.LOG.info("[worldcheck] wrapped round the planet: {} -> {}", off, back.position());
        }
        back.discard();
        // a probe near the east edge puts up seam portals there that look round to the west edge
        for (int dz = -160; dz <= 160; dz += 16) {
            world.getChunk(BlockPos.containing(b.maxX() + 1, 64, dz));
        }
        SeamPortals.update(server, List.of(new SeamPortals.Probe(planet.id(), b.maxX() - 8, 0)));
        List<Portal> seams = SeamPortals.portals(world);
        if (seams.isEmpty()) {
            problems.add("no seam portals at the east edge");
        } else {
            Portal s = seams.get(0);
            boolean right = Math.abs(s.getOriginPos().x - b.maxX()) < 0.01
                    && Math.abs(s.getDestPos().x - b.minX()) < 0.01 && s.getDestDim() == world.dimension();
            if (!right) {
                problems.add("seam portal at " + s.getOriginPos() + " leads to " + s.getDestPos());
            } else {
                TardisRB.LOG.info("[worldcheck] {} seam portals, e.g. {} -> {}", seams.size(), s.getOriginPos(),
                        s.getDestPos());
            }
        }
    }

    private static void run(MinecraftServer server) {
        List<String> problems = new ArrayList<>();
        CrashSite.place(server);
        TardisRecord wreck = TardisManager.get(server).all().stream().filter(r -> r.owner().isEmpty()).findFirst()
                .orElse(null);
        if (wreck == null || wreck.location().isEmpty()) {
            problems.add("no crash site");
        } else {
            GlobalPos at = wreck.location().get();
            ServerLevel level = server.getLevel(at.dimension());
            TardisRB.LOG.info("[worldcheck] the wreck stands at {} in {}", at.pos(), at.dimension().location());
            if (level == null || ExteriorBlock.tardisAt(level, at.pos()).isEmpty()) {
                problems.add("no police box at the crash site");
            }
            if (!Interiors.ensure(server, wreck)) {
                problems.add("interior not built");
            } else {
                ServerLevel inside = Interiors.level(server).orElseThrow();
                BlockPos console = Interiors.origin(wreck.plot()).offset(TardisContent.tuning().interior().console());
                if (!inside.getBlockState(console).is(TardisBlocks.CONSOLE.get())) {
                    problems.add("no console at " + console + ": " + inside.getBlockState(console));
                }
                if (Interiors.at(inside, console).map(r -> r != wreck).orElse(true)) {
                    problems.add("the console doesn't find its TARDIS");
                }
                BlockPos station = Interiors.basement(wreck).offset(4, 1, 4);
                if (!inside.getBlockState(station).is(TardisBlocks.REPAIR_STATION.get())) {
                    problems.add("no repair station in the basement at " + station + ": "
                            + inside.getBlockState(station));
                }
                if (Interiors.at(inside, station).map(r -> r != wreck).orElse(true)) {
                    problems.add("the repair station doesn't find its TARDIS");
                }
                BlockPos hatch = Interiors.origin(wreck.plot()).offset(TardisContent.tuning().interior().hatch());
                if (!inside.getBlockState(hatch).is(Blocks.LADDER)) {
                    problems.add("no ladder down to the basement at " + hatch + ": " + inside.getBlockState(hatch));
                }
                checkRoom(server, wreck, inside, problems);
                checkDoor(server, wreck, level, inside, problems);
            }
        }
        checkSpace(server, problems);
        if (problems.isEmpty()) {
            TardisRB.LOG.info("TARDIS WORLDCHECK PASSED");
        } else {
            problems.forEach(p -> TardisRB.LOG.error("[worldcheck] {}", p));
            TardisRB.LOG.error("TARDIS WORLDCHECK FAILED");
        }
        server.halt(false);
    }
}
