package com.porcupinestudios.tardisrb.gravity.gc.mixin;


import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.porcupinestudios.tardisrb.gravity.gc.api.GravityChangerAPI;
import com.porcupinestudios.tardisrb.gravity.gc.util.RotationUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity {
    @Shadow
    public abstract void readAdditionalSaveData(CompoundTag nbt);
    
    @Shadow
    public abstract EntityDimensions getDimensions(Pose pose);
    
    @Shadow
    public abstract float getViewYRot(float tickDelta);
    
    
    public LivingEntityMixin(EntityType<?> type, Level world) {
        super(type, world);
    }
    
    @Redirect(
        method = "Lnet/minecraft/world/entity/LivingEntity;travel(Lnet/minecraft/world/phys/Vec3;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;getY()D",
            ordinal = 0
        )
    )
    private double redirect_travel_getY_0(LivingEntity livingEntity) {
        Direction gravityDirection = GravityChangerAPI.getGravityDirection(livingEntity);
        if (gravityDirection == Direction.DOWN) {
            return livingEntity.getY();
        }
        
        return RotationUtil.vecWorldToPlayer(livingEntity.position(), gravityDirection).y;
    }
    
    @Redirect(
        method = "travel(Lnet/minecraft/world/phys/Vec3;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;getY()D",
            ordinal = 1
        )
    )
    private double redirect_travel_getY_1(LivingEntity livingEntity) {
        Direction gravityDirection = GravityChangerAPI.getGravityDirection(livingEntity);
        if (gravityDirection == Direction.DOWN) {
            return livingEntity.getY();
        }
        
        return RotationUtil.vecWorldToPlayer(livingEntity.position(), gravityDirection).y;
    }
    
    @Redirect(
        method = "travel(Lnet/minecraft/world/phys/Vec3;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;getY()D",
            ordinal = 2
        )
    )
    private double redirect_travel_getY_2(LivingEntity livingEntity) {
        Direction gravityDirection = GravityChangerAPI.getGravityDirection(livingEntity);
        if (gravityDirection == Direction.DOWN) {
            return livingEntity.getY();
        }
        
        return RotationUtil.vecWorldToPlayer(livingEntity.position(), gravityDirection).y;
    }
    
    @Redirect(
        method = "travel(Lnet/minecraft/world/phys/Vec3;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;getY()D",
            ordinal = 3
        )
    )
    private double redirect_travel_getY_3(LivingEntity livingEntity) {
        Direction gravityDirection = GravityChangerAPI.getGravityDirection(livingEntity);
        if (gravityDirection == Direction.DOWN) {
            return livingEntity.getY();
        }
        
        return RotationUtil.vecWorldToPlayer(livingEntity.position(), gravityDirection).y;
    }
    
    @ModifyVariable(
        method = "travel(Lnet/minecraft/world/phys/Vec3;)V",
        at = @At(
            value = "INVOKE_ASSIGN",
            target = "Lnet/minecraft/world/entity/LivingEntity;getLookAngle()Lnet/minecraft/world/phys/Vec3;",
            ordinal = 0
        ),
        ordinal = 2
    )
    private Vec3 modify_travel_Vec3d_2(Vec3 vec3d) {
        Direction gravityDirection = GravityChangerAPI.getGravityDirection((Entity) (Object) this);
        if (gravityDirection == Direction.DOWN) {
            return vec3d;
        }
        
        return RotationUtil.vecWorldToPlayer(vec3d, gravityDirection);
    }
    
    @ModifyArg(
        method = "playBlockFallSound",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"
        ),
        index = 0
    )
    private BlockPos modify_playBlockFallSound_getBlockState_0(BlockPos blockPos) {
        Direction gravityDirection = GravityChangerAPI.getGravityDirection((Entity) (Object) this);
        if (gravityDirection == Direction.DOWN) {
            return blockPos;
        }
        
        return BlockPos.containing(this.position().add(RotationUtil.vecPlayerToWorld(0, -0.20000000298023224D, 0, gravityDirection)));
    }
    
    @Redirect(
        method = "hasLineOfSight(Lnet/minecraft/world/entity/Entity;)Z",
        at = @At(
            value = "NEW",
            target = "(DDD)Lnet/minecraft/world/phys/Vec3;",
            ordinal = 0
        )
    )
    private Vec3 redirect_canSee_new_0(double x, double y, double z) {
        Direction gravityDirection = GravityChangerAPI.getGravityDirection((Entity) (Object) this);
        if (gravityDirection == Direction.DOWN) {
            return new Vec3(x, y, z);
        }
        
        return this.getEyePosition();
    }
    
    @Redirect(
        method = "hasLineOfSight(Lnet/minecraft/world/entity/Entity;)Z",
        at = @At(
            value = "NEW",
            target = "(DDD)Lnet/minecraft/world/phys/Vec3;",
            ordinal = 1
        )
    )
    private Vec3 redirect_canSee_new_1(double x, double y, double z, Entity entity) {
        Direction gravityDirection = GravityChangerAPI.getGravityDirection(entity);
        if (gravityDirection == Direction.DOWN) {
            return new Vec3(x, y, z);
        }
        
        return entity.getEyePosition();
    }
    
    @Inject(
        method = "getLocalBoundsForPose(Lnet/minecraft/world/entity/Pose;)Lnet/minecraft/world/phys/AABB;",
        at = @At("RETURN"),
        cancellable = true
    )
    private void inject_getBoundingBox(Pose pose, CallbackInfoReturnable<AABB> cir) {
        Direction gravityDirection = GravityChangerAPI.getGravityDirection((Entity) (Object) this);
        if (gravityDirection == Direction.DOWN) return;
        
        AABB box = cir.getReturnValue();
        if (gravityDirection.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
            box = box.move(0.0D, -1.0E-6D, 0.0D);
        }
        cir.setReturnValue(RotationUtil.boxPlayerToWorld(box, gravityDirection));
    }

    
    @WrapOperation(
        method = "spawnItemParticles",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;add(DDD)Lnet/minecraft/world/phys/Vec3;",
            ordinal = 0
        )
    )
    private Vec3 wrapOperation_spawnItemParticles_add_0(Vec3 vec3d, double x, double y, double z, Operation<Vec3> original) {
        Direction gravityDirection = GravityChangerAPI.getGravityDirection((Entity) (Object) this);
        if (gravityDirection == Direction.DOWN) {
            return original.call(vec3d, x, y, z);
        }
        
        Vec3 rotated = RotationUtil.vecPlayerToWorld(vec3d, gravityDirection);
        return original.call(this.getEyePosition(), rotated.x, rotated.y, rotated.z);
    }
    
    @ModifyVariable(
        method = "spawnItemParticles(Lnet/minecraft/world/item/ItemStack;I)V",
        at = @At(
            value = "INVOKE_ASSIGN",
            target = "Lnet/minecraft/world/phys/Vec3;yRot(F)Lnet/minecraft/world/phys/Vec3;",
            ordinal = 0
        ),
        ordinal = 0
    )
    private Vec3 modify_spawnItemParticles_Vec3d_0(Vec3 vec3d) {
        Direction gravityDirection = GravityChangerAPI.getGravityDirection((Entity) (Object) this);
        if (gravityDirection == Direction.DOWN) {
            return vec3d;
        }
        
        return RotationUtil.vecPlayerToWorld(vec3d, gravityDirection);
    }
    
    @ModifyArgs(
        method = "tickEffects",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"
        )
    )
    private void modify_tickStatusEffects_addParticle_0(Args args) {
        Direction gravityDirection = GravityChangerAPI.getGravityDirection((Entity) (Object) this);
        if (gravityDirection == Direction.DOWN) return;
        
        Vec3 vec3d = this.position().subtract(RotationUtil.vecPlayerToWorld(this.position().subtract(args.get(1), args.get(2), args.get(3)), gravityDirection));
        args.set(1, vec3d.x);
        args.set(2, vec3d.y);
        args.set(3, vec3d.z);
    }
    
    @ModifyArgs(
        method = "makePoofParticles",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V",
            ordinal = 0
        )
    )
    private void modify_addDeathParticless_addParticle_0(Args args) {
        Direction gravityDirection = GravityChangerAPI.getGravityDirection((Entity) (Object) this);
        if (gravityDirection == Direction.DOWN) return;
        
        Vec3 vec3d = this.position().subtract(RotationUtil.vecPlayerToWorld(this.position().subtract(args.get(1), args.get(2), args.get(3)), gravityDirection));
        args.set(1, vec3d.x);
        args.set(2, vec3d.y);
        args.set(3, vec3d.z);
    }
    
    @ModifyVariable(
        method = "isDamageSourceBlocked(Lnet/minecraft/world/damagesource/DamageSource;)Z",
        at = @At(
            value = "INVOKE_ASSIGN",
            target = "Lnet/minecraft/world/entity/LivingEntity;calculateViewVector(FF)Lnet/minecraft/world/phys/Vec3;",
            ordinal = 0
        ),
        ordinal = 1
    )
    private Vec3 modify_blockedByShield_Vec3d_1(Vec3 vec3d) {
        Direction gravityDirection = GravityChangerAPI.getGravityDirection((Entity) (Object) this);
        if (gravityDirection == Direction.DOWN) {
            return vec3d;
        }
        
        return RotationUtil.vecWorldToPlayer(vec3d, gravityDirection);
    }
    
    // TODO shield knockback
