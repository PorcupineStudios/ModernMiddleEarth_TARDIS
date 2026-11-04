package com.porcupinestudios.tardisrb.gravity;

import com.porcupinestudios.tardisrb.gravity.gc.GravityHolder;
import com.porcupinestudios.tardisrb.gravity.gc.util.RotationUtil;
import com.porcupinestudios.tardisrb.progression.TardisContent;
import com.porcupinestudios.tardisrb.progression.Tuning;
import com.porcupinestudios.tardisrb.space.StarSystem;
import com.porcupinestudios.tardisrb.space.Systems;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Free tilt (Aaron's "slice 3"): floating in a solar system, a player leans to the true pull, at any angle, instead of
 * the nearest of six directions. The hitbox stays six-way (its gravity direction); only the view, the look direction
 * (aiming, shooting, block picking) and the drawn body lean, turning round the eyes so they all agree. Standing on
 * blocks the lean goes back to the six-way frame. Kept as the world orientation of the player's own frame, so a
 * change of gravity direction underneath doesn't move the view: the yaw and pitch take up the difference.
 * Both sides tick it the same way (server for aiming, client for the view and for other players' bodies).
 */
public final class Tilt {
    private final Quaternionf now;
    private final Quaternionf prev;

    private Tilt(Quaternionf q) {
        now = new Quaternionf(q);
        prev = new Quaternionf(q);
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(Tilt::onServerTick);
    }

    /** This entity's tilt, or null when it isn't leaning freely. */
    public static Tilt of(Entity e) {
        return ((GravityHolder) e).tardis_rb$tilt();
    }

    public static void clear(Entity e) {
        ((GravityHolder) e).tardis_rb$setTilt(null);
    }

    /** The six-way frame of a gravity direction: turns the entity's own axes into world axes. */
    public static Quaternionf frame(Direction g) {
        return new Quaternionf(RotationUtil.getCameraRotationQuaternion(g));
    }

    private static void onServerTick(PlayerTickEvent.Post event) {
        Player p = event.getEntity();
        MinecraftServer server = p.level().getServer();
        if (p.level().isClientSide() || server == null) {
            return;
        }
        Tuning.SpaceTuning t = TardisContent.tuning().space();
        StarSystem st = Systems.isSystem(server, p.level().dimension())
                ? Systems.near(Systems.all(server), p.level().dimension(), p.position()).orElse(null)
                : null;
        tick(p, st, t);
    }

    /** One tick: lean towards the system's pull while floating, back to six-way on blocks, none outside a system. */
    public static void tick(Entity e, StarSystem st, Tuning.SpaceTuning t) {
        GravityHolder h = (GravityHolder) e;
        if (st == null || !t.motion().freeTilt() || !(e instanceof Player) || !e.isAlive()) {
            if (h.tardis_rb$tilt() != null) {
                h.tardis_rb$setTilt(null);
            }
            return;
        }
        Quaternionf f = frame(h.tardis_rb$gravity());
        Tilt tilt = h.tardis_rb$tilt();
        if (tilt == null) {
            tilt = new Tilt(f);
            h.tardis_rb$setTilt(tilt);
        }
        tilt.prev.set(tilt.now);
        tilt.now.slerp(target(f, e.onGround() ? null : st.gravity(e.position(), t)), (float) t.motion().tiltSpeed())
                .normalize();
    }

    /** The frame leaned so its up points against the pull (null pull: the six-way frame itself). */
    static Quaternionf target(Quaternionf frame, Vec3 pull) {
        if (pull == null || pull.lengthSqr() < 1e-14) {
            return frame;
        }
        Vector3f want = new Vector3f((float) -pull.x, (float) -pull.y, (float) -pull.z).normalize();
        Vector3f up = frame.transform(new Vector3f(0, 1, 0));
        return new Quaternionf().rotationTo(up, want).mul(frame);
    }

    /** The leaned frame now (turns the entity's own axes into world axes). */
    public Quaternionf now() {
        return new Quaternionf(now);
    }

    /** The leaned frame between the last two ticks, for drawing. */
    public Quaternionf at(float partial) {
        return new Quaternionf(prev).slerp(now, partial);
    }

    /** A vector in the entity's own axes, in world axes. */
    public Vec3 toWorld(Vec3 local) {
        Vector3f v = now.transform(new Vector3f((float) local.x, (float) local.y, (float) local.z));
        return new Vec3(v.x, v.y, v.z);
    }

    /**
     * Gravity turned to {@code to} under a leaning entity: keep the way up it leans to and the way it looks, and give
     * it the frame of the new direction leaned the same way, putting the difference into yaw and pitch.
     */
    public static void regravitate(Entity e, Direction to) {
        Tilt tilt = of(e);
        if (tilt == null) {
            return;
        }
        Vec3 look = tilt.toWorld(RotationUtil.rotToVec(e.getYRot(), e.getXRot()));
        Vector3f upWorld = tilt.now.transform(new Vector3f(0, 1, 0));
        Quaternionf f = frame(to);
        Quaternionf q = new Quaternionf().rotationTo(f.transform(new Vector3f(0, 1, 0)), upWorld).mul(f);
        Vector3f local = new Quaternionf(q).conjugate()
                .transform(new Vector3f((float) look.x, (float) look.y, (float) look.z));
        Vec2 yawPitch = RotationUtil.vecToRot(local.x, local.y, local.z);
        float deltaYaw = net.minecraft.util.Mth.wrapDegrees(yawPitch.x - e.getYRot());
        float deltaPitch = yawPitch.y - e.getXRot();
        e.setYRot(e.getYRot() + deltaYaw);
        e.setXRot(e.getXRot() + deltaPitch);
        e.yRotO += deltaYaw;
        e.xRotO += deltaPitch;
        if (e instanceof LivingEntity living) {
            living.yBodyRot += deltaYaw;
            living.yBodyRotO += deltaYaw;
            living.yHeadRot += deltaYaw;
            living.yHeadRotO += deltaYaw;
        }
        tilt.now.set(q);
        tilt.prev.set(q);
    }
}
