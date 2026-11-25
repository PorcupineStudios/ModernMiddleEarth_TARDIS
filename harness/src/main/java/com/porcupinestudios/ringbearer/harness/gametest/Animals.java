package com.porcupinestudios.ringbearer.harness.gametest;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import org.jetbrains.annotations.Nullable;

/**
 * Test animals by name, without depending on the mods that add them: {@code "<entity type>"} (a horse, a LOTR port
 * camel), or {@code "<entity type>|<LOTR NPC type>"} for creatures that share one entity type (a hired warg:
 * {@code lotr_port:npc|lotr_port:angmar_warg}), made from the NPC's save data with an owner, as if hired. Tamed if
 * horse-family; grown.
 */
public final class Animals {
    public static final String HORSE = "minecraft:horse";

    private Animals() {}

    /** Whether the mod that adds this animal is loaded. */
    public static boolean exists(String spec) {
        return EntityType.byString(spec.split("\\|")[0]).isPresent();
    }

    @Nullable
    public static Mob create(ServerLevel level, String spec) {
        String[] parts = spec.split("\\|");
        Entity e;
        if (parts.length > 1) {
            CompoundTag tag = new CompoundTag();
            tag.putString("id", parts[0]);
            tag.putString("NpcType", parts[1]);
            tag.putBoolean("Fresh", true);
            tag.putUUID("Owner", UUID.nameUUIDFromBytes("ringbearer_harness:test_owner".getBytes()));
            e = EntityType.loadEntityRecursive(tag, level, x -> x);
        } else {
            e = EntityType.byString(spec).map(t -> t.create(level)).orElse(null);
        }
        if (!(e instanceof Mob mob)) {
            return null;
        }
        if (mob instanceof AbstractHorse horse) {
            horse.setTamed(true);
        }
        mob.setPersistenceRequired();
        return mob;
    }

    /** The id the draught and fit files would list for it: the NPC type, else the entity type. */
    public static ResourceLocation kind(String spec) {
        String[] parts = spec.split("\\|");
        return ResourceLocation.parse(parts[parts.length - 1]);
    }
}
