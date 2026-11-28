package com.porcupinestudios.lotrport.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.porcupinestudios.lotrport.net.HudNet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * LOTR's floating alignment pop-ups (LOTREntityAlignmentBonus): over a kill or a quest giver, what earned it and each
 * faction's change in that faction's colour ("+5 Gondor"), rising a block and fading over LOTR's 80 ticks.
 */
@EventBusSubscriber(modid = "lotr_port", value = Dist.CLIENT)
public final class AlignmentPopups {
    private static final int LIFE = 80;
    private static final int FADE = 20;
    private static final List<Popup> ACTIVE = new ArrayList<>();

    private record Popup(HudNet.AlignmentBonus bonus, long start) {}

    private AlignmentPopups() {}

    public static void receive(HudNet.AlignmentBonus bonus) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            ACTIVE.add(new Popup(bonus, mc.level.getGameTime()));
        }
    }

    @SubscribeEvent
    static void render(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || mc.level == null || ACTIVE.isEmpty()) {
            return;
        }
        float now = mc.level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Font font = mc.font;
        for (Iterator<Popup> it = ACTIVE.iterator(); it.hasNext(); ) {
            Popup p = it.next();
            float age = now - p.start();
            if (age >= LIFE || age < -LIFE) { // a new level's clock: drop old ones
                it.remove();
                continue;
            }
            HudNet.AlignmentBonus b = p.bonus();
            double dist = cam.distanceTo(new Vec3(b.x(), b.y(), b.z()));
            float scale = 0.025f * (float) Math.max(1, dist / 10); // readable from afar, as LOTR's
            int alpha = Math.max(8, Math.round(255 * Math.min(1, (LIFE - age) / FADE)));
            pose.pushPose();
            pose.translate(b.x() - cam.x, b.y() + age / LIFE - cam.y, b.z() - cam.z);
            pose.mulPose(camera.rotation());
            pose.scale(scale, -scale, scale);
            Matrix4f m = pose.last().pose();
            int y = -(font.lineHeight + 1) * b.bonuses().size();
            line(font, m, buffers, b.source(), y, 0xFFFFFF, alpha);
            for (HudNet.Bonus bonus : b.bonuses()) {
                y += font.lineHeight + 1;
                line(font, m, buffers, Component.literal(amount(bonus.amount()) + " ").append(bonus.faction()), y,
                        bonus.color(), alpha);
            }
            pose.popPose();
        }
        buffers.endBatch();
    }

    private static void line(Font font, Matrix4f m, MultiBufferSource buffers, Component text, int y, int rgb,
            int alpha) {
        font.drawInBatch(text, -font.width(text) / 2f, y, alpha << 24 | (rgb & 0xFFFFFF), true, m, buffers,
                Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
    }

    /** "+5", "-2.5": a sign always, a decimal only when there is one. */
    static String amount(float value) {
        String s = value == Math.rint(value) ? String.valueOf((int) value)
                : String.format(Locale.ROOT, "%.1f", value);
        return value > 0 ? "+" + s : s;
    }
}
