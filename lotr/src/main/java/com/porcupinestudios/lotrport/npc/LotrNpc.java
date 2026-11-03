package com.porcupinestudios.lotrport.npc;

import com.porcupinestudios.lotrport.faction.Alignment;
import com.porcupinestudios.lotrport.faction.FactionKills;
import com.porcupinestudios.lotrport.faction.Factions;
import com.porcupinestudios.lotrport.quest.Quests;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;

/**
 * Every LOTR person: one entity whose {@link NpcType} (from data) says who it is. Clients only get what they draw
 * (type id for the name, skin texture); size comes through the scale attribute. Traders use the vanilla trading screen;
 * captains hire out units through {@link Hiring}; hired units follow or guard for their owner and get a
 * {@link People} record.
 */
public class LotrNpc extends PathfinderMob implements Merchant {
    private static final EntityDataAccessor<String> TYPE =
            SynchedEntityData.defineId(LotrNpc.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> SKIN =
            SynchedEntityData.defineId(LotrNpc.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> TALL_SKIN =
            SynchedEntityData.defineId(LotrNpc.class, EntityDataSerializers.BOOLEAN);
    /** How far a trading or hiring player may stand. */
    public static final double DEAL_DISTANCE = 8;

    /** What a hired unit does. */
    public enum Command { NONE, FOLLOW, GUARD }

    private String personName = "";
    @Nullable
    private UUID owner;
    private Command command = Command.NONE;
    @Nullable
    private BlockPos guardPos;
    @Nullable
    private Player tradingPlayer;
    @Nullable
    private MerchantOffers offers;

    public LotrNpc(EntityType<? extends LotrNpc> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.ATTACK_DAMAGE, 2)
                .add(Attributes.FOLLOW_RANGE, 24);
    }

    /** Makes an NPC of the given type and adds it to the world; empty if the type is unknown. */
    public static Optional<LotrNpc> spawn(ServerLevel level, ResourceLocation typeId, double x, double y, double z) {
        Optional<NpcType> type = NpcData.type(typeId);
        if (type.isEmpty()) {
            return Optional.empty();
        }
        LotrNpc npc = LotrEntities.NPC.get().create(level);
        if (npc == null) {
            return Optional.empty();
        }
        npc.moveTo(x, y, z, level.random.nextFloat() * 360f, 0f);
        npc.setNpcType(typeId, type.get());
        level.addFreshEntity(npc);
        return Optional.of(npc);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(TYPE, "");
        builder.define(SKIN, "");
        builder.define(TALL_SKIN, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.2, false));
        goalSelector.addGoal(2, new FollowOwnerGoal());
        goalSelector.addGoal(3, new GuardGoal());
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8) {
            @Override
            public boolean canUse() {
                return command == Command.NONE && tradingPlayer == null && super.canUse();
            }
        });
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this) {
            @Override
            public boolean canUse() {
                LivingEntity attacker = getLastHurtByMob();
                return attacker != null && !attacker.getUUID().equals(owner) && super.canUse();
            }
        });
        targetSelector.addGoal(2, new DefendOwnerGoal());
        targetSelector.addGoal(3,
                new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, true, false, this::isEnemy));
    }

    // ---------------------------------------------------------------- type

    /** Sets who this NPC is: faction, stats, skin, gear and name. */
    public void setNpcType(ResourceLocation id, NpcType type) {
        entityData.set(TYPE, id.toString());
        FactionKills.setFaction(this, type.faction().orElse(null));
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(type.health());
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(type.speed());
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(type.attack());
        getAttribute(Attributes.SCALE).setBaseValue(type.scale());
        setHealth(getMaxHealth());
        List<ResourceLocation> skins = type.skins();
        entityData.set(SKIN, skins.isEmpty() ? "" : skins.get(random.nextInt(skins.size())).toString());
        entityData.set(TALL_SKIN, type.skinHeight() == 64);
        type.gear().forEach((slot, items) -> {
            if (items.isEmpty()) {
                return;
            }
            ResourceLocation item = items.get(random.nextInt(items.size()));
            try {
                setItemSlot(EquipmentSlot.byName(slot), new ItemStack(BuiltInRegistries.ITEM.get(item)));
            } catch (IllegalArgumentException e) {
                // unknown slot name: data problem, skip it
            }
        });
        List<String> names = type.names().map(NpcData::names).orElse(List.of());
        personName = names.isEmpty() ? "" : names.get(random.nextInt(names.size()));
        if (!personName.isEmpty() && (type.trader() || type.captain())) {
            setCustomName(Component.literal(personName + ", ").append(typeName()));
        }
    }

    public ResourceLocation typeId() {
        ResourceLocation id = ResourceLocation.tryParse(entityData.get(TYPE));
        return id == null ? LotrEntities.NPC.getId() : id;
    }

    @Nullable
    public NpcType npcType() {
        return NpcData.type(typeId()).orElse(null);
    }

    /** The skin texture (client and server). */
    public String skin() {
        return entityData.get(SKIN);
    }

    /** Whether the skin is 64x64 (newer layout) rather than the classic 64x32. */
    public boolean tallSkin() {
        return entityData.get(TALL_SKIN);
    }

    public String personName() {
        return personName;
    }

    public Optional<ResourceLocation> faction() {
        return FactionKills.factionOf(this);
    }

    public Component typeName() {
        ResourceLocation id = typeId();
        return Component.translatable("npc." + id.getNamespace() + "." + id.getPath());
    }

    @Override
    protected Component getTypeName() {
        return entityData.get(TYPE).isEmpty() ? super.getTypeName() : typeName();
    }

    // ---------------------------------------------------------------- hiring

    public Optional<UUID> owner() {
        return Optional.ofNullable(owner);
    }

    public boolean isOwnedBy(Player player) {
        return owner != null && owner.equals(player.getUUID());
    }

    public Command command() {
        return command;
    }

    /** Makes this NPC a hired unit of {@code player}, following them. */
    public void hireTo(ServerPlayer player) {
        owner = player.getUUID();
        setPersistenceRequired();
        setCommand(Command.FOLLOW);
    }

    public void setCommand(Command command) {
        this.command = command;
        guardPos = command == Command.GUARD ? blockPosition() : null;
        getNavigation().stop();
    }

    @Nullable
    private Player ownerPlayer() {
        return owner == null ? null : level().getPlayerByUUID(owner);
    }

    @Override
    public boolean requiresCustomPersistence() {
        NpcType type = npcType();
        return owner != null || (type != null && (type.trader() || type.captain())) || super.requiresCustomPersistence();
    }

    // ---------------------------------------------------------------- fighting

    /** Whom this NPC attacks on sight: enemies of its faction, players it dislikes, and monsters if it is hired. */
    public boolean isEnemy(LivingEntity e) {
        if (e == this || !(level() instanceof ServerLevel level) || !e.isAlive()) {
            return false;
        }
        Optional<ResourceLocation> mine = faction();
        if (e instanceof Player player) {
            if (player.isCreative() || player.isSpectator() || isOwnedBy(player)) {
                return false;
            }
            NpcType type = npcType();
            return type != null && type.hostile() && mine.isPresent() && owner == null
                    && Alignment.get(player, mine.get()) < 0;
        }
        if (e instanceof LotrNpc other && owner != null && owner.equals(other.owner)) {
            return false;
        }
        Optional<ResourceLocation> theirs = FactionKills.factionOf(e);
        if (mine.isPresent() && theirs.isPresent()) {
            return Factions.relation(level.getServer(), mine.get(), theirs.get()).isBad();
        }
        return owner != null && e instanceof Enemy;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel level) {
            People.get(level.getServer()).died(level.getServer(), this);
        }
    }

    // ---------------------------------------------------------------- talking to players

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || !isAlive()) {
            return InteractionResult.PASS;
        }
        if (level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer sp)) {
            return InteractionResult.PASS;
        }
        if (isOwnedBy(sp)) {
            if (sp.isShiftKeyDown()) {
                setCommand(command == Command.FOLLOW ? Command.GUARD : Command.FOLLOW);
            }
            sp.displayClientMessage(Component.translatableWithFallback("npc.lotr_port.command." + command.name()
                    .toLowerCase(), command == Command.FOLLOW ? "%s is following you" : "%s is guarding here",
                    getDisplayName()), true);
            return InteractionResult.SUCCESS;
        }
        NpcType type = npcType();
        if (type != null && owner == null && Quests.interact(sp, this)) {
            return InteractionResult.SUCCESS;
        }
        if (type == null || !(type.trader() || type.captain()) || owner != null) {
            return InteractionResult.PASS;
        }
        if (!willDealWith(sp)) {
            sp.sendSystemMessage(Component.translatableWithFallback("npc.lotr_port.refuses",
                    "%s won't deal with you: you need %s alignment with %s", getDisplayName(),
                    type.tradeAlignment(), faction().map(f -> Component.translatable(
                            "faction." + f.getNamespace() + "." + f.getPath())).orElse(Component.empty())));
            return InteractionResult.SUCCESS;
        }
        if (type.captain() && (!type.trader() || sp.isShiftKeyDown())) {
            Hiring.offer(sp, this);
        } else {
            setTradingPlayer(sp);
            openTradingScreen(sp, getDisplayName(), 1);
        }
        return InteractionResult.SUCCESS;
    }

    /** Whether this trader or captain deals with the player (alignment with its faction). */
    public boolean willDealWith(Player player) {
        NpcType type = npcType();
        if (type == null) {
            return false;
        }
        return faction().map(f -> Alignment.get(player, f) >= type.tradeAlignment()).orElse(true);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide && tradingPlayer != null) {
            if (!tradingPlayer.isAlive() || tradingPlayer.distanceTo(this) > DEAL_DISTANCE
                    || !(tradingPlayer.containerMenu instanceof net.minecraft.world.inventory.MerchantMenu)) {
                setTradingPlayer(null);
            } else {
                getNavigation().stop();
                getLookControl().setLookAt(tradingPlayer);
            }
        }
    }

    // ---------------------------------------------------------------- Merchant (vanilla trading screen)

    @Override
    public void setTradingPlayer(@Nullable Player player) {
        tradingPlayer = player;
    }

    @Nullable
    @Override
    public Player getTradingPlayer() {
        return tradingPlayer;
    }

    @Override
    public MerchantOffers getOffers() {
        if (offers == null) {
            offers = level().isClientSide ? new MerchantOffers() : NpcTrading.offers(this);
        }
        return offers;
    }

    @Override
    public void overrideOffers(MerchantOffers offers) {
        this.offers = offers;
    }

    @Override
    public void notifyTrade(MerchantOffer offer) {
        offer.increaseUses();
        ambientSoundTime = -getAmbientSoundInterval();
    }

    @Override
    public void notifyTradeUpdated(ItemStack stack) {}

    @Override
    public int getVillagerXp() {
        return 0;
    }

    @Override
    public void overrideXp(int xp) {}

    @Override
    public boolean showProgressBar() {
        return false;
    }

    @Override
    public SoundEvent getNotifyTradeSound() {
        return SoundEvents.VILLAGER_YES;
    }

    @Override
    public boolean isClientSide() {
        return level().isClientSide;
    }

    // ---------------------------------------------------------------- saving

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("NpcType", entityData.get(TYPE));
        tag.putString("Skin", entityData.get(SKIN));
        tag.putBoolean("TallSkin", entityData.get(TALL_SKIN));
        tag.putString("PersonName", personName);
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        tag.putString("Command", command.name());
        if (guardPos != null) {
            tag.put("GuardPos", NbtUtils.writeBlockPos(guardPos));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(TYPE, tag.getString("NpcType"));
        entityData.set(SKIN, tag.getString("Skin"));
        entityData.set(TALL_SKIN, tag.getBoolean("TallSkin"));
        personName = tag.getString("PersonName");
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        try {
            command = Command.valueOf(tag.getString("Command"));
        } catch (IllegalArgumentException e) {
            command = Command.NONE;
        }
        guardPos = NbtUtils.readBlockPos(tag, "GuardPos").orElse(null);
        // Structure templates hold only the type and "Fresh": roll skin, gear and name when placed.
        if (tag.getBoolean("Fresh") && !level().isClientSide) {
            ResourceLocation id = ResourceLocation.tryParse(tag.getString("NpcType"));
            Optional<NpcType> type = id == null ? Optional.empty() : NpcData.type(id);
            if (type.isPresent()) {
                setNpcType(id, type.get());
            }
        }
    }

    // ---------------------------------------------------------------- goals for hired units

    /** Walks after the owner; catches up by teleport when far behind, as tamed wolves do. */
    private final class FollowOwnerGoal extends Goal {
        FollowOwnerGoal() {
            setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            Player o = ownerPlayer();
            return command == Command.FOLLOW && o != null && !o.isSpectator() && distanceTo(o) > 5;
        }

        @Override
        public boolean canContinueToUse() {
            Player o = ownerPlayer();
            return command == Command.FOLLOW && o != null && distanceTo(o) > 2.5 && !getNavigation().isDone();
        }

        @Override
        public void start() {
            follow();
        }

        @Override
        public void tick() {
            Player o = ownerPlayer();
            if (o == null) {
                return;
            }
            getLookControl().setLookAt(o, 10f, getMaxHeadXRot());
            if (distanceTo(o) > 24 && o.onGround()) {
                teleportTo(o.getX(), o.getY(), o.getZ());
                getNavigation().stop();
            } else if (tickCount % 10 == 0) {
                follow();
            }
        }

        private void follow() {
            Player o = ownerPlayer();
            if (o != null) {
                getNavigation().moveTo(o, 1.1);
            }
        }
    }

    /** Stays near the spot it was told to guard. */
    private final class GuardGoal extends Goal {
        GuardGoal() {
            setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return command == Command.GUARD && guardPos != null && getTarget() == null
                    && guardPos.distToCenterSqr(position()) > 9;
        }

        @Override
        public void start() {
            getNavigation().moveTo(guardPos.getX() + 0.5, guardPos.getY(), guardPos.getZ() + 0.5, 1.0);
        }

        @Override
        public boolean canContinueToUse() {
            return command == Command.GUARD && !getNavigation().isDone();
        }
    }

    /** Attacks whatever hurt its owner, or whatever its owner attacked. */
    private final class DefendOwnerGoal extends TargetGoal {
        private int lastHurtBy;
        private int lastHurt;
        @Nullable
        private LivingEntity chosen;

        DefendOwnerGoal() {
            super(LotrNpc.this, false);
            setFlags(EnumSet.of(Goal.Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            Player o = ownerPlayer();
            if (o == null || command == Command.NONE) {
                return false;
            }
            LivingEntity attacker = o.getLastHurtByMob();
            if (attacker != null && o.getLastHurtByMobTimestamp() != lastHurtBy && fair(attacker)) {
                chosen = attacker;
                return true;
            }
            LivingEntity victim = o.getLastHurtMob();
            if (victim != null && o.getLastHurtMobTimestamp() != lastHurt && fair(victim)) {
                chosen = victim;
                return true;
            }
            return false;
        }

        private boolean fair(LivingEntity e) {
            return e != LotrNpc.this && !(e instanceof LotrNpc n && owner.equals(n.owner))
                    && canAttack(e, TargetingConditions.DEFAULT);
        }

        @Override
        public void start() {
            Player o = ownerPlayer();
            if (o != null) {
                lastHurtBy = o.getLastHurtByMobTimestamp();
                lastHurt = o.getLastHurtMobTimestamp();
            }
            mob.setTarget(chosen);
            super.start();
        }
    }
}
