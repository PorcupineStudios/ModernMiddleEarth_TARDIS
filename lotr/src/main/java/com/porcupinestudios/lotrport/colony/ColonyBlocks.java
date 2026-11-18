package com.porcupinestudios.lotrport.colony;

import com.mojang.serialization.MapCodec;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.colony.building.Building;
import com.porcupinestudios.lotrport.colony.building.BuildingSettings;
import com.porcupinestudios.lotrport.colony.building.Buildings;
import com.porcupinestudios.lotrport.colony.members.Permissions;
import com.porcupinestudios.lotrport.colony.book.Books;
import com.porcupinestudios.lotrport.colony.book.CustomsBook;
import com.porcupinestudios.lotrport.colony.book.HallLedgerBook;
import com.porcupinestudios.lotrport.colony.book.PlaqueBook;
import com.porcupinestudios.lotrport.colony.book.TreasuryBook;
import java.util.Comparator;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The colony's furniture (RingRealms' plaques and Ledger Stand): right-click opens its screen.
 * <ul>
 *   <li>Plaque: hung on a building's wall (inside, or outside by the door), its building page.</li>
 *   <li>Treasury Ledger: the colony's books ({@link TreasuryBook}).</li>
 *   <li>Town Hall Ledger: the register, buildings, people and members ({@link HallLedgerBook}).</li>
 *   <li>Customs Counter: groups wanting to settle ({@link CustomsBook}).</li>
 * </ul>
 * The ledgers and the counter belong to the nearest colony whose Hall is within the building reach.
 */
public final class ColonyBlocks {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(LotrPort.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(LotrPort.MOD_ID);

    public enum Kind { PLAQUE, TREASURY, HALL, CUSTOMS }

    public static final DeferredBlock<Block> PLAQUE = block("building_plaque", Kind.PLAQUE);
    public static final DeferredBlock<Block> TREASURY_LEDGER = block("treasury_ledger", Kind.TREASURY);
    public static final DeferredBlock<Block> HALL_LEDGER = block("town_hall_ledger", Kind.HALL);
    public static final DeferredBlock<Block> CUSTOMS_COUNTER = block("customs_counter", Kind.CUSTOMS);

    private static DeferredBlock<Block> block(String id, Kind kind) {
        DeferredBlock<Block> b = BLOCKS.register(id, () -> new FurnitureBlock(kind, BlockBehaviour.Properties.of()
                .strength(kind == Kind.PLAQUE ? 1f : 2f).sound(kind == Kind.PLAQUE ? SoundType.HANGING_SIGN
                        : SoundType.WOOD).noOcclusion()));
        ITEMS.register(id, () -> new BlockItem(b.get(), new Item.Properties()));
        return b;
    }

    private ColonyBlocks() {}

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
    }

    /** One of the colony's furniture blocks; faces the player who placed it (a plaque: away from its wall). */
    public static final class FurnitureBlock extends HorizontalDirectionalBlock {
        private final Kind kind;

        public FurnitureBlock(Kind kind, BlockBehaviour.Properties p) {
            super(p);
            this.kind = kind;
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
        }

        @Override
        protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
            return simpleCodec(p -> new FurnitureBlock(kind, p));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(FACING);
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext ctx) {
            if (kind == Kind.PLAQUE) {
                Direction face = ctx.getClickedFace();
                if (face.getAxis().isVertical()) {
                    return null; // plaques hang on walls
                }
                return defaultBlockState().setValue(FACING, face);
            }
            return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
        }

