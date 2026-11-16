package com.porcupinestudios.tardisrb.compat.sable.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.collision.PortalCollisionHandler;
import qouteall.imm_ptl.core.ducks.IEEntity;

/** Immersive Portals' cross-portal collision (its redirectHandleCollisions), layered over Sable's ship collision. */
@Mixin(Entity.class)
abstract class PortalCollisionMixin {
    @WrapOperation(method = "move", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;collide(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 tardis_rb$portalCollision(Entity self, Vec3 move, Operation<Vec3> original) {
        PortalCollisionHandler portals = ((IEEntity) self).ip_getPortalCollisionHandler();
        if (IPGlobal.crossPortalCollision && portals != null && portals.hasCollisionEntry()) {
            Vec3 result = portals.handleCollision(self, move);
            return result.lengthSqr() > 400 ? Vec3.ZERO : result; // IP's own sanity cap
        }
        return original.call(self, move);
    }
}
