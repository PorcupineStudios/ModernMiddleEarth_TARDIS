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

    @GameTest(template = "platform")
    public static void gravityFadesAndOrbitsHold(GameTestHelper helper) {
        Planet p = bigPlanet(SkyLink.Wrap.TORUS);
        Tuning.SpaceTuning t = Tuning.DEFAULT.space();
        double floor = p.link().orbit().floor();
        double g0 = com.porcupinestudios.tardisrb.space.Orbits.gravity(p, floor, t);
        helper.assertTrue(Math.abs(g0 - 0.08 * t.gravity()) < 0.0001, "gravity just above the clouds: " + g0);
        double top = com.porcupinestudios.tardisrb.space.Orbits.gravity(p, p.link().orbit().top(), t);
        helper.assertTrue(Math.abs(top / (0.08 * t.gravity()) - 1.0 / 16) < 1e-6,
                "gravity at 3 radii up should be 1/16: " + top / (0.08 * t.gravity()));
        double v = com.porcupinestudios.tardisrb.space.Orbits.orbitSpeed(p, floor, t);
        double still = com.porcupinestudios.tardisrb.space.Orbits.down(p, floor, net.minecraft.world.phys.Vec3.ZERO, t);
        double orbiting = com.porcupinestudios.tardisrb.space.Orbits.down(p, floor,
                new net.minecraft.world.phys.Vec3(v, 0, 0), t);
        helper.assertTrue(Math.abs(still - g0) < 1e-9, "standing still should feel full gravity");
        helper.assertTrue(Math.abs(orbiting) < 1e-9, "orbit speed should cancel gravity: " + orbiting);
        // one tick of physics on a real entity: no drag, pulled down, capped
        var stand = new net.minecraft.world.entity.decoration.ArmorStand(helper.getLevel(), 0, 200, 0);
        stand.setDeltaMovement(0.91, 0, 0);
        stand.setOnGround(false);
        com.porcupinestudios.tardisrb.space.Orbits.step(stand, p, t, 0);
        var after = stand.getDeltaMovement();
        helper.assertTrue(Math.abs(after.x - 1.0) < 1e-6, "drag wasn't undone: " + after);
        helper.assertTrue(after.y < 0, "not pulled down: " + after);
        stand.setDeltaMovement(50, 0, 0);
        com.porcupinestudios.tardisrb.space.Orbits.step(stand, p, t, 0);
        helper.assertTrue(stand.getDeltaMovement().length() <= t.motion().maxSpeed() + 1e-6, "speed not capped");
        // a command's gravity scale reaches space gravity too
        com.porcupinestudios.tardisrb.gravity.GravityScale.setEntity(stand, 0);
        stand.setDeltaMovement(0, 0, 0);
        com.porcupinestudios.tardisrb.space.Orbits.step(stand, p, t, 0);
        helper.assertTrue(Math.abs(stand.getDeltaMovement().y) < 1e-9, "gravity x0 still fell: " + stand.getDeltaMovement());
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void gravityCommandsScaleGravity(GameTestHelper helper) {
        var level = helper.getLevel();
        var stand = new net.minecraft.world.entity.decoration.ArmorStand(level, 0, 200, 0);
        var gravity = stand.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.GRAVITY);
        double normal = gravity.getValue();
        com.porcupinestudios.tardisrb.gravity.GravityScale.setEntity(stand, 0.5);
        helper.assertTrue(Math.abs(gravity.getValue() - normal * 0.5) < 1e-9, "entity x0.5: " + gravity.getValue());
        var server = level.getServer();
        com.porcupinestudios.tardisrb.gravity.GravityScale.setDimension(server, level.dimension(), 0.2);
        level.addFreshEntity(stand);
        helper.assertTrue(Math.abs(com.porcupinestudios.tardisrb.gravity.GravityScale.of(stand) - 0.1) < 1e-9,
                "entity x0.5 in a x0.2 dimension: " + com.porcupinestudios.tardisrb.gravity.GravityScale.of(stand));
        com.porcupinestudios.tardisrb.gravity.GravityScale.setDimension(server, level.dimension(), 1);
        com.porcupinestudios.tardisrb.gravity.GravityScale.setEntity(stand, 1);
        helper.assertTrue(Math.abs(gravity.getValue() - normal) < 1e-9, "reset didn't restore gravity");
        stand.discard();
        helper.succeed();
    }

    /**
     * Aaron (2026-11-04): gravity direction by command. Gravity up drops an armor stand onto a ceiling, gravity east
     * onto a wall, and turning it back down drops it to the floor; saving keeps the direction.
     */
    @GameTest(template = "platform", timeoutTicks = 200)
    public static void gravityDirectionTurns(GameTestHelper helper) {
        var up = net.minecraft.core.Direction.UP;
        var east = net.minecraft.core.Direction.EAST;
        var ceiling = new net.minecraft.core.BlockPos(1, 6, 1);
        var wall = new net.minecraft.core.BlockPos(4, 4, 3);
        helper.setBlock(ceiling, net.minecraft.world.level.block.Blocks.STONE);
        helper.setBlock(wall, net.minecraft.world.level.block.Blocks.STONE);
        helper.setBlock(wall.above(), net.minecraft.world.level.block.Blocks.STONE);
        var high = helper.spawn(net.minecraft.world.entity.EntityType.ARMOR_STAND, new net.minecraft.world.phys.Vec3(1.5, 3.0, 1.5));
        var side = helper.spawn(net.minecraft.world.entity.EntityType.ARMOR_STAND, new net.minecraft.world.phys.Vec3(1.5, 4.5, 3.5));
        helper.assertTrue(com.porcupinestudios.tardisrb.gravity.GravityDirection.set(high, up), "can't turn gravity");
        com.porcupinestudios.tardisrb.gravity.GravityDirection.set(side, east);
        var box = high.getBoundingBox();
        helper.assertTrue(Math.abs(box.maxY - high.getY()) < 1e-3 && box.minY < high.getY() - 1,
                "an upside-down stand should hang from its feet: " + box + " at " + high.position());
        var saved = high.saveWithoutId(new net.minecraft.nbt.CompoundTag());
        helper.assertTrue("up".equals(saved.getString("tardis_rb:gravity")), "direction not saved: " + saved);
        double ceilingY = helper.absolutePos(ceiling).getY();
        double wallX = helper.absolutePos(wall).getX();
        helper.succeedWhen(() -> {
            helper.assertTrue(high.onGround() && Math.abs(high.getY() - ceilingY) < 1e-3,
                    "not standing on the ceiling: y " + high.getY() + " (ceiling " + ceilingY + ")");
            helper.assertTrue(side.onGround() && Math.abs(side.getX() - wallX) < 1e-3,
                    "not standing on the wall: x " + side.getX() + " (wall " + wallX + ")");
            helper.assertTrue(com.porcupinestudios.tardisrb.gravity.GravityDirection.of(high) == up, "lost");
        });
    }
}
