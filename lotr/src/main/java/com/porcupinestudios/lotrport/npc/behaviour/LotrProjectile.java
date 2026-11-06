package com.porcupinestudios.lotrport.npc.behaviour;

import com.porcupinestudios.lotrport.npc.LotrEntities;
import com.porcupinestudios.lotrport.sound.LotrSounds;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * The things LOTR's creatures throw, drawn as their item:
 * <ul>
 * <li>{@code ROCK} (mountain trolls, LOTREntityThrownRock): the troll's thrown-rock damage (5), smashes with
 * {@code troll.rockSmash} and stone dust;</li>
 * <li>{@code SNOW} (snow trolls, LOTREntityTrollSnowball): 3 damage and a puff of snow;</li>
 * <li>{@code WRAITH} (marsh-wraiths, LOTREntityMarshWraithBall): 5 damage and 5 s of slowness.</li>
 * </ul>
 */
public class LotrProjectile extends ThrowableItemProjectile {
    public enum Kind {
        ROCK, SNOW, WRAITH
    }

    private static final EntityDataAccessor<Integer> KIND =
            SynchedEntityData.defineId(LotrProjectile.class, EntityDataSerializers.INT);
    private float damage = 5f;

    public LotrProjectile(EntityType<? extends LotrProjectile> type, Level level) {
        super(type, level);
    }

    public LotrProjectile(Level level, LivingEntity thrower, Kind kind, float damage) {
        super(LotrEntities.PROJECTILE.get(), thrower, level);
        entityData.set(KIND, kind.ordinal());
        this.damage = damage;
        setItem(new net.minecraft.world.item.ItemStack(itemFor(kind)));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(KIND, 0);
    }

    public Kind kind() {
        return Kind.values()[Math.floorMod(entityData.get(KIND), Kind.values().length)];
    }

    private static Item itemFor(Kind kind) {
        return switch (kind) {
            case ROCK -> Items.COBBLESTONE;
            case SNOW -> Items.SNOWBALL;
            case WRAITH -> Items.ENDER_EYE;
        };
    }

    @Override
    protected Item getDefaultItem() {
        return Items.COBBLESTONE;
    }

    @Override
    protected double getDefaultGravity() {
        return kind() == Kind.WRAITH ? 0.0 : 0.03;
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        if (hit.getEntity() == getOwner()) {
            return;
        }
        hit.getEntity().hurt(damageSources().thrown(this, getOwner()), damage);
        if (kind() == Kind.WRAITH && hit.getEntity() instanceof LivingEntity living) {
            living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5 * 20, 0));
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        switch (kind()) {
            case ROCK -> {
                LotrSounds.playAt(level, getX(), getY(), getZ(), "troll.rock_smash", SoundSource.HOSTILE, 2f,
                        0.7f + random.nextFloat() * 0.2f);
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
                        getX(), getY(), getZ(), 15, 0.3, 0.3, 0.3, 0.1);
            }
            case SNOW -> level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM,
                    new net.minecraft.world.item.ItemStack(Items.SNOWBALL)), getX(), getY(), getZ(), 8, 0.1, 0.1,
                    0.1, 0.05);
            case WRAITH -> level.sendParticles(ParticleTypes.SMOKE, getX(), getY(), getZ(), 10, 0.2, 0.2, 0.2, 0.02);
        }
        discard();
    }
}
