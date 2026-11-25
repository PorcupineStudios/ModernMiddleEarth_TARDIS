package com.porcupinestudios.lotrport.world.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * One LOTR structure's template, turned about its middle, with LOTR's foundations: under every block of the ground
 * layer, open space (air, water, plants) is filled down to solid ground, with dirt under grass and dirt and a copy
 * of the block above otherwise (stone under stone, cobble under cobble), as LOTR's generators filled downwards.
 */
public final class LotrStructurePiece extends TemplateStructurePiece {
    /** Foundation blocks under the ground layer that are dirt; deeper ones are stone. */
    private static final int SOIL_DEPTH = 4;
    private final int ground;
    private final int maxFoundation;

    LotrStructurePiece(StructureTemplateManager manager, ResourceLocation id, BlockPos middle, Rotation rotation,
            int ground, int maxFoundation) {
        super(LotrStructures.PIECE.get(), 0, manager, id, id.toString(), settings(manager, id, rotation),
                corner(manager, id, middle));
        this.ground = ground;
        this.maxFoundation = maxFoundation;
    }

    public LotrStructurePiece(StructureTemplateManager manager, CompoundTag tag) {
        super(LotrStructures.PIECE.get(), tag, manager,
                id -> settings(manager, id, Rotation.valueOf(tag.getString("Rot"))));
        this.ground = tag.getInt("Ground");
        this.maxFoundation = tag.getInt("MaxFoundation");
    }

    private static StructurePlaceSettings settings(StructureTemplateManager manager, ResourceLocation id,
            Rotation rotation) {
        Vec3i size = manager.getOrCreate(id).getSize();
        return new StructurePlaceSettings().setRotation(rotation)
                .setRotationPivot(new BlockPos(size.getX() / 2, 0, size.getZ() / 2));
    }

    private static BlockPos corner(StructureTemplateManager manager, ResourceLocation id, BlockPos middle) {
        Vec3i size = manager.getOrCreate(id).getSize();
        return middle.offset(-size.getX() / 2, 0, -size.getZ() / 2);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        super.addAdditionalSaveData(context, tag);
        tag.putString("Rot", placeSettings.getRotation().name());
        tag.putInt("Ground", ground);
        tag.putInt("MaxFoundation", maxFoundation);
    }

