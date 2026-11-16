package com.porcupinestudios.lotrport.content;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Supplier;

import com.mojang.serialization.MapCodec;
import com.porcupinestudios.lotrport.LotrPort;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The beacon of Gondor (LOTRBlockBeacon + LOTRTileEntityBeacon): lit with flint and steel, a match or a fire charge,
 * put out with a water bucket. Once it has burned 100 ticks it is fully lit and lights every beacon within 80 blocks
 * (LOTR's 6400 squared distance), so a chain of beacons passes the signal along; putting one out passes that on too.
 * A lit beacon gives full light, burns what stands in it and throws flames and smoke.
 */
public class GondorBeaconBlock extends ShapedBlock implements EntityBlock {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    static final int FULLY_LIT = 100;
    static final double RANGE_SQ = 6400;

    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, LotrPort.MOD_ID);
    public static final Supplier<BlockEntityType<Beacon>> BLOCK_ENTITY = BLOCK_ENTITIES.register("gondor_beacon",
            () -> BlockEntityType.Builder.of(Beacon::new, LotrBlocks.BY_ID.values().stream()
                    .map(Supplier::get).filter(b -> b instanceof GondorBeaconBlock).toArray(Block[]::new)).build(null));

    /** Loaded beacons, per level, for passing the signal on without scanning the world. */
    private static final Set<Beacon> LOADED = Collections.newSetFromMap(new WeakHashMap<>());

    public GondorBeaconBlock(Properties properties, float[] box) {
        super(properties, box);
        registerDefaultState(stateDefinition.any().setValue(LIT, false));
    }

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return simpleCodec(p -> new GondorBeaconBlock(p, null));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    @Override
    public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(LIT) ? 15 : 0;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Beacon(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        return level.isClientSide ? null : (l, p, s, be) -> {
            if (be instanceof Beacon b) {
                b.serverTick();
            }
        };
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        boolean lit = state.getValue(LIT);
        boolean lighter = stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.FIRE_CHARGE)
                || stack.getItem() instanceof MatchItem;
        if (!lit && lighter) {
            if (!level.isClientSide) {
                setLit(level, pos, true);
                level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1f, 1f);
                if (!player.getAbilities().instabuild) {
                    if (stack.isDamageableItem()) {
                        stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
                    } else {
                        stack.shrink(1);
                    }
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (lit && stack.is(Items.WATER_BUCKET)) {
            if (!level.isClientSide) {
                setLit(level, pos, false);
                level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1f, 1f);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    static void setLit(Level level, BlockPos pos, boolean lit) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof GondorBeaconBlock && state.getValue(LIT) != lit) {
            level.setBlock(pos, state.setValue(LIT, lit), 3);
            if (level.getBlockEntity(pos) instanceof Beacon b) {
                b.counter = 0;
                b.setChanged();
            }
        }
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (state.getValue(LIT) && !entity.fireImmune()) {
            entity.igniteForSeconds(8);
            entity.hurt(level.damageSources().inFire(), 1f);
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource r) {
        if (!state.getValue(LIT)) {
            return;
        }
        for (int i = 0; i < 3; i++) {
            double x = pos.getX() + 0.2 + r.nextDouble() * 0.6;
            double z = pos.getZ() + 0.2 + r.nextDouble() * 0.6;
            level.addParticle(ParticleTypes.FLAME, x, pos.getY() + 0.9, z, 0, 0.05, 0);
            level.addParticle(ParticleTypes.LARGE_SMOKE, x, pos.getY() + 1.2, z, 0, 0.07, 0);
        }
        if (r.nextInt(24) == 0) {
            level.playLocalSound(pos, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 1f, 1f, false);
        }
    }

    /** Counts how long the beacon has been lit (or out), then passes that on to the beacons in range. */
    public static class Beacon extends BlockEntity {
        int counter;

        public Beacon(BlockPos pos, BlockState state) {
            super(BLOCK_ENTITY.get(), pos, state);
        }

        @Override
        public void onLoad() {
            super.onLoad();
            LOADED.add(this);
        }

        @Override
        public void setRemoved() {
            LOADED.remove(this);
            super.setRemoved();
        }

        void serverTick() {
            if (counter > FULLY_LIT || !(level instanceof ServerLevel server)) {
                return;
            }
            if (++counter == FULLY_LIT) {
                boolean lit = getBlockState().getValue(LIT);
                for (Beacon other : LOADED.toArray(new Beacon[0])) {
                    if (other != this && other.level == server && !other.isRemoved()
                            && other.getBlockPos().distSqr(worldPosition) <= RANGE_SQ) {
                        setLit(server, other.getBlockPos(), lit);
                    }
                }
            }
        }

        @Override
        protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            tag.putInt("Counter", counter);
        }

        @Override
        protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            counter = tag.getInt("Counter");
        }
    }
}
