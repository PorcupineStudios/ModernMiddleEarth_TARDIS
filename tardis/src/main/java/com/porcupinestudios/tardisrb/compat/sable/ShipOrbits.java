package com.porcupinestudios.tardisrb.compat.sable;

import com.porcupinestudios.tardisrb.progression.TardisContent;
import com.porcupinestudios.tardisrb.progression.Tuning;
import com.porcupinestudios.tardisrb.space.StarSystem;
import com.porcupinestudios.tardisrb.space.Systems;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.neoforge.event.ForgeSablePrePhysicsTickEvent;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysics;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Aeronautics ships in a solar system: only the focus body pulls them (Sable's own world gravity is turned off in
 * system dimensions), and with {@code space.motion.ship_align} on their up eases toward "away from the focus" as they
 * orbit ({@link ShipAlign}). Players on board keep their own gravity rules for now (docs/sable.md).
 */
public final class ShipOrbits {
    private static final Vector3d UP = new Vector3d(0, 1, 0);
    private static final int TICKS_PER_SECOND = 20;

    private ShipOrbits() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ShipOrbits::prePhysics);
    }

    private static void prePhysics(ForgeSablePrePhysicsTickEvent event) {
        ServerLevel level = event.getPhysicsSystem().getLevel();
        MinecraftServer server = level.getServer();
        if (!Systems.isSystem(server, level.dimension())) {
            return;
        }
        noWorldGravity(level);
        Tuning.SpaceTuning t = TardisContent.tuning().space();
        List<StarSystem> systems = Systems.all(server);
        double dt = event.getTimeStep();
        for (SubLevel s : SubLevelContainer.getContainer(level).getAllSubLevels()) {
            if (!(s instanceof ServerSubLevel ship) || ship.isRemoved()) {
                continue;
            }
            Pose3dc pose = ship.logicalPose();
            Vec3 pos = new Vec3(pose.position().x(), pose.position().y(), pose.position().z());
            Optional<StarSystem> st = Systems.near(systems, level.dimension(), pos);
            if (st.isEmpty()) {
                continue;
            }
            RigidBodyHandle body = event.getPhysicsSystem().getPhysicsHandle(ship);
            if (body == null || !body.isValid()) {
                continue;
            }
            // blocks/tick² -> blocks/s², then the velocity it adds this step
            Vec3 g = st.get().gravity(pos, t).scale(TICKS_PER_SECOND * TICKS_PER_SECOND * dt);
            Vector3d dOmega = new Vector3d();
            if (t.motion().shipAlign()) {
                Vec3 away = pos.subtract(st.get().focusCenter(pos, t));
                if (away.lengthSqr() > 1e-6) {
                    away = away.normalize();
                    Vector3d up = pose.orientation().transform(new Vector3d(UP));
                    dOmega = ShipAlign.deltaOmega(up, new Vector3d(away.x, away.y, away.z), body.getAngularVelocity(),
                            t.motion().shipAlignStrength(), t.motion().shipAlignDamping(), dt);
                }
            }
            body.addLinearAndAngularVelocity(new Vector3d(g.x, g.y, g.z), dOmega);
        }
    }

    /** Sable pulls every ship toward world down unless a dimension says otherwise; in a system the focus does that. */
    private static void noWorldGravity(ServerLevel level) {
        if (DimensionPhysicsData.getGravity(level).lengthSquared() == 0) {
            return;
        }
        DimensionPhysicsData.addPhysicsWithPriority(level.dimension(), new DimensionPhysics(level.dimension().location(),
                Integer.MAX_VALUE, Optional.empty(), Optional.of(new Vector3f()), Optional.empty(), Optional.empty(),
                Optional.empty(), false));
    }
}
