package com.porcupinestudios.tardisrb.compat.sable;

import com.porcupinestudios.tardisrb.progression.Tuning;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.neoforge.event.ForgeSablePrePhysicsTickEvent;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysics;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Vector3d;

/**
 * A real Sable ship in orbit (run by {@code SpaceTests.stationOrbitKeepsItsFloorDown} when Sable is loaded): a 3x1x3
 * deck is assembled, Sable's world gravity is switched off, a small fast level orbit (radius 16, 16 s a turn) round
 * a point beside it (stood on its side so its floor faces the centre) is driven through {@link ShipOrbits#step}, and after a quarter orbit its up must point away from the centre
 * within 5 degrees.
 */
public final class ShipOrbitTest {
    private static final double RADIUS = 16;
    private static final double PERIOD_S = 16;
    private static volatile Consumer<ForgeSablePrePhysicsTickEvent> active;
    private static boolean listening;

    private ShipOrbitTest() {}

    public static void run(GameTestHelper helper) {
        if (!listening) {
            listening = true;
            NeoForge.EVENT_BUS.addListener((ForgeSablePrePhysicsTickEvent e) -> {
                Consumer<ForgeSablePrePhysicsTickEvent> a = active;
                if (a != null) {
                    a.accept(e);
                }
            });
        }
        ServerLevel level = helper.getLevel();
        BlockPos min = helper.absolutePos(new BlockPos(0, 8, 0));
        BlockPos max = helper.absolutePos(new BlockPos(2, 8, 2));
        for (BlockPos p : BlockPos.betweenClosed(min, max)) {
            level.setBlockAndUpdate(p, Blocks.IRON_BLOCK.defaultBlockState());
        }
        ServerSubLevel ship = SubLevelAssemblyHelper.assembleBlocks(level, min, BlockPos.betweenClosed(min, max),
                new BoundingBox3i(min.getX(), min.getY(), min.getZ(), max.getX(), max.getY(), max.getZ()));
        ShipOrbits.noWorldGravity(level);
        // a level orbit beside it (a vertical one would run into the ground), the deck stood on its side so its floor
        // faces the centre, as a station would start its orbit
        Vec3 centre = ShipOrbits.position(ship).subtract(RADIUS, 0, 0);
        Vec3 at = ShipOrbits.position(ship);
        SubLevelContainer.getContainer(level).physicsSystem().getPipeline().teleport(ship,
                new Vector3d(at.x, at.y, at.z), new org.joml.Quaterniond().rotationTo(0, 1, 0, 1, 0, 0));
        double omega = 2 * Math.PI / PERIOD_S;
        double gTick = omega * omega * RADIUS / 400; // blocks/s² -> blocks/tick²
        Tuning.Motion m = Tuning.Motion.DEFAULT;
        Vec3[] start = ShipOrbits.circular(ShipOrbits.position(ship), centre, new Vec3(-gTick, 0, 0),
                new Vec3(0, 0, 1));
        RigidBodyHandle body = SubLevelContainer.getContainer(level).physicsSystem().getPhysicsHandle(ship);
        body.addLinearAndAngularVelocity(new Vector3d(start[0].x, start[0].y, start[0].z),
                new Vector3d(start[1].x, start[1].y, start[1].z));
        active = e -> {
            if (e.getPhysicsSystem().getLevel() == level && !ship.isRemoved()) {
                Vec3 to = centre.subtract(ShipOrbits.position(ship));
                ShipOrbits.step(e.getPhysicsSystem().getPhysicsHandle(ship), ship, centre,
                        to.normalize().scale(gTick), m, e.getTimeStep());
            }
        };
        long quarter = Math.round(PERIOD_S * 20 / 4);
        helper.runAtTickTime(quarter, () -> {
            active = null;
            Vec3 pos = ShipOrbits.position(ship);
            Vec3 away = pos.subtract(centre).normalize();
            Vector3d up = ship.logicalPose().orientation().transform(new Vector3d(0, 1, 0));
            double off = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, up.x * away.x + up.y * away.y
                    + up.z * away.z))));
            double turned = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, away.x))));
            DimensionPhysicsData.putPhysics(level.dimension(), DimensionPhysics.createDefault(level));
            SubLevelContainer.getContainer(level).removeSubLevel(ship,
                    dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED);
            helper.assertTrue(turned > 45, "the ship didn't go round: " + turned + " degrees, at " + pos);
            helper.assertTrue(off < 5, "its up is " + off + " degrees off 'away from the centre' after "
                    + turned + " degrees of orbit");
            helper.succeed();
        });
    }
}
