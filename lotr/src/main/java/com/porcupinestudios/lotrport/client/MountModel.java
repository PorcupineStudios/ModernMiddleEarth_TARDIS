package com.porcupinestudios.lotrport.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.porcupinestudios.lotrport.mount.LotrHorse;
import java.util.Set;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * A mount's body: one of the creature models ({@link CreatureModel}) played by a {@link LotrHorse}. LOTR's own
 * models (camel, elk, rhino, giraffe; the boar) bring their animation with them. Horses, ponies and zebras use
 * Minecraft 1.7.10's horse model, because LOTR's zebra and horse armour textures are painted for it; its animation
 * (walk, rearing, grazing, the open mouth, the tail, stirrups and reins) is written here from 1.7.10's numbers.
 */
public class MountModel extends EntityModel<LotrHorse> {
    private static final Set<String> TACK = Set.of("saddle_bottom", "saddle_front", "saddle_back", "left_saddle_metal",
            "left_saddle_rope", "right_saddle_metal", "right_saddle_rope", "left_face_metal", "right_face_metal",
            "face_ropes");
    private static final Set<String> REINS = Set.of("left_rein", "right_rein");
    private static final Set<String> MULE = Set.of("mule_left_ear", "mule_right_ear", "mule_left_chest",
            "mule_right_chest");
    private static final Set<String> MOUTH = Set.of("mouth_top", "mouth_bottom");

    final CreatureModel body;
    final boolean oldHorse;
    private float partial;
    private boolean saddled;
    private boolean ridden;

    MountModel(CreatureModel body, boolean oldHorse) {
        this.body = body;
        this.oldHorse = oldHorse;
    }

    @Override
    public void prepareMobModel(LotrHorse horse, float limbSwing, float limbSwingAmount, float partialTick) {
        partial = partialTick;
    }

