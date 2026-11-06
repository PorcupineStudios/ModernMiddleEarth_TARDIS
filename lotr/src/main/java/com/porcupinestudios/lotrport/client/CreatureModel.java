package com.porcupinestudios.lotrport.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.porcupinestudios.lotrport.npc.LotrNpc;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;

/**
 * One of LOTR's creature bodies (warg, troll, spider, ent, Balrog...), built from {@code npc_model/<name>.json}
 * ({@code converter/entity_models.py}), and drawn the way LOTR drew it:
 * <ul>
 * <li>its animation is LOTR's setRotationAngles ({@link CreatureAnim});</li>
 * <li>its parts are drawn as LOTR's render() drew them: each draw call with its own GL transforms (a two-headed troll's
 * heads tilted 15 degrees apart, an ent's extra branches), colour and the condition it was drawn under;</li>
 * <li>its renderer's preRenderCallback ({@link #applyPre}: troll and spider size, the Balrog's charge lean, a
 * wraith's fade) and extra passes ({@link Renderer#layers}: troll outfits, armour and weapons).</li>
 * </ul>
 * Inputs come from the NPC: walk, look, age, attack swing, and its synced creature state.
 */
public class CreatureModel extends EntityModel<LotrNpc> {
    private final ModelPart root;
    private final Map<String, ModelPart> named = new LinkedHashMap<>();
    private final Map<String, List<String>> paths = new LinkedHashMap<>();
    private final Map<ModelPart, PartPose> rest = new LinkedHashMap<>();
    private final Set<ModelPart> drawnRoots = new HashSet<>();
    private final CreatureAnim anim;
    private final CreatureAnim.Compiler compiler;
    private final List<Draw> draws = new ArrayList<>();
    final List<Renderer> renderers = new ArrayList<>();
    final CreatureAnim.Ctx ctx = new CreatureAnim.Ctx();
    /** The renderer's colour for this frame (the wraith's fade), multiplied into every draw. */
    private final float[] preColor = { 1, 1, 1, 1 };
    private boolean translucent;

    /** One draw call of LOTR's render(): a part under transforms, in a colour, when its condition holds. */
    record Draw(ModelPart part, List<Op> ops, CreatureAnim.Expr[] color, boolean blend, CreatureAnim.Expr cond) {}

    /** A GL transform: translate, rotate, scale, attach (move into a part's frame), or a group under a condition. */
    record Op(String kind, CreatureAnim.Expr[] args, String part, CreatureAnim.Expr cond, List<Op> group) {}

    /** One of LOTR's renderers for this model: its preRenderCallback and extra passes. */
    record Renderer(List<Op> pre, CreatureAnim.Expr[] color, boolean blend, List<JsonObject> layers,
            List<List<Draw>> layerDraws) {}

    public CreatureModel(JsonObject def) {
        MeshDefinition mesh = new MeshDefinition();
        JsonObject json = def.getAsJsonObject("parts");
        for (Map.Entry<String, JsonElement> e : json.entrySet()) {
            add(mesh.getRoot(), e.getKey(), e.getValue().getAsJsonObject(), List.of(e.getKey()), paths);
        }
        JsonArray size = def.getAsJsonArray("texture");
        root = LayerDefinition.create(mesh, size.get(0).getAsInt(), size.get(1).getAsInt()).bakeRoot();
        root.getAllParts().forEach(p -> rest.put(p, p.storePose()));
        paths.forEach((name, path) -> {
            ModelPart p = root;
            for (String step : path) {
                p = p.getChild(step);
            }
            named.put(name, p);
        });
        Set<String> hidden = new HashSet<>();
        if (def.has("hidden")) {
            def.getAsJsonArray("hidden").forEach(h -> hidden.add(h.getAsString()));
        }
        for (String name : json.keySet()) {
            root.getChild(name).visible = !hidden.contains(name);
        }
        compiler = new CreatureAnim.Compiler(def.has("defs") ? def.getAsJsonArray("defs")
                : def.has("anim") && def.getAsJsonObject("anim").has("defs")
                        ? def.getAsJsonObject("anim").getAsJsonArray("defs") : null);
        anim = def.has("anim") ? new CreatureAnim(def.getAsJsonObject("anim"), named, compiler) : null;
        if (anim != null && def.getAsJsonObject("anim").has("draws")) {
            for (JsonElement d : def.getAsJsonObject("anim").getAsJsonArray("draws")) {
                Draw draw = draw(d.getAsJsonObject());
                if (draw != null) {
                    draws.add(draw);
                    drawnRoots.add(draw.part);
                    translucent |= draw.blend;
                }
            }
        }
        if (def.has("renderers")) {
            for (JsonElement r : def.getAsJsonArray("renderers")) {
                renderers.add(renderer(r.getAsJsonObject()));
            }
        }
        ctx.parts = named;
        ctx.rest = rest;
    }

