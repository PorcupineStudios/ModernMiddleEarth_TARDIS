package com.porcupinestudios.lotrport.crafting;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** A faction crafting table: opens a crafting grid that also knows this faction's recipes. */
public class FactionTableBlock extends Block {
    public static final MapCodec<FactionTableBlock> CODEC = simpleCodec(FactionTableBlock::new);

    public FactionTableBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (!level.isClientSide) {
            var id = BuiltInRegistries.BLOCK.getKey(this);
            player.openMenu(new SimpleMenuProvider(
                    (windowId, inventory, p) -> new FactionCraftingMenu(windowId, inventory,
                            ContainerLevelAccess.create(level, pos), id),
                    Component.translatable(getDescriptionId())));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
