package com.porcupinestudios.tardisrb.client;

import com.porcupinestudios.tardisrb.block.ConsoleBlockEntity;
import com.porcupinestudios.tardisrb.console.ConsoleControls;
import com.porcupinestudios.tardisrb.console.ConsoleLayout;
import com.porcupinestudios.tardisrb.progression.Control;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Which console control the player is looking at, for clicking ({@link ConsoleInput}) and for Jade's tooltip
 * ({@code compat/jade}). The controls stand outside the console block, so each clickable part gets its own hit box:
 * the part's box as it is posed now, in the control's own space, tested against the player's view ray turned into
 * that space (so the boxes follow the sloped panels and the swinging levers). The time rotor's column counts too.
 */
public final class ConsoleAim {
    /** The time rotor's glass round its axis, in blocks: a ray inside it hits the column. */
    private static final double COLUMN_RADIUS = 0.4;

    private ConsoleAim() {}

    /**
     * What is aimed at: the console, the control ({@code null} for the column), the part clicked ({@code ""} for a
     * control with nothing to click, such as a gauge) and where the ray meets it.
     */
    public record Aim(ConsoleBlockEntity console, ConsoleBlockEntity.Shown control, String click, Vec3 hit,
            double distance) {
        /** What the server is sent for a click ({@link ConsoleControls#press}). */
        public String pressed() {
            return control == null ? ConsoleControls.COLUMN : ConsoleLayout.key(control.id(), click);
        }
    }

    /** The nearest control along the player's view within reach, if any. */
    public static Optional<Aim> find(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return Optional.empty();
        }
        Vec3 eye = mc.player.getEyePosition(partialTick);
        Vec3 end = eye.add(mc.player.getViewVector(partialTick).scale(ConsoleLayout.REACH));
        ConsoleModel model = ConsoleModel.get();
        Aim best = null;
        for (ConsoleBlockEntity be : ConsoleBlockEntity.clientLoaded()) {
            if (be.getLevel() != mc.level || be.isRemoved()
                    || Vec3.atCenterOf(be.getBlockPos()).distanceToSqr(eye) > 64) {
                continue;
            }
            float time = be.getLevel().getGameTime() + partialTick;
            Vec3 corner = Vec3.atLowerCornerOf(be.getBlockPos());
            for (ConsoleBlockEntity.Shown c : be.shown()) {
                if (c.model().equals(Control.MONITOR)) {
                    ConsoleModel.ControlPart part = model.controls.get(ConsoleModel.MONITOR);
                    if (part != null) {
                        Matrix4f at = new Matrix4f().translation((float) corner.x, (float) corner.y,
                                (float) corner.z).mul(model.monitorMatrix(turn(model, be)));
                        best = nearer(best, test(be, c, "screen", part.box(), at, eye, end));
                    }
                    continue;
                }
                Optional<ControlModels.Model> m = ControlModels.get(c.model());
                Matrix4f place = model.placement(c);
                if (m.isEmpty() || place == null) {
                    continue;
                }
                Matrix4f at = new Matrix4f().translation((float) corner.x, (float) corner.y, (float) corner.z)
                        .mul(place);
                ControlModels.Pose pose = ControlModels.pose(m.get(), be, c, time);
                boolean clickable = false;
                for (ControlModels.Action a : m.get().actions()) {
                    if (a.click() == null || !m.get().parts().containsKey(a.click())) {
                        continue;
                    }
                    clickable = true;
                    AABB box = ControlModels.box(m.get().parts().get(a.click()), pose.of(a.click()));
                    best = nearer(best, test(be, c, a.click(), box, at, eye, end));
                }
                if (!clickable) {
                    // nothing to click (a gauge): the whole control, so Jade can still name it
                    AABB all = null;
                    for (Map.Entry<String, ControlModels.Part> p : m.get().parts().entrySet()) {
                        AABB b = ControlModels.box(p.getValue(), pose.of(p.getKey()));
                        all = all == null ? b : b == null ? all : all.minmax(b);
                    }
                    best = nearer(best, test(be, c, "", all, at, eye, end));
                }
            }
            // the time rotor's column: clicking it turns the monitor toward the player
            AABB column = new AABB(model.pivotX - COLUMN_RADIUS, 1.0, model.pivotZ - COLUMN_RADIUS,
                    model.pivotX + COLUMN_RADIUS, 3.5, model.pivotZ + COLUMN_RADIUS).move(corner);
            Optional<Vec3> hit = column.clip(eye, end);
            if (hit.isPresent()) {
                best = nearer(best, new Aim(be, null, "", hit.get(), hit.get().distanceTo(eye)));
            }
        }
        return Optional.ofNullable(best);
    }

    /** How far the monitor is turned as drawn now (it eases toward where it was last turned). */
    static float turn(ConsoleModel model, ConsoleBlockEntity be) {
        return Float.isNaN(be.drawnAngle) ? model.monitorTurn(be.monitorAngle()) : be.drawnAngle;
    }

    /** The ray against a box in a control's own space ({@code toWorld} maps that space to the world). */
    private static Aim test(ConsoleBlockEntity be, ConsoleBlockEntity.Shown c, String click, AABB box,
            Matrix4f toWorld, Vec3 eye, Vec3 end) {
        if (box == null) {
            return null;
        }
        Matrix4f toLocal = new Matrix4f(toWorld).invert();
        Optional<Vec3> local = box.inflate(0.01).clip(apply(toLocal, eye), apply(toLocal, end));
        if (local.isEmpty()) {
            return null;
        }
        Vec3 hit = apply(toWorld, local.get());
        return new Aim(be, c, click, hit, hit.distanceTo(eye));
    }

    private static Vec3 apply(Matrix4f m, Vec3 p) {
        Vector4f v = m.transform(new Vector4f((float) p.x, (float) p.y, (float) p.z, 1));
        return new Vec3(v.x, v.y, v.z);
    }

    private static Aim nearer(Aim a, Aim b) {
        return b == null ? a : a == null || b.distance() < a.distance() ? b : a;
    }
}
