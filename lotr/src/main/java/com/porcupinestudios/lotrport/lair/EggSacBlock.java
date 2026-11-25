package com.porcupinestudios.lotrport.lair;

import com.mojang.serialization.MapCodec;
import com.porcupinestudios.lotrport.LotrPort;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * A spider's egg sac (Aaron 2026-11-25: destroying nests cuts their spiders): a silk bundle of eggs in a nest's core.
 * When it is gone, however (broken, burnt, blown up), it hatches and counts against its nest ({@link LairLife}). Soft
 * (a hoe is quickest); drops string.
 */
public final class EggSacBlock extends Block {
    public static final MapCodec<EggSacBlock> CODEC = simpleCodec(EggSacBlock::new);
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 13, 14);
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(LotrPort.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(LotrPort.MOD_ID);

    public static final DeferredBlock<EggSacBlock> BLOCK = BLOCKS.register("spider_egg_sac",
            () -> new EggSacBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOL).strength(0.8f)
                    .sound(SoundType.WOOL).noOcclusion().pushReaction(PushReaction.DESTROY)));
    public static final DeferredItem<BlockItem> ITEM = ITEMS.registerSimpleBlockItem(BLOCK);

    private static final ResourceKey<CreativeModeTab> TAB = ResourceKey.create(Registries.CREATIVE_MODE_TAB,
            ResourceLocation.fromNamespaceAndPath(LotrPort.MOD_ID, "blocks"));

    public EggSacBlock(Properties properties) {
        super(properties);
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey().equals(TAB)) {
                event.accept(ITEM.get());
            }
        });
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (level instanceof ServerLevel server && !newState.is(this)) {
            LairLife.sacGone(server, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
