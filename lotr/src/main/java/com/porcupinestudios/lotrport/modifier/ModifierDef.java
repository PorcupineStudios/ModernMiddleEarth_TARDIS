package com.porcupinestudios.lotrport.modifier;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * One LOTR modifier's rules ({@code data/<ns>/modifier/<enchantment>.json}, LOTREnchantment's fields from the export):
 * the file's id is the enchantment it describes. {@code weight} is its share of random rolls (0 = never rolled: banes,
 * fire, chill, headhunting come only from scrolls or commands); {@code skilful} ones are rolled only for skilful work
 * (traders, ancient items); {@code persists_reforge} ones survive a reforge; {@code value} multiplies a trader's price;
 * {@code upgrade} is what two scrolls of it combine into; {@code acts_as} makes it count as vanilla enchantments too
 * (LOTR's looting and silk touch: vanilla's loot tables only know vanilla's ids).
 */
public record ModifierDef(int weight, boolean skilful, boolean persistsReforge, float value,
        Optional<ResourceLocation> upgrade, Map<ResourceLocation, Integer> actsAs) {
    public static final Codec<ModifierDef> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("weight", 0).forGetter(ModifierDef::weight),
            Codec.BOOL.optionalFieldOf("skilful", false).forGetter(ModifierDef::skilful),
            Codec.BOOL.optionalFieldOf("persists_reforge", false).forGetter(ModifierDef::persistsReforge),
            Codec.FLOAT.optionalFieldOf("value", 1f).forGetter(ModifierDef::value),
            ResourceLocation.CODEC.optionalFieldOf("upgrade").forGetter(ModifierDef::upgrade),
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.INT).optionalFieldOf("acts_as", Map.of())
                    .forGetter(ModifierDef::actsAs))
            .apply(i, ModifierDef::new));
}
