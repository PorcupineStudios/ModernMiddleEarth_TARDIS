package com.porcupinestudios.ringbearer.harness.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import com.porcupinestudios.ringbearer.harness.dev.Movies;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * Dev only (with {@link Movies}): films each run. The camera follows the horse-and-wagon from the side, through a
 * heavily smoothed (critically damped, about a second) follow so the camera itself can't add or hide jitter; every
 * rendered frame is saved to {@code movie/<run>/} with its game time ({@code times.txt}: frame, game ticks), and
 * {@code harness/tools/movies.py} picks frames at even game-time steps for the GIFs.
 */
public final class MovieRecorder {
    private static final Logger LOG = LogUtils.getLogger();
    /** The camera's place beside the followed point (blocks: +x is the side the turns swing away from). */
    private static final Vec3 OFFSET = new Vec3(7.0, 3.0, 0.5);
    private static final double FOLLOW_OMEGA = 3.0; // rad/s of game time

    private static String run;
    private static int horseId;
    private static Path dir;
    private static StringBuilder times;
    private static int frame;
    private static double lastTime = Double.NaN;
    private static Vec3 pos, vel;

    private MovieRecorder() {}

    public static void register() {
        if (!Movies.ON) {
            return;
        }
        NeoForge.EVENT_BUS.addListener(MovieRecorder::chat);
        NeoForge.EVENT_BUS.addListener(MovieRecorder::beforeFrame);
        NeoForge.EVENT_BUS.addListener(MovieRecorder::afterFrame);
    }

    private static void chat(ClientChatReceivedEvent e) {
        String text = e.getMessage().getString();
        if (!text.startsWith(Movies.PREFIX)) {
            return;
        }
        e.setCanceled(true);
        String[] w = text.substring(Movies.PREFIX.length()).split(" ");
        Minecraft mc = Minecraft.getInstance();
        mc.options.hideGui = true;
        switch (w[0]) {
            case "start" -> {
                run = w[1];
                horseId = Integer.parseInt(w[2]);
                dir = mc.gameDirectory.toPath().resolve("movie").resolve(run);
                try {
                    Files.createDirectories(dir);
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
                times = new StringBuilder();
                frame = 0;
                pos = null;
                lastTime = Double.NaN;
                LOG.info("[movie] filming {}", run);
            }
            case "end" -> {
                if (run != null) {
                    try {
                        Files.writeString(dir.resolve("times.txt"), times.toString());
                    } catch (IOException ex) {
                        LOG.warn("couldn't write frame times", ex);
                    }
                    LOG.info("[movie] {}: {} frames", run, frame);
                }
                run = null;
            }
            case "all" -> {
                LOG.info("[movie] all done");
                mc.stop();
            }
            default -> { }
        }
    }

    private static void beforeFrame(RenderFrameEvent.Pre e) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (run == null || p == null || mc.level == null) {
            return;
        }
        Entity horse = mc.level.getEntity(horseId);
        if (horse == null) {
            return;
        }
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(true);
        double now = mc.level.getGameTime() + pt;
        // the middle of the rig: the horse's position moved back toward its wagon
        double yaw = Math.toRadians(Mth.rotLerp(pt, horse.yRotO, horse.getYRot()));
        Vec3 target = horse.getPosition(pt).add(Math.sin(yaw) * 3.0, 0.6, -Math.cos(yaw) * 3.0);
        if (pos == null || Double.isNaN(lastTime)) {
            pos = target;
            vel = Vec3.ZERO;
        } else {
            double dt = Math.max(0, now - lastTime) / 20.0;
            // critically damped follow, backward Euler (stable at any frame time)
            double w = FOLLOW_OMEGA, denom = 1 + 2 * w * dt + w * w * dt * dt;
            vel = vel.add(target.subtract(pos).scale(w * w * dt)).scale(1 / denom);
            pos = pos.add(vel.scale(dt));
        }
        lastTime = now;
        Vec3 eye = pos.add(OFFSET);
        Vec3 d = pos.subtract(eye);
        float camYaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float camPitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z)));
        double y = eye.y - p.getEyeHeight();
        p.setPos(eye.x, y, eye.z);
        p.xo = p.xOld = eye.x;
        p.yo = p.yOld = y;
        p.zo = p.zOld = eye.z;
        p.setYRot(camYaw);
        p.yRotO = camYaw;
        p.setXRot(camPitch);
        p.xRotO = camPitch;
        p.setDeltaMovement(Vec3.ZERO);
    }

    private static void afterFrame(RenderFrameEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (run == null || pos == null || mc.level == null) {
            return;
        }
        NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget());
        Path file = dir.resolve(String.format(Locale.ROOT, "f%05d.png", frame));
        times.append(frame).append(' ').append(String.format(Locale.ROOT, "%.4f", lastTime)).append('\n');
        frame++;
        Util.ioPool().execute(() -> {
            try (image) {
                image.writeToFile(file);
            } catch (IOException ex) {
                LOG.warn("couldn't save a frame", ex);
            }
        });
    }
}
