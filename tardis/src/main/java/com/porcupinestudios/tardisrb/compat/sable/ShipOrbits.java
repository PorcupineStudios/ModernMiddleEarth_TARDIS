package com.porcupinestudios.tardisrb.compat.sable;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.porcupinestudios.tardisrb.gravity.Decks;
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
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import java.util.List;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Space stations are Aeronautics ships (Aaron 2026-11-15). In a solar system only the focus body pulls them (Sable's
 * own world gravity is turned off in system dimensions), and with {@code space.motion.ship_align} on their up eases
 * toward "away from the focus" as they orbit ({@link ShipAlign}). {@code /tardis station orbit} starts a circular
 * orbit; standing on or in a ship, a player's down is the ship's ({@link Decks}).
 */
public final class ShipOrbits {
    private static final Vector3d UP = new Vector3d(0, 1, 0);
    private static final int TICKS_PER_SECOND = 20;

    private ShipOrbits() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ShipOrbits::prePhysics);
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> commands(e.getDispatcher()));
        Decks.down = ShipOrbits::deckDown;
    }

    /** Down on the ship an entity stands on or is in, else null (callers ask only in a solar system). */
    private static Vec3 deckDown(Entity e) {
        SubLevel s = Ships.under(e);
        return s == null ? null : Ships.down(s);
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
            Vec3 pos = position(ship);
            Optional<StarSystem> st = Systems.near(systems, level.dimension(), pos);
            if (st.isEmpty()) {
                continue;
            }
            RigidBodyHandle body = event.getPhysicsSystem().getPhysicsHandle(ship);
            if (body == null || !body.isValid()) {
                continue;
            }
            // outside every gravity radius nothing pulls or turns it: it drifts (centre null)
            step(body, ship, st.get().focusCenter(pos, t), st.get().gravity(pos, t), t.motion(), dt);
        }
    }

    /**
     * One physics step for one ship: the pull ({@code pull}, blocks/tick²) as added velocity, and with ship_align on a
     * spin easing its up toward "away from {@code centre}".
     */
    public static void step(RigidBodyHandle body, ServerSubLevel ship, Vec3 centre, Vec3 pull, Tuning.Motion m,
            double dt) {
        Vec3 pos = position(ship);
        // blocks/tick² -> blocks/s², minus whatever world gravity Sable still applies (its pipeline keeps the value it
        // started with), then the velocity it adds this step
        Vector3d world = DimensionPhysicsData.getGravity(ship.getLevel(), new Vector3d(pos.x, pos.y, pos.z));
        Vec3 g = pull.scale(TICKS_PER_SECOND * TICKS_PER_SECOND).subtract(world.x, world.y, world.z).scale(dt);
        Vector3d dOmega = new Vector3d();
        Vec3 away = centre == null ? Vec3.ZERO : pos.subtract(centre);
        if (m.shipAlign() && away.lengthSqr() > 1e-6) {
            away = away.normalize();
            Pose3dc pose = ship.logicalPose();
            Vector3d up = pose.orientation().transform(new Vector3d(UP));
            // "away" turns at (r x v) / r² as the ship goes round
            Vector3d v = body.getLinearVelocity(new Vector3d());
            Vec3 out = pos.subtract(centre);
            Vec3 turn = out.cross(new Vec3(v.x, v.y, v.z)).scale(1 / out.lengthSqr());
            dOmega = ShipAlign.deltaOmega(up, new Vector3d(away.x, away.y, away.z), body.getAngularVelocity(),
                    new Vector3d(turn.x, turn.y, turn.z), m.shipAlignStrength(), m.shipAlignDamping(), dt);
        }
        body.addLinearAndAngularVelocity(new Vector3d(g.x, g.y, g.z), dOmega);
    }

    /**
     * Space for Sable: no world-down gravity (the focus pulls instead), no air pressure and no drag, so ships keep their
     * speed and orbit. Re-applied whenever a reload put Sable's own data back.
     */
    static void noWorldGravity(ServerLevel level) {
        if (DimensionPhysicsData.of(level).priority() == Integer.MAX_VALUE) {
            return;
        }
        DimensionPhysicsData.addPhysicsWithPriority(level.dimension(), new DimensionPhysics(level.dimension().location(),
                Integer.MAX_VALUE, Optional.of(0f), Optional.of(new Vector3f()), Optional.of(0.0), Optional.empty(),
                Optional.empty(), false));
    }

    private static void commands(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("tardis").requires(s -> s.hasPermission(2))
                .then(Commands.literal("station").then(Commands.literal("orbit").executes(c -> orbit(c.getSource())))));
    }

    /**
     * Puts the ship the player stands on or looks at into a circular orbit round its focus: speed sqrt(g r) along the
     * player's look, square to the radius, and a spin of one turn per orbit so its floor keeps facing the focus.
     */
    private static int orbit(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        SubLevel s = Ships.under(player);
        if (s == null) {
            s = Ships.lookedAt(player, 64);
        }
        if (!(s instanceof ServerSubLevel ship)) {
            source.sendFailure(Component.literal("Stand on or look at a ship"));
            return 0;
        }
        Vec3 pos = position(ship);
        Optional<StarSystem> st = Systems.isSystem(level.getServer(), level.dimension())
                ? Systems.near(Systems.all(level.getServer()), level.dimension(), pos) : Optional.empty();
        if (st.isEmpty()) {
            source.sendFailure(Component.literal("Ships only orbit in a solar system"));
            return 0;
        }
        double speed = startOrbit(level, ship, st.get(), player.getLookAngle());
        if (speed < 0) {
            source.sendFailure(Component.literal("No pull here to orbit"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(String.format("Orbiting at %.1f blocks/s", speed)), true);
        return 1;
    }

    /** Sets a ship's velocity and spin to a circular orbit along {@code look}; its speed (blocks/s), or -1. */
    public static double startOrbit(ServerLevel level, ServerSubLevel ship, StarSystem st, Vec3 look) {
        Tuning.SpaceTuning t = TardisContent.tuning().space();
        Vec3 pos = position(ship);
        Vec3 centre = st.focusCenter(pos, t);
        Vec3[] v = centre == null ? null : circular(pos, centre, st.gravity(pos, t), look);
        if (v == null) {
            return -1;
        }
        RigidBodyHandle body = SubLevelPhysicsSystem.require(level).getPhysicsHandle(ship);
        Vector3d lin = body.getLinearVelocity(new Vector3d());
        Vector3d ang = body.getAngularVelocity(new Vector3d());
        body.addLinearAndAngularVelocity(new Vector3d(v[0].x, v[0].y, v[0].z).sub(lin),
                new Vector3d(v[1].x, v[1].y, v[1].z).sub(ang));
        return v[0].length();
    }

    /**
     * A circular orbit at {@code pos} round {@code centre} under {@code pull} (blocks/tick²): {velocity, spin} in
     * blocks/s and rad/s, moving along {@code look} made square to the radius, or null without a pull.
     */
    public static Vec3[] circular(Vec3 pos, Vec3 centre, Vec3 pull, Vec3 look) {
        Vec3 out = pos.subtract(centre);
        double r = out.length();
        double g = pull.length() * TICKS_PER_SECOND * TICKS_PER_SECOND;
        if (r < 1e-6 || g < 1e-12) {
            return null;
        }
        Vec3 rhat = out.scale(1 / r);
        Vec3 along = look.subtract(rhat.scale(look.dot(rhat)));
        if (along.lengthSqr() < 1e-9) {
            along = rhat.cross(Math.abs(rhat.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0));
        }
        Vec3 vel = along.normalize().scale(Math.sqrt(g * r));
        return new Vec3[] {vel, rhat.cross(vel).scale(1 / r)};
    }

    public static Vec3 position(SubLevel s) {
        var p = s.logicalPose().position();
        return new Vec3(p.x(), p.y(), p.z());
    }
}
