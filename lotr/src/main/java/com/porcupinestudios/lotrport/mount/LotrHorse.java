package com.porcupinestudios.lotrport.mount;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.porcupinestudios.lotrport.content.ContentData;
import com.porcupinestudios.lotrport.sound.LotrSounds;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.horse.AbstractChestedHorse;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AnimalArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;

/**
 * One of LOTR's mounts (LOTREntityHorse and its subclasses): a vanilla horse underneath, so taming, saddles, the
 * inventory screen, jumping and breeding are vanilla's, with a breed ({@link Mounts}) that sets its looks, what it
 * eats, which armour fits, whether it carries a chest, gives milk or fights back, and LOTR's stat rolls.
 */
public class LotrHorse extends AbstractChestedHorse {
    /** Texture index in the low byte, marking index + 1 in the next (0 = no markings). */
    private static final EntityDataAccessor<Integer> VARIANT =
            SynchedEntityData.defineId(LotrHorse.class, EntityDataSerializers.INT);
    private static final double[] HEALTH = {15, 30};
    private static final double[] JUMP = {0.4, 1.0};
    private static final double[] SPEED = {0.1125, 0.3375};

    /** Set on a mount an NPC spawned riding: it goes when its rider's band does (LOTR's belongsToNPC). */
    private boolean npcOwned;

    public LotrHorse(EntityType<? extends LotrHorse> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return AbstractHorse.createBaseHorseAttributes().add(Attributes.ATTACK_DAMAGE, 2.0);
    }

    public Mounts.Breed breed() {
        return Mounts.breed(getType());
    }

    public JsonObject stats() {
        return Mounts.stats(breed().id());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(VARIANT, 0);
    }

    public int textureIndex() {
        return entityData.get(VARIANT) & 0xFF;
    }

    /** Which of the breed's markings it has, or -1. */
    public int markingIndex() {
        return ((entityData.get(VARIANT) >> 8) & 0xFF) - 1;
    }

    public void setLooks(int texture, int marking) {
        entityData.set(VARIANT, (texture & 0xFF) | ((marking + 1) & 0xFF) << 8);
    }

    // ---- stats ----

    @Override
    protected void randomizeAttributes(RandomSource random) {
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(generateMaxHealth(random::nextInt));
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(generateSpeed(random::nextDouble));
        getAttribute(Attributes.JUMP_STRENGTH).setBaseValue(generateJumpStrength(random::nextDouble));
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
            SpawnGroupData data) {
        SpawnGroupData out = super.finalizeSpawn(level, difficulty, reason, data); // rolls the vanilla horse stats
        Mounts.Breed breed = breed();
        setLooks(breed.textures().isEmpty() ? 0 : random.nextInt(breed.textures().size()),
                breed.markings().isEmpty() ? -1 : random.nextInt(breed.markings().size() + 1) - 1);
        JsonObject stats = stats();
        if (stats.has("spawn")) {
            JsonObject spawn = stats.getAsJsonObject("spawn");
            roll(Attributes.MAX_HEALTH, spawn, "health");
            roll(Attributes.MOVEMENT_SPEED, spawn, "speed");
            roll(Attributes.JUMP_STRENGTH, spawn, "jump");
        }
        if (stats.has("boosts")) {
            Holder<Biome> biome = level.getBiome(blockPosition());
            for (JsonElement e : stats.getAsJsonArray("boosts")) {
                JsonObject b = e.getAsJsonObject();
                if (inBiomes(biome, ContentData.str(b, "biomes", ""))) {
                    boost(b);
                    break;
                }
            }
        }
        applyBreed();
        setHealth(getMaxHealth());
        return out;
    }

    /** One of the breed's spawn changes: {@code mul}, {@code mul_rand} (x 1 + random x n), then {@code min}/{@code max}. */
    private void roll(Holder<Attribute> attribute, JsonObject spawn, String key) {
        if (!spawn.has(key)) {
            return;
        }
        JsonObject op = spawn.getAsJsonObject(key);
        double v = getAttributeBaseValue(attribute);
        v *= ContentData.num(op, "mul", 1f);
        v *= 1 + random.nextFloat() * ContentData.num(op, "mul_rand", 0f);
        if (op.has("min")) {
            v = Math.max(v, op.get("min").getAsDouble());
        }
        if (op.has("max")) {
            v = Math.min(v, op.get("max").getAsDouble());
        }
        getAttribute(attribute).setBaseValue(v);
    }

