package com.porcupinestudios.ringbearer.harness;

import com.porcupinestudios.ringbearer.harness.content.HarnessItem;
import com.porcupinestudios.ringbearer.harness.content.Harnessing;
import com.porcupinestudios.ringbearer.harness.content.HitchBlock;
import com.porcupinestudios.ringbearer.harness.content.HitchBlockEntity;
import com.porcupinestudios.ringbearer.harness.content.HitchLink;
import com.porcupinestudios.ringbearer.harness.draught.DraughtAnimals;
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
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent e) -> e.addListener(new DraughtAnimals()));
        NeoForge.EVENT_BUS.addListener(HarnessMod::afterEntityTick);
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
