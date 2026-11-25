package com.porcupinestudios.lotrport.mixin.iris;

import com.porcupinestudios.lotrport.client.ShaderAliases;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Shader packs wave plants and leaves by the vanilla block names (and, in a few packs, tags) in their
 * block.properties. When Iris (1.8) builds a pack's block id map, this gives each LOTR plant, leaf and sapling the id
 * of its vanilla counterpart ({@link ShaderAliases}, data in {@code assets/lotr_port/shaders/block_aliases.json}), so
 * every pack sways them in the wind. Only states the pack itself left without an id are filled. Optional: without
 * Iris, or with an Iris whose method differs, nothing happens ({@code @Pseudo}, require 0).
 */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.shaderpack.materialmap.BlockMaterialMapping", remap = false)
public abstract class IrisBlockIdMixin {
    @Inject(method = "createBlockStateIdMap", at = @At("RETURN"), remap = false, require = 0)
    private static void lotrPort$aliases(CallbackInfoReturnable<Object2IntMap<BlockState>> cir) {
        ShaderAliases.apply(cir.getReturnValue());
    }
}
