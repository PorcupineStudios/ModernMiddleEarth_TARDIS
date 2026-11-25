package com.porcupinestudios.lotrport.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BiFunction;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;

/**
 * Where each part of a creature's model was drawn, for other mods that draw on it (the harness mod's harness: its
 * collar on the neck, its girth on the body, following the animation). When a creature model renders for an entity,
 * the first call (its main model, before saddle and armour passes) records the pose stack it was drawn with; asked
 * for a part during that entity's {@link RenderLivingEvent.Post}, it replays the model's transforms down to the part.
 * Offered through IMC {@code part_pose} ({@link LotrClient}) as a
 * {@code BiFunction<Entity, String, Matrix4f>}: the part's frame (model pixels / 16) in the same camera-relative space
 * as the event's pose stack, or null. Client only, render thread only.
 */
public final class PartPoses {
    private record Seen(Matrix4f base, CreatureModel model, boolean viaDraws) {}

    private static final Map<Entity, Seen> SEEN = new WeakHashMap<>();
    public static final BiFunction<Entity, String, Matrix4f> LOOKUP = PartPoses::of;

    private PartPoses() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener((RenderLivingEvent.Pre<?, ?> e) -> SEEN.remove(e.getEntity()));
    }

    /** The model about to draw for {@code e} under {@code pose} (kept if it is the first this frame). */
    static void record(Entity e, PoseStack pose, CreatureModel model, boolean viaDraws) {
        if (e != null) {
            SEEN.putIfAbsent(e, new Seen(new Matrix4f(pose.last().pose()), model, viaDraws));
        }
    }

    public static Matrix4f of(Entity e, String part) {
        Seen s = SEEN.get(e);
        if (s == null) {
            return null;
        }
        PoseStack pose = new PoseStack();
        pose.last().pose().set(s.base());
        return s.model().intoPart(part, pose, s.viaDraws()) ? new Matrix4f(pose.last().pose()) : null;
    }
}
