package com.porcupinestudios.lotrport.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.porcupinestudios.lotrport.npc.LotrNpc;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * LOTR's own animation for a creature model: its setRotationAngles, turned into expressions by
 * {@code converter/model_anim.py} ({@code npc_model/<name>.json} "anim"), compiled once and run every frame. Inputs
 * are the walk cycle, head look, age, the attack swing and the entity's state; the entity calls LOTR made are
 * answered from the port's NPC where there is an equivalent ({@link #entity}), else 0.
 */
final class CreatureAnim {
    /** One compiled expression. */
    interface Expr {
        float eval(Ctx c);
    }

    /** The inputs of one frame. */
    static final class Ctx {
        LotrNpc npc;
        float limb;
        float amount;
        float age;
        float yaw;
        float pitch;
        float swing;
        Map<String, ModelPart> parts;
        Map<ModelPart, PartPose> rest;
    }

    private record Set(ModelPart part, String field, Expr value) {}

    private final List<Set> sets = new ArrayList<>();
    final boolean biped;

    CreatureAnim(JsonObject anim, Map<String, ModelPart> parts) {
        biped = anim.has("biped") && anim.get("biped").getAsBoolean();
        List<Expr> defs = new ArrayList<>();
        if (anim.has("defs")) {
            for (JsonElement d : anim.getAsJsonArray("defs")) {
                defs.add(compile(d, defs));
            }
        }
        for (JsonElement e : anim.getAsJsonArray("set")) {
            JsonArray s = e.getAsJsonArray();
            ModelPart part = parts.get(s.get(0).getAsString());
            if (part != null) {
                sets.add(new Set(part, s.get(1).getAsString(), compile(s.get(2), defs)));
            }
        }
    }

    /** Evaluates every assignment against this frame (all reads see the rest pose or earlier writes' inputs). */
    void apply(Ctx c) {
        float[] values = new float[sets.size()];
        for (int i = 0; i < sets.size(); i++) {
            values[i] = sets.get(i).value.eval(c);
        }
        for (int i = 0; i < sets.size(); i++) {
            Set s = sets.get(i);
            float v = values[i];
            switch (s.field) {
                case "xRot" -> s.part.xRot = v;
                case "yRot" -> s.part.yRot = v;
                case "zRot" -> s.part.zRot = v;
                case "x" -> s.part.x = v;
                case "y" -> s.part.y = v;
                case "z" -> s.part.z = v;
                case "visible" -> s.part.visible = v > 0.5f;
                default -> { }
            }
        }
    }

    private static Expr compile(JsonElement e, List<Expr> defs) {
        if (e.isJsonPrimitive()) {
            if (e.getAsJsonPrimitive().isBoolean()) {
                float v = e.getAsBoolean() ? 1f : 0f;
                return c -> v;
            }
            float v = e.getAsFloat();
            return c -> v;
        }
        if (e.isJsonNull()) {
            return c -> 0f;
        }
        JsonArray a = e.getAsJsonArray();
        String op = a.get(0).getAsString();
        switch (op) {
            case "in":
                return input(a.get(1).getAsString());
            case "rest": {
                String part = a.get(1).getAsString();
                String field = a.get(2).getAsString();
                return c -> rest(c, part, field);
            }
            case "ref": {
                int i = a.get(1).getAsInt();
                Expr shared = defs.get(i);
                return shared;
            }
            default:
                break;
        }
        List<Expr> args = new ArrayList<>();
        for (int i = 1; i < a.size(); i++) {
            args.add(compile(a.get(i), defs));
        }
        Expr x = args.isEmpty() ? c -> 0f : args.get(0);
        Expr y = args.size() > 1 ? args.get(1) : c -> 0f;
        Expr z = args.size() > 2 ? args.get(2) : c -> 0f;
        return switch (op) {
            case "add" -> c -> x.eval(c) + y.eval(c);
            case "sub" -> c -> x.eval(c) - y.eval(c);
            case "mul" -> c -> x.eval(c) * y.eval(c);
            case "div" -> c -> {
                float d = y.eval(c);
                return d == 0 ? 0f : x.eval(c) / d;
            };
            case "rem" -> c -> {
                float d = y.eval(c);
                return d == 0 ? 0f : x.eval(c) % d;
            };
            case "neg" -> c -> -x.eval(c);
            case "cos" -> c -> Mth.cos(x.eval(c));
            case "sin" -> c -> Mth.sin(x.eval(c));
            case "abs" -> c -> Math.abs(x.eval(c));
            case "sqrt" -> c -> Mth.sqrt(Math.max(0, x.eval(c)));
            case "rad" -> c -> x.eval(c) * Mth.DEG_TO_RAD;
            case "deg" -> c -> x.eval(c) * Mth.RAD_TO_DEG;
            case "min" -> c -> Math.min(x.eval(c), y.eval(c));
            case "max" -> c -> Math.max(x.eval(c), y.eval(c));
            case "floor" -> c -> Mth.floor(x.eval(c));
            case "sign" -> c -> Math.signum(x.eval(c));
            case "atan2" -> c -> (float) Mth.atan2(x.eval(c), y.eval(c));
            case "clamp" -> c -> Mth.clamp(x.eval(c), y.eval(c), z.eval(c));
            case "ite" -> c -> x.eval(c) != 0 ? y.eval(c) : z.eval(c);
            case "lt" -> c -> x.eval(c) < y.eval(c) ? 1f : 0f;
            case "le" -> c -> x.eval(c) <= y.eval(c) ? 1f : 0f;
            case "gt" -> c -> x.eval(c) > y.eval(c) ? 1f : 0f;
            case "ge" -> c -> x.eval(c) >= y.eval(c) ? 1f : 0f;
            case "eq" -> c -> x.eval(c) == y.eval(c) ? 1f : 0f;
            case "ne" -> c -> x.eval(c) != y.eval(c) ? 1f : 0f;
            case "not" -> c -> x.eval(c) == 0 ? 1f : 0f;
            case "and" -> c -> {
                for (Expr p : args) {
                    if (p.eval(c) == 0) {
                        return 0f;
                    }
                }
                return 1f;
            };
            case "or" -> c -> {
                for (Expr p : args) {
                    if (p.eval(c) != 0) {
                        return 1f;
                    }
                }
                return 0f;
            };
            default -> c -> 0f;
        };
    }

