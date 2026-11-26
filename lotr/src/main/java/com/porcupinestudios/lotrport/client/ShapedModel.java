package com.porcupinestudios.lotrport.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;

/**
 * A humanoid drawn at other proportions and posture (2026-11-26): goblins, orcs and Uruks. The boxes and their 64x64
 * skin layout stay the humanoid's, so a player's own skin fits ({@link RaceSkinRemap}); the {@link Shape} scales the
 * parts and adds a stoop on top of vanilla's animation, so walking, swinging, crouching and riding stay vanilla's,
 * and armour (whose parts copy ours) follows. Ears and the goblin nose are head children ({@link NpcModels#tall}).
 */
public class ShapedModel<T extends LivingEntity> extends HumanoidModel<T> {
    /**
     * Part scales and posture.
     *
     * @param head head scale
     * @param bodyX torso width scale
     * @param bodyY torso length scale
     * @param bodyZ torso depth scale
     * @param armXZ arm thickness scale
     * @param armY arm length scale
     * @param legXZ leg thickness scale
     * @param legY leg length scale
     * @param hunch forward lean of the torso, radians
     * @param headSink how far the head sinks between the shoulders, pixels
     * @param headForward how far the head juts forward of the neck, pixels
     */
    public record Shape(float head, float bodyX, float bodyY, float bodyZ, float armXZ, float armY, float legXZ,
                        float legY, float hunch, float headSink, float headForward) {}

    /** Big head, narrow hunched torso, long thin arms, short legs (2026-11-26, the goblin faction). */
    public static final Shape GOBLIN = new Shape(1.2f, 0.85f, 0.85f, 0.9f, 0.75f, 1.15f, 0.8f, 0.75f, 0.35f, 0.8f,
            0.5f);
    /** "Stooped servants of the Enemy": a stoop, arms a little long, legs a little short. */
    public static final Shape ORC = new Shape(1.0f, 1.0f, 0.97f, 1.0f, 1.0f, 1.08f, 1.0f, 0.92f, 0.2f, 0.6f, 0.3f);
    /** Man-high and broad: wide shoulders, thick arms and legs, upright. */
    public static final Shape URUK = new Shape(0.95f, 1.12f, 1.0f, 1.15f, 1.15f, 1.05f, 1.1f, 1.02f, 0.06f, 0.3f, 0f);

    private final Shape shape;
    // where the parts move from vanilla's places (model pixels): the feet stay on the ground, the torso sits on the
    // legs, and the lean carries the shoulders and head forward
    private final float legsDown;
    private final float bodyDown;
    private final float bodyForward;
    private final float armDown;
    private final float armForward;
    private final float armIn;
    private final float headDown;
    private final float headForward;

    public ShapedModel(ModelPart root, Shape shape) {
        super(root);
        this.shape = shape;
        float bodyLen = 12 * shape.bodyY();
        float cos = (float) Math.cos(shape.hunch());
        float sin = (float) Math.sin(shape.hunch());
        legsDown = 12 * (1 - shape.legY());
        bodyDown = legsDown + 12 * (1 - shape.bodyY()) + bodyLen * (1 - cos);
        bodyForward = -bodyLen * sin;
        float shoulder = 2 * shape.armY();
        armDown = bodyDown + shoulder * cos - 2;
        armForward = bodyForward + shoulder * sin;
        armIn = 5 - (4 * shape.bodyX() + shape.armXZ());
        headDown = bodyDown + shape.headSink();
        headForward = bodyForward - shape.headForward();
    }

    public Shape shape() {
        return shape;
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
                          float headPitch) {
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        posture();
    }

    /**
     * The shape's proportions and posture on top of whatever vanilla's animation set this frame. Vanilla sets every
     * y, the arms' x and z and the legs' z each frame, so those are moved by an offset; it never sets the head's or
     * body's z, so those are set outright (an offset there would add up frame after frame).
     */
    void posture() {
        Shape s = shape;
        scale(head, s.head(), s.head(), s.head());
        scale(body, s.bodyX(), s.bodyY(), s.bodyZ());
        scale(rightArm, s.armXZ(), s.armY(), s.armXZ());
        scale(leftArm, s.armXZ(), s.armY(), s.armXZ());
        scale(rightLeg, s.legXZ(), s.legY(), s.legXZ());
        scale(leftLeg, s.legXZ(), s.legY(), s.legXZ());
        rightLeg.y += legsDown;
        leftLeg.y += legsDown;
        body.y += bodyDown;
        body.z = bodyForward;
        body.xRot += s.hunch();
        head.y += headDown;
        head.z = headForward;
        rightArm.x += armIn;
        leftArm.x -= armIn;
        rightArm.y += armDown;
        leftArm.y += armDown;
        rightArm.z += armForward;
        leftArm.z += armForward;
        hat.copyFrom(head);
        scale(hat, s.head(), s.head(), s.head());
    }

    private static void scale(ModelPart part, float x, float y, float z) {
        part.xScale = x;
        part.yScale = y;
        part.zScale = z;
    }

    /**
     * Where a held item goes: the arm's place and turn without its scale (which would squash or stretch the item),
     * then down the arm's extra length to the hand.
     */
    @Override
    public void translateToHand(HumanoidArm side, PoseStack pose) {
        ModelPart arm = getArm(side);
        pose.translate(arm.x / 16f, arm.y / 16f, arm.z / 16f);
        if (arm.xRot != 0 || arm.yRot != 0 || arm.zRot != 0) {
            pose.mulPose(new Quaternionf().rotationZYX(arm.zRot, arm.yRot, arm.xRot));
        }
        pose.translate(0, 10 * (shape.armY() - 1) / 16f, 0);
    }
}
