package com.porcupinestudios.lotrport.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;

/**
 * The goblins' body (2026-11-26, Aaron: a new model for the goblin faction that players can take too): the humanoid
 * boxes and their 64x64 skin layout, so a player's own skin fits it ({@link RaceSkinRemap}), drawn at goblin
 * proportions and posture: a big head with bat ears and a hooked nose ({@link NpcModels#tall}), a narrow hunched
 * torso, long thin arms, short legs. The proportions are part scales and the posture offsets added after vanilla's
 * animation, so walking, swinging, crouching and riding stay vanilla's; armour copies both (its parts copy ours).
 */
public class GoblinModel<T extends LivingEntity> extends HumanoidModel<T> {
    static final float HEAD = 1.2f;
    static final float BODY_XY = 0.85f;
    static final float BODY_Z = 0.9f;
    static final float ARM_XZ = 0.75f;
    static final float ARM_Y = 1.15f;
    static final float LEG_XZ = 0.8f;
    static final float LEG_Y = 0.75f;
    /** Forward lean of the torso, radians. */
    static final float HUNCH = 0.35f;

    // where the parts move from vanilla's places (model pixels): the feet stay on the ground, the shorter body sits
    // on the shorter legs, and the lean carries the shoulders and head forward
    private static final float LEGS_DOWN = 12 * (1 - LEG_Y);
    private static final float BODY_LEN = 12 * BODY_XY;
    private static final float BODY_DOWN = LEGS_DOWN + 12 * (1 - BODY_XY) + BODY_LEN * (1 - (float) Math.cos(HUNCH));
    private static final float BODY_FORWARD = -BODY_LEN * (float) Math.sin(HUNCH);
    private static final float SHOULDER = 2 * ARM_Y;
    private static final float ARM_DOWN = BODY_DOWN + SHOULDER * (float) Math.cos(HUNCH) - 2;
    private static final float ARM_FORWARD = BODY_FORWARD + SHOULDER * (float) Math.sin(HUNCH);
    private static final float ARM_IN = 5 - (4 * BODY_XY + 1 * ARM_XZ);
    private static final float HEAD_DOWN = BODY_DOWN + 0.8f;
    private static final float HEAD_FORWARD = BODY_FORWARD - 0.5f;

    public GoblinModel(ModelPart root) {
        super(root);
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
                          float headPitch) {
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        posture();
    }

    /**
     * Goblin proportions and posture on top of whatever vanilla's animation set this frame. Vanilla sets every y,
     * the arms' x and z and the legs' z each frame, so those are moved by an offset; it never sets the head's or
     * body's z, so those are set outright (an offset there would add up frame after frame).
     */
    void posture() {
        scale(head, HEAD, HEAD, HEAD);
        scale(body, BODY_XY, BODY_XY, BODY_Z);
        scale(rightArm, ARM_XZ, ARM_Y, ARM_XZ);
        scale(leftArm, ARM_XZ, ARM_Y, ARM_XZ);
        scale(rightLeg, LEG_XZ, LEG_Y, LEG_XZ);
        scale(leftLeg, LEG_XZ, LEG_Y, LEG_XZ);
        rightLeg.y += LEGS_DOWN;
        leftLeg.y += LEGS_DOWN;
        body.y += BODY_DOWN;
        body.z = BODY_FORWARD;
        body.xRot += HUNCH;
        head.y += HEAD_DOWN;
        head.z = HEAD_FORWARD;
        rightArm.x += ARM_IN;
        leftArm.x -= ARM_IN;
        rightArm.y += ARM_DOWN;
        leftArm.y += ARM_DOWN;
        rightArm.z += ARM_FORWARD;
        leftArm.z += ARM_FORWARD;
        hat.copyFrom(head);
        scale(hat, HEAD, HEAD, HEAD);
    }

    private static void scale(ModelPart part, float x, float y, float z) {
        part.xScale = x;
        part.yScale = y;
        part.zScale = z;
    }

    /**
     * Where a held item goes: the arm's place and turn without its thin, long scale (which would squash the item),
     * then down the extra length of the arm to the hand.
     */
    @Override
    public void translateToHand(HumanoidArm side, PoseStack pose) {
        ModelPart arm = getArm(side);
        pose.translate(arm.x / 16f, arm.y / 16f, arm.z / 16f);
        if (arm.xRot != 0 || arm.yRot != 0 || arm.zRot != 0) {
            pose.mulPose(new Quaternionf().rotationZYX(arm.zRot, arm.yRot, arm.xRot));
        }
        pose.translate(0, 10 * (ARM_Y - 1) / 16f, 0);
    }
}
