package com.porcupinestudios.ringbearer.harness;

import com.porcupinestudios.ringbearer.harness.content.HarnessBlock;
import com.porcupinestudios.ringbearer.harness.content.HarnessBlockEntity;
import com.porcupinestudios.ringbearer.harness.content.HitchLink;
import com.porcupinestudios.ringbearer.harness.draught.DraughtAnimals;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
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

/**
 * Ring Bearer: Harness. Animals pull Create: Aeronautics vehicles: a harness block on the vehicle, hitched animals,
 * and a pull computed inside Sable's physics steps. Read {@code harness/docs/harness.md} first.
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

    public static final DeferredBlock<HarnessBlock> HARNESS = BLOCKS.register("harness",
            () -> new HarnessBlock(BlockBehaviour.Properties.of().strength(1.5f).sound(SoundType.WOOD).noOcclusion()));
    public static final Supplier<BlockItem> HARNESS_ITEM = ITEMS.registerSimpleBlockItem(HARNESS);
    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<HarnessBlockEntity>> HARNESS_BE = BLOCK_ENTITIES.register("harness",
            () -> BlockEntityType.Builder.of(HarnessBlockEntity::new, HARNESS.get()).build(null));
    public static final Supplier<AttachmentType<Optional<HitchLink>>> HITCH_LINK = ATTACHMENTS.register("hitch_link",
            () -> AttachmentType.<Optional<HitchLink>>builder(Optional::empty)
                    .serialize(HitchLink.CODEC.optionalFieldOf("link").codec(), Optional::isPresent).build());

    public HarnessMod(IEventBus modBus, ModContainer container) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        ATTACHMENTS.register(modBus);
        container.registerConfig(ModConfig.Type.COMMON, HarnessConfig.SPEC);
        modBus.addListener((BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
                e.accept(HARNESS_ITEM.get());
            }
        });
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent e) -> e.addListener(new DraughtAnimals()));
        NeoForge.EVENT_BUS.addListener(HarnessMod::afterEntityTick);
    }

    /** A hitched animal that walked past the end of its traces is held back, after it moves and before it's sent. */
    private static void afterEntityTick(EntityTickEvent.Post event) {
        Entity e = event.getEntity();
        if (e.level().isClientSide || !e.hasData(HITCH_LINK)) {
            return;
        }
        e.getData(HITCH_LINK).ifPresent(link -> {
            if (e.level().getBlockEntity(link.harness()) instanceof HarnessBlockEntity harness) {
                harness.restrain(e);
            }
        });
    }
}
