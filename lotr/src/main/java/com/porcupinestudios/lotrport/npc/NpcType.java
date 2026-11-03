package com.porcupinestudios.lotrport.npc;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * A kind of NPC from data: {@code data/<namespace>/npc/<name>.json}, reloaded with {@code /reload}. Its name is the
 * lang key {@code npc.<namespace>.<name>}. One entity ({@code lotr_port:npc}) plays every type.
 *
 * @param faction who it fights for; kills of it move alignment with that faction
 * @param skins textures it picks one of when it spawns (humanoid skins)
 * @param skinHeight 32 for classic 64x32 skins, 64 for 64x64 ones
 * @param scale size against a player (hobbits and dwarves are smaller)
 * @param health max health
 * @param speed movement speed attribute
 * @param attack melee damage
 * @param gear item ids by slot (mainhand, offhand, head, chest, legs, feet); a list means one picked at random
 * @param names name bank ({@code npc_names/<bank>.json}) it takes a first name from; empty means no name
 * @param hostile whether it attacks players with negative alignment with its faction
 * @param buy trade pool of what it sells to players
 * @param sell trade pool of what it buys from players
 * @param units unit pool of the soldiers and workers it hires out
 * @param tradeAlignment alignment a player needs with its faction to trade or hire
 */
public record NpcType(Optional<ResourceLocation> faction, List<ResourceLocation> skins, int skinHeight, float scale,
        double health, double speed, double attack, Map<String, List<ResourceLocation>> gear, Optional<ResourceLocation> names,
        boolean hostile, Optional<ResourceLocation> buy, Optional<ResourceLocation> sell,
        Optional<ResourceLocation> units, float tradeAlignment) {

    /** One id, or a list of ids. */
    private static final Codec<List<ResourceLocation>> ONE_OR_MORE =
            Codec.either(ResourceLocation.CODEC, ResourceLocation.CODEC.listOf()).xmap(
                    e -> e.map(List::of, l -> l),
                    l -> l.size() == 1 ? Either.left(l.get(0)) : Either.right(l));

    public static final Codec<NpcType> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.optionalFieldOf("faction").forGetter(NpcType::faction),
            ResourceLocation.CODEC.listOf().optionalFieldOf("skins", List.of()).forGetter(NpcType::skins),
            Codec.INT.optionalFieldOf("skin_height", 32).forGetter(NpcType::skinHeight),
            Codec.FLOAT.optionalFieldOf("scale", 1f).forGetter(NpcType::scale),
            Codec.DOUBLE.optionalFieldOf("health", 20.0).forGetter(NpcType::health),
            Codec.DOUBLE.optionalFieldOf("speed", 0.25).forGetter(NpcType::speed),
            Codec.DOUBLE.optionalFieldOf("attack", 2.0).forGetter(NpcType::attack),
            Codec.unboundedMap(Codec.STRING, ONE_OR_MORE)
                    .optionalFieldOf("gear", Map.of())
                    .forGetter(NpcType::gear),
            ResourceLocation.CODEC.optionalFieldOf("names").forGetter(NpcType::names),
            Codec.BOOL.optionalFieldOf("hostile", true).forGetter(NpcType::hostile),
            ResourceLocation.CODEC.optionalFieldOf("buy").forGetter(NpcType::buy),
            ResourceLocation.CODEC.optionalFieldOf("sell").forGetter(NpcType::sell),
            ResourceLocation.CODEC.optionalFieldOf("units").forGetter(NpcType::units),
            Codec.FLOAT.optionalFieldOf("trade_alignment", 0f).forGetter(NpcType::tradeAlignment))
            .apply(i, NpcType::new));

    public boolean trader() {
        return buy.isPresent() || sell.isPresent();
    }

    public boolean captain() {
        return units.isPresent();
    }
}