    /** LOTR's better horses of some lands (Rohan): more health and speed, a higher jump (never past 1 by this). */
    private void boost(JsonObject b) {
        var health = getAttribute(Attributes.MAX_HEALTH);
        health.setBaseValue(health.getBaseValue() * (1 + random.nextFloat() * ContentData.num(b, "health", 0f)));
        var speed = getAttribute(Attributes.MOVEMENT_SPEED);
        speed.setBaseValue(speed.getBaseValue() * (1 + random.nextFloat() * ContentData.num(b, "speed", 0f)));
        var jump = getAttribute(Attributes.JUMP_STRENGTH);
        double old = jump.getBaseValue();
        jump.setBaseValue(Math.min(old + ContentData.num(b, "jump", 0f), Math.max(old, 1.0)));
    }

    private static boolean inBiomes(Holder<Biome> biome, String spec) {
        if (spec.startsWith("#")) {
            ResourceLocation tag = ResourceLocation.tryParse(spec.substring(1));
            return tag != null && biome.is(TagKey.create(Registries.BIOME, tag));
        }
        ResourceLocation id = ResourceLocation.tryParse(spec);
        return id != null && biome.is(id);
    }

    /** The breed's numbers that aren't rolled: its attack. */
    private void applyBreed() {
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(ContentData.num(stats(), "attack", 2f));
    }

    private double[] range(String key, double[] fallback) {
        JsonObject stats = stats();
        if (stats.has(key) && stats.get(key).isJsonArray()) {
            JsonArray a = stats.getAsJsonArray(key);
            return new double[] {a.get(0).getAsDouble(), a.get(1).getAsDouble()};
        }
        return fallback;
    }

    // ---- breeding ----

