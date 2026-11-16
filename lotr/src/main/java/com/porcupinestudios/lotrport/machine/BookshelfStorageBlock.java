package com.porcupinestudios.lotrport.machine;

import com.mojang.serialization.MapCodec;
import com.porcupinestudios.lotrport.content.LotrBlocks;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * LOTR's bookshelf storage: right-click a vanilla bookshelf with an empty hand and it opens as a 27-slot store,
 * still looking like a bookshelf (breaks into a bookshelf, dropping what it holds).
 */
public class BookshelfStorageBlock extends LotrChestBlock {
    public BookshelfStorageBlock(Properties properties) {
        super(properties, null);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(BookshelfStorageBlock::new);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(BookshelfStorageBlock::onUse);
    }

    private static void onUse(PlayerInteractEvent.RightClickBlock event) {
        var level = event.getLevel();
        if (!event.getItemStack().isEmpty() || event.getEntity().isShiftKeyDown()
                || !level.getBlockState(event.getPos()).is(Blocks.BOOKSHELF)) {
            return;
        }
        var block = LotrBlocks.BY_ID.get("bookshelf_storage");
        if (block == null) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
        if (!level.isClientSide) {
            Direction facing = event.getEntity().getDirection().getOpposite();
            level.setBlock(event.getPos(), block.get().defaultBlockState().setValue(FACING, facing), 3);
            if (level.getBlockEntity(event.getPos()) instanceof LotrChestBlockEntity chest) {
                event.getEntity().openMenu(chest);
            }
        }
    }
}
