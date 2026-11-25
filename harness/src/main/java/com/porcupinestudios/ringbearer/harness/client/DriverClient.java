package com.porcupinestudios.ringbearer.harness.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.porcupinestudios.ringbearer.harness.HarnessMod;
import com.porcupinestudios.ringbearer.harness.content.DriveInput;
import com.porcupinestudios.ringbearer.harness.content.HitchBlock;
import com.porcupinestudios.ringbearer.harness.content.HitchLink;
import com.porcupinestudios.ringbearer.harness.physics.Restraint;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

/**
 * The driver's side of {@code content/Reins}: while the local player holds the reins of a hitch, their movement keys go
 * to the server as {@link DriveInput} every tick instead of moving them; every player holding reins is drawn with two
 * reins from their hand to the team leader's head. Also the hold-back of an animal the local player rides
 * (a rider's client moves it, so the server can't hold it back): the same rule ({@link Restraint}), from the limits the
 * animal's {@link HitchLink} carries.
 */
public final class DriverClient {
    private static final Map<Entity, Vector3d> CORRECTIONS = new WeakHashMap<>();

    private DriverClient() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener(DriverClient::keys);
        NeoForge.EVENT_BUS.addListener(DriverClient::drawReins);
        NeoForge.EVENT_BUS.addListener(DriverClient::holdBackRidden);
    }

    private static boolean holdsReins(Player p) {
        return p.getMainHandItem().is(HarnessMod.REINS.get()) || p.getOffhandItem().is(HarnessMod.REINS.get());
    }

    /** The keys drive the team, not the player (sneak and jump stay theirs). */
    private static void keys(MovementInputUpdateEvent event) {
        Player p = event.getEntity();
        if (!(p instanceof LocalPlayer) || p.getData(HarnessMod.DRIVING).isEmpty() || !holdsReins(p)) {
            return;
        }
        var in = event.getInput();
        float forward = Math.signum(in.forwardImpulse), turn = Math.signum(in.leftImpulse);
        boolean sprint = Minecraft.getInstance().options.keySprint.isDown();
        PacketDistributor.sendToServer(new DriveInput(forward, turn, sprint));
        in.forwardImpulse = 0;
        in.leftImpulse = 0;
        in.up = in.down = in.left = in.right = false;
    }

    // --- the hitch on the client ------------------------------------------------------------------------------------

    /** The hitch point and its forward in the world, from the client's view of the vehicle; null if not loaded. */
    @Nullable
    private static Vector3d[] hitch(Level level, BlockPos pos, float partial) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof HitchBlock)) {
            return null;
        }
        Direction f = state.getValue(HitchBlock.FACING);
        Vector3d point = new Vector3d(pos.getX() + 0.5 + 0.5 * f.getStepX(), pos.getY() + 0.5,
                pos.getZ() + 0.5 + 0.5 * f.getStepZ());
        Vector3d forward = new Vector3d(f.getStepX(), 0, f.getStepZ());
        ClientSubLevel vehicle = Sable.HELPER.getContainingClient(pos.getX() >> 4, pos.getZ() >> 4);
        if (vehicle != null) {
            Pose3dc pose = partial < 0 ? vehicle.logicalPose() : vehicle.renderPose(partial);
            pose.transformPosition(point);
            pose.transformNormal(forward);
            forward.y = 0;
            if (forward.lengthSquared() > 1e-9) {
                forward.normalize();
            }
        }
        return new Vector3d[] {point, forward};
    }

    // --- the hold-back for a ridden animal --------------------------------------------------------------------------

    private static void holdBackRidden(EntityTickEvent.Post event) {
        Entity e = event.getEntity();
        Minecraft mc = Minecraft.getInstance();
        if (!e.level().isClientSide || mc.player == null || e.getControllingPassenger() != mc.player
                || !e.hasData(HarnessMod.HITCH_LINK)) {
            return;
        }
        HitchLink link = HitchLink.of(e).orElse(null);
        if (link == null || !link.hasLimits()) {
            return;
        }
        Vector3d[] h = hitch(e.level(), link.harness(), -1);
        if (h == null) {
            return;
        }
        List<Double> l = link.limits();
        double yaw = Math.toRadians(e instanceof Mob m ? m.yBodyRot : e.getYRot());
        double fx = -Math.sin(yaw), fz = Math.cos(yaw), rx = -fz, rz = fx;
        Vector3d tow = new Vector3d(e.getX() + rx * l.get(1) + fx * l.get(3), e.getY() + l.get(2),
                e.getZ() + rz * l.get(1) + fz * l.get(3));
        Vector3d back = Restraint.correction(h[0], h[1], tow, l.get(0), l.get(4), l.get(5), l.get(6),
                CORRECTIONS.computeIfAbsent(e, k -> new Vector3d()));
        if (back.lengthSquared() < 1e-12) {
            return;
        }
        e.setPos(e.getX() - back.x, e.getY(), e.getZ() - back.z);
        Vec3 v = e.getDeltaMovement();
        Vector3d out = back.normalize(new Vector3d());
        double along = v.x * out.x + v.z * out.z;
        if (along > 0) {
            e.setDeltaMovement(v.x - out.x * along, v.y, v.z - out.z * along);
        }
    }

    // --- the reins ------------------------------------------------------------------------------------------------------

    private static void drawReins(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        PoseStack pose = event.getPoseStack();
        for (Player p : mc.level.players()) {
            BlockPos pos = p.getData(HarnessMod.DRIVING).orElse(null);
            if (pos == null || !holdsReins(p)) {
                continue;
            }
            Vector3d[] h = hitch(mc.level, pos, partial);
            Mob leader = h == null ? null : leader(mc.level, pos, h);
            if (leader == null) {
                continue;
            }
            Vec3 at = p.getPosition(partial);
            double yaw = Math.toRadians(Mth.rotLerp(partial, p.yBodyRotO, p.yBodyRot));
            Vec3 f = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
            Vec3 r = new Vec3(-f.z, 0, f.x);
            Vec3 hand = at.add(0, p.getBbHeight() * 0.55, 0).add(f.scale(0.35));
            Vec3 lp = leader.getPosition(partial);
            double ly = Math.toRadians(Mth.rotLerp(partial, leader.yHeadRotO, leader.yHeadRot));
            Vec3 lf = new Vec3(-Math.sin(ly), 0, Math.cos(ly));
            Vec3 head = lp.add(0, leader.getEyeHeight() - 0.15, 0).add(lf.scale(leader.getBbWidth() * 0.5));
            int lightA = LevelRenderer.getLightColor(mc.level, BlockPos.containing(hand));
            int lightB = LevelRenderer.getLightColor(mc.level, BlockPos.containing(head));
            for (int s = -1; s <= 1; s += 2) {
                Vec3 a = hand.add(r.scale(0.1 * s));
                Vec3 b = head.add(new Vec3(-lf.z, 0, lf.x).scale(0.2 * s));
                pose.pushPose();
                pose.translate(a.x - cam.x, a.y - cam.y, a.z - cam.z);
                RopeRenderer.rope(pose, buffers, Vec3.ZERO, b.subtract(a), lightA, lightB, 0x3b2a1a);
                pose.popPose();
            }
        }
        buffers.endBatch(RenderType.leash());
    }

    /** The team's leader as the client sees it: of the animals hitched there, the one furthest ahead of the hitch. */
    @Nullable
    private static Mob leader(Level level, BlockPos pos, Vector3d[] hitch) {
        Vector3d point = hitch[0], f = hitch[1];
        Mob best = null;
        double bestAhead = -Double.MAX_VALUE;
        AABB around = new AABB(point.x, point.y, point.z, point.x, point.y, point.z).inflate(16);
        for (Mob m : level.getEntitiesOfClass(Mob.class, around,
                m -> HitchLink.of(m).map(l -> l.harness().equals(pos)).orElse(false))) {
            double ahead = (m.getX() - point.x) * f.x + (m.getZ() - point.z) * f.z;
            if (ahead > bestAhead) {
                bestAhead = ahead;
                best = m;
            }
        }
        return best;
    }
}
