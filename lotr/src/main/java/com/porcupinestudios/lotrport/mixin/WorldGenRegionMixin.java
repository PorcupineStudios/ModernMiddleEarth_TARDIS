package com.porcupinestudios.lotrport.mixin;

import com.porcupinestudios.lotrport.compat.Unify;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Worldgen places LOTR's block wherever another mod's feature places its copy of one (compat/Unify). */
@Mixin(WorldGenRegion.class)
public abstract class WorldGenRegionMixin {
    @ModifyVariable(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("HEAD"), argsOnly = true)
    private BlockState lotrPort$unify(BlockState state) {
        return Unify.swap(state);
    }
}
