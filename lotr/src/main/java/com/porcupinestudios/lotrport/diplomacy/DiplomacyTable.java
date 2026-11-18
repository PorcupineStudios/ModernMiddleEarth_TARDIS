package com.porcupinestudios.lotrport.diplomacy;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.ui.View;
import com.porcupinestudios.lotrport.ui.Views;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The Diplomacy Table: a map table a ruler keeps in their Hall. Using it opens the diplomacy screen
 * ({@link DiplomacyViews}); so does {@code /lotr_kingdom diplomacy}. Hand-registered: its tags and lang are patched into
 * the generated files by {@code converter/generate.py} and {@code colonies.py UI_EN}.
 */
public final class DiplomacyTable extends Block {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(LotrPort.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(LotrPort.MOD_ID);

    public static final DeferredBlock<DiplomacyTable> BLOCK = BLOCKS.register("diplomacy_table",
            () -> new DiplomacyTable(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5f)
                    .sound(SoundType.WOOD).ignitedByLava()));
    public static final DeferredItem<BlockItem> ITEM = ITEMS.registerSimpleBlockItem(BLOCK, new Item.Properties());

    private static final ResourceKey<CreativeModeTab> TAB = ResourceKey.create(Registries.CREATIVE_MODE_TAB,
            ResourceLocation.fromNamespaceAndPath(LotrPort.MOD_ID, "colonies"));

    public DiplomacyTable(Properties properties) {
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

    public static void open(ServerPlayer player) {
        Views.open(player, new View.Target(DiplomacyViews.ID, "", "realms"), true);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            open(sp);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
