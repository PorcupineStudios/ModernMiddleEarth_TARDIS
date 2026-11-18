package com.porcupinestudios.lotrport.npc;

import com.porcupinestudios.lotrport.rig.RigDef;
import com.porcupinestudios.lotrport.rig.RigPart;
import com.porcupinestudios.lotrport.rig.RigPose;
import com.porcupinestudios.lotrport.rig.RigSkeleton;
import com.porcupinestudios.lotrport.rig.RigState;
import com.porcupinestudios.lotrport.rig.Rigs;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.entity.PartEntity;
import org.joml.Matrix4d;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * An NPC drawn on a procedural rig ({@code rig/}): legs placed on the real ground by IK, the body tilting to fit them,
 * and hitboxes per body part. It is a {@link LotrNpc} in every other way (type, faction, behaviours, AI); spawns of an
 * NPC type named by a rig file become this entity ({@link LotrNpc#entityTypeFor}). Both sides tick the rig.
 */
public class RiggedNpc extends LotrNpc {
    /** Hitbox slots every rigged NPC has (part entity ids are reserved up front, as the Ender Dragon does). */
    public static final int MAX_PARTS = 12;
    /** Client hook for footfalls (sound, dust, camera shake); set by client setup. */
    public static Consumer<Footfall> clientFootfall = f -> { };

    private final RigPart[] parts = new RigPart[MAX_PARTS];
    @Nullable
    private RigSkeleton skeleton;
    @Nullable
    private RigState state;
    @Nullable
    private RigPose pose;
    @Nullable
    private RigPose renderPose;
    private final RigState.Body body = new RigState.Body();
    /** Ticks since it last had something to fight (server; hiding creatures hide again after the rig's settle). */
    private int idle;
    private boolean wasHidden;
    private float hideNow;
    private float hidePrev;

    /** A foot landing, for the client's effects. */
    public record Footfall(RiggedNpc npc, int leg, double x, double y, double z, float heavy, float scale) {}

    public RiggedNpc(EntityType<? extends RiggedNpc> type, Level level) {
        super(type, level);
        for (int i = 0; i < MAX_PARTS; i++) {
            parts[i] = new RigPart(this);
        }
        setId(ENTITY_COUNTER.getAndAdd(MAX_PARTS + 1) + 1);
    }

    @Override
    public void setId(int id) {
        super.setId(id);
        for (int i = 0; i < MAX_PARTS; i++) {
            if (parts[i] != null) {
                parts[i].setId(id + i + 1);
            }
        }
    }

    @Override
    public boolean isMultipartEntity() {
        return true;
    }

    @Override
    public PartEntity<?>[] getParts() {
        return parts;
    }

    /** The rig for this NPC's type on this side; rebuilt state when the rig reloads. */
    @Nullable
    public RigSkeleton skeleton() {
        RigSkeleton now = Rigs.forType(level(), typeId());
        if (now != skeleton) {
            skeleton = now;
            state = now == null ? null : new RigState(now);
            pose = now == null ? null : new RigPose(now);
            renderPose = now == null ? null : new RigPose(now);
            if (now != null) {
                for (int i = 0; i < MAX_PARTS; i++) {
                    if (i < now.parts.length) {
                        RigSkeleton.Part p = now.parts[i];
                        parts[i].configure(true, p.name(), p.width(), p.height(), p.damage(), p.solid());
                    } else {
                        parts[i].configure(false, "", 0.1f, 0.1f, 0, false);
                    }
                }
            }
            refreshDimensions();
        }
        return skeleton;
    }

    @Nullable
    public RigState rigState() {
        skeleton();
        return state;
    }

    /** A pose for drawing (client), separate from the one the hitboxes use. */
    @Nullable
    public RigPose renderPose() {
        skeleton();
        return renderPose;
    }

    /** The creature's size on its rig: the rig's scale times its scale state (a spider's size roll). */
    public float rigScale() {
        RigSkeleton sk = skeleton;
        if (sk == null) {
            return 1;
        }
        float s = sk.def.scale();
        if (sk.def.scaleState().isPresent()) {
            float v = state(sk.def.scaleState().get());
            s *= v > 0 ? v : 1;
        }
        return s;
    }

    /** Fills the rig's inputs from this entity, positions and turns interpolated by {@code partial}. */
    public RigState.Body body(float partial) {
        RigState.Body b = body;
        b.x = Mth.lerp(partial, xo, getX());
        b.y = Mth.lerp(partial, yo, getY());
        b.z = Mth.lerp(partial, zo, getZ());
        b.yaw = Mth.rotLerp(partial, yBodyRotO, yBodyRot);
        b.vx = getX() - xo;
        b.vz = getZ() - zo;
        b.scale = rigScale();
        b.walk = Math.min(1, walkAnimation.speed(partial));
        b.headYaw = Mth.wrapDegrees(Mth.rotLerp(partial, yHeadRotO, yHeadRot) - b.yaw);
        b.headPitch = getViewXRot(partial);
        b.age = tickCount + partial;
        float u = hideProgress(partial);
        b.hide = u <= 0 ? 0 : smooth(Math.min(1, u / CROUCH));
        b.airborne = !onGround() && !isInWater() && !onClimbable() && getDeltaMovement().y < -0.2;
        return b;
    }

    @Override
    public void tick() {
        super.tick();
        RigSkeleton sk = skeleton();
        if (sk == null || state == null) {
            return;
        }
        if (sk.def.hide().isPresent()) {
            hideTick(sk.def.hide().get());
        }
        RigState.Body b = body(1f);
        RigDef.Gait gait = sk.def.gait();
        state.tick(b, this::groundHeight, (leg, x, y, z) -> landed(gait, leg, x, y, z, b.scale));
        if (!isAlive() || sk.parts.length == 0) {
            return;
        }
        if (asBlocks()) { // a pile of blocks: only the body's own box can be hit
            for (int i = 0; i < sk.parts.length && i < MAX_PARTS; i++) {
                parts[i].configure(false, sk.parts[i].name(), 0.1f, 0.1f, 0, false);
                parts[i].follow(getX(), getY(), getZ());
            }
            return;
        }
        pose.pose(state, b, 1f, hasLegParts(sk));
        Matrix4d world = RigState.toWorld(b);
        for (int i = 0; i < sk.parts.length && i < MAX_PARTS; i++) {
            RigSkeleton.Part p = sk.parts[i];
            Vector3f c = pose.point(p.bone(), p.local());
            Vector3d w = world.transformPosition(new Vector3d(c.x, c.y, c.z));
            parts[i].configure(true, p.name(), p.width() * b.scale, p.height() * b.scale, p.damage(), p.solid());
            parts[i].follow(w.x, w.y, w.z);
        }
    }

    /** Whether it wants to be hidden (synced state {@code rig_hidden}); see {@link #hideProgress}. */
    public boolean hidden() {
        return state("rig_hidden") > 0.5f;
    }

    /** How far into hiding, 0 standing to 1 a pile of blocks, interpolated; both sides step it the same way. */
    public float hideProgress(float partial) {
        return Mth.lerp(partial, hidePrev, hideNow);
    }

    /** Fully hidden: drawn only as blocks, no part hitboxes. */
    public boolean asBlocks() {
        return hideNow >= 1f;
    }

    /** The share of the hide that is the crouch (the rest is the settle into blocks). */
    public static final float CROUCH = 0.8f;

    /**
     * Stone giants hiding as rocks, like huorns as trees: with no target within {@code wake} and nothing hurting it for
     * {@code settle} ticks it stops on the block grid, a quarter turn, and over {@code ticks} crouches into its hide
     * pose and settles into blocks; a target near or a hit wakes it the same way back. It doesn't move until it is
     * fully up again.
     */
    private void hideTick(RigDef.Hide hide) {
        if (tickCount <= 1 && hidden()) { // loaded or first seen hidden: already a pile
            hideNow = 1;
        }
        hidePrev = hideNow;
        float step = 1f / Math.max(1, hide.ticks());
        boolean blocksBefore = hideNow >= 1;
        hideNow = hidden() ? Math.min(1, hideNow + step) : Math.max(0, hideNow - step);
        if (blocksBefore != (hideNow >= 1)) {
            refreshDimensions(); // a pile is lower than the creature standing
        }
        if (level().isClientSide) {
            boolean settled = hideNow >= CROUCH;
            if (settled != wasHidden && tickCount > 1) { // dust as it turns to blocks or breaks out of them
                BlockState s = hideBlock(hide);
                double r = getBbWidth() * 0.6;
                for (int i = 0; i < 40; i++) {
                    level().addParticle(new net.minecraft.core.particles.BlockParticleOption(
                            net.minecraft.core.particles.ParticleTypes.BLOCK, s),
                            getX() + (random.nextDouble() - 0.5) * 2 * r, getY() + random.nextDouble() * getBbHeight(),
                            getZ() + (random.nextDouble() - 0.5) * 2 * r, 0, 0.1, 0);
                }
            }
            wasHidden = settled;
            return;
        }
        LivingEntity target = getTarget();
        boolean near = target != null && target.isAlive() && distanceTo(target) <= hide.wake();
        boolean hurtLately = getLastHurtByMob() != null && tickCount - getLastHurtByMobTimestamp() < hide.settle();
        if (near || hurtLately) {
            idle = 0;
            setState("rig_hidden", 0f);
        } else {
            if (target == null || !target.isAlive()) {
                idle++;
            }
            if (!hidden() && idle >= hide.settle() && onGround() && hideNow <= 0) {
                float yaw = Math.round(getYRot() / 90f) * 90f;
                moveTo(Mth.floor(getX()) + 0.5, getY(), Mth.floor(getZ()) + 0.5, yaw, 0);
                setYBodyRot(yaw);
                yBodyRotO = yaw;
                setYHeadRot(yaw);
                setState("rig_hidden", 1f);
            }
        }
        if (hidden() || hideNow > 0) { // hiding, hidden or getting up: hold still, square to the grid
            getNavigation().stop();
            setDeltaMovement(new net.minecraft.world.phys.Vec3(0, getDeltaMovement().y, 0));
            float yaw = Math.round(getYRot() / 90f) * 90f;
            setYRot(yaw);
            setYBodyRot(yaw);
            yBodyRotO = yaw;
            setYHeadRot(yaw);
            yHeadRotO = yaw;
        }
    }

    /** The block a hiding creature looks like (stone if the rig names an unknown one). */
    public static BlockState hideBlock(RigDef.Hide hide) {
        return block(hide.block());
    }

    /** A block by id; stone if there is none of that name. */
    public static BlockState block(String id) {
        net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(id);
        return rl == null ? net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()
                : net.minecraft.core.registries.BuiltInRegistries.BLOCK.getOptional(rl)
                        .orElse(net.minecraft.world.level.block.Blocks.STONE).defaultBlockState();
    }

    private static boolean hasLegParts(RigSkeleton sk) {
        for (RigSkeleton.Part p : sk.parts) {
            for (RigSkeleton.Leg l : sk.legs) {
                if (p.bone() == l.upper() || p.bone() == l.lower()) {
                    return true;
                }
            }
        }
        return false;
    }

    private void landed(RigDef.Gait gait, int leg, double x, double y, double z, float scale) {
        if (level().isClientSide) {
            clientFootfall.accept(new Footfall(this, leg, x, y, z, gait.heavy(), scale));
            return;
        }
        if (gait.stomp() > 0 && isAlive()) {
            double r = 0.35 * scale;
            AABB box = new AABB(x - r, y - 0.1, z - r, x + r, y + 0.6 * scale, z + r);
            for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, box,
                    e -> e != this && e.isAlive() && !sameSide(e) && e.getBbHeight() < getBbHeight())) {
                e.hurt(damageSources().mobAttack(this), gait.stomp());
            }
        }
    }

    /** The top of the highest collision shape in a column between two heights; NaN if none. */
    double groundHeight(double x, double z, double top, double bottom) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int bx = Mth.floor(x);
        int bz = Mth.floor(z);
        for (int y = Mth.floor(top); y >= Mth.floor(bottom); y--) {
            pos.set(bx, y, bz);
            BlockState s = level().getBlockState(pos);
            if (s.isAir()) {
                continue;
            }
            VoxelShape shape = s.getCollisionShape(level(), pos);
            if (!shape.isEmpty()) {
                double h = y + shape.max(Direction.Axis.Y);
                if (h <= top + 0.01) {
                    return h;
                }
            }
        }
        return Double.NaN;
    }

    /** A hit on one of its parts: the part's factor is already applied. */
    public boolean hurtPart(RigPart part, DamageSource source, float amount) {
        return hurt(source, amount);
    }

    private static float smooth(float t) {
        return t * t * (3 - 2 * t);
    }

    @Override
    public boolean isPushable() {
        return hideNow <= 0 && super.isPushable();
    }

    @Override
    public boolean canCollideWith(Entity other) {
        return !(other instanceof RigPart p && p.getParent() == this) && super.canCollideWith(other);
    }

    /** The type's hitbox grown by the rig's scale (the size roll is already in it). */
    @Override
    protected net.minecraft.world.entity.EntityDimensions getDefaultDimensions(net.minecraft.world.entity.Pose p) {
        net.minecraft.world.entity.EntityDimensions d = super.getDefaultDimensions(p);
        if (parts == null) { // still constructing
            return d;
        }
        RigSkeleton sk = skeleton();
        if (sk == null) {
            return d;
        }
        d = d.scale(sk.def.scale());
        if (hideNow >= 1 && sk.voxels().cells.length > 0) {
            int top = 0;
            for (int[] c : sk.voxels().cells) {
                top = Math.max(top, c[1] + 1);
            }
            d = net.minecraft.world.entity.EntityDimensions.scalable(d.width(), Math.min(d.height(), top));
        }
        return d;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        RigSkeleton sk = skeleton;
        AABB box = super.getBoundingBoxForCulling();
        return sk == null ? box : box.inflate(sk.reach * rigScale());
    }
}
