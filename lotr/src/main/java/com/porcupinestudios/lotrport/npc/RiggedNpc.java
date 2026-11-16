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
        RigState.Body b = body(1f);
        RigDef.Gait gait = sk.def.gait();
        state.tick(b, this::groundHeight, (leg, x, y, z) -> landed(gait, leg, x, y, z, b.scale));
        if (!isAlive() || sk.parts.length == 0) {
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
        return sk == null ? d : d.scale(sk.def.scale());
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        RigSkeleton sk = skeleton;
        AABB box = super.getBoundingBoxForCulling();
        return sk == null ? box : box.inflate(sk.reach * rigScale());
    }
}
