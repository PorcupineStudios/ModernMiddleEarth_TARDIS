package com.porcupinestudios.lotrport.compat.create;

import com.google.gson.JsonObject;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.content.ContentData;
import com.simibubi.create.api.packager.unpacking.UnpackingHandler;
import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.impl.unpacking.CrafterUnpackingHandler;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * A Create mechanical crafter per faction (only with Create installed: {@code LotrPort} calls {@link #register} when
 * it is loaded). Which ones, their table and colour: {@code content/create_crafters.json}, written by
 * {@code converter/create_crafters.py}. Each is made at its faction's table and needs positive alignment with the
 * faction (its recipe's {@code alignment}); a grid with one in it also crafts that table's recipes.
 */
public final class FactionCrafters {
    /** Create's crafter: impact 2 (AllBlocks.MECHANICAL_CRAFTER). */
    private static final double STRESS_IMPACT = 2.0;

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(LotrPort.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(LotrPort.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, LotrPort.MOD_ID);

    public static final List<DeferredBlock<FactionCrafterBlock>> CRAFTERS = new ArrayList<>();
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FactionCrafterBlockEntity>> BLOCK_ENTITY;

    static {
        for (JsonObject o : ContentData.read("create_crafters.json")) {
            String id = o.get("id").getAsString();
            ResourceLocation table = ResourceLocation.parse(o.get("table").getAsString());
            int tint = o.get("tint").getAsInt();
            DeferredBlock<FactionCrafterBlock> block = BLOCKS.register(id, () -> new FactionCrafterBlock(
                    BlockBehaviour.Properties.ofFullCopy(Blocks.GOLD_BLOCK).noOcclusion()
                            .mapColor(MapColor.TERRACOTTA_YELLOW), table, tint));
            ITEMS.register(id, () -> new BlockItem(block.get(), new Item.Properties()));
            CRAFTERS.add(block);
        }
        BLOCK_ENTITY = BLOCK_ENTITIES.register("faction_mechanical_crafter", () -> BlockEntityType.Builder.<FactionCrafterBlockEntity>of(
                (pos, state) -> new FactionCrafterBlockEntity(FactionCrafters.BLOCK_ENTITY.get(), pos, state),
                CRAFTERS.stream().map(DeferredBlock::get).toArray(Block[]::new)).build(null));
    }

    private FactionCrafters() {}

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener(FactionCrafters::setup);
        modBus.addListener(FactionCrafters::capabilities);
        modBus.addListener(FactionCrafters::tabs);
        if (FMLEnvironment.dist.isClient()) {
            FactionCraftersClient.register(modBus);
        }
    }

    private static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            for (DeferredBlock<FactionCrafterBlock> b : CRAFTERS) {
                BlockStressValues.IMPACTS.register(b.get(), () -> STRESS_IMPACT);
                UnpackingHandler.REGISTRY.register(b.get(), CrafterUnpackingHandler.INSTANCE);
            }
        });
    }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, BLOCK_ENTITY.get(),
                (be, side) -> be.itemHandler());
    }

    private static void tabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(ResourceKey.create(Registries.CREATIVE_MODE_TAB, LotrPort.id("util")))) {
            CRAFTERS.forEach(b -> event.accept(b.get()));
        }
    }
}
