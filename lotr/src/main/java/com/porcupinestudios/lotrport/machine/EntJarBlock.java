package com.porcupinestudios.lotrport.machine;

import com.mojang.serialization.MapCodec;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.content.ShapedBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The Ent jar (LOTRBlockEntJar). Right-click with:
 * <ul>
 * <li>an Ent-draught: pours it in (you keep the bowl);</li>
 * <li>a bowl: takes a serving of the draught out;</li>
 * <li>a water bucket: fills it with water; an empty bucket takes a full jar of water back;</li>
 * <li>an ingredient, in a biome tagged {@code lotr_port:ent_jar_brewing} (Fangorn): turns its water into that
 * draught ({@code ent_jar} recipes).</li>
 * </ul>
 * Rain fills it slowly.
 */
public class EntJarBlock extends ShapedBlock implements EntityBlock {
    public static final TagKey<Biome> BREWING = TagKey.create(Registries.BIOME, LotrPort.id("ent_jar_brewing"));

    public EntJarBlock(Properties properties, float[] box) {
        super(properties, box);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return simpleCodec(p -> new EntJarBlock(p, null));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EntJarBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        return level.isClientSide ? null : (l, p, s, be) -> {
            if (be instanceof EntJarBlockEntity j) {
                j.serverTick();
            }
        };
    }

    private static boolean isDraught(ItemStack stack) {
        return stack.getItem().builtInRegistryHolder().key().location().getPath().endsWith("_ent_draught");
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof EntJarBlockEntity jar) || stack.isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (isDraught(stack)) {
            if (level.isClientSide) {
                return ItemInteractionResult.SUCCESS;
            }
            if (!jar.pourIn(stack)) {
                return ItemInteractionResult.FAIL;
            }
            player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.BOWL)));
            fillSound(level, pos);
            return ItemInteractionResult.CONSUME;
        }
        if (stack.is(Items.BOWL) && !jar.drink().isEmpty()) {
            if (!level.isClientSide) {
                player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, jar.takeDraught()));
                fillSound(level, pos);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (stack.is(Items.WATER_BUCKET) && jar.amount() == 0) {
            if (!level.isClientSide) {
                jar.fillWithWater();
                player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.BUCKET)));
                level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1f, 1f);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (stack.is(Items.BUCKET) && jar.isWater() && jar.amount() >= jar.capacity()) {
            if (!level.isClientSide) {
                jar.empty();
                player.setItemInHand(hand,
                        ItemUtils.createFilledResult(stack, player, new ItemStack(Items.WATER_BUCKET)));
                level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1f, 1f);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level.getBiome(pos).is(BREWING)) {
            ItemStack draught = jar.brew(stack);
            if (!draught.isEmpty()) {
                if (!level.isClientSide) {
                    jar.turnInto(draught);
                    stack.consume(1, player);
                    level.playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.5f, 1f);
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    private static void fillSound(Level level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 0.5f,
                0.8f + level.random.nextFloat() * 0.4f);
    }
}
