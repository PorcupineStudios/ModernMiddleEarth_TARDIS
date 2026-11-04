package com.porcupinestudios.tardisrb.gametest;

import com.porcupinestudios.tardisrb.TardisRB;
import com.porcupinestudios.tardisrb.progression.SkyLink;
import com.porcupinestudios.tardisrb.progression.Tuning;
import com.porcupinestudios.tardisrb.space.Planet;
import com.porcupinestudios.tardisrb.space.SeamPortals;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Planets: the maths between the full-size surface, the orbit layer and the globe, and wrapping round it. */
@GameTestHolder(TardisRB.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SpaceTests {
    private SpaceTests() {}

    /** A planet the size of Middle-earth (the map is 409,600 x 512,000 blocks; round it is twice its height). */
    static Planet bigPlanet(SkyLink.Wrap wrap) {
        SkyLink link = new SkyLink(Level.OVERWORLD, ResourceKey.create(Registries.DIMENSION, TardisRB.id("space")),
                new SkyLink.Bounds(-410880, 613120, -93440, 418560), 340, 64,
                new SkyLink.Orbit(2_000_000, 0, 16, -1950, 1950, 3.0), wrap, true, 40, Optional.empty(), 512);
        return new Planet(TardisRB.id("test"), link);
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < 1e-6 * Math.max(1, Math.abs(b));
    }

    @GameTest(template = "platform")
    public static void goingUpAndComingDownLandsOnTheSameSpot(GameTestHelper helper) {
        Planet p = bigPlanet(SkyLink.Wrap.GLOBE);
        helper.assertTrue(p.link().problem() == null, "planet rejected: " + p.link().problem());
        helper.assertTrue(near(p.radius(), 1_024_000 / (2 * Math.PI)), "radius " + p.radius());
        double[][] spots = {{0, 0}, {100_000, -2000}, {-400_000, 400_000}, {250_000, 100}};
        for (double[] s : spots) {
            double sx = p.spaceX(s[0]);
            double sz = p.spaceZ(s[1]);
            helper.assertTrue(p.overLayer(sx, sz, 0), "spot " + s[0] + "," + s[1] + " isn't over the layer");
            helper.assertTrue(near(p.surfaceX(sx), s[0]) && near(p.surfaceZ(sz), s[1]),
                    s[0] + "," + s[1] + " came back as " + p.surfaceX(sx) + "," + p.surfaceZ(sz));
        }
        // the layer is the surface shrunk 16 times, round orbit x
        helper.assertTrue(near(p.spaceX(p.centerX()), 2_000_000), "centre isn't at the orbit's x");
        helper.assertTrue(near(p.spaceX(p.minX() + p.width()) - p.spaceX(p.minX()), 64_000), "layer isn't 64000 wide");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void altitudeClimbsFromTheCeilingToABall(GameTestHelper helper) {
        Planet p = bigPlanet(SkyLink.Wrap.GLOBE);
        helper.assertTrue(near(p.altitude(-1950), 276), "altitude at the floor " + p.altitude(-1950));
        helper.assertTrue(near(p.altitude(1950), 3 * p.radius()), "altitude at the top " + p.altitude(1950));
        double last = 0;
        for (int y = -1950; y <= 1950; y += 50) {
            double a = p.altitude(y);
            helper.assertTrue(a > last, "altitude doesn't keep growing at " + y);
            helper.assertTrue(Math.abs(p.spaceY(a) - y) < 1e-6, "spaceY isn't altitude's inverse at " + y);
            last = a;
        }
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void edgesWrapRoundTheGlobe(GameTestHelper helper) {
        Planet p = bigPlanet(SkyLink.Wrap.GLOBE);
        double maxX = p.minX() + p.width();
        helper.assertTrue(p.wrap(0, 0) == null, "a spot inside moved");
        Planet.Wrapped east = p.wrap(maxX + 5, 1000);
        helper.assertTrue(east != null && near(east.x(), p.minX() + 5) && near(east.z(), 1000) && east.turn() == 0,
                "east edge: " + east);
        Planet.Wrapped west = p.wrap(p.minX() - 5, 1000);
        helper.assertTrue(west != null && near(west.x(), maxX - 5), "west edge: " + west);
        // over the north pole: the far side, heading south, and back over it comes home
        Planet.Wrapped pole = p.wrap(100_000, p.minZ() - 5);
        helper.assertTrue(pole != null && near(pole.z(), p.minZ() + 5) && pole.turn() == 180, "north pole: " + pole);
        helper.assertTrue(Math.abs(pole.x() - 100_000) > p.width() / 4, "the pole didn't go to the far side");
        helper.assertTrue(near(p.poleX(pole.x()), 100_000), "crossing the pole twice doesn't come home");
        // a torus just loops
        Planet t = bigPlanet(SkyLink.Wrap.TORUS);
        Planet.Wrapped loop = t.wrap(100_000, t.minZ() + t.height() + 5);
        helper.assertTrue(loop != null && near(loop.z(), t.minZ() + 5) && loop.turn() == 0, "torus: " + loop);
        helper.assertTrue(bigPlanet(SkyLink.Wrap.NONE).wrap(maxX + 5, 0) == null, "wrap none still wrapped");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void seamPortalsOnlyNearTheEdge(GameTestHelper helper) {
        Planet p = bigPlanet(SkyLink.Wrap.GLOBE);
        Tuning.SpaceTuning t = Tuning.DEFAULT.space();
        helper.assertTrue(SeamPortals.needed(p, 0, 0, t) == 0, "portals wanted in the middle of the world");
        int edge = SeamPortals.needed(p, p.minX() + p.width() - 10, 0, t);
        int expected = (2 * t.seamView()) / t.seamSegment() + 1;
        helper.assertTrue(edge >= expected - 1 && edge <= expected + 1, "pieces at the east edge: " + edge);
        int corner = SeamPortals.needed(p, p.minX() + 10, p.minZ() + 10, t);
        // near the corner: three pieces up the west edge and three along the pole edge
        helper.assertTrue(corner == 6, "a corner should need the side and the pole: " + corner);
        helper.succeed();
    }
}
