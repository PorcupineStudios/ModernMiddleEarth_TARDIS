package com.porcupinestudios.lotrport.content;

import com.mojang.serialization.MapCodec;
import com.porcupinestudios.lotrport.LotrPort;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The wild lands' own blocks (Aaron 2026-11-25, the Lone-lands: "custom blocks that match the pallet and visually
 * blend together"): weathered stone and its lichened face, scree (loose rubble that stays put, unlike gravel),
 * moorland soil (dark peaty ground), stony earth (worn ground between the grass) and heath moss (olive, not vanilla's
 * bright moss), all three taken by grass, saplings and plants like dirt (tag {@code minecraft:dirt}), and
 * two dry, untinted plants, tussock grass and dry bracken. Textures, models, loot and names are written by
 * {@code converter/wilds.py}; tags by {@code tags.py HAND}.
 */
public final class WildBlocks {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(LotrPort.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(LotrPort.MOD_ID);

    public static final DeferredBlock<Block> WEATHERED_STONE = BLOCKS.registerSimpleBlock("weathered_stone",
            BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(1.5f, 6f)
                    .requiresCorrectToolForDrops().sound(SoundType.STONE));
    public static final DeferredBlock<Block> LICHEN_STONE = BLOCKS.registerSimpleBlock("lichen_stone",
            BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_LIGHT_GRAY).strength(1.5f, 6f)
                    .requiresCorrectToolForDrops().sound(SoundType.STONE));
    public static final DeferredBlock<Block> SCREE = BLOCKS.registerSimpleBlock("scree",
            BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(0.8f).sound(SoundType.GRAVEL));
    public static final DeferredBlock<Block> MOORLAND_SOIL = BLOCKS.registerSimpleBlock("moorland_soil",
            BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_BROWN).strength(0.6f)
                    .sound(SoundType.ROOTED_DIRT));
    public static final DeferredBlock<Block> STONY_EARTH = BLOCKS.registerSimpleBlock("stony_earth",
            BlockBehaviour.Properties.of().mapColor(MapColor.DIRT).strength(0.5f).sound(SoundType.GRAVEL));
    public static final DeferredBlock<Block> ROCKY_DIRT = BLOCKS.registerSimpleBlock("rocky_dirt",
            BlockBehaviour.Properties.of().mapColor(MapColor.DIRT).strength(0.6f).sound(SoundType.ROOTED_DIRT));
    public static final DeferredBlock<Block> HEATH_MOSS = BLOCKS.registerSimpleBlock("heath_moss",
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).strength(0.1f).sound(SoundType.MOSS));
    public static final DeferredBlock<WildPlant> TUSSOCK = BLOCKS.registerBlock("tussock", WildPlant::new,
            plant(MapColor.SAND));
    public static final DeferredBlock<WildPlant> BRACKEN = BLOCKS.registerBlock("bracken", WildPlant::new,
            plant(MapColor.TERRACOTTA_ORANGE));

    public static final List<DeferredItem<BlockItem>> ITEMS_IN_ORDER = List.of(
            ITEMS.registerSimpleBlockItem(WEATHERED_STONE), ITEMS.registerSimpleBlockItem(LICHEN_STONE),
            ITEMS.registerSimpleBlockItem(SCREE), ITEMS.registerSimpleBlockItem(MOORLAND_SOIL),
            ITEMS.registerSimpleBlockItem(STONY_EARTH), ITEMS.registerSimpleBlockItem(ROCKY_DIRT), ITEMS.registerSimpleBlockItem(HEATH_MOSS),
            ITEMS.registerSimpleBlockItem(TUSSOCK), ITEMS.registerSimpleBlockItem(BRACKEN));

    private static final ResourceKey<CreativeModeTab> TAB = ResourceKey.create(Registries.CREATIVE_MODE_TAB,
            ResourceLocation.fromNamespaceAndPath(LotrPort.MOD_ID, "blocks"));

    private WildBlocks() {}

    private static BlockBehaviour.Properties plant(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).replaceable().noCollission().instabreak()
                .sound(SoundType.GRASS).offsetType(BlockBehaviour.OffsetType.XYZ).ignitedByLava()
                .pushReaction(PushReaction.DESTROY);
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(WildBlocks::tabs);
    }

    private static void tabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(TAB)) {
            ITEMS_IN_ORDER.forEach(i -> event.accept(i.get()));
        }
    }

    /** A dry clump like short grass: on dirt-like ground (grass, moorland soil, coarse dirt, podzol, moss). */
    public static final class WildPlant extends BushBlock {
        public static final MapCodec<WildPlant> CODEC = simpleCodec(WildPlant::new);
        private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 13, 14);

        public WildPlant(BlockBehaviour.Properties properties) {
            super(properties);
        }

        @Override
        protected MapCodec<? extends BushBlock> codec() {
            return CODEC;
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            Vec3 offset = state.getOffset(level, pos);
            return SHAPE.move(offset.x, offset.y, offset.z);
        }
    }
}
