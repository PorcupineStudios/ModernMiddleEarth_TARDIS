package com.porcupinestudios.tardisrb.gravity.gc.mixin;

import com.porcupinestudios.tardisrb.gravity.gc.GravityHolder;
import com.porcupinestudios.tardisrb.gravity.gc.RotationAnimation;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Gives every entity a gravity direction field, saved as {@code tardis_rb:gravity} when it isn't down. */
@Mixin(Entity.class)
public abstract class EntityGravityDataMixin implements GravityHolder {
    @Unique
    private static final String TARDIS_RB$KEY = "tardis_rb:gravity";

    @Unique
    private Direction tardis_rb$gravity = Direction.DOWN;

    @Unique
    private RotationAnimation tardis_rb$animation;

    @Override
    public Direction tardis_rb$gravity() {
        return tardis_rb$gravity;
    }

    @Override
    public void tardis_rb$setGravityField(Direction direction) {
        tardis_rb$gravity = direction;
    }

    @Override
    public RotationAnimation tardis_rb$animation() {
        Entity self = (Entity) (Object) this;
        if (tardis_rb$animation == null && self.level() != null && self.level().isClientSide()) {
            tardis_rb$animation = new RotationAnimation();
        }
        return tardis_rb$animation;
    }

    @Inject(method = "saveWithoutId", at = @At("RETURN"))
    private void tardis_rb$save(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
        if (tardis_rb$gravity != null && tardis_rb$gravity != Direction.DOWN) {
            cir.getReturnValue().putString(TARDIS_RB$KEY, tardis_rb$gravity.getName());
        }
    }

    @Inject(method = "load", at = @At("RETURN"))
    private void tardis_rb$load(CompoundTag tag, CallbackInfo ci) {
        if (tag.contains(TARDIS_RB$KEY)) {
            Direction d = Direction.byName(tag.getString(TARDIS_RB$KEY));
            if (d != null && d != tardis_rb$gravity) {
                tardis_rb$gravity = d;
                Entity self = (Entity) (Object) this;
                self.setBoundingBox(((EntityAccessor) self).gc_makeBoundingBox());
            }
        }
    }
}