    private static float rest(Ctx c, String part, String field) {
        ModelPart p = c.parts.get(part);
        PartPose pose = p == null ? null : c.rest.get(p);
        if (pose == null) {
            return field.equals("visible") ? 1f : 0f;
        }
        return switch (field) {
            case "xRot" -> pose.xRot;
            case "yRot" -> pose.yRot;
            case "zRot" -> pose.zRot;
            case "x" -> pose.x;
            case "y" -> pose.y;
            case "z" -> pose.z;
            default -> 1f;
        };
    }

    private static Expr input(String name) {
        return switch (name) {
            case "limb" -> c -> c.limb;
            case "amount" -> c -> c.amount;
            case "age" -> c -> c.age;
            case "yaw" -> c -> c.yaw;
            case "pitch" -> c -> c.pitch;
            case "swing" -> c -> c.swing;
            case "scale" -> c -> 0.0625f;
            case "riding" -> c -> c.npc.isPassenger() ? 1f : 0f;
            case "child" -> c -> c.npc.isBaby() ? 1f : 0f;
            case "sneak" -> c -> c.npc.isCrouching() ? 1f : 0f;
            case "held_right" -> c -> c.npc.getMainHandItem().isEmpty() ? 0f : 1f;
            case "held_left" -> c -> c.npc.getOffhandItem().isEmpty() ? 0f : 1f;
            default -> name.startsWith("entity.") ? entity(name.substring(7)) : c -> 0f;
        };
    }

    /**
     * The entity values LOTR's animations read, answered from the port's NPC: its age, hurt time and the creature
     * kind (by type id). LOTR-only behaviour state (throwing rocks, sniffing, sitting, fleeing, healing saplings...)
     * reads 0 until those behaviours are ported.
     */
    private static Expr entity(String name) {
        return switch (name) {
            case "field_70173_aa" -> c -> c.npc.tickCount;
            case "field_70737_aN" -> c -> c.npc.hurtTime;
            case "is.EntityLivingBase", "is.LOTREntityNPC" -> c -> 1f;
            case "is.LOTREntityTroll" -> c -> c.npc.model().equals("lotr_port:troll") ? 1f : 0f;
            case "is.LOTREntityMountainTroll" -> c -> c.npc.typeId().getPath().contains("mountain_troll") ? 1f : 0f;
            case "is.LOTREntitySnowTroll" -> c -> c.npc.typeId().getPath().contains("snow_troll") ? 1f : 0f;
            case "is.LOTREntityElf" -> c -> c.npc.model().equals("elf") ? 1f : 0f;
            case "getTailRotation" -> c -> {
                // LOTR's wargs hold the tail like vanilla wolves: high when healthy, drooping when hurt
                LivingEntity e = c.npc;
                float hurt = e.getMaxHealth() <= 0 ? 0 : 1f - e.getHealth() / e.getMaxHealth();
                return (0.55f - hurt * 0.4f) * Mth.PI;
            };
            default -> c -> 0f;
        };
    }
}
