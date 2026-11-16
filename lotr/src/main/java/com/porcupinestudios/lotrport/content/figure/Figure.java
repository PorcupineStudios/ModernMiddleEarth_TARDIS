package com.porcupinestudios.lotrport.content.figure;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A still figure on one of LOTR's creature models: troll statues (stone) and skin rugs (laid flat). Placed by
 * {@link FigureItem}; a hit pops it back into its item. Model, texture and pose are synced for the renderer.
 */
public class Figure extends Entity {
    private static final EntityDataAccessor<String> MODEL = SynchedEntityData.defineId(Figure.class,
            EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> TEXTURE = SynchedEntityData.defineId(Figure.class,
            EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> FLAT = SynchedEntityData.defineId(Figure.class,
            EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<ItemStack> ITEM = SynchedEntityData.defineId(Figure.class,
            EntityDataSerializers.ITEM_STACK);

    public Figure(EntityType<? extends Figure> type, Level level) {
        super(type, level);
    }

    void setup(String model, String texture, boolean flat, ItemStack item) {
        entityData.set(MODEL, model);
        entityData.set(TEXTURE, texture);
        entityData.set(FLAT, flat);
        entityData.set(ITEM, item.copyWithCount(1));
    }

    public String model() {
        return entityData.get(MODEL);
    }

    public String texture() {
        return entityData.get(TEXTURE);
    }

    public ItemStack item() {
        return entityData.get(ITEM);
    }

    public boolean flat() {
        return entityData.get(FLAT);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(MODEL, "").define(TEXTURE, "").define(FLAT, false).define(ITEM, ItemStack.EMPTY);
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean canBeCollidedWith() {
        return !flat();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isInvulnerableTo(source) || isRemoved()) {
            return false;
        }
        if (level() instanceof ServerLevel) {
            if (!(source.getEntity() instanceof Player p && p.getAbilities().instabuild)) {
                spawnAtLocation(entityData.get(ITEM).copy());
            }
            discard();
        }
        return true;
    }

    @Override
    public ItemStack getPickResult() {
        return entityData.get(ITEM).copy();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(MODEL, tag.getString("Model"));
        entityData.set(TEXTURE, tag.getString("Texture"));
        entityData.set(FLAT, tag.getBoolean("Flat"));
        entityData.set(ITEM, ItemStack.parseOptional(registryAccess(), tag.getCompound("Item")));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putString("Model", model());
        tag.putString("Texture", texture());
        tag.putBoolean("Flat", flat());
        if (!entityData.get(ITEM).isEmpty()) {
            tag.put("Item", entityData.get(ITEM).save(registryAccess()));
        }
    }
}
