package com.porcupinestudios.lotrport.drink;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.content.ContentData;
import com.porcupinestudios.lotrport.content.LotrBlocks;
import com.porcupinestudios.lotrport.content.LotrItems;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.items.wrapper.SidedInvWrapper;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * LOTR's drinks, vessels and barrel (the keg, renamed so it isn't taken for vanilla's barrel). A drink is an item in a
 * vessel ({@link #VESSEL}, none = LOTR's mug) with a strength ({@link #STRENGTH}, brewable drinks only), and a fluid
 * ({@code content/drink_fluids.json}: water and milk are vanilla's), one serving being 250 mB. Kegs, drinks and vessels
 * expose NeoForge fluid handlers, which is all Create's pipes, pumps, spouts and item drains need.
 */
public final class Drinks {
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, LotrPort.MOD_ID);
    private static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, LotrPort.MOD_ID);
    private static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, LotrPort.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, LotrPort.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, LotrPort.MOD_ID);

    /** A brewable drink's strength, 0 (weak) to 4 (potent): LOTR's metadata % 100. On items and fluids. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> STRENGTH =
            COMPONENTS.registerComponentType("drink_strength",
                    b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));
    /** The vessel a drink is in when it isn't LOTR's mug: LOTR's metadata / 100. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Holder<Item>>> VESSEL =
            COMPONENTS.registerComponentType("vessel", b -> b.persistent(BuiltInRegistries.ITEM.holderByNameCodec())
                    .networkSynchronized(ByteBufCodecs.holderRegistry(Registries.ITEM)));

    public static final Supplier<BlockEntityType<KegBlockEntity>> KEG = BLOCK_ENTITIES.register("keg",
            () -> BlockEntityType.Builder.of(KegBlockEntity::new, LotrBlocks.BY_ID.values().stream()
                    .map(Supplier::get).filter(b -> b instanceof KegBlock).toArray(Block[]::new)).build(null));
    public static final Supplier<MenuType<KegMenu>> KEG_MENU =
            MENUS.register("keg", () -> new MenuType<>(KegMenu::new, FeatureFlags.DEFAULT_FLAGS));

    /** A drink's fluid and colour (the liquid's average, for the fluid's tint). */
    public record DrinkFluidEntry(ResourceLocation item, ResourceLocation fluid, int color) {}

    /** By drink item id, and by fluid id (water and milk: their mug). */
    public static final Map<ResourceLocation, DrinkFluidEntry> BY_ITEM = new LinkedHashMap<>();
    private static final Map<ResourceLocation, DrinkFluidEntry> BY_FLUID = new LinkedHashMap<>();
    /** The drinks' own fluid types, by drink item id (the client tints them). */
    public static final Map<ResourceLocation, Supplier<FluidType>> FLUID_TYPE_BY_ITEM = new LinkedHashMap<>();
    /** LOTR's vessels by id (0 = mug), from {@code content/vessels.json}. */
    private static final Map<Integer, ResourceLocation> VESSELS = new LinkedHashMap<>();
    private static final Map<ResourceLocation, Integer> VESSEL_IDS = new LinkedHashMap<>();

    private Drinks() {}

    public static void register(IEventBus modBus) {
        NeoForgeMod.enableMilkFluid(); // a mug of milk is milk
        for (JsonObject v : ContentData.read("vessels.json")) {
            ResourceLocation item = ResourceLocation.parse(v.get("item").getAsString());
            VESSELS.put(v.get("id").getAsInt(), item);
            VESSEL_IDS.put(item, v.get("id").getAsInt());
        }
        for (JsonObject f : ContentData.read("drink_fluids.json")) {
            DrinkFluidEntry e = new DrinkFluidEntry(ResourceLocation.parse(f.get("item").getAsString()),
                    ResourceLocation.parse(f.get("fluid").getAsString()), f.get("color").getAsInt());
            BY_ITEM.put(e.item(), e);
            BY_FLUID.putIfAbsent(e.fluid(), e);
            if (e.fluid().equals(e.item())) {
                String id = e.item().getPath();
                Supplier<FluidType> type = FLUID_TYPES.register(id, () -> new FluidType(FluidType.Properties.create()));
                FLUID_TYPE_BY_ITEM.put(e.item(), type);
                FLUIDS.register(id, () -> new DrinkFluid(type));
            }
        }
        COMPONENTS.register(modBus);
        FLUID_TYPES.register(modBus);
        FLUIDS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        modBus.addListener(Drinks::capabilities);
        DrinkData.register();
    }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, KEG.get(), (keg, side) -> keg.fluids);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, KEG.get(), SidedInvWrapper::new);
        Item[] items = LotrItems.BY_ID.values().stream().map(Supplier::get)
                .filter(i -> i instanceof DrinkItem || i instanceof VesselItem).toArray(Item[]::new);
        event.registerItem(Capabilities.FluidHandler.ITEM, (stack, ctx) -> new DrinkFluidHandler(stack), items);
    }

    // ------------------------------------------------------------------ stacks

    public static int strength(ItemStack drink) {
        return drink.getOrDefault(STRENGTH.get(), 0);
    }

    static boolean brewable(Item item) {
        return item instanceof DrinkItem d && d.brewable();
    }

    public static boolean isVessel(Item item) {
        return VESSEL_IDS.containsKey(BuiltInRegistries.ITEM.getKey(item));
    }

    /** The vessel's LOTR id (0 = mug), for the item models' {@code lotr_port:vessel} predicate. */
    public static int vesselId(ItemStack drink) {
        Holder<Item> v = drink.get(VESSEL.get());
        return v == null ? 0 : VESSEL_IDS.getOrDefault(BuiltInRegistries.ITEM.getKey(v.value()), 0);
    }

    /** The empty vessel a drink leaves. */
    public static Item vessel(ItemStack drink) {
        Holder<Item> v = drink.get(VESSEL.get());
        return v != null ? v.value() : BuiltInRegistries.ITEM.get(VESSELS.get(0));
    }

    /** One of the drink, poured into {@code vessel}. */
    public static ItemStack inVessel(ItemStack drink, Item vessel) {
        ItemStack out = drink.copyWithCount(1);
        if (VESSEL_IDS.getOrDefault(BuiltInRegistries.ITEM.getKey(vessel), 0) == 0) {
            out.remove(VESSEL.get());
        } else {
            out.set(VESSEL.get(), vessel.builtInRegistryHolder());
        }
        return out;
    }

    /** The same drink at the same strength, whatever it is in. */
    static boolean same(ItemStack a, ItemStack b) {
        return a.is(b.getItem()) && strength(a) == strength(b);
    }

    /** {@code mb} of the drink as a fluid (its strength on it), or empty if it has none. */
    public static FluidStack toFluid(ItemStack drink, int mb) {
        DrinkFluidEntry e = BY_ITEM.get(BuiltInRegistries.ITEM.getKey(drink.getItem()));
        Fluid fluid = e == null ? Fluids.EMPTY : BuiltInRegistries.FLUID.get(e.fluid());
        if (fluid == Fluids.EMPTY || mb <= 0) {
            return FluidStack.EMPTY;
        }
        FluidStack out = new FluidStack(fluid, mb);
        if (brewable(drink.getItem())) {
            out.set(STRENGTH.get(), strength(drink));
        }
        return out;
    }

    /** One of the drink a fluid is (in a mug), or empty if it isn't one. */
    public static ItemStack fromFluid(FluidStack fluid) {
        DrinkFluidEntry e = fluid.isEmpty() ? null : BY_FLUID.get(BuiltInRegistries.FLUID.getKey(fluid.getFluid()));
        if (e == null) {
            return ItemStack.EMPTY;
        }
        ItemStack out = new ItemStack(BuiltInRegistries.ITEM.get(e.item()));
        if (brewable(out.getItem())) {
            out.set(STRENGTH.get(), fluid.getOrDefault(STRENGTH.get(), 0));
        }
        return out;
    }

    /** A drink as a fluid only: no block, no bucket, never in the world (pipes, tanks and kegs carry it). */
    static final class DrinkFluid extends Fluid {
        private final Supplier<FluidType> type;

        DrinkFluid(Supplier<FluidType> type) {
            this.type = type;
        }

        @Override
        public FluidType getFluidType() {
            return type.get();
        }

        @Override
        public Item getBucket() {
            return Items.AIR;
        }

        @Override
        protected boolean canBeReplacedWith(FluidState state, BlockGetter level, BlockPos pos, Fluid fluid,
                Direction direction) {
            return true;
        }

        @Override
        protected Vec3 getFlow(BlockGetter level, BlockPos pos, FluidState state) {
            return Vec3.ZERO;
        }

        @Override
        public int getTickDelay(LevelReader level) {
            return 5;
        }

        @Override
        protected float getExplosionResistance() {
            return 100f;
        }

        @Override
        public float getHeight(FluidState state, BlockGetter level, BlockPos pos) {
            return 0f;
        }

        @Override
        public float getOwnHeight(FluidState state) {
            return 0f;
        }

        @Override
        protected BlockState createLegacyBlock(FluidState state) {
            return Blocks.AIR.defaultBlockState();
        }

        @Override
        public boolean isSource(FluidState state) {
            return true;
        }

        @Override
        public int getAmount(FluidState state) {
            return 0;
        }

        @Override
        public VoxelShape getShape(FluidState state, BlockGetter level, BlockPos pos) {
            return Shapes.empty();
        }
    }
}
