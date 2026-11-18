package com.porcupinestudios.lotrport.content;

import com.porcupinestudios.lotrport.LotrPort;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The Heart of the Mountain (Aaron 2026-11-17): a single-block ore, one deep in each mountain
 * ({@code world/feature/HeartOfTheMountainFeature}). Hard as ancient debris, glows faintly, drops itself.
 */
public final class HeartOfTheMountain {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(LotrPort.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(LotrPort.MOD_ID);

    public static final DeferredBlock<Block> BLOCK = BLOCKS.registerSimpleBlock("heart_of_the_mountain",
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(30f, 1200f)
                    .requiresCorrectToolForDrops().lightLevel(s -> 9).sound(SoundType.AMETHYST)
                    .pushReaction(PushReaction.BLOCK));
    public static final DeferredItem<BlockItem> ITEM = ITEMS.registerSimpleBlockItem(BLOCK,
            new Item.Properties().rarity(Rarity.EPIC).fireResistant());

    private static final ResourceKey<CreativeModeTab> TAB = ResourceKey.create(Registries.CREATIVE_MODE_TAB,
            ResourceLocation.fromNamespaceAndPath(LotrPort.MOD_ID, "blocks"));

    private HeartOfTheMountain() {}

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(HeartOfTheMountain::tabs);
    }

    private static void tabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(TAB)) {
            event.accept(ITEM.get());
        }
    }
}
