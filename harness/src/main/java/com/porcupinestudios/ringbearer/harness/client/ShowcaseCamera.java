package com.porcupinestudios.ringbearer.harness.client;

import com.mojang.logging.LogUtils;
import com.porcupinestudios.ringbearer.harness.dev.Showcase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * Dev only (with {@link Showcase}): a spectator camera tour of the showcase scene, a screenshot at each stop
 * ({@code screenshots/harness_*.png} in the game folder), then the game quits.
 */
public final class ShowcaseCamera {
    private static final Logger LOG = LogUtils.getLogger();
    /** Each stop: tick, camera (eye) and the point looked at, blocks from the join position. */
    private static final double[][] STOPS = {
            {120, 11, 3.2, 15, 4.5, 1.0, 9.5},     // the wagon and its hitched horse, three-quarters from the front
            {150, 7.5, 3.6, 8.0, 4.5, 1.0, 9.8},   // above the hitch: the two ropes to the tug rings
            {180, 12.5, 1.7, 7.3, 12.5, 1.2, 3.5}, // the free horse's harness from its side
            {210, 9.6, 1.9, 4.6, 12.5, 1.5, 3.5},  // its collar, hames and bridle, from in front
            {240, -2.5, 1.6, 9.2, 4.5, 1.0, 9.6},  // the ropes from the side: hitch hooks to tug rings
    };
    private static BlockPos join, origin;
    private static int ticks;

    private ShowcaseCamera() {}

    public static void register() {
        if (Showcase.ON) {
            NeoForge.EVENT_BUS.addListener(ShowcaseCamera::tick);
        }
    }

    private static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (join == null) {
            join = mc.player.blockPosition();
            mc.options.hideGui = true;
        }
        ticks++;
        if (origin == null && ticks >= 60) {
            // the ground under the join position, as the server built on
            origin = new BlockPos(join.getX(), mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, join.getX(),
                    join.getZ()), join.getZ());
            LOG.info("[showcase] scene at {}", origin);
        }
        if (origin == null) {
            return;
        }
        for (int i = 0; i < STOPS.length; i++) {
            double[] s = STOPS[i];
            if (ticks == (int) s[0] - 20) {
                Vec3 eye = new Vec3(origin.getX() + s[1], origin.getY() + s[2], origin.getZ() + s[3]);
                Vec3 at = new Vec3(origin.getX() + s[4], origin.getY() + s[5], origin.getZ() + s[6]);
                Vec3 d = at.subtract(eye);
                float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
                float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z)));
                mc.player.moveTo(eye.x, eye.y - mc.player.getEyeHeight(), eye.z, yaw, pitch);
                mc.player.setDeltaMovement(Vec3.ZERO);
            } else if (ticks == (int) s[0]) {
                String name = "harness_" + (i + 1) + ".png";
                Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(),
                        msg -> LOG.info("[showcase] {}", msg.getString()));
            }
        }
        if (ticks == (int) STOPS[STOPS.length - 1][0] + 20) {
            LOG.info("[showcase] done");
            mc.stop();
        }
    }
}
