package com.porcupinestudios.lotrport.content;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

/**
 * A shaped block that needs something next to it to stay put, as LOTR's {@code canBlockStay}: fallen leaves and
 * thatch on a floor, chandeliers and cages hanging, marsh lights over water. It can't be placed without its support
 * and pops off (dropping itself) when the support goes. Which block needs what is data: {@code behaviors.json}.
 */
public class SupportedBlock extends ShapedBlock {
    public enum Support {
        FLOOR, FLOOR_OR_WATER, WATER, HANGING, HANGING_OR_FLOOR, CHAIN, STALACTITE, STALAGMITE
    }

    private static Map<String, Support> byId;
    private final Support support;

    public SupportedBlock(Properties properties, float[] box, Support support) {
        super(properties, box);
        this.support = support;
    }

    /** The support a block id needs, or null for none. */
    public static Support of(String id) {
        if (byId == null) {
            byId = new HashMap<>();
            JsonObject s = ContentData.readObject("behaviors.json").getAsJsonObject("support");
            for (String key : s.keySet()) {
                Support v = Support.valueOf(key.toUpperCase(Locale.ROOT));
                JsonArray ids = s.getAsJsonArray(key);
                ids.forEach(i -> byId.put(i.getAsString(), v));
            }
        }
        return byId.get(id);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return simpleCodec(p -> new SupportedBlock(p, null, support));
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return switch (support) {
            case FLOOR, STALAGMITE -> floor(level, pos);
            case FLOOR_OR_WATER -> floor(level, pos) || water(level, pos);
            case WATER -> water(level, pos);
            case HANGING, STALACTITE -> hanging(level, pos);
            case HANGING_OR_FLOOR -> hanging(level, pos) || floor(level, pos);
            case CHAIN -> hanging(level, pos) || level.getBlockState(pos.above()).is(this);
        };
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level,
            BlockPos pos, BlockPos neighborPos) {
        if (dir.getAxis().isVertical() && !canSurvive(state, level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, dir, neighbor, level, pos, neighborPos);
    }

    private static boolean floor(LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    private static boolean water(LevelReader level, BlockPos pos) {
        return level.getFluidState(pos.below()).is(Fluids.WATER);
    }

    /** LOTR: a solid face above, or a fence, wall or chain to hang from. */
    private static boolean hanging(LevelReader level, BlockPos pos) {
        BlockPos above = pos.above();
        BlockState s = level.getBlockState(above);
        return s.isFaceSturdy(level, above, Direction.DOWN) || s.is(BlockTags.FENCES) || s.is(BlockTags.WALLS)
                || s.getBlock() instanceof ChainBlock || s.getBlock() instanceof SupportedBlock b
                        && b.support == Support.CHAIN;
    }
}
