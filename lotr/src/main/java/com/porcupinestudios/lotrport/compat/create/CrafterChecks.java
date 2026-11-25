package com.porcupinestudios.lotrport.compat.create;

import com.simibubi.create.AllBlocks;

/** For GameTests (only called with Create loaded): what Create itself thinks of the faction crafters. */
public final class CrafterChecks {
    private CrafterChecks() {}

    /** Null when all is well, else what is wrong. */
    public static String check() {
        if (FactionCrafters.CRAFTERS.size() < 27) {
            return "only " + FactionCrafters.CRAFTERS.size() + " faction crafters";
        }
        for (var b : FactionCrafters.CRAFTERS) {
            if (!AllBlocks.MECHANICAL_CRAFTER.has(b.get().defaultBlockState())) {
                return b.getId() + " isn't a mechanical crafter to Create (BlockEntryMixin)";
            }
            if (b.get().getBlockEntityType() != FactionCrafters.BLOCK_ENTITY.get()) {
                return b.getId() + " has the wrong block entity type";
            }
        }
        return null;
    }
}
