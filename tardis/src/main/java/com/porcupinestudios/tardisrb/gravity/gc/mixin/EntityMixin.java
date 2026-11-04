package com.porcupinestudios.tardisrb.gravity.gc.mixin;

import com.porcupinestudios.tardisrb.gravity.gc.api.GravityChangerAPI;
import com.porcupinestudios.tardisrb.gravity.gc.util.RotationUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import java.util.List;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Shadow
    private Vec3 position;
    
    @Shadow
    private EntityDimensions dimensions;
    
    @Shadow
    private float eyeHeight;
    
    @Shadow
    public double xo;
    
    @Shadow
    public double yo;
    
    @Shadow
    public double zo;
    
    @Shadow
    public abstract double getX();
    
    @Shadow
    public abstract Vec3 getEyePosition();
    
    @Shadow
    public abstract double getY();
    
    @Shadow
    public abstract double getZ();
    
    @Shadow
    private Level level;
    
    @Shadow
    public abstract int getBlockX();
    
    @Shadow
    public abstract int getBlockZ();
    
    @Shadow
    public boolean noPhysics;
    
    @Shadow
    public abstract Vec3 getDeltaMovement();
    
    @Shadow
    public abstract boolean isVehicle();
    
    @Shadow
    public abstract AABB getBoundingBox();
    
    @Shadow
    private static Vec3 collideWithShapes(Vec3 movement, AABB entityBoundingBox, List<VoxelShape> collisions) {
        return null;
    }
    
    @Shadow
    public abstract Vec3 position();
    
    
    @Shadow
    public abstract boolean isPassengerOfSameVehicle(Entity entity);
    
    @Shadow
    public abstract void push(double deltaX, double deltaY, double deltaZ);
    
    @Shadow
    protected abstract void onBelowWorld();
    
    @Shadow
    public abstract double getEyeY();
    
    @Shadow
    public abstract float getViewYRot(float tickDelta);
    
    @Shadow
    public abstract float getYRot();
    
    @Shadow
    public abstract float getXRot();
    
    @Shadow
    @Final
    protected RandomSource random;
    
    @Shadow
    public float fallDistance;
    
    @Inject(
        method = "makeBoundingBox()Lnet/minecraft/world/phys/AABB;",
        at = @At("RETURN"),
        cancellable = true
    )
    private void inject_calculateBoundingBox(CallbackInfoReturnable<AABB> cir) {
        Entity entity = ((Entity) (Object) this);
        if (entity instanceof Projectile) return;
        
        Direction gravityDirection = GravityChangerAPI.getGravityDirection(entity);
        if (gravityDirection == Direction.DOWN) return;
        
        AABB box = cir.getReturnValue().move(this.position.reverse());
        if (gravityDirection.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
            box = box.move(0.0D, -1.0E-6D, 0.0D);
        }
        cir.setReturnValue(RotationUtil.boxPlayerToWorld(box, gravityDirection).move(this.position));
    }
    
    @Inject(
        method = "calculateViewVector(FF)Lnet/minecraft/world/phys/Vec3;",
        at = @At("RETURN"),
        cancellable = true
    )
    private void inject_getRotationVector(CallbackInfoReturnable<Vec3> cir) {
        com.porcupinestudios.tardisrb.gravity.Tilt tilt = com.porcupinestudios.tardisrb.gravity.Tilt.of((Entity) (Object) this);
        if (tilt != null) {
            cir.setReturnValue(tilt.toWorld(cir.getReturnValue()));
            return;
        }
        Direction gravityDirection = GravityChangerAPI.getGravityDirection((Entity) (Object) this);
        if (gravityDirection == Direction.DOWN) return;
        
        cir.setReturnValue(RotationUtil.vecPlayerToWorld(cir.getReturnValue(), gravityDirection));
    }
    
    @Inject(
        method = "getBlockPosBelowThatAffectsMyMovement()Lnet/minecraft/core/BlockPos;",
        at = @At("HEAD"),
        cancellable = true
    )
    private void inject_getVelocityAffectingPos(CallbackInfoReturnable<BlockPos> cir) {
        Direction gravityDirection = GravityChangerAPI.getGravityDirection((Entity) (Object) this);
        if (gravityDirection == Direction.DOWN) return;
        
        cir.setReturnValue(BlockPos.containing(this.position.add(Vec3.atLowerCornerOf(gravityDirection.getNormal()).scale(0.5000001D))));
    }
    
    @Inject(
        method = "getEyePosition()Lnet/minecraft/world/phys/Vec3;",
        at = @At("HEAD"),
        cancellable = true
    )
    private void inject_getEyePos(CallbackInfoReturnable<Vec3> cir) {
        Direction gravityDirection = GravityChangerAPI.getGravityDirection((Entity) (Object) this);
        if (gravityDirection == Direction.DOWN) return;
        
        cir.setReturnValue(RotationUtil.vecPlayerToWorld(0.0D, this.eyeHeight, 0.0D, gravityDirection).add(this.position));
    }
    
    @Inject(
        method = "getEyePosition(F)Lnet/minecraft/world/phys/Vec3;",
        at = @At("HEAD"),
        cancellable = true
    )
    private void inject_getCameraPosVec(float tickDelta, CallbackInfoReturnable<Vec3> cir) {
        Direction gravityDirection = GravityChangerAPI.getGravityDirection((Entity) (Object) this);
        if (gravityDirection == Direction.DOWN) return;
        
        Vec3 vec3d = RotationUtil.vecPlayerToWorld(0.0D, this.eyeHeight, 0.0D, gravityDirection);
        
        double d = Mth.lerp(tickDelta, this.xo, this.getX()) + vec3d.x;
        double e = Mth.lerp(tickDelta, this.yo, this.getY()) + vec3d.y;
        double f = Mth.lerp(tickDelta, this.zo, this.getZ()) + vec3d.z;
        cir.setReturnValue(new Vec3(d, e, f));
    }
    
    @Inject(
        method = "getLightLevelDependentMagicValue()F",
        at = @At("HEAD"),
        cancellable = true
    )
    private void inject_getBrightnessAtFEyes(CallbackInfoReturnable<Float> cir) {
        Direction gravityDirection = GravityChangerAPI.getGravityDirection((Entity) (Object) this);
        if (gravityDirection == Direction.DOWN) return;
        
        cir.setReturnValue(this.level.hasChunkAt(this.getBlockX(), this.getBlockZ()) ? this.level.getLightLevelDependentMagicValue(BlockPos.containing(this.getEyePosition())) : 0.0F);
    }
    
    // transform move vector from local to world (the velocity is local)
    @ModifyVariable(
        method = "move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V",
        at = @At("HEAD"),
        ordinal = 0,
        argsOnly = true
    )
    private Vec3 modify_move_Vec3d_0_0(Vec3 vec3d) {
        Direction gravityDirection = GravityChangerAPI.getGravityDirection((Entity) (Object) this);
        if (gravityDirection == Direction.DOWN) {
            return vec3d;
        }
        
        return RotationUtil.vecPlayerToWorld(vec3d, gravityDirection);
    }
    
    // looks like not useful
