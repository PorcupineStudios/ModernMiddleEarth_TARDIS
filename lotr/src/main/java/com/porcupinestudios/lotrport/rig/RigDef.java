package com.porcupinestudios.lotrport.rig;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A procedurally animated creature body: {@code data/<ns>/rig/<name>.json}, reloaded with {@code /reload} and sent
 * to clients. It holds the skeleton and its boxes (model pixels, as Blockbench and LOTR's models: y down, ground at
 * y 24, the head towards -z; bone pivots absolute, cube origins relative to their bone's pivot), which bones are legs,
 * how they step, what sways and lags, and the hitbox parts. See {@code docs/rig.md}.
 *
 * @param npcTypes the NPC types that use this body (their spawns become rigged creatures)
 * @param texture texture size in pixels (the NPC type's skins are drawn on it)
 * @param scale size against the model's pixels (1 = 16 pixels a block)
 * @param scaleState an NPC state value the size is multiplied by (a spider's {@code getSpiderScaleAmount})
 * @param bones the skeleton, parents before children
 * @param body the bone the body tilt and lift move (usually the root; empty = all root bones)
 * @param look the bone that turns to where the creature looks
 * @param legs legs placed by IK on the ground
 * @param gait how legs step and the body follows them
 * @param sways idle and walking motion of single bones (breathing, ears, abdomen)
 * @param chains bone chains that lag behind turns (necks, tails, trunks)
 * @param parts hitboxes that follow bones
 * @param hide what the creature turns into while it hides (empty = it can't)
 */
public record RigDef(List<String> npcTypes, List<Integer> texture, float scale, Optional<String> scaleState,
        Map<String, Bone> bones, Optional<String> body, Optional<String> look, List<Leg> legs, Gait gait,
        List<Sway> sways, List<Chain> chains, List<Part> parts, Optional<Hide> hide) {

    /** Three numbers. In its own holder so the nested codecs never wait on this record's class init. */
    private static final class V {
        static final Codec<List<Float>> VEC = Codec.FLOAT.listOf().validate(l -> l.size() == 3
                ? com.mojang.serialization.DataResult.success(l)
                : com.mojang.serialization.DataResult.error(() -> "need 3 numbers, got " + l.size()));
    }

    /** One box of a bone, as LOTR's and Blockbench's Java models: origin relative to the bone pivot. */
    public record Cube(List<Integer> uv, List<Float> origin, List<Float> size, float inflate, boolean mirror) {
        public static final Codec<Cube> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.listOf().fieldOf("uv").forGetter(Cube::uv),
                V.VEC.fieldOf("origin").forGetter(Cube::origin),
                V.VEC.fieldOf("size").forGetter(Cube::size),
                Codec.FLOAT.optionalFieldOf("inflate", 0f).forGetter(Cube::inflate),
                Codec.BOOL.optionalFieldOf("mirror", false).forGetter(Cube::mirror))
                .apply(i, Cube::new));
    }

    /**
     * A bone.
     *
     * @param parent its parent bone; empty for a root
     * @param pivot absolute pivot in model pixels
     * @param rotation rest rotation in degrees (x, y, z; applied z, y, x as Minecraft does)
     */
    public record Bone(Optional<String> parent, List<Float> pivot, List<Float> rotation, List<Cube> cubes) {
        public static final Codec<Bone> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("parent").forGetter(Bone::parent),
                V.VEC.fieldOf("pivot").forGetter(Bone::pivot),
                V.VEC.optionalFieldOf("rotation", List.of(0f, 0f, 0f)).forGetter(Bone::rotation),
                Cube.CODEC.listOf().optionalFieldOf("cubes", List.of()).forGetter(Bone::cubes))
                .apply(i, Bone::new));
    }

    /**
     * A two-bone leg: hip at {@code upper}'s pivot, knee at {@code lower}'s pivot, the foot tip at {@code foot}.
     *
     * @param foot the foot tip at rest, absolute model pixels (on the ground: y 24 for a creature standing at rest)
     * @param group legs of one group step together; groups take turns
     * @param bend which way the knee points when the leg is straight at rest (model pixels direction); empty = the
     *        way the rest pose already bends
     * @param spread where the foot plants, as a multiple of the hip-to-foot distance across the ground at rest (1.4 =
     *        feet set wider than the model's rest pose, knees rising higher)
     */
    public record Leg(String upper, String lower, List<Float> foot, int group, Optional<List<Float>> bend,
            float spread) {
        public static final Codec<Leg> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("upper").forGetter(Leg::upper),
                Codec.STRING.fieldOf("lower").forGetter(Leg::lower),
                V.VEC.fieldOf("foot").forGetter(Leg::foot),
                Codec.INT.optionalFieldOf("group", 0).forGetter(Leg::group),
                V.VEC.optionalFieldOf("bend").forGetter(Leg::bend),
                Codec.FLOAT.optionalFieldOf("spread", 1f).forGetter(Leg::spread))
                .apply(i, Leg::new));
    }

    /**
     * Stepping and body numbers, in blocks at scale 1 (they grow with the creature).
     *
     * @param stepDistance a planted foot steps once its spot has moved this far away
     * @param stepTicks how long a step takes at walking speed
     * @param stepHeight how high a foot lifts mid-step
     * @param lead how many ticks of movement a step lands ahead, so feet land in front instead of trailing
     * @param reachUp how far above the hip line a foot may find ground (climbing a step)
     * @param reachDown how far below the feet a foot searches for ground
     * @param maxTilt the most the body pitches or rolls to fit its feet, degrees
     * @param bodySpring how fast body height and tilt follow the feet (0..1 per tick)
     * @param heavy camera shake and dust on each footfall (0 = none; 1 = a mumak)
     * @param stomp damage to small creatures under a landing foot (0 = none)
     * @param overlap how far through its step a group must be before the next group may lift (1 = never two groups
     *        off the ground at once; 0.5 = an elephant's overlapping walk)
     */
    public record Gait(float stepDistance, int stepTicks, float stepHeight, float lead, float reachUp,
            float reachDown, float maxTilt, float bodySpring, float heavy, float stomp, float overlap) {
        public static final Gait DEFAULT = new Gait(0.6f, 5, 0.25f, 3f, 0.6f, 1.2f, 20f, 0.35f, 0f, 0f, 1f);
        public static final Codec<Gait> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.optionalFieldOf("step_distance", DEFAULT.stepDistance).forGetter(Gait::stepDistance),
                Codec.INT.optionalFieldOf("step_ticks", DEFAULT.stepTicks).forGetter(Gait::stepTicks),
                Codec.FLOAT.optionalFieldOf("step_height", DEFAULT.stepHeight).forGetter(Gait::stepHeight),
                Codec.FLOAT.optionalFieldOf("lead", DEFAULT.lead).forGetter(Gait::lead),
                Codec.FLOAT.optionalFieldOf("reach_up", DEFAULT.reachUp).forGetter(Gait::reachUp),
                Codec.FLOAT.optionalFieldOf("reach_down", DEFAULT.reachDown).forGetter(Gait::reachDown),
                Codec.FLOAT.optionalFieldOf("max_tilt", DEFAULT.maxTilt).forGetter(Gait::maxTilt),
                Codec.FLOAT.optionalFieldOf("body_spring", DEFAULT.bodySpring).forGetter(Gait::bodySpring),
                Codec.FLOAT.optionalFieldOf("heavy", DEFAULT.heavy).forGetter(Gait::heavy),
                Codec.FLOAT.optionalFieldOf("stomp", DEFAULT.stomp).forGetter(Gait::stomp),
                Codec.FLOAT.optionalFieldOf("overlap", DEFAULT.overlap).forGetter(Gait::overlap))
                .apply(i, Gait::new));
    }

    /**
     * A bone rocking on one axis: {@code amount} degrees, {@code speed} radians a tick, times {@code walk} more while
     * walking at full speed.
     */
    public record Sway(String bone, String axis, float amount, float speed, float phase, float walk) {
        public static final Codec<Sway> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("bone").forGetter(Sway::bone),
                Codec.STRING.optionalFieldOf("axis", "x").forGetter(Sway::axis),
                Codec.FLOAT.optionalFieldOf("amount", 3f).forGetter(Sway::amount),
                Codec.FLOAT.optionalFieldOf("speed", 0.08f).forGetter(Sway::speed),
                Codec.FLOAT.optionalFieldOf("phase", 0f).forGetter(Sway::phase),
                Codec.FLOAT.optionalFieldOf("walk", 1f).forGetter(Sway::walk))
                .apply(i, Sway::new));
    }

    /**
     * Bones that swing behind the body when it turns and settle back on a spring: each bone takes {@code drag} of the
     * turn and returns {@code stiffness} of its bend a tick. {@code droop} degrees of pitch per bone while walking.
     */
    public record Chain(List<String> bones, float drag, float stiffness, float droop) {
        public static final Codec<Chain> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.listOf().fieldOf("bones").forGetter(Chain::bones),
                Codec.FLOAT.optionalFieldOf("drag", 0.4f).forGetter(Chain::drag),
                Codec.FLOAT.optionalFieldOf("stiffness", 0.15f).forGetter(Chain::stiffness),
                Codec.FLOAT.optionalFieldOf("droop", 0f).forGetter(Chain::droop))
                .apply(i, Chain::new));
    }

    /**
     * A hitbox on a bone: centred on {@code center} (absolute model pixels at rest), {@code width} x {@code height}
     * blocks at scale 1, {@code damage} times the hit's damage reaches the creature, {@code solid} ones can be stood on.
     */
    public record Part(String name, String bone, List<Float> center, float width, float height, float damage,
            boolean solid) {
        public static final Codec<Part> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("name").forGetter(Part::name),
                Codec.STRING.fieldOf("bone").forGetter(Part::bone),
                V.VEC.fieldOf("center").forGetter(Part::center),
                Codec.FLOAT.fieldOf("width").forGetter(Part::width),
                Codec.FLOAT.fieldOf("height").forGetter(Part::height),
                Codec.FLOAT.optionalFieldOf("damage", 1f).forGetter(Part::damage),
                Codec.BOOL.optionalFieldOf("solid", false).forGetter(Part::solid))
                .apply(i, Part::new));
    }

    /**
     * Hiding as blocks (stone giants): while hidden it stands still, snapped to the block grid and turned to a
     * quarter turn, and draws as {@code block} instead of its body, in the shape of its {@code blocks} (offsets from its
     * feet, model pixels / 16 rounded to whole blocks). It wakes when a target comes within {@code wake} blocks or it
     * is hurt, and hides again after {@code settle} ticks with nothing to fight.
     */
    public record Hide(String block, List<List<Integer>> blocks, float wake, int settle) {
        public static final Codec<Hide> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("block").forGetter(Hide::block),
                Codec.INT.listOf().listOf().fieldOf("blocks").forGetter(Hide::blocks),
                Codec.FLOAT.optionalFieldOf("wake", 6f).forGetter(Hide::wake),
                Codec.INT.optionalFieldOf("settle", 100).forGetter(Hide::settle))
                .apply(i, Hide::new));
    }

    public static final Codec<RigDef> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.listOf().optionalFieldOf("npc_types", List.of()).forGetter(RigDef::npcTypes),
            Codec.INT.listOf().optionalFieldOf("texture", List.of(64, 32)).forGetter(RigDef::texture),
            Codec.FLOAT.optionalFieldOf("scale", 1f).forGetter(RigDef::scale),
            Codec.STRING.optionalFieldOf("scale_state").forGetter(RigDef::scaleState),
            Codec.unboundedMap(Codec.STRING, Bone.CODEC).fieldOf("bones").forGetter(RigDef::bones),
            Codec.STRING.optionalFieldOf("body").forGetter(RigDef::body),
            Codec.STRING.optionalFieldOf("look").forGetter(RigDef::look),
            Leg.CODEC.listOf().optionalFieldOf("legs", List.of()).forGetter(RigDef::legs),
            Gait.CODEC.optionalFieldOf("gait", Gait.DEFAULT).forGetter(RigDef::gait),
            Sway.CODEC.listOf().optionalFieldOf("sways", List.of()).forGetter(RigDef::sways),
            Chain.CODEC.listOf().optionalFieldOf("chains", List.of()).forGetter(RigDef::chains),
            Part.CODEC.listOf().optionalFieldOf("parts", List.of()).forGetter(RigDef::parts),
            Hide.CODEC.optionalFieldOf("hide").forGetter(RigDef::hide))
            .apply(i, RigDef::new));
}
