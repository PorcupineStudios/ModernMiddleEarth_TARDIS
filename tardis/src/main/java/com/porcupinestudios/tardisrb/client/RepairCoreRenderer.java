package com.porcupinestudios.tardisrb.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.porcupinestudios.tardisrb.TardisRB;
import com.porcupinestudios.tardisrb.block.RepairCoreBlock;
import com.porcupinestudios.tardisrb.block.RepairCoreBlockEntity;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;

/**
 * The repair core (Aaron 2026-11-10; {@link RepairCoreBlock}): a hexagonal column under the console, from the lower
 * deck's floor up to the grating, with a plinth, a collar and six blank panels. Each panel's slots come from
 * {@code assets/tardis_rb/core/layout.json} ({@link #layout}) and are drawn as plain insets for now: a later state
 * (installed upgrades, hull, oxygen) only has to pick a colour or fill per slot id ({@link #slot}).
 */
final class RepairCoreRenderer implements BlockEntityRenderer<RepairCoreBlockEntity> {
    private static final ResourceLocation WHITE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    private static final ResourceLocation LAYOUT = TardisRB.id("core/layout.json");
    /** The console's rotor axis in its block (tools/capaldi/README.md), so the core lines up with the column. */
    private static final double AXIS_X = 0.5;
    private static final double AXIS_Z = 0.25;
    /** Flat side to flat side 2 x APOTHEM: it fits the 3 x 3 cells round the axis. */
    private static final double APOTHEM = 1.2;
    private static final double TOP = 2.0;
    private static final double PANEL_LOW = 0.14;
    private static final double PANEL_HIGH = 1.82;

    private static Map<Integer, List<Slot>> layout;

    RepairCoreRenderer(BlockEntityRendererProvider.Context context) {}

    /** One blank slot on a face: x and y 0..1 across and up the face's panel. */
    record Slot(String id, String kind, double x0, double x1, double y0, double y1) {}

    static void reset() {
        layout = null;
    }

    /** The slots per face, read once from the layout file (again after a resource reload). */
    static Map<Integer, List<Slot>> layout() {
        if (layout == null) {
            Map<Integer, List<Slot>> out = new HashMap<>();
            try (Reader r = Minecraft.getInstance().getResourceManager().openAsReader(LAYOUT)) {
                for (JsonElement f : JsonParser.parseReader(r).getAsJsonObject().getAsJsonArray("faces")) {
                    JsonObject face = f.getAsJsonObject();
                    List<Slot> slots = out.computeIfAbsent(face.get("face").getAsInt(), k -> new ArrayList<>());
                    for (JsonElement s : face.getAsJsonArray("slots")) {
                        JsonObject o = s.getAsJsonObject();
                        var x = o.getAsJsonArray("x");
                        var y = o.getAsJsonArray("y");
                        slots.add(new Slot(o.get("id").getAsString(), o.get("kind").getAsString(),
                                x.get(0).getAsDouble(), x.get(1).getAsDouble(), y.get(0).getAsDouble(),
                                y.get(1).getAsDouble()));
                    }
                }
            } catch (Exception e) {
                TardisRB.LOG.error("[core] can't read {}: {}", LAYOUT, e.toString());
            }
            layout = out;
        }
        return layout;
    }

    @Override
    public void render(RepairCoreBlockEntity core, float partialTick, PoseStack pose, MultiBufferSource buffers,
            int light, int overlay) {
        VertexConsumer vc = buffers.getBuffer(RenderType.entitySolid(WHITE));
        PoseStack.Pose p = pose.last();
        prism(vc, p, APOTHEM + 0.1, 0, PANEL_LOW - 0.02, 0xFF464A52, light); // plinth
        prism(vc, p, APOTHEM, PANEL_LOW - 0.02, PANEL_HIGH + 0.02, 0xFF80848C, light); // body
        prism(vc, p, APOTHEM + 0.06, PANEL_HIGH + 0.02, TOP, 0xFF5A5E66, light); // collar
        cap(vc, p, APOTHEM + 0.06, TOP, 0xFF3C4048, light);
        Map<Integer, List<Slot>> slots = layout();
        for (int k = 0; k < 6; k++) {
            // the panel, then a thin rib down each edge, then the slots
            rect(vc, p, k, 0.05, 0.95, 0, 1, APOTHEM + 0.002, 0xFF646870, light);
            rect(vc, p, k, 0, 0.035, 0, 1, APOTHEM + 0.004, 0xFF6E727A, light);
            rect(vc, p, k, 0.965, 1, 0, 1, APOTHEM + 0.004, 0xFF6E727A, light);
            for (Slot s : slots.getOrDefault(k, List.of())) {
                rect(vc, p, k, s.x0(), s.x1(), s.y0(), s.y1(), APOTHEM + 0.004, 0xFF9AA0AA, light);
                double in = 0.02;
                rect(vc, p, k, s.x0() + in, s.x1() - in, s.y0() + in * 0.6, s.y1() - in * 0.6, APOTHEM + 0.006,
                        slot(core, s), light);
            }
        }
    }

