package com.porcupinestudios.ringbearer.harness.content;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The harness: build it into the front of a Create: Aeronautics vehicle, facing where the animals will walk. Lead
 * animals to it and use it to hitch every one you're leading that can pull (it takes their leads); sneak-use with an
 * empty hand to unhitch them (they come back on your leads).
 */
public class HarnessBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<HarnessBlock> CODEC = simpleCodec(HarnessBlock::new);

    public HarnessBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HarnessBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level.isClientSide || !(level.getBlockEntity(pos) instanceof HarnessBlockEntity harness)) {
            return InteractionResult.SUCCESS;
        }
        if (player.isShiftKeyDown()) {
            List<Entity> freed = harness.unhitchAll();
            for (Entity e : freed) {
                if (e instanceof Mob mob) {
                    mob.setLeashedTo(player, true);
                }
            }
            player.displayClientMessage(Component.translatable("message.ringbearer_harness.unhitched", freed.size()),
                    true);
            return InteractionResult.CONSUME;
        }
        int hitched = 0;
        AABB around = player.getBoundingBox().inflate(10);
        for (Mob mob : level.getEntitiesOfClass(Mob.class, around, m -> m.getLeashHolder() == player)) {
            if (harness.hitch(mob)) {
                mob.dropLeash(true, false);
                hitched++;
            }
        }
        player.displayClientMessage(hitched > 0
                ? Component.translatable("message.ringbearer_harness.hitched", hitched)
                : Component.translatable("message.ringbearer_harness.none"), true);
        return InteractionResult.CONSUME;
    }
}
