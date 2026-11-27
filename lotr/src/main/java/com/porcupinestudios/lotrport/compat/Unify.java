package com.porcupinestudios.lotrport.compat;

import com.porcupinestudios.lotrport.LotrPort;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Other mods' copies of LOTR's blocks become LOTR's (Aaron 2026-11-26: "replace all the duplicated modded blocks with
 * the LOTR version"): as the world generates (their trees, plants and structures, every dimension; mixin
 * WorldGenRegionMixin) and when one is placed. The table is the item table of the active
 * {@link ReplaceDropsModifier}s (converter/compat.py; only those whose mods are installed load), taken where both
 * items are blocks. Block states keep every property both blocks have (log axis, slab type, stair shape...).
 * Chunks generated before keep what they have until broken (their drops are LOTR's).
 */
@EventBusSubscriber(modid = LotrPort.MOD_ID)
public final class Unify {
    private static final Map<ResourceLocation, ResourceLocation> IDS = new ConcurrentHashMap<>();
    private static volatile Map<Block, Block> blocks;

    private Unify() {}

    /** A loaded drop table joins the block table (datapack loads and reloads). */
    static void add(Map<ResourceLocation, ResourceLocation> ids) {
        IDS.putAll(ids);
        blocks = null;
    }

    static Map<Block, Block> blocks() {
        Map<Block, Block> map = blocks;
        if (map == null) {
            map = new IdentityHashMap<>();
            for (Map.Entry<Item, Item> e : ReplaceDropsModifier.resolve(IDS).entrySet()) {
                if (e.getKey() instanceof BlockItem a && e.getValue() instanceof BlockItem b) {
                    map.put(a.getBlock(), b.getBlock());
                }
            }
            blocks = map;
        }
        return map;
    }

    /** The LOTR block for a duplicate's state, with the properties they share; any other state as it is. */
    public static BlockState swap(BlockState state) {
        Map<Block, Block> map = blocks();
        if (map.isEmpty()) {
            return state;
        }
        Block to = map.get(state.getBlock());
        return to == null ? state : copy(state, to.defaultBlockState());
    }

    private static BlockState copy(BlockState from, BlockState to) {
        for (Property<?> p : from.getProperties()) {
            Property<?> q = to.getBlock().getStateDefinition().getProperty(p.getName());
            if (q != null) {
                to = set(to, q, nameOf(p, from.getValue(p)));
            }
        }
        return to;
    }

    private static <T extends Comparable<T>> BlockState set(BlockState state, Property<T> p, String value) {
        return p.getValue(value).map(v -> state.setValue(p, v)).orElse(state);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> String nameOf(Property<T> p, Object v) {
        return p.getName((T) v);
    }

    @SubscribeEvent
    static void placed(BlockEvent.EntityPlaceEvent event) {
        BlockState now = event.getPlacedBlock();
        BlockState swapped = swap(now);
        if (swapped != now) {
            event.getLevel().setBlock(event.getPos(), swapped, Block.UPDATE_ALL);
        }
    }
}
