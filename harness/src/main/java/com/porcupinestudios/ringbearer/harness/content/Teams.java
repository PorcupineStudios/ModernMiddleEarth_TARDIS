package com.porcupinestudios.ringbearer.harness.content;

import com.porcupinestudios.ringbearer.harness.draught.Driving;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * A hitch's animals as a team (Aaron 2026-11-25): the one hitched furthest ahead leads, and the rest follow it, each
 * keeping the place it was hitched in (blocks right of and ahead of the hitch) relative to the leader, turned with the
 * leader's heading, so a pair or a rabbit team goes as one. The leader goes where its rider steers it (saddled and
 * ridden), where the reins drive it ({@link Reins}), or where its own AI takes it. A follower with a rider is left to
 * its rider.
 * <p>
 * Each runs before the animal's own tick (server): it sets the animal's velocity for this tick, and the animal's own
 * movement (collision, stepping up, gravity, walk animation) does the rest; the hitch's hold-back after the tick still
 * applies. While it does, the animal's AI may not move, look or jump (its goal flags are held off, and given back the
 * first tick it isn't driven: nothing is saved).
 */
public final class Teams {
    /** blocks/tick: the most a follower makes up on its place per tick, as a share of the gap */
    private static final double CATCH_UP = 0.3;
    /** degrees per tick an animal turns to its heading */
    private static final float TURN_STEP = 12f;
    private static final Map<Mob, Long> HELD = new WeakHashMap<>();

