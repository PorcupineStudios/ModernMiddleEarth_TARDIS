package com.porcupinestudios.tardisrb.compat.sable;

import java.util.List;
import java.util.Set;

import com.bawnorton.mixinsquared.adjuster.MixinAnnotationAdjusterRegistrar;
import net.neoforged.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Sable (Create: Aeronautics' physics) and Immersive Portals both {@code @Redirect} the {@code collide} call in
 * {@code Entity.move}; two redirects on one call crash the game. With Sable loaded we drop IP's redirect (MixinSquared)
 * and put IP's cross-portal collision back as a {@code @WrapOperation} around Sable's ({@link
 * com.porcupinestudios.tardisrb.compat.sable.mixin.PortalCollisionMixin}). Without Sable nothing here applies.
 */
public final class SableMixinPlugin implements IMixinConfigPlugin {
    /**
     * Clashing handlers, as mixin#method. IP's collide redirect: see above. Sable's updatePlayer redirect: IP
     * {@code @Overwrite}s updatePlayer to an empty method (it tracks entities by its own chunk watch records), so Sable's
     * redirect there has nothing to act on and only fails the apply.
     */
    private static final Set<String> DROPPED = Set.of(
            "qouteall.imm_ptl.core.mixin.common.collision.MixinEntity#redirectHandleCollisions",
            "dev.ryanhcode.sable.mixin.entity.entity_tracking.TrackedEntityMixin#sable$trackSubLevelEntities",
            "dev.ryanhcode.sable.mixin.player_standup.PlayerMixin#sable$noCollisionWithSubLevels");

    private boolean sable;

    @Override
    public void onLoad(String mixinPackage) {
        sable = FMLLoader.getLoadingModList().getModFileById("sable") != null;
        if (!sable) return;
        MixinAnnotationAdjusterRegistrar.register((targets, mixin, handler, annotation) ->
                DROPPED.contains(mixin.replace('/', '.') + "#" + handler.name) ? null : annotation);
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return sable;
    }

    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String t, ClassNode c, String m, IMixinInfo i) {}
    @Override public void postApply(String t, ClassNode c, String m, IMixinInfo i) {}
}