//    @ModifyArg(
//        method = "move",
//        at = @At(
//            value = "INVOKE",
//            target = "Lnet/minecraft/world/phys/Vec3;multiply(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;",
//            ordinal = 0
//        ),
//        index = 0
//    )
//    private Vec3 modify_move_multiply_0(Vec3 vec3d) {
//        Direction gravityDirection = GravityChangerAPI.getGravityDirection((Entity) (Object) this);
//        if (gravityDirection == Direction.DOWN) {
//            return vec3d;
//        }
//
//        return RotationUtil.maskPlayerToWorld(vec3d, gravityDirection);
//    }
    
    // transform the argument vector back to local coordinate
    @ModifyVariable(
        method = "move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/util/profiling/ProfilerFiller;pop()V",
            ordinal = 0
        ),
        ordinal = 0,
        argsOnly = true
    )
    private Vec3 modify_move_Vec3d_0_1(Vec3 vec3d) {
        Direction gravityDirection = GravityChangerAPI.getGravityDirection((Entity) (Object) this);
        if (gravityDirection == Direction.DOWN) {
            return vec3d;
        }
        
        return RotationUtil.vecWorldToPlayer(vec3d, gravityDirection);
    }
    
    // transform the local variable (result from collide()) to local coordinate
    @ModifyVariable(
        method = "move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/util/profiling/ProfilerFiller;pop()V",
            ordinal = 0
        ),
        ordinal = 1
    )
    private Vec3 modify_move_Vec3d_1(Vec3 vec3d) {
        Direction gravityDirection = GravityChangerAPI.getGravityDirection((Entity) (Object) this);
        if (gravityDirection == Direction.DOWN) {
            return vec3d;
        }
        
        return RotationUtil.vecWorldToPlayer(vec3d, gravityDirection);
    }
    
    @Inject(
        method = "getOnPosLegacy()Lnet/minecraft/core/BlockPos;",
        at = @At("HEAD"),
        cancellable = true
    )
    private void inject_getLandingPos(CallbackInfoReturnable<BlockPos> cir) {
        Direction gravityDirection = GravityChangerAPI.getGravityDirection((Entity) (Object) this);
        if (gravityDirection == Direction.DOWN) return;
        BlockPos blockPos = BlockPos.containing(RotationUtil.vecPlayerToWorld(0.0D, -0.20000000298023224D, 0.0D, gravityDirection).add(this.position));
        cir.setReturnValue(blockPos);
    }
    
    @Shadow
    private static List<VoxelShape> collectColliders(Entity entity, Level level, List<VoxelShape> collisions,
            AABB boundingBox) {
        return null;
    }

    @Shadow
    public abstract float maxUpStep();

    @Shadow
    public abstract boolean onGround();

    /**
     * Collision with turned gravity, rewritten for 1.21.1's {@code collide} (the upstream port's injections into it
     * don't match the new step-up code): the same steps as vanilla, done along the entity's own axes. Takes and
     * returns world-frame movement, like vanilla.
     */
    @Inject(method = "collide(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;", at = @At("HEAD"),
            cancellable = true)
    private void tardis_rb$collide(Vec3 vec, CallbackInfoReturnable<Vec3> cir) {
        Entity self = (Entity) (Object) this;
        Direction g = GravityChangerAPI.getGravityDirection(self);
        if (g == Direction.DOWN) {
            return;
        }
        Vec3 local = RotationUtil.vecWorldToPlayer(vec, g);
        AABB aabb = this.getBoundingBox();
        List<VoxelShape> list = this.level.getEntityCollisions(self, aabb.expandTowards(vec));
        Vec3 done = local.lengthSqr() == 0.0 ? local
                : tardis_rb$collideLocal(local, aabb, collectColliders(self, this.level, list, aabb.expandTowards(vec)), g);
        boolean x = local.x != done.x;
        boolean y = local.y != done.y;
        boolean z = local.z != done.z;
        boolean landed = y && local.y < 0.0;
        float step = this.maxUpStep();
        if (step > 0.0F && (landed || this.onGround()) && (x || z)) {
            AABB from = landed ? aabb.move(RotationUtil.vecPlayerToWorld(0.0, done.y, 0.0, g)) : aabb;
            AABB reach = from.expandTowards(RotationUtil.vecPlayerToWorld(local.x, step, local.z, g));
            if (!landed) {
                reach = reach.expandTowards(RotationUtil.vecPlayerToWorld(0.0, -1.0E-5F, 0.0, g));
            }
            List<VoxelShape> near = collectColliders(self, this.level, list, reach);
            Direction up = RotationUtil.dirPlayerToWorld(Direction.UP, g);
            double sign = up.getAxisDirection().getStep();
            double base = tardis_rb$bottom(from, up);
            it.unimi.dsi.fastutil.floats.FloatArrayList heights = new it.unimi.dsi.fastutil.floats.FloatArrayList();
            for (VoxelShape shape : near) {
                for (double c : shape.getCoords(up.getAxis())) {
                    float h = (float) (c * sign - base);
                    if (h >= 0.0F && h <= step && h != (float) done.y && !heights.contains(h)) {
                        heights.add(h);
                    }
                }
            }
            float[] sorted = heights.toFloatArray();
            java.util.Arrays.sort(sorted);
            for (float h : sorted) {
                Vec3 stepped = tardis_rb$collideLocal(new Vec3(local.x, h, local.z), from, near, g);
                if (stepped.horizontalDistanceSqr() > done.horizontalDistanceSqr()) {
                    double d0 = tardis_rb$bottom(aabb, up) - tardis_rb$bottom(from, up);
                    cir.setReturnValue(RotationUtil.vecPlayerToWorld(stepped.add(0.0, -d0, 0.0), g));
                    return;
                }
            }
        }
        cir.setReturnValue(RotationUtil.vecPlayerToWorld(done, g));
    }

    /** The box's lowest point along the entity's own up axis, in that axis's (signed) coordinate. */
    private static double tardis_rb$bottom(AABB box, Direction up) {
        return up.getAxisDirection() == Direction.AxisDirection.POSITIVE ? box.min(up.getAxis()) : -box.max(up.getAxis());
    }

    /** Vanilla's collideWithShapes along the entity's own axes: local movement in, local movement out. */
    private static Vec3 tardis_rb$collideLocal(Vec3 local, AABB box, List<VoxelShape> shapes, Direction g) {
        if (shapes.isEmpty()) {
            return local;
        }
        double mx = local.x;
        double my = local.y;
        double mz = local.z;
        Direction dx = RotationUtil.dirPlayerToWorld(Direction.EAST, g);
        Direction dy = RotationUtil.dirPlayerToWorld(Direction.UP, g);
        Direction dz = RotationUtil.dirPlayerToWorld(Direction.SOUTH, g);
        if (my != 0.0) {
            my = Shapes.collide(dy.getAxis(), box, shapes, my * dy.getAxisDirection().getStep())
                    * dy.getAxisDirection().getStep();
            if (my != 0.0) {
                box = box.move(RotationUtil.vecPlayerToWorld(0.0, my, 0.0, g));
            }
        }
        boolean zFirst = Math.abs(mx) < Math.abs(mz);
        if (zFirst && mz != 0.0) {
            mz = Shapes.collide(dz.getAxis(), box, shapes, mz * dz.getAxisDirection().getStep())
                    * dz.getAxisDirection().getStep();
            if (mz != 0.0) {
                box = box.move(RotationUtil.vecPlayerToWorld(0.0, 0.0, mz, g));
            }
        }
        if (mx != 0.0) {
            mx = Shapes.collide(dx.getAxis(), box, shapes, mx * dx.getAxisDirection().getStep())
                    * dx.getAxisDirection().getStep();
            if (!zFirst && mx != 0.0) {
                box = box.move(RotationUtil.vecPlayerToWorld(mx, 0.0, 0.0, g));
            }
        }
        if (!zFirst && mz != 0.0) {
            mz = Shapes.collide(dz.getAxis(), box, shapes, mz * dz.getAxisDirection().getStep())
                    * dz.getAxisDirection().getStep();
        }
        return new Vec3(mx, my, mz);
    }

    @ModifyArgs(
            method = "isFree(DDD)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/AABB;move(DDD)Lnet/minecraft/world/phys/AABB;",
                    ordinal = 0
            )
    )
    private void redirect_doesNotCollide_offset_0(Args args) {
        Vec3 rotate = new Vec3(args.get(0), args.get(1), args.get(2));
        rotate = RotationUtil.vecPlayerToWorld(rotate, GravityChangerAPI.getGravityDirection((Entity) (Object) this));
        args.set(0, rotate.x);
        args.set(1, rotate.y);
        args.set(2, rotate.z);
    }

    
    
    @Inject(
        method = "push(Lnet/minecraft/world/entity/Entity;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void inject_pushAwayFrom(Entity entity, CallbackInfo ci) {
        Direction gravityDirection = GravityChangerAPI.getGravityDirection((Entity) (Object) this);
        Direction otherGravityDirection = GravityChangerAPI.getGravityDirection(entity);
        
        if (gravityDirection == Direction.DOWN && otherGravityDirection == Direction.DOWN) return;
        
        ci.cancel();
        
        if (!this.isPassengerOfSameVehicle(entity)) {
            if (!entity.noPhysics && !this.noPhysics) {
                Vec3 entityOffset = entity.getBoundingBox().getCenter().subtract(this.getBoundingBox().getCenter());
                
                {
                    Vec3 playerEntityOffset = RotationUtil.vecWorldToPlayer(entityOffset, gravityDirection);
                    double dx = playerEntityOffset.x;
                    double dz = playerEntityOffset.z;
                    double f = Mth.absMax(dx, dz);
                    if (f >= 0.009999999776482582D) {
                        f = Math.sqrt(f);
                        dx /= f;
                        dz /= f;
                        double g = 1.0D / f;
                        if (g > 1.0D) {
                            g = 1.0D;
                        }
                        
                        dx *= g;
                        dz *= g;
                        dx *= 0.05000000074505806D;
                        dz *= 0.05000000074505806D;
                        if (!this.isVehicle()) {
                            this.push(-dx, 0.0D, -dz);
                        }
                    }
                }
                
                {
                    Vec3 entityEntityOffset = RotationUtil.vecWorldToPlayer(entityOffset, otherGravityDirection);
                    double dx = entityEntityOffset.x;
                    double dz = entityEntityOffset.z;
                    double f = Mth.absMax(dx, dz);
                    if (f >= 0.009999999776482582D) {
                        f = Math.sqrt(f);
                        dx /= f;
                        dz /= f;
                        double g = 1.0D / f;
                        if (g > 1.0D) {
                            g = 1.0D;
                        }
                        
                        dx *= g;
                        dz *= g;
                        dx *= 0.05000000074505806D;
                        dz *= 0.05000000074505806D;
                        if (!entity.isVehicle()) {
                            entity.push(dx, 0.0D, dz);
                        }
                    }
                }
            }
        }
    }
    
    @Inject(
        method = "checkBelowWorld()V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void inject_attemptTickInVoid(CallbackInfo ci) {
        Entity this_ = (Entity) (Object) this;
    
        Direction gravityDirection = GravityChangerAPI.getGravityDirection(this_);
        if (true &&
            this.getY() > (double) (this.level.getMaxBuildHeight() + 256) &&
            gravityDirection == Direction.UP
        ) {
            this.onBelowWorld();
            ci.cancel();
            return;
        }
        
        if (true &&
            gravityDirection.getAxis() != Direction.Axis.Y &&
            fallDistance > 1024
            // TODO also handle reverse gravity strength
        ) {
            this.onBelowWorld();
            ci.cancel();
        }
    }
    
    
    @ModifyVariable(
        method = "updateFluidOnEyes()V",
        at = @At(
            value = "STORE"
        ),
        ordinal = 0
    )
    private double submergedInWaterEyeFix(double value) {
        return this.getEyePosition().y();
    }

    @ModifyVariable(
        method = "updateFluidOnEyes()V",
        at = @At(
            value = "STORE"
        ),
        ordinal = 0
    )
    private double submergedInWaterPosFix(double value) {
        BlockPos blockpos = BlockPos.containing(this.getEyePosition());
        return value;
    }
    @ModifyArg(
            method = "applyGravity",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/Vec3;add(DDD)Lnet/minecraft/world/phys/Vec3;"
            ),
            index = 1
    ) private double multiplyGravity(double x) {
        return x * GravityChangerAPI.getGravityStrength((Entity) (Object) this);
    }
    
}
