package com.porcupinestudios.tardisrb.compat.sable.mixin;

import dev.ryanhcode.sable.util.SubLevelInclusiveLevelEntityGetter;
import net.minecraft.world.level.entity.EntityLookup;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.LevelEntityGetter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import qouteall.imm_ptl.core.mixin.common.mc_util.IELevelEntityGetterAdapter;

/**
 * Sable wraps each level's entity getter; Immersive Portals casts that getter to its accessor for vanilla's
 * LevelEntityGetterAdapter (McHelper.traverseEntities) and crashes. Answer for the wrapped vanilla getter.
 * IP's traversals then skip ship-borne entities, as they did before Sable.
 */
@Mixin(value = SubLevelInclusiveLevelEntityGetter.class, remap = false)
abstract class SubLevelEntityGetterMixin implements IELevelEntityGetterAdapter {
    @Shadow @Final private LevelEntityGetter<?> delegate;

    @Override
    public EntitySectionStorage<?> getCache() {
        return ((IELevelEntityGetterAdapter) delegate).getCache();
    }

    @Override
    public EntityLookup<?> getIndex() {
        return ((IELevelEntityGetterAdapter) delegate).getIndex();
    }
}
