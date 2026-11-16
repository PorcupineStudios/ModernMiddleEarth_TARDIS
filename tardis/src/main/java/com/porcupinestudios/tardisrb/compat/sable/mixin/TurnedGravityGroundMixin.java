package com.porcupinestudios.tardisrb.compat.sable.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.porcupinestudios.tardisrb.gravity.gc.api.GravityChangerAPI;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Sable recomputes an entity's collision flags in world axes (landing = moving toward world down), which is wrong for
 * turned gravity: someone standing on a ceiling never counts as on the ground. By this call the gravity port has turned
 * both movements back into the entity's own axes, so recompute the flags from them. Down-gravity keeps Sable's values.
 */
@Mixin(Entity.class)
abstract class TurnedGravityGroundMixin {
    @WrapOperation(method = "move", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;setOnGroundWithMovement(ZLnet/minecraft/world/phys/Vec3;)V"))
    private void tardis_rb$localGround(Entity self, boolean onGround, Vec3 collided, Operation<Void> original,
                                       @Local(argsOnly = true) Vec3 move) {
        if (GravityChangerAPI.getGravityDirection(self) == Direction.DOWN) {
            original.call(self, onGround, collided);
            return;
        }
        boolean vertical = !Mth.equal(move.y, collided.y);
        boolean below = vertical && move.y < 0;
        original.call(self, below, collided);
        // Sable's wrapper may sit inside this one and overwrite the flags; set them last either way.
        self.horizontalCollision = !Mth.equal(move.x, collided.x) || !Mth.equal(move.z, collided.z);
        self.verticalCollision = vertical;
        self.verticalCollisionBelow = below;
        self.setOnGroundWithMovement(below, collided);
    }
}