    @Override
    protected void handleDataMarker(String name, BlockPos pos, ServerLevelAccessor level, RandomSource random,
            BoundingBox box) {
        // LOTR's templates carry no data markers
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
            RandomSource random, BoundingBox box, ChunkPos chunk, BlockPos pivot) {
        super.postProcess(level, structures, generator, random, box, chunk, pivot);
        int y = templatePosition.getY() + ground;
        BoundingBox mine = getBoundingBox();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int x = Math.max(mine.minX(), box.minX()); x <= Math.min(mine.maxX(), box.maxX()); x++) {
            for (int z = Math.max(mine.minZ(), box.minZ()); z <= Math.min(mine.maxZ(), box.maxZ()); z++) {
                BlockState top = level.getBlockState(at.set(x, y, z));
                if (top.isAir() && levelGround(level, at, x, y, z)) {
                    continue;
                }
                // paths and farmland (porches, garden beds) aren't full blocks but stand on dirt all the same
                boolean soil = top.is(Blocks.DIRT_PATH) || top.is(Blocks.FARMLAND);
                if (top.isAir() || !soil && !top.isCollisionShapeFullBlock(level, at)) {
                    continue;
                }
                BlockState fill = soil || top.is(BlockTags.DIRT) ? Blocks.DIRT.defaultBlockState() : top;
                for (int d = 1; d <= maxFoundation && y - d > level.getMinBuildHeight(); d++) {
                    BlockState below = level.getBlockState(at.set(x, y - d, z));
                    if (!open(below)) {
                        break;
                    }
                    // ground scans don't see caves, so a cave mouth under the footprint can be deep: dirt for the
                    // top blocks, stone below, all the way down (a building never hangs over a pit)
                    level.setBlock(at, d > SOIL_DEPTH && fill.is(Blocks.DIRT) ? Blocks.STONE.defaultBlockState() : fill, 2);
                }
            }
        }
        compactDoubleChests(level, mine, box);
    }

    /**
     * The template leaves the ground layer out where LOTR's flat export world still had its own grass (gardens, yards,
     * between fences), so on lower ground those spots kept the natural surface a block or more down: pits around the
     * house (Aaron 2026-11-16). LOTR's generators levelled their whole footprint, so an open ground-layer spot with open
     * space under it gets grass, and dirt under that down to the ground, as far as the foundation may go.
     */
    private boolean levelGround(WorldGenLevel level, BlockPos.MutableBlockPos at, int x, int y, int z) {
        int depth = 1;
        while (depth <= maxFoundation && y - depth > level.getMinBuildHeight()
                && open(level.getBlockState(at.set(x, y - depth, z)))) {
            depth++;
        }
        if (depth == 1 || depth > maxFoundation) {
            return false;
        }
        level.setBlock(at.set(x, y, z), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
        for (int d = 1; d < depth; d++) {
            level.setBlock(at.set(x, y - d, z),
                    (d > SOIL_DEPTH ? Blocks.STONE : Blocks.DIRT).defaultBlockState(), 2);
        }
        return true;
    }

    /**
     * LOTR filled each half of a double chest on its own, so opened it showed the first half's items, empty rows,
     * then the second's (Aaron 2026-11-15: "double chests shouldn't have an item gap"). Packs a pair's items, in their
     * order, into the half shown on top (vanilla's FIRST: type RIGHT) and then the other. A pair whose halves are in
     * two chunks is packed when the second of them is built (the first's half isn't a chest yet when it runs).
     */
    private static void compactDoubleChests(WorldGenLevel level, BoundingBox mine, BoundingBox box) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int x = Math.max(mine.minX(), box.minX()); x <= Math.min(mine.maxX(), box.maxX()); x++) {
            for (int z = Math.max(mine.minZ(), box.minZ()); z <= Math.min(mine.maxZ(), box.maxZ()); z++) {
                for (int y = mine.minY(); y <= mine.maxY(); y++) {
                    BlockState state = level.getBlockState(at.set(x, y, z));
                    if (!(state.getBlock() instanceof ChestBlock) || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) {
                        continue;
                    }
                    BlockPos other = at.relative(ChestBlock.getConnectedDirection(state));
                    BlockState otherState = level.getBlockState(other);
                    if (otherState.getBlock() != state.getBlock()
                            || otherState.getValue(ChestBlock.TYPE) != state.getValue(ChestBlock.TYPE).getOpposite()) {
                        continue;
                    }
                    boolean first = state.getValue(ChestBlock.TYPE) == ChestType.RIGHT;
                    if (level.getBlockEntity(first ? at : other) instanceof ChestBlockEntity top
                            && level.getBlockEntity(first ? other : at) instanceof ChestBlockEntity bottom) {
                        pack(top, bottom);
                    }
                }
            }
        }
    }

    private static void pack(ChestBlockEntity top, ChestBlockEntity bottom) {
        java.util.List<ItemStack> items = new java.util.ArrayList<>();
        for (ChestBlockEntity half : new ChestBlockEntity[] {top, bottom}) {
            for (int i = 0; i < half.getContainerSize(); i++) {
                if (!half.getItem(i).isEmpty()) {
                    items.add(half.getItem(i));
                }
                half.setItem(i, ItemStack.EMPTY);
            }
        }
        int n = 0;
        for (ChestBlockEntity half : new ChestBlockEntity[] {top, bottom}) {
            for (int i = 0; i < half.getContainerSize() && n < items.size(); i++) {
                half.setItem(i, items.get(n++));
            }
            half.setChanged();
        }
    }

    private static boolean open(BlockState state) {
        return state.isAir() || state.canBeReplaced() || !state.getFluidState().isEmpty()
                || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS);
    }
}
