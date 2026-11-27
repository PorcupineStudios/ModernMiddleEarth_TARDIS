package com.porcupinestudios.lotrport.compat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

/**
 * Global loot modifier {@code lotr_port:replace_drops}: loot gives one item instead of another, so a block another mod
 * shares with LOTR drops as LOTR's (converter/compat.py writes one per mod, switched on by {@code mod_loaded}). Ids
 * stay ids until first used: a mod that isn't installed has nothing to replace.
 */
public final class ReplaceDropsModifier extends LootModifier {
    public static final MapCodec<ReplaceDropsModifier> CODEC = RecordCodecBuilder.mapCodec(i -> codecStart(i)
            .and(Codec.unboundedMap(ResourceLocation.CODEC, ResourceLocation.CODEC).fieldOf("replace")
                    .forGetter(m -> m.replace))
            .apply(i, ReplaceDropsModifier::new));

    private final Map<ResourceLocation, ResourceLocation> replace;
    private volatile Map<Item, Item> items;

    public ReplaceDropsModifier(LootItemCondition[] conditions, Map<ResourceLocation, ResourceLocation> replace) {
        super(conditions);
        this.replace = replace;
    }

    /** The table as items, both ends registered (an id of a missing mod resolves to air and is left out). */
    public static Map<Item, Item> resolve(Map<ResourceLocation, ResourceLocation> ids) {
        Map<Item, Item> out = new HashMap<>();
        ids.forEach((from, to) -> {
            Item a = BuiltInRegistries.ITEM.get(from);
            Item b = BuiltInRegistries.ITEM.get(to);
            if (a != Items.AIR && b != Items.AIR) {
                out.put(a, b);
            }
        });
        return out;
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
        Map<Item, Item> map = items;
        if (map == null) {
            items = map = resolve(replace);
        }
        for (int i = 0; i < loot.size(); i++) {
            Item to = map.get(loot.get(i).getItem());
            if (to != null) {
                loot.set(i, loot.get(i).transmuteCopy(to, loot.get(i).getCount()));
            }
        }
        return loot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