    @Override
    public boolean canMate(Animal other) {
        return other != this && other.getType() == getType() && canParent() && ((LotrHorse) other).canParent();
    }

    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob other) {
        LotrHorse child = (LotrHorse) getType().create(level);
        if (child != null && other instanceof LotrHorse mate) {
            setOffspringAttributes(mate, child);
            LotrHorse like = random.nextBoolean() ? this : mate;
            child.setLooks(like.textureIndex(), random.nextInt(5) == 0 && !breed().markings().isEmpty()
                    ? random.nextInt(breed().markings().size() + 1) - 1 : like.markingIndex());
            child.applyBreed();
        }
        return child;
    }

    /** A foal's stat is the mean of its parents' and one fresh roll, kept inside the breed's range. */
    @Override
    protected void setOffspringAttributes(AgeableMob mate, AbstractHorse child) {
        inherit(Attributes.MAX_HEALTH, mate, child, range("clamp_health", HEALTH));
        inherit(Attributes.JUMP_STRENGTH, mate, child, range("clamp_jump", JUMP));
        inherit(Attributes.MOVEMENT_SPEED, mate, child, range("clamp_speed", SPEED));
    }

    private void inherit(Holder<Attribute> attribute, AgeableMob mate, AbstractHorse child, double[] range) {
        double fresh = range[0] + random.nextDouble() * (range[1] - range[0]);
        double v = (getAttributeBaseValue(attribute) + mate.getAttributeBaseValue(attribute) + fresh) / 3.0;
        child.getAttribute(attribute).setBaseValue(Mth.clamp(v, range[0], range[1]));
    }

    // ---- food, armour, chest, milk ----

    @Override
    public boolean isFood(ItemStack stack) {
        JsonObject stats = stats();
        if (!stats.has("food")) {
            return super.isFood(stack);
        }
        for (JsonElement e : stats.getAsJsonArray("food")) {
            String s = e.getAsString();
            if (s.startsWith("#")) {
                ResourceLocation tag = ResourceLocation.tryParse(s.substring(1));
                if (tag != null && stack.is(TagKey.create(Registries.ITEM, tag))) {
                    return true;
                }
            } else if (BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(s)) {
                return true;
            }
        }
        return false;
    }

    /** Vanilla knows only its own horse foods; any other food of the breed heals a little and calms it. */
    @Override
    protected boolean handleEating(Player player, ItemStack stack) {
        if (super.handleEating(player, stack)) {
            return true;
        }
        if (!isFood(stack)) {
            return false;
        }
        boolean ate = false;
        if (getHealth() < getMaxHealth()) {
            heal(2f);
            ate = true;
        }
        if (isBaby()) {
            level().addParticle(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER, getRandomX(1), getRandomY() + 0.5,
                    getRandomZ(1), 0, 0, 0);
            if (!level().isClientSide) {
                ageUp(20);
            }
            ate = true;
        } else if (isTamed() && getAge() == 0 && !isInLove() && !level().isClientSide) {
            setInLove(player);
            ate = true;
        }
        if (!isTamed() && getTemper() < getMaxTemper()) {
            if (!level().isClientSide) {
                modifyTemper(3);
            }
            ate = true;
        }
        return ate;
    }

    @Override
    public boolean canUseSlot(EquipmentSlot slot) {
        return true;
    }

    @Override
    public boolean isBodyArmorItem(ItemStack stack) {
        String fits = ContentData.str(stats(), "armour", "");
        if (stack.getItem() instanceof MountArmorItem armour) {
            return armour.mount().equals(fits);
        }
        if (!breed().carpet().isEmpty() && stack.is(ItemTags.WOOL_CARPETS)) {
            return true;
        }
        return "horse".equals(fits) && stack.getItem() instanceof AnimalArmorItem a
                && a.getBodyType() == AnimalArmorItem.BodyType.EQUESTRIAN;
    }

    private boolean carriesChest() {
        return ContentData.bool(stats(), "chest", false);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (getFirstPassenger() instanceof Mob) {
            return InteractionResult.PASS; // an NPC's mount is the NPC's
        }
        ItemStack stack = player.getItemInHand(hand);
        if (stack.is(Items.CHEST) && !carriesChest()) {
            return InteractionResult.PASS;
        }
        if (stack.is(Items.BUCKET) && !isBaby() && ContentData.bool(stats(), "milk", false)) {
            player.playSound(SoundEvents.COW_MILK, 1f, 1f);
            player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, Items.MILK_BUCKET.getDefaultInstance()));
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        return super.mobInteract(player, hand);
    }

    // ---- fighting back ----

    @Override
    protected void registerGoals() {
        super.registerGoals();
        double speed = ContentData.num(stats(), "fights_back", 1.2f);
        goalSelector.addGoal(0, new MeleeAttackGoal(this, speed, false) {
            @Override
            public boolean canUse() {
                return fightsBack() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return fightsBack() && super.canContinueToUse();
            }
        });
        targetSelector.addGoal(1, new HurtByTargetGoal(this) {
            @Override
            public boolean canUse() {
                return fightsBack() && super.canUse();
            }
        });
    }

    /** LOTR's boars, elk and rhinos turn on what hurts them; young ones and ridden ones don't. */
    private boolean fightsBack() {
        return !isBaby() && !isVehicle() && stats().has("fights_back");
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide && getTarget() != null && !fightsBack()) {
            setTarget(null);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide && fightsBack() && source.getEntity() instanceof LivingEntity) {
            sound("angry", null).ifPresent(s -> playSound(s, getSoundVolume(), getVoicePitch()));
        }
        return hurt;
    }

    // ---- NPC riders ----

    /** Makes this an NPC's mount as LOTR does: grown, tame and saddled; it despawns like its rider. */
    public void setNpcOwned() {
        npcOwned = true;
        setAge(0);
        setTamed(true);
        equipSaddle(new ItemStack(Items.SADDLE), (SoundSource) null);
    }

    public boolean npcOwned() {
        return npcOwned;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return npcOwned && getOwnerUUID() == null && !hasCustomName();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Looks", entityData.get(VARIANT));
        tag.putBoolean("NpcOwned", npcOwned);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(VARIANT, tag.getInt("Looks"));
        npcOwned = tag.getBoolean("NpcOwned");
        applyBreed();
    }

    // ---- sounds ----

    private java.util.Optional<SoundEvent> sound(String key, SoundEvent fallback) {
        JsonObject stats = stats();
        if (stats.has("sounds") && stats.getAsJsonObject("sounds").has(key)) {
            String id = stats.getAsJsonObject("sounds").get(key).getAsString();
            if (id.contains(":")) {
                ResourceLocation rl = ResourceLocation.tryParse(id);
                if (rl != null && BuiltInRegistries.SOUND_EVENT.containsKey(rl)) {
                    return java.util.Optional.of(BuiltInRegistries.SOUND_EVENT.get(rl));
                }
            } else {
                var s = LotrSounds.get(id);
                if (s.isPresent()) {
                    return java.util.Optional.of(s.get().value());
                }
            }
        }
        return java.util.Optional.ofNullable(fallback);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return sound("ambient", SoundEvents.HORSE_AMBIENT).orElse(null);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return sound("hurt", SoundEvents.HORSE_HURT).orElse(null);
    }

    @Override
    protected SoundEvent getDeathSound() {
        return sound("death", SoundEvents.HORSE_DEATH).orElse(null);
    }

    @Override
    protected SoundEvent getAngrySound() {
        return sound("angry", SoundEvents.HORSE_ANGRY).orElse(null);
    }

    @Override
    protected SoundEvent getEatingSound() {
        return SoundEvents.HORSE_EAT;
    }
}
