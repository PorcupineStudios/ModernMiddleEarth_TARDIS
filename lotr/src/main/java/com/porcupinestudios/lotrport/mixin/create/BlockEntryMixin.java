package com.porcupinestudios.lotrport.mixin.create;

import com.porcupinestudios.lotrport.compat.create.FactionCrafterBlock;
import com.simibubi.create.AllBlocks;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Create asks {@code AllBlocks.MECHANICAL_CRAFTER.has(state)} wherever it needs a crafter (grids, connected inputs,
 * arms, the block entity): the faction crafters answer yes, so they link into grids like Create's own.
 */
@Mixin(value = BlockEntry.class, remap = false)
public abstract class BlockEntryMixin {
    @Inject(method = "has", at = @At("HEAD"), cancellable = true)
    private void lotr_port$factionCrafter(BlockState state, CallbackInfoReturnable<Boolean> cir) {
        if (state.getBlock() instanceof FactionCrafterBlock && (Object) this == AllBlocks.MECHANICAL_CRAFTER) {
            cir.setReturnValue(true);
        }
    }
}