    @Override
    public void setupAnim(LotrHorse horse, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
            float headPitch) {
        body.attackTime = attackTime;
        body.ctx.partial = partial;
        if (!oldHorse) {
            body.setupAnimFor(horse, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
            return;
        }
        body.resetPose();
        saddled = horse.isSaddled() && !horse.isBaby();
        ridden = horse.isVehicle();
        oldHorse(horse, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int light, int overlay, int color) {
        if (oldHorse) {
            renderHorse(pose, buffer, light, overlay, color, true);
        } else {
            body.renderToBuffer(pose, buffer, light, overlay, color);
        }
    }

    /** The 1.7.10 horse: its body, and with {@code tack} the saddle, bridle and reins painted on the same texture. */
    void renderHorse(PoseStack pose, VertexConsumer buffer, int light, int overlay, int color, boolean tack) {
        ModelPart head = body.part("head");
        for (String name : body.partNames()) {
            if (MULE.contains(name) || (TACK.contains(name) && !(tack && saddled))
                    || (REINS.contains(name) && !(tack && saddled && ridden))) {
                continue;
            }
            ModelPart part = body.part(name);
            if (MOUTH.contains(name) && head != null) {
                pose.pushPose();
                head.translateAndRotate(pose); // the jaws hang on the head
                part.render(pose, buffer, light, overlay, color);
                pose.popPose();
            } else {
                part.render(pose, buffer, light, overlay, color);
            }
        }
    }

    private ModelPart p(String name) {
        return body.part(name);
    }

    private void oldHorse(LotrHorse horse, float limb, float amount, float age, float yawDeg, float pitchDeg) {
        float yaw = Mth.clamp(yawDeg, -20f, 20f) * Mth.DEG_TO_RAD;
        float pitch = pitchDeg * Mth.DEG_TO_RAD;
        if (amount > 0.2f) {
            pitch += Mth.cos(limb * 0.4f) * 0.15f * amount;
        }
        float eat = horse.getEatAnim(partial);
        float rear = horse.getStandAnim(partial);
        float flat = 1f - rear;
        float mouth = horse.getMouthAnim(partial);
        float held = 1f - Math.max(rear, eat);
        float walk = Mth.cos(limb * 0.6662f + Mth.PI);
        float stride = walk * 0.8f * amount;

        ModelPart head = p("head");
        head.xRot = rear * (0.2617994f + pitch) + eat * 2.18166f + held * (0.5235988f + pitch);
        head.yRot = rear * yaw + held * yaw;
        head.y = rear * -6f + eat * 11f + held * 4f;
        head.z = rear * -1f + eat * -10f + held * -10f;
        for (String follower : new String[] {"left_ear", "right_ear", "neck", "mane", "face_ropes", "left_face_metal",
                "right_face_metal"}) {
            ModelPart f = p(follower);
            f.y = head.y;
            f.z = head.z;
            f.xRot = head.xRot;
            f.yRot = head.yRot;
        }
        for (String rein : REINS) {
            ModelPart r = p(rein);
            r.y = head.y;
            r.z = head.z;
            r.xRot = pitch;
            r.yRot = head.yRot;
        }
        ModelPart top = p("mouth_top");
        top.setPos(0f, 0.02f, 0.02f - mouth);
        top.setRotation(-0.09424778f * mouth, 0f, 0f);
        ModelPart bottom = p("mouth_bottom");
        bottom.setPos(0f, 0f, mouth);
        bottom.setRotation(0.15707964f * mouth, 0f, 0f);

        ModelPart bodyPart = p("body");
        bodyPart.xRot = rear * -0.7853981f;

        float lift = 0.2617994f * rear;
        float paw = Mth.cos(age * 0.6f + Mth.PI);
        float back = walk * 0.5f * amount;
        ModelPart frontLeft = p("front_left_leg");
        ModelPart frontRight = p("front_right_leg");
        frontLeft.y = -2f * rear + 9f * flat;
        frontLeft.z = -2f * rear + -8f * flat;
        frontRight.y = frontLeft.y;
        frontRight.z = frontLeft.z;
        ModelPart backLeft = p("back_left_leg");
        ModelPart backRight = p("back_right_leg");
        shin("back_left", backLeft, lift + flat * -back);
        shin("back_right", backRight, lift + flat * back);
        float left = (-1.0471976f + paw) * rear + stride * flat;
        float right = (-1.0471976f - paw) * rear - stride * flat;
        shin("front_left", frontLeft, left);
        shin("front_right", frontRight, right);
        backLeft.xRot = lift + -back * flat;
        backRight.xRot = lift + back * flat;
        frontLeft.xRot = left;
        frontRight.xRot = right;
        float backLeftShin = -0.08726646f * rear + (-back - Math.max(0f, back)) * flat;
        float backRightShin = -0.08726646f * rear + (back - Math.max(0f, -back)) * flat;
        float frontLeftShin = (left + Mth.PI * Math.max(0f, 0.2f + paw * 0.2f)) * rear
                + (stride + Math.max(0f, back)) * flat;
        float frontRightShin = (right + Mth.PI * Math.max(0f, 0.2f - paw * 0.2f)) * rear
                + (-stride + Math.max(0f, -back)) * flat;
        p("back_left_shin").xRot = p("back_left_hoof").xRot = backLeftShin;
        p("back_right_shin").xRot = p("back_right_hoof").xRot = backRightShin;
        p("front_left_shin").xRot = p("front_left_hoof").xRot = frontLeftShin;
        p("front_right_shin").xRot = p("front_right_hoof").xRot = frontRightShin;

        ModelPart saddle = p("saddle_bottom");
        saddle.y = rear * 0.5f + flat * 2f;
        saddle.z = rear * 11f + flat * 2f;
        saddle.xRot = bodyPart.xRot;
        for (String s : new String[] {"saddle_front", "saddle_back"}) {
            p(s).y = saddle.y;
            p(s).z = saddle.z;
            p(s).xRot = bodyPart.xRot;
        }
        for (String s : new String[] {"left_saddle_rope", "left_saddle_metal", "right_saddle_rope",
                "right_saddle_metal"}) {
            ModelPart stirrup = p(s);
            stirrup.y = saddle.y;
            stirrup.z = saddle.z;
            boolean leftSide = s.startsWith("left");
            stirrup.xRot = ridden ? -1.0471976f : stride / 3f;
            stirrup.zRot = ridden ? 0f : (leftSide ? stride : -stride) / 5f;
        }

        float droop = Math.min(0f, -1.3089f + amount * 1.5f);
        ModelPart tail = p("tail_base");
        tail.y = rear * 9f + flat * 3f;
        if (horse.tailCounter != 0) {
            tail.yRot = Mth.cos(age * 0.7f);
            droop = 0f;
        } else {
            tail.yRot = 0f;
        }
        for (String t : new String[] {"tail_middle", "tail_tip"}) {
            p(t).y = tail.y;
            p(t).z = tail.z;
            p(t).yRot = tail.yRot;
        }
        tail.xRot = droop;
        p("tail_middle").xRot = droop;
        p("tail_tip").xRot = -0.2618f + droop;
    }

    /** A lower leg and hoof hang 7 px down the upper leg, whichever way it swings. */
    private void shin(String leg, ModelPart upper, float angle) {
        float y = upper.y + Mth.sin(Mth.HALF_PI + angle) * 7f;
        float z = upper.z + Mth.cos(Mth.PI * 1.5f + angle) * 7f;
        p(leg + "_shin").y = p(leg + "_hoof").y = y;
        p(leg + "_shin").z = p(leg + "_hoof").z = z;
    }
}
