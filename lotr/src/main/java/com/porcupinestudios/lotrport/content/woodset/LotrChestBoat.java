package com.porcupinestudios.lotrport.content.woodset;

import com.porcupinestudios.lotrport.LotrPort;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.ChestBoat;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** A chest boat of a LOTR wood (see {@link LotrBoat}). */
public class LotrChestBoat extends ChestBoat {
    static final EntityDataAccessor<String> WOOD =
            SynchedEntityData.defineId(LotrChestBoat.class, EntityDataSerializers.STRING);

    public LotrChestBoat(EntityType<? extends Boat> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(WOOD, "oak");
    }

    public String wood() {
        return entityData.get(WOOD);
    }

    public void setWood(String wood) {
        entityData.set(WOOD, wood);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Wood", wood());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Wood")) {
            setWood(tag.getString("Wood"));
        }
    }

    @Override
    public Item getDropItem() {
        return BuiltInRegistries.ITEM.get(LotrPort.id(wood() + "_chest_boat"));
    }
}
