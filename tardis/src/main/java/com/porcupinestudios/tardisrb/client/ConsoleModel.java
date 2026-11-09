package com.porcupinestudios.tardisrb.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.porcupinestudios.tardisrb.TardisRB;
import com.porcupinestudios.tardisrb.block.ConsoleBlockEntity;
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
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The Capaldi console room's models from {@code assets/tardis_rb/console/capaldi.json} (made by
 * {@code tools/capaldi.py}): quads in blocks from the console block's lower corner. Parts: {@code body} (the console
 * and its pedestal down to the lower deck, one plain colour: Aaron 2026-11-08, a blank slate for the controls),
 * {@code ctl_screen_1} (the monitor, with its click box), {@code rotor_glass}, {@code rotor_column},
 * {@code rotor_spinners}, {@code rotor_spinner_mid} and {@code arches}. Also each panel's frame, where the small
 * control models stand ({@link #placement}), and how the monitor stands over the side it is turned to
 * ({@link #monitorMatrix}). Loaded on first use and again after a resource reload, so a resource pack can replace it.
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
    /** A panel's frame: the middle of its outer edge on its top, and its right, normal (up) and down-slope axes. */
    public record Panel(Vec3 origin, Vec3 right, Vec3 normal, Vec3 down) {}

    public final List<Panel> panels = new ArrayList<>();
    /** How the monitor stands once raised: tilted toward upright (degrees), lifted and moved in toward the rotor. */
    private final float monitorTilt;
    private final float monitorLift;
    private final float monitorInward;
    /** The monitor's glass, if the model has a monitor. */
    public final Optional<Glass> glass;
    /** Which way the monitor faces in the model, degrees ({@code atan2(z, x)} from the rotor axis). */
    public final float monitorHome;

    private ConsoleModel(JsonObject root) {
        JsonObject ps = root.has("parts") ? root.getAsJsonObject("parts") : new JsonObject();
        for (Map.Entry<String, JsonElement> e : ps.entrySet()) {
            List<Quad> quads = new ArrayList<>();
            for (JsonElement q : e.getValue().getAsJsonArray()) {
                quads.add(quad(q.getAsJsonObject()));
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
        if (root.has("panels")) {
            for (JsonElement el : root.getAsJsonArray("panels")) {
                JsonObject p = el.getAsJsonObject();
                panels.add(new Panel(vec(p, "origin"), vec(p, "right"), vec(p, "normal"), vec(p, "down")));
            }
        }
        JsonObject mon = root.has("monitor") ? root.getAsJsonObject("monitor") : new JsonObject();
        monitorTilt = mon.has("tilt") ? mon.get("tilt").getAsFloat() : 0;
        monitorLift = mon.has("lift") ? mon.get("lift").getAsFloat() : 0;
        monitorInward = mon.has("inward") ? mon.get("inward").getAsFloat() : 0;
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

    /**
     * How far to turn the monitor from where the model has it, as a Y rotation in degrees round the rotor axis, for it
     * to face {@code angle} ({@code atan2(z, x)}; NaN = home).
     */
    public float monitorTurn(float angle) {
        return Float.isNaN(angle) ? 0 : wrap(monitorHome - angle);
    }

    /**
     * Where the monitor is drawn when turned {@code turn} degrees ({@link #monitorTurn}): it stands up off its panel
     * (tilted toward upright about its middle, lifted and moved in toward the rotor, so the controls under it stay
     * visible) and turns round the rotor axis. Maps the model's monitor quads (block corner space) to where they are.
     */
    public Matrix4f monitorMatrix(float turn) {
        Matrix4f m = new Matrix4f().translate(pivotX, 0, pivotZ).rotateY((float) Math.toRadians(turn))
                .translate(-pivotX, 0, -pivotZ);
        ControlPart monitor = controls.get(MONITOR);
        if (monitor == null) {
            return m;
        }
        Vec3 c = monitor.box().getCenter();
        Vec3 out = new Vec3(c.x - pivotX, 0, c.z - pivotZ).normalize();
        // up x out: tilting about it turns the glass's normal toward the outside
        Vector3f axis = new Vector3f((float) out.z, 0, (float) -out.x);
        return m.translate((float) (-out.x * monitorInward), monitorLift, (float) (-out.z * monitorInward))
                .translate((float) c.x, (float) c.y, (float) c.z).rotate((float) Math.toRadians(monitorTilt), axis)
                .translate((float) -c.x, (float) -c.y, (float) -c.z);
    }

    /**
     * Where a small control is drawn: on its panel's top at {@code u} (right of the middle) and {@code v} (up the slope
     * from the outer edge), standing along the panel's normal with the operator's side down the slope, turned
     * {@code turn} degrees and scaled. Maps the control model's space to block corner space. Null if no such panel.
     */
    public Matrix4f placement(ConsoleBlockEntity.Shown c) {
        if (c.panel() < 0 || c.panel() >= panels.size()) {
            return null;
        }
        Panel p = panels.get(c.panel());
        Vec3 at = p.origin().add(p.right().scale(c.u())).subtract(p.down().scale(c.v()));
        Vec3 r = p.right();
        Vec3 n = p.normal();
        Vec3 d = p.down();
        return new Matrix4f((float) r.x, (float) r.y, (float) r.z, 0, (float) n.x, (float) n.y, (float) n.z, 0,
                (float) d.x, (float) d.y, (float) d.z, 0, (float) at.x, (float) at.y, (float) at.z, 1)
                .rotateY((float) Math.toRadians(c.turn())).scale(c.scale());
    }

    /** An angle in degrees brought into -180..180. */
    public static float wrap(float degrees) {
        float d = degrees % 360;
        return d > 180 ? d - 360 : d < -180 ? d + 360 : d;
    }

    /** One quad from the model files' format: {@code v} = four vertices of x y z u v, {@code n} its normal. */
    static Quad quad(JsonObject q) {
        JsonArray verts = q.getAsJsonArray("v");
        float[][] v = new float[4][];
        for (int i = 0; i < 4; i++) {
            v[i] = floats(verts.get(i).getAsJsonArray());
        }
        return new Quad(v, floats(q.getAsJsonArray("n")));
    }

    private static Vec3 vec(JsonObject o, String key) {
        float[] f = floats(o.getAsJsonArray(key));
        return new Vec3(f[0], f[1], f[2]);
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