    private Teams() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(Teams::beforeTick);
        NeoForge.EVENT_BUS.addListener(Teams::release);
    }

    private static void beforeTick(EntityTickEvent.Pre event) {
        Entity e = event.getEntity();
        if (e.level().isClientSide || !(e instanceof Mob mob) || !e.hasData(com.porcupinestudios.ringbearer.harness.HarnessMod.HITCH_LINK)) {
            return;
        }
        HitchLink.of(e).ifPresent(link -> {
            if (e.level().getBlockEntity(link.harness()) instanceof HitchBlockEntity hitch) {
                drive(hitch, mob);
            }
        });
    }

    /** This tick's move for one hitched animal. */
    static void drive(HitchBlockEntity hitch, Mob mob) {
        HitchBlockEntity.Hitch me = hitch.hitchOf(mob);
        HitchBlockEntity.Hitch lead = hitch.leader();
        if (me == null || lead == null || mob.getControllingPassenger() instanceof Player) {
            return; // its rider drives it
        }
        if (me == lead) {
            Reins.Input in = Reins.input(mob.level(), hitch.getBlockPos());
            if (in == null && Math.abs(me.driveSpeed) < 1e-3) {
                me.driveYaw = Float.NaN; // no one on the reins: it goes its own way
                me.driveSpeed = 0;
                return;
            }
            hold(mob);
            steerLeader(me, mob, in == null ? new Reins.Input(0, 0, false, 0) : in);
        } else {
            Entity leader = hitch.animal(lead);
            if (leader == null || !leader.isAlive()) {
                return;
            }
            hold(mob);
            follow(me, lead, leader, mob);
        }
    }

    private static void steerLeader(HitchBlockEntity.Hitch me, Mob mob, Reins.Input in) {
        Driving t = Driving.Data.of(mob);
        if (Float.isNaN(me.driveYaw)) {
            me.driveYaw = mob.getYRot();
        }
        double target = in.forward() > 0 ? (in.sprint() ? t.canter() : t.trot()) : in.forward() < 0 ? -t.reverse() : 0;
        target *= slowing(mob) / 20;
        double accel = t.accel() / 400;
        double change = target - me.driveSpeed;
        boolean slowing = Math.abs(target) < Math.abs(me.driveSpeed);
        me.driveSpeed += Mth.clamp(change, -(slowing ? 2 : 1) * accel, (slowing ? 2 : 1) * accel);
        // A (turn +1) is left: in Minecraft's yaw (0 south, 90 west) a left turn lowers it
        double rate = t.turn() / 20 * (Math.abs(me.driveSpeed) > 0.02 ? 1 : 0.5);
        me.driveYaw = Mth.wrapDegrees((float) (me.driveYaw - in.turn() * rate));
        face(mob, me.driveYaw);
        Vec3 f = forward(me.driveYaw);
        mob.setDeltaMovement(f.x * me.driveSpeed, mob.getDeltaMovement().y, f.z * me.driveSpeed);
    }

    /**
     * A follower's place: the leader's position plus the difference of their places in the team, turned with the
     * leader's heading. It moves as the leader moved last tick, plus a share of the gap to its place, at most a little
     * faster than the leader.
     */
    private static void follow(HitchBlockEntity.Hitch me, HitchBlockEntity.Hitch lead, Entity leader, Mob mob) {
        float heading = leader instanceof Mob m ? m.yBodyRot : leader.getYRot();
        Vec3 f = forward(heading);
        Vec3 right = new Vec3(-f.z, 0, f.x);
        Vec3 place = leader.position().add(f.scale(me.ahead - lead.ahead)).add(right.scale(me.side - lead.side));
        Vec3 leaderMove = new Vec3(leader.getX() - leader.xo, 0, leader.getZ() - leader.zo);
        Vec3 gap = new Vec3(place.x - mob.getX(), 0, place.z - mob.getZ());
        Vec3 v = leaderMove.add(gap.scale(CATCH_UP));
        double cap = Math.max(0.35, leaderMove.length() * 1.5 + 0.1);
        if (v.length() > cap) {
            v = v.scale(cap / v.length());
        }
        mob.setDeltaMovement(v.x, mob.getDeltaMovement().y, v.z);
        if (leaderMove.length() > 0.02) {
            face(mob, heading);
        } else if (gap.length() > 0.3) {
            face(mob, (float) Math.toDegrees(Math.atan2(-v.x, v.z)));
        }
    }

    /** How much the harness slows it (its speed attribute against its base: {@link Straining}). */
    private static double slowing(Mob mob) {
        var attr = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        return attr == null || attr.getBaseValue() <= 0 ? 1 : attr.getValue() / attr.getBaseValue();
    }

    static Vec3 forward(float yaw) {
        double r = Math.toRadians(yaw);
        return new Vec3(-Math.sin(r), 0, Math.cos(r));
    }

    private static void face(Mob mob, float yaw) {
        float y = Mth.approachDegrees(mob.getYRot(), yaw, TURN_STEP);
        mob.setYRot(y);
        mob.setYBodyRot(y);
        mob.setYHeadRot(y);
    }

    /** Keeps the animal's own AI from moving it while the team does. */
    private static void hold(Mob mob) {
        long now = mob.level().getGameTime();
        if (HELD.put(mob, now) == null) {
            mob.goalSelector.disableControlFlag(Goal.Flag.MOVE);
            mob.goalSelector.disableControlFlag(Goal.Flag.JUMP);
            mob.goalSelector.disableControlFlag(Goal.Flag.LOOK);
        }
        mob.getNavigation().stop();
    }

    /** Gives their AI back to animals the team didn't move this tick. */
    private static void release(ServerTickEvent.Post event) {
        if (HELD.isEmpty()) {
            return;
        }
        HELD.entrySet().removeIf(en -> {
            Mob mob = en.getKey();
            long now = mob.level() instanceof ServerLevel sl ? sl.getGameTime() : en.getValue();
            if (!mob.isRemoved() && en.getValue() >= now - 1) {
                return false;
            }
            mob.goalSelector.enableControlFlag(Goal.Flag.MOVE);
            mob.goalSelector.enableControlFlag(Goal.Flag.JUMP);
            mob.goalSelector.enableControlFlag(Goal.Flag.LOOK);
            return true;
        });
    }
}
