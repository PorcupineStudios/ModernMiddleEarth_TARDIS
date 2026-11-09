package com.porcupinestudios.lotrport.drink;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.content.ContentData;
import com.porcupinestudios.lotrport.content.LotrComponents;
import com.porcupinestudios.lotrport.content.LotrDrinkItem;
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
 * vessel ({@link LotrComponents#VESSEL}, absent = mug) with a strength ({@link LotrComponents#DRINK_STRENGTH}), and a fluid
 * ({@code content/drink_fluids.json}: water and milk are vanilla's), one serving being 250 mB. Kegs, drinks and vessels
 * expose NeoForge fluid handlers, which is all Create's pipes, pumps, spouts and item drains need.
 */
public final class Drinks {
    private static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, LotrPort.MOD_ID);
    private static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, LotrPort.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, LotrPort.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, LotrPort.MOD_ID);

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
    /** The vessels ({@code content/drinks.json}: mug, goblet_gold...) by id, and their ids by item. */
    private static final Map<String, ResourceLocation> VESSELS = new LinkedHashMap<>();
    private static final Map<ResourceLocation, String> VESSEL_IDS = new LinkedHashMap<>();
    /** LOTR's number for each vessel item (0 = mug), from {@code content/vessels.json}: the item models' predicate. */
    private static final Map<ResourceLocation, Integer> MODEL_IDS = new LinkedHashMap<>();

    private Drinks() {}

    public static void register(IEventBus modBus) {
        NeoForgeMod.enableMilkFluid(); // a mug of milk is milk
        JsonObject table = ContentData.readObject("drinks.json");
        if (table.has("vessels")) {
            for (var e : table.getAsJsonArray("vessels")) {
                JsonObject v = e.getAsJsonObject();
                ResourceLocation item = ResourceLocation.parse(v.get("item").getAsString());
                VESSELS.put(v.get("id").getAsString(), item);
                VESSEL_IDS.putIfAbsent(item, v.get("id").getAsString());
            }
        }
        for (JsonObject v : ContentData.read("vessels.json")) {
            MODEL_IDS.put(ResourceLocation.parse(v.get("item").getAsString()), v.get("id").getAsInt());
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
                .filter(i -> i instanceof LotrDrinkItem || isVessel(i)).toArray(Item[]::new);
        event.registerItem(Capabilities.FluidHandler.ITEM, (stack, ctx) -> new DrinkFluidHandler(stack), items);
    }

    // ------------------------------------------------------------------ stacks

    public static int strength(ItemStack drink) {
        return drink.getOrDefault(LotrComponents.DRINK_STRENGTH.get(), 0);
    }

    static boolean brewable(Item item) {
        return item instanceof LotrDrinkItem d && d.brewed();
    }

    public static boolean isVessel(Item item) {
        return VESSEL_IDS.containsKey(BuiltInRegistries.ITEM.getKey(item));
    }

    /** The vessel's LOTR number (0 = mug), for the item models' {@code lotr_port:vessel} predicate. */
    public static int vesselId(ItemStack drink) {
        return MODEL_IDS.getOrDefault(VESSELS.get(drink.getOrDefault(LotrComponents.VESSEL.get(), "mug")), 0);
    }

    /** The empty vessel a drink leaves. */
    public static Item vessel(ItemStack drink) {
        ResourceLocation v = VESSELS.get(drink.getOrDefault(LotrComponents.VESSEL.get(), "mug"));
        return BuiltInRegistries.ITEM.get(v != null ? v : VESSELS.getOrDefault("mug", ResourceLocation.withDefaultNamespace("air")));
    }

    /** One of the drink, poured into {@code vessel}. */
    public static ItemStack inVessel(ItemStack drink, Item vessel) {
        ItemStack out = drink.copyWithCount(1);
        String id = VESSEL_IDS.getOrDefault(BuiltInRegistries.ITEM.getKey(vessel), "mug");
        if (id.equals("mug")) {
            out.remove(LotrComponents.VESSEL.get());
        } else {
            out.set(LotrComponents.VESSEL.get(), id);
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
            out.set(LotrComponents.DRINK_STRENGTH.get(), strength(drink));
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
            out.set(LotrComponents.DRINK_STRENGTH.get(), fluid.getOrDefault(LotrComponents.DRINK_STRENGTH.get(), 0));
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
