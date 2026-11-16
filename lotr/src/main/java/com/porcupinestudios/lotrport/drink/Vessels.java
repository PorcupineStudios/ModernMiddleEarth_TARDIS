package com.porcupinestudios.lotrport.drink;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.content.ContentData;
import com.porcupinestudios.lotrport.content.LotrBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Setting drinks down (LOTR's placeable mugs): sneak + use an empty vessel or a drink on the top of a block puts
 * the vessel's block there holding it ({@code behaviors.json} "vessel_blocks": empty vessel item -> block).
 */
public final class Vessels {
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, LotrPort.MOD_ID);
    public static final Supplier<BlockEntityType<VesselBlockEntity>> BLOCK_ENTITY = BLOCK_ENTITIES.register("vessel",
            () -> BlockEntityType.Builder.of(VesselBlockEntity::new, LotrBlocks.BY_ID.values().stream()
                    .map(Supplier::get).filter(b -> b instanceof VesselBlock).toArray(Block[]::new)).build(null));

    private static Map<ResourceLocation, String> blocks;

    private Vessels() {}

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
        NeoForge.EVENT_BUS.addListener(Vessels::onUse);
    }

    /** Block ids that are set-down vessels. */
    public static boolean isVesselBlock(String id) {
        return byItem().containsValue(id);
    }

    private static Map<ResourceLocation, String> byItem() {
        if (blocks == null) {
            blocks = new HashMap<>();
            ContentData.behaviours().getAsJsonObject("vessel_blocks").entrySet().stream()
                    .filter(e -> !e.getKey().startsWith("_"))
                    .forEach(e -> blocks.put(ResourceLocation.parse(e.getKey()), e.getValue().getAsString()));
        }
        return blocks;
    }

    private static void onUse(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();
        if (!player.isShiftKeyDown() || stack.isEmpty() || event.getFace() != Direction.UP) {
            return;
        }
        Item vessel = Drinks.BY_ITEM.containsKey(BuiltInRegistries.ITEM.getKey(stack.getItem()))
                ? Drinks.vessel(stack) : stack.getItem();
        String blockId = byItem().get(BuiltInRegistries.ITEM.getKey(vessel));
        if (blockId == null || !LotrBlocks.BY_ID.containsKey(blockId)) {
            return;
        }
        Level level = event.getLevel();
        BlockPos pos = event.getPos().above();
        BlockState state = LotrBlocks.BY_ID.get(blockId).get().defaultBlockState();
        if (!level.getBlockState(pos).canBeReplaced() || !state.canSurvive(level, pos)) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
        if (level.isClientSide) {
            return;
        }
        level.setBlock(pos, state, 3);
        if (level.getBlockEntity(pos) instanceof VesselBlockEntity be) {
            be.set(stack);
        }
        level.playSound(null, pos, state.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1f, 1f);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
    }
}
