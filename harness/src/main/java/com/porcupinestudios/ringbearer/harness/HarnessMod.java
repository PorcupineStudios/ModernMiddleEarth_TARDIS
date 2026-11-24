package com.porcupinestudios.ringbearer.harness;

import com.porcupinestudios.ringbearer.harness.content.HarnessItem;
import com.porcupinestudios.ringbearer.harness.content.SlopeBlock;
import com.porcupinestudios.ringbearer.harness.content.SlopeContacts;
import com.porcupinestudios.ringbearer.harness.content.WagonWheelBlock;
import com.porcupinestudios.ringbearer.harness.content.WagonWheelBlockEntity;
import com.porcupinestudios.ringbearer.harness.draught.BlockData;
import com.porcupinestudios.ringbearer.harness.draught.SlopeContact;
import com.porcupinestudios.ringbearer.harness.draught.WheelType;
import net.minecraft.world.level.block.Blocks;
import com.porcupinestudios.ringbearer.harness.content.Harnessing;
import com.porcupinestudios.ringbearer.harness.content.HitchBlock;
import com.porcupinestudios.ringbearer.harness.content.HitchBlockEntity;
import com.porcupinestudios.ringbearer.harness.content.HitchLink;
import com.porcupinestudios.ringbearer.harness.draught.DraughtAnimals;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import com.mojang.serialization.Codec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * Ring Bearer: Harness. Animals pull Create: Aeronautics vehicles: they wear a harness (instead of a saddle), are
 * hitched to a hitch block on the vehicle by two ropes, and pull through a force computed inside Sable's physics steps. Read {@code harness/docs/harness.md} first.
 */
