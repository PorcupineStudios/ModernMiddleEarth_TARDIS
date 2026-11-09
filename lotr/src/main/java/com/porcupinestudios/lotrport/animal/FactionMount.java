package com.porcupinestudios.lotrport.animal;

import com.porcupinestudios.lotrport.faction.Alignment;
import com.porcupinestudios.lotrport.faction.FactionKills;
import com.porcupinestudios.lotrport.faction.Factions;
import com.porcupinestudios.lotrport.npc.LotrNpc;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RunAroundLikeCrazyGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * LOTR's wargs and spiders (LOTREntityNPCRideable): faction mobs of Mordor, Angmar, Dol Guldur... that hunt the
 * faction's enemies and players it dislikes, and that a player may ride, tame (by riding, like a horse), feed and
 * saddle only with the type's alignment (LOTR: 50) with that faction; below it the mob throws its rider. Wargs wear
 * warg armor and may spawn carrying one of their faction's orcs; spiders have no saddle (tamed is enough), climb
 * walls (ridden: for a while), and poison or slow what they bite by their kind; their size sets their stats.
 */
public class FactionMount extends LotrMount {
    private static final EntityDataAccessor<String> VENOM = SynchedEntityData.defineId(FactionMount.class,
            EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> RIDEABLE = SynchedEntityData.defineId(FactionMount.class,
            EntityDataSerializers.BOOLEAN);
    private static final int CLIMB_TIME = 100; // LOTR's spiders climb 5 seconds with a rider
    private int climbTime;

    public FactionMount(EntityType<? extends FactionMount> type, Level level) {
        super(type, level);
    }

    private boolean spider() {
        return kind().kind().equals("spider");
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        if (spider()) {
            goalSelector.addGoal(2, new LeapAtTargetGoal(this, 0.4f));
        }
        goalSelector.addGoal(3, new MeleeAttackGoal(this, spider() ? 1.2 : 1.6, false));
        goalSelector.addGoal(4, new RunAroundLikeCrazyGoal(this, 1.2));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 1.0));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12.0f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, true, false,
                this::isEnemy));
        if (!spider()) { // LOTR's wargs hunt rabbits and deer now and then
            targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Rabbit.class, 500, false, false, e -> true));
            targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, LotrAnimal.class, 1000, false, false,
                    e -> e instanceof LotrAnimal a && a.kind().id().equals("deer")));
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(VENOM, "none");
        builder.define(RIDEABLE, true);
    }

    public Optional<ResourceLocation> faction() {
        return Optional.ofNullable(kind().faction());
    }

    /** Whom it attacks on sight: enemies of its faction, and players with negative alignment (not its tamer). */
    public boolean isEnemy(LivingEntity e) {
        if (e == this || !(level() instanceof ServerLevel level) || !e.isAlive() || hasPassenger(e)
                || faction().isEmpty()) {
            return false;
        }
        if (e instanceof Player player) {
            return !player.isCreative() && !player.isSpectator() && !isTamed()
                    && Alignment.get(player, faction().get()) < 0;
        }
        Optional<ResourceLocation> theirs = FactionKills.factionOf(e);
        return theirs.isPresent() && Factions.relation(level.getServer(), faction().get(), theirs.get()).isBad();
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType,
            @Nullable SpawnGroupData data) {
        SpawnGroupData out = super.finalizeSpawn(level, difficulty, spawnType, data);
        AnimalType t = kind();
        faction().ifPresent(f -> FactionKills.setFaction(this, f));
        roll(Attributes.MAX_HEALTH, t.health(), t.healthMax());
        roll(Attributes.ATTACK_DAMAGE, t.attack(), t.attackMax());
        AnimalType.Look look = t.rollLook(random);
        if (look != null) {
            entityData.set(SKIN, look.skin());
            entityData.set(VENOM, look.venom());
            entityData.set(RIDEABLE, look.rideable());
            getAttribute(Attributes.SCALE).setBaseValue(look.scale());
            set(Attributes.MAX_HEALTH, look.health());
            set(Attributes.MOVEMENT_SPEED, look.speed());
            set(Attributes.ATTACK_DAMAGE, look.attack());
        }
        setHealth(getMaxHealth());
        // LOTR: a rideable warg brings a rider 1 time in rider_chance
        ServerLevel server = level.getLevel();
        if (rideable() && t.riderChance() > 0 && !t.riders().isEmpty() && random.nextInt(t.riderChance()) == 0
                && spawnType != MobSpawnType.SPAWN_EGG && spawnType != MobSpawnType.COMMAND) {
            int total = t.riders().stream().mapToInt(AnimalType.Rider::weight).sum();
            int roll = random.nextInt(Math.max(1, total));
            for (AnimalType.Rider r : t.riders()) {
                roll -= r.weight();
                if (roll < 0) {
                    LotrNpc.spawn(server, r.npc(), getX(), getY(), getZ()).ifPresent(npc -> npc.startRiding(this, true));
                    break;
                }
            }
        }
        return out;
    }

    private void roll(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> a, double min,
            double max) {
        if (max > min && getAttribute(a) != null) {
            getAttribute(a).setBaseValue(min + random.nextInt((int) (max - min) + 1));
        }
    }

    private void set(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> a, double v) {
        if (!Double.isNaN(v) && getAttribute(a) != null) {
            getAttribute(a).setBaseValue(v);
        }
    }

    public boolean rideable() {
        return entityData.get(RIDEABLE);
    }

    public String venom() {
        return entityData.get(VENOM);
    }

    /** Whether the player has the alignment LOTR asks for riding, feeding and saddling. */
    public boolean allows(Player player) {
        return faction().map(f -> Alignment.get(player, f) >= kind().alignment()).orElse(true);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!rideable() || getTarget() == player) {
            return InteractionResult.PASS;
        }
        if (!allows(player)) {
            if (!level().isClientSide) {
                player.displayClientMessage(Component.translatableWithFallback("chat.lotr_port.mount_alignment",
                        "You need %s alignment with %s",
                        String.format("%+.0f", kind().alignment()), faction().map(f -> Component.translatable(
                                "faction." + f.getNamespace() + "." + f.getPath())).orElse(Component.empty())), true);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        return super.mobInteract(player, hand);
    }

    @Override
    public boolean canFallInLove() {
        return false; // LOTR's wargs and spiders are NPCs: they don't breed
    }

    @Override
    public boolean canMate(Animal other) {
        return false;
    }

    @Override
    public boolean isSaddleable() {
        return !spider() && super.isSaddleable();
    }

    /** Spiders take no saddle: tamed is enough to steer one (LOTR's isMountSaddled). */
    @Override
    public boolean isSaddled() {
        return spider() ? isTamed() : super.isSaddled();
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            return;
        }
        if (getControllingPassenger() instanceof Player player && !allows(player)) {
            ejectPassengers(); // LOTR checks every tick
        }
        if (spider()) {
            if (!(getFirstPassenger() instanceof Player) || onGround()) {
                climbTime = 0;
            } else if (horizontalCollision) {
                climbTime++;
            }
        }
    }

    @Override
    public boolean onClimbable() {
        return spider() ? horizontalCollision && climbTime < CLIMB_TIME : super.onClimbable();
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return !spider() && super.causeFallDamage(distance, multiplier, source);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return !(spider() && source.is(DamageTypes.FALL)) && super.hurt(source, amount);
    }

    /** LOTR's venom: difficulty * (difficulty + 5) / 2 seconds of slowness or poison. */
    @Override
    public boolean doHurtTarget(Entity target) {
        if (!super.doHurtTarget(target)) {
            return false;
        }
        int d = level().getDifficulty().getId();
        int seconds = d * (d + 5) / 2;
        if (target instanceof LivingEntity living && seconds > 0) {
            if (venom().equals("slowness")) {
                living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, seconds * 20), this);
            } else if (venom().equals("poison")) {
                living.addEffect(new MobEffectInstance(MobEffects.POISON, seconds * 20), this);
            }
        }
        return true;
    }

    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        return !(venom().equals("slowness") && effect.is(MobEffects.MOVEMENT_SLOWDOWN))
                && !(venom().equals("poison") && effect.is(MobEffects.POISON)) && super.canBeAffected(effect);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return !isTamed() && !isVehicle() && !hasCustomName();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Venom", venom());
        tag.putBoolean("Rideable", rideable());
        tag.putInt("ClimbTime", climbTime);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(VENOM, tag.contains("Venom") ? tag.getString("Venom") : "none");
        entityData.set(RIDEABLE, !tag.contains("Rideable") || tag.getBoolean("Rideable"));
        climbTime = tag.getInt("ClimbTime");
        faction().ifPresent(f -> FactionKills.setFaction(this, f));
    }
}
