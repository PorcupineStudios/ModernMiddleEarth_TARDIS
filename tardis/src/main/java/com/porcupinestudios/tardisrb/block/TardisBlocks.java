package com.porcupinestudios.tardisrb.block;

import com.porcupinestudios.tardisrb.TardisRB;
import com.porcupinestudios.tardisrb.compat.create.WinderBlock;
import com.porcupinestudios.tardisrb.compat.create.WinderBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The TARDIS's blocks: the police box exterior (placed only by code, two blocks tall), the console and interior door
 * of the console room, the basement's Repair Station, salvage wreck debris found in the world, and the Create winder. Their creative tab is "TARDIS".
 */
public final class TardisBlocks {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(TardisRB.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(TardisRB.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, TardisRB.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, TardisRB.MOD_ID);

    private static BlockBehaviour.Properties fixed() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLUE).strength(-1.0F, 3600000.0F).noLootTable()
                .pushReaction(PushReaction.BLOCK).sound(SoundType.WOOD);
    }

    public static final DeferredBlock<ExteriorBlock> EXTERIOR =
            BLOCKS.register("exterior", () -> new ExteriorBlock(fixed().noOcclusion()));
    public static final DeferredBlock<ConsoleBlock> CONSOLE =
            BLOCKS.register("console", () -> new ConsoleBlock(fixed().mapColor(MapColor.METAL).noOcclusion()));
    public static final DeferredBlock<InteriorDoorBlock> INTERIOR_DOOR =
            BLOCKS.register("interior_door", () -> new InteriorDoorBlock(fixed()));
    public static final DeferredBlock<Block> SALVAGE_WRECK = BLOCKS.register("salvage_wreck",
            () -> new SalvageBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLUE).strength(2.0F)
                    .sound(SoundType.WOOD).noOcclusion()));

    /** In the basement under the console room: opens the repair and upgrade tree. */
    public static final DeferredBlock<RepairStationBlock> REPAIR_STATION = BLOCKS.register("repair_station",
            () -> new RepairStationBlock(fixed().mapColor(MapColor.METAL).sound(SoundType.METAL)));

    /** Create: turns rotation into charge. */
    public static final DeferredBlock<WinderBlock> WINDER = BLOCKS.register("winder",
            () -> new WinderBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0F)
                    .sound(SoundType.COPPER).noOcclusion()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WinderBlockEntity>> WINDER_BE =
            BLOCK_ENTITIES.register("winder", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new WinderBlockEntity(TardisBlocks.WINDER_BE.get(), pos, state), WINDER.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ConsoleBlockEntity>> CONSOLE_BE =
            BLOCK_ENTITIES.register("console",
                    () -> BlockEntityType.Builder.of(ConsoleBlockEntity::new, CONSOLE.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ExteriorBlockEntity>> EXTERIOR_BE =
            BLOCK_ENTITIES.register("exterior",
                    () -> BlockEntityType.Builder.of(ExteriorBlockEntity::new, EXTERIOR.get()).build(null));

    public static final DeferredItem<BlockItem> CONSOLE_ITEM = ITEMS.registerSimpleBlockItem(CONSOLE);
    public static final DeferredItem<BlockItem> INTERIOR_DOOR_ITEM = ITEMS.registerSimpleBlockItem(INTERIOR_DOOR);
    public static final DeferredItem<BlockItem> SALVAGE_WRECK_ITEM = ITEMS.registerSimpleBlockItem(SALVAGE_WRECK);
    public static final DeferredItem<BlockItem> REPAIR_STATION_ITEM = ITEMS.registerSimpleBlockItem(REPAIR_STATION);
    public static final DeferredItem<BlockItem> WINDER_ITEM = ITEMS.registerSimpleBlockItem(WINDER);

    static {
        TABS.register("tardis", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup." + TardisRB.MOD_ID + ".tardis"))
                .icon(() -> new ItemStack(CONSOLE_ITEM.get()))
                .displayItems((params, output) -> {
                    for (var holder : ITEMS.getEntries()) {
                        output.accept(holder.get());
                    }
                })
                .build());
    }

    private TardisBlocks() {}

    /** Other block files (the Create winder) add items here so they share the tab. */
    public static DeferredRegister.Items items() {
        return ITEMS;
    }

    public static DeferredRegister.Blocks blocks() {
        return BLOCKS;
    }

    public static DeferredRegister<BlockEntityType<?>> blockEntities() {
        return BLOCK_ENTITIES;
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        TABS.register(modBus);
    }
}
