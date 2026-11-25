package com.porcupinestudios.ringbearer.harness.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.porcupinestudios.ringbearer.harness.content.HarnessSteering;
import dev.ryanhcode.offroad.content.blocks.wheel_mount.WheelMountBlock;
import dev.ryanhcode.offroad.content.blocks.wheel_mount.WheelMountBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Offroad steers a wheel mount by the redstone on its two sides (left minus right, 15 = 30 degrees). On a front wheel of
 * a vehicle with a harness, the harness's steering stands in for that redstone, so the front axle follows the pole like
 * a real wagon's. Offroad's own code then turns the wheel (physics) and syncs it to clients (looks).
 */
@Mixin(value = WheelMountBlockEntity.class, remap = false)
abstract class WheelMountSteeringMixin {
    @WrapOperation(method = "getSteeringSignal", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getSignal(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)I"))
    private int ringbearer_harness$harnessSteers(Level level, BlockPos pos, Direction side, Operation<Integer> original) {
        WheelMountBlockEntity self = (WheelMountBlockEntity) (Object) this;
        Integer signal = HarnessSteering.signalFor(self);
        if (signal == null) {
            return original.call(level, pos, side);
        }
        Direction facing = self.getBlockState().getValue(WheelMountBlock.HORIZONTAL_FACING);
        return side == facing.getClockWise() ? Math.max(0, signal) : Math.max(0, -signal);
    }
}
