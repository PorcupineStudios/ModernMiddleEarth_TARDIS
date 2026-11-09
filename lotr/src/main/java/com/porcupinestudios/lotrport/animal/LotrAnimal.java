package com.porcupinestudios.lotrport.animal;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * A LOTR animal that walks, grazes and breeds (deer, aurochs, bears, lions, gemsbok...): one class for every type,
 * the {@link AnimalType} decides. Those that fight (aurochs, bears, lions) turn on whoever hurts them, as in LOTR;
 * the rest flee. Each has a random skin and a sex (a deer's antlers are the male's).
 */
public class LotrAnimal extends Animal {
    static final EntityDataAccessor<Integer> SKIN = SynchedEntityData.defineId(LotrAnimal.class, EntityDataSerializers.INT);
    static final EntityDataAccessor<Boolean> MALE = SynchedEntityData.defineId(LotrAnimal.class,
            EntityDataSerializers.BOOLEAN);

    public LotrAnimal(EntityType<? extends LotrAnimal> type, Level level) {
        super(type, level);
    }

    public AnimalType kind() {
        return LotrAnimals.type(getType());
    }

    @Override
    protected void registerGoals() {
        AnimalType t = kind();
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, t.fights() ? new MeleeAttackGoal(this, 1.3, true) : new PanicGoal(this, 1.6));
        goalSelector.addGoal(2, new BreedGoal(this, 1.0));
        goalSelector.addGoal(3, new TemptGoal(this, 1.2, this::isFood, false));
        goalSelector.addGoal(4, new FollowParentGoal(this, 1.1));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 1.0));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        if (t.fights()) {
            targetSelector.addGoal(1, new HurtByTargetGoal(this));
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SKIN, 0);
        builder.define(MALE, false);
    }

    public int skin() {
        return entityData.get(SKIN);
    }

    public boolean isMale() {
        return entityData.get(MALE);
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType,
            @Nullable SpawnGroupData data) {
        entityData.set(SKIN, random.nextInt(Math.max(1, kind().skins().size())));
        entityData.set(MALE, random.nextBoolean());
        return super.finalizeSpawn(level, difficulty, spawnType, data);
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return kind().isFood(stack);
    }

    // ---------------------------------------------------------------- LOTR's sounds (AnimalType.Sounds)

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        SoundEvent s = AnimalType.Sounds.event(kind().sounds().ambient());
        return s != null ? s : super.getAmbientSound();
    }

    @Nullable
    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        SoundEvent s = AnimalType.Sounds.event(kind().sounds().hurt());
        return s != null ? s : super.getHurtSound(source);
    }

    @Nullable
    @Override
    protected SoundEvent getDeathSound() {
        SoundEvent s = AnimalType.Sounds.event(kind().sounds().death());
        return s != null ? s : super.getDeathSound();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        SoundEvent s = AnimalType.Sounds.event(kind().sounds().step());
        if (s != null) {
            playSound(s, 0.15f, 1.0f);
        } else { // LOTR's default: the block's own step sound
            SoundType type = state.getSoundType(level(), pos, this);
            playSound(type.getStepSound(), type.getVolume() * 0.15f, type.getPitch());
        }
    }

    @Override
    protected float getSoundVolume() {
        return kind().sounds().volume();
    }

    @Override
    public int getAmbientSoundInterval() {
        return kind().sounds().interval();
    }

    @Override
    public boolean canMate(Animal other) {
        return other.getType() == getType() && super.canMate(other); // every LOTR animal shares this class
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob mate) {
        if (!(getType().create(level) instanceof LotrAnimal child)) {
            return null;
        }
        child.entityData.set(SKIN, (random.nextBoolean() || !(mate instanceof LotrAnimal m) ? this : m).skin());
        child.entityData.set(MALE, random.nextBoolean());
        return child;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Skin", skin());
        tag.putBoolean("Male", isMale());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(SKIN, tag.getInt("Skin"));
        entityData.set(MALE, tag.getBoolean("Male"));
    }
}
