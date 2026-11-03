package com.porcupinestudios.tardisrb.gametest;

import com.porcupinestudios.tardisrb.TardisRB;
import com.porcupinestudios.tardisrb.progression.SkyLink;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Space, first slice: the sky link's map between a spot on the planet and a point over the ball. */
@GameTestHolder(TardisRB.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SpaceTests {
    private SpaceTests() {}

    @GameTest(template = "platform")
    public static void goingUpAndComingDownLandsOnTheSameSpot(GameTestHelper helper) {
        SkyLink link = new SkyLink(Level.OVERWORLD, ResourceKey.create(Registries.DIMENSION, TardisRB.id("space")),
                new Vec3(10, 20, 30), 120, 340, 4096, 24);
        double[][] spots = {{0, 0}, {1000, -2000}, {-4000, 3500}, {2500, 100}};
        for (double[] s : spots) {
            Vec3 up = link.toSpace(s[0], s[1], 16);
            helper.assertTrue(Math.abs(up.distanceTo(link.center()) - 136) < 1e-6, "not 16 above the ball: " + up);
            double[] down = link.toSurface(up);
            helper.assertTrue(Math.abs(down[0] - s[0]) < 1e-6 && Math.abs(down[1] - s[1]) < 1e-6,
                    s[0] + "," + s[1] + " came back as " + down[0] + "," + down[1]);
        }
        helper.assertTrue(Math.abs(link.wrap(4096 + 10) - (-4096 + 10)) < 1e-9, "x doesn't wrap round the planet");
        helper.assertTrue(Math.abs(link.wrap(-10) + 10) < 1e-9, "wrap moved a spot inside the planet");
        Vec3 a = link.toSpace(5000, 0, 0);
        Vec3 b = link.toSpace(5000 - 8192, 0, 0);
        helper.assertTrue(a.distanceTo(b) < 1e-6, "a full trip round the planet doesn't come back: " + a + " " + b);
        helper.succeed();
    }
}
