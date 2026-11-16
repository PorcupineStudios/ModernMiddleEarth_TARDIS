package com.porcupinestudios.tardisrb.compat.sable.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.mixinhelpers.CanFallAtleastHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Sable's player_standup check (no standing up into a ship), re-made for Immersive Portals: IP {@code @Overwrite}s
 * canPlayerFitWithinBlocksAndEntitiesWhen, which Sable's same-priority wrapper may not touch (SableMixinPlugin drops
 * it). This mixin runs at a higher priority, so it may wrap IP's version.
 */
@Mixin(value = Player.class, priority = 1100)
abstract class PlayerFitMixin {
    @WrapOperation(method = "canPlayerFitWithinBlocksAndEntitiesWhen", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;noCollision(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Z"))
    private boolean tardis_rb$noShipInTheWay(Level level, Entity entity, AABB box, Operation<Boolean> original) {
        return original.call(level, entity, box) && CanFallAtleastHelper.canFallAtleastWithSubLevels(level, box) == null;
    }
}
