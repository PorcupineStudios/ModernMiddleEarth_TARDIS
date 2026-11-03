package com.porcupinestudios.tardisrb.gametest;

import com.porcupinestudios.tardisrb.TardisRB;
import com.porcupinestudios.tardisrb.door.Doors;
import java.util.Optional;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import qouteall.imm_ptl.core.portal.Portal;

/** The see-through door (Immersive Portals): the turn through the doorway and a real two-way portal pair. */
@GameTestHolder(TardisRB.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DoorTests {
    private DoorTests() {}

    private static boolean close(Vec3 a, Vec3 b) {
        return a.distanceTo(b) < 1e-3;
    }

    @GameTest(template = "platform")
    public static void theDoorwayTurnsTheViewAndLinksBothWays(GameTestHelper helper) {
        // looking into a box that faces east (westward) must come out looking south, into the console room
        Vec3 in = Doors.rotation(Direction.EAST, Direction.SOUTH).rotate(new Vec3(-1, 0, 0));
        helper.assertTrue(close(in, new Vec3(0, 0, 1)), "east box -> south room turns the view to " + in);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            Vec3 w = Doors.axisW(d);
            Vec3 normal = w.cross(new Vec3(0, 1, 0));
            helper.assertTrue(close(normal, Vec3.atLowerCornerOf(d.getNormal())), d + ": width axis gives " + normal);
            Vec3 turned = Doors.rotation(d, Direction.SOUTH).rotate(Vec3.atLowerCornerOf(d.getOpposite().getNormal()));
            helper.assertTrue(close(turned, new Vec3(0, 0, 1)), d + ": looking in comes out as " + turned);
        }

        Doors.Side a = new Doors.Side(helper.absoluteVec(new Vec3(3, 3, 2.5)), Direction.EAST);
        Doors.Side b = new Doors.Side(helper.absoluteVec(new Vec3(10, 3, 2.5)), Direction.SOUTH);
        Optional<Portal[]> pair = Doors.link(helper.getLevel(), a, helper.getLevel(), b);
        helper.assertTrue(pair.isPresent(), "no portals were made");
        Portal there = pair.get()[0];
        Portal back = pair.get()[1];
        helper.assertTrue(there.isAlive() && back.isAlive(), "a portal isn't in the world");
        helper.assertTrue(close(there.transformPoint(a.center()), b.center()), "the doorway leads to "
                + there.transformPoint(a.center()));
        helper.assertTrue(close(there.getNormal(), new Vec3(1, 0, 0)), "the box portal faces " + there.getNormal());
        helper.assertTrue(close(there.transformLocalVec(new Vec3(-1, 0, 0)), new Vec3(0, 0, 1)),
                "walking in comes out going " + there.transformLocalVec(new Vec3(-1, 0, 0)));
        helper.assertTrue(close(back.transformPoint(b.center()), a.center()), "the way back leads to "
                + back.transformPoint(b.center()));
        helper.assertTrue(Doors.TAG.equals(there.portalTag) && Doors.TAG.equals(back.portalTag), "portals not tagged");
        there.discard();
        back.discard();
        helper.succeed();
    }
}
