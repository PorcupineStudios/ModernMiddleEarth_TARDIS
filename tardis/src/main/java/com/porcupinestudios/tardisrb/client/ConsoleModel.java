package com.porcupinestudios.tardisrb.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.porcupinestudios.tardisrb.TardisRB;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Capaldi console room's models from {@code assets/tardis_rb/console/capaldi.json} (made by
 * {@code tools/capaldi.py}): quads in blocks from the console block's lower corner. Parts: {@code body} (the console
 * and its pedestal down to the lower deck), one {@code ctl_<name>} per control, {@code rotor_glass},
 * {@code rotor_column}, {@code rotor_spinners}, {@code rotor_spinner_mid} and {@code arches}. Each control has a click
 * box. Loaded on first use and again after a resource reload, so a resource pack can replace it.
 */
public final class ConsoleModel {
    private static final ResourceLocation FILE = TardisRB.id("console/capaldi.json");
    private static ConsoleModel loaded;

    /** One quad: four vertices of x y z u v, and its normal. */
    public record Quad(float[][] v, float[] n) {}

    /** A control on the model: its kind (lever, slider, dial...), its panel 0-5 and where it can be clicked. */
    public record ControlPart(String kind, int panel, AABB box) {}

    /**
     * The monitor's glass: its centre, the way out of the glass ({@code normal}), the text's up and right directions,
     * and its size along them, all from the console block's corner.
     */
    public record Glass(Vec3 centre, Vec3 normal, Vec3 up, Vec3 right, double width, double height) {}

    /** The control part that is the monitor; it turns round the rotor to face whoever clicks the column. */
    public static final String MONITOR = "screen_1";

    public final Map<String, List<Quad>> parts = new HashMap<>();
    public final Map<String, ControlPart> controls = new HashMap<>();
    /** The time rotor's axis (x, z) from the block's corner. */
    public final float pivotX;
    public final float pivotZ;
    /** The monitor's glass, if the model has a monitor. */
    public final Optional<Glass> glass;
    /** Which way the monitor faces in the model, degrees ({@code atan2(z, x)} from the rotor axis). */
    public final float monitorHome;

    private ConsoleModel(JsonObject root) {
        JsonObject ps = root.has("parts") ? root.getAsJsonObject("parts") : new JsonObject();
        for (Map.Entry<String, JsonElement> e : ps.entrySet()) {
            List<Quad> quads = new ArrayList<>();
            for (JsonElement q : e.getValue().getAsJsonArray()) {
                JsonArray verts = q.getAsJsonObject().getAsJsonArray("v");
                float[][] v = new float[4][];
                for (int i = 0; i < 4; i++) {
                    v[i] = floats(verts.get(i).getAsJsonArray());
                }
                quads.add(new Quad(v, floats(q.getAsJsonObject().getAsJsonArray("n"))));
            }
            parts.put(e.getKey(), quads);
        }
        JsonObject cs = root.has("controls") ? root.getAsJsonObject("controls") : new JsonObject();
        for (Map.Entry<String, JsonElement> e : cs.entrySet()) {
            JsonObject c = e.getValue().getAsJsonObject();
            float[] b = floats(c.getAsJsonArray("box"));
            controls.put(e.getKey(), new ControlPart(c.get("kind").getAsString(), c.get("panel").getAsInt(),
                    new AABB(b[0], b[1], b[2], b[3], b[4], b[5])));
        }
        float[] pivot = root.has("pivot") ? floats(root.getAsJsonArray("pivot")) : new float[] {0.5F, 0, 0.5F};
        pivotX = pivot[0];
        pivotZ = pivot[2];
        glass = findGlass(part("ctl_" + MONITOR));
        ControlPart monitor = controls.get(MONITOR);
        monitorHome = monitor == null ? 0 : (float) Math.toDegrees(Math.atan2(monitor.box().getCenter().z - pivotZ,
                monitor.box().getCenter().x - pivotX));
    }