    private Renderer renderer(JsonObject r) {
        List<Op> pre = List.of();
        CreatureAnim.Expr[] color = null;
        boolean blend = false;
        if (r.has("pre")) {
            JsonObject p = r.getAsJsonObject("pre");
            pre = ops(p.get("ops"));
            color = colors(p.get("color"));
            blend = p.has("blend") && p.get("blend").getAsBoolean();
        }
        List<JsonObject> layers = new ArrayList<>();
        List<List<Draw>> layerDraws = new ArrayList<>();
        if (r.has("layers")) {
            for (JsonElement l : r.getAsJsonArray("layers")) {
                JsonObject layer = l.getAsJsonObject();
                layers.add(layer);
                List<Draw> ds = new ArrayList<>();
                if (layer.has("draws")) {
                    for (JsonElement d : layer.getAsJsonArray("draws")) {
                        Draw draw = draw(d.getAsJsonObject());
                        if (draw != null) {
                            ds.add(draw);
                        }
                    }
                }
                layerDraws.add(ds);
            }
        }
        return new Renderer(pre, color, blend, layers, layerDraws);
    }

    boolean translucent(int renderer) {
        if (translucent) {
            return true;
        }
        if (renderer >= 0 && renderer < renderers.size()) {
            Renderer r = renderers.get(renderer);
            return r.blend || r.color != null;
        }
        return false;
    }

    private Draw draw(JsonObject d) {
        ModelPart part = named.get(d.get("part").getAsString());
        if (part == null) {
            return null;
        }
        return new Draw(part, ops(d.get("ops")), colors(d.get("color")),
                d.has("blend") && d.get("blend").getAsBoolean(), compiler.expr(d.get("cond")));
    }

    private CreatureAnim.Expr[] colors(JsonElement e) {
        if (e == null) {
            return null;
        }
        JsonArray a = compiler.raw(e).getAsJsonArray();
        CreatureAnim.Expr[] out = new CreatureAnim.Expr[4];
        for (int i = 0; i < 4; i++) {
            out[i] = i < a.size() ? compiler.expr(a.get(i)) : c -> 1f;
        }
        return out;
    }

    private List<Op> ops(JsonElement e) {
        List<Op> out = new ArrayList<>();
        if (e == null) {
            return out;
        }
        for (JsonElement o : compiler.raw(e).getAsJsonArray()) {
            JsonArray a = compiler.raw(o).getAsJsonArray();
            String kind = a.get(0).getAsString();
            switch (kind) {
                case "attach" -> out.add(new Op(kind, null, a.get(1).getAsString(), null, null));
                case "when" -> out.add(new Op(kind, null, null, compiler.expr(a.get(1)), ops(a.get(2))));
                default -> {
                    CreatureAnim.Expr[] args = new CreatureAnim.Expr[a.size() - 1];
                    for (int i = 1; i < a.size(); i++) {
                        args[i - 1] = compiler.expr(a.get(i));
                    }
                    out.add(new Op(kind, args, null, null, null));
                }
            }
        }
        return out;
    }

