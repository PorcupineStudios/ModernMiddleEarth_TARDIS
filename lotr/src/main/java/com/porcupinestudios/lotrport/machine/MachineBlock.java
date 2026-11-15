package com.porcupinestudios.lotrport.machine;

import com.mojang.serialization.MapCodec;
import com.porcupinestudios.lotrport.content.FacingBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A LOTR machine with a screen: a faction forge, the hobbit oven, the millstone or the unsmeltery. {@code lit} shows
 * LOTR's active texture while it burns (or mills). Right-click opens the screen; breaking it drops the contents.
 */
public class MachineBlock extends FacingBlock implements EntityBlock {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    private final String machine;

    public MachineBlock(Properties properties, float[] box, String machine) {
        super(properties, box);
        this.machine = machine;
        registerDefaultState(defaultBlockState().setValue(LIT, false));
    }

    public String machine() {
        return machine;
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(p -> new MachineBlock(p, null, machine));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LIT);
    }

    /** The unsmeltery is drawn by its block entity renderer, on LOTR's model. */
    @Override
    protected net.minecraft.world.level.block.RenderShape getRenderShape(BlockState state) {
        return machine.equals("unsmeltery") ? net.minecraft.world.level.block.RenderShape.ENTITYBLOCK_ANIMATED
                : net.minecraft.world.level.block.RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return switch (machine) {
            case "oven" -> new OvenBlockEntity(pos, state);
            case "millstone" -> new MillstoneBlockEntity(pos, state);
            case "unsmeltery" -> new UnsmelteryBlockEntity(pos, state);
            default -> new ForgeBlockEntity(pos, state);
        };
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        return level.isClientSide ? null : (l, p, s, be) -> {
            if (be instanceof MachineBlockEntity m) {
                m.serverTick();
            }
        };
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof MenuProvider menu) {
            player.openMenu(menu);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof Container c) {
            Containers.dropContents(level, pos, c);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return AbstractContainerMenu.getRedstoneSignalFromBlockEntity(level.getBlockEntity(pos));
    }

    /** Smoke and crackle while lit, as a furnace. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT) || machine.equals("millstone")) {
            return;
        }
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.2 + random.nextDouble() * 0.6;
        double z = pos.getZ() + 0.5;
        if (random.nextDouble() < 0.1) {
            level.playLocalSound(x, pos.getY(), z, SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 1f, 1f, false);
        }
        var face = state.getValue(FACING);
        double side = random.nextDouble() * 0.6 - 0.3;
        double dx = face.getStepX() * 0.52 + (face.getAxis() == net.minecraft.core.Direction.Axis.Z ? side : 0);
        double dz = face.getStepZ() * 0.52 + (face.getAxis() == net.minecraft.core.Direction.Axis.X ? side : 0);
        level.addParticle(ParticleTypes.SMOKE, x + dx, y, z + dz, 0, 0, 0);
        level.addParticle(ParticleTypes.FLAME, x + dx, y, z + dz, 0, 0, 0);
    }
}