    /** The glass is the highest of the monitor's big faces (its slab's top and its inset glass). */
    private static Optional<Glass> findGlass(List<Quad> quads) {
        Quad best = null;
        double bestY = -Double.MAX_VALUE;
        double maxArea = 0;
        for (Quad q : quads) {
            maxArea = Math.max(maxArea, area(q));
        }
        for (Quad q : quads) {
            double y = (q.v()[0][1] + q.v()[1][1] + q.v()[2][1] + q.v()[3][1]) / 4;
            if (area(q) > maxArea * 0.4 && y > bestY) {
                bestY = y;
                best = q;
            }
        }
        if (best == null) {
            return Optional.empty();
        }
        Vec3[] v = new Vec3[4];
        Vec3 centre = Vec3.ZERO;
        for (int i = 0; i < 4; i++) {
            v[i] = new Vec3(best.v()[i][0], best.v()[i][1], best.v()[i][2]);
            centre = centre.add(v[i].scale(0.25));
        }
        Vec3 normal = v[1].subtract(v[0]).cross(v[3].subtract(v[0])).normalize();
        if (normal.y < 0) {
            normal = normal.scale(-1);
        }
        // text stands up toward the glass's high edge (the viewer is outside, looking in and down)
        Vec3 up = new Vec3(0, 1, 0).subtract(normal.scale(normal.y)).normalize();
        Vec3 right = up.cross(normal).normalize();
        double w = 0;
        double h = 0;
        for (Vec3 p : v) {
            w = Math.max(w, Math.abs(p.subtract(centre).dot(right)) * 2);
            h = Math.max(h, Math.abs(p.subtract(centre).dot(up)) * 2);
        }
        return Optional.of(new Glass(centre, normal, up, right, w, h));
    }

    private static double area(Quad q) {
        Vec3 a = new Vec3(q.v()[1][0] - q.v()[0][0], q.v()[1][1] - q.v()[0][1], q.v()[1][2] - q.v()[0][2]);
        Vec3 b = new Vec3(q.v()[3][0] - q.v()[0][0], q.v()[3][1] - q.v()[0][1], q.v()[3][2] - q.v()[0][2]);
        return a.cross(b).length();
    }

    /** How high the monitor rises once turned off its own panel, so it clears the controls on the others. */
    public static final double MONITOR_LIFT = 0.5;

    /**
     * How far to turn the monitor from where the model has it, as a {@code PoseStack} Y rotation in degrees round
     * the rotor axis, for it to face {@code angle} ({@code atan2(z, x)}; NaN = home).
     */
    public float monitorTurn(float angle) {
        return Float.isNaN(angle) ? 0 : wrap(monitorHome - angle);
    }

    /** How high the monitor is lifted when turned this far: on its panel at home, risen once off it. */
    public static double monitorLift(float turn) {
        return MONITOR_LIFT * Math.min(1, Math.abs(turn) / 15);
    }

    /** The monitor's click box after turning it {@code turn} degrees and lifting it. */
    public AABB monitorBox(AABB box, float turn) {
        Vec3 c = box.getCenter();
        double a = Math.toRadians(turn);
        double x = c.x - pivotX;
        double z = c.z - pivotZ;
        // the same turn as Axis.YP: x' = x cos a + z sin a, z' = -x sin a + z cos a
        double nx = pivotX + x * Math.cos(a) + z * Math.sin(a);
        double nz = pivotZ - x * Math.sin(a) + z * Math.cos(a);
        double half = Math.max(box.getXsize(), box.getZsize()) / 2;
        double lift = monitorLift(turn);
        return new AABB(nx - half, box.minY + lift, nz - half, nx + half, box.maxY + lift, nz + half);
    }

    /** An angle in degrees brought into -180..180. */
    public static float wrap(float degrees) {
        float d = degrees % 360;
        return d > 180 ? d - 360 : d < -180 ? d + 360 : d;
    }

    public List<Quad> part(String name) {
        return parts.getOrDefault(name, List.of());
    }

    private static float[] floats(JsonArray a) {
        float[] out = new float[a.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = a.get(i).getAsFloat();
        }
        return out;
    }

    /** The models, loading them if needed; empty if the file is missing or broken (logged once). */
    public static ConsoleModel get() {
        if (loaded == null) {
            try (Reader r = Minecraft.getInstance().getResourceManager().openAsReader(FILE)) {
                loaded = new ConsoleModel(JsonParser.parseReader(r).getAsJsonObject());
            } catch (Exception e) {
                TardisRB.LOG.error("Could not read the console model {}", FILE, e);
                loaded = new ConsoleModel(new JsonObject());
            }
        }
        return loaded;
    }

    /** Forgets the models so the next frame reads them again (after a resource reload). */
    public static void reset() {
        loaded = null;
    }
}