    void apply(List<Op> ops, PoseStack pose) {
        for (Op op : ops) {
            switch (op.kind) {
                case "translate" -> pose.translate(op.args[0].eval(ctx), op.args[1].eval(ctx), op.args[2].eval(ctx));
                case "scale" -> pose.scale(op.args[0].eval(ctx), op.args[1].eval(ctx), op.args[2].eval(ctx));
                case "rotate" -> {
                    float x = op.args[1].eval(ctx);
                    float y = op.args[2].eval(ctx);
                    float z = op.args[3].eval(ctx);
                    float len = Mth.sqrt(x * x + y * y + z * z);
                    if (len > 0) {
                        pose.mulPose(new Quaternionf().rotationAxis(op.args[0].eval(ctx) * Mth.DEG_TO_RAD,
                                x / len, y / len, z / len));
                    }
                }
                case "attach" -> {
                    ModelPart p = named.get(op.part);
                    if (p != null) {
                        p.translateAndRotate(pose);
                    }
                }
                case "when" -> {
                    if (op.cond.eval(ctx) != 0) {
                        apply(op.group, pose);
                    }
                }
                default -> { }
            }
        }
    }

    /** The renderer's preRenderCallback for this NPC (troll/spider size, Balrog lean, wraith fade). */
    void applyPre(int renderer, PoseStack pose) {
        preColor[0] = preColor[1] = preColor[2] = preColor[3] = 1f;
        if (renderer < 0 || renderer >= renderers.size()) {
            return;
        }
        Renderer r = renderers.get(renderer);
        apply(r.pre, pose);
        if (r.color != null) {
            for (int i = 0; i < 4; i++) {
                preColor[i] = Mth.clamp(r.color[i].eval(ctx), 0f, 1f);
            }
        }
    }

    private static void add(PartDefinition parent, String name, JsonObject part, List<String> path,
            Map<String, List<String>> paths) {
        paths.put(part.has("name") ? part.get("name").getAsString() : name, path);
        CubeListBuilder cubes = CubeListBuilder.create();
        for (JsonElement c : part.getAsJsonArray("cubes")) {
            JsonObject cube = c.getAsJsonObject();
            JsonArray o = cube.getAsJsonArray("origin");
            JsonArray s = cube.getAsJsonArray("size");
            JsonArray uv = cube.getAsJsonArray("uv");
            cubes.texOffs(uv.get(0).getAsInt(), uv.get(1).getAsInt()).mirror(cube.get("mirror").getAsBoolean())
                    .addBox(o.get(0).getAsFloat(), o.get(1).getAsFloat(), o.get(2).getAsFloat(),
                            s.get(0).getAsFloat(), s.get(1).getAsFloat(), s.get(2).getAsFloat(),
                            new CubeDeformation(cube.get("inflate").getAsFloat()));
        }
        JsonArray p = part.getAsJsonArray("pivot");
        JsonArray r = part.getAsJsonArray("rotation");
        PartDefinition def = parent.addOrReplaceChild(name, cubes, PartPose.offsetAndRotation(
                p.get(0).getAsFloat(), p.get(1).getAsFloat(), p.get(2).getAsFloat(),
                r.get(0).getAsFloat(), r.get(1).getAsFloat(), r.get(2).getAsFloat()));
        int n = 0;
        for (JsonElement child : part.getAsJsonArray("children")) {
            String childName = "child_" + n++;
            List<String> childPath = new ArrayList<>(path);
            childPath.add(childName);
            add(def, childName, child.getAsJsonObject(), childPath, paths);
        }
    }

    /** Sets this frame's inputs (also used by the renderer before {@link #applyPre}). */
    void prepare(LotrNpc npc, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
            float headPitch, float partial) {
        ctx.npc = npc;
        ctx.limb = limbSwing;
        ctx.amount = limbSwingAmount;
        ctx.age = ageInTicks;
        ctx.yaw = netHeadYaw;
        ctx.pitch = headPitch;
        ctx.swing = attackTime;
        ctx.partial = partial;
    }