//    @ModifyArg(
//        method = "blockedByShield",
//        at = @At(
//            value = "INVOKE",
//            target = "Lnet/minecraft/world/phys/Vec3;vectorTo(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;",
//            ordinal = 0
//        ),
//        index = 0
//    )
//    private Vec3 modify_blockedByShield_relativize_0(Vec3 vec3d) {
//        Direction gravityDirection = GravityChangerAPI.getGravityDirection((Entity)(Object)this);
//        if(gravityDirection == Direction.DOWN) {
//            return vec3d;
//        }
//
//        return this.getEyePosition();
//    }

//    @ModifyVariable(
//        method = "Lnet/minecraft/world/entity/LivingEntity;isDamageSourceBlocked(Lnet/minecraft/world/damagesource/DamageSource;)Z",
//        at = @At(
//            value = "INVOKE_ASSIGN",
//            target = "Lnet/minecraft/world/phys/Vec3;normalize()Lnet/minecraft/world/phys/Vec3;",
//            ordinal = 0
//        ),
//        ordinal = 2
//    )
//    private Vec3 modify_blockedByShield_Vec3d_2(Vec3 vec3d) {
//        Direction gravityDirection = GravityChangerAPI.getGravityDirection((Entity)(Object)this);
//        if(gravityDirection == Direction.DOWN) {
//            return vec3d;
//        }
//
//        return RotationUtil.vecWorldToPlayer(vec3d, gravityDirection);
//    }
    
//    @ModifyConstant(method = "travel(Lnet/minecraft/world/phys/Vec3;)V", constant = @Constant())
//    private double multiplyGravity(double constant) {
//        return constant * GravityChangerAPI.getGravityStrength(this);
//    }
    
    @ModifyVariable(method = "calculateFallDamage(FF)I", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private float diminishFallDamage(float value) {
        return value * (float) Math.sqrt(GravityChangerAPI.getGravityStrength(this));
    }
}
