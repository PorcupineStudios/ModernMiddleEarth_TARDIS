package com.porcupinestudios.lotrport.machine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.content.LotrBlocks;
import com.porcupinestudios.ringbearer.api.data.CodecJsonLoader;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import net.neoforged.neoforge.items.wrapper.SidedInvWrapper;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * LOTR's machines and display blocks (missing-features task 31): the faction forges, hobbit oven, millstone and
 * unsmeltery run their {@code MachineRecipe}s; the kebab stand, plates, armour stand, weapon rack, Ent jar and chests
 * hold things. Which block is which comes from {@code machine} in the generated {@code blocks.json}
 * ({@code converter/machines.py}); LOTR's numbers are {@code data/<ns>/machine/settings.json}.
 */
public final class Machines {
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, LotrPort.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, LotrPort.MOD_ID);

    public static final Supplier<BlockEntityType<ForgeBlockEntity>> FORGE =
            type("forge", ForgeBlockEntity::new, "forge");
    public static final Supplier<BlockEntityType<OvenBlockEntity>> OVEN = type("oven", OvenBlockEntity::new, "oven");
    public static final Supplier<BlockEntityType<MillstoneBlockEntity>> MILLSTONE =
            type("millstone", MillstoneBlockEntity::new, "millstone");
    public static final Supplier<BlockEntityType<UnsmelteryBlockEntity>> UNSMELTERY =
            type("unsmeltery", UnsmelteryBlockEntity::new, "unsmeltery");
    public static final Supplier<BlockEntityType<KebabStandBlockEntity>> KEBAB_STAND =
            type("kebab_stand", KebabStandBlockEntity::new, "kebab_stand");
    public static final Supplier<BlockEntityType<DisplayBlockEntity>> DISPLAY =
            type("display", DisplayBlockEntity::new, "plate", "weapon_rack", "armor_stand");
    public static final Supplier<BlockEntityType<EntJarBlockEntity>> ENT_JAR =
            type("ent_jar", EntJarBlockEntity::new, "ent_jar");
    public static final Supplier<BlockEntityType<LotrChestBlockEntity>> CHEST =
            type("chest", LotrChestBlockEntity::new, "chest");

    public static final Supplier<MenuType<MachineMenu>> FORGE_MENU = menu(MachineMenu.Layout.FORGE);
    public static final Supplier<MenuType<MachineMenu>> OVEN_MENU = menu(MachineMenu.Layout.OVEN);
    public static final Supplier<MenuType<MachineMenu>> MILLSTONE_MENU = menu(MachineMenu.Layout.MILLSTONE);
    public static final Supplier<MenuType<MachineMenu>> UNSMELTERY_MENU = menu(MachineMenu.Layout.UNSMELTERY);
    public static final Supplier<MenuType<MachineMenu>> ARMOR_STAND_MENU = menu(MachineMenu.Layout.ARMOR_STAND);

    /** Items a forge smelts as a furnace would (LOTR: rock, sand and clay blocks, clay items, logs). */
    public static final TagKey<Item> FORGE_SMELTABLE = TagKey.create(Registries.ITEM, LotrPort.id("forge_smeltable"));

    /** LOTR's numbers, read from its tile entities. */
    public record Settings(int forgeTicks, int ovenTicks, int millstoneTicks, int unsmelteryTicks, int kebabTicks,
            int kebabSlots, int entJarCapacity, int entJarRainChance) {
        public static final Settings DEFAULT = new Settings(200, 400, 200, 400, 200, 8, 6, 2500);
        public static final Codec<Settings> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("forge_ticks", 200).forGetter(Settings::forgeTicks),
                Codec.INT.optionalFieldOf("oven_ticks", 400).forGetter(Settings::ovenTicks),
                Codec.INT.optionalFieldOf("millstone_ticks", 200).forGetter(Settings::millstoneTicks),
                Codec.INT.optionalFieldOf("unsmeltery_ticks", 400).forGetter(Settings::unsmelteryTicks),
                Codec.INT.optionalFieldOf("kebab_ticks", 200).forGetter(Settings::kebabTicks),
                Codec.INT.optionalFieldOf("kebab_slots", 8).forGetter(Settings::kebabSlots),
                Codec.INT.optionalFieldOf("ent_jar_capacity", 6).forGetter(Settings::entJarCapacity),
                Codec.INT.optionalFieldOf("ent_jar_rain_chance", 2500).forGetter(Settings::entJarRainChance))
                .apply(i, Settings::new));
    }

    private static final CodecJsonLoader<Settings> SETTINGS = new CodecJsonLoader<>("machine", Settings.CODEC);

    private Machines() {}

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        modBus.addListener(Machines::capabilities);
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent event) -> event.addListener(SETTINGS));
    }

    public static Settings settings() {
        return SETTINGS.entries().getOrDefault(LotrPort.id("settings"), Settings.DEFAULT);
    }

    /** Hoppers, pipes and Create's funnels: machines through their sides, chests whole. */
    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, FORGE.get(), SidedInvWrapper::new);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, OVEN.get(), SidedInvWrapper::new);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, MILLSTONE.get(), SidedInvWrapper::new);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, UNSMELTERY.get(), SidedInvWrapper::new);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, CHEST.get(), (chest, side) -> new InvWrapper(chest));
    }

    private static <T extends net.minecraft.world.level.block.entity.BlockEntity> Supplier<BlockEntityType<T>> type(
            String id, BlockEntityType.BlockEntitySupplier<T> factory, String... machines) {
        return BLOCK_ENTITIES.register(id, () -> BlockEntityType.Builder.of(factory, blocks(machines)).build(null));
    }

    private static Supplier<MenuType<MachineMenu>> menu(MachineMenu.Layout layout) {
        return MENUS.register(layout.id, () -> new MenuType<>((id, inv) -> new MachineMenu(layout, id, inv),
                FeatureFlags.DEFAULT_FLAGS));
    }

    /** The registered blocks of these machines. */
    static Block[] blocks(String... machines) {
        java.util.Set<String> want = java.util.Set.of(machines);
        return LotrBlocks.BY_ID.entrySet().stream()
                .filter(e -> want.contains(LotrBlocks.machine(e.getKey())))
                .map(e -> e.getValue().get()).toArray(Block[]::new);
    }
}