    @Override
    public void setupAnim(LotrNpc npc, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
            float headPitch) {
        rest.forEach(ModelPart::loadPose);
        ctx.npc = npc;
        ctx.limb = limbSwing;
        ctx.amount = limbSwingAmount;
        ctx.age = ageInTicks;
        ctx.yaw = netHeadYaw;
        ctx.pitch = headPitch;
        ctx.swing = attackTime;
        if (anim != null) {
            if (anim.biped) {
                vanillaBiped(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
            }
            anim.apply(ctx);
        }
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int light, int overlay, int color) {
        renderDraws(draws, pose, buffer, light, overlay, color);
    }

    /** Draws a list of LOTR draw calls (or the whole model when LOTR's render() wasn't converted). */
    void renderDraws(List<Draw> list, PoseStack pose, VertexConsumer buffer, int light, int overlay, int color) {
        int tinted = tint(color, preColor);
        if (list.isEmpty()) {
            root.render(pose, buffer, light, overlay, tinted);
            return;
        }
        for (Draw d : list) {
            if (d.cond.eval(ctx) == 0) {
                continue;
            }
            pose.pushPose();
            apply(d.ops, pose);
            int c = tinted;
            if (d.color != null) {
                float[] dc = { Mth.clamp(d.color[0].eval(ctx), 0, 1), Mth.clamp(d.color[1].eval(ctx), 0, 1),
                        Mth.clamp(d.color[2].eval(ctx), 0, 1), Mth.clamp(d.color[3].eval(ctx), 0, 1) };
                c = tint(tinted, dc);
            }
            d.part.render(pose, buffer, light, overlay, c);
            pose.popPose();
        }
    }

    private static int tint(int argb, float[] rgba) {
        int a = (int) (FastColor.ARGB32.alpha(argb) * rgba[3]);
        int r = (int) (FastColor.ARGB32.red(argb) * rgba[0]);
        int g = (int) (FastColor.ARGB32.green(argb) * rgba[1]);
        int b = (int) (FastColor.ARGB32.blue(argb) * rgba[2]);
        return FastColor.ARGB32.color(a, r, g, b);
    }

    ModelPart part(String name) {
        return named.get(name);
    }

    /**
     * Vanilla's humanoid animation (1.7.10 ModelBiped, with ModelZombie's raised arms), which LOTR's skeleton model
     * inherits: head look, legs and arms swinging, arms held out.
     */
    private void vanillaBiped(float limb, float amount, float age, float yaw, float pitch) {
        ModelPart head = named.get("head");
        ModelPart hat = named.get("hat");
        ModelPart rightArm = named.get("right_arm");
        ModelPart leftArm = named.get("left_arm");
        ModelPart rightLeg = named.get("right_leg");
        ModelPart leftLeg = named.get("left_leg");
        if (head != null) {
            head.yRot = yaw * Mth.DEG_TO_RAD;
            head.xRot = pitch * Mth.DEG_TO_RAD;
            if (hat != null) {
                hat.yRot = head.yRot;
                hat.xRot = head.xRot;
            }
        }
        float walk = limb * 0.6662f;
        if (rightLeg != null && leftLeg != null) {
            rightLeg.xRot = Mth.cos(walk) * 1.4f * amount;
            leftLeg.xRot = Mth.cos(walk + Mth.PI) * 1.4f * amount;
        }
        if (rightArm != null && leftArm != null) {
            float swing = Mth.sin(attackTime * Mth.PI);
            float lift = Mth.sin((1f - (1f - attackTime) * (1f - attackTime)) * Mth.PI);
            rightArm.zRot = 0f;
            leftArm.zRot = 0f;
            rightArm.yRot = -(0.1f - swing * 0.6f);
            leftArm.yRot = 0.1f - swing * 0.6f;
            rightArm.xRot = -Mth.HALF_PI - (swing * 1.2f - lift * 0.4f);
            leftArm.xRot = -Mth.HALF_PI - (swing * 1.2f - lift * 0.4f);
            rightArm.zRot += Mth.cos(age * 0.09f) * 0.05f + 0.05f;
            leftArm.zRot -= Mth.cos(age * 0.09f) * 0.05f + 0.05f;
            rightArm.xRot += Mth.sin(age * 0.067f) * 0.05f;
            leftArm.xRot -= Mth.sin(age * 0.067f) * 0.05f;
        }
    }
}
