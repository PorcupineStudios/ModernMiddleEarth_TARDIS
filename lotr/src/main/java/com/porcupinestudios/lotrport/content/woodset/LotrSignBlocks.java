package com.porcupinestudios.lotrport.content.woodset;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CeilingHangingSignBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.WallHangingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WoodType;

/**
 * LOTR woods' signs and hanging signs: vanilla's sign blocks with this mod's block entity types (vanilla's types only
 * accept vanilla's blocks). Wall variants pick as their sign item.
 */
public final class LotrSignBlocks {
    private LotrSignBlocks() {}

    /** The sign item a wall sign picks as ({@code mallorn_wall_sign} -> {@code mallorn_sign}). */
    static ItemStack item(Block wall) {
        var key = BuiltInRegistries.BLOCK.getKey(wall);
        return new ItemStack(BuiltInRegistries.ITEM.get(key.withPath(key.getPath().replace("_wall_", "_"))));
    }

    @SuppressWarnings("unchecked")
    static <T extends BlockEntity> BlockEntityTicker<T> ticker(Level level, BlockEntityType<T> type,
            BlockEntityType<? extends SignBlockEntity> ours) {
        return level.isClientSide || type != ours ? null
                : (BlockEntityTicker<T>) (BlockEntityTicker<SignBlockEntity>) SignBlockEntity::tick;
    }

    public static class Standing extends StandingSignBlock {
        public Standing(WoodType type, Properties properties) {
            super(type, properties);
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new LotrSignBlockEntity(pos, state);
        }

        @Override
        public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                BlockEntityType<T> type) {
            return ticker(level, type, WoodSets.SIGN.get());
        }
    }

    public static class Wall extends WallSignBlock {
        public Wall(WoodType type, Properties properties) {
            super(type, properties);
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new LotrSignBlockEntity(pos, state);
        }

        @Override
        public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                BlockEntityType<T> type) {
            return ticker(level, type, WoodSets.SIGN.get());
        }

        @Override
        public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
            return item(this);
        }
    }

    public static class Ceiling extends CeilingHangingSignBlock {
        public Ceiling(WoodType type, Properties properties) {
            super(type, properties);
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new LotrHangingSignBlockEntity(pos, state);
        }

        @Override
        public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                BlockEntityType<T> type) {
            return ticker(level, type, WoodSets.HANGING_SIGN.get());
        }
    }

    public static class WallHanging extends WallHangingSignBlock {
        public WallHanging(WoodType type, Properties properties) {
            super(type, properties);
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new LotrHangingSignBlockEntity(pos, state);
        }

        @Override
        public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                BlockEntityType<T> type) {
            return ticker(level, type, WoodSets.HANGING_SIGN.get());
        }

        @Override
        public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
            return item(this);
        }
    }
}
