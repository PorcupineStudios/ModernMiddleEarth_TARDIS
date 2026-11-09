package com.porcupinestudios.tardisrb.block;

import com.porcupinestudios.tardisrb.TardisRB;
import com.porcupinestudios.tardisrb.compat.create.WinderBlock;
import com.porcupinestudios.tardisrb.compat.create.WinderBlockEntity;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
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
 * of the console room, the Repair Station, salvage wreck debris found in the world, and the Create winder. Their creative tab is "TARDIS".
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
            BLOCKS.register("interior_door", () -> new InteriorDoorBlock(fixed().noOcclusion()));
    public static final DeferredBlock<Block> SALVAGE_WRECK = BLOCKS.register("salvage_wreck",
            () -> new SalvageBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLUE).strength(2.0F)
                    .sound(SoundType.WOOD).noOcclusion()));

    /** Opens the repair and upgrade tree; placed by hand until the lower deck replaces it (2026-11-04). */
    public static final DeferredBlock<RepairStationBlock> REPAIR_STATION = BLOCKS.register("repair_station",
            () -> new RepairStationBlock(fixed().mapColor(MapColor.METAL).sound(SoundType.METAL)));

    /** Create: turns rotation into charge. */
    public static final DeferredBlock<WinderBlock> WINDER = BLOCKS.register("winder",
            () -> new WinderBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0F)
                    .sound(SoundType.COPPER).noOcclusion()));

    // ---- the Capaldi console room (Aaron, 2026-11-04): the parts that look like full blocks are real blocks; fixed
    // like the rest of the interior. Made by tools/capaldi.py.
    private static BlockBehaviour.Properties room(int light) {
        return fixed().mapColor(MapColor.METAL).sound(SoundType.METAL).lightLevel(s -> light);
    }

    public static final DeferredBlock<Block> FLOOR_PLATE = BLOCKS.register("floor_plate", () -> new Block(room(0)));
    public static final DeferredBlock<Block> WALL_PANEL = BLOCKS.register("wall_panel", () -> new Block(room(0)));
    public static final DeferredBlock<Block> FRAME = BLOCKS.register("frame", () -> new Block(room(0)));
    public static final DeferredBlock<Block> ROUNDEL_WALL = BLOCKS.register("roundel_wall", () -> new Block(room(0)));
    public static final DeferredBlock<Block> ROUNDEL_WALL_LIT = BLOCKS.register("roundel_wall_lit",
            () -> new Block(room(12)));
    public static final DeferredBlock<Block> LIGHT_STRIP = BLOCKS.register("light_strip", () -> new Block(room(15)));
    public static final DeferredBlock<Block> GRILLE_PLATE = BLOCKS.register("grille_plate",
            () -> new Block(room(8)));
    public static final DeferredBlock<StairBlock> FRAME_STAIRS = BLOCKS.register("frame_stairs",
            () -> new StairBlock(FRAME.get().defaultBlockState(), room(0)));
    public static final DeferredBlock<StairBlock> FLOOR_PLATE_STAIRS = BLOCKS.register("floor_plate_stairs",
            () -> new StairBlock(FLOOR_PLATE.get().defaultBlockState(), room(0)));
    public static final DeferredBlock<SlabBlock> FRAME_SLAB = BLOCKS.register("frame_slab",
            () -> new SlabBlock(room(0)));
    /** Time vortex debris: the obstacles a flight through the vortex has to dodge (placed and cleared by code). */
    public static final DeferredBlock<Block> VORTEX_DEBRIS = BLOCKS.register("vortex_debris",
            () -> new Block(fixed().mapColor(MapColor.COLOR_PURPLE).lightLevel(s -> 12).noCollission()
                    .noLootTable()));
    public static final DeferredBlock<CollisionOnlyBlock> COLLISION_POST = BLOCKS.register("collision_post",
            () -> new CollisionOnlyBlock(fixed().noOcclusion().noLootTable(), Block.box(6, 0, 6, 10, 16, 10)));
    public static final DeferredBlock<CollisionOnlyBlock> COLLISION_PILLAR = BLOCKS.register("collision_pillar",
            () -> new CollisionOnlyBlock(fixed().noOcclusion().noLootTable(), Block.box(3, 0, 3, 13, 16, 13)));
    public static final DeferredBlock<ConsoleHullBlock> CONSOLE_HULL = BLOCKS.register("console_hull",
            () -> new ConsoleHullBlock(fixed().noOcclusion().noLootTable()));

    static {
        for (DeferredBlock<?> b : List.of(FLOOR_PLATE, WALL_PANEL, FRAME, ROUNDEL_WALL, ROUNDEL_WALL_LIT, LIGHT_STRIP,
                GRILLE_PLATE, FRAME_STAIRS, FLOOR_PLATE_STAIRS, FRAME_SLAB)) {
            ITEMS.registerSimpleBlockItem(b);
        }
    }

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