        /** A plaque hung where no building is registered yet registers the room (it replaced the writ). */
        @Override
        public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                @org.jetbrains.annotations.Nullable net.minecraft.world.entity.LivingEntity placer,
                net.minecraft.world.item.ItemStack stack) {
            super.setPlacedBy(level, pos, state, placer, stack);
            if (kind == Kind.PLAQUE && placer instanceof ServerPlayer sp
                    && level instanceof net.minecraft.server.level.ServerLevel sl) {
                registerAt(sp, sl, pos, state);
            }
        }

        @Override
        protected boolean canSurvive(BlockState state, net.minecraft.world.level.LevelReader level, BlockPos pos) {
            if (kind != Kind.PLAQUE) {
                return true;
            }
            BlockPos wall = pos.relative(state.getValue(FACING).getOpposite());
            return level.getBlockState(wall).isFaceSturdy(level, wall, state.getValue(FACING));
        }

        @Override
        protected BlockState updateShape(BlockState state, Direction dir, BlockState other,
                net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockPos otherPos) {
            return kind == Kind.PLAQUE && dir == state.getValue(FACING).getOpposite() && !canSurvive(state, level, pos)
                    ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
                    : super.updateShape(state, dir, other, level, pos, otherPos);
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return switch (kind) {
                case PLAQUE -> switch (state.getValue(FACING)) {
                    case SOUTH -> Block.box(2, 4, 0, 14, 12, 1.5);
                    case WEST -> Block.box(14.5, 4, 2, 16, 12, 14);
                    case EAST -> Block.box(0, 4, 2, 1.5, 12, 14);
                    default -> Block.box(2, 4, 14.5, 14, 12, 16);
                };
                case CUSTOMS -> Block.box(0, 0, 0, 16, 15, 16);
                default -> Block.box(1, 0, 1, 15, 15, 15);
            };
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                BlockHitResult hit) {
            if (level.isClientSide) {
                return InteractionResult.SUCCESS;
            }
            if (player instanceof ServerPlayer sp) {
                open(sp, pos, state, kind);
            }
            return InteractionResult.CONSUME;
        }
    }

    /** Opens the block's screen, or tells the player why not (on the action bar, never a chat link). */
    static void open(ServerPlayer player, BlockPos pos, BlockState state, Kind kind) {
        if (kind == Kind.PLAQUE) {
            Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);
            for (Colony c : Colonies.get(player.server).all()) {
                if (!c.dimension().equals(player.level().dimension())) {
                    continue;
                }
                Optional<Building> b = plaqueBuilding(Buildings.of(c), pos, facing);
                if (b.isPresent()) {
                    if (!Permissions.isMember(player.getUUID(), c)) {
                        player.displayClientMessage(Component.translatableWithFallback(
                                "colony.lotr_port.plaque.foreign", "This is %s's %s", c.name(), b.get().label()), true);
                        return;
                    }
                    Books.open(player, PlaqueBook.KIND, PlaqueBook.context(c, b.get()), "", "");
                    return;
                }
            }
            // no building yet: measure the room now (the plaque replaced the writ), then open its page
            if (player.level() instanceof net.minecraft.server.level.ServerLevel sl && registerAt(player, sl, pos, state)) {
                open(player, pos, state, kind);
            }
            return;
        }
        int reach = BuildingSettings.get().reach();
        Optional<Colony> colony = Colonies.get(player.server).all().stream()
                .filter(c -> c.dimension().equals(player.level().dimension()) && !c.ai()
                        && c.hall().distSqr(pos) <= (double) reach * reach)
                .min(Comparator.comparingDouble(c -> c.hall().distSqr(pos)));
        if (colony.isEmpty()) {
            player.displayClientMessage(Component.translatableWithFallback("colony.lotr_port.furniture.no_colony",
                    "This belongs to no colony: place it near a Town Hall"), true);
            return;
        }
        Colony c = colony.get();
        String book = switch (kind) {
            case TREASURY -> TreasuryBook.KIND;
            case HALL -> HallLedgerBook.KIND;
            default -> CustomsBook.KIND;
        };
        Books.open(player, book, c.id().toString(), "", "");
    }

    /**
     * Registers the room by a plaque (ColonyBuildings.plaque) unless it already names a building; says what happened
     * over the hotbar (never in chat). True when the plaque names a building afterwards.
     */
    static boolean registerAt(ServerPlayer player, net.minecraft.server.level.ServerLevel level, BlockPos pos,
            BlockState state) {
        Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);
        for (Colony c : Colonies.get(player.server).all()) {
            if (c.dimension().equals(level.dimension()) && plaqueBuilding(Buildings.of(c), pos, facing).isPresent()) {
                return true;
            }
        }
        var r = com.porcupinestudios.lotrport.colony.building.ColonyBuildings.plaque(player, level, pos);
        player.displayClientMessage(r.message(), true);
        return r.building().isPresent();
    }

    /** The building a plaque names: the room it hangs in, the one behind its wall, or one whose door is by it. */
    public static Optional<Building> plaqueBuilding(Buildings all, BlockPos pos, Direction facing) {
        BlockPos[] spots = {pos, pos.relative(facing), pos.relative(facing.getOpposite(), 2), pos.below(),
                pos.relative(facing).below()};
        for (BlockPos p : spots) {
            Optional<Building> b = all.at(p);
            if (b.isPresent()) {
                return b;
            }
        }
        return all.list().stream().filter(b -> b.door().distManhattan(pos) <= 3)
                .min(Comparator.comparingInt(b -> b.door().distManhattan(pos)));
    }
}
