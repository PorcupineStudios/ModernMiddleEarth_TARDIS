package com.porcupinestudios.lotrport.content.goblin;

import com.porcupinestudios.lotrport.LotrPort;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Goblin Town's own blocks (2026-11-26, Aaron: scrappy wood scaffolding, single wooden posts, tied in by contrast and
 * hue). Hand-registered; textures, models, loot and recipes are drawn and written by {@code converter/goblin_blocks.py}
 * from one palette (weathered warm browns, beige rope, dark iron), lang in {@code colonies.py UI_EN}, mining tags in
 * {@code tags.py HAND}.
 */
public final class GoblinBlocks {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(LotrPort.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(LotrPort.MOD_ID);

    private static BlockBehaviour.Properties wood() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).strength(2f, 3f).sound(SoundType.WOOD)
                .ignitedByLava();
    }

    public static final DeferredBlock<Block> GOBLIN_PLANKS = BLOCKS.register("goblin_planks", () -> new Block(wood()));
    public static final DeferredBlock<SlabBlock> GOBLIN_PLANK_SLAB = BLOCKS.register("goblin_plank_slab",
            () -> new SlabBlock(wood()));
    public static final DeferredBlock<StairBlock> GOBLIN_PLANK_STAIRS = BLOCKS.register("goblin_plank_stairs",
            () -> new StairBlock(GOBLIN_PLANKS.get().defaultBlockState(), wood()));
    public static final DeferredBlock<LashedPostBlock> LASHED_POST = BLOCKS.register("lashed_post",
            () -> new LashedPostBlock(wood().noOcclusion()));
    public static final DeferredBlock<Block> GOBLIN_SCAFFOLD = BLOCKS.register("goblin_scaffold",
            () -> new Block(wood().noOcclusion().isViewBlocking((s, l, p) -> false)));
    public static final DeferredBlock<RopeBridgeBlock> ROPE_BRIDGE = BLOCKS.register("rope_bridge",
            () -> new RopeBridgeBlock(wood().strength(1f, 1f).noOcclusion()));
    public static final DeferredBlock<RopeRailBlock> ROPE_RAIL = BLOCKS.register("rope_rail",
            () -> new RopeRailBlock(BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(0.5f)
                    .sound(SoundType.WOOL).noOcclusion().ignitedByLava()));
    public static final DeferredBlock<Block> GOBLIN_CAGE = BLOCKS.register("goblin_cage",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4f, 6f)
                    .sound(SoundType.CHAIN).requiresCorrectToolForDrops().noOcclusion()
                    .isViewBlocking((s, l, p) -> false)));
    public static final DeferredBlock<BrazierBlock> GOBLIN_BRAZIER = BLOCKS.register("goblin_brazier",
            () -> new BrazierBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3f, 6f)
                    .sound(SoundType.LANTERN).requiresCorrectToolForDrops().noOcclusion().lightLevel(s -> 15)));
    public static final DeferredBlock<GoblinStrawBlock> GOBLIN_STRAW = BLOCKS.register("goblin_straw",
            () -> new GoblinStrawBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).strength(0.5f)
                    .sound(SoundType.GRASS).ignitedByLava()));
    public static final DeferredBlock<PorchFloorBlock> PORCH_FLOOR = BLOCKS.register("porch_floor",
            () -> new PorchFloorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(1.2f, 3f)
                    .sound(SoundType.GRAVEL)));

    private static final List<DeferredBlock<? extends Block>> ALL = List.of(GOBLIN_PLANKS, GOBLIN_PLANK_SLAB,
            GOBLIN_PLANK_STAIRS, LASHED_POST, GOBLIN_SCAFFOLD, ROPE_BRIDGE, ROPE_RAIL, GOBLIN_CAGE, GOBLIN_BRAZIER,
            GOBLIN_STRAW, PORCH_FLOOR);
    private static final List<DeferredItem<BlockItem>> ITEMS_ALL = ALL.stream()
            .map(b -> ITEMS.registerSimpleBlockItem(b)).toList();

    private static final ResourceKey<CreativeModeTab> TAB = ResourceKey.create(Registries.CREATIVE_MODE_TAB,
            ResourceLocation.fromNamespaceAndPath(LotrPort.MOD_ID, "blocks"));

    private GoblinBlocks() {}

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(GoblinBlocks::tabs);
    }

    private static void tabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(TAB)) {
            for (Supplier<? extends Item> i : ITEMS_ALL) {
                event.accept(i.get());
            }
        }
    }

    /** A thin post with rope lashings; stands, or lies as a beam (axis like a log). */
    public static final class LashedPostBlock extends RotatedPillarBlock {
        private static final VoxelShape Y = Block.box(5, 0, 5, 11, 16, 11);
        private static final VoxelShape X = Block.box(0, 5, 5, 16, 11, 11);
        private static final VoxelShape Z = Block.box(5, 5, 0, 11, 11, 16);

        public LashedPostBlock(BlockBehaviour.Properties p) {
            super(p);
        }

        @Override
        protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
            return switch (s.getValue(AXIS)) {
                case X -> X;
                case Z -> Z;
                default -> Y;
            };
        }
    }

    /** A rope bridge's slatted deck, 3 pixels thick, low or raised half a block (so a sagging bridge walks). */
    public static final class RopeBridgeBlock extends Block {
        public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
        public static final EnumProperty<Half> HALF = BlockStateProperties.HALF;
        private static final VoxelShape BOTTOM = Block.box(0, 0, 0, 16, 3, 16);
        private static final VoxelShape TOP = Block.box(0, 8, 0, 16, 11, 16);

        public RopeBridgeBlock(BlockBehaviour.Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.Z).setValue(HALF, Half.BOTTOM));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(AXIS, HALF);
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext ctx) {
            Direction face = ctx.getClickedFace();
            double y = ctx.getClickLocation().y - ctx.getClickedPos().getY();
            boolean top = face == Direction.DOWN || (face != Direction.UP && y > 0.5);
            return defaultBlockState().setValue(AXIS, ctx.getHorizontalDirection().getAxis())
                    .setValue(HALF, top ? Half.TOP : Half.BOTTOM);
        }

        @Override
        protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
            return s.getValue(HALF) == Half.TOP ? TOP : BOTTOM;
        }
    }

    /** A rope handrail at hand height along its axis; it stops you as a fence would. */
    public static final class RopeRailBlock extends Block {
        public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
        private static final VoxelShape SHAPE_Z = Block.box(6.5, 9.5, 0, 9.5, 12.5, 16);
        private static final VoxelShape SHAPE_X = Block.box(0, 9.5, 6.5, 16, 12.5, 9.5);
        private static final VoxelShape WALL_Z = Block.box(7, 0, 0, 9, 24, 16);
        private static final VoxelShape WALL_X = Block.box(0, 0, 7, 16, 24, 9);

        public RopeRailBlock(BlockBehaviour.Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.Z));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(AXIS);
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext ctx) {
            return defaultBlockState().setValue(AXIS, ctx.getHorizontalDirection().getAxis());
        }

        @Override
        protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
            return s.getValue(AXIS) == Direction.Axis.X ? SHAPE_X : SHAPE_Z;
        }

        @Override
        protected VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
            return s.getValue(AXIS) == Direction.Axis.X ? WALL_X : WALL_Z;
        }
    }

    /** An iron fire bowl on legs: Goblin Town's light, orange against the grey rock. */
    public static final class BrazierBlock extends Block {
        private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 9, 14);

        public BrazierBlock(BlockBehaviour.Properties p) {
            super(p);
        }

        @Override
        protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
            return SHAPE;
        }

        @Override
        public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
            if (!entity.fireImmune() && !entity.isSteppingCarefully()) {
                entity.igniteForSeconds(3);
            }
            super.stepOn(level, pos, state, entity);
        }
    }

    /** Filthy straw: nothing that lands on it is hurt (the landing at the bottom of the Front Porch's chute). */
    public static final class GoblinStrawBlock extends Block {
        public GoblinStrawBlock(BlockBehaviour.Properties p) {
            super(p);
        }

        @Override
        public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, float fallDistance) {
            entity.causeFallDamage(fallDistance, 0f, level.damageSources().fall());
        }
    }

    /**
     * The Front Porch's floor (and its decoys'): one block, the same everywhere. What is under it decides: over a drop
     * of at least {@code min_drop} it gives way when someone stays on it ({@link GoblinTraps}), on rock it never does,
     * so neither looking nor F3 tells a trap from a decoy. A redstone signal springs one over a drop, so players can
     * build their own (and drive one from Create).
     */
    public static final class PorchFloorBlock extends Block {
        public PorchFloorBlock(BlockBehaviour.Properties p) {
            super(p);
        }

        @Override
        protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from,
                                       boolean moving) {
            super.neighborChanged(state, level, pos, block, from, moving);
            if (level instanceof ServerLevel server && level.hasNeighborSignal(pos)) {
                GoblinTraps.spring(server, pos, null);
            }
        }
    }
}