    /**
     * The colour a slot is drawn in: blank for now, by kind. This is where a slot will show state later (a hull bar's
     * fill, a lit upgrade lamp), looked up by {@link Slot#id}.
     */
    private static int slot(RepairCoreBlockEntity core, Slot s) {
        return switch (s.kind()) {
            case "screen" -> 0xFF181C22;
            case "bar" -> 0xFF282C34;
            default -> 0xFF343840;
        };
    }

    /** Face k's outward normal: face 0 looks at +z (the main door), the rest anticlockwise seen from above. */
    private static Vec3 normal(int k) {
        double a = Math.toRadians(90 - 60 * k);
        return new Vec3(Math.cos(a), 0, Math.sin(a));
    }

    /** The six sides of a hexagonal prism round the axis, {@code apothem} out, from y0 to y1. */
    private static void prism(VertexConsumer vc, PoseStack.Pose p, double apothem, double y0, double y1, int argb,
            int light) {
        for (int k = 0; k < 6; k++) {
            quadOnFace(vc, p, k, 0, 1, y0, y1, apothem, argb, light);
        }
    }

    /** A rectangle on face k's panel (x, y 0..1 of the panel), {@code out} from the axis. */
    private static void rect(VertexConsumer vc, PoseStack.Pose p, int k, double x0, double x1, double y0, double y1,
            double out, int argb, int light) {
        double h0 = PANEL_LOW + (PANEL_HIGH - PANEL_LOW) * y0;
        double h1 = PANEL_LOW + (PANEL_HIGH - PANEL_LOW) * y1;
        quadOnFace(vc, p, k, x0, x1, h0, h1, out, argb, light);
    }

    /** A quad on face k between x0..x1 across it (0 its left edge, 1 its right, seen from outside) and heights y0..y1. */
    private static void quadOnFace(VertexConsumer vc, PoseStack.Pose p, int k, double x0, double x1, double y0,
            double y1, double out, int argb, int light) {
        Vec3 n = normal(k);
        Vec3 right = new Vec3(n.z, 0, -n.x);
        double half = out / Math.sqrt(3); // half the side's width at that distance
        Vec3 mid = new Vec3(AXIS_X, 0, AXIS_Z).add(n.scale(out));
        Vec3 a = mid.add(right.scale((x0 * 2 - 1) * half));
        Vec3 b = mid.add(right.scale((x1 * 2 - 1) * half));
        // seen from outside: bottom left, bottom right, top right, top left (anticlockwise)
        vertex(vc, p, a.x, y0, a.z, n, argb, light);
        vertex(vc, p, b.x, y0, b.z, n, argb, light);
        vertex(vc, p, b.x, y1, b.z, n, argb, light);
        vertex(vc, p, a.x, y1, a.z, n, argb, light);
    }

    /** The flat top, seen through the grating. */
    private static void cap(VertexConsumer vc, PoseStack.Pose p, double apothem, double y, int argb, int light) {
        Vec3 up = new Vec3(0, 1, 0);
        double r = apothem * 2 / Math.sqrt(3);
        for (int k = 0; k < 6; k++) {
            double a0 = Math.toRadians(90 - 60 * k - 30);
            double a1 = Math.toRadians(90 - 60 * k + 30);
            double x0 = AXIS_X + Math.cos(a0) * r;
            double z0 = AXIS_Z + Math.sin(a0) * r;
            double x1 = AXIS_X + Math.cos(a1) * r;
            double z1 = AXIS_Z + Math.sin(a1) * r;
            // both windings, so it shows whichever way round it is
            vertex(vc, p, AXIS_X, y, AXIS_Z, up, argb, light);
            vertex(vc, p, x0, y, z0, up, argb, light);
            vertex(vc, p, x1, y, z1, up, argb, light);
            vertex(vc, p, x1, y, z1, up, argb, light);
            vertex(vc, p, AXIS_X, y, AXIS_Z, up, argb, light);
            vertex(vc, p, x1, y, z1, up, argb, light);
            vertex(vc, p, x0, y, z0, up, argb, light);
            vertex(vc, p, x0, y, z0, up, argb, light);
        }
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose p, double x, double y, double z, Vec3 n, int argb,
            int light) {
        vc.addVertex(p, (float) x, (float) y, (float) z).setColor(argb).setUv(0.5F, 0.5F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, (float) n.x, (float) n.y, (float) n.z);
    }

    @Override
    public AABB getRenderBoundingBox(RepairCoreBlockEntity core) {
        return new AABB(core.getBlockPos()).inflate(2);
    }

    /** No block outline on the core: its cells are plain cubes, the column is round. */
    @EventBusSubscriber(modid = TardisRB.MOD_ID, value = Dist.CLIENT)
    static final class Outline {
        private Outline() {}

        @SubscribeEvent
        static void hide(RenderHighlightEvent.Block event) {
            var level = Minecraft.getInstance().level;
            var block = level == null ? null : level.getBlockState(event.getTarget().getBlockPos()).getBlock();
            if (block instanceof RepairCoreBlock || block instanceof RepairCoreBlock.Casing) {
                event.setCanceled(true);
            }
        }
    }
}
