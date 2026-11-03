package com.porcupinestudios.tardisrb.progression;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * What something costs: items (an item or a tag, with a count) and stored charge. A {@code tardis/cost} file is a
 * reusable bundle; repairs and research name one by id or write their own inline.
 */
public record Cost(List<ItemCost> items, long charge) {
    public static final Cost FREE = new Cost(List.of(), 0);

    /** {@code {"ingredient": {"item": "minecraft:iron_ingot"}, "count": 4}}; a tag works too. */
    public record ItemCost(Ingredient ingredient, int count) {
        public static final Codec<ItemCost> CODEC = RecordCodecBuilder.create(i -> i.group(
                Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(ItemCost::ingredient),
                Codec.intRange(1, 4096).optionalFieldOf("count", 1).forGetter(ItemCost::count))
                .apply(i, ItemCost::new));
    }

    public static final Codec<Cost> CODEC = RecordCodecBuilder.create(i -> i.group(
            ItemCost.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(Cost::items),
            Codec.LONG.optionalFieldOf("charge", 0L).forGetter(Cost::charge))
            .apply(i, Cost::new));

    /** A cost file's id, or a cost written in place. */
    public static final Codec<Either<ResourceLocation, Cost>> REF_CODEC = Codec.either(ResourceLocation.CODEC, CODEC);

    public static final Either<ResourceLocation, Cost> NONE = Either.right(FREE);

    public boolean isFree() {
        return items.isEmpty() && charge <= 0;
    }
}
