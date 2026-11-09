package com.porcupinestudios.lotrport.animal;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
import net.minecraft.world.entity.ai.goal.RunAroundLikeCrazyGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * LOTR's rideable animals (wild boars, elk, giraffes, rhinos, zebras): in LOTR they are horses underneath, so here
 * they are too. Tamed by riding, saddled, bred with their own food once tame. Boars, elk and rhinos fight back.
 */
public class LotrMount extends AbstractHorse {
    static final EntityDataAccessor<Integer> SKIN = SynchedEntityData.defineId(LotrMount.class, EntityDataSerializers.INT);

    public LotrMount(EntityType<? extends LotrMount> type, Level level) {
        super(type, level);
    }

    public AnimalType kind() {
        return LotrAnimals.type(getType());
    }

    @Override
    protected void registerGoals() {
        AnimalType t = kind();
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, t.fights() ? new MeleeAttackGoal(this, 1.2, true) : new PanicGoal(this, 1.2));
        goalSelector.addGoal(1, new RunAroundLikeCrazyGoal(this, 1.2));
        goalSelector.addGoal(2, new BreedGoal(this, 1.0, LotrMount.class));
        goalSelector.addGoal(3, new TemptGoal(this, 1.25, this::isFood, false));
        goalSelector.addGoal(4, new FollowParentGoal(this, 1.0));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.7));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 6.0f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        if (t.fights()) {
            targetSelector.addGoal(1, new HurtByTargetGoal(this));
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SKIN, 0);
    }

    public int skin() {
        return entityData.get(SKIN);
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType,
            @Nullable SpawnGroupData data) {
        entityData.set(SKIN, random.nextInt(Math.max(1, kind().skins().size())));
        return super.finalizeSpawn(level, difficulty, spawnType, data);
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return kind().isFood(stack);
    }

    /** Its own food heals it, grows a foal, and (tame, grown) makes it ready to breed; else the horse's handling. */
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!isVehicle() && isFood(stack) && !stack.is(net.minecraft.tags.ItemTags.HORSE_FOOD)) {
            boolean ate = false;
            if (getHealth() < getMaxHealth()) {
                heal(2.0f);
                ate = true;
            }
            if (isBaby()) {
                ageUp(20);
                ate = true;
            } else if (isTamed() && getAge() == 0 && !isInLove() && canFallInLove()) {
                setInLove(player);
                ate = true;
            }
            if (ate) {
                stack.consume(1, player);
                return InteractionResult.sidedSuccess(level().isClientSide);
            }
        }
        return super.mobInteract(player, hand);
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
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        boolean hit = super.doHurtTarget(target);
        SoundEvent s = AnimalType.Sounds.event(kind().sounds().attack());
        if (hit && s != null) {
            playSound(s, getSoundVolume(), getVoicePitch());
        }
        return hit;
    }

    // ---------------------------------------------------------------- LOTR's mount armor (MountArmorItem)

    @Override
    public boolean canUseSlot(EquipmentSlot slot) {
        return slot == EquipmentSlot.BODY ? kind().armor() != null : super.canUseSlot(slot);
    }

    @Override
    public boolean isBodyArmorItem(ItemStack stack) {
        return stack.getItem() instanceof MountArmorItem m && m.mount().equals(kind().armor());
    }

    @Override
    public boolean canMate(Animal other) {
        return other != this && other.getType() == getType() && other instanceof LotrMount m && canParent()
                && m.canParent();
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob mate) {
        if (!(getType().create(level) instanceof LotrMount child)) {
            return null;
        }
        child.entityData.set(SKIN, (random.nextBoolean() || !(mate instanceof LotrMount m) ? this : m).skin());
        return child;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Skin", skin());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(SKIN, tag.getInt("Skin"));
    }
}
