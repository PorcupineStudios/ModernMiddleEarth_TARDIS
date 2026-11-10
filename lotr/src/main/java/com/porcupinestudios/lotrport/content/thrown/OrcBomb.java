package com.porcupinestudios.lotrport.content.thrown;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A lit orc bomb (LOTREntityOrcBomb): falls like primed TNT and blows up after {@code bomb_fuse} + strength x
 * {@code bomb_fuse_per_strength} ticks with power (strength + 1) x {@code bomb_power_per_strength}; fire bombs set
 * fires. A bomb lit by a player or redstone breaks blocks; one an NPC drops only where mobs may grief.
 */
public class OrcBomb extends Entity implements TraceableEntity {
    private static final EntityDataAccessor<Integer> FUSE =
            SynchedEntityData.defineId(OrcBomb.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<BlockState> BLOCK =
            SynchedEntityData.defineId(OrcBomb.class, EntityDataSerializers.BLOCK_STATE);
    private int strength;
    private boolean fire;
    private boolean byMob;
    @Nullable
    private LivingEntity owner;

    public OrcBomb(EntityType<? extends OrcBomb> type, Level level) {
        super(type, level);
        blocksBuilding = true;
    }

    public OrcBomb(Level level, double x, double y, double z, BlockState bomb, @Nullable LivingEntity owner) {
        this(Thrown.ORC_BOMB.get(), level);
        setPos(x, y, z);
        double a = level.random.nextDouble() * Math.PI * 2;
        setDeltaMovement(-Math.sin(a) * 0.02, 0.2, -Math.cos(a) * 0.02);
        xo = x;
        yo = y;
        zo = z;
        this.owner = owner;
        entityData.set(BLOCK, bomb);
        if (bomb.getBlock() instanceof OrcBombBlock b) {
            strength = b.strength();
            fire = b.fire();
        }
        entityData.set(FUSE, fullFuse());
    }

    private int fullFuse() {
        return (int) (Thrown.num("bomb_fuse", 40) + strength * Thrown.num("bomb_fuse_per_strength", 20));
    }

    /** LOTR's setFuseFromExplosion: a bomb set off by another blast goes soon after. */
    public void fuseFromExplosion() {
        int f = fullFuse();
        entityData.set(FUSE, random.nextInt(Math.max(1, f / 4)) + f / 8);
    }

    /** Dropped by an NPC (LOTR's orc bombardiers): only breaks blocks where mobs may. */
    public OrcBomb byMob() {
        byMob = true;
        return this;
    }

    public int fuse() {
        return entityData.get(FUSE);
    }

    public BlockState block() {
        return entityData.get(BLOCK);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(FUSE, 80);
        builder.define(BLOCK, net.minecraft.world.level.block.Blocks.TNT.defaultBlockState());
    }

    @Override
    protected double getDefaultGravity() {
        return 0.04;
    }

    @Override
    public void tick() {
        applyGravity();
        move(MoverType.SELF, getDeltaMovement());
        setDeltaMovement(getDeltaMovement().scale(0.98));
        if (onGround()) {
            setDeltaMovement(getDeltaMovement().multiply(0.7, -0.5, 0.7));
        }
        int fuse = fuse() - 1;
        entityData.set(FUSE, fuse);
        if (fuse <= 0) {
            discard();
            if (!level().isClientSide) {
                explode();
            }
        } else {
            updateInWaterStateAndDoFluidPushing();
            if (level().isClientSide) {
                level().addParticle(ParticleTypes.SMOKE, getX(), getY() + 0.7, getZ(), 0, 0, 0);
            }
        }
    }

    private void explode() {
        float power = (strength + 1) * Thrown.num("bomb_power_per_strength", 4f);
        Level.ExplosionInteraction how = byMob ? Level.ExplosionInteraction.MOB : Level.ExplosionInteraction.TNT;
        level().explode(this, getX(), getY(0.0625), getZ(), power, fire, how);
    }

    @Nullable
    @Override
    public LivingEntity getOwner() {
        return owner;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putShort("OrcBombFuse", (short) fuse());
        tag.putInt("BombStrengthLevel", strength);
        tag.putBoolean("Fire", fire);
        tag.putBoolean("DroppedByMob", byMob);
        tag.put("Block", net.minecraft.nbt.NbtUtils.writeBlockState(block()));
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(FUSE, (int) tag.getShort("OrcBombFuse"));
        strength = tag.getInt("BombStrengthLevel");
        fire = tag.getBoolean("Fire");
        byMob = tag.getBoolean("DroppedByMob");
        entityData.set(BLOCK, net.minecraft.nbt.NbtUtils.readBlockState(
                level().holderLookup(net.minecraft.core.registries.Registries.BLOCK), tag.getCompound("Block")));
    }
}
