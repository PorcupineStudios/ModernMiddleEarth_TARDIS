package com.porcupinestudios.ringbearer.harness.client;

import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;
import net.minecraft.world.entity.Entity;
import net.neoforged.fml.InterModComms;
import net.neoforged.fml.event.lifecycle.InterModProcessEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.slf4j.Logger;

/**
 * Where a model part of an animal was just drawn, from mods whose models aren't vanilla's: they send IMC
 * {@code part_pose} with a {@code BiFunction<Entity, String, Matrix4f>} giving, during the entity's
 * {@code RenderLivingEvent.Post}, the part's frame (model pixels / 16, camera-relative like the event's pose stack) or
 * null. The LOTR port sends one. {@link BodyHarness} draws harness pieces in those frames, so they move with the head,
 * neck and body.
 */
public final class PartPoses {
    public static final String IMC_METHOD = "part_pose";
    private static final Logger LOG = LogUtils.getLogger();
    private static final List<BiFunction<Entity, String, Matrix4f>> SOURCES = new CopyOnWriteArrayList<>();

    private PartPoses() {}

    @SuppressWarnings("unchecked")
    static void onProcess(InterModProcessEvent event) {
        event.getIMCStream(IMC_METHOD::equals).forEach((InterModComms.IMCMessage m) -> {
            if (m.messageSupplier().get() instanceof BiFunction<?, ?, ?> f) {
                SOURCES.add((BiFunction<Entity, String, Matrix4f>) f);
                LOG.info("Model part poses from {}", m.senderModId());
            }
        });
    }

    @Nullable
    static Matrix4f of(Entity e, String part) {
        for (BiFunction<Entity, String, Matrix4f> f : SOURCES) {
            Matrix4f m = f.apply(e, part);
            if (m != null) {
                return m;
            }
        }
        return null;
    }
}