@Mod(HarnessMod.MOD_ID)
public final class HarnessMod {
    public static final String MOD_ID = "ringbearer_harness";

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MOD_ID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MOD_ID);

    public static final DeferredBlock<HitchBlock> HITCH = BLOCKS.register("hitch",
            () -> new HitchBlock(BlockBehaviour.Properties.of().strength(1.5f).sound(SoundType.WOOD).noOcclusion()));
    public static final Supplier<BlockItem> HITCH_ITEM = ITEMS.registerSimpleBlockItem(HITCH);
    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<HitchBlockEntity>> HITCH_BE = BLOCK_ENTITIES.register("hitch",
            () -> BlockEntityType.Builder.of(HitchBlockEntity::new, HITCH.get()).build(null));
    /** 45-degree slopes: smooth ramps on the ground, and noses that let sleds ride up over block edges. */
    public static final DeferredBlock<SlopeBlock> OAK_SLOPE = slope("oak_slope", Blocks.OAK_PLANKS,
            SlopeBlock.Profile.FULL);
    public static final DeferredBlock<SlopeBlock> STONE_SLOPE = slope("stone_slope", Blocks.SMOOTH_STONE,
            SlopeBlock.Profile.FULL);
    /** The gentle slope's halves (a 2-block ramp at 27 degrees: low then high; upside down, a 2-block sled nose). */
    public static final DeferredBlock<SlopeBlock> OAK_SLOPE_LOW = slope("oak_slope_low", Blocks.OAK_PLANKS,
            SlopeBlock.Profile.LOW);
    public static final DeferredBlock<SlopeBlock> OAK_SLOPE_HIGH = slope("oak_slope_high", Blocks.OAK_PLANKS,
            SlopeBlock.Profile.HIGH);
    public static final DeferredBlock<SlopeBlock> STONE_SLOPE_LOW = slope("stone_slope_low", Blocks.SMOOTH_STONE,
            SlopeBlock.Profile.LOW);
    public static final DeferredBlock<SlopeBlock> STONE_SLOPE_HIGH = slope("stone_slope_high", Blocks.SMOOTH_STONE,
            SlopeBlock.Profile.HIGH);
    public static final Supplier<BlockItem> OAK_SLOPE_ITEM = ITEMS.registerSimpleBlockItem(OAK_SLOPE);
    public static final Supplier<BlockItem> STONE_SLOPE_ITEM = ITEMS.registerSimpleBlockItem(STONE_SLOPE);
    public static final List<Supplier<BlockItem>> GENTLE_SLOPE_ITEMS = List.of(
            ITEMS.registerSimpleBlockItem(OAK_SLOPE_LOW), ITEMS.registerSimpleBlockItem(OAK_SLOPE_HIGH),
            ITEMS.registerSimpleBlockItem(STONE_SLOPE_LOW), ITEMS.registerSimpleBlockItem(STONE_SLOPE_HIGH));
    /** A wagon wheel: rolls smoothly over block edges and slopes, steers from the hitch. */
    public static final DeferredBlock<WagonWheelBlock> WAGON_WHEEL = BLOCKS.register("wagon_wheel",
            () -> new WagonWheelBlock(BlockBehaviour.Properties.of().strength(1.5f).sound(SoundType.WOOD)
                    .noOcclusion()));
    public static final Supplier<BlockItem> WAGON_WHEEL_ITEM = ITEMS.registerSimpleBlockItem(WAGON_WHEEL);
    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<WagonWheelBlockEntity>> WAGON_WHEEL_BE = BLOCK_ENTITIES.register(
            "wagon_wheel", () -> BlockEntityType.Builder.of(WagonWheelBlockEntity::new, WAGON_WHEEL.get()).build(null));
    /** Wheel behaviour by block ({@code data/<ns>/wheel/}) and slope contact by block ({@code slope_contact/}). */
    public static final BlockData<WheelType> WHEELS = new BlockData<>("wheel", WheelType.CODEC, WheelType::blocks);
    public static final BlockData<SlopeContact> SLOPE_CONTACTS = new BlockData<>("slope_contact", SlopeContact.CODEC,
            SlopeContact::blocks);

    /** The harness, worn by an animal instead of a saddle. */
    public static final Supplier<HarnessItem> HARNESS = ITEMS.registerItem("harness", HarnessItem::new,
            new Item.Properties().stacksTo(1));
    public static final Supplier<AttachmentType<Optional<HitchLink>>> HITCH_LINK = ATTACHMENTS.register("hitch_link",
            () -> AttachmentType.<Optional<HitchLink>>builder(Optional::empty)
                    .serialize(HitchLink.CODEC.optionalFieldOf("link").codec(), Optional::isPresent)
                    .sync(ByteBufCodecs.optional(HitchLink.STREAM_CODEC)).build());
    /** Whether the animal wears a harness (saved, and synced so clients draw it). */
    public static final Supplier<AttachmentType<Boolean>> HARNESS_WORN = ATTACHMENTS.register("harness_worn",
            () -> AttachmentType.builder(() -> false).serialize(Codec.BOOL, worn -> worn)
                    .sync(ByteBufCodecs.BOOL).build());

    public HarnessMod(IEventBus modBus, ModContainer container) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        ATTACHMENTS.register(modBus);
        container.registerConfig(ModConfig.Type.COMMON, HarnessConfig.SPEC);
        modBus.addListener((BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
                e.accept(HITCH_ITEM.get());
                e.accept(WAGON_WHEEL_ITEM.get());
                e.accept(OAK_SLOPE_ITEM.get());
                e.accept(STONE_SLOPE_ITEM.get());
                GENTLE_SLOPE_ITEMS.forEach(i -> e.accept(i.get()));
            } else if (e.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
                e.accept(HARNESS.get());
            }
        });
        // the hitch block was called "harness" in the first build (2026-11-24); worlds made then keep their hitches
        modBus.addListener((RegisterEvent e) -> {
            if (e.getRegistryKey().equals(Registries.BLOCK)) {
                BuiltInRegistries.BLOCK.addAlias(id("harness"), id("hitch"));
            } else if (e.getRegistryKey().equals(Registries.BLOCK_ENTITY_TYPE)) {
                BuiltInRegistries.BLOCK_ENTITY_TYPE.addAlias(id("harness"), id("hitch"));
            }
        });
        Harnessing.register();
        com.porcupinestudios.ringbearer.harness.dev.Showcase.register();
        com.porcupinestudios.ringbearer.harness.dev.Movies.register();
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent e) -> {
            e.addListener(new DraughtAnimals());
            e.addListener(WHEELS);
            e.addListener(SLOPE_CONTACTS);
        });
        SlopeContacts.register();
        NeoForge.EVENT_BUS.addListener(HarnessMod::afterEntityTick);
    }

    private static DeferredBlock<SlopeBlock> slope(String name, Block like, SlopeBlock.Profile profile) {
        return BLOCKS.register(name, () -> new SlopeBlock(BlockBehaviour.Properties.ofFullCopy(like).noOcclusion(),
                profile));
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    /** A hitched animal that walked past the end of its traces is held back, after it moves and before it's sent. */
    private static void afterEntityTick(EntityTickEvent.Post event) {
        Entity e = event.getEntity();
        if (e.level().isClientSide || !e.hasData(HITCH_LINK)) {
            return;
        }
        e.getData(HITCH_LINK).ifPresent(link -> {
            if (e.level().getBlockEntity(link.harness()) instanceof HitchBlockEntity hitch) {
                hitch.restrain(e);
            }
        });
    }
}
