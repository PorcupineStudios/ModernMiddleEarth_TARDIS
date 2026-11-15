package com.porcupinestudios.tardisrb.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.porcupinestudios.tardisrb.TardisRB;
import com.porcupinestudios.tardisrb.vortex.Vortex;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/**
 * THROWAWAY (vortex-smooth-shot branch only): with -Dtardis_rb.vortexStep=S, the vortex's clock moves exactly S seconds
 * per rendered frame, and once the camera has been in the vortex for WARMUP frames, every frame is saved to
 * run/vortex_frames/, COUNT of them. Played back at 1/S frames a second, that is the vortex at true speed however slowly
 * the (software) renderer draws.
 */
@EventBusSubscriber(modid = TardisRB.MOD_ID, value = Dist.CLIENT)
public final class FrameDump {
    static final float STEP = Float.parseFloat(System.getProperty("tardis_rb.vortexStep", "0"));
    private static final int WARMUP = 30;
    private static final int COUNT = Integer.getInteger("tardis_rb.vortexFrames", 360);
    private static int frames;
    private static int saved;

    private FrameDump() {
    }

    static float vortexTime() {
        return 100 + frames * STEP;
    }

    @SubscribeEvent
    public static void onFrame(RenderFrameEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (STEP <= 0 || mc.level == null || mc.level.dimension() != Vortex.DIMENSION || saved >= COUNT) {
            return;
        }
        frames++;
        if (frames <= WARMUP) {
            return;
        }
        try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Path dir = mc.gameDirectory.toPath().resolve("vortex_frames");
            Files.createDirectories(dir);
            image.writeToFile(dir.resolve(String.format("f%05d.png", saved++)));
        } catch (Exception e) {
            System.err.println("frame dump: " + e);
        }
    }
}
