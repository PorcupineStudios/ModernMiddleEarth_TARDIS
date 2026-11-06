package com.porcupinestudios.lotrport.npc;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * Who a captain hires out: {@code data/<namespace>/npc_units/<name>.json}.
 *
 * @param npc the NPC type hired
 * @param cost silver coins
 * @param alignment alignment with the captain's faction the player needs
 * @param pledge whether the player must be pledged to the captain's faction
 */
public record UnitPool(List<Unit> units) {
    /**
     * @param mount what it rides (LOTR's mounted units): an NPC type (wargs) or a vanilla entity (horses), spawned
     *              with it and ridden; a mount NPC is hired along with the rider
     */
    public record Unit(ResourceLocation npc, int cost, float alignment, boolean pledge,
            Optional<ResourceLocation> mount) {
        public static final Codec<Unit> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("npc").forGetter(Unit::npc),
                Codec.INT.fieldOf("cost").forGetter(Unit::cost),
                Codec.FLOAT.optionalFieldOf("alignment", 0f).forGetter(Unit::alignment),
                Codec.BOOL.optionalFieldOf("pledge", false).forGetter(Unit::pledge),
                ResourceLocation.CODEC.optionalFieldOf("mount").forGetter(Unit::mount))
                .apply(i, Unit::new));
    }

    public static final Codec<UnitPool> CODEC = RecordCodecBuilder.create(i -> i.group(
            Unit.CODEC.listOf().fieldOf("units").forGetter(UnitPool::units))
            .apply(i, UnitPool::new));
}
