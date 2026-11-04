package com.porcupinestudios.tardisrb.gravity.gc.api;

import com.porcupinestudios.tardisrb.gravity.GravityScale;
import com.porcupinestudios.tardisrb.gravity.gc.GravityHolder;
import com.porcupinestudios.tardisrb.gravity.gc.RotationAnimation;
import com.porcupinestudios.tardisrb.gravity.gc.util.RotationUtil;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The calls the ported Gravity Changer mixins make, backed by our own per-entity field ({@link GravityHolder}) instead
 * of Cardinal Components. Changing the direction goes through {@code gravity.GravityDirection}.
 * <p>
 * Remember: when gravity is not down, an entity's delta movement is in its own frame ("local"); use
 * {@link #getWorldVelocity} / {@link #setWorldVelocity} for world-frame velocity.
 */
public final class GravityChangerAPI {
    private GravityChangerAPI() {}

    public static Direction getGravityDirection(Entity entity) {
        if (entity == null) {
            return Direction.DOWN;
        }
        Direction d = ((GravityHolder) entity).tardis_rb$gravity();
        return d == null ? Direction.DOWN : d;
    }

    /**
     * Strength multiplier for the vanilla gravity constants the mixins scale. Living things get theirs through the
     * GRAVITY attribute already ({@link GravityScale}), so only fall damage uses it there.
     */
    public static double getGravityStrength(Entity entity) {
        return entity instanceof LivingEntity ? GravityScale.of(entity) : 1.0;
    }

    public static RotationAnimation getRotationAnimation(Entity entity) {
        return entity == null ? null : ((GravityHolder) entity).tardis_rb$animation();
    }

    public static Vec3 getWorldVelocity(Entity entity) {
        return RotationUtil.vecPlayerToWorld(entity.getDeltaMovement(), getGravityDirection(entity));
    }

    public static void setWorldVelocity(Entity entity, Vec3 worldVelocity) {
        entity.setDeltaMovement(RotationUtil.vecWorldToPlayer(worldVelocity, getGravityDirection(entity)));
    }

    public static void setBaseGravityDirection(Entity entity, Direction direction) {
        com.porcupinestudios.tardisrb.gravity.GravityDirection.set(entity, direction);
    }

    public static Vec3 getEyeOffset(Entity entity) {
        return RotationUtil.vecPlayerToWorld(0.0, entity.getEyeHeight(), 0.0, getGravityDirection(entity));
    }
}
